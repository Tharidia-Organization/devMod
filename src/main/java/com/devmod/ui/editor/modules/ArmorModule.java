package com.devmod.ui.editor.modules;

import com.devmod.stats.ArmorStats;
import com.devmod.config.ArmorConfigManager;
import com.devmod.DevMod;
import com.devmod.transport.ArmorStatsPayloadV2;
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
 * Editor module for armor statistics.
 * Allows editing damage reductions, armor bonuses, and special effects.
 *
 * Delegates to:
 * - ArmorModuleCore: Stats management, loading, saving, comparison
 * - ArmorModuleUI: UI components and section builders
 *
 * @see EDITOR_DESIGN_SYSTEM.md Section 3.4
 */
public class ArmorModule extends AbstractEditorModule {

    private static final String NBT_KEY = "ArmorModStats";

    public enum ArmorVariant { STANDARD, SHIELD }

    private ArmorVariant variant = ArmorVariant.STANDARD;

    // Delegate classes (lazy initialized to avoid this-escape)
    private ArmorModuleCore core;
    private ArmorModuleUI ui;
    private boolean delegatesInitialized = false;

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public ArmorModule() {
        super("armor", "Armor Editor");
    }

    public ArmorModule(ArmorVariant variant) {
        super("armor", "Armor Editor");
        this.variant = variant == null ? ArmorVariant.STANDARD : variant;
    }

    /** Ensures delegates are initialized (lazy init to avoid this-escape). */
    private void ensureDelegates() {
        if (!delegatesInitialized) {
            this.core = new ArmorModuleCore();
            this.ui = new ArmorModuleUI(this, core);
            delegatesInitialized = true;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // DELEGATE ACCESSORS
    // ═══════════════════════════════════════════════════════════════

    public ArmorModuleCore getCore() {
        ensureDelegates();
        return core;
    }

    public ArmorModuleUI getUI() {
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

        // Auto-detect shield variant from item type
        detectVariantFromItem();

        // Load existing armor stats from item NBT
        core.loadStatsFromItem(item);
        core.setOriginalStats(core.getStats().copy());

        // Reinitialize tabs now that we know the variant
        initializeTabs();

        // Update all components to reflect current stats
        ui.updateComponentsFromStats();
    }

    /**
     * Detects if the item is a shield and sets the variant accordingly.
     */
    private void detectVariantFromItem() {
        if (item == null) {
            variant = ArmorVariant.STANDARD;
            return;
        }

        // Check if item is a ShieldItem
        if (item.getItem() instanceof net.minecraft.world.item.ShieldItem) {
            variant = ArmorVariant.SHIELD;
            DevMod.LOGGER.info("[Editor][Armor] Detected SHIELD variant for item: {}", item.getItem());
        } else {
            variant = ArmorVariant.STANDARD;
        }
    }

    @Override
    protected void initializeTabs() {
        ensureDelegates();
        tabs.clear();

        // Create UI components
        ui.createAllComponents(core.determineSource());

        // Add tabs
        addTab(ModuleTab.of("reduction", "Reduction", ui::getDamageReductionSections));
        addTab(ModuleTab.of("stats", "Stats", ui::getVanillaStatsSections));
        addTab(ModuleTab.of("special", "Special", ui::getSpecialSections));
        if (variant == ArmorVariant.SHIELD) {
            addTab(ModuleTab.of("shield", "Shield", ui::getShieldSections));
            addTab(ModuleTab.of("visual", "Visual", ui::getShieldVisualSections));
            addTab(ModuleTab.of("deflect", "Deflect", ui::getShieldDeflectionSections));
            addTab(ModuleTab.of("shatter", "Shatter", ui::getShieldShatterSections));
        }
        addTab(ModuleTab.of("debug", "Debug", () -> ui.getDebugSections(item)));
    }

    /**
     * Apply external stats (preset/import) while keeping undo/history in sync.
     */
    public void applyExternalStats(ArmorStats newStats, String reason) {
        if (newStats == null) {
            return;
        }
        ensureDelegates();
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
        ensureDelegates();
        ArmorStats stats = core.getStats();

        CompoundTag statsTag = new CompoundTag();
        CompoundTag armorStats = new CompoundTag();
        stats.save(armorStats);
        statsTag.put(NBT_KEY, Objects.requireNonNull(armorStats.copy()));
        statsTag.put("armor_stats_component", Objects.requireNonNull(armorStats));

        // Slot is filled by ItemEditorScreen when sending; default to -1 here
        return new ArmorStatsPayloadV2(Objects.requireNonNull(item, "item cannot be null"), statsTag, isGlobal, -1);
    }

    @Override
    public void applyPreview() {
        ensureDelegates();
        // Create a preview copy and attach CustomData to the copy only
        try {
            ItemStack copy = item.copy();
            // Leverage config manager to set both component and custom data
            ArmorConfigManager.setSpecificStats(copy, core.getStats().copy());
            setPreviewItem(copy);
        } catch (Exception ignored) {
            clearPreview();
        }
    }

    @Override
    public void resetToOriginal() {
        ensureDelegates();
        core.setStats(core.getOriginalStats().copy());
        ui.updateComponentsFromStats();
        clearDirty();
    }

    // ═══════════════════════════════════════════════════════════════
    // DIRTY TRACKING (Override to update badges)
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void markDirty(String changeDescription) {
        super.markDirty(changeDescription);
        // Update source badges whenever a change is made
        if (delegatesInitialized && ui != null) {
            ui.updateSourceBadges();
        }
    }

    @Override
    public void clearDirty() {
        super.clearDirty();
        // Reset source badges to original state
        if (delegatesInitialized && ui != null) {
            ui.updateSourceBadges();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTERS & STATE
    // ═══════════════════════════════════════════════════════════════

    public ArmorStats getStats() {
        ensureDelegates();
        return core.getStats();
    }

    public ArmorStats getOriginalStats() {
        ensureDelegates();
        return core.getOriginalStats();
    }

    public ArmorVariant getVariant() {
        return variant;
    }

    public void setVariant(ArmorVariant variant) {
        this.variant = variant == null ? ArmorVariant.STANDARD : variant;
    }

    @Override
    public boolean hasPendingDiff() {
        ensureDelegates();
        return core.hasPendingDiff();
    }
}
