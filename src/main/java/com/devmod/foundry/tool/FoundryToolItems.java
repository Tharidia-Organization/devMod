package com.devmod.foundry.tool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;

import net.neoforged.neoforge.registries.DeferredHolder;

import com.devmod.DevMod;

/**
 * Tooling item registrations for the foundry module.
 */
public final class FoundryToolItems {
    private FoundryToolItems() {}

    private static final Map<FoundryPartType, DeferredHolder<Item, FoundryPatternItem>> PATTERN_ITEMS = new LinkedHashMap<>();
    private static final Map<FoundryPartType, DeferredHolder<Item, FoundryPartItem>> PART_ITEMS = new LinkedHashMap<>();
    private static final List<DeferredHolder<Item, ? extends Item>> TOOL_ITEMS = new ArrayList<>();
    private static final List<DeferredHolder<Item, ? extends Item>> ARMOR_ITEMS = new ArrayList<>();

    public static final DeferredHolder<Item, FoundryPatternItem> PATTERN_TOOL_HEAD =
        registerPattern("foundry_pattern_tool_head", FoundryPartTypes.TOOL_HEAD);
    public static final DeferredHolder<Item, FoundryPatternItem> PATTERN_TOOL_HANDLE =
        registerPattern("foundry_pattern_tool_handle", FoundryPartTypes.TOOL_HANDLE);
    public static final DeferredHolder<Item, FoundryPatternItem> PATTERN_TOOL_BINDING =
        registerPattern("foundry_pattern_tool_binding", FoundryPartTypes.TOOL_BINDING);
    public static final DeferredHolder<Item, FoundryPatternItem> PATTERN_PICKAXE_HEAD =
        registerPattern("foundry_pattern_pickaxe_head", FoundryPartTypes.PICKAXE_HEAD);
    public static final DeferredHolder<Item, FoundryPatternItem> PATTERN_AXE_HEAD =
        registerPattern("foundry_pattern_axe_head", FoundryPartTypes.AXE_HEAD);
    public static final DeferredHolder<Item, FoundryPatternItem> PATTERN_SHOVEL_HEAD =
        registerPattern("foundry_pattern_shovel_head", FoundryPartTypes.SHOVEL_HEAD);
    public static final DeferredHolder<Item, FoundryPatternItem> PATTERN_SWORD_BLADE =
        registerPattern("foundry_pattern_sword_blade", FoundryPartTypes.SWORD_BLADE);
    public static final DeferredHolder<Item, FoundryPatternItem> PATTERN_HOE_HEAD =
        registerPattern("foundry_pattern_hoe_head", FoundryPartTypes.HOE_HEAD);
    public static final DeferredHolder<Item, FoundryPatternItem> PATTERN_HAMMER_HEAD =
        registerPattern("foundry_pattern_hammer_head", FoundryPartTypes.HAMMER_HEAD);
    public static final DeferredHolder<Item, FoundryPatternItem> PATTERN_EXCAVATOR_HEAD =
        registerPattern("foundry_pattern_excavator_head", FoundryPartTypes.EXCAVATOR_HEAD);
    public static final DeferredHolder<Item, FoundryPatternItem> PATTERN_SCYTHE_HEAD =
        registerPattern("foundry_pattern_scythe_head", FoundryPartTypes.SCYTHE_HEAD);
    public static final DeferredHolder<Item, FoundryPatternItem> PATTERN_DAGGER_BLADE =
        registerPattern("foundry_pattern_dagger_blade", FoundryPartTypes.DAGGER_BLADE);
    public static final DeferredHolder<Item, FoundryPatternItem> PATTERN_SPEAR_HEAD =
        registerPattern("foundry_pattern_spear_head", FoundryPartTypes.SPEAR_HEAD);
    public static final DeferredHolder<Item, FoundryPatternItem> PATTERN_CLEAVER_BLADE =
        registerPattern("foundry_pattern_cleaver_blade", FoundryPartTypes.CLEAVER_BLADE);
    public static final DeferredHolder<Item, FoundryPatternItem> PATTERN_LONGSWORD_BLADE =
        registerPattern("foundry_pattern_longsword_blade", FoundryPartTypes.LONGSWORD_BLADE);
    public static final DeferredHolder<Item, FoundryPatternItem> PATTERN_BATTLEAXE_HEAD =
        registerPattern("foundry_pattern_battleaxe_head", FoundryPartTypes.BATTLEAXE_HEAD);
    public static final DeferredHolder<Item, FoundryPatternItem> PATTERN_MATTOCK_HEAD =
        registerPattern("foundry_pattern_mattock_head", FoundryPartTypes.MATTOCK_HEAD);
    public static final DeferredHolder<Item, FoundryPatternItem> PATTERN_KAMA_HEAD =
        registerPattern("foundry_pattern_kama_head", FoundryPartTypes.KAMA_HEAD);
    public static final DeferredHolder<Item, FoundryPatternItem> PATTERN_ARMOR_PLATE =
        registerPattern("foundry_pattern_armor_plate", FoundryPartTypes.ARMOR_PLATE);
    public static final DeferredHolder<Item, FoundryPatternItem> PATTERN_ARMOR_MAIL =
        registerPattern("foundry_pattern_armor_mail", FoundryPartTypes.ARMOR_MAIL);
    public static final DeferredHolder<Item, FoundryPatternItem> PATTERN_ARMOR_TRIM =
        registerPattern("foundry_pattern_armor_trim", FoundryPartTypes.ARMOR_TRIM);
    public static final DeferredHolder<Item, FoundryPatternItem> PATTERN_BOW_LIMB =
        registerPattern("foundry_pattern_bow_limb", FoundryPartTypes.BOW_LIMB);
    public static final DeferredHolder<Item, FoundryPatternItem> PATTERN_BOWSTRING =
        registerPattern("foundry_pattern_bowstring", FoundryPartTypes.BOWSTRING);
    public static final DeferredHolder<Item, FoundryPatternItem> PATTERN_CROSSBOW_STOCK =
        registerPattern("foundry_pattern_crossbow_stock", FoundryPartTypes.CROSSBOW_STOCK);
    public static final DeferredHolder<Item, FoundryPatternItem> PATTERN_SHIELD_CORE =
        registerPattern("foundry_pattern_shield_core", FoundryPartTypes.SHIELD_CORE);
    public static final DeferredHolder<Item, FoundryPatternItem> PATTERN_SHIELD_PLATING =
        registerPattern("foundry_pattern_shield_plating", FoundryPartTypes.SHIELD_PLATING);
    public static final DeferredHolder<Item, FoundryPatternItem> PATTERN_WRENCH_HEAD =
        registerPattern("foundry_pattern_wrench_head", FoundryPartTypes.WRENCH_HEAD);
    public static final DeferredHolder<Item, FoundryPatternItem> PATTERN_TOOL_GUARD =
        registerPattern("foundry_pattern_tool_guard", FoundryPartTypes.TOOL_GUARD);
    public static final DeferredHolder<Item, FoundryPatternItem> PATTERN_LARGE_HEAD =
        registerPattern("foundry_pattern_large_head", FoundryPartTypes.LARGE_HEAD);
    public static final DeferredHolder<Item, FoundryPatternItem> PATTERN_TOUGH_HANDLE =
        registerPattern("foundry_pattern_tough_handle", FoundryPartTypes.TOUGH_HANDLE);

