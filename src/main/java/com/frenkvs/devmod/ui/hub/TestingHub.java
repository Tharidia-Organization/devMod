package com.frenkvs.devmod.ui.hub;

import com.frenkvs.devmod.WeaponEditorScreen;
import com.frenkvs.devmod.testing.ActiveTestHudOverlay;
import com.frenkvs.devmod.testing.TestCase;
import com.frenkvs.devmod.testing.TestingSession;
import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.UIConstants;
import com.frenkvs.devmod.util.I18n;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * Hub centrale unificato per testing.
 * Combina: selezione test, quick tools, progress tracking.
 *
 * Layout a 3 pannelli:
 * - Sinistra: CategoryPanel (categorie + test list + search)
 * - Centro: TestDetailPanel (dettagli test selezionato)
 * - Destra: QuickToolsPanel (toggle overlay + editor shortcuts)
 * - Footer: ProgressFooter (barra progresso + azioni)
 */
@SuppressWarnings("null")
public class TestingHub extends Screen {

    // === LAYOUT CONSTANTS ===
    private static final int HEADER_HEIGHT = 28;
    private static final int FOOTER_HEIGHT = 44;
    private static final int PANEL_GAP = 2;
    private static final int MIN_HUB_WIDTH = 700;
    private static final int MAX_HUB_WIDTH = 900;
    private static final int MIN_HUB_HEIGHT = 450;
    private static final int MAX_HUB_HEIGHT = 600;

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
        // Calcola dimensioni responsive
        hubWidth = calculateHubWidth();
        hubHeight = calculateHubHeight();
        hubX = (width - hubWidth) / 2;
        hubY = (height - hubHeight) / 2;

        // Verifica se mostrare session start
        showSessionStart = !state.isSessionActive() && !state.hasExistingSession();

