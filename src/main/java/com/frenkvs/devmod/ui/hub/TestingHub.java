package com.frenkvs.devmod.ui.hub;

import com.frenkvs.devmod.actions.ActionIds;
import com.frenkvs.devmod.actions.ActionOrigin;
import com.frenkvs.devmod.actions.ActionRegistry;
import com.frenkvs.devmod.actions.client.ClientActionContexts;
import com.frenkvs.devmod.testing.ActiveTestHudOverlay;
import com.frenkvs.devmod.testing.TestCase;
import com.frenkvs.devmod.testing.TestingSession;
import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.UIConstants;
import com.frenkvs.devmod.ui.editor.components.EditorButton;
import com.frenkvs.devmod.util.I18n;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Set;

/**
 * Central unified hub for testing.
 * Combines: test selection, quick tools, progress tracking.
 *
 * 3-panel layout:
 * - Left: CategoryPanel (categories + test list + search)
 * - Center: TestDetailPanel (selected test details)
 * - Right: QuickToolsPanel (toggle overlay + editor shortcuts)
 * - Footer: ProgressFooter (progress bar + actions)
 */

public class TestingHub extends Screen {

    // === LAYOUT CONSTANTS ===
    private static final int HEADER_HEIGHT = 28;
    private static final int FOOTER_HEIGHT = 44;
    private static final int PANEL_GAP = 2;
    private static final int MIN_HUB_WIDTH = 700;
    private static final int MAX_HUB_WIDTH = 900;
    private static final int MIN_HUB_HEIGHT = 450;
    private static final int MAX_HUB_HEIGHT = 600;
    private static final int HEADER_BUTTON_WIDTH = 18;
    private static final int HEADER_BUTTON_GAP = 6;

    // === STATE ===
    private final TestingHubState state;

    // === PANELS ===
    private CategoryPanel categoryPanel;
    private TestDetailPanel detailPanel;
    private QuickToolsPanel toolsPanel;
    private ProgressFooter footer;

    // === SESSION INPUT ===
    private EditBox testerNameField;
    private boolean showSessionStart = false;
    private final EditorButton startButton = new EditorButton("hub-start", "Start New").style(EditorButton.Style.PRIMARY);
    private final EditorButton resumeButton = new EditorButton("hub-resume", "Resume").style(EditorButton.Style.PRIMARY);
    private final EditorButton headerCloseButton = new EditorButton("hub-close", "X")
        .size(EditorButton.Size.SMALL)
        .style(EditorButton.Style.DANGER)
        .playSound(false);
    private final EditorButton headerMinimizeButton = new EditorButton("hub-minimize", "-")
        .size(EditorButton.Size.SMALL)
        .style(EditorButton.Style.GHOST)
        .playSound(false);

    // === FOCUS ===
    private enum PanelFocus { CATEGORIES, DETAILS, TOOLS }
    private PanelFocus currentFocus = PanelFocus.CATEGORIES;

    // === CACHED DIMENSIONS ===
    private int hubX, hubY, hubWidth, hubHeight;

    public TestingHub() {
        super(I18n.translate("devmod.testing.testing_hub"));
        this.state = TestingHubState.INSTANCE;
    }

    @Override
    protected void init() {
        // Calculate responsive dimensions
        hubWidth = calculateHubWidth();
        hubHeight = calculateHubHeight();
        hubX = (width - hubWidth) / 2;
        hubY = (height - hubHeight) / 2;

        // Check if should show session start
        showSessionStart = !state.isSessionActive() && !state.hasExistingSession();

        if (showSessionStart) {
            initSessionStartUI();
        } else {
            // If there's an existing but inactive session, resume
            if (!state.isSessionActive() && state.hasExistingSession()) {
                state.resumeSession();
            }
            initPanels();
        }

        headerCloseButton.onClick(this::onClose);
        headerMinimizeButton.onClick(this::minimizeToHud);
    }

