package com.devmod.arena.dashboard;

import com.devmod.arena.security.ArenaCommandAudit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
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
public class ArenaDashboardEndpoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArenaDashboardEndpoint.class);

    private static final ArenaDashboardEndpoint INSTANCE = new ArenaDashboardEndpoint();

    /** Rate limit: requests per minute */
    private static final int RATE_LIMIT_PER_MINUTE = 60;

    /** Cache refresh interval */
    private static final Duration CACHE_REFRESH_INTERVAL = Duration.ofMinutes(5);

    /** Token expiry duration */
    private static final Duration TOKEN_EXPIRY = Duration.ofHours(24);

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

    /** Whether the endpoint is enabled */
    private volatile boolean enabled = true;

    private ArenaDashboardEndpoint() {
        // Start background cache refresh
        startCacheRefresh();
    }

    public static ArenaDashboardEndpoint getInstance() {
        return INSTANCE;
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
