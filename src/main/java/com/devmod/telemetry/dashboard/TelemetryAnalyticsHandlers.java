package com.devmod.telemetry.dashboard;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
public class TelemetryAnalyticsHandlers {

    private final TelemetryDashboardServer server;
    private final Gson gson;

    public TelemetryAnalyticsHandlers(TelemetryDashboardServer server, Gson gson) {
        this.server = server;
        this.gson = gson;
    }

    // ========== Basic Analytics ==========

    public String handleAnalyticsOverview(HttpExchange exchange) {
        String interval = server.getTimeInterval(exchange);
        Map<String, String> params = parseQueryParams(exchange);
        String arenaFilter = buildArenaFilter(
            params.get("templateId"),
            params.get("templateVersion"),
            params.get("policyId"),
            params.get("policyVersion")
        );
        Map<String, Object> overview = new HashMap<>();

        // Total hits
        var hitsResult = server.executeQuery("SELECT COUNT(*) as total, SUM(damage) as total_damage FROM combat_hits WHERE ts >= NOW() - INTERVAL '" + interval + "'" + arenaFilter);
        if (!hitsResult.isEmpty()) {
            overview.put("totalHits", hitsResult.get(0).get("total"));
            overview.put("totalDamage", hitsResult.get(0).get("total_damage"));
        }

        // Total deaths
        var deathsResult = server.executeQuery("SELECT COUNT(*) as total FROM combat_deaths WHERE ts >= NOW() - INTERVAL '" + interval + "'" + arenaFilter);
        if (!deathsResult.isEmpty()) {
            overview.put("totalDeaths", deathsResult.get(0).get("total"));
        }

        // Accuracy
        var accuracyResult = server.executeQuery("""
            SELECT
                COUNT(*) as total,
                SUM(CASE WHEN is_miss THEN 1 ELSE 0 END) as misses
            FROM combat_hits
            WHERE ts >= NOW() - INTERVAL '""" + interval + "'" + arenaFilter);
        if (!accuracyResult.isEmpty()) {
            long total = ((Number) accuracyResult.get(0).getOrDefault("total", 0L)).longValue();
            long misses = ((Number) accuracyResult.get(0).getOrDefault("misses", 0L)).longValue();
            double accuracy = total > 0 ? 100.0 * (total - misses) / total : 0;
            overview.put("accuracy", Math.round(accuracy * 10) / 10.0);
        }

        // Mobs killed
        var mobsResult = server.executeQuery("SELECT COUNT(*) as total FROM economy_mob_kills WHERE ts >= NOW() - INTERVAL '" + interval + "'");
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
        String sql = """
            SELECT
                DATE_TRUNC('%s', ts) as time_bucket,
                COUNT(*) as hits,
                ROUND(SUM(damage), 1) as total_damage
            FROM combat_hits
            WHERE ts >= NOW() - INTERVAL '%s'
            %s
            GROUP BY time_bucket
            ORDER BY time_bucket
            """.formatted(bucket, interval, arenaFilter);
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
        String sql = """
            SELECT
                DATE_TRUNC('%s', ts) as time_bucket,
                COUNT(*) as total,
                SUM(CASE WHEN is_miss THEN 1 ELSE 0 END) as misses,
                ROUND(100.0 * (COUNT(*) - SUM(CASE WHEN is_miss THEN 1 ELSE 0 END)) / NULLIF(COUNT(*), 0), 1) as accuracy
            FROM combat_hits
            WHERE ts >= NOW() - INTERVAL '%s'
            %s
            GROUP BY time_bucket
            ORDER BY time_bucket
            """.formatted(bucket, interval, arenaFilter);
        return gson.toJson(server.executeQuery(sql));
    }

