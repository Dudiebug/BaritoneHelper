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

import dev.dudie.baritonehelper.internal.baritone.api.IBaritone;
import dev.dudie.baritonehelper.internal.baritone.BaritoneEntity;
import dev.dudie.baritonehelper.internal.baritone.api.entity.IInventoryProvider;
import dev.dudie.baritonehelper.internal.baritone.api.entity.LivingEntityInventory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import dev.dudie.baritonehelper.internal.baritone.InternalEnchantmentUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class ToolSet {
   private final Map<Block, Double> breakStrengthCache = new HashMap<>();
   private final Function<Block, Double> backendCalculation;
   private final List<ItemStack> hotbar;
   private final int selectedSlot;
   private final boolean disableAutoTool;
   private final boolean useSwordToMine;
   private final boolean itemSaver;
   private final Set<Block> blocksToAvoidBreaking;
   private final double avoidBreakingMultiplier;

   public ToolSet(LivingEntity player) {
      IBaritone baritone = player instanceof BaritoneEntity holder
         ? holder.baritoneEngine()
         : throwMissingEngine(player);
      IInventoryProvider inventoryProvider = (IInventoryProvider)player;
      LivingEntityInventory inventory = inventoryProvider.getLivingInventory();
      this.selectedSlot = Math.max(0, Math.min(LivingEntityInventory.getHotbarSize() - 1, inventory.selectedSlot));
      List<ItemStack> hotbarCopy = new ArrayList<>(LivingEntityInventory.getHotbarSize());
      for (int index = 0; index < LivingEntityInventory.getHotbarSize(); index++) {
         hotbarCopy.add(inventory.getItem(index).copy());
      }
      this.hotbar = List.copyOf(hotbarCopy);
      this.disableAutoTool = baritone.settings().disableAutoTool.get();
      this.useSwordToMine = baritone.settings().useSwordToMine.get();
      this.itemSaver = baritone.settings().itemSaver.get();
      this.blocksToAvoidBreaking = Set.copyOf(baritone.settings().blocksToAvoidBreaking.get());
      this.avoidBreakingMultiplier = baritone.settings().avoidBreakingMultiplier.get();
      if (baritone.settings().considerPotionEffects.get()) {
         double amplifier = potionAmplifier(player);
         Function<Double, Double> amplify = x -> amplifier * x;
         this.backendCalculation = amplify.compose(this::getBestDestructionTime);
      } else {
         this.backendCalculation = this::getBestDestructionTime;
      }
   }

   private static IBaritone throwMissingEngine(LivingEntity player) {
      throw new IllegalStateException("Baritone engine is not attached to " + player.getUUID());
   }

   public double getStrVsBlock(BlockState state) {
      return this.breakStrengthCache.computeIfAbsent(state.getBlock(), this.backendCalculation);
   }

   private int getMaterialCost(ItemStack itemStack) {
      return itemStack.getItem() instanceof TieredItem ? 1 : -1;
   }

   public boolean hasSilkTouch(ItemStack stack) {
      return InternalEnchantmentUtils.getEnchantmentLevel(stack, Enchantments.SILK_TOUCH)!=0;
   }

   public int getBestSlot(Block b, boolean preferSilkTouch) {
      return this.getBestSlot(b.defaultBlockState(), preferSilkTouch, false);
   }

   public int getBestSlot(Block b, boolean preferSilkTouch, boolean pathingCalculation) {
      return this.getBestSlot(b.defaultBlockState(), preferSilkTouch, pathingCalculation);
   }

   public int getBestSlot(BlockState blockState, boolean preferSilkTouch) {
      return this.getBestSlot(blockState, preferSilkTouch, false);
   }

   public int getBestSlot(BlockState blockState, boolean preferSilkTouch, boolean pathingCalculation) {
      if (blockState.getDestroySpeed(null, null) == 0.0F) {
         return this.selectedSlot;
      } else if (this.disableAutoTool && pathingCalculation) {
         return this.selectedSlot;
      } else {
         int best = this.selectedSlot;
         double highestSpeed = Double.NEGATIVE_INFINITY;
         int lowestCost = Integer.MIN_VALUE;
         boolean bestSilkTouch = false;

         for (int i = 0; i < LivingEntityInventory.getHotbarSize(); i++) {
            ItemStack itemStack = this.hotbar.get(i);
            if ((this.useSwordToMine || !(itemStack.getItem() instanceof SwordItem))
               && (!this.itemSaver || itemStack.getDamageValue() < itemStack.getMaxDamage() || itemStack.getMaxDamage() <= 1)) {
               double speed = calculateSpeedVsBlock(itemStack, blockState);
               boolean silkTouch = this.hasSilkTouch(itemStack);
               if (speed > highestSpeed) {
                  highestSpeed = speed;
                  best = i;
                  lowestCost = this.getMaterialCost(itemStack);
                  bestSilkTouch = silkTouch;
               } else if (speed == highestSpeed) {
                  int cost = this.getMaterialCost(itemStack);
                  if (cost < lowestCost && (silkTouch || !bestSilkTouch) || preferSilkTouch && !bestSilkTouch && silkTouch) {
                     highestSpeed = speed;
                     best = i;
                     lowestCost = cost;
                     bestSilkTouch = silkTouch;
                  }
               }
            }
         }

         return best;
      }
   }

   private double getBestDestructionTime(Block b) {
      ItemStack stack = this.hotbar.get(this.getBestSlot(b, false, true));
      return calculateSpeedVsBlock(stack, b.defaultBlockState()) * this.avoidanceMultiplier(b);
   }

   private double avoidanceMultiplier(Block b) {
      return this.blocksToAvoidBreaking.contains(b.builtInRegistryHolder().value())
            ? this.avoidBreakingMultiplier : 1.0;
   }

   public static double calculateSpeedVsBlock(ItemStack item, BlockState state) {
      float hardness = state.getDestroySpeed(null, null);
      if (hardness < 0.0F) {
         return -1.0;
      } else {
         float speed = item.getDestroySpeed(state);
         if (speed > 1.0F) {
            int effLevel = InternalEnchantmentUtils.getEnchantmentLevel(item, Enchantments.EFFICIENCY);
            if (effLevel > 0 && !item.isEmpty()) {
               speed += effLevel * effLevel + 1;
            }
         }

         speed /= hardness;
         return state.requiresCorrectToolForDrops() && (item.isEmpty() || !item.isCorrectToolForDrops(state)) ? speed / 100.0F : speed / 30.0F;
      }
   }

   private static double potionAmplifier(LivingEntity player) {
      double speed = 1.0;
      MobEffectInstance hasteEffect = player.getEffect(MobEffects.DIG_SPEED);
      if (hasteEffect != null) {
         speed *= 1.0 + (hasteEffect.getAmplifier() + 1) * 0.2;
      }

      MobEffectInstance fatigueEffect = player.getEffect(MobEffects.DIG_SLOWDOWN);
      if (fatigueEffect != null) {
         switch (fatigueEffect.getAmplifier()) {
            case 0:
               speed *= 0.3;
               break;
            case 1:
               speed *= 0.09;
               break;
            case 2:
               speed *= 0.0027;
               break;
            default:
               speed *= 8.1E-4;
         }
      }

      return speed;
   }
}
