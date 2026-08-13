package com.devmod.telemetry.dashboard;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.common.base.Splitter;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;

public class TelemetryAnalyticsHandlers {

    private final TelemetryDashboardServer server;
    private final Gson gson;
    private static final Splitter AMPERSAND_SPLITTER = Splitter.on('&');
    private static final String ARENA_FILTER_CLAUSE = """
        AND (? IS NULL OR template_id = ?)
        AND (? IS NULL OR template_version = ?)
        AND (? IS NULL OR policy_id = ?)
        AND (? IS NULL OR policy_version = ?)
        """;
    private static final String PLAYER_FILTER_CLAUSE = "AND (? IS NULL OR target_name = ?)";
    private static final String ATTACKER_FILTER_CLAUSE = "AND (? IS NULL OR attacker_name = ?)";

    /**
     * Row cap for bucketed charts: 720 buckets is 12 hours at minute resolution or 30 days at
     * hour resolution, past which a line chart is unreadable and the payload is pure waste.
     */
    private static final int MAX_TIMELINE_BUCKETS = 720;

    private static String indentClause(String clause, String indent) {
        String trimmed = clause.stripIndent().trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return indent + trimmed.replace("\n", "\n" + indent);
    }

    /**
     * Caps a bucketed query at its newest buckets, re-sorted ascending for charting.
     * The cap must be applied to a descending order inside the subquery: a LIMIT on the
     * ascending query would drop the most recent data instead of the oldest.
     *
     * @param bucketedSql The grouped query, without its own ORDER BY / LIMIT
     * @param bucketColumn The bucket column to order on
     * @param limit Maximum buckets to return
     */
    private static String newestBuckets(String bucketedSql, String bucketColumn, int limit) {
        return """
            SELECT * FROM (
            %s
            ORDER BY %s DESC
            LIMIT %d
            ) AS capped
            ORDER BY %s
            """.formatted(bucketedSql, bucketColumn, limit, bucketColumn);
    }

    private static String newestBuckets(String bucketedSql, String bucketColumn) {
        return newestBuckets(bucketedSql, bucketColumn, MAX_TIMELINE_BUCKETS);
    }

    private record ArenaFilterParams(@Nullable String templateId,
                                     @Nullable Integer templateVersion,
                                     @Nullable String policyId,
                                     @Nullable Integer policyVersion) {}

    public TelemetryAnalyticsHandlers(TelemetryDashboardServer server, Gson gson) {
        this.server = server;
        this.gson = gson;
    }

    // ========== Basic Analytics ==========

    public String handleAnalyticsOverview(HttpExchange exchange) {
        var since = server.getRangeStart(exchange);
        Map<String, String> params = parseQueryParams(exchange);
        ArenaFilterParams filter = parseArenaFilterParams(params);
        Map<String, Object> overview = new HashMap<>();

        // Total hits
        String hitsSql = """
            SELECT COUNT(*) as total, SUM(damage) as total_damage
            FROM combat_hits
            WHERE ts >= ?
            """ + ARENA_FILTER_CLAUSE;
        var hitsResult = server.executeQuery(hitsSql, arenaParamsWithSince(since, filter));
        if (!hitsResult.isEmpty()) {
            overview.put("totalHits", hitsResult.get(0).get("total"));
            overview.put("totalDamage", hitsResult.get(0).get("total_damage"));
        }

        // Total deaths
        String deathsSql = """
            SELECT COUNT(*) as total
            FROM combat_deaths
            WHERE ts >= ?
            """ + ARENA_FILTER_CLAUSE;
        var deathsResult = server.executeQuery(deathsSql, arenaParamsWithSince(since, filter));
        if (!deathsResult.isEmpty()) {
            overview.put("totalDeaths", deathsResult.get(0).get("total"));
        }

        // Accuracy
        String accuracySql = """
            SELECT
                COUNT(*) as total,
                SUM(CASE WHEN is_miss THEN 1 ELSE 0 END) as misses
            FROM combat_hits
            WHERE ts >= ?
            """ + ARENA_FILTER_CLAUSE;
        var accuracyResult = server.executeQuery(accuracySql, arenaParamsWithSince(since, filter));
        if (!accuracyResult.isEmpty()) {
            long total = ((Number) accuracyResult.get(0).getOrDefault("total", 0L)).longValue();
            long misses = ((Number) accuracyResult.get(0).getOrDefault("misses", 0L)).longValue();
            double accuracy = total > 0 ? 100.0 * (total - misses) / total : 0;
            overview.put("accuracy", Math.round(accuracy * 10) / 10.0);
        }

        // Mobs killed
        String mobsSql = "SELECT COUNT(*) as total FROM economy_mob_kills WHERE ts >= ?";
        var mobsResult = server.executeQuery(mobsSql, List.of(server.paramTimestamp(since)));
        if (!mobsResult.isEmpty()) {
            overview.put("mobsKilled", mobsResult.get(0).get("total"));
        }

        // DB size
        var sizeResult = server.executeQuery("SELECT SUM(estimated_size) / 1024 as size_kb FROM duckdb_tables()");
        if (!sizeResult.isEmpty() && sizeResult.get(0).get("size_kb") != null) {
            overview.put("dbSizeKb", sizeResult.get(0).get("size_kb"));
        }

        return gson.toJson(overview);
    }

