package com.devmod.runtime;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import com.devmod.config.Config;
import com.devmod.hologram.runtime.HologramManager;
import com.devmod.nexus.runtime.NexusHubManager;
import com.devmod.zone.data.ZoneDefinition;
import com.devmod.zone.runtime.ZoneResolver;

/**
 * Performance optimization manager for the Nexus dimension.
 * Implements lazy chunk loading and entity culling per zone.
 */
public final class NexusPerformanceManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(NexusPerformanceManager.class);

    public static final NexusPerformanceManager INSTANCE = new NexusPerformanceManager();

    // Chunk loading settings
    private static final int DEFAULT_VIEW_DISTANCE = 4; // chunks
    private static final int ZONE_CHUNK_RADIUS = 3;     // chunks around zone center

    // Entity culling settings
    private static final int CULL_CHECK_INTERVAL = 20;  // ticks between cull checks

    // Player zone tracking for optimization (stores zone ID strings)
    private final Map<UUID, String> playerZones = new ConcurrentHashMap<>();
    private final Map<UUID, Set<ChunkPos>> playerLoadedChunks = new ConcurrentHashMap<>();

    // Zone chunk cache (keyed by zone ID)
    private final Map<String, Set<ChunkPos>> zoneChunks = new ConcurrentHashMap<>();

    // Culled entities tracking
    private final Set<UUID> culledEntities = ConcurrentHashMap.newKeySet();

    // Visible across ticks on server thread
    private int cullTick = 0;
    private volatile boolean initialized = false;

    private NexusPerformanceManager() {}

    /**
     * Initialize the performance manager with zone chunk mappings.
     */
    public void initialize(@Nonnull ServerLevel level, @Nonnull BlockPos hubOrigin) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(hubOrigin, "hubOrigin");

        if (initialized) {
            return;
        }

        LOGGER.info("[NexusPerf] Initializing performance optimizations");

        var server = level.getServer();
        if (server == null) {
            LOGGER.warn("[NexusPerf] Cannot initialize performance manager: server not available");
            return;
        }
        NexusHubManager.INSTANCE.initialize(server);

        // Pre-calculate chunk positions for each zone from the registry
        var registry = com.devmod.zone.data.ZoneRegistry.get(server);
        for (ZoneDefinition zone : registry.getAllZones()) {
            BlockPos zoneCenter = zone.bounds().center();
            Set<ChunkPos> chunks = calculateZoneChunks(zoneCenter);
            zoneChunks.put(zone.zoneId(), chunks);
        }

        initialized = true;
        LOGGER.info("[NexusPerf] Calculated chunk mappings for {} zones", zoneChunks.size());
    }

    /**
     * Calculate the set of chunks that belong to a zone.
     */
    @Nonnull
    private Set<ChunkPos> calculateZoneChunks(@Nonnull BlockPos center) {
        Set<ChunkPos> chunks = new HashSet<>();
        ChunkPos centerChunk = new ChunkPos(center);

        for (int dx = -ZONE_CHUNK_RADIUS; dx <= ZONE_CHUNK_RADIUS; dx++) {
            for (int dz = -ZONE_CHUNK_RADIUS; dz <= ZONE_CHUNK_RADIUS; dz++) {
                chunks.add(new ChunkPos(centerChunk.x + dx, centerChunk.z + dz));
            }
        }

        return chunks;
    }

    /**
     * Tick the performance manager.
     */
    public void tick(@Nonnull ServerLevel level, @Nonnull BlockPos hubOrigin) {
        if (!Config.NEXUS_PERFORMANCE_ENABLED.get()) {
            return;
        }

        if (!initialized) {
            initialize(level, hubOrigin);
        }

        // Update player zones
        updatePlayerZones(level);

        // Entity culling check
        cullTick++;
        if (cullTick >= CULL_CHECK_INTERVAL) {
            cullTick = 0;
            performEntityCulling(level);
        }
    }

    /**
     * Update which zone each player is in.
     */
    private void updatePlayerZones(@Nonnull ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            Optional<ZoneDefinition> zoneOpt = ZoneResolver.INSTANCE.resolve(level, player.blockPosition());
            String currentZone = zoneOpt.map(ZoneDefinition::zoneId).orElse("outside");
            String previousZone = playerZones.put(player.getUUID(), currentZone);

            if (previousZone != null && !previousZone.equals(currentZone)) {
                onPlayerZoneChange(player, previousZone, currentZone);
            }
        }

        // Clean up disconnected players
        Set<UUID> activePlayers = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            activePlayers.add(player.getUUID());
        }
        playerZones.keySet().retainAll(activePlayers);
        playerLoadedChunks.keySet().retainAll(activePlayers);
    }

    /**
     * Handle player zone change for chunk loading optimization.
     */
    private void onPlayerZoneChange(@Nonnull ServerPlayer player,
                                     @Nonnull String oldZone,
                                     @Nonnull String newZone) {
        if (!Config.NEXUS_LAZY_CHUNKS_ENABLED.get()) {
            return;
        }

        UUID playerId = player.getUUID();

        // Get chunks for new zone
        Set<ChunkPos> newChunks = zoneChunks.getOrDefault(newZone, getHubChunks(player.blockPosition()));

        // Track loaded chunks for this player
        playerLoadedChunks.put(playerId, new HashSet<>(newChunks));

        LOGGER.debug("[NexusPerf] Player {} moved from {} to {}, adjusted chunk set",
            player.getName().getString(), oldZone, newZone);
    }

    /**
     * Get chunks for the central hub area.
     */
    @Nonnull
    private Set<ChunkPos> getHubChunks(@Nonnull BlockPos hubOrigin) {
        Set<ChunkPos> chunks = new HashSet<>();
        ChunkPos centerChunk = new ChunkPos(hubOrigin);

        int hubRadius = DEFAULT_VIEW_DISTANCE;
        for (int dx = -hubRadius; dx <= hubRadius; dx++) {
            for (int dz = -hubRadius; dz <= hubRadius; dz++) {
                chunks.add(new ChunkPos(centerChunk.x + dx, centerChunk.z + dz));
            }
        }

        return chunks;
    }

    /**
     * Perform entity culling - hide entities far from players.
     */
    private void performEntityCulling(@Nonnull ServerLevel level) {
        if (!Config.NEXUS_ENTITY_CULLING_ENABLED.get()) {
            return;
        }

        Set<UUID> entitiesToCull = new HashSet<>();
        Set<UUID> entitiesToShow = new HashSet<>();

        // Get all player positions
        Set<Vec3> playerPositions = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            playerPositions.add(player.position());
        }

        // Check each non-player entity
        for (Entity entity : level.getEntities().getAll()) {
            if (entity instanceof ServerPlayer) {
                continue; // Never cull players
            }

            // Skip important entities
            if (isImportantEntity(nn(entity, "entity"))) {
                continue;
            }

            // Check distance to nearest player
            Vec3 entityPos = entity.position();
            double nearestDistSq = Double.MAX_VALUE;

            for (Vec3 playerPos : playerPositions) {
                double distSq = entityPos.distanceToSqr(nn(playerPos, "playerPos"));
                if (distSq < nearestDistSq) {
                    nearestDistSq = distSq;
                }
            }

            // Decide whether to cull
            double cullDistanceSq = Config.NEXUS_CULL_DISTANCE.get() * Config.NEXUS_CULL_DISTANCE.get();
            if (nearestDistSq > cullDistanceSq) {
                entitiesToCull.add(entity.getUUID());
            } else if (culledEntities.contains(entity.getUUID())) {
                entitiesToShow.add(entity.getUUID());
            }
        }

        // Apply culling
        for (UUID entityId : entitiesToCull) {
            Entity entity = level.getEntity(nn(entityId, "entityId"));
            if (entity != null && !culledEntities.contains(entityId)) {
                cullEntity(entity);
                culledEntities.add(entityId);
            }
        }

        // Restore shown entities
        for (UUID entityId : entitiesToShow) {
            Entity entity = level.getEntity(nn(entityId, "entityId"));
            if (entity != null) {
                uncullEntity(entity);
                culledEntities.remove(entityId);
            }
        }

        if (!entitiesToCull.isEmpty() || !entitiesToShow.isEmpty()) {
            LOGGER.debug("[NexusPerf] Culled {} entities, restored {}",
                entitiesToCull.size(), entitiesToShow.size());
        }
    }

    /**
     * Check if an entity should never be culled.
     */
    private boolean isImportantEntity(@Nonnull Entity entity) {
        // Never cull NPC entities (PlayerCloneEntity in NPC mode)
        if (entity instanceof com.devmod.clone.entity.PlayerCloneEntity clone && clone.isNpc()) {
            return true;
        }
        // Check for hologram tags
        Set<String> tags = entity.getTags();
        if (tags.contains(HologramManager.HOLOGRAM_TAG)) {
            return true;
        }

        // Never cull named entities
        if (entity.hasCustomName()) {
            return true;
        }

        return false;
    }

    /**
     * Cull an entity (make it invisible and non-ticking).
     */
    private void cullEntity(@Nonnull Entity entity) {
        entity.setNoGravity(true);
        // Additional culling logic could pause AI, etc.
        // For now we just track it for restoration
    }

    /**
     * Restore a culled entity.
     */
    private void uncullEntity(@Nonnull Entity entity) {
        // Restore normal entity behavior
        // Only restore gravity if entity normally has it
        if (!(entity instanceof net.minecraft.world.entity.decoration.ArmorStand)) {
            entity.setNoGravity(false);
        }
    }

    /**
     * Get the current zone ID for a player.
     */
    @Nullable
    public String getPlayerZone(@Nonnull UUID playerId) {
        return playerZones.get(playerId);
    }

    /**
     * Get chunks that should be loaded for a player based on their zone.
     */
    @Nonnull
    public Set<ChunkPos> getChunksForPlayer(@Nonnull UUID playerId, @Nonnull BlockPos hubOrigin) {
        Set<ChunkPos> chunks = playerLoadedChunks.get(playerId);
        if (chunks != null) {
            return new HashSet<>(chunks);
        }
        return getHubChunks(hubOrigin);
    }

    /**
     * Check if a chunk should be loaded based on player positions.
     */
    public boolean shouldLoadChunk(@Nonnull ChunkPos chunk, @Nonnull ServerLevel level) {
        if (!Config.NEXUS_LAZY_CHUNKS_ENABLED.get()) {
            return true; // Load all chunks if optimization disabled
        }

        // Check if any player needs this chunk
        for (Set<ChunkPos> playerChunks : playerLoadedChunks.values()) {
            if (playerChunks.contains(chunk)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get performance statistics.
     */
    @Nonnull
    public PerformanceStats getStats() {
        int totalCulled = culledEntities.size();
        int trackedPlayers = playerZones.size();
        int totalZoneChunks = zoneChunks.values().stream().mapToInt(Set::size).sum();

        return new PerformanceStats(totalCulled, trackedPlayers, totalZoneChunks, initialized);
    }

    /**
     * Clean up when shutting down.
     */
    public void cleanup() {
        playerZones.clear();
        playerLoadedChunks.clear();
        zoneChunks.clear();
        culledEntities.clear();
        initialized = false;
        LOGGER.debug("[NexusPerf] Cleaned up performance manager state");
    }

    /**
     * Performance statistics record.
     */
    public record PerformanceStats(
        int culledEntities,
        int trackedPlayers,
        int zoneChunks,
        boolean initialized
    ) {}

    @Nonnull
    private static <T> T nn(T value, String context) {
        return Objects.requireNonNull(value, context);
    }
}
