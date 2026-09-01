package dev.dudie.baritonehelper.client;

import dev.dudie.baritonehelper.network.WorkerDashboardActionC2S;
import dev.dudie.baritonehelper.network.WorkerDashboardStateS2C;
import dev.dudie.baritonehelper.network.WorkerActionAcknowledgementS2C;
import dev.dudie.baritonehelper.worker.WorkerActivity;
import dev.dudie.baritonehelper.worker.WorkerBlockReason;
import dev.dudie.baritonehelper.worker.WorkerJob;
import dev.dudie.baritonehelper.worker.WorkerRuntimeState;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.PacketDistributor;

/** Responsive, payload-backed controller. It never infers a target from a world click. */
public final class WorkerDashboardScreen extends Screen {
    private static final int LOGICAL_WIDTH = 640;
    private static final int LOGICAL_HEIGHT = 480;
    private static final float MAX_UI_SCALE = 1.5F;
    private static final int TAB_JOB = 0;
    private static final int TAB_WORLD = 1;
    private static final int TAB_STORAGE = 2;
    private static final int TAB_PATHING = 3;
    private static final int TAB_LOG = 4;
    private static final int PICKER_ROWS = 9;
    private static final String WORK_AREA_MODE = "work_area";
    private static final String ROAM_MODE = "roam";

    private WorkerDashboardStateS2C.Snapshot snapshot;
    private DashboardProtocolAccessors.SnapshotView snapshotView;
    private int tab = TAB_JOB;
    private float uiScale = 1.0F;
    private int logicalWidth;
    private int logicalHeight;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int pickerOffset;
    private int zoneOffset;
    private int activityOffset;
    private List<ResourceLocation> pickerResults = List.of();
    private String lastAcknowledgement = "";
    private String pickupConfirmation = "";
    private boolean requestPending;
    private UUID pendingRequestId;
    private int awaitingSnapshotRevision;

    private EditBox searchBox;
    private EditBox amountBox;
    private EditBox areaXBox;
    private EditBox areaYBox;
    private EditBox areaZBox;
    private EditBox areaHorizontalBox;
    private EditBox areaVerticalBox;
    private EditBox zoneNameBox;
    private EditBox zoneXBox;
    private EditBox zoneYBox;
    private EditBox zoneZBox;
    private EditBox zoneHorizontalBox;
    private EditBox zoneVerticalBox;
    private Button startButton;
    private Button stopButton;
    private Button inventoryButton;
    private Button clearTargetButton;
    private Button clearStorageButton;
    private Button exclusionButton;
    private Button pickupButton;
    private Button amountModeButton;
    private Button applyAmountButton;
    private Button resetProgressButton;
    private Button applyAreaButton;
    private Button usePlayerAreaButton;
    private Button useWorkerAreaButton;
    private Button selectAreaPointButton;
    private Button clearAreaButton;
    private Button selectStorageButton;
    private Button addZoneButton;
    private Button saveZoneButton;
    private Button deleteZoneButton;
    private Button toggleZoneButton;
    private Button selectZonePointButton;
    private Button zoneModeButton;
    private Button zoneEnabledButton;
    private Button workAreaModeButton;
    private Button roamModeButton;
    private String selectedZoneId = "";
    private int zoneMode;
    private boolean zoneEnabled = true;
    private final List<Button> areaPresetButtons = new ArrayList<>();
    private Button jobTab;
    private Button worldTab;
    private Button storageTab;
    private Button pathingTab;
    private Button logTab;
    private final List<Button> pathingButtons = new ArrayList<>();

    public WorkerDashboardScreen(WorkerDashboardStateS2C.Snapshot snapshot) {
        super(Component.translatable("menu.baritonehelper.worker_dashboard"));
        this.snapshot = snapshot;
        this.snapshotView = DashboardProtocolAccessors.snapshotView(snapshot);
        this.awaitingSnapshotRevision = 0;
        if (!selectedZoneId.isBlank() && snapshot.noWorkZones().stream().noneMatch(zone -> zone.id().equals(selectedZoneId))) {
            selectedZoneId = "";
        }
        refreshPickerResults();
    }

    public void setSnapshot(WorkerDashboardStateS2C.Snapshot snapshot) {
        if (snapshot == null) return;
        DashboardProtocolAccessors.SnapshotView incomingView =
                DashboardProtocolAccessors.snapshotView(snapshot);
        if (!acceptsSnapshot(snapshot, incomingView)) return;
        boolean configurationChanged = snapshot.configurationRevision() != this.snapshot.configurationRevision();
        this.snapshot = snapshot;
        this.snapshotView = incomingView;
        if (snapshot.configurationRevision() >= awaitingSnapshotRevision) awaitingSnapshotRevision = 0;
        if (configurationChanged) {
            if (amountBox != null && !amountBox.isFocused()) {
                amountBox.setValue(Integer.toString(snapshot.requestedCount()));
            }
            if (areaXBox != null && !areaXBox.isFocused()) areaXBox.setValue(Integer.toString(snapshot.workAreaCenter().getX()));
            if (areaYBox != null && !areaYBox.isFocused()) areaYBox.setValue(Integer.toString(snapshot.workAreaCenter().getY()));
            if (areaZBox != null && !areaZBox.isFocused()) areaZBox.setValue(Integer.toString(snapshot.workAreaCenter().getZ()));
            if (areaHorizontalBox != null && !areaHorizontalBox.isFocused()) areaHorizontalBox.setValue(Integer.toString(snapshot.horizontalRadius()));
            if (areaVerticalBox != null && !areaVerticalBox.isFocused()) areaVerticalBox.setValue(Integer.toString(snapshot.verticalRadius()));
            if (!selectedZoneId.isBlank()
                    && snapshot.noWorkZones().stream().noneMatch(zone -> zone.id().equals(selectedZoneId))) {
                selectedZoneId = "";
            }
            if (zoneNameBox != null && !zoneNameBox.isFocused()
                    && !zoneXBox.isFocused() && !zoneYBox.isFocused() && !zoneZBox.isFocused()
                    && !zoneHorizontalBox.isFocused() && !zoneVerticalBox.isFocused()) {
                loadSelectedZone();
            }
        }
        clampScrollOffsets();
        updateButtons(configurationChanged);
    }

    public void setAcknowledgement(WorkerActionAcknowledgementS2C acknowledgement) {
        if (acknowledgement == null) return;
        UUID requestId = DashboardProtocolAccessors.requestId(acknowledgement);
        if (pendingRequestId == null || requestId == null || !pendingRequestId.equals(requestId)) return;
        pendingRequestId = null;
        requestPending = false;
        awaitingSnapshotRevision = Math.max(
                awaitingSnapshotRevision, acknowledgement.configurationRevision());
        String message = Component.translatable(acknowledgement.translationKey()).getString();
        lastAcknowledgement = acknowledgement.success() ? "✓ " + message : "! " + message;
        if (DashboardProtocolAccessors.isPickupAcknowledgement(acknowledgement)) {
            String rawMessage = DashboardProtocolAccessors.acknowledgementText(acknowledgement);
            Component pickupMessage = rawMessage.startsWith("message.")
                    ? Component.translatable(rawMessage) : Component.literal(rawMessage);
            pickupConfirmation = (acknowledgement.success() ? "✓ " : "! ") + pickupMessage.getString();
            if (acknowledgement.success()) onClose();
        }
    }