    public static final DeferredHolder<Item, FoundryPartItem> PART_TOOL_HEAD =
        registerPart("foundry_tool_head", FoundryPartTypes.TOOL_HEAD);
    public static final DeferredHolder<Item, FoundryPartItem> PART_TOOL_HANDLE =
        registerPart("foundry_tool_handle", FoundryPartTypes.TOOL_HANDLE);
    public static final DeferredHolder<Item, FoundryPartItem> PART_TOOL_BINDING =
        registerPart("foundry_tool_binding", FoundryPartTypes.TOOL_BINDING);
    public static final DeferredHolder<Item, FoundryPartItem> PART_PICKAXE_HEAD =
        registerPart("foundry_pickaxe_head", FoundryPartTypes.PICKAXE_HEAD);
    public static final DeferredHolder<Item, FoundryPartItem> PART_AXE_HEAD =
        registerPart("foundry_axe_head", FoundryPartTypes.AXE_HEAD);
    public static final DeferredHolder<Item, FoundryPartItem> PART_SHOVEL_HEAD =
        registerPart("foundry_shovel_head", FoundryPartTypes.SHOVEL_HEAD);
    public static final DeferredHolder<Item, FoundryPartItem> PART_SWORD_BLADE =
        registerPart("foundry_sword_blade", FoundryPartTypes.SWORD_BLADE);
    public static final DeferredHolder<Item, FoundryPartItem> PART_HOE_HEAD =
        registerPart("foundry_hoe_head", FoundryPartTypes.HOE_HEAD);
    public static final DeferredHolder<Item, FoundryPartItem> PART_HAMMER_HEAD =
        registerPart("foundry_hammer_head", FoundryPartTypes.HAMMER_HEAD);
    public static final DeferredHolder<Item, FoundryPartItem> PART_EXCAVATOR_HEAD =
        registerPart("foundry_excavator_head", FoundryPartTypes.EXCAVATOR_HEAD);
    public static final DeferredHolder<Item, FoundryPartItem> PART_SCYTHE_HEAD =
        registerPart("foundry_scythe_head", FoundryPartTypes.SCYTHE_HEAD);
    public static final DeferredHolder<Item, FoundryPartItem> PART_DAGGER_BLADE =
        registerPart("foundry_dagger_blade", FoundryPartTypes.DAGGER_BLADE);
    public static final DeferredHolder<Item, FoundryPartItem> PART_SPEAR_HEAD =
        registerPart("foundry_spear_head", FoundryPartTypes.SPEAR_HEAD);
    public static final DeferredHolder<Item, FoundryPartItem> PART_CLEAVER_BLADE =
        registerPart("foundry_cleaver_blade", FoundryPartTypes.CLEAVER_BLADE);
    public static final DeferredHolder<Item, FoundryPartItem> PART_LONGSWORD_BLADE =
        registerPart("foundry_longsword_blade", FoundryPartTypes.LONGSWORD_BLADE);
    public static final DeferredHolder<Item, FoundryPartItem> PART_BATTLEAXE_HEAD =
        registerPart("foundry_battleaxe_head", FoundryPartTypes.BATTLEAXE_HEAD);
    public static final DeferredHolder<Item, FoundryPartItem> PART_MATTOCK_HEAD =
        registerPart("foundry_mattock_head", FoundryPartTypes.MATTOCK_HEAD);
    public static final DeferredHolder<Item, FoundryPartItem> PART_KAMA_HEAD =
        registerPart("foundry_kama_head", FoundryPartTypes.KAMA_HEAD);
    public static final DeferredHolder<Item, FoundryPartItem> PART_ARMOR_PLATE =
        registerPart("foundry_armor_plate", FoundryPartTypes.ARMOR_PLATE);
    public static final DeferredHolder<Item, FoundryPartItem> PART_ARMOR_MAIL =
        registerPart("foundry_armor_mail", FoundryPartTypes.ARMOR_MAIL);
    public static final DeferredHolder<Item, FoundryPartItem> PART_ARMOR_TRIM =
        registerPart("foundry_armor_trim", FoundryPartTypes.ARMOR_TRIM);
    public static final DeferredHolder<Item, FoundryPartItem> PART_BOW_LIMB =
        registerPart("foundry_bow_limb", FoundryPartTypes.BOW_LIMB);
    public static final DeferredHolder<Item, FoundryPartItem> PART_BOWSTRING =
        registerPart("foundry_bowstring", FoundryPartTypes.BOWSTRING);
    public static final DeferredHolder<Item, FoundryPartItem> PART_CROSSBOW_STOCK =
        registerPart("foundry_crossbow_stock", FoundryPartTypes.CROSSBOW_STOCK);
    public static final DeferredHolder<Item, FoundryPartItem> PART_SHIELD_CORE =
        registerPart("foundry_shield_core", FoundryPartTypes.SHIELD_CORE);
    public static final DeferredHolder<Item, FoundryPartItem> PART_SHIELD_PLATING =
        registerPart("foundry_shield_plating", FoundryPartTypes.SHIELD_PLATING);
    public static final DeferredHolder<Item, FoundryPartItem> PART_WRENCH_HEAD =
        registerPart("foundry_wrench_head", FoundryPartTypes.WRENCH_HEAD);
    public static final DeferredHolder<Item, FoundryPartItem> PART_TOOL_GUARD =
        registerPart("foundry_tool_guard", FoundryPartTypes.TOOL_GUARD);
    public static final DeferredHolder<Item, FoundryPartItem> PART_LARGE_HEAD =
        registerPart("foundry_large_head", FoundryPartTypes.LARGE_HEAD);
    public static final DeferredHolder<Item, FoundryPartItem> PART_TOUGH_HANDLE =
        registerPart("foundry_tough_handle", FoundryPartTypes.TOUGH_HANDLE);

