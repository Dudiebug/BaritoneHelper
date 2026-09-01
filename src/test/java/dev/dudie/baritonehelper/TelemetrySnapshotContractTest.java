package dev.dudie.baritonehelper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class TelemetrySnapshotContractTest {
    private static String read(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/dev/dudie/baritonehelper").resolve(relative));
    }

    @Test
    void mineAndPathTelemetryUseImmutableSnapshotsAndRealBoundedQueues() throws Exception {
        String mine = read("internal/baritone/process/MineProcess.java");
        String runtime = read("internal/baritone/InternalBaritoneRuntime.java");
        String scanner = read("internal/baritone/cache/WorldScanner.java");
        String controller = read("worker/WorkerController.java");
        String payload = read("network/WorkerDashboardStateS2C.java");

        assertTrue(mine.contains("public SearchTelemetry telemetry()"));
        assertTrue(mine.contains("CoverageState.DIRTY"));
        assertTrue(mine.contains("CoverageState.SCANNING"));
        assertTrue(runtime.contains("pathQueueDepth()"));
        assertTrue(runtime.contains("scannerQueueDepth()"));
        assertTrue(scanner.contains("CAPTURE_CHUNK_BUDGET = 1"));
        assertTrue(scanner.contains("acquireCaptureBudget(world, ctx.entity().getUUID())"));
        assertTrue(scanner.contains("new ArrayDeque<>()"));
        assertTrue(mine.contains("snapshot.deferred()"));
        assertTrue(scanner.contains("Thread.currentThread().isInterrupted()"));
        assertFalse(controller.contains("public int chunksExamined() { return 0; }"));
        assertTrue(payload.contains("SearchTelemetry search = worker.searchTelemetry()"));
        assertTrue(payload.contains("PathTelemetry path = worker.pathTelemetry()"));
        assertTrue(payload.contains("searchGeneration"));
        assertTrue(payload.contains("simulationTicketCount"));
        assertTrue(payload.contains("pathElapsedNanos"));
    }

    @Test
    void fullSnapshotsAreThrottledWhileAcknowledgementsStayImmediate() throws Exception {
        String network = read("network/WorkerNetwork.java");
        assertTrue(network.contains("serverTick - previous.serverTick() < 10L"));
        assertTrue(network.contains("sendToPlayer(player, acknowledgement)"));
        assertTrue(network.contains("NetworkRegistry.hasChannel"));
    }
}
