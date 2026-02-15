package com.devmod.testing.stats;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;

class CombatEventStatisticsTest {

    private CombatEventStatistics stats;

    @BeforeEach
    void setUp() throws Exception {
        Constructor<CombatEventStatistics> ctor = CombatEventStatistics.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        stats = ctor.newInstance();
    }

    @Test
    @DisplayName("recordEvasionWitnessed increments counter")
    void recordEvasion() {
        stats.recordEvasionWitnessed();
        stats.recordEvasionWitnessed();
        assertEquals(2, stats.getEvasionsWitnessed());
    }

    @Test
    @DisplayName("recordAttackBlocked increments counter")
    void recordBlock() {
        stats.recordAttackBlocked();
        assertEquals(1, stats.getBlockedAttacks());
    }

    @Test
    @DisplayName("recordAttackParried increments counter")
    void recordParry() {
        stats.recordAttackParried();
        stats.recordAttackParried();
        assertEquals(2, stats.getParriedAttacks());
    }

    @Test
    @DisplayName("recordPerfectDodge increments counter")
    void recordDodge() {
        stats.recordPerfectDodge();
        assertEquals(1, stats.getPerfectDodges());
    }

    @Test
    @DisplayName("recordCombo tracks combos and max length")
    void recordCombo() {
        stats.recordCombo(3);
        stats.recordCombo(7);
        stats.recordCombo(5);

        assertEquals(3, stats.getCombosPerformed());
        assertEquals(7, stats.getMaxComboLength());
    }

    @Test
    @DisplayName("toJson/fromJson round-trip")
    void jsonRoundTrip() {
        stats.recordEvasionWitnessed();
        stats.recordAttackBlocked();
        stats.recordAttackParried();
        stats.recordPerfectDodge();
        stats.recordCombo(5);

        JsonObject json = stats.toJson();

        CombatEventStatistics restored;
        try {
            Constructor<CombatEventStatistics> ctor = CombatEventStatistics.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            restored = ctor.newInstance();
        } catch (Exception e) {
            fail("Cannot create instance");
            return;
        }
        restored.fromJson(json);

        assertEquals(1, restored.getEvasionsWitnessed());
        assertEquals(1, restored.getBlockedAttacks());
        assertEquals(1, restored.getParriedAttacks());
        assertEquals(1, restored.getPerfectDodges());
        assertEquals(1, restored.getCombosPerformed());
        assertEquals(5, restored.getMaxComboLength());
    }

    @Test
    @DisplayName("reset clears all counters")
    void reset() {
        stats.recordEvasionWitnessed();
        stats.recordAttackBlocked();
        stats.recordCombo(10);
        stats.reset();

        assertEquals(0, stats.getEvasionsWitnessed());
        assertEquals(0, stats.getBlockedAttacks());
        assertEquals(0, stats.getCombosPerformed());
        assertEquals(0, stats.getMaxComboLength());
    }
}