    private boolean acceptsSnapshot(
            WorkerDashboardStateS2C.Snapshot incoming,
            DashboardProtocolAccessors.SnapshotView incomingView) {
        UUID currentUuid = snapshotView.workerUuid();
        UUID incomingUuid = incomingView.workerUuid();
        if (currentUuid == null || incomingUuid == null || !currentUuid.equals(incomingUuid)) return false;
        return incomingView.stateSequence() > snapshotView.stateSequence();
    }

    @Override
    protected void init() {
        pathingButtons.clear();
        uiScale = Math.min(MAX_UI_SCALE, Math.min(
                width / (float) LOGICAL_WIDTH, height / (float) LOGICAL_HEIGHT));
        if (uiScale <= 0.0F) uiScale = 1.0F;
        logicalWidth = Math.max(LOGICAL_WIDTH, Math.round(width / uiScale));
        logicalHeight = Math.max(LOGICAL_HEIGHT, Math.round(height / uiScale));
        panelWidth = Math.min(760, Math.max(320, logicalWidth - 20));
        panelHeight = Math.min(460, Math.max(300, logicalHeight - 20));
        panelLeft = (logicalWidth - panelWidth) / 2;
        panelTop = (logicalHeight - panelHeight) / 2;
        int tabY = panelTop + 10;
        int tabWidth = Math.max(50, (panelWidth - 30) / 5);
        jobTab = addRenderableWidget(Button.builder(Component.translatable("screen.baritonehelper.tab_job"), b -> selectTab(TAB_JOB))
                .bounds(panelLeft + 10, tabY, tabWidth, 20).build());
        worldTab = addRenderableWidget(Button.builder(Component.translatable("screen.baritonehelper.tab_world"), b -> selectTab(TAB_WORLD))
                .bounds(panelLeft + 12 + tabWidth, tabY, tabWidth, 20).build());
        storageTab = addRenderableWidget(Button.builder(Component.translatable("screen.baritonehelper.tab_storage"), b -> selectTab(TAB_STORAGE))
                .bounds(panelLeft + 14 + tabWidth * 2, tabY, tabWidth, 20).build());
        pathingTab = addRenderableWidget(Button.builder(Component.translatable("screen.baritonehelper.tab_pathing"), b -> selectTab(TAB_PATHING))
                .bounds(panelLeft + 16 + tabWidth * 3, tabY, tabWidth, 20).build());
        logTab = addRenderableWidget(Button.builder(Component.translatable("screen.baritonehelper.tab_log"), b -> selectTab(TAB_LOG))
                .bounds(panelLeft + 18 + tabWidth * 4, tabY, tabWidth, 20).build());

        searchBox = addRenderableWidget(new EditBox(font, panelLeft + 18, panelTop + 68,
                Math.min(270, panelWidth - 250), 20, Component.translatable("screen.baritonehelper.search")));
        searchBox.setHint(Component.translatable("screen.baritonehelper.search_hint"));
        searchBox.setResponder(value -> {
            pickerOffset = 0;
            refreshPickerResults();
        });
        amountBox = addRenderableWidget(new EditBox(font, panelLeft + 18, panelTop + 258, 88, 20,
                Component.translatable("screen.baritonehelper.amount")));
        amountBox.setValue(Integer.toString(snapshot.requestedCount()));
        amountBox.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        areaXBox = areaBox(panelLeft + 18, panelTop + 136, snapshot.workAreaCenter().getX());
        areaYBox = areaBox(panelLeft + 96, panelTop + 136, snapshot.workAreaCenter().getY());
        areaZBox = areaBox(panelLeft + 174, panelTop + 136, snapshot.workAreaCenter().getZ());
        areaHorizontalBox = areaBox(panelLeft + 18, panelTop + 170, snapshot.horizontalRadius());
        areaVerticalBox = areaBox(panelLeft + 126, panelTop + 170, snapshot.verticalRadius());
        int zoneLeft = rightColumnLeft();
        int zoneWidth = rightColumnWidth();
        zoneNameBox = addRenderableWidget(new EditBox(font, zoneLeft, panelTop + 90, zoneWidth, 20,
                Component.translatable("screen.baritonehelper.zone_name")));
        zoneNameBox.setMaxLength(64);
        zoneXBox = zoneBox(zoneLeft, panelTop + 120, snapshot.workAreaCenter().getX());
        zoneYBox = zoneBox(zoneLeft + zoneBoxWidth() + 4, panelTop + 120, snapshot.workAreaCenter().getY());
        zoneZBox = zoneBox(zoneLeft + (zoneBoxWidth() + 4) * 2, panelTop + 120, snapshot.workAreaCenter().getZ());
        zoneHorizontalBox = zoneBox(zoneLeft, panelTop + 150, 8);
        zoneVerticalBox = zoneBox(zoneLeft + zoneBoxWidth() + 4, panelTop + 150, 4);
        zoneModeButton = addRenderableWidget(Button.builder(zoneModeLabel(), b -> {
            zoneMode = zoneMode == 0 ? 1 : 0;
            b.setMessage(zoneModeLabel());
        }).bounds(zoneLeft, panelTop + 176, zoneWidth, 20).build());
        zoneEnabledButton = addRenderableWidget(Button.builder(zoneEnabledLabel(), b -> {
            zoneEnabled = !zoneEnabled;
            b.setMessage(zoneEnabledLabel());
        }).bounds(zoneLeft, panelTop + 198, zoneWidth, 20).build());
        workAreaModeButton = addRenderableWidget(Button.builder(
                modeButtonLabel(WORK_AREA_MODE), b -> setSearchMode(WORK_AREA_MODE))
                .bounds(panelLeft + 18, panelTop + 284, 136, 20).build());
        roamModeButton = addRenderableWidget(Button.builder(
                modeButtonLabel(ROAM_MODE), b -> setSearchMode(ROAM_MODE))
                .bounds(panelLeft + 160, panelTop + 284, 136, 20).build());

        startButton = addRenderableWidget(Button.builder(Component.translatable("button.baritonehelper.start"), b -> send(request(WorkerDashboardActionC2S.Action.START)))
                .bounds(0, 0, 92, 20).build());
        stopButton = addRenderableWidget(Button.builder(Component.translatable("button.baritonehelper.stop"), b -> send(request(WorkerDashboardActionC2S.Action.STOP)))
                .bounds(0, 0, 92, 20).build());
        inventoryButton = addRenderableWidget(Button.builder(Component.translatable("button.baritonehelper.inventory"), b -> send(request(WorkerDashboardActionC2S.Action.OPEN_INVENTORY)))
                .bounds(0, 0, 104, 20).build());
        clearTargetButton = addRenderableWidget(Button.builder(Component.translatable("button.baritonehelper.clear_target"), b -> send(request(WorkerDashboardActionC2S.Action.CLEAR_TARGET)))
                .bounds(0, 0, 112, 20).build());
        clearStorageButton = addRenderableWidget(Button.builder(Component.translatable("button.baritonehelper.clear_storage"), b -> send(request(WorkerDashboardActionC2S.Action.CLEAR_STORAGE)))
                .bounds(0, 0, 112, 20).build());
        exclusionButton = addRenderableWidget(Button.builder(Component.translatable("screen.baritonehelper.toggle_exclusion"), b -> {
            if (!snapshot.targetBlockId().isBlank()) send(request(
                    WorkerDashboardActionC2S.Action.TOGGLE_EXCLUSION, snapshot.targetBlockId(), 0, true,
                    BlockPos.ZERO, 0, 0));
        }).bounds(0, 0, 120, 20).build());
        pickupButton = addRenderableWidget(Button.builder(
                Component.translatable("button.baritonehelper.pickup"),
                b -> send(request(WorkerDashboardActionC2S.Action.PICKUP)))
                .bounds(0, 0, 96, 20).build());
        amountModeButton = addRenderableWidget(Button.builder(modeLabel(), b -> send(request(
                Integer.parseInt(safeAmount()), !snapshot.unlimitedCount())))
                .bounds(panelLeft + 112, panelTop + 258, 108, 20).build());
        applyAmountButton = addRenderableWidget(Button.builder(Component.translatable("screen.baritonehelper.apply"), b -> send(request(
                Integer.parseInt(safeAmount()), snapshot.unlimitedCount())))
                .bounds(panelLeft + 224, panelTop + 258, 70, 20).build());
        resetProgressButton = addRenderableWidget(Button.builder(Component.translatable("screen.baritonehelper.reset_progress"), b -> send(request(
                WorkerDashboardActionC2S.Action.RESET_PROGRESS)))
                .bounds(panelLeft + 18, panelTop + 282, 276, 20).build());
        applyAreaButton = addRenderableWidget(Button.builder(Component.translatable("screen.baritonehelper.apply"), b -> send(request(
                areaPosition(), areaRadius(areaHorizontalBox), areaRadius(areaVerticalBox))))
                .bounds(panelLeft + 18, panelTop + 196, 70, 20).build());
        usePlayerAreaButton = addRenderableWidget(Button.builder(Component.translatable("screen.baritonehelper.use_player"), b -> {
            if (minecraft != null && minecraft.player != null) {
                send(request(minecraft.player.blockPosition(), areaRadius(areaHorizontalBox), areaRadius(areaVerticalBox)));
            }
        }).bounds(panelLeft + 94, panelTop + 196, 112, 20).build());
        useWorkerAreaButton = addRenderableWidget(Button.builder(Component.translatable("screen.baritonehelper.use_worker"), b -> {
            var worker = DashboardProtocolAccessors.clientEntity(snapshotView);
            if (worker != null) {
                send(request(worker.blockPosition(),
                        areaRadius(areaHorizontalBox), areaRadius(areaVerticalBox)));
            }
        }).bounds(panelLeft + 212, panelTop + 196, 112, 20).build());
        selectAreaPointButton = addRenderableWidget(Button.builder(Component.translatable("screen.baritonehelper.select_point"), b -> send(request(
                WorkerDashboardActionC2S.Action.ARM_AREA_SELECTION)))
                .bounds(panelLeft + 330, panelTop + 196, 112, 20).build());
        clearAreaButton = addRenderableWidget(Button.builder(Component.translatable("screen.baritonehelper.clear_area"), b -> send(request(
                WorkerDashboardActionC2S.Action.CLEAR_WORK_AREA)))
                .bounds(panelLeft + 330, panelTop + 218, 112, 20).build());
        areaPresetButtons.clear();
        int[] horizontalPresets = {32, 64, 128, 256, 512};
        int[] verticalPresets = {16, 32, 64, 128};
        for (int i = 0; i < horizontalPresets.length; i++) {
            areaPresetButtons.add(addAreaPresetButton(horizontalPresets[i], true, i));
        }
        for (int i = 0; i < verticalPresets.length; i++) {
            areaPresetButtons.add(addAreaPresetButton(verticalPresets[i], false, i));
        }
        addZoneButton = addRenderableWidget(Button.builder(Component.translatable("screen.baritonehelper.add_zone"), b -> {
            selectedZoneId = "";
            resetZoneEditor();
            updateButtons();
        }).bounds(zoneLeft, panelTop + 220, zoneWidth, 20).build());
        saveZoneButton = addRenderableWidget(Button.builder(Component.translatable("screen.baritonehelper.save_zone"), b -> saveZone())
                .bounds(zoneLeft, panelTop + 242, zoneWidth, 20).build());
        selectZonePointButton = addRenderableWidget(Button.builder(Component.translatable("screen.baritonehelper.select_zone_point"), b -> {
            if (!selectedZoneId.isBlank()) send(request(
                    WorkerDashboardActionC2S.Action.ARM_ZONE_SELECTION, selectedZoneId, 0, true,
                    BlockPos.ZERO, 0, 0));
        }).bounds(zoneLeft, panelTop + 264, zoneWidth, 20).build());
        toggleZoneButton = addRenderableWidget(Button.builder(Component.translatable("screen.baritonehelper.toggle_zone"), b -> {
            if (!selectedZoneId.isBlank()) send(request(
                    WorkerDashboardActionC2S.Action.TOGGLE_NO_WORK_ZONE, selectedZoneId, 0, true,
                    BlockPos.ZERO, 0, 0));
        }).bounds(zoneLeft, panelTop + 286, zoneWidth, 20).build());
        deleteZoneButton = addRenderableWidget(Button.builder(Component.translatable("screen.baritonehelper.delete_zone"), b -> {
            if (!selectedZoneId.isBlank()) send(request(
                    WorkerDashboardActionC2S.Action.DELETE_NO_WORK_ZONE, selectedZoneId, 0, true,
                    BlockPos.ZERO, 0, 0));
        }).bounds(zoneLeft, panelTop + 308, zoneWidth, 20).build());
        selectStorageButton = addRenderableWidget(Button.builder(Component.translatable("screen.baritonehelper.select_storage"), b -> send(request(
                WorkerDashboardActionC2S.Action.ARM_STORAGE_SELECTION)))
                .bounds(panelLeft + 18, panelTop + 196, 140, 20).build());
        addPathingButton("breaking", "screen.baritonehelper.allow_breaking", snapshot.allowBreakingObstructions(), 0);
        addPathingButton("placement", "screen.baritonehelper.allow_placement", snapshot.allowBlockPlacement(), 1);
        addPathingButton("bridging", "screen.baritonehelper.allow_bridging", snapshot.allowBridging(), 2);
        addPathingButton("pillaring", "screen.baritonehelper.allow_pillaring", snapshot.allowPillaring(), 3);
        addPathingButton("parkour", "screen.baritonehelper.allow_parkour", snapshot.allowParkour(), 4);
        addPathingButton("water", "screen.baritonehelper.allow_water", snapshot.allowWaterRoutes(), 5);
        addPathingButton("safer", "screen.baritonehelper.prefer_safer", snapshot.preferSaferRoutes(), 6);
        addPathingButton("avoid_destructive", "screen.baritonehelper.avoid_destructive", snapshot.avoidDestructiveRouting(), 7);
        layoutFooter();
        selectTab(tab);
        updateButtons();
    }

