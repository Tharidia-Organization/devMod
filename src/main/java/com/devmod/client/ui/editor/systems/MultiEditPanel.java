package com.devmod.client.ui.editor.systems;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import com.devmod.DevMod;
import com.devmod.client.ui.ConfirmDialog;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.ItemEditorDataManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.editor.core.ResponsiveLayout;

public class MultiEditPanel {
    private final MultiEditManager manager;
    private final Supplier<String> activeItemTypeSupplier;
    private boolean expanded = true;
    private static final int MAX_VISIBLE_PRESETS = 8;
    private static final int HEADER_HEIGHT = 20;
    private static final int HEADER_TEXT_Y = 6;
    private static final int HEADER_ICON_OFFSET_X = 15;
    private static final int TEXT_INSET = 6;
    private static final int EMPTY_STATE_TOP_GAP = 6;
    private static final int EMPTY_STATE_BORDER = 2;
    private static final int EMPTY_STATE_HEIGHT = 30;
    private static final int EMPTY_STATE_TEXT_GAP = 10;
    private static final int ITEM_ROW_HEIGHT = 18;
    private static final int PRESET_ROW_HEIGHT = 18;
    private static final int PRESET_LABEL_OFFSET_X = 60;
    private static final int PRESET_DROPDOWN_ROW_HEIGHT = 16;
    private static final int PRESET_DROPDOWN_VISIBLE_MAX = 10;
    private static final int PRESET_OPTION_TEXT_Y = 3;
    private static final int PRESET_HINT_PADDING = 4;
    private static final int PRESET_HINT_OFFSET_Y = 10;
    private static final int ITEM_LIST_MAX_VISIBLE = 10;
    private static final int ITEM_REMOVE_OFFSET_X = 18;
    private static final int PREVIEW_TOGGLE_HEIGHT = 16;
    private static final int PREVIEW_TOGGLE_WIDTH = 100;
    private static final int PREVIEW_HINT_OFFSET_X = 110;
    private static final int ACTION_BUTTON_HEIGHT = 16;
    private static final int CLEAR_BUTTON_WIDTH = 80;
    private static final int APPLY_BUTTON_WIDTH = 96;
    private static final int APPLY_BUTTON_OFFSET_X = APPLY_BUTTON_WIDTH + DesignTokens.Spacing.SM;
    private static final int SUMMARY_HEIGHT = 18;
    private static final int MAX_VISIBLE_FAILURES = 20;
    private static final int DEFAULT_VISIBLE_FAILURES = 6;
    private static final int FAILURE_DETAIL_LINE_HEIGHT = 14;
    private static final int FAILURE_DETAIL_PADDING = 6;
    private static final int FAILURE_BUTTON_HEIGHT = 14;
    private static final int FAILURE_BUTTON_Y_OFFSET = 2;
    private static final int FAILURE_TOGGLE_OFFSET_X = 70;
    private static final int FAILURE_TOGGLE_WIDTH = 64;
    private static final int FAILURE_COPY_OFFSET_X = 145;
    private static final int FAILURE_COPY_WIDTH = 70;
    private static final int FAILURE_EXPORT_OFFSET_X = 210;
    private static final int FAILURE_EXPORT_WIDTH = 60;
    private static final int FAILURE_MORE_HEIGHT = 12;
    private static final int FAILURE_MORE_WIDTH_PADDING = 6;
    private static final int PROGRESS_BAR_HEIGHT = 12;
    private static final int PROGRESS_TEXT_OFFSET_Y = 2;
    private static final int PRESET_LABEL_MAX_LENGTH = 28;
    private static final int PRESET_LABEL_TRUNCATE_LENGTH = 25;
    private static final int PRESET_OPTION_MAX_LENGTH = 30;
    private static final int PRESET_OPTION_TRUNCATE_LENGTH = 27;
    private static final int HOVER_LABEL_MAX_LENGTH = 30;
    private static final int ITEM_NAME_MAX_LENGTH = 25;
    private static final int ITEM_NAME_TRUNCATE_LENGTH = 22;
    private static final int FAILURE_LINE_MAX_LENGTH = 60;
    private static final int FAILURE_LINE_TRUNCATE_LENGTH = 57;
    private static final String ELLIPSIS = "...";

