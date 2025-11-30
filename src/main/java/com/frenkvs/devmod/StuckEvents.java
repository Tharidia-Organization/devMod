package com.frenkvs.devmod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber(modid = "devmod", bus = EventBusSubscriber.Bus.GAME)
public class StuckEvents {

    private static final Map<Integer, StuckTracker> stuckMap = new HashMap<>();

    private static class StuckTracker {
        BlockPos lastPos;
        int stuckTicks;
        int pathDisplayCooldown = 0; // Per non disegnare il path 20 volte al secondo (laggherebbe)

        public StuckTracker(BlockPos pos) {
            this.lastPos = pos;
            this.stuckTicks = 0;
        }
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        // Se la funzione è spenta nel config, non fare nulla e risparmia calcoli
        if (!ModConfig.enableStuckDebug && !ModConfig.showMobPath) return;

        if (event.getEntity().level().isClientSide || !(event.getEntity() instanceof Mob mob)) return;

        PathNavigation nav = mob.getNavigation();
        ServerLevel level = (ServerLevel) mob.level();

        // --- 1. VISUALIZZAZIONE PATH (Se attiva) ---
        if (ModConfig.showMobPath) {
            // Se ha un percorso e non è finito
            if (nav.getPath() != null && !nav.isDone()) {
                StuckTracker tracker = stuckMap.computeIfAbsent(mob.getId(), id -> new StuckTracker(mob.blockPosition()));

                // Disegniamo il path ogni 10 tick (0.5 secondi) per evitare troppo lag
                if (tracker.pathDisplayCooldown <= 0) {
                    showPathParticles(level, nav.getPath());
                    tracker.pathDisplayCooldown = 10;
                } else {
                    tracker.pathDisplayCooldown--;
                }
            }
        }

        // --- 2. LOGICA STUCK (Se attiva) ---
        if (ModConfig.enableStuckDebug) {
            // Se non ha path, puliamo e usciamo
            if (nav.isDone() || nav.getPath() == null) {
                stuckMap.remove(mob.getId());
                return;
            }

            StuckTracker tracker = stuckMap.computeIfAbsent(mob.getId(), id -> new StuckTracker(mob.blockPosition()));
            BlockPos currentPos = mob.blockPosition();

            // Confronto posizione
            if (currentPos.equals(tracker.lastPos)) {
                tracker.stuckTicks++;

                // Convertiamo i secondi del menu in Tick (x20)
                int thresholdTicks = ModConfig.stuckThresholdSeconds * 20;

                // SE È BLOCCATO
                if (tracker.stuckTicks > thresholdTicks) {

                    // A) Effetto Visivo HEATMAP (Fiamme)
                    level.sendParticles(ParticleTypes.FLAME,
                            currentPos.getX() + 0.5, currentPos.getY() + 0.2, currentPos.getZ() + 0.5,
                            5, 0.2, 0.2, 0.2, 0.01
                    );

                    // B) Debug in Chat (Se attivo nel menu) - Solo una volta ogni 2 secondi per non spammare
                    if (ModConfig.showStuckChat && tracker.stuckTicks % 40 == 0) {
                        String msg = "§c[DEBUG] Stuck: " + mob.getName().getString() +
                                " @ " + currentPos.toShortString();

                        // Manda il messaggio a tutti i player vicini (raggio 50 blocchi)
                        for (ServerPlayer player : level.players()) {
                            if (player.distanceToSqr(mob) < 2500) { // 50^2
                                player.displayClientMessage(Component.literal(msg), false);
                            }
                        }
                    }
                }
            } else {
                // Si è mosso
                tracker.lastPos = currentPos;
                tracker.stuckTicks = 0;
            }
        }
    }

    // Metodo helper per disegnare il percorso con particelle verdi/blu
    private static void showPathParticles(ServerLevel level, Path path) {
        if (path == null) return;

        // Disegna particelle su ogni "nodo" (punto di svolta) del percorso
        for (int i = 0; i < path.getNodeCount(); i++) {
            net.minecraft.world.level.pathfinder.Node node = path.getNode(i);

            // Usiamo particelle "HAPPY_VILLAGER" (verdi/smeraldo) perché si vedono bene
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    node.x + 0.5, node.y + 0.5, node.z + 0.5,
                    1, 0, 0, 0, 0 // Quantità 1, velocità 0
            );
        }

        // Evidenzia la destinazione finale con particelle diverse (es. SOUL_FIRE_FLAME)
        if (path.getEndNode() != null) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    path.getEndNode().x + 0.5, path.getEndNode().y + 1.0, path.getEndNode().z + 0.5,
                    3, 0, 0, 0, 0.05
            );
        }
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Mob) {
            stuckMap.remove(event.getEntity().getId());
        }
    }
}