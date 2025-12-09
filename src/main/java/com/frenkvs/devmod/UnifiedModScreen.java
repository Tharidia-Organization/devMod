package com.frenkvs.devmod;

import com.frenkvs.devmod.rendering.DebugRenderer;
import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.UIConstants;
import com.frenkvs.devmod.util.I18n;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;

/**
 * Schermata unificata per tutte le impostazioni del mod.
 * Fornisce accesso centralizzato a tutte le funzionalita.
 * Refactored con Axiom-style UI.
 */
@SuppressWarnings("null")
public class UnifiedModScreen extends Screen {

    private static final int CONTENT_WIDTH = 300;
    private static final int TAB_WIDTH = 90;
    private static final int TAB_HEIGHT = 24;
    private static final int ROW_HEIGHT = 24;

    private final Screen parent;
    private Tab currentTab = Tab.MAIN;
    private int mouseX, mouseY;

    // Blur control
    private int originalBlurValue = 0;

    // Tooltip state
    private String hoveredTooltip = null;

    private enum Tab {
        MAIN("devmod.tab.main"),
        DEBUG("devmod.tab.debug"),
        COMBAT("devmod.tab.combat"),
        ANALYTICS("devmod.tab.analytics");

        private final String translationKey;

        Tab(String translationKey) {
            this.translationKey = translationKey;
        }

        public String getName() {
            // Try to translate, fallback to simple name if key not found
            Component translated = I18n.translate(translationKey);
            String result = translated.getString();
            // If translation returns the key itself, use fallback
            if (result.equals(translationKey)) {
                return name().charAt(0) + name().substring(1).toLowerCase();
            }
            return result;
        }
    }

    public UnifiedModScreen(Screen parent) {
        super(I18n.screenTitle("devmod_config"));
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
        // No vanilla widgets - using custom rendering
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;

        // Dark background
        AxiomRenderer.drawScreenBackground(graphics, this.width, this.height);

        // Title
        AxiomRenderer.drawCenteredTitle(graphics, font, this.width, UIConstants.Position.TITLE_Y, "DevMod Configuration");

        // Tab bar
        int tabStartX = (this.width - (Tab.values().length * TAB_WIDTH)) / 2;
        int tabY = 28;

        for (int i = 0; i < Tab.values().length; i++) {
            Tab tab = Tab.values()[i];
            int tabX = tabStartX + (i * TAB_WIDTH);
            boolean selected = tab == currentTab;
            boolean hovered = AxiomRenderer.isMouseOver(mouseX, mouseY, tabX, tabY, TAB_WIDTH - 2, TAB_HEIGHT);

            AxiomRenderer.drawTab(graphics, font, tabX, tabY, TAB_WIDTH - 2, TAB_HEIGHT, tab.getName(), selected, hovered);
        }

        // Content area
        int contentX = (this.width - CONTENT_WIDTH) / 2;
        int contentY = tabY + TAB_HEIGHT + 16;

        // Tab-specific content
        switch (currentTab) {
            case MAIN -> renderMainTab(graphics, contentX, contentY);
            case DEBUG -> renderDebugTab(graphics, contentX, contentY);
            case COMBAT -> renderCombatTab(graphics, contentX, contentY);
            case ANALYTICS -> renderAnalyticsTab(graphics, contentX, contentY);
        }

        // Back button
        int buttonWidth = 100;
        int buttonX = (this.width - buttonWidth) / 2;
        int buttonY = this.height - 35;
        boolean backHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, buttonX, buttonY, buttonWidth, UIConstants.Size.BUTTON_HEIGHT);
        AxiomRenderer.drawButton(graphics, font, buttonX, buttonY, buttonWidth, UIConstants.Size.BUTTON_HEIGHT, "Back", backHovered, false);

