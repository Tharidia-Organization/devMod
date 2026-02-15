package com.devmod.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devmod.compat.mods.iris.IrisCompat;

@DisplayName("IrisCompat graceful degradation")
class IrisCompatFallbackTest {

    @Test
    @DisplayName("isAvailable returns false without Iris")
    void isAvailableReturnsFalse() {
        assertFalse(IrisCompat.isAvailable());
    }

    @Test
    @DisplayName("isApiAvailable returns false without Iris")
    void isApiAvailableReturnsFalse() {
        assertFalse(IrisCompat.isApiAvailable());
    }

    @Test
    @DisplayName("areShadersEnabled returns false without Iris")
    void areShadersEnabledReturnsFalse() {
        assertFalse(IrisCompat.areShadersEnabled());
    }

    @Test
    @DisplayName("getCurrentShaderPack returns null without Iris")
    void getCurrentShaderPackReturnsNull() {
        assertNull(IrisCompat.getCurrentShaderPack());
    }

    @Test
    @DisplayName("isRenderingShadowPass returns false without Iris")
    void isRenderingShadowPassReturnsFalse() {
        assertFalse(IrisCompat.isRenderingShadowPass());
    }

    @Test
    @DisplayName("shouldAdjustForShaders returns false without Iris")
    void shouldAdjustForShadersReturnsFalse() {
        assertFalse(IrisCompat.shouldAdjustForShaders());
    }

    @Test
    @DisplayName("getShaderStatus returns map with available=false")
    void getShaderStatusHasAvailableFalse() {
        Map<String, Object> status = IrisCompat.getShaderStatus();
        assertNotNull(status);
        assertEquals(false, status.get("available"));
    }

    @Test
    @DisplayName("getStatusSummary indicates not available")
    void getStatusSummaryIndicatesNotAvailable() {
        String summary = IrisCompat.getStatusSummary();
        assertTrue(summary.contains("not available"));
    }

    @Test
    @DisplayName("modId is iris")
    void modIdIsIris() {
        var compat = new IrisCompat();
        assertEquals("iris", compat.modId());
    }

    @Test
    @DisplayName("displayName is Iris Shaders")
    void displayNameIsIrisShaders() {
        var compat = new IrisCompat();
        assertEquals("Iris Shaders", compat.displayName());
    }

    @Test
    @DisplayName("priority is low number for high priority")
    void priorityIsHighPriority() {
        var compat = new IrisCompat();
        assertTrue(compat.priority() < 100);
    }

    @Test
    @DisplayName("featureDescription is non-empty")
    void featureDescriptionIsNonEmpty() {
        var compat = new IrisCompat();
        assertFalse(compat.getFeatureDescription().isEmpty());
    }
}
