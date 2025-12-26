package com.devmod.client.ui.hub;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import com.devmod.client.testing.TestingSession;
import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.editor.core.UIConstants;
import com.devmod.testing.TestCase;
import com.devmod.util.I18n;

public class CategoryPanel implements HubPanel {

    private final int x, y, width, height;
    private final Font font;
    private final TestingHubState state;
    private final Consumer<String> onCategorySelected;
    private final Consumer<TestCase> onTestSelected;

    // UI State
    @Nullable
    private String expandedCategory = null;
    private EditBox searchBox;

    // Layout constants
    private static final int SEARCH_HEIGHT = 20;
    private static final int FILTER_ROW_HEIGHT = 16;
    private static final int CATEGORY_ROW_HEIGHT = 22;
    private static final int TEST_ROW_HEIGHT = 18;
    private static final int PADDING = 8;
    private static final int HEADER_HEIGHT = 18;

    // Scroll
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int listStartY = 0;  // Cached for bounds check
    private int listHeight = 0;  // Cached for bounds check

    public CategoryPanel(int x, int y, int width, int height, Font font,
                         TestingHubState state,
                         Consumer<String> onCategorySelected,
                         Consumer<TestCase> onTestSelected) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.font = Objects.requireNonNull(font, "font");
        this.state = state;
        this.onCategorySelected = onCategorySelected;
        this.onTestSelected = onTestSelected;

        // Initialize expanded category from state
        this.expandedCategory = state.getSelectedCategory();

