package com.frenkvs.devmod.client.input;

import com.frenkvs.devmod.client.screen.SettingsScreen;
import com.frenkvs.devmod.client.screen.WeaponEditorScreen;
import com.frenkvs.devmod.permission.PermissionManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = "devmod", value = Dist.CLIENT)
public class KeyInputHandler {

    // Tasto K (Impostazioni Mob)
    public static final KeyMapping OPEN_SETTINGS_KEY = new KeyMapping(
            "key.devmod.settings",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.devmod"
    );

    // Tasto M (Editor Armi)
    public static final KeyMapping OPEN_WEAPON_EDITOR_KEY = new KeyMapping(
            "key.devmod.weapon_editor",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M, // M di Modify
            "key.categories.devmod"
    );

    // Tasto V (FreeCam)
    public static final KeyMapping TOGGLE_FREECAM_KEY = new KeyMapping(
            "key.devmod.freecam",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.devmod"
    );

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SETTINGS_KEY);
        event.register(OPEN_WEAPON_EDITOR_KEY);
        event.register(TOGGLE_FREECAM_KEY);
    }

    @EventBusSubscriber(modid = "devmod", value = Dist.CLIENT)
    public static class GameEvents {
        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            // Check if player has OP level 4 or higher
            if (!PermissionManager.isClientOp()) return;

            // Se premi K
            if (OPEN_SETTINGS_KEY.consumeClick()) {
                Minecraft.getInstance().setScreen(new SettingsScreen());
            }

            // Se premi M (e hai qualcosa in mano)
            if (OPEN_WEAPON_EDITOR_KEY.consumeClick()) {
                if (Minecraft.getInstance().player != null && !Minecraft.getInstance().player.getMainHandItem().isEmpty()) {
                    Minecraft.getInstance().setScreen(new WeaponEditorScreen());
                } else {
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.displayClientMessage(
                                net.minecraft.network.chat.Component.literal("§cDevi avere un oggetto in mano!"), true);
                    }
                }
            }

            // Se premi V (Free Cam)
            if (TOGGLE_FREECAM_KEY.consumeClick()) {
                // CORRETTO: Usa i punti al posto degli slash
                com.frenkvs.devmod.manager.FreeCamHandler.toggle();
            }
        }
    }
}