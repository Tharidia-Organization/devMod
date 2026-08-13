package com.devmod.telemetry.duckdb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

public class DuckDBQueryAPI {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final RateLimitedLogger RATE_LIMITED = new RateLimitedLogger(LOGGER, DuckDBConfig.LOG_RATE_LIMIT_MS);

    private final DuckDBConnectionManager connectionManager;

    public DuckDBQueryAPI(DuckDBConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    /**
     * Create a Statement with analytics query timeout.
     */
    private Statement createStatement(Connection conn) throws SQLException {
        Statement stmt = conn.createStatement();
        stmt.setQueryTimeout(DuckDBConfig.ANALYTICS_QUERY_TIMEOUT_SECONDS);
        return stmt;
    }

    /**
     * Prepare a Statement with analytics query timeout.
     */
    private PreparedStatement prepareStatement(Connection conn, String sql) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setQueryTimeout(DuckDBConfig.ANALYTICS_QUERY_TIMEOUT_SECONDS);
        return stmt;
    }

    // ============================================
    // COMBAT ANALYTICS
    // ============================================

    /**
     * Get weapon damage summaries.
     */
    public List<WeaponSummary> getWeaponSummaries() {
        String sql = """
            SELECT
                COALESCE(attacker_state->>'mainHand', 'fist') as weapon,
                SUM(damage) as total_damage,
                COUNT(*) as hits,
                COUNT(*) FILTER (WHERE hp_after <= 0) as kills,
                COUNT(*) FILTER (WHERE is_miss) as misses,
                ROUND(100.0 * COUNT(*) FILTER (WHERE NOT is_miss) / NULLIF(COUNT(*), 0), 1) as accuracy
            FROM combat_hits
            WHERE attacker_type LIKE '%player%'
            GROUP BY weapon
            ORDER BY total_damage DESC
            LIMIT 100
            """;

        List<WeaponSummary> results = new ArrayList<>();
        try {
            Connection conn = connectionManager.getConnection();
            try (var lease = connectionManager.lockStatements();
                 var stmt = createStatement(conn);
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    results.add(new WeaponSummary(
                        rs.getString("weapon"),
                        rs.getDouble("total_damage"),
                        rs.getInt("hits"),
                        rs.getInt("kills"),
                        rs.getInt("misses"),
                        rs.getDouble("accuracy")
                    ));
                }
            }
        } catch (SQLException e) {
            RATE_LIMITED.error("weapon_summaries", "[DuckDB] Failed to get weapon summaries: {}", safeMessage(e));
        }
        return results;
    }

    /**
     * Get damage by room.
     */
    public List<RoomDamageSummary> getRoomDamageSummaries() {
        String sql = """
            SELECT
                room,
                SUM(damage) as total_damage,
                COUNT(*) as total_hits,
                COUNT(DISTINCT attacker_name) as unique_attackers,
                AVG(damage) as avg_damage_per_hit
            FROM combat_hits
            WHERE room IS NOT NULL
            GROUP BY room
            ORDER BY total_damage DESC
            LIMIT 50
            """;

        List<RoomDamageSummary> results = new ArrayList<>();
        try {
            Connection conn = connectionManager.getConnection();
            try (var lease = connectionManager.lockStatements();
                 var stmt = createStatement(conn);
                 var rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    results.add(new RoomDamageSummary(
                        rs.getString("room"),
                        rs.getDouble("total_damage"),
                        rs.getInt("total_hits"),
                        rs.getInt("unique_attackers"),
                        rs.getDouble("avg_damage_per_hit")
                    ));
                }
            }
        } catch (SQLException e) {
            RATE_LIMITED.error("room_damage", "[DuckDB] Failed to get room damage summaries: {}", safeMessage(e));
        }
        return results;
    }

    // ============================================
    // ENDURANCE ANALYTICS
    // ============================================

    /**
     * Get endurance quest statistics for a player.
     */
    @Nullable
    public EnduranceStats getEnduranceStats(UUID playerId) {
        String sql = """
            SELECT
                COUNT(*) as total_quests,
                COUNT(*) FILTER (WHERE outcome = 'COMPLETED') as completed,
                COUNT(*) FILTER (WHERE outcome = 'FAILED') as failed,
                COUNT(*) FILTER (WHERE outcome = 'ABANDONED') as abandoned,
                SUM(tokens_earned) as total_tokens,
                SUM(prestige_earned) as total_prestige,
                SUM(blood_gems_earned) as total_blood_gems,
                MAX(waves_completed) as best_wave,
                AVG(waves_completed) as avg_waves,
                SUM(total_kills) as total_kills,
                SUM(damage_dealt) as total_damage_dealt,
                SUM(damage_taken) as total_damage_taken
            FROM endurance_sessions
            WHERE player_id = ?
            """;

        try {
            Connection conn = connectionManager.getConnection();
            try (var lease = connectionManager.lockStatements();
                 var stmt = prepareStatement(conn, sql)) {
                stmt.setObject(1, playerId);
                try (var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new EnduranceStats(
                            playerId,
                            rs.getInt("total_quests"),
                            rs.getInt("completed"),
                            rs.getInt("failed"),
                            rs.getInt("abandoned"),
                            rs.getInt("total_tokens"),
                            rs.getInt("total_prestige"),
                            rs.getInt("total_blood_gems"),
                            rs.getInt("best_wave"),
                            rs.getDouble("avg_waves"),
                            rs.getInt("total_kills"),
                            rs.getDouble("total_damage_dealt"),
                            rs.getDouble("total_damage_taken")
                        );
                    }
                }
            }
        } catch (SQLException e) {
            RATE_LIMITED.error("endurance_stats", "[DuckDB] Failed to get endurance stats: {}", safeMessage(e));
        }
        return null;
    }

    /**
     * Get perk usage statistics.
     */
    public List<PerkUsageSummary> getPerkUsageStats() {
        String sql = """
            SELECT
                perk_id,
                perk_name,
                tier,
                category,
                COUNT(*) as times_selected,
                COUNT(DISTINCT player_id) as unique_players
            FROM endurance_perks
            WHERE event_type = 'selected'
            GROUP BY perk_id, perk_name, tier, category
            ORDER BY times_selected DESC
            LIMIT 50
            """;

        List<PerkUsageSummary> results = new ArrayList<>();
        try {
            Connection conn = connectionManager.getConnection();
            try (var lease = connectionManager.lockStatements();
                 var stmt = createStatement(conn);
                 var rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    results.add(new PerkUsageSummary(
                        rs.getString("perk_id"),
                        rs.getString("perk_name"),
                        rs.getString("tier"),
                        rs.getString("category"),
                        rs.getInt("times_selected"),
                        rs.getInt("unique_players")
                    ));
                }
            }
        } catch (SQLException e) {
            RATE_LIMITED.error("perk_usage", "[DuckDB] Failed to get perk usage stats: {}", safeMessage(e));
        }
        return results;
    }

    // ============================================
    // PLAYER ANALYTICS
    // ============================================

    /**
     * Get ability usage statistics for a player.
     */
    public AbilityStats getAbilityStats(UUID playerId) {
        String sql = """
            SELECT
                ability_type,
                COUNT(*) as total_uses,
                COUNT(*) FILTER (WHERE success = true) as successes,
                AVG(stamina_cost) as avg_stamina_cost,
                SUM(damage_negated) as total_damage_negated
            FROM player_abilities
            WHERE player_id = ?
            GROUP BY ability_type
            """;

        AbilityStats stats = new AbilityStats(playerId);
        try {
            Connection conn = connectionManager.getConnection();
            try (var lease = connectionManager.lockStatements();
                 var stmt = prepareStatement(conn, sql)) {
                stmt.setObject(1, playerId);
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String type = rs.getString("ability_type");
                        int total = rs.getInt("total_uses");
                        int successes = rs.getInt("successes");
                        double avgCost = rs.getDouble("avg_stamina_cost");
                        double damageNegated = rs.getDouble("total_damage_negated");

                        switch (type) {
                            case "dash" -> {
                                stats.dashAttempts = total;
                                stats.dashSuccesses = successes;
                                stats.avgDashStaminaCost = avgCost;
                            }
                            case "dodge" -> {
                                stats.dodgeAttempts = total;
                                stats.dodgeSuccesses = successes;
                                stats.avgDodgeStaminaCost = avgCost;
                            }
                            case "perfect_dodge" -> {
                                stats.perfectDodges = total;
                                stats.totalDamageNegated = damageNegated;
                            }
                            default -> {
                                // Unknown ability type - ignore
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            RATE_LIMITED.error("ability_stats", "[DuckDB] Failed to get ability stats: {}", safeMessage(e));
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[DuckDB] Ability stats for {}: avgDashCost={}, avgDodgeCost={}, perfectDodges={}, damageNegated={}",
                playerId, stats.avgDashStaminaCost, stats.avgDodgeStaminaCost, stats.perfectDodges, stats.totalDamageNegated);
        }
        return stats;
    }

    // ============================================
    // SPATIAL ANALYTICS
    // ============================================

    /**
     * Get heatmap data for visualization.
     */
    public List<HeatmapCell> getHeatmap(String heatmapType, String room) {
        String sql = """
            SELECT x, y, z, SUM(count) as intensity
            FROM spatial_heatmaps
            WHERE heatmap_type = ? AND room = ?
            GROUP BY x, y, z
            ORDER BY intensity DESC
            LIMIT 1000
            """;

        List<HeatmapCell> results = new ArrayList<>();
        try {
            Connection conn = connectionManager.getConnection();
            try (var lease = connectionManager.lockStatements();
                 var stmt = prepareStatement(conn, sql)) {
                stmt.setString(1, heatmapType);
                stmt.setString(2, room);
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        results.add(new HeatmapCell(
                            rs.getInt("x"),
                            rs.getInt("y"),
                            rs.getInt("z"),
                            rs.getInt("intensity")
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            RATE_LIMITED.error("heatmap", "[DuckDB] Failed to get heatmap: {}", safeMessage(e));
        }
        return results;
    }

    // ============================================
    // PERFORMANCE ANALYTICS
    // ============================================

    /**
     * Get performance time series data.
     */
    public List<PerformanceSample> getPerformanceTimeSeries(Duration window) {
        String sql = """
            SELECT
                ts,
                mspt,
                tps
            FROM performance_samples
            WHERE ts > ?
            ORDER BY ts
            LIMIT 1000
            """;

        List<PerformanceSample> results = new ArrayList<>();
        try {
            Connection conn = connectionManager.getConnection();
            try (var lease = connectionManager.lockStatements();
                 var stmt = prepareStatement(conn, sql)) {
                stmt.setTimestamp(1, Timestamp.from(Instant.now().minus(window)));
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        results.add(new PerformanceSample(
                            rs.getTimestamp("ts").toInstant(),
                            rs.getDouble("mspt"),
                            rs.getDouble("tps")
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            RATE_LIMITED.error("perf_time_series", "[DuckDB] Failed to get performance time series: {}", safeMessage(e));
        }
        return results;
    }

    /**
     * Get average performance metrics.
     */
    @Nullable
    public PerformanceAverage getPerformanceAverage(Duration window) {
        String sql = """
            SELECT
                AVG(mspt) as avg_mspt,
                MAX(mspt) as max_mspt,
                MIN(mspt) as min_mspt,
                AVG(tps) as avg_tps,
                MIN(tps) as min_tps
            FROM performance_samples
            WHERE ts > ?
            """;

        try {
            Connection conn = connectionManager.getConnection();
            try (var lease = connectionManager.lockStatements();
                 var stmt = prepareStatement(conn, sql)) {
                stmt.setTimestamp(1, Timestamp.from(Instant.now().minus(window)));
                try (var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new PerformanceAverage(
                            rs.getDouble("avg_mspt"),
                            rs.getDouble("max_mspt"),
                            rs.getDouble("min_mspt"),
                            rs.getDouble("avg_tps"),
                            rs.getDouble("min_tps")
                        );
                    }
                }
            }
        } catch (SQLException e) {
            RATE_LIMITED.error("perf_average", "[DuckDB] Failed to get performance average: {}", safeMessage(e));
        }
        return null;
    }

    // ============================================
    // DATA RECORDS
    // ============================================

    public record WeaponSummary(
        String weapon,
        double totalDamage,
        int hits,
        int kills,
        int misses,
        double accuracy
    ) {}

    public record RoomDamageSummary(
        String room,
        double totalDamage,
        int totalHits,
        int uniqueAttackers,
        double avgDamagePerHit
    ) {}

    public record EnduranceStats(
        UUID playerId,
        int totalQuests,
        int completed,
        int failed,
        int abandoned,
        int totalTokens,
        int totalPrestige,
        int totalBloodGems,
        int bestWave,
        double avgWaves,
        int totalKills,
        double totalDamageDealt,
        double totalDamageTaken
    ) {}

    public record PerkUsageSummary(
        String perkId,
        String perkName,
        String tier,
        String category,
        int timesSelected,
        int uniquePlayers
    ) {}

    public static class AbilityStats {
        final UUID playerId;
        int dashAttempts = 0;
        int dashSuccesses = 0;
        double avgDashStaminaCost = 0;
        int dodgeAttempts = 0;
        int dodgeSuccesses = 0;
        double avgDodgeStaminaCost = 0;
        int perfectDodges = 0;
        double totalDamageNegated = 0;

        public AbilityStats(UUID playerId) {
            this.playerId = playerId;
        }

        public double getDashSuccessRate() {
            return dashAttempts > 0 ? (double) dashSuccesses / dashAttempts : 0;
        }

        public double getDodgeSuccessRate() {
            return dodgeAttempts > 0 ? (double) dodgeSuccesses / dodgeAttempts : 0;
        }
    }

    public record HeatmapCell(int x, int y, int z, int intensity) {}

    public record PerformanceSample(Instant timestamp, double mspt, double tps) {}

    public record PerformanceAverage(
        double avgMspt,
        double maxMspt,
        double minMspt,
        double avgTps,
        double minTps
    ) {}

    // ============================================
    // ECONOMY ANALYTICS (P1)
    // ============================================

    /**
     * Get mob kill counts from last N minutes.
     */
    public List<MobKillSummary> getRecentMobKills(int minutes) {
        String sql = """
            SELECT mob_type, COUNT(*) as kill_count,
                   SUM(CASE WHEN had_loot THEN 1 ELSE 0 END) as loot_drops
            FROM economy_mob_kills
            WHERE ts >= NOW() - INTERVAL '%d MINUTES'
            GROUP BY mob_type
            ORDER BY kill_count DESC
            LIMIT 20
            """.formatted(minutes);

        List<MobKillSummary> results = new ArrayList<>();
        try {
            Connection conn = connectionManager.getConnection();
            try (var lease = connectionManager.lockStatements();
                 var stmt = createStatement(conn);
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    results.add(new MobKillSummary(
                        rs.getString("mob_type"),
                        rs.getInt("kill_count"),
                        rs.getInt("loot_drops")
                    ));
                }
            }
        } catch (SQLException e) {
            RATE_LIMITED.warn("mob_kills", "[DuckDB] Failed to query mob kills: {}", safeMessage(e));
        }
        return results;
    }

    /**
     * Get recent mob drops from last N minutes.
     */
    public List<MobDropSummary> getRecentMobDrops(int minutes) {
        String sql = """
            SELECT mob_type, item_id, SUM(item_count) as total_count, COUNT(*) as drop_events
            FROM economy_mob_drops
            WHERE ts >= NOW() - INTERVAL '%d MINUTES'
            GROUP BY mob_type, item_id
            ORDER BY drop_events DESC
            LIMIT 20
            """.formatted(minutes);

        List<MobDropSummary> results = new ArrayList<>();
        try {
            Connection conn = connectionManager.getConnection();
            try (var lease = connectionManager.lockStatements();
                 var stmt = createStatement(conn);
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    results.add(new MobDropSummary(
                        rs.getString("mob_type"),
                        rs.getString("item_id"),
                        rs.getInt("total_count"),
                        rs.getInt("drop_events")
                    ));
                }
            }
        } catch (SQLException e) {
            RATE_LIMITED.warn("mob_drops", "[DuckDB] Failed to query mob drops: {}", safeMessage(e));
        }
        return results;
    }

    /**
     * Get recent item pickups from last N minutes.
     */
    public List<ItemPickupSummary> getRecentItemPickups(int minutes) {
        String sql = """
            SELECT player_name, item_id, SUM(item_count) as total_count, COUNT(*) as pickup_events
            FROM economy_item_pickups
            WHERE ts >= NOW() - INTERVAL '%d MINUTES'
            GROUP BY player_name, item_id
            ORDER BY pickup_events DESC
            LIMIT 20
            """.formatted(minutes);

        List<ItemPickupSummary> results = new ArrayList<>();
        try {
            Connection conn = connectionManager.getConnection();
            try (var lease = connectionManager.lockStatements();
                 var stmt = createStatement(conn);
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    results.add(new ItemPickupSummary(
                        rs.getString("player_name"),
                        rs.getString("item_id"),
                        rs.getInt("total_count"),
                        rs.getInt("pickup_events")
                    ));
                }
            }
        } catch (SQLException e) {
            RATE_LIMITED.warn("item_pickups", "[DuckDB] Failed to query item pickups: {}", safeMessage(e));
        }
        return results;
    }

    /**
     * Get recent item usage from last N minutes.
     */
    public List<ItemUsageSummary> getRecentItemUsage(int minutes) {
        String sql = """
            SELECT player_name, event_type, item_id, SUM(item_count) as total_count, COUNT(*) as events
            FROM economy_item_usage
            WHERE ts >= NOW() - INTERVAL '%d MINUTES'
            GROUP BY player_name, event_type, item_id
            ORDER BY events DESC
            LIMIT 20
            """.formatted(minutes);

        List<ItemUsageSummary> results = new ArrayList<>();
        try {
            Connection conn = connectionManager.getConnection();
            try (var lease = connectionManager.lockStatements();
                 var stmt = createStatement(conn);
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    results.add(new ItemUsageSummary(
                        rs.getString("player_name"),
                        rs.getString("event_type"),
                        rs.getString("item_id"),
                        rs.getInt("total_count"),
                        rs.getInt("events")
                    ));
                }
            }
        } catch (SQLException e) {
            RATE_LIMITED.warn("item_usage", "[DuckDB] Failed to query item usage: {}", safeMessage(e));
        }
        return results;
    }

    /**
     * Get economy table counts for last N minutes (for P1 gate test).
     */
    public EconomyTableCounts getEconomyTableCounts(int minutes) {
        EconomyTableCounts counts = new EconomyTableCounts();
        String interval = "NOW() - INTERVAL '%d MINUTES'".formatted(minutes);

        try {
            Connection conn = connectionManager.getConnection();
            try (var lease = connectionManager.lockStatements();
                 var stmt = createStatement(conn)) {
                // economy_mob_kills
                try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM economy_mob_kills WHERE ts >= " + interval)) {
                    if (rs.next()) counts.mobKills = rs.getInt(1);
                }
                // economy_mob_drops
                try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM economy_mob_drops WHERE ts >= " + interval)) {
                    if (rs.next()) counts.mobDrops = rs.getInt(1);
                }
                // economy_item_pickups
                try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM economy_item_pickups WHERE ts >= " + interval)) {
                    if (rs.next()) counts.itemPickups = rs.getInt(1);
                }
                // economy_item_usage
                try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM economy_item_usage WHERE ts >= " + interval)) {
                    if (rs.next()) counts.itemUsage = rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            RATE_LIMITED.warn("economy_counts", "[DuckDB] Failed to query economy counts: {}", safeMessage(e));
        }
        return counts;
    }

    // Economy records
    public record MobKillSummary(String mobType, int killCount, int lootDrops) {}
    public record MobDropSummary(String mobType, String itemId, int totalCount, int dropEvents) {}
    public record ItemPickupSummary(String playerName, String itemId, int totalCount, int pickupEvents) {}
    public record ItemUsageSummary(String playerName, String eventType, String itemId, int totalCount, int events) {}

    public static class EconomyTableCounts {
        public int mobKills = 0;
        public int mobDrops = 0;
        public int itemPickups = 0;
        public int itemUsage = 0;

        public boolean allNonZero() {
            return mobKills > 0 && mobDrops > 0 && itemPickups > 0 && itemUsage > 0;
        }

        @Override
        public String toString() {
            return "EconomyTableCounts{mobKills=%d, mobDrops=%d, itemPickups=%d, itemUsage=%d}"
                .formatted(mobKills, mobDrops, itemPickups, itemUsage);
        }
    }

    // ============================================
    // PROGRESSION ANALYTICS (P1)
    // ============================================

    /**
     * Get block event summaries from last N minutes.
     */
    public List<BlockEventSummary> getRecentBlockEvents(int minutes) {
        String sql = """
            SELECT player_name, event_type, block_id, COUNT(*) as event_count
            FROM progression_blocks
            WHERE ts >= NOW() - INTERVAL '%d MINUTES'
            GROUP BY player_name, event_type, block_id
            ORDER BY event_count DESC
            LIMIT 20
            """.formatted(minutes);

        List<BlockEventSummary> results = new ArrayList<>();
        try {
            Connection conn = connectionManager.getConnection();
            try (var lease = connectionManager.lockStatements();
                 var stmt = createStatement(conn);
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    results.add(new BlockEventSummary(
                        rs.getString("player_name"),
                        rs.getString("event_type"),
                        rs.getString("block_id"),
                        rs.getInt("event_count")
                    ));
                }
            }
        } catch (SQLException e) {
            RATE_LIMITED.warn("block_events", "[DuckDB] Failed to query block events: {}", safeMessage(e));
        }
        return results;
    }

    /**
     * Get XP event summaries from last N minutes.
     */
    public List<XpEventSummary> getRecentXpEvents(int minutes) {
        String sql = """
            SELECT player_name, event_type, SUM(xp_amount) as total_xp,
                   MIN(old_level) as start_level, MAX(new_level) as end_level, COUNT(*) as event_count
            FROM progression_xp
            WHERE ts >= NOW() - INTERVAL '%d MINUTES'
            GROUP BY player_name, event_type
            ORDER BY total_xp DESC
            LIMIT 20
            """.formatted(minutes);

        List<XpEventSummary> results = new ArrayList<>();
        try {
            Connection conn = connectionManager.getConnection();
            try (var lease = connectionManager.lockStatements();
                 var stmt = createStatement(conn);
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    results.add(new XpEventSummary(
                        rs.getString("player_name"),
                        rs.getString("event_type"),
                        rs.getInt("total_xp"),
                        rs.getInt("start_level"),
                        rs.getInt("end_level"),
                        rs.getInt("event_count")
                    ));
                }
            }
        } catch (SQLException e) {
            RATE_LIMITED.warn("xp_events", "[DuckDB] Failed to query XP events: {}", safeMessage(e));
        }
        return results;
    }

    /**
     * Get recent advancements from last N minutes.
     */
    public List<AdvancementSummary> getRecentAdvancements(int minutes) {
        String sql = """
            SELECT player_name, advancement_id, title, ts
            FROM progression_advancements
            WHERE ts >= NOW() - INTERVAL '%d MINUTES'
            ORDER BY ts DESC
            LIMIT 20
            """.formatted(minutes);

        List<AdvancementSummary> results = new ArrayList<>();
        try {
            Connection conn = connectionManager.getConnection();
            try (var lease = connectionManager.lockStatements();
                 var stmt = createStatement(conn);
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    results.add(new AdvancementSummary(
                        rs.getString("player_name"),
                        rs.getString("advancement_id"),
                        rs.getString("title"),
                        rs.getTimestamp("ts").toInstant()
                    ));
                }
            }
        } catch (SQLException e) {
            RATE_LIMITED.warn("advancements", "[DuckDB] Failed to query advancements: {}", safeMessage(e));
        }
        return results;
    }

    /**
     * Get dimension changes from last N minutes.
     */
    public List<DimensionChangeSummary> getRecentDimensionChanges(int minutes) {
        String sql = """
            SELECT player_name, from_dimension, to_dimension, COUNT(*) as change_count
            FROM progression_dimensions
            WHERE ts >= NOW() - INTERVAL '%d MINUTES'
            GROUP BY player_name, from_dimension, to_dimension
            ORDER BY change_count DESC
            LIMIT 20
            """.formatted(minutes);

        List<DimensionChangeSummary> results = new ArrayList<>();
        try {
            Connection conn = connectionManager.getConnection();
            try (var lease = connectionManager.lockStatements();
                 var stmt = createStatement(conn);
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    results.add(new DimensionChangeSummary(
                        rs.getString("player_name"),
                        rs.getString("from_dimension"),
                        rs.getString("to_dimension"),
                        rs.getInt("change_count")
                    ));
                }
            }
        } catch (SQLException e) {
            RATE_LIMITED.warn("dimension_changes", "[DuckDB] Failed to query dimension changes: {}", safeMessage(e));
        }
        return results;
    }

    /**
     * Get trade summaries from last N minutes.
     */
    public List<TradeSummary> getRecentTrades(int minutes) {
        String sql = """
            SELECT player_name, profession, item_bought, SUM(item_bought_count) as total_bought,
                   item_sold, SUM(item_sold_count) as total_sold, COUNT(*) as trade_count
            FROM progression_trades
            WHERE ts >= NOW() - INTERVAL '%d MINUTES'
            GROUP BY player_name, profession, item_bought, item_sold
            ORDER BY trade_count DESC
            LIMIT 20
            """.formatted(minutes);

        List<TradeSummary> results = new ArrayList<>();
        try {
            Connection conn = connectionManager.getConnection();
            try (var lease = connectionManager.lockStatements();
                 var stmt = createStatement(conn);
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    results.add(new TradeSummary(
                        rs.getString("player_name"),
                        rs.getString("profession"),
                        rs.getString("item_bought"),
                        rs.getInt("total_bought"),
                        rs.getString("item_sold"),
                        rs.getInt("total_sold"),
                        rs.getInt("trade_count")
                    ));
                }
            }
        } catch (SQLException e) {
            RATE_LIMITED.warn("trades", "[DuckDB] Failed to query trades: {}", safeMessage(e));
        }
        return results;
    }

    /**
     * Get fishing summaries from last N minutes.
     */
    public List<FishingSummary> getRecentFishing(int minutes) {
        String sql = """
            SELECT player_name, item_id, SUM(item_count) as total_count, COUNT(*) as catch_count
            FROM progression_fishing
            WHERE ts >= NOW() - INTERVAL '%d MINUTES'
            GROUP BY player_name, item_id
            ORDER BY catch_count DESC
            LIMIT 20
            """.formatted(minutes);

        List<FishingSummary> results = new ArrayList<>();
        try {
            Connection conn = connectionManager.getConnection();
            try (var lease = connectionManager.lockStatements();
                 var stmt = createStatement(conn);
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    results.add(new FishingSummary(
                        rs.getString("player_name"),
                        rs.getString("item_id"),
                        rs.getInt("total_count"),
                        rs.getInt("catch_count")
                    ));
                }
            }
        } catch (SQLException e) {
            RATE_LIMITED.warn("fishing", "[DuckDB] Failed to query fishing: {}", safeMessage(e));
        }
        return results;
    }

    /**
     * Get progression table counts for last N minutes (for P1 gate test).
     */
    public ProgressionTableCounts getProgressionTableCounts(int minutes) {
        ProgressionTableCounts counts = new ProgressionTableCounts();
        String interval = "NOW() - INTERVAL '%d MINUTES'".formatted(minutes);

        try {
            Connection conn = connectionManager.getConnection();
            try (var lease = connectionManager.lockStatements();
                 var stmt = createStatement(conn)) {
                try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM progression_blocks WHERE ts >= " + interval)) {
                    if (rs.next()) counts.blocks = rs.getInt(1);
                }
                try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM progression_xp WHERE ts >= " + interval)) {
                    if (rs.next()) counts.xp = rs.getInt(1);
                }
                try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM progression_advancements WHERE ts >= " + interval)) {
                    if (rs.next()) counts.advancements = rs.getInt(1);
                }
                try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM progression_dimensions WHERE ts >= " + interval)) {
                    if (rs.next()) counts.dimensions = rs.getInt(1);
                }
                try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM progression_trades WHERE ts >= " + interval)) {
                    if (rs.next()) counts.trades = rs.getInt(1);
                }
                try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM progression_fishing WHERE ts >= " + interval)) {
                    if (rs.next()) counts.fishing = rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            RATE_LIMITED.warn("progression_counts", "[DuckDB] Failed to query progression counts: {}", safeMessage(e));
        }
        return counts;
    }

    // Progression records
    public record BlockEventSummary(String playerName, String eventType, String blockId, int eventCount) {}
    public record XpEventSummary(String playerName, String eventType, int totalXp, int startLevel, int endLevel, int eventCount) {}
    public record AdvancementSummary(String playerName, String advancementId, String title, Instant timestamp) {}
    public record DimensionChangeSummary(String playerName, String fromDimension, String toDimension, int changeCount) {}
    public record TradeSummary(String playerName, String profession, String itemBought, int totalBought, String itemSold, int totalSold, int tradeCount) {}
    public record FishingSummary(String playerName, String itemId, int totalCount, int catchCount) {}

    public static class ProgressionTableCounts {
        public int blocks = 0;
        public int xp = 0;
        public int advancements = 0;
        public int dimensions = 0;
        public int trades = 0;
        public int fishing = 0;

        public boolean allNonZero() {
            return blocks > 0 && xp > 0 && advancements > 0 && dimensions > 0 && trades > 0 && fishing > 0;
        }

        @Override
        public String toString() {
            return "ProgressionTableCounts{blocks=%d, xp=%d, advancements=%d, dimensions=%d, trades=%d, fishing=%d}"
                .formatted(blocks, xp, advancements, dimensions, trades, fishing);
        }
    }

    // ============================================
    // ARENA ANALYTICS
    // ============================================

    /**
     * Get all distinct template IDs from arena builds.
     */
    public List<String> getArenaTemplateIds() {
        String sql = """
            SELECT DISTINCT template_id
            FROM arena_template_builds
            ORDER BY template_id
            """;

        List<String> results = new ArrayList<>();
        try {
            Connection conn = connectionManager.getConnection();
            try (var lease = connectionManager.lockStatements();
                 var stmt = createStatement(conn);
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    results.add(rs.getString("template_id"));
                }
            }
        } catch (SQLException e) {
            RATE_LIMITED.warn("arena_template_ids", "[DuckDB] Failed to get arena template IDs: {}", safeMessage(e));
        }
        return results;
    }

    /**
     * Get recent arena template builds.
     */
    public List<ArenaRecords.BuildRecord> getArenaRecentBuilds(String templateId, int limit) {
        return getArenaRecentBuilds(templateId, null, limit);
    }

    /**
     * Get recent arena template builds with optional version filter.
     */
    public List<ArenaRecords.BuildRecord> getArenaRecentBuilds(String templateId, @Nullable Integer templateVersion, int limit) {
        String sql;
        if (templateVersion != null) {
            sql = """
                SELECT arena_id, template_id, template_version, ts, actual_ms, success, error_message
                FROM arena_template_builds
                WHERE template_id = ? AND template_version = ?
                ORDER BY ts DESC
                LIMIT ?
                """;
        } else {
            sql = """
                SELECT arena_id, template_id, template_version, ts, actual_ms, success, error_message
                FROM arena_template_builds
                WHERE template_id = ?
                ORDER BY ts DESC
                LIMIT ?
                """;
        }

        List<ArenaRecords.BuildRecord> results = new ArrayList<>();
        try {
            Connection conn = connectionManager.getConnection();
            try (var lease = connectionManager.lockStatements();
                 var stmt = prepareStatement(conn, sql)) {
                stmt.setString(1, templateId);
                if (templateVersion != null) {
                    stmt.setInt(2, templateVersion);
                    stmt.setInt(3, limit);
                } else {
                    stmt.setInt(2, limit);
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String arenaIdStr = rs.getString("arena_id");
                        UUID arenaId = arenaIdStr != null ? UUID.fromString(arenaIdStr) : UUID.randomUUID();
                        int templateVersionValue = rs.getInt("template_version");
                        String templateVersionStr = rs.wasNull() ? null : String.valueOf(templateVersionValue);
                        Instant startedAt = rs.getTimestamp("ts").toInstant();
                        long actualMsValue = rs.getLong("actual_ms");
                        Long actualMs = rs.wasNull() ? null : actualMsValue;
                        boolean success = rs.getBoolean("success");
                        String status = success ? "success" : "failed";
                        String error = rs.getString("error_message");
                        Instant completedAt = actualMs != null && actualMs > 0
                            ? startedAt.plusMillis(actualMs)
                            : startedAt;

                        results.add(new ArenaRecords.BuildRecord(
                            arenaId,
                            rs.getString("template_id"),
                            templateVersionStr,
                            startedAt,
                            completedAt,
                            actualMs,
                            status,
                            error
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            RATE_LIMITED.warn("arena_recent_builds", "[DuckDB] Failed to get arena recent builds: {}", safeMessage(e));
        }
        return results;
    }

    /**
     * Get arena build performance samples.
     */
    public List<ArenaRecords.BuildPerformanceSample> getArenaBuildPerformance(String templateId, @Nullable Integer templateVersion, int limit) {
        String sql;
        if (templateVersion != null) {
            sql = """
                SELECT ts, baseline_mspt, avg_mspt, peak_mspt
                FROM arena_template_builds
                WHERE template_id = ? AND template_version = ? AND avg_mspt IS NOT NULL
                ORDER BY ts DESC
                LIMIT ?
                """;
        } else {
            sql = """
                SELECT ts, baseline_mspt, avg_mspt, peak_mspt
                FROM arena_template_builds
                WHERE template_id = ? AND avg_mspt IS NOT NULL
                ORDER BY ts DESC
                LIMIT ?
                """;
        }

        List<ArenaRecords.BuildPerformanceSample> results = new ArrayList<>();
        try {
            Connection conn = connectionManager.getConnection();
            try (var lease = connectionManager.lockStatements();
                 var stmt = prepareStatement(conn, sql)) {
                stmt.setString(1, templateId);
                if (templateVersion != null) {
                    stmt.setInt(2, templateVersion);
                    stmt.setInt(3, limit);
                } else {
                    stmt.setInt(2, limit);
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        results.add(new ArenaRecords.BuildPerformanceSample(
                            rs.getTimestamp("ts").toInstant(),
                            getNullableDouble(rs, "baseline_mspt"),
                            getNullableDouble(rs, "avg_mspt"),
                            getNullableDouble(rs, "peak_mspt")
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            RATE_LIMITED.warn("arena_build_perf", "[DuckDB] Failed to get arena build performance: {}", safeMessage(e));
        }
        return results;
    }

    /**
     * Get arena spatial event heatmap.
     */
    public int[][] getArenaHeatmap(String templateId, String eventType, int gridSize) {
        return getArenaHeatmap(templateId, null, eventType, gridSize);
    }

    /**
     * Get arena spatial event heatmap with optional version filter.
     */
    public int[][] getArenaHeatmap(String templateId, @Nullable Integer templateVersion, String eventType, int gridSize) {
        String sql;
        if (templateVersion != null) {
            sql = """
                SELECT grid_x, grid_z, COUNT(*) as count
                FROM arena_spatial_events
                WHERE template_id = ? AND template_version = ? AND event_type = ?
                GROUP BY grid_x, grid_z
                """;
        } else {
            sql = """
                SELECT grid_x, grid_z, COUNT(*) as count
                FROM arena_spatial_events
                WHERE template_id = ? AND event_type = ?
                GROUP BY grid_x, grid_z
                """;
        }

        int[][] grid = new int[gridSize][gridSize];

        try {
            Connection conn = connectionManager.getConnection();
            try (var lease = connectionManager.lockStatements();
                 var stmt = prepareStatement(conn, sql)) {
                stmt.setString(1, templateId);
                if (templateVersion != null) {
                    stmt.setInt(2, templateVersion);
                    stmt.setString(3, eventType);
                } else {
                    stmt.setString(2, eventType);
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        int x = rs.getInt("grid_x");
                        int z = rs.getInt("grid_z");
                        int count = rs.getInt("count");

                        if (x >= 0 && x < gridSize && z >= 0 && z < gridSize) {
                            grid[x][z] = count;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            RATE_LIMITED.warn("arena_heatmap", "[DuckDB] Failed to get arena heatmap: {}", safeMessage(e));
        }
        return grid;
    }

    /**
     * Get arena spatial event count.
     */
    public int getArenaSpatialEventCount(String templateId, String eventType) {
        return getArenaSpatialEventCount(templateId, null, eventType);
    }

    /**
     * Get arena spatial event count with optional version filter.
     */
    public int getArenaSpatialEventCount(String templateId, @Nullable Integer templateVersion, String eventType) {
        String sql;
        if (templateVersion != null) {
            sql = """
                SELECT COUNT(*) as total
                FROM arena_spatial_events
                WHERE template_id = ? AND template_version = ? AND event_type = ?
                """;
        } else {
            sql = """
                SELECT COUNT(*) as total
                FROM arena_spatial_events
                WHERE template_id = ? AND event_type = ?
                """;
        }

        try {
            Connection conn = connectionManager.getConnection();
            try (var lease = connectionManager.lockStatements();
                 var stmt = prepareStatement(conn, sql)) {
                stmt.setString(1, templateId);
                if (templateVersion != null) {
                    stmt.setInt(2, templateVersion);
                    stmt.setString(3, eventType);
                } else {
                    stmt.setString(2, eventType);
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("total");
                    }
                }
            }
        } catch (SQLException e) {
            RATE_LIMITED.warn("arena_spatial_count", "[DuckDB] Failed to get arena spatial event count: {}", safeMessage(e));
        }
        return 0;
    }

    /**
     * Find temporal gaps between arena builds.
     */
    public List<ArenaRecords.TemporalGap> findArenaBuildGaps(Instant since, Duration minGap, int limit) {
        String sql = """
            SELECT current_ts, prev_ts, gap_seconds
            FROM (
                SELECT
                    ts AS current_ts,
                    LAG(ts) OVER (ORDER BY ts) AS prev_ts,
                    EXTRACT(EPOCH FROM (ts - LAG(ts) OVER (ORDER BY ts))) AS gap_seconds
                FROM arena_template_builds
                WHERE ts >= ?
            ) sub
            WHERE prev_ts IS NOT NULL AND gap_seconds > ?
            ORDER BY gap_seconds DESC
            LIMIT ?
            """;

        List<ArenaRecords.TemporalGap> gaps = new ArrayList<>();
        try {
            Connection conn = connectionManager.getConnection();
            try (var lease = connectionManager.lockStatements();
                 var stmt = prepareStatement(conn, sql)) {
                stmt.setTimestamp(1, Timestamp.from(since));
                stmt.setDouble(2, Math.max(0.0, (double) minGap.toSeconds()));
                stmt.setInt(3, Math.max(1, limit));
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Timestamp currentTs = rs.getTimestamp("current_ts");
                        Timestamp prevTs = rs.getTimestamp("prev_ts");
                        double gapSeconds = rs.getDouble("gap_seconds");
                        if (currentTs != null && prevTs != null) {
                            gaps.add(new ArenaRecords.TemporalGap(
                                prevTs.toInstant(),
                                currentTs.toInstant(),
                                Duration.ofMillis((long) (gapSeconds * 1000))
                            ));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            RATE_LIMITED.warn("arena_build_gaps", "[DuckDB] Failed to find arena build gaps: {}", safeMessage(e));
        }
        return gaps;
    }

    /**
     * Count arena builds after a timestamp.
     */
    public long countArenaBuildsAfter(Instant timestamp) {
        String sql = "SELECT COUNT(*) AS count FROM arena_template_builds WHERE ts > ?";

        try {
            Connection conn = connectionManager.getConnection();
            try (var lease = connectionManager.lockStatements();
                 var stmt = prepareStatement(conn, sql)) {
                stmt.setTimestamp(1, Timestamp.from(timestamp));
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong("count");
                    }
                }
            }
        } catch (SQLException e) {
            RATE_LIMITED.warn("arena_build_count", "[DuckDB] Failed to count arena builds: {}", safeMessage(e));
        }
        return 0L;
    }

    /**
     * Get wave aggregates for arena template.
     */
    public List<ArenaRecords.WaveAggregate> getArenaWaveAggregates(String templateId, @Nullable Integer templateVersion,
                                                                    Instant from, Instant to) {
        Instant fromTs = from != null ? from : Instant.EPOCH;
        Instant toTs = to != null ? to : Instant.now();
        String sql;
        if (templateVersion != null) {
            sql = """
                SELECT wave_number,
                       SUM(CASE WHEN event_type = 'start' THEN 1 ELSE 0 END) as attempts,
                       SUM(CASE WHEN event_type = 'complete' THEN 1 ELSE 0 END) as completions,
                       AVG(CASE WHEN event_type = 'complete' THEN duration_ms END) as avg_duration_ms
                FROM endurance_waves
                WHERE template_id = ? AND template_version = ? AND ts >= ? AND ts <= ?
                GROUP BY wave_number
                ORDER BY wave_number
                """;
        } else {
            sql = """
                SELECT wave_number,
                       SUM(CASE WHEN event_type = 'start' THEN 1 ELSE 0 END) as attempts,
                       SUM(CASE WHEN event_type = 'complete' THEN 1 ELSE 0 END) as completions,
                       AVG(CASE WHEN event_type = 'complete' THEN duration_ms END) as avg_duration_ms
                FROM endurance_waves
                WHERE template_id = ? AND ts >= ? AND ts <= ?
                GROUP BY wave_number
                ORDER BY wave_number
                """;
        }

        List<ArenaRecords.WaveAggregate> results = new ArrayList<>();
        try {
            Connection conn = connectionManager.getConnection();
            try (var lease = connectionManager.lockStatements();
                 var stmt = prepareStatement(conn, sql)) {
                stmt.setString(1, templateId);
                if (templateVersion != null) {
                    stmt.setInt(2, templateVersion);
                    stmt.setTimestamp(3, Timestamp.from(fromTs));
                    stmt.setTimestamp(4, Timestamp.from(toTs));
                } else {
                    stmt.setTimestamp(2, Timestamp.from(fromTs));
                    stmt.setTimestamp(3, Timestamp.from(toTs));
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        results.add(new ArenaRecords.WaveAggregate(
                            rs.getInt("wave_number"),
                            rs.getInt("attempts"),
                            rs.getInt("completions"),
                            rs.getDouble("avg_duration_ms")
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            RATE_LIMITED.warn("arena_wave_aggregates", "[DuckDB] Failed to get arena wave aggregates: {}", safeMessage(e));
        }
        return results;
    }

    /**
     * Get average waves completed for arena template.
     */
    public double getArenaAverageWavesCompleted(String templateId, @Nullable Integer templateVersion,
                                                 Instant from, Instant to) {
        Instant fromTs = from != null ? from : Instant.EPOCH;
        Instant toTs = to != null ? to : Instant.now();
        String sql;
        if (templateVersion != null) {
            sql = """
                SELECT AVG(waves_completed) as avg_waves
                FROM endurance_sessions
                WHERE template_id = ? AND template_version = ? AND start_ts >= ? AND start_ts <= ?
                """;
        } else {
            sql = """
                SELECT AVG(waves_completed) as avg_waves
                FROM endurance_sessions
                WHERE template_id = ? AND start_ts >= ? AND start_ts <= ?
                """;
        }

        try {
            Connection conn = connectionManager.getConnection();
            try (var lease = connectionManager.lockStatements();
                 var stmt = prepareStatement(conn, sql)) {
                stmt.setString(1, templateId);
                if (templateVersion != null) {
                    stmt.setInt(2, templateVersion);
                    stmt.setTimestamp(3, Timestamp.from(fromTs));
                    stmt.setTimestamp(4, Timestamp.from(toTs));
                } else {
                    stmt.setTimestamp(2, Timestamp.from(fromTs));
                    stmt.setTimestamp(3, Timestamp.from(toTs));
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getDouble("avg_waves");
                    }
                }
            }
        } catch (SQLException e) {
            RATE_LIMITED.warn("arena_avg_waves", "[DuckDB] Failed to get arena average waves: {}", safeMessage(e));
        }
        return 0.0;
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null ? error.getClass().getSimpleName() : message;
    }

    // Helper method for nullable double
    @Nullable
    private Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    // ============================================
    // LVC (LAST VALUE CACHE) QUERIES - INSTANT
    // ============================================
    // These methods bypass DuckDB and read from in-memory cache.
    // Response time: <1ms, zero database access.

    /**
     * Get real-time DPS for a player (rolling 60s window).
     * Returns 0 if player not found or LVC disabled.
     *
     * @param playerId the player's UUID
     * @return current DPS (damage per second)
     */
    public double getPlayerCurrentDPS(UUID playerId) {
        var lvc = com.devmod.telemetry.duckdb.lvc.TelemetryLVC.INSTANCE;
        return lvc.getCurrentDPS(playerId);
    }

    /**
     * Get player session stats from LVC (instant, no database).
     *
     * @param playerId the player's UUID
     * @return session stats snapshot, or null if player not found
     */
    @Nullable
    public com.devmod.telemetry.duckdb.lvc.PlayerLVCEntry.PlayerLVCSnapshot getPlayerSessionStats(UUID playerId) {
        var lvc = com.devmod.telemetry.duckdb.lvc.TelemetryLVC.INSTANCE;
        return lvc.getPlayerSnapshot(playerId);
    }

    /**
     * Get top damage dealers from LVC (instant, no database).
     *
     * @param limit maximum number of players to return
     * @return list of player damage summaries, sorted by total damage
     */
    public List<com.devmod.telemetry.duckdb.lvc.TelemetryLVC.PlayerDamageSummary> getTopDamageDealers(int limit) {
        var lvc = com.devmod.telemetry.duckdb.lvc.TelemetryLVC.INSTANCE;
        return lvc.getTopDamageDealers(limit);
    }

    /**
     * Get top killers from LVC (instant, no database).
     *
     * @param limit maximum number of players to return
     * @return list of player kill summaries, sorted by kills
     */
    public List<com.devmod.telemetry.duckdb.lvc.TelemetryLVC.PlayerKillSummary> getTopKillers(int limit) {
        var lvc = com.devmod.telemetry.duckdb.lvc.TelemetryLVC.INSTANCE;
        return lvc.getTopKillers(limit);
    }

    /**
     * Get server-wide combat summary from LVC (instant, no database).
     *
     * @return aggregate combat stats for all online players
     */
    public com.devmod.telemetry.duckdb.lvc.TelemetryLVC.ServerCombatSummary getServerCombatSummary() {
        var lvc = com.devmod.telemetry.duckdb.lvc.TelemetryLVC.INSTANCE;
        return lvc.getServerCombatSummary();
    }

    /**
     * Get player kill count from LVC (instant, no database).
     *
     * @param playerId the player's UUID
     * @return total kills this session
     */
    public int getPlayerKillCount(UUID playerId) {
        var lvc = com.devmod.telemetry.duckdb.lvc.TelemetryLVC.INSTANCE;
        return lvc.getKillCount(playerId);
    }

    /**
     * Get player accuracy from LVC (instant, no database).
     *
     * @param playerId the player's UUID
     * @return accuracy as percentage (0.0 - 1.0)
     */
    public double getPlayerAccuracy(UUID playerId) {
        var lvc = com.devmod.telemetry.duckdb.lvc.TelemetryLVC.INSTANCE;
        return lvc.getAccuracy(playerId);
    }

    /**
     * Get player highest wave from LVC (instant, no database).
     *
     * @param playerId the player's UUID
     * @return highest wave reached this session
     */
    public int getPlayerHighestWave(UUID playerId) {
        var lvc = com.devmod.telemetry.duckdb.lvc.TelemetryLVC.INSTANCE;
        return lvc.getHighestWave(playerId);
    }
}
