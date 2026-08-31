package dev.dudie.baritonehelper.gametest;

import dev.dudie.baritonehelper.BaritoneHelper;
import dev.dudie.baritonehelper.entity.WorkerEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BaritoneHelper.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorkerTicketLifecycleGameTests {
    private static final int VIEW_RADIUS = 12;
    private static final int VIEW_DIAMETER = VIEW_RADIUS * 2 + 1;

    private WorkerTicketLifecycleGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    public static void idleWorkerKeepsExactMovingWindowAndRemovalReleasesTickets(
            GameTestHelper helper) {
        helper.setBlock(1, 1, 1, Blocks.STONE);
        WorkerEntity worker = helper.spawn(
                BaritoneHelper.BARITONE_HELPER_ENTITY.get(), 1, 2, 1);

        worker.ensureWorkerTickets();
        assertWindow(helper, worker);

        ChunkPos oldCenter = new ChunkPos(worker.blockPosition());
        worker.setPos(
                (oldCenter.x + VIEW_RADIUS + 1) * 16.0 + 0.5,
                worker.getY(),
                (oldCenter.z - VIEW_RADIUS - 1) * 16.0 + 0.5);
        worker.ensureWorkerTickets();
        assertWindow(helper, worker);

        helper.runAfterDelay(1, () -> {
            helper.assertValueEqual(worker.workerTicketCount(), VIEW_DIAMETER * VIEW_DIAMETER,
                    "idle workers must retain the full window");
            worker.remove(Entity.RemovalReason.DISCARDED);
            helper.assertValueEqual(worker.totalTicketCount(), 0,
                    "removal must release every worker ticket");
            helper.succeed();
        });
    }

    private static void assertWindow(GameTestHelper helper, WorkerEntity worker) {
        int expectedCount = VIEW_DIAMETER * VIEW_DIAMETER;
        ChunkPos center = new ChunkPos(worker.blockPosition());
        var chunks = worker.loadedTicketChunks();
        helper.assertValueEqual(chunks.size(), expectedCount,
                "worker must own exactly 625 Chebyshev-window chunks");
        helper.assertValueEqual(worker.workerTicketCount(), expectedCount,
                "worker ticket count must match loadedTicketChunks()");
        for (int x = center.x - VIEW_RADIUS; x <= center.x + VIEW_RADIUS; x++) {
            for (int z = center.z - VIEW_RADIUS; z <= center.z + VIEW_RADIUS; z++) {
                helper.assertTrue(chunks.contains(ChunkPos.asLong(x, z)),
                        "missing loaded ticket at " + x + "," + z);
            }
        }
    }
}
