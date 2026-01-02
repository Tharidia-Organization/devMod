package com.devmod.client.ui.editor.systems;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import com.devmod.DevMod;
import com.devmod.client.ui.editor.ItemEditorDataManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.components.VirtualizedList;
import com.devmod.client.ui.editor.core.BaseOverlay;
import com.devmod.client.ui.editor.core.DesignTokens;

public class PresetSelectorOverlay extends BaseOverlay {

    // ═══════════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════════

    private static final int WIDTH = 380;
    private static final int HEIGHT = 420;
    private static final int TITLE_HEIGHT = 24;
    private static final int SEARCH_HEIGHT = 22;
    private static final int LIST_ROW_HEIGHT = 20;
    private static final int LIST_VISIBLE_ROWS = 12;
    private static final int PREVIEW_HEIGHT = 50;
    private static final int BUTTON_HEIGHT = 20;
    private static final int PADDING = DesignTokens.Spacing.MD;
    private static final int TITLE_TEXT_OFFSET_Y = 4;
    private static final int CLOSE_BUTTON_SIZE = 12;
    private static final int FILTER_TEXT_OFFSET_Y = 2;
    private static final int FILTER_ROW_HEIGHT = 14;
    private static final int SEARCH_TEXT_PADDING = 6;
    private static final int SEARCH_CURSOR_INSET = 4;
    private static final int SEARCH_CURSOR_WIDTH = 1;
    private static final int BORDER_THICKNESS = 1;
    private static final int PREVIEW_TEXT_PADDING = 6;
    private static final int PREVIEW_TEXT_OFFSET_Y = 4;
    private static final int PREVIEW_LINE_HEIGHT = 12;
    private static final int PREVIEW_MESSAGE_OFFSET_Y = 4;
    private static final int PREVIEW_DESCRIPTION_MAX = 50;
    private static final int PREVIEW_DESCRIPTION_TRUNCATE = 47;
    private static final int ROW_SCOPE_BAR_WIDTH = 3;
    private static final int ROW_NAME_PADDING = 8;
    private static final int ROW_NAME_MAX = 30;
    private static final int ROW_NAME_TRUNCATE = 27;
    private static final int ROW_CATEGORY_PADDING = 6;
    private static final int BUTTON_COUNT = 3;
    private static final int USER_LABEL_OFFSET_X = 40;
    private static final int RENAME_BOX_HEIGHT = 18;
    private static final int RENAME_BOX_PADDING = 4;
    private static final int RENAME_TEXT_PADDING = 4;
    private static final int BUTTON_COUNT_WITH_RENAME = 4;

    private static final int CLOSE_HOVER_COLOR = 0xFFFF4444;
    private static final int RENAME_BG = 0xFF1A1A2A;
    private static final int RENAME_BORDER = 0xFF4488FF;
    private static final int SEARCH_BG_FOCUSED = 0xFF2A2A2A;
    private static final int SEARCH_BG_DEFAULT = 0xFF1E1E1E;
    private static final int PREVIEW_BG = 0xFF161616;
    private static final int ROW_BG_SELECTED = 0xFF1F4D3A;
    private static final int ROW_BG_HOVER = 0xFF2A2A2A;
    private static final int ROW_BG_DEFAULT = 0xFF1A1A1A;
    private static final int SCOPE_COLOR_MODPACK = 0xFFFF9900;
    private static final int SCOPE_COLOR_CATEGORY = 0xFF66AAFF;
    private static final int SCOPE_COLOR_GLOBAL = 0xFF88FF88;
    private static final int SCOPE_COLOR_GLOBAL_USER = 0xFFAAFF88;

    private static final long CURSOR_BLINK_MS = 500L;

    private static final String DEFAULT_CATEGORY = "general";
    private static final String TITLE_TEXT = "PRESETS";
    private static final String SEARCH_PLACEHOLDER = "Search presets...";
    private static final String PREVIEW_EMPTY_MESSAGE = "Select a preset to preview";
    private static final String USER_PRESET_LABEL = "[User]";
    private static final String CLOSE_LABEL = "X";
    private static final String CATEGORY_PREFIX = "Category: ";
    private static final String SELECTED_PREFIX = "Selected: ";
    private static final String LIST_ID = "preset_selector_list";

