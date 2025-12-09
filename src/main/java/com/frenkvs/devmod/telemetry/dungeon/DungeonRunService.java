package com.frenkvs.devmod.telemetry.dungeon;

import com.frenkvs.devmod.telemetry.TelemetryService;
import com.frenkvs.devmod.telemetry.room.RoomService;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VOXEL-LAB M41: Dungeon Run Outcomes
 *
 * Tracks complete dungeon runs from entry to exit/death.
 * Collects metrics for level design analysis:
 * - Run duration
 * - Deaths during run
 * - Rooms visited
 * - Loot collected
 * - Success/failure outcome
 * - Combat stats (kills, damage taken/dealt)
 *
 * A "dungeon" is defined by configured room groups in telemetry_rooms.json.
 */
public class DungeonRunService {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final DungeonRunService INSTANCE = new DungeonRunService();

    // Active runs per player
    private final Map<UUID, DungeonRun> activeRuns = new ConcurrentHashMap<>();

    // Completed runs for analysis
    private final List<DungeonRunResult> completedRuns = Collections.synchronizedList(new ArrayList<>());

    // Maximum completed runs to keep in memory
    private static final int MAX_COMPLETED_RUNS = 100;

    // Timeout for abandoned runs (30 minutes)
    private static final long RUN_TIMEOUT_MS = 30 * 60 * 1000;

    private DungeonRunService() {}

    /**
     * Called when a player enters a dungeon room.
     * Starts a new run if not already in one.
     */
    public void onEnterDungeon(ServerPlayer player, String dungeonId, String roomId) {
        UUID playerId = player.getUUID();

        DungeonRun existingRun = activeRuns.get(playerId);
        if (existingRun != null && existingRun.dungeonId.equals(dungeonId)) {
            // Already in this dungeon, just update room
            existingRun.visitRoom(roomId);
            return;
        }

        // End any previous run
        if (existingRun != null) {
            endRun(playerId, RunOutcome.ABANDONED, "entered different dungeon");
        }

        // Start new run
        DungeonRun run = new DungeonRun(playerId, dungeonId, player.getName().getString());
        run.visitRoom(roomId);
        activeRuns.put(playerId, run);

        LOGGER.debug("[DungeonRun] {} started run in dungeon '{}'", player.getName().getString(), dungeonId);
    }

    /**
     * Called when a player leaves dungeon area.
     */
    public void onExitDungeon(ServerPlayer player, String dungeonId, boolean completed) {
        UUID playerId = player.getUUID();
        DungeonRun run = activeRuns.get(playerId);

        if (run != null && run.dungeonId.equals(dungeonId)) {
            if (completed) {
                endRun(playerId, RunOutcome.SUCCESS, "completed dungeon");
            } else {
                endRun(playerId, RunOutcome.ABANDONED, "left dungeon area");
            }
        }
    }

    /**
     * Called when a player dies in a dungeon.
     */
    public void onPlayerDeath(ServerPlayer player) {
        UUID playerId = player.getUUID();
        DungeonRun run = activeRuns.get(playerId);

        if (run != null) {
            run.deaths++;
            run.lastDeathRoom = run.currentRoom;
            run.lastDeathTime = System.currentTimeMillis();

            // Death ends the run as failure
            endRun(playerId, RunOutcome.DEATH, "died in dungeon");
        }
    }

    /**
     * Called when a player picks up loot in a dungeon.
     */
    public void onLootPickup(ServerPlayer player, ItemStack item) {
        UUID playerId = player.getUUID();
        DungeonRun run = activeRuns.get(playerId);

        if (run != null) {
            String itemId = item.getItem().toString();
            run.lootCollected.merge(itemId, item.getCount(), Integer::sum);
            run.totalLootValue += estimateLootValue(item);
        }
    }

    /**
     * Called when a player kills an enemy in a dungeon.
     */
    public void onEnemyKill(ServerPlayer player, String enemyType) {
        UUID playerId = player.getUUID();
        DungeonRun run = activeRuns.get(playerId);

        if (run != null) {
            run.kills++;
            run.enemiesKilled.merge(enemyType, 1, Integer::sum);
        }
    }

    /**
     * Called when player takes damage in a dungeon.
     */
    public void onDamageTaken(ServerPlayer player, float amount) {
        UUID playerId = player.getUUID();
        DungeonRun run = activeRuns.get(playerId);

        if (run != null) {
            run.damageTaken += amount;
        }
    }

    /**
     * Called when player deals damage in a dungeon.
     */
    public void onDamageDealt(ServerPlayer player, float amount) {
        UUID playerId = player.getUUID();
        DungeonRun run = activeRuns.get(playerId);

        if (run != null) {
            run.damageDealt += amount;
        }
    }

