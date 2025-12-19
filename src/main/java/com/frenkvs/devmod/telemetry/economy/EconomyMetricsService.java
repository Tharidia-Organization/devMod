package com.frenkvs.devmod.telemetry.economy;

import com.frenkvs.devmod.telemetry.TelemetryJson;
import com.frenkvs.devmod.telemetry.room.RoomService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Service for tracking economy and loot metrics (M42-M50).
 *
 * Features:
 * - M42: Loot distribution fairness (chest openings, drops)
 * - M44: Item acquisition tracking
 * - M45: Quest completion rewards
 * - M46: Reward relevance (used vs sold vs discarded)
 * - M50: Resource scarcity pressure
 *
 * Thread-safe for concurrent access from multiple server threads.
 */
public class EconomyMetricsService {
    public static final EconomyMetricsService INSTANCE = new EconomyMetricsService();

    // === M42: Loot Distribution ===
    // Tracks chest openings per room
    private final Map<String, Map<BlockPos, ChestStats>> chestOpenings = new ConcurrentHashMap<>();

    // Tracks item drops per room (from mobs)
    private final Map<String, Map<String, Integer>> itemDropsPerRoom = new ConcurrentHashMap<>();

    // Tracks item pickups per player
    private final Map<UUID, Map<String, Integer>> itemPickupsPerPlayer = new ConcurrentHashMap<>();

    // === M44: Item Acquisition ===
    // Total items acquired by type (global)
    private final Map<String, ItemAcquisitionStats> itemAcquisitionStats = new ConcurrentHashMap<>();

    // === M46: Reward Relevance ===
    // Tracks what happens to items after pickup (equipped, stored, sold, discarded)
    private final Map<UUID, Map<String, ItemLifecycle>> itemLifecycles = new ConcurrentHashMap<>();

    // === M50: Resource Scarcity ===
    // Tracks consumption vs acquisition rates
    private final Map<String, ResourceFlowStats> resourceFlows = new ConcurrentHashMap<>();

    // === MOB KILL & LOOT TRACKING ===
    // Tracks kills and drops per mob type for drop percentage calculation
    private final Map<String, MobLootStats> mobLootStats = new ConcurrentHashMap<>();

    // === Session Stats ===
    private long sessionStartTime = System.currentTimeMillis();
    private int totalChestsOpened = 0;
    private int totalItemsPickedUp = 0;
    private int totalItemsDropped = 0;
    private int totalItemsUsed = 0;
    private int totalMobsKilled = 0;

    private EconomyMetricsService() {}

    // ===== M42: Loot Distribution =====

    /**
     * Record a chest opening event.
     *
     * @param player Player who opened the chest
     * @param level Server level
     * @param chestPos Position of the chest
     * @param contents Items found in chest
     * @return ChestOpenRecord for telemetry logging
     */
    public ChestOpenRecord recordChestOpen(ServerPlayer player, ServerLevel level, BlockPos chestPos, List<ItemStack> contents) {
        String roomId = RoomService.INSTANCE.resolveRoom(level, chestPos);

        ChestStats stats = chestOpenings.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(chestPos, k -> new ChestStats());

        stats.openCount++;
        stats.lastOpenedBy = player.getUUID();
        stats.lastOpenedTime = System.currentTimeMillis();

        // Track items found
        for (ItemStack stack : contents) {
            if (!stack.isEmpty()) {
                String itemId = stack.getItem().toString();
                stats.itemsFound.merge(itemId, stack.getCount(), (a, b) -> a + b);

                // Also track in room drops
                itemDropsPerRoom.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
                        .merge(itemId, stack.getCount(), (a, b) -> a + b);
            }
        }

        totalChestsOpened++;

        return new ChestOpenRecord(
                player.getGameProfile().getName(),
                roomId,
                chestPos,
                contents.stream().filter(s -> !s.isEmpty()).count(),
                stats.openCount
        );
    }

