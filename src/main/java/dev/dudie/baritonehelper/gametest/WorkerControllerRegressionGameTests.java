package dev.dudie.baritonehelper.gametest;

import dev.dudie.baritonehelper.BaritoneHelper;
import dev.dudie.baritonehelper.entity.WorkerEntity;
import dev.dudie.baritonehelper.worker.WorkerActionResult;
import dev.dudie.baritonehelper.worker.WorkerBlockReason;
import dev.dudie.baritonehelper.worker.WorkerJob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BaritoneHelper.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorkerControllerRegressionGameTests {
    private WorkerControllerRegressionGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void targetSelectionReplacesPreviousTargetWithoutStarting(
            GameTestHelper helper) {
        supportWorker(helper);
        WorkerEntity worker = spawnWorker(helper);
        BlockPos firstOrigin = helper.absolutePos(new BlockPos(2, 2, 1));
        BlockPos secondOrigin = helper.absolutePos(new BlockPos(3, 2, 1));
        ResourceLocation dirt = blockId(Blocks.DIRT);
        ResourceLocation iron = blockId(Blocks.IRON_BLOCK);

        worker.configureTarget(dirt, firstOrigin);
        helper.assertValueEqual(worker.job(), WorkerJob.READY, "configured job state");
        helper.assertValueEqual(worker.targetBlockId().orElseThrow(), dirt, "first target");

        worker.configureTarget(iron, secondOrigin);
        helper.assertValueEqual(worker.job(), WorkerJob.READY, "replacement job state");
        helper.assertValueEqual(worker.targetBlockId().orElseThrow(), iron, "replacement target");
        helper.assertValueEqual(worker.jobOrigin(), secondOrigin, "replacement origin");
        helper.assertValueEqual(worker.workerTicketCount(), 0, "stopped configuration tickets");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void startAndStopAreExplicitIdempotentAndRetainConfiguration(
            GameTestHelper helper) {
        supportWorker(helper);
        WorkerEntity worker = spawnWorker(helper);
        BlockPos targetOrigin = helper.absolutePos(new BlockPos(3, 2, 1));
        BlockPos storage = helper.absolutePos(new BlockPos(2, 1, 2));
        ResourceLocation iron = blockId(Blocks.IRON_BLOCK);
        worker.configureTarget(iron, targetOrigin);
        worker.assignStorage(helper.getLevel(), storage);

        helper.assertValueEqual(worker.startJob(), WorkerActionResult.STARTED, "start result");
        helper.assertValueEqual(worker.job(), WorkerJob.COLLECT, "running state");
        helper.assertTrue(worker.workerTicketCount() > 0, "running tickets");
        helper.assertValueEqual(
                worker.startJob(), WorkerActionResult.ALREADY_RUNNING, "duplicate start result");

        helper.assertValueEqual(worker.stopJob(), WorkerActionResult.STOPPED, "stop result");
        helper.assertValueEqual(worker.job(), WorkerJob.READY, "stopped state");
        helper.assertValueEqual(worker.targetBlockId().orElseThrow(), iron, "retained target");
        helper.assertValueEqual(worker.storagePosition().orElseThrow(), storage, "retained storage");
        helper.assertValueEqual(worker.workerTicketCount(), 0, "released tickets");
        helper.assertTrue(worker.getNavigation().isDone(), "stopped navigation");
        helper.assertValueEqual(
                worker.stopJob(), WorkerActionResult.ALREADY_STOPPED, "duplicate stop result");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void startWithoutTargetReturnsVisibleFailureState(GameTestHelper helper) {
        supportWorker(helper);
        WorkerEntity worker = spawnWorker(helper);

        helper.assertValueEqual(
                worker.startJob(), WorkerActionResult.NO_TARGET, "missing-target result");
        helper.assertValueEqual(worker.job(), WorkerJob.IDLE, "missing-target state");
        helper.assertValueEqual(
                worker.blockReason(), WorkerBlockReason.NO_TARGET, "missing-target reason");
        helper.assertValueEqual(worker.workerTicketCount(), 0, "missing-target tickets");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void clearTargetStopsWorkerAndRemovesPlaceholderEquivalent(
            GameTestHelper helper) {
        supportWorker(helper);
        WorkerEntity worker = spawnWorker(helper);
        worker.configureTarget(
                blockId(Blocks.IRON_BLOCK),
                helper.absolutePos(new BlockPos(2, 2, 1)));
        worker.startJob();

        helper.assertValueEqual(
                worker.clearTarget(), WorkerActionResult.TARGET_CLEARED, "clear result");
        helper.assertTrue(worker.targetBlockId().isEmpty(), "target must be empty");
        helper.assertValueEqual(worker.job(), WorkerJob.IDLE, "cleared state");
        helper.assertValueEqual(worker.workerTicketCount(), 0, "cleared tickets");
        helper.succeed();
    }

    private static WorkerEntity spawnWorker(GameTestHelper helper) {
        return helper.spawn(
                BaritoneHelper.BARITONE_HELPER_ENTITY.get(),
                1,
                2,
                1);
    }

    private static void supportWorker(GameTestHelper helper) {
        helper.setBlock(1, 1, 1, Blocks.STONE);
    }

    private static ResourceLocation blockId(net.minecraft.world.level.block.Block block) {
        return BuiltInRegistries.BLOCK.getKey(block);
    }
}
