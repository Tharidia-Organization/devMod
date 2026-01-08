package com.devmod.stats;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.nbt.CompoundTag;

import com.devmod.config.stats.IItemStats;

public class UsableStats implements IItemStats {
    // ═══════════════════════════════════════════════════════════════
    // USE PROPERTIES
    // ═══════════════════════════════════════════════════════════════

    private int useDuration = 0;           // Ticks to complete use (0 = instant)
    private int cooldownDuration = 0;      // Cooldown in ticks after use
    private @Nonnull String useAnimation = "NONE";  // ItemUseAnimation name

    // ═══════════════════════════════════════════════════════════════
    // THROWABLE PROPERTIES (for projectiles)
    // ═══════════════════════════════════════════════════════════════

    private boolean isThrowable = false;
    private float projectileSpeed = 1.5f;      // Base velocity
    private float projectileGravity = 0.03f;   // Gravity factor
    private float projectileInaccuracy = 1.0f; // Spread/inaccuracy
    private int projectileDamage = 0;          // Direct damage on hit

    // ═══════════════════════════════════════════════════════════════
    // CONSUMPTION PROPERTIES
    // ═══════════════════════════════════════════════════════════════

    private boolean consumeOnUse = true;   // Whether item is consumed
    private @Nonnull String remainderItem = "";     // Item left after use (e.g., "minecraft:bucket")

    // ═══════════════════════════════════════════════════════════════
    // ACCESSORS
    // ═══════════════════════════════════════════════════════════════

    public int getUseDuration() {
        return useDuration;
    }

    public void setUseDuration(int value) {
        useDuration = value;
    }

    public int getCooldownDuration() {
        return cooldownDuration;
    }

    public void setCooldownDuration(int value) {
        cooldownDuration = value;
    }

    public String getUseAnimation() {
        return useAnimation;
    }

    public void setUseAnimation(String value) {
        useAnimation = value == null ? "NONE" : value;
    }

    public boolean isThrowable() {
        return isThrowable;
    }

    public void setThrowable(boolean value) {
        isThrowable = value;
    }

    public float getProjectileSpeed() {
        return projectileSpeed;
    }

    public void setProjectileSpeed(float value) {
        projectileSpeed = value;
    }

    public float getProjectileGravity() {
        return projectileGravity;
    }

    public void setProjectileGravity(float value) {
        projectileGravity = value;
    }

    public float getProjectileInaccuracy() {
        return projectileInaccuracy;
    }

    public void setProjectileInaccuracy(float value) {
        projectileInaccuracy = value;
    }

    public int getProjectileDamage() {
        return projectileDamage;
    }

    public void setProjectileDamage(int value) {
        projectileDamage = value;
    }

    public boolean isConsumeOnUse() {
        return consumeOnUse;
    }

    public void setConsumeOnUse(boolean value) {
        consumeOnUse = value;
    }

    public String getRemainderItem() {
        return remainderItem;
    }

    public void setRemainderItem(String value) {
        remainderItem = value == null ? "" : value;
    }

    // ═══════════════════════════════════════════════════════════════
    // SERIALIZATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Save usable stats to NBT compound tag.
     * Uses short keys to minimize storage overhead.
     */
    public void save(CompoundTag tag) {
        // Use properties
        if (useDuration != 0) tag.putInt("UseDur", useDuration);
        if (cooldownDuration != 0) tag.putInt("Cooldown", cooldownDuration);
        if (!"NONE".equals(useAnimation)) {
            tag.putString("UseAnim", Objects.requireNonNull(useAnimation, "useAnimation"));
        }

        // Throwable properties
        if (isThrowable) {
            tag.putBoolean("Throwable", true);
            tag.putFloat("ProjSpeed", projectileSpeed);
            tag.putFloat("ProjGrav", projectileGravity);
            tag.putFloat("ProjInacc", projectileInaccuracy);
            if (projectileDamage != 0) tag.putInt("ProjDmg", projectileDamage);
        }

        // Consumption
        if (!consumeOnUse) tag.putBoolean("NoConsume", true);
        if (!remainderItem.isEmpty()) {
            tag.putString("Remainder", Objects.requireNonNull(remainderItem, "remainderItem"));
        }
    }

    /**
     * Load usable stats from NBT compound tag (static factory).
     */
    public static UsableStats fromTag(CompoundTag tag) {
        UsableStats stats = new UsableStats();
        stats.load(tag);
        return stats;
    }

    /**
     * Load usable stats from NBT compound tag (instance method for IItemStats).
     */
    @Override
    public void load(CompoundTag tag) {
        // Use properties
        if (tag.contains("UseDur")) useDuration = tag.getInt("UseDur");
        if (tag.contains("Cooldown")) cooldownDuration = tag.getInt("Cooldown");
        if (tag.contains("UseAnim")) {
            useAnimation = Objects.requireNonNull(tag.getString("UseAnim"), "UseAnim");
        }

        // Throwable properties
        if (tag.contains("Throwable")) {
            isThrowable = tag.getBoolean("Throwable");
            projectileSpeed = tag.contains("ProjSpeed") ? tag.getFloat("ProjSpeed") : 1.5f;
            projectileGravity = tag.contains("ProjGrav") ? tag.getFloat("ProjGrav") : 0.03f;
            projectileInaccuracy = tag.contains("ProjInacc") ? tag.getFloat("ProjInacc") : 1.0f;
            projectileDamage = tag.contains("ProjDmg") ? tag.getInt("ProjDmg") : 0;
        }

        // Consumption
        if (tag.contains("NoConsume")) consumeOnUse = !tag.getBoolean("NoConsume");
        if (tag.contains("Remainder")) {
            remainderItem = Objects.requireNonNull(tag.getString("Remainder"), "Remainder");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if all values are at defaults (no modifications).
     */
    public boolean isDefault() {
        return useDuration == 0
            && cooldownDuration == 0
            && "NONE".equals(useAnimation)
            && !isThrowable
            && consumeOnUse
            && remainderItem.isEmpty();
    }

    /**
     * Create a copy of these stats.
     */
    public UsableStats copy() {
        UsableStats copy = new UsableStats();
        copy.useDuration = this.useDuration;
        copy.cooldownDuration = this.cooldownDuration;
        copy.useAnimation = this.useAnimation;
        copy.isThrowable = this.isThrowable;
        copy.projectileSpeed = this.projectileSpeed;
        copy.projectileGravity = this.projectileGravity;
        copy.projectileInaccuracy = this.projectileInaccuracy;
        copy.projectileDamage = this.projectileDamage;
        copy.consumeOnUse = this.consumeOnUse;
        copy.remainderItem = this.remainderItem;
        return copy;
    }

    @Override
    public String toString() {
        return "UsableStats{" +
            "useDur=" + useDuration +
            ", cooldown=" + cooldownDuration +
            ", anim=" + useAnimation +
            ", throwable=" + isThrowable +
            ", projSpeed=" + projectileSpeed +
            ", projGrav=" + projectileGravity +
            ", projInacc=" + projectileInaccuracy +
            ", projDmg=" + projectileDamage +
            ", consume=" + consumeOnUse +
            ", remainder=" + remainderItem +
            '}';
    }
}
