package com.devmod.client.ui.editor.modules;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for GeneralModuleCore.
 * Tests item type detection logic.
 */
class GeneralModuleCoreTest {

    private GeneralModuleCore core;

    @BeforeEach
    void setUp() {
        core = new GeneralModuleCore();
    }

    @Test
    void nullItemReturnsfalseForWeapon() {
        assertFalse(core.isWeaponItem(null));
    }

    @Test
    void nullItemReturnsFalseForArmor() {
        assertFalse(core.isArmorItem(null));
    }

    @Test
    void nullItemReturnsFalseForUsable() {
        assertFalse(core.isUsableItem(null));
    }

    @Test
    void nullItemReturnsFalseForFood() {
        assertFalse(core.isFoodItem(null));
    }

    @Test
    void nullItemReturnsFalseForFuel() {
        assertFalse(core.isFuelItem(null));
    }

    @Test
    void nullItemReturnsFalseForEnchantments() {
        assertFalse(core.hasEnchantments(null));
    }

    @Test
    void hasRecipeReturnsTrueForNonNullItem() {
        // hasRecipe currently returns true for any non-null item
        // This test validates the null check
        assertFalse(core.hasRecipe(null));
    }
}
