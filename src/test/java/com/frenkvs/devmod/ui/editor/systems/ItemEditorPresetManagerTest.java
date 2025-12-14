package com.frenkvs.devmod.ui.editor.systems;

import com.frenkvs.devmod.ArmorStats;
import com.frenkvs.devmod.ItemEditorDataManager;
import com.frenkvs.devmod.WeaponStats;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ItemEditorPresetManagerTest {

    @Test
    void applyPreset_appliesWeaponStatsThroughConfigManager() {
        ItemEditorDataManager.PresetData data = new ItemEditorDataManager.PresetData("weapon");
        data.statValues = List.of(
            1f, 2f, 3f, 4f, // hit location
            5f, 6f, 7f, 8f, 9f, 10f, // core stats
            11f, 12f, // crit
            13f, 14f, 15f // bonuses
        );

        DataPreset preset = new DataPreset(data);
        ItemStack stack = new ItemStack("Sword");
        AtomicReference<WeaponStats> captured = new AtomicReference<>();

        ItemEditorPresetManager.INSTANCE.setWeaponApplier((item, stats) -> captured.set(stats));
        try {
            boolean applied = ItemEditorPresetManager.INSTANCE.applyPreset(preset, stack, 0);

            assertTrue(applied, "Preset should be applied");
            WeaponStats stats = captured.get();
            assertNotNull(stats);
            assertEquals(1f, stats.headMult);
            assertEquals(2f, stats.bodyMult);
            assertEquals(3f, stats.armsMult);
            assertEquals(4f, stats.legsMult);
            assertEquals(5f, stats.attackDamage);
            assertEquals(6f, stats.attackSpeed);
            assertEquals(7f, stats.attackReach);
            assertEquals(8f, stats.attackKnockback);
            assertEquals(9f, stats.armorPenetration);
            assertEquals(10f, stats.baseDamageBonus);
            assertEquals(11f, stats.critChance);
            assertEquals(12f, stats.critDamage);
            assertEquals(13f, stats.lifesteal);
            assertEquals(14f, stats.fireDamageBonus);
            assertEquals(15f, stats.magicDamageBonus);
        } finally {
            ItemEditorPresetManager.INSTANCE.resetAppliers();
        }
    }

    @Test
    void applyPreset_appliesArmorStatsThroughConfigManager() {
        ItemEditorDataManager.PresetData data = new ItemEditorDataManager.PresetData("armor");
        data.statValues = List.of(
            1f, 2f, 3f, 4f, 5f, // reductions
            6f, 7f, 8f, // bonuses
            9f, 1f // thorns percent + reflect flag
        );

        DataPreset preset = new DataPreset(data);
        ItemStack stack = new ItemStack("Chest");
        AtomicReference<ArmorStats> captured = new AtomicReference<>();

        ItemEditorPresetManager.INSTANCE.setArmorApplier((item, stats) -> captured.set(stats));
        try {
            boolean applied = ItemEditorPresetManager.INSTANCE.applyPreset(preset, stack, 0);

            assertTrue(applied, "Preset should be applied");
            ArmorStats stats = captured.get();
            assertNotNull(stats);
            assertEquals(1f, stats.physicalReduction);
            assertEquals(2f, stats.fireReduction);
            assertEquals(3f, stats.magicReduction);
            assertEquals(4f, stats.explosionReduction);
            assertEquals(5f, stats.projectileReduction);
            assertEquals(6f, stats.armorBonus);
            assertEquals(7f, stats.toughnessBonus);
            assertEquals(8f, stats.knockbackResistance);
            assertEquals(9f, stats.thornsPercent);
            assertTrue(stats.thornsReflect, "Thorns reflect should be enabled when value > 0.5");
        } finally {
            ItemEditorPresetManager.INSTANCE.resetAppliers();
        }
    }
}
