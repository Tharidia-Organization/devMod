package com.devmod.client.ui.editor.components;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.search.ItemSearchQuery;
import com.devmod.client.ui.editor.core.BaseOverlay;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.editor.core.ScaledCoord;

public class ItemPickerOverlay extends BaseOverlay {

    // ═══════════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════════

    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_HEIGHT = 320;
    private static final int ITEM_SIZE = 20;
    private static final int HEADER_HEIGHT = 50;
    private static final int TAB_HEIGHT = 24;

    // ═══════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════

    private String searchQuery = "";
    private int scrollOffset = 0;
    private int hoveredIndex = -1;
    private boolean searchFocused = true;
    private int cursorBlink = 0;

    // Mode: 0 = Items, 1 = Tags
    private int currentTab = 0;

    // Cached results
    private List<ItemStack> filteredItems = new ArrayList<>();
    private List<TagKey<Item>> filteredTags = new ArrayList<>();
    private boolean filterDirty = true;

    // Callback
    private @Nullable Consumer<ItemStack> onItemSelected;
    private @Nullable Consumer<TagKey<Item>> onTagSelected;

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public ItemPickerOverlay() {
        // Pre-populate items on first show
    }

    // ═══════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Set callback for item selection.
     */
    public ItemPickerOverlay onItemSelected(@Nullable Consumer<ItemStack> callback) {
        this.onItemSelected = callback;
        return this;
    }

    /**
     * Set callback for tag selection.
     */
    public ItemPickerOverlay onTagSelected(@Nullable Consumer<TagKey<Item>> callback) {
        this.onTagSelected = callback;
        return this;
    }

    @Override
    public void show() {
        super.show();
        searchQuery = "";
        scrollOffset = 0;
        searchFocused = true;
        filterDirty = true;
        cursorBlink = 0;
    }

    // ═══════════════════════════════════════════════════════════════
    // FILTERING
    // ═══════════════════════════════════════════════════════════════

    private void updateFilter() {
        if (!filterDirty) return;
        filterDirty = false;

        String safeQuery = Objects.requireNonNull(searchQuery, "searchQuery");

        // Parse query with prefix support (@mod, #tag, $tooltip)
        ItemSearchQuery parsedQuery = ItemSearchQuery.parse(safeQuery);

        if (currentTab == 0) {
            // Filter items using advanced search
            filteredItems.clear();

            BuiltInRegistries.ITEM.forEach(item -> {
                Item safeItem = Objects.requireNonNull(item, "item");
                if (safeItem == Items.AIR) return;

                ItemStack stack = new ItemStack(safeItem);

                if (parsedQuery.matches(stack)) {
                    filteredItems.add(stack);
                }
            });

            // Sort by name
            filteredItems.sort((a, b) -> a.getHoverName().getString()
                .compareToIgnoreCase(b.getHoverName().getString()));

        } else {
            // Filter tags (use raw query for simple tag search)
            filteredTags.clear();
            String tagQuery = safeQuery.toLowerCase(Locale.ROOT).trim();

            // Get common item tags
            List<TagKey<Item>> allTags = getCommonItemTags();

            for (TagKey<Item> tag : allTags) {
                String tagId = tag.location().toString().toLowerCase(Locale.ROOT);
                if (tagQuery.isEmpty() || tagId.contains(tagQuery)) {
                    filteredTags.add(tag);
                }
            }

            // Sort by tag name
            filteredTags.sort((a, b) -> a.location().toString()
                .compareToIgnoreCase(b.location().toString()));
        }

        // Reset scroll
        scrollOffset = 0;
    }

