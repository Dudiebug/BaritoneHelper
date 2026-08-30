package dev.dudie.baritonehelper.worker;

public enum WorkerActivity {
    IDLE("activity.baritonehelper.idle"),
    READY("activity.baritonehelper.ready"),
    SEARCHING("activity.baritonehelper.searching"),
    PATHING("activity.baritonehelper.pathing"),
    COLLECTING("activity.baritonehelper.collecting"),
    RETURNING("activity.baritonehelper.returning"),
    DEPOSITING("activity.baritonehelper.depositing"),
    BLOCKED("activity.baritonehelper.blocked");

    private final String translationKey;

    WorkerActivity(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return translationKey;
    }
}
