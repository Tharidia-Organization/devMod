package com.devmod.abilities.api;

/**
 * Listener interface for ability system events.
 *
 * <p>Implementations can subscribe to the {@link AbilityEventDispatcher} to receive
 * notifications of dodge/dash ability events. This enables addons and other mods
 * to hook into the ability system without coupling to internals.</p>
 *
 * <p>Pre-event methods return {@code boolean}: returning {@code true} cancels the ability.
 * Post-event methods are fire-and-forget notifications.</p>
 *
 * <p>Example implementation:</p>
 * <pre>{@code
 * public class MyAddonAbilityListener implements IAbilityEventListener {
 *     @Override
 *     public boolean onDodgePre(AbilityEvent.DodgePre event) {
 *         // Cancel dodge if player has a debuff
 *         return hasNoDodgeDebuff(event.playerId());
 *     }
 *
 *     @Override
 *     public void onPerfectDodge(AbilityEvent.PerfectDodge event) {
 *         // Award custom bonus on perfect dodge
 *         grantPerfectDodgeBonus(event.playerId(), event.damageNegated());
 *     }
 * }
 * }</pre>
 */
public interface IAbilityEventListener {

    /**
     * Called before a dodge is executed.
     * @return {@code true} to cancel the dodge, {@code false} to allow it
     */
    default boolean onDodgePre(AbilityEvent.DodgePre event) { return false; }

    /**
     * Called after a successful dodge.
     */
    default void onDodgePost(AbilityEvent.DodgePost event) {}

    /**
     * Called before a dash is executed.
     * @return {@code true} to cancel the dash, {@code false} to allow it
     */
    default boolean onDashPre(AbilityEvent.DashPre event) { return false; }

    /**
     * Called after a successful dash.
     */
    default void onDashPost(AbilityEvent.DashPost event) {}

    /**
     * Called when a perfect dodge is triggered.
     */
    default void onPerfectDodge(AbilityEvent.PerfectDodge event) {}

    /**
     * Generic event handler for all event types.
     * Override this for catch-all handling or pattern matching.
     * For Pre-events dispatched via this method, return value is ignored
     * (use the specific onDodgePre/onDashPre methods for cancellation).
     */
    default void onEvent(AbilityEvent event) {
        switch (event) {
            case AbilityEvent.DodgePre e -> onDodgePre(e);
            case AbilityEvent.DodgePost e -> onDodgePost(e);
            case AbilityEvent.DashPre e -> onDashPre(e);
            case AbilityEvent.DashPost e -> onDashPost(e);
            case AbilityEvent.PerfectDodge e -> onPerfectDodge(e);
        }
    }
}
