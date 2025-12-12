package com.frenkvs.devmod;

import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.UIConstants;
import com.frenkvs.devmod.util.I18n;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Armor Editor Screen - dedicated editor for armor statistics.
 *
 * Unlike WeaponEditor (attacker-side: body part multipliers),
 * ArmorEditor handles victim-side damage reduction.
 *
 * Tabs:
 * - PROTECTION: Damage type reductions (physical, fire, magic, explosion, projectile)
 * - ATTRIBUTES: Armor bonus, toughness, knockback resistance
 * - ENCHANTS: Add/remove/modify enchantments (shared logic with WeaponEditor)
 * - DURABILITY: Set durability, unbreakable, repair cost
 * - EFFECTS: Special effects (thorns reflection)
 */
public class ArmorEditorScreen extends Screen {

    // Layout
    private static final int PANEL_WIDTH = 450;
    private static final int PANEL_HEIGHT = 400;
    private static final int TAB_HEIGHT = 24;
    private static final int PREVIEW_SIZE = 80;
    private static final int SLOT_SELECTOR_HEIGHT = 30;

    // Tabs
    private enum Tab { PROTECTION, ATTRIBUTES, ENCHANTS, DURABILITY, EFFECTS }
    private Tab currentTab = Tab.PROTECTION;

    // Armor slots
    private enum ArmorSlotType {
        HEAD(0, "Helmet", EquipmentSlot.HEAD),
        CHEST(1, "Chestplate", EquipmentSlot.CHEST),
        LEGS(2, "Leggings", EquipmentSlot.LEGS),
        FEET(3, "Boots", EquipmentSlot.FEET);

        final int index;
        final String displayName;
        final EquipmentSlot slot;

        ArmorSlotType(int index, String displayName, EquipmentSlot slot) {
            this.index = index;
            this.displayName = displayName;
            this.slot = slot;
        }
    }
    private ArmorSlotType selectedSlot = ArmorSlotType.CHEST;

    // Animation
    private float rotationAngle = 0f;

    // State
    private ItemStack currentArmorPiece = ItemStack.EMPTY;
    private boolean editGlobal = false;

    // Protection tab sliders
    private final List<ProtectionSlider> protectionSliders = new ArrayList<>();

    // Attributes tab sliders
    private final List<AttributeSlider> attributeSliders = new ArrayList<>();

    // Effects tab
    private boolean thornsEnabled = false;
    private float thornsPercent = 0f;
    private EditBox thornsPercentField;

    // Enchantments tab
    private final List<EnchantmentEntry> enchantments = new ArrayList<>();
    private int enchantScrollOffset = 0;
    private String enchantSearch = "";
    private EditBox enchantSearchBox;

    // Enchantment picker
    private boolean showEnchantPicker = false;
    private final List<Holder<Enchantment>> allAvailableEnchants = new ArrayList<>();
    private int pickerScrollOffset = 0;
    private String pickerSearch = "";
    private EditBox pickerSearchBox;

    // Durability tab
    private EditBox durabilityField;
    private EditBox repairCostField;
    private boolean isUnbreakable = false;

    // Feedback
    private String statusMessage = null;
    private int statusColor = 0;
    private int statusTicks = 0;

    // Blur control
    private int originalBlurValue = 0;

    // Tooltip tracking
    private String hoveredTooltip = null;
    private int tooltipX = 0;
    private int tooltipY = 0;

    // Helper classes
    private static class ProtectionSlider {
        final String name;
        final String tooltip;
        final int color;
        float value;
        EditBox inputField;
        boolean isDragging = false;
        float pulseAnimation = 0f;

        ProtectionSlider(String name, String tooltip, int color, float defaultVal) {
            this.name = name;
            this.tooltip = tooltip;
            this.color = color;
            this.value = defaultVal;
        }

        void triggerPulse() {
            this.pulseAnimation = 1.0f;
        }
    }

    private static class AttributeSlider {
        final String name;
        final String tooltip;
        final int color;
        final float min;
        final float max;
        float value;
        EditBox inputField;
        boolean isDragging = false;
        float pulseAnimation = 0f;

        AttributeSlider(String name, String tooltip, int color, float min, float max, float defaultVal) {
            this.name = name;
            this.tooltip = tooltip;
            this.color = color;
            this.min = min;
            this.max = max;
            this.value = defaultVal;
        }

        void triggerPulse() {
            this.pulseAnimation = 1.0f;
        }
    }

    private static class EnchantmentEntry {
        final String name;
        final Holder<Enchantment> holder;
        int level;
        boolean toRemove = false;

        EnchantmentEntry(String name, Holder<Enchantment> holder, int level) {
            this.name = name;
            this.holder = holder;
            this.level = level;
        }

        /** Returns the enchantment holder for applying changes to the item */
        Holder<Enchantment> getHolder() {
            return holder;
        }
    }

    public ArmorEditorScreen() {
        super(Objects.requireNonNull(I18n.screenTitle("armor_editor")));
        Minecraft mc = Objects.requireNonNull(Minecraft.getInstance());

        // Disable blur
        if (mc.options != null) {
            OptionInstance<Integer> blurOption = mc.options.menuBackgroundBlurriness();
            originalBlurValue = blurOption.get();
            blurOption.set(0);
        }

        // Find the first equipped armor slot
        selectInitialArmorSlot(mc);
        loadArmorStats();
        loadEnchantments();
    }

    /** Null-safe font accessor */
    @Nonnull
    private net.minecraft.client.gui.Font getFont() {
        return Objects.requireNonNull(font);
    }

    /** Null-safe slot accessor */
    @Nonnull
    private static EquipmentSlot nn(EquipmentSlot slot) {
        return Objects.requireNonNull(slot);
    }

    private void selectInitialArmorSlot(Minecraft mc) {
        var player = mc.player;
        if (player == null) return;

        // Try to find any equipped armor, prioritizing chestplate
        for (ArmorSlotType slot : ArmorSlotType.values()) {
            ItemStack armor = player.getItemBySlot(nn(slot.slot));
            if (!armor.isEmpty() && armor.getItem() instanceof ArmorItem) {
                selectedSlot = slot;
                currentArmorPiece = armor.copy();
                return;
            }
        }

        // No armor found - use empty chestplate slot
        selectedSlot = ArmorSlotType.CHEST;
        currentArmorPiece = ItemStack.EMPTY;
    }

    private void switchArmorSlot(ArmorSlotType newSlot) {
        Minecraft mc = Objects.requireNonNull(Minecraft.getInstance());
        var player = mc.player;
        if (player == null) return;

        selectedSlot = newSlot;
        currentArmorPiece = player.getItemBySlot(nn(newSlot.slot)).copy();

        // Reload stats for new armor piece
        loadArmorStats();
        loadEnchantments();
    }

