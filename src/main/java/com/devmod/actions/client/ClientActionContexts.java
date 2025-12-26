package com.devmod.actions.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;

import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionOrigin;

public final class ClientActionContexts {
    private ClientActionContexts() {}

    public static ActionContext forRadial() {
        return forClient(ActionOrigin.RADIAL);
    }

    public static ActionContext forKeybind() {
        return forClient(ActionOrigin.KEYBIND);
    }

    public static ActionContext forClient(ActionOrigin origin) {
        return buildContext(origin, null);
    }

    public static ActionContext forClient(ActionOrigin origin, Object payload) {
        return buildContext(origin, payload);
    }

    private static ActionContext buildContext(ActionOrigin origin, Object payload) {
        Minecraft mc = Minecraft.getInstance();
        ActionContext.Builder builder = ActionContext.builder(origin)
            .player(mc.player)
            .clientSide(true)
            .screenOpen(mc.screen != null)
            .shiftDown(Screen.hasShiftDown())
            .ctrlDown(Screen.hasControlDown())
            .altDown(Screen.hasAltDown())
            .commandInvoker(command -> {
                var player = mc.player;
                if (player != null && mc.getConnection() != null) {
                    player.connection.sendCommand(java.util.Objects.requireNonNull(command, "command"));
                }
            });
        builder.commandPrompt(command -> mc.setScreen(new ChatScreen("/"
            + java.util.Objects.requireNonNull(command, "command"))));
        builder.payload(payload);
        return builder.build();
    }
}
