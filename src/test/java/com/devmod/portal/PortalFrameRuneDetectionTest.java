package com.devmod.portal;

import com.devmod.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for portal frame rune detection logic.
 * Tests that runes placed on portal frames are correctly detected and their effects applied.
 */
class PortalFrameRuneDetectionTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    @Nested
    @DisplayName("Rune Effect Calculation")
    class RuneEffectCalculation {

        @Test
        @DisplayName("Empty rune set returns default values")
        void emptyRuneSetReturnsDefaults() {
            Set<RuneType> runes = EnumSet.noneOf(RuneType.class);
            PortalRuneEffects effects = PortalRuneEffects.calculate(runes);

            assertFalse(effects.instantTeleport());
            assertFalse(effects.crossDimension());
            assertEquals(PortalRuneEffects.DEFAULT_RANGE, effects.linkRange());
        }

        @Test
        @DisplayName("HASTE rune enables instant teleport")
        void hasteEnablesInstantTeleport() {
            Set<RuneType> runes = EnumSet.of(RuneType.HASTE);
            PortalRuneEffects effects = PortalRuneEffects.calculate(runes);

            assertTrue(effects.instantTeleport());
            assertFalse(effects.crossDimension());
        }

        @Test
        @DisplayName("GATE rune enables cross-dimension linking")
        void gateEnablesCrossDimension() {
            Set<RuneType> runes = EnumSet.of(RuneType.GATE);
            PortalRuneEffects effects = PortalRuneEffects.calculate(runes);

            assertFalse(effects.instantTeleport());
            assertTrue(effects.crossDimension());
        }

        @Test
        @DisplayName("ENHANCER increases link range")
        void enhancerIncreasesRange() {
            Set<RuneType> runes = EnumSet.of(RuneType.ENHANCER);
            PortalRuneEffects effects = PortalRuneEffects.calculate(runes);

            assertTrue(effects.linkRange() > PortalRuneEffects.DEFAULT_RANGE,
                "ENHANCER should increase range beyond default");
        }

        @Test
        @DisplayName("STRONG_ENHANCER increases range more than ENHANCER")
        void strongEnhancerIncreasesRangeMore() {
            Set<RuneType> enhancerOnly = EnumSet.of(RuneType.ENHANCER);
            Set<RuneType> strongOnly = EnumSet.of(RuneType.STRONG_ENHANCER);

            PortalRuneEffects enhancerEffects = PortalRuneEffects.calculate(enhancerOnly);
            PortalRuneEffects strongEffects = PortalRuneEffects.calculate(strongOnly);

            assertTrue(strongEffects.linkRange() > enhancerEffects.linkRange(),
                "STRONG_ENHANCER should provide more range than ENHANCER");
        }

        @Test
        @DisplayName("INFINITY provides unlimited range")
        void infinityProvidesUnlimitedRange() {
            Set<RuneType> runes = EnumSet.of(RuneType.INFINITY);
            PortalRuneEffects effects = PortalRuneEffects.calculate(runes);

            assertEquals(Integer.MAX_VALUE, effects.linkRange(),
                "INFINITY should provide unlimited range");
        }

        @Test
        @DisplayName("Multiple runes combine effects")
        void multipleRunesCombineEffects() {
            Set<RuneType> runes = EnumSet.of(RuneType.HASTE, RuneType.GATE, RuneType.ENHANCER);
            PortalRuneEffects effects = PortalRuneEffects.calculate(runes);

            assertTrue(effects.instantTeleport(), "Should have instant teleport from HASTE");
            assertTrue(effects.crossDimension(), "Should have cross-dimension from GATE");
            assertTrue(effects.linkRange() > PortalRuneEffects.DEFAULT_RANGE,
                "Should have increased range from ENHANCER");
        }

        @Test
        @DisplayName("Range bonuses stack additively")
        void rangeBonusesStackAdditively() {
            Set<RuneType> enhancerOnly = EnumSet.of(RuneType.ENHANCER);
            Set<RuneType> both = EnumSet.of(RuneType.ENHANCER, RuneType.STRONG_ENHANCER);

            PortalRuneEffects enhancerEffects = PortalRuneEffects.calculate(enhancerOnly);
            PortalRuneEffects bothEffects = PortalRuneEffects.calculate(both);

            // Both enhancers should provide more range than just one
            assertTrue(bothEffects.linkRange() > enhancerEffects.linkRange(),
                "Multiple enhancers should stack");
        }

        @Test
        @DisplayName("INFINITY overrides other range bonuses")
        void infinityOverridesOtherRangeBonuses() {
            Set<RuneType> withInfinity = EnumSet.of(RuneType.ENHANCER, RuneType.INFINITY);
            PortalRuneEffects effects = PortalRuneEffects.calculate(withInfinity);

            assertEquals(Integer.MAX_VALUE, effects.linkRange(),
                "INFINITY should override other range calculations");
        }
    }

    @Nested
    @DisplayName("Default Range Configuration")
    class DefaultRangeConfiguration {

        @Test
        @DisplayName("Default range is 100 blocks")
        void defaultRangeIs100() {
            assertEquals(100, PortalRuneEffects.DEFAULT_RANGE,
                "Default portal linking range should be 100 blocks");
        }

        @Test
        @DisplayName("ENHANCER bonus is 100 blocks")
        void enhancerBonusIs100() {
            assertEquals(100, RuneType.ENHANCER.getRangeBonus(),
                "ENHANCER should add 100 blocks to range");
        }

        @Test
        @DisplayName("STRONG_ENHANCER bonus is 500 blocks")
        void strongEnhancerBonusIs500() {
            assertEquals(500, RuneType.STRONG_ENHANCER.getRangeBonus(),
                "STRONG_ENHANCER should add 500 blocks to range");
        }
    }
}
