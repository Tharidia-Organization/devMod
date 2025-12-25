package com.devmod.client.ui.editor.modules;

import com.devmod.stats.ArmorStats;
import com.devmod.config.ArmorConfigManager;
import com.devmod.client.ui.editor.EditorSection;
import com.devmod.client.ui.editor.components.EditorSlider;
import com.devmod.client.ui.editor.components.EditorToggle;
import com.devmod.client.ui.editor.components.SourceBadge;
import com.devmod.client.ui.editor.core.ResponsiveLayout;
import com.devmod.client.ui.editor.core.UIConstants;
import com.devmod.client.ui.editor.debug.DebugInfoSection;
import com.devmod.client.ui.editor.debug.ItemDebugInfo;
import com.devmod.client.ui.editor.debug.ValueComparison;
import com.devmod.client.ui.editor.sections.SliderSectionAdapter;
import com.devmod.client.ui.editor.sections.ToggleSectionAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * UI components and section builders for ArmorModule.
 * Handles all sliders, toggles, and section creation.
 */
public class ArmorModuleUI {

    private static final String NBT_KEY = "ArmorModStats";
    private static final double EPSILON = 1e-4;

    private final ArmorModule module;
    private final ArmorModuleCore core;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Damage Reduction Tab
    // ═══════════════════════════════════════════════════════════════

    private EditorSlider physicalReductionSlider;
    private EditorSlider fireReductionSlider;
    private EditorSlider magicReductionSlider;
    private EditorSlider explosionReductionSlider;
    private EditorSlider projectileReductionSlider;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Vanilla Stats Tab
    // ═══════════════════════════════════════════════════════════════

    private EditorSlider armorBonusSlider;
    private EditorSlider toughnessBonusSlider;
    private EditorSlider knockbackResistanceSlider;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Special Tab
    // ═══════════════════════════════════════════════════════════════

    private EditorToggle thornsToggle;
    private EditorSlider thornsPercentSlider;
    private EditorToggle shieldReflectToggle;
    private EditorSlider shieldBlockStrengthSlider;
    private EditorSlider shieldRecoverySlider;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Shield Visual Tab (Prismatic Integration)
    // ═══════════════════════════════════════════════════════════════

    private EditorSlider shieldOpacitySlider;
    private EditorToggle shieldGlowToggle;
    private EditorSlider shieldGlowIntensitySlider;
    private EditorSlider shieldNoiseIntensitySlider;
    private EditorSlider shieldPulseSpeedSlider;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Shield Deflection Tab
    // ═══════════════════════════════════════════════════════════════

    private EditorSlider shieldDeflectionSpreadSlider;
    private EditorToggle shieldDeflectToOwnerToggle;
    private EditorSlider shieldDeflectSpeedMultSlider;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Shield Shatter Tab
    // ═══════════════════════════════════════════════════════════════

    private EditorSlider shieldShatterThresholdSlider;
    private EditorToggle shieldAutoRegenerateToggle;
    private EditorSlider shieldRegenDelaySlider;

    public ArmorModuleUI(ArmorModule module, ArmorModuleCore core) {
        this.module = module;
        this.core = core;
    }

    // ═══════════════════════════════════════════════════════════════
    // COMPONENT CREATION
    // ═══════════════════════════════════════════════════════════════

    public void createAllComponents(SourceBadge.Source source) {
        createDamageReductionComponents(source);
        createVanillaStatsComponents(source);
        createSpecialComponents(source);
        createShieldComponents(source);
        createShieldVisualComponents(source);
        createShieldDeflectionComponents(source);
        createShieldShatterComponents(source);
    }

