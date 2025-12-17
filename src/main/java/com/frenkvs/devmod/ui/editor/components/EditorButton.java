package com.frenkvs.devmod.ui.editor.components;

import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.editor.core.EditorSounds;
import com.frenkvs.devmod.ui.editor.core.ResponsiveLayout;
import com.frenkvs.devmod.ui.editor.core.ScaledCoord;
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
    private Integer accentOverride = null;

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

    /**
     * Accent color override (ARGB). If set, it replaces the default Impact accent for this button.
     */
    public EditorButton accent(int argb) {
        this.accentOverride = argb;
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

    public static Builder builder(String id, String label) {
        return new Builder(id, label);
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

        // Palette Impact + override
        boolean active = toggleable && toggled;
        Palette palette = paletteFor(style, accentOverride);
        int bgColor = pickBackground(palette, active);
        int borderColor = pickBorder(palette, active);
        int textColor = pickText(palette, active);

        // Background + impact-style bevel
        graphics.fill(x, y, x + width, y + height, bgColor);
        int highlight = UIConstants.lighten(bgColor, 0.05f);
        int mid = UIConstants.darken(bgColor, 0.02f);
        int shadow = UIConstants.darken(bgColor, 0.08f);
        int gradBand = Math.max(1, ScaledCoord.scaleDim(1));
        // top glow
        graphics.fill(x, y, x + width, y + gradBand, highlight);
        // mid tone
        graphics.fill(x, y + gradBand, x + width, y + height - gradBand, mid);
        // bottom shadow
        graphics.fill(x, y + height - gradBand, x + width, y + height, shadow);

        // Border
        AxiomRenderer.drawBorder(graphics, x, y, width, height, borderColor);
        // Focus/hover ring (impact accent)
        if ((hovered || active) && enabled) {
            int ringColor = UIConstants.withAlpha(borderColor, 0x40);
            AxiomRenderer.drawBorder(graphics, x - 1, y - 1, width + 2, height + 2, ringColor);
        }

        // Pressed effect (slightly offset text)
        int textOffsetY = pressed && hovered ? 1 : 0;

        // Content layout
        int padding = UIConstants.Spacing.SM;
        int contentY = y + (height - font.lineHeight) / 2 + textOffsetY;

        // Right hotkey hint
        int hintWidth = hotkeyHint != null ? font.width(hotkeyHint) : 0;
        int hintX = hotkeyHint != null ? x + width - padding - hintWidth : x + width - padding;

        if (hotkeyHint != null) {
            graphics.drawString(font, hotkeyHint, hintX, contentY, UIConstants.Text.MUTED(), false);
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
        // Soft shadow per la leggibilità (opaco per evitare trasparenze sui testi)
        graphics.drawString(font, labelText, textX + 1, contentY + 1, 0xFF000000, false);
        graphics.drawString(font, labelText, textX, contentY, textColor, false);
    }

    /**
     * Render con dimensioni di default.
     */
    public void render(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        render(graphics, x, y, UIConstants.Size.BUTTON_WIDTH, size.height, mouseX, mouseY);
    }

    private int pickBackground(Palette palette, boolean active) {
        if (!enabled) return palette.disabledBg;
        if ((pressed && hovered) || active) return palette.press;
        if (hovered) return palette.hover;
        return palette.normal;
    }

    private int pickBorder(Palette palette, boolean active) {
        if (!enabled) return palette.disabledBorder;
        if (hovered || active) return palette.hoverBorder;
        return palette.border;
    }

    private int pickText(Palette palette, boolean active) {
        if (!enabled) return palette.disabledText;
        if (style == Style.GHOST && !hovered && !active) return UIConstants.Text.SECONDARY();
        return palette.text;
    }

    private Palette paletteFor(Style style, Integer accentOverride) {
        // Palette Impact HUD centralizzata in UIConstants
        int defaultBase = UIConstants.ImpactButton.DEFAULT_BASE;
        int ghostBase = UIConstants.ImpactButton.GHOST_BASE;

        int accent = accentOverride != null ? accentOverride : UIConstants.ImpactButton.PRIMARY_BORDER;
        int primaryBase = accentOverride != null ? UIConstants.darken(accentOverride, 0.35f) : UIConstants.ImpactButton.PRIMARY_BASE;
        int dangerBase = UIConstants.darken(UIConstants.ImpactButton.DANGER_BASE, 0.04f);
        int successBase = UIConstants.darken(UIConstants.ImpactButton.SUCCESS_BASE, 0.05f);

        return switch (style) {
            case PRIMARY -> new Palette(primaryBase,
                UIConstants.lighten(primaryBase, 0.08f),
                UIConstants.darken(primaryBase, 0.10f),
                accent,
                UIConstants.Text.PRIMARY());
            case DANGER -> new Palette(dangerBase,
                UIConstants.lighten(dangerBase, 0.06f),
                UIConstants.darken(dangerBase, 0.12f),
                UIConstants.ImpactButton.DANGER_BORDER,
                UIConstants.Text.PRIMARY());
            case SUCCESS -> new Palette(successBase,
                UIConstants.lighten(successBase, 0.08f),
                UIConstants.darken(successBase, 0.12f),
                UIConstants.ImpactButton.SUCCESS_BORDER,
                UIConstants.Text.PRIMARY());
            case GHOST -> new Palette(
                ghostBase,
                UIConstants.lighten(ghostBase, 0.05f),
                UIConstants.darken(ghostBase, 0.06f),
                UIConstants.Border.MUTED(),
                UIConstants.Text.PRIMARY());
            default -> new Palette(
                defaultBase,
                UIConstants.lighten(defaultBase, 0.04f),
                UIConstants.darken(defaultBase, 0.12f),
                UIConstants.Border.DEFAULT(),
                UIConstants.Text.PRIMARY());
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

    private static final class Palette {
        final int normal;
        final int hover;
        final int press;
        final int border;
        final int text;
        final int hoverBorder;
        final int disabledBg;
        final int disabledBorder;
        final int disabledText;

        Palette(int normal, int hover, int press, int border, int text) {
            this.normal = normal;
            this.hover = hover;
            this.press = press;
            this.border = border;
            this.text = text;
            this.hoverBorder = UIConstants.Border.ACCENT();
            this.disabledBg = UIConstants.darken(UIConstants.Background.INPUT(), 0.25f);
            this.disabledBorder = UIConstants.Border.MUTED();
            this.disabledText = UIConstants.Text.DISABLED();
        }
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

    public static final class Builder {
        private final EditorButton button;

        private Builder(String id, String label) {
            this.button = new EditorButton(id, label);
        }

        public Builder style(Style style) {
            button.style(style);
            return this;
        }

        public Builder size(Size size) {
            button.size(size);
            return this;
        }

        public Builder icon(String icon) {
            button.icon(icon);
            return this;
        }

        public Builder tooltip(String tooltip) {
            button.tooltip(tooltip);
            return this;
        }

        public Builder hotkeyHint(String hint) {
            button.hotkeyHint(hint);
            return this;
        }

        public Builder enabled(boolean enabled) {
            button.enabled(enabled);
            return this;
        }

        public Builder playSound(boolean play) {
            button.playSound(play);
            return this;
        }

        public Builder toggleable(boolean toggleable) {
            button.toggleable(toggleable);
            return this;
        }

        public Builder toggled(boolean toggled) {
            button.toggled(toggled);
            return this;
        }

        public Builder accent(int argb) {
            button.accent(argb);
            return this;
        }

        public Builder onClick(Runnable callback) {
            button.onClick(callback);
            return this;
        }

        public Builder onToggle(Consumer<Boolean> callback) {
            button.onToggle(callback);
            return this;
        }

        public EditorButton build() {
            return button;
        }
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
