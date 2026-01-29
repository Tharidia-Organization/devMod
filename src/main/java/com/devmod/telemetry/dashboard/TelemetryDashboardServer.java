package com.devmod.telemetry.dashboard;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.google.common.base.Splitter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import com.devmod.arena.dashboard.ArenaDashboardEndpoint;
import com.devmod.telemetry.duckdb.DuckDBTelemetryService;

public class TelemetryDashboardServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Splitter AMPERSAND_SPLITTER = Splitter.on('&');
    public static final TelemetryDashboardServer INSTANCE = new TelemetryDashboardServer();

    public record SqlParam(@Nullable Object value, int sqlType) {}

    private static final int DEFAULT_PORT = 8642;
    private static final String BIND_ADDRESS = "127.0.0.1"; // localhost only for security

    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    private final AtomicBoolean running = new AtomicBoolean(false);
    @Nullable
    private HttpServer server;
    private int port = DEFAULT_PORT;

    // Delegate for analytics handlers
    @Nullable
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
            TelemetryAnalyticsHandlers handlers = new TelemetryAnalyticsHandlers(this, gson);
            initializeArenaDashboard();

            HttpServer httpServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
            server = httpServer;
            HttpServer activeServer = Objects.requireNonNull(server, "server");
            activeServer.setExecutor(Executors.newFixedThreadPool(4));

            // API Routes - Basic
            activeServer.createContext("/api/health", new ApiHandler(this::handleHealth));
            activeServer.createContext("/api/summary", new ApiHandler(this::handleSummary));
            activeServer.createContext("/api/tables", new ApiHandler(this::handleTables));
            activeServer.createContext("/api/query", new ApiHandler(this::handleQuery));

            // API Routes - Raw Data
            activeServer.createContext("/api/combat/hits", new ApiHandler(this::handleCombatHits));
            activeServer.createContext("/api/combat/deaths", new ApiHandler(this::handleCombatDeaths));
            activeServer.createContext("/api/combat/fights", new ApiHandler(this::handleCombatFights));
            activeServer.createContext("/api/combat/weapons", new ApiHandler(this::handleCombatWeapons));
            activeServer.createContext("/api/endurance/sessions", new ApiHandler(this::handleEnduranceSessions));
            activeServer.createContext("/api/endurance/waves", new ApiHandler(this::handleEnduranceWaves));
            activeServer.createContext("/api/endurance/perks", new ApiHandler(this::handleEndurancePerks));
            activeServer.createContext("/api/endurance/performance", new ApiHandler(this::handleEndurancePerformance));
            activeServer.createContext("/api/endurance/leaderboard", new ApiHandler(this::handleLeaderboard));
            activeServer.createContext("/api/endurance/leaderboard/categories", new ApiHandler(this::handleLeaderboardCategories));
            activeServer.createContext("/api/player/snapshots", new ApiHandler(this::handlePlayerSnapshots));
            activeServer.createContext("/api/player/abilities", new ApiHandler(this::handlePlayerAbilities));
            activeServer.createContext("/api/spatial/heatmaps", new ApiHandler(this::handleSpatialHeatmaps));
            activeServer.createContext("/api/spatial/transitions", new ApiHandler(this::handleSpatialTransitions));
            activeServer.createContext("/api/economy/drops", new ApiHandler(this::handleEconomyDrops));
            activeServer.createContext("/api/economy/kills", new ApiHandler(this::handleEconomyKills));
            activeServer.createContext("/api/dungeons/runs", new ApiHandler(this::handleDungeonRuns));
            activeServer.createContext("/api/performance", new ApiHandler(this::handlePerformance));

            // API Routes - Analytics (delegated to TelemetryAnalyticsHandlers)
            activeServer.createContext("/api/analytics/overview", new ApiHandler(handlers::handleAnalyticsOverview));
            activeServer.createContext("/api/analytics/hits-timeline", new ApiHandler(handlers::handleHitsTimeline));
            activeServer.createContext("/api/analytics/damage-by-bodypart", new ApiHandler(handlers::handleDamageByBodypart));
            activeServer.createContext("/api/analytics/damage-by-type", new ApiHandler(handlers::handleDamageByType));
            activeServer.createContext("/api/analytics/weapon-stats", new ApiHandler(handlers::handleWeaponAnalytics));
            activeServer.createContext("/api/analytics/mob-kills", new ApiHandler(handlers::handleMobKillsAnalytics));
            activeServer.createContext("/api/analytics/ttk", new ApiHandler(handlers::handleTTKAnalytics));
            activeServer.createContext("/api/analytics/accuracy-timeline", new ApiHandler(handlers::handleAccuracyTimeline));
            activeServer.createContext("/api/analytics/endurance-stats", new ApiHandler(handlers::handleEnduranceAnalytics));
            activeServer.createContext("/api/analytics/dungeon-stats", new ApiHandler(handlers::handleDungeonAnalytics));
            activeServer.createContext("/api/analytics/room-stats", new ApiHandler(handlers::handleRoomAnalytics));
            activeServer.createContext("/api/analytics/loot-rates", new ApiHandler(handlers::handleLootRatesAnalytics));

            // API Routes - Advanced Analytics v2 (delegated to TelemetryAnalyticsHandlers)
            activeServer.createContext("/api/analytics/dps-timeline", new ApiHandler(handlers::handleDpsTimeline));
            activeServer.createContext("/api/analytics/player-stats", new ApiHandler(handlers::handlePlayerStats));
            activeServer.createContext("/api/analytics/player-comparison", new ApiHandler(handlers::handlePlayerComparison));
            activeServer.createContext("/api/analytics/trends", new ApiHandler(handlers::handleTrends));
            activeServer.createContext("/api/analytics/performance", new ApiHandler(handlers::handlePerformanceAnalytics));
            activeServer.createContext("/api/analytics/fight-analysis", new ApiHandler(handlers::handleFightAnalysis));
            activeServer.createContext("/api/analytics/damage-taken", new ApiHandler(handlers::handleDamageTaken));
            activeServer.createContext("/api/analytics/players-list", new ApiHandler(handlers::handlePlayersList));

            // Arena analytics (DD36) + auth
            activeServer.createContext("/api/arena/token", new ApiHandler(this::handleArenaToken));
            activeServer.createContext("/api/analytics/arena/templates", new ApiHandler(this::handleArenaTemplates));
            activeServer.createContext("/api/analytics/arena/build-metrics", new ApiHandler(this::handleArenaBuildMetrics));
            activeServer.createContext("/api/analytics/arena/performance", new ApiHandler(this::handleArenaPerformance));
            activeServer.createContext("/api/analytics/arena/spawn-heatmap", new ApiHandler(this::handleArenaSpawnHeatmap));
            activeServer.createContext("/api/analytics/arena/death-heatmap", new ApiHandler(this::handleArenaDeathHeatmap));
            activeServer.createContext("/api/analytics/arena/wave-correlation", new ApiHandler(this::handleArenaWaveCorrelation));
            activeServer.createContext("/api/analytics/arena/templates-failure-rate", new ApiHandler(this::handleArenaTemplatesFailureRate));
            activeServer.createContext("/api/export/arena/build-metrics", new ApiHandler(this::handleArenaExportBuildMetrics));
            activeServer.createContext("/api/export/arena/performance", new ApiHandler(this::handleArenaExportPerformance));
            activeServer.createContext("/api/export/arena/wave-correlation", new ApiHandler(this::handleArenaExportWaveCorrelation));

            // Static files for dashboard SPA
            activeServer.createContext("/dashboard", new StaticFileHandler("/dashboard"));
            // Also serve static files from root (for relative paths in HTML)
            activeServer.createContext("/style.css", new StaticFileHandler("/dashboard"));
            activeServer.createContext("/app.js", new StaticFileHandler("/dashboard"));

            activeServer.start();
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
            HttpServer httpServer = server;
            if (httpServer != null) {
                httpServer.stop(1);
            }
            server = null;

            ArenaDashboardEndpoint endpoint = arenaEndpoint;
            if (endpoint != null) {
                try {
                    endpoint.close();
                } catch (Exception e) {
                    LOGGER.debug("[Dashboard] Failed to close arena endpoint", e);
                }
            }
            arenaEndpoint = null;
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
        var telemetry = DuckDBTelemetryService.INSTANCE;

        // Basic status
        health.put("status", telemetry.isEnabled() && !telemetry.isCircuitBroken() ? "ok" : "degraded");
        health.put("duckdb_enabled", telemetry.isEnabled());
        health.put("circuit_broken", telemetry.isCircuitBroken());
        health.put("timestamp", System.currentTimeMillis());

        // Batch writer stats
        var writer = telemetry.getBatchWriter();
        if (writer != null) {
            Map<String, Object> writerStats = new HashMap<>();
            writerStats.put("total_inserts", writer.getTotalInserts());
            writerStats.put("total_batches", writer.getTotalBatches());
            writerStats.put("pending_inserts", writer.getPendingInserts());
            writerStats.put("dropped_inserts", writer.getDroppedInserts());
            writerStats.put("error_count", writer.getErrorCount());
            writerStats.put("pressure_level", writer.getPressureLevel());
            health.put("writer", writerStats);

            // P2: Latency stats with P99
            var latencyStats = writer.getWriteLatencyStats();
            if (latencyStats.sampleCount() > 0) {
                Map<String, Object> latency = new HashMap<>();
                latency.put("sample_count", latencyStats.sampleCount());
                latency.put("avg_ms", Math.round(latencyStats.avgMs() * 100) / 100.0);
                latency.put("p50_ms", Math.round(latencyStats.p50Ms() * 100) / 100.0);
                latency.put("p95_ms", Math.round(latencyStats.p95Ms() * 100) / 100.0);
                latency.put("p99_ms", Math.round(latencyStats.p99Ms() * 100) / 100.0);
                latency.put("max_ms", Math.round(latencyStats.maxMs() * 100) / 100.0);
                latency.put("transient_errors", latencyStats.transientErrors());
                latency.put("permanent_errors", latencyStats.permanentErrors());
                latency.put("degradation_errors", latencyStats.degradationErrors());
                latency.put("total_errors", latencyStats.totalErrors());
                latency.put("p99_high", latencyStats.isP99High(500.0));
                latency.put("error_rate_high", latencyStats.isErrorRateHigh(1.0));
                health.put("latency", latency);
            }

            // P2: Sampling stats
            var samplingConfig = writer.getSamplingConfig();
            Map<String, Object> sampling = new HashMap<>();
            sampling.put("enabled", samplingConfig.enabled());
            sampling.put("sampled_out_count", writer.getSampledOutCount());
            sampling.put("critical_rate", samplingConfig.criticalRate());
            sampling.put("high_rate", samplingConfig.highRate());
            sampling.put("normal_rate", samplingConfig.normalRate());
            sampling.put("low_rate", samplingConfig.lowRate());
            health.put("sampling", sampling);
        }

        // Connection metrics
        var connectionManager = telemetry.getConnectionManager();
        if (connectionManager != null) {
            var connMetrics = connectionManager.getConnectionMetrics();
            Map<String, Object> connection = new HashMap<>();
            connection.put("total_acquisitions", connMetrics.totalAcquisitions());
            connection.put("timeouts", connMetrics.acquisitionTimeouts());
            connection.put("contention_events", connMetrics.contentionEvents());
            connection.put("avg_wait_ms", Math.round(connMetrics.avgWaitTimeMs() * 100) / 100.0);
            connection.put("timeout_rate_pct", Math.round(connMetrics.timeoutRatePercent() * 100) / 100.0);
            connection.put("high_contention", connMetrics.isHighContention());
            connection.put("queue_length", connMetrics.currentQueueLength());
            connection.put("locked", connMetrics.isLocked());
            health.put("connection", connection);
        }

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
        String from = paramOrEmpty(params, "from");
        String to = paramOrEmpty(params, "to");
        int limit = getIntParam(paramOrEmpty(params, "limit"), 1000);
        Map<String, String> filters = buildArenaFilters(params);
        return gson.toJson(queryTableWithFilters("combat_hits", from, to, limit, "ts", filters));
    }

    private String handleCombatDeaths(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        String from = paramOrEmpty(params, "from");
        String to = paramOrEmpty(params, "to");
        int limit = getIntParam(paramOrEmpty(params, "limit"), 500);
        Map<String, String> filters = buildArenaFilters(params);
        return gson.toJson(queryTableWithFilters("combat_deaths", from, to, limit, "ts", filters));
    }

    private String handleCombatFights(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        String from = paramOrEmpty(params, "from");
        String to = paramOrEmpty(params, "to");
        int limit = getIntParam(paramOrEmpty(params, "limit"), 100);
        Map<String, String> filters = buildArenaFilters(params);
        return gson.toJson(queryTableWithFilters("combat_fights", from, to, limit, "start_ts", filters));
    }

    private String handleCombatWeapons(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        Map<String, String> filters = buildArenaFilters(params);
        String from = paramOrEmpty(params, "from");
        String to = paramOrEmpty(params, "to");
        return gson.toJson(getWeaponStats(filters, from, to));
    }

    private String handleEnduranceSessions(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        String from = paramOrEmpty(params, "from");
        String to = paramOrEmpty(params, "to");
        int limit = getIntParam(paramOrEmpty(params, "limit"), 100);
        Map<String, String> filters = buildArenaFilters(params);
        return gson.toJson(queryTableWithFilters("endurance_sessions", from, to, limit, "start_ts", filters));
    }

    private String handleEnduranceWaves(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        int limit = getIntParam(paramOrEmpty(params, "limit"), 500);
        String sessionId = params.get("session_id");
        if (sessionId != null) {
            return gson.toJson(queryWithFilter("endurance_waves", "session_id", sessionId, limit));
        }
        Map<String, String> filters = buildArenaFilters(params);
        return gson.toJson(queryTableWithFilters("endurance_waves", "", "", limit, "ts", filters));
    }

    private String handleEndurancePerks(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        String templateId = params.get("templateId");
        if (templateId != null && !templateId.isBlank()) {
            Map<String, String> filters = buildArenaFilters(params);
            String from = paramOrEmpty(params, "from");
            String to = paramOrEmpty(params, "to");
            return gson.toJson(queryTableWithFilters("endurance_perks", from, to, 500, "ts", filters));
        }
        return gson.toJson(getPerkStats());
    }

    private String handleEndurancePerformance(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        String from = paramOrEmpty(params, "from");
        String to = paramOrEmpty(params, "to");
        int limit = getIntParam(paramOrEmpty(params, "limit"), 200);
        Map<String, String> filters = buildArenaFilters(params);
        return gson.toJson(queryTableWithFilters("endurance_performance", from, to, limit, "ts", filters));
    }

    private String handleLeaderboard(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        String categoryName = paramOrEmpty(params, "category");
        String arenaId = params.get("arenaId");
        String scope = paramOrEmpty(params, "scope"); // global, weekly, arena
        int limit = getIntParam(paramOrEmpty(params, "limit"), 10);

        com.devmod.endurance.LeaderboardSystem.LeaderboardCategory category;
        try {
            category = com.devmod.endurance.LeaderboardSystem.LeaderboardCategory.valueOf(
                categoryName.isEmpty() ? "WAVES_COMPLETED" : categoryName.toUpperCase(java.util.Locale.ROOT)
            );
        } catch (IllegalArgumentException e) {
            return gson.toJson(Map.of("error", "Unknown category: " + categoryName));
        }

        java.util.List<com.devmod.endurance.LeaderboardSystem.LeaderboardEntry> entries;
        if ("weekly".equalsIgnoreCase(scope)) {
            entries = com.devmod.endurance.LeaderboardSystem.INSTANCE.getWeeklyTopEntries(category, limit);
        } else if ("arena".equalsIgnoreCase(scope) && arenaId != null && !arenaId.isEmpty()) {
            entries = com.devmod.endurance.LeaderboardSystem.INSTANCE.getTopEntries(category, arenaId, limit);
        } else {
            entries = com.devmod.endurance.LeaderboardSystem.INSTANCE.getTopEntries(category, limit);
        }

        // Convert to serializable format
        java.util.List<Map<String, Object>> result = new java.util.ArrayList<>();
        int rank = 1;
        for (var entry : entries) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("rank", rank++);
            row.put("playerId", entry.playerId().toString());
            row.put("playerName", entry.playerName());
            row.put("score", entry.score());
            row.put("formattedScore", com.devmod.endurance.LeaderboardSystem.formatScore(category, entry.score()));
            row.put("arenaId", entry.arenaId());
            row.put("timestamp", entry.timestamp() != null ? entry.timestamp().toEpochMilli() : null);
            row.put("metadata", entry.metadata());
            result.add(row);
        }

        return gson.toJson(Map.of(
            "category", category.name(),
            "displayName", category.getDisplayName(),
            "description", category.getDescription(),
            "higherIsBetter", category.isHigherIsBetter(),
            "scope", scope.isEmpty() ? "global" : scope,
            "arenaId", arenaId,
            "entries", result
        ));
    }

    private String handleLeaderboardCategories(HttpExchange exchange) {
        java.util.List<Map<String, Object>> categories = new java.util.ArrayList<>();
        for (var cat : com.devmod.endurance.LeaderboardSystem.LeaderboardCategory.values()) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("id", cat.name());
            row.put("displayName", cat.getDisplayName());
            row.put("description", cat.getDescription());
            row.put("higherIsBetter", cat.isHigherIsBetter());
            categories.add(row);
        }
        return gson.toJson(Map.of("categories", categories));
    }

    private String handlePlayerSnapshots(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        int limit = getIntParam(paramOrEmpty(params, "limit"), 500);
        String playerId = params.get("player_id");
        if (playerId != null) {
            return gson.toJson(queryWithFilter("player_snapshots", "player_id", playerId, limit));
        }
        return gson.toJson(queryTable("player_snapshots", "", "", limit));
    }

    private String handlePlayerAbilities(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        int limit = getIntParam(paramOrEmpty(params, "limit"), 500);
        String playerId = params.get("player_id");
        if (playerId != null) {
            return gson.toJson(queryWithFilter("player_abilities", "player_id", playerId, limit));
        }
        return gson.toJson(queryTable("player_abilities", "", "", limit));
    }

    private String handleSpatialHeatmaps(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        int limit = getIntParam(paramOrEmpty(params, "limit"), 5000);
        String type = paramOrEmpty(params, "type");
        String room = paramOrEmpty(params, "room");
        return gson.toJson(getHeatmapData(type, room, limit));
    }

    private String handleSpatialTransitions(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        int limit = getIntParam(paramOrEmpty(params, "limit"), 1000);
        return gson.toJson(queryTable("spatial_room_transitions", "", "", limit));
    }

    private String handleEconomyDrops(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        int limit = getIntParam(paramOrEmpty(params, "limit"), 500);
        return gson.toJson(queryTable("economy_mob_drops", "", "", limit));
    }

    private String handleEconomyKills(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        int limit = getIntParam(paramOrEmpty(params, "limit"), 50);
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
        String from = paramOrEmpty(params, "from");
        String to = paramOrEmpty(params, "to");
        int limit = getIntParam(paramOrEmpty(params, "limit"), 100);
        return gson.toJson(queryTable("dungeon_runs", from, to, limit, "start_ts"));
    }

    private String handlePerformance(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        String from = paramOrEmpty(params, "from");
        String to = paramOrEmpty(params, "to");
        int limit = getIntParam(paramOrEmpty(params, "limit"), 1000);
        return gson.toJson(queryTable("performance_samples", from, to, limit));
    }

    private String handleArenaToken(HttpExchange exchange) {
        ArenaDashboardEndpoint endpoint = requireArenaEndpoint();
        Map<String, String> params = parseQueryParams(exchange);
        String userId = params.getOrDefault("user", "local");
        boolean full = "true".equalsIgnoreCase(params.get("full"));
        ArenaDashboardEndpoint.TokenPermissions permissions = full
            ? ArenaDashboardEndpoint.TokenPermissions.full()
            : ArenaDashboardEndpoint.TokenPermissions.readOnly();
        String token = endpoint.generateToken(userId, permissions);
        return gson.toJson(Map.of(
            "token", token,
            "userId", userId,
            "permissions", permissions,
            "expiresInSeconds", 24 * 60 * 60
        ));
    }

    private String handleArenaTemplates(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        ArenaDashboardEndpoint endpoint = requireArenaEndpoint();
        ArenaDashboardEndpoint.TokenInfo token = requireArenaToken(endpoint, exchange, params);
        return gson.toJson(endpoint.handleTemplates(token));
    }

    private String handleArenaBuildMetrics(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        ArenaDashboardEndpoint endpoint = requireArenaEndpoint();
        ArenaDashboardEndpoint.TokenInfo token = requireArenaToken(endpoint, exchange, params);
        ArenaDashboardEndpoint.AnalyticsQueryParams query = buildArenaParams(params, true);
        return gson.toJson(endpoint.handleBuildMetrics(token, query));
    }

    private String handleArenaPerformance(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        ArenaDashboardEndpoint endpoint = requireArenaEndpoint();
        ArenaDashboardEndpoint.TokenInfo token = requireArenaToken(endpoint, exchange, params);
        ArenaDashboardEndpoint.AnalyticsQueryParams query = buildArenaParams(params, true);
        return gson.toJson(endpoint.handlePerformance(token, query));
    }

    private String handleArenaSpawnHeatmap(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        ArenaDashboardEndpoint endpoint = requireArenaEndpoint();
        ArenaDashboardEndpoint.TokenInfo token = requireArenaToken(endpoint, exchange, params);
        ArenaDashboardEndpoint.AnalyticsQueryParams query = buildArenaParams(params, true);
        return gson.toJson(endpoint.handleSpawnHeatmap(token, query));
    }

    private String handleArenaDeathHeatmap(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        ArenaDashboardEndpoint endpoint = requireArenaEndpoint();
        ArenaDashboardEndpoint.TokenInfo token = requireArenaToken(endpoint, exchange, params);
        ArenaDashboardEndpoint.AnalyticsQueryParams query = buildArenaParams(params, true);
        return gson.toJson(endpoint.handleDeathHeatmap(token, query));
    }

    private String handleArenaWaveCorrelation(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        ArenaDashboardEndpoint endpoint = requireArenaEndpoint();
        ArenaDashboardEndpoint.TokenInfo token = requireArenaToken(endpoint, exchange, params);
        ArenaDashboardEndpoint.AnalyticsQueryParams query = buildArenaParams(params, true);
        return gson.toJson(endpoint.handleWaveCorrelation(token, query));
    }

    private String handleArenaTemplatesFailureRate(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        ArenaDashboardEndpoint endpoint = requireArenaEndpoint();
        ArenaDashboardEndpoint.TokenInfo token = requireArenaToken(endpoint, exchange, params);
        return gson.toJson(endpoint.handleTemplatesFailureRate(token));
    }

    private String handleArenaExportBuildMetrics(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        ArenaDashboardEndpoint endpoint = requireArenaEndpoint();
        ArenaDashboardEndpoint.TokenInfo token = requireArenaToken(endpoint, exchange, params);
        ArenaDashboardEndpoint.AnalyticsQueryParams query = buildArenaParams(params, true);
        ArenaDashboardEndpoint.ExportFormat format = parseExportFormat(paramOrEmpty(params, "format"));
        return gson.toJson(endpoint.handleExportBuildMetrics(token, query, format));
    }

    private String handleArenaExportPerformance(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        ArenaDashboardEndpoint endpoint = requireArenaEndpoint();
        ArenaDashboardEndpoint.TokenInfo token = requireArenaToken(endpoint, exchange, params);
        ArenaDashboardEndpoint.AnalyticsQueryParams query = buildArenaParams(params, true);
        ArenaDashboardEndpoint.ExportFormat format = parseExportFormat(paramOrEmpty(params, "format"));
        return gson.toJson(endpoint.handleExportPerformance(token, query, format));
    }

    private String handleArenaExportWaveCorrelation(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        ArenaDashboardEndpoint endpoint = requireArenaEndpoint();
        ArenaDashboardEndpoint.TokenInfo token = requireArenaToken(endpoint, exchange, params);
        ArenaDashboardEndpoint.AnalyticsQueryParams query = buildArenaParams(params, true);
        ArenaDashboardEndpoint.ExportFormat format = parseExportFormat(params.get("format"));
        return gson.toJson(endpoint.handleExportWaveCorrelation(token, query, format));
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
        if (!sql.trim().toUpperCase(Locale.ROOT).startsWith("SELECT")) {
            return gson.toJson(Map.of("error", "Only SELECT queries are allowed"));
        }

        return gson.toJson(executeQuery(sql));
    }

    // ========== Query Helpers ==========

    private ArenaDashboardEndpoint requireArenaEndpoint() {
        ArenaDashboardEndpoint endpoint = arenaEndpoint;
        if (endpoint == null) {
            throw new IllegalStateException("Arena analytics not initialized");
        }
        return endpoint;
    }

    private ArenaDashboardEndpoint.TokenInfo requireArenaToken(ArenaDashboardEndpoint endpoint,
                                                              HttpExchange exchange,
                                                              Map<String, String> params) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if ((authHeader == null || authHeader.isBlank()) && params != null) {
            String tokenParam = params.get("token");
            if (tokenParam != null && !tokenParam.isBlank()) {
                authHeader = "Bearer " + tokenParam.trim();
            }
        }
        Optional<ArenaDashboardEndpoint.TokenInfo> token = endpoint.authenticate(authHeader);
        if (token.isEmpty()) {
            throw new SecurityException("Unauthorized");
        }
        if (!endpoint.checkRateLimit(token.get().token())) {
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
        Instant from = parseInstantParam(paramOrEmpty(params, "from"));
        Instant to = parseInstantParam(paramOrEmpty(params, "to"));
        if (from == null || to == null) {
            Duration rangeDuration = parseRangeDuration(params.getOrDefault("range", "7d"));
            from = now.minus(rangeDuration);
            to = now;
        }

        int page = getIntParam(paramOrEmpty(params, "page"), 0);
        int pageSize = getIntParam(paramOrEmpty(params, "pageSize"), 100);

        return new ArenaDashboardEndpoint.AnalyticsQueryParams(templateId, templateVersion, from, to, page, pageSize);
    }

    @Nullable
    private Instant parseInstantParam(@Nullable String value) {
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

    private ArenaDashboardEndpoint.ExportFormat parseExportFormat(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return ArenaDashboardEndpoint.ExportFormat.JSON;
        }
        try {
            return ArenaDashboardEndpoint.ExportFormat.valueOf(value.trim().toUpperCase(Locale.ROOT));
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
                    Object countValue = result.get(0).get("cnt");
                    long count = countValue instanceof Number ? ((Number) countValue).longValue() : 0L;
                    counts.put(table, count);
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
                    Object countValue = result.get(0).get("cnt");
                    long count = countValue instanceof Number ? ((Number) countValue).longValue() : 0L;
                    activity.put(table + "_15min", count);
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

    private List<Map<String, Object>> queryTable(String table, @Nullable String from, @Nullable String to, int limit) {
        return queryTable(table, from, to, limit, "ts");
    }

    private List<Map<String, Object>> queryTable(String table, @Nullable String from, @Nullable String to, int limit, String tsColumn) {
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

    private List<Map<String, Object>> queryTableWithFilters(String table, @Nullable String from, @Nullable String to, int limit,
                                                            String tsColumn, @Nullable Map<String, String> filters) {
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

    @Nullable
    private Timestamp parseTimestamp(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Timestamp.from(Instant.parse(value.trim()));
        } catch (Exception e) {
            return null;
        }
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

    private List<Map<String, Object>> queryWithFilter(String table, String column, String value, int limit) {
        String sql = "SELECT * FROM " + table + " WHERE " + column + " = '" + value +
                     "' ORDER BY ts DESC LIMIT " + limit;
        return executeQuery(sql);
    }

    private List<Map<String, Object>> getWeaponStats(@Nullable Map<String, String> filters, @Nullable String from, @Nullable String to) {
        String sql = """
            SELECT
                COALESCE(NULLIF(JSON_EXTRACT_STRING(attacker_state, '$.mainHand'), ''), 'fist') as weapon,
                COUNT(*) as hit_count,
                ROUND(SUM(damage), 1) as total_damage,
                ROUND(AVG(damage), 2) as avg_damage,
                SUM(CASE WHEN is_miss THEN 1 ELSE 0 END) as misses
            FROM combat_hits
            WHERE (? IS NULL OR ts >= ?)
              AND (? IS NULL OR ts <= ?)
              AND attacker_name IS NOT NULL
              AND (? IS NULL OR template_id = ?)
              AND (? IS NULL OR template_version = ?)
              AND (? IS NULL OR policy_id = ?)
              AND (? IS NULL OR policy_version = ?)
              AND (? IS NULL OR arena_id = ?)
            GROUP BY weapon
            ORDER BY total_damage DESC
            LIMIT 20
            """;

        Timestamp fromTs = parseTimestamp(from);
        Timestamp toTs = parseTimestamp(to);
        if (fromTs == null && toTs == null) {
            fromTs = Timestamp.from(Instant.now().minus(Duration.ofHours(1)));
        }

        String templateId = filters != null ? filters.get("template_id") : null;
        Integer templateVersion = filters != null ? parseNullableInt(filters.get("template_version")) : null;
        String policyId = filters != null ? filters.get("policy_id") : null;
        Integer policyVersion = filters != null ? parseNullableInt(filters.get("policy_version")) : null;
        String arenaId = filters != null ? filters.get("arena_id") : null;

        List<SqlParam> params = List.of(
            paramTimestamp(fromTs),
            paramTimestamp(fromTs),
            paramTimestamp(toTs),
            paramTimestamp(toTs),
            paramString(templateId),
            paramString(templateId),
            paramInt(templateVersion),
            paramInt(templateVersion),
            paramString(policyId),
            paramString(policyId),
            paramInt(policyVersion),
            paramInt(policyVersion),
            paramString(arenaId),
            paramString(arenaId)
        );
        return executeQuery(sql, params);
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

    public Timestamp getRangeStart(HttpExchange exchange) {
        Map<String, String> params = parseQueryParams(exchange);
        String range = params.getOrDefault("range", "24h");
        Duration duration = switch (range) {
            case "1h" -> Duration.ofHours(1);
            case "6h" -> Duration.ofHours(6);
            case "24h" -> Duration.ofHours(24);
            case "7d" -> Duration.ofDays(7);
            case "all" -> Duration.ofDays(365L * 100);
            default -> Duration.ofHours(24);
        };
        return Timestamp.from(Instant.now().minus(duration));
    }

    public SqlParam paramString(@Nullable String value) {
        return new SqlParam(value, Types.VARCHAR);
    }

    public SqlParam paramInt(@Nullable Integer value) {
        return new SqlParam(value, Types.INTEGER);
    }

    public SqlParam paramTimestamp(@Nullable Timestamp value) {
        return new SqlParam(value, Types.TIMESTAMP);
    }
    private List<Map<String, Object>> getHeatmapData(@Nullable String type, @Nullable String room, int limit) {
        String sql = """
            SELECT * FROM spatial_heatmaps
            WHERE (? IS NULL OR heatmap_type = ?)
              AND (? IS NULL OR room = ?)
            ORDER BY ts DESC
            LIMIT ?
            """;
        List<SqlParam> params = List.of(
            paramString(type),
            paramString(type),
            paramString(room),
            paramString(room),
            paramInt(limit)
        );
        return executeQuery(sql, params);
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

    public List<Map<String, Object>> executeQuery(String sql, List<SqlParam> params) {
        List<Map<String, Object>> results = new ArrayList<>();

        if (!DuckDBTelemetryService.INSTANCE.isEnabled()) {
            return results;
        }

        try {
            Connection conn = DuckDBTelemetryService.INSTANCE.getConnection();
            if (conn == null) {
                return results;
            }

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                int index = 1;
                for (SqlParam param : params) {
                    if (param.value() == null) {
                        stmt.setNull(index, param.sqlType());
                    } else {
                        stmt.setObject(index, param.value(), param.sqlType());
                    }
                    index++;
                }

                try (ResultSet rs = stmt.executeQuery()) {
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
            }
        } catch (Exception e) {
            LOGGER.error("[Dashboard] Query error: {} - SQL: {}", e.getMessage(), sql);
        }

        return results;
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
            for (String param : AMPERSAND_SPLITTER.split(query)) {
                if (param.isEmpty()) {
                    continue;
                }
                int equalsIndex = param.indexOf('=');
                if (equalsIndex >= 0) {
                    params.put(param.substring(0, equalsIndex), param.substring(equalsIndex + 1));
                }
            }
        }
        return params;
    }

    private static String paramOrEmpty(Map<String, String> params, String key) {
        String value = params.get(key);
        return value == null ? "" : value;
    }

    private int getIntParam(@Nullable String value, int defaultValue) {
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

    private static class StaticFileHandler implements HttpHandler {
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

            InputStream stream = getClass().getResourceAsStream(fullResourcePath);
            if (stream == null) {
                String notFound = "File not found: " + fullResourcePath;
                exchange.sendResponseHeaders(404, notFound.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(notFound.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            try (InputStream is = stream) {
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
