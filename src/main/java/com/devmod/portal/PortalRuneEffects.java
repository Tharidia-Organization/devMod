package com.devmod.portal;

import java.util.Set;

import javax.annotation.Nonnull;

/**
 * Calculated rune effects for a portal based on runes placed on its frame.
 * Combines effects from multiple runes into a single immutable result.
 */
public record PortalRuneEffects(
    boolean instantTeleport,
    boolean crossDimension,
    int linkRange
) {
    /**
     * Default portal linking range in blocks (without any runes).
     */
    public static final int DEFAULT_RANGE = 100;

    /**
     * Effects for a portal with no runes.
     */
    public static final PortalRuneEffects NONE = new PortalRuneEffects(false, false, DEFAULT_RANGE);

    /**
     * Calculates combined rune effects from a set of runes.
     *
     * @param runes the set of runes placed on the portal frame
     * @return the combined effects
     */
    @Nonnull
    public static PortalRuneEffects calculate(@Nonnull Set<RuneType> runes) {
        if (runes.isEmpty()) {
            return NONE;
        }

        boolean instant = false;
        boolean crossDim = false;
        int totalRange = DEFAULT_RANGE;
        boolean hasInfinity = false;

        for (RuneType rune : runes) {
            if (rune.allowsInstantTeleport()) {
                instant = true;
            }
            if (rune.allowsCrossDimension()) {
                crossDim = true;
            }
            if (rune.getRangeBonus() == Integer.MAX_VALUE) {
                hasInfinity = true;
            } else if (rune.getRangeBonus() > 0) {
                // Additive stacking for range bonuses
                totalRange += rune.getRangeBonus();
            }
        }

        // Infinity overrides all other range calculations
        if (hasInfinity) {
            totalRange = Integer.MAX_VALUE;
        }

        return new PortalRuneEffects(instant, crossDim, totalRange);
    }

    /**
     * Returns true if this portal can link to a portal at the given distance.
     *
     * @param distance the distance in blocks to the target portal
     * @return true if linking is possible
     */
    public boolean canLinkAtDistance(double distance) {
        if (linkRange == Integer.MAX_VALUE) {
            return true;
        }
        return distance <= linkRange;
    }

    /**
     * Returns true if this portal can link to a portal in a different dimension.
     *
     * @param sameDimension true if the portals are in the same dimension
     * @return true if linking is possible
     */
    public boolean canLinkToDimension(boolean sameDimension) {
        return sameDimension || crossDimension;
    }
}
