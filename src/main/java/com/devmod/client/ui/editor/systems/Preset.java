package com.devmod.client.ui.editor.systems;

import java.util.function.Predicate;

import net.minecraft.world.item.ItemStack;

public interface Preset {
    /**
     * Predicate to determine whether this preset applies to the given item.
     */
    Predicate<ItemStack> scope();
}
