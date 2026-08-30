package dev.dudie.baritonehelper.worker;

import dev.dudie.baritonehelper.entity.WorkerEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;

public final class WorkerInventory {
    private WorkerInventory() {
    }

    public static boolean canFitAll(WorkerEntity worker, List<ItemStack> incoming) {
        List<ItemStack> simulated = new ArrayList<>(worker.getContainerSize());
        for (int slot = 0; slot < worker.getContainerSize(); slot++) {
            simulated.add(worker.getItem(slot).copy());
        }
        for (ItemStack stack : incoming) {
            if (!insert(simulated, stack.copy()).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public static boolean insertAll(WorkerEntity worker, List<ItemStack> incoming) {
        if (!canFitAll(worker, incoming)) {
            return false;
        }

        for (ItemStack stack : incoming) {
            ItemStack remainder = insert(worker, stack.copy());
            if (!remainder.isEmpty()) {
                throw new IllegalStateException("Simulated worker insertion diverged from actual insertion");
            }
        }
        worker.setChanged();
        return true;
    }

    private static ItemStack insert(List<ItemStack> slots, ItemStack incoming) {
        if (incoming.isEmpty()) {
            return ItemStack.EMPTY;
        }

        for (ItemStack existing : slots) {
            if (incoming.isEmpty()) {
                break;
            }
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, incoming)) {
                int limit = Math.min(existing.getMaxStackSize(), incoming.getMaxStackSize());
                int moved = Math.min(incoming.getCount(), limit - existing.getCount());
                if (moved > 0) {
                    existing.grow(moved);
                    incoming.shrink(moved);
                }
            }
        }

        for (int slot = 0; slot < slots.size() && !incoming.isEmpty(); slot++) {
            if (slots.get(slot).isEmpty()) {
                int moved = Math.min(incoming.getCount(), incoming.getMaxStackSize());
                slots.set(slot, incoming.copyWithCount(moved));
                incoming.shrink(moved);
            }
        }
        return incoming;
    }

    private static ItemStack insert(WorkerEntity worker, ItemStack incoming) {
        if (incoming.isEmpty()) {
            return ItemStack.EMPTY;
        }

        for (int slot = 0; slot < worker.getContainerSize() && !incoming.isEmpty(); slot++) {
            ItemStack existing = worker.getItem(slot);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, incoming)) {
                int limit = Math.min(existing.getMaxStackSize(), incoming.getMaxStackSize());
                int moved = Math.min(incoming.getCount(), limit - existing.getCount());
                if (moved > 0) {
                    existing.grow(moved);
                    incoming.shrink(moved);
                    worker.setItem(slot, existing);
                }
            }
        }

        for (int slot = 0; slot < worker.getContainerSize() && !incoming.isEmpty(); slot++) {
            if (worker.getItem(slot).isEmpty()) {
                int moved = Math.min(incoming.getCount(), incoming.getMaxStackSize());
                worker.setItem(slot, incoming.copyWithCount(moved));
                incoming.shrink(moved);
            }
        }
        return incoming;
    }
}
