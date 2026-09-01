/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.dudie.baritonehelper.internal.baritone.cache;

import dev.dudie.baritonehelper.internal.baritone.InternalBaritoneRuntime;
import dev.dudie.baritonehelper.entity.WorkerEntity;
import dev.dudie.baritonehelper.worker.SearchMode;
import dev.dudie.baritonehelper.internal.baritone.api.IBaritone;
import dev.dudie.baritonehelper.internal.baritone.api.cache.IWorldScanner;
import dev.dudie.baritonehelper.internal.baritone.api.utils.BetterBlockPos;
import dev.dudie.baritonehelper.internal.baritone.api.utils.BlockUtils;
import dev.dudie.baritonehelper.internal.baritone.api.utils.BlockOptionalMetaLookup;
import dev.dudie.baritonehelper.internal.baritone.api.utils.IEntityContext;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;

/** Server-thread chunk snapshot creation and allocation-light palette scans. */
public enum WorldScanner implements IWorldScanner {
   INSTANCE;

   public static final int SECTION_HEIGHT = 16;
   /** Maximum immutable chunk snapshots copied and later published by one server tick. */
   public static final int CAPTURE_CHUNK_BUDGET = 1;
   /** MineProcess requests a scan every five ticks; retain a queued turn across that cadence. */
   static final int CAPTURE_WAITER_STALE_TICKS = 20;
   private static final Map<ServerLevel, CaptureBudget> CAPTURE_BUDGETS = new WeakHashMap<>();

