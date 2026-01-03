package com.devmod.telemetry.progression;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import com.devmod.telemetry.TelemetryJson;
import com.devmod.telemetry.room.RoomService;

public class PlayerProgressionService {
    public static final PlayerProgressionService INSTANCE = new PlayerProgressionService();

    // === Block Tracking ===
    private final Map<UUID, BlockStats> playerBlockStats = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Integer>> blocksBrokenPerRoom = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Integer>> blocksPlacedPerRoom = new ConcurrentHashMap<>();

    // === XP Tracking ===
    private final Map<UUID, XpStats> playerXpStats = new ConcurrentHashMap<>();

    // === Advancement Tracking ===
    private final Map<UUID, Integer> advancementsEarned = new ConcurrentHashMap<>();

    // === Dimension Tracking ===
    private final Map<UUID, DimensionStats> dimensionStats = new ConcurrentHashMap<>();

    // === Combat Tracking ===
    private final Map<UUID, CombatStats> combatStats = new ConcurrentHashMap<>();

    // === Trade Tracking ===
    private final Map<UUID, TradeStats> tradeStats = new ConcurrentHashMap<>();

    // === Session Stats ===
    private int totalBlocksBroken = 0;
    private int totalBlocksPlaced = 0;
    private int totalXpGained = 0;
    private int totalTrades = 0;
    private int totalFishCaught = 0;

    private PlayerProgressionService() {}

    // ===== BLOCK TRACKING =====

    /**
     * Record a block break event.
     */
    public BlockBreakRecord recordBlockBreak(ServerPlayer player, ServerLevel level, BlockPos pos, String blockId) {
        String roomId = RoomService.INSTANCE.resolveRoom(level, pos);

        // Per-player stats
        BlockStats stats = playerBlockStats.computeIfAbsent(player.getUUID(), k -> new BlockStats());
        stats.blocksBroken++;
        stats.brokenByType.merge(blockId, 1, (a, b) -> a + b);

        // Per-room stats
        blocksBrokenPerRoom.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
                .merge(blockId, 1, (a, b) -> a + b);

        totalBlocksBroken++;

        return new BlockBreakRecord(
                player.getGameProfile().getName(),
                roomId,
                blockId,
                pos,
                level.dimension().location().toString()
        );
    }

    /**
     * Record a block place event.
     */
    public BlockPlaceRecord recordBlockPlace(ServerPlayer player, ServerLevel level, BlockPos pos, String blockId) {
        String roomId = RoomService.INSTANCE.resolveRoom(level, pos);

        // Per-player stats
        BlockStats stats = playerBlockStats.computeIfAbsent(player.getUUID(), k -> new BlockStats());
        stats.blocksPlaced++;
        stats.placedByType.merge(blockId, 1, (a, b) -> a + b);

        // Per-room stats
        blocksPlacedPerRoom.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
                .merge(blockId, 1, (a, b) -> a + b);

        totalBlocksPlaced++;

        return new BlockPlaceRecord(
                player.getGameProfile().getName(),
                roomId,
                blockId,
                pos,
                level.dimension().location().toString()
        );
    }

    // ===== XP TRACKING =====

    /**
     * Record XP pickup event.
     */
    public XpPickupRecord recordXpPickup(ServerPlayer player, int amount) {
        XpStats stats = playerXpStats.computeIfAbsent(player.getUUID(), k -> new XpStats());
        stats.totalXpGained += amount;
        stats.xpPickups++;
        totalXpGained += amount;

        return new XpPickupRecord(
                player.getGameProfile().getName(),
                amount,
                player.experienceLevel,
                player.totalExperience
        );
    }

    /**
     * Record level change event.
     */
    public LevelChangeRecord recordLevelChange(ServerPlayer player, int oldLevel, int newLevel) {
        XpStats stats = playerXpStats.computeIfAbsent(player.getUUID(), k -> new XpStats());
        if (newLevel > stats.maxLevelReached) {
            stats.maxLevelReached = newLevel;
        }

        return new LevelChangeRecord(
                player.getGameProfile().getName(),
                oldLevel,
                newLevel,
                player.totalExperience
        );
    }

