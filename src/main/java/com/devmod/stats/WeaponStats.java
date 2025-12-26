package com.devmod.stats;

import net.minecraft.nbt.CompoundTag;

import com.devmod.config.Config;

public class WeaponStats {
    // ═══════════════════════════════════════════════════════════════
    // HIT LOCATION MULTIPLIERS
    // ═══════════════════════════════════════════════════════════════

    public float headMult = getDefaultHeadMult();
    public float bodyMult = getDefaultBodyMult();
    public float armsMult = getDefaultArmsMult();
    public float legsMult = getDefaultLegsMult();

    // ═══════════════════════════════════════════════════════════════
    // CORE COMBAT STATS
    // ═══════════════════════════════════════════════════════════════

    public float armorPenetration = 0.0f;   // Percentage of armor ignored (0.0 - 1.0)
    public float baseDamageBonus = 0.0f;    // Flat damage added
    public float attackDamage = 0.0f;       // Base attack damage override (0 = use item default)
    public float attackSpeed = 0.0f;        // Attack speed override (0 = use item default)
    public float attackReach = 0.0f;        // Attack reach override (0 = use item default)
    public float attackKnockback = 0.0f;    // Additional knockback
    public float damageBonus = 0.0f;      // Direct damage bonus (0-1)
    public float sweepingRatio = 0.0f;    // AoE damage multiplier (0-1), uses minecraft:sweeping_damage_ratio

    // ═══════════════════════════════════════════════════════════════
    // CRITICAL HIT
    // ═══════════════════════════════════════════════════════════════

    public float critChance = 0.0f;         // Critical hit chance (0.0 - 1.0)
    public float critDamage = 1.5f;         // Critical damage multiplier
    public float armorShred = 0.0f;         // Reduces target armor (0-66)

    // ═══════════════════════════════════════════════════════════════
    // DAMAGE TYPE BONUSES
    // ═══════════════════════════════════════════════════════════════

    public float fireDamageBonus = 0.0f;    // Bonus fire damage per hit
    public float magicDamageBonus = 0.0f;   // Bonus magic damage per hit
    public float lifesteal = 0.0f;          // Heal percentage of damage dealt (0.0 - 1.0)
    public float damageVsUndead = 0.0f;     // Bonus % vs undead (0-2.0)
    public float damageVsArthropods = 0.0f; // Bonus % vs arthropods (0-2.0)
    public float damageVsPlayers = 0.0f;    // Bonus % vs players (0-2.0)
    public float trueDamagePercent = 0.0f;  // Portion of damage as true (0-1.0)

    // ═══════════════════════════════════════════════════════════════
    // DURABILITY
    // ═══════════════════════════════════════════════════════════════
    public int maxDurability = 0;
    public int currentDamage = 0;
    public int repairCost = 0;
    public boolean unbreakable = false;
    public boolean clearToolRules = false;
    public float toolDefaultMiningSpeed = 1.0f;
    public int toolDamagePerBlock = 1;
    public java.util.List<ToolRuleData> toolRules = new java.util.ArrayList<>();

    /**
     * Simple DTO for tool rules editing.
     */
    public static class ToolRuleData {
        public String blockTag = "";
        public float speed = 1.0f;
        public Boolean correctForDrops = Boolean.TRUE;

        public ToolRuleData() {}

        public ToolRuleData(String tag, float speed, Boolean drops) {
            this.blockTag = tag == null ? "" : tag;
            this.speed = speed;
            this.correctForDrops = drops;
        }

        public ToolRuleData copy() {
            return new ToolRuleData(blockTag, speed, correctForDrops);
        }

        public boolean isEmpty() {
            return blockTag == null || blockTag.isBlank();
        }
    }

    // Config-driven defaults with safe fallback
    private static float getDefaultHeadMult() {
        try { return Config.HEAD_DAMAGE_MULTIPLIER.get().floatValue(); }
        catch (Throwable e) { return 1.5f; }
    }

    private static float getDefaultBodyMult() {
        try { return Config.BODY_DAMAGE_MULTIPLIER.get().floatValue(); }
        catch (Throwable e) { return 1.0f; }
    }

    private static float getDefaultArmsMult() {
        try { return Config.ARMS_DAMAGE_MULTIPLIER.get().floatValue(); }
        catch (Throwable e) { return 0.8f; }
    }

