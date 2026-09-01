package dev.dudie.baritonehelper.network;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Bounded, restart-safe replay receipts for dashboard mutations. */
final class RequestReceiptData extends SavedData {
    static final int MAX_RECEIPTS = 256;
    private static final int CURRENT_SCHEMA = 1;
    private static final String DATA_NAME = "baritonehelper_request_receipts";
    private static final Factory<RequestReceiptData> FACTORY = new Factory<>(
            RequestReceiptData::new, RequestReceiptData::load);

    private final LinkedHashMap<RequestKey, WorkerActionAcknowledgementS2C> receipts =
            new LinkedHashMap<>();

    static RequestReceiptData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    Lookup lookup(UUID playerId, WorkerDashboardActionC2S payload) {
        RequestKey exact = RequestKey.from(playerId, payload);
        WorkerActionAcknowledgementS2C acknowledgement = receipts.get(exact);
        if (acknowledgement != null) return new Lookup(acknowledgement, false);
        for (RequestKey key : receipts.keySet()) {
            if (key.sameRequest(exact)) return new Lookup(null, true);
        }
        return null;
    }

    void record(
            UUID playerId,
            WorkerDashboardActionC2S payload,
            WorkerActionAcknowledgementS2C acknowledgement) {
        if (playerId == null || payload == null || acknowledgement == null) return;
        RequestKey key = RequestKey.from(playerId, payload);
        if (!receipts.containsKey(key) && receipts.size() >= MAX_RECEIPTS) {
            Iterator<RequestKey> oldest = receipts.keySet().iterator();
            if (oldest.hasNext()) {
                oldest.next();
                oldest.remove();
            }
        }
        receipts.put(key, acknowledgement);
        setDirty();
    }

    int sizeForTesting() {
        return receipts.size();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Schema", CURRENT_SCHEMA);
        ListTag entries = new ListTag();
        receipts.forEach((key, acknowledgement) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", key.playerId());
            entry.putUUID("Worker", key.workerUuid());
            entry.putUUID("Request", key.requestId());
            WorkerDashboardActionC2S.Fingerprint fingerprint = key.fingerprint();
            entry.putString("Dimension", fingerprint.dimension());
            entry.putInt("ExpectedRevision", fingerprint.expectedRevision());
            entry.putString("Action", fingerprint.action().name());
            entry.putString("BlockId", fingerprint.blockId());
            entry.putInt("Amount", fingerprint.amount());
            entry.putBoolean("Unlimited", fingerprint.unlimited());
            entry.putLong("WorkAreaCenter", fingerprint.workAreaCenter().asLong());
            entry.putInt("HorizontalRadius", fingerprint.horizontalRadius());
            entry.putInt("VerticalRadius", fingerprint.verticalRadius());
            entry.putBoolean("Success", acknowledgement.success());
            entry.putString("ErrorCode", acknowledgement.errorCode());
            entry.putString("TranslationKey", acknowledgement.translationKey());
            entry.putInt("ConfigurationRevision", acknowledgement.configurationRevision());
            entries.add(entry);
        });
        tag.put("Receipts", entries);
        return tag;
    }

    static RequestReceiptData load(CompoundTag tag, HolderLookup.Provider registries) {
        RequestReceiptData data = new RequestReceiptData();
        if (tag.getInt("Schema") != CURRENT_SCHEMA) return data;
        ListTag entries = tag.getList("Receipts", Tag.TAG_COMPOUND);
        int first = Math.max(0, entries.size() - MAX_RECEIPTS);
        for (int index = first; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            try {
                UUID playerId = entry.getUUID("Player");
                UUID workerUuid = entry.getUUID("Worker");
                UUID requestId = entry.getUUID("Request");
                WorkerDashboardActionC2S.Action action = WorkerDashboardActionC2S.Action.valueOf(
                        entry.getString("Action"));
                WorkerDashboardActionC2S.Fingerprint fingerprint =
                        new WorkerDashboardActionC2S.Fingerprint(
                                workerUuid,
                                bounded(entry.getString("Dimension"), 256),
                                entry.getInt("ExpectedRevision"),
                                action,
                                bounded(entry.getString("BlockId"), 256),
                                entry.getInt("Amount"),
                                entry.getBoolean("Unlimited"),
                                BlockPos.of(entry.getLong("WorkAreaCenter")),
                                entry.getInt("HorizontalRadius"),
                                entry.getInt("VerticalRadius"));
                WorkerActionAcknowledgementS2C acknowledgement =
                        new WorkerActionAcknowledgementS2C(
                                requestId,
                                entry.getBoolean("Success"),
                                bounded(entry.getString("ErrorCode"), 128),
                                bounded(entry.getString("TranslationKey"), 256),
                                entry.getInt("ConfigurationRevision"));
                data.receipts.put(
                        new RequestKey(playerId, workerUuid, requestId, fingerprint),
                        acknowledgement);
            } catch (IllegalArgumentException ignored) {
                // A malformed receipt is isolated; valid later receipts still load.
            }
        }
        return data;
    }

    private static String bounded(String value, int maximum) {
        if (value == null) return "";
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    record Lookup(WorkerActionAcknowledgementS2C acknowledgement, boolean conflicting) {
    }

    private record RequestKey(
            UUID playerId,
            UUID workerUuid,
            UUID requestId,
            WorkerDashboardActionC2S.Fingerprint fingerprint) {
        static RequestKey from(UUID playerId, WorkerDashboardActionC2S payload) {
            return new RequestKey(
                    playerId, payload.workerUuid(), payload.requestId(), payload.fingerprint());
        }

        boolean sameRequest(RequestKey other) {
            return playerId.equals(other.playerId)
                    && workerUuid.equals(other.workerUuid)
                    && requestId.equals(other.requestId);
        }
    }
}
