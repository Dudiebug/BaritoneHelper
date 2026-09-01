package dev.dudie.baritonehelper.network;

import dev.dudie.baritonehelper.entity.WorkerEntity;
import dev.dudie.baritonehelper.worker.NoWorkZone;
import dev.dudie.baritonehelper.worker.PathTelemetry;
import dev.dudie.baritonehelper.worker.SearchTelemetry;
import dev.dudie.baritonehelper.worker.WorkerPathingSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-authoritative dashboard snapshot.  It contains no client-derived world state. */
public record WorkerDashboardStateS2C(Snapshot snapshot) implements CustomPacketPayload {
    private static final Map<WorkerEntity, ConfigurationCache> CONFIGURATION_CACHE = new WeakHashMap<>();

    public static final Type<WorkerDashboardStateS2C> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("baritonehelper", "worker_dashboard_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorkerDashboardStateS2C> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> writeSnapshot(buffer, payload.snapshot()),
                    buffer -> new WorkerDashboardStateS2C(readSnapshot(buffer)));
    public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> SNAPSHOT_CODEC =
            StreamCodec.of(WorkerDashboardStateS2C::writeSnapshot, WorkerDashboardStateS2C::readSnapshot);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record ZoneSnapshot(
            String id,
            String name,
            String dimension,
            BlockPos center,
            int horizontalRadius,
            int verticalRadius,
            int mode,
            boolean enabled) {
        static ZoneSnapshot from(NoWorkZone zone) {
            return new ZoneSnapshot(
                    zone.id().toString(),
                    zone.name(),
                    zone.dimension(),
                    zone.center(),
                    zone.horizontalRadius(),
                    zone.verticalRadius(),
                    zone.mode().ordinal(),
                    zone.enabled());
        }
    }

    private record ConfigurationCache(
            int revision,
            boolean allowBreakingObstructions,
            boolean allowBlockPlacement,
            boolean allowBridging,
            boolean allowPillaring,
            boolean allowParkour,
            boolean allowWaterRoutes,
            boolean preferSaferRoutes,
            boolean avoidDestructiveRouting,
            List<String> exclusions,
            List<ZoneSnapshot> noWorkZones) {}

    private static ConfigurationCache configurationCache(WorkerEntity worker, int revision) {
        synchronized (CONFIGURATION_CACHE) {
            ConfigurationCache cached = CONFIGURATION_CACHE.get(worker);
            int cachedRevision = cached == null ? -1 : cached.revision();
            if (cachedRevision == revision) return cached;

            WorkerPathingSettings settings = worker.configuration().pathing();
            ConfigurationCache next = new ConfigurationCache(
                    revision,
                    settings.allowBreakingObstructions,
                    settings.allowBlockPlacement,
                    settings.allowBridging,
                    settings.allowPillaring,
                    settings.allowParkour,
                    settings.allowWaterRoutes,
                    settings.preferSaferRoutes,
                    settings.avoidDestructiveRouting,
                    worker.exclusions().stream()
                            .map(ResourceLocation::toString)
                            .sorted()
                            .limit(128)
                            .toList(),
                    worker.noWorkZones().stream()
                            .limit(128)
                            .map(ZoneSnapshot::from)
                            .toList());
            CONFIGURATION_CACHE.put(worker, next);
            return next;
        }
    }

