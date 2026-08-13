package com.devmod.config.handler.impl;

import java.lang.reflect.Type;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.gson.reflect.TypeToken;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;

import com.devmod.components.ArmorComponents;
import com.devmod.config.component.BooleanComponent;
import com.devmod.config.component.FloatComponent;
import com.devmod.config.component.IntComponent;
import com.devmod.config.handler.AbstractConfigHandler;
import com.devmod.config.handler.DecomposedConfig;
import com.devmod.config.handler.IConfigComponent;
import com.devmod.config.handler.IDecomposedConfig;
import com.devmod.network.PacketValidator;
import com.devmod.stats.ArmorStats;
import com.devmod.stats.ShieldColors;

/**
 * Config handler for armor stats.
 * Provides decompose/recompose functionality with typed components.
 */
public class ArmorConfigHandler extends AbstractConfigHandler<ArmorStats> {

    /** Singleton instance */
    public static final ArmorConfigHandler INSTANCE = new ArmorConfigHandler();

    // ═══════════════════════════════════════════════════════════════
    // COMPONENT DEFINITIONS
    // ═══════════════════════════════════════════════════════════════

    // Damage type reductions
    public static final FloatComponent PHYSICAL_REDUCTION = new FloatComponent(
        "physicalReduction", "Physical Reduction", "Damage Reduction", 0.0f, 0.0f, 1.0f);
    public static final FloatComponent FIRE_REDUCTION = new FloatComponent(
        "fireReduction", "Fire Reduction", "Damage Reduction", 0.0f, 0.0f, 1.0f);
    public static final FloatComponent MAGIC_REDUCTION = new FloatComponent(
        "magicReduction", "Magic Reduction", "Damage Reduction", 0.0f, 0.0f, 1.0f);
    public static final FloatComponent EXPLOSION_REDUCTION = new FloatComponent(
        "explosionReduction", "Explosion Reduction", "Damage Reduction", 0.0f, 0.0f, 1.0f);
    public static final FloatComponent PROJECTILE_REDUCTION = new FloatComponent(
        "projectileReduction", "Projectile Reduction", "Damage Reduction", 0.0f, 0.0f, 1.0f);

    // Vanilla stat modifiers
    public static final FloatComponent ARMOR_BONUS = new FloatComponent(
        "armorBonus", "Armor Bonus", "Stats", 0.0f, 0.0f, 30.0f);
    public static final FloatComponent TOUGHNESS_BONUS = new FloatComponent(
        "toughnessBonus", "Toughness Bonus", "Stats", 0.0f, 0.0f, 20.0f);
    public static final FloatComponent KNOCKBACK_RESISTANCE = new FloatComponent(
        "knockbackResistance", "Knockback Resistance", "Stats", 0.0f, 0.0f, 1.0f);

    // Special effects
    public static final BooleanComponent THORNS_REFLECT = new BooleanComponent(
        "thornsReflect", "Thorns Reflect", "Effects", false);
    public static final FloatComponent THORNS_PERCENT = new FloatComponent(
        "thornsPercent", "Thorns Percent", "Effects", 0.0f, 0.0f, 0.5f);

    // Shield mechanics
    public static final BooleanComponent SHIELD_REFLECT_PROJECTILES = new BooleanComponent(
        "shieldReflectProjectiles", "Reflect Projectiles", "Shield", false);
    public static final FloatComponent SHIELD_BLOCK_STRENGTH = new FloatComponent(
        "shieldBlockStrength", "Block Strength", "Shield", 0.5f, 0.0f, 1.0f);
    public static final FloatComponent SHIELD_RECOVERY_SPEED = new FloatComponent(
        "shieldRecoverySpeed", "Recovery Speed", "Shield", 1.0f, 0.0f, 2.0f);

