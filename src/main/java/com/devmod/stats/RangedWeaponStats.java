package com.devmod.stats;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.CustomData;

import com.devmod.DevMod;
import com.devmod.ammo.AmmoSystem;

public class RangedWeaponStats {
    private static boolean componentReadFailedLogged = false;
    private float drawSpeed = 1.0f;
    private float chargeTime = 1.0f;
    private float accuracy = 1.0f;
    private float range = 1.0f;
    private float projectileSpeed = 1.0f;
    private float projectileGravity = 0.05f;
    private float projectileSpread = 1.0f;
    private float baseDamage = 0.0f;
    private int piercing = 0;
    private int multishotCount = 1;
    private boolean multishot = false;
    private boolean infinityOverride = false;
    private float critChance = 0.0f;
    private float critDamage = 1.5f;
    private String ammoFilter = "";
    // Trident-specific
    private float riptideDistance = 0.0f;
    private float loyaltySpeed = 0.0f;
    private boolean riptideRequiresWater = true;
    private boolean channeling = false;

    public float getDrawSpeed() {
        return drawSpeed;
    }

    public void setDrawSpeed(float value) {
        drawSpeed = value;
    }

    public float getChargeTime() {
        return chargeTime;
    }

    public void setChargeTime(float value) {
        chargeTime = value;
    }

    public float getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(float value) {
        accuracy = value;
    }

    public float getRange() {
        return range;
    }

    public void setRange(float value) {
        range = value;
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

    public float getProjectileSpread() {
        return projectileSpread;
    }

    public void setProjectileSpread(float value) {
        projectileSpread = value;
    }

    public float getBaseDamage() {
        return baseDamage;
    }

    public void setBaseDamage(float value) {
        baseDamage = value;
    }

    public int getPiercing() {
        return piercing;
    }

    public void setPiercing(int value) {
        piercing = value;
    }

    public int getMultishotCount() {
        return multishotCount;
    }

    public void setMultishotCount(int value) {
        multishotCount = value;
    }

    public boolean isMultishot() {
        return multishot;
    }

    public void setMultishot(boolean value) {
        multishot = value;
    }

    public boolean isInfinityOverride() {
        return infinityOverride;
    }

    public void setInfinityOverride(boolean value) {
        infinityOverride = value;
    }

    public float getCritChance() {
        return critChance;
    }

    public void setCritChance(float value) {
        critChance = value;
    }

    public float getCritDamage() {
        return critDamage;
    }

    public void setCritDamage(float value) {
        critDamage = value;
    }

    public String getAmmoFilter() {
        return ammoFilter;
    }

    public void setAmmoFilter(String value) {
        ammoFilter = value == null ? "" : value;
    }

    public float getRiptideDistance() {
        return riptideDistance;
    }

    public void setRiptideDistance(float value) {
        riptideDistance = value;
    }

    public float getLoyaltySpeed() {
        return loyaltySpeed;
    }

    public void setLoyaltySpeed(float value) {
        loyaltySpeed = value;
    }

    public boolean isRiptideRequiresWater() {
        return riptideRequiresWater;
    }

    public void setRiptideRequiresWater(boolean value) {
        riptideRequiresWater = value;
    }

    public boolean isChanneling() {
        return channeling;
    }

    public void setChanneling(boolean value) {
        channeling = value;
    }

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
     * Helper to ensure DataComponentType is non-null for Eclipse null checker.
     */
    @Nonnull
    private static <T> DataComponentType<T> requireComponent(DataComponentType<T> type) {
        if (type == null) {
            throw new IllegalStateException("DataComponentType cannot be null");
        }
        return type;
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
            if (ammoTag != null) stats.ammoFilter = AmmoSystem.formatAmmoFilter(ammoTag);
        } catch (Exception e) {
            if (!componentReadFailedLogged) {
                componentReadFailedLogged = true;
                DevMod.LOGGER.debug("[RangedWeaponStats] Failed to read ranged components, falling back to CustomData", e);
            }
        }

        // Read from CustomData component if present
        DataComponentType<CustomData> customDataType = requireComponent(DataComponents.CUSTOM_DATA);
        if (item.has(customDataType)) {
            CustomData customData = item.get(customDataType);
            if (customData != null) {
                var tag = customData.copyTag();
                if (tag.contains("RangedStats")) {
                    var rangedTag = tag.getCompound("RangedStats");
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
