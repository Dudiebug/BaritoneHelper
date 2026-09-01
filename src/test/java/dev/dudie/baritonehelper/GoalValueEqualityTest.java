package dev.dudie.baritonehelper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import dev.dudie.baritonehelper.internal.baritone.api.pathing.goals.GoalBlock;
import dev.dudie.baritonehelper.internal.baritone.api.pathing.goals.GoalComposite;
import dev.dudie.baritonehelper.internal.baritone.api.pathing.goals.GoalTwoBlocks;
import dev.dudie.baritonehelper.internal.baritone.api.pathing.goals.GoalXZ;
import org.junit.jupiter.api.Test;

final class GoalValueEqualityTest {
    @Test
    void rebuiltMiningGoalsUseValuesRatherThanCensoredStrings() {
        int x = -1234;
        int y = 17;
        int z = 9876;
        assertEquals(new GoalBlock(x, y, z), new GoalBlock(x, y, z));
        assertEquals(new GoalTwoBlocks(x, y, z), new GoalTwoBlocks(x, y, z));
        assertEquals(new GoalXZ(x, z), new GoalXZ(x, z));

        GoalComposite first = new GoalComposite(new GoalTwoBlocks(x, y, z), new GoalBlock(x, y, z));
        GoalComposite rebuilt = new GoalComposite(new GoalTwoBlocks(x, y, z), new GoalBlock(x, y, z));
        assertEquals(first, rebuilt);
        assertEquals(first.hashCode(), rebuilt.hashCode());
        assertNotEquals(first, new GoalComposite(new GoalTwoBlocks(x, y + 1, z), new GoalBlock(x, y, z)));
    }
}
