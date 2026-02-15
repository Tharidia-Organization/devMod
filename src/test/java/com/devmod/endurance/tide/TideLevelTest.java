package com.devmod.endurance.tide;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TideLevelTest {

    @Test
    @DisplayName("Five tide levels are defined")
    void fiveLevelsDefined() {
        assertEquals(5, TideLevel.values().length);
    }

    @Test
    @DisplayName("fromTide returns correct level for each range")
    void fromTideReturnsCorrectLevel() {
        assertEquals(TideLevel.CALM, TideLevel.fromTide(0));
        assertEquals(TideLevel.CALM, TideLevel.fromTide(50));
        assertEquals(TideLevel.CALM, TideLevel.fromTide(99));
        assertEquals(TideLevel.RISING, TideLevel.fromTide(100));
        assertEquals(TideLevel.RISING, TideLevel.fromTide(200));
        assertEquals(TideLevel.HIGH, TideLevel.fromTide(300));
        assertEquals(TideLevel.HIGH, TideLevel.fromTide(499));
        assertEquals(TideLevel.STORM, TideLevel.fromTide(500));
        assertEquals(TideLevel.STORM, TideLevel.fromTide(799));
        assertEquals(TideLevel.APOCALYPSE, TideLevel.fromTide(800));
        assertEquals(TideLevel.APOCALYPSE, TideLevel.fromTide(999));
    }

    @Test
    @DisplayName("fromTide returns APOCALYPSE for values >= 1000")
    void fromTideMaxReturnsApocalypse() {
        assertEquals(TideLevel.APOCALYPSE, TideLevel.fromTide(1000));
        assertEquals(TideLevel.APOCALYPSE, TideLevel.fromTide(5000));
    }

    @Test
    @DisplayName("Stat bonuses increase with tide level")
    void statBonusesIncrease() {
        float prev = -1;
        for (TideLevel level : TideLevel.values()) {
            assertTrue(level.getStatBonus() >= prev,
                level + " stat bonus should be >= previous");
            prev = level.getStatBonus();
        }
    }

    @Test
    @DisplayName("CALM has no stat bonus")
    void calmNoBonus() {
        assertEquals(0f, TideLevel.CALM.getStatBonus(), 0.001f);
    }

    @Test
    @DisplayName("APOCALYPSE has 50% stat bonus")
    void apocalypseBonus() {
        assertEquals(0.50f, TideLevel.APOCALYPSE.getStatBonus(), 0.001f);
    }

    @Test
    @DisplayName("Curse mutators only active from HIGH onward")
    void curseMutatorsFromHigh() {
        assertFalse(TideLevel.CALM.isCurseMutators());
        assertFalse(TideLevel.RISING.isCurseMutators());
        assertTrue(TideLevel.HIGH.isCurseMutators());
        assertTrue(TideLevel.STORM.isCurseMutators());
        assertTrue(TideLevel.APOCALYPSE.isCurseMutators());
    }

    @Test
    @DisplayName("Forced bosses only from STORM onward")
    void forcedBossesFromStorm() {
        assertFalse(TideLevel.CALM.isForcedBosses());
        assertFalse(TideLevel.RISING.isForcedBosses());
        assertFalse(TideLevel.HIGH.isForcedBosses());
        assertTrue(TideLevel.STORM.isForcedBosses());
        assertTrue(TideLevel.APOCALYPSE.isForcedBosses());
    }

    @Test
    @DisplayName("Boss wave intervals are correct")
    void bossWaveIntervals() {
        assertEquals(0, TideLevel.CALM.getBossWaveInterval());
        assertEquals(0, TideLevel.RISING.getBossWaveInterval());
        assertEquals(0, TideLevel.HIGH.getBossWaveInterval());
        assertEquals(3, TideLevel.STORM.getBossWaveInterval());
        assertEquals(2, TideLevel.APOCALYPSE.getBossWaveInterval());
    }

    @Test
    @DisplayName("getProgress returns 0 below min, 1 above max")
    void progressBoundaries() {
        assertEquals(0f, TideLevel.RISING.getProgress(50), 0.001f);   // below min
        assertEquals(1f, TideLevel.RISING.getProgress(300), 0.001f);  // at max
        assertEquals(1f, TideLevel.RISING.getProgress(500), 0.001f);  // above max
    }

    @Test
    @DisplayName("getProgress calculates fraction correctly")
    void progressFraction() {
        // CALM: 0-100, midpoint at 50
        assertEquals(0.5f, TideLevel.CALM.getProgress(50), 0.001f);
        // RISING: 100-300, range 200. At 200: (200-100)/200 = 0.5
        assertEquals(0.5f, TideLevel.RISING.getProgress(200), 0.001f);
    }

    @Test
    @DisplayName("isTideBossThreshold at 1000+")
    void tideBossThreshold() {
        assertFalse(TideLevel.isTideBossThreshold(999));
        assertTrue(TideLevel.isTideBossThreshold(1000));
        assertTrue(TideLevel.isTideBossThreshold(1500));
    }

    @Test
    @DisplayName("getNext returns next level or null at max")
    void getNextLevel() {
        assertEquals(TideLevel.RISING, TideLevel.CALM.getNext());
        assertEquals(TideLevel.HIGH, TideLevel.RISING.getNext());
        assertEquals(TideLevel.STORM, TideLevel.HIGH.getNext());
        assertEquals(TideLevel.APOCALYPSE, TideLevel.STORM.getNext());
        assertNull(TideLevel.APOCALYPSE.getNext());
    }

    @Test
    @DisplayName("getFormattedName contains display name and reset code")
    void formattedNameContents() {
        String formatted = TideLevel.CALM.getFormattedName();
        assertTrue(formatted.contains("Calm"));
        assertTrue(formatted.endsWith("\u00A7r"));
    }

    @Test
    @DisplayName("All levels have display names")
    void allHaveDisplayNames() {
        for (TideLevel level : TideLevel.values()) {
            assertNotNull(level.getDisplayName());
            assertFalse(level.getDisplayName().isEmpty());
        }
    }
}
