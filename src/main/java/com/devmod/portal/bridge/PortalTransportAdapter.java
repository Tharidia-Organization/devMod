package com.devmod.portal.bridge;

import java.util.EnumSet;
import java.util.Set;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import com.devmod.portal.PortalData;
import com.devmod.portal.PortalRegistry;
import com.devmod.portal.PortalRuneEffects;
import com.devmod.transport.TransportColor;
import com.devmod.transport.TransportData;
import com.devmod.transport.TransportEnhancement;
import com.devmod.transport.TransportMode;
import com.devmod.transport.TransportNodeType;
import com.devmod.transport.TransportRegistry;
import com.devmod.transport.executor.TransportExecutor;

/**
 * Adapter that wraps portal teleportation logic to route through
 * {@link TransportExecutor}.
 *
 * <p>This adapter provides the unification layer between the legacy portal
 * system ({@link PortalRegistry}/{@link PortalData}) and the unified transport
 * system ({@link TransportRegistry}/{@link TransportData}).
 *
 * <p><b>Benefits of routing through TransportExecutor:</b>
 * <ul>
 *   <li>Unified cooldown tracking across portals and telepads</li>
 *   <li>Consistent departure/arrival effects and sounds</li>
 *   <li>Unified telemetry and event logging</li>
 *   <li>Transport overlay HUD integration</li>
 * </ul>
 *
 * <p><b>Backward Compatibility:</b>
 * Existing portal configs, colors, rune effects, and linking all continue to
 * work unchanged. The adapter converts PortalData to TransportData on-the-fly
 * for the executor, without modifying the underlying PortalRegistry.
 *
 * @see TransportExecutor
 * @see com.devmod.transport.bridge.LegacyTransportBridge for full migration
 */
