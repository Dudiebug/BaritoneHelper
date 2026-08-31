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

package dev.dudie.baritonehelper.internal.baritone.api.entity;

import net.minecraft.resources.ResourceLocation;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Nameable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot.Type;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import dev.dudie.baritonehelper.internal.baritone.InternalBaritoneRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

public class LivingEntityInventory implements Container, Nameable {
   public static final Logger LOGGER = LogManager.getLogger(InternalBaritoneRuntime.MOD_NAME);
   public static final int ITEM_USAGE_COOLDOWN = 5;
   public static final int MAIN_SIZE = 36;
   private static final int HOTBAR_SIZE = 9;
   public static final int OFF_HAND_SLOT = 40;
   public static final int NOT_FOUND = -1;
   public static final int[] ARMOR_SLOTS = new int[]{0, 1, 2, 3};
   public static final int[] HELMET_SLOTS = new int[]{3};
   public final NonNullList<ItemStack> main;
   public final NonNullList<ItemStack> armor;
   public final NonNullList<ItemStack> offHand;
   private final List<NonNullList<ItemStack>> combinedInventory;
   @Nullable
   private final Container backingInventory;
   public int selectedSlot;
   public LivingEntity player;
   private int changeCount;

   public LivingEntityInventory(LivingEntity player) {
      this(player, player instanceof Container container ? container : null);
   }

   /**
    * Creates a view over an entity's existing inventory when one is available.
    * The view is deliberately list-shaped because Baritone accesses {@link #main}
    * directly, but every list mutation still goes to the canonical Container.
    */
   public LivingEntityInventory(LivingEntity player, @Nullable Container backingInventory) {
      this.player = player;
      this.backingInventory = backingInventory;
      if (backingInventory == null) {
         this.main = NonNullList.withSize(MAIN_SIZE, ItemStack.EMPTY);
         this.armor = NonNullList.withSize(4, ItemStack.EMPTY);
         this.offHand = NonNullList.withSize(1, ItemStack.EMPTY);
      } else {
         this.main = new CanonicalList(this, backingInventory);
         this.armor = NonNullList.create();
         this.offHand = NonNullList.create();
      }
      this.combinedInventory = ImmutableList.of(this.main, this.armor, this.offHand);
   }

   public ItemStack getMainHandStack() {
      this.normalizeSelectedSlot();
      return this.selectedSlot < this.main.size() ? this.main.get(this.selectedSlot) : ItemStack.EMPTY;
   }

   public static int getHotbarSize() {
      return HOTBAR_SIZE;
   }

   public void setSelectedSlot(int slot) {
      this.selectedSlot = Math.max(0, Math.min(HOTBAR_SIZE - 1, slot));
   }

   private void normalizeSelectedSlot() {
      this.setSelectedSlot(this.selectedSlot);
   }

   public ItemStack getStackInHand(InteractionHand hand) {
      if (hand == InteractionHand.MAIN_HAND) {
         return this.getMainHandStack();
      }
      return this.offHand.isEmpty() ? ItemStack.EMPTY : this.offHand.get(0);
   }

   public void setStackInHand(InteractionHand hand, ItemStack stack) {
      if (hand == InteractionHand.MAIN_HAND) {
         this.normalizeSelectedSlot();
         this.setItem(this.selectedSlot, stack);
      } else if (!this.offHand.isEmpty()) {
         this.setItem(this.main.size() + this.armor.size(), stack);
      }
   }

   private boolean canStackAddMore(ItemStack existingStack, ItemStack stack) {
      return !existingStack.isEmpty()
         && ItemStack.isSameItemSameComponents(existingStack, stack)
         && existingStack.isStackable()
         && existingStack.getCount() < existingStack.getMaxStackSize()
         && existingStack.getCount() < this.getMaxStackSize();
   }

   public int getEmptySlot() {
      for (int i = 0; i < this.main.size(); i++) {
         if (this.main.get(i).isEmpty()) {
            return i;
         }
      }

      return -1;
   }

   public void addPickBlock(ItemStack stack) {
      int i = this.getSlotWithStack(stack);
      if (isValidHotbarIndex(i)) {
         this.setSelectedSlot(i);
      } else if (i == -1) {
         this.setSelectedSlot(this.getSwappableHotbarSlot());
         if (!this.getItem(this.selectedSlot).isEmpty()) {
            int j = this.getEmptySlot();
            if (j != -1) {
               this.setItem(j, this.getItem(this.selectedSlot));
            }
         }

         this.setItem(this.selectedSlot, stack);
      } else {
         this.swapSlotWithHotbar(i);
      }
   }

