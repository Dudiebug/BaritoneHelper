package dev.dudie.baritonehelper.gametest;

import dev.dudie.baritonehelper.BaritoneHelper;
import dev.dudie.baritonehelper.entity.WorkerEntity;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BaritoneHelper.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RemoteInventoryLifecycleGameTests {
    private static final BlockPos WORKER_POS = new BlockPos(1, 2, 1);

    private RemoteInventoryLifecycleGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    public static void ownerCanOpenBothInventorySizesFromAnySameDimensionDistance(
            GameTestHelper helper) {
        WorkerEntity worker = spawnWorker(helper);
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        worker.bindTo(owner);
        owner.setPos(worker.getX() + 128.0, worker.getY(), worker.getZ() + 128.0);

        helper.assertTrue(worker.stillValid(owner),
                "an owned worker remains valid at arbitrary same-dimension distance");
        AbstractContainerMenu base = worker.createMenu(1, owner.getInventory(), owner);
        helper.assertTrue(base != null, "owner must be able to open the 27-slot inventory remotely");

        owner.setItemInHand(
                InteractionHand.MAIN_HAND,
                new ItemStack(BaritoneHelper.CARGO_UPGRADE.get()));
        helper.assertValueEqual(
                worker.mobInteract(owner, InteractionHand.MAIN_HAND),
                InteractionResult.SUCCESS,
                "cargo upgrade interaction");
        helper.assertValueEqual(worker.getContainerSize(), WorkerEntity.EXPANDED_SLOTS,
                "cargo upgrade must expose the 54-slot inventory");
        helper.assertTrue(worker.stillValid(owner),
                "the expanded inventory remains valid remotely");
        AbstractContainerMenu expanded = worker.createMenu(2, owner.getInventory(), owner);
        helper.assertTrue(expanded != null, "owner must be able to open the 54-slot inventory remotely");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 60)
    public static void nonOwnerAndCrossDimensionOwnerCannotOpenInventory(
            GameTestHelper helper) {
        WorkerEntity worker = spawnWorker(helper);
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer nonOwner = helper.makeMockServerPlayerInLevel();
        worker.bindTo(owner);
        nonOwner.setPos(worker.getX(), worker.getY(), worker.getZ());

        helper.assertFalse(worker.stillValid(nonOwner), "non-owner must not be valid for the inventory");
        helper.assertTrue(worker.createMenu(1, nonOwner.getInventory(), nonOwner) == null,
                "non-owner must not receive an inventory menu");

        ServerLevel otherDimension = Objects.requireNonNull(
                helper.getLevel().getServer().getLevel(Level.NETHER),
                "Nether level must exist during GameTests");
        owner.changeDimension(new DimensionTransition(
                otherDimension,
                new Vec3(0.5, 80.0, 0.5),
                Vec3.ZERO,
                0.0F,
                0.0F,
                DimensionTransition.DO_NOTHING));

        helper.runAfterDelay(1, () -> {
            helper.assertFalse(worker.stillValid(owner),
                    "an owner in another dimension must not remain valid");
            helper.assertTrue(worker.createMenu(2, owner.getInventory(), owner) == null,
                    "an owner in another dimension must not receive an inventory menu");
            helper.succeed();
        });
    }

    private static WorkerEntity spawnWorker(GameTestHelper helper) {
        helper.setBlock(1, 1, 1, net.minecraft.world.level.block.Blocks.STONE);
        return helper.spawn(
                BaritoneHelper.BARITONE_HELPER_ENTITY.get(),
                WORKER_POS.getX(),
                WORKER_POS.getY(),
                WORKER_POS.getZ());
    }
}
