package com.devmod.config.handler.impl;

import java.lang.reflect.Type;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.gson.reflect.TypeToken;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

import com.devmod.components.WeaponComponents;
import com.devmod.config.component.BooleanComponent;
import com.devmod.config.component.FloatComponent;
import com.devmod.config.component.IntComponent;
import com.devmod.config.handler.AbstractConfigHandler;
import com.devmod.config.handler.DecomposedConfig;
import com.devmod.config.handler.IConfigComponent;
import com.devmod.config.handler.IDecomposedConfig;
import com.devmod.network.PacketValidator;
import com.devmod.stats.WeaponStats;

import net.minecraft.world.item.Item;
import java.util.Collections;

/**
 * Config handler for weapon stats.
 * Provides decompose/recompose functionality with typed components.
 */
public class WeaponConfigHandler extends AbstractConfigHandler<WeaponStats> {

    /** Singleton instance */
    public static final WeaponConfigHandler INSTANCE = new WeaponConfigHandler();

    // ═══════════════════════════════════════════════════════════════
    // COMPONENT DEFINITIONS
    // ═══════════════════════════════════════════════════════════════

    // Hit location multipliers
    public static final FloatComponent HEAD_MULT = new FloatComponent(
        "headMult", "Head Multiplier", "Hit Location", 1.5f, 0.0f, 10.0f);
    public static final FloatComponent BODY_MULT = new FloatComponent(
        "bodyMult", "Body Multiplier", "Hit Location", 1.0f, 0.0f, 10.0f);
    public static final FloatComponent ARMS_MULT = new FloatComponent(
        "armsMult", "Arms Multiplier", "Hit Location", 0.8f, 0.0f, 10.0f);
    public static final FloatComponent LEGS_MULT = new FloatComponent(
        "legsMult", "Legs Multiplier", "Hit Location", 0.7f, 0.0f, 10.0f);

    // Core combat stats
    public static final FloatComponent ATTACK_DAMAGE = new FloatComponent(
        "attackDamage", "Attack Damage", "Combat", 0.0f, 0.0f, 1000.0f);
    public static final FloatComponent ATTACK_SPEED = new FloatComponent(
        "attackSpeed", "Attack Speed", "Combat", 0.0f, -4.0f, 100.0f);
    public static final FloatComponent ATTACK_REACH = new FloatComponent(
        "attackReach", "Attack Reach", "Combat", 0.0f, 0.0f, 100.0f);
    public static final FloatComponent ATTACK_KNOCKBACK = new FloatComponent(
        "attackKnockback", "Attack Knockback", "Combat", 0.0f, 0.0f, 100.0f);
    public static final FloatComponent ARMOR_PENETRATION = new FloatComponent(
        "armorPenetration", "Armor Penetration", "Combat", 0.0f, 0.0f, 1.0f);
    public static final FloatComponent BASE_DAMAGE_BONUS = new FloatComponent(
        "baseDamageBonus", "Base Damage Bonus", "Combat", 0.0f, 0.0f, 1000.0f);
    public static final FloatComponent DAMAGE_BONUS = new FloatComponent(
        "damageBonus", "Damage Bonus", "Combat", 0.0f, 0.0f, 1.0f);
    public static final FloatComponent SWEEPING_RATIO = new FloatComponent(
        "sweepingRatio", "Sweeping Ratio", "Combat", 0.0f, 0.0f, 1.0f);

    // Critical hit
    public static final FloatComponent CRIT_CHANCE = new FloatComponent(
        "critChance", "Crit Chance", "Critical", 0.0f, 0.0f, 1.0f);
    public static final FloatComponent CRIT_DAMAGE = new FloatComponent(
        "critDamage", "Crit Damage", "Critical", 1.5f, 0.0f, 100.0f);
    public static final FloatComponent ARMOR_SHRED = new FloatComponent(
        "armorShred", "Armor Shred", "Critical", 0.0f, 0.0f, 66.0f);

