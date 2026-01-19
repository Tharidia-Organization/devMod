package com.devmod.foundry.tool.modifier;

import com.google.gson.JsonObject;

import net.minecraft.util.GsonHelper;

/**
 * Stat bonuses per modifier level.
 */
public record FoundryModifierStats(
    int durability,
    float miningSpeed,
    float attackDamage,
    float attackSpeed,
    float armor,
    float toughness,
    float knockbackResistance
) {
    public static final FoundryModifierStats EMPTY = new FoundryModifierStats(0, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);

    public static FoundryModifierStats fromJson(JsonObject obj) {
        int durability = GsonHelper.getAsInt(obj, "durability", 0);
        float miningSpeed = GsonHelper.getAsFloat(obj, "mining_speed", 0.0f);
        float attackDamage = GsonHelper.getAsFloat(obj, "attack_damage", 0.0f);
        float attackSpeed = GsonHelper.getAsFloat(obj, "attack_speed", 0.0f);
        float armor = GsonHelper.getAsFloat(obj, "armor", 0.0f);
        float toughness = GsonHelper.getAsFloat(obj, "toughness", 0.0f);
        float knockbackResistance = GsonHelper.getAsFloat(obj, "knockback_resistance", 0.0f);
        return new FoundryModifierStats(durability, miningSpeed, attackDamage, attackSpeed, armor, toughness, knockbackResistance);
    }
}