    public static final DeferredHolder<Item, FoundryToolItem> FOUNDRY_PICKAXE =
        registerTool("foundry_pickaxe", () -> new FoundryToolItem(FoundryToolKind.PICKAXE));
    public static final DeferredHolder<Item, FoundryToolItem> FOUNDRY_AXE =
        registerTool("foundry_axe", () -> new FoundryToolItem(FoundryToolKind.AXE));
    public static final DeferredHolder<Item, FoundryToolItem> FOUNDRY_SHOVEL =
        registerTool("foundry_shovel", () -> new FoundryToolItem(FoundryToolKind.SHOVEL));
    public static final DeferredHolder<Item, FoundryToolItem> FOUNDRY_SWORD =
        registerTool("foundry_sword", () -> new FoundryToolItem(FoundryToolKind.SWORD));
    public static final DeferredHolder<Item, FoundryToolItem> FOUNDRY_HOE =
        registerTool("foundry_hoe", () -> new FoundryToolItem(FoundryToolKind.HOE));
    public static final DeferredHolder<Item, FoundryToolItem> FOUNDRY_HAMMER =
        registerTool("foundry_hammer", () -> new FoundryToolItem(FoundryToolKind.HAMMER));
    public static final DeferredHolder<Item, FoundryToolItem> FOUNDRY_EXCAVATOR =
        registerTool("foundry_excavator", () -> new FoundryToolItem(FoundryToolKind.EXCAVATOR));
    public static final DeferredHolder<Item, FoundryToolItem> FOUNDRY_SCYTHE =
        registerTool("foundry_scythe", () -> new FoundryToolItem(FoundryToolKind.SCYTHE));
    public static final DeferredHolder<Item, FoundryToolItem> FOUNDRY_DAGGER =
        registerTool("foundry_dagger", () -> new FoundryToolItem(FoundryToolKind.DAGGER));
    public static final DeferredHolder<Item, FoundryToolItem> FOUNDRY_SPEAR =
        registerTool("foundry_spear", () -> new FoundryToolItem(FoundryToolKind.SPEAR));
    public static final DeferredHolder<Item, FoundryToolItem> FOUNDRY_CLEAVER =
        registerTool("foundry_cleaver", () -> new FoundryToolItem(FoundryToolKind.CLEAVER));
    public static final DeferredHolder<Item, FoundryToolItem> FOUNDRY_LONGSWORD =
        registerTool("foundry_longsword", () -> new FoundryToolItem(FoundryToolKind.LONGSWORD));
    public static final DeferredHolder<Item, FoundryToolItem> FOUNDRY_BATTLEAXE =
        registerTool("foundry_battleaxe", () -> new FoundryToolItem(FoundryToolKind.BATTLEAXE));
    public static final DeferredHolder<Item, FoundryToolItem> FOUNDRY_MATTOCK =
        registerTool("foundry_mattock", () -> new FoundryToolItem(FoundryToolKind.MATTOCK));
    public static final DeferredHolder<Item, FoundryToolItem> FOUNDRY_KAMA =
        registerTool("foundry_kama", () -> new FoundryToolItem(FoundryToolKind.KAMA));
    public static final DeferredHolder<Item, FoundryBowItem> FOUNDRY_BOW =
        registerTool("foundry_bow", () -> new FoundryBowItem(FoundryToolKind.BOW));
    public static final DeferredHolder<Item, FoundryCrossbowItem> FOUNDRY_CROSSBOW =
        registerTool("foundry_crossbow", () -> new FoundryCrossbowItem(FoundryToolKind.CROSSBOW));
    public static final DeferredHolder<Item, FoundryBowItem> FOUNDRY_LONGBOW =
        registerTool("foundry_longbow", () -> new FoundryBowItem(FoundryToolKind.LONGBOW));
    public static final DeferredHolder<Item, FoundryShieldItem> FOUNDRY_SHIELD =
        registerTool("foundry_shield", () -> new FoundryShieldItem(FoundryToolKind.SHIELD));
    public static final DeferredHolder<Item, FoundryToolItem> FOUNDRY_WRENCH =
        registerTool("foundry_wrench", () -> new FoundryToolItem(FoundryToolKind.WRENCH));
    public static final DeferredHolder<Item, FoundryFishingRodItem> FOUNDRY_FISHING_ROD =
        registerTool("foundry_fishing_rod", () -> new FoundryFishingRodItem(FoundryToolKind.FISHING_ROD));
    public static final DeferredHolder<Item, FoundryStaffItem> FOUNDRY_STAFF =
        registerTool("foundry_staff", () -> new FoundryStaffItem(FoundryToolKind.STAFF));

