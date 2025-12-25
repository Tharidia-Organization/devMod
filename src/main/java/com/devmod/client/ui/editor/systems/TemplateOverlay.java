package com.devmod.client.ui.editor.systems;

import com.devmod.client.ui.editor.ItemEditorDataManager;
import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.components.VirtualizedList;
import com.devmod.client.ui.editor.core.BaseOverlay;
import com.devmod.client.ui.editor.core.ScaledCoord;
import com.devmod.client.ui.editor.core.Typography;
import com.devmod.client.ui.editor.core.UIConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Templates overlay (MVP) with virtualized list and preview.
 * Extends BaseOverlay for consistent modal behavior.
 * Uses VirtualizedList component for consistent scroll behavior.
 *
 * @see EDITOR_DESIGN_SYSTEM.md Section 2.42 (Templates)
 * @see 13-scroll-system.md (Scroll Policy)
 */
public class TemplateOverlay extends BaseOverlay {

    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 280;
    private static final int LIST_ROW_HEIGHT = 24;
    private static final int VISIBLE_ROWS = 8;
    private static final int TITLE_OFFSET_Y = 10;
    private static final int SEARCH_OFFSET_Y = 40;
    private static final int ROW_TEXT_INSET = 6;
    private static final int PREVIEW_HEIGHT = 60;
    private static final int PREVIEW_LINE_HEIGHT = 12;
    private static final int BUTTON_ROW_OFFSET = 28;
    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 18;
    private static final String TITLE_TEXT = "Templates";
    private static final String FILTER_ALL_LABEL = "(all types)";
    private static final String FILTER_PREFIX = "For: ";
    private static final String SEARCH_PLACEHOLDER = "Search (Ctrl+F)";
    private static final String TEMPLATE_NAME_FALLBACK = "(template)";
    private static final String TEMPLATE_NONE_LABEL = "(no templates)";
    private static final String SCOPE_PREFIX = "Scope: ";
    private static final String SCOPE_UNKNOWN = "Unknown";
    private static final String TOUCHES_FORMAT = "Touches: Enchants (%d), Attributes (%d)";

    private final List<ItemEditorDataManager.TemplateData> templates = new ArrayList<>();
    private String searchQuery = "";
    private String filterCategory = null;
    private String lastSearchQuery = "";  // Track search changes

    private Consumer<ItemEditorDataManager.TemplateData> applyCallback = t -> {};
    private Runnable closeCallback = () -> {};

    private boolean searchFocused = false;

    // VirtualizedList for template list - uses standard scroll system
    private final VirtualizedList<ItemEditorDataManager.TemplateData> templateList =
        new VirtualizedList<ItemEditorDataManager.TemplateData>("template_list")
            .rowHeight(LIST_ROW_HEIGHT)
            .onSelect(t -> {})  // Selection handled internally
            .onDoubleClick(t -> {
                if (t != null) {
                    applyCallback.accept(t);
                }
            })
            .rowRenderer((ctx, template) -> {
                String name = template.name == null ? TEMPLATE_NAME_FALLBACK : template.name;
                String scope = template.itemCategory == null ? "" : " [" + template.itemCategory + "]";

                float textScale = Typography.withUiScale(Typography.BODY);
                Font rowFont = Minecraft.getInstance().font;
                Typography.drawText(ctx.graphics(), rowFont, name,
                    ctx.x() + ScaledCoord.scaleDim(ROW_TEXT_INSET),
                    ctx.y() + ScaledCoord.scaleDim(ROW_TEXT_INSET),
                    UIConstants.Text.PRIMARY(), textScale);

                int scopeWidth = Math.round(rowFont.width(scope) * textScale);
                Typography.drawText(ctx.graphics(), rowFont, scope,
                    ctx.x() + ctx.width() - scopeWidth - ScaledCoord.scaleDim(ROW_TEXT_INSET),
                    ctx.y() + ScaledCoord.scaleDim(ROW_TEXT_INSET),
                    UIConstants.Text.SECONDARY(), textScale);
            });

