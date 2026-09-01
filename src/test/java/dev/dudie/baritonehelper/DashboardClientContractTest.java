package dev.dudie.baritonehelper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class DashboardClientContractTest {
    private static String read(String relative) throws IOException {
        return Files.readString(Path.of(relative));
    }

    @Test
    void clientUsesV4IdentitySequenceAndCorrelatedAcknowledgements() throws IOException {
        String screen = read("src/main/java/dev/dudie/baritonehelper/client/WorkerDashboardScreen.java");
        String accessors = read("src/main/java/dev/dudie/baritonehelper/client/DashboardProtocolAccessors.java");

        assertTrue(screen.contains("snapshotView.workerUuid()"));
        assertTrue(screen.contains("snapshotView.stateSequence()"));
        assertTrue(screen.contains("pendingRequestId.equals(requestId)"));
        assertTrue(screen.contains("awaitingSnapshotRevision"));
        assertTrue(screen.contains("if (requestPending || awaitingSnapshotRevision > snapshot.configurationRevision()) return;"));
        assertTrue(screen.contains("private int revision() { return snapshot.configurationRevision(); }"));
        assertTrue(screen.contains("workerUuid(), workerDimension(), revision()"));
        assertFalse(screen.contains("new WorkerDashboardActionC2S(workerEntityId()"));
        assertTrue(accessors.contains("stateSequence"));
        assertTrue(accessors.contains("workerUuid"));
        assertTrue(accessors.contains("value.contains(\"dismiss\")"));
    }

    @Test
    void dashboardKeepsPersistentTelemetryAndScaledHitboxes() throws IOException {
        String screen = read("src/main/java/dev/dudie/baritonehelper/client/WorkerDashboardScreen.java");
        String language = read("src/main/resources/assets/baritonehelper/lang/en_us.json");

        assertTrue(screen.contains("renderHeader(graphics)"));
        assertTrue(screen.contains("renderTelemetry"));
        assertTrue(screen.contains("pickupConfirmation"));
        assertTrue(screen.contains("uiScale"));
        assertTrue(screen.contains("width / (float) LOGICAL_WIDTH"));
        assertTrue(screen.contains("height / (float) LOGICAL_HEIGHT"));
        assertTrue(screen.contains("graphics.pose().scale(uiScale"));
        assertTrue(screen.contains("mouseX / uiScale"));
        assertTrue(screen.contains("WORK_AREA_MODE"));
        assertTrue(screen.contains("ROAM_MODE"));
        assertTrue(language.contains("screen.baritonehelper.worker_identity"));
        assertTrue(language.contains("screen.baritonehelper.pickup"));
        assertTrue(language.contains("screen.baritonehelper.telemetry"));
        assertTrue(language.contains("screen.baritonehelper.work_area_mode"));
        assertTrue(language.contains("screen.baritonehelper.roam"));
    }
}
