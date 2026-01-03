package com.devmod.client.ui.editor.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.world.item.ItemStack;

import com.devmod.client.ui.editor.EditorSection;
import com.devmod.client.ui.editor.EditorStartTab;
import com.devmod.client.ui.editor.components.EditorSlider;
import com.devmod.client.ui.editor.components.EditorToggle;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.editor.sections.AttributeListSection;
import com.devmod.client.ui.editor.sections.EnchantmentListSection;
import com.devmod.client.ui.editor.sections.InfoListSection;
import com.devmod.client.ui.editor.sections.ModuleCardSection;
import com.devmod.client.ui.editor.sections.SliderSectionAdapter;
import com.devmod.client.ui.editor.sections.ToggleSectionAdapter;

/**
 * UI component management for GeneralModule.
 * Handles component creation and section building.
 */
public class GeneralModuleUI {

    private final GeneralModule module;
    private final GeneralModuleCore core;

    // UI Components - Quick Settings Tab
    private @Nullable EditorSlider stackSizeSlider;
    private @Nullable EditorToggle unbreakableToggle;
    private @Nullable EditorSlider durabilitySlider;
    private @Nullable EditorSlider repairCostSlider;

    public GeneralModuleUI(GeneralModule module, GeneralModuleCore core) {
        this.module = Objects.requireNonNull(module, "module cannot be null");
        this.core = Objects.requireNonNull(core, "core cannot be null");
    }

    // ═══════════════════════════════════════════════════════════════
    // COMPONENT CREATION
    // ═══════════════════════════════════════════════════════════════

    public void createQuickSettingsComponents(ItemStack item) {
        int vanillaStackSize = item.getItem().getDefaultMaxStackSize();

        stackSizeSlider = new EditorSlider("stackSize", "Stack Size", 1, 99, item.getMaxStackSize())
            .step(1)
            .format("%.0f")
            .suffix(" items")
            .trackColor(DesignTokens.SliderColors.NEUTRAL)
            .showInput(true)
            .info("Maximum items per inventory slot. Vanilla default: " + vanillaStackSize + ". Most items cap at 64, but modded items can go higher.")
            .onChange(value -> module.markDirty("stack size"));

        unbreakableToggle = new EditorToggle("unbreakable", "Unbreakable", false)
            .tooltip("When enabled, item never loses durability. Sets minecraft:unbreakable component.")
            .onChange(value -> module.markDirty("unbreakable"));

        if (item.isDamageableItem()) {
            int maxDurability = item.getMaxDamage();
            int currentDurability = maxDurability - item.getDamageValue();

            durabilitySlider = new EditorSlider("durability", "Current Durability", 0, maxDurability, currentDurability)
                .step(1)
                .format("%.0f")
                .suffix(" pts")
                .trackColor(DesignTokens.SliderColors.DURABILITY)
                .showInput(true)
                .info("Current durability points remaining. 0 = broken. Max: " + maxDurability + ". Unbreaking enchant gives chance to not consume durability.")
                .onChange(value -> module.markDirty("durability"));

            repairCostSlider = new EditorSlider("repairCost", "Repair Cost", 0, 40, 0)
                .step(1)
                .format("%.0f")
                .suffix(" XP levels")
                .trackColor(DesignTokens.SliderColors.NEUTRAL)
                .showInput(true)
                .info("XP level cost to repair/rename in anvil. Increases each repair. Max 39 before 'Too Expensive'. Can be reset by renaming.")
                .onChange(value -> module.markDirty("repair cost"));
        }
    }

    public void updateSlidersFromItem(ItemStack item) {
        var stackSlider = stackSizeSlider;
        if (stackSlider != null) {
            stackSlider.setValue(item.getMaxStackSize());
        }
        var durSlider = durabilitySlider;
        if (durSlider != null && item.isDamageableItem()) {
            durSlider.setValue(item.getMaxDamage() - item.getDamageValue());
            durSlider.setMax(item.getMaxDamage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SECTION BUILDERS
    // ═══════════════════════════════════════════════════════════════

    public List<EditorSection> getOverviewSections(ItemStack item, Consumer<EditorStartTab> switchCallback) {
        List<EditorSection> sections = new ArrayList<>();

        sections.add(new InfoListSection(
            "overview-header",
            "Item Editing",
            List.of(
                "Select a module below to edit specific properties.",
                "Current item: " + item.getHoverName().getString()
            )
        ));

        // Use getAvailableModules() to determine which module cards to show
        List<EditorStartTab> availableModules = core.getAvailableModules(item);
        boolean hasSpecializedModule = false;

        for (EditorStartTab tab : availableModules) {
            switch (tab) {
                case WEAPON -> {
                    sections.add(ModuleCardSection.weapon(switchCallback));
                    hasSpecializedModule = true;
                }
                case ARMOR -> {
                    sections.add(ModuleCardSection.armor(switchCallback));
                    hasSpecializedModule = true;
                }
                case RECIPE -> sections.add(ModuleCardSection.recipe(switchCallback));
                case USABLE -> {
                    sections.add(ModuleCardSection.usable(switchCallback));
                    hasSpecializedModule = true;
                }
                case FOOD -> {
                    sections.add(ModuleCardSection.food(switchCallback));
                    hasSpecializedModule = true;
                }
                case FUEL -> {
                    sections.add(ModuleCardSection.fuel(switchCallback));
                    hasSpecializedModule = true;
                }
                case GENERAL -> {
                    // GENERAL is the current module, no card needed
                }
            }
        }

        // Show basic item message only if no specialized modules
        if (!hasSpecializedModule) {
            sections.add(new InfoListSection(
                "overview-basic",
                "Basic Item",
                List.of(
                    "This item has no specialized modules.",
                    "Use Quick Settings for basic properties."
                )
            ));
        }

        return sections;
    }

    public List<EditorSection> getQuickSettingsSections(ItemStack item) {
        List<EditorSection> sections = new ArrayList<>();

        sections.add(new SliderSectionAdapter(Objects.requireNonNull(stackSizeSlider, "stackSizeSlider")));
        sections.add(new ToggleSectionAdapter(Objects.requireNonNull(unbreakableToggle, "unbreakableToggle")));

        if (item.isDamageableItem() && durabilitySlider != null) {
            sections.add(new SliderSectionAdapter(durabilitySlider));
            sections.add(new SliderSectionAdapter(Objects.requireNonNull(repairCostSlider, "repairCostSlider")));
        }

        return sections;
    }

    public List<EditorSection> getStatusSections(ItemStack item) {
        List<EditorSection> sections = new ArrayList<>();

        if (core.isWeaponItem(item)) {
            sections.add(core.buildWeaponSummary(item));
        }

        if (core.isArmorItem(item)) {
            sections.add(core.buildArmorSummary(item));
        }

        sections.add(core.buildGeneralSummary(item));

        return sections;
    }

    public List<EditorSection> getInfoSections(ItemStack item) {
        List<String> info = core.extractItemInfo(item);
        return List.of(new InfoListSection("info", "Item Information", info));
    }

    public List<EditorSection> getAttributesSections(ItemStack item) {
        return List.of(new AttributeListSection(
            "attributes",
            "Attribute Modifiers",
            item,
            attr -> module.markDirty(attr)
        ));
    }

    public List<EditorSection> getEnchantmentsSections(ItemStack item) {
        return List.of(new EnchantmentListSection(
            "enchantments",
            "Enchantments",
            item,
            ench -> module.markDirty(ench)
        ));
    }
}
