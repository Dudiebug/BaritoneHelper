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
import dev.dudie.baritonehelper.internal.baritone.api.cache.IContainerMemory;
import dev.dudie.baritonehelper.internal.baritone.api.cache.IWaypointCollection;
import dev.dudie.baritonehelper.internal.baritone.api.cache.IWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public class WorldData implements IWorldData {
   private final WaypointCollection waypoints = new WaypointCollection();
   private final ContainerMemory containerMemory = new ContainerMemory();
   private final CachedWorld cachedWorld = new CachedWorld();
   public final ResourceKey<Level> dimension;

   public WorldData(ResourceKey<Level> dimension) {
      this.dimension = dimension;
   }

   public void readFromNbt(HolderLookup.Provider levelRegistryAccess, CompoundTag tag) {
      this.containerMemory.read(levelRegistryAccess, tag.getCompound("containers"));
      this.waypoints.readFromNbt(tag.getCompound("waypoints"));
   }

   public void writeToNbt(HolderLookup.Provider levelRegistryAccess, CompoundTag tag) {
      tag.put("containers", this.containerMemory.toNbt(levelRegistryAccess));
      tag.put("waypoints", this.waypoints.toNbt());
   }

   @Override
   public ICachedWorld getCachedWorld() {
      return this.cachedWorld;
   }

   @Override
   public IWaypointCollection getWaypoints() {
      return this.waypoints;
   }

   @Override
   public IContainerMemory getContainerMemory() {
      return this.containerMemory;
   }

   @Override
   public void addBlockPosToCache(int blockX, int blockZ) {
      this.addChunkPosToCache(blockX >> 4, blockZ >> 4);
   }

   public void addChunkPosToCache(int chunkX, int chunkZ) {
      this.cachedWorld.markChunkCached(ChunkPos.asLong(chunkX, chunkZ));
   }

   /** Records a block found by a palette scan for the next tracked lookup. */
   public void addBlockPosToCache(String block, BlockPos position) {
      if (block != null) {
         this.cachedWorld.addLocation(block, position);
      }
   }
}
