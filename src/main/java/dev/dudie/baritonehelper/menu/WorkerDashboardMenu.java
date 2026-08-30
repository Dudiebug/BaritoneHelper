package dev.dudie.baritonehelper.menu;

import dev.dudie.baritonehelper.BaritoneHelper;
import dev.dudie.baritonehelper.entity.WorkerEntity;
import dev.dudie.baritonehelper.worker.WorkerActionResult;
import dev.dudie.baritonehelper.worker.WorkerActivity;
import dev.dudie.baritonehelper.worker.WorkerBlockReason;
import dev.dudie.baritonehelper.worker.WorkerJob;
import dev.dudie.baritonehelper.worker.WorkerMessages;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

public final class WorkerDashboardMenu extends AbstractContainerMenu {
    public static final int BUTTON_START = 0;
    public static final int BUTTON_STOP = 1;
    public static final int BUTTON_CLEAR_TARGET = 2;
    public static final int BUTTON_CLEAR_STORAGE = 3;
    public static final int BUTTON_OPEN_INVENTORY = 4;

    private static final int DATA_JOB = 0;
    private static final int DATA_ACTIVITY = 1;
    private static final int DATA_TARGET_ID = 2;
    private static final int DATA_HAS_STORAGE = 3;
    private static final int DATA_STORAGE_X = 4;
    private static final int DATA_STORAGE_Y = 5;
    private static final int DATA_STORAGE_Z = 6;
    private static final int DATA_USED_SLOTS = 7;
    private static final int DATA_CAPACITY = 8;
    private static final int DATA_ITEM_COUNT = 9;
    private static final int DATA_TICKET_COUNT = 10;
    private static final int DATA_BLOCK_REASON = 11;
    private static final int DATA_HAS_TARGET_POSITION = 12;
    private static final int DATA_TARGET_X = 13;
    private static final int DATA_TARGET_Y = 14;
    private static final int DATA_TARGET_Z = 15;
    private static final int DATA_HAS_WORK_POSITION = 16;
    private static final int DATA_WORK_X = 17;
    private static final int DATA_WORK_Y = 18;
    private static final int DATA_WORK_Z = 19;
    private static final int DATA_REPLANS = 20;
    private static final int DATA_LAST_PROGRESS_AGE = 21;
    private static final int DATA_CONFIGURATION_REVISION = 22;
    private static final int DATA_COUNT = 23;

    private final int workerEntityId;
    private final @Nullable WorkerEntity worker;
    private final ContainerData data;

    public WorkerDashboardMenu(
            int containerId,
            Inventory playerInventory,
            RegistryFriendlyByteBuf extraData) {
        this(
                containerId,
                playerInventory,
                extraData.readVarInt(),
                null,
                new SimpleContainerData(DATA_COUNT));
    }

    public WorkerDashboardMenu(
            int containerId,
            Inventory playerInventory,
            WorkerEntity worker) {
        this(
                containerId,
                playerInventory,
                worker.getId(),
                worker,
                createServerData(worker));
    }

    private WorkerDashboardMenu(
            int containerId,
            Inventory playerInventory,
            int workerEntityId,
            @Nullable WorkerEntity worker,
            ContainerData data) {
        super(BaritoneHelper.WORKER_DASHBOARD.get(), containerId);
        this.workerEntityId = workerEntityId;
        this.worker = worker;
        this.data = data;
        checkContainerDataCount(data, DATA_COUNT);
        addDataSlots(data);
    }

    public int workerEntityId() {
        return workerEntityId;
    }

    public WorkerJob job() {
        return enumValue(WorkerJob.values(), data.get(DATA_JOB), WorkerJob.IDLE);
    }

    public WorkerActivity activity() {
        return enumValue(
                WorkerActivity.values(),
                data.get(DATA_ACTIVITY),
                WorkerActivity.IDLE);
    }

    public WorkerBlockReason blockReason() {
        return enumValue(
                WorkerBlockReason.values(),
                data.get(DATA_BLOCK_REASON),
                WorkerBlockReason.NONE);
    }

    public boolean hasTarget() {
        return data.get(DATA_TARGET_ID) >= 0;
    }

    public Block targetBlock() {
        int rawId = data.get(DATA_TARGET_ID);
        return rawId < 0 ? Blocks.AIR : BuiltInRegistries.BLOCK.byId(rawId);
    }

    public boolean hasStorage() {
        return data.get(DATA_HAS_STORAGE) != 0;
    }

    public Optional<BlockPos> storagePosition() {
        if (!hasStorage()) {
            return Optional.empty();
        }
        return Optional.of(new BlockPos(
                data.get(DATA_STORAGE_X),
                data.get(DATA_STORAGE_Y),
                data.get(DATA_STORAGE_Z)));
    }

    public int usedSlots() {
        return data.get(DATA_USED_SLOTS);
    }

    public int capacity() {
        return data.get(DATA_CAPACITY);
    }

    public int itemCount() {
        return data.get(DATA_ITEM_COUNT);
    }

    public int ticketCount() {
        return data.get(DATA_TICKET_COUNT);
    }

    public Optional<BlockPos> currentTargetPosition() {
        if (data.get(DATA_HAS_TARGET_POSITION) == 0) {
            return Optional.empty();
        }
        return Optional.of(new BlockPos(
                data.get(DATA_TARGET_X),
                data.get(DATA_TARGET_Y),
                data.get(DATA_TARGET_Z)));
    }

    public Optional<BlockPos> currentWorkPosition() {
        if (data.get(DATA_HAS_WORK_POSITION) == 0) {
            return Optional.empty();
        }
        return Optional.of(new BlockPos(
                data.get(DATA_WORK_X),
                data.get(DATA_WORK_Y),
                data.get(DATA_WORK_Z)));
    }

