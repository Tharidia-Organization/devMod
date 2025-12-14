package com.frenkvs.devmod.ui.editor.components;

import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.editor.core.EditorSounds;
import com.frenkvs.devmod.ui.editor.core.ResponsiveLayout;
import com.frenkvs.devmod.ui.editor.core.UIConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Header component containing tabs, mode badges, and close button.
 *
 * @see EDITOR_DESIGN_SYSTEM.md Section 2.4 (Header Zone)
 */
public class HeaderComponent {

    // ═══════════════════════════════════════════════════════════════
    // DIMENSIONS (from Section 2.4)
    // ═══════════════════════════════════════════════════════════════

    private static final int HEIGHT = UIConstants.Size.HEADER_HEIGHT;  // 28px
    private static final int TAB_WIDTH = 64;
    private static final int TAB_HEIGHT = 20;
    private static final int TAB_GAP = 4;
    private static final int CLOSE_BUTTON_SIZE = 20;

    // ═══════════════════════════════════════════════════════════════
    // TAB INFO
    // ═══════════════════════════════════════════════════════════════

    /**
     * Tab definition for the header.
     */
    public record TabInfo(String id, String label, String tooltip) {}

    // ═══════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════

    private final List<TabInfo> tabs = new ArrayList<>();
    private int selectedTabIndex = 0;
    private boolean closeButtonHovered = false;

    // Mode badges
    private final ModeBadge modeBadge;
    private final ModeBadge scopeBadge;

    // Bounds
    private ResponsiveLayout.Rect bounds = ResponsiveLayout.Rect.EMPTY;
    private final List<ResponsiveLayout.Rect> tabBounds = new ArrayList<>();
    private ResponsiveLayout.Rect closeBounds = ResponsiveLayout.Rect.EMPTY;

    // Callbacks
    private Consumer<Integer> onTabChange;
    private Runnable onClose;

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public HeaderComponent() {
        this.modeBadge = new ModeBadge(ModeBadge.BadgeType.MODE);
        this.scopeBadge = new ModeBadge(ModeBadge.BadgeType.SCOPE);
    }

    // ═══════════════════════════════════════════════════════════════
    // CONFIGURATION
    // ═══════════════════════════════════════════════════════════════

    public HeaderComponent addTab(String id, String label, String tooltip) {
        tabs.add(new TabInfo(id, label, tooltip));
        return this;
    }

    public HeaderComponent addTab(String id, String label) {
        return addTab(id, label, null);
    }

    public HeaderComponent clearTabs() {
        tabs.clear();
        tabBounds.clear();
        selectedTabIndex = 0;
        return this;
    }

    public HeaderComponent selectTab(int index) {
        if (index >= 0 && index < tabs.size()) {
            this.selectedTabIndex = index;
        }
        return this;
    }