    private List<TagKey<Item>> getCommonItemTags() {
        List<TagKey<Item>> tags = new ArrayList<>();

        // Add common vanilla tags
        tags.add(ItemTags.PLANKS);
        tags.add(ItemTags.LOGS);
        tags.add(ItemTags.WOOL);
        tags.add(ItemTags.STONE_CRAFTING_MATERIALS);
        tags.add(ItemTags.STONE_TOOL_MATERIALS);
        tags.add(ItemTags.COALS);
        tags.add(ItemTags.IRON_ORES);
        tags.add(ItemTags.GOLD_ORES);
        tags.add(ItemTags.DIAMOND_ORES);
        tags.add(ItemTags.EMERALD_ORES);
        tags.add(ItemTags.COPPER_ORES);
        tags.add(ItemTags.BEACON_PAYMENT_ITEMS);
        tags.add(ItemTags.SWORDS);
        tags.add(ItemTags.AXES);
        tags.add(ItemTags.PICKAXES);
        tags.add(ItemTags.SHOVELS);
        tags.add(ItemTags.HOES);
        tags.add(ItemTags.HEAD_ARMOR);
        tags.add(ItemTags.CHEST_ARMOR);
        tags.add(ItemTags.LEG_ARMOR);
        tags.add(ItemTags.FOOT_ARMOR);

        return tags;
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDERING
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected int getPanelWidth() {
        return PANEL_WIDTH;
    }

    @Override
    protected int getPanelHeight() {
        return PANEL_HEIGHT;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, Font font,
                                  int x, int y, int width, int height,
                                  int mouseX, int mouseY) {
        Font safeFont = Objects.requireNonNull(font, "font");
        GuiGraphics safeGraphics = Objects.requireNonNull(graphics, "graphics");
        String safeQueryRender = Objects.requireNonNull(searchQuery, "searchQuery");

        updateFilter();
        cursorBlink++;

        int padding = ScaledCoord.scaleDim(8);
        int innerX = x + padding;
        int innerY = y + padding;
        int innerW = width - padding * 2;

        // === Title ===
        safeGraphics.drawString(safeFont, "Select Item", innerX, innerY, DesignTokens.Text.TITLE(), false);
        innerY += ScaledCoord.scaleDim(14);

        // === Search box ===
        int searchH = ScaledCoord.scaleDim(18);
        int searchBg = searchFocused ? DesignTokens.Background.ACTIVE() : DesignTokens.Background.INPUT();
        safeGraphics.fill(innerX, innerY, innerX + innerW, innerY + searchH, searchBg);
        AxiomRenderer.drawBorder(safeGraphics, innerX, innerY, innerW, searchH,
            searchFocused ? DesignTokens.Border.ACCENT() : DesignTokens.Border.DEFAULT());

        // Search text with hint for search syntax
        String placeholder = "Search... @mod #tag $tooltip";
        String displayText = safeQueryRender.isEmpty() && !searchFocused ? placeholder : safeQueryRender;
        int textColor = safeQueryRender.isEmpty() && !searchFocused ? DesignTokens.Text.MUTED() : DesignTokens.Text.PRIMARY();
        safeGraphics.drawString(safeFont, displayText, innerX + 4, innerY + 5, textColor, false);

        // Cursor
        if (searchFocused && (cursorBlink / 15) % 2 == 0) {
            int cursorX = innerX + 4 + safeFont.width(safeQueryRender);
            safeGraphics.fill(cursorX, innerY + 3, cursorX + 1, innerY + searchH - 3, DesignTokens.Text.PRIMARY());
        }

        innerY += searchH + ScaledCoord.scaleDim(6);

        // === Tabs ===
        int tabW = innerW / 2;
        int tabH = ScaledCoord.scaleDim(TAB_HEIGHT);

        // Items tab
        boolean itemsHover = mouseX >= innerX && mouseX < innerX + tabW &&
                            mouseY >= innerY && mouseY < innerY + tabH;
        int itemsBg = currentTab == 0 ? DesignTokens.Background.ACTIVE() :
                     (itemsHover ? DesignTokens.Background.HOVER() : DesignTokens.Background.PANEL());
        safeGraphics.fill(innerX, innerY, innerX + tabW, innerY + tabH, itemsBg);
        safeGraphics.drawCenteredString(safeFont, "Items", innerX + tabW / 2, innerY + (tabH - 8) / 2,
            currentTab == 0 ? DesignTokens.Text.PRIMARY() : DesignTokens.Text.SECONDARY());

        // Tags tab
        boolean tagsHover = mouseX >= innerX + tabW && mouseX < innerX + innerW &&
                           mouseY >= innerY && mouseY < innerY + tabH;
        int tagsBg = currentTab == 1 ? DesignTokens.Background.ACTIVE() :
                    (tagsHover ? DesignTokens.Background.HOVER() : DesignTokens.Background.PANEL());
        safeGraphics.fill(innerX + tabW, innerY, innerX + innerW, innerY + tabH, tagsBg);
        safeGraphics.drawCenteredString(safeFont, "Tags", innerX + tabW + tabW / 2, innerY + (tabH - 8) / 2,
            currentTab == 1 ? DesignTokens.Text.PRIMARY() : DesignTokens.Text.SECONDARY());

        innerY += tabH + ScaledCoord.scaleDim(4);

        // === Grid area ===
        int gridH = height - (innerY - y) - padding;
        int scaledItemSize = ScaledCoord.scaleDim(ITEM_SIZE);
        int itemsPerRow = Math.max(1, innerW / scaledItemSize);
        int visibleRows = gridH / scaledItemSize;

        // Render items/tags grid
        hoveredIndex = -1;

        if (currentTab == 0) {
            renderItemsGrid(safeGraphics, safeFont, innerX, innerY, innerW, gridH,
                           scaledItemSize, itemsPerRow, visibleRows, mouseX, mouseY);
        } else {
            renderTagsGrid(safeGraphics, safeFont, innerX, innerY, innerW, gridH,
                          mouseX, mouseY);
        }

        // === Footer info ===
        int count = currentTab == 0 ? filteredItems.size() : filteredTags.size();
        String info = count + (currentTab == 0 ? " items" : " tags");
        safeGraphics.drawString(safeFont, info, x + width - padding - safeFont.width(info),
            y + height - padding - 8, DesignTokens.Text.MUTED(), false);
    }

    private void renderItemsGrid(GuiGraphics graphics, Font font,
                                  int x, int y, int w, int h,
                                  int itemSize, int perRow, int visibleRows,
                                  int mouseX, int mouseY) {
        GuiGraphics safeGraphics = Objects.requireNonNull(graphics, "graphics");
        Font safeFont = Objects.requireNonNull(font, "font");
        int totalRows = (filteredItems.size() + perRow - 1) / perRow;
        int maxScroll = Math.max(0, totalRows - visibleRows);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        int startIndex = scrollOffset * perRow;

        for (int row = 0; row < visibleRows; row++) {
            for (int col = 0; col < perRow; col++) {
                int index = startIndex + row * perRow + col;
                if (index >= filteredItems.size()) break;

                int cellX = x + col * itemSize;
                int cellY = y + row * itemSize;

                ItemStack stack = Objects.requireNonNull(filteredItems.get(index), "stack");

                // Hover detection
                boolean hovered = mouseX >= cellX && mouseX < cellX + itemSize &&
                                 mouseY >= cellY && mouseY < cellY + itemSize;
                if (hovered) {
                    hoveredIndex = index;
                    safeGraphics.fill(cellX, cellY, cellX + itemSize, cellY + itemSize,
                        DesignTokens.Background.HOVER());
                }

                // Render item
                int itemPad = (itemSize - 16) / 2;
                safeGraphics.renderItem(stack, cellX + itemPad, cellY + itemPad);
            }
        }

        // Render tooltip for hovered item
        if (hoveredIndex >= 0 && hoveredIndex < filteredItems.size()) {
            ItemStack hovered = Objects.requireNonNull(filteredItems.get(hoveredIndex), "hovered stack");
            safeGraphics.renderTooltip(safeFont, hovered, mouseX, mouseY);
        }

        // Scrollbar
        if (totalRows > visibleRows) {
            int scrollbarX = x + w - 4;
            int scrollbarH = h;
            float scrollProgress = (float) scrollOffset / maxScroll;
            int thumbH = Math.max(20, scrollbarH * visibleRows / totalRows);
            int thumbY = y + (int) ((scrollbarH - thumbH) * scrollProgress);

            safeGraphics.fill(scrollbarX, y, scrollbarX + 3, y + scrollbarH, DesignTokens.Background.INPUT());
            safeGraphics.fill(scrollbarX, thumbY, scrollbarX + 3, thumbY + thumbH, DesignTokens.Border.ACCENT());
        }
    }

    private void renderTagsGrid(GuiGraphics graphics, Font font,
                                int x, int y, int w, int h,
                                int mouseX, int mouseY) {
        GuiGraphics safeGraphics = Objects.requireNonNull(graphics, "graphics");
        Font safeFont = Objects.requireNonNull(font, "font");
        // Tags are rendered as a list (one per row) since they need more space for text
        int rowHeight = ScaledCoord.scaleDim(22);
        int maxVisible = h / rowHeight;
        int maxScroll = Math.max(0, filteredTags.size() - maxVisible);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        for (int i = 0; i < maxVisible && scrollOffset + i < filteredTags.size(); i++) {
            int index = scrollOffset + i;
            TagKey<Item> tag = Objects.requireNonNull(filteredTags.get(index), "tag");

            int rowY = y + i * rowHeight;

            // Hover detection
            boolean hovered = mouseX >= x && mouseX < x + w &&
                             mouseY >= rowY && mouseY < rowY + rowHeight;
            if (hovered) {
                hoveredIndex = index;
                safeGraphics.fill(x, rowY, x + w - 6, rowY + rowHeight, DesignTokens.Background.HOVER());
            }

            // Tag icon (# symbol)
            safeGraphics.drawString(safeFont, "#", x + 4, rowY + (rowHeight - 8) / 2,
                DesignTokens.Accent.ORANGE(), false);

            // Tag name
            String tagName = tag.location().toString();
            if (tagName.startsWith("minecraft:")) {
                tagName = tagName.substring("minecraft:".length());
            }
            safeGraphics.drawString(safeFont, tagName, x + 14, rowY + (rowHeight - 8) / 2,
                DesignTokens.Text.PRIMARY(), false);

            // Item count
            int itemCount = getTagItemCount(tag);
            String countStr = "(" + itemCount + ")";
            safeGraphics.drawString(safeFont, countStr, x + w - 10 - safeFont.width(countStr),
                rowY + (rowHeight - 8) / 2, DesignTokens.Text.MUTED(), false);
        }

        // Tooltip for hovered tag
        if (hoveredIndex >= 0 && hoveredIndex < filteredTags.size()) {
            TagKey<Item> tag = Objects.requireNonNull(filteredTags.get(hoveredIndex), "tooltip tag");
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("#" + tag.location()));
            tooltip.add(Component.literal(getTagItemCount(tag) + " items in tag")
                .withStyle(s -> s.withColor(DesignTokens.Neutral.N550 & DesignTokens.Mask.RGB)));

            // Show first few items
            int count = 0;
            for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                if (count >= 5) {
                    tooltip.add(Component.literal("...").withStyle(s -> s.withColor(DesignTokens.Neutral.N650 & DesignTokens.Mask.RGB)));
                    break;
                }
                tooltip.add(Component.literal("  " + holder.value().getDescription().getString())
                    .withStyle(s -> s.withColor(DesignTokens.Neutral.N500 & DesignTokens.Mask.RGB)));
                count++;
            }

            safeGraphics.renderComponentTooltip(safeFont, tooltip, mouseX, mouseY);
        }