    private void initSessionStartUI() {
        // Centered tester name field
        int fieldWidth = 200;
        int fieldX = hubX + (hubWidth - fieldWidth) / 2;
        int fieldY = hubY + hubHeight / 2 - 30;

        @Nonnull Font uiFont = Objects.requireNonNull(font, "font");
        String labelText = Objects.requireNonNullElse(
            Objects.requireNonNull(I18n.translate("devmod.testing.tester_name"), "testerNameLabel").getString(),
            "Tester Name");
        String hintText = Objects.requireNonNullElse(
            Objects.requireNonNull(I18n.translate("devmod.testing.enter_name"), "testerNameHint").getString(),
            "Enter name");
        @Nonnull EditBox nameField = new EditBox(uiFont, fieldX, fieldY, fieldWidth, 20,
            Objects.requireNonNull(Component.literal(
                Objects.requireNonNull(labelText, "labelText")), "labelTextComponent"));
        nameField.setHint(Objects.requireNonNull(Component.literal(
            Objects.requireNonNull(hintText, "hintText")), "hintTextComponent"));
        nameField.setMaxLength(32);
        String testerName = Objects.requireNonNullElse(TestingSession.INSTANCE.getTesterName(), "");
        nameField.setValue(Objects.requireNonNull(testerName, "testerName"));
        testerNameField = nameField;
        this.addRenderableWidget(nameField);
    }

    private void initPanels() {
        @Nonnull Font uiFont = Objects.requireNonNull(font, "font");
        // Calculate panel widths
        int categoryWidth = Math.max(180, (int)(hubWidth * 0.22f));
        int toolsWidth = Math.max(170, (int)(hubWidth * 0.25f));
        int detailWidth = hubWidth - categoryWidth - toolsWidth - PANEL_GAP * 2;

        int contentY = hubY + HEADER_HEIGHT;
        int contentHeight = hubHeight - HEADER_HEIGHT - FOOTER_HEIGHT;

        // Initialize panels
        categoryPanel = new CategoryPanel(
            hubX, contentY, categoryWidth, contentHeight,
            uiFont, state,
            this::onCategorySelected,
            this::onTestSelected
        );

        detailPanel = new TestDetailPanel(
            hubX + categoryWidth + PANEL_GAP, contentY,
            detailWidth, contentHeight,
            uiFont, state,
            this::onVerdictGiven
        );

        toolsPanel = new QuickToolsPanel(
            hubX + categoryWidth + detailWidth + PANEL_GAP * 2, contentY,
            toolsWidth, contentHeight,
            uiFont, state,
            this::onToolToggled,
            this::onEditorOpened
        );

        footer = new ProgressFooter(
            hubX, hubY + hubHeight - FOOTER_HEIGHT,
            hubWidth, FOOTER_HEIGHT,
            uiFont,
            this::onSaveReport,
            this::onMinimize
        );

        // Load previous state
        if (state.hasActiveTest()) {
            detailPanel.setTest(state.getCurrentTest());
        }
    }

    // === RENDERING ===

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dark background
        AxiomRenderer.drawScreenBackground(graphics, this.width, this.height);

        if (showSessionStart) {
            renderSessionStart(graphics, mouseX, mouseY);
        } else {
            renderHub(graphics, mouseX, mouseY, partialTick);
        }

        // Render widgets
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderSessionStart(GuiGraphics graphics, int mouseX, int mouseY) {
        @Nonnull Font uiFont = Objects.requireNonNull(font, "font");
        // Central panel for session start
        int panelWidth = 350;
        int panelHeight = 200;
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;

        AxiomRenderer.drawPanel(graphics, uiFont, panelX, panelY, panelWidth, panelHeight, "Start Testing Session");

        int contentX = panelX + UIConstants.Spacing.PANEL_PADDING;
        int contentY = panelY + UIConstants.Spacing.HEADER_HEIGHT + UIConstants.Spacing.PANEL_PADDING + 10;

        // Instructions
        graphics.drawString(uiFont, "Welcome to DevMod QA Testing!", contentX, contentY, UIConstants.Text.PRIMARY(), false);
        contentY += 16;

        graphics.drawString(uiFont, "Enter your name to start tracking tests.", contentX, contentY, UIConstants.Text.SECONDARY(), false);
        contentY += 30;

        // Name label
        graphics.drawString(uiFont, "Tester Name:", contentX, contentY + 6, UIConstants.Text.SECONDARY(), false);
        contentY += 40;

        // Buttons
        int buttonWidth = 120;
        int buttonGap = 20;
        int buttonsX = panelX + (panelWidth - buttonWidth * 2 - buttonGap) / 2;

        // Start New
        startButton
            .style(EditorButton.Style.PRIMARY)
            .onClick(this::startSession);
        startButton.render(graphics, buttonsX, contentY, buttonWidth, UIConstants.Size.BUTTON_HEIGHT, mouseX, mouseY);

        // Resume (if available)
        if (TestingSession.INSTANCE.hasExistingSession()) {
            int resumeX = buttonsX + buttonWidth + buttonGap;
            resumeButton
                .style(EditorButton.Style.PRIMARY)
                .onClick(this::resumeSession);
            resumeButton.render(graphics, resumeX, contentY, buttonWidth, UIConstants.Size.BUTTON_HEIGHT, mouseX, mouseY);
        }

        // Hint
        int hintY = panelY + panelHeight - 20;
        AxiomRenderer.drawHint(graphics, uiFont, panelX + 10, hintY, "Press Enter to start, ESC to close");
    }

