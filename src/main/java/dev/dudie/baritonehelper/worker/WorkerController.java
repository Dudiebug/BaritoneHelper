package dev.dudie.baritonehelper.worker;

import dev.dudie.baritonehelper.entity.WorkerEntity;
import dev.dudie.baritonehelper.internal.baritone.api.behavior.PathingStatus;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Collector state machine.  Movement is delegated to the embedded Baritone
 * engine; this class only reserves targets, applies policy, and drives the
 * real interaction adapter.
 */
public final class WorkerController {
    private static final int WATCHDOG_TICKS = 120;
    private static final int REJECTED_TARGET_TICKS = 160;
    private static final int MAX_REPLAN_ATTEMPTS = 3;
    private static final int MAX_EMPTY_SCANS = 10;
    private static final int SEARCH_BLOCK_BUDGET = 4_096;
    private static final double ARRIVAL_DISTANCE_SQUARED = 2.25;
    private static final double INTERACTION_DISTANCE_SQUARED = 36.0;

    private final WorkerEntity worker;
    private final Map<Long, Integer> temporarilyRejected = new HashMap<>();
    private @org.jetbrains.annotations.Nullable BlockPos currentTarget;
    private @org.jetbrains.annotations.Nullable BlockPos currentWorkPosition;
    private @org.jetbrains.annotations.Nullable WorkerPlanner.SearchCursor searchCursor;
    private WorkerActivity activity = WorkerActivity.IDLE;
    private int scanCooldown;
    private int watchdogTicks;
    private int replanAttempts;
    private int emptyScans;
    private int lastProgressAgeTicks;
    private int pendingDropTicks;
    private double bestDistance = Double.MAX_VALUE;
    private int chunksExamined;
    private long maxSearchTickNanos;
    private boolean pathRequested;
    private @org.jetbrains.annotations.Nullable BlockPos lastNavigationDestination;
    private boolean closeInteractionFallback;

    public WorkerController(WorkerEntity worker) {
        this.worker = worker;
    }

    public void tick(ServerLevel level) {
        tickRejectedTargets();
        if (scanCooldown > 0) scanCooldown--;
        if (!worker.job().activelyWorks()) {
            activity = switch (worker.job()) {
                case READY -> WorkerActivity.READY;
                case BLOCKED -> WorkerActivity.BLOCKED;
                case COMPLETED -> WorkerActivity.IDLE;
                default -> WorkerActivity.IDLE;
            };
            closeSearchCursor();
            clearPlan(false);
            worker.releaseWorkerTickets();
            return;
        }

        worker.ensureWorkerTickets();
        lastProgressAgeTicks = Math.min(lastProgressAgeTicks + 1, Integer.MAX_VALUE - 1);
        if (worker.job() == WorkerJob.COLLECT) tickCollection(level);
        else if (worker.job() == WorkerJob.DEPOSIT) tickDeposit(level);
    }

    public Optional<BlockPos> currentTarget() { return Optional.ofNullable(currentTarget); }
    public Optional<BlockPos> currentWorkPosition() { return Optional.ofNullable(currentWorkPosition); }
    public WorkerActivity activity() { return activity; }
    public int replanAttempts() { return replanAttempts; }
    public int lastProgressAgeTicks() { return lastProgressAgeTicks; }
    public int chunksExamined() { return chunksExamined; }
    public int chunksScanned() { return searchCursor == null ? 0 : searchCursor.chunksScanned(); }
    public int positionsExamined() { return searchCursor == null ? 0 : searchCursor.positionsExamined(); }
    public int matchingBlocks() { return searchCursor == null ? 0 : searchCursor.matchingBlocks(); }
    public int candidatesFound() { return searchCursor == null ? 0 : searchCursor.candidatesFound(); }
    public int candidatesRejectedByPolicy() {
        return searchCursor == null ? 0 : searchCursor.candidatesRejectedByPolicy();
    }
    public int candidatesRejectedAsUnreachable() {
        return searchCursor == null ? 0 : searchCursor.candidatesRejectedAsUnreachable();
    }
    public int cachedCandidateCount() { return searchCursor == null ? 0 : searchCursor.cachedCandidateCount(); }
    public int frontierIndex() { return searchCursor == null ? 0 : searchCursor.frontierIndex(); }
    public int frontierSize() { return searchCursor == null ? 0 : searchCursor.frontierSize(); }
    public boolean waitingForSearchChunk() {
        return searchCursor != null && searchCursor.waitingForChunk();
    }
    public boolean pathRequested() { return pathRequested; }
    public Optional<BlockPos> lastNavigationDestination() {
        return Optional.ofNullable(lastNavigationDestination);
    }
    public String lastScannedChunk() { return searchCursor == null ? "" : searchCursor.lastScannedChunk(); }
    public String requestedSearchChunk() { return searchCursor == null ? "" : searchCursor.requestedChunk(); }
    public long maxSearchTickNanos() { return maxSearchTickNanos; }

