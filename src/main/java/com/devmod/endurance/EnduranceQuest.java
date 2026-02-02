package com.devmod.endurance;

import java.util.UUID;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import com.devmod.endurance.EnduranceLogger.Phase;

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
        this.mobId = mobConfig.getMobId();
        this.mobConfig = mobConfig;
    }

    public EnduranceQuest(EnduranceQuestRegistry.MobQuestConfig mobConfig, UUID questId) {
        this.questId = questId != null ? questId : UUID.randomUUID();
        this.mobId = mobConfig.getMobId();
        this.mobConfig = mobConfig;
    }

    // ========== Quest Lifecycle ==========

    /**
     * Start a new quest attempt.
     */
    public void start(UUID arenaId) {
        EnduranceQuestState previousState = state;
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

        EnduranceLogger.phase(Phase.QUEST_START, (ServerPlayer) null, questId,
            "State: %s→%s, mob=%s, waves=%d, endless=%s, attempt=%d",
            previousState, state, mobId, totalWaves, endlessMode, totalAttempts);
    }

    /**
     * Called when a wave is completed.
     */
    public void completeWave() {
        if (state != EnduranceQuestState.IN_PROGRESS) return;

        // Award wave bonus points
        pointsEarnedThisSession += mobConfig.getBonusPointsForWaveClear();

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

            EnduranceLogger.phase(Phase.WAVE_COMPLETE, (ServerPlayer) null, questId,
                "State: IN_PROGRESS→WAVE_COMPLETE, wave=%d/%d, points=%d, kills=%d",
                currentWave, totalWaves, pointsEarnedThisSession, mobsKilledThisSession);
        }
    }

    /**
     * Continue to next wave after checkpoint.
     */
    public void continueToNextWave() {
        if (state != EnduranceQuestState.WAVE_COMPLETE) return;

        int previousWave = currentWave;
        // NOW increment to the next wave number
        currentWave++;
        state = EnduranceQuestState.IN_PROGRESS;

        EnduranceLogger.phase(Phase.CHECKPOINT, (ServerPlayer) null, questId,
            "State: WAVE_COMPLETE→IN_PROGRESS, wave=%d→%d/%d",
            previousWave, currentWave, totalWaves);
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

        EnduranceLogger.phase(Phase.QUEST_COMPLETE, (ServerPlayer) null, questId,
            "State: IN_PROGRESS→COMPLETED, duration=%dms, points=%d, kills=%d, deaths=%d, completions=%d",
            completionTime, pointsEarnedThisSession, mobsKilledThisSession, deathsThisSession, totalCompletions);
    }

    /**
     * Quest failed (player died or abandoned).
     */
    public void fail(boolean abandoned) {
        if (state == EnduranceQuestState.COMPLETED || state == EnduranceQuestState.FAILED) return;

        EnduranceQuestState previousState = state;
        state = EnduranceQuestState.FAILED;
        sessionEndTime = System.currentTimeMillis();

        if (!abandoned) {
            deathsThisSession++;
        }

        // Still update best points if applicable
        if (pointsEarnedThisSession > bestSessionPoints) {
            bestSessionPoints = pointsEarnedThisSession;
        }

        long duration = sessionEndTime - sessionStartTime;
        EnduranceLogger.phase(abandoned ? Phase.QUEST_ABANDON : Phase.QUEST_FAIL, (ServerPlayer) null, questId,
            "State: %s→FAILED, wave=%d/%d, duration=%dms, points=%d, kills=%d, deaths=%d, abandoned=%s",
            previousState, currentWave, totalWaves, duration, pointsEarnedThisSession,
            mobsKilledThisSession, deathsThisSession, abandoned);
    }

    /**
     * Reset quest to available state.
     */
    public void reset() {
        EnduranceQuestState previousState = state;
        state = EnduranceQuestState.AVAILABLE;
        arenaId = null;

        EnduranceLogger.phase(Phase.CLEANUP, (ServerPlayer) null, questId,
            "State: %s→AVAILABLE (reset)", previousState);
    }

    /**
     * Continue quest after player death (with penalty).
     * Unlike reset+start, this continues from current wave.
     */
    public void continueAfterDeath() {
        if (state != EnduranceQuestState.FAILED) return;

        int pointsBefore = pointsEarnedThisSession;
        // Apply death penalty - lose some points
        pointsEarnedThisSession = Math.max(0, pointsEarnedThisSession - 100);

        // Continue from current wave
        state = EnduranceQuestState.IN_PROGRESS;

        deathsThisSession++;

        EnduranceLogger.phase(Phase.PLAYER_RESPAWN, (ServerPlayer) null, questId,
            "State: FAILED→IN_PROGRESS, wave=%d, deaths=%d, pointPenalty=%d→%d",
            currentWave, deathsThisSession, pointsBefore, pointsEarnedThisSession);
    }

    // ========== Combat Events ==========

    /**
     * Record a mob kill.
     */
    public void recordKill() {
        mobsKilledThisSession++;
        pointsEarnedThisSession += mobConfig.getPointsPerKill();
    }

    /**
     * Record damage dealt by player.
     * Caps damage to 10000 to prevent Float.MAX_VALUE from /kill polluting stats.
     */
    public void recordDamageDealt(float damage) {
        float cappedDamage = Math.min(damage, 10000f);
        totalDamageDealtThisSession += (int) cappedDamage;
    }

    /**
     * Record damage taken by player.
     * Caps damage to 10000 to prevent Float.MAX_VALUE from /kill polluting stats.
     */
    public void recordDamageTaken(float damage) {
        float cappedDamage = Math.min(damage, 10000f);
        totalDamageTakenThisSession += (int) cappedDamage;
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
     * Get mob count for current wave (single player).
     */
    public int getCurrentWaveMobCount() {
        return mobConfig.getMobCountForWave(currentWave);
    }

    /**
     * Get mob count for current wave with player scaling.
     *
     * @param playerCount Number of players in the party
     * @param questType Quest type for difficulty multiplier
     * @return Scaled mob count
     */
    public int getCurrentWaveMobCount(int playerCount, QuestType questType) {
        return mobConfig.getMobCountForWave(currentWave, playerCount, questType);
    }

    /**
     * Get mob count for current wave with player scaling and session overrides.
     *
     * @param playerCount Number of players in the party
     * @param questType Quest type for difficulty multiplier
     * @param session Session for config overrides (nullable)
     * @return Scaled mob count
     */
    public int getCurrentWaveMobCount(int playerCount, QuestType questType,
                                      @javax.annotation.Nullable EnduranceQuestManager.ActiveQuestSession session) {
        return mobConfig.getMobCountForWave(currentWave, playerCount, questType, session);
    }

    /**
     * Get display name for this quest.
     */
    public String getDisplayName() {
        return mobConfig.getDisplayName() + " Endurance";
    }

    /**
     * Get difficulty tier.
     */
    public EnduranceQuestRegistry.MobTier getTier() {
        return mobConfig.getTier();
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
            mobConfig.getDisplayName(), state, currentWave, totalWaves, pointsEarnedThisSession);
    }
}
