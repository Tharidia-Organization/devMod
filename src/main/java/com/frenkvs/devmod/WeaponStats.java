package com.frenkvs.devmod;

import net.minecraft.nbt.CompoundTag;

/**
 * Weapon statistics for damage calculation.
 * Applied to attacker during combat.
 *
 * Based on EDITOR_DESIGN_SYSTEM.md Section 2.23 and related.
 */
public class WeaponStats {
    // ═══════════════════════════════════════════════════════════════
    // HIT LOCATION MULTIPLIERS
    // ═══════════════════════════════════════════════════════════════

    public float headMult = getDefaultHeadMult();
    public float bodyMult = getDefaultBodyMult();
    public float armsMult = getDefaultArmsMult();
    public float legsMult = getDefaultLegsMult();

    // ═══════════════════════════════════════════════════════════════
    // CORE COMBAT STATS
    // ═══════════════════════════════════════════════════════════════

    public float armorPenetration = 0.0f;   // Percentage of armor ignored (0.0 - 1.0)
    public float baseDamageBonus = 0.0f;    // Flat damage added
    public float attackDamage = 0.0f;       // Base attack damage override (0 = use item default)
    public float attackSpeed = 0.0f;        // Attack speed override (0 = use item default)
    public float attackReach = 0.0f;        // Attack reach override (0 = use item default)
    public float attackKnockback = 0.0f;    // Additional knockback

    // ═══════════════════════════════════════════════════════════════
    // CRITICAL HIT
    // ═══════════════════════════════════════════════════════════════

    public float critChance = 0.0f;         // Critical hit chance (0.0 - 1.0)
    public float critDamage = 1.5f;         // Critical damage multiplier

    // ═══════════════════════════════════════════════════════════════
    // DAMAGE TYPE BONUSES
    // ═══════════════════════════════════════════════════════════════

    public float fireDamageBonus = 0.0f;    // Bonus fire damage per hit
    public float magicDamageBonus = 0.0f;   // Bonus magic damage per hit
    public float lifesteal = 0.0f;          // Heal percentage of damage dealt (0.0 - 0.5)

    // Config-driven defaults with safe fallback
    private static float getDefaultHeadMult() {
        try { return Config.HEAD_DAMAGE_MULTIPLIER.get().floatValue(); }
        catch (Throwable e) { return 1.5f; }
    }

    private static float getDefaultBodyMult() {
        try { return Config.BODY_DAMAGE_MULTIPLIER.get().floatValue(); }
        catch (Throwable e) { return 1.0f; }
    }

    private static float getDefaultArmsMult() {
        try { return Config.ARMS_DAMAGE_MULTIPLIER.get().floatValue(); }
        catch (Throwable e) { return 0.8f; }
    }

    private static float getDefaultLegsMult() {
        try { return Config.LEGS_DAMAGE_MULTIPLIER.get().floatValue(); }
        catch (Throwable e) { return 0.7f; }
    }

    // ═══════════════════════════════════════════════════════════════
    // SERIALIZATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Save weapon stats to NBT compound tag.
     * Uses short keys to minimize storage overhead.
     */
    public void save(CompoundTag tag) {
        // Hit location multipliers
        tag.putFloat("HeadMult", headMult);
        tag.putFloat("BodyMult", bodyMult);
        tag.putFloat("ArmsMult", armsMult);
        tag.putFloat("LegsMult", legsMult);

        // Core stats (only save non-default values)
        if (armorPenetration != 0.0f) tag.putFloat("ArmorPen", armorPenetration);
        if (baseDamageBonus != 0.0f) tag.putFloat("BaseDmg", baseDamageBonus);
        if (attackDamage != 0.0f) tag.putFloat("AtkDmg", attackDamage);
        if (attackSpeed != 0.0f) tag.putFloat("AtkSpd", attackSpeed);
        if (attackReach != 0.0f) tag.putFloat("AtkRch", attackReach);
        if (attackKnockback != 0.0f) tag.putFloat("AtkKB", attackKnockback);

        // Critical hit
        if (critChance != 0.0f) tag.putFloat("CritCh", critChance);
        if (critDamage != 1.5f) tag.putFloat("CritDmg", critDamage);

        // Damage type bonuses
        if (fireDamageBonus != 0.0f) tag.putFloat("FireDmg", fireDamageBonus);
        if (magicDamageBonus != 0.0f) tag.putFloat("MagicDmg", magicDamageBonus);
        if (lifesteal != 0.0f) tag.putFloat("Lifesteal", lifesteal);
    }

