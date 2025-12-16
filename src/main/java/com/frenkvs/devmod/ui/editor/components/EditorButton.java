package com.frenkvs.devmod.ui.editor.components;

import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.editor.core.EditorSounds;
import com.frenkvs.devmod.ui.editor.core.ResponsiveLayout;
import com.frenkvs.devmod.ui.editor.core.UIConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Custom button component per l'editor.
 * Ora pensato come bottone principale: supporta varianti, icone, hotkey hint, toggle.
 *
 * @see EDITOR_DESIGN_SYSTEM.md Section 2.7
 */
public class EditorButton {

    // ═══════════════════════════════════════════════════════════════
    // STYLES
    // ═══════════════════════════════════════════════════════════════

    public enum Style {
        /** Default button style */
        NORMAL,
        /** Primary action (highlighted) */
        PRIMARY,
        /** Danger/destructive action */
        DANGER,
        /** Success/confirm action */
        SUCCESS,
        /** Ghost/transparent style */
        GHOST
    }

    // ═══════════════════════════════════════════════════════════════
    // CONFIGURATION
    // ═══════════════════════════════════════════════════════════════

    private final String id;
    private final String label;
    private Style style = Style.NORMAL;
    private Size size = Size.MEDIUM;
    private String icon = null;
    private String tooltip = null;
    private String hotkeyHint = null;
    private boolean enabled = true;
    private boolean playSound = true;
    private boolean toggleable = false;
    private boolean toggled = false;

    // State
    private boolean hovered = false;
    private boolean pressed = false;

    // Bounds
    private ResponsiveLayout.Rect bounds = ResponsiveLayout.Rect.EMPTY;

