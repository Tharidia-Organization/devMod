package com.frenkvs.devmod.ui.wizard;

import com.frenkvs.devmod.endurance.EnduranceQuestRegistry;
import com.frenkvs.devmod.testing.IntegratedTestSession;
import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.UIConstants;
import com.frenkvs.devmod.util.I18n;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Quick Test Wizard - Guided workflow to start integrated testing.
 *
 * Connects:
 * - Mob configuration (what to test)
 * - Endurance quest system (controlled test environment)
 * - Telemetry (data collection)
 * - Overlays (real-time feedback)
 *
 * Steps:
 * 1. Select test type (Combat, Boss, Dungeon, Custom)
 * 2. Configure target (mob type, wave count, difficulty)
 * 3. Enable overlays (auto-suggest based on test type)
 * 4. Start test (launches integrated session)
 */
@SuppressWarnings("null") // Minecraft APIs lack null annotations
public class QuickTestWizard extends Screen {

    // Layout
    private static final int PANEL_WIDTH = 500;
    private static final int PANEL_HEIGHT = 380;

    // Wizard state
    private int currentStep = 0;
    private static final int TOTAL_STEPS = 4;

    // Step 1: Test Type
    private TestType selectedTestType = TestType.COMBAT;

    // Step 2: Configuration
    private ResourceLocation selectedMob = null;
    private int waveCount = 5;
    private boolean endlessMode = false;
    private int arenaSize = 64;

    // Step 3: Overlays
    private boolean enableDebugOverlay = true;
    private boolean enableBossPhase = false;
    private boolean enableTelemetryHud = true;
    private boolean enableEntityDensity = false;
    private boolean enableHeatmaps = false;

    // Available mobs
    private List<EnduranceQuestRegistry.MobQuestConfig> availableMobs = new ArrayList<>();
    private int selectedMobIndex = 0;
    private int mobListScroll = 0;

    public QuickTestWizard() {
        super(I18n.screenTitle("quick_test_wizard"));
        loadAvailableMobs();
    }

    /**
     * Returns the font, guaranteed non-null after init().
     */
    @Nonnull
    private Font getFont() {
        return Objects.requireNonNull(this.font);
    }

    private void loadAvailableMobs() {
        availableMobs.clear();
        try {
            availableMobs.addAll(EnduranceQuestRegistry.INSTANCE.getAllMobConfigs());
        } catch (Exception e) {
            // Registry not initialized - will show empty list
        }
        if (!availableMobs.isEmpty()) {
            selectedMob = availableMobs.get(0).mobId;
        }
    }