    private void layoutFooter() {
        int innerLeft = panelLeft + 18;
        int innerWidth = panelWidth - 36;
        if (panelWidth < 700) {
            int gap = 6;
            int buttonWidth = (innerWidth - gap * 3) / 4;
            int firstRow = panelTop + panelHeight - 62;
            int secondRow = firstRow + 24;
            place(startButton, innerLeft, firstRow, buttonWidth, 20);
            place(stopButton, innerLeft + buttonWidth + gap, firstRow, buttonWidth, 20);
            place(inventoryButton, innerLeft + (buttonWidth + gap) * 2, firstRow, buttonWidth, 20);
            place(pickupButton, innerLeft + (buttonWidth + gap) * 3, firstRow, buttonWidth, 20);
            place(clearTargetButton, innerLeft, secondRow, buttonWidth, 20);
            place(clearStorageButton, innerLeft + buttonWidth + gap, secondRow, buttonWidth, 20);
            place(exclusionButton, innerLeft + (buttonWidth + gap) * 2, secondRow, buttonWidth, 20);
            return;
        }
        int x = innerLeft;
        int y = panelTop + panelHeight - 38;
        int gap = 6;
        int buttonWidth = (innerWidth - gap * 6) / 7;
        for (Button button : List.of(startButton, stopButton, inventoryButton, pickupButton,
                clearTargetButton, clearStorageButton, exclusionButton)) {
            x = place(button, x, y, buttonWidth, 20) + gap;
        }
    }

