package com.frenkvs.devmod.telemetry.room;

import com.frenkvs.devmod.telemetry.RoomDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VOXEL-LAB M52: Entity Count per Room
 *
 * Tracks entity counts in each configured room.
 * Provides breakdown by entity type:
 * - Hostile mobs
 * - Passive mobs
 * - NPCs (villagers, etc.)
 * - Players
 * - Bosses
 *
 * PERFORMANCE FIX: Uses incremental processing - scans one room per tick
 * instead of all rooms at once. This prevents TPS drops when many rooms
 * are configured or structures generate many entities.
 *
 * Processing cycle:
 * - Tick 1: Scan room 0
 * - Tick 2: Scan room 1
 * - ...
 * - Tick N: Scan room N-1, finalize global stats
 * - Tick N+1: Start new cycle
 */
public class RoomEntityCounter {
    public static final RoomEntityCounter INSTANCE = new RoomEntityCounter();

    // Entity counts per room
    private final Map<String, RoomEntityStats> roomStats = new ConcurrentHashMap<>();

    // PERFORMANCE FIX: Incremental processing state
    private int currentRoomIndex = 0;
    private List<RoomDefinition> cachedRooms = new ArrayList<>();
    private long lastRoomCacheTime = 0;
    private static final long ROOM_CACHE_DURATION_MS = 5000; // Re-cache rooms every 5 seconds

    // Global stats (all entities in current dimension)
    private volatile GlobalEntityStats globalStats = new GlobalEntityStats();

    // Accumulator for incremental global stats (built up across ticks)
    private GlobalEntityStats pendingGlobalStats = new GlobalEntityStats();

    // Player area scanning state
    private boolean playerAreasPending = false;

    private RoomEntityCounter() {}

    /**
     * Entity statistics for a single room.
     */
    public static class RoomEntityStats {
        public volatile int totalEntities = 0;
        public volatile int hostileMobs = 0;
        public volatile int passiveMobs = 0;
        public volatile int npcs = 0;
        public volatile int players = 0;
        public volatile int bosses = 0;
        public volatile int projectiles = 0;
        public volatile long lastUpdateMs = 0;

        public int getTotalLiving() {
            return hostileMobs + passiveMobs + npcs + players + bosses;
        }

        public void reset() {
            totalEntities = 0;
            hostileMobs = 0;
            passiveMobs = 0;
            npcs = 0;
            players = 0;
            bosses = 0;
            projectiles = 0;
            lastUpdateMs = System.currentTimeMillis();
        }

        @Override
        public String toString() {
            return String.format("Hostile=%d, Passive=%d, NPC=%d, Players=%d, Bosses=%d",
                hostileMobs, passiveMobs, npcs, players, bosses);
        }
    }

    /**
     * Global entity statistics across all loaded areas.
     */
    public static class GlobalEntityStats {
        public volatile int totalEntities = 0;
        public volatile int totalHostile = 0;
        public volatile int totalPassive = 0;
        public volatile int totalPlayers = 0;
        public volatile int totalNpcs = 0;
        public volatile int roomsWithEntities = 0;
        public volatile String busiestRoom = "";
        public volatile int busiestRoomCount = 0;

        public void reset() {
            totalEntities = 0;
            totalHostile = 0;
            totalPassive = 0;
            totalPlayers = 0;
            totalNpcs = 0;
            roomsWithEntities = 0;
            busiestRoom = "";
            busiestRoomCount = 0;
        }
    }

    /**
     * Called every server tick. Uses INCREMENTAL processing.
     * Instead of scanning all rooms in one tick, scans ONE room per tick.
     */
    public void tick(ServerLevel level) {
        // Refresh room cache periodically
        long now = System.currentTimeMillis();
        if (now - lastRoomCacheTime > ROOM_CACHE_DURATION_MS || cachedRooms.isEmpty()) {
            cachedRooms = new ArrayList<>(RoomService.INSTANCE.getRoomDefinitions());
            lastRoomCacheTime = now;
            // Reset cycle when rooms change
            currentRoomIndex = 0;
            pendingGlobalStats.reset();
            playerAreasPending = false;
        }

        // If no rooms configured, just do player area scan
        if (cachedRooms.isEmpty()) {
            scanPlayerAreas(level);
            return;
        }

        // Process ONE room per tick
        if (currentRoomIndex < cachedRooms.size()) {
            RoomDefinition room = cachedRooms.get(currentRoomIndex);
            scanSingleRoom(level, room);
            currentRoomIndex++;
        } else if (!playerAreasPending) {
            // After all rooms, scan player areas (one tick)
            scanPlayerAreas(level);
            playerAreasPending = true;
        } else {
            // Cycle complete - finalize and publish global stats
            globalStats = pendingGlobalStats;

            // Reset for next cycle
            pendingGlobalStats = new GlobalEntityStats();
            currentRoomIndex = 0;
            playerAreasPending = false;
        }
    }

