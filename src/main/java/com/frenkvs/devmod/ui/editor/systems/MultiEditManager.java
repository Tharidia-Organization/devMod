package com.frenkvs.devmod.ui.editor.systems;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Manages editing multiple items simultaneously.
 * Lightweight implementation matching the design doc `EDITOR_DESIGN_SYSTEM.md`.
 */
public class MultiEditManager {

    private final List<ItemStack> selectedItems = new ArrayList<>();
    private final List<Integer> selectedSlots = new ArrayList<>();

    public enum MultiEditMode {
        SINGLE, BATCH_PRESET, BATCH_EDIT, SELECTION
    }

    private MultiEditMode mode = MultiEditMode.SINGLE;

    public void addToSelection(ItemStack item, int slot) {
        Objects.requireNonNull(item, "item cannot be null");
        if (item.isEmpty()) return;
        // Avoid duplicate same-item instances
        if (selectedItems.stream().noneMatch(s -> ItemStack.isSameItem(s, item))) {
            selectedItems.add(item.copy());
            selectedSlots.add(slot);
        }
    }

    public void removeFromSelection(int index) {
        if (index >= 0 && index < selectedItems.size()) {
            selectedItems.remove(index);
            selectedSlots.remove(index);
        }
    }

    public void clearSelection() {
        selectedItems.clear();
        selectedSlots.clear();
    }

    public int getSelectionCount() {
        return selectedItems.size();
    }

    public boolean isSelected(ItemStack item) {
        return selectedItems.stream().anyMatch(s -> ItemStack.isSameItem(s, item));
    }

    public BatchEditResult applyToAll(Consumer<ItemStack> modifier) {
        List<String> successes = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        for (int i = 0; i < selectedItems.size(); i++) {
            try {
                ItemStack item = selectedItems.get(i);
                modifier.accept(item);
                successes.add(item.getHoverName().getString());
            } catch (Exception e) {
                String name = "<unknown>";
                try { name = selectedItems.get(i).getHoverName().getString(); } catch (Exception ex) {}
                failures.add(name + ": " + e.getMessage());
            }
        }

        return new BatchEditResult(successes, failures);
    }

    public BatchEditResult applyPresetToAll(Preset preset, PresetManager presetManager) {
        List<String> successes = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        for (int i = 0; i < selectedItems.size(); i++) {
            ItemStack item = selectedItems.get(i);
            int slot = selectedSlots.get(i);

            try {
                if (preset.scope().test(item)) {
                    boolean ok = presetManager.applyPreset(preset, item, slot);
                    if (ok) successes.add(item.getHoverName().getString());
                    else failures.add(item.getHoverName().getString() + ": apply failed");
                } else {
                    failures.add(item.getHoverName().getString() + ": Scope mismatch");
                }
            } catch (Exception e) {
                failures.add(item.getHoverName().getString() + ": " + e.getMessage());
            }
        }

        return new BatchEditResult(successes, failures);
    }

    public List<ItemStack> getSelectedItems() {
        return Collections.unmodifiableList(selectedItems);
    }

    public List<Integer> getSelectedSlots() {
        return Collections.unmodifiableList(selectedSlots);
    }

    public MultiEditMode getMode() { return mode; }
    public void setMode(MultiEditMode mode) { this.mode = mode; }
}
