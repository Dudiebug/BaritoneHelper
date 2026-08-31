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

import dev.dudie.baritonehelper.internal.baritone.api.cache.ICachedWorld;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

/** Thread-safe locations learned from immutable chunk scans. */
public final class CachedWorld implements ICachedWorld {
   private final Set<Long> cachedChunks = ConcurrentHashMap.newKeySet();
   private final ConcurrentMap<String, Set<Long>> locationsByBlock = new ConcurrentHashMap<>();

   @Override
   public boolean isCached(int blockX, int blockZ) {
      return this.cachedChunks.contains(ChunkPos.asLong(blockX >> 4, blockZ >> 4));
   }

   @Override
   public ArrayList<BlockPos> getLocationsOf(String block, int maximum, int centerX, int centerZ, int maxRegionDistanceSq) {
      ArrayList<BlockPos> result = new ArrayList<>();
      if (block == null || maximum <= 0 || maxRegionDistanceSq < 0) {
         return result;
      }

      Set<Long> locations = this.locationsByBlock.get(block);
      if (locations == null || locations.isEmpty()) {
         return result;
      }

      long centerChunk = ChunkPos.asLong(centerX >> 4, centerZ >> 4);
      for (long packed : locations) {
         ChunkPos chunk = new ChunkPos(ChunkPos.getX(packed), ChunkPos.getZ(packed));
         if (chunk.distanceSquared(centerChunk) <= maxRegionDistanceSq) {
            result.add(BlockPos.of(packed));
         }
      }
      result.sort(Comparator
            .comparingLong((BlockPos pos) -> {
               long dx = (long) pos.getX() - centerX;
               long dz = (long) pos.getZ() - centerZ;
               return dx * dx + dz * dz;
            })
            .thenComparingLong(BlockPos::asLong));
      if (result.size() > maximum) {
         result.subList(maximum, result.size()).clear();
      }
      return result;
   }

   void markChunkCached(long chunk) {
      this.cachedChunks.add(chunk);
   }

   void addLocation(String block, BlockPos position) {
      if (block == null || position == null) {
         return;
      }
      this.locationsByBlock.computeIfAbsent(block, ignored -> ConcurrentHashMap.newKeySet()).add(position.asLong());
   }
}
