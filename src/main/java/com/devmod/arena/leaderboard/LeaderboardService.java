package com.devmod.arena.leaderboard;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
public final class LeaderboardService {

    /**
     * Represents a single leaderboard entry.
     */
    public record LeaderboardEntry(
        UUID playerId,
        String username,
        long score,
        int matchesPlayed,
        int rank
    ) {}

    /**
     * Represents a page of leaderboard results.
     */
    public record LeaderboardPage(
        LeaderboardType type,
        List<LeaderboardEntry> entries,
        int page,
        int pageSize,
        int totalEntries,
        int totalPages,
        Instant cachedAt
    ) {
        public LeaderboardPage {
            entries = entries != null ? List.copyOf(entries) : List.of();
        }

        /**
         * Checks if there's a next page.
         */
        public boolean hasNextPage() {
            return page < totalPages;
        }

        /**
         * Checks if there's a previous page.
         */
        public boolean hasPreviousPage() {
            return page > 1;
        }
    }

    /**
     * Represents a player's rank result.
     */
    public record PlayerRank(
        UUID playerId,
        LeaderboardType type,
        int rank,
        long score,
        Instant cachedAt
    ) {}

    /**
     * Redis cache interface for leaderboard data.
     */
    public interface RedisCache {
        /**
         * Sets a list with TTL.
         */
        void setList(String key, List<LeaderboardEntry> entries, Duration ttl);

        /**
         * Gets a list.
         */
        List<LeaderboardEntry> getList(String key);

        /**
         * Sets player rank in secondary index.
         */
        void setPlayerRank(String indexKey, UUID playerId, int rank, long score);

        /**
         * Gets player rank from secondary index.
         */
        Optional<PlayerRank> getPlayerRank(String indexKey, UUID playerId, LeaderboardType type);

        /**
         * Checks if key exists.
         */
        boolean exists(String key);

        /**
         * Deletes a key.
         */
        void delete(String key);
    }

    /**
     * Database query interface.
     */
    public interface DatabaseQuery {
        /**
         * Executes a leaderboard query.
         */
        List<LeaderboardEntry> executeLeaderboardQuery(String sql, Map<String, Object> params);

        /**
         * Gets total count for pagination.
         */
        int getTotalCount(LeaderboardType type, Map<String, Object> params);
    }

    /**
     * Season configuration for time-based leaderboards.
     */
    public record SeasonConfig(
        String seasonId,
        Instant start,
        Instant end
    ) {
        /**
         * Creates a season config for the current month.
         */
        public static SeasonConfig currentMonth() {
            ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
            ZonedDateTime monthStart = now.withDayOfMonth(1).with(LocalTime.MIDNIGHT);
            ZonedDateTime monthEnd = monthStart.plusMonths(1);
            String id = String.format("season_%d_%02d", now.getYear(), now.getMonthValue());
            return new SeasonConfig(id, monthStart.toInstant(), monthEnd.toInstant());
        }

        /**
         * Creates a custom season with explicit dates.
         */
        public static SeasonConfig of(String seasonId, Instant start, Instant end) {
            return new SeasonConfig(seasonId, start, end);
        }

        /**
         * Checks if the current time is within this season.
         */
        public boolean isActive() {
            Instant now = Instant.now();
            return !now.isBefore(start) && now.isBefore(end);
        }

        /**
         * Gets the duration of this season.
         */
        public Duration duration() {
            return Duration.between(start, end);
        }
    }

    /**
     * Default page size.
     */
    public static final int DEFAULT_PAGE_SIZE = 100;

    /**
     * Maximum page size.
     */
    public static final int MAX_PAGE_SIZE = 500;

    /**
     * Calculation time (03:00 UTC as per DD56).
     */
    public static final LocalTime CALCULATION_TIME = LocalTime.of(3, 0);

    private final RedisCache cache;
    private final DatabaseQuery database;
    private final ScheduledExecutorService scheduler;
    private final Map<LeaderboardType, Instant> lastCalculationTimes;
    private final Consumer<String> logger;
    private volatile SeasonConfig seasonConfig;

