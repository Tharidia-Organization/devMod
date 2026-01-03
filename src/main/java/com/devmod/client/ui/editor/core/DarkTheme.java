package com.devmod.client.ui.editor.core;

/**
 * Dark theme implementation matching current DesignTokens colors.
 * This is the default theme.
 */
public class DarkTheme implements Theme {

    public static final DarkTheme INSTANCE = new DarkTheme();

    private DarkTheme() {}

    @Override
    public String getName() {
        return "Dark";
    }

    @Override
    public boolean isDark() {
        return true;
    }

    // =========================================================================
    // BACKGROUND COLORS
    // =========================================================================

    @Override
    public int panelBackground() {
        return DesignTokens.EditorTheme.Dark.PANEL_BG;
    }

    @Override
    public int panelBackgroundSolid() {
        return DesignTokens.EditorTheme.Dark.PANEL_BG_SOLID;
    }

    @Override
    public int inputBackground() {
        return DesignTokens.EditorTheme.Dark.INPUT_BG;
    }

    @Override
    public int hoverBackground() {
        return DesignTokens.EditorTheme.Dark.HOVER_BG;
    }

    @Override
    public int activeBackground() {
        return DesignTokens.EditorTheme.Dark.ACTIVE_BG;
    }

    @Override
    public int headerBackground() {
        return DesignTokens.EditorTheme.Dark.HEADER_BG;
    }

    @Override
    public int contentBackground() {
        return DesignTokens.EditorTheme.Dark.CONTENT_BG;
    }

    @Override
    public int tabInactiveBackground() {
        return DesignTokens.EditorTheme.Dark.TAB_INACTIVE_BG;
    }

    @Override
    public int tabActiveBackground() {
        return DesignTokens.EditorTheme.Dark.TAB_ACTIVE_BG;
    }

    @Override
    public int overlayBackground() {
        return DesignTokens.EditorTheme.Dark.OVERLAY_BG;
    }

    // =========================================================================
    // BORDER COLORS
    // =========================================================================

    @Override
    public int borderDefault() {
        return DesignTokens.EditorTheme.Dark.BORDER_DEFAULT;
    }

    @Override
    public int borderMuted() {
        return DesignTokens.EditorTheme.Dark.BORDER_MUTED;
    }

    @Override
    public int borderAccent() {
        return DesignTokens.EditorTheme.Dark.BORDER_ACCENT;
    }

    @Override
    public int borderSeparator() {
        return DesignTokens.EditorTheme.Dark.BORDER_SEPARATOR;
    }

    @Override
    public int borderHover() {
        return DesignTokens.EditorTheme.Dark.BORDER_HOVER;
    }

    // =========================================================================
    // TEXT COLORS
    // =========================================================================

    @Override
    public int textPrimary() {
        return DesignTokens.EditorTheme.Dark.TEXT_PRIMARY;
    }

    @Override
    public int textSecondary() {
        return DesignTokens.EditorTheme.Dark.TEXT_SECONDARY;
    }

    @Override
    public int textMuted() {
        return DesignTokens.EditorTheme.Dark.TEXT_MUTED;
    }

    @Override
    public int textTitle() {
        return DesignTokens.EditorTheme.Dark.TEXT_TITLE;
    }

    @Override
    public int textDisabled() {
        return DesignTokens.EditorTheme.Dark.TEXT_DISABLED;
    }

    // =========================================================================
    // BUTTON COLORS
    // =========================================================================

    @Override
    public int buttonNormal() {
        return DesignTokens.EditorTheme.Dark.BUTTON_NORMAL;
    }

    @Override
    public int buttonHover() {
        return DesignTokens.EditorTheme.Dark.BUTTON_HOVER;
    }

    @Override
    public int buttonPressed() {
        return DesignTokens.EditorTheme.Dark.BUTTON_PRESSED;
    }

    @Override
    public int buttonDisabled() {
        return DesignTokens.EditorTheme.Dark.BUTTON_DISABLED;
    }

    // =========================================================================
    // SLIDER COLORS
    // =========================================================================

    @Override
    public int sliderTrack() {
        return DesignTokens.EditorTheme.Dark.SLIDER_TRACK;
    }

    @Override
    public int sliderThumb() {
        return DesignTokens.EditorTheme.Dark.SLIDER_THUMB;
    }

    @Override
    public int sliderThumbHover() {
        return DesignTokens.EditorTheme.Dark.SLIDER_THUMB_HOVER;
    }
}
