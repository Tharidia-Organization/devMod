package com.devmod.client.ui.testing;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.telemetry.UiTelemetry;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.UIConstants;

@OnlyIn(Dist.CLIENT)
public class VoxelLabScreen extends Screen {

    private static final int HEADER_HEIGHT = 50;
    private static final int TAB_BAR_HEIGHT = 32;
    private static final int PADDING = UIConstants.Spacing.PADDING_LG;
    private static final int TAB_SPACING = 4;

    // Tab management
    private final Map<VoxelLabTab, VoxelLabPage> pages = new EnumMap<>(VoxelLabTab.class);
    private final Map<VoxelLabTab, EditorButton> tabButtons = new EnumMap<>(VoxelLabTab.class);
    private VoxelLabTab activeTab = VoxelLabTab.OVERVIEW;

    // Close button
    @Nullable
    private EditorButton closeButton;

    public VoxelLabScreen() {
        super(java.util.Objects.requireNonNull(Component.literal("Voxel Lab"), "title"));
    }

    // ═══════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        // Track screen open for telemetry
        UiTelemetry.screenOpened("testing", "voxel_lab");

        // Create tab buttons
        createTabButtons();

        // Create close button
        getCloseButton();

        // Register pages
        registerPages();

        // Initialize active page
        int contentX = PADDING;
        int contentY = HEADER_HEIGHT + TAB_BAR_HEIGHT;
        int contentWidth = width - PADDING * 2;
        int contentHeight = height - contentY - PADDING;

        for (VoxelLabPage page : pages.values()) {
            page.init(contentX, contentY, contentWidth, contentHeight);
        }

