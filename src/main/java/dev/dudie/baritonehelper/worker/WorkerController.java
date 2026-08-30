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
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public final class WorkerController {
    private static final int SCAN_INTERVAL_TICKS = 40;
    private static final int ACTION_INTERVAL_TICKS = 10;
    private static final int WATCHDOG_TICKS = 200;
    private static final int REJECTED_TARGET_TICKS = 200;
    private static final double ACTION_DISTANCE_SQUARED = 9.0;
    private static final ItemStack WORK_TOOL = new ItemStack(Items.NETHERITE_PICKAXE);

    private final WorkerEntity worker;
    private final Map<Long, Integer> temporarilyRejected = new HashMap<>();
    private @Nullable BlockPos currentTarget;
    private int scanCooldown;
    private int actionCooldown;
    private int watchdogTicks;
    private double bestDistance = Double.MAX_VALUE;

    public WorkerController(WorkerEntity worker) {
        this.worker = worker;
    }

    public void tick(ServerLevel level) {
        tickRejectedTargets();
        worker.ensureWorkerTickets();

        if (scanCooldown > 0) {
            scanCooldown--;
        }
        if (actionCooldown > 0) {
            actionCooldown--;
        }

        switch (worker.job()) {
            case COLLECT -> tickCollection(level);
            case DEPOSIT -> tickDeposit(level);
            case IDLE, PAUSED, BLOCKED -> {
                currentTarget = null;
                watchdogTicks = 0;
                bestDistance = Double.MAX_VALUE;
                worker.getNavigation().stop();
            }
        }
    }

    public Optional<BlockPos> currentTarget() {
        return Optional.ofNullable(currentTarget);
    }

    public void resetTransientState() {
        currentTarget = null;
        scanCooldown = 0;
        actionCooldown = 0;
        watchdogTicks = 0;
        bestDistance = Double.MAX_VALUE;
        temporarilyRejected.clear();
        worker.getNavigation().stop();
    }

    private void tickCollection(ServerLevel level) {
        if (worker.targetBlockId().isEmpty()) {
            worker.markBlocked();
            return;
        }

        if (currentTarget != null
                && !WorkerPlanner.isCollectable(
                        level,
                        worker,
                        currentTarget,
                        worker.targetBlockId().orElseThrow())) {
            currentTarget = null;
            watchdogTicks = 0;
            bestDistance = Double.MAX_VALUE;
        }

        if (currentTarget == null) {
            if (scanCooldown > 0) {
                worker.getNavigation().stop();
                return;
            }
            currentTarget = WorkerPlanner.nearestCollectable(
                    level, worker, temporarilyRejected.keySet()).orElse(null);
            scanCooldown = SCAN_INTERVAL_TICKS;
            watchdogTicks = 0;
            bestDistance = Double.MAX_VALUE;
            if (currentTarget == null) {
                worker.getNavigation().stop();
                return;
            }
        }

        double distance = worker.distanceToSqr(
                currentTarget.getX() + 0.5,
                currentTarget.getY() + 0.5,
                currentTarget.getZ() + 0.5);

        if (distance <= ACTION_DISTANCE_SQUARED) {
            worker.getNavigation().stop();
            if (actionCooldown == 0) {
                collect(level, currentTarget);
                actionCooldown = ACTION_INTERVAL_TICKS;
            }
            return;
        }

        if (worker.getNavigation().isDone() || worker.tickCount % 20 == 0) {
            worker.getNavigation().moveTo(
                    currentTarget.getX() + 0.5,
                    currentTarget.getY(),
                    currentTarget.getZ() + 0.5,
                    1.0);
        }
        watchProgress(currentTarget, distance);
    }

    private void collect(ServerLevel level, BlockPos position) {
        var targetId = worker.targetBlockId().orElse(null);
        if (targetId == null || !WorkerPlanner.isCollectable(level, worker, position, targetId)) {
            currentTarget = null;
            return;
        }

        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            worker.markBlocked();
            currentTarget = null;
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(position);
        var state = level.getBlockState(position);
        List<ItemStack> drops = Block.getDrops(state, level, position, blockEntity, worker, WORK_TOOL);
        if (drops.isEmpty()) {
            reject(position);
            return;
        }

        if (!WorkerInventory.canFitAll(worker, drops)) {
            worker.requestDepositOrBlock();
            currentTarget = null;
            return;
        }

        if (!level.destroyBlock(position, false, worker)) {
            reject(position);
            return;
        }

        WorkerInventory.insertAll(worker, drops);
        currentTarget = null;
        watchdogTicks = 0;
        bestDistance = Double.MAX_VALUE;
    }

    private void tickDeposit(ServerLevel level) {
        BlockPos storage = worker.storagePosition().orElse(null);
        if (storage == null || !worker.storageIsIn(level)) {
            worker.markBlocked();
            return;
        }

        double distance = worker.distanceToSqr(
                storage.getX() + 0.5,
                storage.getY() + 0.5,
                storage.getZ() + 0.5);

        if (distance > ACTION_DISTANCE_SQUARED) {
            if (worker.getNavigation().isDone() || worker.tickCount % 20 == 0) {
                worker.getNavigation().moveTo(
                        storage.getX() + 0.5,
                        storage.getY(),
                        storage.getZ() + 0.5,
                        1.0);
            }
            watchProgress(storage, distance);
            return;
        }

        worker.getNavigation().stop();
        BlockEntity blockEntity = level.getBlockEntity(storage);
        if (!(blockEntity instanceof Container destination)) {
            worker.markBlocked();
            return;
        }

        int moved = WorkerStorage.deposit(worker, destination);
        if (worker.isEmpty()) {
            worker.markCollecting();
            resetTransientState();
        } else if (moved == 0) {
            worker.markBlocked();
        }
    }

    private void watchProgress(BlockPos target, double distance) {
        if (distance + 0.25 < bestDistance) {
            bestDistance = distance;
            watchdogTicks = 0;
            return;
        }

        watchdogTicks++;
        if (watchdogTicks >= WATCHDOG_TICKS) {
            reject(target);
        }
    }

    private void reject(BlockPos position) {
        temporarilyRejected.put(position.asLong(), REJECTED_TARGET_TICKS);
        currentTarget = null;
        watchdogTicks = 0;
        bestDistance = Double.MAX_VALUE;
        scanCooldown = SCAN_INTERVAL_TICKS;
        worker.getNavigation().stop();
    }

    private void tickRejectedTargets() {
        temporarilyRejected.replaceAll((position, ticks) -> ticks - 1);
        temporarilyRejected.entrySet().removeIf(entry -> entry.getValue() <= 0);
    }
}