    @Override
    protected void init() {
        // No widgets - fully custom rendering
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Background
        AxiomRenderer.drawScreenBackground(graphics, width, height);

        // Responsive panel dimensions - ensure fit on small screens
        int panelWidth = Math.min(PANEL_WIDTH, width - 20);
        int panelHeight = Math.min(PANEL_HEIGHT, height - 20);
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;

        // Main panel
        drawMainPanel(graphics, panelX, panelY);

        // Step indicators
        drawStepIndicators(graphics, panelX, panelY);

        // Current step content
        int contentY = panelY + 60;
        switch (currentStep) {
            case 0 -> drawStep1_TestType(graphics, panelX, contentY, mouseX, mouseY);
            case 1 -> drawStep2_Configuration(graphics, panelX, contentY, mouseX, mouseY);
            case 2 -> drawStep3_Overlays(graphics, panelX, contentY, mouseX, mouseY);
            case 3 -> drawStep4_Summary(graphics, panelX, contentY, mouseX, mouseY);
        }

        // Navigation buttons
        drawNavigationButtons(graphics, panelX, panelY + PANEL_HEIGHT - 40, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawMainPanel(GuiGraphics graphics, int x, int y) {
        // Panel background
        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, UIConstants.Background.PANEL_SOLID);

        // Header gradient
        for (int i = 0; i < 40; i++) {
            float t = i / 40f;
            int color = UIConstants.lerp(0xFF2A3A5E, UIConstants.Background.HEADER, t);
            graphics.fill(x, y + i, x + PANEL_WIDTH, y + i + 1, color);
        }

        // Border
        AxiomRenderer.drawBorder(graphics, x, y, PANEL_WIDTH, PANEL_HEIGHT, UIConstants.Border.ACCENT);

        // Title
        String title = "⚡ Quick Test Wizard";
        graphics.drawString(getFont(), title, x + 15, y + 12, UIConstants.Text.ACCENT, false);

        // Subtitle
        String subtitle = getStepTitle();
        graphics.drawString(getFont(), subtitle, x + 15, y + 26, UIConstants.Text.SECONDARY, false);
    }

    private String getStepTitle() {
        return switch (currentStep) {
            case 0 -> "Step 1: Choose Test Type";
            case 1 -> "Step 2: Configure Test Target";
            case 2 -> "Step 3: Select Overlays";
            case 3 -> "Step 4: Review & Start";
            default -> "";
        };
    }

    private void drawStepIndicators(GuiGraphics graphics, int panelX, int panelY) {
        int indicatorY = panelY + 45;
        int startX = panelX + 100;
        int stepWidth = 75;

        for (int i = 0; i < TOTAL_STEPS; i++) {
            int cx = startX + i * stepWidth;
            boolean completed = i < currentStep;
            boolean current = i == currentStep;

            // Circle
            int circleColor = completed ? UIConstants.Accent.GREEN :
                              (current ? UIConstants.Accent.BLUE : UIConstants.Border.MUTED);
            graphics.fill(cx - 8, indicatorY - 8, cx + 8, indicatorY + 8, circleColor);

            // Number
            String num = String.valueOf(i + 1);
            int textColor = (completed || current) ? UIConstants.Text.WHITE : UIConstants.Text.MUTED;
            graphics.drawString(getFont(), num, cx - 3, indicatorY - 4, textColor, false);

            // Connector line
            if (i < TOTAL_STEPS - 1) {
                int lineColor = completed ? UIConstants.Accent.GREEN : UIConstants.Border.MUTED;
                graphics.fill(cx + 12, indicatorY - 1, cx + stepWidth - 12, indicatorY + 1, lineColor);
            }
        }
    }

    // ===================== STEP 1: Test Type =====================
    private void drawStep1_TestType(GuiGraphics graphics, int panelX, int y, int mouseX, int mouseY) {
        int cardWidth = 110;
        int cardHeight = 80;
        int gap = 15;
        int startX = panelX + (PANEL_WIDTH - (cardWidth * 4 + gap * 3)) / 2;

        for (int i = 0; i < TestType.values().length; i++) {
            TestType type = TestType.values()[i];
            int cx = startX + i * (cardWidth + gap);
            boolean selected = type == selectedTestType;
            boolean hovered = AxiomRenderer.isMouseOver(mouseX, mouseY, cx, y, cardWidth, cardHeight);

            drawTestTypeCard(graphics, cx, y, cardWidth, cardHeight, type, selected, hovered);
        }

        // Description
        String desc = selectedTestType.getDescription();
        int descY = y + cardHeight + 20;
        graphics.drawString(getFont(), desc, panelX + 20, descY, UIConstants.Text.SECONDARY, false);

        // Auto-configuration hint
        String hint = "§7Auto-config: " + selectedTestType.getAutoConfigHint();
        graphics.drawString(getFont(), hint, panelX + 20, descY + 15, UIConstants.Text.MUTED, false);
    }

    private void drawTestTypeCard(GuiGraphics graphics, int x, int y, int w, int h,
                                   TestType type, boolean selected, boolean hovered) {
        // Background
        int bgColor = selected ? UIConstants.setAlpha(type.getColor(), 60) :
                      (hovered ? UIConstants.Background.HOVER : UIConstants.Background.INPUT);
        graphics.fill(x, y, x + w, y + h, bgColor);

        // Border
        int borderColor = selected ? type.getColor() : UIConstants.Border.MUTED;
        AxiomRenderer.drawBorder(graphics, x, y, w, h, borderColor);

        // Icon
        graphics.drawString(getFont(), type.getIcon(), x + w/2 - 6, y + 15, type.getColor(), false);

        // Name
        int textW = getFont().width(type.getDisplayName());
        graphics.drawString(getFont(), type.getDisplayName(), x + (w - textW)/2, y + 40,
            selected ? UIConstants.Text.WHITE : UIConstants.Text.PRIMARY, false);

        // Selected indicator
        if (selected) {
            graphics.fill(x, y + h - 3, x + w, y + h, type.getColor());
        }
    }

    // ===================== STEP 2: Configuration =====================
    private void drawStep2_Configuration(GuiGraphics graphics, int panelX, int y, int mouseX, int mouseY) {
        int leftCol = panelX + 20;
        int rightCol = panelX + 260;

        // Mob selection
        graphics.drawString(getFont(), "§lTarget Mob:", leftCol, y, UIConstants.Text.PRIMARY, false);

        // Mob list (scrollable)
        int listY = y + 15;
        int listH = 120;
        int visibleItems = 5;

        graphics.fill(leftCol, listY, leftCol + 200, listY + listH, UIConstants.Background.INPUT);
        AxiomRenderer.drawBorder(graphics, leftCol, listY, 200, listH, UIConstants.Border.MUTED);

        if (availableMobs.isEmpty()) {
            // Show empty state message
            graphics.drawString(getFont(), "No mobs found!", leftCol + 10, listY + 20, UIConstants.Accent.ORANGE, false);
            graphics.drawString(getFont(), "Enter a world first", leftCol + 10, listY + 35, UIConstants.Text.MUTED, false);
            graphics.drawString(getFont(), "to load mob registry.", leftCol + 10, listY + 48, UIConstants.Text.MUTED, false);
        } else {
            for (int i = 0; i < Math.min(visibleItems, availableMobs.size()); i++) {
                int idx = mobListScroll + i;
                if (idx >= availableMobs.size()) break;

                var mob = availableMobs.get(idx);
                int itemY = listY + 5 + i * 22;
                boolean selected = idx == selectedMobIndex;
                boolean hovered = AxiomRenderer.isMouseOver(mouseX, mouseY, leftCol + 2, itemY, 196, 20);

                if (selected) {
                    graphics.fill(leftCol + 2, itemY, leftCol + 198, itemY + 20, UIConstants.Background.ACTIVE);
                } else if (hovered) {
                    graphics.fill(leftCol + 2, itemY, leftCol + 198, itemY + 20, UIConstants.Background.HOVER);
                }

                String mobName = mob.displayName != null ? mob.displayName : mob.mobId.toString();
                if (mobName.length() > 25) mobName = mobName.substring(0, 22) + "...";
                graphics.drawString(getFont(), mobName, leftCol + 8, itemY + 6,
                    selected ? UIConstants.Text.ACCENT : UIConstants.Text.PRIMARY, false);
            }
        }

        // Right column: Wave settings
        graphics.drawString(getFont(), "§lWave Settings:", rightCol, y, UIConstants.Text.PRIMARY, false);

        int settingY = y + 20;

        // Wave count
        graphics.drawString(getFont(), "Waves: " + waveCount, rightCol, settingY, UIConstants.Text.SECONDARY, false);
        drawPlusMinus(graphics, rightCol + 100, settingY - 2, mouseX, mouseY, "waves");
        settingY += 25;

        // Endless mode
        drawCheckbox(graphics, rightCol, settingY, "Endless Mode", endlessMode, mouseX, mouseY, "endless");
        settingY += 25;

        // Arena size
        graphics.drawString(getFont(), "Arena: " + arenaSize + " blocks", rightCol, settingY, UIConstants.Text.SECONDARY, false);
        drawPlusMinus(graphics, rightCol + 120, settingY - 2, mouseX, mouseY, "arena");

        // Preview info
        int previewY = y + 150;
        graphics.fill(panelX + 20, previewY, panelX + PANEL_WIDTH - 20, previewY + 60, UIConstants.Background.INPUT);
        AxiomRenderer.drawBorder(graphics, panelX + 20, previewY, PANEL_WIDTH - 40, 60, UIConstants.Border.MUTED);

        graphics.drawString(getFont(), "§lTest Preview:", panelX + 30, previewY + 8, UIConstants.Text.PRIMARY, false);
        if (selectedMob != null) {
            String preview = String.format("Fight %d waves of %s in %dx%d arena",
                waveCount, selectedMob.getPath(), arenaSize, arenaSize);
            graphics.drawString(getFont(), preview, panelX + 30, previewY + 25, UIConstants.Text.SECONDARY, false);

            String duration = endlessMode ? "Endless (until death)" : "~" + (waveCount * 2) + " minutes estimated";
            graphics.drawString(getFont(), duration, panelX + 30, previewY + 40, UIConstants.Text.MUTED, false);
        }
    }

    private void drawPlusMinus(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, String id) {
        boolean minusHover = AxiomRenderer.isMouseOver(mouseX, mouseY, x, y, 16, 16);
        boolean plusHover = AxiomRenderer.isMouseOver(mouseX, mouseY, x + 24, y, 16, 16);

        graphics.fill(x, y, x + 16, y + 16, minusHover ? UIConstants.Background.HOVER : UIConstants.Background.INPUT);
        graphics.fill(x + 24, y, x + 40, y + 16, plusHover ? UIConstants.Background.HOVER : UIConstants.Background.INPUT);

        graphics.drawString(getFont(), "-", x + 5, y + 4, UIConstants.Text.PRIMARY, false);
        graphics.drawString(getFont(), "+", x + 29, y + 4, UIConstants.Text.PRIMARY, false);
    }

    private void drawCheckbox(GuiGraphics graphics, int x, int y, String label, boolean checked,
                               int mouseX, int mouseY, String id) {
        boolean hovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x, y, 150, 16);

        // Box
        graphics.fill(x, y, x + 14, y + 14, UIConstants.Background.INPUT);
        AxiomRenderer.drawBorder(graphics, x, y, 14, 14, hovered ? UIConstants.Border.ACCENT : UIConstants.Border.MUTED);

        if (checked) {
            graphics.fill(x + 3, y + 3, x + 11, y + 11, UIConstants.Accent.GREEN);
        }

        // Label
        graphics.drawString(getFont(), label, x + 20, y + 3, UIConstants.Text.SECONDARY, false);
    }

