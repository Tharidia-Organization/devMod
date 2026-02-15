package com.devmod.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devmod.compat.mods.sodium.SodiumCompat;

@DisplayName("SodiumCompat graceful degradation")
class SodiumCompatFallbackTest {

    @Test
    @DisplayName("isAvailable returns false without Sodium")
    void isAvailableReturnsFalse() {
        assertFalse(SodiumCompat.isAvailable());
    }

    @Test
    @DisplayName("isApiAvailable returns false without Sodium")
    void isApiAvailableReturnsFalse() {
        assertFalse(SodiumCompat.isApiAvailable());
    }

    @Test
    @DisplayName("getQualityLevel returns null without Sodium")
    void getQualityLevelReturnsNull() {
        assertNull(SodiumCompat.getQualityLevel());
    }

    @Test
    @DisplayName("getPerformanceInfo returns empty map without Sodium")
    void getPerformanceInfoReturnsEmptyMap() {
        Map<String, Object> info = SodiumCompat.getPerformanceInfo();
        assertNotNull(info);
        assertTrue(info.isEmpty());
    }

    @Test
    @DisplayName("getStatusSummary indicates not available")
    void getStatusSummaryIndicatesNotAvailable() {
        String summary = SodiumCompat.getStatusSummary();
        assertTrue(summary.contains("not available"));
    }

    @Test
    @DisplayName("modId is sodium")
    void modIdIsSodium() {
        var compat = new SodiumCompat();
        assertEquals("sodium", compat.modId());
    }

    @Test
    @DisplayName("displayName is Sodium")
    void displayNameIsSodium() {
        var compat = new SodiumCompat();
        assertEquals("Sodium", compat.displayName());
    }

    @Test
    @DisplayName("priority is low number for high priority")
    void priorityIsHighPriority() {
        var compat = new SodiumCompat();
        assertTrue(compat.priority() < 100);
    }

    @Test
    @DisplayName("featureDescription is non-empty")
    void featureDescriptionIsNonEmpty() {
        var compat = new SodiumCompat();
        assertFalse(compat.getFeatureDescription().isEmpty());
    }

    @Test
    @DisplayName("MOD_ID constant matches modId()")
    void modIdConstantMatches() {
        assertEquals("sodium", SodiumCompat.MOD_ID);
    }
}