        // Render tooltip last (on top of everything)
        if (hoveredTooltip != null) {
            AxiomRenderer.drawTooltip(graphics, font, mouseX + 12, mouseY - 10, hoveredTooltip);
        }
    }

    private void renderMainTab(GuiGraphics graphics, int x, int y) {
        hoveredTooltip = null; // Reset tooltip each frame

        // Section: Quick Access
        AxiomRenderer.drawSectionHeader(graphics, font, x, y, "Quick Access");
        y += 20;

        // Mob Configuration button
        boolean mobHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x, y, CONTENT_WIDTH, UIConstants.Size.BUTTON_HEIGHT + 8);
        drawMenuButton(graphics, x, y, CONTENT_WIDTH, "Mob Configuration", "View and modify mob stats", UIConstants.Accent.ORANGE, mobHovered);
        if (mobHovered) {
            hoveredTooltip = "§eRight-click a mob§r to open the config screen. §7Set HP, damage, armor, etc.";
        }
        y += ROW_HEIGHT + 8;

        // Weapon Editor button
        boolean weaponHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x, y, CONTENT_WIDTH, UIConstants.Size.BUTTON_HEIGHT + 8);
        drawMenuButton(graphics, x, y, CONTENT_WIDTH, "Weapon Editor", "Customize weapon damage multipliers", UIConstants.Accent.RED, weaponHovered);
        if (weaponHovered) {
            hoveredTooltip = "§eHold a weapon§r to edit its stats. §7Changes apply globally to that weapon type.";
        }
        y += ROW_HEIGHT + 16;

        // Separator
        AxiomRenderer.drawSeparator(graphics, x, y, CONTENT_WIDTH);
        y += 16;

        // Section: Settings
        AxiomRenderer.drawSectionHeader(graphics, font, x, y, "Settings Categories");
        y += 20;

        // Debug Settings
        boolean debugHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x, y, CONTENT_WIDTH, UIConstants.Size.BUTTON_HEIGHT + 8);
        drawMenuButton(graphics, x, y, CONTENT_WIDTH, "Debug Settings", "Visual overlays and debug tools", UIConstants.Accent.BLUE, debugHovered);
        if (debugHovered) {
            hoveredTooltip = "§7Toggle body part hitboxes, aggro spheres, and real-time stat overlays.";
        }
        y += ROW_HEIGHT + 8;

        // Combat Settings
        boolean combatHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x, y, CONTENT_WIDTH, UIConstants.Size.BUTTON_HEIGHT + 8);
        drawMenuButton(graphics, x, y, CONTENT_WIDTH, "Combat Settings", "Body part detection info", UIConstants.Accent.GREEN, combatHovered);
        if (combatHovered) {
            hoveredTooltip = "§7Info about damage multipliers: §cHEAD 2x§7, §fBODY 1x§7, §bLEGS 0.75x";
        }
        y += ROW_HEIGHT + 8;

        // Analytics
        boolean analyticsHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x, y, CONTENT_WIDTH, UIConstants.Size.BUTTON_HEIGHT + 8);
        drawMenuButton(graphics, x, y, CONTENT_WIDTH, "Analytics Dashboard", "Telemetry and statistics", UIConstants.Accent.PURPLE, analyticsHovered);
        if (analyticsHovered) {
            hoveredTooltip = "§7Open the telemetry dashboard. §ePress J§7 for quick access.";
        }
    }

    private void renderDebugTab(GuiGraphics graphics, int x, int y) {
        hoveredTooltip = null; // Reset tooltip

        // Debug Overlay toggle
        boolean debugEnabled = DebugRenderer.INSTANCE.isEnabled();
        boolean toggleHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x, y, CONTENT_WIDTH, ROW_HEIGHT);

        if (toggleHovered) {
            graphics.fill(x - 2, y, x + CONTENT_WIDTH + 2, y + ROW_HEIGHT, UIConstants.Background.HOVER);
            hoveredTooltip = "§7Shows hitboxes and mob info when looking at entities. §eKey: G";
        }

        graphics.drawString(font, "Debug Overlay (G key)", x, y + 6, UIConstants.Text.PRIMARY, false);
        int toggleX = x + CONTENT_WIDTH - UIConstants.Size.TOGGLE_WIDTH;
        AxiomRenderer.drawToggle(graphics, font, toggleX, y + 2, UIConstants.Size.TOGGLE_WIDTH, UIConstants.Size.TOGGLE_HEIGHT, debugEnabled, toggleHovered);
        y += ROW_HEIGHT + 16;

        // Separator
        AxiomRenderer.drawSeparator(graphics, x, y, CONTENT_WIDTH);
        y += 16;

        // Features section
        AxiomRenderer.drawSectionHeader(graphics, font, x, y, "Debug Overlay Features");
        y += 20;

        String[] features = {
            "Body part hitboxes (HEAD/BODY/ARMS/LEGS)",
            "Damage multipliers visualization",
            "Mob statistics (HP, Armor, Damage)",
            "Aggro range sphere (cyan)",
            "Real-time stat tracking"
        };

        for (String feature : features) {
            // Bullet point
            graphics.fill(x, y + 4, x + 4, y + 8, UIConstants.Accent.CYAN);
            graphics.drawString(font, feature, x + 10, y + 2, UIConstants.Text.SECONDARY, false);
            y += 16;
        }
    }

    private void renderCombatTab(GuiGraphics graphics, int x, int y) {
        hoveredTooltip = null; // Reset tooltip

        // Body Part Detection section
        AxiomRenderer.drawSectionHeader(graphics, font, x, y, "Body Part Detection");
        y += 20;

        // Body part info
        drawBodyPartInfo(graphics, x, y, "HEAD", "x2.0 damage", "75%-100% height", UIConstants.BodyPart.HEAD);
        y += 20;
        drawBodyPartInfo(graphics, x, y, "BODY", "x1.0 damage", "40%-75% height", UIConstants.BodyPart.BODY);
        y += 20;
        drawBodyPartInfo(graphics, x, y, "ARMS", "x0.9 damage", "lateral hits", UIConstants.BodyPart.ARMS);
        y += 20;
        drawBodyPartInfo(graphics, x, y, "LEGS", "x0.75 damage", "0%-40% height", UIConstants.BodyPart.LEGS);
        y += 28;

        // Separator
        AxiomRenderer.drawSeparator(graphics, x, y, CONTENT_WIDTH);
        y += 16;

        // Arrow Detection section
        AxiomRenderer.drawSectionHeader(graphics, font, x, y, "Arrow Detection");
        y += 20;

        drawArrowInfo(graphics, x, y, "Headshots", "RED + DING sound", UIConstants.Accent.RED);
        y += 18;
        drawArrowInfo(graphics, x, y, "Body shots", "GREEN indicator", UIConstants.Accent.GREEN);
        y += 18;
        drawArrowInfo(graphics, x, y, "Leg shots", "CYAN indicator", UIConstants.Accent.CYAN);
    }

    private void renderAnalyticsTab(GuiGraphics graphics, int x, int y) {
        hoveredTooltip = null; // Reset tooltip

        // Dashboard button
        boolean dashHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x, y, CONTENT_WIDTH, UIConstants.Size.BUTTON_HEIGHT + 8);
        drawMenuButton(graphics, x, y, CONTENT_WIDTH, "Open Telemetry Dashboard", "View detailed analytics and export data", UIConstants.Accent.PURPLE, dashHovered);
        if (dashHovered) {
            hoveredTooltip = "§7Real-time combat stats, heatmaps, and data export tools.";
        }
        y += ROW_HEIGHT + 24;

        // Separator
        AxiomRenderer.drawSeparator(graphics, x, y, CONTENT_WIDTH);
        y += 16;

        // Features section
        AxiomRenderer.drawSectionHeader(graphics, font, x, y, "Dashboard Features");
        y += 20;

        String[] features = {
            "Toggle all debug overlays",
            "Export heatmaps to files",
            "View real-time statistics",
            "Load heatmap visualizations"
        };

        for (String feature : features) {
            graphics.fill(x, y + 4, x + 4, y + 8, UIConstants.Accent.PURPLE);
            graphics.drawString(font, feature, x + 10, y + 2, UIConstants.Text.SECONDARY, false);
            y += 16;
        }

        y += 12;

        // Hotkey hint
        graphics.fill(x, y, x + CONTENT_WIDTH, y + 24, UIConstants.Background.INPUT);
        AxiomRenderer.drawBorder(graphics, x, y, CONTENT_WIDTH, 24, UIConstants.Border.DEFAULT);
        graphics.drawString(font, "Hotkey: J to open dashboard directly", x + 8, y + 8, UIConstants.Accent.YELLOW, false);
    }

    private void drawMenuButton(GuiGraphics graphics, int x, int y, int width, String title, String subtitle, int accentColor, boolean hovered) {
        int height = UIConstants.Size.BUTTON_HEIGHT + 8;

        // Background
        int bgColor = hovered ? UIConstants.Background.HOVER : UIConstants.Background.PANEL;
        graphics.fill(x, y, x + width, y + height, bgColor);

        // Accent bar on left
        graphics.fill(x, y, x + 3, y + height, accentColor);

        // Border
        int borderColor = hovered ? UIConstants.Border.ACCENT : UIConstants.Border.DEFAULT;
        AxiomRenderer.drawBorder(graphics, x, y, width, height, borderColor);

        // Title
        graphics.drawString(font, title, x + 10, y + 5, UIConstants.Text.PRIMARY, false);

        // Subtitle
        graphics.drawString(font, subtitle, x + 10, y + 17, UIConstants.Text.MUTED, false);

        // Arrow indicator
        graphics.drawString(font, ">", x + width - 12, y + 10, hovered ? UIConstants.Text.ACCENT : UIConstants.Text.MUTED, false);
    }

    private void drawBodyPartInfo(GuiGraphics graphics, int x, int y, String part, String damage, String range, int color) {
        // Color indicator
        graphics.fill(x, y + 2, x + 8, y + 12, color);

        // Part name
        graphics.drawString(font, part, x + 14, y + 2, color, false);

        // Damage
        graphics.drawString(font, damage, x + 60, y + 2, UIConstants.Text.PRIMARY, false);

        // Range
        graphics.drawString(font, "(" + range + ")", x + 140, y + 2, UIConstants.Text.MUTED, false);
    }

    private void drawArrowInfo(GuiGraphics graphics, int x, int y, String type, String feedback, int color) {
        graphics.fill(x, y + 3, x + 6, y + 9, color);
        graphics.drawString(font, type + ":", x + 12, y + 1, UIConstants.Text.PRIMARY, false);
        graphics.drawString(font, feedback, x + 80, y + 1, UIConstants.Text.SECONDARY, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int mx = (int) mouseX;
        int my = (int) mouseY;

        // Tab clicks
        int tabStartX = (this.width - (Tab.values().length * TAB_WIDTH)) / 2;
        int tabY = 28;

        for (int i = 0; i < Tab.values().length; i++) {
            int tabX = tabStartX + (i * TAB_WIDTH);
            if (AxiomRenderer.isMouseOver(mx, my, tabX, tabY, TAB_WIDTH - 2, TAB_HEIGHT)) {
                currentTab = Tab.values()[i];
                return true;
            }
        }

        // Content area clicks
        int contentX = (this.width - CONTENT_WIDTH) / 2;
        int contentY = tabY + TAB_HEIGHT + 16;

        switch (currentTab) {
            case MAIN -> {
                if (handleMainTabClick(mx, my, contentX, contentY)) return true;
            }
            case DEBUG -> {
                if (handleDebugTabClick(mx, my, contentX, contentY)) return true;
            }
            case ANALYTICS -> {
                if (handleAnalyticsTabClick(mx, my, contentX, contentY)) return true;
            }
        }

        // Back button
        int buttonWidth = 100;
        int buttonX = (this.width - buttonWidth) / 2;
        int buttonY = this.height - 35;
        if (AxiomRenderer.isMouseOver(mx, my, buttonX, buttonY, buttonWidth, UIConstants.Size.BUTTON_HEIGHT)) {
            this.onClose();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleMainTabClick(int mx, int my, int x, int y) {
        y += 20; // Section header

        // Mob Configuration
        if (AxiomRenderer.isMouseOver(mx, my, x, y, CONTENT_WIDTH, UIConstants.Size.BUTTON_HEIGHT + 8)) {
            if (minecraft != null) minecraft.setScreen(new com.frenkvs.devmod.ui.unified.UnifiedSettingsScreen(this));
            return true;
        }
        y += ROW_HEIGHT + 8;

        // Weapon Editor
        if (AxiomRenderer.isMouseOver(mx, my, x, y, CONTENT_WIDTH, UIConstants.Size.BUTTON_HEIGHT + 8)) {
            if (minecraft != null && minecraft.player != null) {
                if (!minecraft.player.getMainHandItem().isEmpty()) {
                    minecraft.setScreen(new WeaponEditorScreen());
                } else {
                    // Show error but stay on screen - don't close
                    minecraft.player.displayClientMessage(I18n.translate("devmod.message.must_hold_item"), true);
                }
            }
            return true;
        }
        y += ROW_HEIGHT + 16 + 16 + 20; // separator + section header

        // Debug Settings
        if (AxiomRenderer.isMouseOver(mx, my, x, y, CONTENT_WIDTH, UIConstants.Size.BUTTON_HEIGHT + 8)) {
            currentTab = Tab.DEBUG;
            return true;
        }
        y += ROW_HEIGHT + 8;

        // Combat Settings
        if (AxiomRenderer.isMouseOver(mx, my, x, y, CONTENT_WIDTH, UIConstants.Size.BUTTON_HEIGHT + 8)) {
            currentTab = Tab.COMBAT;
            return true;
        }
        y += ROW_HEIGHT + 8;

        // Analytics
        if (AxiomRenderer.isMouseOver(mx, my, x, y, CONTENT_WIDTH, UIConstants.Size.BUTTON_HEIGHT + 8)) {
            currentTab = Tab.ANALYTICS;
            return true;
        }

        return false;
    }

    private boolean handleDebugTabClick(int mx, int my, int x, int y) {
        // Debug toggle row
        if (AxiomRenderer.isMouseOver(mx, my, x, y, CONTENT_WIDTH, ROW_HEIGHT)) {
            if (DebugRenderer.INSTANCE.isEnabled()) {
                DebugRenderer.INSTANCE.disable();
            } else {
                DebugRenderer.INSTANCE.enable();
            }
            return true;
        }
        return false;
    }

    private boolean handleAnalyticsTabClick(int mx, int my, int x, int y) {
        // Dashboard button
        if (AxiomRenderer.isMouseOver(mx, my, x, y, CONTENT_WIDTH, UIConstants.Size.BUTTON_HEIGHT + 8)) {
            if (minecraft != null) {
                minecraft.setScreen(new TelemetryDashboardScreen(this));
            }
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        // Restore original blur setting
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            mc.options.menuBackgroundBlurriness().set(originalBlurValue);
        }

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
        // Disable blur - just solid dimmed background
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // Do nothing - disable blur
    }

    @Override
    protected void renderMenuBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
    }
}
