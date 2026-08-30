package dev.dudie.baritonehelper.worker;

import dev.dudie.baritonehelper.entity.WorkerEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class WorkerController {
    private static final int SCAN_INTERVAL_TICKS = 30;
    private static final int ACTION_INTERVAL_TICKS = 10;
    private static final int WATCHDOG_TICKS = 120;
    private static final int REJECTED_TARGET_TICKS = 160;
    private static final int MAX_REPLAN_ATTEMPTS = 3;
    private static final int MAX_EMPTY_SCANS = 10;
    private static final double ARRIVAL_DISTANCE_SQUARED = 2.25;
    private static final double INTERACTION_DISTANCE_SQUARED = 12.25;
    private static final ItemStack WORK_TOOL = new ItemStack(Items.NETHERITE_PICKAXE);

    private final WorkerEntity worker;
    private final Map<Long, Integer> temporarilyRejected = new HashMap<>();
    private @Nullable BlockPos currentTarget;
    private @Nullable BlockPos currentWorkPosition;
    private WorkerActivity activity = WorkerActivity.IDLE;
    private int scanCooldown;
    private int actionCooldown;
    private int watchdogTicks;
    private int replanAttempts;
    private int emptyScans;
    private int lastProgressAgeTicks;
    private double bestDistance = Double.MAX_VALUE;

    public WorkerController(WorkerEntity worker) {
        this.worker = worker;
    }

    public void tick(ServerLevel level) {
        tickRejectedTargets();

        if (scanCooldown > 0) {
            scanCooldown--;
        }
        if (actionCooldown > 0) {
            actionCooldown--;
        }

        if (!worker.job().activelyWorks()) {
            activity = switch (worker.job()) {
                case READY -> WorkerActivity.READY;
                case BLOCKED -> WorkerActivity.BLOCKED;
                default -> WorkerActivity.IDLE;
            };
            clearPlan(false);
            worker.releaseWorkerTickets();
            return;
        }

        worker.ensureWorkerTickets();
        lastProgressAgeTicks = Math.min(lastProgressAgeTicks + 1, Integer.MAX_VALUE - 1);

        switch (worker.job()) {
            case COLLECT -> tickCollection(level);
            case DEPOSIT -> tickDeposit(level);
            case IDLE, READY, BLOCKED -> {
                // Handled before the active-job switch.
            }
        }
    }

    public Optional<BlockPos> currentTarget() {
        return Optional.ofNullable(currentTarget);
    }

    public Optional<BlockPos> currentWorkPosition() {
        return Optional.ofNullable(currentWorkPosition);
    }

    public WorkerActivity activity() {
        return activity;
    }

    public int replanAttempts() {
        return replanAttempts;
    }

    public int lastProgressAgeTicks() {
        return lastProgressAgeTicks;
    }

    public void resetTransientState() {
        currentTarget = null;
        currentWorkPosition = null;
        scanCooldown = 0;
        actionCooldown = 0;
        watchdogTicks = 0;
        replanAttempts = 0;
        emptyScans = 0;
        lastProgressAgeTicks = 0;
        bestDistance = Double.MAX_VALUE;
        temporarilyRejected.clear();
        worker.getNavigation().stop();
        activity = switch (worker.job()) {
            case READY -> WorkerActivity.READY;
            case COLLECT -> WorkerActivity.SEARCHING;
            case DEPOSIT -> WorkerActivity.RETURNING;
            case BLOCKED -> WorkerActivity.BLOCKED;
            default -> WorkerActivity.IDLE;
        };
    }

    private void tickCollection(ServerLevel level) {
        var targetBlockId = worker.targetBlockId().orElse(null);
        if (targetBlockId == null) {
            block(WorkerBlockReason.NO_TARGET);
            return;
        }
        if (worker.isExcluded(targetBlockId)) {
            block(WorkerBlockReason.TARGET_EXCLUDED);
            return;
        }

        if (currentTarget != null
                && !WorkerPlanner.isCollectable(
                        level, worker, currentTarget, targetBlockId)) {
            clearPlan(false);
        }

        if (currentTarget == null || currentWorkPosition == null) {
            activity = WorkerActivity.SEARCHING;
            if (scanCooldown > 0) {
                worker.getNavigation().stop();
                return;
            }

            WorkerPlanner.CollectionPlan plan = WorkerPlanner.nearestCollectable(
                    level, worker, temporarilyRejected.keySet()).orElse(null);
            scanCooldown = SCAN_INTERVAL_TICKS;
            watchdogTicks = 0;
            bestDistance = Double.MAX_VALUE;
            if (plan == null) {
                emptyScans++;
                worker.getNavigation().stop();
                if (emptyScans >= MAX_EMPTY_SCANS) {
                    block(WorkerBlockReason.NO_MATCHING_BLOCKS);
                }
                return;
            }

            emptyScans = 0;
            currentTarget = plan.target();
            currentWorkPosition = plan.workPosition();
            activity = WorkerActivity.PATHING;
        }

        double distance = distanceTo(currentWorkPosition);
        double targetDistance = distanceTo(currentTarget);
        if (distance <= ARRIVAL_DISTANCE_SQUARED
                && targetDistance <= INTERACTION_DISTANCE_SQUARED
                && canReach(level, currentTarget)) {
            activity = WorkerActivity.COLLECTING;
            worker.getNavigation().stop();
            if (actionCooldown == 0) {
                collect(level, currentTarget);
                actionCooldown = ACTION_INTERVAL_TICKS;
            }
            return;
        }

        activity = WorkerActivity.PATHING;
        if (worker.getNavigation().isDone() || worker.tickCount % 20 == 0) {
            if (!navigateTo(currentWorkPosition)) {
                replanOrBlock(WorkerBlockReason.NO_REACHABLE_POSITION);
                return;
            }
        }
        watchProgress(distance, WorkerBlockReason.STUCK);
    }

    private void collect(ServerLevel level, BlockPos position) {
        var targetId = worker.targetBlockId().orElse(null);
        if (targetId == null
                || !WorkerPlanner.isCollectable(level, worker, position, targetId)) {
            clearPlan(false);
            return;
        }

        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            block(WorkerBlockReason.MOB_GRIEFING_DISABLED);
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(position);
        var state = level.getBlockState(position);
        List<ItemStack> drops = Block.getDrops(
                state, level, position, blockEntity, worker, WORK_TOOL);
        if (drops.isEmpty()) {
            replanOrBlock(WorkerBlockReason.TARGET_HAS_NO_DROPS);
            return;
        }

        if (!WorkerInventory.canFitAll(worker, drops)) {
            worker.requestDepositOrBlock();
            clearPlan(false);
            return;
        }

        if (!level.destroyBlock(position, false, worker)) {
            replanOrBlock(WorkerBlockReason.NAVIGATION_FAILED);
            return;
        }

        WorkerInventory.insertAll(worker, drops);
        replanAttempts = 0;
        emptyScans = 0;
        lastProgressAgeTicks = 0;
        clearPlan(false);
        activity = WorkerActivity.SEARCHING;
    }

    private void tickDeposit(ServerLevel level) {
        BlockPos storage = worker.storagePosition().orElse(null);
        if (storage == null) {
            block(WorkerBlockReason.STORAGE_MISSING);
            return;
        }
        if (!worker.storageIsIn(level)) {
            block(WorkerBlockReason.STORAGE_WRONG_DIMENSION);
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(storage);
        if (!(blockEntity instanceof Container destination)) {
            block(WorkerBlockReason.STORAGE_MISSING);
            return;
        }

        if (currentTarget == null || !currentTarget.equals(storage)) {
            currentTarget = storage.immutable();
            currentWorkPosition = WorkerPlanner.nearestWorkPosition(level, worker, storage)
                    .orElse(null);
            watchdogTicks = 0;
            bestDistance = Double.MAX_VALUE;
            if (currentWorkPosition == null) {
                block(WorkerBlockReason.NO_REACHABLE_POSITION);
                return;
            }
        }

        double distance = distanceTo(currentWorkPosition);
        double storageDistance = distanceTo(storage);
        if (distance > ARRIVAL_DISTANCE_SQUARED
                || storageDistance > INTERACTION_DISTANCE_SQUARED
                || !canReach(level, storage)) {
            activity = WorkerActivity.RETURNING;
            if (worker.getNavigation().isDone() || worker.tickCount % 20 == 0) {
                if (!navigateTo(currentWorkPosition)) {
                    replanOrBlock(WorkerBlockReason.NO_REACHABLE_POSITION);
                    return;
                }
            }
            watchProgress(distance, WorkerBlockReason.STUCK);
            return;
        }

        activity = WorkerActivity.DEPOSITING;
        worker.getNavigation().stop();
        int moved = WorkerStorage.deposit(worker, destination);
        if (worker.isEmpty()) {
            if (moved > 0) {
                worker.notifyOwner(
                        net.minecraft.ChatFormatting.GREEN,
                        "message.baritonehelper.deposited",
                        moved);
            }
            worker.markCollecting();
            resetTransientState();
        } else if (moved == 0) {
            block(WorkerBlockReason.STORAGE_FULL);
        }
    }

    private boolean navigateTo(BlockPos destination) {
        boolean started = worker.getNavigation().moveTo(
                destination.getX() + 0.5,
                destination.getY(),
                destination.getZ() + 0.5,
                1.0);
        if (!started && currentTarget != null && !currentTarget.equals(destination)) {
            // Vanilla ground navigation can occasionally reject an otherwise open
            // adjacent node. Pathing toward the solid target makes vanilla stop at
            // the nearest legal adjacent node instead of leaving the worker idle.
            started = worker.getNavigation().moveTo(
                    currentTarget.getX() + 0.5,
                    currentTarget.getY(),
                    currentTarget.getZ() + 0.5,
                    1.0);
        }
        return started;
    }

    private boolean canReach(ServerLevel level, BlockPos target) {
        if (level.getBlockEntity(target) instanceof Container
                && distanceTo(target) <= ARRIVAL_DISTANCE_SQUARED) {
            // Containers directly beside or below the worker are physically
            // reachable even when their partial collision shape confuses the ray.
            return true;
        }
        BlockHitResult hit = level.clip(new ClipContext(
                worker.getEyePosition(),
                Vec3.atCenterOf(target),
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                worker));
        return hit.getType() == HitResult.Type.BLOCK
                && hit.getBlockPos().equals(target);
    }

    private double distanceTo(BlockPos position) {
        return worker.distanceToSqr(
                position.getX() + 0.5,
                position.getY() + 0.5,
                position.getZ() + 0.5);
    }

    private void watchProgress(double distance, WorkerBlockReason terminalReason) {
        if (distance + 0.04 < bestDistance) {
            bestDistance = distance;
            watchdogTicks = 0;
            lastProgressAgeTicks = 0;
            return;
        }

        watchdogTicks++;
        if (watchdogTicks >= WATCHDOG_TICKS) {
            replanOrBlock(terminalReason);
        }
    }

    private void replanOrBlock(WorkerBlockReason terminalReason) {
        replanAttempts++;
        if (replanAttempts >= MAX_REPLAN_ATTEMPTS) {
            block(terminalReason);
            return;
        }

        if (currentTarget != null) {
            temporarilyRejected.put(currentTarget.asLong(), REJECTED_TARGET_TICKS);
        }
        clearPlan(false);
        scanCooldown = 5;
        activity = worker.job() == WorkerJob.DEPOSIT
                ? WorkerActivity.RETURNING
                : WorkerActivity.SEARCHING;
    }

    private void block(WorkerBlockReason reason) {
        clearPlan(false);
        activity = WorkerActivity.BLOCKED;
        worker.markBlocked(reason);
    }

    private void clearPlan(boolean clearRejected) {
        currentTarget = null;
        currentWorkPosition = null;
        watchdogTicks = 0;
        bestDistance = Double.MAX_VALUE;
        worker.getNavigation().stop();
        if (clearRejected) {
            temporarilyRejected.clear();
        }
    }

    private void tickRejectedTargets() {
        temporarilyRejected.replaceAll((position, ticks) -> ticks - 1);
        temporarilyRejected.entrySet().removeIf(entry -> entry.getValue() <= 0);
    }
}
