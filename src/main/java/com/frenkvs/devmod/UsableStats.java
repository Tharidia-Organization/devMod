package com.frenkvs.devmod;

import net.minecraft.nbt.CompoundTag;

import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Usable item statistics for items with cooldowns, use durations, and throwable properties.
 * Applied to items like snowballs, potions, goat horns, shields, bows, etc.
 */
public class UsableStats {
    // ═══════════════════════════════════════════════════════════════
    // USE PROPERTIES
    // ═══════════════════════════════════════════════════════════════

    public int useDuration = 0;           // Ticks to complete use (0 = instant)
    public int cooldownDuration = 0;      // Cooldown in ticks after use
    public @Nonnull String useAnimation = "NONE";  // ItemUseAnimation name

    // ═══════════════════════════════════════════════════════════════
    // THROWABLE PROPERTIES (for projectiles)
    // ═══════════════════════════════════════════════════════════════

    public boolean isThrowable = false;
    public float projectileSpeed = 1.5f;      // Base velocity
    public float projectileGravity = 0.03f;   // Gravity factor
    public float projectileInaccuracy = 1.0f; // Spread/inaccuracy
    public int projectileDamage = 0;          // Direct damage on hit

    // ═══════════════════════════════════════════════════════════════
    // CONSUMPTION PROPERTIES
    // ═══════════════════════════════════════════════════════════════

    public boolean consumeOnUse = true;   // Whether item is consumed
    public @Nonnull String remainderItem = "";     // Item left after use (e.g., "minecraft:bucket")

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
     * Load usable stats from NBT compound tag.
     */
    public static UsableStats load(CompoundTag tag) {
        UsableStats stats = new UsableStats();

        // Use properties
        if (tag.contains("UseDur")) stats.useDuration = tag.getInt("UseDur");
        if (tag.contains("Cooldown")) stats.cooldownDuration = tag.getInt("Cooldown");
        if (tag.contains("UseAnim")) {
            stats.useAnimation = Objects.requireNonNull(tag.getString("UseAnim"), "UseAnim");
        }

        // Throwable properties
        if (tag.contains("Throwable")) {
            stats.isThrowable = tag.getBoolean("Throwable");
            stats.projectileSpeed = tag.contains("ProjSpeed") ? tag.getFloat("ProjSpeed") : 1.5f;
            stats.projectileGravity = tag.contains("ProjGrav") ? tag.getFloat("ProjGrav") : 0.03f;
            stats.projectileInaccuracy = tag.contains("ProjInacc") ? tag.getFloat("ProjInacc") : 1.0f;
            stats.projectileDamage = tag.contains("ProjDmg") ? tag.getInt("ProjDmg") : 0;
        }

        // Consumption
        if (tag.contains("NoConsume")) stats.consumeOnUse = !tag.getBoolean("NoConsume");
        if (tag.contains("Remainder")) {
            stats.remainderItem = Objects.requireNonNull(tag.getString("Remainder"), "Remainder");
        }

        return stats;
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
