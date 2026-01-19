package com.devmod.foundry.tool;

import com.google.gson.JsonObject;

import net.minecraft.util.GsonHelper;

/**
 * Aggregated tool stats.
 *
 * Core combat stats:
 * - durability, miningSpeed, attackDamage, attackSpeed, miningLevel
 *
 * Armor stats:
 * - armor, toughness, knockbackResistance
 *
 * Extended stats:
 * - reach: Attack range bonus (blocks)
 * - critChance: Critical hit chance (0.0-1.0)
 * - critDamage: Critical hit damage multiplier
 * - drawSpeed: Bow/crossbow draw speed bonus
 * - projectileSpeed: Ranged projectile velocity multiplier
 */
public record FoundryToolStats(
    int durability,
    float miningSpeed,
    float attackDamage,
    float attackSpeed,
    int miningLevel,
    float armor,
    float toughness,
    float knockbackResistance,
    // Extended stats
    float reach,
    float critChance,
    float critDamage,
    float drawSpeed,
    float projectileSpeed
) {
    /**
     * Creates stats with default extended values (for backwards compatibility).
     */
    public static FoundryToolStats basic(int durability, float miningSpeed, float attackDamage,
                                          float attackSpeed, int miningLevel, float armor,
                                          float toughness, float knockbackResistance) {
        return new FoundryToolStats(durability, miningSpeed, attackDamage, attackSpeed, miningLevel,
            armor, toughness, knockbackResistance, 0.0f, 0.0f, 1.5f, 0.0f, 1.0f);
    }

    public static FoundryToolStats fromJson(JsonObject obj) {
        int durability = GsonHelper.getAsInt(obj, "durability", 0);
        float miningSpeed = GsonHelper.getAsFloat(obj, "mining_speed", 1.0f);
        float attackDamage = GsonHelper.getAsFloat(obj, "attack_damage", 1.0f);
        float attackSpeed = GsonHelper.getAsFloat(obj, "attack_speed", -2.8f);
        int miningLevel = GsonHelper.getAsInt(obj, "mining_level", 0);
        float armor = GsonHelper.getAsFloat(obj, "armor", 0.0f);
        float toughness = GsonHelper.getAsFloat(obj, "toughness", 0.0f);
        float knockbackResistance = GsonHelper.getAsFloat(obj, "knockback_resistance", 0.0f);
        // Extended stats
        float reach = GsonHelper.getAsFloat(obj, "reach", 0.0f);
        float critChance = GsonHelper.getAsFloat(obj, "crit_chance", 0.0f);
        float critDamage = GsonHelper.getAsFloat(obj, "crit_damage", 1.5f);
        float drawSpeed = GsonHelper.getAsFloat(obj, "draw_speed", 0.0f);
        float projectileSpeed = GsonHelper.getAsFloat(obj, "projectile_speed", 1.0f);
        return new FoundryToolStats(durability, miningSpeed, attackDamage, attackSpeed, miningLevel,
            armor, toughness, knockbackResistance, reach, critChance, critDamage, drawSpeed, projectileSpeed);
    }
}