    private static final int HEADER_BG_HOVER = DesignTokens.MultiEdit.HEADER_BG_HOVER;
    private static final int HEADER_BG_DEFAULT = DesignTokens.MultiEdit.HEADER_BG_DEFAULT;
    private static final int TEXT_PRIMARY_COLOR = DesignTokens.MultiEdit.TEXT_PRIMARY;
    private static final int TEXT_MUTED_COLOR = DesignTokens.MultiEdit.TEXT_MUTED;
    private static final int TEXT_DIM_COLOR = DesignTokens.MultiEdit.TEXT_DIM;
    private static final int TEXT_FAINT_COLOR = DesignTokens.MultiEdit.TEXT_FAINT;
    private static final int TEXT_HINT_COLOR = DesignTokens.MultiEdit.TEXT_HINT;
    private static final int TEXT_SECONDARY_COLOR = DesignTokens.MultiEdit.TEXT_SECONDARY;
    private static final int EMPTY_STATE_BG = DesignTokens.MultiEdit.EMPTY_STATE_BG;
    private static final int PRESET_BG_OPEN = DesignTokens.MultiEdit.PRESET_BG_OPEN;
    private static final int PRESET_BG_CLOSED = DesignTokens.MultiEdit.PRESET_BG_CLOSED;
    private static final int PRESET_LABEL_COLOR_ACTIVE = DesignTokens.MultiEdit.PRESET_LABEL_ACTIVE;
    private static final int DROPDOWN_BG = DesignTokens.MultiEdit.DROPDOWN_BG;
    private static final int DROPDOWN_BG_SELECTED = DesignTokens.MultiEdit.DROPDOWN_BG_SELECTED;
    private static final int DROPDOWN_BG_HOVER = DesignTokens.MultiEdit.DROPDOWN_BG_HOVER;
    private static final int DROPDOWN_BG_DEFAULT = DesignTokens.MultiEdit.DROPDOWN_BG_DEFAULT;
    private static final int DROPDOWN_TEXT_COLOR = DesignTokens.MultiEdit.DROPDOWN_TEXT;
    private static final int DROPDOWN_HINT_COLOR = DesignTokens.MultiEdit.DROPDOWN_HINT;
    private static final int DROPDOWN_HOVER_COLOR = DesignTokens.MultiEdit.DROPDOWN_HOVER;
    private static final int ITEM_BG_HOVER = DesignTokens.MultiEdit.ITEM_BG_HOVER;
    private static final int ITEM_BG_DEFAULT = DesignTokens.MultiEdit.ITEM_BG_DEFAULT;
    private static final int ITEM_REMOVE_HOVER_COLOR = DesignTokens.MultiEdit.ITEM_REMOVE_HOVER;
    private static final int PREVIEW_MODE_COLOR = DesignTokens.MultiEdit.PREVIEW_MODE;
    private static final int ACTION_ROW_BG = DesignTokens.MultiEdit.ACTION_ROW_BG;
    private static final int PROGRESS_BAR_BG = DesignTokens.MultiEdit.PROGRESS_BAR_BG;
    private static final int PROGRESS_BAR_FILL = DesignTokens.MultiEdit.PROGRESS_BAR_FILL;
    private static final int RESULT_BG = DesignTokens.MultiEdit.RESULT_BG;
    private static final int RESULT_SUCCESS_COLOR = DesignTokens.MultiEdit.RESULT_SUCCESS;
    private static final int RESULT_WARNING_COLOR = DesignTokens.MultiEdit.RESULT_WARNING;
    private static final int FAILURE_TEXT_COLOR = DesignTokens.MultiEdit.FAILURE_TEXT;
    private static final int MORE_FAILURES_COLOR = DesignTokens.MultiEdit.MORE_FAILURES;

    // Item bounds for hover/click detection
    private final List<ResponsiveLayout.Rect> itemRects = new ArrayList<>();
    private final List<ResponsiveLayout.Rect> presetOptionRects = new ArrayList<>();
    private final List<Integer> presetOptionIndices = new ArrayList<>();
    private final List<Integer> itemRectIndices = new ArrayList<>();
    private ResponsiveLayout.Rect clearRect = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect applyRect = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect presetRect = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect headerRect = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect presetDropdownArea = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect itemScrollArea = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect failureToggleRect = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect copyFailuresRect = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect moreFailuresRect = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect failureDetailsArea = ResponsiveLayout.Rect.EMPTY;
    @Nullable
    private String hoveredPresetFullName = null;
    private boolean presetDropdownOpen = false;
    private int presetScrollOffset = 0;
    private int selectedPresetIndex = -1;
    @Nullable
    private BatchEditResult lastResult = null;
    @Nullable
    private BatchEditResult pendingStatusResult = null;
    private boolean showFailureDetails = false;
    private boolean showAllFailures = false;
    private boolean applyEnabled = false;
    private boolean clearEnabled = false;
    private boolean persistAllowed = true;
    private boolean previewOnlyMode = false;  // Preview toggle - when true, apply won't persist
    private int itemScrollOffset = 0;
    private int failureScrollOffset = 0;  // Separate scroll for failure details

    @Nullable
    private final java.util.function.BooleanSupplier persistSupplier;
    private ResponsiveLayout.Rect previewToggleRect = ResponsiveLayout.Rect.EMPTY;

    // Buttons using EditorButton component
    private final EditorButton clearButton = new EditorButton("clear", "Clear All")
        .style(EditorButton.Style.DANGER)
        .size(EditorButton.Size.SMALL);
    private final EditorButton applyButton = new EditorButton("apply", "Apply to all")
        .style(EditorButton.Style.SUCCESS)
        .size(EditorButton.Size.SMALL);
    private final EditorButton exportButton = new EditorButton("export", "Export")
        .style(EditorButton.Style.GHOST)
        .size(EditorButton.Size.SMALL);
    private final EditorButton copyFailsButton = new EditorButton("copyFails", "Copy fails")
        .style(EditorButton.Style.GHOST)
        .size(EditorButton.Size.SMALL);
    private final EditorButton detailsButton = new EditorButton("details", "Details")
        .style(EditorButton.Style.GHOST)
        .size(EditorButton.Size.SMALL)
        .toggleable(true);
    private final EditorButton previewToggleButton = new EditorButton("preview", "Preview only")
        .style(EditorButton.Style.GHOST)
        .size(EditorButton.Size.SMALL)
        .toggleable(true);

    // Confirmation dialog callback: (itemCount, presetName, onConfirm) -> void
    @Nullable
    private java.util.function.Consumer<ConfirmDialog> showDialogCallback;
    private static final int CONFIRM_THRESHOLD = 10;  // Show confirm dialog if > 10 items
    private static final int LARGE_BATCH_THRESHOLD = 20;  // Show timing info for large batches
    @Nullable
    private String lastApplyTiming = null;  // e.g., "23 items in 45ms"

    // Real-time progress tracking
    private int applyProgress = -1;  // -1 means not applying, otherwise current item index
    private int applyTotal = 0;      // Total items being processed

    public MultiEditPanel(MultiEditManager manager, @Nullable java.util.function.BooleanSupplier persistSupplier,
                          Supplier<String> activeItemTypeSupplier) {
        this.manager = manager;
        this.persistSupplier = persistSupplier;
        this.activeItemTypeSupplier = activeItemTypeSupplier == null ? () -> "item" : activeItemTypeSupplier;
    }

