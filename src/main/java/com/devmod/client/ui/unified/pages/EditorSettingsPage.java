package com.devmod.client.ui.unified.pages;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.editor.core.EditorConstants;
import com.devmod.client.ui.editor.core.EditorScaleCalculator;
import com.devmod.client.ui.editor.core.ScaledCoord;
import com.devmod.client.ui.editor.core.UIConstants;
import com.devmod.client.ui.unified.SettingsCategory;
import com.devmod.client.ui.unified.SettingsPage;
import com.devmod.config.EditorClientConfig;

/**
 * Editor settings page for Item Editor UI configuration.
 * Includes UI scale selection, sounds toggle, and default mode.
 */
public class EditorSettingsPage implements SettingsPage {

    private static final int ROW_HEIGHT = 24;
    private static final int BUTTON_HEIGHT = 22;
    private static final int BUTTON_WIDTH = 60;
    private static final int BUTTON_GAP = 8;
    private static final int PREVIEW_HEIGHT = 110;
    private static final int PREVIEW_WIDTH_MAX = 240;
    private static final int PREVIEW_PADDING = 6;

    // Track original values
    private EditorClientConfig.EditorUiScale originalUiScale;
    private EditorClientConfig.EditorDefaultMode originalDefaultMode;
    private boolean originalSoundsEnabled;

    // Current values
    private EditorClientConfig.EditorUiScale currentUiScale;
    private EditorClientConfig.EditorDefaultMode currentDefaultMode;
    private boolean currentSoundsEnabled;

    // Scale button options
    private static final EditorClientConfig.EditorUiScale[] SCALE_OPTIONS = EditorClientConfig.EditorUiScale.values();

    @Override
    public SettingsCategory getCategory() {
        return SettingsCategory.EDITOR;
    }

    @Override
    public String getTitle() {
        return "Item Editor Settings";
    }

    @Override
    public void init() {
        // Load from config
        try {
            originalUiScale = EditorClientConfig.EDITOR_UI_SCALE.get();
            originalDefaultMode = EditorClientConfig.EDITOR_DEFAULT_MODE.get();
            originalSoundsEnabled = EditorClientConfig.EDITOR_SOUNDS_ENABLED.get();
        } catch (Exception e) {
            originalUiScale = EditorClientConfig.EditorUiScale.AUTO;
            originalDefaultMode = EditorClientConfig.EditorDefaultMode.PREVIEW;
            originalSoundsEnabled = true;
        }

        currentUiScale = originalUiScale;
        currentDefaultMode = originalDefaultMode;
        currentSoundsEnabled = originalSoundsEnabled;
    }

