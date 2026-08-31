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

package dev.dudie.baritonehelper.internal.baritone.behavior;

import dev.dudie.baritonehelper.entity.WorkerEntity;
import dev.dudie.baritonehelper.internal.baritone.Baritone;
import dev.dudie.baritonehelper.internal.baritone.api.entity.IInventoryProvider;
import dev.dudie.baritonehelper.internal.baritone.api.entity.LivingEntityInventory;
import dev.dudie.baritonehelper.internal.baritone.utils.ToolSet;
import java.util.ArrayList;
import java.util.OptionalInt;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class InventoryBehavior extends Behavior {
   public InventoryBehavior(Baritone baritone) {
      super(baritone);
   }

   private static Player asPlayer(LivingEntity entity) {
      return entity instanceof Player player ? player : null;
   }

   @Override
   public void onTickServer() {
      if (this.baritone.settings().allowInventory.get()) {
         if (this.ctx.entity() instanceof IInventoryProvider player) {
            LivingEntityInventory inventory = player.getLivingInventory();
            int throwaway = this.firstValidThrowaway(inventory);
            if (throwaway >= LivingEntityInventory.getHotbarSize()) {
               this.swapWithHotBar(throwaway, LivingEntityInventory.getHotbarSize() - 1, inventory);
            }

            int pick = this.bestToolAgainst(Blocks.STONE, PickaxeItem.class);
            if (pick >= LivingEntityInventory.getHotbarSize()) {
               for (int i = 0; i < LivingEntityInventory.getHotbarSize(); i++) {
                  if (inventory.getItem(i).getItem() != Items.BUCKET) {
                     this.swapWithHotBar(pick, i, inventory);
                     break;
                  }
               }
            }
         }
      }
   }

   public void attemptToPutOnHotbar(int inMainInvy, Predicate<Integer> disallowedHotbar, LivingEntityInventory inventory) {
      OptionalInt destination = this.getTempHotbarSlot(disallowedHotbar, inventory);
      if (destination.isPresent()) {
         this.swapWithHotBar(inMainInvy, destination.getAsInt(), inventory);
      }
   }

   public OptionalInt getTempHotbarSlot(Predicate<Integer> disallowedHotbar) {
      LivingEntityInventory inventory = this.ctx.inventory();
      return inventory == null ? OptionalInt.empty() : this.getTempHotbarSlot(disallowedHotbar, inventory);
   }

   private OptionalInt getTempHotbarSlot(Predicate<Integer> disallowedHotbar, LivingEntityInventory inventory) {
      if (inventory == null) {
         return OptionalInt.empty();
      } else {
         ArrayList<Integer> candidates = new ArrayList<>();
         int hotbarSize = Math.min(LivingEntityInventory.getHotbarSize(), inventory.main.size());

         for (int i = 0; i < hotbarSize; i++) {
            if (inventory.main.get(i).isEmpty() && !disallowedHotbar.test(i)) {
               candidates.add(i);
            }
         }

         if (candidates.isEmpty()) {
            for (int ix = 0; ix < hotbarSize; ix++) {
               if (!disallowedHotbar.test(ix)) {
                  candidates.add(ix);
               }
            }
         }

          return candidates.isEmpty()
             ? OptionalInt.empty()
             : OptionalInt.of(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())));
      }
   }

   private void swapWithHotBar(int inInventory, int inHotbar, LivingEntityInventory inventory) {
      if (inInventory < 0 || inInventory >= inventory.main.size()
         || inHotbar >= inventory.main.size()
         || !LivingEntityInventory.isValidHotbarIndex(inHotbar)) {
         return;
      }
      ItemStack h = inventory.getItem(inHotbar);
      inventory.setItem(inHotbar, inventory.getItem(inInventory));
      inventory.setItem(inInventory, h);
   }

   private int firstValidThrowaway(LivingEntityInventory inventory) {
      NonNullList<ItemStack> invy = inventory.main;

      for (int i = 0; i < invy.size(); i++) {
          if (this.isAllowedThrowaway(invy.get(i))) {
            return i;
         }
      }

      return -1;
   }

   private int bestToolAgainst(Block against, Class<? extends TieredItem> cla$$) {
      LivingEntityInventory inventory = this.ctx.inventory();
      if (inventory == null) {
         return -1;
      }
      NonNullList<ItemStack> invy = inventory.main;
      int bestInd = -1;
      double bestSpeed = -1.0;

      for (int i = 0; i < invy.size(); i++) {
          ItemStack stack = invy.get(i);
         if (!stack.isEmpty()
            && (!this.baritone.settings().itemSaver.get() || stack.getDamageValue() < stack.getMaxDamage() || stack.getMaxDamage() <= 1)
            && cla$$.isInstance(stack.getItem())) {
            double speed = ToolSet.calculateSpeedVsBlock(stack, against.defaultBlockState());
            if (speed > bestSpeed) {
               bestSpeed = speed;
               bestInd = i;
            }
         }
      }

      return bestInd;
   }

   public boolean hasGenericThrowaway() {
      return this.throwaway(false, this::isAllowedThrowaway);
   }

   private boolean isAllowedThrowaway(ItemStack stack) {
      if (!(this.ctx.entity() instanceof WorkerEntity worker)) {
         return this.baritone.settings().acceptableThrowawayItems.get().contains(stack.getItem());
      }
      if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
      return worker.configuration().pathing().allowsTraversal(
            BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()));
   }

   public boolean selectThrowawayForLocation(boolean select, int x, int y, int z) {
      // The collector has no schematic builder process.  Movement code asks us
      // only to expose a real block item, so the configured traversal palette
      // is the safe fallback here.
      BlockState maybe = this.baritone.bsi == null ? null : this.baritone.bsi.get0(x, y, z);
      if (maybe != null
         && this.throwaway(
            select,
            stack -> stack.getItem() instanceof BlockItem
               && maybe.equals(
                  ((BlockItem)stack.getItem())
                     .getBlock()
                     .getStateForPlacement(
                        new BlockPlaceContext(
                           new UseOnContext(
                               this.ctx.world(),
                               asPlayer(this.ctx.entity()),
                              InteractionHand.MAIN_HAND,
                              stack,
                              new BlockHitResult(
                                 new Vec3(this.ctx.entity().getX(), this.ctx.entity().getY(), this.ctx.entity().getZ()),
                                 Direction.UP,
                                 this.ctx.feetPos(),
                                 false
                              )
                           ) {
                              public boolean isSecondaryUseActive() {
                                 return false;
                              }
                           }
                        )
                     )
               )
         )) {
         return true;
      } else {
         if (maybe != null && this.throwaway(
               select,
               stack -> stack.getItem() instanceof BlockItem
                  && this.isAllowedThrowaway(stack)
                  && ((BlockItem)stack.getItem()).getBlock().equals(maybe.getBlock()))) {
            return true;
         }
         return this.throwaway(select, this::isAllowedThrowaway);
      }
   }

   public boolean throwaway(boolean select, Predicate<? super ItemStack> desired) {
      if (!(this.ctx.entity() instanceof IInventoryProvider p)) {
         return false;
      } else {
         LivingEntityInventory inventory = p.getLivingInventory();
         NonNullList<ItemStack> var7 = inventory.main;

         for (int i = 0; i < LivingEntityInventory.getHotbarSize() && i < var7.size(); i++) {
            ItemStack item = var7.get(i);
            if (desired.test(item)) {
               if (select) {
                  inventory.setSelectedSlot(i);
               }

               return true;
            }
         }

         if (desired.test(inventory.getStackInHand(InteractionHand.OFF_HAND))) {
            for (int ix = 0; ix < LivingEntityInventory.getHotbarSize() && ix < var7.size(); ix++) {
               ItemStack item = var7.get(ix);
               if (item.isEmpty() || item.getItem() instanceof PickaxeItem) {
                  if (select) {
                     inventory.setSelectedSlot(ix);
                  }

                  return true;
               }
            }
         }

         return false;
      }
   }

   public static int getSlotWithStack(LivingEntityInventory inv, java.util.Set<Item> items) {
      if (inv == null || items == null) {
         return -1;
      }
      for (int i = 0; i < inv.main.size(); i++) {
         if (!inv.main.get(i).isEmpty() && items.contains(inv.main.get(i).getItem())) {
            return i;
         }
      }

      return -1;
   }
}
