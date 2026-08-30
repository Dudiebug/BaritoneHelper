package dev.dudie.baritonehelper.worker;

/** Policy applied to a configured spherical (ellipsoid in Y) no-work zone. */
public enum NoWorkZoneMode {
    NO_MODIFY,
    NO_ENTER;

    public static NoWorkZoneMode fromSerialized(String value) {
        try {
            return valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return NO_MODIFY;
        }
    }
}
