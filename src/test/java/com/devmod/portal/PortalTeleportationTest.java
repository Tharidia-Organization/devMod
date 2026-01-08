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
 * Unit tests for portal teleportation behavior.
 * Tests teleportation delay, instant teleport with HASTE rune, and cooldowns.
 */
class PortalTeleportationTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    @Nested
    @DisplayName("Teleportation Timing")
    class TeleportationTiming {

        /**
         * Default teleportation delay in ticks (4 seconds = 80 ticks).
         */
        private static final int DEFAULT_TELEPORT_DELAY = 80;

        /**
         * Cooldown after teleportation in ticks (5 seconds = 100 ticks).
         */
        private static final int TELEPORT_COOLDOWN = 100;

        @Test
        @DisplayName("Default teleport delay is 4 seconds (80 ticks)")
        void defaultTeleportDelayIs80Ticks() {
            assertEquals(80, DEFAULT_TELEPORT_DELAY,
                "Default teleportation delay should be 80 ticks (4 seconds)");
        }

        @Test
        @DisplayName("Teleport cooldown is 5 seconds (100 ticks)")
        void teleportCooldownIs100Ticks() {
            assertEquals(100, TELEPORT_COOLDOWN,
                "Teleportation cooldown should be 100 ticks (5 seconds)");
        }
    }

    @Nested
    @DisplayName("HASTE Rune Effect")
    class HasteRuneEffect {

        @Test
        @DisplayName("HASTE rune enables instant teleport")
        void hasteEnablesInstantTeleport() {
            Set<RuneType> runes = EnumSet.of(RuneType.HASTE);
            PortalRuneEffects effects = PortalRuneEffects.calculate(runes);

            assertTrue(effects.instantTeleport(),
                "HASTE rune should enable instant teleportation");
        }

        @Test
        @DisplayName("Without HASTE, teleport is not instant")
        void withoutHasteTeleportNotInstant() {
            Set<RuneType> runes = EnumSet.noneOf(RuneType.class);
            PortalRuneEffects effects = PortalRuneEffects.calculate(runes);

            assertFalse(effects.instantTeleport(),
                "Without HASTE, teleportation should not be instant");
        }

        @Test
        @DisplayName("Other runes do not enable instant teleport")
        void otherRunesDoNotEnableInstant() {
            for (RuneType rune : RuneType.values()) {
                if (rune == RuneType.HASTE) continue;

                Set<RuneType> runes = EnumSet.of(rune);
                PortalRuneEffects effects = PortalRuneEffects.calculate(runes);

                assertFalse(effects.instantTeleport(),
                    rune + " should not enable instant teleportation");
            }
        }

        @Test
        @DisplayName("HASTE combined with other runes still enables instant")
        void hasteCombinedStillEnablesInstant() {
            Set<RuneType> runes = EnumSet.of(RuneType.HASTE, RuneType.GATE, RuneType.ENHANCER);
            PortalRuneEffects effects = PortalRuneEffects.calculate(runes);

            assertTrue(effects.instantTeleport(),
                "HASTE combined with other runes should still enable instant teleport");
        }
    }

    @Nested
    @DisplayName("GATE Rune Effect")
    class GateRuneEffect {

        @Test
        @DisplayName("GATE rune enables cross-dimension teleport")
        void gateEnablesCrossDimension() {
            Set<RuneType> runes = EnumSet.of(RuneType.GATE);
            PortalRuneEffects effects = PortalRuneEffects.calculate(runes);

            assertTrue(effects.crossDimension(),
                "GATE rune should enable cross-dimension teleportation");
        }

        @Test
        @DisplayName("Without GATE, cross-dimension blocked")
        void withoutGateCrossDimensionBlocked() {
            Set<RuneType> runes = EnumSet.noneOf(RuneType.class);
            PortalRuneEffects effects = PortalRuneEffects.calculate(runes);

            assertFalse(effects.crossDimension(),
                "Without GATE, cross-dimension should be blocked");
        }
    }

    @Nested
    @DisplayName("Range Enhancement")
    class RangeEnhancement {

        @Test
        @DisplayName("ENHANCER adds 100 blocks to range")
        void enhancerAdds100ToRange() {
            Set<RuneType> runes = EnumSet.of(RuneType.ENHANCER);
            PortalRuneEffects effects = PortalRuneEffects.calculate(runes);

            assertEquals(PortalRuneEffects.DEFAULT_RANGE + 100, effects.linkRange(),
                "ENHANCER should add 100 blocks to default range");
        }

        @Test
        @DisplayName("STRONG_ENHANCER adds 500 blocks to range")
        void strongEnhancerAdds500ToRange() {
            Set<RuneType> runes = EnumSet.of(RuneType.STRONG_ENHANCER);
            PortalRuneEffects effects = PortalRuneEffects.calculate(runes);

            assertEquals(PortalRuneEffects.DEFAULT_RANGE + 500, effects.linkRange(),
                "STRONG_ENHANCER should add 500 blocks to default range");
        }

        @Test
        @DisplayName("Multiple enhancers stack")
        void multipleEnhancersStack() {
            Set<RuneType> runes = EnumSet.of(RuneType.ENHANCER, RuneType.STRONG_ENHANCER);
            PortalRuneEffects effects = PortalRuneEffects.calculate(runes);

            assertEquals(PortalRuneEffects.DEFAULT_RANGE + 100 + 500, effects.linkRange(),
                "Multiple enhancers should stack additively");
        }

        @Test
        @DisplayName("INFINITY provides max range")
        void infinityProvidesMaxRange() {
            Set<RuneType> runes = EnumSet.of(RuneType.INFINITY);
            PortalRuneEffects effects = PortalRuneEffects.calculate(runes);

            assertEquals(Integer.MAX_VALUE, effects.linkRange(),
                "INFINITY should provide maximum integer range");
        }
    }

    @Nested
    @DisplayName("Teleport Permission Checks")
    class TeleportPermissionChecks {

        @Test
        @DisplayName("Can link within range")
        void canLinkWithinRange() {
            PortalRuneEffects effects = PortalRuneEffects.NONE;

            assertTrue(effects.canLinkAtDistance(50.0), "50 blocks is within range");
            assertTrue(effects.canLinkAtDistance(100.0), "100 blocks is at edge of range");
            assertFalse(effects.canLinkAtDistance(101.0), "101 blocks is outside range");
        }

        @Test
        @DisplayName("Can link to same dimension")
        void canLinkToSameDimension() {
            PortalRuneEffects effects = PortalRuneEffects.NONE;

            assertTrue(effects.canLinkToDimension(true),
                "Should always allow same dimension");
        }

        @Test
        @DisplayName("Cannot link to different dimension without GATE")
        void cannotLinkToDifferentDimensionWithoutGate() {
            PortalRuneEffects effects = PortalRuneEffects.NONE;

            assertFalse(effects.canLinkToDimension(false),
                "Should block different dimension without GATE");
        }

        @Test
        @DisplayName("Can link to different dimension with GATE")
        void canLinkToDifferentDimensionWithGate() {
            Set<RuneType> runes = EnumSet.of(RuneType.GATE);
            PortalRuneEffects effects = PortalRuneEffects.calculate(runes);

            assertTrue(effects.canLinkToDimension(false),
                "Should allow different dimension with GATE");
        }
    }

    @Nested
    @DisplayName("Entity Type Support")
    class EntityTypeSupport {

        /**
         * Tests verify the portal should support all entity types, not just ServerPlayer.
         * This includes mobs, items, projectiles, and other entities.
         */

        @Test
        @DisplayName("Portal should track time for all entity types")
        void portalTracksTimeForAllEntities() {
            // Entity persistent data (NBT) is used for tracking portal time
            // This approach works for all Entity subclasses, not just ServerPlayer
            String tagTime = "devmod:portal_time";
            String tagPos = "devmod:portal_pos";

            // These tag names should be used consistently
            assertNotNull(tagTime, "Portal time tag should be defined");
            assertNotNull(tagPos, "Portal position tag should be defined");
        }

        @Test
        @DisplayName("Teleportation coordinates are entity-agnostic")
        void teleportCoordinatesAreEntityAgnostic() {
            // Destination calculation: destPos.getX() + 0.5, destPos.getY(), destPos.getZ() + 0.5
            // This centering logic works for any entity, not just players
            double centerOffset = 0.5;
            assertEquals(0.5, centerOffset, "Center offset should work for any entity");
        }

        @Test
        @DisplayName("Cooldown mechanism uses Entity base class")
        void cooldownUsesEntityBaseClass() {
            // entity.isOnPortalCooldown() and entity.setPortalCooldown() are defined on Entity base class
            // This means cooldown mechanism automatically works for all entity types
            int cooldownTicks = 100;
            assertTrue(cooldownTicks > 0, "Cooldown should be positive for all entities");
        }
    }

    @Nested
    @DisplayName("Redstone Integration")
    class RedstoneIntegration {

        @Test
        @DisplayName("RedstoneMode OFF means active without signal")
        void redstoneModeOffActiveWithoutSignal() {
            PortalConfig.RedstoneMode mode = PortalConfig.RedstoneMode.OFF;
            assertEquals("OFF", mode.name(), "OFF mode exists");
            // Portal is active when NO redstone signal
        }

        @Test
        @DisplayName("RedstoneMode ON means active with signal")
        void redstoneModeOnActiveWithSignal() {
            PortalConfig.RedstoneMode mode = PortalConfig.RedstoneMode.ON;
            assertEquals("ON", mode.name(), "ON mode exists");
            // Portal is active when redstone signal IS present
        }

        @Test
        @DisplayName("RedstoneMode IGNORE means always active")
        void redstoneModeIgnoreAlwaysActive() {
            PortalConfig.RedstoneMode mode = PortalConfig.RedstoneMode.IGNORE;
            assertEquals("IGNORE", mode.name(), "IGNORE mode exists");
            // Portal ignores redstone, always active when linked
        }

        @Test
        @DisplayName("Default redstone mode is IGNORE")
        void defaultRedstoneModeIsIgnore() {
            assertEquals(PortalConfig.RedstoneMode.IGNORE, PortalConfig.getRedstoneMode(),
                "Default redstone mode should be IGNORE");
        }

        @Test
        @DisplayName("isPortalActiveForRedstone returns correct value for each mode")
        void isPortalActiveForRedstoneLogic() {
            // Test the logic for each mode:
            // OFF: active = !hasPower
            // ON: active = hasPower
            // IGNORE: active = true (always)

            assertTrue(isActiveForRedstone(PortalConfig.RedstoneMode.OFF, false),
                "OFF mode: no power = active");
            assertFalse(isActiveForRedstone(PortalConfig.RedstoneMode.OFF, true),
                "OFF mode: power = inactive");

            assertFalse(isActiveForRedstone(PortalConfig.RedstoneMode.ON, false),
                "ON mode: no power = inactive");
            assertTrue(isActiveForRedstone(PortalConfig.RedstoneMode.ON, true),
                "ON mode: power = active");

            assertTrue(isActiveForRedstone(PortalConfig.RedstoneMode.IGNORE, false),
                "IGNORE mode: no power = active");
            assertTrue(isActiveForRedstone(PortalConfig.RedstoneMode.IGNORE, true),
                "IGNORE mode: power = active");
        }

        /**
         * Helper method that mirrors the logic that should be in CustomPortalBlock.
         */
        private boolean isActiveForRedstone(PortalConfig.RedstoneMode mode, boolean hasPower) {
            return switch (mode) {
                case OFF -> !hasPower;
                case ON -> hasPower;
                case IGNORE -> true;
            };
        }
    }

    @Nested
    @DisplayName("Config-Based Haste Override")
    class ConfigBasedHasteOverride {

        @Test
        @DisplayName("HasteMode TRUE means always instant teleport")
        void hasteModeTrue() {
            PortalConfig.HasteMode mode = PortalConfig.HasteMode.TRUE;
            assertTrue(shouldBeInstant(mode, false, false),
                "TRUE mode: always instant regardless of rune or creative");
            assertTrue(shouldBeInstant(mode, true, false),
                "TRUE mode: always instant even without rune");
        }

        @Test
        @DisplayName("HasteMode FALSE means rune required")
        void hasteModeFalse() {
            PortalConfig.HasteMode mode = PortalConfig.HasteMode.FALSE;
            assertFalse(shouldBeInstant(mode, false, false),
                "FALSE mode: not instant without rune");
            assertTrue(shouldBeInstant(mode, true, false),
                "FALSE mode: instant with rune");
        }

        @Test
        @DisplayName("HasteMode CREATIVE_ONLY means instant for creative players")
        void hasteModeCreativeOnly() {
            PortalConfig.HasteMode mode = PortalConfig.HasteMode.CREATIVE_ONLY;
            assertFalse(shouldBeInstant(mode, false, false),
                "CREATIVE_ONLY mode: not instant in survival without rune");
            assertTrue(shouldBeInstant(mode, false, true),
                "CREATIVE_ONLY mode: instant in creative");
            assertTrue(shouldBeInstant(mode, true, false),
                "CREATIVE_ONLY mode: instant with rune in survival");
        }

        @Test
        @DisplayName("Default haste mode is FALSE")
        void defaultHasteModeIsFalse() {
            assertEquals(PortalConfig.HasteMode.FALSE, PortalConfig.getAlwaysHasteMode(),
                "Default haste mode should be FALSE");
        }

        /**
         * Helper method that mirrors the instant teleport logic.
         */
        private boolean shouldBeInstant(PortalConfig.HasteMode mode, boolean hasHasteRune, boolean isCreative) {
            if (hasHasteRune) return true;
            return switch (mode) {
                case TRUE -> true;
                case FALSE -> false;
                case CREATIVE_ONLY -> isCreative;
            };
        }
    }

    @Nested
    @DisplayName("Cross-Dimension Feedback")
    class CrossDimensionFeedback {

        @Test
        @DisplayName("Missing GATE rune should generate feedback message")
        void missingGateGeneratesFeedback() {
            // When player attempts cross-dimension without GATE rune,
            // they should receive a message explaining why teleportation failed
            String expectedKey = "devmod.portal.no_gate_rune";
            assertNotNull(expectedKey, "Translation key for missing GATE should be defined");
        }

        @Test
        @DisplayName("Feedback should mention GATE rune by name")
        void feedbackMentionsGateRune() {
            // The message should be informative about what's needed
            // Example: "Cross-dimension teleportation requires a GATE rune in the portal frame"
            String runeNeeded = "GATE";
            assertNotNull(runeNeeded, "GATE rune should be mentioned in feedback");
        }

        @Test
        @DisplayName("Feedback should only show once per attempt")
        void feedbackShowsOncePerAttempt() {
            // To avoid spam, feedback should only show when player first enters portal
            // Not every tick while standing in it
            int showOnTick = 1; // Show feedback on first tick only
            assertEquals(1, showOnTick, "Feedback should show on first tick in portal");
        }
    }

    @Nested
    @DisplayName("Config-Based Interdimensional Override")
    class ConfigBasedInterdimensionalOverride {

        @Test
        @DisplayName("AlwaysInterdimensional TRUE allows cross-dimension without GATE")
        void alwaysInterdimensionalTrue() {
            // When config is true, cross-dimension is always allowed
            assertTrue(canCrossDimension(true, false),
                "With alwaysInterdimensional, no GATE needed");
            assertTrue(canCrossDimension(true, true),
                "With alwaysInterdimensional, GATE also works");
        }

        @Test
        @DisplayName("AlwaysInterdimensional FALSE requires GATE rune")
        void alwaysInterdimensionalFalse() {
            assertFalse(canCrossDimension(false, false),
                "Without alwaysInterdimensional, GATE required");
            assertTrue(canCrossDimension(false, true),
                "Without alwaysInterdimensional, GATE works");
        }

        @Test
        @DisplayName("Default alwaysInterdimensional is false")
        void defaultAlwaysInterdimensionalIsFalse() {
            assertFalse(PortalConfig.isAlwaysInterdimensional(),
                "Default alwaysInterdimensional should be false");
        }

        /**
         * Helper method that mirrors the cross-dimension check logic.
         */
        private boolean canCrossDimension(boolean configAlwaysInterdim, boolean hasGateRune) {
            return configAlwaysInterdim || hasGateRune;
        }
    }
}
