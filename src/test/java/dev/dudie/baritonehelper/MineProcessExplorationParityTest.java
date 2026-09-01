package dev.dudie.baritonehelper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dudie.baritonehelper.internal.baritone.process.MineProcess;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MineProcessExplorationParityTest {
    private static final Path MAIN = Path.of("src/main/java/dev/dudie/baritonehelper");
    private static final Path MINE_PROCESS = MAIN.resolve("internal/baritone/process/MineProcess.java");
    private static final Path SETTINGS = MAIN.resolve("internal/baritone/api/Settings.java");
    private static final Path GOAL_RUN_AWAY = MAIN.resolve(
            "internal/baritone/api/pathing/goals/GoalRunAway.java");
    private static final Path MOVEMENT_HELPER = MAIN.resolve(
            "internal/baritone/pathing/movement/MovementHelper.java");
    private static final Path TOOL_SET = MAIN.resolve(
            "internal/baritone/utils/ToolSet.java");
    private static final Path INPUT_OVERRIDE = MAIN.resolve(
            "internal/baritone/utils/" + "InputOverrideHandler.java");

    @Test
    void workerSearchModeSelectsBoundedFrontierOrRoamGoal() throws IOException {
        String source = readSource(MINE_PROCESS);

        assertTrue(source.contains("SearchMode.WORK_AREA"));
        assertTrue(source.contains("SearchMode.ROAM"));
        assertTrue(source.contains("worker.configuration().searchMode()"));
        assertTrue(source.contains("workAreaCenter"));
        assertTrue(source.contains("horizontalSearchRadius"));
        assertTrue(source.contains("verticalSearchRadius"));
        assertTrue(source.contains("explorationFrontier"));
        assertTrue(source.contains("GoalRunAway"));
        assertFalse(source.contains(
                "!this.baritone.settings().legitMine.get() && this.knownOreLocations.isEmpty()"),
                "an empty target list must not pause before exploration has been exhausted");
    }

    @Test
    void searchLimitsAndSafetyPoliciesRemainExplicit() throws IOException {
        String mineProcess = readSource(MINE_PROCESS);
        String settings = readSource(SETTINGS);
        String movement = readSource(MOVEMENT_HELPER);
        String toolSet = readSource(TOOL_SET);
        String inputOverride = readSource(INPUT_OVERRIDE);

        assertTrue(mineProcess.contains("MAX_SEARCH_CANDIDATES = 4_096"));
        assertTrue(mineProcess.contains("mineMaxOreLocationsCount.get()"));
        assertTrue(mineProcess.contains("getLocationsOf(\n                   target,\n                   MAX_SEARCH_CANDIDATES"));
        assertTrue(mineProcess.contains("minYLevelWhileMining.get()"));
        assertTrue(mineProcess.contains("maxYLevelWhileMining.get()"));
        assertTrue(mineProcess.contains("MovementHelper.avoidBreaking("));
        assertTrue(mineProcess.contains("this.filter.has(state)"));
        assertTrue(mineProcess.contains("WorkerPlanner.nearestWorkPosition("),
                "server workers must path to a reachable interaction stance");
        assertTrue(mineProcess.contains("RotationUtils.reachable(this.ctx, pos).isPresent()"),
                "target interaction must be reach-checked on the server thread");
        assertTrue(mineProcess.contains("worker.canModifyAt(pos)"),
                "target interaction must revalidate worker modification policy");
        assertTrue(mineProcess.contains("snapshot.publishTargetScans()"));
        assertTrue(mineProcess.contains("rejected.contains(ChunkPos.asLong"));
        assertTrue(settings.contains("minYLevelWhileMining = new Settings.Setting<>(0)"));
        assertTrue(settings.contains("maxYLevelWhileMining = new Settings.Setting<>(2031)"));
        assertTrue(settings.contains("mineMaxOreLocationsCount = new Settings.Setting<>(64)"));
        assertTrue(settings.contains("allowBreakAnyway"));
        assertTrue(mineProcess.contains("MiningYRange.relativeTo("));
        assertTrue(movement.contains("b instanceof AirBlock"));
        assertTrue(movement.contains("bsi.worldBorder.canPlaceAt(x, z)"));
        assertTrue(settings.contains("blocksToDisallowBreaking"));
        assertTrue(settings.contains("avoidBreakingMultiplier"));
        assertTrue(movement.contains("blocksToDisallowBreaking"));
        assertFalse(movement.contains("settings.blocksToAvoidBreaking.get().contains(b)"));
        assertTrue(toolSet.contains("blocksToAvoidBreaking"));
        assertTrue(toolSet.contains("avoidBreakingMultiplier"));
        assertTrue(movement.contains("contains(b)"));
        assertTrue(movement.contains("Blocks.SWEET_BERRY_BUSH"));
        assertTrue(movement.contains("Blocks.POWDER_SNOW"));
        assertTrue(movement.contains("PointedDripstoneBlock"));
        assertTrue(movement.contains("AmethystClusterBlock"));
        assertTrue(movement.contains("Blocks.SOUL_SAND"));
        assertTrue(movement.contains("Blocks.TWISTING_VINES"));
        assertTrue(inputOverride.contains("PLAYER_DIRECTIONAL_MAGNITUDE = 1.0F"));
    }

    @Test
    void miningYOffsetsAreRelativeToTheDimensionMinimum() {
        MineProcess.MiningYRange overworld = MineProcess.MiningYRange.relativeTo(-64, 0, 2031);
        assertEquals(-64, overworld.minInclusive());
        assertEquals(1967, overworld.maxInclusive());
        assertTrue(overworld.contains(-64));
        assertTrue(overworld.contains(319));
        assertFalse(overworld.contains(-65));

        MineProcess.MiningYRange shifted = MineProcess.MiningYRange.relativeTo(-64, 16, 32);
        assertEquals(-48, shifted.minInclusive());
        assertEquals(-32, shifted.maxInclusive());
        assertTrue(shifted.contains(-40));
        assertFalse(shifted.contains(-49));
    }

    @Test
    void miningYOffsetAdditionSaturatesInsteadOfOverflowing() {
        MineProcess.MiningYRange range = MineProcess.MiningYRange.relativeTo(
                Integer.MAX_VALUE, 1, Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, range.minInclusive());
        assertEquals(Integer.MAX_VALUE, range.maxInclusive());
    }

    @Test
    void runAwayHeuristicUsesAllBoundsWhenSeveralOriginsAreConfigured() throws IOException {
        String source = readSource(GOAL_RUN_AWAY);

        assertTrue(source.contains("Math.max(maxX, p.getX() + distance)"));
        assertTrue(source.contains("Math.max(maxY, p.getY() + distance)"));
        assertTrue(source.contains("Math.max(maxZ, p.getZ() + distance)"));
        assertTrue(source.contains("boolean equals(Object other)"));
        assertTrue(source.contains("Arrays.equals(this.from, goal.from)"));
        assertTrue(source.contains("int hashCode()"));
    }

    private static String readSource(Path path) throws IOException {
        return Files.readString(path).replace("\r\n", "\n");
    }
}
