package dev.dudie.baritonehelper.item;

import dev.dudie.baritonehelper.ActiveWorkerData;
import dev.dudie.baritonehelper.BaritoneHelper;
import dev.dudie.baritonehelper.BaritoneHelperDataComponents;
import dev.dudie.baritonehelper.entity.WorkerEntity;
import dev.dudie.baritonehelper.worker.PackedWorkerData;
import dev.dudie.baritonehelper.worker.PickupState;
import dev.dudie.baritonehelper.worker.WorkerMessages;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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

        ItemStack stack = context.getItemInHand();
        PackedWorkerData packed = stack.get(BaritoneHelperDataComponents.PACKED_WORKER.get());
        if (packed != null) {
            return placePacked(context, packed);
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

        if (!level.addFreshEntity(worker)) {
            worker.discard();
            return InteractionResult.FAIL;
        }
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

    /** Creates a component-backed worker item without dropping or flattening inventory data. */
    public static ItemStack createPackedStack(WorkerEntity worker) {
        return createPackedStack(worker, UUID.randomUUID());
    }

    public static ItemStack createPackedStack(WorkerEntity worker, UUID transactionUuid) {
        ItemStack stack = new ItemStack(BaritoneHelper.BARITONE_HELPER.get());
        stack.set(
                BaritoneHelperDataComponents.PACKED_WORKER.get(),
                PackedWorkerData.capture(worker, transactionUuid, PickupState.COMMITTED));
        return stack;
    }

    public static ItemStack createPackedStack(PackedWorkerData packed) {
        if (packed == null || !packed.isPlaceable()) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(BaritoneHelper.BARITONE_HELPER.get());
        stack.set(BaritoneHelperDataComponents.PACKED_WORKER.get(), packed);
        return stack;
    }

    public static ItemStack packedStack(WorkerEntity worker) {
        return createPackedStack(worker);
    }

    /**
     * Places a packed worker transactionally. No source or owner record is
     * changed until the candidate has passed every validation and spawned.
     */
    public static InteractionResult placePacked(
            UseOnContext context,
            PackedWorkerData packed) {
        if (!(context.getLevel() instanceof ServerLevel level)
                || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }

        ItemStack source = context.getItemInHand();
        PackedWorkerData sourceData = source.get(BaritoneHelperDataComponents.PACKED_WORKER.get());
        if (source.isEmpty()
                || packed == null
                || sourceData == null
                || !packed.equals(sourceData)
                || !packed.isPlaceable()
                || !packed.ownerUuid().equals(player.getUUID())) {
            return InteractionResult.FAIL;
        }

        ActiveWorkerData active = player.getData(BaritoneHelper.ACTIVE_WORKER);
        if (active.uuid().isPresent()) {
            WorkerMessages.send(
                    player,
                    ChatFormatting.RED,
                    "message.baritonehelper.already_active");
            return InteractionResult.FAIL;
        }
        if (uuidInUse(player, packed.workerUuid())) {
            return InteractionResult.FAIL;
        }

        WorkerEntity worker = BaritoneHelper.BARITONE_HELPER_ENTITY.get().create(level);
        if (worker == null) {
            return InteractionResult.FAIL;
        }

        boolean added = false;
        try {
            worker.setUUID(packed.workerUuid());
            var spawn = context.getClickedPos().relative(context.getClickedFace());
            worker.moveTo(
                    spawn.getX() + 0.5,
                    spawn.getY(),
                    spawn.getZ() + 0.5,
                    player.getYRot(),
                    0.0F);
            worker.bindTo(player);

            if (!level.noCollision(worker) || !packed.restoreInto(worker)) {
                return InteractionResult.FAIL;
            }
            worker.setNoAi(false);
            if (!level.noCollision(worker) || !level.addFreshEntity(worker)) {
                return InteractionResult.FAIL;
            }
            added = true;
        } catch (RuntimeException ignored) {
            return InteractionResult.FAIL;
        } finally {
            if (!added) {
                worker.discard();
            }
        }

        active.set(
                worker.getUUID(),
                level.dimension().location().toString(),
                worker.blockPosition());
        source.shrink(1);
        return InteractionResult.CONSUME;
    }

    private static boolean uuidInUse(ServerPlayer player, UUID workerUuid) {
        for (ServerLevel level : player.getServer().getAllLevels()) {
            if (level.getEntity(workerUuid) != null) {
                return true;
            }
        }
        return false;
    }
}
