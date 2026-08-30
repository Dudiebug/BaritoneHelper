package dev.dudie.baritonehelper.item;

import dev.dudie.baritonehelper.ActiveWorkerData;
import dev.dudie.baritonehelper.BaritoneHelper;
import dev.dudie.baritonehelper.entity.WorkerEntity;
import dev.dudie.baritonehelper.worker.WorkerMessages;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
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

        // World clicks are interpreted only by an explicit, server-tracked
        // selection mode.  In normal use there is deliberately no
        // configureTarget(blockId, position) path: exact target selection is
        // performed by the searchable block picker in the dashboard.
        var zoneSelection = worker.consumeZoneSelection(player);
        if (zoneSelection != null && worker.updateNoWorkZoneCenter(zoneSelection, context.getClickedPos())) {
            WorkerMessages.send(player, ChatFormatting.GREEN,
                    "message.baritonehelper.zone_center_changed");
        } else if (worker.consumeAreaSelection(player)) {
            worker.setWorkArea(context.getClickedPos(), worker.workAreaHorizontalRadius(), worker.workAreaVerticalRadius());
            WorkerMessages.send(player, ChatFormatting.GREEN,
                    "message.baritonehelper.work_area_changed");
        } else if (worker.consumeStorageSelection(player)
                && level.getBlockEntity(context.getClickedPos()) instanceof Container) {
            var position = context.getClickedPos();
            worker.assignStorage(level, position);
            WorkerMessages.send(player, ChatFormatting.GREEN,
                    "message.baritonehelper.storage_assigned",
                    position.getX(), position.getY(), position.getZ());
        }
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
