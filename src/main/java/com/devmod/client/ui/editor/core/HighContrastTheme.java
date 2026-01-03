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
        return DesignTokens.EditorTheme.HighContrast.PANEL_BG;
    }

    @Override
    public int panelBackgroundSolid() {
        return DesignTokens.EditorTheme.HighContrast.PANEL_BG_SOLID;
    }

    @Override
    public int inputBackground() {
        return DesignTokens.EditorTheme.HighContrast.INPUT_BG;
    }

    @Override
    public int hoverBackground() {
        return DesignTokens.EditorTheme.HighContrast.HOVER_BG;
    }

    @Override
    public int activeBackground() {
        return DesignTokens.EditorTheme.HighContrast.ACTIVE_BG;
    }

    @Override
    public int headerBackground() {
        return DesignTokens.EditorTheme.HighContrast.HEADER_BG;
    }

    @Override
    public int contentBackground() {
        return DesignTokens.EditorTheme.HighContrast.CONTENT_BG;
    }

    @Override
    public int tabInactiveBackground() {
        return DesignTokens.EditorTheme.HighContrast.TAB_INACTIVE_BG;
    }

    @Override
    public int tabActiveBackground() {
        return DesignTokens.EditorTheme.HighContrast.TAB_ACTIVE_BG;
    }

    @Override
    public int overlayBackground() {
        return DesignTokens.EditorTheme.HighContrast.OVERLAY_BG;
    }

    @Override
    public int darkerBackground() {
        return DesignTokens.EditorTheme.HighContrast.DARKER_BACKGROUND;
    }

    // =========================================================================
    // BORDER COLORS - Bright visible borders
    // =========================================================================

    @Override
    public int borderDefault() {
        return DesignTokens.EditorTheme.HighContrast.BORDER_DEFAULT;
    }

    @Override
    public int borderMuted() {
        return DesignTokens.EditorTheme.HighContrast.BORDER_MUTED;
    }

    @Override
    public int borderAccent() {
        return DesignTokens.EditorTheme.HighContrast.BORDER_ACCENT;
    }

    @Override
    public int borderSeparator() {
        return DesignTokens.EditorTheme.HighContrast.BORDER_SEPARATOR;
    }

    @Override
    public int borderHover() {
        return DesignTokens.EditorTheme.HighContrast.BORDER_HOVER;
    }

    // =========================================================================
    // TEXT COLORS - Pure white for maximum readability
    // =========================================================================

    @Override
    public int textPrimary() {
        return DesignTokens.EditorTheme.HighContrast.TEXT_PRIMARY;
    }

    @Override
    public int textSecondary() {
        return DesignTokens.EditorTheme.HighContrast.TEXT_SECONDARY;
    }

    @Override
    public int textMuted() {
        return DesignTokens.EditorTheme.HighContrast.TEXT_MUTED;
    }

    @Override
    public int textTitle() {
        return DesignTokens.EditorTheme.HighContrast.TEXT_TITLE;
    }

    @Override
    public int textDisabled() {
        return DesignTokens.EditorTheme.HighContrast.TEXT_DISABLED;
    }

    @Override
    public int textValue() {
        return DesignTokens.EditorTheme.HighContrast.TEXT_VALUE;
    }

    @Override
    public int textFormula() {
        return DesignTokens.EditorTheme.HighContrast.TEXT_FORMULA;
    }

    // =========================================================================
    // ACCENT COLORS - Brighter and more saturated
    // =========================================================================

    @Override
    public int accentPrimary() {
        return DesignTokens.EditorTheme.HighContrast.ACCENT_PRIMARY;
    }

    @Override
    public int accentSuccess() {
        return DesignTokens.EditorTheme.HighContrast.ACCENT_SUCCESS;
    }

    @Override
    public int accentWarning() {
        return DesignTokens.EditorTheme.HighContrast.ACCENT_WARNING;
    }

    @Override
    public int accentError() {
        return DesignTokens.EditorTheme.HighContrast.ACCENT_ERROR;
    }

    @Override
    public int accentInfo() {
        return DesignTokens.EditorTheme.HighContrast.ACCENT_INFO;
    }

    // =========================================================================
    // BUTTON COLORS - Clear visibility
    // =========================================================================

    @Override
    public int buttonNormal() {
        return DesignTokens.EditorTheme.HighContrast.BUTTON_NORMAL;
    }

    @Override
    public int buttonHover() {
        return DesignTokens.EditorTheme.HighContrast.BUTTON_HOVER;
    }

    @Override
    public int buttonPressed() {
        return DesignTokens.EditorTheme.HighContrast.BUTTON_PRESSED;
    }

    @Override
    public int buttonDisabled() {
        return DesignTokens.EditorTheme.HighContrast.BUTTON_DISABLED;
    }

    // =========================================================================
    // SLIDER COLORS - High visibility
    // =========================================================================

    @Override
    public int sliderTrack() {
        return DesignTokens.EditorTheme.HighContrast.SLIDER_TRACK;
    }

    @Override
    public int sliderThumb() {
        return DesignTokens.EditorTheme.HighContrast.SLIDER_THUMB;
    }

    @Override
    public int sliderThumbHover() {
        return DesignTokens.EditorTheme.HighContrast.SLIDER_THUMB_HOVER;
    }
}
