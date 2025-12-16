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
import com.frenkvs.devmod.ui.editor.core.EditorCache;
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
        // Load existing armor stats from item NBT
        loadStatsFromItem();
        originalStats = stats.copy();

        // Update all components to reflect current stats
        updateComponentsFromStats();
    }

    private void loadStatsFromItem() {
        // Prefer component storage; fall back to legacy CustomData
        CompoundTag componentTag = null;
        try {
            componentTag = item.get(Objects.requireNonNull(ArmorComponents.ARMOR_STATS.get(), "armor component type"));
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
            return;
        }

        CustomData customData = item.getOrDefault(
            customDataType,
            Objects.requireNonNull(CustomData.EMPTY, "CustomData.EMPTY cannot be null")
        );
        CompoundTag tag = Objects.requireNonNull(customData.copyTag(), "custom data tag cannot be null");

        if (tag.contains(NBT_KEY)) {
            stats = ArmorStats.load(tag.getCompound(NBT_KEY));
        } else {
            stats = new ArmorStats();
            applyVanillaDefaults(stats);
        }
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
                safeMods(item.getOrDefault(attrType, NONNULL_EMPTY)),
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

                if (attribute == net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) {
                    armor += mod.amount();
                } else if (attribute == net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS) {
                    toughness += mod.amount();
                } else if (attribute == net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE) {
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

        // Add tabs
        addTab(ModuleTab.of("reduction", "Reduction", this::getDamageReductionSections));
        addTab(ModuleTab.of("stats", "Stats", this::getVanillaStatsSections));
        addTab(ModuleTab.of("special", "Special", this::getSpecialSections));
        if (variant == ArmorVariant.SHIELD) {
            addTab(ModuleTab.of("shield", "Shield", this::getShieldSections));
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

    private float calculateEHP() {
        float totalReduction = stats.physicalReduction;
        float cappedReduction = Math.min(totalReduction, 0.8f);
        return 1f / (1f - cappedReduction);
    }

    // ═══════════════════════════════════════════════════════════════
    // DAMAGE REDUCTION TAB
    // ═══════════════════════════════════════════════════════════════

    private void createDamageReductionComponents() {
        physicalReductionSlider = new EditorSlider("physRed", "Physical Reduction", 0f, 80f, 0f)
            .step(1f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.DEFENSE)
            .onChange(v -> { stats.physicalReduction = v / 100f; markDirty("Physical reduction"); });

        fireReductionSlider = new EditorSlider("fireRed", "Fire Reduction", 0f, 80f, 0f)
            .step(1f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .onChange(v -> { stats.fireReduction = v / 100f; markDirty("Fire reduction"); });

        magicReductionSlider = new EditorSlider("magicRed", "Magic Reduction", 0f, 80f, 0f)
            .step(1f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.SPECIAL)
            .onChange(v -> { stats.magicReduction = v / 100f; markDirty("Magic reduction"); });

        explosionReductionSlider = new EditorSlider("explRed", "Explosion Reduction", 0f, 80f, 0f)
            .step(1f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .onChange(v -> { stats.explosionReduction = v / 100f; markDirty("Explosion reduction"); });

        projectileReductionSlider = new EditorSlider("projRed", "Projectile Reduction", 0f, 80f, 0f)
            .step(1f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.DEFENSE)
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
        armorBonusSlider = new EditorSlider("armorBon", "Armor Bonus", 0f, 30f, 0f)
            .step(1f)
            .format("+%.0f")
            .trackColor(UIConstants.SliderColors.DEFENSE)
            .onChange(v -> { stats.armorBonus = v; markDirty("Armor bonus"); });

        toughnessBonusSlider = new EditorSlider("toughBon", "Toughness Bonus", 0f, 20f, 0f)
            .step(0.5f)
            .format("+%.1f")
            .trackColor(UIConstants.SliderColors.DEFENSE)
            .onChange(v -> { stats.toughnessBonus = v; markDirty("Toughness bonus"); });

        knockbackResistanceSlider = new EditorSlider("kbRes", "Knockback Resistance", 0f, 100f, 0f)
            .step(5f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.NEUTRAL)
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
        thornsToggle = new EditorToggle("thorns", "Thorns Reflect", false)
            .onChange(v -> { stats.thornsReflect = v; markDirty("Thorns enabled"); });

        thornsPercentSlider = new EditorSlider("thornsPct", "Thorns Damage", 0f, 50f, 0f)
            .step(1f)
            .format("%.0f")
            .suffix("%")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .onChange(v -> { stats.thornsPercent = v / 100f; markDirty("Thorns damage"); });
    }

    private void createShieldComponents() {
        shieldReflectToggle = new EditorToggle("shieldReflect", "Reflect Projectiles", stats.shieldReflectProjectiles)
            .onChange(v -> { stats.shieldReflectProjectiles = v; markDirty("Shield reflect projectiles"); });
        shieldBlockStrengthSlider = new EditorSlider("shieldBlock", "Block Strength", 0f, 1.0f, stats.shieldBlockStrength)
            .step(0.05f)
            .format("%.2f")
            .trackColor(UIConstants.SliderColors.DEFENSE)
            .onChange(v -> { stats.shieldBlockStrength = v; markDirty("Shield block strength"); });
        shieldRecoverySlider = new EditorSlider("shieldRecovery", "Recovery Speed", 0f, 2.0f, stats.shieldRecoverySpeed)
            .step(0.05f)
            .format("%.2f")
            .trackColor(UIConstants.SliderColors.SPEED)
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
            && a.shieldReflectProjectiles == b.shieldReflectProjectiles;
    }

    // ═══════════════════════════════════════════════════════════════
    // SECTION ADAPTERS
    // ═══════════════════════════════════════════════════════════════

    private class EhpPreviewSection implements EditorSection.CustomSection {
        @Override public String getId() { return "ehpPreview"; }
        @Override public String getLabel() { return "EHP Preview"; }
        @Override public int getHeight() { return UIConstants.Spacing.LG + 14; }

        @Override
        public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
            var font = Objects.requireNonNull(Minecraft.getInstance().font, "font cannot be null");
            float ehp = computeEhp();
            String text = String.format("EHP: %.1fx", ehp);
            int textWidth = font.width(Objects.requireNonNull(text, "text"));
            int x = bounds.x() + (bounds.width() - textWidth) / 2;
            int y = bounds.y() + UIConstants.Spacing.SM;
            graphics.drawString(font, text, x, y, UIConstants.Text.VALUE, false);
        }
    }

    /**
     * Adapts EditorSlider to EditorSection.SliderSection interface.
     */
    private static class SliderSectionAdapter implements EditorSection.SliderSection {
        private final EditorSlider slider;

        SliderSectionAdapter(EditorSlider slider) {
            this.slider = slider;
        }

        @Override public String getId() { return slider.getId(); }
        @Override public String getLabel() { return slider.getLabel(); }
        @Override public int getHeight() { return slider.calculateHeight(); }

        @Override
        public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
            slider.render(graphics, bounds.x(), bounds.y(), bounds.width(), mouseX, mouseY);
        }

        @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return slider.mouseClicked(mouseX, mouseY, button);
        }
        @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
            return slider.mouseReleased(mouseX, mouseY, button);
        }
        @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            return slider.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return slider.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override public float getValue() { return slider.getValue(); }
        @Override public void setValue(float value) { slider.setValue(value); }
        @Override public float getMin() { return slider.getMin(); }
        @Override public float getMax() { return slider.getMax(); }
        @Override public float getStep() { return slider.getStep(); }
        @Override public String getFormat() { return "%.2f"; }
        @Override public int getColor() { return UIConstants.SliderColors.NEUTRAL; }
        @Override public boolean isDragging() { return slider.isDragging(); }
        @Override public void setDragging(boolean dragging) { }
    }

    /**
     * Adapts EditorToggle to EditorSection.ToggleSection interface.
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

        @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return toggle.mouseClicked(mouseX, mouseY, button);
        }
        @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return toggle.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override public boolean getValue() { return toggle.getValue(); }
        @Override public void setValue(boolean value) { toggle.setValue(value); }
    }
}
