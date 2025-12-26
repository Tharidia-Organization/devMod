package com.devmod.client.ui.editor.sections;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import com.devmod.client.ui.editor.EditorSection;
import com.devmod.client.ui.editor.components.EditorSlider;
import com.devmod.client.ui.editor.core.EditorDimensions;
import com.devmod.client.ui.editor.core.ResponsiveLayout;
import com.devmod.client.ui.editor.core.UIConstants;

public final class AttributeListSection implements EditorSection.CustomSection {

    private static final int HEADER_HEIGHT = EditorDimensions.SECTION_HEADER_HEIGHT;
    private static final int ENTRY_HEIGHT = 44; // Slider height + padding
    private static final int TEXT_INSET_X = 8;
    private static final int BOTTOM_PADDING = 8;

    private final String id;
    private final String title;
    private final ItemStack item;
    private final List<AttributeEntry> entries = new ArrayList<>();
    private final Consumer<String> onModify;

    /**
     * Creates a new attribute list section.
     *
     * @param id       Section identifier
     * @param title    Section title
     * @param item     Item to read/write attributes from
     * @param onModify Callback when an attribute is modified (receives attribute name)
     */
    public AttributeListSection(String id, String title, ItemStack item, Consumer<String> onModify) {
        this.id = id;
        this.title = title;
        this.item = item;
        this.onModify = onModify;
        loadAttributes();
    }

    private void loadAttributes() {
        entries.clear();

        ItemAttributeModifiers mods = item.getOrDefault(
            Objects.requireNonNull(DataComponents.ATTRIBUTE_MODIFIERS),
            Objects.requireNonNull(ItemAttributeModifiers.EMPTY)
        );

        // Add standard vanilla attributes with default values
        addVanillaAttribute(Attributes.ATTACK_DAMAGE, "Attack Damage", 1.0, 0, 50, UIConstants.SliderColors.DAMAGE, mods);
        addVanillaAttribute(Attributes.ATTACK_SPEED, "Attack Speed", 4.0, 0, 10, UIConstants.SliderColors.SPEED, mods);
        addVanillaAttribute(Attributes.ARMOR, "Armor", 0.0, 0, 30, UIConstants.SliderColors.DEFENSE, mods);
        addVanillaAttribute(Attributes.ARMOR_TOUGHNESS, "Armor Toughness", 0.0, 0, 20, UIConstants.SliderColors.DEFENSE, mods);
        addVanillaAttribute(Attributes.KNOCKBACK_RESISTANCE, "Knockback Resistance", 0.0, 0, 1, UIConstants.SliderColors.NEUTRAL, mods);
        addVanillaAttribute(Attributes.ATTACK_KNOCKBACK, "Attack Knockback", 0.0, 0, 5, UIConstants.SliderColors.DAMAGE, mods);
        addVanillaAttribute(Attributes.MOVEMENT_SPEED, "Movement Speed", 0.1, 0, 1, UIConstants.SliderColors.SPEED, mods);
    }

    private void addVanillaAttribute(Holder<Attribute> attribute, String displayName, double defaultValue,
                                      double min, double max, int color, ItemAttributeModifiers mods) {
        // Find current value from item
        double currentValue = defaultValue;
        boolean hasModifier = false;

        for (ItemAttributeModifiers.Entry entry : mods.modifiers()) {
            if (entry.attribute().equals(attribute)) {
                AttributeModifier mod = entry.modifier();
                if (mod.operation() == AttributeModifier.Operation.ADD_VALUE) {
                    currentValue = defaultValue + mod.amount();
                    hasModifier = true;
                    break;
                }
            }
        }

        // Create slider
        String sliderId = "attr_" + getAttributeKey(attribute);
        EditorSlider slider = new EditorSlider(sliderId, displayName, (float) min, (float) max, (float) currentValue)
            .step(getStepForAttribute(attribute))
            .format(getFormatForAttribute(attribute))
            .suffix(getSuffixForAttribute(attribute))
            .trackColor(color)
            .showInput(true)
            .info(getInfoForAttribute(attribute))
            .onChange(value -> {
                updateAttribute(attribute, value, defaultValue);
                if (onModify != null) {
                    onModify.accept(displayName);
                }
            });

        entries.add(new AttributeEntry(attribute, displayName, slider, hasModifier, defaultValue));
    }

    private String getAttributeKey(Holder<Attribute> attribute) {
        ResourceLocation key = BuiltInRegistries.ATTRIBUTE.getKey(Objects.requireNonNull(attribute.value()));
        return key != null ? key.getPath() : "unknown";
    }

    private float getStepForAttribute(Holder<Attribute> attribute) {
        if (attribute.equals(Attributes.KNOCKBACK_RESISTANCE) || attribute.equals(Attributes.MOVEMENT_SPEED)) {
            return 0.01f;
        }
        if (attribute.equals(Attributes.ATTACK_SPEED)) {
            return 0.1f;
        }
        return 0.5f;
    }

    private String getFormatForAttribute(Holder<Attribute> attribute) {
        if (attribute.equals(Attributes.KNOCKBACK_RESISTANCE) || attribute.equals(Attributes.MOVEMENT_SPEED)) {
            return "%.2f";
        }
        if (attribute.equals(Attributes.ATTACK_SPEED)) {
            return "%.1f";
        }
        return "%.1f";
    }

