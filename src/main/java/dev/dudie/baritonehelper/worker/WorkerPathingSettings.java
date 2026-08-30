package dev.dudie.baritonehelper.worker;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Collector-scoped movement permissions; no combat/following settings live here. */
public final class WorkerPathingSettings {
    public boolean allowBreakingObstructions = true;
    public boolean allowBlockPlacement = true;
    public boolean allowBridging = true;
    public boolean allowPillaring = true;
    public boolean allowParkour = true;
    public boolean allowWaterRoutes = true;
    public boolean preferSaferRoutes = true;
    public boolean avoidDestructiveRouting = true;
    /** Blocks that may be consumed as temporary bridge/pillar/traverse support. */
    private final Set<ResourceLocation> traversalBlocks = new LinkedHashSet<>(Set.of(
            ResourceLocation.fromNamespaceAndPath("minecraft", "cobblestone"),
            ResourceLocation.fromNamespaceAndPath("minecraft", "cobbled_deepslate"),
            ResourceLocation.fromNamespaceAndPath("minecraft", "dirt"),
            ResourceLocation.fromNamespaceAndPath("minecraft", "netherrack")));

    public Set<ResourceLocation> traversalBlocks() { return Set.copyOf(traversalBlocks); }
    public boolean allowsTraversal(ResourceLocation id) { return id != null && traversalBlocks.contains(id); }
    public void replaceTraversalBlocks(Set<ResourceLocation> ids) {
        traversalBlocks.clear();
        if (ids != null) traversalBlocks.addAll(ids);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("AllowBreakingObstructions", allowBreakingObstructions);
        tag.putBoolean("AllowBlockPlacement", allowBlockPlacement);
        tag.putBoolean("AllowBridging", allowBridging);
        tag.putBoolean("AllowPillaring", allowPillaring);
        tag.putBoolean("AllowParkour", allowParkour);
        tag.putBoolean("AllowWaterRoutes", allowWaterRoutes);
        tag.putBoolean("PreferSaferRoutes", preferSaferRoutes);
        tag.putBoolean("AvoidDestructiveRouting", avoidDestructiveRouting);
        ListTag traversal = new ListTag();
        traversalBlocks.stream().map(ResourceLocation::toString)
                .sorted().forEach(id -> traversal.add(StringTag.valueOf(id)));
        tag.put("TraversalBlocks", traversal);
        return tag;
    }

    public void load(CompoundTag tag) {
        allowBreakingObstructions = !tag.contains("AllowBreakingObstructions") || tag.getBoolean("AllowBreakingObstructions");
        allowBlockPlacement = !tag.contains("AllowBlockPlacement") || tag.getBoolean("AllowBlockPlacement");
        allowBridging = !tag.contains("AllowBridging") || tag.getBoolean("AllowBridging");
        allowPillaring = !tag.contains("AllowPillaring") || tag.getBoolean("AllowPillaring");
        allowParkour = !tag.contains("AllowParkour") || tag.getBoolean("AllowParkour");
        allowWaterRoutes = !tag.contains("AllowWaterRoutes") || tag.getBoolean("AllowWaterRoutes");
        preferSaferRoutes = !tag.contains("PreferSaferRoutes") || tag.getBoolean("PreferSaferRoutes");
        avoidDestructiveRouting = !tag.contains("AvoidDestructiveRouting") || tag.getBoolean("AvoidDestructiveRouting");
        if (tag.contains("TraversalBlocks", Tag.TAG_LIST)) {
            Set<ResourceLocation> loaded = new LinkedHashSet<>();
            ListTag traversal = tag.getList("TraversalBlocks", Tag.TAG_STRING);
            for (int i = 0; i < traversal.size(); i++) {
                ResourceLocation id = ResourceLocation.tryParse(traversal.getString(i));
                if (id != null) loaded.add(id);
            }
            replaceTraversalBlocks(loaded);
        }
    }
}
