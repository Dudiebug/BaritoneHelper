package dev.dudie.baritonehelper.entity;

import dev.dudie.baritonehelper.BaritoneHelper;
import dev.dudie.baritonehelper.internal.baritone.Baritone;
import dev.dudie.baritonehelper.internal.baritone.BaritoneEntity;
import dev.dudie.baritonehelper.internal.baritone.api.entity.IInteractionManagerProvider;
import dev.dudie.baritonehelper.internal.baritone.api.entity.IInventoryProvider;
import dev.dudie.baritonehelper.internal.baritone.api.entity.LivingEntityInteractionManager;
import dev.dudie.baritonehelper.internal.baritone.api.entity.LivingEntityInventory;
import dev.dudie.baritonehelper.internal.baritone.api.behavior.PathingStatus;
import dev.dudie.baritonehelper.internal.baritone.api.pathing.goals.GoalBlock;
import dev.dudie.baritonehelper.internal.baritone.utils.ToolSet;
import dev.dudie.baritonehelper.internal.baritone.utils.player.EntityContext;
import dev.dudie.baritonehelper.worker.WorkerActionResult;
import dev.dudie.baritonehelper.worker.WorkerActivity;
import dev.dudie.baritonehelper.worker.WorkerBlockReason;
import dev.dudie.baritonehelper.worker.WorkerController;
import dev.dudie.baritonehelper.worker.WorkerJob;
import dev.dudie.baritonehelper.worker.WorkerMessages;
import dev.dudie.baritonehelper.worker.WorkerJobConfiguration;
import dev.dudie.baritonehelper.worker.WorkerRuntimeState;
import dev.dudie.baritonehelper.worker.WorkerInventory;
import dev.dudie.baritonehelper.worker.WorkerStorage;
import dev.dudie.baritonehelper.worker.NoWorkZone;
import dev.dudie.baritonehelper.worker.NoWorkZoneMode;
import dev.dudie.baritonehelper.network.WorkerNetwork;
import java.util.Arrays;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.Nullable;