        // Activate initial tab
        VoxelLabPage activePage = pages.get(activeTab);
        if (activePage != null) {
            activePage.onActivate();
        }
    }

    @Override
    public void onClose() {
        // Track screen close for telemetry
        UiTelemetry.screenClosed("testing", "voxel_lab");
        super.onClose();
    }

    private void createTabButtons() {
        tabButtons.clear();

        for (VoxelLabTab tab : VoxelLabTab.values()) {
            EditorButton button = new EditorButton("tab-" + tab.name().toLowerCase(), tab.getLabel())
                .style(tab == activeTab ? EditorButton.Style.PRIMARY : EditorButton.Style.GHOST)
                .size(EditorButton.Size.SMALL)
                .onClick(() -> setActiveTab(tab));
            tabButtons.put(tab, button);
        }
    }

    private void registerPages() {
        pages.clear();

        // Register all pages
        registerPage(new com.devmod.client.ui.testing.pages.OverviewPage());
        registerPage(new com.devmod.client.ui.testing.pages.DebugOverlaysPage());
        registerPage(new com.devmod.client.ui.testing.pages.HudSystemsPage());
        registerPage(new com.devmod.client.ui.testing.pages.TelemetryPage());
        registerPage(new com.devmod.client.ui.testing.pages.EffectsPage());
        registerPage(new com.devmod.client.ui.testing.pages.CombatPage());
        registerPage(new com.devmod.client.ui.testing.pages.ComponentShowcasePage());
    }

    private void registerPage(VoxelLabPage page) {
        pages.put(page.getTab(), page);
    }

    // ═══════════════════════════════════════════════════════════════
    // TAB MANAGEMENT
    // ═══════════════════════════════════════════════════════════════

    public void setActiveTab(VoxelLabTab tab) {
        if (tab == activeTab) return;

        // Deactivate old page
        VoxelLabPage oldPage = pages.get(activeTab);
        if (oldPage != null) {
            oldPage.onDeactivate();
        }

        // Update button styles
        EditorButton oldButton = tabButtons.get(activeTab);
        if (oldButton != null) {
            oldButton.style(EditorButton.Style.GHOST);
        }

        activeTab = tab;

        EditorButton newButton = tabButtons.get(activeTab);
        if (newButton != null) {
            newButton.style(EditorButton.Style.PRIMARY);
        }

        // Activate new page
        VoxelLabPage newPage = pages.get(activeTab);
        if (newPage != null) {
            newPage.onActivate();
        }
    }

    public VoxelLabTab getActiveTab() {
        return activeTab;
    }

    // ═══════════════════════════════════════════════════════════════
    // TICK & RENDER
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void tick() {
        super.tick();

        VoxelLabPage activePage = pages.get(activeTab);
        if (activePage != null) {
            activePage.tick();
        }
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Background
        graphics.fill(0, 0, width, height, UIConstants.Background.CONTENT());

        var font = Objects.requireNonNull(this.font, "font");

        // Header
        renderHeader(graphics, font, mouseX, mouseY);

        // Tab bar
        renderTabBar(graphics, mouseX, mouseY);

        // Active page content
        VoxelLabPage activePage = pages.get(activeTab);
        if (activePage != null) {
            activePage.render(graphics, mouseX, mouseY, partialTick);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics graphics, net.minecraft.client.gui.Font font, int mouseX, int mouseY) {
        Font safeFont = Objects.requireNonNull(font, "font");
        // Title
        graphics.drawString(safeFont, "Voxel Lab", PADDING, PADDING, UIConstants.Text.TITLE(), false);

        // Subtitle with tab description
        String subtitle = Objects.requireNonNull(activeTab.getDescription(), "subtitle");
        graphics.drawString(safeFont, subtitle, PADDING, PADDING + 14, UIConstants.Text.SECONDARY(), false);

        // Close button
        int closeX = width - PADDING - 24;
        int closeY = PADDING;
        EditorButton closeButton = getCloseButton();
        closeButton.render(graphics, closeX, closeY, 24, 24, mouseX, mouseY);
    }

    private void renderTabBar(GuiGraphics graphics, int mouseX, int mouseY) {
        int tabY = HEADER_HEIGHT - 6;

        // Tab bar background
        graphics.fill(0, tabY, width, tabY + TAB_BAR_HEIGHT, UIConstants.Background.PANEL());

        // Render tab buttons
        int tabX = PADDING;
        for (VoxelLabTab tab : VoxelLabTab.values()) {
            EditorButton button = tabButtons.get(tab);
            if (button != null) {
                int tabWidth = calculateTabWidth(tab);
                button.render(graphics, tabX, tabY + 4, tabWidth, TAB_BAR_HEIGHT - 8, mouseX, mouseY);
                tabX += tabWidth + TAB_SPACING;
            }
        }

        // Bottom border
        graphics.fill(0, tabY + TAB_BAR_HEIGHT - 1, width, tabY + TAB_BAR_HEIGHT, UIConstants.Border.DEFAULT());
    }

    private int calculateTabWidth(VoxelLabTab tab) {
        Font safeFont = Objects.requireNonNull(this.font, "font");
        String label = Objects.requireNonNull(tab.getLabel(), "tab label");
        return safeFont.width(label) + 16;
    }

    private EditorButton getCloseButton() {
        if (closeButton == null) {
            closeButton = new EditorButton("close", "\u2715")
                .style(EditorButton.Style.GHOST)
                .size(EditorButton.Size.SMALL)
                .onClick(this::onClose);
        }
        return closeButton;
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Close button
        EditorButton closeButton = getCloseButton();
        if (closeButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Tab buttons
        int tabX = PADDING;
        int tabY = HEADER_HEIGHT - 6 + 4;
        for (VoxelLabTab tab : VoxelLabTab.values()) {
            EditorButton tabButton = tabButtons.get(tab);
            if (tabButton != null) {
                int tabWidth = calculateTabWidth(tab);
                if (mouseX >= tabX && mouseX <= tabX + tabWidth &&
                    mouseY >= tabY && mouseY <= tabY + TAB_BAR_HEIGHT - 8) {
                    if (tabButton.mouseClicked(mouseX, mouseY, button)) {
                        return true;
                    }
                }
                tabX += tabWidth + TAB_SPACING;
            }
        }

        // Active page
        VoxelLabPage activePage = pages.get(activeTab);
        if (activePage != null && activePage.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // Close button
        EditorButton closeButton = getCloseButton();
        if (closeButton.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }

        // Tab buttons
        for (EditorButton tabButton : tabButtons.values()) {
            if (tabButton.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }

        // Active page
        VoxelLabPage activePage = pages.get(activeTab);
        if (activePage != null && activePage.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        VoxelLabPage activePage = pages.get(activeTab);
        if (activePage != null && activePage.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        VoxelLabPage activePage = pages.get(activeTab);
        if (activePage != null && activePage.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        VoxelLabPage activePage = pages.get(activeTab);
        if (activePage != null && activePage.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        VoxelLabPage activePage = pages.get(activeTab);
        if (activePage != null && activePage.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void resize(@Nonnull Minecraft mc, int newWidth, int newHeight) {
        super.resize(mc, newWidth, newHeight);

        // Reinitialize all pages with new dimensions
        int contentX = PADDING;
        int contentY = HEADER_HEIGHT + TAB_BAR_HEIGHT;
        int contentWidth = newWidth - PADDING * 2;
        int contentHeight = newHeight - contentY - PADDING;

        for (VoxelLabPage page : pages.values()) {
            page.init(contentX, contentY, contentWidth, contentHeight);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
