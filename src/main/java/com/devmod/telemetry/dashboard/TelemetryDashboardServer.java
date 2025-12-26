package com.devmod.telemetry.dashboard;

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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;

import com.devmod.arena.dashboard.ArenaDashboardEndpoint;
import com.devmod.telemetry.duckdb.DuckDBTelemetryService;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

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

    // Delegate for analytics handlers
    private TelemetryAnalyticsHandlers analyticsHandlers;
    private ArenaDashboardEndpoint arenaEndpoint;

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
            // Initialize delegate for analytics handlers
            this.analyticsHandlers = new TelemetryAnalyticsHandlers(this, gson);
            initializeArenaDashboard();

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
            server.createContext("/api/endurance/performance", new ApiHandler(this::handleEndurancePerformance));
            server.createContext("/api/player/snapshots", new ApiHandler(this::handlePlayerSnapshots));
            server.createContext("/api/player/abilities", new ApiHandler(this::handlePlayerAbilities));
            server.createContext("/api/spatial/heatmaps", new ApiHandler(this::handleSpatialHeatmaps));
            server.createContext("/api/spatial/transitions", new ApiHandler(this::handleSpatialTransitions));
            server.createContext("/api/economy/drops", new ApiHandler(this::handleEconomyDrops));
            server.createContext("/api/economy/kills", new ApiHandler(this::handleEconomyKills));
            server.createContext("/api/dungeons/runs", new ApiHandler(this::handleDungeonRuns));
            server.createContext("/api/performance", new ApiHandler(this::handlePerformance));

            // API Routes - Analytics (delegated to TelemetryAnalyticsHandlers)
            server.createContext("/api/analytics/overview", new ApiHandler(analyticsHandlers::handleAnalyticsOverview));
            server.createContext("/api/analytics/hits-timeline", new ApiHandler(analyticsHandlers::handleHitsTimeline));
            server.createContext("/api/analytics/damage-by-bodypart", new ApiHandler(analyticsHandlers::handleDamageByBodypart));
            server.createContext("/api/analytics/damage-by-type", new ApiHandler(analyticsHandlers::handleDamageByType));
            server.createContext("/api/analytics/weapon-stats", new ApiHandler(analyticsHandlers::handleWeaponAnalytics));
            server.createContext("/api/analytics/mob-kills", new ApiHandler(analyticsHandlers::handleMobKillsAnalytics));
            server.createContext("/api/analytics/ttk", new ApiHandler(analyticsHandlers::handleTTKAnalytics));
            server.createContext("/api/analytics/accuracy-timeline", new ApiHandler(analyticsHandlers::handleAccuracyTimeline));
            server.createContext("/api/analytics/endurance-stats", new ApiHandler(analyticsHandlers::handleEnduranceAnalytics));
            server.createContext("/api/analytics/dungeon-stats", new ApiHandler(analyticsHandlers::handleDungeonAnalytics));
            server.createContext("/api/analytics/room-stats", new ApiHandler(analyticsHandlers::handleRoomAnalytics));
            server.createContext("/api/analytics/loot-rates", new ApiHandler(analyticsHandlers::handleLootRatesAnalytics));

            // API Routes - Advanced Analytics v2 (delegated to TelemetryAnalyticsHandlers)
            server.createContext("/api/analytics/dps-timeline", new ApiHandler(analyticsHandlers::handleDpsTimeline));
            server.createContext("/api/analytics/player-stats", new ApiHandler(analyticsHandlers::handlePlayerStats));
            server.createContext("/api/analytics/player-comparison", new ApiHandler(analyticsHandlers::handlePlayerComparison));
            server.createContext("/api/analytics/trends", new ApiHandler(analyticsHandlers::handleTrends));
            server.createContext("/api/analytics/performance", new ApiHandler(analyticsHandlers::handlePerformanceAnalytics));
            server.createContext("/api/analytics/fight-analysis", new ApiHandler(analyticsHandlers::handleFightAnalysis));
            server.createContext("/api/analytics/damage-taken", new ApiHandler(analyticsHandlers::handleDamageTaken));
            server.createContext("/api/analytics/players-list", new ApiHandler(analyticsHandlers::handlePlayersList));

            // Arena analytics (DD36) + auth
            server.createContext("/api/arena/token", new ApiHandler(this::handleArenaToken));
            server.createContext("/api/analytics/arena/templates", new ApiHandler(this::handleArenaTemplates));
            server.createContext("/api/analytics/arena/build-metrics", new ApiHandler(this::handleArenaBuildMetrics));
            server.createContext("/api/analytics/arena/performance", new ApiHandler(this::handleArenaPerformance));
            server.createContext("/api/analytics/arena/spawn-heatmap", new ApiHandler(this::handleArenaSpawnHeatmap));
            server.createContext("/api/analytics/arena/death-heatmap", new ApiHandler(this::handleArenaDeathHeatmap));
            server.createContext("/api/analytics/arena/wave-correlation", new ApiHandler(this::handleArenaWaveCorrelation));
            server.createContext("/api/analytics/arena/templates-failure-rate", new ApiHandler(this::handleArenaTemplatesFailureRate));
            server.createContext("/api/export/arena/build-metrics", new ApiHandler(this::handleArenaExportBuildMetrics));
            server.createContext("/api/export/arena/performance", new ApiHandler(this::handleArenaExportPerformance));
            server.createContext("/api/export/arena/wave-correlation", new ApiHandler(this::handleArenaExportWaveCorrelation));

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
            if (arenaEndpoint != null) {
                try { arenaEndpoint.close(); } catch (Exception ignored) {}
                arenaEndpoint = null;
            }
            running.set(false);
            LOGGER.info("[Dashboard] Server stopped");
        } catch (Exception e) {
            LOGGER.error("[Dashboard] Error stopping server: {}", e.getMessage(), e);
        }
    }

    private void initializeArenaDashboard() {
        arenaEndpoint = ArenaDashboardEndpoint.getInstance();
        // Arena analytics now uses DuckDBTelemetryService.INSTANCE via QueryAPI
        if (DuckDBTelemetryService.INSTANCE.getDbPath() == null) {
            LOGGER.warn("[Dashboard] DuckDB path unavailable; arena analytics disabled");
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
        Map<String, String> filters = buildArenaFilters(params);
        return gson.toJson(queryTableWithFilters("combat_hits", params.get("from"), params.get("to"), limit, "ts", filters));
    }

    private String handleCombatDeaths(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        int limit = getIntParam(params.get("limit"), 500);
        Map<String, String> filters = buildArenaFilters(params);
        return gson.toJson(queryTableWithFilters("combat_deaths", params.get("from"), params.get("to"), limit, "ts", filters));
    }

    private String handleCombatFights(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        int limit = getIntParam(params.get("limit"), 100);
        Map<String, String> filters = buildArenaFilters(params);
        return gson.toJson(queryTableWithFilters("combat_fights", params.get("from"), params.get("to"), limit, "start_ts", filters));
    }

    private String handleCombatWeapons(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        Map<String, String> filters = buildArenaFilters(params);
        return gson.toJson(getWeaponStats(filters, params.get("from"), params.get("to")));
    }

    private String handleEnduranceSessions(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        int limit = getIntParam(params.get("limit"), 100);
        Map<String, String> filters = buildArenaFilters(params);
        return gson.toJson(queryTableWithFilters("endurance_sessions", params.get("from"), params.get("to"),
            limit, "start_ts", filters));
    }

    private String handleEnduranceWaves(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        int limit = getIntParam(params.get("limit"), 500);
        String sessionId = params.get("session_id");
        if (sessionId != null) {
            return gson.toJson(queryWithFilter("endurance_waves", "session_id", sessionId, limit));
        }
        Map<String, String> filters = buildArenaFilters(params);
        return gson.toJson(queryTableWithFilters("endurance_waves", null, null, limit, "ts", filters));
    }

    private String handleEndurancePerks(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        String templateId = params.get("templateId");
        if (templateId != null && !templateId.isBlank()) {
            Map<String, String> filters = buildArenaFilters(params);
            return gson.toJson(queryTableWithFilters("endurance_perks", params.get("from"), params.get("to"), 500, "ts", filters));
        }
        return gson.toJson(getPerkStats());
    }

    private String handleEndurancePerformance(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        int limit = getIntParam(params.get("limit"), 200);
        Map<String, String> filters = buildArenaFilters(params);
        return gson.toJson(queryTableWithFilters("endurance_performance", params.get("from"), params.get("to"),
            limit, "ts", filters));
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
        String sql = "SELECT mob_type, COUNT(*) as total_kills, " +
            "SUM(CASE WHEN had_loot THEN 1 ELSE 0 END) as kills_with_loot, " +
            "ROUND(100.0 * SUM(CASE WHEN had_loot THEN 1 ELSE 0 END) / COUNT(*), 1) as loot_rate_pct, " +
            "MIN(ts) as first_kill, MAX(ts) as last_kill " +
            "FROM economy_mob_kills GROUP BY mob_type ORDER BY total_kills DESC LIMIT " + limit;
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

    private String handleArenaToken(HttpExchange exchange) {
        if (arenaEndpoint == null) {
            throw new IllegalStateException("Arena analytics not initialized");
        }
        Map<String, String> params = parseQueryParams(exchange);
        String userId = params.getOrDefault("user", "local");
        boolean full = "true".equalsIgnoreCase(params.get("full"));
        ArenaDashboardEndpoint.TokenPermissions permissions = full
            ? ArenaDashboardEndpoint.TokenPermissions.full()
            : ArenaDashboardEndpoint.TokenPermissions.readOnly();
        String token = arenaEndpoint.generateToken(userId, permissions);
        return gson.toJson(Map.of(
            "token", token,
            "userId", userId,
            "permissions", permissions,
            "expiresInSeconds", 24 * 60 * 60
        ));
    }

    private String handleArenaTemplates(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        ArenaDashboardEndpoint.TokenInfo token = requireArenaToken(exchange, params);
        return gson.toJson(arenaEndpoint.handleTemplates(token));
    }

    private String handleArenaBuildMetrics(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        ArenaDashboardEndpoint.TokenInfo token = requireArenaToken(exchange, params);
        ArenaDashboardEndpoint.AnalyticsQueryParams query = buildArenaParams(params, true);
        return gson.toJson(arenaEndpoint.handleBuildMetrics(token, query));
    }

    private String handleArenaPerformance(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        ArenaDashboardEndpoint.TokenInfo token = requireArenaToken(exchange, params);
        ArenaDashboardEndpoint.AnalyticsQueryParams query = buildArenaParams(params, true);
        return gson.toJson(arenaEndpoint.handlePerformance(token, query));
    }

    private String handleArenaSpawnHeatmap(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        ArenaDashboardEndpoint.TokenInfo token = requireArenaToken(exchange, params);
        ArenaDashboardEndpoint.AnalyticsQueryParams query = buildArenaParams(params, true);
        return gson.toJson(arenaEndpoint.handleSpawnHeatmap(token, query));
    }

    private String handleArenaDeathHeatmap(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        ArenaDashboardEndpoint.TokenInfo token = requireArenaToken(exchange, params);
        ArenaDashboardEndpoint.AnalyticsQueryParams query = buildArenaParams(params, true);
        return gson.toJson(arenaEndpoint.handleDeathHeatmap(token, query));
    }

    private String handleArenaWaveCorrelation(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        ArenaDashboardEndpoint.TokenInfo token = requireArenaToken(exchange, params);
        ArenaDashboardEndpoint.AnalyticsQueryParams query = buildArenaParams(params, true);
        return gson.toJson(arenaEndpoint.handleWaveCorrelation(token, query));
    }

    private String handleArenaTemplatesFailureRate(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        ArenaDashboardEndpoint.TokenInfo token = requireArenaToken(exchange, params);
        return gson.toJson(arenaEndpoint.handleTemplatesFailureRate(token));
    }

    private String handleArenaExportBuildMetrics(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        ArenaDashboardEndpoint.TokenInfo token = requireArenaToken(exchange, params);
        ArenaDashboardEndpoint.AnalyticsQueryParams query = buildArenaParams(params, true);
        ArenaDashboardEndpoint.ExportFormat format = parseExportFormat(params.get("format"));
        return gson.toJson(arenaEndpoint.handleExportBuildMetrics(token, query, format));
    }

    private String handleArenaExportPerformance(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        ArenaDashboardEndpoint.TokenInfo token = requireArenaToken(exchange, params);
        ArenaDashboardEndpoint.AnalyticsQueryParams query = buildArenaParams(params, true);
        ArenaDashboardEndpoint.ExportFormat format = parseExportFormat(params.get("format"));
        return gson.toJson(arenaEndpoint.handleExportPerformance(token, query, format));
    }

    private String handleArenaExportWaveCorrelation(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        ArenaDashboardEndpoint.TokenInfo token = requireArenaToken(exchange, params);
        ArenaDashboardEndpoint.AnalyticsQueryParams query = buildArenaParams(params, true);
        ArenaDashboardEndpoint.ExportFormat format = parseExportFormat(params.get("format"));
        return gson.toJson(arenaEndpoint.handleExportWaveCorrelation(token, query, format));
    }

    private static final java.lang.reflect.Type STRING_MAP_TYPE =
        new com.google.gson.reflect.TypeToken<Map<String, String>>() {}.getType();

    private String handleQuery(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            return gson.toJson(Map.of("error", "POST method required"));
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        Map<String, String> request = gson.fromJson(body, STRING_MAP_TYPE);
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

    private ArenaDashboardEndpoint.TokenInfo requireArenaToken(HttpExchange exchange, Map<String, String> params) {
        if (arenaEndpoint == null) {
            throw new IllegalStateException("Arena analytics not initialized");
        }
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if ((authHeader == null || authHeader.isBlank()) && params != null) {
            String tokenParam = params.get("token");
            if (tokenParam != null && !tokenParam.isBlank()) {
                authHeader = "Bearer " + tokenParam.trim();
            }
        }
        Optional<ArenaDashboardEndpoint.TokenInfo> token = arenaEndpoint.authenticate(authHeader);
        if (token.isEmpty()) {
            throw new SecurityException("Unauthorized");
        }
        if (!arenaEndpoint.checkRateLimit(token.get().token())) {
            throw new SecurityException("Rate limit exceeded");
        }
        return token.get();
    }

    private ArenaDashboardEndpoint.AnalyticsQueryParams buildArenaParams(Map<String, String> params, boolean requireTemplateId) {
        String templateId = params.getOrDefault("templateId", "").trim();
        if (requireTemplateId && templateId.isEmpty()) {
            throw new IllegalArgumentException("Missing templateId");
        }
        Integer templateVersion = null;
        String templateVersionParam = params.get("templateVersion");
        if (templateVersionParam != null && !templateVersionParam.isBlank()) {
            try {
                templateVersion = Integer.parseInt(templateVersionParam.trim());
            } catch (NumberFormatException ignored) {
                templateVersion = null;
            }
        }

        Instant now = Instant.now();
        Instant from = parseInstantParam(params.get("from"));
        Instant to = parseInstantParam(params.get("to"));
        if (from == null || to == null) {
            Duration rangeDuration = parseRangeDuration(params.getOrDefault("range", "7d"));
            from = now.minus(rangeDuration);
            to = now;
        }

        int page = getIntParam(params.get("page"), 0);
        int pageSize = getIntParam(params.get("pageSize"), 100);

        return new ArenaDashboardEndpoint.AnalyticsQueryParams(templateId, templateVersion, from, to, page, pageSize);
    }

    private Instant parseInstantParam(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            if (trimmed.matches("\\d+")) {
                return Instant.ofEpochMilli(Long.parseLong(trimmed));
            }
            return Instant.parse(trimmed);
        } catch (Exception e) {
            return null;
        }
    }

    private Duration parseRangeDuration(String range) {
        return switch (range) {
            case "1h" -> Duration.ofHours(1);
            case "6h" -> Duration.ofHours(6);
            case "24h" -> Duration.ofHours(24);
            case "7d" -> Duration.ofDays(7);
            case "all" -> Duration.ofDays(36500);
            default -> Duration.ofDays(7);
        };
    }

    private ArenaDashboardEndpoint.ExportFormat parseExportFormat(String value) {
        if (value == null || value.isBlank()) {
            return ArenaDashboardEndpoint.ExportFormat.JSON;
        }
        try {
            return ArenaDashboardEndpoint.ExportFormat.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ArenaDashboardEndpoint.ExportFormat.JSON;
        }
    }

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
            "endurance_sessions", "endurance_waves", "endurance_perks", "endurance_performance",
            "player_snapshots", "player_abilities",
            "spatial_heatmaps", "spatial_room_transitions",
            "arena_spatial_events",
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

    private List<Map<String, Object>> queryTableWithFilters(String table, String from, String to, int limit,
                                                            String tsColumn, Map<String, String> filters) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(table);
        List<String> conditions = new ArrayList<>();

        if (from != null && !from.isBlank()) {
            conditions.add(tsColumn + " >= '" + escapeSql(from) + "'");
        }
        if (to != null && !to.isBlank()) {
            conditions.add(tsColumn + " <= '" + escapeSql(to) + "'");
        }

        if (filters != null) {
            for (Map.Entry<String, String> entry : filters.entrySet()) {
                if (entry.getValue() == null || entry.getValue().isBlank()) {
                    continue;
                }
                String column = entry.getKey();
                String value = entry.getValue().trim();
                if (value.matches("^-?\\d+$")) {
                    conditions.add(column + " = " + value);
                } else {
                    conditions.add(column + " = '" + escapeSql(value) + "'");
                }
            }
        }

        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }

        sql.append(" ORDER BY ").append(tsColumn).append(" DESC LIMIT ").append(limit);
        return executeQuery(sql.toString());
    }

    private Map<String, String> buildArenaFilters(Map<String, String> params) {
        Map<String, String> filters = new HashMap<>();
        if (params == null) {
            return filters;
        }
        String templateId = params.get("templateId");
        String templateVersion = params.get("templateVersion");
        String policyId = params.get("policyId");
        String policyVersion = params.get("policyVersion");
        String arenaId = params.get("arenaId");

        if (templateId != null) {
            filters.put("template_id", templateId);
        }
        if (templateVersion != null) {
            filters.put("template_version", templateVersion);
        }
        if (policyId != null) {
            filters.put("policy_id", policyId);
        }
        if (policyVersion != null) {
            filters.put("policy_version", policyVersion);
        }
        if (arenaId != null) {
            filters.put("arena_id", arenaId);
        }
        return filters;
    }

    private String escapeSql(String value) {
        return value.replace("'", "''");
    }

    private List<Map<String, Object>> queryWithFilter(String table, String column, String value, int limit) {
        String sql = "SELECT * FROM " + table + " WHERE " + column + " = '" + value +
                     "' ORDER BY ts DESC LIMIT " + limit;
        return executeQuery(sql);
    }

    private List<Map<String, Object>> getWeaponStats(Map<String, String> filters, String from, String to) {
        StringBuilder sql = new StringBuilder("""
            SELECT
                COALESCE(NULLIF(JSON_EXTRACT_STRING(attacker_state, '$.mainHand'), ''), 'fist') as weapon,
                COUNT(*) as hit_count,
                ROUND(SUM(damage), 1) as total_damage,
                ROUND(AVG(damage), 2) as avg_damage,
                SUM(CASE WHEN is_miss THEN 1 ELSE 0 END) as misses
            FROM combat_hits
            """);
        List<String> conditions = new ArrayList<>();

        if (from != null && !from.isBlank()) {
            conditions.add("ts >= '" + escapeSql(from) + "'");
        }
        if (to != null && !to.isBlank()) {
            conditions.add("ts <= '" + escapeSql(to) + "'");
        }
        if (from == null && to == null) {
            conditions.add("ts >= NOW() - INTERVAL '1 hour'");
        }

        if (filters != null) {
            for (Map.Entry<String, String> entry : filters.entrySet()) {
                if (entry.getValue() == null || entry.getValue().isBlank()) {
                    continue;
                }
                String column = entry.getKey();
                String value = entry.getValue().trim();
                if (value.matches("^-?\\d+$")) {
                    conditions.add(column + " = " + value);
                } else {
                    conditions.add(column + " = '" + escapeSql(value) + "'");
                }
            }
        }

        conditions.add("attacker_name IS NOT NULL");

        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }

        sql.append("""
            GROUP BY weapon
            ORDER BY total_damage DESC
            LIMIT 20
            """);
        return executeQuery(sql.toString());
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

    public String getTimeInterval(HttpExchange exchange) {
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

    public List<Map<String, Object>> executeQuery(String sql) {
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

    public Map<String, String> parseQueryParams(HttpExchange exchange) {
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
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
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
