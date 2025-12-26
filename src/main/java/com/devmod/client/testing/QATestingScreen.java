package com.devmod.client.testing;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.testing.TutorialManager.TutorialStep;
import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.UIConstants;
import com.devmod.testing.TestCase;
import com.devmod.util.I18n;
@OnlyIn(Dist.CLIENT)
public class QATestingScreen extends Screen {

    // Layout constants
    private static final int SIDEBAR_WIDTH = 200;
    private static final int TEST_CARD_HEIGHT = 65;
    private static final int HEADER_HEIGHT = 50;
    private static final int FOOTER_HEIGHT = 40;
    private static final int PADDING = 8;
    private static final int CARD_SPACING = 5;

    // State
    private String selectedCategory = null;
    private TestCase selectedTest = null;
    private int testScrollOffset = 0;
    private int categoryScrollOffset = 0;
    private int maxTestScroll = 0;
    private int maxCategoryScroll = 0;
    private boolean sessionStarted = false;

    // Blur control - save original value to restore on close
    private int originalBlurValue = 0;

    // UI Components
    private EditBox testerNameField;
    private EditorButton startSessionButton;
    private EditorButton resumeSessionButton;
    private EditorButton saveReportButton;
    private EditorButton copyReportButton;
    private final EditorButton passButton = new EditorButton("qa-pass", "PASS").style(EditorButton.Style.SUCCESS);
    private final EditorButton failButton = new EditorButton("qa-fail", "FAIL").style(EditorButton.Style.DANGER);
    private final EditorButton skipButton = new EditorButton("qa-skip", "SKIP").style(EditorButton.Style.NORMAL);
    private final EditorButton autoButton = new EditorButton("qa-auto", "AUTO-CHECK").style(EditorButton.Style.PRIMARY);
    private EditorButton closeButton;

