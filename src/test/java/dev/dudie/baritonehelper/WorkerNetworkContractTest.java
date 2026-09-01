package dev.dudie.baritonehelper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class WorkerNetworkContractTest {
    private static String read(String relative) throws IOException {
        return Files.readString(Path.of(relative));
    }

    @Test
    void serverActionQueuesBeforeValidationOrMutation() throws IOException {
        String source = read("src/main/java/dev/dudie/baritonehelper/network/WorkerNetwork.java");
        int start = source.indexOf("private static void handleActionServer(");
        int end = source.indexOf("\n    private static", start + 1);
        String handler = source.substring(start, end);

        assertTrue(handler.contains("context.enqueueWork"));
        assertTrue(handler.contains("handleActionServerOnServerThread"));
        assertFalse(handler.contains("findOwnedWorker"));
        assertFalse(handler.contains("applyAction"));
    }

    @Test
    void inventoryRequestHasDimensionAndOpenFailureErrors() throws IOException {
        String source = read("src/main/java/dev/dudie/baritonehelper/network/WorkerNetwork.java");

        assertTrue(source.contains("player.level() != worker.level()"));
        assertTrue(source.contains("inventory_wrong_dimension"));
        assertTrue(source.contains("inventory_open_failed"));
        assertTrue(source.contains("message.baritonehelper.other_dimension"));
        assertTrue(source.contains("message.baritonehelper.command_failed"));
        assertTrue(source.contains("canOpenInventory(player)"));
        assertTrue(source.contains("openMenu(worker)"));
        assertFalse(source.contains("closeContainer"));
        assertTrue(source.contains("isPresent()") || source.contains("isEmpty()"));
        assertTrue(source.contains("ownerId"));
    }

    @Test
    void dashboardStateCachesConfigurationDataByRevision() throws IOException {
        String source = read("src/main/java/dev/dudie/baritonehelper/network/WorkerDashboardStateS2C.java");

        assertTrue(source.contains("configurationRevision()"));
        assertTrue(source.contains("ConfigurationCache"));
        assertTrue(source.contains("computeIfAbsent") || source.contains("cachedRevision"));
    }

    @Test
    void clientRejectsOlderOrDuplicateSnapshots() throws IOException {
        String source = read("src/main/java/dev/dudie/baritonehelper/client/WorkerDashboardScreen.java");

        assertTrue(source.contains("incomingView.stateSequence() > snapshotView.stateSequence()"));
        assertTrue(source.contains("currentUuid.equals(incomingUuid)"));
        assertTrue(source.contains("updateButtons(configurationChanged)"));

        String worker = read("src/main/java/dev/dudie/baritonehelper/entity/WorkerEntity.java");
        assertTrue(worker.contains("DashboardStateSequence"));
        assertTrue(worker.contains("nextDashboardStateSequence()"));
    }

    @Test
    void compatibilityMenuDoesNotCloseBeforeInventoryOpen() throws IOException {
        String source = read("src/main/java/dev/dudie/baritonehelper/menu/WorkerDashboardMenu.java");
        int start = source.indexOf("case BUTTON_OPEN_INVENTORY");
        int end = source.indexOf("\n            default", start + 1);
        String inventoryCase = source.substring(start, end);

        assertFalse(inventoryCase.contains("closeContainer"));
        assertTrue(inventoryCase.contains("openMenu(worker)"));
        assertTrue(source.contains("message.baritonehelper.other_dimension"));
        assertTrue(source.contains("message.baritonehelper.command_failed"));
    }
}
