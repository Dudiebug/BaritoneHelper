package dev.dudie.baritonehelper.worker;

/** Fine-grained runtime state shown by the dashboard; WorkerJob remains the v1 save alias. */
public enum WorkerRuntimeState {
    UNCONFIGURED,
    READY,
    STARTING,
    SEARCHING,
    PATHING,
    BREAKING,
    COLLECTING_DROPS,
    RETURNING_TO_STORAGE,
    DEPOSITING,
    COMPLETED,
    STOPPING,
    BLOCKED;

    public static WorkerRuntimeState fromSerialized(String value) {
        try {
            return value == null || value.isBlank() ? UNCONFIGURED : valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return UNCONFIGURED;
        }
    }
}
