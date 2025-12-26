package com.devmod.client.ui.editor.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import com.devmod.client.ui.editor.EditorSection;
import com.devmod.client.ui.editor.components.EditorSlider;
import com.devmod.client.ui.editor.components.EditorTextField;
import com.devmod.client.ui.editor.components.EditorToggle;
import com.devmod.client.ui.editor.components.SourceBadge;
import com.devmod.client.ui.editor.core.EditorDimensions;
import com.devmod.client.ui.editor.core.ResponsiveLayout;
import com.devmod.client.ui.editor.core.UIConstants;
import com.devmod.client.ui.editor.debug.DebugInfoSection;
import com.devmod.client.ui.editor.debug.ItemDebugInfo;
import com.devmod.client.ui.editor.debug.ValueComparison;
import com.devmod.client.ui.editor.sections.SimpleHeaderSection;
import com.devmod.client.ui.editor.sections.SliderSectionAdapter;
import com.devmod.client.ui.editor.sections.TextNoteSection;
import com.devmod.client.ui.editor.sections.ToggleSectionAdapter;
import com.devmod.config.WeaponConfigManager;
import com.devmod.stats.WeaponStats;

/**
 * UI components and section builders for WeaponModule.
 */
public class WeaponModuleUI {

    private final WeaponModule module;
    private final WeaponModuleCore core;
    private final WeaponModuleVariants variants;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Hit Location Tab
    // ═══════════════════════════════════════════════════════════════

    EditorSlider headMultSlider;
    EditorSlider bodyMultSlider;
    EditorSlider armsMultSlider;
    EditorSlider legsMultSlider;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Combat Tab
    // ═══════════════════════════════════════════════════════════════

    EditorSlider attackDamageSlider;
    EditorSlider attackSpeedSlider;
    EditorSlider attackReachSlider;
    EditorSlider attackKnockbackSlider;
    EditorSlider armorPenetrationSlider;
    EditorSlider baseDamageBonusSlider;
    EditorSlider damageBonusSlider;
    EditorSlider sweepingRatioSlider;
    EditorSlider armorShredSlider;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Special Tab
    // ═══════════════════════════════════════════════════════════════

    EditorToggle critEnabledToggle;
    EditorSlider critChanceSlider;
    EditorSlider critDamageSlider;
    EditorToggle lifestealEnabledToggle;
    EditorSlider lifestealSlider;
    EditorToggle fireDamageEnabledToggle;
    EditorSlider fireDamageSlider;
    EditorToggle magicDamageEnabledToggle;
    EditorSlider magicDamageSlider;
    EditorSlider vsUndeadSlider;
    EditorSlider vsArthroSlider;
    EditorSlider vsPlayersSlider;
    EditorSlider trueDamageSlider;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Durability Tab
    // ═══════════════════════════════════════════════════════════════

    EditorSlider maxDurabilitySlider;
    EditorSlider currentDamageSlider;
    EditorSlider repairCostSlider;
    EditorToggle unbreakableToggle;
    EditorToggle clearToolRulesToggle;
    EditorSlider toolDefaultSpeedSlider;
    EditorSlider toolDamagePerBlockSlider;
    final List<ToolRuleSection> toolRuleSections = new ArrayList<>();

    public WeaponModuleUI(WeaponModule module, WeaponModuleCore core, WeaponModuleVariants variants) {
        this.module = module;
        this.core = core;
        this.variants = variants;
    }

    // ═══════════════════════════════════════════════════════════════
    // CREATE ALL COMPONENTS
    // ═══════════════════════════════════════════════════════════════

    public void createAllComponents(SourceBadge.Source dataSource) {
        createHitLocationComponents(dataSource);
        createCombatComponents(dataSource);
        createSpecialComponents(dataSource);
        createDamageTypeComponents(dataSource);
        createDurabilityComponents(dataSource);
        createToolComponents(dataSource);
    }

    // ═══════════════════════════════════════════════════════════════
    // HIT LOCATION TAB
    // ═══════════════════════════════════════════════════════════════

    private void createHitLocationComponents(SourceBadge.Source dataSource) {
        WeaponStats stats = core.getStats();

        headMultSlider = new EditorSlider("headMult", "Head Multiplier", 0.5f, 3.0f, 1.5f)
            .step(0.1f)
            .format("%.1f")
            .suffix("x")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .source(dataSource)
            .info("Damage multiplier when hitting the head. 1.5x = headshots deal 50% more damage.")
            .onChange(v -> { stats.headMult = v; module.markDirty("Head multiplier"); });

        bodyMultSlider = new EditorSlider("bodyMult", "Body Multiplier", 0.5f, 2.0f, 1.0f)
            .step(0.1f)
            .format("%.1f")
            .suffix("x")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .source(dataSource)
            .info("Damage multiplier for body/torso hits. 1.0 = normal damage.")
            .onChange(v -> { stats.bodyMult = v; module.markDirty("Body multiplier"); });

        armsMultSlider = new EditorSlider("armsMult", "Arms Multiplier", 0.3f, 1.5f, 0.8f)
            .step(0.1f)
            .format("%.1f")
            .suffix("x")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .source(dataSource)
            .info("Damage multiplier when hitting arms. 0.8x = arm hits deal 20% less damage.")
            .onChange(v -> { stats.armsMult = v; module.markDirty("Arms multiplier"); });

        legsMultSlider = new EditorSlider("legsMult", "Legs Multiplier", 0.3f, 1.5f, 0.7f)
            .step(0.1f)
            .format("%.1f")
            .suffix("x")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .source(dataSource)
            .info("Damage multiplier when hitting legs. 0.7x = leg hits deal 30% less damage.")
            .onChange(v -> { stats.legsMult = v; module.markDirty("Legs multiplier"); });
    }

