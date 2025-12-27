package com.devmod.client.ui.editor.modules;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import com.devmod.DevMod;
import com.devmod.client.ui.editor.components.SourceBadge;
import com.devmod.client.ui.editor.core.EditorCache;
import com.devmod.config.WeaponConfigManager;
import com.devmod.stats.WeaponStats;

public class WeaponModuleCore {

    static final String NBT_KEY = "WeaponModStats";
    static final double EPSILON = 1e-4;
    static final String DELTA_KEY = "delta";

    private static final @Nonnull net.minecraft.world.item.component.ItemAttributeModifiers NONNULL_ATTR_EMPTY =
        Objects.requireNonNull(net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY,
            "ItemAttributeModifiers.EMPTY cannot be null");

    // Stats state
    WeaponStats stats = new WeaponStats();
    WeaponStats originalStats = new WeaponStats();
    String sourcePrefix = "";
    SourceBadge.Source dataSource = SourceBadge.Source.VANILLA;

    // Reference to parent module
    private final WeaponModule module;

    public WeaponModuleCore(WeaponModule module) {
        this.module = module;
    }

    // ═══════════════════════════════════════════════════════════════
    // LOADING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Load stats from item NBT/components.
     */
    public void loadStatsFromItem(ItemStack item) {
        CompoundTag statsTag = null;
        try {
            statsTag = item.get(Objects.requireNonNull(com.devmod.components.WeaponComponents.WEAPON_STATS.get()));
        } catch (Exception e) {
            DevMod.LOGGER.debug("[Editor][Weapon] Failed to read weapon stats component", e);
        }

        CompoundTag customTag;
        try {
            customTag = item.getOrDefault(
                Objects.requireNonNull(net.minecraft.core.component.DataComponents.CUSTOM_DATA),
                Objects.requireNonNull(net.minecraft.world.item.component.CustomData.EMPTY)
            ).copyTag();
        } catch (Exception e) {
            DevMod.LOGGER.debug("[Editor][Weapon] Failed to read custom data component", e);
            customTag = new CompoundTag();
        }
        if (customTag == null) {
            customTag = new CompoundTag();
        }

        DevMod.LOGGER.info("[Editor][Weapon] Item={} | hasComponent={} | hasCustomData={}",
            item.getItem().toString(),
            statsTag != null && !statsTag.isEmpty(),
            customTag != null && !customTag.isEmpty());

        if (statsTag == null || statsTag.isEmpty()) {
            try {
                if (customTag.contains(NBT_KEY)) {
                    statsTag = customTag.getCompound(NBT_KEY);
                }
            } catch (Exception e) {
                DevMod.LOGGER.debug("[Editor][Weapon] Failed to read weapon stats from custom data", e);
                statsTag = new CompoundTag();
            }
        }

        if (statsTag != null && !statsTag.isEmpty()) {
            sourcePrefix = "[DEV] ";
            dataSource = SourceBadge.Source.DEV;
            DevMod.LOGGER.info("[Editor][Weapon] Loaded stats from component tag (size={})", statsTag.size());
            stats = statsTag.contains(NBT_KEY) ? WeaponStats.load(statsTag.getCompound(NBT_KEY)) : WeaponStats.load(statsTag);
        } else {
            boolean hasCustomData = customTag != null && !customTag.isEmpty();
            sourcePrefix = hasCustomData ? "[NBT] " : "[VANILLA] ";
            dataSource = hasCustomData ? SourceBadge.Source.NBT : SourceBadge.Source.VANILLA;
            DevMod.LOGGER.info("[Editor][Weapon] No custom stats found; applying vanilla defaults. customTagEmpty={} stackAttrMods={}",
                customTag == null || customTag.isEmpty(),
                item.getAttributeModifiers().modifiers().size());
            stats = new WeaponStats();
            applyVanillaDefaults(item, stats);
        }

        // Load tool rules from existing component
        loadToolRules(item);

        originalStats = stats.copy();
    }

