package com.devmod.transport;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import com.devmod.portal.PortalData;

/**
 * Immutable data structure representing a transport node's complete state.
 * This is the unified data model that consolidates PortalData and all transport systems.
 *
 * <p>Supports all transport modes:
 * <ul>
 *   <li><b>Linked:</b> UUID-to-UUID bidirectional linking</li>
 *   <li><b>Network:</b> Named network with random/round-robin/nearest selection</li>
 *   <li><b>Fixed:</b> Unidirectional to hardcoded destination</li>
 *   <li><b>Waypoint:</b> GUI selection from available targets</li>
 *   <li><b>Instance:</b> Dynamic dimension teleport</li>
 *   <li><b>Return:</b> Return to saved position</li>
 *   <li><b>Party:</b> Synchronized group teleport</li>
 *   <li><b>Zone:</b> Nexus zone navigation</li>
 * </ul>
 */
public record TransportData(
    // === Identity ===
    @Nonnull UUID id,
    @Nonnull TransportNodeType nodeType,
    @Nonnull TransportMode mode,

    // === Visual ===
    @Nonnull TransportColor color,
    @Nullable ResourceLocation customModel,
    int frameCount,

    // === Location ===
    @Nullable ResourceLocation dimension,
    @Nullable BlockPos position,
    @Nullable Direction.Axis axis,

    // === Linking (LINKED mode) ===
    @Nullable UUID linkedNodeId,

    // === Network (NETWORK mode) ===
    @Nullable String networkName,
    @Nullable NetworkSelectionMode selectionMode,

    // === Fixed Destination (FIXED mode) ===
    @Nullable ResourceLocation fixedDestDim,
    @Nullable BlockPos fixedDestPos,

    // === Waypoint (WAYPOINT mode) ===
    @Nullable Set<UUID> waypointTargets,

    // === Enhancements ===
    @Nonnull Set<TransportEnhancement> enhancements,

    // === Temporal Data ===
    @Nullable Long createdTick,
    @Nullable Long expirationTick,
    @Nullable TemporalPhase currentPhase,

    // === Snapshot Data ===
    @Nullable BoundingBox snapshotBounds,
    @Nullable CompoundTag snapshotNBT,

    // === Timing (Bibbia Estetica Rule 7) ===
    int chargeTime,     // Default: 40 ticks (2s)
    int cooldownTime,   // Default: 60 ticks (3s)

    // === Ownership ===
    @Nullable UUID creatorId,

    // === Metadata ===
    @Nullable String displayName,
    @Nullable String description
) {
    // ============================================================================
    // NBT TAG NAMES
    // ============================================================================
    private static final String TAG_ID = "id";
    private static final String TAG_NODE_TYPE = "node_type";
    private static final String TAG_MODE = "mode";
    private static final String TAG_COLOR = "color";
    private static final String TAG_CUSTOM_MODEL = "custom_model";
    private static final String TAG_FRAME_COUNT = "frame_count";
    private static final String TAG_DIMENSION = "dimension";
    private static final String TAG_POS_X = "pos_x";
    private static final String TAG_POS_Y = "pos_y";
    private static final String TAG_POS_Z = "pos_z";
    private static final String TAG_AXIS = "axis";
    private static final String TAG_LINKED_ID = "linked_id";
    private static final String TAG_NETWORK = "network";
    private static final String TAG_SELECTION_MODE = "selection_mode";
    private static final String TAG_FIXED_DEST_DIM = "fixed_dest_dim";
    private static final String TAG_FIXED_DEST_X = "fixed_dest_x";
    private static final String TAG_FIXED_DEST_Y = "fixed_dest_y";
    private static final String TAG_FIXED_DEST_Z = "fixed_dest_z";
    private static final String TAG_WAYPOINT_TARGETS = "waypoint_targets";
    private static final String TAG_ENHANCEMENTS = "enhancements";
    private static final String TAG_CREATED_TICK = "created_tick";
    private static final String TAG_EXPIRATION_TICK = "expiration_tick";
    private static final String TAG_PHASE = "phase";
    private static final String TAG_SNAPSHOT_BOUNDS = "snapshot_bounds";
    private static final String TAG_SNAPSHOT_NBT = "snapshot_nbt";
    private static final String TAG_CHARGE_TIME = "charge_time";
    private static final String TAG_COOLDOWN_TIME = "cooldown_time";
    private static final String TAG_CREATOR_ID = "creator_id";
    private static final String TAG_DISPLAY_NAME = "display_name";
    private static final String TAG_DESCRIPTION = "description";

    // ============================================================================
    // DEFAULT TIMING VALUES (Bibbia Estetica Rule 7)
    // ============================================================================
    public static final int DEFAULT_CHARGE_TIME = 40;    // 2 seconds
    public static final int DEFAULT_COOLDOWN_TIME = 60;  // 3 seconds
    public static final int CHARGE_MIN = 0;              // Instant with HASTE
    public static final int CHARGE_MAX = 200;            // 10 seconds max
    public static final int COUNTDOWN_DEFAULT = 200;     // 10 seconds for party
    public static final int ARRIVAL_TIMEOUT = 600;       // 30 seconds

    // ============================================================================
    // INNER ENUMS
    // ============================================================================

    /**
     * Network destination selection modes.
     */
    public enum NetworkSelectionMode {
        RANDOM,       // Random destination
        ROUND_ROBIN,  // Cycle through destinations
        NEAREST,      // Closest destination
        MANUAL;       // Player selects via GUI

        public static NetworkSelectionMode byIndex(int index) {
            NetworkSelectionMode[] values = values();
            if (index < 0 || index >= values.length) {
                return RANDOM;
            }
            return values[index];
        }
    }

    /**
     * Phases for temporal (temporary) transport nodes.
     */
    public enum TemporalPhase {
        CALIBRATING,  // Initial setup (5s)
        ACTIVE,       // Fully operational
        COLLAPSING,   // Warning before expiration (10s)
        EXPIRED;      // Marked for cleanup

        public static TemporalPhase byIndex(int index) {
            TemporalPhase[] values = values();
            if (index < 0 || index >= values.length) {
                return ACTIVE;
            }
            return values[index];
        }
    }

    // ============================================================================
    // QUERY METHODS
    // ============================================================================

    public boolean isTemporary() {
        return enhancements.contains(TransportEnhancement.TEMPORAL);
    }

    public boolean hasSnapshot() {
        return enhancements.contains(TransportEnhancement.SNAPSHOT);
    }

    public boolean isInstant() {
        return enhancements.contains(TransportEnhancement.HASTE)
            || enhancements.contains(TransportEnhancement.FLUX_CAPACITOR)
            || chargeTime == 0;
    }

    public boolean canCrossDimension() {
        return enhancements.contains(TransportEnhancement.GATE);
    }

    public boolean isLinked() {
        return mode == TransportMode.LINKED && linkedNodeId != null;
    }

    public boolean hasNetwork() {
        return mode == TransportMode.NETWORK && networkName != null && !networkName.isEmpty();
    }

    public boolean hasFixedDestination() {
        return mode == TransportMode.FIXED && fixedDestDim != null && fixedDestPos != null;
    }

    public boolean hasPosition() {
        return position != null;
    }

    public boolean hasDimension() {
        return dimension != null;
    }

    public Optional<BlockPos> getPosition() {
        return Optional.ofNullable(position);
    }

    public Optional<ResourceLocation> getDimension() {
        return Optional.ofNullable(dimension);
    }

    public Optional<UUID> getLinkedNodeId() {
        return Optional.ofNullable(linkedNodeId);
    }

    public Optional<String> getNetworkName() {
        return Optional.ofNullable(networkName);
    }

    public Optional<UUID> getCreatorId() {
        return Optional.ofNullable(creatorId);
    }

    /**
     * Calculates effective range considering all range enhancements.
     */
    public int getEffectiveRange() {
        if (enhancements.contains(TransportEnhancement.INFINITY)) {
            return Integer.MAX_VALUE;
        }

        int range = 100; // Base range

        for (TransportEnhancement enhancement : enhancements) {
            int bonus = enhancement.getRangeBonus();
            if (bonus == Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            range += bonus;
        }

        return range;
    }

    /**
     * Calculates effective charge time considering enhancements.
     */
    public int getEffectiveChargeTime() {
        if (isInstant()) {
            return CHARGE_MIN;
        }
        return Math.min(Math.max(chargeTime, CHARGE_MIN), CHARGE_MAX);
    }

    // ============================================================================
    // BUILDER METHODS (Immutable Updates)
    // ============================================================================

    public TransportData withMode(TransportMode newMode) {
        return new TransportData(id, nodeType, Objects.requireNonNull(newMode), color, customModel, frameCount,
            dimension, position, axis, linkedNodeId, networkName, selectionMode,
            fixedDestDim, fixedDestPos, waypointTargets, enhancements,
            createdTick, expirationTick, currentPhase, snapshotBounds, snapshotNBT,
            chargeTime, cooldownTime, creatorId, displayName, description);
    }

    public TransportData withColor(TransportColor newColor) {
        return new TransportData(id, nodeType, mode, Objects.requireNonNull(newColor), customModel, frameCount,
            dimension, position, axis, linkedNodeId, networkName, selectionMode,
            fixedDestDim, fixedDestPos, waypointTargets, enhancements,
            createdTick, expirationTick, currentPhase, snapshotBounds, snapshotNBT,
            chargeTime, cooldownTime, creatorId, displayName, description);
    }

    public TransportData withPosition(@Nullable ResourceLocation dim, @Nullable BlockPos pos) {
        return new TransportData(id, nodeType, mode, color, customModel, frameCount,
            dim, pos, axis, linkedNodeId, networkName, selectionMode,
            fixedDestDim, fixedDestPos, waypointTargets, enhancements,
            createdTick, expirationTick, currentPhase, snapshotBounds, snapshotNBT,
            chargeTime, cooldownTime, creatorId, displayName, description);
    }

    public TransportData withFrameCount(int newFrameCount) {
        return new TransportData(id, nodeType, mode, color, customModel, newFrameCount,
            dimension, position, axis, linkedNodeId, networkName, selectionMode,
            fixedDestDim, fixedDestPos, waypointTargets, enhancements,
            createdTick, expirationTick, currentPhase, snapshotBounds, snapshotNBT,
            chargeTime, cooldownTime, creatorId, displayName, description);
    }

    public TransportData withEnhancement(TransportEnhancement enhancement) {
        Set<TransportEnhancement> newEnhancements = EnumSet.copyOf(enhancements);
        newEnhancements.add(enhancement);
        return new TransportData(id, nodeType, mode, color, customModel, frameCount,
            dimension, position, axis, linkedNodeId, networkName, selectionMode,
            fixedDestDim, fixedDestPos, waypointTargets, newEnhancements,
            createdTick, expirationTick, currentPhase, snapshotBounds, snapshotNBT,
            chargeTime, cooldownTime, creatorId, displayName, description);
    }

    public TransportData withoutEnhancement(TransportEnhancement enhancement) {
        Set<TransportEnhancement> newEnhancements = EnumSet.copyOf(enhancements);
        newEnhancements.remove(enhancement);
        return new TransportData(id, nodeType, mode, color, customModel, frameCount,
            dimension, position, axis, linkedNodeId, networkName, selectionMode,
            fixedDestDim, fixedDestPos, waypointTargets, newEnhancements,
            createdTick, expirationTick, currentPhase, snapshotBounds, snapshotNBT,
            chargeTime, cooldownTime, creatorId, displayName, description);
    }

    public TransportData withEnhancements(Set<TransportEnhancement> newEnhancements) {
        return new TransportData(id, nodeType, mode, color, customModel, frameCount,
            dimension, position, axis, linkedNodeId, networkName, selectionMode,
            fixedDestDim, fixedDestPos, waypointTargets, Objects.requireNonNull(EnumSet.copyOf(newEnhancements)),
            createdTick, expirationTick, currentPhase, snapshotBounds, snapshotNBT,
            chargeTime, cooldownTime, creatorId, displayName, description);
    }

    public TransportData linkTo(@Nonnull UUID targetId) {
        return new TransportData(id, nodeType, TransportMode.LINKED, color, customModel, frameCount,
            dimension, position, axis, Objects.requireNonNull(targetId), null, null,
            null, null, null, enhancements,
            createdTick, expirationTick, currentPhase, snapshotBounds, snapshotNBT,
            chargeTime, cooldownTime, creatorId, displayName, description);
    }

    public TransportData unlink() {
        return new TransportData(id, nodeType, mode, color, customModel, frameCount,
            dimension, position, axis, null, networkName, selectionMode,
            fixedDestDim, fixedDestPos, waypointTargets, enhancements,
            createdTick, expirationTick, currentPhase, snapshotBounds, snapshotNBT,
            chargeTime, cooldownTime, creatorId, displayName, description);
    }

    public TransportData withNetwork(@Nullable String network, @Nullable NetworkSelectionMode selection) {
        return new TransportData(id, nodeType, TransportMode.NETWORK, color, customModel, frameCount,
            dimension, position, axis, null, network, selection != null ? selection : NetworkSelectionMode.RANDOM,
            null, null, null, enhancements,
            createdTick, expirationTick, currentPhase, snapshotBounds, snapshotNBT,
            chargeTime, cooldownTime, creatorId, displayName, description);
    }

    public TransportData withFixedDestination(@Nonnull ResourceLocation destDim, @Nonnull BlockPos destPos) {
        return new TransportData(id, nodeType, TransportMode.FIXED, color, customModel, frameCount,
            dimension, position, axis, null, null, null,
            Objects.requireNonNull(destDim), Objects.requireNonNull(destPos), null, enhancements,
            createdTick, expirationTick, currentPhase, snapshotBounds, snapshotNBT,
            chargeTime, cooldownTime, creatorId, displayName, description);
    }

    public TransportData withTemporalData(long created, long expiration, @Nonnull TemporalPhase phase) {
        Set<TransportEnhancement> newEnhancements = EnumSet.copyOf(enhancements);
        newEnhancements.add(TransportEnhancement.TEMPORAL);
        return new TransportData(id, nodeType, mode, color, customModel, frameCount,
            dimension, position, axis, linkedNodeId, networkName, selectionMode,
            fixedDestDim, fixedDestPos, waypointTargets, newEnhancements,
            created, expiration, phase, snapshotBounds, snapshotNBT,
            chargeTime, cooldownTime, creatorId, displayName, description);
    }

    public TransportData withPhase(@Nonnull TemporalPhase phase) {
        return new TransportData(id, nodeType, mode, color, customModel, frameCount,
            dimension, position, axis, linkedNodeId, networkName, selectionMode,
            fixedDestDim, fixedDestPos, waypointTargets, enhancements,
            createdTick, expirationTick, phase, snapshotBounds, snapshotNBT,
            chargeTime, cooldownTime, creatorId, displayName, description);
    }

    public TransportData withSnapshot(@Nonnull BoundingBox bounds, @Nonnull CompoundTag snapshot) {
        Set<TransportEnhancement> newEnhancements = EnumSet.copyOf(enhancements);
        newEnhancements.add(TransportEnhancement.SNAPSHOT);
        return new TransportData(id, nodeType, mode, color, customModel, frameCount,
            dimension, position, axis, linkedNodeId, networkName, selectionMode,
            fixedDestDim, fixedDestPos, waypointTargets, newEnhancements,
            createdTick, expirationTick, currentPhase, bounds, snapshot,
            chargeTime, cooldownTime, creatorId, displayName, description);
    }

    public TransportData withCreator(@Nullable UUID creator) {
        return new TransportData(id, nodeType, mode, color, customModel, frameCount,
            dimension, position, axis, linkedNodeId, networkName, selectionMode,
            fixedDestDim, fixedDestPos, waypointTargets, enhancements,
            createdTick, expirationTick, currentPhase, snapshotBounds, snapshotNBT,
            chargeTime, cooldownTime, creator, displayName, description);
    }

    public TransportData withDisplayName(@Nullable String name) {
        return new TransportData(id, nodeType, mode, color, customModel, frameCount,
            dimension, position, axis, linkedNodeId, networkName, selectionMode,
            fixedDestDim, fixedDestPos, waypointTargets, enhancements,
            createdTick, expirationTick, currentPhase, snapshotBounds, snapshotNBT,
            chargeTime, cooldownTime, creatorId, name, description);
    }

    public TransportData withTiming(int charge, int cooldown) {
        return new TransportData(id, nodeType, mode, color, customModel, frameCount,
            dimension, position, axis, linkedNodeId, networkName, selectionMode,
            fixedDestDim, fixedDestPos, waypointTargets, enhancements,
            createdTick, expirationTick, currentPhase, snapshotBounds, snapshotNBT,
            charge, cooldown, creatorId, displayName, description);
    }

    public TransportData withChargeTime(int charge) {
        return new TransportData(id, nodeType, mode, color, customModel, frameCount,
            dimension, position, axis, linkedNodeId, networkName, selectionMode,
            fixedDestDim, fixedDestPos, waypointTargets, enhancements,
            createdTick, expirationTick, currentPhase, snapshotBounds, snapshotNBT,
            charge, cooldownTime, creatorId, displayName, description);
    }

    public TransportData withCooldownTime(int cooldown) {
        return new TransportData(id, nodeType, mode, color, customModel, frameCount,
            dimension, position, axis, linkedNodeId, networkName, selectionMode,
            fixedDestDim, fixedDestPos, waypointTargets, enhancements,
            createdTick, expirationTick, currentPhase, snapshotBounds, snapshotNBT,
            chargeTime, cooldown, creatorId, displayName, description);
    }

    public TransportData withCreatorId(@Nullable UUID creator) {
        return new TransportData(id, nodeType, mode, color, customModel, frameCount,
            dimension, position, axis, linkedNodeId, networkName, selectionMode,
            fixedDestDim, fixedDestPos, waypointTargets, enhancements,
            createdTick, expirationTick, currentPhase, snapshotBounds, snapshotNBT,
            chargeTime, cooldownTime, creator, displayName, description);
    }

    // ============================================================================
    // FACTORY METHODS
    // ============================================================================

    /**
     * Creates a new portal node (legacy portal style).
     */
    public static TransportData createPortal(@Nonnull TransportColor color, @Nullable ResourceLocation dim, @Nullable BlockPos pos) {
        return new TransportData(
            Objects.requireNonNull(UUID.randomUUID()), TransportNodeType.PORTAL, TransportMode.LINKED,
            color, null, 0, dim, pos, null, null, null, null, null, null, null,
            Objects.requireNonNull(EnumSet.of(TransportEnhancement.CHARGED)),
            null, null, null, null, null,
            80, 100, null, null, null  // Portal uses 80 tick charge, 100 cooldown
        );
    }

    /**
     * Creates a new telepad/Warp Core node.
     */
    public static TransportData createTelepad(@Nonnull String networkName, @Nonnull ResourceLocation dim, @Nonnull BlockPos pos) {
        return new TransportData(
            Objects.requireNonNull(UUID.randomUUID()), TransportNodeType.TELEPAD, TransportMode.NETWORK,
            TransportColor.CYAN, null, 0, dim, pos, null, null,
            networkName, NetworkSelectionMode.RANDOM,
            null, null, null,
            Objects.requireNonNull(EnumSet.of(TransportEnhancement.CHARGED)),
            null, null, null, null, null,
            DEFAULT_CHARGE_TIME, DEFAULT_COOLDOWN_TIME, null, null, null
        );
    }

    /**
     * Creates a new telepad/Warp Core node with specific ID and configuration.
     */
    public static TransportData createTelepad(
        @Nonnull UUID nodeId,
        @Nonnull TransportColor color,
        @Nonnull ResourceLocation dim,
        @Nonnull BlockPos pos,
        @Nonnull String networkName,
        @Nonnull NetworkSelectionMode selectionMode
    ) {
        return new TransportData(
            nodeId, TransportNodeType.TELEPAD, TransportMode.NETWORK,
            color, null, 0, dim, pos, null, null,
            networkName, selectionMode,
            null, null, null,
            Objects.requireNonNull(EnumSet.of(TransportEnhancement.CHARGED)),
            null, null, null, null, null,
            DEFAULT_CHARGE_TIME, DEFAULT_COOLDOWN_TIME, null, null, null
        );
    }

    /**
     * Creates a new rift gate (temporary portal).
     */
    public static TransportData createRiftGate(
        @Nonnull UUID nodeId,
        @Nonnull ResourceLocation dim, @Nonnull BlockPos pos,
        @Nonnull ResourceLocation destDim, @Nonnull BlockPos destPos,
        long createdTick, long durationTicks
    ) {
        return new TransportData(
            nodeId, TransportNodeType.RIFT_GATE, TransportMode.FIXED,
            TransportColor.PURPLE, null, 0, dim, pos, null, null, null, null,
            destDim, destPos, null,
            Objects.requireNonNull(EnumSet.of(TransportEnhancement.TEMPORAL, TransportEnhancement.SNAPSHOT,
                       TransportEnhancement.HASTE, TransportEnhancement.PROTECTED, TransportEnhancement.BOSSBAR)),
            createdTick, createdTick + durationTicks, TemporalPhase.CALIBRATING,
            null, null,
            0, 100, null, null, null
        );
    }

    /**
     * Creates a new rift gate (temporary portal).
     */
    public static TransportData createRiftGate(
        @Nonnull ResourceLocation dim, @Nonnull BlockPos pos,
        @Nonnull ResourceLocation destDim, @Nonnull BlockPos destPos,
        long createdTick, long durationTicks
    ) {
        return createRiftGate(Objects.requireNonNull(UUID.randomUUID()), dim, pos, destDim, destPos, createdTick, durationTicks);
    }

    /**
     * Creates a new waypoint node.
     */
    public static TransportData createWaypoint(@Nonnull String name, @Nonnull ResourceLocation dim, @Nonnull BlockPos pos) {
        return new TransportData(
            Objects.requireNonNull(UUID.randomUUID()), TransportNodeType.WAYPOINT, TransportMode.WAYPOINT,
            TransportColor.WHITE, null, 0, dim, pos, null, null, null, null,
            null, null, new HashSet<>(),
            Objects.requireNonNull(EnumSet.noneOf(TransportEnhancement.class)),
            null, null, null, null, null,
            0, 20, null, name, null
        );
    }

    /**
     * Creates a new zone portal (Nexus style).
     */
    public static TransportData createZone(
        @Nonnull TransportColor color,
        @Nonnull ResourceLocation sourceDim, @Nonnull BlockPos sourcePos,
        @Nonnull ResourceLocation destDim, @Nonnull BlockPos destPos,
        @Nullable String zoneName
    ) {
        return new TransportData(
            Objects.requireNonNull(UUID.randomUUID()), TransportNodeType.ZONE, TransportMode.FIXED,
            color, null, 8, sourceDim, sourcePos, null, null, null, null,
            destDim, destPos, null,
            Objects.requireNonNull(EnumSet.of(TransportEnhancement.HASTE, TransportEnhancement.PROTECTED)),
            null, null, null, null, null,
            0, 60, null, zoneName, null
        );
    }

    /**
     * Creates a recovery point (non-physical).
     */
    public static TransportData createRecoveryPoint(@Nonnull UUID playerId, @Nonnull ResourceLocation dim, @Nonnull BlockPos pos) {
        return new TransportData(
            Objects.requireNonNull(UUID.randomUUID()), TransportNodeType.RECOVERY_POINT, TransportMode.RETURN,
            TransportColor.WHITE, null, 0, dim, pos, null, null, null, null,
            null, null, null,
            Objects.requireNonNull(EnumSet.of(TransportEnhancement.RECOVERY)),
            null, null, null, null, null,
            0, 0, playerId, null, null
        );
    }

    // ============================================================================
    // MIGRATION FROM LEGACY PORTAL DATA
    // ============================================================================

    /**
     * Converts legacy PortalData to TransportData.
     */
    @Nonnull
    public static TransportData fromPortalData(@Nonnull PortalData legacy) {
        TransportMode mode;
        if (legacy.hasFixedDestination()) {
            mode = TransportMode.FIXED;
        } else if (legacy.hasNetwork()) {
            mode = TransportMode.NETWORK;
        } else {
            mode = TransportMode.LINKED;
        }

        Set<TransportEnhancement> enhancements = EnumSet.of(TransportEnhancement.CHARGED);
        enhancements.add(TransportEnhancement.LEGACY_MODE);

        return new TransportData(
            legacy.id(),
            TransportNodeType.PORTAL,
            mode,
            TransportColor.fromLegacy(legacy.color()),
            null,
            legacy.frameBlockCount(),
            legacy.dimension().orElse(null),
            legacy.position().orElse(null),
            null,
            legacy.linkedPortalId().orElse(null),
            legacy.networkName(),
            NetworkSelectionMode.RANDOM,
            legacy.fixedDestinationDimension().orElse(null),
            legacy.fixedDestination().orElse(null),
            null,
            enhancements,
            null, null, null, null, null,
            80, 100,
            legacy.creator().orElse(null),
            null, null
        );
    }

    // ============================================================================
    // NBT SERIALIZATION
    // ============================================================================

    /**
     * Serializes this transport data to NBT.
     */
    @Nonnull
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        // Identity
        tag.putUUID(TAG_ID, id);
        tag.putString(TAG_NODE_TYPE, nodeType.getSerializedName());
        tag.putString(TAG_MODE, mode.getSerializedName());

        // Visual
        tag.putInt(TAG_COLOR, color.getIndex());
        if (customModel != null) {
            tag.putString(TAG_CUSTOM_MODEL, Objects.requireNonNull(customModel.toString()));
        }
        tag.putInt(TAG_FRAME_COUNT, frameCount);

        // Location
        if (dimension != null) {
            tag.putString(TAG_DIMENSION, Objects.requireNonNull(dimension.toString()));
        }
        if (position != null) {
            BlockPos pos = Objects.requireNonNull(position);
            tag.putInt(TAG_POS_X, pos.getX());
            tag.putInt(TAG_POS_Y, pos.getY());
            tag.putInt(TAG_POS_Z, pos.getZ());
        }
        if (axis != null) {
            tag.putString(TAG_AXIS, Objects.requireNonNull(axis.getName()));
        }

        // Linking
        if (linkedNodeId != null) {
            tag.putUUID(TAG_LINKED_ID, linkedNodeId);
        }

        // Network
        if (networkName != null) {
            tag.putString(TAG_NETWORK, networkName);
        }
        if (selectionMode != null) {
            tag.putInt(TAG_SELECTION_MODE, selectionMode.ordinal());
        }

        // Fixed destination
        if (fixedDestDim != null) {
            tag.putString(TAG_FIXED_DEST_DIM, Objects.requireNonNull(fixedDestDim.toString()));
        }
        if (fixedDestPos != null) {
            BlockPos destPos = Objects.requireNonNull(fixedDestPos);
            tag.putInt(TAG_FIXED_DEST_X, destPos.getX());
            tag.putInt(TAG_FIXED_DEST_Y, destPos.getY());
            tag.putInt(TAG_FIXED_DEST_Z, destPos.getZ());
        }

        // Waypoint targets
        if (waypointTargets != null && !waypointTargets.isEmpty()) {
            ListTag targetList = new ListTag();
            for (UUID target : Objects.requireNonNull(waypointTargets)) {
                targetList.add(StringTag.valueOf(Objects.requireNonNull(target.toString())));
            }
            tag.put(TAG_WAYPOINT_TARGETS, targetList);
        }

        // Enhancements
        ListTag enhancementList = new ListTag();
        for (TransportEnhancement enhancement : enhancements) {
            enhancementList.add(StringTag.valueOf(enhancement.getSerializedName()));
        }
        tag.put(TAG_ENHANCEMENTS, enhancementList);

        // Temporal
        if (createdTick != null) {
            tag.putLong(TAG_CREATED_TICK, createdTick);
        }
        if (expirationTick != null) {
            tag.putLong(TAG_EXPIRATION_TICK, expirationTick);
        }
        if (currentPhase != null) {
            tag.putInt(TAG_PHASE, currentPhase.ordinal());
        }

        // Snapshot
        if (snapshotBounds != null) {
            BoundingBox bounds = Objects.requireNonNull(snapshotBounds);
            CompoundTag boundsTag = new CompoundTag();
            boundsTag.putInt("minX", bounds.minX());
            boundsTag.putInt("minY", bounds.minY());
            boundsTag.putInt("minZ", bounds.minZ());
            boundsTag.putInt("maxX", bounds.maxX());
            boundsTag.putInt("maxY", bounds.maxY());
            boundsTag.putInt("maxZ", bounds.maxZ());
            tag.put(TAG_SNAPSHOT_BOUNDS, boundsTag);
        }
        if (snapshotNBT != null) {
            tag.put(TAG_SNAPSHOT_NBT, snapshotNBT);
        }

        // Timing
        tag.putInt(TAG_CHARGE_TIME, chargeTime);
        tag.putInt(TAG_COOLDOWN_TIME, cooldownTime);

        // Ownership
        if (creatorId != null) {
            tag.putUUID(TAG_CREATOR_ID, creatorId);
        }

        // Metadata
        if (displayName != null) {
            tag.putString(TAG_DISPLAY_NAME, displayName);
        }
        if (description != null) {
            tag.putString(TAG_DESCRIPTION, description);
        }

        return tag;
    }

    /**
     * Deserializes transport data from NBT.
     */
    @Nonnull
    public static TransportData load(@Nonnull CompoundTag tag) {
        // Identity
        UUID id = tag.getUUID(TAG_ID);
        TransportNodeType nodeType = TransportNodeType.byName(tag.getString(TAG_NODE_TYPE));
        TransportMode mode = TransportMode.byName(tag.getString(TAG_MODE));

        // Visual
        TransportColor color = TransportColor.byIndex(tag.getInt(TAG_COLOR));
        ResourceLocation customModel = tag.contains(TAG_CUSTOM_MODEL)
            ? ResourceLocation.parse(Objects.requireNonNull(tag.getString(TAG_CUSTOM_MODEL))) : null;
        int frameCount = tag.getInt(TAG_FRAME_COUNT);

        // Location
        ResourceLocation dimension = tag.contains(TAG_DIMENSION)
            ? ResourceLocation.parse(Objects.requireNonNull(tag.getString(TAG_DIMENSION))) : null;
        BlockPos position = tag.contains(TAG_POS_X)
            ? new BlockPos(tag.getInt(TAG_POS_X), tag.getInt(TAG_POS_Y), tag.getInt(TAG_POS_Z)) : null;
        Direction.Axis axis = tag.contains(TAG_AXIS)
            ? Direction.Axis.byName(Objects.requireNonNull(tag.getString(TAG_AXIS))) : null;

        // Linking
        UUID linkedNodeId = tag.contains(TAG_LINKED_ID) ? tag.getUUID(TAG_LINKED_ID) : null;

        // Network
        String networkName = tag.contains(TAG_NETWORK) ? tag.getString(TAG_NETWORK) : null;
        NetworkSelectionMode selectionMode = tag.contains(TAG_SELECTION_MODE)
            ? NetworkSelectionMode.byIndex(tag.getInt(TAG_SELECTION_MODE)) : null;

        // Fixed destination
        ResourceLocation fixedDestDim = tag.contains(TAG_FIXED_DEST_DIM)
            ? ResourceLocation.parse(Objects.requireNonNull(tag.getString(TAG_FIXED_DEST_DIM))) : null;
        BlockPos fixedDestPos = tag.contains(TAG_FIXED_DEST_X)
            ? new BlockPos(tag.getInt(TAG_FIXED_DEST_X), tag.getInt(TAG_FIXED_DEST_Y), tag.getInt(TAG_FIXED_DEST_Z)) : null;

        // Waypoint targets
        Set<UUID> waypointTargets = null;
        if (tag.contains(TAG_WAYPOINT_TARGETS)) {
            waypointTargets = new HashSet<>();
            ListTag targetList = tag.getList(TAG_WAYPOINT_TARGETS, Tag.TAG_STRING);
            for (int i = 0; i < targetList.size(); i++) {
                waypointTargets.add(UUID.fromString(targetList.getString(i)));
            }
        }

        // Enhancements
        Set<TransportEnhancement> enhancements = EnumSet.noneOf(TransportEnhancement.class);
        if (tag.contains(TAG_ENHANCEMENTS)) {
            ListTag enhancementList = tag.getList(TAG_ENHANCEMENTS, Tag.TAG_STRING);
            for (int i = 0; i < enhancementList.size(); i++) {
                enhancements.add(TransportEnhancement.byName(enhancementList.getString(i)));
            }
        }

        // Temporal
        Long createdTick = tag.contains(TAG_CREATED_TICK) ? tag.getLong(TAG_CREATED_TICK) : null;
        Long expirationTick = tag.contains(TAG_EXPIRATION_TICK) ? tag.getLong(TAG_EXPIRATION_TICK) : null;
        TemporalPhase currentPhase = tag.contains(TAG_PHASE)
            ? TemporalPhase.byIndex(tag.getInt(TAG_PHASE)) : null;

        // Snapshot
        BoundingBox snapshotBounds = null;
        if (tag.contains(TAG_SNAPSHOT_BOUNDS)) {
            CompoundTag boundsTag = tag.getCompound(TAG_SNAPSHOT_BOUNDS);
            snapshotBounds = new BoundingBox(
                boundsTag.getInt("minX"), boundsTag.getInt("minY"), boundsTag.getInt("minZ"),
                boundsTag.getInt("maxX"), boundsTag.getInt("maxY"), boundsTag.getInt("maxZ")
            );
        }
        CompoundTag snapshotNBT = tag.contains(TAG_SNAPSHOT_NBT) ? tag.getCompound(TAG_SNAPSHOT_NBT) : null;

        // Timing
        int chargeTime = tag.contains(TAG_CHARGE_TIME) ? tag.getInt(TAG_CHARGE_TIME) : DEFAULT_CHARGE_TIME;
        int cooldownTime = tag.contains(TAG_COOLDOWN_TIME) ? tag.getInt(TAG_COOLDOWN_TIME) : DEFAULT_COOLDOWN_TIME;

        // Ownership
        UUID creatorId = tag.contains(TAG_CREATOR_ID) ? tag.getUUID(TAG_CREATOR_ID) : null;

        // Metadata
        String displayName = tag.contains(TAG_DISPLAY_NAME) ? tag.getString(TAG_DISPLAY_NAME) : null;
        String description = tag.contains(TAG_DESCRIPTION) ? tag.getString(TAG_DESCRIPTION) : null;

        return new TransportData(
            Objects.requireNonNull(id), Objects.requireNonNull(nodeType), Objects.requireNonNull(mode),
            Objects.requireNonNull(color), customModel, frameCount,
            dimension, position, axis, linkedNodeId, networkName, selectionMode,
            fixedDestDim, fixedDestPos, waypointTargets, Objects.requireNonNull(enhancements),
            createdTick, expirationTick, currentPhase, snapshotBounds, snapshotNBT,
            chargeTime, cooldownTime, creatorId, displayName, description
        );
    }
}
