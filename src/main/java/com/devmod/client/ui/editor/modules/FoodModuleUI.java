package com.devmod.client.ui.editor.modules;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.devmod.client.ui.editor.EditorSection;
import com.devmod.client.ui.editor.components.EditorSlider;
import com.devmod.client.ui.editor.components.EditorToggle;
import com.devmod.client.ui.editor.components.SourceBadge;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.editor.sections.ModuleSummarySection;
import com.devmod.client.ui.editor.sections.SimpleHeaderSection;
import com.devmod.client.ui.editor.sections.EffectListSection;
import com.devmod.client.ui.editor.sections.SliderSectionAdapter;
import com.devmod.client.ui.editor.sections.TextNoteSection;
import com.devmod.client.ui.editor.sections.ToggleSectionAdapter;
import com.devmod.compat.mods.easydiet.EasyDietCompat;
import com.devmod.endurance.nutrition.NutritionCategory;
import com.devmod.stats.FoodStats;

public class FoodModuleUI {

    private final FoodModule module;
    private final FoodModuleCore core;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Nutrition Tab
    // ═══════════════════════════════════════════════════════════════

    @Nullable
    EditorSlider nutritionSlider;
    @Nullable
    EditorSlider saturationSlider;
    @Nullable
    EditorSlider consumptionTimeSlider;
    @Nullable
    EditorToggle canAlwaysEatToggle;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Properties Tab
    // ═══════════════════════════════════════════════════════════════

    @Nullable
    EditorToggle isMeatToggle;
    @Nullable
    EditorToggle isFastFoodToggle;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Easy-Diet Tab
    // ═══════════════════════════════════════════════════════════════

    /** Sliders for each nutrition category (only created if Easy-Diet available). */
    private final Map<NutritionCategory, EditorSlider> dietSliders = new EnumMap<>(NutritionCategory.class);

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
        createEasyDietComponents(dataSource);
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
            .trackColor(DesignTokens.SliderColors.DAMAGE)
            .showInput(true)
            .source(dataSource)
            .info("Hunger points restored. Each point = half drumstick. Steak = 8, Golden Apple = 4.")
            .onChange(v -> { stats.setNutrition(v.intValue()); module.markDirty("Nutrition"); });

        saturationSlider = new EditorSlider("saturation", "Saturation", 0.0f, 2.0f, 0.0f)
            .step(0.1f)
            .format("%.1f")
            .trackColor(DesignTokens.SliderColors.SPECIAL)
            .showInput(true)
            .source(dataSource)
            .info("Saturation modifier. Actual saturation = nutrition * modifier * 2. Golden Apple = 1.2, Steak = 0.8.")
            .onChange(v -> { stats.setSaturation(v); module.markDirty("Saturation"); });

        consumptionTimeSlider = new EditorSlider("consumeTime", "Consumption Time", 1, 100, 32)
            .step(1)
            .format("%.0f")
            .suffix(" ticks")
            .trackColor(DesignTokens.SliderColors.SPEED)
            .showInput(true)
            .source(dataSource)
            .info("Time to eat in ticks. Default food = 32 (1.6s), Dried Kelp = 16. 20 ticks = 1 second.")
            .onChange(v -> { stats.setConsumptionTime(v.intValue()); module.markDirty("Consumption time"); });

