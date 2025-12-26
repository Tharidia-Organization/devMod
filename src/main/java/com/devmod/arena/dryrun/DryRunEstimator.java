package com.devmod.arena.dryrun;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DryRunEstimator {

    private static final Logger LOGGER = LoggerFactory.getLogger(DryRunEstimator.class);

    /**
     * DD10: Base time per block in milliseconds (heuristic).
     */
    private static final double BASE_MS_PER_BLOCK = 0.02;

    /**
     * DD10: Base time per entity in milliseconds (heuristic).
     */
    private static final double BASE_MS_PER_ENTITY = 5.0;

    /**
     * DD10: Overhead factor for chunk loading.
     */
    private static final double CHUNK_OVERHEAD_FACTOR = 1.15;

    /**
     * DD10: Cache TTL in milliseconds (5 minutes).
     */
    private static final long CACHE_TTL_MS = 5 * 60 * 1000;

    /**
     * DD10: Weight for historical P75 vs heuristic (0.7 = 70% historical).
     */
    private static final double HISTORICAL_WEIGHT = 0.70;

    private final HistoricalDataSource historicalData;
    private final Map<String, CachedEstimate> estimateCache = new ConcurrentHashMap<>();

    /**
     * Represents a cached estimate.
     */
    private record CachedEstimate(DryRunResult result, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    /**
     * Interface for querying historical build data from DuckDB.
     */
    public interface HistoricalDataSource {
        /**
         * Gets the P75 build time for a template from DuckDB.
         *
         * @param templateId the template ID
         * @return P75 build time in milliseconds, or empty if no data
         */
        Optional<Long> getP75BuildTimeMs(String templateId);

        /**
         * Gets the P75 block count for a template.
         *
         * @param templateId the template ID
         * @return P75 block count, or empty if no data
         */
        Optional<Integer> getP75BlockCount(String templateId);

        /**
         * Gets the average entity count for a template.
         *
         * @param templateId the template ID
         * @return average entity count, or empty if no data
         */
        Optional<Integer> getAvgEntityCount(String templateId);
    }

    /**
     * Result of a dry-run estimation.
     */
    public record DryRunResult(
        String templateId,
        long estimatedBuildTimeMs,
        int estimatedBlocks,
        int estimatedEntities,
        int estimatedChunks,
        EstimationSource source,
        double confidence,
        Instant calculatedAt
    ) {
        /**
         * Gets the estimated build time as a Duration.
         */
        public Duration estimatedBuildTime() {
            return Duration.ofMillis(estimatedBuildTimeMs);
        }

        /**
         * Checks if this is a high-confidence estimate (>0.8).
         */
        public boolean isHighConfidence() {
            return confidence >= 0.80;
        }
    }

    /**
     * Source of the estimation.
     */
    public enum EstimationSource {
        /** Pure heuristic calculation (no historical data) */
        HEURISTIC,
        /** Based on historical P75 from DuckDB */
        HISTORICAL,
        /** Blend of heuristic and historical */
        BLENDED,
        /** Retrieved from cache */
        CACHED
    }

    /**
     * Input parameters for dry-run estimation.
     */
    public record DryRunInput(
        String templateId,
        int blockCount,
        int entityCount,
        int chunkCount,
        boolean useHistorical
    ) {
        /**
         * Creates input with default settings.
         */
        public static DryRunInput of(String templateId, int blockCount, int entityCount, int chunkCount) {
            return new DryRunInput(templateId, blockCount, entityCount, chunkCount, true);
        }

        /**
         * Creates input for heuristic-only estimation.
         */
        public static DryRunInput heuristicOnly(String templateId, int blockCount, int entityCount, int chunkCount) {
            return new DryRunInput(templateId, blockCount, entityCount, chunkCount, false);
        }
    }

    public DryRunEstimator(HistoricalDataSource historicalData) {
        this.historicalData = historicalData;
    }

    /**
     * Creates an estimator with no historical data (heuristic only).
     */
    public static DryRunEstimator heuristicOnly() {
        return new DryRunEstimator(new NoOpHistoricalDataSource());
    }

    /**
     * Estimates build time for a template.
     * Implements DD10: heuristic + historical P75.
     *
     * @param input the dry-run input parameters
     * @return estimation result
     */
    public DryRunResult estimate(DryRunInput input) {
        // Check cache first
        CachedEstimate cached = estimateCache.get(input.templateId());
        if (cached != null && !cached.isExpired()) {
            LOGGER.debug("Returning cached estimate for template: {}", input.templateId());
            return new DryRunResult(
                cached.result().templateId(),
                cached.result().estimatedBuildTimeMs(),
                cached.result().estimatedBlocks(),
                cached.result().estimatedEntities(),
                cached.result().estimatedChunks(),
                EstimationSource.CACHED,
                cached.result().confidence(),
                cached.result().calculatedAt()
            );
        }

        // Calculate heuristic estimate
        long heuristicMs = calculateHeuristicMs(input);

        // Try to get historical P75 if enabled
        EstimationSource source = EstimationSource.HEURISTIC;
        double confidence = 0.50; // Base confidence for heuristic
        long finalEstimateMs = heuristicMs;

        if (input.useHistorical()) {
            Optional<Long> historicalP75 = historicalData.getP75BuildTimeMs(input.templateId());

            if (historicalP75.isPresent()) {
                // Blend historical and heuristic (DD10)
                finalEstimateMs = blendEstimates(heuristicMs, historicalP75.get());
                source = EstimationSource.BLENDED;
                confidence = 0.85; // Higher confidence with historical data

                LOGGER.debug("Blended estimate for {}: heuristic={}ms, historical={}ms, final={}ms",
                    input.templateId(), heuristicMs, historicalP75.get(), finalEstimateMs);
            }
        }

        DryRunResult result = new DryRunResult(
            input.templateId(),
            finalEstimateMs,
            input.blockCount(),
            input.entityCount(),
            input.chunkCount(),
            source,
            confidence,
            Instant.now()
        );

        // Cache the result
        estimateCache.put(input.templateId(), new CachedEstimate(
            result,
            Instant.now().plusMillis(CACHE_TTL_MS)
        ));

        return result;
    }

    /**
     * Calculates heuristic build time.
     * DD10: Base formula with overhead factors.
     */
    private long calculateHeuristicMs(DryRunInput input) {
        double blockTime = input.blockCount() * BASE_MS_PER_BLOCK;
        double entityTime = input.entityCount() * BASE_MS_PER_ENTITY;
        double chunkOverhead = input.chunkCount() * CHUNK_OVERHEAD_FACTOR;

        double totalMs = (blockTime + entityTime) * (1 + chunkOverhead / 100);

        // Add safety margin (10%)
        totalMs *= 1.10;

        return Math.round(totalMs);
    }

    /**
     * Blends heuristic and historical estimates.
     * DD10: 70% historical, 30% heuristic.
     */
    private long blendEstimates(long heuristicMs, long historicalMs) {
        return Math.round(
            (historicalMs * HISTORICAL_WEIGHT) +
            (heuristicMs * (1 - HISTORICAL_WEIGHT))
        );
    }

    /**
     * Generates the DuckDB SQL query for P75 build time.
     * DD10: Historical query for P75.
     *
     * @param templateId the template ID
     * @param daysBack number of days to look back
     * @return SQL query string
     */
    public static String generateP75Query(String templateId, int daysBack) {
        return """
            SELECT
                template_id,
                PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY build_time_ms) as p75_build_time_ms,
                PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY block_count) as p75_block_count,
                AVG(entity_count) as avg_entity_count,
                COUNT(*) as sample_count
            FROM arena_builds
            WHERE template_id = '%s'
              AND created_at >= NOW() - INTERVAL '%d days'
              AND status = 'SUCCESS'
            GROUP BY template_id
            HAVING COUNT(*) >= 10
            """.formatted(templateId, daysBack);
    }

    /**
     * Clears the estimate cache.
     */
    public void clearCache() {
        estimateCache.clear();
        LOGGER.info("Estimate cache cleared");
    }

    /**
     * Removes expired entries from cache.
     */
    public void cleanupCache() {
        int before = estimateCache.size();
        estimateCache.entrySet().removeIf(e -> e.getValue().isExpired());
        int removed = before - estimateCache.size();
        if (removed > 0) {
            LOGGER.debug("Removed {} expired cache entries", removed);
        }
    }

    /**
     * Gets current cache size.
     */
    public int getCacheSize() {
        return estimateCache.size();
    }

    /**
     * No-op implementation for heuristic-only mode.
     */
    private static class NoOpHistoricalDataSource implements HistoricalDataSource {
        @Override
        public Optional<Long> getP75BuildTimeMs(String templateId) {
            return Optional.empty();
        }

        @Override
        public Optional<Integer> getP75BlockCount(String templateId) {
            return Optional.empty();
        }

        @Override
        public Optional<Integer> getAvgEntityCount(String templateId) {
            return Optional.empty();
        }
    }
}
