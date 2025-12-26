package com.devmod.stats;

import java.util.Objects;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.CustomData;

public class RangedWeaponStats {
    public float drawSpeed = 1.0f;
    public float chargeTime = 1.0f;
    public float accuracy = 1.0f;
    public float range = 1.0f;
    public float projectileSpeed = 1.0f;
    public float projectileGravity = 0.05f;
    public float projectileSpread = 1.0f;
    public float baseDamage = 0.0f;
    public int piercing = 0;
    public int multishotCount = 1;
    public boolean multishot = false;
    public boolean infinityOverride = false;
    public float critChance = 0.0f;
    public float critDamage = 1.5f;
    public String ammoFilter = "";
    // Trident-specific
    public float riptideDistance = 0.0f;
    public float loyaltySpeed = 0.0f;
    public boolean riptideRequiresWater = true;
    public boolean channeling = false;

    public RangedWeaponStats copy() {
        var copy = new RangedWeaponStats();
        copy.drawSpeed = drawSpeed;
        copy.chargeTime = chargeTime;
        copy.accuracy = accuracy;
        copy.range = range;
        copy.projectileSpeed = projectileSpeed;
        copy.projectileGravity = projectileGravity;
        copy.projectileSpread = projectileSpread;
        copy.baseDamage = baseDamage;
        copy.piercing = piercing;
        copy.multishotCount = multishotCount;
        copy.multishot = multishot;
        copy.infinityOverride = infinityOverride;
        copy.critChance = critChance;
        copy.critDamage = critDamage;
        copy.ammoFilter = ammoFilter;
        copy.riptideDistance = riptideDistance;
        copy.loyaltySpeed = loyaltySpeed;
        copy.riptideRequiresWater = riptideRequiresWater;
        copy.channeling = channeling;
        return copy;
    }

    /**
     * Checks if item is a ranged weapon (bow, crossbow, trident).
     */
    public static boolean isRangedWeapon(ItemStack item) {
        return item.getItem() instanceof BowItem
            || item.getItem() instanceof CrossbowItem
            || item.getItem() instanceof TridentItem;
    }

