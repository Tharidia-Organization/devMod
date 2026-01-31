package com.devmod.client.ui.radial.config;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.radial.RadialMenuConfig;

/**
 * Responsive scaling for the RadialMenu system.
 *
 * <h2>Design Philosophy</h2>
 * <p>The radial menu must remain usable across all screen sizes while:
 * <ul>
 *   <li>Maintaining comfortable hit targets (minimum touch/click area)</li>
 *   <li>Staying within screen bounds with proper margins</li>
 *   <li>Scaling smoothly without jarring size jumps</li>
 *   <li>Respecting Minecraft's GUI scale setting</li>
 * </ul>
 *
 * <h2>Scaling Strategy</h2>
 * <p>The menu uses a <b>fit-to-screen</b> approach:
 * <pre>
 * 1. Calculate maximum radius that fits on screen (with margins)
 * 2. Apply configured ratios (inner/outer/item proportions)
 * 3. Clamp to minimum sizes for usability
 * 4. Snap to grid for crisp rendering
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>
 * // In RadialMenuScreen.init() or render():
 * RadialMenuScaler.update();
 *
 * // Get scaled values:
 * int inner = RadialMenuScaler.getInnerRadius();
 * int outer = RadialMenuScaler.getOuterRadius();
 * </pre>
 */
@OnlyIn(Dist.CLIENT)
public final class RadialMenuScaler {

    // ═══════════════════════════════════════════════════════════════════════════
    // REFERENCE VALUES (at 1080p, GUI scale 1x)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Default inner radius at reference resolution. */
    private static final int REF_INNER_RADIUS = 55;

    /** Default outer radius at reference resolution. */
    private static final int REF_OUTER_RADIUS = 130;

    /** Default item ring radius at reference resolution. */
    private static final int REF_ITEM_RADIUS = 95;

    /** Default center button radius at reference resolution. */
    private static final int REF_CENTER_RADIUS = 40;

    // ═══════════════════════════════════════════════════════════════════════════
    // MINIMUM VALUES (for usability)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Minimum inner radius for touch targets. */
    private static final int MIN_INNER_RADIUS = 32;

    /** Minimum outer radius for visibility. */
    private static final int MIN_OUTER_RADIUS = 70;

    /** Minimum center button radius. */
    private static final int MIN_CENTER_RADIUS = 24;

    /** Minimum item size for hit detection. */
    private static final int MIN_ITEM_SIZE = 24;

    // ═══════════════════════════════════════════════════════════════════════════
    // PROPORTIONS (relative to outer radius)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Inner radius as proportion of outer. */
    private static final float INNER_RATIO = 0.42f;  // 55/130 ≈ 0.42

    /** Item radius as proportion of outer. */
    private static final float ITEM_RATIO = 0.73f;   // 95/130 ≈ 0.73

    /** Center button as proportion of inner. */
    private static final float CENTER_RATIO = 0.73f; // 40/55 ≈ 0.73

    /** Screen margin as proportion of smaller dimension. */
    private static final float SCREEN_MARGIN_RATIO = 0.08f;

    /** Maximum menu size as proportion of screen. */
    private static final float MAX_SCREEN_RATIO = 0.85f;

    /** Extra radial padding for item ring + hover expansion (reference units). */
    private static final int ITEM_ENVELOPE_PADDING = RadialMenuConstants.ITEM_RING_OFFSET
        + RadialMenuConstants.ITEM_HOVER_OFFSET
        + RadialMenuConstants.ITEM_BASE_SIZE
        + RadialMenuConstants.ITEM_HOVER_SIZE_BONUS;

    // ═══════════════════════════════════════════════════════════════════════════
    // COMPUTED VALUES (updated each frame)
    // ═══════════════════════════════════════════════════════════════════════════

    private static int innerRadius = REF_INNER_RADIUS;
    private static int outerRadius = REF_OUTER_RADIUS;
    private static int itemRadius = REF_ITEM_RADIUS;
    private static int centerButtonRadius = REF_CENTER_RADIUS;
    private static int favoritesRadius = REF_INNER_RADIUS - RadialMenuConstants.FAVORITES_OFFSET;
    private static int macroHubRadius = REF_CENTER_RADIUS + RadialMenuConstants.MACRO_HUB_OFFSET;
    private static int itemSize = RadialMenuConstants.ITEM_BASE_SIZE;
    private static float scaleFactor = 1.0f;

    private RadialMenuScaler() {}

