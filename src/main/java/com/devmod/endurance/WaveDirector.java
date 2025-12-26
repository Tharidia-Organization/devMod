package com.devmod.endurance;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.core.BlockPos;

import com.devmod.arena.api.ArenaHandle;

public final class WaveDirector {
    public static final WaveDirector INSTANCE = new WaveDirector();

    private static final int TICKS_PER_SECOND = 20;
    private static final int MIN_BATCHES = 2;
    private static final int MAX_BATCHES_EARLY = 3;
    private static final int MAX_BATCHES_LATE = 5;

    private final Random random = new Random();
    private final WaveDirective steadyDirective = new WaveDirective(
        "steady",
        "Steady Pace",
        "Standard wave. No extra risks.",
        1.0f,
        1.0f,
        1.0f,
        null,
        null
    );
    private final List<WaveDirective> directivePool = List.of(
        new WaveDirective(
            "blitz",
            "Blitz Rush",
            "Faster spawns and more pressure.",
            1.25f,
            1.15f,
            0.70f,
            null,
            null
        ),
        new WaveDirective(
            "brute_force",
            "Brute Force",
            "Brutes lead the wave.",
            1.20f,
            1.10f,
            1.0f,
            null,
            SpawnAffix.BRUTE
        ),
        new WaveDirective(
            "sniper_lines",
            "Sniper Lines",
            "Ranged threats dominate.",
            1.20f,
            1.0f,
            1.0f,
            null,
            SpawnAffix.SNIPER
        ),
        new WaveDirective(
            "hold_the_line",
            "Hold the Line",
            "Capture the center under fire.",
            1.30f,
            1.10f,
            1.0f,
            WaveObjectiveState.Type.HOLD_ZONE,
            null
        ),
        new WaveDirective(
            "hunt_the_elite",
            "Hunt the Elite",
            "A marked elite must fall.",
            1.35f,
            0.90f,
            1.0f,
            WaveObjectiveState.Type.ELITE_HUNT,
            SpawnAffix.OBJECTIVE_ELITE
        ),
        new WaveDirective(
            "rush_down",
            "Rush Down",
            "Close-quarters swarm with speed.",
            1.20f,
            1.10f,
            0.85f,
            null,
            SpawnAffix.RUSH
        )
    );

    private WaveDirector() {}

    public enum SpawnRole {
        ANY,
        MELEE,
        RANGED,
        CORNER
    }

    public record SpawnBatch(
        int scheduledTick,
        int count,
        SpawnAffix affix,
        SpawnRole role,
        boolean objectiveTarget
    ) {}

    public record WavePlan(
        WaveObjectiveState objective,
        List<SpawnBatch> batches,
        int totalToSpawn
    ) {}

    public WavePlan planWave(EnduranceQuestManager.ActiveQuestSession session) {
        EnduranceQuest quest = session.getQuest();
        int baseCount = quest.getCurrentWaveMobCount(session.getPlayerCount(), session.getQuestType());
        return planWave(session, Math.max(1, baseCount));
    }

    public WavePlan planWave(EnduranceQuestManager.ActiveQuestSession session, int totalToSpawn) {
        return planWave(session, totalToSpawn, null);
    }

