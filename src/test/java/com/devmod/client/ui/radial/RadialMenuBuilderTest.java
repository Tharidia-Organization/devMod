package com.devmod.client.ui.radial;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.client.ui.radial.model.MacroCategory;

/**
 * Unit tests for RadialMenuBuilder.
 * Verifies the fluent builder API for programmatic radial menu registration.
 */
@DisplayName("RadialMenuBuilder Tests")
public class RadialMenuBuilderTest {

    @BeforeEach
    void setUp() {
        // Reset and initialize for test (does not require Minecraft)
        RadialMenuRuntimeRegistry.reset();
        RadialMenuRuntimeRegistry.initializeForTest();
    }

    @AfterEach
    void tearDown() {
        // Clean up any dynamic categories we added
        RadialMenuRuntimeRegistry.removeCategory(MacroCategory.TOOLS, "test_category");
        RadialMenuRuntimeRegistry.removeCategory(MacroCategory.TOOLS, "builder_test");
        RadialMenuRuntimeRegistry.removeCategory(MacroCategory.ANALYZE, "analysis_tools");
    }

    @Nested
    @DisplayName("Builder API Tests")
    class BuilderApiTests {

        @Test
        @DisplayName("forMacro returns non-null builder")
        void testForMacroReturnsBuilder() {
            RadialMenuBuilder builder = RadialMenuBuilder.forMacro(MacroCategory.TOOLS);
            assertNotNull(builder);
            assertEquals(MacroCategory.TOOLS, builder.getMacro());
        }

