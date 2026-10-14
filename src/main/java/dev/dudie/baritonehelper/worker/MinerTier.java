package dev.dudie.baritonehelper.worker;

import java.util.Optional;

/**
 * Create-gated miner progression. A freshly crafted worker starts at
 * {@link #ANDESITE}; upgrade kits advance a placed worker one tier at a time.
 *
 * <p>Workers without a saved tier (pre-progression saves and directly spawned
 * entities) load as {@link #PRECISION} so existing worlds keep their previous
 * capabilities.
 */
public enum MinerTier {
    ANDESITE("andesite", 128, 64, 0.7F, false, false, false),
    BRASS("brass", 256, 96, 0.85F, false, true, true),
    PRECISION("precision", WorkerJobConfiguration.MAX_HORIZONTAL_RADIUS,
            WorkerJobConfiguration.MAX_VERTICAL_RADIUS, 1.0F, true, true, true);

    private final String serializedName;
    private final int maxHorizontalRadius;
    private final int maxVerticalRadius;
    private final float breakSpeedMultiplier;
    private final boolean allowsRoam;
    private final boolean worksWhileOwnerOffline;
    private final boolean acceptsCargoUpgrade;

    MinerTier(
            String serializedName,
            int maxHorizontalRadius,
            int maxVerticalRadius,
            float breakSpeedMultiplier,
            boolean allowsRoam,
            boolean worksWhileOwnerOffline,
            boolean acceptsCargoUpgrade) {
        this.serializedName = serializedName;
        this.maxHorizontalRadius = maxHorizontalRadius;
        this.maxVerticalRadius = maxVerticalRadius;
        this.breakSpeedMultiplier = breakSpeedMultiplier;
        this.allowsRoam = allowsRoam;
        this.worksWhileOwnerOffline = worksWhileOwnerOffline;
        this.acceptsCargoUpgrade = acceptsCargoUpgrade;
    }

    public String serializedName() { return serializedName; }
    public int maxHorizontalRadius() { return maxHorizontalRadius; }
    public int maxVerticalRadius() { return maxVerticalRadius; }
    public float breakSpeedMultiplier() { return breakSpeedMultiplier; }
    public boolean allowsRoam() { return allowsRoam; }
    public boolean worksWhileOwnerOffline() { return worksWhileOwnerOffline; }
    public boolean acceptsCargoUpgrade() { return acceptsCargoUpgrade; }
    public String translationKey() { return "tier.baritonehelper." + serializedName; }

    public int clampHorizontal(int radius) { return Math.min(radius, maxHorizontalRadius); }
    public int clampVertical(int radius) { return Math.min(radius, maxVerticalRadius); }

    public Optional<MinerTier> next() {
        int index = ordinal() + 1;
        return index < values().length ? Optional.of(values()[index]) : Optional.empty();
    }

    public static MinerTier fromSerialized(String value) {
        for (MinerTier tier : values()) {
            if (tier.serializedName.equals(value)) return tier;
        }
        return PRECISION;
    }
}