    public WavePlan planWave(EnduranceQuestManager.ActiveQuestSession session,
                             int totalToSpawn,
                             @javax.annotation.Nullable WaveDirective directive) {
        EnduranceQuest quest = session.getQuest();
        EnduranceQuestRegistry.MobQuestConfig mobConfig = quest.getMobConfig();
        int waveNumber = quest.getCurrentWave();

        int adjustedTotal = Math.max(1, totalToSpawn);
        if (directive != null && directive.spawnCountMultiplier() > 0f) {
            adjustedTotal = Math.max(1, Math.round(adjustedTotal * directive.spawnCountMultiplier()));
        }

        WaveObjectiveState objective = resolveObjective(session, adjustedTotal, directive);
        int objectiveEliteCount = objective.getType() == WaveObjectiveState.Type.ELITE_HUNT ? 1 : 0;

        int remaining = Math.max(0, adjustedTotal - objectiveEliteCount);
        int groupSize = mobConfig.canSpawnInGroups ? Math.max(1, mobConfig.groupSize) : 1;
        int batchSize = Math.max(1, groupSize);
        int maxBatches = waveNumber <= 2 ? MAX_BATCHES_EARLY : MAX_BATCHES_LATE;
        int plannedBatches = Math.min(maxBatches, Math.max(MIN_BATCHES, (int) Math.ceil(remaining / (double) batchSize)));

        int baseInterval = resolveBaseIntervalTicks(waveNumber);
        if (directive != null && directive.spawnIntervalMultiplier() > 0f) {
            baseInterval = Math.max(12, Math.round(baseInterval * directive.spawnIntervalMultiplier()));
        }
        int tick = 0;
        List<SpawnBatch> batches = new ArrayList<>();

        for (int i = 0; i < plannedBatches && remaining > 0; i++) {
            if (i > 0) {
                tick += baseInterval + random.nextInt(Math.max(1, baseInterval / 3));
            }
            int jitter = random.nextInt(2);
            int count = Math.min(remaining, Math.max(1, batchSize + jitter));
            remaining -= count;

            SpawnAffix affix = pickAffix(waveNumber, false);
            if (directive != null && directive.affixOverride() != null && random.nextFloat() < 0.6f) {
                affix = directive.affixOverride();
            }
            SpawnRole role = resolveSpawnRole(affix);
            batches.add(new SpawnBatch(tick, count, affix, role, false));
        }

        if (objectiveEliteCount > 0) {
            int eliteTick = Math.max(TICKS_PER_SECOND, baseInterval * Math.max(1, plannedBatches / 2));
            batches.add(new SpawnBatch(eliteTick, objectiveEliteCount, SpawnAffix.OBJECTIVE_ELITE,
                SpawnRole.CORNER, true));
        }

        return new WavePlan(objective, batches, adjustedTotal);
    }

    private WaveObjectiveState resolveObjective(EnduranceQuestManager.ActiveQuestSession session,
                                                int totalToSpawn,
                                                @javax.annotation.Nullable WaveDirective directive) {
        EnduranceQuest quest = session.getQuest();
        int waveNumber = quest.getCurrentWave();

        if (directive != null && directive.objectiveOverride() != null) {
            return resolveObjectiveByType(session, totalToSpawn, directive.objectiveOverride());
        }

        if (waveNumber <= 2) {
            return WaveObjectiveState.killAll(totalToSpawn);
        }

        if (waveNumber % 4 == 0) {
            BlockPos center = resolveArenaCenter(session);
            int radius = resolveZoneRadius(session);
            int seconds = 12 + Math.min(8, waveNumber / 2);
            return WaveObjectiveState.holdZone(center, radius, seconds);
        }

        if (waveNumber % 3 == 0) {
            int seconds = 15 + Math.min(10, waveNumber);
            return WaveObjectiveState.surviveSeconds(seconds);
        }

        if (waveNumber % 7 == 0) {
            return WaveObjectiveState.eliteHunt(1);
        }

        return WaveObjectiveState.killAll(totalToSpawn);
    }

    private WaveObjectiveState resolveObjectiveByType(EnduranceQuestManager.ActiveQuestSession session,
                                                      int totalToSpawn,
                                                      WaveObjectiveState.Type type) {
        EnduranceQuest quest = session.getQuest();
        int waveNumber = quest.getCurrentWave();
        switch (type) {
            case SURVIVE_TIME -> {
                int seconds = 15 + Math.min(10, waveNumber);
                return WaveObjectiveState.surviveSeconds(seconds);
            }
            case HOLD_ZONE -> {
                BlockPos center = resolveArenaCenter(session);
                int radius = resolveZoneRadius(session);
                int seconds = 12 + Math.min(8, waveNumber / 2);
                return WaveObjectiveState.holdZone(center, radius, seconds);
            }
            case ELITE_HUNT -> {
                return WaveObjectiveState.eliteHunt(1);
            }
            case KILL_ALL -> {
                return WaveObjectiveState.killAll(totalToSpawn);
            }
        }
        return WaveObjectiveState.killAll(totalToSpawn);
    }

