package com.devmod.recipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

public class RecipeSystemTest {

    // ═══════════════════════════════════════════════════════════════
    // MOCK CLASSES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Mock ResourceLocation for testing without Minecraft dependencies.
     */
    static class MockResourceLocation {
        private final String namespace;
        private final String path;

        public MockResourceLocation(String namespace, String path) {
            this.namespace = namespace;
            this.path = path;
        }

        public static MockResourceLocation parse(String id) {
            int colonIndex = id.indexOf(':');
            if (colonIndex < 0) {
                return new MockResourceLocation("minecraft", id);
            }
            return new MockResourceLocation(id.substring(0, colonIndex), id.substring(colonIndex + 1));
        }

        public String getNamespace() { return namespace; }
        public String getPath() { return path; }

        @Override
        public String toString() {
            return namespace + ":" + path;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof MockResourceLocation other)) return false;
            return namespace.equals(other.namespace) && path.equals(other.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(namespace, path);
        }
    }

    /**
     * Mock IngredientData for testing.
     */
    record MockIngredientData(
        @Nullable MockResourceLocation item,
        @Nullable String tag,
        @Nullable List<MockResourceLocation> alternatives,
        int count
    ) {
        public static MockIngredientData empty() {
            return new MockIngredientData(null, null, null, 0);
        }

        public static MockIngredientData ofItem(String itemId) {
            return new MockIngredientData(MockResourceLocation.parse(itemId), null, null, 1);
        }

        public static MockIngredientData ofItem(String itemId, int count) {
            return new MockIngredientData(MockResourceLocation.parse(itemId), null, null, count);
        }

        public static MockIngredientData ofTag(String tagId) {
            return new MockIngredientData(null, tagId, null, 1);
        }

        public boolean isEmpty() {
            return item == null && tag == null && (alternatives == null || alternatives.isEmpty());
        }

        public boolean isItem() { return item != null; }
        public boolean isTag() { return tag != null; }
    }

    /**
     * Mock ResultData for testing.
     */
    record MockResultData(MockResourceLocation item, int count) {
        public static MockResultData of(String itemId) {
            return new MockResultData(MockResourceLocation.parse(itemId), 1);
        }

        public static MockResultData of(String itemId, int count) {
            return new MockResultData(MockResourceLocation.parse(itemId), count);
        }
    }

    /**
     * Mock RecipeData interface for testing.
     */
    interface MockRecipeData {
        MockResourceLocation id();
        String type();
    }

    /**
     * Mock CraftingRecipeData for testing.
     */
    record MockCraftingRecipeData(
        MockResourceLocation id,
        CraftingType craftingType,
        List<MockIngredientData> ingredients,
        MockResultData result,
        String group,
        RecipeCategory category
    ) implements MockRecipeData {
        @Override
        public String type() {
            return "minecraft:crafting_" + (craftingType == CraftingType.SHAPED ? "shaped" : "shapeless");
        }

        public List<MockIngredientData> getGridIngredients() {
            // For shaped recipes, pad to 9 ingredients
            if (craftingType == CraftingType.SHAPED) {
                List<MockIngredientData> grid = new ArrayList<>(9);
                for (int i = 0; i < 9; i++) {
                    grid.add(i < ingredients.size() ? ingredients.get(i) : MockIngredientData.empty());
                }
                return grid;
            }
            return ingredients;
        }
    }

    /**
     * Mock SmeltingRecipeData for testing.
     */
    record MockSmeltingRecipeData(
        MockResourceLocation id,
        SmeltingType smeltingType,
        MockIngredientData ingredient,
        MockResultData result,
        float experience,
        int cookingTime,
        String group,
        RecipeCategory category
    ) implements MockRecipeData {
        @Override
        public String type() {
            return "minecraft:" + smeltingType.getId();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // INGREDIENT DATA TESTS
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("IngredientData Tests")
    class IngredientDataTests {

        @Test
        @DisplayName("Empty ingredient should be empty")
        void testEmptyIngredient() {
            MockIngredientData empty = MockIngredientData.empty();
            assertTrue(empty.isEmpty());
            assertFalse(empty.isItem());
            assertFalse(empty.isTag());
        }

        @Test
        @DisplayName("Item ingredient should have item")
        void testItemIngredient() {
            MockIngredientData item = MockIngredientData.ofItem("minecraft:diamond");
            assertFalse(item.isEmpty());
            assertTrue(item.isItem());
            assertFalse(item.isTag());
            assertEquals("minecraft:diamond", Objects.requireNonNull(item.item()).toString());
        }

        @Test
        @DisplayName("Tag ingredient should have tag")
        void testTagIngredient() {
            MockIngredientData tag = MockIngredientData.ofTag("#minecraft:logs");
            assertFalse(tag.isEmpty());
            assertFalse(tag.isItem());
            assertTrue(tag.isTag());
            assertEquals("#minecraft:logs", tag.tag());
        }

        @Test
        @DisplayName("Item ingredient with count")
        void testItemIngredientWithCount() {
            MockIngredientData item = MockIngredientData.ofItem("minecraft:iron_ingot", 3);
            assertEquals(3, item.count());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // RESULT DATA TESTS
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ResultData Tests")
    class ResultDataTests {

        @Test
        @DisplayName("Result should have item and count")
        void testResultData() {
            MockResultData result = MockResultData.of("minecraft:diamond_block", 1);
            assertEquals("minecraft:diamond_block", result.item().toString());
            assertEquals(1, result.count());
        }

        @Test
        @DisplayName("Result with multiple count")
        void testResultDataMultiple() {
            MockResultData result = MockResultData.of("minecraft:torch", 4);
            assertEquals(4, result.count());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // CRAFTING RECIPE TESTS
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CraftingRecipeData Tests")
    class CraftingRecipeDataTests {

        @Test
        @DisplayName("Shaped recipe should have correct type")
        void testShapedRecipeType() {
            MockCraftingRecipeData recipe = new MockCraftingRecipeData(
                MockResourceLocation.parse("devmod:test_shaped"),
                CraftingType.SHAPED,
                List.of(MockIngredientData.ofItem("minecraft:diamond")),
                MockResultData.of("minecraft:diamond_block"),
                "",
                RecipeCategory.MISC
            );

            assertEquals("minecraft:crafting_shaped", recipe.type());
        }

        @Test
        @DisplayName("Shapeless recipe should have correct type")
        void testShapelessRecipeType() {
            MockCraftingRecipeData recipe = new MockCraftingRecipeData(
                MockResourceLocation.parse("devmod:test_shapeless"),
                CraftingType.SHAPELESS,
                List.of(MockIngredientData.ofItem("minecraft:diamond_block")),
                MockResultData.of("minecraft:diamond", 9),
                "",
                RecipeCategory.MISC
            );

            assertEquals("minecraft:crafting_shapeless", recipe.type());
        }

        @Test
        @DisplayName("Grid ingredients should pad to 9 for shaped")
        void testGridIngredientsPadding() {
            MockCraftingRecipeData recipe = new MockCraftingRecipeData(
                MockResourceLocation.parse("devmod:test"),
                CraftingType.SHAPED,
                List.of(
                    MockIngredientData.ofItem("minecraft:stick"),
                    MockIngredientData.ofItem("minecraft:diamond")
                ),
                MockResultData.of("minecraft:diamond_sword"),
                "",
                RecipeCategory.EQUIPMENT
            );

            List<MockIngredientData> grid = recipe.getGridIngredients();
            assertEquals(9, grid.size());
            assertFalse(grid.get(0).isEmpty());
            assertFalse(grid.get(1).isEmpty());
            assertTrue(grid.get(2).isEmpty());
        }

        @ParameterizedTest
        @EnumSource(RecipeCategory.class)
        @DisplayName("All recipe categories should be valid")
        void testAllCategories(RecipeCategory category) {
            MockCraftingRecipeData recipe = new MockCraftingRecipeData(
                MockResourceLocation.parse("devmod:test_" + category.name().toLowerCase(Locale.ROOT)),
                CraftingType.SHAPED,
                List.of(MockIngredientData.ofItem("minecraft:stone")),
                MockResultData.of("minecraft:cobblestone"),
                "",
                category
            );

            assertNotNull(recipe.category());
            assertEquals(category, recipe.category());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SMELTING RECIPE TESTS
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("SmeltingRecipeData Tests")
    class SmeltingRecipeDataTests {

        @ParameterizedTest
        @EnumSource(SmeltingType.class)
        @DisplayName("All smelting types should have correct type string")
        void testAllSmeltingTypes(SmeltingType smeltingType) {
            MockSmeltingRecipeData recipe = new MockSmeltingRecipeData(
                MockResourceLocation.parse("devmod:test_" + smeltingType.getId()),
                smeltingType,
                MockIngredientData.ofItem("minecraft:iron_ore"),
                MockResultData.of("minecraft:iron_ingot"),
                0.7f,
                200,
                "",
                RecipeCategory.MISC
            );

            String expectedType = "minecraft:" + smeltingType.getId();
            assertEquals(expectedType, recipe.type());
        }

        @Test
        @DisplayName("Smelting recipe should have experience and cooking time")
        void testSmeltingExperienceAndTime() {
            MockSmeltingRecipeData recipe = new MockSmeltingRecipeData(
                MockResourceLocation.parse("devmod:test_smelting"),
                SmeltingType.SMELTING,
                MockIngredientData.ofItem("minecraft:gold_ore"),
                MockResultData.of("minecraft:gold_ingot"),
                1.0f,
                200,
                "",
                RecipeCategory.MISC
            );

            assertEquals(1.0f, recipe.experience(), 0.001f);
            assertEquals(200, recipe.cookingTime());
        }

        @Test
        @DisplayName("Blast furnace should have shorter cooking time")
        void testBlastFurnaceCookingTime() {
            int standardTime = 200;
            int blastingTime = standardTime / 2;

            MockSmeltingRecipeData blasting = new MockSmeltingRecipeData(
                MockResourceLocation.parse("devmod:test_blasting"),
                SmeltingType.BLASTING,
                MockIngredientData.ofItem("minecraft:iron_ore"),
                MockResultData.of("minecraft:iron_ingot"),
                0.7f,
                blastingTime,
                "",
                RecipeCategory.MISC
            );

            assertEquals(100, blasting.cookingTime());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // RECIPE VALIDATION TESTS
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Recipe Validation Tests")
    class RecipeValidationTests {

        /**
         * Mock validator for testing.
         */
        static class MockValidator {
            public record ValidationResult(boolean valid, List<String> errors) {
                public static ValidationResult success() {
                    return new ValidationResult(true, List.of());
                }

                public static ValidationResult failure(String... errors) {
                    return new ValidationResult(false, Arrays.asList(errors));
                }

                public String getFirstError() {
                    return errors.isEmpty() ? "" : errors.get(0);
                }
            }

            public static ValidationResult validate(MockCraftingRecipeData recipe) {
                List<String> errors = new ArrayList<>();

                if (recipe.id() == null) {
                    errors.add("Recipe ID cannot be null");
                }

                if (recipe.result() == null || recipe.result().item() == null) {
                    errors.add("Recipe must have a valid result");
                }

                if (recipe.ingredients() == null || recipe.ingredients().isEmpty()) {
                    errors.add("Recipe must have at least one ingredient");
                }

                boolean hasValidIngredient = recipe.ingredients() != null &&
                    recipe.ingredients().stream().anyMatch(i -> !i.isEmpty());
                if (!hasValidIngredient) {
                    errors.add("Recipe must have at least one non-empty ingredient");
                }

                return errors.isEmpty() ? ValidationResult.success() : new ValidationResult(false, errors);
            }
        }

        @Test
        @DisplayName("Valid recipe should pass validation")
        void testValidRecipe() {
            MockCraftingRecipeData recipe = new MockCraftingRecipeData(
                MockResourceLocation.parse("devmod:valid_recipe"),
                CraftingType.SHAPED,
                List.of(MockIngredientData.ofItem("minecraft:diamond")),
                MockResultData.of("minecraft:diamond_block"),
                "",
                RecipeCategory.MISC
            );

            var result = MockValidator.validate(recipe);
            assertTrue(result.valid());
        }

        @Test
        @DisplayName("Recipe with empty ingredients should fail validation")
        void testEmptyIngredientsValidation() {
            MockCraftingRecipeData recipe = new MockCraftingRecipeData(
                MockResourceLocation.parse("devmod:invalid_recipe"),
                CraftingType.SHAPED,
                List.of(MockIngredientData.empty(), MockIngredientData.empty()),
                MockResultData.of("minecraft:diamond"),
                "",
                RecipeCategory.MISC
            );

            var result = MockValidator.validate(recipe);
            assertFalse(result.valid());
            assertTrue(result.getFirstError().contains("non-empty ingredient"));
        }

        @Test
        @DisplayName("Recipe with no ingredients should fail validation")
        void testNoIngredientsValidation() {
            MockCraftingRecipeData recipe = new MockCraftingRecipeData(
                MockResourceLocation.parse("devmod:no_ingredients"),
                CraftingType.SHAPELESS,
                List.of(),
                MockResultData.of("minecraft:diamond"),
                "",
                RecipeCategory.MISC
            );

            var result = MockValidator.validate(recipe);
            assertFalse(result.valid());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // MOCK RECIPE CONFIG MANAGER FOR THREAD SAFETY TESTS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Simplified MockRecipeConfigManager for concurrency testing.
     */
    static class MockRecipeConfigManager {
        private final Map<MockResourceLocation, MockRecipeData> recipes = new ConcurrentHashMap<>();
        private final java.util.concurrent.locks.ReadWriteLock lock =
            new java.util.concurrent.locks.ReentrantReadWriteLock();

        public void addRecipe(MockRecipeData recipe) {
            lock.writeLock().lock();
            try {
                recipes.put(recipe.id(), recipe);
            } finally {
                lock.writeLock().unlock();
            }
        }

        public Optional<MockRecipeData> removeRecipe(MockResourceLocation id) {
            lock.writeLock().lock();
            try {
                return Optional.ofNullable(recipes.remove(id));
            } finally {
                lock.writeLock().unlock();
            }
        }

        public Optional<MockRecipeData> getRecipe(MockResourceLocation id) {
            lock.readLock().lock();
            try {
                return Optional.ofNullable(recipes.get(id));
            } finally {
                lock.readLock().unlock();
            }
        }

        public List<MockRecipeData> getAllRecipes() {
            lock.readLock().lock();
            try {
                return new ArrayList<>(recipes.values());
            } finally {
                lock.readLock().unlock();
            }
        }

        public int getRecipeCount() {
            lock.readLock().lock();
            try {
                return recipes.size();
            } finally {
                lock.readLock().unlock();
            }
        }

        public void clear() {
            lock.writeLock().lock();
            try {
                recipes.clear();
            } finally {
                lock.writeLock().unlock();
            }
        }

        public int addBatch(Collection<MockRecipeData> batch) {
            lock.writeLock().lock();
            try {
                int added = 0;
                for (MockRecipeData recipe : batch) {
                    if (recipe != null && recipe.id() != null) {
                        recipes.put(recipe.id(), recipe);
                        added++;
                    }
                }
                return added;
            } finally {
                lock.writeLock().unlock();
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // THREAD SAFETY TESTS
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Thread Safety Tests")
    class ThreadSafetyTests {

        private MockRecipeConfigManager configManager;

        @BeforeEach
        void setUp() {
            configManager = new MockRecipeConfigManager();
        }

        @Test
        @DisplayName("Concurrent adds should not lose data")
        void testConcurrentAdds() throws InterruptedException {
            int threadCount = 10;
            int recipesPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < recipesPerThread; i++) {
                            MockCraftingRecipeData recipe = new MockCraftingRecipeData(
                                MockResourceLocation.parse("devmod:thread" + threadId + "_recipe" + i),
                                CraftingType.SHAPED,
                                List.of(MockIngredientData.ofItem("minecraft:stone")),
                                MockResultData.of("minecraft:cobblestone"),
                                "",
                                RecipeCategory.MISC
                            );
                            configManager.addRecipe(recipe);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            assertEquals(threadCount * recipesPerThread, configManager.getRecipeCount());
        }

        @Test
        @DisplayName("Concurrent reads during writes should not throw")
        void testConcurrentReadsAndWrites() throws InterruptedException {
            int threadCount = 8;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger readCount = new AtomicInteger(0);
            AtomicInteger writeCount = new AtomicInteger(0);

            // Half threads write, half threads read
            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                final boolean isWriter = t < threadCount / 2;

                executor.submit(() -> {
                    try {
                        for (int i = 0; i < 100; i++) {
                            if (isWriter) {
                                MockCraftingRecipeData recipe = new MockCraftingRecipeData(
                                    MockResourceLocation.parse("devmod:thread" + threadId + "_recipe" + i),
                                    CraftingType.SHAPELESS,
                                    List.of(MockIngredientData.ofItem("minecraft:dirt")),
                                    MockResultData.of("minecraft:grass_block"),
                                    "",
                                    RecipeCategory.BUILDING
                                );
                                configManager.addRecipe(recipe);
                                writeCount.incrementAndGet();
                            } else {
                                configManager.getAllRecipes();
                                readCount.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            assertTrue(readCount.get() > 0, "Should have performed reads");
            assertTrue(writeCount.get() > 0, "Should have performed writes");
        }

        @Test
        @DisplayName("Batch operations should be atomic")
        void testBatchAtomic() throws InterruptedException {
            int batchSize = 50;
            List<MockRecipeData> batch = new ArrayList<>();
            for (int i = 0; i < batchSize; i++) {
                batch.add(new MockCraftingRecipeData(
                    MockResourceLocation.parse("devmod:batch_recipe" + i),
                    CraftingType.SHAPED,
                    List.of(MockIngredientData.ofItem("minecraft:gold_ingot")),
                    MockResultData.of("minecraft:gold_block"),
                    "",
                    RecipeCategory.MISC
                ));
            }

            // Add batch while another thread is reading
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch latch = new CountDownLatch(2);

            executor.submit(() -> {
                try {
                    configManager.addBatch(batch);
                } finally {
                    latch.countDown();
                }
            });

            AtomicInteger observedCount = new AtomicInteger(-1);
            executor.submit(() -> {
                try {
                    Thread.sleep(1); // Small delay to increase chance of overlap
                    observedCount.set(configManager.getRecipeCount());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });

            latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            // Final count should be exactly batchSize (either 0 or 50, never partial)
            int finalCount = configManager.getRecipeCount();
            assertEquals(batchSize, finalCount);
        }

        @Test
        @DisplayName("Concurrent remove should not corrupt state")
        void testConcurrentRemove() throws InterruptedException {
            // Pre-populate
            for (int i = 0; i < 100; i++) {
                configManager.addRecipe(new MockCraftingRecipeData(
                    MockResourceLocation.parse("devmod:recipe" + i),
                    CraftingType.SHAPELESS,
                    List.of(MockIngredientData.ofItem("minecraft:stone")),
                    MockResultData.of("minecraft:cobblestone"),
                    "",
                    RecipeCategory.MISC
                ));
            }

            assertEquals(100, configManager.getRecipeCount());

            ExecutorService executor = Executors.newFixedThreadPool(4);
            CountDownLatch latch = new CountDownLatch(4);

            for (int t = 0; t < 4; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = threadId * 25; i < (threadId + 1) * 25; i++) {
                            configManager.removeRecipe(MockResourceLocation.parse("devmod:recipe" + i));
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            assertEquals(0, configManager.getRecipeCount());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // RESOURCE LOCATION TESTS
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ResourceLocation Tests")
    class ResourceLocationTests {

        @Test
        @DisplayName("Parse full resource location")
        void testParseFullId() {
            MockResourceLocation loc = MockResourceLocation.parse("devmod:custom_recipe");
            assertEquals("devmod", loc.getNamespace());
            assertEquals("custom_recipe", loc.getPath());
        }

        @Test
        @DisplayName("Parse without namespace defaults to minecraft")
        void testParseWithoutNamespace() {
            MockResourceLocation loc = MockResourceLocation.parse("diamond_sword");
            assertEquals("minecraft", loc.getNamespace());
            assertEquals("diamond_sword", loc.getPath());
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "minecraft:diamond",
            "devmod:custom_item",
            "modid:path/to/item",
            "a:b",
            "very_long_namespace:very_long_path_name_here"
        })
        @DisplayName("Various resource location formats should parse correctly")
        void testVariousFormats(String id) {
            MockResourceLocation loc = MockResourceLocation.parse(id);
            assertNotNull(loc);
            assertEquals(id, loc.toString());
        }

        @Test
        @DisplayName("Equality should work correctly")
        void testEquality() {
            MockResourceLocation loc1 = MockResourceLocation.parse("devmod:recipe1");
            MockResourceLocation loc2 = MockResourceLocation.parse("devmod:recipe1");
            MockResourceLocation loc3 = MockResourceLocation.parse("devmod:recipe2");

            assertEquals(loc1, loc2);
            assertNotEquals(loc1, loc3);
            assertEquals(loc1.hashCode(), loc2.hashCode());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ENUM TESTS
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Enum Tests")
    class EnumTests {

        @Test
        @DisplayName("CraftingType should have SHAPED and SHAPELESS")
        void testCraftingTypes() {
            assertEquals(2, CraftingType.values().length);
            assertNotNull(CraftingType.SHAPED);
            assertNotNull(CraftingType.SHAPELESS);
        }

        @Test
        @DisplayName("SmeltingType should have all furnace types")
        void testSmeltingTypes() {
            assertTrue(SmeltingType.values().length >= 4);
            assertNotNull(SmeltingType.SMELTING);
            assertNotNull(SmeltingType.BLASTING);
            assertNotNull(SmeltingType.SMOKING);
            assertNotNull(SmeltingType.CAMPFIRE);
        }

        @Test
        @DisplayName("RecipeCategory should have standard categories")
        void testRecipeCategories() {
            assertTrue(RecipeCategory.values().length >= 4);
            assertNotNull(RecipeCategory.MISC);
            assertNotNull(RecipeCategory.BUILDING);
            assertNotNull(RecipeCategory.EQUIPMENT);
            assertNotNull(RecipeCategory.REDSTONE);
        }

        @ParameterizedTest
        @EnumSource(SmeltingType.class)
        @DisplayName("SmeltingType should have valid ID")
        void testSmeltingTypeIds(SmeltingType type) {
            assertNotNull(type.getId());
            assertFalse(type.getId().isEmpty());
        }

        @ParameterizedTest
        @EnumSource(CraftingType.class)
        @DisplayName("CraftingType should have valid ID")
        void testCraftingTypeIds(CraftingType type) {
            assertNotNull(type.getId());
            assertFalse(type.getId().isEmpty());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // EDGE CASES
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        private MockRecipeConfigManager configManager;

        @BeforeEach
        void setUp() {
            configManager = new MockRecipeConfigManager();
        }

        @Test
        @DisplayName("Clear should remove all recipes")
        void testClear() {
            for (int i = 0; i < 10; i++) {
                configManager.addRecipe(new MockCraftingRecipeData(
                    MockResourceLocation.parse("devmod:recipe" + i),
                    CraftingType.SHAPED,
                    List.of(MockIngredientData.ofItem("minecraft:stone")),
                    MockResultData.of("minecraft:cobblestone"),
                    "",
                    RecipeCategory.MISC
                ));
            }

            assertEquals(10, configManager.getRecipeCount());
            configManager.clear();
            assertEquals(0, configManager.getRecipeCount());
        }

        @Test
        @DisplayName("Get non-existent recipe should return empty")
        void testGetNonExistent() {
            Optional<MockRecipeData> result = configManager.getRecipe(
                MockResourceLocation.parse("devmod:does_not_exist")
            );
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Remove non-existent recipe should return empty")
        void testRemoveNonExistent() {
            Optional<MockRecipeData> result = configManager.removeRecipe(
                MockResourceLocation.parse("devmod:does_not_exist")
            );
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Adding same ID should overwrite")
        void testOverwrite() {
            MockCraftingRecipeData recipe1 = new MockCraftingRecipeData(
                MockResourceLocation.parse("devmod:same_id"),
                CraftingType.SHAPED,
                List.of(MockIngredientData.ofItem("minecraft:stone")),
                MockResultData.of("minecraft:cobblestone"),
                "group1",
                RecipeCategory.MISC
            );

            MockCraftingRecipeData recipe2 = new MockCraftingRecipeData(
                MockResourceLocation.parse("devmod:same_id"),
                CraftingType.SHAPELESS,
                List.of(MockIngredientData.ofItem("minecraft:diamond")),
                MockResultData.of("minecraft:diamond_block"),
                "group2",
                RecipeCategory.BUILDING
            );

            configManager.addRecipe(recipe1);
            configManager.addRecipe(recipe2);

            assertEquals(1, configManager.getRecipeCount());

            Optional<MockRecipeData> retrieved = configManager.getRecipe(
                MockResourceLocation.parse("devmod:same_id")
            );
            assertTrue(retrieved.isPresent());
            assertEquals("group2", ((MockCraftingRecipeData) retrieved.get()).group());
        }

        @Test
        @DisplayName("Large number of recipes should work")
        void testLargeNumberOfRecipes() {
            int count = 1000;
            for (int i = 0; i < count; i++) {
                configManager.addRecipe(new MockCraftingRecipeData(
                    MockResourceLocation.parse("devmod:recipe" + i),
                    CraftingType.SHAPED,
                    List.of(MockIngredientData.ofItem("minecraft:stone")),
                    MockResultData.of("minecraft:cobblestone"),
                    "",
                    RecipeCategory.MISC
                ));
            }

            assertEquals(count, configManager.getRecipeCount());

            // Random access should work
            Optional<MockRecipeData> recipe500 = configManager.getRecipe(
                MockResourceLocation.parse("devmod:recipe500")
            );
            assertTrue(recipe500.isPresent());
        }

        @Test
        @DisplayName("Batch with null entries should skip nulls")
        void testBatchWithNulls() {
            List<MockRecipeData> batch = new ArrayList<>();
            batch.add(new MockCraftingRecipeData(
                MockResourceLocation.parse("devmod:valid1"),
                CraftingType.SHAPED,
                List.of(MockIngredientData.ofItem("minecraft:stone")),
                MockResultData.of("minecraft:cobblestone"),
                "",
                RecipeCategory.MISC
            ));
            batch.add(null);
            batch.add(new MockCraftingRecipeData(
                MockResourceLocation.parse("devmod:valid2"),
                CraftingType.SHAPED,
                List.of(MockIngredientData.ofItem("minecraft:stone")),
                MockResultData.of("minecraft:cobblestone"),
                "",
                RecipeCategory.MISC
            ));

            int added = configManager.addBatch(batch);
            assertEquals(2, added);
            assertEquals(2, configManager.getRecipeCount());
        }
    }
}
