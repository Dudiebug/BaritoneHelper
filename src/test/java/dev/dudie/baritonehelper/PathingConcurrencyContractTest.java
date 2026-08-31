package dev.dudie.baritonehelper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PathingConcurrencyContractTest {
    private static String read(String relative) throws IOException {
        return Files.readString(Path.of(relative));
    }

    @Test
    void executorIsCpuSizedBoundedAndFair() throws IOException {
        String source = read("src/main/java/dev/dudie/baritonehelper/internal/baritone/InternalBaritoneRuntime.java");

        assertTrue(source.contains("Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1))"));
        assertTrue(source.contains("new ArrayBlockingQueue<>(PATH_QUEUE_CAPACITY, true)"));
        assertTrue(source.contains("new ArrayBlockingQueue<>(SCAN_QUEUE_CAPACITY, true)"));
        assertTrue(source.contains("getScannerExecutor"));
        assertFalse(source.contains("newFixedThreadPool(2"));
    }

    @Test
    void cancellationAndProgressArePublishedWithoutReset() throws IOException {
        String source = read("src/main/java/dev/dudie/baritonehelper/internal/baritone/pathing/calc/AbstractNodeCostSearch.java");

        assertTrue(source.contains("AtomicBoolean"));
        assertTrue(source.contains("AtomicReference<ProgressSnapshot>"));
        assertTrue(source.contains("cancelRequested.get()"));
        assertFalse(source.contains("cancelRequested = false"));
    }

    @Test
    void queuedCalculationsRetainAndOwnTheirFuture() throws IOException {
        String source = read("src/main/java/dev/dudie/baritonehelper/internal/baritone/behavior/PathingBehavior.java");

        assertTrue(source.contains("Future<?> calculationFuture"));
        assertTrue(source.contains("FutureTask<Void>"));
        assertTrue(source.contains("future.cancel(true)"));
        assertTrue(source.contains("this.inProgress != pathfinder"));
        assertTrue(source.contains("this.calculationGeneration != generation"));
        assertTrue(source.contains("server.execute"));
        assertTrue(source.contains("goalsEquivalent(this.goal, goal)"));
        assertTrue(source.contains("first.toString().equals(second.toString())"),
                "equivalent rebuilt MineProcess goals must not stale an async path result");
    }
}