    public void resetTransientState() {
        worker.stopEngineProcesses();
        currentTarget = null;
        currentWorkPosition = null;
        closeSearchCursor();
        scanCooldown = 0;
        watchdogTicks = 0;
        replanAttempts = 0;
        emptyScans = 0;
        pendingDropTicks = 0;
        lastProgressAgeTicks = 0;
        bestDistance = Double.MAX_VALUE;
        chunksExamined = 0;
        maxSearchTickNanos = 0L;
        pathRequested = false;
        closeInteractionFallback = false;
        temporarilyRejected.clear();
        activity = switch (worker.job()) {
            case READY -> WorkerActivity.READY;
            case COLLECT -> WorkerActivity.SEARCHING;
            case DEPOSIT -> WorkerActivity.RETURNING;
            case BLOCKED -> WorkerActivity.BLOCKED;
            case COMPLETED, IDLE -> WorkerActivity.IDLE;
        };
    }

    private void tickCollection(ServerLevel level) {
        var targetId = worker.targetBlockId().orElse(null);
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
            finishGoal(level);
            return;
        }

        if (pendingDropTicks > 0) {
            activity = WorkerActivity.COLLECTING;
            pendingDropTicks--;
            if (pendingDropTicks == 0) {
                worker.stopEngineProcesses();
                clearPlan(false);
                worker.setRuntimeState(WorkerRuntimeState.SEARCHING);
            }
            return;
        }

        if (currentTarget != null && !worker.isBreaking()
                && !WorkerPlanner.isCollectable(level, worker, currentTarget, targetId)) {
            worker.stopEngineProcesses();
            clearPlan(false);
        }

        if (currentTarget == null || currentWorkPosition == null) {
            activity = WorkerActivity.SEARCHING;
            worker.setRuntimeState(WorkerRuntimeState.SEARCHING);
            if (scanCooldown > 0) return;
            if (searchCursor == null) searchCursor = new WorkerPlanner.SearchCursor(level, worker);
            Optional<WorkerPlanner.CollectionPlan> plan = searchCursor.next(
                    level, worker, temporarilyRejected.keySet(), SEARCH_BLOCK_BUDGET);
            chunksExamined = searchCursor.chunksExamined();
            maxSearchTickNanos = Math.max(maxSearchTickNanos, searchCursor.maxScanNanos());
            scanCooldown = 0;
            if (plan.isEmpty()) {
                if (searchCursor.exhausted()) {
                    emptyScans++;
                    closeSearchCursor();
                    scanCooldown = 5;
                    if (emptyScans >= MAX_EMPTY_SCANS) block(WorkerBlockReason.NO_MATCHING_BLOCKS);
                }
                return;
            }
            emptyScans = 0;
            currentTarget = plan.orElseThrow().target();
            currentWorkPosition = plan.orElseThrow().workPosition();
            if (!worker.hasInventoryRoomFor(level.getBlockState(currentTarget))) {
                worker.requestDepositOrBlock();
                closeSearchCursor();
                clearPlan(false);
                return;
            }
            activity = WorkerActivity.PATHING;
            worker.setRuntimeState(WorkerRuntimeState.PATHING);
            navigateTo(currentWorkPosition);
        }

