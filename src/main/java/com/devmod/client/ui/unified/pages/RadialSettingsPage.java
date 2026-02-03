package com.devmod.client.ui.unified.pages;

import java.util.Locale;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.radial.RadialMenuConfig;
import com.devmod.client.ui.unified.SettingsPage;
import com.devmod.util.I18n;

public class RadialSettingsPage implements SettingsPage {

    // UI constants
    private static final int ROW_HEIGHT = 24;
    private static final int SCROLLBAR_WIDTH = 6;

    // Config values
    private boolean releaseToSelect;
    private boolean rightClickToEdit;
    private boolean enableAnimations;
    private boolean reducedMotion;
    private boolean enableSounds;
    private boolean showTooltips;
    private boolean closeOnToggle;
    private boolean showKeyHints;
    private boolean safeMode;
    private boolean useUsageOrdering;
    private RadialMenuConfig.MenuProfile menuProfile = RadialMenuConfig.MenuProfile.ALL;
    private String themePreset = "default";

    // Original values for dirty check
    private boolean originalReleaseToSelect;
    private boolean originalRightClickToEdit;
    private boolean originalEnableAnimations;
    private boolean originalReducedMotion;
    private boolean originalEnableSounds;
    private boolean originalShowTooltips;
    private boolean originalCloseOnToggle;
    private boolean originalShowKeyHints;
    private boolean originalSafeMode;
    private boolean originalUseUsageOrdering;
    private RadialMenuConfig.MenuProfile originalMenuProfile = RadialMenuConfig.MenuProfile.ALL;
    private String originalThemePreset = "default";

    // Scroll state
    private int scrollOffset = 0;
    private int maxScrollOffset = 0;
    private int visibleHeight = 0;
    private int totalContentHeight = 0;
    private boolean isDraggingScrollbar = false;
    private boolean showScrollbar = false;
    private int lastContentX, lastContentY, lastContentWidth, lastContentHeight;

    private final RadialMenuConfig config = RadialMenuConfig.INSTANCE;

    @Override
    public String getTitle() {
        return "Radial Menu";
    }

