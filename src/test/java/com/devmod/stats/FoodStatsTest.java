package com.devmod.stats;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.CompoundTag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.devmod.endurance.nutrition.NutritionCategory;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FoodStats")
class FoodStatsTest {

    private FoodStats stats;

    @BeforeEach
    void setUp() {
        stats = new FoodStats();
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
        @DisplayName("default nutrition is zero")
        void defaultNutrition() {
            assertEquals(0, stats.getNutrition());
        }

        @Test
        @DisplayName("default saturation is zero")
        void defaultSaturation() {
            assertEquals(0.0f, stats.getSaturation(), 0.001f);
        }

        @Test
        @DisplayName("default consumption time is 32")
        void defaultConsumptionTime() {
            assertEquals(32, stats.getConsumptionTime());
        }

        @Test
        @DisplayName("default flags are false")
        void defaultFlags() {
            assertFalse(stats.isCanAlwaysEat());
            assertFalse(stats.isMeat());
            assertFalse(stats.isFastFood());
        }

        @Test
        @DisplayName("default effects list is empty")
        void defaultEffectsEmpty() {
            assertNotNull(stats.getEffects());
            assertTrue(stats.getEffects().isEmpty());
        }

        @Test
        @DisplayName("no nutrition profile by default")
        void noNutritionProfile() {
            assertFalse(stats.hasNutritionProfile());
            assertNull(stats.getPrimaryCategory());
        }
    }

    // ================================================================
    // isDefault changes
    // ================================================================

    @Nested
    @DisplayName("isDefault becomes false")
    class IsDefaultChanges {

        @Test void nutritionBreaksDefault() {
            stats.setNutrition(5);
            assertFalse(stats.isDefault());
        }

        @Test void saturationBreaksDefault() {
            stats.setSaturation(0.5f);
            assertFalse(stats.isDefault());
        }

        @Test void consumptionTimeBreaksDefault() {
            stats.setConsumptionTime(40);
            assertFalse(stats.isDefault());
        }

        @Test void canAlwaysEatBreaksDefault() {
            stats.setCanAlwaysEat(true);
            assertFalse(stats.isDefault());
        }

        @Test void effectsBreakDefault() {
            List<FoodStats.FoodEffect> effects = new ArrayList<>();
            effects.add(new FoodStats.FoodEffect("minecraft:regeneration", 200, 0, 1.0f));
            stats.setEffects(effects);
            assertFalse(stats.isDefault());
        }

        @Test void nutritionProfileBreaksDefault() {
            stats.setNutritionValue(NutritionCategory.GRAIN, 0.5f);
            assertFalse(stats.isDefault());
        }

        @Test void primaryCategoryBreaksDefault() {
            stats.setPrimaryCategory(NutritionCategory.PROTEIN);
            assertFalse(stats.isDefault());
        }
    }

    // ================================================================
    // getActualSaturation
    // ================================================================

    @Nested
    @DisplayName("getActualSaturation")
    class ActualSaturation {

        @ParameterizedTest
        @CsvSource({
            "0, 0.0, 0.0",
            "5, 0.6, 6.0",    // 5 * 0.6 * 2 = 6.0
            "10, 1.0, 20.0",  // 10 * 1.0 * 2 = 20.0
            "8, 0.5, 8.0",    // 8 * 0.5 * 2 = 8.0
            "20, 2.0, 80.0",  // 20 * 2.0 * 2 = 80.0
            "1, 0.1, 0.2",    // 1 * 0.1 * 2 = 0.2
        })
        @DisplayName("actual saturation = nutrition * saturation * 2")
        void saturationFormula(int nutrition, float saturation, float expected) {
            stats.setNutrition(nutrition);
            stats.setSaturation(saturation);
            assertEquals(expected, stats.getActualSaturation(), 0.01f);
        }

        @Test
        @DisplayName("zero nutrition yields zero saturation regardless of modifier")
        void zeroNutritionYieldsZero() {
            stats.setNutrition(0);
            stats.setSaturation(2.0f);
            assertEquals(0.0f, stats.getActualSaturation(), 0.001f);
        }
    }

    // ================================================================
    // FoodEffect
    // ================================================================

    @Nested
    @DisplayName("FoodEffect")
    class FoodEffectTests {

