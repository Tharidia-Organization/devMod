package com.devmod.client.ui.editor.components;

import java.util.Objects;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.editor.core.EditorSounds;
import com.devmod.client.ui.editor.core.ResponsiveLayout;
import com.devmod.client.ui.editor.core.ScaledCoord;
import com.devmod.client.ui.editor.core.UIConstants;
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

    /**
     * Convenience: render as a vanilla Button for screens still using vanilla widgets.
     * This preserves our styling/state and delegates clicks back to this component.
     */
    public net.minecraft.client.gui.components.Button asVanilla(int x, int y, int width, int height) {
        String safeLabel = Objects.requireNonNull(label, "label");
        var title = Objects.requireNonNull(net.minecraft.network.chat.Component.literal(safeLabel), "label component");
        return net.minecraft.client.gui.components.Button.builder(title, btn -> {
                if (!enabled) return;
                if (onClick != null) onClick.run();
            })
            .bounds(x, y, width, height)
            .build();
    }

    /**
     * Wrapper to set visibility on the underlying vanilla widget when used via asVanilla.
     */
    public void setVisible(boolean visible) {
        // no-op placeholder to avoid null checks in callers; visibility handled on the vanilla widget
    }

    // ═══════════════════════════════════════════════════════════════
    // CONFIGURATION
    // ═══════════════════════════════════════════════════════════════

    private static final int GRADIENT_BAND_HEIGHT = 1;
    private static final int PRIMARY_MOUSE_BUTTON = 0;
    private static final int RING_INSET = 1;
    private static final int RING_EXPAND = 2;
    private static final int RING_ALPHA = 0x40;
    private static final int PRESSED_TEXT_OFFSET = 1;
    private static final int TEXT_SHADOW_OFFSET = 1;
    private static final int TEXT_SHADOW_COLOR = 0xFF000000;
    private static final int SMALL_HEIGHT = 16;
    private static final int LARGE_HEIGHT = 24;

    private static final float HIGHLIGHT_LIGHTEN = 0.05f;
    private static final float MID_DARKEN = 0.02f;
    private static final float SHADOW_DARKEN = 0.08f;
    private static final float ACCENT_OVERRIDE_DARKEN = 0.35f;
    private static final float DANGER_BASE_DARKEN = 0.04f;
    private static final float SUCCESS_BASE_DARKEN = 0.05f;
    private static final float PRIMARY_HOVER_LIGHTEN = 0.08f;
    private static final float PRIMARY_PRESS_DARKEN = 0.10f;
    private static final float DANGER_HOVER_LIGHTEN = 0.06f;
    private static final float DANGER_PRESS_DARKEN = 0.12f;
    private static final float SUCCESS_HOVER_LIGHTEN = 0.08f;
    private static final float SUCCESS_PRESS_DARKEN = 0.12f;
    private static final float GHOST_HOVER_LIGHTEN = 0.05f;
    private static final float GHOST_PRESS_DARKEN = 0.06f;
    private static final float DEFAULT_HOVER_LIGHTEN = 0.04f;
    private static final float DEFAULT_PRESS_DARKEN = 0.12f;
    private static final float DISABLED_BG_DARKEN = 0.25f;

    private static final String ELLIPSIS = "...";

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
        int highlight = UIConstants.lighten(bgColor, HIGHLIGHT_LIGHTEN);
        int mid = UIConstants.darken(bgColor, MID_DARKEN);
        int shadow = UIConstants.darken(bgColor, SHADOW_DARKEN);
        int gradBand = Math.max(GRADIENT_BAND_HEIGHT, ScaledCoord.scaleDim(GRADIENT_BAND_HEIGHT));
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
            int ringColor = UIConstants.withAlpha(borderColor, RING_ALPHA);
            AxiomRenderer.drawBorder(graphics, x - RING_INSET, y - RING_INSET,
                width + RING_EXPAND, height + RING_EXPAND, ringColor);
        }

        // Pressed effect (slightly offset text)
        int textOffsetY = pressed && hovered ? PRESSED_TEXT_OFFSET : 0;

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
        graphics.drawString(font, labelText, textX + TEXT_SHADOW_OFFSET, contentY + TEXT_SHADOW_OFFSET,
            TEXT_SHADOW_COLOR, false);
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
        int primaryBase = accentOverride != null ? UIConstants.darken(accentOverride, ACCENT_OVERRIDE_DARKEN)
            : UIConstants.ImpactButton.PRIMARY_BASE;
        int dangerBase = UIConstants.darken(UIConstants.ImpactButton.DANGER_BASE, DANGER_BASE_DARKEN);
        int successBase = UIConstants.darken(UIConstants.ImpactButton.SUCCESS_BASE, SUCCESS_BASE_DARKEN);

        return switch (style) {
            case PRIMARY -> new Palette(primaryBase,
                UIConstants.lighten(primaryBase, PRIMARY_HOVER_LIGHTEN),
                UIConstants.darken(primaryBase, PRIMARY_PRESS_DARKEN),
                accent,
                UIConstants.Text.PRIMARY());
            case DANGER -> new Palette(dangerBase,
                UIConstants.lighten(dangerBase, DANGER_HOVER_LIGHTEN),
                UIConstants.darken(dangerBase, DANGER_PRESS_DARKEN),
                UIConstants.ImpactButton.DANGER_BORDER,
                UIConstants.Text.PRIMARY());
            case SUCCESS -> new Palette(successBase,
                UIConstants.lighten(successBase, SUCCESS_HOVER_LIGHTEN),
                UIConstants.darken(successBase, SUCCESS_PRESS_DARKEN),
                UIConstants.ImpactButton.SUCCESS_BORDER,
                UIConstants.Text.PRIMARY());
            case GHOST -> new Palette(
                ghostBase,
                UIConstants.lighten(ghostBase, GHOST_HOVER_LIGHTEN),
                UIConstants.darken(ghostBase, GHOST_PRESS_DARKEN),
                UIConstants.Border.MUTED(),
                UIConstants.Text.PRIMARY());
            default -> new Palette(
                defaultBase,
                UIConstants.lighten(defaultBase, DEFAULT_HOVER_LIGHTEN),
                UIConstants.darken(defaultBase, DEFAULT_PRESS_DARKEN),
                UIConstants.Border.DEFAULT(),
                UIConstants.Text.PRIMARY());
        };
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!enabled || button != PRIMARY_MOUSE_BUTTON) return false;

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
        if (button != PRIMARY_MOUSE_BUTTON) {
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
            this.disabledBg = UIConstants.darken(UIConstants.Background.INPUT(), DISABLED_BG_DARKEN);
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
        int ellipsisWidth = font.width(ELLIPSIS);
        if (ellipsisWidth >= maxWidth) {
            return ELLIPSIS;
        }
        int allowed = maxWidth - ellipsisWidth;
        String slice = font.plainSubstrByWidth(safeText, allowed);
        return slice + ELLIPSIS;
    }

    /**
     * Taglie base per altezze differenti.
     */
    public enum Size {
        SMALL(SMALL_HEIGHT),
        MEDIUM(UIConstants.Size.BUTTON_HEIGHT),
        LARGE(LARGE_HEIGHT);

        final int height;

        Size(int height) {
            this.height = height;
        }

        public int height() {
            return height;
        }
    }
}
