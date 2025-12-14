package com.frenkvs.devmod.ui.editor.systems;

import net.minecraft.world.item.ItemStack;

/**
 * Facade used by the MultiEdit subsystem to apply presets to ItemStack instances.
 * Projects with domain-specific preset managers (eg. MobPresetManager) can implement
 * this interface to provide a unified API.
 */
public interface PresetManager {
    /**
     * Apply the preset to the provided item in the given slot index.
     * Implementations should be safe and avoid throwing; instead surface errors by returning false.
     * @return true if applied successfully
     */
    boolean applyPreset(Preset preset, ItemStack item, int slotIndex);
}
