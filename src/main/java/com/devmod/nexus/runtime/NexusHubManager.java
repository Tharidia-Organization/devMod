package com.devmod.nexus.runtime;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.devmod.DevMod;
import com.devmod.area.builder.AreaBuildTaskManager;
import com.devmod.area.data.AreaDefinition;
import com.devmod.area.data.AreaDimensions;
import com.devmod.area.data.AreaRegistry;
import com.devmod.config.Config;
import com.devmod.nexus.builder.NexusFoundationBuilder;
import com.devmod.nexus.data.SlotType;
import com.devmod.nexus.data.ZoneSlot;
import com.devmod.nexus.data.ZoneSlotPresets;
import com.devmod.nexus.data.ZoneSlotRegistry;
import com.devmod.runtime.NexusDimensionManager;
import com.devmod.runtime.NexusPortalManager;
import com.devmod.shared.SharedColorTokens;
import com.devmod.template.runtime.TemplateManager;
import com.devmod.transport.TransportRegistry;
import com.devmod.transport.bridge.NexusTransportBridge;
import com.devmod.zone.data.ZoneBounds;
import com.devmod.zone.data.ZoneDefinition;
import com.devmod.zone.data.ZoneRegistry;
/**
 * Central manager for the Nexus hub system.
 * Orchestrates slot management, area linking, and module integration.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Hub initialization and foundation building</li>
 *   <li>Slot-to-area linking and unlinking</li>
 *   <li>Coordination with Area, Zone, Portal, Transport modules</li>
 *   <li>Runtime event handling</li>
 * </ul>
 */