    public static final DeferredHolder<Item, FoundryArmorItem> FOUNDRY_HELMET =
        registerArmor("foundry_helmet", ArmorItem.Type.HELMET);
    public static final DeferredHolder<Item, FoundryArmorItem> FOUNDRY_CHESTPLATE =
        registerArmor("foundry_chestplate", ArmorItem.Type.CHESTPLATE);
    public static final DeferredHolder<Item, FoundryArmorItem> FOUNDRY_LEGGINGS =
        registerArmor("foundry_leggings", ArmorItem.Type.LEGGINGS);
    public static final DeferredHolder<Item, FoundryArmorItem> FOUNDRY_BOOTS =
        registerArmor("foundry_boots", ArmorItem.Type.BOOTS);

    // Traveler armor (lightweight, more mobility)
    public static final DeferredHolder<Item, FoundryArmorItem> TRAVELER_HELMET =
        registerArmorWithMaterial("traveler_helmet", ArmorItem.Type.HELMET, ArmorMaterials.LEATHER);
    public static final DeferredHolder<Item, FoundryArmorItem> TRAVELER_CHESTPLATE =
        registerArmorWithMaterial("traveler_chestplate", ArmorItem.Type.CHESTPLATE, ArmorMaterials.LEATHER);
    public static final DeferredHolder<Item, FoundryArmorItem> TRAVELER_LEGGINGS =
        registerArmorWithMaterial("traveler_leggings", ArmorItem.Type.LEGGINGS, ArmorMaterials.LEATHER);
    public static final DeferredHolder<Item, FoundryArmorItem> TRAVELER_BOOTS =
        registerArmorWithMaterial("traveler_boots", ArmorItem.Type.BOOTS, ArmorMaterials.LEATHER);

