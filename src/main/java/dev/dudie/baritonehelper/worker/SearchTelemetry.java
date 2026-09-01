package dev.dudie.baritonehelper.worker;

/** Immutable server-thread view of one worker's discovery process. */
public record SearchTelemetry(
        String phase,
        SearchMode mode,
        long generation,
        int chunksExamined,
        int chunksScanned,
        int dirtyChunks,
        int inFlightChunks,
        int indexedTargets,
        int positionsExamined,
        int matchingBlocks,
        int candidatesFound,
        int candidatesRejectedByPolicy,
        int candidatesRejectedAsUnreachable,
        int cachedCandidates,
        int frontierIndex,
        int frontierSize,
        boolean waitingForSearchChunk,
        int queueDepth,
        String lastScannedChunk,
        String requestedSearchChunk,
        long elapsedNanos,
        long maxCaptureNanos) {

    public SearchTelemetry {
        phase = phase == null ? "IDLE" : phase;
        mode = mode == null ? SearchMode.WORK_AREA : mode;
        lastScannedChunk = lastScannedChunk == null ? "" : lastScannedChunk;
        requestedSearchChunk = requestedSearchChunk == null ? "" : requestedSearchChunk;
    }

    public static SearchTelemetry idle(SearchMode mode) {
        return new SearchTelemetry("IDLE", mode, 0L, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, false, 0, "", "", 0L, 0L);
    }
}
