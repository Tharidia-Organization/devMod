package com.devmod.client.ui.editor.modules;

import java.util.List;
import java.util.Objects;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import com.devmod.DevMod;
import com.devmod.client.ui.editor.AbstractEditorModule;
import com.devmod.client.ui.editor.EditorSection;
import com.devmod.client.ui.editor.ModuleTab;
import com.devmod.client.ui.editor.core.EditorCache;
import com.devmod.stats.WeaponStats;
public class WeaponModule extends AbstractEditorModule {

    public enum WeaponVariant { STANDARD, MACE, TRIDENT }

    private WeaponVariant variant = WeaponVariant.STANDARD;

    // Delegate classes (lazy initialized to avoid this-escape)
    private WeaponModuleCore core;
    private WeaponModuleVariants variants;
    private WeaponModuleUI ui;
    private boolean delegatesInitialized = false;

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public WeaponModule() {
        super("weapon", "Weapon Editor");
    }

    public WeaponModule(WeaponVariant variant) {
        super("weapon", "Weapon Editor");
        this.variant = variant == null ? WeaponVariant.STANDARD : variant;
    }

    /** Ensures delegates are initialized (lazy init to avoid this-escape). */
    private void ensureDelegates() {
        if (!delegatesInitialized) {
            this.core = new WeaponModuleCore(this);
            this.variants = new WeaponModuleVariants(this);
            this.ui = new WeaponModuleUI(this, core, variants);
            delegatesInitialized = true;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // DELEGATE ACCESSORS
    // ═══════════════════════════════════════════════════════════════

    public WeaponModuleCore getCore() {
        ensureDelegates();
        return core;
    }

    public WeaponModuleVariants getVariants() {
        ensureDelegates();
        return variants;
    }

    public WeaponModuleUI getUI() {
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
        // Ensure delegates are initialized before use
        ensureDelegates();

        // Load existing weapon stats from item NBT
        core.loadStatsFromItem(item);

        // Load variant data
        CompoundTag customTag = core.getCustomDataTag(item);
        CompoundTag statsTag = null;
        try {
            statsTag = item.get(Objects.requireNonNull(com.devmod.components.WeaponComponents.WEAPON_STATS.get()));
        } catch (Exception ignored) { }
        variants.loadVariantData(customTag, statsTag);
        variants.snapshotVariantOriginals();

        // NOTE: updateSlidersFromStats() is called in initializeTabs() AFTER createAllComponents()
        // Do not call it here as the sliders don't exist yet
    }

    @Override
    protected void initializeTabs() {
        // Ensure delegates are initialized before use
        ensureDelegates();

        tabs.clear();

        // Create UI components
        ui.createAllComponents(core.getDataSource());
        variants.createVariantComponents(variant, core.getDataSource());

        // Add tabs
        addTab(ModuleTab.of("hitlocation", "Hit Location", ui::getHitLocationSections));
        addTab(ModuleTab.of("stats", "Stats", ui::getCombatSections));
        addTab(ModuleTab.of("combat", "Combat", ui::getSpecialSections));
        addTab(ModuleTab.of("damage", "Damage Types", ui::getDamageTypeSections));
        addTab(ModuleTab.of("durability", "Durability", ui::getDurabilitySections));
        addTab(ModuleTab.of("tooling", "Tool Rules", ui::getToolSections));
        if (variant == WeaponVariant.MACE) {
            addTab(ModuleTab.of("mace", "Mace", ui::getMaceSections));
        }
        if (variant == WeaponVariant.TRIDENT) {
            addTab(ModuleTab.of("trident", "Trident", ui::getTridentSections));
        }
        addTab(ModuleTab.of("debug", "Debug", () -> ui.getDebugSections(item)));

        // Sync UI with stats after components are created
        ui.updateSlidersFromStats();
        variants.updateVariantComponents(variant);
    }

    /**
     * Apply external stats (preset/import) while keeping undo/history in sync.
     */
    public void applyExternalStats(WeaponStats newStats, String reason) {
        if (newStats == null) {
            return;
        }
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
        WeaponStats stats = core.getStats();
        WeaponStats originalStats = core.getOriginalStats();

        // Build NBT from current stats
        CompoundTag statsTag = new CompoundTag();
        CompoundTag weaponStats = new CompoundTag();
        com.devmod.config.WeaponConfigManager.clampStats(stats);
        stats.save(weaponStats);
        CompoundTag delta = core.buildDelta(originalStats, stats);
        if (!delta.isEmpty()) {
            statsTag.put(WeaponModuleCore.DELTA_KEY, delta);
        }
        variants.saveVariantData(weaponStats, variant);
        DevMod.LOGGER.info("[Editor][Weapon][BuildPayload] item={} global={} dmg={} spd={} reach={} bonus={} pen={} shred={}",
            item.getItem(), isGlobal, stats.attackDamage, stats.attackSpeed, stats.attackReach,
            stats.baseDamageBonus, stats.armorPenetration, stats.armorShred);
        // Send both legacy and component-friendly payloads
        statsTag.put(WeaponModuleCore.NBT_KEY, Objects.requireNonNull(weaponStats.copy()));
        statsTag.put("weapon_stats_component", Objects.requireNonNull(weaponStats));

        return new com.devmod.network.WeaponStatsPayload(Objects.requireNonNull(item, "item cannot be null"), statsTag, isGlobal);
    }

    @Override
    public void applyPreview() {
        WeaponStats stats = core.getStats();
        // Prepare a preview copy of the item and set CustomData on the copy only.
        try {
            ItemStack copy = item.copy();
            CompoundTag variantTag = new CompoundTag();
            variants.saveVariantData(variantTag, variant);
            DevMod.LOGGER.info("[Editor][Weapon][ApplyPreview] item={} dmg={} spd={} reach={} bonus={} pen={} shred={}",
                item.getItem(), stats.attackDamage, stats.attackSpeed, stats.attackReach,
                stats.baseDamageBonus, stats.armorPenetration, stats.armorShred);
            // Apply full stack edits to the preview copy (CustomData + component + attributes)
            com.devmod.config.WeaponConfigManager.setSpecificStats(copy, stats.copy(), variantTag);
            // Reload from the written stack to pick up server-side clamps/normalization
            WeaponStats applied = com.devmod.config.WeaponConfigManager.getStats(copy).copy();
            core.setStats(applied.copy());
            ui.updateSlidersFromStats();
            // Store the preview copy in the base class so UI can render it without mutating the real item
            setPreviewItem(copy);
            // Also advance our working item/original stats so subsequent edits start from the latest applied state
            this.item = copy;
            core.setOriginalStats(applied.copy());
            variants.snapshotVariantOriginals();
        } catch (Exception ignored) {
            // Best-effort: if preview cannot be created, ensure no preview remains
            clearPreview();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTERS & STATE
    // ═══════════════════════════════════════════════════════════════

    public WeaponStats getStats() {
        return core.getStats();
    }

    public WeaponStats getOriginalStats() {
        return core.getOriginalStats();
    }

    public WeaponVariant getVariant() {
        return variant;
    }

    public void setVariant(WeaponVariant variant) {
        this.variant = variant == null ? WeaponVariant.STANDARD : variant;
    }

    /**
     * Reset all stats to original values loaded from the item.
     */
    public void resetToOriginal() {
        core.setStats(core.getOriginalStats().copy());
        variants.resetToOriginal(variant);
        ui.updateSlidersFromStats();
        variants.updateVariantComponents(variant);
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
