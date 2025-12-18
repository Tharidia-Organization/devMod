package com.frenkvs.devmod.ui.unified;

import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.ConfirmDialog;
import com.frenkvs.devmod.ui.editor.components.EditorButton;
import com.frenkvs.devmod.ui.UIConstants;
import com.frenkvs.devmod.ui.unified.pages.CombatSettingsPage;
import com.frenkvs.devmod.ui.unified.pages.DebugOverlaysPage;
import com.frenkvs.devmod.ui.unified.pages.EditorSettingsPage;
import com.frenkvs.devmod.ui.unified.pages.GeneralSettingsPage;
import com.frenkvs.devmod.ui.unified.pages.KeybindsPage;
import com.frenkvs.devmod.ui.unified.pages.MobConfigPage;
import com.frenkvs.devmod.ui.unified.pages.TelemetryPage;
import com.frenkvs.devmod.ui.unified.pages.VisualizersPage;
import com.frenkvs.devmod.ui.unified.persistence.SettingsManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import com.frenkvs.devmod.util.I18n;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Unified settings screen for all mod settings.
 * Layout with sidebar for navigation and content area for pages.
 */

public class UnifiedSettingsScreen extends Screen {

    // === Layout Constants ===
    private static final int SIDEBAR_WIDTH = 140;
    private static final int HEADER_HEIGHT = 32;
    private static final int FOOTER_HEIGHT = 40;
    private static final int SIDEBAR_ITEM_HEIGHT = 28;
    private static final int PADDING = UIConstants.Spacing.PANEL_PADDING;

    // === State ===
    private SettingsCategory currentCategory = SettingsCategory.GENERAL;
    private final Map<SettingsCategory, SettingsPage> pages = new EnumMap<>(SettingsCategory.class);
    private int mouseX, mouseY;

    // Confirmation dialogs (reusable overlay component)
    private ConfirmDialog resetDialog;
    private ConfirmDialog factoryResetDialog;
    private ConfirmDialog progressResetDialog;
    // Footer buttons
    private final EditorButton footerResetPageBtn = new EditorButton("footer-reset-page", "Reset Page");
    private final EditorButton footerResetProgressBtn = new EditorButton("footer-reset-progress", "Reset Progress");
    private final EditorButton footerFactoryResetBtn = new EditorButton("footer-factory-reset", "Factory Reset").style(EditorButton.Style.DANGER);
    private final EditorButton footerCloseBtn = new EditorButton("footer-close", "Close");
    private final EditorButton footerApplyBtn = new EditorButton("footer-apply", "Apply").style(EditorButton.Style.PRIMARY);

    // Search functionality
    private String searchQuery = "";
    private boolean searchFocused = false;
    private static final int SEARCH_HEIGHT = 20;

    // Animation state
    private float categoryTransitionProgress = 1.0f;

    // Tooltip state
    private String tooltipText = "";
    private long tooltipShowTime = 0;
    private static final long TOOLTIP_DELAY_MS = 500;

    // Blur control
    private int originalBlurValue = 0;

    @Nullable
    private final Screen parent;