    private String getSuffixForAttribute(Holder<Attribute> attribute) {
        if (attribute.equals(Attributes.ATTACK_SPEED)) return "/s";
        if (attribute.equals(Attributes.KNOCKBACK_RESISTANCE)) return "%";
        if (attribute.equals(Attributes.MOVEMENT_SPEED)) return " m/s";
        if (attribute.equals(Attributes.ARMOR) || attribute.equals(Attributes.ARMOR_TOUGHNESS)) return " pts";
        if (attribute.equals(Attributes.ATTACK_DAMAGE) || attribute.equals(Attributes.ATTACK_KNOCKBACK)) return " HP";
        return "";
    }

    private String getInfoForAttribute(Holder<Attribute> attribute) {
        if (attribute.equals(Attributes.ATTACK_DAMAGE)) {
            return "Base damage dealt per hit. Affected by Sharpness and Smite enchantments.";
        }
        if (attribute.equals(Attributes.ATTACK_SPEED)) {
            return "Attacks per second. Lower = slower but stronger hits. Default sword = 1.6/s.";
        }
        if (attribute.equals(Attributes.ARMOR)) {
            return "Reduces damage taken. Each point reduces damage by ~4% (with diminishing returns).";
        }
        if (attribute.equals(Attributes.ARMOR_TOUGHNESS)) {
            return "Reduces armor effectiveness loss from strong attacks. Netherite has 3.0.";
        }
        if (attribute.equals(Attributes.KNOCKBACK_RESISTANCE)) {
            return "Reduces knockback received. 1.0 = immune to knockback. Netherite gives 0.1 per piece.";
        }
        if (attribute.equals(Attributes.ATTACK_KNOCKBACK)) {
            return "Extra knockback dealt to targets. Stacks with Knockback enchantment.";
        }
        if (attribute.equals(Attributes.MOVEMENT_SPEED)) {
            return "Walking speed modifier. Default player speed is 0.1. Higher = faster movement.";
        }
        return "";
    }

    private void updateAttribute(Holder<Attribute> attribute, double newValue, double defaultValue) {
        ItemAttributeModifiers existing = item.getOrDefault(
            Objects.requireNonNull(DataComponents.ATTRIBUTE_MODIFIERS),
            Objects.requireNonNull(ItemAttributeModifiers.EMPTY)
        );

        List<ItemAttributeModifiers.Entry> newEntries = new ArrayList<>();

        // Copy existing entries, except the one we're updating
        for (ItemAttributeModifiers.Entry entry : existing.modifiers()) {
            if (!entry.attribute().equals(attribute)) {
                newEntries.add(entry);
            }
        }

        // Add new modifier if value differs from default
        double diff = newValue - defaultValue;
        if (Math.abs(diff) > 0.001) {
            ResourceLocation modId = Objects.requireNonNull(
                ResourceLocation.fromNamespaceAndPath("devmod", "editor_" + getAttributeKey(attribute)));
            AttributeModifier modifier = new AttributeModifier(
                modId,
                diff,
                AttributeModifier.Operation.ADD_VALUE
            );
            EquipmentSlotGroup slot = Objects.requireNonNull(getSlotForItem());
            newEntries.add(new ItemAttributeModifiers.Entry(
                Objects.requireNonNull(attribute),
                modifier,
                slot
            ));
        }

        item.set(
            Objects.requireNonNull(DataComponents.ATTRIBUTE_MODIFIERS),
            new ItemAttributeModifiers(newEntries, existing.showInTooltip())
        );
    }

    private EquipmentSlotGroup getSlotForItem() {
        // Determine appropriate slot based on item type
        if (item.getItem() instanceof net.minecraft.world.item.ArmorItem armor) {
            return switch (armor.getEquipmentSlot()) {
                case HEAD -> EquipmentSlotGroup.HEAD;
                case CHEST -> EquipmentSlotGroup.CHEST;
                case LEGS -> EquipmentSlotGroup.LEGS;
                case FEET -> EquipmentSlotGroup.FEET;
                default -> EquipmentSlotGroup.MAINHAND;
            };
        }
        return EquipmentSlotGroup.MAINHAND;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getLabel() {
        return title;
    }

    @Override
    public int getHeight() {
        return HEADER_HEIGHT + entries.size() * ENTRY_HEIGHT + BOTTOM_PADDING;
    }

    @Override
    public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
        Font font = Objects.requireNonNull(Minecraft.getInstance().font, "font");

        int y = bounds.y();

        // Header
        graphics.fill(bounds.x(), y, bounds.x() + bounds.width(), y + HEADER_HEIGHT,
            UIConstants.Background.HEADER());
        graphics.drawString(font, title, bounds.x() + TEXT_INSET_X,
            y + (HEADER_HEIGHT - 8) / 2, UIConstants.Text.TITLE(), false);
        y += HEADER_HEIGHT;

        // Render each attribute slider
        for (AttributeEntry entry : entries) {
            entry.slider.render(graphics, bounds.x() + 4, y + 4, bounds.width() - 8, mouseX, mouseY);
            y += ENTRY_HEIGHT;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (AttributeEntry entry : entries) {
            if (entry.slider.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (AttributeEntry entry : entries) {
            if (entry.slider.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        for (AttributeEntry entry : entries) {
            if (entry.slider.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (AttributeEntry entry : entries) {
            if (entry.slider.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        for (AttributeEntry entry : entries) {
            if (entry.slider.charTyped(chr, modifiers)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Internal entry representing one attribute.
     */
    private record AttributeEntry(
        Holder<Attribute> attribute,
        String displayName,
        EditorSlider slider,
        boolean hasModifier,
        double defaultValue
    ) {}
}