    private static float getDefaultLegsMult() {
        try { return Config.LEGS_DAMAGE_MULTIPLIER.get().floatValue(); }
        catch (Throwable e) { return 0.7f; }
    }

    // ═══════════════════════════════════════════════════════════════
    // SERIALIZATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Save weapon stats to NBT compound tag.
     * Uses short keys to minimize storage overhead.
     */
    public void save(CompoundTag tag) {
        // Hit location multipliers
        tag.putFloat("HeadMult", headMult);
        tag.putFloat("BodyMult", bodyMult);
        tag.putFloat("ArmsMult", armsMult);
        tag.putFloat("LegsMult", legsMult);

        // Core stats (only save non-default values)
        if (armorPenetration != 0.0f) tag.putFloat("ArmorPen", armorPenetration);
        if (baseDamageBonus != 0.0f) tag.putFloat("BaseDmg", baseDamageBonus);
        if (attackDamage != 0.0f) tag.putFloat("AtkDmg", attackDamage);
        if (attackSpeed != 0.0f) tag.putFloat("AtkSpd", attackSpeed);
        if (attackReach != 0.0f) tag.putFloat("AtkRch", attackReach);
        if (attackKnockback != 0.0f) tag.putFloat("AtkKB", attackKnockback);
        if (damageBonus != 0.0f) tag.putFloat("DmgBonus", damageBonus);
        if (sweepingRatio != 0.0f) tag.putFloat("SweepRatio", sweepingRatio);

        // Critical hit
        if (critChance != 0.0f) tag.putFloat("CritCh", critChance);
        if (critDamage != 1.5f) tag.putFloat("CritDmg", critDamage);
        if (armorShred != 0.0f) tag.putFloat("ArmorShred", armorShred);

        // Damage type bonuses
        if (fireDamageBonus != 0.0f) tag.putFloat("FireDmg", fireDamageBonus);
        if (magicDamageBonus != 0.0f) tag.putFloat("MagicDmg", magicDamageBonus);
        if (lifesteal != 0.0f) tag.putFloat("Lifesteal", lifesteal);
        if (damageVsUndead != 0.0f) tag.putFloat("VsUndead", damageVsUndead);
        if (damageVsArthropods != 0.0f) tag.putFloat("VsArthro", damageVsArthropods);
        if (damageVsPlayers != 0.0f) tag.putFloat("VsPlayers", damageVsPlayers);
        if (trueDamagePercent != 0.0f) tag.putFloat("TrueDmgPct", trueDamagePercent);

        // Durability
        if (maxDurability > 0) tag.putInt("MaxDur", maxDurability);
        if (currentDamage > 0) tag.putInt("CurDmg", currentDamage);
        if (repairCost > 0) tag.putInt("Repair", repairCost);
        if (unbreakable) tag.putBoolean("Unbreakable", true);
        if (clearToolRules) tag.putBoolean("ClearToolRules", true);
        if (!toolRules.isEmpty() || toolDefaultMiningSpeed != 1.0f || toolDamagePerBlock != 1) {
            net.minecraft.nbt.CompoundTag tool = new net.minecraft.nbt.CompoundTag();
            tool.putFloat("DefaultSpeed", toolDefaultMiningSpeed);
            tool.putInt("DamagePerBlock", toolDamagePerBlock);
            net.minecraft.nbt.ListTag rules = new net.minecraft.nbt.ListTag();
            for (ToolRuleData rule : toolRules) {
                if (rule == null || rule.isEmpty()) continue;
                net.minecraft.nbt.CompoundTag r = new net.minecraft.nbt.CompoundTag();
                r.putString("Tag", rule.blockTag == null ? "" : rule.blockTag);
                r.putFloat("Speed", rule.speed);
                if (rule.correctForDrops != null) {
                    r.putBoolean("Drops", rule.correctForDrops);
                }
                rules.add(r);
            }
            tool.put("Rules", rules);
            tag.put("ToolRules", tool);
        }
    }

