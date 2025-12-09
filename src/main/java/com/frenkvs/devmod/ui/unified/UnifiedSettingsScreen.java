package com.frenkvs.devmod.ui.unified;

import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.UIConstants;
import com.frenkvs.devmod.ui.unified.pages.CombatSettingsPage;
import com.frenkvs.devmod.ui.unified.pages.DebugOverlaysPage;
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
import java.util.Map;

/**
 * Schermata unificata per tutte le impostazioni del mod.
 * Layout con sidebar per navigazione e area contenuto per le pagine.
 */
@SuppressWarnings("null")
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
    private int scrollOffset = 0;
    private int mouseX, mouseY;

    // Reset confirmation dialog
    private boolean showResetConfirmation = false;
    private boolean showFactoryResetConfirmation = false;

    // Search functionality
    private String searchQuery = "";
    private boolean searchFocused = false;
    private static final int SEARCH_HEIGHT = 20;

    // Animation state
    private float categoryTransitionProgress = 1.0f;
    private SettingsCategory transitionFromCategory = null;

    // Tooltip state
    private String tooltipText = "";
    private int tooltipX, tooltipY;
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

    @Override
    protected void init() {
        super.init();

        // Inizializza le pagine
        pages.put(SettingsCategory.GENERAL, new GeneralSettingsPage());
        pages.put(SettingsCategory.DEBUG, new DebugOverlaysPage());
        pages.put(SettingsCategory.VISUALIZERS, new VisualizersPage());
        pages.put(SettingsCategory.COMBAT, new CombatSettingsPage());
        pages.put(SettingsCategory.MOBS, new MobConfigPage());
        pages.put(SettingsCategory.TELEMETRY, new TelemetryPage());
        pages.put(SettingsCategory.KEYBINDS, new KeybindsPage());

        // Inizializza la pagina corrente
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
                transitionFromCategory = null;
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

        // Background scuro
        graphics.fill(0, 0, width, height, UIConstants.Background.SCREEN);

        // Header
        renderHeader(graphics);

        // Sidebar
        renderSidebar(graphics, mouseX, mouseY);

        // Separatore verticale
        int separatorX = SIDEBAR_WIDTH;
        graphics.fill(separatorX, HEADER_HEIGHT, separatorX + 1, height - FOOTER_HEIGHT, UIConstants.Border.DEFAULT);

        // Area contenuto
        renderContent(graphics, mouseX, mouseY);

        // Footer
        renderFooter(graphics, mouseX, mouseY);

        // Reset confirmation dialog (rendered on top)
        if (showResetConfirmation) {
            renderResetConfirmationDialog(graphics, mouseX, mouseY);
        }

        // Factory reset confirmation dialog (rendered on top)
        if (showFactoryResetConfirmation) {
            renderFactoryResetDialog(graphics, mouseX, mouseY);
        }

        // Tooltip (rendered last, on top of everything)
        renderTooltip(graphics, mouseX, mouseY);
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
                        tooltipX = mouseX;
                        tooltipY = mouseY;
                    }

                    if (System.currentTimeMillis() - tooltipShowTime >= TOOLTIP_DELAY_MS && !tooltipText.isEmpty()) {
                        // Draw tooltip
                        int tipWidth = font.width(tooltipText) + 8;
                        int tipHeight = 14;
                        int tipX = Math.min(mouseX + 10, width - tipWidth - 5);
                        int tipY = mouseY - tipHeight - 5;

                        graphics.fill(tipX, tipY, tipX + tipWidth, tipY + tipHeight, UIConstants.Background.PANEL);
                        AxiomRenderer.drawBorder(graphics, tipX, tipY, tipWidth, tipHeight, UIConstants.Border.DEFAULT);
                        graphics.drawString(font, tooltipText, tipX + 4, tipY + 3, UIConstants.Text.PRIMARY, false);
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

    private void renderResetConfirmationDialog(GuiGraphics graphics, int mouseX, int mouseY) {
        // Dim background
        graphics.fill(0, 0, width, height, 0x80000000);

        // Dialog box
        int dialogWidth = 260;
        int dialogHeight = 100;
        int dialogX = (width - dialogWidth) / 2;
        int dialogY = (height - dialogHeight) / 2;

        // Background
        graphics.fill(dialogX, dialogY, dialogX + dialogWidth, dialogY + dialogHeight, UIConstants.Background.PANEL);
        AxiomRenderer.drawBorder(graphics, dialogX, dialogY, dialogWidth, dialogHeight, UIConstants.Border.ACCENT);

        // Title
        String title = "Reset Settings?";
        int titleWidth = font.width(title);
        graphics.drawString(font, title, dialogX + (dialogWidth - titleWidth) / 2, dialogY + 12, UIConstants.Text.PRIMARY, false);

        // Message
        String message = "This will reset " + currentCategory.getLabel() + " to defaults.";
        int messageWidth = font.width(message);
        graphics.drawString(font, message, dialogX + (dialogWidth - messageWidth) / 2, dialogY + 32, UIConstants.Text.SECONDARY, false);

        // Buttons
        int buttonWidth = 80;
        int buttonHeight = UIConstants.Size.BUTTON_HEIGHT;
        int buttonY = dialogY + dialogHeight - buttonHeight - 16;

        // Cancel button
        int cancelX = dialogX + dialogWidth / 2 - buttonWidth - 10;
        boolean cancelHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, cancelX, buttonY, buttonWidth, buttonHeight);
        AxiomRenderer.drawButton(graphics, font, cancelX, buttonY, buttonWidth, buttonHeight, "Cancel", cancelHovered, false);

        // Confirm button
        int confirmX = dialogX + dialogWidth / 2 + 10;
        boolean confirmHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, confirmX, buttonY, buttonWidth, buttonHeight);
        graphics.fill(confirmX, buttonY, confirmX + buttonWidth, buttonY + buttonHeight,
            confirmHovered ? UIConstants.Status.ERROR : UIConstants.Accent.RED);
        AxiomRenderer.drawBorder(graphics, confirmX, buttonY, buttonWidth, buttonHeight, UIConstants.Border.DEFAULT);
        String confirmText = "Reset";
        int confirmTextWidth = font.width(confirmText);
        graphics.drawString(font, confirmText, confirmX + (buttonWidth - confirmTextWidth) / 2,
            buttonY + (buttonHeight - 8) / 2, UIConstants.Text.WHITE, false);
    }

    private boolean handleResetDialogClick(int mouseX, int mouseY) {
        int dialogWidth = 260;
        int dialogHeight = 100;
        int dialogX = (width - dialogWidth) / 2;
        int dialogY = (height - dialogHeight) / 2;

        int buttonWidth = 80;
        int buttonHeight = UIConstants.Size.BUTTON_HEIGHT;
        int buttonY = dialogY + dialogHeight - buttonHeight - 16;

        // Cancel button
        int cancelX = dialogX + dialogWidth / 2 - buttonWidth - 10;
        if (AxiomRenderer.isMouseOver(mouseX, mouseY, cancelX, buttonY, buttonWidth, buttonHeight)) {
            showResetConfirmation = false;
            return true;
        }

        // Confirm button
        int confirmX = dialogX + dialogWidth / 2 + 10;
        if (AxiomRenderer.isMouseOver(mouseX, mouseY, confirmX, buttonY, buttonWidth, buttonHeight)) {
            resetCurrentPage();
            showResetConfirmation = false;
            return true;
        }

        // Click outside dialog cancels
        if (mouseX < dialogX || mouseX > dialogX + dialogWidth ||
            mouseY < dialogY || mouseY > dialogY + dialogHeight) {
            showResetConfirmation = false;
            return true;
        }

        return true; // Consume all clicks when dialog is open
    }

    // === Factory Reset Dialog ===

    private void renderFactoryResetDialog(GuiGraphics graphics, int mouseX, int mouseY) {
        // Dim background (darker for dangerous action)
        graphics.fill(0, 0, width, height, 0xAA000000);

        // Dialog box
        int dialogWidth = 320;
        int dialogHeight = 140;
        int dialogX = (width - dialogWidth) / 2;
        int dialogY = (height - dialogHeight) / 2;

        // Background with red border
        graphics.fill(dialogX - 2, dialogY - 2, dialogX + dialogWidth + 2, dialogY + dialogHeight + 2, UIConstants.Status.ERROR);
        graphics.fill(dialogX, dialogY, dialogX + dialogWidth, dialogY + dialogHeight, UIConstants.Background.PANEL);

        // Warning icon and title
        String title = "!! FACTORY RESET !!";
        int titleWidth = font.width(title);
        graphics.drawString(font, title, dialogX + (dialogWidth - titleWidth) / 2, dialogY + 12, UIConstants.Status.ERROR, false);

        // Warning messages
        String[] messages = {
            "This will PERMANENTLY reset:",
            "- All settings to defaults",
            "- Tutorial/Onboarding progress",
            "- Quest data & achievements",
            "Restart required for full effect."
        };

        int y = dialogY + 30;
        for (String msg : messages) {
            int msgWidth = font.width(msg);
            int color = msg.startsWith("-") ? UIConstants.Text.SECONDARY : UIConstants.Text.PRIMARY;
            graphics.drawString(font, msg, dialogX + (dialogWidth - msgWidth) / 2, y, color, false);
            y += 11;
        }

        // Buttons
        int buttonWidth = 100;
        int buttonHeight = UIConstants.Size.BUTTON_HEIGHT;
        int buttonY = dialogY + dialogHeight - buttonHeight - 12;

        // Cancel button
        int cancelX = dialogX + dialogWidth / 2 - buttonWidth - 15;
        boolean cancelHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, cancelX, buttonY, buttonWidth, buttonHeight);
        AxiomRenderer.drawButton(graphics, font, cancelX, buttonY, buttonWidth, buttonHeight, "Cancel", cancelHovered, false);

        // Confirm button (red)
        int confirmX = dialogX + dialogWidth / 2 + 15;
        boolean confirmHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, confirmX, buttonY, buttonWidth, buttonHeight);
        graphics.fill(confirmX, buttonY, confirmX + buttonWidth, buttonY + buttonHeight,
            confirmHovered ? UIConstants.Status.ERROR : UIConstants.Accent.RED);
        AxiomRenderer.drawBorder(graphics, confirmX, buttonY, buttonWidth, buttonHeight, UIConstants.Border.DEFAULT);
        String confirmText = "RESET ALL";
        int confirmTextWidth = font.width(confirmText);
        graphics.drawString(font, confirmText, confirmX + (buttonWidth - confirmTextWidth) / 2,
            buttonY + (buttonHeight - 8) / 2, UIConstants.Text.WHITE, false);
    }

    private boolean handleFactoryResetDialogClick(int mouseX, int mouseY) {
        int dialogWidth = 320;
        int dialogHeight = 140;
        int dialogX = (width - dialogWidth) / 2;
        int dialogY = (height - dialogHeight) / 2;

        int buttonWidth = 100;
        int buttonHeight = UIConstants.Size.BUTTON_HEIGHT;
        int buttonY = dialogY + dialogHeight - buttonHeight - 12;

        // Cancel button
        int cancelX = dialogX + dialogWidth / 2 - buttonWidth - 15;
        if (AxiomRenderer.isMouseOver(mouseX, mouseY, cancelX, buttonY, buttonWidth, buttonHeight)) {
            showFactoryResetConfirmation = false;
            return true;
        }

        // Confirm button - perform factory reset
        int confirmX = dialogX + dialogWidth / 2 + 15;
        if (AxiomRenderer.isMouseOver(mouseX, mouseY, confirmX, buttonY, buttonWidth, buttonHeight)) {
            performFactoryReset();
            showFactoryResetConfirmation = false;
            return true;
        }

        // Click outside dialog cancels
        if (mouseX < dialogX || mouseX > dialogX + dialogWidth ||
            mouseY < dialogY || mouseY > dialogY + dialogHeight) {
            showFactoryResetConfirmation = false;
            return true;
        }

        return true; // Consume all clicks when dialog is open
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

    // === Header ===

    private void renderHeader(GuiGraphics graphics) {
        // Background header
        graphics.fill(0, 0, width, HEADER_HEIGHT, UIConstants.Background.HEADER);
        graphics.fill(0, HEADER_HEIGHT - 1, width, HEADER_HEIGHT, UIConstants.Border.DEFAULT);

        // Titolo
        graphics.drawString(font, "VOXEL-LAB Settings", PADDING, (HEADER_HEIGHT - 9) / 2, UIConstants.Text.TITLE, false);

        // Breadcrumb a destra
        String breadcrumb = currentCategory.getLabel();
        int breadcrumbWidth = font.width(breadcrumb);
        graphics.drawString(font, breadcrumb, width - breadcrumbWidth - PADDING, (HEADER_HEIGHT - 9) / 2,
            currentCategory.getAccentColor(), false);

        // Close button (X)
        int closeX = width - 20;
        int closeY = (HEADER_HEIGHT - 12) / 2;
        boolean closeHovered = mouseX >= closeX && mouseX < closeX + 12 && mouseY >= closeY && mouseY < closeY + 12;
        int closeColor = closeHovered ? UIConstants.Status.ERROR : UIConstants.Text.MUTED;
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
        int bgColor = searchFocused ? UIConstants.Background.INPUT : UIConstants.Background.PANEL;
        graphics.fill(x, y, x + boxWidth, y + SEARCH_HEIGHT, bgColor);

        // Border
        int borderColor = searchFocused ? UIConstants.Border.ACCENT : UIConstants.Border.DEFAULT;
        AxiomRenderer.drawBorder(graphics, x, y, boxWidth, SEARCH_HEIGHT, borderColor);

        // Search icon or text
        if (searchQuery.isEmpty() && !searchFocused) {
            graphics.drawString(font, "Search...", x + 6, y + 6, UIConstants.Text.MUTED, false);
        } else {
            graphics.drawString(font, searchQuery + (searchFocused ? "_" : ""), x + 6, y + 6, UIConstants.Text.PRIMARY, false);
        }

        // Clear button if has text
        if (!searchQuery.isEmpty()) {
            int clearX = x + boxWidth - 14;
            boolean clearHovered = mouseX >= clearX && mouseX < clearX + 10 && mouseY >= y && mouseY < y + SEARCH_HEIGHT;
            graphics.drawString(font, "x", clearX, y + 6, clearHovered ? UIConstants.Status.ERROR : UIConstants.Text.MUTED, false);
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
            // Accent bar a sinistra
            graphics.fill(0, y - 2, 3, y + SIDEBAR_ITEM_HEIGHT - 6, category.getAccentColor());
        } else if (hovered && hasPage) {
            graphics.fill(x - 2, y - 2, x + itemWidth + 2, y + SIDEBAR_ITEM_HEIGHT - 6,
                UIConstants.Background.HOVER);
        }

        // Icona
        int iconColor = selected ? category.getAccentColor() :
            (hasPage ? UIConstants.Text.SECONDARY : UIConstants.Text.DISABLED);
        graphics.drawString(font, "[" + category.getIcon() + "]", x, y + 4, iconColor, false);

        // Label
        int labelColor = selected ? UIConstants.Text.PRIMARY :
            (hasPage ? UIConstants.Text.SECONDARY : UIConstants.Text.DISABLED);
        graphics.drawString(font, category.getLabel(), x + 28, y + 4, labelColor, false);

        // Indicatore "coming soon" per pagine non implementate
        if (!hasPage) {
            String soon = "soon";
            int soonWidth = font.width(soon);
            graphics.drawString(font, soon, SIDEBAR_WIDTH - soonWidth - PADDING, y + 4,
                UIConstants.Text.DISABLED, false);
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

        // Titolo pagina
        SettingsPage page = pages.get(currentCategory);
        String pageTitle = page != null ? page.getTitle() : currentCategory.getLabel();

        // Animate title with fade
        int titleAlpha = (int) (255 * categoryTransitionProgress);
        int titleColor = (titleAlpha << 24) | (UIConstants.Text.PRIMARY & 0x00FFFFFF);
        graphics.drawString(font, pageTitle, contentX + animOffset, contentY, titleColor, false);

        // Descrizione (animated)
        int descAlpha = (int) (255 * categoryTransitionProgress * 0.7f);
        int descColor = (descAlpha << 24) | (UIConstants.Text.MUTED & 0x00FFFFFF);
        graphics.drawString(font, currentCategory.getDescription(), contentX + animOffset, contentY + 12, descColor, false);

        // Separatore
        int sepY = contentY + 28;
        graphics.fill(contentX, sepY, contentX + contentWidth, sepY + 1, UIConstants.Border.SEPARATOR);

        // Contenuto pagina
        int pageY = sepY + PADDING;
        int pageHeight = contentHeight - 40;

        if (page != null) {
            // Apply content fade during transition
            if (categoryTransitionProgress >= 0.3f) {
                page.render(graphics, font, contentX + animOffset, pageY, contentWidth, pageHeight, mouseX, mouseY);
            }
        } else {
            // Placeholder per pagine non implementate
            String placeholder = "This section is coming soon...";
            int placeholderWidth = font.width(placeholder);
            graphics.drawString(font, placeholder,
                contentX + (contentWidth - placeholderWidth) / 2,
                pageY + pageHeight / 2,
                UIConstants.Text.DISABLED, false);
        }
    }

    // === Footer ===

    private void renderFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        int footerY = height - FOOTER_HEIGHT;

        // Background
        graphics.fill(0, footerY, width, height, UIConstants.Background.HEADER);
        graphics.fill(0, footerY, width, footerY + 1, UIConstants.Border.DEFAULT);

        // Bottoni a sinistra
        int buttonY = footerY + (FOOTER_HEIGHT - UIConstants.Size.BUTTON_HEIGHT) / 2;
        int buttonX = PADDING;

        // Reset Page button
        boolean resetHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, buttonX, buttonY, 80, UIConstants.Size.BUTTON_HEIGHT);
        AxiomRenderer.drawButton(graphics, font, buttonX, buttonY, 80, UIConstants.Size.BUTTON_HEIGHT,
            "Reset Page", resetHovered, false);

        // Factory Reset button (dangerous action - red color)
        buttonX += 90;
        boolean factoryResetHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, buttonX, buttonY, 100, UIConstants.Size.BUTTON_HEIGHT);
        if (factoryResetHovered) {
            graphics.fill(buttonX, buttonY, buttonX + 100, buttonY + UIConstants.Size.BUTTON_HEIGHT, UIConstants.Status.ERROR);
        } else {
            graphics.fill(buttonX, buttonY, buttonX + 100, buttonY + UIConstants.Size.BUTTON_HEIGHT, UIConstants.Accent.RED);
        }
        AxiomRenderer.drawBorder(graphics, buttonX, buttonY, 100, UIConstants.Size.BUTTON_HEIGHT, UIConstants.Border.DEFAULT);
        String factoryText = "Factory Reset";
        int factoryTextWidth = font.width(factoryText);
        graphics.drawString(font, factoryText, buttonX + (100 - factoryTextWidth) / 2,
            buttonY + (UIConstants.Size.BUTTON_HEIGHT - 8) / 2, UIConstants.Text.WHITE, false);

        // Bottoni a destra
        int rightButtonX = width - PADDING - 80;

        // Close button
        boolean closeHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, rightButtonX, buttonY, 80, UIConstants.Size.BUTTON_HEIGHT);
        AxiomRenderer.drawButton(graphics, font, rightButtonX, buttonY, 80, UIConstants.Size.BUTTON_HEIGHT,
            "Close", closeHovered, false);

        // Apply button
        rightButtonX -= 90;
        boolean applyHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, rightButtonX, buttonY, 80, UIConstants.Size.BUTTON_HEIGHT);
        AxiomRenderer.drawButton(graphics, font, rightButtonX, buttonY, 80, UIConstants.Size.BUTTON_HEIGHT,
            "Apply", applyHovered, hasUnsavedChanges());

        // Hint a centro
        String hint = "Press ESC or K to close";
        int hintWidth = font.width(hint);
        graphics.drawString(font, hint, (width - hintWidth) / 2, buttonY + 5, UIConstants.Text.MUTED, false);
    }

    // === Input Handling ===

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle reset confirmation dialog first (blocks other interactions)
        if (showResetConfirmation) {
            return handleResetDialogClick((int) mouseX, (int) mouseY);
        }

        // Handle factory reset confirmation dialog
        if (showFactoryResetConfirmation) {
            return handleFactoryResetDialogClick((int) mouseX, (int) mouseY);
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

                // Clear button
                if (!searchQuery.isEmpty() && mouseX >= PADDING + searchBoxWidth - 14) {
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

        // Footer buttons
        int footerY = height - FOOTER_HEIGHT;
        int buttonY = footerY + (FOOTER_HEIGHT - UIConstants.Size.BUTTON_HEIGHT) / 2;

        // Reset Page button - show confirmation dialog
        if (AxiomRenderer.isMouseOver((int) mouseX, (int) mouseY, PADDING, buttonY, 80, UIConstants.Size.BUTTON_HEIGHT)) {
            showResetConfirmation = true;
            return true;
        }

        // Factory Reset button - show factory reset confirmation
        int factoryResetX = PADDING + 90;
        if (AxiomRenderer.isMouseOver((int) mouseX, (int) mouseY, factoryResetX, buttonY, 100, UIConstants.Size.BUTTON_HEIGHT)) {
            showFactoryResetConfirmation = true;
            return true;
        }

        // Close button
        int closeButtonX = width - PADDING - 80;
        if (AxiomRenderer.isMouseOver((int) mouseX, (int) mouseY, closeButtonX, buttonY, 80, UIConstants.Size.BUTTON_HEIGHT)) {
            onClose();
            return true;
        }

        // Apply button
        int applyButtonX = closeButtonX - 90;
        if (AxiomRenderer.isMouseOver((int) mouseX, (int) mouseY, applyButtonX, buttonY, 80, UIConstants.Size.BUTTON_HEIGHT)) {
            applyChanges();
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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

        // K per chiudere (stesso tasto per aprire)
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

        // Passa alla pagina corrente
        SettingsPage page = pages.get(currentCategory);
        if (page != null && page.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        // Handle search text input
        if (searchFocused && Character.isLetterOrDigit(codePoint) || codePoint == ' ') {
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
        SettingsPage page = pages.get(currentCategory);
        if (page != null) {
            return page.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // === Actions ===

    private void selectCategory(SettingsCategory category) {
        if (category != currentCategory) {
            // Chiudi pagina corrente
            SettingsPage oldPage = pages.get(currentCategory);
            if (oldPage != null) {
                oldPage.onClose();
            }

            // Start transition animation
            transitionFromCategory = currentCategory;
            categoryTransitionProgress = 0.0f;

            // Cambia categoria
            currentCategory = category;
            scrollOffset = 0;

            // Inizializza nuova pagina
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

    private boolean hasUnsavedChanges() {
        SettingsPage page = pages.get(currentCategory);
        return page != null && page.hasUnsavedChanges();
    }

    @Override
    public void onClose() {
        // Restore original blur setting
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            mc.options.menuBackgroundBlurriness().set(originalBlurValue);
        }

        // Salva modifiche se necessario
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
        graphics.fill(0, 0, this.width, this.height, UIConstants.Background.SCREEN);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // Do nothing - disable blur completely
    }

    @Override
    protected void renderMenuBackground(GuiGraphics graphics) {
        // Just solid background, no dimming or blur
        graphics.fill(0, 0, this.width, this.height, UIConstants.Background.SCREEN);
    }

    // === Utility ===

    private int applyAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }
}