    private void createDamageReductionComponents(SourceBadge.Source source) {
        ArmorStats stats = core.getStats();

        physicalReductionSlider = new EditorSlider("physRed", "Physical Reduction", 0f, 80f, 0f)
            .step(1f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.DEFENSE)
            .showInput(true)
            .source(source)
            .info("Reduces physical damage (melee attacks, falls). Max 80%. Formula: Damage * (1 - Reduction%). Stacks with armor value.")
            .onChange(v -> { stats.physicalReduction = v / 100f; module.markDirty("Physical reduction"); });

        fireReductionSlider = new EditorSlider("fireRed", "Fire Reduction", 0f, 80f, 0f)
            .step(1f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .source(source)
            .info("Reduces fire damage (burning, lava, Fire Aspect). Max 80%. Similar to Fire Protection enchant.")
            .onChange(v -> { stats.fireReduction = v / 100f; module.markDirty("Fire reduction"); });

        magicReductionSlider = new EditorSlider("magicRed", "Magic Reduction", 0f, 80f, 0f)
            .step(1f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.SPECIAL)
            .showInput(true)
            .source(source)
            .info("Reduces magic damage (potions, Guardians, Evokers). Max 80%. Bypassed by true damage.")
            .onChange(v -> { stats.magicReduction = v / 100f; module.markDirty("Magic reduction"); });

        explosionReductionSlider = new EditorSlider("explRed", "Explosion Reduction", 0f, 80f, 0f)
            .step(1f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .source(source)
            .info("Reduces explosion damage (Creepers, TNT, Ghast fireballs). Max 80%. Similar to Blast Protection.")
            .onChange(v -> { stats.explosionReduction = v / 100f; module.markDirty("Explosion reduction"); });

        projectileReductionSlider = new EditorSlider("projRed", "Projectile Reduction", 0f, 80f, 0f)
            .step(1f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.DEFENSE)
            .showInput(true)
            .source(source)
            .info("Reduces projectile damage (arrows, tridents, fireballs). Max 80%. Similar to Projectile Protection.")
            .onChange(v -> { stats.projectileReduction = v / 100f; module.markDirty("Projectile reduction"); });
    }

    private void createVanillaStatsComponents(SourceBadge.Source source) {
        ArmorStats stats = core.getStats();

        armorBonusSlider = new EditorSlider("armorBon", "Armor Bonus", 0f, 30f, 0f)
            .step(1f)
            .format("+%.0f")
            .suffix(" pts")
            .trackColor(UIConstants.SliderColors.DEFENSE)
            .showInput(true)
            .source(source)
            .info("Adds to armor points (shields icon). Each point reduces damage by ~4% up to 80% cap. Uses minecraft:armor attribute.")
            .onChange(v -> { stats.armorBonus = v; module.markDirty("Armor bonus"); });

        toughnessBonusSlider = new EditorSlider("toughBon", "Toughness Bonus", 0f, 20f, 0f)
            .step(0.5f)
            .format("+%.1f")
            .suffix(" pts")
            .trackColor(UIConstants.SliderColors.DEFENSE)
            .showInput(true)
            .source(source)
            .info("Reduces armor effectiveness loss from heavy hits. Diamond/Netherite have 2-3 base. Uses minecraft:armor_toughness.")
            .onChange(v -> { stats.toughnessBonus = v; module.markDirty("Toughness bonus"); });

        knockbackResistanceSlider = new EditorSlider("kbRes", "Knockback Resistance", 0f, 100f, 0f)
            .step(5f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.NEUTRAL)
            .showInput(true)
            .source(source)
            .info("Reduces knockback from hits. 100% = no knockback. Netherite gives 10% per piece. Uses minecraft:knockback_resistance.")
            .onChange(v -> { stats.knockbackResistance = v / 100f; module.markDirty("Knockback resistance"); });
    }

    private void createSpecialComponents(SourceBadge.Source source) {
        ArmorStats stats = core.getStats();

        thornsToggle = new EditorToggle("thorns", "Thorns Reflect", false)
            .source(source)
            .tooltip("Enable thorns damage reflection when hit")
            .onChange(v -> { stats.thornsReflect = v; module.markDirty("Thorns enabled"); });

        thornsPercentSlider = new EditorSlider("thornsPct", "Thorns Damage", 0f, 50f, 0f)
            .step(1f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .source(source)
            .info("Reflects damage back to attacker. 50% means attacker takes half the damage they dealt. Only works if Thorns Reflect is enabled.")
            .onChange(v -> { stats.thornsPercent = v / 100f; module.markDirty("Thorns damage"); });
    }

    private void createShieldComponents(SourceBadge.Source source) {
        ArmorStats stats = core.getStats();

        shieldReflectToggle = new EditorToggle("shieldReflect", "Reflect Projectiles", stats.shieldReflectProjectiles)
            .source(source)
            .tooltip("Enables reflecting arrows and projectiles back at attackers when blocking")
            .onChange(v -> { stats.shieldReflectProjectiles = v; module.markDirty("Shield reflect projectiles"); });

        shieldBlockStrengthSlider = new EditorSlider("shieldBlock", "Block Strength", 0f, 1.0f, stats.shieldBlockStrength)
            .step(0.05f)
            .format("%.2f")
            .suffix("x")
            .trackColor(UIConstants.SliderColors.DEFENSE)
            .showInput(true)
            .source(source)
            .info("Damage blocked when shielding. 1.0 = full block, 0.5 = half damage still passes through.")
            .onChange(v -> { stats.shieldBlockStrength = v; module.markDirty("Shield block strength"); });

        shieldRecoverySlider = new EditorSlider("shieldRecovery", "Recovery Speed", 0f, 2.0f, stats.shieldRecoverySpeed)
            .step(0.05f)
            .format("%.2f")
            .suffix("x")
            .trackColor(UIConstants.SliderColors.SPEED)
            .showInput(true)
            .source(source)
            .info("How fast the shield recovers after being disabled by an axe. 2.0 = instant recovery, 0.5 = very slow.")
            .onChange(v -> { stats.shieldRecoverySpeed = v; module.markDirty("Shield recovery"); });
    }

    private void createShieldVisualComponents(SourceBadge.Source source) {
        ArmorStats stats = core.getStats();

        shieldOpacitySlider = new EditorSlider("shieldOpacity", "Shield Opacity", 0.1f, 1.0f, stats.shieldOpacity)
            .step(0.05f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.NEUTRAL)
            .showInput(true)
            .source(source)
            .info("Base transparency of the energy shield. 1.0 = fully opaque, 0.1 = barely visible.")
            .onChange(v -> { stats.shieldOpacity = v; module.markDirty("Shield opacity"); });

        shieldGlowToggle = new EditorToggle("shieldGlow", "Edge Glow", stats.shieldGlowEnabled)
            .source(source)
            .tooltip("Enable Fresnel edge glow effect for a sci-fi look")
            .onChange(v -> { stats.shieldGlowEnabled = v; module.markDirty("Shield glow"); });

        shieldGlowIntensitySlider = new EditorSlider("shieldGlowInt", "Glow Intensity", 0f, 2.0f, stats.shieldGlowIntensity)
            .step(0.1f)
            .format("%.1f")
            .suffix("x")
            .trackColor(UIConstants.SliderColors.SPECIAL)
            .showInput(true)
            .source(source)
            .info("Strength of the edge glow effect. Higher = brighter edges.")
            .onChange(v -> { stats.shieldGlowIntensity = v; module.markDirty("Glow intensity"); });

        shieldNoiseIntensitySlider = new EditorSlider("shieldNoise", "Energy Intensity", 0f, 0.5f, stats.shieldNoiseIntensity)
            .step(0.05f)
            .format("%.2f")
            .suffix("x")
            .trackColor(UIConstants.SliderColors.SPECIAL)
            .showInput(true)
            .source(source)
            .info("Animated energy field noise pattern intensity. 0 = smooth, 0.5 = very turbulent.")
            .onChange(v -> { stats.shieldNoiseIntensity = v; module.markDirty("Energy intensity"); });

        shieldPulseSpeedSlider = new EditorSlider("shieldPulse", "Animation Speed", 0.5f, 2.0f, stats.shieldPulseSpeed)
            .step(0.1f)
            .format("%.1f")
            .suffix("x")
            .trackColor(UIConstants.SliderColors.SPEED)
            .showInput(true)
            .source(source)
            .info("Speed of shield animation effects. 1.0 = normal, 2.0 = double speed.")
            .onChange(v -> { stats.shieldPulseSpeed = v; module.markDirty("Animation speed"); });
    }

    private void createShieldDeflectionComponents(SourceBadge.Source source) {
        ArmorStats stats = core.getStats();

        // Convert radians to degrees for display (0.15 rad ≈ 8.6°)
        float spreadDegrees = (float) Math.toDegrees(stats.shieldDeflectionSpread);

        shieldDeflectionSpreadSlider = new EditorSlider("deflectSpread", "Deflection Spread", 0f, 30f, spreadDegrees)
            .step(1f)
            .format("%.0f")
            .suffix("°")
            .trackColor(UIConstants.SliderColors.NEUTRAL)
            .showInput(true)
            .source(source)
            .info("Maximum random angle for deflected projectiles. 0° = perfect reflection, 30° = very scattered.")
            .onChange(v -> { stats.shieldDeflectionSpread = (float) Math.toRadians(v); module.markDirty("Deflection spread"); });

        shieldDeflectToOwnerToggle = new EditorToggle("deflectReturn", "Return to Sender", stats.shieldDeflectToOwner)
            .source(source)
            .tooltip("Deflect projectiles back toward the original shooter")
            .onChange(v -> { stats.shieldDeflectToOwner = v; module.markDirty("Return to sender"); });

        shieldDeflectSpeedMultSlider = new EditorSlider("deflectSpeed", "Deflect Speed", 0.5f, 1.5f, stats.shieldDeflectSpeedMult)
            .step(0.05f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.SPEED)
            .showInput(true)
            .source(source)
            .info("Projectile speed after deflection. 100% = same speed, 50% = half speed, 150% = faster.")
            .onChange(v -> { stats.shieldDeflectSpeedMult = v; module.markDirty("Deflect speed"); });
    }

    private void createShieldShatterComponents(SourceBadge.Source source) {
        ArmorStats stats = core.getStats();

        shieldShatterThresholdSlider = new EditorSlider("shatterThresh", "Shatter Threshold", 5f, 50f, stats.shieldShatterThreshold)
            .step(1f)
            .format("%.0f")
            .suffix(" dmg")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .source(source)
            .info("Damage required in a single hit to break the shield. Higher = more durable.")
            .onChange(v -> { stats.shieldShatterThreshold = v; module.markDirty("Shatter threshold"); });

        shieldAutoRegenerateToggle = new EditorToggle("autoRegen", "Auto Regenerate", stats.shieldAutoRegenerate)
            .source(source)
            .tooltip("Shield automatically regenerates after being shattered")
            .onChange(v -> { stats.shieldAutoRegenerate = v; module.markDirty("Auto regenerate"); });

        shieldRegenDelaySlider = new EditorSlider("regenDelay", "Regen Delay", 1f, 10f, stats.shieldRegenDelay)
            .step(0.5f)
            .format("%.1f")
            .suffix("s")
            .trackColor(UIConstants.SliderColors.SPEED)
            .showInput(true)
            .source(source)
            .info("Seconds before shield starts regenerating after being shattered.")
            .onChange(v -> { stats.shieldRegenDelay = v; module.markDirty("Regen delay"); });
    }

    // ═══════════════════════════════════════════════════════════════
    // UPDATE COMPONENTS FROM STATS
    // ═══════════════════════════════════════════════════════════════

    public void updateComponentsFromStats() {
        ArmorStats stats = core.getStats();

        if (physicalReductionSlider != null) physicalReductionSlider.setValue(stats.physicalReduction * 100);
        if (fireReductionSlider != null) fireReductionSlider.setValue(stats.fireReduction * 100);
        if (magicReductionSlider != null) magicReductionSlider.setValue(stats.magicReduction * 100);
        if (explosionReductionSlider != null) explosionReductionSlider.setValue(stats.explosionReduction * 100);
        if (projectileReductionSlider != null) projectileReductionSlider.setValue(stats.projectileReduction * 100);

        if (armorBonusSlider != null) armorBonusSlider.setValue(stats.armorBonus);
        if (toughnessBonusSlider != null) toughnessBonusSlider.setValue(stats.toughnessBonus);
        if (knockbackResistanceSlider != null) knockbackResistanceSlider.setValue(stats.knockbackResistance * 100);

        if (thornsToggle != null) thornsToggle.setValue(stats.thornsReflect);
        if (thornsPercentSlider != null) thornsPercentSlider.setValue(stats.thornsPercent * 100);
        if (shieldReflectToggle != null) shieldReflectToggle.setValue(stats.shieldReflectProjectiles);
        if (shieldBlockStrengthSlider != null) shieldBlockStrengthSlider.setValue(stats.shieldBlockStrength);
        if (shieldRecoverySlider != null) shieldRecoverySlider.setValue(stats.shieldRecoverySpeed);

        // Shield Visual Tab
        if (shieldOpacitySlider != null) shieldOpacitySlider.setValue(stats.shieldOpacity);
        if (shieldGlowToggle != null) shieldGlowToggle.setValue(stats.shieldGlowEnabled);
        if (shieldGlowIntensitySlider != null) shieldGlowIntensitySlider.setValue(stats.shieldGlowIntensity);
        if (shieldNoiseIntensitySlider != null) shieldNoiseIntensitySlider.setValue(stats.shieldNoiseIntensity);
        if (shieldPulseSpeedSlider != null) shieldPulseSpeedSlider.setValue(stats.shieldPulseSpeed);

        // Shield Deflection Tab
        if (shieldDeflectionSpreadSlider != null) shieldDeflectionSpreadSlider.setValue((float) Math.toDegrees(stats.shieldDeflectionSpread));
        if (shieldDeflectToOwnerToggle != null) shieldDeflectToOwnerToggle.setValue(stats.shieldDeflectToOwner);
        if (shieldDeflectSpeedMultSlider != null) shieldDeflectSpeedMultSlider.setValue(stats.shieldDeflectSpeedMult);

        // Shield Shatter Tab
        if (shieldShatterThresholdSlider != null) shieldShatterThresholdSlider.setValue(stats.shieldShatterThreshold);
        if (shieldAutoRegenerateToggle != null) shieldAutoRegenerateToggle.setValue(stats.shieldAutoRegenerate);
        if (shieldRegenDelaySlider != null) shieldRegenDelaySlider.setValue(stats.shieldRegenDelay);

        // Update source badges to reflect current state
        updateSourceBadges();
    }

    /**
     * Update all source badges to reflect current modification state.
     * Shows MODIFIED (yellow) if value differs from original, otherwise shows origin source.
     */
    public void updateSourceBadges() {
        SourceBadge.Source baseSource = core.determineSource();
        boolean isModified = core.hasPendingDiff();

        // Use MODIFIED badge if any value changed, otherwise use the base source
        SourceBadge.Source effectiveSource = isModified ? SourceBadge.Source.MODIFIED : baseSource;

        // Update all slider badges
        if (physicalReductionSlider != null) physicalReductionSlider.source(effectiveSource);
        if (fireReductionSlider != null) fireReductionSlider.source(effectiveSource);
        if (magicReductionSlider != null) magicReductionSlider.source(effectiveSource);
        if (explosionReductionSlider != null) explosionReductionSlider.source(effectiveSource);
        if (projectileReductionSlider != null) projectileReductionSlider.source(effectiveSource);
        if (armorBonusSlider != null) armorBonusSlider.source(effectiveSource);
        if (toughnessBonusSlider != null) toughnessBonusSlider.source(effectiveSource);
        if (knockbackResistanceSlider != null) knockbackResistanceSlider.source(effectiveSource);
        if (thornsPercentSlider != null) thornsPercentSlider.source(effectiveSource);
        if (shieldBlockStrengthSlider != null) shieldBlockStrengthSlider.source(effectiveSource);
        if (shieldRecoverySlider != null) shieldRecoverySlider.source(effectiveSource);
        if (shieldOpacitySlider != null) shieldOpacitySlider.source(effectiveSource);
        if (shieldGlowIntensitySlider != null) shieldGlowIntensitySlider.source(effectiveSource);
        if (shieldNoiseIntensitySlider != null) shieldNoiseIntensitySlider.source(effectiveSource);
        if (shieldPulseSpeedSlider != null) shieldPulseSpeedSlider.source(effectiveSource);
        if (shieldDeflectionSpreadSlider != null) shieldDeflectionSpreadSlider.source(effectiveSource);
        if (shieldDeflectSpeedMultSlider != null) shieldDeflectSpeedMultSlider.source(effectiveSource);
        if (shieldShatterThresholdSlider != null) shieldShatterThresholdSlider.source(effectiveSource);
        if (shieldRegenDelaySlider != null) shieldRegenDelaySlider.source(effectiveSource);

        // Update all toggle badges
        if (thornsToggle != null) thornsToggle.source(effectiveSource);
        if (shieldReflectToggle != null) shieldReflectToggle.source(effectiveSource);
        if (shieldGlowToggle != null) shieldGlowToggle.source(effectiveSource);
        if (shieldDeflectToOwnerToggle != null) shieldDeflectToOwnerToggle.source(effectiveSource);
        if (shieldAutoRegenerateToggle != null) shieldAutoRegenerateToggle.source(effectiveSource);
    }

    // ═══════════════════════════════════════════════════════════════
    // SECTION BUILDERS
    // ═══════════════════════════════════════════════════════════════

    public List<EditorSection> getDamageReductionSections() {
        return withEhp(List.of(
            new SliderSectionAdapter(physicalReductionSlider),
            new SliderSectionAdapter(fireReductionSlider),
            new SliderSectionAdapter(magicReductionSlider),
            new SliderSectionAdapter(explosionReductionSlider),
            new SliderSectionAdapter(projectileReductionSlider)
        ));
    }

    public List<EditorSection> getVanillaStatsSections() {
        return withEhp(List.of(
            new SliderSectionAdapter(armorBonusSlider),
            new SliderSectionAdapter(toughnessBonusSlider),
            new SliderSectionAdapter(knockbackResistanceSlider)
        ));
    }

    public List<EditorSection> getSpecialSections() {
        return withEhp(List.of(
            new ToggleSectionAdapter(thornsToggle),
            new SliderSectionAdapter(thornsPercentSlider)
        ));
    }

    public List<EditorSection> getShieldSections() {
        return withEhp(List.of(
            new ToggleSectionAdapter(shieldReflectToggle),
            new SliderSectionAdapter(shieldBlockStrengthSlider),
            new SliderSectionAdapter(shieldRecoverySlider)
        ));
    }

    public List<EditorSection> getShieldVisualSections() {
        return withEhp(List.of(
            new SliderSectionAdapter(shieldOpacitySlider),
            new ToggleSectionAdapter(shieldGlowToggle),
            new SliderSectionAdapter(shieldGlowIntensitySlider),
            new SliderSectionAdapter(shieldNoiseIntensitySlider),
            new SliderSectionAdapter(shieldPulseSpeedSlider)
        ));
    }

    public List<EditorSection> getShieldDeflectionSections() {
        return withEhp(List.of(
            new SliderSectionAdapter(shieldDeflectionSpreadSlider),
            new ToggleSectionAdapter(shieldDeflectToOwnerToggle),
            new SliderSectionAdapter(shieldDeflectSpeedMultSlider)
        ));
    }

    public List<EditorSection> getShieldShatterSections() {
        return withEhp(List.of(
            new SliderSectionAdapter(shieldShatterThresholdSlider),
            new ToggleSectionAdapter(shieldAutoRegenerateToggle),
            new SliderSectionAdapter(shieldRegenDelaySlider)
        ));
    }

    public List<EditorSection> getDebugSections(ItemStack item) {
        ItemDebugInfo info = buildDebugInfo(item);
        List<ValueComparison> comparisons = buildValueComparisons(item);
        List<String> history = module.getRecentHistoryEntriesPublic(8);
        List<String> nbtLines = DebugInfoSection.formatNbtLines(core.getCustomDataTag(item), 12);
        return List.of(new DebugInfoSection(info, comparisons, history, nbtLines, () -> copyDebugInfo(item)));
    }

    private List<EditorSection> withEhp(List<EditorSection> sections) {
        List<EditorSection> result = new ArrayList<>(sections);
        result.add(new EhpPreviewSection());
        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    // DEBUG INFO
    // ═══════════════════════════════════════════════════════════════

    private ItemDebugInfo buildDebugInfo(ItemStack item) {
        var rawItem = Objects.requireNonNull(item.getItem(), "item cannot be null");
        var key = BuiltInRegistries.ITEM.getKey(rawItem);
        String registryName = key == null ? "<unknown>" : key.toString();
        CompoundTag tag = core.getCustomDataTag(item);
        int tagCount = tag.getAllKeys().size();
        boolean hasCustomData = tag.contains(NBT_KEY);
        return new ItemDebugInfo(registryName, item.getCount(), item.getDamageValue(), item.getMaxDamage(),
            tagCount, hasCustomData);
    }

    private List<ValueComparison> buildValueComparisons(ItemStack item) {
        ArmorStats stats = core.getStats();
        ArmorStats originalStats = core.getOriginalStats();

        List<ValueComparison> comparisons = new ArrayList<>();
        CompoundTag tag = core.getCustomDataTag(item);
        boolean hasSpecific = tag.contains(NBT_KEY);
        boolean hasGlobal = ArmorConfigManager.hasGlobalConfig(item.getItem());
        boolean hasServerStats = hasSpecific || hasGlobal;
        ArmorStats serverStats = core.resolveServerStats(tag, item);
        ArmorStats baseline = originalStats == null ? new ArmorStats() : originalStats;

        comparisons.add(makeComparison("Physical Reduction (%)", baseline.physicalReduction * 100, serverStats.physicalReduction * 100, stats.physicalReduction * 100, hasServerStats));
        comparisons.add(makeComparison("Fire Reduction (%)", baseline.fireReduction * 100, serverStats.fireReduction * 100, stats.fireReduction * 100, hasServerStats));
        comparisons.add(makeComparison("Magic Reduction (%)", baseline.magicReduction * 100, serverStats.magicReduction * 100, stats.magicReduction * 100, hasServerStats));
        comparisons.add(makeComparison("Explosion Reduction (%)", baseline.explosionReduction * 100, serverStats.explosionReduction * 100, stats.explosionReduction * 100, hasServerStats));
        comparisons.add(makeComparison("Projectile Reduction (%)", baseline.projectileReduction * 100, serverStats.projectileReduction * 100, stats.projectileReduction * 100, hasServerStats));

        comparisons.add(makeComparison("Armor Bonus", baseline.armorBonus, serverStats.armorBonus, stats.armorBonus, hasServerStats));
        comparisons.add(makeComparison("Toughness Bonus", baseline.toughnessBonus, serverStats.toughnessBonus, stats.toughnessBonus, hasServerStats));
        comparisons.add(makeComparison("Knockback Resist (%)", baseline.knockbackResistance * 100, serverStats.knockbackResistance * 100, stats.knockbackResistance * 100, hasServerStats));

        comparisons.add(makeComparison("Thorns (%)", baseline.thornsPercent * 100, serverStats.thornsPercent * 100, stats.thornsPercent * 100, hasServerStats));
        comparisons.add(makeComparison("Thorns reflect", baseline.thornsReflect ? 1 : 0, serverStats.thornsReflect ? 1 : 0, stats.thornsReflect ? 1 : 0, hasServerStats));

        ArmorModule.ArmorVariant variant = module.getVariant();
        if (variant == ArmorModule.ArmorVariant.SHIELD) {
            comparisons.add(makeComparison("Shield Block", baseline.shieldBlockStrength, serverStats.shieldBlockStrength, stats.shieldBlockStrength, hasServerStats));
            comparisons.add(makeComparison("Shield Recovery", baseline.shieldRecoverySpeed, serverStats.shieldRecoverySpeed, stats.shieldRecoverySpeed, hasServerStats));
            comparisons.add(makeComparison("Shield Reflect", baseline.shieldReflectProjectiles ? 1 : 0, serverStats.shieldReflectProjectiles ? 1 : 0, stats.shieldReflectProjectiles ? 1 : 0, hasServerStats));
        }
        return comparisons;
    }

    private ValueComparison makeComparison(String label, double originalValue, double serverValue, double currentValue, boolean hasServerStats) {
        double server = hasServerStats ? serverValue : Double.NaN;
        boolean mismatch = hasServerStats && Math.abs(serverValue - currentValue) > EPSILON;
        boolean modified = Math.abs(currentValue - originalValue) > EPSILON;
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
    // EHP PREVIEW SECTION
    // ═══════════════════════════════════════════════════════════════

    private class EhpPreviewSection implements EditorSection.CustomSection {
        private static final int HEIGHT_EXTRA = 14;
        private static final int TEXT_OFFSET_Y = UIConstants.Spacing.SM;

        @Override public String getId() { return "ehpPreview"; }
        @Override public String getLabel() { return "EHP Preview"; }
        @Override public int getHeight() { return UIConstants.Spacing.LG + HEIGHT_EXTRA; }

        @Override
        public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
            var font = Objects.requireNonNull(Minecraft.getInstance().font, "font cannot be null");
            float ehp = core.calculateEHP();
            String text = String.format("EHP: %.1fx", ehp);
            int textWidth = font.width(Objects.requireNonNull(text, "text"));
            int x = bounds.x() + (bounds.width() - textWidth) / 2;
            int y = bounds.y() + TEXT_OFFSET_Y;
            graphics.drawString(font, text, x, y, UIConstants.Text.VALUE(), false);
        }
    }
}
