package dev.dudie.baritonehelper.entity;

import dev.dudie.baritonehelper.BaritoneHelper;
import dev.dudie.baritonehelper.menu.WorkerDashboardMenu;
import dev.dudie.baritonehelper.worker.WorkerActionResult;
import dev.dudie.baritonehelper.worker.WorkerActivity;
import dev.dudie.baritonehelper.worker.WorkerBlockReason;
import dev.dudie.baritonehelper.worker.WorkerController;
import dev.dudie.baritonehelper.worker.WorkerJob;
import dev.dudie.baritonehelper.worker.WorkerMessages;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.Nullable;

public final class WorkerEntity extends TamableAnimal implements Container, MenuProvider {
    public static final int BASE_SLOTS = 27;
    public static final int EXPANDED_SLOTS = 54;

    private final NonNullList<ItemStack> items =
            NonNullList.withSize(EXPANDED_SLOTS, ItemStack.EMPTY);
    private final Set<ResourceLocation> exclusions = new LinkedHashSet<>();
    private final Set<Long> workerTicketChunks = new LinkedHashSet<>();
    private final WorkerController workerController = new WorkerController(this);

    private int cargoUpgrades;
    private WorkerJob job = WorkerJob.IDLE;
    private WorkerBlockReason blockReason = WorkerBlockReason.NONE;
    private @Nullable ResourceLocation targetBlockId;
    private BlockPos jobOrigin = BlockPos.ZERO;
    private @Nullable BlockPos storagePosition;
    private String storageDimension = "";
    private boolean ticketsConfirmed;
    private int configurationRevision;

    public WorkerEntity(EntityType<? extends WorkerEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setInvulnerable(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.34)
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
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (tickCount % 20 == 0) {
            updateOwnerRecord(serverLevel);
        }
        workerController.tick(serverLevel);
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
        return configurationRevision;
    }

    public int workerTicketCount() {
        return ticketsConfirmed ? workerTicketChunks.size() : 0;
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

    /**
     * Updates the configured target without starting work. The previous implicit
     * start behavior made it impossible to distinguish configuration from execution.
     */
    public Optional<ResourceLocation> configureTarget(ResourceLocation blockId, BlockPos origin) {
        Optional<ResourceLocation> previous = Optional.ofNullable(targetBlockId);
        targetBlockId = blockId;
        jobOrigin = origin.immutable();
        exclusions.remove(blockId);
        job = WorkerJob.READY;
        blockReason = WorkerBlockReason.NONE;
        configurationRevision++;
        workerController.resetTransientState();
        releaseWorkerTickets();
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
            releaseWorkerTickets();
            setChanged();
            return WorkerActionResult.NO_TARGET;
        }
        if (exclusions.contains(targetBlockId)) {
            job = WorkerJob.READY;
            blockReason = WorkerBlockReason.TARGET_EXCLUDED;
            workerController.resetTransientState();
            releaseWorkerTickets();
            setChanged();
            return WorkerActionResult.TARGET_EXCLUDED;
        }
        if (job.activelyWorks()) {
            return WorkerActionResult.ALREADY_RUNNING;
        }

        job = WorkerJob.COLLECT;
        blockReason = WorkerBlockReason.NONE;
        configurationRevision++;
        workerController.resetTransientState();
        ensureWorkerTickets();
        setChanged();
        return WorkerActionResult.STARTED;
    }

    public WorkerActionResult stopJob() {
        boolean wasRunning = job.activelyWorks() || job == WorkerJob.BLOCKED;
        job = targetBlockId == null ? WorkerJob.IDLE : WorkerJob.READY;
        blockReason = WorkerBlockReason.NONE;
        configurationRevision++;
        workerController.resetTransientState();
        releaseWorkerTickets();
        setChanged();
        return wasRunning ? WorkerActionResult.STOPPED : WorkerActionResult.ALREADY_STOPPED;
    }

    public WorkerActionResult clearTarget() {
        targetBlockId = null;
        job = WorkerJob.IDLE;
        blockReason = WorkerBlockReason.NONE;
        configurationRevision++;
        workerController.resetTransientState();
        releaseWorkerTickets();
        setChanged();
        return WorkerActionResult.TARGET_CLEARED;
    }