        @Test
        @DisplayName("default constructor has correct defaults")
        void defaultConstructor() {
            FoodStats.FoodEffect effect = new FoodStats.FoodEffect();
            assertEquals("", effect.effectId);
            assertEquals(200, effect.duration);
            assertEquals(0, effect.amplifier);
            assertEquals(1.0f, effect.probability, 0.001f);
        }

        @Test
        @DisplayName("parameterized constructor stores values")
        void parameterizedConstructor() {
            FoodStats.FoodEffect effect = new FoodStats.FoodEffect("minecraft:speed", 600, 2, 0.5f);
            assertEquals("minecraft:speed", effect.effectId);
            assertEquals(600, effect.duration);
            assertEquals(2, effect.amplifier);
            assertEquals(0.5f, effect.probability, 0.001f);
        }

        @Test
        @DisplayName("copy is independent")
        void copyIsIndependent() {
            FoodStats.FoodEffect effect = new FoodStats.FoodEffect("minecraft:speed", 100, 1, 0.8f);
            FoodStats.FoodEffect copy = effect.copy();
            effect.duration = 9999;
            assertEquals(100, copy.duration);
            assertEquals("minecraft:speed", copy.effectId);
        }

        @Test
        @DisplayName("equals and hashCode work correctly")
        void equalsAndHashCode() {
            FoodStats.FoodEffect a = new FoodStats.FoodEffect("minecraft:speed", 200, 0, 1.0f);
            FoodStats.FoodEffect b = new FoodStats.FoodEffect("minecraft:speed", 200, 0, 1.0f);
            FoodStats.FoodEffect c = new FoodStats.FoodEffect("minecraft:strength", 200, 0, 1.0f);

            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
            assertNotEquals(a, c);
        }

        @Test
        @DisplayName("FoodEffect NBT save/load roundtrip")
        void nbtRoundtrip() {
            FoodStats.FoodEffect original = new FoodStats.FoodEffect("minecraft:regeneration", 400, 1, 0.75f);
            CompoundTag tag = original.save();
            FoodStats.FoodEffect loaded = FoodStats.FoodEffect.load(tag);

            assertEquals(original, loaded);
        }
    }

    // ================================================================
    // Nutrition Profile (Easy-Diet integration)
    // ================================================================

    @Nested
    @DisplayName("Nutrition Profile")
    class NutritionProfileTests {

        @Test
        @DisplayName("setting value makes profile non-empty")
        void settingValueMakesNonEmpty() {
            stats.setNutritionValue(NutritionCategory.GRAIN, 0.5f);
            assertTrue(stats.hasNutritionProfile());
            assertEquals(0.5f, stats.getNutritionValue(NutritionCategory.GRAIN), 0.001f);
        }

        @Test
        @DisplayName("setting value to zero removes category")
        void settingZeroRemoves() {
            stats.setNutritionValue(NutritionCategory.GRAIN, 0.5f);
            stats.setNutritionValue(NutritionCategory.GRAIN, 0.0f);
            assertFalse(stats.hasNutritionProfile());
            assertEquals(0.0f, stats.getNutritionValue(NutritionCategory.GRAIN), 0.001f);
        }

        @Test
        @DisplayName("setting negative value removes category")
        void settingNegativeRemoves() {
            stats.setNutritionValue(NutritionCategory.PROTEIN, 0.8f);
            stats.setNutritionValue(NutritionCategory.PROTEIN, -0.5f);
            assertEquals(0.0f, stats.getNutritionValue(NutritionCategory.PROTEIN), 0.001f);
        }

        @Test
        @DisplayName("value is capped at 1.0")
        void valueCappedAtOne() {
            stats.setNutritionValue(NutritionCategory.FRUIT, 5.0f);
            assertEquals(1.0f, stats.getNutritionValue(NutritionCategory.FRUIT), 0.001f);
        }

        @Test
        @DisplayName("unset category returns zero")
        void unsetReturnsZero() {
            assertEquals(0.0f, stats.getNutritionValue(NutritionCategory.WATER), 0.001f);
        }

