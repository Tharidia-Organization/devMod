package com.devmod.client.ui.core;

import net.minecraft.client.gui.GuiGraphics;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Base class for responsive HUD overlays.
 *
 * <p>Provides automatic scaling and positioning using {@link UIScaleManager}.
 * Subclasses only need to implement {@link #renderContent} with scaled coordinates.
 *
 * <h2>Usage</h2>
 * <pre>
 * public class MyOverlay extends ResponsiveOverlay {
 *     public MyOverlay() {
 *         super(Anchor.TOP_RIGHT, 200, 100); // 200x100 at top-right
 *     }
 *
 *     {@literal @}Override
 *     protected void renderContent(GuiGraphics graphics, int x, int y, int width, int height) {
 *         // x, y, width, height are already scaled and positioned
 *         graphics.fill(x, y, x + width, y + height, DesignTokens.Utility.SHADOW);
 *     }
 * }
 * </pre>
 */
@OnlyIn(Dist.CLIENT)
public abstract class ResponsiveOverlay {

    /** Anchor position for the overlay. */
    protected final Anchor anchor;

    /** Base width at 1x scale. */
    protected final int baseWidth;

    /** Base height at 1x scale. */
    protected final int baseHeight;

    /** Margin from anchor edge. */
    protected int margin = 12;

    /** Whether this overlay is visible. */
    protected boolean visible = true;

    /** Current scaled dimensions (updated each render). */
    protected int currentX, currentY, currentWidth, currentHeight;

    /**
     * Creates a responsive overlay.
     *
     * @param anchor Position anchor
     * @param baseWidth Width at 1x scale
     * @param baseHeight Height at 1x scale
     */
    protected ResponsiveOverlay(Anchor anchor, int baseWidth, int baseHeight) {
        this.anchor = anchor;
        this.baseWidth = baseWidth;
        this.baseHeight = baseHeight;
    }

    /**
     * Renders the overlay with automatic scaling and positioning.
     *
     * @param graphics The graphics context
     * @param partialTicks Partial ticks for animations
     */
    public final void render(GuiGraphics graphics, float partialTicks) {
        if (!visible) return;

        // Update scale manager
        UIScaleManager.update();

        // Calculate scaled dimensions
        currentWidth = UIScaleManager.scale(baseWidth);
        currentHeight = UIScaleManager.scale(baseHeight);

        // Apply responsive constraints
        currentWidth = constrainWidth(currentWidth);
        currentHeight = constrainHeight(currentHeight);

        // Calculate position based on anchor
        int scaledMargin = UIScaleManager.scale(margin);
        int[] pos = calculatePosition(currentWidth, currentHeight, scaledMargin);
        currentX = pos[0];
        currentY = pos[1];

        // Snap to grid
        currentX = UIScaleManager.snap(currentX);
        currentY = UIScaleManager.snap(currentY);

        // Render
        renderContent(graphics, currentX, currentY, currentWidth, currentHeight, partialTicks);
    }

    /**
     * Override to render the overlay content.
     *
     * @param graphics The graphics context
     * @param x Left position (scaled and positioned)
     * @param y Top position (scaled and positioned)
     * @param width Scaled width
     * @param height Scaled height
     * @param partialTicks Partial ticks for animations
     */
    protected abstract void renderContent(GuiGraphics graphics, int x, int y,
                                          int width, int height, float partialTicks);

    /**
     * Override to constrain width based on screen size.
     * Default: clamp to 80% of safe width.
     */
    protected int constrainWidth(int scaledWidth) {
        return UIScaleManager.clampToSafe(scaledWidth, 0.8f);
    }

    /**
     * Override to constrain height based on screen size.
     * Default: clamp to 60% of safe height.
     */
    protected int constrainHeight(int scaledHeight) {
        int maxHeight = (int) (UIScaleManager.getSafeHeight() * 0.6f);
        return Math.min(scaledHeight, UIScaleManager.snap(maxHeight));
    }

    /**
     * Calculates position based on anchor.
     */
    private int[] calculatePosition(int width, int height, int scaledMargin) {
        int x, y;

        switch (anchor) {
            case TOP_LEFT -> {
                x = UIScaleManager.getSafeLeft() + scaledMargin;
                y = UIScaleManager.getSafeTop() + scaledMargin;
            }
            case TOP_CENTER -> {
                x = UIScaleManager.centerX(width);
                y = UIScaleManager.getSafeTop() + scaledMargin;
            }
            case TOP_RIGHT -> {
                x = UIScaleManager.fromRight(width, scaledMargin);
                y = UIScaleManager.getSafeTop() + scaledMargin;
            }
            case CENTER_LEFT -> {
                x = UIScaleManager.getSafeLeft() + scaledMargin;
                y = UIScaleManager.centerY(height);
            }
            case CENTER -> {
                x = UIScaleManager.centerX(width);
                y = UIScaleManager.centerY(height);
            }
            case CENTER_RIGHT -> {
                x = UIScaleManager.fromRight(width, scaledMargin);
                y = UIScaleManager.centerY(height);
            }
            case BOTTOM_LEFT -> {
                x = UIScaleManager.getSafeLeft() + scaledMargin;
                y = UIScaleManager.fromBottom(height, scaledMargin);
            }
            case BOTTOM_CENTER -> {
                x = UIScaleManager.centerX(width);
                y = UIScaleManager.fromBottom(height, scaledMargin);
            }
            case BOTTOM_RIGHT -> {
                x = UIScaleManager.fromRight(width, scaledMargin);
                y = UIScaleManager.fromBottom(height, scaledMargin);
            }
            default -> {
                x = UIScaleManager.centerX(width);
                y = UIScaleManager.centerY(height);
            }
        }

        return new int[] { x, y };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Sets the margin from anchor edge.
     */
    public void setMargin(int margin) {
        this.margin = margin;
    }

    /**
     * Sets visibility.
     */
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    /**
     * Checks if visible.
     */
    public boolean isVisible() {
        return visible;
    }

    /**
     * Gets current scaled width (valid after render).
     */
    public int getCurrentWidth() {
        return currentWidth;
    }

    /**
     * Gets current scaled height (valid after render).
     */
    public int getCurrentHeight() {
        return currentHeight;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ANCHOR ENUM
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Position anchors for overlay placement.
     */
    public enum Anchor {
        TOP_LEFT,
        TOP_CENTER,
        TOP_RIGHT,
        CENTER_LEFT,
        CENTER,
        CENTER_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_CENTER,
        BOTTOM_RIGHT
    }
}
