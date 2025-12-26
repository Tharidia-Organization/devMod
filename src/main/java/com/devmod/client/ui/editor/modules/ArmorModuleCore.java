package com.devmod.client.ui.editor.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import com.devmod.DevMod;
import com.devmod.client.ui.editor.components.SourceBadge;
import com.devmod.components.ArmorComponents;
import com.devmod.config.ArmorConfigManager;
import com.devmod.stats.ArmorStats;
public class ArmorModuleCore {

    static final String NBT_KEY = "ArmorModStats";
    static final double EPSILON = 1e-4;

    private static final @Nonnull net.minecraft.world.item.component.ItemAttributeModifiers NONNULL_EMPTY =
        Objects.requireNonNull(net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY,
            "ItemAttributeModifiers.EMPTY cannot be null");

    // Source tracking
    boolean hasComponentData = false;
    boolean hasNbtData = false;
    boolean hasGlobalConfig = false;

    // Stats state
    ArmorStats stats = new ArmorStats();
    ArmorStats originalStats = new ArmorStats();

    public ArmorModuleCore() {
    }

    // ═══════════════════════════════════════════════════════════════
    // LOADING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Load stats from item NBT/components.
     */
    public void loadStatsFromItem(ItemStack item) {
        // Reset source tracking
        hasComponentData = false;
        hasNbtData = false;
        hasGlobalConfig = ArmorConfigManager.hasGlobalConfig(item.getItem());

        // Prefer component storage; fall back to legacy CustomData
        CompoundTag componentTag = null;
        try {
            var armorComponent = ArmorComponents.armorStatsComponent();
            componentTag = armorComponent != null ? item.get(armorComponent) : null;
        } catch (Exception ignored) {}

        var customDataType = Objects.requireNonNull(
            DataComponents.CUSTOM_DATA,
            "CUSTOM_DATA component type cannot be null");

        DevMod.LOGGER.info("[Editor][Armor] Item={} | hasComponent={} | hasCustomData={}",
            item.getItem().toString(),
            componentTag != null && !componentTag.isEmpty(),
            item.has(customDataType));

        if (componentTag != null && !componentTag.isEmpty()) {
            stats = ArmorStats.load(componentTag.copy());
            hasComponentData = true;
            originalStats = stats.copy();
            return;
        }

        CustomData customData = item.getOrDefault(
            customDataType,
            Objects.requireNonNull(CustomData.EMPTY, "CustomData.EMPTY cannot be null")
        );
        CompoundTag tag = Objects.requireNonNull(customData.copyTag(), "custom data tag cannot be null");

        if (tag.contains(NBT_KEY)) {
            stats = ArmorStats.load(tag.getCompound(NBT_KEY));
            hasNbtData = true;
        } else {
            stats = new ArmorStats();
            applyVanillaDefaults(item, stats);
        }
        originalStats = stats.copy();
    }