    public String handleHitsTimeline(HttpExchange exchange) {
        String interval = server.getTimeInterval(exchange);
        Map<String, String> params = parseQueryParams(exchange);
        String arenaFilter = buildArenaFilter(
            params.get("templateId"),
            params.get("templateVersion"),
            params.get("policyId"),
            params.get("policyVersion")
        );
        String bucket = interval.contains("hour") ? "minute" : "hour";
        String sql = newestBuckets("""
            SELECT
                DATE_TRUNC('%s', ts) as time_bucket,
                COUNT(*) as hits,
                ROUND(SUM(damage), 1) as total_damage
            FROM combat_hits
            WHERE ts >= NOW() - INTERVAL '%s'
            %s
            GROUP BY time_bucket
            """.formatted(bucket, interval, arenaFilter), "time_bucket");
        return gson.toJson(server.executeQuery(sql));
    }

    public String handleDamageByBodypart(HttpExchange exchange) {
        String interval = server.getTimeInterval(exchange);
        Map<String, String> params = parseQueryParams(exchange);
        String arenaFilter = buildArenaFilter(
            params.get("templateId"),
            params.get("templateVersion"),
            params.get("policyId"),
            params.get("policyVersion")
        );
        String sql = """
            SELECT
                COALESCE(body_part, 'UNKNOWN') as body_part,
                COUNT(*) as hits,
                ROUND(SUM(damage), 1) as total_damage,
                ROUND(AVG(damage), 2) as avg_damage
            FROM combat_hits
            WHERE ts >= NOW() - INTERVAL '%s'
              AND body_part IS NOT NULL
              %s
            GROUP BY body_part
            ORDER BY total_damage DESC
            LIMIT 15
            """.formatted(interval, arenaFilter);
        return gson.toJson(server.executeQuery(sql));
    }

    public String handleDamageByType(HttpExchange exchange) {
        String interval = server.getTimeInterval(exchange);
        Map<String, String> params = parseQueryParams(exchange);
        String arenaFilter = buildArenaFilter(
            params.get("templateId"),
            params.get("templateVersion"),
            params.get("policyId"),
            params.get("policyVersion")
        );
        String sql = """
            SELECT
                COALESCE(damage_type, 'unknown') as damage_type,
                COUNT(*) as hits,
                ROUND(SUM(damage), 1) as total_damage
            FROM combat_hits
            WHERE ts >= NOW() - INTERVAL '%s'
            %s
            GROUP BY damage_type
            ORDER BY total_damage DESC
            LIMIT 15
            """.formatted(interval, arenaFilter);
        return gson.toJson(server.executeQuery(sql));
    }

    public String handleWeaponAnalytics(HttpExchange exchange) {
        String interval = server.getTimeInterval(exchange);
        Map<String, String> params = parseQueryParams(exchange);
        String arenaFilter = buildArenaFilter(
            params.get("templateId"),
            params.get("templateVersion"),
            params.get("policyId"),
            params.get("policyVersion")
        );
        String sql = """
            SELECT
                COALESCE(NULLIF(JSON_EXTRACT_STRING(attacker_state, '$.mainHand'), ''), 'fist') as weapon,
                COUNT(*) as hits,
                ROUND(SUM(damage), 1) as total_damage,
                ROUND(AVG(damage), 2) as avg_damage,
                SUM(CASE WHEN is_miss THEN 1 ELSE 0 END) as misses,
                ROUND(100.0 * (COUNT(*) - SUM(CASE WHEN is_miss THEN 1 ELSE 0 END)) / NULLIF(COUNT(*), 0), 1) as accuracy
            FROM combat_hits
            WHERE ts >= NOW() - INTERVAL '%s'
              AND attacker_name IS NOT NULL
              %s
            GROUP BY weapon
            ORDER BY total_damage DESC
            LIMIT 15
            """.formatted(interval, arenaFilter);
        return gson.toJson(server.executeQuery(sql));
    }

    public String handleMobKillsAnalytics(HttpExchange exchange) {
        String interval = server.getTimeInterval(exchange);
        String sql = """
            SELECT
                REPLACE(mob_type, 'entity.', '') as mob_type,
                COUNT(*) as kills,
                SUM(CASE WHEN had_loot THEN 1 ELSE 0 END) as with_loot,
                ROUND(100.0 * SUM(CASE WHEN had_loot THEN 1 ELSE 0 END) / NULLIF(COUNT(*), 0), 1) as loot_rate
            FROM economy_mob_kills
            WHERE ts >= NOW() - INTERVAL '%s'
            GROUP BY mob_type
            ORDER BY kills DESC
            LIMIT 15
            """.formatted(interval);
        return gson.toJson(server.executeQuery(sql));
    }

    public String handleTTKAnalytics(HttpExchange exchange) {
        String interval = server.getTimeInterval(exchange);
        Map<String, String> params = parseQueryParams(exchange);
        String arenaFilter = buildArenaFilter(
            params.get("templateId"),
            params.get("templateVersion"),
            params.get("policyId"),
            params.get("policyVersion")
        );
        String sql = """
            SELECT
                REPLACE(target_type, 'entity.', '') as mob_type,
                COUNT(*) as deaths,
                ROUND(AVG(ttk_spawn_ms) / 1000.0, 2) as avg_ttk_seconds,
                ROUND(MIN(ttk_spawn_ms) / 1000.0, 2) as min_ttk_seconds,
                ROUND(MAX(ttk_spawn_ms) / 1000.0, 2) as max_ttk_seconds
            FROM combat_deaths
            WHERE ts >= NOW() - INTERVAL '%s'
              AND ttk_spawn_ms IS NOT NULL
              AND ttk_spawn_ms > 0
              %s
            GROUP BY target_type
            ORDER BY deaths DESC
            LIMIT 15
            """.formatted(interval, arenaFilter);
        return gson.toJson(server.executeQuery(sql));
    }

