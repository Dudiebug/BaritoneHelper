package dev.dudie.baritonehelper.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Response for one dashboard request; errors are explicit and never silent. */
public record WorkerActionAcknowledgementS2C(
        UUID requestId,
        boolean success,
        String errorCode,
        String translationKey,
        int configurationRevision) implements CustomPacketPayload {
    public static final Type<WorkerActionAcknowledgementS2C> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("baritonehelper", "worker_action_acknowledgement"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorkerActionAcknowledgementS2C> STREAM_CODEC =
            StreamCodec.of(WorkerActionAcknowledgementS2C::write, WorkerActionAcknowledgementS2C::read);

    public WorkerActionAcknowledgementS2C {
        if (requestId == null) requestId = new UUID(0L, 0L);
        if (errorCode == null) errorCode = "unknown";
        if (translationKey == null) translationKey = "message.baritonehelper.command_failed";
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buffer, WorkerActionAcknowledgementS2C payload) {
        buffer.writeLong(payload.requestId().getMostSignificantBits());
        buffer.writeLong(payload.requestId().getLeastSignificantBits());
        buffer.writeBoolean(payload.success());
        writeString(buffer, payload.errorCode(), 128);
        writeString(buffer, payload.translationKey(), 256);
        buffer.writeVarInt(payload.configurationRevision());
    }

    private static WorkerActionAcknowledgementS2C read(RegistryFriendlyByteBuf buffer) {
        return new WorkerActionAcknowledgementS2C(
                new UUID(buffer.readLong(), buffer.readLong()),
                buffer.readBoolean(),
                buffer.readUtf(128),
                buffer.readUtf(256),
                buffer.readVarInt());
    }

    private static void writeString(RegistryFriendlyByteBuf buffer, String value, int max) {
        String safe = value == null ? "" : value;
        buffer.writeUtf(safe.length() > max ? safe.substring(0, max) : safe, max);
    }
}
