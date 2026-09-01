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
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

/** Thread-safe locations learned from immutable chunk scans. */
public final class CachedWorld implements ICachedWorld {
   private final TargetCoverageLedger ledger;

   public CachedWorld() {
      this(new TargetCoverageLedger());
   }

   CachedWorld(TargetCoverageLedger ledger) {
      this.ledger = ledger;
   }

   @Override
   public boolean isCached(int blockX, int blockZ) {
      return this.ledger.anyScanned(ChunkPos.asLong(blockX >> 4, blockZ >> 4));
   }

   @Override
   public ArrayList<BlockPos> getLocationsOf(String block, int maximum, int centerX, int centerZ, int maxRegionDistanceSq) {
      ArrayList<BlockPos> result = new ArrayList<>();
      if (block == null || maximum <= 0 || maxRegionDistanceSq < 0) {
         return result;
      }

      var locations = this.ledger.allLocations(block);
      if (locations == null || locations.isEmpty()) {
         return result;
      }

      int centerRegionX = centerX >> 9;
      int centerRegionZ = centerZ >> 9;
      for (long packed : locations) {
         BlockPos pos = BlockPos.of(packed);
         long dx = (long) (pos.getX() >> 9) - centerRegionX;
         long dz = (long) (pos.getZ() >> 9) - centerRegionZ;
         if (dx * dx + dz * dz <= maxRegionDistanceSq) result.add(pos);
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
      // Target-aware scans use markTargetScanned; generic cache completion is intentionally ignored.
   }

   void addLocation(String block, BlockPos position) {
      if (block == null || position == null) {
         return;
      }
      this.ledger.addLocation(block, ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4), position.asLong());
   }

   void markTargetScanned(String block, long chunk) {
      this.ledger.markScanned(block, chunk);
   }

   public CoverageState coverage(String block, long chunk) {
      return this.ledger.state(block, chunk);
   }

   public int coverageCount(String block, CoverageState state) {
      return this.ledger.count(block, state);
   }

   public int indexedLocationCount(String block) {
      return this.ledger.locationCount(block);
   }

   public boolean beginScan(String block, long chunk) {
      return this.ledger.beginScan(block, chunk);
   }

   public long beginScanRevision(String block, long chunk) {
      return this.ledger.beginScanRevision(block, chunk);
   }

   public void publishScan(String block, long chunk, java.util.Collection<Long> locations) {
      this.ledger.publish(block, chunk, locations);
   }

   public boolean publishScan(
         String block, long chunk, long expectedRevision, java.util.Collection<Long> locations) {
      return this.ledger.publishIfRevision(block, chunk, expectedRevision, locations);
   }

   public void abortScan(String block, long chunk) {
      this.ledger.abortScan(block, chunk);
   }

   public void abortScan(String block, long chunk, long expectedRevision) {
      this.ledger.abortScan(block, chunk, expectedRevision);
   }

   public void markDirty(long chunk) {
      this.ledger.markDirty(chunk);
   }

   public void recordBlockChange(long chunk, long position, String beforeTarget, String afterTarget) {
      this.ledger.recordBlockChange(chunk, position, beforeTarget, afterTarget);
   }
}