    public int replanAttempts() {
        return data.get(DATA_REPLANS);
    }

    public int lastProgressAgeTicks() {
        return data.get(DATA_LAST_PROGRESS_AGE);
    }

    public int configurationRevision() {
        return data.get(DATA_CONFIGURATION_REVISION);
    }

    public boolean isRunning() {
        return job().activelyWorks();
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (!(player instanceof ServerPlayer serverPlayer)
                || worker == null
                || !worker.isAlive()
                || !worker.isOwnedByPlayer(player)) {
            return false;
        }

        switch (buttonId) {
            case BUTTON_START -> handleStart(serverPlayer);
            case BUTTON_STOP -> handleStop(serverPlayer);
            case BUTTON_CLEAR_TARGET -> {
                worker.clearTarget();
                WorkerMessages.send(
                        serverPlayer,
                        ChatFormatting.YELLOW,
                        "message.baritonehelper.target_cleared");
            }
            case BUTTON_CLEAR_STORAGE -> {
                boolean cleared = worker.clearStorage();
                WorkerMessages.send(
                        serverPlayer,
                        cleared ? ChatFormatting.YELLOW : ChatFormatting.GRAY,
                        cleared
                                ? "message.baritonehelper.storage_cleared"
                                : "message.baritonehelper.storage_already_clear");
            }
            case BUTTON_OPEN_INVENTORY -> {
                serverPlayer.closeContainer();
                serverPlayer.openMenu(worker);
            }
            default -> {
                return false;
            }
        }
        broadcastChanges();
        return true;
    }

    private void handleStart(ServerPlayer player) {
        WorkerActionResult result = worker.startJob();
        switch (result) {
            case STARTED -> WorkerMessages.send(
                    player,
                    ChatFormatting.GREEN,
                    "message.baritonehelper.job_started",
                    WorkerMessages.targetName(worker));
            case ALREADY_RUNNING -> WorkerMessages.send(
                    player,
                    ChatFormatting.YELLOW,
                    "message.baritonehelper.job_already_running");
            case NO_TARGET -> WorkerMessages.send(
                    player,
                    ChatFormatting.RED,
                    "message.baritonehelper.cannot_start_no_target");
            case TARGET_EXCLUDED -> WorkerMessages.send(
                    player,
                    ChatFormatting.RED,
                    "message.baritonehelper.cannot_start_excluded");
            default -> WorkerMessages.send(
                    player,
                    ChatFormatting.RED,
                    "message.baritonehelper.command_failed");
        }
    }

    private void handleStop(ServerPlayer player) {
        WorkerActionResult result = worker.stopJob();
        WorkerMessages.send(
                player,
                result == WorkerActionResult.STOPPED
                        ? ChatFormatting.GREEN
                        : ChatFormatting.GRAY,
                result == WorkerActionResult.STOPPED
                        ? "message.baritonehelper.job_stopped"
                        : "message.baritonehelper.job_already_stopped");
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (worker == null) {
            return true;
        }
        return worker.isAlive()
                && worker.isOwnedByPlayer(player)
                && player.level() == worker.level();
    }

    private static ContainerData createServerData(WorkerEntity worker) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                Optional<BlockPos> storage = worker.storagePosition();
                Optional<BlockPos> target = worker.currentTarget();
                Optional<BlockPos> work = worker.currentWorkPosition();
                return switch (index) {
                    case DATA_JOB -> worker.job().ordinal();
                    case DATA_ACTIVITY -> worker.activity().ordinal();
                    case DATA_TARGET_ID -> worker.targetBlockId()
                            .map(id -> BuiltInRegistries.BLOCK.get(id))
                            .map(BuiltInRegistries.BLOCK::getId)
                            .orElse(-1);
                    case DATA_HAS_STORAGE -> storage.isPresent() ? 1 : 0;
                    case DATA_STORAGE_X -> storage.map(BlockPos::getX).orElse(0);
                    case DATA_STORAGE_Y -> storage.map(BlockPos::getY).orElse(0);
                    case DATA_STORAGE_Z -> storage.map(BlockPos::getZ).orElse(0);
                    case DATA_USED_SLOTS -> worker.inventoryUsedSlots();
                    case DATA_CAPACITY -> worker.getContainerSize();
                    case DATA_ITEM_COUNT -> worker.inventoryItemCount();
                    case DATA_TICKET_COUNT -> worker.workerTicketCount();
                    case DATA_BLOCK_REASON -> worker.blockReason().ordinal();
                    case DATA_HAS_TARGET_POSITION -> target.isPresent() ? 1 : 0;
                    case DATA_TARGET_X -> target.map(BlockPos::getX).orElse(0);
                    case DATA_TARGET_Y -> target.map(BlockPos::getY).orElse(0);
                    case DATA_TARGET_Z -> target.map(BlockPos::getZ).orElse(0);
                    case DATA_HAS_WORK_POSITION -> work.isPresent() ? 1 : 0;
                    case DATA_WORK_X -> work.map(BlockPos::getX).orElse(0);
                    case DATA_WORK_Y -> work.map(BlockPos::getY).orElse(0);
                    case DATA_WORK_Z -> work.map(BlockPos::getZ).orElse(0);
                    case DATA_REPLANS -> worker.replanAttempts();
                    case DATA_LAST_PROGRESS_AGE -> worker.lastProgressAgeTicks();
                    case DATA_CONFIGURATION_REVISION -> worker.configurationRevision();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // Dashboard data is server-authoritative and read-only to the client.
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }

    private static <T> T enumValue(T[] values, int ordinal, T fallback) {
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : fallback;
    }
}
