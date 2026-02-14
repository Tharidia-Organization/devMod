package com.devmod.endurance.api;

/**
 * Public API interface for style rank values.
 *
 * <p>Implemented by {@link com.devmod.endurance.ComboSystem.StyleRank}.
 * External modules should depend on this interface rather than the concrete enum
 * when only read access is needed.</p>
 */
public interface IStyleRank {

    /** Human-readable display name (e.g. "Savage"). */
    String getDisplayName();

    /** Minimum score threshold for this rank. */
    int getThreshold();

    /** ARGB color associated with this rank. */
    int getColor();

    /** Score multiplier applied at this rank. */
    float getMultiplier();

    /** Network-safe integer id for serialization. */
    int getNetworkId();
}