    // Damage type bonuses
    public static final FloatComponent FIRE_DAMAGE_BONUS = new FloatComponent(
        "fireDamageBonus", "Fire Damage", "Elemental", 0.0f, 0.0f, 100.0f);
    public static final FloatComponent MAGIC_DAMAGE_BONUS = new FloatComponent(
        "magicDamageBonus", "Magic Damage", "Elemental", 0.0f, 0.0f, 100.0f);
    public static final FloatComponent LIFESTEAL = new FloatComponent(
        "lifesteal", "Lifesteal", "Elemental", 0.0f, 0.0f, 1.0f);
    public static final FloatComponent DAMAGE_VS_UNDEAD = new FloatComponent(
        "damageVsUndead", "Vs Undead", "Elemental", 0.0f, 0.0f, 2.0f);
    public static final FloatComponent DAMAGE_VS_ARTHROPODS = new FloatComponent(
        "damageVsArthropods", "Vs Arthropods", "Elemental", 0.0f, 0.0f, 2.0f);
    public static final FloatComponent DAMAGE_VS_PLAYERS = new FloatComponent(
        "damageVsPlayers", "Vs Players", "Elemental", 0.0f, 0.0f, 2.0f);
    public static final FloatComponent TRUE_DAMAGE_PERCENT = new FloatComponent(
        "trueDamagePercent", "True Damage %", "Elemental", 0.0f, 0.0f, 1.0f);

    // Durability
    public static final IntComponent MAX_DURABILITY = new IntComponent(
        "maxDurability", "Max Durability", "Durability", 0, 0, 100000);
    public static final IntComponent CURRENT_DAMAGE = new IntComponent(
        "currentDamage", "Current Damage", "Durability", 0, 0, 100000);
    public static final IntComponent REPAIR_COST = new IntComponent(
        "repairCost", "Repair Cost", "Durability", 0, 0, 1000);
    public static final BooleanComponent UNBREAKABLE = new BooleanComponent(
        "unbreakable", "Unbreakable", "Durability", false);

    // Tool properties
    public static final BooleanComponent CLEAR_TOOL_RULES = new BooleanComponent(
        "clearToolRules", "Clear Tool Rules", "Tool", false);
    public static final FloatComponent TOOL_DEFAULT_MINING_SPEED = new FloatComponent(
        "toolDefaultMiningSpeed", "Mining Speed", "Tool", 1.0f, 0.0f, 100.0f);
    public static final IntComponent TOOL_DAMAGE_PER_BLOCK = new IntComponent(
        "toolDamagePerBlock", "Damage Per Block", "Tool", 1, 0, 100);

    private static final Set<IConfigComponent<?>> ALL_COMPONENTS;

    static {
        ALL_COMPONENTS = new LinkedHashSet<>();
        // Hit location
        ALL_COMPONENTS.add(HEAD_MULT);
        ALL_COMPONENTS.add(BODY_MULT);
        ALL_COMPONENTS.add(ARMS_MULT);
        ALL_COMPONENTS.add(LEGS_MULT);
        // Combat
        ALL_COMPONENTS.add(ATTACK_DAMAGE);
        ALL_COMPONENTS.add(ATTACK_SPEED);
        ALL_COMPONENTS.add(ATTACK_REACH);
        ALL_COMPONENTS.add(ATTACK_KNOCKBACK);
        ALL_COMPONENTS.add(ARMOR_PENETRATION);
        ALL_COMPONENTS.add(BASE_DAMAGE_BONUS);
        ALL_COMPONENTS.add(DAMAGE_BONUS);
        ALL_COMPONENTS.add(SWEEPING_RATIO);
        // Critical
        ALL_COMPONENTS.add(CRIT_CHANCE);
        ALL_COMPONENTS.add(CRIT_DAMAGE);
        ALL_COMPONENTS.add(ARMOR_SHRED);
        // Elemental
        ALL_COMPONENTS.add(FIRE_DAMAGE_BONUS);
        ALL_COMPONENTS.add(MAGIC_DAMAGE_BONUS);
        ALL_COMPONENTS.add(LIFESTEAL);
        ALL_COMPONENTS.add(DAMAGE_VS_UNDEAD);
        ALL_COMPONENTS.add(DAMAGE_VS_ARTHROPODS);
        ALL_COMPONENTS.add(DAMAGE_VS_PLAYERS);
        ALL_COMPONENTS.add(TRUE_DAMAGE_PERCENT);
        // Durability
        ALL_COMPONENTS.add(MAX_DURABILITY);
        ALL_COMPONENTS.add(CURRENT_DAMAGE);
        ALL_COMPONENTS.add(REPAIR_COST);
        ALL_COMPONENTS.add(UNBREAKABLE);
        // Tool
        ALL_COMPONENTS.add(CLEAR_TOOL_RULES);
        ALL_COMPONENTS.add(TOOL_DEFAULT_MINING_SPEED);
        ALL_COMPONENTS.add(TOOL_DAMAGE_PER_BLOCK);
    }