    /**
     * Populate armor stats from vanilla attributes when no custom data exists.
     */
    void applyVanillaDefaults(ItemStack item, ArmorStats target) {
        if (target == null) return;
        try {
            net.minecraft.world.entity.EquipmentSlot slot = net.minecraft.world.entity.EquipmentSlot.CHEST;
            if (item.getItem() instanceof net.minecraft.world.item.ArmorItem armorItem) {
                slot = armorItem.getEquipmentSlot();
            } else if (item.getItem() instanceof net.minecraft.world.item.ShieldItem) {
                slot = net.minecraft.world.entity.EquipmentSlot.OFFHAND;
            }

            net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.ItemAttributeModifiers> attrType =
                Objects.requireNonNull(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                    "ATTRIBUTE_MODIFIERS component type cannot be null");

            @Nonnull net.minecraft.world.item.component.ItemAttributeModifiers mods = Objects.requireNonNull(
                safeMods(Objects.requireNonNull(item.getOrDefault(attrType, NONNULL_EMPTY))),
                "attribute modifiers component cannot be null");

            mods = mergeAttributeSets(mods, safeMods(item.getAttributeModifiers()));
            mods = mergeAttributeSets(mods, safeMods(item.getItem().getDefaultAttributeModifiers(item)));

            double armor = 0;
            double toughness = 0;
            double kb = 0;
            for (net.minecraft.world.item.component.ItemAttributeModifiers.Entry entry : mods.modifiers()) {
                if (slot != null && entry.slot() != null && !entry.slot().test(slot)) continue;
                var attrHolder = entry.attribute();
                var mod = entry.modifier();
                if (attrHolder == null || mod == null) continue;
                var attribute = attrHolder.value();
                if (attribute == null) continue;

                if (attribute == net.minecraft.world.entity.ai.attributes.Attributes.ARMOR.value()) {
                    armor += mod.amount();
                } else if (attribute == net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS.value()) {
                    toughness += mod.amount();
                } else if (attribute == net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE.value()) {
                    kb += mod.amount();
                }
            }

            if (armor != 0) {
                target.armorBonus = (float) armor;
                float reduction = Math.min(0.8f, (float) armor / 30f);
                target.physicalReduction = reduction;
                target.projectileReduction = reduction;
                DevMod.LOGGER.info("[Editor][Armor] Attr armor={} -> reduction set to {}", armor, reduction);
            }
            if (toughness != 0) target.toughnessBonus = (float) toughness;
            if (kb != 0) target.knockbackResistance = (float) kb;

            if (item.getItem() instanceof net.minecraft.world.item.ShieldItem) {
                target.shieldBlockStrength = 1.0f;
                target.shieldRecoverySpeed = 1.0f;
                DevMod.LOGGER.info("[Editor][Armor] Shield defaults applied: blockStrength=1, recovery=1");
            }
        } catch (Exception ignored) {
            // best-effort fallback
        }
    }

    @Nonnull
    private static net.minecraft.world.item.component.ItemAttributeModifiers safeMods(
        @Nullable net.minecraft.world.item.component.ItemAttributeModifiers mods
    ) {
        if (mods == null) {
            return NONNULL_EMPTY;
        }
        return Objects.requireNonNull(mods, "ItemAttributeModifiers cannot be null");
    }

    @Nonnull
    private static net.minecraft.world.item.component.ItemAttributeModifiers mergeAttributeSets(
        @Nonnull net.minecraft.world.item.component.ItemAttributeModifiers base,
        @Nullable net.minecraft.world.item.component.ItemAttributeModifiers extra
    ) {
        if (extra == null || extra == NONNULL_EMPTY || extra.modifiers().isEmpty()) {
            return base;
        }
        List<net.minecraft.world.item.component.ItemAttributeModifiers.Entry> merged =
            new ArrayList<>(base.modifiers());
        merged.addAll(extra.modifiers());
        boolean show = base.showInTooltip() || extra.showInTooltip();
        return new net.minecraft.world.item.component.ItemAttributeModifiers(merged, show);
    }

    // ═══════════════════════════════════════════════════════════════
    // CUSTOM DATA TAG
    // ═══════════════════════════════════════════════════════════════

    public CompoundTag getCustomDataTag(ItemStack item) {
        Objects.requireNonNull(item, "item cannot be null");
        CustomData customData = item.getOrDefault(
            Objects.requireNonNull(DataComponents.CUSTOM_DATA, "DataComponents.CUSTOM_DATA cannot be null"),
            Objects.requireNonNull(CustomData.EMPTY, "CustomData.EMPTY cannot be null")
        );
        CompoundTag tag = customData.copyTag();
        return tag == null ? new CompoundTag() : tag;
    }

    // ═══════════════════════════════════════════════════════════════
    // SOURCE DETERMINATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Determine the source badge type based on current data origin.
     */
    public SourceBadge.Source determineSource() {
        if (hasComponentData || hasGlobalConfig) {
            return SourceBadge.Source.DEV;
        }
        if (hasNbtData) {
            return SourceBadge.Source.NBT;
        }
        return SourceBadge.Source.VANILLA;
    }

