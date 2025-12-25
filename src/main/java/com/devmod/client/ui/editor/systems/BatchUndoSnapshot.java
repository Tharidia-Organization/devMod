package com.devmod.ui.editor.systems;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Snapshot of item states before a batch operation.
 * Used for undo functionality in MultiEdit operations.
 */
public class BatchUndoSnapshot {
    private final List<ItemStack> originalStacks;
    private final List<Integer> slots;
    private final String presetName;
    private final long timestamp;

    public BatchUndoSnapshot(List<ItemStack> items, List<Integer> slots, String presetName) {
        // Deep copy the ItemStacks to preserve original state
        this.originalStacks = new ArrayList<>();
        for (ItemStack stack : items) {
            this.originalStacks.add(stack.copy());
        }
        this.slots = new ArrayList<>(slots);
        this.presetName = presetName;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Get the original ItemStacks (copies, safe to modify).
     */
    public List<ItemStack> getOriginalStacks() {
        return Collections.unmodifiableList(originalStacks);
    }

    /**
     * Get the slot indices for each item.
     */
    public List<Integer> getSlots() {
        return Collections.unmodifiableList(slots);
    }

    /**
     * Get the name of the preset that was applied.
     */
    public String getPresetName() {
        return presetName;
    }

    /**
     * Get the timestamp when this snapshot was created.
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Get the number of items in this snapshot.
     */
    public int size() {
        return originalStacks.size();
    }

    /**
     * Check if this snapshot is empty.
     */
    public boolean isEmpty() {
        return originalStacks.isEmpty();
    }

    /**
     * Get a copy of an original ItemStack at the given index.
     */
    public ItemStack getOriginalStack(int index) {
        if (index < 0 || index >= originalStacks.size()) return ItemStack.EMPTY;
        return originalStacks.get(index).copy();
    }

    /**
     * Get the slot for the item at the given index.
     */
    public int getSlot(int index) {
        if (index < 0 || index >= slots.size()) return -1;
        return slots.get(index);
    }
}