    private static int place(Button button, int x, int y, int width, int height) {
        button.setX(x);
        button.setY(y);
        button.setWidth(width);
        button.setHeight(height);
        return x + width;
    }

    private void setSearchMode(String mode) {
        send(request(WorkerDashboardActionC2S.Action.SET_SEARCH_MODE,
                mode, 0, true, BlockPos.ZERO, 0, 0));
    }

    private UUID workerUuid() {
        return snapshotView.workerUuid();
    }

    private String workerDimension() {
        return snapshotView.dimension();
    }

    private boolean samePhysicalDimension() {
        return minecraft != null && minecraft.player != null
                && workerDimension().equals(
                        minecraft.player.level().dimension().location().toString());
    }

    private WorkerDashboardActionC2S request(WorkerDashboardActionC2S.Action action) {
        return new WorkerDashboardActionC2S(workerUuid(), workerDimension(), revision(), action);
    }

    private WorkerDashboardActionC2S request(String blockId) {
        return new WorkerDashboardActionC2S(workerUuid(), workerDimension(), revision(), blockId);
    }

    private WorkerDashboardActionC2S request(int amount, boolean unlimited) {
        return new WorkerDashboardActionC2S(workerUuid(), workerDimension(), revision(), amount, unlimited);
    }

    private WorkerDashboardActionC2S request(String pathingKey, boolean enabled) {
        return new WorkerDashboardActionC2S(workerUuid(), workerDimension(), revision(), pathingKey, enabled);
    }

    private WorkerDashboardActionC2S request(BlockPos center, int horizontalRadius, int verticalRadius) {
        return new WorkerDashboardActionC2S(workerUuid(), workerDimension(), revision(), center,
                horizontalRadius, verticalRadius);
    }

    private WorkerDashboardActionC2S request(WorkerDashboardActionC2S.Action action, String value,
            int mode, boolean enabled, BlockPos center, int horizontalRadius, int verticalRadius) {
        return new WorkerDashboardActionC2S(workerUuid(), workerDimension(), revision(), action,
                value, mode, enabled, center, horizontalRadius, verticalRadius);
    }

    private Component modeButtonLabel(String mode) {
        boolean selected = snapshotView.searchMode()
                .map(value -> value.equalsIgnoreCase(mode))
                .orElse(mode.equals(WORK_AREA_MODE));
        return Component.translatable(mode.equals(ROAM_MODE)
                ? "screen.baritonehelper.roam"
                : "screen.baritonehelper.work_area_mode")
                .append(Component.literal(selected ? "  ✓" : ""));
    }

    private EditBox areaBox(int x, int y, int value) {
        EditBox box = addRenderableWidget(new EditBox(font, x, y, 70, 20, Component.empty()));
        box.setValue(Integer.toString(value));
        box.setFilter(DashboardInput::isSignedInteger);
        return box;
    }

    private Button addAreaPresetButton(int radius, boolean horizontal, int index) {
        String key = horizontal
                ? "screen.baritonehelper.horizontal_preset"
                : "screen.baritonehelper.vertical_preset";
        return addRenderableWidget(Button.builder(Component.translatable(key, radius), b -> {
            if (horizontal) areaHorizontalBox.setValue(Integer.toString(radius));
            else areaVerticalBox.setValue(Integer.toString(radius));
            send(request(areaPosition(), areaRadius(areaHorizontalBox), areaRadius(areaVerticalBox)));
        }).bounds(panelLeft + 18 + index * 48,
                panelTop + (horizontal ? 218 : 240), 45, 20).build());
    }

    private EditBox zoneBox(int x, int y, int value) {
        EditBox box = addRenderableWidget(new EditBox(font, x, y, zoneBoxWidth(), 20, Component.empty()));
        box.setValue(Integer.toString(value));
        box.setFilter(DashboardInput::isSignedInteger);
        return box;
    }

    private int rightColumnLeft() {
        return panelLeft + Math.max(150, Math.min(450, panelWidth - 170));
    }

    private int rightColumnWidth() {
        return Math.max(120, panelLeft + panelWidth - 18 - rightColumnLeft());
    }

    private int zoneBoxWidth() {
        return Math.max(32, Math.min(70, (rightColumnWidth() - 8) / 3));
    }

    private Component zoneModeLabel() {
        return Component.translatable(zoneMode == 0
                ? "screen.baritonehelper.no_modify"
                : "screen.baritonehelper.no_enter");
    }

    private Component zoneEnabledLabel() {
        return Component.translatable(zoneEnabled
                ? "screen.baritonehelper.zone_enabled"
                : "screen.baritonehelper.zone_disabled");
    }

    private void resetZoneEditor() {
        if (zoneNameBox == null) return;
        zoneNameBox.setValue("");
        zoneXBox.setValue(Integer.toString(snapshot.workAreaCenter().getX()));
        zoneYBox.setValue(Integer.toString(snapshot.workAreaCenter().getY()));
        zoneZBox.setValue(Integer.toString(snapshot.workAreaCenter().getZ()));
        zoneHorizontalBox.setValue("8");
        zoneVerticalBox.setValue("4");
        zoneMode = 0;
        zoneEnabled = true;
        zoneModeButton.setMessage(zoneModeLabel());
        zoneEnabledButton.setMessage(zoneEnabledLabel());
    }

    private void loadSelectedZone() {
        if (zoneNameBox == null) return;
        var selected = snapshot.noWorkZones().stream()
                .filter(zone -> zone.id().equals(selectedZoneId)).findFirst().orElse(null);
        if (selected == null) {
            resetZoneEditor();
            return;
        }
        zoneNameBox.setValue(selected.name());
        zoneXBox.setValue(Integer.toString(selected.center().getX()));
        zoneYBox.setValue(Integer.toString(selected.center().getY()));
        zoneZBox.setValue(Integer.toString(selected.center().getZ()));
        zoneHorizontalBox.setValue(Integer.toString(selected.horizontalRadius()));
        zoneVerticalBox.setValue(Integer.toString(selected.verticalRadius()));
        zoneMode = selected.mode() == 1 ? 1 : 0;
        zoneEnabled = selected.enabled();
        zoneModeButton.setMessage(zoneModeLabel());
        zoneEnabledButton.setMessage(zoneEnabledLabel());
    }

