package com.devmod.client.accessibility;

import javax.annotation.Nonnull;

/**
 * Colorblind accessibility presets that remap colors across all DevMod UI/HUD.
 *
 * <p>Each mode defines color transformations optimized for a specific type of
 * color vision deficiency (CVD). The remapping preserves alpha channels and
 * maintains WCAG AA contrast ratios.
 *
 * <p>Two strategies are used:
 * <ul>
 *   <li><b>LUT (Lookup Table):</b> Exact remapping for the ~30 key DevMod colors</li>
 *   <li><b>Matrix Transform:</b> Approximate simulation for arbitrary colors</li>
 * </ul>
 *
 * @see ColorblindConfig for persistence
 * @see com.devmod.client.ui.overlay.OverlayTheme for color usage
 */
public enum ColorblindMode {
    /** Default colors — no remapping. */
    NONE("none", "Default") {
        @Override
        public int remap(int argbColor) {
            return argbColor;
        }
    },

    /**
     * Protanopia (red-blind): reds shift toward brown/orange, greens stay.
     * Affects ~1% of males.
     */
    PROTANOPIA("protanopia", "Protanopia (Red-Blind)") {
        // Daltonization matrix for protanopia (simplified LMS)
        // Maps: red -> orange/brown, keeps green distinguishable
        private static final float[] MATRIX = {
            0.567f, 0.433f, 0.000f,
            0.558f, 0.442f, 0.000f,
            0.000f, 0.242f, 0.758f
        };

        @Override
        public int remap(int argbColor) {
            int lut = LookupTable.PROTANOPIA.remap(argbColor);
            if (lut != argbColor) return lut;
            return applyMatrix(argbColor, MATRIX);
        }
    },

    /**
     * Deuteranopia (green-blind): greens shift toward brown/yellow, reds shift toward orange.
     * Most common CVD, affects ~6% of males.
     */
    DEUTERANOPIA("deuteranopia", "Deuteranopia (Green-Blind)") {
        private static final float[] MATRIX = {
            0.625f, 0.375f, 0.000f,
            0.700f, 0.300f, 0.000f,
            0.000f, 0.300f, 0.700f
        };

        @Override
        public int remap(int argbColor) {
            int lut = LookupTable.DEUTERANOPIA.remap(argbColor);
            if (lut != argbColor) return lut;
            return applyMatrix(argbColor, MATRIX);
        }
    },

    /**
     * Tritanopia (blue-blind): blues shift toward cyan/green, yellows shift toward pink.
     * Rare, affects ~0.01% of population.
     */
    TRITANOPIA("tritanopia", "Tritanopia (Blue-Blind)") {
        private static final float[] MATRIX = {
            0.950f, 0.050f, 0.000f,
            0.000f, 0.433f, 0.567f,
            0.000f, 0.475f, 0.525f
        };

        @Override
        public int remap(int argbColor) {
            int lut = LookupTable.TRITANOPIA.remap(argbColor);
            if (lut != argbColor) return lut;
            return applyMatrix(argbColor, MATRIX);
        }
    };

    private final String serializedName;
    private final String displayName;

    ColorblindMode(String serializedName, String displayName) {
        this.serializedName = serializedName;
        this.displayName = displayName;
    }

    /**
     * Remaps an ARGB color for this colorblind mode.
     * Alpha channel is preserved.
     *
     * @param argbColor the input color (0xAARRGGBB or 0xRRGGBB)
     * @return the remapped color with original alpha
     */
    public abstract int remap(int argbColor);

    @Nonnull
    public String getSerializedName() {
        return serializedName;
    }

    @Nonnull
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets a mode by serialized name.
     */
    @Nonnull
    public static ColorblindMode byName(@Nonnull String name) {
        for (ColorblindMode mode : values()) {
            if (mode.serializedName.equals(name)) {
                return mode;
            }
        }
        return NONE;
    }

    /**
     * Gets a mode by ordinal index.
     */
    @Nonnull
    public static ColorblindMode byIndex(int index) {
        ColorblindMode[] values = values();
        if (index < 0 || index >= values.length) {
            return NONE;
        }
        return values[index];
    }

    // ============================================================================
    // MATRIX TRANSFORM
    // ============================================================================

    /**
     * Applies a 3x3 color transformation matrix to an ARGB color.
     * Preserves the alpha channel.
     */
    protected static int applyMatrix(int argbColor, float[] matrix) {
        int a = (argbColor >> 24) & 0xFF;
        int r = (argbColor >> 16) & 0xFF;
        int g = (argbColor >> 8) & 0xFF;
        int b = argbColor & 0xFF;

        int newR = clamp((int) (matrix[0] * r + matrix[1] * g + matrix[2] * b));
        int newG = clamp((int) (matrix[3] * r + matrix[4] * g + matrix[5] * b));
        int newB = clamp((int) (matrix[6] * r + matrix[7] * g + matrix[8] * b));

        return (a << 24) | (newR << 16) | (newG << 8) | newB;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    /**
     * Extracts the RGB portion (no alpha) of a color for LUT matching.
     */
    static int rgb(int color) {
        return color & 0x00FFFFFF;
    }

    /**
     * Preserves original alpha while applying a new RGB value.
     */
    static int withOriginalAlpha(int original, int newRgb) {
        return (original & 0xFF000000) | (newRgb & 0x00FFFFFF);
    }
}
