package com.devmod.rendering;

/**
 * Types of heatmaps supported by the visualization system.
 * Extracted to shared package for use in both client and server-side code.
 */
public enum HeatmapType {
    DEATH(0xFFFF0000),      // Red
    MOVEMENT(0xFF00FF00),   // Green
    CAMPING(0xFFFFFF00),    // Yellow
    STUCK(0xFFFF8000),      // Orange
    AGGRO_DROP(0xFF8000FF), // Purple
    KITING(0xFF00FFFF),     // Cyan
    LIGHT_SPAWNABLE(0xFFFF0000), // Red (can spawn)
    LIGHT_DARK(0xFFFF8800); // Orange (dark but not spawnable)

    private final int baseColor;

    HeatmapType(int color) {
        this.baseColor = color;
    }

    public int getBaseColor() {
        return baseColor;
    }
}
