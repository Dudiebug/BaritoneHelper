package dev.dudie.baritonehelper.gametest;

import dev.dudie.baritonehelper.BaritoneHelper;
import dev.dudie.baritonehelper.entity.WorkerEntity;
import dev.dudie.baritonehelper.worker.MinerTier;
import dev.dudie.baritonehelper.worker.SearchMode;
import dev.dudie.baritonehelper.worker.WorkerBlockReason;
import dev.dudie.baritonehelper.worker.WorkerJob;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BaritoneHelper.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MinerProgressionGameTests {
    private static final BlockPos WORKER_POS = new BlockPos(1, 2, 1);

    private MinerProgressionGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    public static void craftedWorkerStartsAtAndesiteWithLimits(GameTestHelper helper) {
        WorkerEntity worker = spawnWorker(helper);
        worker.initializeCraftedWorker();

        helper.assertValueEqual(worker.minerTier(), MinerTier.ANDESITE, "crafted tier");
        helper.assertTrue(worker.getItem(0).is(Items.IRON_PICKAXE), "starter pickaxe");
        helper.assertTrue(!worker.getItem(0).isEnchanted(), "starter pickaxe must be unenchanted");
        helper.assertValueEqual(worker.workAreaHorizontalRadius(), 128, "andesite horizontal cap");
        helper.assertValueEqual(worker.workAreaVerticalRadius(), 64, "andesite vertical cap");

        worker.setWorkArea(worker.blockPosition(), 500, 120);
        helper.assertValueEqual(worker.workAreaHorizontalRadius(), 128, "radius stays clamped");
        helper.assertTrue(!worker.setSearchMode(SearchMode.ROAM), "andesite must refuse Roam");
        helper.assertValueEqual(worker.searchMode(), SearchMode.WORK_AREA, "search mode");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    public static void kitsUpgradeOneTierAtATimeAndGateCargo(GameTestHelper helper) {
        WorkerEntity worker = spawnWorker(helper);
        ServerPlayer owner = GameTestPlayers.mockServerPlayer(helper);
        worker.bindTo(owner);
        worker.initializeCraftedWorker();

        interact(worker, owner, new ItemStack(BaritoneHelper.CARGO_UPGRADE.get()));
        helper.assertValueEqual(worker.cargoUpgrades(), 0, "andesite refuses cargo");
        helper.assertValueEqual(worker.getContainerSize(), WorkerEntity.BASE_SLOTS, "andesite slots");

        // Mock players are creative, so item consumption is not observable here.
        interact(worker, owner, new ItemStack(BaritoneHelper.PRECISION_UPGRADE_KIT.get()));
        helper.assertValueEqual(worker.minerTier(), MinerTier.ANDESITE, "tiers cannot be skipped");

        interact(worker, owner, new ItemStack(BaritoneHelper.BRASS_UPGRADE_KIT.get()));
        helper.assertValueEqual(worker.minerTier(), MinerTier.BRASS, "brass upgrade");
        helper.assertTrue(!worker.setSearchMode(SearchMode.ROAM), "brass must refuse Roam");

        interact(worker, owner, new ItemStack(BaritoneHelper.CARGO_UPGRADE.get()));
        helper.assertValueEqual(worker.getContainerSize(), WorkerEntity.EXPANDED_SLOTS, "brass cargo");

        interact(worker, owner, new ItemStack(BaritoneHelper.PRECISION_UPGRADE_KIT.get()));
        helper.assertValueEqual(worker.minerTier(), MinerTier.PRECISION, "precision upgrade");
        helper.assertTrue(worker.setSearchMode(SearchMode.ROAM), "precision allows Roam");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    public static void andesiteWorkerPausesWhenOwnerIsOffline(GameTestHelper helper) {
        helper.setBlock(1, 1, 3, Blocks.COBBLESTONE);
        WorkerEntity worker = spawnWorker(helper);
        worker.setOwnerUUID(UUID.randomUUID());
        worker.initializeCraftedWorker();
        worker.configureTarget(BuiltInRegistries.BLOCK.getKey(Blocks.COBBLESTONE), worker.blockPosition());
        worker.startJob();

        helper.succeedWhen(() -> {
            helper.assertValueEqual(worker.job(), WorkerJob.BLOCKED, "offline owner blocks andesite job");
            helper.assertValueEqual(worker.blockReason(), WorkerBlockReason.OWNER_OFFLINE, "block reason");
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    public static void tierPersistsAndLegacySavesLoadAsPrecision(GameTestHelper helper) {
        WorkerEntity worker = spawnWorker(helper);
        worker.initializeCraftedWorker();
        worker.upgradeTier(MinerTier.BRASS);

        CompoundTag saved = new CompoundTag();
        worker.addAdditionalSaveData(saved);
        WorkerEntity restored = spawnWorker(helper);
        restored.readAdditionalSaveData(saved);
        helper.assertValueEqual(restored.minerTier(), MinerTier.BRASS, "saved tier round-trips");

        saved.remove("MinerTier");
        WorkerEntity legacy = spawnWorker(helper);
        legacy.readAdditionalSaveData(saved);
        helper.assertValueEqual(legacy.minerTier(), MinerTier.PRECISION, "legacy saves keep full capability");
        helper.succeed();
    }

    private static void interact(WorkerEntity worker, ServerPlayer owner, ItemStack stack) {
        owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
        worker.mobInteract(owner, InteractionHand.MAIN_HAND);
    }

    private static WorkerEntity spawnWorker(GameTestHelper helper) {
        helper.setBlock(1, 1, 1, Blocks.STONE);
        return helper.spawn(
                BaritoneHelper.BARITONE_HELPER_ENTITY.get(),
                WORKER_POS.getX(),
                WORKER_POS.getY(),
                WORKER_POS.getZ());
    }
}
