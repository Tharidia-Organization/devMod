package com.devmod.testing.stats;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;

class DamageStatisticsTest {

    private DamageStatistics stats;

    @BeforeEach
    void setUp() throws Exception {
        Constructor<DamageStatistics> ctor = DamageStatistics.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        stats = ctor.newInstance();
    }

    @Test
    @DisplayName("recordDamageDealt tracks totals and body parts")
    void recordDamageDealt() {
        stats.recordDamageDealt(10.5, "HEAD", null);
        stats.recordDamageDealt(5.0, "BODY", null);

        assertEquals(15.5, stats.getTotalDamageDealt(), 0.001);
        assertEquals(2, stats.getHitsDealt());
        assertEquals(10.5, stats.getHighestSingleHit(), 0.001);
        assertEquals(10.5, stats.getDamageOnBodyPart("HEAD"), 0.001);
        assertEquals(5.0, stats.getDamageOnBodyPart("BODY"), 0.001);
        assertEquals(1, stats.getHitsOnBodyPart("head"));
    }

    @Test
    @DisplayName("recordDamageTaken increments taken counters")
    void recordDamageTaken() {
        stats.recordDamageTaken(7.0);
        stats.recordDamageTaken(3.0);

        assertEquals(10.0, stats.getTotalDamageTaken(), 0.001);
        assertEquals(2, stats.getHitsTaken());
    }

    @Test
    @DisplayName("highestSingleHit tracks only the maximum")
    void highestSingleHit() {
        stats.recordDamageDealt(5.0, null, null);
        stats.recordDamageDealt(20.0, null, null);
        stats.recordDamageDealt(10.0, null, null);

        assertEquals(20.0, stats.getHighestSingleHit(), 0.001);
    }

    @Test
    @DisplayName("body part keys are case insensitive")
    void bodyPartCaseInsensitive() {
        stats.recordDamageDealt(5.0, "head", null);
        stats.recordDamageDealt(3.0, "HEAD", null);

        assertEquals(8.0, stats.getDamageOnBodyPart("Head"), 0.001);
        assertEquals(2, stats.getHitsOnBodyPart("HEAD"));
    }

    @Test
    @DisplayName("toJson/fromJson round-trip preserves data")
    void jsonRoundTrip() {
        stats.recordDamageDealt(25.0, "HEAD", null);
        stats.recordDamageDealt(15.0, "BODY", null);
        stats.recordDamageTaken(8.0);

        JsonObject json = stats.toJson();

        DamageStatistics restored;
        try {
            Constructor<DamageStatistics> ctor = DamageStatistics.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            restored = ctor.newInstance();
        } catch (Exception e) {
            fail("Could not create instance");
            return;
        }
        restored.fromJson(json);

        assertEquals(40.0, restored.getTotalDamageDealt(), 0.001);
        assertEquals(8.0, restored.getTotalDamageTaken(), 0.001);
        assertEquals(2, restored.getHitsDealt());
        assertEquals(25.0, restored.getHighestSingleHit(), 0.001);
        assertEquals(25.0, restored.getDamageOnBodyPart("HEAD"), 0.001);
    }

    @Test
    @DisplayName("reset clears all counters")
    void reset() {
        stats.recordDamageDealt(10.0, "HEAD", null);
        stats.recordDamageTaken(5.0);
        stats.reset();

        assertEquals(0.0, stats.getTotalDamageDealt(), 0.001);
        assertEquals(0.0, stats.getTotalDamageTaken(), 0.001);
        assertEquals(0, stats.getHitsDealt());
        assertEquals(0.0, stats.getHighestSingleHit(), 0.001);
    }

    @Test
    @DisplayName("missing body part returns zero")
    void missingBodyPartReturnsZero() {
        assertEquals(0.0, stats.getDamageOnBodyPart("UNKNOWN"), 0.001);
        assertEquals(0, stats.getHitsOnBodyPart("UNKNOWN"));
    }

    @Test
    @DisplayName("isDirty flag tracks changes")
    void dirtyFlag() {
        assertFalse(stats.isDirty());
        stats.recordDamageDealt(1.0, null, null);
        assertTrue(stats.isDirty());
    }
}
