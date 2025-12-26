package com.devmod.client.ui.editor;

import java.util.List;
import java.util.function.BiConsumer;

import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.devmod.client.ui.editor.core.ResponsiveLayout;

public interface EditorModule {

    // ═══════════════════════════════════════════════════════════════
    // IDENTIFICATION
    // ═══════════════════════════════════════════════════════════════

    /** Unique module ID */
    String getId();

    /** Display title */
    String getTitle();

    /** Module icon (optional) */
    @Nullable
    default ResourceLocation getIcon() {
        return null;
    }

    // ═══════════════════════════════════════════════════════════════
    // TABS
    // ═══════════════════════════════════════════════════════════════

    /** Get available tabs for this module */
    List<ModuleTab> getTabs();

    /** Get currently active tab index */
    int getActiveTabIndex();

    /** Set active tab */
    void setActiveTab(int index);

    // ═══════════════════════════════════════════════════════════════
    // CONTENT
    // ═══════════════════════════════════════════════════════════════

    /** Initialize with item to edit */
    void setItem(ItemStack item);

    /** Get the current item being edited */
    ItemStack getItem();

    /** Initialize module with layout info */
    void init(ResponsiveLayout layout);

    /** Get sections for current tab (called by content area) */
    List<EditorSection> getSections();

    /** Render content into the provided area */
    void renderContent(GuiGraphics graphics, ResponsiveLayout.Rect contentBounds, int mouseX, int mouseY);

    /** Calculate total content height for scroll */
    int calculateContentHeight();

    // ═══════════════════════════════════════════════════════════════
    // INPUT
    // ═══════════════════════════════════════════════════════════════

    boolean mouseClicked(double mouseX, double mouseY, int button);
    boolean mouseReleased(double mouseX, double mouseY, int button);
    boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY);
    boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY);
    boolean keyPressed(int keyCode, int scanCode, int modifiers);
    boolean charTyped(char chr, int modifiers);

    // ═══════════════════════════════════════════════════════════════
    // STATE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════

    /** Check if module has unsaved changes */
    boolean hasUnsavedChanges();

    /** Get list of pending changes (for dirty indicator) */
    List<String> getPendingChanges();

    /** Mark a change as pending */
    void markDirty(String changeDescription);

    /** Clear dirty state (after save) */
    void clearDirty();

    /** Provide a status consumer for modules to show messages */
    default void setStatusConsumer(BiConsumer<String, Integer> statusConsumer) {}

    /** Get history entries */
    List<String> getHistoryEntries();

    /** Clear history entries */
    void clearHistory();

    /** Log a timeline entry (for debug/session log) */
    default void logEvent(String description) {}

    /** Dirty tracking can be enabled/disabled (e.g., preview mode) */
    default void setDirtyTrackingEnabled(boolean enabled) {}

    /** Detect if current state differs from original (even if dirty list is empty) */
    default boolean hasPendingDiff() { return hasUnsavedChanges(); }

    /** Provide preview copy of the item (optional) */
    @Nullable
    default ItemStack getPreviewItem() { return null; }

    /** Clear any preview state (optional) */
    default void clearPreview() {}

    // ═══════════════════════════════════════════════════════════════
    // NETWORK
    // ═══════════════════════════════════════════════════════════════

    /** Build payload for server sync */
    @Nullable
    CustomPacketPayload buildPayload(boolean isGlobal);

    /** Apply changes locally (preview mode) */
    void applyPreview();

    /** Reset to original values */
    void resetToOriginal();

    // ═══════════════════════════════════════════════════════════════
    // UNDO/REDO
    // ═══════════════════════════════════════════════════════════════

    boolean canUndo();
    boolean canRedo();
    void undo();
    void redo();
    void saveUndoState();

    // ═══════════════════════════════════════════════════════════════
    // OVERLAY
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if this module has a modal overlay currently visible.
     * Used by the editor screen to determine rendering/input priority.
     */
    default boolean hasActiveOverlay() { return false; }

    /**
     * Render the module's overlay if visible.
     * Called after all other overlays to ensure it appears on top.
     *
     * @param graphics The graphics context
     * @param font The font to use
     * @param screenWidth Screen width for centering
     * @param screenHeight Screen height for centering
     * @param mouseX Current mouse X
     * @param mouseY Current mouse Y
     */
    default void renderOverlay(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                               int screenWidth, int screenHeight, int mouseX, int mouseY) {}

    /**
     * Handle mouse click for module overlay (when overlay is visible).
     * @return true if the click was consumed
     */
    default boolean overlayMouseClicked(double mouseX, double mouseY, int button,
                                        int screenWidth, int screenHeight) { return false; }

    /**
     * Handle mouse scroll for module overlay (when overlay is visible).
     * @return true if the scroll was consumed
     */
    default boolean overlayMouseScrolled(double mouseX, double mouseY, double scrollDelta,
                                         int screenWidth, int screenHeight) { return false; }

    /**
     * Handle key press for module overlay (when overlay is visible).
     * @return true if the key was consumed
     */
    default boolean overlayKeyPressed(int keyCode) { return false; }

    /**
     * Handle character typed for module overlay (when overlay is visible).
     * @return true if the character was consumed
     */
    default boolean overlayCharTyped(char chr, int modifiers) { return false; }

    // ═══════════════════════════════════════════════════════════════
    // MODULE SWITCHING (Navigation Hub support)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Set callback for switching to another module.
     * Used by GeneralModule (Navigation Hub) to switch editor context.
     *
     * @param callback Consumer that receives the target EditorStartTab
     */
    default void setModuleSwitchCallback(java.util.function.Consumer<EditorStartTab> callback) {}

    /**
     * Get available modules for the current item.
     * Returns list of EditorStartTab values that are applicable.
     */
    default List<EditorStartTab> getAvailableModules() {
        return List.of();
    }

    // ═══════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    /** Called when the module is closed */
    default void onClose() {}

    /** Called each tick */
    default void tick() {}
}
