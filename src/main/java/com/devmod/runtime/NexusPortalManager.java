package com.devmod.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.clone.CloneBlocks;
import com.devmod.clone.block.entity.TelepadBlockEntity;
import com.devmod.config.Config;
import com.devmod.nexus.NexusDecorBlocks;
import com.devmod.nexus.runtime.NexusHubManager;
import com.devmod.portal.PortalColor;
import com.devmod.portal.PortalData;
import com.devmod.portal.PortalRegistry;
import com.devmod.zone.data.ZoneDefinition;
import com.devmod.zone.data.ZoneRegistry;

/**
 * Manages zone telepads in the Nexus testing lab.
 * Each zone has a Clone Telepad block with shader-based vortex rendering.
 *
 * <p>Telepads are placed on a decorative platform and configured with a
 * network name matching the zone ID for inter-zone navigation.
 *
 * <p>Zones are defined by {@link com.devmod.zone.data.ZoneRegistry}.
 */
public final class NexusPortalManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(NexusPortalManager.class);

    public static final NexusPortalManager INSTANCE = new NexusPortalManager();

    /** Platform size around each telepad (NxN decorative blocks). */
    private static final int PLATFORM_RADIUS = 2;

    // Track created portal UUIDs for cleanup (data-driven: zoneId -> UUID)
    private final Map<String, UUID> portalIds = new ConcurrentHashMap<>();

    private NexusPortalManager() {}

    /**
     * Initialize zone portals in the Nexus hub.
     * Uses data-driven zones from {@link com.devmod.zone.data.ZoneRegistry}.
     */
    public void initialize(@Nonnull ServerLevel level, @Nonnull BlockPos hubOrigin) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(hubOrigin, "hubOrigin");

        if (!Config.NEXUS_PORTALS_ENABLED.get()) {
            return;
        }

        LOGGER.info("[NexusPortals] Initializing zone portals using data-driven zones");

        // Clear existing portals
        cleanup(level);

        // Use data-driven zone registry
        ZoneRegistry zoneRegistry = getZoneRegistry(level.getServer());

        for (com.devmod.zone.data.ZoneDefinition zone : zoneRegistry.getTeleportableZones()) {
            createZonePortalDataDriven(level, hubOrigin, Objects.requireNonNull(zone));
        }

        LOGGER.info("[NexusPortals] Created {} zone portals", portalIds.size());
    }

    /**
     * Create a zone telepad using data-driven ZoneDefinition.
     * Places a Clone Telepad on a decorative platform.
     */
    private void createZonePortalDataDriven(@Nonnull ServerLevel level, @Nonnull BlockPos hubOrigin,
                                            @Nonnull com.devmod.zone.data.ZoneDefinition zone) {
        BlockPos portalOffset = zone.portalOffset();
        if (portalOffset == null) {
            return;
        }

        BlockPos telepadPos = hubOrigin.offset(portalOffset);

        // Build decorative platform under telepad
        buildTelepadPlatform(level, Objects.requireNonNull(telepadPos));

        // Place the telepad block
        placeTelepad(level, telepadPos, zone.zoneId());

        LOGGER.debug("[NexusPortals] Created {} telepad at {}",
            zone.zoneId(), telepadPos);
    }

    /**
     * Create a telepad for a Nexus zone slot.
     * Used by NexusHubManager when linking areas to slots.
     *
     * @param level the Nexus dimension
     * @param portalPosition the telepad position (absolute world coordinates)
     * @param color the portal color (unused for telepads, kept for API compat)
     * @param destination the teleport destination (unused, telepad uses network)
     * @param slotId the slot ID for tracking and telepad network name
     * @return a generated UUID for tracking, or null if creation failed
     */
    @Nullable
    public UUID createSlotPortal(
            @Nonnull ServerLevel level,
            @Nonnull BlockPos portalPosition,
            @Nonnull PortalColor color,
            @Nonnull BlockPos destination,
            @Nonnull String slotId
    ) {
        Objects.requireNonNull(level);
        Objects.requireNonNull(portalPosition);
        Objects.requireNonNull(color);
        Objects.requireNonNull(destination);
        Objects.requireNonNull(slotId);

        if (!Config.NEXUS_PORTALS_ENABLED.get()) {
            LOGGER.debug("[NexusPortals] Portals disabled, skipping telepad creation");
            return null;
        }

        UUID existingId = portalIds.get(slotId);
        if (existingId != null) {
            LOGGER.debug("[NexusPortals] Slot telepad '{}' already exists", slotId);
            return existingId;
        }

        // Build decorative platform and place telepad
        buildTelepadPlatform(level, portalPosition);
        placeTelepad(level, portalPosition, slotId);

        // Track with generated UUID
        UUID telepadId = UUID.randomUUID();
        portalIds.put(slotId, telepadId);

        LOGGER.debug("[NexusPortals] Created slot telepad '{}' at {}",
            slotId, portalPosition);
        return telepadId;
    }

    /**
     * Remove a portal for a Nexus zone slot.
     *
     * @param level the Nexus dimension
     * @param slotId the slot ID
     * @return true if removed
     */
    public boolean removeSlotPortal(@Nonnull ServerLevel level, @Nonnull String slotId) {
        Objects.requireNonNull(level);
        Objects.requireNonNull(slotId);

        UUID portalId = portalIds.remove(slotId);
        if (portalId == null) {
            return false;
        }

        PortalRegistry registry = PortalRegistry.get(level);
        registry.unregister(portalId);
        LOGGER.debug("[NexusPortals] Removed slot portal '{}'", slotId);
        return true;
    }

    /**
     * Build a decorative platform under a telepad position.
     * Uses NexusDecorBlocks for a sci-fi aesthetic.
     */
    private void buildTelepadPlatform(@Nonnull ServerLevel level, @Nonnull BlockPos center) {
        BlockState platformBlock = NexusDecorBlocks.NEXUS_CIRCUIT.get().defaultBlockState();
        BlockState accentBlock = NexusDecorBlocks.NEXUS_AZURE.get().defaultBlockState();

        // Build platform at Y-1 (under the telepad)
        for (int dx = -PLATFORM_RADIUS; dx <= PLATFORM_RADIUS; dx++) {
            for (int dz = -PLATFORM_RADIUS; dz <= PLATFORM_RADIUS; dz++) {
                BlockPos platformPos = center.offset(dx, -1, dz);
                // Diamond pattern: accent on edges, circuit in center
                boolean isEdge = Math.abs(dx) + Math.abs(dz) == PLATFORM_RADIUS;
                level.setBlock(platformPos, isEdge ? accentBlock : platformBlock, 3);
            }
        }
    }

    /**
     * Place a Clone Telepad block and configure its network name.
     *
     * @param level the level to place in
     * @param pos the position to place the telepad
     * @param zoneId the zone ID used as telepad network name
     */
    private void placeTelepad(@Nonnull ServerLevel level, @Nonnull BlockPos pos, @Nonnull String zoneId) {
        BlockState telepadState = CloneBlocks.TELEPAD.get().defaultBlockState()
            .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.NORTH);
        level.setBlock(pos, telepadState, 3);

        // Configure the telepad network name
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TelepadBlockEntity telepad) {
            telepad.setTelepadName("nexus_" + zoneId);
        } else {
            LOGGER.warn("[NexusPortals] Failed to get TelepadBlockEntity at {}", pos);
        }
    }

    /**
     * Tick the portal system.
     * Note: With real portal blocks, particle effects are handled by CustomPortalBlock.animateTick()
     */
    public void tick(@Nonnull ServerLevel level, @Nonnull BlockPos hubOrigin) {
        if (!Config.NEXUS_PORTALS_ENABLED.get()) {
            return;
        }

        // Verify portals still exist (rebuild if destroyed)
        ZoneRegistry zoneRegistry = getZoneRegistry(level.getServer());

        for (Map.Entry<String, UUID> entry : portalIds.entrySet()) {
            String zoneId = entry.getKey();
            UUID portalId = entry.getValue();

            PortalRegistry registry = PortalRegistry.get(level);
            if (registry.get(Objects.requireNonNull(portalId)).isEmpty()) {
                // Portal was destroyed, recreate it using data-driven zone
                LOGGER.debug("[NexusPortals] Recreating destroyed portal for {}", zoneId);
                zoneRegistry.getZoneById(Objects.requireNonNull(zoneId)).ifPresent(zone ->
                    createZonePortalDataDriven(level, hubOrigin, Objects.requireNonNull(zone)));
            }
        }
    }

    /**
     * Clean up all zone portals.
     */
    public void cleanup(@Nonnull ServerLevel level) {
        PortalRegistry registry = PortalRegistry.get(level);

        // Unregister all tracked portals
        for (UUID portalId : portalIds.values()) {
            registry.unregister(Objects.requireNonNull(portalId));
        }
        portalIds.clear();

        LOGGER.debug("[NexusPortals] Cleaned up zone portals");
    }

    /**
     * Get the color (ARGB) for a zone (for UI rendering).
     */
    private int getZoneColor(@Nullable PortalColor portalColor) {
        PortalColor color = portalColor != null ? portalColor : PortalColor.BLUE;
        return com.devmod.client.ui.editor.core.DesignTokens.Portal.PREVIEW_COLOR_ALPHA
            | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
    }

    /**
     * Get all portal positions for client rendering.
     */
    @Nonnull
    public List<PortalInfo> getPortalInfoList(@Nonnull MinecraftServer server, @Nonnull BlockPos hubOrigin) {
        List<PortalInfo> list = new ArrayList<>();
        ZoneRegistry registry = getZoneRegistry(server);
        for (ZoneDefinition zone : registry.getTeleportableZones()) {
            BlockPos portalOffset = zone.portalOffset();
            if (portalOffset == null) {
                continue;
            }
            BlockPos pos = hubOrigin.offset(portalOffset);
            int color = getZoneColor(zone.portalColor());
            list.add(new PortalInfo(zone.zoneId(), pos, color));
        }
        return list;
    }

    /**
     * Get the zone for a portal position.
     */
    @Nullable
    public ZoneDefinition getZoneAtPosition(@Nonnull MinecraftServer server,
                                            @Nonnull BlockPos hubOrigin,
                                            @Nonnull BlockPos pos) {
        ZoneRegistry registry = getZoneRegistry(server);
        for (ZoneDefinition zone : registry.getTeleportableZones()) {
            BlockPos portalOffset = zone.portalOffset();
            if (portalOffset == null) {
                continue;
            }
            BlockPos portalPos = hubOrigin.offset(portalOffset);
            if (isWithinPortalBounds(Objects.requireNonNull(portalPos), pos)) {
                return zone;
            }
        }
        return null;
    }

    /**
     * Check if a position is within telepad bounds (1x1x1 block).
     */
    private boolean isWithinPortalBounds(@Nonnull BlockPos portalBase, @Nonnull BlockPos check) {
        return portalBase.equals(check);
    }

    /**
     * Portal information for rendering/display.
     */
    public record PortalInfo(
        @Nonnull String zoneId,
        @Nonnull BlockPos position,
        int color
    ) {
        public PortalInfo {
            Objects.requireNonNull(zoneId, "zoneId");
            Objects.requireNonNull(position, "position");
        }
    }

    private ZoneRegistry getZoneRegistry(@Nonnull MinecraftServer server) {
        NexusHubManager.INSTANCE.initialize(server);
        return ZoneRegistry.get(server);
    }
}
