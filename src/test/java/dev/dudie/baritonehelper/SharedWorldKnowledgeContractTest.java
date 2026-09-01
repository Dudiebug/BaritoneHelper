package dev.dudie.baritonehelper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SharedWorldKnowledgeContractTest {
    @Test
    void cacheUsesBlockPackingAndFiveHundredTwelveBlockRegions() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/dev/dudie/baritonehelper/internal/baritone/cache/CachedWorld.java"));

        assertTrue(source.contains("BlockPos pos = BlockPos.of(packed)"));
        assertTrue(source.contains("pos.getX() >> 9"));
        assertTrue(source.contains("pos.getZ() >> 9"));
        assertFalse(source.contains("ChunkPos.getX(packed)"));
        assertFalse(source.contains("ChunkPos.getZ(packed)"));
    }

    @Test
    void serverWorldProvidersShareSavedTargetCoverage() throws IOException {
        String provider = Files.readString(Path.of(
                "src/main/java/dev/dudie/baritonehelper/internal/baritone/cache/WorldProvider.java"));
        String saved = Files.readString(Path.of(
                "src/main/java/dev/dudie/baritonehelper/internal/baritone/cache/SharedWorldKnowledge.java"));

        assertTrue(provider.contains("SharedWorldKnowledge.get(serverLevel)"));
        assertTrue(saved.contains("extends SavedData"));
        assertTrue(saved.contains("TargetCoverageLedger"));
        assertTrue(saved.contains("Schema"));
    }

    @Test
    void worldMutationsDirtyAffectedChunkCoverage() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/dev/dudie/baritonehelper/internal/baritone/cache/WorldKnowledgeEvents.java"));

        assertTrue(source.contains("BlockEvent.BreakEvent"));
        assertTrue(source.contains("BlockEvent.EntityPlaceEvent"));
        assertTrue(source.contains("BlockEvent.FluidPlaceBlockEvent"));
        assertTrue(source.contains("PistonEvent.Post"));
        assertTrue(source.contains("ExplosionEvent.Detonate"));
        assertTrue(source.contains("knowledge.cachedWorld().markDirty"));
    }

    @Test
    void mineDiscoveryChecksCoverageForTheActiveTarget() throws IOException {
        String mine = Files.readString(Path.of(
                "src/main/java/dev/dudie/baritonehelper/internal/baritone/process/MineProcess.java"));
        String scanner = Files.readString(Path.of(
                "src/main/java/dev/dudie/baritonehelper/internal/baritone/cache/WorldScanner.java"));

        assertTrue(mine.contains("snapshot.hasTargetScans(target)"));
        assertTrue(mine.contains("coverage(target, centerChunk) != CoverageState.SCANNED"));
        assertFalse(mine.contains("CachedChunk.BLOCKS_TO_KEEP_TRACK_OF.contains(block)"),
                "all active targets, not Baritone's legacy subset, must read the shared index");
        assertTrue(scanner.contains("boolean hasTargetScans(String target)"));
    }
}
