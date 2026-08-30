package dev.dudie.baritonehelper.internal.baritone;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/** Small server-safe replacement for PlayerEngine's enchantment helper. */
public final class InternalEnchantmentUtils {
    private InternalEnchantmentUtils() {
    }

    public static int getEnchantmentLevel(ItemStack stack, Holder<Enchantment> enchantment) {
        if (stack.isEmpty()) {
            return 0;
        }
        var enchantments = stack.get(DataComponents.ENCHANTMENTS);
        return enchantments == null ? 0 : enchantments.getLevel(enchantment);
    }

    public static int getEnchantmentLevel(ItemStack stack, ResourceKey<Enchantment> enchantment) {
        if (stack.isEmpty()) {
            return 0;
        }
        var enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments == null) {
            return 0;
        }
        return enchantments.keySet().stream()
                .filter(holder -> holder.unwrapKey().map(enchantment::equals).orElse(false))
                .findFirst()
                .map(enchantments::getLevel)
                .orElse(0);
    }
}
