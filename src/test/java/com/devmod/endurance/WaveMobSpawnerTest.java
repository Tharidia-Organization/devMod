package com.devmod.endurance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WaveMobSpawnerTest {

    private final WaveMobSpawner spawner = WaveMobSpawner.INSTANCE;

    // ========== getEliteChance ==========

    @Nested
    @DisplayName("getEliteChance")
    class GetEliteChance {

        @Test
        @DisplayName("returns 0 when baseChance is 0")
        void zeroBaseChance() {
            assertEquals(0f, spawner.getEliteChance(0f, 5, null), 0.001f);
        }

        @Test
        @DisplayName("returns 0 when baseChance is negative")
        void negativeBaseChance() {
            assertEquals(0f, spawner.getEliteChance(-0.1f, 5, null), 0.001f);
        }

        @Test
        @DisplayName("returns 0 for waves below 3")
        void earlyWavesReturnZero() {
            assertEquals(0f, spawner.getEliteChance(0.5f, 1, null), 0.001f);
            assertEquals(0f, spawner.getEliteChance(0.5f, 2, null), 0.001f);
        }

        // Note: getEliteChance for wave 3+ calls EffectiveConfig -> GameMechanicsConfig
        // which throws IllegalStateException in test JVM (config not registered).
        // Those paths are covered by integration tests.
    }

    // ========== shouldLogBossHp ==========

    @Nested
    @DisplayName("shouldLogBossHp")
    class ShouldLogBossHp {

        @Test
        @DisplayName("returns false for null config")
        void nullConfig() {
            assertFalse(WaveMobSpawner.shouldLogBossHp(null));
        }
    }

    // ========== WaveModifier enum ==========

    @Nested
    @DisplayName("WaveModifier enum")
    class WaveModifierEnum {

        @Test
        @DisplayName("all modifiers have display name and description")
        void allModifiersHaveMetadata() {
            for (WaveManager.WaveModifier mod : WaveManager.WaveModifier.values()) {
                assertNotNull(mod.getDisplayName(), mod.name() + " missing display name");
                assertNotNull(mod.getDescription(), mod.name() + " missing description");
                assertFalse(mod.getDisplayName().isEmpty(), mod.name() + " has empty display name");
                assertFalse(mod.getDescription().isEmpty(), mod.name() + " has empty description");
            }
        }

        @Test
        @DisplayName("eight modifiers exist")
        void eightModifiers() {
            assertEquals(8, WaveManager.WaveModifier.values().length);
        }
    }
}
