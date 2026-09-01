package dev.dudie.baritonehelper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorkerTicketFootprintContractTest {
    @Test
    void activeWindowIsSmallAndInactiveWorkersReleaseIt() throws IOException {
        String worker = Files.readString(Path.of(
                "src/main/java/dev/dudie/baritonehelper/entity/WorkerEntity.java"))
                .replace("\r\n", "\n");

        assertTrue(worker.contains("ACTIVE_VIEW_RADIUS = 6"));
        assertTrue(worker.contains("SIMULATION_RADIUS = 2"));
        assertTrue(worker.contains("MAX_SEARCH_TICKETS = 4"));
        assertTrue(worker.contains("VIEW_TICKET_COUNT =\n            (ACTIVE_VIEW_RADIUS * 2 + 1) * (ACTIVE_VIEW_RADIUS * 2 + 1)"));
        assertTrue(worker.contains("MAX_WORKER_TICKETS = VIEW_TICKET_COUNT + MAX_SEARCH_TICKETS"));
        assertTrue(worker.contains("if (!job.activelyWorks() || pickupFrozen()) {\n            releaseWorkerTickets();\n            return;\n        }"));
        assertTrue(worker.contains("return isNoAi() && runtimeState == WorkerRuntimeState.STOPPING"));
        assertTrue(worker.contains("setLegacyTickingTicket(serverLevel, center.toLong(), true)"));
        assertTrue(worker.contains("setSimulationRegionTicket(\n                    serverLevel, center, SIMULATION_RADIUS, true)"));
    }

    @Test
    void safetyIsCentralizedAndRecheckedByOwnedBoundaries() throws IOException {
        String worker = Files.readString(Path.of(
                "src/main/java/dev/dudie/baritonehelper/entity/WorkerEntity.java"))
                .replace("\r\n", "\n");
        String interaction = Files.readString(Path.of(
                "src/main/java/dev/dudie/baritonehelper/internal/baritone/api/entity/LivingEntityInteractionManager.java"))
                .replace("\r\n", "\n");
        String controller = Files.readString(Path.of(
                "src/main/java/dev/dudie/baritonehelper/worker/WorkerController.java"))
                .replace("\r\n", "\n");

        assertTrue(worker.contains("boolean canEnterAt(BlockPos position)"));
        assertTrue(worker.contains("boolean canInteractAt(BlockPos position)"));
        assertTrue(worker.contains("configuration.searchMode() != SearchMode.ROAM"));
        assertTrue(interaction.contains("worker.canInteractAt(blockPos)"));
        assertTrue(interaction.contains("worker.canModifyAt(pos)"));
        assertTrue(interaction.contains("worker.canInteractAt(blockPos3)"));
        assertTrue(interaction.contains("canCommitBlockInteraction"));
        assertTrue(interaction.contains("worker.canModifyAt(\n            placement,"));
        assertTrue(controller.contains("worker.canStoreAt(storage)"));
    }
}
