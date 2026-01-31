package com.devmod.client.ui.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.client.ui.BaseDevModScreen;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.components.EditorButtonWidget;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.runtime.network.AdminInstanceActionPayload;
import com.devmod.runtime.network.AdminInstanceSyncPayload;
/**
 * Admin screen for monitoring and controlling instance dimensions.
 * Provides real-time view of all active instances with management controls.
 */
@OnlyIn(Dist.CLIENT)
public class AdminInstanceScreen extends BaseDevModScreen {

    // Layout
    private static final int PANEL_WIDTH = 500;
    private static final int PANEL_HEIGHT = 380;
    private static final int HEADER_HEIGHT = 50;
    private static final int STATS_HEIGHT = 30;
    private static final int ENTRY_HEIGHT = 80;
    private static final int ENTRY_PADDING = 8;

    // Auto-refresh
    private static final int AUTO_REFRESH_TICKS = 40; // 2 seconds
    private int refreshCounter = 0;

    // State
    private List<AdminInstanceSyncPayload.InstanceSnapshot> instances = new ArrayList<>();
    private int totalPlayers = 0;
    private int activeCount = 0;
    private int pendingDestructionCount = 0;

    // Scroll
    private int scrollOffset = 0;
    private int maxVisibleEntries = 3;

    // Layout cache (scaled dimensions)
    private int panelX, panelY;
    private int scaledPanelWidth, scaledPanelHeight;
    private int scaledHeaderHeight, scaledStatsHeight, scaledEntryHeight;
    private int listX, listY, listWidth, listHeight;

    // Selected instance for actions
    @Nullable
    private UUID selectedInstanceId = null;

    public AdminInstanceScreen() {
        super(Component.translatable("devmod.admin.instance.title"), "admin_instance", "admin");
    }

    @Override
    protected void initContent() {
        // Scale dimensions
        scaledPanelWidth = UIScaleManager.scale(PANEL_WIDTH);
        scaledPanelHeight = UIScaleManager.scale(PANEL_HEIGHT);
        scaledHeaderHeight = UIScaleManager.scale(HEADER_HEIGHT);
        scaledStatsHeight = UIScaleManager.scale(STATS_HEIGHT);
        scaledEntryHeight = UIScaleManager.scale(ENTRY_HEIGHT);
        int scaledEntryPadding = UIScaleManager.scale(ENTRY_PADDING);

        panelX = (width - scaledPanelWidth) / 2;
        panelY = (height - scaledPanelHeight) / 2;

        listX = panelX + scaledEntryPadding;
        listY = panelY + scaledHeaderHeight + scaledStatsHeight;
        listWidth = scaledPanelWidth - (scaledEntryPadding * 2);
        listHeight = scaledPanelHeight - scaledHeaderHeight - scaledStatsHeight - UIScaleManager.scale(50);

        maxVisibleEntries = listHeight / scaledEntryHeight;

        // Refresh button
        EditorButton refreshButton = EditorButton.builder("admin-instance-refresh",
                Component.translatable("devmod.admin.instance.refresh").getString())
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::requestRefresh)
            .build();
        addRenderableWidget(new EditorButtonWidget(refreshButton, panelX + scaledPanelWidth - UIScaleManager.scale(80), panelY + UIScaleManager.scale(10), UIScaleManager.scale(70), UIScaleManager.scale(20)));