    /**
     * Populate stats from vanilla attribute modifiers and durability.
     */
    void applyVanillaDefaults(ItemStack item, WeaponStats target) {
        if (target == null) return;
        try {
            net.minecraft.world.item.component.ItemAttributeModifiers mods = mergeAttributeSources(item);
            double dmg = 0, spd = 0, kb = 0, reach = 0, sweep = 0;
            double critCh = 0, critDmg = 0, shred = 0, lifesteal = 0, dmgBonus = 0;
            double vsUndead = 0, vsArthro = 0, vsPlayers = 0, trueDmg = 0;

            DevMod.LOGGER.info("[Editor][Weapon] Attribute modifiers merged: {}", mods.modifiers().size());
            for (net.minecraft.world.item.component.ItemAttributeModifiers.Entry entry : mods.modifiers()) {
                var slotGroup = entry.slot();
                if (slotGroup != null && !slotGroup.test(net.minecraft.world.entity.EquipmentSlot.MAINHAND)) continue;
                @Nonnull var attrHolder = Objects.requireNonNull(entry.attribute(), "attribute holder cannot be null");
                var mod = entry.modifier();
                if (attrHolder == null || mod == null) continue;
                @Nonnull var attrKey = Objects.requireNonNull(attrHolder.unwrapKey().orElse(null),
                    "attribute resource key cannot be null");
                var attr = attrHolder.value();

                boolean isDmg = attrKey == net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE ||
                    attr == net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE.value();
                boolean isSpd = attrKey == net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED ||
                    attr == net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED.value();
                boolean isKb = attrKey == net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_KNOCKBACK ||
                    attr == net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_KNOCKBACK.value();
                boolean isReach = attrKey == net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE ||
                    attr == net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE.value();
                boolean isSweep = attrKey == net.minecraft.world.entity.ai.attributes.Attributes.SWEEPING_DAMAGE_RATIO ||
                    attr == net.minecraft.world.entity.ai.attributes.Attributes.SWEEPING_DAMAGE_RATIO.value();
                boolean isCritCh = attrKey == com.devmod.attributes.ModAttributes.CRIT_CHANCE.getKey() ||
                    attr == com.devmod.attributes.ModAttributes.CRIT_CHANCE.get();
                boolean isCritDmg = attrKey == com.devmod.attributes.ModAttributes.CRIT_MULTIPLIER.getKey() ||
                    attr == com.devmod.attributes.ModAttributes.CRIT_MULTIPLIER.get();
                boolean isShred = attrKey == com.devmod.attributes.ModAttributes.ARMOR_SHRED.getKey() ||
                    attr == com.devmod.attributes.ModAttributes.ARMOR_SHRED.get();
                boolean isLifesteal = attrKey == com.devmod.attributes.ModAttributes.LIFE_STEAL.getKey() ||
                    attr == com.devmod.attributes.ModAttributes.LIFE_STEAL.get();
                boolean isDmgBonus = attrKey == com.devmod.attributes.ModAttributes.DAMAGE_BONUS.getKey() ||
                    attr == com.devmod.attributes.ModAttributes.DAMAGE_BONUS.get();
                boolean isVsUndead = attrKey == com.devmod.attributes.ModAttributes.DAMAGE_VS_UNDEAD.getKey() ||
                    attr == com.devmod.attributes.ModAttributes.DAMAGE_VS_UNDEAD.get();
                boolean isVsArthro = attrKey == com.devmod.attributes.ModAttributes.DAMAGE_VS_ARTHROPODS.getKey() ||
                    attr == com.devmod.attributes.ModAttributes.DAMAGE_VS_ARTHROPODS.get();
                boolean isVsPlayers = attrKey == com.devmod.attributes.ModAttributes.DAMAGE_VS_PLAYERS.getKey() ||
                    attr == com.devmod.attributes.ModAttributes.DAMAGE_VS_PLAYERS.get();
                boolean isTrueDmg = attrKey == com.devmod.attributes.ModAttributes.TRUE_DAMAGE_PERCENT.getKey() ||
                    attr == com.devmod.attributes.ModAttributes.TRUE_DAMAGE_PERCENT.get();

                if (isDmg) dmg += mod.amount();
                else if (isSpd) spd += mod.amount();
                else if (isKb) kb += mod.amount();
                else if (isReach) reach += mod.amount();
                else if (isSweep) sweep += mod.amount();
                else if (isCritCh) critCh += mod.amount();
                else if (isCritDmg) critDmg += mod.amount();
                else if (isShred) shred += mod.amount();
                else if (isLifesteal) lifesteal += mod.amount();
                else if (isDmgBonus) dmgBonus += mod.amount();
                else if (isVsUndead) vsUndead += mod.amount();
                else if (isVsArthro) vsArthro += mod.amount();
                else if (isVsPlayers) vsPlayers += mod.amount();
                else if (isTrueDmg) trueDmg += mod.amount();
            }

            if (dmg != 0) target.attackDamage = (float) dmg;
            if (spd != 0) target.attackSpeed = (float) spd;
            if (kb != 0) target.attackKnockback = (float) kb;
            if (reach != 0) target.attackReach = (float) reach;
            if (sweep != 0) target.sweepingRatio = (float) sweep;
            if (critCh != 0) target.critChance = (float) (critCh / 100f);
            if (critDmg != 0) target.critDamage = (float) critDmg;
            if (shred != 0) target.armorShred = (float) shred;
            if (lifesteal != 0) target.lifesteal = (float) (lifesteal / 100f);
            if (dmgBonus != 0) target.damageBonus = (float) (dmgBonus / 100f);
            if (vsUndead != 0) target.damageVsUndead = (float) (vsUndead / 100f);
            if (vsArthro != 0) target.damageVsArthropods = (float) (vsArthro / 100f);
            if (vsPlayers != 0) target.damageVsPlayers = (float) (vsPlayers / 100f);
            if (trueDmg != 0) target.trueDamagePercent = (float) (trueDmg / 100f);

            DevMod.LOGGER.info("[Editor][Weapon] From attributes -> dmg={} spd={} kb={} reach={} sweep={} critCh={} critDmg={} shred={} lifesteal={} dmgBonus={} vsUndead={} vsArthro={} vsPlayers={} trueDmg={}",
                target.attackDamage, target.attackSpeed, target.attackKnockback, target.attackReach, target.sweepingRatio,
                target.critChance, target.critDamage, target.armorShred, target.lifesteal, target.damageBonus,
                target.damageVsUndead, target.damageVsArthropods, target.damageVsPlayers, target.trueDamagePercent);
        } catch (Exception ignored) {
            DevMod.LOGGER.warn("[Editor][Weapon] Failed to apply vanilla defaults", ignored);
        }

        try {
            target.maxDurability = item.getMaxDamage();
            target.currentDamage = item.getDamageValue();
            net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.Unbreakable> unbreakableType =
                Objects.requireNonNull(net.minecraft.core.component.DataComponents.UNBREAKABLE,
                    "UNBREAKABLE component type cannot be null");
            target.unbreakable = item.get(unbreakableType) != null;
            DevMod.LOGGER.info("[Editor][Weapon] Durability -> max={} damage={} unbreakable={}", target.maxDurability, target.currentDamage, target.unbreakable);
        } catch (Exception e) {
            DevMod.LOGGER.debug("[Editor][Weapon] Failed to read durability data", e);
        }
    }

