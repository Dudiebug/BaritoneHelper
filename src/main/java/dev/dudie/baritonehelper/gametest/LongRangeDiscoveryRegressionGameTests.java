package dev.dudie.baritonehelper.gametest;

import dev.dudie.baritonehelper.BaritoneHelper;
import dev.dudie.baritonehelper.entity.WorkerEntity;
import dev.dudie.baritonehelper.worker.WorkerPlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BaritoneHelper.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LongRangeDiscoveryRegressionGameTests {
    private static final BlockPos WORKER = new BlockPos(1, 2, 1);

    private LongRangeDiscoveryRegressionGameTests() {
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            timeoutTicks = 420)
    public static void unloadedFrontierChunkIsRetriedAndCollected(GameTestHelper helper) {
        buildFloor(helper, 1, 96, -1, 2);
        BlockPos target = new BlockPos(96, 2, 1);
        helper.setBlock(target, Blocks.IRON_BLOCK);
        WorkerEntity worker = spawnWorker(helper);
        BlockPos center = helper.absolutePos(WORKER);
        worker.configureTarget(blockId(Blocks.IRON_BLOCK), center);
        worker.setWorkArea(center, 128, 64);

        helper.runAfterDelay(25, () -> {
            ChunkPos targetChunk = new ChunkPos(helper.absolutePos(target));
            helper.assertTrue(
                    helper.getLevel().getChunkSource().getChunkNow(targetChunk.x, targetChunk.z) == null,
                    "target chunk must be unloaded before the job starts");
            helper.assertValueEqual(worker.startJob().name(), "STARTED", "job starts");
        });

        helper.runAfterDelay(390, () -> {
            String diagnostic = diagnostic(worker, target);
            helper.assertTrue(helper.getBlockState(target).isAir(), diagnostic);
            helper.succeed();
        });
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            timeoutTicks = 260)
    public static void occludedTargetUsesReachableFutureInteractionPosition(GameTestHelper helper) {
        buildFloor(helper, 1, 12, -1, 3);
        for (int z = 0; z <= 2; z++) {
            helper.setBlock(4, 2, z, Blocks.BEDROCK);
            helper.setBlock(4, 3, z, Blocks.BEDROCK);
        }
        BlockPos target = new BlockPos(10, 2, 1);
        helper.setBlock(target, Blocks.IRON_BLOCK);
        WorkerEntity worker = spawnWorker(helper);
        BlockPos absoluteTarget = helper.absolutePos(target);
        helper.assertFalse(
                WorkerPlanner.hasLineOfSight(helper.getLevel(), worker, absoluteTarget),
                "the target must not be visible from the worker's current position");

        worker.beginCollection(blockId(Blocks.IRON_BLOCK), helper.absolutePos(WORKER));

        helper.runAfterDelay(240, () -> {
            String diagnostic = diagnostic(worker, target);
            helper.assertTrue(helper.getBlockState(target).isAir(), diagnostic);
            helper.succeed();
        });
    }

    private static WorkerEntity spawnWorker(GameTestHelper helper) {
        helper.setBlock(WORKER.getX(), 1, WORKER.getZ(), Blocks.STONE);
        return helper.spawn(
                BaritoneHelper.BARITONE_HELPER_ENTITY.get(),
                WORKER.getX(),
                WORKER.getY(),
                WORKER.getZ());
    }

    private static void buildFloor(
            GameTestHelper helper,
            int minX,
            int maxX,
            int minZ,
            int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
            }
        }
    }

    private static ResourceLocation blockId(net.minecraft.world.level.block.Block block) {
        return BuiltInRegistries.BLOCK.getKey(block);
    }

    private static String diagnostic(WorkerEntity worker, BlockPos target) {
        return "target=" + target
                + ", worker=" + worker.position()
                + ", job=" + worker.job()
                + ", activity=" + worker.activity()
                + ", reason=" + worker.blockReason()
                + ", currentTarget=" + worker.currentTarget()
                + ", workPosition=" + worker.currentWorkPosition()
                + ", chunksExamined=" + worker.chunksExamined()
                + ", tickets=" + worker.workerTicketCount();
    }
}
