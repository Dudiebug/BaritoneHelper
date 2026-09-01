package dev.dudie.baritonehelper.client;

import dev.dudie.baritonehelper.network.WorkerActionAcknowledgementS2C;
import dev.dudie.baritonehelper.network.WorkerDashboardActionC2S;
import dev.dudie.baritonehelper.network.WorkerDashboardStateS2C;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;

/** Compile-time access to the identity and ordering fields used by the dashboard. */
final class DashboardProtocolAccessors {
    private DashboardProtocolAccessors() {}

    static SnapshotView snapshotView(WorkerDashboardStateS2C.Snapshot snapshot) {
        return new SnapshotView(
                snapshot.workerUuid(),
                snapshot.dimension(),
                snapshot.stateSequence(),
                Optional.ofNullable(snapshot.searchMode()),
                Optional.empty());
    }

    static UUID requestId(WorkerDashboardActionC2S payload) {
        return payload.requestId();
    }

    static net.minecraft.world.entity.Entity clientEntity(SnapshotView view) {
        if (Minecraft.getInstance().level == null) return null;
        if (view.workerUuid() == null) return null;
        for (var entity : Minecraft.getInstance().level.entitiesForRendering()) {
            if (view.workerUuid().equals(entity.getUUID())) return entity;
        }
        return null;
    }

    static UUID requestId(WorkerActionAcknowledgementS2C payload) {
        return payload.requestId();
    }

    static String acknowledgementText(WorkerActionAcknowledgementS2C acknowledgement) {
        return acknowledgement.translationKey();
    }

    static boolean isPickupAcknowledgement(WorkerActionAcknowledgementS2C acknowledgement) {
        String value = (acknowledgement.errorCode() + " "
                + acknowledgement.translationKey() + " "
                + acknowledgementText(acknowledgement)).toLowerCase(Locale.ROOT);
        return value.contains("pickup") || value.contains("collect")
                || value.contains("packed") || value.contains("dismiss");
    }

    record SnapshotView(
            UUID workerUuid,
            String dimension,
            long stateSequence,
            Optional<String> searchMode,
            Optional<String> pickupStatus) {}
}