    // ===== ADVANCEMENT TRACKING =====

    /**
     * Record advancement earned.
     */
    public AdvancementRecord recordAdvancement(ServerPlayer player, ResourceLocation advancementId, String title) {
        int totalAdvancements = advancementsEarned.merge(player.getUUID(), 1, Integer::sum);

        return new AdvancementRecord(
                player.getGameProfile().getName(),
                advancementId.toString(),
                title,
                totalAdvancements
        );
    }

    // ===== DIMENSION TRACKING =====

    /**
     * Record dimension change.
     */
    public DimensionChangeRecord recordDimensionChange(ServerPlayer player, String fromDimension, String toDimension) {
        DimensionStats stats = dimensionStats.computeIfAbsent(player.getUUID(), k -> new DimensionStats());
        stats.dimensionChanges++;
        stats.visitedDimensions.add(toDimension);

        return new DimensionChangeRecord(
                player.getGameProfile().getName(),
                fromDimension,
                toDimension,
                stats.visitedDimensions.size()
        );
    }

    // ===== COMBAT TRACKING =====

    /**
     * Record critical hit.
     */
    public CriticalHitRecord recordCriticalHit(ServerPlayer player, String targetName, String targetType,
                                                float damage, float critMultiplier) {
        CombatStats stats = combatStats.computeIfAbsent(player.getUUID(), k -> new CombatStats());
        stats.criticalHits++;
        stats.totalCritDamage += damage;

        return new CriticalHitRecord(
                player.getGameProfile().getName(),
                targetName,
                targetType,
                damage,
                critMultiplier,
                stats.criticalHits
        );
    }

    /**
     * Record attack event.
     */
    public AttackRecord recordAttack(ServerPlayer player, String targetName, String targetType,
                                      String weaponId, boolean isSweep) {
        CombatStats stats = combatStats.computeIfAbsent(player.getUUID(), k -> new CombatStats());
        stats.totalAttacks++;
        if (isSweep) stats.sweepAttacks++;

        return new AttackRecord(
                player.getGameProfile().getName(),
                targetName,
                targetType,
                weaponId,
                isSweep,
                stats.totalAttacks
        );
    }

    // ===== TRADE TRACKING =====

    /**
     * Record villager trade.
     */
    public TradeRecord recordTrade(ServerPlayer player, String villagerType, String profession,
                                    String itemBought, int buyCount, String itemSold, int sellCount) {
        TradeStats stats = tradeStats.computeIfAbsent(player.getUUID(), k -> new TradeStats());
        stats.totalTrades++;
        stats.tradesByProfession.merge(profession, 1, (a, b) -> a + b);
        totalTrades++;

        return new TradeRecord(
                player.getGameProfile().getName(),
                villagerType,
                profession,
                itemBought,
                buyCount,
                itemSold,
                sellCount,
                stats.totalTrades
        );
    }

    // ===== FISHING TRACKING =====

    /**
     * Record fishing event.
     */
    public FishingRecord recordFishing(ServerPlayer player, String itemCaught, int count, BlockPos pos) {
        totalFishCaught++;

        String roomId = RoomService.INSTANCE.resolveRoom((ServerLevel) player.level(), pos);

        return new FishingRecord(
                player.getGameProfile().getName(),
                roomId,
                itemCaught,
                count,
                pos
        );
    }

    // ===== STATISTICS =====

    public int getTotalBlocksBroken() { return totalBlocksBroken; }
    public int getTotalBlocksPlaced() { return totalBlocksPlaced; }
    public int getTotalXpGained() { return totalXpGained; }
    public int getTotalTrades() { return totalTrades; }
    public int getTotalFishCaught() { return totalFishCaught; }

    public @Nullable BlockStats getPlayerBlockStats(UUID playerId) {
        return playerBlockStats.get(playerId);
    }

    public @Nullable XpStats getPlayerXpStats(UUID playerId) {
        return playerXpStats.get(playerId);
    }

