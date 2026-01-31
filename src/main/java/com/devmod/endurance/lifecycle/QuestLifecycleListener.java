package com.devmod.endurance.lifecycle;

import com.devmod.endurance.lifecycle.QuestLifecycleEvent.CheckpointReached;
import com.devmod.endurance.lifecycle.QuestLifecycleEvent.PlayerJoinedQuest;
import com.devmod.endurance.lifecycle.QuestLifecycleEvent.PlayerLeftQuest;
import com.devmod.endurance.lifecycle.QuestLifecycleEvent.QuestEnded;
import com.devmod.endurance.lifecycle.QuestLifecycleEvent.QuestStarted;
import com.devmod.endurance.lifecycle.QuestLifecycleEvent.WaveCompleted;
import com.devmod.endurance.lifecycle.QuestLifecycleEvent.WaveStarted;

/**
 * Interface for systems that need to respond to quest lifecycle events.
 *
 * <p>Implementing this interface allows a system to be notified of quest
 * lifecycle changes without being directly coupled to EnduranceEventHandler.
 * Systems register with the {@link QuestEventBus} and receive events they
 * care about.</p>
 *
 * <p>All methods have default empty implementations, so listeners only need
 * to override the events they care about.</p>
 *
 * <h2>Example Implementation:</h2>
 * <pre>{@code
 * public class ComboSystem implements QuestLifecycleListener {
 *     @Override
 *     public void onQuestStarted(QuestStarted event) {
 *         startSession(event.context().playerId(), event.questId());
 *     }
 *
 *     @Override
 *     public void onQuestEnded(QuestEnded event) {
 *         endSession(event.context().playerId());
 *     }
 *
 *     @Override
 *     public int getPriority() {
 *         return 100; // High priority - runs early
 *     }
 * }
 * }</pre>
 *
 * <h2>Priority System:</h2>
 * <p>Listeners are called in priority order (highest first). Use priorities to
 * control execution order when it matters:</p>
 * <ul>
 *   <li><b>1000+</b>: Critical systems (tracking, state management)</li>
 *   <li><b>500-999</b>: Core gameplay systems (combat, perks)</li>
 *   <li><b>100-499</b>: Feature systems (bargains, hazards, tide)</li>
 *   <li><b>0-99</b>: Analytics and telemetry</li>
 *   <li><b>Negative</b>: Post-processing, notifications</li>
 * </ul>
 *
 * @see QuestEventBus
 * @see QuestLifecycleEvent
 */
public interface QuestLifecycleListener {

    // ═══════════════════════════════════════════════════════════════
    // QUEST LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Called when a quest starts.
     * Use this to initialize tracking sessions and per-quest state.
     *
     * @param event The quest started event with full context
     */
    default void onQuestStarted(QuestStarted event) {}

    /**
     * Called when a quest ends (completed, failed, or abandoned).
     * Use this to clean up tracking sessions and calculate final stats.
     *
     * @param event The quest ended event with full context and outcome
     */
    default void onQuestEnded(QuestEnded event) {}

    // ═══════════════════════════════════════════════════════════════
    // WAVE LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Called when a new wave starts.
     * Use this to reset per-wave state and prepare for combat.
     *
     * @param event The wave started event with wave context
     */
    default void onWaveStarted(WaveStarted event) {}

    /**
     * Called when a wave is completed.
     * Use this to calculate rewards, update progression, and prepare next wave.
     *
     * @param event The wave completed event with combat stats
     */
    default void onWaveCompleted(WaveCompleted event) {}

    // ═══════════════════════════════════════════════════════════════
    // PLAYER LIFECYCLE (for party quests)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Called when a player joins an existing quest (late join in party).
     *
     * @param event The player joined event
     */
    default void onPlayerJoined(PlayerJoinedQuest event) {}

    /**
     * Called when a player leaves a quest (disconnect, death, spectate).
     *
     * @param event The player left event
     */
    default void onPlayerLeft(PlayerLeftQuest event) {}

    // ═══════════════════════════════════════════════════════════════
    // CHECKPOINT
    // ═══════════════════════════════════════════════════════════════

    /**
     * Called when a checkpoint is reached.
     *
     * @param event The checkpoint event
     */
    default void onCheckpointReached(CheckpointReached event) {}

    // ═══════════════════════════════════════════════════════════════
    // LISTENER METADATA
    // ═══════════════════════════════════════════════════════════════

    /**
     * Returns the priority of this listener.
     * Higher priority listeners are called first.
     *
     * <p>Default is 0 (normal priority).</p>
     *
     * <p>Recommended ranges:</p>
     * <ul>
     *   <li><b>1000+</b>: Critical (CombatTracker, ComboSystem)</li>
     *   <li><b>500-999</b>: Core gameplay (PerkSystem, MutatorSystem)</li>
     *   <li><b>100-499</b>: Features (TideManager, HazardSystem)</li>
     *   <li><b>0-99</b>: Analytics (Telemetry, LiveAnalytics)</li>
     *   <li><b>Negative</b>: Post-processing (Notifications)</li>
     * </ul>
     *
     * @return The listener priority (higher = called first)
     */
    default int getPriority() {
        return 0;
    }

    /**
     * Returns the name of this listener for logging/debugging.
     * Default implementation uses the class simple name.
     *
     * @return The listener name
     */
    default String getListenerName() {
        return getClass().getSimpleName();
    }

    /**
     * Returns true if this listener should receive events for practice mode quests.
     * Default is true (receives all events).
     *
     * @return true to receive practice mode events
     */
    default boolean receivePracticeModeEvents() {
        return true;
    }
}
