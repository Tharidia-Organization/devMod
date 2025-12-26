package com.devmod.arena.autosmoke;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devmod.arena.autosmoke.AutosmokeSizeThresholds.TemplateSize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutosmokeSizeThresholdsDirectTest {

    @Test
    @DisplayName("categorize assigns size bands at boundary values")
    void categorizeAssignsSizeBands() {
        AutosmokeSizeThresholds thresholds = new AutosmokeSizeThresholds();

        assertEquals(TemplateSize.SMALL, thresholds.categorize(0));
        assertEquals(TemplateSize.SMALL, thresholds.categorize(4_999));
        assertEquals(TemplateSize.MEDIUM, thresholds.categorize(5_000));
        assertEquals(TemplateSize.MEDIUM, thresholds.categorize(24_999));
        assertEquals(TemplateSize.LARGE, thresholds.categorize(25_000));
        assertEquals(TemplateSize.LARGE, thresholds.categorize(74_999));
        assertEquals(TemplateSize.XLARGE, thresholds.categorize(75_000));
    }

    @Test
    @DisplayName("whitelisted templates bypass size validation")
    void whitelistBypassesValidation() {
        AutosmokeSizeThresholds thresholds = new AutosmokeSizeThresholds();
        String templateId = "devmod:whitelist_test";
        thresholds.addToWhitelist(templateId);

        AutosmokeSizeThresholds.ThresholdResult result = thresholds.validate(
            templateId,
            1_000,
            999,
            Duration.ofSeconds(99),
            Duration.ofSeconds(99)
        );

        assertTrue(thresholds.isWhitelisted(templateId));
        assertTrue(result.passed());
        assertNull(result.failureReason());
        assertEquals(TemplateSize.SMALL, result.detectedSize());
    }

    @Test
    @DisplayName("validate fails on build time above threshold")
    void validateFailsOnBuildTime() {
        AutosmokeSizeThresholds thresholds = new AutosmokeSizeThresholds();

        AutosmokeSizeThresholds.ThresholdResult result = thresholds.validate(
            "devmod:slow_build",
            1_000,
            1,
            Duration.ofSeconds(3),
            Duration.ofMillis(100)
        );

        assertFalse(result.passed());
        assertEquals(TemplateSize.SMALL, result.detectedSize());
        assertTrue(result.failureReason().contains("Build time"));
    }

    @Test
    @DisplayName("validate fails on rollback time and entity count thresholds")
    void validateFailsOnRollbackOrEntities() {
        AutosmokeSizeThresholds thresholds = new AutosmokeSizeThresholds();

        AutosmokeSizeThresholds.ThresholdResult rollbackFail = thresholds.validate(
            "devmod:slow_rollback",
            1_000,
            1,
            Duration.ofSeconds(1),
            Duration.ofMillis(600)
        );

        assertFalse(rollbackFail.passed());
        assertTrue(rollbackFail.failureReason().contains("Rollback time"));

        AutosmokeSizeThresholds.ThresholdResult entityFail = thresholds.validate(
            "devmod:too_many_entities",
            1_000,
            25,
            Duration.ofSeconds(1),
            Duration.ofMillis(100)
        );

        assertFalse(entityFail.passed());
        assertTrue(entityFail.failureReason().contains("Entity count"));
    }
}
