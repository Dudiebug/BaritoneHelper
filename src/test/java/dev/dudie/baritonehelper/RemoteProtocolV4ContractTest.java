package dev.dudie.baritonehelper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class RemoteProtocolV4ContractTest {
    private static String read(String relative) throws IOException {
        return Files.readString(Path.of(relative));
    }

    @Test
    void v4PayloadsUseStableWorkerIdentityAndDimension() throws IOException {
        String action = read("src/main/java/dev/dudie/baritonehelper/network/WorkerDashboardActionC2S.java");
        String state = read("src/main/java/dev/dudie/baritonehelper/network/WorkerDashboardStateS2C.java");
        String actionHeader = action.substring(action.indexOf("public record WorkerDashboardActionC2S"),
                action.indexOf("implements CustomPacketPayload"));
        String snapshotHeader = state.substring(state.indexOf("public record Snapshot"),
                state.indexOf(") {", state.indexOf("public record Snapshot")));

        assertTrue(action.contains("UUID workerUuid"));
        assertTrue(actionHeader.contains("String dimension"));
        assertFalse(actionHeader.contains("int workerEntityId"));
        assertTrue(snapshotHeader.contains("UUID workerUuid"));
        assertTrue(snapshotHeader.contains("String dimension"));
        assertFalse(snapshotHeader.contains("int workerEntityId"));
        assertFalse(action.contains("writeVarInt(payload.workerEntityId())"));
        assertFalse(state.contains("writeVarInt(snapshot.workerEntityId())"));
        assertTrue(read("src/main/java/dev/dudie/baritonehelper/network/WorkerNetwork.java")
                .contains("PROTOCOL = \"4\""));
    }

    @Test
    void snapshotsCarryAnEncodedMonotonicSequence() throws IOException {
        String state = read("src/main/java/dev/dudie/baritonehelper/network/WorkerDashboardStateS2C.java");
        String network = read("src/main/java/dev/dudie/baritonehelper/network/WorkerNetwork.java");

        assertTrue(state.contains("long stateSequence"));
        assertTrue(state.contains("writeVarLong(snapshot.stateSequence())"));
        assertTrue(state.contains("buffer.readVarLong()"));
        assertTrue(network.contains("stateSequence"));
    }

    @Test
    void requestsHaveBoundedPlayerWorkerFingerprintIdempotence() throws IOException {
        String network = read("src/main/java/dev/dudie/baritonehelper/network/WorkerNetwork.java");
        String receipts = read("src/main/java/dev/dudie/baritonehelper/network/RequestReceiptData.java");

        assertTrue(receipts.contains("MAX_RECEIPTS"));
        assertTrue(receipts.contains("LinkedHashMap"));
        assertTrue(receipts.contains("record RequestKey"));
        assertTrue(receipts.contains("UUID playerId"));
        assertTrue(receipts.contains("UUID workerUuid"));
        assertTrue(receipts.contains("UUID requestId"));
        assertTrue(receipts.contains("fingerprint"));
        assertTrue(receipts.contains("oldest.remove()"));
        assertTrue(receipts.contains("extends SavedData"));
        assertTrue(network.contains("request_conflict"));
        assertTrue(network.contains("RequestReceiptData.get(level).lookup"));
    }

    @Test
    void lookupSpansServerDimensionsButPhysicalActionsDoNot() throws IOException {
        String network = read("src/main/java/dev/dudie/baritonehelper/network/WorkerNetwork.java");

        assertTrue(network.contains("findOwnedWorker"));
        assertTrue(network.contains("ServerPlayer player, UUID workerUuid, String dimension"));
        assertTrue(network.contains("WorkerControllerItem.findOwnedWorker(player, workerUuid, dimension)"));
        assertTrue(network.contains("player.getData(BaritoneHelper.ACTIVE_WORKER)"));
        assertFalse(network.contains("level.getAllEntities()"));
        assertTrue(network.contains("player.level() != worker.level()"));
        assertFalse(network.contains("owner.level() == level"));
        assertTrue(network.contains("ARM_STORAGE_SELECTION"));
        assertTrue(network.contains("ARM_AREA_SELECTION"));
        assertTrue(network.contains("ARM_ZONE_SELECTION"));
        assertTrue(network.contains("inventory_wrong_dimension"));
        assertTrue(network.contains("physical_action_wrong_dimension"));
    }

    @Test
    void expectedRevisionIsCheckedAfterReplayLookupAndAckKeepsRequestId() throws IOException {
        String network = read("src/main/java/dev/dudie/baritonehelper/network/WorkerNetwork.java");
        String acknowledgement = read(
                "src/main/java/dev/dudie/baritonehelper/network/WorkerActionAcknowledgementS2C.java");

        assertTrue(acknowledgement.contains("UUID requestId"));
        assertTrue(acknowledgement.contains("payload.requestId()"));
        assertTrue(network.contains("payload.expectedRevision()"));
        assertTrue(network.contains(".record(player.getUUID(), payload, acknowledgement)"));
        assertTrue(network.indexOf("RequestReceiptData.get(level).lookup")
                < network.indexOf("worker.configurationRevision() != payload.expectedRevision()"));
    }
}