@EventBusSubscriber(modid = DevMod.MODID)
public final class NexusHubManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(NexusHubManager.class);

    /** Singleton instance */
    public static final NexusHubManager INSTANCE = new NexusHubManager();

    /** Whether the hub has been initialized this session */
    private volatile boolean hubInitialized = false;

    /** Tick counter for periodic tasks */
    private int tickCounter = 0;

    private NexusHubManager() {}

    // ========================================================================
    // Initialization
    // ========================================================================

    /**
     * Initialize the Nexus hub system.
     * Called after server start when Nexus dimension is ready.
     *
     * @param server the Minecraft server
     */
    public void initialize(@Nonnull MinecraftServer server) {
        Objects.requireNonNull(server);
        boolean firstInit = !hubInitialized;
        if (firstInit) {
            LOGGER.info("[Nexus] Initializing hub system...");
        } else {
            LOGGER.debug("[Nexus] Hub already initialized this session");
        }

        // Get or create slot registry
        ZoneSlotRegistry slotRegistry = ZoneSlotRegistry.get(server);

        // Initialize default slots if not already done
        if (!slotRegistry.isInitialized()) {
            slotRegistry.initializeDefaultSlots();
            LOGGER.info("[Nexus] Default slots initialized");
        }

        ensureZoneRegistry(server, slotRegistry);
        initializeTransportNodes(server);

        hubInitialized = true;
        if (firstInit) {
            LOGGER.info("[Nexus] Hub system initialized with {} slots", slotRegistry.getSlotCount());
        }
    }

    private void ensureZoneRegistry(@Nonnull MinecraftServer server, @Nonnull ZoneSlotRegistry slotRegistry) {
        if (Config.NEXUS_AUTO_CREATE_ZONES.get()) {
            syncSlotZones(server, slotRegistry);
            return;
        }

        ZoneRegistry zones = ZoneRegistry.get(server);
        if (!zones.isInitialized()) {
            zones.initializeWithLegacyZones(Objects.requireNonNull(NexusDimensionManager.getHubOrigin()));
        }
    }

    private void syncSlotZones(@Nonnull MinecraftServer server, @Nonnull ZoneSlotRegistry slotRegistry) {
        ZoneRegistry zones = ZoneRegistry.get(server);
        BlockPos hubOrigin = NexusDimensionManager.getHubOrigin();

        for (ZoneSlot slot : slotRegistry.getAllSlots()) {
            ZoneDefinition target = buildZoneDefinition(Objects.requireNonNull(slot), Objects.requireNonNull(hubOrigin));
            Optional<ZoneDefinition> existing = zones.getZoneById(Objects.requireNonNull(target.zoneId()));
            ZoneDefinition updated;

            if (existing.isPresent()) {
                ZoneDefinition current = existing.get();
                updated = current.toBuilder()
                    .displayName(target.displayName())
                    .bounds(target.bounds())
                    .spawnOffset(target.spawnOffset())
                    .color(target.color())
                    .cueSound(target.cueSound())
                    .hintMessage(target.hintMessage())
                    .allowBuilding(target.allowBuilding())
                    .isSpecial(target.isSpecial())
                    .hasTeleport(target.hasTeleport())
                    .portalOffset(target.portalOffset())
                    .portalColor(target.portalColor())
                    .priority(target.priority())
                    .build();
                zones.updateZone(current.id(), updated);
            } else {
                zones.registerZone(target);
                updated = target;
            }

            slotRegistry.linkZone(Objects.requireNonNull(slot.slotId()), updated.id());
        }

        registerSlotAliases(zones);
        zones.markInitialized();
    }

    private ZoneDefinition buildZoneDefinition(@Nonnull ZoneSlot slot, @Nonnull BlockPos hubOrigin) {
        BlockPos spawnPos = slot.bounds().floorCenter();
        BlockPos spawnOffset = toOffset(spawnPos, hubOrigin);

        boolean allowBuilding = slot.permissions().canPlace() || slot.permissions().canBreak();
        boolean hasTeleport = slot.permissions().canUsePortals();
        boolean isSpecial = slot.type() == SlotType.RESTRICTED;

        BlockPos portalOffset = null;
        if (hasTeleport) {
            BlockPos portalAbs = slot.getAbsolutePortalPosition();
            portalOffset = toOffset(portalAbs, hubOrigin);
        }

        return ZoneDefinition.builder(Objects.requireNonNull(slot.slotId()))
            .displayName(Objects.requireNonNull(slot.displayName()))
            .bounds(Objects.requireNonNull(slot.bounds()))
            .spawnOffset(spawnOffset)
            .color(resolvePortalColor(Objects.requireNonNull(slot.portalColor())))
            .cueSound(Objects.requireNonNull(SoundEvents.NOTE_BLOCK_CHIME.value()))
            .hintMessage("Zone: " + slot.displayName())
            .allowBuilding(allowBuilding)
            .isSpecial(isSpecial)
            .hasTeleport(hasTeleport)
            .portalOffset(portalOffset)
            .portalColor(hasTeleport ? slot.portalColor() : null)
            .priority(slot.priority())
            .build();
    }

    private static BlockPos toOffset(@Nonnull BlockPos absolute, @Nonnull BlockPos origin) {
        return new BlockPos(
            absolute.getX() - origin.getX(),
            absolute.getY() - origin.getY(),
            absolute.getZ() - origin.getZ()
        );
    }

    private static int resolvePortalColor(@Nonnull com.devmod.portal.PortalColor portalColor) {
        return SharedColorTokens.Mask.ALPHA | portalColor.getColor();
    }

    private void registerSlotAliases(@Nonnull ZoneRegistry zones) {
        registerAliasIfPresent(zones, "hub", "spawn");
        registerAliasIfPresent(zones, "home", "spawn");
        registerAliasIfPresent(zones, "overview", "spawn");

        if (zones.resolveAlias("combat") == null) {
            if (zones.getZoneById("tutorial").isPresent()) {
                zones.registerAlias("combat", "tutorial");
            }
        }

        if (zones.resolveAlias("arena") == null) {
            if (zones.getZoneById("war_hub_east").isPresent()) {
                zones.registerAlias("arena", "war_hub_east");
            } else if (zones.getZoneById("war_hub_west").isPresent()) {
                zones.registerAlias("arena", "war_hub_west");
            }
        }

        registerAliasIfPresent(zones, "ui", "dm_mod");
        registerAliasIfPresent(zones, "telemetry", "dm_mod");
    }

    private void registerAliasIfPresent(@Nonnull ZoneRegistry zones, @Nonnull String alias, @Nonnull String target) {
        if (zones.resolveAlias(alias) != null) {
            return;
        }
        if (zones.getZoneById(target).isPresent()) {
            zones.registerAlias(alias, target);
        }
    }

    private void initializeTransportNodes(@Nonnull MinecraftServer server) {
        ServerLevel nexusLevel = server.getLevel(Objects.requireNonNull(NexusDimensionManager.NEXUS_DIMENSION));
        if (nexusLevel == null) {
            LOGGER.warn("[Nexus] Cannot initialize transport nodes: Nexus dimension not available");
            return;
        }
        TransportRegistry registry = TransportRegistry.get(nexusLevel);
        NexusTransportBridge.INSTANCE.initializeNexusNodes(registry, server);
    }

    /**
     * Build the hub foundation in the Nexus dimension.
     * Only builds if not already built.
     *
     * @param level the Nexus dimension level
     * @param origin the hub center position
     * @param force if true, rebuild even if already built
     * @return true if foundation was built
     */
    public boolean buildFoundation(@Nonnull ServerLevel level, @Nonnull BlockPos origin, boolean force) {
        Objects.requireNonNull(level);
        Objects.requireNonNull(origin);

        // Check if already built (use NexusHubSavedData when available)
        // For now, always build if force or first time
        if (!force) {
            // Check a marker block at center
            if (!level.getBlockState(origin).isAir()) {
                LOGGER.debug("[Nexus] Foundation already exists, skipping build");
                return false;
            }
        }

        LOGGER.info("[Nexus] Building hub foundation (force: {})", force);
        NexusFoundationBuilder builder = new NexusFoundationBuilder();
        builder.build(level, origin);
        return true;
    }

    // ========================================================================
    // Slot Management
    // ========================================================================

    /**
     * Link an area to a slot.
     * Validates compatibility and triggers area build at slot location.
     *
     * @param server the server
     * @param slotId the slot to link to
     * @param areaId the area to link
     * @return true if linked successfully
     */
    public boolean linkAreaToSlot(
            @Nonnull MinecraftServer server,
            @Nonnull String slotId,
            @Nonnull UUID areaId
    ) {
        Objects.requireNonNull(server);
        Objects.requireNonNull(slotId);
        Objects.requireNonNull(areaId);

        ZoneSlotRegistry slotRegistry = ZoneSlotRegistry.get(server);
        AreaRegistry areaRegistry = AreaRegistry.get(server);

        // Get slot
        Optional<ZoneSlot> slotOpt = slotRegistry.getSlot(slotId);
        if (slotOpt.isEmpty()) {
            LOGGER.warn("[Nexus] Link failed: slot {} not found", slotId);
            return false;
        }
        ZoneSlot slot = slotOpt.get();

        // Get area
        Optional<AreaDefinition> areaOpt = areaRegistry.getArea(areaId);
        if (areaOpt.isEmpty()) {
            LOGGER.warn("[Nexus] Link failed: area {} not found", areaId);
            return false;
        }
        AreaDefinition area = areaOpt.get();

        // Validate bounds compatibility
        if (!areBoundsCompatible(Objects.requireNonNull(slot.bounds()), Objects.requireNonNull(area))) {
            LOGGER.warn("[Nexus] Link failed: area {} dimensions incompatible with slot {}",
                areaId, slotId);
            return false;
        }

        ensureZoneRegistry(server, slotRegistry);

        // Perform link
        if (!slotRegistry.linkArea(slotId, areaId)) {
            return false;
        }

        AreaDefinition buildArea = alignAreaToSlot(server, area, slot);

        // Queue area build at slot position
        queueAreaBuild(server, Objects.requireNonNull(buildArea), slot);

        // Create portal for the slot
        createPortalForSlot(server, slot);

        LOGGER.info("[Nexus] Linked area '{}' to slot '{}'", area.name(), slotId);
        return true;
    }

    /**
     * Create a portal for a slot at its configured portal position.
     * Respects Config.NEXUS_AUTO_CREATE_PORTALS setting.
     */
    private void createPortalForSlot(@Nonnull MinecraftServer server, @Nonnull ZoneSlot slot) {
        if (!slot.permissions().canUsePortals()) {
            LOGGER.debug("[Nexus] Slot '{}' does not allow portals, skipping portal creation", slot.slotId());
            return;
        }

        // Check config before creating portal
        if (!ZoneSlotPresets.isAutoCreatePortalsEnabled()) {
            LOGGER.debug("[Nexus] Auto-create portals disabled, skipping portal for slot '{}'", slot.slotId());
            return;
        }

        ServerLevel nexusLevel = server.getLevel(Objects.requireNonNull(NexusDimensionManager.NEXUS_DIMENSION));
        if (nexusLevel == null) {
            LOGGER.warn("[Nexus] Cannot create portal: Nexus dimension not available");
            return;
        }

        BlockPos portalPos = slot.getAbsolutePortalPosition();
        BlockPos destination = slot.bounds().floorCenter();

        NexusPortalManager.INSTANCE.createSlotPortal(
            nexusLevel,
            portalPos,
            Objects.requireNonNull(slot.portalColor()),
            destination,
            Objects.requireNonNull(slot.slotId())
        );
    }

    /**
     * Unlink an area from a slot.
     *
     * @param server the server
     * @param slotId the slot to unlink
     * @return true if unlinked successfully
     */
    public boolean unlinkAreaFromSlot(@Nonnull MinecraftServer server, @Nonnull String slotId) {
        Objects.requireNonNull(server);
        Objects.requireNonNull(slotId);

        ZoneSlotRegistry slotRegistry = ZoneSlotRegistry.get(server);

        Optional<ZoneSlot> slotOpt = slotRegistry.getSlot(slotId);
        if (slotOpt.isEmpty()) {
            LOGGER.warn("[Nexus] Unlink failed: slot {} not found", slotId);
            return false;
        }

        ZoneSlot slot = slotOpt.get();
        UUID linkedAreaId = slot.linkedAreaId();
        if (linkedAreaId == null) {
            LOGGER.debug("[Nexus] Slot {} has no linked area", slotId);
            return true;
        }

        if (!slotRegistry.unlinkArea(slotId)) {
            return false;
        }

        if (ZoneSlotPresets.isAutoCreateZonesEnabled()) {
            AreaRegistry areaRegistry = AreaRegistry.get(server);
            areaRegistry.getArea(linkedAreaId).ifPresent(area -> {
                if (Objects.equals(area.linkedZoneId(), slot.slotId())) {
                    AreaDefinition updated = area.withLinkedZone(null);
                    if (!areaRegistry.updateArea(Objects.requireNonNull(area.id()), updated, area.revision())) {
                        LOGGER.warn("[Nexus] Failed to clear linked zone for area {}", area.id());
                    }
                }
            });
        }

        // Remove portal for the slot
        ServerLevel nexusLevel = server.getLevel(Objects.requireNonNull(NexusDimensionManager.NEXUS_DIMENSION));
        if (nexusLevel != null) {
            NexusPortalManager.INSTANCE.removeSlotPortal(nexusLevel, slotId);

            // Optionally clear the area content from the slot location
            if (Config.NEXUS_CLEAR_ON_UNLINK.get()) {
                clearSlotContent(nexusLevel, slot);
            }
        }

        LOGGER.info("[Nexus] Unlinked area from slot '{}'", slotId);
        return true;
    }

    /**
     * Get the slot at a world position.
     */
    @Nonnull
    public Optional<ZoneSlot> getSlotAt(@Nonnull MinecraftServer server, @Nonnull BlockPos pos) {
        return ZoneSlotRegistry.get(Objects.requireNonNull(server))
            .getSlotAt(Objects.requireNonNull(pos));
    }

    // ========================================================================
    // Area Integration
    // ========================================================================

    /**
     * Check if an area's dimensions are compatible with a slot's bounds.
     */
    private boolean areBoundsCompatible(@Nonnull ZoneBounds slotBounds, @Nonnull AreaDefinition area) {
        // Area dimensions should fit within slot bounds
        int areaWidth = area.dimensions().width();
        int areaLength = area.dimensions().length();
        int areaHeight = area.dimensions().height();
        int slotWidth = slotBounds.width();
        int slotLength = slotBounds.length();
        int slotHeight = slotBounds.height();

        // Allow some flexibility - area can be smaller than slot
        return areaWidth <= slotWidth && areaLength <= slotLength && areaHeight <= slotHeight;
    }

    @Nonnull
    private AreaDefinition buildAlignedDefinition(
            @Nonnull AreaDefinition base,
            @Nonnull BlockPos slotCenter,
            @Nonnull AreaDimensions dimensions,
            @Nonnull ResourceLocation nexusDim,
            @Nullable String linkedZoneId
    ) {
        return new AreaDefinition(
            base.id(),
            base.name(),
            base.generationType(),
            base.shape(),
            dimensions,
            slotCenter,
            nexusDim,
            base.palette(),
            base.biomeConfig(),
            base.options(),
            base.creatorUUID(),
            base.createdAt(),
            System.currentTimeMillis(),
            base.revision() + 1,
            base.isMainHub(),
            linkedZoneId,
            base.customShapeNbt()
        );
    }

    @Nonnull
    private AreaDefinition alignAreaToSlot(
            @Nonnull MinecraftServer server,
            @Nonnull AreaDefinition area,
            @Nonnull ZoneSlot slot
    ) {
        BlockPos slotCenter = slot.bounds().floorCenter();
        AreaDimensions alignedDimensions = area.dimensions().withFloorY(slot.bounds().minY());
        ResourceLocation nexusDim = Objects.requireNonNull(NexusDimensionManager.NEXUS_DIMENSION.location());

        String linkedZoneId = area.linkedZoneId();
        if (ZoneSlotPresets.isAutoCreateZonesEnabled()) {
            ZoneRegistry zones = ZoneRegistry.get(server);
            if (zones.getZoneById(Objects.requireNonNull(slot.slotId())).isPresent()) {
                linkedZoneId = slot.slotId();
            }
        }

        boolean changed = !area.centerPosition().equals(slotCenter)
            || !area.dimensions().equals(alignedDimensions)
            || !Objects.equals(area.dimensionId(), nexusDim)
            || !Objects.equals(area.linkedZoneId(), linkedZoneId);

        if (!changed) {
            return area;
        }

        AreaDefinition updated = buildAlignedDefinition(area, slotCenter, Objects.requireNonNull(alignedDimensions), nexusDim, linkedZoneId);
        AreaRegistry areaRegistry = AreaRegistry.get(server);

        try {
            if (areaRegistry.updateArea(Objects.requireNonNull(area.id()), updated, area.revision())) {
                return updated;
            }

            AreaDefinition latest = areaRegistry.getArea(Objects.requireNonNull(area.id())).orElse(area);
            if (latest.revision() != area.revision()) {
                AreaDefinition retry = buildAlignedDefinition(latest, slotCenter, Objects.requireNonNull(alignedDimensions), nexusDim, linkedZoneId);
                if (areaRegistry.updateArea(Objects.requireNonNull(latest.id()), retry, latest.revision())) {
                    return retry;
                }
            }

            LOGGER.warn("[Nexus] Failed to update area {} for slot '{}'", area.id(), slot.slotId());
        } catch (Exception e) {
            LOGGER.warn("[Nexus] Failed to update area {} for slot '{}': {}", area.id(), slot.slotId(), e.getMessage());
        }

        return updated;
    }

    /**
     * Clear the content of a slot by filling it with air.
     * Used when unlinking an area to reset the slot to empty state.
     *
     * @param level the Nexus dimension level
     * @param slot the slot to clear
     */
    private void clearSlotContent(@Nonnull ServerLevel level, @Nonnull ZoneSlot slot) {
        Objects.requireNonNull(level);
        Objects.requireNonNull(slot);

        ZoneBounds bounds = slot.bounds();
        int blocksCleared = 0;

        // Fill the slot bounds with air, leaving the floor intact
        int floorY = bounds.minY();
        int startY = floorY + 1; // Start above the floor

        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int y = startY; y <= bounds.maxY(); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Objects.requireNonNull(Blocks.AIR.defaultBlockState()), 2 | 16);
                        blocksCleared++;
                    }
                }
            }
        }

        LOGGER.info("[Nexus] Cleared slot '{}': {} blocks removed", slot.slotId(), blocksCleared);
    }

    /**
     * Queue an area build at the slot's position.
     *
     * <p>Uses the aligned area definition so builds are anchored to the slot.
     */
    private void queueAreaBuild(
            @Nonnull MinecraftServer server,
            @Nonnull AreaDefinition area,
            @Nonnull ZoneSlot slot
    ) {
        // Get Nexus dimension
        ServerLevel nexusLevel = server.getLevel(Objects.requireNonNull(NexusDimensionManager.NEXUS_DIMENSION));
        if (nexusLevel == null) {
            LOGGER.error("[Nexus] Cannot queue build: Nexus dimension not available");
            return;
        }

        // Log the build request
        BlockPos slotCenter = slot.bounds().floorCenter();
        LOGGER.info("[Nexus] Starting area '{}' build for slot '{}' (slot center: {})",
            area.name(), slot.slotId(), slotCenter);

        try {
            // Use AreaBuildTaskManager to handle the build
            // clearFirst=true to ensure clean slate in slot
            AreaBuildTaskManager.BuildStartResult result =
                AreaBuildTaskManager.INSTANCE.startBuild(nexusLevel, area, null, true);

            switch (result) {
                case STARTED_IMMEDIATE:
                    LOGGER.info("[Nexus] Area '{}' built immediately", area.name());
                    break;
                case STARTED_MULTI_TICK:
                    LOGGER.info("[Nexus] Area '{}' build started (multi-tick)", area.name());
                    break;
                case QUEUED:
                    LOGGER.info("[Nexus] Area '{}' build queued", area.name());
                    break;
                case ALREADY_BUILDING:
                    LOGGER.warn("[Nexus] Area '{}' already building", area.name());
                    break;
                case QUEUE_FULL:
                    LOGGER.error("[Nexus] Build queue full, cannot build area '{}'", area.name());
                    break;
            }
        } catch (Exception e) {
            LOGGER.error("[Nexus] Failed to start area build: {}", e.getMessage());
        }
    }

    /**
     * Apply default templates to empty slots after a hub build.
     */
    public void applySlotTemplates(@Nonnull ServerLevel level) {
        Objects.requireNonNull(level);
        ZoneSlotRegistry slotRegistry = ZoneSlotRegistry.get(level.getServer());

        TemplateManager.INSTANCE.initializePresets(level);

        for (ZoneSlot slot : slotRegistry.getAllSlots()) {
            String templateId = slot.templateId();
            if (templateId == null || templateId.isBlank()) {
                continue;
            }
            if (slot.linkedAreaId() != null) {
                LOGGER.debug("[Nexus] Slot '{}' has linked area, skipping template '{}'", slot.slotId(), templateId);
                continue;
            }

            ZoneBounds bounds = slot.bounds();
            boolean applied = TemplateManager.INSTANCE.applyTemplate(
                level,
                templateId,
                bounds.minX(),
                bounds.minZ(),
                bounds.maxX(),
                bounds.maxZ(),
                bounds.minY(),
                null
            );

            if (applied) {
                LOGGER.info("[Nexus] Applied template '{}' for slot '{}'", templateId, slot.slotId());
            } else {
                LOGGER.warn("[Nexus] Failed to apply template '{}' for slot '{}'", templateId, slot.slotId());
            }
        }
    }

    // ========================================================================
    // Event Handlers
    // ========================================================================

    /**
     * Handle server started event.
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // Initialization is handled by NexusDimensionManager
        // This just ensures the manager is ready
        LOGGER.debug("[Nexus] Server started, hub manager ready");
    }

    /**
     * Handle server tick for periodic tasks.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        INSTANCE.tick(Objects.requireNonNull(event.getServer()));
    }

    private void tick(@Nonnull MinecraftServer server) {
        if (!hubInitialized) {
            return;
        }

        tickCounter++;

        // Ambient particle effects at configured interval
        if (isAmbientEffectsEnabled()) {
            int interval = getAmbientParticleInterval();
            if (tickCounter % interval == 0) {
                spawnAmbientParticles(server);
            }
        }

        // Periodic tasks every 20 ticks (1 second)
        if (tickCounter % 20 == 0) {
            // Could add periodic validation, cleanup, etc.
        }

        // Periodic tasks every 1200 ticks (1 minute)
        if (tickCounter % 1200 == 0) {
            tickCounter = 0; // Reset to prevent overflow
            // Could add less frequent tasks here
        }
    }

    /**
     * Check if ambient effects are enabled in config.
     */
    private boolean isAmbientEffectsEnabled() {
        try {
            return Config.NEXUS_AMBIENT_EFFECTS.get();
        } catch (Exception e) {
            return true; // Default enabled
        }
    }

    /**
     * Get ambient particle interval from config.
     */
    private int getAmbientParticleInterval() {
        try {
            return Config.NEXUS_AMBIENT_PARTICLE_INTERVAL.get();
        } catch (Exception e) {
            return 12; // Default interval
        }
    }

    /**
     * Spawn ambient particles at the hub center.
     * Creates a magical atmosphere around the spawn platform.
     */
    private void spawnAmbientParticles(@Nonnull MinecraftServer server) {
        ServerLevel nexusLevel = server.getLevel(Objects.requireNonNull(NexusDimensionManager.NEXUS_DIMENSION));
        if (nexusLevel == null || nexusLevel.players().isEmpty()) {
            return; // No players to see particles
        }

        int floorY = ZoneSlotPresets.getFloorY();
        double centerX = 0.5;
        double centerZ = 0.5;
        double y = floorY + 2.5;

        // Spawn floating particles around center pillar
        for (int i = 0; i < 3; i++) {
            double angle = Math.random() * Math.PI * 2;
            double radius = 2 + Math.random() * 6;
            double px = centerX + Math.cos(angle) * radius;
            double pz = centerZ + Math.sin(angle) * radius;
            double py = y + Math.random() * 3;

            // End rod particles for magical glow effect
            nexusLevel.sendParticles(
                Objects.requireNonNull(ParticleTypes.END_ROD),
                px, py, pz,
                1,          // count
                0.1, 0.1, 0.1,  // spread
                0.01        // speed
            );
        }

        // Occasional soul particles rising from center
        if (tickCounter % 40 == 0) {
            nexusLevel.sendParticles(
                Objects.requireNonNull(ParticleTypes.SOUL),
                centerX, y, centerZ,
                2,
                0.5, 0.0, 0.5,
                0.02
            );
        }

        // Periodic enchant glint at pillar
        if (tickCounter % 20 == 0) {
            nexusLevel.sendParticles(
                Objects.requireNonNull(ParticleTypes.ENCHANT),
                centerX, y + 1, centerZ,
                5,
                0.3, 0.5, 0.3,
                0.5
            );
        }
    }

    // ========================================================================
    // Status & Info
    // ========================================================================

    /**
     * Get the hub status summary.
     */
    @Nonnull
    public HubStatus getStatus(@Nonnull MinecraftServer server) {
        ZoneSlotRegistry registry = ZoneSlotRegistry.get(Objects.requireNonNull(server));
        return new HubStatus(
            hubInitialized,
            registry.isInitialized(),
            registry.getSlotCount(),
            registry.getLinkedSlots().size(),
            registry.getEmptySlots().size()
        );
    }

    /**
     * Hub status record.
     */
    public record HubStatus(
        boolean sessionInitialized,
        boolean slotsInitialized,
        int totalSlots,
        int linkedSlots,
        int emptySlots
    ) {
        @Override
        public String toString() {
            return String.format(
                "Hub Status: session=%s, slots=%s, total=%d, linked=%d, empty=%d",
                sessionInitialized ? "ready" : "pending",
                slotsInitialized ? "initialized" : "not initialized",
                totalSlots, linkedSlots, emptySlots
            );
        }
    }
}
