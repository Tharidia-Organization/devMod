package com.devmod.npc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NpcState and NpcEmotion enums.
 * Validates color logic, interpolation, serialized names, and state mappings.
 */
class NpcEnumTest {

    // ========================================================================
    // NpcState tests
    // ========================================================================

    @Test
    @DisplayName("NpcState has exactly 6 states")
    void npcStateHasSixValues() {
        assertEquals(6, NpcState.values().length);
    }

    @ParameterizedTest
    @EnumSource(NpcState.class)
    @DisplayName("NpcState primary/secondary colors are non-negative RGB values")
    void npcStatePrimaryAndSecondaryAreValidRgb(NpcState state) {
        int primary = state.getPrimary();
        int secondary = state.getSecondary();
        // RGB values should be in 0x000000..0xFFFFFF range (no alpha)
        assertTrue(primary >= 0 && primary <= 0xFFFFFF,
            "Primary color out of RGB range for " + state + ": 0x" + Integer.toHexString(primary));
        assertTrue(secondary >= 0 && secondary <= 0xFFFFFF,
            "Secondary color out of RGB range for " + state + ": 0x" + Integer.toHexString(secondary));
    }

    @ParameterizedTest
    @EnumSource(NpcState.class)
    @DisplayName("NpcState getPrimaryARGB adds full alpha mask")
    void npcStateGetPrimaryArgbHasFullAlpha(NpcState state) {
        int argb = state.getPrimaryARGB();
        int alpha = (argb >>> 24) & 0xFF;
        assertEquals(0xFF, alpha,
            "Expected full alpha (0xFF) for " + state + ", got 0x" + Integer.toHexString(alpha));
    }

    @ParameterizedTest
    @EnumSource(NpcState.class)
    @DisplayName("NpcState getSecondaryARGB adds full alpha mask")
    void npcStateGetSecondaryArgbHasFullAlpha(NpcState state) {
        int argb = state.getSecondaryARGB();
        int alpha = (argb >>> 24) & 0xFF;
        assertEquals(0xFF, alpha);
    }

    @Test
    @DisplayName("NpcState getPrimaryWithAlpha applies custom alpha correctly")
    void npcStateCustomAlpha() {
        NpcState state = NpcState.IDLE;
        int result = state.getPrimaryWithAlpha(0x80);
        int alpha = (result >>> 24) & 0xFF;
        assertEquals(0x80, alpha);
        // RGB portion should match primary
        assertEquals(state.getPrimary(), result & 0x00FFFFFF);
    }

    @Test
    @DisplayName("NpcState getSecondaryWithAlpha applies custom alpha correctly")
    void npcStateSecondaryCustomAlpha() {
        NpcState state = NpcState.ERROR;
        int result = state.getSecondaryWithAlpha(0x40);
        int alpha = (result >>> 24) & 0xFF;
        assertEquals(0x40, alpha);
        assertEquals(state.getSecondary(), result & 0x00FFFFFF);
    }

    @Test
    @DisplayName("NpcState interpolate at t=0 returns primary color with full alpha")
    void npcStateInterpolateAtZeroReturnsPrimary() {
        NpcState state = NpcState.TALKING;
        int result = state.interpolate(0.0f);
        // Should have full alpha and primary RGB
        assertEquals(0xFF000000 | state.getPrimary(), result);
    }

    @Test
    @DisplayName("NpcState interpolate at t=1 returns secondary color with full alpha")
    void npcStateInterpolateAtOneReturnsSecondary() {
        NpcState state = NpcState.TALKING;
        int result = state.interpolate(1.0f);
        assertEquals(0xFF000000 | state.getSecondary(), result);
    }

    @Test
    @DisplayName("NpcState interpolate clamps t below 0 to 0")
    void npcStateInterpolateClampsBelowZero() {
        NpcState state = NpcState.ACTION;
        int atZero = state.interpolate(0.0f);
        int belowZero = state.interpolate(-5.0f);
        assertEquals(atZero, belowZero);
    }

    @Test
    @DisplayName("NpcState interpolate clamps t above 1 to 1")
    void npcStateInterpolateClampsAboveOne() {
        NpcState state = NpcState.ACTION;
        int atOne = state.interpolate(1.0f);
        int aboveOne = state.interpolate(10.0f);
        assertEquals(atOne, aboveOne);
    }