        // Request initial data
        requestRefresh();
    }

    @Override
    protected void tickContent() {
        refreshCounter++;
        if (refreshCounter >= AUTO_REFRESH_TICKS) {
            refreshCounter = 0;
            requestRefresh();
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Panel background
        graphics.fill(panelX, panelY, panelX + scaledPanelWidth, panelY + scaledPanelHeight, DesignTokens.AdminPanel.PANEL_BG);

        // Header
        graphics.fill(panelX, panelY, panelX + scaledPanelWidth, panelY + scaledHeaderHeight, DesignTokens.AdminPanel.HEADER_BG);

        // Title
        Component title = Objects.requireNonNull(Component.translatable("devmod.admin.instance.title"));
        UIScaleManager.drawScaledString(graphics, safeFont(), title, panelX + UIScaleManager.scale(15), panelY + UIScaleManager.scale(18), DesignTokens.Text.WHITE);

        // Stats bar
        int statsY = panelY + scaledHeaderHeight;
        graphics.fill(panelX, statsY, panelX + scaledPanelWidth, statsY + scaledStatsHeight, DesignTokens.AdminPanel.STATS_BG);

        String stats = String.format("\u00A7a%d Active \u00A7f| \u00A7b%d Players \u00A7f| \u00A7c%d Pending",
            activeCount, totalPlayers, pendingDestructionCount);
        UIScaleManager.drawScaledString(graphics, safeFont(), stats, panelX + UIScaleManager.scale(15), statsY + UIScaleManager.scale(10), DesignTokens.Text.WHITE);

        // Instance list
        renderInstanceList(graphics, mouseX, mouseY);

        // Scroll indicators
        if (scrollOffset > 0) {
            UIScaleManager.drawScaledCenteredString(graphics, safeFont(), "\u25B2", panelX + scaledPanelWidth / 2, listY - UIScaleManager.scale(10), DesignTokens.Text.MUTED);
        }
        if (scrollOffset + maxVisibleEntries < instances.size()) {
            UIScaleManager.drawScaledCenteredString(graphics, safeFont(), "\u25BC", panelX + scaledPanelWidth / 2, listY + listHeight + UIScaleManager.scale(2), DesignTokens.Text.MUTED);
        }

        // Empty state
        if (instances.isEmpty()) {
            UIScaleManager.drawScaledCenteredString(graphics, safeFont(),
                Objects.requireNonNull(Component.translatable("devmod.admin.instance.empty")),
                panelX + scaledPanelWidth / 2, listY + listHeight / 2, DesignTokens.Text.MUTED);
        }
    }

    private void renderInstanceList(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = listY;

        for (int i = scrollOffset; i < Math.min(scrollOffset + maxVisibleEntries, instances.size()); i++) {
            AdminInstanceSyncPayload.InstanceSnapshot instance = instances.get(i);
            renderInstanceEntry(graphics, instance, listX, y, listWidth, scaledEntryHeight - UIScaleManager.scale(4), mouseX, mouseY);
            y += scaledEntryHeight;
        }
    }

    private void renderInstanceEntry(GuiGraphics graphics, AdminInstanceSyncPayload.InstanceSnapshot instance,
                                     int x, int y, int w, int h, int mouseX, int mouseY) {
        // Entry background
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int bgColor = hovered ? DesignTokens.AdminPanel.ENTRY_HOVER : DesignTokens.AdminPanel.ENTRY_BG;
        graphics.fill(x, y, x + w, y + h, bgColor);

        // Border for selected
        if (instance.instanceId().equals(selectedInstanceId)) {
            graphics.renderOutline(x, y, w, h, DesignTokens.AdminPanel.SELECTION);
        }

        // State indicator (colored bar on left)
        int stateColor = getStateColor(instance.stateOrdinal());
        graphics.fill(x, y, x + UIScaleManager.scale(4), y + h, stateColor);

        // Instance info
        int textX = x + UIScaleManager.scale(12);
        int textY = y + UIScaleManager.scale(6);
        int lineSpacing = UIScaleManager.scale(12);

        // State + Arena template
        String header = instance.getStateDisplay() + " \u00A7f" + (instance.arenaTemplate() != null ? instance.arenaTemplate() : "unknown");
        UIScaleManager.drawScaledString(graphics, safeFont(), header, textX, textY, DesignTokens.Text.WHITE);

        // Players
        textY += lineSpacing;
        String players = "Players: " + instance.getPlayerCountDisplay();
        if (!instance.playerNames().isEmpty()) {
            players += " (" + String.join(", ", instance.playerNames()) + ")";
        }
        UIScaleManager.drawScaledString(graphics, safeFont(), players, textX, textY, DesignTokens.Text.SECONDARY);

        // Wave + Duration
        textY += lineSpacing;
        String info = "Wave: " + instance.getWaveDisplay() + " | Duration: " + instance.getDurationDisplay();
        UIScaleManager.drawScaledString(graphics, safeFont(), info, textX, textY, DesignTokens.Text.SECONDARY);

        // Action buttons
        int btnY = y + UIScaleManager.scale(48);
        int btnX = x + w - UIScaleManager.scale(200);
        int btnH = UIScaleManager.scale(16);

        // End Success button
        int successW = UIScaleManager.scale(60);
        if (renderSmallButton(graphics, "Success", btnX, btnY, successW, btnH, mouseX, mouseY, DesignTokens.Semantic.SUCCESS)) {
            // Will be clicked via handleMouseClick
        }

        // End Failure button
        btnX += UIScaleManager.scale(65);
        int failW = UIScaleManager.scale(45);
        if (renderSmallButton(graphics, "Fail", btnX, btnY, failW, btnH, mouseX, mouseY, DesignTokens.Semantic.ERROR)) {
            // Will be clicked via handleMouseClick
        }

        // Force Destroy button (only for empty instances)
        if (instance.isEmpty()) {
            btnX += UIScaleManager.scale(50);
            int destroyW = UIScaleManager.scale(55);
            if (renderSmallButton(graphics, "Destroy", btnX, btnY, destroyW, btnH, mouseX, mouseY, DesignTokens.Semantic.WARNING)) {
                // Will be clicked via handleMouseClick
            }
        }
    }

    private boolean renderSmallButton(GuiGraphics graphics, @Nonnull String text, int x, int y, int w, int h,
                                      int mouseX, int mouseY, int color) {
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int bgColor = hovered ? color : (color & DesignTokens.AdminPanel.RGB_MASK) | DesignTokens.AdminPanel.BUTTON_ALPHA_MASK;
        graphics.fill(x, y, x + w, y + h, bgColor);
        UIScaleManager.drawScaledCenteredString(graphics, safeFont(), text, x + w / 2, y + 4, DesignTokens.Text.WHITE);
        return hovered;
    }

    private int getStateColor(int stateOrdinal) {
        return switch (stateOrdinal) {
            case 0 -> DesignTokens.AdminPanel.STATE_CREATING;
            case 1 -> DesignTokens.AdminPanel.STATE_READY;
            case 2 -> DesignTokens.AdminPanel.STATE_ACTIVE;
            case 3 -> DesignTokens.AdminPanel.STATE_COMPLETING;
            case 4 -> DesignTokens.AdminPanel.STATE_DESTROYING;
            case 5 -> DesignTokens.AdminPanel.STATE_DESTROYED;
            default -> DesignTokens.AdminPanel.STATE_UNKNOWN;
        };
    }

    @Override
    protected boolean handleMouseClick(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        int y = listY;
        for (int i = scrollOffset; i < Math.min(scrollOffset + maxVisibleEntries, instances.size()); i++) {
            AdminInstanceSyncPayload.InstanceSnapshot instance = instances.get(i);
            int entryX = listX;
            int entryW = listWidth;
            int entryH = scaledEntryHeight - UIScaleManager.scale(4);

            if (mouseX >= entryX && mouseX < entryX + entryW && mouseY >= y && mouseY < y + entryH) {
                selectedInstanceId = instance.instanceId();

                // Check button clicks
                int btnY = y + UIScaleManager.scale(48);
                int btnX = entryX + entryW - UIScaleManager.scale(200);
                int btnH = UIScaleManager.scale(16);

                // Success button
                int successW = UIScaleManager.scale(60);
                if (mouseX >= btnX && mouseX < btnX + successW && mouseY >= btnY && mouseY < btnY + btnH) {
                    sendAction(Objects.requireNonNull(AdminInstanceActionPayload.endSuccess(instance.instanceId())));
                    playClickSound();
                    return true;
                }

                // Fail button
                btnX += UIScaleManager.scale(65);
                int failW = UIScaleManager.scale(45);
                if (mouseX >= btnX && mouseX < btnX + failW && mouseY >= btnY && mouseY < btnY + btnH) {
                    sendAction(Objects.requireNonNull(AdminInstanceActionPayload.endFailure(instance.instanceId())));
                    playClickSound();
                    return true;
                }

                // Destroy button
                if (instance.isEmpty()) {
                    btnX += UIScaleManager.scale(50);
                    int destroyW = UIScaleManager.scale(55);
                    if (mouseX >= btnX && mouseX < btnX + destroyW && mouseY >= btnY && mouseY < btnY + btnH) {
                        sendAction(Objects.requireNonNull(AdminInstanceActionPayload.forceDestroy(instance.instanceId())));
                        playClickSound();
                        return true;
                    }
                }

                playClickSound();
                return true;
            }

            y += scaledEntryHeight;
        }

        return false;
    }

    @Override
    protected boolean handleMouseScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Check if mouse is over the list area
        if (mouseX >= listX && mouseX < listX + listWidth && mouseY >= listY && mouseY < listY + listHeight) {
            if (scrollY > 0 && scrollOffset > 0) {
                scrollOffset--;
                return true;
            } else if (scrollY < 0 && scrollOffset + maxVisibleEntries < instances.size()) {
                scrollOffset++;
                return true;
            }
        }
        return false;
    }

    // =========================================================================
    // DATA SYNC
    // =========================================================================

    /**
     * Apply sync data from server.
     */
    public void applySync(AdminInstanceSyncPayload payload) {
        this.instances = new ArrayList<>(payload.instances());
        this.totalPlayers = payload.totalPlayers();
        this.activeCount = payload.activeCount();
        this.pendingDestructionCount = payload.pendingDestructionCount();

        // Clamp scroll offset
        if (scrollOffset > 0 && scrollOffset >= instances.size()) {
            scrollOffset = Math.max(0, instances.size() - maxVisibleEntries);
        }

        // Clear selection if instance no longer exists
        if (selectedInstanceId != null) {
            boolean found = instances.stream().anyMatch(i -> i.instanceId().equals(selectedInstanceId));
            if (!found) {
                selectedInstanceId = null;
            }
        }
    }

    /**
     * Request refresh from server.
     */
    private void requestRefresh() {
        sendAction(Objects.requireNonNull(AdminInstanceActionPayload.refresh()));
    }

    /**
     * Send an action to the server.
     */
    private void sendAction(@Nonnull AdminInstanceActionPayload action) {
        PacketDistributor.sendToServer(Objects.requireNonNull(action, "action"));
    }

    // =========================================================================
    // STATIC OPEN METHOD
    // =========================================================================

    /**
     * Get the currently open admin screen, if any.
     */
    @Nullable
    public static AdminInstanceScreen getCurrent() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.screen instanceof AdminInstanceScreen screen) {
            return screen;
        }
        return null;
    }

    /**
     * Open the admin instance screen.
     */
    public static void open() {
        com.devmod.client.ui.ScreenSafety.openSafe(
            "admin_instance",
            AdminInstanceScreen::new);
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /**
     * Get font with null safety.
     */
    @Nonnull
    private Font safeFont() {
        return Objects.requireNonNull(font, "font");
    }
}
