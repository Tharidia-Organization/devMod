package com.devmod.client.ui.editor.sections;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.EditorSection;
import com.devmod.client.ui.editor.components.EditorSlider;
import com.devmod.client.ui.editor.components.EditorTextField;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.editor.core.EditorDimensions;
import com.devmod.client.ui.editor.core.ResponsiveLayout;

public final class AttributeListSection implements EditorSection.CustomSection {

    private static final int HEADER_HEIGHT = EditorDimensions.SECTION_HEADER_HEIGHT;
    private static final int ENTRY_HEIGHT = 44; // Slider height + padding
    private static final int TEXT_INSET_X = 8;
    private static final int BOTTOM_PADDING = 8;
    private static final int SEARCH_TOP_PADDING = 4;
    private static final int SEARCH_BOTTOM_PADDING = 6;
    private static final int EMPTY_STATE_HEIGHT = 16;
    private static final String EMPTY_STATE_TEXT = "No attributes match this filter";
    private static final int GROUP_HEADER_HEIGHT = 14;
    private static final AttributeGroup[] GROUP_ORDER = new AttributeGroup[] {
        AttributeGroup.VANILLA,
        AttributeGroup.DEVMOD,
        AttributeGroup.MODDED
    };

    private enum AttributeGroup {
        VANILLA("Vanilla"),
        DEVMOD("DevMod"),
        MODDED("Modded");

        private final String label;

        AttributeGroup(String label) {
            this.label = label;
        }
    }

