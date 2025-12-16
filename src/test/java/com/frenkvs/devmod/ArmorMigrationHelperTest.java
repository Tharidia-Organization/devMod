package com.frenkvs.devmod;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for armor migration helper functionality.
 */
public class ArmorMigrationHelperTest {
    
    @Test
    void migrateIfNeeded_withNBTData_migratesSuccessfully() {
        ItemStack helmet = new ItemStack("minecraft:diamond_helmet");
        
        // Add legacy NBT
        ArmorStats original = new ArmorStats();
        original.physicalReduction = 0.25f;
        original.fireReduction = 0.15f;
        
        CompoundTag customTag = new CompoundTag();
        CompoundTag armorTag = new CompoundTag();
        original.save(armorTag);
        customTag.put("ArmorModStats", armorTag);
        helmet.set(java.util.Objects.requireNonNull(DataComponents.CUSTOM_DATA), java.util.Objects.requireNonNull(CustomData.of(customTag)));
        
        // Migrate
        boolean migrated = ArmorMigrationHelper.migrateIfNeeded(helmet);
        
        assertTrue(migrated, "Migration should succeed");
        
        // Verify component was set
        net.minecraft.nbt.CompoundTag componentTag = helmet.get(java.util.Objects.requireNonNull(ArmorComponents.ARMOR_STATS.get()));
        ArmorStats componentStats = componentTag == null ? null : ArmorStats.load(componentTag.copy());
        assertNotNull(componentStats);
        assertEquals(0.25f, componentStats.physicalReduction, 0.001f);
        assertEquals(0.15f, componentStats.fireReduction, 0.001f);
    }
    
    @Test
    void migrateIfNeeded_withExistingComponent_skipsmigration() {
        ItemStack chestplate = new ItemStack("minecraft:iron_chestplate");
        
        // Set component first
        ArmorStats existing = new ArmorStats();
        existing.magicReduction = 0.3f;
        net.minecraft.nbt.CompoundTag existingTag = new net.minecraft.nbt.CompoundTag();
        existing.save(existingTag);
        chestplate.set(java.util.Objects.requireNonNull(ArmorComponents.ARMOR_STATS.get()), java.util.Objects.requireNonNull(existingTag.copy()));
        
        // Add NBT (should be ignored)
        CompoundTag customTag = new CompoundTag();
        CompoundTag armorTag = new CompoundTag();
        ArmorStats nbtStats = new ArmorStats();
        nbtStats.physicalReduction = 0.5f; // Different value
        nbtStats.save(armorTag);
        customTag.put("ArmorModStats", armorTag);
        chestplate.set(java.util.Objects.requireNonNull(DataComponents.CUSTOM_DATA), java.util.Objects.requireNonNull(CustomData.of(customTag)));
        
        // Try migration
        boolean migrated = ArmorMigrationHelper.migrateIfNeeded(chestplate);
        
        assertFalse(migrated, "Should skip migration when component exists");
        
        // Verify original component unchanged
        net.minecraft.nbt.CompoundTag componentTag = chestplate.get(java.util.Objects.requireNonNull(ArmorComponents.ARMOR_STATS.get()));
        ArmorStats componentStats = componentTag == null ? null : ArmorStats.load(componentTag.copy());
        assertNotNull(componentStats);
        assertEquals(0.3f, componentStats.magicReduction, 0.001f);
        assertEquals(0.0f, componentStats.physicalReduction, 0.001f); // Should be original
    }
    
    @Test
    void getStatsWithMigration_performsAutoMigration() {
        ItemStack boots = new ItemStack("minecraft:leather_boots");
        
        // Add only NBT data
        ArmorStats nbtStats = new ArmorStats();
        nbtStats.knockbackResistance = 0.4f;
        nbtStats.thornsReflect = true;
        
        CompoundTag customTag = new CompoundTag();
        CompoundTag armorTag = new CompoundTag();
        nbtStats.save(armorTag);
        customTag.put("ArmorModStats", armorTag);
        boots.set(java.util.Objects.requireNonNull(DataComponents.CUSTOM_DATA), java.util.Objects.requireNonNull(CustomData.of(customTag)));
        
        // Get stats with auto-migration
        ArmorStats stats = ArmorMigrationHelper.getStatsWithMigration(boots);
        
        assertNotNull(stats);
        assertEquals(0.4f, stats.knockbackResistance, 0.001f);
        assertTrue(stats.thornsReflect);
        
        // Verify component was created
        net.minecraft.nbt.CompoundTag componentStats = boots.get(java.util.Objects.requireNonNull(ArmorComponents.ARMOR_STATS.get()));
        assertNotNull(componentStats);
    }
}
