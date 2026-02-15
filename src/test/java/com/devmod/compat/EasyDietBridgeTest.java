package com.devmod.compat;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devmod.compat.mods.easydiet.EasyDietBridge;

@DisplayName("EasyDietBridge null-safety")
class EasyDietBridgeTest {

    @Test
    @DisplayName("getDietValues with null player returns neutral defaults")
    void getDietValuesNullPlayerReturnsDefaults() {
        float[] values = EasyDietBridge.getDietValues(null);
        assertNotNull(values);
        assertEquals(6, values.length);
        for (float v : values) {
            assertEquals(0.5f, v, "Each default value should be 0.5 (neutral)");
        }
    }

    @Test
    @DisplayName("getNutritionBalance with null player returns neutral")
    void getNutritionBalanceNullPlayerReturnsNeutral() {
        float balance = EasyDietBridge.getNutritionBalance(null);
        assertEquals(0.5f, balance);
    }

    @Test
    @DisplayName("isWellFed with null player returns false")
    void isWellFedNullPlayerReturnsFalse() {
        assertFalse(EasyDietBridge.isWellFed(null));
    }

    @Test
    @DisplayName("isMalnourished with null player returns false")
    void isMalnourishedNullPlayerReturnsFalse() {
        assertFalse(EasyDietBridge.isMalnourished(null));
    }

    @Test
    @DisplayName("isCritical with null player returns false")
    void isCriticalNullPlayerReturnsFalse() {
        assertFalse(EasyDietBridge.isCritical(null));
    }

    @Test
    @DisplayName("getLowestValue with null player returns neutral")
    void getLowestValueNullPlayerReturnsNeutral() {
        float lowest = EasyDietBridge.getLowestValue(null);
        assertEquals(0.5f, lowest);
    }
}
