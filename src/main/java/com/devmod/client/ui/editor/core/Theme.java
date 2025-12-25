package com.devmod.client.ui.editor.core;

/**
 * Interface defining a color theme for the editor UI.
 * Implementations provide concrete color values for dark/light themes.
 *
 * @see ThemeManager
 * @see DarkTheme
 * @see LightTheme
 */
public interface Theme {

    /**
     * Get the theme name for display.
     */
    String getName();

    /**
     * Check if this is a dark theme.
     */
    boolean isDark();

    // =========================================================================
    // BACKGROUND COLORS
    // =========================================================================

    /** Main panel background */
    int panelBackground();

    /** Solid panel background for dialogs */
    int panelBackgroundSolid();

    /** Input field background */
    int inputBackground();

    /** Hover state background */
    int hoverBackground();

    /** Active/pressed background */
    int activeBackground();

    /** Header/footer background */
    int headerBackground();

    /** Content area background */
    int contentBackground();

    /** Tab inactive background */
    int tabInactiveBackground();

    /** Tab active background */
    int tabActiveBackground();

    /** Overlay backdrop */
    int overlayBackground();

    /** Darker areas (scrollbar track, deep separators) */
    default int darkerBackground() { return 0xFF0D0D0D; }

    /** Value text color (cyan tinted) */
    default int textValue() { return 0xFFB0E0E6; }

    /** Formula/code text color (green tinted) */
    default int textFormula() { return 0xFF98D4A4; }

    // =========================================================================
    // BORDER COLORS
    // =========================================================================

    /** Default border */
    int borderDefault();

    /** Muted/subtle border */
    int borderMuted();

    /** Accent/focused border */
    int borderAccent();

    /** Separator line */
    int borderSeparator();

    /** Hover border */
    int borderHover();

    // =========================================================================
    // TEXT COLORS
    // =========================================================================

    /** Primary text */
    int textPrimary();

    /** Secondary text */
    int textSecondary();

    /** Muted text */
    int textMuted();

    /** Title text */
    int textTitle();

    /** Disabled text */
    int textDisabled();

    // =========================================================================
    // ACCENT COLORS (usually same across themes)
    // =========================================================================

    /** Primary accent (cyan) */
    default int accentPrimary() { return 0xFF00D4FF; }

    /** Success accent (green) */
    default int accentSuccess() { return 0xFF4CAF50; }

    /** Warning accent (orange) */
    default int accentWarning() { return 0xFFFF9800; }

    /** Error accent (red) */
    default int accentError() { return 0xFFE53935; }

    /** Info accent (blue) */
    default int accentInfo() { return 0xFF2196F3; }

    // =========================================================================
    // BUTTON COLORS
    // =========================================================================

    /** Button normal background */
    int buttonNormal();

    /** Button hover background */
    int buttonHover();

    /** Button pressed background */
    int buttonPressed();

    /** Button disabled background */
    int buttonDisabled();

    // =========================================================================
    // SLIDER COLORS
    // =========================================================================

    /** Slider track background */
    int sliderTrack();

    /** Slider thumb */
    int sliderThumb();

    /** Slider thumb hover */
    int sliderThumbHover();
}
