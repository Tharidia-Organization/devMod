package com.frenkvs.devmod.ui.editor.components;

import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.editor.core.EditorSounds;
import com.frenkvs.devmod.ui.editor.core.ResponsiveLayout;
import com.frenkvs.devmod.ui.editor.core.UIConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Objects;

/**
 * Custom button component for the editor.
 * Supports primary/secondary styles and icons.
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
    private String tooltip = null;
    private boolean enabled = true;
    private boolean playSound = true;

    // State
    private boolean hovered = false;
    private boolean pressed = false;

    // Bounds
    private ResponsiveLayout.Rect bounds = ResponsiveLayout.Rect.EMPTY;

    // Callback
    private Runnable onClick;

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public EditorButton(String id, String label) {
        this.id = id;
        this.label = label;
    }

    // ═══════════════════════════════════════════════════════════════
    // BUILDER METHODS
    // ═══════════════════════════════════════════════════════════════

    public EditorButton style(Style style) {
        this.style = style;
        return this;
    }

    public EditorButton tooltip(String tooltip) {
        this.tooltip = tooltip;
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

    public EditorButton onClick(Runnable callback) {
        this.onClick = callback;
        return this;
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDERING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Render the button at the given position.
     */
    public void render(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;

        // Update bounds
        this.bounds = new ResponsiveLayout.Rect(x, y, width, height);

        // Check hover state
        this.hovered = enabled && bounds.contains(mouseX, mouseY);

        // Get colors based on style and state
        int bgColor = getBackgroundColor();
        int borderColor = getBorderColor();
        int textColor = getTextColor();

        // Background
        graphics.fill(x, y, x + width, y + height, bgColor);

        // Border
        AxiomRenderer.drawBorder(graphics, x, y, width, height, borderColor);

        // Pressed effect (slightly offset text)
        int textOffsetY = pressed && hovered ? 1 : 0;

        // Label (centered)
        int textWidth = font.width(Objects.requireNonNull(label, "label cannot be null"));
        int textX = x + (width - textWidth) / 2;
        int textY = y + (height - 8) / 2 + textOffsetY;
        graphics.drawString(font, label, textX, textY, textColor, false);
    }

    /**
     * Render at default size.
     */
    public void render(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        render(graphics, x, y, UIConstants.Size.BUTTON_WIDTH, UIConstants.Size.BUTTON_HEIGHT, mouseX, mouseY);
    }

    private int getBackgroundColor() {
        if (!enabled) {
            return UIConstants.Button.DISABLED;
        }

        return switch (style) {
            case PRIMARY -> pressed && hovered ? UIConstants.Button.PRIMARY_PRESS :
                           (hovered ? UIConstants.Button.PRIMARY_HOVER : UIConstants.Button.PRIMARY);
            case DANGER -> pressed && hovered ? UIConstants.darken(UIConstants.Accent.RED, 0.3f) :
                          (hovered ? UIConstants.lighten(UIConstants.Accent.RED, 0.1f) : UIConstants.Accent.RED);
            case SUCCESS -> pressed && hovered ? UIConstants.darken(UIConstants.Accent.GREEN, 0.3f) :
                           (hovered ? UIConstants.lighten(UIConstants.Accent.GREEN, 0.1f) : UIConstants.Accent.GREEN);
            case GHOST -> hovered ? UIConstants.Background.HOVER : 0x00000000;
            default -> pressed && hovered ? UIConstants.Button.PRESS :
                      (hovered ? UIConstants.Button.HOVER : UIConstants.Button.NORMAL);
        };
    }

    private int getBorderColor() {
        if (!enabled) {
            return UIConstants.Border.MUTED;
        }

        return switch (style) {
            case PRIMARY -> UIConstants.Accent.CYAN;
            case DANGER -> UIConstants.Accent.RED;
            case SUCCESS -> UIConstants.Accent.GREEN;
            case GHOST -> hovered ? UIConstants.Border.DEFAULT : 0x00000000;
            default -> hovered ? UIConstants.Border.HOVER : UIConstants.Border.DEFAULT;
        };
    }

    private int getTextColor() {
        if (!enabled) {
            return UIConstants.Text.DISABLED;
        }

        return switch (style) {
            case GHOST -> hovered ? UIConstants.Text.PRIMARY : UIConstants.Text.SECONDARY;
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
        if (pressed) {
            pressed = false;

            if (bounds.contains(mouseX, mouseY) && enabled) {
                // Trigger click
                if (playSound) {
                    EditorSounds.playButtonClick();
                }

                if (onClick != null) {
                    onClick.run();
                }
                return true;
            }
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
}
