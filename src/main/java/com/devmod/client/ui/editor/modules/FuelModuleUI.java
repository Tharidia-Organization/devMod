package com.devmod.client.ui.editor.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import com.devmod.client.ui.editor.EditorSection;
import com.devmod.client.ui.editor.components.EditorSlider;
import com.devmod.client.ui.editor.components.EditorToggle;
import com.devmod.client.ui.editor.components.SourceBadge;
import com.devmod.client.ui.editor.core.UIConstants;
import com.devmod.client.ui.editor.sections.SimpleHeaderSection;
import com.devmod.client.ui.editor.sections.SliderSectionAdapter;
import com.devmod.client.ui.editor.sections.TextNoteSection;
import com.devmod.client.ui.editor.sections.ToggleSectionAdapter;
import com.devmod.config.FuelConfigManager;
import com.devmod.stats.FuelStats;

/**
 * UI components and section builders for FuelModule.
 */
public class FuelModuleUI {

    private final FuelModule module;
    private final FuelModuleCore core;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Burn Time Tab
    // ═══════════════════════════════════════════════════════════════

    EditorSlider burnTimeSlider;
    EditorToggle overrideDefaultToggle;
    EditorSlider efficiencySlider;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Cook Time Tab
    // ═══════════════════════════════════════════════════════════════

    EditorToggle customCookTimesToggle;
    EditorSlider furnaceCookTimeSlider;
    EditorSlider blastFurnaceCookTimeSlider;
    EditorSlider smokerCookTimeSlider;
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
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .source(dataSource)
            .info("Burn time in ticks. Coal = 1600, Stick = 100, Coal Block = 16000. 20 ticks = 1 second.")
            .onChange(v -> { stats.burnTime = v.intValue(); module.markDirty("Burn time"); });

        overrideDefaultToggle = new EditorToggle("overrideDefault", "Override Vanilla", false)
            .source(dataSource)
            .onChange(v -> { stats.overrideDefault = v; module.markDirty("Override vanilla"); });