    /**
     * Load weapon stats from NBT compound tag.
     */
    public static WeaponStats load(CompoundTag tag) {
        WeaponStats stats = new WeaponStats();

        // Hit location multipliers
        if (tag.contains("HeadMult")) stats.headMult = tag.getFloat("HeadMult");
        if (tag.contains("BodyMult")) stats.bodyMult = tag.getFloat("BodyMult");
        if (tag.contains("ArmsMult")) stats.armsMult = tag.getFloat("ArmsMult");
        if (tag.contains("LegsMult")) stats.legsMult = tag.getFloat("LegsMult");

        // Core stats
        if (tag.contains("ArmorPen")) stats.armorPenetration = tag.getFloat("ArmorPen");
        if (tag.contains("BaseDmg")) stats.baseDamageBonus = tag.getFloat("BaseDmg");
        if (tag.contains("AtkDmg")) stats.attackDamage = tag.getFloat("AtkDmg");
        if (tag.contains("AtkSpd")) stats.attackSpeed = tag.getFloat("AtkSpd");
        if (tag.contains("AtkRch")) stats.attackReach = tag.getFloat("AtkRch");
        if (tag.contains("AtkKB")) stats.attackKnockback = tag.getFloat("AtkKB");

        // Critical hit
        if (tag.contains("CritCh")) stats.critChance = tag.getFloat("CritCh");
        if (tag.contains("CritDmg")) stats.critDamage = tag.getFloat("CritDmg");

        // Damage type bonuses
        if (tag.contains("FireDmg")) stats.fireDamageBonus = tag.getFloat("FireDmg");
        if (tag.contains("MagicDmg")) stats.magicDamageBonus = tag.getFloat("MagicDmg");
        if (tag.contains("Lifesteal")) stats.lifesteal = tag.getFloat("Lifesteal");

        return stats;
    }

    // ═══════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Calculate DPS based on damage and attack speed.
     */
    public float calculateDPS(float baseItemDamage) {
        float damage = attackDamage > 0 ? attackDamage : baseItemDamage;
        float speed = attackSpeed > 0 ? attackSpeed : 1.6f;
        return damage * speed;
    }

    /**
     * Check if all values are at defaults (no modifications).
     */
    public boolean isDefault() {
        return headMult == getDefaultHeadMult()
            && bodyMult == getDefaultBodyMult()
            && armsMult == getDefaultArmsMult()
            && legsMult == getDefaultLegsMult()
            && armorPenetration == 0.0f
            && baseDamageBonus == 0.0f
            && attackDamage == 0.0f
            && attackSpeed == 0.0f
            && attackReach == 0.0f
            && attackKnockback == 0.0f
            && critChance == 0.0f
            && critDamage == 1.5f
            && fireDamageBonus == 0.0f
            && magicDamageBonus == 0.0f
            && lifesteal == 0.0f;
    }

    /**
     * Create a copy of these stats.
     */
    public WeaponStats copy() {
        WeaponStats copy = new WeaponStats();
        copy.headMult = this.headMult;
        copy.bodyMult = this.bodyMult;
        copy.armsMult = this.armsMult;
        copy.legsMult = this.legsMult;
        copy.armorPenetration = this.armorPenetration;
        copy.baseDamageBonus = this.baseDamageBonus;
        copy.attackDamage = this.attackDamage;
        copy.attackSpeed = this.attackSpeed;
        copy.attackReach = this.attackReach;
        copy.attackKnockback = this.attackKnockback;
        copy.critChance = this.critChance;
        copy.critDamage = this.critDamage;
        copy.fireDamageBonus = this.fireDamageBonus;
        copy.magicDamageBonus = this.magicDamageBonus;
        copy.lifesteal = this.lifesteal;
        return copy;
    }

    @Override
    public String toString() {
        return "WeaponStats{" +
            "head=" + headMult +
            ", body=" + bodyMult +
            ", arms=" + armsMult +
            ", legs=" + legsMult +
            ", atkDmg=" + attackDamage +
            ", atkSpd=" + attackSpeed +
            ", armorPen=" + armorPenetration +
            ", crit=" + critChance + "x" + critDamage +
            '}';
    }
}
