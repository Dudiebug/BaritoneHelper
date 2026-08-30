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

package dev.dudie.baritonehelper.internal.baritone.behavior;

import dev.dudie.baritonehelper.internal.baritone.Baritone;
import dev.dudie.baritonehelper.internal.baritone.api.cache.IWaypoint;
import dev.dudie.baritonehelper.internal.baritone.api.cache.Waypoint;
import dev.dudie.baritonehelper.internal.baritone.api.event.events.BlockInteractEvent;
import dev.dudie.baritonehelper.internal.baritone.api.utils.BetterBlockPos;
import dev.dudie.baritonehelper.internal.baritone.utils.BlockStateInterface;
import net.minecraft.world.level.block.BedBlock;

public final class MemoryBehavior extends Behavior {
   public MemoryBehavior(Baritone baritone) {
      super(baritone);
   }

   @Override
   public void onBlockInteract(BlockInteractEvent event) {
      if (event.getType() == BlockInteractEvent.Type.USE && BlockStateInterface.getBlock(this.ctx, event.getPos()) instanceof BedBlock) {
         this.baritone
            .getWorldProvider()
            .getCurrentWorld()
            .getWaypoints()
            .addWaypoint(new Waypoint("bed", IWaypoint.Tag.BED, BetterBlockPos.from(event.getPos())));
      }
   }
}
