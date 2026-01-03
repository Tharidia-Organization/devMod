package com.devmod.client.ui.editor.modules;

import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import com.devmod.client.ui.editor.RangedWeaponModule;
import com.devmod.client.ui.editor.components.SourceBadge;

/**
 * Core data management for RangedModule.
 * Handles stats loading, source tracking, and state comparison.
 * Follows the same pattern as ArmorModuleCore.
 */
public class RangedModuleCore {

    private static final double EPSILON = 1e-3;

    // Stats state
    private RangedWeaponModule.RangedStats stats = new RangedWeaponModule.RangedStats();
    private RangedWeaponModule.RangedStats originalStats = new RangedWeaponModule.RangedStats();
    private @Nullable RangedWeaponModule.SourcedStats sourcedStats;

    public RangedModuleCore() {
        // Default constructor - stats initialized to defaults
    }

    // ═══════════════════════════════════════════════════════════════
    // LOADING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Load stats from item using RangedWeaponModule.
     */
    public void loadStatsFromItem(ItemStack item) {
        Objects.requireNonNull(item, "item cannot be null");
        stats = RangedWeaponModule.getStats(item);
        sourcedStats = RangedWeaponModule.getSourcedStats(item);
        originalStats = stats.copy();
    }

    // ═══════════════════════════════════════════════════════════════
    // VARIANT DETECTION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Detect variant from item type.
     */
    public RangedModule.RangedVariant detectVariant(ItemStack item) {
        Objects.requireNonNull(item, "item cannot be null");
        var itm = item.getItem();
        if (itm instanceof net.minecraft.world.item.BowItem) {
            return RangedModule.RangedVariant.BOW;
        } else if (itm instanceof net.minecraft.world.item.CrossbowItem) {
            return RangedModule.RangedVariant.CROSSBOW;
        } else if (itm instanceof net.minecraft.world.item.TridentItem) {
            return RangedModule.RangedVariant.TRIDENT;
        }
        return RangedModule.RangedVariant.GENERIC;
    }

    // ═══════════════════════════════════════════════════════════════
    // SOURCE DETERMINATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Determine source badge based on where stats come from.
     */
    public SourceBadge.Source determineSource() {
        RangedWeaponModule.SourcedStats sourced = sourcedStats;
        if (sourced != null) {
            RangedWeaponModule.SourcedValue<?> drawSpeed = sourced.drawSpeed();
            if (drawSpeed != null) {
                return switch (drawSpeed.source()) {
                    case DEVMOD_COMPONENT -> SourceBadge.Source.DEV;
                    case CUSTOM_DATA -> SourceBadge.Source.NBT;
                    default -> SourceBadge.Source.VANILLA;
                };
            }
        }
        return SourceBadge.Source.VANILLA;
    }

    // ═══════════════════════════════════════════════════════════════
    // CUSTOM DATA TAG
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get custom data tag from item.
     */
    public CompoundTag getCustomDataTag(ItemStack item) {
        Objects.requireNonNull(item, "item cannot be null");
        var customType = Objects.requireNonNull(DataComponents.CUSTOM_DATA, "custom data component");
        CustomData data = item.get(customType);
        CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
        return tag == null ? new CompoundTag() : tag;
    }

    // ═══════════════════════════════════════════════════════════════
    // PAYLOAD BUILDING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Build stats tag for network payload.
     */
    public CompoundTag buildStatsTag() {
        CompoundTag rangedTag = new CompoundTag();
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
            rangedTag.putString("ammoFilter", Objects.requireNonNull(stats.ammoFilter));
        }
        return rangedTag;
    }

    // ═══════════════════════════════════════════════════════════════
    // STATE COMPARISON
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if stats have been modified from original.
     */
    public boolean hasPendingDiff() {
        return !statsEquals(stats, originalStats);
    }

    /**
     * Compare two RangedStats objects for equality with epsilon tolerance.
     */
    public boolean statsEquals(@Nullable RangedWeaponModule.RangedStats a,
                               @Nullable RangedWeaponModule.RangedStats b) {
        if (a == null || b == null) return a == b;
        return Math.abs(a.drawSpeed - b.drawSpeed) < EPSILON
            && Math.abs(a.chargeTime - b.chargeTime) < EPSILON
            && Math.abs(a.accuracy - b.accuracy) < EPSILON
            && Math.abs(a.range - b.range) < EPSILON
            && Math.abs(a.projectileSpeed - b.projectileSpeed) < EPSILON
            && Math.abs(a.projectileGravity - b.projectileGravity) < EPSILON
            && Math.abs(a.projectileSpread - b.projectileSpread) < EPSILON
            && Math.abs(a.baseDamage - b.baseDamage) < EPSILON
            && a.piercing == b.piercing
            && a.multishotCount == b.multishotCount
            && a.multishot == b.multishot
            && a.infinityOverride == b.infinityOverride
            && Math.abs(a.critChance - b.critChance) < EPSILON
            && Math.abs(a.critDamage - b.critDamage) < EPSILON
            && Math.abs(a.loyaltySpeed - b.loyaltySpeed) < EPSILON
            && Math.abs(a.riptideDistance - b.riptideDistance) < EPSILON
            && a.riptideRequiresWater == b.riptideRequiresWater
            && a.channeling == b.channeling
            && Objects.equals(a.ammoFilter, b.ammoFilter);
    }

    // ═══════════════════════════════════════════════════════════════
    // RESET
    // ═══════════════════════════════════════════════════════════════

    /**
     * Reset stats to original values.
     */
    public void resetToOriginal() {
        stats = originalStats.copy();
    }

    // ═══════════════════════════════════════════════════════════════
    // PREVIEW
    // ═══════════════════════════════════════════════════════════════

    /**
     * Apply current stats to a copy of the item for preview.
     */
    public ItemStack applyToPreview(ItemStack item) {
        Objects.requireNonNull(item, "item cannot be null");
        ItemStack copy = item.copy();
        RangedWeaponModule.applyStats(copy, stats);
        return copy;
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════

    public RangedWeaponModule.RangedStats getStats() {
        return stats;
    }

    public RangedWeaponModule.RangedStats getOriginalStats() {
        return originalStats;
    }

    @Nullable
    public RangedWeaponModule.SourcedStats getSourcedStats() {
        return sourcedStats;
    }
}