        // Search box - creato ma non come widget (rendering manuale)
        initSearchBox();
    }

    private void initSearchBox() {
        @Nonnull Font safeFont = safeFont();
        Component searchLabel = Objects.requireNonNull(I18n.ui("search"), "searchLabel");
        searchBox = new EditBox(safeFont, x + PADDING, y + PADDING + HEADER_HEIGHT + 4,
            width - PADDING * 2, SEARCH_HEIGHT, searchLabel);
        Component searchHint = Objects.requireNonNull(I18n.translate("devmod.testing.search_tests"), "searchHint");
        searchBox.setHint(searchHint);
        searchBox.setMaxLength(50);
        @Nonnull String safeQuery = Objects.requireNonNull(
            Objects.requireNonNullElse(state.getSearchQuery(), ""),
            "searchQuery");
        searchBox.setValue(safeQuery);
        searchBox.setResponder(query -> state.setSearchQuery(query != null ? query : ""));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Background
        graphics.fill(x, y, x + width, y + height, UIConstants.Background.PANEL());

        // Right separator border
        graphics.fill(x + width - 1, y, x + width, y + height, UIConstants.Border.SEPARATOR());

        int contentY = y + PADDING;

        // Header
        contentY = HubSectionHeader.draw(graphics, safeFont(), "CATEGORIES", x + PADDING, contentY, HEADER_HEIGHT, 4);
        contentY += 4;

        // Search box
        searchBox.setX(x + PADDING);
        searchBox.setY(contentY);
        searchBox.render(graphics, mouseX, mouseY, partialTick);
        contentY += SEARCH_HEIGHT + 8;

        // Filtri
        contentY = renderFilters(graphics, contentY, mouseX, mouseY);
        contentY += 6;

        // Separator
        AxiomRenderer.drawSeparator(graphics, x + PADDING, contentY, width - PADDING * 2);
        contentY += 8;

        // Categories + test list - cache for bounds check in mouseScrolled
        this.listStartY = contentY;
        this.listHeight = y + height - contentY - PADDING;

        renderCategoryList(graphics, listStartY, listHeight, mouseX, mouseY);
    }

    private int renderFilters(GuiGraphics graphics, int startY, int mouseX, int mouseY) {
        @Nonnull Font safeFont = safeFont();
        int filterX = x + PADDING;
        int filterY = startY;

        // Render filters in one row
        TestCase.TestStatus[] statuses = { TestCase.TestStatus.PENDING, TestCase.TestStatus.PASSED, TestCase.TestStatus.FAILED };

        for (TestCase.TestStatus status : statuses) {
            boolean active = state.isFilterActive(status);
            int boxSize = 10;

            // Checkbox background
            int boxColor = active ? status.getColor() : UIConstants.Background.INPUT();
            graphics.fill(filterX, filterY + 2, filterX + boxSize, filterY + 2 + boxSize, boxColor);
            AxiomRenderer.drawBorder(graphics, filterX, filterY + 2, boxSize, boxSize, UIConstants.Border.DEFAULT());

            // Checkmark
            if (active) {
                graphics.drawString(safeFont, "v", filterX + 2, filterY + 2, UIConstants.Text.WHITE(), false);
            }

            // Abbreviated label
            @Nonnull String label = Objects.requireNonNull(
                Objects.requireNonNull(status.name(), "statusName").substring(0, 1),
                "label");
            graphics.drawString(safeFont, label, filterX + boxSize + 3, filterY + 3, UIConstants.Text.MUTED(), false);

            filterX += boxSize + safeFont.width(label) + 10;
        }

        return filterY + FILTER_ROW_HEIGHT;
    }

    private void renderCategoryList(GuiGraphics graphics, int startY, int listHeight, int mouseX, int mouseY) {
        Map<String, List<TestCase>> categories = getFilteredCategories();

        // Scissor for scrolling
        graphics.enableScissor(x, startY, x + width, startY + listHeight);

        int itemY = startY - scrollOffset;
        int totalHeight = 0;

        for (Map.Entry<String, List<TestCase>> entry : categories.entrySet()) {
            @Nonnull String category = Objects.requireNonNull(entry.getKey(), "category");
            List<TestCase> tests = entry.getValue();

            // Category row
            boolean catExpanded = category.equals(expandedCategory);
            boolean catHovered = isInBounds(mouseX, mouseY, x + PADDING, itemY, width - PADDING * 2, CATEGORY_ROW_HEIGHT)
                                && mouseY >= startY && mouseY < startY + listHeight;

            if (itemY + CATEGORY_ROW_HEIGHT > startY - 50 && itemY < startY + listHeight + 50) {
                renderCategoryRow(graphics, x + PADDING, itemY, width - PADDING * 2, category, tests, catExpanded, catHovered);
            }

            itemY += CATEGORY_ROW_HEIGHT;
            totalHeight += CATEGORY_ROW_HEIGHT;

            // Test rows if expanded
            if (catExpanded) {
                for (TestCase test : tests) {
                    boolean testHovered = isInBounds(mouseX, mouseY, x + PADDING + 12, itemY, width - PADDING * 2 - 12, TEST_ROW_HEIGHT)
                                        && mouseY >= startY && mouseY < startY + listHeight;
                    boolean testSelected = test.equals(state.getCurrentTest());

                    if (itemY + TEST_ROW_HEIGHT > startY - 50 && itemY < startY + listHeight + 50) {
                        renderTestRow(graphics, x + PADDING + 12, itemY, width - PADDING * 2 - 12, test, testHovered, testSelected);
                    }

                    itemY += TEST_ROW_HEIGHT;
                    totalHeight += TEST_ROW_HEIGHT;
                }
            }
        }

        maxScroll = Math.max(0, totalHeight - listHeight);

        graphics.disableScissor();

        // Scrollbar
        if (maxScroll > 0) {
            renderScrollbar(graphics, x + width - 6, startY, 4, listHeight);
        }
    }

    private void renderCategoryRow(GuiGraphics graphics, int rx, int ry, int rwidth, String category,
                                   List<TestCase> tests, boolean expanded, boolean hovered) {
        @Nonnull Font safeFont = safeFont();
        // Background hover
        if (hovered) {
            graphics.fill(rx - 2, ry, rx + rwidth + 2, ry + CATEGORY_ROW_HEIGHT - 2, UIConstants.Background.HOVER());
        }

        // Expand icon
        @Nonnull String icon = expanded ? "v" : ">";
        graphics.drawString(safeFont, icon, rx, ry + 6, UIConstants.Text.MUTED(), false);

        // Category name
        @Nonnull String safeCategory = Objects.requireNonNull(category, "category");
        graphics.drawString(safeFont, safeCategory, rx + 12, ry + 6, UIConstants.Text.PRIMARY(), false);

        // Count badge
        long passed = tests.stream().filter(t -> t.getStatus() == TestCase.TestStatus.PASSED).count();
        long total = tests.size();
        @Nonnull String count = Objects.requireNonNull(String.format("%d/%d", passed, total), "count");
        int countColor = passed == total ? UIConstants.Status.SUCCESS() : UIConstants.Text.MUTED();
        int countWidth = safeFont.width(count);
        graphics.drawString(safeFont, count, rx + rwidth - countWidth, ry + 6, countColor, false);
    }

    private void renderTestRow(GuiGraphics graphics, int rx, int ry, int rwidth, TestCase test,
                               boolean hovered, boolean selected) {
        @Nonnull Font safeFont = safeFont();
        // Background
        if (selected) {
            graphics.fill(rx - 2, ry, rx + rwidth + 2, ry + TEST_ROW_HEIGHT - 2, UIConstants.Background.ACTIVE());
        } else if (hovered) {
            graphics.fill(rx - 2, ry, rx + rwidth + 2, ry + TEST_ROW_HEIGHT - 2, UIConstants.Background.HOVER());
        }

        // Status dot
        int dotColor = test.getStatus().getColor();
        graphics.fill(rx, ry + 5, rx + 6, ry + 11, dotColor);

        // Test name (truncated - keep at least 6 chars for readability)
        @Nonnull String name = Objects.requireNonNull(
            Objects.requireNonNullElse(test.getName(), ""),
            "testName");
        int maxNameWidth = rwidth - 14;
        if (safeFont.width(name) > maxNameWidth) {
            String ellipsis = "...";
            int minChars = Math.min(6, name.length());
            while (safeFont.width(name + ellipsis) > maxNameWidth && name.length() > minChars) {
                name = Objects.requireNonNull(name.substring(0, name.length() - 1), "name");
            }
            name += ellipsis;
        }

        int textColor = selected ? UIConstants.Text.ACCENT() : UIConstants.Text.SECONDARY();
        graphics.drawString(safeFont, name, rx + 10, ry + 4, textColor, false);
    }

    private void renderScrollbar(GuiGraphics graphics, int sx, int sy, int swidth, int sheight) {
        // Track
        graphics.fill(sx, sy, sx + swidth, sy + sheight, UIConstants.Background.INPUT());

        // Thumb
        float viewRatio = (float) sheight / (sheight + maxScroll);
        int thumbHeight = Math.max(20, (int)(sheight * viewRatio));
        float scrollRatio = (float) scrollOffset / maxScroll;
        int thumbY = sy + (int)((sheight - thumbHeight) * scrollRatio);

        graphics.fill(sx, thumbY, sx + swidth, thumbY + thumbHeight, UIConstants.Border.DEFAULT());
    }

    private Map<String, List<TestCase>> getFilteredCategories() {
        Map<String, List<TestCase>> all = TestingSession.INSTANCE.getCategorizedTests();
        Map<String, List<TestCase>> filtered = new LinkedHashMap<>();

        String query = Objects.requireNonNullElse(state.getSearchQuery(), "").toLowerCase();
        Set<TestCase.TestStatus> activeFilters = state.getActiveFilters();

        for (Map.Entry<String, List<TestCase>> entry : all.entrySet()) {
            List<TestCase> tests = entry.getValue().stream()
                .filter(t -> activeFilters.contains(t.getStatus()))
                .filter(t -> query.isEmpty() ||
                            Objects.requireNonNullElse(t.getName(), "").toLowerCase().contains(query) ||
                            Objects.requireNonNullElse(t.getDescription(), "").toLowerCase().contains(query))
                .toList();

            if (!tests.isEmpty()) {
                filtered.put(entry.getKey(), new ArrayList<>(tests));
            }
        }

        return filtered;
    }

    // === INPUT HANDLING ===

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;

        // Search box click
        if (searchBox.isMouseOver(mouseX, mouseY)) {
            searchBox.setFocused(true);
            return true;
        }
        searchBox.setFocused(false);

        // Filtri click
        if (handleFilterClick(mx, my)) {
            return true;
        }

        // Category/Test list click
        return handleListClick(mx, my);
    }

    private boolean handleFilterClick(int mx, int my) {
        @Nonnull Font safeFont = safeFont();
        int filterY = y + PADDING + HEADER_HEIGHT + 4 + SEARCH_HEIGHT + 8;
        int filterX = x + PADDING;

        if (my < filterY || my > filterY + FILTER_ROW_HEIGHT) return false;

        TestCase.TestStatus[] statuses = { TestCase.TestStatus.PENDING, TestCase.TestStatus.PASSED, TestCase.TestStatus.FAILED };

        for (TestCase.TestStatus status : statuses) {
            int boxSize = 10;
            @Nonnull String label = Objects.requireNonNull(
                Objects.requireNonNull(status.name(), "statusName").substring(0, 1),
                "label");
            int itemWidth = boxSize + safeFont.width(label) + 10;

            if (mx >= filterX && mx < filterX + itemWidth) {
                state.toggleFilter(status);
                return true;
            }

            filterX += itemWidth;
        }

        return false;
    }

    private boolean handleListClick(int mx, int my) {
        int listStartY = y + PADDING + HEADER_HEIGHT + 4 + SEARCH_HEIGHT + 8 + FILTER_ROW_HEIGHT + 6 + 8;
        int listHeight = y + height - listStartY - PADDING;

        if (my < listStartY || my > listStartY + listHeight) return false;

        Map<String, List<TestCase>> categories = getFilteredCategories();
        int itemY = listStartY - scrollOffset;

        for (Map.Entry<String, List<TestCase>> entry : categories.entrySet()) {
            @Nonnull String category = Objects.requireNonNull(entry.getKey(), "category");
            List<TestCase> tests = entry.getValue();

            // Click on category
            if (my >= itemY && my < itemY + CATEGORY_ROW_HEIGHT) {
                if (category.equals(expandedCategory)) {
                    expandedCategory = null;
                } else {
                    expandedCategory = category;
                    onCategorySelected.accept(category);
                }
                return true;
            }
            itemY += CATEGORY_ROW_HEIGHT;

            // Click on test (if expanded)
            if (category.equals(expandedCategory)) {
                for (TestCase test : tests) {
                    if (my >= itemY && my < itemY + TEST_ROW_HEIGHT) {
                        onTestSelected.accept(test);
                        return true;
                    }
                    itemY += TEST_ROW_HEIGHT;
                }
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // FIX: Only handle scroll if mouse is over the category list area
        if (mouseX < x || mouseX > x + width ||
            mouseY < listStartY || mouseY > listStartY + listHeight) {
            return false;
        }

        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(scrollY * 20)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return searchBox.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return searchBox.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private boolean isInBounds(int mx, int my, int bx, int by, int bw, int bh) {
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }

    @Nonnull
    private Font safeFont() {
        return Objects.requireNonNull(font, "font");
    }

    @Override
    public void refresh() {
        // Force list refresh
    }

    @Override
    public int getX() { return x; }
    @Override
    public int getY() { return y; }
    @Override
    public int getWidth() { return width; }
    @Override
    public int getHeight() { return height; }
}
