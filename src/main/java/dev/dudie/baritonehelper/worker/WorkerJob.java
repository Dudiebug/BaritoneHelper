package dev.dudie.baritonehelper.worker;

public enum WorkerJob {
    IDLE,
    READY,
    COLLECT,
    DEPOSIT,
    BLOCKED;

    public boolean activelyWorks() {
        return this == COLLECT || this == DEPOSIT;
    }

    public boolean isStopped() {
        return this == IDLE || this == READY;
    }

    public static WorkerJob fromSerialized(String value) {
        if ("PAUSED".equals(value)) {
            return READY;
        }
        try {
            return WorkerJob.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return IDLE;
        }
    }
}