    /** All values are immutable and bounded before they are sent over the wire. */
    public record Snapshot(
            UUID workerUuid,
            String dimension,
            long stateSequence,
            int configurationRevision,
            String targetBlockId,
            int job,
            int activity,
            int blockReason,
            int runtimeState,
            int requestedCount,
            boolean unlimitedCount,
            int completedCount,
            boolean hasCurrentTarget,
            BlockPos currentTarget,
            boolean hasCurrentWorkPosition,
            BlockPos currentWorkPosition,
            boolean hasStorage,
            String storageDimension,
            BlockPos storagePosition,
            String workAreaDimension,
            BlockPos workAreaCenter,
            int horizontalRadius,
            int verticalRadius,
            int usedSlots,
            int capacity,
            int itemCount,
             int ticketCount,
             int searchTicketCount,
             int simulationTicketCount,
             int totalTicketCount,
             int replanAttempts,
             int lastProgressAgeTicks,
             String searchPhase,
             String searchMode,
             long searchGeneration,
             int chunksExamined,
             int frontierIndex,
             int frontierSize,
             int chunksScanned,
             int dirtyChunks,
             int inFlightChunks,
             int indexedTargets,
             int searchQueueDepth,
             int positionsExamined,
            int matchingBlocks,
            int candidatesFound,
            int candidatesRejectedByPolicy,
            int candidatesRejectedAsUnreachable,
            int cachedCandidates,
            boolean waitingForSearchChunk,
            boolean pathRequested,
             String lastScannedChunk,
             String requestedSearchChunk,
             long searchElapsedNanos,
             long maxCaptureNanos,
             String pathingStatus,
             String lastNavigationDestination,
             int pathNode,
             int pathLength,
             double pathCost,
             int pathQueueDepth,
             long pathElapsedNanos,
            boolean allowBreakingObstructions,
            boolean allowBlockPlacement,
            boolean allowBridging,
            boolean allowPillaring,
            boolean allowParkour,
            boolean allowWaterRoutes,
            boolean preferSaferRoutes,
            boolean avoidDestructiveRouting,
            List<String> exclusions,
            List<ZoneSnapshot> noWorkZones,
            List<String> activityHistory,
            String resumeNote) {
        /** Local construction has no sequence until WorkerNetwork publishes it. */
        public static Snapshot from(WorkerEntity worker) {
            return from(worker, 0L);
        }

        static Snapshot from(WorkerEntity worker, long stateSequence) {
            ConfigurationCache configuration = configurationCache(worker, worker.configurationRevision());
            SearchTelemetry search = worker.searchTelemetry();
            PathTelemetry path = worker.pathTelemetry();
            return new Snapshot(
                    worker.getUUID(),
                    worker.level().dimension().location().toString(),
                    stateSequence,
                    configuration.revision(),
                    worker.targetBlockId().map(ResourceLocation::toString).orElse(""),
                    worker.job().ordinal(),
                    worker.activity().ordinal(),
                    worker.blockReason().ordinal(),
                    worker.runtimeState().ordinal(),
                    worker.requestedBlockCount(),
                    worker.unlimitedCount(),
                    worker.completedBlockCount(),
                    worker.currentTarget().isPresent(),
                    worker.currentTarget().orElse(BlockPos.ZERO),
                    worker.currentWorkPosition().isPresent(),
                    worker.currentWorkPosition().orElse(BlockPos.ZERO),
                    worker.storagePosition().isPresent(),
                    worker.storageDimension(),
                    worker.storagePosition().orElse(BlockPos.ZERO),
                    worker.workAreaDimension(),
                    worker.workAreaCenter(),
                    worker.workAreaHorizontalRadius(),
                    worker.workAreaVerticalRadius(),
                    worker.inventoryUsedSlots(),
                    worker.getContainerSize(),
                    worker.inventoryItemCount(),
                    path.viewTickets(),
                    path.searchTickets(),
                    path.simulationTickets(),
                    worker.totalTicketCount(),
                    worker.replanAttempts(),
                    worker.lastProgressAgeTicks(),
                    search.phase(),
                    search.mode().serializedName(),
                    search.generation(),
                    search.chunksExamined(),
                    search.frontierIndex(),
                    search.frontierSize(),
                    search.chunksScanned(),
                    search.dirtyChunks(),
                    search.inFlightChunks(),
                    search.indexedTargets(),
                    search.queueDepth(),
                    search.positionsExamined(),
                    search.matchingBlocks(),
                    search.candidatesFound(),
                    search.candidatesRejectedByPolicy(),
                    search.candidatesRejectedAsUnreachable(),
                    search.cachedCandidates(),
                    search.waitingForSearchChunk(),
                    worker.pathRequested(),
                    search.lastScannedChunk(),
                    search.requestedSearchChunk(),
                    search.elapsedNanos(),
                    search.maxCaptureNanos(),
                    path.status().name(),
                    path.destination(),
                    path.pathNode(),
                    path.pathLength(),
                    path.remainingCost(),
                    path.queueDepth(),
                    path.elapsedNanos(),
                    configuration.allowBreakingObstructions(),
                    configuration.allowBlockPlacement(),
                    configuration.allowBridging(),
                    configuration.allowPillaring(),
                    configuration.allowParkour(),
                    configuration.allowWaterRoutes(),
                    configuration.preferSaferRoutes(),
                    configuration.avoidDestructiveRouting(),
                    configuration.exclusions(),
                    configuration.noWorkZones(),
                    worker.activityHistory(),
                    worker.resumeNote());
        }

        /** Compatibility accessor for the pre-v4 UI; it is never sent on the wire. */
        @Deprecated
        public int workerEntityId() {
            return -1;
        }

        Snapshot withStateSequence(long sequence) {
            return new Snapshot(
                    workerUuid, dimension, sequence, configurationRevision, targetBlockId, job, activity,
                    blockReason, runtimeState, requestedCount, unlimitedCount, completedCount,
                    hasCurrentTarget, currentTarget, hasCurrentWorkPosition, currentWorkPosition,
                    hasStorage, storageDimension, storagePosition, workAreaDimension, workAreaCenter,
                    horizontalRadius, verticalRadius, usedSlots, capacity, itemCount, ticketCount,
                    searchTicketCount, simulationTicketCount, totalTicketCount, replanAttempts, lastProgressAgeTicks,
                    searchPhase, searchMode, searchGeneration,
                    chunksExamined, frontierIndex, frontierSize, chunksScanned,
                    dirtyChunks, inFlightChunks, indexedTargets, searchQueueDepth, positionsExamined,
                    matchingBlocks, candidatesFound, candidatesRejectedByPolicy,
                    candidatesRejectedAsUnreachable, cachedCandidates, waitingForSearchChunk,
                    pathRequested, lastScannedChunk, requestedSearchChunk, searchElapsedNanos, maxCaptureNanos,
                    pathingStatus, lastNavigationDestination, pathNode, pathLength, pathCost,
                    pathQueueDepth, pathElapsedNanos,
                    allowBreakingObstructions, allowBlockPlacement, allowBridging, allowPillaring,
                    allowParkour, allowWaterRoutes, preferSaferRoutes, avoidDestructiveRouting,
                    exclusions, noWorkZones, activityHistory, resumeNote);
        }
    }

