package com.devmod.recipe;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("Recipe Enums")
class RecipeEnumsTest {

    // ========================================================================
    // RecipeCategory
    // ========================================================================

    @Nested
    @DisplayName("RecipeCategory")
    class RecipeCategoryTests {

        @Test
        @DisplayName("Has 6 categories")
        void hasSixCategories() {
            assertEquals(6, RecipeCategory.values().length);
        }

        @ParameterizedTest
        @EnumSource(RecipeCategory.class)
        @DisplayName("All categories have non-empty ID")
        void allHaveNonEmptyId(RecipeCategory cat) {
            assertNotNull(cat.getId());
            assertFalse(cat.getId().isEmpty());
        }

        @Test
        @DisplayName("fromString finds by ID")
        void fromStringFindsById() {
            assertEquals(RecipeCategory.EQUIPMENT, RecipeCategory.fromString("equipment"));
            assertEquals(RecipeCategory.BUILDING, RecipeCategory.fromString("building"));
            assertEquals(RecipeCategory.MISC, RecipeCategory.fromString("misc"));
            assertEquals(RecipeCategory.REDSTONE, RecipeCategory.fromString("redstone"));
            assertEquals(RecipeCategory.FOOD, RecipeCategory.fromString("food"));
            assertEquals(RecipeCategory.BLOCKS, RecipeCategory.fromString("blocks"));
        }

        @Test
        @DisplayName("fromString is case insensitive")
        void fromStringCaseInsensitive() {
            assertEquals(RecipeCategory.EQUIPMENT, RecipeCategory.fromString("EQUIPMENT"));
            assertEquals(RecipeCategory.FOOD, RecipeCategory.fromString("Food"));
        }

        @Test
        @DisplayName("fromString returns MISC for unknown")
        void fromStringReturnsMiscForUnknown() {
            assertEquals(RecipeCategory.MISC, RecipeCategory.fromString("unknown"));
        }

        @Test
        @DisplayName("fromString returns MISC for null")
        void fromStringReturnsMiscForNull() {
            assertEquals(RecipeCategory.MISC, RecipeCategory.fromString(null));
        }

        @Test
        @DisplayName("fromString returns MISC for empty")
        void fromStringReturnsMiscForEmpty() {
            assertEquals(RecipeCategory.MISC, RecipeCategory.fromString(""));
        }

        @Test
        @DisplayName("craftingCategories has 4 categories")
        void craftingCategoriesHasFour() {
            RecipeCategory[] cats = RecipeCategory.craftingCategories();
            assertEquals(4, cats.length);
        }

        @Test
        @DisplayName("cookingCategories has 3 categories")
        void cookingCategoriesHasThree() {
            RecipeCategory[] cats = RecipeCategory.cookingCategories();
            assertEquals(3, cats.length);
        }
    }

    // ========================================================================
    // CraftingType
    // ========================================================================

    @Nested
    @DisplayName("CraftingType")
    class CraftingTypeTests {

        @Test
        @DisplayName("Has SHAPED and SHAPELESS")
        void hasTwoTypes() {
            assertEquals(2, CraftingType.values().length);
        }

        @Test
        @DisplayName("SHAPED has correct minecraft type")
        void shapedMinecraftType() {
            assertEquals("minecraft:crafting_shaped", CraftingType.SHAPED.getMinecraftType());
        }

        @Test
        @DisplayName("SHAPELESS has correct minecraft type")
        void shapelessMinecraftType() {
            assertEquals("minecraft:crafting_shapeless", CraftingType.SHAPELESS.getMinecraftType());
        }

        @Test
        @DisplayName("fromMinecraftType recognizes shaped")
        void fromMinecraftTypeRecognizesShaped() {
            assertEquals(CraftingType.SHAPED, CraftingType.fromMinecraftType("minecraft:crafting_shaped"));
        }

        @Test
        @DisplayName("fromMinecraftType recognizes shapeless")
        void fromMinecraftTypeRecognizesShapeless() {
            assertEquals(CraftingType.SHAPELESS, CraftingType.fromMinecraftType("minecraft:crafting_shapeless"));
        }

        @Test
        @DisplayName("fromMinecraftType defaults to SHAPED for null")
        void fromMinecraftTypeDefaultsForNull() {
            assertEquals(CraftingType.SHAPED, CraftingType.fromMinecraftType(null));
        }

        @Test
        @DisplayName("fromMinecraftType defaults to SHAPED for unknown")
        void fromMinecraftTypeDefaultsForUnknown() {
            assertEquals(CraftingType.SHAPED, CraftingType.fromMinecraftType("unknown"));
        }

        @ParameterizedTest
        @EnumSource(CraftingType.class)
        @DisplayName("All types have non-empty ID")
        void allHaveNonEmptyId(CraftingType type) {
            assertNotNull(type.getId());
            assertFalse(type.getId().isEmpty());
        }
    }

    // ========================================================================
    // SmeltingType
    // ========================================================================

    @Nested
    @DisplayName("SmeltingType")
    class SmeltingTypeTests {

