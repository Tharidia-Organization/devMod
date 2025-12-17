package com.frenkvs.devmod.ui.unified.pages;

import com.frenkvs.devmod.ModConfig;
import com.frenkvs.devmod.hud.OnboardingOverlay;
import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.UIConstants;
import com.frenkvs.devmod.ui.unified.SettingsCategory;
import com.frenkvs.devmod.ui.unified.SettingsPage;
import com.frenkvs.devmod.ui.unified.persistence.SettingsManager;
import com.frenkvs.devmod.util.I18n;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.Nonnull;

/**
 * General settings page with toggle controls for overlay visibility, render mode, and colors.
 * Integrates with ModConfig for persistent settings.
 */
public class GeneralSettingsPage implements SettingsPage {

    // Track original values for unsaved changes detection
    private boolean originalShowOverlay;
    private boolean originalShowRender;
    private boolean originalRenderAsBlocks;
    private int originalFollowRangeColor;

    // Current values (may differ from originals if changed)
    private boolean showOverlay;
    private boolean showRender;
    private boolean renderAsBlocks;
    private int followRangeColor;

    // UI constants - use UIConstants.Size for toggle dimensions
    private static final int ROW_HEIGHT = 24;
    private static final int COLOR_PREVIEW_SIZE = 16;

    // Color presets
    private static final int[] COLOR_PRESETS = {
            0xFFFF0000, // Red
            0xFFFFFF00, // Yellow
            0xFF00FF00, // Green
            0xFF00FFFF, // Cyan
            0xFF0000FF  // Blue
    };
    private static final String[] COLOR_NAMES = {"Red", "Yellow", "Green", "Cyan", "Blue"};

    @Override
    public SettingsCategory getCategory() {
        return SettingsCategory.GENERAL;
    }

    @Override
    public String getTitle() {
        return "General Settings";
    }

    @Override
    public void init() {
        // Load current values from ModConfig
        originalShowOverlay = ModConfig.showOverlay;
        originalShowRender = ModConfig.showRender;
        originalRenderAsBlocks = ModConfig.renderAsBlocks;
        originalFollowRangeColor = ModConfig.followRangeColor;

        // Copy to current values
        showOverlay = originalShowOverlay;
        showRender = originalShowRender;
        renderAsBlocks = originalRenderAsBlocks;
        followRangeColor = originalFollowRangeColor;
    }