    // Shield visual settings
    public static final IntComponent SHIELD_COLOR = new IntComponent(
        "shieldColor", "Shield Color", "Shield Visual", ShieldColors.DEFAULT, 0, 0xFFFFFF);
    public static final FloatComponent SHIELD_OPACITY = new FloatComponent(
        "shieldOpacity", "Shield Opacity", "Shield Visual", 0.6f, 0.0f, 1.0f);
    public static final BooleanComponent SHIELD_GLOW_ENABLED = new BooleanComponent(
        "shieldGlowEnabled", "Glow Enabled", "Shield Visual", true);
    public static final FloatComponent SHIELD_GLOW_INTENSITY = new FloatComponent(
        "shieldGlowIntensity", "Glow Intensity", "Shield Visual", 1.0f, 0.0f, 2.0f);
    public static final FloatComponent SHIELD_NOISE_INTENSITY = new FloatComponent(
        "shieldNoiseIntensity", "Noise Intensity", "Shield Visual", 0.15f, 0.0f, 0.5f);
    public static final FloatComponent SHIELD_PULSE_SPEED = new FloatComponent(
        "shieldPulseSpeed", "Pulse Speed", "Shield Visual", 1.0f, 0.5f, 2.0f);

    // Shield deflection settings
    public static final FloatComponent SHIELD_DEFLECTION_SPREAD = new FloatComponent(
        "shieldDeflectionSpread", "Deflection Spread", "Shield Deflect", 0.15f, 0.0f, 1.0f);
    public static final BooleanComponent SHIELD_DEFLECT_TO_OWNER = new BooleanComponent(
        "shieldDeflectToOwner", "Deflect to Owner", "Shield Deflect", false);
    public static final FloatComponent SHIELD_DEFLECT_SPEED_MULT = new FloatComponent(
        "shieldDeflectSpeedMult", "Deflect Speed Mult", "Shield Deflect", 0.8f, 0.5f, 1.5f);

    // Shield shatter settings
    public static final FloatComponent SHIELD_SHATTER_THRESHOLD = new FloatComponent(
        "shieldShatterThreshold", "Shatter Threshold", "Shield Shatter", 10.0f, 0.0f, 100.0f);
    public static final BooleanComponent SHIELD_AUTO_REGENERATE = new BooleanComponent(
        "shieldAutoRegenerate", "Auto Regenerate", "Shield Shatter", true);
    public static final FloatComponent SHIELD_REGEN_DELAY = new FloatComponent(
        "shieldRegenDelay", "Regen Delay", "Shield Shatter", 3.0f, 0.0f, 30.0f);

    private static final Set<IConfigComponent<?>> ALL_COMPONENTS;

    static {
        ALL_COMPONENTS = new LinkedHashSet<>();
        // Damage Reduction
        ALL_COMPONENTS.add(PHYSICAL_REDUCTION);
        ALL_COMPONENTS.add(FIRE_REDUCTION);
        ALL_COMPONENTS.add(MAGIC_REDUCTION);
        ALL_COMPONENTS.add(EXPLOSION_REDUCTION);
        ALL_COMPONENTS.add(PROJECTILE_REDUCTION);
        // Stats
        ALL_COMPONENTS.add(ARMOR_BONUS);
        ALL_COMPONENTS.add(TOUGHNESS_BONUS);
        ALL_COMPONENTS.add(KNOCKBACK_RESISTANCE);
        // Effects
        ALL_COMPONENTS.add(THORNS_REFLECT);
        ALL_COMPONENTS.add(THORNS_PERCENT);
        // Shield
        ALL_COMPONENTS.add(SHIELD_REFLECT_PROJECTILES);
        ALL_COMPONENTS.add(SHIELD_BLOCK_STRENGTH);
        ALL_COMPONENTS.add(SHIELD_RECOVERY_SPEED);
        // Shield Visual
        ALL_COMPONENTS.add(SHIELD_COLOR);
        ALL_COMPONENTS.add(SHIELD_OPACITY);
        ALL_COMPONENTS.add(SHIELD_GLOW_ENABLED);
        ALL_COMPONENTS.add(SHIELD_GLOW_INTENSITY);
        ALL_COMPONENTS.add(SHIELD_NOISE_INTENSITY);
        ALL_COMPONENTS.add(SHIELD_PULSE_SPEED);
        // Shield Deflect
        ALL_COMPONENTS.add(SHIELD_DEFLECTION_SPREAD);
        ALL_COMPONENTS.add(SHIELD_DEFLECT_TO_OWNER);
        ALL_COMPONENTS.add(SHIELD_DEFLECT_SPEED_MULT);
        // Shield Shatter
        ALL_COMPONENTS.add(SHIELD_SHATTER_THRESHOLD);
        ALL_COMPONENTS.add(SHIELD_AUTO_REGENERATE);
        ALL_COMPONENTS.add(SHIELD_REGEN_DELAY);
    }

