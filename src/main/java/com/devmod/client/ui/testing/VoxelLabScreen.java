package com.devmod.client.ui.testing;

import java.util.EnumMap;
import java.util.Locale;
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
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.DesignTokens;

@OnlyIn(Dist.CLIENT)
public class VoxelLabScreen extends Screen {

    private static final int HEADER_HEIGHT = 50;
    private static final int TAB_BAR_HEIGHT = 32;
    private static final int PADDING = DesignTokens.Spacing.PADDING_LG;
    private static final int TAB_SPACING = 4;

    // Tab management
    private final Map<VoxelLabTab, VoxelLabPage> pages = new EnumMap<>(VoxelLabTab.class);
    private final Map<VoxelLabTab, EditorButton> tabButtons = new EnumMap<>(VoxelLabTab.class);
    private VoxelLabTab activeTab = VoxelLabTab.OVERVIEW;

    // Close button
    @Nullable
    private EditorButton closeButton;

    private int scaledHeaderHeight;
    private int scaledTabBarHeight;
    private int scaledPadding;
    private int scaledTabSpacing;

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

        UIScaleManager.update();
        updateScaledMetrics();

        // Create tab buttons
        createTabButtons();

        // Create close button
        getCloseButton();

        // Register pages
        registerPages();

        // Initialize active page
        int contentX = scaledPadding;
        int contentY = scaledHeaderHeight + scaledTabBarHeight;
        int contentWidth = width - scaledPadding * 2;
        int contentHeight = height - contentY - scaledPadding;

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
            EditorButton button = new EditorButton("tab-" + tab.name().toLowerCase(Locale.ROOT), tab.getLabel())
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

    private void updateScaledMetrics() {
        scaledHeaderHeight = UIScaleManager.scale(HEADER_HEIGHT);
        scaledTabBarHeight = UIScaleManager.scale(TAB_BAR_HEIGHT);
        scaledPadding = UIScaleManager.scale(PADDING);
        scaledTabSpacing = UIScaleManager.scale(TAB_SPACING);
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
    public void resize(@Nonnull Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        UIScaleManager.update();
        updateScaledMetrics();
        int contentX = scaledPadding;
        int contentY = scaledHeaderHeight + scaledTabBarHeight;
        int contentWidth = width - scaledPadding * 2;
        int contentHeight = height - contentY - scaledPadding;
        for (VoxelLabPage page : pages.values()) {
            page.init(contentX, contentY, contentWidth, contentHeight);
        }
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();
        updateScaledMetrics();
        // Background
        graphics.fill(0, 0, width, height, DesignTokens.Background.CONTENT());

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
        UIScaleManager.drawScaledString(graphics, safeFont, "Voxel Lab", scaledPadding, scaledPadding, DesignTokens.Text.TITLE(), false);

        // Subtitle with tab description
        String subtitle = Objects.requireNonNull(activeTab.getDescription(), "subtitle");
        UIScaleManager.drawScaledString(graphics, safeFont, subtitle, scaledPadding, scaledPadding + UIScaleManager.scale(14), DesignTokens.Text.SECONDARY(), false);

        // Close button
        int closeSize = UIScaleManager.scale(24);
        int closeX = width - scaledPadding - closeSize;
        int closeY = scaledPadding;
        EditorButton closeButton = getCloseButton();
        closeButton.render(graphics, closeX, closeY, closeSize, closeSize, mouseX, mouseY);
    }

    private void renderTabBar(GuiGraphics graphics, int mouseX, int mouseY) {
        int tabY = scaledHeaderHeight - UIScaleManager.scale(6);

        // Tab bar background
        graphics.fill(0, tabY, width, tabY + scaledTabBarHeight, DesignTokens.Background.PANEL());

        // Render tab buttons
        int tabX = scaledPadding;
        int available = width - scaledPadding * 2;
        int tabs = VoxelLabTab.values().length;
        int maxTabW = tabs > 0 ? Math.max(UIScaleManager.scale(60), (available - scaledTabSpacing * (tabs - 1)) / tabs) : UIScaleManager.scale(80);
        for (VoxelLabTab tab : VoxelLabTab.values()) {
            EditorButton button = tabButtons.get(tab);
            if (button != null) {
                int tabWidth = Math.min(calculateTabWidth(tab), maxTabW);
                button.render(graphics, tabX, tabY + UIScaleManager.scale(4), tabWidth, scaledTabBarHeight - UIScaleManager.scale(8), mouseX, mouseY);
                tabX += tabWidth + scaledTabSpacing;
            }
        }

        // Bottom border
        graphics.fill(0, tabY + scaledTabBarHeight - 1, width, tabY + scaledTabBarHeight, DesignTokens.Border.DEFAULT());
    }

    private int calculateTabWidth(VoxelLabTab tab) {
        Font safeFont = Objects.requireNonNull(this.font, "font");
        String label = Objects.requireNonNull(tab.getLabel(), "tab label");
        return safeFont.width(label) + UIScaleManager.scale(16);
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

        // Tab buttons (EditorButton bounds are set during render)
        for (EditorButton tabButton : tabButtons.values()) {
            if (tabButton != null && tabButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
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
    public boolean isPauseScreen() {
        return false;
    }
}
