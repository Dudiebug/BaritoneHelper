package dev.dudie.baritonehelper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class MineProcessRescanTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/dev/dudie/baritonehelper/internal/baritone/process/MineProcess.java");

    @Test
    void coalescesRescansByGenerationAndPublishesOnTheServerThread() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("rescanGeneration"));
        assertTrue(source.contains("rescanInFlight"));
        assertTrue(source.contains("rescanInFlightGeneration"));
        assertTrue(source.contains("Future<?> rescanFuture"));
        assertTrue(source.contains("future.cancel(true)"));
        assertTrue(source.contains("getScannerExecutor().remove"));
        assertTrue(source.contains("server.execute"));
        assertFalse(source.contains("getExecutor().execute(() -> this.rescan"));
        assertTrue(source.contains("this.rescanGeneration,\n               request.filter()"),
                "periodic coalescing must retain the active lifecycle generation");
        assertTrue(Pattern.compile("\\+\\+this\\.rescanGeneration").matcher(source).results().count() == 1,
                "only cancellation may invalidate an in-flight rescan generation");
        assertFalse(source.contains("No locations for \" + this.filter + \" known, cancelling"),
                "an empty unlimited scan must wait instead of restarting MineProcess every tick");
        assertTrue(source.contains("this.blacklist.clear()"),
                "an exhausted provisional blacklist must permit a later retry");
    }
}
