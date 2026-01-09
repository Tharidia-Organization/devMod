package com.devmod.runtime;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.config.Config;
import com.devmod.portal.PortalBlocks;
import com.devmod.portal.PortalColor;
import com.devmod.portal.PortalData;
import com.devmod.portal.PortalRegistry;
import com.devmod.portal.block.CustomPortalBlock;

/**
 * Manages zone portals in the Nexus hub using the Portal module.
 * Each zone has a real CustomPortalBlock that teleports players when entered.
 *
 * <p>This replaces the old ArmorStand-based system that was invisible and unusable.
 */
public final class NexusPortalManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(NexusPortalManager.class);

    public static final NexusPortalManager INSTANCE = new NexusPortalManager();

    // Portal pedestal positions relative to hub center
    private static final Map<NexusSpawnManager.Zone, BlockPos> PORTAL_OFFSETS = new EnumMap<>(NexusSpawnManager.Zone.class);

    static {
        // Portals placed at the hub center, arranged in a circle
        int radius = 12;
        int y = 1;
        PORTAL_OFFSETS.put(NexusSpawnManager.Zone.COMBAT, new BlockPos(-radius, y, -radius));
        PORTAL_OFFSETS.put(NexusSpawnManager.Zone.ARENA, new BlockPos(-radius, y, 0));
        PORTAL_OFFSETS.put(NexusSpawnManager.Zone.UI, new BlockPos(radius, y, -radius));
        PORTAL_OFFSETS.put(NexusSpawnManager.Zone.TELEMETRY, new BlockPos(radius, y, 0));
        PORTAL_OFFSETS.put(NexusSpawnManager.Zone.SHOWCASE, new BlockPos(-radius, y, radius));
        PORTAL_OFFSETS.put(NexusSpawnManager.Zone.INTEGRATION, new BlockPos(0, y, radius));
        PORTAL_OFFSETS.put(NexusSpawnManager.Zone.SANDBOX, new BlockPos(radius / 2, y, radius));
        PORTAL_OFFSETS.put(NexusSpawnManager.Zone.MECHANICS, new BlockPos(radius, y, radius));
    }

    // Zone to PortalColor mapping
    private static final Map<NexusSpawnManager.Zone, PortalColor> ZONE_TO_COLOR = new EnumMap<>(NexusSpawnManager.Zone.class);

    static {
        ZONE_TO_COLOR.put(NexusSpawnManager.Zone.COMBAT, PortalColor.RED);
        ZONE_TO_COLOR.put(NexusSpawnManager.Zone.ARENA, PortalColor.ORANGE);
        ZONE_TO_COLOR.put(NexusSpawnManager.Zone.UI, PortalColor.LIGHT_BLUE);
        ZONE_TO_COLOR.put(NexusSpawnManager.Zone.TELEMETRY, PortalColor.LIME);
        ZONE_TO_COLOR.put(NexusSpawnManager.Zone.SHOWCASE, PortalColor.MAGENTA);
        ZONE_TO_COLOR.put(NexusSpawnManager.Zone.INTEGRATION, PortalColor.PURPLE);
        ZONE_TO_COLOR.put(NexusSpawnManager.Zone.SANDBOX, PortalColor.CYAN);
        ZONE_TO_COLOR.put(NexusSpawnManager.Zone.MECHANICS, PortalColor.YELLOW);
    }

    // Portal dimensions (interior size)
    private static final int PORTAL_WIDTH = 2;  // 2 blocks wide
    private static final int PORTAL_HEIGHT = 3; // 3 blocks tall

    // Track created portal UUIDs for cleanup
    private final Map<NexusSpawnManager.Zone, UUID> portalIds = new EnumMap<>(NexusSpawnManager.Zone.class);

    private NexusPortalManager() {}

    /**
     * Initialize zone portals in the Nexus hub.
     */
    public void initialize(@Nonnull ServerLevel level, @Nonnull BlockPos hubOrigin) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(hubOrigin, "hubOrigin");

        if (!Config.NEXUS_PORTALS_ENABLED.get()) {
            return;
        }

        LOGGER.info("[NexusPortals] Initializing zone portals using Portal module");

        // Clear existing portals
        cleanup(level);

        // Create portal for each zone
        for (NexusSpawnManager.Zone zone : PORTAL_OFFSETS.keySet()) {
            createZonePortal(level, hubOrigin, Objects.requireNonNull(zone, "zone"));
        }

        LOGGER.info("[NexusPortals] Created {} zone portals", portalIds.size());
    }

    /**
     * Create a zone portal using CustomPortalBlock.
     * Builds a frame and fills interior with portal blocks registered with fixed destination.
     */
    private void createZonePortal(@Nonnull ServerLevel level, @Nonnull BlockPos hubOrigin,
                                   @Nonnull NexusSpawnManager.Zone zone) {
        BlockPos offset = PORTAL_OFFSETS.get(zone);
        if (offset == null) {
            return;
        }

        BlockPos portalCenter = hubOrigin.offset(offset);
        PortalColor color = ZONE_TO_COLOR.getOrDefault(zone, PortalColor.BLUE);

        // Build portal frame (obsidian-like structure)
        buildPortalFrame(level, portalCenter);

        // Get destination (zone spawn point)
        BlockPos destination = NexusSpawnManager.getSpawnForZone(hubOrigin, zone);
        ResourceLocation nexusDim = NexusDimensionManager.NEXUS_DIMENSION.location();

        // Create portal data with fixed destination
        PortalData portalData = PortalData.createWithFixedDestination(
            color,
            nexusDim,
            portalCenter,
            nexusDim,  // Same dimension (Nexus)
            destination
        );

        // Register portal in registry
        PortalRegistry registry = PortalRegistry.get(level);
        registry.register(portalData);
        portalIds.put(zone, portalData.id());

        // Fill interior with portal blocks
        fillPortalInterior(level, portalCenter, color);

        LOGGER.debug("[NexusPortals] Created {} portal at {} -> destination {}",
            zone.id(), portalCenter, destination);
    }

    /**
     * Build the portal frame structure.
     * Creates a simple obsidian frame around the portal interior.
     */
    private void buildPortalFrame(@Nonnull ServerLevel level, @Nonnull BlockPos center) {
        BlockState frameBlock = Blocks.OBSIDIAN.defaultBlockState();

        // Frame corners (relative to center bottom-left of interior)
        // Portal faces Z axis (players approach from north/south)
        // Interior is PORTAL_WIDTH x PORTAL_HEIGHT starting at center

        // Bottom frame (below interior)
        for (int x = -1; x <= PORTAL_WIDTH; x++) {
            level.setBlock(center.offset(x, -1, 0), frameBlock, 3);
        }

        // Top frame (above interior)
        for (int x = -1; x <= PORTAL_WIDTH; x++) {
            level.setBlock(center.offset(x, PORTAL_HEIGHT, 0), frameBlock, 3);
        }

        // Left pillar
        for (int y = -1; y <= PORTAL_HEIGHT; y++) {
            level.setBlock(center.offset(-1, y, 0), frameBlock, 3);
        }

        // Right pillar
        for (int y = -1; y <= PORTAL_HEIGHT; y++) {
            level.setBlock(center.offset(PORTAL_WIDTH, y, 0), frameBlock, 3);
        }
    }

    /**
     * Fill the portal interior with CustomPortalBlock.
     */
    private void fillPortalInterior(@Nonnull ServerLevel level, @Nonnull BlockPos center,
                                     @Nonnull PortalColor color) {
        BlockState portalState = PortalBlocks.CUSTOM_PORTAL.get().defaultBlockState()
            .setValue(CustomPortalBlock.AXIS, Direction.Axis.Z)
            .setValue(CustomPortalBlock.COLOR, color)
            .setValue(CustomPortalBlock.LINKED, true);  // Show as active

        // Fill interior
        for (int x = 0; x < PORTAL_WIDTH; x++) {
            for (int y = 0; y < PORTAL_HEIGHT; y++) {
                level.setBlock(center.offset(x, y, 0), portalState, 3);
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
        for (Map.Entry<NexusSpawnManager.Zone, UUID> entry : portalIds.entrySet()) {
            NexusSpawnManager.Zone zone = entry.getKey();
            UUID portalId = entry.getValue();

            PortalRegistry registry = PortalRegistry.get(level);
            if (registry.get(portalId).isEmpty()) {
                // Portal was destroyed, recreate it
                LOGGER.debug("[NexusPortals] Recreating destroyed portal for {}", zone.id());
                createZonePortal(level, hubOrigin, zone);
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
            registry.unregister(portalId);
        }
        portalIds.clear();

        LOGGER.debug("[NexusPortals] Cleaned up zone portals");
    }

    /**
     * Get the PortalColor for a zone.
     */
    @Nonnull
    public PortalColor getZonePortalColor(@Nonnull NexusSpawnManager.Zone zone) {
        return ZONE_TO_COLOR.getOrDefault(zone, PortalColor.BLUE);
    }

    /**
     * Get the color (ARGB) for a zone (for UI rendering).
     */
    public int getZoneColor(@Nonnull NexusSpawnManager.Zone zone) {
        PortalColor pc = getZonePortalColor(zone);
        return com.devmod.client.ui.editor.core.DesignTokens.Portal.PREVIEW_COLOR_ALPHA
            | (pc.getRed() << 16) | (pc.getGreen() << 8) | pc.getBlue();
    }

    /**
     * Get all portal positions for client rendering.
     */
    @Nonnull
    public List<PortalInfo> getPortalInfoList(@Nonnull BlockPos hubOrigin) {
        List<PortalInfo> list = new ArrayList<>();
        for (Map.Entry<NexusSpawnManager.Zone, BlockPos> entry : PORTAL_OFFSETS.entrySet()) {
            NexusSpawnManager.Zone zone = Objects.requireNonNull(entry.getKey(), "zone");
            BlockPos pos = hubOrigin.offset(Objects.requireNonNull(entry.getValue(), "offset"));
            int color = getZoneColor(zone);
            list.add(new PortalInfo(zone, pos, color));
        }
        return list;
    }

    /**
     * Get the zone for a portal position.
     */
    @Nullable
    public NexusSpawnManager.Zone getZoneAtPosition(@Nonnull BlockPos hubOrigin, @Nonnull BlockPos pos) {
        for (Map.Entry<NexusSpawnManager.Zone, BlockPos> entry : PORTAL_OFFSETS.entrySet()) {
            BlockPos portalPos = hubOrigin.offset(entry.getValue());
            // Check if position is within portal bounds
            if (isWithinPortalBounds(portalPos, pos)) {
                return entry.getKey();
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
        @Nonnull NexusSpawnManager.Zone zone,
        @Nonnull BlockPos position,
        int color
    ) {
        public PortalInfo {
            Objects.requireNonNull(zone, "zone");
            Objects.requireNonNull(position, "position");
        }
    }
}
