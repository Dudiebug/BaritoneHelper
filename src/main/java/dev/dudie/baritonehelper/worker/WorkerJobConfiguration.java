package dev.dudie.baritonehelper.worker;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Authoritative persistent configuration, separate from transient path state. */
public final class WorkerJobConfiguration {
    public static final int MIN_HORIZONTAL_RADIUS = 8;
    public static final int MAX_HORIZONTAL_RADIUS = 512;
    public static final int MIN_VERTICAL_RADIUS = 4;
    public static final int MAX_VERTICAL_RADIUS = 128;
    public static final int DEFAULT_HORIZONTAL_RADIUS = MAX_HORIZONTAL_RADIUS;
    public static final int DEFAULT_VERTICAL_RADIUS = MAX_VERTICAL_RADIUS;
    public static final int MIN_REQUESTED_COUNT = 1;
    public static final int MAX_REQUESTED_COUNT = 1_000_000;

    private ResourceLocation targetBlockId;
    private int requestedBlockCount = 64;
    private boolean unlimitedCount = true;
    private int completedBlockCount;
    private String workAreaDimension = "";
    private BlockPos workAreaCenter = BlockPos.ZERO;
    private int horizontalSearchRadius = DEFAULT_HORIZONTAL_RADIUS;
    private int verticalSearchRadius = DEFAULT_VERTICAL_RADIUS;
    private String storageDimension = "";
    private BlockPos storagePosition;
    private final Set<ResourceLocation> exclusions = new LinkedHashSet<>();
    private final List<NoWorkZone> noWorkZones = new ArrayList<>();
    private final WorkerPathingSettings pathing = new WorkerPathingSettings();
    private int revision;

    public ResourceLocation targetBlockId() { return targetBlockId; }
    public int requestedBlockCount() { return requestedBlockCount; }
    public boolean unlimitedCount() { return unlimitedCount; }
    public int completedBlockCount() { return completedBlockCount; }
    public String workAreaDimension() { return workAreaDimension; }
    public BlockPos workAreaCenter() { return workAreaCenter; }
    public int horizontalSearchRadius() { return horizontalSearchRadius; }
    public int verticalSearchRadius() { return verticalSearchRadius; }
    public String storageDimension() { return storageDimension; }
    public BlockPos storagePosition() { return storagePosition; }
    public Set<ResourceLocation> exclusions() { return Set.copyOf(exclusions); }
    public List<NoWorkZone> noWorkZones() { return List.copyOf(noWorkZones); }
    public WorkerPathingSettings pathing() { return pathing; }
    public int revision() { return revision; }
    public void touch() { revision++; }

    public boolean complete() {
        return !unlimitedCount && completedBlockCount >= requestedBlockCount;
    }

    public void setTarget(ResourceLocation id, BlockPos origin, String dimension) {
        boolean changed = targetBlockId == null ? id != null : !targetBlockId.equals(id);
        targetBlockId = id;
        if (origin != null) {
            workAreaCenter = origin.immutable();
        }
        if (dimension != null) {
            workAreaDimension = dimension;
        }
        if (changed) {
            completedBlockCount = 0;
        }
        revision++;
    }

    public void clearTarget() { targetBlockId = null; completedBlockCount = 0; revision++; }

    public void setRequestedAmount(int count, boolean unlimited) {
        int bounded = Math.max(MIN_REQUESTED_COUNT, Math.min(MAX_REQUESTED_COUNT, count));
        if (requestedBlockCount != bounded || unlimitedCount != unlimited) {
            completedBlockCount = 0;
        }
        requestedBlockCount = bounded;
        unlimitedCount = unlimited;
        revision++;
    }

    public void resetProgress() { completedBlockCount = 0; revision++; }

    public void incrementCompleted() {
        completedBlockCount = Math.min(MAX_REQUESTED_COUNT, completedBlockCount + 1);
        revision++;
    }

    public void setWorkArea(String dimension, BlockPos center, int horizontal, int vertical) {
        workAreaDimension = dimension == null ? "" : dimension;
        workAreaCenter = center == null ? BlockPos.ZERO : center.immutable();
        horizontalSearchRadius = Math.max(MIN_HORIZONTAL_RADIUS, Math.min(MAX_HORIZONTAL_RADIUS, horizontal));
        verticalSearchRadius = Math.max(MIN_VERTICAL_RADIUS, Math.min(MAX_VERTICAL_RADIUS, vertical));
        revision++;
    }

    public void clearWorkArea() { workAreaDimension = ""; workAreaCenter = BlockPos.ZERO; revision++; }

