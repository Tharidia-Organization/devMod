package com.devmod.client.ui.editor.modules;

import java.util.ArrayList;
import java.util.List;
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
import com.devmod.client.ui.editor.sections.SliderSectionAdapter;
import com.devmod.client.ui.editor.sections.TextNoteSection;
import com.devmod.client.ui.editor.sections.ToggleSectionAdapter;
import com.devmod.stats.UsableStats;

public class UsableModuleUI {

    private final UsableModule module;
    private final UsableModuleCore core;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Timing Tab
    // ═══════════════════════════════════════════════════════════════

    @Nullable
    EditorSlider useDurationSlider;
    @Nullable
    EditorSlider cooldownDurationSlider;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Projectile Tab
    // ═══════════════════════════════════════════════════════════════

    @Nullable
    EditorToggle isThrowableToggle;
    @Nullable
    EditorSlider projectileSpeedSlider;
    @Nullable
    EditorSlider projectileGravitySlider;
    @Nullable
    EditorSlider projectileInaccuracySlider;
    @Nullable
    EditorSlider projectileDamageSlider;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Consumption Tab
    // ═══════════════════════════════════════════════════════════════

    @Nullable
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
            .trackColor(DesignTokens.SliderColors.SPEED)
            .showInput(true)
            .source(dataSource)
            .info("Time in ticks to complete item use. 0 = instant, 32 = default food, 20 ticks = 1 second.")
            .onChange(v -> { stats.setUseDuration(v.intValue()); module.markDirty("Use duration"); });

