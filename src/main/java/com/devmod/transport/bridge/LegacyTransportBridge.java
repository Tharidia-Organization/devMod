package com.devmod.transport.bridge;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.MinecraftServer;

import com.devmod.portal.PortalColor;
import com.devmod.portal.PortalData;
import com.devmod.portal.PortalRegistry;
import com.devmod.transport.TransportColor;
import com.devmod.transport.TransportData;
import com.devmod.transport.TransportEnhancement;
import com.devmod.transport.TransportMode;
import com.devmod.transport.TransportNodeType;
import com.devmod.transport.TransportRegistry;

/**
 * Bridge between legacy Portal system and new unified Transport system.
 *
 * <p>Provides:
 * <ul>
 *   <li>Conversion between PortalData and TransportData</li>
 *   <li>Bi-directional registry synchronization</li>
 *   <li>Gradual migration utilities</li>
 *   <li>Backward compatibility for existing portals</li>
 * </ul>
 *
 * <p><b>Migration Strategy:</b>
 * <ol>
 *   <li>Phase 1: Both registries active, LegacyBridge syncs changes</li>
 *   <li>Phase 2: New portals use TransportRegistry only</li>
 *   <li>Phase 3: Migrate all legacy portals to TransportRegistry</li>
 *   <li>Phase 4: Deprecate PortalRegistry</li>
 * </ol>
 */
