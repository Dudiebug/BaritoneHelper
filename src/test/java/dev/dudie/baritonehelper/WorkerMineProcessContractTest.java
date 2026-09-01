package dev.dudie.baritonehelper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorkerMineProcessContractTest {
    private static String read(String relative) throws IOException {
        return Files.readString(Path.of(relative));
    }

    @Test
    void collectionStartsConfiguredBlockThroughMineProcess() throws IOException {
        String controller = read("src/main/java/dev/dudie/baritonehelper/worker/WorkerController.java");
        String worker = read("src/main/java/dev/dudie/baritonehelper/entity/WorkerEntity.java");
        int collectionStart = controller.indexOf("private void tickCollection");
        int depositStart = controller.indexOf("private void tickDeposit");
        String collection = controller.substring(collectionStart, depositStart);

        assertTrue(collection.contains("ensureMineProcessStarted()"));
        assertTrue(collection.contains("mineProcessActive()"));
        assertTrue(controller.contains("BuiltInRegistries.BLOCK.getOptional(targetId)"));
        assertFalse(collection.contains("SearchCursor"));
        assertFalse(collection.contains("nearestWorkPosition"));
        assertFalse(collection.contains("hasLineOfSight"));
        assertFalse(collection.contains("beginPathTo"));
        assertTrue(controller.contains("pathRequested = worker.beginPathTo(destination)"));
        assertTrue(worker.contains("targetBlockId == null || !workerChunkWindowReady()"),
                "MineProcess must not snapshot a partially loaded ticket window");
        assertTrue(worker.contains("SEARCH_TICKETS.forceChunk("));
        assertTrue(worker.contains("loaded.addAll(searchTicketChunks)"));
    }

    @Test
    void obsoleteControllerScannerIsGone() throws IOException {
        String controller = read("src/main/java/dev/dudie/baritonehelper/worker/WorkerController.java");
        String planner = read("src/main/java/dev/dudie/baritonehelper/worker/WorkerPlanner.java");

        assertFalse(controller.contains("new WorkerPlanner.SearchCursor"));
        assertFalse(planner.contains("class SearchCursor"));
        assertFalse(planner.contains("nearestCollectable"));
        assertTrue(planner.contains("MineProcess"));
    }
}
