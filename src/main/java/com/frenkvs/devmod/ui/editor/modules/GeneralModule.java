package com.frenkvs.devmod.ui.editor.modules;

import com.frenkvs.devmod.ui.editor.AbstractEditorModule;
import com.frenkvs.devmod.ui.editor.EditorSection;
import com.frenkvs.devmod.ui.editor.ModuleTab;
import com.frenkvs.devmod.ui.editor.components.EditorSlider;
import com.frenkvs.devmod.ui.editor.components.EditorToggle;
import com.frenkvs.devmod.ui.editor.core.UIConstants;
import com.frenkvs.devmod.ui.editor.sections.InfoListSection;
import com.frenkvs.devmod.ui.editor.sections.SliderSectionAdapter;
import com.frenkvs.devmod.ui.editor.sections.ToggleSectionAdapter;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * General/fallback editor module for items that don't match specific types.
 * Provides basic editing capabilities for common item properties.
 *
 * @see EDITOR_DESIGN_SYSTEM.md Section 3.2
 */
public class GeneralModule extends AbstractEditorModule {

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - General Tab
    // ═══════════════════════════════════════════════════════════════

    private EditorSlider stackSizeSlider;
    private EditorToggle unbreakableToggle;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Durability Tab
    // ═══════════════════════════════════════════════════════════════

    private EditorSlider durabilitySlider;
    private EditorSlider repairCostSlider;

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public GeneralModule() {
        super("general", "Item Editor");
    }

    // ═══════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void onItemSet() {
        // Load basic item properties
        updateSlidersFromItem();
    }

    private void updateSlidersFromItem() {
        if (stackSizeSlider != null) {
            stackSizeSlider.setValue(item.getMaxStackSize());
        }
        if (durabilitySlider != null && item.isDamageableItem()) {
            durabilitySlider.setValue(item.getMaxDamage() - item.getDamageValue());
            durabilitySlider.setMax(item.getMaxDamage());
        }
    }

    @Override
    protected void initializeTabs() {
        tabs.clear();

        // Create UI components
        createGeneralComponents();
        createDurabilityComponents();

        // Add tabs
        addTab(ModuleTab.of("general", "General", this::getGeneralSections));

        // Only add durability tab if item is damageable
        if (item.isDamageableItem()) {
            addTab(ModuleTab.of("durability", "Durability", this::getDurabilitySections));
        }

        // Add info tab
        addTab(ModuleTab.of("info", "Info", this::getInfoSections));

        // Sync UI with current item values
        updateSlidersFromItem();
    }

    // ═══════════════════════════════════════════════════════════════
    // GENERAL TAB
    // ═══════════════════════════════════════════════════════════════

    private void createGeneralComponents() {
        stackSizeSlider = new EditorSlider("stackSize", "Stack Size", 1, 64, item.getMaxStackSize())
            .step(1)
            .format("%.0f")
            .trackColor(UIConstants.SliderColors.NEUTRAL)
            .info("Maximum items per inventory slot. Vanilla max is 64. Some items (tools, armor) stack to 1 by default.")
            .onChange(value -> {
                // Stack size is read-only in vanilla, but could be modded
                markDirty("stack size");
            });

        unbreakableToggle = new EditorToggle("unbreakable", "Unbreakable", false)
            .tooltip("When enabled, item never loses durability. Sets minecraft:unbreakable component.")
            .onChange(value -> markDirty("unbreakable"));
    }

    private List<EditorSection> getGeneralSections() {
        return List.of(
            new SliderSectionAdapter(stackSizeSlider),
            new ToggleSectionAdapter(unbreakableToggle)
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // DURABILITY TAB
    // ═══════════════════════════════════════════════════════════════

    private void createDurabilityComponents() {
        int maxDurability = item.isDamageableItem() ? item.getMaxDamage() : 100;
        int currentDurability = item.isDamageableItem() ? (item.getMaxDamage() - item.getDamageValue()) : 100;

        durabilitySlider = new EditorSlider("durability", "Current Durability", 0, maxDurability, currentDurability)
            .step(1)
            .format("%.0f")
            .trackColor(UIConstants.SliderColors.DURABILITY)
            .info("Current durability points. 0 = broken. Unbreaking enchant gives chance to not consume durability per use.")
            .onChange(value -> markDirty("durability"));

        repairCostSlider = new EditorSlider("repairCost", "Repair Cost", 0, 40, 0)
            .step(1)
            .format("%.0f")
            .trackColor(UIConstants.SliderColors.NEUTRAL)
            .info("XP level cost to repair/rename in anvil. Increases each repair. Max 39 before 'Too Expensive'. Reset by renaming.")
            .onChange(value -> markDirty("repair cost"));
    }

    private List<EditorSection> getDurabilitySections() {
        return List.of(
            new SliderSectionAdapter(durabilitySlider),
            new SliderSectionAdapter(repairCostSlider)
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // INFO TAB
    // ═══════════════════════════════════════════════════════════════

    private List<EditorSection> getInfoSections() {
        // Info tab shows read-only information about the item
        return List.of(new InfoListSection(
            "info",
            "Item Information",
            List.of(
                "Item: " + item.getHoverName().getString(),
                "Type: " + item.getItem().getClass().getSimpleName(),
                "Max Stack: " + item.getMaxStackSize(),
                item.isDamageableItem() ? "Max Durability: " + item.getMaxDamage() : "Not Damageable",
                "Rarity: " + item.getRarity().name()
            )
        ));
    }

    // ═══════════════════════════════════════════════════════════════
    // NETWORK
    // ═══════════════════════════════════════════════════════════════

    @Override
    public CustomPacketPayload buildPayload(boolean isGlobal) {
        // GeneralModule doesn't have a specific payload type
        // It uses generic item modification
        return null;
    }

    // ═══════════════════════════════════════════════════════════════
    // ADAPTERS
    // ═══════════════════════════════════════════════════════════════

}
