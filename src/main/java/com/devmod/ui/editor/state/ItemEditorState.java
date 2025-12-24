package com.devmod.ui.editor.state;

import com.devmod.ui.editor.EditorModule;
import com.devmod.ui.editor.components.SlotSelector;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Centralized state object for ItemEditorScreen.
 * Client-only - never load on dedicated server.
 * Contains the core editing state that is shared across subsystems.
 *
 * <p>This class consolidates scattered state fields to enable:
 * <ul>
 *   <li>Decoupled subsystems that don't need Screen reference</li>
 *   <li>Easier testing via state injection</li>
 *   <li>Clear separation of concerns</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class ItemEditorState {

    // ═══════════════════════════════════════════════════════════════
    // CORE ITEM STATE
    // ═══════════════════════════════════════════════════════════════

    private ItemStack item;
    private final ItemStack originalItem;

    // ═══════════════════════════════════════════════════════════════
    // MODULE STATE
    // ═══════════════════════════════════════════════════════════════

    private EditorModule activeModule;

    // ═══════════════════════════════════════════════════════════════
    // MODE FLAGS
    // ═══════════════════════════════════════════════════════════════

    private boolean previewMode = true;
    private boolean globalMode = false;

    // ═══════════════════════════════════════════════════════════════
    // SLOT STATE
    // ═══════════════════════════════════════════════════════════════

    @Nullable
    private SlotSelector.SlotInfo selectedSlot;

    // ═══════════════════════════════════════════════════════════════
    // CHANGE LISTENERS
    // ═══════════════════════════════════════════════════════════════

    private final List<Consumer<ItemEditorState>> changeListeners = new ArrayList<>();

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    /**
     * Creates a new editor state with the given item.
     *
     * @param item The item to edit (will be copied)
     */
    public ItemEditorState(ItemStack item) {
        this.item = item.copy();
        this.originalItem = item.copy();
    }

    // ═══════════════════════════════════════════════════════════════
    // ITEM ACCESSORS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get the current item being edited.
     */
    public ItemStack getItem() {
        return item;
    }

    /**
     * Get the original item (before any edits).
     */
    public ItemStack getOriginalItem() {
        return originalItem;
    }

    /**
     * Update the current item.
     */
    public void setItem(ItemStack newItem) {
        this.item = newItem.copy();
        notifyListeners();
    }

    /**
     * Check if the item has been modified from original.
     */
    public boolean isDirty() {
        return !ItemStack.matches(item, originalItem);
    }

    // ═══════════════════════════════════════════════════════════════
    // MODULE ACCESSORS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get the active editor module.
     */
    @Nullable
    public EditorModule getActiveModule() {
        return activeModule;
    }

    /**
     * Set the active editor module.
     */
    public void setActiveModule(EditorModule module) {
        this.activeModule = module;
        notifyListeners();
    }

    // ═══════════════════════════════════════════════════════════════
    // MODE ACCESSORS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if in preview mode (changes are not persisted).
     */
    public boolean isPreviewMode() {
        return previewMode;
    }

    /**
     * Set preview mode.
     */
    public void setPreviewMode(boolean preview) {
        this.previewMode = preview;
        notifyListeners();
    }

    /**
     * Check if in global mode (changes apply to all items of type).
     */
    public boolean isGlobalMode() {
        return globalMode;
    }

    /**
     * Set global mode.
     */
    public void setGlobalMode(boolean global) {
        this.globalMode = global;
        notifyListeners();
    }

    /**
     * Toggle between preview and apply mode.
     */
    public void togglePreviewMode() {
        setPreviewMode(!previewMode);
    }

    /**
     * Toggle between specific and global mode.
     */
    public void toggleGlobalMode() {
        setGlobalMode(!globalMode);
    }

    // ═══════════════════════════════════════════════════════════════
    // SLOT ACCESSORS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get the currently selected slot.
     */
    @Nullable
    public SlotSelector.SlotInfo getSelectedSlot() {
        return selectedSlot;
    }

    /**
     * Set the selected slot.
     */
    public void setSelectedSlot(@Nullable SlotSelector.SlotInfo slot) {
        this.selectedSlot = slot;
        notifyListeners();
    }

    // ═══════════════════════════════════════════════════════════════
    // CHANGE NOTIFICATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Add a listener that is notified when state changes.
     */
    public void addChangeListener(Consumer<ItemEditorState> listener) {
        changeListeners.add(listener);
    }

    /**
     * Remove a change listener.
     */
    public void removeChangeListener(Consumer<ItemEditorState> listener) {
        changeListeners.remove(listener);
    }

    /**
     * Remove all change listeners.
     * Call this on screen close to prevent memory leaks.
     */
    public void removeAllListeners() {
        changeListeners.clear();
    }

    private void notifyListeners() {
        for (Consumer<ItemEditorState> listener : changeListeners) {
            listener.accept(this);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // CONVENIENCE METHODS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get mode description for UI display.
     */
    public String getModeDescription() {
        String mode = previewMode ? "Preview" : "Apply";
        String scope = globalMode ? "Global" : "Specific";
        return mode + " / " + scope;
    }

    /**
     * Reset item to original state.
     */
    public void resetToOriginal() {
        this.item = originalItem.copy();
        notifyListeners();
    }
}
