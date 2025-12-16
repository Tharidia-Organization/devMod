package com.frenkvs.devmod;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import java.util.Objects;

/**
 * Helper for migrating armor stats from NBT to components.
 * Minimal implementation for auto-migration support.
 */
public final class ArmorMigrationHelper {
    
    private static final String NBT_KEY = "ArmorModStats";
    
    /**
     * Migrate armor stats from NBT to component if needed.
     * Returns true if migration occurred.
     */
    public static boolean migrateIfNeeded(ItemStack stack) {
        if (stack.isEmpty()) return false;
        
        // Skip if component already exists
        if (stack.has(Objects.requireNonNull(ArmorComponents.ARMOR_STATS.get()))) {
            return false;
        }
        
        // Check for legacy NBT data
        CustomData customData = stack.get(Objects.requireNonNull(DataComponents.CUSTOM_DATA));
        if (customData == null || !customData.contains(NBT_KEY)) {
            return false;
        }
        
        try {
            // Load from NBT
            CompoundTag nbtStats = customData.copyTag().getCompound(NBT_KEY);
            ArmorStats stats = ArmorStats.load(nbtStats);
            CompoundTag statsTag = new CompoundTag();
            stats.save(statsTag);
            
            // Set component
            stack.set(Objects.requireNonNull(ArmorComponents.ARMOR_STATS.get()), statsTag.copy());
            
            // Optionally clean up NBT (keep for compatibility)
            // CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(NBT_KEY));
            
            return true;
        } catch (Exception e) {
            // Migration failed, keep NBT as fallback
            return false;
        }
    }
    
    /**
     * Get armor stats with automatic migration.
     */
    public static ArmorStats getStatsWithMigration(ItemStack stack) {
        // Try component first
        CompoundTag component = stack.get(Objects.requireNonNull(ArmorComponents.ARMOR_STATS.get()));
        if (component != null && !component.isEmpty()) {
            return ArmorStats.load(component.copy());
        }
        
        // Try migration
        if (migrateIfNeeded(stack)) {
            CompoundTag migrated = stack.get(Objects.requireNonNull(ArmorComponents.ARMOR_STATS.get()));
            if (migrated != null && !migrated.isEmpty()) {
                return ArmorStats.load(migrated.copy());
            }
        }
        
        // Fallback to config manager
        return ArmorConfigManager.getStats(stack);
    }
}
