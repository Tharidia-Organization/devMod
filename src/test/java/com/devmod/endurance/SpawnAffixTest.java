package com.devmod.endurance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpawnAffixTest {

    @Test
    @DisplayName("Six affix types are defined")
    void sixAffixesDefined() {
        assertEquals(6, SpawnAffix.values().length);
    }

    @Test
    @DisplayName("BASE has neutral multipliers")
    void baseIsNeutral() {
        assertEquals(1.0f, SpawnAffix.BASE.getHpMultiplier(), 0.001f);
        assertEquals(1.0f, SpawnAffix.BASE.getDamageMultiplier(), 0.001f);
        assertEquals(1.0f, SpawnAffix.BASE.getSpeedMultiplier(), 0.001f);
        assertFalse(SpawnAffix.BASE.isElite());
    }

    @Test
    @DisplayName("RUSH has low HP, high speed, non-elite")
    void rushProperties() {
        assertEquals(0.85f, SpawnAffix.RUSH.getHpMultiplier(), 0.001f);
        assertEquals(1.25f, SpawnAffix.RUSH.getSpeedMultiplier(), 0.001f);
        assertFalse(SpawnAffix.RUSH.isElite());
    }

    @Test
    @DisplayName("BRUTE has high HP, low speed, non-elite")
    void bruteProperties() {
        assertEquals(1.5f, SpawnAffix.BRUTE.getHpMultiplier(), 0.001f);
        assertEquals(0.85f, SpawnAffix.BRUTE.getSpeedMultiplier(), 0.001f);
        assertFalse(SpawnAffix.BRUTE.isElite());
    }

    @Test
    @DisplayName("SNIPER has high damage, non-elite")
    void sniperProperties() {
        assertEquals(1.25f, SpawnAffix.SNIPER.getDamageMultiplier(), 0.001f);
        assertFalse(SpawnAffix.SNIPER.isElite());
    }

    @Test
    @DisplayName("ELITE has neutral stats but is elite")
    void eliteProperties() {
        assertEquals(1.0f, SpawnAffix.ELITE.getHpMultiplier(), 0.001f);
        assertEquals(1.0f, SpawnAffix.ELITE.getDamageMultiplier(), 0.001f);
        assertEquals(1.0f, SpawnAffix.ELITE.getSpeedMultiplier(), 0.001f);
        assertTrue(SpawnAffix.ELITE.isElite());
    }

    @Test
    @DisplayName("OBJECTIVE_ELITE has boosted stats and is elite")
    void objectiveEliteProperties() {
        assertTrue(SpawnAffix.OBJECTIVE_ELITE.getHpMultiplier() > 1.0f);
        assertTrue(SpawnAffix.OBJECTIVE_ELITE.getDamageMultiplier() > 1.0f);
        assertTrue(SpawnAffix.OBJECTIVE_ELITE.getSpeedMultiplier() > 1.0f);
        assertTrue(SpawnAffix.OBJECTIVE_ELITE.isElite());
    }

    @Test
    @DisplayName("Only ELITE and OBJECTIVE_ELITE are elite")
    void onlyTwoElites() {
        int eliteCount = 0;
        for (SpawnAffix affix : SpawnAffix.values()) {
            if (affix.isElite()) eliteCount++;
        }
        assertEquals(2, eliteCount);
    }
}