        // Scrollbar
        if (filteredTags.size() > maxVisible) {
            int scrollbarX = x + w - 4;
            int scrollbarH = h;
            float scrollProgress = maxScroll > 0 ? (float) scrollOffset / maxScroll : 0;
            int thumbH = Math.max(20, scrollbarH * maxVisible / filteredTags.size());
            int thumbY = y + (int) ((scrollbarH - thumbH) * scrollProgress);

            safeGraphics.fill(scrollbarX, y, scrollbarX + 3, y + scrollbarH, DesignTokens.Background.INPUT());
            safeGraphics.fill(scrollbarX, thumbY, scrollbarX + 3, thumbY + thumbH, DesignTokens.Border.ACCENT());
        }
    }

    private int getTagItemCount(TagKey<Item> tag) {
        Objects.requireNonNull(tag, "tag");
        int count = 0;
        for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
            if (holder != null) {
                count++;
            }
        }
        return count;
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected boolean handleMouseClicked(double mouseX, double mouseY,
                                          int panelX, int panelY, int panelW, int panelH) {
        int padding = ScaledCoord.scaleDim(8);
        int innerX = panelX + padding;
        int innerY = panelY + padding + ScaledCoord.scaleDim(14); // After title

        // Search box click
        int searchH = ScaledCoord.scaleDim(18);
        int innerW = panelW - padding * 2;
        if (mouseX >= innerX && mouseX < innerX + innerW &&
            mouseY >= innerY && mouseY < innerY + searchH) {
            searchFocused = true;
            return true;
        }
        innerY += searchH + ScaledCoord.scaleDim(6);

        // Tab clicks
        int tabW = innerW / 2;
        int tabH = ScaledCoord.scaleDim(TAB_HEIGHT);
        if (mouseY >= innerY && mouseY < innerY + tabH) {
            if (mouseX >= innerX && mouseX < innerX + tabW) {
                currentTab = 0;
                filterDirty = true;
                scrollOffset = 0;
                return true;
            } else if (mouseX >= innerX + tabW && mouseX < innerX + innerW) {
                currentTab = 1;
                filterDirty = true;
                scrollOffset = 0;
                return true;
            }
        }
        innerY += tabH + ScaledCoord.scaleDim(4);

        // Grid click - select item/tag
        if (hoveredIndex >= 0) {
            if (currentTab == 0 && hoveredIndex < filteredItems.size()) {
                ItemStack selected = Objects.requireNonNull(filteredItems.get(hoveredIndex), "selected item");
                emitItemSelected(selected);
                hide();
                return true;
            } else if (currentTab == 1 && hoveredIndex < filteredTags.size()) {
                TagKey<Item> selected = Objects.requireNonNull(filteredTags.get(hoveredIndex), "selected tag");
                emitTagSelected(selected);
                hide();
                return true;
            }
        }

        // Unfocus search if clicking elsewhere
        searchFocused = false;
        return true;
    }

    @Override
    protected boolean handleMouseScrolled(double mouseX, double mouseY, double scrollDelta,
                                           int panelX, int panelY, int panelW, int panelH) {
        scrollOffset -= (int) scrollDelta;
        scrollOffset = Math.max(0, scrollOffset);

        // Clamp to max
        int maxScroll;
        if (currentTab == 0) {
            int perRow = Math.max(1, (panelW - ScaledCoord.scaleDim(16)) / ScaledCoord.scaleDim(ITEM_SIZE));
            int totalRows = (filteredItems.size() + perRow - 1) / perRow;
            int visibleRows = (panelH - ScaledCoord.scaleDim(HEADER_HEIGHT + TAB_HEIGHT + 20)) / ScaledCoord.scaleDim(ITEM_SIZE);
            maxScroll = Math.max(0, totalRows - visibleRows);
        } else {
            int rowH = ScaledCoord.scaleDim(22);
            int visible = (panelH - ScaledCoord.scaleDim(HEADER_HEIGHT + TAB_HEIGHT + 20)) / rowH;
            maxScroll = Math.max(0, filteredTags.size() - visible);
        }
        scrollOffset = Math.min(scrollOffset, maxScroll);

        return true;
    }

    @Override
    protected boolean handleKeyPressed(int keyCode) {
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                filterDirty = true;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                // Select first result on enter
                if (currentTab == 0 && !filteredItems.isEmpty()) {
                    emitItemSelected(Objects.requireNonNull(filteredItems.get(0), "first item"));
                    hide();
                } else if (currentTab == 1 && !filteredTags.isEmpty()) {
                    emitTagSelected(Objects.requireNonNull(filteredTags.get(0), "first tag"));
                    hide();
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_TAB) {
                // Switch tabs
                currentTab = (currentTab + 1) % 2;
                filterDirty = true;
                scrollOffset = 0;
                return true;
            }
        }
        return true; // Consume all keys when visible
    }

    @Override
    protected boolean handleCharTyped(char chr, int modifiers) {
        // Allow: letters, digits, underscore, colon, space, and search prefixes (@, #, $)
        if (searchFocused && (Character.isLetterOrDigit(chr) || chr == '_' || chr == ':' || chr == ' '
                || chr == '@' || chr == '#' || chr == '$' || chr == '.' || chr == '-')) {
            searchQuery += chr;
            filterDirty = true;
            return true;
        }
        return false;
    }

    private void emitItemSelected(ItemStack selected) {
        Consumer<ItemStack> callback = onItemSelected;
        if (callback != null) {
            callback.accept(selected);
        }
    }

    private void emitTagSelected(TagKey<Item> selected) {
        Consumer<TagKey<Item>> callback = onTagSelected;
        if (callback != null) {
            callback.accept(selected);
        }
    }
}
