package com.devmod.client.ui.editor.components;

import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import com.devmod.client.ui.editor.core.EditorDimensions;
import com.devmod.client.ui.editor.core.EditorSounds;
import com.devmod.client.ui.editor.core.ResponsiveLayout;
import com.devmod.client.ui.editor.core.UIConstants;

public class SectionHeader {

    // ═══════════════════════════════════════════════════════════════
    // DIMENSIONS
    // ═══════════════════════════════════════════════════════════════

    private static final int HEIGHT = EditorDimensions.SECTION_HEADER_HEIGHT;  // 24px
    private static final int TEXT_HEIGHT = 8;
    private static final int TEXT_OFFSET_Y = (HEIGHT - TEXT_HEIGHT) / 2;
    private static final int TEXT_INSET_X = 8;
    private static final int ACCENT_WIDTH = 2;
    private static final int COLLAPSE_INDICATOR_X = 6;
    private static final int COLLAPSE_TEXT_INSET_X = 18;
    private static final int SEPARATOR_HEIGHT = 1;

    // ═══════════════════════════════════════════════════════════════
    // CONFIGURATION
    // ═══════════════════════════════════════════════════════════════

    private final String id;
    private final String title;
    private boolean collapsible = false;
    private boolean collapsed = false;
    private String tooltip = null;

    // State
    private boolean hovered = false;

    // Bounds
    private ResponsiveLayout.Rect bounds = ResponsiveLayout.Rect.EMPTY;

    // Callback
    private Runnable onToggle;

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public SectionHeader(String id, String title) {
        this.id = id;
        this.title = title;
    }

    // ═══════════════════════════════════════════════════════════════
    // BUILDER METHODS
    // ═══════════════════════════════════════════════════════════════

    public SectionHeader collapsible(boolean collapsible) {
        this.collapsible = collapsible;
        return this;
    }

    public SectionHeader collapsed(boolean collapsed) {
        this.collapsed = collapsed;
        return this;
    }

    public SectionHeader tooltip(String tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    public SectionHeader onToggle(Runnable callback) {
        this.onToggle = callback;
        return this;
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDERING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Render the section header at the given position.
     * @return The height consumed
     */
    public int render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
        var font = Objects.requireNonNull(Minecraft.getInstance().font, "font cannot be null");

        this.bounds = new ResponsiveLayout.Rect(x, y, width, HEIGHT);
        this.hovered = bounds.contains(mouseX, mouseY);

        // Background - subtle darker bar
        graphics.fill(x, y, x + width, y + HEIGHT, UIConstants.Background.HEADER());

        // Left border accent
        graphics.fill(x, y, x + ACCENT_WIDTH, y + HEIGHT, UIConstants.Accent.CYAN());

        // Collapse indicator (if collapsible)
        int textX = x + TEXT_INSET_X;
        if (collapsible) {
            String indicator = collapsed ? "▶" : "▼";
            int indicatorColor = hovered ? UIConstants.Text.PRIMARY() : UIConstants.Text.SECONDARY();
            graphics.drawString(font, indicator, x + COLLAPSE_INDICATOR_X, y + TEXT_OFFSET_Y, indicatorColor, false);
            textX = x + COLLAPSE_TEXT_INSET_X;
        }

        // Title
        int titleColor = UIConstants.Text.TITLE();
        graphics.drawString(font, title, textX, y + TEXT_OFFSET_Y, titleColor, false);

        // Bottom separator line
        graphics.fill(x, y + HEIGHT - SEPARATOR_HEIGHT, x + width, y + HEIGHT, UIConstants.Border.SEPARATOR());

        return HEIGHT;
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        if (collapsible && bounds.contains(mouseX, mouseY)) {
            collapsed = !collapsed;
            EditorSounds.playTabSwitch();
            if (onToggle != null) {
                onToggle.run();
            }
            return true;
        }

        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTERS & SETTERS
    // ═══════════════════════════════════════════════════════════════

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public boolean isCollapsible() {
        return collapsible;
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
    }

    public String getTooltip() {
        return tooltip;
    }

    public int getHeight() {
        return HEIGHT;
    }

    public ResponsiveLayout.Rect getBounds() {
        return bounds;
    }
}
