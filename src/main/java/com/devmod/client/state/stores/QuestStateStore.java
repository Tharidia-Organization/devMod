package com.devmod.client.state.stores;

import java.util.List;

import javax.annotation.Nullable;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.state.BaseStateStore;
import com.devmod.endurance.ComboSystem;
import com.devmod.endurance.EnduranceQuestState;
import com.devmod.endurance.WaveObjectiveState;

/**
 * Client-side store for endurance quest state.
 *
 * <p>Tracks:
 * <ul>
 *   <li>Active quest info (id, template, wave)</li>
 *   <li>Combat stats (kills, damage, deaths)</li>
 *   <li>Wave objectives and progress</li>
 *   <li>Combo state</li>
 *   <li>Rewards earned</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class QuestStateStore extends BaseStateStore<QuestStateStore.QuestState> {

    // Track previous wave for change detection
    private int lastKnownWave = 0;

    public QuestStateStore() {
        super("QuestStore");
    }

    /**
     * Check if there's an active quest.
     */
    public boolean hasActiveQuest() {
        QuestState state = getState();
        return state != null && state.isActive();
    }

    /**
     * Get current wave number (0 if no active quest).
     */
    public int getCurrentWave() {
        QuestState state = getState();
        return state != null ? state.currentWave() : 0;
    }

    /**
     * Get quest state enum.
     */
    public EnduranceQuestState getQuestState() {
        QuestState state = getState();
        return state != null ? state.state() : EnduranceQuestState.AVAILABLE;
    }

    /**
     * Check if a wave just advanced.
     */
    public boolean didWaveAdvance() {
        QuestState state = getState();
        if (state == null) return false;

        boolean advanced = state.currentWave() > lastKnownWave;
        lastKnownWave = state.currentWave();
        return advanced;
    }

    @Override
    protected void onCleared() {
        lastKnownWave = 0;
    }

    /**
     * Immutable quest state snapshot.
     */
    public record QuestState(
        String questId,
        String templateId,
        EnduranceQuestState state,
        int currentWave,
        int maxWaves,
        int mobsKilled,
        int mobsKilledInWave,
        int mobsRemaining,
        double damageDealt,
        double damageTaken,
        int deaths,
        int currentStreak,
        int bestStreak,
        double comboMultiplier,
        ComboSystem.StyleRank styleRank,
        long elapsedTimeMs,
        int rewardPoints,
        @Nullable List<WaveObjectiveState> objectives,
        long serverTimestamp
    ) {
        /**
         * Check if quest is active.
         */
        public boolean isActive() {
            return state == EnduranceQuestState.IN_PROGRESS
                || state == EnduranceQuestState.WAVE_COMPLETE;
        }

        /**
         * Get wave progress as a fraction (0.0 to 1.0).
         */
        public float getWaveProgress() {
            if (maxWaves <= 0) return 0f;
            return (float) currentWave / maxWaves;
        }

        /**
         * Get formatted elapsed time (MM:SS).
         */
        public String getFormattedTime() {
            long seconds = elapsedTimeMs / 1000;
            long minutes = seconds / 60;
            seconds = seconds % 60;
            return String.format("%02d:%02d", minutes, seconds);
        }

        /**
         * Create from server sync payload data.
         */
        public static QuestState fromSyncData(
            String questId,
            String templateId,
            EnduranceQuestState state,
            int currentWave,
            int maxWaves,
            int mobsKilled,
            int mobsKilledInWave,
            int mobsRemaining,
            double damageDealt,
            double damageTaken,
            int deaths,
            int currentStreak,
            int bestStreak,
            double comboMultiplier,
            ComboSystem.StyleRank styleRank,
            long elapsedTimeMs,
            int rewardPoints,
            @Nullable List<WaveObjectiveState> objectives
        ) {
            return new QuestState(
                questId,
                templateId,
                state,
                currentWave,
                maxWaves,
                mobsKilled,
                mobsKilledInWave,
                mobsRemaining,
                damageDealt,
                damageTaken,
                deaths,
                currentStreak,
                bestStreak,
                comboMultiplier,
                styleRank,
                elapsedTimeMs,
                rewardPoints,
                objectives,
                System.currentTimeMillis()
            );
        }

        /**
         * Create empty state.
         */
        public static QuestState empty() {
            return new QuestState(
                "",
                "",
                EnduranceQuestState.AVAILABLE,
                0, 0, 0, 0, 0,
                0, 0, 0,
                0, 0, 1.0,
                ComboSystem.StyleRank.D,
                0, 0,
                List.of(),
                System.currentTimeMillis()
            );
        }
    }
}
