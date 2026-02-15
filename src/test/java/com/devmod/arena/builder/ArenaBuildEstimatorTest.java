package com.devmod.arena.builder;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.devmod.arena.registry.ArenaTemplate;

class ArenaBuildEstimatorTest {

    private final ArenaBuildEstimator estimator = new ArenaBuildEstimator(null, null);

    private ArenaTemplate simpleTemplate(int size) {
        return ArenaTemplate.builder("test")
            .version(1).schemaVersion(1)
            .size(size)
            .floor(new ArenaTemplate.Floor(64, 1, "minecraft:stone_bricks", "solid", null, 0))
            .limits(new ArenaTemplate.Limits(0, 0, 0))
            .build();
    }

    private ArenaTemplate bossTemplate(int size) {
        return ArenaTemplate.builder("boss_arena")
            .version(1).schemaVersion(1)
            .size(size)
            .floor(new ArenaTemplate.Floor(64, 1, "minecraft:stone_bricks", "solid", null, 0))
            .limits(new ArenaTemplate.Limits(0, 0, 0))
            .tags(List.of("boss", "endgame"))
            .build();
    }

    // === determineMaxBlocks ===

    @Test
    void determineMaxBlocks_defaultForNormalTemplate() {
        ArenaTemplate t = simpleTemplate(32);
        assertEquals(ArenaBuildEstimator.DEFAULT_MAX_BLOCKS, estimator.determineMaxBlocks(t));
    }

    @Test
    void determineMaxBlocks_bossTemplateGetsBossLimit() {
        ArenaTemplate t = bossTemplate(64);
        assertEquals(ArenaBuildEstimator.BOSS_MAX_BLOCKS, estimator.determineMaxBlocks(t));
    }

    @Test
    void determineMaxBlocks_templateLimitCappedByHardCap() {
        ArenaTemplate t = ArenaTemplate.builder("test")
            .version(1).schemaVersion(1)
            .size(32)
            .limits(new ArenaTemplate.Limits(0, 999_999, 0))
            .build();
        assertEquals(ArenaBuildEstimator.HARD_CAP_BLOCKS, estimator.determineMaxBlocks(t));
    }

    @Test
    void determineMaxBlocks_templateLimitUsedIfBelowCap() {
        ArenaTemplate t = ArenaTemplate.builder("test")
            .version(1).schemaVersion(1)
            .size(32)
            .limits(new ArenaTemplate.Limits(0, 10_000, 0))
            .build();
        assertEquals(10_000, estimator.determineMaxBlocks(t));
    }

    // === determineMaxBuildTimeMs ===

    @Test
    void determineMaxBuildTimeMs_default() {
        ArenaTemplate t = simpleTemplate(32);
        assertEquals(ArenaBuildEstimator.DEFAULT_MAX_BUILD_TIME_MS, estimator.determineMaxBuildTimeMs(t));
    }

    @Test
    void determineMaxBuildTimeMs_bossGetsMoreTime() {
        ArenaTemplate t = bossTemplate(64);
        assertEquals(ArenaBuildEstimator.BOSS_MAX_BUILD_TIME_MS, estimator.determineMaxBuildTimeMs(t));
    }

    @Test
    void determineMaxBuildTimeMs_templateOverride() {
        ArenaTemplate t = ArenaTemplate.builder("test")
            .version(1).schemaVersion(1)
            .size(32)
            .limits(new ArenaTemplate.Limits(20_000, 0, 0))
            .build();
        assertEquals(20_000, estimator.determineMaxBuildTimeMs(t));
    }

    // === isBossTemplate ===

    @Test
    void isBossTemplate_withBossTag() {
        assertTrue(estimator.isBossTemplate(bossTemplate(32)));
    }

    @Test
    void isBossTemplate_withLargeTag() {
        ArenaTemplate t = ArenaTemplate.builder("test")
            .version(1).schemaVersion(1)
            .size(32)
            .tags(List.of("large"))
            .build();
        assertTrue(estimator.isBossTemplate(t));
    }

    @Test
    void isBossTemplate_withoutRelevantTags() {
        ArenaTemplate t = ArenaTemplate.builder("test")
            .version(1).schemaVersion(1)
            .size(32)
            .tags(List.of("small", "pvp"))
            .build();
        assertFalse(estimator.isBossTemplate(t));
    }

    @Test
    void isBossTemplate_nullTags() {
        ArenaTemplate t = simpleTemplate(32);
        assertFalse(estimator.isBossTemplate(t));
    }

    // === calculateAccuracy ===

    @Test
    void accuracy_excellent() {
        var result = estimator.calculateAccuracy(1000, 1100); // 10% deviation
        assertEquals(ArenaBuilder.AccuracyBand.EXCELLENT, result.band());
        assertFalse(result.needsCalibration());
    }

    @Test
    void accuracy_good() {
        var result = estimator.calculateAccuracy(1000, 1300); // 30% deviation
        assertEquals(ArenaBuilder.AccuracyBand.GOOD, result.band());
    }

    @Test
    void accuracy_acceptable() {
        var result = estimator.calculateAccuracy(1000, 1450); // 45% deviation
        assertEquals(ArenaBuilder.AccuracyBand.ACCEPTABLE, result.band());
    }

    @Test
    void accuracy_poor() {
        var result = estimator.calculateAccuracy(1000, 2000); // 100% deviation
        assertEquals(ArenaBuilder.AccuracyBand.POOR, result.band());
        assertTrue(result.needsCalibration());
    }

    @Test
    void accuracy_zeroEstimate() {
        var result = estimator.calculateAccuracy(0, 1000);
        assertEquals(ArenaBuilder.AccuracyBand.POOR, result.band());
    }

    // === resolveBuildOrder / resolveBuildPriority ===

    @Test
    void resolveBuildOrder_defaultsToFloorFirst() {
        ArenaTemplate t = simpleTemplate(32);
        assertEquals(ArenaTemplate.BuildSettings.Order.FLOOR_FIRST, estimator.resolveBuildOrder(t));
    }

    @Test
    void resolveBuildPriority_defaultsToSync() {
        ArenaTemplate t = simpleTemplate(32);
        assertEquals(ArenaTemplate.BuildSettings.Priority.SYNC, estimator.resolveBuildPriority(t));
    }

    // === estimateBuildTimeMs ===

    @Test
    void estimateBuildTimeMs_returnsPositiveValue() {
        ArenaTemplate t = simpleTemplate(32);
        long estimate = estimator.estimateBuildTimeMs(t);
        assertTrue(estimate > 0, "Estimate should be positive");
    }

    @Test
    void estimateBuildTimeMs_largerArenasTakeLonger() {
        long small = estimator.estimateBuildTimeMs(simpleTemplate(16));
        long large = estimator.estimateBuildTimeMs(simpleTemplate(64));
        assertTrue(large > small, "Larger arenas should estimate longer build times");
    }
}
