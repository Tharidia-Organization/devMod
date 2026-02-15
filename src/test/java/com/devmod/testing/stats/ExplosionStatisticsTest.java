package com.devmod.testing.stats;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;

class ExplosionStatisticsTest {

    private ExplosionStatistics stats;

    @BeforeEach
    void setUp() throws Exception {
        Constructor<ExplosionStatistics> ctor = ExplosionStatistics.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        stats = ctor.newInstance();
    }

    @Test
    @DisplayName("recordExplosion tracks all fields")
    void recordExplosion() {
        stats.recordExplosion(true, false, 3);
        stats.recordExplosion(false, true, 2);
        stats.recordExplosion(false, false, 0);

        assertEquals(3, stats.getExplosionsCaused());
        assertEquals(1, stats.getTntDetonated());
        assertEquals(1, stats.getCreeperExplosions());
        assertEquals(5, stats.getMobsKilledByExplosion());
    }

    @Test
    @DisplayName("toJson/fromJson round-trip")
    void jsonRoundTrip() {
        stats.recordExplosion(true, false, 5);
        stats.recordExplosion(false, true, 2);

        JsonObject json = stats.toJson();

        ExplosionStatistics restored;
        try {
            Constructor<ExplosionStatistics> ctor = ExplosionStatistics.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            restored = ctor.newInstance();
        } catch (Exception e) {
            fail("Cannot create instance");
            return;
        }
        restored.fromJson(json);

        assertEquals(2, restored.getExplosionsCaused());
        assertEquals(1, restored.getTntDetonated());
        assertEquals(1, restored.getCreeperExplosions());
        assertEquals(7, restored.getMobsKilledByExplosion());
    }

    @Test
    @DisplayName("reset clears all")
    void reset() {
        stats.recordExplosion(true, true, 10);
        stats.reset();

        assertEquals(0, stats.getExplosionsCaused());
        assertEquals(0, stats.getTntDetonated());
        assertEquals(0, stats.getCreeperExplosions());
        assertEquals(0, stats.getMobsKilledByExplosion());
    }
}