    public String handleEnduranceAnalytics(HttpExchange exchange) {
        String interval = server.getTimeInterval(exchange);
        Map<String, String> params = parseQueryParams(exchange);
        String templateFilter = buildArenaFilter(
            params.get("templateId"),
            params.get("templateVersion"),
            params.get("policyId"),
            params.get("policyVersion")
        );
        Map<String, Object> stats = new HashMap<>();

        // Session stats
        var sessionResult = server.executeQuery("""
            SELECT
                COUNT(*) as total_sessions,
                SUM(CASE WHEN outcome = 'victory' THEN 1 ELSE 0 END) as wins,
                SUM(CASE WHEN outcome = 'defeat' THEN 1 ELSE 0 END) as losses,
                ROUND(AVG(waves_completed), 1) as avg_waves,
                MAX(waves_completed) as best_wave
            FROM endurance_sessions
            WHERE start_ts >= NOW() - INTERVAL '""" + interval + "'" + templateFilter);
        if (!sessionResult.isEmpty()) {
            stats.putAll(sessionResult.get(0));
            long total = ((Number) sessionResult.get(0).getOrDefault("total_sessions", 0L)).longValue();
            long wins = ((Number) sessionResult.get(0).getOrDefault("wins", 0L)).longValue();
            stats.put("winRate", total > 0 ? Math.round(1000.0 * wins / total) / 10.0 : 0);
        }

        // Outcomes by day
        var outcomesResult = server.executeQuery("""
            SELECT
                DATE_TRUNC('day', start_ts) as day,
                outcome,
                COUNT(*) as count
            FROM endurance_sessions
            WHERE start_ts >= NOW() - INTERVAL '""" + interval + """
            '""" + templateFilter + """
            GROUP BY day, outcome
            ORDER BY day
            """);
        stats.put("outcomes", outcomesResult);

        // Perk popularity
        var perksResult = server.executeQuery("""
            SELECT
                perk_name,
                COUNT(*) as picks
            FROM endurance_perks
            WHERE event_type = 'selected'
              AND ts >= NOW() - INTERVAL '""" + interval + """
            '""" + templateFilter + """
            GROUP BY perk_name
            ORDER BY picks DESC
            LIMIT 10
            """);
        stats.put("perks", perksResult);

        return gson.toJson(stats);
    }

