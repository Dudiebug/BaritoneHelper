package dev.dudie.baritonehelper.worker;

/** How a worker expands discovery when no known target remains. */
public enum SearchMode {
    WORK_AREA("work_area"),
    ROAM("roam");

    private final String serializedName;

    SearchMode(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    /** Missing and future values stay bounded for save compatibility. */
    public static SearchMode fromSerialized(String value) {
        for (SearchMode mode : values()) {
            if (mode.serializedName.equals(value)) return mode;
        }
        return WORK_AREA;
    }
}