    public WeaponConfigHandler() {
        super("weapon_configs.json", "WeaponModStats");
    }

    // ═══════════════════════════════════════════════════════════════
    // ABSTRACT METHOD IMPLEMENTATIONS
    // ═══════════════════════════════════════════════════════════════

    @Override
    public Class<WeaponStats> getStatsClass() {
        return WeaponStats.class;
    }

    @Override
    @Nullable
    protected DataComponentType<CompoundTag> getComponentType() {
        return WeaponComponents.weaponStatsComponent();
    }

    @Override
    protected WeaponStats fromTag(CompoundTag tag) {
        return WeaponStats.fromTag(tag);
    }

    @Override
    protected Type getMapType() {
        return new TypeToken<Map<String, WeaponStats>>(){}.getType();
    }

    @Override
    public WeaponStats createDefault() {
        return new WeaponStats();
    }

    @Override
    public boolean appliesTo(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        // Weapon items: swords, axes, tridents, or items with attack damage attribute
        return stack.getItem() instanceof SwordItem
            || stack.getItem() instanceof AxeItem
            || stack.getItem() instanceof TridentItem
            || stack.has(DataComponents.ATTRIBUTE_MODIFIERS);
    }

    @Override
    public Set<IConfigComponent<?>> getComponents() {
        return ALL_COMPONENTS;
    }

    // ═══════════════════════════════════════════════════════════════
    // VALIDATION
    // ═══════════════════════════════════════════════════════════════

