package dev.dudie.baritonehelper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PathingProgressSnapshotContractTest {
    private static String read(String relative) throws IOException {
        return Files.readString(Path.of(relative));
    }

    @Test
    void progressCopiesCompleteChainsBeforeAtomicPublication() throws IOException {
        String search = read(
                "src/main/java/dev/dudie/baritonehelper/internal/baritone/pathing/calc/AbstractNodeCostSearch.java");
        String node = read(
                "src/main/java/dev/dudie/baritonehelper/internal/baritone/pathing/calc/PathNode.java");

        assertTrue(search.contains("AtomicReference<ProgressSnapshot>"));
        assertTrue(search.contains("PathNode.Snapshot.copyOf(startNode)"));
        assertTrue(search.contains("PathNode.Snapshot.copyOf(bestSoFar[i])"));
        assertTrue(node.contains("static final class Snapshot"));
        assertTrue(node.contains("private final Snapshot previous"));
        assertTrue(node.contains("new Snapshot(chain.get(i), previous)"));
    }

    @Test
    void aStarChecksCancellationBetweenMovementExpansions() throws IOException {
        String search = read(
                "src/main/java/dev/dudie/baritonehelper/internal/baritone/pathing/calc/AStarPathFinder.java");
        int movesLoop = search.indexOf("for (Moves moves : allMoves)");
        int loopEnd = search.indexOf("this.publishProgress();", movesLoop);

        assertTrue(movesLoop >= 0 && loopEnd > movesLoop);
        String expansion = search.substring(movesLoop, loopEnd);
        assertTrue(expansion.contains("cancelRequested.get()"));
        assertTrue(expansion.contains("break;"));
        assertTrue(search.contains("if ((numNodes & 63) == 0)"),
                "immutable progress copies must be sampled, not rebuilt for every node");
    }

    @Test
    void replacementPathUsesCurrentFeetAndEventDrivenCancellation() throws IOException {
        String behavior = read(
                "src/main/java/dev/dudie/baritonehelper/internal/baritone/behavior/PathingBehavior.java");

        assertTrue(behavior.contains("else if (!this.goal.isInGoal(this.ctx.feetPos()))"));
        assertTrue(!behavior.contains("!this.goal.isInGoal(this.expectedSegmentStart)"));
        int softCancel = behavior.indexOf("public void softCancelIfSafe()");
        int segmentCancel = behavior.indexOf("private void secretInternalSegmentCancel()", softCancel);
        String softCancelBody = behavior.substring(softCancel, segmentCancel);
        assertTrue(!softCancelBody.contains("this.status = PathingStatus.CANCELLED"));
        assertTrue(softCancelBody.contains("this.cancelRequested = true"));
        assertTrue(softCancelBody.contains("!this.cancelRequested || !this.isSafeToCancel()"));
    }
}