   @Override
   public List<BlockPos> scanChunkRadius(IEntityContext ctx, BlockOptionalMetaLookup filter, int max, int yLevelThreshold, int maxSearchRadius) {
      if (filter.blocks().isEmpty() || max <= 0 || maxSearchRadius < 0) {
         return new ArrayList<>();
      }

      ScanSnapshot snapshot = this.capture(ctx, filter, maxSearchRadius);
      try {
         List<BlockPos> result = this.scanSnapshot(snapshot, filter, max, yLevelThreshold);
         Set<Long> rejectedChunks = snapshot.publishTargetScans();
         result.removeIf(position -> rejectedChunks.contains(
               ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4)));
         return result;
      } catch (Throwable error) {
         snapshot.abortTargetScans();
         throw error;
      }
   }

   /**
    * Copies loaded chunk palettes on the server thread. The returned object is
    * safe to consume from a planner thread; it contains no live world access.
    */
   public ScanSnapshot capture(IEntityContext ctx, int maxSearchRadius) {
      return this.capture(ctx, null, maxSearchRadius);
   }

   /** Captures loaded palettes and records the worker policy for matching blocks. */
   public ScanSnapshot capture(IEntityContext ctx, BlockOptionalMetaLookup filter, int maxSearchRadius) {
      ServerLevel world = ctx.world();
      assertServerThread(world);

      BetterBlockPos playerPos = ctx.feetPos();
      int playerChunkX = playerPos.getX() >> 4;
      int playerChunkZ = playerPos.getZ() >> 4;
      int[] coordinateIterationOrder = coordinateIterationOrder(world, playerPos.getY());
      ChunkSource chunkSource = world.getChunkSource();
      ArrayList<ChunkSnapshot> chunks = new ArrayList<>();
      Set<Long> loadedWindow = loadedWindow(ctx);
      WorldData worldData = worldData(ctx);
      Set<String> targets = targetNames(filter);
      Map<Long, Map<String, Long>> begun = new HashMap<>();
      int captureBudget = filter == null
            ? Integer.MAX_VALUE
            : acquireCaptureBudget(world, ctx.entity().getUUID());
      boolean deferred = filter != null && captureBudget == 0;

      try {
         for (ChunkPos pos : nearbyChunkPositions(playerChunkX, playerChunkZ, maxSearchRadius, loadedWindow)) {
            if (chunks.size() >= captureBudget) break;
            LevelChunk chunk = chunkSource.getChunkNow(pos.x, pos.z);
            if (chunk == null) continue;
            Map<String, Long> scanTargets = new HashMap<>();
            if (targetCoverageEligible(ctx, pos)) {
               for (String target : targets) {
                  long revision = worldData == null ? 0L : worldData.beginTargetScan(target, pos.toLong());
                  if (revision >= 0L) scanTargets.put(target, revision);
               }
            }
            if (!scanTargets.isEmpty()) begun.put(pos.toLong(), Map.copyOf(scanTargets));
            if (filter == null || !scanTargets.isEmpty()) chunks.add(snapshotChunk(chunk, filter, scanTargets));
         }
      } catch (Throwable error) {
         if (worldData != null) begun.forEach((chunk, leases) -> leases.forEach(
               (target, revision) -> worldData.abortTargetScan(target, chunk, revision)));
         throw error;
      }

      return new ScanSnapshot(playerPos.getY(), coordinateIterationOrder, List.copyOf(chunks), worldData, deferred);
   }

   private static int acquireCaptureBudget(ServerLevel world, UUID owner) {
      CaptureBudget budget = CAPTURE_BUDGETS.computeIfAbsent(world, ignored -> new CaptureBudget());
      return budget.acquire(owner, world.getGameTime());
   }

   /** Scans a previously captured snapshot without touching the live world. */
   public List<BlockPos> scanSnapshot(ScanSnapshot snapshot, BlockOptionalMetaLookup filter, int max, int yLevelThreshold) {
      if (filter.blocks().isEmpty() || max <= 0) {
         return new ArrayList<>();
      }

      ArrayList<BlockPos> result = new ArrayList<>();
      boolean foundWithinY = false;
      for (ChunkSnapshot chunk : snapshot.chunks) {
         ScanChunkResult scan = this.scanSnapshotChunk(
               chunk,
               filter,
               result,
               max,
               yLevelThreshold,
               snapshot.playerY,
               snapshot.coordinateIterationOrder,
               snapshot.worldData,
               foundWithinY);
         foundWithinY = scan.foundWithinY;
         if (scan.stop) {
            break;
         }
      }
      return result;
   }

   @Override
   public List<BlockPos> scanChunk(IEntityContext ctx, BlockOptionalMetaLookup filter, ChunkPos pos, int max, int yLevelThreshold) {
      if (filter.blocks().isEmpty() || max <= 0) {
         return Collections.emptyList();
      }

      ServerLevel world = ctx.world();
      assertServerThread(world);
      if (!isInLoadedWindow(ctx, pos)) {
         return Collections.emptyList();
      }
      LevelChunk chunk = world.getChunkSource().getChunkNow(pos.x, pos.z);
      if (chunk == null || chunk.isEmpty()) {
         return Collections.emptyList();
      }

      ArrayList<BlockPos> result = new ArrayList<>();
      this.scanChunkInto(
            pos.x << 4,
            pos.z << 4,
            chunk,
            filter,
            result,
            max,
            yLevelThreshold,
            ctx.feetPos().getY(),
            coordinateIterationOrder(world, ctx.feetPos().getY()),
            worldData(ctx),
            null,
            Set.of());
      return result;
   }

   @Override
   public int repack(IEntityContext ctx) {
      return this.repack(ctx, 40);
   }

   @Override
   public int repack(IEntityContext ctx, int range) {
      ServerLevel world = ctx.world();
      assertServerThread(world);
      ChunkSource chunkSource = world.getChunkSource();
      BetterBlockPos playerPos = ctx.feetPos();
      int playerChunkX = playerPos.getX() >> 4;
      int playerChunkZ = playerPos.getZ() >> 4;
      int queued = 0;
      Set<Long> loadedWindow = loadedWindow(ctx);

      for (ChunkPos pos : nearbyChunkPositions(playerChunkX, playerChunkZ, range, loadedWindow, true)) {
         LevelChunk chunk = chunkSource.getChunkNow(pos.x, pos.z);
         if (chunk != null && !chunk.isEmpty()) {
            queued++;
         }
      }

      return queued;
   }

   private boolean scanChunkInto(
         int chunkX,
         int chunkZ,
         ChunkAccess chunk,
         BlockOptionalMetaLookup filter,
         Collection<BlockPos> result,
         int max,
         int yLevelThreshold,
         int playerY,
         int[] coordinateIterationOrder,
         WorldData worldData,
          Map<String, Set<Long>> observations,
          Set<String> trackedTargets
   ) {
      LevelChunkSection[] sections = chunk.getSections();
      if (sections.length != coordinateIterationOrder.length) {
         throw new IllegalStateException(
               "Unexpected number of sections in chunk (expected " + coordinateIterationOrder.length + ", got " + sections.length + ")");
      }

      boolean foundWithinY = false;
      for (int yIndex : coordinateIterationOrder) {
         LevelChunkSection section = sections[yIndex];
         if (section == null || section.hasOnlyAir()) {
            continue;
         }

         // Query the palette before walking its 4096 entries.
         PalettedContainer<BlockState> states = section.getStates();
         if (!states.maybeHas(filter::has)) {
            continue;
         }

         ScanSectionResult scan = scanPalette(
               chunkX,
               chunkZ,
               chunk.getMinBuildHeight(),
               yIndex,
               states,
               filter,
               result,
               max,
               yLevelThreshold,
                playerY,
                foundWithinY,
                observations,
                trackedTargets,
                null);
         foundWithinY = scan.foundWithinY;
         if (scan.stop) {
            return true;
         }
      }

      return foundWithinY;
   }

   private ScanChunkResult scanSnapshotChunk(
         ChunkSnapshot chunk,
         BlockOptionalMetaLookup filter,
         Collection<BlockPos> result,
         int max,
         int yLevelThreshold,
         int playerY,
         int[] coordinateIterationOrder,
         WorldData worldData,
         boolean foundWithinY
   ) {
      if (chunk.sections.size() != coordinateIterationOrder.length) {
         throw new IllegalStateException(
               "Unexpected number of sections in chunk (expected " + coordinateIterationOrder.length + ", got " + chunk.sections.size() + ")");
      }

      for (int yIndex : coordinateIterationOrder) {
         PalettedContainer<BlockState> states = chunk.sections.get(yIndex);
         if (states == null || !states.maybeHas(filter::has)) {
            continue;
         }

         ScanSectionResult scan = scanPalette(
               chunk.blockX,
               chunk.blockZ,
               chunk.minBuildHeight,
               yIndex,
               states,
               filter,
               result,
               max,
               yLevelThreshold,
                playerY,
                foundWithinY,
                chunk.observations,
                chunk.targets.keySet(),
                chunk);
         foundWithinY = scan.foundWithinY;
         if (scan.stop) {
            return new ScanChunkResult(foundWithinY, true);
         }
      }

      return new ScanChunkResult(foundWithinY, false);
   }

   private ScanSectionResult scanPalette(
         int chunkX,
         int chunkZ,
         int minBuildHeight,
         int sectionIndex,
         PalettedContainer<BlockState> states,
         BlockOptionalMetaLookup filter,
         Collection<BlockPos> result,
         int max,
         int yLevelThreshold,
         int playerY,
          boolean foundWithinY,
          Map<String, Set<Long>> observations,
          Set<String> trackedTargets,
          ChunkSnapshot counter
   ) {
      int yBase = minBuildHeight + (sectionIndex << 4);
      for (int y = 0; y < 16; y++) {
          if (Thread.currentThread().isInterrupted()) {
             InternalBaritoneRuntime.recordScanCancellation();
             throw new java.util.concurrent.CancellationException("Chunk scan cancelled");
          }
         for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
               if (counter != null) counter.positionsExamined++;
               BlockState state = states.get(x, y, z);
               if (!filter.has(state)) {
                  continue;
               }

                int realY = yBase | y;
                BlockPos pos = new BlockPos(chunkX | x, realY, chunkZ | z);
                String target = BlockUtils.blockToString(state.getBlock());
                if (observations != null && trackedTargets.contains(target)) {
                   observations.computeIfAbsent(target, ignored -> new HashSet<>()).add(pos.asLong());
                }
                if (result.size() >= max) {
                   if (Math.abs(realY - playerY) < yLevelThreshold) {
                      foundWithinY = true;
                   }
                   continue;
                }
                result.add(pos);
            }
         }
      }
      return new ScanSectionResult(foundWithinY, false);
   }

   private static ChunkSnapshot snapshotChunk(
         ChunkAccess chunk, BlockOptionalMetaLookup filter, Map<String, Long> targets) {
      ArrayList<PalettedContainer<BlockState>> sections = new ArrayList<>(chunk.getSections().length);
      for (LevelChunkSection section : chunk.getSections()) {
         if (section == null || section.hasOnlyAir()) {
            sections.add(null);
            continue;
         }
         PalettedContainer<BlockState> source = section.getStates();
         if (filter != null && !source.maybeHas(filter::has)) {
            sections.add(null);
            continue;
         }
         PalettedContainer<BlockState> states = source.copy();
         sections.add(states);
      }
      return new ChunkSnapshot(
            chunk.getPos().toLong(),
            chunk.getPos().x << 4,
            chunk.getPos().z << 4,
            chunk.getMinBuildHeight(),
            Collections.unmodifiableList(sections),
            Map.copyOf(targets));
   }

   private static Set<String> targetNames(BlockOptionalMetaLookup filter) {
      if (filter == null) return Set.of();
      Set<String> targets = new HashSet<>();
      filter.blocks().forEach(meta -> targets.add(BlockUtils.blockToString(meta.getBlock())));
      return Set.copyOf(targets);
   }

   private static Set<Long> loadedWindow(IEntityContext ctx) {
      return ctx.entity() instanceof WorkerEntity worker ? worker.loadedTicketChunks() : null;
   }

   /** WORK_AREA publishes full-chunk coverage only for chunks intersecting its horizontal bounds. */
   private static boolean targetCoverageEligible(IEntityContext ctx, ChunkPos chunk) {
      if (!(ctx.entity() instanceof WorkerEntity worker)
            || worker.searchMode() == SearchMode.ROAM) {
         return true;
      }
      BlockPos center = worker.workAreaCenter();
      long dx = center.getX() < chunk.getMinBlockX()
            ? (long)chunk.getMinBlockX() - center.getX()
            : center.getX() > chunk.getMaxBlockX()
               ? (long)center.getX() - chunk.getMaxBlockX() : 0L;
      long dz = center.getZ() < chunk.getMinBlockZ()
            ? (long)chunk.getMinBlockZ() - center.getZ()
            : center.getZ() > chunk.getMaxBlockZ()
               ? (long)center.getZ() - chunk.getMaxBlockZ() : 0L;
      long radius = worker.horizontalSearchRadius();
      return dx * dx + dz * dz <= radius * radius;
   }

   private static WorldData worldData(IEntityContext ctx) {
      WorldData contextData = ctx.worldData() instanceof WorldData data ? data : null;
      IBaritone baritone;
      try {
         baritone = ctx.baritone();
      } catch (IllegalStateException ignored) {
         return contextData;
      }
      if (baritone != null && baritone.getWorldProvider().getCurrentWorld() instanceof WorldData data) {
         return data;
      }
      return contextData;
   }

   private static boolean isInLoadedWindow(IEntityContext ctx, ChunkPos pos) {
      Set<Long> loadedWindow = loadedWindow(ctx);
      return loadedWindow == null || loadedWindow.contains(pos.toLong());
   }

   private static List<ChunkPos> nearbyChunkPositions(int centerX, int centerZ, int radius, Set<Long> loadedWindow) {
      return nearbyChunkPositions(centerX, centerZ, radius, loadedWindow, false);
   }

   private static List<ChunkPos> nearbyChunkPositions(
         int centerX, int centerZ, int radius, Set<Long> loadedWindow, boolean square) {
      if (radius < 0) {
         return Collections.emptyList();
      }

      ArrayList<ChunkPos> positions;
      if (loadedWindow != null) {
         positions = new ArrayList<>(loadedWindow.size());
         for (long packed : loadedWindow) {
            ChunkPos pos = new ChunkPos(packed);
            long dx = (long) pos.x - centerX;
            long dz = (long) pos.z - centerZ;
            if (square ? Math.max(Math.abs(dx), Math.abs(dz)) <= radius : dx * dx + dz * dz <= (long) radius * radius) {
               positions.add(pos);
            }
         }
      } else {
         positions = new ArrayList<>((radius * 2 + 1) * (radius * 2 + 1));
         for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
               if (square || (long) dx * dx + (long) dz * dz <= (long) radius * radius) {
                  positions.add(new ChunkPos(centerX + dx, centerZ + dz));
               }
            }
         }
      }
      positions.sort(Comparator
            .comparingLong((ChunkPos pos) -> {
               long dx = (long) pos.x - centerX;
               long dz = (long) pos.z - centerZ;
               return dx * dx + dz * dz;
            })
            .thenComparingInt(pos -> pos.x)
            .thenComparingInt(pos -> pos.z));
      return positions;
   }

   private static int[] coordinateIterationOrder(ServerLevel world, int playerY) {
      int playerSection = world.getSectionIndex(playerY);
      return IntStream.range(0, world.getSectionsCount())
            .boxed()
            .sorted(Comparator.comparingInt(section -> Math.abs(section - playerSection)))
            .mapToInt(Integer::intValue)
            .toArray();
   }

   private static void assertServerThread(ServerLevel world) {
      if (world.getServer() != null && !world.getServer().isSameThread()) {
         throw new IllegalStateException("World scans must start on the server thread");
      }
   }

   public static final class ScanSnapshot {
      private final int playerY;
      private final int[] coordinateIterationOrder;
      private final List<ChunkSnapshot> chunks;
      private final WorldData worldData;
      private final boolean deferred;

      private ScanSnapshot(
            int playerY,
            int[] coordinateIterationOrder,
            List<ChunkSnapshot> chunks,
            WorldData worldData,
            boolean deferred) {
         this.playerY = playerY;
         this.coordinateIterationOrder = coordinateIterationOrder.clone();
         this.chunks = chunks;
         this.worldData = worldData;
         this.deferred = deferred;
      }

      /** True when another worker owns this tick's capture slot. */
      public boolean deferred() {
         return this.deferred;
      }

      public Set<Long> publishTargetScans() {
         if (this.worldData == null) return Set.of();
         Set<Long> rejectedChunks = new HashSet<>();
         for (ChunkSnapshot chunk : this.chunks) {
            for (Map.Entry<String, Long> target : chunk.targets.entrySet()) {
               boolean accepted = this.worldData.publishTargetScan(
                     target.getKey(),
                     chunk.chunk,
                     target.getValue(),
                     chunk.observations.getOrDefault(target.getKey(), Set.of()));
               if (!accepted) rejectedChunks.add(chunk.chunk);
            }
         }
         return Set.copyOf(rejectedChunks);
      }

      public void abortTargetScans() {
         if (this.worldData == null) return;
         for (ChunkSnapshot chunk : this.chunks) {
            for (Map.Entry<String, Long> target : chunk.targets.entrySet()) {
               this.worldData.abortTargetScan(target.getKey(), chunk.chunk, target.getValue());
            }
         }
      }

      /** True when capture acquired at least one current lease for this target. */
      public boolean hasTargetScans(String target) {
         if (target == null || target.isBlank()) return false;
         return this.chunks.stream().anyMatch(chunk -> chunk.targets.containsKey(target));
      }

      public int chunkCount() {
         return this.chunks.size();
      }

      public int targetChunkCount(String target) {
         if (target == null || target.isBlank()) return 0;
         return (int)this.chunks.stream().filter(chunk -> chunk.targets.containsKey(target)).count();
      }

      public int capturedPositionCount() {
         long positions = this.chunks.stream().mapToLong(chunk -> chunk.positionsExamined).sum();
         return (int)Math.min(Integer.MAX_VALUE, positions);
      }

      public int observedPositionCount() {
         long positions = this.chunks.stream()
               .flatMap(chunk -> chunk.observations.values().stream())
               .flatMap(Set::stream)
               .distinct()
               .count();
         return (int)Math.min(Integer.MAX_VALUE, positions);
      }

      public String lastTargetChunk() {
         for (int index = this.chunks.size() - 1; index >= 0; index--) {
            ChunkSnapshot chunk = this.chunks.get(index);
            if (!chunk.targets.isEmpty()) return new ChunkPos(chunk.chunk).toString();
         }
         return "";
      }
   }

   private static final class ChunkSnapshot {
      private final long chunk;
      private final int blockX;
      private final int blockZ;
      private final int minBuildHeight;
      private final List<PalettedContainer<BlockState>> sections;
      private final Map<String, Long> targets;
      private final Map<String, Set<Long>> observations = new HashMap<>();
      private int positionsExamined;

      private ChunkSnapshot(
            long chunk,
            int blockX,
            int blockZ,
            int minBuildHeight,
            List<PalettedContainer<BlockState>> sections,
            Map<String, Long> targets) {
         this.chunk = chunk;
         this.blockX = blockX;
         this.blockZ = blockZ;
         this.minBuildHeight = minBuildHeight;
         this.sections = sections;
         this.targets = targets;
      }
   }

   private record ScanSectionResult(boolean foundWithinY, boolean stop) {
   }

   private record ScanChunkResult(boolean foundWithinY, boolean stop) {
   }

   /** Per-level FIFO arbitration prevents one early-ticking worker from starving the rest. */
   static final class CaptureBudget {
      private final ArrayDeque<UUID> waiters = new ArrayDeque<>();
      private final Set<UUID> queued = new HashSet<>();
      private final Map<UUID, Long> lastSeen = new HashMap<>();
      private long tick = Long.MIN_VALUE;
      private int used;

      int acquire(UUID owner, long currentTick) {
         if (this.tick != currentTick) {
            this.tick = currentTick;
            this.used = 0;
            while (!this.waiters.isEmpty()
                  && this.lastSeen.getOrDefault(this.waiters.peekFirst(), Long.MIN_VALUE)
                        < currentTick - CAPTURE_WAITER_STALE_TICKS) {
               UUID stale = this.waiters.removeFirst();
               this.queued.remove(stale);
               this.lastSeen.remove(stale);
            }
         }
         this.lastSeen.put(owner, currentTick);
         if (this.queued.add(owner)) this.waiters.addLast(owner);
         if (this.used >= CAPTURE_CHUNK_BUDGET || !owner.equals(this.waiters.peekFirst())) return 0;
         this.waiters.removeFirst();
         this.queued.remove(owner);
         this.lastSeen.remove(owner);
         this.used++;
         return CAPTURE_CHUNK_BUDGET;
      }
   }
}
