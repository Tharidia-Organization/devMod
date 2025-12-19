package com.frenkvs.devmod.ui.editor.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Singleton manager for editor UI themes.
 * Handles theme switching and notifies listeners of changes.
 *
 * Usage:
 * <pre>
 * // Get current theme
 * Theme theme = ThemeManager.INSTANCE.current();
 * int bg = theme.panelBackground();
 *
 * // Switch theme
 * ThemeManager.INSTANCE.setTheme(LightTheme.INSTANCE);
 *
 * // Toggle between dark/light
 * ThemeManager.INSTANCE.toggle();
 *
 * // Listen for changes
 * ThemeManager.INSTANCE.addListener(theme -> updateColors());
 * </pre>
 *
 * @see Theme
 * @see DarkTheme
 * @see LightTheme
 */
public final class ThemeManager {

    public static final ThemeManager INSTANCE = new ThemeManager();

    private Theme currentTheme = DarkTheme.INSTANCE;
    private final List<ThemeChangeListener> listeners = new ArrayList<>();

    private ThemeManager() {}

    /**
     * Get the current theme.
     */
    public Theme current() {
        return currentTheme;
    }

    /**
     * Set the current theme.
     *
     * @param theme The theme to use
     */
    public void setTheme(Theme theme) {
        if (theme == null || theme == currentTheme) return;

        Theme oldTheme = currentTheme;
        currentTheme = theme;

        // Notify listeners
        for (ThemeChangeListener listener : listeners) {
            listener.onThemeChanged(theme, oldTheme);
        }
    }

    /**
     * Toggle between dark and light themes.
     */
    public void toggle() {
        if (currentTheme.isDark()) {
            setTheme(LightTheme.INSTANCE);
        } else {
            setTheme(DarkTheme.INSTANCE);
        }
    }

    /**
     * Check if current theme is dark.
     */
    public boolean isDark() {
        return currentTheme.isDark();
    }

    /**
     * Add a listener for theme changes.
     *
     * @param listener The listener to add
     */
    public void addListener(ThemeChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Remove a theme change listener.
     *
     * @param listener The listener to remove
     */
    public void removeListener(ThemeChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * Clear all listeners.
     */
    public void clearListeners() {
        listeners.clear();
    }

    // =========================================================================
    // CONVENIENCE ACCESSORS
    // =========================================================================

    // Background colors
    public int panelBg() { return currentTheme.panelBackground(); }
    public int panelBgSolid() { return currentTheme.panelBackgroundSolid(); }
    public int inputBg() { return currentTheme.inputBackground(); }
    public int hoverBg() { return currentTheme.hoverBackground(); }
    public int activeBg() { return currentTheme.activeBackground(); }
    public int headerBg() { return currentTheme.headerBackground(); }
    public int contentBg() { return currentTheme.contentBackground(); }
    public int overlayBg() { return currentTheme.overlayBackground(); }

    // Border colors
    public int border() { return currentTheme.borderDefault(); }
    public int borderMuted() { return currentTheme.borderMuted(); }
    public int borderAccent() { return currentTheme.borderAccent(); }
    public int separator() { return currentTheme.borderSeparator(); }

    // Text colors
    public int textPrimary() { return currentTheme.textPrimary(); }
    public int textSecondary() { return currentTheme.textSecondary(); }
    public int textMuted() { return currentTheme.textMuted(); }
    public int textTitle() { return currentTheme.textTitle(); }
    public int textDisabled() { return currentTheme.textDisabled(); }

    // Accent colors
    public int accent() { return currentTheme.accentPrimary(); }
    public int success() { return currentTheme.accentSuccess(); }
    public int warning() { return currentTheme.accentWarning(); }
    public int error() { return currentTheme.accentError(); }
    public int info() { return currentTheme.accentInfo(); }

    // Button colors
    public int btnNormal() { return currentTheme.buttonNormal(); }
    public int btnHover() { return currentTheme.buttonHover(); }
    public int btnPressed() { return currentTheme.buttonPressed(); }
    public int btnDisabled() { return currentTheme.buttonDisabled(); }

    // Slider colors
    public int sliderTrack() { return currentTheme.sliderTrack(); }
    public int sliderThumb() { return currentTheme.sliderThumb(); }
    public int sliderThumbHover() { return currentTheme.sliderThumbHover(); }

    /**
     * Listener interface for theme changes.
     */
    @FunctionalInterface
    public interface ThemeChangeListener {
        /**
         * Called when the theme changes.
         *
         * @param newTheme The new theme
         * @param oldTheme The previous theme
         */
        void onThemeChanged(Theme newTheme, Theme oldTheme);
    }
}