    /**
     * Record an item pickup event.
     *
     * @param player Player who picked up the item
     * @param item Item stack picked up
     * @param pos Position where item was picked up
     * @return ItemPickupRecord for telemetry logging
     */
    public ItemPickupRecord recordItemPickup(ServerPlayer player, ItemStack item, BlockPos pos) {
        if (item.isEmpty()) return null;

        String itemId = item.getItem().toString();
        int count = item.getCount();

        // Track per player
        itemPickupsPerPlayer.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>())
                .merge(itemId, count, (a, b) -> a + b);

        // Track global acquisition stats
        ItemAcquisitionStats stats = itemAcquisitionStats.computeIfAbsent(itemId, k -> new ItemAcquisitionStats());
        stats.totalAcquired += count;
        stats.lastAcquiredTime = System.currentTimeMillis();

        // Initialize lifecycle tracking
        ItemLifecycle lifecycle = itemLifecycles.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>())
                .computeIfAbsent(itemId, k -> new ItemLifecycle());
        lifecycle.acquired += count;

        // Update resource flow
        ResourceFlowStats flow = resourceFlows.computeIfAbsent(itemId, k -> new ResourceFlowStats());
        flow.inflow += count;

        totalItemsPickedUp += count;

        String roomId = RoomService.INSTANCE.resolveRoom((ServerLevel) player.level(), pos);

        return new ItemPickupRecord(
                player.getGameProfile().getName(),
                roomId,
                itemId,
                count,
                pos
        );
    }

    /**
     * Record an item drop from a mob death.
     *
     * @param level Server level
     * @param mobType Type of mob that dropped the item
     * @param item Item dropped
     * @param pos Position of drop
     * @return MobDropRecord for telemetry logging
     */
    public MobDropRecord recordMobDrop(ServerLevel level, String mobType, ItemStack item, BlockPos pos) {
        if (item.isEmpty()) return null;

        String itemId = item.getItem().toString();
        int count = item.getCount();

        String roomId = RoomService.INSTANCE.resolveRoom(level, pos);

        // Track drops per room
        itemDropsPerRoom.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
                .merge(itemId, count, (a, b) -> a + b);

        // Update acquisition stats
        ItemAcquisitionStats stats = itemAcquisitionStats.computeIfAbsent(itemId, k -> new ItemAcquisitionStats());
        stats.totalDropped += count;
        stats.dropSources.merge(mobType, count, (a, b) -> a + b);

        totalItemsDropped += count;

        return new MobDropRecord(
                mobType,
                roomId,
                itemId,
                count,
                pos
        );
    }

    /**
     * Record a mob kill event. Should be called BEFORE recordMobDrop for accurate tracking.
     *
     * @param mobType Type of mob killed (e.g., "minecraft:zombie")
     * @param hasLoot Whether this kill will drop any loot
     * @return MobKillRecord for telemetry logging
     */
    public MobKillRecord recordMobKill(String mobType, boolean hasLoot) {
        MobLootStats stats = mobLootStats.computeIfAbsent(mobType, k -> new MobLootStats());
        stats.killCount++;
        stats.lastKillTime = System.currentTimeMillis();
        if (hasLoot) {
            stats.killsWithLoot++;
        }
        totalMobsKilled++;

        return new MobKillRecord(mobType, stats.killCount, hasLoot);
    }

    /**
     * Record item drops from a specific mob kill (called after recordMobKill).
     * Updates the per-item drop statistics for calculating drop rates.
     *
     * @param mobType Type of mob that dropped the items
     * @param items List of items dropped
     */
    public void recordMobDropItems(String mobType, List<ItemStack> items) {
        MobLootStats stats = mobLootStats.computeIfAbsent(mobType, k -> new MobLootStats());

        for (ItemStack item : items) {
            if (item.isEmpty()) continue;

            String itemId = item.getItem().toString();
            int count = item.getCount();

            ItemDropStats dropStats = stats.itemDrops.computeIfAbsent(itemId, k -> new ItemDropStats());
            dropStats.dropCount++;
            dropStats.totalQuantity += count;
            stats.totalItemsDropped += count;
        }
    }

    // ===== MOB LOOT STATISTICS =====

    /**
     * Get loot statistics for a specific mob type.
     *
     * @param mobType Mob type identifier
     * @return MobLootStats or null if not tracked
     */
    public MobLootStats getMobLootStats(String mobType) {
        return mobLootStats.get(mobType);
    }

    /**
     * Get all tracked mob types.
     *
     * @return Map of mob type to loot stats
     */
    public Map<String, MobLootStats> getAllMobLootStats() {
        return new ConcurrentHashMap<>(mobLootStats);
    }

    /**
     * Get drop percentage for a specific item from a specific mob.
     *
     * @param mobType Mob type identifier
     * @param itemId Item identifier
     * @return Drop percentage (0-100) or -1 if not tracked
     */
    public double getItemDropPercentage(String mobType, String itemId) {
        MobLootStats mobStats = mobLootStats.get(mobType);
        if (mobStats == null || mobStats.killCount == 0) return -1;

        ItemDropStats itemStats = mobStats.itemDrops.get(itemId);
        if (itemStats == null) return 0.0;

        return itemStats.getDropRate(mobStats.killCount);
    }

    /**
     * Get a summary of drop rates for a mob type.
     *
     * @param mobType Mob type identifier
     * @return MobDropSummary with all drop rates
     */
    public MobDropSummary getMobDropSummary(String mobType) {
        MobLootStats stats = mobLootStats.get(mobType);
        if (stats == null) {
            return new MobDropSummary(mobType, 0, 0, 0.0, 0.0, List.of());
        }

        List<ItemDropInfo> drops = new ArrayList<>();
        for (var entry : stats.itemDrops.entrySet()) {
            ItemDropStats itemStats = entry.getValue();
            drops.add(new ItemDropInfo(
                    entry.getKey(),
                    itemStats.dropCount,
                    itemStats.totalQuantity,
                    itemStats.getDropRate(stats.killCount)
            ));
        }

        // Sort by drop rate descending
        drops.sort((a, b) -> Double.compare(b.dropRate(), a.dropRate()));

        return new MobDropSummary(
                mobType,
                stats.killCount,
                stats.killsWithLoot,
                stats.getLootDropPercentage(),
                stats.getAverageItemsPerKill(),
                drops
        );
    }

    /**
     * Get top mobs by kill count.
     *
     * @param limit Maximum number of mobs to return
     * @return List of MobDropSummary sorted by kill count
     */
    public List<MobDropSummary> getTopKilledMobs(int limit) {
        return mobLootStats.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().killCount, a.getValue().killCount))
                .limit(limit)
                .map(e -> getMobDropSummary(e.getKey()))
                .toList();
    }

    /**
     * Get total mobs killed this session.
     */
    public int getTotalMobsKilled() {
        return totalMobsKilled;
    }

    // ===== M46: Reward Relevance =====

    /**
     * Record an item being used/consumed.
     *
     * @param player Player who used the item
     * @param item Item used
     * @return ItemUsedRecord for telemetry logging
     */
    public ItemUsedRecord recordItemUsed(ServerPlayer player, ItemStack item) {
        if (item.isEmpty()) return null;

        String itemId = item.getItem().toString();
        int count = item.getCount();

        // Update lifecycle
        ItemLifecycle lifecycle = itemLifecycles.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>())
                .computeIfAbsent(itemId, k -> new ItemLifecycle());
        lifecycle.used += count;

        // Update resource flow (outflow)
        ResourceFlowStats flow = resourceFlows.computeIfAbsent(itemId, k -> new ResourceFlowStats());
        flow.outflow += count;

        totalItemsUsed += count;

        return new ItemUsedRecord(
                player.getGameProfile().getName(),
                itemId,
                count,
                "consumed"
        );
    }

    /**
     * Record an item being discarded/dropped intentionally.
     *
     * @param player Player who discarded the item
     * @param item Item discarded
     * @return ItemDiscardRecord for telemetry logging
     */
    public ItemDiscardRecord recordItemDiscarded(ServerPlayer player, ItemStack item) {
        if (item.isEmpty()) return null;

        String itemId = item.getItem().toString();
        int count = item.getCount();

        // Update lifecycle
        ItemLifecycle lifecycle = itemLifecycles.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>())
                .computeIfAbsent(itemId, k -> new ItemLifecycle());
        lifecycle.discarded += count;

        return new ItemDiscardRecord(
                player.getGameProfile().getName(),
                itemId,
                count
        );
    }

    // ===== M50: Resource Scarcity =====

    /**
     * Get resource flow statistics for an item type.
     *
     * @param itemId Item identifier
     * @return ResourceFlowStats or null if not tracked
     */
    public ResourceFlowStats getResourceFlow(String itemId) {
        return resourceFlows.get(itemId);
    }

    /**
     * Calculate scarcity index for an item (0.0 = abundant, 1.0 = scarce).
     *
     * @param itemId Item identifier
     * @return Scarcity index between 0.0 and 1.0
     */
    public double calculateScarcityIndex(String itemId) {
        ResourceFlowStats flow = resourceFlows.get(itemId);
        if (flow == null || flow.inflow == 0) return 0.5; // Unknown = neutral

        // Scarcity = outflow / inflow (capped at 1.0)
        double ratio = (double) flow.outflow / flow.inflow;
        return Math.min(1.0, ratio);
    }

    // ===== Statistics & Reports =====

    /**
     * Get loot distribution summary for a room.
     *
     * @param roomId Room identifier
     * @return LootDistributionSummary
     */
    public LootDistributionSummary getRoomLootSummary(String roomId) {
        Map<BlockPos, ChestStats> chests = chestOpenings.get(roomId);
        Map<String, Integer> drops = itemDropsPerRoom.get(roomId);

        int totalChests = chests != null ? chests.size() : 0;
        int totalOpens = chests != null ? chests.values().stream().mapToInt(s -> s.openCount).sum() : 0;
        int uniqueItems = drops != null ? drops.size() : 0;
        int totalItems = drops != null ? drops.values().stream().mapToInt(Integer::intValue).sum() : 0;

        return new LootDistributionSummary(roomId, totalChests, totalOpens, uniqueItems, totalItems);
    }

    /**
     * Get session economy statistics.
     *
     * @return SessionEconomyStats
     */
    public SessionEconomyStats getSessionStats() {
        long durationMs = System.currentTimeMillis() - sessionStartTime;
        double itemsPerMinute = totalItemsPickedUp / (durationMs / 60000.0);

        // Find most acquired item
        String mostAcquired = "";
        int mostAcquiredCount = 0;
        for (var entry : itemAcquisitionStats.entrySet()) {
            if (entry.getValue().totalAcquired > mostAcquiredCount) {
                mostAcquired = entry.getKey();
                mostAcquiredCount = entry.getValue().totalAcquired;
            }
        }

        // Find most scarce item
        String mostScarce = "";
        double highestScarcity = 0;
        for (String itemId : resourceFlows.keySet()) {
            double scarcity = calculateScarcityIndex(itemId);
            if (scarcity > highestScarcity) {
                mostScarce = itemId;
                highestScarcity = scarcity;
            }
        }

        return new SessionEconomyStats(
                durationMs,
                totalChestsOpened,
                totalItemsPickedUp,
                totalItemsDropped,
                totalItemsUsed,
                itemsPerMinute,
                mostAcquired,
                mostAcquiredCount,
                mostScarce,
                highestScarcity
        );
    }

    /**
     * Export economy data as JSON lines.
     *
     * @param consumer Receives (category, jsonLine) pairs
     */
    public void exportEconomyData(BiConsumer<String, String> consumer) {
        // Export chest stats
        chestOpenings.forEach((room, chests) -> {
            chests.forEach((pos, stats) -> {
                String line = "{\"room\":\"" + TelemetryJson.escape(room) + "\","
                        + "\"pos\":[" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "],"
                        + "\"openCount\":" + stats.openCount + ","
                        + "\"itemTypes\":" + stats.itemsFound.size() + "}";
                consumer.accept("chests", line);
            });
        });

        // Export item acquisition stats
        itemAcquisitionStats.forEach((itemId, stats) -> {
            String line = "{\"item\":\"" + TelemetryJson.escape(itemId) + "\","
                    + "\"acquired\":" + stats.totalAcquired + ","
                    + "\"dropped\":" + stats.totalDropped + "}";
            consumer.accept("items", line);
        });

        // Export resource flows
        resourceFlows.forEach((itemId, flow) -> {
            String line = "{\"item\":\"" + TelemetryJson.escape(itemId) + "\","
                    + "\"inflow\":" + flow.inflow + ","
                    + "\"outflow\":" + flow.outflow + ","
                    + "\"scarcity\":" + String.format("%.3f", calculateScarcityIndex(itemId)) + "}";
            consumer.accept("resource_flow", line);
        });
    }

    // ===== Cleanup =====

    /**
     * Clear all tracked data (for reload).
     */
    public void clear() {
        chestOpenings.clear();
        itemDropsPerRoom.clear();
        itemPickupsPerPlayer.clear();
        itemAcquisitionStats.clear();
        itemLifecycles.clear();
        resourceFlows.clear();
        mobLootStats.clear();
        resetSessionStats();
    }

    /**
     * Reset session statistics.
     */
    public void resetSessionStats() {
        sessionStartTime = System.currentTimeMillis();
        totalChestsOpened = 0;
        totalItemsPickedUp = 0;
        totalItemsDropped = 0;
        totalItemsUsed = 0;
        totalMobsKilled = 0;
    }

    /**
     * Cleanup data for a specific player.
     *
     * @param playerId Player UUID
     */
    public void cleanupPlayer(UUID playerId) {
        itemPickupsPerPlayer.remove(playerId);
        itemLifecycles.remove(playerId);
    }

    // ===== Data Classes =====

    /**
     * Statistics for a single chest.
     */
    public static class ChestStats {
        public int openCount = 0;
        public UUID lastOpenedBy = null;
        public long lastOpenedTime = 0;
        public Map<String, Integer> itemsFound = new ConcurrentHashMap<>();
    }

    /**
     * Acquisition statistics for an item type.
     */
    public static class ItemAcquisitionStats {
        public int totalAcquired = 0;
        public int totalDropped = 0;
        public long lastAcquiredTime = 0;
        public Map<String, Integer> dropSources = new ConcurrentHashMap<>();
    }

    /**
     * Lifecycle tracking for items in player inventory.
     */
    public static class ItemLifecycle {
        public int acquired = 0;
        public int used = 0;
        public int discarded = 0;
        public int equipped = 0;
    }

    /**
     * Resource flow statistics (inflow vs outflow).
     */
    public static class ResourceFlowStats {
        public int inflow = 0;  // Items acquired
        public int outflow = 0; // Items consumed/used
    }

    /**
     * Loot statistics for a specific mob type.
     * Tracks kills, drops, and calculates drop percentages.
     */
    public static class MobLootStats {
        public int killCount = 0;                    // Total times this mob was killed
        public int killsWithLoot = 0;                // Kills that dropped at least one item
        public int totalItemsDropped = 0;            // Total items dropped across all kills
        public Map<String, ItemDropStats> itemDrops = new ConcurrentHashMap<>(); // Per-item stats
        public long lastKillTime = 0;

        /**
         * Calculate the percentage of kills that dropped any loot.
         * @return Percentage (0-100) of kills with loot
         */
        public double getLootDropPercentage() {
            if (killCount == 0) return 0.0;
            return (killsWithLoot * 100.0) / killCount;
        }

        /**
         * Get average items per kill.
         * @return Average items dropped per kill
         */
        public double getAverageItemsPerKill() {
            if (killCount == 0) return 0.0;
            return (double) totalItemsDropped / killCount;
        }
    }

    /**
     * Drop statistics for a specific item from a specific mob.
     */
    public static class ItemDropStats {
        public int dropCount = 0;      // How many times this item dropped
        public int totalQuantity = 0;  // Total quantity dropped (sum of stack sizes)

        /**
         * Calculate drop rate percentage based on mob kill count.
         * @param mobKillCount Total kills of the parent mob
         * @return Percentage (0-100) of kills that dropped this item
         */
        public double getDropRate(int mobKillCount) {
            if (mobKillCount == 0) return 0.0;
            return (dropCount * 100.0) / mobKillCount;
        }
    }

    // ===== Records for Telemetry Logging =====

    public record ChestOpenRecord(String player, String roomId, BlockPos pos, long itemCount, int totalOpens) {
        public String toJson() {
            return "{\"ts\":\"" + Instant.now() + "\","
                    + "\"type\":\"chest_open\","
                    + "\"player\":\"" + TelemetryJson.escape(player) + "\","
                    + "\"room\":\"" + TelemetryJson.escape(roomId) + "\","
                    + "\"pos\":[" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "],"
                    + "\"items\":" + itemCount + ","
                    + "\"totalOpens\":" + totalOpens + "}";
        }
    }

    public record ItemPickupRecord(String player, String roomId, String itemId, int count, BlockPos pos) {
        public String toJson() {
            return "{\"ts\":\"" + Instant.now() + "\","
                    + "\"type\":\"item_pickup\","
                    + "\"player\":\"" + TelemetryJson.escape(player) + "\","
                    + "\"room\":\"" + TelemetryJson.escape(roomId) + "\","
                    + "\"item\":\"" + TelemetryJson.escape(itemId) + "\","
                    + "\"count\":" + count + ","
                    + "\"pos\":[" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "]}";
        }
    }

    public record MobDropRecord(String mobType, String roomId, String itemId, int count, BlockPos pos) {
        public String toJson() {
            return "{\"ts\":\"" + Instant.now() + "\","
                    + "\"type\":\"mob_drop\","
                    + "\"mob\":\"" + TelemetryJson.escape(mobType) + "\","
                    + "\"room\":\"" + TelemetryJson.escape(roomId) + "\","
                    + "\"item\":\"" + TelemetryJson.escape(itemId) + "\","
                    + "\"count\":" + count + ","
                    + "\"pos\":[" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "]}";
        }
    }

    public record ItemUsedRecord(String player, String itemId, int count, String useType) {
        public String toJson() {
            return "{\"ts\":\"" + Instant.now() + "\","
                    + "\"type\":\"item_used\","
                    + "\"player\":\"" + TelemetryJson.escape(player) + "\","
                    + "\"item\":\"" + TelemetryJson.escape(itemId) + "\","
                    + "\"count\":" + count + ","
                    + "\"use\":\"" + TelemetryJson.escape(useType) + "\"}";
        }
    }

    public record ItemDiscardRecord(String player, String itemId, int count) {
        public String toJson() {
            return "{\"ts\":\"" + Instant.now() + "\","
                    + "\"type\":\"item_discard\","
                    + "\"player\":\"" + TelemetryJson.escape(player) + "\","
                    + "\"item\":\"" + TelemetryJson.escape(itemId) + "\","
                    + "\"count\":" + count + "}";
        }
    }

    public record LootDistributionSummary(String roomId, int totalChests, int totalOpens, int uniqueItems, int totalItems) {}

    public record MobKillRecord(String mobType, int totalKills, boolean hadLoot) {
        public String toJson() {
            return "{\"ts\":\"" + Instant.now() + "\","
                    + "\"type\":\"mob_kill\","
                    + "\"mob\":\"" + TelemetryJson.escape(mobType) + "\","
                    + "\"totalKills\":" + totalKills + ","
                    + "\"hadLoot\":" + hadLoot + "}";
        }
    }

    public record ItemDropInfo(String itemId, int dropCount, int totalQuantity, double dropRate) {}

    public record MobDropSummary(
            String mobType,
            int killCount,
            int killsWithLoot,
            double lootDropPercentage,
            double avgItemsPerKill,
            List<ItemDropInfo> itemDrops
    ) {}

    public record SessionEconomyStats(
            long durationMs,
            int chestsOpened,
            int itemsPickedUp,
            int itemsDropped,
            int itemsUsed,
            double itemsPerMinute,
            String mostAcquiredItem,
            int mostAcquiredCount,
            String mostScarceItem,
            double scarcityIndex
    ) {}
}
