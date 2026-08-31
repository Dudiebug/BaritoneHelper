package dev.dudie.baritonehelper.worker;

import dev.dudie.baritonehelper.entity.WorkerEntity;
import dev.dudie.baritonehelper.internal.baritone.api.behavior.PathingStatus;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Owns worker lifecycle and storage policy. Collection discovery, target
 * ordering, stance selection, path calculation, and movement belong to the
 * worker's embedded Baritone MineProcess.
 */
public final class WorkerController {
    private static final int WATCHDOG_TICKS = 120;
    private static final int MAX_REPLAN_ATTEMPTS = 3;
    private static final double ARRIVAL_DISTANCE_SQUARED = 2.25;
    private static final double INTERACTION_DISTANCE_SQUARED = 36.0;

    private final WorkerEntity worker;
    private @org.jetbrains.annotations.Nullable BlockPos currentTarget;
    private @org.jetbrains.annotations.Nullable BlockPos currentWorkPosition;
    private WorkerActivity activity = WorkerActivity.IDLE;
    private int watchdogTicks;
    private int replanAttempts;
    private int lastProgressAgeTicks;
    private double bestDistance = Double.MAX_VALUE;
    private boolean pathRequested;
    private @org.jetbrains.annotations.Nullable BlockPos lastNavigationDestination;
    private boolean closeInteractionFallback;

    public WorkerController(WorkerEntity worker) {
        this.worker = worker;
    }

    public void tick(ServerLevel level) {
        if (!worker.job().activelyWorks()) {
            activity = switch (worker.job()) {
                case READY -> WorkerActivity.READY;
                case BLOCKED -> WorkerActivity.BLOCKED;
                case COMPLETED -> WorkerActivity.IDLE;
                default -> WorkerActivity.IDLE;
            };
            worker.stopEngineProcesses();
            clearPlan(false);
            return;
        }

        worker.ensureWorkerTickets();
        lastProgressAgeTicks = Math.min(lastProgressAgeTicks + 1, Integer.MAX_VALUE - 1);
        if (worker.job() == WorkerJob.COLLECT) {
            tickCollection(level);
        } else if (worker.job() == WorkerJob.DEPOSIT) {
            tickDeposit(level);
        }
    }

    public Optional<BlockPos> currentTarget() { return Optional.ofNullable(currentTarget); }
    public Optional<BlockPos> currentWorkPosition() { return Optional.ofNullable(currentWorkPosition); }
    public WorkerActivity activity() { return activity; }
    public int replanAttempts() { return replanAttempts; }
    public int lastProgressAgeTicks() { return lastProgressAgeTicks; }

    // These accessors remain part of the dashboard payload while discovery is
    // owned by MineProcess rather than by a controller-side scan cursor.
    public int chunksExamined() { return 0; }
    public int chunksScanned() { return 0; }
    public int positionsExamined() { return 0; }
    public int matchingBlocks() { return 0; }
    public int candidatesFound() { return 0; }
    public int candidatesRejectedByPolicy() { return 0; }
    public int candidatesRejectedAsUnreachable() { return 0; }
    public int cachedCandidateCount() { return 0; }
    public int frontierIndex() { return 0; }
    public int frontierSize() { return 0; }
    public boolean waitingForSearchChunk() { return false; }
    public boolean pathRequested() { return pathRequested; }
    public Optional<BlockPos> lastNavigationDestination() {
        return Optional.ofNullable(lastNavigationDestination);
    }
    public String lastScannedChunk() { return ""; }
    public String requestedSearchChunk() { return ""; }
    public long maxSearchTickNanos() { return 0L; }

    public void resetTransientState() {
        worker.stopEngineProcesses();
        clearPlan(true);
        watchdogTicks = 0;
        replanAttempts = 0;
        lastProgressAgeTicks = 0;
        activity = switch (worker.job()) {
            case READY -> WorkerActivity.READY;
            case COLLECT -> WorkerActivity.SEARCHING;
            case DEPOSIT -> WorkerActivity.RETURNING;
            case BLOCKED -> WorkerActivity.BLOCKED;
            case COMPLETED, IDLE -> WorkerActivity.IDLE;
        };
    }

    private void tickCollection(ServerLevel level) {
        ResourceLocation targetId = worker.targetBlockId().orElse(null);
        if (targetId == null) {
            block(WorkerBlockReason.NO_TARGET);
            return;
        }
        if (worker.isExcluded(targetId)) {
            block(WorkerBlockReason.TARGET_EXCLUDED);
            return;
        }
        if (!worker.workAreaDimension().isBlank()
                && !worker.workAreaDimension().equals(level.dimension().location().toString())) {
            block(WorkerBlockReason.WORK_AREA_WRONG_DIMENSION);
            return;
        }
        if (!worker.unlimitedCount() && worker.completedBlockCount() >= worker.requestedBlockCount()) {
            finishGoal();
            return;
        }

        Block targetBlock = BuiltInRegistries.BLOCK.getOptional(targetId).orElse(null);
        if (targetBlock == null || targetBlock == Blocks.AIR) {
            block(WorkerBlockReason.NO_TARGET);
            return;
        }
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            block(WorkerBlockReason.MOB_GRIEFING_DISABLED);
            return;
        }
        if (!worker.hasInventoryRoomFor(targetBlock.defaultBlockState())) {
            worker.requestDepositOrBlock();
            clearPlan(false);
            return;
        }

        if (!worker.ensureMineProcessStarted()) {
            activity = WorkerActivity.SEARCHING;
            return;
        }