    @Override
    public void init() {
        config.load();

        // Load current values
        releaseToSelect = config.releaseToSelect;
        rightClickToEdit = config.rightClickToEdit;
        enableAnimations = config.enableAnimations;
        reducedMotion = config.reducedMotion;
        enableSounds = config.enableSounds;
        showTooltips = config.showTooltips;
        closeOnToggle = config.closeOnToggle;
        showKeyHints = config.showKeyHints;
        safeMode = config.safeMode;
        useUsageOrdering = config.useUsageOrdering;
        menuProfile = config.menuProfile != null ? config.menuProfile : RadialMenuConfig.MenuProfile.ALL;
        themePreset = config.theme != null ? config.theme.presetName : "default";

        // Store originals
        originalReleaseToSelect = releaseToSelect;
        originalRightClickToEdit = rightClickToEdit;
        originalEnableAnimations = enableAnimations;
        originalReducedMotion = reducedMotion;
        originalEnableSounds = enableSounds;
        originalShowTooltips = showTooltips;
        originalCloseOnToggle = closeOnToggle;
        originalShowKeyHints = showKeyHints;
        originalSafeMode = safeMode;
        originalUseUsageOrdering = useUsageOrdering;
        originalMenuProfile = menuProfile;
        originalThemePreset = themePreset;
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, @Nonnull Font font, int x, int y, int width, int height,
                       int mouseX, int mouseY) {
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

            // === SECTION: Behavior ===
            AxiomRenderer.drawSectionHeader(graphics, font, x, currentY, "Behavior");
            currentY += ROW_HEIGHT;

            renderToggleRow(graphics, font, x, currentY, effectiveWidth,
                "Release to Select", "Release key to activate selection", releaseToSelect, mouseX, mouseY);
            currentY += ROW_HEIGHT;

            renderToggleRow(graphics, font, x, currentY, effectiveWidth,
                "Close on Toggle", "Close menu after toggling an item", closeOnToggle, mouseX, mouseY);
            currentY += ROW_HEIGHT;

            renderToggleRow(graphics, font, x, currentY, effectiveWidth,
                "Right Click Details", "Right-click opens details", rightClickToEdit, mouseX, mouseY);
            currentY += ROW_HEIGHT;

            currentY += 8;
            AxiomRenderer.drawSeparator(graphics, x, currentY, width);
            currentY += 16;

            // === SECTION: Feedback ===
            AxiomRenderer.drawSectionHeader(graphics, font, x, currentY, "Feedback");
            currentY += ROW_HEIGHT;

            renderToggleRow(graphics, font, x, currentY, effectiveWidth,
                "Animations", "Enable smooth animations", enableAnimations, mouseX, mouseY);
            currentY += ROW_HEIGHT;

            renderToggleRow(graphics, font, x, currentY, effectiveWidth,
                "Reduced Motion", "Reduce or disable motion effects", reducedMotion, mouseX, mouseY);
            currentY += ROW_HEIGHT;

            renderToggleRow(graphics, font, x, currentY, effectiveWidth,
                "Sounds", "Enable feedback sounds", enableSounds, mouseX, mouseY);
            currentY += ROW_HEIGHT;

            renderToggleRow(graphics, font, x, currentY, effectiveWidth,
                "Tooltips", "Show item descriptions", showTooltips, mouseX, mouseY);
            currentY += ROW_HEIGHT;

            renderToggleRow(graphics, font, x, currentY, effectiveWidth,
                "Key Hints", "Show keyboard shortcuts", showKeyHints, mouseX, mouseY);
            currentY += ROW_HEIGHT;

            currentY += 8;
            AxiomRenderer.drawSeparator(graphics, x, currentY, width);
            currentY += 16;

            // === SECTION: Safety & Ordering ===
            AxiomRenderer.drawSectionHeader(graphics, font, x, currentY, "Safety & Ordering");
            currentY += ROW_HEIGHT;

            renderToggleRow(graphics, font, x, currentY, effectiveWidth,
                "Safe Mode", "Hide risky actions", safeMode, mouseX, mouseY);
            currentY += ROW_HEIGHT;

            renderToggleRow(graphics, font, x, currentY, effectiveWidth,
                "Usage Ordering", "Sort items by usage", useUsageOrdering, mouseX, mouseY);
            currentY += ROW_HEIGHT;

            currentY += 8;
            AxiomRenderer.drawSeparator(graphics, x, currentY, width);
            currentY += 16;

            // === SECTION: Profiles & Theme ===
            AxiomRenderer.drawSectionHeader(graphics, font, x, currentY, "Profiles & Theme");
            currentY += ROW_HEIGHT;

            renderValueRow(graphics, font, x, currentY, effectiveWidth,
                "Menu Profile", "Cycle player/dev/all", profileLabel(menuProfile), mouseX, mouseY);
            currentY += ROW_HEIGHT;

            renderValueRow(graphics, font, x, currentY, effectiveWidth,
                "Theme Preset", "Cycle radial theme", themePreset, mouseX, mouseY);
        } finally {
            graphics.disableScissor();
        }

