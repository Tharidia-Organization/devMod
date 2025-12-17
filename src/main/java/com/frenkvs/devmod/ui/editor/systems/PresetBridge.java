package com.frenkvs.devmod.ui.editor.systems;

import com.frenkvs.devmod.ItemEditorDataManager;

import java.time.Instant;
import java.util.*;

/**
 * Bridge utilities for converting between PresetRegistry.RegistryPreset and ItemEditorDataManager.PresetData.
 *
 * This allows the new hierarchical PresetRegistry to work seamlessly with the existing UI
 * that expects PresetData objects.
 */
public final class PresetBridge {

    private PresetBridge() {}

    // ═══════════════════════════════════════════════════════════════
    // REGISTRY PRESET → PRESET DATA
    // ═══════════════════════════════════════════════════════════════

    /**
     * Convert a RegistryPreset to PresetData for UI compatibility.
     *
     * @param rp the registry preset
     * @return a PresetData object usable by the UI
     */
    public static ItemEditorDataManager.PresetData toPresetData(PresetRegistry.RegistryPreset rp) {
        var data = new ItemEditorDataManager.PresetData(rp.name());
        data.itemType = rp.category();
        data.createdAt = rp.metadata().created().toEpochMilli();
        data.devmodVersion = rp.metadata().version();

        // Map scope to string
        data.scope = switch (rp.scope()) {
            case PresetScope.Global g -> "GLOBAL";
            case PresetScope.Category c -> "CATEGORY";
            case PresetScope.Modpack m -> "MODPACK";
        };

        // Convert values map to statValues list based on category
        data.statValues = convertValuesToStats(rp.values(), rp.category());

        return data;
    }

    /**
     * Convert values map to statValues list.
     * Order depends on category (weapon vs armor).
     */
    private static List<Float> convertValuesToStats(Map<String, Object> values, String category) {
        List<Float> stats = new ArrayList<>();

        if (isWeaponCategory(category)) {
            // Weapon stats order (15 values as per WeaponStats)
            stats.add(getFloat(values, "headMult", 1.0f));
            stats.add(getFloat(values, "bodyMult", 1.0f));
            stats.add(getFloat(values, "limbMult", 1.0f));
            stats.add(getFloat(values, "baseDamage", 5.0f));
            stats.add(getFloat(values, "attackSpeed", 1.6f));
            stats.add(getFloat(values, "critChance", 0.0f));
            stats.add(getFloat(values, "critMultiplier", 1.5f));
            stats.add(getFloat(values, "armorPen", 0.0f));
            stats.add(getFloat(values, "lifesteal", 0.0f));
            stats.add(getFloat(values, "knockback", 0.0f));
            stats.add(getFloat(values, "damageBonus", 0.0f));
            stats.add(getFloat(values, "range", 3.0f));
            stats.add(getFloat(values, "blockBreakSpeed", 1.0f));
            stats.add(getFloat(values, "durabilityMult", 1.0f));
            stats.add(getFloat(values, "enchantability", 10.0f));
        } else if (isArmorCategory(category)) {
            // Armor stats order (10 values as per ArmorStats)
            stats.add(getFloat(values, "physicalReduction", 0.0f));
            stats.add(getFloat(values, "magicReduction", 0.0f));
            stats.add(getFloat(values, "projectileReduction", 0.0f));
            stats.add(getFloat(values, "fireReduction", 0.0f));
            stats.add(getFloat(values, "explosionReduction", 0.0f));
            stats.add(getFloat(values, "fallReduction", 0.0f));
            stats.add(getFloat(values, "thorns", 0.0f));
            stats.add(getFloat(values, "knockbackResist", 0.0f));
            stats.add(getFloat(values, "durabilityMult", 1.0f));
            stats.add(getFloat(values, "weight", 1.0f));
        }

        return stats;
    }

    private static float getFloat(Map<String, Object> values, String key, float defaultValue) {
        Object val = values.get(key);
        if (val instanceof Number n) return n.floatValue();
        return defaultValue;
    }

    // ═══════════════════════════════════════════════════════════════
    // PRESET DATA → REGISTRY PRESET
    // ═══════════════════════════════════════════════════════════════

