package dev.dudie.baritonehelper.worker;

import com.mojang.logging.LogUtils;
import dev.dudie.baritonehelper.entity.WorkerEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * Incremental target discovery.  A cursor examines at most one newly opened
 * chunk and a bounded number of blocks per tick; it never rescans a radius as a
 * cubic volume.
 */
public final class WorkerPlanner {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double CURRENT_POSITION_DISTANCE_SQUARED = 2.25;
    private static final double INTERACTION_REACH_SQUARED = 36.0;
    private static final int HORIZONTAL_STANCE_RADIUS = 3;
    private static final int VERTICAL_STANCE_RADIUS = 3;
    private static final int DEFAULT_BLOCK_BUDGET = 4_096;
    private static final int MAX_SEARCH_TICKETS = 4;
    private static final int MAX_CACHED_CANDIDATES = 32;

    private WorkerPlanner() {
    }

    public record CollectionPlan(BlockPos target, BlockPos workPosition) {
        public CollectionPlan {
            target = target.immutable();
            workPosition = workPosition.immutable();
        }
    }

    public static final class SearchCursor {
        private final String dimension;
        private final BlockPos center;
        private final int horizontalRadius;
        private final int verticalRadius;
        private final List<net.minecraft.world.level.ChunkPos> frontier;
        private int frontierIndex;
        private @org.jetbrains.annotations.Nullable net.minecraft.world.level.ChunkPos requestedChunk;
        private @org.jetbrains.annotations.Nullable net.minecraft.world.level.ChunkPos lastScannedChunk;
        private LevelChunk chunk;
        private int localIndex;
        private int chunksExamined;
        private int chunksScanned;
        private int positionsExamined;
        private int matchingBlocks;
        private int candidatesFound;
        private int candidatesRejectedByPolicy;
        private int candidatesRejectedAsUnreachable;
        private long lastScanNanos;
        private long maxScanNanos;
        private boolean exhausted;
        private final Set<Long> searchTicketChunks = new LinkedHashSet<>();
        private final LinkedHashSet<Long> candidateCache = new LinkedHashSet<>();

        public SearchCursor(ServerLevel level, WorkerEntity worker) {
            this.dimension = level.dimension().location().toString();
            this.center = worker.workAreaCenter().immutable();
            this.horizontalRadius = worker.horizontalSearchRadius();
            this.verticalRadius = worker.verticalSearchRadius();
            this.frontier = buildFrontier(center, horizontalRadius, worker.blockPosition());
        }

