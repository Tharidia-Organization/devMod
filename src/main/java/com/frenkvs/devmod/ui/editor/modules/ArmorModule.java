package com.frenkvs.devmod.ui.editor.modules;

import com.frenkvs.devmod.ArmorStats;
import com.frenkvs.devmod.ArmorConfigManager;
import com.frenkvs.devmod.ArmorComponents;
import com.frenkvs.devmod.DevMod;
import com.frenkvs.devmod.network.ArmorStatsPayloadV2;
import com.frenkvs.devmod.ui.editor.AbstractEditorModule;
import com.frenkvs.devmod.ui.editor.EditorSection;
import com.frenkvs.devmod.ui.editor.ModuleTab;
import com.frenkvs.devmod.ui.editor.components.EditorSlider;
import com.frenkvs.devmod.ui.editor.components.EditorToggle;
import com.frenkvs.devmod.ui.editor.components.SourceBadge;
import com.frenkvs.devmod.ui.editor.core.EditorCache;
import com.frenkvs.devmod.ui.editor.core.ResponsiveLayout;
import com.frenkvs.devmod.ui.editor.core.UIConstants;
import com.frenkvs.devmod.ui.editor.debug.DebugInfoSection;
import com.frenkvs.devmod.ui.editor.debug.ItemDebugInfo;
import com.frenkvs.devmod.ui.editor.debug.ValueComparison;
import com.frenkvs.devmod.ui.editor.sections.SliderSectionAdapter;
import com.frenkvs.devmod.ui.editor.sections.ToggleSectionAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Editor module for armor statistics.
 * Allows editing damage reductions, armor bonuses, and special effects.
 *
 * @see EDITOR_DESIGN_SYSTEM.md Section 3.4
 */
public class ArmorModule extends AbstractEditorModule {

    // ═══════════════════════════════════════════════════════════════
    // ARMOR STATS
    // ═══════════════════════════════════════════════════════════════

    private static final String NBT_KEY = "ArmorModStats";
    private static final double EPSILON = 1e-4;

    // Source tracking for badges
    private boolean hasComponentData = false;
    private boolean hasNbtData = false;
    private boolean hasGlobalConfig = false;
    private static final @Nonnull net.minecraft.world.item.component.ItemAttributeModifiers NONNULL_EMPTY =
        Objects.requireNonNull(net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY,
            "ItemAttributeModifiers.EMPTY cannot be null");
    private ArmorStats stats = new ArmorStats();
    private ArmorStats originalStats = new ArmorStats();
    public enum ArmorVariant { STANDARD, SHIELD }
    private ArmorVariant variant = ArmorVariant.STANDARD;

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

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public ArmorModule() {
        super("armor", "Armor Editor");
    }

    public ArmorModule(ArmorVariant variant) {
        super("armor", "Armor Editor");
        this.variant = variant == null ? ArmorVariant.STANDARD : variant;
    }

    // ═══════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void onItemSet() {
        // Auto-detect shield variant from item type
        detectVariantFromItem();

        // Load existing armor stats from item NBT
        loadStatsFromItem();
        originalStats = stats.copy();

        // Reinitialize tabs now that we know the variant
        initializeTabs();

