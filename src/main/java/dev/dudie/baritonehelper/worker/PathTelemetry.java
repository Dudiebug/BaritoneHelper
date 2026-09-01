package dev.dudie.baritonehelper.worker;

import dev.dudie.baritonehelper.internal.baritone.api.behavior.PathingStatus;

/** Immutable server-thread view of current A* calculation and path execution. */
public record PathTelemetry(
        PathingStatus status,
        int pathNode,
        int pathLength,
        double remainingCost,
        String destination,
        int queueDepth,
        int viewTickets,
        int simulationTickets,
        int searchTickets,
        long elapsedNanos) {

    public PathTelemetry {
        status = status == null ? PathingStatus.IDLE : status;
        destination = destination == null ? "" : destination;
    }

    public static PathTelemetry idle() {
        return new PathTelemetry(PathingStatus.IDLE, 0, 0, 0.0D, "", 0, 0, 0, 0, 0L);
    }
}