    public QATestingScreen() {
        super(I18n.translate("devmod.testing.qa_testing"));
        // Check if there's an existing session to resume
        if (TestingSession.INSTANCE.isSessionActive() || TestingSession.INSTANCE.hasExistingSession()) {
            sessionStarted = TestingSession.INSTANCE.getCompletedTests() > 0 ||
                             TestingSession.INSTANCE.isSessionActive();
        }

        // Disable menu blur when opening this screen
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            OptionInstance<Integer> blurOption = mc.options.menuBackgroundBlurriness();
            originalBlurValue = blurOption.get();
            blurOption.set(0); // Disable blur
        }
    }

    @Override
    protected void init() {
        super.init();
        final @Nonnull Font font = safeFont();

        // Tester name input (only shown before session starts)
        this.testerNameField = new EditBox(
            font, this.width / 2 - 100, this.height / 2 - 30, 200, 20,
            I18n.translate("devmod.testing.tester_name")
        );
        this.testerNameField.setMaxLength(50);
        this.testerNameField.setValue("Tester");
        this.testerNameField.setVisible(!sessionStarted);
        this.addRenderableWidget(Objects.requireNonNull(testerNameField, "testerNameField"));

        // Start session button (only for new sessions)
        boolean hasExisting = TestingSession.INSTANCE.hasExistingSession() &&
                              TestingSession.INSTANCE.getCompletedTests() > 0;

        this.startSessionButton = EditorButton.builder(
            "qa-start-session",
            (hasExisting ? I18n.translate("devmod.testing.new_session") : I18n.translate("devmod.testing.start_session")).getString())
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.LARGE)
            .onClick(() -> invokeAction(ActionIds.QA_SESSION_START))
            .build();

        // Resume session button (if there's an existing session)
        this.resumeSessionButton = EditorButton.builder(
            "qa-resume-session",
            I18n.translate("devmod.testing.resume_session",
                TestingSession.INSTANCE.getCompletedTests(),
                TestingSession.INSTANCE.getTotalTests()).getString())
            .style(EditorButton.Style.SUCCESS)
            .size(EditorButton.Size.MEDIUM)
            .onClick(() -> invokeAction(ActionIds.QA_SESSION_RESUME))
            .build();

        this.saveReportButton = EditorButton.builder(
            "qa-save-report",
            I18n.translate("devmod.testing.save_report").getString()
        ).style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .onClick(() -> invokeAction(ActionIds.QA_REPORT_SAVE))
            .build();

        this.copyReportButton = EditorButton.builder(
            "qa-copy-report",
            I18n.translate("devmod.testing.copy_clipboard").getString()
        ).style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.MEDIUM)
            .onClick(() -> invokeAction(ActionIds.QA_REPORT_COPY))
            .build();

        // Close button (rendered manually)
        closeButton = EditorButton.builder("qa-close", I18n.ui("close").getString())
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onClose)
            .build();

        passButton.onClick(() -> invokeAction(ActionIds.QA_TEST_PASS, "Manual pass", null));
        failButton.onClick(() -> invokeAction(ActionIds.QA_TEST_FAIL, "Manual failure", "Marked as failed by tester"));
        skipButton.onClick(() -> invokeAction(ActionIds.QA_TEST_SKIP, "Skipped by tester", null));
        autoButton.onClick(() -> invokeAction(ActionIds.QA_TEST_AUTO));

        // Set first category as selected
        List<String> categories = TestingSession.INSTANCE.getCategories();
        if (!categories.isEmpty() && selectedCategory == null) {
            selectedCategory = categories.get(0);
        }
    }

    private void startNewSession() {
        String name = testerNameField.getValue().trim();
        if (name.isEmpty()) name = "Anonymous";
        TestingSession.INSTANCE.resetSession();
        TestingSession.INSTANCE.startSession(name);
        sessionStarted = true;
        updateButtonVisibility();
    }

    private void resumeSession() {
        String name = testerNameField.getValue().trim();
        if (name.isEmpty()) name = TestingSession.INSTANCE.getTesterName();
        TestingSession.INSTANCE.resumeSession(name);
        sessionStarted = true;
        updateButtonVisibility();
    }

    private void updateButtonVisibility() {
        testerNameField.setVisible(false);
    }

    private void saveReport() {
        try {
            TestingSession.INSTANCE.captureLogs();
            String path = TestingSession.INSTANCE.saveReport();
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(I18n.translate("devmod.testing.report_saved", path), false);
            }
        } catch (IOException e) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(
                    I18n.translate("devmod.testing.report_error", e.getMessage()), false);
            }
        }
    }

    private void copyReport() {
        TestingSession.INSTANCE.captureLogs();
        TestingSession.INSTANCE.copyReportToClipboard();
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(I18n.translate("devmod.testing.report_copied"), false);
        }
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Draw solid opaque background directly - this covers the blurred world behind
        graphics.fill(0, 0, this.width, this.height, 0xFF1A1A1A);

        // Render content
        if (!sessionStarted) {
            renderStartScreen(graphics, mouseX, mouseY);
        } else {
            renderTestingInterface(graphics, mouseX, mouseY);
        }

        // Render widgets (buttons, text fields) on top
        for (var widget : this.renderables) {
            widget.render(graphics, mouseX, mouseY, partialTick);
        }

        // Render action buttons with Impact styling
        renderActionButtons(graphics, mouseX, mouseY);
    }

    private void renderStartScreen(GuiGraphics graphics, int mouseX, int mouseY) {
        final @Nonnull Font font = safeFont();
        // Title
        AxiomRenderer.drawCenteredTitle(graphics, font, this.width, 30, "DevMod QA Testing Framework");

        // Tutorial panel on left side
        renderTutorialPanel(graphics, mouseX, mouseY);

        // Gamification stats on right side
        renderGamificationPanel(graphics, mouseX, mouseY);

        // Center content
        int centerX = this.width / 2;
        int y = 70;

        // Instructions
        graphics.drawCenteredString(font, "Welcome to the QA Testing Session", centerX, y, UIConstants.Text.PRIMARY());
        y += 16;
        graphics.drawCenteredString(font, "Complete tests to verify all mod features", centerX, y, UIConstants.Text.SECONDARY());
        y += 12;
        graphics.drawCenteredString(font, "Progress is tracked like achievements", centerX, y, UIConstants.Text.SECONDARY());

        // Show existing session info if available
        boolean hasExisting = TestingSession.INSTANCE.hasExistingSession() &&
                              TestingSession.INSTANCE.getCompletedTests() > 0;
        if (hasExisting) {
            y = this.height / 2 - 70;
            graphics.drawCenteredString(font, "Previous Session Found!", centerX, y, UIConstants.Status.SUCCESS());
            y += 15;
            final @Nonnull String progress = Objects.requireNonNull(String.format(
                "Progress: %d/%d tests completed (%.0f%%)",
                TestingSession.INSTANCE.getCompletedTests(),
                TestingSession.INSTANCE.getTotalTests(),
                TestingSession.INSTANCE.getProgressPercent()
            ), "progress");
            graphics.drawCenteredString(font, progress, centerX, y, UIConstants.Text.PRIMARY());
            y += 12;
            graphics.drawCenteredString(font, "Tester: " + TestingSession.INSTANCE.getTesterName(),
                centerX, y, UIConstants.Text.SECONDARY());
        }

        // Stats preview
        y = this.height / 2 + 70;
        int total = TestingSession.INSTANCE.getTotalTests();
        graphics.drawCenteredString(font, total + " tests across " +
            TestingSession.INSTANCE.getCategories().size() + " categories",
            centerX, y, UIConstants.Text.MUTED());
    }

    /**
     * Render the tutorial guidance panel on the left side of start screen.
     */
    private void renderTutorialPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        final @Nonnull Font font = safeFont();
        int panelX = 10;
        int panelY = 60;
        int panelWidth = 200;
        int panelHeight = 160;

        // Panel background
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xE0202035);
        AxiomRenderer.drawBorder(graphics, panelX, panelY, panelWidth, panelHeight, UIConstants.Border.ACCENT());

        // Header
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 18, 0xFF303050);
        graphics.drawString(font, "Tutorial Guide", panelX + 8, panelY + 5, UIConstants.Text.TITLE(), false);

        int contentY = panelY + 24;

        // Current phase
        TutorialManager.TutorialPhase phase = TutorialManager.INSTANCE.getCurrentPhase();
        graphics.drawString(font, "Phase: " + phase.getDisplayName(), panelX + 8, contentY, UIConstants.Text.PRIMARY(), false);
        contentY += 12;
        graphics.drawString(font, phase.getDescription(), panelX + 8, contentY, UIConstants.Text.MUTED(), false);
        contentY += 16;

        // Current step instructions
        TutorialStep step = TutorialManager.INSTANCE.getCurrentStep();
        if (step != null) {
            graphics.drawString(font, "Current Step:", panelX + 8, contentY, UIConstants.Text.SECONDARY(), false);
            contentY += 11;

            // Step title
            String titleRaw = step.getTitle();
            @Nonnull String title = Objects.requireNonNull(titleRaw != null ? titleRaw : "", "title");
            if (font.width(title) > panelWidth - 16) {
                title = title.substring(0, Math.min(title.length(), 22)) + "...";
            }
            graphics.drawString(font, title, panelX + 8, contentY, UIConstants.Text.ACCENT(), false);
            contentY += 14;

            // Step instructions (first 3)
            String[] instructions = step.getInstructions();
            for (int i = 0; i < Math.min(3, instructions.length); i++) {
                String instr = instructions[i];
                if (instr.length() > 28) instr = instr.substring(0, 25) + "...";
                graphics.drawString(font, "- " + instr, panelX + 8, contentY, UIConstants.Text.MUTED(), false);
                contentY += 10;
            }

            // Hint
            contentY += 4;
            String hintRaw = step.getHint();
            @Nonnull String hint = Objects.requireNonNull(hintRaw != null ? hintRaw : "", "hint");
            if (hint.length() > 30) hint = hint.substring(0, 27) + "...";
            graphics.drawString(font, hint, panelX + 8, contentY, 0xFFFFAA00, false);
        }
    }

    /**
     * Render the gamification stats panel on the right side of start screen.
     */
    private void renderGamificationPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        final @Nonnull Font font = safeFont();
        int panelWidth = 180;
        int panelX = this.width - panelWidth - 10;
        int panelY = 60;
        int panelHeight = 140;

        // Panel background
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xE0202035);
        AxiomRenderer.drawBorder(graphics, panelX, panelY, panelWidth, panelHeight, 0xFFFFAA00);

        // Header
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 18, 0xFF403020);
        graphics.drawString(font, "Tester Stats", panelX + 8, panelY + 5, 0xFFFFAA00, false);

        int contentY = panelY + 24;

        // Level
        int level = TutorialManager.INSTANCE.getLevel();
        graphics.drawString(font, "Level: " + level, panelX + 8, contentY, UIConstants.Text.PRIMARY(), false);
        contentY += 14;

        // XP bar
        int xp = TutorialManager.INSTANCE.getTotalXP();
        float levelProgress = TutorialManager.INSTANCE.getLevelProgress();
        int xpToNext = TutorialManager.INSTANCE.getXPToNextLevel();

        graphics.drawString(font, "XP: " + xp, panelX + 8, contentY, UIConstants.Text.SECONDARY(), false);
        contentY += 12;

        // Progress bar
        int barWidth = panelWidth - 16;
        graphics.fill(panelX + 8, contentY, panelX + 8 + barWidth, contentY + 8, 0xFF333344);
        graphics.fill(panelX + 8, contentY, panelX + 8 + (int)(barWidth * levelProgress), contentY + 8, 0xFFFFAA00);
        contentY += 12;

        graphics.drawString(font, xpToNext + " XP to next level", panelX + 8, contentY, UIConstants.Text.MUTED(), false);
        contentY += 16;

        // Achievements count
        int achievements = TutorialManager.INSTANCE.getAchievementCount();
        graphics.drawString(font, "Achievements: " + achievements, panelX + 8, contentY, UIConstants.Text.PRIMARY(), false);
        contentY += 14;

        // Suggested test
        TestCase suggested = TutorialManager.INSTANCE.getSuggestedTest();
        if (suggested != null) {
            graphics.drawString(font, "Suggested:", panelX + 8, contentY, UIConstants.Text.SECONDARY(), false);
            contentY += 11;
            String testName = suggested.getName();
            if (testName.length() > 22) testName = testName.substring(0, 19) + "...";
            graphics.drawString(font, testName, panelX + 8, contentY, UIConstants.Status.SUCCESS(), false);
        }
    }

    private void renderTestingInterface(GuiGraphics graphics, int mouseX, int mouseY) {
        // Header
        renderHeader(graphics);

        // Sidebar with categories
        renderSidebar(graphics, mouseX, mouseY);

        // Main content - test list
        renderTestList(graphics, mouseX, mouseY);

        // Test details panel (if test selected)
        if (selectedTest != null) {
            renderTestDetails(graphics, mouseX, mouseY);
        }

        // Footer with progress
        renderFooter(graphics);
    }

    private void renderHeader(GuiGraphics graphics) {
        final @Nonnull Font font = safeFont();
        // Header background
        graphics.fill(0, 0, this.width, HEADER_HEIGHT, UIConstants.Background.HEADER());
        AxiomRenderer.drawSeparator(graphics, 0, HEADER_HEIGHT - 1, this.width);

        // Title
        graphics.drawString(font, "DevMod QA Testing", PADDING, 8, UIConstants.Text.WHITE(), false);

        // Session info
        String sessionInfo = "Tester: " + TestingSession.INSTANCE.getTesterName();
        graphics.drawString(font, sessionInfo, PADDING, 25, UIConstants.Text.SECONDARY(), false);

        // Progress bar in header
        int progressBarWidth = 200;
        int progressBarX = this.width - progressBarWidth - PADDING;
        int progressBarY = 15;
        float progress = TestingSession.INSTANCE.getProgressPercent() / 100f;
        AxiomRenderer.drawProgressBar(graphics, progressBarX, progressBarY, progressBarWidth, 12, progress, UIConstants.Status.SUCCESS());

        String progressText = String.format("%.0f%% Complete", TestingSession.INSTANCE.getProgressPercent());
        graphics.drawString(font, progressText, progressBarX, progressBarY + 15, UIConstants.Text.PRIMARY(), false);
    }

    private void renderSidebar(GuiGraphics graphics, int mouseX, int mouseY) {
        final @Nonnull Font font = safeFont();
        int sidebarX = 0;
        int sidebarY = HEADER_HEIGHT;
        int sidebarHeight = this.height - HEADER_HEIGHT - FOOTER_HEIGHT;

        // Sidebar background
        graphics.fill(sidebarX, sidebarY, SIDEBAR_WIDTH, sidebarY + sidebarHeight, UIConstants.Background.PANEL());
        AxiomRenderer.drawSeparator(graphics, SIDEBAR_WIDTH - 1, sidebarY, 1);

        // Category list header
        int headerY = sidebarY + PADDING;
        graphics.drawString(font, "Categories", PADDING, headerY, UIConstants.Text.TITLE(), false);
        headerY += 20;

        Map<String, List<TestCase>> categories = TestingSession.INSTANCE.getCategorizedTests();

        // Calculate scroll bounds for categories
        int categoryItemHeight = 32;
        int visibleCategoryHeight = sidebarHeight - 30; // Account for title
        int totalCategoryHeight = categories.size() * categoryItemHeight;
        maxCategoryScroll = Math.max(0, totalCategoryHeight - visibleCategoryHeight);
        categoryScrollOffset = Math.max(0, Math.min(categoryScrollOffset, maxCategoryScroll));

        // Enable scissor for clipping
        int clipTop = headerY;
        int clipBottom = sidebarY + sidebarHeight;
        graphics.enableScissor(sidebarX, clipTop, SIDEBAR_WIDTH - 8, clipBottom);

        int y = headerY - categoryScrollOffset;
        for (String category : categories.keySet()) {
            // Skip categories above visible area
            if (y + categoryItemHeight < clipTop) {
                y += categoryItemHeight;
                continue;
            }
            // Stop if below visible area
            if (y > clipBottom) break;

            List<TestCase> tests = categories.get(category);

            // Count completed/total
            long completed = tests.stream().filter(t ->
                t.getStatus() == TestCase.TestStatus.PASSED ||
                t.getStatus() == TestCase.TestStatus.FAILED ||
                t.getStatus() == TestCase.TestStatus.SKIPPED
            ).count();

            boolean isSelected = category.equals(selectedCategory);
            boolean isHovered = mouseX >= PADDING && mouseX < SIDEBAR_WIDTH - PADDING &&
                               mouseY >= y && mouseY < y + 25 &&
                               mouseY >= clipTop && mouseY < clipBottom;

            // Background for selected/hovered
            if (isSelected) {
                graphics.fill(PADDING - 2, y - 2, SIDEBAR_WIDTH - PADDING + 2, y + 22, UIConstants.Background.ACTIVE());
            } else if (isHovered) {
                graphics.fill(PADDING - 2, y - 2, SIDEBAR_WIDTH - PADDING + 2, y + 22, UIConstants.Background.HOVER());
            }

            // Truncate long category names (keep at least 6 chars for readability)
            String displayCategory = category;
            int maxCategoryWidth = SIDEBAR_WIDTH - PADDING * 2 - 50; // Leave room for progress
            if (font.width(displayCategory) > maxCategoryWidth) {
                int minChars = Math.min(6, category.length());
                while (font.width(displayCategory + "...") > maxCategoryWidth && displayCategory.length() > minChars) {
                    displayCategory = displayCategory.substring(0, displayCategory.length() - 1);
                }
                displayCategory += "...";
            }

            // Category name
            int textColor = isSelected ? UIConstants.Text.ACCENT() : UIConstants.Text.PRIMARY();
            graphics.drawString(font, displayCategory, PADDING + 5, y + 2, textColor, false);

            // Progress indicator
            String progressStr = completed + "/" + tests.size();
            int progressColor = completed == tests.size() ? UIConstants.Status.SUCCESS() : UIConstants.Text.MUTED();
            int progressWidth = font.width(progressStr);
            graphics.drawString(font, progressStr, SIDEBAR_WIDTH - PADDING - progressWidth - 10, y + 2, progressColor, false);

            // Progress bar mini
            float catProgress = tests.isEmpty() ? 0 : (float) completed / tests.size();
            int barColor = catProgress == 1f ? UIConstants.Status.SUCCESS() : UIConstants.Border.ACCENT();
            graphics.fill(PADDING, y + 15, PADDING + (int)((SIDEBAR_WIDTH - PADDING * 2 - 15) * catProgress), y + 17, barColor);

            y += categoryItemHeight;
        }

        graphics.disableScissor();

        // Render scrollbar if needed
        if (maxCategoryScroll > 0) {
            renderScrollbar(graphics, SIDEBAR_WIDTH - 6, headerY, 4, visibleCategoryHeight,
                           categoryScrollOffset, maxCategoryScroll);
        }
    }

    private void renderTestList(GuiGraphics graphics, int mouseX, int mouseY) {
        final @Nonnull Font font = safeFont();
        if (selectedCategory == null) return;

        int listX = SIDEBAR_WIDTH + PADDING;
        int listY = HEADER_HEIGHT + PADDING;
        int listWidth = this.width - SIDEBAR_WIDTH - PADDING * 2;
        int listHeight = this.height - HEADER_HEIGHT - FOOTER_HEIGHT - PADDING * 2;

        // If test details are shown, reduce list width
        if (selectedTest != null) {
            listWidth = listWidth / 2 - PADDING;
        }

        // Title
        graphics.drawString(font, selectedCategory + " Tests", listX, listY, UIConstants.Text.TITLE(), false);
        listY += 20;

        List<TestCase> tests = TestingSession.INSTANCE.getCategorizedTests().get(selectedCategory);
        if (tests == null) return;

        // Calculate scroll bounds
        int totalContentHeight = tests.size() * (TEST_CARD_HEIGHT + CARD_SPACING);
        int visibleHeight = listHeight - 25; // Account for title
        maxTestScroll = Math.max(0, totalContentHeight - visibleHeight);
        testScrollOffset = Math.max(0, Math.min(testScrollOffset, maxTestScroll));

        // Enable scissor for clipping
        int clipTop = listY;
        int clipBottom = this.height - FOOTER_HEIGHT - PADDING;
        graphics.enableScissor(listX, clipTop, listX + listWidth, clipBottom);

        int cardY = listY - testScrollOffset;
        for (int i = 0; i < tests.size(); i++) {
            // Skip rendering cards that are above visible area
            if (cardY + TEST_CARD_HEIGHT + CARD_SPACING < clipTop) {
                cardY += TEST_CARD_HEIGHT + CARD_SPACING;
                continue;
            }
            // Stop rendering cards that are below visible area
            if (cardY > clipBottom) break;

            TestCase test = tests.get(i);
            boolean isHovered = mouseX >= listX && mouseX < listX + listWidth &&
                               mouseY >= cardY && mouseY < cardY + TEST_CARD_HEIGHT - 5 &&
                               mouseY >= clipTop && mouseY < clipBottom;
            boolean isSelected = test == selectedTest;

            renderTestCard(graphics, listX, cardY, listWidth, test, isHovered, isSelected);
            cardY += TEST_CARD_HEIGHT + CARD_SPACING;
        }

        graphics.disableScissor();

        // Render scrollbar if needed
        if (maxTestScroll > 0) {
            renderScrollbar(graphics, listX + listWidth - 6, listY, 4, visibleHeight,
                           testScrollOffset, maxTestScroll);
        }
    }

    private void renderScrollbar(GuiGraphics graphics, int x, int y, int width, int height,
                                  int scrollOffset, int maxScroll) {
        // Track background
        graphics.fill(x, y, x + width, y + height, 0x40FFFFFF);

        // Thumb size and position
        float scrollRatio = (float) scrollOffset / maxScroll;
        int thumbHeight = Math.max(20, (int) (height * height / (float) (height + maxScroll)));
        int thumbY = y + (int) ((height - thumbHeight) * scrollRatio);

        // Thumb
        graphics.fill(x, thumbY, x + width, thumbY + thumbHeight, 0x80FFFFFF);
    }

    /**
     * Truncate text to fit within maxWidth pixels, adding ellipsis if needed.
     */
    private @Nonnull String truncateText(String text, int maxWidth) {
        final @Nonnull Font font = safeFont();
        final @Nonnull String safeText = Objects.requireNonNull(text != null ? text : "", "safeText");
        if (font.width(safeText) <= maxWidth) return safeText;
        String ellipsis = "...";
        int minChars = Math.min(6, safeText.length());
        String truncated = safeText;
        while (font.width(truncated + ellipsis) > maxWidth && truncated.length() > minChars) {
            truncated = truncated.substring(0, truncated.length() - 1);
        }
        return truncated + ellipsis;
    }

    private void renderTestCard(GuiGraphics graphics, int x, int y, int width, TestCase test,
                                 boolean hovered, boolean selected) {
        final @Nonnull Font font = safeFont();
        int height = TEST_CARD_HEIGHT - 10;

        // Card background
        int bgColor = selected ? UIConstants.Background.ACTIVE() :
                      (hovered ? UIConstants.Background.HOVER() : UIConstants.Background.PANEL());
        graphics.fill(x, y, x + width, y + height, bgColor);

        // Left border color based on status
        int statusColor = test.getStatus().getColor();
        graphics.fill(x, y, x + 4, y + height, statusColor);

        // Priority indicator
        int priorityColor = test.getPriority().getColor();
        graphics.fill(x + width - 20, y + 5, x + width - 5, y + 15, priorityColor);

        // Test name (truncated to fit card width)
        String testName = truncateText(test.getName(), width - 40); // Leave room for priority indicator
        graphics.drawString(font, testName, x + 12, y + 5, UIConstants.Text.PRIMARY(), false);

        // Description (truncated to fit card width)
        String desc = truncateText(test.getDescription(), width - 24);
        graphics.drawString(font, desc, x + 12, y + 18, UIConstants.Text.SECONDARY(), false);

        // Status text
        String statusText = "[" + test.getStatus().name() + "]";
        graphics.drawString(font, statusText, x + 12, y + 32, statusColor, false);

        // Auto-validate indicator
        if (test.hasAutoValidator()) {
            graphics.drawString(font, "[AUTO]", x + 80, y + 32, UIConstants.Text.MUTED(), false);
        }

        // Action buttons when hovered
        if (hovered && test.getStatus() != TestCase.TestStatus.PASSED) {
            // Quick action hints
            String hint = test.getStatus() == TestCase.TestStatus.PENDING ?
                "Click to start" : "Click for details";
            int hintWidth = font.width(hint);
            graphics.drawString(font, hint, x + width - hintWidth - 25, y + height - 15, UIConstants.Text.MUTED(), false);
        }

        // Border
        int borderColor = selected ? UIConstants.Border.ACCENT() : UIConstants.Border.DEFAULT();
        AxiomRenderer.drawBorder(graphics, x, y, width, height, borderColor);
    }

    private void renderTestDetails(GuiGraphics graphics, int mouseX, int mouseY) {
        final @Nonnull Font font = safeFont();
        int detailsX = SIDEBAR_WIDTH + (this.width - SIDEBAR_WIDTH) / 2 + PADDING;
        int detailsY = HEADER_HEIGHT + PADDING;
        int detailsWidth = (this.width - SIDEBAR_WIDTH) / 2 - PADDING * 2;
        int detailsHeight = this.height - HEADER_HEIGHT - FOOTER_HEIGHT - PADDING * 2;

        // Panel background
        AxiomRenderer.drawPanel(graphics, font, detailsX, detailsY, detailsWidth, detailsHeight, "Test Details");

        int contentY = detailsY + 30;
        int contentX = detailsX + PADDING;

        // Test name (truncated to fit panel width)
        int maxTextWidth = detailsWidth - PADDING * 2;
        String detailName = truncateText(selectedTest.getName(), maxTextWidth);
        graphics.drawString(font, detailName, contentX, contentY, UIConstants.Text.WHITE(), false);
        contentY += 15;

        // Status and Priority
        String statusLine = "Status: " + selectedTest.getStatus().name() +
                           " | Priority: " + selectedTest.getPriority().name();
        graphics.drawString(font, statusLine, contentX, contentY, selectedTest.getStatus().getColor(), false);
        contentY += 20;

        // Description (truncated to fit panel width)
        AxiomRenderer.drawSectionHeader(graphics, font, contentX, contentY, "Description:");
        contentY += 12;
        String detailDesc = truncateText(selectedTest.getDescription(), maxTextWidth);
        graphics.drawString(font, detailDesc, contentX, contentY, UIConstants.Text.SECONDARY(), false);
        contentY += 20;

        // Instructions (truncated per line to fit panel width)
        AxiomRenderer.drawSectionHeader(graphics, font, contentX, contentY, "Instructions:");
        contentY += 12;

        String[] lines = selectedTest.getInstructions().split("\n");
        for (String line : lines) {
            String truncatedLine = truncateText(line, maxTextWidth);
            graphics.drawString(font, truncatedLine, contentX, contentY, UIConstants.Text.PRIMARY(), false);
            contentY += 12;
        }
        contentY += 10;

        // Comments if any
        if (selectedTest.getComments() != null && !selectedTest.getComments().isEmpty()) {
            AxiomRenderer.drawSectionHeader(graphics, font, contentX, contentY, "Tester Comments:");
            contentY += 12;
            graphics.drawString(font, selectedTest.getComments(), contentX, contentY, UIConstants.Text.MUTED(), false);
            contentY += 15;
        }

        // Action buttons at bottom
        int buttonY = detailsY + detailsHeight - 60;
        int buttonWidth = 80;
        int buttonSpacing = 10;
        int buttonX = contentX;

        boolean enabled = selectedTest != null;
        passButton.enabled(enabled);
        failButton.enabled(enabled);
        skipButton.enabled(enabled);
        autoButton.enabled(enabled && selectedTest.hasAutoValidator());

        passButton.render(graphics, buttonX, buttonY, buttonWidth, UIConstants.Size.BUTTON_HEIGHT, mouseX, mouseY);
        buttonX += buttonWidth + buttonSpacing;

        failButton.render(graphics, buttonX, buttonY, buttonWidth, UIConstants.Size.BUTTON_HEIGHT, mouseX, mouseY);
        buttonX += buttonWidth + buttonSpacing;

        skipButton.render(graphics, buttonX, buttonY, buttonWidth, UIConstants.Size.BUTTON_HEIGHT, mouseX, mouseY);

        if (selectedTest.hasAutoValidator()) {
            buttonX += buttonWidth + buttonSpacing + 20;
            int autoWidth = buttonWidth + 20;
            autoButton.render(graphics, buttonX, buttonY, autoWidth, UIConstants.Size.BUTTON_HEIGHT, mouseX, mouseY);
        }
    }

    private void renderFooter(GuiGraphics graphics) {
        final @Nonnull Font font = safeFont();
        int footerY = this.height - FOOTER_HEIGHT;

        // Footer background
        graphics.fill(0, footerY, this.width, this.height, UIConstants.Background.HEADER());
        AxiomRenderer.drawSeparator(graphics, 0, footerY, this.width);

        // Stats
        int passed = TestingSession.INSTANCE.getPassedTests();
        int failed = TestingSession.INSTANCE.getFailedTests();
        int total = TestingSession.INSTANCE.getTotalTests();
        int remaining = total - TestingSession.INSTANCE.getCompletedTests();

        String stats = String.format("Passed: %d | Failed: %d | Remaining: %d / %d",
            passed, failed, remaining, total);
        graphics.drawString(font, stats, PADDING, footerY + 12, UIConstants.Text.PRIMARY(), false);

        // Keyboard hints
        String hints = "[Enter] Confirm | [1-3] Pass/Fail/Skip | [Esc] Close";
        int hintsWidth = font.width(hints);
        graphics.drawString(font, hints, this.width / 2 - hintsWidth / 2, footerY + 12, UIConstants.Text.MUTED(), false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean hasExisting = TestingSession.INSTANCE.hasExistingSession() &&
            TestingSession.INSTANCE.getCompletedTests() > 0;

        if (button == 0) {
            if (!sessionStarted && startSessionButton != null && startSessionButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (!sessionStarted && hasExisting && resumeSessionButton != null && resumeSessionButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (sessionStarted && saveReportButton != null && saveReportButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (sessionStarted && copyReportButton != null && copyReportButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (closeButton != null && closeButton.mouseClicked(mouseX, mouseY, button)) {
                closeButton.mouseReleased(mouseX, mouseY, button);
                return true;
            }
        }

        if (button == 0) { // Left click
            // Check category clicks
            if (mouseX < SIDEBAR_WIDTH && sessionStarted) {
                int clipTop = HEADER_HEIGHT + PADDING + 20;
                int clipBottom = this.height - FOOTER_HEIGHT;
                if (mouseY >= clipTop && mouseY < clipBottom) {
                    int y = clipTop - categoryScrollOffset;
                    int categoryItemHeight = 32;
                    for (String category : TestingSession.INSTANCE.getCategories()) {
                        if (mouseY >= y && mouseY < y + 25 && y >= clipTop - categoryItemHeight && y < clipBottom) {
                            selectedCategory = category;
                            selectedTest = null;
                            testScrollOffset = 0; // Reset test scroll when changing category
                            return true;
                        }
                        y += categoryItemHeight;
                    }
                }
            }

            // Check test card clicks
            if (mouseX >= SIDEBAR_WIDTH + PADDING && sessionStarted && selectedCategory != null) {
                int listWidth = selectedTest != null ?
                    (this.width - SIDEBAR_WIDTH - PADDING * 2) / 2 - PADDING :
                    this.width - SIDEBAR_WIDTH - PADDING * 2;

                int clipTop = HEADER_HEIGHT + PADDING + 20;
                int clipBottom = this.height - FOOTER_HEIGHT - PADDING;

                if (mouseX < SIDEBAR_WIDTH + PADDING + listWidth && mouseY >= clipTop && mouseY < clipBottom) {
                    int y = clipTop - testScrollOffset;
                    List<TestCase> tests = TestingSession.INSTANCE.getCategorizedTests().get(selectedCategory);
                    if (tests != null) {
                        for (TestCase test : tests) {
                            if (mouseY >= y && mouseY < y + TEST_CARD_HEIGHT - 5 && y >= clipTop - TEST_CARD_HEIGHT && y < clipBottom) {
                                if (selectedTest == test) {
                                    // Start test if pending
                                    if (test.getStatus() == TestCase.TestStatus.PENDING) {
                                        test.startTest();
                                    }
                                } else {
                                    selectedTest = test;
                                }
                                return true;
                            }
                            y += TEST_CARD_HEIGHT + CARD_SPACING;
                        }
                    }
                }
            }

            if (selectedTest != null) {
                if (passButton.mouseClicked(mouseX, mouseY, button)) return true;
                if (failButton.mouseClicked(mouseX, mouseY, button)) return true;
                if (skipButton.mouseClicked(mouseX, mouseY, button)) return true;
                if (autoButton.mouseClicked(mouseX, mouseY, button)) return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean hasExisting = TestingSession.INSTANCE.hasExistingSession() &&
            TestingSession.INSTANCE.getCompletedTests() > 0;

        boolean handled = false;
        if (startSessionButton != null) handled |= startSessionButton.mouseReleased(mouseX, mouseY, button);
        if (hasExisting && resumeSessionButton != null) handled |= resumeSessionButton.mouseReleased(mouseX, mouseY, button);
        if (saveReportButton != null) handled |= saveReportButton.mouseReleased(mouseX, mouseY, button);
        if (copyReportButton != null) handled |= copyReportButton.mouseReleased(mouseX, mouseY, button);
        if (closeButton != null) handled |= closeButton.mouseReleased(mouseX, mouseY, button);

        handled |= passButton.mouseReleased(mouseX, mouseY, button) ||
                   failButton.mouseReleased(mouseX, mouseY, button) ||
                   skipButton.mouseReleased(mouseX, mouseY, button) ||
                   autoButton.mouseReleased(mouseX, mouseY, button);
        return handled || super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean invokeAction(String actionId) {
        ActionContext context = ClientActionContexts.forClient(ActionOrigin.UI, this);
        return ActionRegistry.invoke(actionId, context);
    }

    private boolean invokeAction(String actionId, String reason, String details) {
        QaActionRequest request = new QaActionRequest(this, reason, details);
        ActionContext context = ClientActionContexts.forClient(ActionOrigin.UI, request);
        return ActionRegistry.invoke(actionId, context);
    }

    private void handlePass(String reason) {
        if (selectedTest == null) return;
        selectedTest.markPassed(reason);
        TestingSession.INSTANCE.markDirty();
        TutorialManager.INSTANCE.awardTestXP(selectedTest, true);
        advanceToNextTest();
    }

    private void handleFail(String reason, String details) {
        if (selectedTest == null) return;
        selectedTest.markFailed(reason, details);
        TestingSession.INSTANCE.markDirty();
        TutorialManager.INSTANCE.awardTestXP(selectedTest, false);
    }

    private void handleSkip(String reason) {
        if (selectedTest == null) return;
        selectedTest.skip(reason);
        TestingSession.INSTANCE.markDirty();
        advanceToNextTest();
    }

    private void handleAutoCheck() {
        if (selectedTest == null || !selectedTest.hasAutoValidator()) return;
        boolean passed = selectedTest.runAutoValidation();
        TestingSession.INSTANCE.markDirty();
        TutorialManager.INSTANCE.awardTestXP(selectedTest, passed);
        if (passed) {
            advanceToNextTest();
        }
    }

    private void advanceToNextTest() {
        TestCase next = TestingSession.INSTANCE.getNextPendingTest();
        if (next != null) {
            selectedTest = next;
            selectedCategory = next.getCategory();
            // Update the in-game HUD to show this test
            ActiveTestHudOverlay.setActiveTest(next);
        } else {
            selectedTest = null;
            ActiveTestHudOverlay.clearActiveTest();
        }
    }

    private record QaActionRequest(QATestingScreen screen, String reason, String details) {}

    public static final class Actions {
        private Actions() {}

        public static boolean hasActiveTest(ActionContext context) {
            return resolveTest(context) != null && TestingSession.INSTANCE.isSessionActive();
        }

        public static boolean hasAutoTest(ActionContext context) {
            TestCase test = resolveTest(context);
            return test != null && test.hasAutoValidator();
        }

        public static void startSession(ActionContext context) {
            QATestingScreen screen = resolveScreen(context);
            if (screen != null) {
                screen.startNewSession();
                return;
            }
            String testerName = resolveTesterName(context);
            TestingSession.INSTANCE.resetSession();
            TestingSession.INSTANCE.startSession(testerName);
        }

        public static void resumeSession(ActionContext context) {
            QATestingScreen screen = resolveScreen(context);
            if (screen != null) {
                screen.resumeSession();
                return;
            }
            String testerName = resolveTesterName(context);
            TestingSession.INSTANCE.resumeSession(testerName);
        }

        public static void saveReport(ActionContext context) {
            QATestingScreen screen = resolveScreen(context);
            if (screen != null) {
                screen.saveReport();
                return;
            }
            try {
                TestingSession.INSTANCE.captureLogs();
                String path = TestingSession.INSTANCE.saveReport();
                context.sendSuccess(I18n.translate("devmod.testing.report_saved", path), true);
            } catch (Exception e) {
                context.sendFailure(I18n.translate("devmod.testing.report_error", e.getMessage()));
            }
        }

        public static void copyReport(ActionContext context) {
            QATestingScreen screen = resolveScreen(context);
            if (screen != null) {
                screen.copyReport();
                return;
            }
            TestingSession.INSTANCE.captureLogs();
            TestingSession.INSTANCE.copyReportToClipboard();
            context.sendSuccess(I18n.translate("devmod.testing.report_copied"), true);
        }

        public static void passTest(ActionContext context) {
            QATestingScreen screen = resolveScreen(context);
            String reason = resolveReason(context, "Manual pass");
            if (screen != null) {
                screen.handlePass(reason);
                return;
            }
            TestCase test = resolveTest(context);
            if (test == null) {
                return;
            }
            test.markPassed(reason);
            TestingSession.INSTANCE.markDirty();
            TutorialManager.INSTANCE.awardTestXP(test, true);
            advanceToNextTestFallback();
        }

        public static void failTest(ActionContext context) {
            QATestingScreen screen = resolveScreen(context);
            String reason = resolveReason(context, "Manual failure");
            String details = resolveDetails(context, "Marked as failed by tester");
            if (screen != null) {
                screen.handleFail(reason, details);
                return;
            }
            TestCase test = resolveTest(context);
            if (test == null) {
                return;
            }
            test.markFailed(reason, details);
            TestingSession.INSTANCE.markDirty();
            TutorialManager.INSTANCE.awardTestXP(test, false);
        }

        public static void skipTest(ActionContext context) {
            QATestingScreen screen = resolveScreen(context);
            String reason = resolveReason(context, "Skipped by tester");
            if (screen != null) {
                screen.handleSkip(reason);
                return;
            }
            TestCase test = resolveTest(context);
            if (test == null) {
                return;
            }
            test.skip(reason);
            TestingSession.INSTANCE.markDirty();
            advanceToNextTestFallback();
        }

        public static void autoCheckTest(ActionContext context) {
            QATestingScreen screen = resolveScreen(context);
            if (screen != null) {
                screen.handleAutoCheck();
                return;
            }
            TestCase test = resolveTest(context);
            if (test == null || !test.hasAutoValidator()) {
                return;
            }
            boolean passed = test.runAutoValidation();
            TestingSession.INSTANCE.markDirty();
            TutorialManager.INSTANCE.awardTestXP(test, passed);
            if (passed) {
                advanceToNextTestFallback();
            }
        }

        private static QATestingScreen resolveScreen(ActionContext context) {
            QaActionRequest request = context.getPayload(QaActionRequest.class);
            if (request != null && request.screen() != null) {
                return request.screen();
            }
            return context.getPayload(QATestingScreen.class);
        }

        private static String resolveTesterName(ActionContext context) {
            QATestingScreen screen = resolveScreen(context);
            if (screen != null && screen.testerNameField != null) {
                String name = screen.testerNameField.getValue().trim();
                if (!name.isEmpty()) {
                    return name;
                }
            }
            String name = TestingSession.INSTANCE.getTesterName();
            return (name == null || name.isBlank()) ? "Anonymous" : name;
        }

        private static String resolveReason(ActionContext context, String fallback) {
            QaActionRequest request = context.getPayload(QaActionRequest.class);
            if (request != null && request.reason() != null) {
                return request.reason();
            }
            return fallback;
        }

        private static String resolveDetails(ActionContext context, String fallback) {
            QaActionRequest request = context.getPayload(QaActionRequest.class);
            if (request != null && request.details() != null) {
                return request.details();
            }
            return fallback;
        }

        private static TestCase resolveTest(ActionContext context) {
            QATestingScreen screen = resolveScreen(context);
            if (screen != null) {
                return screen.selectedTest;
            }
            TestCase payloadTest = context.getPayload(TestCase.class);
            if (payloadTest != null) {
                return payloadTest;
            }
            for (TestCase test : TestingSession.INSTANCE.getAllTests()) {
                if (test.getStatus() == TestCase.TestStatus.IN_PROGRESS) {
                    return test;
                }
            }
            return null;
        }

        private static void advanceToNextTestFallback() {
            TestCase next = TestingSession.INSTANCE.getNextPendingTest();
            if (next != null) {
                next.startTest();
                ActiveTestHudOverlay.setActiveTest(next);
            } else {
                ActiveTestHudOverlay.clearActiveTest();
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!sessionStarted) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);

        int scrollAmount = (int) (scrollY * 25); // 25 pixels per scroll tick

        // Check if mouse is over test list area
        if (mouseX > SIDEBAR_WIDTH && mouseX < this.width) {
            testScrollOffset = Math.max(0, Math.min(testScrollOffset - scrollAmount, maxTestScroll));
            return true;
        }

        // Check if mouse is over category sidebar
        if (mouseX < SIDEBAR_WIDTH) {
            categoryScrollOffset = Math.max(0, Math.min(categoryScrollOffset - scrollAmount, maxCategoryScroll));
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (selectedTest != null && sessionStarted) {
            // 1 = Pass, 2 = Fail, 3 = Skip
            if (keyCode == 49) { // 1
                return invokeAction(ActionIds.QA_TEST_PASS, "Keyboard pass", null);
            } else if (keyCode == 50) { // 2
                return invokeAction(ActionIds.QA_TEST_FAIL, "Keyboard shortcut failure", "Marked as failed via keyboard");
            } else if (keyCode == 51) { // 3
                return invokeAction(ActionIds.QA_TEST_SKIP, "Keyboard shortcut skip", null);
            } else if (keyCode == 257) { // Enter - auto-validate if available
                if (selectedTest.hasAutoValidator()) {
                    return invokeAction(ActionIds.QA_TEST_AUTO);
                }
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private @Nonnull Font safeFont() {
        return Objects.requireNonNull(this.font, "font");
    }

    private void renderActionButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean hasExisting = TestingSession.INSTANCE.hasExistingSession() &&
            TestingSession.INSTANCE.getCompletedTests() > 0;

        // Start session
        if (!sessionStarted && startSessionButton != null) {
            startSessionButton.render(graphics, this.width / 2 - 75, this.height / 2 + 10, 150, 20, mouseX, mouseY);
        }

        // Resume session
        if (!sessionStarted && hasExisting && resumeSessionButton != null) {
            resumeSessionButton.render(graphics, this.width / 2 - 100, this.height / 2 + 35, 200, 20, mouseX, mouseY);
        }

        int buttonY = this.height - FOOTER_HEIGHT + 10;

        // Save / Copy (only after session started)
        if (sessionStarted && saveReportButton != null) {
            saveReportButton.render(graphics, this.width - 330, buttonY, 100, 20, mouseX, mouseY);
        }
        if (sessionStarted && copyReportButton != null) {
            copyReportButton.render(graphics, this.width - 220, buttonY, 110, 20, mouseX, mouseY);
        }

        // Close button (always visible)
        if (closeButton != null) {
            closeButton.render(graphics, this.width - 100, buttonY, 90, 20, mouseX, mouseY);
        }
    }

    @Override
    public void onClose() {
        // Restore original blur setting when closing the screen
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            mc.options.menuBackgroundBlurriness().set(originalBlurValue);
        }
        super.onClose();
    }

    @Override
    public void renderBackground(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Override to disable the default blurred background completely
        // Just fill with solid color, no blur
        graphics.fill(0, 0, this.width, this.height, 0xFF1A1A1A);
    }

    /**
     * Override to completely disable the blur effect in 1.21.1
     */
    @Override
    protected void renderBlurredBackground(float partialTick) {
        // Do nothing - disable blur completely
    }

    /**
     * Override to prevent dimming as well
     */
    @Override
    protected void renderMenuBackground(@Nonnull GuiGraphics graphics) {
        // Just solid background, no dimming or blur
        graphics.fill(0, 0, this.width, this.height, 0xFF1A1A1A);
    }

    /**
     * Override renderTransparentBackground to provide a solid color background
     */
    @Override
    public void renderTransparentBackground(@Nonnull GuiGraphics graphics) {
        // Solid color instead of transparent/blurred
        graphics.fill(0, 0, this.width, this.height, 0xFF1A1A1A);
    }
}
