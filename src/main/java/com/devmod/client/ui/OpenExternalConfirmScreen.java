package com.devmod.client.ui;

import java.awt.Desktop;
import java.net.URI;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.DevMod;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.components.EditorButtonWidget;
import com.devmod.client.ui.editor.core.DesignTokens;

@OnlyIn(Dist.CLIENT)
public class OpenExternalConfirmScreen extends Screen {

    private static final int DIALOG_WIDTH = 280;
    private static final int DIALOG_HEIGHT = 120;
    private static final int BUTTON_WIDTH = 120;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 8;

    @Nullable
    private final Screen parentScreen;
    @Nonnull
    private final String url;
    @Nonnull
    private final String dialogTitle;
    @Nullable
    private String statusMessage = null;
    private int statusColor = DesignTokens.ExternalConfirm.TITLE;

    /**
     * Creates a confirmation screen for opening an external URL.
     *
     * @param parentScreen Screen to return to on cancel (can be null)
     * @param url          The URL to open
     * @param title        Title shown in the dialog (e.g., "Open Dashboard?")
     */
    public OpenExternalConfirmScreen(@Nullable Screen parentScreen, String url, String title) {
        super(Objects.requireNonNull(Component.literal(Objects.requireNonNull(title, "title")), "title component"));
        this.parentScreen = parentScreen;
        this.url = Objects.requireNonNull(url, "url");
        this.dialogTitle = title;
    }

    /**
     * Creates a confirmation screen for opening an external URL with default title.
     */
    public OpenExternalConfirmScreen(@Nullable Screen parentScreen, String url) {
        this(parentScreen, url, "Open External Link?");
    }

    @Override
    protected void init() {
        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        int buttonY = dialogY + DIALOG_HEIGHT - BUTTON_HEIGHT - 15;

        // Open in Browser button
        EditorButton openButton = EditorButton.builder("open-external-browser",
                Component.translatable("devmod.ui.open_external.open_browser").getString())
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::openInBrowser)
            .build();
        addRenderableWidget(new EditorButtonWidget(openButton,
            dialogX + (DIALOG_WIDTH - BUTTON_WIDTH * 2 - BUTTON_SPACING) / 2,
            buttonY,
            BUTTON_WIDTH,
            BUTTON_HEIGHT));

        // Copy URL button
        EditorButton copyButton = EditorButton.builder("open-external-copy",
                Component.translatable("devmod.ui.open_external.copy_url").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::copyToClipboard)
            .build();
        addRenderableWidget(new EditorButtonWidget(copyButton,
            dialogX + (DIALOG_WIDTH - BUTTON_WIDTH * 2 - BUTTON_SPACING) / 2 + BUTTON_WIDTH + BUTTON_SPACING,
            buttonY,
            BUTTON_WIDTH,
            BUTTON_HEIGHT));