    /**
     * Ends a dungeon run and records the result.
     */
    private void endRun(UUID playerId, RunOutcome outcome, String reason) {
        DungeonRun run = activeRuns.remove(playerId);
        if (run == null) return;

        run.endTime = System.currentTimeMillis();
        run.outcome = outcome;

        DungeonRunResult result = new DungeonRunResult(run);
        completedRuns.add(result);

        // Trim old runs
        while (completedRuns.size() > MAX_COMPLETED_RUNS) {
            completedRuns.remove(0);
        }

        // Log to telemetry
        logRunResult(result);

        LOGGER.info("[DungeonRun] {} {} dungeon '{}' after {}s ({} deaths, {} kills)",
            run.playerName, outcome.name().toLowerCase(), run.dungeonId,
            result.durationSeconds, run.deaths, run.kills);
    }

    /**
     * Logs run result to telemetry files.
     */
    private void logRunResult(DungeonRunResult result) {
        StringBuilder json = new StringBuilder();
        json.append("{\"ts\":\"").append(Instant.now()).append("\",");
        json.append("\"player\":\"").append(result.playerName).append("\",");
        json.append("\"dungeon\":\"").append(result.dungeonId).append("\",");
        json.append("\"outcome\":\"").append(result.outcome).append("\",");
        json.append("\"duration_sec\":").append(result.durationSeconds).append(",");
        json.append("\"deaths\":").append(result.deaths).append(",");
        json.append("\"kills\":").append(result.kills).append(",");
        json.append("\"rooms_visited\":").append(result.roomsVisited).append(",");
        json.append("\"damage_dealt\":").append(String.format("%.1f", result.damageDealt)).append(",");
        json.append("\"damage_taken\":").append(String.format("%.1f", result.damageTaken)).append(",");
        json.append("\"loot_value\":").append(result.totalLootValue).append(",");
        json.append("\"loot_items\":").append(result.lootItems);
        json.append("}");

        TelemetryService.INSTANCE.appendDungeonRun(json.toString());
    }

    /**
     * Checks for player room transitions and handles dungeon tracking.
     * Called from server tick.
     */
    public void trackPlayer(ServerPlayer player, ServerLevel level) {
        String currentRoom = RoomService.INSTANCE.resolveRoom(level, player.blockPosition());
        UUID playerId = player.getUUID();

        // Get dungeon ID for this room (if any)
        String dungeonId = getDungeonForRoom(currentRoom);

        DungeonRun activeRun = activeRuns.get(playerId);

        if (dungeonId != null) {
            // Player is in a dungeon room
            if (activeRun == null || !activeRun.dungeonId.equals(dungeonId)) {
                onEnterDungeon(player, dungeonId, currentRoom);
            } else {
                activeRun.visitRoom(currentRoom);
            }
        } else {
            // Player is outside dungeon
            if (activeRun != null) {
                onExitDungeon(player, activeRun.dungeonId, false);
            }
        }

        // Check for timed out runs
        checkTimeouts();
    }

    /**
     * Gets the dungeon ID for a room, if it's part of a dungeon.
     * Room names starting with "dungeon_" or containing "_dungeon_" are considered dungeon rooms.
     * The dungeon ID is extracted from the room name pattern.
     */
    private String getDungeonForRoom(String roomId) {
        if (roomId == null || roomId.equals("unknown")) return null;

        // Pattern: "dungeon_name_roomX" or "name_dungeon_roomX"
        if (roomId.startsWith("dungeon_")) {
            // Extract dungeon name: dungeon_castle_room1 -> dungeon_castle
            int lastUnderscore = roomId.lastIndexOf('_');
            if (lastUnderscore > 8) {
                return roomId.substring(0, lastUnderscore);
            }
            return roomId;
        }

        if (roomId.contains("_dungeon")) {
            int idx = roomId.indexOf("_dungeon");
            return roomId.substring(0, idx + 8); // Include "_dungeon"
        }

        return null;
    }

    /**
     * Cleans up timed out runs.
     */
    private void checkTimeouts() {
        long now = System.currentTimeMillis();
        List<UUID> timedOut = new ArrayList<>();

        for (Map.Entry<UUID, DungeonRun> entry : activeRuns.entrySet()) {
            if (now - entry.getValue().startTime > RUN_TIMEOUT_MS) {
                timedOut.add(entry.getKey());
            }
        }

        for (UUID playerId : timedOut) {
            endRun(playerId, RunOutcome.TIMEOUT, "run timed out");
        }
    }

