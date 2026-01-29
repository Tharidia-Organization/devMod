package com.devmod.endurance.combat.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.endurance.challenges.DailyChallengeManager;
import com.devmod.endurance.challenges.WeeklyChallengeManager;
import com.devmod.endurance.combat.api.ComboEvent;
import com.devmod.endurance.combat.api.IComboEventListener;

/**
 * Listener that forwards combo events to challenge tracking systems.
 *
 * <p>This listener decouples combo tracking from challenge progress updates.
 * Previously, ComboSession directly called DailyChallengeManager and
 * WeeklyChallengeManager - now those updates go through the event system.</p>
 *
 * <h2>Challenge Updates</h2>
 * <ul>
 *   <li>Style rank achievements (both daily and weekly)</li>
 *   <li>Combo count progress</li>
 *   <li>Milestone achievements</li>
 * </ul>
 *
 * <h2>Example Challenges Tracked</h2>
 * <ul>
 *   <li>"Reach S rank in a single quest" (RankChanged event)</li>
 *   <li>"Achieve a 50-hit combo" (ComboIncreased event)</li>
 *   <li>"Complete 10 quests with SSS rank" (SessionEnded event)</li>
 * </ul>
 */
public final class ChallengeComboListener implements IComboEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChallengeComboListener.class);

    private final DailyChallengeManager dailyManager;
    private final WeeklyChallengeManager weeklyManager;

    /**
     * Create listener with default managers (singletons).
     */
    public ChallengeComboListener() {
        this(DailyChallengeManager.INSTANCE, WeeklyChallengeManager.INSTANCE);
    }

    /**
     * Create listener with specific managers (for testing).
     *
     * @param dailyManager The daily challenge manager
     * @param weeklyManager The weekly challenge manager
     */
    public ChallengeComboListener(DailyChallengeManager dailyManager,
                                   WeeklyChallengeManager weeklyManager) {
        this.dailyManager = dailyManager;
        this.weeklyManager = weeklyManager;
    }

    @Override
    public void onRankChanged(ComboEvent.RankChanged event) {
        if (event.isPromotion()) {
            LOGGER.debug("[Challenges] Recording style rank {} for player {}",
                event.newRank(), event.playerId());

            // Update daily challenge progress
            dailyManager.onStyleRankUpdate(event.playerId(), event.newRank());

            // Update weekly challenge progress
            weeklyManager.onStyleRankAchieved(event.playerId(), event.newRank());
        }
    }

    @Override
    public void onComboIncreased(ComboEvent.ComboIncreased event) {
        // Only update challenges at significant combo thresholds to reduce overhead
        int combo = event.newCombo();
        if (combo == 10 || combo == 25 || combo == 50 || combo == 100 || combo % 50 == 0) {
            LOGGER.debug("[Challenges] Recording combo {} for player {}",
                combo, event.playerId());

            // Update daily challenge progress
            dailyManager.onComboUpdate(event.playerId(), combo);

            // Update weekly challenge progress
            weeklyManager.onComboUpdate(event.playerId(), combo);
        }
    }

    @Override
    public void onMilestoneReached(ComboEvent.MilestoneReached event) {
        // Milestones are already filtered to significant combos
        LOGGER.debug("[Challenges] Milestone {} reached for player {}",
            event.milestone(), event.playerId());

        // Force update on milestone to ensure challenge tracking
        dailyManager.onComboUpdate(event.playerId(), event.comboCount());
        weeklyManager.onComboUpdate(event.playerId(), event.comboCount());
    }

    @Override
    public void onSessionEnded(ComboEvent.SessionEnded event) {
        // Could track session-level achievements here
        // e.g., "Complete quest with max combo > 100"
        // e.g., "Complete quest with SSS rank"

        LOGGER.debug("[Challenges] Session ended for player {}: maxCombo={}, highestRank={}",
            event.playerId(), event.maxCombo(), event.highestRank());

        // Ensure final state is recorded
        dailyManager.onStyleRankUpdate(event.playerId(), event.highestRank());
        dailyManager.onComboUpdate(event.playerId(), event.maxCombo());

        weeklyManager.onStyleRankAchieved(event.playerId(), event.highestRank());
        weeklyManager.onComboUpdate(event.playerId(), event.maxCombo());
    }
}
