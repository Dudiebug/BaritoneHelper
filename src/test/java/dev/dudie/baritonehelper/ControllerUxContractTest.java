package dev.dudie.baritonehelper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ControllerUxContractTest {
    @Test
    void targetSelectionConfiguresButDoesNotImplicitlyStart() throws IOException {
        String controllerItem = Files.readString(Path.of(
                "src/main/java/dev/dudie/baritonehelper/item/WorkerControllerItem.java"));
        assertTrue(controllerItem.contains("configureTarget(blockId, position)"));
        assertTrue(controllerItem.contains("openDashboard(player)"));
        assertFalse(controllerItem.contains("beginCollection(blockId, position)"));
        assertFalse(controllerItem.contains("togglePaused()"));
    }

    @Test
    void dashboardExposesExplicitStartStopAndClearActions() throws IOException {
        String menu = Files.readString(Path.of(
                "src/main/java/dev/dudie/baritonehelper/menu/WorkerDashboardMenu.java"));
        assertTrue(menu.contains("BUTTON_START"));
        assertTrue(menu.contains("BUTTON_STOP"));
        assertTrue(menu.contains("BUTTON_CLEAR_TARGET"));
        assertTrue(menu.contains("BUTTON_CLEAR_STORAGE"));
        assertTrue(menu.contains("clickMenuButton"));
    }

    @Test
    void navigationSeparatesResourceFromReachableWorkPosition() throws IOException {
        String planner = Files.readString(Path.of(
                "src/main/java/dev/dudie/baritonehelper/worker/WorkerPlanner.java"));
        String controller = Files.readString(Path.of(
                "src/main/java/dev/dudie/baritonehelper/worker/WorkerController.java"));
        assertTrue(planner.contains("record CollectionPlan(BlockPos target, BlockPos workPosition)"));
        assertTrue(planner.contains("canStandAt"));
        assertTrue(controller.contains("currentWorkPosition"));
        assertTrue(controller.contains("navigateTo(currentWorkPosition)"));
    }

    @Test
    void everyControllerActionHasVisibleFeedback() throws IOException {
        String item = Files.readString(Path.of(
                "src/main/java/dev/dudie/baritonehelper/item/WorkerControllerItem.java"));
        String menu = Files.readString(Path.of(
                "src/main/java/dev/dudie/baritonehelper/menu/WorkerDashboardMenu.java"));
        assertTrue(item.contains("WorkerMessages.send"));
        assertTrue(menu.contains("WorkerMessages.send"));
        assertTrue(menu.contains("message.baritonehelper.job_started"));
        assertTrue(menu.contains("message.baritonehelper.job_stopped"));
    }
}
