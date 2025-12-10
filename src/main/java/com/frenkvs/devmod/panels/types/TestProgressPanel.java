package com.frenkvs.devmod.panels.types;

import com.frenkvs.devmod.panels.core.FloatingPanel;
import com.frenkvs.devmod.panels.core.PanelType;
import com.frenkvs.devmod.testing.TestCase;
import com.frenkvs.devmod.testing.TestingSession;
import com.frenkvs.devmod.ui.UIConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Panel that shows the current testing session progress.
 *
 * Displays:
 * - Current test (name and category)
 * - Session progress bar
 * - Passed/failed/pending count
 * - Next test step
 */
public class TestProgressPanel extends FloatingPanel {

    // Cache dati sessione
    private String currentTestName = "";
    private String currentCategory = "";
    private float sessionProgress = 0;
    private int passedCount = 0;
    private int failedCount = 0;
    private int pendingCount = 0;
    private int totalCount = 0;
    private long lastUpdateTick = 0;

    private static final int UPDATE_INTERVAL_TICKS = 20; // Update every second

    /**
     * Creates a test progress panel at a fixed position.
     */
    public TestProgressPanel(Vec3 position) {
        super(PanelType.TEST_PROGRESS, position);
        updateSessionData();
    }

    @Override
    public void tick() {
        super.tick();

        long currentTick = System.currentTimeMillis() / 50;
        if (currentTick - lastUpdateTick >= UPDATE_INTERVAL_TICKS) {
            updateSessionData();
            lastUpdateTick = currentTick;
        }
    }

    /**
     * Updates data from the testing session.
     */
    private void updateSessionData() {
        TestingSession session = TestingSession.INSTANCE;

        if (!session.isSessionActive()) {
            currentTestName = "No active session";
            currentCategory = "";
            sessionProgress = 0;
            passedCount = 0;
            failedCount = 0;
            pendingCount = 0;
            totalCount = 0;
            return;
        }

        // Statistiche
        passedCount = session.getPassedTests();
        failedCount = session.getFailedTests();
        totalCount = session.getTotalTests();
        pendingCount = totalCount - passedCount - failedCount;

        // Progresso
        sessionProgress = totalCount > 0 ? (float)(passedCount + failedCount) / totalCount : 0;

        // Test corrente
        TestCase current = session.getNextPendingTest();
        if (current != null) {
            currentTestName = current.getName();
            currentCategory = current.getCategory();
        } else {
            currentTestName = "Select a test";
            currentCategory = "";
        }
    }

    @Override
    public void renderContent(GuiGraphics graphics, int contentWidth, int contentHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) return;

        int y = 0;
        int lineHeight = 10;

        // Test corrente (keep at least 6 chars for readability)
        String testText = currentTestName;
        if (mc.font.width(testText) > contentWidth) {
            String ellipsis = "...";
            int minChars = Math.min(6, testText.length());
            while (mc.font.width(testText + ellipsis) > contentWidth && testText.length() > minChars) {
                testText = testText.substring(0, testText.length() - 1);
            }
            testText += ellipsis;
        }
        graphics.drawString(mc.font, testText, 0, y, UIConstants.Text.PRIMARY, false);
        y += lineHeight;

        // Categoria
        if (!currentCategory.isEmpty()) {
            graphics.drawString(mc.font, currentCategory, 0, y, UIConstants.Text.MUTED, false);
            y += lineHeight;
        }
        y += 4;

        // Barra progresso
        int barHeight = 6;
        int barWidth = contentWidth - 4;

        // Background
        graphics.fill(0, y, barWidth, y + barHeight, UIConstants.Background.INPUT);

        // Progress fill
        int progressWidth = (int)(barWidth * sessionProgress);
        int progressColor = getProgressColor();
        graphics.fill(0, y, progressWidth, y + barHeight, progressColor);

        // Border
        drawBorder(graphics, 0, y, barWidth, barHeight, UIConstants.Border.MUTED);
        y += barHeight + 4;

        // Counters
        String statsText = String.format("P:%d F:%d /%d", passedCount, failedCount, totalCount);
        graphics.drawString(mc.font, statsText, 0, y, UIConstants.Text.SECONDARY, false);

        // Percentage on the right
        String percentText = String.format("%.0f%%", sessionProgress * 100);
        int percentWidth = mc.font.width(percentText);
        graphics.drawString(mc.font, percentText, contentWidth - percentWidth - 4, y, progressColor, false);
    }

    /**
     * Draws a simple border.
     */
    private void drawBorder(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);           // Top
        graphics.fill(x, y + h - 1, x + w, y + h, color);   // Bottom
        graphics.fill(x, y, x + 1, y + h, color);           // Left
        graphics.fill(x + w - 1, y, x + w, y + h, color);   // Right
    }

    /**
     * Gets the progress bar color.
     */
    private int getProgressColor() {
        if (failedCount > 0) {
            return UIConstants.Status.WARNING; // Orange if there are failures
        }
        if (sessionProgress >= 1.0f) {
            return UIConstants.Status.SUCCESS; // Green if completed
        }
        return UIConstants.Status.INFO; // Blue during progress
    }

    @Override
    public void renderContent3D(PoseStack poseStack, MultiBufferSource bufferSource, Font font,
                                 int contentWidth, int contentHeight, float alpha) {
        int y = 0;
        int lineHeight = 10;

        // Test corrente (keep at least 6 chars for readability)
        String testText = currentTestName;
        if (font.width(testText) > contentWidth) {
            String ellipsis = "...";
            int minChars = Math.min(6, testText.length());
            while (font.width(testText + ellipsis) > contentWidth && testText.length() > minChars) {
                testText = testText.substring(0, testText.length() - 1);
            }
            testText += ellipsis;
        }
        renderText3D(poseStack, bufferSource, font, testText, 0, y, applyAlpha(UIConstants.Text.PRIMARY, alpha));
        y += lineHeight;

        // Categoria
        if (!currentCategory.isEmpty()) {
            renderText3D(poseStack, bufferSource, font, currentCategory, 0, y, applyAlpha(UIConstants.Text.MUTED, alpha));
            y += lineHeight;
        }
        y += 4;

        // Progresso come testo
        String progressText = String.format("Progress: %.0f%%", sessionProgress * 100);
        int progressColor = getProgressColor();
        renderText3D(poseStack, bufferSource, font, progressText, 0, y, applyAlpha(progressColor, alpha));
        y += lineHeight + 2;

        // Contatori
        String statsText = String.format("Passed: %d | Failed: %d | Total: %d", passedCount, failedCount, totalCount);
        renderText3D(poseStack, bufferSource, font, statsText, 0, y, applyAlpha(UIConstants.Text.SECONDARY, alpha));
    }

    @Override
    public String getTitle() {
        return String.format("Test: %.0f%%", sessionProgress * 100);
    }

    // === Getters ===

    public String getCurrentTestName() {
        return currentTestName;
    }

    public String getCurrentCategory() {
        return currentCategory;
    }

    public float getSessionProgress() {
        return sessionProgress;
    }

    public int getPassedCount() {
        return passedCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public int getPendingCount() {
        return pendingCount;
    }

    public int getTotalCount() {
        return totalCount;
    }

    /**
     * Checks if the session is complete.
     */
    public boolean isSessionComplete() {
        return totalCount > 0 && (passedCount + failedCount) >= totalCount;
    }

    @Override
    public String toString() {
        return String.format("TestProgressPanel[progress=%.0f%%, passed=%d, failed=%d]",
            sessionProgress * 100, passedCount, failedCount);
    }
}
