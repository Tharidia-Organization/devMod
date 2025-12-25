package com.devmod.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Standardized ESC key behavior for DevMod screens.
 *
 * <h2>ESC Behavior Policy</h2>
 * <p>ESC handling follows a layered approach with consistent behavior:</p>
 *
 * <ol>
 *   <li><b>Level 1 - Active Overlays</b>: Close any open modal overlays first
 *       (dialogs, tooltips, pickers, popovers)</li>
 *   <li><b>Level 2 - Unsaved Changes</b>: If the screen has unsaved changes,
 *       show a confirmation dialog before closing</li>
 *   <li><b>Level 3 - Close Screen</b>: Close the screen and return to
 *       parent or game</li>
 * </ol>
 *
 * <h2>Special Cases</h2>
 * <ul>
 *   <li><b>Forced Choice Screens</b>: Screens like PerkSelection and QuestDeath
 *       override {@code shouldCloseOnEsc()} to return false, forcing users to
 *       make a selection or wait for timeout.</li>
 *   <li><b>Nested Dialogs</b>: Each dialog handles its own ESC. The parent
 *       screen's ESC handler is only invoked after all dialogs are closed.</li>
 *   <li><b>Shift+ESC</b>: Force-closes all overlays in complex screens like
 *       ItemEditorScreen as an emergency escape.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // In your screen class:
 * private final EscapeBehavior escapeBehavior = new EscapeBehavior()
 *     .addOverlayCheck(this::hasActiveDialog, this::closeActiveDialog)
 *     .addOverlayCheck(this::isPopupOpen, this::closePopup)
 *     .setDirtyCheck(this::hasUnsavedChanges)
 *     .setConfirmDialogFactory(this::showSaveConfirmDialog)
 *     .setCloseAction(this::onClose);
 *
 * @Override
 * public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
 *     if (escapeBehavior.handleKeyPressed(keyCode, modifiers)) {
 *         return true;
 *     }
 *     return super.keyPressed(keyCode, scanCode, modifiers);
 * }
 * }</pre>
 */
@OnlyIn(Dist.CLIENT)
public final class EscapeBehavior {

    /**
     * Record for overlay check + close action pair.
     */
    private record OverlayHandler(BooleanSupplier isOpen, Runnable close) {}

    // ═══════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════

    private final List<OverlayHandler> overlayHandlers = new ArrayList<>();
    private BooleanSupplier dirtyCheck = () -> false;
    private Supplier<Boolean> confirmDialogFactory = () -> false;
    private Runnable closeAction = () -> {};
    private boolean allowShiftEscapeForce = true;

    // ═══════════════════════════════════════════════════════════════
    // CONFIGURATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Add an overlay check and close action pair.
     * Overlays are checked in the order they are added (first added = first checked).
     *
     * @param isOpen Returns true if the overlay is currently open
     * @param close  Action to close the overlay
     * @return this for chaining
     */
    public EscapeBehavior addOverlayCheck(BooleanSupplier isOpen, Runnable close) {
        overlayHandlers.add(new OverlayHandler(isOpen, close));
        return this;
    }

    /**
     * Set the dirty state check (for unsaved changes detection).
     *
     * @param dirtyCheck Returns true if there are unsaved changes
     * @return this for chaining
     */
    public EscapeBehavior setDirtyCheck(BooleanSupplier dirtyCheck) {
        this.dirtyCheck = dirtyCheck != null ? dirtyCheck : () -> false;
        return this;
    }

    /**
     * Set the confirm dialog factory (called when dirty and ESC pressed).
     * The factory should show a confirmation dialog and return true if it was shown.
     *
     * @param factory Factory that shows confirm dialog, returns true if shown
     * @return this for chaining
     */
    public EscapeBehavior setConfirmDialogFactory(Supplier<Boolean> factory) {
        this.confirmDialogFactory = factory != null ? factory : () -> false;
        return this;
    }

    /**
     * Set the close action (called when ESC should close the screen).
     *
     * @param closeAction The close action
     * @return this for chaining
     */
    public EscapeBehavior setCloseAction(Runnable closeAction) {
        this.closeAction = closeAction != null ? closeAction : () -> {};
        return this;
    }

    /**
     * Enable/disable Shift+ESC force close behavior.
     *
     * @param allow true to allow Shift+ESC to force close all overlays
     * @return this for chaining
     */
    public EscapeBehavior setAllowShiftEscapeForce(boolean allow) {
        this.allowShiftEscapeForce = allow;
        return this;
    }

    // ═══════════════════════════════════════════════════════════════
    // KEY HANDLING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Handle key press events following the ESC behavior policy.
     *
     * @param keyCode   The key code
     * @param modifiers The modifier keys
     * @return true if the key was handled
     */
    public boolean handleKeyPressed(int keyCode, int modifiers) {
        if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
            return false;
        }

        // Shift+ESC = Force close all overlays
        if (allowShiftEscapeForce && (modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
            return forceCloseAllOverlays();
        }

        // Level 1: Close overlays in order
        for (OverlayHandler handler : overlayHandlers) {
            if (handler.isOpen().getAsBoolean()) {
                handler.close().run();
                return true;
            }
        }

        // Level 2: Check for unsaved changes
        if (dirtyCheck.getAsBoolean()) {
            if (confirmDialogFactory.get()) {
                return true; // Dialog was shown
            }
        }

        // Level 3: Close the screen
        closeAction.run();
        return true;
    }

    /**
     * Force close all overlays (Shift+ESC behavior).
     *
     * @return true if any overlay was closed
     */
    public boolean forceCloseAllOverlays() {
        boolean anyClosed = false;
        for (OverlayHandler handler : overlayHandlers) {
            if (handler.isOpen().getAsBoolean()) {
                handler.close().run();
                anyClosed = true;
            }
        }
        return anyClosed;
    }

    /**
     * Check if any overlay is currently open.
     *
     * @return true if any overlay is open
     */
    public boolean hasOpenOverlay() {
        for (OverlayHandler handler : overlayHandlers) {
            if (handler.isOpen().getAsBoolean()) {
                return true;
            }
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    // STATIC UTILITIES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Close a screen and return to game.
     *
     * @param screen The screen to close
     */
    public static void closeToGame(Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.setScreen(null);
        }
    }

    /**
     * Close a screen and return to a parent screen.
     *
     * @param screen The screen to close
     * @param parent The parent screen to return to
     */
    public static void closeToParent(Screen screen, Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.setScreen(parent);
        }
    }
}
