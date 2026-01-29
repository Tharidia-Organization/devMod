package com.devmod.endurance.combat.api;

/**
 * Listener interface for combo system events.
 *
 * <p>Implementations can subscribe to the {@link ComboEventDispatcher} to receive
 * notifications of combo state changes. This enables loose coupling between
 * the combo system and dependent systems (telemetry, challenges, UI, etc.).</p>
 *
 * <p>All methods have default empty implementations, allowing listeners to
 * only override the events they care about.</p>
 *
 * <p>Example implementation:</p>
 * <pre>{@code
 * public class TelemetryComboListener implements IComboEventListener {
 *     @Override
 *     public void onRankChanged(ComboEvent.RankChanged event) {
 *         telemetryService.recordRankChange(
 *             event.playerId(),
 *             event.oldRank(),
 *             event.newRank()
 *         );
 *     }
 * }
 * }</pre>
 */
public interface IComboEventListener {

    /**
     * Called when combo count increases.
     */
    default void onComboIncreased(ComboEvent.ComboIncreased event) {}

    /**
     * Called when combo breaks.
     */
    default void onComboBreak(ComboEvent.ComboBreak event) {}

    /**
     * Called when style rank changes.
     */
    default void onRankChanged(ComboEvent.RankChanged event) {}

    /**
     * Called when a combo milestone is reached.
     */
    default void onMilestoneReached(ComboEvent.MilestoneReached event) {}

    /**
     * Called when a special action is performed.
     */
    default void onSpecialAction(ComboEvent.SpecialAction event) {}

    /**
     * Called when flow state changes.
     */
    default void onFlowStateChanged(ComboEvent.FlowStateChanged event) {}

    /**
     * Called when session starts.
     */
    default void onSessionStarted(ComboEvent.SessionStarted event) {}

    /**
     * Called when session ends.
     */
    default void onSessionEnded(ComboEvent.SessionEnded event) {}

    /**
     * Generic event handler for all event types.
     * Override this for catch-all handling or pattern matching.
     *
     * @param event Any combo event
     */
    default void onEvent(ComboEvent event) {
        switch (event) {
            case ComboEvent.ComboIncreased e -> onComboIncreased(e);
            case ComboEvent.ComboBreak e -> onComboBreak(e);
            case ComboEvent.RankChanged e -> onRankChanged(e);
            case ComboEvent.MilestoneReached e -> onMilestoneReached(e);
            case ComboEvent.SpecialAction e -> onSpecialAction(e);
            case ComboEvent.FlowStateChanged e -> onFlowStateChanged(e);
            case ComboEvent.SessionStarted e -> onSessionStarted(e);
            case ComboEvent.SessionEnded e -> onSessionEnded(e);
        }
    }
}
