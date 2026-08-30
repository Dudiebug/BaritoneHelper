package dev.dudie.baritonehelper.gametest;

import dev.dudie.baritonehelper.BaritoneHelper;
import dev.dudie.baritonehelper.entity.WorkerEntity;
import dev.dudie.baritonehelper.worker.WorkerJob;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BaritoneHelper.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BaritoneHelperGameTests {
    private BaritoneHelperGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void ownerInventoryAndJobPersistWithoutTierData(GameTestHelper helper) {
        WorkerEntity original = helper.spawnWithNoFreeWill(
                BaritoneHelper.WORKER_ENTITY.get(), 1, 2, 1);
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        original.bindTo(owner);
        original.setItem(0, new ItemStack(Items.IRON_INGOT, 17));
        original.beginCollection(
                BuiltInRegistries.BLOCK.getKey(
                        net.minecraft.world.level.block.Blocks.IRON_ORE),
                helper.absolutePos(new BlockPos(2, 2, 2)));

        CompoundTag tag = new CompoundTag();
        original.addAdditionalSaveData(tag);

        WorkerEntity restored = BaritoneHelper.WORKER_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(restored != null, "registered worker type must create an entity");
        restored.readAdditionalSaveData(tag);

        helper.assertValueEqual(
                restored.getOwnerUUID(), owner.getUUID(), "persisted owner UUID");
        helper.assertValueEqual(
                restored.getItem(0).getCount(), 17, "persisted inventory count");
        helper.assertValueEqual(
                restored.job(), WorkerJob.COLLECT, "persisted worker job");
        helper.assertFalse(tag.contains("BuddyTier"), "legacy tier data must not be written");
        helper.assertFalse(
                tag.contains("RescueCooldown"), "legacy rescue cooldown must not be written");
        helper.assertFalse(
                tag.contains("TemporaryBlocks"), "legacy rescue blocks must not be written");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void allGameplayDamageLeavesHealthUnchanged(GameTestHelper helper) {
        WorkerEntity worker = helper.spawnWithNoFreeWill(
                BaritoneHelper.WORKER_ENTITY.get(), 1, 2, 1);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 1);
        float health = worker.getHealth();

        worker.hurt(helper.getLevel().damageSources().generic(), 8.0F);
        worker.hurt(helper.getLevel().damageSources().mobAttack(zombie), 8.0F);
        worker.hurt(helper.getLevel().damageSources().fall(), 8.0F);
        worker.hurt(helper.getLevel().damageSources().inFire(), 8.0F);
        worker.hurt(helper.getLevel().damageSources().explosion(zombie, zombie), 8.0F);

        helper.assertValueEqual(worker.getHealth(), health, "worker health");
        helper.assertTrue(worker.isAlive(), "worker must remain alive");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void hostileMobsCannotSelectWorkerAsAttackTarget(GameTestHelper helper) {
        WorkerEntity worker = helper.spawnWithNoFreeWill(
                BaritoneHelper.WORKER_ENTITY.get(), 1, 2, 1);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 1);

        helper.assertFalse(worker.isAttackable(), "worker must not be attackable");
        helper.assertFalse(
                worker.canBeSeenAsEnemy(), "worker must not be eligible as an enemy");
        helper.assertFalse(zombie.canAttack(worker), "hostile mob must reject worker target");
        helper.succeed();
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            timeoutTicks = 80)
    public static void idleWorkerDoesNotFollowOwner(GameTestHelper helper) {
        helper.setBlock(1, 1, 1, net.minecraft.world.level.block.Blocks.STONE);
        WorkerEntity worker = helper.spawn(
                BaritoneHelper.WORKER_ENTITY.get(), 1, 2, 1);
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        moveOwner(owner, helper.absolutePos(new BlockPos(12, 2, 12)));
        worker.bindTo(owner);
        var start = worker.position();

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(
                    worker.position().distanceToSqr(start) < 0.01,
                    "idle worker must remain where it was placed");
            helper.assertValueEqual(
                    worker.level(), helper.getLevel(), "worker dimension");
            helper.succeed();
        });
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            timeoutTicks = 40)
    public static void ownerDismissalDropsContentsOnceReturnsWorkerAndReleasesTickets(
            GameTestHelper helper) {
        WorkerEntity worker = helper.spawnWithNoFreeWill(
                BaritoneHelper.WORKER_ENTITY.get(), 1, 2, 1);
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        worker.bindTo(owner);
        worker.setItem(0, new ItemStack(Items.DIAMOND, 3));
        worker.ensureWorkerTickets();
        var dropArea = worker.getBoundingBox().inflate(2.0);

        helper.assertTrue(worker.workerTicketCount() > 0, "worker must hold chunk tickets");
        owner.setShiftKeyDown(true);
        worker.mobInteract(owner, InteractionHand.MAIN_HAND);

        helper.runAfterDelay(1, () -> {
            helper.assertFalse(worker.isAlive(), "dismissed worker must be removed");
            helper.assertValueEqual(
                    owner.getInventory().countItem(BaritoneHelper.WORKER.get()),
                    1,
                    "returned base worker item");
            List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(
                    ItemEntity.class,
                    dropArea,
                    item -> item.getItem().is(Items.DIAMOND));
            int diamonds = drops.stream().mapToInt(item -> item.getItem().getCount()).sum();
            helper.assertValueEqual(diamonds, 3, "dismissed inventory diamonds");
            helper.assertValueEqual(worker.workerTicketCount(), 0, "released worker tickets");
            helper.succeed();
        });
    }

    private static void moveOwner(
            net.minecraft.world.entity.player.Player player,
            BlockPos position) {
        player.moveTo(
                position.getX() + 0.5,
                position.getY(),
                position.getZ() + 0.5,
                0.0F,
                0.0F);
    }
}
