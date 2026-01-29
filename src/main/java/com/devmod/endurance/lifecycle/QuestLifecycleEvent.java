package com.devmod.endurance.lifecycle;

import java.util.UUID;

/**
 * Sealed hierarchy of quest lifecycle events.
 *
 * <p>Using sealed interfaces ensures type-safety and exhaustive pattern matching
 * when handling events. All events are immutable records containing the full
 * context needed by listeners.</p>
 *
 * <p>Example usage with pattern matching:</p>
 * <pre>{@code
 * switch (event) {
 *     case QuestStarted qs -> handleQuestStart(qs);
 *     case QuestEnded qe -> handleQuestEnd(qe);
 *     case WaveStarted ws -> handleWaveStart(ws);
 *     case WaveCompleted wc -> handleWaveComplete(wc);
 *     case PlayerJoinedQuest pj -> handlePlayerJoin(pj);
 *     case PlayerLeftQuest pl -> handlePlayerLeave(pl);
 * }
 * }</pre>
 *
 * <p>This event system decouples the EnduranceEventHandler (event publisher)
 * from the 35+ subsystems that need to respond to quest lifecycle changes.
 * Instead of the handler calling each system directly via INSTANCE, systems
 * register as listeners and receive events asynchronously.</p>
 *
 * @see QuestEventBus
 * @see QuestLifecycleListener
 */
public sealed interface QuestLifecycleEvent {

    /**
     * The quest ID this event belongs to.
     */
    UUID questId();

    /**
     * Timestamp when this event occurred.
     */
    long timestamp();

    // ═══════════════════════════════════════════════════════════════
    // QUEST LIFECYCLE EVENTS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Fired when a quest starts for a player.
     * Systems should initialize their tracking sessions here.
     */
    record QuestStarted(
        UUID questId,
        long timestamp,
        QuestContext context
    ) implements QuestLifecycleEvent {

        public static QuestStarted of(QuestContext context) {
            return new QuestStarted(
                context.questId(),
                System.currentTimeMillis(),
                context
            );
        }
    }

    /**
     * Fired when a quest ends (completed, failed, or abandoned).
     * Systems should clean up their tracking sessions here.
     */
    record QuestEnded(
        UUID questId,
        long timestamp,
        QuestContext context,
        boolean completed,
        EndReason reason,
        boolean cleanupShared
    ) implements QuestLifecycleEvent {

        public enum EndReason {
            COMPLETED,
            FAILED,
            ABANDONED,
            DISCONNECTED,
            TIMEOUT
        }

        public static QuestEnded completed(QuestContext context, boolean cleanupShared) {
            return new QuestEnded(
                context.questId(),
                System.currentTimeMillis(),
                context,
                true,
                EndReason.COMPLETED,
                cleanupShared
            );
        }

        public static QuestEnded failed(QuestContext context, EndReason reason, boolean cleanupShared) {
            return new QuestEnded(
                context.questId(),
                System.currentTimeMillis(),
                context,
                false,
                reason,
                cleanupShared
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // WAVE LIFECYCLE EVENTS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Fired when a new wave starts.
     * Combat systems should reset per-wave state here.
     */
    record WaveStarted(
        UUID questId,
        long timestamp,
        WaveContext context,
        boolean applyShared
    ) implements QuestLifecycleEvent {

        public static WaveStarted of(WaveContext context, boolean applyShared) {
            return new WaveStarted(
                context.questId(),
                System.currentTimeMillis(),
                context,
                applyShared
            );
        }
    }

    /**
     * Fired when a wave is completed.
     * Systems should calculate rewards and prepare for next wave here.
     */
    record WaveCompleted(
        UUID questId,
        long timestamp,
        WaveContext context,
        boolean applyShared
    ) implements QuestLifecycleEvent {

        public static WaveCompleted of(WaveContext context, boolean applyShared) {
            return new WaveCompleted(
                context.questId(),
                System.currentTimeMillis(),
                context,
                applyShared
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // PLAYER LIFECYCLE EVENTS (for party quests)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Fired when a player joins an existing quest (late join in party).
     */
    record PlayerJoinedQuest(
        UUID questId,
        long timestamp,
        QuestContext context,
        boolean isRejoin
    ) implements QuestLifecycleEvent {

        public static PlayerJoinedQuest of(QuestContext context, boolean isRejoin) {
            return new PlayerJoinedQuest(
                context.questId(),
                System.currentTimeMillis(),
                context,
                isRejoin
            );
        }
    }

    /**
     * Fired when a player leaves a quest (disconnect, death, spectate).
     */
    record PlayerLeftQuest(
        UUID questId,
        long timestamp,
        UUID playerId,
        LeaveReason reason
    ) implements QuestLifecycleEvent {

        public enum LeaveReason {
            DISCONNECTED,
            DEATH,
            SPECTATING,
            KICKED
        }

        public static PlayerLeftQuest of(UUID questId, UUID playerId, LeaveReason reason) {
            return new PlayerLeftQuest(
                questId,
                System.currentTimeMillis(),
                playerId,
                reason
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // CHECKPOINT EVENTS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Fired when a checkpoint is reached (for endless mode or between waves).
     */
    record CheckpointReached(
        UUID questId,
        long timestamp,
        QuestContext context,
        int waveNumber,
        boolean canSaveProgress
    ) implements QuestLifecycleEvent {

        public static CheckpointReached of(QuestContext context, int waveNumber, boolean canSaveProgress) {
            return new CheckpointReached(
                context.questId(),
                System.currentTimeMillis(),
                context,
                waveNumber,
                canSaveProgress
            );
        }
    }
}