    public UnifiedSettingsScreen(@Nullable Screen parent) {
        super(I18n.screenTitle("voxel_lab_settings"));
        this.parent = parent;

        // Disable menu blur when opening this screen
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            OptionInstance<Integer> blurOption = mc.options.menuBackgroundBlurriness();
            originalBlurValue = blurOption.get();
            blurOption.set(0);
        }
    }

    private void initDialogs() {
        this.resetDialog = ConfirmDialog.create(
            "Reset Settings?",
            "Reset",
            "Cancel",
            ConfirmDialog.Style.WARNING,
            this::resetCurrentPage,
            () -> {},
            "This will reset the current category to defaults."
        );

        this.factoryResetDialog = ConfirmDialog.create(
            "!! FACTORY RESET !!",
            "RESET ALL",
            "Cancel",
            ConfirmDialog.Style.DANGER,
            this::performFactoryReset,
            () -> {},
            "This will permanently reset:",
            "- All settings to defaults",
            "- Tutorial/Onboarding progress",
            "- Quest data & achievements",
            "Restart may be required."
        );

        this.progressResetDialog = ConfirmDialog.create(
            "Reset Player Progress",
            "Reset Progress",
            "Cancel",
            ConfirmDialog.Style.WARNING,
            this::performPlayerProgressReset,
            () -> {},
            "This will reset ALL player progress:",
            "- Endurance Quest stats & records",
            "- Tokens, rewards & achievements",
            "- Item Editor presets & favorites",
            "- Leaderboard rankings",
            "Settings will NOT be affected."
        );
    }

    @Override
    protected void init() {
        super.init();

        initDialogs();

        // Initialize pages
        pages.put(SettingsCategory.GENERAL, new GeneralSettingsPage());
        pages.put(SettingsCategory.EDITOR, new EditorSettingsPage());
        pages.put(SettingsCategory.DEBUG, new DebugOverlaysPage());
        pages.put(SettingsCategory.VISUALIZERS, new VisualizersPage());
        pages.put(SettingsCategory.COMBAT, new CombatSettingsPage());
        pages.put(SettingsCategory.MOBS, new MobConfigPage());
        pages.put(SettingsCategory.TELEMETRY, new TelemetryPage());
        pages.put(SettingsCategory.KEYBINDS, new KeybindsPage());

        // Initialize the current page
        SettingsPage currentPage = pages.get(currentCategory);
        if (currentPage != null) {
            currentPage.init();
        }
    }

    @Override
    public void tick() {
        super.tick();

        // Update transition animation
        if (categoryTransitionProgress < 1.0f) {
            categoryTransitionProgress += 0.15f;
            if (categoryTransitionProgress > 1.0f) {
                categoryTransitionProgress = 1.0f;
            }
        }

        // Tick current page
        SettingsPage page = pages.get(currentCategory);
        if (page != null) {
            page.tick();
        }
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;

        // Dark background
        graphics.fill(0, 0, width, height, UIConstants.Background.SCREEN());

        // Header
        renderHeader(graphics);

        // Sidebar
        renderSidebar(graphics, mouseX, mouseY);

        // Vertical separator
        int separatorX = SIDEBAR_WIDTH;
        graphics.fill(separatorX, HEADER_HEIGHT, separatorX + 1, height - FOOTER_HEIGHT, UIConstants.Border.DEFAULT());

        // Content area
        renderContent(graphics, mouseX, mouseY);

        // Footer
        renderFooter(graphics, mouseX, mouseY);

        // Modal overlays (rendered on top)
        renderDialogs(graphics, mouseX, mouseY);

        // Tooltip (rendered last, on top of everything)
        if (!isDialogOpen()) {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    private void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        // Check if hovering over sidebar item for tooltip
        if (mouseX < SIDEBAR_WIDTH && mouseY > HEADER_HEIGHT + PADDING + SEARCH_HEIGHT + 8) {
            int y = HEADER_HEIGHT + PADDING + SEARCH_HEIGHT + 8;

            for (SettingsCategory category : SettingsCategory.values()) {
                if (!searchQuery.isEmpty() && !matchesSearch(category)) {
                    continue;
                }

                if (isMouseOverSidebarItem(mouseX, mouseY, y)) {
                    // Show tooltip after delay
                    if (tooltipShowTime == 0) {
                        tooltipShowTime = System.currentTimeMillis();
                        tooltipText = category.getDescription();
                    }

                    if (System.currentTimeMillis() - tooltipShowTime >= TOOLTIP_DELAY_MS && !tooltipText.isEmpty()) {
                        // Draw tooltip
                        int tipWidth = font.width(tooltipText) + 8;
                        int tipHeight = 14;
                        int tipX = Math.min(mouseX + 10, width - tipWidth - 5);
                        int tipY = mouseY - tipHeight - 5;

                        graphics.fill(tipX, tipY, tipX + tipWidth, tipY + tipHeight, UIConstants.Background.PANEL());
                        AxiomRenderer.drawBorder(graphics, tipX, tipY, tipWidth, tipHeight, UIConstants.Border.DEFAULT());
                        graphics.drawString(font, tooltipText, tipX + 4, tipY + 3, UIConstants.Text.PRIMARY(), false);
                    }
                    return;
                }
                y += SIDEBAR_ITEM_HEIGHT;
            }
        }

        // Reset tooltip state if not hovering
        tooltipShowTime = 0;
        tooltipText = "";
    }

    private void renderDialogs(GuiGraphics graphics, int mouseX, int mouseY) {
        if (resetDialog != null) resetDialog.render(graphics, font, width, height, mouseX, mouseY);
        if (factoryResetDialog != null) factoryResetDialog.render(graphics, font, width, height, mouseX, mouseY);
        if (progressResetDialog != null) progressResetDialog.render(graphics, font, width, height, mouseX, mouseY);
    }

    private boolean isDialogOpen() {
        return (resetDialog != null && resetDialog.isVisible()) ||
               (factoryResetDialog != null && factoryResetDialog.isVisible()) ||
               (progressResetDialog != null && progressResetDialog.isVisible());
    }

    private boolean handleDialogClick(double mouseX, double mouseY) {
        return (resetDialog != null && resetDialog.mouseClicked(mouseX, mouseY, width, height)) ||
               (factoryResetDialog != null && factoryResetDialog.mouseClicked(mouseX, mouseY, width, height)) ||
               (progressResetDialog != null && progressResetDialog.mouseClicked(mouseX, mouseY, width, height));
    }

    private boolean handleDialogScroll(double mouseX, double mouseY, double scrollDelta) {
        return (resetDialog != null && resetDialog.mouseScrolled(mouseX, mouseY, scrollDelta, width, height)) ||
               (factoryResetDialog != null && factoryResetDialog.mouseScrolled(mouseX, mouseY, scrollDelta, width, height)) ||
               (progressResetDialog != null && progressResetDialog.mouseScrolled(mouseX, mouseY, scrollDelta, width, height));
    }

    private boolean handleDialogKey(int keyCode) {
        return (resetDialog != null && resetDialog.keyPressed(keyCode)) ||
               (factoryResetDialog != null && factoryResetDialog.keyPressed(keyCode)) ||
               (progressResetDialog != null && progressResetDialog.keyPressed(keyCode));
    }

    private boolean handleDialogChar(char codePoint, int modifiers) {
        return (resetDialog != null && resetDialog.charTyped(codePoint, modifiers)) ||
               (factoryResetDialog != null && factoryResetDialog.charTyped(codePoint, modifiers)) ||
               (progressResetDialog != null && progressResetDialog.charTyped(codePoint, modifiers));
    }

    private void performFactoryReset() {
        // Call the global reset
        com.frenkvs.devmod.ui.unified.persistence.SettingsManager.INSTANCE.resetAll();

        // Show feedback to user
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(
                I18n.translate("devmod.message.factory_reset_complete"),
                false
            );
        }

        // Close settings screen
        onClose();
    }

    private void performPlayerProgressReset() {
        // Reset ItemEditorDataManager
        try {
            com.frenkvs.devmod.ItemEditorDataManager.INSTANCE.resetAll();
        } catch (Exception e) {
            // Ignore - may not be initialized
        }

        // Reset EnduranceQuestManager (includes RewardSystem and GamificationManager)
        try {
            com.frenkvs.devmod.endurance.EnduranceQuestManager.INSTANCE.clearAllPlayerStats();
        } catch (Exception e) {
            // Ignore - may not be initialized on client
        }

        // Show feedback to user
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(
                I18n.translate("devmod.message.player_progress_reset"),
                false
            );
        }
    }

    // === Header ===

    private void renderHeader(GuiGraphics graphics) {
        // Background header
        graphics.fill(0, 0, width, HEADER_HEIGHT, UIConstants.Background.HEADER());
        graphics.fill(0, HEADER_HEIGHT - 1, width, HEADER_HEIGHT, UIConstants.Border.DEFAULT());

        // Title
        graphics.drawString(font, "VOXEL-LAB Settings", PADDING, (HEADER_HEIGHT - 9) / 2, UIConstants.Text.TITLE(), false);

        // Breadcrumb on the right
        String breadcrumb = currentCategory.getLabel();
        int breadcrumbWidth = font.width(breadcrumb);
        graphics.drawString(font, breadcrumb, width - breadcrumbWidth - PADDING, (HEADER_HEIGHT - 9) / 2,
            currentCategory.getAccentColor(), false);

        // Close button (X)
        int closeX = width - 20;
        int closeY = (HEADER_HEIGHT - 12) / 2;
        boolean closeHovered = mouseX >= closeX && mouseX < closeX + 12 && mouseY >= closeY && mouseY < closeY + 12;
        int closeColor = closeHovered ? UIConstants.Status.ERROR() : UIConstants.Text.MUTED();
        graphics.drawString(font, "X", closeX, closeY, closeColor, false);
    }

    // === Sidebar ===

    private void renderSidebar(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = HEADER_HEIGHT + PADDING;

        // Search box
        renderSearchBox(graphics, PADDING, y, SIDEBAR_WIDTH - PADDING * 2, mouseX, mouseY);
        y += SEARCH_HEIGHT + 8;

        // Category items
        for (SettingsCategory category : SettingsCategory.values()) {
            // Filter by search query
            if (!searchQuery.isEmpty() && !matchesSearch(category)) {
                continue;
            }

            boolean selected = category == currentCategory;
            boolean hovered = isMouseOverSidebarItem(mouseX, mouseY, y);
            boolean hasPage = pages.containsKey(category);

            renderSidebarItem(graphics, category, y, selected, hovered, hasPage);
            y += SIDEBAR_ITEM_HEIGHT;
        }
    }

    private void renderSearchBox(GuiGraphics graphics, int x, int y, int boxWidth, int mouseX, int mouseY) {
        // Background
        int bgColor = searchFocused ? UIConstants.Background.INPUT() : UIConstants.Background.PANEL();
        graphics.fill(x, y, x + boxWidth, y + SEARCH_HEIGHT, bgColor);

        // Border
        int borderColor = searchFocused ? UIConstants.Border.ACCENT() : UIConstants.Border.DEFAULT();
        AxiomRenderer.drawBorder(graphics, x, y, boxWidth, SEARCH_HEIGHT, borderColor);

        // Search icon or text
        if (searchQuery.isEmpty() && !searchFocused) {
            graphics.drawString(font, "Search...", x + 6, y + 6, UIConstants.Text.MUTED(), false);
        } else {
            graphics.drawString(font, searchQuery + (searchFocused ? "_" : ""), x + 6, y + 6, UIConstants.Text.PRIMARY(), false);
        }

        // Clear button if has text (larger hit area for better usability)
        if (!searchQuery.isEmpty()) {
            int clearX = x + boxWidth - 16;
            int clearHitWidth = 16; // Larger hit area than visual "x"
            boolean clearHovered = mouseX >= clearX && mouseX < clearX + clearHitWidth && mouseY >= y && mouseY < y + SEARCH_HEIGHT;
            graphics.drawString(font, "x", clearX + 3, y + 6, clearHovered ? UIConstants.Status.ERROR() : UIConstants.Text.MUTED(), false);
        }
    }

    private boolean matchesSearch(SettingsCategory category) {
        String query = searchQuery.toLowerCase();
        return category.getLabel().toLowerCase().contains(query) ||
               category.getDescription().toLowerCase().contains(query);
    }

    private void renderSidebarItem(GuiGraphics graphics, SettingsCategory category, int y,
                                    boolean selected, boolean hovered, boolean hasPage) {
        int x = PADDING;
        int itemWidth = SIDEBAR_WIDTH - PADDING * 2;

        // Background
        if (selected) {
            graphics.fill(x - 2, y - 2, x + itemWidth + 2, y + SIDEBAR_ITEM_HEIGHT - 6,
                applyAlpha(category.getAccentColor(), 0.2f));
            // Accent bar on the left
            graphics.fill(0, y - 2, 3, y + SIDEBAR_ITEM_HEIGHT - 6, category.getAccentColor());
        } else if (hovered && hasPage) {
            graphics.fill(x - 2, y - 2, x + itemWidth + 2, y + SIDEBAR_ITEM_HEIGHT - 6,
                UIConstants.Background.HOVER());
        }

        // Icon
        int iconColor = selected ? category.getAccentColor() :
            (hasPage ? UIConstants.Text.SECONDARY() : UIConstants.Text.DISABLED());
        graphics.drawString(font, "[" + category.getIcon() + "]", x, y + 4, iconColor, false);

        // Label
        int labelColor = selected ? UIConstants.Text.PRIMARY() :
            (hasPage ? UIConstants.Text.SECONDARY() : UIConstants.Text.DISABLED());
        graphics.drawString(font, category.getLabel(), x + 28, y + 4, labelColor, false);

        // "coming soon" indicator for unimplemented pages
        if (!hasPage) {
            String soon = "soon";
            int soonWidth = font.width(soon);
            graphics.drawString(font, soon, SIDEBAR_WIDTH - soonWidth - PADDING, y + 4,
                UIConstants.Text.DISABLED(), false);
        }
    }

    private boolean isMouseOverSidebarItem(int mouseX, int mouseY, int itemY) {
        return mouseX >= 0 && mouseX < SIDEBAR_WIDTH &&
               mouseY >= itemY - 2 && mouseY < itemY + SIDEBAR_ITEM_HEIGHT - 6;
    }

    // === Content Area ===

    private void renderContent(GuiGraphics graphics, int mouseX, int mouseY) {
        int contentX = SIDEBAR_WIDTH + PADDING;
        int contentY = HEADER_HEIGHT + PADDING;
        int contentWidth = width - SIDEBAR_WIDTH - PADDING * 2;
        int contentHeight = height - HEADER_HEIGHT - FOOTER_HEIGHT - PADDING * 2;

        // Apply fade animation offset
        int animOffset = 0;
        if (categoryTransitionProgress < 1.0f) {
            animOffset = (int) ((1.0f - categoryTransitionProgress) * 20);
        }

        // Page title
        SettingsPage page = pages.get(currentCategory);
        String pageTitle = page != null ? page.getTitle() : currentCategory.getLabel();

        // Animate title with fade
        int titleAlpha = (int) (255 * categoryTransitionProgress);
        int titleColor = (titleAlpha << 24) | (UIConstants.Text.PRIMARY() & 0x00FFFFFF);
        graphics.drawString(font, pageTitle, contentX + animOffset, contentY, titleColor, false);

        // Description (animated)
        int descAlpha = (int) (255 * categoryTransitionProgress * 0.7f);
        int descColor = (descAlpha << 24) | (UIConstants.Text.MUTED() & 0x00FFFFFF);
        graphics.drawString(font, currentCategory.getDescription(), contentX + animOffset, contentY + 12, descColor, false);

        // Separator
        int sepY = contentY + 28;
        graphics.fill(contentX, sepY, contentX + contentWidth, sepY + 1, UIConstants.Border.SEPARATOR());

        // Page content
        int pageY = sepY + PADDING;
        int pageHeight = contentHeight - 40;

        if (page != null) {
            // Apply content fade during transition
            if (categoryTransitionProgress >= 0.3f) {
                page.render(graphics, font, contentX + animOffset, pageY, contentWidth, pageHeight, mouseX, mouseY);
            }
        } else {
            // Placeholder for unimplemented pages
            String placeholder = "This section is coming soon...";
            int placeholderWidth = font.width(placeholder);
            graphics.drawString(font, placeholder,
                contentX + (contentWidth - placeholderWidth) / 2,
                pageY + pageHeight / 2,
                UIConstants.Text.DISABLED(), false);
        }
    }

    // === Footer ===

    private void renderFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        int footerY = height - FOOTER_HEIGHT;

        // Background
        graphics.fill(0, footerY, width, height, UIConstants.Background.HEADER());
        graphics.fill(0, footerY, width, footerY + 1, UIConstants.Border.DEFAULT());

        // Left buttons
        int buttonY = footerY + (FOOTER_HEIGHT - UIConstants.Size.BUTTON_HEIGHT) / 2;
        int buttonX = PADDING;

        // Reset Page button
        footerResetPageBtn
            .style(EditorButton.Style.NORMAL)
            .onClick(() -> {
                resetDialog.configure(
                    "Reset Settings?",
                    List.of("This will reset " + currentCategory.getLabel() + " to defaults.")
                ).show();
            });
        footerResetPageBtn.render(graphics, buttonX, buttonY, 80, UIConstants.Size.BUTTON_HEIGHT, mouseX, mouseY);

        // Reset Progress button (orange - warning action)
        buttonX += 90;
        footerResetProgressBtn
            .style(EditorButton.Style.DANGER)
            .onClick(progressResetDialog::show);
        footerResetProgressBtn.render(graphics, buttonX, buttonY, 105, UIConstants.Size.BUTTON_HEIGHT, mouseX, mouseY);

        // Factory Reset button (dangerous action - red color)
        buttonX += 115;
        footerFactoryResetBtn
            .style(EditorButton.Style.DANGER)
            .onClick(factoryResetDialog::show);
        footerFactoryResetBtn.render(graphics, buttonX, buttonY, 100, UIConstants.Size.BUTTON_HEIGHT, mouseX, mouseY);

        // Right buttons
        int rightButtonX = width - PADDING - 80;

        // Close button
        footerCloseBtn
            .style(EditorButton.Style.NORMAL)
            .onClick(this::onClose);
        footerCloseBtn.render(graphics, rightButtonX, buttonY, 80, UIConstants.Size.BUTTON_HEIGHT, mouseX, mouseY);

        // Apply button
        rightButtonX -= 90;
        footerApplyBtn
            .style(EditorButton.Style.PRIMARY)
            .enabled(true)
            .onClick(this::applyChanges);
        footerApplyBtn.render(graphics, rightButtonX, buttonY, 80, UIConstants.Size.BUTTON_HEIGHT, mouseX, mouseY);

        // Hint at center
        String hint = "Press ESC or K to close";
        int hintWidth = font.width(hint);
        graphics.drawString(font, hint, (width - hintWidth) / 2, buttonY + 5, UIConstants.Text.MUTED(), false);
    }

    // === Input Handling ===

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Modal dialogs intercept all clicks
        if (handleDialogClick(mouseX, mouseY)) {
            return true;
        }

        // Header close button
        int closeX = width - 20;
        int closeY = (HEADER_HEIGHT - 12) / 2;
        if (mouseX >= closeX && mouseX < closeX + 12 && mouseY >= closeY && mouseY < closeY + 12) {
            onClose();
            return true;
        }

        // Sidebar click
        if (mouseX < SIDEBAR_WIDTH && mouseY > HEADER_HEIGHT && mouseY < height - FOOTER_HEIGHT) {
            int y = HEADER_HEIGHT + PADDING;

            // Search box click
            int searchBoxWidth = SIDEBAR_WIDTH - PADDING * 2;
            if (mouseX >= PADDING && mouseX < PADDING + searchBoxWidth &&
                mouseY >= y && mouseY < y + SEARCH_HEIGHT) {

                // Clear button (larger hit area matching render)
                if (!searchQuery.isEmpty() && mouseX >= PADDING + searchBoxWidth - 16) {
                    searchQuery = "";
                    return true;
                }

                searchFocused = true;
                return true;
            } else {
                searchFocused = false;
            }

            y += SEARCH_HEIGHT + 8;

            // Category items
            for (SettingsCategory category : SettingsCategory.values()) {
                if (!searchQuery.isEmpty() && !matchesSearch(category)) {
                    continue;
                }

                if (isMouseOverSidebarItem((int) mouseX, (int) mouseY, y)) {
                    if (pages.containsKey(category)) {
                        selectCategory(category);
                    }
                    return true;
                }
                y += SIDEBAR_ITEM_HEIGHT;
            }
        } else {
            searchFocused = false;
        }

        // Reset Page button - show confirmation dialog
        if (footerResetPageBtn.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Reset Progress button - show player progress reset confirmation
        if (footerResetProgressBtn.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Factory Reset button - show factory reset confirmation
        if (footerFactoryResetBtn.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Close button
        if (footerCloseBtn.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Apply button
        if (footerApplyBtn.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Content area click
        SettingsPage page = pages.get(currentCategory);
        if (page != null) {
            int contentX = SIDEBAR_WIDTH + PADDING;
            int contentY = HEADER_HEIGHT + PADDING + 40;
            int contentWidth = width - SIDEBAR_WIDTH - PADDING * 2;
            if (page.mouseClicked(mouseX, mouseY, button, contentX, contentY, contentWidth)) {
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDialogOpen()) {
            return true;
        }

        boolean handled = false;
        handled |= footerResetPageBtn.mouseReleased(mouseX, mouseY, button);
        handled |= footerResetProgressBtn.mouseReleased(mouseX, mouseY, button);
        handled |= footerFactoryResetBtn.mouseReleased(mouseX, mouseY, button);
        handled |= footerCloseBtn.mouseReleased(mouseX, mouseY, button);
        handled |= footerApplyBtn.mouseReleased(mouseX, mouseY, button);
        if (handled) return true;

        // Propagate to current page
        SettingsPage page = pages.get(currentCategory);
        if (page != null && page.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (handleDialogKey(keyCode)) {
            return true;
        }

        // Handle search input
        if (searchFocused) {
            if (keyCode == 259) { // Backspace
                if (!searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                }
                return true;
            }
            if (keyCode == 256) { // Escape - unfocus search
                searchFocused = false;
                return true;
            }
            if (keyCode == 257) { // Enter - select first match
                for (SettingsCategory cat : SettingsCategory.values()) {
                    if (matchesSearch(cat) && pages.containsKey(cat)) {
                        selectCategory(cat);
                        searchFocused = false;
                        break;
                    }
                }
                return true;
            }
        }

        // K to close (same key used to open)
        if (keyCode == 75 && !searchFocused) { // K key
            onClose();
            return true;
        }

        // Arrow key navigation for categories
        if (!searchFocused) {
            if (keyCode == 264) { // Down arrow
                navigateCategory(1);
                return true;
            }
            if (keyCode == 265) { // Up arrow
                navigateCategory(-1);
                return true;
            }
        }

        // Pass to current page
        SettingsPage page = pages.get(currentCategory);
        if (page != null && page.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (handleDialogChar(codePoint, modifiers)) {
            return true;
        }

        // Handle search text input
        if (searchFocused && (Character.isLetterOrDigit(codePoint) || codePoint == ' ')) {
            if (searchQuery.length() < 20) {
                searchQuery += codePoint;
            }
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private void navigateCategory(int direction) {
        SettingsCategory[] categories = SettingsCategory.values();
        int currentIndex = currentCategory.ordinal();
        int newIndex = currentIndex;

        // Find next valid category
        for (int i = 0; i < categories.length; i++) {
            newIndex = (newIndex + direction + categories.length) % categories.length;
            SettingsCategory candidate = categories[newIndex];

            // Skip if filtered out by search
            if (!searchQuery.isEmpty() && !matchesSearch(candidate)) {
                continue;
            }

            // Skip if no page
            if (!pages.containsKey(candidate)) {
                continue;
            }

            selectCategory(candidate);
            break;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (handleDialogScroll(mouseX, mouseY, scrollY)) {
            return true;
        }

        SettingsPage page = pages.get(currentCategory);
        if (page != null) {
            return page.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDialogOpen()) {
            return true;
        }

        SettingsPage page = pages.get(currentCategory);
        if (page != null && page.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    // === Actions ===

    private void selectCategory(SettingsCategory category) {
        if (category != currentCategory) {
            // Close current page
            SettingsPage oldPage = pages.get(currentCategory);
            if (oldPage != null) {
                oldPage.onClose();
            }

            // Start transition animation
            categoryTransitionProgress = 0.0f;

            // Change category
            currentCategory = category;

            // Initialize new page
            SettingsPage newPage = pages.get(currentCategory);
            if (newPage != null) {
                newPage.init();
            }
        }
    }

    private void resetCurrentPage() {
        SettingsPage page = pages.get(currentCategory);
        if (page != null) {
            page.resetToDefaults();
        }
    }

    private void applyChanges() {
        SettingsPage page = pages.get(currentCategory);
        if (page != null) {
            page.saveChanges();
        }
    }

    @Override
    public void onClose() {
        // Restore original blur setting
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            mc.options.menuBackgroundBlurriness().set(originalBlurValue);
        }

        // Save changes if needed
        for (SettingsPage page : pages.values()) {
            if (page.hasUnsavedChanges()) {
                page.saveChanges();
            }
            page.onClose();
        }

        // Persist settings to disk
        SettingsManager.INSTANCE.save();

        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Override to disable the default blurred background completely
        // Just fill with solid color, no blur
        graphics.fill(0, 0, this.width, this.height, UIConstants.Background.SCREEN());
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // Do nothing - disable blur completely
    }

    @Override
    protected void renderMenuBackground(GuiGraphics graphics) {
        // Just solid background, no dimming or blur
        graphics.fill(0, 0, this.width, this.height, UIConstants.Background.SCREEN());
    }

    // === Utility ===

    private int applyAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }
}
