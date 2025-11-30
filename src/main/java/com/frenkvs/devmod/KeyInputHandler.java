package com.frenkvs.devmod;

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

    // NUOVO Tasto M (Editor Armi)
    public static final KeyMapping OPEN_WEAPON_EDITOR_KEY = new KeyMapping(
            "key.devmod.weapon_editor",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M, // M di Modify
            "key.categories.devmod"
    );

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SETTINGS_KEY);
        event.register(OPEN_WEAPON_EDITOR_KEY); // Registra la M
    }

    @EventBusSubscriber(modid = "devmod", value = Dist.CLIENT)
    public static class GameEvents {
        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {

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
        }
    }
}