    static void writeSnapshot(RegistryFriendlyByteBuf buffer, Snapshot snapshot) {
        buffer.writeLong(snapshot.workerUuid().getMostSignificantBits());
        buffer.writeLong(snapshot.workerUuid().getLeastSignificantBits());
        writeString(buffer, snapshot.dimension(), 256);
        buffer.writeVarLong(snapshot.stateSequence());
        buffer.writeVarInt(snapshot.configurationRevision());
        writeString(buffer, snapshot.targetBlockId(), 256);
        buffer.writeVarInt(snapshot.job());
        buffer.writeVarInt(snapshot.activity());
        buffer.writeVarInt(snapshot.blockReason());
        buffer.writeVarInt(snapshot.runtimeState());
        buffer.writeVarInt(snapshot.requestedCount());
        buffer.writeBoolean(snapshot.unlimitedCount());
        buffer.writeVarInt(snapshot.completedCount());
        buffer.writeBoolean(snapshot.hasCurrentTarget());
        buffer.writeBlockPos(snapshot.currentTarget());
        buffer.writeBoolean(snapshot.hasCurrentWorkPosition());
        buffer.writeBlockPos(snapshot.currentWorkPosition());
        buffer.writeBoolean(snapshot.hasStorage());
        writeString(buffer, snapshot.storageDimension(), 256);
        buffer.writeBlockPos(snapshot.storagePosition());
        writeString(buffer, snapshot.workAreaDimension(), 256);
        buffer.writeBlockPos(snapshot.workAreaCenter());
        buffer.writeVarInt(snapshot.horizontalRadius());
        buffer.writeVarInt(snapshot.verticalRadius());
        buffer.writeVarInt(snapshot.usedSlots());
        buffer.writeVarInt(snapshot.capacity());
        buffer.writeVarInt(snapshot.itemCount());
        buffer.writeVarInt(snapshot.ticketCount());
        buffer.writeVarInt(snapshot.searchTicketCount());
        buffer.writeVarInt(snapshot.simulationTicketCount());
        buffer.writeVarInt(snapshot.totalTicketCount());
        buffer.writeVarInt(snapshot.replanAttempts());
        buffer.writeVarInt(snapshot.lastProgressAgeTicks());
        writeString(buffer, snapshot.searchPhase(), 64);
        writeString(buffer, snapshot.searchMode(), 32);
        buffer.writeVarLong(snapshot.searchGeneration());
        buffer.writeVarInt(snapshot.chunksExamined());
        buffer.writeVarInt(snapshot.frontierIndex());
        buffer.writeVarInt(snapshot.frontierSize());
        buffer.writeVarInt(snapshot.chunksScanned());
        buffer.writeVarInt(snapshot.dirtyChunks());
        buffer.writeVarInt(snapshot.inFlightChunks());
        buffer.writeVarInt(snapshot.indexedTargets());
        buffer.writeVarInt(snapshot.searchQueueDepth());
        buffer.writeVarInt(snapshot.positionsExamined());
        buffer.writeVarInt(snapshot.matchingBlocks());
        buffer.writeVarInt(snapshot.candidatesFound());
        buffer.writeVarInt(snapshot.candidatesRejectedByPolicy());
        buffer.writeVarInt(snapshot.candidatesRejectedAsUnreachable());
        buffer.writeVarInt(snapshot.cachedCandidates());
        buffer.writeBoolean(snapshot.waitingForSearchChunk());
        buffer.writeBoolean(snapshot.pathRequested());
        writeString(buffer, snapshot.lastScannedChunk(), 64);
        writeString(buffer, snapshot.requestedSearchChunk(), 64);
        buffer.writeVarLong(snapshot.searchElapsedNanos());
        buffer.writeVarLong(snapshot.maxCaptureNanos());
        writeString(buffer, snapshot.pathingStatus(), 32);
        writeString(buffer, snapshot.lastNavigationDestination(), 128);
        buffer.writeVarInt(snapshot.pathNode());
        buffer.writeVarInt(snapshot.pathLength());
        buffer.writeDouble(snapshot.pathCost());
        buffer.writeVarInt(snapshot.pathQueueDepth());
        buffer.writeVarLong(snapshot.pathElapsedNanos());
        buffer.writeBoolean(snapshot.allowBreakingObstructions());
        buffer.writeBoolean(snapshot.allowBlockPlacement());
        buffer.writeBoolean(snapshot.allowBridging());
        buffer.writeBoolean(snapshot.allowPillaring());
        buffer.writeBoolean(snapshot.allowParkour());
        buffer.writeBoolean(snapshot.allowWaterRoutes());
        buffer.writeBoolean(snapshot.preferSaferRoutes());
        buffer.writeBoolean(snapshot.avoidDestructiveRouting());
        writeStrings(buffer, snapshot.exclusions(), 128, 256);
        buffer.writeVarInt(Math.min(snapshot.noWorkZones().size(), 128));
        for (int i = 0; i < snapshot.noWorkZones().size() && i < 128; i++) {
            ZoneSnapshot zone = snapshot.noWorkZones().get(i);
            writeString(buffer, zone.id(), 64);
            writeString(buffer, zone.name(), 128);
            writeString(buffer, zone.dimension(), 256);
            buffer.writeBlockPos(zone.center());
            buffer.writeVarInt(zone.horizontalRadius());
            buffer.writeVarInt(zone.verticalRadius());
            buffer.writeVarInt(zone.mode());
            buffer.writeBoolean(zone.enabled());
        }
        writeStrings(buffer, snapshot.activityHistory(), 100, 512);
        writeString(buffer, snapshot.resumeNote(), 512);
    }

