package dev.dudie.buddybot.item;

import dev.dudie.buddybot.BuddyBot;
import dev.dudie.buddybot.entity.BuddyBotEntity;
import dev.dudie.buddybot.logic.BuddyBotTier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public final class BuddyBotItem extends Item {
    private final BuddyBotTier tier;

    public BuddyBotItem(BuddyBotTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public BuddyBotTier tier() { return tier; }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)
                || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        var active = player.getData(BuddyBot.ACTIVE_BUDDY);
        repairStaleRecord(player, active);
        boolean loadedOwnedBot = false;
        for (ServerLevel candidateLevel : player.getServer().getAllLevels()) {
            for (var candidate : candidateLevel.getAllEntities()) {
                if (candidate instanceof BuddyBotEntity bot
                        && player.getUUID().equals(bot.getOwnerUUID())) {
                    loadedOwnedBot = true;
                    break;
                }
            }
            if (loadedOwnedBot) break;
        }
        if (active.uuid().isPresent() || loadedOwnedBot) {
            player.displayClientMessage(Component.translatable("message.buddybot.already_active")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        BuddyBotEntity bot = BuddyBot.BUDDY_BOT_ENTITY.get().create(level);
        if (bot == null) return InteractionResult.FAIL;
        var spawn = context.getClickedPos().relative(context.getClickedFace());
        bot.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                player.getYRot(), 0.0F);
        bot.bindTo(player, tier);
        if (!level.noCollision(bot)) return InteractionResult.FAIL;
        level.addFreshEntity(bot);
        active.set(bot.getUUID(), level.dimension().location().toString(), bot.blockPosition());
        context.getItemInHand().consume(1, player);
        return InteractionResult.CONSUME;
    }

    private static void repairStaleRecord(ServerPlayer player, dev.dudie.buddybot.ActiveBuddyData active) {
        if (active.uuid().isEmpty()) return;
        ResourceLocation dimensionId = ResourceLocation.tryParse(active.dimension());
        if (dimensionId == null) return;
        ServerLevel recordedLevel = player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
        if (recordedLevel == null) return;
        recordedLevel.getChunk(active.position());
        if (recordedLevel.getEntity(active.uuid().orElseThrow()) == null) active.clear();
    }
}