    /**
     * Load weapon stats from NBT compound tag.
     */
    public static WeaponStats load(CompoundTag tag) {
        WeaponStats stats = new WeaponStats();

        // Hit location multipliers
        if (tag.contains("HeadMult")) stats.headMult = tag.getFloat("HeadMult");
        if (tag.contains("BodyMult")) stats.bodyMult = tag.getFloat("BodyMult");
        if (tag.contains("ArmsMult")) stats.armsMult = tag.getFloat("ArmsMult");
        if (tag.contains("LegsMult")) stats.legsMult = tag.getFloat("LegsMult");

        // Core stats
        if (tag.contains("ArmorPen")) stats.armorPenetration = tag.getFloat("ArmorPen");
        if (tag.contains("BaseDmg")) stats.baseDamageBonus = tag.getFloat("BaseDmg");
        if (tag.contains("AtkDmg")) stats.attackDamage = tag.getFloat("AtkDmg");
        if (tag.contains("AtkSpd")) stats.attackSpeed = tag.getFloat("AtkSpd");
        if (tag.contains("AtkRch")) stats.attackReach = tag.getFloat("AtkRch");
        if (tag.contains("AtkKB")) stats.attackKnockback = tag.getFloat("AtkKB");
        if (tag.contains("DmgBonus")) stats.damageBonus = tag.getFloat("DmgBonus");
        if (tag.contains("SweepRatio")) stats.sweepingRatio = tag.getFloat("SweepRatio");

        // Critical hit
        if (tag.contains("CritCh")) stats.critChance = tag.getFloat("CritCh");
        if (tag.contains("CritDmg")) stats.critDamage = tag.getFloat("CritDmg");
        if (tag.contains("ArmorShred")) stats.armorShred = tag.getFloat("ArmorShred");

        // Damage type bonuses
        if (tag.contains("FireDmg")) stats.fireDamageBonus = tag.getFloat("FireDmg");
        if (tag.contains("MagicDmg")) stats.magicDamageBonus = tag.getFloat("MagicDmg");
        if (tag.contains("Lifesteal")) stats.lifesteal = tag.getFloat("Lifesteal");
        if (tag.contains("VsUndead")) stats.damageVsUndead = tag.getFloat("VsUndead");
        if (tag.contains("VsArthro")) stats.damageVsArthropods = tag.getFloat("VsArthro");
        if (tag.contains("VsPlayers")) stats.damageVsPlayers = tag.getFloat("VsPlayers");
        if (tag.contains("TrueDmgPct")) stats.trueDamagePercent = tag.getFloat("TrueDmgPct");

        // Durability
        if (tag.contains("MaxDur")) stats.maxDurability = tag.getInt("MaxDur");
        if (tag.contains("CurDmg")) stats.currentDamage = tag.getInt("CurDmg");
        if (tag.contains("Repair")) stats.repairCost = tag.getInt("Repair");
        if (tag.contains("Unbreakable")) stats.unbreakable = tag.getBoolean("Unbreakable");
        if (tag.contains("ClearToolRules")) stats.clearToolRules = tag.getBoolean("ClearToolRules");
        if (tag.contains("ToolRules")) {
            net.minecraft.nbt.CompoundTag tool = tag.getCompound("ToolRules");
            stats.toolDefaultMiningSpeed = tool.contains("DefaultSpeed") ? tool.getFloat("DefaultSpeed") : 1.0f;
            stats.toolDamagePerBlock = tool.contains("DamagePerBlock") ? tool.getInt("DamagePerBlock") : 1;
            stats.toolRules.clear();
            net.minecraft.nbt.ListTag rules = tool.getList("Rules", 10);
            for (int i = 0; i < rules.size(); i++) {
                net.minecraft.nbt.CompoundTag r = rules.getCompound(i);
                String blockTag = r.getString("Tag");
                float speed = r.contains("Speed") ? r.getFloat("Speed") : 1.0f;
                Boolean drops = r.contains("Drops") ? r.getBoolean("Drops") : null;
                stats.toolRules.add(new ToolRuleData(blockTag, speed, drops));
            }
        }

        return stats;
    }

    // ═══════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Calculate DPS based on damage and attack speed.
     */
    public float calculateDPS(float baseItemDamage) {
        float damage = attackDamage > 0 ? attackDamage : baseItemDamage;
        float speed = attackSpeed > 0 ? attackSpeed : 1.6f;
        return damage * speed;
    }

