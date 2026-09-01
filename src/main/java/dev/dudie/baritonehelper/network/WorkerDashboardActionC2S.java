package dev.dudie.baritonehelper.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Explicit client intent.  The server validates ownership, revision, and every value. */
public record WorkerDashboardActionC2S(
        UUID requestId,
        UUID workerUuid,
        String dimension,
        int expectedRevision,
        Action action,
        String blockId,
        int amount,
        boolean unlimited,
        BlockPos workAreaCenter,
        int horizontalRadius,
        int verticalRadius) implements CustomPacketPayload {
    private static final UUID NO_WORKER_UUID = new UUID(0L, 0L);
    public static final Type<WorkerDashboardActionC2S> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("baritonehelper", "worker_dashboard_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorkerDashboardActionC2S> STREAM_CODEC =
            StreamCodec.of(WorkerDashboardActionC2S::write, WorkerDashboardActionC2S::read);

    public WorkerDashboardActionC2S {
        if (requestId == null) requestId = UUID.randomUUID();
        if (workerUuid == null) workerUuid = NO_WORKER_UUID;
        dimension = bounded(dimension, 256);
        if (action == null) action = Action.INVALID;
        blockId = bounded(blockId, 256);
        workAreaCenter = workAreaCenter == null ? BlockPos.ZERO : workAreaCenter.immutable();
    }

    /** Compatibility bridge for the pre-v4 UI; the server never trusts this int. */
    @Deprecated
    public WorkerDashboardActionC2S(int workerEntityId, int expectedRevision, Action action) {
        this(UUID.randomUUID(), NO_WORKER_UUID, "", expectedRevision, action,
                "", 64, true, BlockPos.ZERO, 64, 32);
    }

    public WorkerDashboardActionC2S(
            UUID workerUuid, String dimension, int expectedRevision, Action action) {
        this(UUID.randomUUID(), workerUuid, dimension, expectedRevision, action,
                "", 64, true, BlockPos.ZERO, 64, 32);
    }

    /** Compatibility bridge for the pre-v4 UI; the server never trusts this int. */
    @Deprecated
    public WorkerDashboardActionC2S(
            int workerEntityId, int expectedRevision, String blockId) {
        this(UUID.randomUUID(), NO_WORKER_UUID, "", expectedRevision,
                Action.SET_TARGET, blockId, 64, true, BlockPos.ZERO, 64, 32);
    }

    public WorkerDashboardActionC2S(
            UUID workerUuid, String dimension, int expectedRevision, String blockId) {
        this(UUID.randomUUID(), workerUuid, dimension, expectedRevision,
                Action.SET_TARGET, blockId, 64, true, BlockPos.ZERO, 64, 32);
    }

    /** Compatibility bridge for the pre-v4 UI; the server never trusts this int. */
    @Deprecated
    public WorkerDashboardActionC2S(
            int workerEntityId, int expectedRevision, int amount, boolean unlimited) {
        this(UUID.randomUUID(), NO_WORKER_UUID, "", expectedRevision,
                Action.SET_AMOUNT, "", amount, unlimited, BlockPos.ZERO, 64, 32);
    }

    public WorkerDashboardActionC2S(
            UUID workerUuid, String dimension, int expectedRevision, int amount, boolean unlimited) {
        this(UUID.randomUUID(), workerUuid, dimension, expectedRevision,
                Action.SET_AMOUNT, "", amount, unlimited, BlockPos.ZERO, 64, 32);
    }

    /** Compatibility bridge for the pre-v4 UI; the server never trusts this int. */
    @Deprecated
    public WorkerDashboardActionC2S(
            int workerEntityId, int expectedRevision, String pathingKey, boolean enabled) {
        this(UUID.randomUUID(), NO_WORKER_UUID, "", expectedRevision,
                Action.TOGGLE_PATHING, pathingKey, 64, enabled, BlockPos.ZERO, 64, 32);
    }

    public WorkerDashboardActionC2S(
            UUID workerUuid, String dimension, int expectedRevision, String pathingKey, boolean enabled) {
        this(UUID.randomUUID(), workerUuid, dimension, expectedRevision,
                Action.TOGGLE_PATHING, pathingKey, 64, enabled, BlockPos.ZERO, 64, 32);
    }

    /** Compatibility bridge for the pre-v4 UI; the server never trusts this int. */
    @Deprecated
    public WorkerDashboardActionC2S(
            int workerEntityId,
            int expectedRevision,
            BlockPos center,
            int horizontalRadius,
            int verticalRadius) {
        this(UUID.randomUUID(), NO_WORKER_UUID, "", expectedRevision,
                Action.SET_WORK_AREA, "", 64, true, center, horizontalRadius, verticalRadius);
    }

    public WorkerDashboardActionC2S(
            UUID workerUuid,
            String dimension,
            int expectedRevision,
            BlockPos center,
            int horizontalRadius,
            int verticalRadius) {
        this(UUID.randomUUID(), workerUuid, dimension, expectedRevision,
                Action.SET_WORK_AREA, "", 64, true, center, horizontalRadius, verticalRadius);
    }

    /** Compatibility bridge for the pre-v4 UI; the server never trusts this int. */
    @Deprecated
    public WorkerDashboardActionC2S(
            int workerEntityId,
            int expectedRevision,
            Action action,
            String value,
            int mode,
            boolean enabled,
            BlockPos center,
            int horizontalRadius,
            int verticalRadius) {
        this(UUID.randomUUID(), NO_WORKER_UUID, "", expectedRevision,
                action, value, mode, enabled, center, horizontalRadius, verticalRadius);
    }

    public WorkerDashboardActionC2S(
            UUID workerUuid,
            String dimension,
            int expectedRevision,
            Action action,
            String value,
            int mode,
            boolean enabled,
            BlockPos center,
            int horizontalRadius,
            int verticalRadius) {
        this(UUID.randomUUID(), workerUuid, dimension, expectedRevision, action, value, mode, enabled,
                center, horizontalRadius, verticalRadius);
    }

    /** Fields that define the request apart from its correlation UUID. */
    public Fingerprint fingerprint() {
        return new Fingerprint(
                workerUuid, dimension, expectedRevision, action, blockId, amount, unlimited,
                workAreaCenter, horizontalRadius, verticalRadius);
    }

    public record Fingerprint(
            UUID workerUuid,
            String dimension,
            int expectedRevision,
            Action action,
            String blockId,
            int amount,
            boolean unlimited,
            BlockPos workAreaCenter,
            int horizontalRadius,
            int verticalRadius) {}

    private static String bounded(String value, int maxChars) {
        if (value == null) return "";
        return value.length() > maxChars ? value.substring(0, maxChars) : value;
    }

    public enum Action {
        START,
        STOP,
        CLEAR_TARGET,
        CLEAR_STORAGE,
        OPEN_INVENTORY,
        SET_TARGET,
        SET_AMOUNT,
        SET_WORK_AREA,
        CLEAR_WORK_AREA,
        RESET_PROGRESS,
        REQUEST_SNAPSHOT,
        TOGGLE_PATHING,
        ARM_STORAGE_SELECTION,
        ARM_AREA_SELECTION,
        ARM_ZONE_SELECTION,
        ADD_NO_WORK_ZONE,
        UPDATE_NO_WORK_ZONE,
        DELETE_NO_WORK_ZONE,
        TOGGLE_NO_WORK_ZONE,
        TOGGLE_EXCLUSION,
        PICKUP,
        SET_SEARCH_MODE,
        INVALID;

        static Action fromOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : INVALID;
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buffer, WorkerDashboardActionC2S payload) {
        buffer.writeLong(payload.requestId().getMostSignificantBits());
        buffer.writeLong(payload.requestId().getLeastSignificantBits());
        buffer.writeLong(payload.workerUuid().getMostSignificantBits());
        buffer.writeLong(payload.workerUuid().getLeastSignificantBits());
        writeString(buffer, payload.dimension(), 256);
        buffer.writeVarInt(payload.expectedRevision());
        buffer.writeVarInt(payload.action() == null ? -1 : payload.action().ordinal());
        writeString(buffer, payload.blockId(), 256);
        buffer.writeVarInt(payload.amount());
        buffer.writeBoolean(payload.unlimited());
        buffer.writeBlockPos(payload.workAreaCenter());
        buffer.writeVarInt(payload.horizontalRadius());
        buffer.writeVarInt(payload.verticalRadius());
    }

    private static WorkerDashboardActionC2S read(RegistryFriendlyByteBuf buffer) {
        return new WorkerDashboardActionC2S(
                new UUID(buffer.readLong(), buffer.readLong()),
                new UUID(buffer.readLong(), buffer.readLong()),
                buffer.readUtf(256),
                buffer.readVarInt(),
                Action.fromOrdinal(buffer.readVarInt()),
                buffer.readUtf(256),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readBlockPos(),
                buffer.readVarInt(),
                buffer.readVarInt());
    }

    private static void writeString(RegistryFriendlyByteBuf buffer, String value, int maxChars) {
        buffer.writeUtf(bounded(value, maxChars), maxChars);
    }
}
