package dev.dudie.baritonehelper.worker;

import dev.dudie.baritonehelper.ActiveWorkerData;
import dev.dudie.baritonehelper.BaritoneHelper;
import dev.dudie.baritonehelper.BaritoneHelperDataComponents;
import dev.dudie.baritonehelper.entity.WorkerEntity;
import dev.dudie.baritonehelper.item.WorkerItem;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** Canonical, server-thread transaction used by local and remote pickup. */
public final class WorkerPickupService {
    public enum Result {
        COMMITTED(true, "ok"),
        NOT_OWNER(false, "not_owner"),
        CONFLICT(false, "pickup_conflict"),
        DELIVERY_FAILED(false, "pickup_delivery_failed");

        private final boolean success;
        private final String errorCode;

        Result(boolean success, String errorCode) {
            this.success = success;
            this.errorCode = errorCode;
        }

        public boolean success() { return success; }
        public String errorCode() { return errorCode; }
    }

    private WorkerPickupService() {}

    public static Result pickup(ServerPlayer owner, WorkerEntity worker, UUID transactionUuid) {
        Objects.requireNonNull(transactionUuid, "transactionUuid");
        if (owner == null || worker == null || worker.isRemoved()
                || !worker.isOwnedByPlayer(owner)) return Result.NOT_OWNER;
        if (!owner.getServer().isSameThread()) {
            throw new IllegalStateException("worker pickup must run on the server thread");
        }

        ActiveWorkerData active = owner.getData(BaritoneHelper.ACTIVE_WORKER);
        if (active.uuid().isEmpty()) {
            active.set(worker.getUUID(), worker.level().dimension().location().toString(), worker.blockPosition());
        }
        active.updateLocation(
                worker.getUUID(), worker.level().dimension().location().toString(), worker.blockPosition());
        if (transactionUuid.equals(active.pickupTransaction().orElse(null))
                && active.pickupState() != PickupState.LIVE) {
            if (reconcile(owner, worker)) return Result.COMMITTED;
            if (active.pickupState() != PickupState.LIVE) return Result.CONFLICT;
        }
        if (!active.beginPickup(worker.getUUID(), transactionUuid)) return Result.CONFLICT;

        WorkerEntity.PickupFreeze freeze = null;
        try {
            freeze = worker.freezeForPickup();
            ItemStack packed = WorkerItem.createPackedStack(worker, transactionUuid);
            PackedWorkerData snapshot = packed.get(BaritoneHelperDataComponents.PACKED_WORKER.get());
            if (!active.attachPickupSnapshot(worker.getUUID(), transactionUuid, snapshot)
                    || !deliver(owner, packed)) {
                active.rollbackPickup(worker.getUUID(), transactionUuid);
                worker.rollbackPickup(freeze);
                return Result.DELIVERY_FAILED;
            }
        } catch (RuntimeException error) {
            active.rollbackPickup(worker.getUUID(), transactionUuid);
            if (freeze == null) worker.restoreInterruptedPickup();
            else worker.rollbackPickup(freeze);
            return Result.DELIVERY_FAILED;
        }

        // These transitions cannot fail after beginPickup on the same server tick.
        if (!active.commitPickup(worker.getUUID(), transactionUuid)) {
            throw new IllegalStateException("pickup transaction changed during delivery");
        }
        worker.commitPickupRemoval();
        if (!active.clearCommittedPickup(worker.getUUID(), transactionUuid)) {
            throw new IllegalStateException("pickup transaction changed after worker removal");
        }
        return Result.COMMITTED;
    }

    /** Reconciles durable pickup crash windows when the owner is next online. */
    public static boolean reconcile(ServerPlayer owner, WorkerEntity worker) {
        if (owner == null || !owner.getServer().isSameThread()) return false;
        ActiveWorkerData active = owner.getData(BaritoneHelper.ACTIVE_WORKER);
        UUID workerUuid = active.uuid().orElse(null);
        if (workerUuid == null || active.pickupState() == PickupState.LIVE) return false;
        if (worker != null && (worker.isRemoved()
                || !workerUuid.equals(worker.getUUID())
                || !worker.isOwnedByPlayer(owner))) worker = null;

        UUID transactionUuid = active.pickupTransaction().orElse(null);
        if (transactionUuid == null) {
            if (worker != null) {
                active.set(workerUuid, worker.level().dimension().location().toString(), worker.blockPosition());
                worker.restoreInterruptedPickup();
            }
            return false;
        }

        if (deliveredItemExists(owner, workerUuid, transactionUuid)) {
            if (active.pickupState() == PickupState.PENDING
                    && !active.commitPickup(workerUuid, transactionUuid)) return false;
            if (worker != null) worker.commitPickupRemoval();
            active.clearCommittedPickup(workerUuid, transactionUuid);
            return true;
        }

        if (active.pickupState() == PickupState.PENDING
                && worker != null
                && active.restoreLivePickup(workerUuid, transactionUuid)) {
            worker.restoreInterruptedPickup();
            return false;
        }

        PackedWorkerData snapshot = active.pickupSnapshot()
                .filter(data -> data.isPlaceable()
                        && workerUuid.equals(data.workerUuid())
                        && owner.getUUID().equals(data.ownerUuid())
                        && transactionUuid.equals(data.transactionUuid()))
                .orElse(null);
        if (snapshot == null && worker != null && active.pickupState() == PickupState.COMMITTED) {
            try {
                snapshot = PackedWorkerData.capture(worker, transactionUuid, PickupState.COMMITTED);
            } catch (RuntimeException ignored) {
                return false;
            }
        }
        if (snapshot == null || !deliver(owner, WorkerItem.createPackedStack(snapshot))) return false;
        if (active.pickupState() == PickupState.PENDING
                && !active.commitPickup(workerUuid, transactionUuid)) return false;
        if (worker != null) worker.commitPickupRemoval();
        return active.clearCommittedPickup(workerUuid, transactionUuid);
    }

    private static boolean deliver(ServerPlayer owner, ItemStack packed) {
        if (owner.getInventory().add(packed) && packed.isEmpty()) return true;
        if (packed.isEmpty()) return true;
        ServerLevel level = (ServerLevel) owner.level();
        ItemEntity item = new ItemEntity(level, owner.getX(), owner.getY(), owner.getZ(), packed);
        item.setTarget(owner.getUUID());
        item.setPickUpDelay(0);
        return level.addFreshEntity(item);
    }

    private static boolean deliveredItemExists(
            ServerPlayer owner, UUID workerUuid, UUID transactionUuid) {
        for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
            if (matches(owner.getInventory().getItem(slot), owner.getUUID(), workerUuid, transactionUuid)) {
                return true;
            }
        }
        for (ServerLevel level : owner.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof ItemEntity item
                        && matches(item.getItem(), owner.getUUID(), workerUuid, transactionUuid)) return true;
            }
        }
        return false;
    }

    private static boolean matches(
            ItemStack stack, UUID ownerUuid, UUID workerUuid, UUID transactionUuid) {
        PackedWorkerData data = stack.get(BaritoneHelperDataComponents.PACKED_WORKER.get());
        return data != null
                && data.isPlaceable()
                && ownerUuid.equals(data.ownerUuid())
                && workerUuid.equals(data.workerUuid())
                && transactionUuid.equals(data.transactionUuid());
    }
}