        if (currentTarget == null || currentWorkPosition == null) return;
        PathingStatus pathingStatus = worker.pathingStatus();
        if (pathRequested && (pathingStatus == PathingStatus.NO_PATH
                || pathingStatus == PathingStatus.FAILED)) {
            rejectCurrentTarget();
            return;
        }
        double distance = distanceTo(currentWorkPosition);
        boolean canInteract = distance <= ARRIVAL_DISTANCE_SQUARED
                && distanceTo(currentTarget) <= INTERACTION_DISTANCE_SQUARED
                && WorkerPlanner.hasLineOfSight(level, worker, currentTarget);
        if (canInteract) {
            if (pathRequested) {
                worker.stopEngineProcesses();
                pathRequested = false;
            }
            activity = WorkerActivity.BREAKING;
            worker.setRuntimeState(WorkerRuntimeState.BREAKING);
            if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                block(WorkerBlockReason.MOB_GRIEFING_DISABLED);
                return;
            }
            BlockState state = level.getBlockState(currentTarget);
            if (!worker.hasInventoryRoomFor(state)) {
                worker.requestDepositOrBlock();
                closeSearchCursor();
                clearPlan(false);
                return;
            }
            if (!worker.hasCorrectToolFor(currentTarget)) {
                block(WorkerBlockReason.MISSING_REQUIRED_TOOL);
                return;
            }
            if (worker.beginOrContinueBreaking(currentTarget)) {
                worker.configuration().incrementCompleted();
                replanAttempts = 0;
                emptyScans = 0;
                lastProgressAgeTicks = 0;
                pendingDropTicks = 10;
                activity = WorkerActivity.COLLECTING;
                worker.setRuntimeState(WorkerRuntimeState.COLLECTING_DROPS);
                worker.notifyOwner(ChatFormatting.GREEN, "message.baritonehelper.block_collected", worker.completedBlockCount());
                if (!worker.unlimitedCount() && worker.completedBlockCount() >= worker.requestedBlockCount()) {
                    // Drops are given a short real-world pickup window before
                    // the finite job returns to storage.
                    pendingDropTicks = 10;
                }
            }
            return;
        }

        activity = WorkerActivity.PATHING;
        worker.setRuntimeState(WorkerRuntimeState.PATHING);
        if (!pathRequested) {
            navigateTo(currentWorkPosition);
        }
        watchProgress(distance, WorkerBlockReason.STUCK);
    }

    private void finishGoal(ServerLevel level) {
        if (worker.hasCargo()) {
            if (worker.storagePosition().isPresent()) {
                worker.requestDepositOrBlock();
                closeSearchCursor();
                clearPlan(false);
            } else {
                block(WorkerBlockReason.STORAGE_MISSING);
            }
            return;
        }
        worker.stopEngineProcesses();
        worker.markCompleted();
        worker.releaseWorkerTickets();
        activity = WorkerActivity.IDLE;
        worker.setRuntimeState(WorkerRuntimeState.COMPLETED);
    }

    private void tickDeposit(ServerLevel level) {
        closeSearchCursor();
        BlockPos storage = worker.storagePosition().orElse(null);
        if (storage == null) { block(WorkerBlockReason.STORAGE_MISSING); return; }
        if (!worker.storageIsIn(level)) { block(WorkerBlockReason.STORAGE_WRONG_DIMENSION); return; }
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
                // A loaded container can be inside a test encasement or a
                // one-block alcove with no standable neighboring cell.  The
                // entity is already within the server's interaction reach,
                // so deposit in place rather than inventing a teleport.
                currentWorkPosition = worker.blockPosition().immutable();
                closeInteractionFallback = true;
            }
            watchdogTicks = 0;
            bestDistance = Double.MAX_VALUE;
            if (currentWorkPosition == null) { block(WorkerBlockReason.NO_REACHABLE_POSITION); return; }
        }
        double distance = distanceTo(currentWorkPosition);
        if (distance > ARRIVAL_DISTANCE_SQUARED || distanceTo(storage) > INTERACTION_DISTANCE_SQUARED
                || (!closeInteractionFallback && !WorkerPlanner.hasLineOfSight(level, worker, storage))) {
            activity = WorkerActivity.RETURNING;
            worker.setRuntimeState(WorkerRuntimeState.RETURNING_TO_STORAGE);
            if (!pathRequested) {
                navigateTo(currentWorkPosition);
            }
            watchProgress(distance, WorkerBlockReason.STUCK);
            return;
        }
        activity = WorkerActivity.DEPOSITING;
        worker.setRuntimeState(WorkerRuntimeState.DEPOSITING);
        int moved = WorkerStorage.deposit(worker, destination);
        if (!worker.hasCargo()) {
            if (moved > 0) worker.notifyOwner(ChatFormatting.GREEN, "message.baritonehelper.deposited", moved);
            worker.stopEngineProcesses();
            if (!worker.unlimitedCount() && worker.completedBlockCount() >= worker.requestedBlockCount()) {
                worker.markCompleted();
                worker.releaseWorkerTickets();
                worker.setRuntimeState(WorkerRuntimeState.COMPLETED);
            } else {
                worker.markCollecting();
            }
            resetTransientState();
        } else if (moved == 0) block(WorkerBlockReason.STORAGE_FULL);
    }

    /** Primary movement adapter: submit a Baritone goal, never vanilla navigation. */
    private void navigateTo(BlockPos destination) {
        if (pathRequested) return;
        lastNavigationDestination = destination.immutable();
        pathRequested = worker.beginPathTo(destination);
    }

    private void rejectCurrentTarget() {
        if (currentTarget != null) {
            temporarilyRejected.put(currentTarget.asLong(), REJECTED_TARGET_TICKS);
        }
        worker.stopEngineProcesses();
        clearPlan(false);
        scanCooldown = 0;
        activity = WorkerActivity.SEARCHING;
    }

    private double distanceTo(BlockPos position) {
        return worker.distanceToSqr(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5);
    }

    private void watchProgress(double distance, WorkerBlockReason terminalReason) {
        if (pathRequested && worker.pathingStatus() == PathingStatus.CALCULATING) {
            // GameTest and busy servers can advance many ticks while the
            // asynchronous pathfinder is still working in wall-clock time.
            // Only watchdog an executing route, not a valid calculation.
            watchdogTicks = 0;
            return;
        }
        if (distance + 0.04 < bestDistance) {
            bestDistance = distance;
            watchdogTicks = 0;
            lastProgressAgeTicks = 0;
            return;
        }
        if (++watchdogTicks >= WATCHDOG_TICKS) replanOrBlock(terminalReason);
    }

    private void replanOrBlock(WorkerBlockReason terminalReason) {
        if (++replanAttempts >= MAX_REPLAN_ATTEMPTS) { block(terminalReason); return; }
        if (currentTarget != null) temporarilyRejected.put(currentTarget.asLong(), REJECTED_TARGET_TICKS);
        worker.stopEngineProcesses();
        clearPlan(false);
        scanCooldown = 5;
        activity = WorkerActivity.SEARCHING;
    }

    private void block(WorkerBlockReason reason) {
        worker.stopEngineProcesses();
        closeSearchCursor();
        clearPlan(false);
        activity = WorkerActivity.BLOCKED;
        worker.markBlocked(reason);
    }

    private void clearPlan(boolean clearRejected) {
        currentTarget = null;
        currentWorkPosition = null;
        pathRequested = false;
        lastNavigationDestination = null;
        watchdogTicks = 0;
        bestDistance = Double.MAX_VALUE;
        closeInteractionFallback = false;
        if (clearRejected) temporarilyRejected.clear();
    }

    private void closeSearchCursor() {
        if (searchCursor != null) {
            searchCursor.close(worker);
            searchCursor = null;
        }
    }

    private void tickRejectedTargets() {
        temporarilyRejected.replaceAll((position, ticks) -> ticks - 1);
        temporarilyRejected.entrySet().removeIf(entry -> entry.getValue() <= 0);
    }
}
