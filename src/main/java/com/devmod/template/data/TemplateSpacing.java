package com.devmod.template.data;

/**
 * Standard spacing constants for room templates.
 * All templates should use these values for consistent appearance.
 */
public final class TemplateSpacing {
    private TemplateSpacing() {}

    // Component sizes
    public static final int PAD_SIZE = 3;
    public static final int CONSOLE_WIDTH = 3;
    public static final int CONSOLE_DEPTH = 2;
    public static final int RACK_WIDTH = 2;
    public static final int PLINTH_SIZE = 3;
    public static final int POD_SIZE = 5;

    // Spacing
    public static final int BENCH_SPACING = 2;
    public static final int WALL_MARGIN = 2;
    public static final int CENTER_CLEAR = 5;
    public static final int COMPONENT_GAP = 2;

    // Heights
    public static final int STANDARD_HEIGHT = 4;
    public static final int TALL_HEIGHT = 6;
    public static final int SCREEN_HEIGHT = 3;
    public static final int PILLAR_HEIGHT = 4;

    // Room size thresholds
    public static final int SMALL_ROOM = 20;
    public static final int MEDIUM_ROOM = 40;
    public static final int LARGE_ROOM = 60;

    /**
     * Get scale factor based on room size.
     */
    public static float getScaleFactor(int roomSize) {
        if (roomSize < SMALL_ROOM) return 0.5f;
        if (roomSize < MEDIUM_ROOM) return 1.0f;
        if (roomSize < LARGE_ROOM) return 1.5f;
        return 2.0f;
    }

    /**
     * Scale a count based on room size.
     */
    public static int scaleCount(int baseCount, int roomSize) {
        float factor = getScaleFactor(roomSize);
        return Math.max(1, Math.round(baseCount * factor));
    }

    /**
     * Check if a component fits at the given position.
     */
    public static boolean fitsWithMargin(int posX, int posZ, int sizeX, int sizeZ,
                                         int minX, int minZ, int maxX, int maxZ) {
        return posX - sizeX / 2 >= minX + WALL_MARGIN &&
               posX + sizeX / 2 <= maxX - WALL_MARGIN &&
               posZ - sizeZ / 2 >= minZ + WALL_MARGIN &&
               posZ + sizeZ / 2 <= maxZ - WALL_MARGIN;
    }
}