    private void loadArmorStats() {
        protectionSliders.clear();
        attributeSliders.clear();

        ArmorStats stats = currentArmorPiece.isEmpty()
            ? new ArmorStats()
            : ArmorConfigManager.getStats(currentArmorPiece);

        // Protection sliders (damage reductions)
        protectionSliders.add(new ProtectionSlider("Physical Reduction",
            "Reduces damage from melee attacks, fall damage, and other physical sources", 0xAAAAAAFF, stats.physicalReduction));
        protectionSliders.add(new ProtectionSlider("Fire Reduction",
            "Reduces damage from fire, lava, and burning effects", 0xFFAA00FF, stats.fireReduction));
        protectionSliders.add(new ProtectionSlider("Magic Reduction",
            "Reduces damage from magic attacks, potions, and wither effects", 0xAA00FFFF, stats.magicReduction));
        protectionSliders.add(new ProtectionSlider("Explosion Reduction",
            "Reduces damage from TNT, creepers, and other explosions", 0xFF5555FF, stats.explosionReduction));
        protectionSliders.add(new ProtectionSlider("Projectile Reduction",
            "Reduces damage from arrows, fireballs, and other projectiles", 0x55FFFFFF, stats.projectileReduction));

        // Attribute sliders
        attributeSliders.add(new AttributeSlider("Armor Bonus",
            "Additional armor points added to the base armor value", UIConstants.Accent.BLUE, -20f, 30f, stats.armorBonus));
        attributeSliders.add(new AttributeSlider("Toughness Bonus",
            "Reduces damage from high-damage attacks (diminishing returns)", UIConstants.Accent.CYAN, -10f, 20f, stats.toughnessBonus));
        attributeSliders.add(new AttributeSlider("Knockback Resistance",
            "Reduces knockback from attacks (1.0 = immune)", UIConstants.Accent.GREEN, 0f, 1f, stats.knockbackResistance));

        // Effects
        thornsEnabled = stats.thornsReflect;
        thornsPercent = stats.thornsPercent;
    }

    private void loadEnchantments() {
        enchantments.clear();
        if (currentArmorPiece.isEmpty()) return;

        Minecraft mc = Objects.requireNonNull(Minecraft.getInstance());
        if (mc.level != null) {
            var lookup = mc.level.registryAccess().lookupOrThrow(
                Objects.requireNonNull(net.minecraft.core.registries.Registries.ENCHANTMENT));
            var allEnchants = currentArmorPiece.getAllEnchantments(Objects.requireNonNull(lookup));
            allEnchants.entrySet().forEach(entry -> {
                Holder<Enchantment> ench = entry.getKey();
                int level = entry.getIntValue();
                String name = formatEnchantmentName(ench);
                enchantments.add(new EnchantmentEntry(name, ench, level));
            });
        } else {
            ItemEnchantments itemEnchants = currentArmorPiece.getOrDefault(
                Objects.requireNonNull(DataComponents.ENCHANTMENTS), Objects.requireNonNull(ItemEnchantments.EMPTY));
            itemEnchants.entrySet().forEach(entry -> {
                Holder<Enchantment> ench = entry.getKey();
                int level = entry.getIntValue();
                String name = formatEnchantmentName(ench);
                enchantments.add(new EnchantmentEntry(name, ench, level));
            });
        }
    }

    private String formatEnchantmentName(Holder<Enchantment> ench) {
        return ench.unwrapKey()
            .map(key -> {
                String path = key.location().getPath();
                String namespace = key.location().getNamespace();
                String name = Arrays.stream(path.split("_"))
                    .map(s -> s.isEmpty() ? s : s.substring(0, 1).toUpperCase() + s.substring(1))
                    .reduce((a, b) -> a + " " + b)
                    .orElse(path);
                if (!namespace.equals("minecraft")) {
                    name = "[" + namespace + "] " + name;
                }
                return name;
            })
            .orElse("Unknown");
    }

    @Override
    protected void init() {
        super.init();

        int centerX = width / 2;
        int panelLeft = centerX - PANEL_WIDTH / 2;

        // Initialize input fields for protection sliders
        int sliderY = 0;
        for (ProtectionSlider slider : protectionSliders) {
            slider.inputField = new EditBox(getFont(), panelLeft + PANEL_WIDTH - 70, sliderY, 50, 16,
                Objects.requireNonNull(Component.literal("")));
            slider.inputField.setValue(Objects.requireNonNull(String.format("%.0f", slider.value * 100)));
            slider.inputField.setMaxLength(5);
            addWidget(slider.inputField);
            sliderY += 30;
        }

        // Initialize input fields for attribute sliders
        for (AttributeSlider slider : attributeSliders) {
            slider.inputField = new EditBox(getFont(), panelLeft + PANEL_WIDTH - 70, sliderY, 50, 16,
                Objects.requireNonNull(Component.literal("")));
            slider.inputField.setValue(Objects.requireNonNull(String.format("%.1f", slider.value)));
            slider.inputField.setMaxLength(6);
            addWidget(slider.inputField);
            sliderY += 30;
        }

        // Thorns percent field
        thornsPercentField = new EditBox(getFont(), panelLeft + 150, 0, 50, 16,
            Objects.requireNonNull(Component.literal("")));
        thornsPercentField.setValue(Objects.requireNonNull(String.format("%.0f", thornsPercent * 100)));
        thornsPercentField.setMaxLength(3);
        addWidget(thornsPercentField);

        // Durability fields
        durabilityField = new EditBox(getFont(), panelLeft + 150, 0, 80, 16,
            Objects.requireNonNull(Component.literal("")));
        if (!currentArmorPiece.isEmpty()) {
            durabilityField.setValue(Objects.requireNonNull(String.valueOf(currentArmorPiece.getDamageValue())));
        }
        durabilityField.setMaxLength(6);
        addWidget(durabilityField);

        repairCostField = new EditBox(getFont(), panelLeft + 150, 0, 80, 16,
            Objects.requireNonNull(Component.literal("")));
        repairCostField.setValue("0");
        repairCostField.setMaxLength(4);
        addWidget(repairCostField);

        // Enchant search box
        enchantSearchBox = new EditBox(getFont(), panelLeft + 10, 0, 150, 16,
            Objects.requireNonNull(Component.literal("")));
        enchantSearchBox.setHint(Objects.requireNonNull(Component.literal("Search...")));
        enchantSearchBox.setMaxLength(30);
        addWidget(enchantSearchBox);

        // Picker search box (for adding new enchantments)
        pickerSearchBox = new EditBox(getFont(), panelLeft + 10, 0, 200, 16,
            Objects.requireNonNull(Component.literal("")));
        pickerSearchBox.setHint(Objects.requireNonNull(Component.literal("Search enchantments...")));
        pickerSearchBox.setMaxLength(30);
        addWidget(pickerSearchBox);
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Reset tooltip each frame
        hoveredTooltip = null;

        // Dark background
        graphics.fill(0, 0, width, height, 0xCC000000);

        int centerX = width / 2;
        int centerY = height / 2;
        int panelLeft = centerX - PANEL_WIDTH / 2;
        int panelTop = centerY - PANEL_HEIGHT / 2;

        // Main panel background
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT,
            UIConstants.Background.PANEL);
        AxiomRenderer.drawBorder(graphics, panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT,
            UIConstants.Border.DEFAULT);