    public String handleAccuracyTimeline(HttpExchange exchange) {
        String interval = server.getTimeInterval(exchange);
        Map<String, String> params = parseQueryParams(exchange);
        String arenaFilter = buildArenaFilter(
            params.get("templateId"),
            params.get("templateVersion"),
            params.get("policyId"),
            params.get("policyVersion")
        );
        String bucket = interval.contains("hour") ? "minute" : "hour";
        String sql = newestBuckets("""
            SELECT
                DATE_TRUNC('%s', ts) as time_bucket,
                COUNT(*) as total,
                SUM(CASE WHEN is_miss THEN 1 ELSE 0 END) as misses,
                ROUND(100.0 * (COUNT(*) - SUM(CASE WHEN is_miss THEN 1 ELSE 0 END)) / NULLIF(COUNT(*), 0), 1) as accuracy
            FROM combat_hits
            WHERE ts >= NOW() - INTERVAL '%s'
            %s
            GROUP BY time_bucket
            """.formatted(bucket, interval, arenaFilter), "time_bucket");
        return gson.toJson(server.executeQuery(sql));
    }

    public String handleEnduranceAnalytics(HttpExchange exchange) {
        var since = server.getRangeStart(exchange);
        Map<String, String> params = parseQueryParams(exchange);
        ArenaFilterParams filter = parseArenaFilterParams(params);
        Map<String, Object> stats = new HashMap<>();

        // Session stats
        String sessionSql = """
            SELECT
                COUNT(*) as total_sessions,
                SUM(CASE WHEN outcome = 'victory' THEN 1 ELSE 0 END) as wins,
                SUM(CASE WHEN outcome = 'defeat' THEN 1 ELSE 0 END) as losses,
                ROUND(AVG(waves_completed), 1) as avg_waves,
                MAX(waves_completed) as best_wave
            FROM endurance_sessions
            WHERE start_ts >= ?
            """ + ARENA_FILTER_CLAUSE;
        var sessionResult = server.executeQuery(sessionSql, arenaParamsWithSince(since, filter));
        if (!sessionResult.isEmpty()) {
            stats.putAll(sessionResult.get(0));
            long total = ((Number) sessionResult.get(0).getOrDefault("total_sessions", 0L)).longValue();
            long wins = ((Number) sessionResult.get(0).getOrDefault("wins", 0L)).longValue();
            stats.put("winRate", total > 0 ? Math.round(1000.0 * wins / total) / 10.0 : 0);
        }

        // Outcomes by day
        String outcomesSql = newestBuckets("""
            SELECT
                DATE_TRUNC('day', start_ts) as day,
                outcome,
                COUNT(*) as count
            FROM endurance_sessions
            WHERE start_ts >= ?
            """ + ARENA_FILTER_CLAUSE + """
            GROUP BY day, outcome
            """, "day");
        var outcomesResult = server.executeQuery(outcomesSql, arenaParamsWithSince(since, filter));
        stats.put("outcomes", outcomesResult);

        // Perk popularity
        String perksSql = """
            SELECT
                perk_name,
                COUNT(*) as picks
            FROM endurance_perks
            WHERE event_type = 'selected'
              AND ts >= ?
            """ + ARENA_FILTER_CLAUSE + """
            GROUP BY perk_name
            ORDER BY picks DESC
            LIMIT 10
            """;
        var perksResult = server.executeQuery(perksSql, arenaParamsWithSince(since, filter));
        stats.put("perks", perksResult);

        return gson.toJson(stats);
    }

    private Map<String, String> parseQueryParams(HttpExchange exchange) {
        Map<String, String> params = new HashMap<>();
        String query = exchange.getRequestURI().getQuery();
        if (query == null || query.isBlank()) {
            return params;
        }
        for (String pair : AMPERSAND_SPLITTER.split(query)) {
            if (pair.isEmpty()) continue;
            int idx = pair.indexOf('=');
            if (idx >= 0) {
                String key = pair.substring(0, idx);
                String value = pair.substring(idx + 1);
                params.put(key, value.replace("+", " "));
            } else {
                params.put(pair, "");
            }
        }
        return params;
    }

    private ArenaFilterParams parseArenaFilterParams(Map<String, String> params) {
        if (params == null) {
            return new ArenaFilterParams(null, null, null, null);
        }
        String templateId = params.get("templateId");
        Integer templateVersion = parseNullableInt(params.get("templateVersion"));
        String policyId = params.get("policyId");
        Integer policyVersion = parseNullableInt(params.get("policyVersion"));
        return new ArenaFilterParams(templateId, templateVersion, policyId, policyVersion);
    }

    @Nullable
    private Integer parseNullableInt(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!value.matches("^-?\\d+$")) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<TelemetryDashboardServer.SqlParam> arenaFilterParams(ArenaFilterParams filter) {
        List<TelemetryDashboardServer.SqlParam> params = new ArrayList<>(8);
        params.add(server.paramString(filter.templateId()));
        params.add(server.paramString(filter.templateId()));
        params.add(server.paramInt(filter.templateVersion()));
        params.add(server.paramInt(filter.templateVersion()));
        params.add(server.paramString(filter.policyId()));
        params.add(server.paramString(filter.policyId()));
        params.add(server.paramInt(filter.policyVersion()));
        params.add(server.paramInt(filter.policyVersion()));
        return params;
    }

