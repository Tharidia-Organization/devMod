package com.frenkvs.devmod.manager;

import com.frenkvs.devmod.config.ModConfig;
import com.frenkvs.devmod.event.common.BuilderState;
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
public class FreeCamHandler {

    private static long lastFrameTime = 0;

    // --- TOGGLE (Chiamato dal tasto V) ---
    public static void toggle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Invertiamo lo stato
        BuilderState.isFreeCamActive = !BuilderState.isFreeCamActive;
        // Sincronizziamo anche ModConfig per sicurezza (se usato altrove)
        ModConfig.freeCamEnabled = BuilderState.isFreeCamActive;

        if (BuilderState.isFreeCamActive) {
            // ATTIVAZIONE
            // 1. Creiamo un ArmorStand invisibile alla posizione della testa del player
            ArmorStand dummy = new ArmorStand(mc.level, mc.player.getX(), mc.player.getY(), mc.player.getZ());
            dummy.setNoGravity(true);
            dummy.setInvisible(true); // Invisibile
            dummy.setShowArms(false);
            dummy.setId(-123456); // ID negativo per evitare conflitti

            // Copiamo rotazione iniziale
            dummy.setYRot(mc.player.getYRot());
            dummy.setXRot(mc.player.getXRot());
            dummy.yRotO = mc.player.getYRot();
            dummy.xRotO = mc.player.getXRot();

            // 2. Lo aggiungiamo al mondo client-side
            mc.level.addEntity(dummy);

            // 3. Impostiamo la camera su di lui
            mc.setCameraEntity(dummy);
            BuilderState.freeCamEntity = dummy;

            mc.player.displayClientMessage(Component.literal("§a[DevMod] FreeCam Attiva"), true);
        } else {
            // DISATTIVAZIONE
            if (BuilderState.freeCamEntity != null) {
                // 1. Ripristiniamo camera sul player
                mc.setCameraEntity(mc.player);
                // 2. Rimuoviamo entità fantoccio
                mc.level.removeEntity(BuilderState.freeCamEntity.getId(), Entity.RemovalReason.DISCARDED);
                BuilderState.freeCamEntity = null;
            }

            mc.player.displayClientMessage(Component.literal("§c[DevMod] FreeCam Disattivata"), true);
        }
    }

    // --- MOVIMENTO FLUIDO (Ogni Frame Render) ---
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!BuilderState.isFreeCamActive || BuilderState.freeCamEntity == null) return;

        // Eseguiamo solo dopo che la camera è stata processata
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Calcolo Delta Time
        long now = System.nanoTime();
        if (lastFrameTime == 0) lastFrameTime = now;
        float dt = (now - lastFrameTime) / 1_000_000_000f;
        lastFrameTime = now;
        if (dt > 0.1f) dt = 0.1f;

        // --- 1. SINCRONIZZAZIONE ROTAZIONE (FIX VISUALE) ---
        // Anche se siamo in FreeCam, il mouse continua a ruotare il "Player" logico.
        // Noi prendiamo quella rotazione e la applichiamo all'ArmorStand.
        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();

        BuilderState.freeCamEntity.setYRot(yaw);
        BuilderState.freeCamEntity.setXRot(pitch);

        // Aggiorniamo anche la "testa" e i valori vecchi per evitare scatti (interpolazione)
        BuilderState.freeCamEntity.yRotO = yaw;
        BuilderState.freeCamEntity.xRotO = pitch;
        BuilderState.freeCamEntity.setYHeadRot(yaw);

        // ---------------------------------------------------

        // Calcolo Vettori Direzione basati sulla NUOVA rotazione
        Vec3 forward = Vec3.directionFromRotation(pitch, yaw);
        Vec3 right = Vec3.directionFromRotation(0, yaw + 90);
        Vec3 up = new Vec3(0, 1, 0);

        // Velocità
        float speed = ModConfig.fcSpeed > 0 ? ModConfig.fcSpeed * 10.0f : 10.0f;
        if (mc.options.keySprint.isDown()) speed *= 3.0f;

        float distance = speed * dt;
        Vec3 move = Vec3.ZERO;

        // Input Tastiera
        if (mc.options.keyUp.isDown())    move = move.add(forward);
        if (mc.options.keyDown.isDown())  move = move.add(forward.reverse());
        if (mc.options.keyLeft.isDown())  move = move.add(right.reverse());
        if (mc.options.keyRight.isDown()) move = move.add(right);
        if (mc.options.keyJump.isDown())  move = move.add(up);
        if (mc.options.keyShift.isDown()) move = move.add(up.reverse());

        // Applica Movimento
        if (move.lengthSqr() > 0) {
            move = move.normalize().scale(distance);
            BuilderState.freeCamEntity.move(net.minecraft.world.entity.MoverType.SELF, move);
        }

        // Resetta la velocità fisica
        BuilderState.freeCamEntity.setDeltaMovement(Vec3.ZERO);
    }

    // --- BLOCCA IL PLAYER VERO ---
    @SubscribeEvent
    public static void onInputUpdate(MovementInputUpdateEvent event) {
        if (BuilderState.isFreeCamActive) {
            // Annulla tutti gli input di movimento inviati al server dal player vero
            event.getInput().forwardImpulse = 0;
            event.getInput().leftImpulse = 0;
            event.getInput().jumping = false;
            event.getInput().shiftKeyDown = false;
        }
    }
}