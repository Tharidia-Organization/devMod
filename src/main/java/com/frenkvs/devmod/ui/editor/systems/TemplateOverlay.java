package com.frenkvs.devmod.ui.editor.systems;

import com.frenkvs.devmod.ItemEditorDataManager;
import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.editor.components.EditorButton;
import com.frenkvs.devmod.ui.editor.core.BaseOverlay;
import com.frenkvs.devmod.ui.editor.core.ScaledCoord;
import com.frenkvs.devmod.ui.editor.core.Typography;
import com.frenkvs.devmod.ui.editor.core.UIConstants;
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
 * Based on EDITOR_DESIGN_SYSTEM section 2.42.
 */
public class TemplateOverlay extends BaseOverlay {

    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 280;
    private static final int LIST_ROW_HEIGHT = 24;

    private final List<ItemEditorDataManager.TemplateData> templates = new ArrayList<>();
    private String searchQuery = "";
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    private String filterCategory = null;

    private Consumer<ItemEditorDataManager.TemplateData> applyCallback = t -> {};
    private Runnable closeCallback = () -> {};

    private boolean searchFocused = false;

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
        this.selectedIndex = this.templates.isEmpty() ? -1 : 0;
        this.scrollOffset = 0;
    }

    public void onApply(Consumer<ItemEditorDataManager.TemplateData> cb) {
        this.applyCallback = cb == null ? t -> {} : cb;
    }

    public void onClose(Runnable cb) {
        this.closeCallback = cb == null ? () -> {} : cb;
    }

    public void resetSearch() {
        this.searchQuery = "";
        this.searchFocused = false;
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

        // Header
        Typography.drawText(graphics, font, "Templates", panelX + ScaledCoord.scaleDim(12), panelY + ScaledCoord.scaleDim(10), UIConstants.Text.TITLE, textScale);
        // Filter label
        String filterLabel = filterCategory == null ? "(all types)" : ("For: " + filterCategory);
        Typography.drawText(graphics, font, filterLabel, panelX + ScaledCoord.scaleDim(12), panelY + ScaledCoord.scaleDim(24), UIConstants.Text.SECONDARY, textScale);

        // Search box (lightweight)
        int searchX = panelX + ScaledCoord.scaleDim(12);
        int searchY = panelY + ScaledCoord.scaleDim(40);
        int searchW = panelW - ScaledCoord.scaleDim(24);
        int searchH = ScaledCoord.scaleDim(16);
        boolean searchHover = mouseX >= searchX && mouseX <= searchX + searchW && mouseY >= searchY && mouseY <= searchY + searchH;
        int searchBg = searchHover || searchFocused ? UIConstants.Background.ACTIVE : UIConstants.Background.INPUT;
        graphics.fill(searchX, searchY, searchX + searchW, searchY + searchH, searchBg);
        AxiomRenderer.drawBorder(graphics, searchX, searchY, searchW, searchH, UIConstants.Border.MUTED);
        String placeholder = searchQuery.isEmpty() ? "Search (Ctrl+F)" : searchQuery;
        int searchColor = searchQuery.isEmpty() ? UIConstants.Text.MUTED : UIConstants.Text.PRIMARY;
        Typography.drawText(graphics, font, placeholder, searchX + ScaledCoord.scaleDim(4), searchY + ScaledCoord.scaleDim(4), searchColor, textScale);

        // List area
        int listX = panelX + ScaledCoord.scaleDim(12);
        int listY = searchY + searchH + ScaledCoord.scaleDim(8);
        int listW = panelW - ScaledCoord.scaleDim(24);
        int listH = ScaledCoord.scaleDim(8 * LIST_ROW_HEIGHT); // visible rows
        int rowHeight = ScaledCoord.scaleDim(LIST_ROW_HEIGHT);

        // Scissor for virtualization
        graphics.enableScissor(listX, listY, listX + listW, listY + listH);

        List<ItemEditorDataManager.TemplateData> filtered = getFiltered();
        if (!filtered.isEmpty() && (selectedIndex < 0 || selectedIndex >= filtered.size())) {
            selectedIndex = 0;
        }
        int totalRows = filtered.size();
        int maxScroll = Math.max(0, totalRows * rowHeight - listH);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        int startRow = rowHeight == 0 ? 0 : scrollOffset / rowHeight;
        int offsetY = rowHeight == 0 ? 0 : -(scrollOffset % rowHeight);

        for (int i = startRow; i < totalRows && (i - startRow) * rowHeight + offsetY < listH; i++) {
            int rowTop = listY + (i - startRow) * rowHeight + offsetY;
            ItemEditorDataManager.TemplateData template = filtered.get(i);
            boolean hovered = mouseX >= listX && mouseX <= listX + listW && mouseY >= rowTop && mouseY <= rowTop + rowHeight;
            boolean selected = i == selectedIndex;
            int rowBg = selected ? UIConstants.Background.ACTIVE : hovered ? UIConstants.Background.HOVER : UIConstants.Background.PANEL;
            graphics.fill(listX, rowTop, listX + listW, rowTop + rowHeight, rowBg);
            String name = template.name == null ? "(template)" : template.name;
            String scope = template.itemCategory == null ? "" : " [" + template.itemCategory + "]";
            Typography.drawText(graphics, font, name, listX + ScaledCoord.scaleDim(6), rowTop + ScaledCoord.scaleDim(6), UIConstants.Text.PRIMARY, textScale);
            int scopeWidth = Math.round(font.width(scope) * textScale);
            Typography.drawText(graphics, font, scope, listX + listW - scopeWidth - ScaledCoord.scaleDim(6), rowTop + ScaledCoord.scaleDim(6), UIConstants.Text.SECONDARY, textScale);
        }

        graphics.disableScissor();

        // Fade indicators if overflowing
        int fadeH = ScaledCoord.scaleDim(6);
        if (scrollOffset > 0) {
            graphics.fill(listX, listY, listX + listW, listY + fadeH, 0x80000000);
        }
        if (maxScroll > 0 && scrollOffset < maxScroll) {
            graphics.fill(listX, listY + listH - fadeH, listX + listW, listY + listH, 0x80000000);
        }

        // Preview box
        int previewX = panelX + ScaledCoord.scaleDim(12);
        int previewY = listY + listH + ScaledCoord.scaleDim(8);
        int previewH = ScaledCoord.scaleDim(60);
        graphics.fill(previewX, previewY, previewX + listW, previewY + previewH, UIConstants.Background.CONTENT);
        AxiomRenderer.drawBorder(graphics, previewX, previewY, listW, previewH, UIConstants.Border.DEFAULT);

        if (!filtered.isEmpty() && selectedIndex >= 0 && selectedIndex < filtered.size()) {
            ItemEditorDataManager.TemplateData selectedTemplate = filtered.get(selectedIndex);
            String name = selectedTemplate.name == null ? "(template)" : selectedTemplate.name;
            Typography.drawText(graphics, font, name, previewX + ScaledCoord.scaleDim(6), previewY + ScaledCoord.scaleDim(6), UIConstants.Text.PRIMARY, textScale);
            String scope = "Scope: " + (selectedTemplate.itemCategory == null ? "Unknown" : selectedTemplate.itemCategory);
            Typography.drawText(graphics, font, scope, previewX + ScaledCoord.scaleDim(6), previewY + ScaledCoord.scaleDim(18), UIConstants.Text.SECONDARY, textScale);

            int ench = selectedTemplate.enchantments == null ? 0 : selectedTemplate.enchantments.size();
            int attrs = selectedTemplate.attributes == null ? 0 : selectedTemplate.attributes.size();
            String touches = "Touches: Enchants (" + ench + "), Attributes (" + attrs + ")";
            Typography.drawText(graphics, font, touches, previewX + ScaledCoord.scaleDim(6), previewY + ScaledCoord.scaleDim(30), UIConstants.Text.MUTED, textScale);
        } else {
            Typography.drawText(graphics, font, "(no templates)", previewX + ScaledCoord.scaleDim(6), previewY + ScaledCoord.scaleDim(6), UIConstants.Text.MUTED, textScale);
        }

        // Buttons using EditorButton components
        int btnY = panelY + panelH - ScaledCoord.scaleDim(28);
        int btnW = ScaledCoord.scaleDim(100);
        int btnH = ScaledCoord.scaleDim(18);
        int applyX = panelX + panelW - btnW - ScaledCoord.scaleDim(12);
        int cancelX = applyX - btnW - ScaledCoord.scaleDim(8);

        boolean canApply = !filtered.isEmpty() && selectedIndex >= 0 && selectedIndex < filtered.size();
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
            List<ItemEditorDataManager.TemplateData> filtered = getFiltered();
            if (!filtered.isEmpty() && selectedIndex >= 0 && selectedIndex < filtered.size()) {
                applyCallback.accept(filtered.get(selectedIndex));
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            moveSelection(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            moveSelection(-1);
            return true;
        }
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
        int searchX = panelX + ScaledCoord.scaleDim(12);
        int searchY = panelY + ScaledCoord.scaleDim(40);
        int searchW = panelW - ScaledCoord.scaleDim(24);
        int searchH = ScaledCoord.scaleDim(16);
        searchFocused = mouseX >= searchX && mouseX <= searchX + searchW && mouseY >= searchY && mouseY <= searchY + searchH;

        // List click
        int listX = panelX + ScaledCoord.scaleDim(12);
        int listY = searchY + searchH + ScaledCoord.scaleDim(8);
        int listW = panelW - ScaledCoord.scaleDim(24);
        int listH = ScaledCoord.scaleDim(8 * LIST_ROW_HEIGHT);
        int rowHeight = ScaledCoord.scaleDim(LIST_ROW_HEIGHT);
        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            List<ItemEditorDataManager.TemplateData> filtered = getFiltered();
            int maxScroll = Math.max(0, filtered.size() * rowHeight - listH);
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
            int localY = (int) mouseY - listY + scrollOffset;
            int index = rowHeight == 0 ? 0 : localY / rowHeight;
            if (index >= 0 && index < filtered.size()) {
                selectedIndex = index;
            }
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
            List<ItemEditorDataManager.TemplateData> filtered = getFiltered();
            if (!filtered.isEmpty() && selectedIndex >= 0 && selectedIndex < filtered.size()) {
                applyCallback.accept(filtered.get(selectedIndex));
            }
            return true;
        }

        return true; // Consume click
    }

    @Override
    protected boolean handleMouseScrolled(double mouseX, double mouseY, double scrollDelta,
                                           int panelX, int panelY, int panelW, int panelH) {
        int searchY = panelY + ScaledCoord.scaleDim(40);
        int listX = panelX + ScaledCoord.scaleDim(12);
        int listY = searchY + ScaledCoord.scaleDim(16) + ScaledCoord.scaleDim(8);
        int listW = panelW - ScaledCoord.scaleDim(24);
        int listH = ScaledCoord.scaleDim(8 * LIST_ROW_HEIGHT);
        int rowHeight = ScaledCoord.scaleDim(LIST_ROW_HEIGHT);

        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            int maxScroll = Math.max(0, getFiltered().size() * rowHeight - listH);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (scrollDelta * rowHeight)));
            return true;
        }
        return false;
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

    private void moveSelection(int delta) {
        List<ItemEditorDataManager.TemplateData> filtered = getFiltered();
        if (filtered.isEmpty()) return;
        selectedIndex = Math.max(0, Math.min(filtered.size() - 1, selectedIndex + delta));
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
}
