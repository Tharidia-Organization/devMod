package com.devmod.stats;

import net.minecraft.nbt.CompoundTag;

public class ArmorStats {

    // Damage type reductions (0.0 = no reduction, 1.0 = 100% reduction)
    // Values are capped at 0.8 total when applied to prevent invincibility
    private float physicalReduction = 0.0f;    // Melee attacks, default physical damage
    private float fireReduction = 0.0f;        // Fire, lava, burning
    private float magicReduction = 0.0f;       // Magic, wither, dragon breath, sonic boom
    private float explosionReduction = 0.0f;   // Explosions, creepers, TNT, fireworks
    private float projectileReduction = 0.0f;  // Arrows, tridents (stacks with physical)

    // Vanilla stat modifiers (additive to existing values)
    private float armorBonus = 0.0f;           // Added to armor value (0-30 recommended)
    private float toughnessBonus = 0.0f;       // Added to armor toughness (0-20 recommended)
    private float knockbackResistance = 0.0f;  // 0.0 - 1.0 (added to existing)

    // Special effects
    private boolean thornsReflect = false;     // Enable thorns damage reflection
    private float thornsPercent = 0.0f;        // How much damage to reflect (0.0 - 0.5)

    // Shield-specific tuning
    private boolean shieldReflectProjectiles = false; // Reflect projectiles while blocking
    private float shieldBlockStrength = 0.5f;         // 0-1 strength multiplier
    private float shieldRecoverySpeed = 1.0f;         // Recovery speed multiplier

    // === Shield Visual Settings (Prismatic Integration) ===
    private int shieldColor = ShieldColors.DEFAULT; // RGB color (Electric Blue)
    private float shieldOpacity = 0.6f;           // Base opacity (0.0-1.0)
    private boolean shieldGlowEnabled = true;     // Fresnel edge glow
    private float shieldGlowIntensity = 1.0f;     // Glow strength (0.0-2.0)
    private float shieldNoiseIntensity = 0.15f;   // Energy field noise (0.0-0.5)
    private float shieldPulseSpeed = 1.0f;        // Animation speed (0.5-2.0)

    // === Shield Deflection Settings ===
    private float shieldDeflectionSpread = 0.15f; // Max angular spread (radians)
    private boolean shieldDeflectToOwner = false; // Deflect back to shooter
    private float shieldDeflectSpeedMult = 0.8f;  // Speed after deflection (0.5-1.5)

    // === Shield Shatter Settings ===
    private float shieldShatterThreshold = 10.0f; // Damage threshold to trigger shatter
    private boolean shieldAutoRegenerate = true;  // Auto-regen after shatter
    private float shieldRegenDelay = 3.0f;        // Seconds before regen starts

    public float getPhysicalReduction() {
        return physicalReduction;
    }

    public void setPhysicalReduction(float value) {
        physicalReduction = value;
    }

    public float getFireReduction() {
        return fireReduction;
    }

    public void setFireReduction(float value) {
        fireReduction = value;
    }

    public float getMagicReduction() {
        return magicReduction;
    }

    public void setMagicReduction(float value) {
        magicReduction = value;
    }

    public float getExplosionReduction() {
        return explosionReduction;
    }

    public void setExplosionReduction(float value) {
        explosionReduction = value;
    }

    public float getProjectileReduction() {
        return projectileReduction;
    }

    public void setProjectileReduction(float value) {
        projectileReduction = value;
    }

    public float getArmorBonus() {
        return armorBonus;
    }

    public void setArmorBonus(float value) {
        armorBonus = value;
    }

    public float getToughnessBonus() {
        return toughnessBonus;
    }

    public void setToughnessBonus(float value) {
        toughnessBonus = value;
    }

    public float getKnockbackResistance() {
        return knockbackResistance;
    }

    public void setKnockbackResistance(float value) {
        knockbackResistance = value;
    }

    public boolean isThornsReflect() {
        return thornsReflect;
    }

    public void setThornsReflect(boolean value) {
        thornsReflect = value;
    }

    public float getThornsPercent() {
        return thornsPercent;
    }

    public void setThornsPercent(float value) {
        thornsPercent = value;
    }

    public boolean isShieldReflectProjectiles() {
        return shieldReflectProjectiles;
    }

    public void setShieldReflectProjectiles(boolean value) {
        shieldReflectProjectiles = value;
    }

    public float getShieldBlockStrength() {
        return shieldBlockStrength;
    }

    public void setShieldBlockStrength(float value) {
        shieldBlockStrength = value;
    }

    public float getShieldRecoverySpeed() {
        return shieldRecoverySpeed;
    }

    public void setShieldRecoverySpeed(float value) {
        shieldRecoverySpeed = value;
    }

    public int getShieldColor() {
        return shieldColor;
    }

    public void setShieldColor(int value) {
        shieldColor = value;
    }

    public float getShieldOpacity() {
        return shieldOpacity;
    }

