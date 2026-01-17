package com.devmod.transport;

import java.util.Objects;

import javax.annotation.Nonnull;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/**
 * Transport modes determining how a node routes teleportation.
 *
 * <p>Modes define:
 * <ul>
 *   <li>How destinations are determined</li>
 *   <li>Whether the link is bidirectional or unidirectional</li>
 *   <li>If multiple destinations are supported</li>
 *   <li>Party/group teleport behavior</li>
 * </ul>
 */
public enum TransportMode implements StringRepresentable {
    /**
     * 1:1 bidirectional linking.
     * Two nodes connected directly via UUID.
     * Stepping on one teleports to the other and vice versa.
     */
    LINKED("linked", true, false, false),

    /**
     * N:N network teleportation.
     * Nodes share a network name, destination selected by mode.
     * Default mode for Telepads.
     */
    NETWORK("network", false, true, false),

    /**
     * 1:1 unidirectional fixed destination.
     * Always teleports to same hardcoded location.
     * Used for Nexus zone portals.
     */
    FIXED("fixed", true, false, false),

    /**
     * 1:N with GUI selection.
     * Opens a menu to choose from available destinations.
     * Used for waypoint hubs.
     */
    WAYPOINT("waypoint", false, true, false),

    /**
     * Teleport to dynamic instance dimension.
     * Used for quest instances with DynamicDimensionManager.
     */
    INSTANCE("instance", true, false, false),

    /**
     * Return to saved position.
     * Teleports back to previously saved return point.
     * Used by Nexus return and RecoverySystem.
     */
    RETURN("return", true, false, false),

    /**
     * Party-synchronized teleport.
     * All party members teleport together with countdown.
     * Used for quest start sequences.
     */
    PARTY("party", true, false, true),

    /**
     * Zone-based teleport.
     * Teleports to predefined zone spawn point.
     * Used for Nexus hub navigation.
     */
    ZONE("zone", true, true, false);

    public static final Codec<TransportMode> CODEC = StringRepresentable.fromEnum(TransportMode::values);

    /** Default mode for new Warp Cores. */
    public static final TransportMode DEFAULT = NETWORK;

    private final String name;
    private final boolean requiresDestination;
    private final boolean supportsMultipleDestinations;
    private final boolean requiresParty;

    TransportMode(String name, boolean requiresDestination, boolean supportsMultipleDestinations, boolean requiresParty) {
        this.name = name;
        this.requiresDestination = requiresDestination;
        this.supportsMultipleDestinations = supportsMultipleDestinations;
        this.requiresParty = requiresParty;
    }

    @Override
    @Nonnull
    public String getSerializedName() {
        return Objects.requireNonNull(name);
    }

    /**
     * Returns true if this mode requires a specific destination node.
     */
    public boolean requiresDestination() {
        return requiresDestination;
    }

    /**
     * Returns true if this mode supports multiple destination choices.
     */
    public boolean supportsMultipleDestinations() {
        return supportsMultipleDestinations;
    }

    /**
     * Returns true if this mode requires party context.
     */
    public boolean requiresParty() {
        return requiresParty;
    }

    /**
     * Returns true if this mode uses a network name for grouping.
     */
    public boolean usesNetworkName() {
        return this == NETWORK || this == ZONE;
    }

    /**
     * Returns true if this mode is bidirectional.
     */
    public boolean isBidirectional() {
        return this == LINKED;
    }

    /**
     * Returns true if this mode supports random destination selection.
     */
    public boolean supportsRandomSelection() {
        return this == NETWORK;
    }

    /**
     * Returns true if this mode shows a destination selection GUI.
     */
    public boolean showsSelectionGui() {
        return this == WAYPOINT;
    }

    /**
     * Gets a mode by its serialized name.
     */
    @Nonnull
    public static TransportMode byName(String name) {
        for (TransportMode mode : values()) {
            if (mode.name.equals(name)) {
                return mode;
            }
        }
        return Objects.requireNonNull(DEFAULT);
    }

    /**
     * Gets a mode by index.
     */
    @Nonnull
    public static TransportMode byIndex(int index) {
        TransportMode[] values = values();
        if (index < 0 || index >= values.length) {
            return Objects.requireNonNull(DEFAULT);
        }
        return Objects.requireNonNull(values[index]);
    }

    /**
     * Returns the index of this mode.
     */
    public int getIndex() {
        return ordinal();
    }
}
