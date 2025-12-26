package com.devmod.client.attributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import com.devmod.attributes.AttributeLogEntry;

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

    // === Configuration ===
    private static final double MAX_TRACKING_DISTANCE = 64.0;
    private static final int MAX_TRACKED_ENTITIES = 8;
    private static final int LOG_HISTORY_SIZE = 50;

    // === PERFORMANCE OPTIMIZATION ===
    private static final int ENTITY_SCAN_INTERVAL_TICKS = 10; // Scan every 10 ticks (~500ms)
    private int ticksSinceLastScan = 0;

    // === State ===
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

    // === Update Logic (called every tick) ===

    public void tick() {
        if (!enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Store in local finals after null check for null-safety analysis
        final var player = Objects.requireNonNull(mc.player, "player");
        final var level = Objects.requireNonNull(mc.level, "level");
        Vec3 playerPos = Objects.requireNonNull(player.position(), "player position");

        // === PERFORMANCE: Scan for new entities only every N ticks ===
        ticksSinceLastScan++;
        boolean shouldScanForNew = ticksSinceLastScan >= ENTITY_SCAN_INTERVAL_TICKS;
        if (shouldScanForNew) {
            ticksSinceLastScan = 0;
        }

        // Update tracked entities (full scan only periodically)
        updateTrackedEntities(level, player, playerPos, shouldScanForNew);

        // Determine primary target (closest or under crosshair)
        updatePrimaryTarget(mc, player);

        // Clean old logs
        cleanOldLogs();
    }

    private void updateTrackedEntities(net.minecraft.client.multiplayer.ClientLevel level,
                                        net.minecraft.client.player.LocalPlayer player,
                                        @Nonnull Vec3 playerPos, boolean scanForNew) {
        // Remove entities that are no longer valid or too far
        trackedEntities.removeIf(tracked -> {
            LivingEntity entity = tracked.getEntity();
            if (entity == null || !entity.isAlive()) return true;
            return entity.distanceToSqr(playerPos) > MAX_TRACKING_DISTANCE * MAX_TRACKING_DISTANCE;
        });

        // Update data of remaining entities
        for (TrackedEntity tracked : trackedEntities) {
            tracked.update();
        }

        // === PERFORMANCE: Search for new entities only when requested ===
        if (!scanForNew) return;

        // Search for new entities to track (nearby mobs)
        if (trackedEntities.size() < MAX_TRACKED_ENTITIES) {
            for (var entity : level.entitiesForRendering()) {
                if (!(entity instanceof Mob mob)) continue;
                if (mob.distanceToSqr(playerPos) > MAX_TRACKING_DISTANCE * MAX_TRACKING_DISTANCE) continue;

                // Check if already tracked
                boolean alreadyTracked = trackedEntities.stream()
                    .anyMatch(t -> t.getEntityId() == mob.getId());

                if (!alreadyTracked) {
                    trackedEntities.add(new TrackedEntity(mob));

                    // Log event
                    addLog(AttributeLogEntry.Type.ENTITY_DETECTED,
                           "Detected: " + mob.getName().getString(),
                           mob.position());

                    if (trackedEntities.size() >= MAX_TRACKED_ENTITIES) break;
                }
            }
        }
    }

    private void updatePrimaryTarget(@Nonnull Minecraft mc, @Nonnull net.minecraft.client.player.LocalPlayer player) {
        // First try: entity under crosshair
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

        // Fallback: closest entity
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

        // Limit size
        while (logHistory.size() > LOG_HISTORY_SIZE) {
            logHistory.remove(logHistory.size() - 1);
        }
    }

    private void cleanOldLogs() {
        long now = System.currentTimeMillis();
        logHistory.removeIf(log -> now - log.timestamp() > 30000); // 30 seconds max
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
