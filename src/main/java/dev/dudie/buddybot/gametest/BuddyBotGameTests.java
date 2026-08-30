package dev.dudie.buddybot.gametest;

import dev.dudie.buddybot.BuddyBot;
import dev.dudie.buddybot.entity.BuddyBotEntity;
import dev.dudie.buddybot.logic.BuddyBotTier;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BuddyBot.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BuddyBotGameTests {
    private BuddyBotGameTests() {}

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void entitySpawnsWithRegisteredType(GameTestHelper helper) {
        BuddyBotEntity bot = helper.spawnWithNoFreeWill(BuddyBot.BUDDY_BOT_ENTITY.get(), 1, 2, 1);
        helper.assertTrue(bot.isAlive(), "BuddyBot must spawn alive");
        helper.assertValueEqual(bot.tier(), BuddyBotTier.BASIC, "default BuddyBot tier");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void ownerAndTierPersistInEntityNbt(GameTestHelper helper) {
        BuddyBotEntity original = helper.spawnWithNoFreeWill(BuddyBot.BUDDY_BOT_ENTITY.get(), 1, 2, 1);
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        original.bindTo(owner, BuddyBotTier.MK3);
        CompoundTag tag = new CompoundTag();
        original.addAdditionalSaveData(tag);

        BuddyBotEntity restored = BuddyBot.BUDDY_BOT_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(restored != null, "registered entity type must create BuddyBot");
        restored.readAdditionalSaveData(tag);
        helper.assertValueEqual(restored.getOwnerUUID(), owner.getUUID(), "persisted owner UUID");
        helper.assertValueEqual(restored.tier(), BuddyBotTier.MK3, "persisted tier");
        helper.succeed();
    }
}