    // Buttons using EditorButton component
    private final EditorButton cancelButton = new EditorButton("cancel", "Cancel")
        .style(EditorButton.Style.NORMAL)
        .size(EditorButton.Size.SMALL);
    private final EditorButton applyButton = new EditorButton("apply", "Apply Template")
        .style(EditorButton.Style.SUCCESS)
        .size(EditorButton.Size.SMALL);

    public void setTemplates(List<ItemEditorDataManager.TemplateData> data, String filterCategory) {
        this.templates.clear();
        this.templates.addAll(data == null ? Collections.emptyList() : data);
        this.filterCategory = filterCategory;
        this.searchQuery = "";
        this.lastSearchQuery = "";
        updateFilteredList();
    }

    /**
     * Update the VirtualizedList with filtered items.
     */
    private void updateFilteredList() {
        List<ItemEditorDataManager.TemplateData> filtered = getFiltered();
        templateList.items(filtered);
        if (!filtered.isEmpty() && templateList.getSelectedIndex() < 0) {
            templateList.setSelectedIndex(0);
        }
    }

    public void onApply(Consumer<ItemEditorDataManager.TemplateData> cb) {
        this.applyCallback = cb == null ? t -> {} : cb;
    }

    public void onClose(Runnable cb) {
        this.closeCallback = cb == null ? () -> {} : cb;
    }

    public void resetSearch() {
        this.searchQuery = "";
        this.lastSearchQuery = "";
        this.searchFocused = false;
        updateFilteredList();
    }

