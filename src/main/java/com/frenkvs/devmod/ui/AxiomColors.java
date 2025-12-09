package com.frenkvs.devmod.ui;

/**
 * Color palette inspired by modern UI design (Axiom-style dark theme)
 */
public final class AxiomColors {

    // Background colors
    public static final int BG_DARK = 0xE0181818;        // Main dark background
    public static final int BG_PANEL = 0xF0202020;       // Panel background
    public static final int BG_HEADER = 0xFF282828;      // Header/title bar
    public static final int BG_INPUT = 0xFF1A1A1A;       // Input field background
    public static final int BG_HOVER = 0xFF2D2D2D;       // Hover state
    public static final int BG_ACTIVE = 0xFF3D3D3D;      // Active/pressed state

    // Border colors
    public static final int BORDER = 0xFF3A3A3A;         // Default border
    public static final int BORDER_LIGHT = 0xFF4A4A4A;   // Light border
    public static final int BORDER_ACCENT = 0xFF5C8AE6; // Accent border (blue)

    // Text colors
    public static final int TEXT_PRIMARY = 0xFFE0E0E0;   // Primary text
    public static final int TEXT_SECONDARY = 0xFF909090; // Secondary text
    public static final int TEXT_MUTED = 0xFF606060;     // Muted text
    public static final int TEXT_ACCENT = 0xFF7CA8F0;    // Accent text (blue)

    // Accent colors
    public static final int ACCENT_BLUE = 0xFF5C8AE6;    // Primary accent
    public static final int ACCENT_GREEN = 0xFF4CAF50;   // Success/positive
    public static final int ACCENT_RED = 0xFFE53935;     // Error/negative
    public static final int ACCENT_YELLOW = 0xFFFFC107; // Warning
    public static final int ACCENT_PURPLE = 0xFF9C27B0; // Special
    public static final int ACCENT_CYAN = 0xFF00BCD4;    // Info

    // Slider colors
    public static final int SLIDER_BG = 0xFF2A2A2A;      // Slider track background
    public static final int SLIDER_FILL = 0xFF5C8AE6;    // Slider fill color
    public static final int SLIDER_KNOB = 0xFFE0E0E0;    // Slider knob

    // Button colors
    public static final int BUTTON_BG = 0xFF2D2D2D;      // Button background
    public static final int BUTTON_HOVER = 0xFF3D3D3D;   // Button hover
    public static final int BUTTON_ACTIVE = 0xFF5C8AE6;  // Button active/pressed
    public static final int BUTTON_TEXT = 0xFFE0E0E0;    // Button text

    // Checkbox colors
    public static final int CHECKBOX_BG = 0xFF1A1A1A;    // Checkbox background
    public static final int CHECKBOX_CHECK = 0xFF5C8AE6; // Checkmark color

    // Scrollbar colors
    public static final int SCROLLBAR_BG = 0xFF1A1A1A;   // Scrollbar track
    public static final int SCROLLBAR_THUMB = 0xFF3A3A3A;// Scrollbar thumb
    public static final int SCROLLBAR_HOVER = 0xFF4A4A4A;// Scrollbar thumb hover

    // Shadow
    public static final int SHADOW = 0x80000000;         // Drop shadow

    private AxiomColors() {}

    /**
     * Get color with modified alpha
     */
    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /**
     * Interpolate between two colors
     */
    public static int lerp(int colorA, int colorB, float t) {
        int aA = (colorA >> 24) & 0xFF;
        int rA = (colorA >> 16) & 0xFF;
        int gA = (colorA >> 8) & 0xFF;
        int bA = colorA & 0xFF;

        int aB = (colorB >> 24) & 0xFF;
        int rB = (colorB >> 16) & 0xFF;
        int gB = (colorB >> 8) & 0xFF;
        int bB = colorB & 0xFF;

        int a = (int)(aA + (aB - aA) * t);
        int r = (int)(rA + (rB - rA) * t);
        int g = (int)(gA + (gB - gA) * t);
        int b = (int)(bA + (bB - bA) * t);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
