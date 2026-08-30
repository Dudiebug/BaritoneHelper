package dev.dudie.baritonehelper.worker;

import dev.dudie.baritonehelper.entity.WorkerEntity;
import java.util.ArrayList;
import java.util.Comparator;
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

/**
 * Incremental target discovery.  A cursor examines at most one newly opened
 * chunk and a bounded number of blocks per tick; it never rescans a radius as a
 * cubic volume.
 */
public final class WorkerPlanner {
    private static final double CURRENT_POSITION_DISTANCE_SQUARED = 2.25;
    private static final int DEFAULT_BLOCK_BUDGET = 4_096;

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
        private LevelChunk chunk;
        private int localIndex;
        private int chunksExamined;
        private int positionsExamined;
        private boolean exhausted;

        public SearchCursor(ServerLevel level, WorkerEntity worker) {
            this.dimension = level.dimension().location().toString();
            this.center = worker.workAreaCenter().immutable();
            this.horizontalRadius = worker.horizontalSearchRadius();
            this.verticalRadius = worker.verticalSearchRadius();
            this.frontier = buildFrontier(center, horizontalRadius);
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
            if (exhausted || !level.dimension().location().toString().equals(dimension)) {
                return Optional.empty();
            }

            int budget = Math.max(1, Math.min(DEFAULT_BLOCK_BUDGET, blockBudget));
            // One new frontier chunk per call bounds chunk acquisition and
            // leaves the rest of the server tick free for entity simulation.
            if (chunk == null) {
                if (frontierIndex >= frontier.size()) {
                    exhausted = true;
                    return Optional.empty();
                }
                var next = frontier.get(frontierIndex++);
                chunksExamined++;
                chunk = level.getChunkSource().getChunkNow(next.x, next.z);
                localIndex = 0;
                if (chunk == null) {
                    return Optional.empty();
                }
            }

            BlockPos best = null;
            BlockPos bestWork = null;
            double bestDistance = Double.MAX_VALUE;
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
                if (!insideWorkArea(candidate)
                        || temporarilyRejected.contains(candidate.asLong())
                        || !isCollectable(level, worker, candidate, worker.targetBlockId().orElse(null))) {
                    continue;
                }
                BlockPos work = nearestWorkPosition(level, worker, candidate).orElse(null);
                if (work == null) {
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

            if (localIndex >= maxLocal) {
                chunk = null;
                localIndex = 0;
            }
            return best == null ? Optional.empty() : Optional.of(new CollectionPlan(best, bestWork));
        }

        public int chunksExamined() { return chunksExamined; }
        public int positionsExamined() { return positionsExamined; }
        public boolean exhausted() { return exhausted; }

        private boolean insideWorkArea(BlockPos position) {
            long dx = (long) position.getX() - center.getX();
            long dz = (long) position.getZ() - center.getZ();
            return dx * dx + dz * dz <= (long) horizontalRadius * horizontalRadius
                    && Math.abs(position.getY() - center.getY()) <= verticalRadius;
        }
    }

    private static List<net.minecraft.world.level.ChunkPos> buildFrontier(BlockPos center, int radius) {
        int minX = Math.floorDiv(center.getX() - radius, 16);
        int maxX = Math.floorDiv(center.getX() + radius, 16);
        int minZ = Math.floorDiv(center.getZ() - radius, 16);
        int maxZ = Math.floorDiv(center.getZ() + radius, 16);
        List<net.minecraft.world.level.ChunkPos> result = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) result.add(new net.minecraft.world.level.ChunkPos(x, z));
        }
        long centerChunkX = Math.floorDiv(center.getX(), 16);
        long centerChunkZ = Math.floorDiv(center.getZ(), 16);
        result.sort(Comparator.comparingLong(pos -> {
            long dx = pos.x - centerChunkX;
            long dz = pos.z - centerChunkZ;
            return dx * dx + dz * dz;
        }));
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
        for (Direction direction : Direction.values()) {
            BlockPos candidate = target.relative(direction);
            if (worker.isInsideNoEnter(candidate) || !canStandAt(level, candidate)) continue;
            if (worker.isInsideNoEnter(target) || worker.isInsideNoModify(target)) continue;
            if (!hasLineOfSight(level, worker, target, candidate)) continue;
            double distance = worker.distanceToSqr(
                    candidate.getX() + 0.5, candidate.getY() + 0.5, candidate.getZ() + 0.5);
            if (distance < bestDistance) {
                best = candidate.immutable();
                bestDistance = distance;
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

    private static boolean hasLineOfSight(ServerLevel level, WorkerEntity worker, BlockPos target, BlockPos ignoredWork) {
        BlockHitResult hit = level.clip(new ClipContext(
                worker.getEyePosition(), Vec3.atCenterOf(target),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, worker));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(target);
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
