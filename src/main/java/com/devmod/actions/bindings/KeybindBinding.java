package com.devmod.actions.bindings;

import java.util.Objects;

import com.devmod.actions.ActionOrigin;

/**
 * Keybind binding adapter. Represents an action triggered by a keyboard shortcut.
 */
public record KeybindBinding(
    String actionId,
    String keybindId
) implements ActionBinding {

    public KeybindBinding {
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(keybindId, "keybindId");
    }

    @Override
    public ActionOrigin origin() {
        return ActionOrigin.KEYBIND;
    }

    @Override
    public String describe() {
        return "Keybind[" + keybindId + " -> " + actionId + "]";
    }
}
