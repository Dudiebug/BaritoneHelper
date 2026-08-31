package dev.dudie.baritonehelper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class MovementParityContractTest {
    private static final Path INPUT = Path.of(
            "src/main/java/dev/dudie/baritonehelper/internal/baritone/utils/InputOverrideHandler.java");
    private static final Path EXECUTOR = Path.of(
            "src/main/java/dev/dudie/baritonehelper/internal/baritone/pathing/path/PathExecutor.java");
    private static final Path EVENTS = Path.of(
            "src/main/java/dev/dudie/baritonehelper/internal/baritone/event/GameEventHandler.java");
    private static final Path CONTEXT = Path.of(
            "src/main/java/dev/dudie/baritonehelper/internal/baritone/utils/player/EntityContext.java");

    @Test
    void inputUsesPlayerDirectionalMagnitudes() throws IOException {
        String source = Files.readString(INPUT);

        assertTrue(source.contains("PLAYER_DIRECTIONAL_MAGNITUDE = 1.0F"));
        assertFalse(source.contains("float speed = 0.3F"));
    }

    @Test
    void sprintMutationIsTransitionBased() throws IOException {
        String source = Files.readString(EXECUTOR);

        assertTrue(source.contains("this.sprintNextTick = this.shouldSprintNextTick(calculationContext)"));
        assertTrue(source.contains("if (this.ctx.entity().isSprinting() != this.sprintNextTick)"));
        assertFalse(source.contains("setSprinting(this.shouldSprintNextTick())"));
        assertFalse(source.contains("setInputForceState(Input.SPRINT, false);\n      if"));

        assertTrue(Files.readString(INPUT).contains("!this.baritone.getPathingBehavior().isPathing()"));
    }

    @Test
    void tickReusesLiveStateAndSearchesNearCurrentMovement() throws IOException {
        String executor = Files.readString(EXECUTOR);
        String events = Files.readString(EVENTS);

        assertTrue(executor.contains("baritone.bsi"));
        assertFalse(executor.contains("new BlockStateInterface(this.ctx)"));
        assertTrue(executor.contains("CalculationContext calculationContext"));
        assertFalse(executor.contains("new CalculationContext(this.behavior.baritone)"));
        assertTrue(executor.contains("this.pathPosition - 10"));
        assertTrue(executor.contains("this.pathPosition + 10"));
        assertFalse(executor.contains("for (IMovement movement : path.movements())"));
        assertTrue(executor.contains("if (!bsi.worldContainsLoadedChunk"));
        assertTrue(events.contains("this.baritone.bsi = new BlockStateInterface"));
        assertTrue(Files.readString(CONTEXT).contains("BlockStateInterface bsi = baritone.bsi"));
    }

    @Test
    void pinnedMovementMathRemainsUnchanged() throws IOException {
        String source = Files.readString(EXECUTOR);

        assertTrue(source.contains("double len = i - this.pathPosition - 0.4;"));
        assertTrue(source.contains("movement.getDest().x + 0.5"));
        assertTrue(source.contains("movement.getDest().z + 0.5"));
        assertTrue(Files.readString(CONTEXT).contains("this.entity().getY() + 0.1251"));
    }
}