    private final String id;
    private final String title;
    private final ItemStack item;
    private final List<AttributeEntry> entries = new ArrayList<>();
    private final List<AttributeEntry> filteredEntries = new ArrayList<>();
    private final EditorTextField searchField;
    private final Consumer<String> onModify;
    private String searchQuery = "";

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
        this.searchField = new EditorTextField(id + "_search", "Search")
            .placeholder("Filter attributes")
            .maxLength(64)
            .onChange(this::onSearchChanged);
        loadAttributes();
    }

    private void loadAttributes() {
        entries.clear();

        ItemAttributeModifiers mods = item.getOrDefault(
            Objects.requireNonNull(DataComponents.ATTRIBUTE_MODIFIERS),
            Objects.requireNonNull(ItemAttributeModifiers.EMPTY)
        );

        for (var entry : BuiltInRegistries.ATTRIBUTE.entrySet()) {
            ResourceKey<Attribute> key = entry.getKey();
            ResourceLocation id = key.location();
            Attribute attr = entry.getValue();
            Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.getHolder(key).orElse(null);
            if (holder == null || attr == null) {
                continue;
            }
            addAttribute(holder, id, mods);
        }

        entries.sort((a, b) -> {
            int groupCompare = Integer.compare(getGroupFor(a).ordinal(), getGroupFor(b).ordinal());
            if (groupCompare != 0) return groupCompare;
            int nameCompare = a.displayName.compareToIgnoreCase(b.displayName);
            if (nameCompare != 0) return nameCompare;
            return a.id.toString().compareToIgnoreCase(b.id.toString());
        });
        updateFilter();
    }

    private void addAttribute(Holder<Attribute> attribute, ResourceLocation id, ItemAttributeModifiers mods) {
        String displayName = resolveDisplayName(attribute, id);
        double defaultValue = attribute.value().getDefaultValue();
        SliderConfig sliderConfig = resolveSliderConfig(attribute, defaultValue);
        // Find current value from item
        double currentValue = defaultValue;
        boolean hasModifier = false;

        for (ItemAttributeModifiers.Entry entry : mods.modifiers()) {
            if (entry.attribute().equals(attribute)) {
                AttributeModifier mod = entry.modifier();
                hasModifier = true;
                if (mod.operation() == AttributeModifier.Operation.ADD_VALUE) {
                    currentValue += mod.amount();
                }
            }
        }

        // Create slider
        String sliderId = "attr_" + getAttributePath(attribute);
        EditorSlider slider = new EditorSlider(sliderId, displayName,
            (float) sliderConfig.min(), (float) sliderConfig.max(), (float) currentValue)
            .step(sliderConfig.step())
            .format(sliderConfig.format())
            .suffix(sliderConfig.suffix())
            .trackColor(sliderConfig.color())
            .showInput(true)
            .info(buildInfoText(attribute, id))
            .onChange(value -> {
                updateAttribute(attribute, value, defaultValue);
                if (onModify != null) {
                    onModify.accept(displayName);
                }
            });

        entries.add(new AttributeEntry(attribute, id, displayName, slider, hasModifier, defaultValue));
    }

    private void onSearchChanged(String text) {
        searchQuery = text == null ? "" : text;
        updateFilter();
    }

    private void updateFilter() {
        filteredEntries.clear();
        String query = searchQuery.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            filteredEntries.addAll(entries);
            return;
        }
        for (AttributeEntry entry : entries) {
            if (matchesFilter(entry, query)) {
                filteredEntries.add(entry);
            }
        }
    }

    private boolean matchesFilter(AttributeEntry entry, String query) {
        String name = entry.displayName.toLowerCase(Locale.ROOT);
        String key = entry.id.toString().toLowerCase(Locale.ROOT);
        return name.contains(query) || key.contains(query);
    }

    private AttributeGroup getGroupFor(AttributeEntry entry) {
        String namespace = entry.id.getNamespace();
        if ("minecraft".equals(namespace)) {
            return AttributeGroup.VANILLA;
        }
        if ("devmod".equals(namespace)) {
            return AttributeGroup.DEVMOD;
        }
        return AttributeGroup.MODDED;
    }

    private int getGroupedHeight() {
        int height = 0;
        for (AttributeGroup group : GROUP_ORDER) {
            int count = 0;
            for (AttributeEntry entry : filteredEntries) {
                if (getGroupFor(entry) == group) {
                    count++;
                }
            }
            if (count > 0) {
                height += GROUP_HEADER_HEIGHT + count * ENTRY_HEIGHT;
            }
        }
        return height;
    }

    private String getAttributePath(Holder<Attribute> attribute) {
        ResourceLocation key = BuiltInRegistries.ATTRIBUTE.getKey(Objects.requireNonNull(attribute.value()));
        return key != null ? key.getPath() : "unknown";
    }

    private SliderConfig resolveSliderConfig(Holder<Attribute> attribute, double defaultValue) {
        if (attribute.equals(Attributes.ATTACK_DAMAGE)) {
            return new SliderConfig(0, 50, 0.5f, "%.1f", " HP", DesignTokens.SliderColors.DAMAGE);
        }
        if (attribute.equals(Attributes.ATTACK_SPEED)) {
            return new SliderConfig(0, 10, 0.1f, "%.1f", "/s", DesignTokens.SliderColors.SPEED);
        }
        if (attribute.equals(Attributes.ARMOR)) {
            return new SliderConfig(0, 30, 0.5f, "%.1f", " pts", DesignTokens.SliderColors.DEFENSE);
        }
        if (attribute.equals(Attributes.ARMOR_TOUGHNESS)) {
            return new SliderConfig(0, 20, 0.5f, "%.1f", " pts", DesignTokens.SliderColors.DEFENSE);
        }
        if (attribute.equals(Attributes.KNOCKBACK_RESISTANCE)) {
            return new SliderConfig(0, 1, 0.01f, "%.2f", "%", DesignTokens.SliderColors.NEUTRAL);
        }
        if (attribute.equals(Attributes.ATTACK_KNOCKBACK)) {
            return new SliderConfig(0, 5, 0.1f, "%.1f", " HP", DesignTokens.SliderColors.DAMAGE);
        }
        if (attribute.equals(Attributes.MOVEMENT_SPEED)) {
            return new SliderConfig(0, 1, 0.01f, "%.2f", " m/s", DesignTokens.SliderColors.SPEED);
        }
        double min = defaultValue - 10.0;
        double max = defaultValue + 10.0;
        if (Math.abs(defaultValue) < 1.0) {
            min = -1.0;
            max = 1.0;
        }
        return new SliderConfig(min, max, 0.1f, "%.2f", "", DesignTokens.SliderColors.NEUTRAL);
    }

    private String resolveDisplayName(Holder<Attribute> attribute, ResourceLocation id) {
        String translated = net.minecraft.client.resources.language.I18n.get(attribute.value().getDescriptionId());
        if (translated != null && !translated.isBlank() && !translated.equals(attribute.value().getDescriptionId())) {
            return translated;
        }
        return id.getPath().replace('_', ' ');
    }

    private String buildInfoText(Holder<Attribute> attribute, ResourceLocation id) {
        String info = getInfoForAttribute(attribute);
        String idLine = "ID: " + id;
        if (info.isBlank()) {
            return idLine;
        }
        return info + "\n" + idLine;
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
                ResourceLocation.fromNamespaceAndPath("devmod", "editor_" + getAttributePath(attribute)));
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
        int listHeight = filteredEntries.isEmpty() ? EMPTY_STATE_HEIGHT : getGroupedHeight();
        return HEADER_HEIGHT + SEARCH_TOP_PADDING + searchField.calculateHeight()
            + SEARCH_BOTTOM_PADDING + listHeight + BOTTOM_PADDING;
    }

    @Override
    public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
        Font font = Objects.requireNonNull(Minecraft.getInstance().font, "font");

        int y = bounds.y();

        // Header
        graphics.fill(bounds.x(), y, bounds.x() + bounds.width(), y + HEADER_HEIGHT,
            DesignTokens.Background.HEADER());
        UIScaleManager.drawScaledString(graphics, font, title, bounds.x() + TEXT_INSET_X,
            y + (HEADER_HEIGHT - 8) / 2, DesignTokens.Text.TITLE(), false);
        y += HEADER_HEIGHT;

        y += SEARCH_TOP_PADDING;
        searchField.render(graphics, bounds.x() + TEXT_INSET_X, y,
            bounds.width() - TEXT_INSET_X * 2, mouseX, mouseY);
        y += searchField.calculateHeight() + SEARCH_BOTTOM_PADDING;

        if (filteredEntries.isEmpty()) {
            UIScaleManager.drawScaledString(graphics, font, EMPTY_STATE_TEXT, bounds.x() + TEXT_INSET_X, y,
                DesignTokens.Text.MUTED(), false);
            return;
        }

        for (AttributeGroup group : GROUP_ORDER) {
            boolean hasGroup = false;
            for (AttributeEntry entry : filteredEntries) {
                if (getGroupFor(entry) == group) {
                    hasGroup = true;
                    break;
                }
            }
            if (!hasGroup) {
                continue;
            }
            UIScaleManager.drawScaledString(graphics, font, group.label, bounds.x() + TEXT_INSET_X, y,
                DesignTokens.Text.SECONDARY(), false);
            y += GROUP_HEADER_HEIGHT;
            for (AttributeEntry entry : filteredEntries) {
                if (getGroupFor(entry) != group) {
                    continue;
                }
                entry.slider.render(graphics, bounds.x() + 4, y + 4, bounds.width() - 8, mouseX, mouseY);
                y += ENTRY_HEIGHT;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (searchField.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        for (AttributeEntry entry : filteredEntries) {
            if (entry.slider.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (AttributeEntry entry : filteredEntries) {
            if (entry.slider.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        for (AttributeEntry entry : filteredEntries) {
            if (entry.slider.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        for (AttributeEntry entry : filteredEntries) {
            if (entry.slider.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchField.charTyped(chr, modifiers)) {
            return true;
        }
        for (AttributeEntry entry : filteredEntries) {
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
        ResourceLocation id,
        String displayName,
        EditorSlider slider,
        boolean hasModifier,
        double defaultValue
    ) {}

    private record SliderConfig(
        double min,
        double max,
        float step,
        String format,
        String suffix,
        int color
    ) {}
}
