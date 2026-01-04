package com.devmod.client.ui.editor.modules;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import com.devmod.DevMod;
import com.devmod.client.ui.editor.AbstractEditorModule;
import com.devmod.client.ui.editor.EditorSection;
import com.devmod.client.ui.editor.ModuleTab;
import com.devmod.client.ui.editor.core.EditorCache;
import com.devmod.stats.UsableStats;

public class UsableModule extends AbstractEditorModule {

    public enum UsableVariant { STANDARD, THROWABLE, CHARGEABLE, BLOCKABLE }

    private UsableVariant variant = UsableVariant.STANDARD;

    // Delegate classes (lazy initialized to avoid this-escape)
    private @Nullable UsableModuleCore core;
    private @Nullable UsableModuleUI ui;
    private boolean delegatesInitialized = false;

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public UsableModule() {
        super("usable", "Usable Item Editor");
    }

    public UsableModule(UsableVariant variant) {
        super("usable", "Usable Item Editor");
        this.variant = variant == null ? UsableVariant.STANDARD : variant;
    }

    /** Ensures delegates are initialized (lazy init to avoid this-escape). */
    private void ensureDelegates() {
        if (!delegatesInitialized) {
            this.core = new UsableModuleCore();
            this.ui = new UsableModuleUI(this, core);
            delegatesInitialized = true;
        }
    }

    private UsableModuleCore requireCore() {
        ensureDelegates();
        return Objects.requireNonNull(core, "core");
    }

    private UsableModuleUI requireUi() {
        ensureDelegates();
        return Objects.requireNonNull(ui, "ui");
    }

    // ═══════════════════════════════════════════════════════════════
    // DELEGATE ACCESSORS
    // ═══════════════════════════════════════════════════════════════

    public UsableModuleCore getCore() {
        return requireCore();
    }

    public UsableModuleUI getUI() {
        return requireUi();
    }

    // ═══════════════════════════════════════════════════════════════
    // PUBLIC WRAPPERS FOR PROTECTED METHODS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Expose history entries to delegate classes.
     */
    public List<String> getRecentHistoryEntriesPublic(int maxEntries) {
        return getRecentHistoryEntries(maxEntries);
    }

    /**
     * Expose status reporting to delegate classes.
     */
    public void reportStatusPublic(String message, int color) {
        reportStatus(message, color);
    }

    // ═══════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void onItemSet() {
        // Ensure delegates are initialized before use
        UsableModuleCore core = requireCore();

        // Load existing usable stats from item NBT
        core.loadStatsFromItem(item);

        // NOTE: updateSlidersFromStats() is called in initializeTabs() AFTER createAllComponents()
        // Do not call it here as the sliders don't exist yet
    }

    @Override
    protected void initializeTabs() {
        // Ensure delegates are initialized before use
        UsableModuleCore core = requireCore();
        UsableModuleUI ui = requireUi();

        tabs.clear();

        // Create UI components
        ui.createAllComponents(core.getDataSource());

        // Add tabs - Summary first to showcase ModuleSummarySection
        addTab(ModuleTab.of("summary", "Summary", ui::getSummarySections));
        addTab(ModuleTab.of("timing", "Timing", ui::getTimingSections));

        if (variant == UsableVariant.THROWABLE) {
            addTab(ModuleTab.of("projectile", "Projectile", ui::getProjectileSections));
        }

        addTab(ModuleTab.of("consumption", "Consumption", ui::getConsumptionSections));
        addTab(ModuleTab.of("debug", "Debug", () -> ui.getDebugSections(item)));

        // Sync UI with stats after components are created
        ui.updateSlidersFromStats();
    }

    /**
     * Apply external stats (preset/import) while keeping undo/history in sync.
     */
    public void applyExternalStats(UsableStats newStats, String reason) {
        if (newStats == null) {
            return;
        }
        UsableModuleCore core = requireCore();
        UsableModuleUI ui = requireUi();
        saveUndoState();
        core.setStats(newStats.copy());
        ui.updateSlidersFromStats();
        pendingChanges.clear();
        String changeReason = (reason == null || reason.isBlank()) ? "Preset applied" : reason;
        pendingChanges.add(changeReason);
        addHistoryEntry(changeReason);
        EditorCache.INSTANCE.invalidateItem(item.toString());
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

    // ═══════════════════════════════════════════════════════════════
    // NETWORK
    // ═══════════════════════════════════════════════════════════════

    @Override
    public CustomPacketPayload buildPayload(boolean isGlobal) {
        UsableStats stats = requireCore().getStats();

        // Build NBT from current stats
        CompoundTag statsTag = new CompoundTag();
        CompoundTag usableStats = new CompoundTag();
        stats.save(usableStats);

        DevMod.LOGGER.info("[Editor][Usable][BuildPayload] item={} global={} useDur={} cooldown={} throwable={}",
            item.getItem(), isGlobal, stats.getUseDuration(), stats.getCooldownDuration(), stats.isThrowable());

        // Send both legacy and component-friendly payloads
        statsTag.put("UsableModStats", Objects.requireNonNull(usableStats.copy()));
        statsTag.put("usable_stats_component", Objects.requireNonNull(usableStats));

        return new com.devmod.network.UsableStatsPayload(
            Objects.requireNonNull(item, "item cannot be null"), statsTag, isGlobal);
    }

    @Override
    public void applyPreview() {
        UsableModuleCore core = requireCore();
        UsableModuleUI ui = requireUi();
        UsableStats stats = core.getStats();
        // Prepare a preview copy of the item and set CustomData on the copy only.
        try {
            ItemStack copy = item.copy();
            DevMod.LOGGER.info("[Editor][Usable][ApplyPreview] item={} useDur={} cooldown={} throwable={}",
                item.getItem(), stats.getUseDuration(), stats.getCooldownDuration(), stats.isThrowable());
            // Apply full stack edits to the preview copy
            com.devmod.config.UsableConfigManager.setSpecificStats(copy, stats.copy());
            // Reload from the written stack to pick up server-side clamps/normalization
            UsableStats applied = com.devmod.config.UsableConfigManager.getStats(copy).copy();
            core.setStats(applied.copy());
            withDirtyTrackingDisabled(ui::updateSlidersFromStats);
            // Store the preview copy in the base class so UI can render it without mutating the real item
            setPreviewItem(copy);
            // Also advance our working item/original stats so subsequent edits start from the latest applied state
            this.item = copy;
            core.setOriginalStats(applied.copy());
        } catch (Exception ignored) {
            // Best-effort: if preview cannot be created, ensure no preview remains
            clearPreview();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTERS & STATE
    // ═══════════════════════════════════════════════════════════════

    public UsableStats getStats() {
        return requireCore().getStats();
    }

    public UsableStats getOriginalStats() {
        return requireCore().getOriginalStats();
    }

    public UsableVariant getVariant() {
        return variant;
    }

    public void setVariant(UsableVariant variant) {
        this.variant = variant == null ? UsableVariant.STANDARD : variant;
    }

    /**
     * Reset all stats to original values loaded from the item.
     */
    @Override
    public void resetToOriginal() {
        UsableModuleCore core = requireCore();
        UsableModuleUI ui = requireUi();
        core.setStats(core.getOriginalStats().copy());
        ui.updateSlidersFromStats();
        clearDirty();
    }

    /**
     * Check if current stats differ from original.
     */
    public boolean hasModifications() {
        return requireCore().hasModifications();
    }

    @Override
    public boolean hasPendingDiff() {
        return hasModifications();
    }
}
