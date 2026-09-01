package dev.dudie.baritonehelper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GameTestHarnessStabilityTest {
    private static final Path GAME_TESTS = Path.of(
            "src/main/java/dev/dudie/baritonehelper/gametest");

    @Test
    void asynchronousGameTestsAllowHostedExecutorsToMakeProgress() throws IOException {
        String longRange = read("LongRangeDiscoveryRegressionGameTests.java");
        String worker = read("BaritoneHelperGameTests.java");
        String fourWorker = read("FourWorkerPerformanceGameTests.java");

        for (String source : new String[] {longRange, worker, fourWorker}) {
            assertTrue(source.contains("ASYNC_TIMEOUT_TICKS = 64_000"));
        }
        assertFalse(longRange.contains("timeoutTicks = 12000"));
        assertFalse(longRange.contains("timeoutTicks = 24000"));
        assertFalse(worker.contains("timeoutTicks = 4000"));
        assertFalse(worker.contains("timeoutTicks = 12000"));
        assertFalse(fourWorker.contains("timeoutTicks = 16000"));
    }

    @Test
    void failureDiagnosticsFitMinecraftBookPages() throws IOException {
        for (String name : new String[] {
                "LongRangeDiscoveryRegressionGameTests.java",
                "BaritoneHelperGameTests.java"}) {
            String source = read(name);
            assertTrue(source.contains("MAX_FAILURE_DIAGNOSTIC_LENGTH = 800"));
            assertTrue(source.contains("MAX_FAILURE_DIAGNOSTIC_LENGTH - 3"));
        }
    }

    private static String read(String name) throws IOException {
        return Files.readString(GAME_TESTS.resolve(name));
    }
}
