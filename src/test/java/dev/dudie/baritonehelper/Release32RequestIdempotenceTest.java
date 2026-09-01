package dev.dudie.baritonehelper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Java-only source gate for the protocol replay boundary.
 *
 * Wire codec execution belongs to the dedicated runtime/GameTest classpath;
 * these checks keep the ordinary JUnit source set free of Minecraft classes.
 */
final class Release32RequestIdempotenceTest {
    @Test
    void requestCorrelationAndFingerprintAreExplicitlyRepresented() throws IOException {
        String action = read(
                "src/main/java/dev/dudie/baritonehelper/network/WorkerDashboardActionC2S.java");
        String record = action.substring(
                action.indexOf("public record WorkerDashboardActionC2S"),
                action.indexOf("implements CustomPacketPayload"));
        String fingerprint = action.substring(
                action.indexOf("public Fingerprint fingerprint()"),
                action.indexOf("public record Fingerprint"));

        assertTrue(record.contains("UUID requestId"));
        assertTrue(record.contains("UUID workerUuid"));
        assertTrue(record.contains("String dimension"));
        assertTrue(record.contains("int expectedRevision"));
        assertTrue(fingerprint.contains("workerUuid"));
        assertTrue(fingerprint.contains("dimension"));
        assertTrue(fingerprint.contains("expectedRevision"));
        assertTrue(fingerprint.contains("action"));
        assertTrue(fingerprint.contains("blockId"));
        assertTrue(fingerprint.contains("amount"));
        assertTrue(fingerprint.contains("unlimited"));
        assertTrue(fingerprint.contains("workAreaCenter"));
        assertTrue(fingerprint.contains("horizontalRadius"));
        assertTrue(fingerprint.contains("verticalRadius"));
    }

    @Test
    void wireOrderPreservesRequestAndWorkerIdentityBeforeMutationFields() throws IOException {
        String action = read(
                "src/main/java/dev/dudie/baritonehelper/network/WorkerDashboardActionC2S.java");
        int requestMost = action.indexOf("payload.requestId().getMostSignificantBits()");
        int workerMost = action.indexOf("payload.workerUuid().getMostSignificantBits()");
        int dimension = action.indexOf("writeString(buffer, payload.dimension(), 256)");
        int revision = action.indexOf("buffer.writeVarInt(payload.expectedRevision())");

        assertTrue(requestMost >= 0);
        assertTrue(workerMost > requestMost);
        assertTrue(dimension > workerMost);
        assertTrue(revision > dimension);
        assertTrue(action.contains("new UUID(buffer.readLong(), buffer.readLong())"));
        assertTrue(action.indexOf("PICKUP") < action.indexOf("SET_SEARCH_MODE"),
                "protocol-4 additions must append after the existing PICKUP action");
        assertTrue(action.contains("INVALID"));
    }

    @Test
    void serverReplayIsBoundedPersistentAndConflictingRequestsAreRejected() throws IOException {
        String network = read(
                "src/main/java/dev/dudie/baritonehelper/network/WorkerNetwork.java");
        String receipts = read(
                "src/main/java/dev/dudie/baritonehelper/network/RequestReceiptData.java");
        String acknowledgement = read(
                "src/main/java/dev/dudie/baritonehelper/network/WorkerActionAcknowledgementS2C.java");

        assertTrue(receipts.matches("(?s).*MAX_RECEIPTS\\s*=\\s*256.*"));
        assertTrue(receipts.contains("extends SavedData"));
        assertTrue(receipts.contains("LinkedHashMap"));
        assertTrue(receipts.contains("oldest.remove()"));
        assertTrue(receipts.contains("record RequestKey"));
        assertTrue(receipts.contains("CURRENT_SCHEMA"));
        assertTrue(receipts.contains("computeIfAbsent(FACTORY, DATA_NAME)"));
        assertTrue(network.contains("REQUEST_CONFLICT"));
        assertTrue(network.contains("RequestReceiptData.get(level).lookup"));
        assertTrue(network.contains(".record(player.getUUID(), payload, acknowledgement)"));
        assertTrue(network.indexOf("RequestReceiptData.get(level).lookup")
                < network.indexOf("worker.configurationRevision() != payload.expectedRevision()"));
        assertTrue(network.contains("cached snapshot replay"));
        assertTrue(acknowledgement.contains("UUID requestId"));
        assertTrue(acknowledgement.contains("payload.requestId()"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }
}