    /**
     * Estimates loot value for statistics.
     */
    private int estimateLootValue(ItemStack item) {
        // Simple heuristic based on rarity and stack size
        int baseValue = switch (item.getRarity()) {
            case COMMON -> 1;
            case UNCOMMON -> 5;
            case RARE -> 25;
            case EPIC -> 100;
        };
        return baseValue * item.getCount();
    }

    /**
     * Gets summaries of recent dungeon runs.
     */
    public List<String> getRunSummaries() {
        List<String> summaries = new ArrayList<>();
        int count = 0;
        for (int i = completedRuns.size() - 1; i >= 0 && count < 10; i--, count++) {
            DungeonRunResult r = completedRuns.get(i);
            summaries.add(String.format("%s: %s in %ds (%s) - %d deaths, %d kills",
                r.playerName, r.dungeonId, r.durationSeconds, r.outcome, r.deaths, r.kills));
        }
        return summaries;
    }

    /**
     * Gets statistics for a specific dungeon.
     */
    public DungeonStats getDungeonStats(String dungeonId) {
        int totalRuns = 0;
        int successes = 0;
        int deaths = 0;
        long totalDuration = 0;
        int totalKills = 0;

        for (DungeonRunResult r : completedRuns) {
            if (r.dungeonId.equals(dungeonId)) {
                totalRuns++;
                if (r.outcome == RunOutcome.SUCCESS) successes++;
                if (r.outcome == RunOutcome.DEATH) deaths++;
                totalDuration += r.durationSeconds;
                totalKills += r.kills;
            }
        }

        return new DungeonStats(
            dungeonId,
            totalRuns,
            successes,
            deaths,
            totalRuns > 0 ? (int)(totalDuration / totalRuns) : 0,
            totalRuns > 0 ? (double)successes / totalRuns * 100 : 0,
            totalKills
        );
    }

    /**
     * Gets active runs count.
     */
    public int getActiveRunsCount() {
        return activeRuns.size();
    }

    /**
     * Clears all data.
     */
    public void clear() {
        activeRuns.clear();
        completedRuns.clear();
    }

    // ==================== Inner Classes ====================

    public enum RunOutcome {
        SUCCESS,    // Completed dungeon
        DEATH,      // Died in dungeon
        ABANDONED,  // Left dungeon without completing
        TIMEOUT     // Run timed out
    }

    /**
     * Active dungeon run tracking.
     */
    private static class DungeonRun {
        final UUID playerId;
        final String dungeonId;
        final String playerName;
        final long startTime;
        final Set<String> roomsVisited = new HashSet<>();
        final Map<String, Integer> lootCollected = new HashMap<>();
        final Map<String, Integer> enemiesKilled = new HashMap<>();

        String currentRoom = "";
        long endTime;
        RunOutcome outcome;
        int deaths = 0;
        int kills = 0;
        float damageTaken = 0;
        float damageDealt = 0;
        int totalLootValue = 0;
        String lastDeathRoom = "";
        long lastDeathTime = 0;

        DungeonRun(UUID playerId, String dungeonId, String playerName) {
            this.playerId = playerId;
            this.dungeonId = dungeonId;
            this.playerName = playerName;
            this.startTime = System.currentTimeMillis();
        }

        void visitRoom(String roomId) {
            this.currentRoom = roomId;
            this.roomsVisited.add(roomId);
        }
    }

    /**
     * Completed run result for analysis.
     */
    public static class DungeonRunResult {
        public final String playerName;
        public final String dungeonId;
        public final RunOutcome outcome;
        public final long durationSeconds;
        public final int roomsVisited;
        public final int deaths;
        public final int kills;
        public final float damageTaken;
        public final float damageDealt;
        public final int totalLootValue;
        public final int lootItems;

        DungeonRunResult(DungeonRun run) {
            this.playerName = run.playerName;
            this.dungeonId = run.dungeonId;
            this.outcome = run.outcome;
            this.durationSeconds = (run.endTime - run.startTime) / 1000;
            this.roomsVisited = run.roomsVisited.size();
            this.deaths = run.deaths;
            this.kills = run.kills;
            this.damageTaken = run.damageTaken;
            this.damageDealt = run.damageDealt;
            this.totalLootValue = run.totalLootValue;
            this.lootItems = run.lootCollected.values().stream().mapToInt(i -> i).sum();
        }
    }

    /**
     * Aggregated statistics for a dungeon.
     */
    public record DungeonStats(
        String dungeonId,
        int totalRuns,
        int successes,
        int deaths,
        int avgDurationSeconds,
        double successRate,
        int totalKills
    ) {
        @Override
        public String toString() {
            return String.format("Dungeon '%s': %d runs, %.1f%% success, avg %ds, %d total kills",
                dungeonId, totalRuns, successRate, avgDurationSeconds, totalKills);
        }
    }
}