    private @Nonnull net.minecraft.world.item.component.ItemAttributeModifiers mergeAttributeSources(ItemStack item) {
        @Nonnull net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.ItemAttributeModifiers> attrType =
            Objects.requireNonNull(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                "ATTRIBUTE_MODIFIERS component type cannot be null");

        net.minecraft.world.item.component.ItemAttributeModifiers stackMods = item.getOrDefault(attrType, NONNULL_ATTR_EMPTY);

        net.minecraft.world.item.component.ItemAttributeModifiers itemMods = NONNULL_ATTR_EMPTY;
        try {
            itemMods = item.getAttributeModifiers();
        } catch (Exception e) {
            DevMod.LOGGER.debug("[Editor][Weapon] Failed to read item attribute modifiers", e);
        }

        net.minecraft.world.item.component.ItemAttributeModifiers defaultMods = NONNULL_ATTR_EMPTY;
        try {
            defaultMods = item.getItem().getDefaultAttributeModifiers(item);
        } catch (Exception e) {
            DevMod.LOGGER.debug("[Editor][Weapon] Failed to read default attribute modifiers", e);
        }

        // Prefer stack -> item -> default
        net.minecraft.world.item.component.ItemAttributeModifiers chosen =
            (!stackMods.modifiers().isEmpty() ? stackMods
                : (!itemMods.modifiers().isEmpty() ? itemMods : defaultMods));

        DevMod.LOGGER.info(
            "[Editor][Weapon] Attribute sources sizes -> default:{} item:{} stack:{} chosen:{}",
            defaultMods.modifiers().size(),
            itemMods.modifiers().size(),
            stackMods.modifiers().size(),
            chosen.modifiers().size()
        );
        for (net.minecraft.world.item.component.ItemAttributeModifiers.Entry e : chosen.modifiers()) {
            DevMod.LOGGER.info("[Editor][Weapon] Mod attr={} op={} amt={} slot={}",
                e.attribute(),
                e.modifier().operation(),
                e.modifier().amount(),
                e.slot());
        }
        return chosen.modifiers().isEmpty() ? NONNULL_ATTR_EMPTY : chosen;
    }

