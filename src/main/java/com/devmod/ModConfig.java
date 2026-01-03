package com.devmod;

import com.devmod.util.FollowRangeColors;

public class ModConfig {
    // VISIBILITY
    private static boolean showOverlay = true;       // Show on-screen text
    private static boolean showRender = false;       // Show circles/blocks on ground (default OFF for performance)

    // BODY PART HITBOXES
    // Toggle to show colored hitboxes for body parts (HEAD, ARMS, BODY, LEGS)
    private static boolean showBodyPartBoxes = false; // Default OFF - can be enabled with Shift+G

    // RENDER MODE
    // true = Illuminate blocks (Red grid) - HEAVY!
    // false = Draw simple circle (Line) - lighter
    private static boolean renderAsBlocks = false;

    // COLORS (ARGB)
    // Cycle: Red -> Yellow -> Green -> Cyan -> Blue
    private static int followRangeColor = FollowRangeColors.RED;

    public static boolean isShowOverlay() {
        return showOverlay;
    }

    public static void setShowOverlay(boolean value) {
        showOverlay = value;
    }

    public static boolean isShowRender() {
        return showRender;
    }

    public static void setShowRender(boolean value) {
        showRender = value;
    }

    public static boolean isShowBodyPartBoxes() {
        return showBodyPartBoxes;
    }

    public static void setShowBodyPartBoxes(boolean value) {
        showBodyPartBoxes = value;
    }

    public static boolean isRenderAsBlocks() {
        return renderAsBlocks;
    }

    public static void setRenderAsBlocks(boolean value) {
        renderAsBlocks = value;
    }

    public static int getFollowRangeColor() {
        return followRangeColor;
    }

    public static void setFollowRangeColor(int color) {
        followRangeColor = color;
    }

    // Method to cycle colors in the menu
    public static void cycleColor() {
        followRangeColor = FollowRangeColors.next(followRangeColor);
    }

    public static String getColorName() {
        return FollowRangeColors.nameFor(followRangeColor);
    }
}