    @Override
    public WeaponStats validateAndClamp(WeaponStats stats) {
        if (stats == null) return createDefault();

        PacketValidator security = PacketValidator.INSTANCE;

        // Hit location multipliers (clamp to component ranges)
        stats.setHeadMult(HEAD_MULT.clamp(stats.getHeadMult()));
        stats.setBodyMult(BODY_MULT.clamp(stats.getBodyMult()));
        stats.setArmsMult(ARMS_MULT.clamp(stats.getArmsMult()));
        stats.setLegsMult(LEGS_MULT.clamp(stats.getLegsMult()));

        // Combat stats (use PacketValidator security limits)
        stats.setAttackDamage((float) security.validateDamage(stats.getAttackDamage()));
        stats.setAttackSpeed((float) security.validateAttackSpeed(stats.getAttackSpeed()));
        stats.setAttackReach((float) security.validateMultiplier(stats.getAttackReach()));
        stats.setAttackKnockback((float) security.validateMultiplier(stats.getAttackKnockback()));
        stats.setArmorPenetration((float) security.validatePenetration(stats.getArmorPenetration()));
        stats.setBaseDamageBonus((float) security.validateDamage(stats.getBaseDamageBonus()));
        stats.setDamageBonus((float) security.validateSweeping(stats.getDamageBonus()));
        stats.setSweepingRatio((float) security.validateSweeping(stats.getSweepingRatio()));

        // Critical stats
        stats.setCritChance((float) security.validateMultiplier(stats.getCritChance()));
        stats.setCritDamage((float) security.validateMultiplier(stats.getCritDamage()));
        stats.setArmorShred((float) security.validateArmorShred(stats.getArmorShred()));

        // Elemental stats
        stats.setFireDamageBonus((float) security.validateMultiplier(stats.getFireDamageBonus()));
        stats.setMagicDamageBonus((float) security.validateMultiplier(stats.getMagicDamageBonus()));
        stats.setLifesteal((float) security.validateMultiplier(stats.getLifesteal()));
        stats.setDamageVsUndead((float) security.validateDamageVs(stats.getDamageVsUndead()));
        stats.setDamageVsArthropods((float) security.validateDamageVs(stats.getDamageVsArthropods()));
        stats.setDamageVsPlayers((float) security.validateDamageVs(stats.getDamageVsPlayers()));
        stats.setTrueDamagePercent((float) security.validateTrueDamage(stats.getTrueDamagePercent()));

        // Durability
        stats.setMaxDurability(security.validateDurability(stats.getMaxDurability()));
        stats.setCurrentDamage(security.validateDurability(stats.getCurrentDamage()));
        stats.setRepairCost(security.validateRepairCost(stats.getRepairCost()));

        // Tool
        stats.setToolDefaultMiningSpeed((float) security.validateToolSpeed(stats.getToolDefaultMiningSpeed()));
        stats.setToolDamagePerBlock(security.validateToolDamagePerBlock(stats.getToolDamagePerBlock()));

        // Custom attribute modifiers
        var customModifiers = stats.getCustomAttributeModifiers();
        if (!customModifiers.isEmpty()) {
            customModifiers.removeIf(Objects::isNull);
            for (var data : customModifiers) {
                if (data.attributeId != null) {
                    data.attributeId = data.attributeId.trim();
                }
                data.amount = security.validateAttributeModifier(data.amount);
                if (data.operation < 0 || data.operation > 2) {
                    data.operation = 0;
                }
            }
        }

        return stats;
    }

    // ═══════════════════════════════════════════════════════════════
    // DECOMPOSE/RECOMPOSE
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void populateDecomposed(DecomposedConfig config, WeaponStats stats) {
        // Hit location
        config.set(HEAD_MULT, stats.getHeadMult());
        config.set(BODY_MULT, stats.getBodyMult());
        config.set(ARMS_MULT, stats.getArmsMult());
        config.set(LEGS_MULT, stats.getLegsMult());

        // Combat
        config.set(ATTACK_DAMAGE, stats.getAttackDamage());
        config.set(ATTACK_SPEED, stats.getAttackSpeed());
        config.set(ATTACK_REACH, stats.getAttackReach());
        config.set(ATTACK_KNOCKBACK, stats.getAttackKnockback());
        config.set(ARMOR_PENETRATION, stats.getArmorPenetration());
        config.set(BASE_DAMAGE_BONUS, stats.getBaseDamageBonus());
        config.set(DAMAGE_BONUS, stats.getDamageBonus());
        config.set(SWEEPING_RATIO, stats.getSweepingRatio());

        // Critical
        config.set(CRIT_CHANCE, stats.getCritChance());
        config.set(CRIT_DAMAGE, stats.getCritDamage());
        config.set(ARMOR_SHRED, stats.getArmorShred());

        // Elemental
        config.set(FIRE_DAMAGE_BONUS, stats.getFireDamageBonus());
        config.set(MAGIC_DAMAGE_BONUS, stats.getMagicDamageBonus());
        config.set(LIFESTEAL, stats.getLifesteal());
        config.set(DAMAGE_VS_UNDEAD, stats.getDamageVsUndead());
        config.set(DAMAGE_VS_ARTHROPODS, stats.getDamageVsArthropods());
        config.set(DAMAGE_VS_PLAYERS, stats.getDamageVsPlayers());
        config.set(TRUE_DAMAGE_PERCENT, stats.getTrueDamagePercent());

        // Durability
        config.set(MAX_DURABILITY, stats.getMaxDurability());
        config.set(CURRENT_DAMAGE, stats.getCurrentDamage());
        config.set(REPAIR_COST, stats.getRepairCost());
        config.set(UNBREAKABLE, stats.isUnbreakable());

        // Tool
        config.set(CLEAR_TOOL_RULES, stats.isClearToolRules());
        config.set(TOOL_DEFAULT_MINING_SPEED, stats.getToolDefaultMiningSpeed());
        config.set(TOOL_DAMAGE_PER_BLOCK, stats.getToolDamagePerBlock());
    }

