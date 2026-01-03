package com.devmod.util;

import com.devmod.shared.SharedColorTokens;

/**
 * Shared palette for follow range visuals.
 */
public final class FollowRangeColors {
    public static final int RED = SharedColorTokens.FollowRange.RED;
    public static final int YELLOW = SharedColorTokens.FollowRange.YELLOW;
    public static final int GREEN = SharedColorTokens.FollowRange.GREEN;
    public static final int CYAN = SharedColorTokens.FollowRange.CYAN;
    public static final int BLUE = SharedColorTokens.FollowRange.BLUE;

    private static final int[] CYCLE = {RED, YELLOW, GREEN, CYAN, BLUE};

    private FollowRangeColors() {}

    public static int next(int current) {
        for (int i = 0; i < CYCLE.length; i++) {
            if (CYCLE[i] == current) {
                return CYCLE[(i + 1) % CYCLE.length];
            }
        }
        return RED;
    }

    public static String nameFor(int color) {
        if (color == RED) return "Red";
        if (color == YELLOW) return "Yellow";
        if (color == GREEN) return "Green";
        if (color == CYAN) return "Cyan";
        if (color == BLUE) return "Blue";
        return "Unknown";
    }
}