    private List<TelemetryDashboardServer.SqlParam> arenaParamsWithSince(java.sql.Timestamp since,
            ArenaFilterParams filter) {
        List<TelemetryDashboardServer.SqlParam> params = new ArrayList<>(1 + 8);
        params.add(server.paramTimestamp(since));
        params.addAll(arenaFilterParams(filter));
        return params;
    }

    private String buildArenaFilter(@Nullable String templateId, @Nullable String templateVersion,
                                    @Nullable String policyId, @Nullable String policyVersion) {
        StringBuilder filter = new StringBuilder();
        if (templateId != null && !templateId.isBlank()) {
            filter.append(" AND template_id = '").append(escapeSql(templateId)).append("'");
        }
        if (templateVersion != null && templateVersion.matches("^-?\\d+$")) {
            filter.append(" AND template_version = ").append(templateVersion);
        }
        if (policyId != null && !policyId.isBlank()) {
            filter.append(" AND policy_id = '").append(escapeSql(policyId)).append("'");
        }
        if (policyVersion != null && policyVersion.matches("^-?\\d+$")) {
            filter.append(" AND policy_version = ").append(policyVersion);
        }
        return filter.toString();
    }

    private String escapeSql(String value) {
        return value.replace("'", "''");
    }

    public String handleDungeonAnalytics(HttpExchange exchange) {
        var since = server.getRangeStart(exchange);
        Map<String, Object> stats = new HashMap<>();

        // Overall stats
        String overallSql = """
            SELECT
                COUNT(*) as total_runs,
                SUM(CASE WHEN outcome = 'completed' THEN 1 ELSE 0 END) as completed,
                ROUND(AVG(duration_ms) / 1000.0 / 60.0, 1) as avg_duration_min,
                ROUND(AVG(deaths), 1) as avg_deaths,
                ROUND(AVG(kills), 1) as avg_kills
            FROM dungeon_runs
            WHERE start_ts >= ?
            """;
        var overallResult = server.executeQuery(overallSql,
            List.of(server.paramTimestamp(since)));
        if (!overallResult.isEmpty()) {
            stats.putAll(overallResult.get(0));
            long total = ((Number) overallResult.get(0).getOrDefault("total_runs", 0L)).longValue();
            long completed = ((Number) overallResult.get(0).getOrDefault("completed", 0L)).longValue();
            stats.put("completionRate", total > 0 ? Math.round(1000.0 * completed / total) / 10.0 : 0);
        }

        // By dungeon
        String byDungeonSql = """
            SELECT
                dungeon_id,
                COUNT(*) as runs,
                SUM(CASE WHEN outcome = 'completed' THEN 1 ELSE 0 END) as completed,
                ROUND(100.0 * SUM(CASE WHEN outcome = 'completed' THEN 1 ELSE 0 END) / NULLIF(COUNT(*), 0), 1) as completion_rate
            FROM dungeon_runs
            WHERE start_ts >= ?
            GROUP BY dungeon_id
            ORDER BY runs DESC
            LIMIT 15
            """;
        var byDungeonResult = server.executeQuery(byDungeonSql,
            List.of(server.paramTimestamp(since)));
        stats.put("byDungeon", byDungeonResult);

        return gson.toJson(stats);
    }

    public String handleRoomAnalytics(HttpExchange exchange) {
        var since = server.getRangeStart(exchange);
        Map<String, String> params = parseQueryParams(exchange);
        ArenaFilterParams filter = parseArenaFilterParams(params);
        Map<String, Object> stats = new HashMap<>();

        // Room visits
        String visitsSql = """
            SELECT
                room,
                COUNT(*) as visits
            FROM spatial_room_transitions
            WHERE ts >= ?
            GROUP BY room
            ORDER BY visits DESC
            LIMIT 15
            """;
        var visitsResult = server.executeQuery(visitsSql, List.of(server.paramTimestamp(since)));
        stats.put("visits", visitsResult);

        // Deaths by room
        String deathsSql = """
            SELECT
                COALESCE(room, 'unknown') as room,
                COUNT(*) as deaths
            FROM combat_deaths
            WHERE ts >= ?
            """ + ARENA_FILTER_CLAUSE + """
            GROUP BY room
            ORDER BY deaths DESC
            LIMIT 15
            """;
        var deathsResult = server.executeQuery(deathsSql, arenaParamsWithSince(since, filter));
        stats.put("deaths", deathsResult);

        return gson.toJson(stats);
    }

    public String handleLootRatesAnalytics(HttpExchange exchange) {
        String interval = server.getTimeInterval(exchange);
        String sql = """
            SELECT
                REPLACE(mob_type, 'entity.', '') as mob_type,
                COUNT(*) as kills,
                ROUND(100.0 * SUM(CASE WHEN had_loot THEN 1 ELSE 0 END) / NULLIF(COUNT(*), 0), 1) as loot_rate
            FROM economy_mob_kills
            WHERE ts >= NOW() - INTERVAL '%s'
            GROUP BY mob_type
            HAVING COUNT(*) >= 5
            ORDER BY loot_rate DESC
            LIMIT 15
            """.formatted(interval);
        return gson.toJson(server.executeQuery(sql));
    }

    // ========== Advanced Analytics v2 ==========

