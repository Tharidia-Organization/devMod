package com.devmod.client.ui.editor.components;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.editor.core.EditorSounds;
import com.devmod.client.ui.editor.core.ResponsiveLayout;
import com.devmod.client.ui.editor.core.ScaledCoord;
import com.devmod.client.ui.editor.core.Typography;
import com.devmod.client.ui.editor.core.UIConstants;

public class FooterComponent {

    // ═══════════════════════════════════════════════════════════════
    // DIMENSIONS (from Section 2.7)
    // ═══════════════════════════════════════════════════════════════

    private static final int HEIGHT = UIConstants.Size.FOOTER_HEIGHT;  // 60px
    private static final int ACTIONS_HEIGHT = 28;
    private static final int APPLY_WIDTH = 112;
    private static final int APPLY_HEIGHT = 36;
    private static final int ACTION_ARROW_WIDTH = 14;
    private static final int ACTION_GAP_BASE = 6;
    private static final int ACTION_MIN_WIDTH = 36;
    private static final int ACTION_GAP_MIN = 1;
    private static final int DIRTY_DOT_SIZE = 6;
    private static final int DIRTY_DOT_Y_OFFSET = 6;
    private static final int BORDER_THICKNESS = 1;
    private static final int SCROLL_TOLERANCE = 1;
    private static final float ACTION_SCALE_MIN = 0.55f;
    private static final float APPLY_BORDER_LIGHTEN = 0.3f;

    // ═══════════════════════════════════════════════════════════════
    // ACTION MENU ITEMS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Action menu item definition.
     */
    public record ActionItem(@Nullable String id, @Nullable String icon, @Nullable String label, boolean isSeparator) {
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

    // Hover states (locally computed during render)
    private boolean applyHovered = false;
    private boolean leftArrowHovered = false;
    private boolean rightArrowHovered = false;
    private boolean actionsOverflow = false;

    // Action buttons (replacing dropdown)
    private final List<ActionItem> actionItems = new ArrayList<>();
    private ResponsiveLayout.Rect historyBounds = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect exportBounds = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect importBounds = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect presetsBounds = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect templatesBounds = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect recipeBounds = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect resetBounds = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect cancelBounds = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect actionsViewport = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect leftArrowBounds = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect rightArrowBounds = ResponsiveLayout.Rect.EMPTY;

    // Bounds
    private ResponsiveLayout.Rect bounds = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect undoBounds = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect redoBounds = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect applyBounds = ResponsiveLayout.Rect.EMPTY;
    private double actionScrollOffset = 0;
    private double actionMaxScroll = 0;

