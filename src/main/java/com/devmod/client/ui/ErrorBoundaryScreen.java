package com.devmod.client.ui;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.shared.SharedColorTokens;
import com.devmod.util.ContextLogger;

/**
 * Base screen class with error boundary protection.
 *
 * <p>Wraps the render method in a try-catch to prevent crashes from propagating.
 * When an error occurs, displays a fallback error UI with:
 * <ul>
 *   <li>Error message</li>
 *   <li>Stack trace (first few lines)</li>
 *   <li>Close button to safely exit</li>
 *   <li>Retry button to attempt recovery</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * public class MyScreen extends ErrorBoundaryScreen {
 *     public MyScreen() {
 *         super(Component.literal("My Screen"));
 *     }
 *
 *     @Override
 *     protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
 *         // Your rendering code here
 *     }
 * }
 * }</pre>
 */
@OnlyIn(Dist.CLIENT)
public abstract class ErrorBoundaryScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorBoundaryScreen.class);

    // Error state
    private boolean hasError = false;
    @Nullable
    private Throwable lastError = null;
    private int errorCount = 0;
    private static final int MAX_ERRORS_BEFORE_LOCKOUT = 3;

    // Error UI
    private static final int ERROR_BG_COLOR = DesignTokens.ErrorBoundary.BG;
    private static final int ERROR_BORDER_COLOR = DesignTokens.ErrorBoundary.BORDER;
    private static final int ERROR_TEXT_COLOR = DesignTokens.ErrorBoundary.TEXT;
    private static final int MAX_STACK_LINES = 5;

    @Nullable
    private Button closeButton;
    @Nullable
    private Button retryButton;

    protected ErrorBoundaryScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        super.init();

        if (hasError) {
            initErrorUI();
        } else {
            try {
                initContent();
            } catch (Exception e) {
                handleError(e, "init");
                initErrorUI();
            }
        }
    }

    /**
     * Initialize screen content. Override instead of init().
     */
    protected abstract void initContent();

    /**
     * Render screen content. Override instead of render().
     */
    protected abstract void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (hasError) {
            renderErrorScreen(graphics, mouseX, mouseY, partialTick);
            return;
        }

        try {
            renderBackground(graphics, mouseX, mouseY, partialTick);
            renderContent(graphics, mouseX, mouseY, partialTick);
            super.render(graphics, mouseX, mouseY, partialTick);
        } catch (Exception e) {
            handleError(e, "render");
        }
    }

    /**
     * Handle an error that occurred during screen operations.
     */
    protected void handleError(Throwable error, String context) {
        errorCount++;
        lastError = error;

        // Log with context
        try (var ctx = ContextLogger.forRequest(
                ContextLogger.generateRequestId(),
                "screen_error"
        )) {
            ctx.put(ContextLogger.KEY_ACTION, context);
            LOGGER.error("[ErrorBoundary] Error in {} (screen={}, count={})",
                context, getClass().getSimpleName(), errorCount, error);
        }

        // Enter error state after threshold
        if (errorCount >= MAX_ERRORS_BEFORE_LOCKOUT || "init".equals(context)) {
            hasError = true;
        }
    }

    /**
     * Initialize the error UI elements.
     */
    private void initErrorUI() {
        clearWidgets();

        int centerX = width / 2;
        int buttonY = height / 2 + 60;

        closeButton = Button.builder(
                Component.translatable("gui.devmod.error.close"),
                b -> onClose()
            )
            .pos(centerX - 105, buttonY)
            .size(100, 20)
            .build();

        retryButton = Button.builder(
                Component.translatable("gui.devmod.error.retry"),
                b -> attemptRecovery()
            )
            .pos(centerX + 5, buttonY)
            .size(100, 20)
            .build();

        addRenderableWidget(closeButton);
        addRenderableWidget(retryButton);
    }

    /**
     * Render the error fallback screen.
     */
    private void renderErrorScreen(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dark background
        graphics.fill(0, 0, width, height, ERROR_BG_COLOR);

        int centerX = width / 2;
        int centerY = height / 2;

        // Error box
        int boxWidth = 400;
        int boxHeight = 180;
        int boxX = centerX - boxWidth / 2;
        int boxY = centerY - boxHeight / 2 - 20;

        // Box background
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, DesignTokens.ErrorBoundary.PANEL_BG);

        // Border
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + 2, ERROR_BORDER_COLOR);
        graphics.fill(boxX, boxY + boxHeight - 2, boxX + boxWidth, boxY + boxHeight, ERROR_BORDER_COLOR);
        graphics.fill(boxX, boxY, boxX + 2, boxY + boxHeight, ERROR_BORDER_COLOR);
        graphics.fill(boxX + boxWidth - 2, boxY, boxX + boxWidth, boxY + boxHeight, ERROR_BORDER_COLOR);

        // Title
        Component errorTitle = Component.translatable("gui.devmod.error.title")
            .withStyle(SharedColorTokens.Chat.RED, ChatFormatting.BOLD);
        graphics.drawCenteredString(font, errorTitle, centerX, boxY + 15, ERROR_TEXT_COLOR);

        // Screen name
        String screenName = getClass().getSimpleName();
        graphics.drawCenteredString(font, screenName, centerX, boxY + 35, DesignTokens.Text.SECONDARY);

        // Error message
        String errorMsg = lastError != null ? lastError.getMessage() : "Unknown error";
        if (errorMsg == null) {
            errorMsg = lastError != null ? lastError.getClass().getSimpleName() : "Unknown error";
        }
        if (errorMsg.length() > 60) {
            errorMsg = errorMsg.substring(0, 57) + "...";
        }
        graphics.drawCenteredString(font, errorMsg, centerX, boxY + 55, DesignTokens.Text.MUTED);

        // Stack trace (limited)
        if (lastError != null) {
            List<String> stackLines = getStackTraceLines(lastError, MAX_STACK_LINES);
            int lineY = boxY + 75;
            for (String line : stackLines) {
                graphics.drawString(font, line, boxX + 10, lineY, DesignTokens.Text.MUTED);
                lineY += 12;
            }
        }

        // Error count
        String countText = String.format("Errors: %d / %d", errorCount, MAX_ERRORS_BEFORE_LOCKOUT);
        graphics.drawCenteredString(font, countText, centerX, boxY + boxHeight - 25, DesignTokens.Text.SECONDARY);

        // Render buttons
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * Attempt to recover from the error state.
     */
    private void attemptRecovery() {
        LOGGER.info("[ErrorBoundary] Attempting recovery for {}", getClass().getSimpleName());

        hasError = false;
        errorCount = 0;
        lastError = null;

        // Re-initialize
        rebuildWidgets();
    }

    /**
     * Get formatted stack trace lines.
     */
    private List<String> getStackTraceLines(Throwable error, int maxLines) {
        List<String> lines = new ArrayList<>();
        StackTraceElement[] stack = error.getStackTrace();

        for (int i = 0; i < Math.min(stack.length, maxLines); i++) {
            StackTraceElement elem = stack[i];
            String className = elem.getClassName();
            // Shorten class name
            int lastDot = className.lastIndexOf('.');
            if (lastDot > 0) {
                className = className.substring(lastDot + 1);
            }
            String line = String.format("  at %s.%s:%d",
                className, elem.getMethodName(), elem.getLineNumber());
            if (line.length() > 55) {
                line = line.substring(0, 52) + "...";
            }
            lines.add(line);
        }

        if (stack.length > maxLines) {
            lines.add(String.format("  ... and %d more", stack.length - maxLines));
        }

        return lines;
    }

    /**
     * Check if the screen is in an error state.
     */
    public boolean hasError() {
        return hasError;
    }

    /**
     * Get the last error that occurred.
     */
    @Nullable
    public Throwable getLastError() {
        return lastError;
    }

    /**
     * Get the number of errors that have occurred.
     */
    public int getErrorCount() {
        return errorCount;
    }

    /**
     * Reset error state (use with caution).
     */
    protected void resetErrorState() {
        hasError = false;
        errorCount = 0;
        lastError = null;
    }

    /**
     * Force error state (for testing).
     */
    protected void forceError(Throwable error) {
        handleError(error, "forced");
        hasError = true;
        rebuildWidgets();
    }

    @Override
    public void onClose() {
        // Log screen close
        LOGGER.debug("[ErrorBoundary] Closing {} (hadError={})", getClass().getSimpleName(), hasError);
        super.onClose();
    }

    /**
     * Static utility to wrap any screen with error boundary protection.
     * For screens that can't extend ErrorBoundaryScreen.
     */
    public static void safeRender(Screen screen, GuiGraphics graphics,
                                   int mouseX, int mouseY, float partialTick,
                                   Runnable renderAction) {
        try {
            renderAction.run();
        } catch (Exception e) {
            LOGGER.error("[ErrorBoundary] Error rendering {}", screen.getClass().getSimpleName(), e);

            // Render minimal error indicator
            graphics.fill(0, 0, screen.width, screen.height, DesignTokens.ErrorBoundary.SCRIM);
            graphics.drawCenteredString(
                Minecraft.getInstance().font,
                Component.translatable("gui.devmod.error.render_failed"),
                screen.width / 2,
                screen.height / 2,
                DesignTokens.ErrorBoundary.HIGHLIGHT
            );
        }
    }
}