    @Override
    public void render(GuiGraphics graphics, @Nonnull Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
        int currentY = y;

        // === SECTION: UI Scale ===
        AxiomRenderer.drawSectionHeader(graphics, font, x, currentY, "UI Scale");
        currentY += ROW_HEIGHT;

        // Description
        graphics.drawString(font, "Select the editor panel scale:", x, currentY, UIConstants.Text.SECONDARY(), false);
        currentY += 14;

        // Scale buttons row
        int buttonX = x;
        for (EditorClientConfig.EditorUiScale scale : SCALE_OPTIONS) {
            boolean selected = scale == currentUiScale;
            boolean hovered = isMouseOver(mouseX, mouseY, buttonX, currentY, BUTTON_WIDTH, BUTTON_HEIGHT);

            drawScaleButton(graphics, font, buttonX, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                    getScaleLabel(scale), selected, hovered);

            buttonX += BUTTON_WIDTH + BUTTON_GAP;
        }
        currentY += BUTTON_HEIGHT + 8;

        // Current scale info + preview
        float effectiveScale = renderScaleInfo(graphics, font, x, currentY, width);
        currentY += ROW_HEIGHT + UIConstants.Spacing.GAP_SMALL;
        renderScalePreview(graphics, font, x, currentY, width, effectiveScale);
        currentY += PREVIEW_HEIGHT + UIConstants.Spacing.GAP_LARGE;

        // Separator
        AxiomRenderer.drawSeparator(graphics, x, currentY, width);
        currentY += 16;

        // === SECTION: Default Mode ===
        AxiomRenderer.drawSectionHeader(graphics, font, x, currentY, "Default Mode");
        currentY += ROW_HEIGHT;

        // Mode description
        graphics.drawString(font, "Mode when opening the editor:", x, currentY, UIConstants.Text.SECONDARY(), false);
        currentY += 14;

        // Preview button
        boolean previewSelected = currentDefaultMode == EditorClientConfig.EditorDefaultMode.PREVIEW;
        boolean previewHovered = isMouseOver(mouseX, mouseY, x, currentY, 100, BUTTON_HEIGHT);
        drawModeButton(graphics, font, x, currentY, 100, BUTTON_HEIGHT, "PREVIEW",
                "Safe, client-only", previewSelected, previewHovered, UIConstants.Accent.YELLOW());

        // Apply button
        boolean applySelected = currentDefaultMode == EditorClientConfig.EditorDefaultMode.APPLY;
        boolean applyHovered = isMouseOver(mouseX, mouseY, x + 110, currentY, 100, BUTTON_HEIGHT);
        drawModeButton(graphics, font, x + 110, currentY, 100, BUTTON_HEIGHT, "APPLY",
                "Persistent changes", applySelected, applyHovered, UIConstants.Accent.GREEN());

        currentY += BUTTON_HEIGHT + 16;

        // Separator
        AxiomRenderer.drawSeparator(graphics, x, currentY, width);
        currentY += 16;

        // === SECTION: Sound Effects ===
        AxiomRenderer.drawSectionHeader(graphics, font, x, currentY, "Sound Effects");
        currentY += ROW_HEIGHT;

        // Sound toggle row
        renderToggleRow(graphics, font, x, currentY, width, "Editor Sounds",
                "Button clicks, slider ticks, toggles", currentSoundsEnabled, mouseX, mouseY);
        currentY += ROW_HEIGHT + 8;

        // Hint
        AxiomRenderer.drawHint(graphics, font, x, currentY, "Changes are applied when you click Apply.");
    }

    private float renderScaleInfo(GuiGraphics graphics, Font font, int x, int y, int width) {
        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        // Calculate effective scale
        float effectiveScale;
        String scaleSource;

        if (currentUiScale == EditorClientConfig.EditorUiScale.AUTO) {
            effectiveScale = EditorScaleCalculator.calculateAutoScale(screenW, screenH);
            scaleSource = "Auto";
        } else {
            effectiveScale = currentUiScale.getScaleFloat();
            scaleSource = currentUiScale.getValue() + "x";
        }

        // Calculate panel size
        int panelW = ScaledCoord.scaleDim(EditorConstants.PANEL_WIDTH, effectiveScale);
        int panelH = ScaledCoord.scaleDim(EditorConstants.PANEL_HEIGHT, effectiveScale);

        // Current info
        String info = String.format("Current: %s → %.2fx | Panel: %d×%d px | Screen: %d×%d",
                scaleSource, effectiveScale, panelW, panelH, screenW, screenH);

        graphics.drawString(Objects.requireNonNull(font), info, x, y, UIConstants.Text.MUTED(), false);
        return effectiveScale;
    }

