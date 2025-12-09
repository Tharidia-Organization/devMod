package com.frenkvs.devmod.client.input;

import com.frenkvs.devmod.client.screen.SettingsScreen;
import com.frenkvs.devmod.client.screen.WeaponEditorScreen;
import com.frenkvs.devmod.config.ModConfig;
import com.frenkvs.devmod.manager.FreeCamHandler;
import com.frenkvs.devmod.permission.PermissionManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos; // <--- QUESTO È L'IMPORT CHE MANCAVA!
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = "devmod", value = Dist.CLIENT)
public class KeyInputHandler {

    // Tasti
    public static final KeyMapping OPEN_SETTINGS_KEY = new KeyMapping("key.devmod.settings", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.devmod");
    public static final KeyMapping OPEN_WEAPON_EDITOR_KEY = new KeyMapping("key.devmod.weapon_editor", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, "key.categories.devmod");
    public static final KeyMapping TOGGLE_FREECAM_KEY = new KeyMapping("key.devmod.freecam", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.devmod");

    // Tasto METRO ('O')
    public static final KeyMapping MARK_POINT_KEY = new KeyMapping("key.devmod.mark_point", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, "key.categories.devmod");

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SETTINGS_KEY);
        event.register(OPEN_WEAPON_EDITOR_KEY);
        event.register(TOGGLE_FREECAM_KEY);
        event.register(MARK_POINT_KEY);
    }

    @EventBusSubscriber(modid = "devmod", value = Dist.CLIENT)
    public static class GameEvents {
        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {

            // K - Settings
            if (OPEN_SETTINGS_KEY.consumeClick()) {
                Minecraft.getInstance().setScreen(new SettingsScreen());
            }

            // M - Weapon Editor
            if (OPEN_WEAPON_EDITOR_KEY.consumeClick()) {
                if (Minecraft.getInstance().player != null && !Minecraft.getInstance().player.getMainHandItem().isEmpty()) {
                    Minecraft.getInstance().setScreen(new WeaponEditorScreen());
                } else if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.displayClientMessage(Component.literal("§cDevi avere un oggetto in mano!"), true);
                }
            }

            // V - FreeCam
            if (TOGGLE_FREECAM_KEY.consumeClick()) {
                FreeCamHandler.toggle();
            }

            // O - METRO / MISURATORE
            if (MARK_POINT_KEY.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();

                // Funziona solo se stai guardando un blocco
                if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
                    // Qui ora BlockPos viene riconosciuto grazie all'import in alto
                    BlockPos targetPos = ((net.minecraft.world.phys.BlockHitResult) mc.hitResult).getBlockPos();

                    if (ModConfig.measurePos1 == null) {
                        // 1. Imposta Punto A
                        ModConfig.measurePos1 = targetPos;
                        mc.player.displayClientMessage(Component.literal("§a[Metro] Punto A impostato: " + targetPos.toShortString()), true);
                    } else if (ModConfig.measurePos2 == null) {
                        // 2. Imposta Punto B
                        ModConfig.measurePos2 = targetPos;

                        double dist = Math.sqrt(ModConfig.measurePos1.distSqr(ModConfig.measurePos2));
                        int dx = Math.abs(ModConfig.measurePos1.getX() - ModConfig.measurePos2.getX()) + 1;
                        int dy = Math.abs(ModConfig.measurePos1.getY() - ModConfig.measurePos2.getY()) + 1;
                        int dz = Math.abs(ModConfig.measurePos1.getZ() - ModConfig.measurePos2.getZ()) + 1;

                        mc.player.displayClientMessage(Component.literal(String.format("§a[Metro] Distanza: %.1fm (Box: %dx%dx%d)", dist, dx, dy, dz)), false);
                    } else {
                        // 3. Reset
                        ModConfig.measurePos1 = null;
                        ModConfig.measurePos2 = null;
                        mc.player.displayClientMessage(Component.literal("§e[Metro] Punti resettati."), true);
                    }
                } else {
                    // Reset se guardi il cielo
                    if (ModConfig.measurePos1 != null) {
                        ModConfig.measurePos1 = null;
                        ModConfig.measurePos2 = null;
                        mc.player.displayClientMessage(Component.literal("§e[Metro] Cancellato (Nessun blocco guardato)."), true);
                    }
                }
            }
        }
    }
}