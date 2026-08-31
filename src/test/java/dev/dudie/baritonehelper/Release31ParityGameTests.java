package dev.dudie.baritonehelper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Wiring contracts for 3.1 behavior that a GameTest cannot reach without
 * first starting the full dashboard/network flow.
 */
class Release31ParityGameTests {
    private static String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }

    @Test
    void collectionStartsTheCanonicalMineProcessWithConfiguredTargets() throws IOException {
        String source = read(
                "src/main/java/dev/dudie/baritonehelper/entity/WorkerEntity.java")
                + read("src/main/java/dev/dudie/baritonehelper/worker/WorkerController.java");
        String normalized = source.replaceAll("\\s+", " ");

        assertTrue(
                normalized.contains("getMineProcess().mine(0,"),
                "starting a collection job must route configured targets to MineProcess.mine(0, ...)");
        assertTrue(
                normalized.contains("getOptional(targetId)")
                        || normalized.contains("targetBlockId"),
                "the MineProcess call must be driven by the configured target, not a hard-coded block");
    }

    @Test
    void collectionControllerDoesNotConstructTheLegacySearchCursor() throws IOException {
        String controller = read(
                "src/main/java/dev/dudie/baritonehelper/worker/WorkerController.java");

        assertFalse(
                controller.contains("new WorkerPlanner.SearchCursor"),
                "production collection routing must not construct WorkerPlanner.SearchCursor");
        assertFalse(
                controller.contains("new SearchCursor("),
                "production collection routing must not construct an unqualified SearchCursor");
    }

    @Test
    void playerInputUsesPinnedMagnitudeAndIsPublishedBeforeEntityTravel() throws IOException {
        String input = read(
                "src/main/java/dev/dudie/baritonehelper/internal/baritone/utils/InputOverrideHandler.java");
        String executor = read(
                "src/main/java/dev/dudie/baritonehelper/internal/baritone/pathing/path/PathExecutor.java");
        String worker = read(
                "src/main/java/dev/dudie/baritonehelper/entity/WorkerEntity.java");

        assertTrue(
                input.contains("1.0F") || input.contains("1.0f"),
                "player-compatible analogue movement magnitude must remain pinned at 1.0");
        assertTrue(
                input.contains("Input.MOVE_FORWARD")
                        && input.contains("Input.MOVE_BACK")
                        && input.contains("Input.MOVE_LEFT")
                        && input.contains("Input.MOVE_RIGHT"),
                "forward, backward, and strafe inputs must all be published");
        assertTrue(
                executor.contains("setSprinting(this.sprintNextTick)"),
                "path execution must publish sprint state through the entity");

        int aiStart = worker.indexOf("customServerAiStep");
        int vanillaAi = worker.indexOf("super.customServerAiStep();", aiStart);
        int engineTick = worker.indexOf("baritoneEngine.serverTick();", aiStart);
        int entityTick = worker.lastIndexOf("public void tick()");
        int superTick = worker.indexOf("super.tick();", entityTick);
        int entityPhaseEngineTick = worker.indexOf("baritoneEngine.serverTick();", entityTick);
        boolean engineRunsInServerAi = aiStart >= 0 && vanillaAi >= aiStart
                && engineTick > vanillaAi && entityTick >= 0;
        boolean engineRunsBeforeEntityTravel = entityTick >= 0 && superTick >= 0
                && entityPhaseEngineTick >= entityTick && entityPhaseEngineTick < superTick;
        assertTrue(
                engineRunsInServerAi || engineRunsBeforeEntityTravel,
                "Baritone input must be selected after vanilla AI and before entity travel consumes it");
    }

    @Test
    void lootContextUsesTheMinecraft121OptionalSequenceContract() throws IOException {
        String blockMeta = read(
                "src/main/java/dev/dudie/baritonehelper/internal/baritone/api/utils/BlockOptionalMeta.java");
        assertTrue(blockMeta.contains("create(Optional.empty())"));
        assertFalse(blockMeta.contains(".create(null)"));
        assertFalse(blockMeta.contains("IItemStack"));
        assertTrue(blockMeta.contains("stack.getItem().hashCode()"));

        String rayTrace = read(
                "src/main/java/dev/dudie/baritonehelper/internal/baritone/api/utils/RayTraceUtils.java");
        assertTrue(rayTrace.contains("entity.getEyeHeight(Pose.CROUCHING)"));
        assertFalse(rayTrace.contains("IEntityAccessor"));
    }

    @Test
    void forcedBaritoneLookIsNotNudgedAwayFromTheMiningClickGate() throws IOException {
        String look = read(
                "src/main/java/dev/dudie/baritonehelper/internal/baritone/behavior/LookBehavior.java")
                .replaceAll("\\s+", " ");
        String worker = read(
                "src/main/java/dev/dudie/baritonehelper/entity/WorkerEntity.java")
                .replaceAll("\\s+", " ");

        assertTrue(
                look.contains("!forcePrimary && !this.baritone.settings().freeLook.get()"),
                "forced movement rotations must stay exact for Rotation.isReallyCloseTo");
        assertTrue(
                worker.contains("this.lookControl = new LookControl(this)")
                        && worker.contains("if (!WorkerEntity.this.job.activelyWorks())"),
                "vanilla LookControl must not overwrite Baritone's exact rotation during an active job");
    }

    @Test
    void canceledAsyncCalculationsRequireGenerationFencingAndServerPublication() throws IOException {
        String pathing = read(
                "src/main/java/dev/dudie/baritonehelper/internal/baritone/behavior/PathingBehavior.java");

        assertTrue(pathing.contains("calculationGeneration"),
                "path calculations must carry a generation fence");
        assertTrue(pathing.contains("this.inProgress != pathfinder"),
                "completion must reject a calculation that is no longer owned");
        assertTrue(pathing.contains("this.calculationGeneration != generation"),
                "completion must reject a stale generation");
        assertTrue(pathing.contains("PathEvent.CANCELED"),
                "cancellation must publish a cancellation event");

        int asyncStart = pathing.indexOf("InternalBaritoneRuntime.getExecutor()");
        int asyncEnd = pathing.indexOf("private static AbstractNodeCostSearch createPathfinder", asyncStart);
        assertTrue(asyncStart >= 0 && asyncEnd > asyncStart,
                "the asynchronous completion region must remain identifiable");
        String asyncRegion = pathing.substring(asyncStart, asyncEnd);
        assertTrue(
                hasServerPublicationMarker(asyncRegion),
                "async completion must marshal path/status publication to the server thread");
    }

    private static boolean hasServerPublicationMarker(String source) {
        return List.of(
                        "getServer().execute",
                        "server.execute",
                        "executeOnServerThread",
                        "runOnServerThread",
                        "enqueueServerTask",
                        "publishOnServerThread")
                .stream()
                .anyMatch(source::contains);
    }
}