   public void swapSlotWithHotbar(int slot) {
      if (slot < 0 || slot >= this.main.size()) {
         return;
      }
      this.setSelectedSlot(this.getSwappableHotbarSlot());
      ItemStack itemStack = this.getItem(this.selectedSlot);
      this.setItem(this.selectedSlot, this.getItem(slot));
      this.setItem(slot, itemStack);
   }

   public static boolean isValidHotbarIndex(int slot) {
      return slot >= 0 && slot < HOTBAR_SIZE;
   }

   public int getSlotWithStack(ItemStack stack) {
      for (int i = 0; i < this.main.size(); i++) {
         if (!this.main.get(i).isEmpty() && ItemStack.isSameItemSameComponents(stack, this.main.get(i))) {
            return i;
         }
      }

      return -1;
   }

   public int indexOf(ItemStack stack) {
      for (int i = 0; i < this.main.size(); i++) {
         ItemStack itemStack = this.main.get(i);
         if (!itemStack.isEmpty()
            && ItemStack.isSameItemSameComponents(stack, itemStack)
            && !itemStack.isDamaged()
            && !itemStack.isEnchanted()
            && !itemStack.has(DataComponents.CUSTOM_NAME)) {
            return i;
         }
      }

      return -1;
   }

   public int getSwappableHotbarSlot() {
      this.normalizeSelectedSlot();
      int hotbarSize = Math.min(HOTBAR_SIZE, this.main.size());
      for (int i = 0; i < hotbarSize; i++) {
         int j = (this.selectedSlot + i) % hotbarSize;
         if (this.main.get(j).isEmpty()) {
            return j;
         }
      }

      for (int ix = 0; ix < hotbarSize; ix++) {
         int j = (this.selectedSlot + ix) % hotbarSize;
         if (!this.main.get(j).isEnchanted()) {
            return j;
         }
      }

      return hotbarSize == 0 ? 0 : this.selectedSlot % hotbarSize;
   }

   public void scrollInHotbar(double scrollAmount) {
      int i = (int)Math.signum(scrollAmount);
      this.setSelectedSlot(Math.floorMod(this.selectedSlot - i, HOTBAR_SIZE));
   }

   public int remove(Predicate<ItemStack> shouldRemove, int maxCount, Container craftingInventory) {
      int i = 0;
      boolean bl = maxCount == 0;
      i += ContainerHelper.clearOrCountMatchingItems(this, shouldRemove, maxCount - i, bl);
      return i + ContainerHelper.clearOrCountMatchingItems(craftingInventory, shouldRemove, maxCount - i, bl);
   }

   private int addStack(ItemStack stack) {
      int i = this.getOccupiedSlotWithRoomForStack(stack);
      if (i == -1) {
         i = this.getEmptySlot();
      }

      return i == -1 ? stack.getCount() : this.addStack(i, stack);
   }

   private int addStack(int slot, ItemStack stack) {
      Item item = stack.getItem();
      int i = stack.getCount();
      ItemStack itemStack = this.getItem(slot);
      if (itemStack.isEmpty()) {
         itemStack = new ItemStack(item, 0);
          itemStack.applyComponents(stack.getComponents());

          this.setItem(slot, itemStack);
      }

      int j = i;
      if (i > itemStack.getMaxStackSize() - itemStack.getCount()) {
         j = itemStack.getMaxStackSize() - itemStack.getCount();
      }

      if (j > this.getMaxStackSize() - itemStack.getCount()) {
         j = this.getMaxStackSize() - itemStack.getCount();
      }

      if (j == 0) {
         return i;
      } else {
         i -= j;
         itemStack.grow(j);
         itemStack.setPopTime(5);
         this.setChanged();
         return i;
      }
   }

   public int getOccupiedSlotWithRoomForStack(ItemStack stack) {
      this.normalizeSelectedSlot();
      if (this.canStackAddMore(this.getMainHandStack(), stack)) {
         return this.selectedSlot;
      } else if (this.offHand.size() == 1 && this.canStackAddMore(this.offHand.get(0), stack)) {
         return OFF_HAND_SLOT;
      } else {
         for (int i = 0; i < this.main.size(); i++) {
            if (this.canStackAddMore(this.main.get(i), stack)) {
               return i;
            }
         }

         return -1;
      }
   }