    // ═══════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════

    private String searchQuery = "";
    private boolean searchFocused = false;
    private String itemCategory = DEFAULT_CATEGORY;
    @Nullable
    private PresetListEntry selectedEntry = null;

    // Rename state
    @Nullable
    private PresetListEntry renamingEntry = null;
    private String renameBuffer = "";
    private boolean renameFocused = false;

    private final List<PresetListEntry> allEntries = new ArrayList<>();
    private final List<PresetListEntry> filteredEntries = new ArrayList<>();

    // ═══════════════════════════════════════════════════════════════
    // CALLBACKS
    // ═══════════════════════════════════════════════════════════════

    @Nullable
    private Consumer<ItemEditorDataManager.PresetData> onApply;
    @Nullable
    private Consumer<ItemEditorDataManager.PresetData> onDelete;
    @Nullable
    private BiConsumer<ItemEditorDataManager.PresetData, String> onRename;
    @Nullable
    private Runnable onSaveCurrent;
    @Nullable
    private Runnable onClose;

    // ═══════════════════════════════════════════════════════════════
    // COMPONENTS (lazy init to avoid this-escape)
    // ═══════════════════════════════════════════════════════════════

    @Nullable
    private VirtualizedList<PresetListEntry> presetList;
    private boolean listInitialized = false;

    private final EditorButton applyButton = new EditorButton("apply", "Apply")
        .style(EditorButton.Style.SUCCESS)
        .size(EditorButton.Size.MEDIUM);

    private final EditorButton deleteButton = new EditorButton("delete", "Delete")
        .style(EditorButton.Style.DANGER)
        .size(EditorButton.Size.MEDIUM);

    private final EditorButton saveButton = new EditorButton("save", "Save Current")
        .style(EditorButton.Style.PRIMARY)
        .size(EditorButton.Size.MEDIUM);

    private final EditorButton renameButton = new EditorButton("rename", "Rename")
        .style(EditorButton.Style.NORMAL)
        .size(EditorButton.Size.MEDIUM);

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public PresetSelectorOverlay() {
        // presetList initialized lazily to avoid this-escape warning
    }

    /** Ensures presetList is initialized (lazy init to avoid this-escape). */
    private void ensureList() {
        if (!listInitialized) {
            presetList = new VirtualizedList<PresetListEntry>(LIST_ID)
                .rowHeight(LIST_ROW_HEIGHT)
                .onSelect(entry -> selectedEntry = entry)
                .onDoubleClick(entry -> {
                    if (entry != null && onApply != null) {
                        onApply.accept(entry.asPresetData());
                        hide();
                    }
                })
                .rowRenderer(this::renderPresetRow);
            listInitialized = true;
        }
    }

    private @Nonnull VirtualizedList<PresetListEntry> requirePresetList() {
        ensureList();
        return Objects.requireNonNull(presetList, "presetList");
    }

    // ═══════════════════════════════════════════════════════════════
    // CONFIGURATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Set the item category context for filtering presets.
     */
    public PresetSelectorOverlay setContext(String category) {
        this.itemCategory = category != null ? category : DEFAULT_CATEGORY;
        return this;
    }

    /**
     * Set callback for when a preset is applied.
     */
    public PresetSelectorOverlay onApply(Consumer<ItemEditorDataManager.PresetData> callback) {
        this.onApply = callback;
        return this;
    }

    /**
     * Set callback for when a preset is deleted.
     */
    public PresetSelectorOverlay onDelete(Consumer<ItemEditorDataManager.PresetData> callback) {
        this.onDelete = callback;
        return this;
    }

    /**
     * Set callback for when a preset is renamed.
     * @param callback receives (preset, newName)
     */
    public PresetSelectorOverlay onRename(BiConsumer<ItemEditorDataManager.PresetData, String> callback) {
        this.onRename = callback;
        return this;
    }

