package com.devmod.client.accessibility;

import javax.annotation.Nonnull;

/**
 * Central access point for colorblind color remapping.
 *
 * <p>All overlay/HUD rendering code should call {@link #getColor(int)} to
 * apply the active colorblind mode. This is a no-op when mode is NONE.
 *
 * <p>Usage:
 * <pre>{@code
 * // Instead of using a raw color:
 * int healthColor = 0xFFFF0000;
 * // Use:
 * int healthColor = ColorblindHelper.getColor(0xFFFF0000);
 * }</pre>
 *
 * @see ColorblindMode
 * @see ColorblindConfig
 */
public final class ColorblindHelper {

    private static ColorblindMode cachedMode = ColorblindMode.NONE;

    private ColorblindHelper() {}

    /**
     * Returns the active colorblind mode, reading from config if loaded.
     */
    @Nonnull
    public static ColorblindMode getActiveMode() {
        try {
            if (ColorblindConfig.SPEC.isLoaded()) {
                cachedMode = ColorblindConfig.COLORBLIND_MODE.get();
            }
        } catch (Exception ignored) {
            // Config not yet loaded; use cached/default
        }
        return cachedMode;
    }

    /**
     * Remaps a color through the active colorblind mode.
     *
     * @param argbColor the input ARGB color
     * @return the remapped color (unchanged if mode is NONE)
     */
    public static int getColor(int argbColor) {
        ColorblindMode mode = getActiveMode();
        if (mode == ColorblindMode.NONE) {
            return argbColor;
        }
        return mode.remap(argbColor);
    }

    /**
     * Sets the active mode directly (for settings UI live preview).
     * The mode will persist when saved through {@link ColorblindConfig}.
     */
    public static void setActiveMode(@Nonnull ColorblindMode mode) {
        cachedMode = mode;
        try {
            if (ColorblindConfig.SPEC.isLoaded()) {
                ColorblindConfig.COLORBLIND_MODE.set(mode);
            }
        } catch (Exception ignored) {
            // Config not writable yet
        }
    }

    /**
     * Cycles to the next colorblind mode. Useful for keybind toggle.
     *
     * @return the new active mode
     */
    @Nonnull
    public static ColorblindMode cycleMode() {
        ColorblindMode current = getActiveMode();
        ColorblindMode[] values = ColorblindMode.values();
        ColorblindMode next = values[(current.ordinal() + 1) % values.length];
        setActiveMode(next);
        return next;
    }
}
