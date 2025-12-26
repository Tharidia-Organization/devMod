package com.devmod.client.ui.editor.systems;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

import net.neoforged.fml.ModList;

import com.devmod.DevMod;
import com.devmod.client.ui.editor.EditorModule;
import com.devmod.client.ui.editor.ItemEditorDataManager;
import com.devmod.client.ui.editor.core.UIConstants;
import com.devmod.client.ui.editor.modules.ArmorModule;
import com.devmod.client.ui.editor.modules.WeaponModule;
import com.devmod.stats.ArmorStats;
import com.devmod.stats.WeaponStats;
import com.devmod.util.DatapackIO;

public final class ItemEditorDataService {

    private static final String DEFAULT_DATAPACK_NAME = "devmod_balance_auto";

    // Callbacks
    private BiConsumer<String, Integer> statusConsumer;
    private Runnable onPresetListChanged;

    // =========================================================================
    // CONFIGURATION
    // =========================================================================

    /**
     * Set the status message consumer.
     */
    public void setStatusConsumer(BiConsumer<String, Integer> consumer) {
        this.statusConsumer = consumer;
    }

    /**
     * Set callback for when preset list changes (after save/delete/rename).
     */
    public void setOnPresetListChanged(Runnable callback) {
        this.onPresetListChanged = callback;
    }

    // =========================================================================
    // EXPORT OPERATIONS
    // =========================================================================

    /**
     * Export current item configuration to a file.
     *
     * @param item         The item to export
     * @param activeModule The active editor module
     * @return true if export was successful
     */
    public boolean exportItem(ItemStack item, EditorModule activeModule) {
        if (!supportsDataOps(activeModule)) {
            showStatus("Export available only for weapons/armors", UIConstants.Accent.ORANGE());
            return false;
        }

        ItemEditorDataManager data = ItemEditorDataManager.INSTANCE;
        ItemEditorDataManager.ItemConfigExport config = new ItemEditorDataManager.ItemConfigExport();
        config.itemId = getItemId(item);
        config.itemName = item.getHoverName().getString();
        config.stats = collectStatsForExport(activeModule);
        config.enchantments = collectEnchantmentsForExport(item);
        config.attributes = collectAttributesForExport(item);
        config.durability = item.isDamageableItem() ? item.getMaxDamage() - item.getDamageValue() : null;
        config.unbreakable = item.has(Objects.requireNonNull(DataComponents.UNBREAKABLE));
        var repairCostType = Objects.requireNonNull(DataComponents.REPAIR_COST);
        config.repairCost = item.has(repairCostType) ? item.get(repairCostType) : null;

        String fileName = buildSafeFileName(config.itemId + "_export_" + System.currentTimeMillis());
        boolean ok = data.exportToFile(config, fileName);

        if (ok) {
            data.addHistoryEntry("export", config.itemName, fileName);
            showStatus("Exported to " + fileName + ".json", UIConstants.Accent.GREEN());

            int exported = DatapackIO.exportOverrides(DEFAULT_DATAPACK_NAME);
            if (exported > 0) {
                showStatus("Datapack: " + DEFAULT_DATAPACK_NAME + " (" + exported + " items)", UIConstants.Accent.BLUE());
            }
        } else {
            showStatus("Export failed", UIConstants.Accent.RED());
        }

        return ok;
    }

    /**
     * Collect stats from the active module for export.
     */
    public List<Float> collectStatsForExport(EditorModule activeModule) {
        List<Float> stats = new ArrayList<>();

        if (activeModule instanceof WeaponModule weaponModule) {
            var s = weaponModule.getStats();
            stats.add(s.headMult);
            stats.add(s.bodyMult);
            stats.add(s.armsMult);
            stats.add(s.legsMult);
            stats.add(s.attackDamage);
            stats.add(s.attackSpeed);
            stats.add(s.attackReach);
            stats.add(s.attackKnockback);
            stats.add(s.armorPenetration);
            stats.add(s.baseDamageBonus);
            stats.add(s.critChance);
            stats.add(s.critDamage);
            stats.add(s.lifesteal);
            stats.add(s.fireDamageBonus);
            stats.add(s.magicDamageBonus);
            stats.add(s.damageBonus);
            stats.add(s.armorShred);
            stats.add(s.damageVsUndead);
            stats.add(s.damageVsArthropods);
            stats.add(s.damageVsPlayers);
            stats.add(s.trueDamagePercent);
        } else if (activeModule instanceof ArmorModule armorModule) {
            var s = armorModule.getStats();
            stats.add(s.physicalReduction);
            stats.add(s.fireReduction);
            stats.add(s.magicReduction);
            stats.add(s.explosionReduction);
            stats.add(s.projectileReduction);
            stats.add(s.armorBonus);
            stats.add(s.toughnessBonus);
            stats.add(s.knockbackResistance);
            stats.add(s.thornsPercent);
            stats.add(s.thornsReflect ? 1f : 0f);
        }

        return stats;
    }