        if (showSessionStart) {
            initSessionStartUI();
        } else {
            // Se c'è sessione esistente ma non attiva, riprendi
            if (!state.isSessionActive() && state.hasExistingSession()) {
                state.resumeSession();
            }
            initPanels();
        }
    }

    private void initSessionStartUI() {
        // Campo nome tester centrato
        int fieldWidth = 200;
        int fieldX = hubX + (hubWidth - fieldWidth) / 2;
        int fieldY = hubY + hubHeight / 2 - 30;

        testerNameField = new EditBox(font, fieldX, fieldY, fieldWidth, 20, I18n.translate("devmod.testing.tester_name"));
        testerNameField.setHint(I18n.translate("devmod.testing.enter_name"));
        testerNameField.setMaxLength(32);
        testerNameField.setValue(TestingSession.INSTANCE.getTesterName());
        this.addRenderableWidget(testerNameField);
    }

    private void initPanels() {
        // Calcola larghezze pannelli
        int categoryWidth = Math.max(180, (int)(hubWidth * 0.22f));
        int toolsWidth = Math.max(170, (int)(hubWidth * 0.25f));
        int detailWidth = hubWidth - categoryWidth - toolsWidth - PANEL_GAP * 2;

        int contentY = hubY + HEADER_HEIGHT;
        int contentHeight = hubHeight - HEADER_HEIGHT - FOOTER_HEIGHT;

        // Inizializza pannelli
        categoryPanel = new CategoryPanel(
            hubX, contentY, categoryWidth, contentHeight,
            font, state,
            this::onCategorySelected,
            this::onTestSelected
        );

        detailPanel = new TestDetailPanel(
            hubX + categoryWidth + PANEL_GAP, contentY,
            detailWidth, contentHeight,
            font, state,
            this::onVerdictGiven
        );

        toolsPanel = new QuickToolsPanel(
            hubX + categoryWidth + detailWidth + PANEL_GAP * 2, contentY,
            toolsWidth, contentHeight,
            font, state,
            this::onToolToggled,
            this::onEditorOpened
        );

        footer = new ProgressFooter(
            hubX, hubY + hubHeight - FOOTER_HEIGHT,
            hubWidth, FOOTER_HEIGHT,
            font,
            this::onSaveReport,
            this::onMinimize
        );

        // Carica stato precedente
        if (state.hasActiveTest()) {
            detailPanel.setTest(state.getCurrentTest());
        }
    }

    // === RENDERING ===

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Background scuro
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
        // Pannello centrale per avvio sessione
        int panelWidth = 350;
        int panelHeight = 200;
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;

        AxiomRenderer.drawPanel(graphics, font, panelX, panelY, panelWidth, panelHeight, "Start Testing Session");

        int contentX = panelX + UIConstants.Spacing.PANEL_PADDING;
        int contentY = panelY + UIConstants.Spacing.HEADER_HEIGHT + UIConstants.Spacing.PANEL_PADDING + 10;

        // Istruzioni
        graphics.drawString(font, "Welcome to DevMod QA Testing!", contentX, contentY, UIConstants.Text.PRIMARY, false);
        contentY += 16;

        graphics.drawString(font, "Enter your name to start tracking tests.", contentX, contentY, UIConstants.Text.SECONDARY, false);
        contentY += 30;

        // Label per nome
        graphics.drawString(font, "Tester Name:", contentX, contentY + 6, UIConstants.Text.SECONDARY, false);
        contentY += 40;

        // Bottoni
        int buttonWidth = 120;
        int buttonGap = 20;
        int buttonsX = panelX + (panelWidth - buttonWidth * 2 - buttonGap) / 2;

        // Start New
        boolean startHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, buttonsX, contentY, buttonWidth, UIConstants.Size.BUTTON_HEIGHT);
        AxiomRenderer.drawButton(graphics, font, buttonsX, contentY, buttonWidth, UIConstants.Size.BUTTON_HEIGHT, "Start New", startHovered, false);

        // Resume (se disponibile)
        if (TestingSession.INSTANCE.hasExistingSession()) {
            int resumeX = buttonsX + buttonWidth + buttonGap;
            boolean resumeHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, resumeX, contentY, buttonWidth, UIConstants.Size.BUTTON_HEIGHT);
            AxiomRenderer.drawButton(graphics, font, resumeX, contentY, buttonWidth, UIConstants.Size.BUTTON_HEIGHT, "Resume", resumeHovered, false);
        }

        // Hint
        int hintY = panelY + panelHeight - 20;
        AxiomRenderer.drawHint(graphics, font, panelX + 10, hintY, "Press Enter to start, ESC to close");
    }

    private void renderHub(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Header
        renderHeader(graphics, mouseX, mouseY);

        // Pannelli con focus indicator
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
        int color = UIConstants.Border.ACCENT;
        // Draw 2px thick border on top of panel
        graphics.fill(x - 1, y - 1, x + width + 1, y, color);           // Top
        graphics.fill(x - 1, y + height, x + width + 1, y + height + 1, color); // Bottom
        graphics.fill(x - 1, y, x, y + height, color);                   // Left
        graphics.fill(x + width, y, x + width + 1, y + height, color);   // Right
    }

    private void renderHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        // Background header
        graphics.fill(hubX, hubY, hubX + hubWidth, hubY + HEADER_HEIGHT, UIConstants.Background.HEADER);

        // Bordo inferiore
        graphics.fill(hubX, hubY + HEADER_HEIGHT - 1, hubX + hubWidth, hubY + HEADER_HEIGHT, UIConstants.Border.DEFAULT);

        // Titolo
        graphics.drawString(font, "DEVMOD TESTING HUB", hubX + 12, hubY + 10, UIConstants.Text.TITLE, false);

        // Session info
        String tester = "Tester: " + TestingSession.INSTANCE.getTesterName();
        int testerWidth = font.width(tester);
        graphics.drawString(font, tester, hubX + hubWidth - testerWidth - 80, hubY + 10, UIConstants.Text.MUTED, false);

        // Close button [X]
        int closeX = hubX + hubWidth - 22;
        boolean closeHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, closeX, hubY + 6, 16, 16);
        int closeColor = closeHovered ? UIConstants.Accent.RED : UIConstants.Text.MUTED;
        graphics.drawString(font, "X", closeX + 4, hubY + 10, closeColor, false);

        // Minimize button [-]
        int minX = closeX - 20;
        boolean minHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, minX, hubY + 6, 16, 16);
        int minColor = minHovered ? UIConstants.Text.ACCENT : UIConstants.Text.MUTED;
        graphics.drawString(font, "-", minX + 4, hubY + 10, minColor, false);
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

        // ESC: Minimizza invece di chiudere
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            minimizeToHud();
            return true;
        }

        // Tab: Cicla focus tra pannelli
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            cycleFocus(Screen.hasShiftDown() ? -1 : 1);
            return true;
        }

        // 1/2/3: Verdetto rapido se c'è test attivo
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

        // Delega ai pannelli
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

        // Header buttons
        if (my >= hubY && my < hubY + HEADER_HEIGHT) {
            // Close button
            int closeX = hubX + hubWidth - 22;
            if (AxiomRenderer.isMouseOver(mx, my, closeX, hubY + 6, 16, 16)) {
                this.onClose();
                return true;
            }
            // Minimize button
            int minX = closeX - 20;
            if (AxiomRenderer.isMouseOver(mx, my, minX, hubY + 6, 16, 16)) {
                minimizeToHud();
                return true;
            }
        }

        // Pannelli
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

        int panelWidth = 350;
        int panelHeight = 200;
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;

        int contentY = panelY + UIConstants.Spacing.HEADER_HEIGHT + UIConstants.Spacing.PANEL_PADDING + 10 + 16 + 30 + 40;

        int buttonWidth = 120;
        int buttonGap = 20;
        int buttonsX = panelX + (panelWidth - buttonWidth * 2 - buttonGap) / 2;

        // Start New button
        if (AxiomRenderer.isMouseOver(mx, my, buttonsX, contentY, buttonWidth, UIConstants.Size.BUTTON_HEIGHT)) {
            startSession();
            return true;
        }

        // Resume button
        if (TestingSession.INSTANCE.hasExistingSession()) {
            int resumeX = buttonsX + buttonWidth + buttonGap;
            if (AxiomRenderer.isMouseOver(mx, my, resumeX, contentY, buttonWidth, UIConstants.Size.BUTTON_HEIGHT)) {
                resumeSession();
                return true;
            }
        }

        return super.mouseClicked(mx, my, button);
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

        // Avanza al prossimo test
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

    private void onToolToggled(ToolType tool, boolean enabled) {
        state.setToolEnabled(tool, enabled);
        if (detailPanel != null) {
            detailPanel.updateToolStatus(tool, enabled);
        }
    }

    private void onEditorOpened(EditorType editor) {
        state.saveState();

        switch (editor) {
            case WEAPON -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && !mc.player.getMainHandItem().isEmpty()) {
                    mc.setScreen(new WeaponEditorScreen());
                } else {
                    // Mostra messaggio
                    showNotification("Hold an item in your main hand first!");
                }
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

        // Reinizializza con i pannelli
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
     * Chiamato quando l'utente preme K mentre l'hub è minimizzato.
     */
    public static void restoreFromHud() {
        TestingHubState.INSTANCE.setMinimized(false);
        ActiveTestHudOverlay.setEnabled(false);
        Minecraft.getInstance().setScreen(new TestingHub());
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
        // Usa action bar di Minecraft per feedback
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(message), true);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