    /**
     * Check if all values are at defaults (no modifications).
     */
    public boolean isDefault() {
        return headMult == getDefaultHeadMult()
            && bodyMult == getDefaultBodyMult()
            && armsMult == getDefaultArmsMult()
            && legsMult == getDefaultLegsMult()
            && armorPenetration == 0.0f
            && baseDamageBonus == 0.0f
            && attackDamage == 0.0f
            && attackSpeed == 0.0f
            && attackReach == 0.0f
            && attackKnockback == 0.0f
            && damageBonus == 0.0f
            && sweepingRatio == 0.0f
            && critChance == 0.0f
            && critDamage == 1.5f
            && armorShred == 0.0f
            && fireDamageBonus == 0.0f
            && magicDamageBonus == 0.0f
            && lifesteal == 0.0f
            && damageVsUndead == 0.0f
            && damageVsArthropods == 0.0f
            && damageVsPlayers == 0.0f
            && trueDamagePercent == 0.0f
            && maxDurability == 0
            && currentDamage == 0
            && repairCost == 0
            && !unbreakable
            && !clearToolRules
            && toolRules.isEmpty()
            && toolDefaultMiningSpeed == 1.0f
            && toolDamagePerBlock == 1;
    }

    /**
     * Create a copy of these stats.
     */
    public WeaponStats copy() {
        WeaponStats copy = new WeaponStats();
        copy.headMult = this.headMult;
        copy.bodyMult = this.bodyMult;
        copy.armsMult = this.armsMult;
        copy.legsMult = this.legsMult;
        copy.armorPenetration = this.armorPenetration;
        copy.baseDamageBonus = this.baseDamageBonus;
        copy.attackDamage = this.attackDamage;
        copy.attackSpeed = this.attackSpeed;
        copy.attackReach = this.attackReach;
        copy.attackKnockback = this.attackKnockback;
        copy.damageBonus = this.damageBonus;
        copy.sweepingRatio = this.sweepingRatio;
        copy.critChance = this.critChance;
        copy.critDamage = this.critDamage;
        copy.armorShred = this.armorShred;
        copy.fireDamageBonus = this.fireDamageBonus;
        copy.magicDamageBonus = this.magicDamageBonus;
        copy.lifesteal = this.lifesteal;
        copy.damageVsUndead = this.damageVsUndead;
        copy.damageVsArthropods = this.damageVsArthropods;
        copy.damageVsPlayers = this.damageVsPlayers;
        copy.trueDamagePercent = this.trueDamagePercent;
        copy.maxDurability = this.maxDurability;
        copy.currentDamage = this.currentDamage;
        copy.repairCost = this.repairCost;
        copy.unbreakable = this.unbreakable;
        copy.clearToolRules = this.clearToolRules;
        copy.toolDefaultMiningSpeed = this.toolDefaultMiningSpeed;
        copy.toolDamagePerBlock = this.toolDamagePerBlock;
        copy.toolRules = new java.util.ArrayList<>();
        for (ToolRuleData rule : this.toolRules) {
            copy.toolRules.add(rule == null ? null : rule.copy());
        }
        return copy;
    }

    @Override
    public String toString() {
        return "WeaponStats{" +
            "head=" + headMult +
            ", body=" + bodyMult +
            ", arms=" + armsMult +
            ", legs=" + legsMult +
            ", atkDmg=" + attackDamage +
            ", atkSpd=" + attackSpeed +
            ", armorPen=" + armorPenetration +
            ", crit=" + critChance + "x" + critDamage +
            ", armorShred=" + armorShred +
            ", fire=" + fireDamageBonus +
            ", magic=" + magicDamageBonus +
            ", lifesteal=" + lifesteal +
            ", vsUndead=" + damageVsUndead +
            ", vsArthro=" + damageVsArthropods +
            ", vsPlayers=" + damageVsPlayers +
            ", truePct=" + trueDamagePercent +
            ", bonus=" + damageBonus +
            ", sweeping=" + sweepingRatio +
            ", maxDur=" + maxDurability +
            ", curDmg=" + currentDamage +
            ", repair=" + repairCost +
            ", unbreakable=" + unbreakable +
            ", clearToolRules=" + clearToolRules +
            ", toolRules=" + toolRules +
            ", toolDefaultSpeed=" + toolDefaultMiningSpeed +
            ", toolDamagePerBlock=" + toolDamagePerBlock +
            '}';
    }
}
