package com.frenkvs.devmod;

import net.minecraft.nbt.CompoundTag;

/**
 * Armor statistics for damage reduction.
 * Applied to victim during damage calculations.
 *
 * Unlike WeaponStats (attacker-side: body part multipliers, armor penetration),
 * ArmorStats are victim-side: damage reduction by damage type.
 */
public class ArmorStats {

    // Damage type reductions (0.0 = no reduction, 1.0 = 100% reduction)
    // Values are capped at 0.8 total when applied to prevent invincibility
    public float physicalReduction = 0.0f;    // Melee attacks, default physical damage
    public float fireReduction = 0.0f;        // Fire, lava, burning
    public float magicReduction = 0.0f;       // Magic, wither, dragon breath, sonic boom
    public float explosionReduction = 0.0f;   // Explosions, creepers, TNT, fireworks
    public float projectileReduction = 0.0f;  // Arrows, tridents (stacks with physical)

    // Vanilla stat modifiers (additive to existing values)
    public float armorBonus = 0.0f;           // Added to armor value (0-30 recommended)
    public float toughnessBonus = 0.0f;       // Added to armor toughness (0-20 recommended)
    public float knockbackResistance = 0.0f;  // 0.0 - 1.0 (added to existing)

    // Special effects
    public boolean thornsReflect = false;     // Enable thorns damage reflection
    public float thornsPercent = 0.0f;        // How much damage to reflect (0.0 - 0.5)

    // Shield-specific tuning
    public boolean shieldReflectProjectiles = false; // Reflect projectiles while blocking
    public float shieldBlockStrength = 0.5f;         // 0-1 strength multiplier
    public float shieldRecoverySpeed = 1.0f;         // Recovery speed multiplier

    /**
     * Save armor stats to NBT compound tag.
     * Uses short keys to minimize storage overhead.
     */
    public void save(CompoundTag tag) {
        // Only save non-default values to reduce NBT size
        if (physicalReduction != 0.0f) tag.putFloat("PhysRed", physicalReduction);
        if (fireReduction != 0.0f) tag.putFloat("FireRed", fireReduction);
        if (magicReduction != 0.0f) tag.putFloat("MagicRed", magicReduction);
        if (explosionReduction != 0.0f) tag.putFloat("ExplRed", explosionReduction);
        if (projectileReduction != 0.0f) tag.putFloat("ProjRed", projectileReduction);
        if (armorBonus != 0.0f) tag.putFloat("ArmorBon", armorBonus);
        if (toughnessBonus != 0.0f) tag.putFloat("ToughBon", toughnessBonus);
        if (knockbackResistance != 0.0f) tag.putFloat("KBRes", knockbackResistance);
        if (thornsReflect) tag.putBoolean("Thorns", true);
        if (thornsPercent != 0.0f) tag.putFloat("ThornsPct", thornsPercent);
        if (shieldReflectProjectiles) tag.putBoolean("ShieldReflect", true);
        if (Float.compare(shieldBlockStrength, 0.5f) != 0) tag.putFloat("ShieldBlock", shieldBlockStrength);
        if (Float.compare(shieldRecoverySpeed, 1.0f) != 0) tag.putFloat("ShieldRecovery", shieldRecoverySpeed);
    }