   public void updateItems() {
      for (NonNullList<ItemStack> defaultedList : this.combinedInventory) {
         for (int i = 0; i < defaultedList.size(); i++) {
            if (!defaultedList.get(i).isEmpty()) {
               defaultedList.get(i).inventoryTick(this.player.level(), this.player, i, this.selectedSlot == i);
            }
         }
      }
   }

   public boolean insertStack(ItemStack stack) {
      return this.insertStack(-1, stack);
   }

   public boolean insertStack(int slot, ItemStack stack) {
      if (stack == null || stack.isEmpty()) {
         return false;
      } else {
         try {
            if (stack.isDamaged()) {
               if (slot == -1) {
                  slot = this.getEmptySlot();
               }

               if (slot >= 0) {
                  this.setItem(slot, stack.copyAndClear());
                  this.getItem(slot).setPopTime(5);
                  this.setChanged();
                  return true;
               } else {
                  return false;
               }
            } else {
               int i;
               do {
                  i = stack.getCount();
                  if (slot == -1) {
                     stack.setCount(this.addStack(stack));
                  } else {
                     stack.setCount(this.addStack(slot, stack));
                  }
               } while (!stack.isEmpty() && stack.getCount() < i);

               return stack.getCount() < i;
            }
         } catch (Throwable var6) {
            CrashReport crashReport = CrashReport.forThrowable(var6, "Adding item to inventory");
            CrashReportCategory crashReportSection = crashReport.addCategory("Item being added");
            crashReportSection.setDetail("Item ID", Item.getId(stack.getItem()));
            crashReportSection.setDetail("Item data", stack.getDamageValue());
            crashReportSection.setDetail("Item name", () -> stack.getHoverName().getString());
            throw new ReportedException(crashReport);
         }
      }
   }

   public ItemStack removeItem(int slot, int amount) {
      if (slot < 0 || slot >= this.getContainerSize() || amount <= 0) {
         return ItemStack.EMPTY;
      }

      ItemStack existing = this.getItem(slot);
      if (existing.isEmpty()) {
         return ItemStack.EMPTY;
      }

      ItemStack removed = existing.split(amount);
      this.setItem(slot, existing);
      return removed;
   }

   public void removeOne(ItemStack stack) {
      int offset = 0;
      for (NonNullList<ItemStack> defaultedList : this.combinedInventory) {
         for (int i = 0; i < defaultedList.size(); i++) {
            if (defaultedList.get(i) == stack) {
               if (defaultedList == this.main) {
                  this.setItem(offset + i, ItemStack.EMPTY);
               } else {
                  defaultedList.set(i, ItemStack.EMPTY);
                  this.setChanged();
               }
               break;
            }
         }
         offset += defaultedList.size();
      }
   }

   public ItemStack removeItemNoUpdate(int slot) {
      if (slot < 0 || slot >= this.getContainerSize()) {
         return ItemStack.EMPTY;
      }
      if (this.backingInventory != null) {
         return this.backingInventory.removeItemNoUpdate(slot);
      }

      ItemStack itemStack = this.getItem(slot);
      if (!itemStack.isEmpty()) {
         this.setItem(slot, ItemStack.EMPTY);
         return itemStack;
      }
      return ItemStack.EMPTY;
   }

   public void setItem(int slot, ItemStack stack) {
      if (slot < 0 || slot >= this.getContainerSize()) {
         return;
      }
      if (stack == null) {
         stack = ItemStack.EMPTY;
      }
      int limit = Math.min(this.getMaxStackSize(), stack.getMaxStackSize());
      if (stack.getCount() > limit) {
         stack = stack.copyWithCount(limit);
      }

      if (this.backingInventory != null) {
         this.backingInventory.setItem(slot, stack);
         return;
      }

      int remaining = slot;
      for (NonNullList<ItemStack> defaultedList : this.combinedInventory) {
         if (remaining < defaultedList.size()) {
            defaultedList.set(remaining, stack);
            this.setChanged();
            return;
         }
         remaining -= defaultedList.size();
      }
   }

   public float getBlockBreakingSpeed(BlockState block) {
      return this.getMainHandStack().getDestroySpeed(block);
   }