    // Callbacks
    @Nullable
    private Runnable onUndo;
    @Nullable
    private Runnable onRedo;
    @Nullable
    private Runnable onApply;
    @Nullable
    private java.util.function.Consumer<String> onAction;

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public FooterComponent() {
        // Default action items
        actionItems.add(new ActionItem("history", "📋", "History"));
        actionItems.add(new ActionItem("export", "📤", "Export"));
        actionItems.add(new ActionItem("import", "📥", "Import"));
        actionItems.add(new ActionItem("presets", "💾", "Presets"));
        actionItems.add(new ActionItem("templates", "📂", "Templates"));
        actionItems.add(new ActionItem("recipe", "📜", "Recipe"));
        actionItems.add(new ActionItem("reset", "↺", "Reset"));
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

        int footerHeight = ScaledCoord.scaleDim(HEIGHT);
        int actionsHeight = ScaledCoord.scaleDim(ACTIONS_HEIGHT);
        int applyWidth = ScaledCoord.scaleDim(APPLY_WIDTH);
        int applyHeight = ScaledCoord.scaleDim(APPLY_HEIGHT);
        int arrowWidth = ScaledCoord.scaleDim(ACTION_ARROW_WIDTH);
        int arrowPad = arrowWidth + ScaledCoord.scaleDim(UIConstants.Spacing.SM);

        this.bounds = new ResponsiveLayout.Rect(x, y, width, footerHeight);

        // Background
        graphics.fill(x, y, x + width, y + footerHeight, UIConstants.Background.HEADER());

        // Top border
        graphics.fill(x, y, x + width, y + BORDER_THICKNESS, UIConstants.Border.SEPARATOR());

        // Calculate positions
        int contentY = y + ScaledCoord.scaleDim(UIConstants.Spacing.LG);

        // Clear undo/redo bounds (no longer rendered in footer)
        undoBounds = ResponsiveLayout.Rect.EMPTY;
        redoBounds = ResponsiveLayout.Rect.EMPTY;

        // Action buttons row (History, Export, Import, Presets, Reset, Cancel)
        int padding = ScaledCoord.scaleDim(UIConstants.Spacing.LG);
        int applyMargin = ScaledCoord.scaleDim(UIConstants.Spacing.LG);
        int startX = x + padding; // initial left padding
        int gapBase = ScaledCoord.scaleDim(ACTION_GAP_BASE);
        int btnY = contentY;
        int minWidth = ScaledCoord.scaleDim(ACTION_MIN_WIDTH);

        String[] labels = new String[] { "History", "Export", "Import", "Presets", "Templates", "Recipe", "Reset", "Cancel" };
        int[] widths = new int[labels.length];
        float btnFontScale = Typography.buttonScale();
        int innerPad = ScaledCoord.scaleDim(UIConstants.Spacing.LG);
        for (int i = 0; i < labels.length; i++) {
            String lbl = Objects.requireNonNull(labels[i], "label cannot be null");
            int textW = Math.round(font.width(lbl) * btnFontScale);
            widths[i] = Math.max(minWidth, textW + innerPad * 2);
        }

        // Fit buttons in available space (avoid overlap with Apply) and enable horizontal scrolling when needed
        int gap = gapBase;
        int baseRowWidth = gap * (widths.length - 1);
        for (int w : widths) baseRowWidth += w;

        int applyX = x + width - applyWidth - applyMargin;
        int available = Math.max(0, applyX - startX - applyMargin);
        if (available < baseRowWidth) {
            float scale = Math.max(ACTION_SCALE_MIN, (float) available / (float) baseRowWidth);
            gap = Math.max(ScaledCoord.scaleDim(UIConstants.Spacing.XS), Math.round(gapBase * scale));
            for (int i = 0; i < widths.length; i++) {
                widths[i] = Math.max(minWidth, Math.round(widths[i] * scale));
            }
            int scaledRowWidth = gap * (widths.length - 1);
            for (int w : widths) scaledRowWidth += w;
            if (scaledRowWidth > available) {
                int overflow = scaledRowWidth - available;
                int per = (int) Math.ceil((double) overflow / widths.length);
                for (int i = 0; i < widths.length; i++) {
                    widths[i] = Math.max(minWidth, widths[i] - per);
                }
                scaledRowWidth = gap * (widths.length - 1);
                for (int w : widths) scaledRowWidth += w;
                if (scaledRowWidth > available) {
                    int gapOverflow = scaledRowWidth - available;
                    int gapReduce = (int) Math.ceil((double) gapOverflow / Math.max(1, widths.length - 1));
                    gap = Math.max(ScaledCoord.scaleDim(ACTION_GAP_MIN), gap - gapReduce);
                }
            }
        }

        actionsOverflow = false;
        leftArrowBounds = ResponsiveLayout.Rect.EMPTY;
        rightArrowBounds = ResponsiveLayout.Rect.EMPTY;
        actionsViewport = ResponsiveLayout.Rect.EMPTY;

        int baseActionsWidth = gap * (widths.length - 1);
        for (int w : widths) baseActionsWidth += w;

        // Center row when it fits
        if (baseActionsWidth <= available) {
            startX = startX + Math.max(0, (available - baseActionsWidth) / 2);
        }

        int actionsAvailable = Math.max(0, applyX - startX - applyMargin);
        if (baseActionsWidth > actionsAvailable) {
            actionsOverflow = true;
            actionsAvailable = Math.max(0, actionsAvailable - arrowPad * 2);
            actionMaxScroll = Math.max(0, baseActionsWidth - actionsAvailable);
            actionScrollOffset = Math.max(0, Math.min(actionScrollOffset, actionMaxScroll));

            leftArrowBounds = new ResponsiveLayout.Rect(startX, btnY, arrowWidth, actionsHeight);
            actionsViewport = new ResponsiveLayout.Rect(leftArrowBounds.right() + ScaledCoord.scaleDim(UIConstants.Spacing.XS), btnY,
                Math.max(0, actionsAvailable), actionsHeight);
            rightArrowBounds = new ResponsiveLayout.Rect(actionsViewport.right() + ScaledCoord.scaleDim(UIConstants.Spacing.XS), btnY, arrowWidth, actionsHeight);
            leftArrowHovered = leftArrowBounds.contains(mouseX, mouseY);
            rightArrowHovered = rightArrowBounds.contains(mouseX, mouseY);
        } else {
            actionScrollOffset = 0;
            actionMaxScroll = 0;
            actionsViewport = new ResponsiveLayout.Rect(startX, btnY, Math.max(0, actionsAvailable), actionsHeight);
        }

        int renderStartX = actionsViewport.x() - (int) Math.round(actionScrollOffset);
        if (actionsOverflow && !actionsViewport.isEmpty()) {
            graphics.enableScissor(actionsViewport.x(), actionsViewport.y(),
                actionsViewport.x() + actionsViewport.width(), actionsViewport.y() + actionsViewport.height());
        }

        int cursorX = renderStartX;
        boolean historyHovered = new ResponsiveLayout.Rect(cursorX, btnY, widths[0], actionsHeight).contains(mouseX, mouseY);
        historyBounds = renderActionButton(graphics, font, cursorX, btnY, widths[0], actionsHeight, "History", historyHovered);

        cursorX = historyBounds.right() + gap;
        boolean exportHovered = new ResponsiveLayout.Rect(cursorX, btnY, widths[1], actionsHeight).contains(mouseX, mouseY);
        exportBounds = renderActionButton(graphics, font, cursorX, btnY, widths[1], actionsHeight, "Export", exportHovered);

        cursorX = exportBounds.right() + gap;
        boolean importHovered = new ResponsiveLayout.Rect(cursorX, btnY, widths[2], actionsHeight).contains(mouseX, mouseY);
        importBounds = renderActionButton(graphics, font, cursorX, btnY, widths[2], actionsHeight, "Import", importHovered);

        cursorX = importBounds.right() + gap;
        boolean presetsHovered = new ResponsiveLayout.Rect(cursorX, btnY, widths[3], actionsHeight).contains(mouseX, mouseY);
        presetsBounds = renderActionButton(graphics, font, cursorX, btnY, widths[3], actionsHeight, "Presets", presetsHovered);

        cursorX = presetsBounds.right() + gap;
        boolean templatesHovered = new ResponsiveLayout.Rect(cursorX, btnY, widths[4], actionsHeight).contains(mouseX, mouseY);
        templatesBounds = renderActionButton(graphics, font, cursorX, btnY, widths[4], actionsHeight, "Templates", templatesHovered);

        cursorX = templatesBounds.right() + gap;
        boolean recipeHovered = new ResponsiveLayout.Rect(cursorX, btnY, widths[5], actionsHeight).contains(mouseX, mouseY);
        recipeBounds = renderActionButton(graphics, font, cursorX, btnY, widths[5], actionsHeight, "Recipe", recipeHovered);

        cursorX = recipeBounds.right() + gap;
        boolean resetHovered = new ResponsiveLayout.Rect(cursorX, btnY, widths[6], actionsHeight).contains(mouseX, mouseY);
        resetBounds = renderActionButton(graphics, font, cursorX, btnY, widths[6], actionsHeight, "Reset", resetHovered);

        cursorX = resetBounds.right() + gap;
        boolean cancelHovered = new ResponsiveLayout.Rect(cursorX, btnY, widths[7], actionsHeight).contains(mouseX, mouseY);
        cancelBounds = renderActionButton(graphics, font, cursorX, btnY, widths[7], actionsHeight, "Cancel", cancelHovered);

        if (actionsOverflow && !actionsViewport.isEmpty()) {
            graphics.disableScissor();
        }

        if (actionsOverflow) {
            renderArrow(graphics, font, leftArrowBounds, "◀", actionScrollOffset > 0, leftArrowHovered);
            renderArrow(graphics, font, rightArrowBounds, "▶", actionScrollOffset < actionMaxScroll - SCROLL_TOLERANCE,
                rightArrowHovered);
        }

        // Apply button (right aligned)
        int applyY = y + ScaledCoord.scaleDim(UIConstants.Spacing.MD);
        applyBounds = new ResponsiveLayout.Rect(applyX, applyY, applyWidth, applyHeight);
        applyHovered = applyBounds.contains(mouseX, mouseY);
        renderApplyButton(graphics, font, applyX, applyY, applyWidth, applyHeight);

        return footerHeight;
    }