    private void loadToolRules(ItemStack item) {
        try {
            net.minecraft.world.item.component.Tool tool = item.get(Objects.requireNonNull(net.minecraft.core.component.DataComponents.TOOL));
            if (tool != null) {
                stats.toolDefaultMiningSpeed = tool.defaultMiningSpeed();
                stats.toolDamagePerBlock = tool.damagePerBlock();
                stats.toolRules.clear();
                for (net.minecraft.world.item.component.Tool.Rule rule : tool.rules()) {
                    String blockTag = "";
                    net.minecraft.core.HolderSet<net.minecraft.world.level.block.Block> blocks = rule.blocks();
                    if (blocks instanceof net.minecraft.core.HolderSet.Named<net.minecraft.world.level.block.Block> named) {
                        blockTag = named.key().location().toString();
                    }
                    float speed = rule.speed().orElse(stats.toolDefaultMiningSpeed);
                    Boolean drops = rule.correctForDrops().orElse(null);
                    stats.toolRules.add(new WeaponStats.ToolRuleData(blockTag, speed, drops));
                }
            }
        } catch (Exception e) {
            DevMod.LOGGER.debug("[Editor][Weapon] Failed to load tool rules", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // CUSTOM DATA TAG
    // ═══════════════════════════════════════════════════════════════

    public CompoundTag getCustomDataTag(ItemStack item) {
        Objects.requireNonNull(item, "item cannot be null");
        var customData = item.getOrDefault(
            Objects.requireNonNull(DataComponents.CUSTOM_DATA, "DataComponents.CUSTOM_DATA cannot be null"),
            Objects.requireNonNull(CustomData.EMPTY, "CustomData.EMPTY cannot be null")
        );
        CompoundTag tag = customData.copyTag();
        return tag == null ? new CompoundTag() : tag;
    }

    // ═══════════════════════════════════════════════════════════════
    // DELTA COMPUTATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Build a delta tag containing only fields that differ from baseline.
     */
    public CompoundTag buildDelta(WeaponStats baseline, WeaponStats current) {
        CompoundTag delta = new CompoundTag();
        if (baseline == null || current == null) return delta;

        putIfChanged(delta, "HeadMult", baseline.headMult, current.headMult);
        putIfChanged(delta, "BodyMult", baseline.bodyMult, current.bodyMult);
        putIfChanged(delta, "ArmsMult", baseline.armsMult, current.armsMult);
        putIfChanged(delta, "LegsMult", baseline.legsMult, current.legsMult);

        putIfChanged(delta, "ArmorPen", baseline.armorPenetration, current.armorPenetration);
        putIfChanged(delta, "BaseDmg", baseline.baseDamageBonus, current.baseDamageBonus);
        putIfChanged(delta, "AtkDmg", baseline.attackDamage, current.attackDamage);
        putIfChanged(delta, "AtkSpd", baseline.attackSpeed, current.attackSpeed);
        putIfChanged(delta, "AtkRch", baseline.attackReach, current.attackReach);
        putIfChanged(delta, "AtkKB", baseline.attackKnockback, current.attackKnockback);
        putIfChanged(delta, "Sweep", baseline.damageBonus, current.damageBonus);

        putIfChanged(delta, "CritCh", baseline.critChance, current.critChance);
        putIfChanged(delta, "CritDmg", baseline.critDamage, current.critDamage);
        putIfChanged(delta, "ArmorShred", baseline.armorShred, current.armorShred);

        putIfChanged(delta, "FireDmg", baseline.fireDamageBonus, current.fireDamageBonus);
        putIfChanged(delta, "MagicDmg", baseline.magicDamageBonus, current.magicDamageBonus);
        putIfChanged(delta, "Lifesteal", baseline.lifesteal, current.lifesteal);
        putIfChanged(delta, "VsUndead", baseline.damageVsUndead, current.damageVsUndead);
        putIfChanged(delta, "VsArthro", baseline.damageVsArthropods, current.damageVsArthropods);
        putIfChanged(delta, "VsPlayers", baseline.damageVsPlayers, current.damageVsPlayers);
        putIfChanged(delta, "TrueDmgPct", baseline.trueDamagePercent, current.trueDamagePercent);

        putIfChanged(delta, "MaxDur", baseline.maxDurability, current.maxDurability);
        putIfChanged(delta, "CurDmg", baseline.currentDamage, current.currentDamage);
        putIfChanged(delta, "Repair", baseline.repairCost, current.repairCost);
        if (baseline.unbreakable != current.unbreakable) delta.putBoolean("Unbreakable", current.unbreakable);
        if (baseline.clearToolRules != current.clearToolRules) delta.putBoolean("ClearToolRules", current.clearToolRules);
        putIfChanged(delta, "DefaultSpeed", baseline.toolDefaultMiningSpeed, current.toolDefaultMiningSpeed);
        putIfChanged(delta, "DamagePerBlock", baseline.toolDamagePerBlock, current.toolDamagePerBlock);

        return delta;
    }

    private void putIfChanged(CompoundTag tag, String key, float base, float cur) {
        if (Math.abs(base - cur) > EPSILON) {
            tag.putFloat(Objects.requireNonNull(key, "key"), cur);
        }
    }

    private void putIfChanged(CompoundTag tag, String key, int base, int cur) {
        if (base != cur) {
            tag.putInt(Objects.requireNonNull(key, "key"), cur);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // STATE COMPARISON
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if current stats differ from original.
     */
    public boolean hasModifications() {
        return !statsEquals(stats, originalStats) || module.getVariants().variantChanged();
    }

    boolean statsEquals(WeaponStats a, WeaponStats b) {
        return a.headMult == b.headMult
            && a.bodyMult == b.bodyMult
            && a.armsMult == b.armsMult
            && a.legsMult == b.legsMult
            && a.attackDamage == b.attackDamage
            && a.attackSpeed == b.attackSpeed
            && a.attackReach == b.attackReach
            && a.attackKnockback == b.attackKnockback
            && a.damageBonus == b.damageBonus
            && a.armorPenetration == b.armorPenetration
            && a.baseDamageBonus == b.baseDamageBonus
            && a.armorShred == b.armorShred
            && a.critChance == b.critChance
            && a.critDamage == b.critDamage
            && a.lifesteal == b.lifesteal
            && a.fireDamageBonus == b.fireDamageBonus
            && a.magicDamageBonus == b.magicDamageBonus
            && a.damageVsUndead == b.damageVsUndead
            && a.damageVsArthropods == b.damageVsArthropods
            && a.damageVsPlayers == b.damageVsPlayers
            && a.trueDamagePercent == b.trueDamagePercent
            && a.maxDurability == b.maxDurability
            && a.currentDamage == b.currentDamage
            && a.repairCost == b.repairCost
            && a.unbreakable == b.unbreakable
            && a.clearToolRules == b.clearToolRules
            && Float.compare(a.toolDefaultMiningSpeed, b.toolDefaultMiningSpeed) == 0
            && a.toolDamagePerBlock == b.toolDamagePerBlock
            && toolRulesEqual(a.toolRules, b.toolRules)
            && !module.getVariants().variantChanged();
    }

    private boolean toolRulesEqual(java.util.List<WeaponStats.ToolRuleData> a, java.util.List<WeaponStats.ToolRuleData> b) {
        int max = Math.max(a.size(), b.size());
        for (int i = 0; i < max; i++) {
            WeaponStats.ToolRuleData ra = i < a.size() ? a.get(i) : null;
            WeaponStats.ToolRuleData rb = i < b.size() ? b.get(i) : null;
            if ((ra == null || ra.isEmpty()) && (rb == null || rb.isEmpty())) {
                continue;
            }
            if (ra == null || rb == null) {
                return false;
            }
            if (!Objects.equals(ra.blockTag, rb.blockTag)) return false;
            if (Float.compare(ra.speed, rb.speed) != 0) return false;
            if (!Objects.equals(ra.correctForDrops, rb.correctForDrops)) return false;
        }
        return true;
    }

    // ═══════════════════════════════════════════════════════════════
    // RESOLVE SERVER STATS
    // ═══════════════════════════════════════════════════════════════

    public WeaponStats resolveServerStats(CompoundTag tag, ItemStack item) {
        if (tag.contains(NBT_KEY)) {
            return loadStatsFromTag(tag);
        }
        WeaponStats global = WeaponConfigManager.getGlobalStats(item.getItem());
        return global == null ? new WeaponStats() : global;
    }

    WeaponStats loadStatsFromTag(CompoundTag tag) {
        if (tag.contains(NBT_KEY)) {
            return WeaponStats.load(tag.getCompound(NBT_KEY));
        }
        return new WeaponStats();
    }

    // ═══════════════════════════════════════════════════════════════
    // DPS COMPUTATION
    // ═══════════════════════════════════════════════════════════════

    public float computeDps(ItemStack item) {
        return EditorCache.INSTANCE.getOrCompute(
            EditorCache.Types.DPS,
            Objects.requireNonNull(item.toString()),
            Float.class,
            () -> stats.calculateDPS(4.0f)
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════

    public WeaponStats getStats() {
        return stats;
    }

    public void setStats(WeaponStats stats) {
        this.stats = stats;
    }

    public WeaponStats getOriginalStats() {
        return originalStats;
    }

    public void setOriginalStats(WeaponStats originalStats) {
        this.originalStats = originalStats;
    }

    public String getSourcePrefix() {
        return sourcePrefix;
    }

    public SourceBadge.Source getDataSource() {
        return dataSource;
    }
}
