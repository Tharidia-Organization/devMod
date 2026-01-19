package com.devmod.foundry.tool.material;

import com.google.gson.JsonObject;

import net.minecraft.util.GsonHelper;

/**
 * Stats contributed by a material for a specific part category.
 */
public record FoundryMaterialStats(
    int durability,
    float miningSpeed,
    float attackDamage,
    float attackSpeed,
    float durabilityMultiplier,
    float miningSpeedMultiplier,
    float attackDamageMultiplier,
    int miningLevel
) {
    public static final FoundryMaterialStats EMPTY = new FoundryMaterialStats(
        0, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0
    );

    public static FoundryMaterialStats fromJson(JsonObject obj) {
        int durability = GsonHelper.getAsInt(obj, "durability", 0);
        float miningSpeed = GsonHelper.getAsFloat(obj, "mining_speed", 0.0f);
        float attackDamage = GsonHelper.getAsFloat(obj, "attack_damage", 0.0f);
        float attackSpeed = GsonHelper.getAsFloat(obj, "attack_speed", 0.0f);
        float durabilityMultiplier = GsonHelper.getAsFloat(obj, "durability_multiplier", 1.0f);
        float miningSpeedMultiplier = GsonHelper.getAsFloat(obj, "mining_speed_multiplier", 1.0f);
        float attackDamageMultiplier = GsonHelper.getAsFloat(obj, "attack_damage_multiplier", 1.0f);
        int miningLevel = GsonHelper.getAsInt(obj, "mining_level", 0);
        return new FoundryMaterialStats(
            durability,
            miningSpeed,
            attackDamage,
            attackSpeed,
            durabilityMultiplier,
            miningSpeedMultiplier,
            attackDamageMultiplier,
            miningLevel
        );
    }
}
