package com.devmod.client.ui.editor.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
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
import com.devmod.stats.FoodStats;

/**
 * UI components and section builders for FoodModule.
 */
public class FoodModuleUI {

    private final FoodModule module;
    private final FoodModuleCore core;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Nutrition Tab
    // ═══════════════════════════════════════════════════════════════

    EditorSlider nutritionSlider;
    EditorSlider saturationSlider;
    EditorSlider consumptionTimeSlider;
    EditorToggle canAlwaysEatToggle;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Properties Tab
    // ═══════════════════════════════════════════════════════════════

    EditorToggle isMeatToggle;
    EditorToggle isFastFoodToggle;

    public FoodModuleUI(FoodModule module, FoodModuleCore core) {
        this.module = module;
        this.core = core;
    }

    // ═══════════════════════════════════════════════════════════════
    // CREATE ALL COMPONENTS
    // ═══════════════════════════════════════════════════════════════

    public void createAllComponents(SourceBadge.Source dataSource) {
        createNutritionComponents(dataSource);
        createPropertiesComponents(dataSource);
    }

    // ═══════════════════════════════════════════════════════════════
    // NUTRITION TAB
    // ═══════════════════════════════════════════════════════════════

    private void createNutritionComponents(SourceBadge.Source dataSource) {
        FoodStats stats = core.getStats();

        nutritionSlider = new EditorSlider("nutrition", "Nutrition", 0, 20, 0)
            .step(1)
            .format("%.0f")
            .suffix(" pts")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .source(dataSource)
            .info("Hunger points restored. Each point = half drumstick. Steak = 8, Golden Apple = 4.")
            .onChange(v -> { stats.nutrition = v.intValue(); module.markDirty("Nutrition"); });

        saturationSlider = new EditorSlider("saturation", "Saturation", 0.0f, 2.0f, 0.0f)
            .step(0.1f)
            .format("%.1f")
            .trackColor(UIConstants.SliderColors.SPECIAL)
            .showInput(true)
            .source(dataSource)
            .info("Saturation modifier. Actual saturation = nutrition * modifier * 2. Golden Apple = 1.2, Steak = 0.8.")
            .onChange(v -> { stats.saturation = v; module.markDirty("Saturation"); });

        consumptionTimeSlider = new EditorSlider("consumeTime", "Consumption Time", 1, 100, 32)
            .step(1)
            .format("%.0f")
            .suffix(" ticks")
            .trackColor(UIConstants.SliderColors.SPEED)
            .showInput(true)
            .source(dataSource)
            .info("Time to eat in ticks. Default food = 32 (1.6s), Dried Kelp = 16. 20 ticks = 1 second.")
            .onChange(v -> { stats.consumptionTime = v.intValue(); module.markDirty("Consumption time"); });

        canAlwaysEatToggle = new EditorToggle("canAlwaysEat", "Can Always Eat", false)
            .source(dataSource)
            .onChange(v -> { stats.canAlwaysEat = v; module.markDirty("Can always eat"); });
    }

    public List<EditorSection> getNutritionSections() {
        List<EditorSection> sections = new ArrayList<>();
        sections.add(new SimpleHeaderSection("nutrition-header", "Nutrition Properties"));
        sections.add(new SliderSectionAdapter(nutritionSlider));
        sections.add(new SliderSectionAdapter(saturationSlider));
        sections.add(new SliderSectionAdapter(consumptionTimeSlider));
        sections.add(new ToggleSectionAdapter(canAlwaysEatToggle));
        sections.add(new TextNoteSection("nutrition-note", "Actual saturation = nutrition × modifier × 2."));
        return sections;
    }

    // ═══════════════════════════════════════════════════════════════
    // EFFECTS TAB
    // ═══════════════════════════════════════════════════════════════

