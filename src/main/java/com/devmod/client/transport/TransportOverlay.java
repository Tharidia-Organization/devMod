package com.devmod.client.transport;

import javax.annotation.Nonnull;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;

import com.devmod.transport.TransportState;
import com.devmod.transport.network.TransportChargeUpdatePayload;
import com.devmod.transport.network.TransportCountdownPayload;
import com.devmod.transport.network.TransportPartyStatusPayload;
import com.devmod.transport.network.TransportStatePayload;

/**
 * HUD overlay for transport charging and state display.
 *
 * <p>From Bibbia Estetica Regola 4:
 * <ul>
 *   <li>Progress bar ALWAYS horizontal, centered</li>
 *   <li>State ALWAYS bottom-left</li>
 *   <li>Destination ALWAYS bottom-right</li>
 *   <li>Action hint ALWAYS bottom center</li>
 *   <li>Border color = TransportState.primary</li>
 *   <li>Position: Screen center, slightly above</li>
 *   <li>Transparency: 85% opaque (0xD9)</li>
 * </ul>
 */
public class TransportOverlay implements LayeredDraw.Layer {

    private static final int BACKGROUND_ALPHA = 0xD9; // 85% opaque
    private static final int BAR_WIDTH = 200;
    private static final int BAR_HEIGHT = 10;
    private static final int BAR_BORDER = 2;

    private boolean active = false;
    private TransportState state = TransportState.IDLE;
    private int currentCharge = 0;
    private int requiredCharge = 40;
    private String destinationName = "";
    private int distance = 0;
    private int ticksSinceLastUpdate = 0;

    private static final int SERVER_UPDATE_TIMEOUT_TICKS = 20;

    /**
     * Updates the overlay state from a TransportStatePayload.
     */
    public void updateState(@Nonnull TransportStatePayload payload) {
        this.active = payload.inTransport();
        this.state = payload.getState();
        this.currentCharge = payload.currentCharge();
        this.requiredCharge = payload.requiredCharge();
        this.destinationName = payload.destinationName();
        this.distance = payload.distance();
        markUpdated();
    }

    /**
     * Updates charge progress from a TransportChargeUpdatePayload.
     */
    public void updateCharge(@Nonnull TransportChargeUpdatePayload payload) {
        this.active = true;
        this.currentCharge = payload.currentCharge();
        this.requiredCharge = payload.requiredCharge();
        if (payload.isComplete()) {
            this.state = TransportState.ACTIVE;
        }
        markUpdated();
    }

    /**
     * Resets the overlay to inactive state.
     */
    public void reset() {
        this.active = false;
        this.state = TransportState.IDLE;
        this.currentCharge = 0;
        this.requiredCharge = 40;
        this.destinationName = "";
        this.distance = 0;
        this.ticksSinceLastUpdate = 0;
    }

    /**
     * Returns whether the overlay is currently active.
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Ticks the overlay to handle timeouts when updates stop arriving.
     */
    public void tick() {
        if (!active) {
            return;
        }
        ticksSinceLastUpdate++;
        if (ticksSinceLastUpdate > SERVER_UPDATE_TIMEOUT_TICKS) {
            reset();
        }
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, @Nonnull DeltaTracker deltaTracker) {
        if (!active) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) {
            return;
        }

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        Font font = mc.font;

        // Calculate center position (slightly above middle)
        int centerX = screenWidth / 2;
        int centerY = (int) (screenHeight * 0.4);

        // Get colors from state
        int primaryColor = state.getPrimaryColor();
        int secondaryColor = state.getSecondaryColor();
        int borderColor = (BACKGROUND_ALPHA << 24) | (primaryColor & 0xFFFFFF);
        int backgroundColor = (BACKGROUND_ALPHA << 24) | 0x000000;

        // Render background panel
        int panelWidth = BAR_WIDTH + 40;
        int panelHeight = 60;
        int panelX = centerX - panelWidth / 2;
        int panelY = centerY - panelHeight / 2;

        renderPanel(graphics, panelX, panelY, panelWidth, panelHeight, borderColor, backgroundColor);

        // Render state text (top center of panel)
        String stateText = getStateText();
        int stateTextWidth = font.width(stateText);
        graphics.drawString(font, stateText, centerX - stateTextWidth / 2, panelY + 6, primaryColor, true);

        // Render progress bar (center of panel)
        int barX = centerX - BAR_WIDTH / 2;
        int barY = panelY + 22;
        renderProgressBar(graphics, barX, barY, BAR_WIDTH, BAR_HEIGHT, primaryColor, secondaryColor);

        // Render percentage text (below progress bar)
        int percent = requiredCharge > 0 ? (currentCharge * 100) / requiredCharge : 0;
        String percentText = percent + "%";
        int percentTextWidth = font.width(percentText);
        graphics.drawString(font, percentText, centerX - percentTextWidth / 2, barY + BAR_HEIGHT + 4, 0xFFFFFF, true);

        // Render destination text (bottom of panel)
        if (!destinationName.isEmpty()) {
            String destText = destinationName;
            if (distance > 0) {
                destText += " (" + distance + "m)";
            }
            int destTextWidth = font.width(destText);
            graphics.drawString(font, destText, centerX - destTextWidth / 2, panelY + panelHeight - 14, secondaryColor, true);
        }

