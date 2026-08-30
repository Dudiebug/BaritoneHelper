package dev.dudie.baritonehelper.entity;

import dev.dudie.baritonehelper.BaritoneHelper;
import dev.dudie.baritonehelper.worker.WorkerController;
import dev.dudie.baritonehelper.worker.WorkerJob;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
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
    private WorkerJob resumeJob = WorkerJob.COLLECT;
    private @Nullable ResourceLocation targetBlockId;
    private BlockPos jobOrigin = BlockPos.ZERO;
    private @Nullable BlockPos storagePosition;
    private String storageDimension = "";
    private boolean ticketsConfirmed;

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
        setCustomName(Component.translatable("entity.baritonehelper.worker"));
        setCustomNameVisible(false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
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

    public Optional<ResourceLocation> targetBlockId() {
        return Optional.ofNullable(targetBlockId);
    }

    public BlockPos jobOrigin() {
        return jobOrigin;
    }

    public Optional<BlockPos> storagePosition() {
        return Optional.ofNullable(storagePosition);
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

    public int workerTicketCount() {
        return ticketsConfirmed ? workerTicketChunks.size() : 0;
    }

    public void beginCollection(ResourceLocation blockId, BlockPos origin) {
        targetBlockId = blockId;
        jobOrigin = origin.immutable();
        exclusions.remove(blockId);
        job = WorkerJob.COLLECT;
        resumeJob = WorkerJob.COLLECT;
        workerController.resetTransientState();
        setChanged();
    }

    public void assignStorage(ServerLevel level, BlockPos position) {
        storageDimension = level.dimension().location().toString();
        storagePosition = position.immutable();
        if (job == WorkerJob.BLOCKED && targetBlockId != null) {
            job = WorkerJob.COLLECT;
        }
        workerController.resetTransientState();
        setChanged();
    }

    public boolean toggleExclusion(ResourceLocation blockId) {
        boolean excluded;
        if (exclusions.remove(blockId)) {
            excluded = false;
        } else {
            exclusions.add(blockId);
            excluded = true;
            if (blockId.equals(targetBlockId)) {
                job = WorkerJob.IDLE;
                targetBlockId = null;
            }
        }
        workerController.resetTransientState();
        setChanged();
        return excluded;
    }

    public WorkerJob togglePaused() {
        if (job == WorkerJob.PAUSED) {
            job = resumeJob.resumableFallback();
        } else {
            resumeJob = job.resumableFallback();
            job = WorkerJob.PAUSED;
        }
        workerController.resetTransientState();
        setChanged();
        return job;
    }

    public void markCollecting() {
        job = WorkerJob.COLLECT;
        resumeJob = WorkerJob.COLLECT;
        setChanged();
    }

    public void markBlocked() {
        if (job.activelyWorks()) {
            resumeJob = job;
        }
        job = WorkerJob.BLOCKED;
        getNavigation().stop();
        setChanged();
    }

    public void requestDepositOrBlock() {
        if (storagePosition != null && !storageDimension.isBlank()) {
            job = WorkerJob.DEPOSIT;
            resumeJob = WorkerJob.DEPOSIT;
        } else {
            markBlocked();
        }
        setChanged();
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
        if (job == WorkerJob.DEPOSIT && storagePosition != null && storageIsIn((ServerLevel) level())) {
            desired.add(new ChunkPos(storagePosition).toLong());
        }
        workerController.currentTarget().ifPresent(target ->
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
        if (!isOwnedBy(player)) {
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
                player.displayClientMessage(
                        Component.translatable("message.baritonehelper.cargo_installed"),
                        true);
            } else {
                player.displayClientMessage(
                        Component.translatable("message.baritonehelper.cargo_max"),
                        true);
            }
            return InteractionResult.SUCCESS;
        }

        if (held.is(BaritoneHelper.WORKER_CONTROLLER.get()) && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(this);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    private void dismiss(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.getData(BaritoneHelper.ACTIVE_WORKER).clear();
        }

        releaseInventoryOnce();
        ItemStack workerItem = new ItemStack(BaritoneHelper.WORKER.get());
        if (!player.getInventory().add(workerItem)) {
            player.drop(workerItem, false);
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
        if (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED) {
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
        return slot >= 0 && slot < getContainerSize() ? items.get(slot) : ItemStack.EMPTY;
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
    }

    @Override
    public boolean stillValid(Player player) {
        return isAlive() && isOwnedBy(player) && player.distanceToSqr(this) <= 64.0;
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
        return Component.translatable("container.baritonehelper.worker");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(
            int containerId,
            Inventory playerInventory,
            Player player) {
        if (!isOwnedBy(player)) {
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
        tag.putString("ResumeJob", resumeJob.name());
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
        tag.putLongArray("WorkerTicketChunks", workerTicketChunks.stream()
                .mapToLong(Long::longValue)
                .toArray());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setInvulnerable(true);
        clearContent();
        ContainerHelper.loadAllItems(tag, items, level().registryAccess());

        cargoUpgrades = Math.max(0, Math.min(1, tag.getInt("CargoUpgrades")));
        job = WorkerJob.fromSerialized(tag.getString("WorkerJob"));
        resumeJob = WorkerJob.fromSerialized(tag.getString("ResumeJob")).resumableFallback();

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

        workerTicketChunks.clear();
        Arrays.stream(tag.getLongArray("WorkerTicketChunks"))
                .forEach(workerTicketChunks::add);
        ticketsConfirmed = false;
        workerController.resetTransientState();

        setCustomName(Component.translatable("entity.baritonehelper.worker"));
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
