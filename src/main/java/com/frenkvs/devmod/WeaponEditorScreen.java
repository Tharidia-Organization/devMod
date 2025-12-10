package com.frenkvs.devmod;

import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.UIConstants;
import com.frenkvs.devmod.util.I18n;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Complete Item Editor with multiple tabs:
 * - Stats: Body part multipliers, damage modifiers
 * - Enchantments: Add/remove/modify enchantments
 * - Durability: Set durability, unbreakable, repair cost
 * - Attributes: Attack damage, speed, armor, etc.
 * - NBT: Raw component data viewer
 */
@SuppressWarnings("null")
public class WeaponEditorScreen extends Screen {

    // Layout
    private static final int PANEL_WIDTH = 450;
    private static final int PANEL_HEIGHT = 400;
    private static final int TAB_HEIGHT = 24;
    private static final int PREVIEW_SIZE = 80;

    // Tabs
    private enum Tab { STATS, ENCHANTS, DURABILITY, ATTRIBUTES, COMPONENTS }
    private Tab currentTab = Tab.STATS;

    // Animation
    private float rotationAngle = 0f;

    // State
    private final ItemStack stack;
    private boolean editGlobal = false;

    // Stats tab
    private final List<StatSlider> statSliders = new ArrayList<>();

    // Enchantments tab
    private final List<EnchantmentEntry> enchantments = new ArrayList<>();
    private int enchantScrollOffset = 0;
    private String enchantSearch = "";
    private EditBox enchantSearchBox;

    // Enchantment picker (shows ALL enchantments from ALL mods)
    private boolean showEnchantPicker = false;
    private final List<Holder<Enchantment>> allAvailableEnchants = new ArrayList<>();
    private int pickerScrollOffset = 0;
    private String pickerSearch = "";
    private EditBox pickerSearchBox;

    // Attribute picker (shows ALL attributes from ALL mods)
    private boolean showAttributePicker = false;
    private final List<Holder<Attribute>> allAvailableAttributes = new ArrayList<>();
    private int attrPickerScrollOffset = 0;
    private String attrPickerSearch = "";
    private EditBox attrPickerSearchBox;

    // Undo/Redo system
    private final java.util.Deque<EditorState> undoStack = new java.util.ArrayDeque<>();
    private final java.util.Deque<EditorState> redoStack = new java.util.ArrayDeque<>();
    private static final int MAX_UNDO_STATES = 50;

    // Presets (uses persistent storage via ItemEditorDataManager)
    private boolean showPresetMenu = false;
    private String presetName = "";
    private EditBox presetNameBox;
    private int presetScrollOffset = 0;

    // Favorites
    private boolean showFavoritesOnly = false;

    // Enchantment Filters
    private enum EnchantFilter { ALL, FAVORITES, COMPATIBLE, WEAPON, ARMOR, TOOL }
    private EnchantFilter currentEnchantFilter = EnchantFilter.ALL;

    // History panel
    private boolean showHistoryPanel = false;
    private int historyScrollOffset = 0;

    // Export/Import
    private boolean showExportImportMenu = false;
    private EditBox exportNameBox;
    private String exportName = "";
    private int importScrollOffset = 0;

    // Batch mode
    private boolean batchModeEnabled = false;

    // Compare mode
    private boolean showComparePanel = false;
    private ItemStack compareStack = ItemStack.EMPTY;

    // Templates
    private boolean showTemplateMenu = false;
    private int templateScrollOffset = 0;

    // Durability tab
    private EditBox durabilityField;
    private EditBox repairCostField;
    private boolean isUnbreakable = false;

    // Attributes tab
    private final List<AttributeEntry> attributes = new ArrayList<>();

    // Feedback
    private String statusMessage = null;
    private int statusColor = 0;
    private int statusTicks = 0;

    // Blur control
    private int originalBlurValue = 0;

    // Helper classes
    private static class StatSlider {
        final String name;
        final String tooltip;
        final int color;
        final float min, max;
        float value;
        EditBox inputField;
        boolean isDragging = false;

        StatSlider(String name, String tooltip, int color, float min, float max, float defaultVal) {
            this.name = name;
            this.tooltip = tooltip;
            this.color = color;
            this.min = min;
            this.max = max;
            this.value = defaultVal;
        }
    }

    private static class EnchantmentEntry {
        final Holder<Enchantment> enchantment;
        final String name;
        int level;
        boolean toRemove = false;

        EnchantmentEntry(Holder<Enchantment> ench, String name, int level) {
            this.enchantment = ench;
            this.name = name;
            this.level = level;
        }
    }

    private static class AttributeEntry {
        final String name;
        final Holder<Attribute> attribute;
        double value;
        AttributeModifier.Operation operation;
        EditBox inputField;

        AttributeEntry(String name, Holder<Attribute> attr, double value, AttributeModifier.Operation op) {
            this.name = name;
            this.attribute = attr;
            this.value = value;
            this.operation = op;
        }

        AttributeEntry copy() {
            return new AttributeEntry(name, attribute, value, operation);
        }
    }

    // State snapshot for undo/redo
    private static class EditorState {
        final List<Float> statValues;
        final List<EnchantmentSnapshot> enchantments;
        final List<AttributeSnapshot> attributes;
        final int durability;
        final int repairCost;
        final boolean unbreakable;

        EditorState(List<Float> stats, List<EnchantmentSnapshot> enchs, List<AttributeSnapshot> attrs,
                    int dura, int repair, boolean unbreak) {
            this.statValues = new ArrayList<>(stats);
            this.enchantments = new ArrayList<>(enchs);
            this.attributes = new ArrayList<>(attrs);
            this.durability = dura;
            this.repairCost = repair;
            this.unbreakable = unbreak;
        }
    }

    private record EnchantmentSnapshot(Holder<Enchantment> ench, String name, int level, boolean removed) {}
    private record AttributeSnapshot(Holder<Attribute> attr, String name, double value, AttributeModifier.Operation op) {}

    public WeaponEditorScreen() {
        super(I18n.screenTitle("weapon_editor"));
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        this.stack = player != null ? player.getMainHandItem().copy() : ItemStack.EMPTY;

        // Disable blur
        if (mc.options != null) {
            OptionInstance<Integer> blurOption = mc.options.menuBackgroundBlurriness();
            originalBlurValue = blurOption.get();
            blurOption.set(0);
        }

        initStatSliders();
        loadEnchantments();
        loadAttributes();
    }

    private void initStatSliders() {
        WeaponStats stats = WeaponConfigManager.getStats(stack);

        statSliders.add(new StatSlider("Head Multiplier",
            "Damage multiplier for headshots", UIConstants.BodyPart.HEAD, 0.5f, 5.0f, stats.headMult));
        statSliders.add(new StatSlider("Body Multiplier",
            "Damage multiplier for body hits", UIConstants.BodyPart.BODY, 0.5f, 3.0f, stats.bodyMult));
        statSliders.add(new StatSlider("Legs Multiplier",
            "Damage multiplier for leg hits", UIConstants.BodyPart.LEGS, 0.25f, 2.0f, stats.legsMult));
        statSliders.add(new StatSlider("Armor Penetration",
            "% of armor ignored (0-100)", UIConstants.Accent.ORANGE, 0f, 100f, stats.armorPenetration));
        statSliders.add(new StatSlider("Bonus Damage",
            "Flat damage added to hits", UIConstants.Accent.RED, -10f, 50f, stats.baseDamageBonus));
    }

