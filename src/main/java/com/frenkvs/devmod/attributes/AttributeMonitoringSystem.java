package com.frenkvs.devmod.attributes;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Attribute Monitoring System - Controller principale.
 *
 * Sistema ispirato all'immagine di riferimento che mostra:
 * - Pannello HUD laterale con attributi del target
 * - Raggi 3D di Line of Sight verso le entità
 * - Log history degli eventi
 * - Tracking in tempo reale
 *
 * Attivazione: Tasto U (Utility)
 */
public class AttributeMonitoringSystem {
    public static final AttributeMonitoringSystem INSTANCE = new AttributeMonitoringSystem();

    // === Configurazione ===
    private static final double MAX_TRACKING_DISTANCE = 64.0;
    private static final int MAX_TRACKED_ENTITIES = 8;
    private static final int LOG_HISTORY_SIZE = 50;

    // === PERFORMANCE OPTIMIZATION ===
    private static final int ENTITY_SCAN_INTERVAL_TICKS = 10; // Scan ogni 10 ticks (~500ms)
    private int ticksSinceLastScan = 0;

    // === Stato ===
    private boolean enabled = false;
    private final List<TrackedEntity> trackedEntities = new ArrayList<>();
    private final List<AttributeLogEntry> logHistory = new ArrayList<>();
    private TrackedEntity primaryTarget = null;

    private AttributeMonitoringSystem() {}

    // === Toggle/Enable ===

    public void toggle() {
        enabled = !enabled;
        if (!enabled) {
            clear();
        }
    }

    public void setEnabled(boolean value) {
        enabled = value;
        if (!enabled) {
            clear();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    // === Update Logic (chiamato ogni tick) ===

    @SuppressWarnings("null") // player/level are checked for null before use
    public void tick() {
        if (!enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Store in local finals after null check for null-safety analysis
        final var player = mc.player;
        final var level = mc.level;
        Vec3 playerPos = player.position();

        // === PERFORMANCE: Scan per nuove entità solo ogni N ticks ===
        ticksSinceLastScan++;
        boolean shouldScanForNew = ticksSinceLastScan >= ENTITY_SCAN_INTERVAL_TICKS;
        if (shouldScanForNew) {
            ticksSinceLastScan = 0;
        }

        // Aggiorna le entità tracciate (scan completo solo periodicamente)
        updateTrackedEntities(level, player, playerPos, shouldScanForNew);

        // Determina il target primario (più vicino o sotto mirino)
        updatePrimaryTarget(mc, player);

        // Pulisci log vecchi
        cleanOldLogs();
    }

    private void updateTrackedEntities(net.minecraft.client.multiplayer.ClientLevel level,
                                        net.minecraft.client.player.LocalPlayer player,
                                        @Nonnull Vec3 playerPos, boolean scanForNew) {
        // Rimuovi entità non più valide o troppo lontane
        trackedEntities.removeIf(tracked -> {
            LivingEntity entity = tracked.getEntity();
            if (entity == null || !entity.isAlive()) return true;
            return entity.distanceToSqr(playerPos) > MAX_TRACKING_DISTANCE * MAX_TRACKING_DISTANCE;
        });

        // Aggiorna i dati delle entità rimanenti
        for (TrackedEntity tracked : trackedEntities) {
            tracked.update();
        }

        // === PERFORMANCE: Cerca nuove entità solo quando richiesto ===
        if (!scanForNew) return;

        // Cerca nuove entità da tracciare (mob vicini)
        if (trackedEntities.size() < MAX_TRACKED_ENTITIES) {
            for (var entity : level.entitiesForRendering()) {
                if (!(entity instanceof Mob mob)) continue;
                if (mob.distanceToSqr(playerPos) > MAX_TRACKING_DISTANCE * MAX_TRACKING_DISTANCE) continue;

                // Controlla se già tracciata
                boolean alreadyTracked = trackedEntities.stream()
                    .anyMatch(t -> t.getEntityId() == mob.getId());

                if (!alreadyTracked) {
                    trackedEntities.add(new TrackedEntity(mob));

                    // Log evento
                    addLog(AttributeLogEntry.Type.ENTITY_DETECTED,
                           "Detected: " + mob.getName().getString(),
                           mob.position());

                    if (trackedEntities.size() >= MAX_TRACKED_ENTITIES) break;
                }
            }
        }
    }

    @SuppressWarnings("null") // player is guaranteed non-null when called from tick()
    private void updatePrimaryTarget(Minecraft mc, net.minecraft.client.player.LocalPlayer player) {
        // Prima prova: entità sotto il mirino
        if (mc.crosshairPickEntity instanceof LivingEntity crosshairTarget) {
            for (TrackedEntity tracked : trackedEntities) {
                if (tracked.getEntityId() == crosshairTarget.getId()) {
                    if (primaryTarget != tracked) {
                        primaryTarget = tracked;
                        addLog(AttributeLogEntry.Type.TARGET_CHANGED,
                               "Target: " + crosshairTarget.getName().getString(),
                               crosshairTarget.position());
                    }
                    return;
                }
            }
        }

        // Fallback: entità più vicina
        if (!trackedEntities.isEmpty()) {
            TrackedEntity closest = null;
            double closestDist = Double.MAX_VALUE;

            for (TrackedEntity tracked : trackedEntities) {
                LivingEntity entity = tracked.getEntity();
                if (entity == null) continue;

                double dist = entity.distanceToSqr(player);
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = tracked;
                }
            }

            if (closest != null && primaryTarget != closest) {
                primaryTarget = closest;
            }
        }
    }

    // === Log System ===

    public void addLog(AttributeLogEntry.Type type, String message, @Nullable Vec3 position) {
        logHistory.add(0, new AttributeLogEntry(type, message, position));

        // Limita dimensione
        while (logHistory.size() > LOG_HISTORY_SIZE) {
            logHistory.remove(logHistory.size() - 1);
        }
    }

    private void cleanOldLogs() {
        long now = System.currentTimeMillis();
        logHistory.removeIf(log -> now - log.timestamp() > 30000); // 30 secondi max
    }

    // === Accessors ===

    public List<TrackedEntity> getTrackedEntities() {
        return trackedEntities;
    }

    @Nullable
    public TrackedEntity getPrimaryTarget() {
        return primaryTarget;
    }

    public List<AttributeLogEntry> getLogHistory() {
        return logHistory;
    }

    public int getTrackedCount() {
        return trackedEntities.size();
    }

    // === Clear ===

    public void clear() {
        trackedEntities.clear();
        logHistory.clear();
        primaryTarget = null;
    }
}
