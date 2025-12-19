package com.frenkvs.devmod.ui.editor.modules;

import com.frenkvs.devmod.UsableStats;
import com.frenkvs.devmod.ui.editor.EditorSection;
import com.frenkvs.devmod.ui.editor.components.EditorSlider;
import com.frenkvs.devmod.ui.editor.components.EditorToggle;
import com.frenkvs.devmod.ui.editor.components.SourceBadge;
import com.frenkvs.devmod.ui.editor.core.UIConstants;
import com.frenkvs.devmod.ui.editor.sections.SimpleHeaderSection;
import com.frenkvs.devmod.ui.editor.sections.SliderSectionAdapter;
import com.frenkvs.devmod.ui.editor.sections.TextNoteSection;
import com.frenkvs.devmod.ui.editor.sections.ToggleSectionAdapter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * UI components and section builders for UsableModule.
 */
public class UsableModuleUI {

    private final UsableModule module;
    private final UsableModuleCore core;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Timing Tab
    // ═══════════════════════════════════════════════════════════════

    EditorSlider useDurationSlider;
    EditorSlider cooldownDurationSlider;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Projectile Tab
    // ═══════════════════════════════════════════════════════════════

    EditorToggle isThrowableToggle;
    EditorSlider projectileSpeedSlider;
    EditorSlider projectileGravitySlider;
    EditorSlider projectileInaccuracySlider;
    EditorSlider projectileDamageSlider;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Consumption Tab
    // ═══════════════════════════════════════════════════════════════

    EditorToggle consumeOnUseToggle;

    public UsableModuleUI(UsableModule module, UsableModuleCore core) {
        this.module = module;
        this.core = core;
    }

    // ═══════════════════════════════════════════════════════════════
    // CREATE ALL COMPONENTS
    // ═══════════════════════════════════════════════════════════════

    public void createAllComponents(SourceBadge.Source dataSource) {
        createTimingComponents(dataSource);
        createProjectileComponents(dataSource);
        createConsumptionComponents(dataSource);
    }

    // ═══════════════════════════════════════════════════════════════
    // TIMING TAB
    // ═══════════════════════════════════════════════════════════════

    private void createTimingComponents(SourceBadge.Source dataSource) {
        UsableStats stats = core.getStats();

        useDurationSlider = new EditorSlider("useDur", "Use Duration", 0, 200, 0)
            .step(1)
            .format("%.0f")
            .suffix(" ticks")
            .trackColor(UIConstants.SliderColors.SPEED)
            .showInput(true)
            .source(dataSource)
            .info("Time in ticks to complete item use. 0 = instant, 32 = default food, 20 ticks = 1 second.")
            .onChange(v -> { stats.useDuration = v.intValue(); module.markDirty("Use duration"); });

        cooldownDurationSlider = new EditorSlider("cooldown", "Cooldown", 0, 600, 0)
            .step(1)
            .format("%.0f")
            .suffix(" ticks")
            .trackColor(UIConstants.SliderColors.SPECIAL)
            .showInput(true)
            .source(dataSource)
            .info("Cooldown time after use in ticks. 20 ticks = 1 second, 600 ticks = 30 seconds (ender pearl default).")
            .onChange(v -> { stats.cooldownDuration = v.intValue(); module.markDirty("Cooldown"); });
    }

    public List<EditorSection> getTimingSections() {
        List<EditorSection> sections = new ArrayList<>();
        sections.add(new SimpleHeaderSection("timing-header", "Timing Properties"));
        sections.add(new SliderSectionAdapter(useDurationSlider));
        sections.add(new SliderSectionAdapter(cooldownDurationSlider));
        sections.add(new TextNoteSection("timing-note", "20 ticks = 1 second. Food default is 32 ticks (1.6s)."));
        return sections;
    }

    // ═══════════════════════════════════════════════════════════════
    // PROJECTILE TAB
    // ═══════════════════════════════════════════════════════════════

    private void createProjectileComponents(SourceBadge.Source dataSource) {
        UsableStats stats = core.getStats();

        isThrowableToggle = new EditorToggle("throwable", "Is Throwable", false)
            .source(dataSource)
            .onChange(v -> { stats.isThrowable = v; module.markDirty("Throwable"); });

        projectileSpeedSlider = new EditorSlider("projSpeed", "Projectile Speed", 0.5f, 5.0f, 1.5f)
            .step(0.1f)
            .format("%.1f")
            .trackColor(UIConstants.SliderColors.SPEED)
            .showInput(true)
            .source(dataSource)
            .info("Base velocity of thrown projectile. 1.5 = snowball, 3.0 = arrow.")
            .onChange(v -> { stats.projectileSpeed = v; module.markDirty("Projectile speed"); });

        projectileGravitySlider = new EditorSlider("projGrav", "Projectile Gravity", 0.0f, 0.1f, 0.03f)
            .step(0.005f)
            .format("%.3f")
            .trackColor(UIConstants.SliderColors.NEUTRAL)
            .showInput(true)
            .source(dataSource)
            .info("Gravity factor affecting projectile arc. 0.03 = default, 0 = no drop.")
            .onChange(v -> { stats.projectileGravity = v; module.markDirty("Projectile gravity"); });

        projectileInaccuracySlider = new EditorSlider("projInacc", "Inaccuracy", 0.0f, 5.0f, 1.0f)
            .step(0.1f)
            .format("%.1f")
            .trackColor(UIConstants.SliderColors.NEUTRAL)
            .showInput(true)
            .source(dataSource)
            .info("Spread/inaccuracy of projectile. 0 = perfectly accurate, 1 = default.")
            .onChange(v -> { stats.projectileInaccuracy = v; module.markDirty("Projectile inaccuracy"); });

        projectileDamageSlider = new EditorSlider("projDmg", "Direct Damage", 0, 20, 0)
            .step(1)
            .format("%.0f")
            .trackColor(UIConstants.SliderColors.DAMAGE)
            .showInput(true)
            .source(dataSource)
            .info("Damage dealt on direct hit. Snowballs deal 0, eggs deal 0, ender pearls deal 5.")
            .onChange(v -> { stats.projectileDamage = v.intValue(); module.markDirty("Projectile damage"); });
    }

