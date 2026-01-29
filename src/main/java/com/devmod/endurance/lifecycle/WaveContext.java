package com.devmod.endurance.lifecycle;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerPlayer;

import com.devmod.endurance.ArenaContext;
import com.devmod.endurance.CombatTracker;
import com.devmod.endurance.combat.api.IComboSession;
import com.devmod.endurance.EnduranceQuest;
import com.devmod.endurance.EnduranceQuestManager.ActiveQuestSession;
import com.devmod.endurance.MutatorSystem;
import com.devmod.endurance.WaveManager;
import com.devmod.endurance.WaveObjectiveState;

/**
 * Immutable context for wave lifecycle events.
 * Contains all information needed by listeners to handle wave start/complete.
 *
 * <p>Extends QuestContext with wave-specific data like wave number,
 * combat stats, and tracking sessions.</p>
 */
public record WaveContext(
    // Quest context fields
    UUID questId,
    UUID playerId,
    ServerPlayer player,
    EnduranceQuest quest,
    ActiveQuestSession session,
    @Nullable ArenaContext arena,
    boolean practiceMode,
    @Nullable UUID partyId,

    // Wave-specific fields
    int waveNumber,
    int totalWaves,
    boolean isEndlessMode,
    boolean isBossWave,
    int mobCount,
    String enemyType,

    // Tracking sessions (may be null during wave start)
    @Nullable IComboSession comboSession,
    @Nullable MutatorSystem.MutatorSession mutatorSession,
    @Nullable CombatTracker.WaveCombatStats waveStats,

    // Objective info
    @Nullable WaveObjectiveState objective,
    @Nullable String directiveId,
    float rewardMultiplier
) {
    /**
     * Creates a WaveContext for wave start events.
     *
     * @param questContext The parent quest context
     * @param waveNumber The wave number starting
     * @param isBossWave Whether this is a boss wave
     * @param mobCount Number of mobs in this wave
     * @param enemyType Display name of enemy type
     * @param comboSession The player's combo session
     * @return A new WaveContext
     */
    public static WaveContext forWaveStart(
            QuestContext questContext,
            int waveNumber,
            boolean isBossWave,
            int mobCount,
            String enemyType,
            @Nullable IComboSession comboSession) {

        Objects.requireNonNull(questContext, "questContext cannot be null");

        return new WaveContext(
            questContext.questId(),
            questContext.playerId(),
            questContext.player(),
            questContext.quest(),
            questContext.session(),
            questContext.arena(),
            questContext.practiceMode(),
            questContext.partyId(),
            waveNumber,
            questContext.getTotalWaves(),
            questContext.isEndlessMode(),
            isBossWave,
            mobCount,
            enemyType,
            comboSession,
            null, // mutatorSession loaded separately
            null, // waveStats not available at start
            null, // objective
            null, // directiveId
            1.0f  // rewardMultiplier
        );
    }

    /**
     * Creates a WaveContext for wave complete events.
     *
     * @param questContext The parent quest context
     * @param waveNumber The wave number completed
     * @param comboSession The player's combo session
     * @param mutatorSession The mutator session
     * @param waveStats Combat stats for this wave
     * @param waveState The wave state (for objective/directive info)
     * @return A new WaveContext
     */
    public static WaveContext forWaveComplete(
            QuestContext questContext,
            int waveNumber,
            @Nullable IComboSession comboSession,
            @Nullable MutatorSystem.MutatorSession mutatorSession,
            @Nullable CombatTracker.WaveCombatStats waveStats,
            @Nullable WaveManager.WaveState waveState) {

        Objects.requireNonNull(questContext, "questContext cannot be null");

        WaveObjectiveState objective = waveState != null ? waveState.getObjective() : null;
        String directiveId = waveState != null ? waveState.getDirectiveId() : null;
        float rewardMultiplier = waveState != null ? waveState.getRewardMultiplier() : 1.0f;

        return new WaveContext(
            questContext.questId(),
            questContext.playerId(),
            questContext.player(),
            questContext.quest(),
            questContext.session(),
            questContext.arena(),
            questContext.practiceMode(),
            questContext.partyId(),
            waveNumber,
            questContext.getTotalWaves(),
            questContext.isEndlessMode(),
            false, // isBossWave determined separately
            0,     // mobCount not relevant for complete
            "",    // enemyType not relevant for complete
            comboSession,
            mutatorSession,
            waveStats,
            objective,
            directiveId,
            rewardMultiplier
        );
    }

    /**
     * Returns the style rank from the combo session.
     */
    public String getStyleRank() {
        return comboSession != null ? comboSession.getCurrentRank().getDisplayName() : "D";
    }

    /**
     * Returns the max combo from the combo session.
     */
    public int getMaxCombo() {
        return comboSession != null ? comboSession.getMaxCombo() : 0;
    }

    /**
     * Returns damage dealt this wave.
     */
    public float getWaveDamageDealt() {
        return waveStats != null ? waveStats.damageDealt : 0;
    }

    /**
     * Returns damage taken this wave.
     */
    public float getWaveDamageTaken() {
        return waveStats != null ? waveStats.damageTaken : 0;
    }

    /**
     * Returns kills this wave.
     */
    public int getWaveKills() {
        return waveStats != null ? waveStats.kills : 0;
    }

    /**
     * Returns deaths this wave.
     */
    public int getWaveDeaths() {
        return waveStats != null ? waveStats.deaths : 0;
    }

    /**
     * Returns true if this was a flawless wave (no damage taken, at least 1 kill).
     */
    public boolean isFlawless() {
        return getWaveDamageTaken() == 0 && getWaveKills() > 0;
    }

    /**
     * Returns true if style rank is SSS.
     */
    public boolean isSSSRank() {
        return "SSS".equals(getStyleRank());
    }

    /**
     * Returns the active mutator count.
     */
    public int getActiveMutatorCount() {
        return mutatorSession != null ? mutatorSession.getActiveMutatorCount() : 0;
    }

    /**
     * Returns true if there are more waves after this one.
     */
    public boolean hasMoreWaves() {
        return waveNumber < totalWaves || isEndlessMode;
    }
}
