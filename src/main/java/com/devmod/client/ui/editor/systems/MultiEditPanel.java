package com.devmod.ui.editor.systems;

import com.devmod.ui.editor.components.EditorButton;
import com.devmod.ui.editor.core.ResponsiveLayout;
import com.devmod.ui.editor.core.UIConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import com.devmod.ui.editor.ItemEditorDataManager;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Simple UI panel for multi-edit selection. This is a lightweight implementation
 * intended to match the sample in `EDITOR_DESIGN_SYSTEM.md` and to provide concrete
 * hover tracking and basic remove interaction.
 */
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
    private static final int APPLY_BUTTON_OFFSET_X = APPLY_BUTTON_WIDTH + UIConstants.Spacing.SM;
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

    private static final int HEADER_BG_HOVER = 0xFF333333;
    private static final int HEADER_BG_DEFAULT = 0xFF2A2A2A;
    private static final int TEXT_PRIMARY_COLOR = 0xFFFFFFFF;
    private static final int TEXT_MUTED_COLOR = 0xFFAAAAAA;
    private static final int TEXT_DIM_COLOR = 0xFF888888;
    private static final int TEXT_FAINT_COLOR = 0xFF777777;
    private static final int TEXT_HINT_COLOR = 0xFF555555;
    private static final int TEXT_SECONDARY_COLOR = 0xFFCCCCCC;
    private static final int EMPTY_STATE_BG = 0xFF161616;
    private static final int PRESET_BG_OPEN = 0xFF2E2E2E;
    private static final int PRESET_BG_CLOSED = 0xFF1E1E1E;
    private static final int PRESET_LABEL_COLOR_ACTIVE = 0xFF88FF88;
    private static final int DROPDOWN_BG = 0xFF111111;
    private static final int DROPDOWN_BG_SELECTED = 0xFF1F4D3A;
    private static final int DROPDOWN_BG_HOVER = 0xFF2A2A2A;
    private static final int DROPDOWN_BG_DEFAULT = 0xFF1A1A1A;
    private static final int DROPDOWN_TEXT_COLOR = 0xFFEFEFEF;
    private static final int DROPDOWN_HINT_COLOR = 0xFF888888;
    private static final int DROPDOWN_HOVER_COLOR = 0xFF99CCFF;
    private static final int ITEM_BG_HOVER = 0xFF3A3A3A;
    private static final int ITEM_BG_DEFAULT = 0xFF222222;
    private static final int ITEM_REMOVE_HOVER_COLOR = 0xFFFF4444;
    private static final int PREVIEW_MODE_COLOR = 0xFFFFB366;
    private static final int ACTION_ROW_BG = 0xFF1A1A1A;
    private static final int PROGRESS_BAR_BG = 0xFF1A1A1A;
    private static final int PROGRESS_BAR_FILL = 0xFF4CAF50;
    private static final int RESULT_BG = 0xFF101010;
    private static final int RESULT_SUCCESS_COLOR = 0xFF66FF66;
    private static final int RESULT_WARNING_COLOR = 0xFFFFC107;
    private static final int FAILURE_TEXT_COLOR = 0xFFFF8888;
    private static final int MORE_FAILURES_COLOR = 0xFFFFBB66;

    // Item bounds for hover/click detection
    private final List<ResponsiveLayout.Rect> itemRects = new ArrayList<>();
    private final List<ResponsiveLayout.Rect> presetOptionRects = new ArrayList<>();
    private final List<Integer> presetOptionIndices = new ArrayList<>();
    private final List<Integer> itemRectIndices = new ArrayList<>();
    private ResponsiveLayout.Rect clearRect;
    private ResponsiveLayout.Rect applyRect;
    private ResponsiveLayout.Rect presetRect;
    private ResponsiveLayout.Rect headerRect;
    private ResponsiveLayout.Rect presetDropdownArea;
    private ResponsiveLayout.Rect itemScrollArea;
    private ResponsiveLayout.Rect failureToggleRect;
    private ResponsiveLayout.Rect copyFailuresRect;
    private ResponsiveLayout.Rect moreFailuresRect;
    private ResponsiveLayout.Rect failureDetailsArea;
    private String hoveredPresetFullName = null;
    private boolean presetDropdownOpen = false;
    private int presetScrollOffset = 0;
    private int selectedPresetIndex = -1;
    private BatchEditResult lastResult = null;
    private BatchEditResult pendingStatusResult = null;
    private boolean showFailureDetails = false;
    private boolean showAllFailures = false;
    private boolean applyEnabled = false;
    private boolean clearEnabled = false;
    private boolean persistAllowed = true;
    private boolean previewOnlyMode = false;  // Preview toggle - when true, apply won't persist
    private int itemScrollOffset = 0;
    private int failureScrollOffset = 0;  // Separate scroll for failure details

    private final java.util.function.BooleanSupplier persistSupplier;
    private ResponsiveLayout.Rect previewToggleRect;

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
    private java.util.function.Consumer<ConfirmDialog> showDialogCallback;
    private static final int CONFIRM_THRESHOLD = 10;  // Show confirm dialog if > 10 items
    private static final int LARGE_BATCH_THRESHOLD = 20;  // Show timing info for large batches
    private String lastApplyTiming = null;  // e.g., "23 items in 45ms"

    // Real-time progress tracking
    private int applyProgress = -1;  // -1 means not applying, otherwise current item index
    private int applyTotal = 0;      // Total items being processed

    public MultiEditPanel(MultiEditManager manager, java.util.function.BooleanSupplier persistSupplier,
                          Supplier<String> activeItemTypeSupplier) {
        this.manager = manager;
        this.persistSupplier = persistSupplier;
        this.activeItemTypeSupplier = activeItemTypeSupplier == null ? () -> "item" : activeItemTypeSupplier;
    }

    /**
     * Set a callback to show confirmation dialogs via the parent screen.
     * @param callback Consumer that receives a ConfirmDialog to show
     */
    public void setShowDialogCallback(java.util.function.Consumer<ConfirmDialog> callback) {
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
                if (seenNames.add(converted.name.toLowerCase())) {
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
                    if (up.name != null && seenNames.add(up.name.toLowerCase())) {
                        result.add(up);
                    }
                }
            }
        } catch (Exception ignored) {}

        return result;
    }

    public int render(GuiGraphics graphics, Font font, int x, int y, int width, int mouseX, int mouseY) {
        int count = manager.getSelectionCount();
        Font safeFont = Objects.requireNonNull(font, "font cannot be null");
        itemRects.clear();
        itemRectIndices.clear();
        presetOptionRects.clear();
        presetOptionIndices.clear();
        clearRect = null;
        applyRect = null;
        presetRect = null;
        headerRect = null;
        failureToggleRect = null;
        copyFailuresRect = null;
        moreFailuresRect = null;
        failureDetailsArea = null;
        presetDropdownArea = null;
        itemScrollArea = null;
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
        graphics.drawString(safeFont, countText, x + UIConstants.Spacing.SM, y + HEADER_TEXT_Y, countColor, false);

        // Expand/collapse button
        String expandIcon = expanded ? "▼" : "▶";
        graphics.drawString(safeFont, expandIcon, x + width - HEADER_ICON_OFFSET_X, y + HEADER_TEXT_Y,
            headerHovered ? TEXT_PRIMARY_COLOR : TEXT_MUTED_COLOR, false);

        if (!expanded || count == 0) {
            presetDropdownOpen = false;
            showFailureDetails = false;
            showAllFailures = false;
            if (!expanded) {
                return headerHeight;
            }
            int emptyY = y + headerHeight + EMPTY_STATE_TOP_GAP;
            graphics.fill(x + EMPTY_STATE_BORDER, emptyY, x + width - EMPTY_STATE_BORDER, emptyY + EMPTY_STATE_HEIGHT, EMPTY_STATE_BG);
            graphics.drawString(safeFont, "No matching items in inventory", x + TEXT_INSET, emptyY + TEXT_INSET, TEXT_DIM_COLOR, false);
            graphics.drawString(safeFont, "Add items or press M to rescan", x + TEXT_INSET,
                emptyY + TEXT_INSET + EMPTY_STATE_TEXT_GAP, TEXT_HINT_COLOR, false);
            return (emptyY + EMPTY_STATE_HEIGHT) - y;
        }

        List<ItemEditorDataManager.PresetData> presets = availablePresets();
        persistAllowed = persistSupplier == null || persistSupplier.getAsBoolean();
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
        presetRect = new ResponsiveLayout.Rect(x + UIConstants.Spacing.SM, listY,
            width - UIConstants.Spacing.MD, presetHeight);
        int presetBg = presetDropdownOpen ? PRESET_BG_OPEN : PRESET_BG_CLOSED;
        graphics.fill(presetRect.x(), presetRect.y(), presetRect.right(), presetRect.bottom(), presetBg);
        String itemType = activeItemTypeSupplier.get();
        if (itemType == null || itemType.isBlank()) itemType = "item";
        String scopeLabel = "Preset (" + itemType + ")";
        graphics.drawString(safeFont, scopeLabel, presetRect.x() + TEXT_INSET, listY + UIConstants.Spacing.SM,
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
        graphics.drawString(safeFont, presetLabel, presetRect.x() + PRESET_LABEL_OFFSET_X,
            listY + UIConstants.Spacing.SM, labelColor, false);
        graphics.drawString(safeFont, presetDropdownOpen ? "▲" : "▼",
            presetRect.right() - UIConstants.Spacing.LG, listY + UIConstants.Spacing.SM, TEXT_MUTED_COLOR, false);

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
                graphics.drawString(safeFont, display, optRect.x() + TEXT_INSET,
                    optRect.y() + PRESET_OPTION_TEXT_Y, DROPDOWN_TEXT_COLOR, false);
            }

            if (maxOffset > 0) {
                String hint = "Scroll " + (startIndex + 1) + "-" + (startIndex + visibleCount) + "/" + presets.size();
                graphics.drawString(safeFont, hint, presetDropdownArea.right() - safeFont.width(hint) - PRESET_HINT_PADDING,
                    presetDropdownArea.bottom() - PRESET_HINT_OFFSET_Y, DROPDOWN_HINT_COLOR, false);
            }
            if (hoveredPresetFullName != null) {
                String hoverLine = "↳ " + hoveredPresetFullName;
                if (safeFont.width(hoverLine) > presetDropdownArea.width() - UIConstants.Spacing.MD) {
                    hoverLine = hoverLine.substring(0, Math.max(0, Math.min(hoverLine.length(), HOVER_LABEL_MAX_LENGTH)))
                        + ELLIPSIS;
                }
                graphics.drawString(safeFont, hoverLine, presetDropdownArea.x() + TEXT_INSET,
                    presetDropdownArea.bottom() - PRESET_HINT_OFFSET_Y, DROPDOWN_HOVER_COLOR, false);
            }
            listY += presetHeight + dropdownHeight + UIConstants.Spacing.SM;
        } else {
            listY += presetHeight + UIConstants.Spacing.SM;
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
            graphics.drawString(safeFont, "▪", x + UIConstants.Spacing.SM,
                listY + UIConstants.Spacing.SM, TEXT_DIM_COLOR, false);

            String name = item.getHoverName().getString();
            if (name.length() > ITEM_NAME_MAX_LENGTH) {
                name = name.substring(0, ITEM_NAME_TRUNCATE_LENGTH) + ELLIPSIS;
            }
            graphics.drawString(safeFont, name, x + UIConstants.Spacing.XL,
                listY + UIConstants.Spacing.SM, TEXT_PRIMARY_COLOR, false);

            // Remove button
            int removeX = x + width - ITEM_REMOVE_OFFSET_X;
            graphics.drawString(safeFont, "✗", removeX, listY + UIConstants.Spacing.SM,
                hovered ? ITEM_REMOVE_HOVER_COLOR : TEXT_FAINT_COLOR, false);

            listY += itemHeight;
        }

        listY += UIConstants.Spacing.SM;
        if (!persistAllowed) {
            graphics.drawString(safeFont, "Preview mode: switch to Apply to persist", x + TEXT_INSET, listY, PREVIEW_MODE_COLOR, false);
            listY += UIConstants.Spacing.LG;
        }

        // Preview toggle row
        int toggleH = PREVIEW_TOGGLE_HEIGHT;
        previewToggleRect = new ResponsiveLayout.Rect(x + UIConstants.Spacing.SM, listY, PREVIEW_TOGGLE_WIDTH, toggleH);
        previewToggleButton.toggled(previewOnlyMode);
        previewToggleButton.render(graphics, previewToggleRect.x(), previewToggleRect.y(), previewToggleRect.width(), toggleH, mouseX, mouseY);
        if (previewOnlyMode) {
            graphics.drawString(safeFont, "(will not persist)", x + PREVIEW_HINT_OFFSET_X,
                listY + UIConstants.Spacing.SM, PREVIEW_MODE_COLOR, false);
        }
        listY += toggleH + UIConstants.Spacing.SM;

        // Action buttons area using EditorButton components
        graphics.fill(x, listY, x + width, listY + UIConstants.Spacing.XXL, ACTION_ROW_BG);

        int btnH = ACTION_BUTTON_HEIGHT;
        clearRect = new ResponsiveLayout.Rect(x + UIConstants.Spacing.SM, listY + UIConstants.Spacing.SM, CLEAR_BUTTON_WIDTH, btnH);
        applyRect = new ResponsiveLayout.Rect(x + width - APPLY_BUTTON_OFFSET_X,
            listY + UIConstants.Spacing.SM, APPLY_BUTTON_WIDTH, btnH);

        clearButton.setEnabled(clearEnabled);
        applyButton.setEnabled(applyEnabled);

        clearButton.render(graphics, clearRect.x(), clearRect.y(), clearRect.width(), btnH, mouseX, mouseY);
        applyButton.render(graphics, applyRect.x(), applyRect.y(), applyRect.width(), btnH, mouseX, mouseY);

        listY += UIConstants.Spacing.XXL;

        // Progress bar during batch apply operation
        if (applyProgress >= 0 && applyTotal > 0) {
            int progressBarHeight = PROGRESS_BAR_HEIGHT;
            int progressBarY = listY;
            int progressBarWidth = width - UIConstants.Spacing.MD * 2;
            int progressBarX = x + UIConstants.Spacing.MD;

            // Background
            graphics.fill(progressBarX, progressBarY, progressBarX + progressBarWidth, progressBarY + progressBarHeight, PROGRESS_BAR_BG);

            // Progress fill
            float progress = (float) applyProgress / applyTotal;
            int fillWidth = (int) (progressBarWidth * progress);
            graphics.fill(progressBarX, progressBarY, progressBarX + fillWidth, progressBarY + progressBarHeight, PROGRESS_BAR_FILL);

            // Progress text
            String progressText = String.format("Applying... %d/%d", applyProgress, applyTotal);
            String safeProgressText = Objects.requireNonNull(progressText, "progressText");
            int textWidth = safeFont.width(safeProgressText);
            int textX = progressBarX + (progressBarWidth - textWidth) / 2;
            graphics.drawString(safeFont, safeProgressText, textX, progressBarY + PROGRESS_TEXT_OFFSET_Y, TEXT_PRIMARY_COLOR, false);

            listY += progressBarHeight + UIConstants.Spacing.SM;
        }

        if (lastResult != null) {
            int summaryHeight = SUMMARY_HEIGHT;
            int maxVisibleFailures = showAllFailures ? Math.min(MAX_VISIBLE_FAILURES, lastResult.failureCount())
                                                     : Math.min(DEFAULT_VISIBLE_FAILURES, lastResult.failureCount());
            int detailLines = showFailureDetails ? maxVisibleFailures : 0;
            int detailHeight = detailLines == 0 ? 0 : detailLines * FAILURE_DETAIL_LINE_HEIGHT + FAILURE_DETAIL_PADDING;
            int panelHeight = summaryHeight + detailHeight;

            graphics.fill(x, listY, x + width, listY + panelHeight, RESULT_BG);
            String summary = "Last apply: " + lastResult.successCount() + " success";
            if (lastResult.successCount() != 1) summary += "es";
            if (lastResult.failureCount() > 0) {
                summary += ", " + lastResult.failureCount() + " failed";
            }
            // Show timing info for large batch operations
            if (lastApplyTiming != null) {
                summary += " (" + lastApplyTiming + ")";
            }
            int summaryColor = lastResult.failureCount() == 0 ? RESULT_SUCCESS_COLOR : RESULT_WARNING_COLOR;
            graphics.drawString(safeFont, summary, x + TEXT_INSET, listY + UIConstants.Spacing.SM, summaryColor, false);

            if (lastResult.failureCount() > 0) {
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
                failureToggleRect = null;
                copyFailuresRect = null;
                moreFailuresRect = null;
                showFailureDetails = false;
                showAllFailures = false;
            }

            if (showFailureDetails && lastResult.failureCount() > 0) {
                int detailY = listY + summaryHeight;
                List<BatchEditResult.FailureDetail> details = lastResult.failureDetails();
                int maxLines = Math.min(detailLines, details.size());
                int maxFailureOffset = Math.max(0, details.size() - maxLines);
                if (failureScrollOffset > maxFailureOffset) failureScrollOffset = maxFailureOffset;
                int startIdx = Math.max(0, Math.min(maxFailureOffset, failureScrollOffset));
                int endIdx = Math.min(details.size(), startIdx + maxLines);

                // Define scroll area for failure details (reuse detailHeight from above)
                failureDetailsArea = new ResponsiveLayout.Rect(x, detailY, width, detailHeight);

                for (int i = startIdx; i < endIdx; i++) {
                    BatchEditResult.FailureDetail d = details.get(i);
                    String line = "slot#" + d.slot + " " + (d.itemId == null ? "<unknown-id>" : d.itemId) + " - " + d.message;
                    if (line.length() > FAILURE_LINE_MAX_LENGTH) {
                        line = line.substring(0, FAILURE_LINE_TRUNCATE_LENGTH) + ELLIPSIS;
                    }
                    graphics.drawString(safeFont, "• " + line, x + UIConstants.Spacing.MD,
                        detailY + (i - startIdx) * FAILURE_DETAIL_LINE_HEIGHT, FAILURE_TEXT_COLOR, false);
                }
                if (details.size() > endIdx) {
                    String moreText = "(+" + (details.size() - endIdx) + " more)";
                    moreFailuresRect = new ResponsiveLayout.Rect(x + UIConstants.Spacing.MD,
                        detailY + (endIdx - startIdx) * FAILURE_DETAIL_LINE_HEIGHT,
                        safeFont.width(moreText) + FAILURE_MORE_WIDTH_PADDING, FAILURE_MORE_HEIGHT);
                    graphics.drawString(safeFont, moreText, moreFailuresRect.x(), moreFailuresRect.y(), MORE_FAILURES_COLOR, false);
                } else {
                    moreFailuresRect = null;
                }
            }

            listY += panelHeight + UIConstants.Spacing.SM;
        }

        return listY - y;
    }
    /**
     * Handle mouse clicks inside the panel. Returns true if consumed.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (headerRect != null && headerRect.contains(mouseX, mouseY)) {
            expanded = !expanded;
            if (!expanded) {
                presetDropdownOpen = false;
                showFailureDetails = false;
                showAllFailures = false;
            }
            return true;
        }

        if (!expanded) return false;

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
                if (itemCount > CONFIRM_THRESHOLD && !previewOnlyMode && showDialogCallback != null) {
                    var dialog = ConfirmDialog.batchApply(itemCount, presetName,
                        () -> doApply(presetData),  // onConfirm
                        () -> {}                     // onCancel
                    );
                    showDialogCallback.accept(dialog);
                } else {
                    doApply(presetData);
                }
            }
            return true;
        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!expanded) return false;

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
        if (failureDetailsArea != null && failureDetailsArea.contains(mouseX, mouseY) && lastResult != null) {
            int failureCount = lastResult.failureCount();
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
        boolean persist = !previewOnlyMode && (persistSupplier == null || persistSupplier.getAsBoolean());
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
        if (lastResult == null || lastResult.failureCount() == 0) return;
        try {
            StringBuilder sb = new StringBuilder();
            for (BatchEditResult.FailureDetail d : lastResult.failureDetails()) {
                sb.append(java.util.Objects.requireNonNull(d.toString(), "failure detail cannot be null")).append("\n");
                if (d.stackTrace != null) {
                    sb.append(d.stackTrace).append("\n");
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
        if (lastResult == null || lastResult.failureCount() == 0) return;
        try {
            java.nio.file.Path out = java.nio.file.Paths.get("multiedit_failures_" + System.currentTimeMillis() + ".log");
            try (java.io.BufferedWriter w = java.nio.file.Files.newBufferedWriter(out)) {
                for (BatchEditResult.FailureDetail d : lastResult.failureDetails()) {
                    w.write(d.toString()); w.newLine();
                    if (d.stackTrace != null) {
                        w.write(d.stackTrace); w.newLine();
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
            } catch (Exception ignored) {}
        } catch (Exception ignored) {
            // best-effort export only
        }
    }

    /**
     * Retrieve and clear the last batch edit result (if any).
     */
    public BatchEditResult takeLastResult() {
        BatchEditResult r = pendingStatusResult;
        pendingStatusResult = null;
        return r;
    }
}
