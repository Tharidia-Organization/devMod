package com.frenkvs.devmod.telemetry.dashboard;

import com.frenkvs.devmod.telemetry.duckdb.DuckDBTelemetryService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Embedded HTTP server for telemetry dashboard.
 *
 * Provides:
 * - REST API endpoints for querying DuckDB telemetry data
 * - Static file serving for the SPA dashboard
 * - CORS support for development
 *
 * Access at: http://localhost:8642/dashboard
 */
public class TelemetryDashboardServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final TelemetryDashboardServer INSTANCE = new TelemetryDashboardServer();

    private static final int DEFAULT_PORT = 8642;
    private static final String BIND_ADDRESS = "127.0.0.1"; // localhost only for security

    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private HttpServer server;
    private int port = DEFAULT_PORT;

    private TelemetryDashboardServer() {}

    /**
     * Starts the dashboard server.
     */
    public synchronized void start() {
        if (running.get()) {
            LOGGER.warn("[Dashboard] Server already running on port {}", port);
            return;
        }

        try {
            server = HttpServer.create(new InetSocketAddress(BIND_ADDRESS, port), 0);
            server.setExecutor(Executors.newFixedThreadPool(4));

            // API Routes - Basic
            server.createContext("/api/health", new ApiHandler(this::handleHealth));
            server.createContext("/api/summary", new ApiHandler(this::handleSummary));
            server.createContext("/api/tables", new ApiHandler(this::handleTables));
            server.createContext("/api/query", new ApiHandler(this::handleQuery));

            // API Routes - Raw Data
            server.createContext("/api/combat/hits", new ApiHandler(this::handleCombatHits));
            server.createContext("/api/combat/deaths", new ApiHandler(this::handleCombatDeaths));
            server.createContext("/api/combat/fights", new ApiHandler(this::handleCombatFights));
            server.createContext("/api/combat/weapons", new ApiHandler(this::handleCombatWeapons));
            server.createContext("/api/endurance/sessions", new ApiHandler(this::handleEnduranceSessions));
            server.createContext("/api/endurance/waves", new ApiHandler(this::handleEnduranceWaves));
            server.createContext("/api/endurance/perks", new ApiHandler(this::handleEndurancePerks));
            server.createContext("/api/player/snapshots", new ApiHandler(this::handlePlayerSnapshots));
            server.createContext("/api/player/abilities", new ApiHandler(this::handlePlayerAbilities));
            server.createContext("/api/spatial/heatmaps", new ApiHandler(this::handleSpatialHeatmaps));
            server.createContext("/api/spatial/transitions", new ApiHandler(this::handleSpatialTransitions));
            server.createContext("/api/economy/drops", new ApiHandler(this::handleEconomyDrops));
            server.createContext("/api/economy/kills", new ApiHandler(this::handleEconomyKills));
            server.createContext("/api/dungeons/runs", new ApiHandler(this::handleDungeonRuns));
            server.createContext("/api/performance", new ApiHandler(this::handlePerformance));

            // API Routes - Analytics (aggregated data for charts)
            server.createContext("/api/analytics/overview", new ApiHandler(this::handleAnalyticsOverview));
            server.createContext("/api/analytics/hits-timeline", new ApiHandler(this::handleHitsTimeline));
            server.createContext("/api/analytics/damage-by-bodypart", new ApiHandler(this::handleDamageByBodypart));
            server.createContext("/api/analytics/damage-by-type", new ApiHandler(this::handleDamageByType));
            server.createContext("/api/analytics/weapon-stats", new ApiHandler(this::handleWeaponAnalytics));
            server.createContext("/api/analytics/mob-kills", new ApiHandler(this::handleMobKillsAnalytics));
            server.createContext("/api/analytics/ttk", new ApiHandler(this::handleTTKAnalytics));
            server.createContext("/api/analytics/accuracy-timeline", new ApiHandler(this::handleAccuracyTimeline));
            server.createContext("/api/analytics/endurance-stats", new ApiHandler(this::handleEnduranceAnalytics));
            server.createContext("/api/analytics/dungeon-stats", new ApiHandler(this::handleDungeonAnalytics));
            server.createContext("/api/analytics/room-stats", new ApiHandler(this::handleRoomAnalytics));
            server.createContext("/api/analytics/loot-rates", new ApiHandler(this::handleLootRatesAnalytics));

            // API Routes - Advanced Analytics v2
            server.createContext("/api/analytics/dps-timeline", new ApiHandler(this::handleDpsTimeline));
            server.createContext("/api/analytics/player-stats", new ApiHandler(this::handlePlayerStats));
            server.createContext("/api/analytics/player-comparison", new ApiHandler(this::handlePlayerComparison));
            server.createContext("/api/analytics/trends", new ApiHandler(this::handleTrends));
            server.createContext("/api/analytics/performance", new ApiHandler(this::handlePerformanceAnalytics));
            server.createContext("/api/analytics/fight-analysis", new ApiHandler(this::handleFightAnalysis));
            server.createContext("/api/analytics/damage-taken", new ApiHandler(this::handleDamageTaken));
            server.createContext("/api/analytics/players-list", new ApiHandler(this::handlePlayersList));

            // Static files for dashboard SPA
            server.createContext("/dashboard", new StaticFileHandler("/dashboard"));
            // Also serve static files from root (for relative paths in HTML)
            server.createContext("/style.css", new StaticFileHandler("/dashboard"));
            server.createContext("/app.js", new StaticFileHandler("/dashboard"));

            server.start();
            running.set(true);
            LOGGER.info("[Dashboard] Server started at http://{}:{}/dashboard", BIND_ADDRESS, port);

        } catch (IOException e) {
            LOGGER.error("[Dashboard] Failed to start server: {}", e.getMessage(), e);
        }
    }

    /**
     * Stops the dashboard server.
     */
    public synchronized void stop() {
        if (!running.get()) {
            return;
        }

        try {
            if (server != null) {
                server.stop(1);
                server = null;
            }
            running.set(false);
            LOGGER.info("[Dashboard] Server stopped");
        } catch (Exception e) {
            LOGGER.error("[Dashboard] Error stopping server: {}", e.getMessage(), e);
        }
    }

    /**
     * Checks if the server is running.
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Gets the dashboard URL.
     */
    public String getDashboardUrl() {
        return "http://" + BIND_ADDRESS + ":" + port + "/dashboard";
    }

    /**
     * Opens the dashboard in the default browser.
     */
    public void openInBrowser() {
        if (!running.get()) {
            LOGGER.warn("[Dashboard] Server not running, starting...");
            start();
        }

        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(getDashboardUrl()));
                LOGGER.info("[Dashboard] Opened browser: {}", getDashboardUrl());
            } else {
                LOGGER.warn("[Dashboard] Desktop browse not supported. Open manually: {}", getDashboardUrl());
            }
        } catch (Exception e) {
            LOGGER.error("[Dashboard] Failed to open browser: {}", e.getMessage());
        }
    }

    // ========== API Handlers ==========

    private String handleHealth(HttpExchange exchange) {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "ok");
        health.put("duckdb_enabled", DuckDBTelemetryService.INSTANCE.isEnabled());
        health.put("timestamp", System.currentTimeMillis());
        return gson.toJson(health);
    }

    private String handleSummary(HttpExchange exchange) {
        return gson.toJson(getSummaryStats());
    }

    private String handleTables(HttpExchange exchange) {
        return gson.toJson(getTableList());
    }

    private String handleCombatHits(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        int limit = getIntParam(params.get("limit"), 1000);
        return gson.toJson(queryTable("combat_hits", params.get("from"), params.get("to"), limit));
    }

    private String handleCombatDeaths(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        int limit = getIntParam(params.get("limit"), 500);
        return gson.toJson(queryTable("combat_deaths", params.get("from"), params.get("to"), limit));
    }

    private String handleCombatFights(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        int limit = getIntParam(params.get("limit"), 100);
        return gson.toJson(queryTable("combat_fights", params.get("from"), params.get("to"), limit, "start_ts"));
    }

    private String handleCombatWeapons(HttpExchange exchange) {
        return gson.toJson(getWeaponStats());
    }

    private String handleEnduranceSessions(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        int limit = getIntParam(params.get("limit"), 100);
        return gson.toJson(queryTable("endurance_sessions", params.get("from"), params.get("to"), limit, "start_ts"));
    }

    private String handleEnduranceWaves(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        int limit = getIntParam(params.get("limit"), 500);
        String sessionId = params.get("session_id");
        if (sessionId != null) {
            return gson.toJson(queryWithFilter("endurance_waves", "session_id", sessionId, limit));
        }
        return gson.toJson(queryTable("endurance_waves", null, null, limit));
    }

    private String handleEndurancePerks(HttpExchange exchange) {
        return gson.toJson(getPerkStats());
    }

    private String handlePlayerSnapshots(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        int limit = getIntParam(params.get("limit"), 500);
        String playerId = params.get("player_id");
        if (playerId != null) {
            return gson.toJson(queryWithFilter("player_snapshots", "player_id", playerId, limit));
        }
        return gson.toJson(queryTable("player_snapshots", null, null, limit));
    }

    private String handlePlayerAbilities(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        int limit = getIntParam(params.get("limit"), 500);
        String playerId = params.get("player_id");
        if (playerId != null) {
            return gson.toJson(queryWithFilter("player_abilities", "player_id", playerId, limit));
        }
        return gson.toJson(queryTable("player_abilities", null, null, limit));
    }

    private String handleSpatialHeatmaps(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        int limit = getIntParam(params.get("limit"), 5000);
        return gson.toJson(getHeatmapData(params.get("type"), params.get("room"), limit));
    }

    private String handleSpatialTransitions(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        int limit = getIntParam(params.get("limit"), 1000);
        return gson.toJson(queryTable("spatial_room_transitions", null, null, limit));
    }

    private String handleEconomyDrops(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        int limit = getIntParam(params.get("limit"), 500);
        return gson.toJson(queryTable("economy_mob_drops", null, null, limit));
    }

    private String handleEconomyKills(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        int limit = getIntParam(params.get("limit"), 50);
        // Aggregate mob kills by type - show total kills and loot rate per mob
        String sql = """
            SELECT
                mob_type,
                COUNT(*) as total_kills,
                SUM(CASE WHEN had_loot THEN 1 ELSE 0 END) as kills_with_loot,
                ROUND(100.0 * SUM(CASE WHEN had_loot THEN 1 ELSE 0 END) / COUNT(*), 1) as loot_rate_pct,
                MIN(ts) as first_kill,
                MAX(ts) as last_kill
            FROM economy_mob_kills
            GROUP BY mob_type
            ORDER BY total_kills DESC
            LIMIT """ + limit;
        return gson.toJson(executeQuery(sql));
    }

    private String handleDungeonRuns(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        int limit = getIntParam(params.get("limit"), 100);
        return gson.toJson(queryTable("dungeon_runs", params.get("from"), params.get("to"), limit, "start_ts"));
    }

    private String handlePerformance(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        int limit = getIntParam(params.get("limit"), 1000);
        return gson.toJson(queryTable("performance_samples", params.get("from"), params.get("to"), limit));
    }

    private String handleQuery(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            return gson.toJson(Map.of("error", "POST method required"));
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        Map<String, String> request = gson.fromJson(body, Map.class);
        String sql = request != null ? request.get("sql") : null;

        if (sql == null || sql.isBlank()) {
            return gson.toJson(Map.of("error", "Missing 'sql' parameter"));
        }

        // Security: only allow SELECT queries
        if (!sql.trim().toUpperCase().startsWith("SELECT")) {
            return gson.toJson(Map.of("error", "Only SELECT queries are allowed"));
        }

        return gson.toJson(executeQuery(sql));
    }

    // ========== Query Helpers ==========

    private Map<String, Object> getSummaryStats() {
        Map<String, Object> summary = new HashMap<>();

        try {
            summary.put("tables", getTableCounts());
            summary.put("recent_activity", getRecentActivity());
            summary.put("db_size_kb", getDatabaseSizeKb());
        } catch (Exception e) {
            summary.put("error", e.getMessage());
        }

        return summary;
    }

    private Map<String, Long> getTableCounts() {
        Map<String, Long> counts = new HashMap<>();
        String[] tables = {
            "combat_hits", "combat_deaths", "combat_fights", "combat_heals", "combat_spawns",
            "endurance_sessions", "endurance_waves", "endurance_perks",
            "player_snapshots", "player_abilities",
            "spatial_heatmaps", "spatial_room_transitions",
            "economy_mob_kills", "economy_mob_drops",
            "dungeon_runs", "performance_samples"
        };

        for (String table : tables) {
            try {
                List<Map<String, Object>> result = executeQuery("SELECT COUNT(*) as cnt FROM " + table);
                if (!result.isEmpty()) {
                    counts.put(table, ((Number) result.get(0).get("cnt")).longValue());
                }
            } catch (Exception e) {
                counts.put(table, -1L);
            }
        }

        return counts;
    }

    private Map<String, Long> getRecentActivity() {
        Map<String, Long> activity = new HashMap<>();
        String[] tables = {"combat_hits", "combat_deaths", "endurance_sessions", "player_snapshots"};

        for (String table : tables) {
            try {
                String sql = "SELECT COUNT(*) as cnt FROM " + table +
                             " WHERE ts >= NOW() - INTERVAL '15 minutes'";
                List<Map<String, Object>> result = executeQuery(sql);
                if (!result.isEmpty()) {
                    activity.put(table + "_15min", ((Number) result.get(0).get("cnt")).longValue());
                }
            } catch (Exception e) {
                // Table might not have 'ts' column
            }
        }

        return activity;
    }

    private long getDatabaseSizeKb() {
        try {
            List<Map<String, Object>> result = executeQuery(
                "SELECT SUM(estimated_size) / 1024 as size_kb FROM duckdb_tables()"
            );
            if (!result.isEmpty() && result.get(0).get("size_kb") != null) {
                return ((Number) result.get(0).get("size_kb")).longValue();
            }
        } catch (Exception e) {
            // Ignore
        }
        return -1;
    }

    private List<Map<String, Object>> queryTable(String table, String from, String to, int limit) {
        return queryTable(table, from, to, limit, "ts");
    }

    private List<Map<String, Object>> queryTable(String table, String from, String to, int limit, String tsColumn) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(table);
        List<String> conditions = new ArrayList<>();

        if (from != null && !from.isBlank()) {
            conditions.add(tsColumn + " >= '" + from + "'");
        }
        if (to != null && !to.isBlank()) {
            conditions.add(tsColumn + " <= '" + to + "'");
        }

        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }

        sql.append(" ORDER BY ").append(tsColumn).append(" DESC LIMIT ").append(limit);

        return executeQuery(sql.toString());
    }

    private List<Map<String, Object>> queryWithFilter(String table, String column, String value, int limit) {
        String sql = "SELECT * FROM " + table + " WHERE " + column + " = '" + value +
                     "' ORDER BY ts DESC LIMIT " + limit;
        return executeQuery(sql);
    }

    private List<Map<String, Object>> getWeaponStats() {
        String sql = """
            SELECT
                COALESCE(NULLIF(JSON_EXTRACT_STRING(attacker_state, '$.mainHand'), ''), 'fist') as weapon,
                COUNT(*) as hit_count,
                ROUND(SUM(damage), 1) as total_damage,
                ROUND(AVG(damage), 2) as avg_damage,
                SUM(CASE WHEN is_miss THEN 1 ELSE 0 END) as misses
            FROM combat_hits
            WHERE ts >= NOW() - INTERVAL '1 hour'
              AND attacker_name IS NOT NULL
            GROUP BY weapon
            ORDER BY total_damage DESC
            LIMIT 20
            """;
        return executeQuery(sql);
    }

    private List<Map<String, Object>> getPerkStats() {
        String sql = """
            SELECT
                perk_name,
                perk_id,
                category,
                tier,
                COUNT(*) as picks,
                AVG(stack_count) as avg_stacks
            FROM endurance_perks
            WHERE event_type = 'selected'
            GROUP BY perk_name, perk_id, category, tier
            ORDER BY picks DESC
            LIMIT 50
            """;
        return executeQuery(sql);
    }

    // ========== Analytics Handlers ==========

    private String getTimeInterval(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        String range = params.getOrDefault("range", "24h");
        return switch (range) {
            case "1h" -> "1 hour";
            case "6h" -> "6 hours";
            case "24h" -> "24 hours";
            case "7d" -> "7 days";
            case "all" -> "100 years";
            default -> "24 hours";
        };
    }

    private String handleAnalyticsOverview(HttpExchange exchange) {
        String interval = getTimeInterval(exchange);
        Map<String, Object> overview = new HashMap<>();

        // Total hits
        var hitsResult = executeQuery("SELECT COUNT(*) as total, SUM(damage) as total_damage FROM combat_hits WHERE ts >= NOW() - INTERVAL '" + interval + "'");
        if (!hitsResult.isEmpty()) {
            overview.put("totalHits", hitsResult.get(0).get("total"));
            overview.put("totalDamage", hitsResult.get(0).get("total_damage"));
        }

        // Total deaths
        var deathsResult = executeQuery("SELECT COUNT(*) as total FROM combat_deaths WHERE ts >= NOW() - INTERVAL '" + interval + "'");
        if (!deathsResult.isEmpty()) {
            overview.put("totalDeaths", deathsResult.get(0).get("total"));
        }

        // Accuracy
        var accuracyResult = executeQuery("""
            SELECT
                COUNT(*) as total,
                SUM(CASE WHEN is_miss THEN 1 ELSE 0 END) as misses
            FROM combat_hits
            WHERE ts >= NOW() - INTERVAL '""" + interval + "'");
        if (!accuracyResult.isEmpty()) {
            long total = ((Number) accuracyResult.get(0).getOrDefault("total", 0L)).longValue();
            long misses = ((Number) accuracyResult.get(0).getOrDefault("misses", 0L)).longValue();
            double accuracy = total > 0 ? 100.0 * (total - misses) / total : 0;
            overview.put("accuracy", Math.round(accuracy * 10) / 10.0);
        }

        // Mobs killed
        var mobsResult = executeQuery("SELECT COUNT(*) as total FROM economy_mob_kills WHERE ts >= NOW() - INTERVAL '" + interval + "'");
        if (!mobsResult.isEmpty()) {
            overview.put("mobsKilled", mobsResult.get(0).get("total"));
        }

        // DB size
        var sizeResult = executeQuery("SELECT SUM(estimated_size) / 1024 as size_kb FROM duckdb_tables()");
        if (!sizeResult.isEmpty() && sizeResult.get(0).get("size_kb") != null) {
            overview.put("dbSizeKb", sizeResult.get(0).get("size_kb"));
        }

        return gson.toJson(overview);
    }

    private String handleHitsTimeline(HttpExchange exchange) {
        String interval = getTimeInterval(exchange);
        String bucket = interval.contains("hour") ? "minute" : "hour";
        String sql = """
            SELECT
                DATE_TRUNC('%s', ts) as time_bucket,
                COUNT(*) as hits,
                ROUND(SUM(damage), 1) as total_damage
            FROM combat_hits
            WHERE ts >= NOW() - INTERVAL '%s'
            GROUP BY time_bucket
            ORDER BY time_bucket
            """.formatted(bucket, interval);
        return gson.toJson(executeQuery(sql));
    }

    private String handleDamageByBodypart(HttpExchange exchange) {
        String interval = getTimeInterval(exchange);
        String sql = """
            SELECT
                COALESCE(body_part, 'UNKNOWN') as body_part,
                COUNT(*) as hits,
                ROUND(SUM(damage), 1) as total_damage,
                ROUND(AVG(damage), 2) as avg_damage
            FROM combat_hits
            WHERE ts >= NOW() - INTERVAL '%s'
              AND body_part IS NOT NULL
            GROUP BY body_part
            ORDER BY total_damage DESC
            """.formatted(interval);
        return gson.toJson(executeQuery(sql));
    }

    private String handleDamageByType(HttpExchange exchange) {
        String interval = getTimeInterval(exchange);
        String sql = """
            SELECT
                COALESCE(damage_type, 'unknown') as damage_type,
                COUNT(*) as hits,
                ROUND(SUM(damage), 1) as total_damage
            FROM combat_hits
            WHERE ts >= NOW() - INTERVAL '%s'
            GROUP BY damage_type
            ORDER BY total_damage DESC
            LIMIT 15
            """.formatted(interval);
        return gson.toJson(executeQuery(sql));
    }

    private String handleWeaponAnalytics(HttpExchange exchange) {
        String interval = getTimeInterval(exchange);
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
            GROUP BY weapon
            ORDER BY total_damage DESC
            LIMIT 15
            """.formatted(interval);
        return gson.toJson(executeQuery(sql));
    }

    private String handleMobKillsAnalytics(HttpExchange exchange) {
        String interval = getTimeInterval(exchange);
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
        return gson.toJson(executeQuery(sql));
    }

    private String handleTTKAnalytics(HttpExchange exchange) {
        String interval = getTimeInterval(exchange);
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
            GROUP BY target_type
            ORDER BY deaths DESC
            LIMIT 15
            """.formatted(interval);
        return gson.toJson(executeQuery(sql));
    }

    private String handleAccuracyTimeline(HttpExchange exchange) {
        String interval = getTimeInterval(exchange);
        String bucket = interval.contains("hour") ? "minute" : "hour";
        String sql = """
            SELECT
                DATE_TRUNC('%s', ts) as time_bucket,
                COUNT(*) as total,
                SUM(CASE WHEN is_miss THEN 1 ELSE 0 END) as misses,
                ROUND(100.0 * (COUNT(*) - SUM(CASE WHEN is_miss THEN 1 ELSE 0 END)) / NULLIF(COUNT(*), 0), 1) as accuracy
            FROM combat_hits
            WHERE ts >= NOW() - INTERVAL '%s'
            GROUP BY time_bucket
            ORDER BY time_bucket
            """.formatted(bucket, interval);
        return gson.toJson(executeQuery(sql));
    }

    private String handleEnduranceAnalytics(HttpExchange exchange) {
        String interval = getTimeInterval(exchange);
        Map<String, Object> stats = new HashMap<>();

        // Session stats
        var sessionResult = executeQuery("""
            SELECT
                COUNT(*) as total_sessions,
                SUM(CASE WHEN outcome = 'victory' THEN 1 ELSE 0 END) as wins,
                SUM(CASE WHEN outcome = 'defeat' THEN 1 ELSE 0 END) as losses,
                ROUND(AVG(waves_completed), 1) as avg_waves,
                MAX(waves_completed) as best_wave
            FROM endurance_sessions
            WHERE start_ts >= NOW() - INTERVAL '""" + interval + "'");
        if (!sessionResult.isEmpty()) {
            stats.putAll(sessionResult.get(0));
            long total = ((Number) sessionResult.get(0).getOrDefault("total_sessions", 0L)).longValue();
            long wins = ((Number) sessionResult.get(0).getOrDefault("wins", 0L)).longValue();
            stats.put("winRate", total > 0 ? Math.round(1000.0 * wins / total) / 10.0 : 0);
        }

        // Outcomes by day
        var outcomesResult = executeQuery("""
            SELECT
                DATE_TRUNC('day', start_ts) as day,
                outcome,
                COUNT(*) as count
            FROM endurance_sessions
            WHERE start_ts >= NOW() - INTERVAL '""" + interval + """
            '
            GROUP BY day, outcome
            ORDER BY day
            """);
        stats.put("outcomes", outcomesResult);

        // Perk popularity
        var perksResult = executeQuery("""
            SELECT
                perk_name,
                COUNT(*) as picks
            FROM endurance_perks
            WHERE event_type = 'selected'
              AND ts >= NOW() - INTERVAL '""" + interval + """
            '
            GROUP BY perk_name
            ORDER BY picks DESC
            LIMIT 10
            """);
        stats.put("perks", perksResult);

        return gson.toJson(stats);
    }

    private String handleDungeonAnalytics(HttpExchange exchange) {
        String interval = getTimeInterval(exchange);
        Map<String, Object> stats = new HashMap<>();

        // Overall stats
        var overallResult = executeQuery("""
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
        var byDungeonResult = executeQuery("""
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

    private String handleRoomAnalytics(HttpExchange exchange) {
        String interval = getTimeInterval(exchange);
        Map<String, Object> stats = new HashMap<>();

        // Room visits
        var visitsResult = executeQuery("""
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
        var deathsResult = executeQuery("""
            SELECT
                COALESCE(room, 'unknown') as room,
                COUNT(*) as deaths
            FROM combat_deaths
            WHERE ts >= NOW() - INTERVAL '""" + interval + """
            '
            GROUP BY room
            ORDER BY deaths DESC
            LIMIT 15
            """);
        stats.put("deaths", deathsResult);

        return gson.toJson(stats);
    }

    private String handleLootRatesAnalytics(HttpExchange exchange) {
        String interval = getTimeInterval(exchange);
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
        return gson.toJson(executeQuery(sql));
    }

    // ========== Advanced Analytics v2 Handlers ==========

    /**
     * Real DPS timeline - damage per second over time buckets
     */
    private String handleDpsTimeline(HttpExchange exchange) {
        String interval = getTimeInterval(exchange);
        Map<String, String> params = parseQueryParams(exchange);
        String player = params.get("player");

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
            GROUP BY time_bucket
            ORDER BY time_bucket
            """.formatted(bucket, bucketSeconds, interval, playerFilter);
        return gson.toJson(executeQuery(sql));
    }

    /**
     * Detailed stats for a specific player
     */
    private String handlePlayerStats(HttpExchange exchange) {
        String interval = getTimeInterval(exchange);
        Map<String, String> params = parseQueryParams(exchange);
        String player = params.get("player");

        if (player == null || player.isBlank()) {
            return gson.toJson(Map.of("error", "Missing 'player' parameter"));
        }

        Map<String, Object> stats = new HashMap<>();

        // Combat stats as attacker
        var attackerStats = executeQuery("""
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
            """.formatted(player, interval));
        if (!attackerStats.isEmpty()) {
            stats.put("combat", attackerStats.get(0));
        }

        // Damage taken
        var damageTaken = executeQuery("""
            SELECT
                COUNT(*) as times_hit,
                ROUND(SUM(damage), 1) as total_damage_taken,
                ROUND(AVG(damage), 2) as avg_damage_taken,
                COUNT(DISTINCT attacker_name) as unique_attackers
            FROM combat_hits
            WHERE target_name = '%s'
              AND ts >= NOW() - INTERVAL '%s'
            """.formatted(player, interval));
        if (!damageTaken.isEmpty()) {
            stats.put("damageTaken", damageTaken.get(0));
        }

        // Deaths
        var deaths = executeQuery("""
            SELECT
                COUNT(*) as total_deaths,
                COUNT(DISTINCT cause) as death_types
            FROM combat_deaths
            WHERE target_name = '%s'
              AND ts >= NOW() - INTERVAL '%s'
            """.formatted(player, interval));
        if (!deaths.isEmpty()) {
            stats.put("deaths", deaths.get(0));
        }

        // Kills
        var kills = executeQuery("""
            SELECT COUNT(*) as total_kills
            FROM economy_mob_kills
            WHERE player_name = '%s'
              AND ts >= NOW() - INTERVAL '%s'
            """.formatted(player, interval));
        if (!kills.isEmpty()) {
            stats.put("kills", kills.get(0));
        }

        // Weapon breakdown
        var weapons = executeQuery("""
            SELECT
                COALESCE(NULLIF(JSON_EXTRACT_STRING(attacker_state, '$.mainHand'), ''), 'fist') as weapon,
                COUNT(*) as hits,
                ROUND(SUM(damage), 1) as damage,
                ROUND(AVG(damage), 2) as avg_damage
            FROM combat_hits
            WHERE attacker_name = '%s'
              AND ts >= NOW() - INTERVAL '%s'
              AND NOT is_miss
            GROUP BY weapon
            ORDER BY damage DESC
            LIMIT 10
            """.formatted(player, interval));
        stats.put("weapons", weapons);

        // DPS over time
        var dpsTimeline = executeQuery("""
            SELECT
                DATE_TRUNC('minute', ts) as time_bucket,
                ROUND(SUM(damage) / 60.0, 2) as dps
            FROM combat_hits
            WHERE attacker_name = '%s'
              AND ts >= NOW() - INTERVAL '%s'
              AND NOT is_miss
            GROUP BY time_bucket
            ORDER BY time_bucket
            LIMIT 60
            """.formatted(player, interval));
        stats.put("dpsTimeline", dpsTimeline);

        return gson.toJson(stats);
    }

    /**
     * Compare multiple players side by side
     */
    private String handlePlayerComparison(HttpExchange exchange) {
        String interval = getTimeInterval(exchange);
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
            GROUP BY attacker_name
            HAVING COUNT(*) >= 10
            ORDER BY total_damage DESC
            LIMIT 20
            """.formatted(interval);
        return gson.toJson(executeQuery(sql));
    }

    /**
     * Trend analysis - compare current period vs previous period
     */
    private String handleTrends(HttpExchange exchange) {
        String interval = getTimeInterval(exchange);
        Map<String, Object> trends = new HashMap<>();

        // Current period stats
        var current = executeQuery("""
            SELECT
                COUNT(*) as hits,
                ROUND(SUM(damage), 1) as damage,
                COUNT(DISTINCT attacker_name) as players,
                ROUND(AVG(damage), 2) as avg_damage
            FROM combat_hits
            WHERE ts >= NOW() - INTERVAL '%s'
            """.formatted(interval));

        // Previous period stats (same duration, before current)
        var previous = executeQuery("""
            SELECT
                COUNT(*) as hits,
                ROUND(SUM(damage), 1) as damage,
                COUNT(DISTINCT attacker_name) as players,
                ROUND(AVG(damage), 2) as avg_damage
            FROM combat_hits
            WHERE ts >= NOW() - INTERVAL '%s' - INTERVAL '%s'
              AND ts < NOW() - INTERVAL '%s'
            """.formatted(interval, interval, interval));

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
        var currentDeaths = executeQuery("SELECT COUNT(*) as deaths FROM combat_deaths WHERE ts >= NOW() - INTERVAL '" + interval + "'");
        var previousDeaths = executeQuery("SELECT COUNT(*) as deaths FROM combat_deaths WHERE ts >= NOW() - INTERVAL '" + interval + "' - INTERVAL '" + interval + "' AND ts < NOW() - INTERVAL '" + interval + "'");

        if (!currentDeaths.isEmpty()) trends.put("currentDeaths", currentDeaths.get(0).get("deaths"));
        if (!previousDeaths.isEmpty()) trends.put("previousDeaths", previousDeaths.get(0).get("deaths"));
        if (!currentDeaths.isEmpty() && !previousDeaths.isEmpty()) {
            trends.put("deathsChange", calculateDelta(currentDeaths.get(0).get("deaths"), previousDeaths.get(0).get("deaths")));
        }

        // Kills trends
        var currentKills = executeQuery("SELECT COUNT(*) as kills FROM economy_mob_kills WHERE ts >= NOW() - INTERVAL '" + interval + "'");
        var previousKills = executeQuery("SELECT COUNT(*) as kills FROM economy_mob_kills WHERE ts >= NOW() - INTERVAL '" + interval + "' - INTERVAL '" + interval + "' AND ts < NOW() - INTERVAL '" + interval + "'");

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
    private String handlePerformanceAnalytics(HttpExchange exchange) {
        String interval = getTimeInterval(exchange);
        Map<String, Object> perf = new HashMap<>();

        // TPS timeline
        var tpsTimeline = executeQuery("""
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
        var memTimeline = executeQuery("""
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
        var entityTimeline = executeQuery("""
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
        var summary = executeQuery("""
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
    private String handleFightAnalysis(HttpExchange exchange) {
        String interval = getTimeInterval(exchange);
        Map<String, Object> analysis = new HashMap<>();

        // Recent fights with details
        var fights = executeQuery("""
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
            ORDER BY start_ts DESC
            LIMIT 50
            """.formatted(interval));
        analysis.put("fights", fights);

        // Aggregated fight stats
        var stats = executeQuery("""
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
            """.formatted(interval));
        if (!stats.isEmpty()) {
            analysis.put("stats", stats.get(0));
        }

        return gson.toJson(analysis);
    }

    /**
     * Damage taken analysis - who/what is hurting players
     */
    private String handleDamageTaken(HttpExchange exchange) {
        String interval = getTimeInterval(exchange);
        Map<String, String> params = parseQueryParams(exchange);
        String player = params.get("player");

        String playerFilter = (player != null && !player.isBlank())
            ? " AND target_name = '" + player + "'"
            : "";

        Map<String, Object> analysis = new HashMap<>();

        // Damage by source type
        var bySource = executeQuery("""
            SELECT
                COALESCE(REPLACE(attacker_type, 'entity.', ''), 'environment') as source,
                COUNT(*) as hits,
                ROUND(SUM(damage), 1) as total_damage,
                ROUND(AVG(damage), 2) as avg_damage
            FROM combat_hits
            WHERE ts >= NOW() - INTERVAL '%s'
              AND target_name IS NOT NULL
              %s
            GROUP BY source
            ORDER BY total_damage DESC
            LIMIT 15
            """.formatted(interval, playerFilter));
        analysis.put("bySource", bySource);

        // Damage by type
        var byType = executeQuery("""
            SELECT
                COALESCE(damage_type, 'unknown') as damage_type,
                COUNT(*) as hits,
                ROUND(SUM(damage), 1) as total_damage
            FROM combat_hits
            WHERE ts >= NOW() - INTERVAL '%s'
              AND target_name IS NOT NULL
              %s
            GROUP BY damage_type
            ORDER BY total_damage DESC
            """.formatted(interval, playerFilter));
        analysis.put("byType", byType);

        // Damage timeline
        String bucket = interval.contains("hour") ? "minute" : "hour";
        var timeline = executeQuery("""
            SELECT
                DATE_TRUNC('%s', ts) as time_bucket,
                ROUND(SUM(damage), 1) as damage_taken,
                COUNT(*) as hits_taken
            FROM combat_hits
            WHERE ts >= NOW() - INTERVAL '%s'
              AND target_name IS NOT NULL
              %s
            GROUP BY time_bucket
            ORDER BY time_bucket
            """.formatted(bucket, interval, playerFilter));
        analysis.put("timeline", timeline);

        return gson.toJson(analysis);
    }

    /**
     * List of active players for filtering
     */
    private String handlePlayersList(HttpExchange exchange) {
        String interval = getTimeInterval(exchange);
        String sql = """
            SELECT DISTINCT
                attacker_name as player,
                COUNT(*) as activity
            FROM combat_hits
            WHERE ts >= NOW() - INTERVAL '%s'
              AND attacker_name IS NOT NULL
            GROUP BY attacker_name
            ORDER BY activity DESC
            LIMIT 50
            """.formatted(interval);
        return gson.toJson(executeQuery(sql));
    }

    private List<Map<String, Object>> getHeatmapData(String type, String room, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM spatial_heatmaps");
        List<String> conditions = new ArrayList<>();

        if (type != null && !type.isBlank()) {
            conditions.add("heatmap_type = '" + type + "'");
        }
        if (room != null && !room.isBlank()) {
            conditions.add("room = '" + room + "'");
        }

        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }

        sql.append(" ORDER BY ts DESC LIMIT ").append(limit);

        return executeQuery(sql.toString());
    }

    private List<Map<String, Object>> getTableList() {
        String sql = """
            SELECT table_name, estimated_size
            FROM duckdb_tables()
            WHERE schema_name = 'main'
            ORDER BY table_name
            """;
        return executeQuery(sql);
    }

    private List<Map<String, Object>> executeQuery(String sql) {
        List<Map<String, Object>> results = new ArrayList<>();

        if (!DuckDBTelemetryService.INSTANCE.isEnabled()) {
            return results;
        }

        try {
            Connection conn = DuckDBTelemetryService.INSTANCE.getConnection();
            if (conn == null) {
                return results;
            }

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();

                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        String colName = meta.getColumnLabel(i);
                        Object value = rs.getObject(i);
                        row.put(colName, value);
                    }
                    results.add(row);
                }
            }
        } catch (Exception e) {
            LOGGER.error("[Dashboard] Query error: {} - SQL: {}", e.getMessage(), sql);
        }

        return results;
    }

    private Map<String, String> parseQueryParams(HttpExchange exchange) {
        Map<String, String> params = new HashMap<>();
        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=", 2);
                if (pair.length == 2) {
                    params.put(pair[0], pair[1]);
                }
            }
        }
        return params;
    }

    private int getIntParam(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // ========== HTTP Handlers ==========

    @FunctionalInterface
    private interface RequestHandler {
        String handle(HttpExchange exchange) throws IOException;
    }

    private class ApiHandler implements HttpHandler {
        private final RequestHandler handler;

        ApiHandler(RequestHandler handler) {
            this.handler = handler;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // CORS headers
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
            exchange.getResponseHeaders().add("Content-Type", "application/json");

            // Handle OPTIONS preflight
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            String response;
            int statusCode = 200;
            try {
                response = handler.handle(exchange);
            } catch (Exception e) {
                LOGGER.error("[Dashboard] API error: {}", e.getMessage());
                response = gson.toJson(Map.of("error", e.getMessage()));
                statusCode = 500;
            }

            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private class StaticFileHandler implements HttpHandler {
        private final String resourceBase;

        StaticFileHandler(String resourceBase) {
            this.resourceBase = resourceBase;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();

            // Determine resource path
            String resourcePath;
            if (path.equals("/dashboard") || path.equals("/dashboard/")) {
                resourcePath = "/index.html";
            } else if (path.startsWith("/dashboard/")) {
                resourcePath = path.substring("/dashboard".length());
            } else {
                // Direct file request (e.g., /style.css, /app.js)
                resourcePath = path;
            }

            if (resourcePath.isEmpty()) {
                resourcePath = "/index.html";
            }

            String fullResourcePath = resourceBase + resourcePath;
            String contentType = getContentType(resourcePath);
            exchange.getResponseHeaders().add("Content-Type", contentType);

            try (InputStream is = getClass().getResourceAsStream(fullResourcePath)) {
                if (is == null) {
                    String notFound = "File not found: " + fullResourcePath;
                    exchange.sendResponseHeaders(404, notFound.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(notFound.getBytes(StandardCharsets.UTF_8));
                    }
                    return;
                }

                byte[] bytes = is.readAllBytes();
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
        }

        private String getContentType(String path) {
            if (path.endsWith(".html")) return "text/html";
            if (path.endsWith(".css")) return "text/css";
            if (path.endsWith(".js")) return "application/javascript";
            if (path.endsWith(".json")) return "application/json";
            if (path.endsWith(".png")) return "image/png";
            if (path.endsWith(".ico")) return "image/x-icon";
            return "text/plain";
        }
    }
}
