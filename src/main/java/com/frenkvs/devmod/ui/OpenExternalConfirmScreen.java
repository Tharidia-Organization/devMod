package com.frenkvs.devmod.ui;

import com.frenkvs.devmod.DevMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.Desktop;
import java.net.URI;
import java.util.Objects;

/**
 * Confirmation screen for opening external URLs.
 * Provides "Open in Browser" and "Copy URL" options with fallback handling.
 */
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
    private final String title;
    @Nullable
    private String statusMessage = null;
    private int statusColor = 0xFFFFFF;

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
        this.title = title;
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
        addRenderableWidget(Objects.requireNonNull(Button.builder(
            Objects.requireNonNull(Component.translatable("devmod.ui.open_external.open_browser")),
            button -> openInBrowser()
        ).bounds(
            dialogX + (DIALOG_WIDTH - BUTTON_WIDTH * 2 - BUTTON_SPACING) / 2,
            buttonY,
            BUTTON_WIDTH,
            BUTTON_HEIGHT
        ).build()));

        // Copy URL button
        addRenderableWidget(Objects.requireNonNull(Button.builder(
            Objects.requireNonNull(Component.translatable("devmod.ui.open_external.copy_url")),
            button -> copyToClipboard()
        ).bounds(
            dialogX + (DIALOG_WIDTH - BUTTON_WIDTH * 2 - BUTTON_SPACING) / 2 + BUTTON_WIDTH + BUTTON_SPACING,
            buttonY,
            BUTTON_WIDTH,
            BUTTON_HEIGHT
        ).build()));

        // Cancel button (small, below)
        addRenderableWidget(Objects.requireNonNull(Button.builder(
            Objects.requireNonNull(Component.translatable("gui.cancel")),
            button -> onClose()
        ).bounds(
            (width - 60) / 2,
            buttonY + BUTTON_HEIGHT + 5,
            60,
            BUTTON_HEIGHT
        ).build()));
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // Dialog background
        graphics.fill(dialogX, dialogY, dialogX + DIALOG_WIDTH, dialogY + DIALOG_HEIGHT, 0xDD000000);
        graphics.renderOutline(dialogX, dialogY, DIALOG_WIDTH, DIALOG_HEIGHT, 0xFF555555);

        // Null-safe font access
        var renderFont = this.font;
        if (renderFont == null) {
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        // Title
        graphics.drawCenteredString(renderFont, title, width / 2, dialogY + 12, 0xFFFFFF);

        // URL (truncated if too long)
        String displayUrl = url;
        int maxUrlWidth = DIALOG_WIDTH - 20;
        if (renderFont.width(displayUrl) > maxUrlWidth) {
            while (renderFont.width(displayUrl + "...") > maxUrlWidth && displayUrl.length() > 10) {
                displayUrl = displayUrl.substring(0, displayUrl.length() - 1);
            }
            displayUrl += "...";
        }
        graphics.drawCenteredString(renderFont, displayUrl, width / 2, dialogY + 32, 0xAAAAAA);

        // Status message (if any)
        String currentStatus = statusMessage;
        if (currentStatus != null) {
            graphics.drawCenteredString(renderFont, currentStatus, width / 2, dialogY + 50, statusColor);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void openInBrowser() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                statusMessage = Component.translatable("devmod.ui.open_external.opened").getString();
                statusColor = 0x55FF55;

                // Log telemetry
                logExternalOpen(true, null);

                // Close after short delay
                Minecraft.getInstance().tell(() -> {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ignored) {}
                    onClose();
                });
            } else {
                // Desktop not supported, offer copy instead
                statusMessage = Component.translatable("devmod.ui.open_external.desktop_not_supported").getString();
                statusColor = 0xFFAA00;
                copyToClipboard();
            }
        } catch (Exception e) {
            DevMod.LOGGER.error("[OpenExternalConfirmScreen] Failed to open URL: {}", url, e);
            statusMessage = Component.translatable("devmod.ui.open_external.failed").getString();
            statusColor = 0xFF5555;

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
            statusColor = 0x55FF55;

            // Log telemetry
            logCopyAction();
        } catch (Exception e) {
            DevMod.LOGGER.error("[OpenExternalConfirmScreen] Failed to copy URL: {}", url, e);
            statusMessage = Component.translatable("devmod.ui.open_external.copy_failed").getString();
            statusColor = 0xFF5555;
        }
    }

    private void logExternalOpen(boolean success, @Nullable String error) {
        var player = Minecraft.getInstance().player;
        String playerName = player != null ? player.getGameProfile().getName() : "unknown";

        StringBuilder sb = new StringBuilder();
        sb.append("{\"ts\":\"").append(java.time.Instant.now()).append("\",");
        sb.append("\"type\":\"external_url_opened\",");
        sb.append("\"url\":\"").append(com.frenkvs.devmod.telemetry.TelemetryJson.escape(url)).append("\",");
        sb.append("\"success\":").append(success).append(",");
        if (error != null) {
            sb.append("\"error\":\"").append(com.frenkvs.devmod.telemetry.TelemetryJson.escape(error)).append("\",");
        }
        sb.append("\"player\":\"").append(com.frenkvs.devmod.telemetry.TelemetryJson.escape(playerName)).append("\"}");

        com.frenkvs.devmod.telemetry.TelemetryService.INSTANCE.appendActionLine(sb.toString());
    }

    private void logCopyAction() {
        var player = Minecraft.getInstance().player;
        String playerName = player != null ? player.getGameProfile().getName() : "unknown";

        String line = "{\"ts\":\"" + java.time.Instant.now() + "\"," +
            "\"type\":\"external_url_copied\"," +
            "\"url\":\"" + com.frenkvs.devmod.telemetry.TelemetryJson.escape(url) + "\"," +
            "\"player\":\"" + com.frenkvs.devmod.telemetry.TelemetryJson.escape(playerName) + "\"}";

        com.frenkvs.devmod.telemetry.TelemetryService.INSTANCE.appendActionLine(line);
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
        Minecraft.getInstance().setScreen(new OpenExternalConfirmScreen(parentScreen, url, title));
    }

    /**
     * Utility method to open a URL with confirmation dialog using default title.
     */
    public static void openWithConfirmation(@Nullable Screen parentScreen, String url) {
        openWithConfirmation(parentScreen, url, "Open External Link?");
    }
}
