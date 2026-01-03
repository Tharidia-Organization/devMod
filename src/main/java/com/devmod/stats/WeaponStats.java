package com.devmod.stats;

import net.minecraft.nbt.CompoundTag;

import com.devmod.config.Config;

public class WeaponStats {
    // ═══════════════════════════════════════════════════════════════
    // HIT LOCATION MULTIPLIERS
    // ═══════════════════════════════════════════════════════════════

    private float headMult = getDefaultHeadMult();
    private float bodyMult = getDefaultBodyMult();
    private float armsMult = getDefaultArmsMult();
    private float legsMult = getDefaultLegsMult();

    // ═══════════════════════════════════════════════════════════════
    // CORE COMBAT STATS
    // ═══════════════════════════════════════════════════════════════

    private float armorPenetration = 0.0f;   // Percentage of armor ignored (0.0 - 1.0)
    private float baseDamageBonus = 0.0f;    // Flat damage added
    private float attackDamage = 0.0f;       // Base attack damage override (0 = use item default)
    private float attackSpeed = 0.0f;        // Attack speed override (0 = use item default)
    private float attackReach = 0.0f;        // Attack reach override (0 = use item default)
    private float attackKnockback = 0.0f;    // Additional knockback
    private float damageBonus = 0.0f;      // Direct damage bonus (0-1)
    private float sweepingRatio = 0.0f;    // AoE damage multiplier (0-1), uses minecraft:sweeping_damage_ratio

    // ═══════════════════════════════════════════════════════════════
    // CRITICAL HIT
    // ═══════════════════════════════════════════════════════════════

    private float critChance = 0.0f;         // Critical hit chance (0.0 - 1.0)
    private float critDamage = 1.5f;         // Critical damage multiplier
    private float armorShred = 0.0f;         // Reduces target armor (0-66)

    // ═══════════════════════════════════════════════════════════════
    // DAMAGE TYPE BONUSES
    // ═══════════════════════════════════════════════════════════════

    private float fireDamageBonus = 0.0f;    // Bonus fire damage per hit
    private float magicDamageBonus = 0.0f;   // Bonus magic damage per hit
    private float lifesteal = 0.0f;          // Heal percentage of damage dealt (0.0 - 1.0)
    private float damageVsUndead = 0.0f;     // Bonus % vs undead (0-2.0)
    private float damageVsArthropods = 0.0f; // Bonus % vs arthropods (0-2.0)
    private float damageVsPlayers = 0.0f;    // Bonus % vs players (0-2.0)
    private float trueDamagePercent = 0.0f;  // Portion of damage as true (0-1.0)

    // ═══════════════════════════════════════════════════════════════
    // DURABILITY
    // ═══════════════════════════════════════════════════════════════
    private int maxDurability = 0;
    private int currentDamage = 0;
    private int repairCost = 0;
    private boolean unbreakable = false;
    private boolean clearToolRules = false;
    private float toolDefaultMiningSpeed = 1.0f;
    private int toolDamagePerBlock = 1;
    public java.util.List<ToolRuleData> toolRules = new java.util.ArrayList<>();

    public float getHeadMult() {
        return headMult;
    }

    public void setHeadMult(float value) {
        headMult = value;
    }

    public float getBodyMult() {
        return bodyMult;
    }

    public void setBodyMult(float value) {
        bodyMult = value;
    }

    public float getArmsMult() {
        return armsMult;
    }

    public void setArmsMult(float value) {
        armsMult = value;
    }

    public float getLegsMult() {
        return legsMult;
    }

    public void setLegsMult(float value) {
        legsMult = value;
    }

    public float getArmorPenetration() {
        return armorPenetration;
    }

    public void setArmorPenetration(float value) {
        armorPenetration = value;
    }

    public float getBaseDamageBonus() {
        return baseDamageBonus;
    }

    public void setBaseDamageBonus(float value) {
        baseDamageBonus = value;
    }

    public float getAttackDamage() {
        return attackDamage;
    }

    public void setAttackDamage(float value) {
        attackDamage = value;
    }

    public float getAttackSpeed() {
        return attackSpeed;
    }

    public void setAttackSpeed(float value) {
        attackSpeed = value;
    }

    public float getAttackReach() {
        return attackReach;
    }

    public void setAttackReach(float value) {
        attackReach = value;
    }

    public float getAttackKnockback() {
        return attackKnockback;
    }

    public void setAttackKnockback(float value) {
        attackKnockback = value;
    }

    public float getDamageBonus() {
        return damageBonus;
    }

    public void setDamageBonus(float value) {
        damageBonus = value;
    }

    public float getSweepingRatio() {
        return sweepingRatio;
    }

