package com.frenkvs.devmod.ui.editor.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages keyboard focus navigation between focusable UI components.
 * Supports Tab/Shift+Tab navigation and focus state tracking.
 *
 * Usage:
 * <pre>
 * FocusManager focus = new FocusManager();
 * focus.register(button1);
 * focus.register(slider1);
 * focus.register(toggle1);
 *
 * // In keyPressed handler:
 * if (focus.handleKeyPressed(keyCode, modifiers)) {
 *     return true;
 * }
 *
 * // In render:
 * boolean isFocused = focus.isFocused(button1);
 * </pre>
 *
 * @see Focusable
 * @see EditorComponent
 */
public class FocusManager {

    private final List<Focusable> focusables = new ArrayList<>();
    private int focusIndex = -1;
    private boolean enabled = true;

    /**
     * Interface for components that can receive keyboard focus.
     */
    public interface Focusable {
        /**
         * Get unique ID for this focusable.
         */
        String getId();

        /**
         * Check if this component can currently receive focus.
         * Return false if disabled or hidden.
         */
        default boolean canFocus() {
            return true;
        }

        /**
         * Called when this component gains focus.
         */
        default void onFocusGained() {}

        /**
         * Called when this component loses focus.
         */
        default void onFocusLost() {}
    }

    // =========================================================================
    // REGISTRATION
    // =========================================================================

    /**
     * Register a focusable component.
     * Components are navigated in registration order.
     *
     * @param focusable The component to register
     */
    public void register(Focusable focusable) {
        if (focusable != null && !focusables.contains(focusable)) {
            focusables.add(focusable);
        }
    }

    /**
     * Unregister a focusable component.
     *
     * @param focusable The component to unregister
     */
    public void unregister(Focusable focusable) {
        int idx = focusables.indexOf(focusable);
        if (idx >= 0) {
            // Adjust focus index if needed
            if (idx < focusIndex) {
                focusIndex--;
            } else if (idx == focusIndex) {
                focusIndex = -1;
            }
            focusables.remove(idx);
        }
    }

    /**
     * Clear all registered focusables.
     */
    public void clear() {
        if (focusIndex >= 0 && focusIndex < focusables.size()) {
            focusables.get(focusIndex).onFocusLost();
        }
        focusables.clear();
        focusIndex = -1;
    }

    // =========================================================================
    // FOCUS CONTROL
    // =========================================================================

    /**
     * Move focus to the next focusable component.
     *
     * @return true if focus was moved
     */
    public boolean focusNext() {
        if (focusables.isEmpty() || !enabled) return false;

        // Start from current or beginning
        int start = focusIndex >= 0 ? focusIndex + 1 : 0;
        int count = focusables.size();

        for (int i = 0; i < count; i++) {
            int idx = (start + i) % count;
            Focusable target = focusables.get(idx);
            if (target.canFocus()) {
                setFocusIndex(idx);
                return true;
            }
        }

        return false;
    }

    /**
     * Move focus to the previous focusable component.
     *
     * @return true if focus was moved
     */
    public boolean focusPrevious() {
        if (focusables.isEmpty() || !enabled) return false;

        // Start from current or end
        int start = focusIndex >= 0 ? focusIndex - 1 : focusables.size() - 1;
        int count = focusables.size();

        for (int i = 0; i < count; i++) {
            int idx = (start - i + count) % count;
            Focusable target = focusables.get(idx);
            if (target.canFocus()) {
                setFocusIndex(idx);
                return true;
            }
        }

        return false;
    }

    /**
     * Set focus to a specific component.
     *
     * @param focusable The component to focus
     * @return true if focus was set
     */
    public boolean setFocus(Focusable focusable) {
        int idx = focusables.indexOf(focusable);
        if (idx >= 0 && focusables.get(idx).canFocus()) {
            setFocusIndex(idx);
            return true;
        }
        return false;
    }

    /**
     * Set focus by component ID.
     *
     * @param id The ID of the component to focus
     * @return true if focus was set
     */
    public boolean setFocusById(String id) {
        for (int i = 0; i < focusables.size(); i++) {
            Focusable f = focusables.get(i);
            if (f.getId().equals(id) && f.canFocus()) {
                setFocusIndex(i);
                return true;
            }
        }
        return false;
    }

    /**
     * Clear focus from all components.
     */
    public void clearFocus() {
        if (focusIndex >= 0 && focusIndex < focusables.size()) {
            focusables.get(focusIndex).onFocusLost();
        }
        focusIndex = -1;
    }

    private void setFocusIndex(int newIndex) {
        if (newIndex == focusIndex) return;

        // Notify old focused component
        if (focusIndex >= 0 && focusIndex < focusables.size()) {
            focusables.get(focusIndex).onFocusLost();
        }

        focusIndex = newIndex;

        // Notify new focused component
        if (focusIndex >= 0 && focusIndex < focusables.size()) {
            focusables.get(focusIndex).onFocusGained();
        }
    }

    // =========================================================================
    // STATE QUERIES
    // =========================================================================

    /**
     * Check if a component is currently focused.
     *
     * @param focusable The component to check
     * @return true if focused
     */
    public boolean isFocused(Focusable focusable) {
        if (focusIndex < 0 || focusIndex >= focusables.size()) return false;
        return focusables.get(focusIndex) == focusable;
    }

    /**
     * Check if a component ID is currently focused.
     *
     * @param id The component ID to check
     * @return true if focused
     */
    public boolean isFocusedById(String id) {
        if (focusIndex < 0 || focusIndex >= focusables.size()) return false;
        return focusables.get(focusIndex).getId().equals(id);
    }

    /**
     * Get the currently focused component.
     *
     * @return The focused component, or null if none
     */
    public Focusable getFocused() {
        if (focusIndex < 0 || focusIndex >= focusables.size()) return null;
        return focusables.get(focusIndex);
    }

    /**
     * Check if any component has focus.
     */
    public boolean hasFocus() {
        return focusIndex >= 0 && focusIndex < focusables.size();
    }

    /**
     * Get the number of registered focusables.
     */
    public int size() {
        return focusables.size();
    }

    /**
     * Check if focus management is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enable or disable focus management.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            clearFocus();
        }
    }

    // =========================================================================
    // INPUT HANDLING
    // =========================================================================

    /**
     * Handle key press for focus navigation.
     * Responds to Tab and Shift+Tab.
     *
     * @param keyCode   GLFW key code
     * @param modifiers Keyboard modifiers
     * @return true if the key was handled
     */
    public boolean handleKeyPressed(int keyCode, int modifiers) {
        if (!enabled || focusables.isEmpty()) return false;

        // Tab key
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
            boolean shift = (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0;
            return shift ? focusPrevious() : focusNext();
        }

        return false;
    }

    /**
     * Handle mouse click to set focus on clicked component.
     *
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param focusables Map of components with their bounds
     */
    public void handleMouseClick(double mouseX, double mouseY) {
        // This is a simplified version - in practice you'd check bounds
        // The actual implementation would need component bounds
    }
}
