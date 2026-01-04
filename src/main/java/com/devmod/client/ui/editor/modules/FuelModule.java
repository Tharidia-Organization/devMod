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
import com.devmod.stats.FuelStats;

public class FuelModule extends AbstractEditorModule {

    // Delegate classes (lazy initialized to avoid this-escape)
    private @Nullable FuelModuleCore core;
    private @Nullable FuelModuleUI ui;
    private boolean delegatesInitialized = false;

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public FuelModule() {
        super("fuel", "Fuel Editor");
    }

    /** Ensures delegates are initialized (lazy init to avoid this-escape). */
    private void ensureDelegates() {
        if (!delegatesInitialized) {
            this.core = new FuelModuleCore();
            this.ui = new FuelModuleUI(this, core);
            delegatesInitialized = true;
        }
    }

    private FuelModuleCore requireCore() {
        ensureDelegates();
        return Objects.requireNonNull(core, "core");
    }

    private FuelModuleUI requireUi() {
        ensureDelegates();
        return Objects.requireNonNull(ui, "ui");
    }

    // ═══════════════════════════════════════════════════════════════
    // DELEGATE ACCESSORS
    // ═══════════════════════════════════════════════════════════════

    public FuelModuleCore getCore() {
        return requireCore();
    }

    public FuelModuleUI getUI() {
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
        requireCore().loadStatsFromItem(item);
    }

    @Override
    protected void initializeTabs() {
        FuelModuleCore core = requireCore();
        FuelModuleUI ui = requireUi();
        tabs.clear();

        // Create UI components
        ui.createAllComponents(core.getDataSource());

        // Add tabs - Summary first to showcase ModuleSummarySection
        addTab(ModuleTab.of("summary", "Summary", ui::getSummarySections));
        addTab(ModuleTab.of("burntime", "Burn Time", ui::getBurnTimeSections));
        addTab(ModuleTab.of("cooktime", "Cook Time", ui::getCookTimeSections));
        addTab(ModuleTab.of("debug", "Debug", () -> ui.getDebugSections(item)));

        // Sync UI with stats after components are created
        ui.updateComponentsFromStats();
    }

    /**
     * Apply external stats (preset/import) while keeping undo/history in sync.
     */
    public void applyExternalStats(FuelStats newStats, String reason) {
        if (newStats == null) {
            return;
        }
        FuelModuleCore core = requireCore();
        FuelModuleUI ui = requireUi();
        saveUndoState();
        core.setStats(newStats.copy());
        ui.updateComponentsFromStats();
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
        FuelStats stats = requireCore().getStats();

        // Build NBT from current stats
        CompoundTag statsTag = new CompoundTag();
        CompoundTag fuelStats = new CompoundTag();
        stats.save(fuelStats);

        DevMod.LOGGER.info("[Editor][Fuel][BuildPayload] item={} global={} burnTime={} efficiency={}",
            item.getItem(), isGlobal, stats.getBurnTime(), stats.getEfficiencyMultiplier());

        // Send both legacy and component-friendly payloads
        statsTag.put("FuelModStats", Objects.requireNonNull(fuelStats.copy()));
        statsTag.put("fuel_stats_component", Objects.requireNonNull(fuelStats));

        return new com.devmod.network.FuelStatsPayload(
            Objects.requireNonNull(item, "item cannot be null"), statsTag, isGlobal);
    }

    @Override
    public void applyPreview() {
        FuelModuleCore core = requireCore();
        FuelModuleUI ui = requireUi();
        FuelStats stats = core.getStats();
        try {
            ItemStack copy = item.copy();
            DevMod.LOGGER.info("[Editor][Fuel][ApplyPreview] item={} burnTime={} efficiency={}",
                item.getItem(), stats.getBurnTime(), stats.getEfficiencyMultiplier());

            com.devmod.config.FuelConfigManager.setSpecificStats(copy, stats.copy());
            FuelStats applied = com.devmod.config.FuelConfigManager.getStats(copy).copy();
            core.setStats(applied.copy());
            withDirtyTrackingDisabled(ui::updateComponentsFromStats);
            setPreviewItem(copy);
            this.item = copy;
            core.setOriginalStats(applied.copy());
        } catch (Exception ignored) {
            clearPreview();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTERS & STATE
    // ═══════════════════════════════════════════════════════════════

    public FuelStats getStats() {
        return requireCore().getStats();
    }

    public FuelStats getOriginalStats() {
        return requireCore().getOriginalStats();
    }

    /**
     * Reset all stats to original values loaded from the item.
     */
    @Override
    public void resetToOriginal() {
        FuelModuleCore core = requireCore();
        FuelModuleUI ui = requireUi();
        core.setStats(core.getOriginalStats().copy());
        ui.updateComponentsFromStats();
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