    public void setSweepingRatio(float value) {
        sweepingRatio = value;
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

    public float getArmorShred() {
        return armorShred;
    }

    public void setArmorShred(float value) {
        armorShred = value;
    }

    public float getFireDamageBonus() {
        return fireDamageBonus;
    }

    public void setFireDamageBonus(float value) {
        fireDamageBonus = value;
    }

    public float getMagicDamageBonus() {
        return magicDamageBonus;
    }

    public void setMagicDamageBonus(float value) {
        magicDamageBonus = value;
    }

    public float getLifesteal() {
        return lifesteal;
    }

    public void setLifesteal(float value) {
        lifesteal = value;
    }

    public float getDamageVsUndead() {
        return damageVsUndead;
    }

    public void setDamageVsUndead(float value) {
        damageVsUndead = value;
    }

    public float getDamageVsArthropods() {
        return damageVsArthropods;
    }

    public void setDamageVsArthropods(float value) {
        damageVsArthropods = value;
    }

    public float getDamageVsPlayers() {
        return damageVsPlayers;
    }

    public void setDamageVsPlayers(float value) {
        damageVsPlayers = value;
    }

    public float getTrueDamagePercent() {
        return trueDamagePercent;
    }

    public void setTrueDamagePercent(float value) {
        trueDamagePercent = value;
    }

    public int getMaxDurability() {
        return maxDurability;
    }

    public void setMaxDurability(int value) {
        maxDurability = value;
    }

    public int getCurrentDamage() {
        return currentDamage;
    }

    public void setCurrentDamage(int value) {
        currentDamage = value;
    }

    public int getRepairCost() {
        return repairCost;
    }

    public void setRepairCost(int value) {
        repairCost = value;
    }

    public boolean isUnbreakable() {
        return unbreakable;
    }

    public void setUnbreakable(boolean value) {
        unbreakable = value;
    }

    public boolean isClearToolRules() {
        return clearToolRules;
    }

    public void setClearToolRules(boolean value) {
        clearToolRules = value;
    }

    public float getToolDefaultMiningSpeed() {
        return toolDefaultMiningSpeed;
    }

    public void setToolDefaultMiningSpeed(float value) {
        toolDefaultMiningSpeed = value;
    }

    public int getToolDamagePerBlock() {
        return toolDamagePerBlock;
    }

    public void setToolDamagePerBlock(int value) {
        toolDamagePerBlock = value;
    }

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
        if (!isDefaultValue(critDamage, 1.5f)) tag.putFloat("CritDmg", critDamage);
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
        if (tag.contains("HeadMult")) stats.setHeadMult(tag.getFloat("HeadMult"));
        if (tag.contains("BodyMult")) stats.setBodyMult(tag.getFloat("BodyMult"));
        if (tag.contains("ArmsMult")) stats.setArmsMult(tag.getFloat("ArmsMult"));
        if (tag.contains("LegsMult")) stats.setLegsMult(tag.getFloat("LegsMult"));

        // Core stats
        if (tag.contains("ArmorPen")) stats.setArmorPenetration(tag.getFloat("ArmorPen"));
        if (tag.contains("BaseDmg")) stats.setBaseDamageBonus(tag.getFloat("BaseDmg"));
        if (tag.contains("AtkDmg")) stats.setAttackDamage(tag.getFloat("AtkDmg"));
        if (tag.contains("AtkSpd")) stats.setAttackSpeed(tag.getFloat("AtkSpd"));
        if (tag.contains("AtkRch")) stats.setAttackReach(tag.getFloat("AtkRch"));
        if (tag.contains("AtkKB")) stats.setAttackKnockback(tag.getFloat("AtkKB"));
        if (tag.contains("DmgBonus")) stats.setDamageBonus(tag.getFloat("DmgBonus"));
        if (tag.contains("SweepRatio")) stats.setSweepingRatio(tag.getFloat("SweepRatio"));

        // Critical hit
        if (tag.contains("CritCh")) stats.setCritChance(tag.getFloat("CritCh"));
        if (tag.contains("CritDmg")) stats.setCritDamage(tag.getFloat("CritDmg"));
        if (tag.contains("ArmorShred")) stats.setArmorShred(tag.getFloat("ArmorShred"));

        // Damage type bonuses
        if (tag.contains("FireDmg")) stats.setFireDamageBonus(tag.getFloat("FireDmg"));
        if (tag.contains("MagicDmg")) stats.setMagicDamageBonus(tag.getFloat("MagicDmg"));
        if (tag.contains("Lifesteal")) stats.setLifesteal(tag.getFloat("Lifesteal"));
        if (tag.contains("VsUndead")) stats.setDamageVsUndead(tag.getFloat("VsUndead"));
        if (tag.contains("VsArthro")) stats.setDamageVsArthropods(tag.getFloat("VsArthro"));
        if (tag.contains("VsPlayers")) stats.setDamageVsPlayers(tag.getFloat("VsPlayers"));
        if (tag.contains("TrueDmgPct")) stats.setTrueDamagePercent(tag.getFloat("TrueDmgPct"));

        // Durability
        if (tag.contains("MaxDur")) stats.setMaxDurability(tag.getInt("MaxDur"));
        if (tag.contains("CurDmg")) stats.setCurrentDamage(tag.getInt("CurDmg"));
        if (tag.contains("Repair")) stats.setRepairCost(tag.getInt("Repair"));
        if (tag.contains("Unbreakable")) stats.setUnbreakable(tag.getBoolean("Unbreakable"));
        if (tag.contains("ClearToolRules")) stats.setClearToolRules(tag.getBoolean("ClearToolRules"));
        if (tag.contains("ToolRules")) {
            net.minecraft.nbt.CompoundTag tool = tag.getCompound("ToolRules");
            stats.setToolDefaultMiningSpeed(tool.contains("DefaultSpeed") ? tool.getFloat("DefaultSpeed") : 1.0f);
            stats.setToolDamagePerBlock(tool.contains("DamagePerBlock") ? tool.getInt("DamagePerBlock") : 1);
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

    private static boolean isZero(float value) {
        return Float.compare(value, 0.0f) == 0;
    }

    private static boolean isDefaultValue(float value, float expected) {
        return Float.compare(value, expected) == 0;
    }

    /**
     * Check if all values are at defaults (no modifications).
     */
    public boolean isDefault() {
        return isDefaultValue(headMult, getDefaultHeadMult())
            && isDefaultValue(bodyMult, getDefaultBodyMult())
            && isDefaultValue(armsMult, getDefaultArmsMult())
            && isDefaultValue(legsMult, getDefaultLegsMult())
            && isZero(armorPenetration)
            && isZero(baseDamageBonus)
            && isZero(attackDamage)
            && isZero(attackSpeed)
            && isZero(attackReach)
            && isZero(attackKnockback)
            && isZero(damageBonus)
            && isZero(sweepingRatio)
            && isZero(critChance)
            && isDefaultValue(critDamage, 1.5f)
            && isZero(armorShred)
            && isZero(fireDamageBonus)
            && isZero(magicDamageBonus)
            && isZero(lifesteal)
            && isZero(damageVsUndead)
            && isZero(damageVsArthropods)
            && isZero(damageVsPlayers)
            && isZero(trueDamagePercent)
            && maxDurability == 0
            && currentDamage == 0
            && repairCost == 0
            && !unbreakable
            && !clearToolRules
            && toolRules.isEmpty()
            && isDefaultValue(toolDefaultMiningSpeed, 1.0f)
            && toolDamagePerBlock == 1;
    }

    /**
     * Create a copy of these stats.
     */
    public WeaponStats copy() {
        WeaponStats copy = new WeaponStats();
        copy.setHeadMult(this.getHeadMult());
        copy.setBodyMult(this.getBodyMult());
        copy.setArmsMult(this.getArmsMult());
        copy.setLegsMult(this.getLegsMult());
        copy.setArmorPenetration(this.getArmorPenetration());
        copy.setBaseDamageBonus(this.getBaseDamageBonus());
        copy.setAttackDamage(this.getAttackDamage());
        copy.setAttackSpeed(this.getAttackSpeed());
        copy.setAttackReach(this.getAttackReach());
        copy.setAttackKnockback(this.getAttackKnockback());
        copy.setDamageBonus(this.getDamageBonus());
        copy.setSweepingRatio(this.getSweepingRatio());
        copy.setCritChance(this.getCritChance());
        copy.setCritDamage(this.getCritDamage());
        copy.setArmorShred(this.getArmorShred());
        copy.setFireDamageBonus(this.getFireDamageBonus());
        copy.setMagicDamageBonus(this.getMagicDamageBonus());
        copy.setLifesteal(this.getLifesteal());
        copy.setDamageVsUndead(this.getDamageVsUndead());
        copy.setDamageVsArthropods(this.getDamageVsArthropods());
        copy.setDamageVsPlayers(this.getDamageVsPlayers());
        copy.setTrueDamagePercent(this.getTrueDamagePercent());
        copy.setMaxDurability(this.getMaxDurability());
        copy.setCurrentDamage(this.getCurrentDamage());
        copy.setRepairCost(this.getRepairCost());
        copy.setUnbreakable(this.isUnbreakable());
        copy.setClearToolRules(this.isClearToolRules());
        copy.setToolDefaultMiningSpeed(this.getToolDefaultMiningSpeed());
        copy.setToolDamagePerBlock(this.getToolDamagePerBlock());
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