    private void renderHub(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Header
        renderHeader(graphics, mouseX, mouseY);

        // Panels with focus indicator
        if (categoryPanel != null) {
            categoryPanel.render(graphics, mouseX, mouseY, partialTick);
            if (currentFocus == PanelFocus.CATEGORIES) {
                renderFocusIndicator(graphics, categoryPanel.getX(), categoryPanel.getY(),
                    categoryPanel.getWidth(), categoryPanel.getHeight());
            }
        }
        if (detailPanel != null) {
            detailPanel.render(graphics, mouseX, mouseY, partialTick);
            if (currentFocus == PanelFocus.DETAILS) {
                renderFocusIndicator(graphics, detailPanel.getX(), detailPanel.getY(),
                    detailPanel.getWidth(), detailPanel.getHeight());
            }
        }
        if (toolsPanel != null) {
            toolsPanel.render(graphics, mouseX, mouseY, partialTick);
            if (currentFocus == PanelFocus.TOOLS) {
                renderFocusIndicator(graphics, toolsPanel.getX(), toolsPanel.getY(),
                    toolsPanel.getWidth(), toolsPanel.getHeight());
            }
        }

        // Footer
        if (footer != null) {
            footer.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    /**
     * Render focus indicator (accent border) around the focused panel.
     */
    private void renderFocusIndicator(GuiGraphics graphics, int x, int y, int width, int height) {
        int color = UIConstants.Border.ACCENT();
        // Draw 2px thick border on top of panel
        graphics.fill(x - 1, y - 1, x + width + 1, y, color);           // Top
        graphics.fill(x - 1, y + height, x + width + 1, y + height + 1, color); // Bottom
        graphics.fill(x - 1, y, x, y + height, color);                   // Left
        graphics.fill(x + width, y, x + width + 1, y + height, color);   // Right
    }

    private void renderHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        @Nonnull Font uiFont = Objects.requireNonNull(font, "font");
        // Background header
        graphics.fill(hubX, hubY, hubX + hubWidth, hubY + HEADER_HEIGHT, UIConstants.Background.HEADER());

        // Bottom border
        graphics.fill(hubX, hubY + HEADER_HEIGHT - 1, hubX + hubWidth, hubY + HEADER_HEIGHT, UIConstants.Border.DEFAULT());

        // Title
        graphics.drawString(uiFont, "DEVMOD TESTING HUB", hubX + 12, hubY + 10, UIConstants.Text.TITLE(), false);

        // Session info
        String tester = "Tester: " + TestingSession.INSTANCE.getTesterName();
        int testerWidth = uiFont.width(tester);
        graphics.drawString(uiFont, tester, hubX + hubWidth - testerWidth - 80, hubY + 10, UIConstants.Text.MUTED(), false);

        int buttonHeight = EditorButton.Size.SMALL.height();
        int buttonY = hubY + (HEADER_HEIGHT - buttonHeight) / 2;

        // Close button [X]
        int closeX = hubX + hubWidth - HEADER_BUTTON_WIDTH - HEADER_BUTTON_GAP;
        headerCloseButton.render(graphics, closeX, buttonY, HEADER_BUTTON_WIDTH, buttonHeight, mouseX, mouseY);

        // Minimize button [-]
        int minX = closeX - HEADER_BUTTON_WIDTH - HEADER_BUTTON_GAP;
        headerMinimizeButton.render(graphics, minX, buttonY, HEADER_BUTTON_WIDTH, buttonHeight, mouseX, mouseY);
    }

    // === INPUT HANDLING ===

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Session start mode
        if (showSessionStart) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                startSession();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.onClose();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        // ESC: Minimize instead of closing
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            minimizeToHud();
            return true;
        }

