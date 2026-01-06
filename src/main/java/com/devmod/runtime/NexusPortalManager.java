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

import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.config.Config;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Manages portal pedestals in the Nexus hub for visual zone navigation.
 * Each zone has a portal pedestal that players can interact with to teleport.
 */
public final class NexusPortalManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(NexusPortalManager.class);

    public static final NexusPortalManager INSTANCE = new NexusPortalManager();

    public static final String PORTAL_TAG = "devmod_nexus_portal";
    private static final String PORTAL_ZONE_PREFIX = "devmod_portal_zone_";

    // Portal pedestal positions relative to hub center (near zone entrances)
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

    // Portal colors (ARGB) for each zone
    private static final Map<NexusSpawnManager.Zone, Integer> ZONE_COLORS = new EnumMap<>(NexusSpawnManager.Zone.class);

    static {
        ZONE_COLORS.put(NexusSpawnManager.Zone.COMBAT, DesignTokens.Nexus.ZONE_COMBAT);
        ZONE_COLORS.put(NexusSpawnManager.Zone.ARENA, DesignTokens.Nexus.ZONE_ARENA);
        ZONE_COLORS.put(NexusSpawnManager.Zone.UI, DesignTokens.Nexus.ZONE_UI);
        ZONE_COLORS.put(NexusSpawnManager.Zone.TELEMETRY, DesignTokens.Nexus.ZONE_TELEMETRY);
        ZONE_COLORS.put(NexusSpawnManager.Zone.SHOWCASE, DesignTokens.Nexus.ZONE_SHOWCASE);
        ZONE_COLORS.put(NexusSpawnManager.Zone.INTEGRATION, DesignTokens.Nexus.ZONE_INTEGRATION);
        ZONE_COLORS.put(NexusSpawnManager.Zone.SANDBOX, DesignTokens.Nexus.ZONE_SANDBOX);
        ZONE_COLORS.put(NexusSpawnManager.Zone.MECHANICS, DesignTokens.Nexus.ZONE_MECHANICS);
    }

    private final Map<NexusSpawnManager.Zone, UUID> portalEntities = new EnumMap<>(NexusSpawnManager.Zone.class);
    private int particleTick = 0;

    private NexusPortalManager() {}

    /**
     * Initialize portal pedestals in the Nexus hub.
     */
    public void initialize(@Nonnull ServerLevel level, @Nonnull BlockPos hubOrigin) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(hubOrigin, "hubOrigin");

        if (!Config.NEXUS_PORTALS_ENABLED.get()) {
            return;
        }

        LOGGER.info("[NexusPortals] Initializing portal pedestals");

        // Clear existing portals
        cleanup(level);

        // Create portal for each zone
        for (NexusSpawnManager.Zone zone : PORTAL_OFFSETS.keySet()) {
            createPortalPedestal(level, hubOrigin, Objects.requireNonNull(zone, "zone"));
        }
    }

    /**
     * Create a portal pedestal for a specific zone.
     */
    private void createPortalPedestal(@Nonnull ServerLevel level, @Nonnull BlockPos hubOrigin,
                                       @Nonnull NexusSpawnManager.Zone zone) {
        BlockPos offset = PORTAL_OFFSETS.get(zone);
        if (offset == null) {
            return;
        }

        BlockPos portalPos = Objects.requireNonNull(hubOrigin.offset(offset), "portalPos");
        Vec3 spawnPos = Objects.requireNonNull(Vec3.atBottomCenterOf(portalPos), "spawnPos");

        // Create invisible armor stand as portal marker
        ArmorStand portal = new ArmorStand(Objects.requireNonNull(EntityType.ARMOR_STAND, "EntityType.ARMOR_STAND"), level);
        portal.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        portal.setInvisible(true);
        portal.setNoGravity(true);
        portal.setInvulnerable(true);
        portal.setCustomName(Objects.requireNonNull(Component.literal(Objects.requireNonNull(zone.label(), "zone.label")), "customName"));
        portal.setCustomNameVisible(true);
        portal.setSilent(true);
        portal.setNoBasePlate(true);
        portal.setShowArms(false);

        // Tag for identification
        portal.addTag(PORTAL_TAG);
        portal.addTag(PORTAL_ZONE_PREFIX + zone.id());

        level.addFreshEntity(portal);
        portalEntities.put(zone, portal.getUUID());

        LOGGER.debug("[NexusPortals] Created portal for {} at {}", zone.id(), portalPos);
    }

    /**
     * Tick the portal system - spawn particles around portals.
     */
    public void tick(@Nonnull ServerLevel level, @Nonnull BlockPos hubOrigin) {
        if (!Config.NEXUS_PORTALS_ENABLED.get()) {
            return;
        }

        particleTick++;
        int particleInterval = Config.NEXUS_PORTAL_PARTICLE_INTERVAL.get();
        if (particleTick < particleInterval) {
            return;
        }
        particleTick = 0;

        // Spawn particles at each portal
        for (Map.Entry<NexusSpawnManager.Zone, UUID> entry : portalEntities.entrySet()) {
            NexusSpawnManager.Zone zone = Objects.requireNonNull(entry.getKey(), "zone");
            UUID portalId = Objects.requireNonNull(entry.getValue(), "portalId");

            Entity portal = level.getEntity(portalId);
            if (portal == null) {
                // Portal was removed, recreate it
                createPortalPedestal(level, hubOrigin, zone);
                continue;
            }

            spawnPortalParticles(level, Objects.requireNonNull(portal.position(), "portal.position"), zone);
        }
    }

    /**
     * Spawn particle effects around a portal.
     */
    private void spawnPortalParticles(@Nonnull ServerLevel level, @Nonnull Vec3 pos,
                                       @Nonnull NexusSpawnManager.Zone zone) {
        // Spiral upward particles
        level.sendParticles(
            Objects.requireNonNull(ParticleTypes.PORTAL, "ParticleTypes.PORTAL"),
            pos.x, pos.y + 0.5, pos.z,
            5,
            0.3, 0.5, 0.3,
            0.05
        );

        // Zone-specific ambient particles
        switch (zone) {
            case COMBAT -> level.sendParticles(Objects.requireNonNull(ParticleTypes.FLAME, "ParticleTypes.FLAME"), pos.x, pos.y + 1.0, pos.z, 2, 0.2, 0.3, 0.2, 0.01);
            case ARENA -> level.sendParticles(Objects.requireNonNull(ParticleTypes.LAVA, "ParticleTypes.LAVA"), pos.x, pos.y + 1.0, pos.z, 1, 0.2, 0.2, 0.2, 0.0);
            case UI -> level.sendParticles(Objects.requireNonNull(ParticleTypes.SOUL_FIRE_FLAME, "ParticleTypes.SOUL_FIRE_FLAME"), pos.x, pos.y + 1.0, pos.z, 2, 0.2, 0.3, 0.2, 0.01);
            case TELEMETRY -> level.sendParticles(Objects.requireNonNull(ParticleTypes.HAPPY_VILLAGER, "ParticleTypes.HAPPY_VILLAGER"), pos.x, pos.y + 1.0, pos.z, 2, 0.3, 0.3, 0.3, 0.0);
            case SHOWCASE -> level.sendParticles(Objects.requireNonNull(ParticleTypes.END_ROD, "ParticleTypes.END_ROD"), pos.x, pos.y + 1.0, pos.z, 2, 0.2, 0.4, 0.2, 0.02);
            case INTEGRATION -> level.sendParticles(Objects.requireNonNull(ParticleTypes.WITCH, "ParticleTypes.WITCH"), pos.x, pos.y + 1.0, pos.z, 2, 0.2, 0.3, 0.2, 0.0);
            case SANDBOX -> level.sendParticles(Objects.requireNonNull(ParticleTypes.DRIPPING_WATER, "ParticleTypes.DRIPPING_WATER"), pos.x, pos.y + 1.5, pos.z, 2, 0.3, 0.2, 0.3, 0.0);
            case MECHANICS -> level.sendParticles(Objects.requireNonNull(ParticleTypes.SMOKE, "ParticleTypes.SMOKE"), pos.x, pos.y + 1.0, pos.z, 2, 0.2, 0.3, 0.2, 0.01);
            default -> {}
        }
    }

    /**
     * Handle player interaction with a portal pedestal.
     * Returns true if the interaction was handled.
     */
    public boolean handleInteraction(@Nonnull ServerPlayer player, @Nonnull Entity target) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(target, "target");

        if (!target.getTags().contains(PORTAL_TAG)) {
            return false;
        }

        // Find which zone this portal belongs to
        NexusSpawnManager.Zone targetZone = null;
        for (NexusSpawnManager.Zone zone : NexusSpawnManager.Zone.values()) {
            if (target.getTags().contains(PORTAL_ZONE_PREFIX + zone.id())) {
                targetZone = zone;
                break;
            }
        }

        if (targetZone == null) {
            return false;
        }

        // Teleport player to the zone
        teleportToZone(player, targetZone);
        return true;
    }

    /**
     * Teleport a player to a specific zone.
     */
    public void teleportToZone(@Nonnull ServerPlayer player, @Nonnull NexusSpawnManager.Zone zone) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(zone, "zone");

        BlockPos hubOrigin = NexusDimensionManager.getHubOrigin();
        BlockPos destination = NexusSpawnManager.getSpawnForZone(hubOrigin, zone);

        // Play departure effect
        playTeleportEffect(player, true);

        // Teleport
        player.teleportTo(destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5);

        // Play arrival effect
        playTeleportEffect(player, false);

        // Send message
        player.displayClientMessage(
            Objects.requireNonNull(Component.literal("Teleported to " + zone.label()), "teleportMessage"),
            true
        );

        LOGGER.debug("[NexusPortals] {} teleported to {}", player.getName().getString(), zone.id());
    }

    /**
     * Play teleport visual and sound effects.
     */
    private void playTeleportEffect(@Nonnull ServerPlayer player, boolean isDeparture) {
        ServerLevel level = player.serverLevel();
        Vec3 pos = player.position();

        BlockPos playerBlockPos = Objects.requireNonNull(player.blockPosition(), "player.blockPosition");

        if (isDeparture) {
            // Departure: imploding particles
            level.sendParticles(
                Objects.requireNonNull(ParticleTypes.REVERSE_PORTAL, "ParticleTypes.REVERSE_PORTAL"),
                pos.x, pos.y + 1.0, pos.z,
                30,
                0.5, 1.0, 0.5,
                0.1
            );
            level.playSound(null, playerBlockPos,
                Objects.requireNonNull(SoundEvents.ENDERMAN_TELEPORT, "SoundEvents.ENDERMAN_TELEPORT"), SoundSource.PLAYERS, 0.8f, 1.2f);
        } else {
            // Arrival: exploding particles
            level.sendParticles(
                Objects.requireNonNull(ParticleTypes.PORTAL, "ParticleTypes.PORTAL"),
                pos.x, pos.y + 1.0, pos.z,
                40,
                0.5, 1.0, 0.5,
                0.2
            );
            level.playSound(null, playerBlockPos,
                Objects.requireNonNull(SoundEvents.CHORUS_FRUIT_TELEPORT, "SoundEvents.CHORUS_FRUIT_TELEPORT"), SoundSource.PLAYERS, 0.6f, 1.0f);
        }
    }

    /**
     * Find a portal entity near a position.
     */
    @Nullable
    public Entity findNearestPortal(@Nonnull ServerLevel level, @Nonnull Vec3 pos, double radius) {
        Vec3 minCorner = Objects.requireNonNull(pos.subtract(radius, radius, radius), "minCorner");
        Vec3 maxCorner = Objects.requireNonNull(pos.add(radius, radius, radius), "maxCorner");
        AABB searchBox = new AABB(minCorner, maxCorner);
        List<Entity> entities = level.getEntities((Entity) null, searchBox,
            e -> e.getTags().contains(PORTAL_TAG));

        if (entities.isEmpty()) {
            return null;
        }

        Entity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (Entity entity : entities) {
            double dist = entity.position().distanceToSqr(pos);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = entity;
            }
        }
        return nearest;
    }

    /**
     * Get the zone for a portal entity.
     */
    @Nullable
    public NexusSpawnManager.Zone getPortalZone(@Nonnull Entity portal) {
        for (NexusSpawnManager.Zone zone : NexusSpawnManager.Zone.values()) {
            if (portal.getTags().contains(PORTAL_ZONE_PREFIX + zone.id())) {
                return zone;
            }
        }
        return null;
    }

    /**
     * Get the color for a zone (for client rendering).
     */
    public int getZoneColor(@Nonnull NexusSpawnManager.Zone zone) {
        return ZONE_COLORS.getOrDefault(zone, DesignTokens.Nexus.WHITE);
    }

    /**
     * Get all portal positions for client rendering.
     */
    @Nonnull
    public List<PortalInfo> getPortalInfoList(@Nonnull BlockPos hubOrigin) {
        List<PortalInfo> list = new ArrayList<>();
        for (Map.Entry<NexusSpawnManager.Zone, BlockPos> entry : PORTAL_OFFSETS.entrySet()) {
            NexusSpawnManager.Zone zone = Objects.requireNonNull(entry.getKey(), "zone");
            BlockPos pos = Objects.requireNonNull(hubOrigin.offset(Objects.requireNonNull(entry.getValue(), "entry.getValue")), "pos");
            int color = getZoneColor(zone);
            list.add(new PortalInfo(zone, pos, color));
        }
        return list;
    }

    /**
     * Clean up all portal entities.
     */
    public void cleanup(@Nonnull ServerLevel level) {
        // Remove all portal entities
        for (UUID portalId : portalEntities.values()) {
            Entity portal = level.getEntity(Objects.requireNonNull(portalId, "portalId"));
            if (portal != null) {
                portal.discard();
            }
        }
        portalEntities.clear();

        // Also scan for any orphaned portals
        List<Entity> toRemove = new ArrayList<>();
        for (Entity entity : level.getEntities().getAll()) {
            if (entity.getTags().contains(PORTAL_TAG)) {
                toRemove.add(entity);
            }
        }
        for (Entity entity : toRemove) {
            entity.discard();
        }

        LOGGER.debug("[NexusPortals] Cleaned up portal entities");
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