    // Callback
    private Runnable onClick;
    private Consumer<Boolean> onToggle;

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public EditorButton(String id, String label) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.label = Objects.requireNonNull(label, "label cannot be null");
    }

    // ═══════════════════════════════════════════════════════════════
    // BUILDER METHODS
    // ═══════════════════════════════════════════════════════════════

    public EditorButton style(Style style) {
        this.style = style;
        return this;
    }

    public EditorButton size(Size size) {
        this.size = size;
        return this;
    }

    /**
     * Icona testuale (glyph) da mostrare a sinistra del label.
     */
    public EditorButton icon(String icon) {
        this.icon = icon;
        return this;
    }

    public EditorButton tooltip(String tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    public EditorButton hotkeyHint(String hotkeyHint) {
        this.hotkeyHint = hotkeyHint;
        return this;
    }

    public EditorButton enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public EditorButton playSound(boolean playSound) {
        this.playSound = playSound;
        return this;
    }

    public EditorButton toggleable(boolean toggleable) {
        this.toggleable = toggleable;
        return this;
    }

    public EditorButton toggled(boolean toggled) {
        this.toggleable = true;
        this.toggled = toggled;
        return this;
    }

    public EditorButton onClick(Runnable callback) {
        this.onClick = callback;
        return this;
    }

    public EditorButton onToggle(Consumer<Boolean> callback) {
        this.onToggle = callback;
        return this;
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDERING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Render del bottone alla posizione indicata.
     */
    public void render(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;

        // Update bounds
        this.bounds = new ResponsiveLayout.Rect(x, y, width, height);

        // Check hover state
        this.hovered = enabled && bounds.contains(mouseX, mouseY);

        // Get colors based on style and state
        boolean active = toggleable && toggled;
        int bgColor = getBackgroundColor(active);
        int borderColor = getBorderColor(active);
        int textColor = getTextColor(active);

        // Background
        graphics.fill(x, y, x + width, y + height, bgColor);

        // Border
        AxiomRenderer.drawBorder(graphics, x, y, width, height, borderColor);

        // Pressed effect (slightly offset text)
        int textOffsetY = pressed && hovered ? 1 : 0;

        // Content layout
        int padding = UIConstants.Spacing.SM;
        int contentY = y + (height - font.lineHeight) / 2 + textOffsetY;

        // Right hotkey hint
        int hintWidth = hotkeyHint != null ? font.width(hotkeyHint) : 0;
        int hintX = hotkeyHint != null ? x + width - padding - hintWidth : x + width - padding;

        if (hotkeyHint != null) {
            graphics.drawString(font, hotkeyHint, hintX, contentY, UIConstants.Text.MUTED, false);
        }

        // Left icon
        int iconWidth = icon != null ? font.width(icon) : 0;
        int iconX = x + padding;
        if (icon != null) {
            graphics.drawString(font, icon, iconX, contentY, textColor, false);
        }

        // Label, con ellissi se serve
        int labelStartX = icon != null ? iconX + iconWidth + UIConstants.Spacing.SM : x + padding;
        int labelAreaRight = hotkeyHint != null ? hintX - UIConstants.Spacing.SM : x + width - padding;
        int labelAreaWidth = Math.max(0, labelAreaRight - labelStartX);
        String labelText = Objects.requireNonNull(fitToWidth(label, labelAreaWidth, font), "labelText");
        int textWidth = font.width(labelText);
        int textX = labelStartX + Math.max(0, (labelAreaWidth - textWidth) / 2);
        graphics.drawString(font, labelText, textX, contentY, textColor, false);
    }

    /**
     * Render con dimensioni di default.
     */
    public void render(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        render(graphics, x, y, UIConstants.Size.BUTTON_WIDTH, size.height, mouseX, mouseY);
    }

    private int getBackgroundColor(boolean active) {
        if (!enabled) {
            return UIConstants.Button.DISABLED;
        }

        return switch (style) {
            case PRIMARY -> (pressed && hovered) || active
                ? UIConstants.darken(UIConstants.Accent.CYAN, 0.2f)
                : (hovered ? UIConstants.lighten(UIConstants.Accent.CYAN, 0.05f) : UIConstants.Accent.CYAN);
            case DANGER -> (pressed && hovered) || active
                ? UIConstants.darken(UIConstants.Accent.RED, 0.2f)
                : (hovered ? UIConstants.lighten(UIConstants.Accent.RED, 0.05f) : UIConstants.Accent.RED);
            case SUCCESS -> (pressed && hovered) || active
                ? UIConstants.darken(UIConstants.Accent.GREEN, 0.2f)
                : (hovered ? UIConstants.lighten(UIConstants.Accent.GREEN, 0.05f) : UIConstants.Accent.GREEN);
            case GHOST -> hovered || active ? UIConstants.Background.HOVER : UIConstants.Background.INPUT;
            default -> (pressed && hovered) || active
                ? UIConstants.Background.ACTIVE
                : (hovered ? UIConstants.Background.HOVER : UIConstants.Background.INPUT);
        };
    }

    private int getBorderColor(boolean active) {
        if (!enabled) {
            return UIConstants.Border.MUTED;
        }

        return switch (style) {
            case PRIMARY -> UIConstants.Accent.CYAN;
            case DANGER -> UIConstants.Accent.RED;
            case SUCCESS -> UIConstants.Accent.GREEN;
            case GHOST -> (hovered || active) ? UIConstants.Border.HOVER : UIConstants.Border.DEFAULT;
            default -> (hovered || active) ? UIConstants.Border.HOVER : UIConstants.Border.DEFAULT;
        };
    }

    private int getTextColor(boolean active) {
        if (!enabled) {
            return UIConstants.Text.DISABLED;
        }

        return switch (style) {
            case GHOST -> hovered || active ? UIConstants.Text.PRIMARY : UIConstants.Text.SECONDARY;
            default -> UIConstants.Text.PRIMARY;
        };
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!enabled || button != 0) return false;

        if (bounds.contains(mouseX, mouseY)) {
            pressed = true;
            return true;
        }

        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!pressed) {
            return false;
        }

        pressed = false;
        if (button != 0) {
            return false;
        }

        if (bounds.contains(mouseX, mouseY) && enabled) {
            // Trigger click
            if (playSound) {
                EditorSounds.playButtonClick();
            }

            if (toggleable) {
                toggled = !toggled;
                if (onToggle != null) {
                    onToggle.accept(toggled);
                }
            }

            if (onClick != null) {
                onClick.run();
            }
            return true;
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public Style getStyle() {
        return style;
    }

    public String getTooltip() {
        return tooltip;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isHovered() {
        return hovered;
    }

    public boolean isPressed() {
        return pressed;
    }

    public ResponsiveLayout.Rect getBounds() {
        return bounds;
    }

    public boolean isToggleable() {
        return toggleable;
    }

    public boolean isToggled() {
        return toggled;
    }

    public Size getSize() {
        return size;
    }

    /**
     * Ritorna un tooltip attivo (solo se hovered e presente).
     */
    public String activeTooltip() {
        return hovered ? tooltip : null;
    }

    private String fitToWidth(String text, int maxWidth, net.minecraft.client.gui.Font font) {
        String safeText = Objects.requireNonNull(text, "text");
        if (maxWidth <= 0) {
            return "";
        }
        if (font.width(safeText) <= maxWidth) {
            return safeText;
        }
        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        if (ellipsisWidth >= maxWidth) {
            return ellipsis;
        }
        int allowed = maxWidth - ellipsisWidth;
        String slice = font.plainSubstrByWidth(safeText, allowed);
        return slice + ellipsis;
    }

    /**
     * Taglie base per altezze differenti.
     */
    public enum Size {
        SMALL(16),
        MEDIUM(UIConstants.Size.BUTTON_HEIGHT),
        LARGE(24);

        final int height;

        Size(int height) {
            this.height = height;
        }

        public int height() {
            return height;
        }
    }
}
