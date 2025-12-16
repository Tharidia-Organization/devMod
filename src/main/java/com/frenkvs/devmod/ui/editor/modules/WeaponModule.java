package com.frenkvs.devmod.ui.editor.modules;

import com.frenkvs.devmod.WeaponStats;
import com.frenkvs.devmod.WeaponConfigManager;
import com.frenkvs.devmod.DevMod;
import com.frenkvs.devmod.ui.editor.AbstractEditorModule;
import com.frenkvs.devmod.ui.editor.EditorSection;
import com.frenkvs.devmod.ui.editor.ModuleTab;
import com.frenkvs.devmod.ui.editor.components.EditorSlider;
import com.frenkvs.devmod.ui.editor.components.EditorTextField;
import com.frenkvs.devmod.ui.editor.components.EditorToggle;
import com.frenkvs.devmod.ui.editor.core.EditorCache;
import com.frenkvs.devmod.ui.editor.core.EditorDimensions;
import com.frenkvs.devmod.ui.editor.core.ResponsiveLayout;
import com.frenkvs.devmod.ui.editor.core.UIConstants;
import com.frenkvs.devmod.ui.editor.debug.DebugInfoSection;
import com.frenkvs.devmod.ui.editor.debug.ItemDebugInfo;
import com.frenkvs.devmod.ui.editor.debug.ValueComparison;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Editor module for weapon statistics.
 * Allows editing damage multipliers, attack stats, and special effects.
 *
 * @see EDITOR_DESIGN_SYSTEM.md Section 3.3
 */
public class WeaponModule extends AbstractEditorModule {

    // ═══════════════════════════════════════════════════════════════
    // WEAPON STATS
    // ═══════════════════════════════════════════════════════════════

    private static final String NBT_KEY = "WeaponModStats";
    private static final double EPSILON = 1e-4;
    private WeaponStats stats = new WeaponStats();
    private WeaponStats originalStats = new WeaponStats();
    private static final String DELTA_KEY = "delta";
    public enum WeaponVariant { STANDARD, MACE, TRIDENT }
    private WeaponVariant variant = WeaponVariant.STANDARD;
    private String sourcePrefix = "";
    // Variant-specific state (placeholder persisted in CustomData extras)
    private float smashBonus = 3.0f;
    private float smashCap = 150.0f;
    private float smashKnockback = 1.0f;
    private float smashAoeDamage = 50.0f; // percent
    private boolean smashNegateFall = false;
    private float throwDamage = 8.0f;
    private float throwSpeed = 2.5f;
    private float loyaltySpeed = 1.5f;
    private static final @Nonnull net.minecraft.world.item.component.ItemAttributeModifiers NONNULL_ATTR_EMPTY =
        Objects.requireNonNull(net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY,
            "ItemAttributeModifiers.EMPTY cannot be null");
    private float riptideDistance = 12.0f;
    private float riptideDamage = 6.0f;
    private boolean riptideRequiresWater = true;
    // Variant baselines for dirty comparison/reset
    private float originalSmashBonus = smashBonus;
    private float originalSmashCap = smashCap;
    private float originalSmashKnockback = smashKnockback;
    private float originalSmashAoeDamage = smashAoeDamage;
    private boolean originalSmashNegateFall = smashNegateFall;
    private float originalThrowDamage = throwDamage;
    private float originalThrowSpeed = throwSpeed;
    private float originalLoyaltySpeed = loyaltySpeed;
    private float originalRiptideDistance = riptideDistance;
    private float originalRiptideDamage = riptideDamage;
    private boolean originalRiptideRequiresWater = riptideRequiresWater;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Hit Location Tab
    // ═══════════════════════════════════════════════════════════════

    private EditorSlider headMultSlider;
    private EditorSlider bodyMultSlider;
    private EditorSlider armsMultSlider;
    private EditorSlider legsMultSlider;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Combat Tab
    // ═══════════════════════════════════════════════════════════════

    private EditorSlider attackDamageSlider;
    private EditorSlider attackSpeedSlider;
    private EditorSlider attackReachSlider;
    private EditorSlider attackKnockbackSlider;
    private EditorSlider armorPenetrationSlider;
    private EditorSlider baseDamageBonusSlider;
    private EditorSlider damageBonusSlider;
    // Variant-specific sliders
    private EditorSlider smashBonusSlider;
    private EditorSlider smashCapSlider;
    private EditorSlider smashKnockbackSlider;
    private EditorSlider smashAoeDamageSlider;
    private EditorToggle smashFallNegationToggle;
    private EditorSlider throwDamageSlider;
    private EditorSlider throwSpeedSlider;
    private EditorSlider loyaltySpeedSlider;
    private EditorSlider riptideDistanceSlider;
    private EditorSlider riptideDamageSlider;
    private EditorToggle riptideRequiresWaterToggle;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Special Tab
    // ═══════════════════════════════════════════════════════════════

    private EditorToggle critEnabledToggle;
    private EditorSlider critChanceSlider;
    private EditorSlider critDamageSlider;
    private EditorToggle lifestealEnabledToggle;
    private EditorSlider lifestealSlider;
    private EditorToggle fireDamageEnabledToggle;
    private EditorSlider fireDamageSlider;
    private EditorToggle magicDamageEnabledToggle;
    private EditorSlider magicDamageSlider;
    private EditorSlider armorShredSlider;
    private EditorSlider trueDamageSlider;
    private EditorSlider vsUndeadSlider;
    private EditorSlider vsArthroSlider;
    private EditorSlider vsPlayersSlider;

    // Durability
    private EditorSlider maxDurabilitySlider;
    private EditorSlider currentDamageSlider;
    private EditorSlider repairCostSlider;
    private EditorToggle unbreakableToggle;
    private EditorToggle clearToolRulesToggle;
    private EditorSlider toolDefaultSpeedSlider;
    private EditorSlider toolDamagePerBlockSlider;
    private final List<ToolRuleSection> toolRuleSections = new ArrayList<>();

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public WeaponModule() {
        super("weapon", "Weapon Editor");
    }

    public WeaponModule(WeaponVariant variant) {
        super("weapon", "Weapon Editor");
        this.variant = variant == null ? WeaponVariant.STANDARD : variant;
    }

    // ═══════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void onItemSet() {
        // Load existing weapon stats from item NBT
        loadStatsFromItem();
        originalStats = stats.copy();

