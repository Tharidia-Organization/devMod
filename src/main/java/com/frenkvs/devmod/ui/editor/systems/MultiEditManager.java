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
    private java.util.function.BiFunction<ItemStack, Integer, Boolean> persistenceHandler;

    public enum MultiEditMode {
        SINGLE, BATCH_PRESET, BATCH_EDIT, SELECTION
    }

    private MultiEditMode mode = MultiEditMode.SINGLE;

    public void addToSelection(ItemStack item, int slot) {
        ItemStack safeItem = Objects.requireNonNull(item, "item cannot be null");
        if (safeItem.isEmpty()) return;
        // Avoid duplicate same-item instances
        if (selectedItems.stream()
            .filter(Objects::nonNull)
            .noneMatch(s -> ItemStack.isSameItem(Objects.requireNonNull(s, "selected item cannot be null"), safeItem))) {
            ItemStack copy = Objects.requireNonNull(safeItem.copy(), "item copy cannot be null");
            selectedItems.add(copy);
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
        if (item == null || item.isEmpty()) return false;
        return selectedItems.stream().anyMatch(s -> s != null && ItemStack.isSameItem(s, item));
    }

    public BatchEditResult applyToAll(Consumer<ItemStack> modifier) {
        List<String> successes = new ArrayList<>();
        List<BatchEditResult.FailureDetail> failures = new ArrayList<>();

        for (int i = 0; i < selectedItems.size(); i++) {
            try {
                ItemStack item = Objects.requireNonNull(selectedItems.get(i), "selected item cannot be null");
                modifier.accept(item);
                successes.add(item.getHoverName().getString());
            } catch (Exception e) {
                failures.add(new BatchEditResult.FailureDetail(
                    safeName(selectedItems.get(i)),
                    safeItemId(selectedItems.get(i)),
                    i < selectedSlots.size() ? selectedSlots.get(i) : -1,
                    e.getMessage(),
                    stackTraceOf(e)
                ));
            }
        }

        return new BatchEditResult(successes, failures);
    }

    /** Optional hook to persist item changes (e.g., send payload or update inventory). */
    public void setPersistenceHandler(java.util.function.BiFunction<ItemStack, Integer, Boolean> handler) {
        this.persistenceHandler = handler;
    }

    // Backward-compatible helper (persist=false by default)
    public BatchEditResult applyPresetToAll(Preset preset, PresetManager presetManager) {
        return applyPresetToAll(preset, presetManager, false);
    }

    public BatchEditResult applyPresetToAll(Preset preset, PresetManager presetManager, boolean persist) {
        List<String> successes = new ArrayList<>();
        List<BatchEditResult.FailureDetail> failures = new ArrayList<>();

        for (int i = 0; i < selectedItems.size(); i++) {
            ItemStack item = Objects.requireNonNull(selectedItems.get(i), "selected item cannot be null");
            int slot = selectedSlots.get(i);

            try {
                if (preset.scope().test(item)) {
                    boolean ok = presetManager.applyPreset(preset, item, slot);
                    if (ok) {
                        boolean persisted = true;
                        if (persist && persistenceHandler != null) {
                            try {
                                persisted = Boolean.TRUE.equals(persistenceHandler.apply(item, slot));
                            } catch (Exception ignored) {
                                persisted = false;
                            }
                        }
                        if (!persisted) {
                            failures.add(new BatchEditResult.FailureDetail(
                                safeName(item), safeItemId(item), slot, "persist failed", null));
                        } else {
                            successes.add(item.getHoverName().getString());
                        }
                    } else {
                        failures.add(new BatchEditResult.FailureDetail(
                            safeName(item), safeItemId(item), slot, "apply failed", null));
                    }
                } else {
                    failures.add(new BatchEditResult.FailureDetail(
                        safeName(item), safeItemId(item), slot, "Scope mismatch", null));
                }
            } catch (Exception e) {
                failures.add(new BatchEditResult.FailureDetail(
                    safeName(item), safeItemId(item), slot, e.getMessage(), stackTraceOf(e)));
            }
        }

        return new BatchEditResult(successes, failures);
    }

    private static String safeName(ItemStack item) {
        try { return Objects.requireNonNull(item, "item cannot be null").getHoverName().getString(); } catch (Exception e) { return "<unknown>"; }
    }

    private static String safeItemId(ItemStack item) {
        try {
            var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                Objects.requireNonNull(Objects.requireNonNull(item, "item cannot be null").getItem(), "item type cannot be null"));
            return key == null ? null : key.toString();
        } catch (Exception e) { return null; }
    }

    private static String stackTraceOf(Throwable t) {
        try (java.io.StringWriter sw = new java.io.StringWriter(); java.io.PrintWriter pw = new java.io.PrintWriter(sw)) {
            t.printStackTrace(pw);
            return sw.toString();
        } catch (Exception ignored) {
            return null;
        }
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