   private void writeItemTag(HolderLookup.Provider levelRegistryAccess, ItemStack stack, ListTag nbtList, int index){
      if(stack.isEmpty()){
         return;
      }
      LOGGER.info("Writing itemTag={}");
      CompoundTag itemTag = new CompoundTag();
      stack.save(levelRegistryAccess, itemTag);

      if (!itemTag.contains("id")) {
         ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
         if (key != null) {
            itemTag.putString("id", key.toString());
            itemTag.putByte("Count", (byte) stack.getCount());
         } else{
            LOGGER.info("ERR writing item: key={}, itemTag={} ", key, itemTag);
         }
      }
      itemTag.putByte("Slot", (byte)index);
      nbtList.add(itemTag);
      LOGGER.info("Done writing itemTag={}");
   }

   public ListTag writeNbt(HolderLookup.Provider levelRegistryAccess, ListTag nbtList) {
      LOGGER.info("writeNBT inventory");
      for (int i = 0; i < this.main.size(); i++) {
         ItemStack stack = this.main.get(i);
         writeItemTag(levelRegistryAccess, stack, nbtList, i);
      }
   
      // armor
      for (int ix = 0; ix < this.armor.size(); ix++) {
         ItemStack stack = this.armor.get(ix);
         writeItemTag(levelRegistryAccess, stack, nbtList, ix + 100);
      }
   
      // offHand
      for (int ixx = 0; ixx < this.offHand.size(); ixx++) {
         ItemStack stack = this.offHand.get(ixx);
         writeItemTag(levelRegistryAccess, stack, nbtList, ixx + 150);
      }
   
      LOGGER.info("DONE writeNBT inventory");
      return nbtList;
   }
   public void readNbt(HolderLookup.Provider levelRegistryAccess, ListTag nbtList) {
      LOGGER.info("writeNBT inventory");
      this.clearContent();
      
      for (int i = 0; i < nbtList.size(); i++) {
         CompoundTag nbtCompound = nbtList.getCompound(i);
         int j = nbtCompound.getByte("Slot") & 255;
         ItemStack itemStack = ItemStack.parseOptional(levelRegistryAccess, nbtCompound);
         if (!itemStack.isEmpty()) {
            LOGGER.info("Reading stack {}", itemStack);
            if (j >= 0 && j < this.main.size()) {
               this.main.set(j, itemStack);
            } else if (j >= 100 && j < this.armor.size() + 100) {
               this.armor.set(j - 100, itemStack);
            } else if (j >= 150 && j < this.offHand.size() + 150) {
               this.offHand.set(j - 150, itemStack);
            }
         }
      }
      LOGGER.info("DONE writeNBT inventory");
   }

   public int getContainerSize() {
      return this.backingInventory == null
         ? this.main.size() + this.armor.size() + this.offHand.size()
         : this.backingInventory.getContainerSize();
   }

   public boolean isEmpty() {
      for (ItemStack itemStack : this.main) {
         if (!itemStack.isEmpty()) {
            return false;
         }
      }

      for (ItemStack itemStackx : this.armor) {
         if (!itemStackx.isEmpty()) {
            return false;
         }
      }

      for (ItemStack itemStackxx : this.offHand) {
         if (!itemStackxx.isEmpty()) {
            return false;
         }
      }

      return true;
   }

   public ItemStack getItem(int slot) {
      if (slot < 0 || slot >= this.getContainerSize()) {
         return ItemStack.EMPTY;
      }
      if (this.backingInventory != null) {
         ItemStack stack = this.backingInventory.getItem(slot);
         return stack == null ? ItemStack.EMPTY : stack;
      }

      int remaining = slot;
      for (NonNullList<ItemStack> defaultedList : this.combinedInventory) {
         if (remaining < defaultedList.size()) {
            return defaultedList.get(remaining);
         }
         remaining -= defaultedList.size();
      }
      return ItemStack.EMPTY;
   }

   public Component getName() {
      return Component.translatable("container.inventory");
   }

   public ItemStack getArmorStack(int slot) {
      return slot >= 0 && slot < this.armor.size() ? this.armor.get(slot) : ItemStack.EMPTY;
   }

