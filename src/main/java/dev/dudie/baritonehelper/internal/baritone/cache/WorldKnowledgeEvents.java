package dev.dudie.baritonehelper.internal.baritone.cache;

import dev.dudie.baritonehelper.BaritoneHelper;
import dev.dudie.baritonehelper.internal.baritone.api.utils.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;

/** Conservatively invalidates persisted observations after server world changes. */
@EventBusSubscriber(modid = BaritoneHelper.MOD_ID)
public final class WorldKnowledgeEvents {
   private WorldKnowledgeEvents() {
   }

   @SubscribeEvent
   public static void onBreak(BlockEvent.BreakEvent event) {
      markDirty(event.getLevel(), event.getPos());
   }

   @SubscribeEvent
   public static void onPlace(BlockEvent.EntityPlaceEvent event) {
      markDirty(event.getLevel(), event.getPos());
   }

   @SubscribeEvent
   public static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
      markDirty(event.getLevel(), event.getPos());
      markDirty(event.getLevel(), event.getLiquidPos());
   }

   @SubscribeEvent
   public static void onPiston(PistonEvent.Post event) {
      markDirty(event.getLevel(), event.getPos());
      markDirty(event.getLevel(), event.getFaceOffsetPos());
      event.getStructureHelper().getToPush().forEach(pos -> {
         markDirty(event.getLevel(), pos);
         markDirty(event.getLevel(), pos.relative(event.getDirection()));
      });
      event.getStructureHelper().getToDestroy().forEach(pos -> markDirty(event.getLevel(), pos));
   }

   @SubscribeEvent
   public static void onExplosion(ExplosionEvent.Detonate event) {
      if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
      event.getAffectedBlocks().stream()
            .map(pos -> ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4))
            .distinct()
            .forEach(chunk -> SharedWorldKnowledge.get(serverLevel).cachedWorld().markDirty(chunk));
   }

   /** Commit-time invalidation hook for mutations that do not emit a BlockEvent. */
   public static void markDirty(LevelAccessor level, BlockPos position) {
      if (!(level instanceof ServerLevel serverLevel) || position == null) return;
      SharedWorldKnowledge knowledge = SharedWorldKnowledge.get(serverLevel);
      knowledge.cachedWorld().markDirty(ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4));
   }

   /** Exact commit-time update for a mutation whose before/after states are known. */
   public static void recordBlockChange(
         LevelAccessor level, BlockPos position, BlockState before, BlockState after) {
      if (!(level instanceof ServerLevel serverLevel)
            || position == null || before == null || after == null) return;
      SharedWorldKnowledge.get(serverLevel).cachedWorld().recordBlockChange(
            ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4),
            position.asLong(),
            BlockUtils.blockToString(before.getBlock()),
            BlockUtils.blockToString(after.getBlock()));
   }
}
