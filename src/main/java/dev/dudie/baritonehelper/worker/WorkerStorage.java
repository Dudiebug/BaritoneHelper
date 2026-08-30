package dev.dudie.baritonehelper.worker;

import dev.dudie.baritonehelper.entity.WorkerEntity;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public final class WorkerStorage {
    private WorkerStorage() {
    }

    public static int deposit(WorkerEntity worker, Container destination) {
        int movedTotal = 0;

        for (int workerSlot = 0; workerSlot < worker.getContainerSize(); workerSlot++) {
            ItemStack source = worker.getItem(workerSlot);
            if (source.isEmpty()) {
                continue;
            }

            int before = source.getCount();
            moveInto(destination, source);
            movedTotal += before - source.getCount();

            if (source.isEmpty()) {
                worker.setItem(workerSlot, ItemStack.EMPTY);
            } else {
                worker.setItem(workerSlot, source);
            }
        }

        if (movedTotal > 0) {
            destination.setChanged();
            worker.setChanged();
        }
        return movedTotal;
    }

    private static void moveInto(Container destination, ItemStack source) {
        for (int slot = 0; slot < destination.getContainerSize() && !source.isEmpty(); slot++) {
            ItemStack existing = destination.getItem(slot);
            if (!existing.isEmpty()
                    && destination.canPlaceItem(slot, source)
                    && ItemStack.isSameItemSameComponents(existing, source)) {
                int limit = Math.min(destination.getMaxStackSize(), existing.getMaxStackSize());
                int moved = Math.min(source.getCount(), limit - existing.getCount());
                if (moved > 0) {
                    existing.grow(moved);
                    source.shrink(moved);
                    destination.setItem(slot, existing);
                }
            }
        }

        for (int slot = 0; slot < destination.getContainerSize() && !source.isEmpty(); slot++) {
            if (destination.getItem(slot).isEmpty() && destination.canPlaceItem(slot, source)) {
                int limit = Math.min(destination.getMaxStackSize(), source.getMaxStackSize());
                int moved = Math.min(source.getCount(), limit);
                destination.setItem(slot, source.copyWithCount(moved));
                source.shrink(moved);
            }
        }
    }
}