        cooldownDurationSlider = new EditorSlider("cooldown", "Cooldown", 0, 600, 0)
            .step(1)
            .format("%.0f")
            .suffix(" ticks")
            .trackColor(DesignTokens.SliderColors.SPECIAL)
            .showInput(true)
            .source(dataSource)
            .info("Cooldown time after use in ticks. 20 ticks = 1 second, 600 ticks = 30 seconds (ender pearl default).")
            .onChange(v -> { stats.setCooldownDuration(v.intValue()); module.markDirty("Cooldown"); });
    }

    public List<EditorSection> getTimingSections() {
        EditorSlider useDuration = Objects.requireNonNull(useDurationSlider, "useDurationSlider");
        EditorSlider cooldownDuration = Objects.requireNonNull(cooldownDurationSlider, "cooldownDurationSlider");
        List<EditorSection> sections = new ArrayList<>();
        sections.add(new SimpleHeaderSection("timing-header", "Timing Properties"));
        sections.add(new SliderSectionAdapter(useDuration));
        sections.add(new SliderSectionAdapter(cooldownDuration));
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
            .onChange(v -> { stats.setThrowable(v); module.markDirty("Throwable"); });

        projectileSpeedSlider = new EditorSlider("projSpeed", "Projectile Speed", 0.5f, 5.0f, 1.5f)
            .step(0.1f)
            .format("%.1f")
            .trackColor(DesignTokens.SliderColors.SPEED)
            .showInput(true)
            .source(dataSource)
            .info("Base velocity of thrown projectile. 1.5 = snowball, 3.0 = arrow.")
            .onChange(v -> { stats.setProjectileSpeed(v); module.markDirty("Projectile speed"); });

        projectileGravitySlider = new EditorSlider("projGrav", "Projectile Gravity", 0.0f, 0.1f, 0.03f)
            .step(0.005f)
            .format("%.3f")
            .trackColor(DesignTokens.SliderColors.NEUTRAL)
            .showInput(true)
            .source(dataSource)
            .info("Gravity factor affecting projectile arc. 0.03 = default, 0 = no drop.")
            .onChange(v -> { stats.setProjectileGravity(v); module.markDirty("Projectile gravity"); });

        projectileInaccuracySlider = new EditorSlider("projInacc", "Inaccuracy", 0.0f, 5.0f, 1.0f)
            .step(0.1f)
            .format("%.1f")
            .trackColor(DesignTokens.SliderColors.NEUTRAL)
            .showInput(true)
            .source(dataSource)
            .info("Spread/inaccuracy of projectile. 0 = perfectly accurate, 1 = default.")
            .onChange(v -> { stats.setProjectileInaccuracy(v); module.markDirty("Projectile inaccuracy"); });

        projectileDamageSlider = new EditorSlider("projDmg", "Direct Damage", 0, 20, 0)
            .step(1)
            .format("%.0f")
            .trackColor(DesignTokens.SliderColors.DAMAGE)
            .showInput(true)
            .source(dataSource)
            .info("Damage dealt on direct hit. Snowballs deal 0, eggs deal 0, ender pearls deal 5.")
            .onChange(v -> { stats.setProjectileDamage(v.intValue()); module.markDirty("Projectile damage"); });
    }

    public List<EditorSection> getProjectileSections() {
        EditorToggle throwableToggle = Objects.requireNonNull(isThrowableToggle, "isThrowableToggle");
        EditorSlider projectileSpeed = Objects.requireNonNull(projectileSpeedSlider, "projectileSpeedSlider");
        EditorSlider projectileGravity = Objects.requireNonNull(projectileGravitySlider, "projectileGravitySlider");
        EditorSlider projectileInaccuracy = Objects.requireNonNull(projectileInaccuracySlider, "projectileInaccuracySlider");
        EditorSlider projectileDamage = Objects.requireNonNull(projectileDamageSlider, "projectileDamageSlider");
        List<EditorSection> sections = new ArrayList<>();
        sections.add(new SimpleHeaderSection("proj-header", "Projectile Properties"));
        sections.add(new ToggleSectionAdapter(throwableToggle));
        sections.add(new SliderSectionAdapter(projectileSpeed));
        sections.add(new SliderSectionAdapter(projectileGravity));
        sections.add(new SliderSectionAdapter(projectileInaccuracy));
        sections.add(new SliderSectionAdapter(projectileDamage));
        return sections;
    }

    // ═══════════════════════════════════════════════════════════════
    // CONSUMPTION TAB
    // ═══════════════════════════════════════════════════════════════

    private void createConsumptionComponents(SourceBadge.Source dataSource) {
        UsableStats stats = core.getStats();

        consumeOnUseToggle = new EditorToggle("consume", "Consume On Use", true)
            .source(dataSource)
            .onChange(v -> { stats.setConsumeOnUse(v); module.markDirty("Consume on use"); });
    }

    public List<EditorSection> getConsumptionSections() {
        EditorToggle consumeOnUse = Objects.requireNonNull(consumeOnUseToggle, "consumeOnUseToggle");
        List<EditorSection> sections = new ArrayList<>();
        sections.add(new SimpleHeaderSection("cons-header", "Consumption Properties"));
        sections.add(new ToggleSectionAdapter(consumeOnUse));
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
            @Nonnull Item safeItem = Objects.requireNonNull(item.getItem(), "item");
            String itemId = BuiltInRegistries.ITEM.getKey(safeItem).toString();
            sections.add(new TextNoteSection("debug-item", "Item: " + itemId));
            sections.add(new TextNoteSection("debug-source", "Data Source: " + core.getSourcePrefix()));

            UsableStats stats = core.getStats();
            UsableStats original = core.getOriginalStats();

            // Show current values and whether they differ from original
            String durChanged = stats.getUseDuration() != original.getUseDuration() ? " *" : "";
            sections.add(new TextNoteSection("debug-dur", "Use Duration: " + stats.getUseDuration() + durChanged));

            String cooldownChanged = stats.getCooldownDuration() != original.getCooldownDuration() ? " *" : "";
            sections.add(new TextNoteSection("debug-cd", "Cooldown: " + stats.getCooldownDuration() + cooldownChanged));

            String throwChanged = stats.isThrowable() != original.isThrowable() ? " *" : "";
            sections.add(new TextNoteSection("debug-throw", "Throwable: " + stats.isThrowable() + throwChanged));

            String speedChanged = Math.abs(stats.getProjectileSpeed() - original.getProjectileSpeed()) > 0.001f ? " *" : "";
            sections.add(new TextNoteSection("debug-speed", String.format("Proj. Speed: %.2f%s", stats.getProjectileSpeed(), speedChanged)));

            String consumeChanged = stats.isConsumeOnUse() != original.isConsumeOnUse() ? " *" : "";
            sections.add(new TextNoteSection("debug-consume", "Consume: " + stats.isConsumeOnUse() + consumeChanged));

            sections.add(new TextNoteSection("debug-legend", "* = modified from original"));
        }

        return sections;
    }

    // ═══════════════════════════════════════════════════════════════
    // SUMMARY TAB (uses ModuleSummarySection with StatEntry overload)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get summary sections using ModuleSummarySection with addStat(StatEntry) overload.
     * This demonstrates usage of the StatEntry direct add pattern.
     */
    public List<EditorSection> getSummarySections() {
        UsableStats stats = core.getStats();

        // Pre-build StatEntry objects to use addStat(StatEntry) overload
        ModuleSummarySection.StatEntry useDur = new ModuleSummarySection.StatEntry(
            "Use Duration", stats.getUseDuration(), "%.0f ticks");
        ModuleSummarySection.StatEntry cooldown = new ModuleSummarySection.StatEntry(
            "Cooldown", stats.getCooldownDuration(), "%.0f ticks");

        // Build summary using addStat(StatEntry) overload
        ModuleSummarySection summary = ModuleSummarySection.builder("usable-summary", "Usable Item Summary")
            .accentColor(DesignTokens.Accent.PURPLE())
            .addStat(useDur)                                                    // StatEntry overload
            .addStat(cooldown)                                                  // StatEntry overload
            .addStat("Throwable", stats.isThrowable() ? 1.0 : 0.0, stats.isThrowable() ? "Yes" : "No")
            .addStat("Proj. Speed", stats.getProjectileSpeed(), "%.1f")              // 3-param overload
            .addStat("Consume", stats.isConsumeOnUse() ? 1.0 : 0.0, stats.isConsumeOnUse() ? "Yes" : "No")
            .build();

        return List.of(summary);
    }

    // ═══════════════════════════════════════════════════════════════
    // SLIDER SYNC
    // ═══════════════════════════════════════════════════════════════

    /**
     * Update all sliders to reflect current stats values.
     */
    public void updateSlidersFromStats() {
        UsableStats stats = core.getStats();

        if (useDurationSlider != null) useDurationSlider.setValue(stats.getUseDuration());
        if (cooldownDurationSlider != null) cooldownDurationSlider.setValue(stats.getCooldownDuration());

        if (isThrowableToggle != null) isThrowableToggle.setValue(stats.isThrowable());
        if (projectileSpeedSlider != null) projectileSpeedSlider.setValue(stats.getProjectileSpeed());
        if (projectileGravitySlider != null) projectileGravitySlider.setValue(stats.getProjectileGravity());
        if (projectileInaccuracySlider != null) projectileInaccuracySlider.setValue(stats.getProjectileInaccuracy());
        if (projectileDamageSlider != null) projectileDamageSlider.setValue(stats.getProjectileDamage());

        if (consumeOnUseToggle != null) consumeOnUseToggle.setValue(stats.isConsumeOnUse());
    }
}
