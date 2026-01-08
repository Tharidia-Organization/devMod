package com.devmod.client.ui.editor.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import com.devmod.client.ui.editor.EditorSection;
import com.devmod.client.ui.editor.components.EditorSlider;
import com.devmod.client.ui.editor.components.EditorToggle;
import com.devmod.client.ui.editor.components.SourceBadge;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.editor.sections.ModuleSummarySection;
import com.devmod.client.ui.editor.sections.SimpleHeaderSection;
import com.devmod.client.ui.editor.sections.SliderSectionAdapter;
import com.devmod.client.ui.editor.sections.TextNoteSection;
import com.devmod.client.ui.editor.sections.ToggleSectionAdapter;
import com.devmod.config.handler.impl.FuelConfigHandler;
import com.devmod.stats.FuelStats;

public class FuelModuleUI {

    private final FuelModule module;
    private final FuelModuleCore core;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Burn Time Tab
    // ═══════════════════════════════════════════════════════════════

    @Nullable
    EditorSlider burnTimeSlider;
    @Nullable
    EditorToggle overrideDefaultToggle;
    @Nullable
    EditorSlider efficiencySlider;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Cook Time Tab
    // ═══════════════════════════════════════════════════════════════

    @Nullable
    EditorToggle customCookTimesToggle;
    @Nullable
    EditorSlider furnaceCookTimeSlider;
    @Nullable
    EditorSlider blastFurnaceCookTimeSlider;
    @Nullable
    EditorSlider smokerCookTimeSlider;
    @Nullable
    EditorSlider campfireCookTimeSlider;

    public FuelModuleUI(FuelModule module, FuelModuleCore core) {
        this.module = module;
        this.core = core;
    }

    // ═══════════════════════════════════════════════════════════════
    // CREATE ALL COMPONENTS
    // ═══════════════════════════════════════════════════════════════

    public void createAllComponents(SourceBadge.Source dataSource) {
        createBurnTimeComponents(dataSource);
        createCookTimeComponents(dataSource);
    }

    // ═══════════════════════════════════════════════════════════════
    // BURN TIME TAB
    // ═══════════════════════════════════════════════════════════════

    private void createBurnTimeComponents(SourceBadge.Source dataSource) {
        FuelStats stats = core.getStats();

        burnTimeSlider = new EditorSlider("burnTime", "Burn Time", 0, 32000, 0)
            .step(10)
            .format("%.0f")
            .suffix(" ticks")
            .trackColor(DesignTokens.SliderColors.DAMAGE)
            .showInput(true)
            .source(dataSource)
            .info("Burn time in ticks. Coal = 1600, Stick = 100, Coal Block = 16000. 20 ticks = 1 second.")
            .onChange(v -> { stats.setBurnTime(v.intValue()); module.markDirty("Burn time"); });

        overrideDefaultToggle = new EditorToggle("overrideDefault", "Override Vanilla", false)
            .source(dataSource)
            .onChange(v -> { stats.setOverrideDefault(v); module.markDirty("Override vanilla"); });

        efficiencySlider = new EditorSlider("efficiency", "Efficiency", 0.1f, 3.0f, 1.0f)
            .step(0.1f)
            .format("%.1f")
            .suffix("x")
            .trackColor(DesignTokens.SliderColors.SPECIAL)
            .showInput(true)
            .source(dataSource)
            .info("Multiplier for burn time. 1.0 = normal, 2.0 = double burn time, 0.5 = half.")
            .onChange(v -> { stats.setEfficiencyMultiplier(v); module.markDirty("Efficiency"); });
    }

    public List<EditorSection> getBurnTimeSections() {
        EditorSlider burnTime = Objects.requireNonNull(burnTimeSlider, "burnTimeSlider");
        EditorToggle overrideDefault = Objects.requireNonNull(overrideDefaultToggle, "overrideDefaultToggle");
        EditorSlider efficiency = Objects.requireNonNull(efficiencySlider, "efficiencySlider");
        List<EditorSection> sections = new ArrayList<>();
        sections.add(new SimpleHeaderSection("burn-header", "Burn Time Properties"));
        sections.add(new SliderSectionAdapter(burnTime));
        sections.add(new ToggleSectionAdapter(overrideDefault));
        sections.add(new SliderSectionAdapter(efficiency));

        // Show effective burn time
        FuelStats stats = core.getStats();
        int effectiveBurn = stats.getEffectiveBurnTime();
        float itemsSmeltable = stats.getItemsSmeltable();
        String effNote = String.format("Effective: %d ticks (%.1f items)", effectiveBurn, itemsSmeltable);
        sections.add(new TextNoteSection("burn-effective", effNote));

        sections.add(new TextNoteSection("burn-note", "Enable 'Override Vanilla' to apply custom burn time."));
        return sections;
    }

