package com.devmod.stats;

import net.minecraft.nbt.CompoundTag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FuelStats")
class FuelStatsTest {

    private FuelStats stats;

    @BeforeEach
    void setUp() {
        stats = new FuelStats();
    }

    // ================================================================
    // Default state
    // ================================================================

    @Nested
    @DisplayName("Default State")
    class DefaultState {

        @Test
        @DisplayName("new instance reports isDefault true")
        void newInstanceIsDefault() {
            assertTrue(stats.isDefault());
        }

        @Test
        @DisplayName("default burn time is zero")
        void defaultBurnTime() {
            assertEquals(0, stats.getBurnTime());
        }

        @Test
        @DisplayName("default override is false")
        void defaultOverride() {
            assertFalse(stats.isOverrideDefault());
        }

        @Test
        @DisplayName("default efficiency is 1.0")
        void defaultEfficiency() {
            assertEquals(1.0f, stats.getEfficiencyMultiplier(), 0.001f);
        }

        @Test
        @DisplayName("default cook times match vanilla")
        void defaultCookTimes() {
            assertEquals(200, stats.getFurnaceCookTime());
            assertEquals(100, stats.getBlastFurnaceCookTime());
            assertEquals(100, stats.getSmokerCookTime());
            assertEquals(600, stats.getCampfireCookTime());
        }

        @Test
        @DisplayName("custom cook times disabled by default")
        void customCookTimesDisabled() {
            assertFalse(stats.isCustomCookTimesEnabled());
        }
    }

    // ================================================================
    // isDefault changes
    // ================================================================

    @Nested
    @DisplayName("isDefault becomes false")
    class IsDefaultChanges {

        @Test void burnTimeBreaksDefault() {
            stats.setBurnTime(200);
            assertFalse(stats.isDefault());
        }

        @Test void overrideBreaksDefault() {
            stats.setOverrideDefault(true);
            assertFalse(stats.isDefault());
        }

        @Test void efficiencyBreaksDefault() {
            stats.setEfficiencyMultiplier(1.5f);
            assertFalse(stats.isDefault());
        }

        @Test void furnaceCookTimeBreaksDefault() {
            stats.setFurnaceCookTime(300);
            assertFalse(stats.isDefault());
        }

        @Test void blastFurnaceCookTimeBreaksDefault() {
            stats.setBlastFurnaceCookTime(50);
            assertFalse(stats.isDefault());
        }

        @Test void smokerCookTimeBreaksDefault() {
            stats.setSmokerCookTime(150);
            assertFalse(stats.isDefault());
        }

        @Test void campfireCookTimeBreaksDefault() {
            stats.setCampfireCookTime(800);
            assertFalse(stats.isDefault());
        }

        @Test void customCookTimesEnabledBreaksDefault() {
            stats.setCustomCookTimesEnabled(true);
            assertFalse(stats.isDefault());
        }
    }

    // ================================================================
    // getEffectiveBurnTime
    // ================================================================

    @Nested
    @DisplayName("getEffectiveBurnTime")
    class EffectiveBurnTime {

        @ParameterizedTest
        @CsvSource({
            "200, 1.0, 200",   // coal: 200 * 1.0
            "200, 1.5, 300",   // coal + efficiency: 200 * 1.5
            "200, 0.5, 100",   // coal * 0.5
            "1600, 1.0, 1600", // coal block
            "1600, 2.0, 3200", // coal block * 2
            "0, 1.0, 0",       // no burn time
            "0, 3.0, 0",       // no burn time * any
        })
        @DisplayName("effective burn time = burnTime * efficiency")
        void effectiveBurnTime(int burnTime, float efficiency, int expected) {
            stats.setBurnTime(burnTime);
            stats.setEfficiencyMultiplier(efficiency);
            assertEquals(expected, stats.getEffectiveBurnTime());
        }

        @Test
        @DisplayName("rounds correctly for fractional results")
        void roundsCorrectly() {
            stats.setBurnTime(100);
            stats.setEfficiencyMultiplier(1.3f);
            assertEquals(Math.round(100 * 1.3f), stats.getEffectiveBurnTime());
        }
    }

    // ================================================================
    // getItemsSmeltable
    // ================================================================

    @Nested
    @DisplayName("getItemsSmeltable")
    class ItemsSmeltable {

        @ParameterizedTest
        @CsvSource({
            "200, 1.0, 1.0",    // coal smelts 1 item
            "400, 1.0, 2.0",    // 400 ticks = 2 items
            "1600, 1.0, 8.0",   // coal block = 8 items
            "0, 1.0, 0.0",      // no burn = no items
            "200, 2.0, 2.0",    // coal + 2x efficiency = 2 items
            "100, 1.0, 0.5",    // half a smelt
        })
        @DisplayName("items smeltable = effectiveBurnTime / 200")
        void itemsSmeltable(int burnTime, float efficiency, float expected) {
            stats.setBurnTime(burnTime);
            stats.setEfficiencyMultiplier(efficiency);
            assertEquals(expected, stats.getItemsSmeltable(), 0.01f);
        }
    }

