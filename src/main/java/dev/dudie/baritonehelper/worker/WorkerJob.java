package dev.dudie.baritonehelper.worker;

public enum WorkerJob {
    IDLE,
    COLLECT,
    DEPOSIT,
    PAUSED,
    BLOCKED;

    public boolean activelyWorks() {
        return this == COLLECT || this == DEPOSIT;
    }

    public WorkerJob resumableFallback() {
        return activelyWorks() ? this : COLLECT;
    }

    public static WorkerJob fromSerialized(String value) {
        try {
            return WorkerJob.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return IDLE;
        }
    }
}
