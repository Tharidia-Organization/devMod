package com.devmod.client.ui.components;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.ui.editor.core.UIConstants;
import com.devmod.util.I18n;
@OnlyIn(Dist.CLIENT)
public class LoadingIndicator {

    /** Loading states */
    public enum State {
        IDLE,
        LOADING,
        ERROR,
        SUCCESS
    }

    // State
    private State state = State.IDLE;
    private String message = "";
    private String errorMessage = "";
    private long startTime = 0;
    private float spinnerAngle = 0;

    // Configuration
    private long timeoutMs = 30000; // 30 second default timeout
    private Runnable onRetry = null;

    // ═══════════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Start loading with a message.
     */
    public void start(@Nonnull String message) {
        this.state = State.LOADING;
        this.message = Objects.requireNonNull(message);
        this.errorMessage = "";
        this.startTime = System.currentTimeMillis();
        this.spinnerAngle = 0;
    }

    /**
     * Stop loading (success).
     */
    public void stop() {
        this.state = State.IDLE;
    }

    /**
     * Stop loading with success state (briefly shows checkmark).
     */
    public void success() {
        this.state = State.SUCCESS;
    }

    /**
     * Show error state.
     */
    public void showError(@Nonnull String errorMessage) {
        this.state = State.ERROR;
        this.errorMessage = Objects.requireNonNull(errorMessage);
    }

    /**
     * Reset to idle state.
     */
    public void reset() {
        this.state = State.IDLE;
        this.message = "";
        this.errorMessage = "";
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Set timeout in milliseconds.
     */
    public LoadingIndicator setTimeout(long timeoutMs) {
        this.timeoutMs = timeoutMs;
        return this;
    }

    /**
     * Set retry callback (shown in error state).
     */
    public LoadingIndicator setOnRetry(@Nullable Runnable onRetry) {
        this.onRetry = onRetry;
        return this;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RENDERING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Update animation state. Call every frame.
     */
    public void tick(float deltaTime) {
        if (state == State.LOADING) {
            spinnerAngle += deltaTime * 180f; // 180 degrees per second

            // Check for timeout
            if (isTimedOut()) {
                showError(I18n.translate("devmod.loading.timeout").getString());
            }
        }
    }

    /**
     * Render the loading indicator centered in the given area.
     */
    public void render(@Nonnull GuiGraphics graphics, @Nonnull Font font, int x, int y, int width, int height) {
        int centerX = x + width / 2;
        int centerY = y + height / 2;

        switch (state) {
            case LOADING -> renderLoading(graphics, font, centerX, centerY);
            case ERROR -> renderError(graphics, font, centerX, centerY);
            case SUCCESS -> renderSuccess(graphics, font, centerX, centerY);
            default -> {} // IDLE - render nothing
        }
    }

    /**
     * Render a compact loading indicator (just spinner + text).
     */
    public void renderCompact(@Nonnull GuiGraphics graphics, @Nonnull Font font, int x, int y) {
        if (state != State.LOADING) return;

        // Spinner
        renderSpinner(graphics, x + 8, y + 6, 6);

        // Text
        graphics.drawString(font, message, x + 22, y, UIConstants.Text.SECONDARY());
    }

    private void renderLoading(@Nonnull GuiGraphics graphics, @Nonnull Font font, int centerX, int centerY) {
        // Spinner
        renderSpinner(graphics, centerX, centerY - 15, 10);

        // Message
        int messageWidth = font.width(message);
        graphics.drawString(font, message, centerX - messageWidth / 2, centerY + 5, UIConstants.Text.SECONDARY());

        // Elapsed time
        long elapsed = System.currentTimeMillis() - startTime;
        String timeText = formatElapsedTime(elapsed);
        int timeWidth = font.width(timeText);
        graphics.drawString(font, timeText, centerX - timeWidth / 2, centerY + 18, UIConstants.Text.MUTED());
    }

    private void renderSpinner(@Nonnull GuiGraphics graphics, int centerX, int centerY, int radius) {
        for (int i = 0; i < 8; i++) {
            float angle = (float) Math.toRadians(spinnerAngle + i * 45);
            int dotX = centerX + (int) (Math.cos(angle) * radius);
            int dotY = centerY + (int) (Math.sin(angle) * radius);
            int alpha = 255 - (i * 28);
            int dotColor = (alpha << 24) | (UIConstants.Accent.BLUE() & 0x00FFFFFF);
            int dotSize = i == 0 ? 3 : 2;
            graphics.fill(dotX - dotSize / 2, dotY - dotSize / 2, dotX + dotSize / 2 + 1, dotY + dotSize / 2 + 1, dotColor);
        }
    }

    private void renderError(@Nonnull GuiGraphics graphics, @Nonnull Font font, int centerX, int centerY) {
        // Error icon
        graphics.drawString(font, "\u2716", centerX - 4, centerY - 15, 0xFFFF4444);

        // Error message
        int msgWidth = font.width(errorMessage);
        graphics.drawString(font, errorMessage, centerX - msgWidth / 2, centerY + 5, 0xFFFF6666);

        // Retry hint if available
        if (onRetry != null) {
            String retryText = I18n.translate("devmod.loading.click_retry").getString();
            int retryWidth = font.width(retryText);
            graphics.drawString(font, retryText, centerX - retryWidth / 2, centerY + 18, UIConstants.Text.MUTED());
        }
    }

    private void renderSuccess(@Nonnull GuiGraphics graphics, @Nonnull Font font, int centerX, int centerY) {
        // Checkmark
        graphics.drawString(font, "\u2714", centerX - 4, centerY - 5, 0xFF44FF44);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STATE QUERIES
    // ═══════════════════════════════════════════════════════════════════════════

    public boolean isLoading() {
        return state == State.LOADING;
    }

    public boolean isError() {
        return state == State.ERROR;
    }

    public boolean isIdle() {
        return state == State.IDLE;
    }

    public State getState() {
        return state;
    }

    public boolean isTimedOut() {
        if (state != State.LOADING) return false;
        return (System.currentTimeMillis() - startTime) > timeoutMs;
    }

    public long getElapsedMs() {
        if (startTime == 0) return 0;
        return System.currentTimeMillis() - startTime;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INTERACTION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Handle click for retry (if in error state with retry callback).
     *
     * @return true if click was handled
     */
    public boolean handleClick() {
        if (state == State.ERROR && onRetry != null) {
            onRetry.run();
            return true;
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private String formatElapsedTime(long elapsedMs) {
        if (elapsedMs < 1000) {
            return "< 1s";
        } else if (elapsedMs < 60000) {
            return String.format("%.1fs", elapsedMs / 1000.0);
        } else {
            long minutes = elapsedMs / 60000;
            long seconds = (elapsedMs % 60000) / 1000;
            return String.format("%dm %ds", minutes, seconds);
        }
    }
}
