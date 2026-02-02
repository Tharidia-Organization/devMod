package com.devmod.config.handler.impl;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.gson.reflect.TypeToken;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import com.devmod.attributes.ModAttributes;
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

        // Apply attribute modifiers to the item stack
        try {
            ItemAttributeModifiers attrMods = buildAttributeModifiers(stack, clamped);
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS, attrMods);
        } catch (Exception e) {
            // Attribute modifier application failed - log but don't break
            logger.warn("[WeaponConfig] Failed to apply attribute modifiers: {}", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ATTRIBUTE MODIFIER BUILDER
    // ═══════════════════════════════════════════════════════════════

    private static final String DEVMOD_NAMESPACE = "devmod";

    /**
     * Build ItemAttributeModifiers from WeaponStats.
     * This converts the stats values into actual Minecraft attribute modifiers
     * that affect gameplay (damage, speed, reach, etc.).
     */
    private ItemAttributeModifiers buildAttributeModifiers(ItemStack stack, WeaponStats stats) {
        List<ItemAttributeModifiers.Entry> entries = new ArrayList<>();

        // Get existing modifiers to preserve showInTooltip setting
        ItemAttributeModifiers existing = stack.getOrDefault(
            DataComponents.ATTRIBUTE_MODIFIERS,
            ItemAttributeModifiers.EMPTY
        );

        // Attack Damage
        if (stats.getAttackDamage() != 0) {
            addModifier(entries, Attributes.ATTACK_DAMAGE, "attack_damage", stats.getAttackDamage());
        }

        // Attack Speed
        if (stats.getAttackSpeed() != 0) {
            addModifier(entries, Attributes.ATTACK_SPEED, "attack_speed", stats.getAttackSpeed());
        }

        // Attack Reach (Entity Interaction Range)
        if (stats.getAttackReach() != 0) {
            addModifier(entries, Attributes.ENTITY_INTERACTION_RANGE, "attack_reach", stats.getAttackReach());
        }

        // Attack Knockback
        if (stats.getAttackKnockback() != 0) {
            addModifier(entries, Attributes.ATTACK_KNOCKBACK, "attack_knockback", stats.getAttackKnockback());
        }

        // Sweeping Damage Ratio
        if (stats.getSweepingRatio() != 0) {
            addModifier(entries, Attributes.SWEEPING_DAMAGE_RATIO, "sweeping_ratio", stats.getSweepingRatio());
        }

        // Custom DevMod attributes (DeferredHolder implements Holder<Attribute>)
        try {
            // Crit Chance
            if (stats.getCritChance() != 0 && ModAttributes.CRIT_CHANCE != null && ModAttributes.CRIT_CHANCE.isBound()) {
                addModifierWithHolder(entries, ModAttributes.CRIT_CHANCE, "crit_chance", stats.getCritChance());
            }

            // Crit Damage/Multiplier
            if (stats.getCritDamage() != 0 && ModAttributes.CRIT_MULTIPLIER != null && ModAttributes.CRIT_MULTIPLIER.isBound()) {
                addModifierWithHolder(entries, ModAttributes.CRIT_MULTIPLIER, "crit_multiplier", stats.getCritDamage());
            }

            // Armor Shred
            if (stats.getArmorShred() != 0 && ModAttributes.ARMOR_SHRED != null && ModAttributes.ARMOR_SHRED.isBound()) {
                addModifierWithHolder(entries, ModAttributes.ARMOR_SHRED, "armor_shred", stats.getArmorShred());
            }

            // Lifesteal
            if (stats.getLifesteal() != 0 && ModAttributes.LIFE_STEAL != null && ModAttributes.LIFE_STEAL.isBound()) {
                addModifierWithHolder(entries, ModAttributes.LIFE_STEAL, "lifesteal", stats.getLifesteal());
            }

            // Damage Bonus
            if (stats.getDamageBonus() != 0 && ModAttributes.DAMAGE_BONUS != null && ModAttributes.DAMAGE_BONUS.isBound()) {
                addModifierWithHolder(entries, ModAttributes.DAMAGE_BONUS, "damage_bonus", stats.getDamageBonus());
            }

            // Damage vs Undead
            if (stats.getDamageVsUndead() != 0 && ModAttributes.DAMAGE_VS_UNDEAD != null && ModAttributes.DAMAGE_VS_UNDEAD.isBound()) {
                addModifierWithHolder(entries, ModAttributes.DAMAGE_VS_UNDEAD, "damage_vs_undead", stats.getDamageVsUndead());
            }

            // Damage vs Arthropods
            if (stats.getDamageVsArthropods() != 0 && ModAttributes.DAMAGE_VS_ARTHROPODS != null && ModAttributes.DAMAGE_VS_ARTHROPODS.isBound()) {
                addModifierWithHolder(entries, ModAttributes.DAMAGE_VS_ARTHROPODS, "damage_vs_arthropods", stats.getDamageVsArthropods());
            }

            // Damage vs Players
            if (stats.getDamageVsPlayers() != 0 && ModAttributes.DAMAGE_VS_PLAYERS != null && ModAttributes.DAMAGE_VS_PLAYERS.isBound()) {
                addModifierWithHolder(entries, ModAttributes.DAMAGE_VS_PLAYERS, "damage_vs_players", stats.getDamageVsPlayers());
            }

            // True Damage Percent
            if (stats.getTrueDamagePercent() != 0 && ModAttributes.TRUE_DAMAGE_PERCENT != null && ModAttributes.TRUE_DAMAGE_PERCENT.isBound()) {
                addModifierWithHolder(entries, ModAttributes.TRUE_DAMAGE_PERCENT, "true_damage_percent", stats.getTrueDamagePercent());
            }
        } catch (Exception e) {
            // ModAttributes may not be fully initialized, skip custom attributes
            logger.debug("[WeaponConfig] Custom attributes not available: {}", e.getMessage());
        }

        // Add custom attribute modifiers from stats
        for (WeaponStats.AttributeModifierData customMod : stats.getCustomAttributeModifiers()) {
            if (customMod == null || customMod.attributeId == null || customMod.attributeId.isEmpty()) {
                continue;
            }
            try {
                ResourceLocation attrLoc = ResourceLocation.tryParse(customMod.attributeId);
                if (attrLoc == null) continue;

                // Try to get the attribute holder from registry
                var registry = net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE;
                var attrOpt = registry.getHolder(attrLoc);
                if (attrOpt.isEmpty()) continue;

                Holder<Attribute> holder = attrOpt.get();
                AttributeModifier.Operation op = switch (customMod.operation) {
                    case 1 -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
                    case 2 -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
                    default -> AttributeModifier.Operation.ADD_VALUE;
                };

                ResourceLocation modId = ResourceLocation.fromNamespaceAndPath(DEVMOD_NAMESPACE, "custom_" + attrLoc.getPath());
                AttributeModifier modifier = new AttributeModifier(modId, customMod.amount, op);
                entries.add(new ItemAttributeModifiers.Entry(holder, modifier, EquipmentSlotGroup.MAINHAND));
            } catch (Exception e) {
                logger.debug("[WeaponConfig] Failed to apply custom attribute {}: {}", customMod.attributeId, e.getMessage());
            }
        }

        return new ItemAttributeModifiers(entries, existing.showInTooltip());
    }

    /**
     * Add a vanilla attribute modifier entry.
     */
    private void addModifier(List<ItemAttributeModifiers.Entry> entries,
                             Holder<Attribute> attribute, String name, double value) {
        ResourceLocation modId = ResourceLocation.fromNamespaceAndPath(DEVMOD_NAMESPACE, name);
        AttributeModifier modifier = new AttributeModifier(modId, value, AttributeModifier.Operation.ADD_VALUE);
        entries.add(new ItemAttributeModifiers.Entry(attribute, modifier, EquipmentSlotGroup.MAINHAND));
    }

    /**
     * Add a custom attribute modifier entry with an existing holder.
     */
    private void addModifierWithHolder(List<ItemAttributeModifiers.Entry> entries,
                                       Holder<Attribute> holder, String name, double value) {
        ResourceLocation modId = ResourceLocation.fromNamespaceAndPath(DEVMOD_NAMESPACE, name);
        AttributeModifier modifier = new AttributeModifier(modId, value, AttributeModifier.Operation.ADD_VALUE);
        entries.add(new ItemAttributeModifiers.Entry(holder, modifier, EquipmentSlotGroup.MAINHAND));
    }
}