    public List<EditorSection> getHitLocationSections() {
        return List.of(
            new SliderSectionAdapter(headMultSlider),
            new SliderSectionAdapter(bodyMultSlider),
            new SliderSectionAdapter(armsMultSlider),
            new SliderSectionAdapter(legsMultSlider)
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // COMBAT TAB
    // ═══════════════════════════════════════════════════════════════

    private void createCombatComponents(SourceBadge.Source dataSource) {
        WeaponStats stats = core.getStats();

        attackDamageSlider = new EditorSlider("atkDmg", "Attack Damage", 0f, 50f, 0f)
            .step(0.5f)
            .format("%.1f")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .source(dataSource)
            .info("Base damage added to attacks. Final = (Base + Attack Damage) * Multipliers. Uses minecraft:attack_damage attribute.")
            .onChange(v -> { stats.attackDamage = v; module.markDirty("Attack damage"); });

        attackSpeedSlider = new EditorSlider("atkSpd", "Attack Speed", -5f, 4f, 0f)
            .step(0.1f)
            .format("%.1f")
            .trackColor(UIConstants.SliderColors.SPEED)
            .showInput(true)
            .source(dataSource)
            .info("Modifier to attack cooldown. Player base is 4.0, so -2.4 means 1.6 attacks/sec. DPS = Damage * (4 + Speed).")
            .onChange(v -> { stats.attackSpeed = v; module.markDirty("Attack speed"); });

        attackReachSlider = new EditorSlider("atkRch", "Attack Reach", 0f, 6f, 0f)
            .step(0.1f)
            .format("%.1f")
            .suffix(" blocks")
            .trackColor(UIConstants.SliderColors.NEUTRAL)
            .showInput(true)
            .source(dataSource)
            .info("Additional melee range. Player base reach is 3.0 blocks. Uses minecraft:entity_interaction_range.")
            .onChange(v -> { stats.attackReach = v; module.markDirty("Attack reach"); });

        attackKnockbackSlider = new EditorSlider("atkKB", "Knockback", 0f, 5f, 0f)
            .step(0.1f)
            .format("%.1f")
            .trackColor(UIConstants.SliderColors.NEUTRAL)
            .showInput(true)
            .source(dataSource)
            .info("Additional knockback strength. 1.0 = one Knockback enchantment level. Uses minecraft:attack_knockback.")
            .onChange(v -> { stats.attackKnockback = v; module.markDirty("Knockback"); });

        damageBonusSlider = new EditorSlider("damageBonus", "Damage Bonus", 0f, 100f, 0f)
            .step(1f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.NEUTRAL)
            .showInput(true)
            .source(dataSource)
            .info("Percentage multiplier to final damage. 50% = 1.5x damage. Uses devmod:damage_bonus custom attribute.")
            .onChange(v -> { stats.damageBonus = v / 100f; module.markDirty("Damage bonus"); });

        sweepingRatioSlider = new EditorSlider("sweepRatio", "Sweeping Ratio", 0f, 100f, 0f)
            .step(1f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.SPECIAL)
            .showInput(true)
            .source(dataSource)
            .info("AoE sweep damage ratio. 100% = full damage to nearby enemies. Similar to Sweeping Edge. Uses minecraft:sweeping_damage_ratio.")
            .onChange(v -> { stats.sweepingRatio = v / 100f; module.markDirty("Sweeping ratio"); });

        armorPenetrationSlider = new EditorSlider("armorPen", "Armor Penetration", 0f, 100f, 0f)
            .step(1f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.SPECIAL)
            .showInput(true)
            .source(dataSource)
            .info("Percentage of target's armor ignored. 50% pen vs 20 armor = effective 10 armor. Applied before damage reduction.")
            .onChange(v -> { stats.armorPenetration = v / 100f; module.markDirty("Armor penetration"); });

        baseDamageBonusSlider = new EditorSlider("baseDmg", "Base Damage Bonus", -50f, 50f, 0f)
            .step(0.5f)
            .format("+%.1f")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .source(dataSource)
            .info("Flat bonus to weapon's base damage before multipliers. Negative values reduce damage.")
            .onChange(v -> { stats.baseDamageBonus = v; module.markDirty("Base damage bonus"); });

        armorShredSlider = new EditorSlider("armorShred", "Armor Shred", 0f, 66f, 0f)
            .step(1f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .source(dataSource)
            .info("Permanently reduces target's armor on hit. Stacks up to cap. Uses devmod:armor_shred attribute.")
            .onChange(v -> { stats.armorShred = v; module.markDirty("Armor shred"); });
    }

    public List<EditorSection> getCombatSections() {
        return withDps(List.of(
            new SliderSectionAdapter(attackDamageSlider),
            new SliderSectionAdapter(attackSpeedSlider),
            new SliderSectionAdapter(attackReachSlider),
            new SliderSectionAdapter(attackKnockbackSlider),
            new SliderSectionAdapter(damageBonusSlider),
            new SliderSectionAdapter(sweepingRatioSlider),
            new SliderSectionAdapter(armorPenetrationSlider),
            new SliderSectionAdapter(baseDamageBonusSlider),
            new SliderSectionAdapter(armorShredSlider)
        ));
    }

    // ═══════════════════════════════════════════════════════════════
    // SPECIAL TAB
    // ═══════════════════════════════════════════════════════════════

    private void createSpecialComponents(SourceBadge.Source dataSource) {
        WeaponStats stats = core.getStats();

        critEnabledToggle = new EditorToggle("critEnabled", "Enable Critical Hits", stats.critChance > 0)
            .source(dataSource)
            .tooltip("Enable critical hit mechanics for this weapon")
            .onChange(enabled -> {
                critChanceSlider.setEnabled(enabled);
                critDamageSlider.setEnabled(enabled);
                if (!enabled) {
                    stats.critChance = 0f;
                    critChanceSlider.setValue(0f);
                }
                module.markDirty("Critical hits " + (enabled ? "enabled" : "disabled"));
            });

        critChanceSlider = new EditorSlider("critCh", "Critical Chance", 0f, 100f, 0f)
            .step(1f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.PERCENT)
            .showInput(true)
            .source(dataSource)
            .enabled(stats.critChance > 0)
            .info("Chance to deal critical hit. Rolled on each attack. Uses devmod:crit_chance attribute.")
            .onChange(v -> { stats.critChance = v / 100f; module.markDirty("Critical chance"); });

        critDamageSlider = new EditorSlider("critDmg", "Critical Damage", 1f, 5f, 1.5f)
            .step(0.1f)
            .format("%.1f")
            .suffix("x")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .source(dataSource)
            .enabled(stats.critChance > 0)
            .info("Damage multiplier on critical hit. 2.0x = double damage. Uses devmod:crit_multiplier attribute.")
            .onChange(v -> { stats.critDamage = v; module.markDirty("Critical damage"); });

        lifestealEnabledToggle = new EditorToggle("lifestealEnabled", "Enable Lifesteal", stats.lifesteal > 0)
            .source(dataSource)
            .tooltip("Heal a percentage of damage dealt")
            .onChange(enabled -> {
                lifestealSlider.setEnabled(enabled);
                if (!enabled) {
                    stats.lifesteal = 0f;
                    lifestealSlider.setValue(0f);
                }
                module.markDirty("Lifesteal " + (enabled ? "enabled" : "disabled"));
            });

        lifestealSlider = new EditorSlider("lifesteal", "Lifesteal", 0f, 50f, 0f)
            .step(1f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.SPECIAL)
            .showInput(true)
            .source(dataSource)
            .enabled(stats.lifesteal > 0)
            .info("Heals attacker for percentage of damage dealt. 10% lifesteal on 20 damage = 2 HP healed.")
            .onChange(v -> { stats.lifesteal = v / 100f; module.markDirty("Lifesteal"); });

        fireDamageEnabledToggle = new EditorToggle("fireEnabled", "Enable Fire Damage", stats.fireDamageBonus > 0)
            .source(dataSource)
            .tooltip("Add fire damage to attacks")
            .onChange(enabled -> {
                fireDamageSlider.setEnabled(enabled);
                if (!enabled) {
                    stats.fireDamageBonus = 0f;
                    fireDamageSlider.setValue(0f);
                }
                module.markDirty("Fire damage " + (enabled ? "enabled" : "disabled"));
            });

        fireDamageSlider = new EditorSlider("fireDmg", "Fire Damage", 0f, 20f, 0f)
            .step(0.5f)
            .format("+%.1f")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .source(dataSource)
            .enabled(stats.fireDamageBonus > 0)
            .info("Extra fire damage added to attacks. Sets target on fire. Ignores some armor.")
            .onChange(v -> { stats.fireDamageBonus = v; module.markDirty("Fire damage"); });

        magicDamageEnabledToggle = new EditorToggle("magicEnabled", "Enable Magic Damage", stats.magicDamageBonus > 0)
            .source(dataSource)
            .tooltip("Add magic damage to attacks")
            .onChange(enabled -> {
                magicDamageSlider.setEnabled(enabled);
                if (!enabled) {
                    stats.magicDamageBonus = 0f;
                    magicDamageSlider.setValue(0f);
                }
                module.markDirty("Magic damage " + (enabled ? "enabled" : "disabled"));
            });

        magicDamageSlider = new EditorSlider("magicDmg", "Magic Damage", 0f, 20f, 0f)
            .step(0.5f)
            .format("+%.1f")
            .trackColor(UIConstants.SliderColors.SPECIAL)
            .showInput(true)
            .source(dataSource)
            .enabled(stats.magicDamageBonus > 0)
            .info("Extra magic damage added to attacks. Bypasses physical armor. Affected by magic resistance.")
            .onChange(v -> { stats.magicDamageBonus = v; module.markDirty("Magic damage"); });
    }

    public List<EditorSection> getSpecialSections() {
        return withDps(List.of(
            new ToggleSectionAdapter(critEnabledToggle),
            new SliderSectionAdapter(critChanceSlider),
            new SliderSectionAdapter(critDamageSlider),
            new ToggleSectionAdapter(lifestealEnabledToggle),
            new SliderSectionAdapter(lifestealSlider)
        ));
    }

    // ═══════════════════════════════════════════════════════════════
    // DAMAGE TYPE TAB
    // ═══════════════════════════════════════════════════════════════

    private void createDamageTypeComponents(SourceBadge.Source dataSource) {
        WeaponStats stats = core.getStats();

        vsUndeadSlider = new EditorSlider("vsUndead", "Damage vs Undead", 0f, 200f, 0f)
            .step(1f)
            .format("+%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .source(dataSource)
            .info("Bonus damage vs undead mobs (Zombies, Skeletons, Phantoms, etc.). Similar to Smite enchant.")
            .onChange(v -> { stats.damageVsUndead = v / 100f; module.markDirty("Damage vs undead"); });

        vsArthroSlider = new EditorSlider("vsArthro", "Damage vs Arthropods", 0f, 200f, 0f)
            .step(1f)
            .format("+%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .source(dataSource)
            .info("Bonus damage vs arthropods (Spiders, Silverfish, Bees, Endermites). Similar to Bane of Arthropods.")
            .onChange(v -> { stats.damageVsArthropods = v / 100f; module.markDirty("Damage vs arthropods"); });

        vsPlayersSlider = new EditorSlider("vsPlayers", "Damage vs Players", 0f, 200f, 0f)
            .step(1f)
            .format("+%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .source(dataSource)
            .info("Bonus damage specifically against players. PvP-focused modifier.")
            .onChange(v -> { stats.damageVsPlayers = v / 100f; module.markDirty("Damage vs players"); });

        trueDamageSlider = new EditorSlider("trueDmg", "True Damage Portion", 0f, 100f, 0f)
            .step(1f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.SPECIAL)
            .showInput(true)
            .source(dataSource)
            .info("Percentage of damage that bypasses ALL armor and protection. 100% = full true damage.")
            .onChange(v -> { stats.trueDamagePercent = v / 100f; module.markDirty("True damage"); });
    }

    public List<EditorSection> getDamageTypeSections() {
        return withDps(List.of(
            new SliderSectionAdapter(vsUndeadSlider),
            new SliderSectionAdapter(vsArthroSlider),
            new SliderSectionAdapter(vsPlayersSlider),
            new ToggleSectionAdapter(fireDamageEnabledToggle),
            new SliderSectionAdapter(fireDamageSlider),
            new ToggleSectionAdapter(magicDamageEnabledToggle),
            new SliderSectionAdapter(magicDamageSlider),
            new SliderSectionAdapter(trueDamageSlider)
        ));
    }

    // ═══════════════════════════════════════════════════════════════
    // DURABILITY TAB
    // ═══════════════════════════════════════════════════════════════

    private void createDurabilityComponents(SourceBadge.Source dataSource) {
        WeaponStats stats = core.getStats();

        maxDurabilitySlider = new EditorSlider("maxDur", "Max Durability", 0f, 4096f, 0f)
            .step(16f)
            .format("%.0f")
            .trackColor(UIConstants.SliderColors.DURABILITY)
            .showInput(true)
            .source(dataSource)
            .info("Maximum durability points. Netherite sword = 2031. Item breaks when current damage reaches this value.")
            .onChange(v -> { stats.maxDurability = Math.round(v); module.markDirty("Max durability"); });

        currentDamageSlider = new EditorSlider("curDmg", "Current Damage", 0f, 4096f, 0f)
            .step(1f)
            .format("%.0f")
            .trackColor(UIConstants.SliderColors.DURABILITY)
            .showInput(true)
            .source(dataSource)
            .info("Current damage taken. 0 = full durability. When this reaches max durability, item breaks.")
            .onChange(v -> { stats.currentDamage = Math.round(v); module.markDirty("Current damage"); });

        repairCostSlider = new EditorSlider("repair", "Repair Cost", 0f, 100f, 0f)
            .step(1f)
            .format("%.0f")
            .trackColor(UIConstants.SliderColors.DURABILITY)
            .showInput(true)
            .source(dataSource)
            .info("XP level cost to repair/rename in anvil. Increases each repair. Max 39 before 'Too Expensive'.")
            .onChange(v -> { stats.repairCost = Math.round(v); module.markDirty("Repair cost"); });

        unbreakableToggle = new EditorToggle("unbreakable", "Unbreakable", stats.unbreakable)
            .source(dataSource)
            .tooltip("When enabled, item never loses durability. Sets minecraft:unbreakable component.")
            .onChange(v -> { stats.unbreakable = v; module.markDirty("Unbreakable"); });

        clearToolRulesToggle = new EditorToggle("clearTools", "Clear Tool Rules (tags/speeds)", stats.clearToolRules)
            .source(dataSource)
            .tooltip("Removes tool rules component; useful to bypass mining penalties (apply to persist)")
            .onChange(v -> { stats.clearToolRules = v; module.markDirty("Clear tool rules"); });
    }

    public List<EditorSection> getDurabilitySections() {
        return List.of(
            new SliderSectionAdapter(maxDurabilitySlider),
            new SliderSectionAdapter(currentDamageSlider),
            new SliderSectionAdapter(repairCostSlider),
            new ToggleSectionAdapter(unbreakableToggle)
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // TOOL RULES TAB
    // ═══════════════════════════════════════════════════════════════

    private void createToolComponents(SourceBadge.Source dataSource) {
        WeaponStats stats = core.getStats();
        toolRuleSections.clear();

        toolDefaultSpeedSlider = new EditorSlider("toolDefaultSpeed", "Default Mining Speed", 0f, 64f, stats.toolDefaultMiningSpeed)
            .step(0.1f)
            .format("%.1f")
            .trackColor(UIConstants.SliderColors.SPECIAL)
            .onChange(v -> { stats.toolDefaultMiningSpeed = v; module.markDirty("Tool default speed"); });

        toolDamagePerBlockSlider = new EditorSlider("toolDamagePerBlock", "Damage Per Block", 0f, 64f, stats.toolDamagePerBlock)
            .step(1f)
            .format("%.0f")
            .trackColor(UIConstants.SliderColors.DURABILITY)
            .onChange(v -> { stats.toolDamagePerBlock = Math.round(v); module.markDirty("Tool damage per block"); });

        int maxRules = 3;
        for (int i = 0; i < maxRules; i++) {
            WeaponStats.ToolRuleData data = (stats.toolRules.size() > i)
                ? stats.toolRules.get(i)
                : new WeaponStats.ToolRuleData();
            ToolRuleSection row = new ToolRuleSection("toolRule" + i, i + 1, data);
            toolRuleSections.add(row);
        }
    }

    public List<EditorSection> getToolSections() {
        List<EditorSection> sections = new ArrayList<>();
        sections.add(new SliderSectionAdapter(toolDefaultSpeedSlider));
        sections.add(new SliderSectionAdapter(toolDamagePerBlockSlider));
        toolRuleSections.forEach(sections::add);
        sections.add(new ToggleSectionAdapter(clearToolRulesToggle));
        sections.add(new TextNoteSection(
            "toolRulesNote",
            "Block tags (e.g. minecraft:mineable/pickaxe) with speed + drops",
            24,
            UIConstants.Spacing.SM,
            6,
            UIConstants.Text.SECONDARY()
        ));
        return sections;
    }

    // ═══════════════════════════════════════════════════════════════
    // MACE / TRIDENT VARIANT SECTIONS
    // ═══════════════════════════════════════════════════════════════

    public List<EditorSection> getMaceSections() {
        return withDps(List.of(
            new SimpleHeaderSection(
                "maceHeader",
                "Smash Attack",
                EditorDimensions.SECTION_HEADER_HEIGHT,
                UIConstants.Spacing.SM,
                4
            ),
            new SliderSectionAdapter(variants.getSmashBonusSlider()),
            new SliderSectionAdapter(variants.getSmashCapSlider()),
            new SliderSectionAdapter(variants.getSmashKnockbackSlider()),
            new SliderSectionAdapter(variants.getSmashAoeDamageSlider()),
            new ToggleSectionAdapter(variants.getSmashFallNegationToggle())
        ));
    }

    public List<EditorSection> getTridentSections() {
        return withDps(List.of(
            new SimpleHeaderSection(
                "tridentHeader",
                "Throw / Riptide",
                EditorDimensions.SECTION_HEADER_HEIGHT,
                UIConstants.Spacing.SM,
                4
            ),
            new SliderSectionAdapter(variants.getThrowDamageSlider()),
            new SliderSectionAdapter(variants.getThrowSpeedSlider()),
            new SliderSectionAdapter(variants.getLoyaltySpeedSlider()),
            new SliderSectionAdapter(variants.getRiptideDistanceSlider()),
            new SliderSectionAdapter(variants.getRiptideDamageSlider()),
            new ToggleSectionAdapter(variants.getRiptideRequiresWaterToggle())
        ));
    }

    // ═══════════════════════════════════════════════════════════════
    // DEBUG SECTIONS
    // ═══════════════════════════════════════════════════════════════

    public List<EditorSection> getDebugSections(ItemStack item) {
        ItemDebugInfo info = buildDebugInfo(item);
        List<ValueComparison> comparisons = buildValueComparisons(item);
        List<String> history = module.getRecentHistoryEntriesPublic(8);
        List<String> nbtLines = DebugInfoSection.formatNbtLines(core.getCustomDataTag(item), 12);
        return List.of(new DebugInfoSection(info, comparisons, history, nbtLines, () -> copyDebugInfo(item)));
    }

    private ItemDebugInfo buildDebugInfo(ItemStack item) {
        var rawItem = Objects.requireNonNull(item.getItem(), "item cannot be null");
        var key = BuiltInRegistries.ITEM.getKey(rawItem);
        String registryName = key == null ? "<unknown>" : key.toString();
        CompoundTag customTag = core.getCustomDataTag(item);
        int tagCount = customTag.getAllKeys().size();
        boolean hasCustomData = customTag.contains(WeaponModuleCore.NBT_KEY);
        return new ItemDebugInfo(registryName, item.getCount(), item.getDamageValue(), item.getMaxDamage(),
            tagCount, hasCustomData);
    }

    private List<ValueComparison> buildValueComparisons(ItemStack item) {
        List<ValueComparison> comparisons = new ArrayList<>();
        CompoundTag customTag = core.getCustomDataTag(item);
        boolean hasSpecific = customTag.contains(WeaponModuleCore.NBT_KEY);
        boolean hasGlobal = WeaponConfigManager.hasGlobalConfig(item.getItem());
        boolean hasServerStats = hasSpecific || hasGlobal;
        WeaponStats serverStats = core.resolveServerStats(customTag, item);
        WeaponStats baseline = core.getOriginalStats() == null ? new WeaponStats() : core.getOriginalStats();
        WeaponStats stats = core.getStats();

        comparisons.add(makeComparison("Head multiplier", baseline.headMult, serverStats.headMult, stats.headMult, hasServerStats));
        comparisons.add(makeComparison("Body multiplier", baseline.bodyMult, serverStats.bodyMult, stats.bodyMult, hasServerStats));
        comparisons.add(makeComparison("Arms multiplier", baseline.armsMult, serverStats.armsMult, stats.armsMult, hasServerStats));
        comparisons.add(makeComparison("Legs multiplier", baseline.legsMult, serverStats.legsMult, stats.legsMult, hasServerStats));

        comparisons.add(makeComparison("Attack damage", baseline.attackDamage, serverStats.attackDamage, stats.attackDamage, hasServerStats));
        comparisons.add(makeComparison("Attack speed", baseline.attackSpeed, serverStats.attackSpeed, stats.attackSpeed, hasServerStats));
        comparisons.add(makeComparison("Attack reach", baseline.attackReach, serverStats.attackReach, stats.attackReach, hasServerStats));
        comparisons.add(makeComparison("Attack knockback", baseline.attackKnockback, serverStats.attackKnockback, stats.attackKnockback, hasServerStats));
        comparisons.add(makeComparison("Armor penetration (%)", baseline.armorPenetration * 100, serverStats.armorPenetration * 100, stats.armorPenetration * 100, hasServerStats));
        comparisons.add(makeComparison("Base damage bonus", baseline.baseDamageBonus, serverStats.baseDamageBonus, stats.baseDamageBonus, hasServerStats));

        comparisons.add(makeComparison("Crit chance (%)", baseline.critChance * 100, serverStats.critChance * 100, stats.critChance * 100, hasServerStats));
        comparisons.add(makeComparison("Crit damage", baseline.critDamage, serverStats.critDamage, stats.critDamage, hasServerStats));

        comparisons.add(makeComparison("Lifesteal (%)", baseline.lifesteal * 100, serverStats.lifesteal * 100, stats.lifesteal * 100, hasServerStats));
        comparisons.add(makeComparison("Fire damage", baseline.fireDamageBonus, serverStats.fireDamageBonus, stats.fireDamageBonus, hasServerStats));
        comparisons.add(makeComparison("Magic damage", baseline.magicDamageBonus, serverStats.magicDamageBonus, stats.magicDamageBonus, hasServerStats));
        return comparisons;
    }

    private ValueComparison makeComparison(String label, double originalValue, double serverValue, double currentValue, boolean hasServerStats) {
        double server = hasServerStats ? serverValue : Double.NaN;
        boolean mismatch = hasServerStats && Math.abs(serverValue - currentValue) > WeaponModuleCore.EPSILON;
        boolean modified = Math.abs(currentValue - originalValue) > WeaponModuleCore.EPSILON;
        return new ValueComparison(label, originalValue, currentValue, server, modified, mismatch);
    }

    private void copyDebugInfo(ItemStack item) {
        ItemDebugInfo info = buildDebugInfo(item);
        List<ValueComparison> comparisons = buildValueComparisons(item);
        List<String> history = module.getRecentHistoryEntriesPublic(8);
        List<String> nbtLines = DebugInfoSection.formatNbtLines(core.getCustomDataTag(item), 16);
        String payload = buildDebugClipboardText(info, comparisons, history, nbtLines);

        Minecraft mc = Minecraft.getInstance();
        String safePayload = Objects.requireNonNullElse(payload, "");
        if (mc != null && mc.keyboardHandler != null) {
            mc.keyboardHandler.setClipboard(Objects.requireNonNull(safePayload, "payload cannot be null"));
        }
        module.reportStatusPublic("Debug info copied!", UIConstants.Accent.GREEN());
    }

    private String buildDebugClipboardText(ItemDebugInfo info, List<ValueComparison> comparisons,
                                           List<String> history, List<String> nbtLines) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== DevMod Debug Info ===\n");
        sb.append("Registry: ").append(info.registryName()).append("\n");
        sb.append("Stack size: ").append(info.stackSize()).append("\n");
        sb.append("Damage: ").append(info.currentDamage()).append("/").append(info.maxDamage()).append("\n");
        sb.append("NBT tags: ").append(info.nbtTagCount()).append("\n");
        sb.append("Custom data: ").append(info.hasCustomData() ? "yes" : "no").append("\n\n");

        sb.append("--- Values ---\n");
        for (ValueComparison comp : comparisons) {
            String attr = comp.attributeName() == null ? "<attr>" : comp.attributeName();
            String suffix = comp.hasMismatch() ? " [MISMATCH]" : comp.isModified() ? " [MOD]" :
                Double.isNaN(comp.serverValue()) ? " [SERVER N/A]" : "";
            sb.append(String.format(Locale.US, "%s: orig %s srv %s cur %s%s\n",
                attr,
                formatValue(comp.originalValue()),
                formatValue(comp.serverValue()),
                formatValue(comp.currentValue()),
                suffix));
        }
        if (comparisons.stream().noneMatch(c -> !Double.isNaN(c.serverValue()))) {
            sb.append("NOTE: Server/config baseline NOT AVAILABLE (showing item/custom data only)\n");
        }

        sb.append("\n--- History ---\n");
        if (history.isEmpty()) {
            sb.append("(none)\n");
        } else {
            for (String entry : history) {
                sb.append(entry).append("\n");
            }
        }

        sb.append("\n--- NBT ---\n");
        if (nbtLines.isEmpty()) {
            sb.append("(empty)\n");
        } else {
            for (String line : nbtLines) {
                sb.append(line).append("\n");
            }
        }

        return sb.toString();
    }

    private String formatValue(double value) {
        return Double.isNaN(value) ? "n/a" : String.format(Locale.US, "%.2f", value);
    }

    // ═══════════════════════════════════════════════════════════════
    // DPS PREVIEW SECTION
    // ═══════════════════════════════════════════════════════════════

    private List<EditorSection> withDps(List<EditorSection> sections) {
        List<EditorSection> result = new ArrayList<>(sections);
        result.add(new DpsPreviewSection());
        return result;
    }

    class DpsPreviewSection implements EditorSection.CustomSection {
        private static final int HEIGHT_EXTRA = 14;
        private static final int TEXT_OFFSET_Y = UIConstants.Spacing.SM;

        @Override
        public String getId() { return "dpsPreview"; }

        @Override
        public String getLabel() { return "DPS Preview"; }

        @Override
        public int getHeight() { return UIConstants.Spacing.LG + HEIGHT_EXTRA; }

        @Override
        public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
            var font = Objects.requireNonNull(Minecraft.getInstance().font, "font cannot be null");
            float dps = core.computeDps(module.getItem());
            String dpsText = String.format("DPS: %.1f", dps);
            int textWidth = font.width(Objects.requireNonNull(dpsText));
            int x = bounds.x() + (bounds.width() - textWidth) / 2;
            int y = bounds.y() + TEXT_OFFSET_Y;
            graphics.drawString(font, dpsText, x, y, UIConstants.Text.VALUE(), false);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // TOOL RULE SECTION (Inner Class)
    // ═══════════════════════════════════════════════════════════════

    class ToolRuleSection implements EditorSection.CustomSection {
        private final String id;
        private final int displayIndex;
        private final EditorTextField tagField;
        private final EditorSlider speedSlider;
        private final EditorToggle dropsToggle;
        private WeaponStats.ToolRuleData data;

        ToolRuleSection(String id, int displayIndex, WeaponStats.ToolRuleData initial) {
            this.id = id;
            this.displayIndex = displayIndex;
            this.data = initial == null ? new WeaponStats.ToolRuleData() : initial.copy();
            tagField = new EditorTextField(id + "Tag", "Rule " + displayIndex + " - Block Tag")
                .placeholder("minecraft:mineable/pickaxe")
                .maxLength(64)
                .onChange(val -> {
                    data.blockTag = val == null ? "" : val.trim();
                    sync();
                    module.markDirty("Tool rule tag");
                });
            speedSlider = new EditorSlider(id + "Speed", "Mining Speed Multiplier", 0f, 64f, data.speed)
                .step(0.1f)
                .format("%.1f")
                .trackColor(UIConstants.SliderColors.SPECIAL)
                .onChange(v -> {
                    data.speed = v;
                    sync();
                    module.markDirty("Tool rule speed");
                });
            dropsToggle = new EditorToggle(id + "Drops", "Correct For Drops", data.correctForDrops == null ? true : data.correctForDrops)
                .tooltip("If true, this tool counts as correct for drops")
                .onChange(v -> {
                    data.correctForDrops = v;
                    sync();
                    module.markDirty("Tool rule drops");
                });
        }

        void setData(WeaponStats.ToolRuleData newData) {
            data = newData == null ? new WeaponStats.ToolRuleData() : newData.copy();
            tagField.setValue(data.blockTag == null ? "" : data.blockTag);
            speedSlider.setValue(data.speed);
            dropsToggle.setValue(data.correctForDrops == null ? true : data.correctForDrops);
        }

        private void sync() {
            WeaponStats stats = core.getStats();
            int idx = displayIndex - 1;
            while (stats.toolRules.size() <= idx) {
                stats.toolRules.add(new WeaponStats.ToolRuleData());
            }
            stats.toolRules.set(idx, data);
            stats.toolRules.removeIf(rule -> rule == null || rule.isEmpty());
        }

        @Override
        public String getId() { return id; }

        @Override
        public String getLabel() { return "Tool Rule " + displayIndex; }

        @Override
        public int getHeight() {
            int height = tagField.calculateHeight();
            height += UIConstants.Spacing.SM;
            height += speedSlider.calculateHeight();
            height += UIConstants.Spacing.SM;
            height += EditorDimensions.TOGGLE_HEIGHT;
            return height;
        }

        @Override
        public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
            int x = bounds.x();
            int y = bounds.y();
            int width = bounds.width();
            y += tagField.render(graphics, x, y, width, mouseX, mouseY);
            y += UIConstants.Spacing.SM;
            speedSlider.render(graphics, x, y, width, mouseX, mouseY);
            y += speedSlider.calculateHeight() + UIConstants.Spacing.SM;
            dropsToggle.render(graphics, x, y, width, mouseX, mouseY);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return tagField.mouseClicked(mouseX, mouseY, button)
                || speedSlider.mouseClicked(mouseX, mouseY, button)
                || dropsToggle.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            return speedSlider.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            return speedSlider.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return tagField.keyPressed(keyCode, scanCode, modifiers)
                || speedSlider.keyPressed(keyCode, scanCode, modifiers)
                || dropsToggle.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            return tagField.charTyped(chr, modifiers);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // UPDATE SLIDERS FROM STATS
    // ═══════════════════════════════════════════════════════════════

    public void updateSlidersFromStats() {
        WeaponStats stats = core.getStats();

        if (headMultSlider != null) headMultSlider.setValue(stats.headMult);
        if (bodyMultSlider != null) bodyMultSlider.setValue(stats.bodyMult);
        if (armsMultSlider != null) armsMultSlider.setValue(stats.armsMult);
        if (legsMultSlider != null) legsMultSlider.setValue(stats.legsMult);

        if (attackDamageSlider != null) attackDamageSlider.setValue(stats.attackDamage);
        if (attackSpeedSlider != null) attackSpeedSlider.setValue(stats.attackSpeed);
        if (attackReachSlider != null) attackReachSlider.setValue(stats.attackReach);
        if (attackKnockbackSlider != null) attackKnockbackSlider.setValue(stats.attackKnockback);
        if (armorPenetrationSlider != null) armorPenetrationSlider.setValue(stats.armorPenetration * 100);
        if (baseDamageBonusSlider != null) baseDamageBonusSlider.setValue(stats.baseDamageBonus);
        if (damageBonusSlider != null) damageBonusSlider.setValue(stats.damageBonus * 100);
        if (sweepingRatioSlider != null) sweepingRatioSlider.setValue(stats.sweepingRatio * 100);

        if (critChanceSlider != null) critChanceSlider.setValue(stats.critChance * 100);
        if (critDamageSlider != null) critDamageSlider.setValue(stats.critDamage);
        if (lifestealSlider != null) lifestealSlider.setValue(stats.lifesteal * 100);
        if (fireDamageSlider != null) fireDamageSlider.setValue(stats.fireDamageBonus);
        if (magicDamageSlider != null) magicDamageSlider.setValue(stats.magicDamageBonus);
        if (armorShredSlider != null) armorShredSlider.setValue(stats.armorShred);
        if (trueDamageSlider != null) trueDamageSlider.setValue(stats.trueDamagePercent * 100);
        if (vsUndeadSlider != null) vsUndeadSlider.setValue(stats.damageVsUndead * 100);
        if (vsArthroSlider != null) vsArthroSlider.setValue(stats.damageVsArthropods * 100);
        if (vsPlayersSlider != null) vsPlayersSlider.setValue(stats.damageVsPlayers * 100);

        if (maxDurabilitySlider != null) maxDurabilitySlider.setValue(stats.maxDurability);
        if (currentDamageSlider != null) currentDamageSlider.setValue(stats.currentDamage);
        if (repairCostSlider != null) repairCostSlider.setValue(stats.repairCost);
        if (unbreakableToggle != null) unbreakableToggle.setValue(stats.unbreakable);
        if (clearToolRulesToggle != null) clearToolRulesToggle.setValue(stats.clearToolRules);
        if (toolDefaultSpeedSlider != null) toolDefaultSpeedSlider.setValue(stats.toolDefaultMiningSpeed);
        if (toolDamagePerBlockSlider != null) toolDamagePerBlockSlider.setValue(stats.toolDamagePerBlock);

        for (int i = 0; i < toolRuleSections.size(); i++) {
            WeaponStats.ToolRuleData data = (stats.toolRules.size() > i)
                ? stats.toolRules.get(i)
                : new WeaponStats.ToolRuleData();
            toolRuleSections.get(i).setData(data);
        }

        applySourceLabels();
    }

    private void applySourceLabels() {
        String prefix = core.getSourcePrefix();
        applySourceLabel(attackDamageSlider, prefix);
        applySourceLabel(attackSpeedSlider, prefix);
        applySourceLabel(attackReachSlider, prefix);
        applySourceLabel(attackKnockbackSlider, prefix);
        applySourceLabel(damageBonusSlider, prefix);
        applySourceLabel(sweepingRatioSlider, prefix);
        applySourceLabel(armorPenetrationSlider, prefix);
        applySourceLabel(baseDamageBonusSlider, prefix);
        applySourceLabel(armorShredSlider, prefix);
        applySourceLabel(critChanceSlider, prefix);
        applySourceLabel(critDamageSlider, prefix);
        applySourceLabel(lifestealSlider, prefix);
        applySourceLabel(fireDamageSlider, prefix);
        applySourceLabel(magicDamageSlider, prefix);
        applySourceLabel(trueDamageSlider, prefix);
        applySourceLabel(vsUndeadSlider, prefix);
        applySourceLabel(vsArthroSlider, prefix);
        applySourceLabel(vsPlayersSlider, prefix);
    }

    private void applySourceLabel(EditorSlider slider, String prefix) {
        if (slider == null) return;
        String label = slider.getLabel();
        slider.setLabel(prefix + label.replaceFirst("^\\[[^]]+\\] ", ""));
    }
}
