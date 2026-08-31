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

import dev.dudie.baritonehelper.entity.WorkerEntity;
import dev.dudie.baritonehelper.internal.baritone.api.IBaritone;
import dev.dudie.baritonehelper.internal.baritone.api.cache.IWorldScanner;
import dev.dudie.baritonehelper.internal.baritone.api.utils.BetterBlockPos;
import dev.dudie.baritonehelper.internal.baritone.api.utils.BlockUtils;
import dev.dudie.baritonehelper.internal.baritone.api.utils.BlockOptionalMetaLookup;
import dev.dudie.baritonehelper.internal.baritone.api.utils.IEntityContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

   @Override
   public List<BlockPos> scanChunkRadius(IEntityContext ctx, BlockOptionalMetaLookup filter, int max, int yLevelThreshold, int maxSearchRadius) {
      if (filter.blocks().isEmpty() || max <= 0 || maxSearchRadius < 0) {
         return new ArrayList<>();
      }

      ScanSnapshot snapshot = this.capture(ctx, filter, maxSearchRadius);
      return this.scanSnapshot(snapshot, filter, max, yLevelThreshold);
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

      for (ChunkPos pos : nearbyChunkPositions(playerChunkX, playerChunkZ, maxSearchRadius, loadedWindow)) {
         LevelChunk chunk = chunkSource.getChunkNow(pos.x, pos.z);
         if (chunk != null && !chunk.isEmpty()) {
            chunks.add(snapshotChunk(chunk, filter, ctx.entity() instanceof WorkerEntity worker ? worker : null));
         }
      }

      WorldData worldData = worldData(ctx);
      return new ScanSnapshot(playerPos.getY(), coordinateIterationOrder, List.copyOf(chunks), worldData);
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
            ctx.entity() instanceof WorkerEntity worker ? worker : null,
            Collections.emptySet());
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
         WorkerEntity worker,
         Set<Long> rejectedPositions
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
               worldData,
               worker,
               rejectedPositions);
         foundWithinY = scan.foundWithinY;
         if (scan.stop) {
            return true;
         }
      }

      if (worldData != null) {
         worldData.addChunkPosToCache(chunkX >> 4, chunkZ >> 4);
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
               worldData,
               null,
               chunk.rejectedPositions);
         foundWithinY = scan.foundWithinY;
         if (scan.stop) {
            return new ScanChunkResult(foundWithinY, true);
         }
      }

      if (worldData != null) {
         worldData.addChunkPosToCache(chunk.blockX >> 4, chunk.blockZ >> 4);
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
         WorldData worldData,
         WorkerEntity worker,
         Set<Long> rejectedPositions
   ) {
      int yBase = minBuildHeight + (sectionIndex << 4);
      for (int y = 0; y < 16; y++) {
         for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
               BlockState state = states.get(x, y, z);
               if (!filter.has(state)) {
                  continue;
               }

               int realY = yBase | y;
               BlockPos pos = new BlockPos(chunkX | x, realY, chunkZ | z);
               if (rejectedPositions.contains(pos.asLong()) || worker != null && !worker.canModifyAt(pos)) {
                  continue;
               }
               if (worldData != null) {
                  worldData.addBlockPosToCache(BlockUtils.blockToString(state.getBlock()), pos);
               }
               if (result.size() >= max) {
                  if (Math.abs(realY - playerY) < yLevelThreshold) {
                     foundWithinY = true;
                  } else if (foundWithinY) {
                     return new ScanSectionResult(true, true);
                  }
               }
               result.add(pos);
            }
         }
      }
      return new ScanSectionResult(foundWithinY, false);
   }

   private static ChunkSnapshot snapshotChunk(ChunkAccess chunk, BlockOptionalMetaLookup filter, WorkerEntity worker) {
      ArrayList<PalettedContainer<BlockState>> sections = new ArrayList<>(chunk.getSections().length);
      Set<Long> rejectedPositions = worker == null || filter == null ? Collections.emptySet() : new HashSet<>();
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
         if (worker != null && filter != null) {
            int yBase = chunk.getMinBuildHeight() + (sections.size() - 1 << 4);
            for (int y = 0; y < 16; y++) {
               for (int z = 0; z < 16; z++) {
                  for (int x = 0; x < 16; x++) {
                     if (filter.has(states.get(x, y, z))) {
                        BlockPos pos = new BlockPos(chunk.getPos().x << 4 | x, yBase | y, chunk.getPos().z << 4 | z);
                        if (!worker.canModifyAt(pos)) {
                           rejectedPositions.add(pos.asLong());
                        }
                     }
                  }
               }
            }
         }
      }
      return new ChunkSnapshot(
            chunk.getPos().x << 4,
            chunk.getPos().z << 4,
            chunk.getMinBuildHeight(),
            Collections.unmodifiableList(sections),
            Set.copyOf(rejectedPositions));
   }

   private static Set<Long> loadedWindow(IEntityContext ctx) {
      return ctx.entity() instanceof WorkerEntity worker ? worker.loadedTicketChunks() : null;
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

      private ScanSnapshot(int playerY, int[] coordinateIterationOrder, List<ChunkSnapshot> chunks, WorldData worldData) {
         this.playerY = playerY;
         this.coordinateIterationOrder = coordinateIterationOrder.clone();
         this.chunks = chunks;
         this.worldData = worldData;
      }
   }

   private static final class ChunkSnapshot {
      private final int blockX;
      private final int blockZ;
      private final int minBuildHeight;
      private final List<PalettedContainer<BlockState>> sections;
      private final Set<Long> rejectedPositions;

      private ChunkSnapshot(
            int blockX,
            int blockZ,
            int minBuildHeight,
            List<PalettedContainer<BlockState>> sections,
            Set<Long> rejectedPositions) {
         this.blockX = blockX;
         this.blockZ = blockZ;
         this.minBuildHeight = minBuildHeight;
         this.sections = sections;
         this.rejectedPositions = rejectedPositions;
      }
   }

   private record ScanSectionResult(boolean foundWithinY, boolean stop) {
   }

   private record ScanChunkResult(boolean foundWithinY, boolean stop) {
   }
}