    public ArmorConfigHandler() {
        super("armor_configs.json", "ArmorModStats");
    }

    // ═══════════════════════════════════════════════════════════════
    // ABSTRACT METHOD IMPLEMENTATIONS
    // ═══════════════════════════════════════════════════════════════

    @Override
    public Class<ArmorStats> getStatsClass() {
        return ArmorStats.class;
    }

    @Override
    @Nullable
    protected DataComponentType<CompoundTag> getComponentType() {
        try {
            return ArmorComponents.armorStatsComponent();
        } catch (IllegalStateException e) {
            return null;
        }
    }

    @Override
    protected ArmorStats fromTag(CompoundTag tag) {
        return ArmorStats.fromTag(tag);
    }

    @Override
    protected Type getMapType() {
        return new TypeToken<Map<String, ArmorStats>>(){}.getType();
    }

    @Override
    public ArmorStats createDefault() {
        return new ArmorStats();
    }

    @Override
    public boolean appliesTo(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem() instanceof ArmorItem || stack.getItem() instanceof ShieldItem;
    }

    @Override
    public Set<IConfigComponent<?>> getComponents() {
        return ALL_COMPONENTS;
    }

    // ═══════════════════════════════════════════════════════════════
    // VALIDATION
    // ═══════════════════════════════════════════════════════════════

    @Override
    public ArmorStats validateAndClamp(ArmorStats input) {
        if (input == null) return createDefault();

        ArmorStats stats = input.copy();
        PacketValidator security = PacketValidator.INSTANCE;

        // Damage reductions
        stats.setPhysicalReduction((float) security.validateArmorReduction(stats.getPhysicalReduction()));
        stats.setFireReduction((float) security.validateArmorReduction(stats.getFireReduction()));
        stats.setMagicReduction((float) security.validateArmorReduction(stats.getMagicReduction()));
        stats.setExplosionReduction((float) security.validateArmorReduction(stats.getExplosionReduction()));
        stats.setProjectileReduction((float) security.validateArmorReduction(stats.getProjectileReduction()));

        // Vanilla stats
        stats.setArmorBonus((float) security.validateArmorBonus(stats.getArmorBonus()));
        stats.setToughnessBonus((float) security.validateToughnessBonus(stats.getToughnessBonus()));
        stats.setKnockbackResistance((float) security.validateKnockbackResistance(stats.getKnockbackResistance()));

        // Thorns
        stats.setThornsPercent((float) security.validateThornsPercent(stats.getThornsPercent()));

        // Shield
        stats.setShieldBlockStrength((float) security.validateShieldBlock(stats.getShieldBlockStrength()));
        stats.setShieldRecoverySpeed((float) security.validateShieldRecovery(stats.getShieldRecoverySpeed()));

        // Shield visual (use component clamp for values without PacketValidator methods)
        stats.setShieldColor(SHIELD_COLOR.clamp(stats.getShieldColor()));
        stats.setShieldOpacity(SHIELD_OPACITY.clamp(stats.getShieldOpacity()));
        stats.setShieldGlowIntensity(SHIELD_GLOW_INTENSITY.clamp(stats.getShieldGlowIntensity()));
        stats.setShieldNoiseIntensity(SHIELD_NOISE_INTENSITY.clamp(stats.getShieldNoiseIntensity()));
        stats.setShieldPulseSpeed(SHIELD_PULSE_SPEED.clamp(stats.getShieldPulseSpeed()));

        // Shield deflect
        stats.setShieldDeflectionSpread(SHIELD_DEFLECTION_SPREAD.clamp(stats.getShieldDeflectionSpread()));
        stats.setShieldDeflectSpeedMult(SHIELD_DEFLECT_SPEED_MULT.clamp(stats.getShieldDeflectSpeedMult()));

        // Shield shatter
        stats.setShieldShatterThreshold(SHIELD_SHATTER_THRESHOLD.clamp(stats.getShieldShatterThreshold()));
        stats.setShieldRegenDelay(SHIELD_REGEN_DELAY.clamp(stats.getShieldRegenDelay()));

        return stats;
    }