        // Update all components to reflect current stats
        updateComponentsFromStats();
    }

    /**
     * Detects if the item is a shield and sets the variant accordingly.
     */
    private void detectVariantFromItem() {
        if (item == null) {
            variant = ArmorVariant.STANDARD;
            return;
        }

        // Check if item is a ShieldItem
        if (item.getItem() instanceof net.minecraft.world.item.ShieldItem) {
            variant = ArmorVariant.SHIELD;
            DevMod.LOGGER.info("[Editor][Armor] Detected SHIELD variant for item: {}", item.getItem());
        } else {
            variant = ArmorVariant.STANDARD;
        }
    }

    private void loadStatsFromItem() {
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
            applyVanillaDefaults(stats);
        }
    }

    /**
     * Determine the source badge type based on current data origin.
     */
    private SourceBadge.Source determineSource() {
        if (hasComponentData || hasGlobalConfig) {
            return SourceBadge.Source.DEV;
        }
        if (hasNbtData) {
            return SourceBadge.Source.NBT;
        }
        return SourceBadge.Source.VANILLA;
    }

    /**
     * Populate armor stats from vanilla attributes when no custom data exists.
     */
    private void applyVanillaDefaults(ArmorStats target) {
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

            @Nonnull net.minecraft.world.item.component.ItemAttributeModifiers mods = java.util.Objects.requireNonNull(
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
        return java.util.Objects.requireNonNull(mods, "ItemAttributeModifiers cannot be null");
    }

    @Nonnull
    private static net.minecraft.world.item.component.ItemAttributeModifiers mergeAttributeSets(
        @Nonnull net.minecraft.world.item.component.ItemAttributeModifiers base,
        @Nullable net.minecraft.world.item.component.ItemAttributeModifiers extra
    ) {
        if (extra == null || extra == NONNULL_EMPTY || extra.modifiers().isEmpty()) {
            return base;
        }
        java.util.List<net.minecraft.world.item.component.ItemAttributeModifiers.Entry> merged =
            new java.util.ArrayList<>(base.modifiers());
        merged.addAll(extra.modifiers());
        boolean show = base.showInTooltip() || extra.showInTooltip();
        return new net.minecraft.world.item.component.ItemAttributeModifiers(merged, show);
    }

    private void updateComponentsFromStats() {
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
    private void updateSourceBadges() {
        SourceBadge.Source baseSource = determineSource();
        boolean isModified = hasPendingDiff();

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

    /**
     * Apply external stats (preset/import) while keeping undo/history in sync.
     */
    public void applyExternalStats(ArmorStats newStats, String reason) {
        if (newStats == null) {
            return;
        }
        saveUndoState();
        stats = newStats.copy();
        updateComponentsFromStats();
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
        createDamageReductionComponents();
        createVanillaStatsComponents();
        createSpecialComponents();
        createShieldComponents();
        createShieldVisualComponents();
        createShieldDeflectionComponents();
        createShieldShatterComponents();

        // Add tabs
        addTab(ModuleTab.of("reduction", "Reduction", this::getDamageReductionSections));
        addTab(ModuleTab.of("stats", "Stats", this::getVanillaStatsSections));
        addTab(ModuleTab.of("special", "Special", this::getSpecialSections));
        if (variant == ArmorVariant.SHIELD) {
            addTab(ModuleTab.of("shield", "Shield", this::getShieldSections));
            addTab(ModuleTab.of("visual", "Visual", this::getShieldVisualSections));
            addTab(ModuleTab.of("deflect", "Deflect", this::getShieldDeflectionSections));
            addTab(ModuleTab.of("shatter", "Shatter", this::getShieldShatterSections));
        }
        addTab(ModuleTab.of("debug", "Debug", this::getDebugSections));
    }

    private CompoundTag getCustomDataTag() {
        Objects.requireNonNull(item, "item cannot be null");
        CustomData customData = item.getOrDefault(
            Objects.requireNonNull(DataComponents.CUSTOM_DATA, "DataComponents.CUSTOM_DATA cannot be null"),
            Objects.requireNonNull(CustomData.EMPTY, "CustomData.EMPTY cannot be null")
        );
        CompoundTag tag = customData.copyTag();
        return tag == null ? new CompoundTag() : tag;
    }

    private List<EditorSection> getDebugSections() {
        ItemDebugInfo info = buildDebugInfo();
        List<ValueComparison> comparisons = buildValueComparisons();
        List<String> history = getRecentHistoryEntries(8);
        List<String> nbtLines = DebugInfoSection.formatNbtLines(getCustomDataTag(), 12);
        return List.of(new DebugInfoSection(info, comparisons, history, nbtLines, this::copyDebugInfo));
    }

    private List<EditorSection> withEhp(List<EditorSection> sections) {
        List<EditorSection> result = new ArrayList<>(sections);
        result.add(new EhpPreviewSection());
        return result;
    }

    private float computeEhp() {
        return EditorCache.getInstance().getOrCompute(
            EditorCache.Types.EHP,
            Objects.requireNonNull(item.toString()),
            this::calculateEHP
        );
    }

    private ItemDebugInfo buildDebugInfo() {
        var rawItem = Objects.requireNonNull(item.getItem(), "item cannot be null");
        var key = BuiltInRegistries.ITEM.getKey(rawItem);
        String registryName = key == null ? "<unknown>" : key.toString();
        CompoundTag tag = getCustomDataTag();
        int tagCount = tag.getAllKeys().size();
        boolean hasCustomData = tag.contains(NBT_KEY);
        return new ItemDebugInfo(registryName, item.getCount(), item.getDamageValue(), item.getMaxDamage(),
            tagCount, hasCustomData);
    }

    private List<ValueComparison> buildValueComparisons() {
        List<ValueComparison> comparisons = new ArrayList<>();
        CompoundTag tag = getCustomDataTag();
        boolean hasSpecific = tag.contains(NBT_KEY);
        boolean hasGlobal = ArmorConfigManager.hasGlobalConfig(item.getItem());
        boolean hasServerStats = hasSpecific || hasGlobal;
        ArmorStats serverStats = resolveServerStats(tag);
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
        if (variant == ArmorVariant.SHIELD) {
            comparisons.add(makeComparison("Shield Block", baseline.shieldBlockStrength, serverStats.shieldBlockStrength, stats.shieldBlockStrength, hasServerStats));
            comparisons.add(makeComparison("Shield Recovery", baseline.shieldRecoverySpeed, serverStats.shieldRecoverySpeed, stats.shieldRecoverySpeed, hasServerStats));
            comparisons.add(makeComparison("Shield Reflect", baseline.shieldReflectProjectiles ? 1 : 0, serverStats.shieldReflectProjectiles ? 1 : 0, stats.shieldReflectProjectiles ? 1 : 0, hasServerStats));
        }
        return comparisons;
    }

    private ArmorStats loadStatsFromTag(CompoundTag tag) {
        if (tag.contains(NBT_KEY)) {
            return ArmorStats.load(tag.getCompound(NBT_KEY));
        }
        return new ArmorStats();
    }

    private ArmorStats resolveServerStats(CompoundTag tag) {
        if (tag.contains(NBT_KEY)) {
            return loadStatsFromTag(tag);
        }
        ArmorStats global = ArmorConfigManager.getGlobalStats(item.getItem());
        return global == null ? new ArmorStats() : global;
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
        reportStatus("Debug info copied!", UIConstants.Accent.GREEN());
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

    private float calculateEHP() {
        float totalReduction = stats.physicalReduction;
        float cappedReduction = Math.min(totalReduction, 0.8f);
        return 1f / (1f - cappedReduction);
    }

    // ═══════════════════════════════════════════════════════════════
    // DAMAGE REDUCTION TAB
    // ═══════════════════════════════════════════════════════════════

    private void createDamageReductionComponents() {
        SourceBadge.Source source = determineSource();

        physicalReductionSlider = new EditorSlider("physRed", "Physical Reduction", 0f, 80f, 0f)
            .step(1f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.DEFENSE)
            .showInput(true)
            .source(source)
            .info("Reduces physical damage (melee attacks, falls). Max 80%. Formula: Damage * (1 - Reduction%). Stacks with armor value.")
            .onChange(v -> { stats.physicalReduction = v / 100f; markDirty("Physical reduction"); });

        fireReductionSlider = new EditorSlider("fireRed", "Fire Reduction", 0f, 80f, 0f)
            .step(1f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .source(source)
            .info("Reduces fire damage (burning, lava, Fire Aspect). Max 80%. Similar to Fire Protection enchant.")
            .onChange(v -> { stats.fireReduction = v / 100f; markDirty("Fire reduction"); });

        magicReductionSlider = new EditorSlider("magicRed", "Magic Reduction", 0f, 80f, 0f)
            .step(1f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.SPECIAL)
            .showInput(true)
            .source(source)
            .info("Reduces magic damage (potions, Guardians, Evokers). Max 80%. Bypassed by true damage.")
            .onChange(v -> { stats.magicReduction = v / 100f; markDirty("Magic reduction"); });

        explosionReductionSlider = new EditorSlider("explRed", "Explosion Reduction", 0f, 80f, 0f)
            .step(1f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .source(source)
            .info("Reduces explosion damage (Creepers, TNT, Ghast fireballs). Max 80%. Similar to Blast Protection.")
            .onChange(v -> { stats.explosionReduction = v / 100f; markDirty("Explosion reduction"); });

        projectileReductionSlider = new EditorSlider("projRed", "Projectile Reduction", 0f, 80f, 0f)
            .step(1f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.DEFENSE)
            .showInput(true)
            .source(source)
            .info("Reduces projectile damage (arrows, tridents, fireballs). Max 80%. Similar to Projectile Protection.")
            .onChange(v -> { stats.projectileReduction = v / 100f; markDirty("Projectile reduction"); });
    }

    private List<EditorSection> getDamageReductionSections() {
        return withEhp(List.of(
            new SliderSectionAdapter(physicalReductionSlider),
            new SliderSectionAdapter(fireReductionSlider),
            new SliderSectionAdapter(magicReductionSlider),
            new SliderSectionAdapter(explosionReductionSlider),
            new SliderSectionAdapter(projectileReductionSlider)
        ));
    }

    // ═══════════════════════════════════════════════════════════════
    // VANILLA STATS TAB
    // ═══════════════════════════════════════════════════════════════

    private void createVanillaStatsComponents() {
        SourceBadge.Source source = determineSource();

        armorBonusSlider = new EditorSlider("armorBon", "Armor Bonus", 0f, 30f, 0f)
            .step(1f)
            .format("+%.0f")
            .suffix(" pts")
            .trackColor(UIConstants.SliderColors.DEFENSE)
            .showInput(true)
            .source(source)
            .info("Adds to armor points (shields icon). Each point reduces damage by ~4% up to 80% cap. Uses minecraft:armor attribute.")
            .onChange(v -> { stats.armorBonus = v; markDirty("Armor bonus"); });

        toughnessBonusSlider = new EditorSlider("toughBon", "Toughness Bonus", 0f, 20f, 0f)
            .step(0.5f)
            .format("+%.1f")
            .suffix(" pts")
            .trackColor(UIConstants.SliderColors.DEFENSE)
            .showInput(true)
            .source(source)
            .info("Reduces armor effectiveness loss from heavy hits. Diamond/Netherite have 2-3 base. Uses minecraft:armor_toughness.")
            .onChange(v -> { stats.toughnessBonus = v; markDirty("Toughness bonus"); });

        knockbackResistanceSlider = new EditorSlider("kbRes", "Knockback Resistance", 0f, 100f, 0f)
            .step(5f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.NEUTRAL)
            .showInput(true)
            .source(source)
            .info("Reduces knockback from hits. 100% = no knockback. Netherite gives 10% per piece. Uses minecraft:knockback_resistance.")
            .onChange(v -> { stats.knockbackResistance = v / 100f; markDirty("Knockback resistance"); });
    }

    private List<EditorSection> getVanillaStatsSections() {
        return withEhp(List.of(
            new SliderSectionAdapter(armorBonusSlider),
            new SliderSectionAdapter(toughnessBonusSlider),
            new SliderSectionAdapter(knockbackResistanceSlider)
        ));
    }

    // ═══════════════════════════════════════════════════════════════
    // SPECIAL TAB
    // ═══════════════════════════════════════════════════════════════

    private void createSpecialComponents() {
        SourceBadge.Source source = determineSource();

        thornsToggle = new EditorToggle("thorns", "Thorns Reflect", false)
            .source(source)
            .tooltip("Enable thorns damage reflection when hit")
            .onChange(v -> { stats.thornsReflect = v; markDirty("Thorns enabled"); });

        thornsPercentSlider = new EditorSlider("thornsPct", "Thorns Damage", 0f, 50f, 0f)
            .step(1f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .source(source)
            .info("Reflects damage back to attacker. 50% means attacker takes half the damage they dealt. Only works if Thorns Reflect is enabled.")
            .onChange(v -> { stats.thornsPercent = v / 100f; markDirty("Thorns damage"); });
    }

    private void createShieldComponents() {
        SourceBadge.Source source = determineSource();

        shieldReflectToggle = new EditorToggle("shieldReflect", "Reflect Projectiles", stats.shieldReflectProjectiles)
            .source(source)
            .tooltip("Enables reflecting arrows and projectiles back at attackers when blocking")
            .onChange(v -> { stats.shieldReflectProjectiles = v; markDirty("Shield reflect projectiles"); });
        shieldBlockStrengthSlider = new EditorSlider("shieldBlock", "Block Strength", 0f, 1.0f, stats.shieldBlockStrength)
            .step(0.05f)
            .format("%.2f")
            .suffix("x")
            .trackColor(UIConstants.SliderColors.DEFENSE)
            .showInput(true)
            .source(source)
            .info("Damage blocked when shielding. 1.0 = full block, 0.5 = half damage still passes through.")
            .onChange(v -> { stats.shieldBlockStrength = v; markDirty("Shield block strength"); });
        shieldRecoverySlider = new EditorSlider("shieldRecovery", "Recovery Speed", 0f, 2.0f, stats.shieldRecoverySpeed)
            .step(0.05f)
            .format("%.2f")
            .suffix("x")
            .trackColor(UIConstants.SliderColors.SPEED)
            .showInput(true)
            .source(source)
            .info("How fast the shield recovers after being disabled by an axe. 2.0 = instant recovery, 0.5 = very slow.")
            .onChange(v -> { stats.shieldRecoverySpeed = v; markDirty("Shield recovery"); });
    }

    private List<EditorSection> getSpecialSections() {
        return withEhp(List.of(
            new ToggleSectionAdapter(thornsToggle),
            new SliderSectionAdapter(thornsPercentSlider)
        ));
    }

    private List<EditorSection> getShieldSections() {
        return withEhp(List.of(
            new ToggleSectionAdapter(shieldReflectToggle),
            new SliderSectionAdapter(shieldBlockStrengthSlider),
            new SliderSectionAdapter(shieldRecoverySlider)
        ));
    }

    // ═══════════════════════════════════════════════════════════════
    // SHIELD VISUAL TAB (Prismatic Integration)
    // ═══════════════════════════════════════════════════════════════

    private void createShieldVisualComponents() {
        SourceBadge.Source source = determineSource();

        shieldOpacitySlider = new EditorSlider("shieldOpacity", "Shield Opacity", 0.1f, 1.0f, stats.shieldOpacity)
            .step(0.05f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.NEUTRAL)
            .showInput(true)
            .source(source)
            .info("Base transparency of the energy shield. 1.0 = fully opaque, 0.1 = barely visible.")
            .onChange(v -> { stats.shieldOpacity = v; markDirty("Shield opacity"); });

        shieldGlowToggle = new EditorToggle("shieldGlow", "Edge Glow", stats.shieldGlowEnabled)
            .source(source)
            .tooltip("Enable Fresnel edge glow effect for a sci-fi look")
            .onChange(v -> { stats.shieldGlowEnabled = v; markDirty("Shield glow"); });

        shieldGlowIntensitySlider = new EditorSlider("shieldGlowInt", "Glow Intensity", 0f, 2.0f, stats.shieldGlowIntensity)
            .step(0.1f)
            .format("%.1f")
            .suffix("x")
            .trackColor(UIConstants.SliderColors.SPECIAL)
            .showInput(true)
            .source(source)
            .info("Strength of the edge glow effect. Higher = brighter edges.")
            .onChange(v -> { stats.shieldGlowIntensity = v; markDirty("Glow intensity"); });

        shieldNoiseIntensitySlider = new EditorSlider("shieldNoise", "Energy Intensity", 0f, 0.5f, stats.shieldNoiseIntensity)
            .step(0.05f)
            .format("%.2f")
            .suffix("x")
            .trackColor(UIConstants.SliderColors.SPECIAL)
            .showInput(true)
            .source(source)
            .info("Animated energy field noise pattern intensity. 0 = smooth, 0.5 = very turbulent.")
            .onChange(v -> { stats.shieldNoiseIntensity = v; markDirty("Energy intensity"); });

        shieldPulseSpeedSlider = new EditorSlider("shieldPulse", "Animation Speed", 0.5f, 2.0f, stats.shieldPulseSpeed)
            .step(0.1f)
            .format("%.1f")
            .suffix("x")
            .trackColor(UIConstants.SliderColors.SPEED)
            .showInput(true)
            .source(source)
            .info("Speed of shield animation effects. 1.0 = normal, 2.0 = double speed.")
            .onChange(v -> { stats.shieldPulseSpeed = v; markDirty("Animation speed"); });
    }

    private List<EditorSection> getShieldVisualSections() {
        return withEhp(List.of(
            new SliderSectionAdapter(shieldOpacitySlider),
            new ToggleSectionAdapter(shieldGlowToggle),
            new SliderSectionAdapter(shieldGlowIntensitySlider),
            new SliderSectionAdapter(shieldNoiseIntensitySlider),
            new SliderSectionAdapter(shieldPulseSpeedSlider)
        ));
    }

    // ═══════════════════════════════════════════════════════════════
    // SHIELD DEFLECTION TAB
    // ═══════════════════════════════════════════════════════════════

    private void createShieldDeflectionComponents() {
        SourceBadge.Source source = determineSource();

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
            .onChange(v -> { stats.shieldDeflectionSpread = (float) Math.toRadians(v); markDirty("Deflection spread"); });

        shieldDeflectToOwnerToggle = new EditorToggle("deflectReturn", "Return to Sender", stats.shieldDeflectToOwner)
            .source(source)
            .tooltip("Deflect projectiles back toward the original shooter")
            .onChange(v -> { stats.shieldDeflectToOwner = v; markDirty("Return to sender"); });

        shieldDeflectSpeedMultSlider = new EditorSlider("deflectSpeed", "Deflect Speed", 0.5f, 1.5f, stats.shieldDeflectSpeedMult)
            .step(0.05f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.SPEED)
            .showInput(true)
            .source(source)
            .info("Projectile speed after deflection. 100% = same speed, 50% = half speed, 150% = faster.")
            .onChange(v -> { stats.shieldDeflectSpeedMult = v; markDirty("Deflect speed"); });
    }

    private List<EditorSection> getShieldDeflectionSections() {
        return withEhp(List.of(
            new SliderSectionAdapter(shieldDeflectionSpreadSlider),
            new ToggleSectionAdapter(shieldDeflectToOwnerToggle),
            new SliderSectionAdapter(shieldDeflectSpeedMultSlider)
        ));
    }

    // ═══════════════════════════════════════════════════════════════
    // SHIELD SHATTER TAB
    // ═══════════════════════════════════════════════════════════════

    private void createShieldShatterComponents() {
        SourceBadge.Source source = determineSource();

        shieldShatterThresholdSlider = new EditorSlider("shatterThresh", "Shatter Threshold", 5f, 50f, stats.shieldShatterThreshold)
            .step(1f)
            .format("%.0f")
            .suffix(" dmg")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .source(source)
            .info("Damage required in a single hit to break the shield. Higher = more durable.")
            .onChange(v -> { stats.shieldShatterThreshold = v; markDirty("Shatter threshold"); });

        shieldAutoRegenerateToggle = new EditorToggle("autoRegen", "Auto Regenerate", stats.shieldAutoRegenerate)
            .source(source)
            .tooltip("Shield automatically regenerates after being shattered")
            .onChange(v -> { stats.shieldAutoRegenerate = v; markDirty("Auto regenerate"); });

        shieldRegenDelaySlider = new EditorSlider("regenDelay", "Regen Delay", 1f, 10f, stats.shieldRegenDelay)
            .step(0.5f)
            .format("%.1f")
            .suffix("s")
            .trackColor(UIConstants.SliderColors.SPEED)
            .showInput(true)
            .source(source)
            .info("Seconds before shield starts regenerating after being shattered.")
            .onChange(v -> { stats.shieldRegenDelay = v; markDirty("Regen delay"); });
    }

    private List<EditorSection> getShieldShatterSections() {
        return withEhp(List.of(
            new SliderSectionAdapter(shieldShatterThresholdSlider),
            new ToggleSectionAdapter(shieldAutoRegenerateToggle),
            new SliderSectionAdapter(shieldRegenDelaySlider)
        ));
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
        CompoundTag statsTag = new CompoundTag();
        CompoundTag armorStats = new CompoundTag();
        stats.save(armorStats);
        statsTag.put(NBT_KEY, Objects.requireNonNull(armorStats.copy()));
        statsTag.put("armor_stats_component", Objects.requireNonNull(armorStats));

        // Slot is filled by ItemEditorScreen when sending; default to -1 here
        return new ArmorStatsPayloadV2(Objects.requireNonNull(item, "item cannot be null"), statsTag, isGlobal, -1);
    }

    @Override
    public void applyPreview() {
        // Create a preview copy and attach CustomData to the copy only
        try {
            ItemStack copy = item.copy();
            // Leverage config manager to set both component and custom data
            ArmorConfigManager.setSpecificStats(copy, stats.copy());
            setPreviewItem(copy);
        } catch (Exception ignored) {
            clearPreview();
        }
    }

    @Override
    public void resetToOriginal() {
        stats = originalStats.copy();
        updateComponentsFromStats();
        clearDirty();
    }

    // ═══════════════════════════════════════════════════════════════
    // DIRTY TRACKING (Override to update badges)
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void markDirty(String changeDescription) {
        super.markDirty(changeDescription);
        // Update source badges whenever a change is made
        updateSourceBadges();
    }

    @Override
    public void clearDirty() {
        super.clearDirty();
        // Reset source badges to original state
        updateSourceBadges();
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════

    public ArmorStats getStats() {
        return stats;
    }

    public ArmorStats getOriginalStats() {
        return originalStats;
    }

    @Override
    public boolean hasPendingDiff() {
        return !statsEquals(stats, originalStats);
    }

    private boolean statsEquals(ArmorStats a, ArmorStats b) {
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
    // SECTION ADAPTERS
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
            float ehp = computeEhp();
            String text = String.format("EHP: %.1fx", ehp);
            int textWidth = font.width(Objects.requireNonNull(text, "text"));
            int x = bounds.x() + (bounds.width() - textWidth) / 2;
            int y = bounds.y() + TEXT_OFFSET_Y;
            graphics.drawString(font, text, x, y, UIConstants.Text.VALUE(), false);
        }
    }
}