    // ═══════════════════════════════════════════════════════════════
    // COOK TIME TAB
    // ═══════════════════════════════════════════════════════════════

    private void createCookTimeComponents(SourceBadge.Source dataSource) {
        FuelStats stats = core.getStats();

        customCookTimesToggle = new EditorToggle("customCookTimes", "Enable Custom Cook Times", false)
            .source(dataSource)
            .onChange(v -> { stats.setCustomCookTimesEnabled(v); module.markDirty("Custom cook times"); });

        furnaceCookTimeSlider = new EditorSlider("furnaceCook", "Furnace", 1, 1000, 200)
            .step(10)
            .format("%.0f")
            .suffix(" ticks")
            .trackColor(DesignTokens.SliderColors.SPEED)
            .showInput(true)
            .source(dataSource)
            .info("Cook time in standard furnace. Default = 200 ticks (10 seconds).")
            .onChange(v -> { stats.setFurnaceCookTime(v.intValue()); module.markDirty("Furnace cook time"); });

        blastFurnaceCookTimeSlider = new EditorSlider("blastCook", "Blast Furnace", 1, 500, 100)
            .step(10)
            .format("%.0f")
            .suffix(" ticks")
            .trackColor(DesignTokens.SliderColors.SPEED)
            .showInput(true)
            .source(dataSource)
            .info("Cook time in blast furnace. Default = 100 ticks (5 seconds, 2x faster).")
            .onChange(v -> { stats.setBlastFurnaceCookTime(v.intValue()); module.markDirty("Blast furnace cook time"); });

        smokerCookTimeSlider = new EditorSlider("smokerCook", "Smoker", 1, 500, 100)
            .step(10)
            .format("%.0f")
            .suffix(" ticks")
            .trackColor(DesignTokens.SliderColors.SPEED)
            .showInput(true)
            .source(dataSource)
            .info("Cook time in smoker. Default = 100 ticks (5 seconds, 2x faster for food).")
            .onChange(v -> { stats.setSmokerCookTime(v.intValue()); module.markDirty("Smoker cook time"); });

        campfireCookTimeSlider = new EditorSlider("campfireCook", "Campfire", 1, 2000, 600)
            .step(20)
            .format("%.0f")
            .suffix(" ticks")
            .trackColor(DesignTokens.SliderColors.SPEED)
            .showInput(true)
            .source(dataSource)
            .info("Cook time on campfire. Default = 600 ticks (30 seconds).")
            .onChange(v -> { stats.setCampfireCookTime(v.intValue()); module.markDirty("Campfire cook time"); });
    }

    public List<EditorSection> getCookTimeSections() {
        EditorToggle customCookTimes = Objects.requireNonNull(customCookTimesToggle, "customCookTimesToggle");
        EditorSlider furnaceCook = Objects.requireNonNull(furnaceCookTimeSlider, "furnaceCookTimeSlider");
        EditorSlider blastCook = Objects.requireNonNull(blastFurnaceCookTimeSlider, "blastFurnaceCookTimeSlider");
        EditorSlider smokerCook = Objects.requireNonNull(smokerCookTimeSlider, "smokerCookTimeSlider");
        EditorSlider campfireCook = Objects.requireNonNull(campfireCookTimeSlider, "campfireCookTimeSlider");
        List<EditorSection> sections = new ArrayList<>();
        sections.add(new SimpleHeaderSection("cook-header", "Cook Time Properties"));
        sections.add(new ToggleSectionAdapter(customCookTimes));
        sections.add(new SliderSectionAdapter(furnaceCook));
        sections.add(new SliderSectionAdapter(blastCook));
        sections.add(new SliderSectionAdapter(smokerCook));
        sections.add(new SliderSectionAdapter(campfireCook));
        sections.add(new TextNoteSection("cook-note", "Cook times require recipe integration. Enable 'Custom Cook Times' to apply."));
        return sections;
    }

    // ═══════════════════════════════════════════════════════════════
    // DEBUG TAB
    // ═══════════════════════════════════════════════════════════════

