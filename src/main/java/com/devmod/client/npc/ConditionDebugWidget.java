package com.devmod.client.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.npc.dialog.condition.ConditionDebugResult;
import com.devmod.npc.dialog.condition.DialogCondition;

/**
 * Widget for displaying condition debug results in a tree-like structure.
 * Shows pass/fail status with detailed reasons for each condition.
 *
 * <p>The widget displays:
 * <ul>
 *   <li>Condition type with status icon (checkmark/X)</li>
 *   <li>Debug reason explaining why the condition passed/failed</li>
 *   <li>Nested children for composite conditions (And, Or, Not)</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class ConditionDebugWidget extends AbstractWidget {

    // Colors
    private static final int COLOR_BG = DesignTokens.Panel.BG;
    private static final int COLOR_BORDER = DesignTokens.Stroke.DEFAULT;
    private static final int COLOR_PASS = 0xFF4CAF50;  // Green
    private static final int COLOR_FAIL = 0xFFF44336;  // Red
    private static final int COLOR_TEXT_DIM = DesignTokens.Text.SECONDARY;
    private static final int COLOR_HEADER = DesignTokens.Text.MUTED;

    // Layout
    private static final int LINE_HEIGHT = 14;
    private static final int INDENT_SIZE = 16;
    private static final int PADDING = 8;

    @Nullable
    private ConditionDebugResult debugResult;
    @Nullable
    private DialogCondition condition;

    private final List<DebugLine> lines = new ArrayList<>();
    private int scrollOffset = 0;
    private int contentHeight = 0;

    public ConditionDebugWidget(int x, int y, int width, int height) {
        super(x, y, width, height, Component.literal("Condition Debug"));
    }

    /**
     * Sets the debug result to display.
     */
    public void setDebugResult(@Nullable ConditionDebugResult result) {
        this.debugResult = result;
        this.condition = result != null ? result.condition() : null;
        rebuildLines();
    }

    /**
     * Sets a condition without debug result (structure only mode).
     */
    public void setCondition(@Nullable DialogCondition condition) {
        this.condition = condition;
        this.debugResult = null;
        rebuildLines();
    }

    /**
     * Clears the displayed content.
     */
    public void clear() {
        this.debugResult = null;
        this.condition = null;
        this.lines.clear();
        this.contentHeight = 0;
    }

    private void rebuildLines() {
        lines.clear();
        scrollOffset = 0;

        if (debugResult != null) {
            addDebugResultLines(debugResult, 0);
        } else if (condition != null) {
            addConditionStructureLines(condition, 0);
        }

        contentHeight = lines.size() * LINE_HEIGHT + PADDING * 2;
    }

    private void addDebugResultLines(ConditionDebugResult result, int depth) {
        String icon = result.passed() ? "\u2705" : "\u274C";  // Checkmark or X emoji
        int color = result.passed() ? COLOR_PASS : COLOR_FAIL;

        String typeText = result.getConditionType().toUpperCase(Locale.ROOT);
        lines.add(new DebugLine(depth, icon + " " + typeText, color, true));
        lines.add(new DebugLine(depth + 1, result.reason(), COLOR_TEXT_DIM, false));

        for (ConditionDebugResult child : result.children()) {
            addDebugResultLines(child, depth + 1);
        }
    }

    private void addConditionStructureLines(DialogCondition condition, int depth) {
        String typeText = condition.getType().toUpperCase(Locale.ROOT);
        lines.add(new DebugLine(depth, "\u25B6 " + typeText, COLOR_HEADER, true));  // Triangle bullet

        // Add parameter info
        String params = getConditionParams(condition);
        if (!params.isEmpty()) {
            lines.add(new DebugLine(depth + 1, params, COLOR_TEXT_DIM, false));
        }

        // Recurse for nested conditions
        switch (condition) {
            case DialogCondition.And and -> {
                for (DialogCondition child : and.conditions()) {
                    addConditionStructureLines(child, depth + 1);
                }
            }
            case DialogCondition.Or or -> {
                for (DialogCondition child : or.conditions()) {
                    addConditionStructureLines(child, depth + 1);
                }
            }
            case DialogCondition.Not not -> addConditionStructureLines(not.condition(), depth + 1);
            default -> {}
        }
    }

    private String getConditionParams(DialogCondition condition) {
        return switch (condition) {
            case DialogCondition.Always a -> "";
            case DialogCondition.FirstVisit fv -> {
                if (fv.npcId().getMostSignificantBits() == 0 && fv.npcId().getLeastSignificantBits() == 0) {
                    yield "NPC: current";
                }
                yield "NPC: " + fv.npcId().toString().substring(0, 8) + "...";
            }
            case DialogCondition.HasPermission hp -> "OP Level: " + hp.opLevel();
            case DialogCondition.HasSelectedOption hso -> "Option: " + hso.optionId();
            case DialogCondition.NpcHasEmotion nhe -> "Emotion: " + nhe.emotion().getSerializedName();
            case DialogCondition.QuestCompleted qc -> "Quest: " + qc.questId();
            case DialogCondition.QuestCount qn -> "Count " + qn.comparison().name() + " " + qn.minQuests();
            case DialogCondition.TimeOfDay tod -> "Time: " + tod.minTime() + " - " + tod.maxTime();
            case DialogCondition.Custom cu -> "Handler: " + cu.handlerId();
            case DialogCondition.And a -> a.conditions().size() + " conditions";
            case DialogCondition.Or o -> o.conditions().size() + " conditions";
            case DialogCondition.Not n -> "1 condition";
        };
    }

    @Override
    protected void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;

        // Background
        graphics.fill(getX(), getY(), getX() + width, getY() + height, COLOR_BG);
        graphics.renderOutline(getX(), getY(), width, height, COLOR_BORDER);

        // Header
        String header = debugResult != null ? "Debug Results" : (condition != null ? "Condition Structure" : "No Condition");
        graphics.drawString(font, header, getX() + PADDING, getY() + PADDING, COLOR_HEADER);

        // Content area with clipping
        int contentX = getX() + PADDING;
        int contentY = getY() + PADDING + LINE_HEIGHT + 4;
        int contentWidth = width - PADDING * 2;
        int contentHeight = height - PADDING * 2 - LINE_HEIGHT - 4;

        // Enable scissor for clipping
        graphics.enableScissor(contentX, contentY, contentX + contentWidth, contentY + contentHeight);

        // Render lines
        int y = contentY - scrollOffset;
        for (DebugLine line : lines) {
            if (y + LINE_HEIGHT > contentY && y < contentY + contentHeight) {
                int indent = line.depth * INDENT_SIZE;
                String text = line.text;
                int textX = contentX + indent;

                if (line.isHeader) {
                    graphics.drawString(font, text, textX, y, line.color);
                } else {
                    // Truncate long text
                    int maxWidth = contentWidth - indent - PADDING;
                    if (font.width(text) > maxWidth) {
                        text = font.plainSubstrByWidth(text, maxWidth - font.width("...")) + "...";
                    }
                    graphics.drawString(font, text, textX, y, line.color);
                }
            }
            y += LINE_HEIGHT;
        }

        graphics.disableScissor();

        // Scrollbar if needed
        if (this.contentHeight > contentHeight) {
            renderScrollbar(graphics, contentX + contentWidth - 4, contentY, 4, contentHeight);
        }
    }

    private void renderScrollbar(GuiGraphics graphics, int x, int y, int width, int height) {
        int contentHeight = this.contentHeight;
        int viewHeight = this.height - PADDING * 2 - LINE_HEIGHT - 4;

        if (contentHeight <= viewHeight) return;

        float scrollRatio = (float) scrollOffset / (contentHeight - viewHeight);
        int thumbHeight = Math.max(20, viewHeight * viewHeight / contentHeight);
        int thumbY = y + (int) ((height - thumbHeight) * scrollRatio);

        // Track
        graphics.fill(x, y, x + width, y + height, 0x40FFFFFF);
        // Thumb
        graphics.fill(x, thumbY, x + width, thumbY + thumbHeight, 0xAAFFFFFF);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isMouseOver(mouseX, mouseY)) {
            int viewHeight = height - PADDING * 2 - LINE_HEIGHT - 4;
            int maxScroll = Math.max(0, contentHeight - viewHeight);
            scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - scrollY * LINE_HEIGHT * 3));
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    /**
     * Represents a single line in the debug output.
     */
    private record DebugLine(int depth, String text, int color, boolean isHeader) {}
}