public final class PortalTransportAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(PortalTransportAdapter.class);

    public static final PortalTransportAdapter INSTANCE = new PortalTransportAdapter();

    private PortalTransportAdapter() {}

    // ============================================================================
    // TELEPORTATION VIA TRANSPORT EXECUTOR
    // ============================================================================

    /**
     * Attempts to teleport an entity through a portal using the TransportExecutor.
     *
     * <p>Converts the portal data to a TransportData node and delegates to
     * {@link TransportExecutor#executeTeleport(Entity, TransportData)}.
     *
     * @param entity the entity to teleport
     * @param level the server level
     * @param portal the source portal data
     * @param runeEffects the rune effects on this portal (for enhancements)
     * @return true if teleport was initiated successfully
     */
    public boolean teleportViaExecutor(
        @Nonnull Entity entity,
        @Nonnull ServerLevel level,
        @Nonnull PortalData portal,
        @Nonnull PortalRuneEffects runeEffects
    ) {
        TransportData transportNode = convertPortalToTransport(portal, runeEffects);

        // Ensure the transport node is registered (ephemeral, for executor lookup)
        TransportRegistry transportRegistry = TransportRegistry.get(level);
        if (transportRegistry.get(portal.id()).isEmpty()) {
            transportRegistry.register(transportNode);
            LOGGER.debug("[PortalAdapter] Registered ephemeral transport node for portal {}", portal.id());
        } else {
            transportRegistry.update(transportNode);
        }

        // Also register the linked destination if applicable
        registerLinkedDestination(portal, level, transportRegistry);

        // Delegate to TransportExecutor
        boolean success = TransportExecutor.INSTANCE.executeTeleport(entity, transportNode);

        if (success) {
            LOGGER.debug("[PortalAdapter] Teleport via executor succeeded for entity {} through portal {}",
                entity.getName().getString(), portal.id());
        } else {
            LOGGER.debug("[PortalAdapter] Teleport via executor failed for entity {} through portal {}",
                entity.getName().getString(), portal.id());
        }

        return success;
    }

    /**
     * Starts charging for a player at a portal, using TransportExecutor charging.
     *
     * @param player the player
     * @param level the server level
     * @param portal the portal data
     * @param runeEffects the rune effects on this portal
     * @return true if charging started
     */
    public boolean startChargingViaExecutor(
        @Nonnull ServerPlayer player,
        @Nonnull ServerLevel level,
        @Nonnull PortalData portal,
        @Nonnull PortalRuneEffects runeEffects
    ) {
        TransportData transportNode = convertPortalToTransport(portal, runeEffects);

        // Register in transport registry
        TransportRegistry transportRegistry = TransportRegistry.get(level);
        if (transportRegistry.get(portal.id()).isEmpty()) {
            transportRegistry.register(transportNode);
        } else {
            transportRegistry.update(transportNode);
        }

        registerLinkedDestination(portal, level, transportRegistry);

        return TransportExecutor.INSTANCE.startCharging(player, transportNode);
    }

    /**
     * Checks if an entity is on transport cooldown (includes portal cooldowns).
     */
    public boolean isOnCooldown(@Nonnull Entity entity) {
        return TransportExecutor.INSTANCE.isOnCooldown(entity);
    }

    // ============================================================================
    // CONVERSION: PortalData -> TransportData
    // ============================================================================

    /**
     * Converts a PortalData to a TransportData for use with TransportExecutor.
     *
     * <p>The conversion maps:
     * <ul>
     *   <li>Portal linking -> TransportMode.LINKED</li>
     *   <li>Fixed destination -> TransportMode.FIXED</li>
     *   <li>Network name -> TransportMode.NETWORK</li>
     *   <li>Rune effects -> TransportEnhancements</li>
     *   <li>PortalColor -> TransportColor</li>
     * </ul>
     */
    @Nonnull
    public TransportData convertPortalToTransport(
        @Nonnull PortalData portal,
        @Nonnull PortalRuneEffects runeEffects
    ) {
        TransportMode mode = determineMode(portal);
        Set<TransportEnhancement> enhancements = buildEnhancements(runeEffects);
        TransportColor color = TransportColor.fromLegacy(portal.color());

        int chargeTime = runeEffects.instantTeleport() ? 0 : 80; // 4 second default
        int cooldownTime = 100; // 5 seconds (matches TELEPORT_COOLDOWN)

        return new TransportData(
            portal.id(),
            TransportNodeType.PORTAL,
            mode,
            color,
            null, // customModel
            portal.frameBlockCount(),
            portal.dimension().orElse(null),
            portal.position().orElse(null),
            null, // axis
            portal.linkedPortalId().orElse(null),
            portal.networkName(),
            mode == TransportMode.NETWORK ? TransportData.NetworkSelectionMode.RANDOM : null,
            portal.fixedDestinationDimension().orElse(null),
            portal.fixedDestination().orElse(null),
            null, // waypointTargets
            enhancements,
            null, // createdTick
            null, // expirationTick
            null, // currentPhase
            null, // snapshotBounds
            null, // snapshotNBT
            chargeTime,
            cooldownTime,
            portal.creator().orElse(null),
            null, // displayName
            null  // description
        );
    }

    @Nonnull
    private TransportMode determineMode(@Nonnull PortalData portal) {
        if (portal.hasFixedDestination()) {
            return TransportMode.FIXED;
        }
        if (portal.hasNetwork()) {
            return TransportMode.NETWORK;
        }
        return TransportMode.LINKED;
    }

    @Nonnull
    private Set<TransportEnhancement> buildEnhancements(@Nonnull PortalRuneEffects effects) {
        EnumSet<TransportEnhancement> enhancements = EnumSet.of(TransportEnhancement.CHARGED);

        if (effects.instantTeleport()) {
            enhancements.add(TransportEnhancement.HASTE);
        }
        if (effects.crossDimension()) {
            enhancements.add(TransportEnhancement.GATE);
        }
        if (effects.linkRange() == Integer.MAX_VALUE) {
            enhancements.add(TransportEnhancement.INFINITY);
        }

        return enhancements;
    }

    /**
     * Registers the linked destination portal in the TransportRegistry
     * so the executor can resolve it during teleportation.
     */
    private void registerLinkedDestination(
        @Nonnull PortalData sourcePortal,
        @Nonnull ServerLevel level,
        @Nonnull TransportRegistry transportRegistry
    ) {
        sourcePortal.linkedPortalId().ifPresent(linkedId -> {
            if (transportRegistry.get(linkedId).isEmpty()) {
                PortalRegistry portalRegistry = PortalRegistry.get(level);
                portalRegistry.get(linkedId).ifPresent(linkedPortal -> {
                    TransportData linkedNode = convertPortalToTransport(
                        linkedPortal, PortalRuneEffects.NONE);
                    transportRegistry.register(linkedNode);
                    LOGGER.debug("[PortalAdapter] Registered linked destination {} for portal {}",
                        linkedId, sourcePortal.id());
                });
            }
        });
    }

    // ============================================================================
    // SYNC UTILITIES
    // ============================================================================

    /**
     * Syncs all portals from PortalRegistry to TransportRegistry.
     * Safe to call multiple times; existing entries are updated, not duplicated.
     *
     * @param server the Minecraft server
     * @return the number of portals synced
     */
    public int syncAllPortals(@Nonnull MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        PortalRegistry portalRegistry = PortalRegistry.get(overworld);
        TransportRegistry transportRegistry = TransportRegistry.get(overworld);

        int synced = 0;
        for (PortalData portal : portalRegistry.getAll()) {
            TransportData transportNode = convertPortalToTransport(portal, PortalRuneEffects.NONE);

            if (transportRegistry.get(portal.id()).isPresent()) {
                transportRegistry.update(transportNode);
            } else {
                transportRegistry.register(transportNode);
            }
            synced++;
        }

        LOGGER.info("[PortalAdapter] Synced {} portals to TransportRegistry", synced);
        return synced;
    }
}
