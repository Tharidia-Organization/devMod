package com.frenkvs.devmod.ui.editor.systems;

import com.frenkvs.devmod.ui.editor.core.ResponsiveLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import com.frenkvs.devmod.ItemEditorDataManager;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Simple UI panel for multi-edit selection. This is a lightweight implementation
 * intended to match the sample in `EDITOR_DESIGN_SYSTEM.md` and to provide concrete
 * hover tracking and basic remove interaction.
 */
public class MultiEditPanel {
    private final MultiEditManager manager;
    private boolean expanded = false;
    private static final int MAX_VISIBLE_PRESETS = 8;

    // Item bounds for hover/click detection
    private final List<ResponsiveLayout.Rect> itemRects = new ArrayList<>();
    private final List<ResponsiveLayout.Rect> presetOptionRects = new ArrayList<>();
    private final List<Integer> presetOptionIndices = new ArrayList<>();
    private ResponsiveLayout.Rect clearRect;
    private ResponsiveLayout.Rect applyRect;
    private ResponsiveLayout.Rect presetRect;
    private ResponsiveLayout.Rect presetDropdownArea;
    private ResponsiveLayout.Rect failureToggleRect;
    private ResponsiveLayout.Rect copyFailuresRect;
    private boolean presetDropdownOpen = false;
    private int presetScrollOffset = 0;
    private int selectedPresetIndex = -1;
    private BatchEditResult lastResult = null;
    private BatchEditResult pendingStatusResult = null;
    private boolean showFailureDetails = false;

    private List<ItemEditorDataManager.PresetData> availablePresets() {
        try {
            ItemEditorDataManager.INSTANCE.ensureInitialized();
            return ItemEditorDataManager.INSTANCE.getPresets();
        } catch (Exception e) {
            return List.of();
        }
    }

    public MultiEditPanel(MultiEditManager manager) {
        this.manager = manager;
    }

    public void setExpanded(boolean expanded) { this.expanded = expanded; }
    public boolean isExpanded() { return expanded; }


    public int render(GuiGraphics graphics, Font font, int x, int y, int width, int mouseX, int mouseY) {
        int count = manager.getSelectionCount();
        Font safeFont = Objects.requireNonNull(font, "font cannot be null");
        itemRects.clear();
        presetOptionRects.clear();
        presetOptionIndices.clear();
        failureToggleRect = null;
        copyFailuresRect = null;
        presetDropdownArea = null;

        // Header bar
        int headerHeight = 20;
        graphics.fill(x, y, x + width, y + headerHeight, 0xFF2A2A2A);

        // Selection count
        String countText = count + " item" + (count != 1 ? "s" : "") + " selected";
        graphics.drawString(safeFont, countText, x + 4, y + 6, 0xFFFFFFFF, false);

        // Expand/collapse button
        String expandIcon = expanded ? "▼" : "▶";
        graphics.drawString(safeFont, expandIcon, x + width - 15, y + 6, 0xFFAAAAAA, false);

        if (!expanded || count == 0) {
            presetDropdownOpen = false;
            return headerHeight;
        }

        List<ItemEditorDataManager.PresetData> presets = availablePresets();
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

        int listY = y + headerHeight;
        int itemHeight = 18;

        // Preset selector area (dropdown)
        int presetHeight = 18;
        presetRect = new ResponsiveLayout.Rect(x + 4, listY, width - 8, presetHeight);
        int presetBg = presetDropdownOpen ? 0xFF2E2E2E : 0xFF1E1E1E;
        graphics.fill(presetRect.x(), presetRect.y(), presetRect.right(), presetRect.bottom(), presetBg);
        graphics.drawString(safeFont, "Preset", presetRect.x() + 6, listY + 4, 0xFFCCCCCC, false);

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
            int visibleCount = Math.min(MAX_VISIBLE_PRESETS, presets.size());
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
                if (display.length() > 30) display = display.substring(0, 27) + "...";
                graphics.drawString(safeFont, display, optRect.x() + 6, optRect.y() + 3, 0xFFEFEFEF, false);
            }

