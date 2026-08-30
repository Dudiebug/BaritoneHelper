package dev.dudie.baritonehelper.worker;

import dev.dudie.baritonehelper.entity.WorkerEntity;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public final class WorkerPlanner {
    public static final int HORIZONTAL_RANGE = 16;
    public static final int VERTICAL_RANGE = 8;

    private WorkerPlanner() {
    }

    public static Optional<BlockPos> nearestCollectable(
            ServerLevel level,
            WorkerEntity worker,
            Set<Long> temporarilyRejected) {
        ResourceLocation target = worker.targetBlockId().orElse(null);
        if (target == null || worker.isExcluded(target)) {
            return Optional.empty();
        }

        BlockPos origin = worker.jobOrigin();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int y = origin.getY() - VERTICAL_RANGE; y <= origin.getY() + VERTICAL_RANGE; y++) {
            for (int x = origin.getX() - HORIZONTAL_RANGE; x <= origin.getX() + HORIZONTAL_RANGE; x++) {
                for (int z = origin.getZ() - HORIZONTAL_RANGE; z <= origin.getZ() + HORIZONTAL_RANGE; z++) {
                    BlockPos candidate = new BlockPos(x, y, z);
                    if (temporarilyRejected.contains(candidate.asLong()) || !level.hasChunkAt(candidate)) {
                        continue;
                    }
                    if (!isCollectable(level, worker, candidate, target)) {
                        continue;
                    }

                    double distance = worker.distanceToSqr(
                            candidate.getX() + 0.5,
                            candidate.getY() + 0.5,
                            candidate.getZ() + 0.5);
                    if (distance < bestDistance) {
                        best = candidate;
                        bestDistance = distance;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    public static boolean isCollectable(
            ServerLevel level,
            WorkerEntity worker,
            BlockPos position,
            ResourceLocation target) {
        if (!level.hasChunkAt(position) || level.getBlockEntity(position) != null) {
            return false;
        }

        BlockState state = level.getBlockState(position);
        if (state.isAir()
                || !BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(target)
                || worker.isExcluded(target)) {
            return false;
        }

        return state.getDestroySpeed(level, position) >= 0.0F;
    }
}