        @Test
        @DisplayName("getNutritionProfile returns unmodifiable view")
        void unmodifiableView() {
            stats.setNutritionValue(NutritionCategory.GRAIN, 0.5f);
            Map<NutritionCategory, Float> profile = stats.getNutritionProfile();
            assertThrows(UnsupportedOperationException.class,
                () -> profile.put(NutritionCategory.PROTEIN, 0.5f));
        }

        @Test
        @DisplayName("setNutritionProfile replaces existing profile")
        void setProfileReplaces() {
            stats.setNutritionValue(NutritionCategory.GRAIN, 0.5f);
            Map<NutritionCategory, Float> newProfile = new EnumMap<>(NutritionCategory.class);
            newProfile.put(NutritionCategory.PROTEIN, 0.8f);
            stats.setNutritionProfile(newProfile);
            assertEquals(0.0f, stats.getNutritionValue(NutritionCategory.GRAIN), 0.001f);
            assertEquals(0.8f, stats.getNutritionValue(NutritionCategory.PROTEIN), 0.001f);
        }

        @Test
        @DisplayName("setNutritionProfile with null clears profile")
        void setNullClearsProfile() {
            stats.setNutritionValue(NutritionCategory.GRAIN, 0.5f);
            stats.setNutritionProfile(null);
            assertFalse(stats.hasNutritionProfile());
        }

        @Test
        @DisplayName("setNutritionProfile filters zero and negative values")
        void filtersInvalidValues() {
            Map<NutritionCategory, Float> profile = new EnumMap<>(NutritionCategory.class);
            profile.put(NutritionCategory.GRAIN, 0.5f);
            profile.put(NutritionCategory.PROTEIN, 0.0f);
            profile.put(NutritionCategory.VEGETABLE, -0.1f);
            stats.setNutritionProfile(profile);
            assertEquals(1, stats.getNutritionProfile().size());
            assertEquals(0.5f, stats.getNutritionValue(NutritionCategory.GRAIN), 0.001f);
        }

        @Test
        @DisplayName("setNutritionProfile caps values at 1.0")
        void capsValues() {
            Map<NutritionCategory, Float> profile = new EnumMap<>(NutritionCategory.class);
            profile.put(NutritionCategory.SUGAR, 3.0f);
            stats.setNutritionProfile(profile);
            assertEquals(1.0f, stats.getNutritionValue(NutritionCategory.SUGAR), 0.001f);
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
            stats.setNutrition(8);
            stats.setSaturation(0.8f);
            stats.setConsumptionTime(40);
            stats.setCanAlwaysEat(true);
            stats.setMeat(true);
            stats.setFastFood(true);

            List<FoodStats.FoodEffect> effects = new ArrayList<>();
            effects.add(new FoodStats.FoodEffect("minecraft:regeneration", 200, 1, 1.0f));
            effects.add(new FoodStats.FoodEffect("minecraft:poison", 100, 0, 0.5f));
            stats.setEffects(effects);

            stats.setNutritionValue(NutritionCategory.GRAIN, 0.7f);
            stats.setNutritionValue(NutritionCategory.PROTEIN, 0.3f);
            stats.setPrimaryCategory(NutritionCategory.GRAIN);

            CompoundTag tag = new CompoundTag();
            stats.save(tag);
            FoodStats loaded = new FoodStats();
            loaded.load(tag);

            assertEquals(8, loaded.getNutrition());
            assertEquals(0.8f, loaded.getSaturation(), 0.001f);
            assertEquals(40, loaded.getConsumptionTime());
            assertTrue(loaded.isCanAlwaysEat());
            assertTrue(loaded.isMeat());
            assertTrue(loaded.isFastFood());

            assertEquals(2, loaded.getEffects().size());
            assertEquals("minecraft:regeneration", loaded.getEffects().get(0).effectId);
            assertEquals("minecraft:poison", loaded.getEffects().get(1).effectId);
            assertEquals(0.5f, loaded.getEffects().get(1).probability, 0.001f);

            assertTrue(loaded.hasNutritionProfile());
            assertEquals(0.7f, loaded.getNutritionValue(NutritionCategory.GRAIN), 0.001f);
            assertEquals(0.3f, loaded.getNutritionValue(NutritionCategory.PROTEIN), 0.001f);
            assertEquals(NutritionCategory.GRAIN, loaded.getPrimaryCategory());
        }