        // Cancel button (small, below)
        EditorButton cancelButton = EditorButton.builder("open-external-cancel",
                Component.translatable("gui.cancel").getString())
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onClose)
            .build();
        addRenderableWidget(new EditorButtonWidget(cancelButton,
            (width - 60) / 2,
            buttonY + BUTTON_HEIGHT + 5,
            60,
            BUTTON_HEIGHT));
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int scaledDialogWidth = UIScaleManager.scale(DIALOG_WIDTH);
        int scaledDialogHeight = UIScaleManager.scale(DIALOG_HEIGHT);
        int dialogX = UIScaleManager.centerX(scaledDialogWidth);
        int dialogY = UIScaleManager.centerY(scaledDialogHeight);

        // Dialog background
        graphics.fill(dialogX, dialogY, dialogX + scaledDialogWidth, dialogY + scaledDialogHeight, DesignTokens.ExternalConfirm.BG);
        graphics.renderOutline(dialogX, dialogY, scaledDialogWidth, scaledDialogHeight, DesignTokens.ExternalConfirm.BORDER);

        // Null-safe font access
        var renderFont = this.font;
        if (renderFont == null) {
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        // Title
        graphics.drawCenteredString(renderFont, dialogTitle, UIScaleManager.getCenterX(), dialogY + UIScaleManager.scale(12), DesignTokens.ExternalConfirm.TITLE);

        // URL (truncated if too long)
        String displayUrl = url;
        int maxUrlWidth = scaledDialogWidth - UIScaleManager.scale(20);
        if (renderFont.width(displayUrl) > maxUrlWidth) {
            while (renderFont.width(displayUrl + "...") > maxUrlWidth && displayUrl.length() > 10) {
                displayUrl = displayUrl.substring(0, displayUrl.length() - 1);
            }
            displayUrl += "...";
        }
        graphics.drawCenteredString(renderFont, displayUrl, UIScaleManager.getCenterX(), dialogY + UIScaleManager.scale(32), DesignTokens.ExternalConfirm.URL);

        // Status message (if any)
        String currentStatus = statusMessage;
        if (currentStatus != null) {
            graphics.drawCenteredString(renderFont, currentStatus, UIScaleManager.getCenterX(), dialogY + UIScaleManager.scale(50), statusColor);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void openInBrowser() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                statusMessage = Component.translatable("devmod.ui.open_external.opened").getString();
                statusColor = DesignTokens.ExternalConfirm.STATUS_OK;

                // Log telemetry
                logExternalOpen(true, null);

                // Close after short delay
                Minecraft.getInstance().tell(() -> {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    onClose();
                });
            } else {
                // Desktop not supported, offer copy instead
                statusMessage = Component.translatable("devmod.ui.open_external.desktop_not_supported").getString();
                statusColor = DesignTokens.ExternalConfirm.STATUS_WARN;
                copyToClipboard();
            }
        } catch (Exception e) {
            DevMod.LOGGER.error("[OpenExternalConfirmScreen] Failed to open URL: {}", url, e);
            statusMessage = Component.translatable("devmod.ui.open_external.failed").getString();
            statusColor = DesignTokens.ExternalConfirm.STATUS_ERROR;

            // Log telemetry
            logExternalOpen(false, e.getMessage());

            // Auto-copy on failure
            copyToClipboard();
        }
    }

    private void copyToClipboard() {
        try {
            var keyboard = Minecraft.getInstance().keyboardHandler;
            if (keyboard != null) {
                keyboard.setClipboard(url);
            }
            statusMessage = Component.translatable("devmod.ui.open_external.copied").getString();
            statusColor = DesignTokens.ExternalConfirm.STATUS_OK;

            // Log telemetry
            logCopyAction();
        } catch (Exception e) {
            DevMod.LOGGER.error("[OpenExternalConfirmScreen] Failed to copy URL: {}", url, e);
            statusMessage = Component.translatable("devmod.ui.open_external.copy_failed").getString();
            statusColor = DesignTokens.ExternalConfirm.STATUS_ERROR;
        }
    }

    private void logExternalOpen(boolean success, @Nullable String error) {
        var player = Minecraft.getInstance().player;
        String playerName = player != null ? player.getGameProfile().getName() : "unknown";

        StringBuilder sb = new StringBuilder();
        sb.append("{\"ts\":\"").append(java.time.Instant.now()).append("\",");
        sb.append("\"type\":\"external_url_opened\",");
        sb.append("\"url\":\"").append(com.devmod.telemetry.TelemetryJson.escape(url)).append("\",");
        sb.append("\"success\":").append(success).append(",");
        if (error != null) {
            sb.append("\"error\":\"").append(com.devmod.telemetry.TelemetryJson.escape(error)).append("\",");
        }
        sb.append("\"player\":\"").append(com.devmod.telemetry.TelemetryJson.escape(playerName)).append("\"}");

        com.devmod.telemetry.TelemetryService.INSTANCE.appendActionLine(sb.toString());
    }

    private void logCopyAction() {
        var player = Minecraft.getInstance().player;
        String playerName = player != null ? player.getGameProfile().getName() : "unknown";

        String line = "{\"ts\":\"" + java.time.Instant.now() + "\"," +
            "\"type\":\"external_url_copied\"," +
            "\"url\":\"" + com.devmod.telemetry.TelemetryJson.escape(url) + "\"," +
            "\"player\":\"" + com.devmod.telemetry.TelemetryJson.escape(playerName) + "\"}";

        com.devmod.telemetry.TelemetryService.INSTANCE.appendActionLine(line);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parentScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Utility method to open a URL with confirmation dialog.
     * Call this instead of directly opening URLs.
     *
     * @param parentScreen Screen to return to
     * @param url          URL to open
     * @param title        Dialog title
     */
    public static void openWithConfirmation(@Nullable Screen parentScreen, String url, String title) {
        com.devmod.client.ui.ScreenSafety.openSafe(
            "open_external_confirm",
            parentScreen,
            () -> new OpenExternalConfirmScreen(parentScreen, url, title));
    }

    /**
     * Utility method to open a URL with confirmation dialog using default title.
     */
    public static void openWithConfirmation(@Nullable Screen parentScreen, String url) {
        openWithConfirmation(parentScreen, url, "Open External Link?");
    }
}
