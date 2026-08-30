/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.dudie.baritonehelper.internal.baritone.api;

import dev.dudie.baritonehelper.internal.baritone.Baritone;
import dev.dudie.baritonehelper.internal.baritone.api.behavior.ILookBehavior;
import dev.dudie.baritonehelper.internal.baritone.api.behavior.IPathingBehavior;
import dev.dudie.baritonehelper.internal.baritone.api.cache.IWorldProvider;
import dev.dudie.baritonehelper.internal.baritone.api.event.listener.IEventBus;
import dev.dudie.baritonehelper.internal.baritone.api.pathing.calc.IPathingControlManager;
import dev.dudie.baritonehelper.internal.baritone.api.process.ICustomGoalProcess;
import dev.dudie.baritonehelper.internal.baritone.api.process.IGetToBlockProcess;
import dev.dudie.baritonehelper.internal.baritone.api.process.IMineProcess;
import dev.dudie.baritonehelper.internal.baritone.api.utils.IEntityContext;
import dev.dudie.baritonehelper.internal.baritone.api.utils.IInputOverrideHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public interface IBaritone {
    IPathingBehavior getPathingBehavior();
    ILookBehavior getLookBehavior();
    IMineProcess getMineProcess();
    ICustomGoalProcess getCustomGoalProcess();
    IGetToBlockProcess getGetToBlockProcess();
    IWorldProvider getWorldProvider();
    IPathingControlManager getPathingControlManager();
    IInputOverrideHandler getInputOverrideHandler();
    IEntityContext getEntityContext();
    IEventBus getGameEventHandler();
    void logDebug(String message);

    default void logDirect(String message) {
        logDebug(message);
    }

    default void logDirect(String message, ChatFormatting color) {
        logDebug(message);
    }

    default void logDirect(Component... components) {
        for (Component component : components) {
            logDebug(component.getString());
        }
    }
    boolean isActive();
    Settings settings();
    void serverTick();

    static IBaritone create(net.minecraft.world.entity.LivingEntity entity) {
        return new Baritone(entity);
    }
}