    private int resolveBaseIntervalTicks(int waveNumber) {
        if (waveNumber <= 2) {
            return 50;
        }
        if (waveNumber <= 4) {
            return 40;
        }
        if (waveNumber <= 7) {
            return 32;
        }
        return 28;
    }

    private SpawnAffix pickAffix(int waveNumber, boolean objectiveElite) {
        if (objectiveElite) {
            return SpawnAffix.OBJECTIVE_ELITE;
        }
        if (waveNumber <= 2) {
            return SpawnAffix.BASE;
        }
        float eliteChance = getEliteRampChance(waveNumber);
        if (eliteChance > 0 && random.nextFloat() < eliteChance) {
            return SpawnAffix.ELITE;
        }
        float roll = random.nextFloat();
        if (roll < 0.25f) {
            return SpawnAffix.RUSH;
        }
        if (roll < 0.50f) {
            return SpawnAffix.BRUTE;
        }
        if (roll < 0.65f) {
            return SpawnAffix.SNIPER;
        }
        return SpawnAffix.BASE;
    }

    private float getEliteRampChance(int waveNumber) {
        if (waveNumber < 3) {
            return 0f;
        }
        if (waveNumber < 5) {
            return 0.10f;
        }
        if (waveNumber < 8) {
            return 0.20f;
        }
        return 0.35f;
    }

    private SpawnRole resolveSpawnRole(SpawnAffix affix) {
        return switch (affix) {
            case SNIPER -> SpawnRole.RANGED;
            case RUSH, BRUTE -> SpawnRole.MELEE;
            case ELITE, OBJECTIVE_ELITE -> SpawnRole.CORNER;
            default -> SpawnRole.ANY;
        };
    }

    private BlockPos resolveArenaCenter(EnduranceQuestManager.ActiveQuestSession session) {
        ArenaHandle handle = session.getArenaHandle();
        if (handle != null && handle.center() != null) {
            ArenaHandle.BlockPos center = handle.center();
            return new BlockPos(center.x(), center.y(), center.z());
        }
        ArenaContext arena = session.getArena();
        return arena != null ? arena.getCenter() : BlockPos.ZERO;
    }

    private int resolveZoneRadius(EnduranceQuestManager.ActiveQuestSession session) {
        ArenaHandle handle = session.getArenaHandle();
        if (handle != null && handle.bounds() != null) {
            ArenaHandle.AABB bounds = handle.bounds();
            int sizeX = Math.max(1, bounds.maxX() - bounds.minX());
            int sizeZ = Math.max(1, bounds.maxZ() - bounds.minZ());
            return Math.max(4, Math.min(8, Math.min(sizeX, sizeZ) / 4));
        }
        return 6;
    }

    public List<WaveDirective> rollDirectiveChoices(int nextWaveNumber) {
        List<WaveDirective> choices = new ArrayList<>();
        choices.add(steadyDirective);

        List<WaveDirective> candidates = new ArrayList<>();
        for (WaveDirective directive : directivePool) {
            if (isDirectiveAllowed(directive, nextWaveNumber)) {
                candidates.add(directive);
            }
        }
        java.util.Collections.shuffle(candidates, random);
        int desired = Math.min(3, Math.max(2, 1 + candidates.size()));
        for (int i = 0; i < candidates.size() && choices.size() < desired; i++) {
            choices.add(candidates.get(i));
        }

        return choices;
    }

    public @javax.annotation.Nullable WaveDirective findDirective(@javax.annotation.Nullable String directiveId) {
        if (directiveId == null || directiveId.isBlank()) {
            return null;
        }
        if (steadyDirective.id().equals(directiveId)) {
            return steadyDirective;
        }
        for (WaveDirective directive : directivePool) {
            if (directive != null && directive.id().equals(directiveId)) {
                return directive;
            }
        }
        return null;
    }

    private boolean isDirectiveAllowed(WaveDirective directive, int waveNumber) {
        if (directive == null) {
            return false;
        }
        if (waveNumber <= 2) {
            return directive.objectiveOverride() == null && directive.affixOverride() == null;
        }
        if (waveNumber <= 3 && directive.objectiveOverride() == WaveObjectiveState.Type.ELITE_HUNT) {
            return false;
        }
        return true;
    }
}