        if (showScrollbar) {
            renderScrollbar(graphics, x + width - SCROLLBAR_WIDTH - 2, y, height);
        }
    }

    private void renderToggleRow(GuiGraphics graphics, @Nonnull Font font, int x, int y, int width,
                                 String label, String description, boolean enabled, int mouseX, int mouseY) {
        boolean rowHovered = isMouseOver(mouseX, mouseY, x, y, width, ROW_HEIGHT);
        if (rowHovered) {
            graphics.fill(x, y, x + width, y + ROW_HEIGHT, DesignTokens.Surface.LEVEL_1);
        }
        UIScaleManager.drawScaledString(graphics, font, label, x, y + 4, DesignTokens.Text.PRIMARY, false);
        UIScaleManager.drawScaledString(graphics, font, description, x, y + 14, DesignTokens.Text.MUTED, false);
        int toggleWidth = 36;
        int toggleHeight = 18;
        int toggleX = x + width - toggleWidth;
        AxiomRenderer.drawToggle(graphics, font, toggleX, y, toggleWidth, toggleHeight, enabled, rowHovered);
    }

    private void renderValueRow(GuiGraphics graphics, @Nonnull Font font, int x, int y, int width,
                                String label, String description, String value, int mouseX, int mouseY) {
        boolean rowHovered = isMouseOver(mouseX, mouseY, x, y, width, ROW_HEIGHT);
        if (rowHovered) {
            graphics.fill(x, y, x + width, y + ROW_HEIGHT, DesignTokens.Surface.LEVEL_1);
        }
        UIScaleManager.drawScaledString(graphics, font, label, x, y + 4, DesignTokens.Text.PRIMARY, false);
        UIScaleManager.drawScaledString(graphics, font, description, x, y + 14, DesignTokens.Text.MUTED, false);
        String safeValue = Objects.requireNonNull(value, "value");
        int valueWidth = UIScaleManager.getScaledStringWidth(font, safeValue);
        int valueX = x + width - valueWidth;
        UIScaleManager.drawScaledString(graphics, font, safeValue, valueX, y + 4, DesignTokens.Text.SECONDARY, false);
    }

    private void renderScrollbar(GuiGraphics graphics, int x, int y, int height) {
        float visibleRatio = (float) visibleHeight / totalContentHeight;
        int thumbHeight = Math.max(20, (int) (height * visibleRatio));
        int trackHeight = height - thumbHeight;
        int thumbY = y;
        if (trackHeight > 0) {
            thumbY = y + (int) (trackHeight * (scrollOffset / (float) maxScrollOffset));
        }
        graphics.fill(x, y, x + SCROLLBAR_WIDTH, y + height, DesignTokens.Background.INPUT);
        graphics.fill(x, thumbY, x + SCROLLBAR_WIDTH, thumbY + thumbHeight, DesignTokens.Text.MUTED);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int contentX, int contentY, int contentWidth) {
        if (button != 0 || !isMouseOverContent(mouseX, mouseY)) {
            return false;
        }

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
                    scrollOffset = (int) (maxScrollOffset * clickRatio);
                }
                return true;
            }
        }

        int effectiveWidth = showScrollbar ? Math.max(0, contentWidth - SCROLLBAR_WIDTH - 4) : contentWidth;
        int currentY = contentY - scrollOffset;

        // Behavior header
        currentY += ROW_HEIGHT;

        if (isMouseOver((int) mouseX, (int) mouseY, contentX, currentY, effectiveWidth, ROW_HEIGHT)) {
            releaseToSelect = !releaseToSelect;
            config.releaseToSelect = releaseToSelect;
            config.save();
            return true;
        }
        currentY += ROW_HEIGHT;

        if (isMouseOver((int) mouseX, (int) mouseY, contentX, currentY, effectiveWidth, ROW_HEIGHT)) {
            closeOnToggle = !closeOnToggle;
            config.closeOnToggle = closeOnToggle;
            config.save();
            return true;
        }
        currentY += ROW_HEIGHT;

        if (isMouseOver((int) mouseX, (int) mouseY, contentX, currentY, effectiveWidth, ROW_HEIGHT)) {
            rightClickToEdit = !rightClickToEdit;
            config.rightClickToEdit = rightClickToEdit;
            config.save();
            return true;
        }
        currentY += ROW_HEIGHT;

        currentY += 8 + 16;

        // Feedback header
        currentY += ROW_HEIGHT;

        if (isMouseOver((int) mouseX, (int) mouseY, contentX, currentY, effectiveWidth, ROW_HEIGHT)) {
            enableAnimations = !enableAnimations;
            config.enableAnimations = enableAnimations;
            config.save();
            return true;
        }
        currentY += ROW_HEIGHT;

        if (isMouseOver((int) mouseX, (int) mouseY, contentX, currentY, effectiveWidth, ROW_HEIGHT)) {
            reducedMotion = !reducedMotion;
            config.reducedMotion = reducedMotion;
            config.save();
            return true;
        }
        currentY += ROW_HEIGHT;

        if (isMouseOver((int) mouseX, (int) mouseY, contentX, currentY, effectiveWidth, ROW_HEIGHT)) {
            enableSounds = !enableSounds;
            config.enableSounds = enableSounds;
            config.save();
            return true;
        }
        currentY += ROW_HEIGHT;

        if (isMouseOver((int) mouseX, (int) mouseY, contentX, currentY, effectiveWidth, ROW_HEIGHT)) {
            showTooltips = !showTooltips;
            config.showTooltips = showTooltips;
            config.save();
            return true;
        }
        currentY += ROW_HEIGHT;

        if (isMouseOver((int) mouseX, (int) mouseY, contentX, currentY, effectiveWidth, ROW_HEIGHT)) {
            showKeyHints = !showKeyHints;
            config.showKeyHints = showKeyHints;
            config.save();
            return true;
        }
        currentY += ROW_HEIGHT;

        currentY += 8 + 16;

        // Safety header
        currentY += ROW_HEIGHT;

        if (isMouseOver((int) mouseX, (int) mouseY, contentX, currentY, effectiveWidth, ROW_HEIGHT)) {
            safeMode = !safeMode;
            config.safeMode = safeMode;
            config.save();
            return true;
        }
        currentY += ROW_HEIGHT;

        if (isMouseOver((int) mouseX, (int) mouseY, contentX, currentY, effectiveWidth, ROW_HEIGHT)) {
            useUsageOrdering = !useUsageOrdering;
            config.useUsageOrdering = useUsageOrdering;
            config.save();
            return true;
        }
        currentY += ROW_HEIGHT;

        currentY += 8 + 16;

        // Profiles header
        currentY += ROW_HEIGHT;

        if (isMouseOver((int) mouseX, (int) mouseY, contentX, currentY, effectiveWidth, ROW_HEIGHT)) {
            config.cycleProfile();
            menuProfile = config.menuProfile;
            return true;
        }
        currentY += ROW_HEIGHT;

        if (isMouseOver((int) mouseX, (int) mouseY, contentX, currentY, effectiveWidth, ROW_HEIGHT)) {
            config.cycleTheme();
            themePreset = config.theme.presetName;
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
                scrollOffset = (int) (maxScrollOffset * dragRatio);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean hasUnsavedChanges() {
        // Guard against being called before init()
        if (themePreset == null || originalThemePreset == null) {
            return false;
        }
        return releaseToSelect != originalReleaseToSelect
            || rightClickToEdit != originalRightClickToEdit
            || enableAnimations != originalEnableAnimations
            || reducedMotion != originalReducedMotion
            || enableSounds != originalEnableSounds
            || showTooltips != originalShowTooltips
            || closeOnToggle != originalCloseOnToggle
            || showKeyHints != originalShowKeyHints
            || safeMode != originalSafeMode
            || useUsageOrdering != originalUseUsageOrdering
            || menuProfile != originalMenuProfile
            || !themePreset.equals(originalThemePreset);
    }

    @Override
    public void saveChanges() {
        originalReleaseToSelect = releaseToSelect;
        originalRightClickToEdit = rightClickToEdit;
        originalEnableAnimations = enableAnimations;
        originalReducedMotion = reducedMotion;
        originalEnableSounds = enableSounds;
        originalShowTooltips = showTooltips;
        originalCloseOnToggle = closeOnToggle;
        originalShowKeyHints = showKeyHints;
        originalSafeMode = safeMode;
        originalUseUsageOrdering = useUsageOrdering;
        originalMenuProfile = menuProfile;
        originalThemePreset = themePreset;
        config.save();
    }

    @Override
    public void resetToDefaults() {
        releaseToSelect = true;
        rightClickToEdit = true;
        enableAnimations = true;
        reducedMotion = false;
        enableSounds = true;
        showTooltips = true;
        closeOnToggle = false;
        showKeyHints = true;
        safeMode = false;
        useUsageOrdering = false;
        menuProfile = RadialMenuConfig.MenuProfile.ALL;
        themePreset = "default";

        config.releaseToSelect = releaseToSelect;
        config.rightClickToEdit = rightClickToEdit;
        config.enableAnimations = enableAnimations;
        config.reducedMotion = reducedMotion;
        config.enableSounds = enableSounds;
        config.showTooltips = showTooltips;
        config.closeOnToggle = closeOnToggle;
        config.showKeyHints = showKeyHints;
        config.safeMode = safeMode;
        config.useUsageOrdering = useUsageOrdering;
        config.menuProfile = menuProfile;
        config.setTheme(themePreset);
        config.save();
    }

    private int calculateContentHeight() {
        int height = 0;
        // Behavior
        height += ROW_HEIGHT;
        height += ROW_HEIGHT * 3;
        height += 8 + 16;
        // Feedback
        height += ROW_HEIGHT;
        height += ROW_HEIGHT * 5;
        height += 8 + 16;
        // Safety & Ordering
        height += ROW_HEIGHT;
        height += ROW_HEIGHT * 2;
        height += 8 + 16;
        // Profiles & Theme
        height += ROW_HEIGHT;
        height += ROW_HEIGHT * 2;
        return height;
    }

    private boolean isMouseOver(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private boolean isMouseOverContent(double mouseX, double mouseY) {
        return mouseX >= lastContentX && mouseX <= lastContentX + lastContentWidth
            && mouseY >= lastContentY && mouseY <= lastContentY + lastContentHeight;
    }

    private String profileLabel(RadialMenuConfig.MenuProfile profile) {
        if (profile == null) {
            return I18n.translate("devmod.radial.profile.all").getString();
        }
        return I18n.translate("devmod.radial.profile." + profile.name().toLowerCase(Locale.ROOT)).getString();
    }
}
