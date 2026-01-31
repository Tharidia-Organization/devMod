package com.devmod.client.endurance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;
import com.google.errorprone.annotations.Immutable;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.Enchantment;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.notification.ClientNotificationManager;
import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.EditorStartTab;
import com.devmod.client.ui.editor.ItemEditorScreen;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.search.ItemSearchQuery;
import com.devmod.endurance.CustomKit;
import com.devmod.endurance.KitManager;
import com.devmod.endurance.KitPersistence;
import com.devmod.notification.Notification;
import com.devmod.notification.NotificationCategory;
import com.devmod.notification.NotificationPriority;
import com.devmod.util.I18n;
@OnlyIn(Dist.CLIENT)
public class KitSelectionScreen extends Screen {
    private static final Splitter UNDERSCORE_SPLITTER = Splitter.on('_');
    private static final Logger LOGGER = LoggerFactory.getLogger(KitSelectionScreen.class);

    // Layout
    private static final int ITEM_SIZE = 18;
    private static final int ITEM_MARGIN = 2;
    private static final int PANEL_PADDING = 12;
    private static final int TAB_HEIGHT = 28;
    private static final int SLOT_SIZE = 22;
    private static final int LABEL_LINE_HEIGHT = 14;
    private static final int SECTION_DIVIDER_SPACING = 12;
    private static final int ACTION_BUTTON_HEIGHT = 18;
    private static final int ACTION_BUTTON_GAP = 6;

    // Colors - Using DesignTokens for consistency
    private static final int COLOR_BG = DesignTokens.Bg.LEVEL_0;
    private static final int COLOR_PANEL = DesignTokens.Bg.LEVEL_1;
    private static final int COLOR_PANEL_HEADER = DesignTokens.Surface.LEVEL_1;
    private static final int COLOR_ITEM_BG = DesignTokens.Bg.LEVEL_0;
    private static final int COLOR_ITEM_HOVER = DesignTokens.Surface.LEVEL_2;
    private static final int COLOR_SLOT_EMPTY = DesignTokens.Surface.LEVEL_1;
    private static final int COLOR_SLOT_FILLED = DesignTokens.Surface.LEVEL_2;
    private static final int COLOR_SLOT_SELECTED = DesignTokens.Accent.PRIMARY;
    private static final int COLOR_ACCENT = DesignTokens.Accent.SECONDARY;
    private static final int COLOR_ACCENT_GREEN = DesignTokens.Semantic.SUCCESS;
    private static final int COLOR_ACCENT_ORANGE = DesignTokens.Semantic.WARNING;
    private static final int COLOR_ACCENT_PURPLE = EnduranceUiTheme.KitSelection.ACCENT_PURPLE;  // Kit-specific purple (no token equivalent)
    private static final int COLOR_TEXT = DesignTokens.Text.PRIMARY;
    private static final int COLOR_TEXT_DIM = DesignTokens.Text.MUTED;
    private static final int COLOR_TEXT_INVERSE = DesignTokens.Text.INVERSE;
    private static final int COLOR_TEXT_WHITE = DesignTokens.Text.WHITE;
    private static final int COLOR_BORDER = DesignTokens.Stroke.DEFAULT;

    // Button success variants
    private static final int COLOR_BTN_SUCCESS_HOVER = EnduranceUiTheme.KitSelection.BTN_SUCCESS_HOVER;
    private static final int COLOR_BTN_SUCCESS_BORDER_HOVER = EnduranceUiTheme.KitSelection.BTN_SUCCESS_BORDER_HOVER;
    private static final int COLOR_BTN_SUCCESS_BORDER = EnduranceUiTheme.KitSelection.BTN_SUCCESS_BORDER;

    @Immutable
    @FunctionalInterface
    private interface ItemStackFilter extends Predicate<ItemStack> {}

    private record ItemGridLayout(int panelX, int panelY, int panelW, int panelH,
                                  int gridX, int gridY, int gridW, int gridH) {}

    private record KitPanelLayout(int panelX, int panelY, int panelW, int panelH, int contentX,
                                  int equipmentLabelY, int equipmentRowY,
                                  int hotbarLabelY, int hotbarRowY,
                                  int actionsDividerY, int actionsLabelY, int actionsRowY,
                                  int presetsDividerY, int presetsLabelY, int presetsRowY,
                                  int instructionsDividerY, int instructionsStartY) {}

    private enum ActionType {
        EDIT,
        ENCHANT,
        REMOVE
    }

    private record ActionButton(ActionType type, String label, int x, int y, int w, int h, boolean enabled) {}

    // Categories with icons
    private enum Category {
        ALL("devmod.kit.category.all", "devmod.kit.category.all.tab", "◆", EnduranceUiTheme.KitCategory.ALL, stack -> true),
        ARMOR("devmod.kit.category.armor", "devmod.kit.category.armor.tab", "🛡", EnduranceUiTheme.KitCategory.ARMOR,
            KitSelectionScreen::isArmorEquipable),
        WEAPONS("devmod.kit.category.weapons", "devmod.kit.category.weapons.tab", "⚔", EnduranceUiTheme.KitCategory.WEAPONS,
            stack -> stack.getItem() instanceof SwordItem ||
                                          stack.getItem() instanceof AxeItem ||
                                          stack.getItem() instanceof BowItem ||
                                          stack.getItem() instanceof CrossbowItem ||
                                          stack.getItem() instanceof TridentItem ||
                                          stack.getItem() instanceof MaceItem),
        TOOLS("devmod.kit.category.tools", "devmod.kit.category.tools.tab", "⛏", EnduranceUiTheme.KitCategory.TOOLS,
            stack -> (stack.getItem() instanceof TieredItem) &&
                                       !(stack.getItem() instanceof SwordItem) &&
                                       !(stack.getItem() instanceof AxeItem)),
        POTIONS("devmod.kit.category.potions", "devmod.kit.category.potions.tab", "🧪", EnduranceUiTheme.KitCategory.POTIONS,
            stack -> stack.getItem() instanceof PotionItem ||
                                          stack.getItem() == Items.SPLASH_POTION ||
                                          stack.getItem() == Items.LINGERING_POTION),
        FOOD("devmod.kit.category.food", "devmod.kit.category.food.tab", "🍖", EnduranceUiTheme.KitCategory.FOOD,
            stack -> stack.has(Objects.requireNonNull(net.minecraft.core.component.DataComponents.FOOD))),
        COMBAT("devmod.kit.category.combat", "devmod.kit.category.combat.tab", "🏹", EnduranceUiTheme.KitCategory.COMBAT,
            stack -> isOffhandEquipable(stack) ||
                                         stack.getItem() == Items.ARROW ||
                                         stack.getItem() == Items.SPECTRAL_ARROW ||
                                         stack.getItem() == Items.TIPPED_ARROW ||
                                         stack.getItem() == Items.TOTEM_OF_UNDYING ||
                                         stack.getItem() == Items.GOLDEN_APPLE ||
                                         stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE ||
                                         stack.getItem() == Items.ENDER_PEARL ||
                                         stack.getItem() == Items.FIREWORK_ROCKET),
        BLOCKS("devmod.kit.category.blocks", "devmod.kit.category.blocks.tab", "▣", EnduranceUiTheme.KitCategory.BLOCKS,
            stack -> stack.getItem() instanceof BlockItem);

        final String nameKey;
        final String tabKey;
        final String icon;
        final int color;
        final ItemStackFilter filter;

        Category(String nameKey, String tabKey, String icon, int color, ItemStackFilter filter) {
            this.nameKey = nameKey;
            this.tabKey = tabKey;
            this.icon = icon;
            this.color = color;
            this.filter = filter;
        }

        boolean matches(ItemStack stack) {
            return filter.test(stack);
        }

        String displayName() {
            return I18n.translate(nameKey).getString();
        }

        String tabLabel() {
            return icon + " " + I18n.translate(tabKey).getString();
        }
    }

    // State
    private final List<ItemStack> allItems = new ArrayList<>();
    private final List<ItemStack> filteredItems = new ArrayList<>();
    private final Map<Integer, ItemStack> kitSlots = new LinkedHashMap<>();
    private Category selectedCategory = Category.ALL;
    private String searchQuery = "";
    private int itemScrollOffset = 0;
    private int itemMaxScroll = 0;
    private int kitLowerScrollOffset = 0;
    private int kitLowerMaxScroll = 0;
    private int selectedSlot = -1;
    private boolean needsSearchTabRefresh = false;
    private int searchTabRetryTicks = 0;
    private boolean initialKitLoad = false;

    // Scaled layout dimensions (updated each frame for responsiveness)
    private int scaledItemSize;
    private int scaledItemMargin;
    private int scaledPanelPadding;
    private int scaledTabHeight;
    private int scaledSlotSize;
    private int scaledLabelLineHeight;
    private int scaledSectionDividerSpacing;
    private int scaledActionButtonHeight;
    private int scaledActionButtonGap;
    private int scaledTextLineHeight;

    // Popup state
    private boolean showEnchantPopup = false;
    private int enchantSlot = -1;
    private List<EnchantmentOption> availableEnchants = new ArrayList<>();
    private int enchantScrollOffset = 0;

    private boolean showNameDialog = false;
    private String kitNameInput = "";
    @Nullable
    private EditBox kitNameBox;
    @Nullable
    private EditBox searchBox;

    // Slot configuration
    private static final String[] SLOT_ICONS = {
        "⛑", "🛡", "👖", "👢", "🤚",
        "①", "②", "③", "④", "⑤", "⑥", "⑦", "⑧", "⑨"
    };

    // UI Components
    @Nullable
    private final Consumer<List<ItemStack>> onKitSelected;
    @Nullable
    private final Consumer<CustomKit> onKitSaved;
    @Nullable
    private final Screen parentScreen;
    @Nullable
    private CustomKit editingKit;

    public KitSelectionScreen(@Nullable Screen parent, @Nullable Consumer<List<ItemStack>> onSelect) {
        this(parent, onSelect, null, null);
    }

    public KitSelectionScreen(@Nullable Screen parent, @Nullable Consumer<List<ItemStack>> onSelect, @Nullable CustomKit existingKit) {
        this(parent, onSelect, null, existingKit);
    }

    public KitSelectionScreen(@Nullable Screen parent, @Nullable Consumer<List<ItemStack>> onSelect,
                              @Nullable Consumer<CustomKit> onSave, @Nullable CustomKit existingKit) {
        super(I18n.screenTitle("kit_selection"));
        this.parentScreen = parent;
        this.onKitSelected = onSelect;
        this.onKitSaved = onSave;
        this.editingKit = existingKit;
    }

    @Override
    protected void init() {
        super.init();

        // Initialize scaled dimensions before any calculations that depend on them
        UIScaleManager.update();
        scaledItemSize = UIScaleManager.scale(ITEM_SIZE);
        scaledItemMargin = UIScaleManager.scale(ITEM_MARGIN);
        scaledPanelPadding = UIScaleManager.scale(PANEL_PADDING);
        scaledTabHeight = UIScaleManager.scale(TAB_HEIGHT);
        scaledSlotSize = UIScaleManager.scale(SLOT_SIZE);
        scaledLabelLineHeight = Math.max(UIScaleManager.getScaledLineHeight(), UIScaleManager.scale(LABEL_LINE_HEIGHT));
        scaledSectionDividerSpacing = UIScaleManager.scale(SECTION_DIVIDER_SPACING);
        scaledActionButtonHeight = UIScaleManager.scale(ACTION_BUTTON_HEIGHT);
        scaledActionButtonGap = UIScaleManager.scale(ACTION_BUTTON_GAP);
        scaledTextLineHeight = UIScaleManager.getScaledLineHeight();

        loadAllItems();
        if (!initialKitLoad) {
            loadExistingKit();
            initialKitLoad = true;
        }
        initSearchBox();
        filterItems();
    }

