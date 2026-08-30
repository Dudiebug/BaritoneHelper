package dev.dudie.baritonehelper.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Opens the dashboard with an authoritative initial snapshot. */
public record OpenWorkerDashboardS2C(WorkerDashboardStateS2C.Snapshot snapshot)
        implements CustomPacketPayload {
    public static final Type<OpenWorkerDashboardS2C> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("baritonehelper", "open_worker_dashboard"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenWorkerDashboardS2C> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> WorkerDashboardStateS2C.writeSnapshot(buffer, payload.snapshot()),
                    buffer -> new OpenWorkerDashboardS2C(WorkerDashboardStateS2C.readSnapshot(buffer)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
