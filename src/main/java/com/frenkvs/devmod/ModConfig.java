package com.frenkvs.devmod;

public class ModConfig {
    // VISIBILITY
    public static boolean showOverlay = true;       // Show on-screen text
    public static boolean showRender = false;       // Show circles/blocks on ground (default OFF for performance)

    // BODY PART HITBOXES
    // Toggle to show colored hitboxes for body parts (HEAD, ARMS, BODY, LEGS)
    public static boolean showBodyPartBoxes = false; // Default OFF - can be enabled with Shift+G

    // RENDER MODE
    // true = Illuminate blocks (Red grid) - HEAVY!
    // false = Draw simple circle (Line) - lighter
    public static boolean renderAsBlocks = false;

    // COLORS (In ARGB Hex format)
    // 0xFFFF0000 = Red, 0xFFFFFF00 = Yellow, 0xFF00FF00 = Green, 0xFF00FFFF = Cyan
    public static int followRangeColor = 0xFFFF0000; // Default Red

    // Method to cycle colors in the menu
    public static void cycleColor() {
        if (followRangeColor == 0xFFFF0000) followRangeColor = 0xFFFFFF00; // Red -> Yellow
        else if (followRangeColor == 0xFFFFFF00) followRangeColor = 0xFF00FF00; // Yellow -> Green
        else if (followRangeColor == 0xFF00FF00) followRangeColor = 0xFF00FFFF; // Green -> Cyan
        else if (followRangeColor == 0xFF00FFFF) followRangeColor = 0xFF0000FF; // Cyan -> Blue
        else followRangeColor = 0xFFFF0000; // Blue -> Red
    }

    public static String getColorName() {
        if (followRangeColor == 0xFFFF0000) return "Red";
        if (followRangeColor == 0xFFFFFF00) return "Yellow";
        if (followRangeColor == 0xFF00FF00) return "Green";
        if (followRangeColor == 0xFF00FFFF) return "Cyan";
        if (followRangeColor == 0xFF0000FF) return "Blue";
        return "Unknown";
    }
}