        /**
         * Advances this cursor.  The caller should invoke it once per worker
         * tick.  A returned plan is reserved by the controller and must be
         * revalidated before interaction.
         */
        public Optional<CollectionPlan> next(
                ServerLevel level,
                WorkerEntity worker,
                Set<Long> temporarilyRejected,
                int blockBudget) {
            lastScanNanos = 0L;
            if (exhausted) {
                return Optional.empty();
            }
            if (!level.dimension().location().toString().equals(dimension)) {
                exhausted = true;
                return Optional.empty();
            }

            ResourceLocation target = worker.targetBlockId().orElse(null);
            Optional<CollectionPlan> cached = pollCachedCandidate(
                    level, worker, temporarilyRejected, target);
            if (cached.isPresent()) {
                releaseSearchTicketsForPath(worker);
                return cached;
            }

            int budget = Math.max(1, Math.min(DEFAULT_BLOCK_BUDGET, blockBudget));
            // A missing frontier chunk is a wait state, not a completed item.
            if (chunk == null) {
                if (requestedChunk == null) {
                    if (frontierIndex >= frontier.size()) {
                        exhausted = candidateCache.isEmpty();
                        return Optional.empty();
                    }
                    requestedChunk = frontier.get(frontierIndex);
                    chunksExamined++;
                    LOGGER.debug("[Baritone Helper/Search] target={} area={} H={} V={} frontier={}/{} request={}",
                            target, center, horizontalRadius, verticalRadius,
                            frontierIndex + 1, frontier.size(), requestedChunk);
                }
                chunk = level.getChunkSource().getChunkNow(
                        requestedChunk.x, requestedChunk.z);
                if (chunk == null) {
                    primeSearchTickets(level, worker);
                    return Optional.empty();
                }
                LOGGER.debug("[Baritone Helper/Search] loaded={}", requestedChunk);
                localIndex = 0;
            }

            // Keep only a small look-ahead window warm.  The current chunk is
            // scanned in order, while the next few frontier chunks can load
            // in parallel without consuming the route-critical ticket budget.
            primeSearchTickets(level, worker);

            BlockPos best = null;
            BlockPos bestWork = null;
            double bestDistance = Double.MAX_VALUE;
            long scanStarted = System.nanoTime();
            int minY = Math.max(level.getMinBuildHeight(), center.getY() - verticalRadius);
            int maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + verticalRadius);
            int maxLocal = Math.max(0, (maxY - minY + 1) * 16 * 16);
            while (localIndex < maxLocal && budget-- > 0) {
                int index = localIndex++;
                int y = minY + index / 256;
                int x = chunk.getPos().getMinBlockX() + (index & 15);
                int z = chunk.getPos().getMinBlockZ() + ((index >> 4) & 15);
                positionsExamined++;
                BlockPos candidate = new BlockPos(x, y, z);
                boolean matches = target != null
                        && BuiltInRegistries.BLOCK.getKey(
                                level.getBlockState(candidate).getBlock()).equals(target);
                if (!matches) continue;
                matchingBlocks++;
                if (!insideWorkArea(candidate)) continue;
                if (temporarilyRejected.contains(candidate.asLong())) {
                    candidatesRejectedAsUnreachable++;
                    continue;
                }
                if (!isCollectable(level, worker, candidate, target)) {
                    candidatesRejectedByPolicy++;
                    continue;
                }
                candidatesFound++;
                cacheCandidate(candidate);
                BlockPos work = nearestWorkPosition(level, worker, candidate).orElse(null);
                if (work == null) {
                    primeCandidateStanceTickets(level, worker, candidate);
                    if (allCandidateStanceChunksLoaded(level, candidate)) {
                        candidateCache.remove(candidate.asLong());
                        candidatesRejectedAsUnreachable++;
                        releaseCandidateStanceTickets(worker, candidate);
                    }
                    continue;
                }
                double distance = worker.distanceToSqr(
                        work.getX() + 0.5, work.getY() + 0.5, work.getZ() + 0.5);
                if (distance < bestDistance) {
                    best = candidate;
                    bestWork = work;
                    bestDistance = distance;
                }
            }

            lastScanNanos = System.nanoTime() - scanStarted;
            maxScanNanos = Math.max(maxScanNanos, lastScanNanos);
            if (localIndex >= maxLocal) {
                LOGGER.debug("[Baritone Helper/Search] scanned={} positions={} matches={} candidates={} maxScanMs={}",
                        requestedChunk, positionsExamined, matchingBlocks, candidatesFound,
                        maxScanNanos / 1_000_000.0);
                lastScannedChunk = requestedChunk;
                chunksScanned++;
                releaseSearchTicket(worker, requestedChunk);
                requestedChunk = null;
                chunk = null;
                localIndex = 0;
                frontierIndex++;
            }
            if (best != null) {
                LOGGER.debug("[Baritone Helper/Search] selected={} stance={}", best, bestWork);
                candidateCache.remove(best.asLong());
                releaseSearchTicketsForPath(worker);
            }
            return best == null ? Optional.empty() : Optional.of(new CollectionPlan(best, bestWork));
        }

        public int chunksExamined() { return chunksExamined; }
        public int chunksScanned() { return chunksScanned; }
        public int positionsExamined() { return positionsExamined; }
        public int matchingBlocks() { return matchingBlocks; }
        public int candidatesFound() { return candidatesFound; }
        public int candidatesRejectedByPolicy() { return candidatesRejectedByPolicy; }
        public int candidatesRejectedAsUnreachable() { return candidatesRejectedAsUnreachable; }
        public long lastScanNanos() { return lastScanNanos; }
        public long maxScanNanos() { return maxScanNanos; }
        public int frontierIndex() { return frontierIndex; }
        public int frontierSize() { return frontier.size(); }
        public int cachedCandidateCount() { return candidateCache.size(); }
        public boolean waitingForChunk() { return requestedChunk != null && chunk == null; }
        public String lastScannedChunk() { return lastScannedChunk == null ? "" : lastScannedChunk.toString(); }
        public String requestedChunk() { return requestedChunk == null ? "" : requestedChunk.toString(); }
        public boolean exhausted() { return exhausted; }

