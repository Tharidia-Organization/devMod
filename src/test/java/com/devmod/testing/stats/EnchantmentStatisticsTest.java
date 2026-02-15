package com.devmod.testing.stats;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;

class EnchantmentStatisticsTest {

    private EnchantmentStatistics stats;

    @BeforeEach
    void setUp() throws Exception {
        Constructor<EnchantmentStatistics> ctor = EnchantmentStatistics.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        stats = ctor.newInstance();
    }

    @Test
    @DisplayName("recordItemEnchanted increments count")
    void recordItemEnchanted() {
        stats.recordItemEnchanted();
        stats.recordItemEnchanted();
        assertEquals(2, stats.getItemsEnchanted());
    }

    @Test
    @DisplayName("recordEnchantedKill tracks kills and types")
    void recordEnchantedKill() {
        stats.recordEnchantedKill("sharpness");
        stats.recordEnchantedKill("fire_aspect");
        stats.recordEnchantedKill("sharpness");

        assertEquals(3, stats.getEnchantedWeaponKills());
        assertEquals(2, stats.getUniqueEnchantmentsUsed());
        assertEquals(2, stats.getKillsWithEnchantment("sharpness"));
        assertEquals(1, stats.getKillsWithEnchantment("fire_aspect"));
    }

    @Test
    @DisplayName("null enchantment type does not crash")
    void nullEnchantmentType() {
        assertDoesNotThrow(() -> stats.recordEnchantedKill(null));
        assertEquals(1, stats.getEnchantedWeaponKills());
        assertEquals(0, stats.getUniqueEnchantmentsUsed());
    }

    @Test
    @DisplayName("toJson/fromJson round-trip")
    void jsonRoundTrip() {
        stats.recordItemEnchanted();
        stats.recordEnchantedKill("sharpness");
        stats.recordEnchantedKill("smite");

        JsonObject json = stats.toJson();

        EnchantmentStatistics restored;
        try {
            Constructor<EnchantmentStatistics> ctor = EnchantmentStatistics.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            restored = ctor.newInstance();
        } catch (Exception e) {
            fail("Cannot create instance");
            return;
        }
        restored.fromJson(json);

        assertEquals(1, restored.getItemsEnchanted());
        assertEquals(2, restored.getEnchantedWeaponKills());
        assertEquals(2, restored.getUniqueEnchantmentsUsed());
    }

    @Test
    @DisplayName("reset clears all data")
    void reset() {
        stats.recordItemEnchanted();
        stats.recordEnchantedKill("sharpness");
        stats.reset();

        assertEquals(0, stats.getItemsEnchanted());
        assertEquals(0, stats.getEnchantedWeaponKills());
        assertEquals(0, stats.getUniqueEnchantmentsUsed());
    }
}
