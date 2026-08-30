/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.dudie.baritonehelper.internal.baritone.utils;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class Debug {
   private static final Logger LOGGER = LogUtils.getLogger();

   public static void logInternal(String message) {
      LOGGER.debug("{}", message);
   }

   public static void logMessage(String message) {
      LOGGER.info("{}", message);
   }

   public static void logDebug(String message) {
      LOGGER.debug("{}", message);
   }

   public static void logError(String message) {
      LOGGER.error("{}", message);
   }
}