        public void close(WorkerEntity worker) {
            for (long packed : Set.copyOf(searchTicketChunks)) {
                worker.releaseSearchTicket(new net.minecraft.world.level.ChunkPos(packed));
            }
            searchTicketChunks.clear();
            candidateCache.clear();
            requestedChunk = null;
            chunk = null;
        }

        private Optional<CollectionPlan> pollCachedCandidate(
                ServerLevel level,
                WorkerEntity worker,
                Set<Long> temporarilyRejected,
                ResourceLocation target) {
            BlockPos best = null;
            BlockPos bestWork = null;
            double bestDistance = Double.MAX_VALUE;
            for (long packed : Set.copyOf(candidateCache)) {
                BlockPos candidate = BlockPos.of(packed);
                if (temporarilyRejected.contains(packed)
                        || !isCollectable(level, worker, candidate, target)) {
                    candidateCache.remove(packed);
                    continue;
                }
                BlockPos work = nearestWorkPosition(level, worker, candidate).orElse(null);
                if (work == null) {
                    primeCandidateStanceTickets(level, worker, candidate);
                    if (allCandidateStanceChunksLoaded(level, candidate)) {
                        candidateCache.remove(packed);
                        candidatesRejectedAsUnreachable++;
                        releaseCandidateStanceTickets(worker, candidate);
                    }
                    continue;
                }
                double distance = worker.distanceToSqr(
                        work.getX() + 0.5, work.getY() + 0.5, work.getZ() + 0.5);
                if (distance < bestDistance) {
                    best = candidate;
                    bestWork = work;
                    bestDistance = distance;
                }
            }
            if (best == null) return Optional.empty();
            candidateCache.remove(best.asLong());
            return Optional.of(new CollectionPlan(best, bestWork));
        }

        private void cacheCandidate(BlockPos candidate) {
            long packed = candidate.asLong();
            candidateCache.remove(packed);
            candidateCache.add(packed);
            while (candidateCache.size() > MAX_CACHED_CANDIDATES) {
                candidateCache.remove(candidateCache.iterator().next());
            }
        }

        private void primeSearchTickets(ServerLevel level, WorkerEntity worker) {
            int end = Math.min(frontier.size(), frontierIndex + MAX_SEARCH_TICKETS);
            for (int index = frontierIndex; index < end; index++) {
                if (searchTicketChunks.size() >= MAX_SEARCH_TICKETS) break;
                net.minecraft.world.level.ChunkPos candidate = frontier.get(index);
                long packed = candidate.toLong();
                if (searchTicketChunks.contains(packed)
                        || level.getChunkSource().getChunkNow(candidate.x, candidate.z) != null) {
                    continue;
                }
                if (!worker.requestSearchTicket(candidate)) break;
                searchTicketChunks.add(packed);
            }
        }