    /**
     * Convert a PresetData to RegistryPreset for registry storage.
     *
     * @param pd the preset data from UI
     * @return a RegistryPreset object
     */
    public static PresetRegistry.RegistryPreset toRegistryPreset(ItemEditorDataManager.PresetData pd) {
        String id = generateId(pd.name);
        String category = pd.itemType != null ? pd.itemType : "general";

        Map<String, Object> values = convertStatsToValues(pd.statValues, category);

        PresetRegistry.PresetMetadata metadata = new PresetRegistry.PresetMetadata(
            pd.devmodVersion != null ? pd.devmodVersion : "1.0.0",
            "user",
            Instant.ofEpochMilli(pd.createdAt > 0 ? pd.createdAt : System.currentTimeMillis()),
            List.of("user")
        );

        return new PresetRegistry.RegistryPreset(
            id,
            pd.name,
            "",  // No description in PresetData
            new PresetScope.Global(),  // User presets are global scope
            category,
            values,
            metadata
        );
    }

    /**
     * Convert statValues list to values map.
     */
    private static Map<String, Object> convertStatsToValues(List<Float> stats, String category) {
        Map<String, Object> values = new LinkedHashMap<>();

        if (stats == null || stats.isEmpty()) {
            return values;
        }

        if (isWeaponCategory(category) && stats.size() >= 15) {
            // Weapon stats
            values.put("headMult", stats.get(0));
            values.put("bodyMult", stats.get(1));
            values.put("limbMult", stats.get(2));
            values.put("baseDamage", stats.get(3));
            values.put("attackSpeed", stats.get(4));
            values.put("critChance", stats.get(5));
            values.put("critMultiplier", stats.get(6));
            values.put("armorPen", stats.get(7));
            values.put("lifesteal", stats.get(8));
            values.put("knockback", stats.get(9));
            values.put("damageBonus", stats.get(10));
            values.put("range", stats.get(11));
            values.put("blockBreakSpeed", stats.get(12));
            values.put("durabilityMult", stats.get(13));
            values.put("enchantability", stats.get(14));
        } else if (isArmorCategory(category) && stats.size() >= 10) {
            // Armor stats
            values.put("physicalReduction", stats.get(0));
            values.put("magicReduction", stats.get(1));
            values.put("projectileReduction", stats.get(2));
            values.put("fireReduction", stats.get(3));
            values.put("explosionReduction", stats.get(4));
            values.put("fallReduction", stats.get(5));
            values.put("thorns", stats.get(6));
            values.put("knockbackResist", stats.get(7));
            values.put("durabilityMult", stats.get(8));
            values.put("weight", stats.get(9));
        }

        return values;
    }

    /**
     * Generate a filesystem-safe ID from a preset name.
     */
    public static String generateId(String name) {
        if (name == null || name.isEmpty()) {
            return "unnamed_" + System.currentTimeMillis();
        }

        return name.toLowerCase()
            .replaceAll("[^a-z0-9_]", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_|_$", "");
    }

    // ═══════════════════════════════════════════════════════════════
    // CATEGORY HELPERS
    // ═══════════════════════════════════════════════════════════════

    private static boolean isWeaponCategory(String category) {
        if (category == null) return false;
        return Set.of("sword", "axe", "pickaxe", "shovel", "hoe", "bow", "crossbow", "trident", "weapon")
            .contains(category.toLowerCase());
    }

    private static boolean isArmorCategory(String category) {
        if (category == null) return false;
        return Set.of("helmet", "chestplate", "leggings", "boots", "shield", "armor")
            .contains(category.toLowerCase());
    }

    // ═══════════════════════════════════════════════════════════════
    // MERGE UTILITIES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Merge registry presets with user presets, respecting priority.
     * Registry presets come first (higher priority), then user presets.
     *
     * @param registryPresets presets from PresetRegistry
     * @param userPresets presets from ItemEditorDataManager
     * @return merged list with registry presets first
     */
    public static List<ItemEditorDataManager.PresetData> mergePresets(
            List<PresetRegistry.RegistryPreset> registryPresets,
            List<ItemEditorDataManager.PresetData> userPresets) {

        List<ItemEditorDataManager.PresetData> result = new ArrayList<>();

        // Add registry presets (converted)
        for (var rp : registryPresets) {
            result.add(toPresetData(rp));
        }

        // Add user presets (avoiding duplicates by name)
        Set<String> existingNames = result.stream()
            .map(p -> p.name.toLowerCase())
            .collect(java.util.stream.Collectors.toSet());

        for (var up : userPresets) {
            if (!existingNames.contains(up.name.toLowerCase())) {
                result.add(up);
            }
        }

        return result;
    }
}