        if (!worker.mineProcessActive()) {
            PathingStatus status = worker.pathingStatus();
            block(status == PathingStatus.NO_PATH || status == PathingStatus.FAILED
                    ? WorkerBlockReason.NAVIGATION_FAILED
                    : WorkerBlockReason.NO_MATCHING_BLOCKS);
            return;
        }

        updateCollectionActivity();
    }

    private void updateCollectionActivity() {
        if (worker.isBreaking() || worker.interactionManagerMining()) {
            activity = WorkerActivity.BREAKING;
            worker.setRuntimeState(WorkerRuntimeState.BREAKING);
        } else if (worker.pathingStatus() == PathingStatus.IDLE) {
            activity = WorkerActivity.SEARCHING;
            worker.setRuntimeState(WorkerRuntimeState.SEARCHING);
        } else {
            activity = WorkerActivity.PATHING;
            worker.setRuntimeState(WorkerRuntimeState.PATHING);
        }
    }

    private void finishGoal() {
        if (worker.hasCargo()) {
            if (worker.storagePosition().isPresent()) {
                worker.requestDepositOrBlock();
                clearPlan(false);
            } else {
                block(WorkerBlockReason.STORAGE_MISSING);
            }
            return;
        }
        worker.stopEngineProcesses();
        clearPlan(false);
        worker.markCompleted();
        activity = WorkerActivity.IDLE;
        worker.setRuntimeState(WorkerRuntimeState.COMPLETED);
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
        if (worker.isInsideNoEnter(storage) || worker.isInsideNoModify(storage)) {
            block(WorkerBlockReason.STORAGE_IN_NO_WORK_ZONE);
            return;
        }
        if (!(level.getBlockEntity(storage) instanceof Container destination)) {
            block(WorkerBlockReason.STORAGE_MISSING);
            return;
        }
        if (currentTarget == null || !currentTarget.equals(storage)) {
            currentTarget = storage.immutable();
            currentWorkPosition = WorkerPlanner.nearestWorkPosition(level, worker, storage).orElse(null);
            if (currentWorkPosition == null && distanceTo(storage) <= INTERACTION_DISTANCE_SQUARED) {
                currentWorkPosition = worker.blockPosition().immutable();
                closeInteractionFallback = true;
            }
            watchdogTicks = 0;
            bestDistance = Double.MAX_VALUE;
            if (currentWorkPosition == null) {
                block(WorkerBlockReason.NO_REACHABLE_POSITION);
                return;
            }
        }

        double distance = distanceTo(currentWorkPosition);
        if (distance > ARRIVAL_DISTANCE_SQUARED || distanceTo(storage) > INTERACTION_DISTANCE_SQUARED
                || (!closeInteractionFallback && !WorkerPlanner.hasLineOfSight(level, worker, storage))) {
            activity = WorkerActivity.RETURNING;
            worker.setRuntimeState(WorkerRuntimeState.RETURNING_TO_STORAGE);
            navigateToStorage(currentWorkPosition);
            watchProgress(distance, WorkerBlockReason.STUCK);
            return;
        }

        activity = WorkerActivity.DEPOSITING;
        worker.setRuntimeState(WorkerRuntimeState.DEPOSITING);
        int moved = WorkerStorage.deposit(worker, destination);
        if (!worker.hasCargo()) {
            if (moved > 0) {
                worker.notifyOwner(ChatFormatting.GREEN, "message.baritonehelper.deposited", moved);
            }
            worker.stopEngineProcesses();
            if (!worker.unlimitedCount() && worker.completedBlockCount() >= worker.requestedBlockCount()) {
                worker.markCompleted();
                worker.setRuntimeState(WorkerRuntimeState.COMPLETED);
            } else {
                worker.markCollecting();
            }
            resetTransientState();
        } else if (moved == 0) {
            block(WorkerBlockReason.STORAGE_FULL);
        }
    }

    /** Storage-only movement; collection movement remains MineProcess-owned. */
    private void navigateToStorage(BlockPos destination) {
        if (pathRequested) return;
        lastNavigationDestination = destination.immutable();
        worker.ensureWorkerTickets();
        pathRequested = worker.beginPathTo(destination);
    }

    private double distanceTo(BlockPos position) {
        return worker.distanceToSqr(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5);
    }

    private void watchProgress(double distance, WorkerBlockReason terminalReason) {
        if (pathRequested && worker.pathingStatus() == PathingStatus.CALCULATING) {
            watchdogTicks = 0;
            return;
        }
        if (distance + 0.04 < bestDistance) {
            bestDistance = distance;
            watchdogTicks = 0;
            lastProgressAgeTicks = 0;
            return;
        }
        if (++watchdogTicks >= WATCHDOG_TICKS) {
            replanOrBlock(terminalReason);
        }
    }

    private void replanOrBlock(WorkerBlockReason terminalReason) {
        if (++replanAttempts >= MAX_REPLAN_ATTEMPTS) {
            block(terminalReason);
            return;
        }
        worker.stopEngineProcesses();
        clearPlan(false);
        activity = WorkerActivity.RETURNING;
    }

    private void block(WorkerBlockReason reason) {
        worker.stopEngineProcesses();
        clearPlan(false);
        activity = WorkerActivity.BLOCKED;
        worker.markBlocked(reason);
    }

    private void clearPlan(boolean clearProgress) {
        currentTarget = null;
        currentWorkPosition = null;
        pathRequested = false;
        lastNavigationDestination = null;
        watchdogTicks = 0;
        bestDistance = Double.MAX_VALUE;
        closeInteractionFallback = false;
        if (clearProgress) {
            replanAttempts = 0;
        }
    }
}
