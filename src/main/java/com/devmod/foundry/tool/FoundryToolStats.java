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
    int miningLevel
) {
    public static FoundryToolStats fromJson(JsonObject obj) {
        int durability = GsonHelper.getAsInt(obj, "durability", 0);
        float miningSpeed = GsonHelper.getAsFloat(obj, "mining_speed", 1.0f);
        float attackDamage = GsonHelper.getAsFloat(obj, "attack_damage", 1.0f);
        float attackSpeed = GsonHelper.getAsFloat(obj, "attack_speed", -2.8f);
        int miningLevel = GsonHelper.getAsInt(obj, "mining_level", 0);
        return new FoundryToolStats(durability, miningSpeed, attackDamage, attackSpeed, miningLevel);
    }
}
