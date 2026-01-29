package com.devmod.endurance.combat.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.endurance.combat.api.ComboEvent;
import com.devmod.endurance.combat.api.IComboEventListener;
import com.devmod.notification.NotificationService;

/**
 * Listener that sends player notifications for combo events.
 *
 * <p>Handles combo decay notifications and rank changes, replacing
 * the deprecated polling-based notification system.</p>
 */
public final class NotificationComboListener implements IComboEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationComboListener.class);

    @Override
    public void onComboBreak(ComboEvent.ComboBreak event) {
        if (event.reason() != ComboEvent.ComboBreak.BreakReason.DAMAGE_TAKEN) {
            return;
        }

        // Only notify for significant combo loss
        if (event.lostCombo() < 3) {
            return;
        }

        LOGGER.debug("[Notification] Combo decay: player={}, lost={}",
            event.playerId(), event.lostCombo());

        // Note: Rank info is sent separately via RankChanged event
        // NotificationService expects both, so we'll use current rank ordinal
        NotificationService.INSTANCE.notifyComboDecay(
            event.playerId(),
            event.lostCombo(),
            -1,  // Previous rank (will be overwritten if rank also changed)
            -1   // New rank (will be overwritten if rank also changed)
        );
    }

    @Override
    public void onRankChanged(ComboEvent.RankChanged event) {
        if (event.isPromotion()) {
            LOGGER.debug("[Notification] Rank promotion: player={}, {} -> {}",
                event.playerId(), event.oldRank(), event.newRank());
            // Promotions are handled differently (announcements, etc.)
            return;
        }

        // Demotion notification
        LOGGER.debug("[Notification] Rank demotion: player={}, {} -> {}",
            event.playerId(), event.oldRank(), event.newRank());

        NotificationService.INSTANCE.notifyComboDecay(
            event.playerId(),
            0,  // Combo lost (already notified in ComboBreak)
            event.oldRank().ordinal(),
            event.newRank().ordinal()
        );
    }
}
