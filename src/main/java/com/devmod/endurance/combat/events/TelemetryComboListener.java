package com.devmod.endurance.combat.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.endurance.combat.api.ComboEvent;
import com.devmod.endurance.combat.api.IComboEventListener;
import com.devmod.telemetry.endurance.EnduranceTelemetryService;

/**
 * Listener that forwards combo events to the telemetry system.
 *
 * <p>This listener decouples combo tracking from telemetry recording.
 * Instead of calling telemetry directly from ComboSession, events are
 * dispatched and this listener handles the recording.</p>
 *
 * <h2>Benefits</h2>
 * <ul>
 *   <li>ComboSession has no dependency on telemetry</li>
 *   <li>Telemetry can be disabled by not registering this listener</li>
 *   <li>Easy to test combo logic without telemetry side effects</li>
 *   <li>Single place to modify all telemetry recording</li>
 * </ul>
 *
 * <h2>Events Recorded</h2>
 * <ul>
 *   <li>{@link ComboEvent.RankChanged} - Style rank changes</li>
 *   <li>{@link ComboEvent.SpecialAction} - High-value actions</li>
 *   <li>{@link ComboEvent.MilestoneReached} - Combo milestones</li>
 *   <li>{@link ComboEvent.ComboBreak} - Combo breaks from damage</li>
 *   <li>{@link ComboEvent.SessionEnded} - Final session stats</li>
 * </ul>
 */
public final class TelemetryComboListener implements IComboEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelemetryComboListener.class);

    private final EnduranceTelemetryService telemetry;

    /**
     * Create a telemetry listener using the singleton service.
     */
    public TelemetryComboListener() {
        this(EnduranceTelemetryService.INSTANCE);
    }

    /**
     * Create a telemetry listener with a specific service (for testing).
     *
     * @param telemetry The telemetry service to use
     */
    public TelemetryComboListener(EnduranceTelemetryService telemetry) {
        this.telemetry = telemetry;
    }

    @Override
    public void onRankChanged(ComboEvent.RankChanged event) {
        if (event.isPromotion()) {
            LOGGER.debug("[Telemetry] Recording rank promotion: {} -> {} for player {}",
                event.oldRank(), event.newRank(), event.playerId());

            telemetry.recordStyleRankChange(
                event.playerId(),
                event.questId(),
                event.oldRank(),
                event.newRank(),
                event.currentScore(),
                0 // Combo count not available in event - consider adding
            );
        }
    }

    @Override
    public void onSpecialAction(ComboEvent.SpecialAction event) {
        LOGGER.debug("[Telemetry] Recording special action: {} for player {}",
            event.action(), event.playerId());

        telemetry.recordSpecialAction(
            event.playerId(),
            event.questId(),
            event.action(),
            event.basePoints(),
            event.styleEarned(),
            event.currentCombo()
        );
    }

    @Override
    public void onMilestoneReached(ComboEvent.MilestoneReached event) {
        LOGGER.debug("[Telemetry] Recording combo milestone: {} ({} hits) for player {}",
            event.milestone(), event.comboCount(), event.playerId());

        telemetry.recordComboMilestone(
            event.playerId(),
            event.questId(),
            event.comboCount(),
            event.styleEarned(),
            null // Current rank not available - consider adding to event
        );
    }

    @Override
    public void onComboBreak(ComboEvent.ComboBreak event) {
        if (event.reason() == ComboEvent.ComboBreak.BreakReason.DAMAGE_TAKEN) {
            LOGGER.debug("[Telemetry] Recording combo break: {} hits lost for player {}",
                event.lostCombo(), event.playerId());

            telemetry.recordComboBreak(
                event.playerId(),
                event.questId(),
                event.lostCombo(),
                null, // Previous rank not available
                null, // Current rank not available
                event.damageIfHit()
            );
        }
    }

    @Override
    public void onSessionStarted(ComboEvent.SessionStarted event) {
        LOGGER.debug("[Telemetry] Combo session started for player {} in quest {}",
            event.playerId(), event.questId());
        // Quest start is recorded elsewhere - this is just for combo session tracking
    }

    @Override
    public void onSessionEnded(ComboEvent.SessionEnded event) {
        LOGGER.debug("[Telemetry] Combo session ended for player {}: score={}, maxCombo={}, rank={}",
            event.playerId(), event.finalScore(), event.maxCombo(), event.highestRank());

        // Final stats are recorded via QuestCompletionPayload
        // This could be used for additional combo-specific analytics
    }
}
