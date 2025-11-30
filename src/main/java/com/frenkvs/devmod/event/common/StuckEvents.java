package com.frenkvs.devmod.event.common;

import com.frenkvs.devmod.config.ModConfig;
import com.frenkvs.devmod.network.payload.PathRenderPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

                // Inviamo i dati del path ai client ogni 10 tick (0.5 secondi)
                if (tracker.pathDisplayCooldown <= 0) {
                    sendPathToClients(level, mob, nav.getPath(), null);
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

                    // A) Invia visualizzazione stuck ai client
                    sendPathToClients(level, mob, nav.getPath(), currentPos);

                    // B) Debug in Chat (Se attivo nel menu) - Solo una volta ogni 2 secondi per non spammare
                    if (ModConfig.showStuckChat && tracker.stuckTicks % 40 == 0) {
                        // Crea messaggio cliccabile per teleportarsi
                        Component coordText = Component.literal(currentPos.toShortString())
                                .setStyle(Style.EMPTY
                                        .withColor(0xFFFF55)
                                        .withUnderlined(true)
                                        .withClickEvent(new ClickEvent(
                                                ClickEvent.Action.RUN_COMMAND,
                                                "/tp @s " + currentPos.getX() + " " + currentPos.getY() + " " + currentPos.getZ()
                                        ))
                                        .withHoverEvent(new HoverEvent(
                                                HoverEvent.Action.SHOW_TEXT,
                                                Component.literal("§aClick to teleport")
                                        ))
                                );

                        Component fullMsg = Component.literal("§c[DEBUG] Stuck: ")
                                .append(Component.literal(mob.getName().getString()).withColor(0xFF5555))
                                .append(Component.literal(" @ "))
                                .append(coordText);

                        // Manda il messaggio a tutti i player vicini (raggio 50 blocchi)
                        for (ServerPlayer player : level.players()) {
                            if (player.distanceToSqr(mob) < 2500) { // 50^2
                                player.displayClientMessage(fullMsg, false);
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

    // Metodo helper per inviare i dati del path ai client per il rendering
    private static void sendPathToClients(ServerLevel level, Mob mob, Path path, BlockPos stuckPos) {
        if (path == null) return;

        // Raccogli tutti i nodi del path
        List<BlockPos> pathNodes = new ArrayList<>();
        for (int i = 0; i < path.getNodeCount(); i++) {
            net.minecraft.world.level.pathfinder.Node node = path.getNode(i);
            pathNodes.add(new BlockPos(node.x, node.y, node.z));
        }

        // Ottieni il nodo finale
        BlockPos endNode = null;
        if (path.getEndNode() != null) {
            endNode = new BlockPos(path.getEndNode().x, path.getEndNode().y, path.getEndNode().z);
        }

        // Debug: verifica che abbiamo dati da inviare
        if (pathNodes.isEmpty() && endNode == null && stuckPos == null) {
            return;
        }

        // Crea e invia il payload a tutti i player vicini
        PathRenderPayload payload = new PathRenderPayload(pathNodes, endNode, stuckPos, mob.getId());
        
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(mob) < 10000) { // 100 blocchi di raggio
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Mob) {
            stuckMap.remove(event.getEntity().getId());
        }
    }
}