package dev.dudie.baritonehelper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Java-only UI layout contract; client rendering is unavailable to JUnit. */
final class Release32UiLayoutTest {
    private static final int LOGICAL_WIDTH = 640;
    private static final int LOGICAL_HEIGHT = 480;
    private static final float MAX_UI_SCALE = 1.5F;

    @Test
    void screenDeclaresFiveTabsAndScalesInputAndRenderingTogether() throws IOException {
        String source = read(
                "src/main/java/dev/dudie/baritonehelper/client/WorkerDashboardScreen.java");
        assertTrue(source.contains("TAB_JOB"));
        assertTrue(source.contains("TAB_WORLD"));
        assertTrue(source.contains("TAB_STORAGE"));
        assertTrue(source.contains("TAB_PATHING"));
        assertTrue(source.contains("TAB_LOG"));
        assertTrue(source.contains("(panelWidth - 30) / 5"));
        assertTrue(source.contains("uiScale = Math.min(MAX_UI_SCALE"));
        assertTrue(source.contains("graphics.pose().scale(uiScale, uiScale, 1.0F)"));
        assertTrue(source.contains("mouseX / uiScale"));
        assertTrue(source.contains("mouseY / uiScale"));
    }

    @Test
    void logicalPanelFitsRequiredMinimumWindowSizes() {
        for (int[] size : new int[][] {{320, 240}, {640, 360}, {1280, 720}}) {
            Layout layout = layout(size[0], size[1]);
            assertTrue(layout.scale > 0.0F);
            assertTrue(layout.panelWidth >= 320 && layout.panelWidth <= 760);
            assertTrue(layout.panelHeight >= 300 && layout.panelHeight <= 460);
            assertTrue(layout.panelLeft >= 0 && layout.panelTop >= 0);
            assertTrue(layout.panelLeft + layout.panelWidth <= layout.logicalWidth);
            assertTrue(layout.panelTop + layout.panelHeight <= layout.logicalHeight);
        }
        assertEquals(0.5F, layout(320, 240).scale, 0.0001F);
        assertEquals(0.75F, layout(640, 360).scale, 0.0001F);
        assertEquals(MAX_UI_SCALE, layout(1280, 720).scale, 0.0001F);
    }

    @Test
    void narrowFooterHasExplicitResponsiveLayoutBranch() throws IOException {
        String source = read(
                "src/main/java/dev/dudie/baritonehelper/client/WorkerDashboardScreen.java");
        int footer = source.indexOf("private void layoutFooter()");
        assertTrue(footer >= 0);
        String footerSource = source.substring(footer, source.indexOf("    private ", footer + 20));
        assertTrue(footerSource.contains("if (panelWidth < 700)"));
        assertTrue(footerSource.contains("buttonWidth = (innerWidth - gap * 3) / 4"));
        assertTrue(footerSource.contains("pickupButton"));
        assertTrue(footerSource.contains("secondRow = firstRow + 24"));
    }

    @Test
    void worldAndActivityListsScrollWithoutCoveringFeedback() throws IOException {
        String source = read(
                "src/main/java/dev/dudie/baritonehelper/client/WorkerDashboardScreen.java");
        assertTrue(source.contains("int index = zoneOffset + row"));
        assertTrue(source.contains("history.size() - 1 - activityOffset"));
        assertTrue(source.contains("snapshot.noWorkZones().size() - visibleZoneRows()"));
        assertTrue(source.contains("snapshot.activityHistory().size() - visibleActivityRows()"));
        assertTrue(source.indexOf("super.render(graphics, logicalMouseX, logicalMouseY, partialTick);")
                < source.indexOf("renderFeedback(graphics);"));
    }

    @Test
    void compactJobControlsAndRemotePhysicalGatesAreExplicit() throws IOException {
        String source = read(
                "src/main/java/dev/dudie/baritonehelper/client/WorkerDashboardScreen.java");
        assertTrue(source.contains(".bounds(panelLeft + 18, panelTop + 282, 276, 20)"));
        assertTrue(source.contains("renderTelemetry(graphics, x, panelTop + 308, 276, 84)"));
        assertTrue(source.contains("usePlayerAreaButton.active = local"));
        assertTrue(source.contains("inventoryButton.active = local"));
        assertTrue(source.contains("selectStorageButton.active = local"));
        assertTrue(source.contains("screen.baritonehelper.remote_physical_limit"));
    }

    private static Layout layout(int width, int height) {
        float scale = Math.min(MAX_UI_SCALE,
                Math.min(width / (float) LOGICAL_WIDTH, height / (float) LOGICAL_HEIGHT));
        if (scale <= 0.0F) scale = 1.0F;
        int logicalWidth = Math.max(LOGICAL_WIDTH, Math.round(width / scale));
        int logicalHeight = Math.max(LOGICAL_HEIGHT, Math.round(height / scale));
        int panelWidth = Math.min(760, Math.max(320, logicalWidth - 20));
        int panelHeight = Math.min(460, Math.max(300, logicalHeight - 20));
        return new Layout(scale, logicalWidth, logicalHeight,
                (logicalWidth - panelWidth) / 2, (logicalHeight - panelHeight) / 2,
                panelWidth, panelHeight);
    }

    private record Layout(
            float scale,
            int logicalWidth,
            int logicalHeight,
            int panelLeft,
            int panelTop,
            int panelWidth,
            int panelHeight) {
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }
}