    // ═══════════════════════════════════════════════════════════════
    // DECOMPOSE/RECOMPOSE
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void populateDecomposed(DecomposedConfig config, ArmorStats stats) {
        // Damage Reduction
        config.set(PHYSICAL_REDUCTION, stats.getPhysicalReduction());
        config.set(FIRE_REDUCTION, stats.getFireReduction());
        config.set(MAGIC_REDUCTION, stats.getMagicReduction());
        config.set(EXPLOSION_REDUCTION, stats.getExplosionReduction());
        config.set(PROJECTILE_REDUCTION, stats.getProjectileReduction());

        // Stats
        config.set(ARMOR_BONUS, stats.getArmorBonus());
        config.set(TOUGHNESS_BONUS, stats.getToughnessBonus());
        config.set(KNOCKBACK_RESISTANCE, stats.getKnockbackResistance());

        // Effects
        config.set(THORNS_REFLECT, stats.isThornsReflect());
        config.set(THORNS_PERCENT, stats.getThornsPercent());

        // Shield
        config.set(SHIELD_REFLECT_PROJECTILES, stats.isShieldReflectProjectiles());
        config.set(SHIELD_BLOCK_STRENGTH, stats.getShieldBlockStrength());
        config.set(SHIELD_RECOVERY_SPEED, stats.getShieldRecoverySpeed());

        // Shield Visual
        config.set(SHIELD_COLOR, stats.getShieldColor());
        config.set(SHIELD_OPACITY, stats.getShieldOpacity());
        config.set(SHIELD_GLOW_ENABLED, stats.isShieldGlowEnabled());
        config.set(SHIELD_GLOW_INTENSITY, stats.getShieldGlowIntensity());
        config.set(SHIELD_NOISE_INTENSITY, stats.getShieldNoiseIntensity());
        config.set(SHIELD_PULSE_SPEED, stats.getShieldPulseSpeed());

        // Shield Deflect
        config.set(SHIELD_DEFLECTION_SPREAD, stats.getShieldDeflectionSpread());
        config.set(SHIELD_DEFLECT_TO_OWNER, stats.isShieldDeflectToOwner());
        config.set(SHIELD_DEFLECT_SPEED_MULT, stats.getShieldDeflectSpeedMult());

        // Shield Shatter
        config.set(SHIELD_SHATTER_THRESHOLD, stats.getShieldShatterThreshold());
        config.set(SHIELD_AUTO_REGENERATE, stats.isShieldAutoRegenerate());
        config.set(SHIELD_REGEN_DELAY, stats.getShieldRegenDelay());
    }