    @Override
    public void render(GuiGraphics graphics, @Nonnull Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
        int currentY = y;

        // === SECTION: Visibility ===
        AxiomRenderer.drawSectionHeader(graphics, font, x, currentY, "Visibility");
        currentY += ROW_HEIGHT;

        // Show Overlay HUD toggle
        renderToggleRow(graphics, font, x, currentY, width, "Show Overlay HUD",
                "Displays damage info and stats on screen", showOverlay, mouseX, mouseY);
        currentY += ROW_HEIGHT;

        // Show World Render toggle
        renderToggleRow(graphics, font, x, currentY, width, "Show World Render",
                "Displays circles/blocks in the world", showRender, mouseX, mouseY);
        currentY += ROW_HEIGHT;

        // Separator
        currentY += 8;
        AxiomRenderer.drawSeparator(graphics, x, currentY, width);
        currentY += 16;

        // === SECTION: Render Mode ===
        AxiomRenderer.drawSectionHeader(graphics, font, x, currentY, "Render Mode");
        currentY += ROW_HEIGHT;

        // Render as Blocks toggle
        renderToggleRow(graphics, font, x, currentY, width, "Render as Block Grid",
                "ON: Heavy block grid | OFF: Light circle lines", renderAsBlocks, mouseX, mouseY);
        currentY += ROW_HEIGHT;

        // Separator
        currentY += 8;
        AxiomRenderer.drawSeparator(graphics, x, currentY, width);
        currentY += 16;

        // === SECTION: Colors ===
        AxiomRenderer.drawSectionHeader(graphics, font, x, currentY, "Follow Range Color");
        currentY += ROW_HEIGHT;

        // Current color preview
        graphics.drawString(font, "Current:", x, currentY + 4, UIConstants.Text.SECONDARY(), false);

        // Color preview square
        int previewX = x + 60;
        graphics.fill(previewX, currentY, previewX + COLOR_PREVIEW_SIZE, currentY + COLOR_PREVIEW_SIZE, followRangeColor);
        AxiomRenderer.drawBorder(graphics, previewX, currentY, COLOR_PREVIEW_SIZE, COLOR_PREVIEW_SIZE, UIConstants.Border.DEFAULT());

        // Color name
        String colorName = getColorName(followRangeColor);
        graphics.drawString(font, colorName, previewX + COLOR_PREVIEW_SIZE + 8, currentY + 4, UIConstants.Text.PRIMARY(), false);
        currentY += ROW_HEIGHT + 4;

        // Color presets
        graphics.drawString(font, "Presets:", x, currentY + 4, UIConstants.Text.SECONDARY(), false);
        int colorX = x + 60;
        for (int i = 0; i < COLOR_PRESETS.length; i++) {
            boolean selected = followRangeColor == COLOR_PRESETS[i];
            boolean hovered = isMouseOver(mouseX, mouseY, colorX, currentY, COLOR_PREVIEW_SIZE + 4, COLOR_PREVIEW_SIZE + 4);

            // Background for selected
            if (selected) {
                graphics.fill(colorX - 2, currentY - 2, colorX + COLOR_PREVIEW_SIZE + 2, currentY + COLOR_PREVIEW_SIZE + 2,
                        UIConstants.Background.ACTIVE());
            } else if (hovered) {
                graphics.fill(colorX - 2, currentY - 2, colorX + COLOR_PREVIEW_SIZE + 2, currentY + COLOR_PREVIEW_SIZE + 2,
                        UIConstants.Background.HOVER());
            }

            // Color square
            graphics.fill(colorX, currentY, colorX + COLOR_PREVIEW_SIZE, currentY + COLOR_PREVIEW_SIZE, COLOR_PRESETS[i]);
            int borderColor = selected ? UIConstants.Border.ACCENT() : UIConstants.Border.DEFAULT();
            AxiomRenderer.drawBorder(graphics, colorX, currentY, COLOR_PREVIEW_SIZE, COLOR_PREVIEW_SIZE, borderColor);

            colorX += COLOR_PREVIEW_SIZE + 8;
        }
        currentY += ROW_HEIGHT + 8;

        // Separator
        AxiomRenderer.drawSeparator(graphics, x, currentY, width);
        currentY += 16;

        // === SECTION: Tutorial ===
        AxiomRenderer.drawSectionHeader(graphics, font, x, currentY, "Tutorial");
        currentY += ROW_HEIGHT;

        // Replay Tutorial button
        int btnWidth = 140;
        int btnHeight = 22;
        boolean btnHovered = isMouseOver(mouseX, mouseY, x, currentY, btnWidth, btnHeight);
        drawActionButton(graphics, font, x, currentY, btnWidth, btnHeight, "Replay Tutorial", btnHovered, UIConstants.Accent.BLUE());

        // Description
        graphics.drawString(font, "Restart the interactive guide", x + btnWidth + 12, currentY + 6, UIConstants.Text.MUTED(), false);
        currentY += ROW_HEIGHT + 8;

        // Hint
        AxiomRenderer.drawHint(graphics, font, x, currentY, "Changes are applied immediately. Press K to close.");
    }

    /**
     * Draws a styled action button.
     */
    private void drawActionButton(GuiGraphics graphics, @Nonnull Font font, int x, int y, int w, int h,
                                   @Nonnull String text, boolean hovered, int accentColor) {
        // Background
        int bgColor = hovered ? UIConstants.setAlpha(accentColor, 60) : UIConstants.Background.INPUT();
        graphics.fill(x, y, x + w, y + h, bgColor);

        // Accent bar on left
        graphics.fill(x, y, x + 3, y + h, accentColor);

        // Border
        int borderColor = hovered ? accentColor : UIConstants.Border.MUTED();
        AxiomRenderer.drawBorder(graphics, x, y, w, h, borderColor);

        // Text centered
        int textW = font.width(text);
        int textX = x + (w - textW) / 2;
        int textY = y + (h - 8) / 2;
        graphics.drawString(font, text, textX, textY, hovered ? UIConstants.Text.WHITE() : UIConstants.Text.PRIMARY(), false);
    }

