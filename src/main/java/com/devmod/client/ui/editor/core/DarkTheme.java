package com.devmod.ui.editor.core;

/**
 * Dark theme implementation matching current UIConstants colors.
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
        return 0xE0181818;
    }

    @Override
    public int panelBackgroundSolid() {
        return 0xFF181818;
    }

    @Override
    public int inputBackground() {
        return 0xFF252525;
    }

    @Override
    public int hoverBackground() {
        return 0xFF353535;
    }

    @Override
    public int activeBackground() {
        return 0xFF454545;
    }

    @Override
    public int headerBackground() {
        return 0xFF1A1A1A;
    }

    @Override
    public int contentBackground() {
        return 0xFF202020;
    }

    @Override
    public int tabInactiveBackground() {
        return 0xFF282828;
    }

    @Override
    public int tabActiveBackground() {
        return 0xFF383838;
    }

    @Override
    public int overlayBackground() {
        return 0x80000000;
    }

    // =========================================================================
    // BORDER COLORS
    // =========================================================================

    @Override
    public int borderDefault() {
        return 0xFF3A3A3A;
    }

    @Override
    public int borderMuted() {
        return 0xFF2A2A2A;
    }

    @Override
    public int borderAccent() {
        return 0xFF00D4FF;
    }

    @Override
    public int borderSeparator() {
        return 0xFF333333;
    }

    @Override
    public int borderHover() {
        return 0xFF5A5A5A;
    }

    // =========================================================================
    // TEXT COLORS
    // =========================================================================

    @Override
    public int textPrimary() {
        return 0xFFE0E0E0;
    }

    @Override
    public int textSecondary() {
        return 0xFFAAAAAA;
    }

    @Override
    public int textMuted() {
        return 0xFF666666;
    }

    @Override
    public int textTitle() {
        return 0xFFFFFFFF;
    }

    @Override
    public int textDisabled() {
        return 0xFF555555;
    }

    // =========================================================================
    // BUTTON COLORS
    // =========================================================================

    @Override
    public int buttonNormal() {
        return 0xFF2A2A2A;
    }

    @Override
    public int buttonHover() {
        return 0xFF3A3A3A;
    }

    @Override
    public int buttonPressed() {
        return 0xFF1A1A1A;
    }

    @Override
    public int buttonDisabled() {
        return 0xFF1A1A1A;
    }

    // =========================================================================
    // SLIDER COLORS
    // =========================================================================

    @Override
    public int sliderTrack() {
        return 0xFF2A2A2A;
    }

    @Override
    public int sliderThumb() {
        return 0xFF5A5A5A;
    }

    @Override
    public int sliderThumbHover() {
        return 0xFF7A7A7A;
    }
}