            if (maxOffset > 0) {
                String hint = "Scroll " + (startIndex + 1) + "-" + (startIndex + visibleCount) + "/" + presets.size();
                graphics.drawString(safeFont, hint, presetDropdownArea.right() - safeFont.width(hint) - 4, presetDropdownArea.bottom() - 10, 0xFF888888, false);
            }
            listY += presetHeight + dropdownHeight + 4;
        } else {
            listY += presetHeight + 4;
        }

        for (int i = 0; i < manager.getSelectedItems().size(); i++) {
            ItemStack item = manager.getSelectedItems().get(i);

            ResponsiveLayout.Rect rect = new ResponsiveLayout.Rect(x, listY, width, itemHeight);
            itemRects.add(rect);

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

        // Action buttons area
        listY += 4;
        graphics.fill(x, listY, x + width, listY + 24, 0xFF1A1A1A);

        clearRect = new ResponsiveLayout.Rect(x + 4, listY + 4, 80, 16);
        applyRect = new ResponsiveLayout.Rect(x + width - 100, listY + 4, 96, 16);

        graphics.fill(clearRect.x(), clearRect.y(), clearRect.x() + clearRect.width(), clearRect.y() + clearRect.height(), 0xFF2A1A1A);
        graphics.drawString(safeFont, "[Clear All]", clearRect.x() + 4, clearRect.y() + 3, 0xFFFF8888, false);

        graphics.fill(applyRect.x(), applyRect.y(), applyRect.x() + applyRect.width(), applyRect.y() + applyRect.height(), 0xFF1A2A1A);
        graphics.drawString(safeFont, "[Apply to all]", applyRect.x() + 4, applyRect.y() + 3, 0xFF88FF88, false);

        listY += 24;

        if (lastResult != null) {
            int summaryHeight = 18;
            int detailLines = showFailureDetails ? Math.min(6, lastResult.failureCount()) : 0;
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
                failureToggleRect = new ResponsiveLayout.Rect(x + width - 70, listY + 2, 64, 14);
                copyFailuresRect = new ResponsiveLayout.Rect(x + width - 140, listY + 2, 64, 14);
                graphics.fill(copyFailuresRect.x(), copyFailuresRect.y(), copyFailuresRect.right(), copyFailuresRect.bottom(), 0xFF222222);
                graphics.drawString(safeFont, "Copy", copyFailuresRect.x() + 10, copyFailuresRect.y() + 3, 0xFFEEEEEE, false);
                graphics.fill(failureToggleRect.x(), failureToggleRect.y(), failureToggleRect.right(), failureToggleRect.bottom(), 0xFF222222);
                graphics.drawString(safeFont, showFailureDetails ? "Hide" : "Details", failureToggleRect.x() + 6, failureToggleRect.y() + 3, 0xFFEEEEEE, false);
            } else {
                failureToggleRect = null;
                copyFailuresRect = null;
                showFailureDetails = false;
            }

            if (showFailureDetails && lastResult.failureCount() > 0) {
                int detailY = listY + summaryHeight;
                List<String> failures = lastResult.failures();
                int maxLines = Math.min(detailLines, failures.size());
                for (int i = 0; i < maxLines; i++) {
                    String line = failures.get(i);
                    if (line.length() > 40) line = line.substring(0, 37) + "...";
                    graphics.drawString(safeFont, "• " + line, x + 8, detailY + i * 14, 0xFFFF8888, false);
                }
                if (failures.size() > maxLines) {
                    graphics.drawString(safeFont, "(+" + (failures.size() - maxLines) + " more)", x + 8, detailY + maxLines * 14, 0xFFFF8888, false);
                }
            }

            listY += panelHeight + 4;
        }

        return listY - y;
    }
    /**
     * Handle mouse clicks inside the panel. Returns true if consumed.
     */
    
    /**
     * Handle mouse clicks inside the panel. Returns true if consumed.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
                    manager.removeFromSelection(i);
                    return true;
                }
            }
        }

        if (copyFailuresRect != null && copyFailuresRect.contains(mouseX, mouseY)) {
            copyFailuresToClipboard();
            return true;
        }
        if (failureToggleRect != null && failureToggleRect.contains(mouseX, mouseY)) {
            showFailureDetails = !showFailureDetails;
            return true;
        }

        // Clear all
        if (clearRect != null && clearRect.contains(mouseX, mouseY)) {
            manager.clearSelection();
            return true;
        }

        // Apply to all
        if (applyRect != null && applyRect.contains(mouseX, mouseY)) {
            if (selectedPresetIndex >= 0 && selectedPresetIndex < presets.size()) {
                var presetData = presets.get(selectedPresetIndex);
                var dataPreset = new DataPreset(presetData);
                var adapter = ItemEditorPresetManager.INSTANCE;
                lastResult = manager.applyPresetToAll(dataPreset, adapter);
                pendingStatusResult = lastResult;
                showFailureDetails = lastResult != null && lastResult.failureCount() > 0;
            }
            return true;
        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!expanded || !presetDropdownOpen || presetDropdownArea == null) return false;
        if (!presetDropdownArea.contains(mouseX, mouseY)) return false;

        var presets = availablePresets();
        if (presets.isEmpty()) return false;
        int visibleCount = Math.min(MAX_VISIBLE_PRESETS, presets.size());
        int maxOffset = Math.max(0, presets.size() - visibleCount);
        if (maxOffset == 0) return false;

        int delta = (int) Math.signum(scrollY);
        presetScrollOffset = Math.max(0, Math.min(maxOffset, presetScrollOffset - delta));
        return true;
    }

    private void copyFailuresToClipboard() {
        if (lastResult == null || lastResult.failureCount() == 0) return;
        try {
            String payload = String.join("\\n", lastResult.failures());
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.keyboardHandler != null) {
                mc.keyboardHandler.setClipboard(payload);
            }
        } catch (Exception ignored) {
            // best-effort copy only
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