    /**
     * Collect enchantments from item for export.
     */
    public List<ItemEditorDataManager.EnchantData> collectEnchantmentsForExport(ItemStack item) {
        List<ItemEditorDataManager.EnchantData> result = new ArrayList<>();
        var enchantType = Objects.requireNonNull(DataComponents.ENCHANTMENTS);
        ItemEnchantments enchants = item.has(enchantType)
            ? Objects.requireNonNull(item.get(enchantType))
            : ItemEnchantments.EMPTY;

        Minecraft mc = Minecraft.getInstance();
        Level level = mc != null ? mc.level : null;
        if (level == null) return result;

        var enchantKey = Objects.requireNonNull(Registries.ENCHANTMENT);
        var registry = Objects.requireNonNull(level.registryAccess().registryOrThrow(enchantKey));

        enchants.entrySet().forEach(entry -> {
            Holder<Enchantment> holder = entry.getKey();
            int enchantLevel = entry.getIntValue();
            Enchantment enchant = Objects.requireNonNull(holder.value());
            var key = registry.getKey(enchant);
            if (key != null) {
                result.add(new ItemEditorDataManager.EnchantData(key.toString(), enchantLevel));
            }
        });

        return result;
    }

    /**
     * Collect attributes from item for export.
     */
    public List<ItemEditorDataManager.AttrData> collectAttributesForExport(ItemStack item) {
        List<ItemEditorDataManager.AttrData> result = new ArrayList<>();
        var attrType = Objects.requireNonNull(DataComponents.ATTRIBUTE_MODIFIERS);
        ItemAttributeModifiers modifiers = item.has(attrType)
            ? Objects.requireNonNull(item.get(attrType))
            : ItemAttributeModifiers.EMPTY;

        Objects.requireNonNull(modifiers.modifiers()).forEach(entry -> {
            Holder<Attribute> attr = entry.attribute();
            AttributeModifier mod = entry.modifier();
            var key = BuiltInRegistries.ATTRIBUTE.getKey(Objects.requireNonNull(attr.value()));
            if (key != null) {
                int opOrdinal = mod.operation().ordinal();
                result.add(new ItemEditorDataManager.AttrData(key.toString(), mod.amount(), opOrdinal));
            }
        });

        return result;
    }

    // =========================================================================
    // IMPORT OPERATIONS
    // =========================================================================

    /**
     * Import item configuration from most recent export file.
     *
     * @param item         The current item (for history)
     * @param activeModule The active editor module
     * @return true if import was successful
     */
    public boolean importItem(ItemStack item, EditorModule activeModule) {
        if (!supportsDataOps(activeModule)) {
            showStatus("Import available only for weapons/armors", UIConstants.Accent.ORANGE());
            return false;
        }

        ItemEditorDataManager data = ItemEditorDataManager.INSTANCE;
        List<String> exports = data.listExportFiles();
        if (exports.isEmpty()) {
            showStatus("No exports found", UIConstants.Accent.ORANGE());
            return false;
        }

        String fileName = exports.get(exports.size() - 1);
        try {
            ItemEditorDataManager.ItemConfigExport imported = data.importFromFile(fileName);
            applyImportedStats(imported, "Imported " + fileName, activeModule);
            data.addHistoryEntry("import", item.getHoverName().getString(), fileName);
            showStatus("Imported " + fileName, UIConstants.Accent.BLUE());

            int applied = DatapackIO.importOverrides(DEFAULT_DATAPACK_NAME);
            if (applied > 0) {
                showStatus("Imported datapack overrides (" + applied + ")", UIConstants.Accent.BLUE());
            }
            return true;
        } catch (Exception e) {
            showStatus("Import failed: " + e.getMessage(), UIConstants.Accent.RED());
            return false;
        }
    }