    @Override
    public void tick() {
        super.tick();
        if (!needsSearchTabRefresh) {
            return;
        }
        searchTabRetryTicks++;
        if (searchTabRetryTicks % 10 != 0) {
            return;
        }
        if (isSearchTabReady()) {
            loadAllItems();
            filterItems();
            needsSearchTabRefresh = false;
        } else if (searchTabRetryTicks > 200) {
            needsSearchTabRefresh = false;
        }
    }

    private void loadExistingKit() {
        kitSlots.clear();
        CustomKit kit = editingKit;
        if (kit != null) {
            // Use full restoration to preserve attributes, durability, NBT, etc.
            var mc = Minecraft.getInstance();
            var registryAccess = mc.level != null ? mc.level.registryAccess() : null;
            List<ItemStack> items = kit.toItemStacks(registryAccess);
            populateKitSlots(items);
            kitNameInput = kit.getName();
            return;
        }

        if (KitManager.INSTANCE.hasTemporaryKit()) {
            List<ItemStack> items = KitManager.INSTANCE.getTemporaryKitItems();
            populateKitSlots(items);
            String tempName = KitManager.INSTANCE.getTemporaryKitName();
            if (tempName != null && !tempName.isBlank()) {
                kitNameInput = tempName;
            }
            return;
        }

        kitNameInput = "";
    }