    public @Nullable CombatStats getPlayerCombatStats(UUID playerId) {
        return combatStats.get(playerId);
    }

    // ===== CLEANUP =====

    public void clear() {
        playerBlockStats.clear();
        blocksBrokenPerRoom.clear();
        blocksPlacedPerRoom.clear();
        playerXpStats.clear();
        advancementsEarned.clear();
        dimensionStats.clear();
        combatStats.clear();
        tradeStats.clear();
        totalBlocksBroken = 0;
        totalBlocksPlaced = 0;
        totalXpGained = 0;
        totalTrades = 0;
        totalFishCaught = 0;
    }

    public void cleanupPlayer(UUID playerId) {
        playerBlockStats.remove(playerId);
        playerXpStats.remove(playerId);
        advancementsEarned.remove(playerId);
        dimensionStats.remove(playerId);
        combatStats.remove(playerId);
        tradeStats.remove(playerId);
    }

    // ===== DATA CLASSES =====

    public static class BlockStats {
        public int blocksBroken = 0;
        public int blocksPlaced = 0;
        public Map<String, Integer> brokenByType = new ConcurrentHashMap<>();
        public Map<String, Integer> placedByType = new ConcurrentHashMap<>();
    }

    public static class XpStats {
        public int totalXpGained = 0;
        public int xpPickups = 0;
        public int maxLevelReached = 0;
    }

    public static class DimensionStats {
        public int dimensionChanges = 0;
        public java.util.Set<String> visitedDimensions = ConcurrentHashMap.newKeySet();
    }

    public static class CombatStats {
        public int totalAttacks = 0;
        public int criticalHits = 0;
        public int sweepAttacks = 0;
        public float totalCritDamage = 0;
    }

    public static class TradeStats {
        public int totalTrades = 0;
        public Map<String, Integer> tradesByProfession = new ConcurrentHashMap<>();
    }

    // ===== RECORDS FOR TELEMETRY =====

    public record BlockBreakRecord(String player, String roomId, String blockId, BlockPos pos, String world) {
        public String toJson() {
            return "{\"ts\":\"" + Instant.now() + "\","
                    + "\"type\":\"block_break\","
                    + "\"player\":\"" + TelemetryJson.escape(player) + "\","
                    + "\"room\":\"" + TelemetryJson.escape(roomId) + "\","
                    + "\"block\":\"" + TelemetryJson.escape(blockId) + "\","
                    + "\"pos\":[" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "],"
                    + "\"world\":\"" + TelemetryJson.escape(world) + "\"}";
        }
    }

    public record BlockPlaceRecord(String player, String roomId, String blockId, BlockPos pos, String world) {
        public String toJson() {
            return "{\"ts\":\"" + Instant.now() + "\","
                    + "\"type\":\"block_place\","
                    + "\"player\":\"" + TelemetryJson.escape(player) + "\","
                    + "\"room\":\"" + TelemetryJson.escape(roomId) + "\","
                    + "\"block\":\"" + TelemetryJson.escape(blockId) + "\","
                    + "\"pos\":[" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "],"
                    + "\"world\":\"" + TelemetryJson.escape(world) + "\"}";
        }
    }

    public record XpPickupRecord(String player, int amount, int currentLevel, int totalXp) {
        public String toJson() {
            return "{\"ts\":\"" + Instant.now() + "\","
                    + "\"type\":\"xp_pickup\","
                    + "\"player\":\"" + TelemetryJson.escape(player) + "\","
                    + "\"amount\":" + amount + ","
                    + "\"level\":" + currentLevel + ","
                    + "\"totalXp\":" + totalXp + "}";
        }
    }

    public record LevelChangeRecord(String player, int oldLevel, int newLevel, int totalXp) {
        public String toJson() {
            return "{\"ts\":\"" + Instant.now() + "\","
                    + "\"type\":\"level_change\","
                    + "\"player\":\"" + TelemetryJson.escape(player) + "\","
                    + "\"oldLevel\":" + oldLevel + ","
                    + "\"newLevel\":" + newLevel + ","
                    + "\"totalXp\":" + totalXp + "}";
        }
    }

