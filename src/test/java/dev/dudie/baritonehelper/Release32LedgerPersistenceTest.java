package dev.dudie.baritonehelper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source-level gate for the runtime ledger persistence path.
 *
 * Minecraft SavedData is exercised by the Release32 GameTest/runtime harness;
 * the ordinary JUnit source set intentionally stays Java-only.
 */
final class Release32LedgerPersistenceTest {
    @Test
    void persistenceWritesTargetChunkStateAndLocations() throws IOException {
        String source = read(
                "src/main/java/dev/dudie/baritonehelper/internal/baritone/cache/SharedWorldKnowledge.java");
        assertTrue(source.contains("tag.putInt(\"Schema\", CURRENT_SCHEMA)"));
        assertTrue(source.contains("entry.putString(\"Target\", snapshot.target())"));
        assertTrue(source.contains("entry.putLong(\"Chunk\", snapshot.chunk())"));
        assertTrue(source.contains("entry.putString(\"State\", snapshot.state().name())"));
        assertTrue(source.contains("entry.putLongArray(\"Locations\""));
        assertTrue(source.contains("data.ledger.restore(snapshots)"));
    }

    @Test
    void interruptedScansBecomeDirtyAndUnknownSchemasAreRejected() throws IOException {
        String ledger = read(
                "src/main/java/dev/dudie/baritonehelper/internal/baritone/cache/TargetCoverageLedger.java");
        String persistence = read(
                "src/main/java/dev/dudie/baritonehelper/internal/baritone/cache/SharedWorldKnowledge.java");

        assertTrue(ledger.contains(
                "knowledge.state == CoverageState.SCANNING ? CoverageState.DIRTY : knowledge.state"));
        assertTrue(ledger.contains("snapshot.state() == CoverageState.SCANNING"));
        assertTrue(ledger.contains("? CoverageState.DIRTY : snapshot.state()"));
        assertTrue(persistence.contains(
                "if (tag.getInt(\"Schema\") != CURRENT_SCHEMA) return data;"));
    }

    @Test
    void observationsAreTargetScopedAndUncertainDirtyLocationsAreCleared() throws IOException {
        String source = read(
                "src/main/java/dev/dudie/baritonehelper/internal/baritone/cache/TargetCoverageLedger.java");
        assertTrue(source.contains("Map<String, Map<Long, ChunkKnowledge>> targets"));
        assertTrue(source.contains("target(target, false).get(chunk)"));
        assertTrue(source.contains("knowledge.state != CoverageState.SCANNED && knowledge.state != CoverageState.DIRTY"));
        assertTrue(source.contains("|| knowledge.state == CoverageState.DIRTY"));
        assertTrue(source.contains("recordBlockChange"));
        assertTrue(source.contains("knowledge.locations.clear()"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }
}
