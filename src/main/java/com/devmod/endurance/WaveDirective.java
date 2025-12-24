package com.devmod.endurance;

/**
 * Risk/Reward directive applied to the next wave.
 * Directives change mechanics (spawns/objectives) and reward scaling.
 */
public record WaveDirective(
    String id,
    String name,
    String description,
    float rewardMultiplier,
    float spawnCountMultiplier,
    float spawnIntervalMultiplier,
    @javax.annotation.Nullable WaveObjectiveState.Type objectiveOverride,
    @javax.annotation.Nullable SpawnAffix affixOverride
) {}