    /**
     * Gets stats from an ItemStack, reading from components and CustomData.
     */
    public static RangedWeaponStats getStats(ItemStack item) {
        var stats = new RangedWeaponStats();

        // Set defaults based on weapon type
        if (item.getItem() instanceof BowItem) {
            stats.drawSpeed = 1.0f;
            stats.chargeTime = 1.0f;
            stats.accuracy = 0.95f;
            stats.range = 1.0f;
            stats.projectileSpread = 1.0f;
            stats.projectileGravity = 0.05f;
        } else if (item.getItem() instanceof CrossbowItem) {
            stats.drawSpeed = 0.8f;
            stats.chargeTime = 1.0f;
            stats.accuracy = 1.0f;
            stats.range = 1.2f;
            stats.projectileSpeed = 1.1f;
            stats.projectileSpread = 0.0f;
            stats.projectileGravity = 0.05f;
        } else if (item.getItem() instanceof TridentItem) {
            stats.drawSpeed = 1.0f;
            stats.chargeTime = 1.0f;
            stats.accuracy = 1.0f;
            stats.range = 1.2f;
            stats.projectileSpeed = 1.0f;
            stats.projectileSpread = 0.0f;
            stats.projectileGravity = 0.03f;
            stats.baseDamage = 8.0f;
        }

        // Read from data components first (if present)
        try {
            Float drawTicks = item.get(Objects.requireNonNull(com.devmod.components.RangedComponents.DRAW_TIME_TICKS.get()));
            if (drawTicks != null) stats.drawSpeed = drawTicks;
            Float projSpeed = item.get(Objects.requireNonNull(com.devmod.components.RangedComponents.PROJECTILE_SPEED.get()));
            if (projSpeed != null) stats.projectileSpeed = projSpeed;
            Float projGrav = item.get(Objects.requireNonNull(com.devmod.components.RangedComponents.PROJECTILE_GRAVITY.get()));
            if (projGrav != null) stats.projectileGravity = projGrav;
            Float projSpread = item.get(Objects.requireNonNull(com.devmod.components.RangedComponents.PROJECTILE_SPREAD.get()));
            if (projSpread != null) stats.projectileSpread = projSpread;
            Float baseDmg = item.get(Objects.requireNonNull(com.devmod.components.RangedComponents.BASE_ARROW_DAMAGE.get()));
            if (baseDmg != null) stats.baseDamage = baseDmg;
            Integer multiCount = item.get(Objects.requireNonNull(com.devmod.components.RangedComponents.MULTISHOT_COUNT.get()));
            if (multiCount != null) stats.multishotCount = multiCount;
            Integer pierce = item.get(Objects.requireNonNull(com.devmod.components.RangedComponents.PIERCING_LEVEL.get()));
            if (pierce != null) stats.piercing = pierce;
            ResourceLocation ammoTag = item.get(Objects.requireNonNull(com.devmod.components.RangedComponents.AMMO_TAG_FILTER.get()));
            if (ammoTag != null) stats.ammoFilter = ammoTag.toString();
        } catch (Exception ignored) {
            // graceful fallback to CustomData
        }

        // Read from CustomData component if present
        if (item.has(DataComponents.CUSTOM_DATA)) {
            CustomData customData = item.get(DataComponents.CUSTOM_DATA);
            if (customData != null) {
                var tag = customData.copyTag();
                if (tag.contains("devmod:ranged")) {
                    var rangedTag = tag.getCompound("devmod:ranged");
                    if (rangedTag.contains("drawSpeed")) stats.drawSpeed = rangedTag.getFloat("drawSpeed");
                    if (rangedTag.contains("chargeTime")) stats.chargeTime = rangedTag.getFloat("chargeTime");
                    if (rangedTag.contains("accuracy")) stats.accuracy = rangedTag.getFloat("accuracy");
                    if (rangedTag.contains("range")) stats.range = rangedTag.getFloat("range");
                    if (rangedTag.contains("projectileSpeed")) stats.projectileSpeed = rangedTag.getFloat("projectileSpeed");
                    if (rangedTag.contains("projectileGravity")) stats.projectileGravity = rangedTag.getFloat("projectileGravity");
                    if (rangedTag.contains("projectileSpread")) stats.projectileSpread = rangedTag.getFloat("projectileSpread");
                    if (rangedTag.contains("baseDamage")) stats.baseDamage = rangedTag.getFloat("baseDamage");
                    if (rangedTag.contains("piercing")) stats.piercing = rangedTag.getInt("piercing");
                    if (rangedTag.contains("multishotCount")) stats.multishotCount = rangedTag.getInt("multishotCount");
                    if (rangedTag.contains("multishot")) stats.multishot = rangedTag.getBoolean("multishot");
                    if (rangedTag.contains("infinityOverride")) stats.infinityOverride = rangedTag.getBoolean("infinityOverride");
                    if (rangedTag.contains("critChance")) stats.critChance = rangedTag.getFloat("critChance");
                    if (rangedTag.contains("critDamage")) stats.critDamage = rangedTag.getFloat("critDamage");
                    if (rangedTag.contains("ammoFilter")) stats.ammoFilter = rangedTag.getString("ammoFilter");
                    // Trident-specific
                    if (rangedTag.contains("riptideDistance")) stats.riptideDistance = rangedTag.getFloat("riptideDistance");
                    if (rangedTag.contains("loyaltySpeed")) stats.loyaltySpeed = rangedTag.getFloat("loyaltySpeed");
                    if (rangedTag.contains("riptideRequiresWater")) stats.riptideRequiresWater = rangedTag.getBoolean("riptideRequiresWater");
                    if (rangedTag.contains("channeling")) stats.channeling = rangedTag.getBoolean("channeling");
                }
            }
        }

        return stats;
    }
}
