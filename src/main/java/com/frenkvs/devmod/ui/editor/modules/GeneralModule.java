package com.frenkvs.devmod.ui.editor.modules;

import com.frenkvs.devmod.ui.editor.AbstractEditorModule;
import com.frenkvs.devmod.ui.editor.EditorSection;
import com.frenkvs.devmod.ui.editor.EditorStartTab;
import com.frenkvs.devmod.ui.editor.ModuleTab;
import com.frenkvs.devmod.ui.editor.components.EditorSlider;
import com.frenkvs.devmod.ui.editor.components.EditorToggle;
import com.frenkvs.devmod.ui.editor.core.UIConstants;
import com.frenkvs.devmod.ui.editor.sections.AttributeListSection;
import com.frenkvs.devmod.ui.editor.sections.EnchantmentListSection;
import com.frenkvs.devmod.ui.editor.sections.InfoListSection;
import com.frenkvs.devmod.ui.editor.sections.ModuleCardSection;
import com.frenkvs.devmod.ui.editor.sections.ModuleSummarySection;
import com.frenkvs.devmod.ui.editor.sections.SliderSectionAdapter;
import com.frenkvs.devmod.ui.editor.sections.ToggleSectionAdapter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SpyglassItem;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * General/Navigation Hub module for the Item Editor.
 *
 * <p>Serves as the central navigation point when opening items that could be
 * edited by multiple specialized modules. Provides:</p>
 * <ul>
 *   <li><b>Overview Tab</b> - Navigation cards to switch to specialized modules</li>
 *   <li><b>Quick Settings Tab</b> - Basic item properties (stack size, durability)</li>
 *   <li><b>Status Tab</b> - Cross-module summaries showing key stats</li>
 *   <li><b>Info Tab</b> - Read-only item metadata</li>
 * </ul>
 *
 * @see EDITOR_DESIGN_SYSTEM.md Section 27 (Navigation Hub)
 */

public class GeneralModule extends AbstractEditorModule {

    // ═══════════════════════════════════════════════════════════════
    // MODULE SWITCHING
    // ═══════════════════════════════════════════════════════════════

    private Consumer<EditorStartTab> moduleSwitchCallback;

    // ═══════════════════════════════════════════════════════════════
    // UI COMPONENTS - Quick Settings Tab
    // ═══════════════════════════════════════════════════════════════

    private EditorSlider stackSizeSlider;
    private EditorToggle unbreakableToggle;
    private EditorSlider durabilitySlider;
    private EditorSlider repairCostSlider;

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public GeneralModule() {
        super("general", "Navigation Hub");
    }

    // ═══════════════════════════════════════════════════════════════
    // MODULE SWITCHING SUPPORT
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void setModuleSwitchCallback(Consumer<EditorStartTab> callback) {
        this.moduleSwitchCallback = callback;
    }

    @Override
    public List<EditorStartTab> getAvailableModules() {
        List<EditorStartTab> modules = new ArrayList<>();

        if (isWeaponItem()) {
            modules.add(EditorStartTab.WEAPON);
        }
        if (isArmorItem()) {
            modules.add(EditorStartTab.ARMOR);
        }
        if (hasRecipe()) {
            modules.add(EditorStartTab.RECIPE);
        }
        if (isUsableItem()) {
            modules.add(EditorStartTab.USABLE);
        }
        if (isFoodItem()) {
            modules.add(EditorStartTab.FOOD);
        }
        if (isFuelItem()) {
            modules.add(EditorStartTab.FUEL);
        }
        // General is always available (we're in it)
        modules.add(EditorStartTab.GENERAL);

        return modules;
    }

    // ═══════════════════════════════════════════════════════════════
    // ITEM TYPE DETECTION
    // ═══════════════════════════════════════════════════════════════

    private boolean isWeaponItem() {
        return item != null && (
            item.getItem() instanceof SwordItem ||
            item.getItem() instanceof TieredItem ||
            item.getItem() instanceof ProjectileWeaponItem
        );
    }

    private boolean isArmorItem() {
        return item != null && item.getItem() instanceof ArmorItem;
    }