        @Test
        @DisplayName("forMacro with null throws NullPointerException")
        void testForMacroWithNullThrows() {
            assertThrows(NullPointerException.class, () -> {
                try {
                    var method = RadialMenuBuilder.class.getDeclaredMethod("forMacro", MacroCategory.class);
                    method.invoke(null, new Object[] { null });
                } catch (java.lang.reflect.InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof RuntimeException runtime) {
                        throw runtime;
                    }
                    if (cause instanceof Error error) {
                        throw error;
                    }
                    throw new RuntimeException(cause);
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        @Test
        @DisplayName("category returns CategoryBuilder")
        void testCategoryReturnsCategoryBuilder() {
            RadialMenuBuilder builder = RadialMenuBuilder.forMacro(MacroCategory.TOOLS);
            RadialMenuBuilder.CategoryBuilder catBuilder = builder.category("test_category");
            assertNotNull(catBuilder);
        }

        @Test
        @DisplayName("CategoryBuilder chain returns to parent")
        void testCategoryBuilderChain() {
            RadialMenuBuilder parent = RadialMenuBuilder.forMacro(MacroCategory.TOOLS);
            RadialMenuBuilder returned = parent
                .category("test_category")
                    .name("Test Category")
                    .color(0xFFFF0000)
                    .icon("🔧")
                .endCategory();
            assertSame(parent, returned);
        }

        @Test
        @DisplayName("Full builder chain with items")
        void testFullBuilderChain() {
            int initialCount = RadialMenuRuntimeRegistry.getCategoryCount(MacroCategory.TOOLS);

            RadialMenuBuilder.forMacro(MacroCategory.TOOLS)
                .category("builder_test")
                    .name("Builder Test")
                    .color(0xFF00FF00)
                    .icon("✓")
                    .item("devmod.debug.overlay.toggle")
                    .item("devmod.debug.body_parts.toggle", () -> true)
                .endCategory()
                .register();

            int afterCount = RadialMenuRuntimeRegistry.getCategoryCount(MacroCategory.TOOLS);
            assertEquals(initialCount + 1, afterCount, "One category should be added");

            // Verify the category exists
            RadialCategory found = RadialMenuRuntimeRegistry.findCategory("builder_test");
            assertNotNull(found, "Category should be findable");
            assertEquals("Builder Test", found.getName());
        }

        @Test
        @DisplayName("Multiple categories in one builder")
        void testMultipleCategories() {
            int initialCount = RadialMenuRuntimeRegistry.getCategoryCount(MacroCategory.ANALYZE);

            RadialMenuBuilder builder = RadialMenuBuilder.forMacro(MacroCategory.ANALYZE);
            builder.category("analysis_tools")
                    .name("Analysis")
                    .color(0xFF0000FF)
                .endCategory();

            builder.register();

            int afterCount = RadialMenuRuntimeRegistry.getCategoryCount(MacroCategory.ANALYZE);
            assertTrue(afterCount > initialCount, "Categories should be added");
        }
    }

    @Nested
    @DisplayName("CategoryBuilder Configuration Tests")
    class CategoryBuilderConfigTests {

        @Test
        @DisplayName("name() sets category name")
        void testNameSetter() {
            RadialMenuBuilder.forMacro(MacroCategory.TOOLS)
                .category("test_category")
                    .name("Custom Name")
                .endCategory()
                .register();

            RadialCategory found = RadialMenuRuntimeRegistry.findCategory("test_category");
            assertNotNull(found);
            assertEquals("Custom Name", found.getName());
        }

        @Test
        @DisplayName("color() sets category color")
        void testColorSetter() {
            int expectedColor = 0xFFAABBCC;

            RadialMenuBuilder.forMacro(MacroCategory.TOOLS)
                .category("test_category")
                    .name("Color Test")
                    .color(expectedColor)
                .endCategory()
                .register();

            RadialCategory found = RadialMenuRuntimeRegistry.findCategory("test_category");
            assertNotNull(found);
            assertEquals(expectedColor, found.getColor());
        }

        @Test
        @DisplayName("icon() sets emoji icon")
        void testIconSetter() {
            RadialMenuBuilder.forMacro(MacroCategory.TOOLS)
                .category("test_category")
                    .name("Icon Test")
                    .icon("🔧")
                .endCategory()
                .register();

            RadialCategory found = RadialMenuRuntimeRegistry.findCategory("test_category");
            assertNotNull(found);
            assertEquals("🔧", found.getIcon());
        }

        @Test
        @DisplayName("item() with visibility supplier")
        void testItemWithVisibility() {
            RadialMenuBuilder.forMacro(MacroCategory.TOOLS)
                .category("test_category")
                    .name("Visibility Test")
                    .item("devmod.debug.overlay.toggle", () -> true)
                    .item("devmod.debug.body_parts.toggle", () -> false)
                .endCategory()
                .register();

            RadialCategory found = RadialMenuRuntimeRegistry.findCategory("test_category");
            assertNotNull(found);
            // Items are added but may be filtered by visibility
            assertNotNull(found.getItems());
        }
    }

    @Nested
    @DisplayName("SubcategoryBuilder Tests")
    class SubcategoryBuilderTests {

        @Test
        @DisplayName("subcategory() returns SubcategoryBuilder")
        void testSubcategoryReturnsBuilder() {
            RadialMenuBuilder.forMacro(MacroCategory.TOOLS)
                .category("test_category")
                    .name("Parent")
                    .subcategory("sub1")
                        .name("Subcategory 1")
                    .endSubcategory()
                .endCategory()
                .register();

            RadialCategory found = RadialMenuRuntimeRegistry.findCategory("test_category");
            assertNotNull(found);
            // Subcategories are added as items to the parent category
            // The builder should have created the structure correctly
            assertNotNull(found.getItems(), "Category should have items list");
        }

        @Test
        @DisplayName("endSubcategory() returns to parent CategoryBuilder")
        void testEndSubcategoryReturnsToParent() {
            RadialMenuBuilder.CategoryBuilder catBuilder = RadialMenuBuilder.forMacro(MacroCategory.TOOLS)
                .category("test_category");

            RadialMenuBuilder.CategoryBuilder returned = catBuilder
                .subcategory("sub1")
                    .name("Sub")
                .endSubcategory();

            assertSame(catBuilder, returned);
        }

        @Test
        @DisplayName("Nested subcategories work")
        void testNestedSubcategories() {
            RadialMenuBuilder.forMacro(MacroCategory.TOOLS)
                .category("test_category")
                    .name("Parent")
                    .subcategory("level1")
                        .name("Level 1")
                        .subcategory("level2")
                            .name("Level 2")
                        .endNestedSubcategory()
                    .endSubcategory()
                .endCategory()
                .register();

            RadialCategory found = RadialMenuRuntimeRegistry.findCategory("test_category");
            assertNotNull(found);
        }

        @Test
        @DisplayName("endNestedSubcategory() on top-level throws")
        void testEndNestedOnTopLevelThrows() {
            assertThrows(IllegalStateException.class, () -> {
                RadialMenuBuilder.forMacro(MacroCategory.TOOLS)
                    .category("test_category")
                        .subcategory("sub1")
                        .endNestedSubcategory(); // Should throw - not nested
            });
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Registered category appears in getCategories")
        void testRegisteredCategoryInGetCategories() {
            String uniqueId = "test_integration_" + System.currentTimeMillis();

            RadialMenuBuilder.forMacro(MacroCategory.TOOLS)
                .category(uniqueId)
                    .name("Integration Test")
                .endCategory()
                .register();

            List<RadialCategory> categories = RadialMenuRuntimeRegistry.getCategories(MacroCategory.TOOLS);
            boolean found = categories.stream().anyMatch(c -> uniqueId.equals(c.getId()));
            assertTrue(found, "Category should appear in getCategories");

            // Cleanup
            RadialMenuRuntimeRegistry.removeCategory(MacroCategory.TOOLS, uniqueId);
        }

        @Test
        @DisplayName("removeCategory removes dynamically added category")
        void testRemoveCategory() {
            String uniqueId = "test_remove_" + System.currentTimeMillis();

            RadialMenuBuilder.forMacro(MacroCategory.TOOLS)
                .category(uniqueId)
                    .name("To Remove")
                .endCategory()
                .register();

            // Verify it exists
            assertNotNull(RadialMenuRuntimeRegistry.findCategory(uniqueId));

            // Remove it
            boolean removed = RadialMenuRuntimeRegistry.removeCategory(MacroCategory.TOOLS, uniqueId);
            assertTrue(removed);

            // Verify it's gone
            assertNull(RadialMenuRuntimeRegistry.findCategory(uniqueId));
        }
    }
}
