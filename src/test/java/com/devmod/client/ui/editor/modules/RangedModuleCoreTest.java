package com.devmod.client.ui.editor.modules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.devmod.client.ui.editor.RangedWeaponModule;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RangedModuleCore.
 * Tests stats management, comparison, and state tracking.
 */
public class RangedModuleCoreTest {

    private RangedModuleCore core;

    @BeforeEach
    void setUp() {
        core = new RangedModuleCore();
    }

    @Test
    void defaultStatsAreInitialized() {
        RangedWeaponModule.RangedStats stats = core.getStats();
        assertNotNull(stats, "Stats should not be null");
        // Check default values
        assertEquals(1.0f, stats.drawSpeed, 0.001f, "Default drawSpeed should be 1.0");
        assertEquals(1.0f, stats.accuracy, 0.001f, "Default accuracy should be 1.0");
    }

    @Test
    void statsEqualsWithEpsilon() {
        RangedWeaponModule.RangedStats a = new RangedWeaponModule.RangedStats();
        RangedWeaponModule.RangedStats b = new RangedWeaponModule.RangedStats();

        // Identical stats should be equal
        assertTrue(core.statsEquals(a, b), "Identical stats should be equal");

        // Small difference within epsilon should be equal
        b.drawSpeed = 1.0005f; // Within 1e-3 epsilon
        assertTrue(core.statsEquals(a, b), "Stats within epsilon should be equal");

        // Larger difference should not be equal
        b.drawSpeed = 1.01f; // Outside 1e-3 epsilon
        assertFalse(core.statsEquals(a, b), "Stats outside epsilon should not be equal");
    }

    @Test
    void hasPendingDiffDetectsChanges() {
        // Initially no diff
        assertFalse(core.hasPendingDiff(), "No pending diff initially");

        // Modify stats
        core.getStats().drawSpeed = 2.0f;
        assertTrue(core.hasPendingDiff(), "Should have pending diff after modification");
    }

    @Test
    void resetToOriginalRestoresStats() {
        // Save original
        RangedWeaponModule.RangedStats original = core.getStats().copy();

        // Modify stats
        core.getStats().drawSpeed = 2.0f;
        core.getStats().accuracy = 0.5f;
        assertTrue(core.hasPendingDiff(), "Should have pending diff");

        // Reset
        core.resetToOriginal();
        assertFalse(core.hasPendingDiff(), "No pending diff after reset");
        assertEquals(original.drawSpeed, core.getStats().drawSpeed, 0.001f);
        assertEquals(original.accuracy, core.getStats().accuracy, 0.001f);
    }

    @Test
    void buildStatsTagContainsAllFields() {
        core.getStats().drawSpeed = 1.5f;
        core.getStats().accuracy = 0.9f;
        core.getStats().range = 2.0f;
        core.getStats().projectileSpeed = 1.2f;
        core.getStats().piercing = 3;
        core.getStats().multishot = true;
        core.getStats().multishotCount = 3;
        core.getStats().critChance = 0.1f;
        core.getStats().critDamage = 1.5f;

        var tag = core.buildStatsTag();
        assertNotNull(tag, "Stats tag should not be null");
        assertEquals(1.5f, tag.getFloat("drawSpeed"), 0.001f);
        assertEquals(0.9f, tag.getFloat("accuracy"), 0.001f);
        assertEquals(2.0f, tag.getFloat("range"), 0.001f);
        assertEquals(1.2f, tag.getFloat("projectileSpeed"), 0.001f);
        assertEquals(3, tag.getInt("piercing"));
        assertTrue(tag.getBoolean("multishot"));
        assertEquals(3, tag.getInt("multishotCount"));
        assertEquals(0.1f, tag.getFloat("critChance"), 0.001f);
        assertEquals(1.5f, tag.getFloat("critDamage"), 0.001f);
    }

    @Test
    void statsEqualsHandlesNulls() {
        RangedWeaponModule.RangedStats a = new RangedWeaponModule.RangedStats();

        // Both null should be equal
        assertTrue(core.statsEquals(null, null), "Both null should be equal");

        // One null should not be equal
        assertFalse(core.statsEquals(a, null), "One null should not be equal");
        assertFalse(core.statsEquals(null, a), "One null should not be equal");
    }

    @Test
    void statsEqualsChecksBooleans() {
        RangedWeaponModule.RangedStats a = new RangedWeaponModule.RangedStats();
        RangedWeaponModule.RangedStats b = new RangedWeaponModule.RangedStats();

        a.multishot = true;
        b.multishot = false;
        assertFalse(core.statsEquals(a, b), "Different multishot should not be equal");

        b.multishot = true;
        assertTrue(core.statsEquals(a, b), "Same multishot should be equal");
    }

    @Test
    void statsEqualsChecksIntegers() {
        RangedWeaponModule.RangedStats a = new RangedWeaponModule.RangedStats();
        RangedWeaponModule.RangedStats b = new RangedWeaponModule.RangedStats();

        a.piercing = 3;
        b.piercing = 2;
        assertFalse(core.statsEquals(a, b), "Different piercing should not be equal");

        b.piercing = 3;
        assertTrue(core.statsEquals(a, b), "Same piercing should be equal");
    }

    @Test
    void statsEqualsChecksAmmoFilter() {
        RangedWeaponModule.RangedStats a = new RangedWeaponModule.RangedStats();
        RangedWeaponModule.RangedStats b = new RangedWeaponModule.RangedStats();

        a.ammoFilter = "#minecraft:arrows";
        b.ammoFilter = "minecraft:arrow";
        assertFalse(core.statsEquals(a, b), "Different ammoFilter should not be equal");

        b.ammoFilter = "#minecraft:arrows";
        assertTrue(core.statsEquals(a, b), "Same ammoFilter should be equal");
    }

}
