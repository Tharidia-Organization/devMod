package com.devmod.client.ui.editor.components;

import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import com.devmod.client.ui.editor.core.ScaledCoord;
import com.devmod.client.ui.editor.core.Typography;
import com.devmod.client.ui.editor.core.UIConstants;

public class SourceBadge {

    /**
     * Source type indicating where a value originates from.
     */
    public enum Source {
        /** Value modified by DevMod (component or global config) */
        DEV("DEV", UIConstants.Accent.CYAN(), "Modified by DevMod"),
        /** Value loaded from item NBT data */
        NBT("NBT", UIConstants.Accent.ORANGE(), "From item NBT data"),
        /** Default vanilla value (no modification) */
        VANILLA("VAN", UIConstants.Text.MUTED(), "Vanilla default"),
        /** Value differs from original (unsaved changes) */
        MODIFIED("MOD", UIConstants.Accent.YELLOW, "Unsaved changes");

        private final String label;
        private final int color;
        private final String tooltip;

        Source(String label, int color, String tooltip) {
            this.label = label;
            this.color = color;
            this.tooltip = tooltip;
        }

        public String getLabel() { return label; }
        public int getColor() { return color; }
        public String getTooltip() { return tooltip; }
    }

    // Dimensions
    private static final int BADGE_WIDTH = 28;
    private static final int BADGE_HEIGHT = 12;
    private static final int INLINE_PADDING = 2;
    private static final int BORDER_THICKNESS = 1;
    private static final int HOVER_BG_ALPHA = 60;
    private static final int IDLE_BG_ALPHA = 40;
    private static final int BORDER_ALPHA = 120;
    private static final float MODIFIED_EPSILON = 0.0001f;

    private Source source = Source.VANILLA;
    private boolean hovered = false;

    public SourceBadge() {}

    public SourceBadge(Source source) {
        this.source = source;
    }

    /**
     * Set the source type.
     */
    public SourceBadge source(Source source) {
        this.source = source;
        return this;
    }

    public Source getSource() {
        return source;
    }

    public void setSource(Source source) {
        this.source = source;
    }

    /**
     * Check if badge is hovered (for tooltip display).
     */
    public boolean isHovered() {
        return hovered;
    }

    /**
     * Get tooltip text for the current source.
     */
    @Nullable
    public String getTooltipText() {
        return hovered ? source.getTooltip() : null;
    }

    /**
     * Render the badge at the given position.
     * @return width consumed
     */
    public int render(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        var font = Objects.requireNonNull(Minecraft.getInstance().font, "font cannot be null");

        int badgeW = ScaledCoord.scaleDim(BADGE_WIDTH);
        int badgeH = ScaledCoord.scaleDim(BADGE_HEIGHT);
        float textScale = Typography.buttonScale();

        this.hovered = mouseX >= x && mouseX < x + badgeW && mouseY >= y && mouseY < y + badgeH;

        // Background with source color (muted)
        int bgColor = UIConstants.withAlpha(source.getColor(), hovered ? HOVER_BG_ALPHA : IDLE_BG_ALPHA);
        graphics.fill(x, y, x + badgeW, y + badgeH, bgColor);

        // Border
        int borderColor = hovered ? source.getColor() : UIConstants.withAlpha(source.getColor(), BORDER_ALPHA);
        drawThinBorder(graphics, x, y, badgeW, badgeH, borderColor);

        // Label centered
        String label = Objects.requireNonNull(source.getLabel());
        int textWidth = Math.round(font.width(label) * textScale);
        int textX = x + (badgeW - textWidth) / 2;
        int textY = y + (badgeH - Math.round(font.lineHeight * textScale)) / 2;
        Typography.drawText(graphics, font, label, textX, textY, source.getColor(), textScale);

        return badgeW;
    }

    /**
     * Render inline badge next to a label (compact version).
     * @return width consumed
     */
    public int renderInline(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        var font = Objects.requireNonNull(Minecraft.getInstance().font, "font cannot be null");

        float textScale = Typography.buttonScale();
        String label = "[" + source.getLabel() + "]";
        int textWidth = Math.round(font.width(label) * textScale);

        int height = Math.round(font.lineHeight * textScale);
        this.hovered = mouseX >= x && mouseX < x + textWidth && mouseY >= y && mouseY < y + height;

        int color = hovered ? UIConstants.lighten(source.getColor(), 0.2f) : source.getColor();
        Typography.drawText(graphics, font, label, x, y, color, textScale);

        return textWidth + ScaledCoord.scale(INLINE_PADDING);
    }

    private void drawThinBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + BORDER_THICKNESS, color);           // top
        g.fill(x, y + h - BORDER_THICKNESS, x + w, y + h, color);   // bottom
        g.fill(x, y, x + BORDER_THICKNESS, y + h, color);           // left
        g.fill(x + w - BORDER_THICKNESS, y, x + w, y + h, color);   // right
    }

    /**
     * Get badge width for layout calculations.
     */
    public int getWidth() {
        return ScaledCoord.scaleDim(BADGE_WIDTH);
    }

    /**
     * Get badge height for layout calculations.
     */
    public int getHeight() {
        return ScaledCoord.scaleDim(BADGE_HEIGHT);
    }

    // ═══════════════════════════════════════════════════════════════
    // STATIC UTILITIES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Determine source based on value comparison.
     * @param currentValue current value
     * @param originalValue original value when loaded
     * @param vanillaDefault vanilla default value
     * @param hasNbtData whether item has NBT data for this stat
     * @param hasDevModData whether item has DevMod component/config data
     */
    public static Source determineSource(
            float currentValue,
            float originalValue,
            float vanillaDefault,
            boolean hasNbtData,
            boolean hasDevModData) {

        // If current differs from original, it's modified (unsaved)
        if (Math.abs(currentValue - originalValue) > MODIFIED_EPSILON) {
            return Source.MODIFIED;
        }

        // If has DevMod data (component or global config)
        if (hasDevModData) {
            return Source.DEV;
        }

        // If has NBT data
        if (hasNbtData) {
            return Source.NBT;
        }

        // Otherwise vanilla default
        return Source.VANILLA;
    }

    /**
     * Simplified source determination for boolean values.
     */
    public static Source determineSource(
            boolean currentValue,
            boolean originalValue,
            boolean vanillaDefault,
            boolean hasNbtData,
            boolean hasDevModData) {

        if (currentValue != originalValue) {
            return Source.MODIFIED;
        }
        if (hasDevModData) {
            return Source.DEV;
        }
        if (hasNbtData) {
            return Source.NBT;
        }
        return Source.VANILLA;
    }
}
