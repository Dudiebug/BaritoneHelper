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

package dev.dudie.baritonehelper.internal.baritone.pathing.calc;

import dev.dudie.baritonehelper.internal.baritone.api.pathing.goals.Goal;
import dev.dudie.baritonehelper.internal.baritone.api.utils.BetterBlockPos;
import java.util.ArrayList;
import java.util.List;

public final class PathNode {
   public final int x;
   public final int y;
   public final int z;
   public final double estimatedCostToGoal;
   public double cost;
   public double oxygenCost;
   public double combinedCost;
   public PathNode previous = null;
   public int heapPosition;

   public PathNode(int x, int y, int z, Goal goal) {
      this.cost = 1000000.0;
      this.oxygenCost = 0.0;
      this.estimatedCostToGoal = goal.heuristic(x, y, z);
      if (Double.isNaN(this.estimatedCostToGoal)) {
         throw new IllegalStateException(goal + " calculated implausible heuristic");
      } else {
         this.heapPosition = -1;
         this.x = x;
         this.y = y;
         this.z = z;
      }
   }

   /** Immutable copy of one complete previous-node chain for cross-thread progress reads. */
   static final class Snapshot {
      private final int x;
      private final int y;
      private final int z;
      private final double cost;
      private final double oxygenCost;
      private final double combinedCost;
      private final int heapPosition;
      private final Snapshot previous;

      private Snapshot(PathNode node, Snapshot previous) {
         this.x = node.x;
         this.y = node.y;
         this.z = node.z;
         this.cost = node.cost;
         this.oxygenCost = node.oxygenCost;
         this.combinedCost = node.combinedCost;
         this.heapPosition = node.heapPosition;
         this.previous = previous;
      }

      static Snapshot copyOf(PathNode end) {
         if (end == null) {
            return null;
         }

         List<PathNode> chain = new ArrayList<>();
         for (PathNode current = end; current != null; current = current.previous) {
            chain.add(current);
         }

         Snapshot previous = null;
         for (int i = chain.size() - 1; i >= 0; i--) {
            previous = new Snapshot(chain.get(i), previous);
         }
         return previous;
      }

      int x() {
         return this.x;
      }

      int y() {
         return this.y;
      }

      int z() {
         return this.z;
      }

      PathNode toPathNode(Goal goal) {
         List<Snapshot> chain = new ArrayList<>();
         for (Snapshot current = this; current != null; current = current.previous) {
            chain.add(current);
         }

         PathNode previousNode = null;
         for (int i = chain.size() - 1; i >= 0; i--) {
            Snapshot source = chain.get(i);
            PathNode node = new PathNode(source.x, source.y, source.z, goal);
            node.cost = source.cost;
            node.oxygenCost = source.oxygenCost;
            node.combinedCost = source.combinedCost;
            node.heapPosition = source.heapPosition;
            node.previous = previousNode;
            previousNode = node;
         }
         return previousNode;
      }
   }

   public boolean isOpen() {
      return this.heapPosition != -1;
   }

   @Override
   public int hashCode() {
      return (int)BetterBlockPos.longHash(this.x, this.y, this.z);
   }

   @Override
   public boolean equals(Object obj) {
      PathNode other = (PathNode)obj;
      return this.x == other.x && this.y == other.y && this.z == other.z;
   }
}
