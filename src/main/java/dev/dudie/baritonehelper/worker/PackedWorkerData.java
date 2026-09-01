package dev.dudie.baritonehelper.worker;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.dudie.baritonehelper.entity.WorkerEntity;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Immutable, versioned payload carried by a packed worker item.
 *
 * The payload deliberately uses the existing worker save boundary. That keeps
 * all item components and persistent worker configuration in one canonical
 * format while filtering runtime tickets and active work before placement.
 */
public final class PackedWorkerData {
    public static final int CURRENT_SCHEMA = 1;
    public static final int SCHEMA_VERSION = CURRENT_SCHEMA;
    private static final int MAX_PERSISTENT_DATA_BYTES = 2_000_000;

    private static final Codec<CompoundTag> PERSISTENT_DATA_CODEC = CompoundTag.CODEC.validate(
            tag -> tag.sizeInBytes() <= MAX_PERSISTENT_DATA_BYTES
                    ? DataResult.success(tag)
                    : DataResult.error(() -> "packed worker payload is too large"));

    public static final Codec<PackedWorkerData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.validate(PackedWorkerData::validateSchema)
                    .fieldOf("schema")
                    .forGetter(PackedWorkerData::schema),
            UUIDUtil.CODEC.fieldOf("worker_uuid").forGetter(PackedWorkerData::workerUuid),
            UUIDUtil.CODEC.fieldOf("owner_uuid").forGetter(PackedWorkerData::ownerUuid),
            UUIDUtil.CODEC.fieldOf("transaction_uuid").forGetter(PackedWorkerData::transactionUuid),
            PERSISTENT_DATA_CODEC.fieldOf("persistent_data").forGetter(PackedWorkerData::persistentData),
            PickupState.CODEC.optionalFieldOf("pickup_state", PickupState.LIVE)
                    .forGetter(PackedWorkerData::pickupState))
            .apply(instance, PackedWorkerData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PackedWorkerData> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    PackedWorkerData::schema,
                    UUIDUtil.STREAM_CODEC,
                    PackedWorkerData::workerUuid,
                    UUIDUtil.STREAM_CODEC,
                    PackedWorkerData::ownerUuid,
                    UUIDUtil.STREAM_CODEC,
                    PackedWorkerData::transactionUuid,
                    ByteBufCodecs.COMPOUND_TAG,
                    PackedWorkerData::persistentData,
                    PickupState.STREAM_CODEC,
                    PackedWorkerData::pickupState,
                    PackedWorkerData::new);

    private final int schema;
    private final UUID workerUuid;
    private final UUID ownerUuid;
    private final UUID transactionUuid;
    private final CompoundTag persistentData;
    private final PickupState pickupState;

    public PackedWorkerData(UUID workerUuid, UUID ownerUuid, CompoundTag persistentData) {
        this(CURRENT_SCHEMA, workerUuid, ownerUuid, UUID.randomUUID(), persistentData, PickupState.LIVE);
    }

    public PackedWorkerData(
            int schema,
            UUID workerUuid,
            UUID ownerUuid,
            CompoundTag persistentData) {
        this(schema, workerUuid, ownerUuid, UUID.randomUUID(), persistentData, PickupState.LIVE);
    }

    public PackedWorkerData(
            int schema,
            UUID workerUuid,
            UUID ownerUuid,
            UUID transactionUuid,
            CompoundTag persistentData,
            PickupState pickupState) {
        this.schema = schema;
        this.workerUuid = Objects.requireNonNull(workerUuid, "workerUuid");
        this.ownerUuid = Objects.requireNonNull(ownerUuid, "ownerUuid");
        this.transactionUuid = Objects.requireNonNull(transactionUuid, "transactionUuid");
        this.persistentData = normalize(persistentData, ownerUuid);
        this.pickupState = pickupState == null ? PickupState.LIVE : pickupState;
    }

    public static PackedWorkerData capture(WorkerEntity worker) {
        return capture(worker, UUID.randomUUID(), PickupState.LIVE);
    }

    public static PackedWorkerData capture(
            WorkerEntity worker, UUID transactionUuid, PickupState pickupState) {
        Objects.requireNonNull(worker, "worker");
        UUID ownerUuid = worker.getOwnerUUID();
        if (ownerUuid == null) {
            throw new IllegalArgumentException("cannot pack an unowned worker");
        }

        CompoundTag data = new CompoundTag();
        worker.addAdditionalSaveData(data);
        return new PackedWorkerData(
                CURRENT_SCHEMA,
                worker.getUUID(),
                ownerUuid,
                Objects.requireNonNull(transactionUuid, "transactionUuid"),
                data,
                Objects.requireNonNull(pickupState, "pickupState"));
    }

    public static PackedWorkerData from(WorkerEntity worker) {
        return capture(worker);
    }

    public int schema() {
        return schema;
    }

    public int schemaVersion() {
        return schema;
    }

    public int version() {
        return schema;
    }

    public UUID workerUuid() {
        return workerUuid;
    }

    public UUID workerId() {
        return workerUuid;
    }

    public UUID ownerUuid() {
        return ownerUuid;
    }

    public UUID ownerId() {
        return ownerUuid;
    }

    public UUID transactionUuid() {
        return transactionUuid;
    }

    public CompoundTag persistentData() {
        return persistentData.copy();
    }

    public CompoundTag entityData() {
        return persistentData();
    }

    public PickupState pickupState() {
        return pickupState;
    }

    public PickupState state() {
        return pickupState;
    }

    public boolean isSupported() {
        return schema == CURRENT_SCHEMA;
    }

    public boolean isCurrentSchema() {
        return isSupported();
    }

    public boolean isPlaceable() {
        return isSupported() && pickupState == PickupState.COMMITTED;
    }

    public PackedWorkerData withPickupState(PickupState next) {
        PickupState requested = Objects.requireNonNull(next, "next");
        if (!pickupState.canTransitionTo(requested)) {
            throw new IllegalStateException(
                    "invalid pickup transition: " + pickupState + " -> " + requested);
        }
        return new PackedWorkerData(
                schema, workerUuid, ownerUuid, transactionUuid, persistentData, requested);
    }

    public boolean restoreInto(WorkerEntity worker) {
        if (worker == null || !isPlaceable() || !workerUuid.equals(worker.getUUID())) {
            return false;
        }
        try {
            worker.readAdditionalSaveData(persistentData());
        } catch (RuntimeException ignored) {
            return false;
        }
        return ownerUuid.equals(worker.getOwnerUUID()) && !worker.job().activelyWorks();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PackedWorkerData that)) return false;
        return schema == that.schema
                && workerUuid.equals(that.workerUuid)
                && ownerUuid.equals(that.ownerUuid)
                && transactionUuid.equals(that.transactionUuid)
                && pickupState == that.pickupState
                && persistentData.equals(that.persistentData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schema, workerUuid, ownerUuid, transactionUuid, persistentData, pickupState);
    }

    @Override
    public String toString() {
        return "PackedWorkerData["
                + "schema=" + schema
                + ", workerUuid=" + workerUuid
                + ", ownerUuid=" + ownerUuid
                + ", transactionUuid=" + transactionUuid
                + ", pickupState=" + pickupState
                + "]";
    }

    private static CompoundTag normalize(CompoundTag original, UUID ownerUuid) {
        CompoundTag data = original == null ? new CompoundTag() : original.copy();
        data.remove("WorkerTicketChunks");
        data.remove("WorkerSimulationTicketChunks");
        data.remove("SearchTicketChunks");
        data.remove("NoAI");
        data.remove("BlockReason");
        data.putUUID("Owner", ownerUuid);
        data.putInt("BaritoneHelperSchema", 3);
        data.putString("WorkerJob", WorkerJob.READY.name());
        data.putString("RuntimeState", WorkerRuntimeState.READY.name());
        return data;
    }

    private static DataResult<Integer> validateSchema(int value) {
        return value == CURRENT_SCHEMA
                ? DataResult.success(value)
                : DataResult.error(() -> "unsupported packed worker schema " + value);
    }
}
