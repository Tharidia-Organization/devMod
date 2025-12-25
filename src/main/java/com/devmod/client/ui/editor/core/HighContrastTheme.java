package com.devmod.client.ui.editor.core;

/**
 * High-contrast theme for improved accessibility.
 * Uses pure black/white with bright accent colors for maximum visibility.
 *
 * <p>WCAG 2.1 AA/AAA compliant contrast ratios:</p>
 * <ul>
 *   <li>Text on backgrounds: 21:1 (white on black)</li>
 *   <li>Interactive elements: Bright saturated colors</li>
 *   <li>Borders: Visible white/yellow outlines</li>
 * </ul>
 *
 * @see ThemeManager
 * @see <a href="https://www.w3.org/WAI/WCAG21/Understanding/contrast-minimum">WCAG Contrast</a>
 */
public class HighContrastTheme implements Theme {

    public static final HighContrastTheme INSTANCE = new HighContrastTheme();

    private HighContrastTheme() {}

    @Override
    public String getName() {
        return "High Contrast";
    }

    @Override
    public boolean isDark() {
        return true; // Black background = dark theme
    }

    // =========================================================================
    // BACKGROUND COLORS - Pure black for maximum contrast
    // =========================================================================

    @Override
    public int panelBackground() {
        return 0xFF000000; // Pure black
    }

    @Override
    public int panelBackgroundSolid() {
        return 0xFF000000;
    }

    @Override
    public int inputBackground() {
        return 0xFF000000;
    }

    @Override
    public int hoverBackground() {
        return 0xFF1A1A1A; // Slight highlight on hover
    }

    @Override
    public int activeBackground() {
        return 0xFF333333; // More visible when active
    }

    @Override
    public int headerBackground() {
        return 0xFF000000;
    }

    @Override
    public int contentBackground() {
        return 0xFF000000;
    }

    @Override
    public int tabInactiveBackground() {
        return 0xFF000000;
    }

    @Override
    public int tabActiveBackground() {
        return 0xFF1A1A1A;
    }

    @Override
    public int overlayBackground() {
        return 0xE0000000; // Darker backdrop
    }

    @Override
    public int darkerBackground() {
        return 0xFF000000;
    }

    // =========================================================================
    // BORDER COLORS - Bright visible borders
    // =========================================================================

    @Override
    public int borderDefault() {
        return 0xFFFFFFFF; // White borders
    }

    @Override
    public int borderMuted() {
        return 0xFFAAAAAA; // Slightly muted but still visible
    }

    @Override
    public int borderAccent() {
        return 0xFFFFFF00; // Bright yellow for focus
    }

    @Override
    public int borderSeparator() {
        return 0xFFFFFFFF;
    }

    @Override
    public int borderHover() {
        return 0xFFFFFF00; // Yellow on hover
    }

    // =========================================================================
    // TEXT COLORS - Pure white for maximum readability
    // =========================================================================

    @Override
    public int textPrimary() {
        return 0xFFFFFFFF; // Pure white
    }

    @Override
    public int textSecondary() {
        return 0xFFFFFFFF; // White, not gray
    }

    @Override
    public int textMuted() {
        return 0xFFCCCCCC; // Light gray but still readable
    }

    @Override
    public int textTitle() {
        return 0xFFFFFFFF;
    }

    @Override
    public int textDisabled() {
        return 0xFF888888; // Gray for disabled
    }

    @Override
    public int textValue() {
        return 0xFF00FFFF; // Bright cyan for values
    }

    @Override
    public int textFormula() {
        return 0xFF00FF00; // Bright green for code
    }

    // =========================================================================
    // ACCENT COLORS - Brighter and more saturated
    // =========================================================================

    @Override
    public int accentPrimary() {
        return 0xFF00FFFF; // Bright cyan
    }

    @Override
    public int accentSuccess() {
        return 0xFF00FF00; // Bright green
    }

    @Override
    public int accentWarning() {
        return 0xFFFFFF00; // Bright yellow
    }

    @Override
    public int accentError() {
        return 0xFFFF0000; // Bright red
    }

    @Override
    public int accentInfo() {
        return 0xFF00AAFF; // Bright blue
    }

    // =========================================================================
    // BUTTON COLORS - Clear visibility
    // =========================================================================

    @Override
    public int buttonNormal() {
        return 0xFF000000; // Black with white border
    }

    @Override
    public int buttonHover() {
        return 0xFF222222;
    }

    @Override
    public int buttonPressed() {
        return 0xFF444444;
    }

    @Override
    public int buttonDisabled() {
        return 0xFF111111;
    }

    // =========================================================================
    // SLIDER COLORS - High visibility
    // =========================================================================

    @Override
    public int sliderTrack() {
        return 0xFF333333;
    }

    @Override
    public int sliderThumb() {
        return 0xFFFFFFFF; // White thumb
    }

    @Override
    public int sliderThumbHover() {
        return 0xFFFFFF00; // Yellow on hover
    }
}
