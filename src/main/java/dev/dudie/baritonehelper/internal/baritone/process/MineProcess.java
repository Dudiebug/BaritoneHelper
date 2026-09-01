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
import dev.dudie.baritonehelper.worker.SearchMode;
import dev.dudie.baritonehelper.worker.SearchTelemetry;
import dev.dudie.baritonehelper.worker.WorkerPlanner;
import dev.dudie.baritonehelper.internal.baritone.InternalBaritoneRuntime;
import dev.dudie.baritonehelper.internal.baritone.Baritone;
import dev.dudie.baritonehelper.internal.baritone.api.entity.LivingEntityInventory;
import dev.dudie.baritonehelper.internal.baritone.api.pathing.goals.Goal;
import dev.dudie.baritonehelper.internal.baritone.api.pathing.goals.GoalBlock;
import dev.dudie.baritonehelper.internal.baritone.api.pathing.goals.GoalComposite;
import dev.dudie.baritonehelper.internal.baritone.api.pathing.goals.GoalNear;
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
import dev.dudie.baritonehelper.internal.baritone.cache.CachedWorld;
import dev.dudie.baritonehelper.internal.baritone.cache.CoverageState;
import dev.dudie.baritonehelper.internal.baritone.cache.WorldData;
import dev.dudie.baritonehelper.internal.baritone.cache.WorldScanner;
import dev.dudie.baritonehelper.internal.baritone.pathing.movement.CalculationContext;
import dev.dudie.baritonehelper.internal.baritone.pathing.movement.MovementHelper;
import dev.dudie.baritonehelper.internal.baritone.utils.BaritoneProcessHelper;
import dev.dudie.baritonehelper.internal.baritone.utils.BlockStateInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class MineProcess extends BaritoneProcessHelper implements IMineProcess {
   public enum SearchOutcome {
      ACTIVE,
      NO_MATCHING_BLOCKS,
      SEARCH_AREA_UNREACHABLE
   }

   /** Inclusive mining bounds expressed as offsets from the dimension minimum Y. */
   public record MiningYRange(int minInclusive, int maxInclusive) {
      public static MiningYRange relativeTo(int dimensionMinY, int minOffset, int maxOffset) {
         return new MiningYRange(saturatedAdd(dimensionMinY, minOffset),
               saturatedAdd(dimensionMinY, maxOffset));
      }

      public boolean contains(int y) {
         return y >= this.minInclusive && y <= this.maxInclusive;
      }

      private static int saturatedAdd(int base, int offset) {
         long result = (long) base + offset;
         return result < Integer.MIN_VALUE ? Integer.MIN_VALUE
               : result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
      }
   }

   private static final int MAX_SEARCH_CANDIDATES = 4_096;
   private static final int MAX_SEARCH_TICKETS = 4;
   private static final int FRONTIER_GOAL_RANGE = 4;
   private static final int PATH_FAILURE_LIMIT = 3;
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
   private WorldScanner.ScanSnapshot rescanSnapshot;
   private long rescanGeneration;
   private List<ChunkPos> explorationFrontier = List.of();
   private int explorationFrontierIndex;
   private boolean explorationExhausted;
   private int explorationConfigurationRevision = -1;
   private final Set<Long> explorationSearchTickets = new HashSet<>();
   private String lastTerminalState = "none";
   private SearchOutcome searchOutcome = SearchOutcome.ACTIVE;
   private boolean terminalVerificationNeeded;
   private boolean hadPathFailure;
   private boolean lastGoalWasExploration;
   private final Map<BlockPos, Integer> targetPathFailures = new HashMap<>();
   private final Map<Long, Integer> explorationPathFailures = new HashMap<>();
   private long unexpectedLostControlCount;
   private String lastUnexpectedLostControl = "none";
   private String lastGenerationCaller = "none";
   private long searchStartedNanos;
   private int telemetryChunksExamined;
   private int telemetryChunksScanned;
   private int telemetryPositionsExamined;
   private int telemetryMatchingBlocks;
   private int telemetryCandidatesFound;
   private int telemetryPolicyRejects;
   private int telemetryUnreachableRejects;
   private String telemetryLastScannedChunk = "";
   private long telemetryMaxCaptureNanos;

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
            this.rememberTerminal("quantity-reached");
            this.cancel();
            return null;
         }
      }

      if (calcFailed) {
         if (this.ctx.entity() instanceof WorkerEntity worker
               && this.lastGoalWasExploration) {
            this.lastGoalWasExploration = false;
            if (this.knownOreLocations.isEmpty() && !this.rescanPending()
                  && this.explorationFrontierIndex < this.explorationFrontier.size()) {
               this.recordExplorationPathFailure(worker);
            }
         } else
         if (this.knownOreLocations.isEmpty()) {
            if (this.ctx.entity() instanceof WorkerEntity worker) {
               if (!this.rescanPending()
                     && this.explorationFrontierIndex < this.explorationFrontier.size()) {
                  this.recordExplorationPathFailure(worker);
               }
            } else if (!this.workerExplorationPending()) {
               this.logDirect("Unable to find any path to " + this.filter + ", canceling mine");
               this.rememberTerminal("calc-failed-no-frontier");
               this.cancel();
               return null;
            }
         } else if (!this.baritone.settings().blacklistClosestOnFailure.get()
               && !(this.ctx.entity() instanceof WorkerEntity)) {
            this.logDirect("Unable to find any path to " + this.filter + ", canceling mine");
            this.rememberTerminal("calc-failed-blacklist-disabled");
            this.cancel();
            return null;
         } else {
            this.hadPathFailure = true;
            BlockPos closest = this.knownOreLocations.stream()
                  .min(Comparator.comparingDouble(this.ctx.feetPos()::distSqr)).orElse(null);
            int failures = closest == null
                  ? PATH_FAILURE_LIMIT : this.targetPathFailures.merge(closest, 1, Integer::sum);
            if (!(this.ctx.entity() instanceof WorkerEntity) || failures >= PATH_FAILURE_LIMIT) {
               this.logDirect("Unable to find any path to " + this.filter + ", blacklisting presumably unreachable closest instance...");
                if (closest != null) {
                   this.blacklist.add(closest);
                   this.targetPathFailures.remove(closest);
                   this.telemetryUnreachableRejects++;
               }
               this.knownOreLocations.removeIf(this.blacklist::contains);
            }
         }
      }

      if (!this.canBreakTargets()) {
         this.logDirect("Unable to mine when allowBreak is false!");
         this.rememberTerminal("breaking-disabled");
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

         Optional<BlockPos> interactionTarget = curr.stream()
            .filter(pos -> this.filter.has(this.ctx.world().getBlockState(pos)))
            .filter(pos -> !(this.ctx.entity() instanceof WorkerEntity worker) || worker.canModifyAt(pos))
            .filter(pos -> RotationUtils.reachable(this.ctx, pos).isPresent())
            .min(Comparator.comparingDouble(this.ctx.feetPos()::distSqr));
         this.baritone.getInputOverrideHandler().clearAllKeys();
         if (interactionTarget.isPresent() && this.ctx.entity().onGround()) {
            BlockPos pos = interactionTarget.get();
            BlockState state = this.ctx.world().getBlockState(pos);
            if (this.filter.has(state)
                  && !MovementHelper.avoidBreaking(
                        this.baritone.bsi, pos.getX(), pos.getY(), pos.getZ(), state, this.baritone.settings())) {
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

         PathingCommand command = this.updateGoal();
         if (command == null) {
            if (this.terminalVerificationNeeded && !this.rescanPending()) {
               this.terminalVerificationNeeded = false;
               this.requestRescan(new ArrayList<>(this.knownOreLocations));
            }
            if (this.rescanPending()) {
               return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
            this.searchOutcome = this.hadPathFailure
                  ? SearchOutcome.SEARCH_AREA_UNREACHABLE
                  : SearchOutcome.NO_MATCHING_BLOCKS;
            this.rememberTerminal("no-goal");
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
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
      if (this.filter != null) {
         this.unexpectedLostControlCount++;
         this.lastUnexpectedLostControl = StackWalker.getInstance()
               .walk(frames -> frames.skip(1).limit(4)
                     .map(frame -> frame.getClassName() + "." + frame.getMethodName())
                     .collect(Collectors.joining(" <- ")));
      }
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
         if (this.ctx.entity() instanceof WorkerEntity worker) {
            PathingCommand exploration = this.workerExplorationGoal(worker);
            if (exploration != null) {
               this.lastGoalWasExploration = true;
               return exploration;
            }
         }

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
         if (this.ctx.entity() instanceof WorkerEntity worker) {
            List<BlockPos> sealed = locs.stream()
               .filter(pos -> context.bsi.worldContainsLoadedChunk(pos.getX(), pos.getZ()))
               .filter(pos -> this.permanentlySealedTarget(worker, pos, context))
               .toList();
            if (!sealed.isEmpty()) {
               this.blacklist.addAll(sealed);
               this.hadPathFailure = true;
               this.telemetryUnreachableRejects += sealed.size();
               locs = locs.stream().filter(pos -> !sealed.contains(pos)).toList();
            }
         }
         locs = prune(context, new ArrayList<>(locs), this.filter, this.candidateLimit(), this.blacklist, this.droppedItemsScan());
         int locsSize = locs.size();
         Goal[] list = new Goal[locsSize];

         for (int i = 0; i < locsSize; i++) {
            BlockPos loc = locs.get(i);
            Goal coalesce = this.coalesce(loc, locs, context);
            list[i] = coalesce;
         }

         Goal goal = new GoalComposite(list);
         this.lastGoalWasExploration = false;
         this.knownOreLocations = locs;
         this.targetPathFailures.keySet().retainAll(locs);
         if (this.ctx.entity() instanceof WorkerEntity worker) {
            this.releaseExplorationTickets(worker);
         }
         return new PathingCommand(goal, legit ? PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH : PathingCommandType.REVALIDATE_GOAL_AND_PATH);
      }
   }

   private void recordExplorationPathFailure(WorkerEntity worker) {
      if (this.explorationFrontierIndex >= this.explorationFrontier.size()) return;
      ChunkPos failed = this.explorationFrontier.get(this.explorationFrontierIndex);
      int failures = this.explorationPathFailures.merge(failed.toLong(), 1, Integer::sum);
      if (failures < PATH_FAILURE_LIMIT) return;
      this.explorationPathFailures.remove(failed.toLong());
      this.explorationFrontierIndex++;
      this.releaseExplorationTicket(worker, failed);
      this.hadPathFailure = true;
      this.telemetryUnreachableRejects++;
   }

   private boolean permanentlySealedTarget(
         WorkerEntity worker, BlockPos target, CalculationContext context) {
      if (WorkerPlanner.nearestWorkPosition(this.ctx.world(), worker, target).isPresent()) {
         return false;
      }
      for (Direction direction : Direction.values()) {
         BlockPos neighbor = target.relative(direction);
         BlockState state = context.bsi.get0(neighbor);
         if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
         }
         if (worker.canModifyAt(neighbor)
               && state.getDestroySpeed(this.ctx.world(), neighbor) >= 0.0F) {
            return false;
         }
      }
      return true;
   }

   /**
    * Keeps an empty worker mine process useful while target discovery is in
    * progress. WORK_AREA consumes a bounded, target-aware chunk frontier;
    * ROAM deliberately retains Baritone's long-lived run-away goal.
    */
   private PathingCommand workerExplorationGoal(WorkerEntity worker) {
      SearchMode mode = worker.configuration().searchMode();
      if (mode == SearchMode.WORK_AREA) {
         Goal goal = this.nextWorkAreaGoal(worker);
         return goal == null
            ? null
            : new PathingCommand(goal, PathingCommandType.REVALIDATE_GOAL_AND_PATH);
      }
      if (mode != SearchMode.ROAM) {
         return null;
      }

      if (this.branchPoint == null) {
         this.branchPoint = this.ctx.feetPos().immutable();
      }
      if (this.branchPointRunaway == null) {
         Integer maintainY = this.baritone.settings().exploreMaintainY.get();
         this.branchPointRunaway = maintainY == null || maintainY < 0
            ? new GoalRunAway(1.0, this.branchPoint)
            : new GoalRunAway(1.0, maintainY, this.branchPoint);
      }
      return new PathingCommand(this.branchPointRunaway, PathingCommandType.REVALIDATE_GOAL_AND_PATH);
   }

   private boolean workerExplorationPending() {
      if (!(this.ctx.entity() instanceof WorkerEntity worker)) {
         return false;
      }
      return worker.configuration().searchMode() == SearchMode.ROAM || !this.explorationExhausted;
   }

   private Goal nextWorkAreaGoal(WorkerEntity worker) {
      this.ensureExplorationFrontier(worker);
      while (this.explorationFrontierIndex < this.explorationFrontier.size()) {
         ChunkPos chunk = this.explorationFrontier.get(this.explorationFrontierIndex);
         if (this.targetChunkScanned(chunk)) {
            this.explorationPathFailures.remove(chunk.toLong());
            this.releaseExplorationTicket(worker, chunk);
            this.explorationFrontierIndex++;
            continue;
         }

         this.primeExplorationTickets(worker);
         return new GoalNear(this.frontierWaypoint(worker, chunk), FRONTIER_GOAL_RANGE);
      }

      this.explorationExhausted = true;
      this.releaseExplorationTickets(worker);
      return null;
   }

   private void ensureExplorationFrontier(WorkerEntity worker) {
      int configurationRevision = worker.configuration().revision();
      if (configurationRevision != this.explorationConfigurationRevision) {
         this.releaseExplorationTickets(worker);
         this.explorationFrontier = List.of();
         this.explorationFrontierIndex = 0;
         this.explorationExhausted = false;
         this.explorationConfigurationRevision = configurationRevision;
         this.explorationPathFailures.clear();
      }
      if (!this.explorationFrontier.isEmpty() || this.explorationExhausted) {
         return;
      }

      BlockPos center = worker.workAreaCenter();
      int radius = worker.horizontalSearchRadius();
      this.explorationFrontier = buildFrontier(center, radius, this.ctx.feetPos());
   }

   private static List<ChunkPos> buildFrontier(
         BlockPos center, int radius, BlockPos workerPosition) {
      int minChunkX = Math.floorDiv(center.getX() - radius, 16);
      int maxChunkX = Math.floorDiv(center.getX() + radius, 16);
      int minChunkZ = Math.floorDiv(center.getZ() - radius, 16);
      int maxChunkZ = Math.floorDiv(center.getZ() + radius, 16);
      List<ChunkPos> frontier = new ArrayList<>();
      for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
         for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
            if (insideWorkArea(center, radius, frontierWaypoint(center, chunk))) {
               frontier.add(chunk);
            }
         }
      }
      frontier.sort(frontierPriority(workerPosition));
      return List.copyOf(frontier);
   }

   private static Comparator<ChunkPos> frontierPriority(BlockPos workerPosition) {
      int workerChunkX = Math.floorDiv(workerPosition.getX(), 16);
      int workerChunkZ = Math.floorDiv(workerPosition.getZ(), 16);
      return Comparator.<ChunkPos>comparingLong(chunk -> {
         long dx = (long)chunk.x - workerChunkX;
         long dz = (long)chunk.z - workerChunkZ;
         return dx * dx + dz * dz;
      }).thenComparingInt(chunk -> chunk.x).thenComparingInt(chunk -> chunk.z);
   }

   private BlockPos frontierWaypoint(WorkerEntity worker, ChunkPos chunk) {
      BlockPos center = worker.workAreaCenter();
      int minY = center.getY() - worker.verticalSearchRadius();
      int maxY = center.getY() + worker.verticalSearchRadius();
      int y = Math.max(minY, Math.min(maxY, this.ctx.feetPos().getY()));
      BlockPos horizontal = this.frontierWaypoint(center, chunk);
      return new BlockPos(horizontal.getX(), y, horizontal.getZ());
   }

   private static BlockPos frontierWaypoint(BlockPos center, ChunkPos chunk) {
      int x = Math.max(chunk.getMinBlockX(), Math.min(chunk.getMaxBlockX(), center.getX()));
      int z = Math.max(chunk.getMinBlockZ(), Math.min(chunk.getMaxBlockZ(), center.getZ()));
      return new BlockPos(x, center.getY(), z);
   }

   private static boolean insideWorkArea(BlockPos center, int radius, BlockPos position) {
      long dx = (long)position.getX() - center.getX();
      long dz = (long)position.getZ() - center.getZ();
      return dx * dx + dz * dz <= (long)radius * radius;
   }

   private boolean targetChunkScanned(ChunkPos chunk) {
      if (!(this.baritone.getWorldProvider().getCurrentWorld() instanceof WorldData worldData)
            || !(worldData.getCachedWorld() instanceof CachedWorld cachedWorld)) {
         return false;
      }
      long packed = chunk.toLong();
      for (BlockOptionalMeta block : this.filter.blocks()) {
         if (cachedWorld.coverage(BlockUtils.blockToString(block.getBlock()), packed)
               != CoverageState.SCANNED) {
            return false;
         }
      }
      return true;
   }

   private void primeExplorationTickets(WorkerEntity worker) {
      int end = Math.min(this.explorationFrontier.size(),
            this.explorationFrontierIndex + MAX_SEARCH_TICKETS);
      for (int index = this.explorationFrontierIndex; index < end; index++) {
         if (this.explorationSearchTickets.size() >= MAX_SEARCH_TICKETS) {
            return;
         }
         ChunkPos chunk = this.explorationFrontier.get(index);
         long packed = chunk.toLong();
         if (this.explorationSearchTickets.contains(packed) || this.isLoaded(chunk)) {
            continue;
         }
         if (worker.requestSearchTicket(chunk)) {
            this.explorationSearchTickets.add(packed);
         }
      }
   }

   private boolean isLoaded(ChunkPos chunk) {
      return this.ctx.world() instanceof ServerLevel level
            && level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null;
   }

   private void releaseExplorationTicket(WorkerEntity worker, ChunkPos chunk) {
      if (this.explorationSearchTickets.remove(chunk.toLong())) {
         worker.releaseSearchTicket(chunk);
      }
   }

   private void releaseExplorationTickets(WorkerEntity worker) {
      for (long packed : Set.copyOf(this.explorationSearchTickets)) {
         ChunkPos chunk = new ChunkPos(packed);
         worker.releaseSearchTicket(chunk);
         this.explorationSearchTickets.remove(packed);
      }
   }

   private void requestRescan(List<BlockPos> already) {
      if (this.filter == null || this.baritone.settings().legitMine.get()
            && !(this.ctx.entity() instanceof WorkerEntity)) {
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
      WorldScanner.ScanSnapshot snapshot = null;
      long captureStarted = System.nanoTime();
      try {
         CalculationContext context = new CalculationContext(this.baritone, true);
         snapshot = WorldScanner.INSTANCE.capture(this.ctx, request.filter(), 32);
         if (snapshot.deferred()) {
            InternalBaritoneRuntime.LOGGER.debug(
                  "Deferred mine generation {} because another worker owns this tick's capture slot",
                  request.generation());
            this.finishRescan(request);
            return;
         }
         int targetLeases = 0;
         for (BlockOptionalMeta block : request.filter().blocks()) {
            targetLeases += snapshot.targetChunkCount(BlockUtils.blockToString(block.getBlock()));
         }
         long captureElapsed = Math.max(0L, System.nanoTime() - captureStarted);
         this.telemetryMaxCaptureNanos = Math.max(this.telemetryMaxCaptureNanos, captureElapsed);
         this.telemetryChunksExamined = snapshot.chunkCount();
         this.telemetryChunksScanned = targetLeases;
         this.telemetryPositionsExamined = snapshot.capturedPositionCount();
         InternalBaritoneRuntime.LOGGER.debug(
               "Captured {} chunks ({} target leases) for mine generation {} at {}",
               snapshot.chunkCount(),
               targetLeases,
               request.generation(),
               request.center());
         synchronized (this.rescanLock) {
            if (!this.rescanInFlight || this.rescanInFlightGeneration != request.generation()) {
               snapshot.abortTargetScans();
               return;
            }
            this.rescanSnapshot = snapshot;
         }
         if (request.server() == null) {
            this.publishRescan(request, snapshot, this.computeRescan(request, context, snapshot), null);
            return;
         }
         WorldScanner.ScanSnapshot captured = snapshot;
         FutureTask<Void> task = new FutureTask<>(() -> {
            this.runRescan(request, context, captured);
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
         this.publishRescan(request, snapshot, null, error);
      }
   }

   private void runRescan(RescanRequest request, CalculationContext context, WorldScanner.ScanSnapshot snapshot) {
      try {
         List<BlockPos> locations = this.computeRescan(request, context, snapshot);
         MinecraftServer server = request.server();
         server.execute(() -> this.publishRescan(request, snapshot, locations, null));
      } catch (Throwable error) {
         MinecraftServer server = request.server();
         server.execute(() -> this.publishRescan(request, snapshot, null, error));
      }
   }

   private List<BlockPos> computeRescan(RescanRequest request, CalculationContext context, WorldScanner.ScanSnapshot snapshot) {
      List<BlockPos> dropped = new ArrayList<>(request.dropped());
      List<BlockPos> locations = searchWorld(
            context,
            request.filter(),
            this.candidateLimit(),
            request.already(),
            request.blacklist(),
            dropped,
            snapshot,
            request.center());
      locations.addAll(dropped);
      InternalBaritoneRuntime.LOGGER.debug(
            "Mine generation {} produced {} candidates for {}",
            request.generation(), locations.size(), request.filter());
      return new ArrayList<>(locations);
   }

   private void publishRescan(
         RescanRequest request,
         WorldScanner.ScanSnapshot snapshot,
         List<BlockPos> locations,
         Throwable error) {
      boolean current;
      synchronized (this.rescanLock) {
         current = request.generation() == this.rescanGeneration && request.filter() == this.filter;
         if (this.rescanSnapshot == snapshot) this.rescanSnapshot = null;
      }
      Set<Long> rejectedChunks = Set.of();
      if (current) {
         if (error != null) {
            if (snapshot != null) snapshot.abortTargetScans();
            InternalBaritoneRuntime.LOGGER.error("Unable to rescan for " + request.filter(), error);
         } else {
            if (snapshot != null) {
               rejectedChunks = snapshot.publishTargetScans();
               Set<Long> rejected = rejectedChunks;
               if (locations != null) locations.removeIf(position -> rejected.contains(ChunkPos.asLong(
                     position.getX() >> 4, position.getZ() >> 4)));
            }
            if (this.ctx.entity() instanceof WorkerEntity worker && locations != null) {
               int beforePolicy = locations.size();
               locations.removeIf(pos -> !worker.canModifyAt(pos));
               this.telemetryPolicyRejects += beforePolicy - locations.size();
            }
         }
         if (error == null) {
            this.telemetryPositionsExamined = snapshot == null ? 0 : snapshot.capturedPositionCount();
            this.telemetryMatchingBlocks = snapshot == null ? 0 : snapshot.observedPositionCount();
            this.telemetryCandidatesFound = locations == null ? 0 : locations.size();
            if (snapshot != null && !snapshot.lastTargetChunk().isBlank()) {
               this.telemetryLastScannedChunk = snapshot.lastTargetChunk();
            }
         }
         if (error == null && (locations == null || locations.isEmpty())) {
            // An unlimited server worker is a long-lived process. Keep it
            // paused and let the bounded periodic scanner discover blocks
            // added or loaded later instead of cancel/restarting every tick.
            this.knownOreLocations = new ArrayList<>();
            this.targetPathFailures.clear();
            // A path failure blacklist is provisional. If it excludes every
            // known candidate, require one blacklist-free verification scan
            // before reporting a terminal result. This also prevents an
            // async cache refresh from losing a race with frontier exhaustion.
            this.hadPathFailure = !this.explorationPathFailures.isEmpty()
                  || this.hadPathFailure && snapshot != null && snapshot.observedPositionCount() > 0;
            this.terminalVerificationNeeded = !this.blacklist.isEmpty() || !rejectedChunks.isEmpty();
            this.blacklist.clear();
         } else if (error == null) {
            this.knownOreLocations = new ArrayList<>(locations);
            this.targetPathFailures.keySet().retainAll(this.knownOreLocations);
            this.terminalVerificationNeeded = false;
         }
         InternalBaritoneRuntime.LOGGER.debug(
               "Published mine generation {} with {} candidates (error={})",
               request.generation(),
               locations == null ? 0 : locations.size(),
               error == null ? "none" : error.getClass().getSimpleName());
      } else if (snapshot != null) {
         snapshot.abortTargetScans();
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
         if (this.pendingRescan != null && this.filter != null
               && (this.ctx.entity() instanceof WorkerEntity || !this.baritone.settings().legitMine.get())) {
            RescanRequest pending = this.pendingRescan;
            // The pending request may have been captured while the completed
            // scan still owned the only known candidates. Carry the freshly
            // published list forward so an older empty view cannot erase it.
            next = new RescanRequest(
                  pending.generation(),
                  pending.filter(),
                  List.copyOf(this.knownOreLocations),
                  pending.blacklist(),
                  pending.dropped(),
                  pending.center(),
                  pending.server());
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
      if (this.ctx.entity() instanceof WorkerEntity worker) {
         Optional<BlockPos> interactionPosition = WorkerPlanner.nearestWorkPosition(
               this.ctx.world(), worker, loc);
         if (interactionPosition.isPresent()) {
            return new GoalBlock(interactionPosition.get());
         }
      }
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
          String target = BlockUtils.blockToString(block);
          // The 3.2 shared index is target-aware for every block, so the
          // legacy CachedChunk whitelist must not hide restart candidates.
          locs.addAll(
             ctx.worldData
                .getCachedWorld()
                .getLocationsOf(
                   target,
                   MAX_SEARCH_CANDIDATES,
                   center.getX(),
                   center.getZ(),
                   2
                )
          );
          long centerChunk = ChunkPos.asLong(
             Math.floorDiv(center.getX(), 16), Math.floorDiv(center.getZ(), 16));
          boolean needsTargetScan = snapshot != null
             ? snapshot.hasTargetScans(target)
             : !(ctx.worldData.getCachedWorld() instanceof CachedWorld cachedWorld)
                || cachedWorld.coverage(target, centerChunk) != CoverageState.SCANNED;
          if (needsTargetScan) {
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

      this.knownOreLocations = prune(
            new CalculationContext(this.baritone),
            this.knownOreLocations,
            this.filter,
            this.candidateLimit(),
            this.blacklist,
            dropped);
      this.targetPathFailures.keySet().retainAll(this.knownOreLocations);
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
      int boundedMax = Math.max(0, Math.min(max, MAX_SEARCH_CANDIDATES));
      dropped.removeIf(drop -> {
         for (BlockPos pos : locs2) {
            if (pos.distSqr(drop) <= 9.0 && filter.has(ctx.get(pos.getX(), pos.getY(), pos.getZ())) && plausibleToBreak(ctx, pos)) {
               return true;
            }
         }

         return false;
      });
      MiningYRange miningYRange = MiningYRange.relativeTo(
            ctx.worldBottom,
            ctx.getBaritone().settings().minYLevelWhileMining.get(),
            ctx.getBaritone().settings().maxYLevelWhileMining.get());
      List<BlockPos> locs = locs2.stream()
         .distinct()
         .filter(
            pos -> !ctx.bsi.worldContainsLoadedChunk(pos.getX(), pos.getZ())
               || filter.has(ctx.get(pos.getX(), pos.getY(), pos.getZ()))
               || dropped.contains(pos)
         )
         .filter(pos -> plausibleToBreak(ctx, pos))
         .filter(pos -> ctx.getBaritone().settings().allowOnlyExposedOres.get() ? isNextToAir(ctx, pos) : true)
         .filter(pos -> miningYRange.contains(pos.getY()))
         .filter(pos -> !blacklist.contains(pos))
         .filter(pos -> ctx.safeForThreadedUse
            || !(ctx.getBaritone().getEntityContext().entity() instanceof WorkerEntity worker)
            || worker.canModifyAt(pos))
         .sorted(Comparator.comparingDouble(center::distSqr))
         .collect(Collectors.toList());
      return locs.size() > boundedMax ? locs.subList(0, boundedMax) : locs;
   }

   private boolean rescanPending() {
      synchronized (this.rescanLock) {
         return this.rescanInFlight || this.pendingRescan != null;
      }
   }

   /** Immutable, allocation-bounded state used by diagnostics and telemetry. */
   public String diagnosticState() {
      boolean scanning;
      boolean pending;
      synchronized (this.rescanLock) {
         scanning = this.rescanInFlight;
         pending = this.pendingRescan != null;
      }
      ChunkPos frontier = this.explorationFrontierIndex < this.explorationFrontier.size()
            ? this.explorationFrontier.get(this.explorationFrontierIndex) : null;
      return "generation=" + this.rescanGeneration
            + ", scanning=" + scanning
            + ", pending=" + pending
             + ", known=" + boundedSample(this.knownOreLocations)
             + ", blacklist=" + boundedSample(this.blacklist)
             + ", pathFailures=" + this.targetPathFailures
             + ", frontierPathFailures=" + this.explorationPathFailures
             + ", lastGoalExploration=" + this.lastGoalWasExploration
             + ", frontier=" + this.explorationFrontierIndex + "/" + this.explorationFrontier.size()
            + ", frontierChunk=" + frontier
            + ", exhausted=" + this.explorationExhausted
            + ", outcome=" + this.searchOutcome
            + ", lostControl=" + this.unexpectedLostControlCount
            + ":" + this.lastUnexpectedLostControl
            + ", generationCaller=" + this.lastGenerationCaller
            + ", lastTerminal=" + this.lastTerminalState;
   }

   public SearchOutcome searchOutcome() {
      return this.searchOutcome;
   }

   /** Immutable dashboard snapshot; all mutable engine state is copied here. */
   public SearchTelemetry telemetry() {
      SearchMode mode = this.ctx.entity() instanceof WorkerEntity worker
            ? worker.searchMode() : SearchMode.WORK_AREA;
      boolean pending = this.rescanPending();
      int scanned = 0;
      int dirty = 0;
      int inFlight = 0;
      int indexed = 0;
      if (this.filter != null
            && this.baritone.getWorldProvider().getCurrentWorld() instanceof WorldData worldData
            && worldData.getCachedWorld() instanceof CachedWorld cachedWorld) {
         Set<String> targets = this.filter.blocks().stream()
               .map(block -> BlockUtils.blockToString(block.getBlock()))
               .collect(Collectors.toSet());
         for (String target : targets) {
            scanned += cachedWorld.coverageCount(target, CoverageState.SCANNED);
            dirty += cachedWorld.coverageCount(target, CoverageState.DIRTY);
            inFlight += cachedWorld.coverageCount(target, CoverageState.SCANNING);
            indexed += cachedWorld.indexedLocationCount(target);
         }
      }
      ChunkPos frontier = this.explorationFrontierIndex < this.explorationFrontier.size()
            ? this.explorationFrontier.get(this.explorationFrontierIndex) : null;
      String requested = frontier == null ? "" : frontier.toString();
      boolean waiting = frontier != null && !this.targetChunkScanned(frontier) && !this.isLoaded(frontier);
      String phase;
      if (this.filter == null) phase = "IDLE";
      else if (this.searchOutcome != SearchOutcome.ACTIVE) phase = this.searchOutcome.name();
      else if (pending) phase = "SCANNING";
      else if (!this.knownOreLocations.isEmpty()) phase = "TARGETING";
      else phase = mode == SearchMode.ROAM ? "ROAMING" : "FRONTIER";
      long elapsed = this.filter == null || this.searchStartedNanos == 0L
            ? 0L : Math.max(0L, System.nanoTime() - this.searchStartedNanos);
      return new SearchTelemetry(
            phase,
            mode,
            this.rescanGeneration,
            this.telemetryChunksExamined,
            Math.max(scanned, this.telemetryChunksScanned),
            dirty,
            inFlight,
            indexed,
            this.telemetryPositionsExamined,
            this.telemetryMatchingBlocks,
            this.telemetryCandidatesFound,
            this.telemetryPolicyRejects,
            this.telemetryUnreachableRejects,
            this.knownOreLocations == null ? 0 : this.knownOreLocations.size(),
            this.explorationFrontierIndex,
            this.explorationFrontier.size(),
            waiting,
            InternalBaritoneRuntime.scannerQueueDepth(),
            this.telemetryLastScannedChunk,
            requested,
            elapsed,
            this.telemetryMaxCaptureNanos);
   }

   private void rememberTerminal(String cause) {
      this.lastTerminalState = cause
            + " known=" + boundedSample(this.knownOreLocations)
            + " blacklist=" + boundedSample(this.blacklist)
            + " frontier=" + this.explorationFrontierIndex + "/" + this.explorationFrontier.size()
            + " exhausted=" + this.explorationExhausted;
   }

   private static List<BlockPos> boundedSample(List<BlockPos> positions) {
      if (positions == null || positions.isEmpty()) return List.of();
      return List.copyOf(positions.subList(0, Math.min(positions.size(), 5)));
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
      BlockState state = ctx.bsi.get0(pos);
      boolean serverWorker = ctx.getBaritone().getEntityContext().entity() instanceof WorkerEntity;
      return !MovementHelper.avoidBreaking(
            ctx.bsi, pos.getX(), pos.getY(), pos.getZ(), state, ctx.getBaritone().settings())
         && MovementHelper.getMiningDurationTicks(ctx, pos.getX(), pos.getY(), pos.getZ(), state, true) < 1000000.0
         // Upstream prunes a target sandwiched vertically by bedrock. A server
         // worker must retain it long enough to distinguish an open side from
         // a genuinely sealed target and report SEARCH_AREA_UNREACHABLE.
         && (serverWorker
            || ctx.bsi.get0(pos.above()).getBlock() != Blocks.BEDROCK
            || ctx.bsi.get0(pos.below()).getBlock() != Blocks.BEDROCK);
   }

   @Override
   public void mineByName(int quantity, String... blocks) {
      this.mine(quantity, new BlockOptionalMetaLookup(this.baritone.getEntityContext().world(), blocks));
   }

   @Override
   public void mine(int quantity, BlockOptionalMetaLookup filter) {
      this.cancelRescan();
      this.resetExploration();
      if (filter != null && !this.baritone.settings().allowBreak.get()) {
         List<BlockOptionalMeta> allowed = filter.blocks().stream()
               .filter(target -> this.baritone.settings().allowBreakAnyway.get().contains(target.getBlock()))
               .toList();
         if (allowed.isEmpty()) {
            this.logDirect("Unable to mine when allowBreak is false and target is not in allowBreakAnyway!");
            filter = null;
         } else {
            filter = new BlockOptionalMetaLookup(allowed.toArray(BlockOptionalMeta[]::new));
         }
      }
      this.filter = filter;
      if (filter != null) {
         this.searchOutcome = SearchOutcome.ACTIVE;
         this.searchStartedNanos = System.nanoTime();
         this.telemetryChunksExamined = 0;
         this.telemetryChunksScanned = 0;
         this.telemetryPositionsExamined = 0;
         this.telemetryMatchingBlocks = 0;
         this.telemetryCandidatesFound = 0;
         this.telemetryPolicyRejects = 0;
         this.telemetryUnreachableRejects = 0;
         this.telemetryLastScannedChunk = "";
         this.telemetryMaxCaptureNanos = 0L;
         this.terminalVerificationNeeded = false;
         this.hadPathFailure = false;
         this.lastGoalWasExploration = false;
         this.targetPathFailures.clear();
      }
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

   private boolean canBreakTargets() {
      return this.baritone.settings().allowBreak.get()
            || this.filter != null && this.filter.blocks().stream().allMatch(
                  target -> this.baritone.settings().allowBreakAnyway.get().contains(target.getBlock()));
   }

   private int candidateLimit() {
      return Math.max(1, Math.min(
            MAX_SEARCH_CANDIDATES,
            this.baritone.settings().mineMaxOreLocationsCount.get()));
   }

   /** Cancel only this process's scan; generation fencing rejects late callbacks. */
   public void shutdown() {
      this.cancelRescan();
      this.resetExploration();
   }

   private void resetExploration() {
      if (this.ctx.entity() instanceof WorkerEntity worker) {
         this.releaseExplorationTickets(worker);
      }
      this.explorationFrontier = List.of();
      this.explorationFrontierIndex = 0;
      this.explorationExhausted = false;
      this.explorationConfigurationRevision = -1;
      this.explorationPathFailures.clear();
   }

   private void cancelRescan() {
      Future<?> future;
      WorldScanner.ScanSnapshot snapshot;
      synchronized (this.rescanLock) {
         this.lastGenerationCaller = StackWalker.getInstance()
               .walk(frames -> frames.skip(1).limit(4)
                     .map(frame -> frame.getClassName() + "." + frame.getMethodName())
                     .collect(Collectors.joining(" <- ")));
         ++this.rescanGeneration;
         this.pendingRescan = null;
         this.rescanInFlight = false;
         this.rescanInFlightGeneration = -1L;
         future = this.rescanFuture;
         this.rescanFuture = null;
         snapshot = this.rescanSnapshot;
         this.rescanSnapshot = null;
      }
      if (snapshot != null) snapshot.abortTargetScans();
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
