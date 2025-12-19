package com.frenkvs.devmod.ui.editor;

import com.frenkvs.devmod.ArmorStats;
import com.frenkvs.devmod.DevMod;
import com.frenkvs.devmod.ItemEditorDataManager;
import com.frenkvs.devmod.WeaponStats;
import com.frenkvs.devmod.integration.PufferfishCompat;
import com.frenkvs.devmod.ui.editor.core.EditorCache;
import com.frenkvs.devmod.ui.editor.core.UIConstants;
import com.frenkvs.devmod.ui.editor.modules.ArmorModule;
import com.frenkvs.devmod.ui.editor.modules.WeaponModule;
import com.frenkvs.devmod.util.DatapackIO;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Handles data operations for ItemEditorScreen: export, import, presets, templates.
 * Extracted for single responsibility.
 */

public class ItemEditorDataOps {

    private static final String DEFAULT_DATAPACK_NAME = "devmod_balance_auto";

    /**
     * Export current item configuration to file.
     */
    public boolean handleExport(ItemStack item, EditorModule activeModule, BiConsumer<String, Integer> statusCallback) {
        if (!supportsDataOps(activeModule)) {
            statusCallback.accept("Export available only for weapons/armors", UIConstants.Accent.ORANGE());
            return false;
        }

        ItemEditorDataManager data = ItemEditorDataManager.INSTANCE;
        ItemEditorDataManager.ItemConfigExport config = new ItemEditorDataManager.ItemConfigExport();
        config.itemId = getCurrentItemId(item);
        config.itemName = item.getHoverName().getString();
        config.stats = collectStatsForExport(activeModule);
        config.enchantments = collectEnchantmentsForExport(item);
        config.attributes = collectAttributesForExport(item);
        config.durability = item.isDamageableItem() ? item.getMaxDamage() - item.getDamageValue() : null;
        config.unbreakable = item.has(Objects.requireNonNull(DataComponents.UNBREAKABLE));
        config.repairCost = item.has(Objects.requireNonNull(DataComponents.REPAIR_COST))
            ? item.get(DataComponents.REPAIR_COST) : null;

        String fileName = buildSafeFileName(config.itemId + "_export_" + System.currentTimeMillis());
        boolean ok = data.exportToFile(config, fileName);
        if (ok) {
            data.addHistoryEntry("export", config.itemName, fileName);
            statusCallback.accept("Exported to " + fileName + ".json", UIConstants.Accent.GREEN());
            int exported = DatapackIO.exportOverrides(DEFAULT_DATAPACK_NAME);
            if (exported > 0) {
                statusCallback.accept("Datapack: " + DEFAULT_DATAPACK_NAME + " (" + exported + " items)", UIConstants.Accent.BLUE());
            }
            return true;
        } else {
            statusCallback.accept("Export failed", UIConstants.Accent.RED());
            return false;
        }
    }

    /**
     * Import configuration from most recent export file.
     */
    public boolean handleImport(ItemStack item, EditorModule activeModule, BiConsumer<String, Integer> statusCallback) {
        if (!supportsDataOps(activeModule)) {
            statusCallback.accept("Import available only for weapons/armors", UIConstants.Accent.ORANGE());
            return false;
        }

        ItemEditorDataManager data = ItemEditorDataManager.INSTANCE;
        List<String> exports = data.listExportFiles();
        if (exports.isEmpty()) {
            statusCallback.accept("No exports found", UIConstants.Accent.ORANGE());
            return false;
        }

        String fileName = exports.get(exports.size() - 1);
        try {
            ItemEditorDataManager.ItemConfigExport imported = data.importFromFile(fileName);
            applyImportedStats(imported, "Imported " + fileName, activeModule, statusCallback);
            data.addHistoryEntry("import", item.getHoverName().getString(), fileName);
            statusCallback.accept("Imported " + fileName, UIConstants.Accent.BLUE());
            int applied = DatapackIO.importOverrides(DEFAULT_DATAPACK_NAME);
            if (applied > 0) {
                statusCallback.accept("Imported datapack overrides (" + applied + ")", UIConstants.Accent.BLUE());
            }
            return true;
        } catch (Exception e) {
            statusCallback.accept("Import failed: " + e.getMessage(), UIConstants.Accent.RED());
            return false;
        }
    }

    /**
     * Apply a preset to the current module.
     */
    public void applyPreset(ItemEditorDataManager.PresetData preset, EditorModule activeModule,
            BiConsumer<String, Integer> statusCallback) {
        if (preset == null) {
            statusCallback.accept("Preset not found", UIConstants.Accent.RED());
            return;
        }
        ItemEditorDataManager.ItemConfigExport wrapper = new ItemEditorDataManager.ItemConfigExport();
        wrapper.stats = preset.statValues;
        applyImportedStats(wrapper, "Preset: " + preset.name, activeModule, statusCallback);
    }