    private void renderScalePreview(GuiGraphics graphics, Font font, int x, int y, int width, float effectiveScale) {
        Font safeFont = Objects.requireNonNull(font, "font");
        int previewWidth = Math.min(width, PREVIEW_WIDTH_MAX);
        int previewHeight = PREVIEW_HEIGHT;
        int previewX = x;
        int previewY = y;

        graphics.fill(previewX, previewY, previewX + previewWidth, previewY + previewHeight, UIConstants.Background.INPUT());
        AxiomRenderer.drawBorder(graphics, previewX, previewY, previewWidth, previewHeight, UIConstants.Border.MUTED());

        int panelW = ScaledCoord.scaleDim(EditorConstants.PANEL_WIDTH, effectiveScale);
        int panelH = ScaledCoord.scaleDim(EditorConstants.PANEL_HEIGHT, effectiveScale);
        float fitScale = Math.min(
                (previewWidth - PREVIEW_PADDING * 2) / (float) panelW,
                (previewHeight - PREVIEW_PADDING * 2) / (float) panelH
        );
        int scaledW = Math.max(1, Math.round(panelW * fitScale));
        int scaledH = Math.max(1, Math.round(panelH * fitScale));
        int panelX = previewX + (previewWidth - scaledW) / 2;
        int panelY = previewY + (previewHeight - scaledH) / 2;

        graphics.fill(panelX, panelY, panelX + scaledW, panelY + scaledH, UIConstants.Background.PANEL());
        AxiomRenderer.drawBorder(graphics, panelX, panelY, scaledW, scaledH, UIConstants.Border.DEFAULT());

        int headerH = Math.max(1, Math.round(ScaledCoord.scaleDim(EditorConstants.HEADER_HEIGHT, effectiveScale) * fitScale));
        graphics.fill(panelX, panelY, panelX + scaledW, panelY + headerH, UIConstants.Background.HEADER());

        int leftW = Math.max(1, Math.round(ScaledCoord.scaleDim(EditorConstants.LEFT_COLUMN_WIDTH, effectiveScale) * fitScale));
        graphics.fill(panelX, panelY + headerH, panelX + leftW, panelY + scaledH, UIConstants.Background.INPUT());
        graphics.fill(panelX + leftW, panelY + headerH, panelX + leftW + 1, panelY + scaledH, UIConstants.Border.SEPARATOR());

        String label = String.format("Preview %.2fx", effectiveScale);
        graphics.drawString(safeFont, Objects.requireNonNull(label, "label"),
            previewX + 6, previewY + previewHeight - 12, UIConstants.Text.MUTED(), false);
    }

    private void drawScaleButton(GuiGraphics graphics, Font font, int x, int y, int w, int h,
                                  String label, boolean selected, boolean hovered) {
        Font safeFont = Objects.requireNonNull(font, "font");
        // Background
        int bgColor;
        if (selected) {
            bgColor = UIConstants.setAlpha(UIConstants.Accent.CYAN(), 80);
        } else if (hovered) {
            bgColor = UIConstants.Background.HOVER();
        } else {
            bgColor = UIConstants.Background.INPUT();
        }
        graphics.fill(x, y, x + w, y + h, bgColor);

        // Border
        int borderColor = selected ? UIConstants.Accent.CYAN() : (hovered ? UIConstants.Border.ACCENT() : UIConstants.Border.DEFAULT());
        AxiomRenderer.drawBorder(graphics, x, y, w, h, borderColor);

        // Text centered
        int textW = safeFont.width(Objects.requireNonNull(label, "label"));
        int textX = x + (w - textW) / 2;
        int textY = y + (h - 8) / 2;
        int textColor = selected ? UIConstants.Text.PRIMARY() : (hovered ? UIConstants.Text.PRIMARY() : UIConstants.Text.SECONDARY());
        graphics.drawString(safeFont, Objects.requireNonNull(label, "label"), textX, textY, textColor, false);
    }

    private void drawModeButton(GuiGraphics graphics, Font font, int x, int y, int w, int h,
                                 String label, String desc, boolean selected, boolean hovered, int accentColor) {
        Font safeFont = Objects.requireNonNull(font, "font");
        // Background
        int bgColor;
        if (selected) {
            bgColor = UIConstants.setAlpha(accentColor, 60);
        } else if (hovered) {
            bgColor = UIConstants.Background.HOVER();
        } else {
            bgColor = UIConstants.Background.INPUT();
        }
        graphics.fill(x, y, x + w, y + h, bgColor);

        // Left accent bar if selected
        if (selected) {
            graphics.fill(x, y, x + 3, y + h, accentColor);
        }

        // Border
        int borderColor = selected ? accentColor : (hovered ? UIConstants.Border.ACCENT() : UIConstants.Border.DEFAULT());
        AxiomRenderer.drawBorder(graphics, x, y, w, h, borderColor);

        // Label
        int textX = x + 8;
        graphics.drawString(safeFont, Objects.requireNonNull(label, "label"),
            textX, y + 4, selected ? UIConstants.Text.PRIMARY() : UIConstants.Text.SECONDARY(), false);

        // Description (smaller)
        graphics.drawString(safeFont, Objects.requireNonNull(desc, "description"),
            textX, y + 13, UIConstants.Text.MUTED(), false);
    }