    private void renderToggleRow(GuiGraphics graphics, @Nonnull Font font, int x, int y, int width,
                                  String label, String description, boolean enabled, int mouseX, int mouseY) {
        // Check if mouse is over entire row (for visual feedback)
        boolean rowHovered = isMouseOver(mouseX, mouseY, x, y, width, ROW_HEIGHT);

        // Draw subtle hover background for entire row
        if (rowHovered) {
            graphics.fill(x, y, x + width, y + ROW_HEIGHT, UIConstants.Background.HOVER());
        }

        // Label
        graphics.drawString(font, label, x, y + 4, UIConstants.Text.PRIMARY(), false);

        // Description (smaller, muted)
        graphics.drawString(font, description, x, y + 14, UIConstants.Text.MUTED(), false);

        // Toggle on right - use standardized dimensions from UIConstants
        int toggleX = x + width - UIConstants.Size.TOGGLE_WIDTH;
        AxiomRenderer.drawToggle(graphics, font, toggleX, y, UIConstants.Size.TOGGLE_WIDTH, UIConstants.Size.TOGGLE_HEIGHT, enabled, rowHovered);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int contentX, int contentY, int contentWidth) {
        if (button != 0) return false;

        int currentY = contentY;

        // Skip section header
        currentY += ROW_HEIGHT;

        // Show Overlay toggle - click anywhere on the row
        if (isMouseOver((int) mouseX, (int) mouseY, contentX, currentY, contentWidth, ROW_HEIGHT)) {
            showOverlay = !showOverlay;
            ModConfig.showOverlay = showOverlay;
            return true;
        }
        currentY += ROW_HEIGHT;

        // Show World Render toggle - click anywhere on the row
        if (isMouseOver((int) mouseX, (int) mouseY, contentX, currentY, contentWidth, ROW_HEIGHT)) {
            showRender = !showRender;
            ModConfig.showRender = showRender;
            return true;
        }
        currentY += ROW_HEIGHT;

        // Separator space
        currentY += 8 + 16;

        // Skip section header
        currentY += ROW_HEIGHT;

        // Render as Blocks toggle - click anywhere on the row
        if (isMouseOver((int) mouseX, (int) mouseY, contentX, currentY, contentWidth, ROW_HEIGHT)) {
            renderAsBlocks = !renderAsBlocks;
            ModConfig.renderAsBlocks = renderAsBlocks;
            return true;
        }
        currentY += ROW_HEIGHT;

        // Separator space
        currentY += 8 + 16;

        // Skip section header
        currentY += ROW_HEIGHT;

        // Skip "Current:" row
        currentY += ROW_HEIGHT + 4;

        // Color presets
        int colorX = contentX + 60;
        for (int i = 0; i < COLOR_PRESETS.length; i++) {
            if (isMouseOver((int) mouseX, (int) mouseY, colorX, currentY, COLOR_PREVIEW_SIZE + 4, COLOR_PREVIEW_SIZE + 4)) {
                followRangeColor = COLOR_PRESETS[i];
                ModConfig.followRangeColor = followRangeColor;
                return true;
            }
            colorX += COLOR_PREVIEW_SIZE + 8;
        }
        currentY += ROW_HEIGHT + 8;

        // Separator + Tutorial section header
        currentY += 16 + ROW_HEIGHT;

        // Replay Tutorial button
        int btnWidth = 140;
        int btnHeight = 22;
        if (isMouseOver((int) mouseX, (int) mouseY, contentX, currentY, btnWidth, btnHeight)) {
            replayTutorial();
            return true;
        }

        return false;
    }

    /**
     * Resets tutorial state and starts the onboarding overlay.
     */
    private void replayTutorial() {
        // Reset onboarding flags
        var settings = SettingsManager.INSTANCE.getSettings();
        settings.onboarding.tutorialCompleted = false;
        settings.onboarding.hasSeenWelcome = true; // Keep this true to skip welcome screen
        SettingsManager.INSTANCE.markDirty();

        // Close current screen and start onboarding
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(
                I18n.translate("devmod.message.tutorial_restarted"),
                false
            );
        }

        // Activate the onboarding overlay
        OnboardingOverlay.start();

        // Close settings screen
        mc.setScreen(null);
    }

    @Override
    public boolean hasUnsavedChanges() {
        return showOverlay != originalShowOverlay ||
               showRender != originalShowRender ||
               renderAsBlocks != originalRenderAsBlocks ||
               followRangeColor != originalFollowRangeColor;
    }

    @Override
    public void saveChanges() {
        // Changes are applied immediately to ModConfig, so just update originals
        originalShowOverlay = showOverlay;
        originalShowRender = showRender;
        originalRenderAsBlocks = renderAsBlocks;
        originalFollowRangeColor = followRangeColor;
    }

    @Override
    public void resetToDefaults() {
        showOverlay = true;
        showRender = false;
        renderAsBlocks = false;
        followRangeColor = 0xFFFF0000; // Red

        // Apply to ModConfig
        ModConfig.showOverlay = showOverlay;
        ModConfig.showRender = showRender;
        ModConfig.renderAsBlocks = renderAsBlocks;
        ModConfig.followRangeColor = followRangeColor;
    }

    @Override
    public int getContentHeight() {
        return 360; // Increased to accommodate Tutorial section
    }

    private boolean isMouseOver(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private String getColorName(int color) {
        for (int i = 0; i < COLOR_PRESETS.length; i++) {
            if (COLOR_PRESETS[i] == color) {
                return COLOR_NAMES[i];
            }
        }
        return String.format("#%06X", color & 0xFFFFFF);
    }
}
