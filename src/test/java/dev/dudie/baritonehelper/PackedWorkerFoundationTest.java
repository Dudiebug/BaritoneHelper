package dev.dudie.baritonehelper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PackedWorkerFoundationTest {
    private static final Path MAIN = Path.of("src/main/java/dev/dudie/baritonehelper");

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }

    @Test
    void activeWorkerDataHasConditionalIdentityAndSchemaGuards() throws IOException {
        String source = read("ActiveWorkerData.java");
        assertTrue(source.contains("CURRENT_SCHEMA"));
        assertTrue(source.contains("SCHEMA_VERSION = CURRENT_SCHEMA"));
        assertTrue(source.contains("boolean matches(UUID workerUuid)"));
        assertTrue(source.contains("boolean matches(UUID workerUuid, String workerDimension)"));
        assertTrue(source.contains("boolean clearIfMatches(UUID workerUuid)"));
        assertTrue(source.contains("if (!matches(workerUuid))"));
        assertTrue(source.contains("tag.putInt(\"schema\", schema)"));
        assertTrue(source.contains("tag.contains(\"schema\")"));
        assertTrue(source.contains("boolean beginPickup(UUID workerUuid, UUID transactionUuid)"));
        assertTrue(source.contains("boolean commitPickup(UUID workerUuid, UUID transactionUuid)"));
        assertTrue(source.contains("boolean rollbackPickup(UUID workerUuid, UUID transactionUuid)"));
        assertTrue(source.contains("boolean clearCommittedPickup(UUID workerUuid, UUID transactionUuid)"));
        assertTrue(source.contains("boolean restoreLivePickup(UUID workerUuid, UUID transactionUuid)"));
        assertTrue(source.contains("tag.putUUID(\"pickup_transaction\""));
        assertTrue(source.contains("attachPickupSnapshot"));
        assertTrue(source.contains("tag.put(\"pickup_snapshot\""));
        assertTrue(source.contains("PackedWorkerData.CODEC.parse"));
    }

    @Test
    void onePickupServiceOwnsFreezeDeliveryCommitRollbackAndRestartReconciliation() throws IOException {
        String service = read("worker/WorkerPickupService.java");
        String worker = read("entity/WorkerEntity.java");
        String network = read("network/WorkerNetwork.java");

        assertTrue(service.contains("Canonical, server-thread transaction"));
        assertTrue(service.contains("active.beginPickup"));
        assertTrue(service.contains("worker.freezeForPickup"));
        assertTrue(service.contains("WorkerItem.createPackedStack"));
        assertTrue(service.contains("active.commitPickup"));
        assertTrue(service.contains("active.clearCommittedPickup"));
        assertTrue(service.contains("worker.rollbackPickup"));
        assertTrue(service.contains("setTarget(owner.getUUID())"));
        assertTrue(service.contains("boolean reconcile"));
        assertTrue(worker.contains("WorkerPickupService.pickup"));
        assertTrue(network.contains("WorkerPickupService.pickup"));
    }

    @Test
    void packedDataIsVersionedImmutableAndUsesBothCodecs() throws IOException {
        String source = read("worker/PackedWorkerData.java");
        assertTrue(source.contains("public static final int CURRENT_SCHEMA"));
        assertTrue(source.contains("Codec<PackedWorkerData> CODEC"));
        assertTrue(source.contains("StreamCodec<RegistryFriendlyByteBuf, PackedWorkerData> STREAM_CODEC"));
        assertTrue(source.contains("UUIDUtil.CODEC"));
        assertTrue(source.contains("UUIDUtil.STREAM_CODEC"));
        assertTrue(source.contains("private final UUID workerUuid"));
        assertTrue(source.contains("private final UUID ownerUuid"));
        assertTrue(source.contains("private final UUID transactionUuid"));
        assertTrue(source.contains("private final CompoundTag persistentData"));
        assertTrue(source.contains("original.copy()"));
        assertTrue(source.contains("return persistentData.copy()"));
        assertTrue(source.contains("ByteBufCodecs.COMPOUND_TAG"));
        assertTrue(source.contains("worker.addAdditionalSaveData(data)"));
        assertTrue(source.contains("worker.readAdditionalSaveData(persistentData())"));
        assertTrue(source.contains("data.remove(\"WorkerTicketChunks\")"));
        assertTrue(source.contains("data.remove(\"WorkerSimulationTicketChunks\")"));
        assertTrue(source.contains("data.putString(\"RuntimeState\", WorkerRuntimeState.READY.name())"));
        assertTrue(source.contains("data.putUUID(\"Owner\", ownerUuid)"));
    }

    @Test
    void pickupStateDefinesLivePendingCommittedRollbackAndTerminalStates() throws IOException {
        String source = read("worker/PickupState.java");
        assertTrue(source.contains("LIVE(\"live\")"));
        assertTrue(source.contains("PENDING(\"pending\")"));
        assertTrue(source.contains("COMMITTED(\"committed\")"));
        assertTrue(source.contains("Codec<PickupState> CODEC"));
        assertTrue(source.contains("StreamCodec<ByteBuf, PickupState> STREAM_CODEC"));
        assertTrue(source.contains("case LIVE -> next == PENDING"));
        assertTrue(source.contains("next == LIVE || next == COMMITTED"));
        assertTrue(source.contains("this == COMMITTED"));
    }

    @Test
    void registrationAndPlacementKeepValidationBeforeMutation() throws IOException {
        String components = read("BaritoneHelperDataComponents.java");
        String bootstrap = read("BaritoneHelper.java");
        String item = read("item/WorkerItem.java");
        assertTrue(components.contains("registerComponentType"));
        assertTrue(components.contains("persistent(PackedWorkerData.CODEC)"));
        assertTrue(components.contains("networkSynchronized(PackedWorkerData.STREAM_CODEC)"));
        assertTrue(bootstrap.contains("DATA_COMPONENTS.register(modBus)"));
        assertTrue(item.contains("packed.isPlaceable()"));
        assertTrue(item.contains("PackedWorkerData.capture(worker, transactionUuid, PickupState.COMMITTED)"));
        assertTrue(item.contains("packed.ownerUuid().equals(player.getUUID())"));
        assertTrue(item.contains("uuidInUse(player, packed.workerUuid())"));
        assertTrue(item.contains("packed.restoreInto(worker)"));
        assertTrue(item.contains("level.addFreshEntity(worker)"));
        assertTrue(item.indexOf("source.shrink(1)") > item.indexOf("level.addFreshEntity(worker)"));
        assertFalse(item.contains("source.consume(1, player)"));
    }
}
