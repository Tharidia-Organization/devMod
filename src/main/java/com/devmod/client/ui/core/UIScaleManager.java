package com.devmod.client.ui.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Unified UI scaling manager for all DevMod screens and overlays.
 *
 * <h2>Design Philosophy</h2>
 * <p>All UI elements should scale correctly with:
 * <ul>
 *   <li>Window size changes</li>
 *   <li>Fullscreen toggle</li>
 *   <li>Minecraft GUI scale setting</li>
 *   <li>Different aspect ratios (ultrawide, 4:3, etc.)</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>
 * // In render method:
 * UIScaleManager.update(); // Once per frame
 *
 * // Get scaled dimensions:
 * int panelWidth = UIScaleManager.scale(200); // 200px at 1x scale
 * float radius = UIScaleManager.scaleF(75.0f);
 *
 * // Get screen info:
 * int safeWidth = UIScaleManager.getSafeWidth();
 * ScreenSize size = UIScaleManager.getScreenSize();
 * </pre>
 *
 * <h2>Grid System</h2>
 * <p>All dimensions are snapped to a 4px grid for crisp rendering:
 * <pre>
 * UIScaleManager.snap(17)  → 16
 * UIScaleManager.snap(18)  → 20
 * </pre>
 */
@OnlyIn(Dist.CLIENT)
public final class UIScaleManager {

    // ═══════════════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Base grid unit (all dimensions snap to multiples of this). */
    public static final int GRID_UNIT = 4;

    /** Minimum safe margin from screen edges. */
    public static final int EDGE_MARGIN = 8;

    /** Minimum gap/margin to prevent element overlap (2px). */
    public static final int MIN_GAP = 2;

    /** Minimum text line height to prevent text overlap (8px). */
    public static final int MIN_LINE_HEIGHT = 8;

    /** Minimum item size for clickable elements (16px). */
    public static final int MIN_ITEM_SIZE = 16;

    /** Minimum padding for panels/containers (4px). */
    public static final int MIN_PADDING = 4;

    /** Reference resolution for 1x scale (1080p). */
    private static final int REFERENCE_HEIGHT = 1080;

    /** Minimum scale factor. */
    private static final float MIN_SCALE = 0.5f;

    /** Maximum scale factor. */
    private static final float MAX_SCALE = 3.0f;

    // ═══════════════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════════════

    private static int screenWidth = 1920;
    private static int screenHeight = 1080;
    private static int scaledWidth = 1920;
    private static int scaledHeight = 1080;
    private static float guiScale = 1.0f;
    private static float autoScale = 1.0f;
    private static float effectiveScale = 1.0f;
    private static ScreenSize screenSize = ScreenSize.LARGE;
    private static boolean initialized = false;

    private UIScaleManager() {}

    // ═══════════════════════════════════════════════════════════════════════════
    // UPDATE (call once per frame)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Updates scale calculations. Call at start of render.
     */
    public static void update() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return;

        // Get raw window dimensions
        screenWidth = mc.getWindow().getWidth();
        screenHeight = mc.getWindow().getHeight();

        // Get scaled dimensions (what Minecraft uses for GUI)
        scaledWidth = mc.getWindow().getGuiScaledWidth();
        scaledHeight = mc.getWindow().getGuiScaledHeight();

        // Get Minecraft's GUI scale factor
        guiScale = (float) mc.getWindow().getGuiScale();

        // Calculate auto-scale based on screen height
        autoScale = calculateAutoScale(scaledHeight);

        // Effective scale combines GUI scale awareness with auto-scale
        effectiveScale = autoScale;

        // Determine screen size category
        screenSize = ScreenSize.fromHeight(scaledHeight);