    /**
     * Apply imported stats to the active module.
     */
    public void applyImportedStats(ItemEditorDataManager.ItemConfigExport config, String reason, EditorModule activeModule) {
        if (config == null || config.stats == null) {
            showStatus("Import file missing stats", UIConstants.Accent.RED());
            return;
        }

        if (activeModule instanceof WeaponModule weaponModule) {
            WeaponStats newStats = weaponModule.getStats().copy();
            List<Float> values = config.stats;
            newStats.headMult = getStat(values, 0, newStats.headMult);
            newStats.bodyMult = getStat(values, 1, newStats.bodyMult);
            newStats.armsMult = getStat(values, 2, newStats.armsMult);
            newStats.legsMult = getStat(values, 3, newStats.legsMult);
            newStats.attackDamage = getStat(values, 4, newStats.attackDamage);
            newStats.attackSpeed = getStat(values, 5, newStats.attackSpeed);
            newStats.attackReach = getStat(values, 6, newStats.attackReach);
            newStats.attackKnockback = getStat(values, 7, newStats.attackKnockback);
            newStats.armorPenetration = getStat(values, 8, newStats.armorPenetration);
            newStats.baseDamageBonus = getStat(values, 9, newStats.baseDamageBonus);
            newStats.critChance = getStat(values, 10, newStats.critChance);
            newStats.critDamage = getStat(values, 11, newStats.critDamage);
            newStats.lifesteal = getStat(values, 12, newStats.lifesteal);
            newStats.fireDamageBonus = getStat(values, 13, newStats.fireDamageBonus);
            newStats.magicDamageBonus = getStat(values, 14, newStats.magicDamageBonus);
            newStats.damageBonus = getStat(values, 15, newStats.damageBonus);
            newStats.armorShred = getStat(values, 16, newStats.armorShred);
            newStats.damageVsUndead = getStat(values, 17, newStats.damageVsUndead);
            newStats.damageVsArthropods = getStat(values, 18, newStats.damageVsArthropods);
            newStats.damageVsPlayers = getStat(values, 19, newStats.damageVsPlayers);
            newStats.trueDamagePercent = getStat(values, 20, newStats.trueDamagePercent);
            weaponModule.applyExternalStats(newStats, reason);
            weaponModule.applyPreview();
        } else if (activeModule instanceof ArmorModule armorModule) {
            ArmorStats newStats = armorModule.getStats().copy();
            List<Float> values = config.stats;
            newStats.physicalReduction = getStat(values, 0, newStats.physicalReduction);
            newStats.fireReduction = getStat(values, 1, newStats.fireReduction);
            newStats.magicReduction = getStat(values, 2, newStats.magicReduction);
            newStats.explosionReduction = getStat(values, 3, newStats.explosionReduction);
            newStats.projectileReduction = getStat(values, 4, newStats.projectileReduction);
            newStats.armorBonus = getStat(values, 5, newStats.armorBonus);
            newStats.toughnessBonus = getStat(values, 6, newStats.toughnessBonus);
            newStats.knockbackResistance = getStat(values, 7, newStats.knockbackResistance);
            newStats.thornsPercent = getStat(values, 8, newStats.thornsPercent);
            newStats.thornsReflect = getStat(values, 9, newStats.thornsReflect ? 1f : 0f) > 0.5f;
            armorModule.applyExternalStats(newStats, reason);
            armorModule.applyPreview();
        } else {
            showStatus("Unsupported editor for import", UIConstants.Accent.RED());
        }
    }

    // =========================================================================
    // PRESET OPERATIONS
    // =========================================================================

    /**
     * Apply a preset to the active module.
     *
     * @param preset       The preset to apply
     * @param activeModule The active editor module
     * @return The preset name if applied successfully, null otherwise
     */
    public String applyPreset(ItemEditorDataManager.PresetData preset, EditorModule activeModule) {
        if (preset == null) {
            showStatus("Preset not found", UIConstants.Accent.RED());
            return null;
        }

        ItemEditorDataManager.ItemConfigExport wrapper = new ItemEditorDataManager.ItemConfigExport();
        wrapper.stats = preset.statValues;
        applyImportedStats(wrapper, "Preset: " + preset.name, activeModule);
        return preset.name;
    }

    /**
     * Delete a preset.
     *
     * @param presetName The name of the preset to delete
     * @param itemName   The item name for history
     */
    public void deletePreset(String presetName, String itemName) {
        if (presetName == null) return;

        ItemEditorDataManager.INSTANCE.deletePreset(presetName);
        ItemEditorDataManager.INSTANCE.addHistoryEntry("preset_delete", itemName, presetName);
        DevMod.LOGGER.info("[DataOps] Preset deleted: {}", presetName);
        showStatus("Preset deleted: " + presetName, UIConstants.Accent.BLUE());

        notifyPresetListChanged();
    }