    public void setStorage(String dimension, BlockPos position) {
        storageDimension = dimension == null ? "" : dimension;
        storagePosition = position == null ? null : position.immutable();
        revision++;
    }

    public void clearStorage() { storageDimension = ""; storagePosition = null; revision++; }

    public void setExcluded(ResourceLocation id, boolean excluded) {
        if (id != null && excluded) exclusions.add(id); else if (id != null) exclusions.remove(id);
        revision++;
    }

    public void replaceNoWorkZones(List<NoWorkZone> zones) {
        noWorkZones.clear();
        if (zones != null) noWorkZones.addAll(zones);
        revision++;
    }

    public boolean inZone(String dimension, BlockPos position, NoWorkZoneMode mode) {
        return noWorkZones.stream().anyMatch(zone -> zone.mode() == mode && zone.contains(dimension, position));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (targetBlockId != null) tag.putString("TargetBlockId", targetBlockId.toString());
        tag.putInt("RequestedBlockCount", requestedBlockCount);
        tag.putBoolean("UnlimitedCount", unlimitedCount);
        tag.putInt("CompletedBlockCount", completedBlockCount);
        tag.putString("WorkAreaDimension", workAreaDimension);
        tag.putLong("WorkAreaCenter", workAreaCenter.asLong());
        tag.putInt("HorizontalSearchRadius", horizontalSearchRadius);
        tag.putInt("VerticalSearchRadius", verticalSearchRadius);
        tag.putString("StorageDimension", storageDimension);
        if (storagePosition != null) tag.putLong("StoragePosition", storagePosition.asLong());
        tag.putInt("ConfigurationRevision", revision);
        ListTag excluded = new ListTag();
        exclusions.forEach(id -> excluded.add(StringTag.valueOf(id.toString())));
        tag.put("Exclusions", excluded);
        ListTag zones = new ListTag();
        noWorkZones.forEach(zone -> zones.add(zone.save()));
        tag.put("NoWorkZones", zones);
        tag.put("Pathing", pathing.save());
        return tag;
    }

    public void load(CompoundTag tag, BlockPos fallbackOrigin, String fallbackDimension) {
        targetBlockId = tag.contains("TargetBlockId", Tag.TAG_STRING)
                ? ResourceLocation.tryParse(tag.getString("TargetBlockId")) : null;
        requestedBlockCount = Math.max(MIN_REQUESTED_COUNT, Math.min(MAX_REQUESTED_COUNT,
                tag.contains("RequestedBlockCount") ? tag.getInt("RequestedBlockCount") : requestedBlockCount));
        unlimitedCount = !tag.contains("UnlimitedCount") || tag.getBoolean("UnlimitedCount");
        // Unlimited jobs may legitimately exceed the finite default request;
        // clamp only to the protocol's safety ceiling when restoring them.
        completedBlockCount = Math.max(0, Math.min(MAX_REQUESTED_COUNT, tag.getInt("CompletedBlockCount")));
        workAreaDimension = tag.getString("WorkAreaDimension");
        if (workAreaDimension.isBlank()) workAreaDimension = fallbackDimension == null ? "" : fallbackDimension;
        workAreaCenter = tag.contains("WorkAreaCenter", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("WorkAreaCenter")) : fallbackOrigin;
        horizontalSearchRadius = Math.max(MIN_HORIZONTAL_RADIUS, Math.min(MAX_HORIZONTAL_RADIUS,
                tag.contains("HorizontalSearchRadius") ? tag.getInt("HorizontalSearchRadius") : DEFAULT_HORIZONTAL_RADIUS));
        verticalSearchRadius = Math.max(MIN_VERTICAL_RADIUS, Math.min(MAX_VERTICAL_RADIUS,
                tag.contains("VerticalSearchRadius") ? tag.getInt("VerticalSearchRadius") : DEFAULT_VERTICAL_RADIUS));
        storageDimension = tag.getString("StorageDimension");
        storagePosition = tag.contains("StoragePosition", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("StoragePosition")) : null;
        revision = Math.max(0, tag.getInt("ConfigurationRevision"));
        exclusions.clear();
        ListTag excluded = tag.getList("Exclusions", Tag.TAG_STRING);
        for (int i = 0; i < excluded.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(excluded.getString(i));
            if (id != null) exclusions.add(id);
        }
        noWorkZones.clear();
        ListTag zones = tag.getList("NoWorkZones", Tag.TAG_COMPOUND);
        for (int i = 0; i < zones.size(); i++) noWorkZones.add(NoWorkZone.load(zones.getCompound(i)));
        if (tag.contains("Pathing", Tag.TAG_COMPOUND)) pathing.load(tag.getCompound("Pathing"));
    }
}
