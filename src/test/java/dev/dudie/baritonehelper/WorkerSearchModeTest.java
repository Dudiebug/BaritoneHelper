package dev.dudie.baritonehelper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dudie.baritonehelper.worker.SearchMode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorkerSearchModeTest {
    @Test
    void legacyJobsDefaultToWorkAreaAndRoamRoundTrips() throws IOException {
        assertEquals(SearchMode.WORK_AREA, SearchMode.fromSerialized(null));
        assertEquals(SearchMode.WORK_AREA, SearchMode.fromSerialized(""));
        assertEquals(SearchMode.WORK_AREA, SearchMode.fromSerialized("future-mode"));
        assertEquals(SearchMode.ROAM, SearchMode.fromSerialized(SearchMode.ROAM.serializedName()));

        String configuration = Files.readString(Path.of(
                "src/main/java/dev/dudie/baritonehelper/worker/WorkerJobConfiguration.java"));
        assertTrue(configuration.contains("tag.putString(\"SearchMode\", searchMode.serializedName())"));
        assertTrue(configuration.contains("SearchMode.fromSerialized(tag.getString(\"SearchMode\"))"));

        String action = Files.readString(Path.of(
                "src/main/java/dev/dudie/baritonehelper/network/WorkerDashboardActionC2S.java"));
        String network = Files.readString(Path.of(
                "src/main/java/dev/dudie/baritonehelper/network/WorkerNetwork.java"));
        String screen = Files.readString(Path.of(
                "src/main/java/dev/dudie/baritonehelper/client/WorkerDashboardScreen.java"));
        assertTrue(action.contains("SET_SEARCH_MODE"));
        assertTrue(network.contains("case SET_SEARCH_MODE"));
        assertTrue(network.contains("mode.serializedName().equals(payload.blockId())"));
        assertTrue(screen.contains("Action.SET_SEARCH_MODE"));
    }
}
