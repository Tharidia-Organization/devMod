package com.devmod.arena.zone;

/**
 * Defines how zones transition into each other at their boundaries.
 */
public record ZoneTransition(
    TransitionType type,
    int width,
    boolean blendMaterials,
    boolean blendLighting
) {
    /** Hard boundary with no transition */
    public static final ZoneTransition HARD = new ZoneTransition(TransitionType.HARD, 0, false, false);

    /** Gradual 3-block gradient transition */
    public static final ZoneTransition GRADIENT_SMALL = new ZoneTransition(TransitionType.GRADIENT, 3, true, true);

    /** Medium 5-block gradient transition */
    public static final ZoneTransition GRADIENT_MEDIUM = new ZoneTransition(TransitionType.GRADIENT, 5, true, true);

    /** Large 8-block gradient transition */
    public static final ZoneTransition GRADIENT_LARGE = new ZoneTransition(TransitionType.GRADIENT, 8, true, true);

    /** Wall barrier between zones */
    public static final ZoneTransition WALL = new ZoneTransition(TransitionType.WALL, 1, false, false);

    /**
     * Creates a custom gradient transition.
     */
    public static ZoneTransition gradient(int width) {
        return new ZoneTransition(TransitionType.GRADIENT, width, true, true);
    }

    /**
     * Creates a wall transition with custom width.
     */
    public static ZoneTransition wall(int width) {
        return new ZoneTransition(TransitionType.WALL, width, false, false);
    }

    /**
     * Transition types between zones.
     */
    public enum TransitionType {
        /** No transition - zones meet at a hard boundary */
        HARD,
        /** Gradual blend between zone materials/lighting */
        GRADIENT,
        /** Physical wall barrier between zones */
        WALL,
        /** Gap/void between zones */
        GAP
    }

    /**
     * Returns true if this transition requires extra space between zones.
     */
    public boolean requiresSpace() {
        return type == TransitionType.WALL || type == TransitionType.GAP || width > 0;
    }

    /**
     * Returns the total space needed for this transition.
     */
    public int getRequiredSpace() {
        return switch (type) {
            case HARD -> 0;
            case GRADIENT -> width;
            case WALL -> width;
            case GAP -> width;
        };
    }
}
