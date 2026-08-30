package dev.dudie.baritonehelper.item;

import dev.dudie.baritonehelper.ActiveWorkerData;
import dev.dudie.baritonehelper.BaritoneHelper;
import dev.dudie.baritonehelper.entity.WorkerEntity;
import dev.dudie.baritonehelper.worker.WorkerMessages;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public final class WorkerItem extends Item {
    public WorkerItem(Properties properties) {
        super(properties);
    }

    @Override
    public String getDescriptionId() {
        return "item.baritonehelper.baritone_helper";
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)
                || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }

        ActiveWorkerData active = player.getData(BaritoneHelper.ACTIVE_WORKER);
        WorkerControllerItem.findOwnedWorker(player);
        if (active.uuid().isPresent()) {
            player.displayClientMessage(
                    Component.translatable("message.baritonehelper.already_active")
                            .withStyle(ChatFormatting.RED),
                    false);
            return InteractionResult.FAIL;
        }

        WorkerEntity worker = BaritoneHelper.BARITONE_HELPER_ENTITY.get().create(level);
        if (worker == null) {
            return InteractionResult.FAIL;
        }

        var spawn = context.getClickedPos().relative(context.getClickedFace());
        worker.moveTo(
                spawn.getX() + 0.5,
                spawn.getY(),
                spawn.getZ() + 0.5,
                player.getYRot(),
                0.0F);
        worker.bindTo(player);

        if (!level.noCollision(worker)) {
            WorkerMessages.send(
                    player,
                    ChatFormatting.RED,
                    "message.baritonehelper.cannot_place");
            return InteractionResult.FAIL;
        }

        level.addFreshEntity(worker);
        active.set(
                worker.getUUID(),
                level.dimension().location().toString(),
                worker.blockPosition());
        context.getItemInHand().consume(1, player);
        WorkerMessages.send(
                player,
                ChatFormatting.GREEN,
                "message.baritonehelper.placed");
        WorkerMessages.send(
                player,
                ChatFormatting.GRAY,
                "message.baritonehelper.placed_hint");
        return InteractionResult.CONSUME;
    }
}
