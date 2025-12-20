package com.devmod.arena.autosmoke;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Autosmoke Size Thresholds implementing DD33: Autosmoke Soglie.
 *
 * <p>Key features:
 * <ul>
 *   <li>Size-based thresholds for autosmoke tests</li>
 *   <li>Template whitelist for skip</li>
 *   <li>Configurable thresholds per template size</li>
 *   <li>Default thresholds for small, medium, large, xlarge</li>
 * </ul>
 */
public class AutosmokeSizeThresholds {

    /**
     * Size category for templates.
     */
    public enum TemplateSize {
        /** < 5,000 blocks */
        SMALL,
        /** 5,000 - 25,000 blocks */
        MEDIUM,
        /** 25,000 - 75,000 blocks */
        LARGE,
        /** > 75,000 blocks */
        XLARGE
    }

    /**
     * Threshold configuration for a size category.
     */
    public record SizeThreshold(
        TemplateSize size,
        int minBlocks,
        int maxBlocks,
        Duration maxBuildTime,
        Duration maxRollbackTime,
        int maxEntities,
        double maxMemoryMb
    ) {
        /**
         * Checks if a block count falls within this threshold.
         */
        public boolean matches(int blockCount) {
            return blockCount >= minBlocks && blockCount < maxBlocks;
        }
    }

    /**
     * Result of threshold validation.
     */
    public record ThresholdResult(
        boolean passed,
        String templateId,
        TemplateSize detectedSize,
        SizeThreshold threshold,
        Duration actualBuildTime,
        Duration actualRollbackTime,
        int actualBlocks,
        int actualEntities,
        String failureReason
    ) {
        public static ThresholdResult pass(String templateId, TemplateSize size, SizeThreshold threshold,
                Duration buildTime, Duration rollbackTime, int blocks, int entities) {
            return new ThresholdResult(true, templateId, size, threshold,
                buildTime, rollbackTime, blocks, entities, null);
        }

        public static ThresholdResult fail(String templateId, TemplateSize size, SizeThreshold threshold,
                Duration buildTime, Duration rollbackTime, int blocks, int entities, String reason) {
            return new ThresholdResult(false, templateId, size, threshold,
                buildTime, rollbackTime, blocks, entities, reason);
        }
    }

    /**
     * DD33: Default thresholds per size category.
     */
    private static final Map<TemplateSize, SizeThreshold> DEFAULT_THRESHOLDS = Map.of(
        TemplateSize.SMALL, new SizeThreshold(
            TemplateSize.SMALL,
            0, 5_000,
            Duration.ofSeconds(2),
            Duration.ofMillis(500),
            20,
            50.0
        ),
        TemplateSize.MEDIUM, new SizeThreshold(
            TemplateSize.MEDIUM,
            5_000, 25_000,
            Duration.ofSeconds(5),
            Duration.ofSeconds(1),
            50,
            150.0
        ),
        TemplateSize.LARGE, new SizeThreshold(
            TemplateSize.LARGE,
            25_000, 75_000,
            Duration.ofSeconds(15),
            Duration.ofSeconds(3),
            100,
            300.0
        ),
        TemplateSize.XLARGE, new SizeThreshold(
            TemplateSize.XLARGE,
            75_000, Integer.MAX_VALUE,
            Duration.ofSeconds(30),
            Duration.ofSeconds(5),
            200,
            500.0
        )
    );

    private final Map<TemplateSize, SizeThreshold> thresholds;
    private final Set<String> whitelist;

    public AutosmokeSizeThresholds() {
        this.thresholds = new ConcurrentHashMap<>(DEFAULT_THRESHOLDS);
        this.whitelist = ConcurrentHashMap.newKeySet();
    }

    /**
     * Determines the size category for a block count.
     *
     * @param blockCount the number of blocks
     * @return the size category
     */
    public TemplateSize categorize(int blockCount) {
        for (SizeThreshold threshold : thresholds.values()) {
            if (threshold.matches(blockCount)) {
                return threshold.size();
            }
        }
        return TemplateSize.XLARGE;
    }

