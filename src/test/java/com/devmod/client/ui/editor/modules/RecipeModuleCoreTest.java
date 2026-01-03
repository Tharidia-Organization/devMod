package com.devmod.client.ui.editor.modules;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.devmod.recipe.CraftingType;
import com.devmod.recipe.RecipeCategory;

/**
 * Unit tests for RecipeModuleCore.
 * Tests state management and validation logic.
 */
class RecipeModuleCoreTest {

    private RecipeModuleCore core;

    @BeforeEach
    void setUp() {
        core = new RecipeModuleCore();
    }

    @Test
    void defaultStateIsInitialized() {
        assertEquals(CraftingType.SHAPED, core.getCraftingType());
        assertEquals("", core.getRecipeId());
        assertEquals("", core.getRecipeGroup());
        assertEquals(RecipeCategory.MISC, core.getCategory());
        assertEquals(1, core.getResultQuantity());
        assertFalse(core.isReplaceVanillaRecipe());
        assertNull(core.getVanillaRecipeToReplace());
    }

    @Test
    void setCraftingTypeUpdatesState() {
        core.setCraftingType(CraftingType.SHAPELESS);
        assertEquals(CraftingType.SHAPELESS, core.getCraftingType());
    }

    @Test
    void setCraftingTypeRejectsNull() {
        assertThrows(NullPointerException.class, () -> core.setCraftingType(null));
    }

    @Test
    void setRecipeIdHandlesNull() {
        core.setRecipeId(null);
        assertEquals("", core.getRecipeId());
    }

    @Test
    void setRecipeIdStoresValue() {
        core.setRecipeId("devmod:test_recipe");
        assertEquals("devmod:test_recipe", core.getRecipeId());
    }

    @Test
    void setRecipeGroupHandlesNull() {
        core.setRecipeGroup(null);
        assertEquals("", core.getRecipeGroup());
    }

    @Test
    void setCategoryHandlesNull() {
        core.setCategory(null);
        assertEquals(RecipeCategory.MISC, core.getCategory());
    }

    @Test
    void setCategoryStoresValue() {
        core.setCategory(RecipeCategory.BUILDING);
        assertEquals(RecipeCategory.BUILDING, core.getCategory());
    }

    @Test
    void setResultQuantityClampsToMinimum() {
        core.setResultQuantity(0);
        assertEquals(1, core.getResultQuantity());

        core.setResultQuantity(-5);
        assertEquals(1, core.getResultQuantity());
    }

    @Test
    void setResultQuantityClampsToMaximum() {
        core.setResultQuantity(100);
        assertEquals(64, core.getResultQuantity());

        core.setResultQuantity(65);
        assertEquals(64, core.getResultQuantity());
    }

    @Test
    void setResultQuantityAcceptsValidValues() {
        core.setResultQuantity(16);
        assertEquals(16, core.getResultQuantity());

        core.setResultQuantity(64);
        assertEquals(64, core.getResultQuantity());

        core.setResultQuantity(1);
        assertEquals(1, core.getResultQuantity());
    }

    @Test
    void replaceVanillaRecipeToggle() {
        assertFalse(core.isReplaceVanillaRecipe());

        core.setReplaceVanillaRecipe(true);
        assertTrue(core.isReplaceVanillaRecipe());

        core.setReplaceVanillaRecipe(false);
        assertFalse(core.isReplaceVanillaRecipe());
    }

    @Test
    void invalidateValidationIsCallable() {
        // Just verify the method exists and doesn't throw
        assertDoesNotThrow(() -> core.invalidateValidation());
    }
}
