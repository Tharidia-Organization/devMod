package com.devmod.transport;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.util.StringRepresentable;

/**
 * States for transport nodes following the Unified Transport Aesthetic Bible (Rule 1).
 * Each state has associated primary and secondary colors for consistent visual feedback.
 *
 * <p>These colors are used for:
 * <ul>
 *   <li>LED/indicator on the Core block</li>
 *   <li>Particles during charging</li>
 *   <li>UI overlay border</li>
 *   <li>Message text color</li>
 * </ul>
 */
public enum TransportState implements StringRepresentable {
    /** Ready, waiting for activation. Blue tones. */
    IDLE("idle", 0x1a3a5c, 0x4a8ab0),

    /** Charging in progress. Green tones. */
    CHARGING("charging", 0x2ecc71, 0x7dcea0),

    /** Teleport imminent or in progress. White/gold tones. */
    ACTIVE("active", 0xffffff, 0xf1c40f),

    /** Error state, no destination. Red tones. */
    ERROR("error", 0xe74c3c, 0x922b21),

    /** Waiting for cooldown. Gray tones. */
    COOLDOWN("cooldown", 0x7f8c8d, 0x5d6d7e),

    /** Not accessible. Purple tones. */
    LOCKED("locked", 0x8e44ad, 0x5b2c6f);

    private final String name;
    private final int primaryColor;
    private final int secondaryColor;

    TransportState(String name, int primaryColor, int secondaryColor) {
        this.name = name;
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
    }

    /**
     * Returns the primary color for this state (RGB integer).
     */
    public int getPrimaryColor() {
        return primaryColor;
    }

    /**
     * Returns the secondary color for this state (RGB integer).
     */
    public int getSecondaryColor() {
        return secondaryColor;
    }

    /**
     * Returns the primary color with full alpha (ARGB integer).
     */
    public int getPrimaryColorWithAlpha() {
        return 0xFF000000 | primaryColor;
    }

    /**
     * Returns the secondary color with full alpha (ARGB integer).
     */
    public int getSecondaryColorWithAlpha() {
        return 0xFF000000 | secondaryColor;
    }

    /**
     * Returns the red component of the primary color (0-255).
     */
    public int getPrimaryRed() {
        return (primaryColor >> 16) & 0xFF;
    }

    /**
     * Returns the green component of the primary color (0-255).
     */
    public int getPrimaryGreen() {
        return (primaryColor >> 8) & 0xFF;
    }

    /**
     * Returns the blue component of the primary color (0-255).
     */
    public int getPrimaryBlue() {
        return primaryColor & 0xFF;
    }

    /**
     * Returns whether this state indicates the transport is usable.
     */
    public boolean isUsable() {
        return this == IDLE || this == CHARGING || this == ACTIVE;
    }

    /**
     * Returns whether this state indicates an issue.
     */
    public boolean isProblematic() {
        return this == ERROR || this == LOCKED;
    }

    @Override
    @Nonnull
    public String getSerializedName() {
        return Objects.requireNonNull(name);
    }

    /**
     * Gets a state by its serialized name.
     */
    @Nonnull
    public static TransportState byName(String name) {
        for (TransportState state : values()) {
            if (state.name.equals(name)) {
                return state;
            }
        }
        return IDLE;
    }
}
