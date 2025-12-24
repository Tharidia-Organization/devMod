package com.devmod.ui.editor.modules;

import com.devmod.stats.FoodStats;
import com.devmod.DevMod;
import com.devmod.ui.editor.AbstractEditorModule;
import com.devmod.ui.editor.EditorSection;
import com.devmod.ui.editor.ModuleTab;
import com.devmod.ui.editor.core.EditorCache;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;

/**
 * Editor module for food item statistics.
 * Allows editing nutrition, saturation, consumption time, and effects.
 *
 * Delegates to:
 * - FoodModuleCore: Stats management, loading, saving
 * - FoodModuleUI: UI components and section builders
 */
public class FoodModule extends AbstractEditorModule {

    // Delegate classes (lazy initialized to avoid this-escape)
    private FoodModuleCore core;
    private FoodModuleUI ui;
    private boolean delegatesInitialized = false;

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public FoodModule() {
        super("food", "Food Editor");
    }

    /** Ensures delegates are initialized (lazy init to avoid this-escape). */
    private void ensureDelegates() {
        if (!delegatesInitialized) {
            this.core = new FoodModuleCore(this);
            this.ui = new FoodModuleUI(this, core);
            delegatesInitialized = true;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // DELEGATE ACCESSORS
    // ═══════════════════════════════════════════════════════════════

    public FoodModuleCore getCore() {
        ensureDelegates();
        return core;
    }

    public FoodModuleUI getUI() {
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
        addTab(ModuleTab.of("nutrition", "Nutrition", ui::getNutritionSections));
        addTab(ModuleTab.of("effects", "Effects", ui::getEffectsSections));
        addTab(ModuleTab.of("properties", "Properties", ui::getPropertiesSections));
        addTab(ModuleTab.of("debug", "Debug", () -> ui.getDebugSections(item)));

        // Sync UI with stats after components are created
        ui.updateComponentsFromStats();
    }

    /**
     * Apply external stats (preset/import) while keeping undo/history in sync.
     */
    public void applyExternalStats(FoodStats newStats, String reason) {
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
        FoodStats stats = core.getStats();

        // Build NBT from current stats
        CompoundTag statsTag = new CompoundTag();
        CompoundTag foodStats = new CompoundTag();
        stats.save(foodStats);

        DevMod.LOGGER.info("[Editor][Food][BuildPayload] item={} global={} nutrition={} saturation={} effects={}",
            item.getItem(), isGlobal, stats.nutrition, stats.saturation, stats.effects.size());

        // Send both legacy and component-friendly payloads
        statsTag.put("FoodModStats", Objects.requireNonNull(foodStats.copy()));
        statsTag.put("food_stats_component", Objects.requireNonNull(foodStats));

        return new com.devmod.network.FoodStatsPayload(
            Objects.requireNonNull(item, "item cannot be null"), statsTag, isGlobal);
    }

    @Override
    public void applyPreview() {
        FoodStats stats = core.getStats();
        try {
            ItemStack copy = item.copy();
            DevMod.LOGGER.info("[Editor][Food][ApplyPreview] item={} nutrition={} saturation={} effects={}",
                item.getItem(), stats.nutrition, stats.saturation, stats.effects.size());

            com.devmod.config.FoodConfigManager.setSpecificStats(copy, stats.copy());
            FoodStats applied = com.devmod.config.FoodConfigManager.getStats(copy).copy();
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

    public FoodStats getStats() {
        return core.getStats();
    }

    public FoodStats getOriginalStats() {
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
