package com.frenkvs.devmod.ui.editor.modules;

import com.frenkvs.devmod.FuelStats;
import com.frenkvs.devmod.DevMod;
import com.frenkvs.devmod.ui.editor.AbstractEditorModule;
import com.frenkvs.devmod.ui.editor.EditorSection;
import com.frenkvs.devmod.ui.editor.ModuleTab;
import com.frenkvs.devmod.ui.editor.core.EditorCache;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;

/**
 * Editor module for fuel item statistics.
 * Allows editing burn time, efficiency, and cook times for all furnace types.
 *
 * Delegates to:
 * - FuelModuleCore: Stats management, loading, saving
 * - FuelModuleUI: UI components and section builders
 */
public class FuelModule extends AbstractEditorModule {

    // Delegate classes (lazy initialized to avoid this-escape)
    private FuelModuleCore core;
    private FuelModuleUI ui;
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
            this.core = new FuelModuleCore(this);
            this.ui = new FuelModuleUI(this, core);
            delegatesInitialized = true;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // DELEGATE ACCESSORS
    // ═══════════════════════════════════════════════════════════════

    public FuelModuleCore getCore() {
        ensureDelegates();
        return core;
    }

    public FuelModuleUI getUI() {
        ensureDelegates();
        return ui;
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
        ensureDelegates();
        core.loadStatsFromItem(item);
    }

    @Override
    protected void initializeTabs() {
        ensureDelegates();
        tabs.clear();

        // Create UI components
        ui.createAllComponents(core.getDataSource());

        // Add tabs
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
        FuelStats stats = core.getStats();

        // Build NBT from current stats
        CompoundTag statsTag = new CompoundTag();
        CompoundTag fuelStats = new CompoundTag();
        stats.save(fuelStats);

        DevMod.LOGGER.info("[Editor][Fuel][BuildPayload] item={} global={} burnTime={} efficiency={}",
            item.getItem(), isGlobal, stats.burnTime, stats.efficiencyMultiplier);

        // Send both legacy and component-friendly payloads
        statsTag.put("FuelModStats", Objects.requireNonNull(fuelStats.copy()));
        statsTag.put("fuel_stats_component", Objects.requireNonNull(fuelStats));

        return new com.frenkvs.devmod.network.FuelStatsPayload(
            Objects.requireNonNull(item, "item cannot be null"), statsTag, isGlobal);
    }

    @Override
    public void applyPreview() {
        FuelStats stats = core.getStats();
        try {
            ItemStack copy = item.copy();
            DevMod.LOGGER.info("[Editor][Fuel][ApplyPreview] item={} burnTime={} efficiency={}",
                item.getItem(), stats.burnTime, stats.efficiencyMultiplier);

            com.frenkvs.devmod.FuelConfigManager.setSpecificStats(copy, stats.copy());
            FuelStats applied = com.frenkvs.devmod.FuelConfigManager.getStats(copy).copy();
            core.setStats(applied.copy());
            ui.updateComponentsFromStats();
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
        return core.getStats();
    }

    public FuelStats getOriginalStats() {
        return core.getOriginalStats();
    }

    /**
     * Reset all stats to original values loaded from the item.
     */
    public void resetToOriginal() {
        core.setStats(core.getOriginalStats().copy());
        ui.updateComponentsFromStats();
        clearDirty();
    }

    /**
     * Check if current stats differ from original.
     */
    public boolean hasModifications() {
        return core.hasModifications();
    }

    @Override
    public boolean hasPendingDiff() {
        return hasModifications();
    }
}
