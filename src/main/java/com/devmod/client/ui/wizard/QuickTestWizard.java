package com.devmod.client.ui.wizard;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.testing.IntegratedTestSession;
import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.ConfirmDialog;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.endurance.EnduranceQuestRegistry;
import com.devmod.util.I18n;
@OnlyIn(Dist.CLIENT)
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
    private @Nullable ResourceLocation selectedMob = null;
    private int waveCount = 5;
    private boolean endlessMode = false;
    private int arenaSize = 64;

    // Step 3: Overlays
    private boolean enableDebugOverlay = true;
    private boolean enableBossPhase = false;
    private boolean enableTelemetryHud = true;
    private boolean enableEntityDensity = false;
    private boolean enableHeatmaps = false;

    // Quick presets for wave/endless setup
    private final java.util.List<PresetScenario> presetScenarios = java.util.List.of(
        new PresetScenario("Sprint 3", "3-wave smoke test", 3, false, 64),
        new PresetScenario("Baseline 10", "Standard 10 waves", 10, false, 64),
        new PresetScenario("Boss Prep", "15 waves (3 boss)", 15, false, 72),
        new PresetScenario("Endless", "Play until death", 0, true, 64),
        new PresetScenario("Stress 20", "High-load run", 20, false, 80)
    );
    private final List<PresetHitbox> presetHitboxes = new ArrayList<>();
    private @Nullable PresetScenario selectedPreset = null;

    // Available mobs
    private List<EnduranceQuestRegistry.MobQuestConfig> availableMobs = new ArrayList<>();
    private int selectedMobIndex = 0;
    private int mobListScroll = 0;

    // Modal overlay for cancel/exit confirmation
    private @Nullable ConfirmDialog exitDialog;

    // Navigation buttons (shared component adapters)
    private @Nullable com.devmod.client.ui.editor.components.EditorButton backButton;
    private @Nullable com.devmod.client.ui.editor.components.EditorButton nextButton;
    private @Nullable com.devmod.client.ui.editor.components.EditorButton cancelButton;
    private @Nullable com.devmod.client.ui.editor.components.EditorButton startButton;

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
            selectedMob = availableMobs.get(0).getMobId();
        }
    }

    @Override
    protected void init() {
        clearWidgets();

        backButton = new com.devmod.client.ui.editor.components.EditorButton("wizard-back", "← Back")
            .style(com.devmod.client.ui.editor.components.EditorButton.Style.NORMAL)
            .size(com.devmod.client.ui.editor.components.EditorButton.Size.MEDIUM)
            .onClick(() -> {
                if (currentStep > 0) {
                    currentStep--;
                    updateNavButtonsVisibility();
                }
            });

        nextButton = new com.devmod.client.ui.editor.components.EditorButton("wizard-next", "Next →")
            .style(com.devmod.client.ui.editor.components.EditorButton.Style.PRIMARY)
            .size(com.devmod.client.ui.editor.components.EditorButton.Size.MEDIUM)
            .onClick(() -> {
                if (currentStep < TOTAL_STEPS - 1) {
                    currentStep++;
                    updateNavButtonsVisibility();
                }
            });

        startButton = new com.devmod.client.ui.editor.components.EditorButton("wizard-start", "Start ⚡")
            .style(com.devmod.client.ui.editor.components.EditorButton.Style.PRIMARY)
            .size(com.devmod.client.ui.editor.components.EditorButton.Size.MEDIUM)
            .onClick(this::startTest);

        cancelButton = new com.devmod.client.ui.editor.components.EditorButton("wizard-cancel", "Cancel")
            .style(com.devmod.client.ui.editor.components.EditorButton.Style.GHOST)
            .size(com.devmod.client.ui.editor.components.EditorButton.Size.MEDIUM)
            .onClick(() -> {
                if (exitDialog != null) {
                    exitDialog.show();
                } else {
                    onClose();
                }
            });

        updateNavButtonsVisibility();

        exitDialog = ConfirmDialog.create(
            "Exit Quick Test Wizard",
            "Exit",
            "Stay",
            ConfirmDialog.Style.WARNING,
            this::onClose,
            () -> {}
        );
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();
        // Background
        AxiomRenderer.drawScreenBackground(graphics, width, height);

        // Responsive panel dimensions with scaling
        int scaledPanelWidth = UIScaleManager.scale(PANEL_WIDTH);
        int scaledPanelHeight = UIScaleManager.scale(PANEL_HEIGHT);
        int panelWidth = Math.min(scaledPanelWidth, width - UIScaleManager.scale(20));
        int panelHeight = Math.min(scaledPanelHeight, height - UIScaleManager.scale(20));
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;

        // Main panel
        drawMainPanel(graphics, panelX, panelY, panelWidth, panelHeight);

        // Step indicators
        drawStepIndicators(graphics, panelX, panelY, panelWidth);

        // Current step content
        int contentY = panelY + UIScaleManager.scale(60);
        switch (currentStep) {
            case 0 -> drawStep1_TestType(graphics, panelX, contentY, panelWidth, mouseX, mouseY);
            case 1 -> drawStep2_Configuration(graphics, panelX, contentY, panelWidth, mouseX, mouseY);
            case 2 -> drawStep3_Overlays(graphics, panelX, contentY, panelWidth, mouseX, mouseY);
            case 3 -> drawStep4_Summary(graphics, panelX, contentY, panelWidth, mouseX, mouseY);
        }

        // Modal overlay rendered last
        renderNavButtons(graphics, mouseX, mouseY);

        if (exitDialog != null) {
            exitDialog.render(graphics, getFont(), width, height, mouseX, mouseY);
        }
    }

    private void drawMainPanel(GuiGraphics graphics, int x, int y, int panelWidth, int panelHeight) {
        // Panel background
        graphics.fill(x, y, x + panelWidth, y + panelHeight, DesignTokens.Background.PANEL_SOLID);

        // Header gradient
        int headerHeight = UIScaleManager.scale(40);
        for (int i = 0; i < headerHeight; i++) {
            float t = (float) i / headerHeight;
            int color = DesignTokens.lerp(DesignTokens.QuickTest.HEADER_GRADIENT_START, DesignTokens.Background.HEADER, t);
            graphics.fill(x, y + i, x + panelWidth, y + i + 1, color);
        }

        // Border
        AxiomRenderer.drawBorder(graphics, x, y, panelWidth, panelHeight, DesignTokens.Border.ACCENT);

        // Title
        String title = "⚡ Quick Test Wizard";
        UIScaleManager.drawScaledString(graphics, getFont(), title, x + UIScaleManager.scale(15), y + UIScaleManager.scale(12), DesignTokens.Accent.PRIMARY, false);

        // Subtitle
        String subtitle = getStepTitle();
        UIScaleManager.drawScaledString(graphics, getFont(), subtitle, x + UIScaleManager.scale(15), y + UIScaleManager.scale(26), DesignTokens.Text.SECONDARY, false);
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

    private void drawStepIndicators(GuiGraphics graphics, int panelX, int panelY, int panelWidth) {
        int indicatorY = panelY + UIScaleManager.scale(45);
        int stepWidth = UIScaleManager.scale(75);
        int totalStepWidth = TOTAL_STEPS * stepWidth;
        int startX = panelX + (panelWidth - totalStepWidth) / 2 + stepWidth / 2;
        int circleRadius = UIScaleManager.scale(8);

        for (int i = 0; i < TOTAL_STEPS; i++) {
            int cx = startX + i * stepWidth;
            boolean completed = i < currentStep;
            boolean current = i == currentStep;

            // Circle
            int circleColor = completed ? DesignTokens.Semantic.SUCCESS :
                              (current ? DesignTokens.Semantic.INFO : DesignTokens.Border.MUTED);
            graphics.fill(cx - circleRadius, indicatorY - circleRadius, cx + circleRadius, indicatorY + circleRadius, circleColor);

            // Number
            String num = String.valueOf(i + 1);
            int textColor = (completed || current) ? DesignTokens.Text.TITLE : DesignTokens.Text.MUTED;
            UIScaleManager.drawScaledString(graphics, getFont(), num, cx - UIScaleManager.scale(3), indicatorY - UIScaleManager.scale(4), textColor, false);

            // Connector line
            if (i < TOTAL_STEPS - 1) {
                int lineColor = completed ? DesignTokens.Semantic.SUCCESS : DesignTokens.Border.MUTED;
                int lineOffset = UIScaleManager.scale(12);
                graphics.fill(cx + lineOffset, indicatorY - 1, cx + stepWidth - lineOffset, indicatorY + 1, lineColor);
            }
        }
    }

    // ===================== STEP 1: Test Type =====================
    private void drawStep1_TestType(GuiGraphics graphics, int panelX, int y, int panelWidth, int mouseX, int mouseY) {
        int cardWidth = UIScaleManager.scale(110);
        int cardHeight = UIScaleManager.scale(80);
        int gap = UIScaleManager.scale(15);
        int startX = panelX + (panelWidth - (cardWidth * 4 + gap * 3)) / 2;

        for (int i = 0; i < TestType.values().length; i++) {
            TestType type = TestType.values()[i];
            int cx = startX + i * (cardWidth + gap);
            boolean selected = type == selectedTestType;
            boolean hovered = AxiomRenderer.isMouseOver(mouseX, mouseY, cx, y, cardWidth, cardHeight);

            drawTestTypeCard(graphics, cx, y, cardWidth, cardHeight, type, selected, hovered);
        }

        // Description
        String desc = Objects.requireNonNullElse(selectedTestType.getDescription(), "");
        int margin = UIScaleManager.scale(20);
        int descY = y + cardHeight + margin;
        UIScaleManager.drawScaledString(graphics, getFont(), desc, panelX + margin, descY, DesignTokens.Text.SECONDARY, false);

        // Auto-configuration hint
        String hint = "\u00A77Auto-config: " + Objects.requireNonNullElse(selectedTestType.getAutoConfigHint(), "");
        UIScaleManager.drawScaledString(graphics, getFont(), hint, panelX + margin, descY + UIScaleManager.scale(15), DesignTokens.Text.MUTED, false);
    }

    private void drawTestTypeCard(GuiGraphics graphics, int x, int y, int w, int h,
                                   TestType type, boolean selected, boolean hovered) {
        // Background
        int bgColor = selected ? DesignTokens.withAlpha(type.getColor(), 60) :
                      (hovered ? DesignTokens.Background.HOVER : DesignTokens.Background.INPUT);
        graphics.fill(x, y, x + w, y + h, bgColor);

        // Border
        int borderColor = selected ? type.getColor() : DesignTokens.Border.MUTED;
        AxiomRenderer.drawBorder(graphics, x, y, w, h, borderColor);

        // Icon
        UIScaleManager.drawScaledString(graphics, getFont(), type.getIcon(), x + w/2 - 6, y + 15, type.getColor(), false);

        // Name
        @Nonnull String displayName = Objects.requireNonNull(Objects.requireNonNullElse(type.getDisplayName(), ""), "displayName");
        int textW = UIScaleManager.getScaledStringWidth(getFont(), displayName);
        UIScaleManager.drawScaledString(graphics, getFont(), displayName, x + (w - textW)/2, y + 40,
            selected ? DesignTokens.Text.TITLE : DesignTokens.Text.PRIMARY, false);

        // Selected indicator
        if (selected) {
            graphics.fill(x, y + h - 3, x + w, y + h, type.getColor());
        }
    }

    // ===================== STEP 2: Configuration =====================
    private void drawStep2_Configuration(GuiGraphics graphics, int panelX, int y, int panelWidth, int mouseX, int mouseY) {
        int margin = UIScaleManager.scale(20);
        int leftCol = panelX + margin;
        int rightCol = panelX + UIScaleManager.scale(260);

        // Mob selection
        UIScaleManager.drawScaledString(graphics, getFont(), "\u00A7lTarget Mob:", leftCol, y, DesignTokens.Text.PRIMARY, false);

        // Mob list (scrollable)
        int listY = y + UIScaleManager.scale(15);
        int listW = UIScaleManager.scale(200);
        int listH = UIScaleManager.scale(120);
        int visibleItems = 5;
        int itemHeight = UIScaleManager.scale(22);

        graphics.fill(leftCol, listY, leftCol + listW, listY + listH, DesignTokens.Background.INPUT);
        AxiomRenderer.drawBorder(graphics, leftCol, listY, listW, listH, DesignTokens.Border.MUTED);

        if (availableMobs.isEmpty()) {
            // Show empty state message
            int textOffset = UIScaleManager.scale(10);
            UIScaleManager.drawScaledString(graphics, getFont(), "No mobs found!", leftCol + textOffset, listY + UIScaleManager.scale(20), DesignTokens.Semantic.WARNING, false);
            UIScaleManager.drawScaledString(graphics, getFont(), "Enter a world first", leftCol + textOffset, listY + UIScaleManager.scale(35), DesignTokens.Text.MUTED, false);
            UIScaleManager.drawScaledString(graphics, getFont(), "to load mob registry.", leftCol + textOffset, listY + UIScaleManager.scale(48), DesignTokens.Text.MUTED, false);
        } else {
            for (int i = 0; i < Math.min(visibleItems, availableMobs.size()); i++) {
                int idx = mobListScroll + i;
                if (idx >= availableMobs.size()) break;

                var mob = availableMobs.get(idx);
                int itemY = listY + UIScaleManager.scale(5) + i * itemHeight;
                int itemW = listW - UIScaleManager.scale(4);
                int itemH = UIScaleManager.scale(20);
                boolean selected = idx == selectedMobIndex;
                boolean hovered = AxiomRenderer.isMouseOver(mouseX, mouseY, leftCol + 2, itemY, itemW, itemH);

                if (selected) {
                    graphics.fill(leftCol + 2, itemY, leftCol + 2 + itemW, itemY + itemH, DesignTokens.Background.ACTIVE);
                } else if (hovered) {
                    graphics.fill(leftCol + 2, itemY, leftCol + 2 + itemW, itemY + itemH, DesignTokens.Background.HOVER);
                }

                String mobName = mob.getDisplayName() != null ? mob.getDisplayName() : mob.getMobId().toString();
                if (mobName.length() > 25) mobName = mobName.substring(0, 22) + "...";
                UIScaleManager.drawScaledString(graphics, getFont(), mobName, leftCol + UIScaleManager.scale(8), itemY + UIScaleManager.scale(6),
                    selected ? DesignTokens.Accent.PRIMARY : DesignTokens.Text.PRIMARY, false);
            }
        }

        // Right column: Wave settings
        UIScaleManager.drawScaledString(graphics, getFont(), "\u00A7lWave Settings:", rightCol, y, DesignTokens.Text.PRIMARY, false);

        int settingY = y + UIScaleManager.scale(20);
        int rowHeight = UIScaleManager.scale(25);

        // Wave count
        UIScaleManager.drawScaledString(graphics, getFont(), "Waves: " + waveCount, rightCol, settingY, DesignTokens.Text.SECONDARY, false);
        drawPlusMinus(graphics, rightCol + UIScaleManager.scale(100), settingY - 2, mouseX, mouseY);
        settingY += rowHeight;

        // Endless mode
        drawCheckbox(graphics, rightCol, settingY, "Endless Mode", endlessMode, mouseX, mouseY);
        settingY += rowHeight;

        // Arena size
        UIScaleManager.drawScaledString(graphics, getFont(), "Arena: " + arenaSize + " blocks", rightCol, settingY, DesignTokens.Text.SECONDARY, false);
        drawPlusMinus(graphics, rightCol + UIScaleManager.scale(120), settingY - 2, mouseX, mouseY);

        // Quick presets
        int presetsY = y + UIScaleManager.scale(110);
        drawPresets(graphics, panelX + margin, presetsY, panelWidth, mouseX, mouseY);

        // Preview info (pushed down to make room for presets)
        int previewY = y + UIScaleManager.scale(190);
        int previewH = UIScaleManager.scale(60);
        graphics.fill(panelX + margin, previewY, panelX + panelWidth - margin, previewY + previewH, DesignTokens.Background.INPUT);
        AxiomRenderer.drawBorder(graphics, panelX + margin, previewY, panelWidth - margin * 2, previewH, DesignTokens.Border.MUTED);

        int textPad = UIScaleManager.scale(10);
        UIScaleManager.drawScaledString(graphics, getFont(), "\u00A7lTest Preview:", panelX + margin + textPad, previewY + UIScaleManager.scale(8), DesignTokens.Text.PRIMARY, false);
        if (selectedMob != null) {
            String preview = String.format("Fight %d waves of %s in %dx%d arena",
                waveCount, selectedMob.getPath(), arenaSize, arenaSize);
            UIScaleManager.drawScaledString(graphics, getFont(), preview, panelX + margin + textPad, previewY + UIScaleManager.scale(25), DesignTokens.Text.SECONDARY, false);

            String duration = endlessMode ? "Endless (until death)" : "~" + (waveCount * 2) + " minutes estimated";
            UIScaleManager.drawScaledString(graphics, getFont(), duration, panelX + margin + textPad, previewY + UIScaleManager.scale(40), DesignTokens.Text.MUTED, false);
        }
    }

    private void drawPlusMinus(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        boolean minusHover = AxiomRenderer.isMouseOver(mouseX, mouseY, x, y, 16, 16);
        boolean plusHover = AxiomRenderer.isMouseOver(mouseX, mouseY, x + 24, y, 16, 16);

        graphics.fill(x, y, x + 16, y + 16, minusHover ? DesignTokens.Background.HOVER : DesignTokens.Background.INPUT);
        graphics.fill(x + 24, y, x + 40, y + 16, plusHover ? DesignTokens.Background.HOVER : DesignTokens.Background.INPUT);

        UIScaleManager.drawScaledString(graphics, getFont(), "-", x + 5, y + 4, DesignTokens.Text.PRIMARY, false);
        UIScaleManager.drawScaledString(graphics, getFont(), "+", x + 29, y + 4, DesignTokens.Text.PRIMARY, false);
    }

    private void drawPresets(GuiGraphics graphics, int x, int y, int panelWidth, int mouseX, int mouseY) {
        presetHitboxes.clear();

        UIScaleManager.drawScaledString(graphics, getFont(), "\u00A7lQuick presets:", x, y - 12, DesignTokens.Text.PRIMARY, false);

        int margin = UIScaleManager.scale(20);
        int gap = UIScaleManager.scale(10);
        int availableWidth = panelWidth - margin * 2;
        int colWidth = (availableWidth - gap) / 2;
        int rowHeight = UIScaleManager.scale(32);

        for (int i = 0; i < presetScenarios.size(); i++) {
            PresetScenario preset = presetScenarios.get(i);
            int col = i % 2;
            int row = i / 2;
            int px = x + col * (colWidth + gap);
            int py = y + row * (rowHeight + gap);

            boolean hovered = AxiomRenderer.isMouseOver(mouseX, mouseY, px, py, colWidth, rowHeight);
            boolean selected = preset.equals(selectedPreset);

            int bg = selected ? DesignTokens.Background.ACTIVE : hovered ? DesignTokens.Background.HOVER : DesignTokens.Background.INPUT;
            graphics.fill(px, py, px + colWidth, py + rowHeight, bg);
            AxiomRenderer.drawBorder(graphics, px, py, colWidth, rowHeight, selected ? DesignTokens.Semantic.SUCCESS : DesignTokens.Border.MUTED);

            UIScaleManager.drawScaledString(graphics, getFont(), preset.name, px + 8, py + 7, DesignTokens.Text.PRIMARY, false);
            UIScaleManager.drawScaledString(graphics, getFont(), preset.description, px + 8, py + 20, DesignTokens.Text.MUTED, false);

            presetHitboxes.add(new PresetHitbox(preset, px, py, colWidth, rowHeight));
        }
    }

    private void drawCheckbox(GuiGraphics graphics, int x, int y, String label, boolean checked,
                               int mouseX, int mouseY) {
        boolean hovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x, y, 150, 16);

        // Box
        graphics.fill(x, y, x + 14, y + 14, DesignTokens.Background.INPUT);
        AxiomRenderer.drawBorder(graphics, x, y, 14, 14, hovered ? DesignTokens.Border.ACCENT : DesignTokens.Border.MUTED);

        if (checked) {
            graphics.fill(x + 3, y + 3, x + 11, y + 11, DesignTokens.Semantic.SUCCESS);
        }

        // Label
        UIScaleManager.drawScaledString(graphics, getFont(), label, x + 20, y + 3, DesignTokens.Text.SECONDARY, false);
    }

    // ===================== STEP 3: Overlays =====================
    private void drawStep3_Overlays(GuiGraphics graphics, int panelX, int y, int panelWidth, int mouseX, int mouseY) {
        int margin = UIScaleManager.scale(20);
        UIScaleManager.drawScaledString(graphics, getFont(), "\u00A7lRecommended overlays for " + selectedTestType.getDisplayName() + ":",
            panelX + margin, y, DesignTokens.Text.PRIMARY, false);

        int rowHeight = UIScaleManager.scale(25);
        int checkY = y + rowHeight;
        int col1 = panelX + UIScaleManager.scale(30);
        int col2 = panelX + UIScaleManager.scale(260);

        // Column 1
        enableTelemetryHud = drawOverlayCheckbox(graphics, col1, checkY, "Telemetry Status HUD",
            enableTelemetryHud, true, mouseX, mouseY);
        checkY += rowHeight;

        enableDebugOverlay = drawOverlayCheckbox(graphics, col1, checkY, "Debug Overlay (Body Parts)",
            enableDebugOverlay, selectedTestType == TestType.COMBAT, mouseX, mouseY);
        checkY += rowHeight;

        enableBossPhase = drawOverlayCheckbox(graphics, col1, checkY, "Boss Phase Overlay",
            enableBossPhase, selectedTestType == TestType.BOSS, mouseX, mouseY);

        // Column 2
        checkY = y + rowHeight;
        enableEntityDensity = drawOverlayCheckbox(graphics, col2, checkY, "Entity Density",
            enableEntityDensity, selectedTestType == TestType.DUNGEON, mouseX, mouseY);
        checkY += rowHeight;

        enableHeatmaps = drawOverlayCheckbox(graphics, col2, checkY, "Record Heatmaps",
            enableHeatmaps, true, mouseX, mouseY);

        // Info box
        int infoY = y + UIScaleManager.scale(120);
        int infoH = UIScaleManager.scale(70);
        graphics.fill(panelX + margin, infoY, panelX + panelWidth - margin, infoY + infoH, DesignTokens.QuickTest.INFO_BG);
        AxiomRenderer.drawBorder(graphics, panelX + margin, infoY, panelWidth - margin * 2, infoH, DesignTokens.Semantic.WARNING);

        int textPad = UIScaleManager.scale(10);
        UIScaleManager.drawScaledString(graphics, getFont(), "\u00A76ℹ\u00A7r Overlays provide real-time feedback during testing:",
            panelX + margin + textPad, infoY + UIScaleManager.scale(10), DesignTokens.Text.PRIMARY, false);
        UIScaleManager.drawScaledString(graphics, getFont(), "• Telemetry HUD shows recording status and current room",
            panelX + margin + textPad, infoY + UIScaleManager.scale(25), DesignTokens.Text.SECONDARY, false);
        UIScaleManager.drawScaledString(graphics, getFont(), "• Debug overlay shows hitboxes and damage numbers",
            panelX + margin + textPad, infoY + UIScaleManager.scale(38), DesignTokens.Text.SECONDARY, false);
        UIScaleManager.drawScaledString(graphics, getFont(), "• Heatmaps record movement patterns for analysis",
            panelX + margin + textPad, infoY + UIScaleManager.scale(51), DesignTokens.Text.SECONDARY, false);
    }

    private boolean drawOverlayCheckbox(GuiGraphics graphics, int x, int y, String label,
                                         boolean checked, boolean recommended, int mouseX, int mouseY) {
        boolean hovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x - 5, y - 2, 200, 20);

        // Box
        graphics.fill(x, y, x + 14, y + 14, DesignTokens.Background.INPUT);
        int borderColor = recommended ? DesignTokens.Accent.YELLOW : DesignTokens.Border.MUTED;
        AxiomRenderer.drawBorder(graphics, x, y, 14, 14, hovered ? DesignTokens.Border.ACCENT : borderColor);

        if (checked) {
            graphics.fill(x + 3, y + 3, x + 11, y + 11, DesignTokens.Semantic.SUCCESS);
        }

        // Label with recommendation marker
        String displayLabel = recommended ? label + " \u00A76★" : label;
        UIScaleManager.drawScaledString(graphics, getFont(), displayLabel, x + 20, y + 3, DesignTokens.Text.SECONDARY, false);

        return checked; // Return unchanged - click handling separate
    }

    // ===================== STEP 4: Summary =====================
    private void drawStep4_Summary(GuiGraphics graphics, int panelX, int y, int panelWidth, int mouseX, int mouseY) {
        int margin = UIScaleManager.scale(20);
        UIScaleManager.drawScaledString(graphics, getFont(), "\u00A7l\u00A7aReady to Start Test!", panelX + margin, y, DesignTokens.Semantic.SUCCESS, false);

        int rowHeight = UIScaleManager.scale(18);
        int summaryY = y + UIScaleManager.scale(25);
        int leftCol = panelX + UIScaleManager.scale(30);
        int rightCol = panelX + UIScaleManager.scale(280);

        // Test type
        UIScaleManager.drawScaledString(graphics, getFont(), "Test Type:", leftCol, summaryY, DesignTokens.Text.MUTED, false);
        UIScaleManager.drawScaledString(graphics, getFont(), selectedTestType.getDisplayName(), rightCol, summaryY, DesignTokens.Text.PRIMARY, false);
        summaryY += rowHeight;

        // Preset / waves
        UIScaleManager.drawScaledString(graphics, getFont(), "Preset:", leftCol, summaryY, DesignTokens.Text.MUTED, false);
        PresetScenario localPreset = selectedPreset;
        String presetLabel = localPreset != null ? localPreset.name : "Custom";
        UIScaleManager.drawScaledString(graphics, getFont(), presetLabel, rightCol, summaryY, DesignTokens.Text.PRIMARY, false);
        summaryY += rowHeight;

        UIScaleManager.drawScaledString(graphics, getFont(), "Waves / Mode:", leftCol, summaryY, DesignTokens.Text.MUTED, false);
        String waveLabel = endlessMode ? "Endless" : waveCount + " waves";
        UIScaleManager.drawScaledString(graphics, getFont(), waveLabel, rightCol, summaryY, DesignTokens.Text.PRIMARY, false);
        summaryY += rowHeight;

        // Target
        UIScaleManager.drawScaledString(graphics, getFont(), "Target Mob:", leftCol, summaryY, DesignTokens.Text.MUTED, false);
        String mobName = selectedMob != null ? selectedMob.getPath() : "None";
        UIScaleManager.drawScaledString(graphics, getFont(), mobName, rightCol, summaryY, DesignTokens.Text.PRIMARY, false);
        summaryY += rowHeight;

        // Arena
        UIScaleManager.drawScaledString(graphics, getFont(), "Arena Size:", leftCol, summaryY, DesignTokens.Text.MUTED, false);
        UIScaleManager.drawScaledString(graphics, getFont(), arenaSize + "x" + arenaSize + " blocks", rightCol, summaryY, DesignTokens.Text.PRIMARY, false);
        summaryY += rowHeight;

        // Overlays
        UIScaleManager.drawScaledString(graphics, getFont(), "Active Overlays:", leftCol, summaryY, DesignTokens.Text.MUTED, false);
        List<String> activeOverlays = new ArrayList<>();
        if (enableTelemetryHud) activeOverlays.add("Telemetry");
        if (enableDebugOverlay) activeOverlays.add("Debug");
        if (enableBossPhase) activeOverlays.add("Boss Phase");
        if (enableEntityDensity) activeOverlays.add("Density");
        if (enableHeatmaps) activeOverlays.add("Heatmaps");
        UIScaleManager.drawScaledString(graphics, getFont(), String.join(", ", activeOverlays), rightCol, summaryY, DesignTokens.Text.PRIMARY, false);

        // Big start button
        int btnWidth = UIScaleManager.scale(200);
        int btnX = panelX + (panelWidth - btnWidth) / 2;
        int btnY = y + UIScaleManager.scale(150);
        int btnHeight = UIScaleManager.scale(40);
        boolean btnHover = AxiomRenderer.isMouseOver(mouseX, mouseY, btnX, btnY, btnWidth, btnHeight);

        int btnBg = btnHover ? DesignTokens.withAlpha(DesignTokens.Semantic.SUCCESS, 80) : DesignTokens.Background.INPUT;
        graphics.fill(btnX, btnY, btnX + btnWidth, btnY + btnHeight, btnBg);
        graphics.fill(btnX, btnY, btnX + UIScaleManager.scale(4), btnY + btnHeight, DesignTokens.Semantic.SUCCESS);
        AxiomRenderer.drawBorder(graphics, btnX, btnY, btnWidth, btnHeight, btnHover ? DesignTokens.Semantic.SUCCESS : DesignTokens.Border.MUTED);

        String btnText = "▶ START TEST";
        int btnTextW = UIScaleManager.getScaledStringWidth(getFont(), btnText);
        UIScaleManager.drawScaledString(graphics, getFont(), btnText, btnX + (btnWidth - btnTextW) / 2, btnY + UIScaleManager.scale(16),
            btnHover ? DesignTokens.Semantic.SUCCESS : DesignTokens.Text.PRIMARY, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Modal consumes input
        if (exitDialog != null && exitDialog.mouseClicked(mouseX, mouseY, width, height)) {
            return true;
        }
        if (exitDialog != null && exitDialog.isVisible()) {
            return true; // Block background interactions while dialog open
        }

        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        // Capture nullable fields in local variables for null safety
        var localBackButton = backButton;
        var localNextButton = nextButton;
        var localCancelButton = cancelButton;
        var localStartButton = startButton;

        if (localBackButton == null || localNextButton == null || localCancelButton == null || localStartButton == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        int mx = (int) mouseX;
        int my = (int) mouseY;
        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;
        int contentY = panelY + 60;

        if (currentStep > 0 && localBackButton.mouseClicked(mouseX, mouseY, button)) {
            localBackButton.mouseReleased(mouseX, mouseY, button);
            return true;
        }
        if (currentStep < TOTAL_STEPS - 1 && localNextButton.mouseClicked(mouseX, mouseY, button)) {
            localNextButton.mouseReleased(mouseX, mouseY, button);
            return true;
        }
        if (currentStep == TOTAL_STEPS - 1 && localStartButton.mouseClicked(mouseX, mouseY, button)) {
            localStartButton.mouseReleased(mouseX, mouseY, button);
            return true;
        }
        if (localCancelButton.mouseClicked(mouseX, mouseY, button)) {
            localCancelButton.mouseReleased(mouseX, mouseY, button);
            return true;
        }

        // Handle step-specific clicks
        switch (currentStep) {
            case 0 -> handleStep1Click(mx, my, panelX, contentY);
            case 1 -> handleStep2Click(mx, my, panelX, contentY);
            case 2 -> handleStep3Click(mx, my, panelX, contentY);
            case 3 -> handleStep4Click(mx, my, panelX, contentY);
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (exitDialog != null && exitDialog.mouseScrolled(mouseX, mouseY, scrollY, width, height)) {
            return true;
        }
        if (exitDialog != null && exitDialog.isVisible()) {
            return true;
        }

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

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // Capture nullable fields in local variables for null safety
        var localBackButton = backButton;
        var localNextButton = nextButton;
        var localCancelButton = cancelButton;
        var localStartButton = startButton;

        if (localBackButton == null || localNextButton == null || localCancelButton == null || localStartButton == null) {
            return super.mouseReleased(mouseX, mouseY, button);
        }
        boolean handled = false;
        handled |= localBackButton.mouseReleased(mouseX, mouseY, button);
        handled |= localNextButton.mouseReleased(mouseX, mouseY, button);
        handled |= localStartButton.mouseReleased(mouseX, mouseY, button);
        handled |= localCancelButton.mouseReleased(mouseX, mouseY, button);
        if (handled) return true;
        return super.mouseReleased(mouseX, mouseY, button);
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
                selectedMob = availableMobs.get(idx).getMobId();
                return;
            }
        }

        // Wave plus/minus
        int settingY = y + 20;
        if (AxiomRenderer.isMouseOver(mx, my, rightCol + 100, settingY - 2, 16, 16)) {
            waveCount = Math.max(1, waveCount - 1);
            selectedPreset = null;
        } else if (AxiomRenderer.isMouseOver(mx, my, rightCol + 124, settingY - 2, 16, 16)) {
            waveCount = Math.min(50, waveCount + 1);
            selectedPreset = null;
        }

        // Endless checkbox
        settingY += 25;
        if (AxiomRenderer.isMouseOver(mx, my, rightCol, settingY, 150, 16)) {
            endlessMode = !endlessMode;
            selectedPreset = null;
        }

        // Arena plus/minus
        settingY += 25;
        if (AxiomRenderer.isMouseOver(mx, my, rightCol + 120, settingY - 2, 16, 16)) {
            arenaSize = Math.max(32, arenaSize - 16);
            selectedPreset = null;
        } else if (AxiomRenderer.isMouseOver(mx, my, rightCol + 144, settingY - 2, 16, 16)) {
            arenaSize = Math.min(128, arenaSize + 16);
            selectedPreset = null;
        }

        // Preset chips
        for (PresetHitbox hit : presetHitboxes) {
            if (hit.contains(mx, my)) {
                applyPreset(hit.preset());
                break;
            }
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

    private void renderNavButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        // Capture nullable fields in local variables for null safety
        var localBackButton = backButton;
        var localNextButton = nextButton;
        var localCancelButton = cancelButton;
        var localStartButton = startButton;

        if (localBackButton == null || localNextButton == null || localCancelButton == null || localStartButton == null) {
            return;
        }
        int panelWidth = Math.min(PANEL_WIDTH, width - 20);
        int panelHeight = Math.min(PANEL_HEIGHT, height - 20);
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;
        int navY = panelY + panelHeight - 36;

        if (currentStep > 0) {
            localBackButton.render(graphics, panelX + 20, navY, 80, 24, mouseX, mouseY);
        }
        if (currentStep < TOTAL_STEPS - 1) {
            localNextButton.render(graphics, panelX + panelWidth - 100, navY, 80, 24, mouseX, mouseY);
        }
        if (currentStep == TOTAL_STEPS - 1) {
            localStartButton.render(graphics, panelX + panelWidth - 120, navY, 100, 24, mouseX, mouseY);
        }
        localCancelButton.render(graphics, panelX + panelWidth / 2 - 40, navY, 80, 24, mouseX, mouseY);
    }

    private void updateNavButtonsVisibility() {
        boolean atFirstStep = currentStep == 0;
        boolean atLastStep = currentStep == TOTAL_STEPS - 1;

        // Capture nullable fields in local variables for null safety
        var localBackButton = backButton;
        var localNextButton = nextButton;
        var localStartButton = startButton;

        if (localBackButton == null || localNextButton == null || localStartButton == null) {
            return;
        }
        localBackButton.setEnabled(!atFirstStep);
        localNextButton.setEnabled(!atLastStep);
        localStartButton.setEnabled(atLastStep);
    }

    private void applyAutoConfig() {
        // Auto-configure based on test type
        switch (selectedTestType) {
            case COMBAT -> {
                waveCount = 5;
                enableDebugOverlay = true;
                enableBossPhase = false;
                enableEntityDensity = false;
                selectedPreset = null;
            }
            case BOSS -> {
                waveCount = 3;
                enableDebugOverlay = true;
                enableBossPhase = true;
                enableEntityDensity = false;
                selectedPreset = null;
            }
            case DUNGEON -> {
                waveCount = 10;
                enableDebugOverlay = false;
                enableBossPhase = false;
                enableEntityDensity = true;
                selectedPreset = null;
            }
            case CUSTOM -> {
                // Keep current settings
            }
        }
    }

    private void applyPreset(PresetScenario preset) {
        this.waveCount = preset.waves;
        this.endlessMode = preset.endless;
        this.arenaSize = preset.arenaSize;
        this.selectedPreset = preset;
        this.selectedTestType = TestType.CUSTOM; // mark as custom tuning
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
            updateNavButtonsVisibility();
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
            com.devmod.ModConfig.setShowBodyPartBoxes(true);
        }
        if (enableBossPhase) {
            com.devmod.client.overlay.BossPhaseOverlay.setEnabled(true);
        }
        if (enableEntityDensity) {
            com.devmod.client.overlay.EntityDensityOverlay.setEnabled(true);
        }

        // Start integrated session (handles telemetry, overlays, and tracking)
        IntegratedTestSession.INSTANCE.startSession(
            sessionType,
            selectedMob,
            endlessMode ? 0 : waveCount,
            null // No linked TestCase from wizard
        );

        // Enable the integrated test HUD
        com.devmod.client.overlay.IntegratedTestOverlay.setEnabled(true);

        // Close wizard and open endurance quest screen with pre-selection
        com.devmod.client.ui.ScreenSafety.openSafe(
            "endurance_quest",
            () -> new com.devmod.client.endurance.EnduranceQuestScreen(
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

    // ===================== Preset DTOs =====================
    private static class PresetScenario {
        final String name;
        final String description;
        final int waves;
        final boolean endless;
        final int arenaSize;

        PresetScenario(String name, String description, int waves, boolean endless, int arenaSize) {
            this.name = name;
            this.description = description;
            this.waves = waves;
            this.endless = endless;
            this.arenaSize = arenaSize;
        }
    }

    private record PresetHitbox(PresetScenario preset, int x, int y, int w, int h) {
        boolean contains(int mx, int my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    // ===================== Test Type Enum =====================
    public enum TestType {
        COMBAT("⚔", "Combat", DesignTokens.Semantic.ERROR,
            "Test damage calculations, hitboxes, and combat feel",
            "5 waves, Debug overlay"),
        BOSS("👑", "Boss", DesignTokens.Accent.PURPLE,
            "Test boss mechanics, phases, and balance",
            "3 waves, Boss Phase overlay"),
        DUNGEON("🏰", "Dungeon", DesignTokens.Semantic.INFO,
            "Test room navigation, mob density, and pacing",
            "10 waves, Entity Density overlay"),
        CUSTOM("🔧", "Custom", DesignTokens.Semantic.WARNING,
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

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (exitDialog != null && exitDialog.keyPressed(keyCode)) {
            return true;
        }
        if (exitDialog != null && exitDialog.isVisible()) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (exitDialog != null) {
                exitDialog.show();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (exitDialog != null && exitDialog.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (exitDialog != null && exitDialog.isVisible()) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }
}
