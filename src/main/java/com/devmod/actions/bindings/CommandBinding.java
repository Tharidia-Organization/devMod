package com.devmod.actions.bindings;

import java.util.Objects;

import com.devmod.actions.ActionOrigin;

/**
 * Brigadier command binding adapter. Represents an action accessible via a chat command.
 */
public record CommandBinding(
    String actionId,
    String commandPath
) implements ActionBinding {

    public CommandBinding {
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(commandPath, "commandPath");
    }

    @Override
    public ActionOrigin origin() {
        return ActionOrigin.COMMAND;
    }

    @Override
    public String describe() {
        return "Command[/" + commandPath + " -> " + actionId + "]";
    }
}
