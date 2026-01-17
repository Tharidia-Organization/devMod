package com.devmod.transport;

import java.util.Objects;

import javax.annotation.Nonnull;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/**
 * Types of transport nodes in the unified transport system.
 * Each type represents a different physical manifestation or use case.
 *
 * <p>Node types determine:
 * <ul>
 *   <li>Whether a physical block exists in the world</li>
 *   <li>What modes are available for the node</li>
 *   <li>Whether the node supports modules/enhancements</li>
 *   <li>Visual representation and behavior</li>
 * </ul>
 */
public enum TransportNodeType implements StringRepresentable {
    /**
     * Multi-block portal with frame.
     * Legacy CustomPortalBlock or Warp Core with frame segments.
     */
    PORTAL("portal", true, true, true),

    /**
     * Single block teleport pad (Warp Core base).
     * Evolved from TelepadBlock.
     */
    TELEPAD("telepad", true, true, true),

    /**
     * Temporary spawned portal structure.
     * Evolved from RiftStampManager.
     */
    RIFT_GATE("rift_gate", true, false, false),

    /**
     * Non-physical marker point for waypoint selection.
     * Used for GUI-based destination selection.
     */
    WAYPOINT("waypoint", false, false, false),

    /**
     * Nexus zone entry point.
     * Pre-configured portals in hub areas.
     */
    ZONE("zone", true, false, false),

    /**
     * Dynamic dimension entry gate.
     * Used for quest instances.
     */
    INSTANCE_GATE("instance", true, false, false),

    /**
     * Recovery teleport target.
     * Non-physical, used by RecoverySystem.
     */
    RECOVERY_POINT("recovery", false, false, false),

    /**
     * Respawn anchor integration point.
     * Links to vanilla respawn anchor mechanics.
     */
    ANCHOR("anchor", true, false, false),

    /**
     * Non-physical programmatic node.
     * Used for API/command-based teleportation.
     */
    VIRTUAL("virtual", false, false, false);

    public static final Codec<TransportNodeType> CODEC = StringRepresentable.fromEnum(TransportNodeType::values);

    private final String name;
    private final boolean hasPhysicalBlock;
    private final boolean supportsLinking;
    private final boolean supportsModules;

    TransportNodeType(String name, boolean hasPhysicalBlock, boolean supportsLinking, boolean supportsModules) {
        this.name = name;
        this.hasPhysicalBlock = hasPhysicalBlock;
        this.supportsLinking = supportsLinking;
        this.supportsModules = supportsModules;
    }

    @Override
    @Nonnull
    public String getSerializedName() {
        return Objects.requireNonNull(name);
    }

    /**
     * Returns true if this node type has a physical block in the world.
     */
    public boolean hasPhysicalBlock() {
        return hasPhysicalBlock;
    }

    /**
     * Returns true if this node type supports UUID-to-UUID linking.
     */
    public boolean supportsLinking() {
        return supportsLinking;
    }

    /**
     * Returns true if this node type supports Warp Core modules.
     */
    public boolean supportsModules() {
        return supportsModules;
    }

    /**
     * Returns true if this node type is temporary (has expiration).
     */
    public boolean isTemporary() {
        return this == RIFT_GATE;
    }

    /**
     * Returns true if this node type is part of the Nexus system.
     */
    public boolean isNexusRelated() {
        return this == ZONE || this == INSTANCE_GATE;
    }

    /**
     * Returns true if this node type persists across server restarts.
     */
    public boolean isPersistent() {
        return this != RIFT_GATE && this != VIRTUAL && this != RECOVERY_POINT;
    }

    /**
     * Gets a node type by its serialized name.
     */
    @Nonnull
    public static TransportNodeType byName(String name) {
        for (TransportNodeType type : values()) {
            if (type.name.equals(name)) {
                return type;
            }
        }
        return TELEPAD;
    }

    /**
     * Gets a node type by index.
     */
    @Nonnull
    public static TransportNodeType byIndex(int index) {
        TransportNodeType[] values = values();
        if (index < 0 || index >= values.length) {
            return Objects.requireNonNull(TELEPAD);
        }
        return Objects.requireNonNull(values[index]);
    }

    /**
     * Returns the index of this type.
     */
    public int getIndex() {
        return ordinal();
    }
}
