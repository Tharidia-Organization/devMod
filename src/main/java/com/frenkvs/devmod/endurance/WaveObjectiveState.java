package com.frenkvs.devmod.endurance;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks the current wave objective state and progress.
 * Objectives are gameplay mechanics, not just UI text.
 */
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
    private int targetCount;
    private int progressTicks;
    private int progressCount;

    private boolean complete;
    private boolean failed;

    private BlockPos zoneCenter;
    private int zoneRadius;
    private final Set<UUID> objectiveTargets = new HashSet<>();

    private WaveObjectiveState(Type type,
                               String title,
                               String description,
                               int targetTicks,
                               int targetCount) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.targetTicks = Math.max(0, targetTicks);
        this.targetCount = Math.max(0, targetCount);
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
        return complete;
    }

    public boolean isFailed() {
        return failed;
    }

    public boolean shouldRespawnExternalDeaths() {
        return type == Type.KILL_ALL || type == Type.ELITE_HUNT;
    }

    public int getProgressForUi() {
        return type == Type.SURVIVE_TIME || type == Type.HOLD_ZONE
            ? (int) Math.ceil(progressTicks / (double) TICKS_PER_SECOND)
            : progressCount;
    }

    public int getTargetForUi() {
        return type == Type.SURVIVE_TIME || type == Type.HOLD_ZONE
            ? (int) Math.ceil(targetTicks / (double) TICKS_PER_SECOND)
            : targetCount;
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
        if (mobId == null || complete || failed) {
            return;
        }
        if (type == Type.ELITE_HUNT && objectiveTargets.remove(mobId)) {
            progressCount++;
            if (progressCount >= targetCount) {
                complete = true;
            }
        }
    }

    public void recordKill() {
        if (complete || failed) {
            return;
        }
        if (type == Type.KILL_ALL) {
            progressCount++;
            if (progressCount >= targetCount) {
                complete = true;
            }
        }
    }

    public void adjustKillTarget(int newTarget) {
        if (type == Type.KILL_ALL) {
            targetCount = Math.max(0, newTarget);
            if (targetCount == 0) {
                complete = true;
            } else if (progressCount >= targetCount) {
                complete = true;
            }
        }
    }

    public void tick(Player player) {
        if (complete || failed) {
            return;
        }
        switch (type) {
            case SURVIVE_TIME -> {
                progressTicks++;
                if (progressTicks >= targetTicks) {
                    complete = true;
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
                if (distSq <= radiusSq) {
                    progressTicks++;
                } else {
                    progressTicks = Math.max(0, progressTicks - 2);
                }
                if (progressTicks >= targetTicks) {
                    complete = true;
                }
            }
            case KILL_ALL, ELITE_HUNT -> {
                // No periodic tick needed.
            }
        }
    }
}