    private void populateKitSlots(List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        int slot = 5;
        for (ItemStack stack : items) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            int equipSlot = getKitSlotIndex(stack);
            if (equipSlot >= 0 && !kitSlots.containsKey(equipSlot)) {
                kitSlots.put(equipSlot, stack.copy());
                continue;
            }
            while (slot <= 13 && kitSlots.containsKey(slot)) slot++;
            if (slot <= 13) {
                kitSlots.put(slot, stack.copy());
                slot++;
            }
        }
    }

    private void initSearchBox() {
        var safeFont = Objects.requireNonNull(font);
        int searchWidth = UIScaleManager.scale(200);
        int searchX = UIScaleManager.scale(PANEL_PADDING + 8);
        int searchY = UIScaleManager.scale(TAB_HEIGHT + PANEL_PADDING + 36);
        final EditBox box = new EditBox(safeFont, searchX, searchY, searchWidth, 18, I18n.ui("search"));
        // Hint shows search syntax: @mod for namespace, #tag for tags, $text for tooltip
        box.setHint(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("Search... @mod #tag $tooltip")));
        box.setBordered(false);
        box.setTextColor(DesignTokens.Text.PRIMARY());
        box.setTextColorUneditable(DesignTokens.Text.MUTED());
        box.setResponder(query -> {
            searchQuery = query;
            filterItems();
        });
        addRenderableWidget(box);
        searchBox = box;
    }

    private boolean isSearchTabReady() {
        var mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        try {
            return !CreativeModeTabs.searchTab().getDisplayItems().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private void loadAllItems() {
        allItems.clear();
        Map<Integer, List<ItemStack>> buckets = new HashMap<>();
        boolean loadedFromTabs = false;

        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level != null) {
            try {
                // Try to get items from the search tab (already populated by game)
                for (ItemStack stack : CreativeModeTabs.searchTab().getDisplayItems()) {
                    addItemIfUnique(buckets, allItems, stack);
                }
                loadedFromTabs = !allItems.isEmpty();
            } catch (Exception e) {
                LOGGER.warn("[KitSelectionScreen] Failed to load creative tab items; falling back to registry list.", e);
            }
        }

        for (Item item : BuiltInRegistries.ITEM) {
            if (item != Items.AIR) {
                addItemIfUnique(buckets, allItems, Objects.requireNonNull(item.getDefaultInstance()));
            }
        }

        needsSearchTabRefresh = level != null && !loadedFromTabs;
        searchTabRetryTicks = 0;

        if (level != null) {
            appendPotionVariants(buckets, level.registryAccess());
        }

        if (loadedFromTabs) {
            LOGGER.debug("[KitSelectionScreen] Loaded {} items (creative tabs + registry fallback)", allItems.size());
        } else {
            LOGGER.debug("[KitSelectionScreen] Loaded {} items (registry only)", allItems.size());
        }
    }

    private void appendPotionVariants(Map<Integer, List<ItemStack>> buckets, net.minecraft.core.RegistryAccess registryAccess) {
        if (registryAccess == null) {
            return;
        }

        var potionRegistry = registryAccess.registryOrThrow(Objects.requireNonNull(Registries.POTION));
        for (Holder<net.minecraft.world.item.alchemy.Potion> holder : potionRegistry.holders().toList()) {
            Holder<net.minecraft.world.item.alchemy.Potion> safeHolder = Objects.requireNonNull(holder);
            addPotionVariant(buckets, Items.POTION, safeHolder);
            addPotionVariant(buckets, Items.SPLASH_POTION, safeHolder);
            addPotionVariant(buckets, Items.LINGERING_POTION, safeHolder);
            addPotionVariant(buckets, Items.TIPPED_ARROW, safeHolder);
        }
    }

    private void addPotionVariant(Map<Integer, List<ItemStack>> buckets, Item baseItem,
                                  Holder<net.minecraft.world.item.alchemy.Potion> potion) {
        if (baseItem == null || baseItem == Items.AIR) {
            return;
        }
        ItemStack stack = new ItemStack(baseItem);
        stack.set(Objects.requireNonNull(net.minecraft.core.component.DataComponents.POTION_CONTENTS),
            new net.minecraft.world.item.alchemy.PotionContents(Objects.requireNonNull(potion)));
        addItemIfUnique(buckets, allItems, stack);
    }

    private void filterItems() {
        filteredItems.clear();

        // Parse query with prefix support (@mod, #tag, $tooltip)
        ItemSearchQuery parsedQuery = ItemSearchQuery.parse(Objects.requireNonNull(searchQuery));

        for (ItemStack stack : allItems) {
            ItemStack safeStack = Objects.requireNonNull(stack);
            if (!selectedCategory.matches(safeStack)) continue;
            if (!parsedQuery.matches(safeStack)) continue;
            filteredItems.add(safeStack);
        }

        itemScrollOffset = 0;
        calculateMaxScroll();
    }

    private void calculateMaxScroll() {
        ItemGridLayout layout = getItemGridLayout();
        int itemsPerRow = Math.max(1, layout.gridW() / (scaledItemSize + scaledItemMargin));
        int rows = (filteredItems.size() + itemsPerRow - 1) / itemsPerRow;
        int contentHeight = rows * (scaledItemSize + scaledItemMargin);
        int viewportHeight = layout.gridH();
        itemMaxScroll = Math.max(0, contentHeight - viewportHeight);
    }

    private ItemGridLayout getItemGridLayout() {
        int panelX = scaledPanelPadding;
        int panelY = scaledTabHeight + scaledPanelPadding;
        int panelW = (width / 2) - scaledPanelPadding * 2;
        int panelH = height - scaledTabHeight - scaledPanelPadding * 2 - UIScaleManager.scale(55);
        int headerH = UIScaleManager.scale(28);
        int searchH = searchBox != null ? searchBox.getHeight() : 18;
        int searchY = panelY + headerH + UIScaleManager.scale(6);
        int gridX = panelX + UIScaleManager.scale(8);
        int gridY = searchY + searchH + UIScaleManager.scale(10);
        int gridW = panelW - UIScaleManager.scale(20);
        int gridH = Math.max(0, (panelY + panelH) - gridY - UIScaleManager.scale(10));
        return new ItemGridLayout(panelX, panelY, panelW, panelH, gridX, gridY, gridW, gridH);
    }

    private KitPanelLayout getKitPanelLayout() {
        int panelX = width / 2 + scaledPanelPadding;
        int panelY = scaledTabHeight + scaledPanelPadding;
        int panelW = (width / 2) - scaledPanelPadding * 2;
        int panelH = height - scaledTabHeight - scaledPanelPadding * 2 - UIScaleManager.scale(55);
        int contentX = panelX + UIScaleManager.scale(12);

        int headerH = UIScaleManager.scale(28);
        int y = panelY + headerH + UIScaleManager.scale(12);

        int equipmentLabelY = y;
        int equipmentRowY = equipmentLabelY + scaledLabelLineHeight;
        y = equipmentRowY + scaledSlotSize + UIScaleManager.scale(16);

        int hotbarLabelY = y;
        int hotbarRowY = hotbarLabelY + scaledLabelLineHeight;
        y = hotbarRowY + scaledSlotSize + scaledSectionDividerSpacing;

        int actionsDividerY = y;
        y += scaledSectionDividerSpacing;

        int actionsLabelY = y;
        int actionsRowY = actionsLabelY + scaledLabelLineHeight;
        y = actionsRowY + scaledActionButtonHeight + scaledSectionDividerSpacing;

        int presetsDividerY = y;
        y += scaledSectionDividerSpacing;

        int presetsLabelY = y;
        int presetsRowY = presetsLabelY + scaledLabelLineHeight;
        y = presetsRowY + scaledActionButtonHeight + scaledSectionDividerSpacing;

        int instructionsDividerY = y;
        int instructionsStartY = instructionsDividerY + scaledSectionDividerSpacing;

        return new KitPanelLayout(panelX, panelY, panelW, panelH, contentX,
            equipmentLabelY, equipmentRowY,
            hotbarLabelY, hotbarRowY,
            actionsDividerY, actionsLabelY, actionsRowY,
            presetsDividerY, presetsLabelY, presetsRowY,
            instructionsDividerY, instructionsStartY);
    }

    private int getKitLowerStartY(KitPanelLayout layout) {
        return layout.actionsDividerY() + 1;
    }

    private int getKitLowerEndY(KitPanelLayout layout) {
        return layout.panelY() + layout.panelH() - 1;
    }

    private void updateKitLowerScroll(KitPanelLayout layout) {
        int lowerStartY = getKitLowerStartY(layout);
        int lowerEndY = getKitLowerEndY(layout);
        int lowerViewportH = Math.max(0, lowerEndY - lowerStartY);

        int instructionHeight = getInstructionLines().length * scaledTextLineHeight;
        int lowerContentHeight = (layout.instructionsStartY() + instructionHeight) - layout.actionsLabelY();

        kitLowerMaxScroll = Math.max(0, lowerContentHeight - lowerViewportH);
        if (kitLowerScrollOffset > kitLowerMaxScroll) {
            kitLowerScrollOffset = kitLowerMaxScroll;
        }
    }

    private boolean isMouseInKitLowerRegion(int mouseX, int mouseY, KitPanelLayout layout) {
        int lowerStartY = getKitLowerStartY(layout);
        int lowerEndY = getKitLowerEndY(layout);
        return mouseX >= layout.panelX() && mouseX < layout.panelX() + layout.panelW() &&
            mouseY >= lowerStartY && mouseY < lowerEndY;
    }

    private static void addItemIfUnique(Map<Integer, List<ItemStack>> buckets, List<ItemStack> target, ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() == Items.AIR) {
            return;
        }
        int hash = ItemStack.hashItemAndComponents(stack);
        List<ItemStack> bucket = buckets.computeIfAbsent(hash, key -> new ArrayList<>());
        for (ItemStack existing : bucket) {
            if (existing != null && ItemStack.isSameItemSameComponents(existing, stack)) {
                return;
            }
        }
        ItemStack copy = stack.copy();
        bucket.add(copy);
        target.add(copy);
    }

    @Nullable
    private static EquipmentSlot getEquipSlot(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Equipable equipable = Equipable.get(stack);
        return equipable != null ? equipable.getEquipmentSlot() : null;
    }

    private static boolean isArmorEquipable(ItemStack stack) {
        EquipmentSlot slot = getEquipSlot(stack);
        return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST ||
            slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
    }

    private static boolean isOffhandEquipable(ItemStack stack) {
        return getEquipSlot(stack) == EquipmentSlot.OFFHAND;
    }

    private int getKitSlotIndex(ItemStack stack) {
        EquipmentSlot slot = getEquipSlot(stack);
        if (slot == null) {
            return -1;
        }
        return switch (slot) {
            case HEAD -> 0;
            case CHEST -> 1;
            case LEGS -> 2;
            case FEET -> 3;
            case OFFHAND -> 4;
            default -> -1;
        };
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();

        // Update scaled dimensions for responsiveness
        scaledItemSize = UIScaleManager.scale(ITEM_SIZE);
        scaledItemMargin = UIScaleManager.scale(ITEM_MARGIN);
        scaledPanelPadding = UIScaleManager.scale(PANEL_PADDING);
        scaledTabHeight = UIScaleManager.scale(TAB_HEIGHT);
        scaledSlotSize = UIScaleManager.scale(SLOT_SIZE);
        scaledLabelLineHeight = UIScaleManager.scale(LABEL_LINE_HEIGHT);
        scaledSectionDividerSpacing = UIScaleManager.scale(SECTION_DIVIDER_SPACING);
        scaledActionButtonHeight = UIScaleManager.scale(ACTION_BUTTON_HEIGHT);
        scaledActionButtonGap = UIScaleManager.scale(ACTION_BUTTON_GAP);

        // Dark background
        graphics.fill(0, 0, width, height, COLOR_BG);

        updateSearchBoxLayout();
        renderHeader(graphics, mouseX, mouseY);
        renderItemBrowser(graphics, mouseX, mouseY);
        renderKitPanel(graphics, mouseX, mouseY);
        renderBottomBar(graphics, mouseX, mouseY);

        renderInputBackgrounds(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);

        // Render popups last (on top)
        if (showEnchantPopup) {
            renderModalLayer(graphics, () -> renderEnchantPopup(graphics, mouseX, mouseY));
        }
        if (showNameDialog) {
            renderModalLayer(graphics, () -> renderNameDialog(graphics, mouseX, mouseY));
        }

        // Tooltips (after popups)
        if (!showEnchantPopup && !showNameDialog) {
            renderTooltips(graphics, mouseX, mouseY);
        }
    }

    private void renderInputBackgrounds(GuiGraphics graphics) {
        var box = searchBox;
        if (box != null) {
            AxiomRenderer.drawInputBackground(graphics, box.getX(), box.getY(), box.getWidth(),
                box.getHeight(), box.isFocused());
        }
    }

    private void updateSearchBoxLayout() {
        var box = searchBox;
        if (box == null) {
            return;
        }
        int panelX = scaledPanelPadding;
        int panelY = scaledTabHeight + scaledPanelPadding;
        int panelW = (width / 2) - scaledPanelPadding * 2;
        int headerH = UIScaleManager.scale(28);
        int searchX = panelX + UIScaleManager.scale(10);
        int searchY = panelY + headerH + UIScaleManager.scale(6);
        int searchW = panelW - UIScaleManager.scale(20);
        box.setX(searchX);
        box.setY(searchY);
        box.setWidth(searchW);
    }

    private void drawScaledText(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                String text, int x, int y, int color) {
        UIScaleManager.drawScaledString(graphics, font, text, x, y, color, false);
    }

    private void renderHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        var safeFont = Objects.requireNonNull(font);

        // Header background
        graphics.fill(0, 0, width, scaledTabHeight, COLOR_PANEL_HEADER);
        graphics.fill(0, scaledTabHeight - 1, width, scaledTabHeight, COLOR_BORDER);

        // Title
        drawScaledText(graphics, safeFont, I18n.translate("devmod.kit.header.title").getString(),
            scaledPanelPadding, UIScaleManager.scale(10), COLOR_TEXT);

        // Category tabs
        int tabX = UIScaleManager.scale(120);
        int tabW = UIScaleManager.scale(80);
        int tabMargin = UIScaleManager.scale(4);
        for (Category cat : Category.values()) {
            boolean selected = cat == selectedCategory;
            boolean hovered = mouseX >= tabX && mouseX < tabX + tabW && mouseY >= tabMargin && mouseY < scaledTabHeight - tabMargin;

            int tabColor = selected ? cat.color : (hovered ? COLOR_ITEM_HOVER : COLOR_PANEL);
            graphics.fill(tabX, tabMargin, tabX + tabW, scaledTabHeight - tabMargin, tabColor);

            if (selected) {
                // Bottom accent line
                graphics.fill(tabX, scaledTabHeight - tabMargin, tabX + tabW, scaledTabHeight - UIScaleManager.scale(2), cat.color);
            }

            String label = Objects.requireNonNull(cat.tabLabel());
            int textW = UIScaleManager.getScaledStringWidth(safeFont, label);
            int textX = tabX + (tabW - textW) / 2;
            int textY = tabMargin + (scaledTabHeight - tabMargin * 2 - scaledTextLineHeight) / 2;
            drawScaledText(graphics, safeFont, label, textX, textY, selected ? COLOR_TEXT_INVERSE : COLOR_TEXT);

            tabX += tabW + 4;
        }
    }

    private void renderItemBrowser(GuiGraphics graphics, int mouseX, int mouseY) {
        var safeFont = Objects.requireNonNull(font);

        ItemGridLayout layout = getItemGridLayout();
        int panelX = layout.panelX();
        int panelY = layout.panelY();
        int panelW = layout.panelW();
        int panelH = layout.panelH();

        // Panel background
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, COLOR_PANEL);
        renderPanelBorder(graphics, panelX, panelY, panelW, panelH);

        // Panel header
        int headerHeight = UIScaleManager.scale(28);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + headerHeight, COLOR_PANEL_HEADER);
        String headerText = I18n.translate("devmod.kit.header.items",
            selectedCategory.displayName(), filteredItems.size()).getString();
        drawScaledText(graphics, safeFont, headerText, panelX + UIScaleManager.scale(10),
            panelY + UIScaleManager.scale(10), selectedCategory.color);

        // Item grid
        int gridX = layout.gridX();
        int gridY = layout.gridY();
        int gridW = layout.gridW();
        int gridH = layout.gridH();

        graphics.enableScissor(gridX, gridY, gridX + gridW, gridY + gridH);

        int itemsPerRow = Math.max(1, gridW / (scaledItemSize + scaledItemMargin));
        int y = gridY - itemScrollOffset;

        if (filteredItems.isEmpty()) {
            String emptyLabel = Objects.requireNonNull(I18n.ui("no_results").getString());
            int textW = UIScaleManager.getScaledStringWidth(safeFont, emptyLabel);
            int textX = gridX + (gridW - textW) / 2;
            int textY = gridY + gridH / 2 - (scaledTextLineHeight / 2);
            drawScaledText(graphics, safeFont, emptyLabel, textX, textY, COLOR_TEXT_DIM);
        } else {
            for (int i = 0; i < filteredItems.size(); i++) {
                int col = i % itemsPerRow;
                int row = i / itemsPerRow;
                int itemX = gridX + col * (scaledItemSize + scaledItemMargin);
                int itemY = y + row * (scaledItemSize + scaledItemMargin);

                if (itemY + scaledItemSize < gridY || itemY > gridY + gridH) continue;

                ItemStack stack = Objects.requireNonNull(filteredItems.get(i));
                boolean hovered = mouseX >= itemX && mouseX < itemX + scaledItemSize &&
                                  mouseY >= itemY && mouseY < itemY + scaledItemSize;

                int bgColor = hovered ? COLOR_ITEM_HOVER : COLOR_ITEM_BG;
                graphics.fill(itemX, itemY, itemX + scaledItemSize, itemY + scaledItemSize, bgColor);

                if (hovered) {
                    // Hover border
                    graphics.fill(itemX, itemY, itemX + scaledItemSize, itemY + 1, COLOR_ACCENT);
                    graphics.fill(itemX, itemY + scaledItemSize - 1, itemX + scaledItemSize, itemY + scaledItemSize, COLOR_ACCENT);
                    graphics.fill(itemX, itemY, itemX + 1, itemY + scaledItemSize, COLOR_ACCENT);
                    graphics.fill(itemX + scaledItemSize - 1, itemY, itemX + scaledItemSize, itemY + scaledItemSize, COLOR_ACCENT);
                }

                graphics.renderItem(stack, itemX + 1, itemY + 1);
            }
        }

        graphics.disableScissor();

        // Scrollbar
        if (itemMaxScroll > 0) {
            int sbX = panelX + panelW - UIScaleManager.scale(8);
            int sbH = Math.max(UIScaleManager.scale(20), (int) ((float) gridH / (gridH + itemMaxScroll) * gridH));
            int sbY = gridY + (int) ((float) itemScrollOffset / itemMaxScroll * (gridH - sbH));
            graphics.fill(sbX, gridY, sbX + UIScaleManager.scale(4), gridY + gridH, COLOR_ITEM_BG);
            graphics.fill(sbX, sbY, sbX + UIScaleManager.scale(4), sbY + sbH, COLOR_ACCENT);
        }
    }

    private void renderKitPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        var safeFont = Objects.requireNonNull(font);

        KitPanelLayout layout = getKitPanelLayout();
        int panelX = layout.panelX();
        int panelY = layout.panelY();
        int panelW = layout.panelW();
        int panelH = layout.panelH();
        int contentX = layout.contentX();
        updateKitLowerScroll(layout);

        // Panel background
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, COLOR_PANEL);
        renderPanelBorder(graphics, panelX, panelY, panelW, panelH);

        // Panel header
        int headerHeight = UIScaleManager.scale(28);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + headerHeight, COLOR_PANEL_HEADER);
        int itemCount = (int) kitSlots.values().stream().filter(s -> !s.isEmpty()).count();
        String headerText = I18n.translate("devmod.kit.header.kit", itemCount).getString();
        drawScaledText(graphics, safeFont, headerText, panelX + UIScaleManager.scale(10),
            panelY + UIScaleManager.scale(10), COLOR_ACCENT_ORANGE);

        // === CHARACTER PREVIEW ===
        int previewX = panelX + panelW - UIScaleManager.scale(80);
        int previewY = layout.equipmentLabelY() + UIScaleManager.scale(80);
        renderCharacterPreview(graphics, previewX, previewY, mouseX, mouseY);

        // === EQUIPMENT SLOTS ===
        drawScaledText(graphics, safeFont, I18n.translate("devmod.kit.section.equipment").getString(),
            contentX, layout.equipmentLabelY(), COLOR_TEXT_DIM);

        // Armor slots (vertical)
        int slotGap = UIScaleManager.scale(6);
        for (int slot = 0; slot < 4; slot++) {
            renderSlot(graphics, contentX + slot * (scaledSlotSize + slotGap), layout.equipmentRowY(), slot, mouseX, mouseY);
        }

        // Offhand
        renderSlot(graphics, contentX + 4 * (scaledSlotSize + slotGap) + UIScaleManager.scale(10), layout.equipmentRowY(), 4, mouseX, mouseY);

        // === HOTBAR SLOTS ===
        drawScaledText(graphics, safeFont, I18n.translate("devmod.kit.section.hotbar").getString(),
            contentX, layout.hotbarLabelY(), COLOR_TEXT_DIM);

        int hotbarSlotGap = UIScaleManager.scale(4);
        for (int i = 0; i < 9; i++) {
            int slotX = contentX + i * (scaledSlotSize + hotbarSlotGap);
            renderSlot(graphics, slotX, layout.hotbarRowY(), 5 + i, mouseX, mouseY);
        }

        // Divider between hotbar and scrollable actions/presets area
        graphics.fill(contentX - 4, layout.actionsDividerY(), panelX + panelW - 12,
            layout.actionsDividerY() + 1, COLOR_BORDER);

        int lowerStartY = getKitLowerStartY(layout);
        int lowerEndY = getKitLowerEndY(layout);
        if (lowerEndY > lowerStartY) {
            graphics.enableScissor(panelX + 1, lowerStartY, panelX + panelW - 1, lowerEndY);
            int lowerOffset = kitLowerScrollOffset;

            // === ACTIONS ===
            renderActionsHeader(graphics, layout, lowerOffset);
            renderActionButtons(graphics, layout, mouseX, mouseY, lowerOffset);

        // === QUICK PRESETS ===
        graphics.fill(contentX - 4, layout.presetsDividerY() - lowerOffset, panelX + panelW - 12,
            layout.presetsDividerY() - lowerOffset + 1, COLOR_BORDER);

        drawScaledText(graphics, safeFont, I18n.translate("devmod.kit.section.quick_presets").getString(),
            contentX, layout.presetsLabelY() - lowerOffset, COLOR_TEXT_DIM);

            String[][] presets = getQuickPresets();

            int px = contentX;
            int presetY = layout.presetsRowY() - lowerOffset;
            for (int i = 0; i < presets.length; i++) {
                String presetName = Objects.requireNonNull(presets[i][0]);
                String presetIcon = Objects.requireNonNull(presets[i][1]);
                int pw = UIScaleManager.getScaledStringWidth(safeFont, presetName) + UIScaleManager.scale(20);
                boolean pHover = mouseX >= px && mouseX < px + pw && mouseY >= presetY && mouseY < presetY + scaledActionButtonHeight;

                graphics.fill(px, presetY, px + pw, presetY + scaledActionButtonHeight, pHover ? COLOR_ITEM_HOVER : COLOR_SLOT_EMPTY);
                renderBorder(graphics, px, presetY, pw, scaledActionButtonHeight, pHover ? COLOR_ACCENT : COLOR_BORDER);

                drawScaledText(graphics, safeFont, presetIcon + " " + presetName,
                    px + UIScaleManager.scale(4), presetY + UIScaleManager.scale(5), COLOR_TEXT);
                px += pw + UIScaleManager.scale(6);
            }

            // === INSTRUCTIONS ===
            graphics.fill(contentX - 4, layout.instructionsDividerY() - lowerOffset, panelX + panelW - 12,
                layout.instructionsDividerY() - lowerOffset + 1, COLOR_BORDER);

            int y = layout.instructionsStartY() - lowerOffset;
            String[] instructions = getInstructionLines();
            for (String instr : instructions) {
                drawScaledText(graphics, safeFont, instr, contentX, y, COLOR_TEXT_DIM);
                y += scaledTextLineHeight;
            }

            graphics.disableScissor();
        }

        if (kitLowerMaxScroll > 0) {
            int sbX = panelX + panelW - 6;
            int sbY = lowerStartY + 2;
            int sbH = Math.max(10, (lowerEndY - lowerStartY) - 4);
            int thumbH = Math.max(16, (int) ((float) sbH / (sbH + kitLowerMaxScroll) * sbH));
            int thumbY = sbY + (int) ((float) kitLowerScrollOffset / kitLowerMaxScroll * (sbH - thumbH));
            graphics.fill(sbX, sbY, sbX + 3, sbY + sbH, COLOR_ITEM_BG);
            graphics.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, COLOR_ACCENT);
        }
    }

    private void renderCharacterPreview(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        var safeFont = Objects.requireNonNull(font);
        var mc = Minecraft.getInstance();
        Player player = mc.player;

        if (player == null) {
            drawScaledText(graphics, safeFont, I18n.translate("devmod.kit.preview.unavailable").getString(),
                x - 20, y - 40, COLOR_TEXT_DIM);
            return;
        }

        // Save current equipment
        Map<EquipmentSlot, ItemStack> savedEquipment = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            savedEquipment.put(Objects.requireNonNull(slot), player.getItemBySlot(slot).copy());
        }

        // Apply kit equipment for preview
        ItemStack empty = Objects.requireNonNull(ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.HEAD, Objects.requireNonNull(Objects.requireNonNull(kitSlots.getOrDefault(0, empty)).copy()));
        player.setItemSlot(EquipmentSlot.CHEST, Objects.requireNonNull(Objects.requireNonNull(kitSlots.getOrDefault(1, empty)).copy()));
        player.setItemSlot(EquipmentSlot.LEGS, Objects.requireNonNull(Objects.requireNonNull(kitSlots.getOrDefault(2, empty)).copy()));
        player.setItemSlot(EquipmentSlot.FEET, Objects.requireNonNull(Objects.requireNonNull(kitSlots.getOrDefault(3, empty)).copy()));
        player.setItemSlot(EquipmentSlot.OFFHAND, Objects.requireNonNull(Objects.requireNonNull(kitSlots.getOrDefault(4, empty)).copy()));
        player.setItemSlot(EquipmentSlot.MAINHAND, Objects.requireNonNull(Objects.requireNonNull(kitSlots.getOrDefault(5, empty)).copy()));

        // Render player with mouse-follow rotation
        InventoryScreen.renderEntityInInventoryFollowsMouse(
            Objects.requireNonNull(graphics), x - 30, y - 80, x + 30, y + 10,
            35, 0.1f, mouseX, mouseY, player
        );

        // Restore equipment
        for (Map.Entry<EquipmentSlot, ItemStack> entry : savedEquipment.entrySet()) {
            player.setItemSlot(Objects.requireNonNull(entry.getKey()), Objects.requireNonNull(entry.getValue()));
        }
    }

    private void renderSlot(GuiGraphics graphics, int x, int y, int slot, int mouseX, int mouseY) {
        var safeFont = Objects.requireNonNull(font);
        boolean hovered = mouseX >= x && mouseX < x + scaledSlotSize && mouseY >= y && mouseY < y + scaledSlotSize;
        boolean selected = selectedSlot == slot;

        ItemStack stack = kitSlots.getOrDefault(slot, ItemStack.EMPTY);
        boolean hasItem = !stack.isEmpty();

        // Slot background
        int bgColor = selected ? COLOR_SLOT_SELECTED : (hovered ? COLOR_ITEM_HOVER : (hasItem ? COLOR_SLOT_FILLED : COLOR_SLOT_EMPTY));
        graphics.fill(x, y, x + scaledSlotSize, y + scaledSlotSize, bgColor);

        // Border
        int borderColor = selected ? COLOR_ACCENT : (hovered ? COLOR_ACCENT : COLOR_BORDER);
        renderBorder(graphics, x, y, scaledSlotSize, scaledSlotSize, borderColor);

        // Render item or slot hint
        if (hasItem) {
            graphics.renderItem(stack, x + UIScaleManager.scale(3), y + UIScaleManager.scale(3));
            if (stack.getCount() > 1) {
                String count = Objects.requireNonNull(String.valueOf(stack.getCount()));
                int countW = UIScaleManager.getScaledStringWidth(safeFont, count);
                drawScaledText(graphics, safeFont, count,
                    x + scaledSlotSize - countW - 1,
                    y + scaledSlotSize - scaledTextLineHeight,
                    COLOR_TEXT_WHITE);
            }
            // Enchant indicator
            if (stack.isEnchanted()) {
                drawScaledText(graphics, safeFont, "\u00A7d✦", x + 1, y + 1, COLOR_ACCENT_PURPLE);
            }
        } else {
            // Show slot icon
            String icon = Objects.requireNonNull(SLOT_ICONS[slot]);
            int iconW = UIScaleManager.getScaledStringWidth(safeFont, icon);
            int iconX = x + (scaledSlotSize - iconW) / 2;
            int iconY = y + (scaledSlotSize - scaledTextLineHeight) / 2;
            drawScaledText(graphics, safeFont, Objects.requireNonNull("\u00A78" + icon), iconX, iconY, COLOR_TEXT_DIM);
        }
    }

    private void renderActionsHeader(GuiGraphics graphics, KitPanelLayout layout, int yOffset) {
        var safeFont = Objects.requireNonNull(font);
        float textScale = UIScaleManager.getTextScale();
        String header = Objects.requireNonNull(I18n.translate("devmod.kit.section.actions").getString());
        drawScaledText(graphics, safeFont, header, layout.contentX(), layout.actionsLabelY() - yOffset, COLOR_TEXT_DIM);

        int panelRight = layout.panelX() + layout.panelW() - 12;
        int leftMin = layout.contentX() + UIScaleManager.getScaledStringWidth(safeFont, header) + 8;
        int maxWidth = panelRight - leftMin;
        if (maxWidth <= 0) {
            return;
        }

        String selection = Objects.requireNonNull(getActionsSelectionLabel());
        int selectionWidth = UIScaleManager.getScaledStringWidth(safeFont, selection);
        if (selectionWidth > maxWidth) {
            int maxWidthUnscaled = (int) (maxWidth / textScale);
            int ellipsisWidthUnscaled = safeFont.width("...");
            if (maxWidthUnscaled <= ellipsisWidthUnscaled) {
                selection = Objects.requireNonNull(safeFont.plainSubstrByWidth(selection, maxWidthUnscaled));
            } else {
                selection = Objects.requireNonNull(safeFont.plainSubstrByWidth(selection, maxWidthUnscaled - ellipsisWidthUnscaled)) + "...";
            }
        }
        int selectionX = panelRight - UIScaleManager.getScaledStringWidth(safeFont, selection);
        drawScaledText(graphics, safeFont, selection, selectionX, layout.actionsLabelY() - yOffset, COLOR_TEXT_DIM);
    }

    private void renderActionButtons(GuiGraphics graphics, KitPanelLayout layout, int mouseX, int mouseY, int yOffset) {
        var safeFont = Objects.requireNonNull(font);
        for (ActionButton button : buildActionButtons(layout, yOffset)) {
            boolean hovered = mouseX >= button.x() && mouseX < button.x() + button.w() &&
                              mouseY >= button.y() && mouseY < button.y() + button.h();
            boolean enabled = button.enabled();
            int bgColor = enabled ? (hovered ? COLOR_ITEM_HOVER : COLOR_PANEL) : COLOR_SLOT_EMPTY;
            int borderColor = enabled ? (hovered ? COLOR_ACCENT : COLOR_BORDER) : COLOR_BORDER;
            int textColor = enabled ? COLOR_TEXT : COLOR_TEXT_DIM;

            graphics.fill(button.x(), button.y(), button.x() + button.w(), button.y() + button.h(), bgColor);
            renderBorder(graphics, button.x(), button.y(), button.w(), button.h(), borderColor);

            String label = Objects.requireNonNull(button.label());
            int textW = UIScaleManager.getScaledStringWidth(safeFont, label);
            int textX = button.x() + (button.w() - textW) / 2;
            int textY = button.y() + (button.h() - scaledTextLineHeight) / 2;
            drawScaledText(graphics, safeFont, label, textX, textY, textColor);
        }
    }

    private List<ActionButton> buildActionButtons(KitPanelLayout layout, int yOffset) {
        var safeFont = Objects.requireNonNull(font);
        List<ActionButton> buttons = new ArrayList<>();

        ItemStack selectedStack = selectedSlot >= 0
            ? kitSlots.getOrDefault(selectedSlot, Objects.requireNonNull(ItemStack.EMPTY))
            : Objects.requireNonNull(ItemStack.EMPTY);
        boolean hasItem = selectedSlot >= 0 && !selectedStack.isEmpty();

        int x = layout.contentX();
        int y = layout.actionsRowY() - yOffset;

        String editLabel = Objects.requireNonNull(I18n.ui("edit").getString());
        int editW = Math.max(UIScaleManager.scale(70), UIScaleManager.getScaledStringWidth(safeFont, editLabel) + UIScaleManager.scale(16));
        buttons.add(new ActionButton(ActionType.EDIT, editLabel, x, y, editW, scaledActionButtonHeight, hasItem));
        x += editW + scaledActionButtonGap;

        String enchantLabel = Objects.requireNonNull(I18n.ui("enchant").getString());
        int enchantW = Math.max(UIScaleManager.scale(80), UIScaleManager.getScaledStringWidth(safeFont, enchantLabel) + UIScaleManager.scale(16));
        buttons.add(new ActionButton(ActionType.ENCHANT, enchantLabel, x, y, enchantW, scaledActionButtonHeight, hasItem));
        x += enchantW + scaledActionButtonGap;

        String removeLabel = Objects.requireNonNull(I18n.ui("remove").getString());
        int removeW = Math.max(UIScaleManager.scale(70), UIScaleManager.getScaledStringWidth(safeFont, removeLabel) + UIScaleManager.scale(16));
        buttons.add(new ActionButton(ActionType.REMOVE, removeLabel, x, y, removeW, scaledActionButtonHeight, hasItem));

        return buttons;
    }

    private String getActionsSelectionLabel() {
        if (selectedSlot < 0) {
            return Objects.requireNonNull(I18n.translate("devmod.kit.actions.select_slot").getString());
        }

        ItemStack stack = kitSlots.getOrDefault(selectedSlot, Objects.requireNonNull(ItemStack.EMPTY));
        if (stack.isEmpty()) {
            return I18n.translate("devmod.kit.actions.selected_empty", getSlotName(selectedSlot)).getString();
        }

        return I18n.translate("devmod.kit.actions.selected_item", stack.getHoverName().getString()).getString();
    }

    private void renderBottomBar(GuiGraphics graphics, int mouseX, int mouseY) {
        var safeFont = Objects.requireNonNull(font);
        int barY = height - UIScaleManager.scale(50);

        // Bar background
        graphics.fill(0, barY, width, height, COLOR_PANEL_HEADER);
        graphics.fill(0, barY, width, barY + 1, COLOR_BORDER);

        int btnY = barY + UIScaleManager.scale(10);
        int btnH = UIScaleManager.scale(28);
        int btnW = UIScaleManager.scale(90);
        int btnGap = UIScaleManager.scale(10);

        // Back button
        renderButton(graphics, scaledPanelPadding, btnY, btnW, btnH,
            I18n.translate("devmod.kit.button.back").getString(), COLOR_PANEL,
            mouseX, mouseY);

        // Clear button
        renderButton(graphics, scaledPanelPadding + btnW + btnGap, btnY, btnW, btnH,
            I18n.translate("devmod.kit.button.clear").getString(), COLOR_PANEL,
            mouseX, mouseY);

        // Right side buttons
        int rx = width - scaledPanelPadding;

        // Use Kit button (prominent)
        int useW = UIScaleManager.scale(120);
        boolean useHover = mouseX >= rx - useW && mouseX < rx && mouseY >= btnY && mouseY < btnY + btnH;
        graphics.fill(rx - useW, btnY, rx, btnY + btnH, useHover ? COLOR_BTN_SUCCESS_HOVER : COLOR_ACCENT_GREEN);
        renderBorder(graphics, rx - useW, btnY, useW, btnH, useHover ? COLOR_BTN_SUCCESS_BORDER_HOVER : COLOR_BTN_SUCCESS_BORDER);
        String useText = Objects.requireNonNull(I18n.translate("devmod.kit.button.use").getString());
        int useTextW = UIScaleManager.getScaledStringWidth(safeFont, useText);
        int useTextX = rx - useW + (useW - useTextW) / 2;
        int useTextY = btnY + (btnH - scaledTextLineHeight) / 2;
        drawScaledText(graphics, safeFont, useText, useTextX, useTextY, COLOR_TEXT_WHITE);

        // Save button
        int saveW = UIScaleManager.scale(110);
        boolean saveHover = mouseX >= rx - useW - saveW - btnGap && mouseX < rx - useW - btnGap && mouseY >= btnY && mouseY < btnY + btnH;
        graphics.fill(rx - useW - saveW - btnGap, btnY, rx - useW - btnGap, btnY + btnH, saveHover ? COLOR_ITEM_HOVER : COLOR_PANEL);
        renderBorder(graphics, rx - useW - saveW - btnGap, btnY, saveW, btnH, saveHover ? COLOR_ACCENT : COLOR_BORDER);
        String saveText = Objects.requireNonNull(I18n.translate("devmod.kit.button.save_preset").getString());
        int saveTextW = UIScaleManager.getScaledStringWidth(safeFont, saveText);
        int saveTextX = rx - useW - saveW - btnGap + (saveW - saveTextW) / 2;
        int saveTextY = btnY + (btnH - scaledTextLineHeight) / 2;
        drawScaledText(graphics, safeFont, saveText, saveTextX, saveTextY, COLOR_TEXT);
    }

    private void renderButton(GuiGraphics graphics, int x, int y, int w, int h, String text, int bgColor,
                              int mouseX, int mouseY) {
        var safeFont = Objects.requireNonNull(font);
        String safeText = Objects.requireNonNull(text);
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;

        graphics.fill(x, y, x + w, y + h, hovered ? COLOR_ITEM_HOVER : bgColor);
        renderBorder(graphics, x, y, w, h, hovered ? COLOR_ACCENT : COLOR_BORDER);

        int textW = UIScaleManager.getScaledStringWidth(safeFont, safeText);
        int textX = x + (w - textW) / 2;
        int textY = y + (h - scaledTextLineHeight) / 2;
        drawScaledText(graphics, safeFont, safeText, textX, textY, COLOR_TEXT);
    }

    private void renderBorder(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    private void renderPanelBorder(GuiGraphics graphics, int x, int y, int w, int h) {
        renderBorder(graphics, x, y, w, h, COLOR_BORDER);
    }

    private void renderModalLayer(GuiGraphics graphics, Runnable renderer) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, DesignTokens.ZOrder.MODAL);
        RenderSystem.disableDepthTest();
        renderer.run();
        RenderSystem.enableDepthTest();
        graphics.pose().popPose();
    }

    private void renderEnchantPopup(GuiGraphics graphics, int mouseX, int mouseY) {
        var safeFont = Objects.requireNonNull(font);

        int popupW = 280;
        int popupH = 320;
        int popupX = (width - popupW) / 2;
        int popupY = (height - popupH) / 2;

        // Darken background
        graphics.fill(0, 0, width, height, EnduranceUiTheme.KitSelection.SCRIM);

        // Popup background
        graphics.fill(popupX, popupY, popupX + popupW, popupY + popupH, COLOR_PANEL);
        renderBorder(graphics, popupX, popupY, popupW, popupH, COLOR_ACCENT_PURPLE);

        // Header
        graphics.fill(popupX, popupY, popupX + popupW, popupY + 30, COLOR_PANEL_HEADER);
        drawScaledText(graphics, safeFont, I18n.translate("devmod.kit.popup.enchant_title").getString(),
            popupX + 10, popupY + 11, COLOR_TEXT);

        // Close button
        int closeX = popupX + popupW - 22;
        boolean closeHover = mouseX >= closeX && mouseX < closeX + 16 && mouseY >= popupY + 7 && mouseY < popupY + 23;
        drawScaledText(graphics, safeFont, closeHover ? "\u00A7c✖" : "\u00A77✖", closeX, popupY + 11, COLOR_TEXT);

        // Enchantment list
        int listY = popupY + 40;
        int listH = popupH - 80;

        graphics.enableScissor(popupX + 10, listY, popupX + popupW - 10, listY + listH);

        if (availableEnchants.isEmpty()) {
            String emptyLabel = Objects.requireNonNull(I18n.ui("no_results").getString());
            int textW = UIScaleManager.getScaledStringWidth(safeFont, emptyLabel);
            int textX = popupX + (popupW - textW) / 2;
            int textY = listY + listH / 2 - (scaledTextLineHeight / 2);
            drawScaledText(graphics, safeFont, emptyLabel, textX, textY, COLOR_TEXT_DIM);
        } else {
            int ey = listY - enchantScrollOffset;
            for (EnchantmentOption opt : availableEnchants) {
                if (ey + 24 < listY || ey > listY + listH) {
                    ey += 26;
                    continue;
                }

                boolean eHover = mouseX >= popupX + 10 && mouseX < popupX + popupW - 10 &&
                                mouseY >= ey && mouseY < ey + 24;

                graphics.fill(popupX + 10, ey, popupX + popupW - 10, ey + 24, eHover ? COLOR_ITEM_HOVER : COLOR_SLOT_EMPTY);
                renderBorder(graphics, popupX + 10, ey, popupW - 20, 24, eHover ? COLOR_ACCENT_PURPLE : COLOR_BORDER);

                drawScaledText(graphics, safeFont, opt.displayName, popupX + 16, ey + 8, COLOR_TEXT);

                // Level buttons
                int lvlX = popupX + popupW - 80;
                for (int lvl = 1; lvl <= opt.maxLevel && lvl <= 5; lvl++) {
                    int btnX = lvlX + (lvl - 1) * 14;
                    boolean lvlHover = mouseX >= btnX && mouseX < btnX + 12 && mouseY >= ey + 4 && mouseY < ey + 20;
                    int lvlColor = lvlHover ? COLOR_ACCENT_PURPLE : COLOR_TEXT_DIM;
                    drawScaledText(graphics, safeFont, String.valueOf(lvl), btnX + 3, ey + 8, lvlColor);
                }

                ey += 26;
            }
        }

        graphics.disableScissor();

        // Cancel button
        int btnY = popupY + popupH - 35;
        boolean cancelHover = mouseX >= popupX + popupW/2 - 40 && mouseX < popupX + popupW/2 + 40 &&
                             mouseY >= btnY && mouseY < btnY + 24;
        graphics.fill(popupX + popupW/2 - 40, btnY, popupX + popupW/2 + 40, btnY + 24, cancelHover ? COLOR_ITEM_HOVER : COLOR_PANEL);
        renderBorder(graphics, popupX + popupW/2 - 40, btnY, 80, 24, cancelHover ? COLOR_ACCENT : COLOR_BORDER);
        String cancelLabel = Objects.requireNonNull(I18n.ui("cancel").getString());
        int cancelW = UIScaleManager.getScaledStringWidth(safeFont, cancelLabel);
        drawScaledText(graphics, safeFont, cancelLabel, popupX + popupW/2 - cancelW / 2, btnY + 8, COLOR_TEXT);
    }

    private void renderNameDialog(GuiGraphics graphics, int mouseX, int mouseY) {
        var safeFont = Objects.requireNonNull(font);

        int dialogW = 300;
        int dialogH = 140;
        int dialogX = (width - dialogW) / 2;
        int dialogY = (height - dialogH) / 2;

        // Darken background
        graphics.fill(0, 0, width, height, EnduranceUiTheme.KitSelection.SCRIM);

        // Dialog background
        graphics.fill(dialogX, dialogY, dialogX + dialogW, dialogY + dialogH, COLOR_PANEL);
        renderBorder(graphics, dialogX, dialogY, dialogW, dialogH, COLOR_ACCENT_ORANGE);

        // Header
        graphics.fill(dialogX, dialogY, dialogX + dialogW, dialogY + 30, COLOR_PANEL_HEADER);
        drawScaledText(graphics, safeFont, I18n.translate("devmod.kit.dialog.save_title").getString(),
            dialogX + 10, dialogY + 11, COLOR_TEXT);

        // Name input field
        EditBox nameBox = kitNameBox;
        if (nameBox == null) {
            nameBox = new EditBox(safeFont, dialogX + 20, dialogY + 50, dialogW - 40, 20, I18n.ui("kit_name"));
            nameBox.setValue(Objects.requireNonNull(kitNameInput));
            nameBox.setHint(Objects.requireNonNull(I18n.translate("devmod.kit.enter_name")));
            nameBox.setBordered(false);
            nameBox.setTextColor(DesignTokens.Text.PRIMARY());
            nameBox.setTextColorUneditable(DesignTokens.Text.MUTED());
            kitNameBox = nameBox;
            addRenderableWidget(nameBox);
        }

        AxiomRenderer.drawInputBackground(graphics, nameBox.getX(), nameBox.getY(), nameBox.getWidth(),
            nameBox.getHeight(), nameBox.isFocused());
        nameBox.render(graphics, mouseX, mouseY, 0);

        // Buttons
        int btnY = dialogY + dialogH - 40;

        // Cancel
        boolean cancelHover = mouseX >= dialogX + 20 && mouseX < dialogX + 100 && mouseY >= btnY && mouseY < btnY + 26;
        graphics.fill(dialogX + 20, btnY, dialogX + 100, btnY + 26, cancelHover ? COLOR_ITEM_HOVER : COLOR_PANEL);
        renderBorder(graphics, dialogX + 20, btnY, 80, 26, cancelHover ? COLOR_ACCENT : COLOR_BORDER);
        String cancelLabel = Objects.requireNonNull(I18n.ui("cancel").getString());
        int cancelW = UIScaleManager.getScaledStringWidth(safeFont, cancelLabel);
        drawScaledText(graphics, safeFont, cancelLabel,
            dialogX + 20 + (80 - cancelW) / 2, btnY + 9, COLOR_TEXT);

        // Save
        boolean saveHover = mouseX >= dialogX + dialogW - 100 && mouseX < dialogX + dialogW - 20 &&
                           mouseY >= btnY && mouseY < btnY + 26;
        graphics.fill(dialogX + dialogW - 100, btnY, dialogX + dialogW - 20, btnY + 26,
            saveHover ? COLOR_BTN_SUCCESS_HOVER : COLOR_ACCENT_GREEN);
        renderBorder(graphics, dialogX + dialogW - 100, btnY, 80, 26, saveHover ? COLOR_BTN_SUCCESS_BORDER_HOVER : COLOR_BTN_SUCCESS_BORDER);
        String saveLabel = "\u00A7l" + I18n.ui("save").getString();
        int saveW = UIScaleManager.getScaledStringWidth(safeFont, saveLabel);
        drawScaledText(graphics, safeFont, saveLabel,
            dialogX + dialogW - 100 + (80 - saveW) / 2, btnY + 9, COLOR_TEXT_WHITE);
    }

    private void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        // Item browser tooltips
        ItemGridLayout layout = getItemGridLayout();
        int gridX = layout.gridX();
        int gridY = layout.gridY();
        int gridW = layout.gridW();
        int gridH = layout.gridH();

        if (mouseX >= gridX && mouseX < gridX + gridW && mouseY >= gridY && mouseY < gridY + gridH) {
            int itemsPerRow = Math.max(1, gridW / (scaledItemSize + scaledItemMargin));
            int relX = mouseX - gridX;
            int relY = mouseY - gridY + itemScrollOffset;
            int col = relX / (scaledItemSize + scaledItemMargin);
            int row = relY / (scaledItemSize + scaledItemMargin);
            int index = row * itemsPerRow + col;

            if (index >= 0 && index < filteredItems.size()) {
                ItemStack stack = Objects.requireNonNull(filteredItems.get(index));
                graphics.renderTooltip(Objects.requireNonNull(font), stack, mouseX, mouseY);
            }
        }

        // Slot tooltips
        renderSlotTooltips(graphics, mouseX, mouseY);
    }

    private void renderSlotTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        KitPanelLayout layout = getKitPanelLayout();
        int panelX = layout.contentX();
        int y = layout.equipmentRowY();

        // Armor + offhand row
        int slotGap = UIScaleManager.scale(6);
        for (int slot = 0; slot < 5; slot++) {
            int slotX = panelX + (slot < 4 ? slot * (scaledSlotSize + slotGap) : 4 * (scaledSlotSize + slotGap) + UIScaleManager.scale(10));
            int slotY = y;

            if (mouseX >= slotX && mouseX < slotX + scaledSlotSize && mouseY >= slotY && mouseY < slotY + scaledSlotSize) {
                ItemStack stack = kitSlots.getOrDefault(slot, ItemStack.EMPTY);
                if (!stack.isEmpty()) {
                    graphics.renderTooltip(Objects.requireNonNull(font), stack, mouseX, mouseY);
                } else {
                    List<net.minecraft.network.chat.Component> tooltip = Objects.requireNonNull(List.of(
                        net.minecraft.network.chat.Component.literal("\u00A77" + getSlotName(slot))));
                    graphics.renderTooltip(Objects.requireNonNull(font), tooltip, Objects.requireNonNull(Optional.empty()), mouseX, mouseY);
                }
                return;
            }
        }

        // Hotbar row
        int hotbarY = layout.hotbarRowY();
        int hotbarSlotGap = UIScaleManager.scale(4);
        for (int i = 0; i < 9; i++) {
            int slotX = panelX + i * (scaledSlotSize + hotbarSlotGap);
            int slotY = hotbarY;

            if (mouseX >= slotX && mouseX < slotX + scaledSlotSize && mouseY >= slotY && mouseY < slotY + scaledSlotSize) {
                ItemStack stack = kitSlots.getOrDefault(5 + i, Objects.requireNonNull(ItemStack.EMPTY));
                if (!stack.isEmpty()) {
                    graphics.renderTooltip(Objects.requireNonNull(font), stack, mouseX, mouseY);
                } else {
                    List<net.minecraft.network.chat.Component> tooltip = Objects.requireNonNull(List.of(
                        net.minecraft.network.chat.Component.literal("\u00A77" + getSlotName(5 + i))));
                    graphics.renderTooltip(Objects.requireNonNull(font), tooltip, Objects.requireNonNull(Optional.empty()), mouseX, mouseY);
                }
                return;
            }
        }
    }

    private String getSlotName(int slot) {
        return switch (slot) {
            case 0 -> I18n.translate("devmod.kit.slot.helmet").getString();
            case 1 -> I18n.translate("devmod.kit.slot.chestplate").getString();
            case 2 -> I18n.translate("devmod.kit.slot.leggings").getString();
            case 3 -> I18n.translate("devmod.kit.slot.boots").getString();
            case 4 -> I18n.translate("devmod.kit.slot.offhand").getString();
            default -> I18n.translate("devmod.kit.slot.hotbar", slot - 4).getString();
        };
    }

    private String[][] getQuickPresets() {
        return new String[][] {
            {I18n.translate("devmod.kit.preset.iron").getString(), "🛡"},
            {I18n.translate("devmod.kit.preset.diamond").getString(), "💎"},
            {I18n.translate("devmod.kit.preset.netherite").getString(), "🔥"},
            {I18n.translate("devmod.kit.preset.max_enchanted").getString(), "✨"}
        };
    }

    private String[] getInstructionLines() {
        return new String[] {
            I18n.translate("devmod.kit.instructions.add").getString(),
            I18n.translate("devmod.kit.instructions.remove").getString(),
            I18n.translate("devmod.kit.instructions.stack").getString(),
            I18n.translate("devmod.kit.instructions.stack_exact").getString(),
            I18n.translate("devmod.kit.instructions.slot").getString(),
            I18n.translate("devmod.kit.instructions.actions").getString()
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle popups first
        if (showNameDialog) {
            return handleNameDialogClick((int) mouseX, (int) mouseY, button);
        }
        if (showEnchantPopup) {
            return handleEnchantPopupClick((int) mouseX, (int) mouseY);
        }

        // Category tabs
        if (handleCategoryTabClick((int) mouseX, (int) mouseY)) return true;

        // Bottom bar buttons
        if (handleBottomBarClick((int) mouseX, (int) mouseY)) return true;

        // Item browser
        if (handleItemBrowserClick((int) mouseX, (int) mouseY)) return true;

        // Kit slots
        if (handleSlotClick((int) mouseX, (int) mouseY, button)) return true;

        // Actions
        if (handleActionButtonsClick((int) mouseX, (int) mouseY)) return true;

        // Quick presets
        if (handlePresetClick((int) mouseX, (int) mouseY)) return true;

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleCategoryTabClick(int mouseX, int mouseY) {
        int tabMargin = UIScaleManager.scale(4);
        if (mouseY < tabMargin || mouseY >= scaledTabHeight - tabMargin) return false;

        int tabX = UIScaleManager.scale(120);
        int tabW = UIScaleManager.scale(80);
        for (Category cat : Category.values()) {
            if (mouseX >= tabX && mouseX < tabX + tabW) {
                selectedCategory = cat;
                filterItems();
                playClickSound();
                return true;
            }
            tabX += tabW + UIScaleManager.scale(4);
        }
        return false;
    }

    private boolean handleBottomBarClick(int mouseX, int mouseY) {
        int barY = height - UIScaleManager.scale(50);
        int btnY = barY + UIScaleManager.scale(10);
        int btnH = UIScaleManager.scale(28);
        int btnW = UIScaleManager.scale(90);
        int btnGap = UIScaleManager.scale(10);

        // Back button
        if (mouseX >= scaledPanelPadding && mouseX < scaledPanelPadding + btnW &&
            mouseY >= btnY && mouseY < btnY + btnH) {
            goBack();
            return true;
        }

        // Clear button
        if (mouseX >= scaledPanelPadding + btnW + btnGap && mouseX < scaledPanelPadding + btnW * 2 + btnGap &&
            mouseY >= btnY && mouseY < btnY + btnH) {
            clearKit();
            return true;
        }

        int rx = width - scaledPanelPadding;
        int useW = UIScaleManager.scale(120);
        int saveW = UIScaleManager.scale(110);

        // Use Kit button
        if (mouseX >= rx - useW && mouseX < rx && mouseY >= btnY && mouseY < btnY + btnH) {
            useKit();
            return true;
        }

        // Save button
        if (mouseX >= rx - useW - saveW - btnGap && mouseX < rx - useW - btnGap &&
            mouseY >= btnY && mouseY < btnY + btnH) {
            openNameDialog();
            return true;
        }

        return false;
    }

    private boolean handleItemBrowserClick(int mouseX, int mouseY) {
        ItemGridLayout layout = getItemGridLayout();
        int gridX = layout.gridX();
        int gridY = layout.gridY();
        int gridW = layout.gridW();
        int gridH = layout.gridH();

        if (mouseX < gridX || mouseX >= gridX + gridW || mouseY < gridY || mouseY >= gridY + gridH) {
            return false;
        }

        int itemsPerRow = Math.max(1, gridW / (scaledItemSize + scaledItemMargin));
        int relX = mouseX - gridX;
        int relY = mouseY - gridY + itemScrollOffset;
        int col = relX / (scaledItemSize + scaledItemMargin);
        int row = relY / (scaledItemSize + scaledItemMargin);
        int index = row * itemsPerRow + col;

        if (index >= 0 && index < filteredItems.size()) {
            ItemStack stack = filteredItems.get(index).copy();

            if (hasShiftDown()) {
                stack.setCount(stack.getMaxStackSize());
            }

            if (selectedSlot >= 0) {
                if (selectedSlot >= 5) {
                    int merged = mergeIntoSlot(selectedSlot, stack);
                    if (merged > 0 && !stack.isEmpty()) {
                        addItemToKit(stack);
                    } else if (merged == 0) {
                        kitSlots.put(selectedSlot, stack);
                    }
                } else {
                    kitSlots.put(selectedSlot, stack);
                }
            } else {
                addItemToKit(stack);
            }

            playClickSound();
            return true;
        }
        return false;
    }

    private boolean handleSlotClick(int mouseX, int mouseY, int button) {
        KitPanelLayout layout = getKitPanelLayout();
        int panelX = layout.contentX();
        int y = layout.equipmentRowY();

        // Armor + offhand
        int slotGap = UIScaleManager.scale(6);
        for (int slot = 0; slot < 5; slot++) {
            int slotX = panelX + (slot < 4 ? slot * (scaledSlotSize + slotGap) : 4 * (scaledSlotSize + slotGap) + UIScaleManager.scale(10));
            int slotY = y;

            if (mouseX >= slotX && mouseX < slotX + scaledSlotSize && mouseY >= slotY && mouseY < slotY + scaledSlotSize) {
                return handleSlotAction(slot, button);
            }
        }

        // Hotbar
        int hotbarY = layout.hotbarRowY();
        int hotbarSlotGap = UIScaleManager.scale(4);
        for (int i = 0; i < 9; i++) {
            int slotX = panelX + i * (scaledSlotSize + hotbarSlotGap);
            int slotY = hotbarY;

            if (mouseX >= slotX && mouseX < slotX + scaledSlotSize && mouseY >= slotY && mouseY < slotY + scaledSlotSize) {
                return handleSlotAction(5 + i, button);
            }
        }

        return false;
    }

    private boolean handleSlotAction(int slot, int button) {
        if (button == 1) {
            // Right click = remove
            kitSlots.remove(slot);
            playRemoveSound();
        } else if (button == 0 && hasControlDown()) {
            // Ctrl+left click = open full item editor
            ItemStack stack = kitSlots.get(slot);
            if (stack != null && !stack.isEmpty()) {
                openItemEditor(slot, stack);
            }
        } else if (button == 0 && hasShiftDown()) {
            // Shift+left click = enchant menu
            ItemStack stack = kitSlots.get(slot);
            if (stack != null && !stack.isEmpty()) {
                openEnchantPopup(slot);
            }
        } else {
            // Left click = select slot
            selectedSlot = (selectedSlot == slot) ? -1 : slot;
            playClickSound();
        }
        return true;
    }

    private boolean handleActionButtonsClick(int mouseX, int mouseY) {
        KitPanelLayout layout = getKitPanelLayout();
        updateKitLowerScroll(layout);
        if (!isMouseInKitLowerRegion(mouseX, mouseY, layout)) {
            return false;
        }
        for (ActionButton button : buildActionButtons(layout, kitLowerScrollOffset)) {
            if (mouseX >= button.x() && mouseX < button.x() + button.w() &&
                mouseY >= button.y() && mouseY < button.y() + button.h()) {
                if (!button.enabled()) {
                    return true;
                }
                if (selectedSlot < 0) {
                    return true;
                }

                ItemStack stack = kitSlots.getOrDefault(selectedSlot, Objects.requireNonNull(ItemStack.EMPTY));
                switch (button.type()) {
                    case EDIT -> openItemEditor(selectedSlot, stack);
                    case ENCHANT -> openEnchantPopup(selectedSlot);
                    case REMOVE -> {
                        kitSlots.remove(selectedSlot);
                        playRemoveSound();
                    }
                }
                return true;
            }
        }
        return false;
    }

    private boolean handlePresetClick(int mouseX, int mouseY) {
        var safeFont = Objects.requireNonNull(font);
        KitPanelLayout layout = getKitPanelLayout();
        int panelX = layout.contentX();
        updateKitLowerScroll(layout);
        if (!isMouseInKitLowerRegion(mouseX, mouseY, layout)) {
            return false;
        }
        int y = layout.presetsRowY() - kitLowerScrollOffset;

        String[][] presets = getQuickPresets();

        int px = panelX;
        for (int i = 0; i < presets.length; i++) {
            int pw = UIScaleManager.getScaledStringWidth(safeFont, Objects.requireNonNull(presets[i][0])) + UIScaleManager.scale(20);
            if (mouseX >= px && mouseX < px + pw && mouseY >= y && mouseY < y + scaledActionButtonHeight) {
                applyQuickSet(i);
                playSuccessSound();
                return true;
            }
            px += pw + UIScaleManager.scale(6);
        }
        return false;
    }

    private boolean handleEnchantPopupClick(int mouseX, int mouseY) {
        int popupW = 280;
        int popupH = 320;
        int popupX = (width - popupW) / 2;
        int popupY = (height - popupH) / 2;

        // Close button
        int closeX = popupX + popupW - 22;
        if (mouseX >= closeX && mouseX < closeX + 16 && mouseY >= popupY + 7 && mouseY < popupY + 23) {
            showEnchantPopup = false;
            playClickSound();
            return true;
        }

        // Cancel button
        int btnY = popupY + popupH - 35;
        if (mouseX >= popupX + popupW/2 - 40 && mouseX < popupX + popupW/2 + 40 &&
            mouseY >= btnY && mouseY < btnY + 24) {
            showEnchantPopup = false;
            playClickSound();
            return true;
        }

        // Enchantment selection
        int listY = popupY + 40;
        int ey = listY - enchantScrollOffset;
        for (EnchantmentOption opt : availableEnchants) {
            if (mouseY >= ey && mouseY < ey + 24) {
                // Level buttons
                int lvlX = popupX + popupW - 80;
                for (int lvl = 1; lvl <= opt.maxLevel && lvl <= 5; lvl++) {
                    int btnX = lvlX + (lvl - 1) * 14;
                    if (mouseX >= btnX && mouseX < btnX + 12) {
                        applyEnchantment(opt, lvl);
                        return true;
                    }
                }
            }
            ey += 26;
        }

        return true; // Consume click in popup area
    }

    private boolean handleNameDialogClick(int mouseX, int mouseY, int button) {
        int dialogW = 300;
        int dialogH = 140;
        int dialogX = (width - dialogW) / 2;
        int dialogY = (height - dialogH) / 2;

        int btnY = dialogY + dialogH - 40;

        // Cancel button
        if (mouseX >= dialogX + 20 && mouseX < dialogX + 100 && mouseY >= btnY && mouseY < btnY + 26) {
            closeNameDialog();
            return true;
        }

        // Save button
        if (mouseX >= dialogX + dialogW - 100 && mouseX < dialogX + dialogW - 20 &&
            mouseY >= btnY && mouseY < btnY + 26) {
            saveKitWithName();
            return true;
        }

        // Pass to text field
        if (kitNameBox != null) {
            return kitNameBox.mouseClicked(mouseX, mouseY, button);
        }

        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (showEnchantPopup) {
            enchantScrollOffset = Math.max(0, enchantScrollOffset - (int) (scrollY * 20));
            return true;
        }

        ItemGridLayout layout = getItemGridLayout();
        int gridX = layout.gridX();
        int gridY = layout.gridY();
        int gridW = layout.gridW();
        int gridH = layout.gridH();

        if (mouseX >= gridX && mouseX < gridX + gridW && mouseY >= gridY && mouseY < gridY + gridH) {
            itemScrollOffset = Math.max(0, Math.min(itemMaxScroll, itemScrollOffset - (int) (scrollY * 24)));
            return true;
        }

        KitPanelLayout kitLayout = getKitPanelLayout();
        updateKitLowerScroll(kitLayout);
        if (kitLowerMaxScroll > 0 && isMouseInKitLowerRegion((int) mouseX, (int) mouseY, kitLayout)) {
            kitLowerScrollOffset = Math.max(0, Math.min(kitLowerMaxScroll, kitLowerScrollOffset - (int) (scrollY * 20)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (showNameDialog) {
            if (keyCode == 256) { // Escape
                closeNameDialog();
                return true;
            }
            if (keyCode == 257) { // Enter
                saveKitWithName();
                return true;
            }
            if (kitNameBox != null) {
                return kitNameBox.keyPressed(keyCode, scanCode, modifiers);
            }
            return true;
        }

        if (keyCode == 256 && showEnchantPopup) {
            showEnchantPopup = false;
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        final EditBox nameBox = kitNameBox;
        if (showNameDialog && nameBox != null && nameBox.isFocused()) {
            return nameBox.charTyped(chr, modifiers);
        }
        return super.charTyped(chr, modifiers);
    }

    private void addItemToKit(ItemStack stack) {
        int equipSlot = getKitSlotIndex(stack);
        if (equipSlot >= 0) {
            kitSlots.put(equipSlot, stack);
            return;
        }
        if (tryStackIntoHotbar(stack)) {
            return;
        }
        for (int slot = 5; slot <= 13; slot++) {
            if (!kitSlots.containsKey(slot) || kitSlots.get(slot).isEmpty()) {
                kitSlots.put(slot, stack);
                return;
            }
        }
    }

    private boolean tryStackIntoHotbar(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getMaxStackSize() <= 1) {
            return false;
        }
        for (int slot = 5; slot <= 13; slot++) {
            int merged = mergeIntoSlot(slot, stack);
            if (merged > 0 && stack.isEmpty()) {
                return true;
            }
        }
        return stack.isEmpty();
    }

    private int mergeIntoSlot(int slot, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        ItemStack existing = kitSlots.get(slot);
        if (existing == null || existing.isEmpty()) {
            return 0;
        }
        if (existing.getMaxStackSize() <= 1 || !ItemStack.isSameItemSameComponents(existing, stack)) {
            return 0;
        }
        int max = Math.min(existing.getMaxStackSize(), stack.getMaxStackSize());
        int room = max - existing.getCount();
        if (room <= 0) {
            return 0;
        }
        int toAdd = Math.min(room, stack.getCount());
        if (toAdd <= 0) {
            return 0;
        }
        existing.setCount(existing.getCount() + toAdd);
        stack.shrink(toAdd);
        return toAdd;
    }

    private void applyQuickSet(int setIndex) {
        Item helmet, chest, legs, boots, sword, shield;
        boolean maxEnchant = setIndex == 3;

        switch (setIndex) {
            case 0 -> { // Iron
                helmet = Items.IRON_HELMET; chest = Items.IRON_CHESTPLATE;
                legs = Items.IRON_LEGGINGS; boots = Items.IRON_BOOTS;
                sword = Items.IRON_SWORD; shield = Items.SHIELD;
            }
            case 1 -> { // Diamond
                helmet = Items.DIAMOND_HELMET; chest = Items.DIAMOND_CHESTPLATE;
                legs = Items.DIAMOND_LEGGINGS; boots = Items.DIAMOND_BOOTS;
                sword = Items.DIAMOND_SWORD; shield = Items.SHIELD;
            }
            default -> { // Netherite or Max Enchanted
                helmet = Items.NETHERITE_HELMET; chest = Items.NETHERITE_CHESTPLATE;
                legs = Items.NETHERITE_LEGGINGS; boots = Items.NETHERITE_BOOTS;
                sword = Items.NETHERITE_SWORD; shield = Items.SHIELD;
            }
        }

        kitSlots.put(0, new ItemStack(Objects.requireNonNull(helmet)));
        kitSlots.put(1, new ItemStack(Objects.requireNonNull(chest)));
        kitSlots.put(2, new ItemStack(Objects.requireNonNull(legs)));
        kitSlots.put(3, new ItemStack(Objects.requireNonNull(boots)));
        kitSlots.put(4, new ItemStack(Objects.requireNonNull(shield)));
        kitSlots.put(5, new ItemStack(Objects.requireNonNull(sword)));

        // Add some extras for max enchanted
        if (maxEnchant) {
            kitSlots.put(6, new ItemStack(Objects.requireNonNull(Items.GOLDEN_APPLE), 16));
            kitSlots.put(7, new ItemStack(Objects.requireNonNull(Items.ENDER_PEARL), 16));
            kitSlots.put(8, new ItemStack(Objects.requireNonNull(Items.TOTEM_OF_UNDYING)));
        }
    }

    private void openEnchantPopup(int slot) {
        enchantSlot = slot;
        showEnchantPopup = true;
        enchantScrollOffset = 0;
        loadAvailableEnchantments(slot);
        playClickSound();
    }

    /**
     * Opens the full ItemEditor for the item in the specified slot.
     * When the user applies changes in the editor, the modified item
     * is returned via callback and placed back in the slot.
     *
     * @param slot The kit slot index
     * @param stack The item to edit
     */
    private void openItemEditor(int slot, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        // Create callback to receive the edited item
        java.util.function.Consumer<ItemStack> onItemEdited = editedStack -> {
            if (editedStack != null && !editedStack.isEmpty()) {
                kitSlots.put(slot, editedStack.copy());
                LOGGER.debug("Kit slot {} updated with edited item: {}", slot, editedStack.getHoverName().getString());
                Notification notification = Notification.builder(NotificationCategory.SYSTEM)
                    .titleKey("devmod.kit.notification.item_saved.title")
                    .messageKey("devmod.kit.notification.item_saved.message")
                    .param("item", editedStack.getHoverName().getString())
                    .priority(NotificationPriority.NORMAL)
                    .displayDurationMs(2000)
                    .build();
                ClientNotificationManager.INSTANCE.handleNotification(notification);
            }
        };

        // Use GENERAL tab as default - the editor will auto-detect the appropriate module
        EditorStartTab startTab = EditorStartTab.GENERAL;

        // Open the editor with callback support
        com.devmod.client.ui.ScreenSafety.openSafe(
            "item_editor",
            this,
            () -> new ItemEditorScreen(stack, startTab, this, onItemEdited, true));
        playClickSound();
    }

    private void loadAvailableEnchantments(int slot) {
        availableEnchants.clear();
        ItemStack stack = kitSlots.get(slot);
        if (stack == null || stack.isEmpty()) return;

        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null) return;

        var enchantmentRegistry = level.registryAccess().registryOrThrow(Objects.requireNonNull(Registries.ENCHANTMENT));

        for (Holder<Enchantment> holder : enchantmentRegistry.holders().toList()) {
            Holder<Enchantment> safeHolder = Objects.requireNonNull(holder);
            Enchantment enchant = safeHolder.value();
            // Check if enchantment can be applied to this item using supportedItems
            var itemHolder = Objects.requireNonNull(stack.getItemHolder());
            if (enchant.definition().supportedItems().contains(itemHolder)) {
                String name = safeHolder.unwrapKey()
                    .map(key -> formatEnchantmentName(Objects.requireNonNull(key.location().getPath())))
                    .orElse(I18n.translate("devmod.kit.enchantment.unknown").getString());
                availableEnchants.add(new EnchantmentOption(safeHolder, name, enchant.getMaxLevel()));
            }
        }

        // Sort alphabetically
        availableEnchants.sort(Comparator.comparing(e -> e.displayName));
    }

    private String formatEnchantmentName(@Nonnull String path) {
        return UNDERSCORE_SPLITTER.splitToList(path).stream()
            .map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1))
            .reduce((a, b) -> a + " " + b)
            .orElse(path);
    }

    private String getDefaultKitNameWithIndex() {
        return I18n.translate("devmod.kit.default_name", KitPersistence.getKitCount() + 1).getString();
    }

    private String getDefaultKitNameSimple() {
        return I18n.translate("devmod.kit.default_name.simple").getString();
    }

    private void applyEnchantment(EnchantmentOption opt, int level) {
        ItemStack stack = kitSlots.get(enchantSlot);
        if (stack == null || stack.isEmpty()) return;

        stack.enchant(Objects.requireNonNull(opt.holder), level);
        showEnchantPopup = false;
        playSuccessSound();
    }

    private void openNameDialog() {
        showNameDialog = true;
        if (kitNameInput.isEmpty()) {
            kitNameInput = getDefaultKitNameWithIndex();
        }
        kitNameBox = null; // Will be recreated in render
        playClickSound();
    }

    private void closeNameDialog() {
        showNameDialog = false;
        if (kitNameBox != null) {
            removeWidget(kitNameBox);
            kitNameBox = null;
        }
        playClickSound();
    }

    private void saveKitWithName() {
        if (kitNameBox != null) {
            kitNameInput = kitNameBox.getValue().trim();
        }
        if (kitNameInput.isEmpty()) {
            kitNameInput = getDefaultKitNameWithIndex();
        }

        CustomKit kit = editingKit != null ? editingKit : new CustomKit(kitNameInput);
        if (editingKit != null) {
            kit.setName(kitNameInput);
        }
        kit.clearItems();

        var mc = Minecraft.getInstance();
        var level = mc.level;
        var registryAccess = level != null ? level.registryAccess() : null;

        for (int slot = 0; slot <= 13; slot++) {
            ItemStack stack = kitSlots.get(slot);
            if (stack != null && !stack.isEmpty()) {
                kit.addItem(stack, registryAccess);
            }
        }

        boolean saved = KitPersistence.saveKit(kit);
        if (!saved) {
            Notification notification = Notification.builder(NotificationCategory.SYSTEM)
                .titleKey("devmod.kit.notification.save_failed.title")
                .messageKey("devmod.kit.notification.save_failed.message")
                .priority(NotificationPriority.HIGH)
                .displayDurationMs(3000)
                .build();
            ClientNotificationManager.INSTANCE.handleNotification(notification);
            playRemoveSound();
            return;
        }

        int itemCount = kit.getItemCount();
        LOGGER.info("[KitSelectionScreen] Saved kit: {} with {} items", kit.getName(), itemCount);
        if (onKitSaved != null) {
            onKitSaved.accept(kit);
        }
        Notification notification = Notification.builder(NotificationCategory.SYSTEM)
            .titleKey("devmod.kit.notification.saved.title")
            .messageKey("devmod.kit.notification.saved.message")
            .param("name", kit.getName())
            .param("count", itemCount)
            .priority(NotificationPriority.NORMAL)
            .displayDurationMs(2500)
            .build();
        ClientNotificationManager.INSTANCE.handleNotification(notification);

        closeNameDialog();
        playSuccessSound();
    }

    private void clearKit() {
        kitSlots.clear();
        selectedSlot = -1;
        playRemoveSound();
    }

    private void useKit() {
        List<ItemStack> items = new ArrayList<>();
        for (int slot = 0; slot <= 13; slot++) {
            ItemStack stack = kitSlots.get(slot);
            if (stack != null && !stack.isEmpty()) {
                items.add(stack.copy());
            }
        }

        if (items.isEmpty()) return;

        String name = kitNameInput.isEmpty() ? getDefaultKitNameSimple() : kitNameInput;
        KitManager.INSTANCE.setTemporaryKit(items, name);
        Notification notification = Notification.builder(NotificationCategory.SYSTEM)
            .titleKey("devmod.kit.notification.temporary.title")
            .messageKey("devmod.kit.notification.temporary.message")
            .param("name", name)
            .param("count", items.size())
            .priority(NotificationPriority.NORMAL)
            .displayDurationMs(2200)
            .build();
        ClientNotificationManager.INSTANCE.handleNotification(notification);

        if (onKitSelected != null) {
            onKitSelected.accept(items);
        }

        playSuccessSound();
        goBack();
    }

    private void goBack() {
        Minecraft.getInstance().setScreen(parentScreen);
    }

    private void playClickSound() {
        var mc = Minecraft.getInstance();
        mc.getSoundManager().play(Objects.requireNonNull(SimpleSoundInstance.forUI(Objects.requireNonNull(SoundEvents.UI_BUTTON_CLICK), 1.0f)));
    }

    private void playSuccessSound() {
        var mc = Minecraft.getInstance();
        mc.getSoundManager().play(Objects.requireNonNull(SimpleSoundInstance.forUI(Objects.requireNonNull(SoundEvents.PLAYER_LEVELUP), 1.5f, 0.5f)));
    }

    private void playRemoveSound() {
        var mc = Minecraft.getInstance();
        mc.getSoundManager().play(Objects.requireNonNull(SimpleSoundInstance.forUI(Objects.requireNonNull(SoundEvents.UI_STONECUTTER_TAKE_RESULT), 0.8f)));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record EnchantmentOption(Holder<Enchantment> holder, String displayName, int maxLevel) {}
}