    @Override
    protected void applyFromDecomposed(IDecomposedConfig config, WeaponStats stats) {
        // Hit location
        config.get(HEAD_MULT).ifPresent(stats::setHeadMult);
        config.get(BODY_MULT).ifPresent(stats::setBodyMult);
        config.get(ARMS_MULT).ifPresent(stats::setArmsMult);
        config.get(LEGS_MULT).ifPresent(stats::setLegsMult);

        // Combat
        config.get(ATTACK_DAMAGE).ifPresent(stats::setAttackDamage);
        config.get(ATTACK_SPEED).ifPresent(stats::setAttackSpeed);
        config.get(ATTACK_REACH).ifPresent(stats::setAttackReach);
        config.get(ATTACK_KNOCKBACK).ifPresent(stats::setAttackKnockback);
        config.get(ARMOR_PENETRATION).ifPresent(stats::setArmorPenetration);
        config.get(BASE_DAMAGE_BONUS).ifPresent(stats::setBaseDamageBonus);
        config.get(DAMAGE_BONUS).ifPresent(stats::setDamageBonus);
        config.get(SWEEPING_RATIO).ifPresent(stats::setSweepingRatio);

        // Critical
        config.get(CRIT_CHANCE).ifPresent(stats::setCritChance);
        config.get(CRIT_DAMAGE).ifPresent(stats::setCritDamage);
        config.get(ARMOR_SHRED).ifPresent(stats::setArmorShred);

        // Elemental
        config.get(FIRE_DAMAGE_BONUS).ifPresent(stats::setFireDamageBonus);
        config.get(MAGIC_DAMAGE_BONUS).ifPresent(stats::setMagicDamageBonus);
        config.get(LIFESTEAL).ifPresent(stats::setLifesteal);
        config.get(DAMAGE_VS_UNDEAD).ifPresent(stats::setDamageVsUndead);
        config.get(DAMAGE_VS_ARTHROPODS).ifPresent(stats::setDamageVsArthropods);
        config.get(DAMAGE_VS_PLAYERS).ifPresent(stats::setDamageVsPlayers);
        config.get(TRUE_DAMAGE_PERCENT).ifPresent(stats::setTrueDamagePercent);

        // Durability
        config.get(MAX_DURABILITY).ifPresent(stats::setMaxDurability);
        config.get(CURRENT_DAMAGE).ifPresent(stats::setCurrentDamage);
        config.get(REPAIR_COST).ifPresent(stats::setRepairCost);
        config.get(UNBREAKABLE).ifPresent(stats::setUnbreakable);

        // Tool
        config.get(CLEAR_TOOL_RULES).ifPresent(stats::setClearToolRules);
        config.get(TOOL_DEFAULT_MINING_SPEED).ifPresent(stats::setToolDefaultMiningSpeed);
        config.get(TOOL_DAMAGE_PER_BLOCK).ifPresent(stats::setToolDamagePerBlock);
    }

    // ═══════════════════════════════════════════════════════════════
    // STATIC COMPATIBILITY API (for legacy code migration)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if an item is a weapon.
     */
    public static boolean isWeapon(ItemStack stack) {
        return INSTANCE.appliesTo(stack);
    }