    private int zoneInt(EditBox box, int fallback) {
        try { return Integer.parseInt(box.getValue()); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private void saveZone() {
        BlockPos center = new BlockPos(zoneInt(zoneXBox, snapshot.workAreaCenter().getX()),
                zoneInt(zoneYBox, snapshot.workAreaCenter().getY()),
                zoneInt(zoneZBox, snapshot.workAreaCenter().getZ()));
        int horizontal = Math.max(0, Math.min(512, zoneInt(zoneHorizontalBox, 8)));
        int vertical = Math.max(0, Math.min(128, zoneInt(zoneVerticalBox, 4)));
        if (selectedZoneId.isBlank()) {
            send(request(WorkerDashboardActionC2S.Action.ADD_NO_WORK_ZONE,
                    zoneNameBox.getValue(), zoneMode, zoneEnabled, center, horizontal, vertical));
        } else {
            send(request(WorkerDashboardActionC2S.Action.UPDATE_NO_WORK_ZONE,
                    selectedZoneId, zoneMode, zoneEnabled, center, horizontal, vertical));
        }
    }

    private int areaRadius(EditBox box) {
        try { return Integer.parseInt(box.getValue()); }
        catch (NumberFormatException ignored) { return 1; }
    }

    private BlockPos areaPosition() {
        return new BlockPos(areaRadius(areaXBox), areaRadius(areaYBox), areaRadius(areaZBox));
    }

    private int revision() { return snapshot.configurationRevision(); }

    private String safeAmount() {
        try {
            return Integer.toString(Math.max(1, Math.min(1_000_000, Integer.parseInt(amountBox.getValue()))));
        } catch (NumberFormatException ignored) {
            return Integer.toString(snapshot.requestedCount());
        }
    }

    private Component modeLabel() {
        return snapshot.unlimitedCount()
                ? Component.translatable("screen.baritonehelper.unlimited")
                : Component.translatable("screen.baritonehelper.finite");
    }

    private void selectTab(int tab) {
        this.tab = tab;
        if (searchBox == null) return;
        boolean job = tab == TAB_JOB;
        boolean world = tab == TAB_WORLD;
        boolean pathing = tab == TAB_PATHING;
        searchBox.visible = job;
        amountBox.visible = job;
        amountModeButton.visible = job;
        applyAmountButton.visible = job;
        resetProgressButton.visible = job;
        applyAreaButton.visible = world;
        areaXBox.visible = world;
        areaYBox.visible = world;
        areaZBox.visible = world;
        areaHorizontalBox.visible = world;
        areaVerticalBox.visible = world;
        zoneNameBox.visible = world;
        zoneXBox.visible = world;
        zoneYBox.visible = world;
        zoneZBox.visible = world;
        zoneHorizontalBox.visible = world;
        zoneVerticalBox.visible = world;
        usePlayerAreaButton.visible = world;
        useWorkerAreaButton.visible = world;
        selectAreaPointButton.visible = world;
        clearAreaButton.visible = world;
        for (Button button : areaPresetButtons) button.visible = world;
        workAreaModeButton.visible = world;
        roamModeButton.visible = world;
        selectStorageButton.visible = tab == TAB_STORAGE;
        addZoneButton.visible = world;
        saveZoneButton.visible = world;
        selectZonePointButton.visible = world;
        toggleZoneButton.visible = world;
        deleteZoneButton.visible = world;
        zoneModeButton.visible = world;
        zoneEnabledButton.visible = world;
        for (Button button : pathingButtons) button.visible = pathing;
        updateButtons();
    }

    private void addPathingButton(String key, String label, boolean enabled, int row) {
        Button button = addRenderableWidget(Button.builder(pathingLabel(label, enabled), b -> {
            send(request(key, !pathingEnabled(key)));
        }).bounds(panelLeft + 18, panelTop + 62 + row * 22, Math.min(250, panelWidth - 36), 20).build());
        pathingButtons.add(button);
    }

    private boolean pathingEnabled(String key) {
        return switch (key) {
            case "breaking" -> snapshot.allowBreakingObstructions();
            case "placement" -> snapshot.allowBlockPlacement();
            case "bridging" -> snapshot.allowBridging();
            case "pillaring" -> snapshot.allowPillaring();
            case "parkour" -> snapshot.allowParkour();
            case "water" -> snapshot.allowWaterRoutes();
            case "safer" -> snapshot.preferSaferRoutes();
            case "avoid_destructive" -> snapshot.avoidDestructiveRouting();
            default -> false;
        };
    }

    private Component pathingLabel(String key, boolean enabled) {
        return Component.translatable(key).append(Component.literal(enabled ? "  ON" : "  OFF"));
    }

    private void updateButtons() {
        updateButtons(true);
    }

    private void updateButtons(boolean configurationChanged) {
        if (startButton == null) return;
        WorkerJob job = enumValue(WorkerJob.values(), snapshot.job(), WorkerJob.IDLE);
        boolean running = job.activelyWorks();
        boolean local = samePhysicalDimension();
        startButton.active = !snapshot.targetBlockId().isBlank() && !running;
        stopButton.active = running || job == WorkerJob.BLOCKED;
        inventoryButton.active = local;
        clearTargetButton.active = !snapshot.targetBlockId().isBlank();
        clearStorageButton.active = snapshot.hasStorage();
        exclusionButton.active = !snapshot.targetBlockId().isBlank();
        pickupButton.active = !requestPending
                && awaitingSnapshotRevision <= snapshot.configurationRevision();
        resetProgressButton.active = !snapshot.targetBlockId().isBlank() && snapshot.completedCount() > 0;
        usePlayerAreaButton.active = local;
        useWorkerAreaButton.active = local
                && DashboardProtocolAccessors.clientEntity(snapshotView) != null;
        selectAreaPointButton.active = local;
        selectStorageButton.active = local;
        addZoneButton.active = snapshot.noWorkZones().size() < 128;
        saveZoneButton.active = !selectedZoneId.isBlank()
                || snapshot.noWorkZones().size() < 128;
        selectZonePointButton.active = local && !selectedZoneId.isBlank();
        toggleZoneButton.active = !selectedZoneId.isBlank();
        deleteZoneButton.active = !selectedZoneId.isBlank();
        workAreaModeButton.active = true;
        roamModeButton.active = true;
        workAreaModeButton.setMessage(modeButtonLabel(WORK_AREA_MODE));
        roamModeButton.setMessage(modeButtonLabel(ROAM_MODE));
        if (!configurationChanged) return;
        zoneModeButton.setMessage(zoneModeLabel());
        zoneEnabledButton.setMessage(zoneEnabledLabel());
        amountModeButton.setMessage(modeLabel());
        if (pathingButtons.size() == 8) {
            String[] keys = {"breaking", "placement", "bridging", "pillaring", "parkour", "water", "safer", "avoid_destructive"};
            String[] labels = {"screen.baritonehelper.allow_breaking", "screen.baritonehelper.allow_placement", "screen.baritonehelper.allow_bridging", "screen.baritonehelper.allow_pillaring", "screen.baritonehelper.allow_parkour", "screen.baritonehelper.allow_water", "screen.baritonehelper.prefer_safer", "screen.baritonehelper.avoid_destructive"};
            for (int i = 0; i < keys.length; i++) pathingButtons.get(i).setMessage(pathingLabel(labels[i], pathingEnabled(keys[i])));
        }
    }

    private void send(WorkerDashboardActionC2S action) {
        if (requestPending || awaitingSnapshotRevision > snapshot.configurationRevision()) return;
        UUID requestId = DashboardProtocolAccessors.requestId(action);
        if (requestId == null) return;
        pendingRequestId = requestId;
        requestPending = true;
        PacketDistributor.sendToServer(action);
    }

    private void refreshPickerResults() {
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(java.util.Locale.ROOT);
        pickerResults = BuiltInRegistries.BLOCK.entrySet().stream()
                .filter(entry -> entry.getValue() != Blocks.AIR)
                .map(entry -> entry.getKey().location())
                .filter(id -> query.isEmpty() || id.toString().toLowerCase(java.util.Locale.ROOT).contains(query)
                        || BuiltInRegistries.BLOCK.get(id).getName().getString().toLowerCase(java.util.Locale.ROOT).contains(query))
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double logicalMouseX = mouseX / uiScale;
        double logicalMouseY = mouseY / uiScale;
        if (tab == TAB_JOB && searchBox != null && logicalMouseX >= panelLeft + 300
                && logicalMouseX < panelLeft + panelWidth - 18) {
            int row = (int) ((logicalMouseY - (panelTop + 94)) / 20);
            if (row >= 0 && row < PICKER_ROWS) {
                int index = pickerOffset + row;
                if (index >= 0 && index < pickerResults.size()) {
                    send(request(pickerResults.get(index).toString()));
                    return true;
                }
            }
        }
        if (tab == TAB_WORLD && logicalMouseX >= panelLeft + 18 && logicalMouseX < panelLeft + panelWidth - 18
                && logicalMouseY >= zoneListTop() && logicalMouseY < footerTop() - 22) {
            int row = (int) ((logicalMouseY - zoneListTop()) / 18);
            int index = zoneOffset + row;
            if (row >= 0 && row < visibleZoneRows()
                    && index < snapshot.noWorkZones().size()) {
                selectedZoneId = snapshot.noWorkZones().get(index).id();
                loadSelectedZone();
                updateButtons();
                return true;
            }
        }
        return super.mouseClicked(logicalMouseX, logicalMouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        double logicalMouseX = mouseX / uiScale;
        double logicalMouseY = mouseY / uiScale;
        if (tab == TAB_JOB && logicalMouseX >= panelLeft + 300) {
            int max = Math.max(0, pickerResults.size() - PICKER_ROWS);
            pickerOffset = Math.max(0, Math.min(max, pickerOffset + (deltaY > 0 ? -1 : 1)));
            return true;
        }
        if (tab == TAB_WORLD && logicalMouseX >= panelLeft + 18
                && logicalMouseX < panelLeft + panelWidth - 18
                && logicalMouseY >= zoneListTop() && logicalMouseY < footerTop() - 22) {
            int max = Math.max(0, snapshot.noWorkZones().size() - visibleZoneRows());
            zoneOffset = Math.max(0, Math.min(max, zoneOffset + (deltaY > 0 ? -1 : 1)));
            return true;
        }
        if (tab == TAB_LOG && logicalMouseX >= panelLeft + 18
                && logicalMouseX < panelLeft + panelWidth - 18
                && logicalMouseY >= panelTop + 56 && logicalMouseY < footerTop() - 8) {
            int max = Math.max(0, snapshot.activityHistory().size() - visibleActivityRows());
            activityOffset = Math.max(0, Math.min(max,
                    activityOffset + (deltaY > 0 ? -1 : 1)));
            return true;
        }
        return super.mouseScrolled(logicalMouseX, logicalMouseY, deltaX, deltaY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.pose().pushPose();
        graphics.pose().scale(uiScale, uiScale, 1.0F);
        int logicalMouseX = Math.round(mouseX / uiScale);
        int logicalMouseY = Math.round(mouseY / uiScale);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 0xF010151C);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 3, 0xFF4B8BBE);
        renderContent(graphics);
        super.render(graphics, logicalMouseX, logicalMouseY, partialTick);
        renderFeedback(graphics);
        graphics.pose().popPose();
    }

    private void renderContent(GuiGraphics graphics) {
        renderHeader(graphics);
        if (tab == TAB_JOB) renderJob(graphics);
        else if (tab == TAB_WORLD) renderWorld(graphics);
        else if (tab == TAB_STORAGE) renderStorage(graphics);
        else if (tab == TAB_PATHING) renderPathing(graphics);
        else renderLog(graphics);
    }

    private void renderHeader(GuiGraphics graphics) {
        WorkerJob job = enumValue(WorkerJob.values(), snapshot.job(), WorkerJob.IDLE);
        WorkerActivity activity = enumValue(WorkerActivity.values(), snapshot.activity(), WorkerActivity.IDLE);
        WorkerBlockReason reason = enumValue(WorkerBlockReason.values(), snapshot.blockReason(), WorkerBlockReason.NONE);
        WorkerRuntimeState runtime = enumValue(WorkerRuntimeState.values(), snapshot.runtimeState(), WorkerRuntimeState.UNCONFIGURED);
        int x = panelLeft + 18;
        int right = panelLeft + panelWidth - 18;
        graphics.drawString(font, title, x, panelTop + 36, 0xFFFFFF);
        String state = Component.translatable("screen.baritonehelper.job", Component.translatable(jobKey(job)))
                .append(Component.literal("  "))
                .append(Component.translatable("screen.baritonehelper.activity", Component.translatable(activity.translationKey())))
                .getString();
        int stateWidth = right - (x + 128) - (requestPending ? 90 : 0);
        graphics.drawString(font, font.plainSubstrByWidth(state, Math.max(80, stateWidth)),
                x + 128, panelTop + 36, reason == WorkerBlockReason.NONE ? 0xD9E8F5 : 0xFF8D8D);
        if (requestPending) {
            graphics.drawString(font, Component.translatable("screen.baritonehelper.pending"),
                    right - 82, panelTop + 36, 0xE6D4B8);
        }
        String worker = snapshotView.workerUuid() == null
                ? "-"
                : snapshotView.workerUuid().toString();
        String identity = Component.translatable("screen.baritonehelper.worker_identity", worker,
                snapshotView.dimension(), snapshotView.stateSequence()).getString()
                + " | " + Component.translatable("screen.baritonehelper.runtime", runtime.name()).getString();
        if (reason != WorkerBlockReason.NONE) {
            identity += " | " + Component.translatable("screen.baritonehelper.reason",
                    Component.translatable(reason.translationKey())).getString();
        }
        graphics.drawString(font, font.plainSubstrByWidth(identity, right - x), x, panelTop + 48, 0x9FB1C1);
    }

    private void renderFeedback(GuiGraphics graphics) {
        int x = panelLeft + 18;
        int width = panelWidth - 36;
        if (!pickupConfirmation.isBlank()) {
            graphics.drawString(font, font.plainSubstrByWidth(pickupConfirmation, width), x,
                    footerTop() - 28, pickupConfirmation.startsWith("!") ? 0xFF8D8D : 0xB8E6B8);
        }
        if (!lastAcknowledgement.isBlank()) {
            graphics.drawString(font, font.plainSubstrByWidth(lastAcknowledgement, width), x,
                    footerTop() - 16, lastAcknowledgement.startsWith("!") ? 0xFF8D8D : 0xB8E6B8);
        }
    }

    private void renderJob(GuiGraphics graphics) {
        int x = panelLeft + 18;
        graphics.fill(x, panelTop + 56, panelLeft + 294, panelTop + 186, 0xFF18212C);
        graphics.fill(panelLeft + 300, panelTop + 56, panelLeft + panelWidth - 18, panelTop + 276, 0xFF18212C);
        String target = snapshot.targetBlockId().isBlank()
                ? Component.translatable("screen.baritonehelper.not_set").getString()
                : snapshot.targetBlockId();
        graphics.drawString(font, fit(Component.translatable("screen.baritonehelper.target", target), 260),
                x + 8, panelTop + 58, 0xD9E8F5);
        graphics.drawString(font, fit(pickupLine(), 260), x + 8, panelTop + 80, 0xB8E6B8);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.progress", snapshot.completedCount(),
                snapshot.unlimitedCount() ? "∞" : Integer.toString(snapshot.requestedCount())), x + 8, panelTop + 102, 0xD9E8F5);
        drawProgressBar(graphics, x + 8, panelTop + 116, 260, 7,
                snapshot.completedCount(), snapshot.unlimitedCount() ? Math.max(1, snapshot.completedCount()) : snapshot.requestedCount());
        graphics.drawString(font, Component.translatable("screen.baritonehelper.inventory", snapshot.usedSlots(),
                snapshot.capacity(), snapshot.itemCount()), x + 8, panelTop + 136, 0xC5D4E0);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.diagnostics", snapshot.ticketCount(),
                snapshot.simulationTicketCount(), snapshot.searchTicketCount(), snapshot.totalTicketCount(), snapshot.replanAttempts(),
                snapshot.lastProgressAgeTicks()), x + 8, panelTop + 154, 0x9FB1C1);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.picker"), panelLeft + 308, panelTop + 58, 0xD9E8F5);
        for (int row = 0; row < PICKER_ROWS; row++) {
            int index = pickerOffset + row;
            if (index >= pickerResults.size()) break;
            ResourceLocation id = pickerResults.get(index);
            int y = panelTop + 94 + row * 20;
            boolean selected = id.toString().equals(snapshot.targetBlockId());
            if (selected) graphics.fill(panelLeft + 304, y - 2, panelLeft + panelWidth - 22, y + 16, 0xFF315A73);
            var item = BuiltInRegistries.BLOCK.get(id).asItem();
            if (item != Items.AIR) {
                graphics.renderFakeItem(new ItemStack(item), panelLeft + 308, y - 3);
            }
            String displayId = font.plainSubstrByWidth(id.toString(), Math.max(40, panelWidth - 360));
            graphics.drawString(font, displayId, panelLeft + 332, y, selected ? 0xFFFFFF : 0xC5D4E0);
        }
        graphics.drawString(font, Component.translatable("screen.baritonehelper.amount"), x, panelTop + 246, 0x9FB1C1);
        renderTelemetry(graphics, x, panelTop + 308, 276, 84);
    }

    private Component pickupLine() {
        if (!pickupConfirmation.isBlank()) {
            return Component.translatable("screen.baritonehelper.pickup_confirmed", pickupConfirmation);
        }
        WorkerActivity activity = enumValue(WorkerActivity.values(), snapshot.activity(), WorkerActivity.IDLE);
        String status = snapshotView.pickupStatus().orElseGet(() -> activity == WorkerActivity.COLLECTING
                ? "collecting" : "cargo");
        return Component.translatable("screen.baritonehelper.pickup", status, snapshot.itemCount());
    }

    private void renderTelemetry(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF18212C);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.telemetry"), x + 8, y + 8, 0xD9E8F5);
        graphics.drawString(font, fit(Component.translatable("screen.baritonehelper.search_progress",
                snapshot.frontierIndex(), snapshot.frontierSize()), width - 16), x + 8, y + 23, 0xC5D4E0);
        drawProgressBar(graphics, x + 8, y + 35, width - 16, 6, snapshot.frontierIndex(), snapshot.frontierSize());
        graphics.drawString(font, fit(Component.translatable("screen.baritonehelper.path_progress",
                snapshot.pathNode(), snapshot.pathLength(), snapshot.pathingStatus()), width - 16),
                x + 8, y + 47, 0xC5D4E0);
        drawProgressBar(graphics, x + 8, y + 59, width - 16, 6, snapshot.pathNode(), snapshot.pathLength());
        graphics.drawString(font, fit(Component.translatable("screen.baritonehelper.telemetry_counts",
                snapshot.chunksExamined(), snapshot.positionsExamined(), snapshot.matchingBlocks(),
                snapshot.candidatesFound()), width - 16), x + 8, y + 71, 0x9FB1C1);
    }

    private void drawProgressBar(GuiGraphics graphics, int x, int y, int width, int height,
            int value, int maximum) {
        graphics.fill(x, y, x + width, y + height, 0xFF0D1117);
        double ratio = maximum <= 0 ? 0.0D : Math.max(0.0D, Math.min(1.0D, value / (double) maximum));
        graphics.fill(x, y, x + (int) Math.round(width * ratio), y + height, 0xFF4B8BBE);
    }

    private Component fit(Component component, int width) {
        return Component.literal(font.plainSubstrByWidth(component.getString(), width));
    }

    private void renderWorld(GuiGraphics graphics) {
        int x = panelLeft + 18;
        graphics.fill(x, panelTop + 56, panelLeft + panelWidth - 18, footerTop() - 8, 0xFF18212C);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.work_area"), x + 8, panelTop + 66, 0xD9E8F5);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.dimension", snapshot.workAreaDimension()), x + 8, panelTop + 86, 0xC5D4E0);
        if (!samePhysicalDimension()) {
            graphics.drawString(font, fit(Component.translatable(
                    "screen.baritonehelper.remote_physical_limit"), 420), x + 8,
                    panelTop + 102, 0xE6D4B8);
        }
        graphics.drawString(font, Component.translatable("screen.baritonehelper.x"), x + 2, panelTop + 126, 0x9FB1C1);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.y"), x + 80, panelTop + 126, 0x9FB1C1);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.z"), x + 158, panelTop + 126, 0x9FB1C1);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.horizontal"), x + 2, panelTop + 160, 0x9FB1C1);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.vertical"), x + 110, panelTop + 160, 0x9FB1C1);
        int chunkSpan = snapshot.horizontalRadius() * 2 / 16 + 1;
        int detailsOffset = 20;
        graphics.drawString(font, Component.translatable("screen.baritonehelper.search_mode",
                snapshotView.searchMode().orElse(WORK_AREA_MODE)), x + 8, panelTop + 264, 0xC5D4E0);
        graphics.drawString(font, fit(Component.translatable("screen.baritonehelper.destination",
                snapshot.hasCurrentWorkPosition() ? formatPos(snapshot.currentWorkPosition())
                        : Component.translatable("screen.baritonehelper.none")), 260), x + 8,
                panelTop + 270 + detailsOffset, 0xD9E8F5);
        graphics.drawString(font, fit(Component.translatable("screen.baritonehelper.storage",
                snapshot.hasStorage() ? formatPos(snapshot.storagePosition())
                        : Component.translatable("screen.baritonehelper.not_set")), 260), x + 8,
                panelTop + 286 + detailsOffset, 0xD9E8F5);
        graphics.drawString(font, fit(Component.translatable("screen.baritonehelper.area_coverage",
                chunkSpan * chunkSpan, chunkSpan, chunkSpan, snapshot.verticalRadius()), 260), x + 8,
                panelTop + 302 + detailsOffset, 0xC5D4E0);
        int zoneLeft = rightColumnLeft();
        graphics.drawString(font, Component.translatable("screen.baritonehelper.zone_editor"), zoneLeft, panelTop + 78, 0xD9E8F5);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.zone_name"), zoneLeft, panelTop + 86, 0x8996A3);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.zone_center"), zoneLeft, panelTop + 114, 0x8996A3);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.zone_radii"), zoneLeft, panelTop + 144, 0x8996A3);
        int zoneListTop = zoneListTop();
        graphics.drawString(font, Component.translatable("screen.baritonehelper.no_work_zone_list"), x + 8, zoneListTop - 18, 0xD9E8F5);
        for (int row = 0; row < visibleZoneRows(); row++) {
            int index = zoneOffset + row;
            if (index >= snapshot.noWorkZones().size()) break;
            var zone = snapshot.noWorkZones().get(index);
            int y = zoneListTop + row * 18;
            if (zone.id().equals(selectedZoneId)) graphics.fill(x + 4, y - 2, panelLeft + panelWidth - 22, y + 14, 0xFF315A73);
            String name = zone.name().isBlank() ? zone.id() : zone.name();
            String mode = zone.mode() == 0 ? "NO_MODIFY" : "NO_ENTER";
            graphics.drawString(font, font.plainSubstrByWidth(name + " (" + mode + ")", panelWidth - 50), x + 8, y, 0xC5D4E0);
        }
    }

    private int visibleZoneRows() {
        return Math.max(0, Math.min(8, (footerTop() - zoneListTop() - 22) / 18));
    }

    private int zoneListTop() {
        return panelTop + 342;
    }

    private int visibleActivityRows() {
        return Math.max(1, Math.min(12, (footerTop() - panelTop - 104) / 18));
    }

    private void clampScrollOffsets() {
        zoneOffset = Math.max(0, Math.min(zoneOffset,
                Math.max(0, snapshot.noWorkZones().size() - visibleZoneRows())));
        activityOffset = Math.max(0, Math.min(activityOffset,
                Math.max(0, snapshot.activityHistory().size() - visibleActivityRows())));
    }

    private int footerTop() {
        return panelTop + panelHeight - (panelWidth < 700 ? 62 : 38);
    }

    private void renderStorage(GuiGraphics graphics) {
        int x = panelLeft + 18;
        graphics.fill(x, panelTop + 56, panelLeft + panelWidth - 18, panelTop + 226, 0xFF18212C);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.storage_inventory"), x + 8, panelTop + 66, 0xD9E8F5);
        graphics.drawString(font, fit(Component.translatable("screen.baritonehelper.storage",
                snapshot.hasStorage() ? formatPos(snapshot.storagePosition())
                        : Component.translatable("screen.baritonehelper.not_set")), panelWidth - 52), x + 8,
                panelTop + 88, 0xC5D4E0);
        graphics.drawString(font, fit(Component.translatable("screen.baritonehelper.storage_dimension",
                snapshot.storageDimension()), panelWidth - 52), x + 8, panelTop + 106, 0xC5D4E0);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.inventory", snapshot.usedSlots(), snapshot.capacity(), snapshot.itemCount()), x + 8, panelTop + 136, 0xD9E8F5);
        drawProgressBar(graphics, x + 8, panelTop + 152, panelWidth - 52, 7, snapshot.usedSlots(), snapshot.capacity());
        graphics.drawString(font, fit(pickupLine(), panelWidth - 52), x + 8, panelTop + 168, 0xB8E6B8);
        graphics.drawString(font, Component.translatable(samePhysicalDimension()
                ? "screen.baritonehelper.storage_hint"
                : "screen.baritonehelper.remote_physical_limit"), x + 8, panelTop + 188,
                samePhysicalDimension() ? 0x8996A3 : 0xE6D4B8);
    }

    private void renderPathing(GuiGraphics graphics) {
        int x = panelLeft + 286;
        int width = panelLeft + panelWidth - 18 - x;
        graphics.fill(x, panelTop + 56, panelLeft + panelWidth - 18, panelTop + 250, 0xFF18212C);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.pathing_summary"), x + 8, panelTop + 66, 0xD9E8F5);
        graphics.drawString(font, fit(Component.translatable("screen.baritonehelper.diagnostics", snapshot.ticketCount(),
                snapshot.simulationTicketCount(), snapshot.searchTicketCount(), snapshot.totalTicketCount(), snapshot.replanAttempts(),
                snapshot.lastProgressAgeTicks()), width - 16), x + 8, panelTop + 90, 0xC5D4E0);
        graphics.drawString(font, fit(Component.translatable("screen.baritonehelper.path_diagnostics",
                snapshot.pathingStatus(), snapshot.pathNode(), snapshot.pathLength(),
                String.format(java.util.Locale.ROOT, "%.1f", snapshot.pathCost()), snapshot.pathQueueDepth(),
                snapshot.pathElapsedNanos() / 1_000_000L), width - 16),
                x + 8, panelTop + 108, 0xC5D4E0);
        drawProgressBar(graphics, x + 8, panelTop + 122, width - 16, 6, snapshot.pathNode(), snapshot.pathLength());
        graphics.drawString(font, fit(Component.translatable("screen.baritonehelper.search_pipeline",
                snapshot.searchPhase(), snapshot.searchMode(), snapshot.searchGeneration(), snapshot.chunksScanned(),
                snapshot.dirtyChunks(), snapshot.inFlightChunks(), snapshot.indexedTargets(), snapshot.searchQueueDepth(),
                snapshot.searchElapsedNanos() / 1_000_000L), width - 16), x + 8, panelTop + 136, 0xC5D4E0);
        graphics.drawString(font, fit(Component.translatable("screen.baritonehelper.search_diagnostics",
                snapshot.frontierIndex(), snapshot.frontierSize(), snapshot.chunksScanned(),
                snapshot.positionsExamined(), snapshot.matchingBlocks(), snapshot.candidatesFound(),
                snapshot.candidatesRejectedByPolicy(), snapshot.candidatesRejectedAsUnreachable(),
                snapshot.cachedCandidates()), width - 16), x + 8, panelTop + 154, 0xC5D4E0);
        graphics.drawString(font, fit(Component.translatable("screen.baritonehelper.search_state",
                snapshot.waitingForSearchChunk(), snapshot.lastScannedChunk().isBlank() ? "-" : snapshot.lastScannedChunk(),
                snapshot.requestedSearchChunk().isBlank() ? "-" : snapshot.requestedSearchChunk()), width - 16),
                x + 8, panelTop + 172, 0xC5D4E0);
        graphics.drawString(font, fit(Component.translatable("screen.baritonehelper.last_route",
                snapshot.lastNavigationDestination().isBlank() ? Component.translatable("screen.baritonehelper.none")
                        : snapshot.lastNavigationDestination()), width - 16), x + 8, panelTop + 190, 0xC5D4E0);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.pathing_hint"), x + 8, panelTop + 220, 0x8996A3);
    }

    private void renderLog(GuiGraphics graphics) {
        int x = panelLeft + 18;
        graphics.fill(x, panelTop + 56, panelLeft + panelWidth - 18, footerTop() - 8, 0xFF18212C);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.activity_log"), x + 8, panelTop + 66, 0xD9E8F5);
        int row = 0;
        int maxRows = visibleActivityRows();
        List<String> history = snapshot.activityHistory();
        for (int index = history.size() - 1 - activityOffset;
                index >= 0 && row < maxRows; index--) {
            String entry = history.get(index);
            graphics.drawString(font, fit(Component.literal(entry), panelWidth - 52), x + 8,
                    panelTop + 88 + row * 18, 0xC5D4E0);
            row++;
        }
        if (row == 0) graphics.drawString(font, Component.translatable("screen.baritonehelper.no_history"), x + 8, panelTop + 88, 0x8996A3);
        if (!snapshot.resumeNote().isBlank()) graphics.drawString(font, fit(Component.translatable("screen.baritonehelper.resume", snapshot.resumeNote()), panelWidth - 52), x + 8, Math.min(panelTop + 260, footerTop() - 26), 0x9FB1C1);
    }

    private static String formatPos(net.minecraft.core.BlockPos position) {
        return position.getX() + ", " + position.getY() + ", " + position.getZ();
    }

    private static String jobKey(WorkerJob job) {
        return switch (job) {
            case IDLE -> "job.baritonehelper.idle";
            case READY -> "job.baritonehelper.ready";
            case COLLECT -> "job.baritonehelper.collect";
            case DEPOSIT -> "job.baritonehelper.deposit";
            case BLOCKED -> "job.baritonehelper.blocked";
            case COMPLETED -> "job.baritonehelper.completed";
        };
    }

    private static <T> T enumValue(T[] values, int ordinal, T fallback) {
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : fallback;
    }
}
