package com.devmod.portal;

import com.devmod.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for portal configuration system.
 * Tests default values, range settings, behavior modes, and redstone integration.
 */
class PortalConfigTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    @Nested
    @DisplayName("Default Range Values")
    class DefaultRangeValues {

        @Test
        @DisplayName("Default range is 100 blocks")
        void defaultRangeIs100() {
            assertEquals(100, PortalConfig.getDefaultRange(),
                "Default portal linking range should be 100 blocks");
        }

        @Test
        @DisplayName("Range with enhancer is 1000 blocks")
        void rangeWithEnhancerIs1000() {
            assertEquals(1000, PortalConfig.getRangeWithEnhancer(),
                "Range with ENHANCER rune should be 1000 blocks");
        }

        @Test
        @DisplayName("Range with strong enhancer is 10000 blocks")
        void rangeWithStrongEnhancerIs10000() {
            assertEquals(10000, PortalConfig.getRangeWithStrongEnhancer(),
                "Range with STRONG_ENHANCER rune should be 10000 blocks");
        }
    }

    @Nested
    @DisplayName("Behavior Settings Defaults")
    class BehaviorSettingsDefaults {

        @Test
        @DisplayName("Unlimited range is disabled by default")
        void unlimitedRangeDisabledByDefault() {
            assertFalse(PortalConfig.isUnlimitedRange(),
                "Unlimited range should be disabled by default");
        }

        @Test
        @DisplayName("Always interdimensional is disabled by default")
        void alwaysInterdimDisabledByDefault() {
            assertFalse(PortalConfig.isAlwaysInterdimensional(),
                "Always interdimensional should be disabled by default");
        }

        @Test
        @DisplayName("Always haste is FALSE by default")
        void alwaysHasteDisabledByDefault() {
            assertEquals(PortalConfig.HasteMode.FALSE, PortalConfig.getAlwaysHasteMode(),
                "Always haste mode should be FALSE by default");
        }

        @Test
        @DisplayName("Private portals is disabled by default")
        void privatePortalsDisabledByDefault() {
            assertFalse(PortalConfig.isPrivatePortals(),
                "Private portals should be disabled by default");
        }
    }

    @Nested
    @DisplayName("Redstone Mode Settings")
    class RedstoneModeSettings {

        @Test
        @DisplayName("Redstone mode is IGNORE by default")
        void redstoneModeIgnoreByDefault() {
            assertEquals(PortalConfig.RedstoneMode.IGNORE, PortalConfig.getRedstoneMode(),
                "Redstone mode should be IGNORE by default");
        }

        @Test
        @DisplayName("RedstoneMode enum has all expected values")
        void redstoneModeEnumHasAllValues() {
            PortalConfig.RedstoneMode[] modes = PortalConfig.RedstoneMode.values();
            assertEquals(3, modes.length, "Should have exactly 3 redstone modes");
            assertNotNull(PortalConfig.RedstoneMode.OFF, "OFF mode should exist");
            assertNotNull(PortalConfig.RedstoneMode.ON, "ON mode should exist");
            assertNotNull(PortalConfig.RedstoneMode.IGNORE, "IGNORE mode should exist");
        }
    }

    @Nested
    @DisplayName("Haste Mode Settings")
    class HasteModeSettings {

        @Test
        @DisplayName("HasteMode enum has all expected values")
        void hasteModeEnumHasAllValues() {
            PortalConfig.HasteMode[] modes = PortalConfig.HasteMode.values();
            assertEquals(3, modes.length, "Should have exactly 3 haste modes");
            assertNotNull(PortalConfig.HasteMode.TRUE, "TRUE mode should exist");
            assertNotNull(PortalConfig.HasteMode.FALSE, "FALSE mode should exist");
            assertNotNull(PortalConfig.HasteMode.CREATIVE_ONLY, "CREATIVE_ONLY mode should exist");
        }
    }

    @Nested
    @DisplayName("Effective Range Calculation")
    class EffectiveRangeCalculation {

        @Test
        @DisplayName("Effective range with no runes uses default")
        void effectiveRangeNoRunes() {
            int effectiveRange = PortalConfig.calculateEffectiveRange(false, false, false);
            assertEquals(PortalConfig.getDefaultRange(), effectiveRange,
                "With no runes, should use default range");
        }

        @Test
        @DisplayName("Effective range with ENHANCER uses enhancer range")
        void effectiveRangeWithEnhancer() {
            int effectiveRange = PortalConfig.calculateEffectiveRange(true, false, false);
            assertEquals(PortalConfig.getRangeWithEnhancer(), effectiveRange,
                "With ENHANCER, should use enhancer range");
        }

        @Test
        @DisplayName("Effective range with STRONG_ENHANCER uses strong enhancer range")
        void effectiveRangeWithStrongEnhancer() {
            int effectiveRange = PortalConfig.calculateEffectiveRange(false, true, false);
            assertEquals(PortalConfig.getRangeWithStrongEnhancer(), effectiveRange,
                "With STRONG_ENHANCER, should use strong enhancer range");
        }

        @Test
        @DisplayName("Effective range with INFINITY uses MAX_VALUE")
        void effectiveRangeWithInfinity() {
            int effectiveRange = PortalConfig.calculateEffectiveRange(false, false, true);
            assertEquals(Integer.MAX_VALUE, effectiveRange,
                "With INFINITY, should use Integer.MAX_VALUE");
        }

        @Test
        @DisplayName("INFINITY takes precedence over other enhancers")
        void infinityTakesPrecedence() {
            int effectiveRange = PortalConfig.calculateEffectiveRange(true, true, true);
            assertEquals(Integer.MAX_VALUE, effectiveRange,
                "INFINITY should take precedence over other enhancers");
        }

        @Test
        @DisplayName("STRONG_ENHANCER takes precedence over ENHANCER")
        void strongEnhancerTakesPrecedence() {
            int effectiveRange = PortalConfig.calculateEffectiveRange(true, true, false);
            assertEquals(PortalConfig.getRangeWithStrongEnhancer(), effectiveRange,
                "STRONG_ENHANCER should take precedence over ENHANCER");
        }
    }

    @Nested
    @DisplayName("Integration with PortalRuneEffects")
    class IntegrationWithRuneEffects {

        @Test
        @DisplayName("PortalRuneEffects DEFAULT_RANGE matches config")
        void portalRuneEffectsDefaultRangeMatchesConfig() {
            assertEquals(PortalConfig.getDefaultRange(), PortalRuneEffects.DEFAULT_RANGE,
                "PortalRuneEffects.DEFAULT_RANGE should match PortalConfig.getDefaultRange()");
        }
    }
}
