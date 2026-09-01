package dev.dudie.baritonehelper;

import java.util.Optional;
import java.util.UUID;
import dev.dudie.baritonehelper.worker.PackedWorkerData;
import dev.dudie.baritonehelper.worker.PickupState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class ActiveWorkerData implements INBTSerializable<CompoundTag> {
    public static final int CURRENT_SCHEMA = 3;
    public static final int SCHEMA_VERSION = CURRENT_SCHEMA;

    private UUID uuid;
    private String dimension = "";
    private BlockPos position = BlockPos.ZERO;
    private int schema = CURRENT_SCHEMA;
    private UUID pickupTransaction;
    private PickupState pickupState = PickupState.LIVE;
    private PackedWorkerData pickupSnapshot;

    public Optional<UUID> uuid() {
        return Optional.ofNullable(uuid);
    }

    public String dimension() {
        return dimension;
    }

    public BlockPos position() {
        return position;
    }

    public int schema() {
        return schema;
    }

    public int schemaVersion() {
        return schema;
    }

    public Optional<UUID> pickupTransaction() {
        return Optional.ofNullable(pickupTransaction);
    }

    public PickupState pickupState() {
        return pickupState;
    }

    public Optional<PackedWorkerData> pickupSnapshot() {
        return Optional.ofNullable(pickupSnapshot);
    }

    public boolean beginPickup(UUID workerUuid, UUID transactionUuid) {
        if (!matches(workerUuid) || transactionUuid == null
                || pickupState == PickupState.COMMITTED) return false;
        if (pickupState == PickupState.PENDING) {
            return transactionUuid.equals(pickupTransaction);
        }
        pickupTransaction = transactionUuid;
        pickupState = PickupState.PENDING;
        pickupSnapshot = null;
        return true;
    }

    public boolean attachPickupSnapshot(
            UUID workerUuid, UUID transactionUuid, PackedWorkerData snapshot) {
        if (!matchesPickup(workerUuid, transactionUuid, PickupState.PENDING)
                || snapshot == null
                || !snapshot.isPlaceable()
                || !workerUuid.equals(snapshot.workerUuid())
                || !transactionUuid.equals(snapshot.transactionUuid())) return false;
        pickupSnapshot = snapshot;
        return true;
    }

    public boolean rollbackPickup(UUID workerUuid, UUID transactionUuid) {
        if (!matchesPickup(workerUuid, transactionUuid, PickupState.PENDING)) return false;
        pickupTransaction = null;
        pickupState = PickupState.LIVE;
        pickupSnapshot = null;
        return true;
    }

    public boolean commitPickup(UUID workerUuid, UUID transactionUuid) {
        if (!matchesPickup(workerUuid, transactionUuid, PickupState.PENDING)) return false;
        pickupState = PickupState.COMMITTED;
        return true;
    }

    public boolean clearCommittedPickup(UUID workerUuid, UUID transactionUuid) {
        if (!matchesPickup(workerUuid, transactionUuid, PickupState.COMMITTED)) return false;
        clear();
        return true;
    }

    public boolean restoreLivePickup(UUID workerUuid, UUID transactionUuid) {
        if (!matches(workerUuid) || transactionUuid == null
                || !transactionUuid.equals(pickupTransaction)
                || pickupState != PickupState.PENDING) return false;
        pickupTransaction = null;
        pickupState = PickupState.LIVE;
        pickupSnapshot = null;
        return true;
    }

    public boolean isSupportedSchema() {
        return schema >= 1 && schema <= CURRENT_SCHEMA;
    }

    public boolean matches(UUID workerUuid) {
        return workerUuid != null && workerUuid.equals(uuid);
    }

    public boolean matches(UUID workerUuid, String workerDimension) {
        return matches(workerUuid)
                && (workerDimension == null || workerDimension.equals(dimension));
    }

    public boolean clearIfMatches(UUID workerUuid) {
        if (!matches(workerUuid)) {
            return false;
        }
        clear();
        return true;
    }

    public boolean clearIfMatches(UUID workerUuid, String workerDimension) {
        if (!matches(workerUuid, workerDimension)) {
            return false;
        }
        clear();
        return true;
    }

    public void set(UUID uuid, String dimension, BlockPos position) {
        this.uuid = uuid;
        this.dimension = dimension == null ? "" : dimension;
        this.position = position == null ? BlockPos.ZERO : position.immutable();
        this.schema = CURRENT_SCHEMA;
        this.pickupTransaction = null;
        this.pickupState = PickupState.LIVE;
        this.pickupSnapshot = null;
    }

    /** Refreshes location without erasing an in-flight pickup transaction. */
    public boolean updateLocation(UUID workerUuid, String dimension, BlockPos position) {
        if (!matches(workerUuid)) return false;
        this.dimension = dimension == null ? "" : dimension;
        this.position = position == null ? BlockPos.ZERO : position.immutable();
        this.schema = CURRENT_SCHEMA;
        return true;
    }

    public void clear() {
        uuid = null;
        dimension = "";
        position = BlockPos.ZERO;
        schema = CURRENT_SCHEMA;
        pickupTransaction = null;
        pickupState = PickupState.LIVE;
        pickupSnapshot = null;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        if (uuid != null) {
            tag.putUUID("uuid", uuid);
        }
        tag.putInt("schema", schema);
        tag.putString("dimension", dimension);
        tag.putLong("position", position.asLong());
        if (pickupTransaction != null) tag.putUUID("pickup_transaction", pickupTransaction);
        tag.putString("pickup_state", pickupState.serializedName());
        if (pickupSnapshot != null) {
            PackedWorkerData.CODEC.encodeStart(NbtOps.INSTANCE, pickupSnapshot)
                    .result()
                    .ifPresent(encoded -> tag.put("pickup_snapshot", encoded));
        }
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        uuid = tag.hasUUID("uuid") ? tag.getUUID("uuid") : null;
        schema = tag.contains("schema") ? tag.getInt("schema") : 1;
        if (!isSupportedSchema()) {
            clear();
            return;
        }
        dimension = tag.getString("dimension");
        position = BlockPos.of(tag.getLong("position"));
        pickupTransaction = tag.hasUUID("pickup_transaction")
                ? tag.getUUID("pickup_transaction") : null;
        pickupState = PickupState.fromSerialized(tag.getString("pickup_state"));
        pickupSnapshot = tag.contains("pickup_snapshot", Tag.TAG_COMPOUND)
                ? PackedWorkerData.CODEC.parse(NbtOps.INSTANCE, tag.get("pickup_snapshot"))
                        .result().orElse(null)
                : null;
        if ((pickupState != PickupState.PENDING && pickupState != PickupState.COMMITTED)
                || pickupTransaction == null) {
            pickupTransaction = null;
            pickupState = PickupState.LIVE;
            pickupSnapshot = null;
        } else if (pickupSnapshot != null
                && (!pickupSnapshot.isPlaceable()
                || !pickupSnapshot.workerUuid().equals(uuid)
                || !pickupSnapshot.transactionUuid().equals(pickupTransaction))) {
            pickupSnapshot = null;
        }
    }

    private boolean matchesPickup(UUID workerUuid, UUID transactionUuid, PickupState expected) {
        return matches(workerUuid)
                && transactionUuid != null
                && transactionUuid.equals(pickupTransaction)
                && pickupState == expected;
    }
}
