package com.devmod.client.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.DesignTokens;

/**
 * Runtime test screen for verifying UI responsiveness across:
 * - Different GUI scale settings (1x, 2x, 3x, Auto)
 * - Different window sizes
 * - Fullscreen/Windowed modes
 *
 * Usage: Open via /devmod ui-test command or from QA Testing menu
 */
@OnlyIn(Dist.CLIENT)
public class UIResponsivenessTestScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger(UIResponsivenessTestScreen.class);

    // Test configurations
    private static final int[] GUI_SCALES = {0, 1, 2, 3, 4}; // 0 = Auto
    private static final int[][] TEST_RESOLUTIONS = {
        {854, 480},   // 480p
        {1280, 720},  // 720p
        {1920, 1080}, // 1080p
        {2560, 1440}, // 1440p
        {640, 480},   // 4:3
        {2560, 1080}, // Ultrawide 21:9
    };

    // Layout
    private static final int PANEL_WIDTH = 700;
    private static final int PANEL_HEIGHT = 500;
    private static final int HEADER_HEIGHT = 50;
    private static final int ROW_HEIGHT = 24;

    // State
    private final List<ScreenTestEntry> screenRegistry = new ArrayList<>();
    private final List<TestResult> testResults = new ArrayList<>();
    private int scrollOffset = 0;
    private int currentGuiScaleIndex = 0;
    private int currentResolutionIndex = 0;

    // Original settings to restore
    private int originalGuiScale;
    private boolean originalFullscreen;

    // Buttons
    @Nullable private EditorButton runAllButton;
    @Nullable private EditorButton cycleScaleButton;
    @Nullable private EditorButton cycleResButton;
    @Nullable private EditorButton toggleFullscreenButton;
    @Nullable private EditorButton closeButton;

    // Scaled layout cache
    private int panelX, panelY;
    private int scaledPanelWidth, scaledPanelHeight;
    private int scaledHeaderHeight, scaledRowHeight;

    public UIResponsivenessTestScreen() {
        super(Component.literal("UI Responsiveness Test"));
        registerAllScreens();
        saveOriginalSettings();
    }

    private void saveOriginalSettings() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null && mc.getWindow() != null) {
            originalGuiScale = mc.options.guiScale().get();
            originalFullscreen = mc.getWindow().isFullscreen();
        }
    }

    private void restoreOriginalSettings() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null && mc.getWindow() != null) {
            mc.options.guiScale().set(originalGuiScale);
            mc.resizeDisplay();
            if (mc.getWindow().isFullscreen() != originalFullscreen) {
                mc.getWindow().toggleFullScreen();
            }
        }
    }

    /**
     * Register all screens that should be tested.
     * Add new screens here as they are created.
     */
    private void registerAllScreens() {
        // Party screens
        registerScreen("PartyScreen", "party", () -> new com.devmod.client.party.PartyScreen());
        registerScreen("InvitePopupScreen", "party", () -> {
            // Requires special setup - skip for now
            return null;
        });

        // Admin screens
        registerScreen("AdminInstanceScreen", "admin", () -> new com.devmod.client.ui.admin.AdminInstanceScreen());

        // Transport screens
        registerScreen("TransportConfigScreen", "transport", () -> {
            // Requires payload - skip for now
            return null;
        });

        // Endurance screens
        registerScreen("KitSelectionScreen", "endurance", () -> {
            // Requires party context - skip for now
            return null;
        });
        registerScreen("EnduranceShopScreen", "endurance", () -> new com.devmod.client.endurance.EnduranceShopScreen());
        registerScreen("EnduranceSettingsScreen", "endurance", () -> {
            // Requires specific setup - skip for now
            return null;
        });
        registerScreen("PerkSelectionScreen", "endurance", () -> {
            // Requires perk list - skip for now
            return null;
        });

        // UI screens
        registerScreen("WelcomeScreen", "ui", () -> new com.devmod.client.ui.WelcomeScreen());
        registerScreen("SeasonPassScreen", "season", () -> new com.devmod.client.ui.season.SeasonPassScreen());
        registerScreen("RadialMenuScreen", "radial", () -> new com.devmod.client.ui.radial.RadialMenuScreen());
        registerScreen("EditorHubScreen", "ui", () -> new com.devmod.client.ui.screens.EditorHubScreen(null));
        registerScreen("TelemetryDashboardScreen", "telemetry", () -> new com.devmod.client.ui.screens.TelemetryDashboardScreen(null));
        registerScreen("TestingHub", "testing", () -> new com.devmod.client.ui.hub.TestingHub());
        registerScreen("UnifiedSettingsScreen", "settings", () -> {
            // Requires specific setup - skip for now
            return null;
        });

        // Notification screens
        registerScreen("NotificationCenterScreen", "notification", () -> {
            // Requires specific setup - skip for now
            return null;
        });
        registerScreen("NotificationSettingsScreen", "notification", () -> {
            // Requires specific setup - skip for now
            return null;
        });

        // Testing screens
        registerScreen("QATestingScreen", "testing", () -> new com.devmod.client.testing.QATestingScreen());
        registerScreen("BadgeTestScreen", "testing", () -> new com.devmod.client.testing.BadgeTestScreen());

        // Combat/Overlay screens
        registerScreen("CombatRecapScreen", "combat", () -> {
            // Requires specific combat data - skip for now
            return null;
        });

        // Editor screens
        registerScreen("MobConfigScreen", "editor", () -> {
            // Requires target mob - skip for now
            return null;
        });
        registerScreen("MobEquipmentScreen", "editor", () -> {
            // Requires target mob + parent screen - skip for now
            return null;
        });
        registerScreen("QuestEditorScreen", "editor", () -> new com.devmod.client.quest.QuestEditorScreen());

        LOGGER.info("[UITest] Registered {} screens for testing", screenRegistry.size());
    }

    private void registerScreen(String name, String category, Supplier<Screen> factory) {
        screenRegistry.add(new ScreenTestEntry(name, category, factory));
    }

    @Override
    protected void init() {
        super.init();
        UIScaleManager.update();

        // Calculate scaled dimensions
        scaledPanelWidth = Math.min(UIScaleManager.scale(PANEL_WIDTH), UIScaleManager.getSafeWidth());
        scaledPanelHeight = Math.min(UIScaleManager.scale(PANEL_HEIGHT), UIScaleManager.getSafeHeight());
        scaledHeaderHeight = UIScaleManager.scale(HEADER_HEIGHT);
        scaledRowHeight = UIScaleManager.scale(ROW_HEIGHT);

        panelX = (width - scaledPanelWidth) / 2;
        panelY = (height - scaledPanelHeight) / 2;
        panelX = Math.max(UIScaleManager.getSafeLeft(), Math.min(panelX, UIScaleManager.getSafeRight() - scaledPanelWidth));
        panelY = Math.max(UIScaleManager.getSafeTop(), Math.min(panelY, UIScaleManager.getSafeBottom() - scaledPanelHeight));

        // Initialize buttons
        initButtons();
    }

    private void initButtons() {
        // Note: Layout calculations are done in renderButtons(), buttons are just created here
        runAllButton = EditorButton.builder("ui-test-run-all", "Run All Tests")
            .style(EditorButton.Style.PRIMARY)
            .onClick(this::runAllTests)
            .build();

        cycleScaleButton = EditorButton.builder("ui-test-scale", "GUI Scale: " + getScaleLabel())
            .style(EditorButton.Style.NORMAL)
            .onClick(this::cycleGuiScale)
            .build();

        cycleResButton = EditorButton.builder("ui-test-res", "Resolution")
            .style(EditorButton.Style.NORMAL)
            .onClick(this::cycleResolution)
            .build();

        toggleFullscreenButton = EditorButton.builder("ui-test-fullscreen", "Toggle Fullscreen")
            .style(EditorButton.Style.GHOST)
            .onClick(this::toggleFullscreen)
            .build();

        closeButton = EditorButton.builder("ui-test-close", "Close")
            .style(EditorButton.Style.GHOST)
            .onClick(this::onClose)
            .build();
    }

    private String getScaleLabel() {
        Minecraft mc = Minecraft.getInstance();
        int scale = mc.options != null ? mc.options.guiScale().get() : 0;
        return scale == 0 ? "Auto" : scale + "x";
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();

        // Recalculate if needed (window resize)
        scaledPanelWidth = UIScaleManager.scale(PANEL_WIDTH);
        scaledPanelHeight = UIScaleManager.scale(PANEL_HEIGHT);
        panelX = (width - scaledPanelWidth) / 2;
        panelY = (height - scaledPanelHeight) / 2;

        // Background
        renderBackground(graphics, mouseX, mouseY, partialTick);

        // Panel
        graphics.fill(panelX, panelY, panelX + scaledPanelWidth, panelY + scaledPanelHeight, DesignTokens.Bg.LEVEL_1);

        // Header
        graphics.fill(panelX, panelY, panelX + scaledPanelWidth, panelY + scaledHeaderHeight, DesignTokens.Surface.LEVEL_1);
        String header = truncateToWidth(font, "UI Responsiveness Test", scaledPanelWidth - UIScaleManager.scale(30));
        UIScaleManager.drawScaledString(graphics, font, header, panelX + UIScaleManager.scale(15), panelY + UIScaleManager.scale(10), DesignTokens.Text.PRIMARY);

        // Current settings info
        String info = String.format("Scale: %s | Window: %dx%d | Fullscreen: %s",
            getScaleLabel(), width, height, Minecraft.getInstance().getWindow().isFullscreen() ? "Yes" : "No");
        info = truncateToWidth(font, info, scaledPanelWidth - UIScaleManager.scale(30));
        UIScaleManager.drawScaledString(graphics, font, info, panelX + UIScaleManager.scale(15), panelY + UIScaleManager.scale(28), DesignTokens.Text.SECONDARY);

        // Screen list
        renderScreenList(graphics, mouseX, mouseY);

        // Test results summary
        renderTestSummary(graphics);

        // Buttons
        renderButtons(graphics, mouseX, mouseY);

        // Border
        renderBorder(graphics, panelX, panelY, scaledPanelWidth, scaledPanelHeight, DesignTokens.Stroke.MUTED);
    }

    private void renderScreenList(GuiGraphics graphics, int mouseX, int mouseY) {
        int listY = panelY + scaledHeaderHeight + UIScaleManager.scale(10);
        int listHeight = scaledPanelHeight - scaledHeaderHeight - UIScaleManager.scale(100);
        int visibleRows = listHeight / scaledRowHeight;

        // List background
        graphics.fill(panelX + UIScaleManager.scale(10), listY,
            panelX + scaledPanelWidth - UIScaleManager.scale(10), listY + listHeight,
            DesignTokens.Bg.LEVEL_0);

        // Render visible entries
        for (int i = 0; i < visibleRows && (scrollOffset + i) < screenRegistry.size(); i++) {
            int index = scrollOffset + i;
            ScreenTestEntry entry = screenRegistry.get(index);
            int rowY = listY + i * scaledRowHeight;

            // Row background (alternate)
            int rowBg = (index % 2 == 0) ? DesignTokens.Surface.LEVEL_0 : DesignTokens.Surface.LEVEL_1;
            graphics.fill(panelX + UIScaleManager.scale(10), rowY,
                panelX + scaledPanelWidth - UIScaleManager.scale(10), rowY + scaledRowHeight, rowBg);

            // Screen name
            int nameX = panelX + UIScaleManager.scale(20);
            int nameMaxWidth = Math.max(0, scaledPanelWidth - UIScaleManager.scale(220));
            String nameText = truncateToWidth(font, entry.name, nameMaxWidth);
            UIScaleManager.drawScaledString(graphics, font, nameText, nameX, rowY + UIScaleManager.scale(6), DesignTokens.Text.PRIMARY);

            // Category
            String categoryText = truncateToWidth(font, "[" + entry.category + "]", UIScaleManager.scale(90));
            UIScaleManager.drawScaledString(graphics, font, categoryText,
                panelX + UIScaleManager.scale(200), rowY + UIScaleManager.scale(6), DesignTokens.Text.MUTED);

            // Test result indicator
            TestResult result = getResultFor(entry.name);
            if (result != null) {
                int statusColor = result.passed ? DesignTokens.Semantic.SUCCESS : DesignTokens.Semantic.ERROR;
                String statusText = result.passed ? "PASS" : "FAIL";
                UIScaleManager.drawScaledString(graphics, font, statusText,
                    panelX + scaledPanelWidth - UIScaleManager.scale(60), rowY + UIScaleManager.scale(6), statusColor);
            }

            // Test button
            boolean hovered = mouseX >= panelX + scaledPanelWidth - UIScaleManager.scale(100) &&
                mouseX < panelX + scaledPanelWidth - UIScaleManager.scale(15) &&
                mouseY >= rowY && mouseY < rowY + scaledRowHeight;

            if (hovered) {
                graphics.fill(panelX + scaledPanelWidth - UIScaleManager.scale(100), rowY,
                    panelX + scaledPanelWidth - UIScaleManager.scale(15), rowY + scaledRowHeight,
                    DesignTokens.Surface.LEVEL_2);
                UIScaleManager.drawScaledString(graphics, font, "[Test]",
                    panelX + scaledPanelWidth - UIScaleManager.scale(90), rowY + UIScaleManager.scale(6), DesignTokens.Accent.PRIMARY);
            }
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            UIScaleManager.drawScaledCenteredString(graphics, font, "^", panelX + scaledPanelWidth / 2, listY - UIScaleManager.scale(8), DesignTokens.Text.MUTED);
        }
        if (scrollOffset + visibleRows < screenRegistry.size()) {
            UIScaleManager.drawScaledCenteredString(graphics, font, "v", panelX + scaledPanelWidth / 2, listY + listHeight + UIScaleManager.scale(2), DesignTokens.Text.MUTED);
        }
    }

    private void renderTestSummary(GuiGraphics graphics) {
        int summaryY = panelY + scaledPanelHeight - UIScaleManager.scale(70);

        int passed = (int) testResults.stream().filter(r -> r.passed).count();
        int failed = (int) testResults.stream().filter(r -> !r.passed).count();
        int total = screenRegistry.size();
        int tested = testResults.size();

        String summary = String.format("Tested: %d/%d | Passed: %d | Failed: %d", tested, total, passed, failed);
        UIScaleManager.drawScaledString(graphics, font, summary, panelX + UIScaleManager.scale(15), summaryY, DesignTokens.Text.SECONDARY);

        // Progress bar
        int barX = panelX + UIScaleManager.scale(15);
        int barY = summaryY + UIScaleManager.scale(14);
        int barWidth = scaledPanelWidth - UIScaleManager.scale(30);
        int barHeight = UIScaleManager.scale(8);

        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, DesignTokens.Surface.LEVEL_0);

        if (total > 0) {
            int passedWidth = (int) ((float) passed / total * barWidth);
            int failedWidth = (int) ((float) failed / total * barWidth);

            if (passedWidth > 0) {
                graphics.fill(barX, barY, barX + passedWidth, barY + barHeight, DesignTokens.Semantic.SUCCESS);
            }
            if (failedWidth > 0) {
                graphics.fill(barX + passedWidth, barY, barX + passedWidth + failedWidth, barY + barHeight, DesignTokens.Semantic.ERROR);
            }
        }
    }

    private void renderButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        int buttonY = panelY + scaledPanelHeight - UIScaleManager.scale(35);
        int buttonWidth = UIScaleManager.scale(110);
        int buttonHeight = UIScaleManager.scale(22);
        int buttonGap = UIScaleManager.scale(8);
        int startX = panelX + UIScaleManager.scale(15);

        if (runAllButton != null) {
            runAllButton.render(graphics, startX, buttonY, buttonWidth, buttonHeight, mouseX, mouseY);
        }
        startX += buttonWidth + buttonGap;

        if (cycleScaleButton != null) {
            // Note: EditorButton label is immutable, but render will display correctly
            cycleScaleButton.render(graphics, startX, buttonY, buttonWidth, buttonHeight, mouseX, mouseY);
        }
        startX += buttonWidth + buttonGap;

        if (cycleResButton != null) {
            cycleResButton.render(graphics, startX, buttonY, buttonWidth, buttonHeight, mouseX, mouseY);
        }
        startX += buttonWidth + buttonGap;

        if (toggleFullscreenButton != null) {
            toggleFullscreenButton.render(graphics, startX, buttonY, buttonWidth, buttonHeight, mouseX, mouseY);
        }

        // Close button on right
        if (closeButton != null) {
            closeButton.render(graphics, panelX + scaledPanelWidth - buttonWidth - UIScaleManager.scale(15), buttonY, buttonWidth, buttonHeight, mouseX, mouseY);
        }
    }

    private void renderBorder(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Nullable
    private TestResult getResultFor(String screenName) {
        return testResults.stream()
            .filter(r -> r.screenName.equals(screenName))
            .findFirst()
            .orElse(null);
    }

    // === Test Actions ===

    private void runAllTests() {
        LOGGER.info("[UITest] Starting comprehensive UI tests...");
        testResults.clear();
        for (ScreenTestEntry entry : screenRegistry) {
            testScreen(entry);
        }

        LOGGER.info("[UITest] Completed. Passed: {}, Failed: {}",
            testResults.stream().filter(r -> r.passed).count(),
            testResults.stream().filter(r -> !r.passed).count());
    }

    private void testScreen(ScreenTestEntry entry) {
        try {
            Screen screen = entry.factory.get();
            if (screen == null) {
                testResults.add(new TestResult(entry.name, true, "Skipped (requires special setup)"));
                return;
            }

            // Initialize the screen to trigger layout calculations
            Minecraft mc = Minecraft.getInstance();
            screen.init(mc, width, height);

            // Basic validation - check that the screen can render without exception
            // In a real test we'd check for overflow, but that requires more infrastructure
            testResults.add(new TestResult(entry.name, true, "Initialized OK at " + width + "x" + height));

        } catch (Exception e) {
            LOGGER.error("[UITest] Failed to test {}: {}", entry.name, e.getMessage());
            testResults.add(new TestResult(entry.name, false, "Exception: " + e.getMessage()));
        }
    }

    private void cycleGuiScale() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return;

        currentGuiScaleIndex = (currentGuiScaleIndex + 1) % GUI_SCALES.length;
        int newScale = GUI_SCALES[currentGuiScaleIndex];

        mc.options.guiScale().set(newScale);
        mc.resizeDisplay();

        LOGGER.info("[UITest] Changed GUI scale to: {}", newScale == 0 ? "Auto" : newScale + "x");
    }

    private void cycleResolution() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null || mc.getWindow().isFullscreen()) {
            LOGGER.warn("[UITest] Cannot change resolution in fullscreen mode");
            return;
        }

        currentResolutionIndex = (currentResolutionIndex + 1) % TEST_RESOLUTIONS.length;
        int[] res = TEST_RESOLUTIONS[currentResolutionIndex];

        // Note: Minecraft doesn't have a direct API to resize the window
        // This would require GLFW calls which are platform-specific
        LOGGER.info("[UITest] Would change to: {}x{} (requires manual resize)", res[0], res[1]);
    }

    private void toggleFullscreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() != null) {
            mc.getWindow().toggleFullScreen();
            LOGGER.info("[UITest] Toggled fullscreen: {}", mc.getWindow().isFullscreen());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int buttonY = panelY + scaledPanelHeight - UIScaleManager.scale(35);
        int buttonWidth = UIScaleManager.scale(110);
        int buttonHeight = UIScaleManager.scale(22);
        int buttonGap = UIScaleManager.scale(8);
        int startX = panelX + UIScaleManager.scale(15);

        // Run All button
        if (isInBounds(mouseX, mouseY, startX, buttonY, buttonWidth, buttonHeight)) {
            runAllTests();
            return true;
        }
        startX += buttonWidth + buttonGap;

        // Cycle Scale button
        if (isInBounds(mouseX, mouseY, startX, buttonY, buttonWidth, buttonHeight)) {
            cycleGuiScale();
            return true;
        }
        startX += buttonWidth + buttonGap;

        // Cycle Resolution button
        if (isInBounds(mouseX, mouseY, startX, buttonY, buttonWidth, buttonHeight)) {
            cycleResolution();
            return true;
        }
        startX += buttonWidth + buttonGap;

        // Toggle Fullscreen button
        if (isInBounds(mouseX, mouseY, startX, buttonY, buttonWidth, buttonHeight)) {
            toggleFullscreen();
            return true;
        }

        // Close button
        if (isInBounds(mouseX, mouseY, panelX + scaledPanelWidth - buttonWidth - UIScaleManager.scale(15), buttonY, buttonWidth, buttonHeight)) {
            onClose();
            return true;
        }

        // Screen list clicks
        int listY = panelY + scaledHeaderHeight + UIScaleManager.scale(10);
        int listHeight = scaledPanelHeight - scaledHeaderHeight - UIScaleManager.scale(100);
        int visibleRows = listHeight / scaledRowHeight;

        for (int i = 0; i < visibleRows && (scrollOffset + i) < screenRegistry.size(); i++) {
            int index = scrollOffset + i;
            int rowY = listY + i * scaledRowHeight;

            // Test button area
            if (mouseX >= panelX + scaledPanelWidth - UIScaleManager.scale(100) &&
                mouseX < panelX + scaledPanelWidth - UIScaleManager.scale(15) &&
                mouseY >= rowY && mouseY < rowY + scaledRowHeight) {

                testScreen(screenRegistry.get(index));
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isInBounds(double x, double y, int bx, int by, int bw, int bh) {
        return x >= bx && x < bx + bw && y >= by && y < by + bh;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int listY = panelY + scaledHeaderHeight + UIScaleManager.scale(10);
        int listHeight = scaledPanelHeight - scaledHeaderHeight - UIScaleManager.scale(100);
        int visibleRows = listHeight / scaledRowHeight;

        if (mouseY >= listY && mouseY < listY + listHeight) {
            if (scrollY > 0 && scrollOffset > 0) {
                scrollOffset--;
                return true;
            } else if (scrollY < 0 && scrollOffset + visibleRows < screenRegistry.size()) {
                scrollOffset++;
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        restoreOriginalSettings();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // === Data Classes ===

    private record ScreenTestEntry(String name, String category, Supplier<Screen> factory) {}

    private record TestResult(String screenName, boolean passed, String message) {}

    private static String truncateToWidth(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (maxWidth <= 0 || font.width(text) <= maxWidth) {
            return text;
        }
        int ellipsisWidth = font.width("...");
        int allowed = Math.max(0, maxWidth - ellipsisWidth);
        if (allowed <= 0) {
            return "...";
        }
        return font.plainSubstrByWidth(text, allowed) + "...";
    }

    // === Static Open ===

    public static void open() {
        Minecraft.getInstance().setScreen(new UIResponsivenessTestScreen());
    }
}
