package com.frenkvs.devmod.actions;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;

public final class ActionCommandInvoker {
    private ActionCommandInvoker() {}

    public static int invoke(String actionId, CommandContext<CommandSourceStack> context) {
        return ActionRegistry.invoke(actionId, ActionContext.fromCommand(context)) ? 1 : 0;
    }
}