    public List<EditorSection> getProjectileSections() {
        List<EditorSection> sections = new ArrayList<>();
        sections.add(new SimpleHeaderSection("proj-header", "Projectile Properties"));
        sections.add(new ToggleSectionAdapter(isThrowableToggle));
        sections.add(new SliderSectionAdapter(projectileSpeedSlider));
        sections.add(new SliderSectionAdapter(projectileGravitySlider));
        sections.add(new SliderSectionAdapter(projectileInaccuracySlider));
        sections.add(new SliderSectionAdapter(projectileDamageSlider));
        return sections;
    }

    // ═══════════════════════════════════════════════════════════════
    // CONSUMPTION TAB
    // ═══════════════════════════════════════════════════════════════

    private void createConsumptionComponents(SourceBadge.Source dataSource) {
        UsableStats stats = core.getStats();

        consumeOnUseToggle = new EditorToggle("consume", "Consume On Use", true)
            .source(dataSource)
            .onChange(v -> { stats.consumeOnUse = v; module.markDirty("Consume on use"); });
    }

    public List<EditorSection> getConsumptionSections() {
        List<EditorSection> sections = new ArrayList<>();
        sections.add(new SimpleHeaderSection("cons-header", "Consumption Properties"));
        sections.add(new ToggleSectionAdapter(consumeOnUseToggle));
        sections.add(new TextNoteSection("cons-note", "Remainder item (e.g., bucket after milk) can be set via commands."));
        return sections;
    }

    // ═══════════════════════════════════════════════════════════════
    // DEBUG TAB
    // ═══════════════════════════════════════════════════════════════

    public List<EditorSection> getDebugSections(ItemStack item) {
        List<EditorSection> sections = new ArrayList<>();
        sections.add(new SimpleHeaderSection("debug-header", "Debug Information"));

        if (item != null && !item.isEmpty()) {
            String itemId = BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
            sections.add(new TextNoteSection("debug-item", "Item: " + itemId));
            sections.add(new TextNoteSection("debug-source", "Data Source: " + core.getSourcePrefix()));

            UsableStats stats = core.getStats();
            UsableStats original = core.getOriginalStats();

            // Show current values and whether they differ from original
            String durChanged = stats.useDuration != original.useDuration ? " *" : "";
            sections.add(new TextNoteSection("debug-dur", "Use Duration: " + stats.useDuration + durChanged));

            String cooldownChanged = stats.cooldownDuration != original.cooldownDuration ? " *" : "";
            sections.add(new TextNoteSection("debug-cd", "Cooldown: " + stats.cooldownDuration + cooldownChanged));

            String throwChanged = stats.isThrowable != original.isThrowable ? " *" : "";
            sections.add(new TextNoteSection("debug-throw", "Throwable: " + stats.isThrowable + throwChanged));

            String speedChanged = Math.abs(stats.projectileSpeed - original.projectileSpeed) > 0.001f ? " *" : "";
            sections.add(new TextNoteSection("debug-speed", String.format("Proj. Speed: %.2f%s", stats.projectileSpeed, speedChanged)));

            String consumeChanged = stats.consumeOnUse != original.consumeOnUse ? " *" : "";
            sections.add(new TextNoteSection("debug-consume", "Consume: " + stats.consumeOnUse + consumeChanged));

            sections.add(new TextNoteSection("debug-legend", "* = modified from original"));
        }

        return sections;
    }

    // ═══════════════════════════════════════════════════════════════
    // SLIDER SYNC
    // ═══════════════════════════════════════════════════════════════

    /**
     * Update all sliders to reflect current stats values.
     */
    public void updateSlidersFromStats() {
        UsableStats stats = core.getStats();

        if (useDurationSlider != null) useDurationSlider.setValue(stats.useDuration);
        if (cooldownDurationSlider != null) cooldownDurationSlider.setValue(stats.cooldownDuration);

        if (isThrowableToggle != null) isThrowableToggle.setValue(stats.isThrowable);
        if (projectileSpeedSlider != null) projectileSpeedSlider.setValue(stats.projectileSpeed);
        if (projectileGravitySlider != null) projectileGravitySlider.setValue(stats.projectileGravity);
        if (projectileInaccuracySlider != null) projectileInaccuracySlider.setValue(stats.projectileInaccuracy);
        if (projectileDamageSlider != null) projectileDamageSlider.setValue(stats.projectileDamage);

        if (consumeOnUseToggle != null) consumeOnUseToggle.setValue(stats.consumeOnUse);
    }
}