    // ═══════════════════════════════════════════════════════════════
    // SERVER STATS RESOLUTION
    // ═══════════════════════════════════════════════════════════════

    public ArmorStats resolveServerStats(CompoundTag tag, ItemStack item) {
        if (tag.contains(NBT_KEY)) {
            return loadStatsFromTag(tag);
        }
        ArmorStats global = ArmorConfigManager.getGlobalStats(item.getItem());
        return global == null ? new ArmorStats() : global;
    }

    ArmorStats loadStatsFromTag(CompoundTag tag) {
        if (tag.contains(NBT_KEY)) {
            return ArmorStats.load(tag.getCompound(NBT_KEY));
        }
        return new ArmorStats();
    }

    // ═══════════════════════════════════════════════════════════════
    // EHP COMPUTATION
    // ═══════════════════════════════════════════════════════════════

    public float calculateEHP() {
        float totalReduction = stats.physicalReduction;
        float cappedReduction = Math.min(totalReduction, 0.8f);
        return 1f / (1f - cappedReduction);
    }

    // ═══════════════════════════════════════════════════════════════
    // STATE COMPARISON
    // ═══════════════════════════════════════════════════════════════

    public boolean hasPendingDiff() {
        return !statsEquals(stats, originalStats);
    }

    boolean statsEquals(ArmorStats a, ArmorStats b) {
        return Float.compare(a.physicalReduction, b.physicalReduction) == 0
            && Float.compare(a.fireReduction, b.fireReduction) == 0
            && Float.compare(a.magicReduction, b.magicReduction) == 0
            && Float.compare(a.explosionReduction, b.explosionReduction) == 0
            && Float.compare(a.projectileReduction, b.projectileReduction) == 0
            && Float.compare(a.armorBonus, b.armorBonus) == 0
            && Float.compare(a.toughnessBonus, b.toughnessBonus) == 0
            && Float.compare(a.knockbackResistance, b.knockbackResistance) == 0
            && Float.compare(a.thornsPercent, b.thornsPercent) == 0
            && a.thornsReflect == b.thornsReflect
            && Float.compare(a.shieldBlockStrength, b.shieldBlockStrength) == 0
            && Float.compare(a.shieldRecoverySpeed, b.shieldRecoverySpeed) == 0
            && a.shieldReflectProjectiles == b.shieldReflectProjectiles
            // Shield Visual
            && a.shieldColor == b.shieldColor
            && Float.compare(a.shieldOpacity, b.shieldOpacity) == 0
            && a.shieldGlowEnabled == b.shieldGlowEnabled
            && Float.compare(a.shieldGlowIntensity, b.shieldGlowIntensity) == 0
            && Float.compare(a.shieldNoiseIntensity, b.shieldNoiseIntensity) == 0
            && Float.compare(a.shieldPulseSpeed, b.shieldPulseSpeed) == 0
            // Shield Deflection
            && Float.compare(a.shieldDeflectionSpread, b.shieldDeflectionSpread) == 0
            && a.shieldDeflectToOwner == b.shieldDeflectToOwner
            && Float.compare(a.shieldDeflectSpeedMult, b.shieldDeflectSpeedMult) == 0
            // Shield Shatter
            && Float.compare(a.shieldShatterThreshold, b.shieldShatterThreshold) == 0
            && a.shieldAutoRegenerate == b.shieldAutoRegenerate
            && Float.compare(a.shieldRegenDelay, b.shieldRegenDelay) == 0;
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTERS & SETTERS
    // ═══════════════════════════════════════════════════════════════

    public ArmorStats getStats() {
        return stats;
    }

    public void setStats(ArmorStats stats) {
        this.stats = stats;
    }

    public ArmorStats getOriginalStats() {
        return originalStats;
    }

    public void setOriginalStats(ArmorStats originalStats) {
        this.originalStats = originalStats;
    }

    public boolean hasGlobalConfig() {
        return hasGlobalConfig;
    }
}
