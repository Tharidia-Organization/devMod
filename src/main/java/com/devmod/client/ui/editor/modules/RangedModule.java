package com.devmod.client.ui.editor.modules;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.devmod.client.ui.editor.AbstractEditorModule;
import com.devmod.client.ui.editor.EditorSection;
import com.devmod.client.ui.editor.ModuleTab;
import com.devmod.client.ui.editor.RangedWeaponModule;
import com.devmod.network.RangedWeaponStatsPayload;

/**
 * Ranged weapon editor module.
 * Orchestrates Core (data) and UI (components) delegates.
 * Supports BOW, CROSSBOW, TRIDENT, and GENERIC variants.
 */
public class RangedModule extends AbstractEditorModule {

    public enum RangedVariant { BOW, CROSSBOW, TRIDENT, GENERIC }

    private RangedVariant variant = RangedVariant.GENERIC;

    // Delegate classes (lazy initialized to avoid this-escape)
    private @Nullable RangedModuleCore core;
    private @Nullable RangedModuleUI ui;
    private boolean delegatesInitialized = false;

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTORS
    // ═══════════════════════════════════════════════════════════════

    public RangedModule(RangedVariant variant) {
        super("ranged", "Ranged Weapon Editor");
        this.variant = variant == null ? RangedVariant.GENERIC : variant;
    }

    /** Ensures delegates are initialized (lazy init to avoid this-escape). */
    private void ensureDelegates() {
        if (!delegatesInitialized) {
            this.core = new RangedModuleCore();
            this.ui = new RangedModuleUI(this, core);
            delegatesInitialized = true;
        }
    }

    private RangedModuleCore requireCore() {
        ensureDelegates();
        return Objects.requireNonNull(core, "core");
    }

    private RangedModuleUI requireUi() {
        ensureDelegates();
        return Objects.requireNonNull(ui, "ui");
    }

    // ═══════════════════════════════════════════════════════════════
    // DELEGATE ACCESSORS
    // ═══════════════════════════════════════════════════════════════

    public RangedModuleCore getCore() {
        return requireCore();
    }

    public RangedModuleUI getUI() {
        return requireUi();
    }

    // ═══════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void onItemSet() {
        RangedModuleCore core = requireCore();
        RangedModuleUI ui = requireUi();

        // Auto-detect variant from item type
        variant = core.detectVariant(item);

        // Load stats from item
        core.loadStatsFromItem(item);

        // Update UI components
        ui.updateComponentsFromStats();
    }

    @Override
    protected void initializeTabs() {
        RangedModuleUI ui = requireUi();
        tabs.clear();

        // Create UI components based on variant
        ui.createAllComponents(variant);

        // Add tabs
        addTab(ModuleTab.of(getVariantTabId(), getVariantTabLabel(), ui::getMechanicsSections));
        addTab(ModuleTab.of("projectile", "Projectile", ui::getProjectileSections));
        addTab(ModuleTab.of("damage", "Damage", ui::getDamageSections));
        addTab(ModuleTab.of("ammo", "Ammo", ui::getAmmoSections));
        if (variant == RangedVariant.TRIDENT) {
            addTab(ModuleTab.of("trident", "Trident", ui::getTridentSections));
        }
        addTab(ModuleTab.of("debug", "Debug", () -> ui.getDebugSections(item)));
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
        RangedModuleCore core = requireCore();
        CompoundTag root = new CompoundTag();
        root.put("RangedStats", Objects.requireNonNull(core.buildStatsTag(), "stats tag"));
        return new RangedWeaponStatsPayload(Objects.requireNonNull(item, "item cannot be null"), root, isGlobal);
    }

    @Override
    public void applyPreview() {
        RangedModuleCore core = requireCore();
        try {
            setPreviewItem(core.applyToPreview(item));
        } catch (Exception ignored) {
            clearPreview();
        }
    }

    @Override
    public void resetToOriginal() {
        RangedModuleCore core = requireCore();
        RangedModuleUI ui = requireUi();
        core.resetToOriginal();
        ui.updateComponentsFromStats();
        clearDirty();
    }

    @Override
    public boolean hasPendingDiff() {
        return requireCore().hasPendingDiff();
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTERS & STATE
    // ═══════════════════════════════════════════════════════════════

    public RangedWeaponModule.RangedStats getStats() {
        return requireCore().getStats();
    }

    public RangedVariant getVariant() {
        return variant;
    }

    /**
     * Expose history entries to delegate classes.
     */
    @Override
    public List<String> getHistoryEntries() {
        return getRecentHistoryEntries(8);
    }

    // ═══════════════════════════════════════════════════════════════
    // VARIANT TAB HELPERS
    // ═══════════════════════════════════════════════════════════════

    private String getVariantTabLabel() {
        return switch (variant) {
            case BOW -> "Bow";
            case CROSSBOW -> "Crossbow";
            case TRIDENT -> "Trident";
            default -> "Mechanics";
        };
    }

    private String getVariantTabId() {
        return switch (variant) {
            case BOW -> "bow";
            case CROSSBOW -> "crossbow";
            case TRIDENT -> "trident";
            default -> "mechanics";
        };
    }
}
