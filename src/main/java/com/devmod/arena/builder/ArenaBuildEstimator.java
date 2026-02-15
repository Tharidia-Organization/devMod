package com.devmod.arena.builder;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.devmod.arena.config.ArenaTemplateConfig;
import com.devmod.arena.registry.ArenaTemplate;

/**
 * Build estimation, validation, and accuracy calculation for arena builds.
 * Extracted from ArenaBuilder.
 */
class ArenaBuildEstimator {

    // DD8: Block limits by category
    static final int DEFAULT_MAX_BLOCKS = 50_000;
    static final int BOSS_MAX_BLOCKS = 100_000;
    static final int HARD_CAP_BLOCKS = 150_000;
    static final int DEFAULT_MAX_BUILD_TIME_MS = 5_000;
    static final int BOSS_MAX_BUILD_TIME_MS = 15_000;

    // DD10: Estimation constants
    private static final double MS_PER_BLOCK_BASELINE = 0.05;
    private static final double NBT_MULTIPLIER = 1.5;
    private static final double HAZARD_MULTIPLIER = 1.2;
    private static final int MIN_HISTORY_SAMPLES = 5;

    // DD10: Accuracy bands
    private static final double ACCURACY_EXCELLENT = 0.20;
    private static final double ACCURACY_GOOD = 0.35;
    private static final double ACCURACY_ACCEPTABLE = 0.50;

    @Nullable
    private final ArenaBuilder.BuildHistoryStore historyStore;
    @Nullable
    private final ArenaTemplateConfig.ConfigSnapshot configSnapshot;

    ArenaBuildEstimator(@Nullable ArenaBuilder.BuildHistoryStore historyStore,
                        @Nullable ArenaTemplateConfig.ConfigSnapshot configSnapshot) {
        this.historyStore = historyStore;
        this.configSnapshot = configSnapshot;
    }

    long estimateBuildTimeMs(ArenaTemplate template) {
        if (historyStore != null) {
            Long historical = historyStore.getP75BuildTime(template.id(), MIN_HISTORY_SAMPLES);
            if (historical != null) {
                return historical;
            }
        }
        return estimateHeuristic(template);
    }

    ArenaBuilder.AccuracyResult calculateAccuracy(long estimated, long actual) {
        if (estimated == 0) {
            return new ArenaBuilder.AccuracyResult(ArenaBuilder.AccuracyBand.POOR, 1.0);
        }

        double deviation = Math.abs((double)(actual - estimated) / estimated);

        ArenaBuilder.AccuracyBand band;
        if (deviation <= ACCURACY_EXCELLENT) {
            band = ArenaBuilder.AccuracyBand.EXCELLENT;
        } else if (deviation <= ACCURACY_GOOD) {
            band = ArenaBuilder.AccuracyBand.GOOD;
        } else if (deviation <= ACCURACY_ACCEPTABLE) {
            band = ArenaBuilder.AccuracyBand.ACCEPTABLE;
        } else {
            band = ArenaBuilder.AccuracyBand.POOR;
        }

        return new ArenaBuilder.AccuracyResult(band, deviation);
    }

    ArenaBuilder.BuildValidation validateBuild(ArenaTemplate template) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        BuildDryRun dryRun = BuildDryRunCalculator.calculate(template);
        int blocksRequired = dryRun.totalBlocks();
        int maxBlocks = determineMaxBlocks(template);
        int warnBlocks = (int) (maxBlocks * 0.80);

        if (blocksRequired > maxBlocks) {
            errors.add("Estimated blocks %d exceed limit %d".formatted(blocksRequired, maxBlocks));
        } else if (blocksRequired > warnBlocks) {
            warnings.add("Estimated blocks %d near limit %d".formatted(blocksRequired, maxBlocks));
        }

        long estimatedMs = estimateBuildTimeMs(template);
        long maxBuildTimeMs = determineMaxBuildTimeMs(template);
        long warnBuildTimeMs = (long) (maxBuildTimeMs * 0.80);
        if (estimatedMs > maxBuildTimeMs) {
            errors.add("Estimated build time %dms exceeds limit %dms".formatted(estimatedMs, maxBuildTimeMs));
        } else if (estimatedMs > warnBuildTimeMs) {
            warnings.add("Estimated build time %dms near limit %dms".formatted(estimatedMs, maxBuildTimeMs));
        }