    public HeaderComponent selectTab(String id) {
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).id().equals(id)) {
                this.selectedTabIndex = i;
                break;
            }
        }
        return this;
    }

    public HeaderComponent onTabChange(Consumer<Integer> callback) {
        this.onTabChange = callback;
        return this;
    }

    public HeaderComponent onClose(Runnable callback) {
        this.onClose = callback;
        return this;
    }

    // ═══════════════════════════════════════════════════════════════
    // BADGE ACCESS
    // ═══════════════════════════════════════════════════════════════

    public ModeBadge getModeBadge() {
        return modeBadge;
    }

    public ModeBadge getScopeBadge() {
        return scopeBadge;
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDERING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Render the header at the given position.
     * @return The height consumed
     */
    public int render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
        var font = Objects.requireNonNull(Minecraft.getInstance().font, "font cannot be null");

        this.bounds = new ResponsiveLayout.Rect(x, y, width, HEIGHT);
        tabBounds.clear();

        // Background
        graphics.fill(x, y, x + width, y + HEIGHT, UIConstants.Background.HEADER);

        // Bottom border
        graphics.fill(x, y + HEIGHT - 1, x + width, y + HEIGHT, UIConstants.Border.SEPARATOR);

        // Calculate tab positions (centered)
        int totalTabWidth = tabs.size() * TAB_WIDTH + (tabs.size() - 1) * TAB_GAP;
        int tabStartX = x + (width - totalTabWidth) / 2;
        int tabY = y + (HEIGHT - TAB_HEIGHT) / 2;

        // Render tabs
        for (int i = 0; i < tabs.size(); i++) {
            TabInfo tab = tabs.get(i);
            int tabX = tabStartX + i * (TAB_WIDTH + TAB_GAP);
            ResponsiveLayout.Rect tabRect = new ResponsiveLayout.Rect(tabX, tabY, TAB_WIDTH, TAB_HEIGHT);
            tabBounds.add(tabRect);

            boolean isSelected = (i == selectedTabIndex);
            boolean isHovered = tabRect.contains(mouseX, mouseY);

            renderTab(graphics, font, tabX, tabY, tab.label(), isSelected, isHovered);
        }

        // Right side components
        int rightX = x + width;

        // Close button (right - 24)
        int closeX = rightX - CLOSE_BUTTON_SIZE - 4;
        int closeY = y + (HEIGHT - CLOSE_BUTTON_SIZE) / 2;
        closeBounds = new ResponsiveLayout.Rect(closeX, closeY, CLOSE_BUTTON_SIZE, CLOSE_BUTTON_SIZE);
        closeButtonHovered = closeBounds.contains(mouseX, mouseY);
        renderCloseButton(graphics, font, closeX, closeY);

        // Scope badge (right - 110)
        int scopeX = rightX - 110;
        int badgeY = y + (HEIGHT - scopeBadge.getHeight()) / 2;
        scopeBadge.render(graphics, scopeX, badgeY, mouseX, mouseY);

        // Mode badge (right - 200)
        int modeX = rightX - 200;
        modeBadge.render(graphics, modeX, badgeY, mouseX, mouseY);

        return HEIGHT;
    }

    private void renderTab(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                          int x, int y, String label, boolean selected, boolean hovered) {
        // Background
        int bgColor = selected ? UIConstants.Tab.SELECTED :
                     (hovered ? UIConstants.Tab.HOVER : UIConstants.Tab.NORMAL);
        graphics.fill(x, y, x + TAB_WIDTH, y + TAB_HEIGHT, bgColor);

        // Selected indicator (bottom line)
        if (selected) {
            graphics.fill(x, y + TAB_HEIGHT - 2, x + TAB_WIDTH, y + TAB_HEIGHT,
                         UIConstants.Tab.INDICATOR);
        }

        // Border on hover
        if (hovered && !selected) {
            AxiomRenderer.drawBorder(graphics, x, y, TAB_WIDTH, TAB_HEIGHT, UIConstants.Border.HOVER);
        }

        // Text (centered)
        String displayLabel = label != null ? label : "";
        int textWidth = font.width(displayLabel);
        int textX = x + (TAB_WIDTH - textWidth) / 2;
        int textY = y + (TAB_HEIGHT - 8) / 2;
        int textColor = selected ? UIConstants.Text.PRIMARY :
                       (hovered ? UIConstants.Text.PRIMARY : UIConstants.Text.SECONDARY);
        graphics.drawString(font, displayLabel, textX, textY, textColor, false);
    }

    private void renderCloseButton(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                   int x, int y) {
        // Background
        int bgColor = closeButtonHovered ? UIConstants.Button.DANGER_HOVER : UIConstants.Background.INPUT;
        graphics.fill(x, y, x + CLOSE_BUTTON_SIZE, y + CLOSE_BUTTON_SIZE, bgColor);

        // Border
        int borderColor = closeButtonHovered ? UIConstants.Accent.RED : UIConstants.Border.DEFAULT;
        AxiomRenderer.drawBorder(graphics, x, y, CLOSE_BUTTON_SIZE, CLOSE_BUTTON_SIZE, borderColor);

        // X symbol
        String closeSymbol = "✕";
        int textWidth = font.width(closeSymbol);
        int textX = x + (CLOSE_BUTTON_SIZE - textWidth) / 2;
        int textY = y + (CLOSE_BUTTON_SIZE - 8) / 2;
        int textColor = closeButtonHovered ? UIConstants.Text.PRIMARY : UIConstants.Text.SECONDARY;
        graphics.drawString(font, closeSymbol, textX, textY, textColor, false);
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        // Check badges first (they may have dropdowns open)
        if (modeBadge.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (scopeBadge.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Check close button
        if (closeBounds.contains(mouseX, mouseY)) {
            EditorSounds.playButtonClick();
            if (onClose != null) {
                onClose.run();
            }
            return true;
        }

        // Check tabs
        for (int i = 0; i < tabBounds.size(); i++) {
            if (tabBounds.get(i).contains(mouseX, mouseY)) {
                if (i != selectedTabIndex) {
                    selectedTabIndex = i;
                    EditorSounds.playTabSwitch();
                    if (onTabChange != null) {
                        onTabChange.accept(i);
                    }
                }
                return true;
            }
        }

        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Number keys 1-9 to switch tabs
        if (keyCode >= org.lwjgl.glfw.GLFW.GLFW_KEY_1 && keyCode <= org.lwjgl.glfw.GLFW.GLFW_KEY_9) {
            int tabIndex = keyCode - org.lwjgl.glfw.GLFW.GLFW_KEY_1;
            if (tabIndex < tabs.size() && tabIndex != selectedTabIndex) {
                selectedTabIndex = tabIndex;
                EditorSounds.playTabSwitch();
                if (onTabChange != null) {
                    onTabChange.accept(tabIndex);
                }
                return true;
            }
        }

        // F5 to toggle mode
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_F5) {
            modeBadge.toggle();
            return true;
        }

        return false;
    }

    /**
     * Close any open dropdowns.
     */
    public void closeDropdowns() {
        modeBadge.closeDropdown();
        scopeBadge.closeDropdown();
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════

    public List<TabInfo> getTabs() {
        return tabs;
    }

    public int getSelectedTabIndex() {
        return selectedTabIndex;
    }

    public TabInfo getSelectedTab() {
        if (selectedTabIndex >= 0 && selectedTabIndex < tabs.size()) {
            return tabs.get(selectedTabIndex);
        }
        return null;
    }

    public String getSelectedTabId() {
        TabInfo tab = getSelectedTab();
        return tab != null ? tab.id() : null;
    }

    public int getHeight() {
        return HEIGHT;
    }

    public ResponsiveLayout.Rect getBounds() {
        return bounds;
    }

    public boolean isPreviewMode() {
        return modeBadge.isPreviewMode();
    }

    public boolean isApplyMode() {
        return modeBadge.isApplyMode();
    }

    public boolean isGlobalScope() {
        return scopeBadge.isGlobalScope();
    }

    public boolean isSpecificScope() {
        return scopeBadge.isSpecificScope();
    }
}
