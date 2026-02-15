package com.devmod.testing.stats;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;

class PotionStatisticsTest {

    private PotionStatistics stats;

    @BeforeEach
    void setUp() throws Exception {
        Constructor<PotionStatistics> ctor = PotionStatistics.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        stats = ctor.newInstance();
    }

    @Test
    @DisplayName("recordPotionUsed tracks counts and types")
    void recordPotionUsed() {
        stats.recordPotionUsed("speed", true, false);
        stats.recordPotionUsed("strength", false, true);
        stats.recordPotionUsed("speed", false, false);

        assertEquals(3, stats.getPotionsUsed());
        assertEquals(1, stats.getSplashPotionsThrown());
        assertEquals(1, stats.getLingeringPotionsThrown());
        assertEquals(2, stats.getEffectUsageCount("speed"));
        assertEquals(1, stats.getEffectUsageCount("strength"));
    }

    @Test
    @DisplayName("recordEffectGained increments effects gained")
    void recordEffectGained() {
        stats.recordEffectGained("fire_resistance");
        stats.recordEffectGained("speed");
        stats.recordEffectGained("speed");

        assertEquals(3, stats.getPotionEffectsGained());
        assertEquals(2, stats.getUniqueEffectsUsed());
    }

    @Test
    @DisplayName("null effectType does not crash")
    void nullEffectType() {
        assertDoesNotThrow(() -> stats.recordPotionUsed(null, false, false));
        assertDoesNotThrow(() -> stats.recordEffectGained(null));
        assertEquals(1, stats.getPotionsUsed());
    }

    @Test
    @DisplayName("toJson/fromJson round-trip")
    void jsonRoundTrip() {
        stats.recordPotionUsed("speed", true, false);
        stats.recordEffectGained("strength");

        JsonObject json = stats.toJson();

        PotionStatistics restored;
        try {
            Constructor<PotionStatistics> ctor = PotionStatistics.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            restored = ctor.newInstance();
        } catch (Exception e) {
            fail("Cannot create instance");
            return;
        }
        restored.fromJson(json);

        assertEquals(1, restored.getPotionsUsed());
        assertEquals(1, restored.getPotionEffectsGained());
        assertEquals(1, restored.getSplashPotionsThrown());
    }

    @Test
    @DisplayName("reset clears everything")
    void reset() {
        stats.recordPotionUsed("speed", true, true);
        stats.recordEffectGained("strength");
        stats.reset();

        assertEquals(0, stats.getPotionsUsed());
        assertEquals(0, stats.getPotionEffectsGained());
        assertEquals(0, stats.getUniqueEffectsUsed());
        assertEquals(0, stats.getSplashPotionsThrown());
        assertEquals(0, stats.getLingeringPotionsThrown());
    }
}