    public List<EditorSection> getDebugSections(ItemStack item) {
        List<EditorSection> sections = new ArrayList<>();
        sections.add(new SimpleHeaderSection("debug-header", "Debug Information"));

        if (item != null && !item.isEmpty()) {
            String itemId = BuiltInRegistries.ITEM
                .getKey(Objects.requireNonNull(item.getItem(), "item"))
                .toString();
            sections.add(new TextNoteSection("debug-item", "Item: " + itemId));
            sections.add(new TextNoteSection("debug-source", "Data Source: " + core.getSourcePrefix()));

            // Vanilla burn time
            int vanillaBurn = FuelConfigHandler.getVanillaBurnTime(item);
            sections.add(new TextNoteSection("debug-vanilla", "Vanilla Burn Time: " + vanillaBurn + " ticks"));

            FuelStats stats = core.getStats();
            FuelStats original = core.getOriginalStats();

            String burnChanged = stats.getBurnTime() != original.getBurnTime() ? " *" : "";
            sections.add(new TextNoteSection("debug-burn", "Burn Time: " + stats.getBurnTime() + burnChanged));

            String effChanged = Math.abs(stats.getEfficiencyMultiplier() - original.getEfficiencyMultiplier()) > 0.001f ? " *" : "";
            sections.add(new TextNoteSection("debug-eff", String.format("Efficiency: %.1f%s", stats.getEfficiencyMultiplier(), effChanged)));

            String effectiveBurn = "Effective: " + stats.getEffectiveBurnTime() + " ticks";
            sections.add(new TextNoteSection("debug-effective", effectiveBurn));

            String overrideChanged = stats.isOverrideDefault() != original.isOverrideDefault() ? " *" : "";
            sections.add(new TextNoteSection("debug-override", "Override: " + stats.isOverrideDefault() + overrideChanged));

            sections.add(new TextNoteSection("debug-legend", "* = modified from original"));
        }

        return sections;
    }

    // ═══════════════════════════════════════════════════════════════
    // SUMMARY TAB (uses ModuleSummarySection with 2-param StatEntry)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get summary sections using ModuleSummarySection with 2-param StatEntry constructor.
     * This demonstrates usage of the simplest StatEntry pattern with just label and value.
     */
    public List<EditorSection> getSummarySections() {
        FuelStats stats = core.getStats();

        // Use 2-param StatEntry constructor for simplest usage pattern
        ModuleSummarySection.StatEntry burnEntry = new ModuleSummarySection.StatEntry(
            "Burn Time", stats.getBurnTime());
        ModuleSummarySection.StatEntry effEntry = new ModuleSummarySection.StatEntry(
            "Efficiency", stats.getEfficiencyMultiplier());

        // Build summary demonstrating various overloads
        ModuleSummarySection summary = ModuleSummarySection.builder("fuel-summary", "Fuel Summary")
            .accentColor(DesignTokens.Accent.ORANGE())
            .addStat(burnEntry)                                                 // StatEntry overload (2-param ctor)
            .addStat(effEntry)                                                  // StatEntry overload (2-param ctor)
            .addStat("Effective", stats.getEffectiveBurnTime(), "%.0f ticks")   // 3-param overload
            .addStat("Items", stats.getItemsSmeltable())                        // 2-param overload
            .addStat("Override", stats.isOverrideDefault() ? 1.0 : 0.0, stats.isOverrideDefault() ? "Yes" : "No")
            .build();

        return List.of(summary);
    }

    // ═══════════════════════════════════════════════════════════════
    // COMPONENT SYNC
    // ═══════════════════════════════════════════════════════════════

    /**
     * Update all components to reflect current stats values.
     */
    public void updateComponentsFromStats() {
        FuelStats stats = core.getStats();

        // Burn time components
        if (burnTimeSlider != null) burnTimeSlider.setValue(stats.getBurnTime());
        if (overrideDefaultToggle != null) overrideDefaultToggle.setValue(stats.isOverrideDefault());
        if (efficiencySlider != null) efficiencySlider.setValue(stats.getEfficiencyMultiplier());

        // Cook time components
        if (customCookTimesToggle != null) customCookTimesToggle.setValue(stats.isCustomCookTimesEnabled());
        if (furnaceCookTimeSlider != null) furnaceCookTimeSlider.setValue(stats.getFurnaceCookTime());
        if (blastFurnaceCookTimeSlider != null) blastFurnaceCookTimeSlider.setValue(stats.getBlastFurnaceCookTime());
        if (smokerCookTimeSlider != null) smokerCookTimeSlider.setValue(stats.getSmokerCookTime());
        if (campfireCookTimeSlider != null) campfireCookTimeSlider.setValue(stats.getCampfireCookTime());
    }
}
