package com.devmod.ui.editor.core;

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
        return 0xE0F5F5F5;
    }

    @Override
    public int panelBackgroundSolid() {
        return 0xFFF5F5F5;
    }

    @Override
    public int inputBackground() {
        return 0xFFFFFFFF;
    }

    @Override
    public int hoverBackground() {
        return 0xFFE8E8E8;
    }

    @Override
    public int activeBackground() {
        return 0xFFDDDDDD;
    }

    @Override
    public int headerBackground() {
        return 0xFFEEEEEE;
    }

    @Override
    public int contentBackground() {
        return 0xFFF0F0F0;
    }

    @Override
    public int tabInactiveBackground() {
        return 0xFFE0E0E0;
    }

    @Override
    public int tabActiveBackground() {
        return 0xFFD0D0D0;
    }

    @Override
    public int overlayBackground() {
        return 0x60000000;
    }

    // =========================================================================
    // BORDER COLORS
    // =========================================================================

    @Override
    public int borderDefault() {
        return 0xFFCCCCCC;
    }

    @Override
    public int borderMuted() {
        return 0xFFDDDDDD;
    }

    @Override
    public int borderAccent() {
        return 0xFF0099CC;
    }

    @Override
    public int borderSeparator() {
        return 0xFFD5D5D5;
    }

    @Override
    public int borderHover() {
        return 0xFFAAAAAA;
    }

    // =========================================================================
    // TEXT COLORS
    // =========================================================================

    @Override
    public int textPrimary() {
        return 0xFF2A2A2A;
    }

    @Override
    public int textSecondary() {
        return 0xFF555555;
    }

    @Override
    public int textMuted() {
        return 0xFF888888;
    }

    @Override
    public int textTitle() {
        return 0xFF000000;
    }

    @Override
    public int textDisabled() {
        return 0xFFAAAAAA;
    }

    // =========================================================================
    // ACCENT COLORS (slightly adjusted for light theme contrast)
    // =========================================================================

    @Override
    public int accentPrimary() {
        return 0xFF0099CC;  // Slightly darker cyan
    }

    @Override
    public int accentSuccess() {
        return 0xFF388E3C;  // Darker green
    }

    @Override
    public int accentWarning() {
        return 0xFFE65100;  // Darker orange
    }

    @Override
    public int accentError() {
        return 0xFFC62828;  // Darker red
    }

    @Override
    public int accentInfo() {
        return 0xFF1565C0;  // Darker blue
    }

    // =========================================================================
    // BUTTON COLORS
    // =========================================================================

    @Override
    public int buttonNormal() {
        return 0xFFE0E0E0;
    }

    @Override
    public int buttonHover() {
        return 0xFFD0D0D0;
    }

    @Override
    public int buttonPressed() {
        return 0xFFC0C0C0;
    }

    @Override
    public int buttonDisabled() {
        return 0xFFEEEEEE;
    }

    // =========================================================================
    // SLIDER COLORS
    // =========================================================================

    @Override
    public int sliderTrack() {
        return 0xFFD0D0D0;
    }

    @Override
    public int sliderThumb() {
        return 0xFF888888;
    }

    @Override
    public int sliderThumbHover() {
        return 0xFF666666;
    }
}