    /**
     * Save current configuration as a new preset.
     */
    public String saveCurrentAsPreset(ItemStack item, EditorModule activeModule, boolean isGlobalMode,
            BiConsumer<String, Integer> statusCallback) {
        String itemType = getActiveItemType(activeModule);
        String presetName = buildPresetName(getCurrentItemId(item), itemType);
        ItemEditorDataManager.PresetData preset = buildPresetFromCurrent(presetName, itemType,
            collectStatsForExport(activeModule), isGlobalMode);
        ItemEditorDataManager.INSTANCE.savePreset(preset);
        ItemEditorDataManager.INSTANCE.addHistoryEntry("preset_save", item.getHoverName().getString(), presetName);
        DevMod.LOGGER.info("[Editor] Preset saved: {}", presetName);
        statusCallback.accept("Preset saved: " + presetName, UIConstants.Accent.GREEN());
        return presetName;
    }

    /**
     * Delete a preset.
     */
    public void deletePreset(ItemEditorDataManager.PresetData preset, ItemStack item,
            BiConsumer<String, Integer> statusCallback) {
        if (preset == null || preset.name == null) return;
        ItemEditorDataManager.INSTANCE.deletePreset(preset.name);
        ItemEditorDataManager.INSTANCE.addHistoryEntry("preset_delete", item.getHoverName().getString(), preset.name);
        DevMod.LOGGER.info("[Editor] Preset deleted: {}", preset.name);
        statusCallback.accept("Preset deleted: " + preset.name, UIConstants.Accent.BLUE());
    }

    /**
     * Rename a preset.
     */
    public void renamePreset(ItemEditorDataManager.PresetData preset, String newName, ItemStack item,
            BiConsumer<String, Integer> statusCallback) {
        if (preset == null || preset.name == null || newName == null || newName.isBlank()) return;
        String oldName = preset.name;
        preset.name = newName.trim();
        ItemEditorDataManager.INSTANCE.deletePreset(oldName);
        ItemEditorDataManager.INSTANCE.savePreset(preset);
        ItemEditorDataManager.INSTANCE.addHistoryEntry("preset_rename", item.getHoverName().getString(),
            oldName + " -> " + newName);
        DevMod.LOGGER.info("[Editor] Preset renamed: {} -> {}", oldName, newName);
        statusCallback.accept("Preset renamed: " + newName, UIConstants.Accent.BLUE());
    }

    /**
     * Apply a template to an item.
     */
    public void applyTemplate(ItemEditorDataManager.TemplateData template, ItemStack item,
            EditorModule activeModule, boolean isPreviewMode, BiConsumer<String, Integer> statusCallback) {
        if (template == null) return;

        ItemStack target = isPreviewMode ? item.copy() : item;

        // Enchantments
        if (template.enchantments != null && !template.enchantments.isEmpty()) {
            applyTemplateEnchantments(template, target);
        }

        // Attributes
        if (template.attributes != null && !template.attributes.isEmpty()) {
            applyTemplateAttributes(template, target);
        }

        if (activeModule != null) {
            activeModule.setItem(target);
            activeModule.applyPreview();
            if (!isPreviewMode) {
                activeModule.markDirty("Applied template: " + template.name);
            }
        }

        String name = template.name == null ? "template" : template.name;
        ItemEditorDataManager.INSTANCE.addHistoryEntry("template_apply", item.getHoverName().getString(), name);
        statusCallback.accept("Applied template " + name, UIConstants.Accent.GREEN());
        EditorCache.INSTANCE.invalidateItem(getCurrentItemId(item));
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════

    public boolean supportsDataOps(EditorModule activeModule) {
        return activeModule instanceof WeaponModule || activeModule instanceof ArmorModule;
    }

    public String getActiveItemType(EditorModule activeModule) {
        if (activeModule instanceof WeaponModule) return "weapon";
        if (activeModule instanceof ArmorModule) return "armor";
        return "item";
    }

    public String getCurrentItemId(ItemStack item) {
        var key = BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(item.getItem()));
        return key == null ? "unknown" : key.toString();
    }

