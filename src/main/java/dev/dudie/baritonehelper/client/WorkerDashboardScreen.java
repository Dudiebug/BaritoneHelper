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
    private static final int TAB_JOB = 0;
    private static final int TAB_WORLD = 1;
    private static final int TAB_STORAGE = 2;
    private static final int TAB_PATHING = 3;
    private static final int TAB_LOG = 4;
    private static final int PICKER_ROWS = 9;

    private WorkerDashboardStateS2C.Snapshot snapshot;
    private int tab = TAB_JOB;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int pickerOffset;
    private List<ResourceLocation> pickerResults = List.of();
    private String lastAcknowledgement = "";
    private boolean requestPending;

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
        if (!selectedZoneId.isBlank() && snapshot.noWorkZones().stream().noneMatch(zone -> zone.id().equals(selectedZoneId))) {
            selectedZoneId = "";
        }
        refreshPickerResults();
    }

    public int workerEntityId() {
        return snapshot.workerEntityId();
    }

    public void setSnapshot(WorkerDashboardStateS2C.Snapshot snapshot) {
        if (snapshot.workerEntityId() != workerEntityId()) return;
        this.snapshot = snapshot;
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
        updateButtons();
    }

    public void setAcknowledgement(WorkerActionAcknowledgementS2C acknowledgement) {
        requestPending = false;
        String message = Component.translatable(acknowledgement.translationKey()).getString();
        lastAcknowledgement = acknowledgement.success() ? "✓ " + message : "! " + message;
    }

    @Override
    protected void init() {
        pathingButtons.clear();
        panelWidth = Math.min(760, Math.max(320, width - 20));
        panelHeight = Math.min(560, Math.max(420, height - 28));
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
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

        startButton = addRenderableWidget(Button.builder(Component.translatable("button.baritonehelper.start"), b -> send(new WorkerDashboardActionC2S(workerEntityId(), revision(), WorkerDashboardActionC2S.Action.START)))
                .bounds(panelLeft + 18, panelTop + panelHeight - 38, 92, 20).build());
        stopButton = addRenderableWidget(Button.builder(Component.translatable("button.baritonehelper.stop"), b -> send(new WorkerDashboardActionC2S(workerEntityId(), revision(), WorkerDashboardActionC2S.Action.STOP)))
                .bounds(panelLeft + 116, panelTop + panelHeight - 38, 92, 20).build());
        inventoryButton = addRenderableWidget(Button.builder(Component.translatable("button.baritonehelper.inventory"), b -> send(new WorkerDashboardActionC2S(workerEntityId(), revision(), WorkerDashboardActionC2S.Action.OPEN_INVENTORY)))
                .bounds(panelLeft + 214, panelTop + panelHeight - 38, 104, 20).build());
        clearTargetButton = addRenderableWidget(Button.builder(Component.translatable("button.baritonehelper.clear_target"), b -> send(new WorkerDashboardActionC2S(workerEntityId(), revision(), WorkerDashboardActionC2S.Action.CLEAR_TARGET)))
                .bounds(panelLeft + 324, panelTop + panelHeight - 38, 112, 20).build());
        clearStorageButton = addRenderableWidget(Button.builder(Component.translatable("button.baritonehelper.clear_storage"), b -> send(new WorkerDashboardActionC2S(workerEntityId(), revision(), WorkerDashboardActionC2S.Action.CLEAR_STORAGE)))
                .bounds(panelLeft + 442, panelTop + panelHeight - 38, 112, 20).build());
        exclusionButton = addRenderableWidget(Button.builder(Component.translatable("screen.baritonehelper.toggle_exclusion"), b -> {
            if (!snapshot.targetBlockId().isBlank()) send(new WorkerDashboardActionC2S(workerEntityId(), revision(),
                    WorkerDashboardActionC2S.Action.TOGGLE_EXCLUSION, snapshot.targetBlockId(), 0, true,
                    BlockPos.ZERO, 0, 0));
        }).bounds(Math.min(panelLeft + 562, panelLeft + panelWidth - 122), panelTop + panelHeight - 38, 120, 20).build());
        amountModeButton = addRenderableWidget(Button.builder(modeLabel(), b -> send(new WorkerDashboardActionC2S(
                workerEntityId(), revision(), Integer.parseInt(safeAmount()), !snapshot.unlimitedCount())))
                .bounds(panelLeft + 112, panelTop + 258, 108, 20).build());
        applyAmountButton = addRenderableWidget(Button.builder(Component.translatable("screen.baritonehelper.apply"), b -> send(new WorkerDashboardActionC2S(
                workerEntityId(), revision(), Integer.parseInt(safeAmount()), snapshot.unlimitedCount())))
                .bounds(panelLeft + 224, panelTop + 258, 70, 20).build());
        resetProgressButton = addRenderableWidget(Button.builder(Component.translatable("screen.baritonehelper.reset_progress"), b -> send(new WorkerDashboardActionC2S(
                workerEntityId(), revision(), WorkerDashboardActionC2S.Action.RESET_PROGRESS)))
                .bounds(panelLeft + 300, panelTop + 258, 120, 20).build());
        applyAreaButton = addRenderableWidget(Button.builder(Component.translatable("screen.baritonehelper.apply"), b -> send(new WorkerDashboardActionC2S(
                workerEntityId(), revision(), areaPosition(), areaRadius(areaHorizontalBox), areaRadius(areaVerticalBox))))
                .bounds(panelLeft + 18, panelTop + 196, 70, 20).build());
        usePlayerAreaButton = addRenderableWidget(Button.builder(Component.translatable("screen.baritonehelper.use_player"), b -> {
            if (minecraft != null && minecraft.player != null) {
                send(new WorkerDashboardActionC2S(workerEntityId(), revision(), minecraft.player.blockPosition(),
                        areaRadius(areaHorizontalBox), areaRadius(areaVerticalBox)));
            }
        }).bounds(panelLeft + 94, panelTop + 196, 112, 20).build());
        useWorkerAreaButton = addRenderableWidget(Button.builder(Component.translatable("screen.baritonehelper.use_worker"), b -> {
            if (minecraft != null && minecraft.level != null && minecraft.level.getEntity(workerEntityId()) != null) {
                send(new WorkerDashboardActionC2S(workerEntityId(), revision(), minecraft.level.getEntity(workerEntityId()).blockPosition(),
                        areaRadius(areaHorizontalBox), areaRadius(areaVerticalBox)));
            }
        }).bounds(panelLeft + 212, panelTop + 196, 112, 20).build());
        selectAreaPointButton = addRenderableWidget(Button.builder(Component.translatable("screen.baritonehelper.select_point"), b -> send(new WorkerDashboardActionC2S(
                workerEntityId(), revision(), WorkerDashboardActionC2S.Action.ARM_AREA_SELECTION)))
                .bounds(panelLeft + 330, panelTop + 196, 112, 20).build());
        clearAreaButton = addRenderableWidget(Button.builder(Component.translatable("screen.baritonehelper.clear_area"), b -> send(new WorkerDashboardActionC2S(
                workerEntityId(), revision(), WorkerDashboardActionC2S.Action.CLEAR_WORK_AREA)))
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
            if (!selectedZoneId.isBlank()) send(new WorkerDashboardActionC2S(workerEntityId(), revision(),
                    WorkerDashboardActionC2S.Action.ARM_ZONE_SELECTION, selectedZoneId, 0, true,
                    BlockPos.ZERO, 0, 0));
        }).bounds(zoneLeft, panelTop + 264, zoneWidth, 20).build());
        toggleZoneButton = addRenderableWidget(Button.builder(Component.translatable("screen.baritonehelper.toggle_zone"), b -> {
            if (!selectedZoneId.isBlank()) send(new WorkerDashboardActionC2S(workerEntityId(), revision(),
                    WorkerDashboardActionC2S.Action.TOGGLE_NO_WORK_ZONE, selectedZoneId, 0, true,
                    BlockPos.ZERO, 0, 0));
        }).bounds(zoneLeft, panelTop + 286, zoneWidth, 20).build());
        deleteZoneButton = addRenderableWidget(Button.builder(Component.translatable("screen.baritonehelper.delete_zone"), b -> {
            if (!selectedZoneId.isBlank()) send(new WorkerDashboardActionC2S(workerEntityId(), revision(),
                    WorkerDashboardActionC2S.Action.DELETE_NO_WORK_ZONE, selectedZoneId, 0, true,
                    BlockPos.ZERO, 0, 0));
        }).bounds(zoneLeft, panelTop + 308, zoneWidth, 20).build());
        selectStorageButton = addRenderableWidget(Button.builder(Component.translatable("screen.baritonehelper.select_storage"), b -> send(new WorkerDashboardActionC2S(
                workerEntityId(), revision(), WorkerDashboardActionC2S.Action.ARM_STORAGE_SELECTION)))
                .bounds(panelLeft + 18, panelTop + 196, 140, 20).build());
        addPathingButton("breaking", "screen.baritonehelper.allow_breaking", snapshot.allowBreakingObstructions(), 0);
        addPathingButton("placement", "screen.baritonehelper.allow_placement", snapshot.allowBlockPlacement(), 1);
        addPathingButton("bridging", "screen.baritonehelper.allow_bridging", snapshot.allowBridging(), 2);
        addPathingButton("pillaring", "screen.baritonehelper.allow_pillaring", snapshot.allowPillaring(), 3);
        addPathingButton("parkour", "screen.baritonehelper.allow_parkour", snapshot.allowParkour(), 4);
        addPathingButton("water", "screen.baritonehelper.allow_water", snapshot.allowWaterRoutes(), 5);
        addPathingButton("safer", "screen.baritonehelper.prefer_safer", snapshot.preferSaferRoutes(), 6);
        addPathingButton("avoid_destructive", "screen.baritonehelper.avoid_destructive", snapshot.avoidDestructiveRouting(), 7);
        selectTab(tab);
        updateButtons();
    }

    private EditBox areaBox(int x, int y, int value) {
        EditBox box = addRenderableWidget(new EditBox(font, x, y, 70, 20, Component.empty()));
        box.setValue(Integer.toString(value));
        box.setFilter(valueText -> valueText.isEmpty() || valueText.startsWith("-")
                || valueText.chars().allMatch(Character::isDigit));
        return box;
    }

    private Button addAreaPresetButton(int radius, boolean horizontal, int index) {
        String key = horizontal
                ? "screen.baritonehelper.horizontal_preset"
                : "screen.baritonehelper.vertical_preset";
        return addRenderableWidget(Button.builder(Component.translatable(key, radius), b -> {
            if (horizontal) areaHorizontalBox.setValue(Integer.toString(radius));
            else areaVerticalBox.setValue(Integer.toString(radius));
            send(new WorkerDashboardActionC2S(workerEntityId(), revision(), areaPosition(),
                    areaRadius(areaHorizontalBox), areaRadius(areaVerticalBox)));
        }).bounds(panelLeft + 18 + index * 48,
                panelTop + (horizontal ? 218 : 240), 45, 20).build());
    }

    private EditBox zoneBox(int x, int y, int value) {
        EditBox box = addRenderableWidget(new EditBox(font, x, y, zoneBoxWidth(), 20, Component.empty()));
        box.setValue(Integer.toString(value));
        box.setFilter(valueText -> valueText.isEmpty() || valueText.equals("-")
                || valueText.chars().allMatch(Character::isDigit));
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
            send(new WorkerDashboardActionC2S(workerEntityId(), revision(),
                    WorkerDashboardActionC2S.Action.ADD_NO_WORK_ZONE,
                    zoneNameBox.getValue(), zoneMode, zoneEnabled, center, horizontal, vertical));
        } else {
            send(new WorkerDashboardActionC2S(workerEntityId(), revision(),
                    WorkerDashboardActionC2S.Action.UPDATE_NO_WORK_ZONE,
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
            send(new WorkerDashboardActionC2S(workerEntityId(), revision(), key, !pathingEnabled(key)));
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
        if (startButton == null) return;
        WorkerJob job = enumValue(WorkerJob.values(), snapshot.job(), WorkerJob.IDLE);
        boolean running = job.activelyWorks();
        startButton.active = !snapshot.targetBlockId().isBlank() && !running;
        stopButton.active = running || job == WorkerJob.BLOCKED;
        inventoryButton.active = true;
        clearTargetButton.active = !snapshot.targetBlockId().isBlank();
        clearStorageButton.active = snapshot.hasStorage();
        exclusionButton.active = !snapshot.targetBlockId().isBlank();
        resetProgressButton.active = !snapshot.targetBlockId().isBlank() && snapshot.completedCount() > 0;
        saveZoneButton.active = true;
        selectZonePointButton.active = !selectedZoneId.isBlank();
        toggleZoneButton.active = !selectedZoneId.isBlank();
        deleteZoneButton.active = !selectedZoneId.isBlank();
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
        if (tab == TAB_JOB && searchBox != null && mouseX >= panelLeft + 300
                && mouseX < panelLeft + panelWidth - 18) {
            int row = (int) ((mouseY - (panelTop + 94)) / 20);
            if (row >= 0 && row < PICKER_ROWS) {
                int index = pickerOffset + row;
                if (index >= 0 && index < pickerResults.size()) {
                    send(new WorkerDashboardActionC2S(workerEntityId(), revision(), pickerResults.get(index).toString()));
                    return true;
                }
            }
        }
        if (tab == TAB_WORLD && mouseX >= panelLeft + 18 && mouseX < panelLeft + panelWidth - 18
                && mouseY >= panelTop + 350) {
            int row = (int) ((mouseY - (panelTop + 350)) / 18);
            if (row >= 0 && row < visibleZoneRows()
                    && row < snapshot.noWorkZones().size()) {
                selectedZoneId = snapshot.noWorkZones().get(row).id();
                loadSelectedZone();
                updateButtons();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (tab == TAB_JOB && mouseX >= panelLeft + 300) {
            int max = Math.max(0, pickerResults.size() - PICKER_ROWS);
            pickerOffset = Math.max(0, Math.min(max, pickerOffset + (deltaY > 0 ? -1 : 1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 0xF010151C);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 3, 0xFF4B8BBE);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderContent(graphics);
    }

    private void renderContent(GuiGraphics graphics) {
        int textX = panelLeft + 18;
        graphics.drawString(font, title, textX, panelTop + 40, 0xFFFFFF);
        if (requestPending) graphics.drawString(font, Component.translatable("screen.baritonehelper.pending"), panelLeft + panelWidth - 110, panelTop + 40, 0xE6D4B8);
        WorkerJob job = enumValue(WorkerJob.values(), snapshot.job(), WorkerJob.IDLE);
        WorkerActivity activity = enumValue(WorkerActivity.values(), snapshot.activity(), WorkerActivity.IDLE);
        WorkerBlockReason reason = enumValue(WorkerBlockReason.values(), snapshot.blockReason(), WorkerBlockReason.NONE);
        WorkerRuntimeState runtime = enumValue(WorkerRuntimeState.values(), snapshot.runtimeState(), WorkerRuntimeState.UNCONFIGURED);
        if (tab == TAB_JOB) {
            graphics.drawString(font, Component.translatable("screen.baritonehelper.job", Component.translatable(jobKey(job))), textX, panelTop + 286, 0xD9E8F5);
            graphics.drawString(font, Component.translatable("screen.baritonehelper.activity", Component.translatable(activity.translationKey())), textX, panelTop + 301, 0xD9E8F5);
            graphics.drawString(font, Component.translatable("screen.baritonehelper.runtime", runtime.name()), textX, panelTop + 316, 0x9FB1C1);
            if (!lastAcknowledgement.isBlank()) graphics.drawString(font, Component.literal(lastAcknowledgement), textX, panelTop + 346, 0xB8E6B8);
            if (reason != WorkerBlockReason.NONE) graphics.drawString(font, Component.translatable("screen.baritonehelper.reason", Component.translatable(reason.translationKey())), textX, panelTop + 331, 0xFF8D8D);
        }
        if (tab == TAB_JOB) renderJob(graphics);
        else if (tab == TAB_WORLD) renderWorld(graphics);
        else if (tab == TAB_STORAGE) renderStorage(graphics);
        else if (tab == TAB_PATHING) renderPathing(graphics);
        else renderLog(graphics);
    }

    private void renderJob(GuiGraphics graphics) {
        int x = panelLeft + 18;
        graphics.fill(x, panelTop + 56, panelLeft + 294, panelTop + 186, 0xFF18212C);
        graphics.fill(panelLeft + 300, panelTop + 56, panelLeft + panelWidth - 18, panelTop + 276, 0xFF18212C);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.target", snapshot.targetBlockId().isBlank()
                ? Component.translatable("screen.baritonehelper.not_set") : Component.literal(snapshot.targetBlockId())), x + 8, panelTop + 58, 0xD9E8F5);
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
        graphics.drawString(font, Component.translatable("screen.baritonehelper.progress", snapshot.completedCount(), snapshot.unlimitedCount() ? "∞" : Integer.toString(snapshot.requestedCount())), x, panelTop + 226, 0xB8E6B8);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.amount"), x, panelTop + 246, 0x9FB1C1);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.inventory", snapshot.usedSlots(), snapshot.capacity(), snapshot.itemCount()), x, panelTop + 342, 0xD9E8F5);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.diagnostics", snapshot.ticketCount(), snapshot.searchTicketCount(), snapshot.totalTicketCount(), snapshot.replanAttempts(), snapshot.lastProgressAgeTicks()), x, panelTop + 357, 0x9FB1C1);
    }

    private void renderWorld(GuiGraphics graphics) {
        int x = panelLeft + 18;
        graphics.fill(x, panelTop + 56, panelLeft + panelWidth - 18, panelTop + panelHeight - 54, 0xFF18212C);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.work_area"), x + 8, panelTop + 66, 0xD9E8F5);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.dimension", snapshot.workAreaDimension()), x + 8, panelTop + 86, 0xC5D4E0);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.x"), x + 2, panelTop + 126, 0x9FB1C1);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.y"), x + 80, panelTop + 126, 0x9FB1C1);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.z"), x + 158, panelTop + 126, 0x9FB1C1);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.horizontal"), x + 2, panelTop + 160, 0x9FB1C1);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.vertical"), x + 110, panelTop + 160, 0x9FB1C1);
        int chunkSpan = snapshot.horizontalRadius() * 2 / 16 + 1;
        graphics.drawString(font, Component.translatable("screen.baritonehelper.destination", snapshot.hasCurrentWorkPosition() ? formatPos(snapshot.currentWorkPosition()) : Component.translatable("screen.baritonehelper.none")), x + 8, panelTop + 270, 0xD9E8F5);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.storage", snapshot.hasStorage() ? formatPos(snapshot.storagePosition()) : Component.translatable("screen.baritonehelper.not_set")), x + 8, panelTop + 286, 0xD9E8F5);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.area_coverage", chunkSpan * chunkSpan, chunkSpan, chunkSpan, snapshot.verticalRadius()), x + 8, panelTop + 302, 0xC5D4E0);
        int zoneLeft = rightColumnLeft();
        graphics.drawString(font, Component.translatable("screen.baritonehelper.zone_editor"), zoneLeft, panelTop + 78, 0xD9E8F5);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.zone_name"), zoneLeft, panelTop + 86, 0x8996A3);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.zone_center"), zoneLeft, panelTop + 114, 0x8996A3);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.zone_radii"), zoneLeft, panelTop + 144, 0x8996A3);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.no_work_zone_list"), x + 8, panelTop + 334, 0xD9E8F5);
        for (int i = 0; i < snapshot.noWorkZones().size() && i < visibleZoneRows(); i++) {
            var zone = snapshot.noWorkZones().get(i);
            int y = panelTop + 352 + i * 18;
            if (zone.id().equals(selectedZoneId)) graphics.fill(x + 4, y - 2, panelLeft + panelWidth - 22, y + 14, 0xFF315A73);
            String name = zone.name().isBlank() ? zone.id() : zone.name();
            String mode = zone.mode() == 0 ? "NO_MODIFY" : "NO_ENTER";
            graphics.drawString(font, font.plainSubstrByWidth(name + " (" + mode + ")", panelWidth - 50), x + 8, y, 0xC5D4E0);
        }
        graphics.drawString(font, Component.translatable("screen.baritonehelper.world_hint"), x + 8, panelTop + 318, 0x8996A3);
    }

    private int visibleZoneRows() {
        return Math.max(0, Math.min(8, 1 + (panelHeight - 420) / 18));
    }

    private void renderStorage(GuiGraphics graphics) {
        int x = panelLeft + 18;
        graphics.fill(x, panelTop + 56, panelLeft + panelWidth - 18, panelTop + 226, 0xFF18212C);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.storage_inventory"), x + 8, panelTop + 66, 0xD9E8F5);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.storage", snapshot.hasStorage() ? formatPos(snapshot.storagePosition()) : Component.translatable("screen.baritonehelper.not_set")), x + 8, panelTop + 88, 0xC5D4E0);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.storage_dimension", snapshot.storageDimension()), x + 8, panelTop + 106, 0xC5D4E0);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.inventory", snapshot.usedSlots(), snapshot.capacity(), snapshot.itemCount()), x + 8, panelTop + 136, 0xD9E8F5);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.storage_hint"), x + 8, panelTop + 188, 0x8996A3);
    }

    private void renderPathing(GuiGraphics graphics) {
        int x = panelLeft + 286;
        graphics.fill(x, panelTop + 56, panelLeft + panelWidth - 18, panelTop + 250, 0xFF18212C);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.pathing_summary"), x + 8, panelTop + 66, 0xD9E8F5);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.diagnostics", snapshot.ticketCount(), snapshot.searchTicketCount(), snapshot.totalTicketCount(), snapshot.replanAttempts(), snapshot.lastProgressAgeTicks()), x + 8, panelTop + 90, 0xC5D4E0);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.path_diagnostics", snapshot.pathingStatus(), snapshot.pathNode(), snapshot.pathLength(),
                String.format(java.util.Locale.ROOT, "%.1f", snapshot.pathCost()), snapshot.pathRequested()), x + 8, panelTop + 108, 0xC5D4E0);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.search_diagnostics", snapshot.frontierIndex(), snapshot.frontierSize(), snapshot.chunksScanned(), snapshot.positionsExamined(), snapshot.matchingBlocks(), snapshot.candidatesFound(), snapshot.candidatesRejectedByPolicy(), snapshot.candidatesRejectedAsUnreachable(), snapshot.cachedCandidates()), x + 8, panelTop + 126, 0xC5D4E0);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.search_state", snapshot.waitingForSearchChunk(), snapshot.lastScannedChunk().isBlank() ? "-" : snapshot.lastScannedChunk(), snapshot.requestedSearchChunk().isBlank() ? "-" : snapshot.requestedSearchChunk()), x + 8, panelTop + 144, 0xC5D4E0);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.destination", snapshot.hasCurrentWorkPosition() ? formatPos(snapshot.currentWorkPosition()) : Component.translatable("screen.baritonehelper.none")), x + 8, panelTop + 166, 0xC5D4E0);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.last_route", snapshot.lastNavigationDestination().isBlank() ? Component.translatable("screen.baritonehelper.none") : snapshot.lastNavigationDestination()), x + 8, panelTop + 184, 0xC5D4E0);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.pathing_hint"), x + 8, panelTop + 220, 0x8996A3);
    }

    private void renderLog(GuiGraphics graphics) {
        int x = panelLeft + 18;
        graphics.fill(x, panelTop + 56, panelLeft + panelWidth - 18, panelTop + panelHeight - 54, 0xFF18212C);
        graphics.drawString(font, Component.translatable("screen.baritonehelper.activity_log"), x + 8, panelTop + 66, 0xD9E8F5);
        int row = 0;
        for (String entry : snapshot.activityHistory()) {
            if (row >= 9) break;
            graphics.drawString(font, Component.literal(entry), x + 8, panelTop + 88 + row * 18, 0xC5D4E0);
            row++;
        }
        if (row == 0) graphics.drawString(font, Component.translatable("screen.baritonehelper.no_history"), x + 8, panelTop + 88, 0x8996A3);
        if (!snapshot.resumeNote().isBlank()) graphics.drawString(font, Component.translatable("screen.baritonehelper.resume", snapshot.resumeNote()), x + 8, panelTop + 260, 0x9FB1C1);
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
