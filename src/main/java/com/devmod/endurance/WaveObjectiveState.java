package com.devmod.endurance;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class WaveObjectiveState {
    public enum Type {
        KILL_ALL,
        SURVIVE_TIME,
        HOLD_ZONE,
        ELITE_HUNT
    }

    private static final int TICKS_PER_SECOND = 20;

    private final Type type;
    private final String title;
    private final String description;

    private final int targetTicks;
    private final AtomicInteger targetCount;
    private final AtomicInteger progressTicks = new AtomicInteger();
    private final AtomicInteger progressCount = new AtomicInteger();

    private final AtomicBoolean complete = new AtomicBoolean(false);
    private final AtomicBoolean failed = new AtomicBoolean(false);

    private BlockPos zoneCenter;
    private int zoneRadius;
    private final Set<UUID> objectiveTargets = ConcurrentHashMap.newKeySet();

    private WaveObjectiveState(Type type,
                               String title,
                               String description,
                               int targetTicks,
                               int targetCount) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.targetTicks = Math.max(0, targetTicks);
        this.targetCount = new AtomicInteger(Math.max(0, targetCount));
    }

    public static WaveObjectiveState killAll(int totalMobs) {
        return new WaveObjectiveState(
            Type.KILL_ALL,
            "Eliminate the wave",
            "Defeat all enemies",
            0,
            totalMobs
        );
    }

    public static WaveObjectiveState surviveSeconds(int seconds) {
        int ticks = Math.max(1, seconds) * TICKS_PER_SECOND;
        return new WaveObjectiveState(
            Type.SURVIVE_TIME,
            "Survive the onslaught",
            "Hold out for " + seconds + "s",
            ticks,
            0
        );
    }

    public static WaveObjectiveState holdZone(BlockPos center, int radius, int seconds) {
        int ticks = Math.max(1, seconds) * TICKS_PER_SECOND;
        WaveObjectiveState state = new WaveObjectiveState(
            Type.HOLD_ZONE,
            "Hold the center",
            "Stay in the zone for " + seconds + "s",
            ticks,
            0
        );
        state.zoneCenter = center;
        state.zoneRadius = Math.max(2, radius);
        return state;
    }

    public static WaveObjectiveState eliteHunt(int eliteCount) {
        return new WaveObjectiveState(
            Type.ELITE_HUNT,
            "Hunt the elite",
            "Eliminate elite targets",
            0,
            Math.max(1, eliteCount)
        );
    }

    public Type getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public boolean isComplete() {
        return complete.get();
    }

    public boolean isFailed() {
        return failed.get();
    }

    public boolean shouldRespawnExternalDeaths() {
        return type == Type.KILL_ALL || type == Type.ELITE_HUNT;
    }

    public int getProgressForUi() {
        return type == Type.SURVIVE_TIME || type == Type.HOLD_ZONE
            ? (int) Math.ceil(progressTicks.get() / (double) TICKS_PER_SECOND)
            : progressCount.get();
    }

    public int getTargetForUi() {
        return type == Type.SURVIVE_TIME || type == Type.HOLD_ZONE
            ? (int) Math.ceil(targetTicks / (double) TICKS_PER_SECOND)
            : targetCount.get();
    }

    public Set<UUID> getObjectiveTargets() {
        return Collections.unmodifiableSet(objectiveTargets);
    }

    public void registerObjectiveTarget(UUID mobId) {
        if (mobId != null && type == Type.ELITE_HUNT) {
            objectiveTargets.add(mobId);
        }
    }

    public void recordObjectiveKill(UUID mobId) {
        if (mobId == null || complete.get() || failed.get()) {
            return;
        }
        if (type == Type.ELITE_HUNT && objectiveTargets.remove(mobId)) {
            int newProgress = progressCount.incrementAndGet();
            if (newProgress >= targetCount.get()) {
                complete.set(true);
            }
        }
    }

    public void recordKill() {
        if (complete.get() || failed.get()) {
            return;
        }
        if (type == Type.KILL_ALL) {
            int newProgress = progressCount.incrementAndGet();
            if (newProgress >= targetCount.get()) {
                complete.set(true);
            }
        }
    }

    public void adjustKillTarget(int newTarget) {
        if (type == Type.KILL_ALL) {
            int adjustedTarget = Math.max(0, newTarget);
            targetCount.set(adjustedTarget);
            if (adjustedTarget == 0 || progressCount.get() >= adjustedTarget) {
                complete.set(true);
            }
        }
    }

    public void tick(Player player) {
        if (complete.get() || failed.get()) {
            return;
        }
        switch (type) {
            case SURVIVE_TIME -> {
                int updatedTicks = progressTicks.incrementAndGet();
                if (updatedTicks >= targetTicks) {
                    complete.set(true);
                }
            }
            case HOLD_ZONE -> {
                if (player == null || zoneCenter == null) {
                    return;
                }
                Vec3 pos = player.position();
                double dx = pos.x - (zoneCenter.getX() + 0.5);
                double dz = pos.z - (zoneCenter.getZ() + 0.5);
                double distSq = dx * dx + dz * dz;
                double radiusSq = (double) zoneRadius * zoneRadius;
                int updatedTicks = progressTicks.updateAndGet(currentTicks -> {
                    if (distSq <= radiusSq) {
                        return currentTicks + 1;
                    }
                    return Math.max(0, currentTicks - 2);
                });
                if (updatedTicks >= targetTicks) {
                    complete.set(true);
                }
            }
            case KILL_ALL, ELITE_HUNT -> {
                // No periodic tick needed.
            }
        }
    }
}
