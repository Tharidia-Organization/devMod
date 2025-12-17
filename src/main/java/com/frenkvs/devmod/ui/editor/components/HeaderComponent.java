package com.frenkvs.devmod.ui.editor.components;

import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.editor.core.EditorSounds;
import com.frenkvs.devmod.ui.editor.core.ResponsiveLayout;
import com.frenkvs.devmod.ui.editor.core.ScaledCoord;
import com.frenkvs.devmod.ui.editor.core.Typography;
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

    private static final int HEIGHT = UIConstants.Size.HEADER_HEIGHT;  // 28px base
    private static final int TAB_WIDTH = UIConstants.Size.TAB_WIDTH;
    private static final int TAB_HEIGHT = UIConstants.Size.TAB_HEIGHT;
    private static final int TAB_GAP = UIConstants.Size.TAB_GAP;
    private static final int CLOSE_BUTTON_SIZE = 20;
    private static final int ARROW_WIDTH = 14;
    private static final int LEFT_PADDING = 10;
    private static final int BORDER_THICKNESS = 1;
    private static final int SCROLL_TOLERANCE = 1;
    private static final int FADE_COLOR_SOLID = 0xAA101010;
    private static final int FADE_COLOR_TRANSPARENT = 0x00101010;

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
    private boolean leftArrowHovered = false;
    private boolean rightArrowHovered = false;
    private boolean tabsOverflow = false;
    private double tabScrollOffset = 0;
    private double tabMaxScroll = 0;

    // Mode badges
    private final ModeBadge modeBadge;
    private final ModeBadge scopeBadge;

    // Bounds
    private ResponsiveLayout.Rect bounds = ResponsiveLayout.Rect.EMPTY;
    private final List<ResponsiveLayout.Rect> tabBounds = new ArrayList<>();
    private ResponsiveLayout.Rect tabsViewport = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect leftArrowBounds = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect rightArrowBounds = ResponsiveLayout.Rect.EMPTY;
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
        // Scale dimensions to current UI scale
        int headerHeight = ScaledCoord.scaleDim(HEIGHT);
        int tabWidth = ScaledCoord.scaleDim(TAB_WIDTH);
        int tabHeight = ScaledCoord.scaleDim(TAB_HEIGHT);
        int tabGap = ScaledCoord.scaleDim(TAB_GAP);
        int closeSize = ScaledCoord.scaleDim(CLOSE_BUTTON_SIZE);
        int arrowWidth = ScaledCoord.scaleDim(ARROW_WIDTH);
        int leftPadding = ScaledCoord.scaleDim(LEFT_PADDING);

        this.bounds = new ResponsiveLayout.Rect(x, y, width, headerHeight);
        tabBounds.clear();
        leftArrowBounds = ResponsiveLayout.Rect.EMPTY;
        rightArrowBounds = ResponsiveLayout.Rect.EMPTY;
        tabsViewport = ResponsiveLayout.Rect.EMPTY;

        // Background
        graphics.fill(x, y, x + width, y + headerHeight, UIConstants.Background.HEADER());

        // Bottom border
        graphics.fill(x, y + headerHeight - BORDER_THICKNESS, x + width, y + headerHeight,
            UIConstants.Border.SEPARATOR());

        // Calculate available width for tabs (reserve space for badges + close)
        int badgeGap = ScaledCoord.scaleDim(UIConstants.Spacing.MD);
        int rightPadding = ScaledCoord.scaleDim(UIConstants.Spacing.MD);
        int reservedRight = rightPadding + closeSize + badgeGap
            + scopeBadge.getWidth() + badgeGap + modeBadge.getWidth();
        int availableWidth = Math.max(0, width - reservedRight - leftPadding);

        int totalTabWidth = tabs.isEmpty() ? 0 :
            tabs.size() * tabWidth + (tabs.size() - 1) * tabGap;
        tabsOverflow = totalTabWidth > availableWidth;

        int tabY = y + (headerHeight - tabHeight) / 2;
        int tabsAreaX = x + leftPadding;
        int tabsAreaWidth = availableWidth;

        // Adjust for arrow buttons when overflowing
        if (tabsOverflow) {
            int arrowPad = arrowWidth + ScaledCoord.scaleDim(UIConstants.Spacing.SM);
            tabsAreaX += arrowPad;
            tabsAreaWidth -= arrowPad * 2;
            tabMaxScroll = Math.max(0, totalTabWidth - tabsAreaWidth);
            tabScrollOffset = Math.max(0, Math.min(tabScrollOffset, tabMaxScroll));

            // Arrow bounds
            leftArrowBounds = new ResponsiveLayout.Rect(x + leftPadding, tabY, arrowWidth, tabHeight);
            rightArrowBounds = new ResponsiveLayout.Rect(x + width - reservedRight - arrowWidth, tabY, arrowWidth, tabHeight);
            leftArrowHovered = leftArrowBounds.contains(mouseX, mouseY);
            rightArrowHovered = rightArrowBounds.contains(mouseX, mouseY);
        } else {
            tabScrollOffset = 0;
            tabMaxScroll = 0;
        }

        tabsViewport = new ResponsiveLayout.Rect(tabsAreaX, tabY, Math.max(0, tabsAreaWidth), tabHeight);

        // Render tabs with clipping
        graphics.enableScissor(tabsViewport.x(), tabsViewport.y(),
            tabsViewport.x() + tabsViewport.width(), tabsViewport.y() + tabsViewport.height());

        int tabStartX;
        if (tabsOverflow) {
            tabStartX = tabsViewport.x() - (int) Math.round(tabScrollOffset);
        } else {
            int centeredOffset = (tabsAreaWidth - totalTabWidth) / 2;
            tabStartX = tabsViewport.x() + Math.max(0, centeredOffset);
        }

        for (int i = 0; i < tabs.size(); i++) {
            TabInfo tab = tabs.get(i);
            int tabX = tabStartX + i * (tabWidth + tabGap);
            ResponsiveLayout.Rect tabRect = new ResponsiveLayout.Rect(tabX, tabY, tabWidth, tabHeight);
            tabBounds.add(tabRect);

            boolean isSelected = (i == selectedTabIndex);
            boolean isHovered = tabRect.contains(mouseX, mouseY) && tabsViewport.contains(mouseX, mouseY);

            // Only render if visible within viewport to avoid overdraw
            if (tabX + tabWidth >= tabsViewport.x() && tabX <= tabsViewport.x() + tabsViewport.width()) {
                renderTab(graphics, font, tabX, tabY, tabWidth, tabHeight, tab.label(), isSelected, isHovered);
            }
        }

        graphics.disableScissor();

        // Fade edges + arrows on overflow
        if (tabsOverflow) {
            renderArrow(graphics, font, leftArrowBounds, "◀", tabScrollOffset > 0, leftArrowHovered);
            renderArrow(graphics, font, rightArrowBounds, "▶", tabScrollOffset < tabMaxScroll - SCROLL_TOLERANCE,
                rightArrowHovered);

            int fadeWidth = ScaledCoord.scaleDim(UIConstants.Spacing.MD);
            int fadeTop = tabY;
            int fadeBottom = tabY + tabHeight;
            graphics.fillGradient(tabsViewport.x(), fadeTop, tabsViewport.x() + fadeWidth, fadeBottom,
                FADE_COLOR_SOLID, FADE_COLOR_TRANSPARENT);
            graphics.fillGradient(tabsViewport.x() + tabsViewport.width() - fadeWidth, fadeTop,
                tabsViewport.x() + tabsViewport.width(), fadeBottom,
                FADE_COLOR_TRANSPARENT, FADE_COLOR_SOLID);
        }

        // Right side components
        int rightX = x + width;

        // Close button (right aligned with padding)
        int closeX = rightX - closeSize - rightPadding;
        int closeY = y + (headerHeight - closeSize) / 2;
        closeBounds = new ResponsiveLayout.Rect(closeX, closeY, closeSize, closeSize);
        closeButtonHovered = closeBounds.contains(mouseX, mouseY);
        renderCloseButton(graphics, font, closeX, closeY);

        int badgeY = y + (headerHeight - scopeBadge.getHeight()) / 2;
        int scopeX = closeX - badgeGap - scopeBadge.getWidth();
        scopeBadge.render(graphics, scopeX, badgeY, mouseX, mouseY);

        int modeX = scopeX - badgeGap - modeBadge.getWidth();
        modeBadge.render(graphics, modeX, badgeY, mouseX, mouseY);

        return headerHeight;
    }

    /**
     * Render dropdown overlays for badges after main content so they are not occluded.
     */
    public void renderOverlays(GuiGraphics graphics, int mouseX, int mouseY) {
        modeBadge.renderOverlay(graphics, mouseX, mouseY);
        scopeBadge.renderOverlay(graphics, mouseX, mouseY);
    }

    private void renderTab(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                           int x, int y, int width, int height, String label, boolean selected, boolean hovered) {
        // Background
        int bgColor = selected ? UIConstants.Tab.SELECTED :
                (hovered ? UIConstants.Tab.HOVER : UIConstants.Tab.NORMAL);
        graphics.fill(x, y, x + width, y + height, bgColor);

        // Selected indicator (bottom line)
        if (selected) {
            int indicatorH = ScaledCoord.scaleDim(UIConstants.Spacing.XS);
            graphics.fill(x, y + height - indicatorH, x + width, y + height,
                    UIConstants.Tab.INDICATOR);
        }

        // Border on hover
        if (hovered && !selected) {
            AxiomRenderer.drawBorder(graphics, x, y, width, height, UIConstants.Border.HOVER());
        }

        // Text (centered) with UI-scale-aware font
        String displayLabel = label != null ? label : "";
        float fontScale = Typography.tabLabelScale();
        int textWidth = font.width(displayLabel);
        int textWidthScaled = Math.round(textWidth * fontScale);
        int textX = x + (width - textWidthScaled) / 2;
        int textHeight = Math.round(font.lineHeight * fontScale);
        int textY = y + (height - textHeight) / 2;
        int textColor = selected ? UIConstants.Text.PRIMARY() :
                (hovered ? UIConstants.Text.PRIMARY() : UIConstants.Text.SECONDARY());
        Typography.drawText(graphics, font, displayLabel, textX, textY, textColor, fontScale);
    }

    private void renderArrow(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                             ResponsiveLayout.Rect bounds, String label,
                             boolean enabled, boolean hovered) {
        String safeLabel = Objects.requireNonNull(label, "arrow label cannot be null");
        float fontScale = Typography.buttonScale();
        int bgColor = !enabled ? UIConstants.Button.DISABLED() :
            (hovered ? UIConstants.Button.HOVER() : UIConstants.Button.NORMAL());
        graphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), bgColor);
        int borderColor = enabled && hovered ? UIConstants.Border.ACCENT() : UIConstants.Border.DEFAULT();
        AxiomRenderer.drawBorder(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(), borderColor);
        int textWidthScaled = Math.round(font.width(safeLabel) * fontScale);
        int textHeightScaled = Math.round(font.lineHeight * fontScale);
        int textX = bounds.x() + (bounds.width() - textWidthScaled) / 2;
        int textY = bounds.y() + (bounds.height() - textHeightScaled) / 2;
        int textColor = enabled ? UIConstants.Text.PRIMARY() : UIConstants.Text.DISABLED();
        Typography.drawText(graphics, font, safeLabel, textX, textY, textColor, fontScale);
    }

    private void renderCloseButton(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                   int x, int y) {
        int size = ScaledCoord.scaleDim(CLOSE_BUTTON_SIZE);
        // Background
        int bgColor = closeButtonHovered ? UIConstants.Button.DANGER_HOVER : UIConstants.Background.INPUT();
        graphics.fill(x, y, x + size, y + size, bgColor);

        // Border
        int borderColor = closeButtonHovered ? UIConstants.Accent.RED() : UIConstants.Border.DEFAULT();
        AxiomRenderer.drawBorder(graphics, x, y, size, size, borderColor);

        // X symbol
        String closeSymbol = "✕";
        int textWidth = font.width(closeSymbol);
        int textX = x + (size - textWidth) / 2;
        int textY = y + (size - font.lineHeight) / 2;
        int textColor = closeButtonHovered ? UIConstants.Text.PRIMARY() : UIConstants.Text.SECONDARY();
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

        if (tabsOverflow) {
            double step = ScaledCoord.scaleDim(TAB_WIDTH + TAB_GAP);
            if (leftArrowBounds.contains(mouseX, mouseY)) {
                scrollTabs(-step);
                return true;
            }
            if (rightArrowBounds.contains(mouseX, mouseY)) {
                scrollTabs(step);
                return true;
            }
        }

        // Check tabs
        for (int i = 0; i < tabBounds.size(); i++) {
            if (tabsViewport.contains(mouseX, mouseY) && tabBounds.get(i).contains(mouseX, mouseY)) {
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

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!tabsOverflow || tabsViewport.isEmpty()) return false;
        if (!tabsViewport.contains(mouseX, mouseY)) return false;

        scrollTabs(-scrollY * ScaledCoord.scaleDim(TAB_WIDTH + TAB_GAP));
        return true;
    }

    private void scrollTabs(double delta) {
        tabScrollOffset = Math.max(0, Math.min(tabScrollOffset + delta, tabMaxScroll));
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
