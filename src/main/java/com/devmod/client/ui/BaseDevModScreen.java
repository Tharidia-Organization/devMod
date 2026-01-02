package com.devmod.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.state.ClientStateManager;
import com.devmod.client.telemetry.UiTelemetry;

/**
 * Base screen class for all DevMod screens.
 *
 * <p>Provides common functionality:
 * <ul>
 *   <li>Error boundary with fallback UI</li>
 *   <li>Telemetry hooks (open/close tracking)</li>
 *   <li>Common keyboard handling (ESC, etc.)</li>
 *   <li>Theme integration</li>
 *   <li>State subscription management</li>
 *   <li>Consistent close behavior</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * public class MyScreen extends BaseDevModScreen {
 *     public MyScreen() {
 *         super(Component.literal("My Screen"), "my_screen");
 *     }
 *
 *     @Override
 *     protected void initContent() {
 *         // Initialize your widgets here
 *     }
 *
 *     @Override
 *     protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
 *         // Render your content here
 *     }
 * }
 * }</pre>
 */
@OnlyIn(Dist.CLIENT)
public abstract class BaseDevModScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseDevModScreen.class);

    // ============================================================================
    // ERROR BOUNDARY
    // ============================================================================

    /** Maximum consecutive errors before giving up on recovery */
    private static final int MAX_ERROR_COUNT = 3;

    /** Whether we're in error state */
    private boolean hasError = false;

    /** Error message to display */
    @Nullable
    private String errorMessage = null;

    /** Consecutive error count */
    private int errorCount = 0;

    // ============================================================================
    // TELEMETRY
    // ============================================================================

    /** Screen identifier for telemetry */
    private final String screenId;

    /** Screen category for telemetry */
    private final String screenCategory;

    /** Whether telemetry has been sent for this open */
    private boolean telemetrySent = false;

    // ============================================================================
    // STATE MANAGEMENT
    // ============================================================================

    /** State subscriptions to clean up on close */
    private final List<Runnable> subscriptionCleanups = new ArrayList<>();

    /** Parent screen to return to */
    @Nullable
    protected Screen parentScreen;

    /** Whether screen is being closed */
    private boolean isClosing = false;

    // ============================================================================
    // UI STATE
    // ============================================================================

    /** Status message to show temporarily */
    @Nullable
    private String statusMessage;

    /** Status message color */
    private int statusColor = 0xFFFFFFFF;

    /** Ticks remaining for status message */
    private int statusTicks = 0;

    /** Default status message duration in ticks */
    protected static final int STATUS_DURATION_TICKS = 60;

    // ============================================================================
    // CONSTRUCTORS
    // ============================================================================

    /**
     * Create a new DevMod screen.
     *
     * @param title The screen title
     * @param screenId Unique identifier for telemetry (e.g., "mailbox", "party")
     */
    protected BaseDevModScreen(Component title, String screenId) {
        this(title, screenId, "devmod");
    }

    /**
     * Create a new DevMod screen with category.
     *
     * @param title The screen title
     * @param screenId Unique identifier for telemetry
     * @param screenCategory Category for grouping (e.g., "social", "combat")
     */
    protected BaseDevModScreen(Component title, String screenId, String screenCategory) {
        super(title);
        this.screenId = screenId;
        this.screenCategory = screenCategory;
    }

    /**
     * Set parent screen to return to on close.
     */
    public BaseDevModScreen withParent(@Nullable Screen parent) {
        this.parentScreen = parent;
        return this;
    }

    // ============================================================================
    // LIFECYCLE
    // ============================================================================

    @Override
    protected final void init() {
        // Reset error state on re-init
        if (errorCount < MAX_ERROR_COUNT) {
            hasError = false;
            errorMessage = null;
        }

        // Track screen open
        if (!telemetrySent) {
            UiTelemetry.screenOpened(screenCategory, screenId);
            telemetrySent = true;
        }

        try {
            initContent();
        } catch (Exception e) {
            handleInitError(e);
        }
    }

    /**
     * Initialize screen content. Override this instead of init().
     */
    protected abstract void initContent();

    @Override
    public void onClose() {
        if (isClosing) return;
        isClosing = true;

        // Clean up subscriptions
        for (Runnable cleanup : subscriptionCleanups) {
            try {
                cleanup.run();
            } catch (Exception e) {
                LOGGER.warn("[{}] Error cleaning up subscription", screenId, e);
            }
        }
        subscriptionCleanups.clear();

        // Track screen close
        UiTelemetry.screenClosed(screenCategory, screenId);

        // Call subclass cleanup
        try {
            onContentClose();
        } catch (Exception e) {
            LOGGER.warn("[{}] Error in onContentClose", screenId, e);
        }

        // Return to parent or close
        if (parentScreen != null) {
            Minecraft.getInstance().setScreen(parentScreen);
        } else {
            super.onClose();
        }
    }

    /**
     * Called when screen is closing. Override to clean up resources.
     */
    protected void onContentClose() {
        // Default implementation does nothing
    }

    // ============================================================================
    // RENDERING
    // ============================================================================

    @Override
    public final void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (hasError) {
            renderErrorFallback(graphics, mouseX, mouseY, partialTick);
            return;
        }

        try {
            // Render background
            renderBackground(graphics, mouseX, mouseY, partialTick);

            // Render content
            renderContent(graphics, mouseX, mouseY, partialTick);

            // Render widgets
            super.render(graphics, mouseX, mouseY, partialTick);

            // Render status message
            if (statusTicks > 0) {
                renderStatusMessage(graphics);
            }

            // Reset error count on successful render
            if (errorCount > 0) {
                errorCount = 0;
            }
        } catch (Exception e) {
            handleRenderError(e);
        }
    }

    /**
     * Render screen content. Override this instead of render().
     */
    protected abstract void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    /**
     * Render error fallback UI.
     */
    protected void renderErrorFallback(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dark background
        graphics.fill(0, 0, width, height, 0xE0200000);

        // Error title
        int centerX = width / 2;
        int centerY = height / 2;

        Component errorTitle = Component.translatable("devmod.screen.error.title");
        graphics.drawCenteredString(font, errorTitle, centerX, centerY - 30, 0xFFFF4444);

        // Error message
        if (errorMessage != null) {
            Component msgComponent = Component.literal(errorMessage);
            graphics.drawCenteredString(font, msgComponent, centerX, centerY - 10, 0xFFCCCCCC);
        }

        // Instructions
        Component escHint = Component.translatable("devmod.screen.error.hint");
        graphics.drawCenteredString(font, escHint, centerX, centerY + 20, 0xFF888888);

        // Retry hint if not maxed out
        if (errorCount < MAX_ERROR_COUNT) {
            Component retryHint = Component.translatable("devmod.screen.error.retry");
            graphics.drawCenteredString(font, retryHint, centerX, centerY + 40, 0xFF888888);
        }
    }

    /**
     * Render status message.
     */
    protected void renderStatusMessage(GuiGraphics graphics) {
        if (statusMessage == null) return;

        int msgWidth = font.width(statusMessage);
        int msgX = (width - msgWidth) / 2;
        int msgY = height - 50;

        // Background
        graphics.fill(msgX - 6, msgY - 4, msgX + msgWidth + 6, msgY + 12, 0xC0000000);

        // Text
        graphics.drawString(font, statusMessage, msgX, msgY, statusColor, false);
    }

    // ============================================================================
    // TICK
    // ============================================================================

    @Override
    public void tick() {
        super.tick();

        // Update status message
        if (statusTicks > 0) {
            statusTicks--;
            if (statusTicks == 0) {
                statusMessage = null;
            }
        }

        // Call subclass tick
        try {
            tickContent();
        } catch (Exception e) {
            LOGGER.warn("[{}] Error in tickContent", screenId, e);
        }
    }

    /**
     * Called each tick. Override to update state.
     */
    protected void tickContent() {
        // Default implementation does nothing
    }

    // ============================================================================
    // INPUT HANDLING
    // ============================================================================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC always closes
        if (keyCode == 256) { // GLFW_KEY_ESCAPE
            onClose();
            return true;
        }

        // Let subclass handle first
        try {
            if (handleKeyPress(keyCode, scanCode, modifiers)) {
                return true;
            }
        } catch (Exception e) {
            LOGGER.warn("[{}] Error in handleKeyPress", screenId, e);
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Handle key press. Return true if consumed.
     */
    protected boolean handleKeyPress(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        try {
            if (handleMouseClick(mouseX, mouseY, button)) {
                return true;
            }
        } catch (Exception e) {
            LOGGER.warn("[{}] Error in handleMouseClick", screenId, e);
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Handle mouse click. Return true if consumed.
     */
    protected boolean handleMouseClick(double mouseX, double mouseY, int button) {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        try {
            if (handleMouseScroll(mouseX, mouseY, scrollX, scrollY)) {
                return true;
            }
        } catch (Exception e) {
            LOGGER.warn("[{}] Error in handleMouseScroll", screenId, e);
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * Handle mouse scroll. Return true if consumed.
     */
    protected boolean handleMouseScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    // ============================================================================
    // ERROR HANDLING
    // ============================================================================

    private void handleInitError(Exception e) {
        errorCount++;
        hasError = true;
        errorMessage = e.getMessage();
        LOGGER.error("[{}] Error during init (count: {})", screenId, errorCount, e);
    }

    private void handleRenderError(Exception e) {
        errorCount++;
        if (errorCount >= MAX_ERROR_COUNT) {
            hasError = true;
            errorMessage = e.getMessage();
        }
        LOGGER.error("[{}] Error during render (count: {})", screenId, errorCount, e);
    }

    // ============================================================================
    // STATUS MESSAGES
    // ============================================================================

    /**
     * Show a temporary status message.
     */
    protected void showStatus(String message, int color) {
        this.statusMessage = message;
        this.statusColor = color;
        this.statusTicks = STATUS_DURATION_TICKS;
    }

    /**
     * Show a success status message.
     */
    protected void showSuccess(String message) {
        showStatus(message, 0xFF55FF55);
    }

    /**
     * Show an error status message.
     */
    protected void showError(String message) {
        showStatus(message, 0xFFFF5555);
    }

    /**
     * Show an info status message.
     */
    protected void showInfo(String message) {
        showStatus(message, 0xFFFFFF55);
    }

    // ============================================================================
    // STATE SUBSCRIPTIONS
    // ============================================================================

    /**
     * Subscribe to client state changes with automatic cleanup.
     */
    protected <T> void subscribeToState(
            java.util.function.Supplier<T> stateGetter,
            Consumer<T> onChange) {
        // Initial state
        onChange.accept(stateGetter.get());

        // Note: Full implementation would integrate with ClientStateManager
        // This is a simplified version that works with direct polling
    }

    /**
     * Register a cleanup action to run on close.
     */
    protected void registerCleanup(Runnable cleanup) {
        subscriptionCleanups.add(cleanup);
    }

    // ============================================================================
    // WIDGET HELPERS
    // ============================================================================

    /**
     * Play standard click sound.
     */
    protected void playClickSound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(),
                1.0f, 1.0f
            );
        }
    }

    // ============================================================================
    // GETTERS
    // ============================================================================

    /**
     * Get the screen identifier.
     */
    public String getScreenId() {
        return screenId;
    }

    /**
     * Get the screen category.
     */
    public String getScreenCategory() {
        return screenCategory;
    }

    /**
     * Check if screen is in error state.
     */
    public boolean hasError() {
        return hasError;
    }

    /**
     * Get client state manager for convenience.
     */
    protected ClientStateManager getStateManager() {
        return ClientStateManager.INSTANCE;
    }
}
