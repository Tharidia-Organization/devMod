package com.devmod.combat.bridge;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;

class CombatVisualsBridgeTest {

    // ========== NoOp implementation ==========

    @Test
    void noOp_isDefaultInstance() {
        // Reset to default by setting a NoOp
        CombatVisualsBridge.setInstance(new CombatVisualsBridge.NoOp());
        assertNotNull(CombatVisualsBridge.get());
        assertInstanceOf(CombatVisualsBridge.NoOp.class, CombatVisualsBridge.get());
    }

    @Test
    void noOp_getImpactDataReturnsNull() {
        var noOp = new CombatVisualsBridge.NoOp();
        assertNull(noOp.getImpactData());
    }

    @Test
    void noOp_getImpactTargetReturnsNull() {
        var noOp = new CombatVisualsBridge.NoOp();
        assertNull(noOp.getImpactTarget(new Object()));
    }

    @Test
    void noOp_getImpactAttackerUUIDReturnsNull() {
        var noOp = new CombatVisualsBridge.NoOp();
        assertNull(noOp.getImpactAttackerUUID(new Object()));
    }

    @Test
    void noOp_resolveRangedStatsReturnsDefaults() {
        var noOp = new CombatVisualsBridge.NoOp();
        var result = noOp.resolveRangedStats(ItemStack.EMPTY);
        assertEquals(CombatVisualsBridge.RangedStatsSnapshot.DEFAULTS, result);
    }

    @Test
    void noOp_recordDpsDamageDoesNotThrow() {
        var noOp = new CombatVisualsBridge.NoOp();
        assertDoesNotThrow(() -> noOp.recordDpsDamage(UUID.randomUUID(), 10f));
    }

    // ========== RangedStatsSnapshot ==========

    @Test
    void rangedStatsDefaults_hasExpectedValues() {
        var defaults = CombatVisualsBridge.RangedStatsSnapshot.DEFAULTS;
        assertEquals(0f, defaults.baseDamage());
        assertEquals(1f, defaults.projectileSpeed());
        assertEquals(0f, defaults.critChance());
        assertEquals(1.5f, defaults.critDamage());
    }

    @Test
    void rangedStatsSnapshot_customValues() {
        var snapshot = new CombatVisualsBridge.RangedStatsSnapshot(8f, 2f, 0.25f, 2.0f);
        assertEquals(8f, snapshot.baseDamage());
        assertEquals(2f, snapshot.projectileSpeed());
        assertEquals(0.25f, snapshot.critChance());
        assertEquals(2.0f, snapshot.critDamage());
    }

    @Test
    void rangedStatsSnapshot_equalsWorks() {
        var a = new CombatVisualsBridge.RangedStatsSnapshot(5f, 1f, 0.1f, 1.5f);
        var b = new CombatVisualsBridge.RangedStatsSnapshot(5f, 1f, 0.1f, 1.5f);
        assertEquals(a, b);
    }

    @Test
    void rangedStatsSnapshot_notEqualsForDifferentValues() {
        var a = new CombatVisualsBridge.RangedStatsSnapshot(5f, 1f, 0.1f, 1.5f);
        var b = new CombatVisualsBridge.RangedStatsSnapshot(6f, 1f, 0.1f, 1.5f);
        assertNotEquals(a, b);
    }

    // ========== Singleton get/set ==========

    @Test
    void setInstance_replacesDefault() {
        var original = CombatVisualsBridge.get();
        var custom = new CombatVisualsBridge.NoOp();
        CombatVisualsBridge.setInstance(custom);
        assertSame(custom, CombatVisualsBridge.get());

        // Restore original
        CombatVisualsBridge.setInstance(original);
    }
}
