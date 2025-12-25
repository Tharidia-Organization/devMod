package com.devmod.client.ui.editor.systems;

import net.minecraft.world.item.ItemStack;
import java.util.function.Predicate;

/**
 * Lightweight Preset abstraction used by the MultiEdit subsystem.
 */
public interface Preset {
    /**
     * Predicate to determine whether this preset applies to the given item.
     */
    Predicate<ItemStack> scope();
}
