package com.frenkvs.devmod.endurance;

import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Represents a single Endurance Quest instance for a specific mob type.
 * Each quest is a standalone challenge where the player fights waves of that mob.
 */
public class EnduranceQuest {

    private final UUID questId;
    private final ResourceLocation mobId;
    private final EnduranceQuestRegistry.MobQuestConfig mobConfig;

    // Quest progress
    private EnduranceQuestState state = EnduranceQuestState.AVAILABLE;
    private int currentWave = 0;
    private int totalWaves = 10; // Default, can be configured
    private boolean endlessMode = false;

    // Session stats (reset each attempt)
    private int mobsKilledThisSession = 0;
    private int totalDamageDealtThisSession = 0;
    private int totalDamageTakenThisSession = 0;
    private long sessionStartTime = 0;
    private long sessionEndTime = 0;
    private int deathsThisSession = 0;
    private int pointsEarnedThisSession = 0;

    // Persistent best records
    private int highestWaveReached = 0;
    private int bestSessionPoints = 0;
    private long fastestCompletionTime = Long.MAX_VALUE; // milliseconds
    private int totalCompletions = 0;
    private int totalAttempts = 0;

    // Boss wave tracking
    private int bossWavesCompleted = 0;

    // Arena reference
    private UUID arenaId;

    public EnduranceQuest(EnduranceQuestRegistry.MobQuestConfig mobConfig) {
        this.questId = UUID.randomUUID();
        this.mobId = mobConfig.mobId;
        this.mobConfig = mobConfig;
    }

    // ========== Quest Lifecycle ==========

    /**
     * Start a new quest attempt.
     */
    public void start(UUID arenaId) {
        if (state != EnduranceQuestState.AVAILABLE && state != EnduranceQuestState.COOLDOWN) {
            throw new IllegalStateException("Quest cannot be started in state: " + state);
        }

        this.arenaId = arenaId;
        this.state = EnduranceQuestState.IN_PROGRESS;
        this.currentWave = 1;

        // Reset session stats
        this.mobsKilledThisSession = 0;
        this.totalDamageDealtThisSession = 0;
        this.totalDamageTakenThisSession = 0;
        this.sessionStartTime = System.currentTimeMillis();
        this.sessionEndTime = 0;
        this.deathsThisSession = 0;
        this.pointsEarnedThisSession = 0;

        this.totalAttempts++;
    }

    /**
     * Called when a wave is completed.
     */
    public void completeWave() {
        if (state != EnduranceQuestState.IN_PROGRESS) return;

        // Award wave bonus points
        pointsEarnedThisSession += mobConfig.bonusPointsForWaveClear;

        // Update high score
        if (currentWave > highestWaveReached) {
            highestWaveReached = currentWave;
        }

        if (currentWave >= totalWaves && !endlessMode) {
            // Quest completed!
            complete();
        } else {
            // Wave complete - DON'T increment currentWave here!
            // It will be incremented in continueToNextWave()
            // This ensures the checkpoint screen shows the correct wave number
            state = EnduranceQuestState.WAVE_COMPLETE;
        }
    }

    /**
     * Continue to next wave after checkpoint.
     */
    public void continueToNextWave() {
        if (state != EnduranceQuestState.WAVE_COMPLETE) return;

        // NOW increment to the next wave number
        currentWave++;
        state = EnduranceQuestState.IN_PROGRESS;
    }

    /**
     * Quest completed successfully.
     */
    private void complete() {
        state = EnduranceQuestState.COMPLETED;
        sessionEndTime = System.currentTimeMillis();

        totalCompletions++;

        // Update best records
        long completionTime = sessionEndTime - sessionStartTime;
        if (completionTime < fastestCompletionTime) {
            fastestCompletionTime = completionTime;
        }

        if (pointsEarnedThisSession > bestSessionPoints) {
            bestSessionPoints = pointsEarnedThisSession;
        }
    }

    /**
     * Quest failed (player died or abandoned).
     */
    public void fail(boolean abandoned) {
        if (state == EnduranceQuestState.COMPLETED || state == EnduranceQuestState.FAILED) return;

        state = EnduranceQuestState.FAILED;
        sessionEndTime = System.currentTimeMillis();

        if (!abandoned) {
            deathsThisSession++;
        }

        // Still update best points if applicable
        if (pointsEarnedThisSession > bestSessionPoints) {
            bestSessionPoints = pointsEarnedThisSession;
        }
    }