    /**
     * Gets the threshold for a size category.
     *
     * @param size the size category
     * @return the threshold configuration
     */
    public SizeThreshold getThreshold(TemplateSize size) {
        return thresholds.get(size);
    }

    /**
     * Gets the threshold for a block count.
     *
     * @param blockCount the number of blocks
     * @return the threshold configuration
     */
    public SizeThreshold getThresholdForBlocks(int blockCount) {
        return getThreshold(categorize(blockCount));
    }

    /**
     * Validates a template against size thresholds.
     * DD33: Size-based threshold validation.
     *
     * @param templateId the template ID
     * @param blockCount actual block count
     * @param entityCount actual entity count
     * @param buildTime actual build time
     * @param rollbackTime actual rollback time
     * @return validation result
     */
    public ThresholdResult validate(
            String templateId,
            int blockCount,
            int entityCount,
            Duration buildTime,
            Duration rollbackTime) {

        // Check whitelist
        if (whitelist.contains(templateId)) {
            TemplateSize size = categorize(blockCount);
            return ThresholdResult.pass(templateId, size, getThreshold(size),
                buildTime, rollbackTime, blockCount, entityCount);
        }

        TemplateSize size = categorize(blockCount);
        SizeThreshold threshold = getThreshold(size);

        // Validate build time
        if (buildTime.compareTo(threshold.maxBuildTime()) > 0) {
            return ThresholdResult.fail(templateId, size, threshold,
                buildTime, rollbackTime, blockCount, entityCount,
                String.format("Build time %dms exceeds threshold %dms for %s templates",
                    buildTime.toMillis(), threshold.maxBuildTime().toMillis(), size));
        }

        // Validate rollback time
        if (rollbackTime.compareTo(threshold.maxRollbackTime()) > 0) {
            return ThresholdResult.fail(templateId, size, threshold,
                buildTime, rollbackTime, blockCount, entityCount,
                String.format("Rollback time %dms exceeds threshold %dms for %s templates",
                    rollbackTime.toMillis(), threshold.maxRollbackTime().toMillis(), size));
        }

        // Validate entity count
        if (entityCount > threshold.maxEntities()) {
            return ThresholdResult.fail(templateId, size, threshold,
                buildTime, rollbackTime, blockCount, entityCount,
                String.format("Entity count %d exceeds threshold %d for %s templates",
                    entityCount, threshold.maxEntities(), size));
        }

        return ThresholdResult.pass(templateId, size, threshold,
            buildTime, rollbackTime, blockCount, entityCount);
    }

    /**
     * Adds a template to the whitelist.
     * DD33: Whitelisted templates skip threshold validation.
     *
     * @param templateId the template ID
     */
    public void addToWhitelist(String templateId) {
        whitelist.add(templateId);
    }

    /**
     * Removes a template from the whitelist.
     *
     * @param templateId the template ID
     */
    public void removeFromWhitelist(String templateId) {
        whitelist.remove(templateId);
    }

    /**
     * Checks if a template is whitelisted.
     *
     * @param templateId the template ID
     * @return true if whitelisted
     */
    public boolean isWhitelisted(String templateId) {
        return whitelist.contains(templateId);
    }

    /**
     * Gets all whitelisted templates.
     *
     * @return set of whitelisted template IDs
     */
    public Set<String> getWhitelist() {
        return Set.copyOf(whitelist);
    }

    /**
     * Updates a threshold configuration.
     *
     * @param threshold the new threshold
     */
    public void updateThreshold(SizeThreshold threshold) {
        thresholds.put(threshold.size(), threshold);
    }

    /**
     * Resets thresholds to defaults.
     */
    public void resetToDefaults() {
        thresholds.clear();
        thresholds.putAll(DEFAULT_THRESHOLDS);
    }

    /**
     * Gets all current thresholds.
     *
     * @return map of size to threshold
     */
    public Map<TemplateSize, SizeThreshold> getAllThresholds() {
        return Map.copyOf(thresholds);
    }
}