    private void renderToggleRow(GuiGraphics graphics, @Nonnull Font font, int x, int y, int width,
                                  String label, String description, boolean enabled, int mouseX, int mouseY) {
        boolean rowHovered = isMouseOver(mouseX, mouseY, x, y, width, ROW_HEIGHT);

        if (rowHovered) {
            graphics.fill(x, y, x + width, y + ROW_HEIGHT, UIConstants.Background.HOVER());
        }

        graphics.drawString(font, label, x, y + 4, UIConstants.Text.PRIMARY(), false);
        graphics.drawString(font, description, x, y + 14, UIConstants.Text.MUTED(), false);

        int toggleX = x + width - UIConstants.Size.TOGGLE_WIDTH;
        AxiomRenderer.drawToggle(graphics, font, toggleX, y, UIConstants.Size.TOGGLE_WIDTH, UIConstants.Size.TOGGLE_HEIGHT, enabled, rowHovered);
    }

    private String getScaleLabel(EditorClientConfig.EditorUiScale scale) {
        return switch (scale) {
            case AUTO -> "Auto";
            case SCALE_1_0 -> "1.0x";
            case SCALE_1_25 -> "1.25x";
            case SCALE_1_5 -> "1.5x";
            case SCALE_2_0 -> "2.0x";
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int contentX, int contentY, int contentWidth) {
        if (button != 0) return false;

        int currentY = contentY;

        // Skip section header
        currentY += ROW_HEIGHT;

        // Skip description
        currentY += 14;

        // Scale buttons
        int buttonX = contentX;
        for (EditorClientConfig.EditorUiScale scale : SCALE_OPTIONS) {
            if (isMouseOver((int) mouseX, (int) mouseY, buttonX, currentY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                currentUiScale = scale;
                return true;
            }
            buttonX += BUTTON_WIDTH + BUTTON_GAP;
        }
        currentY += BUTTON_HEIGHT + 8;

        // Scale info row + preview
        currentY += ROW_HEIGHT + UIConstants.Spacing.GAP_SMALL;
        currentY += PREVIEW_HEIGHT + UIConstants.Spacing.GAP_LARGE;

        // Separator
        currentY += 16;

        // Section header (Default Mode)
        currentY += ROW_HEIGHT;

        // Description
        currentY += 14;

        // Preview button
        if (isMouseOver((int) mouseX, (int) mouseY, contentX, currentY, 100, BUTTON_HEIGHT)) {
            currentDefaultMode = EditorClientConfig.EditorDefaultMode.PREVIEW;
            return true;
        }

        // Apply button
        if (isMouseOver((int) mouseX, (int) mouseY, contentX + 110, currentY, 100, BUTTON_HEIGHT)) {
            currentDefaultMode = EditorClientConfig.EditorDefaultMode.APPLY;
            return true;
        }
        currentY += BUTTON_HEIGHT + 16;

        // Separator
        currentY += 16;

        // Section header (Sound Effects)
        currentY += ROW_HEIGHT;

        // Sound toggle row
        if (isMouseOver((int) mouseX, (int) mouseY, contentX, currentY, contentWidth, ROW_HEIGHT)) {
            currentSoundsEnabled = !currentSoundsEnabled;
            return true;
        }

        return false;
    }

    @Override
    public boolean hasUnsavedChanges() {
        return currentUiScale != originalUiScale ||
               currentDefaultMode != originalDefaultMode ||
               currentSoundsEnabled != originalSoundsEnabled;
    }

    @Override
    public void saveChanges() {
        try {
            EditorClientConfig.EDITOR_UI_SCALE.set(Objects.requireNonNull(currentUiScale));
            EditorClientConfig.EDITOR_DEFAULT_MODE.set(Objects.requireNonNull(currentDefaultMode));
            EditorClientConfig.EDITOR_SOUNDS_ENABLED.set(currentSoundsEnabled);

            originalUiScale = currentUiScale;
            originalDefaultMode = currentDefaultMode;
            originalSoundsEnabled = currentSoundsEnabled;
        } catch (Exception e) {
            // Config may be read-only
        }
    }

    @Override
    public void resetToDefaults() {
        currentUiScale = EditorClientConfig.EditorUiScale.AUTO;
        currentDefaultMode = EditorClientConfig.EditorDefaultMode.PREVIEW;
        currentSoundsEnabled = true;
    }

    @Override
    public int getContentHeight() {
        return 280 + PREVIEW_HEIGHT + UIConstants.Spacing.GAP_LARGE;
    }

    private boolean isMouseOver(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
