package com.frenkvs.devmod.ui.editor.debug;

import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.editor.EditorSection;
import com.frenkvs.devmod.ui.editor.core.EditorDimensions;
import com.frenkvs.devmod.ui.editor.core.ResponsiveLayout;
import com.frenkvs.devmod.ui.editor.core.UIConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Locale;

/**
 * Custom section rendering the debug information panel.
 */
public final class DebugInfoSection implements EditorSection.CustomSection {

    private static final int LINE_HEIGHT = 12;
    private static final int BUTTON_WIDTH = 90;
    private static final int BUTTON_HEIGHT = 18;

    private final ItemDebugInfo debugInfo;
    private final List<ValueComparison> comparisons;
    private final List<String> changeLog;
    private final List<String> nbtLines;
    private final Runnable onCopy;
    private final List<String> infoLines;
    private final int totalHeight;

    private ResponsiveLayout.Rect copyButtonBounds = ResponsiveLayout.Rect.EMPTY;

    public DebugInfoSection(ItemDebugInfo debugInfo,
                            List<ValueComparison> comparisons,
                            List<String> changeLog,
                            List<String> nbtLines,
                            Runnable onCopy) {
        this.debugInfo = Objects.requireNonNull(debugInfo, "debug info cannot be null");
        this.comparisons = List.copyOf(comparisons);
        this.changeLog = List.copyOf(changeLog);
        this.nbtLines = List.copyOf(nbtLines);
        this.onCopy = onCopy;
        this.infoLines = buildInfoLines();
        this.totalHeight = calculateHeight();
    }

    private List<String> buildInfoLines() {
        List<String> lines = new ArrayList<>();
        String registry = debugInfo.registryName() == null ? "<unknown>" : debugInfo.registryName();
        lines.add("Registry: " + registry);
        lines.add("Stack size: " + debugInfo.stackSize());
        lines.add("Damage: " + debugInfo.currentDamage() + "/" + debugInfo.maxDamage());
        lines.add("NBT tags: " + debugInfo.nbtTagCount());
        lines.add("Custom data: " + (debugInfo.hasCustomData() ? "yes" : "no"));
        return lines;
    }

    private int calculateHeight() {
        int height = EditorDimensions.SECTION_HEADER_HEIGHT + UIConstants.Spacing.SM;
        height += infoLines.size() * LINE_HEIGHT + UIConstants.Spacing.SM;
        height += LINE_HEIGHT; // label
        height += Math.max(1, comparisons.size()) * LINE_HEIGHT + UIConstants.Spacing.SM;
        height += LINE_HEIGHT; // session log label
        height += Math.max(1, changeLog.size()) * LINE_HEIGHT + UIConstants.Spacing.SM;
        height += LINE_HEIGHT; // NBT label
        height += Math.max(1, nbtLines.size()) * LINE_HEIGHT;
        height += UIConstants.Spacing.MD;
        return height;
    }

    @Override
    public String getId() {
        return "debug";
    }

    @Override
    public String getLabel() {
        return "Debug Info";
    }

    @Override
    public int getHeight() {
        return totalHeight;
    }

    @Override
    public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
        Font font = Objects.requireNonNull(Minecraft.getInstance().font, "font cannot be null");
        int x = bounds.x();
        int y = bounds.y();
        int width = bounds.width();

        // Header background
        graphics.fill(x, y, x + width, y + EditorDimensions.SECTION_HEADER_HEIGHT, UIConstants.Background.HEADER());
        String title = "DEBUG INFO";
        graphics.drawString(font, title, x + UIConstants.Spacing.SM, y + (EditorDimensions.SECTION_HEADER_HEIGHT - 8) / 2,
            UIConstants.Accent.CYAN(), false);

        int btnX = x + width - BUTTON_WIDTH - UIConstants.Spacing.SM;
        int btnY = y + (EditorDimensions.SECTION_HEADER_HEIGHT - BUTTON_HEIGHT) / 2;
        copyButtonBounds = new ResponsiveLayout.Rect(btnX, btnY, BUTTON_WIDTH, BUTTON_HEIGHT);
        boolean hovering = copyButtonBounds.contains(mouseX, mouseY);
        int btnBg = hovering ? UIConstants.Background.HOVER() : UIConstants.Background.INPUT();
        graphics.fill(btnX, btnY, btnX + BUTTON_WIDTH, btnY + BUTTON_HEIGHT, btnBg);
        AxiomRenderer.drawBorder(graphics, btnX, btnY, BUTTON_WIDTH, BUTTON_HEIGHT, UIConstants.Border.ACCENT());
        String buttonText = "Copy Debug";
        int textX = btnX + (BUTTON_WIDTH - font.width(buttonText)) / 2;
        int textY = btnY + (BUTTON_HEIGHT - 8) / 2;
        graphics.drawString(font, buttonText, textX, textY, UIConstants.Text.PRIMARY(), false);

        int contentY = y + EditorDimensions.SECTION_HEADER_HEIGHT + UIConstants.Spacing.SM;
        int indentX = x + UIConstants.Spacing.SM;