    /**
     * Set a callback to show confirmation dialogs via the parent screen.
     * @param callback Consumer that receives a ConfirmDialog to show
     */
    public void setShowDialogCallback(@Nullable java.util.function.Consumer<ConfirmDialog> callback) {
        this.showDialogCallback = callback;
    }

    public void setExpanded(boolean expanded) { this.expanded = expanded; }
    public boolean isExpanded() { return expanded; }

    private List<ItemEditorDataManager.PresetData> availablePresets() {
        List<ItemEditorDataManager.PresetData> result = new ArrayList<>();
        HashSet<String> seenNames = new HashSet<>();
        String type = activeItemTypeSupplier.get();

        try {
            // 1. Load from PresetRegistry (hierarchical presets - higher priority)
            List<PresetRegistry.RegistryPreset> registryPresets =
                PresetRegistry.INSTANCE.getPresetsForCategory(type != null ? type : "general");
            for (var rp : registryPresets) {
                ItemEditorDataManager.PresetData converted = PresetBridge.toPresetData(rp);
                String registryName = converted.name;
                if (registryName == null || registryName.isBlank()) {
                    registryName = "Preset_" + converted.createdAt;
                    converted.name = registryName;
                }
                if (seenNames.add(registryName.toLowerCase(Locale.ROOT))) {
                    result.add(converted);
                }
            }
        } catch (Exception ignored) {
            // PresetRegistry may not be initialized yet
        }

        try {
            // 2. Load user presets from ItemEditorDataManager
            ItemEditorDataManager.INSTANCE.ensureInitialized();
            List<ItemEditorDataManager.PresetData> userPresets;
            if (type != null && !type.isBlank()) {
                userPresets = ItemEditorDataManager.INSTANCE.getPresetsForItemType(type);
            } else {
                userPresets = ItemEditorDataManager.INSTANCE.getPresets();
            }
            if (userPresets != null) {
                for (var up : userPresets) {
                    String presetName = up.name;
                    if (presetName == null || presetName.isBlank()) {
                        presetName = "Preset_" + up.createdAt;
                        up.name = presetName;
                    }
                    if (seenNames.add(presetName.toLowerCase(Locale.ROOT))) {
                        result.add(up);
                    }
                }
            }
        } catch (Exception e) {
            DevMod.LOGGER.debug("[MultiEditPanel] Failed to load user presets", e);
        }

        return result;
    }