        @Test
        @DisplayName("loading from empty tag restores defaults")
        void loadFromEmptyTag() {
            FoodStats loaded = new FoodStats();
            loaded.load(new CompoundTag());
            assertEquals(0, loaded.getNutrition());
            assertEquals(0.0f, loaded.getSaturation(), 0.001f);
            assertFalse(loaded.isCanAlwaysEat());
            assertTrue(loaded.getEffects().isEmpty());
            assertNull(loaded.getPrimaryCategory());
        }

        @Test
        @DisplayName("effects list clears on reload")
        void effectsClearOnReload() {
            List<FoodStats.FoodEffect> effects = new ArrayList<>();
            effects.add(new FoodStats.FoodEffect("minecraft:speed", 100, 0, 1.0f));
            stats.setEffects(effects);

            CompoundTag tag = new CompoundTag();
            stats.save(tag);

            // Load with empty tag (no effects)
            stats.load(new CompoundTag());
            assertTrue(stats.getEffects().isEmpty());
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
            stats.setNutrition(10);
            stats.setSaturation(1.0f);
            stats.setConsumptionTime(20);
            stats.setCanAlwaysEat(true);
            stats.setMeat(true);

            List<FoodStats.FoodEffect> effects = new ArrayList<>();
            effects.add(new FoodStats.FoodEffect("minecraft:speed", 200, 1, 1.0f));
            stats.setEffects(effects);

            stats.setNutritionValue(NutritionCategory.VEGETABLE, 0.8f);
            stats.setPrimaryCategory(NutritionCategory.VEGETABLE);

            FoodStats copy = stats.copy();

            assertEquals(10, copy.getNutrition());
            assertEquals(1.0f, copy.getSaturation(), 0.001f);
            assertEquals(20, copy.getConsumptionTime());
            assertTrue(copy.isCanAlwaysEat());
            assertTrue(copy.isMeat());
            assertEquals(1, copy.getEffects().size());
            assertEquals(0.8f, copy.getNutritionValue(NutritionCategory.VEGETABLE), 0.001f);
            assertEquals(NutritionCategory.VEGETABLE, copy.getPrimaryCategory());
        }

        @Test
        @DisplayName("copy is independent from original")
        void copyIsIndependent() {
            stats.setNutrition(5);
            stats.setNutritionValue(NutritionCategory.GRAIN, 0.5f);

            FoodStats copy = stats.copy();
            stats.setNutrition(99);
            stats.setNutritionValue(NutritionCategory.GRAIN, 0.0f);

            assertEquals(5, copy.getNutrition());
            assertEquals(0.5f, copy.getNutritionValue(NutritionCategory.GRAIN), 0.001f);
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
            FoodStats a = new FoodStats();
            a.setNutrition(5);
            a.setSaturation(0.5f);

            FoodStats b = new FoodStats();
            b.setNutrition(5);
            b.setSaturation(0.5f);

            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("different values are not equal")
        void differentValuesNotEqual() {
            FoodStats a = new FoodStats();
            a.setNutrition(5);

            FoodStats b = new FoodStats();
            b.setNutrition(10);

            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("equals handles identity")
        void equalsIdentity() {
            assertEquals(stats, stats);
        }

        @Test
        @DisplayName("equals handles null")
        void equalsNull() {
            assertNotEquals(null, stats);
        }

        @Test
        @DisplayName("equals handles wrong type")
        void equalsWrongType() {
            assertNotEquals("not food stats", stats);
        }
    }

    // ================================================================
    // setEffects null safety
    // ================================================================

    @Test
    @DisplayName("setEffects with null creates empty list")
    void setEffectsNullCreatesEmpty() {
        stats.setEffects(null);
        assertNotNull(stats.getEffects());
        assertTrue(stats.getEffects().isEmpty());
    }

    // ================================================================
    // toString
    // ================================================================

    @Test
    @DisplayName("toString includes key fields")
    void toStringContainsKeyFields() {
        stats.setNutrition(8);
        stats.setSaturation(0.6f);
        String str = stats.toString();
        assertTrue(str.contains("FoodStats"));
        assertTrue(str.contains("nutrition=8"));
        assertTrue(str.contains("saturation=0.6"));
    }
}
