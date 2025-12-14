package com.frenkvs.devmod.ui.editor;

import com.frenkvs.devmod.ui.editor.core.ResponsiveLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Interface for editor content modules.
 * Modules provide content structure, NOT layout/positions.
 *
 * @see EDITOR_DESIGN_SYSTEM.md#32-editor-module-interface
 */
public interface EditorModule {

    // ═══════════════════════════════════════════════════════════════
    // IDENTIFICATION
    // ═══════════════════════════════════════════════════════════════

    /** Unique module ID */
    String getId();

    /** Display title */
    String getTitle();

    /** Module icon (optional) */
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

    // ═══════════════════════════════════════════════════════════════
    // NETWORK
    // ═══════════════════════════════════════════════════════════════

    /** Build payload for server sync */
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
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    /** Called when the module is closed */
    default void onClose() {}

    /** Called each tick */
    default void tick() {}
}
