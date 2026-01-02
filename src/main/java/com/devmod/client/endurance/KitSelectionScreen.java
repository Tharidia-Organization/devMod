package com.devmod.client.endurance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
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
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.Enchantment;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.ui.editor.EditorStartTab;
import com.devmod.client.ui.editor.ItemEditorScreen;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.endurance.CustomKit;
import com.devmod.endurance.KitManager;
import com.devmod.endurance.KitPersistence;
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
    private static final int COLOR_ACCENT_PURPLE = 0xFFA371F7;  // Kit-specific purple (no token equivalent)
    private static final int COLOR_TEXT = DesignTokens.Text.PRIMARY;
    private static final int COLOR_TEXT_DIM = DesignTokens.Text.MUTED;
    private static final int COLOR_TEXT_INVERSE = DesignTokens.Text.INVERSE;
    private static final int COLOR_TEXT_WHITE = 0xFFFFFFFF;
    private static final int COLOR_BORDER = DesignTokens.Stroke.DEFAULT;

    // Button success variants
    private static final int COLOR_BTN_SUCCESS_HOVER = 0xFF2EA043;
    private static final int COLOR_BTN_SUCCESS_BORDER_HOVER = 0xFF3FB950;
    private static final int COLOR_BTN_SUCCESS_BORDER = 0xFF238636;

    @Immutable
    @FunctionalInterface
    private interface ItemStackFilter extends Predicate<ItemStack> {}

    // Categories with icons
    private enum Category {
        ALL("devmod.kit.category.all", "devmod.kit.category.all.tab", "◆", 0xFFE6EDF3, stack -> true),
        ARMOR("devmod.kit.category.armor", "devmod.kit.category.armor.tab", "🛡", 0xFF58A6FF, stack -> stack.getItem() instanceof ArmorItem),
        WEAPONS("devmod.kit.category.weapons", "devmod.kit.category.weapons.tab", "⚔", 0xFFF85149, stack -> stack.getItem() instanceof SwordItem ||
                                          stack.getItem() instanceof AxeItem ||
                                          stack.getItem() instanceof BowItem ||
                                          stack.getItem() instanceof CrossbowItem ||
                                          stack.getItem() instanceof TridentItem ||
                                          stack.getItem() instanceof MaceItem),
        TOOLS("devmod.kit.category.tools", "devmod.kit.category.tools.tab", "⛏", 0xFFD29922, stack -> (stack.getItem() instanceof TieredItem) &&
                                       !(stack.getItem() instanceof SwordItem) &&
                                       !(stack.getItem() instanceof AxeItem)),
        POTIONS("devmod.kit.category.potions", "devmod.kit.category.potions.tab", "🧪", 0xFFA371F7, stack -> stack.getItem() instanceof PotionItem ||
                                          stack.getItem() == Items.SPLASH_POTION ||
                                          stack.getItem() == Items.LINGERING_POTION),
        FOOD("devmod.kit.category.food", "devmod.kit.category.food.tab", "🍖", 0xFF3FB950, stack -> stack.has(Objects.requireNonNull(net.minecraft.core.component.DataComponents.FOOD))),
        COMBAT("devmod.kit.category.combat", "devmod.kit.category.combat.tab", "🏹", 0xFFFF7B72, stack -> stack.getItem() instanceof ShieldItem ||
                                         stack.getItem() == Items.ARROW ||
                                         stack.getItem() == Items.SPECTRAL_ARROW ||
                                         stack.getItem() == Items.TIPPED_ARROW ||
                                         stack.getItem() == Items.TOTEM_OF_UNDYING ||
                                         stack.getItem() == Items.GOLDEN_APPLE ||
                                         stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE ||
                                         stack.getItem() == Items.ENDER_PEARL ||
                                         stack.getItem() == Items.FIREWORK_ROCKET),
        BLOCKS("devmod.kit.category.blocks", "devmod.kit.category.blocks.tab", "▣", 0xFF79C0FF, stack -> stack.getItem() instanceof BlockItem);

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
    private int selectedSlot = -1;

    // Popup state
    private boolean showEnchantPopup = false;
    private int enchantSlot = -1;
    private List<EnchantmentOption> availableEnchants = new ArrayList<>();
    private int enchantScrollOffset = 0;

    private boolean showNameDialog = false;
    private String kitNameInput = "";
    @Nullable
    private EditBox kitNameBox;

    // Slot configuration
    private static final String[] SLOT_ICONS = {
        "⛑", "🛡", "👖", "👢", "🤚",
        "①", "②", "③", "④", "⑤", "⑥", "⑦", "⑧", "⑨"
    };

    // UI Components
    @Nullable
    private final Consumer<List<ItemStack>> onKitSelected;
    @Nullable
    private final Screen parentScreen;
    @Nullable
    private CustomKit editingKit;

    public KitSelectionScreen(@Nullable Screen parent, @Nullable Consumer<List<ItemStack>> onSelect) {
        super(I18n.screenTitle("kit_selection"));
        this.parentScreen = parent;
        this.onKitSelected = onSelect;
        this.editingKit = null;
    }

    public KitSelectionScreen(@Nullable Screen parent, @Nullable Consumer<List<ItemStack>> onSelect, @Nullable CustomKit existingKit) {
        super(I18n.screenTitle("kit_selection"));
        this.parentScreen = parent;
        this.onKitSelected = onSelect;
        this.editingKit = existingKit;
    }

    @Override
    protected void init() {
        super.init();
        loadAllItems();
        loadExistingKit();
        initSearchBox();
        filterItems();
    }

    private void loadExistingKit() {
        kitSlots.clear();
        CustomKit kit = editingKit;
        if (kit != null) {
            // Use full restoration to preserve attributes, durability, NBT, etc.
            var mc = Minecraft.getInstance();
            var registryAccess = mc.level != null ? mc.level.registryAccess() : null;
            List<ItemStack> items = kit.toItemStacks(registryAccess);
            int slot = 5;
            for (ItemStack stack : items) {
                if (!stack.isEmpty()) {
                    if (stack.getItem() instanceof ArmorItem armor) {
                        int armorSlot = switch (armor.getEquipmentSlot()) {
                            case HEAD -> 0;
                            case CHEST -> 1;
                            case LEGS -> 2;
                            case FEET -> 3;
                            default -> -1;
                        };
                        if (armorSlot >= 0 && !kitSlots.containsKey(armorSlot)) {
                            kitSlots.put(armorSlot, stack.copy());
                            continue;
                        }
                    }
                    if (stack.getItem() instanceof ShieldItem && !kitSlots.containsKey(4)) {
                        kitSlots.put(4, stack.copy());
                        continue;
                    }
                    while (slot <= 13 && kitSlots.containsKey(slot)) slot++;
                    if (slot <= 13) {
                        kitSlots.put(slot, stack.copy());
                        slot++;
                    }
                }
            }
            kitNameInput = kit.getName();
        }
    }

    private void initSearchBox() {
        var safeFont = Objects.requireNonNull(font);
        int searchWidth = 200;
        int searchX = PANEL_PADDING + 8;
        int searchY = TAB_HEIGHT + PANEL_PADDING + 36;
        final EditBox box = new EditBox(safeFont, searchX, searchY, searchWidth, 18, I18n.ui("search"));
        box.setHint(Objects.requireNonNull(I18n.translate("devmod.kit.search_items")));
        box.setBordered(true);
        box.setResponder(query -> {
            searchQuery = query;
            filterItems();
        });
        addRenderableWidget(box);
    }

    private void loadAllItems() {
        allItems.clear();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item != Items.AIR) {
                allItems.add(new ItemStack(Objects.requireNonNull(item)));
            }
        }
        LOGGER.debug("[KitSelectionScreen] Loaded {} items", allItems.size());
    }

    private void filterItems() {
        filteredItems.clear();
        String query = searchQuery.toLowerCase(Locale.ROOT).trim();

        for (ItemStack stack : allItems) {
            if (!selectedCategory.matches(stack)) continue;
            if (!query.isEmpty()) {
                String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
                String id = Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(stack.getItem())))
                    .toString()
                    .toLowerCase(Locale.ROOT);
                if (!name.contains(query) && !id.contains(query)) continue;
            }
            filteredItems.add(stack);
        }

        itemScrollOffset = 0;
        calculateMaxScroll();
    }

    private void calculateMaxScroll() {
        int browserWidth = (width / 2) - PANEL_PADDING * 3 - 16;
        int itemsPerRow = Math.max(1, browserWidth / (ITEM_SIZE + ITEM_MARGIN));
        int rows = (filteredItems.size() + itemsPerRow - 1) / itemsPerRow;
        int contentHeight = rows * (ITEM_SIZE + ITEM_MARGIN);
        int viewportHeight = height - TAB_HEIGHT - 140;
        itemMaxScroll = Math.max(0, contentHeight - viewportHeight);
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dark background
        graphics.fill(0, 0, width, height, COLOR_BG);

        renderHeader(graphics, mouseX, mouseY);
        renderItemBrowser(graphics, mouseX, mouseY);
        renderKitPanel(graphics, mouseX, mouseY);
        renderBottomBar(graphics, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);

        // Render popups last (on top)
        if (showEnchantPopup) {
            renderEnchantPopup(graphics, mouseX, mouseY);
        }
        if (showNameDialog) {
            renderNameDialog(graphics, mouseX, mouseY);
        }

        // Tooltips (after popups)
        if (!showEnchantPopup && !showNameDialog) {
            renderTooltips(graphics, mouseX, mouseY);
        }
    }

    private void renderHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        var safeFont = Objects.requireNonNull(font);

        // Header background
        graphics.fill(0, 0, width, TAB_HEIGHT, COLOR_PANEL_HEADER);
        graphics.fill(0, TAB_HEIGHT - 1, width, TAB_HEIGHT, COLOR_BORDER);

        // Title
        graphics.drawString(safeFont, I18n.translate("devmod.kit.header.title").getString(), PANEL_PADDING, 10, COLOR_TEXT);

        // Category tabs
        int tabX = 120;
        int tabW = 80;
        for (Category cat : Category.values()) {
            boolean selected = cat == selectedCategory;
            boolean hovered = mouseX >= tabX && mouseX < tabX + tabW && mouseY >= 4 && mouseY < TAB_HEIGHT - 4;

            int tabColor = selected ? cat.color : (hovered ? COLOR_ITEM_HOVER : COLOR_PANEL);
            graphics.fill(tabX, 4, tabX + tabW, TAB_HEIGHT - 4, tabColor);

            if (selected) {
                // Bottom accent line
                graphics.fill(tabX, TAB_HEIGHT - 4, tabX + tabW, TAB_HEIGHT - 2, cat.color);
            }

            String label = Objects.requireNonNull(cat.tabLabel());
            int textX = tabX + (tabW - safeFont.width(label)) / 2;
            graphics.drawString(safeFont, label, textX, 10, selected ? COLOR_TEXT_INVERSE : COLOR_TEXT);

            tabX += tabW + 4;
        }
    }

    private void renderItemBrowser(GuiGraphics graphics, int mouseX, int mouseY) {
        var safeFont = Objects.requireNonNull(font);

        int panelX = PANEL_PADDING;
        int panelY = TAB_HEIGHT + PANEL_PADDING;
        int panelW = (width / 2) - PANEL_PADDING * 2;
        int panelH = height - TAB_HEIGHT - PANEL_PADDING * 2 - 55;

        // Panel background
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, COLOR_PANEL);
        renderPanelBorder(graphics, panelX, panelY, panelW, panelH);

        // Panel header
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 28, COLOR_PANEL_HEADER);
        String headerText = I18n.translate("devmod.kit.header.items",
            selectedCategory.displayName(), filteredItems.size()).getString();
        graphics.drawString(safeFont, headerText, panelX + 10, panelY + 10, selectedCategory.color);

        // Item grid
        int gridX = panelX + 8;
        int gridY = panelY + 58;
        int gridW = panelW - 20;
        int gridH = panelH - 68;

        graphics.enableScissor(gridX, gridY, gridX + gridW, gridY + gridH);

        int itemsPerRow = Math.max(1, gridW / (ITEM_SIZE + ITEM_MARGIN));
        int y = gridY - itemScrollOffset;

        for (int i = 0; i < filteredItems.size(); i++) {
            int col = i % itemsPerRow;
            int row = i / itemsPerRow;
            int itemX = gridX + col * (ITEM_SIZE + ITEM_MARGIN);
            int itemY = y + row * (ITEM_SIZE + ITEM_MARGIN);

            if (itemY + ITEM_SIZE < gridY || itemY > gridY + gridH) continue;

            ItemStack stack = Objects.requireNonNull(filteredItems.get(i));
            boolean hovered = mouseX >= itemX && mouseX < itemX + ITEM_SIZE &&
                              mouseY >= itemY && mouseY < itemY + ITEM_SIZE;

            int bgColor = hovered ? COLOR_ITEM_HOVER : COLOR_ITEM_BG;
            graphics.fill(itemX, itemY, itemX + ITEM_SIZE, itemY + ITEM_SIZE, bgColor);

            if (hovered) {
                // Hover border
                graphics.fill(itemX, itemY, itemX + ITEM_SIZE, itemY + 1, COLOR_ACCENT);
                graphics.fill(itemX, itemY + ITEM_SIZE - 1, itemX + ITEM_SIZE, itemY + ITEM_SIZE, COLOR_ACCENT);
                graphics.fill(itemX, itemY, itemX + 1, itemY + ITEM_SIZE, COLOR_ACCENT);
                graphics.fill(itemX + ITEM_SIZE - 1, itemY, itemX + ITEM_SIZE, itemY + ITEM_SIZE, COLOR_ACCENT);
            }

            graphics.renderItem(stack, itemX + 1, itemY + 1);
        }

        graphics.disableScissor();

        // Scrollbar
        if (itemMaxScroll > 0) {
            int sbX = panelX + panelW - 8;
            int sbH = Math.max(20, (int) ((float) gridH / (gridH + itemMaxScroll) * gridH));
            int sbY = gridY + (int) ((float) itemScrollOffset / itemMaxScroll * (gridH - sbH));
            graphics.fill(sbX, gridY, sbX + 4, gridY + gridH, COLOR_ITEM_BG);
            graphics.fill(sbX, sbY, sbX + 4, sbY + sbH, COLOR_ACCENT);
        }
    }

    private void renderKitPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        var safeFont = Objects.requireNonNull(font);

        int panelX = width / 2 + PANEL_PADDING;
        int panelY = TAB_HEIGHT + PANEL_PADDING;
        int panelW = (width / 2) - PANEL_PADDING * 2;
        int panelH = height - TAB_HEIGHT - PANEL_PADDING * 2 - 55;

        // Panel background
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, COLOR_PANEL);
        renderPanelBorder(graphics, panelX, panelY, panelW, panelH);

        // Panel header
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 28, COLOR_PANEL_HEADER);
        int itemCount = (int) kitSlots.values().stream().filter(s -> !s.isEmpty()).count();
        String headerText = I18n.translate("devmod.kit.header.kit", itemCount).getString();
        graphics.drawString(safeFont, headerText, panelX + 10, panelY + 10, COLOR_ACCENT_ORANGE);

        int contentX = panelX + 12;
        int y = panelY + 40;

        // === CHARACTER PREVIEW ===
        int previewX = panelX + panelW - 80;
        int previewY = y + 80;
        renderCharacterPreview(graphics, previewX, previewY, mouseX, mouseY);

        // === EQUIPMENT SLOTS ===
        graphics.drawString(safeFont, I18n.translate("devmod.kit.section.equipment").getString(), contentX, y, COLOR_TEXT_DIM);
        y += 14;

        // Armor slots (vertical)
        for (int slot = 0; slot < 4; slot++) {
            renderSlot(graphics, contentX + slot * (SLOT_SIZE + 6), y, slot, mouseX, mouseY);
        }

        // Offhand
        renderSlot(graphics, contentX + 4 * (SLOT_SIZE + 6) + 10, y, 4, mouseX, mouseY);
        y += SLOT_SIZE + 16;

        // === HOTBAR SLOTS ===
        graphics.drawString(safeFont, I18n.translate("devmod.kit.section.hotbar").getString(), contentX, y, COLOR_TEXT_DIM);
        y += 14;

        for (int i = 0; i < 9; i++) {
            int slotX = contentX + i * (SLOT_SIZE + 4);
            renderSlot(graphics, slotX, y, 5 + i, mouseX, mouseY);
        }
        y += SLOT_SIZE + 20;

        // === QUICK PRESETS ===
        graphics.fill(contentX - 4, y, panelX + panelW - 12, y + 1, COLOR_BORDER);
        y += 12;

        graphics.drawString(safeFont, I18n.translate("devmod.kit.section.quick_presets").getString(), contentX, y, COLOR_TEXT_DIM);
        y += 14;

        String[][] presets = getQuickPresets();

        int px = contentX;
        for (int i = 0; i < presets.length; i++) {
            String presetName = Objects.requireNonNull(presets[i][0]);
            String presetIcon = Objects.requireNonNull(presets[i][1]);
            int pw = safeFont.width(presetName) + 20;
            boolean pHover = mouseX >= px && mouseX < px + pw && mouseY >= y && mouseY < y + 18;

            graphics.fill(px, y, px + pw, y + 18, pHover ? COLOR_ITEM_HOVER : COLOR_SLOT_EMPTY);
            renderBorder(graphics, px, y, pw, 18, pHover ? COLOR_ACCENT : COLOR_BORDER);

            graphics.drawString(safeFont, presetIcon + " " + presetName, px + 4, y + 5, COLOR_TEXT);
            px += pw + 6;
        }
        y += 28;

        // === INSTRUCTIONS ===
        graphics.fill(contentX - 4, y, panelX + panelW - 12, y + 1, COLOR_BORDER);
        y += 12;

        String[] instructions = getInstructionLines();

        for (String instr : instructions) {
            graphics.drawString(safeFont, instr, contentX, y, COLOR_TEXT_DIM);
            y += 11;
        }
    }

    private void renderCharacterPreview(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        var safeFont = Objects.requireNonNull(font);
        var mc = Minecraft.getInstance();
        Player player = mc.player;

        if (player == null) {
            graphics.drawString(safeFont, I18n.translate("devmod.kit.preview.unavailable").getString(),
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
        boolean hovered = mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE;
        boolean selected = selectedSlot == slot;

        ItemStack stack = kitSlots.getOrDefault(slot, ItemStack.EMPTY);
        boolean hasItem = !stack.isEmpty();

        // Slot background
        int bgColor = selected ? COLOR_SLOT_SELECTED : (hovered ? COLOR_ITEM_HOVER : (hasItem ? COLOR_SLOT_FILLED : COLOR_SLOT_EMPTY));
        graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, bgColor);

        // Border
        int borderColor = selected ? COLOR_ACCENT : (hovered ? COLOR_ACCENT : COLOR_BORDER);
        renderBorder(graphics, x, y, SLOT_SIZE, SLOT_SIZE, borderColor);

        // Render item or slot hint
        if (hasItem) {
            graphics.renderItem(stack, x + 3, y + 3);
            if (stack.getCount() > 1) {
                String count = Objects.requireNonNull(String.valueOf(stack.getCount()));
                graphics.drawString(safeFont, count, x + SLOT_SIZE - safeFont.width(count) - 1, y + SLOT_SIZE - 9, COLOR_TEXT_WHITE);
            }
            // Enchant indicator
            if (stack.isEnchanted()) {
                graphics.drawString(safeFont, "§d✦", x + 1, y + 1, COLOR_ACCENT_PURPLE);
            }
        } else {
            // Show slot icon
            String icon = Objects.requireNonNull(SLOT_ICONS[slot]);
            int iconX = x + (SLOT_SIZE - safeFont.width(icon)) / 2;
            int iconY = y + (SLOT_SIZE - 8) / 2;
            graphics.drawString(safeFont, Objects.requireNonNull("§8" + icon), iconX, iconY, COLOR_TEXT_DIM);
        }
    }

    private void renderBottomBar(GuiGraphics graphics, int mouseX, int mouseY) {
        var safeFont = Objects.requireNonNull(font);
        int barY = height - 50;

        // Bar background
        graphics.fill(0, barY, width, height, COLOR_PANEL_HEADER);
        graphics.fill(0, barY, width, barY + 1, COLOR_BORDER);

        int btnY = barY + 10;
        int btnH = 28;

        // Back button
        renderButton(graphics, PANEL_PADDING, btnY, 90, btnH,
            I18n.translate("devmod.kit.button.back").getString(), COLOR_PANEL,
            mouseX, mouseY);

        // Clear button
        renderButton(graphics, PANEL_PADDING + 100, btnY, 90, btnH,
            I18n.translate("devmod.kit.button.clear").getString(), COLOR_PANEL,
            mouseX, mouseY);

        // Right side buttons
        int rx = width - PANEL_PADDING;

        // Use Kit button (prominent)
        int useW = 120;
        boolean useHover = mouseX >= rx - useW && mouseX < rx && mouseY >= btnY && mouseY < btnY + btnH;
        graphics.fill(rx - useW, btnY, rx, btnY + btnH, useHover ? COLOR_BTN_SUCCESS_HOVER : COLOR_ACCENT_GREEN);
        renderBorder(graphics, rx - useW, btnY, useW, btnH, useHover ? COLOR_BTN_SUCCESS_BORDER_HOVER : COLOR_BTN_SUCCESS_BORDER);
        String useText = Objects.requireNonNull(I18n.translate("devmod.kit.button.use").getString());
        graphics.drawString(safeFont, useText, rx - useW + (useW - safeFont.width(useText)) / 2, btnY + 10, COLOR_TEXT_WHITE);

        // Save button
        int saveW = 110;
        boolean saveHover = mouseX >= rx - useW - saveW - 10 && mouseX < rx - useW - 10 && mouseY >= btnY && mouseY < btnY + btnH;
        graphics.fill(rx - useW - saveW - 10, btnY, rx - useW - 10, btnY + btnH, saveHover ? COLOR_ITEM_HOVER : COLOR_PANEL);
        renderBorder(graphics, rx - useW - saveW - 10, btnY, saveW, btnH, saveHover ? COLOR_ACCENT : COLOR_BORDER);
        String saveText = Objects.requireNonNull(I18n.translate("devmod.kit.button.save_preset").getString());
        graphics.drawString(safeFont, saveText, rx - useW - saveW - 10 + (saveW - safeFont.width(saveText)) / 2, btnY + 10, COLOR_TEXT);
    }

    private void renderButton(GuiGraphics graphics, int x, int y, int w, int h, String text, int bgColor,
                              int mouseX, int mouseY) {
        var safeFont = Objects.requireNonNull(font);
        String safeText = Objects.requireNonNull(text);
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;

        graphics.fill(x, y, x + w, y + h, hovered ? COLOR_ITEM_HOVER : bgColor);
        renderBorder(graphics, x, y, w, h, hovered ? COLOR_ACCENT : COLOR_BORDER);

        int textX = x + (w - safeFont.width(safeText)) / 2;
        graphics.drawString(safeFont, safeText, textX, y + (h - 8) / 2, COLOR_TEXT);
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

    private void renderEnchantPopup(GuiGraphics graphics, int mouseX, int mouseY) {
        var safeFont = Objects.requireNonNull(font);

        int popupW = 280;
        int popupH = 320;
        int popupX = (width - popupW) / 2;
        int popupY = (height - popupH) / 2;

        // Darken background
        graphics.fill(0, 0, width, height, 0xAA000000);

        // Popup background
        graphics.fill(popupX, popupY, popupX + popupW, popupY + popupH, COLOR_PANEL);
        renderBorder(graphics, popupX, popupY, popupW, popupH, COLOR_ACCENT_PURPLE);

        // Header
        graphics.fill(popupX, popupY, popupX + popupW, popupY + 30, COLOR_PANEL_HEADER);
        graphics.drawString(safeFont, I18n.translate("devmod.kit.popup.enchant_title").getString(),
            popupX + 10, popupY + 11, COLOR_TEXT);

        // Close button
        int closeX = popupX + popupW - 22;
        boolean closeHover = mouseX >= closeX && mouseX < closeX + 16 && mouseY >= popupY + 7 && mouseY < popupY + 23;
        graphics.drawString(safeFont, closeHover ? "§c✖" : "§7✖", closeX, popupY + 11, COLOR_TEXT);

        // Enchantment list
        int listY = popupY + 40;
        int listH = popupH - 80;

        graphics.enableScissor(popupX + 10, listY, popupX + popupW - 10, listY + listH);

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

            graphics.drawString(safeFont, opt.displayName, popupX + 16, ey + 8, COLOR_TEXT);

            // Level buttons
            int lvlX = popupX + popupW - 80;
            for (int lvl = 1; lvl <= opt.maxLevel && lvl <= 5; lvl++) {
                int btnX = lvlX + (lvl - 1) * 14;
                boolean lvlHover = mouseX >= btnX && mouseX < btnX + 12 && mouseY >= ey + 4 && mouseY < ey + 20;
                int lvlColor = lvlHover ? COLOR_ACCENT_PURPLE : COLOR_TEXT_DIM;
                graphics.drawString(safeFont, String.valueOf(lvl), btnX + 3, ey + 8, lvlColor);
            }

            ey += 26;
        }

        graphics.disableScissor();

        // Cancel button
        int btnY = popupY + popupH - 35;
        boolean cancelHover = mouseX >= popupX + popupW/2 - 40 && mouseX < popupX + popupW/2 + 40 &&
                             mouseY >= btnY && mouseY < btnY + 24;
        graphics.fill(popupX + popupW/2 - 40, btnY, popupX + popupW/2 + 40, btnY + 24, cancelHover ? COLOR_ITEM_HOVER : COLOR_PANEL);
        renderBorder(graphics, popupX + popupW/2 - 40, btnY, 80, 24, cancelHover ? COLOR_ACCENT : COLOR_BORDER);
        String cancelLabel = Objects.requireNonNull(I18n.ui("cancel").getString());
        graphics.drawString(safeFont, cancelLabel, popupX + popupW/2 - safeFont.width(cancelLabel) / 2, btnY + 8, COLOR_TEXT);
    }

    private void renderNameDialog(GuiGraphics graphics, int mouseX, int mouseY) {
        var safeFont = Objects.requireNonNull(font);

        int dialogW = 300;
        int dialogH = 140;
        int dialogX = (width - dialogW) / 2;
        int dialogY = (height - dialogH) / 2;

        // Darken background
        graphics.fill(0, 0, width, height, 0xAA000000);

        // Dialog background
        graphics.fill(dialogX, dialogY, dialogX + dialogW, dialogY + dialogH, COLOR_PANEL);
        renderBorder(graphics, dialogX, dialogY, dialogW, dialogH, COLOR_ACCENT_ORANGE);

        // Header
        graphics.fill(dialogX, dialogY, dialogX + dialogW, dialogY + 30, COLOR_PANEL_HEADER);
        graphics.drawString(safeFont, I18n.translate("devmod.kit.dialog.save_title").getString(),
            dialogX + 10, dialogY + 11, COLOR_TEXT);

        // Name input field
        EditBox nameBox = kitNameBox;
        if (nameBox == null) {
            nameBox = new EditBox(safeFont, dialogX + 20, dialogY + 50, dialogW - 40, 20, I18n.ui("kit_name"));
            nameBox.setValue(Objects.requireNonNull(kitNameInput));
            nameBox.setHint(Objects.requireNonNull(I18n.translate("devmod.kit.enter_name")));
            kitNameBox = nameBox;
            addRenderableWidget(nameBox);
        }

        nameBox.render(graphics, mouseX, mouseY, 0);

        // Buttons
        int btnY = dialogY + dialogH - 40;

        // Cancel
        boolean cancelHover = mouseX >= dialogX + 20 && mouseX < dialogX + 100 && mouseY >= btnY && mouseY < btnY + 26;
        graphics.fill(dialogX + 20, btnY, dialogX + 100, btnY + 26, cancelHover ? COLOR_ITEM_HOVER : COLOR_PANEL);
        renderBorder(graphics, dialogX + 20, btnY, 80, 26, cancelHover ? COLOR_ACCENT : COLOR_BORDER);
        String cancelLabel = Objects.requireNonNull(I18n.ui("cancel").getString());
        graphics.drawString(safeFont, cancelLabel,
            dialogX + 20 + (80 - safeFont.width(cancelLabel)) / 2, btnY + 9, COLOR_TEXT);

        // Save
        boolean saveHover = mouseX >= dialogX + dialogW - 100 && mouseX < dialogX + dialogW - 20 &&
                           mouseY >= btnY && mouseY < btnY + 26;
        graphics.fill(dialogX + dialogW - 100, btnY, dialogX + dialogW - 20, btnY + 26,
            saveHover ? COLOR_BTN_SUCCESS_HOVER : COLOR_ACCENT_GREEN);
        renderBorder(graphics, dialogX + dialogW - 100, btnY, 80, 26, saveHover ? COLOR_BTN_SUCCESS_BORDER_HOVER : COLOR_BTN_SUCCESS_BORDER);
        String saveLabel = "§l" + I18n.ui("save").getString();
        graphics.drawString(safeFont, saveLabel,
            dialogX + dialogW - 100 + (80 - safeFont.width(saveLabel)) / 2, btnY + 9, COLOR_TEXT_WHITE);
    }

    private void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        // Item browser tooltips
        int gridX = PANEL_PADDING + 8;
        int gridY = TAB_HEIGHT + PANEL_PADDING + 58;
        int gridW = (width / 2) - PANEL_PADDING * 2 - 20;
        int gridH = height - TAB_HEIGHT - 140 - 68;

        if (mouseX >= gridX && mouseX < gridX + gridW && mouseY >= gridY && mouseY < gridY + gridH) {
            int itemsPerRow = Math.max(1, gridW / (ITEM_SIZE + ITEM_MARGIN));
            int relX = mouseX - gridX;
            int relY = mouseY - gridY + itemScrollOffset;
            int col = relX / (ITEM_SIZE + ITEM_MARGIN);
            int row = relY / (ITEM_SIZE + ITEM_MARGIN);
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
        int panelX = width / 2 + PANEL_PADDING + 12;
        int y = TAB_HEIGHT + PANEL_PADDING + 54;

        // Armor + offhand row
        for (int slot = 0; slot < 5; slot++) {
            int slotX = panelX + (slot < 4 ? slot * (SLOT_SIZE + 6) : 4 * (SLOT_SIZE + 6) + 10);
            int slotY = y;

            if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                ItemStack stack = kitSlots.getOrDefault(slot, ItemStack.EMPTY);
                if (!stack.isEmpty()) {
                    graphics.renderTooltip(Objects.requireNonNull(font), stack, mouseX, mouseY);
                } else {
                    List<net.minecraft.network.chat.Component> tooltip = Objects.requireNonNull(List.of(
                        net.minecraft.network.chat.Component.literal("§7" + getSlotName(slot))));
                    graphics.renderTooltip(Objects.requireNonNull(font), tooltip, Objects.requireNonNull(Optional.empty()), mouseX, mouseY);
                }
                return;
            }
        }

        // Hotbar row
        int hotbarY = y + SLOT_SIZE + 30;
        for (int i = 0; i < 9; i++) {
            int slotX = panelX + i * (SLOT_SIZE + 4);
            int slotY = hotbarY;

            if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                ItemStack stack = kitSlots.getOrDefault(5 + i, Objects.requireNonNull(ItemStack.EMPTY));
                if (!stack.isEmpty()) {
                    graphics.renderTooltip(Objects.requireNonNull(font), stack, mouseX, mouseY);
                } else {
                    List<net.minecraft.network.chat.Component> tooltip = Objects.requireNonNull(List.of(
                        net.minecraft.network.chat.Component.literal("§7" + getSlotName(5 + i))));
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
            I18n.translate("devmod.kit.instructions.slot").getString()
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

        // Quick presets
        if (handlePresetClick((int) mouseX, (int) mouseY)) return true;

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleCategoryTabClick(int mouseX, int mouseY) {
        if (mouseY < 4 || mouseY >= TAB_HEIGHT - 4) return false;

        int tabX = 120;
        int tabW = 80;
        for (Category cat : Category.values()) {
            if (mouseX >= tabX && mouseX < tabX + tabW) {
                selectedCategory = cat;
                filterItems();
                playClickSound();
                return true;
            }
            tabX += tabW + 4;
        }
        return false;
    }

    private boolean handleBottomBarClick(int mouseX, int mouseY) {
        int barY = height - 50;
        int btnY = barY + 10;
        int btnH = 28;

        // Back button
        if (mouseX >= PANEL_PADDING && mouseX < PANEL_PADDING + 90 &&
            mouseY >= btnY && mouseY < btnY + btnH) {
            goBack();
            return true;
        }

        // Clear button
        if (mouseX >= PANEL_PADDING + 100 && mouseX < PANEL_PADDING + 190 &&
            mouseY >= btnY && mouseY < btnY + btnH) {
            clearKit();
            return true;
        }

        int rx = width - PANEL_PADDING;
        int useW = 120;
        int saveW = 110;

        // Use Kit button
        if (mouseX >= rx - useW && mouseX < rx && mouseY >= btnY && mouseY < btnY + btnH) {
            useKit();
            return true;
        }

        // Save button
        if (mouseX >= rx - useW - saveW - 10 && mouseX < rx - useW - 10 &&
            mouseY >= btnY && mouseY < btnY + btnH) {
            openNameDialog();
            return true;
        }

        return false;
    }

    private boolean handleItemBrowserClick(int mouseX, int mouseY) {
        int gridX = PANEL_PADDING + 8;
        int gridY = TAB_HEIGHT + PANEL_PADDING + 58;
        int gridW = (width / 2) - PANEL_PADDING * 2 - 20;
        int gridH = height - TAB_HEIGHT - 140 - 68;

        if (mouseX < gridX || mouseX >= gridX + gridW || mouseY < gridY || mouseY >= gridY + gridH) {
            return false;
        }

        int itemsPerRow = Math.max(1, gridW / (ITEM_SIZE + ITEM_MARGIN));
        int relX = mouseX - gridX;
        int relY = mouseY - gridY + itemScrollOffset;
        int col = relX / (ITEM_SIZE + ITEM_MARGIN);
        int row = relY / (ITEM_SIZE + ITEM_MARGIN);
        int index = row * itemsPerRow + col;

        if (index >= 0 && index < filteredItems.size()) {
            ItemStack stack = filteredItems.get(index).copy();

            if (hasShiftDown()) {
                stack.setCount(stack.getMaxStackSize());
            }

            if (selectedSlot >= 0) {
                kitSlots.put(selectedSlot, stack);
                selectedSlot = -1;
            } else {
                addItemToKit(stack);
            }

            playClickSound();
            return true;
        }
        return false;
    }

    private boolean handleSlotClick(int mouseX, int mouseY, int button) {
        int panelX = width / 2 + PANEL_PADDING + 12;
        int y = TAB_HEIGHT + PANEL_PADDING + 54;

        // Armor + offhand
        for (int slot = 0; slot < 5; slot++) {
            int slotX = panelX + (slot < 4 ? slot * (SLOT_SIZE + 6) : 4 * (SLOT_SIZE + 6) + 10);
            int slotY = y;

            if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                return handleSlotAction(slot, button);
            }
        }

        // Hotbar
        int hotbarY = y + SLOT_SIZE + 30;
        for (int i = 0; i < 9; i++) {
            int slotX = panelX + i * (SLOT_SIZE + 4);
            int slotY = hotbarY;

            if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
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

    private boolean handlePresetClick(int mouseX, int mouseY) {
        var safeFont = Objects.requireNonNull(font);
        int panelX = width / 2 + PANEL_PADDING + 12;
        int y = TAB_HEIGHT + PANEL_PADDING + 54 + SLOT_SIZE + 16 + 14 + SLOT_SIZE + 20 + 12 + 14;

        String[][] presets = getQuickPresets();

        int px = panelX;
        for (int i = 0; i < presets.length; i++) {
            int pw = safeFont.width(Objects.requireNonNull(presets[i][0])) + 20;
            if (mouseX >= px && mouseX < px + pw && mouseY >= y && mouseY < y + 18) {
                applyQuickSet(i);
                playSuccessSound();
                return true;
            }
            px += pw + 6;
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

        int gridX = PANEL_PADDING + 8;
        int gridY = TAB_HEIGHT + PANEL_PADDING + 58;
        int gridW = (width / 2) - PANEL_PADDING * 2 - 20;
        int gridH = height - TAB_HEIGHT - 140 - 68;

        if (mouseX >= gridX && mouseX < gridX + gridW && mouseY >= gridY && mouseY < gridY + gridH) {
            itemScrollOffset = Math.max(0, Math.min(itemMaxScroll, itemScrollOffset - (int) (scrollY * 24)));
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
        if (stack.getItem() instanceof ArmorItem armor) {
            int slot = switch (armor.getEquipmentSlot()) {
                case HEAD -> 0;
                case CHEST -> 1;
                case LEGS -> 2;
                case FEET -> 3;
                default -> -1;
            };
            if (slot >= 0) {
                kitSlots.put(slot, stack);
                return;
            }
        }
        if (stack.getItem() instanceof ShieldItem) {
            kitSlots.put(4, stack);
            return;
        }
        for (int slot = 5; slot <= 13; slot++) {
            if (!kitSlots.containsKey(slot) || kitSlots.get(slot).isEmpty()) {
                kitSlots.put(slot, stack);
                return;
            }
        }
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
                kitSlots.put(slot, editedStack);
                LOGGER.debug("Kit slot {} updated with edited item: {}", slot, editedStack.getHoverName().getString());
            }
        };

        // Use GENERAL tab as default - the editor will auto-detect the appropriate module
        EditorStartTab startTab = EditorStartTab.GENERAL;

        // Open the editor with callback support
        var mc = Minecraft.getInstance();
        mc.setScreen(new ItemEditorScreen(stack, startTab, this, onItemEdited));
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

        for (int slot = 0; slot <= 13; slot++) {
            ItemStack stack = kitSlots.get(slot);
            if (stack != null && !stack.isEmpty()) {
                kit.addItem(stack);
            }
        }

        KitPersistence.saveKit(kit);
        LOGGER.info("[KitSelectionScreen] Saved kit: {} with {} items", kit.getName(), kit.getItemCount());

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