    private boolean hasRecipe() {
        // For now, assume most items can have recipes
        // In future could check RecipeManager
        return item != null;
    }

    private boolean isUsableItem() {
        if (item == null) return false;
        Item i = item.getItem();
        return i instanceof SnowballItem || i instanceof EggItem ||
               i instanceof EnderpearlItem || i instanceof ThrowablePotionItem ||
               i instanceof BowItem || i instanceof CrossbowItem ||
               i instanceof ShieldItem || i instanceof SpyglassItem ||
               i instanceof BrushItem ||
               item.has(DataComponents.FOOD); // Food items are also "usable"
    }

    private boolean isFoodItem() {
        return item != null && item.has(DataComponents.FOOD);
    }

    private boolean isFuelItem() {
        if (item == null) return false;
        // Use FuelConfigManager which supports both vanilla and modded items
        return com.frenkvs.devmod.FuelConfigManager.isFuel(item);
    }

    private boolean hasEnchantments() {
        if (item == null) return false;
        var enchants = item.getOrDefault(
            java.util.Objects.requireNonNull(DataComponents.ENCHANTMENTS),
            java.util.Objects.requireNonNull(ItemEnchantments.EMPTY)
        );
        return !enchants.isEmpty();
    }

    // ═══════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void onItemSet() {
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
        createQuickSettingsComponents();

        // Tab 1: Overview - Navigation Hub
        addTab(ModuleTab.of("overview", "Overview", this::getOverviewSections));

        // Tab 2: Quick Settings - Basic properties
        addTab(ModuleTab.of("settings", "Quick Settings", this::getQuickSettingsSections));

        // Tab 3: Status - Cross-module summaries
        addTab(ModuleTab.of("status", "Status", this::getStatusSections));

        // Tab 4: Info - Read-only metadata
        addTab(ModuleTab.of("info", "Info", this::getInfoSections));

        // Tab 5: Attributes - Item attribute modifiers
        addTab(ModuleTab.of("attributes", "Attributes", this::getAttributesSections));

        // Tab 6: Enchantments - Item enchantments
        if (item.isEnchantable() || hasEnchantments()) {
            addTab(ModuleTab.of("enchantments", "Enchantments", this::getEnchantmentsSections));
        }

