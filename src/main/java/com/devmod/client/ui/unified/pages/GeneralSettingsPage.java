package com.devmod.client.ui.unified.pages;

import javax.annotation.Nonnull;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import com.devmod.ModConfig;
import com.devmod.client.overlay.OnboardingOverlay;
import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.editor.core.ThemeManager;
import com.devmod.client.ui.scroll.Scrollbar;
import com.devmod.client.ui.unified.SettingsCategory;
import com.devmod.client.ui.unified.SettingsPage;
import com.devmod.client.ui.unified.persistence.SettingsManager;
import com.devmod.util.I18n;

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

    // UI constants
    private static final int ROW_HEIGHT = 24;
    private static final int COLOR_PREVIEW_SIZE = 16;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int HINT_HEIGHT = 20;

    // Scroll state
    private int scrollOffset = 0;
    private int maxScrollOffset = 0;
    private int visibleHeight = 0;
    private int totalContentHeight = 0;
    private boolean isDraggingScrollbar = false;
    private boolean showScrollbar = false;
    private int lastContentX, lastContentY, lastContentWidth, lastContentHeight;

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
        lastContentX = x;
        lastContentY = y;
        lastContentWidth = width;
        lastContentHeight = height;
        visibleHeight = height;

        totalContentHeight = calculateContentHeight();
        maxScrollOffset = Math.max(0, totalContentHeight - visibleHeight);
        showScrollbar = totalContentHeight > visibleHeight;
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));

        int effectiveWidth = showScrollbar ? Math.max(0, width - SCROLLBAR_WIDTH - 4) : width;

        graphics.enableScissor(x, y, x + width, y + height);
        try {
            int currentY = y - scrollOffset;

            // === SECTION: Visibility ===
            AxiomRenderer.drawSectionHeader(graphics, font, x, currentY, "Visibility");
            currentY += ROW_HEIGHT;

            // Show Overlay HUD toggle
            renderToggleRow(graphics, font, x, currentY, effectiveWidth, "Show Overlay HUD",
                    "Displays damage info and stats on screen", showOverlay, mouseX, mouseY);
            currentY += ROW_HEIGHT;

            // Show World Render toggle
            renderToggleRow(graphics, font, x, currentY, effectiveWidth, "Show World Render",
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
            renderToggleRow(graphics, font, x, currentY, effectiveWidth, "Render as Block Grid",
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
            graphics.drawString(font, "Current:", x, currentY + 4, DesignTokens.Text.SECONDARY, false);

            // Color preview square
            int previewX = x + 60;
            graphics.fill(previewX, currentY, previewX + COLOR_PREVIEW_SIZE, currentY + COLOR_PREVIEW_SIZE, followRangeColor);
            AxiomRenderer.drawBorder(graphics, previewX, currentY, COLOR_PREVIEW_SIZE, COLOR_PREVIEW_SIZE, DesignTokens.Stroke.DEFAULT);

            // Color name
            String colorName = getColorName(followRangeColor);
            graphics.drawString(font, colorName, previewX + COLOR_PREVIEW_SIZE + 8, currentY + 4, DesignTokens.Text.PRIMARY, false);
            currentY += ROW_HEIGHT + 4;

            // Color presets
            graphics.drawString(font, "Presets:", x, currentY + 4, DesignTokens.Text.SECONDARY, false);
            int colorX = x + 60;
            for (int i = 0; i < COLOR_PRESETS.length; i++) {
                boolean selected = followRangeColor == COLOR_PRESETS[i];
                boolean hovered = isMouseOver(mouseX, mouseY, colorX, currentY, COLOR_PREVIEW_SIZE + 4, COLOR_PREVIEW_SIZE + 4);

                // Background for selected
                if (selected) {
                    graphics.fill(colorX - 2, currentY - 2, colorX + COLOR_PREVIEW_SIZE + 2, currentY + COLOR_PREVIEW_SIZE + 2,
                            DesignTokens.Surface.LEVEL_2);
                } else if (hovered) {
                    graphics.fill(colorX - 2, currentY - 2, colorX + COLOR_PREVIEW_SIZE + 2, currentY + COLOR_PREVIEW_SIZE + 2,
                            DesignTokens.Surface.LEVEL_1);
                }

                // Color square
                graphics.fill(colorX, currentY, colorX + COLOR_PREVIEW_SIZE, currentY + COLOR_PREVIEW_SIZE, COLOR_PRESETS[i]);
                int borderColor = selected ? DesignTokens.Accent.PRIMARY : DesignTokens.Stroke.DEFAULT;
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
            drawActionButton(graphics, font, x, currentY, btnWidth, btnHeight, "Replay Tutorial", btnHovered, DesignTokens.Semantic.INFO);

            // Description
            graphics.drawString(font, "Restart the interactive guide", x + btnWidth + 12, currentY + 6, DesignTokens.Text.MUTED, false);
            currentY += ROW_HEIGHT + 8;

            // Separator
            AxiomRenderer.drawSeparator(graphics, x, currentY, width);
            currentY += 16;

            // === SECTION: Accessibility ===
            AxiomRenderer.drawSectionHeader(graphics, font, x, currentY, "Accessibility");
            currentY += ROW_HEIGHT;

            // High Contrast toggle
            boolean highContrast = ThemeManager.INSTANCE.isHighContrast();
            renderToggleRow(graphics, font, x, currentY, effectiveWidth, "High Contrast Mode",
                    "Maximum visibility with bright colors", highContrast, mouseX, mouseY);
            currentY += ROW_HEIGHT;

            // Theme cycle button
            btnHovered = isMouseOver(mouseX, mouseY, x, currentY, btnWidth, btnHeight);
            String themeName = ThemeManager.INSTANCE.current().getName();
            drawActionButton(graphics, font, x, currentY, btnWidth, btnHeight, "Theme: " + themeName, btnHovered, DesignTokens.Semantic.INFO);
            graphics.drawString(font, "Cycle: Dark → Light → High Contrast", x + btnWidth + 12, currentY + 6, DesignTokens.Text.MUTED, false);
            currentY += ROW_HEIGHT + 8;

            // Hint
            AxiomRenderer.drawHint(graphics, font, x, currentY, "Changes are applied immediately. Press K to close.");
        } finally {
            graphics.disableScissor();
        }

        if (showScrollbar) {
            renderScrollbar(graphics, x + width - SCROLLBAR_WIDTH - 2, y, SCROLLBAR_WIDTH, height);
        }
    }

    /**
     * Draws a styled action button.
     */
    private void drawActionButton(GuiGraphics graphics, @Nonnull Font font, int x, int y, int w, int h,
                                   @Nonnull String text, boolean hovered, int accentColor) {
        // Background
        int bgColor = hovered ? DesignTokens.withAlpha(accentColor, 60) : DesignTokens.Bg.LEVEL_0;
        graphics.fill(x, y, x + w, y + h, bgColor);

        // Accent bar on left
        graphics.fill(x, y, x + 3, y + h, accentColor);

        // Border
        int borderColor = hovered ? accentColor : DesignTokens.Stroke.MUTED;
        AxiomRenderer.drawBorder(graphics, x, y, w, h, borderColor);

        // Text centered
        int textW = font.width(text);
        int textX = x + (w - textW) / 2;
        int textY = y + (h - 8) / 2;
        graphics.drawString(font, text, textX, textY, hovered ? 0xFFFFFFFF : DesignTokens.Text.PRIMARY, false);
    }

    private void renderToggleRow(GuiGraphics graphics, @Nonnull Font font, int x, int y, int width,
                                  String label, String description, boolean enabled, int mouseX, int mouseY) {
        // Check if mouse is over entire row (for visual feedback)
        boolean rowHovered = isMouseOver(mouseX, mouseY, x, y, width, ROW_HEIGHT);

        // Draw subtle hover background for entire row
        if (rowHovered) {
            graphics.fill(x, y, x + width, y + ROW_HEIGHT, DesignTokens.Surface.LEVEL_1);
        }

        // Label
        graphics.drawString(font, label, x, y + 4, DesignTokens.Text.PRIMARY, false);

        // Description (smaller, muted)
        graphics.drawString(font, description, x, y + 14, DesignTokens.Text.MUTED, false);

        // Toggle on right - standardized dimensions (36x18)
        int toggleWidth = 36;
        int toggleHeight = 18;
        int toggleX = x + width - toggleWidth;
        AxiomRenderer.drawToggle(graphics, font, toggleX, y, toggleWidth, toggleHeight, enabled, rowHovered);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int contentX, int contentY, int contentWidth) {
        if (button != 0 || !isMouseOverContent(mouseX, mouseY)) return false;

        if (showScrollbar) {
            int scrollbarX = contentX + contentWidth - SCROLLBAR_WIDTH - 2;
            if (mouseX >= scrollbarX && mouseX <= scrollbarX + SCROLLBAR_WIDTH + 2) {
                isDraggingScrollbar = true;
                float visibleRatio = (float) visibleHeight / totalContentHeight;
                int thumbHeight = Math.max(20, (int) (lastContentHeight * visibleRatio));
                int trackHeight = lastContentHeight - thumbHeight;
                if (trackHeight > 0) {
                    float clickRatio = (float) (mouseY - contentY - thumbHeight / 2.0f) / trackHeight;
                    clickRatio = Math.max(0, Math.min(1, clickRatio));
                    scrollOffset = (int)(maxScrollOffset * clickRatio);
                }
                return true;
            }
        }

        int effectiveWidth = showScrollbar ? Math.max(0, contentWidth - SCROLLBAR_WIDTH - 4) : contentWidth;
        int currentY = contentY - scrollOffset;

        // Skip section header
        currentY += ROW_HEIGHT;

        // Show Overlay toggle - click anywhere on the row
        if (isMouseOver((int) mouseX, (int) mouseY, contentX, currentY, effectiveWidth, ROW_HEIGHT)) {
            showOverlay = !showOverlay;
            ModConfig.showOverlay = showOverlay;
            return true;
        }
        currentY += ROW_HEIGHT;

        // Show World Render toggle - click anywhere on the row
        if (isMouseOver((int) mouseX, (int) mouseY, contentX, currentY, effectiveWidth, ROW_HEIGHT)) {
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
        if (isMouseOver((int) mouseX, (int) mouseY, contentX, currentY, effectiveWidth, ROW_HEIGHT)) {
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
        currentY += ROW_HEIGHT + 8;

        // Separator + Accessibility section header
        currentY += 16 + ROW_HEIGHT;

        // High Contrast toggle - click anywhere on the row
        if (isMouseOver((int) mouseX, (int) mouseY, contentX, currentY, effectiveWidth, ROW_HEIGHT)) {
            ThemeManager.INSTANCE.setHighContrast(!ThemeManager.INSTANCE.isHighContrast());
            return true;
        }
        currentY += ROW_HEIGHT;

        // Theme cycle button
        if (isMouseOver((int) mouseX, (int) mouseY, contentX, currentY, btnWidth, btnHeight)) {
            ThemeManager.INSTANCE.cycleTheme();
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!showScrollbar || maxScrollOffset <= 0 || !isMouseOverContent(mouseX, mouseY)) {
            return false;
        }
        scrollOffset -= (int) (scrollY * 20);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDraggingScrollbar) {
            isDraggingScrollbar = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && isDraggingScrollbar && maxScrollOffset > 0) {
            float visibleRatio = (float) visibleHeight / totalContentHeight;
            int thumbHeight = Math.max(20, (int) (lastContentHeight * visibleRatio));
            int trackHeight = lastContentHeight - thumbHeight;
            if (trackHeight > 0) {
                float dragRatio = (float) (mouseY - lastContentY - thumbHeight / 2.0f) / trackHeight;
                dragRatio = Math.max(0, Math.min(1, dragRatio));
                scrollOffset = (int)(maxScrollOffset * dragRatio);
            }
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
        return calculateContentHeight();
    }

    private int calculateContentHeight() {
        int height = 0;
        // Visibility
        height += ROW_HEIGHT;
        height += ROW_HEIGHT * 2;
        height += 8 + 16;
        // Render Mode
        height += ROW_HEIGHT;
        height += ROW_HEIGHT;
        height += 8 + 16;
        // Colors
        height += ROW_HEIGHT;
        height += ROW_HEIGHT + 4;
        height += ROW_HEIGHT + 8;
        height += 16;
        // Tutorial
        height += ROW_HEIGHT;
        height += ROW_HEIGHT + 8;
        height += 16;
        // Accessibility
        height += ROW_HEIGHT;
        height += ROW_HEIGHT;
        height += ROW_HEIGHT + 8;
        // Hint
        height += HINT_HEIGHT;
        return height;
    }

    private boolean isMouseOver(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void renderScrollbar(GuiGraphics graphics, int x, int y, int barWidth, int height) {
        // Delegate to unified Scrollbar helper
        Scrollbar.render(graphics, x, y, barWidth, height,
            scrollOffset, totalContentHeight, visibleHeight,
            false, isDraggingScrollbar);
    }

    private boolean isMouseOverContent(double mouseX, double mouseY) {
        return mouseX >= lastContentX && mouseX <= lastContentX + lastContentWidth
            && mouseY >= lastContentY && mouseY <= lastContentY + lastContentHeight;
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