    public void setShieldOpacity(float value) {
        shieldOpacity = value;
    }

    public boolean isShieldGlowEnabled() {
        return shieldGlowEnabled;
    }

    public void setShieldGlowEnabled(boolean value) {
        shieldGlowEnabled = value;
    }

    public float getShieldGlowIntensity() {
        return shieldGlowIntensity;
    }

    public void setShieldGlowIntensity(float value) {
        shieldGlowIntensity = value;
    }

    public float getShieldNoiseIntensity() {
        return shieldNoiseIntensity;
    }

    public void setShieldNoiseIntensity(float value) {
        shieldNoiseIntensity = value;
    }

    public float getShieldPulseSpeed() {
        return shieldPulseSpeed;
    }

    public void setShieldPulseSpeed(float value) {
        shieldPulseSpeed = value;
    }

    public float getShieldDeflectionSpread() {
        return shieldDeflectionSpread;
    }

    public void setShieldDeflectionSpread(float value) {
        shieldDeflectionSpread = value;
    }

    public boolean isShieldDeflectToOwner() {
        return shieldDeflectToOwner;
    }

    public void setShieldDeflectToOwner(boolean value) {
        shieldDeflectToOwner = value;
    }

    public float getShieldDeflectSpeedMult() {
        return shieldDeflectSpeedMult;
    }

    public void setShieldDeflectSpeedMult(float value) {
        shieldDeflectSpeedMult = value;
    }

    public float getShieldShatterThreshold() {
        return shieldShatterThreshold;
    }

    public void setShieldShatterThreshold(float value) {
        shieldShatterThreshold = value;
    }

    public boolean isShieldAutoRegenerate() {
        return shieldAutoRegenerate;
    }

    public void setShieldAutoRegenerate(boolean value) {
        shieldAutoRegenerate = value;
    }

    public float getShieldRegenDelay() {
        return shieldRegenDelay;
    }

    public void setShieldRegenDelay(float value) {
        shieldRegenDelay = value;
    }

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

        // Shield Visual Settings
        if (shieldColor != ShieldColors.DEFAULT) tag.putInt("ShieldColor", shieldColor);
        if (Float.compare(shieldOpacity, 0.6f) != 0) tag.putFloat("ShieldOpacity", shieldOpacity);
        if (!shieldGlowEnabled) tag.putBoolean("ShieldGlow", false);
        if (Float.compare(shieldGlowIntensity, 1.0f) != 0) tag.putFloat("ShieldGlowInt", shieldGlowIntensity);
        if (Float.compare(shieldNoiseIntensity, 0.15f) != 0) tag.putFloat("ShieldNoise", shieldNoiseIntensity);
        if (Float.compare(shieldPulseSpeed, 1.0f) != 0) tag.putFloat("ShieldPulse", shieldPulseSpeed);

        // Shield Deflection Settings
        if (Float.compare(shieldDeflectionSpread, 0.15f) != 0) tag.putFloat("DeflectSpread", shieldDeflectionSpread);
        if (shieldDeflectToOwner) tag.putBoolean("DeflectReturn", true);
        if (Float.compare(shieldDeflectSpeedMult, 0.8f) != 0) tag.putFloat("DeflectSpeed", shieldDeflectSpeedMult);

