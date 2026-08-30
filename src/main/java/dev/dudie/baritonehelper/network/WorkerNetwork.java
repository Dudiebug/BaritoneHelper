package dev.dudie.baritonehelper.network;

import dev.dudie.baritonehelper.BaritoneHelper;
import dev.dudie.baritonehelper.entity.WorkerEntity;
import dev.dudie.baritonehelper.worker.WorkerActionResult;
import dev.dudie.baritonehelper.worker.WorkerMessages;
import dev.dudie.baritonehelper.worker.NoWorkZone;
import dev.dudie.baritonehelper.worker.NoWorkZoneMode;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Registration and validation boundary for the dashboard protocol. */
@EventBusSubscriber(modid = BaritoneHelper.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class WorkerNetwork {
    private static final String PROTOCOL = "2";

    private WorkerNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(PROTOCOL);
        registrar.playToClient(
                OpenWorkerDashboardS2C.TYPE,
                OpenWorkerDashboardS2C.STREAM_CODEC,
                WorkerNetwork::handleOpenClient);
        registrar.playToClient(
                WorkerDashboardStateS2C.TYPE,
                WorkerDashboardStateS2C.STREAM_CODEC,
                WorkerNetwork::handleStateClient);
        registrar.playToClient(
                WorkerActionAcknowledgementS2C.TYPE,
                WorkerActionAcknowledgementS2C.STREAM_CODEC,
                WorkerNetwork::handleAcknowledgementClient);
        registrar.playToServer(
                WorkerDashboardActionC2S.TYPE,
                WorkerDashboardActionC2S.STREAM_CODEC,
                WorkerNetwork::handleActionServer);
    }

    public static void openDashboard(ServerPlayer player, WorkerEntity worker) {
        PacketDistributor.sendToPlayer(
                player,
                new OpenWorkerDashboardS2C(WorkerDashboardStateS2C.Snapshot.from(worker)));
    }

    public static void sendStateToOwner(WorkerEntity worker) {
        if (!(worker.level() instanceof ServerLevel level) || worker.getOwnerUUID() == null) return;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(worker.getOwnerUUID());
        if (owner != null && owner.level() == level) {
            PacketDistributor.sendToPlayer(
                    owner,
                    new WorkerDashboardStateS2C(WorkerDashboardStateS2C.Snapshot.from(worker)));
        }
    }

    private static void handleOpenClient(OpenWorkerDashboardS2C payload, IPayloadContext context) {
        if (!FMLEnvironment.dist.isClient()) return;
        context.enqueueWork(() -> invokeClient("open", payload));
    }

    private static void handleStateClient(WorkerDashboardStateS2C payload, IPayloadContext context) {
        if (!FMLEnvironment.dist.isClient()) return;
        context.enqueueWork(() -> invokeClient("state", payload));
    }

    private static void handleAcknowledgementClient(
            WorkerActionAcknowledgementS2C payload, IPayloadContext context) {
        if (!FMLEnvironment.dist.isClient()) return;
        context.enqueueWork(() -> invokeClient("ack", payload));
    }

    /** Reflection keeps client-only GUI classes out of the dedicated-server class graph. */
    private static void invokeClient(String method, Object payload) {
        try {
            Class<?> client = Class.forName("dev.dudie.baritonehelper.client.WorkerDashboardClient");
            if ("open".equals(method)) {
                client.getMethod("open", OpenWorkerDashboardS2C.class).invoke(null, payload);
            } else if ("state".equals(method)) {
                client.getMethod("state", WorkerDashboardStateS2C.class).invoke(null, payload);
            } else if ("ack".equals(method)) {
                client.getMethod("ack", WorkerActionAcknowledgementS2C.class).invoke(null, payload);
            }
        } catch (ReflectiveOperationException exception) {
            dev.dudie.baritonehelper.internal.baritone.InternalBaritoneRuntime.LOGGER.error(
                    "Unable to deliver worker dashboard payload", exception);
        }
    }

    private static void handleActionServer(WorkerDashboardActionC2S payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) return;
        WorkerEntity worker = findOwnedWorker(player, payload.workerEntityId());
        if (worker == null) {
            PacketDistributor.sendToPlayer(player, new WorkerActionAcknowledgementS2C(
                    payload.requestId(), false, "worker_not_found", "message.baritonehelper.no_worker", 0));
            return;
        }
        if (worker.configurationRevision() != payload.expectedRevision()) {
            WorkerMessages.send(player, ChatFormatting.YELLOW, "message.baritonehelper.dashboard_stale");
            PacketDistributor.sendToPlayer(player, new WorkerActionAcknowledgementS2C(
                    payload.requestId(), false, "stale_revision", "message.baritonehelper.dashboard_stale",
                    worker.configurationRevision()));
            sendStateToOwner(worker);
            return;
        }
        WorkerActionResult result = applyAction(player, worker, payload);
        sendActionFeedback(player, payload.action(), result);
        boolean success = switch (result) {
            case INVALID_CONFIGURATION, STALE_REVISION, NO_TARGET, TARGET_EXCLUDED,
                    ALREADY_COMPLETED -> false;
            default -> true;
        };
        String errorCode = success ? "ok" : result.name().toLowerCase(java.util.Locale.ROOT);
        String translationKey = success ? "message.baritonehelper.action_applied" : errorTranslation(result);
        PacketDistributor.sendToPlayer(player, new WorkerActionAcknowledgementS2C(
                payload.requestId(), success, errorCode, translationKey, worker.configurationRevision()));
        sendStateToOwner(worker);
    }

    private static WorkerEntity findOwnedWorker(ServerPlayer player, int entityId) {
        if (!(player.level() instanceof ServerLevel level)) return null;
        var entity = level.getEntity(entityId);
        return entity instanceof WorkerEntity worker
                && worker.isAlive()
                && worker.isOwnedByPlayer(player)
                ? worker
                : null;
    }

    private static WorkerActionResult applyAction(
            ServerPlayer player, WorkerEntity worker, WorkerDashboardActionC2S payload) {
        WorkerDashboardActionC2S.Action action = payload.action();
        if (action == null) return WorkerActionResult.INVALID_CONFIGURATION;
        if (action == WorkerDashboardActionC2S.Action.SET_AMOUNT
                && (payload.amount() < 1 || payload.amount() > 1_000_000)) {
            return WorkerActionResult.INVALID_CONFIGURATION;
        }
        if (action == WorkerDashboardActionC2S.Action.SET_WORK_AREA
                && (payload.horizontalRadius() < 8 || payload.horizontalRadius() > 512
                || payload.verticalRadius() < 4 || payload.verticalRadius() > 128)) {
            return WorkerActionResult.INVALID_CONFIGURATION;
        }
        return switch (action) {
            case START -> worker.startJob();
            case STOP -> worker.stopJob();
            case CLEAR_TARGET -> worker.clearTarget();
            case CLEAR_STORAGE -> {
                worker.clearStorage();
                yield WorkerActionResult.TARGET_CLEARED;
            }
            case OPEN_INVENTORY -> {
                player.closeContainer();
                player.openMenu(worker);
                yield WorkerActionResult.ALREADY_STOPPED;
            }
            case RESET_PROGRESS -> {
                worker.resetProgress();
                yield WorkerActionResult.ALREADY_STOPPED;
            }
            case REQUEST_SNAPSHOT -> WorkerActionResult.ALREADY_STOPPED;
            case SET_AMOUNT -> {
                worker.setRequestedAmount(payload.amount(), payload.unlimited());
                yield WorkerActionResult.ALREADY_STOPPED;
            }
            case SET_WORK_AREA -> {
                BlockPos center = payload.workAreaCenter() == null
                        ? worker.blockPosition() : payload.workAreaCenter();
                worker.setWorkArea(center, payload.horizontalRadius(), payload.verticalRadius());
                yield WorkerActionResult.ALREADY_STOPPED;
            }
            case CLEAR_WORK_AREA -> {
                worker.clearWorkArea();
                yield WorkerActionResult.ALREADY_STOPPED;
            }
            case TOGGLE_PATHING -> worker.setPathingFlag(payload.blockId(), payload.unlimited())
                    ? WorkerActionResult.ALREADY_STOPPED
                    : WorkerActionResult.INVALID_CONFIGURATION;
            case ARM_STORAGE_SELECTION -> {
                worker.armStorageSelection(player);
                yield WorkerActionResult.ALREADY_STOPPED;
            }
            case ARM_AREA_SELECTION -> {
                worker.armAreaSelection(player);
                yield WorkerActionResult.ALREADY_STOPPED;
            }
            case ARM_ZONE_SELECTION -> {
                try {
                    worker.armZoneSelection(player, UUID.fromString(payload.blockId()));
                    yield WorkerActionResult.ALREADY_STOPPED;
                } catch (IllegalArgumentException ignored) {
                    yield WorkerActionResult.INVALID_CONFIGURATION;
                }
            }
            case ADD_NO_WORK_ZONE -> addNoWorkZone(worker, payload);
            case UPDATE_NO_WORK_ZONE -> updateNoWorkZone(worker, payload);
            case DELETE_NO_WORK_ZONE -> deleteNoWorkZone(worker, payload.blockId());
            case TOGGLE_NO_WORK_ZONE -> toggleNoWorkZone(worker, payload.blockId());
            case TOGGLE_EXCLUSION -> toggleExclusion(worker, payload.blockId());
            case SET_TARGET -> configureTarget(player, worker, payload.blockId());
        };
    }

    private static WorkerActionResult configureTarget(
            ServerPlayer player, WorkerEntity worker, String rawId) {
        ResourceLocation id = ResourceLocation.tryParse(rawId == null ? "" : rawId);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)
                || BuiltInRegistries.BLOCK.get(id) == Blocks.AIR) {
            WorkerMessages.send(player, ChatFormatting.RED, "message.baritonehelper.invalid_target");
            return WorkerActionResult.INVALID_CONFIGURATION;
        }
        // Target selection is independent from the configured work area.  Keep
        // the existing center when replacing a target instead of silently
        // moving the search window to the worker's current position.
        worker.configureTarget(id, worker.workAreaCenter());
        WorkerMessages.send(player, ChatFormatting.GREEN, "message.baritonehelper.target_ready");
        return WorkerActionResult.ALREADY_STOPPED;
    }

    private static void sendActionFeedback(
            ServerPlayer player, WorkerDashboardActionC2S.Action action, WorkerActionResult result) {
        if (result == WorkerActionResult.INVALID_CONFIGURATION || action == null) return;
        switch (action) {
            case START -> {
                if (result == WorkerActionResult.STARTED) {
                    WorkerMessages.send(player, ChatFormatting.GREEN, "message.baritonehelper.job_started");
                } else if (result == WorkerActionResult.ALREADY_COMPLETED) {
                    WorkerMessages.send(player, ChatFormatting.YELLOW, "message.baritonehelper.already_completed");
                } else if (result == WorkerActionResult.NO_TARGET) {
                    WorkerMessages.send(player, ChatFormatting.RED, "message.baritonehelper.cannot_start_no_target");
                } else if (result == WorkerActionResult.TARGET_EXCLUDED) {
                    WorkerMessages.send(player, ChatFormatting.RED, "message.baritonehelper.cannot_start_excluded");
                } else {
                    WorkerMessages.send(player, ChatFormatting.YELLOW, "message.baritonehelper.job_already_running");
                }
            }
            case STOP -> WorkerMessages.send(player,
                    result == WorkerActionResult.STOPPED ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                    result == WorkerActionResult.STOPPED ? "message.baritonehelper.job_stopped" : "message.baritonehelper.job_already_stopped");
            case CLEAR_TARGET -> WorkerMessages.send(player, ChatFormatting.YELLOW, "message.baritonehelper.target_cleared");
            case CLEAR_STORAGE -> WorkerMessages.send(player, ChatFormatting.YELLOW, "message.baritonehelper.storage_cleared");
            case SET_AMOUNT -> WorkerMessages.send(player, ChatFormatting.GREEN, "message.baritonehelper.amount_changed");
            case SET_WORK_AREA -> WorkerMessages.send(player, ChatFormatting.GREEN, "message.baritonehelper.work_area_changed");
            case CLEAR_WORK_AREA -> WorkerMessages.send(player, ChatFormatting.YELLOW, "message.baritonehelper.work_area_cleared");
            case ARM_STORAGE_SELECTION -> WorkerMessages.send(player, ChatFormatting.YELLOW, "message.baritonehelper.storage_select_mode");
            case ARM_AREA_SELECTION -> WorkerMessages.send(player, ChatFormatting.YELLOW, "message.baritonehelper.area_select_mode");
            case ARM_ZONE_SELECTION -> WorkerMessages.send(player, ChatFormatting.YELLOW, "message.baritonehelper.zone_select_mode");
            case ADD_NO_WORK_ZONE -> WorkerMessages.send(player, ChatFormatting.GREEN, "message.baritonehelper.zone_added");
            case UPDATE_NO_WORK_ZONE -> WorkerMessages.send(player, ChatFormatting.GREEN, "message.baritonehelper.zone_updated");
            case DELETE_NO_WORK_ZONE -> WorkerMessages.send(player, ChatFormatting.GREEN, "message.baritonehelper.zone_deleted");
            case TOGGLE_NO_WORK_ZONE -> WorkerMessages.send(player, ChatFormatting.GREEN, "message.baritonehelper.zone_toggled");
            case TOGGLE_PATHING -> WorkerMessages.send(player, ChatFormatting.GREEN, "message.baritonehelper.pathing_changed");
            case TOGGLE_EXCLUSION -> WorkerMessages.send(player, ChatFormatting.GREEN, "message.baritonehelper.exclusion_changed");
            case RESET_PROGRESS -> WorkerMessages.send(player, ChatFormatting.GREEN, "message.baritonehelper.progress_reset");
            default -> { }
        }
    }

    private static String errorTranslation(WorkerActionResult result) {
        return switch (result) {
            case NO_TARGET -> "message.baritonehelper.cannot_start_no_target";
            case TARGET_EXCLUDED -> "message.baritonehelper.cannot_start_excluded";
            case ALREADY_COMPLETED -> "message.baritonehelper.already_completed";
            case STALE_REVISION -> "message.baritonehelper.dashboard_stale";
            default -> "message.baritonehelper.command_failed";
        };
    }

    private static WorkerActionResult addNoWorkZone(
            WorkerEntity worker, WorkerDashboardActionC2S payload) {
        if (payload.amount() < 0 || payload.amount() >= NoWorkZoneMode.values().length) {
            return WorkerActionResult.INVALID_CONFIGURATION;
        }
        if (payload.horizontalRadius() < 0 || payload.horizontalRadius() > 512
                || payload.verticalRadius() < 0 || payload.verticalRadius() > 128) {
            return WorkerActionResult.INVALID_CONFIGURATION;
        }
        worker.addNoWorkZone(new NoWorkZone(
                UUID.randomUUID(),
                payload.blockId() == null || payload.blockId().isBlank() ? "No-work zone" : payload.blockId(),
                worker.level().dimension().location().toString(),
                payload.workAreaCenter() == null ? worker.blockPosition() : payload.workAreaCenter(),
                payload.horizontalRadius(),
                payload.verticalRadius(),
                NoWorkZoneMode.values()[payload.amount()],
                payload.unlimited()));
        return WorkerActionResult.ALREADY_STOPPED;
    }

    private static WorkerActionResult updateNoWorkZone(
            WorkerEntity worker, WorkerDashboardActionC2S payload) {
        if (payload.amount() < 0 || payload.amount() >= NoWorkZoneMode.values().length
                || payload.horizontalRadius() < 0 || payload.horizontalRadius() > 512
                || payload.verticalRadius() < 0 || payload.verticalRadius() > 128) {
            return WorkerActionResult.INVALID_CONFIGURATION;
        }
        try {
            return worker.updateNoWorkZone(
                    UUID.fromString(payload.blockId()),
                    payload.workAreaCenter(),
                    payload.horizontalRadius(),
                    payload.verticalRadius(),
                    NoWorkZoneMode.values()[payload.amount()],
                    payload.unlimited())
                    ? WorkerActionResult.ALREADY_STOPPED : WorkerActionResult.INVALID_CONFIGURATION;
        } catch (IllegalArgumentException ignored) {
            return WorkerActionResult.INVALID_CONFIGURATION;
        }
    }

    private static WorkerActionResult deleteNoWorkZone(WorkerEntity worker, String rawId) {
        try {
            return worker.removeNoWorkZone(UUID.fromString(rawId))
                    ? WorkerActionResult.ALREADY_STOPPED : WorkerActionResult.INVALID_CONFIGURATION;
        } catch (IllegalArgumentException ignored) {
            return WorkerActionResult.INVALID_CONFIGURATION;
        }
    }

    private static WorkerActionResult toggleNoWorkZone(WorkerEntity worker, String rawId) {
        try {
            return worker.toggleNoWorkZone(UUID.fromString(rawId))
                    ? WorkerActionResult.ALREADY_STOPPED : WorkerActionResult.INVALID_CONFIGURATION;
        } catch (IllegalArgumentException ignored) {
            return WorkerActionResult.INVALID_CONFIGURATION;
        }
    }

    private static WorkerActionResult toggleExclusion(WorkerEntity worker, String rawId) {
        ResourceLocation id = ResourceLocation.tryParse(rawId == null ? "" : rawId);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) return WorkerActionResult.INVALID_CONFIGURATION;
        worker.toggleExclusion(id);
        return WorkerActionResult.ALREADY_STOPPED;
    }
}
