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
import com.devmod.testing.TestCase;

@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)

public class ActiveTestHudOverlay {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "active_test_hud");

    // === Colors (matching QATestingScreen style) ===
    private static final int PANEL_BG = 0xE0202030;           // Dark blue-purple
    private static final int PANEL_BORDER = 0xFF5588FF;       // Light blue border
    private static final int PANEL_HEADER_BG = 0xFF303050;    // Darker header

    private static final int TEXT_TITLE = 0xFF00FFFF;         // Cyan
    private static final int TEXT_PRIMARY = 0xFFFFFFFF;       // White
    private static final int TEXT_SECONDARY = 0xFFBBBBBB;     // Light gray
    private static final int TEXT_MUTED = 0xFF888888;         // Gray
    private static final int TEXT_SUCCESS = 0xFF55FF55;       // Green
    private static final int TEXT_WARNING = 0xFFFFAA00;       // Orange

    private static final int PROGRESS_BG = 0xFF333344;
    private static final int PROGRESS_FILL = 0xFF5588FF;
    private static final int PROGRESS_COMPLETE = 0xFF55FF55;

    // === Layout ===
    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_PADDING = 8;
    private static final int LINE_HEIGHT = 11;
    private static final int HEADER_HEIGHT = 22;

    // === State ===
    private static boolean enabled = true;
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
        int x = 10;
        int y = screenHeight - 30;

        String hint = "[F7] Open Testing Hub";
        g.fill(x - 4, y - 4, x + font.width(hint) + 8, y + 12, 0x80000000);
        g.drawString(font, hint, x, y, TEXT_MUTED, false);
    }

    /**
     * Render minimized mode - just a small indicator.
     */
    private static void renderMinimized(@Nonnull GuiGraphics g, @Nonnull Font font) {
        int screenHeight = g.guiHeight();
        int x = 10;
        int y = screenHeight - 50;

        // Compact bar
        int barWidth = 160;
        g.fill(x, y, x + barWidth, y + 18, PANEL_BG);
        drawBorder(g, x, y, barWidth, 18, PANEL_BORDER);

        // Progress and hint
        float progress = TestingSession.INSTANCE.getProgressPercent() / 100f;
        String progressText = String.format("QA: %.0f%% [Tab to expand]", progress * 100);
        g.drawString(font, progressText, x + 4, y + 5, TEXT_SECONDARY, false);

        // Mini progress bar
        int progressWidth = barWidth - 8;
        g.fill(x + 4, y + 14, x + 4 + progressWidth, y + 16, PROGRESS_BG);
        g.fill(x + 4, y + 14, x + 4 + (int)(progressWidth * progress), y + 16,
               progress >= 1f ? PROGRESS_COMPLETE : PROGRESS_FILL);
    }

    /**
     * Render when no specific test is active but session is running.
     */
    private static void renderNoActiveTest(@Nonnull GuiGraphics g, @Nonnull Font font) {
        int screenHeight = g.guiHeight();
        int x = 10;
        int y = screenHeight - 80;

        int height = 60;

        // Panel background
        g.fill(x, y, x + PANEL_WIDTH, y + height, PANEL_BG);
        drawBorder(g, x, y, PANEL_WIDTH, height, PANEL_BORDER);

        // Header
        g.fill(x, y, x + PANEL_WIDTH, y + HEADER_HEIGHT, PANEL_HEADER_BG);
        g.drawString(font, "QA Testing - Ready", x + PANEL_PADDING, y + 7, TEXT_TITLE, false);

        // Content
        int contentY = y + HEADER_HEIGHT + PANEL_PADDING;

        // Progress
        int passed = TestingSession.INSTANCE.getPassedTests();
        int total = TestingSession.INSTANCE.getTotalTests();
        String progressText = String.format("Progress: %d/%d tests (%.0f%%)",
            passed, total, TestingSession.INSTANCE.getProgressPercent());
        g.drawString(font, progressText, x + PANEL_PADDING, contentY, TEXT_PRIMARY, false);
        contentY += LINE_HEIGHT + 4;

        // Hint
        g.drawString(font, "[F7] Open Testing Hub to select a test", x + PANEL_PADDING, contentY, TEXT_MUTED, false);
    }

    /**
     * Render active test panel with full details.
     */
    private static void renderActiveTest(@Nonnull GuiGraphics g, @Nonnull Font font) {
        if (activeTest == null) return;

        int screenHeight = g.guiHeight();
        int x = 10;

        // Calculate dynamic height based on instructions
        String instructionText = Objects.requireNonNullElse(activeTest.getInstructions(), "");
        String[] instructions = instructionText.split("\n");
        int instructionLines = Math.min(instructions.length, 6); // Max 6 steps visible
        int height = HEADER_HEIGHT + PANEL_PADDING * 3 +
                     LINE_HEIGHT * 2 + // Name + Description
                     LINE_HEIGHT * instructionLines + // Instructions
                     20 + // Progress bar
                     LINE_HEIGHT + 8; // Actions hint

        int y = screenHeight - height - 20;

        // New test animation (slide in + glow)
        if (showNewTestAnimation) {
            float animTime = (System.currentTimeMillis() - testStartTime) / 500f;
            if (animTime > 1f) {
                showNewTestAnimation = false;
            } else {
                // Slide in from left
                x = (int)(x - 100 * (1f - animTime));
                // Glow effect
                int glowAlpha = (int)(100 * (1f - animTime));
                g.fill(x - 2, y - 2, x + PANEL_WIDTH + 4, y + height + 4,
                       (glowAlpha << 24) | 0x5588FF);
            }
        }

        // Panel background
        g.fill(x, y, x + PANEL_WIDTH, y + height, PANEL_BG);
        drawBorder(g, x, y, PANEL_WIDTH, height,
                   activeTest.getStatus() == TestCase.TestStatus.IN_PROGRESS ? TEXT_WARNING : PANEL_BORDER);

        // Header with status color
        int headerColor = activeTest.getStatus() == TestCase.TestStatus.IN_PROGRESS ?
                          0xFF504020 : PANEL_HEADER_BG;
        g.fill(x, y, x + PANEL_WIDTH, y + HEADER_HEIGHT, headerColor);

        // Status indicator dot
        int statusColor = activeTest.getStatus().getColor();
        g.fill(x + PANEL_PADDING, y + 8, x + PANEL_PADDING + 6, y + 14, statusColor);

        // Test name
        String testName = Objects.requireNonNullElse(activeTest.getName(), "Unnamed Test");
        testName = Objects.requireNonNull(testName, "testName");
        if (font.width(testName) > PANEL_WIDTH - 50) {
            testName = testName.substring(0, 25) + "...";
        }
        g.drawString(font, testName, x + PANEL_PADDING + 10, y + 7, TEXT_TITLE, false);

        // Priority badge
        String priorityBadge = "[" + activeTest.getPriority().name().charAt(0) + "]";
        int badgeX = x + PANEL_WIDTH - font.width(priorityBadge) - PANEL_PADDING;
        g.drawString(font, priorityBadge, badgeX, y + 7, activeTest.getPriority().getColor(), false);

        int contentY = y + HEADER_HEIGHT + PANEL_PADDING;

        // Category
        String category = Objects.requireNonNullElse(activeTest.getCategory(), "Uncategorized");
        g.drawString(font, category, x + PANEL_PADDING, contentY, TEXT_SECONDARY, false);
        contentY += LINE_HEIGHT + 2;

        // Description (truncated)
        String desc = Objects.requireNonNullElse(activeTest.getDescription(), "");
        if (desc.length() > 45) desc = desc.substring(0, 42) + "...";
        g.drawString(font, desc, x + PANEL_PADDING, contentY, TEXT_MUTED, false);
        contentY += LINE_HEIGHT + 6;

        // Instructions as checklist
        g.drawString(font, "Steps:", x + PANEL_PADDING, contentY, TEXT_PRIMARY, false);
        contentY += LINE_HEIGHT;

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
            if (step.length() > 38) step = step.substring(0, 35) + "...";

            g.drawString(font, checkbox + step, x + PANEL_PADDING + 4, contentY, checkboxColor, false);
            contentY += LINE_HEIGHT;
        }

        if (instructions.length > instructionLines) {
            g.drawString(font, "... +" + (instructions.length - instructionLines) + " more",
                x + PANEL_PADDING + 4, contentY, TEXT_MUTED, false);
            contentY += LINE_HEIGHT;
        }

        contentY += 4;

        // Progress bar (if test has progress checker)
        if (activeTest.hasProgressChecker() || activeTest.hasAutoValidator()) {
            float progress = activeTest.getCachedProgress();
            int progressBarWidth = PANEL_WIDTH - PANEL_PADDING * 2;

            g.fill(x + PANEL_PADDING, contentY, x + PANEL_PADDING + progressBarWidth, contentY + 8, PROGRESS_BG);
            int fillWidth = (int)(progressBarWidth * progress);
            g.fill(x + PANEL_PADDING, contentY, x + PANEL_PADDING + fillWidth, contentY + 8,
                   progress >= 1f ? PROGRESS_COMPLETE : PROGRESS_FILL);

            // Progress percentage
            final String progressText = Objects.requireNonNull(
                String.format("%.0f%%", progress * 100),
                "progress text");
            int textX = x + PANEL_PADDING + progressBarWidth / 2 - font.width(progressText) / 2;
            g.drawString(font, progressText, textX, contentY, TEXT_PRIMARY, false);

            contentY += 12;
        }

        // Action hints
        String hints = "[1] Pass  [2] Fail  [3] Skip  [F7] Hub";
        int hintsWidth = font.width(hints);
        g.drawString(font, hints, x + PANEL_WIDTH / 2 - hintsWidth / 2, contentY, TEXT_MUTED, false);
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
