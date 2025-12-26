package com.devmod.client.ui.editor.components;

import java.util.Objects;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.editor.core.EditorSounds;
import com.devmod.client.ui.editor.core.ResponsiveLayout;
import com.devmod.client.ui.editor.core.ScaledCoord;
import com.devmod.client.ui.editor.core.Typography;
import com.devmod.client.ui.editor.core.UIConstants;

public class ModeBadge {

    // ═══════════════════════════════════════════════════════════════
    // DIMENSIONS (from Section 2.4 Header Zone)
    // ═══════════════════════════════════════════════════════════════

    private static final int WIDTH = 100;
    private static final int HEIGHT = 20;
    private static final int DROPDOWN_PADDING = 10;
    private static final int OPTION_TEXT_INSET = 6;
    private static final int OPTION_BORDER_INSET = 1;
    private static final int OPTION_INDICATOR_WIDTH = 2;

    // ═══════════════════════════════════════════════════════════════
    // BADGE TYPES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Type of badge - determines display style.
     */
    public enum BadgeType {
        /**
         * Scope badge: GLOBAL affects all items of type, SPECIFIC affects only this item.
         */
        SCOPE,
        /**
         * Mode badge: PREVIEW for safe experimentation, APPLY for persistent changes.
         */
        MODE
    }

    /**
     * Scope values for SCOPE badge type.
     */
    public enum Scope {
        /** Changes affect all items of this type */
        GLOBAL("●GLOBAL", "⬤", UIConstants.Mode.GLOBAL_BORDER, UIConstants.Mode.GLOBAL_BG),
        /** Changes affect only this specific item */
        SPECIFIC("●SPECIFIC", "◉", UIConstants.Mode.SPECIFIC_BORDER, UIConstants.Mode.SPECIFIC_BG);

        private final String label;
        private final String icon;
        private final int borderColor;
        private final int bgColor;

        Scope(String label, String icon, int borderColor, int bgColor) {
            this.label = label;
            this.icon = icon;
            this.borderColor = borderColor;
            this.bgColor = bgColor;
        }

        public String getLabel() { return label; }
        public String getIcon() { return icon; }
        public int getBorderColor() { return borderColor; }
        public int getBgColor() { return bgColor; }
    }

    /**
     * Mode values for MODE badge type.
     */
    public enum Mode {
        /** Preview mode - changes are client-only, reset on close */
        PREVIEW("PREVIEW", "👁", UIConstants.Mode.PREVIEW_BORDER, UIConstants.Mode.PREVIEW_BG),
        /** Apply mode - changes sent to server on Apply */
        APPLY("APPLY", "⚡", UIConstants.Mode.APPLY_BORDER, UIConstants.Mode.APPLY_BG);

        private final String label;
        private final String icon;
        private final int borderColor;
        private final int bgColor;

        Mode(String label, String icon, int borderColor, int bgColor) {
            this.label = label;
            this.icon = icon;
            this.borderColor = borderColor;
            this.bgColor = bgColor;
        }

        public String getLabel() { return label; }
        public String getIcon() { return icon; }
        public int getBorderColor() { return borderColor; }
        public int getBgColor() { return bgColor; }
    }

    // ═══════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════

    private BadgeType badgeType = BadgeType.SCOPE;
    private Scope scope = Scope.GLOBAL;
    private Mode mode = Mode.PREVIEW;
    private boolean clickable = true;
    private boolean hovered = false;
    private boolean showDropdown = false;
    private int lastRenderX = 0;
    private int lastRenderY = 0;

    // Bounds
    private ResponsiveLayout.Rect bounds = ResponsiveLayout.Rect.EMPTY;

    // Callbacks
    @Nullable
    private Consumer<Scope> onScopeChange;
    @Nullable
    private Consumer<Mode> onModeChange;
    @Nullable
    private Runnable onSetDefaultMode;

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public ModeBadge() {}

    public ModeBadge(BadgeType type) {
        this.badgeType = type;
    }