    /**
     * Load armor stats from NBT compound tag.
     */
    public static ArmorStats load(CompoundTag tag) {
        ArmorStats stats = new ArmorStats();
        if (tag.contains("PhysRed")) stats.physicalReduction = tag.getFloat("PhysRed");
        if (tag.contains("FireRed")) stats.fireReduction = tag.getFloat("FireRed");
        if (tag.contains("MagicRed")) stats.magicReduction = tag.getFloat("MagicRed");
        if (tag.contains("ExplRed")) stats.explosionReduction = tag.getFloat("ExplRed");
        if (tag.contains("ProjRed")) stats.projectileReduction = tag.getFloat("ProjRed");
        if (tag.contains("ArmorBon")) stats.armorBonus = tag.getFloat("ArmorBon");
        if (tag.contains("ToughBon")) stats.toughnessBonus = tag.getFloat("ToughBon");
        if (tag.contains("KBRes")) stats.knockbackResistance = tag.getFloat("KBRes");
        if (tag.contains("Thorns")) stats.thornsReflect = tag.getBoolean("Thorns");
        if (tag.contains("ThornsPct")) stats.thornsPercent = tag.getFloat("ThornsPct");
        stats.shieldReflectProjectiles = tag.contains("ShieldReflect") && tag.getBoolean("ShieldReflect");
        stats.shieldBlockStrength = tag.contains("ShieldBlock") ? tag.getFloat("ShieldBlock") : 0.5f;
        stats.shieldRecoverySpeed = tag.contains("ShieldRecovery") ? tag.getFloat("ShieldRecovery") : 1.0f;
        return stats;
    }

    /**
     * Check if all values are at defaults (no modifications).
     */
    public boolean isDefault() {
        return physicalReduction == 0.0f
            && fireReduction == 0.0f
            && magicReduction == 0.0f
            && explosionReduction == 0.0f
            && projectileReduction == 0.0f
            && armorBonus == 0.0f
            && toughnessBonus == 0.0f
            && knockbackResistance == 0.0f
            && !thornsReflect
            && thornsPercent == 0.0f
            && !shieldReflectProjectiles
            && Float.compare(shieldBlockStrength, 0.5f) == 0
            && Float.compare(shieldRecoverySpeed, 1.0f) == 0;
    }

    /**
     * Get total damage reduction for a specific damage category.
     * Note: projectile damage applies both physical AND projectile reduction.
     *
     * @param isPhysical true for melee/physical damage
     * @param isFire true for fire/lava damage
     * @param isMagic true for magic/wither damage
     * @param isExplosion true for explosion damage
     * @param isProjectile true for arrow/trident damage
     * @return total reduction percentage (0.0 - 1.0)
     */
    public float getReductionFor(boolean isPhysical, boolean isFire, boolean isMagic,
                                  boolean isExplosion, boolean isProjectile) {
        float reduction = 0.0f;

        if (isPhysical) reduction += physicalReduction;
        if (isFire) reduction += fireReduction;
        if (isMagic) reduction += magicReduction;
        if (isExplosion) reduction += explosionReduction;
        if (isProjectile) reduction += projectileReduction;

        return reduction;
    }

    /**
     * Create a copy of these stats.
     */
    public ArmorStats copy() {
        ArmorStats copy = new ArmorStats();
        copy.physicalReduction = this.physicalReduction;
        copy.fireReduction = this.fireReduction;
        copy.magicReduction = this.magicReduction;
        copy.explosionReduction = this.explosionReduction;
        copy.projectileReduction = this.projectileReduction;
        copy.armorBonus = this.armorBonus;
        copy.toughnessBonus = this.toughnessBonus;
        copy.knockbackResistance = this.knockbackResistance;
        copy.thornsReflect = this.thornsReflect;
        copy.thornsPercent = this.thornsPercent;
        copy.shieldReflectProjectiles = this.shieldReflectProjectiles;
        copy.shieldBlockStrength = this.shieldBlockStrength;
        copy.shieldRecoverySpeed = this.shieldRecoverySpeed;
        return copy;
    }

    @Override
    public String toString() {
        return "ArmorStats{" +
            "phys=" + physicalReduction +
            ", fire=" + fireReduction +
            ", magic=" + magicReduction +
            ", expl=" + explosionReduction +
            ", proj=" + projectileReduction +
            ", armor=" + armorBonus +
            ", tough=" + toughnessBonus +
            ", kb=" + knockbackResistance +
            ", thorns=" + thornsReflect + "/" + thornsPercent +
            ", shield=" + shieldBlockStrength + "/" + shieldRecoverySpeed + "/" + shieldReflectProjectiles +
            '}';
    }
}