    // ═══════════════════════════════════════════════════════════════════════════
    // UPDATE
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Updates all scaled values based on current screen dimensions.
     * Call at start of RadialMenuScreen.render() or in init().
     */
    public static void update() {
        UIScaleManager.update();
        int maxOuterForItems = computeMaxOuterRadius();

        // Calculate desired outer radius from reference scaled
        int scaledOuter = UIScaleManager.scale(REF_OUTER_RADIUS);

        int effectiveMinOuter = Math.min(MIN_OUTER_RADIUS, maxOuterForItems);
        outerRadius = Math.max(effectiveMinOuter, Math.min(scaledOuter, maxOuterForItems));

        // Calculate scale factor relative to reference
        scaleFactor = (float) outerRadius / REF_OUTER_RADIUS;

        // Calculate other radii proportionally
        innerRadius = Math.max(MIN_INNER_RADIUS, UIScaleManager.snap((int) (outerRadius * INNER_RATIO)));
        int innerMax = Math.max(12, outerRadius - UIScaleManager.snap(12));
        innerRadius = Math.min(innerRadius, innerMax);
        itemRadius = UIScaleManager.snap((int) (outerRadius * ITEM_RATIO));
        centerButtonRadius = Math.max(MIN_CENTER_RADIUS, UIScaleManager.snap((int) (innerRadius * CENTER_RATIO)));
        centerButtonRadius = Math.min(centerButtonRadius, Math.max(8, innerRadius - UIScaleManager.snap(6)));

        // Derived values
        favoritesRadius = Math.max(centerButtonRadius + 8, innerRadius - scaleConstant(RadialMenuConstants.FAVORITES_OFFSET));
        macroHubRadius = centerButtonRadius + scaleConstant(RadialMenuConstants.MACRO_HUB_OFFSET);

        // Item size scales with menu
        itemSize = Math.max(MIN_ITEM_SIZE, scaleConstant(RadialMenuConstants.ITEM_BASE_SIZE));

        // Clamp favorites ring so bubbles fit between hub and inner ring
        int favoriteBubble = scaleConstant(RadialMenuConstants.FAVORITE_BASE_SIZE + RadialMenuConstants.FAVORITE_SIZE_BONUS);
        int favoritePadding = scaleConstant(4);
        int minFav = macroHubRadius + favoriteBubble + favoritePadding;
        int maxFav = innerRadius - favoriteBubble - favoritePadding;
        if (maxFav >= minFav) {
            favoritesRadius = Math.max(minFav, Math.min(favoritesRadius, maxFav));
        }
    }

    /**
     * Updates using config overrides (for user customization).
     * Falls back to auto-scaling when config values are default.
     */
    public static void updateWithConfig(RadialMenuConfig config) {
        update(); // First calculate auto values

        // If user has custom config values that differ significantly from defaults,
        // use them scaled appropriately
        if (config != null) {
            boolean hasCustomInner = Math.abs(config.innerRadius - REF_INNER_RADIUS) > 5;
            boolean hasCustomOuter = Math.abs(config.outerRadius - REF_OUTER_RADIUS) > 5;

            if (hasCustomInner || hasCustomOuter) {
                // User has customized - scale their values
                float userScale = UIScaleManager.getEffectiveScale();
                if (hasCustomOuter) {
                    outerRadius = UIScaleManager.snap((int) (config.outerRadius * userScale));
                }
                if (hasCustomInner) {
                    innerRadius = UIScaleManager.snap((int) (config.innerRadius * userScale));
                }
                itemRadius = UIScaleManager.snap((int) (config.itemRadius * userScale));
                centerButtonRadius = UIScaleManager.snap((int) (config.centerButtonRadius * userScale));

                // Clamp to screen envelope and recompute derived values
                int maxOuterForItems = computeMaxOuterRadius();
                int effectiveMinOuter = Math.min(MIN_OUTER_RADIUS, maxOuterForItems);
                outerRadius = Math.max(effectiveMinOuter, Math.min(outerRadius, maxOuterForItems));

                scaleFactor = (float) outerRadius / REF_OUTER_RADIUS;

                int innerMax = Math.max(12, outerRadius - UIScaleManager.snap(12));
                innerRadius = Math.min(innerRadius, innerMax);
                innerRadius = Math.max(Math.min(MIN_INNER_RADIUS, innerMax), innerRadius);

                int itemMin = Math.max(8, innerRadius + UIScaleManager.snap(4));
                int itemMax = Math.max(itemMin, outerRadius - UIScaleManager.snap(4));
                itemRadius = Math.max(itemMin, Math.min(itemRadius, itemMax));

                centerButtonRadius = Math.min(centerButtonRadius, Math.max(8, innerRadius - UIScaleManager.snap(6)));

                favoritesRadius = Math.max(centerButtonRadius + 8,
                    innerRadius - scaleConstant(RadialMenuConstants.FAVORITES_OFFSET));
                macroHubRadius = centerButtonRadius + scaleConstant(RadialMenuConstants.MACRO_HUB_OFFSET);

                itemSize = Math.max(MIN_ITEM_SIZE, scaleConstant(RadialMenuConstants.ITEM_BASE_SIZE));

                int favoriteBubble = scaleConstant(RadialMenuConstants.FAVORITE_BASE_SIZE + RadialMenuConstants.FAVORITE_SIZE_BONUS);
                int favoritePadding = scaleConstant(4);
                int minFav = macroHubRadius + favoriteBubble + favoritePadding;
                int maxFav = innerRadius - favoriteBubble - favoritePadding;
                if (maxFav >= minFav) {
                    favoritesRadius = Math.max(minFav, Math.min(favoritesRadius, maxFav));
                }
            }
        }
    }