    /**
     * Real DPS timeline - damage per second over time buckets
     */
    public String handleDpsTimeline(HttpExchange exchange) {
        String interval = server.getTimeInterval(exchange);
        var since = server.getRangeStart(exchange);
        Map<String, String> params = server.parseQueryParams(exchange);
        String player = params.get("player");
        ArenaFilterParams filter = parseArenaFilterParams(params);

        // Calculate DPS per time bucket (damage / seconds in bucket)
        String bucket = interval.contains("hour") ? "minute" : "hour";
        int bucketSeconds = bucket.equals("minute") ? 60 : 3600;

        String sql = newestBuckets(String.join("\n",
            "SELECT",
            "    DATE_TRUNC(?, ts) as time_bucket,",
            "    ROUND(SUM(damage) / ?, 2) as dps,",
            "    ROUND(SUM(damage), 1) as total_damage,",
            "    COUNT(*) as hits,",
            "    ROUND(AVG(damage), 2) as avg_hit",
            "FROM combat_hits",
            "WHERE ts >= ?",
            "  AND attacker_name IS NOT NULL",
            "  AND NOT is_miss",
            indentClause(ATTACKER_FILTER_CLAUSE, "  "),
            indentClause(ARENA_FILTER_CLAUSE, "  "),
            "GROUP BY time_bucket"
        ), "time_bucket");
        List<TelemetryDashboardServer.SqlParam> paramsList = new ArrayList<>();
        paramsList.add(server.paramString(bucket));
        paramsList.add(server.paramInt(bucketSeconds));
        paramsList.add(server.paramTimestamp(since));
        paramsList.add(server.paramString(player));
        paramsList.add(server.paramString(player));
        paramsList.addAll(arenaFilterParams(filter));
        return gson.toJson(server.executeQuery(sql, paramsList));
    }

    /**
     * Detailed stats for a specific player
     */
    public String handlePlayerStats(HttpExchange exchange) {
        String interval = server.getTimeInterval(exchange);
        Map<String, String> params = server.parseQueryParams(exchange);
        String player = params.get("player");
        String arenaFilter = buildArenaFilter(
            params.get("templateId"),
            params.get("templateVersion"),
            params.get("policyId"),
            params.get("policyVersion")
        );

        if (player == null || player.isBlank()) {
            return gson.toJson(Map.of("error", "Missing 'player' parameter"));
        }

        Map<String, Object> stats = new HashMap<>();

        // Combat stats as attacker
        var attackerStats = server.executeQuery("""
            SELECT
                COUNT(*) as total_hits,
                SUM(CASE WHEN NOT is_miss THEN 1 ELSE 0 END) as hits_landed,
                ROUND(SUM(damage), 1) as total_damage_dealt,
                ROUND(AVG(CASE WHEN NOT is_miss THEN damage END), 2) as avg_damage,
                ROUND(MAX(damage), 1) as max_hit,
                ROUND(100.0 * SUM(CASE WHEN NOT is_miss THEN 1 ELSE 0 END) / NULLIF(COUNT(*), 0), 1) as accuracy,
                COUNT(DISTINCT target_name) as unique_targets
            FROM combat_hits
            WHERE attacker_name = '%s'
              AND ts >= NOW() - INTERVAL '%s'
              %s
            """.formatted(player, interval, arenaFilter));
        if (!attackerStats.isEmpty()) {
            stats.put("combat", attackerStats.get(0));
        }

        // Damage taken
        var damageTaken = server.executeQuery("""
            SELECT
                COUNT(*) as times_hit,
                ROUND(SUM(damage), 1) as total_damage_taken,
                ROUND(AVG(damage), 2) as avg_damage_taken,
                COUNT(DISTINCT attacker_name) as unique_attackers
            FROM combat_hits
            WHERE target_name = '%s'
              AND ts >= NOW() - INTERVAL '%s'
              %s
            """.formatted(player, interval, arenaFilter));
        if (!damageTaken.isEmpty()) {
            stats.put("damageTaken", damageTaken.get(0));
        }

        // Deaths
        var deaths = server.executeQuery("""
            SELECT
                COUNT(*) as total_deaths,
                COUNT(DISTINCT cause) as death_types
            FROM combat_deaths
            WHERE target_name = '%s'
              AND ts >= NOW() - INTERVAL '%s'
              %s
            """.formatted(player, interval, arenaFilter));
        if (!deaths.isEmpty()) {
            stats.put("deaths", deaths.get(0));
        }

        // Kills
        var kills = server.executeQuery("""
            SELECT COUNT(*) as total_kills
            FROM economy_mob_kills
            WHERE player_name = '%s'
              AND ts >= NOW() - INTERVAL '%s'
            """.formatted(player, interval));
        if (!kills.isEmpty()) {
            stats.put("kills", kills.get(0));
        }

        // Weapon breakdown
        var weapons = server.executeQuery("""
            SELECT
                COALESCE(NULLIF(JSON_EXTRACT_STRING(attacker_state, '$.mainHand'), ''), 'fist') as weapon,
                COUNT(*) as hits,
                ROUND(SUM(damage), 1) as damage,
                ROUND(AVG(damage), 2) as avg_damage
            FROM combat_hits
            WHERE attacker_name = '%s'
              AND ts >= NOW() - INTERVAL '%s'
              AND NOT is_miss
              %s
            GROUP BY weapon
            ORDER BY damage DESC
            LIMIT 10
            """.formatted(player, interval, arenaFilter));
        stats.put("weapons", weapons);

        // DPS over time
        var dpsTimeline = server.executeQuery(newestBuckets("""
            SELECT
                DATE_TRUNC('minute', ts) as time_bucket,
                ROUND(SUM(damage) / 60.0, 2) as dps
            FROM combat_hits
            WHERE attacker_name = '%s'
              AND ts >= NOW() - INTERVAL '%s'
              AND NOT is_miss
              %s
            GROUP BY time_bucket
            """.formatted(player, interval, arenaFilter), "time_bucket", 60));
        stats.put("dpsTimeline", dpsTimeline);

        return gson.toJson(stats);
    }