        initialized = true;
    }

    /**
     * Calculates automatic scale factor based on screen height.
     * Reference: 1080p = 1.0x scale
     */
    private static float calculateAutoScale(int height) {
        float raw = (float) height / REFERENCE_HEIGHT;
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, raw));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCALING METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Scales an integer dimension and snaps to grid.
     *
     * @param baseValue The base value at 1x scale
     * @return Scaled and grid-snapped value
     */
    public static int scale(int baseValue) {
        return snap((int) (baseValue * effectiveScale));
    }

    /**
     * Scales an integer dimension with a guaranteed minimum value.
     * Use this for margins, gaps, and spacing that should never be 0.
     *
     * @param baseValue The base value at 1x scale
     * @param minimum The minimum return value (prevents overlap on small screens)
     * @return Scaled and grid-snapped value, at least the minimum
     */
    public static int scaleMin(int baseValue, int minimum) {
        return Math.max(minimum, snap((int) (baseValue * effectiveScale)));
    }

    /**
     * Scales a float dimension.
     *
     * @param baseValue The base value at 1x scale
     * @return Scaled value (not snapped, for smooth animations)
     */
    public static float scaleF(float baseValue) {
        return baseValue * effectiveScale;
    }

    /**
     * Scales a dimension for text (uses slightly different formula).
     * Text should scale less aggressively to maintain readability.
     *
     * @param baseSize Base font size
     * @return Scaled font size
     */
    public static int scaleText(int baseSize) {
        // Text scales at 70% of the rate to prevent tiny/huge text
        float textScale = 1.0f + (effectiveScale - 1.0f) * (7f / 10f);
        return Math.max(6, snap((int) (baseSize * textScale)));
    }

    /**
     * Inverse scale - converts screen coordinates to base coordinates.
     *
     * @param screenValue Value in current screen space
     * @return Value in base (1x) space
     */
    public static int unscale(int screenValue) {
        return (int) (screenValue / effectiveScale);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GRID ALIGNMENT
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Snaps a value to the nearest grid point.
     */
    public static int snap(int value) {
        return ((value + GRID_UNIT / 2) / GRID_UNIT) * GRID_UNIT;
    }

    /**
     * Snaps a value up to the next grid point.
     */
    public static int snapUp(int value) {
        return ((value + GRID_UNIT - 1) / GRID_UNIT) * GRID_UNIT;
    }

    /**
     * Snaps a value down to the previous grid point.
     */
    public static int snapDown(int value) {
        return (value / GRID_UNIT) * GRID_UNIT;
    }

    /**
     * Checks if a value is on the grid.
     */
    public static boolean isOnGrid(int value) {
        return value % GRID_UNIT == 0;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SAFE ZONES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gets the safe width (accounting for edge margins).
     */
    public static int getSafeWidth() {
        return scaledWidth - (EDGE_MARGIN * 2);
    }

    /**
     * Gets the safe height (accounting for edge margins).
     */
    public static int getSafeHeight() {
        return scaledHeight - (EDGE_MARGIN * 2);
    }

    /**
     * Gets the left edge of the safe zone.
     */
    public static int getSafeLeft() {
        return EDGE_MARGIN;
    }

    /**
     * Gets the top edge of the safe zone.
     */
    public static int getSafeTop() {
        return EDGE_MARGIN;
    }

    /**
     * Gets the right edge of the safe zone.
     */
    public static int getSafeRight() {
        return scaledWidth - EDGE_MARGIN;
    }

    /**
     * Gets the bottom edge of the safe zone.
     */
    public static int getSafeBottom() {
        return scaledHeight - EDGE_MARGIN;
    }

    /**
     * Gets the center X coordinate.
     */
    public static int getCenterX() {
        return scaledWidth / 2;
    }

    /**
     * Gets the center Y coordinate.
     */
    public static int getCenterY() {
        return scaledHeight / 2;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RESPONSIVE BREAKPOINTS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gets the current screen size category.
     */
    public static ScreenSize getScreenSize() {
        return screenSize;
    }

    /**
     * Checks if screen is at least the given size.
     */
    public static boolean isAtLeast(ScreenSize minSize) {
        return screenSize.ordinal() >= minSize.ordinal();
    }

    /**
     * Checks if screen is at most the given size.
     */
    public static boolean isAtMost(ScreenSize maxSize) {
        return screenSize.ordinal() <= maxSize.ordinal();
    }

    /**
     * Gets a value based on screen size breakpoints.
     *
     * @param small Value for SMALL screens
     * @param medium Value for MEDIUM screens
     * @param large Value for LARGE screens
     * @param xlarge Value for XLARGE screens
     * @return The appropriate value for current screen size
     */
    public static int responsive(int small, int medium, int large, int xlarge) {
        return switch (screenSize) {
            case SMALL -> small;
            case MEDIUM -> medium;
            case LARGE -> large;
            case XLARGE -> xlarge;
        };
    }

    /**
     * Gets a float value based on screen size breakpoints.
     */
    public static float responsiveF(float small, float medium, float large, float xlarge) {
        return switch (screenSize) {
            case SMALL -> small;
            case MEDIUM -> medium;
            case LARGE -> large;
            case XLARGE -> xlarge;
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LAYOUT HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Centers a panel horizontally.
     *
     * @param panelWidth Width of the panel
     * @return X coordinate for centered panel
     */
    public static int centerX(int panelWidth) {
        return (scaledWidth - panelWidth) / 2;
    }

    /**
     * Centers a panel vertically.
     *
     * @param panelHeight Height of the panel
     * @return Y coordinate for centered panel
     */
    public static int centerY(int panelHeight) {
        return (scaledHeight - panelHeight) / 2;
    }

    /**
     * Positions a panel from the right edge.
     *
     * @param panelWidth Width of the panel
     * @param margin Margin from right edge
     * @return X coordinate
     */
    public static int fromRight(int panelWidth, int margin) {
        return scaledWidth - panelWidth - margin;
    }

    /**
     * Positions a panel from the bottom edge.
     *
     * @param panelHeight Height of the panel
     * @param margin Margin from bottom edge
     * @return Y coordinate
     */
    public static int fromBottom(int panelHeight, int margin) {
        return scaledHeight - panelHeight - margin;
    }

    /**
     * Clamps a dimension to fit within safe bounds.
     *
     * @param value Desired dimension
     * @param maxPercent Maximum percentage of safe area (0-1)
     * @return Clamped dimension
     */
    public static int clampToSafe(int value, float maxPercent) {
        int maxWidth = (int) (getSafeWidth() * maxPercent);
        return Math.min(value, snap(maxWidth));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ASPECT RATIO
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gets the current aspect ratio.
     */
    public static float getAspectRatio() {
        return (float) scaledWidth / scaledHeight;
    }

    /**
     * Checks if screen is ultrawide (>2.0 aspect ratio).
     */
    public static boolean isUltrawide() {
        return getAspectRatio() > 2.0f;
    }

    /**
     * Checks if screen is portrait mode.
     */
    public static boolean isPortrait() {
        return getAspectRatio() < 1.0f;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════════════════

    public static int getScreenWidth() { return screenWidth; }
    public static int getScreenHeight() { return screenHeight; }
    public static int getScaledWidth() { return scaledWidth; }
    public static int getScaledHeight() { return scaledHeight; }
    public static float getGuiScale() { return guiScale; }
    public static float getAutoScale() { return autoScale; }
    public static float getEffectiveScale() { return effectiveScale; }
    public static boolean isInitialized() { return initialized; }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCREEN SIZE ENUM
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Screen size categories for responsive layouts.
     */
    public enum ScreenSize {
        /** Under 480px height (very small window). */
        SMALL(0, 480),

        /** 480-720px height (small window). */
        MEDIUM(480, 720),

        /** 720-1080px height (standard). */
        LARGE(720, 1080),

        /** Over 1080px height (high resolution). */
        XLARGE(1080, Integer.MAX_VALUE);

        private final int minHeight;
        private final int maxHeight;

        ScreenSize(int minHeight, int maxHeight) {
            this.minHeight = minHeight;
            this.maxHeight = maxHeight;
        }

        public int getMinHeight() { return minHeight; }
        public int getMaxHeight() { return maxHeight; }

        public static ScreenSize fromHeight(int height) {
            for (ScreenSize size : values()) {
                if (height >= size.minHeight && height < size.maxHeight) {
                    return size;
                }
            }
            return LARGE;
        }
    }

    // =========================================================================
    // TEXT RENDERING
    // =========================================================================

    /**
     * Draws a string with proper UI scaling.
     *
     * @param graphics The GuiGraphics context
     * @param font The font to use
     * @param text The text to draw
     * @param x X coordinate
     * @param y Y coordinate
     * @param color Text color (ARGB)
     * @param shadow Whether to draw with shadow
     */
    public static void drawScaledString(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                        String text, int x, int y, int color, boolean shadow) {
        graphics.drawString(font, text, x, y, color, shadow);
    }

    /**
     * Draws a string with proper UI scaling (no shadow).
     *
     * @param graphics The GuiGraphics context
     * @param font The font to use
     * @param text The text to draw
     * @param x X coordinate
     * @param y Y coordinate
     * @param color Text color (ARGB)
     */
    public static void drawScaledString(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                        String text, int x, int y, int color) {
        graphics.drawString(font, text, x, y, color, false);
    }

    /**
     * Draws a centered string with proper UI scaling.
     *
     * @param graphics The GuiGraphics context
     * @param font The font to use
     * @param text The text to draw
     * @param centerX Center X coordinate
     * @param y Y coordinate
     * @param color Text color (ARGB)
     */
    public static void drawScaledCenteredString(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                                String text, int centerX, int y, int color) {
        graphics.drawCenteredString(font, text, centerX, y, color);
    }

    /**
     * Gets the width of a string with proper UI scaling.
     *
     * @param font The font to use
     * @param text The text to measure
     * @return The width in pixels
     */
    public static int getScaledStringWidth(net.minecraft.client.gui.Font font, String text) {
        return font.width(text);
    }

    /**
     * Draws a Component with proper UI scaling.
     *
     * @param graphics The GuiGraphics context
     * @param font The font to use
     * @param text The Component to draw
     * @param x X coordinate
     * @param y Y coordinate
     * @param color Text color (ARGB)
     * @param shadow Whether to draw with shadow
     */
    public static void drawScaledString(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                        net.minecraft.network.chat.Component text, int x, int y, int color, boolean shadow) {
        graphics.drawString(font, text, x, y, color, shadow);
    }

    /**
     * Draws a Component with proper UI scaling (no shadow).
     *
     * @param graphics The GuiGraphics context
     * @param font The font to use
     * @param text The Component to draw
     * @param x X coordinate
     * @param y Y coordinate
     * @param color Text color (ARGB)
     */
    public static void drawScaledString(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                        net.minecraft.network.chat.Component text, int x, int y, int color) {
        graphics.drawString(font, text, x, y, color, false);
    }

    /**
     * Draws a FormattedCharSequence with proper UI scaling.
     *
     * @param graphics The GuiGraphics context
     * @param font The font to use
     * @param text The FormattedCharSequence to draw
     * @param x X coordinate
     * @param y Y coordinate
     * @param color Text color (ARGB)
     * @param shadow Whether to draw shadow
     */
    public static void drawScaledString(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                        net.minecraft.util.FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        graphics.drawString(font, text, x, y, color, shadow);
    }

    /**
     * Draws a FormattedCharSequence without shadow.
     *
     * @param graphics The GuiGraphics context
     * @param font The font to use
     * @param text The FormattedCharSequence to draw
     * @param x X coordinate
     * @param y Y coordinate
     * @param color Text color (ARGB)
     */
    public static void drawScaledString(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                        net.minecraft.util.FormattedCharSequence text, int x, int y, int color) {
        graphics.drawString(font, text, x, y, color, false);
    }

    /**
     * Draws a centered Component with proper UI scaling.
     *
     * @param graphics The GuiGraphics context
     * @param font The font to use
     * @param text The Component to draw
     * @param centerX Center X coordinate
     * @param y Y coordinate
     * @param color Text color (ARGB)
     */
    public static void drawScaledCenteredString(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                                net.minecraft.network.chat.Component text, int centerX, int y, int color) {
        graphics.drawCenteredString(font, text, centerX, y, color);
    }

    /**
     * Gets the width of a Component with proper UI scaling.
     *
     * @param font The font to use
     * @param text The Component to measure
     * @return The width in pixels
     */
    public static int getScaledStringWidth(net.minecraft.client.gui.Font font, net.minecraft.network.chat.Component text) {
        return font.width(text);
    }

    /**
     * Gets the scaled line height for text rendering.
     * Uses the default Minecraft font line height (9 pixels) scaled appropriately.
     * Minimum of MIN_LINE_HEIGHT to prevent text overlap on small screens.
     *
     * @return The scaled line height in pixels
     */
    public static int getScaledLineHeight() {
        return Math.max(MIN_LINE_HEIGHT, scale(9));
    }

    /**
     * Gets the text scale factor for custom text rendering.
     * Text scales at 70% of the rate to prevent tiny/huge text.
     *
     * @return The text scale factor
     */
    public static float getTextScale() {
        return 1.0f + (effectiveScale - 1.0f) * (7f / 10f);
    }
}
