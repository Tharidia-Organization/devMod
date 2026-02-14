package com.devmod.stats;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RangedWeaponStats")
class RangedWeaponStatsTest {

    private RangedWeaponStats stats;

    @BeforeEach
    void setUp() {
        stats = new RangedWeaponStats();
    }

    // ================================================================
    // Default state
    // ================================================================

    @Nested
    @DisplayName("Default State")
    class DefaultState {

        @Test
        @DisplayName("default draw speed is 1.0")
        void defaultDrawSpeed() {
            assertEquals(1.0f, stats.getDrawSpeed(), 0.001f);
        }

        @Test
        @DisplayName("default charge time is 1.0")
        void defaultChargeTime() {
            assertEquals(1.0f, stats.getChargeTime(), 0.001f);
        }

        @Test
        @DisplayName("default accuracy is 1.0")
        void defaultAccuracy() {
            assertEquals(1.0f, stats.getAccuracy(), 0.001f);
        }

        @Test
        @DisplayName("default range is 1.0")
        void defaultRange() {
            assertEquals(1.0f, stats.getRange(), 0.001f);
        }

        @Test
        @DisplayName("default projectile speed is 1.0")
        void defaultProjectileSpeed() {
            assertEquals(1.0f, stats.getProjectileSpeed(), 0.001f);
        }

        @Test
        @DisplayName("default projectile gravity is 0.05")
        void defaultProjectileGravity() {
            assertEquals(0.05f, stats.getProjectileGravity(), 0.001f);
        }

        @Test
        @DisplayName("default projectile spread is 1.0")
        void defaultProjectileSpread() {
            assertEquals(1.0f, stats.getProjectileSpread(), 0.001f);
        }

        @Test
        @DisplayName("default base damage is 0")
        void defaultBaseDamage() {
            assertEquals(0.0f, stats.getBaseDamage(), 0.001f);
        }

        @Test
        @DisplayName("default piercing is 0")
        void defaultPiercing() {
            assertEquals(0, stats.getPiercing());
        }

        @Test
        @DisplayName("default multishot count is 1")
        void defaultMultishotCount() {
            assertEquals(1, stats.getMultishotCount());
        }

        @Test
        @DisplayName("multishot is false by default")
        void defaultMultishot() {
            assertFalse(stats.isMultishot());
        }

        @Test
        @DisplayName("infinity override is false by default")
        void defaultInfinityOverride() {
            assertFalse(stats.isInfinityOverride());
        }

        @Test
        @DisplayName("default crit chance is 0")
        void defaultCritChance() {
            assertEquals(0.0f, stats.getCritChance(), 0.001f);
        }

        @Test
        @DisplayName("default crit damage is 1.5")
        void defaultCritDamage() {
            assertEquals(1.5f, stats.getCritDamage(), 0.001f);
        }

        @Test
        @DisplayName("default ammo filter is empty")
        void defaultAmmoFilter() {
            assertEquals("", stats.getAmmoFilter());
        }

        @Test
        @DisplayName("trident defaults")
        void tridentDefaults() {
            assertEquals(0.0f, stats.getRiptideDistance(), 0.001f);
            assertEquals(0.0f, stats.getLoyaltySpeed(), 0.001f);
            assertTrue(stats.isRiptideRequiresWater());
            assertFalse(stats.isChanneling());
        }
    }

    // ================================================================
    // Setters and getters
    // ================================================================

    @Nested
    @DisplayName("Setters and Getters")
    class SettersGetters {

        @Test
        @DisplayName("setAmmoFilter null becomes empty string")
        void setAmmoFilterNull() {
            stats.setAmmoFilter(null);
            assertEquals("", stats.getAmmoFilter());
        }

        @Test
        @DisplayName("set and get all combat stats")
        void setAndGetCombatStats() {
            stats.setDrawSpeed(0.8f);
            stats.setChargeTime(1.5f);
            stats.setAccuracy(0.95f);
            stats.setRange(1.2f);
            stats.setProjectileSpeed(1.5f);
            stats.setProjectileGravity(0.03f);
            stats.setProjectileSpread(0.5f);
            stats.setBaseDamage(6.0f);
            stats.setPiercing(3);
            stats.setMultishotCount(3);
            stats.setMultishot(true);
            stats.setInfinityOverride(true);
            stats.setCritChance(0.1f);
            stats.setCritDamage(2.0f);
            stats.setAmmoFilter("minecraft:arrows");

            assertEquals(0.8f, stats.getDrawSpeed(), 0.001f);
            assertEquals(1.5f, stats.getChargeTime(), 0.001f);
            assertEquals(0.95f, stats.getAccuracy(), 0.001f);
            assertEquals(1.2f, stats.getRange(), 0.001f);
            assertEquals(1.5f, stats.getProjectileSpeed(), 0.001f);
            assertEquals(0.03f, stats.getProjectileGravity(), 0.001f);
            assertEquals(0.5f, stats.getProjectileSpread(), 0.001f);
            assertEquals(6.0f, stats.getBaseDamage(), 0.001f);
            assertEquals(3, stats.getPiercing());
            assertEquals(3, stats.getMultishotCount());
            assertTrue(stats.isMultishot());
            assertTrue(stats.isInfinityOverride());
            assertEquals(0.1f, stats.getCritChance(), 0.001f);
            assertEquals(2.0f, stats.getCritDamage(), 0.001f);
            assertEquals("minecraft:arrows", stats.getAmmoFilter());
        }