        // Title bar
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + 28,
            UIConstants.Background.HEADER);
        graphics.drawCenteredString(getFont(), "Armor Editor", centerX, panelTop + 10, 0xFFFFFF);

        // Armor slot selector (below title)
        renderArmorSlotSelector(graphics, panelLeft, panelTop + 30, mouseX, mouseY);

        // Tabs
        int tabY = panelTop + 30 + SLOT_SELECTOR_HEIGHT;
        renderTabs(graphics, panelLeft, tabY, mouseX, mouseY);

        // Content area
        int contentY = tabY + TAB_HEIGHT + 5;
        int contentHeight = PANEL_HEIGHT - 30 - SLOT_SELECTOR_HEIGHT - TAB_HEIGHT - 70;

        // Render current tab content
        switch (currentTab) {
            case PROTECTION -> renderProtectionTab(graphics, panelLeft, contentY, contentHeight, mouseX, mouseY);
            case ATTRIBUTES -> renderAttributesTab(graphics, panelLeft, contentY, contentHeight, mouseX, mouseY);
            case ENCHANTS -> renderEnchantsTab(graphics, panelLeft, contentY, contentHeight, mouseX, mouseY);
            case DURABILITY -> renderDurabilityTab(graphics, panelLeft, contentY, contentHeight, mouseX, mouseY);
            case EFFECTS -> renderEffectsTab(graphics, panelLeft, contentY, contentHeight, mouseX, mouseY);
        }

        // Global/Specific toggle
        int bottomY = panelTop + PANEL_HEIGHT - 35;
        renderGlobalToggle(graphics, panelLeft, bottomY, mouseX, mouseY);

        // Apply/Close buttons
        renderBottomButtons(graphics, panelLeft, panelTop + PANEL_HEIGHT - 30, mouseX, mouseY);

        // Armor preview
        renderArmorPreview(graphics, panelLeft + PANEL_WIDTH - PREVIEW_SIZE - 10, panelTop + 35 + SLOT_SELECTOR_HEIGHT);

        // Status message
        if (statusMessage != null && statusTicks > 0) {
            graphics.drawCenteredString(getFont(), Objects.requireNonNull(statusMessage), centerX, panelTop + PANEL_HEIGHT - 50, statusColor);
        }

        // Animation update
        rotationAngle += partialTick * 0.5f;
        if (statusTicks > 0) statusTicks--;

        // Update pulse animations
        for (ProtectionSlider slider : protectionSliders) {
            if (slider.pulseAnimation > 0) slider.pulseAnimation -= 0.05f;
        }
        for (AttributeSlider slider : attributeSliders) {
            if (slider.pulseAnimation > 0) slider.pulseAnimation -= 0.05f;
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        // Render tooltip last (on top of everything)
        if (hoveredTooltip != null) {
            renderTooltip(graphics, hoveredTooltip, tooltipX, tooltipY);
        }
    }

    private void renderTooltip(GuiGraphics graphics, String text, int x, int y) {
        int tooltipWidth = getFont().width(Objects.requireNonNull(text)) + 8;
        int tooltipHeight = 14;

        // Adjust position to stay on screen
        int drawX = Math.min(x + 10, width - tooltipWidth - 5);
        int drawY = y - tooltipHeight - 5;
        if (drawY < 5) drawY = y + 15;

        // Background
        graphics.fill(drawX - 2, drawY - 2, drawX + tooltipWidth + 2, drawY + tooltipHeight + 2, 0xF0100010);
        // Border
        graphics.fill(drawX - 2, drawY - 2, drawX + tooltipWidth + 2, drawY - 1, 0xFF505050);
        graphics.fill(drawX - 2, drawY + tooltipHeight + 1, drawX + tooltipWidth + 2, drawY + tooltipHeight + 2, 0xFF505050);
        graphics.fill(drawX - 2, drawY - 1, drawX - 1, drawY + tooltipHeight + 1, 0xFF505050);
        graphics.fill(drawX + tooltipWidth + 1, drawY - 1, drawX + tooltipWidth + 2, drawY + tooltipHeight + 1, 0xFF505050);
        // Text
        graphics.drawString(getFont(), text, drawX + 4, drawY + 3, 0xFFFFFF);
    }

    private void renderArmorSlotSelector(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int slotWidth = 80;
        int spacing = 10;
        int totalWidth = (slotWidth * 4) + (spacing * 3);
        int startX = x + (PANEL_WIDTH - totalWidth) / 2;

        Minecraft mc = Objects.requireNonNull(Minecraft.getInstance());
        var player = mc.player;

        for (ArmorSlotType slot : ArmorSlotType.values()) {
            int slotX = startX + (slot.index * (slotWidth + spacing));
            boolean isSelected = selectedSlot == slot;
            boolean isHovered = mouseX >= slotX && mouseX < slotX + slotWidth &&
                               mouseY >= y && mouseY < y + SLOT_SELECTOR_HEIGHT;

            // Background
            int bgColor = isSelected ? UIConstants.Accent.BLUE : (isHovered ? 0x40FFFFFF : 0x20FFFFFF);
            graphics.fill(slotX, y, slotX + slotWidth, y + SLOT_SELECTOR_HEIGHT, bgColor);

            // Border
            int borderColor = isSelected ? UIConstants.Accent.CYAN : UIConstants.Border.DEFAULT;
            AxiomRenderer.drawBorder(graphics, slotX, y, slotWidth, SLOT_SELECTOR_HEIGHT, borderColor);

            // Slot name
            graphics.drawCenteredString(getFont(), Objects.requireNonNull(slot.displayName), slotX + slotWidth / 2, y + 5,
                isSelected ? 0xFFFFFF : 0xAAAAAA);

            // Show if armor is equipped in this slot
            if (player != null) {
                ItemStack armor = player.getItemBySlot(nn(slot.slot));
                if (!armor.isEmpty()) {
                    graphics.drawCenteredString(getFont(), "[*]", slotX + slotWidth / 2, y + 18, 0x55FF55);
                } else {
                    graphics.drawCenteredString(getFont(), "[-]", slotX + slotWidth / 2, y + 18, 0x555555);
                }
            }
        }
    }

    private void renderTabs(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        Tab[] tabs = Tab.values();
        int tabWidth = PANEL_WIDTH / tabs.length;

        for (int i = 0; i < tabs.length; i++) {
            Tab tab = tabs[i];
            int tabX = x + (i * tabWidth);
            boolean isSelected = currentTab == tab;
            boolean isHovered = mouseX >= tabX && mouseX < tabX + tabWidth &&
                               mouseY >= y && mouseY < y + TAB_HEIGHT;

            // Tab background
            int bgColor = isSelected ? UIConstants.Accent.BLUE : (isHovered ? 0x40FFFFFF : 0x20FFFFFF);
            graphics.fill(tabX, y, tabX + tabWidth, y + TAB_HEIGHT, bgColor);

            // Tab border
            if (isSelected) {
                graphics.fill(tabX, y + TAB_HEIGHT - 2, tabX + tabWidth, y + TAB_HEIGHT, UIConstants.Accent.CYAN);
            }

            // Tab label
            String label = Objects.requireNonNull(tab.name());
            graphics.drawCenteredString(getFont(), label, tabX + tabWidth / 2, y + 7,
                isSelected ? 0xFFFFFF : 0xAAAAAA);
        }
    }

    private void renderProtectionTab(GuiGraphics graphics, int x, int y, int height, int mouseX, int mouseY) {
        int sliderWidth = PANEL_WIDTH - PREVIEW_SIZE - 40;
        int sliderY = y + 10;

        graphics.drawString(getFont(), "Damage Reduction by Type:", x + 10, sliderY, 0xFFFFFF);
        sliderY += 20;

        for (ProtectionSlider slider : protectionSliders) {
            renderProtectionSlider(graphics, x + 10, sliderY, sliderWidth, slider, mouseX, mouseY);
            sliderY += 40;
        }

        // Total reduction display
        float total = 0;
        for (ProtectionSlider s : protectionSliders) total += s.value;
        int totalColor = total > 0.8f ? 0xFF5555 : (total > 0.5f ? 0xFFAA00 : 0x55FF55);
        graphics.drawString(getFont(), String.format("Total: %.0f%% (max 80%%)", total * 100), x + 10, sliderY, totalColor);
    }

    private void renderProtectionSlider(GuiGraphics graphics, int x, int y, int width,
                                        ProtectionSlider slider, int mouseX, int mouseY) {
        // Label
        graphics.drawString(getFont(), slider.name + ":", x, y, 0xCCCCCC);

        // Slider track
        int trackY = y + 15;
        int trackWidth = width - 70;
        graphics.fill(x, trackY, x + trackWidth, trackY + 6, 0x40FFFFFF);

        // Filled portion
        int fillWidth = (int) (trackWidth * slider.value);
        int fillColor = slider.color | 0xFF000000;
        if (slider.pulseAnimation > 0) {
            fillColor = blendColors(fillColor, 0xFFFFFFFF, slider.pulseAnimation * 0.3f);
        }
        graphics.fill(x, trackY, x + fillWidth, trackY + 6, fillColor);

        // Handle
        int handleX = x + fillWidth - 3;
        graphics.fill(handleX, trackY - 2, handleX + 6, trackY + 8, 0xFFFFFFFF);

        // Percentage display
        String percent = String.format("%.0f%%", slider.value * 100);
        graphics.drawString(getFont(), percent, x + trackWidth + 10, y + 12, 0xFFFFFF);

        // Update input field position
        if (slider.inputField != null) {
            slider.inputField.setX(x + trackWidth + 5);
            slider.inputField.setY(y + 10);
        }

        // Check for hover to show tooltip
        if (mouseX >= x && mouseX < x + trackWidth && mouseY >= y && mouseY < y + 30) {
            hoveredTooltip = slider.tooltip;
            tooltipX = mouseX;
            tooltipY = mouseY;
        }
    }

    private void renderAttributesTab(GuiGraphics graphics, int x, int y, int height, int mouseX, int mouseY) {
        int sliderWidth = PANEL_WIDTH - PREVIEW_SIZE - 40;
        int sliderY = y + 10;

        graphics.drawString(getFont(), "Armor Attribute Modifiers:", x + 10, sliderY, 0xFFFFFF);
        sliderY += 20;

        for (AttributeSlider slider : attributeSliders) {
            renderAttributeSlider(graphics, x + 10, sliderY, sliderWidth, slider, mouseX, mouseY);
            sliderY += 45;
        }
    }

    private void renderAttributeSlider(GuiGraphics graphics, int x, int y, int width,
                                       AttributeSlider slider, int mouseX, int mouseY) {
        // Label
        graphics.drawString(getFont(), slider.name + ":", x, y, 0xCCCCCC);

        // Slider track
        int trackY = y + 15;
        int trackWidth = width - 80;
        graphics.fill(x, trackY, x + trackWidth, trackY + 6, 0x40FFFFFF);

        // Calculate fill
        float normalizedValue = (slider.value - slider.min) / (slider.max - slider.min);
        int fillWidth = (int) (trackWidth * normalizedValue);

        // Filled portion
        int fillColor = slider.color | 0xFF000000;
        if (slider.pulseAnimation > 0) {
            fillColor = blendColors(fillColor, 0xFFFFFFFF, slider.pulseAnimation * 0.3f);
        }
        graphics.fill(x, trackY, x + fillWidth, trackY + 6, fillColor);

        // Handle
        int handleX = x + fillWidth - 3;
        graphics.fill(handleX, trackY - 2, handleX + 6, trackY + 8, 0xFFFFFFFF);

        // Value display
        String valueStr;
        if (slider.max <= 1f) {
            valueStr = String.format("%.0f%%", slider.value * 100);
        } else {
            valueStr = String.format("%.1f", slider.value);
        }
        graphics.drawString(getFont(), valueStr, x + trackWidth + 10, y + 12, 0xFFFFFF);

        // Update input field position
        if (slider.inputField != null) {
            slider.inputField.setX(x + trackWidth + 5);
            slider.inputField.setY(y + 10);
        }

        // Check for hover to show tooltip
        if (mouseX >= x && mouseX < x + trackWidth && mouseY >= y && mouseY < y + 35) {
            hoveredTooltip = slider.tooltip;
            tooltipX = mouseX;
            tooltipY = mouseY;
        }
    }

    private void renderEnchantsTab(GuiGraphics graphics, int x, int y, int height, int mouseX, int mouseY) {
        if (showEnchantPicker) {
            renderEnchantmentPicker(graphics, x, y, height, mouseX, mouseY);
            return;
        }

        // Search box
        enchantSearchBox.setX(x + 10);
        enchantSearchBox.setY(y + 5);
        enchantSearch = enchantSearchBox.getValue();

        graphics.drawString(getFont(), "Enchantments:", x + 10, y + 25, 0xFFFFFF);

        // List enchantments
        int listY = y + 40;
        int listHeight = height - 80;
        int entryHeight = 22;

        graphics.enableScissor(x + 5, listY, x + PANEL_WIDTH - PREVIEW_SIZE - 20, listY + listHeight);

        int displayY = listY - (enchantScrollOffset * entryHeight);
        for (EnchantmentEntry entry : enchantments) {
            if (!enchantSearch.isEmpty() &&
                !entry.name.toLowerCase().contains(enchantSearch.toLowerCase())) {
                continue;
            }

            if (displayY >= listY - entryHeight && displayY < listY + listHeight) {
                renderEnchantmentEntry(graphics, x + 10, displayY, PANEL_WIDTH - PREVIEW_SIZE - 40, entry, mouseX, mouseY);
            }
            displayY += entryHeight;
        }

        graphics.disableScissor();

        // Add enchantment button
        int addBtnY = y + height - 30;
        boolean addHovered = mouseX >= x + 10 && mouseX < x + 120 && mouseY >= addBtnY && mouseY < addBtnY + 20;
        graphics.fill(x + 10, addBtnY, x + 120, addBtnY + 20, addHovered ? 0x6055FF55 : 0x4055FF55);
        graphics.drawCenteredString(getFont(), "+ Add Enchant", x + 65, addBtnY + 6, 0xFFFFFF);
    }

    private void renderEnchantmentPicker(GuiGraphics graphics, int x, int y, int height, int mouseX, int mouseY) {
        // Title
        graphics.drawString(getFont(), "Select Enchantment to Add:", x + 10, y + 5, 0xFFFFFF);

        // Search box
        pickerSearchBox.setX(x + 10);
        pickerSearchBox.setY(y + 20);
        pickerSearch = pickerSearchBox.getValue();

        // Back button
        int backBtnX = x + PANEL_WIDTH - PREVIEW_SIZE - 80;
        boolean backHovered = mouseX >= backBtnX && mouseX < backBtnX + 60 && mouseY >= y + 5 && mouseY < y + 25;
        graphics.fill(backBtnX, y + 5, backBtnX + 60, y + 25, backHovered ? 0x60FF5555 : 0x40FF5555);
        graphics.drawCenteredString(getFont(), "Back", backBtnX + 30, y + 10, 0xFFFFFF);

        // Enchantment list
        int listY = y + 45;
        int listHeight = height - 55;
        int entryHeight = 20;
        int listWidth = PANEL_WIDTH - PREVIEW_SIZE - 30;

        graphics.enableScissor(x + 5, listY, x + listWidth + 15, listY + listHeight);

        int displayY = listY - (pickerScrollOffset * entryHeight);
        for (Holder<Enchantment> enchHolder : allAvailableEnchants) {
            String enchName = formatEnchantmentName(enchHolder);

            // Filter by search
            if (!pickerSearch.isEmpty() &&
                !enchName.toLowerCase().contains(pickerSearch.toLowerCase())) {
                continue;
            }

            // Check if already added
            boolean alreadyHas = enchantments.stream()
                .anyMatch(e -> e.name.equals(enchName) && !e.toRemove);

            if (displayY >= listY - entryHeight && displayY < listY + listHeight) {
                boolean isHovered = mouseX >= x + 10 && mouseX < x + listWidth &&
                                   mouseY >= displayY && mouseY < displayY + entryHeight;

                // Background
                int bgColor = alreadyHas ? 0x40555555 : (isHovered ? 0x4055FF55 : 0x20FFFFFF);
                graphics.fill(x + 10, displayY, x + listWidth, displayY + entryHeight - 2, bgColor);

                // Name
                int textColor = alreadyHas ? 0x888888 : 0xFFFFFF;
                graphics.drawString(getFont(), enchName, x + 15, displayY + 5, textColor);

                // "Added" indicator
                if (alreadyHas) {
                    graphics.drawString(getFont(), "[Added]", x + listWidth - 50, displayY + 5, 0x55FF55);
                }
            }
            displayY += entryHeight;
        }

        graphics.disableScissor();

        // Scroll hint
        if (allAvailableEnchants.size() > listHeight / entryHeight) {
            graphics.drawString(getFont(), "Scroll to see more...", x + 10, y + height - 10, 0x888888);
        }
    }

    private void renderEnchantmentEntry(GuiGraphics graphics, int x, int y, int width,
                                        EnchantmentEntry entry, int mouseX, int mouseY) {
        // Background
        int bgColor = entry.toRemove ? 0x40FF5555 : 0x20FFFFFF;
        graphics.fill(x, y, x + width, y + 20, bgColor);

        // Name
        String displayName = entry.name + " " + toRoman(entry.level);
        graphics.drawString(getFont(), displayName, x + 5, y + 6, entry.toRemove ? 0xFF5555 : 0xFFFFFF);

        // Level controls
        int btnX = x + width - 60;
        // Minus button
        graphics.fill(btnX, y + 2, btnX + 16, y + 18, 0x40FFFFFF);
        graphics.drawCenteredString(getFont(), "-", btnX + 8, y + 5, 0xFFFFFF);

        // Plus button
        graphics.fill(btnX + 20, y + 2, btnX + 36, y + 18, 0x40FFFFFF);
        graphics.drawCenteredString(getFont(), "+", btnX + 28, y + 5, 0xFFFFFF);

        // Remove button
        graphics.fill(btnX + 40, y + 2, btnX + 56, y + 18, 0x40FF5555);
        graphics.drawCenteredString(getFont(), "X", btnX + 48, y + 5, 0xFFFFFF);
    }

    private void renderDurabilityTab(GuiGraphics graphics, int x, int y, int height, int mouseX, int mouseY) {
        if (currentArmorPiece.isEmpty()) {
            graphics.drawCenteredString(getFont(), "No armor in this slot", x + PANEL_WIDTH / 2, y + height / 2, 0xAAAAAA);
            return;
        }

        int fieldY = y + 20;

        // Current durability
        graphics.drawString(getFont(), "Current Damage:", x + 10, fieldY, 0xCCCCCC);
        durabilityField.setX(x + 130);
        durabilityField.setY(fieldY - 2);
        int maxDura = currentArmorPiece.getMaxDamage();
        graphics.drawString(getFont(), "/ " + maxDura, x + 220, fieldY, 0x888888);

        fieldY += 35;

        // Repair cost
        graphics.drawString(getFont(), "Repair Cost:", x + 10, fieldY, 0xCCCCCC);
        repairCostField.setX(x + 130);
        repairCostField.setY(fieldY - 2);

        fieldY += 35;

        // Unbreakable toggle
        boolean unbHovered = mouseX >= x + 10 && mouseX < x + 200 && mouseY >= fieldY && mouseY < fieldY + 20;
        graphics.fill(x + 10, fieldY, x + 200, fieldY + 20, unbHovered ? 0x40FFFFFF : 0x20FFFFFF);
        graphics.drawString(getFont(), "Unbreakable: " + (isUnbreakable ? "YES" : "NO"), x + 15, fieldY + 6,
            isUnbreakable ? 0x55FF55 : 0xAAAAAA);

        fieldY += 35;

        // Quick repair button
        boolean repairHovered = mouseX >= x + 10 && mouseX < x + 120 && mouseY >= fieldY && mouseY < fieldY + 20;
        graphics.fill(x + 10, fieldY, x + 120, fieldY + 20, repairHovered ? 0x6055FF55 : 0x4055FF55);
        graphics.drawCenteredString(getFont(), "Full Repair", x + 65, fieldY + 6, 0xFFFFFF);
    }

    private void renderEffectsTab(GuiGraphics graphics, int x, int y, int height, int mouseX, int mouseY) {
        int effectY = y + 20;

        graphics.drawString(getFont(), "Special Effects:", x + 10, effectY, 0xFFFFFF);
        effectY += 25;

        // Thorns toggle
        boolean thornsHovered = mouseX >= x + 10 && mouseX < x + 200 && mouseY >= effectY && mouseY < effectY + 20;
        graphics.fill(x + 10, effectY, x + 200, effectY + 20, thornsHovered ? 0x40FFFFFF : 0x20FFFFFF);
        graphics.drawString(getFont(), "Thorns Reflection: " + (thornsEnabled ? "ON" : "OFF"), x + 15, effectY + 6,
            thornsEnabled ? 0x55FF55 : 0xAAAAAA);

        effectY += 30;

        // Thorns percentage (only if enabled)
        if (thornsEnabled) {
            graphics.drawString(getFont(), "Reflection %:", x + 10, effectY, 0xCCCCCC);
            thornsPercentField.setX(x + 130);
            thornsPercentField.setY(effectY - 2);
            graphics.drawString(getFont(), "(0-50%)", x + 190, effectY, 0x888888);

            effectY += 30;

            // Preview calculation
            float reflectDamage = thornsPercent * 10; // Example: 10 damage taken
            graphics.drawString(getFont(), String.format("Example: 10 damage -> %.1f reflected", reflectDamage),
                x + 10, effectY, 0xFFAA00);
        }
    }

    private void renderGlobalToggle(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int toggleWidth = 180;
        boolean isHovered = mouseX >= x + 10 && mouseX < x + 10 + toggleWidth &&
                           mouseY >= y && mouseY < y + 20;

        int bgColor = isHovered ? 0x40FFFFFF : 0x20FFFFFF;
        graphics.fill(x + 10, y, x + 10 + toggleWidth, y + 20, bgColor);

        String text = editGlobal ? "Mode: GLOBAL (all same items)" : "Mode: SPECIFIC (this item only)";
        int textColor = editGlobal ? UIConstants.Accent.ORANGE : UIConstants.Accent.CYAN;
        graphics.drawString(getFont(), text, x + 15, y + 6, textColor);
    }

    private void renderBottomButtons(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        // Apply button
        int applyX = x + PANEL_WIDTH / 2 - 100;
        boolean applyHovered = mouseX >= applyX && mouseX < applyX + 80 && mouseY >= y && mouseY < y + 22;
        graphics.fill(applyX, y, applyX + 80, y + 22, applyHovered ? 0x8055FF55 : 0x6055FF55);
        graphics.drawCenteredString(getFont(), "Apply", applyX + 40, y + 7, 0xFFFFFF);

        // Close button
        int closeX = x + PANEL_WIDTH / 2 + 20;
        boolean closeHovered = mouseX >= closeX && mouseX < closeX + 80 && mouseY >= y && mouseY < y + 22;
        graphics.fill(closeX, y, closeX + 80, y + 22, closeHovered ? 0x80FF5555 : 0x60FF5555);
        graphics.drawCenteredString(getFont(), "Close", closeX + 40, y + 7, 0xFFFFFF);
    }

    private void renderArmorPreview(GuiGraphics graphics, int x, int y) {
        Minecraft mc = Objects.requireNonNull(Minecraft.getInstance());
        var player = mc.player;

        // Background panel
        graphics.fill(x, y, x + PREVIEW_SIZE, y + PREVIEW_SIZE + 40, 0x40000000);

        if (player == null) {
            graphics.drawCenteredString(getFont(), "No Player", x + PREVIEW_SIZE / 2, y + PREVIEW_SIZE / 2, 0x555555);
            return;
        }

        // Render 3D player mannequin with current armor
        int centerX = x + PREVIEW_SIZE / 2;
        int entityY = y + PREVIEW_SIZE + 25; // Position at bottom of preview area
        int scale = 35; // Scale for the player model

        // Create rotation based on animation
        Quaternionf rotation = Objects.requireNonNull(new Quaternionf()
            .rotationXYZ(0, (float) Math.toRadians(rotationAngle * 20), (float) Math.PI));

        try {
            InventoryScreen.renderEntityInInventory(
                graphics,
                centerX, entityY,
                scale,
                new Vector3f(0, 0, 0),
                rotation,
                null,
                player
            );
        } catch (Exception e) {
            // Fallback to simple text if rendering fails
            graphics.drawCenteredString(getFont(), "Preview N/A", centerX, y + PREVIEW_SIZE / 2, 0x888888);
        }

        // Show selected slot indicator
        String slotName = Objects.requireNonNull(selectedSlot.displayName);
        graphics.drawCenteredString(getFont(), slotName, centerX, y + PREVIEW_SIZE + 30, 0xFFFFAA);

        // Show item name if armor is equipped
        if (!currentArmorPiece.isEmpty()) {
            String itemName = currentArmorPiece.getHoverName().getString();
            if (itemName.length() > 12) itemName = itemName.substring(0, 10) + "...";
            graphics.drawCenteredString(getFont(), itemName, centerX, y + PREVIEW_SIZE + 42, 0x55FF55);
        } else {
            graphics.drawCenteredString(getFont(), "(Empty)", centerX, y + PREVIEW_SIZE + 42, 0x555555);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int centerX = width / 2;
        int centerY = height / 2;
        int panelLeft = centerX - PANEL_WIDTH / 2;
        int panelTop = centerY - PANEL_HEIGHT / 2;

        // Armor slot selector
        int slotY = panelTop + 30;
        int slotWidth = 80;
        int spacing = 10;
        int totalWidth = (slotWidth * 4) + (spacing * 3);
        int startX = panelLeft + (PANEL_WIDTH - totalWidth) / 2;

        for (ArmorSlotType slot : ArmorSlotType.values()) {
            int slotX = startX + (slot.index * (slotWidth + spacing));
            if (mouseX >= slotX && mouseX < slotX + slotWidth &&
                mouseY >= slotY && mouseY < slotY + SLOT_SELECTOR_HEIGHT) {
                switchArmorSlot(slot);
                return true;
            }
        }

        // Tab clicks
        int tabY = panelTop + 30 + SLOT_SELECTOR_HEIGHT;
        Tab[] tabs = Tab.values();
        int tabWidth = PANEL_WIDTH / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            int tabX = panelLeft + (i * tabWidth);
            if (mouseX >= tabX && mouseX < tabX + tabWidth &&
                mouseY >= tabY && mouseY < tabY + TAB_HEIGHT) {
                currentTab = tabs[i];
                return true;
            }
        }

        // Global/Specific toggle
        int bottomY = panelTop + PANEL_HEIGHT - 35;
        if (mouseX >= panelLeft + 10 && mouseX < panelLeft + 190 &&
            mouseY >= bottomY && mouseY < bottomY + 20) {
            editGlobal = !editGlobal;
            showStatus(editGlobal ? "Editing GLOBAL config" : "Editing SPECIFIC item", 0xFFFFAA);
            return true;
        }

        // Apply button
        int applyX = panelLeft + PANEL_WIDTH / 2 - 100;
        int applyY = panelTop + PANEL_HEIGHT - 30;
        if (mouseX >= applyX && mouseX < applyX + 80 && mouseY >= applyY && mouseY < applyY + 22) {
            applyChanges();
            return true;
        }

        // Close button
        int closeX = panelLeft + PANEL_WIDTH / 2 + 20;
        if (mouseX >= closeX && mouseX < closeX + 80 && mouseY >= applyY && mouseY < applyY + 22) {
            onClose();
            return true;
        }

        // Tab-specific clicks
        int contentY = tabY + TAB_HEIGHT + 5;
        int contentHeight = PANEL_HEIGHT - 30 - SLOT_SELECTOR_HEIGHT - TAB_HEIGHT - 70;

        switch (currentTab) {
            case DURABILITY -> {
                // Unbreakable toggle
                int unbY = contentY + 90;
                if (mouseX >= panelLeft + 10 && mouseX < panelLeft + 200 &&
                    mouseY >= unbY && mouseY < unbY + 20) {
                    isUnbreakable = !isUnbreakable;
                    return true;
                }

                // Full repair button
                int repairY = unbY + 35;
                if (mouseX >= panelLeft + 10 && mouseX < panelLeft + 120 &&
                    mouseY >= repairY && mouseY < repairY + 20) {
                    durabilityField.setValue("0");
                    showStatus("Armor fully repaired!", 0x55FF55);
                    return true;
                }
            }
            case EFFECTS -> {
                // Thorns toggle
                int thornsY = contentY + 45;
                if (mouseX >= panelLeft + 10 && mouseX < panelLeft + 200 &&
                    mouseY >= thornsY && mouseY < thornsY + 20) {
                    thornsEnabled = !thornsEnabled;
                    return true;
                }
            }
            case ENCHANTS -> {
                if (showEnchantPicker) {
                    // Back button
                    int backBtnX = panelLeft + PANEL_WIDTH - PREVIEW_SIZE - 80;
                    if (mouseX >= backBtnX && mouseX < backBtnX + 60 &&
                        mouseY >= contentY + 5 && mouseY < contentY + 25) {
                        showEnchantPicker = false;
                        pickerScrollOffset = 0;
                        pickerSearchBox.setValue("");
                        return true;
                    }

                    // Click on enchantment in picker to add it
                    int listY = contentY + 45;
                    int listHeight = contentHeight - 55;
                    int entryHeight = 20;
                    int listWidth = PANEL_WIDTH - PREVIEW_SIZE - 30;

                    if (mouseX >= panelLeft + 10 && mouseX < panelLeft + listWidth &&
                        mouseY >= listY && mouseY < listY + listHeight) {

                        int displayY = listY - (pickerScrollOffset * entryHeight);
                        for (Holder<Enchantment> enchHolder : allAvailableEnchants) {
                            String enchName = formatEnchantmentName(enchHolder);

                            // Filter by search
                            if (!pickerSearch.isEmpty() &&
                                !enchName.toLowerCase().contains(pickerSearch.toLowerCase())) {
                                continue;
                            }

                            if (mouseY >= displayY && mouseY < displayY + entryHeight) {
                                // Check if already added
                                boolean alreadyHas = enchantments.stream()
                                    .anyMatch(e -> e.name.equals(enchName) && !e.toRemove);

                                if (!alreadyHas) {
                                    // Add new enchantment
                                    enchantments.add(new EnchantmentEntry(enchName, enchHolder, 1));
                                    showStatus("Added: " + enchName, 0x55FF55);
                                }
                                return true;
                            }
                            displayY += entryHeight;
                        }
                    }
                } else {
                    // Add enchantment button
                    int addBtnY = contentY + contentHeight - 30;
                    if (mouseX >= panelLeft + 10 && mouseX < panelLeft + 120 &&
                        mouseY >= addBtnY && mouseY < addBtnY + 20) {
                        loadAllAvailableEnchantments();
                        showEnchantPicker = true;
                        pickerScrollOffset = 0;
                        return true;
                    }

                    // Click on enchantment entry buttons (-, +, X)
                    int listY = contentY + 40;
                    int entryHeight = 22;
                    int entryWidth = PANEL_WIDTH - PREVIEW_SIZE - 40;

                    int displayY = listY - (enchantScrollOffset * entryHeight);
                    for (EnchantmentEntry entry : enchantments) {
                        if (!enchantSearch.isEmpty() &&
                            !entry.name.toLowerCase().contains(enchantSearch.toLowerCase())) {
                            continue;
                        }

                        if (mouseY >= displayY && mouseY < displayY + entryHeight) {
                            int btnX = panelLeft + 10 + entryWidth - 60;

                            // Minus button
                            if (mouseX >= btnX && mouseX < btnX + 16) {
                                if (entry.level > 1) {
                                    entry.level--;
                                    showStatus(entry.name + " -> Level " + entry.level, 0xFFFFAA);
                                }
                                return true;
                            }

                            // Plus button
                            if (mouseX >= btnX + 20 && mouseX < btnX + 36) {
                                if (entry.level < 10) {
                                    entry.level++;
                                    showStatus(entry.name + " -> Level " + entry.level, 0xFFFFAA);
                                }
                                return true;
                            }

                            // Remove button
                            if (mouseX >= btnX + 40 && mouseX < btnX + 56) {
                                entry.toRemove = !entry.toRemove;
                                showStatus(entry.toRemove ? "Marked for removal" : "Removal cancelled",
                                    entry.toRemove ? 0xFF5555 : 0x55FF55);
                                return true;
                            }
                        }
                        displayY += entryHeight;
                    }
                }
            }
            case PROTECTION, ATTRIBUTES -> {
                // Handled by slider dragging below
            }
        }

        // Handle slider dragging
        if (currentTab == Tab.PROTECTION) {
            int sliderY = contentY + 30;
            for (ProtectionSlider slider : protectionSliders) {
                if (mouseY >= sliderY && mouseY < sliderY + 30) {
                    int trackX = panelLeft + 10;
                    int trackWidth = PANEL_WIDTH - PREVIEW_SIZE - 110;
                    if (mouseX >= trackX && mouseX < trackX + trackWidth) {
                        slider.isDragging = true;
                        updateProtectionSliderValue(slider, (int) mouseX, trackX, trackWidth);
                        return true;
                    }
                }
                sliderY += 40;
            }
        } else if (currentTab == Tab.ATTRIBUTES) {
            int sliderY = contentY + 30;
            for (AttributeSlider slider : attributeSliders) {
                if (mouseY >= sliderY && mouseY < sliderY + 35) {
                    int trackX = panelLeft + 10;
                    int trackWidth = PANEL_WIDTH - PREVIEW_SIZE - 120;
                    if (mouseX >= trackX && mouseX < trackX + trackWidth) {
                        slider.isDragging = true;
                        updateAttributeSliderValue(slider, (int) mouseX, trackX, trackWidth);
                        return true;
                    }
                }
                sliderY += 45;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        int centerX = width / 2;
        int panelLeft = centerX - PANEL_WIDTH / 2;

        if (currentTab == Tab.PROTECTION) {
            int trackX = panelLeft + 10;
            int trackWidth = PANEL_WIDTH - PREVIEW_SIZE - 110;
            for (ProtectionSlider slider : protectionSliders) {
                if (slider.isDragging) {
                    updateProtectionSliderValue(slider, (int) mouseX, trackX, trackWidth);
                    return true;
                }
            }
        } else if (currentTab == Tab.ATTRIBUTES) {
            int trackX = panelLeft + 10;
            int trackWidth = PANEL_WIDTH - PREVIEW_SIZE - 120;
            for (AttributeSlider slider : attributeSliders) {
                if (slider.isDragging) {
                    updateAttributeSliderValue(slider, (int) mouseX, trackX, trackWidth);
                    return true;
                }
            }
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (ProtectionSlider slider : protectionSliders) {
            slider.isDragging = false;
        }
        for (AttributeSlider slider : attributeSliders) {
            slider.isDragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (currentTab == Tab.ENCHANTS) {
            if (showEnchantPicker) {
                // Scroll picker list
                int maxScroll = Math.max(0, allAvailableEnchants.size() - 10);
                pickerScrollOffset = Math.max(0, Math.min(maxScroll, pickerScrollOffset - (int) scrollY));
            } else {
                // Scroll enchantment list
                int maxScroll = Math.max(0, enchantments.size() - 5);
                enchantScrollOffset = Math.max(0, Math.min(maxScroll, enchantScrollOffset - (int) scrollY));
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void updateProtectionSliderValue(ProtectionSlider slider, int mouseX, int trackX, int trackWidth) {
        float normalized = (float) (mouseX - trackX) / trackWidth;
        normalized = Math.max(0f, Math.min(1f, normalized));
        if (slider.value != normalized) {
            slider.value = normalized;
            slider.triggerPulse();
            if (slider.inputField != null) {
                slider.inputField.setValue(Objects.requireNonNull(String.format("%.0f", slider.value * 100)));
            }
        }
    }

    private void updateAttributeSliderValue(AttributeSlider slider, int mouseX, int trackX, int trackWidth) {
        float normalized = (float) (mouseX - trackX) / trackWidth;
        normalized = Math.max(0f, Math.min(1f, normalized));
        float newValue = slider.min + (normalized * (slider.max - slider.min));
        if (slider.value != newValue) {
            slider.value = newValue;
            slider.triggerPulse();
            if (slider.inputField != null) {
                if (slider.max <= 1f) {
                    slider.inputField.setValue(Objects.requireNonNull(String.format("%.0f", slider.value * 100)));
                } else {
                    slider.inputField.setValue(Objects.requireNonNull(String.format("%.1f", slider.value)));
                }
            }
        }
    }

    private void applyChanges() {
        if (currentArmorPiece.isEmpty()) {
            showStatus("No armor to modify!", 0xFF5555);
            return;
        }

        // Build ArmorStats from UI values
        ArmorStats stats = new ArmorStats();
        if (protectionSliders.size() >= 5) {
            stats.physicalReduction = protectionSliders.get(0).value;
            stats.fireReduction = protectionSliders.get(1).value;
            stats.magicReduction = protectionSliders.get(2).value;
            stats.explosionReduction = protectionSliders.get(3).value;
            stats.projectileReduction = protectionSliders.get(4).value;
        }
        if (attributeSliders.size() >= 3) {
            stats.armorBonus = attributeSliders.get(0).value;
            stats.toughnessBonus = attributeSliders.get(1).value;
            stats.knockbackResistance = attributeSliders.get(2).value;
        }
        stats.thornsReflect = thornsEnabled;
        try {
            stats.thornsPercent = Float.parseFloat(thornsPercentField.getValue()) / 100f;
        } catch (NumberFormatException e) {
            stats.thornsPercent = 0f;
        }

        // Get item registry name for global config
        String itemName = Objects.requireNonNull(
            BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(currentArmorPiece.getItem()))).toString();

        // Collect enchantment changes (holders needed for server-side application)
        List<EnchantmentChange> enchantChanges = new ArrayList<>();
        for (EnchantmentEntry entry : enchantments) {
            Holder<Enchantment> holder = entry.getHolder();
            if (entry.toRemove) {
                enchantChanges.add(new EnchantmentChange(holder, 0)); // level 0 = remove
            } else {
                enchantChanges.add(new EnchantmentChange(holder, entry.level));
            }
        }

        // Send packet to server
        UpdateArmorPayload payload = UpdateArmorPayload.fromArmorStats(
            editGlobal,
            selectedSlot.index,
            stats,
            itemName
        );

        PacketDistributor.sendToServer(Objects.requireNonNull(payload));

        // Remove entries marked for removal from UI
        enchantments.removeIf(e -> e.toRemove);

        showStatus(editGlobal ? "Global armor config saved!" : "Armor stats updated!", 0x55FF55);
    }

    /** Represents a pending enchantment change to be sent to server */
    private record EnchantmentChange(Holder<Enchantment> holder, int level) {}

    private void loadAllAvailableEnchantments() {
        allAvailableEnchants.clear();
        Minecraft mc = Objects.requireNonNull(Minecraft.getInstance());
        if (mc.level == null) return;

        var registry = Objects.requireNonNull(mc.level).registryAccess().registryOrThrow(
            Objects.requireNonNull(net.minecraft.core.registries.Registries.ENCHANTMENT));
        registry.holders().forEach(holder -> allAvailableEnchants.add(holder));

        // Sort: minecraft first, then alphabetically
        allAvailableEnchants.sort((a, b) -> {
            String nsA = a.unwrapKey().map(k -> k.location().getNamespace()).orElse("");
            String nsB = b.unwrapKey().map(k -> k.location().getNamespace()).orElse("");
            if (!nsA.equals(nsB)) {
                if (nsA.equals("minecraft")) return -1;
                if (nsB.equals("minecraft")) return 1;
                return nsA.compareTo(nsB);
            }
            return formatEnchantmentName(a).compareTo(formatEnchantmentName(b));
        });
    }

    private void showStatus(String message, int color) {
        statusMessage = message;
        statusColor = color;
        statusTicks = 60; // 3 seconds
    }

    private int blendColors(int color1, int color2, float ratio) {
        int a1 = (color1 >> 24) & 0xFF, r1 = (color1 >> 16) & 0xFF, g1 = (color1 >> 8) & 0xFF, b1 = color1 & 0xFF;
        int a2 = (color2 >> 24) & 0xFF, r2 = (color2 >> 16) & 0xFF, g2 = (color2 >> 8) & 0xFF, b2 = color2 & 0xFF;
        int a = (int) (a1 + (a2 - a1) * ratio);
        int r = (int) (r1 + (r2 - r1) * ratio);
        int g = (int) (g1 + (g2 - g1) * ratio);
        int b = (int) (b1 + (b2 - b1) * ratio);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private String toRoman(int num) {
        if (num <= 0 || num > 10) return String.valueOf(num);
        String[] romans = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return romans[num - 1];
    }

    @Override
    public void onClose() {
        // Restore blur setting
        Minecraft mc = Objects.requireNonNull(Minecraft.getInstance());
        if (mc.options != null) {
            mc.options.menuBackgroundBlurriness().set(originalBlurValue);
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
