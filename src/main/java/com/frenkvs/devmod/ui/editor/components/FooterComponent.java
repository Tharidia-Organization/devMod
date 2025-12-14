package com.frenkvs.devmod.ui.editor.components;

import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.editor.core.EditorSounds;
import com.frenkvs.devmod.ui.editor.core.ResponsiveLayout;
import com.frenkvs.devmod.ui.editor.core.UIConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Footer component with undo/redo, actions menu, and apply button.
 *
 * @see EDITOR_DESIGN_SYSTEM.md Section 2.7 (Footer Zone)
 */
public class FooterComponent {

    // ═══════════════════════════════════════════════════════════════
    // DIMENSIONS (from Section 2.7)
    // ═══════════════════════════════════════════════════════════════

    private static final int HEIGHT = UIConstants.Size.FOOTER_HEIGHT;  // 52px
    private static final int UNDO_REDO_SIZE = 32;
    private static final int UNDO_REDO_HEIGHT = 28;
    private static final int ACTIONS_WIDTH = 100;
    private static final int ACTIONS_HEIGHT = 28;
    private static final int APPLY_WIDTH = 112;
    private static final int APPLY_HEIGHT = 36;

    // ═══════════════════════════════════════════════════════════════
    // ACTION MENU ITEMS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Action menu item definition.
     */
    public record ActionItem(String id, String icon, String label, boolean isSeparator) {
        public ActionItem(String id, String icon, String label) {
            this(id, icon, label, false);
        }