    /**
     * Scan a single room and update its stats.
     * Called once per tick for incremental processing.
     */
    private void scanSingleRoom(ServerLevel level, RoomDefinition room) {
        RoomEntityStats stats = roomStats.computeIfAbsent(room.id(), k -> new RoomEntityStats());
        stats.reset();

        // Get bounding box for the room
        BlockPos min = room.min();
        BlockPos max = room.max();
        AABB roomBox = new AABB(
            Math.min(min.getX(), max.getX()),
            Math.min(min.getY(), max.getY()),
            Math.min(min.getZ(), max.getZ()),
            Math.max(min.getX(), max.getX()) + 1,
            Math.max(min.getY(), max.getY()) + 1,
            Math.max(min.getZ(), max.getZ()) + 1
        );
        java.util.Objects.requireNonNull(roomBox);

        // Query entities in the room
        List<Entity> entitiesInRoom = level.getEntities(null, roomBox);

        for (Entity entity : entitiesInRoom) {
            stats.totalEntities++;

            if (entity instanceof Player) {
                stats.players++;
                pendingGlobalStats.totalPlayers++;
            } else if (entity instanceof Monster) {
                stats.hostileMobs++;
                pendingGlobalStats.totalHostile++;
                // Check for bosses (high HP mobs)
                if (entity instanceof LivingEntity living) {
                    if (living.getMaxHealth() >= 100) {
                        stats.bosses++;
                    }
                }
            } else if (entity instanceof Animal) {
                stats.passiveMobs++;
                pendingGlobalStats.totalPassive++;
            } else if (entity instanceof Villager) {
                stats.npcs++;
                pendingGlobalStats.totalNpcs++;
            } else if (entity instanceof Mob) {
                // Other mobs (golems, etc.)
                stats.passiveMobs++;
                pendingGlobalStats.totalPassive++;
            }
        }

        // Update global accumulators
        pendingGlobalStats.totalEntities += stats.totalEntities;
        if (stats.totalEntities > 0) {
            pendingGlobalStats.roomsWithEntities++;
        }
        if (stats.totalEntities > pendingGlobalStats.busiestRoomCount) {
            pendingGlobalStats.busiestRoomCount = stats.totalEntities;
            pendingGlobalStats.busiestRoom = room.id();
        }
    }

    /**
     * Scan areas around players not in configured rooms.
     * Uses smaller scan radius (16 blocks) for performance.
     */
    private void scanPlayerAreas(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            String playerRoom = RoomService.INSTANCE.resolveRoom(level, player.blockPosition());

            // Skip if player is in a configured room (already scanned)
            if (roomStats.containsKey(playerRoom) && !playerRoom.equals("unknown")) {
                continue;
            }

            // PERFORMANCE FIX: Reduced scan radius from 32 to 16 blocks
            AABB scanBox = Objects.requireNonNull(Objects.requireNonNull(player.getBoundingBox()).inflate(16));
            List<Entity> nearbyEntities = level.getEntities(player, scanBox);

            RoomEntityStats fallbackStats = roomStats.computeIfAbsent(playerRoom, k -> new RoomEntityStats());
            fallbackStats.reset();

            for (Entity entity : nearbyEntities) {
                fallbackStats.totalEntities++;
                if (entity instanceof Monster) {
                    fallbackStats.hostileMobs++;
                } else if (entity instanceof Animal) {
                    fallbackStats.passiveMobs++;
                } else if (entity instanceof Villager) {
                    fallbackStats.npcs++;
                } else if (entity instanceof Player) {
                    fallbackStats.players++;
                }
            }
        }
    }

    /**
     * Gets entity stats for a specific room.
     */
    public RoomEntityStats getStatsForRoom(String roomId) {
        return roomStats.getOrDefault(roomId, new RoomEntityStats());
    }

    /**
     * Gets entity stats for the room containing a position.
     */
    public RoomEntityStats getStatsAtPosition(ServerLevel level, BlockPos pos) {
        String roomId = RoomService.INSTANCE.resolveRoom(level, pos);
        return getStatsForRoom(roomId);
    }

    /**
     * Gets global entity statistics.
     */
    public GlobalEntityStats getGlobalStats() {
        return globalStats;
    }

    /**
     * Gets all room stats.
     */
    public Map<String, RoomEntityStats> getAllRoomStats() {
        return Map.copyOf(roomStats);
    }

    /**
     * Clears all tracked stats. Call when world is unloaded.
     */
    public void clear() {
        roomStats.clear();
        globalStats = new GlobalEntityStats();
        pendingGlobalStats = new GlobalEntityStats();
        cachedRooms.clear();
        currentRoomIndex = 0;
        playerAreasPending = false;
    }

    /**
     * Gets a summary string for display in UIs.
     */
    public String getSummary() {
        GlobalEntityStats g = globalStats;
        return String.format(
            "Entities: %d total | Hostile: %d | Passive: %d | Players: %d | Rooms: %d",
            g.totalEntities, g.totalHostile, g.totalPassive, g.totalPlayers, g.roomsWithEntities
        );
    }

    /**
     * Gets the most dangerous room (most hostile mobs).
     */
    public String getMostDangerousRoom() {
        String worstRoom = "";
        int maxHostile = 0;

        for (Map.Entry<String, RoomEntityStats> entry : roomStats.entrySet()) {
            if (entry.getValue().hostileMobs > maxHostile) {
                maxHostile = entry.getValue().hostileMobs;
                worstRoom = entry.getKey();
            }
        }

        return worstRoom.isEmpty() ? "None" : worstRoom + " (" + maxHostile + " hostile)";
    }

    /**
     * Gets current processing progress (for debugging).
     */
    public String getProcessingStatus() {
        return String.format("Room %d/%d, PlayerAreas=%s",
            currentRoomIndex, cachedRooms.size(), playerAreasPending);
    }
}
