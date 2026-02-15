package com.devmod.config.gamedesign;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("GameDesignConfig extended tests")
class GameDesignConfigExtendedTest {

    // ================================================================
    // Default values
    // ================================================================

    @Nested
    @DisplayName("defaults")
    class DefaultValues {

        @Test
        @DisplayName("new config has version 1")
        void defaultVersion() {
            assertEquals(1, new GameDesignConfig().getVersion());
        }

        @Test
        @DisplayName("all sub-configs are non-null by default")
        void allSubConfigsNonNull() {
            var config = new GameDesignConfig();
            assertNotNull(config.getResonance());
            assertNotNull(config.getContracts());
            assertNotNull(config.getSignatureWeapons());
            assertNotNull(config.getNemesis());
            assertNotNull(config.getTide());
        }

        @Test
        @DisplayName("resonance defaults are sensible")
        void resonanceDefaults() {
            var r = new GameDesignConfig().getResonance();
            assertTrue(r.enabled);
            assertTrue(r.duoWindowMs > 0);
            assertTrue(r.trinityWindowMs > 0);
            assertTrue(r.apocalypseWindowMs > 0);
            // Windows should be progressively tighter
            assertTrue(r.duoWindowMs > r.trinityWindowMs);
            assertTrue(r.trinityWindowMs > r.apocalypseWindowMs);
            // Damage multipliers should be progressively higher
            assertTrue(r.duoDamageMultiplier < r.trinityDamageMultiplier);
            assertTrue(r.trinityDamageMultiplier < r.apocalypseDamageMultiplier);
        }

        @Test
        @DisplayName("tide level thresholds are ordered")
        void tideLevelThresholdsOrdered() {
            var t = new GameDesignConfig().getTide();
            assertTrue(t.calmMax < t.risingMax);
            assertTrue(t.risingMax < t.highMax);
            assertTrue(t.highMax < t.stormMax);
            assertTrue(t.stormMax < t.maxTide);
        }

        @Test
        @DisplayName("tide stat bonuses increase with level")
        void tideStatBonusesIncrease() {
            var t = new GameDesignConfig().getTide();
            assertTrue(t.risingStatBonus < t.highStatBonus);
            assertTrue(t.highStatBonus < t.stormStatBonus);
            assertTrue(t.stormStatBonus < t.apocalypseStatBonus);
        }

        @Test
        @DisplayName("signature weapon evolution stages are ordered")
        void sigWeaponStagesOrdered() {
            var sw = new GameDesignConfig().getSignatureWeapons();
            assertTrue(sw.stage2TraitsRequired < sw.stage3TraitsRequired);
            assertTrue(sw.stage3TraitsRequired < sw.stage4TraitsRequired);
        }

        @Test
        @DisplayName("nemesis scar levels are ordered")
        void nemesisScarLevelsOrdered() {
            var n = new GameDesignConfig().getNemesis();
            assertTrue(n.scarLevel1Defeats < n.scarLevel2Defeats);
            assertTrue(n.scarLevel2Defeats < n.scarLevel3Defeats);
        }
    }

    // ================================================================
    // Setters with null safety
    // ================================================================

    @Nested
    @DisplayName("setters with null safety")
    class NullSafeSetters {

        @Test
        @DisplayName("setResonance with null creates new default")
        void setResonanceNull() {
            var config = new GameDesignConfig();
            config.setResonance(null);
            assertNotNull(config.getResonance());
        }

        @Test
        @DisplayName("setContracts with null creates new default")
        void setContractsNull() {
            var config = new GameDesignConfig();
            config.setContracts(null);
            assertNotNull(config.getContracts());
        }

        @Test
        @DisplayName("setSignatureWeapons with null creates new default")
        void setSignatureWeaponsNull() {
            var config = new GameDesignConfig();
            config.setSignatureWeapons(null);
            assertNotNull(config.getSignatureWeapons());
        }

        @Test
        @DisplayName("setNemesis with null creates new default")
        void setNemesisNull() {
            var config = new GameDesignConfig();
            config.setNemesis(null);
            assertNotNull(config.getNemesis());
        }

        @Test
        @DisplayName("setTide with null creates new default")
        void setTideNull() {
            var config = new GameDesignConfig();
            config.setTide(null);
            assertNotNull(config.getTide());
        }
    }

    // ================================================================
    // Copy depth
    // ================================================================

    @Nested
    @DisplayName("copy completeness")
    class CopyCompleteness {

        @Test
        @DisplayName("copy preserves version")
        void copyPreservesVersion() {
            var config = new GameDesignConfig();
            config.setVersion(42);
            assertEquals(42, config.copy().getVersion());
        }

        @Test
        @DisplayName("copy preserves signature weapons fields")
        void copyPreservesSignatureWeapons() {
            var config = new GameDesignConfig();
            config.getSignatureWeapons().headshotThreshold = 999;
            config.getSignatureWeapons().executionerBonus = 0.75f;

            var copy = config.copy();
            assertEquals(999, copy.getSignatureWeapons().headshotThreshold);
            assertEquals(0.75f, copy.getSignatureWeapons().executionerBonus, 0.0001f);

            // Verify independence
            copy.getSignatureWeapons().headshotThreshold = 1;
            assertEquals(999, config.getSignatureWeapons().headshotThreshold);
        }

        @Test
        @DisplayName("copy preserves nemesis fields")
        void copyPreservesNemesis() {
            var config = new GameDesignConfig();
            config.getNemesis().memoryDurationDays = 90;
            config.getNemesis().projectileDeflectionChance = 0.99f;

            var copy = config.copy();
            assertEquals(90, copy.getNemesis().memoryDurationDays);
            assertEquals(0.99f, copy.getNemesis().projectileDeflectionChance, 0.0001f);

            // Verify independence
            copy.getNemesis().memoryDurationDays = 1;
            assertEquals(90, config.getNemesis().memoryDurationDays);
        }

        @Test
        @DisplayName("copy preserves contracts fields")
        void copyPreservesContracts() {
            var config = new GameDesignConfig();
            config.getContracts().glassReward = 10.0f;
            config.getContracts().majorTierMinWave = 7;

            var copy = config.copy();
            assertEquals(10.0f, copy.getContracts().glassReward, 0.0001f);
            assertEquals(7, copy.getContracts().majorTierMinWave);

            // Verify independence
            copy.getContracts().glassReward = 1.0f;
            assertEquals(10.0f, config.getContracts().glassReward, 0.0001f);
        }
    }
}