    public int render(GuiGraphics graphics, Font font, int x, int y, int width, int mouseX, int mouseY) {
        int count = manager.getSelectionCount();
        Font safeFont = Objects.requireNonNull(font, "font cannot be null");
        itemRects.clear();
        itemRectIndices.clear();
        presetOptionRects.clear();
        presetOptionIndices.clear();
        clearRect = ResponsiveLayout.Rect.EMPTY;
        applyRect = ResponsiveLayout.Rect.EMPTY;
        presetRect = ResponsiveLayout.Rect.EMPTY;
        headerRect = ResponsiveLayout.Rect.EMPTY;
        failureToggleRect = ResponsiveLayout.Rect.EMPTY;
        copyFailuresRect = ResponsiveLayout.Rect.EMPTY;
        moreFailuresRect = ResponsiveLayout.Rect.EMPTY;
        failureDetailsArea = ResponsiveLayout.Rect.EMPTY;
        presetDropdownArea = ResponsiveLayout.Rect.EMPTY;
        itemScrollArea = ResponsiveLayout.Rect.EMPTY;
        hoveredPresetFullName = null;
        applyEnabled = false;
        clearEnabled = false;

        // Header bar
        int headerHeight = HEADER_HEIGHT;
        headerRect = new ResponsiveLayout.Rect(x, y, width, headerHeight);
        boolean headerHovered = headerRect.contains(mouseX, mouseY);
        int headerBg = headerHovered ? HEADER_BG_HOVER : HEADER_BG_DEFAULT;
        graphics.fill(headerRect.x(), headerRect.y(), headerRect.right(), headerRect.bottom(), headerBg);

        // Selection count
        String countText = count + " item" + (count != 1 ? "s" : "") + " selected";
        int countColor = count == 0 ? TEXT_MUTED_COLOR : TEXT_PRIMARY_COLOR;
        UIScaleManager.drawScaledString(graphics, safeFont, countText, x + DesignTokens.Spacing.SM, y + HEADER_TEXT_Y, countColor, false);

        // Expand/collapse button
        String expandIcon = isExpanded() ? "▼" : "▶";
        UIScaleManager.drawScaledString(graphics, safeFont, expandIcon, x + width - HEADER_ICON_OFFSET_X, y + HEADER_TEXT_Y,
            headerHovered ? TEXT_PRIMARY_COLOR : TEXT_MUTED_COLOR, false);

        if (!isExpanded() || count == 0) {
            presetDropdownOpen = false;
            showFailureDetails = false;
            showAllFailures = false;
            if (!isExpanded()) {
                return headerHeight;
            }
            int emptyY = y + headerHeight + EMPTY_STATE_TOP_GAP;
            graphics.fill(x + EMPTY_STATE_BORDER, emptyY, x + width - EMPTY_STATE_BORDER, emptyY + EMPTY_STATE_HEIGHT, EMPTY_STATE_BG);
            UIScaleManager.drawScaledString(graphics, safeFont, "No matching items in inventory", x + TEXT_INSET, emptyY + TEXT_INSET, TEXT_DIM_COLOR, false);
            UIScaleManager.drawScaledString(graphics, safeFont, "Add items or press M to rescan", x + TEXT_INSET,
                emptyY + TEXT_INSET + EMPTY_STATE_TEXT_GAP, TEXT_HINT_COLOR, false);
            return (emptyY + EMPTY_STATE_HEIGHT) - y;
        }

        List<ItemEditorDataManager.PresetData> presets = availablePresets();
        // Local capture for null safety
        java.util.function.BooleanSupplier supplier = persistSupplier;
        persistAllowed = supplier == null || supplier.getAsBoolean();
        if (presets.isEmpty()) {
            selectedPresetIndex = -1;
            presetScrollOffset = 0;
            presetDropdownOpen = false;
        } else {
            if (selectedPresetIndex < 0) selectedPresetIndex = 0;
            if (selectedPresetIndex >= presets.size()) selectedPresetIndex = presets.size() - 1;
            int maxOffset = Math.max(0, presets.size() - MAX_VISIBLE_PRESETS);
            if (presetScrollOffset > maxOffset) presetScrollOffset = maxOffset;
        }

        applyEnabled = persistAllowed && count > 0 && !presets.isEmpty();
        clearEnabled = count > 0;

        int listY = y + headerHeight;
        int itemHeight = ITEM_ROW_HEIGHT;

        // Preset selector area (dropdown)
        int presetHeight = PRESET_ROW_HEIGHT;
        presetRect = new ResponsiveLayout.Rect(x + DesignTokens.Spacing.SM, listY,
            width - DesignTokens.Spacing.MD, presetHeight);
        int presetBg = presetDropdownOpen ? PRESET_BG_OPEN : PRESET_BG_CLOSED;
        graphics.fill(presetRect.x(), presetRect.y(), presetRect.right(), presetRect.bottom(), presetBg);
        String itemType = activeItemTypeSupplier.get();
        if (itemType == null || itemType.isBlank()) itemType = "item";
        String scopeLabel = "Preset (" + itemType + ")";
        UIScaleManager.drawScaledString(graphics, safeFont, scopeLabel, presetRect.x() + TEXT_INSET, listY + DesignTokens.Spacing.SM,
            TEXT_SECONDARY_COLOR, false);

        String presetLabel = "(no presets)";
        if (!presets.isEmpty() && selectedPresetIndex >= 0 && selectedPresetIndex < presets.size()) {
            String name = presets.get(selectedPresetIndex).name;
            presetLabel = (name == null || name.isEmpty()) ? "Unnamed" : name;
        }
        if (presetLabel.length() > PRESET_LABEL_MAX_LENGTH) {
            presetLabel = presetLabel.substring(0, PRESET_LABEL_TRUNCATE_LENGTH) + ELLIPSIS;
        }
        int labelColor = presets.isEmpty() ? TEXT_FAINT_COLOR : PRESET_LABEL_COLOR_ACTIVE;
        UIScaleManager.drawScaledString(graphics, safeFont, presetLabel, presetRect.x() + PRESET_LABEL_OFFSET_X,
            listY + DesignTokens.Spacing.SM, labelColor, false);
        UIScaleManager.drawScaledString(graphics, safeFont, presetDropdownOpen ? "▲" : "▼",
            presetRect.right() - DesignTokens.Spacing.LG, listY + DesignTokens.Spacing.SM, TEXT_MUTED_COLOR, false);

        if (presetDropdownOpen && !presets.isEmpty()) {
            int optionHeight = PRESET_DROPDOWN_ROW_HEIGHT;
            int visibleCount = Math.min(PRESET_DROPDOWN_VISIBLE_MAX, Math.min(MAX_VISIBLE_PRESETS, presets.size()));
            int maxOffset = Math.max(0, presets.size() - visibleCount);
            int startIndex = Math.min(presetScrollOffset, maxOffset);
            int dropdownHeight = visibleCount * optionHeight;
            presetDropdownArea = new ResponsiveLayout.Rect(presetRect.x(), presetRect.bottom(), presetRect.width(), dropdownHeight);
            graphics.fill(presetDropdownArea.x(), presetDropdownArea.y(), presetDropdownArea.right(), presetDropdownArea.bottom(), DROPDOWN_BG);

            for (int i = 0; i < visibleCount; i++) {
                int idx = startIndex + i;
                ResponsiveLayout.Rect optRect = new ResponsiveLayout.Rect(presetRect.x(), presetRect.bottom() + i * optionHeight, presetRect.width(), optionHeight);
                presetOptionRects.add(optRect);
                presetOptionIndices.add(idx);
                boolean hovered = optRect.contains(mouseX, mouseY);
                boolean selected = idx == selectedPresetIndex;
                int bg = selected ? DROPDOWN_BG_SELECTED : hovered ? DROPDOWN_BG_HOVER : DROPDOWN_BG_DEFAULT;
                graphics.fill(optRect.x(), optRect.y(), optRect.right(), optRect.bottom(), bg);
                String name = presets.get(idx).name;
                String display = (name == null || name.isEmpty()) ? "Unnamed preset" : name;
                if (hovered) {
                    hoveredPresetFullName = display;
                }
                if (display.length() > PRESET_OPTION_MAX_LENGTH) {
                    display = display.substring(0, PRESET_OPTION_TRUNCATE_LENGTH) + ELLIPSIS;
                }
                UIScaleManager.drawScaledString(graphics, safeFont, display, optRect.x() + TEXT_INSET,
                    optRect.y() + PRESET_OPTION_TEXT_Y, DROPDOWN_TEXT_COLOR, false);
            }

            if (maxOffset > 0) {
                String hint = "Scroll " + (startIndex + 1) + "-" + (startIndex + visibleCount) + "/" + presets.size();
                UIScaleManager.drawScaledString(graphics, safeFont, hint, presetDropdownArea.right() - UIScaleManager.getScaledStringWidth(safeFont, hint) - PRESET_HINT_PADDING,
                    presetDropdownArea.bottom() - PRESET_HINT_OFFSET_Y, DROPDOWN_HINT_COLOR, false);
            }
            if (hoveredPresetFullName != null) {
                String hoverLine = "↳ " + hoveredPresetFullName;
                if (UIScaleManager.getScaledStringWidth(safeFont, hoverLine) > presetDropdownArea.width() - DesignTokens.Spacing.MD) {
                    hoverLine = hoverLine.substring(0, Math.max(0, Math.min(hoverLine.length(), HOVER_LABEL_MAX_LENGTH)))
                        + ELLIPSIS;
                }
                UIScaleManager.drawScaledString(graphics, safeFont, hoverLine, presetDropdownArea.x() + TEXT_INSET,
                    presetDropdownArea.bottom() - PRESET_HINT_OFFSET_Y, DROPDOWN_HOVER_COLOR, false);
            }
            listY += presetHeight + dropdownHeight + DesignTokens.Spacing.SM;
        } else {
            listY += presetHeight + DesignTokens.Spacing.SM;
        }

        // Virtualized item list with scrolling
        int maxVisibleItems = ITEM_LIST_MAX_VISIBLE;
        int countItems = manager.getSelectedItems().size();
        int visibleCount = Math.min(maxVisibleItems, countItems);
        int maxItemOffset = Math.max(0, countItems - visibleCount);
        if (itemScrollOffset > maxItemOffset) itemScrollOffset = maxItemOffset;
        int itemListHeight = visibleCount * itemHeight;
        itemScrollArea = new ResponsiveLayout.Rect(x, listY, width, itemListHeight);

        for (int vis = 0; vis < visibleCount; vis++) {
            int idx = itemScrollOffset + vis;
            ItemStack item = manager.getSelectedItems().get(idx);

            ResponsiveLayout.Rect rect = new ResponsiveLayout.Rect(x, listY, width, itemHeight);
            itemRects.add(rect);
            itemRectIndices.add(idx);

            boolean hovered = rect.contains(mouseX, mouseY);
            int bg = hovered ? ITEM_BG_HOVER : ITEM_BG_DEFAULT;
            graphics.fill(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height(), bg);

            // Item icon placeholder
            UIScaleManager.drawScaledString(graphics, safeFont, "▪", x + DesignTokens.Spacing.SM,
                listY + DesignTokens.Spacing.SM, TEXT_DIM_COLOR, false);

            String name = item.getHoverName().getString();
            if (name.length() > ITEM_NAME_MAX_LENGTH) {
                name = name.substring(0, ITEM_NAME_TRUNCATE_LENGTH) + ELLIPSIS;
            }
            UIScaleManager.drawScaledString(graphics, safeFont, name, x + DesignTokens.Spacing.XL,
                listY + DesignTokens.Spacing.SM, TEXT_PRIMARY_COLOR, false);

            // Remove button
            int removeX = x + width - ITEM_REMOVE_OFFSET_X;
            UIScaleManager.drawScaledString(graphics, safeFont, "✗", removeX, listY + DesignTokens.Spacing.SM,
                hovered ? ITEM_REMOVE_HOVER_COLOR : TEXT_FAINT_COLOR, false);

            listY += itemHeight;
        }

        listY += DesignTokens.Spacing.SM;
        if (!persistAllowed) {
            UIScaleManager.drawScaledString(graphics, safeFont, "Preview mode: switch to Apply to persist", x + TEXT_INSET, listY, PREVIEW_MODE_COLOR, false);
            listY += DesignTokens.Spacing.LG;
        }

        // Preview toggle row
        int toggleH = PREVIEW_TOGGLE_HEIGHT;
        previewToggleRect = new ResponsiveLayout.Rect(x + DesignTokens.Spacing.SM, listY, PREVIEW_TOGGLE_WIDTH, toggleH);
        previewToggleButton.toggled(previewOnlyMode);
        previewToggleButton.render(graphics, previewToggleRect.x(), previewToggleRect.y(), previewToggleRect.width(), toggleH, mouseX, mouseY);
        if (previewOnlyMode) {
            UIScaleManager.drawScaledString(graphics, safeFont, "(will not persist)", x + PREVIEW_HINT_OFFSET_X,
                listY + DesignTokens.Spacing.SM, PREVIEW_MODE_COLOR, false);
        }
        listY += toggleH + DesignTokens.Spacing.SM;

        // Action buttons area using EditorButton components
        graphics.fill(x, listY, x + width, listY + DesignTokens.Spacing.XXL, ACTION_ROW_BG);

        int btnH = ACTION_BUTTON_HEIGHT;
        clearRect = new ResponsiveLayout.Rect(x + DesignTokens.Spacing.SM, listY + DesignTokens.Spacing.SM, CLEAR_BUTTON_WIDTH, btnH);
        applyRect = new ResponsiveLayout.Rect(x + width - APPLY_BUTTON_OFFSET_X,
            listY + DesignTokens.Spacing.SM, APPLY_BUTTON_WIDTH, btnH);

        clearButton.setEnabled(clearEnabled);
        applyButton.setEnabled(applyEnabled);

        clearButton.render(graphics, clearRect.x(), clearRect.y(), clearRect.width(), btnH, mouseX, mouseY);
        applyButton.render(graphics, applyRect.x(), applyRect.y(), applyRect.width(), btnH, mouseX, mouseY);

        listY += DesignTokens.Spacing.XXL;

        // Progress bar during batch apply operation
        if (applyProgress >= 0 && applyTotal > 0) {
            int progressBarHeight = PROGRESS_BAR_HEIGHT;
            int progressBarY = listY;
            int progressBarWidth = width - DesignTokens.Spacing.MD * 2;
            int progressBarX = x + DesignTokens.Spacing.MD;

            // Background
            graphics.fill(progressBarX, progressBarY, progressBarX + progressBarWidth, progressBarY + progressBarHeight, PROGRESS_BAR_BG);

            // Progress fill
            float progress = (float) applyProgress / applyTotal;
            int fillWidth = (int) (progressBarWidth * progress);
            graphics.fill(progressBarX, progressBarY, progressBarX + fillWidth, progressBarY + progressBarHeight, PROGRESS_BAR_FILL);

            // Progress text
            String progressText = String.format("Applying... %d/%d", applyProgress, applyTotal);
            String safeProgressText = Objects.requireNonNull(progressText, "progressText");
            int textWidth = UIScaleManager.getScaledStringWidth(safeFont, safeProgressText);
            int textX = progressBarX + (progressBarWidth - textWidth) / 2;
            UIScaleManager.drawScaledString(graphics, safeFont, safeProgressText, textX, progressBarY + PROGRESS_TEXT_OFFSET_Y, TEXT_PRIMARY_COLOR, false);

            listY += progressBarHeight + DesignTokens.Spacing.SM;
        }

        // Local capture for null safety
        BatchEditResult result = lastResult;
        if (result != null) {
            int summaryHeight = SUMMARY_HEIGHT;
            int maxVisibleFailures = showAllFailures ? Math.min(MAX_VISIBLE_FAILURES, result.failureCount())
                                                     : Math.min(DEFAULT_VISIBLE_FAILURES, result.failureCount());
            int detailLines = showFailureDetails ? maxVisibleFailures : 0;
            int detailHeight = detailLines == 0 ? 0 : detailLines * FAILURE_DETAIL_LINE_HEIGHT + FAILURE_DETAIL_PADDING;
            int panelHeight = summaryHeight + detailHeight;

            graphics.fill(x, listY, x + width, listY + panelHeight, RESULT_BG);
            String summary = "Last apply: " + result.successCount() + " success";
            if (result.successCount() != 1) summary += "es";
            if (result.failureCount() > 0) {
                summary += ", " + result.failureCount() + " failed";
            }
            // Show timing info for large batch operations
            if (lastApplyTiming != null) {
                summary += " (" + lastApplyTiming + ")";
            }
            int summaryColor = result.failureCount() == 0 ? RESULT_SUCCESS_COLOR : RESULT_WARNING_COLOR;
            UIScaleManager.drawScaledString(graphics, safeFont, summary, x + TEXT_INSET, listY + DesignTokens.Spacing.SM, summaryColor, false);

            if (result.failureCount() > 0) {
                int failBtnH = FAILURE_BUTTON_HEIGHT;
                failureToggleRect = new ResponsiveLayout.Rect(x + width - FAILURE_TOGGLE_OFFSET_X,
                    listY + FAILURE_BUTTON_Y_OFFSET, FAILURE_TOGGLE_WIDTH, failBtnH);
                copyFailuresRect = new ResponsiveLayout.Rect(x + width - FAILURE_COPY_OFFSET_X,
                    listY + FAILURE_BUTTON_Y_OFFSET, FAILURE_COPY_WIDTH, failBtnH);
                ResponsiveLayout.Rect exportRect = new ResponsiveLayout.Rect(x + width - FAILURE_EXPORT_OFFSET_X,
                    listY + FAILURE_BUTTON_Y_OFFSET, FAILURE_EXPORT_WIDTH, failBtnH);

                // Render buttons using EditorButton components
                exportButton.render(graphics, exportRect.x(), exportRect.y(), exportRect.width(), failBtnH, mouseX, mouseY);
                copyFailsButton.render(graphics, copyFailuresRect.x(), copyFailuresRect.y(), copyFailuresRect.width(), failBtnH, mouseX, mouseY);
                detailsButton.toggled(showFailureDetails);
                detailsButton.render(graphics, failureToggleRect.x(), failureToggleRect.y(), failureToggleRect.width(), failBtnH, mouseX, mouseY);
            } else {
                failureToggleRect = ResponsiveLayout.Rect.EMPTY;
                copyFailuresRect = ResponsiveLayout.Rect.EMPTY;
                moreFailuresRect = ResponsiveLayout.Rect.EMPTY;
                failureDetailsArea = ResponsiveLayout.Rect.EMPTY;
                showFailureDetails = false;
                showAllFailures = false;
            }

            if (showFailureDetails && result.failureCount() > 0) {
                int detailY = listY + summaryHeight;
                List<BatchEditResult.FailureDetail> details = result.failureDetails();
                int maxLines = Math.min(detailLines, details.size());
                int maxFailureOffset = Math.max(0, details.size() - maxLines);
                if (failureScrollOffset > maxFailureOffset) failureScrollOffset = maxFailureOffset;
                int startIdx = Math.max(0, Math.min(maxFailureOffset, failureScrollOffset));
                int endIdx = Math.min(details.size(), startIdx + maxLines);

                // Define scroll area for failure details (reuse detailHeight from above)
                failureDetailsArea = new ResponsiveLayout.Rect(x, detailY, width, detailHeight);

                for (int i = startIdx; i < endIdx; i++) {
                    BatchEditResult.FailureDetail d = details.get(i);
                    String line = "slot#" + d.slot() + " " + (d.itemId() == null ? "<unknown-id>" : d.itemId()) + " - " + d.message();
                    if (line.length() > FAILURE_LINE_MAX_LENGTH) {
                        line = line.substring(0, FAILURE_LINE_TRUNCATE_LENGTH) + ELLIPSIS;
                    }
                    UIScaleManager.drawScaledString(graphics, safeFont, "• " + line, x + DesignTokens.Spacing.MD,
                        detailY + (i - startIdx) * FAILURE_DETAIL_LINE_HEIGHT, FAILURE_TEXT_COLOR, false);
                }
                if (details.size() > endIdx) {
                    String moreText = "(+" + (details.size() - endIdx) + " more)";
                    moreFailuresRect = new ResponsiveLayout.Rect(x + DesignTokens.Spacing.MD,
                        detailY + (endIdx - startIdx) * FAILURE_DETAIL_LINE_HEIGHT,
                        UIScaleManager.getScaledStringWidth(safeFont, moreText) + FAILURE_MORE_WIDTH_PADDING, FAILURE_MORE_HEIGHT);
                    UIScaleManager.drawScaledString(graphics, safeFont, moreText, moreFailuresRect.x(), moreFailuresRect.y(), MORE_FAILURES_COLOR, false);
                } else {
                    moreFailuresRect = ResponsiveLayout.Rect.EMPTY;
                }
            }

            listY += panelHeight + DesignTokens.Spacing.SM;
        }

        return listY - y;
    }
    /**
     * Handle mouse clicks inside the panel. Returns true if consumed.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (headerRect != null && headerRect.contains(mouseX, mouseY)) {
            expanded = !expanded;
            if (!isExpanded()) {
                presetDropdownOpen = false;
                showFailureDetails = false;
                showAllFailures = false;
            }
            return true;
        }

        if (!isExpanded()) return false;

        List<ItemEditorDataManager.PresetData> presets = availablePresets();

        if (presetDropdownOpen && !presetOptionRects.isEmpty()) {
            for (int i = 0; i < presetOptionRects.size(); i++) {
                var rect = presetOptionRects.get(i);
                if (rect.contains(mouseX, mouseY)) {
                    if (i < presetOptionIndices.size()) {
                        selectedPresetIndex = presetOptionIndices.get(i);
                    }
                    presetDropdownOpen = false;
                    return true;
                }
            }
            if (presetDropdownArea != null && presetDropdownArea.contains(mouseX, mouseY)) {
                return true;
            }
        }

        if (presetRect != null && presetRect.contains(mouseX, mouseY)) {
            if (!presets.isEmpty()) {
                presetDropdownOpen = !presetDropdownOpen;
            } else {
                presetDropdownOpen = false;
            }
            return true;
        } else if (presetDropdownOpen && presetDropdownArea != null && !presetDropdownArea.contains(mouseX, mouseY)) {
            presetDropdownOpen = false;
        }

        // Check items remove buttons
        for (int i = 0; i < itemRects.size(); i++) {
            var rect = itemRects.get(i);
            if (rect.contains(mouseX, mouseY)) {
                // If clicked near right edge, interpret as remove (ITEM_REMOVE_OFFSET_X px from right)
                if (mouseX >= rect.x() + rect.width() - ITEM_REMOVE_OFFSET_X) {
                    int idx = (i < itemRectIndices.size()) ? itemRectIndices.get(i) : i;
                    manager.removeFromSelection(idx);
                    return true;
                }
            }
        }

        // Failure detail buttons using EditorButton components
        if (exportButton.mouseClicked(mouseX, mouseY, 0)) {
            exportButton.mouseReleased(mouseX, mouseY, 0);
            exportFailuresToFile();
            return true;
        }
        if (copyFailsButton.mouseClicked(mouseX, mouseY, 0)) {
            copyFailsButton.mouseReleased(mouseX, mouseY, 0);
            copyFailuresToClipboard();
            return true;
        }
        if (detailsButton.mouseClicked(mouseX, mouseY, 0)) {
            detailsButton.mouseReleased(mouseX, mouseY, 0);
            showFailureDetails = !showFailureDetails;
            if (!showFailureDetails) {
                showAllFailures = false;
            }
            return true;
        }
        if (moreFailuresRect != null && moreFailuresRect.contains(mouseX, mouseY)) {
            showAllFailures = true;
            showFailureDetails = true;
            return true;
        }

        // Preview toggle button
        if (previewToggleButton.mouseClicked(mouseX, mouseY, 0)) {
            previewToggleButton.mouseReleased(mouseX, mouseY, 0);
            previewOnlyMode = !previewOnlyMode;
            return true;
        }

        // Clear all using EditorButton
        if (clearButton.mouseClicked(mouseX, mouseY, 0)) {
            clearButton.mouseReleased(mouseX, mouseY, 0);
            if (clearEnabled) {
                manager.clearSelection();
                lastResult = null;
                pendingStatusResult = null;
                failureScrollOffset = 0;  // Reset failure scroll on clear
            }
            return true;
        }

        // Apply to all using EditorButton
        if (applyButton.mouseClicked(mouseX, mouseY, 0)) {
            applyButton.mouseReleased(mouseX, mouseY, 0);
            if (applyEnabled && selectedPresetIndex >= 0 && selectedPresetIndex < presets.size()) {
                var presetData = presets.get(selectedPresetIndex);
                int itemCount = manager.getSelectionCount();
                String presetName = presetData.name != null ? presetData.name : "Unnamed";

                // If many items and not in preview mode, show confirmation dialog
                // Local capture for null safety
                java.util.function.Consumer<ConfirmDialog> dialogCallback = showDialogCallback;
                if (itemCount > CONFIRM_THRESHOLD && !previewOnlyMode && dialogCallback != null) {
                    var dialog = ConfirmDialog.batchApply(itemCount, presetName,
                        () -> doApply(presetData),  // onConfirm
                        () -> {}                     // onCancel
                    );
                    dialogCallback.accept(dialog);
                } else {
                    doApply(presetData);
                }
            }
            return true;
        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!isExpanded()) return false;

        // Scroll inside preset dropdown
        if (presetDropdownOpen && presetDropdownArea != null && presetDropdownArea.contains(mouseX, mouseY)) {
            var presets = availablePresets();
            if (presets.isEmpty()) return false;
            int visibleCount = Math.min(PRESET_DROPDOWN_VISIBLE_MAX, Math.min(MAX_VISIBLE_PRESETS, presets.size()));
            int maxOffset = Math.max(0, presets.size() - visibleCount);
            if (maxOffset == 0) return false;

            int delta = (int) Math.signum(scrollY);
            presetScrollOffset = Math.max(0, Math.min(maxOffset, presetScrollOffset - delta));
            return true;
        }

        // Scroll inside item list
        if (itemScrollArea != null && itemScrollArea.contains(mouseX, mouseY)) {
            int count = manager.getSelectionCount();
            int maxVisible = ITEM_LIST_MAX_VISIBLE;
            int maxOffset = Math.max(0, count - maxVisible);
            if (maxOffset == 0) return false;
            int delta = (int) Math.signum(scrollY);
            itemScrollOffset = Math.max(0, Math.min(maxOffset, itemScrollOffset - delta));
            return true;
        }

        // Scroll inside failure details (separate from item list)
        // Local capture for null safety
        BatchEditResult result = lastResult;
        if (failureDetailsArea != null && failureDetailsArea.contains(mouseX, mouseY) && result != null) {
            int failureCount = result.failureCount();
            int maxVisibleFailures = showAllFailures ? Math.min(MAX_VISIBLE_FAILURES, failureCount)
                                                     : Math.min(DEFAULT_VISIBLE_FAILURES, failureCount);
            int maxOffset = Math.max(0, failureCount - maxVisibleFailures);
            if (maxOffset == 0) return false;
            int delta = (int) Math.signum(scrollY);
            failureScrollOffset = Math.max(0, Math.min(maxOffset, failureScrollOffset - delta));
            return true;
        }

        return false;
    }

    /**
     * Execute the actual batch apply operation.
     * Extracted to allow calling from both direct click and confirmation dialog callback.
     */
    private void doApply(ItemEditorDataManager.PresetData presetData) {
        var dataPreset = new DataPreset(presetData);
        var adapter = ItemEditorPresetManager.INSTANCE;
        // Respect previewOnlyMode: if true, never persist regardless of persistSupplier
        // Local capture for null safety
        java.util.function.BooleanSupplier supplier = persistSupplier;
        boolean persist = !previewOnlyMode && (supplier == null || supplier.getAsBoolean());
        // Create undo snapshot before applying (for Ctrl+Z batch undo) - only if persisting
        if (persist) {
            String presetName = presetData.name != null ? presetData.name : "Unnamed";
            manager.createSnapshot(presetName);
        }
        int itemCount = manager.getSelectionCount();

        // Initialize progress tracking
        applyProgress = 0;
        applyTotal = itemCount;

        long startTime = System.currentTimeMillis();
        // Use progress callback for real-time updates
        lastResult = manager.applyPresetToAll(dataPreset, adapter, persist, (current, total) -> {
            applyProgress = current;
            applyTotal = total;
        });
        long elapsed = System.currentTimeMillis() - startTime;

        // Reset progress (operation complete)
        applyProgress = -1;
        applyTotal = 0;

        // Track timing info for large batches
        if (itemCount >= LARGE_BATCH_THRESHOLD) {
            lastApplyTiming = itemCount + " items in " + elapsed + "ms";
        } else {
            lastApplyTiming = null;
        }
        pendingStatusResult = lastResult;
        showFailureDetails = lastResult != null && lastResult.failureCount() > 0;
        failureScrollOffset = 0;  // Reset failure scroll on new apply
    }

