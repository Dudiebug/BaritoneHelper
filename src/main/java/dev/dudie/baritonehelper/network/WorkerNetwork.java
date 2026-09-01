package dev.dudie.baritonehelper.network;

import dev.dudie.baritonehelper.BaritoneHelper;
import dev.dudie.baritonehelper.ActiveWorkerData;
import dev.dudie.baritonehelper.entity.WorkerEntity;
import dev.dudie.baritonehelper.item.WorkerControllerItem;
import dev.dudie.baritonehelper.worker.WorkerActionResult;
import dev.dudie.baritonehelper.worker.WorkerMessages;
import dev.dudie.baritonehelper.worker.WorkerPickupService;
import dev.dudie.baritonehelper.worker.NoWorkZone;
import dev.dudie.baritonehelper.worker.NoWorkZoneMode;
import dev.dudie.baritonehelper.worker.SearchMode;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.Map;
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
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Registration and validation boundary for the dashboard protocol. */
@EventBusSubscriber(modid = BaritoneHelper.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class WorkerNetwork {
    private static final String PROTOCOL = "4";
    private static final String REQUEST_CONFLICT = "request_conflict";
    private static final String INVENTORY_WRONG_DIMENSION = "inventory_wrong_dimension";
    private static final String INVENTORY_OPEN_FAILED = "inventory_open_failed";
    private static final String PHYSICAL_ACTION_WRONG_DIMENSION = "physical_action_wrong_dimension";
    private static final UUID NO_WORKER_UUID = new UUID(0L, 0L);
    private static final Map<WorkerEntity, SentSnapshot> LAST_SENT_SNAPSHOTS = new WeakHashMap<>();

    private record SentSnapshot(
            UUID ownerId, long serverTick, WorkerDashboardStateS2C.Snapshot snapshot) {}
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
        if (player == null || worker == null || !worker.isAlive()
                || !worker.isOwnedByPlayer(player)) return;
        WorkerDashboardStateS2C.Snapshot snapshot = nextSnapshot(worker, player.getUUID(), true);
        sendToPlayer(player, new OpenWorkerDashboardS2C(snapshot));
    }

    public static void sendStateToOwner(WorkerEntity worker) {
        if (!(worker.level() instanceof ServerLevel level) || worker.getOwnerUUID() == null) return;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(worker.getOwnerUUID());
        if (owner != null) {
            WorkerDashboardStateS2C.Snapshot snapshot = nextSnapshot(worker, owner.getUUID(), false);
            if (snapshot == null) return;
            sendToPlayer(owner, new WorkerDashboardStateS2C(snapshot));
        }
    }

    private static WorkerDashboardStateS2C.Snapshot nextSnapshot(
            WorkerEntity worker, UUID ownerId, boolean force) {
        long serverTick = worker.level().getGameTime();
        synchronized (LAST_SENT_SNAPSHOTS) {
            SentSnapshot previous = LAST_SENT_SNAPSHOTS.get(worker);
            if (!force && previous != null && ownerId.equals(previous.ownerId())
                    && serverTick >= previous.serverTick()
                    && serverTick - previous.serverTick() < 10L) return null;
            WorkerDashboardStateS2C.Snapshot current = WorkerDashboardStateS2C.Snapshot.from(worker);
            if (!force && previous != null
                    && ownerId.equals(previous.ownerId())
                    && current.withStateSequence(previous.snapshot().stateSequence())
                            .equals(previous.snapshot())) return null;
            long nextSequence = worker.nextDashboardStateSequence();
            WorkerDashboardStateS2C.Snapshot next = current.withStateSequence(nextSequence);
            LAST_SENT_SNAPSHOTS.put(worker, new SentSnapshot(ownerId, serverTick, next));
            return next;
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
        context.enqueueWork(() -> handleActionServerOnServerThread(payload, context));
    }

    private static void handleActionServerOnServerThread(
            WorkerDashboardActionC2S payload, IPayloadContext context) {
        if (payload == null || !(context.player() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) return;

        RequestReceiptData.Lookup cached = RequestReceiptData.get(level).lookup(
                player.getUUID(), payload);
        if (cached != null) {
            if (cached.conflicting()) {
                sendAcknowledgement(player, payload, false, REQUEST_CONFLICT,
                        "message.baritonehelper.command_failed", 0);
            } else {
                sendToPlayer(player, cached.acknowledgement());
                if (payload.action() == WorkerDashboardActionC2S.Action.REQUEST_SNAPSHOT) {
                    // A cached snapshot replay still requests current state; only the mutation ACK is replayed.
                    WorkerEntity cachedWorker = findOwnedWorker(
                            player, payload.workerUuid(), payload.dimension());
                    if (cachedWorker != null) sendStateToOwner(cachedWorker);
                }
            }
            return;
        }

        WorkerEntity worker = findOwnedWorker(player, payload.workerUuid(), payload.dimension());
        if (worker == null) {
            boolean wrongDimension = isPhysicalAction(payload.action())
                    && ownedWorkerInOtherDimension(player, payload.workerUuid());
            String errorCode = wrongDimension
                    ? payload.action() == WorkerDashboardActionC2S.Action.OPEN_INVENTORY
                            ? INVENTORY_WRONG_DIMENSION : PHYSICAL_ACTION_WRONG_DIMENSION
                    : "worker_not_found";
            sendAcknowledgement(player, payload, false, errorCode,
                    wrongDimension
                            ? "message.baritonehelper.other_dimension"
                            : "message.baritonehelper.no_worker",
                    0);
            return;
        }
        if (worker.configurationRevision() != payload.expectedRevision()) {
            WorkerMessages.send(player, ChatFormatting.YELLOW, "message.baritonehelper.dashboard_stale");
            sendAcknowledgement(player, payload, false, "stale_revision",
                    "message.baritonehelper.dashboard_stale", worker.configurationRevision());
            sendStateToOwner(worker);
            return;
        }
        if (payload.action() == WorkerDashboardActionC2S.Action.PICKUP) {
            int revision = worker.configurationRevision();
            WorkerPickupService.Result pickup = WorkerPickupService.pickup(
                    player, worker, payload.requestId());
            String translation = pickup.success()
                    ? "message.baritonehelper.dismissed"
                    : "message.baritonehelper.pickup_failed";
            sendAcknowledgement(player, payload, pickup.success(), pickup.errorCode(), translation, revision);
            WorkerMessages.send(player,
                    pickup.success() ? ChatFormatting.GREEN : ChatFormatting.RED,
                    translation);
            return;
        }
        if (payload.action() == WorkerDashboardActionC2S.Action.OPEN_INVENTORY) {
            handleInventoryRequest(player, worker, payload);
            return;
        }
        if (isPhysicalAction(payload.action()) && player.level() != worker.level()) {
            sendAcknowledgement(player, payload, false, PHYSICAL_ACTION_WRONG_DIMENSION,
                    "message.baritonehelper.other_dimension", worker.configurationRevision());
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
        sendAcknowledgement(player, payload, success, errorCode, translationKey, worker.configurationRevision());
        sendStateToOwner(worker);
    }

    private static void handleInventoryRequest(
            ServerPlayer player, WorkerEntity worker, WorkerDashboardActionC2S payload) {
        if (player.level() != worker.level()) {
            sendAcknowledgement(player, payload, false, INVENTORY_WRONG_DIMENSION,
                    "message.baritonehelper.other_dimension", worker.configurationRevision());
            return;
        }
        if (!worker.canOpenInventory(player)) {
            sendAcknowledgement(player, payload, false, INVENTORY_OPEN_FAILED,
                    "message.baritonehelper.command_failed", worker.configurationRevision());
            return;
        }
        try {
            if (player.openMenu(worker).isEmpty()) {
                sendAcknowledgement(player, payload, false, INVENTORY_OPEN_FAILED,
                        "message.baritonehelper.command_failed", worker.configurationRevision());
                return;
            }
        } catch (RuntimeException ignored) {
            sendAcknowledgement(player, payload, false, INVENTORY_OPEN_FAILED,
                    "message.baritonehelper.command_failed", worker.configurationRevision());
            return;
        }
        sendAcknowledgement(player, payload, true, "ok",
                "message.baritonehelper.action_applied", worker.configurationRevision());
    }

    private static void sendAcknowledgement(
            ServerPlayer player,
            WorkerDashboardActionC2S payload,
            boolean success,
            String errorCode,
            String translationKey,
            int revision) {
        WorkerActionAcknowledgementS2C acknowledgement = new WorkerActionAcknowledgementS2C(
                payload.requestId(), success, errorCode, translationKey, revision);
        if (player.level() instanceof ServerLevel level) {
            RequestReceiptData.get(level).record(player.getUUID(), payload, acknowledgement);
        }
        sendToPlayer(player, acknowledgement);
    }

    private static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (player == null || payload == null
                || !NetworkRegistry.hasChannel(player.connection, payload.type().id())) return;
        PacketDistributor.sendToPlayer(player, payload);
    }

    private static WorkerEntity findOwnedWorker(
            ServerPlayer player, UUID workerUuid, String dimension) {
        return WorkerControllerItem.findOwnedWorker(player, workerUuid, dimension).orElse(null);
    }

    private static boolean ownedWorkerInOtherDimension(ServerPlayer player, UUID workerUuid) {
        ActiveWorkerData active = player.getData(BaritoneHelper.ACTIVE_WORKER);
        return active.matches(workerUuid)
                && !active.dimension().equals(player.level().dimension().location().toString());
    }

    private static boolean isPhysicalAction(WorkerDashboardActionC2S.Action action) {
        return action == WorkerDashboardActionC2S.Action.OPEN_INVENTORY
                || action == WorkerDashboardActionC2S.Action.ARM_STORAGE_SELECTION
                || action == WorkerDashboardActionC2S.Action.ARM_AREA_SELECTION
                || action == WorkerDashboardActionC2S.Action.ARM_ZONE_SELECTION;
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
            case OPEN_INVENTORY -> WorkerActionResult.INVALID_CONFIGURATION;
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
            case SET_SEARCH_MODE -> {
                SearchMode mode = SearchMode.fromSerialized(payload.blockId());
                yield mode.serializedName().equals(payload.blockId()) && worker.setSearchMode(mode)
                        ? WorkerActionResult.ALREADY_STOPPED
                        : WorkerActionResult.INVALID_CONFIGURATION;
            }
            case SET_TARGET -> configureTarget(player, worker, payload.blockId());
            case PICKUP -> WorkerActionResult.INVALID_CONFIGURATION;
            case INVALID -> WorkerActionResult.INVALID_CONFIGURATION;
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
            case SET_SEARCH_MODE -> WorkerMessages.send(player, ChatFormatting.GREEN, "message.baritonehelper.search_mode_changed");
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
        return worker.addNoWorkZone(new NoWorkZone(
                UUID.randomUUID(),
                payload.blockId() == null || payload.blockId().isBlank() ? "No-work zone" : payload.blockId(),
                worker.level().dimension().location().toString(),
                payload.workAreaCenter() == null ? worker.blockPosition() : payload.workAreaCenter(),
                payload.horizontalRadius(),
                payload.verticalRadius(),
                NoWorkZoneMode.values()[payload.amount()],
                payload.unlimited()))
                ? WorkerActionResult.ALREADY_STOPPED
                : WorkerActionResult.INVALID_CONFIGURATION;
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
