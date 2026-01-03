package com.devmod.client.ui.editor.core;

/**
 * Light theme implementation with inverted colors.
 * Provides a light mode alternative for the editor UI.
 */
public class LightTheme implements Theme {

    public static final LightTheme INSTANCE = new LightTheme();

    private LightTheme() {}

    @Override
    public String getName() {
        return "Light";
    }

    @Override
    public boolean isDark() {
        return false;
    }

    // =========================================================================
    // BACKGROUND COLORS
    // =========================================================================

    @Override
    public int panelBackground() {
        return DesignTokens.EditorTheme.Light.PANEL_BG;
    }

    @Override
    public int panelBackgroundSolid() {
        return DesignTokens.EditorTheme.Light.PANEL_BG_SOLID;
    }

    @Override
    public int inputBackground() {
        return DesignTokens.EditorTheme.Light.INPUT_BG;
    }

    @Override
    public int hoverBackground() {
        return DesignTokens.EditorTheme.Light.HOVER_BG;
    }

    @Override
    public int activeBackground() {
        return DesignTokens.EditorTheme.Light.ACTIVE_BG;
    }

    @Override
    public int headerBackground() {
        return DesignTokens.EditorTheme.Light.HEADER_BG;
    }

    @Override
    public int contentBackground() {
        return DesignTokens.EditorTheme.Light.CONTENT_BG;
    }

    @Override
    public int tabInactiveBackground() {
        return DesignTokens.EditorTheme.Light.TAB_INACTIVE_BG;
    }

    @Override
    public int tabActiveBackground() {
        return DesignTokens.EditorTheme.Light.TAB_ACTIVE_BG;
    }

    @Override
    public int overlayBackground() {
        return DesignTokens.EditorTheme.Light.OVERLAY_BG;
    }

    // =========================================================================
    // BORDER COLORS
    // =========================================================================

    @Override
    public int borderDefault() {
        return DesignTokens.EditorTheme.Light.BORDER_DEFAULT;
    }

    @Override
    public int borderMuted() {
        return DesignTokens.EditorTheme.Light.BORDER_MUTED;
    }

    @Override
    public int borderAccent() {
        return DesignTokens.EditorTheme.Light.BORDER_ACCENT;
    }

    @Override
    public int borderSeparator() {
        return DesignTokens.EditorTheme.Light.BORDER_SEPARATOR;
    }

    @Override
    public int borderHover() {
        return DesignTokens.EditorTheme.Light.BORDER_HOVER;
    }

    // =========================================================================
    // TEXT COLORS
    // =========================================================================

    @Override
    public int textPrimary() {
        return DesignTokens.EditorTheme.Light.TEXT_PRIMARY;
    }

    @Override
    public int textSecondary() {
        return DesignTokens.EditorTheme.Light.TEXT_SECONDARY;
    }

    @Override
    public int textMuted() {
        return DesignTokens.EditorTheme.Light.TEXT_MUTED;
    }

    @Override
    public int textTitle() {
        return DesignTokens.EditorTheme.Light.TEXT_TITLE;
    }

    @Override
    public int textDisabled() {
        return DesignTokens.EditorTheme.Light.TEXT_DISABLED;
    }

    // =========================================================================
    // ACCENT COLORS (slightly adjusted for light theme contrast)
    // =========================================================================

    @Override
    public int accentPrimary() {
        return DesignTokens.EditorTheme.Light.ACCENT_PRIMARY;
    }

    @Override
    public int accentSuccess() {
        return DesignTokens.EditorTheme.Light.ACCENT_SUCCESS;
    }

    @Override
    public int accentWarning() {
        return DesignTokens.EditorTheme.Light.ACCENT_WARNING;
    }

    @Override
    public int accentError() {
        return DesignTokens.EditorTheme.Light.ACCENT_ERROR;
    }

    @Override
    public int accentInfo() {
        return DesignTokens.EditorTheme.Light.ACCENT_INFO;
    }

    // =========================================================================
    // BUTTON COLORS
    // =========================================================================

    @Override
    public int buttonNormal() {
        return DesignTokens.EditorTheme.Light.BUTTON_NORMAL;
    }

    @Override
    public int buttonHover() {
        return DesignTokens.EditorTheme.Light.BUTTON_HOVER;
    }

    @Override
    public int buttonPressed() {
        return DesignTokens.EditorTheme.Light.BUTTON_PRESSED;
    }

    @Override
    public int buttonDisabled() {
        return DesignTokens.EditorTheme.Light.BUTTON_DISABLED;
    }

    // =========================================================================
    // SLIDER COLORS
    // =========================================================================

    @Override
    public int sliderTrack() {
        return DesignTokens.EditorTheme.Light.SLIDER_TRACK;
    }

    @Override
    public int sliderThumb() {
        return DesignTokens.EditorTheme.Light.SLIDER_THUMB;
    }

    @Override
    public int sliderThumbHover() {
        return DesignTokens.EditorTheme.Light.SLIDER_THUMB_HOVER;
    }
}