    private void copyFailuresToClipboard() {
        // Local capture for null safety
        BatchEditResult result = lastResult;
        if (result == null || result.failureCount() == 0) return;
        try {
            StringBuilder sb = new StringBuilder();
            for (BatchEditResult.FailureDetail d : result.failureDetails()) {
                sb.append(java.util.Objects.requireNonNull(d.toString(), "failure detail cannot be null")).append("\n");
                if (d.stackTrace() != null) {
                    sb.append(d.stackTrace()).append("\n");
                }
            }
            String payload = sb.toString();
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.keyboardHandler != null) {
                mc.keyboardHandler.setClipboard(java.util.Objects.requireNonNull(payload, "clipboard payload cannot be null"));
            }
        } catch (Exception ignored) {
            // best-effort copy only
        }
    }

    private void exportFailuresToFile() {
        // Local capture for null safety
        BatchEditResult result = lastResult;
        if (result == null || result.failureCount() == 0) return;
        try {
            java.nio.file.Path out = java.nio.file.Paths.get("multiedit_failures_" + System.currentTimeMillis() + ".log");
            try (java.io.BufferedWriter w = java.nio.file.Files.newBufferedWriter(out)) {
                for (BatchEditResult.FailureDetail d : result.failureDetails()) {
                    w.write(d.toString()); w.newLine();
                    if (d.stackTrace() != null) {
                        w.write(d.stackTrace()); w.newLine();
                    }
                }
            }
            // Show status via Minecraft screen if available
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.gui != null && mc.gui.getChat() != null) {
                    var msg = java.util.Objects.requireNonNull(
                        net.minecraft.network.chat.Component.literal("Exported failures to " + out.toAbsolutePath()),
                        "chat message cannot be null");
                    mc.gui.getChat().addMessage(msg);
                }
            } catch (Exception e) {
                DevMod.LOGGER.debug("[MultiEditPanel] Failed to post export message", e);
            }
        } catch (Exception ignored) {
            // best-effort export only
        }
    }

    /**
     * Retrieve and clear the last batch edit result (if any).
     */
    @Nullable
    public BatchEditResult takeLastResult() {
        BatchEditResult r = pendingStatusResult;
        pendingStatusResult = null;
        return r;
    }
}