        int sizeX = ArenaShapeHelper.getSizeX(template);
        int sizeZ = ArenaShapeHelper.getSizeZ(template);
        int chunksX = (int) Math.ceil(sizeX / 16.0);
        int chunksZ = (int) Math.ceil(sizeZ / 16.0);
        int chunksRequired = Math.max(1, chunksX * chunksZ);

        return new ArenaBuilder.BuildValidation(errors.isEmpty(), blocksRequired, chunksRequired, estimatedMs, warnings, errors);
    }

    int determineMaxBlocks(ArenaTemplate template) {
        if (template.limits() != null && template.limits().maxBlocks() > 0) {
            return Math.min(template.limits().maxBlocks(), HARD_CAP_BLOCKS);
        }

        int defaultMax = DEFAULT_MAX_BLOCKS;
        int bossMax = BOSS_MAX_BLOCKS;
        ArenaTemplateConfig.ConfigSnapshot snapshot = configSnapshot;
        if (snapshot != null) {
            defaultMax = snapshot.defaultMaxBlocks();
            bossMax = snapshot.bossMaxBlocks();
        }
        return Math.min(isBossTemplate(template) ? bossMax : defaultMax, HARD_CAP_BLOCKS);
    }

    long determineMaxBuildTimeMs(ArenaTemplate template) {
        if (template.limits() != null && template.limits().maxBuildTimeMs() > 0) {
            return template.limits().maxBuildTimeMs();
        }
        int defaultMax = DEFAULT_MAX_BUILD_TIME_MS;
        int bossMax = BOSS_MAX_BUILD_TIME_MS;
        ArenaTemplateConfig.ConfigSnapshot snapshot = configSnapshot;
        if (snapshot != null) {
            defaultMax = snapshot.defaultMaxBuildTimeMs();
            bossMax = snapshot.bossMaxBuildTimeMs();
        }
        return isBossTemplate(template) ? bossMax : defaultMax;
    }

    ArenaTemplate.BuildSettings.Order resolveBuildOrder(ArenaTemplate template) {
        if (template.buildSettings() == null || template.buildSettings().buildOrder() == null) {
            return ArenaTemplate.BuildSettings.Order.FLOOR_FIRST;
        }
        return template.buildSettings().buildOrder();
    }

    ArenaTemplate.BuildSettings.Priority resolveBuildPriority(ArenaTemplate template) {
        if (template.buildSettings() == null || template.buildSettings().buildPriority() == null) {
            return ArenaTemplate.BuildSettings.Priority.SYNC;
        }
        return template.buildSettings().buildPriority();
    }

    boolean isBossTemplate(ArenaTemplate template) {
        return template.tags() != null
            && (template.tags().contains("boss") || template.tags().contains("large"));
    }

    // === Private ===

    private long estimateHeuristic(ArenaTemplate template) {
        int estimatedBlocks = estimateBlockCount(template);
        double baseEstimate = estimatedBlocks * MS_PER_BLOCK_BASELINE;

        if (template.structureNbt() != null) {
            baseEstimate *= NBT_MULTIPLIER;
        }
        if (template.hazards() != null && template.hazards().size() > 10) {
            baseEstimate *= HAZARD_MULTIPLIER;
        }

        return (long) baseEstimate;
    }

    private int estimateBlockCount(ArenaTemplate template) {
        int sizeX = ArenaShapeHelper.getSizeX(template);
        int sizeZ = ArenaShapeHelper.getSizeZ(template);

        int floorBlocks = sizeX * sizeZ;
        if (template.floor() != null) {
            floorBlocks *= template.floor().thickness();
        }

        int wallBlocks = 0;
        if (template.walls() != null && template.walls().enabled()) {
            int perimeter = 2 * (sizeX + sizeZ - 2);
            wallBlocks = perimeter * template.walls().height() * template.walls().thickness();
        }

        int ceilingBlocks = 0;
        if (template.ceiling() != null && template.ceiling().enabled()) {
            ceilingBlocks = sizeX * sizeZ * template.ceiling().thickness();
        }

        return floorBlocks + wallBlocks + ceilingBlocks;
    }
}