    // ═══════════════════════════════════════════════════════════════
    // BUILDER METHODS
    // ═══════════════════════════════════════════════════════════════

    public ModeBadge badgeType(BadgeType type) {
        this.badgeType = type;
        return this;
    }

    public boolean isHovered() {
        return hovered;
    }

    @Nullable
    public String getTooltipText() {
        if (!hovered) return null;
        if (badgeType == BadgeType.SCOPE) {
            return scope == Scope.GLOBAL
                ? "Changes will apply to ALL items of this type"
                : "Changes will apply only to THIS specific item";
        } else {
            return mode == Mode.PREVIEW
                ? "Preview: local-only changes (no server sync)"
                : "Apply: changes will be persisted and sent to server";
        }
    }

    public ModeBadge scope(Scope scope) {
        this.scope = scope;
        return this;
    }

    public ModeBadge mode(Mode mode) {
        this.mode = mode;
        return this;
    }

    public ModeBadge clickable(boolean clickable) {
        this.clickable = clickable;
        return this;
    }

    public ModeBadge onScopeChange(Consumer<Scope> callback) {
        this.onScopeChange = callback;
        return this;
    }

    public ModeBadge onModeChange(Consumer<Mode> callback) {
        this.onModeChange = callback;
        return this;
    }

    public ModeBadge onSetDefaultMode(Runnable callback) {
        this.onSetDefaultMode = callback;
        return this;
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDERING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Render the badge at the given position.
     * @return The width consumed
     */
    public int render(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        var font = Objects.requireNonNull(Minecraft.getInstance().font, "font cannot be null");

        int badgeWidth = ScaledCoord.scaleDim(WIDTH);
        int badgeHeight = ScaledCoord.scaleDim(HEIGHT);
        int dropdownPadding = ScaledCoord.scale(DROPDOWN_PADDING);
        float textScale = Typography.buttonScale();

        this.bounds = new ResponsiveLayout.Rect(x, y, badgeWidth, badgeHeight);
        this.lastRenderX = x;
        this.lastRenderY = y;
        this.hovered = clickable && bounds.contains(mouseX, mouseY);

        // Get current display values based on badge type
        String label;
        int borderColor;
        int bgColor;

        if (badgeType == BadgeType.SCOPE) {
            label = scope.getLabel();
            borderColor = scope.getBorderColor();
            bgColor = scope.getBgColor();
        } else {
            label = mode.getIcon() + " " + mode.getLabel();
            borderColor = mode.getBorderColor();
            bgColor = mode.getBgColor();
        }

        // Hover effect
        if (hovered) {
            bgColor = UIConstants.lighten(bgColor, 0.2f);
        }

        // Background
        graphics.fill(x, y, x + badgeWidth, y + badgeHeight, bgColor);

        // Border
        AxiomRenderer.drawBorder(graphics, x, y, badgeWidth, badgeHeight, borderColor);

        // Text (centered)
        int textWidth = label != null ? Math.round(font.width(label) * textScale) : 0;
        int textHeight = Math.round(font.lineHeight * textScale);
        int textX = x + (badgeWidth - textWidth) / 2;
        int textY = y + (badgeHeight - textHeight) / 2;
        Typography.drawText(graphics, font, label, textX, textY, UIConstants.Text.PRIMARY(), textScale);

        // Dropdown indicator (if clickable)
        if (clickable) {
            String indicator = showDropdown ? "▲" : "▼";
            int indicatorWidth = Math.round(font.width(indicator) * textScale);
            int indicatorX = x + badgeWidth - dropdownPadding - indicatorWidth / 2;
            Typography.drawText(graphics, font, indicator, indicatorX, textY, UIConstants.Text.MUTED(), textScale);
        }

        return badgeWidth;
    }

    /**
     * Render dropdown overlay on a second pass so it appears above content.
     */
    public void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!showDropdown) return;
        renderDropdown(graphics, lastRenderX, lastRenderY + ScaledCoord.scaleDim(HEIGHT), mouseX, mouseY);
    }

    private void renderDropdown(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        var font = Objects.requireNonNull(Minecraft.getInstance().font, "font cannot be null");

        int dropdownHeight;
        Object[] options;
        int badgeWidth = ScaledCoord.scaleDim(WIDTH);
        int badgeHeight = ScaledCoord.scaleDim(HEIGHT);
        float textScale = Typography.buttonScale();

        boolean includeDefaultAction = badgeType == BadgeType.MODE && onSetDefaultMode != null;

        if (badgeType == BadgeType.SCOPE) {
            options = Scope.values();
            dropdownHeight = options.length * badgeHeight;
        } else {
            options = Mode.values();
            dropdownHeight = options.length * badgeHeight + (includeDefaultAction ? badgeHeight : 0);
        }

        // Dropdown background
        graphics.fill(x, y, x + badgeWidth, y + dropdownHeight, UIConstants.Background.PANEL_SOLID());
        AxiomRenderer.drawBorder(graphics, x, y, badgeWidth, dropdownHeight, UIConstants.Border.DEFAULT());

        // Render options
        int optionY = y;
        for (Object option : options) {
            String label;
            int borderColor;
            boolean isSelected;

            if (badgeType == BadgeType.SCOPE) {
                Scope s = (Scope) option;
                label = s.getLabel();
                borderColor = s.getBorderColor();
                isSelected = s == scope;
            } else {
                Mode m = (Mode) option;
                label = m.getIcon() + " " + m.getLabel();
                borderColor = m.getBorderColor();
                isSelected = m == mode;
            }

            ResponsiveLayout.Rect optionBounds = new ResponsiveLayout.Rect(x, optionY, badgeWidth, badgeHeight);
            boolean optionHovered = optionBounds.contains(mouseX, mouseY);

            // Option background
            int optionBg = isSelected ? UIConstants.Background.ACTIVE() :
                          (optionHovered ? UIConstants.Background.HOVER() : UIConstants.Background.INPUT());
            graphics.fill(x + OPTION_BORDER_INSET, optionY, x + badgeWidth - OPTION_BORDER_INSET,
                optionY + badgeHeight, optionBg);

            // Selection indicator
            if (isSelected) {
                graphics.fill(x + OPTION_BORDER_INSET, optionY, x + OPTION_BORDER_INSET + OPTION_INDICATOR_WIDTH,
                    optionY + badgeHeight, borderColor);
            }

            // Option text
            int textX = x + OPTION_TEXT_INSET;
            int textY = optionY + (badgeHeight - Math.round(font.lineHeight * textScale)) / 2;
            int textColor = isSelected ? UIConstants.Text.PRIMARY() : UIConstants.Text.SECONDARY();
            Typography.drawText(graphics, font, label, textX, textY, textColor, textScale);

            optionY += badgeHeight;
        }

        // Optional "Set as default" action for mode badge
        if (includeDefaultAction) {
            ResponsiveLayout.Rect optionBounds = new ResponsiveLayout.Rect(x, optionY, badgeWidth, badgeHeight);
            boolean optionHovered = optionBounds.contains(mouseX, mouseY);
            int optionBg = optionHovered ? UIConstants.Background.HOVER() : UIConstants.Background.INPUT();
            graphics.fill(x + OPTION_BORDER_INSET, optionY, x + badgeWidth - OPTION_BORDER_INSET,
                optionY + badgeHeight, optionBg);
            int textX = x + OPTION_TEXT_INSET;
            int textY = optionY + (badgeHeight - Math.round(font.lineHeight * textScale)) / 2;
            Typography.drawText(graphics, font, "Set current as default", textX, textY,
                UIConstants.Text.SECONDARY(), textScale);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!clickable || button != 0) return false;

        // Check dropdown options first (if open)
        if (showDropdown) {
            int dropdownY = bounds.y() + HEIGHT;
            Object[] options = badgeType == BadgeType.SCOPE ? Scope.values() : Mode.values();
            boolean includeDefaultAction = badgeType == BadgeType.MODE && onSetDefaultMode != null;

            for (int i = 0; i < options.length; i++) {
                ResponsiveLayout.Rect optionBounds = new ResponsiveLayout.Rect(
                    bounds.x(), dropdownY + i * HEIGHT, WIDTH, HEIGHT
                );

                if (optionBounds.contains(mouseX, mouseY)) {
                    if (badgeType == BadgeType.SCOPE) {
                        Scope newScope = (Scope) options[i];
                        if (newScope != scope) {
                            scope = newScope;
                            EditorSounds.playTabSwitch();
                            if (onScopeChange != null) {
                                onScopeChange.accept(scope);
                            }
                        }
                    } else {
                        Mode newMode = (Mode) options[i];
                        if (newMode != mode) {
                            mode = newMode;
                            EditorSounds.playTabSwitch();
                            if (onModeChange != null) {
                                onModeChange.accept(mode);
                            }
                        }
                    }
                    showDropdown = false;
                    return true;
                }
            }

            // Extra "Set as default" row (mode badge only)
            if (includeDefaultAction) {
                ResponsiveLayout.Rect defaultBounds = new ResponsiveLayout.Rect(
                    bounds.x(), dropdownY + options.length * HEIGHT, WIDTH, HEIGHT
                );
                if (defaultBounds.contains(mouseX, mouseY)) {
                    EditorSounds.playButtonClick();
                    if (onSetDefaultMode != null) {
                        onSetDefaultMode.run();
                    }
                    showDropdown = false;
                    return true;
                }
            }

            // Click outside dropdown closes it
            showDropdown = false;
            return true;
        }

        // Toggle dropdown on badge click
        if (bounds.contains(mouseX, mouseY)) {
            showDropdown = !showDropdown;
            EditorSounds.playButtonClick();
            return true;
        }

        return false;
    }

    /**
     * Close dropdown if clicking outside.
     */
    public void closeDropdown() {
        showDropdown = false;
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTERS & SETTERS
    // ═══════════════════════════════════════════════════════════════

    public BadgeType getBadgeType() {
        return badgeType;
    }

    public Scope getScope() {
        return scope;
    }

    public void setScope(Scope scope) {
        this.scope = scope;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public boolean isClickable() {
        return clickable;
    }

    public boolean isDropdownOpen() {
        return showDropdown;
    }

    public int getWidth() {
        return ScaledCoord.scaleDim(WIDTH);
    }

    public int getHeight() {
        return ScaledCoord.scaleDim(HEIGHT);
    }

    public ResponsiveLayout.Rect getBounds() {
        return bounds;
    }

    /**
     * Toggle between modes (for keyboard shortcut F5).
     */
    public void toggle() {
        if (badgeType == BadgeType.SCOPE) {
            scope = (scope == Scope.GLOBAL) ? Scope.SPECIFIC : Scope.GLOBAL;
            EditorSounds.playTabSwitch();
            if (onScopeChange != null) {
                onScopeChange.accept(scope);
            }
        } else {
            mode = (mode == Mode.PREVIEW) ? Mode.APPLY : Mode.PREVIEW;
            EditorSounds.playTabSwitch();
            if (onModeChange != null) {
                onModeChange.accept(mode);
            }
        }
    }

    /**
     * Check if currently in preview mode.
     */
    public boolean isPreviewMode() {
        return badgeType == BadgeType.MODE && mode == Mode.PREVIEW;
    }

    /**
     * Check if currently in apply mode.
     */
    public boolean isApplyMode() {
        return badgeType == BadgeType.MODE && mode == Mode.APPLY;
    }

    /**
     * Check if currently in global scope.
     */
    public boolean isGlobalScope() {
        return badgeType == BadgeType.SCOPE && scope == Scope.GLOBAL;
    }

    /**
     * Check if currently in specific scope.
     */
    public boolean isSpecificScope() {
        return badgeType == BadgeType.SCOPE && scope == Scope.SPECIFIC;
    }

}
