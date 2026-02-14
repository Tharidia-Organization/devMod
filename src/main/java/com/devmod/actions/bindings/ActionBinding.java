package com.devmod.actions.bindings;

import com.devmod.actions.ActionOrigin;

/**
 * Interface for binding an action to an input source. Each binding type
 * (radial, keybind, command, network) implements this to declare which
 * action it triggers and from which origin.
 */
public interface ActionBinding {

    /**
     * Returns the action ID this binding triggers.
     */
    String actionId();

    /**
     * Returns the origin this binding uses when invoking the action.
     */
    ActionOrigin origin();

    /**
     * Returns a human-readable description of this binding (for debug/logging).
     */
    String describe();
}