        // Tab: Cycle focus between panels
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            cycleFocus(Screen.hasShiftDown() ? -1 : 1);
            return true;
        }

        // 1/2/3: Quick verdict if there's an active test
        if (state.hasActiveTest()) {
            if (keyCode == GLFW.GLFW_KEY_1) {
                onVerdictGiven(state.getCurrentTest(), Verdict.PASS);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_2) {
                onVerdictGiven(state.getCurrentTest(), Verdict.FAIL);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_3) {
                onVerdictGiven(state.getCurrentTest(), Verdict.SKIP);
                return true;
            }
        }

        // Delegate to panels
        if (categoryPanel != null && currentFocus == PanelFocus.CATEGORIES) {
            if (categoryPanel.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        if (detailPanel != null && currentFocus == PanelFocus.DETAILS) {
            if (detailPanel.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        if (toolsPanel != null && currentFocus == PanelFocus.TOOLS) {
            if (toolsPanel.keyPressed(keyCode, scanCode, modifiers)) return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (categoryPanel != null && currentFocus == PanelFocus.CATEGORIES) {
            if (categoryPanel.charTyped(codePoint, modifiers)) return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;

        // Session start mode
        if (showSessionStart) {
            return handleSessionStartClick(mx, my, button);
        }

        if (headerCloseButton.mouseClicked(mx, my, button)) return true;
        if (headerMinimizeButton.mouseClicked(mx, my, button)) return true;

        // Panels
        if (categoryPanel != null && categoryPanel.isMouseOver(mx, my)) {
            currentFocus = PanelFocus.CATEGORIES;
            return categoryPanel.mouseClicked(mouseX, mouseY, button);
        }
        if (detailPanel != null && detailPanel.isMouseOver(mx, my)) {
            currentFocus = PanelFocus.DETAILS;
            return detailPanel.mouseClicked(mouseX, mouseY, button);
        }
        if (toolsPanel != null && toolsPanel.isMouseOver(mx, my)) {
            currentFocus = PanelFocus.TOOLS;
            return toolsPanel.mouseClicked(mouseX, mouseY, button);
        }
        if (footer != null && footer.isMouseOver(mx, my)) {
            return footer.mouseClicked(mouseX, mouseY, button);
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleSessionStartClick(int mx, int my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);

        // Start / Resume buttons
        if (startButton.mouseClicked(mx, my, button)) return true;
        if (startButton.mouseReleased(mx, my, button)) return true;

        if (TestingSession.INSTANCE.hasExistingSession()) {
            resumeButton.mouseClicked(mx, my, button);
            if (resumeButton.mouseReleased(mx, my, button)) return true;
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;

        if (showSessionStart) {
            if (startButton.mouseReleased(mx, my, button)) return true;
            if (TestingSession.INSTANCE.hasExistingSession() && resumeButton.mouseReleased(mx, my, button)) return true;
        }

        if (headerCloseButton.mouseReleased(mx, my, button)) return true;
        if (headerMinimizeButton.mouseReleased(mx, my, button)) return true;

        if (categoryPanel != null && categoryPanel.isMouseOver(mx, my)) {
            if (categoryPanel.mouseReleased(mouseX, mouseY, button)) return true;
        }
        if (detailPanel != null && detailPanel.isMouseOver(mx, my)) {
            if (detailPanel.mouseReleased(mouseX, mouseY, button)) return true;
        }
        if (toolsPanel != null && toolsPanel.isMouseOver(mx, my)) {
            if (toolsPanel.mouseReleased(mouseX, mouseY, button)) return true;
        }
        if (footer != null && footer.isMouseOver(mx, my)) {
            if (footer.mouseReleased(mouseX, mouseY, button)) return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int mx = (int) mouseX;
        int my = (int) mouseY;

        if (categoryPanel != null && categoryPanel.isMouseOver(mx, my)) {
            return categoryPanel.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (detailPanel != null && detailPanel.isMouseOver(mx, my)) {
            return detailPanel.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (toolsPanel != null && toolsPanel.isMouseOver(mx, my)) {
            return toolsPanel.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // === CALLBACKS ===

    private void onCategorySelected(String category) {
        state.setSelectedCategory(category);
        if (detailPanel != null) {
            detailPanel.clearTest();
        }
    }

    private void onTestSelected(TestCase test) {
        state.setCurrentTest(test);
        if (detailPanel != null) {
            detailPanel.setTest(test);
        }
        if (toolsPanel != null) {
            Set<ToolType> required = TestingHubState.inferRequiredTools(test);
            toolsPanel.highlightRequired(required);
        }
    }

    private void onVerdictGiven(TestCase test, Verdict verdict) {
        if (test == null) return;

        switch (verdict) {
            case PASS -> test.markPassed("Via Testing Hub");
            case FAIL -> test.markFailed("Via Testing Hub", "Manual failure");
            case SKIP -> test.skip("Via Testing Hub");
        }

        TestingSession.INSTANCE.markDirty();

        // Refresh UI
        if (footer != null) footer.refresh();
        if (categoryPanel != null) categoryPanel.refresh();

        // Advance to next test
        state.advanceToNextTest();
        if (state.hasActiveTest()) {
            if (detailPanel != null) {
                detailPanel.setTest(state.getCurrentTest());
            }
        } else {
            if (detailPanel != null) {
                detailPanel.showCompletionMessage();
            }
        }
    }

    private void onToolToggled(ToolType tool, @Nullable Boolean enabled) {
        boolean isEnabled = Boolean.TRUE.equals(enabled);
        state.setToolEnabled(tool, isEnabled);
        if (detailPanel != null) {
            detailPanel.updateToolStatus(tool, isEnabled);
        }
    }

    private void onEditorOpened(EditorType editor) {
        state.saveState();

        switch (editor) {
            case WEAPON -> {
                ActionRegistry.invoke(ActionIds.UI_ITEM_EDITOR_OPEN_WEAPON,
                    ClientActionContexts.forClient(ActionOrigin.UI));
            }
            case MOB_CONFIG -> {
                showNotification("Right-click a mob while holding the config tool");
                minimizeToHud();
            }
        }
    }

    private void onSaveReport() {
        try {
            String path = TestingSession.INSTANCE.saveReport();
            showNotification("Report saved to: " + path);
        } catch (Exception e) {
            showNotification("Failed to save report: " + e.getMessage());
        }
    }

    private void onMinimize() {
        minimizeToHud();
    }

    // === SESSION MANAGEMENT ===

    private void startSession() {
        String name = testerNameField != null ? testerNameField.getValue().trim() : "Anonymous";
        if (name.isEmpty()) name = "Anonymous";

        state.startSession(name);
        showSessionStart = false;

        // Reinitialize with panels
        this.clearWidgets();
        initPanels();
    }

    private void resumeSession() {
        state.resumeSession();
        showSessionStart = false;

        this.clearWidgets();
        initPanels();
    }

    // === MINIMIZATION ===

    private void minimizeToHud() {
        state.setMinimized(true);
        state.saveState();

        if (state.hasActiveTest()) {
            ActiveTestHudOverlay.setActiveTest(state.getCurrentTest());
        }
        ActiveTestHudOverlay.setEnabled(true);

        this.onClose();
    }

    /**
     * Called when the user presses K while the hub is minimized.
     */
    public static void restoreFromHud() {
        TestingHubState.INSTANCE.setMinimized(false);
        ActiveTestHudOverlay.setEnabled(false);
        ActionRegistry.invoke(ActionIds.UI_TESTING_HUB_OPEN,
            ClientActionContexts.forClient(ActionOrigin.UI));
    }

    // === UTILITY ===

    private void cycleFocus(int direction) {
        PanelFocus[] values = PanelFocus.values();
        int current = currentFocus.ordinal();
        int next = (current + direction + values.length) % values.length;
        currentFocus = values[next];
    }

    private int calculateHubWidth() {
        int preferred = (int)(width * 0.88);
        return Math.max(MIN_HUB_WIDTH, Math.min(MAX_HUB_WIDTH, preferred));
    }

    private int calculateHubHeight() {
        int preferred = (int)(height * 0.85);
        return Math.max(MIN_HUB_HEIGHT, Math.min(MAX_HUB_HEIGHT, preferred));
    }

    private void showNotification(String message) {
        // Use Minecraft's action bar for feedback
        @Nullable Minecraft mc = this.minecraft;
        if (mc == null) return;
        var player = mc.player;
        if (player == null) return;
        String safeMessage = Objects.requireNonNullElse(message, "");
        Component messageComponent = Objects.requireNonNull(Component.literal(
            Objects.requireNonNull(safeMessage, "safeMessage")), "messageComponent");
        player.displayClientMessage(messageComponent, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