    public List<EditorSection> getEffectsSections() {
        List<EditorSection> sections = new ArrayList<>();
        sections.add(new SimpleHeaderSection("effects-header", "Food Effects"));

        FoodStats stats = core.getStats();
        if (stats.effects.isEmpty()) {
            sections.add(new TextNoteSection("effects-empty", "No effects configured. Use commands to add effects."));
        } else {
            for (int i = 0; i < stats.effects.size(); i++) {
                FoodStats.FoodEffect effect = stats.effects.get(i);
                String info = String.format("%d. %s (Lvl %d, %d ticks, %.0f%%)",
                    i + 1, effect.effectId, effect.amplifier + 1, effect.duration, effect.probability * 100);
                sections.add(new TextNoteSection("effect-" + i, info));
            }
        }

        sections.add(new TextNoteSection("effects-note", "Effect editing UI coming soon. Use /devmod food addeffect for now."));
        return sections;
    }

    // ═══════════════════════════════════════════════════════════════
    // PROPERTIES TAB
    // ═══════════════════════════════════════════════════════════════

    private void createPropertiesComponents(SourceBadge.Source dataSource) {
        FoodStats stats = core.getStats();

        isMeatToggle = new EditorToggle("isMeat", "Is Meat", false)
            .source(dataSource)
            .onChange(v -> { stats.isMeat = v; module.markDirty("Is meat"); });

        isFastFoodToggle = new EditorToggle("isFastFood", "Is Fast Food", false)
            .source(dataSource)
            .onChange(v -> { stats.isFastFood = v; module.markDirty("Is fast food"); });
    }

    public List<EditorSection> getPropertiesSections() {
        List<EditorSection> sections = new ArrayList<>();
        sections.add(new SimpleHeaderSection("props-header", "Food Properties"));
        sections.add(new ToggleSectionAdapter(isMeatToggle));
        sections.add(new ToggleSectionAdapter(isFastFoodToggle));
        sections.add(new TextNoteSection("props-note", "Is Meat: Wolves can eat. Is Fast Food: Faster eating animation."));
        return sections;
    }

    // ═══════════════════════════════════════════════════════════════
    // DEBUG TAB
    // ═══════════════════════════════════════════════════════════════

    public List<EditorSection> getDebugSections(ItemStack item) {
        List<EditorSection> sections = new ArrayList<>();
        sections.add(new SimpleHeaderSection("debug-header", "Debug Information"));

        if (item != null && !item.isEmpty()) {
            @Nonnull Item safeItem = Objects.requireNonNull(item.getItem(), "item");
            String itemId = BuiltInRegistries.ITEM.getKey(safeItem).toString();
            sections.add(new TextNoteSection("debug-item", "Item: " + itemId));
            sections.add(new TextNoteSection("debug-source", "Data Source: " + core.getSourcePrefix()));

            FoodStats stats = core.getStats();
            FoodStats original = core.getOriginalStats();

            String nutritionChanged = stats.nutrition != original.nutrition ? " *" : "";
            sections.add(new TextNoteSection("debug-nutrition", "Nutrition: " + stats.nutrition + nutritionChanged));

            String satChanged = Math.abs(stats.saturation - original.saturation) > 0.001f ? " *" : "";
            sections.add(new TextNoteSection("debug-sat", String.format("Saturation: %.1f%s", stats.saturation, satChanged)));

            String timeChanged = stats.consumptionTime != original.consumptionTime ? " *" : "";
            sections.add(new TextNoteSection("debug-time", "Consume Time: " + stats.consumptionTime + timeChanged));

            String effectsChanged = stats.effects.size() != original.effects.size() ? " *" : "";
            sections.add(new TextNoteSection("debug-effects", "Effects: " + stats.effects.size() + effectsChanged));

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
        FoodStats stats = core.getStats();

        if (nutritionSlider != null) nutritionSlider.setValue(stats.nutrition);
        if (saturationSlider != null) saturationSlider.setValue(stats.saturation);
        if (consumptionTimeSlider != null) consumptionTimeSlider.setValue(stats.consumptionTime);
        if (canAlwaysEatToggle != null) canAlwaysEatToggle.setValue(stats.canAlwaysEat);

        if (isMeatToggle != null) isMeatToggle.setValue(stats.isMeat);
        if (isFastFoodToggle != null) isFastFoodToggle.setValue(stats.isFastFood);
    }
}