    // ===================== STEP 3: Overlays =====================
    private void drawStep3_Overlays(GuiGraphics graphics, int panelX, int y, int mouseX, int mouseY) {
        graphics.drawString(getFont(), "§lRecommended overlays for " + selectedTestType.getDisplayName() + ":",
            panelX + 20, y, UIConstants.Text.PRIMARY, false);

        int checkY = y + 25;
        int col1 = panelX + 30;
        int col2 = panelX + 260;

        // Column 1
        enableTelemetryHud = drawOverlayCheckbox(graphics, col1, checkY, "Telemetry Status HUD",
            enableTelemetryHud, true, mouseX, mouseY);
        checkY += 25;

        enableDebugOverlay = drawOverlayCheckbox(graphics, col1, checkY, "Debug Overlay (Body Parts)",
            enableDebugOverlay, selectedTestType == TestType.COMBAT, mouseX, mouseY);
        checkY += 25;

        enableBossPhase = drawOverlayCheckbox(graphics, col1, checkY, "Boss Phase Overlay",
            enableBossPhase, selectedTestType == TestType.BOSS, mouseX, mouseY);

        // Column 2
        checkY = y + 25;
        enableEntityDensity = drawOverlayCheckbox(graphics, col2, checkY, "Entity Density",
            enableEntityDensity, selectedTestType == TestType.DUNGEON, mouseX, mouseY);
        checkY += 25;

        enableHeatmaps = drawOverlayCheckbox(graphics, col2, checkY, "Record Heatmaps",
            enableHeatmaps, true, mouseX, mouseY);

        // Info box
        int infoY = y + 120;
        graphics.fill(panelX + 20, infoY, panelX + PANEL_WIDTH - 20, infoY + 70, 0x40FFAA00);
        AxiomRenderer.drawBorder(graphics, panelX + 20, infoY, PANEL_WIDTH - 40, 70, UIConstants.Accent.ORANGE);

        graphics.drawString(getFont(), "§6ℹ§r Overlays provide real-time feedback during testing:",
            panelX + 30, infoY + 10, UIConstants.Text.PRIMARY, false);
        graphics.drawString(getFont(), "• Telemetry HUD shows recording status and current room",
            panelX + 30, infoY + 25, UIConstants.Text.SECONDARY, false);
        graphics.drawString(getFont(), "• Debug overlay shows hitboxes and damage numbers",
            panelX + 30, infoY + 38, UIConstants.Text.SECONDARY, false);
        graphics.drawString(getFont(), "• Heatmaps record movement patterns for analysis",
            panelX + 30, infoY + 51, UIConstants.Text.SECONDARY, false);
    }

