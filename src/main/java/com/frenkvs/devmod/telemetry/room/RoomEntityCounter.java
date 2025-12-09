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

import java.util.List;
import java.util.Map;
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
 * Updates periodically (not every tick) for performance.
 */
public class RoomEntityCounter {
    public static final RoomEntityCounter INSTANCE = new RoomEntityCounter();

    // Entity counts per room
    private final Map<String, RoomEntityStats> roomStats = new ConcurrentHashMap<>();

    // Update interval (ticks)
    private static final int UPDATE_INTERVAL_TICKS = 20; // Update every second
    private int tickCounter = 0;

    // Global stats (all entities in current dimension)
    private volatile GlobalEntityStats globalStats = new GlobalEntityStats();

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
    }

    /**
     * Called every server tick. Performs entity counting periodically.
     */
    public void tick(ServerLevel level) {
        tickCounter++;
        if (tickCounter < UPDATE_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;

        updateEntityCounts(level);
    }

    /**
     * Updates entity counts for all rooms and global stats.
     */
    private void updateEntityCounts(ServerLevel level) {
        // Reset global stats
        GlobalEntityStats newGlobal = new GlobalEntityStats();

        // Get all rooms
        List<RoomDefinition> rooms = RoomService.INSTANCE.getRoomDefinitions();

        // Track per-room stats
        for (RoomDefinition room : rooms) {
            RoomEntityStats stats = roomStats.computeIfAbsent(room.id(), k -> new RoomEntityStats());
            stats.reset();

            // Get bounding box for the room from min/max coordinates
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

            // Query entities in the room
            List<Entity> entitiesInRoom = level.getEntities(null, roomBox);

            for (Entity entity : entitiesInRoom) {
                stats.totalEntities++;

                if (entity instanceof Player) {
                    stats.players++;
                    newGlobal.totalPlayers++;
                } else if (entity instanceof Monster) {
                    stats.hostileMobs++;
                    newGlobal.totalHostile++;
                    // Check for bosses (high HP mobs)
                    if (entity instanceof LivingEntity living) {
                        if (living.getMaxHealth() >= 100) {
                            stats.bosses++;
                        }
                    }
                } else if (entity instanceof Animal) {
                    stats.passiveMobs++;
                    newGlobal.totalPassive++;
                } else if (entity instanceof Villager) {
                    stats.npcs++;
                    newGlobal.totalNpcs++;
                } else if (entity instanceof Mob) {
                    // Other mobs (golems, etc.)
                    stats.passiveMobs++;
                    newGlobal.totalPassive++;
                }
            }

            newGlobal.totalEntities += stats.totalEntities;
            if (stats.totalEntities > 0) {
                newGlobal.roomsWithEntities++;
            }
            if (stats.totalEntities > newGlobal.busiestRoomCount) {
                newGlobal.busiestRoomCount = stats.totalEntities;
                newGlobal.busiestRoom = room.id();
            }
        }

        // Also count entities not in any room (fallback to chunk-based counting)
        // This is a lightweight count of all entities in loaded chunks around players
        for (ServerPlayer player : level.players()) {
            String playerRoom = RoomService.INSTANCE.resolveRoom(level, player.blockPosition());
            if (!roomStats.containsKey(playerRoom)) {
                // Count entities around player in non-room area
                AABB scanBox = player.getBoundingBox().inflate(32);
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

        globalStats = newGlobal;
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
        tickCounter = 0;
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
}
