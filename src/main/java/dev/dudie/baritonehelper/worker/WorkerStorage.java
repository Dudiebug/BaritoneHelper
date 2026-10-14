package dev.dudie.baritonehelper.worker;

import dev.dudie.baritonehelper.entity.WorkerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.Nullable;

/**
 * Deposits through the block item-handler capability, so any block exposing
 * an inventory is valid storage: vanilla containers as well as Create item
 * vaults, depots, funnels, and other logistics blocks.
 */
public final class WorkerStorage {
    private WorkerStorage() {
    }

    /** Resolves storage at a position, or null when the block has no inventory. */
    public static @Nullable IItemHandler resolve(Level level, BlockPos position) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, position, null);
        if (handler != null) return handler;
        return level.getBlockEntity(position) instanceof Container container
                ? new InvWrapper(container)
                : null;
    }

    public static boolean isStorage(Level level, BlockPos position) {
        return resolve(level, position) != null;
    }

    public static int deposit(WorkerEntity worker, Container destination) {
        return deposit(worker, new InvWrapper(destination));
    }

    public static int deposit(WorkerEntity worker, IItemHandler destination) {
        int movedTotal = 0;

        for (int workerSlot = 0; workerSlot < worker.getContainerSize(); workerSlot++) {
            ItemStack source = worker.getItem(workerSlot);
            if (source.isEmpty() || WorkerEntity.isReservedTool(source)) {
                continue;
            }

            ItemStack remainder = ItemHandlerHelper.insertItemStacked(destination, source.copy(), false);
            int moved = source.getCount() - remainder.getCount();
            if (moved > 0) {
                movedTotal += moved;
                worker.setItem(workerSlot, remainder);
            }
        }

        if (movedTotal > 0) {
            worker.setChanged();
        }
        return movedTotal;
    }

    public static boolean canAcceptAny(WorkerEntity worker, Container destination) {
        return canAcceptAny(worker, new InvWrapper(destination));
    }

    public static boolean canAcceptAny(WorkerEntity worker, IItemHandler destination) {
        for (int sourceSlot = 0; sourceSlot < worker.getContainerSize(); sourceSlot++) {
            ItemStack source = worker.getItem(sourceSlot);
            if (source.isEmpty() || WorkerEntity.isReservedTool(source)) continue;
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(destination, source.copy(), true);
            if (remainder.getCount() < source.getCount()) {
                return true;
            }
        }
        return false;
    }
}