        @Test
        @DisplayName("trident-specific setters work")
        void tridentSetters() {
            stats.setRiptideDistance(5.0f);
            stats.setLoyaltySpeed(2.0f);
            stats.setRiptideRequiresWater(false);
            stats.setChanneling(true);

            assertEquals(5.0f, stats.getRiptideDistance(), 0.001f);
            assertEquals(2.0f, stats.getLoyaltySpeed(), 0.001f);
            assertFalse(stats.isRiptideRequiresWater());
            assertTrue(stats.isChanneling());
        }
    }

    // ================================================================
    // Copy
    // ================================================================

    @Nested
    @DisplayName("Copy")
    class CopyTests {

        @Test
        @DisplayName("copy preserves all fields")
        void copyPreservesAllFields() {
            stats.setDrawSpeed(0.9f);
            stats.setChargeTime(1.2f);
            stats.setAccuracy(0.8f);
            stats.setRange(1.5f);
            stats.setProjectileSpeed(2.0f);
            stats.setProjectileGravity(0.04f);
            stats.setProjectileSpread(0.3f);
            stats.setBaseDamage(8.0f);
            stats.setPiercing(2);
            stats.setMultishotCount(5);
            stats.setMultishot(true);
            stats.setInfinityOverride(true);
            stats.setCritChance(0.15f);
            stats.setCritDamage(2.5f);
            stats.setAmmoFilter("test:arrow");
            stats.setRiptideDistance(3.0f);
            stats.setLoyaltySpeed(1.5f);
            stats.setRiptideRequiresWater(false);
            stats.setChanneling(true);

            RangedWeaponStats copy = stats.copy();

            assertEquals(0.9f, copy.getDrawSpeed(), 0.001f);
            assertEquals(1.2f, copy.getChargeTime(), 0.001f);
            assertEquals(0.8f, copy.getAccuracy(), 0.001f);
            assertEquals(1.5f, copy.getRange(), 0.001f);
            assertEquals(2.0f, copy.getProjectileSpeed(), 0.001f);
            assertEquals(0.04f, copy.getProjectileGravity(), 0.001f);
            assertEquals(0.3f, copy.getProjectileSpread(), 0.001f);
            assertEquals(8.0f, copy.getBaseDamage(), 0.001f);
            assertEquals(2, copy.getPiercing());
            assertEquals(5, copy.getMultishotCount());
            assertTrue(copy.isMultishot());
            assertTrue(copy.isInfinityOverride());
            assertEquals(0.15f, copy.getCritChance(), 0.001f);
            assertEquals(2.5f, copy.getCritDamage(), 0.001f);
            assertEquals("test:arrow", copy.getAmmoFilter());
            assertEquals(3.0f, copy.getRiptideDistance(), 0.001f);
            assertEquals(1.5f, copy.getLoyaltySpeed(), 0.001f);
            assertFalse(copy.isRiptideRequiresWater());
            assertTrue(copy.isChanneling());
        }

        @Test
        @DisplayName("copy is independent from original")
        void copyIsIndependent() {
            stats.setBaseDamage(10.0f);
            stats.setAmmoFilter("original");

            RangedWeaponStats copy = stats.copy();
            stats.setBaseDamage(99.0f);
            stats.setAmmoFilter("changed");

            assertEquals(10.0f, copy.getBaseDamage(), 0.001f);
            assertEquals("original", copy.getAmmoFilter());
        }
    }

    // ================================================================
    // Edge cases
    // ================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("negative piercing level")
        void negativePiercing() {
            stats.setPiercing(-1);
            assertEquals(-1, stats.getPiercing());
        }

        @Test
        @DisplayName("zero draw speed")
        void zeroDrawSpeed() {
            stats.setDrawSpeed(0.0f);
            assertEquals(0.0f, stats.getDrawSpeed(), 0.001f);
        }

        @Test
        @DisplayName("very high projectile speed")
        void highProjectileSpeed() {
            stats.setProjectileSpeed(100.0f);
            assertEquals(100.0f, stats.getProjectileSpeed(), 0.001f);
        }

        @Test
        @DisplayName("crit chance can exceed 1.0 (not clamped at this level)")
        void critChanceExceedsOne() {
            stats.setCritChance(2.0f);
            assertEquals(2.0f, stats.getCritChance(), 0.001f);
        }
    }
}