public final class WorkerEntity extends TamableAnimal implements Container, MenuProvider,
        BaritoneEntity, IInventoryProvider, IInteractionManagerProvider {
    public static final int BASE_SLOTS = 27;
    public static final int EXPANDED_SLOTS = 54;
    public static final int WORKER_CHUNK_RADIUS = 12;
    public static final int MAX_WORKER_TICKETS =
            (WORKER_CHUNK_RADIUS * 2 + 1) * (WORKER_CHUNK_RADIUS * 2 + 1);
    private static final int PATHING_SNAPSHOT_RADIUS = 4;

    private final NonNullList<ItemStack> items =
            NonNullList.withSize(EXPANDED_SLOTS, ItemStack.EMPTY);
    private final Set<ResourceLocation> exclusions = new LinkedHashSet<>();
    private final Set<Long> workerTicketChunks = new LinkedHashSet<>();
    private final Set<Long> simulationTicketChunks = new LinkedHashSet<>();
    private final Set<Long> searchTicketChunks = new LinkedHashSet<>();
    private final WorkerController workerController = new WorkerController(this);
    private final WorkerJobConfiguration configuration = new WorkerJobConfiguration();
    private transient @Nullable Baritone baritoneEngine;
    private transient @Nullable EntityContext baritoneContext;
    private transient @Nullable LivingEntityInventory baritoneInventory;
    private transient @Nullable LivingEntityInteractionManager interactionManager;
    private final long[] workerTickNanos = new long[200];
    private int workerTickSampleIndex;
    private int workerTickSampleCount;
    private transient boolean spawnSurfaceChecked;
    private transient boolean storageSelectionArmed;
    private transient boolean areaSelectionArmed;
    private transient @Nullable UUID zoneSelectionId;
    private final Deque<String> activityHistory = new ArrayDeque<>();
    private String resumeNote = "";
    private WorkerRuntimeState runtimeState = WorkerRuntimeState.UNCONFIGURED;
    private @Nullable BlockPos breakingPosition;
    private boolean brokeBlockThisTick;

    private int cargoUpgrades;
    private WorkerJob job = WorkerJob.IDLE;
    private WorkerBlockReason blockReason = WorkerBlockReason.NONE;
    private @Nullable ResourceLocation targetBlockId;
    private BlockPos jobOrigin = BlockPos.ZERO;
    private @Nullable BlockPos storagePosition;
    private String storageDimension = "";
    private boolean ticketsConfirmed;
    private long ticketCenter = Long.MIN_VALUE;
    private int ticketSimulationRadius = -1;
    private int configurationRevision;

    public WorkerEntity(EntityType<? extends WorkerEntity> type, Level level) {
        super(type, level);
        // Mob.serverAiStep normally lets vanilla MoveControl overwrite xxa/zza
        // immediately before travel().  While a job is active the embedded
        // engine owns those inputs, so leave the control state untouched and
        // let the next engine tick replace it with the current command.
        this.moveControl = new MoveControl(this) {
            @Override
            public void tick() {
                if (WorkerEntity.this.job.activelyWorks()) {
                    if (WorkerEntity.this.baritoneEngine == null
                            || WorkerEntity.this.baritoneEngine.getPathingBehavior().getCurrent() == null) {
                        WorkerEntity.this.xxa = 0.0F;
                        WorkerEntity.this.zza = 0.0F;
                        WorkerEntity.this.setJumping(false);
                    }
                } else {
                    super.tick();
                }
            }
        };
        // Mob.serverAiStep invokes LookControl after customServerAiStep. While
        // a job is active, Baritone has already published the exact yaw/pitch
        // needed by its reachability and click gate, so vanilla look control
        // must not replace that rotation before InputOverrideHandler uses it.
        this.lookControl = new LookControl(this) {
            @Override
            public void tick() {
                if (!WorkerEntity.this.job.activelyWorks()) {
                    super.tick();
                }
            }
        };
        setPersistenceRequired();
        setInvulnerable(true);
        // A newly placed helper has one ordinary, damageable starter tool.  It
        // is still an inventory item (and can be removed), so missing-tool
        // validation remains meaningful for cleared or resumed inventories.
        ItemStack starterPickaxe = new ItemStack(Items.IRON_PICKAXE);
        starterPickaxe.enchant(
                level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY),
                5);
        items.set(0, starterPickaxe);
        configuration.setWorkArea(level.dimension().location().toString(), blockPosition(),
                WorkerJobConfiguration.DEFAULT_HORIZONTAL_RADIUS,
                WorkerJobConfiguration.DEFAULT_VERTICAL_RADIUS);
    }

    /** Lazily creates the single server-side engine owned by this entity. */
    @Override
    public synchronized Baritone baritoneEngine() {
        if (level().isClientSide) {
            throw new IllegalStateException("Baritone Helper pathing is server-side only");
        }
        if (baritoneEngine == null) {
            baritoneContext = new EntityContext(this);
            baritoneInventory = new LivingEntityInventory(this);
            interactionManager = new LivingEntityInteractionManager(this);
            baritoneEngine = new Baritone(baritoneContext);
            baritoneEngine.settings().allowBreak.set(true);
            baritoneEngine.settings().allowPlace.set(true);
            baritoneEngine.settings().allowParkour.set(true);
            baritoneEngine.settings().allowParkourPlace.set(true);
            baritoneEngine.settings().allowSwimming.set(true);
            baritoneEngine.settings().allowWaterBucketFall.set(false);
            // Client Baritone can spend its own frame budget rescanning every
            // five ticks. A server worker shares the tick with every entity,
            // so one coalesced scan per second keeps discovery responsive
            // without four workers continuously copying chunk palettes.
            baritoneEngine.settings().mineGoalUpdateInterval.set(20);
            applyPathingSettings();
            syncBaritoneInventory();
        }
        return baritoneEngine;
    }

    public synchronized void disposeBaritoneEngine() {
        if (baritoneEngine != null) {
            cancelBreaking();
            baritoneEngine.shutdown();
        }
        baritoneEngine = null;
        baritoneContext = null;
        baritoneInventory = null;
        interactionManager = null;
        breakingPosition = null;
        releaseSearchTickets();
    }

    @Override
    public LivingEntityInventory getLivingInventory() {
        if (baritoneInventory == null && !level().isClientSide) baritoneEngine();
        return baritoneInventory == null ? new LivingEntityInventory(this) : baritoneInventory;
    }

    @Override
    public LivingEntityInteractionManager getInteractionManager() {
        if (interactionManager == null && !level().isClientSide) baritoneEngine();
        return interactionManager;
    }

    public WorkerJobConfiguration configuration() { return configuration; }
    public WorkerRuntimeState runtimeState() { return runtimeState; }
    public void setRuntimeState(WorkerRuntimeState state) {
        WorkerRuntimeState next = state == null ? WorkerRuntimeState.READY : state;
        if (runtimeState != next) {
            runtimeState = next;
            recordActivity(next.name());
        }
    }
    public List<String> activityHistory() { return List.copyOf(activityHistory); }
    public String resumeNote() { return resumeNote; }
    public void setResumeNote(String note) { resumeNote = note == null ? "" : note; }
    private void recordActivity(String event) {
        if (event == null || event.isBlank()) return;
        activityHistory.addLast(tickCount + ": " + event);
        while (activityHistory.size() > 100) activityHistory.removeFirst();
    }
    public int tickAge() { return tickCount; }
    public void markCompleted() {
        disposeBaritoneEngine();
        job = WorkerJob.COMPLETED;
        blockReason = WorkerBlockReason.NONE;
        setRuntimeState(WorkerRuntimeState.COMPLETED);
        recordActivity("COMPLETED");
        resumeNote = "Goal completed";
        setChanged();
    }
    public int requestedBlockCount() { return configuration.requestedBlockCount(); }
    public boolean unlimitedCount() { return configuration.unlimitedCount(); }
    public int completedBlockCount() { return configuration.completedBlockCount(); }
    public int workAreaHorizontalRadius() { return configuration.horizontalSearchRadius(); }
    public int workAreaVerticalRadius() { return configuration.verticalSearchRadius(); }
    public int horizontalSearchRadius() { return configuration.horizontalSearchRadius(); }
    public int verticalSearchRadius() { return configuration.verticalSearchRadius(); }
    public BlockPos workAreaCenter() { return configuration.workAreaCenter(); }
    public String workAreaDimension() { return configuration.workAreaDimension(); }
    public java.util.List<NoWorkZone> noWorkZones() { return configuration.noWorkZones(); }
    public boolean isInsideNoModify(BlockPos position) {
        return configuration.inZone(level().dimension().location().toString(), position, NoWorkZoneMode.NO_MODIFY);
    }
    public boolean isInsideNoEnter(BlockPos position) {
        return configuration.inZone(level().dimension().location().toString(), position, NoWorkZoneMode.NO_ENTER);
    }
    /** Shared guard used by both the collector and the embedded interaction adapter. */
    public boolean canModifyAt(BlockPos position) {
        if (position == null || level().isClientSide
                || isInsideNoModify(position) || isInsideNoEnter(position)
                || storagePosition().filter(position::equals).isPresent()
                || level().getBlockEntity(position) != null) return false;
        if (!workAreaDimension().isBlank()
                && !workAreaDimension().equals(level().dimension().location().toString())) return false;
        long dx = (long) position.getX() - workAreaCenter().getX();
        long dz = (long) position.getZ() - workAreaCenter().getZ();
        if (dx * dx + dz * dz > (long) horizontalSearchRadius() * horizontalSearchRadius()
                || Math.abs(position.getY() - workAreaCenter().getY()) > verticalSearchRadius()) {
            return false;
        }
        if (!level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return false;
        BlockState state = level().getBlockState(position);
        return state.getDestroySpeed(level(), position) >= 0.0F
                && !exclusions.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    public void recordBaritoneBlockBroken(BlockPos position, BlockState state) {
        if (state == null || targetBlockId == null || position == null) return;
        if (targetBlockId.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()))) {
            configuration.incrementCompleted();
            setRuntimeState(WorkerRuntimeState.COLLECTING_DROPS);
            setChanged();
        }
    }
    public void setRequestedAmount(int amount, boolean unlimited) {
        cancelForConfigurationChange();
        configuration.setRequestedAmount(amount, unlimited);
        setRuntimeState(targetBlockId == null ? WorkerRuntimeState.UNCONFIGURED : WorkerRuntimeState.READY);
        setChanged();
    }
    public void resetProgress() {
        cancelForConfigurationChange();
        configuration.resetProgress();
        setRuntimeState(targetBlockId == null ? WorkerRuntimeState.UNCONFIGURED : WorkerRuntimeState.READY);
        setChanged();
    }
    public void setWorkArea(BlockPos center, int horizontal, int vertical) {
        cancelForConfigurationChange();
        configuration.setWorkArea(level().dimension().location().toString(), center, horizontal, vertical);
        jobOrigin = center.immutable();
        setRuntimeState(targetBlockId == null ? WorkerRuntimeState.UNCONFIGURED : WorkerRuntimeState.READY);
        setChanged();
    }

    public void clearWorkArea() {
        cancelForConfigurationChange();
        configuration.clearWorkArea();
        jobOrigin = BlockPos.ZERO;
        setRuntimeState(targetBlockId == null ? WorkerRuntimeState.UNCONFIGURED : WorkerRuntimeState.READY);
        setChanged();
    }

    public boolean setPathingFlag(String key, boolean enabled) {
        var settings = configuration.pathing();
        switch (key == null ? "" : key) {
            case "breaking" -> settings.allowBreakingObstructions = enabled;
            case "placement" -> settings.allowBlockPlacement = enabled;
            case "bridging" -> settings.allowBridging = enabled;
            case "pillaring" -> settings.allowPillaring = enabled;
            case "parkour" -> settings.allowParkour = enabled;
            case "water" -> settings.allowWaterRoutes = enabled;
            case "safer" -> settings.preferSaferRoutes = enabled;
            case "avoid_destructive" -> settings.avoidDestructiveRouting = enabled;
            default -> { return false; }
        }
        configuration.touch();
        configurationRevision++;
        if (baritoneEngine != null) applyPathingSettings();
        setChanged();
        return true;
    }

    public void armStorageSelection(ServerPlayer player) {
        storageSelectionArmed = isOwnedByPlayer(player);
        areaSelectionArmed = false;
        zoneSelectionId = null;
    }

    public void armAreaSelection(ServerPlayer player) {
        areaSelectionArmed = isOwnedByPlayer(player);
        storageSelectionArmed = false;
        zoneSelectionId = null;
    }

    public void armZoneSelection(ServerPlayer player, @Nullable UUID zoneId) {
        zoneSelectionId = isOwnedByPlayer(player) ? zoneId : null;
        areaSelectionArmed = false;
        storageSelectionArmed = false;
    }

    public boolean consumeStorageSelection(ServerPlayer player) {
        boolean armed = storageSelectionArmed && isOwnedByPlayer(player);
        storageSelectionArmed = false;
        return armed;
    }

    public boolean consumeAreaSelection(ServerPlayer player) {
        boolean armed = areaSelectionArmed && isOwnedByPlayer(player);
        areaSelectionArmed = false;
        return armed;
    }

    public @Nullable UUID consumeZoneSelection(ServerPlayer player) {
        UUID selected = zoneSelectionId;
        zoneSelectionId = null;
        return selected != null && isOwnedByPlayer(player) ? selected : null;
    }
    public void addNoWorkZone(NoWorkZone zone) {
        java.util.ArrayList<NoWorkZone> zones = new java.util.ArrayList<>(configuration.noWorkZones());
        if (zones.size() < 128) zones.add(zone);
        configuration.replaceNoWorkZones(zones);
        workerController.resetTransientState();
        setChanged();
    }
    public boolean removeNoWorkZone(java.util.UUID id) {
        java.util.ArrayList<NoWorkZone> zones = new java.util.ArrayList<>(configuration.noWorkZones());
        boolean removed = zones.removeIf(zone -> zone.id().equals(id));
        if (removed) configuration.replaceNoWorkZones(zones);
        workerController.resetTransientState();
        setChanged();
        return removed;
    }

    public boolean updateNoWorkZone(
            java.util.UUID id,
            String name,
            BlockPos center,
            int horizontalRadius,
            int verticalRadius,
            NoWorkZoneMode mode,
            boolean enabled) {
        java.util.ArrayList<NoWorkZone> zones = new java.util.ArrayList<>(configuration.noWorkZones());
        for (NoWorkZone zone : zones) {
            if (zone.id().equals(id)) {
                zone.update(name, level().dimension().location().toString(), center,
                        horizontalRadius, verticalRadius, mode, enabled);
                configuration.replaceNoWorkZones(zones);
                workerController.resetTransientState();
                setChanged();
                return true;
            }
        }
        return false;
    }

    public boolean updateNoWorkZoneCenter(java.util.UUID id, BlockPos center) {
        java.util.ArrayList<NoWorkZone> zones = new java.util.ArrayList<>(configuration.noWorkZones());
        for (NoWorkZone zone : zones) {
            if (zone.id().equals(id)) {
                zone.update(zone.name(), zone.dimension(), center, zone.horizontalRadius(),
                        zone.verticalRadius(), zone.mode(), zone.enabled());
                configuration.replaceNoWorkZones(zones);
                workerController.resetTransientState();
                setChanged();
                return true;
            }
        }
        return false;
    }

    public boolean updateNoWorkZone(
            java.util.UUID id,
            BlockPos center,
            int horizontalRadius,
            int verticalRadius,
            NoWorkZoneMode mode,
            boolean enabled) {
        java.util.ArrayList<NoWorkZone> zones = new java.util.ArrayList<>(configuration.noWorkZones());
        for (NoWorkZone zone : zones) {
            if (zone.id().equals(id)) {
                zone.update(zone.name(), level().dimension().location().toString(), center,
                        horizontalRadius, verticalRadius, mode, enabled);
                configuration.replaceNoWorkZones(zones);
                workerController.resetTransientState();
                setChanged();
                return true;
            }
        }
        return false;
    }

    public boolean toggleNoWorkZone(java.util.UUID id) {
        java.util.ArrayList<NoWorkZone> zones = new java.util.ArrayList<>(configuration.noWorkZones());
        for (NoWorkZone zone : zones) {
            if (zone.id().equals(id)) {
                zone.update(zone.name(), zone.dimension(), zone.center(), zone.horizontalRadius(),
                        zone.verticalRadius(), zone.mode(), !zone.enabled());
                configuration.replaceNoWorkZones(zones);
                workerController.resetTransientState();
                setChanged();
                return true;
            }
        }
        return false;
    }

    private void cancelForConfigurationChange() {
        if (job.activelyWorks()) {
            disposeBaritoneEngine();
            job = targetBlockId == null ? WorkerJob.IDLE : WorkerJob.READY;
        }
        workerController.resetTransientState();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.1)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    public void bindTo(Player owner) {
        tame(owner);
        setOwnerUUID(owner.getUUID());
        setCustomName(Component.translatable("entity.baritonehelper.baritone_helper"));
        setCustomNameVisible(false);
    }

    public boolean isOwnedByPlayer(Player player) {
        UUID ownerId = getOwnerUUID();
        return ownerId != null && ownerId.equals(player.getUUID());
    }

    @Override
    protected void registerGoals() {
        // The worker receives movement only from WorkerController while a job is active.
    }

    @Override
    public float getSpeed() {
        return (float) getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel serverLevel)) return;
        long started = System.nanoTime();
        workerController.tick(serverLevel);
        if (baritoneEngine != null) {
            baritoneEngine.serverTick();
            // Client Baritone relies on the player's game mode to advance an
            // active block-break each tick. The server entity owns that state
            // directly, so advance its canonical interaction manager here.
            if (interactionManager != null) interactionManager.update();
        }
        workerTickNanos[workerTickSampleIndex] = System.nanoTime() - started;
        workerTickSampleIndex = (workerTickSampleIndex + 1) % workerTickNanos.length;
        workerTickSampleCount = Math.min(workerTickSampleCount + 1, workerTickNanos.length);
    }

    @Override
    public void tick() {
        // GameTest encasements place a barrier at the structure edge.  A test
        // may spawn the worker exactly in that edge block; move to the nearest
        // clear, supported cell instead of leaving the physics/path engine
        // embedded in a barrier.  No world block is modified.
        if (!spawnSurfaceChecked && job.activelyWorks()) {
            BlockPos feet = blockPosition();
            if (level().getBlockState(feet).is(Blocks.BARRIER)) {
                boolean moved = false;
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    BlockPos candidate = feet.relative(direction);
                    boolean viable = level().getBlockState(candidate).getCollisionShape(level(), candidate).isEmpty()
                            && level().getBlockState(candidate.above()).getCollisionShape(level(), candidate.above()).isEmpty()
                            && level().getBlockState(candidate.below()).isFaceSturdy(level(), candidate.below(), Direction.UP);
                    if (viable) {
                        setPos(candidate.getX() + 0.5, getY(), candidate.getZ() + 0.5);
                        moved = true;
                        break;
                    }
                }
                if (!moved && level().getBlockState(feet.above()).getCollisionShape(level(), feet.above()).isEmpty()
                        && level().getBlockState(feet.above(2)).getCollisionShape(level(), feet.above(2)).isEmpty()) {
                    setPos(getX(), getY() + 1.0, getZ());
                }
            }
            spawnSurfaceChecked = true;
        }
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        ensureWorkerTickets();
        if (job.activelyWorks()) {
            collectNearbyDrops();
        }
        if (tickCount % 20 == 0) {
            updateOwnerRecord(serverLevel);
        }
        if (tickCount % 10 == 0
                && (job.activelyWorks() || runtimeState == WorkerRuntimeState.BLOCKED)) {
            WorkerNetwork.sendStateToOwner(this);
        }
        syncBaritoneInventory();
    }

    private void syncBaritoneInventory() {
        if (baritoneInventory == null) return;
        baritoneInventory.setSelectedSlot(baritoneInventory.selectedSlot);
        ItemStack hand = baritoneInventory.getMainHandStack();
        if (getMainHandItem() != hand) setItemInHand(InteractionHand.MAIN_HAND, hand);
    }

    public void selectToolFor(BlockPos position) {
        if (baritoneInventory == null) baritoneEngine();
        ItemStack selected = getMainHandItem();
        int best = new ToolSet(this).getBestSlot(level().getBlockState(position), false);
        if (best >= 0 && best < baritoneInventory.main.size()) {
            if (best < 9) baritoneInventory.setSelectedSlot(best);
            else baritoneInventory.swapSlotWithHotbar(best);
            selected = baritoneInventory.getMainHandStack();
            setItemInHand(InteractionHand.MAIN_HAND, selected);
        }
    }

    public boolean hasCorrectToolFor(BlockPos position) {
        BlockState state = level().getBlockState(position);
        if (!state.requiresCorrectToolForDrops()) return true;
        selectToolFor(position);
        return getMainHandItem().isCorrectToolForDrops(state);
    }

    public boolean hasInventoryRoomFor(BlockState state) {
        ItemStack expected = new ItemStack(state.getBlock().asItem());
        for (int slot = 0; slot < getContainerSize(); slot++) {
            ItemStack existing = getItem(slot);
            if (existing.isEmpty() || (ItemStack.isSameItemSameComponents(existing, expected)
                    && existing.getCount() < Math.min(existing.getMaxStackSize(), getMaxStackSize()))) return true;
        }
        return false;
    }

    /**
     * Returns whether the worker is carrying collectible cargo.  Damageable or
     * tiered items are treated as tools/equipment and remain available for a
     * later job instead of forcing an otherwise empty finite goal to deposit.
     */
    public boolean hasCargo() {
        for (int slot = 0; slot < getContainerSize(); slot++) {
            ItemStack stack = getItem(slot);
            if (!stack.isEmpty() && !isReservedTool(stack)) return true;
        }
        return false;
    }

    public static boolean isReservedTool(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (stack.isDamageableItem() || stack.getItem() instanceof TieredItem);
    }

    public boolean beginOrContinueBreaking(BlockPos position) {
        if (!(level() instanceof ServerLevel serverLevel) || !canModifyAt(position)) return false;
        if (!hasCorrectToolFor(position)) return false;
        if (interactionManager == null) baritoneEngine();
        if (breakingPosition != null && breakingPosition.equals(position)
                && !interactionManager.isMining() && !level().getBlockState(position).isAir()) {
            // A rejected or interrupted finish leaves no active server mining
            // operation.  Treat that as a fresh attempt instead of retaining
            // a marker that can never make progress.
            breakingPosition = null;
        }
        if (breakingPosition == null || !breakingPosition.equals(position)) {
            cancelBreaking();
            breakingPosition = position.immutable();
            Direction face = Direction.getNearest(
                    getX() - position.getX() - 0.5,
                    getEyeY() - position.getY() - 0.5,
                    getZ() - position.getZ() - 0.5);
            interactionManager.processBlockBreakingAction(
                    position, net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                    face, serverLevel.getMaxBuildHeight(), tickCount);
        }
        interactionManager.update();
        if (interactionManager.isMining()
                && interactionManager.getBlockBreakingProgress() >= 10) {
            Direction face = Direction.getNearest(
                    getX() - position.getX() - 0.5,
                    getEyeY() - position.getY() - 0.5,
                    getZ() - position.getZ() - 0.5);
            interactionManager.processBlockBreakingAction(
                    position, net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                    face, serverLevel.getMaxBuildHeight(), tickCount);
        }
        brokeBlockThisTick = level().getBlockState(position).isAir();
        if (brokeBlockThisTick) {
            breakingPosition = null;
            return true;
        }
        return false;
    }

    public void cancelBreaking() {
        if (interactionManager != null && interactionManager.isMining()) {
            interactionManager.processBlockBreakingAction(
                    interactionManager.getMiningPos(),
                    net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                    Direction.UP, level().getMaxBuildHeight(), tickCount);
        }
        if (level() instanceof ServerLevel serverLevel && breakingPosition != null) {
            serverLevel.destroyBlockProgress(getId(), breakingPosition, -1);
        }
        breakingPosition = null;
        brokeBlockThisTick = false;
    }

    public boolean brokeBlockThisTick() { return brokeBlockThisTick; }
    public boolean isBreaking() { return breakingPosition != null; }
    public boolean interactionManagerMining() {
        return interactionManager != null && interactionManager.isMining();
    }
    public int blockBreakingProgress() {
        return interactionManager == null ? -1 : interactionManager.getBlockBreakingProgress();
    }

    private void collectNearbyDrops() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        for (net.minecraft.world.entity.item.ItemEntity item : serverLevel.getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class, getBoundingBox().inflate(2.5))) {
            if (item.isRemoved() || !item.isAlive()) continue;
            Entity dropOwner = item.getOwner();
            if (dropOwner != null && dropOwner != this) continue;
            BlockPos dropPosition = item.blockPosition();
            if (isInsideNoModify(dropPosition) || isInsideNoEnter(dropPosition)) continue;
            ItemStack stack = item.getItem();
            int before = stack.getCount();
            WorkerInventory.insertGroundStack(this, stack);
            if (stack.getCount() != before) {
                item.setItem(stack);
                item.setPickUpDelay(0);
                if (stack.isEmpty()) item.discard();
            }
        }
    }

    public boolean beginPathTo(BlockPos destination) {
        ensureWorkerTickets();
        if (level() instanceof ServerLevel serverLevel) {
            for (long packed : workerTicketChunks) {
                ChunkPos chunk = new ChunkPos(packed);
                if (serverLevel.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) {
                    return false;
                }
            }
        }
        Baritone engine = baritoneEngine();
        applyPathingSettings();
        engine.getCustomGoalProcess().setGoalAndPath(new GoalBlock(destination));
        return true;
    }

    private void applyPathingSettings() {
        if (baritoneEngine == null) return;
        var settings = configuration.pathing();
        baritoneEngine.settings().allowBreak.set(settings.allowBreakingObstructions);
        baritoneEngine.settings().allowPlace.set(settings.allowBlockPlacement);
        baritoneEngine.settings().allowParkour.set(settings.allowParkour);
        baritoneEngine.settings().allowParkourPlace.set(
                settings.allowBridging || settings.allowPillaring);
        baritoneEngine.settings().allowParkourAscend.set(settings.allowPillaring);
        baritoneEngine.settings().allowSwimming.set(settings.allowWaterRoutes);
        baritoneEngine.settings().sprintInWater.set(settings.allowWaterRoutes);
        baritoneEngine.settings().avoidUpdatingFallingBlocks.set(settings.avoidDestructiveRouting);
        baritoneEngine.settings().avoidance.set(settings.preferSaferRoutes);
    }

    public void stopEngineProcesses() {
        if (baritoneEngine != null) {
            baritoneEngine.getCustomGoalProcess().onLostControl();
            baritoneEngine.getMineProcess().onLostControl();
            baritoneEngine.getGetToBlockProcess().onLostControl();
            baritoneEngine.getPathingBehavior().forceCancel();
            baritoneEngine.getInputOverrideHandler().clearAllKeys();
        }
        cancelBreaking();
    }

    private void updateOwnerRecord(ServerLevel currentLevel) {
        UUID ownerId = getOwnerUUID();
        if (ownerId == null) {
            return;
        }

        ServerPlayer owner = currentLevel.getServer().getPlayerList().getPlayer(ownerId);
        if (owner != null) {
            owner.getData(BaritoneHelper.ACTIVE_WORKER).set(
                    getUUID(),
                    currentLevel.dimension().location().toString(),
                    blockPosition());
        }
    }

    public WorkerJob job() {
        return job;
    }

    public WorkerBlockReason blockReason() {
        return blockReason;
    }

    public WorkerActivity activity() {
        return workerController.activity();
    }

    public Optional<ResourceLocation> targetBlockId() {
        return Optional.ofNullable(targetBlockId);
    }

    public BlockPos jobOrigin() {
        return jobOrigin;
    }

    public Optional<BlockPos> storagePosition() {
        return Optional.ofNullable(storagePosition);
    }

    public String storageDimension() {
        return storageDimension;
    }

    public boolean storageIsIn(ServerLevel level) {
        return storagePosition != null
                && storageDimension.equals(level.dimension().location().toString());
    }

    public boolean isExcluded(ResourceLocation blockId) {
        return exclusions.contains(blockId);
    }

    public Set<ResourceLocation> exclusions() {
        return Set.copyOf(exclusions);
    }

    public int cargoUpgrades() {
        return cargoUpgrades;
    }

    public int configurationRevision() {
        return Math.max(configurationRevision, configuration.revision());
    }

    public int workerTicketCount() {
        return ticketsConfirmed ? workerTicketChunks.size() : 0;
    }

    public int searchTicketCount() {
        return searchTicketChunks.size();
    }

    public int simulationTicketCount() {
        return simulationTicketChunks.size();
    }

    public long workerTickP95Nanos() {
        if (workerTickSampleCount == 0) return 0L;
        long[] samples = Arrays.copyOf(workerTickNanos, workerTickSampleCount);
        Arrays.sort(samples);
        return samples[(int) Math.ceil(samples.length * 0.95D) - 1];
    }

    public int totalTicketCount() {
        return workerTicketChunks.size() + searchTicketChunks.size();
    }

    public Set<Long> loadedTicketChunks() {
        return Set.copyOf(workerTicketChunks);
    }

    /** Server-thread source set for immutable path-calculation snapshots. */
    public Set<Long> pathingTicketChunks() {
        ChunkPos center = chunkPosition();
        return Set.copyOf(WorkerChunkWindow.around(
                center.x, center.z, PATHING_SNAPSHOT_RADIUS));
    }

    public boolean workerChunkWindowReady() {
        if (!(level() instanceof ServerLevel serverLevel)
                || workerTicketChunks.size() != MAX_WORKER_TICKETS) {
            return false;
        }
        for (long packed : workerTicketChunks) {
            ChunkPos chunk = new ChunkPos(packed);
            if (serverLevel.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) return false;
        }
        return true;
    }

    public boolean ensureMineProcessStarted() {
        if (targetBlockId == null || workerTicketChunks.size() != MAX_WORKER_TICKETS) {
            if (targetBlockId != null) setRuntimeState(WorkerRuntimeState.LOADING_CHUNKS);
            return false;
        }
        Baritone engine = baritoneEngine();
        applyPathingSettings();
        if (!engine.getMineProcess().isActive()) {
            engine.getMineProcess().mine(0, BuiltInRegistries.BLOCK.get(targetBlockId));
        }
        setRuntimeState(WorkerRuntimeState.SEARCHING);
        return true;
    }

    public boolean mineProcessActive() {
        return baritoneEngine != null && baritoneEngine.getMineProcess().isActive();
    }

    public int inventoryUsedSlots() {
        int used = 0;
        for (int slot = 0; slot < getContainerSize(); slot++) {
            if (!items.get(slot).isEmpty()) {
                used++;
            }
        }
        return used;
    }

    public int inventoryItemCount() {
        int count = 0;
        for (int slot = 0; slot < getContainerSize(); slot++) {
            count += items.get(slot).getCount();
        }
        return count;
    }

    public Optional<BlockPos> currentTarget() {
        return workerController.currentTarget();
    }

    public Optional<BlockPos> currentWorkPosition() {
        return workerController.currentWorkPosition();
    }

    public int replanAttempts() {
        return workerController.replanAttempts();
    }

    public int lastProgressAgeTicks() {
        return workerController.lastProgressAgeTicks();
    }

    public int chunksExamined() {
        return workerController.chunksExamined();
    }

    public int chunksScanned() { return workerController.chunksScanned(); }
    public int positionsExamined() { return workerController.positionsExamined(); }
    public int matchingBlocks() { return workerController.matchingBlocks(); }
    public int candidatesFound() { return workerController.candidatesFound(); }
    public int candidatesRejectedByPolicy() { return workerController.candidatesRejectedByPolicy(); }
    public int candidatesRejectedAsUnreachable() { return workerController.candidatesRejectedAsUnreachable(); }
    public int cachedCandidateCount() { return workerController.cachedCandidateCount(); }
    public int frontierIndex() { return workerController.frontierIndex(); }
    public int frontierSize() { return workerController.frontierSize(); }
    public boolean waitingForSearchChunk() { return workerController.waitingForSearchChunk(); }
    public boolean pathRequested() { return workerController.pathRequested(); }
    public Optional<BlockPos> lastNavigationDestination() {
        return workerController.lastNavigationDestination();
    }
    public String lastScannedChunk() { return workerController.lastScannedChunk(); }
    public String requestedSearchChunk() { return workerController.requestedSearchChunk(); }
    public long maxSearchTickNanos() { return workerController.maxSearchTickNanos(); }

    public int currentPathNode() {
        if (baritoneEngine == null || baritoneEngine.getPathingBehavior().getCurrent() == null) return 0;
        return baritoneEngine.getPathingBehavior().getCurrent().getPosition();
    }

    public int currentPathLength() {
        if (baritoneEngine == null || baritoneEngine.getPathingBehavior().getCurrent() == null) return 0;
        return baritoneEngine.getPathingBehavior().getCurrent().getPath().length();
    }

    public double currentPathCost() {
        if (baritoneEngine == null || baritoneEngine.getPathingBehavior().getCurrent() == null) return 0.0D;
        var current = baritoneEngine.getPathingBehavior().getCurrent();
        return current.getPath().ticksRemainingFrom(Math.max(0, current.getPosition()));
    }

    public String currentPathSample() {
        if (baritoneEngine == null || baritoneEngine.getPathingBehavior().getCurrent() == null) return "";
        var positions = baritoneEngine.getPathingBehavior().getCurrent().getPath().positions();
        int end = Math.min(positions.size(), 12);
        return positions.subList(0, end).toString();
    }

    public PathingStatus pathingStatus() {
        return baritoneEngine == null
                ? PathingStatus.IDLE
                : baritoneEngine.getPathingBehavior().getStatus();
    }

    /**
     * Updates the configured target without starting work. The previous implicit
     * start behavior made it impossible to distinguish configuration from execution.
     */
    public Optional<ResourceLocation> configureTarget(ResourceLocation blockId, BlockPos origin) {
        cancelForConfigurationChange();
        Optional<ResourceLocation> previous = Optional.ofNullable(targetBlockId);
        targetBlockId = blockId;
        jobOrigin = origin.immutable();
        configuration.setTarget(blockId, origin, level().dimension().location().toString());
        if (exclusions.remove(blockId)) {
            configuration.setExcluded(blockId, false);
        }
        job = WorkerJob.READY;
        blockReason = WorkerBlockReason.NONE;
        setRuntimeState(WorkerRuntimeState.READY);
        recordActivity("TARGET_CONFIGURED:" + blockId);
        configurationRevision++;
        workerController.resetTransientState();
        setChanged();
        return previous;
    }

    /**
     * Compatibility helper for internal tests and older integrations. New player
     * controls call configureTarget and startJob separately.
     */
    public void beginCollection(ResourceLocation blockId, BlockPos origin) {
        configureTarget(blockId, origin);
        startJob();
    }

    public WorkerActionResult startJob() {
        if (targetBlockId == null) {
            job = WorkerJob.IDLE;
            blockReason = WorkerBlockReason.NO_TARGET;
            workerController.resetTransientState();
            setChanged();
            return WorkerActionResult.NO_TARGET;
        }
        if (!BuiltInRegistries.BLOCK.containsKey(targetBlockId)
                || BuiltInRegistries.BLOCK.get(targetBlockId) == Blocks.AIR) {
            markBlocked(WorkerBlockReason.NO_TARGET);
            setChanged();
            return WorkerActionResult.INVALID_CONFIGURATION;
        }
        if (!configuration.workAreaDimension().isBlank()
                && !configuration.workAreaDimension().equals(level().dimension().location().toString())) {
            markBlocked(WorkerBlockReason.WORK_AREA_WRONG_DIMENSION);
            setChanged();
            return WorkerActionResult.INVALID_CONFIGURATION;
        }
        if (exclusions.contains(targetBlockId)) {
            job = WorkerJob.READY;
            blockReason = WorkerBlockReason.TARGET_EXCLUDED;
            workerController.resetTransientState();
            setChanged();
            return WorkerActionResult.TARGET_EXCLUDED;
        }
        if (configuration.complete()) {
            job = WorkerJob.COMPLETED;
            blockReason = WorkerBlockReason.NONE;
            setRuntimeState(WorkerRuntimeState.COMPLETED);
            setChanged();
            return WorkerActionResult.ALREADY_COMPLETED;
        }
        if (job.activelyWorks()) {
            return WorkerActionResult.ALREADY_RUNNING;
        }

        job = WorkerJob.COLLECT;
        blockReason = WorkerBlockReason.NONE;
        setRuntimeState(WorkerRuntimeState.STARTING);
        recordActivity("START_REQUESTED");
        resumeNote = "Active job; recalculate path after restart";
        configurationRevision++;
        workerController.resetTransientState();
        ensureWorkerTickets();
        setChanged();
        return WorkerActionResult.STARTED;
    }

    public WorkerActionResult stopJob() {
        boolean wasRunning = job.activelyWorks() || job == WorkerJob.BLOCKED;
        setRuntimeState(WorkerRuntimeState.STOPPING);
        disposeBaritoneEngine();
        job = targetBlockId == null ? WorkerJob.IDLE : WorkerJob.READY;
        blockReason = WorkerBlockReason.NONE;
        configurationRevision++;
        workerController.resetTransientState();
        setRuntimeState(targetBlockId == null ? WorkerRuntimeState.UNCONFIGURED : WorkerRuntimeState.READY);
        recordActivity("STOP_REQUESTED");
        setChanged();
        return wasRunning ? WorkerActionResult.STOPPED : WorkerActionResult.ALREADY_STOPPED;
    }

    public WorkerActionResult clearTarget() {
        cancelForConfigurationChange();
        targetBlockId = null;
        configuration.clearTarget();
        job = WorkerJob.IDLE;
        blockReason = WorkerBlockReason.NONE;
        configurationRevision++;
        workerController.resetTransientState();
        setChanged();
        return WorkerActionResult.TARGET_CLEARED;
    }

    public void assignStorage(ServerLevel level, BlockPos position) {
        if (position == null || level != level()
                || isInsideNoEnter(position) || isInsideNoModify(position)) {
            if (level != null) notifyOwner(ChatFormatting.RED, "message.baritonehelper.storage_in_no_work_zone");
            return;
        }
        storageDimension = level.dimension().location().toString();
        storagePosition = position.immutable();
        configuration.setStorage(storageDimension, storagePosition);
        if (job == WorkerJob.BLOCKED) {
            job = targetBlockId == null ? WorkerJob.IDLE : WorkerJob.READY;
            blockReason = WorkerBlockReason.NONE;
        }
        configurationRevision++;
        workerController.resetTransientState();
        setChanged();
    }

    public boolean clearStorage() {
        boolean hadStorage = storagePosition != null || !storageDimension.isBlank();
        storagePosition = null;
        storageDimension = "";
        configuration.clearStorage();
        configurationRevision++;
        if (job == WorkerJob.DEPOSIT) {
            markBlocked(WorkerBlockReason.STORAGE_MISSING);
        } else {
            workerController.resetTransientState();
            setChanged();
        }
        return hadStorage;
    }

    public boolean toggleExclusion(ResourceLocation blockId) {
        boolean excluded;
        if (exclusions.remove(blockId)) {
            excluded = false;
        } else {
            exclusions.add(blockId);
            excluded = true;
            if (blockId.equals(targetBlockId)) {
                targetBlockId = null;
                configuration.clearTarget();
                job = WorkerJob.IDLE;
                blockReason = WorkerBlockReason.NONE;
                disposeBaritoneEngine();
            }
        }
        configuration.setExcluded(blockId, excluded);
        configurationRevision++;
        workerController.resetTransientState();
        setChanged();
        return excluded;
    }

    public void markCollecting() {
        if (targetBlockId == null) {
            job = WorkerJob.IDLE;
            blockReason = WorkerBlockReason.NO_TARGET;
            setRuntimeState(WorkerRuntimeState.UNCONFIGURED);
        } else {
            job = WorkerJob.COLLECT;
            blockReason = WorkerBlockReason.NONE;
            setRuntimeState(WorkerRuntimeState.SEARCHING);
        }
        setChanged();
    }

    public void markBlocked(WorkerBlockReason reason) {
        boolean changed = job != WorkerJob.BLOCKED || blockReason != reason;
        job = WorkerJob.BLOCKED;
        blockReason = reason;
        setRuntimeState(WorkerRuntimeState.BLOCKED);
        disposeBaritoneEngine();
        setChanged();
        if (changed) {
            recordActivity("BLOCKED:" + reason.name());
            notifyOwner(
                    ChatFormatting.RED,
                    "message.baritonehelper.blocked",
                    Component.translatable(reason.translationKey()));
        }
    }

    public void markBlocked() {
        markBlocked(WorkerBlockReason.NAVIGATION_FAILED);
    }

    public void requestDepositOrBlock() {
        if (storagePosition != null && !storageDimension.isBlank()) {
            if (level() instanceof ServerLevel level
                    && level.getBlockEntity(storagePosition) instanceof Container destination
                    && !WorkerStorage.canAcceptAny(this, destination)) {
                markBlocked(WorkerBlockReason.STORAGE_FULL);
            } else {
                job = WorkerJob.DEPOSIT;
                blockReason = WorkerBlockReason.NONE;
                setRuntimeState(WorkerRuntimeState.RETURNING_TO_STORAGE);
            }
        } else {
            markBlocked(WorkerBlockReason.INVENTORY_FULL_NO_STORAGE);
        }
        setChanged();
    }

    public void notifyOwner(
            ChatFormatting formatting,
            String translationKey,
            Object... arguments) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        UUID ownerId = getOwnerUUID();
        if (ownerId == null) {
            return;
        }
        ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(ownerId);
        if (owner != null) {
            WorkerMessages.send(owner, formatting, translationKey, arguments);
        }
    }

    @Override
    public void setLevel(Level level) {
        if (level != level()) {
            // A dimension transfer invalidates the EntityContext and every
            // world-backed path calculation.  Dispose first while the old
            // level still owns the entity's tickets and break animation.
            if (workerController != null) {
                workerController.resetTransientState();
            }
            disposeBaritoneEngine();
            releaseWorkerTickets();
        }
        super.setLevel(level);
        spawnSurfaceChecked = false;
    }

    public void openDashboard(ServerPlayer player) {
        if (!isOwnedByPlayer(player) || player.level() != level()) {
            return;
        }
        WorkerNetwork.openDashboard(player, this);
    }

    public void ensureWorkerTickets() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        ChunkPos center = chunkPosition();
        int simulationRadius = Math.min(
                WORKER_CHUNK_RADIUS,
                Math.max(0, serverLevel.getServer().getPlayerList().getSimulationDistance()));
        if (ticketsConfirmed
                && ticketCenter == center.toLong()
                && ticketSimulationRadius == simulationRadius) {
            return;
        }

        Set<Long> desired = WorkerChunkWindow.around(center.x, center.z, WORKER_CHUNK_RADIUS);
        Set<Long> desiredSimulation = WorkerChunkWindow.around(center.x, center.z, simulationRadius);
        if (!ticketsConfirmed) {
            // Controller tickets use level 31 even when forceTicks is false.
            // Keep only one persistent center anchor, which reloads the entity
            // after restart; the 625-chunk view is a runtime level-33 ticket.
            for (long oldChunk : workerTicketChunks) {
                setPersistentAnchor(serverLevel, oldChunk, false);
                setLegacyTickingTicket(serverLevel, oldChunk, false);
            }
            setPersistentAnchor(serverLevel, center.toLong(), true);
            for (long chunk : desired) {
                setViewTicket(serverLevel, chunk, true);
            }
            setSimulationRegionTicket(serverLevel, center, simulationRadius, true);
            workerTicketChunks.clear();
            workerTicketChunks.addAll(desired);
            simulationTicketChunks.clear();
            simulationTicketChunks.addAll(desiredSimulation);
            ticketsConfirmed = true;
            ticketCenter = center.toLong();
            ticketSimulationRadius = simulationRadius;
            return;
        }

        long previousCenter = ticketCenter;
        int previousSimulationRadius = ticketSimulationRadius;
        boolean centerChanged = previousCenter != center.toLong();
        boolean simulationChanged = centerChanged || previousSimulationRadius != simulationRadius;

        if (centerChanged) {
            setPersistentAnchor(serverLevel, center.toLong(), true);
        }
        reconcileViewTickets(serverLevel, workerTicketChunks, desired);
        if (simulationChanged) {
            setSimulationRegionTicket(serverLevel, center, simulationRadius, true);
            if (previousCenter != Long.MIN_VALUE) {
                setSimulationRegionTicket(
                        serverLevel,
                        new ChunkPos(previousCenter),
                        Math.max(0, previousSimulationRadius),
                        false);
            }
            simulationTicketChunks.clear();
            simulationTicketChunks.addAll(desiredSimulation);
        }
        if (centerChanged && previousCenter != Long.MIN_VALUE) {
            setPersistentAnchor(serverLevel, previousCenter, false);
        }
        ticketCenter = center.toLong();
        ticketSimulationRadius = simulationRadius;
    }

    public void releaseWorkerTickets() {
        if (level() instanceof ServerLevel serverLevel) {
            for (long chunk : Set.copyOf(workerTicketChunks)) {
                setViewTicket(serverLevel, chunk, false);
                setLegacyTickingTicket(serverLevel, chunk, false);
            }
            if (ticketCenter != Long.MIN_VALUE) {
                setSimulationRegionTicket(
                        serverLevel,
                        new ChunkPos(ticketCenter),
                        Math.max(0, ticketSimulationRadius),
                        false);
                setPersistentAnchor(serverLevel, ticketCenter, false);
            }
        }
        workerTicketChunks.clear();
        simulationTicketChunks.clear();
        ticketsConfirmed = false;
        ticketCenter = Long.MIN_VALUE;
        ticketSimulationRadius = -1;
        releaseSearchTickets();
    }

    public boolean requestSearchTicket(ChunkPos chunk) {
        if (!(level() instanceof ServerLevel serverLevel)) return false;
        long packed = chunk.toLong();
        return workerTicketChunks.contains(packed)
                && serverLevel.getChunkSource().getChunkNow(chunk.x, chunk.z) != null;
    }

    public void releaseSearchTicket(ChunkPos chunk) {
        searchTicketChunks.remove(chunk.toLong());
    }

    private void releaseSearchTickets() {
        if (level() instanceof ServerLevel serverLevel) {
            for (long packed : Set.copyOf(searchTicketChunks)) {
                ChunkPos chunk = new ChunkPos(packed);
                BaritoneHelper.SEARCH_TICKETS.forceChunk(
                        serverLevel, getUUID(), chunk.x, chunk.z, false, true);
            }
        }
        searchTicketChunks.clear();
    }

    private void reconcileViewTickets(ServerLevel level, Set<Long> current, Set<Long> desired) {
        // Add the entering strip before dropping the leaving strip so a moving
        // worker never loses its loaded route between adjacent windows.
        for (long chunk : desired) {
            if (current.add(chunk)) {
                setViewTicket(level, chunk, true);
            }
        }
        for (long oldChunk : Set.copyOf(current)) {
            if (!desired.contains(oldChunk)) {
                setViewTicket(level, oldChunk, false);
                current.remove(oldChunk);
            }
        }
    }

    private void setPersistentAnchor(ServerLevel level, long packedChunk, boolean add) {
        ChunkPos chunk = new ChunkPos(packedChunk);
        BaritoneHelper.WORKER_TICKETS.forceChunk(
                level,
                getUUID(),
                chunk.x,
                chunk.z,
                add,
                false);
    }

    private void setViewTicket(ServerLevel level, long packedChunk, boolean add) {
        ChunkPos chunk = new ChunkPos(packedChunk);
        int fullLevel = ChunkLevel.byStatus(FullChunkStatus.FULL);
        if (add) {
            level.getChunkSource().chunkMap.getDistanceManager().addTicket(
                    BaritoneHelper.WORKER_VIEW_TICKET, chunk, fullLevel, getUUID());
        } else {
            level.getChunkSource().chunkMap.getDistanceManager().removeTicket(
                    BaritoneHelper.WORKER_VIEW_TICKET, chunk, fullLevel, getUUID());
        }
    }

    private void setSimulationRegionTicket(
            ServerLevel level, ChunkPos center, int radius, boolean add) {
        int distance = 2 + radius;
        if (add) {
            level.getChunkSource().addRegionTicket(
                    BaritoneHelper.WORKER_SIMULATION_TICKET,
                    center,
                    distance,
                    getUUID(),
                    false);
        } else {
            level.getChunkSource().removeRegionTicket(
                    BaritoneHelper.WORKER_SIMULATION_TICKET,
                    center,
                    distance,
                    getUUID(),
                    false);
        }
    }

    private void setLegacyTickingTicket(ServerLevel level, long packedChunk, boolean add) {
        ChunkPos chunk = new ChunkPos(packedChunk);
        BaritoneHelper.WORKER_TICKETS.forceChunk(
                level, getUUID(), chunk.x, chunk.z, add, true);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return false;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return false;
    }

    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isOwnedByPlayer(player)) {
            return InteractionResult.PASS;
        }

        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown()) {
            dismiss(player);
            return InteractionResult.SUCCESS;
        }

        ItemStack held = player.getItemInHand(hand);
        if (held.is(BaritoneHelper.CARGO_UPGRADE.get())) {
            if (cargoUpgrades == 0) {
                cargoUpgrades = 1;
                held.consume(1, player);
                setChanged();
                if (player instanceof ServerPlayer serverPlayer) {
                    WorkerMessages.send(
                            serverPlayer,
                            ChatFormatting.GREEN,
                            "message.baritonehelper.cargo_installed");
                }
            } else if (player instanceof ServerPlayer serverPlayer) {
                WorkerMessages.send(
                        serverPlayer,
                        ChatFormatting.YELLOW,
                        "message.baritonehelper.cargo_max");
            }
            return InteractionResult.SUCCESS;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            if (held.is(BaritoneHelper.WORKER_CONTROLLER.get())) {
                openDashboard(serverPlayer);
            } else if (held.isEmpty()) {
                serverPlayer.openMenu(this);
            } else {
                return InteractionResult.PASS;
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    private void dismiss(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.getData(BaritoneHelper.ACTIVE_WORKER).clear();
            WorkerMessages.send(
                    serverPlayer,
                    ChatFormatting.GREEN,
                    "message.baritonehelper.dismissed");
        }

        stopJob();
        releaseInventoryOnce();
        ItemStack helperItem = new ItemStack(BaritoneHelper.BARITONE_HELPER.get());
        if (!player.getInventory().add(helperItem)) {
            player.drop(helperItem, false);
        }

        releaseWorkerTickets();
        discard();
        gameEvent(GameEvent.ENTITY_DISMOUNT, player);
    }

    private void releaseInventoryOnce() {
        for (int slot = 0; slot < EXPANDED_SLOTS; slot++) {
            ItemStack stack = items.get(slot);
            if (!stack.isEmpty()) {
                spawnAtLocation(stack.copy());
                items.set(slot, ItemStack.EMPTY);
            }
        }
        setChanged();
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        disposeBaritoneEngine();
        if (reason.shouldDestroy()) releaseWorkerTickets();
        super.remove(reason);
    }

    @Override
    public int getContainerSize() {
        return cargoUpgrades > 0 ? EXPANDED_SLOTS : BASE_SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (int slot = 0; slot < getContainerSize(); slot++) {
            if (!items.get(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < getContainerSize()
                ? items.get(slot)
                : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot < 0 || slot >= getContainerSize() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = items.get(slot).split(amount);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= getContainerSize()) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = items.get(slot);
        items.set(slot, ItemStack.EMPTY);
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= getContainerSize()) {
            return;
        }
        int limit = Math.min(getMaxStackSize(), stack.getMaxStackSize());
        if (stack.getCount() > limit) {
            stack = stack.copyWithCount(limit);
        }
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public void setChanged() {
        // Entity NBT is saved by the normal entity persistence lifecycle.
    }

    @Override
    public boolean stillValid(Player player) {
        return canOpenInventory(player);
    }

    public boolean canOpenInventory(Player player) {
        return isAlive() && isOwnedByPlayer(player) && player.level() == level();
    }

    @Override
    public void clearContent() {
        for (int slot = 0; slot < EXPANDED_SLOTS; slot++) {
            items.set(slot, ItemStack.EMPTY);
        }
        setChanged();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.baritonehelper.baritone_helper");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(
            int containerId,
            Inventory playerInventory,
            Player player) {
        if (!canOpenInventory(player)) {
            return null;
        }
        return cargoUpgrades > 0
                ? ChestMenu.sixRows(containerId, playerInventory, this)
                : ChestMenu.threeRows(containerId, playerInventory, this);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ContainerHelper.saveAllItems(tag, items, level().registryAccess());
        tag.putInt("BaritoneHelperSchema", 3);
        tag.put("WorkerJobConfiguration", configuration.save());
        tag.putInt("CargoUpgrades", cargoUpgrades);
        tag.putString("WorkerJob", job.name());
        tag.putString("BlockReason", blockReason.name());
        tag.putInt("ConfigurationRevision", configurationRevision);
        tag.putString("RuntimeState", runtimeState.name());
        tag.putString("ResumeNote", resumeNote);
        ListTag history = new ListTag();
        activityHistory.forEach(event -> history.add(StringTag.valueOf(event)));
        tag.put("ActivityHistory", history);
        if (targetBlockId != null) {
            tag.putString("TargetBlock", targetBlockId.toString());
        }
        tag.putLong("JobOrigin", jobOrigin.asLong());
        if (storagePosition != null) {
            tag.putLong("StoragePosition", storagePosition.asLong());
            tag.putString("StorageDimension", storageDimension);
        }

        ListTag excluded = new ListTag();
        exclusions.forEach(id -> excluded.add(StringTag.valueOf(id.toString())));
        tag.put("ExcludedBlocks", excluded);
        tag.putLongArray(
                "WorkerTicketChunks",
                workerTicketChunks.stream().mapToLong(Long::longValue).toArray());
        tag.putLongArray(
                "WorkerSimulationTicketChunks",
                simulationTicketChunks.stream().mapToLong(Long::longValue).toArray());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setInvulnerable(true);
        clearContent();
        ContainerHelper.loadAllItems(tag, items, level().registryAccess());

        cargoUpgrades = Math.max(0, Math.min(1, tag.getInt("CargoUpgrades")));
        job = WorkerJob.fromSerialized(tag.getString("WorkerJob"));
        blockReason = WorkerBlockReason.fromSerialized(tag.getString("BlockReason"));
        configurationRevision = Math.max(0, tag.getInt("ConfigurationRevision"));
        resumeNote = tag.getString("ResumeNote");
        activityHistory.clear();
        ListTag history = tag.getList("ActivityHistory", Tag.TAG_STRING);
        for (int index = Math.max(0, history.size() - 100); index < history.size(); index++) {
            activityHistory.addLast(history.getString(index));
        }
        runtimeState = WorkerRuntimeState.fromSerialized(tag.getString("RuntimeState"));

        targetBlockId = tag.contains("TargetBlock", Tag.TAG_STRING)
                ? ResourceLocation.tryParse(tag.getString("TargetBlock"))
                : null;
        jobOrigin = tag.contains("JobOrigin", Tag.TAG_LONG)
                ? BlockPos.of(tag.getLong("JobOrigin"))
                : blockPosition();

        storagePosition = tag.contains("StoragePosition", Tag.TAG_LONG)
                ? BlockPos.of(tag.getLong("StoragePosition"))
                : null;
        storageDimension = tag.getString("StorageDimension");

        if (tag.contains("WorkerJobConfiguration", Tag.TAG_COMPOUND)) {
            configuration.load(
                    tag.getCompound("WorkerJobConfiguration"),
                    jobOrigin,
                    level().dimension().location().toString());
            targetBlockId = configuration.targetBlockId();
            jobOrigin = configuration.workAreaCenter();
            storagePosition = configuration.storagePosition();
            storageDimension = configuration.storageDimension();
            exclusions.clear();
            exclusions.addAll(configuration.exclusions());
            if (runtimeState == WorkerRuntimeState.UNCONFIGURED && targetBlockId != null) {
                runtimeState = WorkerRuntimeState.READY;
            }
        } else {
            // v1 migration: retain the old public fields but never resume
            // destructive work automatically.  The legacy WorkerJob value is
            // kept for old integrations; runtimeState is authoritative v2 UI.
            configuration.setWorkArea(
                    level().dimension().location().toString(), jobOrigin,
                    WorkerJobConfiguration.DEFAULT_HORIZONTAL_RADIUS,
                    WorkerJobConfiguration.DEFAULT_VERTICAL_RADIUS);
            configuration.setRequestedAmount(64, true);
            if (targetBlockId != null) configuration.setTarget(
                    targetBlockId, jobOrigin, level().dimension().location().toString());
            configuration.setStorage(storageDimension, storagePosition);
            exclusions.forEach(id -> configuration.setExcluded(id, true));
            job = targetBlockId == null ? WorkerJob.IDLE : WorkerJob.READY;
            blockReason = WorkerBlockReason.NONE;
            runtimeState = targetBlockId == null ? WorkerRuntimeState.UNCONFIGURED : WorkerRuntimeState.READY;
        }

        exclusions.clear();
        ListTag excluded = tag.getList("ExcludedBlocks", Tag.TAG_STRING);
        for (int index = 0; index < excluded.size(); index++) {
            ResourceLocation id = ResourceLocation.tryParse(excluded.getString(index));
            if (id != null) {
                exclusions.add(id);
            }
        }

        if (targetBlockId == null && job != WorkerJob.IDLE) {
            job = WorkerJob.IDLE;
            blockReason = WorkerBlockReason.NO_TARGET;
        }

        workerTicketChunks.clear();
        Arrays.stream(tag.getLongArray("WorkerTicketChunks"))
                .forEach(workerTicketChunks::add);
        simulationTicketChunks.clear();
        Arrays.stream(tag.getLongArray("WorkerSimulationTicketChunks"))
                .forEach(simulationTicketChunks::add);
        ticketsConfirmed = false;
        ticketCenter = Long.MIN_VALUE;
        ticketSimulationRadius = -1;
        workerController.resetTransientState();

        setCustomName(Component.translatable("entity.baritonehelper.baritone_helper"));
        setCustomNameVisible(false);
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    public @Nullable WorkerEntity getBreedOffspring(
            ServerLevel level,
            net.minecraft.world.entity.AgeableMob otherParent) {
        return null;
    }
}
