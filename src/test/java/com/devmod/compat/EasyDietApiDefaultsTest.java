package com.devmod.compat;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.devmod.compat.mods.easydiet.EasyDietApi;

/**
 * Tests for EasyDietApi constants, status reporting, and default-value
 * logic that can be verified without the Easy-Diet mod present.
 *
 * When Easy-Diet is not installed, the API should return neutral defaults
 * (0.5) that neither trigger bonuses nor penalties.
 */
class EasyDietApiDefaultsTest {

    // ================================================================
    // Threshold constants
    // ================================================================

    @Test
    void wellFedThreshold_isHigherThanMalnourished() {
        assertTrue(EasyDietApi.WELL_FED_THRESHOLD > EasyDietApi.MALNOURISHED_THRESHOLD);
    }

    @Test
    void malnourishedThreshold_isHigherThanCritical() {
        assertTrue(EasyDietApi.MALNOURISHED_THRESHOLD > EasyDietApi.CRITICAL_THRESHOLD);
    }

    @Test
    void categoryCount_isSix() {
        assertEquals(6, EasyDietApi.CATEGORY_COUNT);
    }

    // ================================================================
    // Status constants
    // ================================================================

    @Test
    void statusConstants_areDistinct() {
        assertNotEquals(EasyDietApi.STATUS_READY, EasyDietApi.STATUS_FAILED);
        assertNotEquals(EasyDietApi.STATUS_READY, EasyDietApi.STATUS_NOT_INITIALIZED);
        assertNotEquals(EasyDietApi.STATUS_READY, EasyDietApi.STATUS_UNAVAILABLE);
    }

    // ================================================================
    // getReflectionStatus — without Easy-Diet installed
    // ================================================================

    @Test
    void reflectionStatus_withoutEasyDiet_isNotReady() {
        String status = EasyDietApi.getReflectionStatus();
        // Should be either "unavailable" or "not initialized" — never "ready"
        assertNotEquals(EasyDietApi.STATUS_READY, status);
    }

    // ================================================================
    // isReady — without Easy-Diet
    // ================================================================

    @Test
    void isReady_withoutEasyDiet_returnsFalse() {
        // isReady is package-private, so we access via the public getReflectionStatus
        String status = EasyDietApi.getReflectionStatus();
        assertNotEquals(EasyDietApi.STATUS_READY, status,
            "API should not report ready when Easy-Diet is not installed");
    }

    // ================================================================
    // Default value contract
    // ================================================================

    @Test
    void defaultValues_areNeutral() {
        // The default of 0.5 should be:
        // - Above MALNOURISHED_THRESHOLD (0.2) → no penalty
        // - Below WELL_FED_THRESHOLD (0.6) → no bonus
        float defaultValue = 0.5f;
        assertTrue(defaultValue > EasyDietApi.MALNOURISHED_THRESHOLD);
        assertTrue(defaultValue < EasyDietApi.WELL_FED_THRESHOLD);
        assertTrue(defaultValue > EasyDietApi.CRITICAL_THRESHOLD);
    }

    // ================================================================
    // getDietValue — boundary validation
    // ================================================================

    @Test
    void getDietValue_negativeIndex_returnsNeutral() {
        float value = EasyDietApi.getDietValue(null, -1);
        assertEquals(0.5f, value);
    }

    @Test
    void getDietValue_outOfBoundsIndex_returnsNeutral() {
        float value = EasyDietApi.getDietValue(null, EasyDietApi.CATEGORY_COUNT);
        assertEquals(0.5f, value);
    }

    @Test
    void getDietValue_validIndex_withoutMod_returnsNeutral() {
        // With null player and no mod, should return default
        float value = EasyDietApi.getDietValue(null, 0);
        assertEquals(0.5f, value);
    }
}