        for (String line : infoLines) {
            graphics.drawString(font, line, indentX, contentY, UIConstants.Text.SECONDARY(), false);
            contentY += LINE_HEIGHT;
        }

        contentY += UIConstants.Spacing.SM;
        contentY = renderComparisonBlock(graphics, font, indentX, contentY);
        contentY += UIConstants.Spacing.SM;
        contentY = renderHistoryBlock(graphics, font, indentX, contentY);
        contentY += UIConstants.Spacing.SM;
        contentY = renderNbtBlock(graphics, font, indentX, contentY);
    }

    private int renderComparisonBlock(GuiGraphics graphics, Font font, int x, int y) {
        Font safeFont = Objects.requireNonNull(font, "font cannot be null");
        graphics.drawString(safeFont, "Value comparisons", x, y, UIConstants.Text.TITLE(), false);
        y += LINE_HEIGHT;
        if (comparisons.isEmpty()) {
            graphics.drawString(safeFont, "(none)", x + 4, y, UIConstants.Text.MUTED(), false);
            return y + LINE_HEIGHT;
        }
        boolean hasServerBaseline = comparisons.stream().anyMatch(c -> !Double.isNaN(c.serverValue()));
        if (!hasServerBaseline) {
            graphics.drawString(safeFont, "(Server/config baseline not available — showing item only)", x + 4, y,
                UIConstants.Text.MUTED(), false);
            y += LINE_HEIGHT;
        }
        for (ValueComparison comp : comparisons) {
            String orig = formatValue(comp.originalValue());
            String srv = formatValue(comp.serverValue());
            String cur = formatValue(comp.currentValue());
            String suffix = comp.hasMismatch() ? " [MISMATCH]" :
                comp.isModified() ? " [MOD]" :
                Double.isNaN(comp.serverValue()) ? " [SERVER N/A]" : "";

            int color = comp.hasMismatch() ? UIConstants.Accent.RED() :
                comp.isModified() ? UIConstants.Accent.YELLOW :
                Double.isNaN(comp.serverValue()) ? UIConstants.Text.MUTED() : UIConstants.Text.SECONDARY();

            String line = String.format("%-18s orig:%7s srv:%7s cur:%7s%s",
                comp.attributeName(), orig, srv, cur, suffix);
            graphics.drawString(safeFont, line, x, y, color, false);
            y += LINE_HEIGHT;
        }
        return y;
    }

    private String formatValue(double value) {
        return Double.isNaN(value) ? "n/a" : String.format(Locale.US, "%.2f", value);
    }

    private int renderHistoryBlock(GuiGraphics graphics, Font font, int x, int y) {
        Font safeFont = Objects.requireNonNull(font, "font cannot be null");
        graphics.drawString(safeFont, "Session log", x, y, UIConstants.Text.TITLE(), false);
        y += LINE_HEIGHT;
        if (changeLog.isEmpty()) {
            graphics.drawString(safeFont, "(no entries)", x + 4, y, UIConstants.Text.MUTED(), false);
            return y + LINE_HEIGHT;
        }
        for (String entry : changeLog) {
            graphics.drawString(safeFont, entry, x + 4, y, UIConstants.Text.SECONDARY(), false);
            y += LINE_HEIGHT;
        }
        return y;
    }

    private int renderNbtBlock(GuiGraphics graphics, Font font, int x, int y) {
        Font safeFont = Objects.requireNonNull(font, "font cannot be null");
        graphics.drawString(safeFont, "NBT data", x, y, UIConstants.Text.TITLE(), false);
        y += LINE_HEIGHT;
        if (nbtLines.isEmpty()) {
            graphics.drawString(safeFont, "(empty)", x + 4, y, UIConstants.Text.MUTED(), false);
            return y + LINE_HEIGHT;
        }
        for (String line : nbtLines) {
            graphics.drawString(safeFont, line, x + 4, y, UIConstants.Text.FORMULA(), false);
            y += LINE_HEIGHT;
        }
        return y;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (copyButtonBounds.contains(mouseX, mouseY) && onCopy != null) {
            onCopy.run();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return false;
    }

    public static List<String> formatNbtLines(CompoundTag tag, int maxLines) {
        if (tag == null || tag.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        int[] count = {0};
        formatTag(lines, tag, "", maxLines, count);
        return lines.isEmpty() ? List.of("(empty)") : lines;
    }

    private static void formatTag(List<String> lines, CompoundTag tag, String indent, int maxLines, int[] count) {
        String safeIndent = Objects.requireNonNull(indent, "indent cannot be null");
        for (String key : tag.getAllKeys()) {
            if (count[0] >= maxLines) return;
            String safeKey = Objects.requireNonNull(key, "nbt key cannot be null");
            var value = tag.get(safeKey);
            String prefix = safeIndent + safeKey + ": ";
            if (value instanceof CompoundTag nested) {
                lines.add(prefix + "{");
                count[0]++;
                if (count[0] >= maxLines) return;
                formatTag(lines, nested, safeIndent + "  ", maxLines, count);
                if (count[0] >= maxLines) return;
                lines.add(safeIndent + "}");
                count[0]++;
            } else {
                lines.add(prefix + value);
                count[0]++;
            }
        }
    }
}
