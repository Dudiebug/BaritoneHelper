package dev.dudie.baritonehelper.item;

import dev.dudie.baritonehelper.ActiveWorkerData;
import dev.dudie.baritonehelper.BaritoneHelper;
import dev.dudie.baritonehelper.entity.WorkerEntity;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class WorkerControllerItem extends Item {
    public WorkerControllerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)
                || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }

        WorkerEntity worker = findOwnedWorker(player).orElse(null);
        if (worker == null) {
            message(player, "message.baritonehelper.no_worker", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        if (worker.level() != level) {
            message(player, "message.baritonehelper.other_dimension", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }

        var position = context.getClickedPos();
        var state = level.getBlockState(position);
        if (state.isAir()) {
            return InteractionResult.FAIL;
        }

        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (player.isShiftKeyDown()) {
            BlockEntity blockEntity = level.getBlockEntity(position);
            if (blockEntity instanceof Container) {
                worker.assignStorage(level, position);
                player.displayClientMessage(
                        Component.translatable(
                                "message.baritonehelper.storage_assigned",
                                position.getX(),
                                position.getY(),
                                position.getZ()),
                        true);
            } else {
                boolean excluded = worker.toggleExclusion(blockId);
                player.displayClientMessage(
                        Component.translatable(
                                excluded
                                        ? "message.baritonehelper.excluded"
                                        : "message.baritonehelper.included",
                                blockId.toString()),
                        true);
            }
        } else {
            worker.beginCollection(blockId, position);
            player.displayClientMessage(
                    Component.translatable(
                            "message.baritonehelper.collecting",
                            blockId.toString()),
                    true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level,
            net.minecraft.world.entity.player.Player player,
            InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel)
                || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }

        WorkerEntity worker = findOwnedWorker(serverPlayer).orElse(null);
        if (worker == null) {
            message(serverPlayer, "message.baritonehelper.no_worker", ChatFormatting.RED);
            return InteractionResultHolder.fail(stack);
        }

        var newJob = worker.togglePaused();
        serverPlayer.displayClientMessage(
                Component.translatable(
                        newJob == dev.dudie.baritonehelper.worker.WorkerJob.PAUSED
                                ? "message.baritonehelper.paused"
                                : "message.baritonehelper.resumed"),
                true);
        return InteractionResultHolder.consume(stack);
    }

    public static Optional<WorkerEntity> findOwnedWorker(ServerPlayer player) {
        ActiveWorkerData active = player.getData(BaritoneHelper.ACTIVE_WORKER);

        WorkerEntity recorded = resolveRecordedWorker(player, active);
        if (recorded != null) {
            return Optional.of(recorded);
        }

        for (ServerLevel level : player.getServer().getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (entity instanceof WorkerEntity worker
                        && player.getUUID().equals(worker.getOwnerUUID())) {
                    active.set(
                            worker.getUUID(),
                            level.dimension().location().toString(),
                            worker.blockPosition());
                    return Optional.of(worker);
                }
            }
        }

        active.clear();
        return Optional.empty();
    }

    private static WorkerEntity resolveRecordedWorker(
            ServerPlayer player,
            ActiveWorkerData active) {
        if (active.uuid().isEmpty()) {
            return null;
        }

        ResourceLocation dimensionId = ResourceLocation.tryParse(active.dimension());
        if (dimensionId == null) {
            active.clear();
            return null;
        }

        ServerLevel level = player.getServer().getLevel(
                ResourceKey.create(Registries.DIMENSION, dimensionId));
        if (level == null) {
            active.clear();
            return null;
        }

        level.getChunk(active.position());
        var entity = level.getEntity(active.uuid().orElseThrow());
        if (entity instanceof WorkerEntity worker
                && player.getUUID().equals(worker.getOwnerUUID())) {
            active.set(
                    worker.getUUID(),
                    level.dimension().location().toString(),
                    worker.blockPosition());
            return worker;
        }

        active.clear();
        return null;
    }

    private static void message(
            ServerPlayer player,
            String translationKey,
            ChatFormatting formatting) {
        player.displayClientMessage(
                Component.translatable(translationKey).withStyle(formatting),
                true);
    }
}
