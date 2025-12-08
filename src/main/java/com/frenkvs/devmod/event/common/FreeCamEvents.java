package com.frenkvs.devmod.event.common;

import com.frenkvs.devmod.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = "devmod", value = Dist.CLIENT)
public class FreeCamEvents {

    private static ArmorStand freeCamEntity;
    private static long lastFrameTime = 0; // Per calcolare il Delta Time

    // Usiamo RenderLevelStageEvent perché gira a FPS infiniti (ogni frame), non a 20 TPS.
    // Questo rende il movimento FLUIDISSIMO.
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        // Eseguiamo solo in una fase specifica per non calcolare 10 volte per frame
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Calcolo del Delta Time (Tempo passato dall'ultimo frame in secondi)
        long currentTime = System.nanoTime();
        if (lastFrameTime == 0) lastFrameTime = currentTime;
        float dt = (currentTime - lastFrameTime) / 1_000_000_000f; // Convertiamo nanosecondi in secondi
        lastFrameTime = currentTime;

        // Se il gioco si freeza o carica, evitiamo salti enormi
        if (dt > 0.1f) dt = 0.1f;

        // --- GESTIONE ATTIVAZIONE ---
        if (ModConfig.freeCamEnabled) {
            if (freeCamEntity == null) {
                // Creazione Entità Fantoccio
                freeCamEntity = new ArmorStand(mc.level, mc.player.getX(), mc.player.getY(), mc.player.getZ());
                freeCamEntity.setNoGravity(true);
                freeCamEntity.setInvisible(true);
                freeCamEntity.setId(-123456);
                mc.level.addEntity(freeCamEntity);
                mc.setCameraEntity(freeCamEntity);

                // Sync Coordinate
                ModConfig.fcX = mc.player.getX();
                ModConfig.fcY = mc.player.getY() + mc.player.getEyeHeight();
                ModConfig.fcZ = mc.player.getZ();
                ModConfig.fcYaw = mc.player.getYRot();
                ModConfig.fcPitch = mc.player.getXRot();

                mc.player.displayClientMessage(Component.literal("§a[FreeCam] Fluid Mode Attiva"), true);
            }
        } else {
            // Disattivazione
            if (freeCamEntity != null) {
                mc.setCameraEntity(mc.player);
                mc.level.removeEntity(freeCamEntity.getId(), Entity.RemovalReason.DISCARDED);
                freeCamEntity = null;
                mc.player.displayClientMessage(Component.literal("§c[FreeCam] Disattivata"), true);
            }
            return;
        }

        // --- MOVIMENTO BASATO SU DELTA TIME ---

        // 1. Aggiorna Rotazione (Input Mouse)
        // Prendiamo la rotazione direttamente dal player (che viene aggiornata dal mouse ogni frame)
        ModConfig.fcYaw = mc.player.getYRot();
        ModConfig.fcPitch = mc.player.getXRot();

        // 2. Calcola Velocità (Blocchi al Secondo)
        // Moltiplichiamo per 20 perché speed standard è tarata sui tick (blocchi/tick), noi siamo in secondi
        float baseSpeed = (ModConfig.fcSpeed > 0 ? ModConfig.fcSpeed : 1.0f) * 20.0f;
        if (mc.options.keySprint.isDown()) baseSpeed *= 2.5f;

        float distanceThisFrame = baseSpeed * dt; // Spostamento per QUESTO frame

        // 3. Calcola Vettori Direzionali
        Vec3 forward = Vec3.directionFromRotation(ModConfig.fcPitch, ModConfig.fcYaw);
        Vec3 right = Vec3.directionFromRotation(0, ModConfig.fcYaw + 90);
        Vec3 up = new Vec3(0, 1, 0);

        Vec3 moveVector = Vec3.ZERO;

        if (mc.options.keyUp.isDown())    moveVector = moveVector.add(forward);
        if (mc.options.keyDown.isDown())  moveVector = moveVector.add(forward.reverse());
        if (mc.options.keyRight.isDown()) moveVector = moveVector.add(right);
        if (mc.options.keyLeft.isDown())  moveVector = moveVector.add(right.reverse());
        if (mc.options.keyJump.isDown())  moveVector = moveVector.add(up);
        if (mc.options.keyShift.isDown()) moveVector = moveVector.add(up.reverse());

        // 4. Applica Movimento
        if (moveVector.length() > 0) {
            moveVector = moveVector.normalize().scale(distanceThisFrame);
            ModConfig.fcX += moveVector.x;
            ModConfig.fcY += moveVector.y;
            ModConfig.fcZ += moveVector.z;
        }

        // 5. Aggiorna Entità
        if (freeCamEntity != null) {
            // Posizione precisa al millimetro per questo frame
            freeCamEntity.setPos(ModConfig.fcX, ModConfig.fcY, ModConfig.fcZ);

            // Per evitare interpolazioni strane di Minecraft, aggiorniamo anche le posizioni "vecchie"
            // così il renderer non cerca di "lisciare" il movimento che abbiamo già calcolato noi.
            freeCamEntity.xo = ModConfig.fcX;
            freeCamEntity.yo = ModConfig.fcY;
            freeCamEntity.zo = ModConfig.fcZ;

            // Rotazione Completa
            freeCamEntity.setYRot(ModConfig.fcYaw);
            freeCamEntity.setXRot(ModConfig.fcPitch);
            freeCamEntity.setYHeadRot(ModConfig.fcYaw);
            freeCamEntity.yRotO = ModConfig.fcYaw;
            freeCamEntity.xRotO = ModConfig.fcPitch;
            freeCamEntity.yBodyRot = ModConfig.fcYaw;
        }
    }

    // 2. BLOCCA IL CORPO DEL GIOCATORE
    @SubscribeEvent
    public static void onInputUpdate(MovementInputUpdateEvent event) {
        if (ModConfig.freeCamEnabled) {
            event.getInput().forwardImpulse = 0;
            event.getInput().leftImpulse = 0;
            event.getInput().jumping = false;
            event.getInput().shiftKeyDown = false;
        }
    }
}