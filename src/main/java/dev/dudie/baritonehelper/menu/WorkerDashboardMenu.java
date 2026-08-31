package dev.dudie.baritonehelper.menu;

import dev.dudie.baritonehelper.BaritoneHelper;
import dev.dudie.baritonehelper.entity.WorkerEntity;
import dev.dudie.baritonehelper.worker.WorkerActionResult;
import dev.dudie.baritonehelper.worker.WorkerMessages;
import dev.dudie.baritonehelper.worker.WorkerJob;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Deprecated compatibility menu for integrations that still reference the v1
 * menu type. The v2 dashboard is a normal Screen synchronized by payloads;
 * this class is not used by WorkerEntity.openDashboard and has no legacy data
 * synchronization path.
 */
@Deprecated
public final class WorkerDashboardMenu extends AbstractContainerMenu {
    public static final int BUTTON_START = 0;
    public static final int BUTTON_STOP = 1;
    public static final int BUTTON_CLEAR_TARGET = 2;
    public static final int BUTTON_CLEAR_STORAGE = 3;
    public static final int BUTTON_OPEN_INVENTORY = 4;

    private final int workerEntityId;
    private final @Nullable WorkerEntity worker;

    public WorkerDashboardMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, extraData.readVarInt(), null);
    }

    public WorkerDashboardMenu(int containerId, Inventory playerInventory, WorkerEntity worker) {
        this(containerId, playerInventory, worker.getId(), worker);
    }

    private WorkerDashboardMenu(int containerId, Inventory ignored, int workerEntityId, @Nullable WorkerEntity worker) {
        super(BaritoneHelper.WORKER_DASHBOARD.get(), containerId);
        this.workerEntityId = workerEntityId;
        this.worker = worker;
    }

    public int workerEntityId() { return workerEntityId; }
    public WorkerJob job() { return worker == null ? WorkerJob.IDLE : worker.job(); }
    public boolean hasTarget() { return worker != null && worker.targetBlockId().isPresent(); }
    public boolean hasStorage() { return worker != null && worker.storagePosition().isPresent(); }
    public Optional<BlockPos> currentTargetPosition() { return worker == null ? Optional.empty() : worker.currentTarget(); }
    public Optional<BlockPos> currentWorkPosition() { return worker == null ? Optional.empty() : worker.currentWorkPosition(); }
    public int configurationRevision() { return worker == null ? 0 : worker.configurationRevision(); }
    public boolean isRunning() { return job().activelyWorks(); }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (!(player instanceof ServerPlayer serverPlayer) || worker == null) return false;
        if (!worker.canOpenInventory(player)) {
            if (buttonId == BUTTON_OPEN_INVENTORY
                    && worker.isAlive()
                    && worker.isOwnedByPlayer(player)
                    && player.level() != worker.level()) {
                WorkerMessages.send(serverPlayer, ChatFormatting.RED,
                        "message.baritonehelper.other_dimension");
            }
            return false;
        }
        switch (buttonId) {
            case BUTTON_START -> handleStart(serverPlayer);
            case BUTTON_STOP -> handleStop(serverPlayer);
            case BUTTON_CLEAR_TARGET -> {
                worker.clearTarget();
                WorkerMessages.send(serverPlayer, ChatFormatting.YELLOW, "message.baritonehelper.target_cleared");
            }
            case BUTTON_CLEAR_STORAGE -> {
                boolean cleared = worker.clearStorage();
                WorkerMessages.send(serverPlayer, cleared ? ChatFormatting.YELLOW : ChatFormatting.GRAY,
                        cleared ? "message.baritonehelper.storage_cleared" : "message.baritonehelper.storage_already_clear");
            }
            case BUTTON_OPEN_INVENTORY -> {
                if (serverPlayer.openMenu(worker).isEmpty()) {
                    WorkerMessages.send(serverPlayer, ChatFormatting.RED,
                            "message.baritonehelper.command_failed");
                }
            }
            default -> { return false; }
        }
        return true;
    }

    private void handleStart(ServerPlayer player) {
        WorkerActionResult result = worker.startJob();
        WorkerMessages.send(player,
                result == WorkerActionResult.STARTED ? ChatFormatting.GREEN : ChatFormatting.YELLOW,
                result == WorkerActionResult.STARTED ? "message.baritonehelper.job_started" : "message.baritonehelper.command_failed");
    }

    private void handleStop(ServerPlayer player) {
        WorkerActionResult result = worker.stopJob();
        WorkerMessages.send(player,
                result == WorkerActionResult.STOPPED ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                result == WorkerActionResult.STOPPED ? "message.baritonehelper.job_stopped" : "message.baritonehelper.job_already_stopped");
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) {
        return worker == null || worker.canOpenInventory(player);
    }
}
