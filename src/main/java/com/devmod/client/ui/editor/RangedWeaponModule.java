package com.devmod.client.ui.editor;

import java.util.Objects;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class RangedWeaponModule {
    public enum ValueSource {
        VANILLA_DEFAULT,
        DEVMOD_COMPONENT,
        CUSTOM_DATA,
        UNKNOWN
    }

    public record SourcedValue<T>(T value, ValueSource source, String detail) {
        public static <T> SourcedValue<T> vanilla(T val) { return new SourcedValue<>(val, ValueSource.VANILLA_DEFAULT, null); }
        public static <T> SourcedValue<T> devmod(T val) { return new SourcedValue<>(val, ValueSource.DEVMOD_COMPONENT, null); }
        public static <T> SourcedValue<T> custom(T val) { return new SourcedValue<>(val, ValueSource.CUSTOM_DATA, null); }
    }

    public record SourcedStats(
        SourcedValue<Float> drawSpeed,
        SourcedValue<Float> chargeTime,
        SourcedValue<Float> accuracy,
        SourcedValue<Float> range,
        SourcedValue<Float> projectileSpeed,
        SourcedValue<Float> projectileGravity,
        SourcedValue<Float> projectileSpread,
        SourcedValue<Float> baseDamage,
        SourcedValue<Integer> piercing,
        SourcedValue<Integer> multishotCount,
        SourcedValue<Boolean> multishot,
        SourcedValue<Boolean> infinityOverride,
        SourcedValue<Float> critChance,
        SourcedValue<Float> critDamage,
        SourcedValue<String> ammoFilter,
        SourcedValue<Float> riptideDistance,
        SourcedValue<Float> loyaltySpeed,
        SourcedValue<Boolean> riptideRequiresWater,
        SourcedValue<Boolean> channeling
    ) { }
    public static class RangedStats {
        public float drawSpeed = 1.0f;
        public float chargeTime = 1.0f; // crossbow reload speed multiplier
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
        public String ammoFilter = ""; // optional filter/indicator for ammo source
        // Trident-specific
        public float riptideDistance = 0.0f;
        public float loyaltySpeed = 0.0f;
        public boolean riptideRequiresWater = true;
        public boolean channeling = false;
        
        public RangedStats copy() {
            var copy = new RangedStats();
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
    }
    
    public static boolean isRangedWeapon(ItemStack item) {
        return item.getItem() instanceof BowItem || item.getItem() instanceof CrossbowItem;
    }
    
    public static RangedStats getStats(ItemStack item) {
        var stats = new RangedStats();
        
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
        } else if (item.getItem() instanceof net.minecraft.world.item.TridentItem) {
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
            Float drawTicks = item.get(java.util.Objects.requireNonNull(com.devmod.components.RangedComponents.DRAW_TIME_TICKS.get()));
            if (drawTicks != null) stats.drawSpeed = drawTicks;
            Float projSpeed = item.get(java.util.Objects.requireNonNull(com.devmod.components.RangedComponents.PROJECTILE_SPEED.get()));
            if (projSpeed != null) stats.projectileSpeed = projSpeed;
            Float projGrav = item.get(java.util.Objects.requireNonNull(com.devmod.components.RangedComponents.PROJECTILE_GRAVITY.get()));
            if (projGrav != null) stats.projectileGravity = projGrav;
            Float projSpread = item.get(java.util.Objects.requireNonNull(com.devmod.components.RangedComponents.PROJECTILE_SPREAD.get()));
            if (projSpread != null) stats.projectileSpread = projSpread;
            Float baseDmg = item.get(java.util.Objects.requireNonNull(com.devmod.components.RangedComponents.BASE_ARROW_DAMAGE.get()));
            if (baseDmg != null) stats.baseDamage = baseDmg;
            Integer multiCount = item.get(java.util.Objects.requireNonNull(com.devmod.components.RangedComponents.MULTISHOT_COUNT.get()));
            if (multiCount != null) stats.multishotCount = multiCount;
            Integer pierce = item.get(java.util.Objects.requireNonNull(com.devmod.components.RangedComponents.PIERCING_LEVEL.get()));
            if (pierce != null) stats.piercing = pierce;
            ResourceLocation ammoTag = item.get(java.util.Objects.requireNonNull(com.devmod.components.RangedComponents.AMMO_TAG_FILTER.get()));
            if (ammoTag != null) stats.ammoFilter = ammoTag.toString();
        } catch (Exception ignored) {
            // graceful fallback to CustomData
        }

        // Read from CustomData component if present
        DataComponentType<CustomData> customDataType =
            Objects.requireNonNull(net.minecraft.core.component.DataComponents.CUSTOM_DATA, "custom data component");
        CustomData customData = item.get(customDataType);
        if (customData != null) {
            var tag = java.util.Objects.requireNonNull(customData.copyTag(), "custom data tag cannot be null");
            if (tag.contains("RangedStats")) {
                var rangedTag = tag.getCompound("RangedStats");
                stats.drawSpeed = rangedTag.getFloat("drawSpeed");
                stats.chargeTime = rangedTag.contains("chargeTime") ? rangedTag.getFloat("chargeTime") : stats.chargeTime;
                stats.accuracy = rangedTag.getFloat("accuracy");
                stats.range = rangedTag.getFloat("range");
                stats.projectileSpeed = rangedTag.getFloat("projectileSpeed");
                stats.projectileGravity = rangedTag.contains("projectileGravity") ? rangedTag.getFloat("projectileGravity") : stats.projectileGravity;
                stats.projectileSpread = rangedTag.contains("projectileSpread") ? rangedTag.getFloat("projectileSpread") : stats.projectileSpread;
                stats.baseDamage = rangedTag.contains("baseDamage") ? rangedTag.getFloat("baseDamage") : stats.baseDamage;
                stats.piercing = rangedTag.getInt("piercing");
                stats.multishotCount = rangedTag.contains("multishotCount") ? rangedTag.getInt("multishotCount") : stats.multishotCount;
                stats.multishot = rangedTag.getBoolean("multishot");
                stats.infinityOverride = rangedTag.getBoolean("infinityOverride");
                stats.critChance = rangedTag.getFloat("critChance");
                stats.critDamage = rangedTag.getFloat("critDamage");
                if (rangedTag.contains("ammoFilter")) {
                    stats.ammoFilter = rangedTag.getString("ammoFilter");
                }
                if (rangedTag.contains("riptideDistance")) {
                    stats.riptideDistance = rangedTag.getFloat("riptideDistance");
                }
                if (rangedTag.contains("loyaltySpeed")) {
                    stats.loyaltySpeed = rangedTag.getFloat("loyaltySpeed");
                }
                if (rangedTag.contains("riptideRequiresWater")) {
                    stats.riptideRequiresWater = rangedTag.getBoolean("riptideRequiresWater");
                }
                if (rangedTag.contains("channeling")) {
                    stats.channeling = rangedTag.getBoolean("channeling");
                }
            }
        }
        
        return stats;
    }
    
    public static void applyStats(ItemStack item, RangedStats stats) {
        var customComponent = Objects.requireNonNull(DataComponents.CUSTOM_DATA, "custom data component");
        CustomData customData = Objects.requireNonNullElse(item.get(customComponent), CustomData.EMPTY);
        
        var tag = customData.copyTag();
        var rangedTag = new net.minecraft.nbt.CompoundTag();

        rangedTag.putFloat("drawSpeed", stats.drawSpeed);
        rangedTag.putFloat("chargeTime", stats.chargeTime);
        rangedTag.putFloat("accuracy", stats.accuracy);
        rangedTag.putFloat("range", stats.range);
        rangedTag.putFloat("projectileSpeed", stats.projectileSpeed);
        rangedTag.putFloat("projectileGravity", stats.projectileGravity);
        rangedTag.putFloat("projectileSpread", stats.projectileSpread);
        rangedTag.putFloat("baseDamage", stats.baseDamage);
        rangedTag.putInt("piercing", stats.piercing);
        rangedTag.putInt("multishotCount", stats.multishotCount);
        rangedTag.putBoolean("multishot", stats.multishot);
        rangedTag.putBoolean("infinityOverride", stats.infinityOverride);
        rangedTag.putFloat("critChance", stats.critChance);
        rangedTag.putFloat("critDamage", stats.critDamage);
        rangedTag.putFloat("riptideDistance", stats.riptideDistance);
        rangedTag.putFloat("loyaltySpeed", stats.loyaltySpeed);
        rangedTag.putBoolean("riptideRequiresWater", stats.riptideRequiresWater);
        rangedTag.putBoolean("channeling", stats.channeling);
        if (stats.ammoFilter != null && !stats.ammoFilter.isBlank()) {
            rangedTag.putString("ammoFilter", java.util.Objects.requireNonNull(stats.ammoFilter));
        }

        tag.put("RangedStats", rangedTag);
        item.set(customComponent, CustomData.of(tag));

        // Also write dedicated data components for downstream hooks
        try {
            item.set(java.util.Objects.requireNonNull(com.devmod.components.RangedComponents.DRAW_TIME_TICKS.get()), stats.drawSpeed);
            item.set(java.util.Objects.requireNonNull(com.devmod.components.RangedComponents.PROJECTILE_SPEED.get()), stats.projectileSpeed);
            item.set(java.util.Objects.requireNonNull(com.devmod.components.RangedComponents.PROJECTILE_GRAVITY.get()), stats.projectileGravity);
            item.set(java.util.Objects.requireNonNull(com.devmod.components.RangedComponents.PROJECTILE_SPREAD.get()), stats.projectileSpread);
            item.set(java.util.Objects.requireNonNull(com.devmod.components.RangedComponents.BASE_ARROW_DAMAGE.get()), stats.baseDamage);
            item.set(java.util.Objects.requireNonNull(com.devmod.components.RangedComponents.MULTISHOT_COUNT.get()), stats.multishotCount);
            item.set(java.util.Objects.requireNonNull(com.devmod.components.RangedComponents.PIERCING_LEVEL.get()), stats.piercing);
            if (stats.ammoFilter != null && !stats.ammoFilter.isBlank()) {
                item.set(java.util.Objects.requireNonNull(com.devmod.components.RangedComponents.AMMO_TAG_FILTER.get()), ResourceLocation.parse(java.util.Objects.requireNonNull(stats.ammoFilter)));
            } else {
                item.remove(java.util.Objects.requireNonNull(com.devmod.components.RangedComponents.AMMO_TAG_FILTER.get()));
            }
            // Trident extras are CustomData-only for now
        } catch (Exception ignored) {
            // best effort
        }
    }

    /**
    * Build sourced stats describing origin (vanilla vs devmod component vs custom NBT).
     */
    public static SourcedStats getSourcedStats(ItemStack item) {
        RangedStats raw = getStats(item);
        ValueSource compSrc = ValueSource.VANILLA_DEFAULT;
        if (item.has(java.util.Objects.requireNonNull(com.devmod.components.RangedComponents.DRAW_TIME_TICKS.get()))) compSrc = ValueSource.DEVMOD_COMPONENT;
        else if (item.has(Objects.requireNonNull(DataComponents.CUSTOM_DATA))) compSrc = ValueSource.CUSTOM_DATA;

        return new SourcedStats(
            new SourcedValue<>(raw.drawSpeed, compSrc, null),
            new SourcedValue<>(raw.chargeTime, compSrc, null),
            new SourcedValue<>(raw.accuracy, compSrc, null),
            new SourcedValue<>(raw.range, compSrc, null),
            new SourcedValue<>(raw.projectileSpeed, compSrc, null),
            new SourcedValue<>(raw.projectileGravity, compSrc, null),
            new SourcedValue<>(raw.projectileSpread, compSrc, null),
            new SourcedValue<>(raw.baseDamage, compSrc, null),
            new SourcedValue<>(raw.piercing, compSrc, null),
            new SourcedValue<>(raw.multishotCount, compSrc, null),
            new SourcedValue<>(raw.multishot, compSrc, null),
            new SourcedValue<>(raw.infinityOverride, compSrc, null),
            new SourcedValue<>(raw.critChance, compSrc, null),
            new SourcedValue<>(raw.critDamage, compSrc, null),
            new SourcedValue<>(raw.ammoFilter, compSrc, null),
            new SourcedValue<>(raw.riptideDistance, compSrc, null),
            new SourcedValue<>(raw.loyaltySpeed, compSrc, null),
            new SourcedValue<>(raw.riptideRequiresWater, compSrc, null),
            new SourcedValue<>(raw.channeling, compSrc, null)
        );
    }
}
