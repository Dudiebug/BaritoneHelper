package dev.dudie.baritonehelper.gametest;

import dev.dudie.baritonehelper.BaritoneHelper;
import dev.dudie.baritonehelper.entity.WorkerEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import dev.dudie.baritonehelper.worker.WorkerBlockReason;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BaritoneHelper.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorkerTicketLifecycleGameTests {
    private static final int VIEW_RADIUS = WorkerEntity.ACTIVE_VIEW_RADIUS;
    private static final int VIEW_DIAMETER = VIEW_RADIUS * 2 + 1;

    private WorkerTicketLifecycleGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    public static void activeWorkerKeepsExactMovingWindowAndInactiveRemovalReleasesTickets(
            GameTestHelper helper) {
        helper.setBlock(1, 1, 1, Blocks.STONE);
        WorkerEntity worker = helper.spawn(
                BaritoneHelper.BARITONE_HELPER_ENTITY.get(), 1, 2, 1);

        worker.ensureWorkerTickets();
        assertNoTickets(helper, worker, "idle worker");

        worker.configureTarget(
                BuiltInRegistries.BLOCK.getKey(Blocks.DIRT), worker.blockPosition());
        worker.startJob();
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
                    "active workers must retain the full view window");
            helper.assertValueEqual(worker.simulationTicketCount(), 25,
                    "active workers must retain a radius-2 simulation footprint");
            worker.stopJob();
            assertNoTickets(helper, worker, "stopped worker");
            worker.startJob();
            worker.markBlocked(WorkerBlockReason.STUCK);
            assertNoTickets(helper, worker, "blocked worker");
            worker.startJob();
            worker.markCompleted();
            assertNoTickets(helper, worker, "completed worker");
            worker.startJob();
            worker.remove(Entity.RemovalReason.DISCARDED);
            assertNoTickets(helper, worker, "removed worker");
            helper.succeed();
        });
    }

    private static void assertWindow(GameTestHelper helper, WorkerEntity worker) {
        int expectedCount = VIEW_DIAMETER * VIEW_DIAMETER;
        ChunkPos center = new ChunkPos(worker.blockPosition());
        var chunks = worker.loadedTicketChunks();
        helper.assertValueEqual(chunks.size(), expectedCount,
                "worker must own exactly 169 Chebyshev-window chunks");
        helper.assertValueEqual(worker.workerTicketCount(), expectedCount,
                "worker ticket count must match loadedTicketChunks()");
        for (int x = center.x - VIEW_RADIUS; x <= center.x + VIEW_RADIUS; x++) {
            for (int z = center.z - VIEW_RADIUS; z <= center.z + VIEW_RADIUS; z++) {
                helper.assertTrue(chunks.contains(ChunkPos.asLong(x, z)),
                        "missing loaded ticket at " + x + "," + z);
            }
        }
    }

    private static void assertNoTickets(GameTestHelper helper, WorkerEntity worker, String state) {
        helper.assertValueEqual(worker.workerTicketCount(), 0,
                state + " must release view tickets");
        helper.assertValueEqual(worker.simulationTicketCount(), 0,
                state + " must release simulation tickets");
        helper.assertValueEqual(worker.totalTicketCount(), 0,
                state + " must release every tracked ticket");
    }
}