    /**
     * Compare multiple players side by side
     */
    public String handlePlayerComparison(HttpExchange exchange) {
        String interval = server.getTimeInterval(exchange);
        Map<String, String> params = server.parseQueryParams(exchange);
        String arenaFilter = buildArenaFilter(
            params.get("templateId"),
            params.get("templateVersion"),
            params.get("policyId"),
            params.get("policyVersion")
        );
        String sql = """
            SELECT
                attacker_name as player,
                COUNT(*) as total_hits,
                ROUND(SUM(damage), 1) as total_damage,
                ROUND(AVG(damage), 2) as avg_damage,
                ROUND(MAX(damage), 1) as max_hit,
                ROUND(100.0 * SUM(CASE WHEN NOT is_miss THEN 1 ELSE 0 END) / NULLIF(COUNT(*), 0), 1) as accuracy,
                ROUND(SUM(damage) / GREATEST(1, EXTRACT(EPOCH FROM MAX(ts) - MIN(ts))), 2) as session_dps
            FROM combat_hits
            WHERE ts >= NOW() - INTERVAL '%s'
              AND attacker_name IS NOT NULL
              %s
            GROUP BY attacker_name
            HAVING COUNT(*) >= 10
            ORDER BY total_damage DESC
            LIMIT 20
            """.formatted(interval, arenaFilter);
        return gson.toJson(server.executeQuery(sql));
    }

    /**
     * Trend analysis - compare current period vs previous period
     */
    public String handleTrends(HttpExchange exchange) {
        var since = server.getRangeStart(exchange);
        Map<String, String> params = server.parseQueryParams(exchange);
        ArenaFilterParams filter = parseArenaFilterParams(params);
        Map<String, Object> trends = new HashMap<>();

        // Current period stats
        String currentSql = """
            SELECT
                COUNT(*) as hits,
                ROUND(SUM(damage), 1) as damage,
                COUNT(DISTINCT attacker_name) as players,
                ROUND(AVG(damage), 2) as avg_damage
            FROM combat_hits
            WHERE ts >= ?
            """ + ARENA_FILTER_CLAUSE;
        var current = server.executeQuery(currentSql, arenaParamsWithSince(since, filter));

        // Previous period stats (same duration, before current)
        Duration window = Duration.between(since.toInstant(), Instant.now());
        java.sql.Timestamp previousStart = java.sql.Timestamp.from(since.toInstant().minus(window));
        java.sql.Timestamp previousEnd = since;

        String previousSql = """
            SELECT
                COUNT(*) as hits,
                ROUND(SUM(damage), 1) as damage,
                COUNT(DISTINCT attacker_name) as players,
                ROUND(AVG(damage), 2) as avg_damage
            FROM combat_hits
            WHERE ts >= ?
              AND ts < ?
            """ + ARENA_FILTER_CLAUSE;
        List<TelemetryDashboardServer.SqlParam> previousParams = new ArrayList<>();
        previousParams.add(server.paramTimestamp(previousStart));
        previousParams.add(server.paramTimestamp(previousEnd));
        previousParams.addAll(arenaFilterParams(filter));
        var previous = server.executeQuery(previousSql, previousParams);

        if (!current.isEmpty()) trends.put("current", current.get(0));
        if (!previous.isEmpty()) trends.put("previous", previous.get(0));

        // Calculate deltas
        if (!current.isEmpty() && !previous.isEmpty()) {
            Map<String, Object> deltas = new HashMap<>();
            Map<String, Object> curr = current.get(0);
            Map<String, Object> prev = previous.get(0);

            deltas.put("hitsChange", calculateDelta(curr.get("hits"), prev.get("hits")));
            deltas.put("damageChange", calculateDelta(curr.get("damage"), prev.get("damage")));
            deltas.put("playersChange", calculateDelta(curr.get("players"), prev.get("players")));
            deltas.put("avgDamageChange", calculateDelta(curr.get("avg_damage"), prev.get("avg_damage")));

            trends.put("deltas", deltas);
        }

        // Deaths trends
        String deathsCurrentSql = """
            SELECT COUNT(*) as deaths
            FROM combat_deaths
            WHERE ts >= ?
            """ + ARENA_FILTER_CLAUSE;
        var currentDeaths = server.executeQuery(deathsCurrentSql, arenaParamsWithSince(since, filter));
        String deathsPreviousSql = """
            SELECT COUNT(*) as deaths
            FROM combat_deaths
            WHERE ts >= ?
              AND ts < ?
            """ + ARENA_FILTER_CLAUSE;
        List<TelemetryDashboardServer.SqlParam> deathsPreviousParams = new ArrayList<>();
        deathsPreviousParams.add(server.paramTimestamp(previousStart));
        deathsPreviousParams.add(server.paramTimestamp(previousEnd));
        deathsPreviousParams.addAll(arenaFilterParams(filter));
        var previousDeaths = server.executeQuery(deathsPreviousSql, deathsPreviousParams);

        if (!currentDeaths.isEmpty()) trends.put("currentDeaths", currentDeaths.get(0).get("deaths"));
        if (!previousDeaths.isEmpty()) trends.put("previousDeaths", previousDeaths.get(0).get("deaths"));
        if (!currentDeaths.isEmpty() && !previousDeaths.isEmpty()) {
            trends.put("deathsChange", calculateDelta(currentDeaths.get(0).get("deaths"), previousDeaths.get(0).get("deaths")));
        }

        // Kills trends
        String killsCurrentSql = "SELECT COUNT(*) as kills FROM economy_mob_kills WHERE ts >= ?";
        var currentKills = server.executeQuery(killsCurrentSql,
            List.of(server.paramTimestamp(since)));
        String killsPreviousSql = "SELECT COUNT(*) as kills FROM economy_mob_kills WHERE ts >= ? AND ts < ?";
        var previousKills = server.executeQuery(killsPreviousSql,
            List.of(server.paramTimestamp(previousStart), server.paramTimestamp(previousEnd)));

        if (!currentKills.isEmpty()) trends.put("currentKills", currentKills.get(0).get("kills"));
        if (!previousKills.isEmpty()) trends.put("previousKills", previousKills.get(0).get("kills"));
        if (!currentKills.isEmpty() && !previousKills.isEmpty()) {
            trends.put("killsChange", calculateDelta(currentKills.get(0).get("kills"), previousKills.get(0).get("kills")));
        }

        return gson.toJson(trends);
    }

