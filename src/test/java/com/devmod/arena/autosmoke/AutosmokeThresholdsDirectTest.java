package com.devmod.arena.autosmoke;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutosmokeThresholdsDirectTest {

    @Test
    @DisplayName("forTemplate honors explicit overrides")
    void forTemplateHonorsExplicitOverrides() {
        AutosmokeThresholds custom = AutosmokeThresholds.customStrict(2, 10);
        AutosmokeThresholds.registerTemplateThresholds("devmod:unit_override", custom);

        assertSame(custom, AutosmokeThresholds.forTemplate("devmod:unit_override"));
    }

    @Test
    @DisplayName("forTemplate infers large/async modes and defaults to strict")
    void forTemplateInfersModes() {
        assertSame(AutosmokeThresholds.LARGE, AutosmokeThresholds.forTemplate("devmod:arena_large_64"));
        assertSame(AutosmokeThresholds.ASYNC, AutosmokeThresholds.forTemplate("devmod:stress_test"));
        assertSame(AutosmokeThresholds.STRICT, AutosmokeThresholds.forTemplate("devmod:arena_tiny"));
    }

    @Test
    @DisplayName("validate returns pass and no violations when within thresholds")
    void validatePassesWithinThresholds() {
        AutosmokeThresholds.ValidationResult result = AutosmokeThresholds.STRICT.validate(4, 60, 256);

        assertTrue(result.passed());
        assertTrue(result.playersOk());
        assertTrue(result.durationOk());
        assertTrue(result.memoryOk());
        assertEquals("None", result.getViolations());
    }

    @Test
    @DisplayName("validate reports all violations when exceeded")
    void validateReportsViolations() {
        AutosmokeThresholds.ValidationResult result = AutosmokeThresholds.STRICT.validate(5, 61, 257);

        assertFalse(result.passed());
        assertFalse(result.playersOk());
        assertFalse(result.durationOk());
        assertFalse(result.memoryOk());
        assertEquals("Players: 5 > 4; Duration: 61s > 60s; Memory: 257MB > 256MB;", result.getViolations());
    }
}
