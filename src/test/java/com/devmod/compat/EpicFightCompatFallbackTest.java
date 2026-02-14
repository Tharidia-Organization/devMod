package com.devmod.compat;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.devmod.compat.mods.epicfight.EpicFightCompat;

/**
 * Tests for EpicFightCompat graceful degradation when Epic Fight
 * is not installed. Every public API method should return a safe
 * default (false, null, -1) rather than throwing.
 */
class EpicFightCompatFallbackTest {

    // ================================================================
    // Availability checks
    // ================================================================

    @Test
    void isAvailable_withoutEpicFight_returnsFalse() {
        assertFalse(EpicFightCompat.isAvailable());
    }

    @Test
    void isApiAvailable_withoutEpicFight_returnsFalse() {
        assertFalse(EpicFightCompat.isApiAvailable());
    }

    // ================================================================
    // Entity patch API — null-safe
    // ================================================================

    @Test
    void hasEntityPatch_null_returnsFalse() {
        assertFalse(EpicFightCompat.hasEntityPatch(null));
    }

    @Test
    void getEntityPatch_null_returnsNull() {
        assertNull(EpicFightCompat.getEntityPatch(null));
    }

    @Test
    void getPlayerPatch_null_returnsNull() {
        assertNull(EpicFightCompat.getPlayerPatch(null));
    }

    // ================================================================
    // Battle mode
    // ================================================================

    @Test
    void isInBattleMode_null_returnsFalse() {
        assertFalse(EpicFightCompat.isInBattleMode(null));
    }

    // ================================================================
    // Action state
    // ================================================================

    @Test
    void isInAction_null_returnsFalse() {
        assertFalse(EpicFightCompat.isInAction(null));
    }

    // ================================================================
    // Stamina
    // ================================================================

    @Test
    void getStamina_null_returnsNegativeOne() {
        assertEquals(-1f, EpicFightCompat.getStamina(null));
    }

    @Test
    void getMaxStamina_null_returnsNegativeOne() {
        assertEquals(-1f, EpicFightCompat.getMaxStamina(null));
    }

    // ================================================================
    // Animation
    // ================================================================

    @Test
    void getCurrentAnimationName_null_returnsNull() {
        assertNull(EpicFightCompat.getCurrentAnimationName(null));
    }

    // ================================================================
    // Combat active
    // ================================================================

    @Test
    void isCombatActive_null_returnsFalse() {
        assertFalse(EpicFightCompat.isCombatActive(null));
    }

    // ================================================================
    // Guard / Parry
    // ================================================================

    @Test
    void isHoldingSkill_null_returnsFalse() {
        assertFalse(EpicFightCompat.isHoldingSkill(null));
    }

    @Test
    void getHoldingSkill_null_returnsNull() {
        assertNull(EpicFightCompat.getHoldingSkill(null));
    }

    @Test
    void getHoldingSkillName_null_returnsNull() {
        assertNull(EpicFightCompat.getHoldingSkillName(null));
    }

    @Test
    void isGuarding_null_returnsFalse() {
        assertFalse(EpicFightCompat.isGuarding(null));
    }

    @Test
    void isParrying_null_returnsFalse() {
        assertFalse(EpicFightCompat.isParrying(null));
    }

    @Test
    void getTicksSinceLastAction_null_returnsNegativeOne() {
        assertEquals(-1, EpicFightCompat.getTicksSinceLastAction(null));
    }

    @Test
    void isInParryWindow_null_returnsFalse() {
        // ticksSince is -1, which is < 0, so condition is false
        assertFalse(EpicFightCompat.isInParryWindow(null));
    }

    @Test
    void isPerfectParry_null_returnsFalse() {
        assertFalse(EpicFightCompat.isPerfectParry(null));
    }

    // ================================================================
    // Parry window constant
    // ================================================================

    @Test
    void getParryWindow_returnsPositiveValue() {
        assertTrue(EpicFightCompat.getParryWindow() > 0);
    }

    // ================================================================
    // Sound events
    // ================================================================

    @Test
    void getParrySoundEvent_withoutEpicFight_returnsNull() {
        assertNull(EpicFightCompat.getParrySoundEvent());
    }

    @Test
    void getGuardSoundEvent_withoutEpicFight_returnsNull() {
        assertNull(EpicFightCompat.getGuardSoundEvent());
    }

    // ================================================================
    // Module metadata
    // ================================================================

    @Test
    void modId_isEpicFight() {
        var compat = new EpicFightCompat();
        assertEquals("epicfight", compat.modId());
    }

    @Test
    void displayName_isEpicFight() {
        var compat = new EpicFightCompat();
        assertEquals("Epic Fight", compat.displayName());
    }

    @Test
    void priority_isLow() {
        var compat = new EpicFightCompat();
        assertTrue(compat.priority() < 100,
            "Epic Fight should have high priority (low number)");
    }

    @Test
    void featureDescription_isNonEmpty() {
        var compat = new EpicFightCompat();
        assertFalse(compat.getFeatureDescription().isEmpty());
    }
}
