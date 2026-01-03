package com.devmod;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import com.devmod.components.ArmorComponents;
import com.devmod.stats.ArmorStats;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;

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
        originalStats.setPhysicalReduction(0.3f);
        originalStats.setFireReduction(0.2f);
        originalStats.setArmorBonus(5.0f);
        originalStats.setThornsReflect(true);
        originalStats.setThornsPercent(0.1f);
        
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
        assertEquals(0.3f, componentStats.getPhysicalReduction(), 0.001f);
        assertEquals(0.2f, componentStats.getFireReduction(), 0.001f);
        assertEquals(5.0f, componentStats.getArmorBonus(), 0.001f);
        assertTrue(componentStats.isThornsReflect());
        assertEquals(0.1f, componentStats.getThornsPercent(), 0.001f);
    }
    
    @Test
    void componentRoundTrip_preservesData() {
        var armorComponent = ArmorComponents.armorStatsComponent();
        assertNotNull(armorComponent, "armor_stats component unavailable");

        ItemStack chestplate = new ItemStack(requireNonNull(Items.NETHERITE_CHESTPLATE));
        
        ArmorStats original = new ArmorStats();
        original.setMagicReduction(0.4f);
        original.setExplosionReduction(0.3f);
        original.setKnockbackResistance(0.5f);
        original.setShieldBlockStrength(0.8f);
        original.setShieldReflectProjectiles(true);
        
        // Set component
        CompoundTag tag = new CompoundTag();
        original.save(tag);
        chestplate.set(armorComponent, java.util.Objects.requireNonNull(tag.copy()));
        
        // Retrieve component
        CompoundTag retrievedTag = chestplate.get(armorComponent);
        ArmorStats retrieved = retrievedTag == null ? null : ArmorStats.load(retrievedTag.copy());
        
        assertNotNull(retrieved);
        assertEquals(0.4f, retrieved.getMagicReduction(), 0.001f);
        assertEquals(0.3f, retrieved.getExplosionReduction(), 0.001f);
        assertEquals(0.5f, retrieved.getKnockbackResistance(), 0.001f);
        assertEquals(0.8f, retrieved.getShieldBlockStrength(), 0.001f);
        assertTrue(retrieved.isShieldReflectProjectiles());
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
