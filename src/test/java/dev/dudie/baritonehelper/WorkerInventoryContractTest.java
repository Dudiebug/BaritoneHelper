package dev.dudie.baritonehelper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorkerInventoryContractTest {
    private static final Path INVENTORY = Path.of(
            "src/main/java/dev/dudie/baritonehelper/internal/baritone/api/entity/LivingEntityInventory.java");
    private static final Path BEHAVIOR = Path.of(
            "src/main/java/dev/dudie/baritonehelper/internal/baritone/behavior/InventoryBehavior.java");
    private static final Path TOOLS = Path.of(
            "src/main/java/dev/dudie/baritonehelper/internal/baritone/utils/ToolSet.java");
    private static final Path INTERACTIONS = Path.of(
            "src/main/java/dev/dudie/baritonehelper/internal/baritone/api/entity/LivingEntityInteractionManager.java");

    @Test
    void canonicalContainerViewKeepsFixedSlotsAndForwardsWrites() throws IOException {
        String source = Files.readString(INVENTORY);

        assertTrue(source.contains("private final Container backingInventory"));
        assertTrue(source.contains("player instanceof Container container ? container : null"));
        assertTrue(source.contains("private static final class CanonicalList"));
        assertTrue(source.contains("this.owner.setItem(index, stack)"));
        assertTrue(source.contains("this.backingInventory.setItem(slot, stack)"));
        assertTrue(source.contains("this.backingInventory.clearContent()"));
        assertFalse(source.contains("public final NonNullList<ItemStack> main ="));
    }

    @Test
    void handResultsAndBreakAccountingUseCanonicalServerState() throws IOException {
        String source = Files.readString(INTERACTIONS);

        assertTrue(source.contains("provider.getLivingInventory().getStackInHand(hand)"));
        assertTrue(source.contains("inventory.setStackInHand(hand, stack)"));
        String hook = "worker.recordBaritoneBlockBroken(pos, blockState);";
        assertEquals(1, occurrences(source, hook));
        assertTrue(source.indexOf(hook) > source.indexOf("this.world.removeBlock(pos, false)"));
        assertFalse(source.contains("getMainHandItem()"));
        assertFalse(source.contains("getOffhandItem()"));
        assertFalse(source.contains("use(world, null"));
        assertFalse(source.contains("pickupBlock(null"));
        assertFalse(source.contains("emptyContents(null"));
    }

    @Test
    void hotbarAndTargetStateSelectionRemainExplicit() throws IOException {
        String inventory = Files.readString(INVENTORY);
        String behavior = Files.readString(BEHAVIOR);
        String tools = Files.readString(TOOLS);

        assertTrue(inventory.contains("private static final int HOTBAR_SIZE = 9"));
        assertTrue(inventory.contains("slot < HOTBAR_SIZE"));
        assertTrue(behavior.contains("i < LivingEntityInventory.getHotbarSize()"));
        assertFalse(behavior.contains("for (int i = 1; i < 8; i++)"));
        assertTrue(tools.contains("getBestSlot(BlockState blockState, boolean preferSilkTouch)"));
        assertTrue(tools.contains("calculateSpeedVsBlock(itemStack, blockState)"));
    }

    @Test
    void interactionMutationsCommitOnlySuccessfulWorkingResults() throws IOException {
        String source = Files.readString(INTERACTIONS);

        assertTrue(source.contains("ItemStack working = original.copy()"));
        assertTrue(source.contains("ItemStack itemStack = getStackInHand(user, hand).copy()"));
        assertTrue(source.contains("result.getResult() == InteractionResult.FAIL"));
        assertTrue(source.contains("private void commitHandResult"));
        assertTrue(source.contains("ItemStack remainder = outputStack.copy()"));
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