    private Map<String, String> parseQueryParams(HttpExchange exchange) {
        Map<String, String> params = new HashMap<>();
        String query = exchange.getRequestURI().getQuery();
        if (query == null || query.isBlank()) {
            return params;
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
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

    private String buildArenaFilter(String templateId, String templateVersion,
                                    String policyId, String policyVersion) {
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
        String interval = server.getTimeInterval(exchange);
        Map<String, Object> stats = new HashMap<>();

        // Overall stats
        var overallResult = server.executeQuery("""
            SELECT
                COUNT(*) as total_runs,
                SUM(CASE WHEN outcome = 'completed' THEN 1 ELSE 0 END) as completed,
                ROUND(AVG(duration_ms) / 1000.0 / 60.0, 1) as avg_duration_min,
                ROUND(AVG(deaths), 1) as avg_deaths,
                ROUND(AVG(kills), 1) as avg_kills
            FROM dungeon_runs
            WHERE start_ts >= NOW() - INTERVAL '""" + interval + "'");
        if (!overallResult.isEmpty()) {
            stats.putAll(overallResult.get(0));
            long total = ((Number) overallResult.get(0).getOrDefault("total_runs", 0L)).longValue();
            long completed = ((Number) overallResult.get(0).getOrDefault("completed", 0L)).longValue();
            stats.put("completionRate", total > 0 ? Math.round(1000.0 * completed / total) / 10.0 : 0);
        }

        // By dungeon
        var byDungeonResult = server.executeQuery("""
            SELECT
                dungeon_id,
                COUNT(*) as runs,
                SUM(CASE WHEN outcome = 'completed' THEN 1 ELSE 0 END) as completed,
                ROUND(100.0 * SUM(CASE WHEN outcome = 'completed' THEN 1 ELSE 0 END) / NULLIF(COUNT(*), 0), 1) as completion_rate
            FROM dungeon_runs
            WHERE start_ts >= NOW() - INTERVAL '""" + interval + """
            '
            GROUP BY dungeon_id
            ORDER BY runs DESC
            """);
        stats.put("byDungeon", byDungeonResult);

        return gson.toJson(stats);
    }

    public String handleRoomAnalytics(HttpExchange exchange) {
        String interval = server.getTimeInterval(exchange);
        Map<String, String> params = parseQueryParams(exchange);
        String arenaFilter = buildArenaFilter(
            params.get("templateId"),
            params.get("templateVersion"),
            params.get("policyId"),
            params.get("policyVersion")
        );
        Map<String, Object> stats = new HashMap<>();

        // Room visits
        var visitsResult = server.executeQuery("""
            SELECT
                room,
                COUNT(*) as visits
            FROM spatial_room_transitions
            WHERE ts >= NOW() - INTERVAL '""" + interval + """
            '
            GROUP BY room
            ORDER BY visits DESC
            LIMIT 15
            """);
        stats.put("visits", visitsResult);

        // Deaths by room
        var deathsResult = server.executeQuery("""
            SELECT
                COALESCE(room, 'unknown') as room,
                COUNT(*) as deaths
            FROM combat_deaths
            WHERE ts >= NOW() - INTERVAL '""" + interval + """
            '""" + arenaFilter + """
            GROUP BY room
            ORDER BY deaths DESC
            LIMIT 15
            """);
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
        Map<String, String> params = server.parseQueryParams(exchange);
        String player = params.get("player");
        String arenaFilter = buildArenaFilter(
            params.get("templateId"),
            params.get("templateVersion"),
            params.get("policyId"),
            params.get("policyVersion")
        );

        String playerFilter = (player != null && !player.isBlank())
            ? " AND attacker_name = '" + player + "'"
            : "";

        // Calculate DPS per time bucket (damage / seconds in bucket)
        String bucket = interval.contains("hour") ? "minute" : "hour";
        int bucketSeconds = bucket.equals("minute") ? 60 : 3600;

        String sql = """
            SELECT
                DATE_TRUNC('%s', ts) as time_bucket,
                ROUND(SUM(damage) / %d.0, 2) as dps,
                ROUND(SUM(damage), 1) as total_damage,
                COUNT(*) as hits,
                ROUND(AVG(damage), 2) as avg_hit
            FROM combat_hits
            WHERE ts >= NOW() - INTERVAL '%s'
              AND attacker_name IS NOT NULL
              AND NOT is_miss
              %s
              %s
            GROUP BY time_bucket
            ORDER BY time_bucket
            """.formatted(bucket, bucketSeconds, interval, playerFilter, arenaFilter);
        return gson.toJson(server.executeQuery(sql));
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
        var dpsTimeline = server.executeQuery("""
            SELECT
                DATE_TRUNC('minute', ts) as time_bucket,
                ROUND(SUM(damage) / 60.0, 2) as dps
            FROM combat_hits
            WHERE attacker_name = '%s'
              AND ts >= NOW() - INTERVAL '%s'
              AND NOT is_miss
              %s
            GROUP BY time_bucket
            ORDER BY time_bucket
            LIMIT 60
            """.formatted(player, interval, arenaFilter));
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
        String interval = server.getTimeInterval(exchange);
        Map<String, String> params = server.parseQueryParams(exchange);
        String arenaFilter = buildArenaFilter(
            params.get("templateId"),
            params.get("templateVersion"),
            params.get("policyId"),
            params.get("policyVersion")
        );
        Map<String, Object> trends = new HashMap<>();

        // Current period stats
        var current = server.executeQuery("""
            SELECT
                COUNT(*) as hits,
                ROUND(SUM(damage), 1) as damage,
                COUNT(DISTINCT attacker_name) as players,
                ROUND(AVG(damage), 2) as avg_damage
            FROM combat_hits
            WHERE ts >= NOW() - INTERVAL '%s'
            %s
            """.formatted(interval, arenaFilter));

        // Previous period stats (same duration, before current)
        var previous = server.executeQuery("""
            SELECT
                COUNT(*) as hits,
                ROUND(SUM(damage), 1) as damage,
                COUNT(DISTINCT attacker_name) as players,
                ROUND(AVG(damage), 2) as avg_damage
            FROM combat_hits
            WHERE ts >= NOW() - INTERVAL '%s' - INTERVAL '%s'
              AND ts < NOW() - INTERVAL '%s'
            %s
            """.formatted(interval, interval, interval, arenaFilter));

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
        var currentDeaths = server.executeQuery("SELECT COUNT(*) as deaths FROM combat_deaths WHERE ts >= NOW() - INTERVAL '" + interval + "'" + arenaFilter);
        var previousDeaths = server.executeQuery("SELECT COUNT(*) as deaths FROM combat_deaths WHERE ts >= NOW() - INTERVAL '" + interval + "' - INTERVAL '" + interval + "' AND ts < NOW() - INTERVAL '" + interval + "'" + arenaFilter);

        if (!currentDeaths.isEmpty()) trends.put("currentDeaths", currentDeaths.get(0).get("deaths"));
        if (!previousDeaths.isEmpty()) trends.put("previousDeaths", previousDeaths.get(0).get("deaths"));
        if (!currentDeaths.isEmpty() && !previousDeaths.isEmpty()) {
            trends.put("deathsChange", calculateDelta(currentDeaths.get(0).get("deaths"), previousDeaths.get(0).get("deaths")));
        }

        // Kills trends
        var currentKills = server.executeQuery("SELECT COUNT(*) as kills FROM economy_mob_kills WHERE ts >= NOW() - INTERVAL '" + interval + "'");
        var previousKills = server.executeQuery("SELECT COUNT(*) as kills FROM economy_mob_kills WHERE ts >= NOW() - INTERVAL '" + interval + "' - INTERVAL '" + interval + "' AND ts < NOW() - INTERVAL '" + interval + "'");

        if (!currentKills.isEmpty()) trends.put("currentKills", currentKills.get(0).get("kills"));
        if (!previousKills.isEmpty()) trends.put("previousKills", previousKills.get(0).get("kills"));
        if (!currentKills.isEmpty() && !previousKills.isEmpty()) {
            trends.put("killsChange", calculateDelta(currentKills.get(0).get("kills"), previousKills.get(0).get("kills")));
        }

        return gson.toJson(trends);
    }

    private double calculateDelta(Object current, Object previous) {
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
        var tpsTimeline = server.executeQuery("""
            SELECT
                DATE_TRUNC('minute', ts) as time_bucket,
                ROUND(AVG(tps), 2) as avg_tps,
                ROUND(MIN(tps), 2) as min_tps,
                ROUND(MAX(tps), 2) as max_tps
            FROM performance_samples
            WHERE ts >= NOW() - INTERVAL '%s'
            GROUP BY time_bucket
            ORDER BY time_bucket
            """.formatted(interval));
        perf.put("tpsTimeline", tpsTimeline);

        // Memory timeline
        var memTimeline = server.executeQuery("""
            SELECT
                DATE_TRUNC('minute', ts) as time_bucket,
                ROUND(AVG(memory_used_mb), 1) as avg_memory_mb,
                ROUND(MAX(memory_used_mb), 1) as max_memory_mb
            FROM performance_samples
            WHERE ts >= NOW() - INTERVAL '%s'
            GROUP BY time_bucket
            ORDER BY time_bucket
            """.formatted(interval));
        perf.put("memoryTimeline", memTimeline);

        // Entity counts
        var entityTimeline = server.executeQuery("""
            SELECT
                DATE_TRUNC('minute', ts) as time_bucket,
                ROUND(AVG(entity_count), 0) as avg_entities,
                MAX(entity_count) as max_entities
            FROM performance_samples
            WHERE ts >= NOW() - INTERVAL '%s'
            GROUP BY time_bucket
            ORDER BY time_bucket
            """.formatted(interval));
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
        Map<String, String> params = server.parseQueryParams(exchange);
        String player = params.get("player");
        String arenaFilter = buildArenaFilter(
            params.get("templateId"),
            params.get("templateVersion"),
            params.get("policyId"),
            params.get("policyVersion")
        );

        String playerFilter = (player != null && !player.isBlank())
            ? " AND target_name = '" + player + "'"
            : "";

        Map<String, Object> analysis = new HashMap<>();

        // Damage by source type
        var bySource = server.executeQuery("""
            SELECT
                COALESCE(REPLACE(attacker_type, 'entity.', ''), 'environment') as source,
                COUNT(*) as hits,
                ROUND(SUM(damage), 1) as total_damage,
                ROUND(AVG(damage), 2) as avg_damage
            FROM combat_hits
            WHERE ts >= NOW() - INTERVAL '%s'
              AND target_name IS NOT NULL
              %s
              %s
            GROUP BY source
            ORDER BY total_damage DESC
            LIMIT 15
            """.formatted(interval, playerFilter, arenaFilter));
        analysis.put("bySource", bySource);

        // Damage by type
        var byType = server.executeQuery("""
            SELECT
                COALESCE(damage_type, 'unknown') as damage_type,
                COUNT(*) as hits,
                ROUND(SUM(damage), 1) as total_damage
            FROM combat_hits
            WHERE ts >= NOW() - INTERVAL '%s'
              AND target_name IS NOT NULL
              %s
              %s
            GROUP BY damage_type
            ORDER BY total_damage DESC
            """.formatted(interval, playerFilter, arenaFilter));
        analysis.put("byType", byType);

        // Damage timeline
        String bucket = interval.contains("hour") ? "minute" : "hour";
        var timeline = server.executeQuery("""
            SELECT
                DATE_TRUNC('%s', ts) as time_bucket,
                ROUND(SUM(damage), 1) as damage_taken,
                COUNT(*) as hits_taken
            FROM combat_hits
            WHERE ts >= NOW() - INTERVAL '%s'
              AND target_name IS NOT NULL
              %s
              %s
            GROUP BY time_bucket
            ORDER BY time_bucket
            """.formatted(bucket, interval, playerFilter, arenaFilter));
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