    private double calculateDelta(@Nullable Object current, @Nullable Object previous) {
        if (current == null || previous == null) return 0;
        double curr = ((Number) current).doubleValue();
        double prev = ((Number) previous).doubleValue();
        if (prev == 0) return curr > 0 ? 100 : 0;
        return Math.round(1000.0 * (curr - prev) / prev) / 10.0;
    }

    /**
     * Server performance metrics
     */
    public String handlePerformanceAnalytics(HttpExchange exchange) {
        String interval = server.getTimeInterval(exchange);
        Map<String, Object> perf = new HashMap<>();

        // TPS timeline
        var tpsTimeline = server.executeQuery(newestBuckets("""
            SELECT
                DATE_TRUNC('minute', ts) as time_bucket,
                ROUND(AVG(tps), 2) as avg_tps,
                ROUND(MIN(tps), 2) as min_tps,
                ROUND(MAX(tps), 2) as max_tps
            FROM performance_samples
            WHERE ts >= NOW() - INTERVAL '%s'
            GROUP BY time_bucket
            """.formatted(interval), "time_bucket"));
        perf.put("tpsTimeline", tpsTimeline);

        // Memory timeline
        var memTimeline = server.executeQuery(newestBuckets("""
            SELECT
                DATE_TRUNC('minute', ts) as time_bucket,
                ROUND(AVG(memory_used_mb), 1) as avg_memory_mb,
                ROUND(MAX(memory_used_mb), 1) as max_memory_mb
            FROM performance_samples
            WHERE ts >= NOW() - INTERVAL '%s'
            GROUP BY time_bucket
            """.formatted(interval), "time_bucket"));
        perf.put("memoryTimeline", memTimeline);

        // Entity counts
        var entityTimeline = server.executeQuery(newestBuckets("""
            SELECT
                DATE_TRUNC('minute', ts) as time_bucket,
                ROUND(AVG(entity_count), 0) as avg_entities,
                MAX(entity_count) as max_entities
            FROM performance_samples
            WHERE ts >= NOW() - INTERVAL '%s'
            GROUP BY time_bucket
            """.formatted(interval), "time_bucket"));
        perf.put("entityTimeline", entityTimeline);

        // Summary stats
        var summary = server.executeQuery("""
            SELECT
                ROUND(AVG(tps), 2) as avg_tps,
                ROUND(MIN(tps), 2) as min_tps,
                ROUND(AVG(memory_used_mb), 1) as avg_memory_mb,
                ROUND(AVG(entity_count), 0) as avg_entities,
                COUNT(*) as samples
            FROM performance_samples
            WHERE ts >= NOW() - INTERVAL '%s'
            """.formatted(interval));
        if (!summary.isEmpty()) {
            perf.put("summary", summary.get(0));
        }

        return gson.toJson(perf);
    }

    /**
     * Fight analysis - damage dealt vs received per fight session
     */
    public String handleFightAnalysis(HttpExchange exchange) {
        String interval = server.getTimeInterval(exchange);
        Map<String, String> params = server.parseQueryParams(exchange);
        String arenaFilter = buildArenaFilter(
            params.get("templateId"),
            params.get("templateVersion"),
            params.get("policyId"),
            params.get("policyVersion")
        );
        Map<String, Object> analysis = new HashMap<>();

        // Recent fights with details
        var fights = server.executeQuery("""
            SELECT
                fight_id,
                start_ts,
                duration_ms,
                total_damage_dealt,
                total_damage_received,
                hits_dealt,
                hits_received,
                ROUND(total_damage_dealt / GREATEST(1, hits_dealt), 2) as avg_damage_dealt,
                ROUND(total_damage_received / GREATEST(1, hits_received), 2) as avg_damage_received,
                outcome
            FROM combat_fights
            WHERE start_ts >= NOW() - INTERVAL '%s'
              %s
            ORDER BY start_ts DESC
            LIMIT 50
            """.formatted(interval, arenaFilter));
        analysis.put("fights", fights);

        // Aggregated fight stats
        var stats = server.executeQuery("""
            SELECT
                COUNT(*) as total_fights,
                ROUND(AVG(duration_ms) / 1000.0, 1) as avg_duration_sec,
                ROUND(SUM(total_damage_dealt), 1) as total_damage_dealt,
                ROUND(SUM(total_damage_received), 1) as total_damage_received,
                ROUND(AVG(total_damage_dealt), 1) as avg_damage_dealt_per_fight,
                ROUND(AVG(total_damage_received), 1) as avg_damage_received_per_fight,
                SUM(CASE WHEN outcome = 'victory' THEN 1 ELSE 0 END) as victories,
                SUM(CASE WHEN outcome = 'defeat' THEN 1 ELSE 0 END) as defeats
            FROM combat_fights
            WHERE start_ts >= NOW() - INTERVAL '%s'
              %s
            """.formatted(interval, arenaFilter));
        if (!stats.isEmpty()) {
            analysis.put("stats", stats.get(0));
        }

        return gson.toJson(analysis);
    }