        efficiencySlider = new EditorSlider("efficiency", "Efficiency", 0.1f, 3.0f, 1.0f)
            .step(0.1f)
            .format("%.1f")
            .suffix("x")
            .trackColor(UIConstants.SliderColors.SPECIAL)
            .showInput(true)
            .source(dataSource)
            .info("Multiplier for burn time. 1.0 = normal, 2.0 = double burn time, 0.5 = half.")
            .onChange(v -> { stats.efficiencyMultiplier = v; module.markDirty("Efficiency"); });
    }

    public List<EditorSection> getBurnTimeSections() {
        List<EditorSection> sections = new ArrayList<>();
        sections.add(new SimpleHeaderSection("burn-header", "Burn Time Properties"));
        sections.add(new SliderSectionAdapter(burnTimeSlider));
        sections.add(new ToggleSectionAdapter(overrideDefaultToggle));
        sections.add(new SliderSectionAdapter(efficiencySlider));

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
            .onChange(v -> { stats.customCookTimesEnabled = v; module.markDirty("Custom cook times"); });

        furnaceCookTimeSlider = new EditorSlider("furnaceCook", "Furnace", 1, 1000, 200)
            .step(10)
            .format("%.0f")
            .suffix(" ticks")
            .trackColor(UIConstants.SliderColors.SPEED)
            .showInput(true)
            .source(dataSource)
            .info("Cook time in standard furnace. Default = 200 ticks (10 seconds).")
            .onChange(v -> { stats.furnaceCookTime = v.intValue(); module.markDirty("Furnace cook time"); });

        blastFurnaceCookTimeSlider = new EditorSlider("blastCook", "Blast Furnace", 1, 500, 100)
            .step(10)
            .format("%.0f")
            .suffix(" ticks")
            .trackColor(UIConstants.SliderColors.SPEED)
            .showInput(true)
            .source(dataSource)
            .info("Cook time in blast furnace. Default = 100 ticks (5 seconds, 2x faster).")
            .onChange(v -> { stats.blastFurnaceCookTime = v.intValue(); module.markDirty("Blast furnace cook time"); });

        smokerCookTimeSlider = new EditorSlider("smokerCook", "Smoker", 1, 500, 100)
            .step(10)
            .format("%.0f")
            .suffix(" ticks")
            .trackColor(UIConstants.SliderColors.SPEED)
            .showInput(true)
            .source(dataSource)
            .info("Cook time in smoker. Default = 100 ticks (5 seconds, 2x faster for food).")
            .onChange(v -> { stats.smokerCookTime = v.intValue(); module.markDirty("Smoker cook time"); });

        campfireCookTimeSlider = new EditorSlider("campfireCook", "Campfire", 1, 2000, 600)
            .step(20)
            .format("%.0f")
            .suffix(" ticks")
            .trackColor(UIConstants.SliderColors.SPEED)
            .showInput(true)
            .source(dataSource)
            .info("Cook time on campfire. Default = 600 ticks (30 seconds).")
            .onChange(v -> { stats.campfireCookTime = v.intValue(); module.markDirty("Campfire cook time"); });
    }

    public List<EditorSection> getCookTimeSections() {
        List<EditorSection> sections = new ArrayList<>();
        sections.add(new SimpleHeaderSection("cook-header", "Cook Time Properties"));
        sections.add(new ToggleSectionAdapter(customCookTimesToggle));
        sections.add(new SliderSectionAdapter(furnaceCookTimeSlider));
        sections.add(new SliderSectionAdapter(blastFurnaceCookTimeSlider));
        sections.add(new SliderSectionAdapter(smokerCookTimeSlider));
        sections.add(new SliderSectionAdapter(campfireCookTimeSlider));
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
            int vanillaBurn = FuelConfigManager.getVanillaBurnTime(item);
            sections.add(new TextNoteSection("debug-vanilla", "Vanilla Burn Time: " + vanillaBurn + " ticks"));

            FuelStats stats = core.getStats();
            FuelStats original = core.getOriginalStats();

            String burnChanged = stats.burnTime != original.burnTime ? " *" : "";
            sections.add(new TextNoteSection("debug-burn", "Burn Time: " + stats.burnTime + burnChanged));

            String effChanged = Math.abs(stats.efficiencyMultiplier - original.efficiencyMultiplier) > 0.001f ? " *" : "";
            sections.add(new TextNoteSection("debug-eff", String.format("Efficiency: %.1f%s", stats.efficiencyMultiplier, effChanged)));

            String effectiveBurn = "Effective: " + stats.getEffectiveBurnTime() + " ticks";
            sections.add(new TextNoteSection("debug-effective", effectiveBurn));

            String overrideChanged = stats.overrideDefault != original.overrideDefault ? " *" : "";
            sections.add(new TextNoteSection("debug-override", "Override: " + stats.overrideDefault + overrideChanged));

            sections.add(new TextNoteSection("debug-legend", "* = modified from original"));
        }

        return sections;
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
        if (burnTimeSlider != null) burnTimeSlider.setValue(stats.burnTime);
        if (overrideDefaultToggle != null) overrideDefaultToggle.setValue(stats.overrideDefault);
        if (efficiencySlider != null) efficiencySlider.setValue(stats.efficiencyMultiplier);

        // Cook time components
        if (customCookTimesToggle != null) customCookTimesToggle.setValue(stats.customCookTimesEnabled);
        if (furnaceCookTimeSlider != null) furnaceCookTimeSlider.setValue(stats.furnaceCookTime);
        if (blastFurnaceCookTimeSlider != null) blastFurnaceCookTimeSlider.setValue(stats.blastFurnaceCookTime);
        if (smokerCookTimeSlider != null) smokerCookTimeSlider.setValue(stats.smokerCookTime);
        if (campfireCookTimeSlider != null) campfireCookTimeSlider.setValue(stats.campfireCookTime);
    }
}
