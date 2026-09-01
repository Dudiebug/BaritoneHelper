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

package dev.dudie.baritonehelper.internal.baritone.utils;

import dev.dudie.baritonehelper.entity.WorkerEntity;
import dev.dudie.baritonehelper.internal.baritone.api.utils.IEntityContext;
import dev.dudie.baritonehelper.internal.baritone.utils.pathing.BetterWorldBorder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;

public class BlockStateInterface {
   private final net.minecraft.server.level.ServerChunkCache provider;
   private final Map<Long, ChunkSnapshot> snapshots;
   protected final BlockGetter world;
   public final MutableBlockPos isPassableBlockPos;
   public final BlockGetter access;
   public final BetterWorldBorder worldBorder;
   private LevelChunk prev = null;
   private static final BlockState AIR = Blocks.AIR.defaultBlockState();

   public BlockStateInterface(IEntityContext ctx) {
      this(ctx.world());
   }

   public BlockStateInterface(Level world) {
      this(world, null);
   }

   private BlockStateInterface(Level world, Set<Long> snapshotChunks) {
      this.world = world;
      this.provider = world.getChunkSource() instanceof net.minecraft.server.level.ServerChunkCache cache
            ? cache
            : null;
      this.snapshots = snapshotChunks == null ? null : snapshotChunks(snapshotChunks);
      this.isPassableBlockPos = new MutableBlockPos();
      this.access = new BlockStateInterfaceAccessWrapper(this);
      this.worldBorder = new BetterWorldBorder(world.getWorldBorder());
   }

   /** Copies ticketed chunk palettes on the server thread for async A*. */
   public static BlockStateInterface threadSafe(IEntityContext ctx) {
      Set<Long> chunks = ctx.entity() instanceof WorkerEntity worker
            ? worker.pathingTicketChunks()
            : Set.of(new ChunkPos(ctx.entity().blockPosition()).toLong());
      return new BlockStateInterface(ctx.world(), chunks);
   }

   public boolean worldContainsLoadedChunk(int blockX, int blockZ) {
      if (this.snapshots != null) {
         return this.snapshots.containsKey(ChunkPos.asLong(blockX >> 4, blockZ >> 4));
      }
      return this.getLoadedChunk(blockX >> 4, blockZ >> 4) != null;
   }

   public static Block getBlock(IEntityContext ctx, BlockPos pos) {
      return get(ctx, pos).getBlock();
   }

   public static BlockState get(IEntityContext ctx, BlockPos pos) {
      return ctx.world().getBlockState(pos);
   }

   public BlockState get0(BlockPos pos) {
      return this.get0(pos.getX(), pos.getY(), pos.getZ());
   }

   public BlockState get0(int x, int y, int z) {
      if (this.world.isOutsideBuildHeight(y)) {
         return AIR;
      } else if (this.snapshots != null) {
         ChunkSnapshot snapshot = this.snapshots.get(ChunkPos.asLong(x >> 4, z >> 4));
         return snapshot == null ? AIR : snapshot.get(this.world.getSectionIndex(y), x, y, z);
      } else {
         LevelChunk cached = this.prev;
         if (cached != null && cached.getPos().x == x >> 4 && cached.getPos().z == z >> 4) {
            return getFromChunk(this.world, cached, x, y, z);
         } else {
            LevelChunk chunk = this.getLoadedChunk(x >> 4, z >> 4);
            if (chunk != null && !chunk.isEmpty()) {
               this.prev = chunk;
               return getFromChunk(this.world, chunk, x, y, z);
            } else {
               return AIR;
            }
         }
      }
   }

   public boolean isLoaded(int x, int z) {
      if (this.snapshots != null) {
         return this.snapshots.containsKey(ChunkPos.asLong(x >> 4, z >> 4));
      }
      LevelChunk prevChunk = this.prev;
      if (prevChunk != null && prevChunk.getPos().x == x >> 4 && prevChunk.getPos().z == z >> 4) {
         return true;
      } else {
         prevChunk = this.getLoadedChunk(x >> 4, z >> 4);
         if (prevChunk != null && !prevChunk.isEmpty()) {
            this.prev = prevChunk;
            return true;
         } else {
            return false;
         }
      }
   }

   private LevelChunk getLoadedChunk(int chunkX, int chunkZ) {
      if (this.provider == null) {
         return null;
      }
      return this.provider.getChunkNow(chunkX, chunkZ);
   }

   private Map<Long, ChunkSnapshot> snapshotChunks(Set<Long> chunks) {
      if (this.provider == null) return Map.of();
      if (this.provider.getLevel().getServer() != null
            && !this.provider.getLevel().getServer().isSameThread()) {
         throw new IllegalStateException("Chunk snapshots must be created on the server thread");
      }
      java.util.HashMap<Long, ChunkSnapshot> result = new java.util.HashMap<>();
      for (long packed : chunks) {
         ChunkPos pos = new ChunkPos(packed);
         LevelChunk chunk = this.provider.getChunkNow(pos.x, pos.z);
         if (chunk == null || chunk.isEmpty()) continue;
         List<Optional<PalettedContainer<BlockState>>> sections = new ArrayList<>();
         for (LevelChunkSection section : chunk.getSections()) {
            sections.add(section.hasOnlyAir()
                  ? Optional.empty()
                  : Optional.of(section.getStates().copy()));
         }
         result.put(packed, new ChunkSnapshot(List.copyOf(sections)));
      }
      return Map.copyOf(result);
   }

   private record ChunkSnapshot(List<Optional<PalettedContainer<BlockState>>> sections) {
      BlockState get(int sectionIndex, int x, int y, int z) {
         if (sectionIndex < 0 || sectionIndex >= sections.size()) return AIR;
         return sections.get(sectionIndex)
               .map(section -> section.get(x & 15, y & 15, z & 15))
               .orElse(AIR);
      }
   }

   public static BlockState getFromChunk(BlockGetter world, ChunkAccess chunk, int x, int y, int z) {
      LevelChunkSection section = chunk.getSections()[world.getSectionIndex(y)];
      return section.hasOnlyAir() ? AIR : section.getBlockState(x & 15, y & 15, z & 15);
   }
}
