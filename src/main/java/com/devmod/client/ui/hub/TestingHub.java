package com.devmod.client.ui.hub;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.telemetry.UiTelemetry;
import com.devmod.client.testing.ActiveTestHudOverlay;
import com.devmod.client.testing.TestingSession;
import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.testing.TestCase;
import com.devmod.util.I18n;
@OnlyIn(Dist.CLIENT)
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
    @Nullable
    private CategoryPanel categoryPanel;
    @Nullable
    private TestDetailPanel detailPanel;
    @Nullable
    private QuickToolsPanel toolsPanel;
    @Nullable
    private ProgressFooter footer;

    // === SESSION INPUT ===
    @Nullable
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
    private static final List<PanelFocus> FOCUS_ORDER = List.of(PanelFocus.values());
    private PanelFocus currentFocus = PanelFocus.CATEGORIES;

    // === CACHED DIMENSIONS ===
    private int hubX, hubY, hubWidth, hubHeight;

    public TestingHub() {
        super(I18n.translate("devmod.testing.testing_hub"));
        this.state = TestingHubState.INSTANCE;
    }

    @Override
    protected void init() {
        // Track screen open for telemetry
        UiTelemetry.screenOpened("testing", "testing_hub");

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

    @Override
    public void onClose() {
        // Track screen close for telemetry
        UiTelemetry.screenClosed("testing", "testing_hub");
        super.onClose();
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
        nameField.setBordered(false);
        nameField.setTextColor(DesignTokens.Text.PRIMARY());
        nameField.setTextColorUneditable(DesignTokens.Text.MUTED());
        testerNameField = nameField;
        this.addRenderableWidget(nameField);
    }

    private void initPanels() {
        @Nonnull Font uiFont = Objects.requireNonNull(font, "font");
        // Calculate panel widths with scaling
        int scaledPanelGap = UIScaleManager.scale(PANEL_GAP);
        int categoryWidth = Math.max(UIScaleManager.scale(180), (int)(hubWidth * 0.22f));
        int toolsWidth = Math.max(UIScaleManager.scale(170), (int)(hubWidth * 0.25f));
        int detailWidth = hubWidth - categoryWidth - toolsWidth - scaledPanelGap * 2;

        int scaledHeaderHeight = UIScaleManager.scale(HEADER_HEIGHT);
        int scaledFooterHeight = UIScaleManager.scale(FOOTER_HEIGHT);
        int contentY = hubY + scaledHeaderHeight;
        int contentHeight = hubHeight - scaledHeaderHeight - scaledFooterHeight;

        // Initialize panels
        categoryPanel = new CategoryPanel(
            hubX, contentY, categoryWidth, contentHeight,
            uiFont, state,
            this::onCategorySelected,
            this::onTestSelected
        );

        detailPanel = new TestDetailPanel(
            hubX + categoryWidth + scaledPanelGap, contentY,
            detailWidth, contentHeight,
            uiFont,
            this::onVerdictGiven
        );

        toolsPanel = new QuickToolsPanel(
            hubX + categoryWidth + detailWidth + scaledPanelGap * 2, contentY,
            toolsWidth, contentHeight,
            uiFont, state,
            this::onToolToggled,
            this::onEditorOpened
        );

        footer = new ProgressFooter(
            hubX, hubY + hubHeight - scaledFooterHeight,
            hubWidth, scaledFooterHeight,
            uiFont,
            this::onSaveReport,
            this::onMinimize
        );

        // Load previous state
        if (state.hasActiveTest()) {
            TestCase currentTest = state.getCurrentTest();
            TestDetailPanel detail = detailPanel;
            if (currentTest != null && detail != null) {
                detail.setTest(currentTest);
            }
        }
    }

    // === RENDERING ===

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();
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
        // Central panel for session start with scaling
        int panelWidth = UIScaleManager.scale(350);
        int panelHeight = UIScaleManager.scale(200);
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;

        AxiomRenderer.drawPanel(graphics, uiFont, panelX, panelY, panelWidth, panelHeight, "Start Testing Session");

        int contentX = panelX + UIScaleManager.scale(DesignTokens.Spacing.PANEL_PADDING);
        int contentY = panelY + UIScaleManager.scale(DesignTokens.Spacing.HEADER_HEIGHT + DesignTokens.Spacing.PANEL_PADDING + 10);

        // Instructions
        UIScaleManager.drawScaledString(graphics, uiFont, "Welcome to DevMod QA Testing!", contentX, contentY, DesignTokens.Text.PRIMARY(), false);
        contentY += UIScaleManager.scale(16);

        UIScaleManager.drawScaledString(graphics, uiFont, "Enter your name to start tracking tests.", contentX, contentY, DesignTokens.Text.SECONDARY(), false);
        contentY += UIScaleManager.scale(30);

        // Name label
        UIScaleManager.drawScaledString(graphics, uiFont, "Tester Name:", contentX, contentY + UIScaleManager.scale(6), DesignTokens.Text.SECONDARY(), false);
        contentY += UIScaleManager.scale(40);

        if (testerNameField != null) {
            AxiomRenderer.drawInputBackground(graphics, testerNameField.getX(), testerNameField.getY(),
                testerNameField.getWidth(), testerNameField.getHeight(), testerNameField.isFocused());
        }

        // Buttons
        int buttonWidth = UIScaleManager.scale(120);
        int buttonGap = UIScaleManager.scale(20);
        int buttonsX = panelX + (panelWidth - buttonWidth * 2 - buttonGap) / 2;

        // Start New
        startButton
            .style(EditorButton.Style.PRIMARY)
            .onClick(this::startSession);
        startButton.render(graphics, buttonsX, contentY, buttonWidth, DesignTokens.Size.BUTTON_HEIGHT, mouseX, mouseY);

        // Resume (if available)
        if (TestingSession.INSTANCE.hasExistingSession()) {
            int resumeX = buttonsX + buttonWidth + buttonGap;
            resumeButton
                .style(EditorButton.Style.PRIMARY)
                .onClick(this::resumeSession);
            resumeButton.render(graphics, resumeX, contentY, buttonWidth, DesignTokens.Size.BUTTON_HEIGHT, mouseX, mouseY);
        }

        // Hint
        int hintY = panelY + panelHeight - UIScaleManager.scale(20);
        AxiomRenderer.drawHint(graphics, uiFont, panelX + UIScaleManager.scale(10), hintY, "Press Enter to start, ESC to close");
    }

    private void renderHub(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Header
        renderHeader(graphics, mouseX, mouseY);

        // Panels with focus indicator
        CategoryPanel catPanel = categoryPanel;
        if (catPanel != null) {
            catPanel.render(graphics, mouseX, mouseY, partialTick);
            if (currentFocus == PanelFocus.CATEGORIES) {
                renderFocusIndicator(graphics, catPanel.getX(), catPanel.getY(),
                    catPanel.getWidth(), catPanel.getHeight());
            }
        }
        TestDetailPanel detPanel = detailPanel;
        if (detPanel != null) {
            detPanel.render(graphics, mouseX, mouseY, partialTick);
            if (currentFocus == PanelFocus.DETAILS) {
                renderFocusIndicator(graphics, detPanel.getX(), detPanel.getY(),
                    detPanel.getWidth(), detPanel.getHeight());
            }
        }
        QuickToolsPanel tPanel = toolsPanel;
        if (tPanel != null) {
            tPanel.render(graphics, mouseX, mouseY, partialTick);
            if (currentFocus == PanelFocus.TOOLS) {
                renderFocusIndicator(graphics, tPanel.getX(), tPanel.getY(),
                    tPanel.getWidth(), tPanel.getHeight());
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
        int color = DesignTokens.Border.ACCENT();
        // Draw 2px thick border on top of panel
        graphics.fill(x - 1, y - 1, x + width + 1, y, color);           // Top
        graphics.fill(x - 1, y + height, x + width + 1, y + height + 1, color); // Bottom
        graphics.fill(x - 1, y, x, y + height, color);                   // Left
        graphics.fill(x + width, y, x + width + 1, y + height, color);   // Right
    }

    private void renderHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        @Nonnull Font uiFont = Objects.requireNonNull(font, "font");
        int scaledHeaderHeight = UIScaleManager.scale(HEADER_HEIGHT);

        // Background header
        graphics.fill(hubX, hubY, hubX + hubWidth, hubY + scaledHeaderHeight, DesignTokens.Background.HEADER());

        // Bottom border
        graphics.fill(hubX, hubY + scaledHeaderHeight - 1, hubX + hubWidth, hubY + scaledHeaderHeight, DesignTokens.Border.DEFAULT());

        // Title
        UIScaleManager.drawScaledString(graphics, uiFont, "DEVMOD TESTING HUB", hubX + UIScaleManager.scale(12), hubY + UIScaleManager.scale(10), DesignTokens.Text.TITLE(), false);

        // Session info
        String tester = "Tester: " + TestingSession.INSTANCE.getTesterName();
        int testerWidth = UIScaleManager.getScaledStringWidth(uiFont, tester);
        UIScaleManager.drawScaledString(graphics, uiFont, tester, hubX + hubWidth - testerWidth - UIScaleManager.scale(80), hubY + UIScaleManager.scale(10), DesignTokens.Text.MUTED(), false);

        int buttonHeight = EditorButton.Size.SMALL.height();
        int buttonY = hubY + (scaledHeaderHeight - buttonHeight) / 2;

        // Close button [X]
        int scaledButtonWidth = UIScaleManager.scale(HEADER_BUTTON_WIDTH);
        int scaledButtonGap = UIScaleManager.scale(HEADER_BUTTON_GAP);
        int closeX = hubX + hubWidth - scaledButtonWidth - scaledButtonGap;
        headerCloseButton.render(graphics, closeX, buttonY, scaledButtonWidth, buttonHeight, mouseX, mouseY);

        // Minimize button [-]
        int minX = closeX - scaledButtonWidth - scaledButtonGap;
        headerMinimizeButton.render(graphics, minX, buttonY, scaledButtonWidth, buttonHeight, mouseX, mouseY);
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
            TestCase currentTest = state.getCurrentTest();
            if (currentTest == null) {
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_1) {
                onVerdictGiven(currentTest, Verdict.PASS);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_2) {
                onVerdictGiven(currentTest, Verdict.FAIL);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_3) {
                onVerdictGiven(currentTest, Verdict.SKIP);
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

        // Panels - capture in local variables to satisfy null checker
        CategoryPanel catPanel = categoryPanel;
        if (catPanel != null && catPanel.isMouseOver(mx, my)) {
            currentFocus = PanelFocus.CATEGORIES;
            return catPanel.mouseClicked(mouseX, mouseY, button);
        }
        TestDetailPanel detPanel = detailPanel;
        if (detPanel != null && detPanel.isMouseOver(mx, my)) {
            currentFocus = PanelFocus.DETAILS;
            return detPanel.mouseClicked(mouseX, mouseY, button);
        }
        QuickToolsPanel tPanel = toolsPanel;
        if (tPanel != null && tPanel.isMouseOver(mx, my)) {
            currentFocus = PanelFocus.TOOLS;
            return tPanel.mouseClicked(mouseX, mouseY, button);
        }
        ProgressFooter foot = footer;
        if (foot != null && foot.isMouseOver(mx, my)) {
            return foot.mouseClicked(mouseX, mouseY, button);
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

        CategoryPanel catPanel = categoryPanel;
        if (catPanel != null && catPanel.isMouseOver(mx, my)) {
            if (catPanel.mouseReleased(mouseX, mouseY, button)) return true;
        }
        TestDetailPanel detPanel = detailPanel;
        if (detPanel != null && detPanel.isMouseOver(mx, my)) {
            if (detPanel.mouseReleased(mouseX, mouseY, button)) return true;
        }
        QuickToolsPanel tPanel = toolsPanel;
        if (tPanel != null && tPanel.isMouseOver(mx, my)) {
            if (tPanel.mouseReleased(mouseX, mouseY, button)) return true;
        }
        ProgressFooter foot = footer;
        if (foot != null && foot.isMouseOver(mx, my)) {
            if (foot.mouseReleased(mouseX, mouseY, button)) return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int mx = (int) mouseX;
        int my = (int) mouseY;

        CategoryPanel catPanel = categoryPanel;
        if (catPanel != null && catPanel.isMouseOver(mx, my)) {
            return catPanel.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        TestDetailPanel detPanel = detailPanel;
        if (detPanel != null && detPanel.isMouseOver(mx, my)) {
            return detPanel.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        QuickToolsPanel tPanel = toolsPanel;
        if (tPanel != null && tPanel.isMouseOver(mx, my)) {
            return tPanel.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // === CALLBACKS ===

    private void onCategorySelected(String category) {
        state.setSelectedCategory(category);
        TestDetailPanel detPanel = detailPanel;
        if (detPanel != null) {
            detPanel.clearTest();
        }
    }

    private void onTestSelected(TestCase test) {
        state.setCurrentTest(test);
        TestDetailPanel detPanel = detailPanel;
        if (detPanel != null) {
            detPanel.setTest(test);
        }
        QuickToolsPanel tPanel = toolsPanel;
        if (tPanel != null) {
            Set<ToolType> required = TestingHubState.inferRequiredTools(test);
            tPanel.highlightRequired(required);
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

        // Capture nullable fields in local variables
        ProgressFooter foot = footer;
        CategoryPanel catPanel = categoryPanel;
        TestDetailPanel detPanel = detailPanel;
        QuickToolsPanel tPanel = toolsPanel;

        // Refresh UI
        if (foot != null) foot.refresh();
        if (catPanel != null) catPanel.refresh();

        // Advance to next test
        state.advanceToNextTest();
        if (state.hasActiveTest()) {
            if (detPanel != null) {
                TestCase currentTest = state.getCurrentTest();
                if (currentTest != null) {
                    detPanel.setTest(currentTest);
                }
            }
        } else {
            if (detPanel != null) {
                detPanel.showCompletionMessage();
            }
            if (tPanel != null) {
                tPanel.clearHighlights();
            }
        }
    }

    private void onToolToggled(ToolType tool, @Nullable Boolean enabled) {
        boolean isEnabled = Boolean.TRUE.equals(enabled);
        state.setToolEnabled(tool, isEnabled);
        // Tool status is read fresh each render via tool.isEnabled() in renderRequiredTools()
    }

    private void onEditorOpened(EditorType editor) {
        state.saveState();

        switch (editor) {
            case WEAPON -> {
                ActionRegistry.invoke(ActionIds.UI_ITEM_EDITOR_OPEN_WEAPON,
                    ClientActionContexts.forClient(ActionOrigin.UI));
            }
            case MOB_CONFIG -> {
                boolean opened = ActionRegistry.invoke(ActionIds.UI_MOB_CONFIG_OPEN,
                    ClientActionContexts.forClient(ActionOrigin.UI));
                if (!opened) {
                    Minecraft mc = this.minecraft;
                    boolean hasMobTarget = false;
                    if (mc != null && mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult entityHit) {
                        hasMobTarget = entityHit.getEntity() instanceof net.minecraft.world.entity.Mob;
                    }
                    if (!hasMobTarget) {
                        showNotification("Right-click a mob while holding the config tool");
                        minimizeToHud();
                    }
                }
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
            TestCase currentTest = state.getCurrentTest();
            if (currentTest != null) {
                ActiveTestHudOverlay.setActiveTest(currentTest);
            }
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
        int current = FOCUS_ORDER.indexOf(currentFocus);
        if (current < 0) {
            current = 0;
        }
        int next = (current + direction + FOCUS_ORDER.size()) % FOCUS_ORDER.size();
        currentFocus = FOCUS_ORDER.get(next);
    }

    private int calculateHubWidth() {
        int preferred = (int)(width * 0.88);
        int scaledMin = UIScaleManager.scale(MIN_HUB_WIDTH);
        int scaledMax = UIScaleManager.scale(MAX_HUB_WIDTH);
        return Math.max(scaledMin, Math.min(scaledMax, preferred));
    }

    private int calculateHubHeight() {
        int preferred = (int)(height * 0.85);
        int scaledMin = UIScaleManager.scale(MIN_HUB_HEIGHT);
        int scaledMax = UIScaleManager.scale(MAX_HUB_HEIGHT);
        return Math.max(scaledMin, Math.min(scaledMax, preferred));
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
