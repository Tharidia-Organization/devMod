package com.devmod.client.ui.hub;

import java.util.EnumMap;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.UIConstants;
import com.devmod.testing.TestCase;

public class TestDetailPanel implements HubPanel {

    private final int x, y, width, height;
    private final Font font;
    private final TestingHubState state;
    private final BiConsumer<TestCase, Verdict> onVerdictGiven;
    private final EnumMap<Verdict, EditorButton> verdictButtons;

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
        this.verdictButtons = buildVerdictButtons();
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
        graphics.fill(x, y, x + width, y + height, UIConstants.Background.PANEL());

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

        int w1 = Objects.requireNonNull(font, "font").width(line1);
        int w2 = Objects.requireNonNull(font, "font").width(line2);

        graphics.drawString(Objects.requireNonNull(font, "font"), Objects.requireNonNull(line1, "line1"), centerX - w1 / 2, centerY - 10, UIConstants.Text.MUTED(), false);
        graphics.drawString(Objects.requireNonNull(font, "font"), Objects.requireNonNull(line2, "line2"), centerX - w2 / 2, centerY + 4, UIConstants.Text.MUTED(), false);
    }

    private void renderCompletionMessage(GuiGraphics graphics) {
        int centerX = x + width / 2;
        int centerY = y + height / 2;

        String line1 = "All tests completed!";
        String line2 = "Save your report to share results.";

        int w1 = Objects.requireNonNull(font, "font").width(line1);
        int w2 = Objects.requireNonNull(font, "font").width(line2);

        graphics.drawString(Objects.requireNonNull(font, "font"), Objects.requireNonNull(line1, "line1"), centerX - w1 / 2, centerY - 10, UIConstants.Status.SUCCESS(), false);
        graphics.drawString(Objects.requireNonNull(font, "font"), Objects.requireNonNull(line2, "line2"), centerX - w2 / 2, centerY + 6, UIConstants.Text.SECONDARY(), false);
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
        contentY = HubSectionHeader.draw(graphics, Objects.requireNonNull(font, "font"),
            "Description:", contentX, contentY, LINE_HEIGHT, 0);
        contentY += 2;

        String desc = currentTest.getDescription();
        contentY = renderWrappedText(graphics, contentX + 4, contentY, contentWidth - 8, desc, UIConstants.Text.SECONDARY());
        contentY += 12;

        // === ISTRUZIONI ===
        contentY = HubSectionHeader.draw(graphics, Objects.requireNonNull(font, "font"),
            "Instructions:", contentX, contentY, LINE_HEIGHT, 0);
        contentY += 4;

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
        if (Objects.requireNonNull(font, "font").width(Objects.requireNonNull(name, "name")) > cw - 60) {
            name = name.substring(0, Math.min(name.length(), 40)) + "...";
        }
        graphics.drawString(Objects.requireNonNull(font, "font"), name, cx, cy + 4, UIConstants.Text.PRIMARY(), false);

        // Priority badge
        String priority = "[" + currentTest.getPriority().name() + "]";
        int priorityWidth = Objects.requireNonNull(font, "font").width(priority);
        graphics.drawString(Objects.requireNonNull(font, "font"), Objects.requireNonNull(priority, "priority"), cx + cw - priorityWidth, cy + 4, currentTest.getPriority().getColor(), false);

        // Underline
        graphics.fill(cx, cy + HEADER_HEIGHT - 4, cx + cw, cy + HEADER_HEIGHT - 3, UIConstants.Border.SEPARATOR());
    }

    private void renderStatusRow(GuiGraphics graphics, int cx, int cy, int cw) {
        // Status dot
        int statusColor = currentTest.getStatus().getColor();
        graphics.fill(cx, cy + 2, cx + 8, cy + 10, statusColor);

        // Status text
        graphics.drawString(Objects.requireNonNull(font, "font"), currentTest.getStatus().getDisplayName(), cx + 12, cy, statusColor, false);

        // Category
        String category = "Category: " + currentTest.getCategory();
        int catWidth = Objects.requireNonNull(font, "font").width(category);
        graphics.drawString(Objects.requireNonNull(font, "font"), Objects.requireNonNull(category, "category"), cx + cw - catWidth, cy, UIConstants.Text.MUTED(), false);
    }

    private int renderWrappedText(GuiGraphics graphics, int cx, int cy, int maxWidth, String text, int color) {
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        int lineY = cy;

        for (String word : words) {
            String testLine = line.length() > 0 ? line + " " + word : word;
            if (Objects.requireNonNull(font, "font").width(Objects.requireNonNull(testLine, "testLine")) > maxWidth) {
                graphics.drawString(Objects.requireNonNull(font, "font"), line.toString(), cx, lineY, color, false);
                lineY += LINE_HEIGHT;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(testLine);
            }
        }

        if (line.length() > 0) {
            graphics.drawString(Objects.requireNonNull(font, "font"), line.toString(), cx, lineY, color, false);
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

            int checkColor = isChecked ? UIConstants.Status.SUCCESS() : UIConstants.Text.MUTED();
            String checkbox = isChecked ? "[X]" : "[ ]";

            if (stepY >= cy - 20 && stepY < cy + maxHeight + 20) {
                graphics.drawString(Objects.requireNonNull(font, "font"), checkbox, cx, stepY, checkColor, false);

                // Step text (wrapped if necessary)
                String stepText = step;
                int textX = cx + Objects.requireNonNull(font, "font").width(checkbox) + 4;
                int textWidth = cw - Objects.requireNonNull(font, "font").width(checkbox) - 8;

                if (Objects.requireNonNull(font, "font").width(stepText) > textWidth) {
                    // Truncate with ellipsis - keep at least 10 chars for readability
                    String ellipsis = "...";
                    int minChars = Math.min(10, stepText.length());
                    while (Objects.requireNonNull(font, "font").width(stepText + ellipsis) > textWidth && stepText.length() > minChars) {
                        stepText = stepText.substring(0, stepText.length() - 1);
                    }
                    stepText = stepText + ellipsis;
                }

                graphics.drawString(Objects.requireNonNull(font, "font"), stepText, textX, stepY, UIConstants.Text.SECONDARY(), false);
            }

            stepY += LINE_HEIGHT + 4;
            totalHeight += LINE_HEIGHT + 4;
        }

        maxInstructionScroll = Math.max(0, totalHeight - maxHeight);

        graphics.disableScissor();

        return cy + Math.min(totalHeight, maxHeight);
    }

    private int renderRequiredTools(GuiGraphics graphics, int cx, int cy, int cw, int mouseX, int mouseY) {
        cy = HubSectionHeader.draw(graphics, Objects.requireNonNull(font, "font"),
            "Required Tools:", cx, cy, LINE_HEIGHT, 0);
        cy += 4;

        int toolX = cx + 4;
        for (ToolType tool : requiredTools) {
            boolean enabled = tool.isEnabled();
            int badgeColor = enabled ? UIConstants.Status.SUCCESS() : UIConstants.Status.WARNING();

            String badge = "[" + tool.getHotkey() + "] " + tool.getLabel();
            String status = enabled ? " OK" : " NEEDED";

            graphics.drawString(Objects.requireNonNull(font, "font"), badge, toolX, cy, UIConstants.Text.SECONDARY(), false);
            int badgeWidth = Objects.requireNonNull(font, "font").width(badge);
            graphics.drawString(Objects.requireNonNull(font, "font"), status, toolX + badgeWidth, cy, badgeColor, false);

            toolX += Objects.requireNonNull(font, "font").width(badge + status) + 16;

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
            EditorButton button = verdictButtons.get(verdict);
            if (button == null) {
                continue;
            }
            button.setEnabled(currentTest != null);
            button.render(graphics, bx, buttonsY, BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || currentTest == null) return false;

        for (EditorButton buttonItem : verdictButtons.values()) {
            if (buttonItem.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0 || currentTest == null) return false;

        for (EditorButton buttonItem : verdictButtons.values()) {
            if (buttonItem.mouseReleased(mouseX, mouseY, button)) {
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

    public TestingHubState getState() {
        return state;
    }

    private EnumMap<Verdict, EditorButton> buildVerdictButtons() {
        EnumMap<Verdict, EditorButton> buttons = new EnumMap<>(Verdict.class);
        for (Verdict verdict : Verdict.values()) {
            EditorButton.Style style = switch (verdict) {
                case PASS -> EditorButton.Style.SUCCESS;
                case FAIL -> EditorButton.Style.DANGER;
                case SKIP -> EditorButton.Style.GHOST;
            };
            EditorButton button = EditorButton.builder("verdict-" + verdict.name().toLowerCase(), verdict.getLabel())
                .style(style)
                .hotkeyHint("[" + verdict.getHotkey() + "]")
                .onClick(() -> handleVerdict(verdict))
                .build();
            buttons.put(verdict, button);
        }
        return buttons;
    }

    private void handleVerdict(Verdict verdict) {
        if (currentTest == null) {
            return;
        }
        onVerdictGiven.accept(currentTest, verdict);
    }
}
