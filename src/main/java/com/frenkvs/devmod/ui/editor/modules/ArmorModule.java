package com.frenkvs.devmod.ui.editor.modules;

import com.frenkvs.devmod.ArmorStats;
import com.frenkvs.devmod.network.ArmorStatsPayload;
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
import net.minecraft.world.item.component.CustomData;

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
    private ArmorStats stats = new ArmorStats();
    private ArmorStats originalStats = new ArmorStats();

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

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public ArmorModule() {
        super("armor", "Armor Editor");
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
        CustomData customData = item.getOrDefault(
            Objects.requireNonNull(DataComponents.CUSTOM_DATA, "CUSTOM_DATA component type cannot be null"),
            Objects.requireNonNull(CustomData.EMPTY, "CustomData.EMPTY cannot be null")
        );
        CompoundTag tag = customData.copyTag();

        if (tag.contains(NBT_KEY)) {
            stats = ArmorStats.load(tag.getCompound(NBT_KEY));
        } else {
            stats = new ArmorStats();
        }
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

        // Add tabs
        addTab(ModuleTab.of("reduction", "Reduction", this::getDamageReductionSections));
        addTab(ModuleTab.of("stats", "Stats", this::getVanillaStatsSections));
        addTab(ModuleTab.of("special", "Special", this::getSpecialSections));
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
        List<String> history = getRecentHistoryEntries(5);
        List<String> nbtLines = DebugInfoSection.formatNbtLines(getCustomDataTag(), 8);
        return List.of(new DebugInfoSection(info, comparisons, history, nbtLines, this::copyDebugInfo));
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
        ArmorStats actual = loadStatsFromTag(getCustomDataTag());
        comparisons.add(makeComparison("Physical Reduction (%)", actual.physicalReduction * 100, stats.physicalReduction * 100));
        comparisons.add(makeComparison("Fire Reduction (%)", actual.fireReduction * 100, stats.fireReduction * 100));
        comparisons.add(makeComparison("Magic Reduction (%)", actual.magicReduction * 100, stats.magicReduction * 100));
        comparisons.add(makeComparison("Explosion Reduction (%)", actual.explosionReduction * 100, stats.explosionReduction * 100));
        comparisons.add(makeComparison("Projectile Reduction (%)", actual.projectileReduction * 100, stats.projectileReduction * 100));

        comparisons.add(makeComparison("Armor Bonus", actual.armorBonus, stats.armorBonus));
        comparisons.add(makeComparison("Toughness Bonus", actual.toughnessBonus, stats.toughnessBonus));
        comparisons.add(makeComparison("Knockback Resist (%)", actual.knockbackResistance * 100, stats.knockbackResistance * 100));

        comparisons.add(makeComparison("Thorns (%)", actual.thornsPercent * 100, stats.thornsPercent * 100));
        comparisons.add(makeComparison("Thorns reflect", actual.thornsReflect ? 1 : 0, stats.thornsReflect ? 1 : 0));
        return comparisons;
    }

    private ArmorStats loadStatsFromTag(CompoundTag tag) {
        if (tag.contains(NBT_KEY)) {
            return ArmorStats.load(tag.getCompound(NBT_KEY));
        }
        return new ArmorStats();
    }

    private ValueComparison makeComparison(String label, double actualValue, double current) {
        boolean mismatch = Math.abs(actualValue - current) > 0.1e-3;
        return new ValueComparison(label, actualValue, current, actualValue, mismatch, mismatch);
    }

    private void copyDebugInfo() {
        ItemDebugInfo info = buildDebugInfo();
        List<ValueComparison> comparisons = buildValueComparisons();
        List<String> history = getRecentHistoryEntries(5);
        List<String> nbtLines = DebugInfoSection.formatNbtLines(getCustomDataTag(), 12);
        String payload = buildDebugClipboardText(info, comparisons, history, nbtLines);

        Minecraft mc = Minecraft.getInstance();
        if (mc.keyboardHandler != null) {
            mc.keyboardHandler.setClipboard(payload);
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
            String suffix = comp.hasMismatch() ? " [MISMATCH]" : comp.isModified() ? " [MOD]" : "";
            sb.append(String.format(Locale.US, "%s: exp %.2f srv %.2f cur %.2f%s\n",
                comp.attributeName(), comp.originalValue(), comp.serverValue(), comp.currentValue(), suffix));
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
        return List.of(
            new SliderSectionAdapter(physicalReductionSlider),
            new SliderSectionAdapter(fireReductionSlider),
            new SliderSectionAdapter(magicReductionSlider),
            new SliderSectionAdapter(explosionReductionSlider),
            new SliderSectionAdapter(projectileReductionSlider)
        );
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
        return List.of(
            new SliderSectionAdapter(armorBonusSlider),
            new SliderSectionAdapter(toughnessBonusSlider),
            new SliderSectionAdapter(knockbackResistanceSlider)
        );
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

    private List<EditorSection> getSpecialSections() {
        return List.of(
            new ToggleSectionAdapter(thornsToggle),
            new SliderSectionAdapter(thornsPercentSlider)
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDERING
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void renderContent(GuiGraphics graphics, ResponsiveLayout.Rect contentBounds, int mouseX, int mouseY) {
        int y = contentBounds.y() + UIConstants.Spacing.MD;
        int sliderWidth = layout != null ? layout.getSliderWidth() : 200;
        int x = contentBounds.x() + (contentBounds.width() - sliderWidth) / 2;

        // Render based on current tab
        switch (activeTabIndex) {
            case 0 -> renderDamageReductionTab(graphics, x, y, sliderWidth, mouseX, mouseY);
            case 1 -> renderVanillaStatsTab(graphics, x, y, sliderWidth, mouseX, mouseY);
            case 2 -> renderSpecialTab(graphics, x, y, sliderWidth, mouseX, mouseY);
        }

        // EHP preview at bottom
        renderEHPPreview(graphics, contentBounds, y + calculateCurrentTabHeight());
    }

    private void renderDamageReductionTab(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
        y += physicalReductionSlider.render(graphics, x, y, width, mouseX, mouseY) + UIConstants.Spacing.SM;
        y += fireReductionSlider.render(graphics, x, y, width, mouseX, mouseY) + UIConstants.Spacing.SM;
        y += magicReductionSlider.render(graphics, x, y, width, mouseX, mouseY) + UIConstants.Spacing.SM;
        y += explosionReductionSlider.render(graphics, x, y, width, mouseX, mouseY) + UIConstants.Spacing.SM;
        projectileReductionSlider.render(graphics, x, y, width, mouseX, mouseY);
    }

    private void renderVanillaStatsTab(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
        y += armorBonusSlider.render(graphics, x, y, width, mouseX, mouseY) + UIConstants.Spacing.SM;
        y += toughnessBonusSlider.render(graphics, x, y, width, mouseX, mouseY) + UIConstants.Spacing.SM;
        knockbackResistanceSlider.render(graphics, x, y, width, mouseX, mouseY);
    }

    private void renderSpecialTab(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
        y += thornsToggle.render(graphics, x, y, width, mouseX, mouseY) + UIConstants.Spacing.SM;

        // Only show thorns percent if thorns is enabled
        if (stats.thornsReflect) {
            thornsPercentSlider.render(graphics, x, y, width, mouseX, mouseY);
        }
    }

    private void renderEHPPreview(GuiGraphics graphics, ResponsiveLayout.Rect contentBounds, int startY) {
        var font = Objects.requireNonNull(Minecraft.getInstance().font, "font cannot be null");
        int y = startY + UIConstants.Spacing.LG;

        // Calculate effective health with caching
        float ehpMultiplier = EditorCache.getInstance().getOrCompute(
            EditorCache.Types.EHP,
            Objects.requireNonNull(item.toString()),
            this::calculateEHP
        );

        String ehpText = String.format("EHP: %.1fx", ehpMultiplier);
        int textWidth = font.width(Objects.requireNonNull(ehpText, "ehpText cannot be null"));
        int x = contentBounds.x() + (contentBounds.width() - textWidth) / 2;

        graphics.drawString(font, ehpText, x, y, UIConstants.Text.VALUE, false);
    }

    /**
     * Calculate effective health multiplier based on damage reductions.
     * EHP = 1 / (1 - reduction), capped at 80% reduction.
     */
    private float calculateEHP() {
        // Sum all reductions (physical is most common baseline)
        float totalReduction = stats.physicalReduction;
        // Cap at 80% to prevent invincibility
        float cappedReduction = Math.min(totalReduction, 0.8f);
        return 1f / (1f - cappedReduction);
    }

    private int calculateCurrentTabHeight() {
        return switch (activeTabIndex) {
            case 0 -> 5 * (physicalReductionSlider.calculateHeight() + UIConstants.Spacing.SM);
            case 1 -> 3 * (armorBonusSlider.calculateHeight() + UIConstants.Spacing.SM);
            case 2 -> thornsToggle.render(null, 0, 0, 0, 0, 0) +
                      (stats.thornsReflect ? thornsPercentSlider.calculateHeight() + UIConstants.Spacing.SM : 0);
            default -> 0;
        };
    }

    @Override
    public int calculateContentHeight() {
        int height = UIConstants.Spacing.MD;

        height += switch (activeTabIndex) {
            case 0 -> 5 * (physicalReductionSlider.calculateHeight() + UIConstants.Spacing.SM);
            case 1 -> 3 * (armorBonusSlider.calculateHeight() + UIConstants.Spacing.SM);
            case 2 -> 20 + UIConstants.Spacing.SM + // Toggle height approximation
                      (stats.thornsReflect ? thornsPercentSlider.calculateHeight() + UIConstants.Spacing.SM : 0);
            default -> 0;
        };

        // EHP preview
        height += UIConstants.Spacing.LG + 12;

        return height + UIConstants.Spacing.MD;
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════

    private List<EditorSlider> getCurrentTabSliders() {
        return switch (activeTabIndex) {
            case 0 -> List.of(physicalReductionSlider, fireReductionSlider, magicReductionSlider,
                             explosionReductionSlider, projectileReductionSlider);
            case 1 -> List.of(armorBonusSlider, toughnessBonusSlider, knockbackResistanceSlider);
            case 2 -> stats.thornsReflect ? List.of(thornsPercentSlider) : List.of();
            default -> List.of();
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle toggle in special tab
        if (activeTabIndex == 2 && thornsToggle.mouseClicked(mouseX, mouseY, button)) {
            saveUndoState();
            return true;
        }

        for (EditorSlider slider : getCurrentTabSliders()) {
            if (slider.mouseClicked(mouseX, mouseY, button)) {
                saveUndoState();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (EditorSlider slider : getCurrentTabSliders()) {
            if (slider.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        for (EditorSlider slider : getCurrentTabSliders()) {
            if (slider.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        for (EditorSlider slider : getCurrentTabSliders()) {
            if (slider.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                saveUndoState();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (activeTabIndex == 2 && thornsToggle.keyPressed(keyCode, scanCode, modifiers)) {
            saveUndoState();
            return true;
        }

        for (EditorSlider slider : getCurrentTabSliders()) {
            if (slider.keyPressed(keyCode, scanCode, modifiers)) {
                saveUndoState();
                return true;
            }
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    // NETWORK
    // ═══════════════════════════════════════════════════════════════

    @Override
    public CustomPacketPayload buildPayload(boolean isGlobal) {
        CompoundTag statsTag = new CompoundTag();
        stats.save(statsTag);

        return new ArmorStatsPayload(Objects.requireNonNull(item), statsTag, isGlobal);
    }

    @Override
    public void applyPreview() {
        // Apply stats to item for preview (client-side only)
        CustomData customData = item.getOrDefault(
            Objects.requireNonNull(DataComponents.CUSTOM_DATA, "CUSTOM_DATA component type cannot be null"),
            Objects.requireNonNull(CustomData.EMPTY, "CustomData.EMPTY cannot be null")
        );
        CompoundTag tag = customData.copyTag();

        CompoundTag statsTag = new CompoundTag();
        stats.save(statsTag);
        tag.put(NBT_KEY, statsTag);

        item.set(
            Objects.requireNonNull(DataComponents.CUSTOM_DATA, "CUSTOM_DATA component type cannot be null"),
            CustomData.of(tag)
        );
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
            && a.thornsReflect == b.thornsReflect;
    }

    // ═══════════════════════════════════════════════════════════════
    // SECTION ADAPTERS
    // ═══════════════════════════════════════════════════════════════

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
