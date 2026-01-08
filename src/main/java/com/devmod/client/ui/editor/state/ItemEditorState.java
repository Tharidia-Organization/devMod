package com.devmod.client.ui.editor.state;

import javax.annotation.Nullable;

import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.ui.editor.EditorModule;
import com.devmod.client.ui.editor.components.SlotSelector;

/**
 * Centralized state holder for ItemEditorScreen.
 *
 * <p>Manages: item state, module state, mode flags, slot selection.
 * All methods are 100% utilized by ItemEditorScreen and ModeController.
 */
@OnlyIn(Dist.CLIENT)
public final class ItemEditorState {

    // ═══════════════════════════════════════════════════════════════
    // CORE ITEM STATE
    // ═══════════════════════════════════════════════════════════════

    private final ItemStack item;

    // ═══════════════════════════════════════════════════════════════
    // MODULE STATE
    // ═══════════════════════════════════════════════════════════════

    @Nullable
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
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    /**
     * Creates a new editor state with the given item.
     *
     * @param item The item to edit (will be copied)
     */
    public ItemEditorState(ItemStack item) {
        this.item = item.copy();
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
        this.globalMode = false;
    }

    /**
     * Toggle between specific and global mode.
     */
    public void toggleGlobalMode() {
        this.globalMode = false;
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
    }

    // ═══════════════════════════════════════════════════════════════
    // CONVENIENCE METHODS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get mode description for UI display.
     */
    public String getModeDescription() {
        String mode = previewMode ? "Preview" : "Apply";
        return mode + " / Specific";
    }
}