    public record AdvancementRecord(String player, String advancementId, String title, int totalAdvancements) {
        public String toJson() {
            return "{\"ts\":\"" + Instant.now() + "\","
                    + "\"type\":\"advancement\","
                    + "\"player\":\"" + TelemetryJson.escape(player) + "\","
                    + "\"advancement\":\"" + TelemetryJson.escape(advancementId) + "\","
                    + "\"title\":\"" + TelemetryJson.escape(title) + "\","
                    + "\"totalAdvancements\":" + totalAdvancements + "}";
        }
    }

    public record DimensionChangeRecord(String player, String fromDimension, String toDimension, int uniqueDimensions) {
        public String toJson() {
            return "{\"ts\":\"" + Instant.now() + "\","
                    + "\"type\":\"dimension_change\","
                    + "\"player\":\"" + TelemetryJson.escape(player) + "\","
                    + "\"from\":\"" + TelemetryJson.escape(fromDimension) + "\","
                    + "\"to\":\"" + TelemetryJson.escape(toDimension) + "\","
                    + "\"uniqueDimensions\":" + uniqueDimensions + "}";
        }
    }

    public record CriticalHitRecord(String player, String target, String targetType,
                                     float damage, float multiplier, int totalCrits) {
        public String toJson() {
            return "{\"ts\":\"" + Instant.now() + "\","
                    + "\"type\":\"critical_hit\","
                    + "\"player\":\"" + TelemetryJson.escape(player) + "\","
                    + "\"target\":\"" + TelemetryJson.escape(target) + "\","
                    + "\"targetType\":\"" + TelemetryJson.escape(targetType) + "\","
                    + "\"damage\":" + damage + ","
                    + "\"multiplier\":" + multiplier + ","
                    + "\"totalCrits\":" + totalCrits + "}";
        }
    }

    public record AttackRecord(String player, String target, String targetType,
                                String weapon, boolean isSweep, int totalAttacks) {
        public String toJson() {
            return "{\"ts\":\"" + Instant.now() + "\","
                    + "\"type\":\"attack\","
                    + "\"player\":\"" + TelemetryJson.escape(player) + "\","
                    + "\"target\":\"" + TelemetryJson.escape(target) + "\","
                    + "\"targetType\":\"" + TelemetryJson.escape(targetType) + "\","
                    + "\"weapon\":\"" + TelemetryJson.escape(weapon) + "\","
                    + "\"sweep\":" + isSweep + ","
                    + "\"totalAttacks\":" + totalAttacks + "}";
        }
    }

    public record TradeRecord(String player, String villagerType, String profession,
                               String itemBought, int buyCount, String itemSold, int sellCount, int totalTrades) {
        public String toJson() {
            return "{\"ts\":\"" + Instant.now() + "\","
                    + "\"type\":\"trade\","
                    + "\"player\":\"" + TelemetryJson.escape(player) + "\","
                    + "\"villager\":\"" + TelemetryJson.escape(villagerType) + "\","
                    + "\"profession\":\"" + TelemetryJson.escape(profession) + "\","
                    + "\"bought\":\"" + TelemetryJson.escape(itemBought) + "\","
                    + "\"buyCount\":" + buyCount + ","
                    + "\"sold\":\"" + TelemetryJson.escape(itemSold) + "\","
                    + "\"sellCount\":" + sellCount + ","
                    + "\"totalTrades\":" + totalTrades + "}";
        }
    }

    public record FishingRecord(String player, String roomId, String itemCaught, int count, BlockPos pos) {
        public String toJson() {
            return "{\"ts\":\"" + Instant.now() + "\","
                    + "\"type\":\"fishing\","
                    + "\"player\":\"" + TelemetryJson.escape(player) + "\","
                    + "\"room\":\"" + TelemetryJson.escape(roomId) + "\","
                    + "\"item\":\"" + TelemetryJson.escape(itemCaught) + "\","
                    + "\"count\":" + count + ","
                    + "\"pos\":[" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "]}";
        }
    }
}