    private ResponsiveLayout.Rect renderActionButton(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                                     int x, int y, int width, int height, String label, boolean hovered) {
        float fontScale = Typography.buttonScale();
        ResponsiveLayout.Rect rect = new ResponsiveLayout.Rect(x, y, width, height);
        int bgColor = hovered ? UIConstants.Button.HOVER() : UIConstants.Button.NORMAL();
        graphics.fill(x, y, x + width, y + height, bgColor);
        int borderColor = hovered ? UIConstants.Border.ACCENT() : UIConstants.Border.DEFAULT();
        AxiomRenderer.drawBorder(graphics, x, y, width, height, borderColor);
        String safeLabel = Objects.requireNonNull(label, "label cannot be null");
        int textWidthScaled = Math.round(font.width(safeLabel) * fontScale);
        int textHeightScaled = Math.round(font.lineHeight * fontScale);
        int textX = x + (width - textWidthScaled) / 2;
        int textY = y + (height - textHeightScaled) / 2;
        Typography.drawText(graphics, font, safeLabel, textX, textY, UIConstants.Text.PRIMARY(), fontScale);
        return rect;
    }

    private void renderArrow(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                             ResponsiveLayout.Rect rect, String symbol, boolean enabled, boolean hovered) {
        int bgColor = !enabled ? UIConstants.Button.DISABLED() :
            (hovered ? UIConstants.Button.HOVER() : UIConstants.Button.NORMAL());
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), bgColor);
        int borderColor = enabled && hovered ? UIConstants.Border.ACCENT() : UIConstants.Border.DEFAULT();
        AxiomRenderer.drawBorder(graphics, rect.x(), rect.y(), rect.width(), rect.height(), borderColor);
        int textColor = enabled ? UIConstants.Text.PRIMARY() : UIConstants.Text.DISABLED();
        float fontScale = Typography.buttonScale();
        String safeSymbol = Objects.requireNonNull(symbol, "symbol cannot be null");
        int textX = rect.x() + (rect.width() - Math.round(font.width(safeSymbol) * fontScale)) / 2;
        int textY = rect.y() + (rect.height() - Math.round(font.lineHeight * fontScale)) / 2;
        Typography.drawText(graphics, font, safeSymbol, textX, textY, textColor, fontScale);
    }

    private void renderApplyButton(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                    int x, int y, int width, int height) {
        float fontScale = Typography.buttonScale();
        boolean enabled = canApply && isDirty;

        // Background - green tint for primary action
        int bgColor = !enabled ? UIConstants.Button.DISABLED() :
                     (applyHovered ? UIConstants.Button.PRIMARY_HOVER : UIConstants.Button.PRIMARY);
        graphics.fill(x, y, x + width, y + height, bgColor);

        // Border
        int borderColor = enabled ? UIConstants.Accent.GREEN() : UIConstants.Border.DEFAULT();
        if (applyHovered && enabled) {
            borderColor = UIConstants.lighten(borderColor, APPLY_BORDER_LIGHTEN);
        }
        AxiomRenderer.drawBorder(graphics, x, y, width, height, borderColor);

        // Text
        String label;
        if (!canApply) {
            label = "Preview Only";
        } else if (!isDirty) {
            label = "No Changes";
        } else if (pendingCount > 0) {
            label = "✓ Apply (" + pendingCount + ")";
        } else {
            label = "✓ Apply";
        }
        int textColor = enabled ? UIConstants.Text.PRIMARY() : UIConstants.Text.DISABLED();
        int textWidthScaled = Math.round(font.width(label) * fontScale);
        int textHeightScaled = Math.round(font.lineHeight * fontScale);
        int textX = x + (width - textWidthScaled) / 2;
        int textY = y + (height - textHeightScaled) / 2;
        Typography.drawText(graphics, font, label, textX, textY, textColor, fontScale);

        // Dirty indicator dot
        if (isDirty) {
            int dotSize = ScaledCoord.scaleDim(DIRTY_DOT_SIZE);
            int dotX = x + width - ScaledCoord.scaleDim(UIConstants.Spacing.LG);
            int dotY = y + ScaledCoord.scaleDim(DIRTY_DOT_Y_OFFSET);
            graphics.fill(dotX, dotY, dotX + dotSize, dotY + dotSize, UIConstants.Accent.ORANGE());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        // Action buttons (respect viewport when overflowing)
        boolean withinActions = !actionsOverflow || actionsViewport.contains(mouseX, mouseY);

        if (historyBounds.contains(mouseX, mouseY) && withinActions) {
            triggerAction("history");
            return true;
        }
        if (exportBounds.contains(mouseX, mouseY) && withinActions) {
            triggerAction("export");
            return true;
        }
        if (importBounds.contains(mouseX, mouseY) && withinActions) {
            triggerAction("import");
            return true;
        }
        if (presetsBounds.contains(mouseX, mouseY) && withinActions) {
            triggerAction("presets");
            return true;
        }
        if (templatesBounds.contains(mouseX, mouseY) && withinActions) {
            triggerAction("templates");
            return true;
        }
        if (recipeBounds.contains(mouseX, mouseY) && withinActions) {
            triggerAction("recipe");
            return true;
        }
        if (resetBounds.contains(mouseX, mouseY) && withinActions) {
            triggerAction("reset");
            return true;
        }
        if (cancelBounds.contains(mouseX, mouseY) && withinActions) {
            triggerAction("cancel");
            return true;
        }

        if (actionsOverflow) {
            double step = ScaledCoord.scaleDim(APPLY_WIDTH / 2);
            if (leftArrowBounds.contains(mouseX, mouseY)) {
                scrollActions(-step);
                return true;
            }
            if (rightArrowBounds.contains(mouseX, mouseY)) {
                scrollActions(step);
                return true;
            }
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

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!actionsOverflow || actionsViewport.isEmpty()) return false;
        if (!actionsViewport.contains(mouseX, mouseY)) return false;
        scrollActions(-scrollY * ScaledCoord.scaleDim(APPLY_WIDTH / 2));
        return true;
    }

    private void scrollActions(double delta) {
        actionScrollOffset = Math.max(0, Math.min(actionScrollOffset + delta, actionMaxScroll));
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

    private void triggerAction(String id) {
        EditorSounds.playButtonClick();
        if (onAction != null) {
            onAction.accept(id);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════

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