    public String detectTemplateCategory(ItemStack item) {
        String itemId = getCurrentItemId(item);
        return ItemEditorDataManager.INSTANCE.suggestTemplate(itemId)
            .map(t -> t.itemCategory)
            .orElse(null);
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

    private List<Float> collectStatsForExport(EditorModule activeModule) {
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

    private List<ItemEditorDataManager.EnchantData> collectEnchantmentsForExport(ItemStack item) {
        List<ItemEditorDataManager.EnchantData> result = new ArrayList<>();
        ItemEnchantments enchants = item.getOrDefault(
            Objects.requireNonNull(DataComponents.ENCHANTMENTS), ItemEnchantments.EMPTY);

        Minecraft mc = Minecraft.getInstance();
        Level level = mc != null ? mc.level : null;
        if (level == null) return result;

        var registry = level.registryAccess().registryOrThrow(Objects.requireNonNull(Registries.ENCHANTMENT));
        enchants.entrySet().forEach(entry -> {
            Holder<Enchantment> holder = entry.getKey();
            int enchantLevel = entry.getIntValue();
            var key = registry.getKey(Objects.requireNonNull(holder.value()));
            if (key != null) {
                result.add(new ItemEditorDataManager.EnchantData(key.toString(), enchantLevel));
            }
        });
        return result;
    }

    private List<ItemEditorDataManager.AttrData> collectAttributesForExport(ItemStack item) {
        List<ItemEditorDataManager.AttrData> result = new ArrayList<>();
        ItemAttributeModifiers modifiers = item.getOrDefault(
            Objects.requireNonNull(DataComponents.ATTRIBUTE_MODIFIERS), ItemAttributeModifiers.EMPTY);

        Objects.requireNonNull(modifiers.modifiers()).forEach(entry -> {
            Holder<Attribute> attr = entry.attribute();
            AttributeModifier mod = entry.modifier();
            var key = BuiltInRegistries.ATTRIBUTE.getKey(Objects.requireNonNull(attr.value()));
            if (key != null) {
                result.add(new ItemEditorDataManager.AttrData(key.toString(), mod.amount(), mod.operation().ordinal()));
            }
        });
        return result;
    }

    private ItemEditorDataManager.PresetData buildPresetFromCurrent(String name, String itemType,
            List<Float> stats, boolean isGlobalMode) {
        ItemEditorDataManager.PresetData preset = new ItemEditorDataManager.PresetData(name);
        preset.itemType = itemType;
        preset.scope = isGlobalMode ? "GLOBAL" : "SPECIFIC";
        preset.devmodVersion = getDevModVersion();
        preset.statValues = stats;
        return preset;
    }

    private void applyImportedStats(ItemEditorDataManager.ItemConfigExport config, String reason,
            EditorModule activeModule, BiConsumer<String, Integer> statusCallback) {
        if (config == null || config.stats == null) {
            statusCallback.accept("Import file missing stats", UIConstants.Accent.RED());
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
            statusCallback.accept("Unsupported editor for import", UIConstants.Accent.RED());
        }
    }

    private void applyTemplateEnchantments(ItemEditorDataManager.TemplateData template, ItemStack target) {
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        var mc = Minecraft.getInstance();
        var level = mc != null ? mc.level : null;
        var enchantmentRegistry = level != null
            ? level.registryAccess().registryOrThrow(Objects.requireNonNull(Registries.ENCHANTMENT))
            : null;

        for (ItemEditorDataManager.EnchantData ench : template.enchantments) {
            if (ench == null || ench.id == null) continue;
            ResourceLocation id = ResourceLocation.tryParse(ench.id);
            if (id == null) continue;
            ResourceKey<Enchantment> key = ResourceKey.create(Objects.requireNonNull(Registries.ENCHANTMENT), id);
            Holder<Enchantment> holder = enchantmentRegistry != null
                ? enchantmentRegistry.getHolder(key).orElse(null)
                : null;
            if (holder != null) {
                mutable.set(holder, ench.level);
            }
        }
        target.set(Objects.requireNonNull(DataComponents.ENCHANTMENTS), mutable.toImmutable());
    }

    private void applyTemplateAttributes(ItemEditorDataManager.TemplateData template, ItemStack target) {
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();

        for (ItemEditorDataManager.AttrData attr : template.attributes) {
            if (attr == null || attr.id == null) continue;
            ResourceLocation id = ResourceLocation.tryParse(attr.id);
            if (id == null) continue;
            ResourceKey<Attribute> key = ResourceKey.create(Objects.requireNonNull(Registries.ATTRIBUTE), id);
            Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.getHolder(key).orElse(null);
            if (holder == null) {
                holder = PufferfishCompat.map(id, BuiltInRegistries.ATTRIBUTE);
            }
            if (holder == null) continue;

            AttributeModifier.Operation op = switch (attr.operation) {
                case 1 -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
                case 2 -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
                default -> AttributeModifier.Operation.ADD_VALUE;
            };
            ResourceLocation modifierId = ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "template_" + id.getPath());
            AttributeModifier modifier = new AttributeModifier(modifierId, attr.value, op);
            builder.add(holder, modifier, EquipmentSlotGroup.ANY);
        }
        target.set(Objects.requireNonNull(DataComponents.ATTRIBUTE_MODIFIERS), builder.build());
    }

    private float getStat(List<Float> values, int index, float fallback) {
        if (values == null || index < 0 || index >= values.size()) {
            return fallback;
        }
        Float value = values.get(index);
        return value == null ? fallback : value;
    }
}
