/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.dudie.baritonehelper.internal.baritone;

import dev.dudie.baritonehelper.internal.baritone.api.IBaritone;
import dev.dudie.baritonehelper.internal.baritone.api.Settings;
import dev.dudie.baritonehelper.internal.baritone.api.cache.IWorldProvider;
import dev.dudie.baritonehelper.internal.baritone.api.event.listener.IEventBus;
import dev.dudie.baritonehelper.internal.baritone.api.process.ICustomGoalProcess;
import dev.dudie.baritonehelper.internal.baritone.api.process.IGetToBlockProcess;
import dev.dudie.baritonehelper.internal.baritone.api.process.IMineProcess;
import dev.dudie.baritonehelper.internal.baritone.api.utils.IEntityContext;
import dev.dudie.baritonehelper.internal.baritone.api.utils.IInputOverrideHandler;
import dev.dudie.baritonehelper.internal.baritone.behavior.Behavior;
import dev.dudie.baritonehelper.internal.baritone.behavior.InventoryBehavior;
import dev.dudie.baritonehelper.internal.baritone.behavior.LookBehavior;
import dev.dudie.baritonehelper.internal.baritone.behavior.MemoryBehavior;
import dev.dudie.baritonehelper.internal.baritone.behavior.PathingBehavior;
import dev.dudie.baritonehelper.internal.baritone.cache.WorldProvider;
import dev.dudie.baritonehelper.internal.baritone.event.GameEventHandler;
import dev.dudie.baritonehelper.internal.baritone.process.CustomGoalProcess;
import dev.dudie.baritonehelper.internal.baritone.process.GetToBlockProcess;
import dev.dudie.baritonehelper.internal.baritone.process.MineProcess;
import dev.dudie.baritonehelper.internal.baritone.utils.InputOverrideHandler;
import dev.dudie.baritonehelper.internal.baritone.utils.PathingControlManager;
import dev.dudie.baritonehelper.internal.baritone.utils.BlockStateInterface;
import net.minecraft.world.entity.LivingEntity;

/**
 * The collector's one Baritone engine.  It intentionally exposes only the
 * server-side pathing and interaction processes needed by a worker.
 */
public final class Baritone implements IBaritone {
    /** Rebuilt on every server tick so path execution sees current terrain. */
    public volatile BlockStateInterface bsi;
    private final Settings settings = new Settings();
    private final GameEventHandler gameEventHandler = new GameEventHandler(this);
    private final IEntityContext entityContext;
    private final WorldProvider worldProvider;
    private final PathingBehavior pathingBehavior;
    private final LookBehavior lookBehavior;
    private final MemoryBehavior memoryBehavior;
    private final InventoryBehavior inventoryBehavior;
    private final InputOverrideHandler inputOverrideHandler;
    private final PathingControlManager pathingControlManager;
    private final MineProcess mineProcess;
    private final CustomGoalProcess customGoalProcess;
    private final GetToBlockProcess getToBlockProcess;

    public Baritone(IEntityContext entityContext) {
        this.entityContext = entityContext;
        this.worldProvider = new WorldProvider(entityContext.world());
        if (entityContext instanceof dev.dudie.baritonehelper.internal.baritone.utils.player.EntityContext context) {
            context.attach(this);
        }
        this.settings.allowBreak.set(true);
        this.settings.allowPlace.set(true);
        this.settings.allowParkour.set(true);
        this.settings.allowParkourPlace.set(true);
        this.settings.allowSwimming.set(true);
        this.settings.sprintInWater.set(true);
        this.pathingBehavior = new PathingBehavior(this);
        this.lookBehavior = new LookBehavior(this);
        this.memoryBehavior = new MemoryBehavior(this);
        this.inventoryBehavior = new InventoryBehavior(this);
        this.inputOverrideHandler = new InputOverrideHandler(this);
        this.pathingControlManager = new PathingControlManager(this);
        this.mineProcess = new MineProcess(this);
        this.customGoalProcess = new CustomGoalProcess(this);
        this.getToBlockProcess = new GetToBlockProcess(this);
        this.pathingControlManager.registerProcess(this.mineProcess);
        this.pathingControlManager.registerProcess(this.customGoalProcess);
        this.pathingControlManager.registerProcess(this.getToBlockProcess);
    }

    public Baritone(LivingEntity entity) {
        this(new dev.dudie.baritonehelper.internal.baritone.utils.player.EntityContext(entity));
    }

    public void registerBehavior(Behavior behavior) {
        this.gameEventHandler.registerEventListener(behavior);
    }

    @Override
    public PathingBehavior getPathingBehavior() {
        return this.pathingBehavior;
    }

    @Override
    public LookBehavior getLookBehavior() {
        return this.lookBehavior;
    }

    @Override
    public IMineProcess getMineProcess() {
        return this.mineProcess;
    }

    @Override
    public ICustomGoalProcess getCustomGoalProcess() {
        return this.customGoalProcess;
    }

    @Override
    public IGetToBlockProcess getGetToBlockProcess() {
        return this.getToBlockProcess;
    }

    @Override
    public IWorldProvider getWorldProvider() {
        return this.worldProvider;
    }

    @Override
    public PathingControlManager getPathingControlManager() {
        return this.pathingControlManager;
    }

    @Override
    public IInputOverrideHandler getInputOverrideHandler() {
        return this.inputOverrideHandler;
    }

    @Override
    public IEntityContext getEntityContext() {
        return this.entityContext;
    }

    @Override
    public IEventBus getGameEventHandler() {
        return this.gameEventHandler;
    }

    @Override
    public Settings settings() {
        return this.settings;
    }

    @Override
    public boolean isActive() {
        return this.pathingControlManager.isActive();
    }

    @Override
    public void logDebug(String message) {
        InternalBaritoneRuntime.LOGGER.debug(message);
    }

    @Override
    public void serverTick() {
        this.gameEventHandler.onTickServer();
    }

    @Override
    public void logDirect(String message) {
        InternalBaritoneRuntime.LOGGER.info(message);
    }

    @Override
    public void logDirect(String message, net.minecraft.ChatFormatting color) {
        InternalBaritoneRuntime.LOGGER.info(message);
    }

    @Override
    public void logDirect(net.minecraft.network.chat.Component... components) {
        for (net.minecraft.network.chat.Component component : components) {
            logDirect(component.getString());
        }
    }

    public void shutdown() {
        this.pathingBehavior.shutdown();
        this.inputOverrideHandler.clearAllKeys();
        this.pathingControlManager.cancelEverything();
    }

    public MemoryBehavior getMemoryBehavior() {
        return this.memoryBehavior;
    }

    public InventoryBehavior getInventoryBehavior() {
        return this.inventoryBehavior;
    }
}