    static Snapshot readSnapshot(RegistryFriendlyByteBuf buffer) {
        UUID workerUuid = new UUID(buffer.readLong(), buffer.readLong());
        String dimension = buffer.readUtf(256);
        long stateSequence = buffer.readVarLong();
        int revision = buffer.readVarInt();
        String target = buffer.readUtf(256);
        int job = buffer.readVarInt();
        int activity = buffer.readVarInt();
        int reason = buffer.readVarInt();
        int runtime = buffer.readVarInt();
        int requested = buffer.readVarInt();
        boolean unlimited = buffer.readBoolean();
        int completed = buffer.readVarInt();
        boolean hasTarget = buffer.readBoolean();
        BlockPos currentTarget = buffer.readBlockPos();
        boolean hasWork = buffer.readBoolean();
        BlockPos currentWork = buffer.readBlockPos();
        boolean hasStorage = buffer.readBoolean();
        String storageDimension = buffer.readUtf(256);
        BlockPos storage = buffer.readBlockPos();
        String areaDimension = buffer.readUtf(256);
        BlockPos areaCenter = buffer.readBlockPos();
        int horizontal = buffer.readVarInt();
        int vertical = buffer.readVarInt();
        int used = buffer.readVarInt();
        int capacity = buffer.readVarInt();
        int itemCount = buffer.readVarInt();
        int tickets = buffer.readVarInt();
        int searchTickets = buffer.readVarInt();
        int simulationTickets = buffer.readVarInt();
        int totalTickets = buffer.readVarInt();
        int replans = buffer.readVarInt();
        int lastProgress = buffer.readVarInt();
        String searchPhase = buffer.readUtf(64);
        String searchMode = buffer.readUtf(32);
        long searchGeneration = buffer.readVarLong();
        int chunksExamined = buffer.readVarInt();
        int frontierIndex = buffer.readVarInt();
        int frontierSize = buffer.readVarInt();
        int chunksScanned = buffer.readVarInt();
        int dirtyChunks = buffer.readVarInt();
        int inFlightChunks = buffer.readVarInt();
        int indexedTargets = buffer.readVarInt();
        int searchQueueDepth = buffer.readVarInt();
        int positionsExamined = buffer.readVarInt();
        int matchingBlocks = buffer.readVarInt();
        int candidatesFound = buffer.readVarInt();
        int candidatesRejectedByPolicy = buffer.readVarInt();
        int candidatesRejectedAsUnreachable = buffer.readVarInt();
        int cachedCandidates = buffer.readVarInt();
        boolean waitingForSearchChunk = buffer.readBoolean();
        boolean pathRequested = buffer.readBoolean();
        String lastScannedChunk = buffer.readUtf(64);
        String requestedSearchChunk = buffer.readUtf(64);
        long searchElapsedNanos = buffer.readVarLong();
        long maxCaptureNanos = buffer.readVarLong();
        String pathingStatus = buffer.readUtf(32);
        String lastNavigationDestination = buffer.readUtf(128);
        int pathNode = buffer.readVarInt();
        int pathLength = buffer.readVarInt();
        double pathCost = buffer.readDouble();
        int pathQueueDepth = buffer.readVarInt();
        long pathElapsedNanos = buffer.readVarLong();
        boolean allowBreaking = buffer.readBoolean();
        boolean allowPlacement = buffer.readBoolean();
        boolean allowBridging = buffer.readBoolean();
        boolean allowPillaring = buffer.readBoolean();
        boolean allowParkour = buffer.readBoolean();
        boolean allowWater = buffer.readBoolean();
        boolean safer = buffer.readBoolean();
        boolean avoidDestructive = buffer.readBoolean();
        List<String> exclusions = readStrings(buffer, 128, 256);
        int zoneCount = Math.max(0, Math.min(buffer.readVarInt(), 128));
        List<ZoneSnapshot> zones = new ArrayList<>(zoneCount);
        for (int i = 0; i < zoneCount; i++) {
            zones.add(new ZoneSnapshot(
                    buffer.readUtf(64),
                    buffer.readUtf(128),
                    buffer.readUtf(256),
                    buffer.readBlockPos(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readBoolean()));
        }
        List<String> history = readStrings(buffer, 100, 512);
        String resumeNote = buffer.readUtf(512);
        return new Snapshot(
                workerUuid, dimension, stateSequence, revision, target, job, activity, reason, runtime,
                requested, unlimited, completed, hasTarget, currentTarget, hasWork, currentWork,
                hasStorage, storageDimension, storage, areaDimension, areaCenter, horizontal, vertical,
                used, capacity, itemCount, tickets, searchTickets, simulationTickets, totalTickets,
                replans, lastProgress, searchPhase, searchMode, searchGeneration,
                chunksExamined, frontierIndex, frontierSize, chunksScanned,
                dirtyChunks, inFlightChunks, indexedTargets, searchQueueDepth, positionsExamined,
                matchingBlocks, candidatesFound, candidatesRejectedByPolicy,
                candidatesRejectedAsUnreachable, cachedCandidates, waitingForSearchChunk, pathRequested,
                lastScannedChunk, requestedSearchChunk, searchElapsedNanos, maxCaptureNanos,
                pathingStatus, lastNavigationDestination, pathNode, pathLength, pathCost,
                pathQueueDepth, pathElapsedNanos,
                allowBreaking, allowPlacement, allowBridging, allowPillaring, allowParkour,
                allowWater, safer, avoidDestructive, exclusions, zones, history, resumeNote);
    }

    private static void writeString(RegistryFriendlyByteBuf buffer, String value, int maxChars) {
        String safe = value == null ? "" : value;
        if (safe.length() > maxChars) safe = safe.substring(0, maxChars);
        buffer.writeUtf(safe, maxChars);
    }

    private static void writeStrings(
            RegistryFriendlyByteBuf buffer, List<String> values, int maxCount, int maxChars) {
        int count = Math.min(values == null ? 0 : values.size(), maxCount);
        buffer.writeVarInt(count);
        for (int i = 0; i < count; i++) writeString(buffer, values.get(i), maxChars);
    }

    private static List<String> readStrings(
            RegistryFriendlyByteBuf buffer, int maxCount, int maxChars) {
        int count = Math.max(0, Math.min(buffer.readVarInt(), maxCount));
        List<String> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) values.add(buffer.readUtf(maxChars));
        return List.copyOf(values);
    }
}
