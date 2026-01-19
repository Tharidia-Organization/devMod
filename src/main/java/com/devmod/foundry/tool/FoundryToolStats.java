package com.devmod.foundry.tool;

import com.google.gson.JsonObject;

import net.minecraft.util.GsonHelper;

/**
 * Aggregated tool stats.
 */
public record FoundryToolStats(
    int durability,
    float miningSpeed,
    float attackDamage,
    float attackSpeed,
    int miningLevel,
    float armor,
    float toughness,
    float knockbackResistance
) {
    public static FoundryToolStats fromJson(JsonObject obj) {
        int durability = GsonHelper.getAsInt(obj, "durability", 0);
        float miningSpeed = GsonHelper.getAsFloat(obj, "mining_speed", 1.0f);
        float attackDamage = GsonHelper.getAsFloat(obj, "attack_damage", 1.0f);
        float attackSpeed = GsonHelper.getAsFloat(obj, "attack_speed", -2.8f);
        int miningLevel = GsonHelper.getAsInt(obj, "mining_level", 0);
        float armor = GsonHelper.getAsFloat(obj, "armor", 0.0f);
        float toughness = GsonHelper.getAsFloat(obj, "toughness", 0.0f);
        float knockbackResistance = GsonHelper.getAsFloat(obj, "knockback_resistance", 0.0f);
        return new FoundryToolStats(durability, miningSpeed, attackDamage, attackSpeed, miningLevel, armor, toughness, knockbackResistance);
    }
}
