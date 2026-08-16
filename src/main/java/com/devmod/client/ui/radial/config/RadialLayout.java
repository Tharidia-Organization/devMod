package com.devmod.client.ui.radial.config;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The radial menu's geometry, computed once and shared by everything that draws or hit-tests it.
 *
 * <h2>Why this exists</h2>
 *
 * <p>The radii used to be seven independent static fields, each built by adding a hand-picked
 * offset outward from the centre, and every element anchored itself to whichever one seemed
 * closest. Widening one band silently moved its neighbours: sizing the macro hub to fit its icon
 * pushed the icons into the ring above, and re-anchoring the context badge to clear the hub pushed
 * it into the category labels. Each fix moved the collision somewhere else because nothing owned
 * the layout as a whole.
 *
 * <p>Here the rings are a <b>stack</b>. Each band declares the thickness it needs for the content
 * it holds, and every band above it starts where the previous one ended. Adding a band, or making
 * one thicker, shifts everything outside it automatically -- there is no second place to update
 * and no neighbour to break.
 *
 * <p>{@link #badgeAnchor()} is the radius anything drawn around the centre must clear. Elements
 * that float outside the rings ask for it instead of picking a band to measure from, which is the
 * mistake that produced the overlapping context badge twice.
 *
 * <h2>Reference</h2>
 *
 * <p>The one-immutable-geometry-object approach is how EZActions structures its radial menu
 * ({@code RadialScreenMath.Radii}, computed by {@code computeRadii} and passed to every draw and
 * pick call). Two ideas are taken from it: bands expressed as an explicit thickness rather than an
 * offset, and hit-testing reading the same object the renderer reads, so the two cannot drift.
 *
 * @param centerButton radius of the central close/back button
 * @param macroHubOuter outer radius of the macro hub band
 * @param favoritesRing radius the favourite bubbles are centred on; meaningless when not enabled
 * @param favoritesEnabled whether there is room for the favourites ring at all
 * @param innerRing inner radius of the category ring
 * @param outerRing outer radius of the category ring
 * @param itemRing radius the per-item icons are centred on, outside the category ring
 * @param badgeAnchor radius that anything floating around the centre must clear
 */
@OnlyIn(Dist.CLIENT)
public record RadialLayout(
    int centerButton,
    int macroHubOuter,
    int favoritesRing,
    boolean favoritesEnabled,
    int innerRing,
    int outerRing,
    int itemRing,
    int badgeAnchor
) {

    /**
     * Build the ring stack outward from the centre.
     *
     * <p>Each argument is the thickness a band needs, not a position. Callers derive those from the
     * content -- an icon's size, a line of text -- so a band is never narrower than what it has to
     * hold. {@code innerRing} is pushed out if the bands below it would otherwise overlap it, which
     * is the check that was missing when the macro hub grew.
     *
     * @param centerButton radius of the central button
     * @param macroBand thickness needed by the macro hub
     * @param favoritesBand thickness needed by a favourite bubble, including its padding
     * @param gap breathing room between bands
     * @param desiredInner inner radius the category ring would like
     * @param desiredOuter outer radius of the category ring
     * @param itemOffset how far outside the category ring the item icons sit
     * @return the resolved layout, with every band clear of the ones below it
     */
    public static RadialLayout stack(int centerButton,
                                     int macroBand,
                                     int favoritesBand,
                                     int gap,
                                     int desiredInner,
                                     int desiredOuter,
                                     int itemOffset) {
        int macroOuter = centerButton + Math.max(1, macroBand);

        // Favourites sit between the hub and the category ring. They only exist if that gap can
        // hold a whole bubble; otherwise the ring is dropped rather than drawn collapsed on itself.
        int favoritesCentre = macroOuter + gap + favoritesBand / 2;
        int neededInner = favoritesCentre + favoritesBand / 2 + gap;
        boolean favorites = favoritesBand > 0 && neededInner <= desiredInner;

        int inner = favorites ? Math.max(desiredInner, neededInner) : Math.max(desiredInner, macroOuter + gap);
        int outer = Math.max(desiredOuter, inner + gap);

        // Anything floating near the centre clears the outermost thing drawn there. Asking for a
        // single number here is what stops the next element from picking the wrong band to measure
        // from.
        int anchor = Math.max(macroOuter, favorites ? favoritesCentre + favoritesBand / 2 : macroOuter);

        return new RadialLayout(
            centerButton,
            macroOuter,
            favorites ? favoritesCentre : 0,
            favorites,
            inner,
            outer,
            outer + itemOffset,
            anchor);
    }
}
