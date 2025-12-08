package com.frenkvs.devmod.client.input;

import com.frenkvs.devmod.client.screen.SettingsScreen;
import com.frenkvs.devmod.client.screen.WeaponEditorScreen;
import com.frenkvs.devmod.config.ModConfig;
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
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.HitResult;

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
    // NUOVO Tasto V (FreeCam) - AGGIUNGI QUESTO BLOCCO
    public static final KeyMapping TOGGLE_FREECAM_KEY = new KeyMapping(
            "key.devmod.freecam",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V, // Impostiamo V come tasto di default
            "key.categories.devmod"
    );
    // NUOVO TASTO 'O' (Options/Overlay/Origin)
    public static final KeyMapping MARK_POINT_KEY = new KeyMapping(
            "key.devmod.mark_point",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            "key.categories.devmod"
    );

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SETTINGS_KEY);
        event.register(OPEN_WEAPON_EDITOR_KEY); // Registra la M
        event.register(TOGGLE_FREECAM_KEY);
        event.register(MARK_POINT_KEY);
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

            // LOGICA MISURATORE
            if (MARK_POINT_KEY.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
                    BlockPos targetPos = ((net.minecraft.world.phys.BlockHitResult) mc.hitResult).getBlockPos();

                    // Logica ciclica: Set A -> Set B -> Reset -> Set A...
                    if (ModConfig.measurePos1 == null) {
                        ModConfig.measurePos1 = targetPos;
                        mc.player.displayClientMessage(Component.literal("§a[Misura] Punto A impostato: " + targetPos.toShortString()), true);
                    } else if (ModConfig.measurePos2 == null) {
                        ModConfig.measurePos2 = targetPos;

                        // Calcolo immediato in chat
                        double dist = Math.sqrt(ModConfig.measurePos1.distSqr(ModConfig.measurePos2));
                        int dx = Math.abs(ModConfig.measurePos1.getX() - ModConfig.measurePos2.getX()) + 1; // +1 perché include i blocchi estremi
                        int dy = Math.abs(ModConfig.measurePos1.getY() - ModConfig.measurePos2.getY()) + 1;
                        int dz = Math.abs(ModConfig.measurePos1.getZ() - ModConfig.measurePos2.getZ()) + 1;

                        mc.player.displayClientMessage(Component.literal(String.format("§a[Misura] Punto B impostato. Dist: %.2f (Area: %dx%dx%d)", dist, dx, dy, dz)), false);
                    } else {
                        // Reset
                        ModConfig.measurePos1 = null;
                        ModConfig.measurePos2 = null;
                        mc.player.displayClientMessage(Component.literal("§e[Misura] Punti resettati."), true);
                    }
                } else {
                    // Reset se guardi il cielo
                    ModConfig.measurePos1 = null;
                    ModConfig.measurePos2 = null;
                    mc.player.displayClientMessage(Component.literal("§e[Misura] Reset (Nessun blocco guardato)"), true);
                }
            }

            // Se premi V (Free Cam)
            if (TOGGLE_FREECAM_KEY.consumeClick()) {
                ModConfig.freeCamEnabled = !ModConfig.freeCamEnabled;

                if (ModConfig.freeCamEnabled) {
                    // ATTIVAZIONE: Copia la posizione attuale del player nella telecamera fantasma
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        ModConfig.fcX = mc.player.getX();
                        ModConfig.fcY = mc.player.getY() + mc.player.getEyeHeight(); // Parti dagli occhi
                        ModConfig.fcZ = mc.player.getZ();
                        ModConfig.fcYaw = mc.player.getYRot();
                        ModConfig.fcPitch = mc.player.getXRot();

                        mc.player.displayClientMessage(
                                net.minecraft.network.chat.Component.literal("§a[DevMod] FreeCam ATTIVA"), true);
                    }
                } else {
                    // DISATTIVAZIONE
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.displayClientMessage(
                                net.minecraft.network.chat.Component.literal("§c[DevMod] FreeCam DISATTIVATA"), true);
                    }
                }
            }
        }
    }
}