    /**
     * Rename a preset.
     *
     * @param preset   The preset to rename
     * @param newName  The new name
     * @param itemName The item name for history
     * @return The new name if successful, null otherwise
     */
    public String renamePreset(ItemEditorDataManager.PresetData preset, String newName, String itemName) {
        if (preset == null || preset.name == null || newName == null || newName.isBlank()) {
            return null;
        }

        String oldName = preset.name;
        preset.name = newName.trim();

        ItemEditorDataManager.INSTANCE.deletePreset(oldName);
        ItemEditorDataManager.INSTANCE.savePreset(preset);
        ItemEditorDataManager.INSTANCE.addHistoryEntry("preset_rename", itemName, oldName + " -> " + newName);
        DevMod.LOGGER.info("[DataOps] Preset renamed: {} -> {}", oldName, newName);
        showStatus("Preset renamed: " + newName, UIConstants.Accent.BLUE());

        notifyPresetListChanged();
        return newName;
    }

    /**
     * Save current module state as a new preset.
     *
     * @param item         The current item
     * @param activeModule The active editor module
     * @param isGlobalMode Whether in global mode
     * @return The preset name if saved successfully, null otherwise
     */
    public String saveCurrentAsPreset(ItemStack item, EditorModule activeModule, boolean isGlobalMode) {
        String itemType = getActiveItemType(activeModule);
        String presetName = buildPresetName(getItemId(item), itemType);
        ItemEditorDataManager.PresetData preset = buildPresetFromCurrent(presetName, itemType, activeModule, isGlobalMode);

        ItemEditorDataManager.INSTANCE.savePreset(preset);
        ItemEditorDataManager.INSTANCE.addHistoryEntry("preset_save", item.getHoverName().getString(), presetName);
        DevMod.LOGGER.info("[DataOps] Preset saved: {}", presetName);
        showStatus("Preset saved: " + presetName, UIConstants.Accent.GREEN());

        notifyPresetListChanged();
        return presetName;
    }

    /**
     * Build a preset from current module state.
     */
    public ItemEditorDataManager.PresetData buildPresetFromCurrent(String name, String itemType,
                                                                    EditorModule activeModule, boolean isGlobalMode) {
        ItemEditorDataManager.PresetData preset = new ItemEditorDataManager.PresetData(name);
        preset.itemType = itemType;
        preset.scope = isGlobalMode ? "GLOBAL" : "SPECIFIC";
        preset.devmodVersion = getDevModVersion();
        preset.statValues = collectStatsForExport(activeModule);
        return preset;
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    /**
     * Check if data operations are supported for the active module.
     */
    public boolean supportsDataOps(EditorModule activeModule) {
        return activeModule instanceof WeaponModule || activeModule instanceof ArmorModule;
    }

    /**
     * Get the item type string for the active module.
     */
    public String getActiveItemType(EditorModule activeModule) {
        if (activeModule instanceof WeaponModule) return "weapon";
        if (activeModule instanceof ArmorModule) return "armor";
        return "item";
    }

    /**
     * Get the item ID as a string.
     */
    public String getItemId(ItemStack item) {
        var key = BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(item.getItem()));
        return key == null ? "unknown" : key.toString();
    }

    private String buildSafeFileName(String raw) {
        return raw.replace(":", "_").replace("/", "_");
    }

    private String buildPresetName(String itemId, String itemType) {
        String base = buildSafeFileName(itemId);
        return base + "_preset_" + itemType + "_" + System.currentTimeMillis();
    }

    private String getDevModVersion() {
        return ModList.get()
            .getModContainerById(DevMod.MODID)
            .map(container -> container.getModInfo().getVersion().toString())
            .orElse("unknown");
    }

    private float getStat(List<Float> values, int index, float fallback) {
        if (values == null || index < 0 || index >= values.size()) {
            return fallback;
        }
        Float value = values.get(index);
        return value == null ? fallback : value;
    }

    private void showStatus(String message, int color) {
        if (statusConsumer != null) {
            statusConsumer.accept(message, color);
        }
    }

    private void notifyPresetListChanged() {
        if (onPresetListChanged != null) {
            onPresetListChanged.run();
        }
    }
}
