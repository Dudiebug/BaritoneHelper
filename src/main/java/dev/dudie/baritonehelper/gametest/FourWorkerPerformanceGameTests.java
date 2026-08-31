package dev.dudie.baritonehelper.gametest;

import com.mojang.logging.LogUtils;
import dev.dudie.baritonehelper.BaritoneHelper;
import dev.dudie.baritonehelper.entity.WorkerEntity;
import dev.dudie.baritonehelper.worker.WorkerActionResult;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BaritoneHelper.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FourWorkerPerformanceGameTests {
    private static final long AGGREGATE_WORKER_P95_BUDGET_NANOS = 2_000_000L;

    private FourWorkerPerformanceGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "zzzzFourWorkerPerformance", timeoutTicks = 16000)
    public static void fourActiveWorkersRemainFairAndWithinTickBudget(GameTestHelper helper) {
        for (var entity : helper.getLevel().getAllEntities()) {
            if (entity instanceof WorkerEntity worker) worker.discard();
        }
        for (int x = 1; x <= 19; x++) {
            for (int z = 0; z <= 14; z++) helper.setBlock(x, 1, z, Blocks.STONE);
        }

        List<BlockPos> starts = List.of(
                new BlockPos(1, 2, 1),
                new BlockPos(1, 2, 5),
                new BlockPos(1, 2, 9),
                new BlockPos(1, 2, 13));
        List<Block> targetBlocks = List.of(
                Blocks.REDSTONE_BLOCK,
                Blocks.EMERALD_BLOCK,
                Blocks.DIAMOND_BLOCK,
                Blocks.GOLD_BLOCK);
        List<BlockPos> targets = starts.stream()
                .map(start -> new BlockPos(17, 2, start.getZ()))
                .toList();
        for (int index = 0; index < targets.size(); index++) {
            helper.setBlock(targets.get(index), targetBlocks.get(index));
        }

        List<WorkerEntity> workers = starts.stream()
                .map(start -> helper.spawn(
                        BaritoneHelper.BARITONE_HELPER_ENTITY.get(),
                        start.getX(), start.getY(), start.getZ()))
                .toList();
        for (int index = 0; index < workers.size(); index++) {
            WorkerEntity worker = workers.get(index);
            worker.configureTarget(
                    BuiltInRegistries.BLOCK.getKey(targetBlocks.get(index)),
                    helper.absolutePos(starts.get(index)));
            worker.setWorkArea(helper.absolutePos(starts.get(index)), 32, 16);
            helper.assertValueEqual(worker.startJob(), WorkerActionResult.STARTED,
                    "worker " + index + " starts");
        }

        helper.succeedWhen(() -> {
            helper.assertTrue(targets.stream().allMatch(target -> helper.getBlockState(target).isAir()),
                    "all four workers must receive executor time and collect their target");
            long aggregateP95 = workers.stream().mapToLong(WorkerEntity::workerTickP95Nanos).sum();
            List<Double> individualP95 = workers.stream()
                    .map(worker -> worker.workerTickP95Nanos() / 1_000_000.0)
                    .toList();
            helper.assertTrue(workers.stream().allMatch(worker -> worker.workerTickP95Nanos() > 0L),
                    "all workers must publish tick samples");
            helper.assertTrue(aggregateP95 <= AGGREGATE_WORKER_P95_BUDGET_NANOS,
                    "aggregate worker p95 exceeded 2 ms: " + aggregateP95 / 1_000_000.0
                            + " ms; individual=" + individualP95);
            helper.assertTrue(workers.stream().allMatch(worker -> worker.maxSearchTickNanos() < 50_000_000L),
                    "a worker search step exceeded 50 ms");
            LogUtils.getLogger().info(
                    "[Baritone Helper/Four Worker Benchmark] aggregateWorkerP95Ms={}, individualP95Ms={}",
                    aggregateP95 / 1_000_000.0,
                    individualP95);
            workers.forEach(WorkerEntity::stopJob);
            helper.succeed();
        });
    }
}
