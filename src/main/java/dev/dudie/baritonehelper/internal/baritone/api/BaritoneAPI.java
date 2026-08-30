/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.dudie.baritonehelper.internal.baritone.api;

import net.minecraft.network.chat.Component;

/** Server-only global settings facade retained for the extracted engine API. */
public final class BaritoneAPI {
    private static final Settings GLOBAL_SETTINGS = new Settings();
    private static final Component PREFIX = Component.literal("[Baritone Helper]");

    private BaritoneAPI() {
    }

    public static Settings getGlobalSettings() {
        return GLOBAL_SETTINGS;
    }

    public static Component getPrefix() {
        return PREFIX;
    }
}