    private static int computeMaxOuterRadius() {
        int screenWidth = UIScaleManager.getScaledWidth();
        int screenHeight = UIScaleManager.getScaledHeight();
        int minDimension = Math.min(screenWidth, screenHeight);

        int margin = (int) (minDimension * SCREEN_MARGIN_RATIO);
        int maxMenuDiameter = (int) ((minDimension - margin * 2) * MAX_SCREEN_RATIO);
        int maxRadius = Math.max(0, maxMenuDiameter / 2);

        float envelopeRatio = (float) ITEM_ENVELOPE_PADDING / REF_OUTER_RADIUS;
        int maxOuterForItems = (int) (maxRadius / (1.0f + envelopeRatio));
        if (maxOuterForItems <= 0) {
            maxOuterForItems = Math.max(16, maxRadius);
        }
        return maxOuterForItems;
    }

    /**
     * Outer envelope radius including item ring and hover expansion.
     */
    public static int getMenuOuterExtent() {
        int hoverPad = scaleConstant(RadialMenuConstants.ITEM_HOVER_OFFSET + RadialMenuConstants.ITEM_HOVER_SIZE_BONUS);
        return outerRadius + getItemRingOffset() + getItemSize() + hoverPad;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCALING HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Scales a constant value based on current menu scale.
     */
    public static int scaleConstant(int baseValue) {
        return UIScaleManager.snap((int) (baseValue * scaleFactor));
    }

    /**
     * Scales a float constant.
     */
    public static float scaleConstantF(float baseValue) {
        return baseValue * scaleFactor;
    }

    /**
     * Gets item ring offset (distance beyond outer radius where items appear).
     */
    public static int getItemRingOffset() {
        return scaleConstant(RadialMenuConstants.ITEM_RING_OFFSET);
    }

    /**
     * Gets the hover expansion distance for items.
     */
    public static int getItemHoverOffset() {
        return scaleConstant(RadialMenuConstants.ITEM_HOVER_OFFSET);
    }

    /**
     * Gets glow radius for selected category.
     */
    public static int getCategoryGlowRadius() {
        return scaleConstant(RadialMenuConstants.CATEGORY_GLOW_RADIUS);
    }

    /**
     * Gets tooltip vertical offset.
     */
    public static int getTooltipOffsetY() {
        return scaleConstant(RadialMenuConstants.TOOLTIP_OFFSET_Y);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RESPONSIVE VALUES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gets font scale factor for menu text.
     * Text scales less aggressively than geometry.
     */
    public static float getFontScale() {
        return 1.0f + (scaleFactor - 1.0f) * 0.6f;
    }

    /**
     * Gets border thickness based on scale.
     */
    public static int getBorderThickness() {
        if (scaleFactor < 0.7f) return 1;
        if (scaleFactor > 1.5f) return 3;
        return 2;
    }

    /**
     * Gets whether to show detailed labels (false on tiny screens).
     */
    public static boolean showDetailedLabels() {
        return UIScaleManager.isAtLeast(UIScaleManager.ScreenSize.MEDIUM);
    }

    /**
     * Gets whether to show category badges (false on small screens).
     */
    public static boolean showBadges() {
        return UIScaleManager.isAtLeast(UIScaleManager.ScreenSize.MEDIUM) && scaleFactor >= 0.6f;
    }

    /**
     * Gets maximum visible items per category for current scale.
     * Reduces on small screens to prevent overlap.
     */
    public static int getMaxVisibleItems() {
        if (scaleFactor < 0.5f) return 6;
        if (scaleFactor < 0.7f) return 8;
        return RadialMenuConstants.MAX_ITEMS_PER_CATEGORY;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════════════════

    public static int getInnerRadius() { return innerRadius; }
    public static int getOuterRadius() { return outerRadius; }
    public static int getItemRadius() { return itemRadius; }
    public static int getCenterButtonRadius() { return centerButtonRadius; }
    public static int getFavoritesRadius() { return favoritesRadius; }
    public static int getMacroHubRadius() { return macroHubRadius; }
    public static int getItemSize() { return itemSize; }
    public static float getScaleFactor() { return scaleFactor; }

    /**
     * Gets center X coordinate (screen center).
     */
    public static int getCenterX() {
        return UIScaleManager.getCenterX();
    }

    /**
     * Gets center Y coordinate (screen center).
     */
    public static int getCenterY() {
        return UIScaleManager.getCenterY();
    }
}