    // Plate armor (heavy, high protection)
    public static final DeferredHolder<Item, FoundryArmorItem> PLATE_HELMET =
        registerArmorWithMaterial("plate_helmet", ArmorItem.Type.HELMET, ArmorMaterials.DIAMOND);
    public static final DeferredHolder<Item, FoundryArmorItem> PLATE_CHESTPLATE =
        registerArmorWithMaterial("plate_chestplate", ArmorItem.Type.CHESTPLATE, ArmorMaterials.DIAMOND);
    public static final DeferredHolder<Item, FoundryArmorItem> PLATE_LEGGINGS =
        registerArmorWithMaterial("plate_leggings", ArmorItem.Type.LEGGINGS, ArmorMaterials.DIAMOND);
    public static final DeferredHolder<Item, FoundryArmorItem> PLATE_BOOTS =
        registerArmorWithMaterial("plate_boots", ArmorItem.Type.BOOTS, ArmorMaterials.DIAMOND);

    // Slime armor (bouncy, fall damage reduction)
    public static final DeferredHolder<Item, FoundryArmorItem> SLIME_HELMET =
        registerArmorWithMaterial("slime_helmet", ArmorItem.Type.HELMET, ArmorMaterials.CHAIN);
    public static final DeferredHolder<Item, FoundryArmorItem> SLIME_CHESTPLATE =
        registerArmorWithMaterial("slime_chestplate", ArmorItem.Type.CHESTPLATE, ArmorMaterials.CHAIN);
    public static final DeferredHolder<Item, FoundryArmorItem> SLIME_LEGGINGS =
        registerArmorWithMaterial("slime_leggings", ArmorItem.Type.LEGGINGS, ArmorMaterials.CHAIN);
    public static final DeferredHolder<Item, FoundryArmorItem> SLIME_BOOTS =
        registerArmorWithMaterial("slime_boots", ArmorItem.Type.BOOTS, ArmorMaterials.CHAIN);

