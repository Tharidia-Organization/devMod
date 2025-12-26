package com.devmod.client.ui.editor.systems;

import net.minecraft.world.item.ItemStack;

public interface PresetManager {
    /**
     * Apply the preset to the provided item in the given slot index.
     * Implementations should be safe and avoid throwing; instead surface errors by returning false.
     * @return true if applied successfully
     */
    boolean applyPreset(Preset preset, ItemStack item, int slotIndex);
}