    /**
     * Set callback for saving current item as preset.
     */
    public PresetSelectorOverlay onSaveCurrent(Runnable callback) {
        this.onSaveCurrent = callback;
        return this;
    }

    /**
     * Set callback for when overlay is closed.
     */
    public PresetSelectorOverlay onClose(Runnable callback) {
        this.onClose = callback;
        return this;
    }

    // ═══════════════════════════════════════════════════════════════
    // DATA LOADING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Refresh the preset list from all sources.
     */
    public void refreshPresets() {
        allEntries.clear();

        // Load from PresetRegistry (hierarchical presets)
        List<PresetRegistry.RegistryPreset> registryPresets =
            PresetRegistry.INSTANCE.getPresetsForCategory(itemCategory);

        for (var rp : registryPresets) {
            allEntries.add(new PresetListEntry(
                rp.id(),
                rp.name(),
                rp.description(),
                rp.scope(),
                rp.category(),
                PresetBridge.toPresetData(rp),
                false
            ));
        }

        // Load user presets from ItemEditorDataManager
        try {
            ItemEditorDataManager.INSTANCE.ensureInitialized();
            List<ItemEditorDataManager.PresetData> userPresets =
                ItemEditorDataManager.INSTANCE.getPresetsForItemType(itemCategory);

            for (var up : userPresets) {
                String userPresetName = (up.name == null || up.name.isBlank())
                    ? "Preset_" + up.createdAt
                    : up.name;
                // Avoid duplicates by name
                boolean alreadyExists = allEntries.stream()
                    .anyMatch(e -> e.name().equalsIgnoreCase(userPresetName));
                if (!alreadyExists) {
                    allEntries.add(new PresetListEntry(
                        PresetBridge.generateId(userPresetName),
                        userPresetName,
                        "",
                        new PresetScope.Global(),
                        up.itemType != null ? up.itemType : itemCategory,
                        up,
                        true
                    ));
                }
            }
        } catch (Exception e) {
            DevMod.LOGGER.warn("[PresetSelectorOverlay] Failed to load user presets", e);
        }

        updateFilteredList();
    }

    private void updateFilteredList() {
        VirtualizedList<PresetListEntry> list = requirePresetList();
        filteredEntries.clear();

        String query = searchQuery.toLowerCase(Locale.ROOT).trim();

        for (var entry : allEntries) {
            if (query.isEmpty() ||
                entry.name().toLowerCase(Locale.ROOT).contains(query) ||
                entry.description().toLowerCase(Locale.ROOT).contains(query) ||
                entry.scope().label().toLowerCase(Locale.ROOT).contains(query)) {
                filteredEntries.add(entry);
            }
        }

        // Sort by scope priority (descending), then by name
        filteredEntries.sort((a, b) -> {
            int cmp = Integer.compare(b.scope().priority(), a.scope().priority());
            if (cmp != 0) return cmp;
            return a.name().compareToIgnoreCase(b.name());
        });

        list.items(filteredEntries);

        // Keep selection if still in list, otherwise select first
        if (selectedEntry != null && !filteredEntries.contains(selectedEntry)) {
            selectedEntry = filteredEntries.isEmpty() ? null : filteredEntries.get(0);
        }
        if (selectedEntry != null) {
            int idx = filteredEntries.indexOf(selectedEntry);
            if (idx >= 0) {
                list.setSelectedIndex(idx);
            }
        }
    }

    /**
     * Reset search query and refresh.
     */
    public void resetSearch() {
        searchQuery = "";
        searchFocused = false;
        refreshPresets();
    }

    // ═══════════════════════════════════════════════════════════════
    // SHOW/HIDE OVERRIDES
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void show() {
        super.show();
        refreshPresets();
    }

    @Override
    public void hide() {
        super.hide();
        // Note: onClose is NOT called here to avoid recursion with ItemEditorScreen.updateOverlayFlags()
        // The close callback should only be triggered by user actions (ESC key, clicking outside, etc.)
        // which are handled via requestClose() method
    }

