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
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.config.Config;
import com.devmod.nexus.runtime.NexusHubManager;
import com.devmod.portal.PortalBlocks;
import com.devmod.portal.PortalColor;
import com.devmod.portal.PortalData;
import com.devmod.portal.PortalRegistry;
import com.devmod.portal.block.CustomPortalBlock;
import com.devmod.zone.data.ZoneDefinition;
import com.devmod.zone.data.ZoneRegistry;

/**
 * Manages zone portals in the Nexus hub using the Portal module.
 * Each zone has a real CustomPortalBlock that teleports players when entered.
 *
 * <p>This replaces the old ArmorStand-based system that was invisible and unusable.
 *
 * <p>Portals are built from data-driven zone definitions provided by {@link com.devmod.zone.data.ZoneRegistry}.
 */
public final class NexusPortalManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(NexusPortalManager.class);

    public static final NexusPortalManager INSTANCE = new NexusPortalManager();

    // Portal dimensions (interior size)
    private static final int PORTAL_WIDTH = 2;  // 2 blocks wide
    private static final int PORTAL_HEIGHT = 3; // 3 blocks tall

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
     * Create a zone portal using data-driven ZoneDefinition.
     */
    private void createZonePortalDataDriven(@Nonnull ServerLevel level, @Nonnull BlockPos hubOrigin,
                                            @Nonnull com.devmod.zone.data.ZoneDefinition zone) {
        BlockPos portalOffset = zone.portalOffset();
        if (portalOffset == null) {
            // No portal defined for this zone
            return;
        }

        BlockPos portalCenter = hubOrigin.offset(portalOffset);
        PortalColor color = zone.portalColor();
        if (color == null) {
            color = PortalColor.BLUE;
        }

        // Build portal frame
        buildPortalFrame(level, Objects.requireNonNull(portalCenter));

        // Get destination (zone spawn point)
        BlockPos destination = zone.getAbsoluteSpawn(hubOrigin);
        if (destination == null) {
            destination = zone.bounds().floorCenter();
        }
        ResourceLocation nexusDim = NexusDimensionManager.NEXUS_DIMENSION.location();

        // Create portal data with fixed destination
        PortalData portalData = PortalData.createWithFixedDestination(
            color,
            Objects.requireNonNull(nexusDim),
            Objects.requireNonNull(portalCenter),
            Objects.requireNonNull(nexusDim),  // Same dimension (Nexus)
            Objects.requireNonNull(destination)
        );

        // Register portal in registry
        PortalRegistry registry = PortalRegistry.get(level);
        registry.register(Objects.requireNonNull(portalData));
        portalIds.put(zone.zoneId(), portalData.id());

        // Fill interior with portal blocks
        fillPortalInterior(level, Objects.requireNonNull(portalCenter), color);

        LOGGER.debug("[NexusPortals] Created {} portal at {} -> destination {}",
            zone.zoneId(), portalCenter, destination);
    }

    /**
     * Create a portal for a Nexus zone slot.
     * Used by NexusHubManager when linking areas to slots.
     *
     * @param level the Nexus dimension
     * @param portalPosition the portal position (absolute world coordinates)
     * @param color the portal color
     * @param destination the teleport destination
     * @param slotId the slot ID for tracking
     * @return the portal UUID, or null if creation failed
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
            LOGGER.debug("[NexusPortals] Portals disabled, skipping slot portal creation");
            return null;
        }

        UUID existingId = portalIds.get(slotId);
        if (existingId != null) {
            LOGGER.debug("[NexusPortals] Slot portal '{}' already exists", slotId);
            return existingId;
        }

        PortalRegistry registry = PortalRegistry.get(level);
        Optional<PortalData> existingPortal = registry.getByPosition(portalPosition);
        if (existingPortal.isPresent()) {
            UUID portalId = existingPortal.get().id();
            portalIds.put(slotId, portalId);
            LOGGER.debug("[NexusPortals] Slot portal '{}' already registered at {}", slotId, portalPosition);
            return portalId;
        }

        // Build portal frame
        buildPortalFrame(level, portalPosition);

        // Create portal data with fixed destination
        ResourceLocation nexusDim = NexusDimensionManager.NEXUS_DIMENSION.location();
        PortalData portalData = PortalData.createWithFixedDestination(
            color,
            Objects.requireNonNull(nexusDim),
            portalPosition,
            nexusDim,
            destination
        );

        // Register portal
        registry.register(Objects.requireNonNull(portalData));
        portalIds.put(slotId, portalData.id());

        // Fill interior with portal blocks
        fillPortalInterior(level, portalPosition, color);

        LOGGER.debug("[NexusPortals] Created slot portal '{}' at {} -> {}",
            slotId, portalPosition, destination);
        return portalData.id();
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
     * Build the portal frame structure.
     * Creates a simple obsidian frame around the portal interior.
     */
    private void buildPortalFrame(@Nonnull ServerLevel level, @Nonnull BlockPos center) {
        BlockState frameBlock = Objects.requireNonNull(
            Objects.requireNonNull(Blocks.OBSIDIAN, "Blocks.OBSIDIAN").defaultBlockState(),
            "frameBlock"
        );

        // Frame corners (relative to center bottom-left of interior)
        // Portal faces Z axis (players approach from north/south)
        // Interior is PORTAL_WIDTH x PORTAL_HEIGHT starting at center

        // Bottom frame (below interior)
        for (int x = -1; x <= PORTAL_WIDTH; x++) {
            BlockPos framePos = Objects.requireNonNull(center.offset(x, -1, 0), "framePos");
            level.setBlock(framePos, frameBlock, 3);
        }

        // Top frame (above interior)
        for (int x = -1; x <= PORTAL_WIDTH; x++) {
            BlockPos framePos = Objects.requireNonNull(center.offset(x, PORTAL_HEIGHT, 0), "framePos");
            level.setBlock(framePos, frameBlock, 3);
        }

        // Left pillar
        for (int y = -1; y <= PORTAL_HEIGHT; y++) {
            BlockPos framePos = Objects.requireNonNull(center.offset(-1, y, 0), "framePos");
            level.setBlock(framePos, frameBlock, 3);
        }

        // Right pillar
        for (int y = -1; y <= PORTAL_HEIGHT; y++) {
            BlockPos framePos = Objects.requireNonNull(center.offset(PORTAL_WIDTH, y, 0), "framePos");
            level.setBlock(framePos, frameBlock, 3);
        }
    }

    /**
     * Fill the portal interior with CustomPortalBlock.
     */
    private void fillPortalInterior(@Nonnull ServerLevel level, @Nonnull BlockPos center,
                                     @Nonnull PortalColor color) {
        BlockState portalState = Objects.requireNonNull(
            Objects.requireNonNull(PortalBlocks.CUSTOM_PORTAL.get(), "PortalBlocks.CUSTOM_PORTAL").defaultBlockState()
                .setValue(Objects.requireNonNull(CustomPortalBlock.AXIS), Direction.Axis.Z)
                .setValue(Objects.requireNonNull(CustomPortalBlock.COLOR), color)
                .setValue(Objects.requireNonNull(CustomPortalBlock.LINKED), true),
            "portalState"
        );

        // Fill interior
        for (int x = 0; x < PORTAL_WIDTH; x++) {
            for (int y = 0; y < PORTAL_HEIGHT; y++) {
                BlockPos portalPos = Objects.requireNonNull(center.offset(x, y, 0), "portalPos");
                level.setBlock(portalPos, portalState, 3);
            }
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
     * Check if a position is within portal bounds.
     */
    private boolean isWithinPortalBounds(@Nonnull BlockPos portalBase, @Nonnull BlockPos check) {
        int dx = check.getX() - portalBase.getX();
        int dy = check.getY() - portalBase.getY();
        int dz = check.getZ() - portalBase.getZ();

        return dx >= 0 && dx < PORTAL_WIDTH
            && dy >= 0 && dy < PORTAL_HEIGHT
            && dz == 0;
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
