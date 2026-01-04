package com.devmod.client.ui.editor.modules;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.devmod.client.ui.editor.AbstractEditorModule;
import com.devmod.client.ui.editor.EditorStartTab;
import com.devmod.client.ui.editor.ModuleTab;

/**
 * General editor module - Navigation Hub.
 * Orchestrates Core (data) and UI (components) delegates.
 */
public class GeneralModule extends AbstractEditorModule {

    // ═══════════════════════════════════════════════════════════════
    // MODULE SWITCHING
    // ═══════════════════════════════════════════════════════════════

    private @Nullable Consumer<EditorStartTab> moduleSwitchCallback;

    // ═══════════════════════════════════════════════════════════════
    // DELEGATES (lazy initialized to avoid this-escape)
    // ═══════════════════════════════════════════════════════════════

    private @Nullable GeneralModuleCore core;
    private @Nullable GeneralModuleUI ui;
    private boolean delegatesInitialized = false;

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public GeneralModule() {
        super("general", "Navigation Hub");
    }

    private void ensureDelegates() {
        if (!delegatesInitialized) {
            this.core = new GeneralModuleCore();
            this.ui = new GeneralModuleUI(this, core);
            delegatesInitialized = true;
        }
    }

    private GeneralModuleCore requireCore() {
        ensureDelegates();
        return Objects.requireNonNull(core, "core");
    }

    private GeneralModuleUI requireUi() {
        ensureDelegates();
        return Objects.requireNonNull(ui, "ui");
    }

    // ═══════════════════════════════════════════════════════════════
    // MODULE SWITCHING SUPPORT
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void setModuleSwitchCallback(Consumer<EditorStartTab> callback) {
        this.moduleSwitchCallback = callback;
    }

    private void switchModule(EditorStartTab tab) {
        Consumer<EditorStartTab> callback = moduleSwitchCallback;
        if (callback != null) {
            callback.accept(tab);
        }
    }

    @Override
    public List<EditorStartTab> getAvailableModules() {
        return requireCore().getAvailableModules(item);
    }

    // ═══════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void onItemSet() {
        requireUi().updateSlidersFromItem(item);
    }

    @Override
    protected void initializeTabs() {
        tabs.clear();
        GeneralModuleCore coreRef = requireCore();
        GeneralModuleUI uiRef = requireUi();

        uiRef.createQuickSettingsComponents(item);

        addTab(ModuleTab.of("overview", "Overview", () -> uiRef.getOverviewSections(item, this::switchModule)));
        addTab(ModuleTab.of("settings", "Quick Settings", () -> uiRef.getQuickSettingsSections(item)));
        addTab(ModuleTab.of("status", "Status", () -> uiRef.getStatusSections(item)));
        addTab(ModuleTab.of("info", "Info", () -> uiRef.getInfoSections(item)));
        addTab(ModuleTab.of("attributes", "Attributes", () -> uiRef.getAttributesSections(item)));

        if (item.isEnchantable() || coreRef.hasEnchantments(item)) {
            addTab(ModuleTab.of("enchantments", "Enchantments", () -> uiRef.getEnchantmentsSections(item)));
        }

        uiRef.updateSlidersFromItem(item);
    }

    // ═══════════════════════════════════════════════════════════════
    // NETWORK
    // ═══════════════════════════════════════════════════════════════

    @Override
    public @Nullable CustomPacketPayload buildPayload(boolean isGlobal) {
        return null;
    }

    @Override
    public void applyPreview() {
        GeneralModuleUI uiRef = requireUi();
        try {
            var copy = item.copy();
            uiRef.applyQuickSettingsToItem(copy);
            setPreviewItem(copy);
            this.item = copy;
            withDirtyTrackingDisabled(() -> uiRef.updateSlidersFromItem(copy));
        } catch (Exception ignored) {
            clearPreview();
        }
    }
}
