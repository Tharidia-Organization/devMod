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
import com.devmod.stats.FoodStats;

public class FoodModule extends AbstractEditorModule {

    // Delegate classes (lazy initialized to avoid this-escape)
    private @Nullable FoodModuleCore core;
    private @Nullable FoodModuleUI ui;
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
            this.core = new FoodModuleCore();
            this.ui = new FoodModuleUI(this, core);
            delegatesInitialized = true;
        }
    }

    private FoodModuleCore requireCore() {
        ensureDelegates();
        return Objects.requireNonNull(core, "core");
    }

    private FoodModuleUI requireUi() {
        ensureDelegates();
        return Objects.requireNonNull(ui, "ui");
    }

    // ═══════════════════════════════════════════════════════════════
    // DELEGATE ACCESSORS
    // ═══════════════════════════════════════════════════════════════

    public FoodModuleCore getCore() {
        return requireCore();
    }

    public FoodModuleUI getUI() {
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
        FoodModuleCore core = requireCore();
        FoodModuleUI ui = requireUi();
        tabs.clear();

        // Create UI components
        ui.createAllComponents(core.getDataSource());

        // Add tabs - Summary first to showcase ModuleSummarySection
        addTab(ModuleTab.of("summary", "Summary", ui::getSummarySections));
        addTab(ModuleTab.of("nutrition", "Nutrition", ui::getNutritionSections));
        addTab(ModuleTab.of("effects", "Effects", ui::getEffectsSections));
        addTab(ModuleTab.of("properties", "Properties", ui::getPropertiesSections));
        addTab(ModuleTab.of("easydiet", "Easy-Diet", ui::getEasyDietSections));
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
        FoodModuleCore core = requireCore();
        FoodModuleUI ui = requireUi();
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
        FoodStats stats = requireCore().getStats();

        // Build NBT from current stats
        CompoundTag statsTag = new CompoundTag();
        CompoundTag foodStats = new CompoundTag();
        stats.save(foodStats);

        DevMod.LOGGER.info("[Editor][Food][BuildPayload] item={} global={} nutrition={} saturation={} effects={}",
            item.getItem(), isGlobal, stats.getNutrition(), stats.getSaturation(), stats.getEffects().size());

        // Send both legacy and component-friendly payloads
        statsTag.put("FoodModStats", Objects.requireNonNull(foodStats.copy()));
        statsTag.put("food_stats_component", Objects.requireNonNull(foodStats));

        return new com.devmod.network.FoodStatsPayload(
            Objects.requireNonNull(item, "item cannot be null"), statsTag, isGlobal);
    }

    @Override
    public void applyPreview() {
        FoodModuleCore core = requireCore();
        FoodModuleUI ui = requireUi();
        FoodStats stats = core.getStats();
        try {
            ItemStack copy = item.copy();
            DevMod.LOGGER.info("[Editor][Food][ApplyPreview] item={} nutrition={} saturation={} effects={}",
                item.getItem(), stats.getNutrition(), stats.getSaturation(), stats.getEffects().size());

            com.devmod.config.handler.impl.FoodConfigHandler.INSTANCE.setSpecificStats(copy, stats.copy());
            FoodStats applied = com.devmod.config.handler.impl.FoodConfigHandler.INSTANCE.getStats(copy).copy();
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

    public FoodStats getStats() {
        return requireCore().getStats();
    }

    public FoodStats getOriginalStats() {
        return requireCore().getOriginalStats();
    }

    /**
     * Reset all stats to original values loaded from the item.
     */
    @Override
    public void resetToOriginal() {
        FoodModuleCore core = requireCore();
        FoodModuleUI ui = requireUi();
        core.setStats(core.getOriginalStats().copy());
        ui.updateComponentsFromStats();
        clearDirty();
    }

    @Override
    public void resetToDefaults() {
        FoodModuleUI ui = requireUi();
        ItemStack baseline = originalItem.copy();
        com.devmod.config.handler.impl.FoodConfigHandler.clearItemSpecificStats(baseline);
        setItem(baseline);
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
