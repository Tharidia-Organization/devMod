package com.devmod.client.testing;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.devmod.DevMod;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.testing.TestCase;

@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)

public class ActiveTestHudOverlay {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "active_test_hud");

    // === Colors (matching QATestingScreen style) ===
    private static final int PANEL_BG = TestingUiTheme.Hud.PANEL_BG;
    private static final int PANEL_BORDER = TestingUiTheme.Hud.PANEL_BORDER;
    private static final int PANEL_HEADER_BG = TestingUiTheme.Hud.PANEL_HEADER_BG;

    private static final int TEXT_TITLE = TestingUiTheme.Hud.TEXT_TITLE;
    private static final int TEXT_PRIMARY = TestingUiTheme.Hud.TEXT_PRIMARY;
    private static final int TEXT_SECONDARY = TestingUiTheme.Hud.TEXT_SECONDARY;
    private static final int TEXT_MUTED = TestingUiTheme.Hud.TEXT_MUTED;
    private static final int TEXT_SUCCESS = TestingUiTheme.Hud.TEXT_SUCCESS;
    private static final int TEXT_WARNING = TestingUiTheme.Hud.TEXT_WARNING;

    private static final int PROGRESS_BG = TestingUiTheme.Hud.PROGRESS_BG;
    private static final int PROGRESS_FILL = TestingUiTheme.Hud.PROGRESS_FILL;
    private static final int PROGRESS_COMPLETE = TestingUiTheme.Hud.PROGRESS_COMPLETE;

    // === Layout ===
    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_PADDING = 8;
    private static final int LINE_HEIGHT = 11;
    private static final int HEADER_HEIGHT = 22;

    // === State (disabled by default - enable via radial menu) ===
    private static boolean enabled = false;
    private static boolean minimized = false;
    @Nullable
    private static TestCase activeTest = null;
    private static long lastUpdateTime = 0;
    private static float animationProgress = 0f;

    // Animation for new test assignment
    private static long testStartTime = 0;
    private static boolean showNewTestAnimation = false;

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        @Nonnull ResourceLocation overlay = Objects.requireNonNull(
            VanillaGuiLayers.HOTBAR,
            "hotbar layer"
        );
        @Nonnull ResourceLocation layerId = Objects.requireNonNull(LAYER_ID, "layer id");
        event.registerAbove(
            overlay,
            layerId,
            ActiveTestHudOverlay::render
        );
    }

    private static void render(@Nonnull GuiGraphics graphics, @Nonnull DeltaTracker deltaTracker) {
        if (!enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (mc.screen != null) return; // Don't show when any screen is open

        // Update UIScaleManager for responsive scaling
        UIScaleManager.update();

        final @Nonnull GuiGraphics g = Objects.requireNonNull(graphics, "graphics");
        final @Nonnull Font font = Objects.requireNonNull(mc.font, "font");

        // Check if QA session is active
        if (!TestingSession.INSTANCE.isSessionActive()) {
            // Show minimal "start QA" hint
            renderInactiveHint(g, font);
            return;
        }

        // Get current active test
        TestCase currentTest = getActiveTest();

        // Handle test change animation
        if (currentTest != activeTest) {
            activeTest = currentTest;
            if (activeTest != null) {
                testStartTime = System.currentTimeMillis();
                showNewTestAnimation = true;
            }
        }

        // Update animation
        updateAnimation();

        if (minimized) {
            renderMinimized(g, font);
        } else if (activeTest != null) {
            renderActiveTest(g, font);
        } else {
            renderNoActiveTest(g, font);
        }
    }

    /**
     * Render hint when QA session is not active.
     */
    private static void renderInactiveHint(@Nonnull GuiGraphics g, @Nonnull Font font) {
        int screenHeight = g.guiHeight();
        int x = UIScaleManager.scale(10);
        int y = screenHeight - UIScaleManager.scale(30);

        String hint = "[F7] Open Testing Hub";
        int hintWidth = font.width(hint);
        int safeLeft = UIScaleManager.getSafeLeft();
        int safeRight = UIScaleManager.getSafeRight();
        int safeTop = UIScaleManager.getSafeTop();
        int safeBottom = UIScaleManager.getSafeBottom();
        x = Math.max(safeLeft, Math.min(x, safeRight - hintWidth - 8));
        y = Math.max(safeTop, Math.min(y, safeBottom - UIScaleManager.scale(14)));
        g.fill(x - 4, y - 4, x + hintWidth + 8, y + 12, TestingUiTheme.Hud.HINT_BG);
        UIScaleManager.drawScaledString(g, font, hint, x, y, TEXT_MUTED, false);
    }

    /**
     * Render minimized mode - just a small indicator.
     */
    private static void renderMinimized(@Nonnull GuiGraphics g, @Nonnull Font font) {
        int screenHeight = g.guiHeight();
        int x = UIScaleManager.scale(10);
        int y = screenHeight - UIScaleManager.scale(50);

        // Compact bar
        int barWidth = UIScaleManager.scale(160);
        int barHeight = UIScaleManager.scale(18);
        int safeLeft = UIScaleManager.getSafeLeft();
        int safeRight = UIScaleManager.getSafeRight();
        int safeTop = UIScaleManager.getSafeTop();
        int safeBottom = UIScaleManager.getSafeBottom();
        x = Math.max(safeLeft, Math.min(x, safeRight - barWidth));
        y = Math.max(safeTop, Math.min(y, safeBottom - barHeight));
        g.fill(x, y, x + barWidth, y + barHeight, PANEL_BG);
        drawBorder(g, x, y, barWidth, barHeight, PANEL_BORDER);

        // Progress and hint
        float progress = TestingSession.INSTANCE.getProgressPercent() / 100f;
        String progressText = String.format("QA: %.0f%% [Tab to expand]", progress * 100);
        progressText = truncateToWidth(font, progressText, barWidth - UIScaleManager.scale(8));
        UIScaleManager.drawScaledString(g, font, progressText, x + 4, y + UIScaleManager.scale(5), TEXT_SECONDARY, false);

        // Mini progress bar
        int progressWidth = barWidth - 8;
        int miniBarY = y + UIScaleManager.scale(14);
        int miniBarHeight = UIScaleManager.scale(2);
        g.fill(x + 4, miniBarY, x + 4 + progressWidth, miniBarY + miniBarHeight, PROGRESS_BG);
        g.fill(x + 4, miniBarY, x + 4 + (int)(progressWidth * progress), miniBarY + miniBarHeight,
               progress >= 1f ? PROGRESS_COMPLETE : PROGRESS_FILL);
    }

    /**
     * Render when no specific test is active but session is running.
     */
    private static void renderNoActiveTest(@Nonnull GuiGraphics g, @Nonnull Font font) {
        int screenHeight = g.guiHeight();
        int x = UIScaleManager.scale(10);
        int y = screenHeight - UIScaleManager.scale(80);

        int sPanelWidth = UIScaleManager.scale(PANEL_WIDTH);
        int panelWidth = Math.min(sPanelWidth, UIScaleManager.getSafeWidth());
        int sPanelPadding = UIScaleManager.scale(PANEL_PADDING);
        int sHeaderHeight = UIScaleManager.scale(HEADER_HEIGHT);
        int sLineHeight = UIScaleManager.getScaledLineHeight(font, LINE_HEIGHT);
        int height = UIScaleManager.scale(60);
        int safeLeft = UIScaleManager.getSafeLeft();
        int safeRight = UIScaleManager.getSafeRight();
        int safeTop = UIScaleManager.getSafeTop();
        int safeBottom = UIScaleManager.getSafeBottom();
        x = Math.max(safeLeft, Math.min(x, safeRight - panelWidth));
        y = Math.max(safeTop, Math.min(y, safeBottom - height));

        // Panel background
        g.fill(x, y, x + panelWidth, y + height, PANEL_BG);
        drawBorder(g, x, y, panelWidth, height, PANEL_BORDER);

        // Header
        g.fill(x, y, x + panelWidth, y + sHeaderHeight, PANEL_HEADER_BG);
        int contentWidth = Math.max(0, panelWidth - sPanelPadding * 2);
        String header = truncateToWidth(font, "QA Testing - Ready", contentWidth);
        UIScaleManager.drawScaledString(g, font, header, x + sPanelPadding, y + UIScaleManager.scale(7), TEXT_TITLE, false);

        // Content
        int contentY = y + sHeaderHeight + sPanelPadding;

        // Progress
        int passed = TestingSession.INSTANCE.getPassedTests();
        int total = TestingSession.INSTANCE.getTotalTests();
        String progressText = String.format("Progress: %d/%d tests (%.0f%%)",
            passed, total, TestingSession.INSTANCE.getProgressPercent());
        progressText = truncateToWidth(font, progressText, contentWidth);
        UIScaleManager.drawScaledString(g, font, progressText, x + sPanelPadding, contentY, TEXT_PRIMARY, false);
        contentY += sLineHeight + 4;

        // Hint
        String hint = truncateToWidth(font, "[F7] Open Testing Hub to select a test", contentWidth);
        UIScaleManager.drawScaledString(g, font, hint, x + sPanelPadding, contentY, TEXT_MUTED, false);
    }

    /**
     * Render active test panel with full details.
     */
    private static void renderActiveTest(@Nonnull GuiGraphics g, @Nonnull Font font) {
        if (activeTest == null) return;

        int screenWidth = g.guiWidth();
        int screenHeight = g.guiHeight();
        int x = UIScaleManager.scale(10);

        // Scaled layout values
        int sPanelWidth = UIScaleManager.scale(PANEL_WIDTH);
        int panelWidth = Math.min(sPanelWidth, UIScaleManager.getSafeWidth());
        int sPanelPadding = UIScaleManager.scale(PANEL_PADDING);
        int sHeaderHeight = UIScaleManager.scale(HEADER_HEIGHT);
        int sLineHeight = UIScaleManager.getScaledLineHeight(font, LINE_HEIGHT);

        // Calculate dynamic height based on instructions
        String instructionText = Objects.requireNonNullElse(activeTest.getInstructions(), "");
        String[] instructions = instructionText.lines().toArray(String[]::new);
        int instructionLines = Math.min(instructions.length, 6); // Max 6 steps visible
        int height = sHeaderHeight + sPanelPadding * 3 +
                     sLineHeight * 2 + // Name + Description
                     sLineHeight * instructionLines + // Instructions
                     UIScaleManager.scale(20) + // Progress bar
                     sLineHeight + UIScaleManager.scale(8); // Actions hint

        int y = screenHeight - height - UIScaleManager.scale(20);
        int safeLeft = UIScaleManager.getSafeLeft();
        int safeRight = UIScaleManager.getSafeRight();
        int safeTop = UIScaleManager.getSafeTop();
        int safeBottom = UIScaleManager.getSafeBottom();
        x = Math.max(safeLeft, Math.min(x, safeRight - panelWidth));
        y = Math.max(safeTop, Math.min(y, safeBottom - height));

        // New test animation (slide in + glow)
        if (showNewTestAnimation) {
            float animTime = (System.currentTimeMillis() - testStartTime) / 500f;
            if (animTime > 1f) {
                showNewTestAnimation = false;
            } else {
                // Slide in from left
                x = (int)(x - UIScaleManager.scale(100) * (1f - animTime));
                x = Math.max(safeLeft, Math.min(x, safeRight - panelWidth));
                // Glow effect
                int glowAlpha = (int)(100 * (1f - animTime));
                g.fill(x - 2, y - 2, x + panelWidth + 4, y + height + 4,
                       (glowAlpha << 24) | TestingUiTheme.Hud.NEW_TEST_GLOW_RGB);
            }
        }

        // Panel background
        g.fill(x, y, x + panelWidth, y + height, PANEL_BG);
        drawBorder(g, x, y, panelWidth, height,
                   activeTest.getStatus() == TestCase.TestStatus.IN_PROGRESS ? TEXT_WARNING : PANEL_BORDER);

        // Header with status color
        int headerColor = activeTest.getStatus() == TestCase.TestStatus.IN_PROGRESS
            ? TestingUiTheme.Hud.HEADER_IN_PROGRESS
            : PANEL_HEADER_BG;
        g.fill(x, y, x + panelWidth, y + sHeaderHeight, headerColor);

        // Status indicator dot
        int statusColor = activeTest.getStatus().getColor();
        int dotSize = UIScaleManager.scale(6);
        g.fill(x + sPanelPadding, y + UIScaleManager.scale(8), x + sPanelPadding + dotSize, y + UIScaleManager.scale(14), statusColor);

        // Test name
        String testName = Objects.requireNonNullElse(activeTest.getName(), "Unnamed Test");
        int nameMaxWidth = Math.max(0, panelWidth - UIScaleManager.scale(50));
        testName = truncateToWidth(font, testName, nameMaxWidth);
        UIScaleManager.drawScaledString(g, font, testName, x + sPanelPadding + UIScaleManager.scale(10), y + UIScaleManager.scale(7), TEXT_TITLE, false);

        // Priority badge
        String priorityBadge = "[" + activeTest.getPriority().name().charAt(0) + "]";
        int badgeX = x + panelWidth - UIScaleManager.getScaledStringWidth(font, priorityBadge) - sPanelPadding;
        UIScaleManager.drawScaledString(g, font, priorityBadge, badgeX, y + UIScaleManager.scale(7), activeTest.getPriority().getColor(), false);

        int contentY = y + sHeaderHeight + sPanelPadding;

        // Category
        String category = Objects.requireNonNullElse(activeTest.getCategory(), "Uncategorized");
        category = truncateToWidth(font, category, panelWidth - sPanelPadding * 2);
        UIScaleManager.drawScaledString(g, font, category, x + sPanelPadding, contentY, TEXT_SECONDARY, false);
        contentY += sLineHeight + 2;

        // Description (truncated)
        String desc = Objects.requireNonNullElse(activeTest.getDescription(), "");
        desc = truncateToWidth(font, desc, panelWidth - sPanelPadding * 2);
        UIScaleManager.drawScaledString(g, font, desc, x + sPanelPadding, contentY, TEXT_MUTED, false);
        contentY += sLineHeight + UIScaleManager.scale(6);

        // Instructions as checklist
        UIScaleManager.drawScaledString(g, font, "Steps:", x + sPanelPadding, contentY, TEXT_PRIMARY, false);
        contentY += sLineHeight;

        for (int i = 0; i < instructionLines; i++) {
            String step = instructions[i].trim();
            if (step.isEmpty()) continue;

            // Checkbox style
            String checkbox = "[ ] ";
            int checkboxColor = TEXT_MUTED;

            // Auto-check based on progress if available
            if (activeTest.hasProgressChecker()) {
                float progress = activeTest.getCachedProgress();
                float stepThreshold = (float)(i + 1) / instructions.length;
                if (progress >= stepThreshold) {
                    checkbox = "[X] ";
                    checkboxColor = TEXT_SUCCESS;
                }
            }

            // Truncate long steps
            step = truncateToWidth(font, checkbox + step, panelWidth - sPanelPadding * 2 - UIScaleManager.scale(4));
            UIScaleManager.drawScaledString(g, font, step, x + sPanelPadding + 4, contentY, checkboxColor, false);
            contentY += sLineHeight;
        }

        if (instructions.length > instructionLines) {
            String more = "... +" + (instructions.length - instructionLines) + " more";
            more = truncateToWidth(font, more, panelWidth - sPanelPadding * 2);
            UIScaleManager.drawScaledString(g, font, more, x + sPanelPadding + 4, contentY, TEXT_MUTED, false);
            contentY += sLineHeight;
        }

        contentY += 4;

        // Progress bar (if test has progress checker)
        if (activeTest.hasProgressChecker() || activeTest.hasAutoValidator()) {
            float progress = activeTest.getCachedProgress();
            int progressBarWidth = panelWidth - sPanelPadding * 2;
            int progressBarHeight = UIScaleManager.scale(8);

            g.fill(x + sPanelPadding, contentY, x + sPanelPadding + progressBarWidth, contentY + progressBarHeight, PROGRESS_BG);
            int fillWidth = (int)(progressBarWidth * progress);
            g.fill(x + sPanelPadding, contentY, x + sPanelPadding + fillWidth, contentY + progressBarHeight,
                   progress >= 1f ? PROGRESS_COMPLETE : PROGRESS_FILL);

            // Progress percentage
            final String progressText = Objects.requireNonNull(
                String.format("%.0f%%", progress * 100),
                "progress text");
            int textX = x + sPanelPadding + progressBarWidth / 2 - UIScaleManager.getScaledStringWidth(font, progressText) / 2;
            UIScaleManager.drawScaledString(g, font, progressText, textX, contentY, TEXT_PRIMARY, false);

            contentY += UIScaleManager.scale(12);
        }

        // Action hints
        String hints = "[1] Pass  [2] Fail  [3] Skip  [F7] Hub";
        hints = truncateToWidth(font, hints, panelWidth - sPanelPadding * 2);
        int hintsWidth = UIScaleManager.getScaledStringWidth(font, hints);
        UIScaleManager.drawScaledString(g, font, hints, x + panelWidth / 2 - hintsWidth / 2, contentY, TEXT_MUTED, false);
    }

    private static String truncateToWidth(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (maxWidth <= 0 || font.width(text) <= maxWidth) {
            return text;
        }
        int ellipsisWidth = font.width("...");
        int allowed = Math.max(0, maxWidth - ellipsisWidth);
        if (allowed <= 0) {
            return "...";
        }
        return font.plainSubstrByWidth(text, allowed) + "...";
    }

    private static void drawBorder(GuiGraphics g, int x, int y, int width, int height, int color) {
        g.fill(x, y, x + width, y + 1, color);                    // Top
        g.fill(x, y + height - 1, x + width, y + height, color);  // Bottom
        g.fill(x, y, x + 1, y + height, color);                   // Left
        g.fill(x + width - 1, y, x + width, y + height, color);   // Right
    }

    private static void updateAnimation() {
        long now = System.currentTimeMillis();
        if (now - lastUpdateTime > 50) {
            lastUpdateTime = now;
            animationProgress += 0.05f;
            if (animationProgress > 1f) animationProgress = 0f;
        }
    }

    /**
     * Get the currently active (in-progress) test.
     */
    @Nullable
    private static TestCase getActiveTest() {
        // First check for explicitly in-progress tests
        for (TestCase test : TestingSession.INSTANCE.getAllTests()) {
            if (test.getStatus() == TestCase.TestStatus.IN_PROGRESS) {
                return test;
            }
        }
        return null;
    }

    // === Public API ===

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void toggle() {
        enabled = !enabled;
    }

    public static void toggleMinimized() {
        minimized = !minimized;
    }

    public static boolean isMinimized() {
        return minimized;
    }

    /**
     * Set a test as active (called when user clicks a test in QA screen).
     */
    public static void setActiveTest(TestCase test) {
        if (test != activeTest) {
            activeTest = test;
            testStartTime = System.currentTimeMillis();
            showNewTestAnimation = true;
        }
    }

    /**
     * Clear the active test display.
     */
    public static void clearActiveTest() {
        activeTest = null;
        showNewTestAnimation = false;
    }

    /**
     * Handle keyboard shortcuts for quick actions while HUD is visible.
     * Returns true if the key was consumed.
     */
    public static boolean handleKeyPress(int keyCode) {
        if (!enabled || activeTest == null) return false;
        if (!TestingSession.INSTANCE.isSessionActive()) return false;

        // Tab = toggle minimize
        if (keyCode == 258) { // Tab
            toggleMinimized();
            return true;
        }

        // Only handle action keys when not minimized
        if (minimized) return false;

        // 1 = Pass
        if (keyCode == 49) {
            activeTest.markPassed("Quick pass via HUD");
            TestingSession.INSTANCE.markDirty();
            advanceToNextTest();
            return true;
        }
        // 2 = Fail
        if (keyCode == 50) {
            activeTest.markFailed("Quick fail via HUD", "Marked as failed via in-game HUD");
            TestingSession.INSTANCE.markDirty();
            return true;
        }
        // 3 = Skip
        if (keyCode == 51) {
            activeTest.skip("Quick skip via HUD");
            TestingSession.INSTANCE.markDirty();
            advanceToNextTest();
            return true;
        }

        return false;
    }

    private static void advanceToNextTest() {
        TestCase next = TestingSession.INSTANCE.getNextPendingTest();
        if (next != null) {
            next.startTest();
            setActiveTest(next);
        } else {
            clearActiveTest();
        }
    }
}
