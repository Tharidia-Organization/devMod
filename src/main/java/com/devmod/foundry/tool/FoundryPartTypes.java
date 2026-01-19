package com.devmod.foundry.tool;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;

/**
 * Built-in part types for foundry tools.
 */
public final class FoundryPartTypes {
    private FoundryPartTypes() {}

    private static final Map<ResourceLocation, FoundryPartType> REGISTRY = new LinkedHashMap<>();

    public static final FoundryPartType TOOL_HEAD = register("tool_head", "head", 1);
    public static final FoundryPartType TOOL_HANDLE = register("tool_handle", "handle", 1);
    public static final FoundryPartType TOOL_BINDING = register("tool_binding", "binding", 1);
    public static final FoundryPartType PICKAXE_HEAD = register("pickaxe_head", "head", 1);
    public static final FoundryPartType AXE_HEAD = register("axe_head", "head", 1);
    public static final FoundryPartType SHOVEL_HEAD = register("shovel_head", "head", 1);
    public static final FoundryPartType SWORD_BLADE = register("sword_blade", "head", 1);
    public static final FoundryPartType HOE_HEAD = register("hoe_head", "head", 1);
    public static final FoundryPartType HAMMER_HEAD = register("hammer_head", "head", 2);
    public static final FoundryPartType EXCAVATOR_HEAD = register("excavator_head", "head", 2);
    public static final FoundryPartType SCYTHE_HEAD = register("scythe_head", "head", 2);
    public static final FoundryPartType DAGGER_BLADE = register("dagger_blade", "head", 1);
    public static final FoundryPartType SPEAR_HEAD = register("spear_head", "head", 1);
    public static final FoundryPartType CLEAVER_BLADE = register("cleaver_blade", "head", 2);
    public static final FoundryPartType LONGSWORD_BLADE = register("longsword_blade", "head", 1);
    public static final FoundryPartType BATTLEAXE_HEAD = register("battleaxe_head", "head", 2);
    public static final FoundryPartType MATTOCK_HEAD = register("mattock_head", "head", 2);
    public static final FoundryPartType KAMA_HEAD = register("kama_head", "head", 1);
    public static final FoundryPartType BOW_LIMB = register("bow_limb", "head", 1);
    public static final FoundryPartType BOWSTRING = register("bowstring", "binding", 1);
    public static final FoundryPartType CROSSBOW_STOCK = register("crossbow_stock", "handle", 2);
    public static final FoundryPartType SHIELD_CORE = register("shield_core", "head", 2);
    public static final FoundryPartType SHIELD_PLATING = register("shield_plating", "plate", 2);
    public static final FoundryPartType WRENCH_HEAD = register("wrench_head", "head", 2);
    public static final FoundryPartType ARMOR_PLATE = register("armor_plate", "plate", 2);
    public static final FoundryPartType ARMOR_MAIL = register("armor_mail", "mail", 2);
    public static final FoundryPartType ARMOR_TRIM = register("armor_trim", "trim", 1);
    public static final FoundryPartType TOOL_GUARD = register("tool_guard", "extra", 1);
    public static final FoundryPartType LARGE_HEAD = register("large_head", "head", 3);
    public static final FoundryPartType TOUGH_HANDLE = register("tough_handle", "handle", 2);

    private static FoundryPartType register(String id, String statKey, int cost) {
        ResourceLocation key = ResourceLocation.fromNamespaceAndPath(DevMod.MODID, id);
        FoundryPartType type = new FoundryPartType(key, statKey, cost);
        REGISTRY.put(key, type);
        return type;
    }

    public static FoundryPartType get(ResourceLocation id) {
        return Objects.requireNonNull(REGISTRY.get(id));
    }

    public static Map<ResourceLocation, FoundryPartType> all() {
        return Map.copyOf(REGISTRY);
    }
}
