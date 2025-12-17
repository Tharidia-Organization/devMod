package com.frenkvs.devmod.ui.hub;

import com.frenkvs.devmod.testing.TestingSession;
import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.UIConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.Nonnull;

/**
 * Footer with progress bar and quick actions.
 * Shows: progress bar, stats, save report button, minimize button.
 */
public class ProgressFooter implements HubPanel {

    private final int x, y, width, height;
    @Nonnull
    private final Font font;
    private final Runnable onSaveReport;
    private final Runnable onMinimize;

    private static final int PADDING = 10;
    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 24;
    private static final int PROGRESS_BAR_HEIGHT = 8;

    public ProgressFooter(int x, int y, int width, int height, @Nonnull Font font,
                          Runnable onSaveReport, Runnable onMinimize) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.font = font;
        this.onSaveReport = onSaveReport;
        this.onMinimize = onMinimize;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Background
        graphics.fill(x, y, x + width, y + height, UIConstants.Background.HEADER());

        // Top border
        graphics.fill(x, y, x + width, y + 1, UIConstants.Border.DEFAULT());

        int contentY = y + PADDING;

        // Progress stats
        TestingSession session = TestingSession.INSTANCE;
        int total = session.getTotalTests();
        int passed = session.getPassedTests();
        int failed = session.getFailedTests();
        int pending = total - passed - failed;

        String statsText = String.format("Progress: %d/%d | Passed: %d | Failed: %d | Pending: %d",
            passed + failed, total, passed, failed, pending);
        graphics.drawString(font, statsText, x + PADDING, contentY, UIConstants.Text.SECONDARY(), false);
        contentY += 14;

        // Progress bar
        int barX = x + PADDING;
        int barWidth = width - PADDING * 2 - BUTTON_WIDTH * 2 - 20;
        renderProgressBar(graphics, barX, contentY, barWidth, passed, failed, pending, total);

        // Buttons on the right
        int buttonY = contentY - 6;
        int saveX = x + width - PADDING - BUTTON_WIDTH * 2 - 8;
        boolean saveHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, saveX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT);
        renderButton(graphics, saveX, buttonY, "Save Report", saveHovered);

        int minX = x + width - PADDING - BUTTON_WIDTH;
        boolean minHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, minX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT);
        renderButton(graphics, minX, buttonY, "Minimize", minHovered);
    }

    private void renderProgressBar(GuiGraphics graphics, int bx, int by, int bw,
                                   int passed, int failed, int pending, int total) {
        if (total == 0) {
            // Empty bar
            graphics.fill(bx, by, bx + bw, by + PROGRESS_BAR_HEIGHT, UIConstants.Background.INPUT());
            return;
        }

        // Background
        graphics.fill(bx, by, bx + bw, by + PROGRESS_BAR_HEIGHT, UIConstants.Background.INPUT());

        // Passed segment (green)
        int passedWidth = (int)((float)passed / total * bw);
        if (passedWidth > 0) {
            graphics.fill(bx, by, bx + passedWidth, by + PROGRESS_BAR_HEIGHT, UIConstants.Status.SUCCESS());
        }

        // Failed segment (red)
        int failedWidth = (int)((float)failed / total * bw);
        if (failedWidth > 0) {
            int failedX = bx + passedWidth;
            graphics.fill(failedX, by, failedX + failedWidth, by + PROGRESS_BAR_HEIGHT, UIConstants.Status.ERROR());
        }

        // Pending is the remaining space (stays as background)

        // Border
        AxiomRenderer.drawBorder(graphics, bx, by, bw, PROGRESS_BAR_HEIGHT, UIConstants.Border.DEFAULT());
    }

    private void renderButton(GuiGraphics graphics, int bx, int by, @Nonnull String label, boolean hovered) {
        int bgColor = hovered ? UIConstants.Background.HOVER() : UIConstants.Background.INPUT();
        graphics.fill(bx, by, bx + BUTTON_WIDTH, by + BUTTON_HEIGHT, bgColor);
        AxiomRenderer.drawBorder(graphics, bx, by, BUTTON_WIDTH, BUTTON_HEIGHT, UIConstants.Border.DEFAULT());

        int labelWidth = font.width(label);
        int labelX = bx + (BUTTON_WIDTH - labelWidth) / 2;
        int labelY = by + (BUTTON_HEIGHT - 9) / 2;
        graphics.drawString(font, label, labelX, labelY, UIConstants.Text.PRIMARY(), false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        int mx = (int) mouseX;
        int my = (int) mouseY;

        int buttonY = y + PADDING + 14 - 6;

        // Save Report
        int saveX = x + width - PADDING - BUTTON_WIDTH * 2 - 8;
        if (AxiomRenderer.isMouseOver(mx, my, saveX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            onSaveReport.run();
            return true;
        }

        // Minimize
        int minX = x + width - PADDING - BUTTON_WIDTH;
        if (AxiomRenderer.isMouseOver(mx, my, minX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            onMinimize.run();
            return true;
        }

        return false;
    }

    @Override
    public boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public void refresh() {
        // Force redraw on next render
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