    public static void init() {
        // Static init only.
    }

    public static Collection<FoundryPatternItem> getPatternItems() {
        List<FoundryPatternItem> items = new ArrayList<>();
        for (DeferredHolder<Item, FoundryPatternItem> holder : PATTERN_ITEMS.values()) {
            items.add(Objects.requireNonNull(holder.get()));
        }
        return List.copyOf(items);
    }

    public static Collection<FoundryPartItem> getPartItems() {
        List<FoundryPartItem> items = new ArrayList<>();
        for (DeferredHolder<Item, FoundryPartItem> holder : PART_ITEMS.values()) {
            items.add(Objects.requireNonNull(holder.get()));
        }
        return List.copyOf(items);
    }

    public static Collection<Item> getToolItems() {
        List<Item> items = new ArrayList<>();
        for (DeferredHolder<Item, ? extends Item> holder : TOOL_ITEMS) {
            items.add(Objects.requireNonNull(holder.get()));
        }
        return List.copyOf(items);
    }

    public static Collection<Item> getArmorItems() {
        List<Item> items = new ArrayList<>();
        for (DeferredHolder<Item, ? extends Item> holder : ARMOR_ITEMS) {
            items.add(Objects.requireNonNull(holder.get()));
        }
        return List.copyOf(items);
    }

    @Nullable
    public static FoundryPartItem getPartItem(FoundryPartType partType) {
        DeferredHolder<Item, FoundryPartItem> holder = PART_ITEMS.get(partType);
        return holder != null ? Objects.requireNonNull(holder.get()) : null;
    }

    private static DeferredHolder<Item, FoundryPatternItem> registerPattern(String name, FoundryPartType partType) {
        DeferredHolder<Item, FoundryPatternItem> holder = DevMod.ITEMS.register(
            name,
            () -> new FoundryPatternItem(partType)
        );
        PATTERN_ITEMS.put(partType, holder);
        return holder;
    }

    private static DeferredHolder<Item, FoundryPartItem> registerPart(String name, FoundryPartType partType) {
        DeferredHolder<Item, FoundryPartItem> holder = DevMod.ITEMS.register(
            name,
            () -> new FoundryPartItem(partType)
        );
        PART_ITEMS.put(partType, holder);
        return holder;
    }

    private static <T extends Item> DeferredHolder<Item, T> registerTool(String name, Supplier<T> supplier) {
        DeferredHolder<Item, T> holder = DevMod.ITEMS.register(name, supplier);
        TOOL_ITEMS.add(holder);
        return holder;
    }

    private static DeferredHolder<Item, FoundryArmorItem> registerArmor(String name, ArmorItem.Type type) {
        DeferredHolder<Item, FoundryArmorItem> holder = DevMod.ITEMS.register(
            name,
            () -> new FoundryArmorItem(ArmorMaterials.IRON, type)
        );
        ARMOR_ITEMS.add(holder);
        return holder;
    }

    private static DeferredHolder<Item, FoundryArmorItem> registerArmorWithMaterial(
        String name,
        ArmorItem.Type type,
        net.minecraft.core.Holder<net.minecraft.world.item.ArmorMaterial> material
    ) {
        DeferredHolder<Item, FoundryArmorItem> holder = DevMod.ITEMS.register(
            name,
            () -> new FoundryArmorItem(material, type)
        );
        ARMOR_ITEMS.add(holder);
        return holder;
    }
}