    /**
     * Creates a LeaderboardService with Redis cache, database query, and season config.
     *
     * @param cache Redis cache implementation
     * @param database Database query implementation
     * @param logger Logger consumer
     * @param seasonConfig Season configuration for time-based leaderboards
     */
    public LeaderboardService(RedisCache cache, DatabaseQuery database, Consumer<String> logger, SeasonConfig seasonConfig) {
        this.cache = cache;
        this.database = database;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "leaderboard-scheduler");
            t.setDaemon(true);
            return t;
        });
        this.lastCalculationTimes = new ConcurrentHashMap<>();
        this.logger = logger != null ? logger : s -> {};
        this.seasonConfig = seasonConfig != null ? seasonConfig : SeasonConfig.currentMonth();
    }

    /**
     * Creates a LeaderboardService with Redis cache and database query.
     *
     * @param cache Redis cache implementation
     * @param database Database query implementation
     * @param logger Logger consumer
     */
    public LeaderboardService(RedisCache cache, DatabaseQuery database, Consumer<String> logger) {
        this(cache, database, logger, null);
    }

    /**
     * Creates a LeaderboardService with no-op logger.
     */
    public LeaderboardService(RedisCache cache, DatabaseQuery database) {
        this(cache, database, null, null);
    }

    /**
     * Updates the season configuration.
     *
     * @param seasonConfig new season configuration
     */
    public void setSeasonConfig(SeasonConfig seasonConfig) {
        this.seasonConfig = seasonConfig != null ? seasonConfig : SeasonConfig.currentMonth();
        logger.accept("Season config updated: " + this.seasonConfig.seasonId());
    }

    /**
     * Gets the current season configuration.
     *
     * @return current season config
     */
    public SeasonConfig getSeasonConfig() {
        return seasonConfig;
    }

    /**
     * Starts the scheduled leaderboard calculation.
     * Implements DD56: cron job 03:00 daily.
     */
    public void startScheduledCalculation() {
        // Calculate initial delay to 03:00 UTC
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime nextRun = now.with(CALCULATION_TIME);
        if (now.isAfter(nextRun)) {
            nextRun = nextRun.plusDays(1);
        }
        long initialDelay = Duration.between(now, nextRun).toSeconds();

        logger.accept("Scheduling leaderboard calculation at 03:00 UTC, initial delay: " + initialDelay + "s");

        scheduler.scheduleAtFixedRate(
            this::calculateAllLeaderboards,
            initialDelay,
            TimeUnit.DAYS.toSeconds(1),
            TimeUnit.SECONDS
        );
    }

    /**
     * Stops the scheduled calculation.
     */
    public void stopScheduledCalculation() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Calculates all leaderboards and updates cache.
     * This is called by the scheduler at 03:00 daily.
     */
    public void calculateAllLeaderboards() {
        logger.accept("Starting leaderboard calculation at " + Instant.now());

        for (LeaderboardType type : LeaderboardType.values()) {
            try {
                calculateAndCacheLeaderboard(type);
                lastCalculationTimes.put(type, Instant.now());
                logger.accept("Calculated leaderboard: " + type.getDisplayName());
            } catch (Exception e) {
                logger.accept("Error calculating leaderboard " + type + ": " + e.getMessage());
            }
        }

        logger.accept("Leaderboard calculation completed at " + Instant.now());
    }

    /**
     * Calculates and caches a specific leaderboard.
     *
     * @param type the leaderboard type
     */
    public void calculateAndCacheLeaderboard(LeaderboardType type) {
        Map<String, Object> params = getDefaultParams(type);

        // Fetch all entries for caching
        List<LeaderboardEntry> allEntries = database.executeLeaderboardQuery(
            type.getSqlQuery().replace("LIMIT :limit OFFSET :offset", ""),
            params
        );

        // Cache the full leaderboard
        cache.setList(type.getRedisKey(), allEntries, type.getCacheTtl());

        // Build secondary index for O(1) player rank lookup (DD56)
        for (LeaderboardEntry entry : allEntries) {
            cache.setPlayerRank(
                type.getPlayerRankIndexKey(),
                entry.playerId(),
                entry.rank(),
                entry.score()
            );
        }
    }

    /**
     * Gets a page of the leaderboard.
     * Implements DD56: Redis cache, O(1) read.
     *
     * @param type leaderboard type
     * @param page page number (1-based)
     * @param pageSize entries per page
     * @return leaderboard page or empty if cache miss
     */
    public LeaderboardPage getLeaderboard(LeaderboardType type, int page, int pageSize) {
        // Validate parameters
        page = Math.max(1, page);
        pageSize = Math.min(MAX_PAGE_SIZE, Math.max(1, pageSize));

        // Try to get from cache
        List<LeaderboardEntry> cachedEntries = cache.getList(type.getRedisKey());

        if (cachedEntries == null || cachedEntries.isEmpty()) {
            // Cache miss - return empty list (DD56: don't block on calculation)
            logger.accept("Cache miss for leaderboard: " + type.getKey());
            return new LeaderboardPage(
                type,
                List.of(),
                page,
                pageSize,
                0,
                0,
                null
            );
        }

        // Calculate pagination
        int totalEntries = cachedEntries.size();
        int totalPages = (int) Math.ceil((double) totalEntries / pageSize);
        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, totalEntries);

        List<LeaderboardEntry> pageEntries;
        if (startIndex >= totalEntries) {
            pageEntries = List.of();
        } else {
            pageEntries = cachedEntries.subList(startIndex, endIndex);
        }

        Instant cachedAt = lastCalculationTimes.get(type);

        return new LeaderboardPage(
            type,
            pageEntries,
            page,
            pageSize,
            totalEntries,
            totalPages,
            cachedAt
        );
    }

    /**
     * Gets the leaderboard with default page size.
     *
     * @param type leaderboard type
     * @param page page number
     * @return leaderboard page
     */
    public LeaderboardPage getLeaderboard(LeaderboardType type, int page) {
        return getLeaderboard(type, page, DEFAULT_PAGE_SIZE);
    }

    /**
     * Gets a player's rank from the secondary index.
     * Implements DD56: O(1) player rank lookup.
     *
     * @param type leaderboard type
     * @param playerId player to look up
     * @return player rank or empty if not found
     */
    public Optional<PlayerRank> getPlayerRank(LeaderboardType type, UUID playerId) {
        return cache.getPlayerRank(type.getPlayerRankIndexKey(), playerId, type);
    }

    /**
     * Gets players around a specific player's rank.
     *
     * @param type leaderboard type
     * @param playerId the player to center around
     * @param radius number of players above and below to include
     * @return list of entries around the player
     */
    public List<LeaderboardEntry> getPlayersAround(LeaderboardType type, UUID playerId, int radius) {
        Optional<PlayerRank> playerRank = getPlayerRank(type, playerId);
        if (playerRank.isEmpty()) {
            return List.of();
        }

        int rank = playerRank.get().rank();
        int startRank = Math.max(1, rank - radius);
        int endRank = rank + radius;

        List<LeaderboardEntry> cachedEntries = cache.getList(type.getRedisKey());
        if (cachedEntries == null || cachedEntries.isEmpty()) {
            return List.of();
        }

        return cachedEntries.stream()
            .filter(e -> e.rank() >= startRank && e.rank() <= endRank)
            .toList();
    }

    /**
     * Checks if a leaderboard is currently cached.
     *
     * @param type leaderboard type
     * @return true if cached
     */
    public boolean isCached(LeaderboardType type) {
        return cache.exists(type.getRedisKey());
    }

    /**
     * Gets the last calculation time for a leaderboard.
     *
     * @param type leaderboard type
     * @return last calculation time or empty
     */
    public Optional<Instant> getLastCalculationTime(LeaderboardType type) {
        return Optional.ofNullable(lastCalculationTimes.get(type));
    }

    /**
     * Forces a recalculation of a specific leaderboard.
     *
     * @param type leaderboard type
     */
    public void forceRecalculate(LeaderboardType type) {
        calculateAndCacheLeaderboard(type);
        lastCalculationTimes.put(type, Instant.now());
    }

    /**
     * Invalidates a leaderboard cache.
     *
     * @param type leaderboard type
     */
    public void invalidateCache(LeaderboardType type) {
        cache.delete(type.getRedisKey());
        cache.delete(type.getPlayerRankIndexKey());
    }

    /**
     * Gets default query parameters for a leaderboard type.
     *
     * @param type leaderboard type
     * @return parameter map
     */
    private Map<String, Object> getDefaultParams(LeaderboardType type) {
        Map<String, Object> params = new ConcurrentHashMap<>();

        if (type == LeaderboardType.SEASON_WINS) {
            // Use configured season config for season-based leaderboards
            SeasonConfig season = this.seasonConfig;
            params.put("season_id", season.seasonId());
            params.put("season_start", season.start());
            params.put("season_end", season.end());
        }

        return params;
    }

    /**
     * In-memory cache implementation for testing.
     * In production, use Redis.
     */
    public static class InMemoryCache implements RedisCache {
        private final Map<String, CacheEntry<List<LeaderboardEntry>>> listCache = new ConcurrentHashMap<>();
        private final Map<String, Map<UUID, PlayerRankData>> playerRankIndex = new ConcurrentHashMap<>();

        private record CacheEntry<T>(T value, Instant expiresAt) {
            boolean isExpired() {
                return Instant.now().isAfter(expiresAt);
            }
        }

        private record PlayerRankData(int rank, long score, Instant cachedAt) {}

        @Override
        public void setList(String key, List<LeaderboardEntry> entries, Duration ttl) {
            listCache.put(key, new CacheEntry<>(new ArrayList<>(entries), Instant.now().plus(ttl)));
        }

        @Override
        public List<LeaderboardEntry> getList(String key) {
            CacheEntry<List<LeaderboardEntry>> entry = listCache.get(key);
            if (entry == null || entry.isExpired()) {
                return null;
            }
            return entry.value();
        }

        @Override
        public void setPlayerRank(String indexKey, UUID playerId, int rank, long score) {
            playerRankIndex.computeIfAbsent(indexKey, k -> new ConcurrentHashMap<>())
                .put(playerId, new PlayerRankData(rank, score, Instant.now()));
        }

        @Override
        public Optional<PlayerRank> getPlayerRank(String indexKey, UUID playerId, LeaderboardType type) {
            Map<UUID, PlayerRankData> index = playerRankIndex.get(indexKey);
            if (index == null) {
                return Optional.empty();
            }
            PlayerRankData data = index.get(playerId);
            if (data == null) {
                return Optional.empty();
            }
            return Optional.of(new PlayerRank(playerId, type, data.rank(), data.score(), data.cachedAt()));
        }

        @Override
        public boolean exists(String key) {
            CacheEntry<List<LeaderboardEntry>> entry = listCache.get(key);
            return entry != null && !entry.isExpired();
        }

        @Override
        public void delete(String key) {
            listCache.remove(key);
            playerRankIndex.remove(key);
        }
    }
}