    public void assignStorage(ServerLevel level, BlockPos position) {
        storageDimension = level.dimension().location().toString();
        storagePosition = position.immutable();
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
                job = WorkerJob.IDLE;
                blockReason = WorkerBlockReason.NONE;
                releaseWorkerTickets();
            }
        }
        configurationRevision++;
        workerController.resetTransientState();
        setChanged();
        return excluded;
    }

    public void markCollecting() {
        if (targetBlockId == null) {
            job = WorkerJob.IDLE;
            blockReason = WorkerBlockReason.NO_TARGET;
        } else {
            job = WorkerJob.COLLECT;
            blockReason = WorkerBlockReason.NONE;
        }
        setChanged();
    }

    public void markBlocked(WorkerBlockReason reason) {
        boolean changed = job != WorkerJob.BLOCKED || blockReason != reason;
        job = WorkerJob.BLOCKED;
        blockReason = reason;
        getNavigation().stop();
        setChanged();
        if (changed) {
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
            job = WorkerJob.DEPOSIT;
            blockReason = WorkerBlockReason.NONE;
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

    public void openDashboard(ServerPlayer player) {
        if (!isOwnedByPlayer(player) || player.level() != level()) {
            return;
        }
        player.openMenu(
                new SimpleMenuProvider(
                        (containerId, playerInventory, ignored) ->
                                new WorkerDashboardMenu(containerId, playerInventory, this),
                        Component.translatable("menu.baritonehelper.worker_dashboard")),
                buffer -> buffer.writeVarInt(getId()));
    }

    public void ensureWorkerTickets() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Set<Long> desired = desiredTicketChunks();
        if (!ticketsConfirmed) {
            for (long oldChunk : workerTicketChunks) {
                if (!desired.contains(oldChunk)) {
                    setTicket(serverLevel, oldChunk, false);
                }
            }
            for (long chunk : desired) {
                setTicket(serverLevel, chunk, true);
            }
            workerTicketChunks.clear();
            workerTicketChunks.addAll(desired);
            ticketsConfirmed = true;
            return;
        }

        for (long oldChunk : Set.copyOf(workerTicketChunks)) {
            if (!desired.contains(oldChunk)) {
                setTicket(serverLevel, oldChunk, false);
                workerTicketChunks.remove(oldChunk);
            }
        }
        for (long chunk : desired) {
            if (workerTicketChunks.add(chunk)) {
                setTicket(serverLevel, chunk, true);
            }
        }
    }

    public void releaseWorkerTickets() {
        if (level() instanceof ServerLevel serverLevel) {
            for (long chunk : Set.copyOf(workerTicketChunks)) {
                setTicket(serverLevel, chunk, false);
            }
        }
        workerTicketChunks.clear();
        ticketsConfirmed = false;
    }

    private Set<Long> desiredTicketChunks() {
        Set<Long> desired = new LinkedHashSet<>();
        ChunkPos current = chunkPosition();
        for (int x = current.x - 1; x <= current.x + 1; x++) {
            for (int z = current.z - 1; z <= current.z + 1; z++) {
                desired.add(ChunkPos.asLong(x, z));
            }
        }

        if (job.activelyWorks()) {
            desired.add(new ChunkPos(jobOrigin).toLong());
        }
        if (job == WorkerJob.DEPOSIT
                && storagePosition != null
                && storageIsIn((ServerLevel) level())) {
            desired.add(new ChunkPos(storagePosition).toLong());
        }
        workerController.currentTarget().ifPresent(target ->
                desired.add(new ChunkPos(target).toLong()));
        workerController.currentWorkPosition().ifPresent(target ->
                desired.add(new ChunkPos(target).toLong()));
        return desired;
    }

    private void setTicket(ServerLevel level, long packedChunk, boolean add) {
        ChunkPos chunk = new ChunkPos(packedChunk);
        BaritoneHelper.WORKER_TICKETS.forceChunk(
                level,
                getUUID(),
                chunk.x,
                chunk.z,
                add,
                true);
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
        if (reason == Entity.RemovalReason.KILLED
                || reason == Entity.RemovalReason.DISCARDED) {
            releaseWorkerTickets();
        }
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
        return isAlive()
                && isOwnedByPlayer(player)
                && player.distanceToSqr(this) <= 64.0;
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
        if (!isOwnedByPlayer(player)) {
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
        tag.putInt("CargoUpgrades", cargoUpgrades);
        tag.putString("WorkerJob", job.name());
        tag.putString("BlockReason", blockReason.name());
        tag.putInt("ConfigurationRevision", configurationRevision);
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
        ticketsConfirmed = false;
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