   public void damageArmor(DamageSource damageSource, float amount, int[] slots) {
      if (!(amount <= 0.0F)) {
         amount /= 4.0F;
         if (amount < 1.0F) {
            amount = 1.0F;
         }

          for (int i : slots) {
            if (i < 0 || i >= this.armor.size()) {
               continue;
            }
            ItemStack itemStack = this.armor.get(i);
            if ((!damageSource.is(DamageTypeTags.IS_FIRE) || !itemStack.getItem().components().has(DataComponents.FIRE_RESISTANT)) && itemStack.getItem() instanceof ArmorItem) {
               itemStack.hurtAndBreak((int)amount, this.player, this.player.getEquipmentSlotForItem(itemStack));
            }
         }
      }
   }

   public void dropAll() {
      for (int slot = 0; slot < this.getContainerSize(); slot++) {
         ItemStack itemStack = this.getItem(slot);
         if (!itemStack.isEmpty()) {
            this.player.spawnAtLocation(itemStack);
            this.setItem(slot, ItemStack.EMPTY);
         }
      }
   }

   public void setChanged() {
      this.changeCount++;
      if (this.backingInventory != null) {
         this.backingInventory.setChanged();
      }
   }

   public int getChangeCount() {
      return this.changeCount;
   }

   public boolean stillValid(Player player) {
      return this.player.isRemoved() ? false : !(player.distanceToSqr(this.player) > 64.0);
   }

   public boolean contains(ItemStack stack) {
      for (List<ItemStack> list : this.combinedInventory) {
         for (ItemStack itemStack : list) {
            if (!itemStack.isEmpty() && ItemStack.isSameItemSameComponents(itemStack, stack)) {
               return true;
            }
         }
      }

      return false;
   }

   public boolean contains(TagKey<Item> key) {
      for (List<ItemStack> list : this.combinedInventory) {
         for (ItemStack itemStack : list) {
            if (!itemStack.isEmpty() && itemStack.is(key)) {
               return true;
            }
         }
      }

      return false;
   }

   public void clone(LivingEntityInventory other) {
      int size = Math.min(this.getContainerSize(), other.getContainerSize());
      for (int i = 0; i < size; i++) {
         this.setItem(i, other.getItem(i).copy());
      }
      for (int i = size; i < this.getContainerSize(); i++) {
         this.setItem(i, ItemStack.EMPTY);
      }

      this.setSelectedSlot(other.selectedSlot);
   }

   public void clearContent() {
      if (this.backingInventory != null) {
         this.backingInventory.clearContent();
         this.changeCount++;
         return;
      }

      for (NonNullList<ItemStack> list : this.combinedInventory) {
         for (int i = 0; i < list.size(); i++) {
            list.set(i, ItemStack.EMPTY);
         }
      }
      this.setChanged();
   }

   public void populateRecipeFinder(StackedContents finder) {
      for (ItemStack itemStack : this.main) {
         finder.accountSimpleStack(itemStack);
      }
   }

   public ItemStack dropSelectedItem(boolean entireStack) {
      this.normalizeSelectedSlot();
      ItemStack itemStack = this.getMainHandStack();
      return itemStack.isEmpty() ? ItemStack.EMPTY : this.removeItem(this.selectedSlot, entireStack ? itemStack.getCount() : 1);
   }

   private static final class CanonicalList extends NonNullList<ItemStack> {
      private final LivingEntityInventory owner;
      private final Container backingInventory;

      private CanonicalList(LivingEntityInventory owner, Container backingInventory) {
         super(List.of(), ItemStack.EMPTY);
         this.owner = owner;
         this.backingInventory = backingInventory;
      }

      @Override
      public ItemStack get(int index) {
         this.checkIndex(index);
         ItemStack stack = this.backingInventory.getItem(index);
         return stack == null ? ItemStack.EMPTY : stack;
      }

      @Override
      public ItemStack set(int index, ItemStack stack) {
         this.checkIndex(index);
         ItemStack previous = this.get(index);
         this.owner.setItem(index, stack);
         return previous;
      }

      @Override
      public void add(int index, ItemStack stack) {
         throw new UnsupportedOperationException("A canonical inventory has fixed slots");
      }

      @Override
      public ItemStack remove(int index) {
         this.checkIndex(index);
         ItemStack previous = this.get(index);
         this.backingInventory.removeItemNoUpdate(index);
         return previous;
      }

      @Override
      public int size() {
         return this.backingInventory.getContainerSize();
      }

      @Override
      public void clear() {
         this.owner.clearContent();
      }

      private void checkIndex(int index) {
         if (index < 0 || index >= this.size()) {
            throw new IndexOutOfBoundsException("index=" + index + ", size=" + this.size());
         }
      }
   }
}
