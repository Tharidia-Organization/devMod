package com.frenkvs.devmod.ui.editor.systems;

import com.frenkvs.devmod.ItemEditorDataManager;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Preset wrapper for ItemEditorDataManager.PresetData
 */
public class DataPreset implements Preset {
    private final ItemEditorDataManager.PresetData data;

    public DataPreset(ItemEditorDataManager.PresetData data) {
        this.data = Objects.requireNonNull(data);
    }

    public ItemEditorDataManager.PresetData getData() { return data; }

    @Override
    public Predicate<ItemStack> scope() {
        return stack -> {
            if (stack == null || stack.isEmpty()) return false;
            if (data == null) return false;
            // GLOBAL scope applies to any item
            if ("GLOBAL".equalsIgnoreCase(data.scope)) return true;
            // If itemType not specified, treat as applicable to any item
            if (data.itemType == null || data.itemType.isBlank()) return true;

            try {
                // Avoid nullness conversion issues by using the item's string form as a best-effort match
                String id = stack.getItem().toString().toLowerCase();
                return id.contains(data.itemType.toLowerCase());
            } catch (Exception e) {
                return false;
            }
        };
    }
}
