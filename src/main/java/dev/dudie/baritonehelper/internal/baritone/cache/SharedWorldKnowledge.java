package dev.dudie.baritonehelper.internal.baritone.cache;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Dimension-owned persistent observations shared by every worker Baritone. */
public final class SharedWorldKnowledge extends SavedData {
   private static final int CURRENT_SCHEMA = 1;
   private static final String DATA_NAME = "baritonehelper_world_knowledge";
   private static final Factory<SharedWorldKnowledge> FACTORY = new Factory<>(
         SharedWorldKnowledge::new,
         SharedWorldKnowledge::load);

   private final TargetCoverageLedger ledger = new TargetCoverageLedger(this::setDirty);
   private final CachedWorld cachedWorld = new CachedWorld(this.ledger);

   public static SharedWorldKnowledge get(ServerLevel serverLevel) {
      return serverLevel.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
   }

   public CachedWorld cachedWorld() {
      return this.cachedWorld;
   }

   public TargetCoverageLedger ledger() {
      return this.ledger;
   }

   @Override
   public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
      tag.putInt("Schema", CURRENT_SCHEMA);
      ListTag entries = new ListTag();
      for (TargetCoverageLedger.ChunkSnapshot snapshot : this.ledger.snapshot()) {
         CompoundTag entry = new CompoundTag();
         entry.putString("Target", snapshot.target());
         entry.putLong("Chunk", snapshot.chunk());
         entry.putString("State", snapshot.state().name());
         entry.putLongArray("Locations", snapshot.locations().stream().mapToLong(Long::longValue).toArray());
         entries.add(entry);
      }
      tag.put("Entries", entries);
      return tag;
   }

   private static SharedWorldKnowledge load(CompoundTag tag, HolderLookup.Provider registries) {
      SharedWorldKnowledge data = new SharedWorldKnowledge();
      if (tag.getInt("Schema") != CURRENT_SCHEMA) return data;

      List<TargetCoverageLedger.ChunkSnapshot> snapshots = new ArrayList<>();
      ListTag entries = tag.getList("Entries", Tag.TAG_COMPOUND);
      for (int index = 0; index < entries.size(); index++) {
         CompoundTag entry = entries.getCompound(index);
         CoverageState state;
         try {
            state = CoverageState.valueOf(entry.getString("State"));
         } catch (IllegalArgumentException ignored) {
            continue;
         }
         long[] packed = entry.getLongArray("Locations");
         List<Long> locations = new ArrayList<>(packed.length);
         for (long location : packed) locations.add(location);
         snapshots.add(new TargetCoverageLedger.ChunkSnapshot(
               entry.getString("Target"), entry.getLong("Chunk"), state, Set.copyOf(locations)));
      }
      data.ledger.restore(snapshots);
      return data;
   }
}
