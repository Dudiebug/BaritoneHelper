package dev.dudie.baritonehelper.client;

import dev.dudie.baritonehelper.menu.WorkerDashboardMenu;
import dev.dudie.baritonehelper.worker.WorkerBlockReason;
import dev.dudie.baritonehelper.worker.WorkerJob;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class WorkerDashboardScreen
        extends AbstractContainerScreen<WorkerDashboardMenu> {
    private Button startButton;
    private Button stopButton;
    private Button clearTargetButton;
    private Button clearStorageButton;

    public WorkerDashboardScreen(
            WorkerDashboardMenu menu,
            Inventory playerInventory,
            Component title) {
        super(menu, playerInventory, title);
        imageWidth = 276;
        imageHeight = 210;
        titleLabelX = 12;
        titleLabelY = 10;
        inventoryLabelY = 1000;
    }

    @Override
    protected void init() {
        super.init();
        int firstRow = topPos + 148;
        int secondRow = topPos + 174;

        startButton = addRenderableWidget(Button.builder(
                        Component.translatable("button.baritonehelper.start"),
                        button -> sendButton(WorkerDashboardMenu.BUTTON_START))
                .bounds(leftPos + 12, firstRow, 78, 20)
                .build());
        stopButton = addRenderableWidget(Button.builder(
                        Component.translatable("button.baritonehelper.stop"),
                        button -> sendButton(WorkerDashboardMenu.BUTTON_STOP))
                .bounds(leftPos + 96, firstRow, 78, 20)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("button.baritonehelper.inventory"),
                        button -> sendButton(WorkerDashboardMenu.BUTTON_OPEN_INVENTORY))
                .bounds(leftPos + 180, firstRow, 84, 20)
                .build());

        clearTargetButton = addRenderableWidget(Button.builder(
                        Component.translatable("button.baritonehelper.clear_target"),
                        button -> sendButton(WorkerDashboardMenu.BUTTON_CLEAR_TARGET))
                .bounds(leftPos + 12, secondRow, 120, 20)
                .build());
        clearStorageButton = addRenderableWidget(Button.builder(
                        Component.translatable("button.baritonehelper.clear_storage"),
                        button -> sendButton(WorkerDashboardMenu.BUTTON_CLEAR_STORAGE))
                .bounds(leftPos + 144, secondRow, 120, 20)
                .build());
        updateButtonState();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtonState();
    }

    private void updateButtonState() {
        if (startButton == null) {
            return;
        }
        startButton.active = menu.hasTarget() && !menu.isRunning();
        stopButton.active = menu.isRunning() || menu.job() == WorkerJob.BLOCKED;
        clearTargetButton.active = menu.hasTarget();
        clearStorageButton.active = menu.hasStorage();
    }

    private void sendButton(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(
            GuiGraphics graphics,
            float partialTick,
            int mouseX,
            int mouseY) {
        int left = leftPos;
        int top = topPos;
        graphics.fill(left, top, left + imageWidth, top + imageHeight, 0xEE10151C);
        graphics.fill(left + 1, top + 1, left + imageWidth - 1, top + 2, 0xFF4B8BBE);
        graphics.fill(left + 8, top + 28, left + imageWidth - 8, top + 83, 0xFF18212C);
        graphics.fill(left + 8, top + 88, left + imageWidth - 8, top + 138, 0xFF18212C);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFFFFFF);

        Component job = Component.translatable(
                "screen.baritonehelper.job",
                Component.translatable(jobKey(menu.job())));
        Component activity = Component.translatable(
                "screen.baritonehelper.activity",
                Component.translatable(menu.activity().translationKey()));
        graphics.drawString(font, job, 14, 34, 0xD9E8F5);
        graphics.drawString(font, activity, 14, 48, 0xD9E8F5);

        Component target = menu.hasTarget()
                ? menu.targetBlock().getName()
                : Component.translatable("screen.baritonehelper.not_set");
        graphics.drawString(
                font,
                Component.translatable("screen.baritonehelper.target", target),
                14,
                64,
                menu.hasTarget() ? 0xB8E6B8 : 0xE6B8B8);

        String storage = menu.storagePosition()
                .map(WorkerDashboardScreen::formatPos)
                .orElseGet(() -> Component.translatable("screen.baritonehelper.not_set").getString());
        graphics.drawString(
                font,
                Component.translatable("screen.baritonehelper.storage", storage),
                14,
                94,
                menu.hasStorage() ? 0xB8E6B8 : 0xE6D4B8);

        String destination = menu.currentWorkPosition()
                .map(WorkerDashboardScreen::formatPos)
                .orElseGet(() -> Component.translatable("screen.baritonehelper.none").getString());
        graphics.drawString(
                font,
                Component.translatable("screen.baritonehelper.destination", destination),
                14,
                108,
                0xD9E8F5);
        graphics.drawString(
                font,
                Component.translatable(
                        "screen.baritonehelper.inventory",
                        menu.usedSlots(),
                        menu.capacity(),
                        menu.itemCount()),
                14,
                122,
                0xD9E8F5);

        graphics.drawString(
                font,
                Component.translatable(
                        "screen.baritonehelper.diagnostics",
                        menu.ticketCount(),
                        menu.replanAttempts(),
                        menu.lastProgressAgeTicks()),
                144,
                122,
                0x9FB1C1);

        if (menu.blockReason() != WorkerBlockReason.NONE) {
            graphics.drawString(
                    font,
                    Component.translatable(
                            "screen.baritonehelper.reason",
                            Component.translatable(menu.blockReason().translationKey())),
                    14,
                    136,
                    0xFF8D8D);
        }

        graphics.drawString(
                font,
                Component.translatable("screen.baritonehelper.instructions"),
                12,
                200,
                0x8996A3);
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
        };
    }
}
