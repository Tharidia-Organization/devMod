package com.devmod.actions;

import net.minecraft.commands.CommandSourceStack;

import com.mojang.brigadier.context.CommandContext;

public final class ActionCommandInvoker {
    private ActionCommandInvoker() {}

    public static int invoke(String actionId, CommandContext<CommandSourceStack> context) {
        return ActionRegistry.invoke(actionId, ActionContext.fromCommand(context)) ? 1 : 0;
    }
}
