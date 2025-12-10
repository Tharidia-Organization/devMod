package com.frenkvs.devmod.ui.hub;

import com.frenkvs.devmod.testing.TestCase;
import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.UIConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Central panel showing selected test details:
 * - Name and description
 * - Step-by-step instructions
 * - Required tools
 * - Verdict buttons (PASS/FAIL/SKIP)
 */
public class TestDetailPanel implements HubPanel {

    private final int x, y, width, height;
    private final Font font;
    private final TestingHubState state;
    private final BiConsumer<TestCase, Verdict> onVerdictGiven;

    // Current test
    private TestCase currentTest = null;
    private boolean showCompletion = false;

    // Tool status cache
    private Set<ToolType> requiredTools = null;

    // Layout
    private static final int PADDING = 12;
    private static final int HEADER_HEIGHT = 28;
    private static final int LINE_HEIGHT = 12;
    private static final int BUTTON_HEIGHT = 24;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_GAP = 12;

    // Scroll for long instructions
    private int instructionScroll = 0;
    private int maxInstructionScroll = 0;

    public TestDetailPanel(int x, int y, int width, int height, Font font,
                           TestingHubState state,
                           BiConsumer<TestCase, Verdict> onVerdictGiven) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.font = font;
        this.state = state;
        this.onVerdictGiven = onVerdictGiven;
    }

    public void setTest(TestCase test) {
        this.currentTest = test;
        this.showCompletion = false;
        this.instructionScroll = 0;
        if (test != null) {
            this.requiredTools = TestingHubState.inferRequiredTools(test);
        } else {
            this.requiredTools = null;
        }
    }

    public void clearTest() {
        this.currentTest = null;
        this.showCompletion = false;
        this.requiredTools = null;
    }

    public void showCompletionMessage() {
        this.showCompletion = true;
        this.currentTest = null;
    }

    public void updateToolStatus(ToolType tool, boolean enabled) {
        // Trigger re-render with new tool state
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Background
        graphics.fill(x, y, x + width, y + height, UIConstants.Background.PANEL);

        if (showCompletion) {
            renderCompletionMessage(graphics);
        } else if (currentTest != null) {
            renderTestDetails(graphics, mouseX, mouseY);
        } else {
            renderEmptyState(graphics);
        }
    }

    private void renderEmptyState(GuiGraphics graphics) {
        int centerX = x + width / 2;
        int centerY = y + height / 2;

        String line1 = "Select a test from the list";
        String line2 = "to see details here";

        int w1 = font.width(line1);
        int w2 = font.width(line2);

        graphics.drawString(font, line1, centerX - w1 / 2, centerY - 10, UIConstants.Text.MUTED, false);
        graphics.drawString(font, line2, centerX - w2 / 2, centerY + 4, UIConstants.Text.MUTED, false);
    }

    private void renderCompletionMessage(GuiGraphics graphics) {
        int centerX = x + width / 2;
        int centerY = y + height / 2;

        String line1 = "All tests completed!";
        String line2 = "Save your report to share results.";

        int w1 = font.width(line1);
        int w2 = font.width(line2);

        graphics.drawString(font, line1, centerX - w1 / 2, centerY - 10, UIConstants.Status.SUCCESS, false);
        graphics.drawString(font, line2, centerX - w2 / 2, centerY + 6, UIConstants.Text.SECONDARY, false);
    }

    private void renderTestDetails(GuiGraphics graphics, int mouseX, int mouseY) {
        int contentX = x + PADDING;
        int contentY = y + PADDING;
        int contentWidth = width - PADDING * 2;

        // === HEADER: Nome test + Priority ===
        renderHeader(graphics, contentX, contentY, contentWidth);
        contentY += HEADER_HEIGHT + 8;

        // === STATUS + CATEGORY ===
        renderStatusRow(graphics, contentX, contentY, contentWidth);
        contentY += LINE_HEIGHT + 8;

        // Separator
        AxiomRenderer.drawSeparator(graphics, contentX, contentY, contentWidth);
        contentY += 8;

        // === DESCRIPTION ===
        graphics.drawString(font, "Description:", contentX, contentY, UIConstants.Text.TITLE, false);
        contentY += LINE_HEIGHT + 2;

        String desc = currentTest.getDescription();
        contentY = renderWrappedText(graphics, contentX + 4, contentY, contentWidth - 8, desc, UIConstants.Text.SECONDARY);
        contentY += 12;

        // === ISTRUZIONI ===
        graphics.drawString(font, "Instructions:", contentX, contentY, UIConstants.Text.TITLE, false);
        contentY += LINE_HEIGHT + 4;

        int instructionsHeight = height - contentY - y - BUTTON_HEIGHT - 60;
        contentY = renderInstructions(graphics, contentX, contentY, contentWidth, instructionsHeight);
        contentY += 12;

        // === REQUIRED TOOLS ===
        if (requiredTools != null && !requiredTools.isEmpty()) {
            contentY = renderRequiredTools(graphics, contentX, contentY, contentWidth, mouseX, mouseY);
            contentY += 8;
        }

        // === VERDICT BUTTONS ===
        renderVerdictButtons(graphics, mouseX, mouseY);
    }

    private void renderHeader(GuiGraphics graphics, int cx, int cy, int cw) {
        // Test name
        String name = currentTest.getName();
        if (font.width(name) > cw - 60) {
            name = name.substring(0, Math.min(name.length(), 40)) + "...";
        }
        graphics.drawString(font, name, cx, cy + 4, UIConstants.Text.PRIMARY, false);

        // Priority badge
        String priority = "[" + currentTest.getPriority().name() + "]";
        int priorityWidth = font.width(priority);
        graphics.drawString(font, priority, cx + cw - priorityWidth, cy + 4, currentTest.getPriority().getColor(), false);

        // Underline
        graphics.fill(cx, cy + HEADER_HEIGHT - 4, cx + cw, cy + HEADER_HEIGHT - 3, UIConstants.Border.SEPARATOR);
    }

    private void renderStatusRow(GuiGraphics graphics, int cx, int cy, int cw) {
        // Status dot
        int statusColor = currentTest.getStatus().getColor();
        graphics.fill(cx, cy + 2, cx + 8, cy + 10, statusColor);

        // Status text
        graphics.drawString(font, currentTest.getStatus().getDisplayName(), cx + 12, cy, statusColor, false);

        // Category
        String category = "Category: " + currentTest.getCategory();
        int catWidth = font.width(category);
        graphics.drawString(font, category, cx + cw - catWidth, cy, UIConstants.Text.MUTED, false);
    }

    private int renderWrappedText(GuiGraphics graphics, int cx, int cy, int maxWidth, String text, int color) {
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        int lineY = cy;

        for (String word : words) {
            String testLine = line.length() > 0 ? line + " " + word : word;
            if (font.width(testLine) > maxWidth) {
                graphics.drawString(font, line.toString(), cx, lineY, color, false);
                lineY += LINE_HEIGHT;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(testLine);
            }
        }

        if (line.length() > 0) {
            graphics.drawString(font, line.toString(), cx, lineY, color, false);
            lineY += LINE_HEIGHT;
        }

        return lineY;
    }

    private int renderInstructions(GuiGraphics graphics, int cx, int cy, int cw, int maxHeight) {
        String[] steps = currentTest.getInstructions().split("\n");

        // Scissor for scroll
        graphics.enableScissor(cx, cy, cx + cw, cy + maxHeight);

        int stepY = cy - instructionScroll;
        int totalHeight = 0;

        for (int i = 0; i < steps.length; i++) {
            String step = steps[i].trim();
            if (step.isEmpty()) continue;

            // Checkbox
            boolean isChecked = false;
            if (currentTest.hasProgressChecker()) {
                float progress = currentTest.getCachedProgress();
                float threshold = (float)(i + 1) / steps.length;
                isChecked = progress >= threshold;
            }

            int checkColor = isChecked ? UIConstants.Status.SUCCESS : UIConstants.Text.MUTED;
            String checkbox = isChecked ? "[X]" : "[ ]";

            if (stepY >= cy - 20 && stepY < cy + maxHeight + 20) {
                graphics.drawString(font, checkbox, cx, stepY, checkColor, false);

                // Step text (wrapped if necessary)
                String stepText = step;
                int textX = cx + font.width(checkbox) + 4;
                int textWidth = cw - font.width(checkbox) - 8;

                if (font.width(stepText) > textWidth) {
                    // Truncate with ellipsis - keep at least 10 chars for readability
                    String ellipsis = "...";
                    int minChars = Math.min(10, stepText.length());
                    while (font.width(stepText + ellipsis) > textWidth && stepText.length() > minChars) {
                        stepText = stepText.substring(0, stepText.length() - 1);
                    }
                    stepText = stepText + ellipsis;
                }

                graphics.drawString(font, stepText, textX, stepY, UIConstants.Text.SECONDARY, false);
            }

            stepY += LINE_HEIGHT + 4;
            totalHeight += LINE_HEIGHT + 4;
        }

        maxInstructionScroll = Math.max(0, totalHeight - maxHeight);

        graphics.disableScissor();

        return cy + Math.min(totalHeight, maxHeight);
    }

    private int renderRequiredTools(GuiGraphics graphics, int cx, int cy, int cw, int mouseX, int mouseY) {
        graphics.drawString(font, "Required Tools:", cx, cy, UIConstants.Text.TITLE, false);
        cy += LINE_HEIGHT + 4;

        int toolX = cx + 4;
        for (ToolType tool : requiredTools) {
            boolean enabled = tool.isEnabled();
            int badgeColor = enabled ? UIConstants.Status.SUCCESS : UIConstants.Status.WARNING;

            String badge = "[" + tool.getHotkey() + "] " + tool.getLabel();
            String status = enabled ? " OK" : " NEEDED";

            graphics.drawString(font, badge, toolX, cy, UIConstants.Text.SECONDARY, false);
            int badgeWidth = font.width(badge);
            graphics.drawString(font, status, toolX + badgeWidth, cy, badgeColor, false);

            toolX += font.width(badge + status) + 16;

            // New line if necessary
            if (toolX > cx + cw - 100) {
                toolX = cx + 4;
                cy += LINE_HEIGHT + 2;
            }
        }

        return cy + LINE_HEIGHT;
    }

    private void renderVerdictButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        int buttonsY = y + height - PADDING - BUTTON_HEIGHT;
        int totalWidth = BUTTON_WIDTH * 3 + BUTTON_GAP * 2;
        int buttonsX = x + (width - totalWidth) / 2;

        for (Verdict verdict : Verdict.values()) {
            int bx = buttonsX + verdict.ordinal() * (BUTTON_WIDTH + BUTTON_GAP);
            boolean hovered = AxiomRenderer.isMouseOver(mouseX, mouseY, bx, buttonsY, BUTTON_WIDTH, BUTTON_HEIGHT);

            // Background
            int bgColor = hovered ? UIConstants.Background.HOVER : UIConstants.Background.INPUT;
            graphics.fill(bx, buttonsY, bx + BUTTON_WIDTH, buttonsY + BUTTON_HEIGHT, bgColor);

            // Border with verdict color
            int borderColor = hovered ? verdict.getColor() : UIConstants.Border.DEFAULT;
            AxiomRenderer.drawBorder(graphics, bx, buttonsY, BUTTON_WIDTH, BUTTON_HEIGHT, borderColor);

            // Label
            String label = verdict.getLabel();
            int labelWidth = font.width(label);
            int labelColor = hovered ? verdict.getColor() : UIConstants.Text.PRIMARY;
            graphics.drawString(font, label, bx + (BUTTON_WIDTH - labelWidth) / 2, buttonsY + 8, labelColor, false);

            // Hotkey hint below
            String hotkey = "[" + verdict.getHotkey() + "]";
            int hotkeyWidth = font.width(hotkey);
            graphics.drawString(font, hotkey, bx + (BUTTON_WIDTH - hotkeyWidth) / 2, buttonsY + BUTTON_HEIGHT + 2, UIConstants.Text.MUTED, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || currentTest == null) return false;

        int mx = (int) mouseX;
        int my = (int) mouseY;

        // Check verdict buttons
        int buttonsY = y + height - PADDING - BUTTON_HEIGHT;
        int totalWidth = BUTTON_WIDTH * 3 + BUTTON_GAP * 2;
        int buttonsX = x + (width - totalWidth) / 2;

        for (Verdict verdict : Verdict.values()) {
            int bx = buttonsX + verdict.ordinal() * (BUTTON_WIDTH + BUTTON_GAP);
            if (AxiomRenderer.isMouseOver(mx, my, bx, buttonsY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                onVerdictGiven.accept(currentTest, verdict);
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (currentTest != null) {
            instructionScroll = Math.max(0, Math.min(maxInstructionScroll, instructionScroll - (int)(scrollY * 15)));
            return true;
        }
        return false;
    }

    @Override
    public boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
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
