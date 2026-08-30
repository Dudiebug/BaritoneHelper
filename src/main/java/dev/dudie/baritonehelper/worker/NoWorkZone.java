package dev.dudie.baritonehelper.worker;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** Persistent, owner-editable movement/modification policy. */
public final class NoWorkZone {
    private final UUID id;
    private String name;
    private String dimension;
    private BlockPos center;
    private int horizontalRadius;
    private int verticalRadius;
    private NoWorkZoneMode mode;
    private boolean enabled;

    public NoWorkZone(
            UUID id,
            String name,
            String dimension,
            BlockPos center,
            int horizontalRadius,
            int verticalRadius,
            NoWorkZoneMode mode,
            boolean enabled) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.name = name == null ? "" : name;
        this.dimension = dimension == null ? "" : dimension;
        this.center = center == null ? BlockPos.ZERO : center.immutable();
        this.horizontalRadius = Math.max(0, horizontalRadius);
        this.verticalRadius = Math.max(0, verticalRadius);
        this.mode = mode == null ? NoWorkZoneMode.NO_MODIFY : mode;
        this.enabled = enabled;
    }

    public NoWorkZone(String dimension, BlockPos center, int horizontalRadius, int verticalRadius) {
        this(UUID.randomUUID(), "", dimension, center, horizontalRadius, verticalRadius,
                NoWorkZoneMode.NO_MODIFY, true);
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public String dimension() { return dimension; }
    public BlockPos center() { return center; }
    public int horizontalRadius() { return horizontalRadius; }
    public int verticalRadius() { return verticalRadius; }
    public NoWorkZoneMode mode() { return mode; }
    public boolean enabled() { return enabled; }

    public void update(
            String name,
            String dimension,
            BlockPos center,
            int horizontalRadius,
            int verticalRadius,
            NoWorkZoneMode mode,
            boolean enabled) {
        this.name = name == null ? "" : name;
        this.dimension = dimension == null ? "" : dimension;
        this.center = center == null ? BlockPos.ZERO : center.immutable();
        this.horizontalRadius = Math.max(0, horizontalRadius);
        this.verticalRadius = Math.max(0, verticalRadius);
        this.mode = mode == null ? NoWorkZoneMode.NO_MODIFY : mode;
        this.enabled = enabled;
    }

    public boolean contains(String dimension, BlockPos position) {
        if (!enabled || !this.dimension.equals(dimension) || position == null) {
            return false;
        }
        long dx = (long) position.getX() - center.getX();
        long dz = (long) position.getZ() - center.getZ();
        long dy = (long) position.getY() - center.getY();
        return dx * dx + dz * dz <= (long) horizontalRadius * horizontalRadius
                && Math.abs(dy) <= verticalRadius;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        tag.putString("Dimension", dimension);
        tag.putLong("Center", center.asLong());
        tag.putInt("HorizontalRadius", horizontalRadius);
        tag.putInt("VerticalRadius", verticalRadius);
        tag.putString("Mode", mode.name());
        tag.putBoolean("Enabled", enabled);
        return tag;
    }

    public static NoWorkZone load(CompoundTag tag) {
        return new NoWorkZone(
                tag.hasUUID("Id") ? tag.getUUID("Id") : UUID.randomUUID(),
                tag.getString("Name"),
                tag.getString("Dimension"),
                BlockPos.of(tag.getLong("Center")),
                tag.getInt("HorizontalRadius"),
                tag.getInt("VerticalRadius"),
                NoWorkZoneMode.fromSerialized(tag.getString("Mode")),
                !tag.contains("Enabled") || tag.getBoolean("Enabled"));
    }
}