public final class LegacyTransportBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyTransportBridge.class);

    public static final LegacyTransportBridge INSTANCE = new LegacyTransportBridge();

    /** Prefix used by telepads in PortalRegistry */
    private static final String TELEPAD_NETWORK_PREFIX = "telepad:";

    /** Whether to sync new registrations to both registries */
    private boolean dualRegistryMode = true;

    private LegacyTransportBridge() {}

    // ============================================================================
    // CONVERSION: PortalData -> TransportData
    // ============================================================================

    /**
     * Converts a legacy PortalData to TransportData.
     *
     * @param portal The legacy portal data
     * @return The converted transport data
     */
    @Nonnull
    public TransportData convertFromPortal(@Nonnull PortalData portal) {
        // Determine transport mode from portal configuration
        TransportMode mode = determineMode(portal);
        TransportNodeType nodeType = determineNodeType(portal);
        TransportColor color = convertColor(portal.color());

        // Build enhancement set from rune type
        EnumSet<TransportEnhancement> enhancements = EnumSet.noneOf(TransportEnhancement.class);
        if (portal.runeType() != null) {
            switch (portal.runeType()) {
                case HASTE -> enhancements.add(TransportEnhancement.HASTE);
                case GATE -> enhancements.add(TransportEnhancement.GATE);
                case ENHANCER -> enhancements.add(TransportEnhancement.ENHANCER);
                case STRONG_ENHANCER -> enhancements.add(TransportEnhancement.STRONG_ENHANCER);
                case INFINITY -> enhancements.add(TransportEnhancement.INFINITY);
            }
        }

        return new TransportData(
            portal.id(),
            nodeType,
            mode,
            color,
            null, // customModel
            portal.frameBlockCount(),
            portal.dim(),
            portal.pos(),
            null, // axis
            portal.linkedId(),
            portal.networkName(),
            mode == TransportMode.NETWORK ? TransportData.NetworkSelectionMode.RANDOM : null,
            portal.fixedDestDim(),
            portal.fixedDestPos(),
            null, // waypointTargets
            enhancements,
            null, // createdTick
            null, // expirationTick
            null, // currentPhase
            null, // snapshotBounds
            null, // snapshotNBT
            TransportData.DEFAULT_CHARGE_TIME,
            TransportData.DEFAULT_COOLDOWN_TIME,
            portal.creatorId(),
            null, // displayName
            null  // description
        );
    }

    /**
     * Determines the transport mode from portal configuration.
     */
    @Nonnull
    private TransportMode determineMode(PortalData portal) {
        if (portal.linkedId() != null) {
            return TransportMode.LINKED;
        }
        if (portal.fixedDestDim() != null && portal.fixedDestPos() != null) {
            return TransportMode.FIXED;
        }
        if (portal.networkName() != null && !portal.networkName().isEmpty()) {
            return TransportMode.NETWORK;
        }
        return TransportMode.LINKED; // Default to linked
    }

    /**
     * Determines the node type from portal configuration.
     */
    @Nonnull
    private TransportNodeType determineNodeType(PortalData portal) {
        String network = portal.networkName();
        if (network != null && network.startsWith(TELEPAD_NETWORK_PREFIX)) {
            return TransportNodeType.TELEPAD;
        }
        if (portal.frameBlockCount() > 0) {
            return TransportNodeType.PORTAL;
        }
        return TransportNodeType.TELEPAD;
    }

    // ============================================================================
    // CONVERSION: TransportData -> PortalData
    // ============================================================================

    /**
     * Converts a TransportData back to legacy PortalData.
     * Used for backward compatibility during migration.
     *
     * @param transport The transport data
     * @return The converted portal data
     */
    @Nonnull
    public PortalData convertToPortal(@Nonnull TransportData transport) {
        PortalColor color = convertColor(transport.color());

        String networkName = null;
        if (transport.mode() == TransportMode.NETWORK && transport.networkName() != null) {
            networkName = transport.networkName();
        }
        if (transport.nodeType() == TransportNodeType.TELEPAD && transport.displayName() != null) {
            networkName = TELEPAD_NETWORK_PREFIX + transport.displayName();
        }

        return new PortalData(
            transport.id(),
            color,
            transport.linkedNodeId(),
            transport.dimension(),
            transport.position(),
            null, // runeType - not reversible
            transport.frameCount(),
            transport.creatorId(),
            transport.fixedDestDim(),
            transport.fixedDestPos(),
            networkName
        );
    }

    // ============================================================================
    // COLOR CONVERSION
    // ============================================================================

    /**
     * Converts PortalColor to TransportColor.
     */
    @Nonnull
    public TransportColor convertColor(@Nonnull PortalColor portalColor) {
        return switch (portalColor) {
            case WHITE -> TransportColor.WHITE;
            case ORANGE -> TransportColor.ORANGE;
            case MAGENTA -> TransportColor.MAGENTA;
            case LIGHT_BLUE -> TransportColor.LIGHT_BLUE;
            case YELLOW -> TransportColor.YELLOW;
            case LIME -> TransportColor.LIME;
            case PINK -> TransportColor.PINK;
            case GRAY -> TransportColor.GRAY;
            case LIGHT_GRAY -> TransportColor.LIGHT_GRAY;
            case CYAN -> TransportColor.CYAN;
            case PURPLE -> TransportColor.PURPLE;
            case BLUE -> TransportColor.BLUE;
            case BROWN -> TransportColor.BROWN;
            case GREEN -> TransportColor.GREEN;
            case RED -> TransportColor.RED;
            case BLACK -> TransportColor.BLACK;
        };
    }

    /**
     * Converts TransportColor to PortalColor.
     */
    @Nonnull
    public PortalColor convertColor(@Nonnull TransportColor transportColor) {
        return switch (transportColor) {
            case WHITE -> PortalColor.WHITE;
            case ORANGE -> PortalColor.ORANGE;
            case MAGENTA -> PortalColor.MAGENTA;
            case LIGHT_BLUE -> PortalColor.LIGHT_BLUE;
            case YELLOW -> PortalColor.YELLOW;
            case LIME -> PortalColor.LIME;
            case PINK -> PortalColor.PINK;
            case GRAY -> PortalColor.GRAY;
            case LIGHT_GRAY -> PortalColor.LIGHT_GRAY;
            case CYAN -> PortalColor.CYAN;
            case PURPLE -> PortalColor.PURPLE;
            case BLUE -> PortalColor.BLUE;
            case BROWN -> PortalColor.BROWN;
            case GREEN -> PortalColor.GREEN;
            case RED -> PortalColor.RED;
            case BLACK -> PortalColor.BLACK;
        };
    }

    // ============================================================================
    // REGISTRY SYNCHRONIZATION
    // ============================================================================

    /**
     * Syncs a portal from PortalRegistry to TransportRegistry.
     *
     * @param portalId The portal UUID
     * @param server The Minecraft server
     * @return true if sync was successful
     */
    public boolean syncToTransportRegistry(@Nonnull UUID portalId, @Nonnull MinecraftServer server) {
        PortalRegistry portalRegistry = PortalRegistry.get(server.overworld());
        TransportRegistry transportRegistry = TransportRegistry.get(server.overworld());

        Optional<PortalData> portalOpt = portalRegistry.get(portalId);
        if (portalOpt.isEmpty()) {
            LOGGER.warn("[LegacyBridge] Portal {} not found in PortalRegistry", portalId);
            return false;
        }

        TransportData transportData = convertFromPortal(portalOpt.get());

        // Check if already exists
        if (transportRegistry.get(portalId).isPresent()) {
            // Update existing
            transportRegistry.update(transportData);
        } else {
            // Register new
            transportRegistry.register(transportData);
        }

        LOGGER.debug("[LegacyBridge] Synced portal {} to TransportRegistry", portalId);
        return true;
    }

    /**
     * Syncs a transport node from TransportRegistry back to PortalRegistry.
     * Used during dual-registry mode.
     *
     * @param nodeId The transport node UUID
     * @param server The Minecraft server
     * @return true if sync was successful
     */
    public boolean syncToPortalRegistry(@Nonnull UUID nodeId, @Nonnull MinecraftServer server) {
        if (!dualRegistryMode) {
            return false;
        }

        TransportRegistry transportRegistry = TransportRegistry.get(server.overworld());
        PortalRegistry portalRegistry = PortalRegistry.get(server.overworld());

        Optional<TransportData> transportOpt = transportRegistry.get(nodeId);
        if (transportOpt.isEmpty()) {
            LOGGER.warn("[LegacyBridge] Transport node {} not found in TransportRegistry", nodeId);
            return false;
        }

        PortalData portalData = convertToPortal(transportOpt.get());
        portalRegistry.register(portalData);

        LOGGER.debug("[LegacyBridge] Synced transport node {} to PortalRegistry", nodeId);
        return true;
    }

    // ============================================================================
    // MIGRATION UTILITIES
    // ============================================================================

    /**
     * Migrates all portals from PortalRegistry to TransportRegistry.
     *
     * @param server The Minecraft server
     * @return Migration result with counts
     */
    @Nonnull
    public MigrationResult migrateAllPortals(@Nonnull MinecraftServer server) {
        PortalRegistry portalRegistry = PortalRegistry.get(server.overworld());
        TransportRegistry transportRegistry = TransportRegistry.get(server.overworld());

        int migrated = 0;
        int failed = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        for (PortalData portal : portalRegistry.getAll()) {
            try {
                // Skip if already in TransportRegistry
                if (transportRegistry.get(portal.id()).isPresent()) {
                    skipped++;
                    continue;
                }

                TransportData transportData = convertFromPortal(portal);
                transportRegistry.register(transportData);
                migrated++;
            } catch (Exception e) {
                failed++;
                errors.add("Portal " + portal.id() + ": " + e.getMessage());
                LOGGER.error("[LegacyBridge] Failed to migrate portal {}", portal.id(), e);
            }
        }

        LOGGER.info("[LegacyBridge] Migration complete: {} migrated, {} skipped, {} failed",
            migrated, skipped, failed);

        return new MigrationResult(migrated, skipped, failed, errors);
    }

    /**
     * Migration result record.
     */
    public record MigrationResult(
        int migrated,
        int skipped,
        int failed,
        List<String> errors
    ) {
        public boolean hasErrors() {
            return failed > 0;
        }

        public int total() {
            return migrated + skipped + failed;
        }
    }

    /**
     * Validates that all portals have been migrated.
     *
     * @param server The Minecraft server
     * @return Validation result
     */
    @Nonnull
    public ValidationResult validateMigration(@Nonnull MinecraftServer server) {
        PortalRegistry portalRegistry = PortalRegistry.get(server.overworld());
        TransportRegistry transportRegistry = TransportRegistry.get(server.overworld());

        int matched = 0;
        int missing = 0;
        List<UUID> missingIds = new ArrayList<>();

        for (PortalData portal : portalRegistry.getAll()) {
            if (transportRegistry.get(portal.id()).isPresent()) {
                matched++;
            } else {
                missing++;
                missingIds.add(portal.id());
            }
        }

        return new ValidationResult(matched, missing, missingIds);
    }

    /**
     * Validation result record.
     */
    public record ValidationResult(
        int matched,
        int missing,
        List<UUID> missingIds
    ) {
        public boolean isComplete() {
            return missing == 0;
        }
    }

    // ============================================================================
    // TELEPAD UTILITIES
    // ============================================================================

    /**
     * Gets telepad network name for a given name.
     */
    @Nonnull
    public String getTelepadNetworkName(@Nonnull String telepadName) {
        return TELEPAD_NETWORK_PREFIX + telepadName;
    }

    /**
     * Checks if a network name is a telepad network.
     */
    public boolean isTelepadNetwork(@Nullable String networkName) {
        return networkName != null && networkName.startsWith(TELEPAD_NETWORK_PREFIX);
    }

    /**
     * Extracts telepad name from network name.
     */
    @Nullable
    public String extractTelepadName(@Nullable String networkName) {
        if (networkName != null && networkName.startsWith(TELEPAD_NETWORK_PREFIX)) {
            return networkName.substring(TELEPAD_NETWORK_PREFIX.length());
        }
        return null;
    }

    // ============================================================================
    // CONFIGURATION
    // ============================================================================

    /**
     * Sets dual-registry mode.
     * When enabled, changes are synced to both registries.
     */
    public void setDualRegistryMode(boolean enabled) {
        this.dualRegistryMode = enabled;
        LOGGER.info("[LegacyBridge] Dual registry mode: {}", enabled);
    }

    /**
     * Returns whether dual-registry mode is enabled.
     */
    public boolean isDualRegistryMode() {
        return dualRegistryMode;
    }
}
