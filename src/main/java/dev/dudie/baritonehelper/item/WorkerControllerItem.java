package dev.dudie.baritonehelper.item;

import dev.dudie.baritonehelper.ActiveWorkerData;
import dev.dudie.baritonehelper.BaritoneHelper;
import dev.dudie.baritonehelper.entity.WorkerEntity;
import dev.dudie.baritonehelper.worker.WorkerMessages;
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
            WorkerMessages.send(
                    player,
                    ChatFormatting.RED,
                    "message.baritonehelper.no_worker");
            return InteractionResult.FAIL;
        }
        if (worker.level() != level) {
            WorkerMessages.send(
                    player,
                    ChatFormatting.RED,
                    "message.baritonehelper.other_dimension");
            return InteractionResult.FAIL;
        }

        var position = context.getClickedPos();
        var state = level.getBlockState(position);
        if (state.isAir()) {
            WorkerMessages.send(
                    player,
                    ChatFormatting.RED,
                    "message.baritonehelper.invalid_target");
            return InteractionResult.FAIL;
        }

        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (player.isShiftKeyDown()) {
            BlockEntity blockEntity = level.getBlockEntity(position);
            if (blockEntity instanceof Container) {
                worker.assignStorage(level, position);
                WorkerMessages.send(
                        player,
                        ChatFormatting.GREEN,
                        "message.baritonehelper.storage_assigned",
                        position.getX(),
                        position.getY(),
                        position.getZ());
            } else {
                boolean excluded = worker.toggleExclusion(blockId);
                WorkerMessages.send(
                        player,
                        excluded ? ChatFormatting.YELLOW : ChatFormatting.GREEN,
                        excluded
                                ? "message.baritonehelper.excluded"
                                : "message.baritonehelper.included",
                        state.getBlock().getName());
            }
            return InteractionResult.CONSUME;
        }

        if (level.getBlockEntity(position) != null
                || state.getDestroySpeed(level, position) < 0.0F) {
            WorkerMessages.send(
                    player,
                    ChatFormatting.RED,
                    "message.baritonehelper.invalid_target");
            return InteractionResult.FAIL;
        }

        Optional<ResourceLocation> previous = worker.configureTarget(blockId, position);
        Component targetName = state.getBlock().getName();
        if (previous.isEmpty()) {
            WorkerMessages.send(
                    player,
                    ChatFormatting.GREEN,
                    "message.baritonehelper.target_set",
                    targetName);
        } else if (previous.get().equals(blockId)) {
            WorkerMessages.send(
                    player,
                    ChatFormatting.GREEN,
                    "message.baritonehelper.target_confirmed",
                    targetName);
        } else {
            Component previousName = BuiltInRegistries.BLOCK.get(previous.get()).getName();
            WorkerMessages.send(
                    player,
                    ChatFormatting.GREEN,
                    "message.baritonehelper.target_changed",
                    previousName,
                    targetName);
        }
        WorkerMessages.send(
                player,
                ChatFormatting.GRAY,
                "message.baritonehelper.target_ready");
        worker.openDashboard(player);
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
            WorkerMessages.send(
                    serverPlayer,
                    ChatFormatting.RED,
                    "message.baritonehelper.no_worker");
            return InteractionResultHolder.fail(stack);
        }
        if (worker.level() != level) {
            WorkerMessages.send(
                    serverPlayer,
                    ChatFormatting.RED,
                    "message.baritonehelper.other_dimension");
            return InteractionResultHolder.fail(stack);
        }

        worker.openDashboard(serverPlayer);
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
}
