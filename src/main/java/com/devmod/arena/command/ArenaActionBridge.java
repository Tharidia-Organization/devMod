package com.devmod.arena.command;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import javax.annotation.Nullable;

public final class ArenaActionBridge {
    private static final Map<String, Function<CommandContext<CommandSourceStack>, Integer>> HANDLERS =
        new ConcurrentHashMap<>();

    private ArenaActionBridge() {}

    public static void register(String actionId, Function<CommandContext<CommandSourceStack>, Integer> handler) {
        HANDLERS.put(actionId, handler);
    }

    @Nullable
    public static Function<CommandContext<CommandSourceStack>, Integer> getHandler(String actionId) {
        return HANDLERS.get(actionId);
    }
}
