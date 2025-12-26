package com.devmod.client.ui.editor.systems;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

public class MultiEditManager {

    private static final long BATCH_TIMEOUT_MS = 5000; // 5 seconds max for batch operations

    private final List<ItemStack> selectedItems = new ArrayList<>();
    private final List<Integer> selectedSlots = new ArrayList<>();
    @Nullable
    private java.util.function.BiFunction<ItemStack, Integer, Boolean> persistenceHandler;

    // Undo support for batch operations
    @Nullable
    private BatchUndoSnapshot lastSnapshot = null;

    // Cancellation support
    private volatile boolean cancelRequested = false;

    public enum MultiEditMode {
        SINGLE, BATCH_PRESET, BATCH_EDIT, SELECTION
    }

    /**
     * Callback for progress updates during batch operations.
     */
    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int current, int total);
    }

    private MultiEditMode mode = MultiEditMode.SINGLE;

    public void addToSelection(ItemStack item, int slot) {
        ItemStack safeItem = Objects.requireNonNull(item, "item cannot be null");
        if (safeItem.isEmpty()) return;
        // Avoid duplicate same-item instances
        if (selectedItems.stream()
            .filter(Objects::nonNull)
            .noneMatch(s -> ItemStack.isSameItem(Objects.requireNonNull(s, "selected item cannot be null"), safeItem))) {
            selectedItems.add(safeItem);
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
                    safeMessage(e),
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
        return applyPresetToAll(preset, presetManager, persist, null);
    }

    /**
     * Apply a preset to all selected items with optional progress callback.
     *
     * @param preset The preset to apply
     * @param presetManager The manager to handle preset application
     * @param persist Whether to persist changes
     * @param progress Optional callback for progress updates (can be null)
     * @return Result containing successes and failures
     */
    public BatchEditResult applyPresetToAll(Preset preset, PresetManager presetManager, boolean persist,
                                            @Nullable ProgressCallback progress) {
        List<String> successes = new ArrayList<>();
        List<BatchEditResult.FailureDetail> failures = new ArrayList<>();

        long startTime = System.currentTimeMillis();
        cancelRequested = false; // Reset cancellation flag at start
        int total = selectedItems.size();

        for (int i = 0; i < selectedItems.size(); i++) {
            // Report progress
            if (progress != null) {
                progress.onProgress(i + 1, total);
            }

            // Check for timeout
            if (System.currentTimeMillis() - startTime > BATCH_TIMEOUT_MS) {
                int remaining = selectedItems.size() - i;
                failures.add(new BatchEditResult.FailureDetail(
                    "<timeout>", null, -1, "Operation timed out after " + BATCH_TIMEOUT_MS + "ms, " + remaining + " items skipped", null));
                break;
            }

            // Check for cancellation
            if (cancelRequested) {
                int remaining = selectedItems.size() - i;
                failures.add(new BatchEditResult.FailureDetail(
                    "<cancelled>", null, -1, "Cancelled by user, " + remaining + " items skipped", null));
                cancelRequested = false;
                break;
            }

            ItemStack item = Objects.requireNonNull(selectedItems.get(i), "selected item cannot be null");
            int slot = selectedSlots.get(i);

            try {
                // Validate slot before any operation
                if (!isSlotValid(slot)) {
                    failures.add(new BatchEditResult.FailureDetail(
                        safeName(item), safeItemId(item), slot, "Invalid slot (item moved?)", null));
                    continue;
                }

                if (preset.scope().test(item)) {
                    boolean ok = presetManager.applyPreset(preset, item, slot);
                    if (ok) {
                        boolean persisted = true;
                        if (persist && persistenceHandler != null) {
                            // Re-validate slot before persist (item might have moved)
                            if (!isSlotValid(slot)) {
                                failures.add(new BatchEditResult.FailureDetail(
                                    safeName(item), safeItemId(item), slot, "Slot invalidated before persist", null));
                                continue;
                            }
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
                    safeName(item), safeItemId(item), slot, safeMessage(e), stackTraceOf(e)));
            }
        }

        return new BatchEditResult(successes, failures);
    }

    /**
     * Request cancellation of the current batch operation.
     * The operation will stop at the next item boundary.
     */
    public void requestCancel() {
        cancelRequested = true;
    }

    /**
     * Check if a cancellation has been requested.
     */
    public boolean isCancelRequested() {
        return cancelRequested;
    }

    /**
     * Check if a slot index is valid in the player's inventory.
     * In test environments (no Minecraft client), assumes slots 0-35 are valid.
     */
    private boolean isSlotValid(int slot) {
        try {
            var mc = Minecraft.getInstance();
            if (mc == null || mc.player == null) {
                // Test environment fallback: assume standard inventory size (36 slots)
                return slot >= 0 && slot < 36;
            }
            var player = Objects.requireNonNull(mc.player, "player");
            var inv = player.getInventory();
            return slot >= 0 && slot < inv.items.size();
        } catch (Exception e) {
            // Minecraft not initialized (unit tests), use fallback
            return slot >= 0 && slot < 36;
        }
    }

    private static String safeName(ItemStack item) {
        try { return Objects.requireNonNull(item, "item cannot be null").getHoverName().getString(); } catch (Exception e) { return "<unknown>"; }
    }

    @Nullable
    private static String safeItemId(ItemStack item) {
        try {
            var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                Objects.requireNonNull(Objects.requireNonNull(item, "item cannot be null").getItem(), "item type cannot be null"));
            return key == null ? null : key.toString();
        } catch (Exception e) { return null; }
    }

    @Nullable
    private static String stackTraceOf(Throwable t) {
        try (java.io.StringWriter sw = new java.io.StringWriter(); java.io.PrintWriter pw = new java.io.PrintWriter(sw)) {
            t.printStackTrace(pw);
            return sw.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String safeMessage(Throwable t) {
        String message = t.getMessage();
        return message != null ? message : t.getClass().getSimpleName();
    }

    public List<ItemStack> getSelectedItems() {
        return Collections.unmodifiableList(selectedItems);
    }

    public List<Integer> getSelectedSlots() {
        return Collections.unmodifiableList(selectedSlots);
    }

    public MultiEditMode getMode() { return mode; }
    public void setMode(MultiEditMode mode) { this.mode = mode; }

    // === Batch Undo Support ===

    /**
     * Create a snapshot of current selected items before applying changes.
     * Call this before applyPresetToAll() to enable undo.
     */
    public void createSnapshot(String presetName) {
        lastSnapshot = new BatchUndoSnapshot(selectedItems, selectedSlots, presetName);
    }

    /**
     * Check if an undo snapshot is available.
     */
    public boolean hasSnapshot() {
        return lastSnapshot != null && !lastSnapshot.isEmpty();
    }

    /**
     * Get the name of the preset in the last snapshot.
     */
    @Nullable
    public String getSnapshotPresetName() {
        return lastSnapshot != null ? lastSnapshot.getPresetName() : null;
    }

    /**
     * Restore items to their state before the last batch operation.
     * Returns a BatchEditResult with success/failure for each item.
     */
    public BatchEditResult restoreSnapshot() {
        if (lastSnapshot == null || lastSnapshot.isEmpty()) {
            return new BatchEditResult(List.of(), List.of(
                new BatchEditResult.FailureDetail("<none>", null, -1, "No snapshot available", null)
            ));
        }

        List<String> successes = new ArrayList<>();
        List<BatchEditResult.FailureDetail> failures = new ArrayList<>();

        var mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return new BatchEditResult(List.of(), List.of(
                new BatchEditResult.FailureDetail("<none>", null, -1, "Player not available", null)
            ));
        }

        var player = Objects.requireNonNull(mc.player, "player");
        var inv = player.getInventory();

        for (int i = 0; i < lastSnapshot.size(); i++) {
            int slot = lastSnapshot.getSlot(i);
            ItemStack original = lastSnapshot.getOriginalStack(i);

            try {
                if (slot < 0 || slot >= inv.items.size()) {
                    failures.add(new BatchEditResult.FailureDetail(
                        safeName(original), safeItemId(original), slot, "Invalid slot", null));
                    continue;
                }

                // Restore the original ItemStack to the inventory slot
                inv.items.set(slot, original.copy());

                // If persistence handler is set, persist the restored item
                if (persistenceHandler != null) {
                    try {
                        Boolean result = persistenceHandler.apply(original, slot);
                        if (!Boolean.TRUE.equals(result)) {
                            failures.add(new BatchEditResult.FailureDetail(
                                safeName(original), safeItemId(original), slot, "Restore persist failed", null));
                            continue;
                        }
                    } catch (Exception e) {
                        failures.add(new BatchEditResult.FailureDetail(
                            safeName(original), safeItemId(original), slot, "Restore persist error: " + safeMessage(e), null));
                        continue;
                    }
                }

                successes.add(original.getHoverName().getString());
            } catch (Exception e) {
                failures.add(new BatchEditResult.FailureDetail(
                    safeName(original), safeItemId(original), slot, safeMessage(e), stackTraceOf(e)));
            }
        }

        // Clear snapshot after restore (can only undo once)
        lastSnapshot = null;

        return new BatchEditResult(successes, failures);
    }

    /**
     * Clear the undo snapshot without restoring.
     */
    public void clearSnapshot() {
        lastSnapshot = null;
    }
}