        // Update all sliders to reflect current stats
        updateSlidersFromStats();
    }

    private void loadStatsFromItem() {
        // Prefer typed data component, then legacy CustomData
        CompoundTag statsTag = null;
        try {
            statsTag = item.get(Objects.requireNonNull(com.frenkvs.devmod.WeaponComponents.WEAPON_STATS.get()));
        } catch (Exception ignored) { }

        CompoundTag customTag;
        try {
            customTag = item.getOrDefault(
                Objects.requireNonNull(net.minecraft.core.component.DataComponents.CUSTOM_DATA),
                Objects.requireNonNull(net.minecraft.world.item.component.CustomData.EMPTY)
            ).copyTag();
        } catch (Exception ignored) {
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
            } catch (Exception ignored) {
                statsTag = new CompoundTag();
            }
        }

        if (statsTag != null && !statsTag.isEmpty()) {
            sourcePrefix = "[DEV] ";
            DevMod.LOGGER.info("[Editor][Weapon] Loaded stats from component tag (size={})", statsTag.size());
            stats = statsTag.contains(NBT_KEY) ? WeaponStats.load(statsTag.getCompound(NBT_KEY)) : WeaponStats.load(statsTag);
        } else {
            sourcePrefix = (customTag != null && !customTag.isEmpty()) ? "[NBT] " : "[VANILLA] ";
            DevMod.LOGGER.info("[Editor][Weapon] No custom stats found; applying vanilla defaults. customTagEmpty={} stackAttrMods={}",
                customTag == null || customTag.isEmpty(),
                item.getAttributeModifiers().modifiers().size());
            stats = new WeaponStats();
            applyVanillaDefaults(stats);
        }

        // Variant data always read from custom data for now
        loadVariantData(customTag, statsTag);

        // Prefill tool rules from existing component so editor mirrors stack state
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
        } catch (Exception ignored) {
            // best effort; if we cannot read tool component we keep defaults
        }
        snapshotVariantOriginals();
    }

    /**
     * Populate stats from vanilla attribute modifiers and durability when no custom data exists.
     */
    private void applyVanillaDefaults(WeaponStats target) {
        if (target == null) return;
        try {
            net.minecraft.world.item.component.ItemAttributeModifiers mods = mergeAttributeSources();
            double dmg = 0;
            double spd = 0;
            double kb = 0;
            double critCh = 0;
            double critDmg = 0;
            double shred = 0;
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
                boolean isCritCh = attrKey == com.frenkvs.devmod.ModAttributes.CRIT_CHANCE.getKey() ||
                    attr == com.frenkvs.devmod.ModAttributes.CRIT_CHANCE.get();
                boolean isCritDmg = attrKey == com.frenkvs.devmod.ModAttributes.CRIT_MULTIPLIER.getKey() ||
                    attr == com.frenkvs.devmod.ModAttributes.CRIT_MULTIPLIER.get();
                boolean isShred = attrKey == com.frenkvs.devmod.ModAttributes.ARMOR_SHRED.getKey() ||
                    attr == com.frenkvs.devmod.ModAttributes.ARMOR_SHRED.get();

                if (isDmg) {
                    dmg += mod.amount();
                } else if (isSpd) {
                    spd += mod.amount();
                } else if (isKb) {
                    kb += mod.amount();
                } else if (isCritCh) {
                    critCh += mod.amount();
                } else if (isCritDmg) {
                    critDmg += mod.amount();
                } else if (isShred) {
                    shred += mod.amount();
                }
            }
            if (dmg != 0) target.attackDamage = (float) dmg;
            if (spd != 0) target.attackSpeed = (float) spd;
            if (kb != 0) target.attackKnockback = (float) kb;
            if (critCh != 0) target.critChance = (float) (critCh / 100f);
            if (critDmg != 0) target.critDamage = (float) critDmg;
            if (shred != 0) target.armorShred = (float) shred;
            DevMod.LOGGER.info("[Editor][Weapon] From attributes -> dmg={} spd={} kb={} critCh={} critDmg={} shred={}",
                target.attackDamage, target.attackSpeed, target.attackKnockback, target.critChance, target.critDamage, target.armorShred);
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
        } catch (Exception ignored) {
            // ignore durability if item doesn't expose it
        }
    }

    private @Nonnull net.minecraft.world.item.component.ItemAttributeModifiers mergeAttributeSources() {
        @Nonnull net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.ItemAttributeModifiers> attrType =
            Objects.requireNonNull(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                "ATTRIBUTE_MODIFIERS component type cannot be null");

        net.minecraft.world.item.component.ItemAttributeModifiers stackMods = item.getOrDefault(attrType, NONNULL_ATTR_EMPTY);

        net.minecraft.world.item.component.ItemAttributeModifiers itemMods = NONNULL_ATTR_EMPTY;
        try {
            itemMods = item.getAttributeModifiers();
        } catch (Exception ignored) {}

        net.minecraft.world.item.component.ItemAttributeModifiers defaultMods = NONNULL_ATTR_EMPTY;
        try {
            defaultMods = item.getItem().getDefaultAttributeModifiers(item);
        } catch (Exception ignored) {}

        // Prefer stack -> item -> default to avoid double-counting the same vanilla entries
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

    private void updateSlidersFromStats() {
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

    /**
     * Apply external stats (preset/import) while keeping undo/history in sync.
     */
    public void applyExternalStats(WeaponStats newStats, String reason) {
        if (newStats == null) {
            return;
        }
        saveUndoState();
        stats = newStats.copy();
        updateSlidersFromStats();
        pendingChanges.clear();
        String changeReason = (reason == null || reason.isBlank()) ? "Preset applied" : reason;
        pendingChanges.add(changeReason);
        addHistoryEntry(changeReason);
        EditorCache.getInstance().invalidateItem(item.toString());
    }

    @Override
    protected void initializeTabs() {
        tabs.clear();

        // Create UI components
        createHitLocationComponents();
        createCombatComponents();
        createSpecialComponents();
        createDamageTypeComponents();
        createDurabilityComponents();
        createToolComponents();
        createVariantComponents();

        // Add tabs
        addTab(ModuleTab.of("hitlocation", "Hit Location", this::getHitLocationSections));
        addTab(ModuleTab.of("stats", "Stats", this::getCombatSections));
        addTab(ModuleTab.of("combat", "Combat", this::getSpecialSections));
        addTab(ModuleTab.of("damage", "Damage Types", this::getDamageTypeSections));
        addTab(ModuleTab.of("durability", "Durability", this::getDurabilitySections));
        addTab(ModuleTab.of("tooling", "Tool Rules", this::getToolSections));
        if (variant == WeaponVariant.MACE) {
            addTab(ModuleTab.of("mace", "Mace", this::getMaceSections));
        }
        if (variant == WeaponVariant.TRIDENT) {
            addTab(ModuleTab.of("trident", "Trident", this::getTridentSections));
        }
        addTab(ModuleTab.of("debug", "Debug", this::getDebugSections));

        // Sync UI with stats after components are created
        updateSlidersFromStats();
    }

    private void createVariantComponents() {
        if (variant == WeaponVariant.MACE) {
            smashBonusSlider = new EditorSlider("smashBonus", "Fall Damage Bonus", 0f, 20f, 3.0f)
                .step(0.5f)
                .format("%.1f")
                .suffix(" per block")
                .trackColor(UIConstants.SliderColors.DAMAGE)
                .onChange(v -> { smashBonus = v; markDirty("Smash fall bonus"); });
            smashCapSlider = new EditorSlider("smashCap", "Max Bonus Damage", 0f, 300f, 150f)
                .step(5f)
                .format("%.0f")
                .trackColor(UIConstants.SliderColors.DAMAGE)
                .onChange(v -> { smashCap = v; markDirty("Smash cap"); });
            smashKnockbackSlider = new EditorSlider("smashKb", "Smash Knockback", 0f, 5f, 1.0f)
                .step(0.1f)
                .format("%.1f")
                .trackColor(UIConstants.SliderColors.NEUTRAL)
                .onChange(v -> { smashKnockback = v; markDirty("Smash knockback"); });
            smashAoeDamageSlider = new EditorSlider("smashAoe", "Smash AOE Damage", 0f, 200f, 50f)
                .step(5f)
                .format("%.0f")
                .suffix("%")
                .trackColor(UIConstants.SliderColors.DAMAGE)
                .onChange(v -> { smashAoeDamage = v; markDirty("Smash AOE damage"); });
            smashFallNegationToggle = new EditorToggle("smashNegate", "Fall Damage Negation", false)
                .onChange(v -> { smashNegateFall = v; markDirty("Fall damage negation"); });
        } else if (variant == WeaponVariant.TRIDENT) {
            throwDamageSlider = new EditorSlider("throwDmg", "Throw Damage", 0f, 30f, 8f)
                .step(0.5f)
                .format("%.1f")
                .trackColor(UIConstants.SliderColors.DAMAGE)
                .onChange(v -> { throwDamage = v; markDirty("Throw damage"); });
            throwSpeedSlider = new EditorSlider("throwSpeed", "Throw Speed", 0f, 5f, 2.5f)
                .step(0.1f)
                .format("%.2f")
                .suffix(" blocks/tick")
                .trackColor(UIConstants.SliderColors.SPEED)
                .onChange(v -> { throwSpeed = v; markDirty("Throw speed"); });
            loyaltySpeedSlider = new EditorSlider("loyaltySpeed", "Return Speed (Loyalty)", 0f, 5f, 1.5f)
                .step(0.1f)
                .format("%.2f")
                .trackColor(UIConstants.SliderColors.SPEED)
                .onChange(v -> { loyaltySpeed = v; markDirty("Return speed"); });
            riptideDistanceSlider = new EditorSlider("riptideDist", "Riptide Distance", 0f, 30f, 12f)
                .step(0.5f)
                .format("%.1f")
                .suffix(" blocks")
                .trackColor(UIConstants.SliderColors.SPEED)
                .onChange(v -> { riptideDistance = v; markDirty("Riptide distance"); });
            riptideDamageSlider = new EditorSlider("riptideDmg", "Riptide Damage", 0f, 20f, 6f)
                .step(0.5f)
                .format("%.1f")
                .trackColor(UIConstants.SliderColors.DAMAGE)
                .onChange(v -> { riptideDamage = v; markDirty("Riptide damage"); });
            riptideRequiresWaterToggle = new EditorToggle("riptideWater", "Requires Water", true)
                .onChange(v -> { riptideRequiresWater = v; markDirty("Riptide water requirement"); });
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // HIT LOCATION TAB
    // ═══════════════════════════════════════════════════════════════

    private void createHitLocationComponents() {
        headMultSlider = new EditorSlider("headMult", "Head Multiplier", 0.5f, 3.0f, 1.5f)
            .step(0.1f)
            .format("%.1f")
            .suffix("x")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .onChange(v -> { stats.headMult = v; markDirty("Head multiplier"); });

        bodyMultSlider = new EditorSlider("bodyMult", "Body Multiplier", 0.5f, 2.0f, 1.0f)
            .step(0.1f)
            .format("%.1f")
            .suffix("x")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .onChange(v -> { stats.bodyMult = v; markDirty("Body multiplier"); });

        armsMultSlider = new EditorSlider("armsMult", "Arms Multiplier", 0.3f, 1.5f, 0.8f)
            .step(0.1f)
            .format("%.1f")
            .suffix("x")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .onChange(v -> { stats.armsMult = v; markDirty("Arms multiplier"); });

        legsMultSlider = new EditorSlider("legsMult", "Legs Multiplier", 0.3f, 1.5f, 0.7f)
            .step(0.1f)
            .format("%.1f")
            .suffix("x")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .onChange(v -> { stats.legsMult = v; markDirty("Legs multiplier"); });
    }

    private List<EditorSection> getHitLocationSections() {
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

    private void createCombatComponents() {
        attackDamageSlider = new EditorSlider("atkDmg", "Attack Damage", 0f, 50f, 0f)
            .step(0.5f)
            .format("%.1f")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .onChange(v -> { stats.attackDamage = v; markDirty("Attack damage"); });

        // Allow vanilla negative speeds (e.g., -2.4) by setting a negative minimum.
        attackSpeedSlider = new EditorSlider("atkSpd", "Attack Speed", -5f, 4f, 0f)
            .step(0.1f)
            .format("%.1f")
            .trackColor(UIConstants.SliderColors.SPEED)
            .showInput(true)
            .onChange(v -> { stats.attackSpeed = v; markDirty("Attack speed"); });

        attackReachSlider = new EditorSlider("atkRch", "Attack Reach", 0f, 6f, 0f)
            .step(0.1f)
            .format("%.1f")
            .suffix(" blocks")
            .trackColor(UIConstants.SliderColors.NEUTRAL)
            .showInput(true)
            .onChange(v -> { stats.attackReach = v; markDirty("Attack reach"); });

        attackKnockbackSlider = new EditorSlider("atkKB", "Knockback", 0f, 5f, 0f)
            .step(0.1f)
            .format("%.1f")
            .trackColor(UIConstants.SliderColors.NEUTRAL)
            .showInput(true)
            .onChange(v -> { stats.attackKnockback = v; markDirty("Knockback"); });

        damageBonusSlider = new EditorSlider("damageBonus", "Damage Bonus", 0f, 100f, 0f)
            .step(1f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.NEUTRAL)
            .showInput(true)
            .onChange(v -> { stats.damageBonus = v / 100f; markDirty("Damage bonus"); });

        armorPenetrationSlider = new EditorSlider("armorPen", "Armor Penetration", 0f, 100f, 0f)
            .step(1f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.SPECIAL)
            .showInput(true)
            .onChange(v -> { stats.armorPenetration = v / 100f; markDirty("Armor penetration"); });

        baseDamageBonusSlider = new EditorSlider("baseDmg", "Base Damage Bonus", -50f, 50f, 0f)
            .step(0.5f)
            .format("+%.1f")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .onChange(v -> { stats.baseDamageBonus = v; markDirty("Base damage bonus"); });

        armorShredSlider = new EditorSlider("armorShred", "Armor Shred", 0f, 66f, 0f)
            .step(1f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .onChange(v -> { stats.armorShred = v; markDirty("Armor shred"); });
    }

    private List<EditorSection> getCombatSections() {
        return withDps(List.of(
            new SliderSectionAdapter(attackDamageSlider),
            new SliderSectionAdapter(attackSpeedSlider),
            new SliderSectionAdapter(attackReachSlider),
            new SliderSectionAdapter(attackKnockbackSlider),
            new SliderSectionAdapter(damageBonusSlider),
            new SliderSectionAdapter(armorPenetrationSlider),
            new SliderSectionAdapter(baseDamageBonusSlider),
            new SliderSectionAdapter(armorShredSlider)
        ));
    }

    private List<EditorSection> getMaceSections() {
        return withDps(List.of(
            new EditorSection.HeaderSection() {
                private boolean collapsed;
                @Override public String getId() { return "maceHeader"; }
                @Override public String getLabel() { return "Smash Attack"; }
                @Override public int getHeight() { return EditorDimensions.SECTION_HEADER_HEIGHT; }
                @Override public void render(GuiGraphics g, ResponsiveLayout.Rect b, int mx, int my) {
                    g.drawString(Objects.requireNonNull(Minecraft.getInstance().font), getLabel(), b.x() + UIConstants.Spacing.SM, b.y() + 4, UIConstants.Text.TITLE, false);
                }
                @Override public boolean isCollapsible() { return false; }
                @Override public boolean isCollapsed() { return collapsed; }
                @Override public void setCollapsed(boolean collapsed) { this.collapsed = collapsed; }
            },
            new SliderSectionAdapter(smashBonusSlider),
            new SliderSectionAdapter(smashCapSlider),
            new SliderSectionAdapter(smashKnockbackSlider),
            new SliderSectionAdapter(smashAoeDamageSlider),
            new ToggleSectionAdapter(smashFallNegationToggle)
        ));
    }

    private List<EditorSection> getTridentSections() {
        return withDps(List.of(
            new EditorSection.HeaderSection() {
                private boolean collapsed;
                @Override public String getId() { return "tridentHeader"; }
                @Override public String getLabel() { return "Throw / Riptide"; }
                @Override public int getHeight() { return EditorDimensions.SECTION_HEADER_HEIGHT; }
                @Override public void render(GuiGraphics g, ResponsiveLayout.Rect b, int mx, int my) {
                    g.drawString(Objects.requireNonNull(Minecraft.getInstance().font), getLabel(), b.x() + UIConstants.Spacing.SM, b.y() + 4, UIConstants.Text.TITLE, false);
                }
                @Override public boolean isCollapsible() { return false; }
                @Override public boolean isCollapsed() { return collapsed; }
                @Override public void setCollapsed(boolean collapsed) { this.collapsed = collapsed; }
            },
            new SliderSectionAdapter(throwDamageSlider),
            new SliderSectionAdapter(throwSpeedSlider),
            new SliderSectionAdapter(loyaltySpeedSlider),
            new SliderSectionAdapter(riptideDistanceSlider),
            new SliderSectionAdapter(riptideDamageSlider),
            new ToggleSectionAdapter(riptideRequiresWaterToggle)
        ));
    }

    // ═══════════════════════════════════════════════════════════════
    // SPECIAL TAB
    // ═══════════════════════════════════════════════════════════════

    private void createSpecialComponents() {
        // Critical Hit Toggle & Sliders
        critEnabledToggle = new EditorToggle("critEnabled", "Enable Critical Hits", stats.critChance > 0)
            .tooltip("Enable critical hit mechanics for this weapon")
            .onChange(enabled -> {
                critChanceSlider.setEnabled(enabled);
                critDamageSlider.setEnabled(enabled);
                if (!enabled) {
                    stats.critChance = 0f;
                    critChanceSlider.setValue(0f);
                }
                markDirty("Critical hits " + (enabled ? "enabled" : "disabled"));
            });

        critChanceSlider = new EditorSlider("critCh", "Critical Chance", 0f, 100f, 0f)
            .step(1f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.PERCENT)
            .enabled(stats.critChance > 0)
            .onChange(v -> { stats.critChance = v / 100f; markDirty("Critical chance"); });

        critDamageSlider = new EditorSlider("critDmg", "Critical Damage", 1f, 5f, 1.5f)
            .step(0.1f)
            .format("%.1f")
            .suffix("x")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .enabled(stats.critChance > 0)
            .onChange(v -> { stats.critDamage = v; markDirty("Critical damage"); });

        // Lifesteal Toggle & Slider
        lifestealEnabledToggle = new EditorToggle("lifestealEnabled", "Enable Lifesteal", stats.lifesteal > 0)
            .tooltip("Heal a percentage of damage dealt")
            .onChange(enabled -> {
                lifestealSlider.setEnabled(enabled);
                if (!enabled) {
                    stats.lifesteal = 0f;
                    lifestealSlider.setValue(0f);
                }
                markDirty("Lifesteal " + (enabled ? "enabled" : "disabled"));
            });

        lifestealSlider = new EditorSlider("lifesteal", "Lifesteal", 0f, 50f, 0f)
            .step(1f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.SPECIAL)
            .enabled(stats.lifesteal > 0)
            .onChange(v -> { stats.lifesteal = v / 100f; markDirty("Lifesteal"); });

        // Fire Damage Toggle & Slider
        fireDamageEnabledToggle = new EditorToggle("fireEnabled", "Enable Fire Damage", stats.fireDamageBonus > 0)
            .tooltip("Add fire damage to attacks")
            .onChange(enabled -> {
                fireDamageSlider.setEnabled(enabled);
                if (!enabled) {
                    stats.fireDamageBonus = 0f;
                    fireDamageSlider.setValue(0f);
                }
                markDirty("Fire damage " + (enabled ? "enabled" : "disabled"));
            });

        fireDamageSlider = new EditorSlider("fireDmg", "Fire Damage", 0f, 20f, 0f)
            .step(0.5f)
            .format("+%.1f")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .enabled(stats.fireDamageBonus > 0)
            .onChange(v -> { stats.fireDamageBonus = v; markDirty("Fire damage"); });

        // Magic Damage Toggle & Slider
        magicDamageEnabledToggle = new EditorToggle("magicEnabled", "Enable Magic Damage", stats.magicDamageBonus > 0)
            .tooltip("Add magic damage to attacks")
            .onChange(enabled -> {
                magicDamageSlider.setEnabled(enabled);
                if (!enabled) {
                    stats.magicDamageBonus = 0f;
                    magicDamageSlider.setValue(0f);
                }
                markDirty("Magic damage " + (enabled ? "enabled" : "disabled"));
            });

        magicDamageSlider = new EditorSlider("magicDmg", "Magic Damage", 0f, 20f, 0f)
            .step(0.5f)
            .format("+%.1f")
            .trackColor(UIConstants.SliderColors.SPECIAL)
            .enabled(stats.magicDamageBonus > 0)
            .onChange(v -> { stats.magicDamageBonus = v; markDirty("Magic damage"); });
    }

    private List<EditorSection> getSpecialSections() {
        return withDps(List.of(
            new ToggleSectionAdapter(critEnabledToggle),
            new SliderSectionAdapter(critChanceSlider),
            new SliderSectionAdapter(critDamageSlider),
            new ToggleSectionAdapter(lifestealEnabledToggle),
            new SliderSectionAdapter(lifestealSlider)
        ));
    }

    private void createDamageTypeComponents() {
        vsUndeadSlider = new EditorSlider("vsUndead", "Damage vs Undead", 0f, 200f, 0f)
            .step(1f)
            .format("+%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .onChange(v -> { stats.damageVsUndead = v / 100f; markDirty("Damage vs undead"); });

        vsArthroSlider = new EditorSlider("vsArthro", "Damage vs Arthropods", 0f, 200f, 0f)
            .step(1f)
            .format("+%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .onChange(v -> { stats.damageVsArthropods = v / 100f; markDirty("Damage vs arthropods"); });

        vsPlayersSlider = new EditorSlider("vsPlayers", "Damage vs Players", 0f, 200f, 0f)
            .step(1f)
            .format("+%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .onChange(v -> { stats.damageVsPlayers = v / 100f; markDirty("Damage vs players"); });

        trueDamageSlider = new EditorSlider("trueDmg", "True Damage Portion", 0f, 100f, 0f)
            .step(1f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.SPECIAL)
            .onChange(v -> { stats.trueDamagePercent = v / 100f; markDirty("True damage"); });
    }

    private List<EditorSection> getDamageTypeSections() {
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

    private void createDurabilityComponents() {
        maxDurabilitySlider = new EditorSlider("maxDur", "Max Durability", 0f, 4096f, 0f)
            .step(16f)
            .format("%.0f")
            .trackColor(UIConstants.SliderColors.DURABILITY)
            .onChange(v -> { stats.maxDurability = Math.round(v); markDirty("Max durability"); });
        currentDamageSlider = new EditorSlider("curDmg", "Current Damage", 0f, 4096f, 0f)
            .step(1f)
            .format("%.0f")
            .trackColor(UIConstants.SliderColors.DURABILITY)
            .onChange(v -> { stats.currentDamage = Math.round(v); markDirty("Current damage"); });
        repairCostSlider = new EditorSlider("repair", "Repair Cost", 0f, 100f, 0f)
            .step(1f)
            .format("%.0f")
            .trackColor(UIConstants.SliderColors.DURABILITY)
            .onChange(v -> { stats.repairCost = Math.round(v); markDirty("Repair cost"); });
        unbreakableToggle = new EditorToggle("unbreakable", "Unbreakable", stats.unbreakable)
            .onChange(v -> { stats.unbreakable = v; markDirty("Unbreakable"); });
        clearToolRulesToggle = new EditorToggle("clearTools", "Clear Tool Rules (tags/speeds)", stats.clearToolRules)
            .tooltip("Removes tool rules component; useful to bypass mining penalties (apply to persist)")
            .onChange(v -> { stats.clearToolRules = v; markDirty("Clear tool rules"); });
    }

    private List<EditorSection> getDurabilitySections() {
        return List.of(
            new SliderSectionAdapter(maxDurabilitySlider),
            new SliderSectionAdapter(currentDamageSlider),
            new SliderSectionAdapter(repairCostSlider),
            new ToggleSectionAdapter(unbreakableToggle)
        );
    }

    private void createToolComponents() {
        toolRuleSections.clear();
        toolDefaultSpeedSlider = new EditorSlider("toolDefaultSpeed", "Default Mining Speed", 0f, 64f, stats.toolDefaultMiningSpeed)
            .step(0.1f)
            .format("%.1f")
            .trackColor(UIConstants.SliderColors.SPECIAL)
            .onChange(v -> { stats.toolDefaultMiningSpeed = v; markDirty("Tool default speed"); });
        toolDamagePerBlockSlider = new EditorSlider("toolDamagePerBlock", "Damage Per Block", 0f, 64f, stats.toolDamagePerBlock)
            .step(1f)
            .format("%.0f")
            .trackColor(UIConstants.SliderColors.DURABILITY)
            .onChange(v -> { stats.toolDamagePerBlock = Math.round(v); markDirty("Tool damage per block"); });

        int maxRules = 3;
        for (int i = 0; i < maxRules; i++) {
            WeaponStats.ToolRuleData data = (stats.toolRules.size() > i)
                ? stats.toolRules.get(i)
                : new WeaponStats.ToolRuleData();
            ToolRuleSection row = new ToolRuleSection("toolRule" + i, i + 1, data);
            toolRuleSections.add(row);
        }
    }

    private List<EditorSection> getToolSections() {
        List<EditorSection> sections = new ArrayList<>();
        sections.add(new SliderSectionAdapter(toolDefaultSpeedSlider));
        sections.add(new SliderSectionAdapter(toolDamagePerBlockSlider));
        toolRuleSections.forEach(sections::add);
        sections.add(new ToggleSectionAdapter(clearToolRulesToggle));
        sections.add(new TextNoteSection("toolRulesNote", "Block tags (e.g. minecraft:mineable/pickaxe) with speed + drops"));
        return sections;
    }

    private void ensureToolRuleSlot(int index) {
        while (stats.toolRules.size() <= index) {
            stats.toolRules.add(new WeaponStats.ToolRuleData());
        }
    }

    private void trimEmptyToolRules() {
        stats.toolRules.removeIf(rule -> rule == null || rule.isEmpty());
    }

    private class ToolRuleSection implements EditorSection.CustomSection {
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
                    markDirty("Tool rule tag");
                });
            speedSlider = new EditorSlider(id + "Speed", "Mining Speed Multiplier", 0f, 64f, data.speed)
                .step(0.1f)
                .format("%.1f")
                .trackColor(UIConstants.SliderColors.SPECIAL)
                .onChange(v -> {
                    data.speed = v;
                    sync();
                    markDirty("Tool rule speed");
                });
            dropsToggle = new EditorToggle(id + "Drops", "Correct For Drops", data.correctForDrops == null ? true : data.correctForDrops)
                .tooltip("If true, this tool counts as correct for drops")
                .onChange(v -> {
                    data.correctForDrops = v;
                    sync();
                    markDirty("Tool rule drops");
                });
        }

        void setData(WeaponStats.ToolRuleData newData) {
            data = newData == null ? new WeaponStats.ToolRuleData() : newData.copy();
            tagField.setValue(data.blockTag == null ? "" : data.blockTag);
            speedSlider.setValue(data.speed);
            dropsToggle.setValue(data.correctForDrops == null ? true : data.correctForDrops);
        }

        private void sync() {
            int idx = displayIndex - 1;
            ensureToolRuleSlot(idx);
            stats.toolRules.set(idx, data);
            trimEmptyToolRules();
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getLabel() {
            return "Tool Rule " + displayIndex;
        }

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

    private static class TextNoteSection implements EditorSection.CustomSection {
        private final String id;
        private final String text;

        TextNoteSection(String id, String text) {
            this.id = id;
            this.text = text;
        }

        @Override public String getId() { return id; }
        @Override public String getLabel() { return ""; }
        @Override public int getHeight() { return 24; }
        @Override
        public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
            graphics.drawString(Objects.requireNonNull(Minecraft.getInstance().font), text,
                bounds.x() + UIConstants.Spacing.SM, bounds.y() + 6, UIConstants.Text.SECONDARY, false);
        }
    }

    private CompoundTag getCustomDataTag() {
        Objects.requireNonNull(item, "item cannot be null");
        var customData = item.getOrDefault(
            Objects.requireNonNull(DataComponents.CUSTOM_DATA, "DataComponents.CUSTOM_DATA cannot be null"),
            Objects.requireNonNull(CustomData.EMPTY, "CustomData.EMPTY cannot be null")
        );
        CompoundTag tag = customData.copyTag();
        return tag == null ? new CompoundTag() : tag;
    }

    private void loadVariantData(CompoundTag customRoot, CompoundTag componentRoot) {
        CompoundTag source = customRoot == null ? new CompoundTag() : customRoot;
        if (source.isEmpty() && componentRoot != null) {
            source = componentRoot;
        }
        if (source.contains("Mace")) {
            CompoundTag mace = source.getCompound("Mace");
            smashBonus = mace.getFloat("smashBonus");
            smashCap = mace.getFloat("smashCap");
            smashKnockback = mace.getFloat("smashKnockback");
            smashAoeDamage = mace.getFloat("smashAoeDamage");
            smashNegateFall = mace.getBoolean("smashNegateFall");
        }
        if (source.contains("Trident")) {
            CompoundTag tri = source.getCompound("Trident");
            throwDamage = tri.getFloat("throwDamage");
            throwSpeed = tri.getFloat("throwSpeed");
            loyaltySpeed = tri.getFloat("loyaltySpeed");
            riptideDistance = tri.getFloat("riptideDistance");
            riptideDamage = tri.getFloat("riptideDamage");
            riptideRequiresWater = tri.getBoolean("riptideRequiresWater");
        }
        updateVariantComponents();
        snapshotVariantOriginals();
    }

    private void saveVariantData(CompoundTag root) {
        if (variant == WeaponVariant.MACE) {
            CompoundTag mace = new CompoundTag();
            mace.putFloat("smashBonus", smashBonus);
            mace.putFloat("smashCap", smashCap);
            mace.putFloat("smashKnockback", smashKnockback);
            mace.putFloat("smashAoeDamage", smashAoeDamage);
            mace.putBoolean("smashNegateFall", smashNegateFall);
            root.put("Mace", mace);
        } else if (variant == WeaponVariant.TRIDENT) {
            CompoundTag tri = new CompoundTag();
            tri.putFloat("throwDamage", throwDamage);
            tri.putFloat("throwSpeed", throwSpeed);
            tri.putFloat("loyaltySpeed", loyaltySpeed);
            tri.putFloat("riptideDistance", riptideDistance);
            tri.putFloat("riptideDamage", riptideDamage);
            tri.putBoolean("riptideRequiresWater", riptideRequiresWater);
            root.put("Trident", tri);
        }
    }

    private void updateVariantComponents() {
        if (variant == WeaponVariant.MACE) {
            if (smashBonusSlider != null) smashBonusSlider.setValue(smashBonus);
            if (smashCapSlider != null) smashCapSlider.setValue(smashCap);
            if (smashKnockbackSlider != null) smashKnockbackSlider.setValue(smashKnockback);
            if (smashAoeDamageSlider != null) smashAoeDamageSlider.setValue(smashAoeDamage);
            if (smashFallNegationToggle != null) smashFallNegationToggle.setValue(smashNegateFall);
        }
        if (variant == WeaponVariant.TRIDENT) {
            if (throwDamageSlider != null) throwDamageSlider.setValue(throwDamage);
            if (throwSpeedSlider != null) throwSpeedSlider.setValue(throwSpeed);
            if (loyaltySpeedSlider != null) loyaltySpeedSlider.setValue(loyaltySpeed);
            if (riptideDistanceSlider != null) riptideDistanceSlider.setValue(riptideDistance);
            if (riptideDamageSlider != null) riptideDamageSlider.setValue(riptideDamage);
            if (riptideRequiresWaterToggle != null) riptideRequiresWaterToggle.setValue(riptideRequiresWater);
        }
    }

    private void applySourceLabels() {
        applySourceLabel(attackDamageSlider);
        applySourceLabel(attackSpeedSlider);
        applySourceLabel(attackReachSlider);
        applySourceLabel(attackKnockbackSlider);
        applySourceLabel(damageBonusSlider);
        applySourceLabel(armorPenetrationSlider);
        applySourceLabel(baseDamageBonusSlider);
        applySourceLabel(armorShredSlider);
        applySourceLabel(critChanceSlider);
        applySourceLabel(critDamageSlider);
        applySourceLabel(lifestealSlider);
        applySourceLabel(fireDamageSlider);
        applySourceLabel(magicDamageSlider);
        applySourceLabel(trueDamageSlider);
        applySourceLabel(vsUndeadSlider);
        applySourceLabel(vsArthroSlider);
        applySourceLabel(vsPlayersSlider);
    }

    private void applySourceLabel(EditorSlider slider) {
        if (slider == null) return;
        String label = slider.getLabel();
        slider.setLabel(sourcePrefix + label.replaceFirst("^\\[[^]]+\\] ", ""));
    }

    private void snapshotVariantOriginals() {
        originalSmashBonus = smashBonus;
        originalSmashCap = smashCap;
        originalSmashKnockback = smashKnockback;
        originalSmashAoeDamage = smashAoeDamage;
        originalSmashNegateFall = smashNegateFall;
        originalThrowDamage = throwDamage;
        originalThrowSpeed = throwSpeed;
        originalLoyaltySpeed = loyaltySpeed;
        originalRiptideDistance = riptideDistance;
        originalRiptideDamage = riptideDamage;
        originalRiptideRequiresWater = riptideRequiresWater;
    }

    private List<EditorSection> getDebugSections() {
        ItemDebugInfo info = buildDebugInfo();
        List<ValueComparison> comparisons = buildValueComparisons();
        List<String> history = getRecentHistoryEntries(8);
        List<String> nbtLines = DebugInfoSection.formatNbtLines(getCustomDataTag(), 12);
        return List.of(new DebugInfoSection(info, comparisons, history, nbtLines, this::copyDebugInfo));
    }

    private List<EditorSection> withDps(List<EditorSection> sections) {
        List<EditorSection> result = new ArrayList<>(sections);
        result.add(new DpsPreviewSection());
        return result;
    }

    private float computeDps() {
        return EditorCache.getInstance().getOrCompute(
            EditorCache.Types.DPS,
            Objects.requireNonNull(item.toString()),
            () -> stats.calculateDPS(4.0f)
        );
    }

    private ItemDebugInfo buildDebugInfo() {
        var rawItem = Objects.requireNonNull(item.getItem(), "item cannot be null");
        var key = BuiltInRegistries.ITEM.getKey(rawItem);
        String registryName = key == null ? "<unknown>" : key.toString();
        CompoundTag customTag = getCustomDataTag();
        int tagCount = customTag.getAllKeys().size();
        boolean hasCustomData = customTag.contains(NBT_KEY);
        return new ItemDebugInfo(registryName, item.getCount(), item.getDamageValue(), item.getMaxDamage(),
            tagCount, hasCustomData);
    }

    private List<ValueComparison> buildValueComparisons() {
        List<ValueComparison> comparisons = new ArrayList<>();
        CompoundTag customTag = getCustomDataTag();
        boolean hasSpecific = customTag.contains(NBT_KEY);
        boolean hasGlobal = WeaponConfigManager.hasGlobalConfig(item.getItem());
        boolean hasServerStats = hasSpecific || hasGlobal;
        WeaponStats serverStats = resolveServerStats(customTag);
        WeaponStats baseline = originalStats == null ? new WeaponStats() : originalStats;

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

    private WeaponStats loadStatsFromTag(CompoundTag tag) {
        if (tag.contains(NBT_KEY)) {
            return WeaponStats.load(tag.getCompound(NBT_KEY));
        }
        return new WeaponStats();
    }

    private WeaponStats resolveServerStats(CompoundTag tag) {
        if (tag.contains(NBT_KEY)) {
            return loadStatsFromTag(tag);
        }
        // Fall back to GLOBAL config if present
        WeaponStats global = WeaponConfigManager.getGlobalStats(item.getItem());
        return global == null ? new WeaponStats() : global;
    }

    private ValueComparison makeComparison(String label, double originalValue, double serverValue, double currentValue, boolean hasServerStats) {
        double server = hasServerStats ? serverValue : Double.NaN;
        boolean mismatch = hasServerStats && Math.abs(serverValue - currentValue) > EPSILON;
        boolean modified = Math.abs(currentValue - originalValue) > EPSILON;
        return new ValueComparison(label, originalValue, currentValue, server, modified, mismatch);
    }

    private void copyDebugInfo() {
        ItemDebugInfo info = buildDebugInfo();
        List<ValueComparison> comparisons = buildValueComparisons();
        List<String> history = getRecentHistoryEntries(8);
        List<String> nbtLines = DebugInfoSection.formatNbtLines(getCustomDataTag(), 16);
        String payload = buildDebugClipboardText(info, comparisons, history, nbtLines);

        Minecraft mc = Minecraft.getInstance();
        String safePayload = java.util.Objects.requireNonNullElse(payload, "");
        if (mc != null && mc.keyboardHandler != null) {
            mc.keyboardHandler.setClipboard(java.util.Objects.requireNonNull(safePayload, "payload cannot be null"));
        }
        reportStatus("Debug info copied!", UIConstants.Accent.GREEN);
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
    // INPUT (section-based with undo)
    // ═══════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (EditorSection section : getSections()) {
            if (section.mouseClicked(mouseX, mouseY, button)) {
                saveUndoState();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (EditorSection section : getSections()) {
            if (section.keyPressed(keyCode, scanCode, modifiers)) {
                saveUndoState();
                return true;
            }
        }
        return false;
    }

    // NETWORK
    // ═══════════════════════════════════════════════════════════════

    @Override
    public CustomPacketPayload buildPayload(boolean isGlobal) {
        // Build NBT from current stats
        CompoundTag statsTag = new CompoundTag();
        CompoundTag weaponStats = new CompoundTag();
        com.frenkvs.devmod.WeaponConfigManager.clampStats(stats);
        stats.save(weaponStats);
        CompoundTag delta = buildDelta(originalStats, stats);
        if (!delta.isEmpty()) {
            statsTag.put(DELTA_KEY, delta);
        }
        saveVariantData(weaponStats);
        DevMod.LOGGER.info("[Editor][Weapon][BuildPayload] item={} global={} dmg={} spd={} reach={} bonus={} pen={} shred={}",
            item.getItem(), isGlobal, stats.attackDamage, stats.attackSpeed, stats.attackReach,
            stats.baseDamageBonus, stats.armorPenetration, stats.armorShred);
        // Send both legacy and component-friendly payloads
        statsTag.put(NBT_KEY, Objects.requireNonNull(weaponStats.copy()));
        statsTag.put("weapon_stats_component", Objects.requireNonNull(weaponStats));

        return new com.frenkvs.devmod.network.WeaponStatsPayloadV2(Objects.requireNonNull(item, "item cannot be null"), statsTag, isGlobal);
    }

    @Override
    public void applyPreview() {
        // Prepare a preview copy of the item and set CustomData on the copy only.
        try {
            ItemStack copy = item.copy();
            CompoundTag variantTag = new CompoundTag();
            saveVariantData(variantTag);
            DevMod.LOGGER.info("[Editor][Weapon][ApplyPreview] item={} dmg={} spd={} reach={} bonus={} pen={} shred={}",
                item.getItem(), stats.attackDamage, stats.attackSpeed, stats.attackReach,
                stats.baseDamageBonus, stats.armorPenetration, stats.armorShred);
            // Apply full stack edits to the preview copy (CustomData + component + attributes)
            com.frenkvs.devmod.WeaponConfigManager.setSpecificStats(copy, stats.copy(), variantTag);
            // Reload from the written stack to pick up server-side clamps/normalization
            WeaponStats applied = com.frenkvs.devmod.WeaponConfigManager.getStats(copy).copy();
            stats = applied.copy();
            updateSlidersFromStats();
            // Store the preview copy in the base class so UI can render it without mutating the real item
            setPreviewItem(copy);
            // Also advance our working item/original stats so subsequent edits start from the latest applied state
            this.item = copy;
            this.originalStats = applied.copy();
            snapshotVariantOriginals();
        } catch (Exception ignored) {
            // Best-effort: if preview cannot be created, ensure no preview remains
            clearPreview();
        }
    }

    /**
     * Build a delta tag containing only fields that differ from baseline.
     */
    private CompoundTag buildDelta(WeaponStats baseline, WeaponStats current) {
        CompoundTag delta = new CompoundTag();
        if (baseline == null || current == null) return delta;

        // Core stats
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
            tag.putFloat(java.util.Objects.requireNonNull(key, "key"), cur);
        }
    }

    private void putIfChanged(CompoundTag tag, String key, int base, int cur) {
        if (base != cur) {
            tag.putInt(java.util.Objects.requireNonNull(key, "key"), cur);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTERS & STATE
    // ═══════════════════════════════════════════════════════════════

    public WeaponStats getStats() {
        return stats;
    }

    public WeaponStats getOriginalStats() {
        return originalStats;
    }

    public WeaponVariant getVariant() {
        return variant;
    }

    public void setVariant(WeaponVariant variant) {
        this.variant = variant == null ? WeaponVariant.STANDARD : variant;
    }

    /**
     * Reset all stats to original values loaded from the item.
     */
    public void resetToOriginal() {
        stats = originalStats.copy();
        smashBonus = originalSmashBonus;
        smashCap = originalSmashCap;
        smashNegateFall = originalSmashNegateFall;
        throwDamage = originalThrowDamage;
        throwSpeed = originalThrowSpeed;
        loyaltySpeed = originalLoyaltySpeed;
        riptideDistance = originalRiptideDistance;
        riptideDamage = originalRiptideDamage;
        riptideRequiresWater = originalRiptideRequiresWater;
        updateSlidersFromStats();
        updateVariantComponents();
        clearDirty();
    }

    /**
     * Check if current stats differ from original.
     */
    public boolean hasModifications() {
        return !statsEquals(stats, originalStats) || variantChanged();
    }

    @Override
    public boolean hasPendingDiff() {
        return hasModifications();
    }

    private boolean statsEquals(WeaponStats a, WeaponStats b) {
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
            && !variantChanged();
    }

    private boolean toolRulesEqual(List<WeaponStats.ToolRuleData> a, List<WeaponStats.ToolRuleData> b) {
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

    private boolean variantChanged() {
        return switch (variant) {
            case MACE -> Float.compare(smashBonus, originalSmashBonus) != 0
                || Float.compare(smashCap, originalSmashCap) != 0
                || Float.compare(smashKnockback, originalSmashKnockback) != 0
                || Float.compare(smashAoeDamage, originalSmashAoeDamage) != 0
                || smashNegateFall != originalSmashNegateFall;
            case TRIDENT -> Float.compare(throwDamage, originalThrowDamage) != 0
                || Float.compare(throwSpeed, originalThrowSpeed) != 0
                || Float.compare(loyaltySpeed, originalLoyaltySpeed) != 0
                || Float.compare(riptideDistance, originalRiptideDistance) != 0
                || Float.compare(riptideDamage, originalRiptideDamage) != 0
                || riptideRequiresWater != originalRiptideRequiresWater;
            default -> false;
        };
    }

    // ═══════════════════════════════════════════════════════════════
    // SLIDER SECTION ADAPTER
    // ═══════════════════════════════════════════════════════════════

    private class DpsPreviewSection implements EditorSection.CustomSection {
        @Override
        public String getId() { return "dpsPreview"; }
        @Override
        public String getLabel() { return "DPS Preview"; }
        @Override
        public int getHeight() { return UIConstants.Spacing.LG + 14; }

        @Override
        public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
            var font = Objects.requireNonNull(Minecraft.getInstance().font, "font cannot be null");
            float dps = computeDps();
            String dpsText = String.format("DPS: %.1f", dps);
            int textWidth = font.width(Objects.requireNonNull(dpsText));
            int x = bounds.x() + (bounds.width() - textWidth) / 2;
            int y = bounds.y() + UIConstants.Spacing.SM;
            graphics.drawString(font, dpsText, x, y, UIConstants.Text.VALUE, false);
        }
    }

    /**
     * Adapts EditorSlider to EditorSection interface.
     */
    private static class SliderSectionAdapter implements EditorSection.SliderSection {
        private final EditorSlider slider;

        SliderSectionAdapter(EditorSlider slider) {
            this.slider = slider;
        }

        @Override
        public String getId() { return slider.getId(); }

        @Override
        public String getLabel() { return slider.getLabel(); }

        @Override
        public int getHeight() { return slider.calculateHeight(); }

        @Override
        public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
            slider.render(graphics, bounds.x(), bounds.y(), bounds.width(), mouseX, mouseY);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return slider.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            return slider.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            return slider.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return slider.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            return slider.charTyped(chr, modifiers);
        }

        @Override
        public float getValue() { return slider.getValue(); }

        @Override
        public void setValue(float value) { slider.setValue(value); }

        @Override
        public float getMin() { return slider.getMin(); }

        @Override
        public float getMax() { return slider.getMax(); }

        @Override
        public float getStep() { return slider.getStep(); }

        @Override
        public String getFormat() { return "%.2f"; }

        @Override
        public int getColor() { return UIConstants.SliderColors.NEUTRAL; }

        @Override
        public boolean isDragging() { return slider.isDragging(); }

        @Override
        public void setDragging(boolean dragging) { /* handled internally */ }
    }

    /**
     * Adapts EditorToggle to EditorSection.ToggleSection.
     */
    private static class ToggleSectionAdapter implements EditorSection.ToggleSection {
        private final EditorToggle toggle;

        ToggleSectionAdapter(EditorToggle toggle) {
            this.toggle = toggle;
        }

        @Override public String getId() { return toggle.getId(); }
        @Override public String getLabel() { return toggle.getLabel(); }
        @Override public int getHeight() { return 20; }

        @Override
        public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
            toggle.render(graphics, bounds.x(), bounds.y(), bounds.width(), mouseX, mouseY);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return toggle.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return toggle.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override public boolean getValue() { return toggle.getValue(); }
        @Override public void setValue(boolean value) { toggle.setValue(value); }
    }
}