    /**
     * Damage taken analysis - who/what is hurting players
     */
    public String handleDamageTaken(HttpExchange exchange) {
        String interval = server.getTimeInterval(exchange);
        var since = server.getRangeStart(exchange);
        Map<String, String> params = server.parseQueryParams(exchange);
        String player = params.get("player");
        ArenaFilterParams filter = parseArenaFilterParams(params);

        Map<String, Object> analysis = new HashMap<>();

        // Damage by source type
        String bySourceSql = String.join("\n",
            "SELECT",
            "    COALESCE(REPLACE(attacker_type, 'entity.', ''), 'environment') as source,",
            "    COUNT(*) as hits,",
            "    ROUND(SUM(damage), 1) as total_damage,",
            "    ROUND(AVG(damage), 2) as avg_damage",
            "FROM combat_hits",
            "WHERE ts >= ?",
            "  AND target_name IS NOT NULL",
            indentClause(PLAYER_FILTER_CLAUSE, "  "),
            indentClause(ARENA_FILTER_CLAUSE, "  "),
            "GROUP BY source",
            "ORDER BY total_damage DESC",
            "LIMIT 15"
        );
        List<TelemetryDashboardServer.SqlParam> bySourceParams = new ArrayList<>();
        bySourceParams.add(server.paramTimestamp(since));
        bySourceParams.add(server.paramString(player));
        bySourceParams.add(server.paramString(player));
        bySourceParams.addAll(arenaFilterParams(filter));
        var bySource = server.executeQuery(bySourceSql, bySourceParams);
        analysis.put("bySource", bySource);

        // Damage by type
        String byTypeSql = String.join("\n",
            "SELECT",
            "    COALESCE(damage_type, 'unknown') as damage_type,",
            "    COUNT(*) as hits,",
            "    ROUND(SUM(damage), 1) as total_damage",
            "FROM combat_hits",
            "WHERE ts >= ?",
            "  AND target_name IS NOT NULL",
            indentClause(PLAYER_FILTER_CLAUSE, "  "),
            indentClause(ARENA_FILTER_CLAUSE, "  "),
            "GROUP BY damage_type",
            "ORDER BY total_damage DESC",
            "LIMIT 15"
        );
        List<TelemetryDashboardServer.SqlParam> byTypeParams = new ArrayList<>();
        byTypeParams.add(server.paramTimestamp(since));
        byTypeParams.add(server.paramString(player));
        byTypeParams.add(server.paramString(player));
        byTypeParams.addAll(arenaFilterParams(filter));
        var byType = server.executeQuery(byTypeSql, byTypeParams);
        analysis.put("byType", byType);

        // Damage timeline
        String bucket = interval.contains("hour") ? "minute" : "hour";
        String timelineSql = newestBuckets(String.join("\n",
            "SELECT",
            "    DATE_TRUNC(?, ts) as time_bucket,",
            "    ROUND(SUM(damage), 1) as damage_taken,",
            "    COUNT(*) as hits_taken",
            "FROM combat_hits",
            "WHERE ts >= ?",
            "  AND target_name IS NOT NULL",
            indentClause(PLAYER_FILTER_CLAUSE, "  "),
            indentClause(ARENA_FILTER_CLAUSE, "  "),
            "GROUP BY time_bucket"
        ), "time_bucket");
        List<TelemetryDashboardServer.SqlParam> timelineParams = new ArrayList<>();
        timelineParams.add(server.paramString(bucket));
        timelineParams.add(server.paramTimestamp(since));
        timelineParams.add(server.paramString(player));
        timelineParams.add(server.paramString(player));
        timelineParams.addAll(arenaFilterParams(filter));
        var timeline = server.executeQuery(timelineSql, timelineParams);
        analysis.put("timeline", timeline);

        return gson.toJson(analysis);
    }

    /**
     * List of active players for filtering
     */
    public String handlePlayersList(HttpExchange exchange) {
        String interval = server.getTimeInterval(exchange);
        Map<String, String> params = server.parseQueryParams(exchange);
        String arenaFilter = buildArenaFilter(
            params.get("templateId"),
            params.get("templateVersion"),
            params.get("policyId"),
            params.get("policyVersion")
        );
        String sql = """
            SELECT DISTINCT
                attacker_name as player,
                COUNT(*) as activity
            FROM combat_hits
            WHERE ts >= NOW() - INTERVAL '%s'
              AND attacker_name IS NOT NULL
              %s
            GROUP BY attacker_name
            ORDER BY activity DESC
            LIMIT 50
            """.formatted(interval, arenaFilter);
        return gson.toJson(server.executeQuery(sql));
    }
}
