package dev.dudie.baritonehelper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Java-only source gate for the versioned packed-worker component boundary.
 *
 * NBT and registry round trips belong to the dedicated runtime/GameTest
 * classpath; this ordinary JUnit class verifies the executable seams without
 * importing Minecraft classes.
 */
final class Release32PackedComponentTest {
    @Test
    void packedPayloadIsVersionedCodecBackedAndDefensive() throws IOException {
        String packed = read(
                "src/main/java/dev/dudie/baritonehelper/worker/PackedWorkerData.java");
        assertTrue(packed.contains("public static final int CURRENT_SCHEMA"));
        assertTrue(packed.contains("transaction_uuid"));
        assertTrue(packed.contains("transactionUuid()"));
        assertTrue(packed.contains("public static final Codec<PackedWorkerData> CODEC"));
        assertTrue(packed.contains("public static final StreamCodec"));
        assertTrue(packed.contains("Objects.requireNonNull(workerUuid"));
        assertTrue(packed.contains("this.persistentData = normalize(persistentData, ownerUuid)"));
        assertTrue(packed.contains("return persistentData.copy()"));
        assertTrue(packed.contains("schema == CURRENT_SCHEMA"));
    }

    @Test
    void packingStripsRuntimeTicketsAndPreventsPendingPlacement() throws IOException {
        String packed = read(
                "src/main/java/dev/dudie/baritonehelper/worker/PackedWorkerData.java");
        assertTrue(packed.contains("data.remove(\"WorkerTicketChunks\")"));
        assertTrue(packed.contains("data.remove(\"WorkerSimulationTicketChunks\")"));
        assertTrue(packed.contains("data.remove(\"SearchTicketChunks\")"));
        assertTrue(packed.contains("data.putString(\"WorkerJob\", WorkerJob.READY.name())"));
        assertTrue(packed.contains("data.putString(\"RuntimeState\", WorkerRuntimeState.READY.name())"));
        assertTrue(packed.contains("pickupState == PickupState.COMMITTED"));
        assertTrue(packed.contains("UUID transactionUuid"));
        assertTrue(packed.contains("pickupState.canTransitionTo(requested)"));
    }

    @Test
    void componentRegistrationUsesPersistentAndNetworkCodecsAndPlacementCommitsLast()
            throws IOException {
        String component = read(
                "src/main/java/dev/dudie/baritonehelper/BaritoneHelperDataComponents.java");
        String item = read("src/main/java/dev/dudie/baritonehelper/item/WorkerItem.java");
        assertTrue(component.contains("PACKED_WORKER"));
        assertTrue(component.contains("persistent(PackedWorkerData.CODEC)"));
        assertTrue(component.contains("networkSynchronized(PackedWorkerData.STREAM_CODEC)"));
        assertTrue(item.contains("packed.isPlaceable()"));
        assertTrue(item.contains("packed.restoreInto(worker)"));
        assertTrue(item.indexOf("level.addFreshEntity(worker)")
                < item.indexOf("source.shrink(1)"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }
}