        // Sync UI with current item values
        updateSlidersFromItem();
    }

    // ═══════════════════════════════════════════════════════════════
    // TAB 1: OVERVIEW (Navigation Hub)
    // ═══════════════════════════════════════════════════════════════

    private List<EditorSection> getOverviewSections() {
        List<EditorSection> sections = new ArrayList<>();

        // Header section explaining the hub
        sections.add(new InfoListSection(
            "overview-header",
            "Item Editing",
            List.of(
                "Select a module below to edit specific properties.",
                "Current item: " + item.getHoverName().getString()
            )
        ));

        // Module cards based on item capabilities
        if (isWeaponItem()) {
            sections.add(ModuleCardSection.weapon(
                tab -> { if (moduleSwitchCallback != null) moduleSwitchCallback.accept(tab); }
            ));
        }

        if (isArmorItem()) {
            sections.add(ModuleCardSection.armor(
                tab -> { if (moduleSwitchCallback != null) moduleSwitchCallback.accept(tab); }
            ));
        }

        // Recipe module (available for most items)
        sections.add(ModuleCardSection.recipe(
            tab -> { if (moduleSwitchCallback != null) moduleSwitchCallback.accept(tab); }
        ));

        // Usable module (for throwables, items with cooldown, etc.)
        if (isUsableItem()) {
            sections.add(ModuleCardSection.usable(
                tab -> { if (moduleSwitchCallback != null) moduleSwitchCallback.accept(tab); }
            ));
        }

        // Food module (for edible items)
        if (isFoodItem()) {
            sections.add(ModuleCardSection.food(
                tab -> { if (moduleSwitchCallback != null) moduleSwitchCallback.accept(tab); }
            ));
        }

        // Fuel module (for burnable items)
        if (isFuelItem()) {
            sections.add(ModuleCardSection.fuel(
                tab -> { if (moduleSwitchCallback != null) moduleSwitchCallback.accept(tab); }
            ));
        }

        // If no specialized modules available, show a message
        if (!isWeaponItem() && !isArmorItem() && !isUsableItem() && !isFoodItem() && !isFuelItem()) {
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

    // ═══════════════════════════════════════════════════════════════
    // TAB 2: QUICK SETTINGS
    // ═══════════════════════════════════════════════════════════════

    private void createQuickSettingsComponents() {
        int vanillaStackSize = item.getItem().getDefaultMaxStackSize();

        stackSizeSlider = new EditorSlider("stackSize", "Stack Size", 1, 99, item.getMaxStackSize())
            .step(1)
            .format("%.0f")
            .suffix(" items")
            .trackColor(UIConstants.SliderColors.NEUTRAL)
            .showInput(true)
            .info("Maximum items per inventory slot. Vanilla default: " + vanillaStackSize + ". Most items cap at 64, but modded items can go higher.")
            .onChange(value -> markDirty("stack size"));

        unbreakableToggle = new EditorToggle("unbreakable", "Unbreakable", false)
            .tooltip("When enabled, item never loses durability. Sets minecraft:unbreakable component.")
            .onChange(value -> markDirty("unbreakable"));

        if (item.isDamageableItem()) {
            int maxDurability = item.getMaxDamage();
            int currentDurability = maxDurability - item.getDamageValue();

            durabilitySlider = new EditorSlider("durability", "Current Durability", 0, maxDurability, currentDurability)
                .step(1)
                .format("%.0f")
                .suffix(" pts")
                .trackColor(UIConstants.SliderColors.DURABILITY)
                .showInput(true)
                .info("Current durability points remaining. 0 = broken. Max: " + maxDurability + ". Unbreaking enchant gives chance to not consume durability.")
                .onChange(value -> markDirty("durability"));

            repairCostSlider = new EditorSlider("repairCost", "Repair Cost", 0, 40, 0)
                .step(1)
                .format("%.0f")
                .suffix(" XP levels")
                .trackColor(UIConstants.SliderColors.NEUTRAL)
                .showInput(true)
                .info("XP level cost to repair/rename in anvil. Increases each repair. Max 39 before 'Too Expensive'. Can be reset by renaming.")
                .onChange(value -> markDirty("repair cost"));
        }
    }

    private List<EditorSection> getQuickSettingsSections() {
        List<EditorSection> sections = new ArrayList<>();

        sections.add(new SliderSectionAdapter(stackSizeSlider));
        sections.add(new ToggleSectionAdapter(unbreakableToggle));

        if (item.isDamageableItem() && durabilitySlider != null) {
            sections.add(new SliderSectionAdapter(durabilitySlider));
            sections.add(new SliderSectionAdapter(repairCostSlider));
        }

        return sections;
    }

    // ═══════════════════════════════════════════════════════════════
    // TAB 3: STATUS (Cross-Module Summaries)
    // ═══════════════════════════════════════════════════════════════

    private List<EditorSection> getStatusSections() {
        List<EditorSection> sections = new ArrayList<>();

        // Weapon stats summary (if applicable)
        if (isWeaponItem()) {
            sections.add(buildWeaponSummary());
        }

        // Armor stats summary (if applicable)
        if (isArmorItem()) {
            sections.add(buildArmorSummary());
        }

        // General item stats (always shown)
        sections.add(buildGeneralSummary());

        return sections;
    }

    private ModuleSummarySection buildWeaponSummary() {
        // Extract weapon stats from item attributes
        double damage = 0;
        double speed = 0;

        // Try to get weapon stats from attributes
        if (item.getItem() instanceof SwordItem sword) {
            damage = sword.getTier().getAttackDamageBonus() + 1; // Base damage
            speed = -2.4; // Default sword speed modifier
        } else if (item.getItem() instanceof TieredItem tiered) {
            damage = tiered.getTier().getAttackDamageBonus();
        }

        return ModuleSummarySection.builder("summary-weapon", "Weapon Stats")
            .accentColor(UIConstants.Accent.RED())
            .addStat("Base Damage", damage, "%.1f", UIConstants.Accent.RED(), "VAN")
            .addStat("Attack Speed", 4.0 + speed, "%.2f/s", UIConstants.Text.PRIMARY(), "VAN")
            .addStat("DPS", damage * (4.0 + speed), "%.1f", UIConstants.Accent.ORANGE(), null)
            .build();
    }

    private ModuleSummarySection buildArmorSummary() {
        double defense = 0;
        double toughness = 0;

        if (item.getItem() instanceof ArmorItem armor) {
            defense = armor.getDefense();
            toughness = armor.getToughness();
        }

        return ModuleSummarySection.builder("summary-armor", "Armor Stats")
            .accentColor(UIConstants.Accent.BLUE())
            .addStat("Defense", defense, "%.0f", UIConstants.Accent.BLUE(), "VAN")
            .addStat("Toughness", toughness, "%.1f", UIConstants.Text.PRIMARY(), "VAN")
            .build();
    }

    private ModuleSummarySection buildGeneralSummary() {
        ModuleSummarySection.Builder builder = ModuleSummarySection.builder("summary-general", "Item Properties")
            .accentColor(UIConstants.Accent.INFO());

        builder.addStat("Max Stack", item.getMaxStackSize(), "%.0f", UIConstants.Text.PRIMARY(), null);

        if (item.isDamageableItem()) {
            int current = item.getMaxDamage() - item.getDamageValue();
            int max = item.getMaxDamage();
            double percent = (double) current / max * 100;
            int durabilityColor = percent > 50 ? UIConstants.Accent.GREEN()
                                : percent > 20 ? UIConstants.Accent.ORANGE()
                                : UIConstants.Accent.RED();
            builder.addStat("Durability", percent, "%.0f%%", durabilityColor, null);
        }

        return builder.build();
    }

    // ═══════════════════════════════════════════════════════════════
    // TAB 4: INFO (Read-Only Metadata)
    // ═══════════════════════════════════════════════════════════════

    private List<EditorSection> getInfoSections() {
        List<String> info = new ArrayList<>();

        info.add("Name: " + item.getHoverName().getString());
        info.add("Type: " + item.getItem().getClass().getSimpleName());
        info.add("Registry: " + BuiltInRegistries.ITEM.getKey(item.getItem()));
        info.add("Rarity: " + item.getRarity().name());
        info.add("Max Stack: " + item.getMaxStackSize());

        if (item.isDamageableItem()) {
            info.add("Max Durability: " + item.getMaxDamage());
            info.add("Current Damage: " + item.getDamageValue());
        } else {
            info.add("Not Damageable");
        }

        // Capabilities
        List<String> capabilities = new ArrayList<>();
        if (isWeaponItem()) capabilities.add("Weapon");
        if (isArmorItem()) capabilities.add("Armor");
        if (item.isEnchantable()) capabilities.add("Enchantable");
        if (item.isDamageableItem()) capabilities.add("Damageable");
        if (!capabilities.isEmpty()) {
            info.add("Capabilities: " + String.join(", ", capabilities));
        }

        return List.of(new InfoListSection("info", "Item Information", info));
    }

    // ═══════════════════════════════════════════════════════════════
    // TAB 5: ATTRIBUTES
    // ═══════════════════════════════════════════════════════════════

    private List<EditorSection> getAttributesSections() {
        return List.of(new AttributeListSection(
            "attributes",
            "Attribute Modifiers",
            item,
            attr -> markDirty(attr)
        ));
    }

    // ═══════════════════════════════════════════════════════════════
    // TAB 6: ENCHANTMENTS
    // ═══════════════════════════════════════════════════════════════

    private List<EditorSection> getEnchantmentsSections() {
        return List.of(new EnchantmentListSection(
            "enchantments",
            "Enchantments",
            item,
            ench -> markDirty(ench)
        ));
    }

    // ═══════════════════════════════════════════════════════════════
    // NETWORK
    // ═══════════════════════════════════════════════════════════════

    @Override
    public CustomPacketPayload buildPayload(boolean isGlobal) {
        // GeneralModule uses generic item modification
        // Future: Could build a GenericItemPayload for stack size, durability, etc.
        return null;
    }
}
