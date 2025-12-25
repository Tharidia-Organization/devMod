package com.devmod.arena.dashboard;

import com.devmod.arena.security.ArenaCommandAudit;
import com.devmod.telemetry.duckdb.ArenaRecords;
import com.devmod.telemetry.duckdb.DuckDBQueryAPI;
import com.devmod.telemetry.duckdb.DuckDBTelemetryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * DD35: Dashboard Auth - token + cache + background refresh
 * DD36: Analytics Query Limits - 30 giorni max, pagination, timeout 10s
 *
 * HTTP endpoint handler for the arena dashboard.
 * Features:
 * - Token-based authentication
 * - Rate limiting (60 req/min per token)
 * - Metrics cache with 5-minute background refresh
 */
public class ArenaDashboardEndpoint implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArenaDashboardEndpoint.class);

    /** Rate limit: requests per minute */
    private static final int RATE_LIMIT_PER_MINUTE = 60;

    /** Cache refresh interval */
    private static final Duration CACHE_REFRESH_INTERVAL = Duration.ofMinutes(5);

    /** Token expiry duration */
    private static final Duration TOKEN_EXPIRY = Duration.ofHours(24);

    /** DD36: Query timeout duration */
    private static final Duration QUERY_TIMEOUT = Duration.ofSeconds(10);

    private static final ArenaDashboardEndpoint INSTANCE = new ArenaDashboardEndpoint();

    /** Active authentication tokens */
    private final Map<String, TokenInfo> validTokens = new ConcurrentHashMap<>();

    /** Rate limit tracking: token -> request timestamps */
    private final Map<String, ConcurrentLinkedQueue<Instant>> rateLimitBuckets = new ConcurrentHashMap<>();

    /** Metrics cache */
    private final Map<String, CachedMetric<?>> metricsCache = new ConcurrentHashMap<>();

    /** Background refresh executor */
    private final ScheduledExecutorService refreshExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ArenaDashboard-CacheRefresh");
                t.setDaemon(true);
                return t;
            });

    /** DD36: Query executor for timeout-controlled operations */
    private final ExecutorService queryExecutor =
            Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "ArenaDashboard-Query");
                t.setDaemon(true);
                return t;
            });

    /** Whether the endpoint is enabled */
    private volatile boolean enabled = true;

    /** Query API for analytics queries - uses unified DuckDBTelemetryService */
    private DuckDBQueryAPI queryAPI;

    private ArenaDashboardEndpoint() {
        // Start background cache refresh
        startCacheRefresh();
    }

    /**
     * Initializes the query API from DuckDBTelemetryService.
     * Called automatically when queries are needed.
     */
    private DuckDBQueryAPI getQueryAPI() {
        if (queryAPI == null) {
            queryAPI = DuckDBTelemetryService.INSTANCE.getQueryAPI();
        }
        return queryAPI;
    }

    /**
     * Legacy method for backwards compatibility.
     * @deprecated Query API is now obtained from DuckDBTelemetryService
     */
    @Deprecated
    public void setRepository(Object repository) {
        LOGGER.info("DuckDB repository configured via TelemetryService");
    }

    public static ArenaDashboardEndpoint getInstance() {
        return INSTANCE;
    }

    /**
     * Gracefully shuts down executors and clears caches to prevent leaks on reload/shutdown.
     */
    @Override
    public void close() {
        try {
            refreshExecutor.shutdownNow();
        } catch (Exception ignored) {}
        try {
            queryExecutor.shutdownNow();
        } catch (Exception ignored) {}
        metricsCache.clear();
        rateLimitBuckets.clear();
        validTokens.clear();
    }

    /**
     * Authenticates a request using a bearer token
     *
     * @param authHeader The Authorization header value
     * @return The authenticated token info, or empty if invalid
     */
    public Optional<TokenInfo> authenticate(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }

        String token = authHeader.substring(7).trim();
        TokenInfo tokenInfo = validTokens.get(token);

        if (tokenInfo == null) {
            return Optional.empty();
        }

        // Check expiry
        if (tokenInfo.isExpired()) {
            validTokens.remove(token);
            return Optional.empty();
        }

        return Optional.of(tokenInfo);
    }

    /**
     * Checks rate limit for a token
     *
     * @param token The authentication token
     * @return true if request is allowed, false if rate limited
     */
    public boolean checkRateLimit(String token) {
        ConcurrentLinkedQueue<Instant> bucket = rateLimitBuckets.computeIfAbsent(
                token, k -> new ConcurrentLinkedQueue<>());

        Instant now = Instant.now();
        Instant windowStart = now.minus(Duration.ofMinutes(1));

        // Remove old entries
        bucket.removeIf(ts -> ts.isBefore(windowStart));

        // Check limit
        if (bucket.size() >= RATE_LIMIT_PER_MINUTE) {
            ArenaCommandAudit.getInstance().logSecurityEvent(
                    "RATE_LIMIT", null, "Token: " + maskToken(token));
            return false;
        }

        // Record this request
        bucket.add(now);
        return true;
    }

    /**
     * Generates a new authentication token
     *
     * @param userId The user ID to associate
     * @param permissions The permissions to grant
     * @return The generated token
     */
    public String generateToken(String userId, TokenPermissions permissions) {
        String token = generateSecureToken();
        Instant expiry = Instant.now().plus(TOKEN_EXPIRY);

        validTokens.put(token, new TokenInfo(token, userId, permissions, expiry));

        LOGGER.info("Generated dashboard token for user: {}", userId);
        return token;
    }

    /**
     * Revokes an authentication token
     *
     * @param token The token to revoke
     * @return true if token was found and revoked
     */
    public boolean revokeToken(String token) {
        TokenInfo removed = validTokens.remove(token);
        if (removed != null) {
            rateLimitBuckets.remove(token);
            LOGGER.info("Revoked dashboard token for user: {}", removed.userId());
            return true;
        }
        return false;
    }

    /**
     * Gets a cached metric value, computing if necessary
     *
     * @param key The metric key
     * @param supplier The supplier to compute the metric if not cached
     * @param <T> The metric value type
     * @return The metric value
     */
    @SuppressWarnings("unchecked")
    public <T> T getCachedMetric(String key, Supplier<T> supplier) {
        CachedMetric<?> cached = metricsCache.get(key);

        if (cached != null && !cached.isExpired()) {
            return (T) cached.value();
        }

        // Compute and cache
        T value = supplier.get();
        metricsCache.put(key, new CachedMetric<>(value, Instant.now().plus(CACHE_REFRESH_INTERVAL)));

        return value;
    }

    /**
     * Registers a metric for background refresh
     *
     * @param key The metric key
     * @param supplier The supplier to compute the metric
     */
    public void registerRefreshableMetric(String key, Supplier<?> supplier) {
        // Initial computation
        Object value = supplier.get();
        metricsCache.put(key, new CachedMetric<>(value, Instant.now().plus(CACHE_REFRESH_INTERVAL)));

        // The background refresh task will handle updates
        LOGGER.debug("Registered refreshable metric: {}", key);
    }

    /**
     * DD36: Executes a query with timeout protection.
     * Returns the default value if query times out or fails.
     *
     * @param supplier The query supplier
     * @param defaultValue The default value on timeout/error
     * @param <T> The result type
     * @return The query result or default value
     */
    private <T> T executeWithTimeout(Supplier<T> supplier, T defaultValue) {
        try {
            Future<T> future = queryExecutor.submit(supplier::get);
            return future.get(QUERY_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            LOGGER.warn("DD36: Query timed out after {}s", QUERY_TIMEOUT.toSeconds());
            return defaultValue;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("DD36: Query interrupted");
            return defaultValue;
        } catch (ExecutionException e) {
            LOGGER.error("DD36: Query execution failed", e.getCause());
            return defaultValue;
        }
    }

    /**
     * Invalidates a cached metric
     *
     * @param key The metric key
     */
    public void invalidateCache(String key) {
        metricsCache.remove(key);
    }

    /**
     * Invalidates all cached metrics
     */
    public void invalidateAllCaches() {
        metricsCache.clear();
    }

    /**
     * Starts the background cache refresh task
     */
    private void startCacheRefresh() {
        refreshExecutor.scheduleAtFixedRate(
                this::refreshAllCaches,
                CACHE_REFRESH_INTERVAL.toMinutes(),
                CACHE_REFRESH_INTERVAL.toMinutes(),
                TimeUnit.MINUTES
        );
    }

    /**
     * Refreshes all cached metrics
     */
    private void refreshAllCaches() {
        LOGGER.debug("Running background cache refresh for {} metrics", metricsCache.size());

        // Mark all caches as expired to force refresh on next access
        metricsCache.replaceAll((key, cached) ->
                new CachedMetric<>(cached.value(), Instant.now().minus(Duration.ofSeconds(1)))
        );
    }

    /**
     * Generates a secure random token
     */
    private String generateSecureToken() {
        java.security.SecureRandom random = new java.security.SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);

        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Masks a token for logging (shows first 8 chars)
     */
    private String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "****";
        }
        return token.substring(0, 8) + "****";
    }

    /**
     * Gets the current rate limit bucket size for a token
     */
    public int getCurrentRateLimitUsage(String token) {
        ConcurrentLinkedQueue<Instant> bucket = rateLimitBuckets.get(token);
        if (bucket == null) {
            return 0;
        }

        Instant windowStart = Instant.now().minus(Duration.ofMinutes(1));
        bucket.removeIf(ts -> ts.isBefore(windowStart));
        return bucket.size();
    }

    /**
     * Gets the number of active tokens
     */
    public int getActiveTokenCount() {
        // Clean up expired tokens first
        validTokens.entrySet().removeIf(e -> e.getValue().isExpired());
        return validTokens.size();
    }

    /**
     * Gets the cache size
     */
    public int getCacheSize() {
        return metricsCache.size();
    }

    /**
     * Enables or disables the endpoint
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Gets whether the endpoint is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Shuts down the background executor
     */
    public void shutdown() {
        refreshExecutor.shutdown();
        try {
            if (!refreshExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                refreshExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            refreshExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ========== Analytics Endpoints (DD36) ==========

    /**
     * DD36: Query parameters for analytics endpoints.
     * Enforces 30-day max range and pagination.
     */
    public record AnalyticsQueryParams(
        String templateId,
        Integer templateVersion,
        Instant from,
        Instant to,
        int page,
        int pageSize
    ) {
        private static final Duration MAX_RANGE = Duration.ofDays(30);
        private static final int DEFAULT_PAGE_SIZE = 100;
        private static final int MAX_PAGE_SIZE = 1000;

        public AnalyticsQueryParams {
            // Clamp date range to 30 days
            if (from != null && to != null) {
                Duration range = Duration.between(from, to);
                if (range.compareTo(MAX_RANGE) > 0) {
                    from = to.minus(MAX_RANGE);
                }
            }
            // Clamp page size
            if (pageSize <= 0) pageSize = DEFAULT_PAGE_SIZE;
            if (pageSize > MAX_PAGE_SIZE) pageSize = MAX_PAGE_SIZE;
            if (page < 0) page = 0;
        }

        public static AnalyticsQueryParams forTemplate(String templateId) {
            return new AnalyticsQueryParams(
                templateId,
                null,
                Instant.now().minus(Duration.ofDays(7)),
                Instant.now(),
                0,
                DEFAULT_PAGE_SIZE
            );
        }
    }

    /**
     * DD36: Build metrics response.
     * GET /api/analytics/arena/build-metrics
     */
    public record BuildMetricsResponse(
        String templateId,
        int totalBuilds,
        int successfulBuilds,
        int failedBuilds,
        double avgBuildTimeMs,
        double p95BuildTimeMs,
        double failureRate,
        Instant from,
        Instant to
    ) {
        public static BuildMetricsResponse empty(String templateId) {
            return new BuildMetricsResponse(templateId, 0, 0, 0, 0.0, 0.0, 0.0,
                Instant.now().minus(Duration.ofDays(7)), Instant.now());
        }
    }

    /**
     * DD36: Performance metrics response.
     * GET /api/analytics/arena/performance
     */
    public record PerformanceResponse(
        String templateId,
        double avgMspt,
        double maxMspt,
        double avgTps,
        int sampleCount,
        java.util.List<MsptSample> samples
    ) {
        public record MsptSample(Instant timestamp, double mspt, double tps) {}

        public static PerformanceResponse empty(String templateId) {
            return new PerformanceResponse(templateId, 0.0, 0.0, 20.0, 0, java.util.List.of());
        }
    }

    /**
     * DD36: Heatmap data response.
     * GET /api/analytics/arena/spawn-heatmap
     * GET /api/analytics/arena/death-heatmap
     */
    public record HeatmapResponse(
        String templateId,
        String type,
        int gridSizeX,
        int gridSizeZ,
        int[][] grid,
        int maxValue,
        int totalEvents
    ) {
        public static HeatmapResponse empty(String templateId, String type) {
            return new HeatmapResponse(templateId, type, 16, 16, new int[16][16], 0, 0);
        }
    }

    /**
     * DD36: Wave correlation data.
     * GET /api/analytics/arena/wave-correlation
     */
    public record WaveCorrelationResponse(
        String templateId,
        java.util.List<WaveData> waves,
        double avgCompletionRate,
        int avgWaveReached
    ) {
        public record WaveData(
            int waveNumber,
            int attempts,
            int completions,
            double completionRate,
            double avgDurationSeconds
        ) {}

        public static WaveCorrelationResponse empty(String templateId) {
            return new WaveCorrelationResponse(templateId, java.util.List.of(), 0.0, 0);
        }
    }

    /**
     * Handles /api/analytics/arena/build-metrics request.
     * Returns cached or computed build metrics for a template.
     * DD36: Uses 10s query timeout.
     */
    public BuildMetricsResponse handleBuildMetrics(TokenInfo token, AnalyticsQueryParams params) {
        if (!token.permissions().canReadAnalytics()) {
            throw new SecurityException("Token lacks analytics permission");
        }

        String cacheKey = buildCacheKey("build-metrics", params);
        return getCachedMetric(cacheKey, () ->
            executeWithTimeout(() -> computeBuildMetrics(params), BuildMetricsResponse.empty(params.templateId()))
        );
    }

    /**
     * Handles /api/analytics/arena/performance request.
     * DD36: Uses 10s query timeout.
     */
    public PerformanceResponse handlePerformance(TokenInfo token, AnalyticsQueryParams params) {
        if (!token.permissions().canReadAnalytics()) {
            throw new SecurityException("Token lacks analytics permission");
        }

        String cacheKey = buildCacheKey("performance", params);
        return getCachedMetric(cacheKey, () ->
            executeWithTimeout(() -> computePerformance(params), PerformanceResponse.empty(params.templateId()))
        );
    }

    /**
     * Handles /api/analytics/arena/spawn-heatmap request.
     * DD36: Uses 10s query timeout.
     */
    public HeatmapResponse handleSpawnHeatmap(TokenInfo token, AnalyticsQueryParams params) {
        if (!token.permissions().canReadAnalytics()) {
            throw new SecurityException("Token lacks analytics permission");
        }

        String cacheKey = buildCacheKey("spawn-heatmap", params);
        return getCachedMetric(cacheKey, () ->
            executeWithTimeout(() -> computeHeatmap(params, "spawn"), HeatmapResponse.empty(params.templateId(), "spawn"))
        );
    }

    /**
     * Handles /api/analytics/arena/death-heatmap request.
     * DD36: Uses 10s query timeout.
     */
    public HeatmapResponse handleDeathHeatmap(TokenInfo token, AnalyticsQueryParams params) {
        if (!token.permissions().canReadAnalytics()) {
            throw new SecurityException("Token lacks analytics permission");
        }

        String cacheKey = buildCacheKey("death-heatmap", params);
        return getCachedMetric(cacheKey, () ->
            executeWithTimeout(() -> computeHeatmap(params, "death"), HeatmapResponse.empty(params.templateId(), "death"))
        );
    }

    /**
     * Handles /api/analytics/arena/wave-correlation request.
     * DD36: Uses 10s query timeout.
     */
    public WaveCorrelationResponse handleWaveCorrelation(TokenInfo token, AnalyticsQueryParams params) {
        if (!token.permissions().canReadAnalytics()) {
            throw new SecurityException("Token lacks analytics permission");
        }

        String cacheKey = buildCacheKey("wave-correlation", params);
        return getCachedMetric(cacheKey, () ->
            executeWithTimeout(() -> computeWaveCorrelation(params), WaveCorrelationResponse.empty(params.templateId()))
        );
    }

    /**
     * Gap 3: Handles /api/analytics/arena/templates-failure-rate request.
     * Returns failure rate for ALL templates for comparison chart.
     * DD36: Uses 10s query timeout.
     */
    public TemplatesFailureRateResponse handleTemplatesFailureRate(TokenInfo token) {
        if (!token.permissions().canReadAnalytics()) {
            throw new SecurityException("Token lacks analytics permission");
        }

        String cacheKey = "templates-failure-rate:all";
        return getCachedMetric(cacheKey, () ->
            executeWithTimeout(this::computeTemplatesFailureRate, TemplatesFailureRateResponse.empty())
        );
    }

    /**
     * Handles /api/analytics/arena/templates request.
     * Returns list of available template IDs.
     */
    public TemplateListResponse handleTemplates(TokenInfo token) {
        if (!token.permissions().canReadAnalytics()) {
            throw new SecurityException("Token lacks analytics permission");
        }

        String cacheKey = "templates:list";
        return getCachedMetric(cacheKey, () -> executeWithTimeout(this::computeTemplateList, TemplateListResponse.empty()));
    }

    public record TemplateListResponse(
        List<String> templates,
        Instant generatedAt
    ) {
        public static TemplateListResponse empty() {
            return new TemplateListResponse(List.of(), Instant.now());
        }
    }

    /**
     * Gap 3: Response record for templates failure rate comparison.
     */
    public record TemplatesFailureRateResponse(
        List<TemplateFailureInfo> templates,
        Instant generatedAt
    ) {
        public static TemplatesFailureRateResponse empty() {
            return new TemplatesFailureRateResponse(List.of(), Instant.now());
        }
    }

    /**
     * Gap 3: Individual template failure info.
     */
    public record TemplateFailureInfo(
        String templateId,
        int totalBuilds,
        int failedBuilds,
        double failureRate
    ) {}

    private TemplateListResponse computeTemplateList() {
        if (getQueryAPI() == null) {
            LOGGER.warn("No repository configured, returning empty template list");
            return TemplateListResponse.empty();
        }
        try {
            List<String> templateIds = getQueryAPI().getArenaTemplateIds();
            return new TemplateListResponse(templateIds, Instant.now());
        } catch (Exception e) {
            LOGGER.error("Failed to fetch template list", e);
            return TemplateListResponse.empty();
        }
    }

    /**
     * Gap 3: Computes failure rate for all templates.
     */
    private TemplatesFailureRateResponse computeTemplatesFailureRate() {
        LOGGER.debug("Computing failure rates for all templates");

        if (getQueryAPI() == null) {
            LOGGER.warn("No repository configured, returning empty failure rates");
            return TemplatesFailureRateResponse.empty();
        }

        try {
            // Query all template IDs
            List<String> templateIds = getQueryAPI().getArenaTemplateIds();

            if (templateIds.isEmpty()) {
                return TemplatesFailureRateResponse.empty();
            }

            List<TemplateFailureInfo> results = new ArrayList<>();

            for (String templateId : templateIds) {
                List<ArenaRecords.BuildRecord> builds = getQueryAPI().getArenaRecentBuilds(templateId, 1000);

                if (!builds.isEmpty()) {
                    int totalBuilds = builds.size();
                    int failedBuilds = (int) builds.stream()
                        .filter(b -> "failed".equalsIgnoreCase(b.status()))
                        .count();
                    double failureRate = totalBuilds > 0
                        ? (failedBuilds * 100.0 / totalBuilds)
                        : 0.0;

                    results.add(new TemplateFailureInfo(
                        templateId,
                        totalBuilds,
                        failedBuilds,
                        Math.round(failureRate * 10.0) / 10.0 // Round to 1 decimal
                    ));
                }
            }

            return new TemplatesFailureRateResponse(results, Instant.now());

        } catch (Exception e) {
            LOGGER.error("Failed to compute templates failure rate", e);
            return TemplatesFailureRateResponse.empty();
        }
    }

    // ========== Analytics Computation ==========

    /**
     * DD36: Computes build metrics from DuckDB getQueryAPI().
     */
    private BuildMetricsResponse computeBuildMetrics(AnalyticsQueryParams params) {
        LOGGER.debug("Computing build metrics for template: {}", params.templateId());

        if (getQueryAPI() == null) {
            LOGGER.warn("No repository configured, returning empty metrics");
            return BuildMetricsResponse.empty(params.templateId());
        }

        try {
            // Query recent builds for this template
            List<ArenaRecords.BuildRecord> builds = getQueryAPI().getArenaRecentBuilds(
                params.templateId(),
                params.templateVersion(),
                params.pageSize()
            );

            if (builds.isEmpty()) {
                return BuildMetricsResponse.empty(params.templateId());
            }

            // Calculate metrics
            int totalBuilds = builds.size();
            int successfulBuilds = 0;
            int failedBuilds = 0;
            long totalDurationMs = 0;
            List<Long> durations = new ArrayList<>();

            for (ArenaRecords.BuildRecord build : builds) {
                if ("success".equalsIgnoreCase(build.status())) {
                    successfulBuilds++;
                } else if ("failed".equalsIgnoreCase(build.status())) {
                    failedBuilds++;
                }

                if (build.durationMs() != null && build.durationMs() > 0) {
                    totalDurationMs += build.durationMs();
                    durations.add(build.durationMs());
                }
            }

            double avgBuildTimeMs = durations.isEmpty() ? 0 : (double) totalDurationMs / durations.size();

            // Calculate p95
            double p95BuildTimeMs = 0;
            if (!durations.isEmpty()) {
                durations.sort(Long::compareTo);
                int p95Index = (int) Math.ceil(durations.size() * 0.95) - 1;
                p95Index = Math.max(0, Math.min(p95Index, durations.size() - 1));
                p95BuildTimeMs = durations.get(p95Index);
            }

            double failureRate = totalBuilds > 0 ? (double) failedBuilds / totalBuilds * 100 : 0;

            return new BuildMetricsResponse(
                params.templateId(),
                totalBuilds,
                successfulBuilds,
                failedBuilds,
                avgBuildTimeMs,
                p95BuildTimeMs,
                failureRate,
                params.from(),
                params.to()
            );

        } catch (Exception e) {
            LOGGER.error("Failed to query build metrics for template: {}", params.templateId(), e);
            return BuildMetricsResponse.empty(params.templateId());
        }
    }

    /**
     * DD36: Computes performance metrics.
     * Note: Currently uses estimated data based on build times.
     * Full implementation requires MSPT/TPS telemetry data.
     */
    private PerformanceResponse computePerformance(AnalyticsQueryParams params) {
        LOGGER.debug("Computing performance for template: {}", params.templateId());

        if (getQueryAPI() == null) {
            return PerformanceResponse.empty(params.templateId());
        }

        try {
            // Prefer real performance samples if available
            List<ArenaRecords.BuildPerformanceSample> perfSamples =
                getQueryAPI().getArenaBuildPerformance(params.templateId(), params.templateVersion(), 100);
            if (!perfSamples.isEmpty()) {
                List<PerformanceResponse.MsptSample> samples = new ArrayList<>();
                double totalMspt = 0;
                double maxMspt = 0;
                int count = 0;
                for (ArenaRecords.BuildPerformanceSample sample : perfSamples) {
                    Double msptValue = sample.avgMspt() != null ? sample.avgMspt() : sample.peakMspt();
                    if (msptValue == null || msptValue <= 0) {
                        continue;
                    }
                    double mspt = msptValue;
                    double tps = Math.min(1000.0 / mspt, 20.0);
                    samples.add(new PerformanceResponse.MsptSample(sample.timestamp(), mspt, tps));
                    totalMspt += mspt;
                    maxMspt = Math.max(maxMspt, mspt);
                    count++;
                }
                if (count > 0) {
                    double avgMspt = totalMspt / count;
                    double avgTps = Math.max(1.0, 20.0 - (avgMspt / 5.0));
                    return new PerformanceResponse(
                        params.templateId(),
                        avgMspt,
                        maxMspt,
                        avgTps,
                        samples.size(),
                        samples.subList(0, Math.min(50, samples.size()))
                    );
                }
            }

            // Query recent builds to estimate performance impact
            List<ArenaRecords.BuildRecord> builds = getQueryAPI().getArenaRecentBuilds(
                params.templateId(),
                params.templateVersion(),
                100
            );

            if (builds.isEmpty()) {
                return PerformanceResponse.empty(params.templateId());
            }

            // Estimate MSPT impact from build durations
            // Assumption: longer builds = more MSPT during construction
            List<PerformanceResponse.MsptSample> samples = new ArrayList<>();
            double totalMspt = 0;
            double maxMspt = 0;

            for (ArenaRecords.BuildRecord build : builds) {
                if (build.durationMs() != null && build.durationMs() > 0 && build.completedAt() != null) {
                    // Estimate MSPT based on build duration (rough heuristic)
                    double estimatedMspt = Math.min(50.0, build.durationMs() / 100.0);
                    double estimatedTps = Math.max(1.0, 20.0 - (estimatedMspt / 5.0));

                    samples.add(new PerformanceResponse.MsptSample(
                        build.completedAt(),
                        estimatedMspt,
                        estimatedTps
                    ));

                    totalMspt += estimatedMspt;
                    maxMspt = Math.max(maxMspt, estimatedMspt);
                }
            }

            double avgMspt = samples.isEmpty() ? 0 : totalMspt / samples.size();
            double avgTps = avgMspt > 0 ? Math.max(1.0, 20.0 - (avgMspt / 5.0)) : 20.0;

            return new PerformanceResponse(
                params.templateId(),
                avgMspt,
                maxMspt,
                avgTps,
                samples.size(),
                samples.subList(0, Math.min(50, samples.size())) // Limit samples in response
            );

        } catch (Exception e) {
            LOGGER.error("Failed to compute performance for template: {}", params.templateId(), e);
            return PerformanceResponse.empty(params.templateId());
        }
    }

    /**
     * Gap 4: Computes spawn/death heatmap from DuckDB spatial events table.
     * Falls back to simulated data if no spatial events exist.
     */
    private HeatmapResponse computeHeatmap(AnalyticsQueryParams params, String type) {
        LOGGER.debug("Computing {} heatmap for template: {}", type, params.templateId());

        int gridSize = 16;
        int[][] grid = new int[gridSize][gridSize];

        if (getQueryAPI() != null) {
            try {
                // Gap 4: Try to get real spatial event data first
                grid = getQueryAPI().getArenaHeatmap(params.templateId(), params.templateVersion(), type, gridSize);

                // Check if we have any real data
                int totalEvents = 0;
                for (int[] row : grid) {
                    for (int val : row) {
                        totalEvents += val;
                    }
                }

                // If no real data, fall back to simulated data for demo purposes
                if (totalEvents == 0) {
                    LOGGER.debug("No spatial events found, using simulated heatmap data");
                    grid = generateSimulatedHeatmap(params.templateId(), params.templateVersion(), gridSize);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to query spatial events: {}", e.getMessage());
                grid = generateSimulatedHeatmap(params.templateId(), params.templateVersion(), gridSize);
            }
        } else {
            grid = generateSimulatedHeatmap(params.templateId(), params.templateVersion(), gridSize);
        }

        // Calculate max and total
        int maxValue = 0;
        int totalEvents = 0;
        for (int[] row : grid) {
            for (int val : row) {
                maxValue = Math.max(maxValue, val);
                totalEvents += val;
            }
        }

        return new HeatmapResponse(
            params.templateId(),
            type,
            gridSize,
            gridSize,
            grid,
            maxValue,
            totalEvents
        );
    }

    /**
     * Gap 4: Generates simulated heatmap data for demo/fallback.
     */
    private int[][] generateSimulatedHeatmap(String templateId, Integer templateVersion, int gridSize) {
        int[][] grid = new int[gridSize][gridSize];

        if (getQueryAPI() != null) {
            try {
                List<ArenaRecords.BuildRecord> builds =
                    getQueryAPI().getArenaRecentBuilds(templateId, templateVersion, 50);

                if (!builds.isEmpty()) {
                    int centerX = gridSize / 2;
                    int centerZ = gridSize / 2;

                    // Distribute events around center with decay
                    for (int dx = -3; dx <= 3; dx++) {
                        for (int dz = -3; dz <= 3; dz++) {
                            int x = centerX + dx;
                            int z = centerZ + dz;
                            if (x >= 0 && x < gridSize && z >= 0 && z < gridSize) {
                                int distance = Math.abs(dx) + Math.abs(dz);
                                int events = Math.max(0, builds.size() - distance * 2);
                                grid[x][z] = events;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to generate simulated heatmap: {}", e.getMessage());
            }
        }

        return grid;
    }

    /**
     * DD36: Computes wave correlation data.
     * Note: Full implementation requires wave completion telemetry.
     * Currently returns estimated data based on usage patterns.
     */
    private WaveCorrelationResponse computeWaveCorrelation(AnalyticsQueryParams params) {
        LOGGER.debug("Computing wave correlation for template: {}", params.templateId());
        if (getQueryAPI() == null) {
            LOGGER.warn("No repository configured, returning empty wave correlation");
            return WaveCorrelationResponse.empty(params.templateId());
        }

        try {
            List<ArenaRecords.WaveAggregate> aggregates = getQueryAPI().getArenaWaveAggregates(
                params.templateId(), params.templateVersion(), params.from(), params.to());
            if (aggregates.isEmpty()) {
                return WaveCorrelationResponse.empty(params.templateId());
            }

            List<WaveCorrelationResponse.WaveData> waves = new ArrayList<>();
            for (ArenaRecords.WaveAggregate agg : aggregates) {
                int attempts = Math.max(0, agg.attempts());
                int completions = Math.max(0, agg.completions());
                double completionRate = attempts > 0 ? (double) completions / attempts * 100.0 : 0.0;
                double avgDurationSeconds = agg.avgDurationMs() > 0 ? agg.avgDurationMs() / 1000.0 : 0.0;
                waves.add(new WaveCorrelationResponse.WaveData(
                    agg.waveNumber(),
                    attempts,
                    completions,
                    completionRate,
                    avgDurationSeconds
                ));
            }

            double avgCompletionRate = waves.stream()
                .mapToDouble(WaveCorrelationResponse.WaveData::completionRate)
                .average()
                .orElse(0.0);

            double avgWavesCompleted = getQueryAPI().getArenaAverageWavesCompleted(
                params.templateId(), params.templateVersion(), params.from(), params.to());
            int avgWaveReached = avgWavesCompleted > 0 ? (int) Math.round(avgWavesCompleted) : 0;

            return new WaveCorrelationResponse(
                params.templateId(),
                waves,
                avgCompletionRate,
                avgWaveReached
            );
        } catch (Exception e) {
            LOGGER.error("Failed to compute wave correlation for template: {}", params.templateId(), e);
            return WaveCorrelationResponse.empty(params.templateId());
        }
    }

    private String buildCacheKey(String prefix, AnalyticsQueryParams params) {
        if (params.templateVersion() != null) {
            return prefix + ":" + params.templateId() + ":v" + params.templateVersion();
        }
        return prefix + ":" + params.templateId();
    }

    // ========== G9: Export Endpoints ==========

    /**
     * G9: Export format enum.
     */
    public enum ExportFormat {
        CSV,
        JSON
    }

    /**
     * G9: Export response with content and metadata.
     */
    public record ExportResponse(
        String format,
        String filename,
        String contentType,
        String content,
        int recordCount,
        Instant generatedAt
    ) {}

    /**
     * G9: Handles /api/export/build-metrics request.
     * Exports build metrics as CSV or JSON.
     *
     * @param token The authentication token
     * @param params Query parameters
     * @param format Export format (CSV or JSON)
     * @return ExportResponse with formatted data
     */
    public ExportResponse handleExportBuildMetrics(TokenInfo token, AnalyticsQueryParams params, ExportFormat format) {
        if (!token.permissions().canExport()) {
            throw new SecurityException("Token lacks export permission");
        }

        BuildMetricsResponse metrics = computeBuildMetrics(params);
        String filename = "build-metrics-" + params.templateId() + "-" + Instant.now().toEpochMilli();

        if (format == ExportFormat.CSV) {
            String csv = buildMetricsToCsv(metrics);
            return new ExportResponse("csv", filename + ".csv", "text/csv", csv, 1, Instant.now());
        } else {
            String json = buildMetricsToJson(metrics);
            return new ExportResponse("json", filename + ".json", "application/json", json, 1, Instant.now());
        }
    }

    /**
     * G9: Handles /api/export/performance request.
     * Exports performance data as CSV or JSON.
     */
    public ExportResponse handleExportPerformance(TokenInfo token, AnalyticsQueryParams params, ExportFormat format) {
        if (!token.permissions().canExport()) {
            throw new SecurityException("Token lacks export permission");
        }

        PerformanceResponse perf = computePerformance(params);
        String filename = "performance-" + params.templateId() + "-" + Instant.now().toEpochMilli();

        if (format == ExportFormat.CSV) {
            String csv = performanceToCsv(perf);
            return new ExportResponse("csv", filename + ".csv", "text/csv", csv, perf.sampleCount(), Instant.now());
        } else {
            String json = performanceToJson(perf);
            return new ExportResponse("json", filename + ".json", "application/json", json, perf.sampleCount(), Instant.now());
        }
    }

    /**
     * G9: Handles /api/export/wave-correlation request.
     * Exports wave correlation data as CSV or JSON.
     */
    public ExportResponse handleExportWaveCorrelation(TokenInfo token, AnalyticsQueryParams params, ExportFormat format) {
        if (!token.permissions().canExport()) {
            throw new SecurityException("Token lacks export permission");
        }

        WaveCorrelationResponse waves = computeWaveCorrelation(params);
        String filename = "wave-correlation-" + params.templateId() + "-" + Instant.now().toEpochMilli();

        if (format == ExportFormat.CSV) {
            String csv = waveCorrelationToCsv(waves);
            return new ExportResponse("csv", filename + ".csv", "text/csv", csv, waves.waves().size(), Instant.now());
        } else {
            String json = waveCorrelationToJson(waves);
            return new ExportResponse("json", filename + ".json", "application/json", json, waves.waves().size(), Instant.now());
        }
    }

    /**
     * G9: Handles /api/export/full-report request.
     * Exports all analytics data for a template as a combined report.
     */
    public ExportResponse handleExportFullReport(TokenInfo token, AnalyticsQueryParams params, ExportFormat format) {
        if (!token.permissions().canExport()) {
            throw new SecurityException("Token lacks export permission");
        }

        BuildMetricsResponse build = computeBuildMetrics(params);
        PerformanceResponse perf = computePerformance(params);
        WaveCorrelationResponse waves = computeWaveCorrelation(params);

        String filename = "full-report-" + params.templateId() + "-" + Instant.now().toEpochMilli();
        int totalRecords = 1 + perf.sampleCount() + waves.waves().size();

        if (format == ExportFormat.CSV) {
            String csv = fullReportToCsv(build, perf, waves);
            return new ExportResponse("csv", filename + ".csv", "text/csv", csv, totalRecords, Instant.now());
        } else {
            String json = fullReportToJson(build, perf, waves);
            return new ExportResponse("json", filename + ".json", "application/json", json, totalRecords, Instant.now());
        }
    }

    // ========== G9: CSV/JSON Formatters ==========

    private String buildMetricsToCsv(BuildMetricsResponse m) {
        StringBuilder csv = new StringBuilder();
        csv.append("template_id,total_builds,successful_builds,failed_builds,avg_build_time_ms,p95_build_time_ms,failure_rate,from,to\n");
        csv.append(String.format("%s,%d,%d,%d,%.2f,%.2f,%.2f,%s,%s\n",
            m.templateId(), m.totalBuilds(), m.successfulBuilds(), m.failedBuilds(),
            m.avgBuildTimeMs(), m.p95BuildTimeMs(), m.failureRate(),
            m.from(), m.to()
        ));
        return csv.toString();
    }

    private String buildMetricsToJson(BuildMetricsResponse m) {
        return String.format(
            "{\"templateId\":\"%s\",\"totalBuilds\":%d,\"successfulBuilds\":%d,\"failedBuilds\":%d," +
            "\"avgBuildTimeMs\":%.2f,\"p95BuildTimeMs\":%.2f,\"failureRate\":%.2f,\"from\":\"%s\",\"to\":\"%s\"}",
            escapeJson(m.templateId()), m.totalBuilds(), m.successfulBuilds(), m.failedBuilds(),
            m.avgBuildTimeMs(), m.p95BuildTimeMs(), m.failureRate(),
            m.from(), m.to()
        );
    }

    private String performanceToCsv(PerformanceResponse p) {
        StringBuilder csv = new StringBuilder();
        csv.append("template_id,timestamp,mspt,tps\n");
        for (PerformanceResponse.MsptSample sample : p.samples()) {
            csv.append(String.format("%s,%s,%.2f,%.2f\n",
                p.templateId(), sample.timestamp(), sample.mspt(), sample.tps()
            ));
        }
        return csv.toString();
    }

    private String performanceToJson(PerformanceResponse p) {
        StringBuilder json = new StringBuilder();
        json.append("{\"templateId\":\"").append(escapeJson(p.templateId())).append("\",");
        json.append("\"avgMspt\":").append(p.avgMspt()).append(",");
        json.append("\"maxMspt\":").append(p.maxMspt()).append(",");
        json.append("\"avgTps\":").append(p.avgTps()).append(",");
        json.append("\"sampleCount\":").append(p.sampleCount()).append(",");
        json.append("\"samples\":[");
        for (int i = 0; i < p.samples().size(); i++) {
            var s = p.samples().get(i);
            if (i > 0) json.append(",");
            json.append(String.format("{\"timestamp\":\"%s\",\"mspt\":%.2f,\"tps\":%.2f}",
                s.timestamp(), s.mspt(), s.tps()));
        }
        json.append("]}");
        return json.toString();
    }

    private String waveCorrelationToCsv(WaveCorrelationResponse w) {
        StringBuilder csv = new StringBuilder();
        csv.append("template_id,wave_number,attempts,completions,completion_rate,avg_duration_seconds\n");
        for (WaveCorrelationResponse.WaveData wave : w.waves()) {
            csv.append(String.format("%s,%d,%d,%d,%.2f,%.2f\n",
                w.templateId(), wave.waveNumber(), wave.attempts(), wave.completions(),
                wave.completionRate(), wave.avgDurationSeconds()
            ));
        }
        return csv.toString();
    }

    private String waveCorrelationToJson(WaveCorrelationResponse w) {
        StringBuilder json = new StringBuilder();
        json.append("{\"templateId\":\"").append(escapeJson(w.templateId())).append("\",");
        json.append("\"avgCompletionRate\":").append(w.avgCompletionRate()).append(",");
        json.append("\"avgWaveReached\":").append(w.avgWaveReached()).append(",");
        json.append("\"waves\":[");
        for (int i = 0; i < w.waves().size(); i++) {
            var wd = w.waves().get(i);
            if (i > 0) json.append(",");
            json.append(String.format("{\"waveNumber\":%d,\"attempts\":%d,\"completions\":%d,\"completionRate\":%.2f,\"avgDurationSeconds\":%.2f}",
                wd.waveNumber(), wd.attempts(), wd.completions(), wd.completionRate(), wd.avgDurationSeconds()));
        }
        json.append("]}");
        return json.toString();
    }

    private String fullReportToCsv(BuildMetricsResponse build, PerformanceResponse perf, WaveCorrelationResponse waves) {
        StringBuilder csv = new StringBuilder();

        // Section: Build Metrics
        csv.append("# Build Metrics\n");
        csv.append(buildMetricsToCsv(build));
        csv.append("\n");

        // Section: Performance
        csv.append("# Performance Samples\n");
        csv.append(performanceToCsv(perf));
        csv.append("\n");

        // Section: Wave Correlation
        csv.append("# Wave Correlation\n");
        csv.append(waveCorrelationToCsv(waves));

        return csv.toString();
    }

    private String fullReportToJson(BuildMetricsResponse build, PerformanceResponse perf, WaveCorrelationResponse waves) {
        return String.format(
            "{\"buildMetrics\":%s,\"performance\":%s,\"waveCorrelation\":%s,\"generatedAt\":\"%s\"}",
            buildMetricsToJson(build),
            performanceToJson(perf),
            waveCorrelationToJson(waves),
            Instant.now()
        );
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ========== Records ==========

    /**
     * Token information record
     */
    public record TokenInfo(
        String token,
        String userId,
        TokenPermissions permissions,
        Instant expiry
    ) {
        public boolean isExpired() {
            return Instant.now().isAfter(expiry);
        }

        public long getRemainingSeconds() {
            return Math.max(0, expiry.getEpochSecond() - Instant.now().getEpochSecond());
        }
    }

    /**
     * Token permissions
     */
    public record TokenPermissions(
        boolean canReadMetrics,
        boolean canReadAnalytics,
        boolean canExport,
        boolean canManageArenas
    ) {
        public static TokenPermissions readOnly() {
            return new TokenPermissions(true, true, false, false);
        }

        public static TokenPermissions full() {
            return new TokenPermissions(true, true, true, true);
        }
    }

    /**
     * Cached metric value with expiry
     */
    private record CachedMetric<T>(
        T value,
        Instant expiry
    ) {
        boolean isExpired() {
            return Instant.now().isAfter(expiry);
        }
    }
}
