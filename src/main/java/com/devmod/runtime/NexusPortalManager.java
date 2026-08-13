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

    /**
     * Ticks between griefing checks. Replacing a telepad recreates its BlockEntity, so this
     * must stay far away from every-tick.
     */
    private static final int VERIFY_INTERVAL_TICKS = 100;

    // Track created telepads for verification and cleanup (zoneId or slotId -> telepad)
    private final Map<String, TrackedTelepad> telepads = new ConcurrentHashMap<>();

    private int tickCounter = 0;

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

        LOGGER.info("[NexusPortals] Created {} zone portals", telepads.size());
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

        BlockPos telepadPos = Objects.requireNonNull(hubOrigin.offset(portalOffset));

        // Build decorative platform under telepad
        buildTelepadPlatform(level, telepadPos);

        // Place the telepad block
        placeTelepad(level, telepadPos, zone.zoneId());

        telepads.put(Objects.requireNonNull(zone.zoneId()),
            new TrackedTelepad(Objects.requireNonNull(UUID.randomUUID()), telepadPos));

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

        TrackedTelepad existing = telepads.get(slotId);
        if (existing != null) {
            LOGGER.debug("[NexusPortals] Slot telepad '{}' already exists", slotId);
            return existing.id();
        }

        // Build decorative platform and place telepad
        buildTelepadPlatform(level, portalPosition);
        placeTelepad(level, portalPosition, slotId);

        // Track with generated UUID
        UUID telepadId = Objects.requireNonNull(UUID.randomUUID());
        telepads.put(slotId, new TrackedTelepad(telepadId, portalPosition));

        LOGGER.debug("[NexusPortals] Created slot telepad '{}' at {}",
            slotId, portalPosition);
        return telepadId;
    }

    /**
     * Remove a portal for a Nexus zone slot.
     *
     * <p>Only the telepad block is removed. Its platform is written at the slot floor level,
     * so clearing that too would punch a hole through the hub floor.
     *
     * @param level the Nexus dimension
     * @param slotId the slot ID
     * @return true if removed
     */
    public boolean removeSlotPortal(@Nonnull ServerLevel level, @Nonnull String slotId) {
        Objects.requireNonNull(level);
        Objects.requireNonNull(slotId);

        TrackedTelepad telepad = telepads.remove(slotId);
        if (telepad == null) {
            return false;
        }

        BlockPos pos = telepad.position();
        if (isTelepadAt(level, pos)) {
            level.setBlock(pos, Objects.requireNonNull(Blocks.AIR.defaultBlockState()), 3);
        }

        LOGGER.debug("[NexusPortals] Removed slot portal '{}' at {}", slotId, pos);
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

        if (++tickCounter < VERIFY_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;

        if (telepads.isEmpty()) {
            return;
        }

        // Verify telepads still exist (rebuild if griefed). The tracked block, not a
        // PortalData, is what was placed, so existence is a blockstate check at the
        // recorded position.
        ZoneRegistry zoneRegistry = getZoneRegistry(level.getServer());

        for (Map.Entry<String, TrackedTelepad> entry : telepads.entrySet()) {
            String zoneId = entry.getKey();
            TrackedTelepad telepad = entry.getValue();

            if (isTelepadAt(level, telepad.position())) {
                continue;
            }

            LOGGER.debug("[NexusPortals] Recreating destroyed telepad for {}", zoneId);
            zoneRegistry.getZoneById(Objects.requireNonNull(zoneId)).ifPresent(zone ->
                createZonePortalDataDriven(level, hubOrigin, Objects.requireNonNull(zone)));
        }
    }

    /**
     * Check whether a telepad block still stands at a tracked position.
     *
     * <p>Positions outside a loaded chunk read as absent, so this is only called for the
     * Nexus hub, whose chunks are force-loaded.
     */
    private boolean isTelepadAt(@Nonnull ServerLevel level, @Nonnull BlockPos pos) {
        return level.getBlockState(pos).is(CloneBlocks.TELEPAD.get());
    }

    /**
     * Stop tracking all zone telepads.
     *
     * <p>Blocks are deliberately left in the world: this runs on shutdown as well as before
     * a rebuild, and the hub is expected to persist across restarts.
     */
    public void cleanup(@Nonnull ServerLevel level) {
        Objects.requireNonNull(level);
        telepads.clear();

        LOGGER.debug("[NexusPortals] Cleaned up zone portal tracking");
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
     * A telepad this manager placed, and where it placed it.
     */
    private record TrackedTelepad(
        @Nonnull UUID id,
        @Nonnull BlockPos position
    ) {
        private TrackedTelepad {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(position, "position");
        }
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