    private void loadEnchantments() {
        enchantments.clear();
        if (stack.isEmpty()) return;

        // Use NeoForge's getAllEnchantments for full mod compatibility
        // This detects enchantments from ALL mods (Iron's Spells, Apotheosis, etc.)
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            var lookup = mc.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
            // IItemStackExtension.getAllEnchantments returns gameplay-relevant enchantments
            var allEnchants = stack.getAllEnchantments(lookup);
            allEnchants.entrySet().forEach(entry -> {
                Holder<Enchantment> ench = entry.getKey();
                int level = entry.getIntValue();
                String name = formatEnchantmentName(ench);
                enchantments.add(new EnchantmentEntry(ench, name, level));
            });
        } else {
            // Fallback to DataComponents if no level available
            ItemEnchantments itemEnchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            itemEnchants.entrySet().forEach(entry -> {
                Holder<Enchantment> ench = entry.getKey();
                int level = entry.getIntValue();
                String name = formatEnchantmentName(ench);
                enchantments.add(new EnchantmentEntry(ench, name, level));
            });
        }
    }

    private String formatEnchantmentName(Holder<Enchantment> ench) {
        // Get the enchantment key and format it nicely
        return ench.unwrapKey()
            .map(key -> {
                String path = key.location().getPath();
                String namespace = key.location().getNamespace();
                String name = Arrays.stream(path.split("_"))
                    .map(s -> s.isEmpty() ? s : s.substring(0, 1).toUpperCase() + s.substring(1))
                    .reduce((a, b) -> a + " " + b)
                    .orElse(path);
                // Add mod prefix for non-minecraft enchantments
                if (!namespace.equals("minecraft")) {
                    name = "[" + namespace + "] " + name;
                }
                return name;
            })
            .orElse("Unknown");
    }

    /**
     * Load ALL available enchantments from the registry (vanilla + ALL mods).
     * This allows adding any enchantment from any installed mod.
     */
    private void loadAllAvailableEnchantments() {
        allAvailableEnchants.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        var registry = mc.level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);

        // Add all enchantments from the registry
        registry.holders().forEach(holder -> {
            allAvailableEnchants.add(holder);
        });

        // Sort by namespace then by name
        allAvailableEnchants.sort((a, b) -> {
            String nsA = a.unwrapKey().map(k -> k.location().getNamespace()).orElse("");
            String nsB = b.unwrapKey().map(k -> k.location().getNamespace()).orElse("");
            if (!nsA.equals(nsB)) {
                // minecraft first, then alphabetically
                if (nsA.equals("minecraft")) return -1;
                if (nsB.equals("minecraft")) return 1;
                return nsA.compareTo(nsB);
            }
            return formatEnchantmentName(a).compareTo(formatEnchantmentName(b));
        });
    }

    /**
     * Get filtered list of available enchantments for picker.
     * Supports multiple filter types: ALL, FAVORITES, COMPATIBLE, WEAPON, ARMOR, TOOL
     */
    private List<Holder<Enchantment>> getFilteredAvailableEnchants() {
        java.util.stream.Stream<Holder<Enchantment>> stream = allAvailableEnchants.stream();

        // Apply category filter
        switch (currentEnchantFilter) {
            case FAVORITES -> {
                java.util.Set<String> favs = ItemEditorDataManager.INSTANCE.getFavoriteEnchantments();
                stream = stream.filter(e -> {
                    String id = e.unwrapKey().map(k -> k.location().toString()).orElse("");
                    return favs.contains(id);
                });
            }
            case COMPATIBLE -> {
                // Filter to enchantments compatible with current item
                stream = stream.filter(this::isEnchantmentCompatibleWithItem);
            }
            case WEAPON -> {
                stream = stream.filter(e -> isEnchantmentType(e, "weapon"));
            }
            case ARMOR -> {
                stream = stream.filter(e -> isEnchantmentType(e, "armor"));
            }
            case TOOL -> {
                stream = stream.filter(e -> isEnchantmentType(e, "tool"));
            }
            case ALL -> {} // No filtering
        }

        // Apply search filter
        if (!pickerSearch.isEmpty()) {
            String search = pickerSearch.toLowerCase();
            stream = stream.filter(e -> formatEnchantmentName(e).toLowerCase().contains(search));
        }

        return stream.toList();
    }

    /**
     * Check if an enchantment is compatible with the current item.
     * Uses deprecated API as Minecraft 1.21's enchantment system is data-driven
     * and there's no stable non-deprecated alternative for this check.
     */
    @SuppressWarnings("deprecation")
    private boolean isEnchantmentCompatibleWithItem(Holder<Enchantment> ench) {
        if (stack.isEmpty()) return true;
        try {
            Enchantment enchantment = ench.value();
            // Check if item supports this enchantment
            return enchantment.isSupportedItem(stack);
        } catch (Exception e) {
            return true; // If we can't check, allow it
        }
    }

    /**
     * Check if enchantment belongs to a category (weapon/armor/tool).
     * Uses heuristics based on enchantment name and supported items.
     */
    private boolean isEnchantmentType(Holder<Enchantment> ench, String type) {
        String name = formatEnchantmentName(ench).toLowerCase();
        String id = ench.unwrapKey().map(k -> k.location().getPath()).orElse("").toLowerCase();

        return switch (type) {
            case "weapon" -> {
                // Weapon enchantments typically have these keywords
                yield name.contains("sharp") || name.contains("smite") || name.contains("bane") ||
                      name.contains("knockback") || name.contains("fire") || name.contains("loot") ||
                      name.contains("sweep") || name.contains("damage") || name.contains("attack") ||
                      id.contains("sharpness") || id.contains("smite") || id.contains("bane") ||
                      id.contains("knockback") || id.contains("fire_aspect") || id.contains("looting") ||
                      id.contains("sweeping");
            }
            case "armor" -> {
                // Armor enchantments
                yield name.contains("protection") || name.contains("thorns") || name.contains("respir") ||
                      name.contains("aqua") || name.contains("feather") || name.contains("frost") ||
                      name.contains("depth") || name.contains("soul") || name.contains("swift") ||
                      id.contains("protection") || id.contains("thorns") || id.contains("respiration") ||
                      id.contains("aqua_affinity") || id.contains("feather_falling") || id.contains("frost_walker") ||
                      id.contains("depth_strider") || id.contains("soul_speed") || id.contains("swift_sneak");
            }
            case "tool" -> {
                // Tool enchantments
                yield name.contains("efficien") || name.contains("silk") || name.contains("fortune") ||
                      name.contains("unbreak") || name.contains("mending") ||
                      id.contains("efficiency") || id.contains("silk_touch") || id.contains("fortune") ||
                      id.contains("unbreaking") || id.contains("mending");
            }
            default -> true;
        };
    }

    /**
     * Add enchantment from picker to the item.
     */
    private void addEnchantmentFromPicker(Holder<Enchantment> ench) {
        saveUndoState();
        // Check if already present
        for (EnchantmentEntry entry : enchantments) {
            if (entry.enchantment.equals(ench)) {
                entry.level = Math.min(255, entry.level + 1);
                entry.toRemove = false;
                showStatus("Increased " + entry.name + " level!", UIConstants.Accent.GREEN);
                showEnchantPicker = false;
                updateFieldVisibility();
                return;
            }
        }
        // Add new
        String name = formatEnchantmentName(ench);
        enchantments.add(new EnchantmentEntry(ench, name, 1));
        showStatus("Added " + name + "!", UIConstants.Accent.GREEN);
        showEnchantPicker = false;
        updateFieldVisibility();
    }

    private void loadAttributes() {
        attributes.clear();
        if (stack.isEmpty()) return;

        // Load ALL attributes from the item (vanilla + modded)
        // This automatically detects attributes from ANY mod
        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);

        // Track which attributes we've added to avoid duplicates
        Set<Holder<Attribute>> addedAttrs = new HashSet<>();

        // First add all existing modifiers from the item
        for (var entry : modifiers.modifiers()) {
            Holder<Attribute> attr = entry.attribute();
            if (!addedAttrs.contains(attr)) {
                String name = formatAttributeName(attr);
                attributes.add(new AttributeEntry(name, attr, entry.modifier().amount(), entry.modifier().operation()));
                addedAttrs.add(attr);
            }
        }

        // Then add common vanilla attributes if not present (for convenience)
        addDefaultAttribute(addedAttrs, "Attack Damage", Attributes.ATTACK_DAMAGE);
        addDefaultAttribute(addedAttrs, "Attack Speed", Attributes.ATTACK_SPEED);
        addDefaultAttribute(addedAttrs, "Attack Knockback", Attributes.ATTACK_KNOCKBACK);
        addDefaultAttribute(addedAttrs, "Armor", Attributes.ARMOR);
        addDefaultAttribute(addedAttrs, "Armor Toughness", Attributes.ARMOR_TOUGHNESS);
        addDefaultAttribute(addedAttrs, "Movement Speed", Attributes.MOVEMENT_SPEED);
    }

    private void addDefaultAttribute(java.util.Set<Holder<Attribute>> added, String name, Holder<Attribute> attr) {
        if (!added.contains(attr)) {
            attributes.add(new AttributeEntry(name, attr, 0, AttributeModifier.Operation.ADD_VALUE));
            added.add(attr);
        }
    }

    private String formatAttributeName(Holder<Attribute> attr) {
        return attr.unwrapKey()
            .map(key -> {
                String path = key.location().getPath();
                // Remove "generic." prefix if present
                if (path.startsWith("generic.")) {
                    path = path.substring(8);
                }
                return Arrays.stream(path.split("_"))
                    .map(s -> s.isEmpty() ? s : s.substring(0, 1).toUpperCase() + s.substring(1))
                    .reduce((a, b) -> a + " " + b)
                    .orElse(path);
            })
            .orElse("Unknown");
    }

    @Override
    protected void init() {
        if (font == null) return;

        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;

        // Enchantment search box
        enchantSearchBox = new EditBox(font, panelX + 120, panelY + TAB_HEIGHT + 40, 150, 16, I18n.ui("search"));
        enchantSearchBox.setHint(I18n.translate("devmod.ui.search_enchantments"));
        enchantSearchBox.setResponder(s -> enchantSearch = s.toLowerCase());
        this.addRenderableWidget(enchantSearchBox);
        enchantSearchBox.visible = false;

        // Picker search box (for enchantment picker overlay)
        int pickerX = panelX + 20;
        int pickerY = panelY + TAB_HEIGHT + PREVIEW_SIZE + 20;
        pickerSearchBox = new EditBox(font, pickerX + 10, pickerY + 22, 200, 14, I18n.ui("search"));
        pickerSearchBox.setHint(I18n.translate("devmod.ui.search_all_enchantments"));
        pickerSearchBox.setResponder(s -> { pickerSearch = s.toLowerCase(); pickerScrollOffset = 0; });
        this.addRenderableWidget(pickerSearchBox);
        pickerSearchBox.visible = false;

        // Attribute picker search box
        attrPickerSearchBox = new EditBox(font, pickerX + 10, pickerY + 22, 200, 14, I18n.ui("search"));
        attrPickerSearchBox.setHint(I18n.translate("devmod.ui.search_all_attributes"));
        attrPickerSearchBox.setResponder(s -> { attrPickerSearch = s.toLowerCase(); attrPickerScrollOffset = 0; });
        this.addRenderableWidget(attrPickerSearchBox);
        attrPickerSearchBox.visible = false;

        // Preset name box
        presetNameBox = new EditBox(font, panelX + 100, panelY + PANEL_HEIGHT - 80, 150, 16, I18n.translate("devmod.ui.preset"));
        presetNameBox.setHint(I18n.translate("devmod.ui.preset_name"));
        presetNameBox.setResponder(s -> presetName = s);
        this.addRenderableWidget(presetNameBox);
        presetNameBox.visible = false;

        // Durability fields
        durabilityField = new EditBox(font, panelX + 200, panelY + TAB_HEIGHT + 60, 80, 16, Component.empty());
        durabilityField.setMaxLength(6);
        this.addRenderableWidget(durabilityField);
        durabilityField.visible = false;

        repairCostField = new EditBox(font, panelX + 200, panelY + TAB_HEIGHT + 90, 80, 16, Component.empty());
        repairCostField.setMaxLength(4);
        this.addRenderableWidget(repairCostField);
        repairCostField.visible = false;

        // Stat input fields
        int inputX = panelX + PANEL_WIDTH - 70;
        int startY = panelY + TAB_HEIGHT + PREVIEW_SIZE + 30;

        for (int i = 0; i < statSliders.size(); i++) {
            StatSlider stat = statSliders.get(i);
            int y = startY + i * 28;

            EditBox field = new EditBox(font, inputX, y + 4, 50, 16, Component.empty());
            field.setMaxLength(8);
            field.setValue(formatFloat(stat.value));

            final StatSlider currentStat = stat;
            field.setResponder(text -> {
                try {
                    float val = Float.parseFloat(text.trim());
                    if (val >= currentStat.min && val <= currentStat.max) {
                        currentStat.value = val;
                    }
                } catch (NumberFormatException ignored) {}
            });

            this.addRenderableWidget(field);
            stat.inputField = field;
        }

        // Attribute input fields
        for (int i = 0; i < attributes.size(); i++) {
            AttributeEntry attr = attributes.get(i);
            int y = startY + i * 28;

            EditBox field = new EditBox(font, inputX, y + 4, 50, 16, Component.empty());
            field.setMaxLength(10);
            field.setValue(formatDouble(attr.value));
            field.visible = false;

            final AttributeEntry currentAttr = attr;
            field.setResponder(text -> {
                try {
                    currentAttr.value = Double.parseDouble(text.trim());
                } catch (NumberFormatException ignored) {}
            });

            this.addRenderableWidget(field);
            attr.inputField = field;
        }

        loadDurabilityValues();
        updateFieldVisibility();
    }

    private void loadDurabilityValues() {
        if (stack.isEmpty()) return;

        int maxDura = stack.getMaxDamage();
        int damage = stack.getDamageValue();
        int remaining = maxDura - damage;

        durabilityField.setValue(String.valueOf(remaining));

        Integer repairCost = stack.get(DataComponents.REPAIR_COST);
        repairCostField.setValue(String.valueOf(repairCost != null ? repairCost : 0));

        isUnbreakable = stack.has(DataComponents.UNBREAKABLE);
    }

    private void updateFieldVisibility() {
        // Hide all
        enchantSearchBox.visible = false;
        durabilityField.visible = false;
        repairCostField.visible = false;
        pickerSearchBox.visible = false;
        attrPickerSearchBox.visible = false;
        presetNameBox.visible = false;

        for (StatSlider stat : statSliders) {
            if (stat.inputField != null) stat.inputField.visible = false;
        }
        for (AttributeEntry attr : attributes) {
            if (attr.inputField != null) attr.inputField.visible = false;
        }

        // Show based on tab
        switch (currentTab) {
            case STATS:
                for (StatSlider stat : statSliders) {
                    if (stat.inputField != null) stat.inputField.visible = true;
                }
                break;
            case ENCHANTS:
                enchantSearchBox.visible = !showEnchantPicker;
                pickerSearchBox.visible = showEnchantPicker;
                break;
            case DURABILITY:
                durabilityField.visible = true;
                repairCostField.visible = true;
                break;
            case ATTRIBUTES:
                for (AttributeEntry attr : attributes) {
                    if (attr.inputField != null) attr.inputField.visible = !showAttributePicker;
                }
                attrPickerSearchBox.visible = showAttributePicker;
                break;
            case COMPONENTS:
                // No fields
                break;
        }

        // Preset menu
        presetNameBox.visible = showPresetMenu;
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        rotationAngle += 0.8f;
        if (rotationAngle >= 360f) rotationAngle -= 360f;

        renderGradientBackground(graphics);

        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;

        renderPanel(graphics, panelX, panelY);

        // === EMPTY HAND CHECK ===
        if (stack.isEmpty()) {
            renderEmptyHandMessage(graphics, panelX, panelY);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        renderTabs(graphics, panelX, panelY, mouseX, mouseY);
        renderPreview(graphics, panelX, panelY);
        renderModeToggle(graphics, panelX, panelY, mouseX, mouseY);

        // Tab content
        int contentY = panelY + TAB_HEIGHT + PREVIEW_SIZE + 20;
        switch (currentTab) {
            case STATS -> renderStatsTab(graphics, panelX, contentY, mouseX, mouseY);
            case ENCHANTS -> renderEnchantsTab(graphics, panelX, contentY, mouseX, mouseY);
            case DURABILITY -> renderDurabilityTab(graphics, panelX, contentY, mouseX, mouseY);
            case ATTRIBUTES -> renderAttributesTab(graphics, panelX, contentY, mouseX, mouseY);
            case COMPONENTS -> renderComponentsTab(graphics, panelX, contentY, mouseX, mouseY);
        }

        // Buttons
        renderButtons(graphics, panelX, panelY + PANEL_HEIGHT - 35, mouseX, mouseY);

        // Status
        if (statusMessage != null && statusTicks > 0) {
            int msgX = (this.width - font.width(statusMessage)) / 2;
            graphics.drawString(font, statusMessage, msgX, panelY + PANEL_HEIGHT + 5, statusColor, false);
            statusTicks--;
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * Render a helpful message when no item is held.
     */
    private void renderEmptyHandMessage(GuiGraphics graphics, int panelX, int panelY) {
        int centerX = panelX + PANEL_WIDTH / 2;
        int centerY = panelY + PANEL_HEIGHT / 2;

        // Icon
        graphics.drawCenteredString(font, "§7✋", centerX, centerY - 40, 0xFFFFFFFF);

        // Title
        graphics.drawCenteredString(font, "§eNo Item in Hand", centerX, centerY - 20, 0xFFFFAA00);

        // Instructions
        graphics.drawCenteredString(font, "§7Hold an item in your main hand", centerX, centerY, 0xFFAAAAAA);
        graphics.drawCenteredString(font, "§7to edit its properties.", centerX, centerY + 12, 0xFFAAAAAA);

        // Hint
        graphics.drawCenteredString(font, "§8Press ESC to close", centerX, centerY + 40, 0xFF666666);
    }

    private void renderGradientBackground(GuiGraphics graphics) {
        for (int y = 0; y < this.height; y++) {
            float ratio = (float) y / this.height;
            int color = lerpColor(0xE0101828, 0xE0080810, ratio);
            graphics.fill(0, y, this.width, y + 1, color);
        }
    }

    private void renderPanel(GuiGraphics graphics, int x, int y) {
        // Shadow
        for (int i = 4; i > 0; i--) {
            graphics.fill(x + i, y + i, x + PANEL_WIDTH + i, y + PANEL_HEIGHT + i, (20 * (5 - i)) << 24);
        }

        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xF0181828);
        AxiomRenderer.drawBorder(graphics, x, y, PANEL_WIDTH, PANEL_HEIGHT, UIConstants.Border.DEFAULT);
    }

    private void renderTabs(GuiGraphics graphics, int panelX, int panelY, int mouseX, int mouseY) {
        Tab[] tabs = Tab.values();
        int tabWidth = PANEL_WIDTH / tabs.length;

        for (int i = 0; i < tabs.length; i++) {
            Tab tab = tabs[i];
            int tx = panelX + i * tabWidth;
            boolean selected = currentTab == tab;
            boolean hovered = AxiomRenderer.isMouseOver(mouseX, mouseY, tx, panelY, tabWidth, TAB_HEIGHT);

            int bgColor = selected ? UIConstants.Background.ACTIVE : (hovered ? UIConstants.Background.HOVER : UIConstants.Background.HEADER);
            graphics.fill(tx, panelY, tx + tabWidth, panelY + TAB_HEIGHT, bgColor);

            if (selected) {
                graphics.fill(tx, panelY + TAB_HEIGHT - 2, tx + tabWidth, panelY + TAB_HEIGHT, UIConstants.Border.ACCENT);
            }

            String name = tab.name().charAt(0) + tab.name().substring(1).toLowerCase();
            int textColor = selected ? UIConstants.Text.ACCENT : UIConstants.Text.PRIMARY;
            int textX = tx + (tabWidth - font.width(name)) / 2;
            graphics.drawString(font, name, textX, panelY + 8, textColor, false);
        }
    }

    private void renderPreview(GuiGraphics graphics, int panelX, int panelY) {
        int previewX = panelX + 10;
        int previewY = panelY + TAB_HEIGHT + 5;

        graphics.fill(previewX, previewY, previewX + PREVIEW_SIZE, previewY + PREVIEW_SIZE, 0xFF0A0A14);
        AxiomRenderer.drawBorder(graphics, previewX, previewY, PREVIEW_SIZE, PREVIEW_SIZE, UIConstants.Border.DEFAULT);

        if (!stack.isEmpty()) {
            render3DItem(graphics, stack, previewX + PREVIEW_SIZE / 2, previewY + PREVIEW_SIZE / 2 - 5, rotationAngle);
        }

        // Item info to the right
        int infoX = previewX + PREVIEW_SIZE + 15;
        int infoY = previewY + 5;

        if (!stack.isEmpty()) {
            graphics.drawString(font, stack.getHoverName(), infoX, infoY, UIConstants.Text.PRIMARY, false);
            infoY += 12;

            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            graphics.drawString(font, itemId, infoX, infoY, UIConstants.Text.MUTED, false);
            infoY += 12;

            // Quick stats
            graphics.drawString(font, "Enchants: " + enchantments.size(), infoX, infoY, UIConstants.Text.SECONDARY, false);
            infoY += 10;

            if (stack.isDamageableItem()) {
                int dura = stack.getMaxDamage() - stack.getDamageValue();
                int max = stack.getMaxDamage();
                float pct = (float) dura / max * 100;
                int duraColor = pct > 50 ? UIConstants.Accent.GREEN : (pct > 25 ? UIConstants.Accent.YELLOW : UIConstants.Accent.RED);
                graphics.drawString(font, String.format("Durability: %d/%d (%.0f%%)", dura, max, pct), infoX, infoY, duraColor, false);
            }
        } else {
            graphics.drawString(font, "No item in hand", infoX, infoY + 20, UIConstants.Text.MUTED, false);
        }
    }

    private void renderModeToggle(GuiGraphics graphics, int panelX, int panelY, int mouseX, int mouseY) {
        int modeX = panelX + PANEL_WIDTH - 100;
        int modeY = panelY + TAB_HEIGHT + 10;

        boolean hovered = AxiomRenderer.isMouseOver(mouseX, mouseY, modeX, modeY, 90, 16);
        int bgColor = hovered ? UIConstants.Background.HOVER : UIConstants.Background.INPUT;
        int borderColor = editGlobal ? UIConstants.Accent.ORANGE : UIConstants.Accent.GREEN;

        graphics.fill(modeX, modeY, modeX + 90, modeY + 16, bgColor);
        AxiomRenderer.drawBorder(graphics, modeX, modeY, 90, 16, borderColor);

        String text = editGlobal ? "GLOBAL" : "SPECIFIC";
        int textX = modeX + (90 - font.width(text)) / 2;
        graphics.drawString(font, text, textX, modeY + 4, borderColor, false);
    }

    private void render3DItem(GuiGraphics graphics, ItemStack itemStack, int x, int y, float rotation) {
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer itemRenderer = mc.getItemRenderer();

        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();

        poseStack.translate(x, y, 150);
        poseStack.scale(48f, -48f, 48f);

        Quaternionf rotationQuat = new Quaternionf()
            .rotateY((float) Math.toRadians(rotation))
            .rotateX((float) Math.toRadians(-20));
        poseStack.mulPose(rotationQuat);

        Lighting.setupFor3DItems();

        itemRenderer.renderStatic(itemStack, ItemDisplayContext.GUI, 15728880, 655360,
            poseStack, graphics.bufferSource(), mc.level, 0);

        graphics.flush();
        Lighting.setupForFlatItems();

        poseStack.popPose();
    }

    private void renderStatsTab(GuiGraphics graphics, int panelX, int startY, int mouseX, int mouseY) {
        int contentX = panelX + 15;
        int sliderX = contentX + 130;
        int sliderWidth = 150;

        graphics.drawString(font, "Body Part Multipliers", contentX, startY - 15, UIConstants.Text.TITLE, false);

        for (int i = 0; i < statSliders.size(); i++) {
            StatSlider stat = statSliders.get(i);
            int y = startY + i * 28;

            // Indicator
            graphics.fill(contentX, y + 6, contentX + 4, y + 14, stat.color);

            // Label
            graphics.drawString(font, stat.name, contentX + 10, y + 6, UIConstants.Text.PRIMARY, false);

            // Slider
            renderSlider(graphics, sliderX, y + 4, sliderWidth, 14, stat, mouseX, mouseY);
        }
    }

    private void renderSlider(GuiGraphics graphics, int x, int y, int width, int height, StatSlider stat, int mouseX, int mouseY) {
        graphics.fill(x, y, x + width, y + height, UIConstants.Background.INPUT);

        float ratio = (stat.value - stat.min) / (stat.max - stat.min);
        int fillWidth = (int) (width * ratio);

        for (int i = 0; i < fillWidth; i++) {
            int color = lerpColor(stat.color, UIConstants.lighten(stat.color, 0.3f), (float) i / width);
            graphics.fill(x + i, y, x + i + 1, y + height, color);
        }

        int knobX = Math.max(x, Math.min(x + width - 6, x + fillWidth - 3));
        boolean hovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x, y, width, height);
        graphics.fill(knobX, y - 1, knobX + 6, y + height + 1, hovered || stat.isDragging ? UIConstants.Text.WHITE : UIConstants.Text.SECONDARY);

        AxiomRenderer.drawBorder(graphics, x, y, width, height, UIConstants.Border.MUTED);
    }

    private void renderEnchantsTab(GuiGraphics graphics, int panelX, int startY, int mouseX, int mouseY) {
        int contentX = panelX + 15;

        graphics.drawString(font, "Current Enchantments", contentX, startY - 15, UIConstants.Text.TITLE, false);

        // Add button
        int addBtnX = panelX + PANEL_WIDTH - 100;
        boolean addHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, addBtnX, startY - 18, 80, 18);
        graphics.fill(addBtnX, startY - 18, addBtnX + 80, startY, addHovered ? UIConstants.Accent.GREEN : UIConstants.Background.INPUT);
        AxiomRenderer.drawBorder(graphics, addBtnX, startY - 18, 80, 18, UIConstants.Accent.GREEN);
        graphics.drawString(font, "+ Add", addBtnX + 25, startY - 14, UIConstants.Text.WHITE, false);

        // Enchantment list
        int listY = startY + 25;
        int visibleCount = 8;

        List<EnchantmentEntry> filtered = enchantments.stream()
            .filter(e -> enchantSearch.isEmpty() || e.name.toLowerCase().contains(enchantSearch))
            .toList();

        for (int i = 0; i < Math.min(visibleCount, filtered.size() - enchantScrollOffset); i++) {
            int idx = i + enchantScrollOffset;
            if (idx >= filtered.size()) break;

            EnchantmentEntry ench = filtered.get(idx);
            int ey = listY + i * 22;

            boolean rowHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, contentX, ey, PANEL_WIDTH - 50, 20);
            if (rowHovered) {
                graphics.fill(contentX, ey, contentX + PANEL_WIDTH - 50, ey + 20, UIConstants.Background.HOVER);
            }

            // Name
            graphics.drawString(font, ench.name, contentX + 5, ey + 6, ench.toRemove ? UIConstants.Text.MUTED : UIConstants.Text.PRIMARY, false);

            // Level controls
            int levelX = panelX + PANEL_WIDTH - 150;

            // Minus button
            boolean minusHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, levelX, ey + 2, 16, 16);
            graphics.fill(levelX, ey + 2, levelX + 16, ey + 18, minusHovered ? UIConstants.Accent.RED : UIConstants.Background.INPUT);
            graphics.drawString(font, "-", levelX + 5, ey + 5, UIConstants.Text.WHITE, false);

            // Level
            graphics.drawString(font, String.valueOf(ench.level), levelX + 25, ey + 6, UIConstants.Accent.CYAN, false);

            // Plus button
            int plusX = levelX + 40;
            boolean plusHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, plusX, ey + 2, 16, 16);
            graphics.fill(plusX, ey + 2, plusX + 16, ey + 18, plusHovered ? UIConstants.Accent.GREEN : UIConstants.Background.INPUT);
            graphics.drawString(font, "+", plusX + 5, ey + 5, UIConstants.Text.WHITE, false);

            // Remove button
            int removeX = levelX + 65;
            boolean removeHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, removeX, ey + 2, 16, 16);
            graphics.fill(removeX, ey + 2, removeX + 16, ey + 18, removeHovered ? UIConstants.Accent.RED : UIConstants.Background.INPUT);
            graphics.drawString(font, "X", removeX + 4, ey + 5, UIConstants.Text.WHITE, false);
        }

        // Scroll indicator
        if (filtered.size() > visibleCount) {
            int scrollHeight = 150;
            int thumbHeight = (int) ((float) visibleCount / filtered.size() * scrollHeight);
            int thumbY = listY + (int) ((float) enchantScrollOffset / (filtered.size() - visibleCount) * (scrollHeight - thumbHeight));

            graphics.fill(panelX + PANEL_WIDTH - 25, listY, panelX + PANEL_WIDTH - 20, listY + scrollHeight, UIConstants.Background.INPUT);
            graphics.fill(panelX + PANEL_WIDTH - 25, thumbY, panelX + PANEL_WIDTH - 20, thumbY + thumbHeight, UIConstants.Border.DEFAULT);
        }

        // Render enchantment picker overlay if open
        if (showEnchantPicker) {
            renderEnchantmentPicker(graphics, panelX, startY, mouseX, mouseY);
        }
    }

    /**
     * Render the enchantment picker overlay showing ALL enchantments from ALL mods.
     */
    private void renderEnchantmentPicker(GuiGraphics graphics, int panelX, int startY, int mouseX, int mouseY) {
        int pickerX = panelX + 20;
        int pickerY = startY;
        int pickerW = PANEL_WIDTH - 40;
        int pickerH = 200; // Reduced height to avoid overlapping with buttons

        // Darken background
        graphics.fill(panelX, startY - 20, panelX + PANEL_WIDTH, startY + 220, 0xC0000000);

        // Picker background
        graphics.fill(pickerX, pickerY, pickerX + pickerW, pickerY + pickerH, UIConstants.Background.PANEL);
        AxiomRenderer.drawBorder(graphics, pickerX, pickerY, pickerW, pickerH, UIConstants.Accent.PURPLE);

        // Title
        graphics.drawString(font, "Add Enchantment", pickerX + 10, pickerY + 8, UIConstants.Accent.PURPLE, false);

        // Close button
        int closeX = pickerX + pickerW - 20;
        boolean closeHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, closeX, pickerY + 5, 16, 16);
        graphics.fill(closeX, pickerY + 5, closeX + 16, pickerY + 21, closeHovered ? UIConstants.Accent.RED : UIConstants.Background.INPUT);
        graphics.drawString(font, "X", closeX + 4, pickerY + 9, UIConstants.Text.WHITE, false);

        // Filter tabs row
        int filterY = pickerY + 22;
        int filterTabW = 40;
        String[] filterNames = {"All", "★", "Fit", "Wpn", "Arm", "Tool"};
        EnchantFilter[] filterValues = EnchantFilter.values();
        for (int i = 0; i < filterNames.length; i++) {
            int fx = pickerX + 5 + i * (filterTabW + 2);
            boolean selected = currentEnchantFilter == filterValues[i];
            boolean fHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, fx, filterY, filterTabW, 14);

            int bgColor = selected ? UIConstants.Accent.PURPLE : (fHovered ? UIConstants.Background.HOVER : UIConstants.Background.INPUT);
            graphics.fill(fx, filterY, fx + filterTabW, filterY + 14, bgColor);

            int txtColor = selected ? UIConstants.Text.WHITE : UIConstants.Text.SECONDARY;
            int txtX = fx + (filterTabW - font.width(filterNames[i])) / 2;
            graphics.drawString(font, filterNames[i], txtX, filterY + 3, txtColor, false);
        }

        // Search box is rendered by parent - it's at pickerY + 38

        // Enchantment count
        List<Holder<Enchantment>> filtered = getFilteredAvailableEnchants();
        graphics.drawString(font, filtered.size() + " found", pickerX + 180, pickerY + 40, UIConstants.Text.MUTED, false);

        // Enchantment list (moved down for filter tabs + search box)
        int listY = pickerY + 58;
        int itemHeight = 18;
        int visibleItems = 6; // Reduced to fit in smaller picker

        Holder<Enchantment> hoveredEnch = null;
        int hoveredY = 0;

        for (int i = 0; i < Math.min(visibleItems, filtered.size() - pickerScrollOffset); i++) {
            int idx = i + pickerScrollOffset;
            if (idx >= filtered.size()) break;

            Holder<Enchantment> ench = filtered.get(idx);
            int itemY = listY + i * itemHeight;
            String name = formatEnchantmentName(ench);

            boolean hovered = AxiomRenderer.isMouseOver(mouseX, mouseY, pickerX + 10, itemY, pickerW - 40, itemHeight);
            if (hovered) {
                graphics.fill(pickerX + 10, itemY, pickerX + pickerW - 30, itemY + itemHeight, UIConstants.Background.HOVER);
                hoveredEnch = ench;
                hoveredY = itemY;
            }

            // Check if favorite
            String enchId = ench.unwrapKey().map(k -> k.location().toString()).orElse("");
            boolean isFavorite = ItemEditorDataManager.INSTANCE.getFavoriteEnchantments().contains(enchId);

            // Color code by namespace
            int color = UIConstants.Text.PRIMARY;
            String namespace = ench.unwrapKey().map(k -> k.location().getNamespace()).orElse("minecraft");
            if (!namespace.equals("minecraft")) {
                color = UIConstants.Accent.CYAN; // Modded enchantments in cyan
            }

            // Draw favorite star
            if (isFavorite) {
                graphics.drawString(font, "★", pickerX + 10, itemY + 5, UIConstants.Accent.GOLD, false);
                graphics.drawString(font, name, pickerX + 22, itemY + 5, color, false);
            } else {
                graphics.drawString(font, name, pickerX + 15, itemY + 5, color, false);
            }
        }

        // Scroll indicator for picker
        if (filtered.size() > visibleItems) {
            int scrollHeight = visibleItems * itemHeight;
            int thumbHeight = Math.max(10, (int) ((float) visibleItems / filtered.size() * scrollHeight));
            int maxScroll = Math.max(1, filtered.size() - visibleItems);
            int thumbY = listY + (int) ((float) pickerScrollOffset / maxScroll * (scrollHeight - thumbHeight));

            graphics.fill(pickerX + pickerW - 20, listY, pickerX + pickerW - 15, listY + scrollHeight, UIConstants.Background.INPUT);
            graphics.fill(pickerX + pickerW - 20, thumbY, pickerX + pickerW - 15, thumbY + thumbHeight, UIConstants.Accent.PURPLE);
        }

        // Hint with favorite instruction
        graphics.drawString(font, "Click=add | Right-click=★favorite", pickerX + 10, pickerY + pickerH - 15, UIConstants.Text.MUTED, false);

        // Tooltip for hovered enchantment
        if (hoveredEnch != null) {
            renderEnchantmentTooltip(graphics, hoveredEnch, mouseX, mouseY);
        }
    }

    /**
     * Render tooltip for an enchantment showing description and stats.
     */
    private void renderEnchantmentTooltip(GuiGraphics graphics, Holder<Enchantment> ench, int mouseX, int mouseY) {
        List<String> lines = new ArrayList<>();

        // Get enchantment ID
        String id = ench.unwrapKey().map(k -> k.location().toString()).orElse("unknown");
        lines.add("§7" + id);

        // Get max level
        Enchantment enchValue = ench.value();
        int maxLevel = enchValue.getMaxLevel();
        lines.add("§eMax Level: §f" + maxLevel);

        // Get description from translation (if available)
        String descKey = "enchantment." + id.replace(":", ".") + ".desc";
        Component desc = Component.translatable(descKey);
        String descText = desc.getString();
        if (!descText.equals(descKey)) {
            lines.add("§7" + descText);
        }

        // Render tooltip
        int tooltipX = mouseX + 12;
        int tooltipY = mouseY - 10;
        int tooltipW = 0;
        for (String line : lines) {
            tooltipW = Math.max(tooltipW, font.width(line.replaceAll("§.", "")));
        }
        tooltipW += 8;
        int tooltipH = lines.size() * 10 + 6;

        // Keep on screen
        if (tooltipX + tooltipW > this.width) tooltipX = mouseX - tooltipW - 5;
        if (tooltipY + tooltipH > this.height) tooltipY = this.height - tooltipH;

        // Background
        graphics.fill(tooltipX - 2, tooltipY - 2, tooltipX + tooltipW + 2, tooltipY + tooltipH + 2, 0xF0100010);
        AxiomRenderer.drawBorder(graphics, tooltipX - 2, tooltipY - 2, tooltipW + 4, tooltipH + 4, UIConstants.Accent.PURPLE);

        // Text
        int ty = tooltipY;
        for (String line : lines) {
            graphics.drawString(font, line, tooltipX + 2, ty + 2, UIConstants.Text.PRIMARY, false);
            ty += 10;
        }
    }

    private void renderDurabilityTab(GuiGraphics graphics, int panelX, int startY, int mouseX, int mouseY) {
        int contentX = panelX + 15;

        graphics.drawString(font, "Durability Settings", contentX, startY - 15, UIConstants.Text.TITLE, false);

        if (!stack.isDamageableItem()) {
            graphics.drawString(font, "This item has no durability", contentX, startY + 20, UIConstants.Text.MUTED, false);
            return;
        }

        int y = startY + 10;

        // Current durability
        graphics.drawString(font, "Current Durability:", contentX, y, UIConstants.Text.PRIMARY, false);
        // Field positioned in init()

        y += 30;

        // Max durability (info only)
        graphics.drawString(font, "Max Durability:", contentX, y, UIConstants.Text.PRIMARY, false);
        graphics.drawString(font, String.valueOf(stack.getMaxDamage()), contentX + 200, y, UIConstants.Text.SECONDARY, false);

        y += 30;

        // Repair cost
        graphics.drawString(font, "Repair Cost:", contentX, y, UIConstants.Text.PRIMARY, false);

        y += 35;

        // Unbreakable toggle
        boolean unbHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, contentX, y, 200, 20);
        graphics.fill(contentX, y, contentX + 200, y + 20, unbHovered ? UIConstants.Background.HOVER : UIConstants.Background.INPUT);
        AxiomRenderer.drawBorder(graphics, contentX, y, 200, 20, isUnbreakable ? UIConstants.Accent.GREEN : UIConstants.Border.MUTED);
        graphics.drawString(font, "Unbreakable: " + (isUnbreakable ? "ON" : "OFF"), contentX + 10, y + 6,
            isUnbreakable ? UIConstants.Accent.GREEN : UIConstants.Text.SECONDARY, false);

        y += 35;

        // Quick actions
        graphics.drawString(font, "Quick Actions:", contentX, y, UIConstants.Text.TITLE, false);
        y += 18;

        // Full repair button
        boolean repairHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, contentX, y, 100, 20);
        graphics.fill(contentX, y, contentX + 100, y + 20, repairHovered ? UIConstants.Accent.GREEN : UIConstants.Background.INPUT);
        AxiomRenderer.drawBorder(graphics, contentX, y, 100, 20, UIConstants.Accent.GREEN);
        graphics.drawString(font, "Full Repair", contentX + 20, y + 6, UIConstants.Text.WHITE, false);

        // Break button
        int breakX = contentX + 110;
        boolean breakHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, breakX, y, 100, 20);
        graphics.fill(breakX, y, breakX + 100, y + 20, breakHovered ? UIConstants.Accent.RED : UIConstants.Background.INPUT);
        AxiomRenderer.drawBorder(graphics, breakX, y, 100, 20, UIConstants.Accent.RED);
        graphics.drawString(font, "Set to 1", breakX + 25, y + 6, UIConstants.Text.WHITE, false);
    }

    private void renderAttributesTab(GuiGraphics graphics, int panelX, int startY, int mouseX, int mouseY) {
        int contentX = panelX + 15;

        graphics.drawString(font, "Attribute Modifiers", contentX, startY - 15, UIConstants.Text.TITLE, false);

        // Add button
        int addBtnX = panelX + PANEL_WIDTH - 100;
        boolean addHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, addBtnX, startY - 18, 80, 18);
        graphics.fill(addBtnX, startY - 18, addBtnX + 80, startY, addHovered ? UIConstants.Accent.GREEN : UIConstants.Background.INPUT);
        AxiomRenderer.drawBorder(graphics, addBtnX, startY - 18, 80, 18, UIConstants.Accent.GREEN);
        graphics.drawString(font, "+ Add", addBtnX + 25, startY - 14, UIConstants.Text.WHITE, false);

        for (int i = 0; i < attributes.size(); i++) {
            AttributeEntry attr = attributes.get(i);
            int y = startY + i * 28;

            graphics.drawString(font, attr.name + ":", contentX, y + 6, UIConstants.Text.PRIMARY, false);

            // Operation selector
            int opX = panelX + 200;
            String opText = switch (attr.operation) {
                case ADD_VALUE -> "+";
                case ADD_MULTIPLIED_BASE -> "×";
                case ADD_MULTIPLIED_TOTAL -> "×%";
            };

            boolean opHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, opX, y + 2, 20, 16);
            graphics.fill(opX, y + 2, opX + 20, y + 18, opHovered ? UIConstants.Background.HOVER : UIConstants.Background.INPUT);
            graphics.drawString(font, opText, opX + 6, y + 5, UIConstants.Accent.CYAN, false);

            // Remove button
            int removeX = panelX + PANEL_WIDTH - 40;
            boolean removeHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, removeX, y + 2, 16, 16);
            graphics.fill(removeX, y + 2, removeX + 16, y + 18, removeHovered ? UIConstants.Accent.RED : UIConstants.Background.INPUT);
            graphics.drawString(font, "X", removeX + 4, y + 5, UIConstants.Text.WHITE, false);
        }

        // Render attribute picker overlay if open
        if (showAttributePicker) {
            renderAttributePicker(graphics, panelX, startY, mouseX, mouseY);
        }
    }

    /**
     * Load ALL available attributes from the registry (vanilla + ALL mods).
     */
    private void loadAllAvailableAttributes() {
        allAvailableAttributes.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        var registry = mc.level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ATTRIBUTE);

        // Add all attributes from the registry
        registry.holders().forEach(holder -> {
            allAvailableAttributes.add(holder);
        });

        // Sort by namespace then by name
        allAvailableAttributes.sort((a, b) -> {
            String nsA = a.unwrapKey().map(k -> k.location().getNamespace()).orElse("");
            String nsB = b.unwrapKey().map(k -> k.location().getNamespace()).orElse("");
            if (!nsA.equals(nsB)) {
                if (nsA.equals("minecraft")) return -1;
                if (nsB.equals("minecraft")) return 1;
                return nsA.compareTo(nsB);
            }
            return formatAttributeName(a).compareTo(formatAttributeName(b));
        });
    }

    /**
     * Get filtered list of available attributes for picker.
     */
    private List<Holder<Attribute>> getFilteredAvailableAttributes() {
        if (attrPickerSearch.isEmpty()) {
            return allAvailableAttributes;
        }
        String search = attrPickerSearch.toLowerCase();
        return allAvailableAttributes.stream()
            .filter(a -> formatAttributeName(a).toLowerCase().contains(search))
            .toList();
    }

    /**
     * Render the attribute picker overlay showing ALL attributes from ALL mods.
     */
    private void renderAttributePicker(GuiGraphics graphics, int panelX, int startY, int mouseX, int mouseY) {
        int pickerX = panelX + 20;
        int pickerY = startY;
        int pickerW = PANEL_WIDTH - 40;
        int pickerH = 200; // Reduced height to avoid overlapping with buttons

        // Darken background
        graphics.fill(panelX, startY - 20, panelX + PANEL_WIDTH, startY + 220, 0xC0000000);

        // Picker background
        graphics.fill(pickerX, pickerY, pickerX + pickerW, pickerY + pickerH, UIConstants.Background.PANEL);
        AxiomRenderer.drawBorder(graphics, pickerX, pickerY, pickerW, pickerH, UIConstants.Accent.ORANGE);

        // Title
        graphics.drawString(font, "Add Attribute (All Mods)", pickerX + 10, pickerY + 8, UIConstants.Accent.ORANGE, false);

        // Close button
        int closeX = pickerX + pickerW - 20;
        boolean closeHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, closeX, pickerY + 5, 16, 16);
        graphics.fill(closeX, pickerY + 5, closeX + 16, pickerY + 21, closeHovered ? UIConstants.Accent.RED : UIConstants.Background.INPUT);
        graphics.drawString(font, "X", closeX + 4, pickerY + 9, UIConstants.Text.WHITE, false);

        // Search box is rendered by parent

        // Attribute count
        List<Holder<Attribute>> filtered = getFilteredAvailableAttributes();
        graphics.drawString(font, filtered.size() + " found", pickerX + 220, pickerY + 24, UIConstants.Text.MUTED, false);

        // Attribute list
        int listY = pickerY + 42;
        int itemHeight = 18;
        int visibleItems = 7; // Reduced to fit in smaller picker

        for (int i = 0; i < Math.min(visibleItems, filtered.size() - attrPickerScrollOffset); i++) {
            int idx = i + attrPickerScrollOffset;
            if (idx >= filtered.size()) break;

            Holder<Attribute> attr = filtered.get(idx);
            int itemY = listY + i * itemHeight;
            String name = formatAttributeName(attr);

            boolean hovered = AxiomRenderer.isMouseOver(mouseX, mouseY, pickerX + 10, itemY, pickerW - 40, itemHeight);
            if (hovered) {
                graphics.fill(pickerX + 10, itemY, pickerX + pickerW - 30, itemY + itemHeight, UIConstants.Background.HOVER);
            }

            // Color code by namespace
            int color = UIConstants.Text.PRIMARY;
            String namespace = attr.unwrapKey().map(k -> k.location().getNamespace()).orElse("minecraft");
            if (!namespace.equals("minecraft")) {
                color = UIConstants.Accent.CYAN;
            }

            graphics.drawString(font, name, pickerX + 15, itemY + 5, color, false);

            // Show full ID on right side
            String id = attr.unwrapKey().map(k -> k.location().toString()).orElse("");
            int idWidth = font.width(id);
            if (idWidth < pickerW - 150) {
                graphics.drawString(font, id, pickerX + pickerW - idWidth - 30, itemY + 5, UIConstants.Text.MUTED, false);
            }
        }

        // Scroll indicator
        if (filtered.size() > visibleItems) {
            int scrollHeight = visibleItems * itemHeight;
            int thumbHeight = Math.max(10, (int) ((float) visibleItems / filtered.size() * scrollHeight));
            int maxScroll = Math.max(1, filtered.size() - visibleItems);
            int thumbY = listY + (int) ((float) attrPickerScrollOffset / maxScroll * (scrollHeight - thumbHeight));

            graphics.fill(pickerX + pickerW - 20, listY, pickerX + pickerW - 15, listY + scrollHeight, UIConstants.Background.INPUT);
            graphics.fill(pickerX + pickerW - 20, thumbY, pickerX + pickerW - 15, thumbY + thumbHeight, UIConstants.Accent.ORANGE);
        }

        // Hint
        graphics.drawString(font, "Click to add • Scroll for more", pickerX + 10, pickerY + pickerH - 15, UIConstants.Text.MUTED, false);
    }

    /**
     * Add attribute from picker to the item.
     */
    private void addAttributeFromPicker(Holder<Attribute> attr) {
        // Check if already present
        for (AttributeEntry entry : attributes) {
            if (entry.attribute.equals(attr)) {
                showStatus("Attribute already added!", UIConstants.Accent.YELLOW);
                showAttributePicker = false;
                updateFieldVisibility();
                return;
            }
        }
        // Add new
        String name = formatAttributeName(attr);
        attributes.add(new AttributeEntry(name, attr, 0, AttributeModifier.Operation.ADD_VALUE));
        showStatus("Added " + name + "!", UIConstants.Accent.GREEN);
        showAttributePicker = false;
        updateFieldVisibility();
        // Reinitialize attribute input fields
        rebuildAttributeFields();
    }

    /**
     * Rebuild attribute input fields after adding/removing attributes.
     */
    private void rebuildAttributeFields() {
        // Remove old fields
        for (AttributeEntry attr : attributes) {
            if (attr.inputField != null) {
                this.removeWidget(attr.inputField);
            }
        }

        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;
        int inputX = panelX + PANEL_WIDTH - 70;
        int startY = panelY + TAB_HEIGHT + PREVIEW_SIZE + 30;

        for (int i = 0; i < attributes.size(); i++) {
            AttributeEntry attr = attributes.get(i);
            int y = startY + i * 28;

            EditBox field = new EditBox(font, inputX, y + 4, 50, 16, Component.empty());
            field.setMaxLength(10);
            field.setValue(formatDouble(attr.value));
            field.visible = currentTab == Tab.ATTRIBUTES && !showAttributePicker;

            final AttributeEntry currentAttr = attr;
            field.setResponder(text -> {
                try {
                    currentAttr.value = Double.parseDouble(text.trim());
                } catch (NumberFormatException ignored) {}
            });

            this.addRenderableWidget(field);
            attr.inputField = field;
        }
    }

    private void renderComponentsTab(GuiGraphics graphics, int panelX, int startY, int mouseX, int mouseY) {
        int contentX = panelX + 15;

        graphics.drawString(font, "Item Components (Read-Only)", contentX, startY - 15, UIConstants.Text.TITLE, false);

        if (stack.isEmpty()) {
            graphics.drawString(font, "No item selected", contentX, startY + 20, UIConstants.Text.MUTED, false);
            return;
        }

        int y = startY + 5;
        int count = 0;

        // List all components
        for (var entry : stack.getComponents()) {
            if (count++ > 12) {
                graphics.drawString(font, "... and more", contentX, y, UIConstants.Text.MUTED, false);
                break;
            }

            String compName = entry.type().toString();
            if (compName.contains(":")) {
                compName = compName.substring(compName.indexOf(":") + 1);
            }

            graphics.drawString(font, "• " + compName, contentX, y, UIConstants.Text.SECONDARY, false);
            y += 12;
        }
    }

    private void renderButtons(GuiGraphics graphics, int panelX, int buttonY, int mouseX, int mouseY) {
        // Top row: Undo/Redo/Presets
        int smallBtnW = 50;
        int topRowY = buttonY - 30;

        // Undo button
        int undoX = panelX + 15;
        boolean undoHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, undoX, topRowY, smallBtnW, 20);
        boolean undoEnabled = !undoStack.isEmpty();
        renderSmallButton(graphics, undoX, topRowY, smallBtnW, "↩ Undo", undoHovered, undoEnabled ? UIConstants.Accent.YELLOW : UIConstants.Text.MUTED);

        // Redo button
        int redoX = undoX + smallBtnW + 5;
        boolean redoHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, redoX, topRowY, smallBtnW, 20);
        boolean redoEnabled = !redoStack.isEmpty();
        renderSmallButton(graphics, redoX, topRowY, smallBtnW, "Redo ↪", redoHovered, redoEnabled ? UIConstants.Accent.GREEN : UIConstants.Text.MUTED);

        // History button
        int historyX = redoX + smallBtnW + 10;
        boolean historyHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, historyX, topRowY, 55, 20);
        renderSmallButton(graphics, historyX, topRowY, 55, "History", historyHovered, UIConstants.Accent.CYAN);

        // Export button (copies config to clipboard)
        int exportX = historyX + 60;
        boolean exportHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, exportX, topRowY, 50, 20);
        renderSmallButton(graphics, exportX, topRowY, 50, "Export", exportHovered, UIConstants.Accent.ORANGE);

        // Import button (imports config from clipboard)
        int importX = exportX + 55;
        boolean importHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, importX, topRowY, 50, 20);
        renderSmallButton(graphics, importX, topRowY, 50, "Import", importHovered, UIConstants.Accent.GOLD);

        // Template button (suggests templates based on item type)
        int templateX = importX + 55;
        boolean templateHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, templateX, topRowY, 60, 20);
        renderSmallButton(graphics, templateX, topRowY, 60, "Template", templateHovered, UIConstants.Accent.GREEN);

        // Presets button
        int presetsX = panelX + PANEL_WIDTH - 80;
        boolean presetsHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, presetsX, topRowY, 65, 20);
        renderSmallButton(graphics, presetsX, topRowY, 65, "Presets", presetsHovered, UIConstants.Accent.PURPLE);

        // Main buttons row
        int buttonWidth = 100;
        int gap = 15;
        int totalWidth = buttonWidth * 3 + gap * 2;
        int startX = panelX + (PANEL_WIDTH - totalWidth) / 2;

        // Reset
        boolean resetHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, startX, buttonY, buttonWidth, 24);
        renderButton(graphics, startX, buttonY, buttonWidth, "Reset", resetHovered, UIConstants.Accent.YELLOW);

        // Cancel
        int cancelX = startX + buttonWidth + gap;
        boolean cancelHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, cancelX, buttonY, buttonWidth, 24);
        renderButton(graphics, cancelX, buttonY, buttonWidth, "Cancel", cancelHovered, UIConstants.Text.MUTED);

        // Apply
        int applyX = cancelX + buttonWidth + gap;
        boolean applyHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, applyX, buttonY, buttonWidth, 24);
        renderButton(graphics, applyX, buttonY, buttonWidth, "Apply", applyHovered, UIConstants.Accent.GREEN);

        // Preset menu overlay
        if (showPresetMenu) {
            renderPresetMenu(graphics, panelX, buttonY - 150, mouseX, mouseY);
        }

        // History panel overlay
        if (showHistoryPanel) {
            renderHistoryPanel(graphics, panelX, buttonY - 180, mouseX, mouseY);
        }

        // Template menu overlay
        if (showTemplateMenu) {
            renderTemplateMenu(graphics, panelX, buttonY - 150, mouseX, mouseY);
        }
    }

    private void renderTemplateMenu(GuiGraphics graphics, int panelX, int menuY, int mouseX, int mouseY) {
        int menuX = panelX + 100;
        int menuW = 200;
        int menuH = 180; // Increased height for more items

        // Background
        graphics.fill(menuX, menuY, menuX + menuW, menuY + menuH, UIConstants.Background.PANEL);
        AxiomRenderer.drawBorder(graphics, menuX, menuY, menuW, menuH, UIConstants.Accent.GREEN);

        // Title
        graphics.drawString(font, "Apply Template", menuX + 10, menuY + 8, UIConstants.Accent.GREEN, false);

        // Close button
        int closeX = menuX + menuW - 20;
        boolean closeHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, closeX, menuY + 5, 14, 14);
        graphics.fill(closeX, menuY + 5, closeX + 14, menuY + 19, closeHovered ? UIConstants.Accent.RED : UIConstants.Background.INPUT);
        graphics.drawString(font, "X", closeX + 3, menuY + 8, UIConstants.Text.WHITE, false);

        // Suggest template based on current item
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        var suggestedOpt = ItemEditorDataManager.INSTANCE.suggestTemplate(itemId);

        int y = menuY + 25;
        if (suggestedOpt.isPresent()) {
            ItemEditorDataManager.TemplateData suggested = suggestedOpt.get();
            graphics.drawString(font, "Suggested:", menuX + 10, y, UIConstants.Text.SECONDARY, false);
            y += 12;
            boolean suggestHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, menuX + 10, y, menuW - 20, 16);
            if (suggestHovered) {
                graphics.fill(menuX + 10, y, menuX + menuW - 10, y + 16, UIConstants.Background.HOVER);
            }
            graphics.drawString(font, "★ " + suggested.name, menuX + 15, y + 4, UIConstants.Accent.GOLD, false);
            y += 20;
        }

        // Show other templates with scrolling
        graphics.drawString(font, "All Templates:", menuX + 10, y, UIConstants.Text.SECONDARY, false);
        y += 12;

        Map<String, ItemEditorDataManager.TemplateData> templates = ItemEditorDataManager.INSTANCE.getTemplates();
        List<ItemEditorDataManager.TemplateData> templateList = new ArrayList<>(templates.values());
        int visibleItems = 7; // Max visible items
        int itemHeight = 15;
        int listStartY = y;

        // Clamp scroll offset
        int maxScroll = Math.max(0, templateList.size() - visibleItems);
        templateScrollOffset = Math.max(0, Math.min(maxScroll, templateScrollOffset));

        for (int i = 0; i < Math.min(visibleItems, templateList.size() - templateScrollOffset); i++) {
            int idx = i + templateScrollOffset;
            if (idx >= templateList.size()) break;
            ItemEditorDataManager.TemplateData template = templateList.get(idx);
            boolean itemHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, menuX + 10, y, menuW - 30, 14);
            if (itemHovered) {
                graphics.fill(menuX + 10, y, menuX + menuW - 20, y + 14, UIConstants.Background.HOVER);
            }
            graphics.drawString(font, template.name, menuX + 15, y + 3, UIConstants.Text.PRIMARY, false);
            y += itemHeight;
        }

        // Draw scrollbar if needed
        if (templateList.size() > visibleItems) {
            int scrollbarX = menuX + menuW - 12;
            int scrollbarHeight = visibleItems * itemHeight;
            int thumbHeight = Math.max(20, scrollbarHeight * visibleItems / templateList.size());
            int thumbY = listStartY + (int) ((float) templateScrollOffset / maxScroll * (scrollbarHeight - thumbHeight));

            // Scrollbar track
            graphics.fill(scrollbarX, listStartY, scrollbarX + 6, listStartY + scrollbarHeight, UIConstants.Background.INPUT);
            // Scrollbar thumb
            graphics.fill(scrollbarX, thumbY, scrollbarX + 6, thumbY + thumbHeight, UIConstants.Accent.GREEN);

            // Scroll hint
            graphics.drawString(font, "↕", scrollbarX - 1, menuY + menuH - 14, UIConstants.Text.MUTED, false);
        }
    }

    private void renderHistoryPanel(GuiGraphics graphics, int panelX, int menuY, int mouseX, int mouseY) {
        int menuX = panelX + 15;
        int menuW = 200;
        int menuH = 180;

        // Background
        graphics.fill(menuX, menuY, menuX + menuW, menuY + menuH, UIConstants.Background.PANEL);
        AxiomRenderer.drawBorder(graphics, menuX, menuY, menuW, menuH, UIConstants.Accent.CYAN);

        // Title with close button
        graphics.drawString(font, "Edit History", menuX + 10, menuY + 8, UIConstants.Accent.CYAN, false);

        int closeX = menuX + menuW - 20;
        boolean closeHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, closeX, menuY + 5, 14, 14);
        graphics.fill(closeX, menuY + 5, closeX + 14, menuY + 19, closeHovered ? UIConstants.Accent.RED : UIConstants.Background.INPUT);
        graphics.drawString(font, "X", closeX + 3, menuY + 8, UIConstants.Text.WHITE, false);

        // History entries with scrolling
        List<ItemEditorDataManager.HistoryEntry> history = ItemEditorDataManager.INSTANCE.getHistory();
        int listY = menuY + 25;
        int itemHeight = 16;
        int visibleItems = 8;

        if (history.isEmpty()) {
            graphics.drawString(font, "No history yet", menuX + 10, listY, UIConstants.Text.MUTED, false);
        } else {
            // Clamp scroll offset
            int maxScroll = Math.max(0, history.size() - visibleItems);
            historyScrollOffset = Math.max(0, Math.min(maxScroll, historyScrollOffset));

            // Display entries (most recent first, with scroll)
            for (int i = 0; i < Math.min(visibleItems, history.size() - historyScrollOffset); i++) {
                int idx = history.size() - 1 - i - historyScrollOffset;
                if (idx < 0) break;
                ItemEditorDataManager.HistoryEntry entry = history.get(idx);
                int itemY = listY + i * itemHeight;

                // Timestamp (short format)
                String time = new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date(entry.timestamp));
                graphics.drawString(font, time, menuX + 8, itemY, UIConstants.Text.MUTED, false);

                // Action
                String actionText = entry.action;
                if (actionText.length() > 18) actionText = actionText.substring(0, 16) + "..";
                graphics.drawString(font, actionText, menuX + 40, itemY, UIConstants.Text.PRIMARY, false);
            }

            // Draw scrollbar if needed
            if (history.size() > visibleItems) {
                int scrollbarX = menuX + menuW - 12;
                int scrollbarHeight = visibleItems * itemHeight;
                int thumbHeight = Math.max(20, scrollbarHeight * visibleItems / history.size());
                int thumbY = listY + (int) ((float) historyScrollOffset / maxScroll * (scrollbarHeight - thumbHeight));

                // Scrollbar track
                graphics.fill(scrollbarX, listY, scrollbarX + 6, listY + scrollbarHeight, UIConstants.Background.INPUT);
                // Scrollbar thumb
                graphics.fill(scrollbarX, thumbY, scrollbarX + 6, thumbY + thumbHeight, UIConstants.Accent.CYAN);
            }
        }

        // Clear button
        int clearY = menuY + menuH - 20;
        boolean clearHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, menuX + 10, clearY, 60, 14);
        graphics.fill(menuX + 10, clearY, menuX + 70, clearY + 14, clearHovered ? UIConstants.Accent.RED : UIConstants.Background.INPUT);
        graphics.drawString(font, "Clear", menuX + 25, clearY + 3, UIConstants.Text.WHITE, false);
    }

    private void renderSmallButton(GuiGraphics graphics, int x, int y, int width, String text, boolean hovered, int color) {
        int bgColor = hovered ? color : UIConstants.Background.INPUT;
        graphics.fill(x, y, x + width, y + 20, bgColor);
        AxiomRenderer.drawBorder(graphics, x, y, width, 20, color);
        int textX = x + (width - font.width(text)) / 2;
        graphics.drawString(font, text, textX, y + 6, hovered ? UIConstants.Text.WHITE : color, false);
    }

    private void renderPresetMenu(GuiGraphics graphics, int panelX, int menuY, int mouseX, int mouseY) {
        int menuX = panelX + PANEL_WIDTH - 200;
        int menuW = 185;
        int menuH = 180; // Increased height

        // Background
        graphics.fill(menuX, menuY, menuX + menuW, menuY + menuH, UIConstants.Background.PANEL);
        AxiomRenderer.drawBorder(graphics, menuX, menuY, menuW, menuH, UIConstants.Accent.PURPLE);

        // Title
        graphics.drawString(font, "Presets", menuX + 10, menuY + 8, UIConstants.Accent.PURPLE, false);

        // Close button
        int closeX = menuX + menuW - 18;
        boolean closeHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, closeX, menuY + 5, 14, 14);
        graphics.fill(closeX, menuY + 5, closeX + 14, menuY + 19, closeHovered ? UIConstants.Accent.RED : UIConstants.Background.INPUT);
        graphics.drawString(font, "X", closeX + 3, menuY + 7, UIConstants.Text.WHITE, false);

        // Save section
        int y = menuY + 25;
        graphics.drawString(font, "Save as:", menuX + 10, y, UIConstants.Text.SECONDARY, false);
        // Input field rendered separately

        // Save button
        int saveBtnX = menuX + menuW - 50;
        boolean saveHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, saveBtnX, y + 15, 40, 16);
        graphics.fill(saveBtnX, y + 15, saveBtnX + 40, y + 31, saveHovered ? UIConstants.Accent.GREEN : UIConstants.Background.INPUT);
        graphics.drawString(font, "Save", saveBtnX + 8, y + 19, UIConstants.Text.WHITE, false);

        // Preset list with scrolling
        y += 40;
        graphics.drawString(font, "Load:", menuX + 10, y, UIConstants.Text.SECONDARY, false);
        y += 12;
        int listStartY = y;

        List<ItemEditorDataManager.PresetData> savedPresets = ItemEditorDataManager.INSTANCE.getPresets();
        int visibleItems = 6; // Increased visible items
        int itemHeight = 18;

        if (savedPresets.isEmpty()) {
            graphics.drawString(font, "No presets saved", menuX + 10, y, UIConstants.Text.MUTED, false);
        } else {
            // Clamp scroll offset
            int maxScroll = Math.max(0, savedPresets.size() - visibleItems);
            presetScrollOffset = Math.max(0, Math.min(maxScroll, presetScrollOffset));

            for (int i = 0; i < Math.min(visibleItems, savedPresets.size() - presetScrollOffset); i++) {
                int idx = i + presetScrollOffset;
                if (idx >= savedPresets.size()) break;
                ItemEditorDataManager.PresetData preset = savedPresets.get(idx);
                int itemY = y + i * itemHeight;

                boolean itemHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, menuX + 10, itemY, menuW - 50, 16);
                if (itemHovered) {
                    graphics.fill(menuX + 10, itemY, menuX + menuW - 40, itemY + 16, UIConstants.Background.HOVER);
                }
                graphics.drawString(font, preset.name, menuX + 15, itemY + 4, UIConstants.Text.PRIMARY, false);

                // Delete button
                int delX = menuX + menuW - 35;
                boolean delHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, delX, itemY, 14, 14);
                graphics.fill(delX, itemY + 1, delX + 14, itemY + 15, delHovered ? UIConstants.Accent.RED : UIConstants.Background.INPUT);
                graphics.drawString(font, "X", delX + 3, itemY + 4, UIConstants.Text.WHITE, false);
            }

            // Draw scrollbar if needed
            if (savedPresets.size() > visibleItems) {
                int scrollbarX = menuX + menuW - 12;
                int scrollbarHeight = visibleItems * itemHeight;
                int thumbHeight = Math.max(20, scrollbarHeight * visibleItems / savedPresets.size());
                int thumbY = listStartY + (int) ((float) presetScrollOffset / maxScroll * (scrollbarHeight - thumbHeight));

                // Scrollbar track
                graphics.fill(scrollbarX, listStartY, scrollbarX + 6, listStartY + scrollbarHeight, UIConstants.Background.INPUT);
                // Scrollbar thumb
                graphics.fill(scrollbarX, thumbY, scrollbarX + 6, thumbY + thumbHeight, UIConstants.Accent.PURPLE);
            }
        }
    }

    private void renderButton(GuiGraphics graphics, int x, int y, int width, String text, boolean hovered, int color) {
        int bgColor = hovered ? color : UIConstants.Background.INPUT;
        graphics.fill(x, y, x + width, y + 24, bgColor);
        AxiomRenderer.drawBorder(graphics, x, y, width, 24, color);

        int textX = x + (width - font.width(text)) / 2;
        graphics.drawString(font, text, textX, y + 8, hovered ? UIConstants.Text.WHITE : color, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;
        int contentY = panelY + TAB_HEIGHT + PREVIEW_SIZE + 20;

        // Right-click handling for favorites in enchantment picker
        if (button == 1 && showEnchantPicker) {
            handleEnchantPickerRightClick(mx, my, panelX, contentY);
            return true;
        }

        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        // Tab clicks
        Tab[] tabs = Tab.values();
        int tabWidth = PANEL_WIDTH / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            int tx = panelX + i * tabWidth;
            if (AxiomRenderer.isMouseOver(mx, my, tx, panelY, tabWidth, TAB_HEIGHT)) {
                currentTab = tabs[i];
                updateFieldVisibility();
                return true;
            }
        }

        // Mode toggle
        int modeX = panelX + PANEL_WIDTH - 100;
        int modeY = panelY + TAB_HEIGHT + 10;
        if (AxiomRenderer.isMouseOver(mx, my, modeX, modeY, 90, 16)) {
            editGlobal = !editGlobal;
            return true;
        }

        // Tab-specific clicks
        switch (currentTab) {
            case STATS -> handleStatsClick(mx, my, panelX, contentY);
            case ENCHANTS -> handleEnchantsClick(mx, my, panelX, contentY);
            case DURABILITY -> handleDurabilityClick(mx, my, panelX, contentY);
            case ATTRIBUTES -> handleAttributesClick(mx, my, panelX, contentY);
            case COMPONENTS -> {} // Read-only tab, no clicks
        }

        // Button clicks
        int buttonY = panelY + PANEL_HEIGHT - 35;

        // Top row buttons (Undo/Redo/Presets)
        int smallBtnW = 50;
        int topRowY = buttonY - 30;

        // Undo button
        int undoX = panelX + 15;
        if (AxiomRenderer.isMouseOver(mx, my, undoX, topRowY, smallBtnW, 20)) {
            undo();
            return true;
        }

        // Redo button
        int redoX = undoX + smallBtnW + 5;
        if (AxiomRenderer.isMouseOver(mx, my, redoX, topRowY, smallBtnW, 20)) {
            redo();
            return true;
        }

        // History button
        int historyX = redoX + smallBtnW + 10;
        if (AxiomRenderer.isMouseOver(mx, my, historyX, topRowY, 55, 20)) {
            showHistoryPanel = !showHistoryPanel;
            showPresetMenu = false; // Close preset menu
            return true;
        }

        // History panel clicks
        if (showHistoryPanel) {
            if (handleHistoryPanelClick(mx, my, panelX, buttonY - 180)) {
                return true;
            }
        }

        // Export button
        int exportX = historyX + 60;
        if (AxiomRenderer.isMouseOver(mx, my, exportX, topRowY, 50, 20)) {
            exportConfigToClipboard();
            return true;
        }

        // Import button
        int importX = exportX + 55;
        if (AxiomRenderer.isMouseOver(mx, my, importX, topRowY, 50, 20)) {
            importConfigFromClipboard();
            return true;
        }

        // Template button
        int templateX = importX + 55;
        if (AxiomRenderer.isMouseOver(mx, my, templateX, topRowY, 60, 20)) {
            showTemplateMenu = !showTemplateMenu;
            showPresetMenu = false;
            showHistoryPanel = false;
            return true;
        }

        // Template menu clicks
        if (showTemplateMenu) {
            if (handleTemplateMenuClick(mx, my, panelX, buttonY - 150)) {
                return true;
            }
        }

        // Presets button
        int presetsX = panelX + PANEL_WIDTH - 80;
        if (AxiomRenderer.isMouseOver(mx, my, presetsX, topRowY, 65, 20)) {
            showPresetMenu = !showPresetMenu;
            showHistoryPanel = false; // Close history panel
            updateFieldVisibility();
            return true;
        }

        // Preset menu clicks
        if (showPresetMenu) {
            if (handlePresetMenuClick(mx, my, panelX, buttonY - 150)) {
                return true;
            }
        }

        // Main row buttons
        int buttonWidth = 100;
        int gap = 15;
        int totalWidth = buttonWidth * 3 + gap * 2;
        int startX = panelX + (PANEL_WIDTH - totalWidth) / 2;

        if (AxiomRenderer.isMouseOver(mx, my, startX, buttonY, buttonWidth, 24)) {
            saveUndoState();
            reset();
            return true;
        }

        int cancelX = startX + buttonWidth + gap;
        if (AxiomRenderer.isMouseOver(mx, my, cancelX, buttonY, buttonWidth, 24)) {
            onClose();
            return true;
        }

        int applyX = cancelX + buttonWidth + gap;
        if (AxiomRenderer.isMouseOver(mx, my, applyX, buttonY, buttonWidth, 24)) {
            apply();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Handle clicks in the preset menu.
     */
    private boolean handlePresetMenuClick(int mx, int my, int panelX, int menuY) {
        int menuX = panelX + PANEL_WIDTH - 200;
        int menuW = 185;
        int menuH = 180;

        // Click outside closes menu
        if (!AxiomRenderer.isMouseOver(mx, my, menuX, menuY, menuW, menuH)) {
            showPresetMenu = false;
            updateFieldVisibility();
            return true;
        }

        // Close button
        int closeX = menuX + menuW - 18;
        if (AxiomRenderer.isMouseOver(mx, my, closeX, menuY + 5, 14, 14)) {
            showPresetMenu = false;
            updateFieldVisibility();
            return true;
        }

        // Save button
        int y = menuY + 25;
        int saveBtnX = menuX + menuW - 50;
        if (AxiomRenderer.isMouseOver(mx, my, saveBtnX, y + 15, 40, 16)) {
            savePreset(presetName);
            return true;
        }

        // Preset list clicks with scroll support
        y += 52;
        List<ItemEditorDataManager.PresetData> savedPresets = ItemEditorDataManager.INSTANCE.getPresets();
        int visibleItems = 6;
        int itemHeight = 18;

        for (int i = 0; i < Math.min(visibleItems, savedPresets.size() - presetScrollOffset); i++) {
            int idx = i + presetScrollOffset;
            if (idx >= savedPresets.size()) break;
            ItemEditorDataManager.PresetData preset = savedPresets.get(idx);
            int itemY = y + i * itemHeight;

            // Delete button
            int delX = menuX + menuW - 35;
            if (AxiomRenderer.isMouseOver(mx, my, delX, itemY, 14, 14)) {
                deletePresetData(preset.name);
                return true;
            }

            // Load preset (click on name)
            if (AxiomRenderer.isMouseOver(mx, my, menuX + 10, itemY, menuW - 50, 16)) {
                loadPresetData(preset);
                return true;
            }
        }

        return false;
    }

    /**
     * Handle clicks in the template menu.
     */
    private boolean handleTemplateMenuClick(int mx, int my, int panelX, int menuY) {
        int menuX = panelX + 100;
        int menuW = 200;
        int menuH = 180;

        // Close button (X)
        int closeX = menuX + menuW - 20;
        if (AxiomRenderer.isMouseOver(mx, my, closeX, menuY + 5, 14, 14)) {
            showTemplateMenu = false;
            return true;
        }

        // Click outside closes menu
        if (!AxiomRenderer.isMouseOver(mx, my, menuX, menuY, menuW, menuH)) {
            showTemplateMenu = false;
            return true;
        }

        // Suggest template click
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        var suggestedOpt = ItemEditorDataManager.INSTANCE.suggestTemplate(itemId);

        int y = menuY + 25;
        if (suggestedOpt.isPresent()) {
            y += 12; // "Suggested:" label
            if (AxiomRenderer.isMouseOver(mx, my, menuX + 10, y, menuW - 20, 16)) {
                applyTemplate(suggestedOpt.get());
                return true;
            }
            y += 20;
        }

        // All templates list with scroll support
        y += 12; // "All Templates:" label
        Map<String, ItemEditorDataManager.TemplateData> templates = ItemEditorDataManager.INSTANCE.getTemplates();
        List<ItemEditorDataManager.TemplateData> templateList = new ArrayList<>(templates.values());
        int visibleItems = 7;
        int itemHeight = 15;

        for (int i = 0; i < Math.min(visibleItems, templateList.size() - templateScrollOffset); i++) {
            int idx = i + templateScrollOffset;
            if (idx >= templateList.size()) break;
            if (AxiomRenderer.isMouseOver(mx, my, menuX + 10, y, menuW - 30, 14)) {
                applyTemplate(templateList.get(idx));
                return true;
            }
            y += itemHeight;
        }

        return false;
    }

    /**
     * Apply a template to current item.
     */
    private void applyTemplate(ItemEditorDataManager.TemplateData template) {
        saveUndoState();

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // Apply enchantments from template
        var enchRegistry = mc.level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        for (ItemEditorDataManager.EnchantData ed : template.enchantments) {
            try {
                var loc = net.minecraft.resources.ResourceLocation.parse(ed.id);
                var holder = enchRegistry.getHolder(loc);
                if (holder.isPresent()) {
                    String eName = formatEnchantmentName(holder.get());
                    // Check if already present
                    boolean found = false;
                    for (EnchantmentEntry entry : enchantments) {
                        String entryId = entry.enchantment.unwrapKey().map(k -> k.location().toString()).orElse("");
                        if (entryId.equals(ed.id)) {
                            entry.level = ed.level;
                            entry.toRemove = false;
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        enchantments.add(new EnchantmentEntry(holder.get(), eName, ed.level));
                    }
                }
            } catch (Exception ignored) {}
        }

        // Apply attributes from template
        var attrRegistry = mc.level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ATTRIBUTE);
        for (ItemEditorDataManager.AttrData ad : template.attributes) {
            try {
                var loc = net.minecraft.resources.ResourceLocation.parse(ad.id);
                var holder = attrRegistry.getHolder(loc);
                if (holder.isPresent()) {
                    String aName = formatAttributeName(holder.get());
                    AttributeModifier.Operation op = switch (ad.operation) {
                        case 1 -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
                        case 2 -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
                        default -> AttributeModifier.Operation.ADD_VALUE;
                    };
                    // Check if already present
                    boolean found = false;
                    for (AttributeEntry entry : attributes) {
                        String entryId = entry.attribute.unwrapKey().map(k -> k.location().toString()).orElse("");
                        if (entryId.equals(ad.id)) {
                            entry.value = ad.value;
                            entry.operation = op;
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        attributes.add(new AttributeEntry(aName, holder.get(), ad.value, op));
                    }
                }
            } catch (Exception ignored) {}
        }

        rebuildAttributeFields();

        showTemplateMenu = false;
        showStatus("Applied template: " + template.name, UIConstants.Accent.GREEN);
        ItemEditorDataManager.INSTANCE.addHistoryEntry("Applied template", template.name, template.itemCategory);
    }

    /**
     * Handle clicks in the history panel.
     */
    private boolean handleHistoryPanelClick(int mx, int my, int panelX, int menuY) {
        int menuX = panelX + 15;
        int menuW = 200;
        int menuH = 160;

        // Close button (X)
        int closeX = menuX + menuW - 20;
        if (AxiomRenderer.isMouseOver(mx, my, closeX, menuY + 5, 14, 14)) {
            showHistoryPanel = false;
            return true;
        }

        // Clear button
        int clearY = menuY + menuH - 20;
        if (AxiomRenderer.isMouseOver(mx, my, menuX + 10, clearY, 60, 14)) {
            ItemEditorDataManager.INSTANCE.clearHistory();
            showStatus("History cleared", UIConstants.Text.MUTED);
            return true;
        }

        // Click outside closes panel
        if (!AxiomRenderer.isMouseOver(mx, my, menuX, menuY, menuW, menuH)) {
            showHistoryPanel = false;
            return true;
        }

        return false;
    }

    private void handleStatsClick(int mx, int my, int panelX, int startY) {
        int sliderX = panelX + 15 + 130;
        int sliderWidth = 150;

        for (int i = 0; i < statSliders.size(); i++) {
            StatSlider stat = statSliders.get(i);
            int y = startY + i * 28 + 4;

            if (AxiomRenderer.isMouseOver(mx, my, sliderX, y, sliderWidth, 14)) {
                stat.isDragging = true;
                updateSliderFromMouse(stat, mx, sliderX, sliderWidth);
            }
        }
    }

    private void handleEnchantsClick(int mx, int my, int panelX, int startY) {
        // Handle picker if open
        if (showEnchantPicker) {
            handleEnchantPickerClick(mx, my, panelX, startY);
            return;
        }

        // Add button - opens enchantment picker with ALL mod enchantments
        int addBtnX = panelX + PANEL_WIDTH - 100;
        if (AxiomRenderer.isMouseOver(mx, my, addBtnX, startY - 18, 80, 18)) {
            loadAllAvailableEnchantments();
            showEnchantPicker = true;
            pickerScrollOffset = 0;
            pickerSearch = "";
            return;
        }

        // Enchantment list interactions
        int listY = startY + 25;
        List<EnchantmentEntry> filtered = enchantments.stream()
            .filter(e -> enchantSearch.isEmpty() || e.name.toLowerCase().contains(enchantSearch))
            .toList();

        for (int i = 0; i < Math.min(8, filtered.size() - enchantScrollOffset); i++) {
            int idx = i + enchantScrollOffset;
            if (idx >= filtered.size()) break;

            EnchantmentEntry ench = filtered.get(idx);
            int ey = listY + i * 22;
            int levelX = panelX + PANEL_WIDTH - 150;

            // Minus
            if (AxiomRenderer.isMouseOver(mx, my, levelX, ey + 2, 16, 16)) {
                saveUndoState();
                if (ench.level > 1) {
                    ench.level--;
                } else {
                    ench.toRemove = true;
                }
                return;
            }

            // Plus
            int plusX = levelX + 40;
            if (AxiomRenderer.isMouseOver(mx, my, plusX, ey + 2, 16, 16)) {
                saveUndoState();
                ench.level = Math.min(255, ench.level + 1);
                ench.toRemove = false;
                return;
            }

            // Remove
            int removeX = levelX + 65;
            if (AxiomRenderer.isMouseOver(mx, my, removeX, ey + 2, 16, 16)) {
                saveUndoState();
                ench.toRemove = !ench.toRemove;
                return;
            }
        }
    }

    /**
     * Handle clicks in the enchantment picker overlay.
     * Shows ALL enchantments from ALL mods with filter support.
     */
    private void handleEnchantPickerClick(int mx, int my, int panelX, int startY) {
        int pickerX = panelX + 20;
        int pickerY = startY;
        int pickerW = PANEL_WIDTH - 40;
        int pickerH = 200;

        // Close button (X)
        int closeX = pickerX + pickerW - 20;
        if (AxiomRenderer.isMouseOver(mx, my, closeX, pickerY + 5, 16, 16)) {
            showEnchantPicker = false;
            return;
        }

        // Filter tabs
        int filterY = pickerY + 22;
        int filterTabW = 40;
        EnchantFilter[] filterValues = EnchantFilter.values();
        for (int i = 0; i < filterValues.length; i++) {
            int fx = pickerX + 5 + i * (filterTabW + 2);
            if (AxiomRenderer.isMouseOver(mx, my, fx, filterY, filterTabW, 14)) {
                currentEnchantFilter = filterValues[i];
                pickerScrollOffset = 0; // Reset scroll when changing filter
                return;
            }
        }

        // Click outside picker closes it
        if (!AxiomRenderer.isMouseOver(mx, my, pickerX, pickerY, pickerW, pickerH)) {
            showEnchantPicker = false;
            return;
        }

        // Enchantment list (adjusted Y for filter tabs)
        List<Holder<Enchantment>> filtered = getFilteredAvailableEnchants();
        int listY = pickerY + 58;
        int itemHeight = 18;
        int visibleItems = 6;

        for (int i = 0; i < Math.min(visibleItems, filtered.size() - pickerScrollOffset); i++) {
            int idx = i + pickerScrollOffset;
            if (idx >= filtered.size()) break;

            int itemY = listY + i * itemHeight;
            if (AxiomRenderer.isMouseOver(mx, my, pickerX + 10, itemY, pickerW - 40, itemHeight)) {
                addEnchantmentFromPicker(filtered.get(idx));
                return;
            }
        }
    }

    /**
     * Handle right-click in enchantment picker to toggle favorites.
     */
    private void handleEnchantPickerRightClick(int mx, int my, int panelX, int startY) {
        int pickerX = panelX + 20;
        int pickerY = startY;
        int pickerW = PANEL_WIDTH - 40;

        // Enchantment list (same Y calculation as handleEnchantPickerClick)
        List<Holder<Enchantment>> filtered = getFilteredAvailableEnchants();
        int listY = pickerY + 58;
        int itemHeight = 18;
        int visibleItems = 6;

        for (int i = 0; i < Math.min(visibleItems, filtered.size() - pickerScrollOffset); i++) {
            int idx = i + pickerScrollOffset;
            if (idx >= filtered.size()) break;

            int itemY = listY + i * itemHeight;
            if (AxiomRenderer.isMouseOver(mx, my, pickerX + 10, itemY, pickerW - 40, itemHeight)) {
                Holder<Enchantment> ench = filtered.get(idx);
                String enchId = ench.unwrapKey().map(k -> k.location().toString()).orElse("");
                if (!enchId.isEmpty()) {
                    ItemEditorDataManager.INSTANCE.toggleEnchantmentFavorite(enchId);
                    String name = formatEnchantmentName(ench);
                    boolean isFav = ItemEditorDataManager.INSTANCE.getFavoriteEnchantments().contains(enchId);
                    showStatus(isFav ? "★ Added to favorites: " + name : "Removed from favorites: " + name,
                               isFav ? UIConstants.Accent.GOLD : UIConstants.Text.MUTED);
                }
                return;
            }
        }
    }

    private void handleDurabilityClick(int mx, int my, int panelX, int startY) {
        int contentX = panelX + 15;

        // Unbreakable toggle
        int unbY = startY + 10 + 30 + 30 + 35;
        if (AxiomRenderer.isMouseOver(mx, my, contentX, unbY, 200, 20)) {
            isUnbreakable = !isUnbreakable;
            return;
        }

        // Quick actions
        int actionY = unbY + 35 + 18;

        // Full repair
        if (AxiomRenderer.isMouseOver(mx, my, contentX, actionY, 100, 20)) {
            durabilityField.setValue(String.valueOf(stack.getMaxDamage()));
            showStatus("Durability set to max!", UIConstants.Accent.GREEN);
            return;
        }

        // Set to 1
        int breakX = contentX + 110;
        if (AxiomRenderer.isMouseOver(mx, my, breakX, actionY, 100, 20)) {
            durabilityField.setValue("1");
            showStatus("Durability set to 1!", UIConstants.Accent.RED);
        }
    }

    private void handleAttributesClick(int mx, int my, int panelX, int startY) {
        // Handle picker if open
        if (showAttributePicker) {
            handleAttributePickerClick(mx, my, panelX, startY);
            return;
        }

        // Add button - opens attribute picker
        int addBtnX = panelX + PANEL_WIDTH - 100;
        if (AxiomRenderer.isMouseOver(mx, my, addBtnX, startY - 18, 80, 18)) {
            loadAllAvailableAttributes();
            showAttributePicker = true;
            attrPickerScrollOffset = 0;
            attrPickerSearch = "";
            updateFieldVisibility();
            return;
        }

        for (int i = 0; i < attributes.size(); i++) {
            AttributeEntry attr = attributes.get(i);
            int y = startY + i * 28;
            int opX = panelX + 200;

            // Operation toggle
            if (AxiomRenderer.isMouseOver(mx, my, opX, y + 2, 20, 16)) {
                attr.operation = switch (attr.operation) {
                    case ADD_VALUE -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
                    case ADD_MULTIPLIED_BASE -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
                    case ADD_MULTIPLIED_TOTAL -> AttributeModifier.Operation.ADD_VALUE;
                };
                saveUndoState();
                return;
            }

            // Remove button
            int removeX = panelX + PANEL_WIDTH - 40;
            if (AxiomRenderer.isMouseOver(mx, my, removeX, y + 2, 16, 16)) {
                saveUndoState();
                attributes.remove(i);
                rebuildAttributeFields();
                showStatus("Attribute removed", UIConstants.Accent.YELLOW);
                return;
            }
        }
    }

    /**
     * Handle clicks in the attribute picker overlay.
     */
    private void handleAttributePickerClick(int mx, int my, int panelX, int startY) {
        int pickerX = panelX + 20;
        int pickerY = startY;
        int pickerW = PANEL_WIDTH - 40;
        int pickerH = 200;

        // Close button
        int closeX = pickerX + pickerW - 20;
        if (AxiomRenderer.isMouseOver(mx, my, closeX, pickerY + 5, 16, 16)) {
            showAttributePicker = false;
            updateFieldVisibility();
            return;
        }

        // Click outside picker closes it
        if (!AxiomRenderer.isMouseOver(mx, my, pickerX, pickerY, pickerW, pickerH)) {
            showAttributePicker = false;
            updateFieldVisibility();
            return;
        }

        // Attribute list
        List<Holder<Attribute>> filtered = getFilteredAvailableAttributes();
        int listY = pickerY + 42;
        int itemHeight = 18;
        int visibleItems = 7;

        for (int i = 0; i < Math.min(visibleItems, filtered.size() - attrPickerScrollOffset); i++) {
            int idx = i + attrPickerScrollOffset;
            if (idx >= filtered.size()) break;

            int itemY = listY + i * itemHeight;
            if (AxiomRenderer.isMouseOver(mx, my, pickerX + 10, itemY, pickerW - 40, itemHeight)) {
                saveUndoState();
                addAttributeFromPicker(filtered.get(idx));
                return;
            }
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && currentTab == Tab.STATS) {
            int panelX = (this.width - PANEL_WIDTH) / 2;
            int sliderX = panelX + 15 + 130;
            int sliderWidth = 150;

            for (StatSlider stat : statSliders) {
                if (stat.isDragging) {
                    updateSliderFromMouse(stat, (int) mouseX, sliderX, sliderWidth);
                    return true;
                }
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (StatSlider stat : statSliders) {
            stat.isDragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Template menu scroll
        if (showTemplateMenu) {
            Map<String, ItemEditorDataManager.TemplateData> templates = ItemEditorDataManager.INSTANCE.getTemplates();
            int maxScroll = Math.max(0, templates.size() - 7);
            templateScrollOffset = Math.max(0, Math.min(maxScroll, templateScrollOffset - (int) scrollY));
            return true;
        }

        // History panel scroll
        if (showHistoryPanel) {
            List<ItemEditorDataManager.HistoryEntry> history = ItemEditorDataManager.INSTANCE.getHistory();
            int maxScroll = Math.max(0, history.size() - 8);
            historyScrollOffset = Math.max(0, Math.min(maxScroll, historyScrollOffset - (int) scrollY));
            return true;
        }

        // Preset menu scroll
        if (showPresetMenu) {
            List<ItemEditorDataManager.PresetData> presets = ItemEditorDataManager.INSTANCE.getPresets();
            int maxScroll = Math.max(0, presets.size() - 6);
            presetScrollOffset = Math.max(0, Math.min(maxScroll, presetScrollOffset - (int) scrollY));
            return true;
        }

        if (currentTab == Tab.ENCHANTS) {
            if (showEnchantPicker) {
                // Scroll the picker
                List<Holder<Enchantment>> filtered = getFilteredAvailableEnchants();
                int maxScroll = Math.max(0, filtered.size() - 8);
                pickerScrollOffset = Math.max(0, Math.min(maxScroll, pickerScrollOffset - (int) scrollY));
            } else {
                // Scroll the enchantment list
                int maxScroll = Math.max(0, enchantments.size() - 8);
                enchantScrollOffset = Math.max(0, Math.min(maxScroll, enchantScrollOffset - (int) scrollY));
            }
            return true;
        }
        if (currentTab == Tab.ATTRIBUTES && showAttributePicker) {
            List<Holder<Attribute>> filtered = getFilteredAvailableAttributes();
            int maxScroll = Math.max(0, filtered.size() - 8);
            attrPickerScrollOffset = Math.max(0, Math.min(maxScroll, attrPickerScrollOffset - (int) scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ==================== UNDO/REDO SYSTEM ====================

    /**
     * Save current state to undo stack.
     */
    private void saveUndoState() {
        List<Float> stats = new ArrayList<>();
        for (StatSlider s : statSliders) {
            stats.add(s.value);
        }

        List<EnchantmentSnapshot> enchs = new ArrayList<>();
        for (EnchantmentEntry e : enchantments) {
            enchs.add(new EnchantmentSnapshot(e.enchantment, e.name, e.level, e.toRemove));
        }

        List<AttributeSnapshot> attrs = new ArrayList<>();
        for (AttributeEntry a : attributes) {
            attrs.add(new AttributeSnapshot(a.attribute, a.name, a.value, a.operation));
        }

        int dura = 0;
        try { dura = Integer.parseInt(durabilityField.getValue()); } catch (Exception ignored) {}
        int repair = 0;
        try { repair = Integer.parseInt(repairCostField.getValue()); } catch (Exception ignored) {}

        undoStack.push(new EditorState(stats, enchs, attrs, dura, repair, isUnbreakable));

        // Limit stack size
        while (undoStack.size() > MAX_UNDO_STATES) {
            undoStack.removeLast();
        }

        // Clear redo stack on new action
        redoStack.clear();
    }

    /**
     * Undo last action.
     */
    private void undo() {
        if (undoStack.isEmpty()) {
            showStatus("Nothing to undo", UIConstants.Text.MUTED);
            return;
        }

        // Save current state to redo stack first
        List<Float> currentStats = new ArrayList<>();
        for (StatSlider s : statSliders) currentStats.add(s.value);
        List<EnchantmentSnapshot> currentEnchs = new ArrayList<>();
        for (EnchantmentEntry e : enchantments) currentEnchs.add(new EnchantmentSnapshot(e.enchantment, e.name, e.level, e.toRemove));
        List<AttributeSnapshot> currentAttrs = new ArrayList<>();
        for (AttributeEntry a : attributes) currentAttrs.add(new AttributeSnapshot(a.attribute, a.name, a.value, a.operation));
        int dura = 0; try { dura = Integer.parseInt(durabilityField.getValue()); } catch (Exception ignored) {}
        int repair = 0; try { repair = Integer.parseInt(repairCostField.getValue()); } catch (Exception ignored) {}
        redoStack.push(new EditorState(currentStats, currentEnchs, currentAttrs, dura, repair, isUnbreakable));

        // Restore previous state
        EditorState state = undoStack.pop();
        restoreState(state);
        showStatus("Undo", UIConstants.Accent.YELLOW);
    }

    /**
     * Redo last undone action.
     */
    private void redo() {
        if (redoStack.isEmpty()) {
            showStatus("Nothing to redo", UIConstants.Text.MUTED);
            return;
        }

        // Save current to undo
        List<Float> currentStats = new ArrayList<>();
        for (StatSlider s : statSliders) currentStats.add(s.value);
        List<EnchantmentSnapshot> currentEnchs = new ArrayList<>();
        for (EnchantmentEntry e : enchantments) currentEnchs.add(new EnchantmentSnapshot(e.enchantment, e.name, e.level, e.toRemove));
        List<AttributeSnapshot> currentAttrs = new ArrayList<>();
        for (AttributeEntry a : attributes) currentAttrs.add(new AttributeSnapshot(a.attribute, a.name, a.value, a.operation));
        int dura = 0; try { dura = Integer.parseInt(durabilityField.getValue()); } catch (Exception ignored) {}
        int repair = 0; try { repair = Integer.parseInt(repairCostField.getValue()); } catch (Exception ignored) {}
        undoStack.push(new EditorState(currentStats, currentEnchs, currentAttrs, dura, repair, isUnbreakable));

        // Restore redo state
        EditorState state = redoStack.pop();
        restoreState(state);
        showStatus("Redo", UIConstants.Accent.GREEN);
    }

    /**
     * Restore editor state from snapshot.
     */
    private void restoreState(EditorState state) {
        // Restore stats
        for (int i = 0; i < Math.min(state.statValues.size(), statSliders.size()); i++) {
            statSliders.get(i).value = state.statValues.get(i);
            if (statSliders.get(i).inputField != null) {
                statSliders.get(i).inputField.setValue(formatFloat(state.statValues.get(i)));
            }
        }

        // Restore enchantments
        enchantments.clear();
        for (EnchantmentSnapshot snap : state.enchantments) {
            EnchantmentEntry entry = new EnchantmentEntry(snap.ench(), snap.name(), snap.level());
            entry.toRemove = snap.removed();
            enchantments.add(entry);
        }

        // Restore attributes
        attributes.clear();
        for (AttributeSnapshot snap : state.attributes) {
            attributes.add(new AttributeEntry(snap.name(), snap.attr(), snap.value(), snap.op()));
        }
        rebuildAttributeFields();

        // Restore durability
        durabilityField.setValue(String.valueOf(state.durability));
        repairCostField.setValue(String.valueOf(state.repairCost));
        isUnbreakable = state.unbreakable;
    }

    // ==================== PRESET SYSTEM (Persistent Storage) ====================

    /**
     * Save current configuration as a preset (persistent storage).
     */
    private void savePreset(String name) {
        if (name == null || name.trim().isEmpty()) {
            showStatus("Enter a preset name!", UIConstants.Accent.RED);
            return;
        }

        ItemEditorDataManager.PresetData preset = new ItemEditorDataManager.PresetData(name.trim());

        // Stats
        for (StatSlider s : statSliders) {
            preset.statValues.add(s.value);
        }

        // Item type for filtering
        if (!stack.isEmpty()) {
            preset.itemType = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        }

        // Enchantments (convert to serializable format)
        for (EnchantmentEntry e : enchantments) {
            if (!e.toRemove) {
                String enchId = e.enchantment.unwrapKey()
                    .map(k -> k.location().toString())
                    .orElse("");
                if (!enchId.isEmpty()) {
                    preset.enchantments.add(new ItemEditorDataManager.EnchantData(enchId, e.level));
                }
            }
        }

        // Attributes (convert to serializable format)
        for (AttributeEntry a : attributes) {
            String attrId = a.attribute.unwrapKey()
                .map(k -> k.location().toString())
                .orElse("");
            if (!attrId.isEmpty()) {
                int opCode = switch (a.operation) {
                    case ADD_VALUE -> 0;
                    case ADD_MULTIPLIED_BASE -> 1;
                    case ADD_MULTIPLIED_TOTAL -> 2;
                };
                preset.attributes.add(new ItemEditorDataManager.AttrData(attrId, a.value, opCode));
            }
        }

        preset.unbreakable = isUnbreakable;

        // Save to persistent storage
        ItemEditorDataManager.INSTANCE.savePreset(preset);

        // Add to history
        ItemEditorDataManager.INSTANCE.addHistoryEntry("preset_save",
            stack.isEmpty() ? "Unknown" : stack.getHoverName().getString(),
            "Saved preset: " + name.trim());

        showStatus("Preset '" + name.trim() + "' saved!", UIConstants.Accent.GREEN);
        showPresetMenu = false;
        presetNameBox.visible = false;
    }

    /**
     * Load a preset configuration from persistent storage.
     */
    private void loadPresetData(ItemEditorDataManager.PresetData preset) {
        saveUndoState();

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // Restore stats
        for (int i = 0; i < Math.min(preset.statValues.size(), statSliders.size()); i++) {
            statSliders.get(i).value = preset.statValues.get(i);
            if (statSliders.get(i).inputField != null) {
                statSliders.get(i).inputField.setValue(formatFloat(preset.statValues.get(i)));
            }
        }

        // Restore enchantments (convert from serializable format)
        enchantments.clear();
        var enchRegistry = mc.level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        for (ItemEditorDataManager.EnchantData ed : preset.enchantments) {
            try {
                var loc = net.minecraft.resources.ResourceLocation.parse(ed.id);
                var holder = enchRegistry.getHolder(loc);
                if (holder.isPresent()) {
                    String eName = formatEnchantmentName(holder.get());
                    enchantments.add(new EnchantmentEntry(holder.get(), eName, ed.level));
                }
            } catch (Exception ignored) {}
        }

        // Restore attributes (convert from serializable format)
        attributes.clear();
        var attrRegistry = mc.level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ATTRIBUTE);
        for (ItemEditorDataManager.AttrData ad : preset.attributes) {
            try {
                var loc = net.minecraft.resources.ResourceLocation.parse(ad.id);
                var holder = attrRegistry.getHolder(loc);
                if (holder.isPresent()) {
                    String aName = formatAttributeName(holder.get());
                    AttributeModifier.Operation op = switch (ad.operation) {
                        case 1 -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
                        case 2 -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
                        default -> AttributeModifier.Operation.ADD_VALUE;
                    };
                    attributes.add(new AttributeEntry(aName, holder.get(), ad.value, op));
                }
            } catch (Exception ignored) {}
        }
        rebuildAttributeFields();

        isUnbreakable = preset.unbreakable;

        // Add to history
        ItemEditorDataManager.INSTANCE.addHistoryEntry("preset_load",
            stack.isEmpty() ? "Unknown" : stack.getHoverName().getString(),
            "Loaded preset: " + preset.name);

        showStatus("Loaded preset: " + preset.name, UIConstants.Accent.GREEN);
        showPresetMenu = false;
    }

    /**
     * Delete a preset from persistent storage.
     */
    private void deletePresetData(String presetName) {
        ItemEditorDataManager.INSTANCE.deletePreset(presetName);
        showStatus("Deleted preset: " + presetName, UIConstants.Accent.YELLOW);
    }

    // ==================== SLIDER HANDLING ====================

    private void updateSliderFromMouse(StatSlider stat, int mouseX, int sliderX, int sliderWidth) {
        float ratio = Math.max(0, Math.min(1, (float) (mouseX - sliderX) / sliderWidth));
        float value = stat.min + ratio * (stat.max - stat.min);
        value = Math.round(value * 20) / 20f;
        stat.value = value;
        if (stat.inputField != null) {
            stat.inputField.setValue(formatFloat(value));
        }
    }

    private void reset() {
        // Reset stats - clear existing sliders first to avoid duplicates
        statSliders.clear();
        initStatSliders();
        for (StatSlider stat : statSliders) {
            if (stat.inputField != null) {
                stat.inputField.setValue(formatFloat(stat.value));
            }
        }

        // Reset enchantments
        loadEnchantments();

        // Reset durability
        loadDurabilityValues();

        // Reset attributes
        loadAttributes();

        showStatus("Reset to original values", UIConstants.Accent.YELLOW);
    }

    private void apply() {
        // Apply stats (body part multipliers) - guard against missing sliders
        if (statSliders.size() < 5) {
            showStatus("Error: Stats not initialized", UIConstants.Status.ERROR);
            return;
        }
        float head = statSliders.get(0).value;
        float body = statSliders.get(1).value;
        float legs = statSliders.get(2).value;
        float pen = statSliders.get(3).value;
        float bonus = statSliders.get(4).value;

        PacketDistributor.sendToServer(new UpdateWeaponPayload(editGlobal, head, body, legs, pen, bonus, ""));

        // Build item modification payload
        int durability = 0;
        try {
            durability = Integer.parseInt(durabilityField.getValue());
        } catch (NumberFormatException ignored) {}

        int repairCost = 0;
        try {
            repairCost = Integer.parseInt(repairCostField.getValue());
        } catch (NumberFormatException ignored) {}

        // Build enchantment changes list
        List<String> enchantmentChanges = new ArrayList<>();
        for (EnchantmentEntry entry : enchantments) {
            String enchantId = entry.enchantment.unwrapKey()
                .map(key -> key.location().toString())
                .orElse("");
            if (!enchantId.isEmpty()) {
                if (entry.toRemove) {
                    // Level 0 = remove
                    enchantmentChanges.add(enchantId + ":0");
                } else {
                    enchantmentChanges.add(enchantId + ":" + entry.level);
                }
            }
        }

        // Build attribute changes list
        List<String> attributeChanges = new ArrayList<>();
        for (AttributeEntry attr : attributes) {
            String attrId = attr.attribute.unwrapKey()
                .map(key -> key.location().toString())
                .orElse("");
            if (!attrId.isEmpty()) {
                int opCode = switch (attr.operation) {
                    case ADD_VALUE -> 0;
                    case ADD_MULTIPLIED_BASE -> 1;
                    case ADD_MULTIPLIED_TOTAL -> 2;
                };
                attributeChanges.add(attrId + ":" + attr.value + ":" + opCode);
            }
        }

        // Send item modification packet
        PacketDistributor.sendToServer(new ModifyItemPayload(
            durability,
            isUnbreakable,
            repairCost,
            enchantmentChanges,
            attributeChanges
        ));

        // Log to history
        String itemName = stack.getHoverName().getString();
        StringBuilder details = new StringBuilder();
        if (!enchantmentChanges.isEmpty()) details.append("Enchants:").append(enchantmentChanges.size()).append(" ");
        if (!attributeChanges.isEmpty()) details.append("Attrs:").append(attributeChanges.size()).append(" ");
        if (isUnbreakable) details.append("Unbreakable ");
        ItemEditorDataManager.INSTANCE.addHistoryEntry("Applied changes", itemName, details.toString().trim());

        showStatus("Changes applied!", UIConstants.Accent.GREEN);
    }

    /**
     * Export current configuration to clipboard as JSON.
     */
    private void exportConfigToClipboard() {
        try {
            ItemEditorDataManager.ItemConfigExport config = new ItemEditorDataManager.ItemConfigExport();

            // Basic item info
            config.itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            config.itemName = stack.getHoverName().getString();

            // Stats
            config.stats = new ArrayList<>();
            for (StatSlider stat : statSliders) {
                config.stats.add(stat.value);
            }

            // Enchantments
            config.enchantments = new ArrayList<>();
            for (EnchantmentEntry entry : enchantments) {
                if (!entry.toRemove) {
                    String enchId = entry.enchantment.unwrapKey().map(k -> k.location().toString()).orElse("");
                    if (!enchId.isEmpty()) {
                        config.enchantments.add(new ItemEditorDataManager.EnchantData(enchId, entry.level));
                    }
                }
            }

            // Attributes
            config.attributes = new ArrayList<>();
            for (AttributeEntry attr : attributes) {
                String attrId = attr.attribute.unwrapKey().map(k -> k.location().toString()).orElse("");
                if (!attrId.isEmpty()) {
                    int opCode = switch (attr.operation) {
                        case ADD_VALUE -> 0;
                        case ADD_MULTIPLIED_BASE -> 1;
                        case ADD_MULTIPLIED_TOTAL -> 2;
                    };
                    config.attributes.add(new ItemEditorDataManager.AttrData(attrId, attr.value, opCode));
                }
            }

            // Other
            config.unbreakable = isUnbreakable;
            try {
                config.durability = Integer.parseInt(durabilityField.getValue());
            } catch (NumberFormatException ignored) {}
            try {
                config.repairCost = Integer.parseInt(repairCostField.getValue());
            } catch (NumberFormatException ignored) {}

            // Export to clipboard
            String json = ItemEditorDataManager.INSTANCE.exportToJson(config);
            Minecraft.getInstance().keyboardHandler.setClipboard(json);

            showStatus("Config exported to clipboard!", UIConstants.Accent.GREEN);
            ItemEditorDataManager.INSTANCE.addHistoryEntry("Exported config", config.itemName, "to clipboard");
        } catch (Exception e) {
            showStatus("Export failed: " + e.getMessage(), UIConstants.Accent.RED);
        }
    }

    /**
     * Import configuration from clipboard JSON.
     */
    private void importConfigFromClipboard() {
        try {
            String json = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (json == null || json.isEmpty()) {
                showStatus("Clipboard is empty", UIConstants.Accent.YELLOW);
                return;
            }

            ItemEditorDataManager.ItemConfigExport config = ItemEditorDataManager.INSTANCE.importFromJson(json);
            if (config == null) {
                showStatus("Invalid config format", UIConstants.Accent.RED);
                return;
            }

            saveUndoState();

            // Restore stats
            if (config.stats != null) {
                for (int i = 0; i < Math.min(config.stats.size(), statSliders.size()); i++) {
                    statSliders.get(i).value = config.stats.get(i);
                    if (statSliders.get(i).inputField != null) {
                        statSliders.get(i).inputField.setValue(formatFloat(config.stats.get(i)));
                    }
                }
            }

            // Restore enchantments
            if (config.enchantments != null && !config.enchantments.isEmpty()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null) {
                    enchantments.clear();
                    var enchRegistry = mc.level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
                    for (ItemEditorDataManager.EnchantData ed : config.enchantments) {
                        try {
                            var loc = net.minecraft.resources.ResourceLocation.parse(ed.id);
                            var holder = enchRegistry.getHolder(loc);
                            if (holder.isPresent()) {
                                String eName = formatEnchantmentName(holder.get());
                                enchantments.add(new EnchantmentEntry(holder.get(), eName, ed.level));
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }

            // Restore attributes
            if (config.attributes != null && !config.attributes.isEmpty()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null) {
                    attributes.clear();
                    var attrRegistry = mc.level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ATTRIBUTE);
                    for (ItemEditorDataManager.AttrData ad : config.attributes) {
                        try {
                            var loc = net.minecraft.resources.ResourceLocation.parse(ad.id);
                            var holder = attrRegistry.getHolder(loc);
                            if (holder.isPresent()) {
                                String aName = formatAttributeName(holder.get());
                                AttributeModifier.Operation op = switch (ad.operation) {
                                    case 1 -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
                                    case 2 -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
                                    default -> AttributeModifier.Operation.ADD_VALUE;
                                };
                                attributes.add(new AttributeEntry(aName, holder.get(), ad.value, op));
                            }
                        } catch (Exception ignored) {}
                    }
                    rebuildAttributeFields();
                }
            }

            // Restore other settings
            if (config.unbreakable != null) {
                isUnbreakable = config.unbreakable;
            }
            if (config.durability != null && durabilityField != null) {
                durabilityField.setValue(String.valueOf(config.durability));
            }
            if (config.repairCost != null && repairCostField != null) {
                repairCostField.setValue(String.valueOf(config.repairCost));
            }

            showStatus("Config imported from clipboard!", UIConstants.Accent.GREEN);
            ItemEditorDataManager.INSTANCE.addHistoryEntry("Imported config", config.itemName != null ? config.itemName : "unknown", "from clipboard");
        } catch (com.google.gson.JsonParseException e) {
            showStatus("Invalid JSON format", UIConstants.Accent.RED);
        } catch (Exception e) {
            showStatus("Import failed: " + e.getMessage(), UIConstants.Accent.RED);
        }
    }

    private void showStatus(String msg, int color) {
        statusMessage = msg;
        statusColor = color;
        statusTicks = 60;
    }

    private static String formatFloat(float value) {
        if (value == (int) value) return String.valueOf((int) value);
        return String.format("%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String formatDouble(double value) {
        if (value == (int) value) return String.valueOf((int) value);
        return String.format("%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static int lerpColor(int colorA, int colorB, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aA = (colorA >> 24) & 0xFF, rA = (colorA >> 16) & 0xFF, gA = (colorA >> 8) & 0xFF, bA = colorA & 0xFF;
        int aB = (colorB >> 24) & 0xFF, rB = (colorB >> 16) & 0xFF, gB = (colorB >> 8) & 0xFF, bB = colorB & 0xFF;
        return ((int)(aA + (aB - aA) * t) << 24) | ((int)(rA + (rB - rA) * t) << 16) |
               ((int)(gA + (gB - gA) * t) << 8) | (int)(bA + (bB - bA) * t);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Ctrl+Z = Undo
        if (keyCode == 90 && (modifiers & 2) != 0) { // Z with Ctrl
            if ((modifiers & 1) != 0) { // Shift = Redo
                redo();
            } else {
                undo();
            }
            return true;
        }
        // Ctrl+Y = Redo
        if (keyCode == 89 && (modifiers & 2) != 0) {
            redo();
            return true;
        }
        // Ctrl+S = Save preset
        if (keyCode == 83 && (modifiers & 2) != 0) {
            showPresetMenu = !showPresetMenu;
            updateFieldVisibility();
            return true;
        }
        // Escape closes pickers
        if (keyCode == 256) {
            if (showEnchantPicker) {
                showEnchantPicker = false;
                updateFieldVisibility();
                return true;
            }
            if (showAttributePicker) {
                showAttributePicker = false;
                updateFieldVisibility();
                return true;
            }
            if (showPresetMenu) {
                showPresetMenu = false;
                updateFieldVisibility();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        if (statusTicks > 0) statusTicks--;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            mc.options.menuBackgroundBlurriness().set(originalBlurValue);
        }
        super.onClose();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override
    protected void renderBlurredBackground(float pt) {}

    @Override
    protected void renderMenuBackground(GuiGraphics g) {}
}
