package com.devmod.foundry.tool.material;

import com.google.gson.JsonObject;

import net.minecraft.util.GsonHelper;

/**
 * Stats contributed by a material for a specific part category.
 *
 * Core stats (base values):
 * - durability: Base durability points
 * - miningSpeed: Base mining speed
 * - attackDamage: Base attack damage
 * - attackSpeed: Attack speed modifier (-4 to +4)
 * - miningLevel: Mining tier level (0=wood, 1=stone, 2=iron, 3=diamond, 4=netherite)
 * - armor: Base armor points
 * - toughness: Armor toughness
 * - knockbackResistance: Knockback resistance (0.0-1.0)
 *
 * Multiplier stats (applied to base values):
 * - durabilityMultiplier: Multiplies final durability
 * - miningSpeedMultiplier: Multiplies final mining speed
 * - attackDamageMultiplier: Multiplies final attack damage
 *
 * Part-specific stats:
 * - hardness: Material hardness for heads (affects break resistance)
 * - flexibility: Handle/mail flexibility (affects durability on impact)
 * - connectionStrength: Binding/trim connection strength (affects part bonding)
 * - drawSpeed: Bow/crossbow draw speed modifier
 * - projectileSpeed: Ranged weapon projectile velocity
 * - reach: Weapon reach modifier
 * - critChance: Critical hit chance bonus (0.0-1.0)
 * - critDamage: Critical hit damage multiplier
 */
public record FoundryMaterialStats(
    // Core stats
    int durability,
    float miningSpeed,
    float attackDamage,
    float attackSpeed,
    int miningLevel,
    float armor,
    float toughness,
    float knockbackResistance,
    // Multiplier stats
    float durabilityMultiplier,
    float miningSpeedMultiplier,
    float attackDamageMultiplier,
    // Part-specific stats
    float hardness,
    float flexibility,
    float connectionStrength,
    float drawSpeed,
    float projectileSpeed,
    float reach,
    float critChance,
    float critDamage
) {
    public static final FoundryMaterialStats EMPTY = new FoundryMaterialStats(
        0, 0.0f, 0.0f, 0.0f, 0, 0.0f, 0.0f, 0.0f,
        1.0f, 1.0f, 1.0f,
        0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.5f
    );

    public static FoundryMaterialStats fromJson(JsonObject obj) {
        // Core stats
        int durability = GsonHelper.getAsInt(obj, "durability", 0);
        float miningSpeed = GsonHelper.getAsFloat(obj, "mining_speed", 0.0f);
        float attackDamage = GsonHelper.getAsFloat(obj, "attack_damage", 0.0f);
        float attackSpeed = GsonHelper.getAsFloat(obj, "attack_speed", 0.0f);
        int miningLevel = GsonHelper.getAsInt(obj, "mining_level", 0);
        float armor = GsonHelper.getAsFloat(obj, "armor", 0.0f);
        float toughness = GsonHelper.getAsFloat(obj, "toughness", 0.0f);
        float knockbackResistance = GsonHelper.getAsFloat(obj, "knockback_resistance", 0.0f);

        // Multiplier stats
        float durabilityMultiplier = GsonHelper.getAsFloat(obj, "durability_multiplier", 1.0f);
        float miningSpeedMultiplier = GsonHelper.getAsFloat(obj, "mining_speed_multiplier", 1.0f);
        float attackDamageMultiplier = GsonHelper.getAsFloat(obj, "attack_damage_multiplier", 1.0f);

        // Part-specific stats
        float hardness = GsonHelper.getAsFloat(obj, "hardness", 0.0f);
        float flexibility = GsonHelper.getAsFloat(obj, "flexibility", 0.0f);
        float connectionStrength = GsonHelper.getAsFloat(obj, "connection_strength", 0.0f);
        float drawSpeed = GsonHelper.getAsFloat(obj, "draw_speed", 0.0f);
        float projectileSpeed = GsonHelper.getAsFloat(obj, "projectile_speed", 0.0f);
        float reach = GsonHelper.getAsFloat(obj, "reach", 0.0f);
        float critChance = GsonHelper.getAsFloat(obj, "crit_chance", 0.0f);
        float critDamage = GsonHelper.getAsFloat(obj, "crit_damage", 1.5f);

        return new FoundryMaterialStats(
            durability,
            miningSpeed,
            attackDamage,
            attackSpeed,
            miningLevel,
            armor,
            toughness,
            knockbackResistance,
            durabilityMultiplier,
            miningSpeedMultiplier,
            attackDamageMultiplier,
            hardness,
            flexibility,
            connectionStrength,
            drawSpeed,
            projectileSpeed,
            reach,
            critChance,
            critDamage
        );
    }

    /**
     * Combines two stats, adding additive values and multiplying multipliers.
     */
    public FoundryMaterialStats combine(FoundryMaterialStats other) {
        return new FoundryMaterialStats(
            this.durability + other.durability,
            this.miningSpeed + other.miningSpeed,
            this.attackDamage + other.attackDamage,
            this.attackSpeed + other.attackSpeed,
            Math.max(this.miningLevel, other.miningLevel),
            this.armor + other.armor,
            this.toughness + other.toughness,
            Math.min(1.0f, this.knockbackResistance + other.knockbackResistance),
            this.durabilityMultiplier * other.durabilityMultiplier,
            this.miningSpeedMultiplier * other.miningSpeedMultiplier,
            this.attackDamageMultiplier * other.attackDamageMultiplier,
            Math.max(this.hardness, other.hardness),
            (this.flexibility + other.flexibility) / 2f, // Average flexibility
            Math.max(this.connectionStrength, other.connectionStrength),
            this.drawSpeed + other.drawSpeed,
            (this.projectileSpeed + other.projectileSpeed) / 2f, // Average projectile speed
            this.reach + other.reach,
            Math.min(1.0f, this.critChance + other.critChance),
            Math.max(this.critDamage, other.critDamage)
        );
    }

    /**
     * Scales all stats by a multiplier.
     */
    public FoundryMaterialStats scale(float factor) {
        return new FoundryMaterialStats(
            (int) (this.durability * factor),
            this.miningSpeed * factor,
            this.attackDamage * factor,
            this.attackSpeed * factor,
            this.miningLevel,
            this.armor * factor,
            this.toughness * factor,
            this.knockbackResistance * factor,
            this.durabilityMultiplier,
            this.miningSpeedMultiplier,
            this.attackDamageMultiplier,
            this.hardness * factor,
            this.flexibility,
            this.connectionStrength * factor,
            this.drawSpeed * factor,
            this.projectileSpeed,
            this.reach * factor,
            this.critChance * factor,
            this.critDamage
        );
    }
}