    @Override
    protected void applyFromDecomposed(IDecomposedConfig config, ArmorStats stats) {
        // Damage Reduction
        config.get(PHYSICAL_REDUCTION).ifPresent(stats::setPhysicalReduction);
        config.get(FIRE_REDUCTION).ifPresent(stats::setFireReduction);
        config.get(MAGIC_REDUCTION).ifPresent(stats::setMagicReduction);
        config.get(EXPLOSION_REDUCTION).ifPresent(stats::setExplosionReduction);
        config.get(PROJECTILE_REDUCTION).ifPresent(stats::setProjectileReduction);

        // Stats
        config.get(ARMOR_BONUS).ifPresent(stats::setArmorBonus);
        config.get(TOUGHNESS_BONUS).ifPresent(stats::setToughnessBonus);
        config.get(KNOCKBACK_RESISTANCE).ifPresent(stats::setKnockbackResistance);

        // Effects
        config.get(THORNS_REFLECT).ifPresent(stats::setThornsReflect);
        config.get(THORNS_PERCENT).ifPresent(stats::setThornsPercent);

        // Shield
        config.get(SHIELD_REFLECT_PROJECTILES).ifPresent(stats::setShieldReflectProjectiles);
        config.get(SHIELD_BLOCK_STRENGTH).ifPresent(stats::setShieldBlockStrength);
        config.get(SHIELD_RECOVERY_SPEED).ifPresent(stats::setShieldRecoverySpeed);

        // Shield Visual
        config.get(SHIELD_COLOR).ifPresent(stats::setShieldColor);
        config.get(SHIELD_OPACITY).ifPresent(stats::setShieldOpacity);
        config.get(SHIELD_GLOW_ENABLED).ifPresent(stats::setShieldGlowEnabled);
        config.get(SHIELD_GLOW_INTENSITY).ifPresent(stats::setShieldGlowIntensity);
        config.get(SHIELD_NOISE_INTENSITY).ifPresent(stats::setShieldNoiseIntensity);
        config.get(SHIELD_PULSE_SPEED).ifPresent(stats::setShieldPulseSpeed);

        // Shield Deflect
        config.get(SHIELD_DEFLECTION_SPREAD).ifPresent(stats::setShieldDeflectionSpread);
        config.get(SHIELD_DEFLECT_TO_OWNER).ifPresent(stats::setShieldDeflectToOwner);
        config.get(SHIELD_DEFLECT_SPEED_MULT).ifPresent(stats::setShieldDeflectSpeedMult);

        // Shield Shatter
        config.get(SHIELD_SHATTER_THRESHOLD).ifPresent(stats::setShieldShatterThreshold);
        config.get(SHIELD_AUTO_REGENERATE).ifPresent(stats::setShieldAutoRegenerate);
        config.get(SHIELD_REGEN_DELAY).ifPresent(stats::setShieldRegenDelay);
    }

    // ═══════════════════════════════════════════════════════════════
    // STATIC COMPATIBILITY API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if an item is armor (ArmorItem or ShieldItem).
     */
    public static boolean isArmor(ItemStack stack) {
        return INSTANCE.appliesTo(stack);
    }

    /**
     * Check if an item has global config.
     */
    public static boolean hasGlobalConfig(Item item) {
        return item != null && INSTANCE.globalStats.containsKey(item);
    }

    /**
     * Get global stats for an item (static version).
     */
    public static ArmorStats getItemGlobalStats(Item item) {
        if (item == null) return new ArmorStats();
        ArmorStats stats = INSTANCE.globalStats.get(item);
        return stats != null ? stats.copy() : new ArmorStats();
    }

    /**
     * Get all global stats as an immutable map (static version).
     */
    public static Map<Item, ArmorStats> getEveryGlobalStats() {
        return INSTANCE.getAllGlobalStats();
    }

    /**
     * Set global stats without saving to disk (for client sync).
     */
    public static void setGlobalStatsClientOnly(Item item, ArmorStats stats) {
        if (item == null || stats == null) return;
        INSTANCE.globalStats.put(item, stats.copy());
    }

    /**
     * Clamp stats values to valid ranges.
     */
    public static ArmorStats clampStats(ArmorStats stats) {
        return INSTANCE.validateAndClamp(stats);
    }

    /**
     * Clear specific stats from an item stack (static version).
     */
    public static void clearItemSpecificStats(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        INSTANCE.clearSpecificStats(stack);
    }
}
