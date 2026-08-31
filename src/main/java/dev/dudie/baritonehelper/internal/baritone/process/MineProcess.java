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

package dev.dudie.baritonehelper.internal.baritone.process;

import dev.dudie.baritonehelper.entity.WorkerEntity;
import dev.dudie.baritonehelper.internal.baritone.InternalBaritoneRuntime;
import dev.dudie.baritonehelper.internal.baritone.Baritone;
import dev.dudie.baritonehelper.internal.baritone.api.entity.LivingEntityInventory;
import dev.dudie.baritonehelper.internal.baritone.api.pathing.goals.Goal;
import dev.dudie.baritonehelper.internal.baritone.api.pathing.goals.GoalBlock;
import dev.dudie.baritonehelper.internal.baritone.api.pathing.goals.GoalComposite;
import dev.dudie.baritonehelper.internal.baritone.api.pathing.goals.GoalRunAway;
import dev.dudie.baritonehelper.internal.baritone.api.pathing.goals.GoalTwoBlocks;
import dev.dudie.baritonehelper.internal.baritone.api.process.IMineProcess;
import dev.dudie.baritonehelper.internal.baritone.api.process.PathingCommand;
import dev.dudie.baritonehelper.internal.baritone.api.process.PathingCommandType;
import dev.dudie.baritonehelper.internal.baritone.api.utils.BlockOptionalMeta;
import dev.dudie.baritonehelper.internal.baritone.api.utils.BlockOptionalMetaLookup;
import dev.dudie.baritonehelper.internal.baritone.api.utils.BlockUtils;
import dev.dudie.baritonehelper.internal.baritone.api.utils.Rotation;
import dev.dudie.baritonehelper.internal.baritone.api.utils.RotationUtils;
import dev.dudie.baritonehelper.internal.baritone.api.utils.input.Input;
import dev.dudie.baritonehelper.internal.baritone.cache.CachedChunk;
import dev.dudie.baritonehelper.internal.baritone.cache.WorldScanner;
import dev.dudie.baritonehelper.internal.baritone.pathing.movement.CalculationContext;
import dev.dudie.baritonehelper.internal.baritone.pathing.movement.MovementHelper;
import dev.dudie.baritonehelper.internal.baritone.utils.BaritoneProcessHelper;
import dev.dudie.baritonehelper.internal.baritone.utils.BlockStateInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class MineProcess extends BaritoneProcessHelper implements IMineProcess {
   private static final int ORE_LOCATIONS_COUNT = 64;
   private BlockOptionalMetaLookup filter;
   private List<BlockPos> knownOreLocations;
   private List<BlockPos> blacklist;
   private Map<BlockPos, Long> anticipatedDrops;
   private BlockPos branchPoint;
   private GoalRunAway branchPointRunaway;
   private int desiredQuantity;
   private int tickCount;
   private final Object rescanLock = new Object();
   private RescanRequest pendingRescan;
   private boolean rescanInFlight;
   private long rescanInFlightGeneration = -1L;
   private Future<?> rescanFuture;
   private long rescanGeneration;

   public MineProcess(Baritone baritone) {
      super(baritone);
   }

   @Override
   public boolean isActive() {
      return this.filter != null;
   }

   @Override
   public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
      if (this.desiredQuantity > 0) {
         LivingEntityInventory inventory = this.ctx.inventory();
         int curr = inventory == null ? -1 : inventory.main.stream().filter(stack -> this.filter.has(stack)).mapToInt(ItemStack::getCount).sum();
         InternalBaritoneRuntime.LOGGER.debug("Currently have " + curr + " valid items");
         if (curr >= this.desiredQuantity) {
            this.logDirect("Have " + curr + " valid items");
            this.cancel();
            return null;
         }
      }

      if (calcFailed) {
         if (this.knownOreLocations.isEmpty() || !this.baritone.settings().blacklistClosestOnFailure.get()) {
            this.logDirect("Unable to find any path to " + this.filter + ", canceling mine");

            this.cancel();
            return null;
         }

         this.logDirect("Unable to find any path to " + this.filter + ", blacklisting presumably unreachable closest instance...");

         this.knownOreLocations.stream().min(Comparator.comparingDouble(this.ctx.feetPos()::distSqr)).ifPresent(this.blacklist::add);
         this.knownOreLocations.removeIf(this.blacklist::contains);
      }

      if (!this.baritone.settings().allowBreak.get()) {
         this.logDirect("Unable to mine when allowBreak is false!");
         this.cancel();
         return null;
      } else {
         this.updateLoucaSystem();
         int mineGoalUpdateInterval = this.baritone.settings().mineGoalUpdateInterval.get();
         List<BlockPos> curr = new ArrayList<>(this.knownOreLocations);
         if (mineGoalUpdateInterval != 0 && this.tickCount++ % mineGoalUpdateInterval == 0) {
            this.requestRescan(curr);
         }

         if (this.baritone.settings().legitMine.get()) {
            this.addNearby();
         }

         Optional<BlockPos> shaft = curr.stream()
            .filter(pos -> pos.getX() == this.ctx.feetPos().getX() && pos.getZ() == this.ctx.feetPos().getZ())
            .filter(pos -> pos.getY() >= this.ctx.feetPos().getY())
            .filter(pos -> !(BlockStateInterface.get(this.ctx, pos).getBlock() instanceof AirBlock))
            .min(Comparator.comparingDouble(this.ctx.feetPos()::distSqr));
         this.baritone.getInputOverrideHandler().clearAllKeys();
         if (shaft.isPresent() && this.ctx.entity().onGround()) {
            BlockPos pos = shaft.get();
            BlockState state = this.baritone.bsi.get0(pos);
            if (!MovementHelper.avoidBreaking(this.baritone.bsi, pos.getX(), pos.getY(), pos.getZ(), state, this.baritone.settings())) {
               Optional<Rotation> rot = RotationUtils.reachable(this.ctx, pos);
               if (rot.isPresent() && isSafeToCancel) {
                  this.baritone.getLookBehavior().updateTarget(rot.get(), true);
                  MovementHelper.switchToBestToolFor(this.ctx, this.ctx.world().getBlockState(pos));
                  if (this.ctx.isLookingAt(pos) || this.ctx.entityRotations().isReallyCloseTo(rot.get())) {
                     this.baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                  }

                  return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
               }
            }
         }

         if (!this.baritone.settings().legitMine.get() && this.knownOreLocations.isEmpty()) {
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
         }

         PathingCommand command = this.updateGoal();
         if (command == null) {
            this.cancel();
            return null;
         } else {
            return command;
         }
      }
   }

   private void updateLoucaSystem() {
      Map<BlockPos, Long> copy = new HashMap<>(this.anticipatedDrops);
      this.ctx.getSelectedBlock().ifPresent(posx -> {
         if (this.knownOreLocations.contains(posx)) {
            copy.put(posx, System.currentTimeMillis() + this.baritone.settings().mineDropLoiterDurationMSThanksLouca.get());
         }
      });

      for (BlockPos pos : this.anticipatedDrops.keySet()) {
         if (copy.get(pos) < System.currentTimeMillis()) {
            copy.remove(pos);
         }
      }

      this.anticipatedDrops = copy;
   }

   @Override
   public void onLostControl() {
      this.mine(0, (BlockOptionalMetaLookup)null);
   }

   @Override
   public String displayName0() {
      return "Mine " + this.filter;
   }

   private PathingCommand updateGoal() {
      boolean legit = this.baritone.settings().legitMine.get();
      List<BlockPos> locs = this.knownOreLocations;
      if (locs.isEmpty()) {
         if (!legit) {
            return null;
         } else {
            int y = this.baritone.settings().legitMineYLevel.get();
            if (this.branchPoint == null) {
               this.branchPoint = this.ctx.feetPos();
            }

            if (this.branchPointRunaway == null) {
               this.branchPointRunaway = new GoalRunAway(1.0, y, this.branchPoint) {
                  @Override
                  public boolean isInGoal(int x, int yx, int z) {
                     return false;
                  }

                  @Override
                  public double heuristic() {
                     return Double.NEGATIVE_INFINITY;
                  }
               };
            }

            return new PathingCommand(this.branchPointRunaway, PathingCommandType.REVALIDATE_GOAL_AND_PATH);
         }
      } else {
         CalculationContext context = new CalculationContext(this.baritone);
         locs = prune(context, new ArrayList<>(locs), this.filter, 64, this.blacklist, this.droppedItemsScan());
         int locsSize = locs.size();
         Goal[] list = new Goal[locsSize];

         for (int i = 0; i < locsSize; i++) {
            BlockPos loc = locs.get(i);
            Goal coalesce = this.coalesce(loc, locs, context);
            list[i] = coalesce;
         }

         Goal goal = new GoalComposite(list);
         this.knownOreLocations = locs;
         return new PathingCommand(goal, legit ? PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH : PathingCommandType.REVALIDATE_GOAL_AND_PATH);
      }
   }

   private void requestRescan(List<BlockPos> already) {
      if (this.filter == null || this.baritone.settings().legitMine.get()) {
         return;
      }

      BlockPos center = this.ctx.feetPos().immutable();
      List<BlockPos> safeAlready = new ArrayList<>(already);
      List<BlockPos> safeDropped = new ArrayList<>(this.droppedItemsScan());
      if (this.ctx.entity() instanceof WorkerEntity worker) {
         safeAlready.removeIf(pos -> !worker.canModifyAt(pos));
         safeDropped.removeIf(pos -> !worker.canModifyAt(pos));
      }
      RescanRequest request = new RescanRequest(
            0L,
            this.filter,
            List.copyOf(safeAlready),
            List.copyOf(this.blacklist),
            List.copyOf(safeDropped),
            center,
            this.ctx.world().getServer());
      boolean start;
      synchronized (this.rescanLock) {
         request = new RescanRequest(
               this.rescanGeneration,
               request.filter(),
               request.already(),
               request.blacklist(),
               request.dropped(),
               request.center(),
               request.server());
         this.pendingRescan = request;
         start = !this.rescanInFlight;
         if (start) {
            this.pendingRescan = null;
            this.rescanInFlight = true;
            this.rescanInFlightGeneration = request.generation();
         }
      }
      if (start) {
         this.startRescan(request);
      }
   }

   private void startRescan(RescanRequest request) {
      try {
         CalculationContext context = new CalculationContext(this.baritone, true);
         WorldScanner.ScanSnapshot snapshot = WorldScanner.INSTANCE.capture(this.ctx, request.filter(), 32);
         if (request.server() == null) {
            this.publishRescan(request, this.computeRescan(request, context, snapshot), null);
            return;
         }
         FutureTask<Void> task = new FutureTask<>(() -> {
            this.runRescan(request, context, snapshot);
            return null;
         });
         synchronized (this.rescanLock) {
            if (!this.rescanInFlight || this.rescanInFlightGeneration != request.generation()) {
               task.cancel(false);
               return;
            }
            this.rescanFuture = task;
         }
         InternalBaritoneRuntime.getScannerExecutor().execute(task);
      } catch (Throwable error) {
         this.publishRescan(request, null, error);
      }
   }

   private void runRescan(RescanRequest request, CalculationContext context, WorldScanner.ScanSnapshot snapshot) {
      try {
         List<BlockPos> locations = this.computeRescan(request, context, snapshot);
         MinecraftServer server = request.server();
         server.execute(() -> this.publishRescan(request, locations, null));
      } catch (Throwable error) {
         MinecraftServer server = request.server();
         server.execute(() -> this.publishRescan(request, null, error));
      }
   }

   private List<BlockPos> computeRescan(RescanRequest request, CalculationContext context, WorldScanner.ScanSnapshot snapshot) {
      List<BlockPos> dropped = new ArrayList<>(request.dropped());
      List<BlockPos> locations = searchWorld(
            context,
            request.filter(),
            64,
            request.already(),
            request.blacklist(),
            dropped,
            snapshot,
            request.center());
      locations.addAll(dropped);
      return new ArrayList<>(locations);
   }

   private void publishRescan(RescanRequest request, List<BlockPos> locations, Throwable error) {
      boolean current;
      synchronized (this.rescanLock) {
         current = request.generation() == this.rescanGeneration && request.filter() == this.filter;
      }
      if (current) {
         if (error != null) {
            InternalBaritoneRuntime.LOGGER.error("Unable to rescan for " + request.filter(), error);
         } else if (locations == null || locations.isEmpty()) {
            // An unlimited server worker is a long-lived process. Keep it
            // paused and let the bounded periodic scanner discover blocks
            // added or loaded later instead of cancel/restarting every tick.
            this.knownOreLocations = new ArrayList<>();
            // A path failure blacklist is provisional. If it excludes every
            // known candidate, begin a fresh scan cycle so a transient chunk
            // edge or world change cannot make the target unreachable forever.
            this.blacklist.clear();
         } else {
            this.knownOreLocations = new ArrayList<>(locations);
         }
      }
      this.finishRescan(request);
   }

   private void finishRescan(RescanRequest completed) {
      RescanRequest next = null;
      synchronized (this.rescanLock) {
         if (!this.rescanInFlight || this.rescanInFlightGeneration != completed.generation()) {
            return;
         }
         this.rescanInFlight = false;
         this.rescanInFlightGeneration = -1L;
         this.rescanFuture = null;
         if (this.pendingRescan != null && this.filter != null && !this.baritone.settings().legitMine.get()) {
            next = this.pendingRescan;
            this.pendingRescan = null;
            this.rescanInFlight = true;
            this.rescanInFlightGeneration = next.generation();
         } else {
            this.pendingRescan = null;
         }
      }
      if (next != null) {
         this.startRescan(next);
      }
   }

   private boolean internalMiningGoal(BlockPos pos, CalculationContext context, List<BlockPos> locs) {
      if (locs.contains(pos)) {
         return true;
      } else {
         BlockState state = context.bsi.get0(pos);
         return this.baritone.settings().internalMiningAirException.get() && state.getBlock() instanceof AirBlock
            ? true
            : this.filter.has(state) && plausibleToBreak(context, pos);
      }
   }

   private Goal coalesce(BlockPos loc, List<BlockPos> locs, CalculationContext context) {
      boolean assumeVerticalShaftMine = !(this.baritone.bsi.get0(loc.above()).getBlock() instanceof FallingBlock);
      if (!this.baritone.settings().forceInternalMining.get()) {
         return (Goal)(assumeVerticalShaftMine ? new MineProcess.GoalThreeBlocks(loc) : new GoalTwoBlocks(loc));
      } else {
         boolean upwardGoal = this.internalMiningGoal(loc.above(), context, locs);
         boolean downwardGoal = this.internalMiningGoal(loc.below(), context, locs);
         boolean doubleDownwardGoal = this.internalMiningGoal(loc.below(2), context, locs);
         if (upwardGoal == downwardGoal) {
            return (Goal)(doubleDownwardGoal && assumeVerticalShaftMine ? new MineProcess.GoalThreeBlocks(loc) : new GoalTwoBlocks(loc));
         } else if (upwardGoal) {
            return new GoalBlock(loc);
         } else {
            return (Goal)(doubleDownwardGoal && assumeVerticalShaftMine ? new GoalTwoBlocks(loc.below()) : new GoalBlock(loc.below()));
         }
      }
   }

   public List<BlockPos> droppedItemsScan() {
      if (!this.baritone.settings().mineScanDroppedItems.get()) {
         return Collections.emptyList();
      } else {
         List<BlockPos> ret = new ArrayList<>();

         for (Entity entity : this.ctx.world().getAllEntities()) {
            if (entity instanceof ItemEntity ei && this.filter.has(ei.getItem())) {
               ret.add(entity.blockPosition());
            }
         }

         ret.addAll(this.anticipatedDrops.keySet());
         return ret;
      }
   }

   public static List<BlockPos> searchWorld(
      CalculationContext ctx, BlockOptionalMetaLookup filter, int max, List<BlockPos> alreadyKnown, List<BlockPos> blacklist, List<BlockPos> dropped
   ) {
      BlockPos center = ctx.safeForThreadedUse ? BlockPos.ZERO : new BlockPos(ctx.baritone.getEntityContext().feetPos());
      return searchWorld(ctx, filter, max, alreadyKnown, blacklist, dropped, null, center);
   }

   private static List<BlockPos> searchWorld(
      CalculationContext ctx,
      BlockOptionalMetaLookup filter,
      int max,
      List<BlockPos> alreadyKnown,
      List<BlockPos> blacklist,
      List<BlockPos> dropped,
      WorldScanner.ScanSnapshot snapshot,
      BlockPos center
   ) {
      List<BlockPos> locs = new ArrayList<>();
      List<Block> untracked = new ArrayList<>();

      for (BlockOptionalMeta bom : filter.blocks()) {
          Block block = bom.getBlock();
          if (CachedChunk.BLOCKS_TO_KEEP_TRACK_OF.contains(block)) {
            locs.addAll(
               ctx.worldData
                  .getCachedWorld()
                  .getLocationsOf(
                     BlockUtils.blockToString(block),
                     ctx.baritone.settings().maxCachedWorldScanCount.get(),
                     center.getX(),
                     center.getZ(),
                     2
                  )
            );
            if (!ctx.worldData.getCachedWorld().isCached(center.getX(), center.getZ())) {
               untracked.add(block);
            }
         } else {
            untracked.add(block);
         }
      }

      locs = prune(ctx, locs, filter, max, blacklist, dropped, center);
      if (!untracked.isEmpty() || ctx.baritone.settings().extendCacheOnThreshold.get() && locs.size() < max) {
         if (snapshot != null) {
            locs.addAll(WorldScanner.INSTANCE.scanSnapshot(snapshot, filter, max, 10));
         } else if (!ctx.safeForThreadedUse) {
            locs.addAll(WorldScanner.INSTANCE.scanChunkRadius(ctx.getBaritone().getEntityContext(), filter, max, 10, 32));
         }
      }

      locs.addAll(alreadyKnown);
      return prune(ctx, locs, filter, max, blacklist, dropped, center);
   }

   private void addNearby() {
      List<BlockPos> dropped = this.droppedItemsScan();
      this.knownOreLocations.addAll(dropped);
      BlockPos playerFeet = this.ctx.feetPos();
      BlockStateInterface bsi = new BlockStateInterface(this.ctx);
      int searchDist = 10;
      double fakedBlockReachDistance = 20.0;

      for (int x = playerFeet.getX() - searchDist; x <= playerFeet.getX() + searchDist; x++) {
         for (int y = playerFeet.getY() - searchDist; y <= playerFeet.getY() + searchDist; y++) {
            for (int z = playerFeet.getZ() - searchDist; z <= playerFeet.getZ() + searchDist; z++) {
               if (this.filter.has(bsi.get0(x, y, z))) {
                  BlockPos pos = new BlockPos(x, y, z);
                  if (this.baritone.settings().legitMineIncludeDiagonals.get() && this.knownOreLocations.stream().anyMatch(ore -> ore.distSqr(pos) <= 2.0)
                     || RotationUtils.reachable(this.ctx.entity(), pos, fakedBlockReachDistance).isPresent()) {
                     this.knownOreLocations.add(pos);
                  }
               }
            }
         }
      }

      this.knownOreLocations = prune(new CalculationContext(this.baritone), this.knownOreLocations, this.filter, 64, this.blacklist, dropped);
   }

   private static List<BlockPos> prune(
      CalculationContext ctx, List<BlockPos> locs2, BlockOptionalMetaLookup filter, int max, List<BlockPos> blacklist, List<BlockPos> dropped
   ) {
      BlockPos center = ctx.safeForThreadedUse ? BlockPos.ZERO : new BlockPos(ctx.getBaritone().getEntityContext().feetPos());
      return prune(ctx, locs2, filter, max, blacklist, dropped, center);
   }

   private static List<BlockPos> prune(
      CalculationContext ctx,
      List<BlockPos> locs2,
      BlockOptionalMetaLookup filter,
      int max,
      List<BlockPos> blacklist,
      List<BlockPos> dropped,
      BlockPos center
   ) {
      dropped.removeIf(drop -> {
         for (BlockPos pos : locs2) {
            if (pos.distSqr(drop) <= 9.0 && filter.has(ctx.get(pos.getX(), pos.getY(), pos.getZ())) && plausibleToBreak(ctx, pos)) {
               return true;
            }
         }

         return false;
      });
      List<BlockPos> locs = locs2.stream()
         .distinct()
         .filter(
            pos -> !ctx.bsi.worldContainsLoadedChunk(pos.getX(), pos.getZ())
               || filter.has(ctx.get(pos.getX(), pos.getY(), pos.getZ()))
               || dropped.contains(pos)
         )
         .filter(pos -> plausibleToBreak(ctx, pos))
         .filter(pos -> ctx.getBaritone().settings().allowOnlyExposedOres.get() ? isNextToAir(ctx, pos) : true)
         .filter(pos -> pos.getY() >= ctx.getBaritone().settings().minYLevelWhileMining.get())
         .filter(pos -> !blacklist.contains(pos))
         .filter(pos -> ctx.safeForThreadedUse
            || !(ctx.getBaritone().getEntityContext().entity() instanceof WorkerEntity worker)
            || worker.canModifyAt(pos))
         .sorted(Comparator.comparingDouble(center::distSqr))
         .collect(Collectors.toList());
      return locs.size() > max ? locs.subList(0, max) : locs;
   }

   public static boolean isNextToAir(CalculationContext ctx, BlockPos pos) {
      int radius = ctx.getBaritone().settings().allowOnlyExposedOresDistance.get();

      for (int dx = -radius; dx <= radius; dx++) {
         for (int dy = -radius; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
               if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) <= radius
                  && MovementHelper.isTransparent(ctx.getBlock(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz))) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   public static boolean plausibleToBreak(CalculationContext ctx, BlockPos pos) {
      return MovementHelper.getMiningDurationTicks(ctx, pos.getX(), pos.getY(), pos.getZ(), ctx.bsi.get0(pos), true) >= 1000000.0
         ? false
         : ctx.bsi.get0(pos.above()).getBlock() != Blocks.BEDROCK || ctx.bsi.get0(pos.below()).getBlock() != Blocks.BEDROCK;
   }

   @Override
   public void mineByName(int quantity, String... blocks) {
      this.mine(quantity, new BlockOptionalMetaLookup(this.baritone.getEntityContext().world(), blocks));
   }

   @Override
   public void mine(int quantity, BlockOptionalMetaLookup filter) {
      this.cancelRescan();
      this.filter = filter;
      if (filter != null && !this.baritone.settings().allowBreak.get()) {
         this.logDirect("Unable to mine when allowBreak is false!");
         this.mine(quantity, (BlockOptionalMetaLookup)null);
      } else {
         this.desiredQuantity = quantity;
         this.knownOreLocations = new ArrayList<>();
         this.blacklist = new ArrayList<>();
         this.branchPoint = null;
         this.branchPointRunaway = null;
         this.anticipatedDrops = new HashMap<>();
         if (filter != null) {
            this.requestRescan(new ArrayList<>());
         }
      }
   }

   /** Cancel only this process's scan; generation fencing rejects late callbacks. */
   public void shutdown() {
      this.cancelRescan();
   }

   private void cancelRescan() {
      Future<?> future;
      synchronized (this.rescanLock) {
         ++this.rescanGeneration;
         this.pendingRescan = null;
         this.rescanInFlight = false;
         this.rescanInFlightGeneration = -1L;
         future = this.rescanFuture;
         this.rescanFuture = null;
      }
      if (future != null) {
         future.cancel(true);
         if (future instanceof Runnable runnable) {
            InternalBaritoneRuntime.getScannerExecutor().remove(runnable);
         }
      }
   }

   @Override
   public void mine(int quantity, Block... blocks) {
      this.mine(
         quantity,
         new BlockOptionalMetaLookup(
            Stream.of(blocks).map(block -> new BlockOptionalMeta(this.baritone.getEntityContext().world(), block)).toArray(BlockOptionalMeta[]::new)
         )
      );
   }

   private record RescanRequest(
      long generation,
      BlockOptionalMetaLookup filter,
      List<BlockPos> already,
      List<BlockPos> blacklist,
      List<BlockPos> dropped,
      BlockPos center,
      MinecraftServer server
   ) {
   }

   private static class GoalThreeBlocks extends GoalTwoBlocks {
      public GoalThreeBlocks(BlockPos pos) {
         super(pos);
      }

      @Override
      public boolean isInGoal(int x, int y, int z) {
         return x == this.x && (y == this.y || y == this.y - 1 || y == this.y - 2) && z == this.z;
      }

      @Override
      public double heuristic(int x, int y, int z) {
         int xDiff = x - this.x;
         int yDiff = y - this.y;
         int zDiff = z - this.z;
         return GoalBlock.calculate(xDiff, yDiff < -1 ? yDiff + 2 : (yDiff == -1 ? 0 : yDiff), zDiff);
      }
   }
}