    /**
     * Reset quest to available state.
     */
    public void reset() {
        state = EnduranceQuestState.AVAILABLE;
        arenaId = null;
    }

    /**
     * Continue quest after player death (with penalty).
     * Unlike reset+start, this continues from current wave.
     */
    public void continueAfterDeath() {
        if (state != EnduranceQuestState.FAILED) return;

        // Apply death penalty - lose some points
        pointsEarnedThisSession = Math.max(0, pointsEarnedThisSession - 100);

        // Continue from current wave
        state = EnduranceQuestState.IN_PROGRESS;

        deathsThisSession++;
    }

    // ========== Combat Events ==========

    /**
     * Record a mob kill.
     */
    public void recordKill() {
        mobsKilledThisSession++;
        pointsEarnedThisSession += mobConfig.pointsPerKill;
    }

    /**
     * Record damage dealt by player.
     */
    public void recordDamageDealt(float damage) {
        totalDamageDealtThisSession += (int) damage;
    }

    /**
     * Record damage taken by player.
     */
    public void recordDamageTaken(float damage) {
        totalDamageTakenThisSession += (int) damage;
    }

    // ========== Getters ==========

    public UUID getQuestId() { return questId; }
    public ResourceLocation getMobId() { return mobId; }
    public EnduranceQuestRegistry.MobQuestConfig getMobConfig() { return mobConfig; }
    public EnduranceQuestState getState() { return state; }
    public int getCurrentWave() { return currentWave; }
    public int getTotalWaves() { return totalWaves; }
    public boolean isEndlessMode() { return endlessMode; }
    public UUID getArenaId() { return arenaId; }

    // Session stats
    public int getMobsKilledThisSession() { return mobsKilledThisSession; }
    public int getTotalDamageDealtThisSession() { return totalDamageDealtThisSession; }
    public int getTotalDamageTakenThisSession() { return totalDamageTakenThisSession; }
    public long getSessionDuration() {
        if (sessionStartTime == 0) return 0;
        long endTime = sessionEndTime > 0 ? sessionEndTime : System.currentTimeMillis();
        return endTime - sessionStartTime;
    }
    public int getDeathsThisSession() { return deathsThisSession; }
    public int getPointsEarnedThisSession() { return pointsEarnedThisSession; }

    // Best records
    public int getHighestWaveReached() { return highestWaveReached; }
    public int getBestSessionPoints() { return bestSessionPoints; }
    public long getFastestCompletionTime() { return fastestCompletionTime; }
    public int getTotalCompletions() { return totalCompletions; }
    public int getTotalAttempts() { return totalAttempts; }
    public int getBossWavesCompleted() { return bossWavesCompleted; }

    // Alias for compatibility
    public float getDamageTakenThisSession() { return totalDamageTakenThisSession; }

    // ========== Setters ==========

    public void setTotalWaves(int totalWaves) {
        this.totalWaves = totalWaves;
    }

    public void setEndlessMode(boolean endlessMode) {
        this.endlessMode = endlessMode;
    }

    public void incrementBossWavesCompleted() {
        this.bossWavesCompleted++;
    }

    /**
     * Get mob count for current wave.
     */
    public int getCurrentWaveMobCount() {
        return mobConfig.getMobCountForWave(currentWave);
    }

    /**
     * Get display name for this quest.
     */
    public String getDisplayName() {
        return mobConfig.displayName + " Endurance";
    }

    /**
     * Get difficulty tier.
     */
    public EnduranceQuestRegistry.MobTier getTier() {
        return mobConfig.tier;
    }

    /**
     * Check if quest is currently active.
     */
    public boolean isActive() {
        return state == EnduranceQuestState.IN_PROGRESS || state == EnduranceQuestState.WAVE_COMPLETE;
    }

    /**
     * Get completion percentage for current session.
     */
    public float getCompletionPercentage() {
        if (endlessMode) return 0;
        return (float) (currentWave - 1) / totalWaves * 100;
    }

    @Override
    public String toString() {
        return String.format("EnduranceQuest[%s, State=%s, Wave=%d/%d, Points=%d]",
            mobConfig.displayName, state, currentWave, totalWaves, pointsEarnedThisSession);
    }
}