    /**
     * Request closing the overlay with callback notification.
     * Use this for user-initiated close actions (ESC, click outside, close button).
     */
    public void requestClose() {
        if (onClose != null) {
            onClose.run();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDERING
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected int getPanelWidth() {
        return WIDTH;
    }

    @Override
    protected int getPanelHeight() {
        return HEIGHT;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, Font font,
                                  int x, int y, int width, int height,
                                  int mouseX, int mouseY) {
        int currentY = y + PADDING;

        // Title
        String title = TITLE_TEXT;
        int titleWidth = font.width(title);
        graphics.drawString(font, title, x + (width - titleWidth) / 2, currentY + TITLE_TEXT_OFFSET_Y,
            DesignTokens.Text.TITLE(), false);

        // Close button (X) in top right
        int closeX = x + width - PADDING - CLOSE_BUTTON_SIZE;
        boolean closeHovered = mouseX >= closeX && mouseX <= closeX + CLOSE_BUTTON_SIZE &&
                               mouseY >= currentY && mouseY <= currentY + TITLE_HEIGHT;
        graphics.drawString(font, CLOSE_LABEL, closeX, currentY + TITLE_TEXT_OFFSET_Y,
            closeHovered ? CLOSE_HOVER_COLOR : DesignTokens.Text.MUTED(), false);

        currentY += TITLE_HEIGHT;

        // Filter info
        String filterInfo = CATEGORY_PREFIX + itemCategory;
        graphics.drawString(font, filterInfo, x + PADDING, currentY + FILTER_TEXT_OFFSET_Y,
            DesignTokens.Text.MUTED(), false);
        currentY += FILTER_ROW_HEIGHT;

        // Search box
        renderSearchBox(graphics, font, x + PADDING, currentY, width - PADDING * 2, SEARCH_HEIGHT);
        currentY += SEARCH_HEIGHT + DesignTokens.Spacing.SM;

        // Preset list
        int listHeight = LIST_VISIBLE_ROWS * LIST_ROW_HEIGHT;
        requirePresetList().render(graphics, x + PADDING, currentY, width - PADDING * 2, listHeight, mouseX, mouseY);
        currentY += listHeight + DesignTokens.Spacing.SM;

        // Preview box or Rename box
        if (renamingEntry != null && renameFocused) {
            renderRenameBox(graphics, font, x + PADDING, currentY, width - PADDING * 2, PREVIEW_HEIGHT);
        } else {
            renderPreviewBox(graphics, font, x + PADDING, currentY, width - PADDING * 2, PREVIEW_HEIGHT);
        }
        currentY += PREVIEW_HEIGHT + DesignTokens.Spacing.SM;

        // Buttons row (4 buttons when user preset selected, 3 otherwise)
        boolean canRename = selectedEntry != null && selectedEntry.isUserPreset();
        int numButtons = canRename ? BUTTON_COUNT_WITH_RENAME : BUTTON_COUNT;
        int buttonWidth = (width - PADDING * (numButtons + 1)) / numButtons;

        saveButton.render(graphics, x + PADDING, currentY, buttonWidth, BUTTON_HEIGHT, mouseX, mouseY);

        int btnX = x + PADDING + buttonWidth + PADDING;
        if (canRename) {
            renameButton.setEnabled(renamingEntry == null);
            renameButton.render(graphics, btnX, currentY, buttonWidth, BUTTON_HEIGHT, mouseX, mouseY);
            btnX += buttonWidth + PADDING;
        }

        deleteButton.setEnabled(selectedEntry != null && selectedEntry.isUserPreset());
        deleteButton.render(graphics, btnX, currentY, buttonWidth, BUTTON_HEIGHT, mouseX, mouseY);
        btnX += buttonWidth + PADDING;

        applyButton.setEnabled(selectedEntry != null && renamingEntry == null);
        applyButton.render(graphics, btnX, currentY, buttonWidth, BUTTON_HEIGHT, mouseX, mouseY);
    }

    private void renderRenameBox(GuiGraphics graphics, Font font, int x, int y, int width, int height) {
        Font safeFont = Objects.requireNonNull(font, "font");
        // Background
        graphics.fill(x, y, x + width, y + height, RENAME_BG);

        // Border
        graphics.fill(x, y, x + width, y + BORDER_THICKNESS, RENAME_BORDER);
        graphics.fill(x, y + height - BORDER_THICKNESS, x + width, y + height, RENAME_BORDER);
        graphics.fill(x, y, x + BORDER_THICKNESS, y + height, RENAME_BORDER);
        graphics.fill(x + width - BORDER_THICKNESS, y, x + width, y + height, RENAME_BORDER);

        // Label
        String label = "Rename preset:";
        graphics.drawString(safeFont, Objects.requireNonNull(label, "label"),
            x + RENAME_TEXT_PADDING, y + RENAME_BOX_PADDING,
            DesignTokens.Text.MUTED(), false);

        // Input box
        int inputY = y + RENAME_BOX_PADDING + safeFont.lineHeight + RENAME_BOX_PADDING;
        int inputH = RENAME_BOX_HEIGHT;
        int inputW = width - RENAME_TEXT_PADDING * 2;

        graphics.fill(x + RENAME_TEXT_PADDING, inputY, x + RENAME_TEXT_PADDING + inputW, inputY + inputH,
            SEARCH_BG_FOCUSED);
        graphics.fill(x + RENAME_TEXT_PADDING, inputY, x + RENAME_TEXT_PADDING + inputW, inputY + BORDER_THICKNESS,
            DesignTokens.Border.ACCENT());
        graphics.fill(x + RENAME_TEXT_PADDING, inputY + inputH - BORDER_THICKNESS, x + RENAME_TEXT_PADDING + inputW, inputY + inputH,
            DesignTokens.Border.ACCENT());

        // Text
        String displayText = renameBuffer.isEmpty() ? (renamingEntry != null ? renamingEntry.name() : "") : renameBuffer;
        graphics.drawString(safeFont, Objects.requireNonNull(displayText, "displayText"),
            x + RENAME_TEXT_PADDING + RENAME_BOX_PADDING,
            inputY + (inputH - safeFont.lineHeight) / 2, DesignTokens.Text.PRIMARY(), false);

        // Cursor
        if ((System.currentTimeMillis() / CURSOR_BLINK_MS) % 2 == 0) {
            int cursorX = x + RENAME_TEXT_PADDING + RENAME_BOX_PADDING +
                safeFont.width(Objects.requireNonNull(renameBuffer, "renameBuffer"));
            graphics.fill(cursorX, inputY + SEARCH_CURSOR_INSET, cursorX + SEARCH_CURSOR_WIDTH,
                inputY + inputH - SEARCH_CURSOR_INSET, DesignTokens.Text.PRIMARY());
        }

        // Hint
        String hint = "Press Enter to confirm, Escape to cancel";
        graphics.drawString(safeFont, Objects.requireNonNull(hint, "hint"),
            x + RENAME_TEXT_PADDING, y + height - safeFont.lineHeight - RENAME_BOX_PADDING,
            DesignTokens.Text.MUTED(), false);
    }

    private void renderSearchBox(GuiGraphics graphics, Font font, int x, int y, int width, int height) {
        Font safeFont = Objects.requireNonNull(font, "font");
        // Background
        int bg = searchFocused ? SEARCH_BG_FOCUSED : SEARCH_BG_DEFAULT;
        graphics.fill(x, y, x + width, y + height, bg);

        // Border
        int borderColor = searchFocused ? DesignTokens.Border.ACCENT() : DesignTokens.Border.DEFAULT();
        graphics.fill(x, y, x + width, y + BORDER_THICKNESS, borderColor);
        graphics.fill(x, y + height - BORDER_THICKNESS, x + width, y + height, borderColor);
        graphics.fill(x, y, x + BORDER_THICKNESS, y + height, borderColor);
        graphics.fill(x + width - BORDER_THICKNESS, y, x + width, y + height, borderColor);

        // Text
        String displayText = searchQuery.isEmpty() && !searchFocused ? SEARCH_PLACEHOLDER : searchQuery;
        int textColor = searchQuery.isEmpty() && !searchFocused ? DesignTokens.Text.MUTED() : DesignTokens.Text.PRIMARY();
        graphics.drawString(safeFont, Objects.requireNonNull(displayText, "displayText"),
            x + SEARCH_TEXT_PADDING, y + (height - safeFont.lineHeight) / 2, textColor, false);

        // Cursor when focused
        if (searchFocused && (System.currentTimeMillis() / CURSOR_BLINK_MS) % 2 == 0) {
            int cursorX = x + SEARCH_TEXT_PADDING + safeFont.width(Objects.requireNonNull(searchQuery, "searchQuery"));
            graphics.fill(cursorX, y + SEARCH_CURSOR_INSET, cursorX + SEARCH_CURSOR_WIDTH, y + height - SEARCH_CURSOR_INSET,
                DesignTokens.Text.PRIMARY());
        }
    }

    private void renderPreviewBox(GuiGraphics graphics, Font font, int x, int y, int width, int height) {
        Font safeFont = Objects.requireNonNull(font, "font");
        // Background
        graphics.fill(x, y, x + width, y + height, PREVIEW_BG);

        if (selectedEntry == null) {
            String msg = PREVIEW_EMPTY_MESSAGE;
            int textWidth = safeFont.width(Objects.requireNonNull(msg, "previewMessage"));
            graphics.drawString(safeFont, Objects.requireNonNull(msg, "previewMessage"),
                x + (width - textWidth) / 2, y + height / 2 - PREVIEW_MESSAGE_OFFSET_Y,
                DesignTokens.Text.MUTED(), false);
            return;
        }

        // Selected preset info
        int textY = y + PREVIEW_TEXT_OFFSET_Y;
        graphics.drawString(safeFont, Objects.requireNonNull(SELECTED_PREFIX + selectedEntry.name(), "selectedLabel"),
            x + PREVIEW_TEXT_PADDING, textY,
            DesignTokens.Text.PRIMARY(), false);
        textY += PREVIEW_LINE_HEIGHT;

        // Scope badge
        String scopeLabel = selectedEntry.scope().label();
        int scopeColor = switch (selectedEntry.scope()) {
            case PresetScope.Modpack ignored -> SCOPE_COLOR_MODPACK;
            case PresetScope.Category ignored -> SCOPE_COLOR_CATEGORY;
            case PresetScope.Global ignored -> SCOPE_COLOR_GLOBAL;
        };
        graphics.drawString(safeFont, Objects.requireNonNull(scopeLabel, "scopeLabel"),
            x + PREVIEW_TEXT_PADDING, textY, scopeColor, false);
        textY += PREVIEW_LINE_HEIGHT;

        // Description (truncated)
        String desc = selectedEntry.description();
        if (desc != null && !desc.isEmpty()) {
            if (desc.length() > PREVIEW_DESCRIPTION_MAX) {
                desc = desc.substring(0, PREVIEW_DESCRIPTION_TRUNCATE) + "...";
            }
            graphics.drawString(safeFont, Objects.requireNonNull(desc, "description"),
                x + PREVIEW_TEXT_PADDING, textY, DesignTokens.Text.MUTED(), false);
        }

        // User preset indicator
        if (selectedEntry.isUserPreset()) {
            graphics.drawString(safeFont, Objects.requireNonNull(USER_PRESET_LABEL, "userLabel"),
                x + width - USER_LABEL_OFFSET_X, y + PREVIEW_TEXT_OFFSET_Y,
                SCOPE_COLOR_GLOBAL_USER, false);
        }
    }

    private void renderPresetRow(VirtualizedList.RowRenderContext<PresetListEntry> ctx, PresetListEntry entry) {
        GuiGraphics graphics = ctx.graphics();
        Font font = Objects.requireNonNull(Minecraft.getInstance().font, "font");
        int x = ctx.x();
        int y = ctx.y();
        int width = ctx.width();
        boolean selected = ctx.selected();
        boolean hovered = ctx.hovered();

        // Background
        int bg = selected ? ROW_BG_SELECTED : hovered ? ROW_BG_HOVER : ROW_BG_DEFAULT;
        graphics.fill(x, y, x + width, y + LIST_ROW_HEIGHT, bg);

        // Scope indicator (colored bar)
        int scopeColor = switch (entry.scope()) {
            case PresetScope.Modpack ignored -> SCOPE_COLOR_MODPACK;
            case PresetScope.Category ignored -> SCOPE_COLOR_CATEGORY;
            case PresetScope.Global ignored -> entry.isUserPreset() ? SCOPE_COLOR_GLOBAL_USER : SCOPE_COLOR_GLOBAL;
        };
        graphics.fill(x, y, x + ROW_SCOPE_BAR_WIDTH, y + LIST_ROW_HEIGHT, scopeColor);

        // Name
        String name = entry.name();
        if (name.length() > ROW_NAME_MAX) {
            name = name.substring(0, ROW_NAME_TRUNCATE) + "...";
        }
        graphics.drawString(font, Objects.requireNonNull(name, "name"),
            x + ROW_NAME_PADDING, y + (LIST_ROW_HEIGHT - font.lineHeight) / 2,
            DesignTokens.Text.PRIMARY(), false);

        // Category tag on right
        String cat = entry.category();
        if (cat != null && !cat.isEmpty()) {
            int catWidth = font.width(Objects.requireNonNull(cat, "category"));
            graphics.drawString(font, Objects.requireNonNull(cat, "category"),
                x + width - catWidth - ROW_CATEGORY_PADDING,
                y + (LIST_ROW_HEIGHT - font.lineHeight) / 2,
                DesignTokens.Text.MUTED(), false);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected boolean handleMouseClicked(double mouseX, double mouseY,
        int panelX, int panelY, int panelW, int panelH) {
        // Close button
        int closeX = panelX + panelW - PADDING - CLOSE_BUTTON_SIZE;
        int closeY = panelY + PADDING;
        if (mouseX >= closeX && mouseX <= closeX + CLOSE_BUTTON_SIZE && mouseY >= closeY && mouseY <= closeY + TITLE_HEIGHT) {
            hide();
            return true;
        }

        // Calculate regions
        int searchY = panelY + PADDING + TITLE_HEIGHT + FILTER_ROW_HEIGHT;
        int searchH = SEARCH_HEIGHT;

        // Search box click
        if (mouseY >= searchY && mouseY <= searchY + searchH &&
            mouseX >= panelX + PADDING && mouseX <= panelX + panelW - PADDING) {
            searchFocused = true;
            return true;
        } else {
            searchFocused = false;
        }

        // List click
        if (requirePresetList().mouseClicked(mouseX, mouseY, 0)) {
            return true;
        }

        // Button clicks
        if (saveButton.mouseClicked(mouseX, mouseY, 0)) {
            saveButton.mouseReleased(mouseX, mouseY, 0);
            if (onSaveCurrent != null) {
                onSaveCurrent.run();
            }
            return true;
        }

        // Rename button click (only visible for user presets)
        if (selectedEntry != null && selectedEntry.isUserPreset() && renameButton.mouseClicked(mouseX, mouseY, 0)) {
            renameButton.mouseReleased(mouseX, mouseY, 0);
            startRename(selectedEntry);
            return true;
        }

        if (deleteButton.mouseClicked(mouseX, mouseY, 0)) {
            deleteButton.mouseReleased(mouseX, mouseY, 0);
            if (selectedEntry != null && selectedEntry.isUserPreset() && onDelete != null) {
                onDelete.accept(selectedEntry.asPresetData());
                cancelRename();
                refreshPresets();
            }
            return true;
        }

        if (applyButton.mouseClicked(mouseX, mouseY, 0)) {
            applyButton.mouseReleased(mouseX, mouseY, 0);
            if (selectedEntry != null && renamingEntry == null && onApply != null) {
                onApply.accept(selectedEntry.asPresetData());
                hide();
            }
            return true;
        }

        return true;
    }

    private void startRename(PresetListEntry entry) {
        if (entry == null || !entry.isUserPreset()) return;
        renamingEntry = entry;
        renameBuffer = entry.name();
        renameFocused = true;
        searchFocused = false;
    }

    private void cancelRename() {
        renamingEntry = null;
        renameBuffer = "";
        renameFocused = false;
    }

    private void commitRename() {
        if (renamingEntry == null || renameBuffer.isBlank()) {
            cancelRename();
            return;
        }

        String newName = renameBuffer.trim();
        if (newName.equals(renamingEntry.name())) {
            cancelRename();
            return;
        }

        if (onRename != null) {
            onRename.accept(renamingEntry.asPresetData(), newName);
        }

        cancelRename();
        refreshPresets();
    }

    @Override
    protected boolean handleMouseScrolled(double mouseX, double mouseY, double scrollDelta,
                                           int panelX, int panelY, int panelW, int panelH) {
        return requirePresetList().mouseScrolled(mouseX, mouseY, scrollDelta);
    }

    @Override
    protected boolean handleKeyPressed(int keyCode) {
        // ─── Rename mode input ───────────────────────────────────────
        if (renameFocused && renamingEntry != null) {
            // Escape cancels rename
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                cancelRename();
                return true;
            }
            // Enter commits rename
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                commitRename();
                return true;
            }
            // Backspace in rename
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !renameBuffer.isEmpty()) {
                renameBuffer = renameBuffer.substring(0, renameBuffer.length() - 1);
                return true;
            }
            // Block other keys while renaming
            return true;
        }

        // ─── Normal mode input ───────────────────────────────────────
        // Search focus toggle (Ctrl+F)
        if (keyCode == GLFW.GLFW_KEY_F && (GLFW.glfwGetKey(GLFW.glfwGetCurrentContext(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                                            GLFW.glfwGetKey(GLFW.glfwGetCurrentContext(), GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS)) {
            searchFocused = true;
            return true;
        }

        // Backspace in search
        if (searchFocused && keyCode == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
            searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
            updateFilteredList();
            return true;
        }

        // F2 to start rename (like Windows Explorer)
        if (keyCode == GLFW.GLFW_KEY_F2 && selectedEntry != null && selectedEntry.isUserPreset()) {
            startRename(selectedEntry);
            return true;
        }

        // Enter to apply
        if (keyCode == GLFW.GLFW_KEY_ENTER && selectedEntry != null && onApply != null) {
            onApply.accept(selectedEntry.asPresetData());
            hide();
            return true;
        }

        // Arrow navigation
        if (keyCode == GLFW.GLFW_KEY_UP) {
            VirtualizedList<PresetListEntry> list = requirePresetList();
            list.selectPrevious();
            selectedEntry = list.getSelectedItem();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            VirtualizedList<PresetListEntry> list = requirePresetList();
            list.selectNext();
            selectedEntry = list.getSelectedItem();
            return true;
        }

        // Delete key
        if (keyCode == GLFW.GLFW_KEY_DELETE && selectedEntry != null && selectedEntry.isUserPreset() && onDelete != null) {
            onDelete.accept(selectedEntry.asPresetData());
            refreshPresets();
            return true;
        }

        return true;
    }

    @Override
    protected boolean handleCharTyped(char chr, int modifiers) {
        // Rename mode char input
        if (renameFocused && renamingEntry != null) {
            if (Character.isLetterOrDigit(chr) || chr == ' ' || chr == '_' || chr == '-' || chr == '.') {
                renameBuffer += chr;
                return true;
            }
            return true; // Block other chars
        }

        // Search mode char input
        if (searchFocused && (Character.isLetterOrDigit(chr) || chr == ' ' || chr == '_' || chr == '-')) {
            searchQuery += chr;
            updateFilteredList();
            return true;
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    // INNER TYPES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Entry in the preset list, unifying registry and user presets.
     */
    public record PresetListEntry(
        String id,
        String name,
        String description,
        PresetScope scope,
        String category,
        ItemEditorDataManager.PresetData asPresetData,
        boolean isUserPreset
    ) {}
}