    private boolean drawOverlayCheckbox(GuiGraphics graphics, int x, int y, String label,
                                         boolean checked, boolean recommended, int mouseX, int mouseY) {
        boolean hovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x - 5, y - 2, 200, 20);

        // Box
        graphics.fill(x, y, x + 14, y + 14, UIConstants.Background.INPUT);
        int borderColor = recommended ? UIConstants.Accent.GOLD : UIConstants.Border.MUTED;
        AxiomRenderer.drawBorder(graphics, x, y, 14, 14, hovered ? UIConstants.Border.ACCENT : borderColor);

        if (checked) {
            graphics.fill(x + 3, y + 3, x + 11, y + 11, UIConstants.Accent.GREEN);
        }

        // Label with recommendation marker
        String displayLabel = recommended ? label + " §6★" : label;
        graphics.drawString(getFont(), displayLabel, x + 20, y + 3, UIConstants.Text.SECONDARY, false);

        return checked; // Return unchanged - click handling separate
    }

    // ===================== STEP 4: Summary =====================
    private void drawStep4_Summary(GuiGraphics graphics, int panelX, int y, int mouseX, int mouseY) {
        graphics.drawString(getFont(), "§l§aReady to Start Test!", panelX + 20, y, UIConstants.Accent.GREEN, false);

        int summaryY = y + 25;
        int leftCol = panelX + 30;
        int rightCol = panelX + 280;

        // Test type
        graphics.drawString(getFont(), "Test Type:", leftCol, summaryY, UIConstants.Text.MUTED, false);
        graphics.drawString(getFont(), selectedTestType.getDisplayName(), rightCol, summaryY, UIConstants.Text.PRIMARY, false);
        summaryY += 18;

        // Target
        graphics.drawString(getFont(), "Target Mob:", leftCol, summaryY, UIConstants.Text.MUTED, false);
        String mobName = selectedMob != null ? selectedMob.getPath() : "None";
        graphics.drawString(getFont(), mobName, rightCol, summaryY, UIConstants.Text.PRIMARY, false);
        summaryY += 18;

        // Waves
        graphics.drawString(getFont(), "Waves:", leftCol, summaryY, UIConstants.Text.MUTED, false);
        graphics.drawString(getFont(), endlessMode ? "Endless" : String.valueOf(waveCount), rightCol, summaryY, UIConstants.Text.PRIMARY, false);
        summaryY += 18;

        // Arena
        graphics.drawString(getFont(), "Arena Size:", leftCol, summaryY, UIConstants.Text.MUTED, false);
        graphics.drawString(getFont(), arenaSize + "x" + arenaSize + " blocks", rightCol, summaryY, UIConstants.Text.PRIMARY, false);
        summaryY += 18;

        // Overlays
        graphics.drawString(getFont(), "Active Overlays:", leftCol, summaryY, UIConstants.Text.MUTED, false);
        List<String> activeOverlays = new ArrayList<>();
        if (enableTelemetryHud) activeOverlays.add("Telemetry");
        if (enableDebugOverlay) activeOverlays.add("Debug");
        if (enableBossPhase) activeOverlays.add("Boss Phase");
        if (enableEntityDensity) activeOverlays.add("Density");
        if (enableHeatmaps) activeOverlays.add("Heatmaps");
        graphics.drawString(getFont(), String.join(", ", activeOverlays), rightCol, summaryY, UIConstants.Text.PRIMARY, false);

        // Big start button
        int btnX = panelX + (PANEL_WIDTH - 200) / 2;
        int btnY = y + 150;
        boolean btnHover = AxiomRenderer.isMouseOver(mouseX, mouseY, btnX, btnY, 200, 40);

        int btnBg = btnHover ? UIConstants.setAlpha(UIConstants.Accent.GREEN, 80) : UIConstants.Background.INPUT;
        graphics.fill(btnX, btnY, btnX + 200, btnY + 40, btnBg);
        graphics.fill(btnX, btnY, btnX + 4, btnY + 40, UIConstants.Accent.GREEN);
        AxiomRenderer.drawBorder(graphics, btnX, btnY, 200, 40, btnHover ? UIConstants.Accent.GREEN : UIConstants.Border.MUTED);

        String btnText = "▶ START TEST";
        int btnTextW = getFont().width(btnText);
        graphics.drawString(getFont(), btnText, btnX + (200 - btnTextW) / 2, btnY + 16,
            btnHover ? UIConstants.Accent.GREEN : UIConstants.Text.PRIMARY, false);
    }

    // ===================== Navigation =====================
    private void drawNavigationButtons(GuiGraphics graphics, int panelX, int y, int mouseX, int mouseY) {
        // Back button
        if (currentStep > 0) {
            boolean backHover = AxiomRenderer.isMouseOver(mouseX, mouseY, panelX + 20, y, 80, 25);
            graphics.fill(panelX + 20, y, panelX + 100, y + 25,
                backHover ? UIConstants.Background.HOVER : UIConstants.Background.INPUT);
            AxiomRenderer.drawBorder(graphics, panelX + 20, y, 80, 25, UIConstants.Border.MUTED);
            graphics.drawString(getFont(), "← Back", panelX + 35, y + 8, UIConstants.Text.PRIMARY, false);
        }

        // Next/Start button
        if (currentStep < TOTAL_STEPS - 1) {
            boolean nextHover = AxiomRenderer.isMouseOver(mouseX, mouseY, panelX + PANEL_WIDTH - 100, y, 80, 25);
            int nextBg = nextHover ? UIConstants.setAlpha(UIConstants.Accent.BLUE, 60) : UIConstants.Background.INPUT;
            graphics.fill(panelX + PANEL_WIDTH - 100, y, panelX + PANEL_WIDTH - 20, y + 25, nextBg);
            graphics.fill(panelX + PANEL_WIDTH - 100, y, panelX + PANEL_WIDTH - 97, y + 25, UIConstants.Accent.BLUE);
            AxiomRenderer.drawBorder(graphics, panelX + PANEL_WIDTH - 100, y, 80, 25,
                nextHover ? UIConstants.Accent.BLUE : UIConstants.Border.MUTED);
            graphics.drawString(getFont(), "Next →", panelX + PANEL_WIDTH - 85, y + 8, UIConstants.Text.PRIMARY, false);
        }

        // Cancel button (always visible)
        boolean cancelHover = AxiomRenderer.isMouseOver(mouseX, mouseY, panelX + PANEL_WIDTH / 2 - 40, y, 80, 25);
        if (cancelHover) {
            graphics.fill(panelX + PANEL_WIDTH / 2 - 40, y, panelX + PANEL_WIDTH / 2 + 40, y + 25,
                UIConstants.Background.HOVER);
        }
        graphics.drawString(getFont(), "Cancel", panelX + PANEL_WIDTH / 2 - 18, y + 8, UIConstants.Text.MUTED, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int mx = (int) mouseX;
        int my = (int) mouseY;
        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;
        int contentY = panelY + 60;

        // Handle step-specific clicks
        switch (currentStep) {
            case 0 -> handleStep1Click(mx, my, panelX, contentY);
            case 1 -> handleStep2Click(mx, my, panelX, contentY);
            case 2 -> handleStep3Click(mx, my, panelX, contentY);
            case 3 -> handleStep4Click(mx, my, panelX, contentY);
        }

        // Navigation buttons
        int navY = panelY + PANEL_HEIGHT - 40;

        // Back
        if (currentStep > 0 && AxiomRenderer.isMouseOver(mx, my, panelX + 20, navY, 80, 25)) {
            currentStep--;
            return true;
        }

        // Next
        if (currentStep < TOTAL_STEPS - 1 &&
            AxiomRenderer.isMouseOver(mx, my, panelX + PANEL_WIDTH - 100, navY, 80, 25)) {
            currentStep++;
            return true;
        }

        // Cancel
        if (AxiomRenderer.isMouseOver(mx, my, panelX + PANEL_WIDTH / 2 - 40, navY, 80, 25)) {
            onClose();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Only scroll in step 2 (mob selection)
        if (currentStep == 1) {
            int panelX = (width - PANEL_WIDTH) / 2;
            int contentY = (height - PANEL_HEIGHT) / 2 + 60;
            int leftCol = panelX + 20;
            int listY = contentY + 15;
            int listH = 120;

            // Check if mouse is over the mob list area
            if (AxiomRenderer.isMouseOver((int) mouseX, (int) mouseY, leftCol, listY, 200, listH)) {
                int maxScroll = Math.max(0, availableMobs.size() - 5);
                mobListScroll -= (int) scrollY;
                mobListScroll = Math.max(0, Math.min(maxScroll, mobListScroll));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void handleStep1Click(int mx, int my, int panelX, int y) {
        int cardWidth = 110;
        int cardHeight = 80;
        int gap = 15;
        int startX = panelX + (PANEL_WIDTH - (cardWidth * 4 + gap * 3)) / 2;

        for (int i = 0; i < TestType.values().length; i++) {
            int cx = startX + i * (cardWidth + gap);
            if (AxiomRenderer.isMouseOver(mx, my, cx, y, cardWidth, cardHeight)) {
                selectedTestType = TestType.values()[i];
                applyAutoConfig();
                break;
            }
        }
    }

    private void handleStep2Click(int mx, int my, int panelX, int y) {
        int leftCol = panelX + 20;
        int rightCol = panelX + 260;

        // Mob list clicks
        int listY = y + 15;
        for (int i = 0; i < Math.min(5, availableMobs.size()); i++) {
            int idx = mobListScroll + i;
            if (idx >= availableMobs.size()) break;
            int itemY = listY + 5 + i * 22;
            if (AxiomRenderer.isMouseOver(mx, my, leftCol + 2, itemY, 196, 20)) {
                selectedMobIndex = idx;
                selectedMob = availableMobs.get(idx).mobId;
                return;
            }
        }

        // Wave plus/minus
        int settingY = y + 20;
        if (AxiomRenderer.isMouseOver(mx, my, rightCol + 100, settingY - 2, 16, 16)) {
            waveCount = Math.max(1, waveCount - 1);
        } else if (AxiomRenderer.isMouseOver(mx, my, rightCol + 124, settingY - 2, 16, 16)) {
            waveCount = Math.min(50, waveCount + 1);
        }

        // Endless checkbox
        settingY += 25;
        if (AxiomRenderer.isMouseOver(mx, my, rightCol, settingY, 150, 16)) {
            endlessMode = !endlessMode;
        }

        // Arena plus/minus
        settingY += 25;
        if (AxiomRenderer.isMouseOver(mx, my, rightCol + 120, settingY - 2, 16, 16)) {
            arenaSize = Math.max(32, arenaSize - 16);
        } else if (AxiomRenderer.isMouseOver(mx, my, rightCol + 144, settingY - 2, 16, 16)) {
            arenaSize = Math.min(128, arenaSize + 16);
        }
    }

    private void handleStep3Click(int mx, int my, int panelX, int y) {
        int checkY = y + 25;
        int col1 = panelX + 30;
        int col2 = panelX + 260;

        // Column 1 checkboxes
        if (AxiomRenderer.isMouseOver(mx, my, col1 - 5, checkY - 2, 200, 20)) {
            enableTelemetryHud = !enableTelemetryHud;
            return;
        }
        checkY += 25;
        if (AxiomRenderer.isMouseOver(mx, my, col1 - 5, checkY - 2, 200, 20)) {
            enableDebugOverlay = !enableDebugOverlay;
            return;
        }
        checkY += 25;
        if (AxiomRenderer.isMouseOver(mx, my, col1 - 5, checkY - 2, 200, 20)) {
            enableBossPhase = !enableBossPhase;
            return;
        }

        // Column 2 checkboxes
        checkY = y + 25;
        if (AxiomRenderer.isMouseOver(mx, my, col2 - 5, checkY - 2, 200, 20)) {
            enableEntityDensity = !enableEntityDensity;
            return;
        }
        checkY += 25;
        if (AxiomRenderer.isMouseOver(mx, my, col2 - 5, checkY - 2, 200, 20)) {
            enableHeatmaps = !enableHeatmaps;
        }
    }

    private void handleStep4Click(int mx, int my, int panelX, int y) {
        // Big start button
        int btnX = panelX + (PANEL_WIDTH - 200) / 2;
        int btnY = y + 150;
        if (AxiomRenderer.isMouseOver(mx, my, btnX, btnY, 200, 40)) {
            startTest();
        }
    }

    private void applyAutoConfig() {
        // Auto-configure based on test type
        switch (selectedTestType) {
            case COMBAT -> {
                waveCount = 5;
                enableDebugOverlay = true;
                enableBossPhase = false;
                enableEntityDensity = false;
            }
            case BOSS -> {
                waveCount = 3;
                enableDebugOverlay = true;
                enableBossPhase = true;
                enableEntityDensity = false;
            }
            case DUNGEON -> {
                waveCount = 10;
                enableDebugOverlay = false;
                enableBossPhase = false;
                enableEntityDensity = true;
            }
            case CUSTOM -> {
                // Keep current settings
            }
        }
    }

    private void startTest() {
        Minecraft mc = Minecraft.getInstance();
        var localPlayer = mc.player;
        if (localPlayer == null) return;

        // Validate mob selection
        if (selectedMob == null) {
            localPlayer.displayClientMessage(
                I18n.translate("devmod.quicktest.select_mob_first"),
                true
            );
            mc.getSoundManager().play(Objects.requireNonNull(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    Objects.requireNonNull(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value()), 0.5f)));
            currentStep = 1; // Go back to mob selection step
            return;
        }

        // Map wizard test type to IntegratedTestSession type
        IntegratedTestSession.TestSessionType sessionType = switch (selectedTestType) {
            case COMBAT -> IntegratedTestSession.TestSessionType.COMBAT_BASIC;
            case BOSS -> IntegratedTestSession.TestSessionType.BOSS_FIGHT;
            case DUNGEON -> IntegratedTestSession.TestSessionType.SURVIVAL_WAVES;
            case CUSTOM -> IntegratedTestSession.TestSessionType.CUSTOM;
        };

        // Configure additional overlays based on wizard selections
        if (enableDebugOverlay) {
            com.frenkvs.devmod.ModConfig.showBodyPartBoxes = true;
        }
        if (enableBossPhase) {
            com.frenkvs.devmod.hud.BossPhaseOverlay.setEnabled(true);
        }
        if (enableEntityDensity) {
            com.frenkvs.devmod.hud.EntityDensityOverlay.setEnabled(true);
        }

        // Start integrated session (handles telemetry, overlays, and tracking)
        IntegratedTestSession.INSTANCE.startSession(
            sessionType,
            selectedMob,
            endlessMode ? 0 : waveCount,
            null // No linked TestCase from wizard
        );

        // Enable the integrated test HUD
        com.frenkvs.devmod.hud.IntegratedTestHud.setEnabled(true);

        // Close wizard and open endurance quest screen with pre-selection
        mc.setScreen(new com.frenkvs.devmod.endurance.EnduranceQuestScreen(
            selectedMob,
            endlessMode ? 0 : waveCount
        ));

        // Show notification
        localPlayer.displayClientMessage(
            I18n.translate("devmod.quicktest.session_started"),
            false
        );
        localPlayer.displayClientMessage(
            I18n.translate("devmod.quicktest.mob_preselected"),
            false
        );
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ===================== Test Type Enum =====================
    public enum TestType {
        COMBAT("⚔", "Combat", UIConstants.Accent.RED,
            "Test damage calculations, hitboxes, and combat feel",
            "5 waves, Debug overlay"),
        BOSS("👑", "Boss", UIConstants.Accent.PURPLE,
            "Test boss mechanics, phases, and balance",
            "3 waves, Boss Phase overlay"),
        DUNGEON("🏰", "Dungeon", UIConstants.Accent.BLUE,
            "Test room navigation, mob density, and pacing",
            "10 waves, Entity Density overlay"),
        CUSTOM("🔧", "Custom", UIConstants.Accent.ORANGE,
            "Configure everything manually",
            "Your settings");

        private final String icon;
        private final String displayName;
        private final int color;
        private final String description;
        private final String autoConfigHint;

        TestType(String icon, String displayName, int color, String description, String autoConfigHint) {
            this.icon = icon;
            this.displayName = displayName;
            this.color = color;
            this.description = description;
            this.autoConfigHint = autoConfigHint;
        }

        public String getIcon() { return icon; }
        public String getDisplayName() { return displayName; }
        public int getColor() { return color; }
        public String getDescription() { return description; }
        public String getAutoConfigHint() { return autoConfigHint; }
    }
}
