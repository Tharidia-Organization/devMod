package com.frenkvs.devmod.ui.editor.systems;

import com.frenkvs.devmod.ui.editor.components.EditorButton;
import com.frenkvs.devmod.ui.editor.core.ResponsiveLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import com.frenkvs.devmod.ItemEditorDataManager;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
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
    private int itemScrollOffset = 0;

    private final java.util.function.BooleanSupplier persistSupplier;

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

    public MultiEditPanel(MultiEditManager manager, java.util.function.BooleanSupplier persistSupplier,
                          Supplier<String> activeItemTypeSupplier) {
        this.manager = manager;
        this.persistSupplier = persistSupplier;
        this.activeItemTypeSupplier = activeItemTypeSupplier == null ? () -> "item" : activeItemTypeSupplier;
    }

    public void setExpanded(boolean expanded) { this.expanded = expanded; }
    public boolean isExpanded() { return expanded; }

    private List<ItemEditorDataManager.PresetData> availablePresets() {
        try {
            ItemEditorDataManager.INSTANCE.ensureInitialized();
            String type = activeItemTypeSupplier.get();
            if (type != null && !type.isBlank()) {
                List<ItemEditorDataManager.PresetData> scoped = ItemEditorDataManager.INSTANCE.getPresetsForItemType(type);
                if (scoped != null && !scoped.isEmpty()) return scoped;
            }
            return ItemEditorDataManager.INSTANCE.getPresets();
        } catch (Exception e) {
            return List.of();
        }
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
        presetDropdownArea = null;
        itemScrollArea = null;
        hoveredPresetFullName = null;
        applyEnabled = false;
        clearEnabled = false;

        // Header bar
        int headerHeight = 20;
        headerRect = new ResponsiveLayout.Rect(x, y, width, headerHeight);
        boolean headerHovered = headerRect.contains(mouseX, mouseY);
        int headerBg = headerHovered ? 0xFF333333 : 0xFF2A2A2A;
        graphics.fill(headerRect.x(), headerRect.y(), headerRect.right(), headerRect.bottom(), headerBg);

        // Selection count
        String countText = count + " item" + (count != 1 ? "s" : "") + " selected";
        int countColor = count == 0 ? 0xFFAAAAAA : 0xFFFFFFFF;
        graphics.drawString(safeFont, countText, x + 4, y + 6, countColor, false);

        // Expand/collapse button
        String expandIcon = expanded ? "▼" : "▶";
        graphics.drawString(safeFont, expandIcon, x + width - 15, y + 6, headerHovered ? 0xFFFFFFFF : 0xFFAAAAAA, false);

        if (!expanded || count == 0) {
            presetDropdownOpen = false;
            showFailureDetails = false;
            showAllFailures = false;
            if (!expanded) {
                return headerHeight;
            }
            int emptyY = y + headerHeight + 6;
            graphics.fill(x + 2, emptyY, x + width - 2, emptyY + 30, 0xFF161616);
            graphics.drawString(safeFont, "No matching items in inventory", x + 6, emptyY + 6, 0xFF888888, false);
            graphics.drawString(safeFont, "Add items or press M to rescan", x + 6, emptyY + 16, 0xFF555555, false);
            return (emptyY + 30) - y;
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
        int itemHeight = 18;

        // Preset selector area (dropdown)
        int presetHeight = 18;
        presetRect = new ResponsiveLayout.Rect(x + 4, listY, width - 8, presetHeight);
        int presetBg = presetDropdownOpen ? 0xFF2E2E2E : 0xFF1E1E1E;
        graphics.fill(presetRect.x(), presetRect.y(), presetRect.right(), presetRect.bottom(), presetBg);
        String itemType = activeItemTypeSupplier.get();
        if (itemType == null || itemType.isBlank()) itemType = "item";
        String scopeLabel = "Preset (" + itemType + ")";
        graphics.drawString(safeFont, scopeLabel, presetRect.x() + 6, listY + 4, 0xFFCCCCCC, false);

        String presetLabel = "(no presets)";
        if (!presets.isEmpty() && selectedPresetIndex >= 0 && selectedPresetIndex < presets.size()) {
            String name = presets.get(selectedPresetIndex).name;
            presetLabel = (name == null || name.isEmpty()) ? "Unnamed" : name;
        }
        if (presetLabel.length() > 28) presetLabel = presetLabel.substring(0, 25) + "...";
        int labelColor = presets.isEmpty() ? 0xFF777777 : 0xFF88FF88;
        graphics.drawString(safeFont, presetLabel, presetRect.x() + 60, listY + 4, labelColor, false);
        graphics.drawString(safeFont, presetDropdownOpen ? "▲" : "▼", presetRect.right() - 12, listY + 4, 0xFFAAAAAA, false);

        if (presetDropdownOpen && !presets.isEmpty()) {
            int optionHeight = 16;
            int visibleCount = Math.min(10, Math.min(MAX_VISIBLE_PRESETS, presets.size()));
            int maxOffset = Math.max(0, presets.size() - visibleCount);
            int startIndex = Math.min(presetScrollOffset, maxOffset);
            int dropdownHeight = visibleCount * optionHeight;
            presetDropdownArea = new ResponsiveLayout.Rect(presetRect.x(), presetRect.bottom(), presetRect.width(), dropdownHeight);
            graphics.fill(presetDropdownArea.x(), presetDropdownArea.y(), presetDropdownArea.right(), presetDropdownArea.bottom(), 0xFF111111);

            for (int i = 0; i < visibleCount; i++) {
                int idx = startIndex + i;
                ResponsiveLayout.Rect optRect = new ResponsiveLayout.Rect(presetRect.x(), presetRect.bottom() + i * optionHeight, presetRect.width(), optionHeight);
                presetOptionRects.add(optRect);
                presetOptionIndices.add(idx);
                boolean hovered = optRect.contains(mouseX, mouseY);
                boolean selected = idx == selectedPresetIndex;
                int bg = selected ? 0xFF1F4D3A : hovered ? 0xFF2A2A2A : 0xFF1A1A1A;
                graphics.fill(optRect.x(), optRect.y(), optRect.right(), optRect.bottom(), bg);
                String name = presets.get(idx).name;
                String display = (name == null || name.isEmpty()) ? "Unnamed preset" : name;
                if (hovered) {
                    hoveredPresetFullName = display;
                }
                if (display.length() > 30) display = display.substring(0, 27) + "...";
                graphics.drawString(safeFont, display, optRect.x() + 6, optRect.y() + 3, 0xFFEFEFEF, false);
            }

            if (maxOffset > 0) {
                String hint = "Scroll " + (startIndex + 1) + "-" + (startIndex + visibleCount) + "/" + presets.size();
                graphics.drawString(safeFont, hint, presetDropdownArea.right() - safeFont.width(hint) - 4, presetDropdownArea.bottom() - 10, 0xFF888888, false);
            }
            if (hoveredPresetFullName != null) {
                String hoverLine = "↳ " + hoveredPresetFullName;
                if (safeFont.width(hoverLine) > presetDropdownArea.width() - 8) {
                    hoverLine = hoverLine.substring(0, Math.max(0, Math.min(hoverLine.length(), 30))) + "...";
                }
                graphics.drawString(safeFont, hoverLine, presetDropdownArea.x() + 6, presetDropdownArea.bottom() - 10, 0xFF99CCFF, false);
            }
            listY += presetHeight + dropdownHeight + 4;
        } else {
            listY += presetHeight + 4;
        }

        // Virtualized item list with scrolling
        int maxVisibleItems = 10;
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
            int bg = hovered ? 0xFF3A3A3A : 0xFF222222;
            graphics.fill(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height(), bg);

            // Item icon placeholder
            graphics.drawString(safeFont, "▪", x + 4, listY + 4, 0xFF888888, false);

            String name = item.getHoverName().getString();
            if (name.length() > 25) name = name.substring(0, 22) + "...";
            graphics.drawString(safeFont, name, x + 16, listY + 4, 0xFFFFFFFF, false);

            // Remove button
            int removeX = x + width - 18;
            graphics.drawString(safeFont, "✗", removeX, listY + 4, hovered ? 0xFFFF4444 : 0xFF777777, false);

            listY += itemHeight;
        }

        listY += 4;
        if (!persistAllowed) {
            graphics.drawString(safeFont, "Preview mode: switch to Apply to persist", x + 6, listY, 0xFFFFB366, false);
            listY += 12;
        }

        // Action buttons area using EditorButton components
        graphics.fill(x, listY, x + width, listY + 24, 0xFF1A1A1A);

        int btnH = 16;
        clearRect = new ResponsiveLayout.Rect(x + 4, listY + 4, 80, btnH);
        applyRect = new ResponsiveLayout.Rect(x + width - 100, listY + 4, 96, btnH);

        clearButton.setEnabled(clearEnabled);
        applyButton.setEnabled(applyEnabled);

        clearButton.render(graphics, clearRect.x(), clearRect.y(), clearRect.width(), btnH, mouseX, mouseY);
        applyButton.render(graphics, applyRect.x(), applyRect.y(), applyRect.width(), btnH, mouseX, mouseY);

        listY += 24;

        if (lastResult != null) {
            int summaryHeight = 18;
            int maxVisibleFailures = showAllFailures ? Math.min(20, lastResult.failureCount()) : Math.min(6, lastResult.failureCount());
            int detailLines = showFailureDetails ? maxVisibleFailures : 0;
            int detailHeight = detailLines == 0 ? 0 : detailLines * 14 + 6;
            int panelHeight = summaryHeight + detailHeight;

            graphics.fill(x, listY, x + width, listY + panelHeight, 0xFF101010);
            String summary = "Last apply: " + lastResult.successCount() + " success";
            if (lastResult.successCount() != 1) summary += "es";
            if (lastResult.failureCount() > 0) {
                summary += ", " + lastResult.failureCount() + " failed";
            }
            int summaryColor = lastResult.failureCount() == 0 ? 0xFF66FF66 : 0xFFFFC107;
            graphics.drawString(safeFont, summary, x + 6, listY + 4, summaryColor, false);

            if (lastResult.failureCount() > 0) {
                int failBtnH = 14;
                failureToggleRect = new ResponsiveLayout.Rect(x + width - 70, listY + 2, 64, failBtnH);
                copyFailuresRect = new ResponsiveLayout.Rect(x + width - 145, listY + 2, 70, failBtnH);
                ResponsiveLayout.Rect exportRect = new ResponsiveLayout.Rect(x + width - 210, listY + 2, 60, failBtnH);

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
                int startIdx = Math.max(0, Math.min(details.size() - maxLines, itemScrollOffset)); // reuse offset logic pattern
                int endIdx = Math.min(details.size(), startIdx + maxLines);
                for (int i = startIdx; i < endIdx; i++) {
                    BatchEditResult.FailureDetail d = details.get(i);
                    String line = "slot#" + d.slot + " " + (d.itemId == null ? "<unknown-id>" : d.itemId) + " - " + d.message;
                    if (line.length() > 60) line = line.substring(0, 57) + "...";
                    graphics.drawString(safeFont, "• " + line, x + 8, detailY + (i - startIdx) * 14, 0xFFFF8888, false);
                }
                if (details.size() > endIdx) {
                    String moreText = "(+" + (details.size() - endIdx) + " more)";
                    moreFailuresRect = new ResponsiveLayout.Rect(x + 8, detailY + (endIdx - startIdx) * 14, safeFont.width(moreText) + 6, 12);
                    graphics.drawString(safeFont, moreText, moreFailuresRect.x(), moreFailuresRect.y(), 0xFFFFBB66, false);
                } else {
                    moreFailuresRect = null;
                }
            }

            listY += panelHeight + 4;
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
                // If clicked near right edge, interpret as remove (12 px from right)
                if (mouseX >= rect.x() + rect.width() - 18) {
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

        // Clear all using EditorButton
        if (clearButton.mouseClicked(mouseX, mouseY, 0)) {
            clearButton.mouseReleased(mouseX, mouseY, 0);
            if (clearEnabled) {
                manager.clearSelection();
                lastResult = null;
                pendingStatusResult = null;
            }
            return true;
        }

        // Apply to all using EditorButton
        if (applyButton.mouseClicked(mouseX, mouseY, 0)) {
            applyButton.mouseReleased(mouseX, mouseY, 0);
            if (applyEnabled && selectedPresetIndex >= 0 && selectedPresetIndex < presets.size()) {
                var presetData = presets.get(selectedPresetIndex);
                var dataPreset = new DataPreset(presetData);
                var adapter = ItemEditorPresetManager.INSTANCE;
                boolean persist = persistSupplier == null ? true : persistSupplier.getAsBoolean();
                lastResult = manager.applyPresetToAll(dataPreset, adapter, persist);
                pendingStatusResult = lastResult;
                showFailureDetails = lastResult != null && lastResult.failureCount() > 0;
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
            int visibleCount = Math.min(MAX_VISIBLE_PRESETS, presets.size());
            int maxOffset = Math.max(0, presets.size() - visibleCount);
            if (maxOffset == 0) return false;

            int delta = (int) Math.signum(scrollY);
            presetScrollOffset = Math.max(0, Math.min(maxOffset, presetScrollOffset - delta));
            return true;
        }

        // Scroll inside item list
        if (itemScrollArea != null && itemScrollArea.contains(mouseX, mouseY)) {
            int count = manager.getSelectionCount();
            int maxVisible = 10;
            int maxOffset = Math.max(0, count - maxVisible);
            if (maxOffset == 0) return false;
            int delta = (int) Math.signum(scrollY);
            itemScrollOffset = Math.max(0, Math.min(maxOffset, itemScrollOffset - delta));
            return true;
        }

        return false;
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