        private void primeCandidateStanceTickets(
                ServerLevel level, WorkerEntity worker, BlockPos target) {
            int minChunkX = Math.floorDiv(target.getX() - HORIZONTAL_STANCE_RADIUS, 16);
            int maxChunkX = Math.floorDiv(target.getX() + HORIZONTAL_STANCE_RADIUS, 16);
            int minChunkZ = Math.floorDiv(target.getZ() - HORIZONTAL_STANCE_RADIUS, 16);
            int maxChunkZ = Math.floorDiv(target.getZ() + HORIZONTAL_STANCE_RADIUS, 16);
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    if (searchTicketChunks.size() >= MAX_SEARCH_TICKETS) return;
                    net.minecraft.world.level.ChunkPos stanceChunk =
                            new net.minecraft.world.level.ChunkPos(chunkX, chunkZ);
                    long packed = stanceChunk.toLong();
                    if (searchTicketChunks.contains(packed)
                            || level.getChunkSource().getChunkNow(chunkX, chunkZ) != null) {
                        continue;
                    }
                    if (worker.requestSearchTicket(stanceChunk)) {
                        searchTicketChunks.add(packed);
                    }
                }
            }
        }

        private boolean allCandidateStanceChunksLoaded(ServerLevel level, BlockPos target) {
            int minChunkX = Math.floorDiv(target.getX() - HORIZONTAL_STANCE_RADIUS, 16);
            int maxChunkX = Math.floorDiv(target.getX() + HORIZONTAL_STANCE_RADIUS, 16);
            int minChunkZ = Math.floorDiv(target.getZ() - HORIZONTAL_STANCE_RADIUS, 16);
            int maxChunkZ = Math.floorDiv(target.getZ() + HORIZONTAL_STANCE_RADIUS, 16);
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) return false;
                }
            }
            return true;
        }

        private void releaseCandidateStanceTickets(WorkerEntity worker, BlockPos target) {
            int minChunkX = Math.floorDiv(target.getX() - HORIZONTAL_STANCE_RADIUS, 16);
            int maxChunkX = Math.floorDiv(target.getX() + HORIZONTAL_STANCE_RADIUS, 16);
            int minChunkZ = Math.floorDiv(target.getZ() - HORIZONTAL_STANCE_RADIUS, 16);
            int maxChunkZ = Math.floorDiv(target.getZ() + HORIZONTAL_STANCE_RADIUS, 16);
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    net.minecraft.world.level.ChunkPos stanceChunk =
                            new net.minecraft.world.level.ChunkPos(chunkX, chunkZ);
                    if (requestedChunk == null || !requestedChunk.equals(stanceChunk)) {
                        releaseSearchTicket(worker, stanceChunk);
                    }
                }
            }
        }

        private void releaseSearchTicket(WorkerEntity worker,
                net.minecraft.world.level.ChunkPos chunk) {
            if (chunk != null && searchTicketChunks.remove(chunk.toLong())) {
                worker.releaseSearchTicket(chunk);
            }
        }

        private void releaseSearchTicketsForPath(WorkerEntity worker) {
            for (long packed : Set.copyOf(searchTicketChunks)) {
                net.minecraft.world.level.ChunkPos loaded =
                        new net.minecraft.world.level.ChunkPos(packed);
                worker.releaseSearchTicket(loaded);
                searchTicketChunks.remove(packed);
            }
            // Preserve the scan index, but reacquire the chunk if it unloads
            // while the worker follows the promoted target route.
            chunk = null;
        }

        private boolean insideWorkArea(BlockPos position) {
            long dx = (long) position.getX() - center.getX();
            long dz = (long) position.getZ() - center.getZ();
            return dx * dx + dz * dz <= (long) horizontalRadius * horizontalRadius
                    && Math.abs(position.getY() - center.getY()) <= verticalRadius;
        }
    }

    private static List<net.minecraft.world.level.ChunkPos> buildFrontier(
            BlockPos center, int radius, BlockPos workerPosition) {
        int minX = Math.floorDiv(center.getX() - radius, 16);
        int maxX = Math.floorDiv(center.getX() + radius, 16);
        int minZ = Math.floorDiv(center.getZ() - radius, 16);
        int maxZ = Math.floorDiv(center.getZ() + radius, 16);
        List<net.minecraft.world.level.ChunkPos> result = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) result.add(new net.minecraft.world.level.ChunkPos(x, z));
        }
        long workerChunkX = Math.floorDiv(workerPosition.getX(), 16);
        long workerChunkZ = Math.floorDiv(workerPosition.getZ(), 16);
        result.sort(Comparator.<net.minecraft.world.level.ChunkPos>comparingLong(pos -> {
            long workerDx = pos.x - workerChunkX;
            long workerDz = pos.z - workerChunkZ;
            return workerDx * workerDx + workerDz * workerDz;
        }).thenComparingInt(pos -> pos.x).thenComparingInt(pos -> pos.z));
        return result;
    }

    /** Compatibility helper for small callers; production code uses SearchCursor. */
    public static Optional<CollectionPlan> nearestCollectable(
            ServerLevel level,
            WorkerEntity worker,
            Set<Long> temporarilyRejected) {
        return new SearchCursor(level, worker).next(level, worker, temporarilyRejected, DEFAULT_BLOCK_BUDGET);
    }

    public static Optional<BlockPos> nearestWorkPosition(
            ServerLevel level,
            WorkerEntity worker,
            BlockPos target) {
        double targetDistance = worker.distanceToSqr(
                target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        boolean closeContainer = level.getBlockEntity(target) instanceof Container;
        if (targetDistance <= CURRENT_POSITION_DISTANCE_SQUARED
                && canStandAt(level, worker.blockPosition())
                && !worker.isInsideNoEnter(worker.blockPosition())
                && (closeContainer || hasLineOfSight(level, worker, target))) {
            return Optional.of(worker.blockPosition().immutable());
        }

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        if (worker.isInsideNoEnter(target) || worker.isInsideNoModify(target)) {
            return Optional.empty();
        }
        for (int dy = -VERTICAL_STANCE_RADIUS; dy <= VERTICAL_STANCE_RADIUS; dy++) {
            for (int dx = -HORIZONTAL_STANCE_RADIUS; dx <= HORIZONTAL_STANCE_RADIUS; dx++) {
                for (int dz = -HORIZONTAL_STANCE_RADIUS; dz <= HORIZONTAL_STANCE_RADIUS; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos candidate = target.offset(dx, dy, dz);
                    if (worker.isInsideNoEnter(candidate) || !canStandAt(level, candidate)) continue;
                    Vec3 candidateEye = eyePosition(worker, candidate);
                    if (candidateEye.distanceToSqr(Vec3.atCenterOf(target)) > INTERACTION_REACH_SQUARED
                            || !hasLineOfSight(level, worker, target, candidate)) continue;
                    double distance = worker.distanceToSqr(
                            candidate.getX() + 0.5, candidate.getY() + 0.5, candidate.getZ() + 0.5);
                    if (distance < bestDistance) {
                        best = candidate.immutable();
                        bestDistance = distance;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    public static boolean canStandAt(ServerLevel level, BlockPos feet) {
        if (!level.hasChunkAt(feet) || !level.hasChunkAt(feet.above()) || !level.hasChunkAt(feet.below())) {
            return false;
        }
        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(feet.above());
        BlockPos floorPos = feet.below();
        BlockState floorState = level.getBlockState(floorPos);
        return feetState.getCollisionShape(level, feet).isEmpty()
                && headState.getCollisionShape(level, feet.above()).isEmpty()
                && (floorState.isFaceSturdy(level, floorPos, Direction.UP)
                    || !feetState.getFluidState().isEmpty());
    }

    public static boolean hasLineOfSight(ServerLevel level, WorkerEntity worker, BlockPos target) {
        return hasLineOfSight(level, worker, target, worker.blockPosition());
    }

    private static boolean hasLineOfSight(ServerLevel level, WorkerEntity worker, BlockPos target, BlockPos work) {
        Vec3 eye = eyePosition(worker, work);
        BlockHitResult hit = level.clip(new ClipContext(
                eye, Vec3.atCenterOf(target),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, worker));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(target);
    }

    private static Vec3 eyePosition(WorkerEntity worker, BlockPos work) {
        return new Vec3(
                work.getX() + 0.5,
                work.getY() + worker.getEyeHeight(),
                work.getZ() + 0.5);
    }

    public static boolean isCollectable(
            ServerLevel level,
            WorkerEntity worker,
            BlockPos position,
            ResourceLocation target) {
        if (target == null || !level.hasChunkAt(position) || level.getBlockEntity(position) != null
                || worker.isInsideNoModify(position) || worker.isInsideNoEnter(position)) return false;
        BlockState state = level.getBlockState(position);
        if (state.isAir() || !BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(target)) return false;
        return state.getDestroySpeed(level, position) >= 0.0F;
    }
}