        canAlwaysEatToggle = new EditorToggle("canAlwaysEat", "Can Always Eat", false)
            .source(dataSource)
            .onChange(v -> { stats.setCanAlwaysEat(v); module.markDirty("Can always eat"); });
    }

    public List<EditorSection> getNutritionSections() {
        EditorSlider nutrition = Objects.requireNonNull(nutritionSlider, "nutritionSlider");
        EditorSlider saturation = Objects.requireNonNull(saturationSlider, "saturationSlider");
        EditorSlider consumptionTime = Objects.requireNonNull(consumptionTimeSlider, "consumptionTimeSlider");
        EditorToggle canAlwaysEat = Objects.requireNonNull(canAlwaysEatToggle, "canAlwaysEatToggle");
        List<EditorSection> sections = new ArrayList<>();
        sections.add(new SimpleHeaderSection("nutrition-header", "Nutrition Properties"));
        sections.add(new SliderSectionAdapter(nutrition));
        sections.add(new SliderSectionAdapter(saturation));
        sections.add(new SliderSectionAdapter(consumptionTime));
        sections.add(new ToggleSectionAdapter(canAlwaysEat));
        sections.add(new TextNoteSection("nutrition-note", "Actual saturation = nutrition × modifier × 2."));
        return sections;
    }

    // ═══════════════════════════════════════════════════════════════
    // EFFECTS TAB
    // ═══════════════════════════════════════════════════════════════

    public List<EditorSection> getEffectsSections() {
        List<EditorSection> sections = new ArrayList<>();

        FoodStats stats = core.getStats();
        sections.add(new EffectListSection("effects-list", "Food Effects", stats,
            reason -> module.markDirty(reason)));

        return sections;
    }

    // ═══════════════════════════════════════════════════════════════
    // PROPERTIES TAB
    // ═══════════════════════════════════════════════════════════════

    private void createPropertiesComponents(SourceBadge.Source dataSource) {
        FoodStats stats = core.getStats();

        isMeatToggle = new EditorToggle("isMeat", "Is Meat", false)
            .source(dataSource)
            .onChange(v -> { stats.setMeat(v); module.markDirty("Is meat"); });

        isFastFoodToggle = new EditorToggle("isFastFood", "Is Fast Food", false)
            .source(dataSource)
            .onChange(v -> { stats.setFastFood(v); module.markDirty("Is fast food"); });
    }

    // ═══════════════════════════════════════════════════════════════
    // EASY-DIET TAB
    // ═══════════════════════════════════════════════════════════════

    private void createEasyDietComponents(SourceBadge.Source dataSource) {
        // Always create the components (UI available even without mod runtime)
        FoodStats stats = core.getStats();
        dietSliders.clear();

        for (NutritionCategory cat : NutritionCategory.ALL) {
            EditorSlider slider = new EditorSlider(
                "diet_" + cat.getKey(),
                cat.getDisplayName(),
                0, 100, 0 // Use 0-100 range for percentage display
            )
                .step(5)
                .format("%.0f")
                .suffix("%")
                .trackColor(cat.getColor())
                .showInput(true)
                .source(dataSource)
                .info(getCategoryDescription(cat))
                .onChange(v -> {
                    // Convert 0-100 to 0.0-1.0 for storage
                    stats.setNutritionValue(cat, v / 100f);
                    module.markDirty(cat.getDisplayName() + " nutrition");
                });

            dietSliders.put(cat, slider);
        }
    }

    private String getCategoryDescription(NutritionCategory cat) {
        return switch (cat) {
            case GRAIN -> "Cereals, bread, pasta, grains. Provides sustained energy.";
            case PROTEIN -> "Meat, fish, eggs, legumes. Builds strength and endurance.";
            case VEGETABLE -> "Vegetables, greens, roots. Boosts defense and healing.";
            case FRUIT -> "Fruits, berries. Improves speed and agility.";
            case SUGAR -> "Sweets, honey. Quick energy burst but short-lived.";
            case WATER -> "Water, drinks, soups. Essential for all body functions.";
        };
    }

    /**
     * Check if Easy-Diet tab should be shown.
     * Tab is available when Easy-Diet mod is loaded.
     */
    public boolean isEasyDietAvailable() {
        return EasyDietCompat.isAvailable();
    }

    public List<EditorSection> getEasyDietSections() {
        List<EditorSection> sections = new ArrayList<>();

        if (!isEasyDietAvailable()) {
            sections.add(new SimpleHeaderSection("diet-header", "Easy-Diet Integration"));
            sections.add(new TextNoteSection("diet-unavailable",
                "Easy-Diet mod is not installed. Install it to enable nutrition profiles."));
            return sections;
        }

        sections.add(new SimpleHeaderSection("diet-header", "Nutrition Profile"));

        // Add slider for each category
        for (NutritionCategory cat : NutritionCategory.ALL) {
            EditorSlider slider = dietSliders.get(cat);
            if (slider != null) {
                sections.add(new SliderSectionAdapter(slider));
            }
        }

        // Add info note
        FoodStats stats = core.getStats();
        if (stats.hasNutritionProfile()) {
            int count = NutritionCategory.COUNT;
            int configured = 0;
            for (NutritionCategory cat : NutritionCategory.ALL) {
                if (stats.getNutritionValue(cat) > 0) configured++;
            }
            sections.add(new TextNoteSection("diet-status",
                String.format("Configured: %d/%d categories", configured, count)));
        } else {
            sections.add(new TextNoteSection("diet-note",
                "Set values above to define this food's nutritional contribution."));
        }

        sections.add(new TextNoteSection("diet-info",
            "Nutrition affects combat in Endurance quests: well-fed = +15% damage, malnourished = -25% damage."));

        return sections;
    }

    public List<EditorSection> getPropertiesSections() {
        EditorToggle isMeat = Objects.requireNonNull(isMeatToggle, "isMeatToggle");
        EditorToggle isFastFood = Objects.requireNonNull(isFastFoodToggle, "isFastFoodToggle");
        List<EditorSection> sections = new ArrayList<>();
        sections.add(new SimpleHeaderSection("props-header", "Food Properties"));
        sections.add(new ToggleSectionAdapter(isMeat));
        sections.add(new ToggleSectionAdapter(isFastFood));
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

            String nutritionChanged = stats.getNutrition() != original.getNutrition() ? " *" : "";
            sections.add(new TextNoteSection("debug-nutrition", "Nutrition: " + stats.getNutrition() + nutritionChanged));

            String satChanged = Math.abs(stats.getSaturation() - original.getSaturation()) > 0.001f ? " *" : "";
            sections.add(new TextNoteSection("debug-sat", String.format("Saturation: %.1f%s", stats.getSaturation(), satChanged)));

            String timeChanged = stats.getConsumptionTime() != original.getConsumptionTime() ? " *" : "";
            sections.add(new TextNoteSection("debug-time", "Consume Time: " + stats.getConsumptionTime() + timeChanged));

            String effectsChanged = stats.getEffects().size() != original.getEffects().size() ? " *" : "";
            sections.add(new TextNoteSection("debug-effects", "Effects: " + stats.getEffects().size() + effectsChanged));

            sections.add(new TextNoteSection("debug-legend", "* = modified from original"));
        }

        return sections;
    }

    // ═══════════════════════════════════════════════════════════════
    // SUMMARY TAB (uses ModuleSummarySection convenience overloads)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get summary sections using ModuleSummarySection with convenience overloads.
     * This demonstrates usage of the 2-param and 3-param addStat methods.
     */
    public List<EditorSection> getSummarySections() {
        FoodStats stats = core.getStats();

        // Calculate actual saturation value
        double actualSaturation = stats.getNutrition() * stats.getSaturation() * 2.0;

        // Build summary using convenience overloads:
        // - addStat(label, value) for simple stats with defaults
        // - addStat(label, value, format) for custom formatting
        ModuleSummarySection summary = ModuleSummarySection.builder("food-summary", "Nutrition Summary")
            .accentColor(DesignTokens.Accent.GREEN())
            .addStat("Nutrition", stats.getNutrition())                          // 2-param overload
            .addStat("Saturation Mod", stats.getSaturation(), "%.1fx")           // 3-param overload
            .addStat("Actual Saturation", actualSaturation, "%.1f")         // 3-param overload
            .addStat("Eat Time", stats.getConsumptionTime() / 20.0, "%.1fs")     // 3-param overload
            .addStat("Effects", stats.getEffects().size())                       // 2-param overload
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
        FoodStats stats = core.getStats();

        if (nutritionSlider != null) nutritionSlider.setValue(stats.getNutrition());
        if (saturationSlider != null) saturationSlider.setValue(stats.getSaturation());
        if (consumptionTimeSlider != null) consumptionTimeSlider.setValue(stats.getConsumptionTime());
        if (canAlwaysEatToggle != null) canAlwaysEatToggle.setValue(stats.isCanAlwaysEat());

        if (isMeatToggle != null) isMeatToggle.setValue(stats.isMeat());
        if (isFastFoodToggle != null) isFastFoodToggle.setValue(stats.isFastFood());

        // Update Easy-Diet sliders (convert 0.0-1.0 to 0-100 for display)
        for (NutritionCategory cat : NutritionCategory.ALL) {
            EditorSlider slider = dietSliders.get(cat);
            if (slider != null) {
                float value = stats.getNutritionValue(cat);
                slider.setValue(value * 100f);
            }
        }
    }
}
