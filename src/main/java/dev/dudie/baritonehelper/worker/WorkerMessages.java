package dev.dudie.baritonehelper.worker;

import dev.dudie.baritonehelper.entity.WorkerEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

public final class WorkerMessages {
    private WorkerMessages() {
    }

    public static void send(
            ServerPlayer player,
            ChatFormatting formatting,
            String translationKey,
            Object... arguments) {
        MutableComponent message = Component.literal("[Baritone Helper] ")
                .withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.translatable(translationKey, arguments).withStyle(formatting));
        player.displayClientMessage(message, false);
    }

    public static Component targetName(WorkerEntity worker) {
        return worker.targetBlockId()
                .map(id -> BuiltInRegistries.BLOCK.get(id))
                .filter(block -> block != Blocks.AIR)
                .map(block -> (Component) block.getName())
                .orElse(Component.translatable("screen.baritonehelper.not_set"));
    }
}
