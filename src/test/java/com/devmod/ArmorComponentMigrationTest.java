package com.devmod;

import com.devmod.components.ArmorComponents;
import com.devmod.config.ArmorConfigManager;
import com.devmod.stats.ArmorStats;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import static org.junit.jupiter.api.Assertions.*;
import static java.util.Objects.requireNonNull;

/**
 * Tests for NBT → component migration in armor system.
 */
public class ArmorComponentMigrationTest {

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();
    }
    
    @Test
    void migrateFromNBT_preservesAllStats() {
        var armorComponent = ArmorComponents.armorStatsComponent();
        assertNotNull(armorComponent, "armor_stats component unavailable");

        // Create armor with legacy NBT data
        ItemStack helmet = new ItemStack(requireNonNull(Items.DIAMOND_HELMET));
        
        // Add legacy NBT stats
        ArmorStats originalStats = new ArmorStats();
        originalStats.physicalReduction = 0.3f;
        originalStats.fireReduction = 0.2f;
        originalStats.armorBonus = 5.0f;
        originalStats.thornsReflect = true;
        originalStats.thornsPercent = 0.1f;
        
        CompoundTag customTag = new CompoundTag();
        CompoundTag armorTag = new CompoundTag();
        originalStats.save(armorTag);
        customTag.put("ArmorModStats", armorTag);
        
        helmet.set(java.util.Objects.requireNonNull(DataComponents.CUSTOM_DATA), java.util.Objects.requireNonNull(CustomData.of(customTag)));
        
        // Simulate migration by reading from NBT and setting component
        CustomData customData = helmet.get(java.util.Objects.requireNonNull(DataComponents.CUSTOM_DATA));
        if (customData != null && customData.contains("ArmorModStats")) {
            ArmorStats migratedStats = ArmorStats.load(java.util.Objects.requireNonNull(customData.copyTag()).getCompound("ArmorModStats"));
            CompoundTag migratedTag = new CompoundTag();
            migratedStats.save(migratedTag);
            helmet.set(armorComponent, java.util.Objects.requireNonNull(migratedTag.copy()));
        }
        
        // Verify component was set correctly
        CompoundTag componentTag = helmet.get(armorComponent);
        ArmorStats componentStats = componentTag == null ? null : ArmorStats.load(componentTag.copy());
        assertNotNull(componentStats);
        assertEquals(0.3f, componentStats.physicalReduction, 0.001f);
        assertEquals(0.2f, componentStats.fireReduction, 0.001f);
        assertEquals(5.0f, componentStats.armorBonus, 0.001f);
        assertTrue(componentStats.thornsReflect);
        assertEquals(0.1f, componentStats.thornsPercent, 0.001f);
    }
    
    @Test
    void componentRoundTrip_preservesData() {
        var armorComponent = ArmorComponents.armorStatsComponent();
        assertNotNull(armorComponent, "armor_stats component unavailable");

        ItemStack chestplate = new ItemStack(requireNonNull(Items.NETHERITE_CHESTPLATE));
        
        ArmorStats original = new ArmorStats();
        original.magicReduction = 0.4f;
        original.explosionReduction = 0.3f;
        original.knockbackResistance = 0.5f;
        original.shieldBlockStrength = 0.8f;
        original.shieldReflectProjectiles = true;
        
        // Set component
        CompoundTag tag = new CompoundTag();
        original.save(tag);
        chestplate.set(armorComponent, java.util.Objects.requireNonNull(tag.copy()));
        
        // Retrieve component
        CompoundTag retrievedTag = chestplate.get(armorComponent);
        ArmorStats retrieved = retrievedTag == null ? null : ArmorStats.load(retrievedTag.copy());
        
        assertNotNull(retrieved);
        assertEquals(0.4f, retrieved.magicReduction, 0.001f);
        assertEquals(0.3f, retrieved.explosionReduction, 0.001f);
        assertEquals(0.5f, retrieved.knockbackResistance, 0.001f);
        assertEquals(0.8f, retrieved.shieldBlockStrength, 0.001f);
        assertTrue(retrieved.shieldReflectProjectiles);
    }
    
    @Test
    void emptyComponent_returnsDefaults() {
        var armorComponent = ArmorComponents.armorStatsComponent();
        assertNotNull(armorComponent, "armor_stats component unavailable");

        ItemStack boots = new ItemStack(requireNonNull(Items.LEATHER_BOOTS));
        
        // No component set
        CompoundTag statsTag = boots.get(armorComponent);
        
        // Should be null (no component), fallback to defaults handled by ArmorConfigManager
        assertNull(statsTag);
    }
}
