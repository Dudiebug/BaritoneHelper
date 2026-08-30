package dev.dudie.baritonehelper.worker;

import dev.dudie.baritonehelper.entity.WorkerEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class WorkerPlanner {
    public static final int HORIZONTAL_RANGE = 16;
    public static final int VERTICAL_RANGE = 8;

    private WorkerPlanner() {
    }

    public record CollectionPlan(BlockPos target, BlockPos workPosition) {
        public CollectionPlan {
            target = target.immutable();
            workPosition = workPosition.immutable();
        }
    }

    public static Optional<CollectionPlan> nearestCollectable(
            ServerLevel level,
            WorkerEntity worker,
            Set<Long> temporarilyRejected) {
        ResourceLocation targetId = worker.targetBlockId().orElse(null);
        if (targetId == null || worker.isExcluded(targetId)) {
            return Optional.empty();
        }

        BlockPos origin = worker.jobOrigin();
        CollectionPlan best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int y = origin.getY() - VERTICAL_RANGE; y <= origin.getY() + VERTICAL_RANGE; y++) {
            for (int x = origin.getX() - HORIZONTAL_RANGE; x <= origin.getX() + HORIZONTAL_RANGE; x++) {
                for (int z = origin.getZ() - HORIZONTAL_RANGE; z <= origin.getZ() + HORIZONTAL_RANGE; z++) {
                    BlockPos candidate = new BlockPos(x, y, z);
                    if (temporarilyRejected.contains(candidate.asLong())
                            || !isCollectable(level, worker, candidate, targetId)) {
                        continue;
                    }

                    BlockPos workPosition = nearestWorkPosition(level, worker, candidate)
                            .orElse(null);
                    if (workPosition == null) {
                        continue;
                    }

                    double distance = worker.distanceToSqr(
                            workPosition.getX() + 0.5,
                            workPosition.getY(),
                            workPosition.getZ() + 0.5);
                    if (distance < bestDistance) {
                        best = new CollectionPlan(candidate, workPosition);
                        bestDistance = distance;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    public static Optional<BlockPos> nearestWorkPosition(
            ServerLevel level,
            WorkerEntity worker,
            BlockPos target) {
        List<BlockPos> candidates = new ArrayList<>(9);
        if (worker.blockPosition().distManhattan(target) <= 3) {
            candidates.add(worker.blockPosition());
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            candidates.add(target.relative(direction));
        }
        candidates.add(target.above());
        candidates.add(target.below());

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos candidate : candidates) {
            if (!canStandAt(level, worker, candidate)) {
                continue;
            }
            double distance = worker.distanceToSqr(
                    candidate.getX() + 0.5,
                    candidate.getY(),
                    candidate.getZ() + 0.5);
            if (distance < bestDistance) {
                best = candidate.immutable();
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    public static boolean canStandAt(
            ServerLevel level,
            WorkerEntity worker,
            BlockPos feet) {
        if (!level.hasChunkAt(feet)
                || !level.hasChunkAt(feet.above())
                || !level.hasChunkAt(feet.below())) {
            return false;
        }

        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(feet.above());
        BlockPos floorPos = feet.below();
        BlockState floorState = level.getBlockState(floorPos);
        if (!feetState.getCollisionShape(level, feet).isEmpty()
                || !headState.getCollisionShape(level, feet.above()).isEmpty()
                || !floorState.isFaceSturdy(level, floorPos, Direction.UP)) {
            return false;
        }

        AABB currentBox = worker.getBoundingBox();
        Vec3 destination = Vec3.atBottomCenterOf(feet);
        Vec3 current = worker.position();
        AABB destinationBox = currentBox.move(
                destination.x - current.x,
                destination.y - current.y,
                destination.z - current.z);
        return level.noCollision(worker, destinationBox);
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