        public static ActionItem createSeparator() {
            return new ActionItem(null, null, null, true);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════

    private boolean canUndo = false;
    private boolean canRedo = false;
    private boolean canApply = false;
    private boolean isDirty = false;
    private int pendingCount = 0;

    // Hover states
    private boolean undoHovered = false;
    private boolean redoHovered = false;
    private boolean actionsHovered = false;
    private boolean applyHovered = false;

    // Actions dropdown
    private boolean actionsOpen = false;
    private final List<ActionItem> actionItems = new ArrayList<>();
    private int hoveredActionIndex = -1;

    // Bounds
    private ResponsiveLayout.Rect bounds = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect undoBounds = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect redoBounds = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect actionsBounds = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect applyBounds = ResponsiveLayout.Rect.EMPTY;

    // Callbacks
    private Runnable onUndo;
    private Runnable onRedo;
    private Runnable onApply;
    private java.util.function.Consumer<String> onAction;

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public FooterComponent() {
        // Default action items
        actionItems.add(new ActionItem("history", "📋", "History"));
        actionItems.add(ActionItem.createSeparator());
        actionItems.add(new ActionItem("export", "📤", "Export"));
        actionItems.add(new ActionItem("import", "📥", "Import"));
        actionItems.add(ActionItem.createSeparator());
        actionItems.add(new ActionItem("presets", "💾", "Presets"));
        actionItems.add(new ActionItem("reset", "↺", "Reset"));
        actionItems.add(ActionItem.createSeparator());
        actionItems.add(new ActionItem("cancel", "✗", "Cancel"));
    }

    // ═══════════════════════════════════════════════════════════════
    // CONFIGURATION
    // ═══════════════════════════════════════════════════════════════

    public FooterComponent canUndo(boolean canUndo) {
        this.canUndo = canUndo;
        return this;
    }

    public FooterComponent canRedo(boolean canRedo) {
        this.canRedo = canRedo;
        return this;
    }

    public FooterComponent canApply(boolean canApply) {
        this.canApply = canApply;
        return this;
    }

    public FooterComponent isDirty(boolean isDirty) {
        this.isDirty = isDirty;
        return this;
    }

    public FooterComponent pendingCount(int count) {
        this.pendingCount = Math.max(0, count);
        return this;
    }

    public FooterComponent onUndo(Runnable callback) {
        this.onUndo = callback;
        return this;
    }

    public FooterComponent onRedo(Runnable callback) {
        this.onRedo = callback;
        return this;
    }

    public FooterComponent onApply(Runnable callback) {
        this.onApply = callback;
        return this;
    }

    public FooterComponent onAction(java.util.function.Consumer<String> callback) {
        this.onAction = callback;
        return this;
    }

    public FooterComponent clearActions() {
        actionItems.clear();
        return this;
    }

    public FooterComponent addAction(String id, String icon, String label) {
        actionItems.add(new ActionItem(id, icon, label));
        return this;
    }

    public FooterComponent addSeparator() {
        actionItems.add(ActionItem.createSeparator());
        return this;
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDERING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Render the footer at the given position.
     * @return The height consumed
     */
    public int render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
        var font = Objects.requireNonNull(Minecraft.getInstance().font, "font cannot be null");

        this.bounds = new ResponsiveLayout.Rect(x, y, width, HEIGHT);

        // Background
        graphics.fill(x, y, x + width, y + HEIGHT, UIConstants.Background.HEADER);

        // Top border
        graphics.fill(x, y, x + width, y + 1, UIConstants.Border.SEPARATOR);

        // Calculate positions
        int contentY = y + 12;

        // Undo button (x=8)
        int undoX = x + 8;
        undoBounds = new ResponsiveLayout.Rect(undoX, contentY, UNDO_REDO_SIZE, UNDO_REDO_HEIGHT);
        undoHovered = undoBounds.contains(mouseX, mouseY);
        renderUndoRedoButton(graphics, font, undoX, contentY, "↶", canUndo, undoHovered);

        // Redo button (x=44)
        int redoX = x + 44;
        redoBounds = new ResponsiveLayout.Rect(redoX, contentY, UNDO_REDO_SIZE, UNDO_REDO_HEIGHT);
        redoHovered = redoBounds.contains(mouseX, mouseY);
        renderUndoRedoButton(graphics, font, redoX, contentY, "↷", canRedo, redoHovered);

        // Separator (x=84)
        int separatorX = x + 84;
        graphics.fill(separatorX, y + 8, separatorX + 1, y + 8 + 36, UIConstants.Border.SEPARATOR);

        // Actions menu (x=92)
        int actionsX = x + 92;
        actionsBounds = new ResponsiveLayout.Rect(actionsX, contentY, ACTIONS_WIDTH, ACTIONS_HEIGHT);
        actionsHovered = actionsBounds.contains(mouseX, mouseY);
        renderActionsButton(graphics, font, actionsX, contentY, mouseX, mouseY);

        // Apply button (right - 120)
        int applyX = x + width - 120;
        int applyY = y + 8;
        applyBounds = new ResponsiveLayout.Rect(applyX, applyY, APPLY_WIDTH, APPLY_HEIGHT);
        applyHovered = applyBounds.contains(mouseX, mouseY);
        renderApplyButton(graphics, font, applyX, applyY);

        // Render dropdown if open
        if (actionsOpen) {
            renderActionsDropdown(graphics, font, actionsX, contentY + ACTIONS_HEIGHT, mouseX, mouseY);
        }

        return HEIGHT;
    }

    private void renderUndoRedoButton(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                       int x, int y, String symbol, boolean enabled, boolean hovered) {
        // Background
        int bgColor = !enabled ? UIConstants.Button.DISABLED :
                     (hovered ? UIConstants.Button.HOVER : UIConstants.Button.NORMAL);
        graphics.fill(x, y, x + UNDO_REDO_SIZE, y + UNDO_REDO_HEIGHT, bgColor);

        // Border
        int borderColor = enabled && hovered ? UIConstants.Border.ACCENT : UIConstants.Border.DEFAULT;
        AxiomRenderer.drawBorder(graphics, x, y, UNDO_REDO_SIZE, UNDO_REDO_HEIGHT, borderColor);

        // Symbol
        int textColor = enabled ? UIConstants.Text.PRIMARY : UIConstants.Text.DISABLED;
        int textX = x + (UNDO_REDO_SIZE - Objects.requireNonNull(font, "font cannot be null").width(Objects.requireNonNull(symbol, "symbol cannot be null"))) / 2;
        int textY = y + (UNDO_REDO_HEIGHT - 8) / 2;
        graphics.drawString(font, symbol, textX, textY, textColor, false);
    }

    private void renderActionsButton(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                      int x, int y, int mouseX, int mouseY) {
        // Background
        int bgColor = actionsOpen ? UIConstants.Button.PRESSED :
                     (actionsHovered ? UIConstants.Button.HOVER : UIConstants.Button.NORMAL);
        graphics.fill(x, y, x + ACTIONS_WIDTH, y + ACTIONS_HEIGHT, bgColor);

        // Border
        int borderColor = actionsHovered || actionsOpen ? UIConstants.Border.ACCENT : UIConstants.Border.DEFAULT;
        AxiomRenderer.drawBorder(graphics, x, y, ACTIONS_WIDTH, ACTIONS_HEIGHT, borderColor);

        // Text
        String label = "≡ Actions";
        String indicator = actionsOpen ? "▲" : "▼";
        int textX = x + 8;
        int textY = y + (ACTIONS_HEIGHT - 8) / 2;
        graphics.drawString(Objects.requireNonNull(font, "font cannot be null"),
            Objects.requireNonNull(label, "label cannot be null"), textX, textY, UIConstants.Text.PRIMARY, false);

        int indicatorX = x + ACTIONS_WIDTH - 12;
        graphics.drawString(font, Objects.requireNonNull(indicator, "indicator cannot be null"),
            indicatorX, textY, UIConstants.Text.MUTED, false);
    }

    private void renderActionsDropdown(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                        int x, int y, int mouseX, int mouseY) {
        int itemHeight = 24;
        int separatorHeight = 8;
        int dropdownHeight = 0;

        // Calculate total height
        for (ActionItem item : actionItems) {
            dropdownHeight += item.isSeparator() ? separatorHeight : itemHeight;
        }

        // Dropdown background
        graphics.fill(x, y, x + ACTIONS_WIDTH, y + dropdownHeight, UIConstants.Background.PANEL_SOLID);
        AxiomRenderer.drawBorder(graphics, x, y, ACTIONS_WIDTH, dropdownHeight, UIConstants.Border.DEFAULT);

        // Render items
        hoveredActionIndex = -1;
        int itemY = y;
        for (int i = 0; i < actionItems.size(); i++) {
            ActionItem item = actionItems.get(i);

            if (item.isSeparator()) {
                // Separator line
                graphics.fill(x + 8, itemY + 3, x + ACTIONS_WIDTH - 8, itemY + 4,
                             UIConstants.Border.SEPARATOR);
                itemY += separatorHeight;
            } else {
                ResponsiveLayout.Rect itemBounds = new ResponsiveLayout.Rect(x, itemY, ACTIONS_WIDTH, itemHeight);
                boolean itemHovered = itemBounds.contains(mouseX, mouseY);

                if (itemHovered) {
                    hoveredActionIndex = i;
                    graphics.fill(x + 1, itemY, x + ACTIONS_WIDTH - 1, itemY + itemHeight,
                                 UIConstants.Background.HOVER);
                }

                // Icon and label
                String icon = item.icon() != null ? item.icon() : "";
                String label = item.label() != null ? item.label() : "";
                int textX = x + 8;
                int textY = itemY + (itemHeight - 8) / 2;
        graphics.drawString(Objects.requireNonNull(font, "font cannot be null"),
            (icon == null ? "" : icon) + " " + (label == null ? "" : label),
            textX, textY,
            itemHovered ? UIConstants.Text.PRIMARY : UIConstants.Text.SECONDARY, false);

                itemY += itemHeight;
            }
        }
    }

    private void renderApplyButton(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                    int x, int y) {
        boolean enabled = canApply && isDirty;

        // Background - green tint for primary action
        int bgColor = !enabled ? UIConstants.Button.DISABLED :
                     (applyHovered ? UIConstants.Button.PRIMARY_HOVER : UIConstants.Button.PRIMARY);
        graphics.fill(x, y, x + APPLY_WIDTH, y + APPLY_HEIGHT, bgColor);

        // Border
        int borderColor = enabled ? UIConstants.Accent.GREEN : UIConstants.Border.DEFAULT;
        if (applyHovered && enabled) {
            borderColor = UIConstants.lighten(borderColor, 0.3f);
        }
        AxiomRenderer.drawBorder(graphics, x, y, APPLY_WIDTH, APPLY_HEIGHT, borderColor);

        // Text
        String label;
        if (!canApply) {
            label = "Preview Only";
        } else if (pendingCount > 0) {
            label = "✓ Apply (" + pendingCount + ")";
        } else {
            label = "✓ Apply";
        }
        int textColor = enabled ? UIConstants.Text.PRIMARY : UIConstants.Text.DISABLED;
        int textWidth = font.width(label);
        int textX = x + (APPLY_WIDTH - textWidth) / 2;
        int textY = y + (APPLY_HEIGHT - 8) / 2;
        graphics.drawString(font, label, textX, textY, textColor, false);

        // Dirty indicator dot
        if (isDirty) {
            int dotX = x + APPLY_WIDTH - 12;
            int dotY = y + 6;
            graphics.fill(dotX, dotY, dotX + 6, dotY + 6, UIConstants.Accent.ORANGE);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        // Check dropdown items first (if open)
        if (actionsOpen && hoveredActionIndex >= 0) {
            ActionItem item = actionItems.get(hoveredActionIndex);
            if (!item.isSeparator() && item.id() != null) {
                EditorSounds.playButtonClick();
                actionsOpen = false;
                if (onAction != null) {
                    onAction.accept(item.id());
                }
                return true;
            }
        }

        // Check actions button (toggle dropdown)
        if (actionsBounds.contains(mouseX, mouseY)) {
            actionsOpen = !actionsOpen;
            EditorSounds.playButtonClick();
            return true;
        }

        // Close dropdown if clicking outside
        if (actionsOpen) {
            actionsOpen = false;
            return true;
        }

        // Check undo button
        if (undoBounds.contains(mouseX, mouseY) && canUndo) {
            EditorSounds.playUndo();
            if (onUndo != null) {
                onUndo.run();
            }
            return true;
        }

        // Check redo button
        if (redoBounds.contains(mouseX, mouseY) && canRedo) {
            EditorSounds.playRedo();
            if (onRedo != null) {
                onRedo.run();
            }
            return true;
        }

        // Check apply button
        if (applyBounds.contains(mouseX, mouseY) && canApply && isDirty) {
            EditorSounds.playSuccess();
            if (onApply != null) {
                onApply.run();
            }
            return true;
        }

        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Ctrl+Z = Undo
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_Z &&
            (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0) {
            if ((modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0) {
                // Ctrl+Shift+Z = Redo
                if (canRedo && onRedo != null) {
                    EditorSounds.playRedo();
                    onRedo.run();
                    return true;
                }
            } else {
                // Ctrl+Z = Undo
                if (canUndo && onUndo != null) {
                    EditorSounds.playUndo();
                    onUndo.run();
                    return true;
                }
            }
        }

        // Ctrl+Y = Redo
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_Y &&
            (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0) {
            if (canRedo && onRedo != null) {
                EditorSounds.playRedo();
                onRedo.run();
                return true;
            }
        }

        // Ctrl+S = Apply
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_S &&
            (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0) {
            if (canApply && isDirty && onApply != null) {
                EditorSounds.playSuccess();
                onApply.run();
                return true;
            }
        }

        return false;
    }

    /**
     * Close the actions dropdown.
     */
    public void closeDropdown() {
        actionsOpen = false;
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════

    public boolean isActionsOpen() {
        return actionsOpen;
    }

    public boolean canUndo() {
        return canUndo;
    }

    public boolean canRedo() {
        return canRedo;
    }

    public boolean canApply() {
        return canApply;
    }

    public boolean isDirty() {
        return isDirty;
    }

    public int getHeight() {
        return HEIGHT;
    }

    public ResponsiveLayout.Rect getBounds() {
        return bounds;
    }
}