    // =========================================================================
    // BaseOverlay IMPLEMENTATION
    // =========================================================================

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
                                  int panelX, int panelY, int panelW, int panelH,
                                  int mouseX, int mouseY) {
        float textScale = Typography.withUiScale(Typography.BODY);

        // Check if search changed and update list
        if (!searchQuery.equals(lastSearchQuery)) {
            lastSearchQuery = searchQuery;
            updateFilteredList();
        }

        // Header
        Typography.drawText(graphics, font, TITLE_TEXT, panelX + ScaledCoord.scaleDim(UIConstants.Spacing.LG),
            panelY + ScaledCoord.scaleDim(TITLE_OFFSET_Y), UIConstants.Text.TITLE(), textScale);
        // Filter label
        String filterLabel = filterCategory == null ? FILTER_ALL_LABEL : (FILTER_PREFIX + filterCategory);
        Typography.drawText(graphics, font, filterLabel, panelX + ScaledCoord.scaleDim(UIConstants.Spacing.LG),
            panelY + ScaledCoord.scaleDim(UIConstants.Spacing.XXL), UIConstants.Text.SECONDARY(), textScale);

        // Search box (lightweight)
        SearchBounds search = getSearchBounds(panelX, panelY, panelW);
        boolean searchHover = mouseX >= search.x() && mouseX <= search.x() + search.w()
            && mouseY >= search.y() && mouseY <= search.y() + search.h();
        int searchBg = searchHover || searchFocused ? UIConstants.Background.ACTIVE() : UIConstants.Background.INPUT();
        graphics.fill(search.x(), search.y(), search.x() + search.w(), search.y() + search.h(), searchBg);
        AxiomRenderer.drawBorder(graphics, search.x(), search.y(), search.w(), search.h(), UIConstants.Border.MUTED());
        String placeholder = searchQuery.isEmpty() ? SEARCH_PLACEHOLDER : searchQuery;
        int searchColor = searchQuery.isEmpty() ? UIConstants.Text.MUTED() : UIConstants.Text.PRIMARY();
        Typography.drawText(graphics, font, placeholder, search.x() + ScaledCoord.scaleDim(UIConstants.Spacing.SM),
            search.y() + ScaledCoord.scaleDim(UIConstants.Spacing.SM), searchColor, textScale);

        // List area - using VirtualizedList
        int listX = panelX + ScaledCoord.scaleDim(UIConstants.Spacing.LG);
        int listY = search.y() + search.h() + ScaledCoord.scaleDim(UIConstants.Spacing.MD);
        int listW = panelW - ScaledCoord.scaleDim(UIConstants.Spacing.XXL);
        int listH = ScaledCoord.scaleDim(VISIBLE_ROWS * LIST_ROW_HEIGHT);

        // Render the VirtualizedList (rowRenderer configured at initialization)
        templateList.render(graphics, listX, listY, listW, listH, mouseX, mouseY);

        // Preview box
        int previewX = panelX + ScaledCoord.scaleDim(UIConstants.Spacing.LG);
        int previewY = listY + listH + ScaledCoord.scaleDim(UIConstants.Spacing.MD);
        int previewH = ScaledCoord.scaleDim(PREVIEW_HEIGHT);
        graphics.fill(previewX, previewY, previewX + listW, previewY + previewH, UIConstants.Background.CONTENT());
        AxiomRenderer.drawBorder(graphics, previewX, previewY, listW, previewH, UIConstants.Border.DEFAULT());

        ItemEditorDataManager.TemplateData selectedTemplate = templateList.getSelectedItem();
        if (selectedTemplate != null) {
            String name = selectedTemplate.name == null ? TEMPLATE_NAME_FALLBACK : selectedTemplate.name;
            Typography.drawText(graphics, font, name, previewX + ScaledCoord.scaleDim(ROW_TEXT_INSET),
                previewY + ScaledCoord.scaleDim(ROW_TEXT_INSET), UIConstants.Text.PRIMARY(), textScale);
            String scope = SCOPE_PREFIX + (selectedTemplate.itemCategory == null ? SCOPE_UNKNOWN : selectedTemplate.itemCategory);
            Typography.drawText(graphics, font, scope, previewX + ScaledCoord.scaleDim(ROW_TEXT_INSET),
                previewY + ScaledCoord.scaleDim(ROW_TEXT_INSET + PREVIEW_LINE_HEIGHT), UIConstants.Text.SECONDARY(), textScale);

            int ench = selectedTemplate.enchantments == null ? 0 : selectedTemplate.enchantments.size();
            int attrs = selectedTemplate.attributes == null ? 0 : selectedTemplate.attributes.size();
            String touches = String.format(TOUCHES_FORMAT, ench, attrs);
            Typography.drawText(graphics, font, touches, previewX + ScaledCoord.scaleDim(ROW_TEXT_INSET),
                previewY + ScaledCoord.scaleDim(ROW_TEXT_INSET + PREVIEW_LINE_HEIGHT * 2), UIConstants.Text.MUTED(), textScale);
        } else {
            Typography.drawText(graphics, font, TEMPLATE_NONE_LABEL, previewX + ScaledCoord.scaleDim(ROW_TEXT_INSET),
                previewY + ScaledCoord.scaleDim(ROW_TEXT_INSET), UIConstants.Text.MUTED(), textScale);
        }

        // Buttons using EditorButton components
        int btnY = panelY + panelH - ScaledCoord.scaleDim(BUTTON_ROW_OFFSET);
        int btnW = ScaledCoord.scaleDim(BUTTON_WIDTH);
        int btnH = ScaledCoord.scaleDim(BUTTON_HEIGHT);
        int applyX = panelX + panelW - btnW - ScaledCoord.scaleDim(UIConstants.Spacing.LG);
        int cancelX = applyX - btnW - ScaledCoord.scaleDim(UIConstants.Spacing.MD);

        boolean canApply = selectedTemplate != null;
        applyButton.setEnabled(canApply);

        cancelButton.render(graphics, cancelX, btnY, btnW, btnH, mouseX, mouseY);
        applyButton.render(graphics, applyX, btnY, btnW, btnH, mouseX, mouseY);
    }

    @Override
    protected void onEscapePressed() {
        closeCallback.run();
    }

    @Override
    protected boolean handleKeyPressed(int keyCode) {
        // Enter applies
        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            ItemEditorDataManager.TemplateData selected = templateList.getSelectedItem();
            if (selected != null) {
                applyCallback.accept(selected);
            }
            return true;
        }

        // Arrow keys delegate to VirtualizedList
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            templateList.selectNext();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            templateList.selectPrevious();
            return true;
        }

        // Page up/down and Home/End
        if (templateList.keyPressed(keyCode, 0, 0)) {
            return true;
        }

        // Backspace for search
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && searchFocused && !searchQuery.isEmpty()) {
            searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
            return true;
        }
        return true; // Consume all keys when visible
    }

    /**
     * Handle key pressed with modifiers (for Ctrl+F).
     * This method should be called from the screen's keyPressed handler.
     */
    public boolean keyPressed(int keyCode, int modifiers) {
        if (!visible) return false;

        // Ctrl+F focuses search
        if (keyCode == GLFW.GLFW_KEY_F && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            searchFocused = true;
            return true;
        }

        // Delegate to BaseOverlay's keyPressed (handles ESC and other keys)
        return keyPressed(keyCode);
    }

    @Override
    protected boolean handleMouseClicked(double mouseX, double mouseY,
        int panelX, int panelY, int panelW, int panelH) {
        // Search focus
        SearchBounds search = getSearchBounds(panelX, panelY, panelW);
        searchFocused = mouseX >= search.x() && mouseX <= search.x() + search.w()
            && mouseY >= search.y() && mouseY <= search.y() + search.h();

        // Delegate list click to VirtualizedList
        if (templateList.mouseClicked(mouseX, mouseY, 0)) {
            return true;
        }

        // Button clicks using EditorButton components
        if (cancelButton.mouseClicked(mouseX, mouseY, 0)) {
            cancelButton.mouseReleased(mouseX, mouseY, 0);
            closeCallback.run();
            return true;
        }
        if (applyButton.mouseClicked(mouseX, mouseY, 0)) {
            applyButton.mouseReleased(mouseX, mouseY, 0);
            ItemEditorDataManager.TemplateData selected = templateList.getSelectedItem();
            if (selected != null) {
                applyCallback.accept(selected);
            }
            return true;
        }

        return true; // Consume click
    }

    @Override
    protected boolean handleMouseScrolled(double mouseX, double mouseY, double scrollDelta,
                                           int panelX, int panelY, int panelW, int panelH) {
        // Delegate scroll to VirtualizedList
        return templateList.mouseScrolled(mouseX, mouseY, scrollDelta);
    }

    @Override
    protected boolean handleCharTyped(char chr, int modifiers) {
        if (searchFocused) {
            if (!Character.isISOControl(chr)) {
                searchQuery += chr;
            }
            return true;
        }
        return false;
    }

    private List<ItemEditorDataManager.TemplateData> getFiltered() {
        if (templates.isEmpty()) return Collections.emptyList();
        String q = searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);
        List<ItemEditorDataManager.TemplateData> filtered = new ArrayList<>();
        for (ItemEditorDataManager.TemplateData template : templates) {
            if (template == null) continue;
            if (filterCategory != null && template.itemCategory != null &&
                !template.itemCategory.equalsIgnoreCase(filterCategory)) {
                continue;
            }
            if (!q.isEmpty()) {
                String name = template.name == null ? "" : template.name.toLowerCase(Locale.ROOT);
                if (!name.contains(q)) continue;
            }
            filtered.add(template);
        }
        return filtered;
    }

    private SearchBounds getSearchBounds(int panelX, int panelY, int panelW) {
        int searchX = panelX + ScaledCoord.scaleDim(UIConstants.Spacing.LG);
        int searchY = panelY + ScaledCoord.scaleDim(SEARCH_OFFSET_Y);
        int searchW = panelW - ScaledCoord.scaleDim(UIConstants.Spacing.XXL);
        int searchH = ScaledCoord.scaleDim(UIConstants.Spacing.XL);
        return new SearchBounds(searchX, searchY, searchW, searchH);
    }

    private record SearchBounds(int x, int y, int w, int h) {}
}
