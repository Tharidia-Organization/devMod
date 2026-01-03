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
    default int darkerBackground() { return DesignTokens.EditorTheme.Shared.DARKER_BACKGROUND; }

    /** Value text color (cyan tinted) */
    default int textValue() { return DesignTokens.EditorTheme.Shared.TEXT_VALUE; }

    /** Formula/code text color (green tinted) */
    default int textFormula() { return DesignTokens.EditorTheme.Shared.TEXT_FORMULA; }

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
    default int accentPrimary() { return DesignTokens.EditorTheme.Shared.ACCENT_PRIMARY; }

    /** Success accent (green) */
    default int accentSuccess() { return DesignTokens.EditorTheme.Shared.ACCENT_SUCCESS; }

    /** Warning accent (orange) */
    default int accentWarning() { return DesignTokens.EditorTheme.Shared.ACCENT_WARNING; }

    /** Error accent (red) */
    default int accentError() { return DesignTokens.EditorTheme.Shared.ACCENT_ERROR; }

    /** Info accent (blue) */
    default int accentInfo() { return DesignTokens.EditorTheme.Shared.ACCENT_INFO; }

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