        // Render action hint at bottom center of screen
        renderActionHint(graphics, font, screenWidth, screenHeight, primaryColor);
    }

    /**
     * Renders a panel with border.
     */
    private void renderPanel(GuiGraphics graphics, int x, int y, int width, int height, int borderColor, int bgColor) {
        // Background
        graphics.fill(x, y, x + width, y + height, bgColor);

        // Border (2 pixel)
        graphics.fill(x, y, x + width, y + BAR_BORDER, borderColor);  // Top
        graphics.fill(x, y + height - BAR_BORDER, x + width, y + height, borderColor);  // Bottom
        graphics.fill(x, y, x + BAR_BORDER, y + height, borderColor);  // Left
        graphics.fill(x + width - BAR_BORDER, y, x + width, y + height, borderColor);  // Right
    }

    /**
     * Renders the progress bar.
     */
    private void renderProgressBar(GuiGraphics graphics, int x, int y, int width, int height, int primaryColor, int secondaryColor) {
        // Background (dark)
        int bgColor = (BACKGROUND_ALPHA << 24) | 0x1a1a1a;
        graphics.fill(x, y, x + width, y + height, bgColor);

        // Border
        int borderColor = (BACKGROUND_ALPHA << 24) | (primaryColor & 0xFFFFFF);
        graphics.fill(x, y, x + width, y + 1, borderColor);  // Top
        graphics.fill(x, y + height - 1, x + width, y + height, borderColor);  // Bottom
        graphics.fill(x, y, x + 1, y + height, borderColor);  // Left
        graphics.fill(x + width - 1, y, x + width, y + height, borderColor);  // Right

        // Progress fill
        float progress = requiredCharge > 0 ? (float) currentCharge / requiredCharge : 0f;
        progress = Math.min(1f, Math.max(0f, progress));
        int fillWidth = (int) ((width - 4) * progress);
        if (fillWidth > 0) {
            int fillColor = (0xFF << 24) | (secondaryColor & 0xFFFFFF);
            graphics.fill(x + 2, y + 2, x + 2 + fillWidth, y + height - 2, fillColor);
        }
    }

    /**
     * Renders the action hint at the bottom of the screen.
     */
    private void renderActionHint(GuiGraphics graphics, Font font, int screenWidth, int screenHeight, int color) {
        String hint = getActionHint();
        if (hint.isEmpty()) {
            return;
        }

        int hintWidth = font.width(hint);
        int hintX = screenWidth / 2 - hintWidth / 2;
        int hintY = screenHeight - 40;

        // Background for hint
        int bgColor = (BACKGROUND_ALPHA << 24) | 0x000000;
        graphics.fill(hintX - 4, hintY - 2, hintX + hintWidth + 4, hintY + font.lineHeight + 2, bgColor);

        graphics.drawString(font, hint, hintX, hintY, color, true);
    }

    /**
     * Gets the state display text.
     */
    private String getStateText() {
        return switch (state) {
            case IDLE -> "READY";
            case CHARGING -> "CHARGING";
            case ACTIVE -> "TELEPORTING";
            case ERROR -> "ERROR";
            case COOLDOWN -> "COOLDOWN";
            case LOCKED -> "LOCKED";
        };
    }

    /**
     * Gets the action hint text.
     */
    private String getActionHint() {
        return switch (state) {
            case IDLE -> "Stand still to charge";
            case CHARGING -> "Stay on pad...";
            case ACTIVE -> "";
            case ERROR -> "No destination found";
            case COOLDOWN -> "Please wait...";
            case LOCKED -> "Access denied";
        };
    }

    /**
     * Gets the translatable state component.
     */
    public Component getStateComponent() {
        return Component.translatable("transport.devmod.state." + state.getSerializedName());
    }

    /**
     * Updates countdown display from a TransportCountdownPayload.
     */
    public void updateCountdown(@Nonnull TransportCountdownPayload payload) {
        this.active = true;
        this.state = payload.isUrgent() ? TransportState.ERROR : TransportState.CHARGING;
        this.currentCharge = payload.totalSeconds() - payload.secondsRemaining();
        this.requiredCharge = payload.totalSeconds();
        this.destinationName = payload.secondsRemaining() + "s";
        markUpdated();
    }

    /**
     * Updates party status display from a TransportPartyStatusPayload.
     */
    public void updatePartyStatus(@Nonnull TransportPartyStatusPayload payload) {
        this.active = !payload.isComplete() && !payload.isCancelled();
        this.state = switch (payload.phase()) {
            case TransportPartyStatusPayload.PHASE_COUNTDOWN -> TransportState.CHARGING;
            case TransportPartyStatusPayload.PHASE_TELEPORTING -> TransportState.ACTIVE;
            case TransportPartyStatusPayload.PHASE_WAITING -> TransportState.CHARGING;
            case TransportPartyStatusPayload.PHASE_COMPLETE -> TransportState.ACTIVE;
            case TransportPartyStatusPayload.PHASE_CANCELLED -> TransportState.ERROR;
            default -> TransportState.IDLE;
        };
        this.currentCharge = payload.arrivedCount();
        this.requiredCharge = payload.expectedCount();
        this.destinationName = "Party: " + payload.arrivedCount() + "/" + payload.expectedCount();
        markUpdated();
    }

    private void markUpdated() {
        ticksSinceLastUpdate = 0;
    }
}
