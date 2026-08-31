package dev.dudie.baritonehelper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class V2ArchitectureContractTest {
    private static String read(String relative) throws IOException {
        return Files.readString(Path.of(relative));
    }

    @Test
    void productionUsesRealEngineAndInteractionBoundaries() throws IOException {
        String controller = read("src/main/java/dev/dudie/baritonehelper/worker/WorkerController.java");
        String worker = read("src/main/java/dev/dudie/baritonehelper/entity/WorkerEntity.java");
        assertTrue(controller.contains("beginPathTo"));
        assertTrue(read("src/main/java/dev/dudie/baritonehelper/entity/WorkerEntity.java").contains("GoalBlock"));
        assertFalse(controller.contains("getNavigation().moveTo"));
        assertFalse(controller.contains("destroyBlock("));
        assertFalse(worker.contains("level.setBlock("));
        assertTrue(worker.contains("processBlockBreakingAction"));
        assertTrue(worker.contains("ItemEntity"));
    }

    @Test
    void dashboardIsPayloadBackedAndExactPickerIsPresent() throws IOException {
        String screen = read("src/main/java/dev/dudie/baritonehelper/client/WorkerDashboardScreen.java");
        String network = read("src/main/java/dev/dudie/baritonehelper/network/WorkerNetwork.java");
        assertTrue(screen.contains("extends Screen"));
        assertTrue(screen.contains("BuiltInRegistries.BLOCK"));
        assertTrue(network.contains("expectedRevision"));
        assertTrue(network.contains("requestId"));
        assertTrue(network.contains("PacketDistributor.sendToServer") || network.contains("sendToPlayer"));
    }

    @Test
    void persistentConfigurationHasV2FieldsAndNoCubicScan() throws IOException {
        String source;
        try (var paths = Files.walk(Path.of("src/main/java/dev/dudie/baritonehelper/worker"))) {
            source = paths.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> {
                        try { return Files.readString(path); }
                        catch (IOException exception) { throw new RuntimeException(exception); }
                    }).collect(Collectors.joining("\n"));
        }
        assertTrue(source.contains("unlimitedCount"));
        assertTrue(source.contains("horizontalSearchRadius"));
        assertTrue(source.contains("NoWorkZone"));
        assertTrue(source.contains("TraversalBlocks"));
        assertFalse(source.contains("HORIZONTAL_RANGE = 16"));
        assertFalse(source.contains("VERTICAL_RANGE = 8"));
    }

    @Test
    void longRangeSearchIsTicketedCachedAndIndependentOfCurrentEyeLos() throws IOException {
        String planner = read("src/main/java/dev/dudie/baritonehelper/worker/WorkerPlanner.java");
        String controller = read("src/main/java/dev/dudie/baritonehelper/worker/WorkerController.java");
        String worker = read("src/main/java/dev/dudie/baritonehelper/entity/WorkerEntity.java");
        String blockState = read("src/main/java/dev/dudie/baritonehelper/internal/baritone/utils/BlockStateInterface.java");
        String calculation = read("src/main/java/dev/dudie/baritonehelper/internal/baritone/pathing/movement/CalculationContext.java");
        String pathing = read("src/main/java/dev/dudie/baritonehelper/internal/baritone/behavior/PathingBehavior.java");
        assertTrue(planner.contains("requestedChunk"));
        assertTrue(planner.contains("primeSearchTickets"));
        assertTrue(planner.contains("MAX_SEARCH_TICKETS = 4"));
        assertTrue(planner.contains("MAX_CACHED_CANDIDATES = 32"));
        assertTrue(planner.contains("work.getX() + 0.5"));
        assertTrue(planner.contains("work.getY() + worker.getEyeHeight()"));
        assertFalse(planner.contains("getEyePosition()"));
        assertTrue(worker.contains("SEARCH_TICKETS.forceChunk"));
        assertTrue(worker.contains("workerController.resetTransientState()"));
        assertTrue(controller.contains("PathingStatus.NO_PATH"));
        assertTrue(controller.contains("worker.pathingStatus() == PathingStatus.CALCULATING"));
        assertTrue(calculation.contains("BlockStateInterface.threadSafe"));
        assertTrue(blockState.contains("getStates().copy()"));
        assertFalse(blockState.contains("getVisibleChunkIfPresent"));
        assertTrue(pathing.contains("calculationGeneration"));
        assertTrue(pathing.contains("this.inProgress != pathfinder"));
    }

    @Test
    void releaseMetadataIsV2AndIncludesSourceLicense() throws IOException {
        String properties = read("gradle.properties");
        assertTrue(properties.contains("mod_version=2.0.0"));
        assertTrue(properties.contains("LGPL-3.0-or-later"));
        assertTrue(Files.exists(Path.of("LICENSES/LGPL-3.0.txt")));
        assertTrue(Files.exists(Path.of("THIRD_PARTY_NOTICES.md")));
    }
}
