package dev.dudie.baritonehelper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorldScannerDiscoveryTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/dev/dudie/baritonehelper/internal/baritone/cache/WorldScanner.java");

    @Test
    void scansLoadedChunksOnceWithoutABlockingRadiusLoop() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("getChunkNow"));
        assertTrue(source.contains("maybeHas"));
        assertTrue(source.contains("getStates()"));
        assertFalse(source.contains("while (true)"));
        assertFalse(source.contains("getChunk(pos.x, pos.z, ChunkStatus.FULL, false)"));
    }

    @Test
    void keepsWorldReadsOnTheSnapshotSideOfTheBoundary() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("ScanSnapshot"));
        assertTrue(source.contains("copy()"));
        assertTrue(source.contains("scanSnapshot"));
    }
}
