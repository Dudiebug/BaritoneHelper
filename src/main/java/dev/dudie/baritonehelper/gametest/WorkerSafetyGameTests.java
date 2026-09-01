package dev.dudie.baritonehelper.gametest;

import dev.dudie.baritonehelper.BaritoneHelper;
import dev.dudie.baritonehelper.entity.WorkerEntity;
import dev.dudie.baritonehelper.worker.NoWorkZone;
import dev.dudie.baritonehelper.worker.NoWorkZoneMode;
import dev.dudie.baritonehelper.worker.SearchMode;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BaritoneHelper.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorkerSafetyGameTests {
    private WorkerSafetyGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void roamOnlyBypassesAreaBoundsAndStorageHasItsOwnCommitGate(
            GameTestHelper helper) {
        helper.setBlock(1, 1, 1, Blocks.STONE);
        WorkerEntity worker = helper.spawn(
                BaritoneHelper.BARITONE_HELPER_ENTITY.get(), 1, 2, 1);
        worker.setWorkArea(worker.blockPosition(), 8, 8);

        BlockPos outside = helper.absolutePos(new BlockPos(20, 2, 1));
        helper.getLevel().setBlockAndUpdate(outside, Blocks.STONE.defaultBlockState());
        helper.assertFalse(worker.canEnterAt(outside), "WORK_AREA must bound entry");
        helper.assertFalse(worker.canModifyAt(outside), "WORK_AREA must bound modification");

        worker.configuration().setSearchMode(SearchMode.ROAM);
        helper.assertTrue(worker.canEnterAt(outside), "ROAM may bypass only area bounds");
        helper.assertTrue(worker.canModifyAt(outside), "ROAM may modify outside the area");

        NoWorkZone noModify = new NoWorkZone(
                UUID.randomUUID(), "modify", helper.getLevel().dimension().location().toString(),
                outside, 1, 2, NoWorkZoneMode.NO_MODIFY, true);
        worker.addNoWorkZone(noModify);
        helper.assertTrue(worker.canEnterAt(outside), "NO_MODIFY must remain walkable");
        helper.assertFalse(worker.canInteractAt(outside), "NO_MODIFY must block interaction");
        helper.assertFalse(worker.canModifyAt(outside), "NO_MODIFY must block modification");

        BlockPos noEnter = helper.absolutePos(new BlockPos(4, 2, 1));
        NoWorkZone noEnterZone = new NoWorkZone(
                UUID.randomUUID(), "enter", helper.getLevel().dimension().location().toString(),
                noEnter, 1, 2, NoWorkZoneMode.NO_ENTER, true);
        worker.addNoWorkZone(noEnterZone);
        helper.assertFalse(worker.canEnterAt(noEnter), "NO_ENTER must block entry");
        helper.assertFalse(worker.canInteractAt(noEnter), "NO_ENTER must block interaction");

        BlockPos storage = helper.absolutePos(new BlockPos(2, 1, 2));
        helper.getLevel().setBlockAndUpdate(storage, Blocks.CHEST.defaultBlockState());
        helper.runAfterDelay(1, () -> {
            worker.assignStorage(helper.getLevel(), storage);
            helper.assertTrue(worker.canStoreAt(storage),
                    "designated storage remains usable: storage=" + worker.storagePosition()
                            + ", storageDimension=" + worker.storageDimension()
                            + ", workerDimension=" + helper.getLevel().dimension().location()
                            + ", blockEntity=" + helper.getLevel().getBlockEntity(storage)
                            + ", canEnter=" + worker.canEnterAt(storage)
                            + ", noModify=" + worker.isInsideNoModify(storage)
                            + ", mobGriefing=" + helper.getLevel().getGameRules().getBoolean(
                                    GameRules.RULE_MOBGRIEFING));
            helper.assertFalse(worker.canInteractAt(storage), "generic interaction must not mutate containers");

            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(
                    false, helper.getLevel().getServer());
            try {
                helper.assertFalse(worker.canStoreAt(storage), "mobGriefing must gate storage commits");
                helper.assertFalse(worker.canModifyAt(outside), "mobGriefing must gate modifications");
            } finally {
                helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(
                        true, helper.getLevel().getServer());
            }
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void blockEntitiesAndExclusionsRemainProtectedAtCommit(GameTestHelper helper) {
        helper.setBlock(1, 1, 1, Blocks.STONE);
        WorkerEntity worker = helper.spawn(
                BaritoneHelper.BARITONE_HELPER_ENTITY.get(), 1, 2, 1);
        BlockPos chest = helper.absolutePos(new BlockPos(2, 2, 1));
        helper.getLevel().setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        helper.assertFalse(worker.canInteractAt(chest), "block entities must not be generic targets");
        helper.assertFalse(worker.canModifyAt(chest), "block entities must not be break targets");

        BlockPos excluded = helper.absolutePos(new BlockPos(3, 2, 1));
        helper.getLevel().setBlockAndUpdate(excluded, Blocks.DIRT.defaultBlockState());
        worker.toggleExclusion(BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        helper.assertFalse(worker.canInteractAt(excluded), "excluded blocks must not be interacted with");
        helper.assertFalse(worker.canModifyAt(excluded), "excluded blocks must not be modified");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void noWorkZoneCapRejectsThe129thZone(GameTestHelper helper) {
        WorkerEntity worker = helper.spawn(
                BaritoneHelper.BARITONE_HELPER_ENTITY.get(), 1, 2, 1);
        String dimension = helper.getLevel().dimension().location().toString();
        for (int index = 0; index < 128; index++) {
            helper.assertTrue(worker.addNoWorkZone(new NoWorkZone(
                    UUID.randomUUID(), "zone-" + index, dimension, worker.blockPosition(),
                    0, 0, NoWorkZoneMode.NO_MODIFY, true)),
                    "zone " + index + " should fit below the cap");
        }
        helper.assertFalse(worker.addNoWorkZone(new NoWorkZone(
                UUID.randomUUID(), "overflow", dimension, worker.blockPosition(),
                0, 0, NoWorkZoneMode.NO_MODIFY, true)),
                "the 129th zone must be rejected instead of silently acknowledged");
        helper.assertValueEqual(worker.configuration().noWorkZones().size(), 128,
                "persisted no-work-zone count");
        helper.succeed();
    }
}
