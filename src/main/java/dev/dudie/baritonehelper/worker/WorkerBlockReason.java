package dev.dudie.baritonehelper.worker;

public enum WorkerBlockReason {
    NONE("reason.baritonehelper.none"),
    NO_TARGET("reason.baritonehelper.no_target"),
    TARGET_EXCLUDED("reason.baritonehelper.target_excluded"),
    NO_MATCHING_BLOCKS("reason.baritonehelper.no_matching_blocks"),
    NO_REACHABLE_POSITION("reason.baritonehelper.no_reachable_position"),
    NAVIGATION_FAILED("reason.baritonehelper.navigation_failed"),
    STUCK("reason.baritonehelper.stuck"),
    MOB_GRIEFING_DISABLED("reason.baritonehelper.mob_griefing_disabled"),
    TARGET_HAS_NO_DROPS("reason.baritonehelper.target_has_no_drops"),
    INVENTORY_FULL_NO_STORAGE("reason.baritonehelper.inventory_full_no_storage"),
    STORAGE_MISSING("reason.baritonehelper.storage_missing"),
    STORAGE_WRONG_DIMENSION("reason.baritonehelper.storage_wrong_dimension"),
    STORAGE_FULL("reason.baritonehelper.storage_full");

    private final String translationKey;

    WorkerBlockReason(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return translationKey;
    }

    public static WorkerBlockReason fromSerialized(String value) {
        try {
            return WorkerBlockReason.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return NONE;
        }
    }
}