    // ========================================================================
    // NpcEmotion tests
    // ========================================================================

    @Test
    @DisplayName("NpcEmotion has exactly 8 emotions")
    void npcEmotionHasEightValues() {
        assertEquals(8, NpcEmotion.values().length);
    }

    @ParameterizedTest
    @EnumSource(NpcEmotion.class)
    @DisplayName("NpcEmotion getSerializedName is non-null and non-empty")
    void npcEmotionSerializedNameNonEmpty(NpcEmotion emotion) {
        String name = emotion.getSerializedName();
        assertNotNull(name);
        assertFalse(name.isEmpty());
    }

    @ParameterizedTest
    @EnumSource(NpcEmotion.class)
    @DisplayName("NpcEmotion getVisualState is non-null")
    void npcEmotionVisualStateNonNull(NpcEmotion emotion) {
        assertNotNull(emotion.getVisualState());
    }

    @Test
    @DisplayName("NpcEmotion.NEUTRAL has no particle")
    void neutralHasNoParticle() {
        assertFalse(NpcEmotion.NEUTRAL.hasParticle());
    }

    @ParameterizedTest
    @EnumSource(value = NpcEmotion.class, names = "NEUTRAL", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Non-NEUTRAL emotions have particles")
    void nonNeutralEmotionsHaveParticles(NpcEmotion emotion) {
        assertTrue(emotion.hasParticle());
    }

    @Test
    @DisplayName("NpcEmotion.fromName returns correct emotion")
    void fromNameReturnsCorrectEmotion() {
        assertEquals(NpcEmotion.HAPPY, NpcEmotion.fromName("happy"));
        assertEquals(NpcEmotion.ANGRY, NpcEmotion.fromName("angry"));
        assertEquals(NpcEmotion.SURPRISED, NpcEmotion.fromName("surprised"));
    }

    @Test
    @DisplayName("NpcEmotion.fromName is case-insensitive")
    void fromNameIsCaseInsensitive() {
        assertEquals(NpcEmotion.HAPPY, NpcEmotion.fromName("HAPPY"));
        assertEquals(NpcEmotion.HAPPY, NpcEmotion.fromName("Happy"));
        assertEquals(NpcEmotion.SCARED, NpcEmotion.fromName("SCARED"));
    }

    @Test
    @DisplayName("NpcEmotion.fromName returns NEUTRAL for unknown names")
    void fromNameReturnsNeutralForUnknown() {
        assertEquals(NpcEmotion.NEUTRAL, NpcEmotion.fromName("nonexistent"));
        assertEquals(NpcEmotion.NEUTRAL, NpcEmotion.fromName(""));
    }

    @Test
    @DisplayName("NpcEmotion visual state mappings are correct")
    void emotionVisualStateMappings() {
        assertEquals(NpcState.IDLE, NpcEmotion.NEUTRAL.getVisualState());
        assertEquals(NpcState.ACTION, NpcEmotion.HAPPY.getVisualState());
        assertEquals(NpcState.THINKING, NpcEmotion.SAD.getVisualState());
        assertEquals(NpcState.ERROR, NpcEmotion.ANGRY.getVisualState());
        assertEquals(NpcState.TALKING, NpcEmotion.SURPRISED.getVisualState());
        assertEquals(NpcState.UNAVAILABLE, NpcEmotion.SCARED.getVisualState());
        assertEquals(NpcState.THINKING, NpcEmotion.CURIOUS.getVisualState());
        assertEquals(NpcState.ACTION, NpcEmotion.EXCITED.getVisualState());
    }

    @Test
    @DisplayName("NpcEmotion color delegation matches visual state")
    void emotionColorDelegation() {
        for (NpcEmotion emotion : NpcEmotion.values()) {
            assertEquals(emotion.getVisualState().getPrimary(), emotion.getPrimaryColor(),
                "Primary color mismatch for " + emotion);
            assertEquals(emotion.getVisualState().getSecondary(), emotion.getSecondaryColor(),
                "Secondary color mismatch for " + emotion);
        }
    }
}