    /**
     * Get global stats for a specific item type.
     * Returns null if no global config exists.
     */
    @Nullable
    public static WeaponStats getItemGlobalStats(Item item) {
        if (item == null) return null;
        return INSTANCE.globalStats.get(item);
    }

    /**
     * Get all global stats as an unmodifiable map.
     */
    public static Map<Item, WeaponStats> getEveryGlobalStats() {
        return Collections.unmodifiableMap(INSTANCE.globalStats);
    }

    /**
     * Get default global stats (no item specified).
     * Returns a new default WeaponStats instance.
     */
    public static WeaponStats getGlobalStats() {
        return new WeaponStats();
    }


    /**
     * Check if item has global config.
     */
    public static boolean hasGlobalConfig(Item item) {
        return item != null && INSTANCE.globalStats.containsKey(item);
    }

    /**
     * Set global stats (client-only, for sync purposes).
     */
    public static void setGlobalStatsClientOnly(Item item, WeaponStats stats) {
        if (item == null || stats == null) return;
        INSTANCE.globalStats.put(item, stats.copy());
    }

    /**
     * Validate and clamp stats using security limits.
     */
    public static WeaponStats clampStats(WeaponStats stats) {
        return INSTANCE.validateAndClamp(stats);
    }

    /**
     * Clear item-specific stats from an ItemStack.
     */
    public static void clearItemSpecificStats(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        INSTANCE.clearSpecificStats(stack);
    }

    /**
     * Clear all global stats.
     */
    public static void clearAllGlobalStats() {
        INSTANCE.globalStats.clear();
    }

    /**
     * Load stats from vanilla attribute modifiers (for reconstruction).
     * Returns null if no weapon-like attributes found.
     */
    @Nullable
    public static WeaponStats loadFromAttributeModifiers(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        try {
            var mods = stack.getAttributeModifiers();
            if (mods == null || mods.modifiers().isEmpty()) return null;

            WeaponStats stats = new WeaponStats();
            boolean found = false;

            for (var entry : mods.modifiers()) {
                var attr = entry.attribute();
                var mod = entry.modifier();
                if (attr == null || mod == null) continue;

                var attrValue = attr.value();
                if (attrValue == net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE.value()) {
                    stats.setAttackDamage((float) mod.amount());
                    found = true;
                } else if (attrValue == net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED.value()) {
                    stats.setAttackSpeed((float) mod.amount());
                    found = true;
                }
            }

            return found ? stats : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Set specific stats with optional variant extras (Mace/Trident data).
     */
    public void setSpecificStats(ItemStack stack, WeaponStats stats, @Nullable CompoundTag variantExtras) {
        if (stack == null || stack.isEmpty() || stats == null) return;

        WeaponStats clamped = validateAndClamp(stats.copy());

        // Build stats tag
        CompoundTag statsTag = new CompoundTag();
        clamped.save(statsTag);

        // Merge variant-specific data (Mace/Trident) without overwriting stats keys
        if (variantExtras != null) {
            try {
                if (variantExtras.contains("Mace")) {
                    statsTag.put("Mace", Objects.requireNonNull(variantExtras.getCompound("Mace").copy()));
                }
                if (variantExtras.contains("Trident")) {
                    statsTag.put("Trident", Objects.requireNonNull(variantExtras.getCompound("Trident").copy()));
                }
            } catch (Exception e) {
                // Ignore variant merge errors
            }
        }

        // Store in CustomData
        var customDataType = Objects.requireNonNull(DataComponents.CUSTOM_DATA);
        net.minecraft.world.item.component.CustomData.update(customDataType, stack, tag -> {
            tag.put(nbtKey, Objects.requireNonNull(statsTag.copy()));
        });

        // Also store in typed component
        var componentType = getComponentType();
        if (componentType != null) {
            try {
                stack.set(componentType, statsTag.copy());
            } catch (Exception e) {
                // Component set failed
            }
        }
    }
}
