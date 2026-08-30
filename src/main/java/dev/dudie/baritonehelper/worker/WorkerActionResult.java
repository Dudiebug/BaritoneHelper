package dev.dudie.baritonehelper.worker;

public enum WorkerActionResult {
    STARTED,
    ALREADY_RUNNING,
    STOPPED,
    ALREADY_STOPPED,
    TARGET_CLEARED,
    NO_TARGET,
    TARGET_EXCLUDED
}