        // Shield Shatter Settings
        if (Float.compare(shieldShatterThreshold, 10.0f) != 0) tag.putFloat("ShatterThresh", shieldShatterThreshold);
        if (!shieldAutoRegenerate) tag.putBoolean("ShieldAutoRegen", false);
        if (Float.compare(shieldRegenDelay, 3.0f) != 0) tag.putFloat("RegenDelay", shieldRegenDelay);
    }

    /**
     * Load armor stats from NBT compound tag.
     */
    public static ArmorStats load(CompoundTag tag) {
        ArmorStats stats = new ArmorStats();
        if (tag.contains("PhysRed")) stats.setPhysicalReduction(tag.getFloat("PhysRed"));
        if (tag.contains("FireRed")) stats.setFireReduction(tag.getFloat("FireRed"));
        if (tag.contains("MagicRed")) stats.setMagicReduction(tag.getFloat("MagicRed"));
        if (tag.contains("ExplRed")) stats.setExplosionReduction(tag.getFloat("ExplRed"));
        if (tag.contains("ProjRed")) stats.setProjectileReduction(tag.getFloat("ProjRed"));
        if (tag.contains("ArmorBon")) stats.setArmorBonus(tag.getFloat("ArmorBon"));
        if (tag.contains("ToughBon")) stats.setToughnessBonus(tag.getFloat("ToughBon"));
        if (tag.contains("KBRes")) stats.setKnockbackResistance(tag.getFloat("KBRes"));
        if (tag.contains("Thorns")) stats.setThornsReflect(tag.getBoolean("Thorns"));
        if (tag.contains("ThornsPct")) stats.setThornsPercent(tag.getFloat("ThornsPct"));
        stats.setShieldReflectProjectiles(tag.contains("ShieldReflect") && tag.getBoolean("ShieldReflect"));
        stats.setShieldBlockStrength(tag.contains("ShieldBlock") ? tag.getFloat("ShieldBlock") : 0.5f);
        stats.setShieldRecoverySpeed(tag.contains("ShieldRecovery") ? tag.getFloat("ShieldRecovery") : 1.0f);

        // Shield Visual Settings
        stats.setShieldColor(tag.contains("ShieldColor") ? tag.getInt("ShieldColor") : ShieldColors.DEFAULT);
        stats.setShieldOpacity(tag.contains("ShieldOpacity") ? tag.getFloat("ShieldOpacity") : 0.6f);
        stats.setShieldGlowEnabled(!tag.contains("ShieldGlow") || tag.getBoolean("ShieldGlow"));
        stats.setShieldGlowIntensity(tag.contains("ShieldGlowInt") ? tag.getFloat("ShieldGlowInt") : 1.0f);
        stats.setShieldNoiseIntensity(tag.contains("ShieldNoise") ? tag.getFloat("ShieldNoise") : 0.15f);
        stats.setShieldPulseSpeed(tag.contains("ShieldPulse") ? tag.getFloat("ShieldPulse") : 1.0f);

        // Shield Deflection Settings
        stats.setShieldDeflectionSpread(tag.contains("DeflectSpread") ? tag.getFloat("DeflectSpread") : 0.15f);
        stats.setShieldDeflectToOwner(tag.contains("DeflectReturn") && tag.getBoolean("DeflectReturn"));
        stats.setShieldDeflectSpeedMult(tag.contains("DeflectSpeed") ? tag.getFloat("DeflectSpeed") : 0.8f);

        // Shield Shatter Settings
        stats.setShieldShatterThreshold(tag.contains("ShatterThresh") ? tag.getFloat("ShatterThresh") : 10.0f);
        stats.setShieldAutoRegenerate(!tag.contains("ShieldAutoRegen") || tag.getBoolean("ShieldAutoRegen"));
        stats.setShieldRegenDelay(tag.contains("RegenDelay") ? tag.getFloat("RegenDelay") : 3.0f);

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
            && Float.compare(shieldRecoverySpeed, 1.0f) == 0
            // Shield Visual
            && shieldColor == ShieldColors.DEFAULT
            && Float.compare(shieldOpacity, 0.6f) == 0
            && shieldGlowEnabled
            && Float.compare(shieldGlowIntensity, 1.0f) == 0
            && Float.compare(shieldNoiseIntensity, 0.15f) == 0
            && Float.compare(shieldPulseSpeed, 1.0f) == 0
            // Shield Deflection
            && Float.compare(shieldDeflectionSpread, 0.15f) == 0
            && !shieldDeflectToOwner
            && Float.compare(shieldDeflectSpeedMult, 0.8f) == 0
            // Shield Shatter
            && Float.compare(shieldShatterThreshold, 10.0f) == 0
            && shieldAutoRegenerate
            && Float.compare(shieldRegenDelay, 3.0f) == 0;
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
        copy.setPhysicalReduction(this.getPhysicalReduction());
        copy.setFireReduction(this.getFireReduction());
        copy.setMagicReduction(this.getMagicReduction());
        copy.setExplosionReduction(this.getExplosionReduction());
        copy.setProjectileReduction(this.getProjectileReduction());
        copy.setArmorBonus(this.getArmorBonus());
        copy.setToughnessBonus(this.getToughnessBonus());
        copy.setKnockbackResistance(this.getKnockbackResistance());
        copy.setThornsReflect(this.isThornsReflect());
        copy.setThornsPercent(this.getThornsPercent());
        copy.setShieldReflectProjectiles(this.isShieldReflectProjectiles());
        copy.setShieldBlockStrength(this.getShieldBlockStrength());
        copy.setShieldRecoverySpeed(this.getShieldRecoverySpeed());
        // Shield Visual
        copy.setShieldColor(this.getShieldColor());
        copy.setShieldOpacity(this.getShieldOpacity());
        copy.setShieldGlowEnabled(this.isShieldGlowEnabled());
        copy.setShieldGlowIntensity(this.getShieldGlowIntensity());
        copy.setShieldNoiseIntensity(this.getShieldNoiseIntensity());
        copy.setShieldPulseSpeed(this.getShieldPulseSpeed());
        // Shield Deflection
        copy.setShieldDeflectionSpread(this.getShieldDeflectionSpread());
        copy.setShieldDeflectToOwner(this.isShieldDeflectToOwner());
        copy.setShieldDeflectSpeedMult(this.getShieldDeflectSpeedMult());
        // Shield Shatter
        copy.setShieldShatterThreshold(this.getShieldShatterThreshold());
        copy.setShieldAutoRegenerate(this.isShieldAutoRegenerate());
        copy.setShieldRegenDelay(this.getShieldRegenDelay());
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
