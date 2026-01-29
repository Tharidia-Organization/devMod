package com.devmod.transport.bridge;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import com.devmod.config.Config;
import com.devmod.nexus.runtime.NexusHubManager;
import com.devmod.runtime.NexusDimensionManager;
import com.devmod.runtime.NexusReturnSavedData;
import com.devmod.transport.TransportColor;
import com.devmod.transport.TransportData;
import com.devmod.transport.TransportRegistry;
import com.devmod.transport.executor.RecoveryManager;

/**
 * Bridge between the Transport system and the Nexus dimension.
 *
 * <p>Provides:
 * <ul>
 *   <li>Teleportation to Nexus zones (HUB, ARENA, MARKET, etc.)</li>
 *   <li>Return point management</li>
 *   <li>Zone-specific transport nodes</li>
 * </ul>
 */
public final class NexusTransportBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(NexusTransportBridge.class);

    public static final NexusTransportBridge INSTANCE = new NexusTransportBridge();

    private NexusTransportBridge() {}

    /**
     * Checks if the Nexus dimension is available.
     */
    public boolean isAvailable() {
        return Config.NEXUS_ENABLED.get();
    }

    /**
     * Teleports a player to the Nexus Hub (default spawn).
     *
     * @param player The player to teleport
     * @return true if teleport was successful
     */
    public boolean teleportToHub(ServerPlayer player) {
        if (!isAvailable()) {
            LOGGER.warn("[NexusBridge] Nexus dimension not enabled");
            return false;
        }

        // Save return point if not already in Nexus
        saveReturnPointIfNeeded(player);

        NexusDimensionManager.INSTANCE.teleportPlayerToSpawn(player);
        LOGGER.debug("[NexusBridge] Teleported {} to Nexus Hub", player.getName().getString());
        return true;
    }


    /**
     * Teleports a player to a specific Nexus zone by ID (data-driven).
     *
     * @param player The player to teleport
     * @param zoneId The zone ID or alias (e.g., "combat", "sandbox", "home")
     * @return true if teleport was successful
     */
    public boolean teleportToZone(ServerPlayer player, String zoneId) {
        if (!isAvailable()) {
            LOGGER.warn("[NexusBridge] Nexus dimension not enabled");
            return false;
        }

        // Save return point if not already in Nexus
        saveReturnPointIfNeeded(player);

        boolean success = NexusDimensionManager.INSTANCE.teleportPlayerToZone(player, zoneId);
        if (success) {
            LOGGER.debug("[NexusBridge] Teleported {} to Nexus zone {}", player.getName().getString(), zoneId);
        } else {
            LOGGER.warn("[NexusBridge] Failed to teleport {} to zone {}: zone not found",
                player.getName().getString(), zoneId);
        }
        return success;
    }

    /**
     * Teleports a player back to their return point (exit Nexus).
     *
     * @param player The player to teleport
     * @return true if teleport was successful
     */
    public boolean teleportToReturn(ServerPlayer player) {
        // Try Nexus return system first
        if (NexusDimensionManager.INSTANCE.teleportPlayerToReturn(player)) {
            LOGGER.debug("[NexusBridge] Returned {} via Nexus return system", player.getName().getString());
            return true;
        }

        // Fallback to Recovery Manager
        if (RecoveryManager.INSTANCE.hasRecoveryPoint(Objects.requireNonNull(player.getUUID()))) {
            return RecoveryManager.INSTANCE.restorePlayer(player);
        }

        LOGGER.warn("[NexusBridge] No return point for {}", player.getName().getString());
        return false;
    }

    /**
     * Checks if a player is currently in the Nexus dimension.
     */
    public boolean isPlayerInNexus(ServerPlayer player) {
        return player.level().dimension().equals(NexusDimensionManager.NEXUS_DIMENSION);
    }

    /**
     * Gets the Nexus dimension resource location.
     */
    @Nonnull
    public ResourceLocation getNexusDimension() {
        return Objects.requireNonNull(NexusDimensionManager.NEXUS_DIMENSION.location());
    }

    /**
     * Saves a return point for the player if they're not already in Nexus.
     */
    private void saveReturnPointIfNeeded(ServerPlayer player) {
        if (!isPlayerInNexus(player)) {
            MinecraftServer server = player.getServer();
            if (server != null) {
                NexusReturnSavedData.get(server).recordReturn(player);
            }
            // Also save in Recovery Manager as backup
            RecoveryManager.INSTANCE.saveRecoveryPoint(player);
        }
    }

    /**
     * Creates transport nodes for all Nexus zones.
     * Uses data-driven zones from {@link com.devmod.zone.data.ZoneRegistry}.
     *
     * @param registry The transport registry
     * @param server The Minecraft server
     */
    public void initializeNexusNodes(TransportRegistry registry, MinecraftServer server) {
        if (!isAvailable()) {
            return;
        }

        BlockPos hubOrigin = NexusDimensionManager.getHubOrigin();
        ResourceLocation nexusDim = getNexusDimension();

        // Use data-driven zone registry
        com.devmod.zone.data.ZoneRegistry zoneRegistry = com.devmod.zone.data.ZoneRegistry.get(server);
        if (!zoneRegistry.isInitialized()) {
            NexusHubManager.INSTANCE.initialize(server);
            return;
        }
        for (com.devmod.zone.data.ZoneDefinition zone : zoneRegistry.getTeleportableZones()) {
            BlockPos spawnPos = zone.getAbsoluteSpawn(Objects.requireNonNull(hubOrigin));
            if (spawnPos == null) {
                spawnPos = zone.bounds().floorCenter();
            }

            TransportData nodeData = TransportData.createZone(
                getZoneColorFromDefinition(zone),
                Objects.requireNonNull(nexusDim), Objects.requireNonNull(spawnPos),
                Objects.requireNonNull(nexusDim), Objects.requireNonNull(spawnPos),
                "Nexus " + zone.displayName()
            );

            // Check if already registered
            if (registry.getByPosition(Objects.requireNonNull(spawnPos)).isEmpty()) {
                registry.register(Objects.requireNonNull(nodeData));
                LOGGER.debug("[NexusBridge] Registered Nexus zone node: {}", zone.zoneId());
            }
        }

        LOGGER.info("[NexusBridge] Initialized Nexus transport nodes");
    }

    /**
     * Gets the transport color from a ZoneDefinition.
     */
    @Nonnull
    private TransportColor getZoneColorFromDefinition(com.devmod.zone.data.ZoneDefinition zone) {
        // Map portal color to transport color
        com.devmod.portal.PortalColor portalColor = zone.portalColor();
        if (portalColor == null) {
            return TransportColor.CYAN; // default
        }
        return switch (portalColor) {
            case RED -> TransportColor.RED;
            case ORANGE -> TransportColor.ORANGE;
            case YELLOW -> TransportColor.YELLOW;
            case LIME -> TransportColor.LIME;
            case GREEN -> TransportColor.GREEN;
            case CYAN -> TransportColor.CYAN;
            case LIGHT_BLUE -> TransportColor.LIGHT_BLUE;
            case BLUE -> TransportColor.BLUE;
            case PURPLE -> TransportColor.PURPLE;
            case MAGENTA -> TransportColor.MAGENTA;
            case PINK -> TransportColor.PINK;
            case WHITE -> TransportColor.WHITE;
            case LIGHT_GRAY -> TransportColor.LIGHT_GRAY;
            case GRAY -> TransportColor.GRAY;
            case BLACK -> TransportColor.BLACK;
            case BROWN -> TransportColor.BROWN;
        };
    }


    /**
     * Creates a return transport node for a specific zone by ID (data-driven).
     *
     * @param registry The transport registry
     * @param zoneId The source zone ID
     * @return The created transport data, or empty if zone not found
     */
    @Nonnull
    public Optional<TransportData> createReturnNode(
            TransportRegistry registry,
            String zoneId,
            MinecraftServer server) {

        if (!isAvailable()) {
            return Optional.empty();
        }

        NexusHubManager.INSTANCE.initialize(server);
        Optional<com.devmod.zone.data.ZoneDefinition> zoneOpt =
            com.devmod.zone.runtime.ZoneResolver.INSTANCE.resolveByNameOrAlias(server, zoneId);
        if (zoneOpt.isEmpty()) {
            return Optional.empty();
        }

        com.devmod.zone.data.ZoneDefinition zone = zoneOpt.get();
        BlockPos hubOrigin = NexusDimensionManager.getHubOrigin();
        BlockPos zonePos = zone.getAbsoluteSpawn(Objects.requireNonNull(hubOrigin));
        if (zonePos == null) {
            zonePos = zone.bounds().floorCenter();
        }
        ResourceLocation nexusDim = getNexusDimension();

        TransportData nodeData = TransportData.createRecoveryPoint(
            UUID.randomUUID(), nexusDim, zonePos
        ).withDisplayName("Return from " + zone.displayName())
         .withColor(getZoneColorFromDefinition(zone));

        registry.register(nodeData);

        return Optional.of(nodeData);
    }

    /**
     * Gets the spawn position for a zone by ID (data-driven).
     *
     * @param zoneId The zone ID or alias
     * @param server The Minecraft server
     * @return The spawn position, or hub origin if zone not found
     */
    @Nonnull
    public BlockPos getZoneSpawn(String zoneId, MinecraftServer server) {
        BlockPos hubOrigin = NexusDimensionManager.getHubOrigin();
        NexusHubManager.INSTANCE.initialize(server);

        Optional<com.devmod.zone.data.ZoneDefinition> zoneOpt =
            com.devmod.zone.runtime.ZoneResolver.INSTANCE.resolveByNameOrAlias(server, zoneId);
        if (zoneOpt.isEmpty()) {
            return Objects.requireNonNull(hubOrigin);
        }

        com.devmod.zone.data.ZoneDefinition zone = zoneOpt.get();
        BlockPos spawn = zone.getAbsoluteSpawn(Objects.requireNonNull(hubOrigin));
        return Objects.requireNonNull(spawn != null ? spawn : zone.bounds().floorCenter());
    }
}
