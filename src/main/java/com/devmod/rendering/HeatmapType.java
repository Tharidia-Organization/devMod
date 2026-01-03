package com.devmod.rendering;

/**
 * Types of heatmaps supported by the visualization system.
 * Extracted to shared package for use in both client and server-side code.
 */
public enum HeatmapType {
    DEATH(HeatmapColors.DEATH),      // Red
    MOVEMENT(HeatmapColors.MOVEMENT),   // Green
    CAMPING(HeatmapColors.CAMPING),    // Yellow
    STUCK(HeatmapColors.STUCK),      // Orange
    AGGRO_DROP(HeatmapColors.AGGRO_DROP), // Purple
    KITING(HeatmapColors.KITING),     // Cyan
    LIGHT_SPAWNABLE(HeatmapColors.LIGHT_SPAWNABLE), // Red (can spawn)
    LIGHT_DARK(HeatmapColors.LIGHT_DARK); // Orange (dark but not spawnable)

    private final int baseColor;

    HeatmapType(int color) {
        this.baseColor = color;
    }

    public int getBaseColor() {
        return baseColor;
    }
}