        @Test
        @DisplayName("Has 4 smelting types")
        void hasFourTypes() {
            assertEquals(4, SmeltingType.values().length);
        }

        @ParameterizedTest
        @EnumSource(SmeltingType.class)
        @DisplayName("All types have positive default cooking time")
        void allHavePositiveCookingTime(SmeltingType type) {
            assertTrue(type.getDefaultCookingTime() > 0);
        }

        @ParameterizedTest
        @EnumSource(SmeltingType.class)
        @DisplayName("All types have positive default cooking time in seconds")
        void allHavePositiveCookingTimeSeconds(SmeltingType type) {
            assertTrue(type.getDefaultCookingTimeSeconds() > 0);
        }

        @Test
        @DisplayName("SMELTING default cooking time is 200 ticks")
        void smeltingDefaultTime() {
            assertEquals(200, SmeltingType.SMELTING.getDefaultCookingTime());
            assertEquals(10.0f, SmeltingType.SMELTING.getDefaultCookingTimeSeconds(), 0.001f);
        }

        @Test
        @DisplayName("BLASTING is half of smelting time")
        void blastingIsHalfTime() {
            assertEquals(100, SmeltingType.BLASTING.getDefaultCookingTime());
        }

        @Test
        @DisplayName("CAMPFIRE has longest default time")
        void campfireLongestTime() {
            assertEquals(600, SmeltingType.CAMPFIRE.getDefaultCookingTime());
        }

        @Test
        @DisplayName("fromMinecraftType works for all types")
        void fromMinecraftTypeWorks() {
            assertEquals(SmeltingType.SMELTING, SmeltingType.fromMinecraftType("minecraft:smelting"));
            assertEquals(SmeltingType.BLASTING, SmeltingType.fromMinecraftType("minecraft:blasting"));
            assertEquals(SmeltingType.SMOKING, SmeltingType.fromMinecraftType("minecraft:smoking"));
            assertEquals(SmeltingType.CAMPFIRE, SmeltingType.fromMinecraftType("minecraft:campfire_cooking"));
        }

        @Test
        @DisplayName("fromMinecraftType defaults to SMELTING for null")
        void fromMinecraftTypeDefaultsForNull() {
            assertEquals(SmeltingType.SMELTING, SmeltingType.fromMinecraftType(null));
        }

        @Test
        @DisplayName("fromMinecraftType defaults to SMELTING for unknown")
        void fromMinecraftTypeDefaultsForUnknown() {
            assertEquals(SmeltingType.SMELTING, SmeltingType.fromMinecraftType("unknown"));
        }

        @ParameterizedTest
        @EnumSource(SmeltingType.class)
        @DisplayName("All types have non-empty minecraft type")
        void allHaveNonEmptyMinecraftType(SmeltingType type) {
            assertNotNull(type.getMinecraftType());
            assertTrue(type.getMinecraftType().startsWith("minecraft:"));
        }
    }

    // ========================================================================
    // SmithingType
    // ========================================================================

    @Nested
    @DisplayName("SmithingType")
    class SmithingTypeTests {

        @Test
        @DisplayName("Has 2 smithing types")
        void hasTwoTypes() {
            assertEquals(2, SmithingType.values().length);
        }

        @Test
        @DisplayName("TRANSFORM has correct minecraft type")
        void transformMinecraftType() {
            assertEquals("minecraft:smithing_transform", SmithingType.TRANSFORM.getMinecraftType());
        }

        @Test
        @DisplayName("TRIM has correct minecraft type")
        void trimMinecraftType() {
            assertEquals("minecraft:smithing_trim", SmithingType.TRIM.getMinecraftType());
        }

        @Test
        @DisplayName("fromMinecraftType recognizes transform")
        void fromMinecraftTypeRecognizesTransform() {
            assertEquals(SmithingType.TRANSFORM, SmithingType.fromMinecraftType("minecraft:smithing_transform"));
        }

        @Test
        @DisplayName("fromMinecraftType recognizes trim")
        void fromMinecraftTypeRecognizesTrim() {
            assertEquals(SmithingType.TRIM, SmithingType.fromMinecraftType("minecraft:smithing_trim"));
        }

        @Test
        @DisplayName("fromMinecraftType defaults to TRANSFORM for null")
        void fromMinecraftTypeDefaultsForNull() {
            assertEquals(SmithingType.TRANSFORM, SmithingType.fromMinecraftType(null));
        }

        @Test
        @DisplayName("fromMinecraftType defaults to TRANSFORM for unknown")
        void fromMinecraftTypeDefaultsForUnknown() {
            assertEquals(SmithingType.TRANSFORM, SmithingType.fromMinecraftType("unknown"));
        }

        @ParameterizedTest
        @EnumSource(SmithingType.class)
        @DisplayName("All types have non-empty ID")
        void allHaveNonEmptyId(SmithingType type) {
            assertNotNull(type.getId());
            assertFalse(type.getId().isEmpty());
        }
    }
}
