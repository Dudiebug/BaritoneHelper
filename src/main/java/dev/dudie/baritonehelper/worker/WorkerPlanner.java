package dev.dudie.baritonehelper.worker;

import dev.dudie.baritonehelper.entity.WorkerEntity;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Storage interaction geometry only. Collection discovery and target ordering
 * are delegated to MineProcess.
 */
public final class WorkerPlanner {
    private static final double CURRENT_POSITION_DISTANCE_SQUARED = 2.25;
    private static final double INTERACTION_REACH_SQUARED = 36.0;
    private static final int HORIZONTAL_STANCE_RADIUS = 3;
    private static final int VERTICAL_STANCE_RADIUS = 3;

    private WorkerPlanner() {
    }

    /** Compatibility payload retained for dashboard/test callers; not used for collection. */
    public record CollectionPlan(BlockPos target, BlockPos workPosition) {
        public CollectionPlan {
            target = target.immutable();
            workPosition = workPosition.immutable();
        }
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

    private static boolean hasLineOfSight(
            ServerLevel level, WorkerEntity worker, BlockPos target, BlockPos work) {
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

    /** Compatibility predicate for existing diagnostics; MineProcess owns collection use. */
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