    // ================================================================
    // NBT save/load roundtrip
    // ================================================================

    @Nested
    @DisplayName("NBT Serialization")
    class NbtSerialization {

        @Test
        @DisplayName("full roundtrip preserves all fields")
        void fullRoundtrip() {
            stats.setBurnTime(1600);
            stats.setOverrideDefault(true);
            stats.setEfficiencyMultiplier(1.5f);
            stats.setFurnaceCookTime(150);
            stats.setBlastFurnaceCookTime(75);
            stats.setSmokerCookTime(75);
            stats.setCampfireCookTime(450);
            stats.setCustomCookTimesEnabled(true);

            CompoundTag tag = new CompoundTag();
            stats.save(tag);
            FuelStats loaded = FuelStats.fromTag(tag);

            assertEquals(1600, loaded.getBurnTime());
            assertTrue(loaded.isOverrideDefault());
            assertEquals(1.5f, loaded.getEfficiencyMultiplier(), 0.001f);
            assertEquals(150, loaded.getFurnaceCookTime());
            assertEquals(75, loaded.getBlastFurnaceCookTime());
            assertEquals(75, loaded.getSmokerCookTime());
            assertEquals(450, loaded.getCampfireCookTime());
            assertTrue(loaded.isCustomCookTimesEnabled());
        }

        @Test
        @DisplayName("loading from empty tag restores defaults")
        void loadFromEmptyTag() {
            FuelStats loaded = FuelStats.fromTag(new CompoundTag());
            assertEquals(0, loaded.getBurnTime());
            assertFalse(loaded.isOverrideDefault());
            assertEquals(1.0f, loaded.getEfficiencyMultiplier(), 0.001f);
            assertEquals(200, loaded.getFurnaceCookTime());
            assertEquals(100, loaded.getBlastFurnaceCookTime());
            assertEquals(100, loaded.getSmokerCookTime());
            assertEquals(600, loaded.getCampfireCookTime());
            assertFalse(loaded.isCustomCookTimesEnabled());
        }

        @Test
        @DisplayName("partial tag with missing cook times uses defaults")
        void partialTagUsesDefaults() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("burnTime", 300);
            tag.putBoolean("overrideDefault", true);
            // No cook time keys

            FuelStats loaded = FuelStats.fromTag(tag);
            assertEquals(300, loaded.getBurnTime());
            assertTrue(loaded.isOverrideDefault());
            assertEquals(1.0f, loaded.getEfficiencyMultiplier(), 0.001f);
            assertEquals(200, loaded.getFurnaceCookTime());
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
            stats.setBurnTime(800);
            stats.setOverrideDefault(true);
            stats.setEfficiencyMultiplier(2.0f);
            stats.setFurnaceCookTime(100);
            stats.setBlastFurnaceCookTime(50);
            stats.setSmokerCookTime(50);
            stats.setCampfireCookTime(300);
            stats.setCustomCookTimesEnabled(true);

            FuelStats copy = stats.copy();

            assertEquals(800, copy.getBurnTime());
            assertTrue(copy.isOverrideDefault());
            assertEquals(2.0f, copy.getEfficiencyMultiplier(), 0.001f);
            assertEquals(100, copy.getFurnaceCookTime());
            assertEquals(50, copy.getBlastFurnaceCookTime());
            assertEquals(50, copy.getSmokerCookTime());
            assertEquals(300, copy.getCampfireCookTime());
            assertTrue(copy.isCustomCookTimesEnabled());
        }

        @Test
        @DisplayName("copy is independent from original")
        void copyIsIndependent() {
            stats.setBurnTime(400);
            FuelStats copy = stats.copy();
            stats.setBurnTime(9999);
            assertEquals(400, copy.getBurnTime());
        }
    }

    // ================================================================
    // equals / hashCode
    // ================================================================

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsHashCode {

        @Test
        @DisplayName("same values are equal")
        void sameValuesEqual() {
            FuelStats a = new FuelStats();
            a.setBurnTime(200);
            a.setEfficiencyMultiplier(1.5f);

            FuelStats b = new FuelStats();
            b.setBurnTime(200);
            b.setEfficiencyMultiplier(1.5f);

            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("different values are not equal")
        void differentValuesNotEqual() {
            FuelStats a = new FuelStats();
            a.setBurnTime(200);

            FuelStats b = new FuelStats();
            b.setBurnTime(400);

            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("identity equals works")
        void identityEquals() {
            assertEquals(stats, stats);
        }

        @Test
        @DisplayName("null not equal")
        void nullNotEqual() {
            assertNotEquals(null, stats);
        }
    }

    // ================================================================
    // toString
    // ================================================================

    @Test
    @DisplayName("toString includes key fields")
    void toStringContainsKeyFields() {
        stats.setBurnTime(200);
        String str = stats.toString();
        assertTrue(str.contains("FuelStats"));
        assertTrue(str.contains("burnTime=200"));
    }
}
