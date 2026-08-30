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
        int workerEntityId,
        int expectedRevision,
        Action action,
        String blockId,
        int amount,
        boolean unlimited,
        BlockPos workAreaCenter,
        int horizontalRadius,
        int verticalRadius) implements CustomPacketPayload {
    public static final Type<WorkerDashboardActionC2S> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("baritonehelper", "worker_dashboard_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorkerDashboardActionC2S> STREAM_CODEC =
            StreamCodec.of(WorkerDashboardActionC2S::write, WorkerDashboardActionC2S::read);

    public WorkerDashboardActionC2S {
        if (requestId == null) requestId = UUID.randomUUID();
        if (action == null) action = Action.REQUEST_SNAPSHOT;
    }

    public WorkerDashboardActionC2S(int workerEntityId, int expectedRevision, Action action) {
        this(UUID.randomUUID(), workerEntityId, expectedRevision, action, "", 64, true, BlockPos.ZERO, 64, 32);
    }

    public WorkerDashboardActionC2S(
            int workerEntityId, int expectedRevision, String blockId) {
        this(UUID.randomUUID(), workerEntityId, expectedRevision, Action.SET_TARGET, blockId, 64, true, BlockPos.ZERO, 64, 32);
    }

    public WorkerDashboardActionC2S(
            int workerEntityId, int expectedRevision, int amount, boolean unlimited) {
        this(UUID.randomUUID(), workerEntityId, expectedRevision, Action.SET_AMOUNT, "", amount, unlimited,
                BlockPos.ZERO, 64, 32);
    }

    public WorkerDashboardActionC2S(
            int workerEntityId, int expectedRevision, String pathingKey, boolean enabled) {
        this(UUID.randomUUID(), workerEntityId, expectedRevision, Action.TOGGLE_PATHING,
                pathingKey, 64, enabled, BlockPos.ZERO, 64, 32);
    }

    public WorkerDashboardActionC2S(
            int workerEntityId,
            int expectedRevision,
            BlockPos center,
            int horizontalRadius,
            int verticalRadius) {
        this(UUID.randomUUID(), workerEntityId, expectedRevision, Action.SET_WORK_AREA, "", 64, true,
                center, horizontalRadius, verticalRadius);
    }

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
        this(UUID.randomUUID(), workerEntityId, expectedRevision, action, value, mode, enabled,
                center, horizontalRadius, verticalRadius);
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
        TOGGLE_EXCLUSION;

        static Action fromOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : null;
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buffer, WorkerDashboardActionC2S payload) {
        buffer.writeLong(payload.requestId().getMostSignificantBits());
        buffer.writeLong(payload.requestId().getLeastSignificantBits());
        buffer.writeVarInt(payload.workerEntityId());
        buffer.writeVarInt(payload.expectedRevision());
        buffer.writeVarInt(payload.action() == null ? -1 : payload.action().ordinal());
        String blockId = payload.blockId() == null ? "" : payload.blockId();
        buffer.writeUtf(blockId.length() > 256 ? blockId.substring(0, 256) : blockId, 256);
        buffer.writeVarInt(payload.amount());
        buffer.writeBoolean(payload.unlimited());
        buffer.writeBlockPos(payload.workAreaCenter() == null ? BlockPos.ZERO : payload.workAreaCenter());
        buffer.writeVarInt(payload.horizontalRadius());
        buffer.writeVarInt(payload.verticalRadius());
    }

    private static WorkerDashboardActionC2S read(RegistryFriendlyByteBuf buffer) {
        return new WorkerDashboardActionC2S(
                new UUID(buffer.readLong(), buffer.readLong()),
                buffer.readVarInt(),
                buffer.readVarInt(),
                Action.fromOrdinal(buffer.readVarInt()),
                buffer.readUtf(256),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readBlockPos(),
                buffer.readVarInt(),
                buffer.readVarInt());
    }
}
