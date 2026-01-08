package com.devmod.util;

import java.util.Objects;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

public final class ItemStackResetUtils {

    private ItemStackResetUtils() {}

    /**
     * Remove non-editor custom components like name, lore, and enchantments.
     */
    public static void clearNonEditorComponents(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        try {
            stack.remove(Objects.requireNonNull(DataComponents.CUSTOM_NAME));
        } catch (Exception ignored) {
            // best effort
        }
        try {
            stack.remove(Objects.requireNonNull(DataComponents.LORE));
        } catch (Exception ignored) {
            // best effort
        }
        try {
            stack.remove(Objects.requireNonNull(DataComponents.ENCHANTMENTS));
        } catch (Exception ignored) {
            // best effort
        }
        try {
            stack.remove(Objects.requireNonNull(DataComponents.STORED_ENCHANTMENTS));
        } catch (Exception ignored) {
            // best effort
        }
    }
}
