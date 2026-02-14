package com.devmod.endurance.season;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;

import com.devmod.abilities.api.AbilityEvent;
import com.devmod.abilities.api.IAbilityEventListener;
import com.devmod.endurance.combat.api.ComboEvent;
import com.devmod.endurance.combat.api.IComboEventListener;
import com.devmod.endurance.lifecycle.QuestLifecycleEvent;
import com.devmod.endurance.lifecycle.QuestLifecycleListener;
import com.devmod.network.NetworkHandler;

/**
 * Wires season pass XP earning to gameplay events.
 *
 * <p>Listens to quest lifecycle events (wave clear, quest completion, boss kills),
 * combo milestones, and perfect dodges to grant season pass XP.</p>
 *
 * <p>Implements three listener interfaces to hook into all XP sources:</p>
 * <ul>
 *   <li>{@link QuestLifecycleListener} - wave clear, quest end</li>
 *   <li>{@link IComboEventListener} - combo milestones</li>
 *   <li>{@link IAbilityEventListener} - perfect dodges</li>
 * </ul>
 */
public final class SeasonPassXPListener
        implements QuestLifecycleListener, IComboEventListener, IAbilityEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeasonPassXPListener.class);

    public static final SeasonPassXPListener INSTANCE = new SeasonPassXPListener();

    // XP amounts (matching SeasonPassSystem defaults)
    private static final int XP_WAVE_CLEAR = SeasonPassSystem.DEFAULT_XP_PER_WAVE;
    private static final int XP_QUEST_COMPLETION = 500;
    private static final int XP_QUEST_BASE = 100;
    private static final int XP_COMBO_50 = 75;
    private static final int XP_BOSS_KILL = SeasonPassSystem.DEFAULT_XP_PER_BOSS_KILL;
    private static final int XP_DAILY_LOGIN = 50;
    private static final int XP_PERFECT_DODGE = 10;

    // Track daily login grants (playerId -> last grant date)
    private final Map<UUID, LocalDate> dailyLoginGrants = new ConcurrentHashMap<>();

    // Track combo milestones already awarded per quest to avoid duplicates
    private final Map<UUID, Integer> comboMilestonesAwarded = new ConcurrentHashMap<>();

    private SeasonPassXPListener() {}

    // =========================================================================
    // QuestLifecycleListener - Wave Clear & Quest Completion
    // =========================================================================

    @Override
    public void onWaveCompleted(QuestLifecycleEvent.WaveCompleted event) {
        if (event.context().practiceMode()) return;

        UUID playerId = event.context().playerId();
        int waveNumber = event.context().waveNumber();

        // Award wave clear XP
        grantXP(playerId, XP_WAVE_CLEAR, "wave_clear_" + waveNumber);

        // Check if this was a boss wave
        UUID questId = event.questId();
        boolean isBossWave = com.devmod.endurance.BossWaveSystem.INSTANCE.isBossWave(waveNumber, questId);
        if (isBossWave) {
            grantXP(playerId, XP_BOSS_KILL, "boss_kill_wave_" + waveNumber);
        }
    }

    @Override
    public void onQuestEnded(QuestLifecycleEvent.QuestEnded event) {
        if (event.context().practiceMode()) return;

        UUID playerId = event.context().playerId();

        // Clean up combo milestone tracking
        comboMilestonesAwarded.remove(playerId);

        if (!event.completed()) return;

        // Award quest completion bonus (base + wave-scaling)
        int wavesCompleted = event.context().quest().getCurrentWave();
        int questXp = XP_QUEST_BASE + (wavesCompleted * 20);
        questXp = Math.min(questXp, XP_QUEST_COMPLETION);
        grantXP(playerId, questXp, "quest_completion");
    }

    @Override
    public void onQuestStarted(QuestLifecycleEvent.QuestStarted event) {
        if (event.context().practiceMode()) return;
        // Reset combo milestone tracking for new quest
        comboMilestonesAwarded.put(event.context().playerId(), 0);
    }

    @Override
    public int getPriority() {
        return 50; // Feature system priority
    }

    @Override
    public boolean receivePracticeModeEvents() {
        return false;
    }

    @Override
    public String getListenerName() {
        return "SeasonPassXPListener";
    }

    // =========================================================================
    // IComboEventListener - Combo Milestones
    // =========================================================================

    @Override
    public void onMilestoneReached(ComboEvent.MilestoneReached event) {
        UUID playerId = event.playerId();
        int comboCount = event.comboCount();

        // Only award for 50+ hit milestones
        if (comboCount >= 50) {
            // Check if we already awarded this milestone level for this quest
            int previousHighest = comboMilestonesAwarded.getOrDefault(playerId, 0);
            // Award at 50, 100, 150, etc. - each 50-hit threshold
            int milestoneLevel = (comboCount / 50) * 50;
            if (milestoneLevel > previousHighest) {
                comboMilestonesAwarded.put(playerId, milestoneLevel);
                grantXP(playerId, XP_COMBO_50, "combo_milestone_" + milestoneLevel);
            }
        }
    }

    // =========================================================================
    // IAbilityEventListener - Perfect Dodges
    // =========================================================================

    @Override
    public void onPerfectDodge(AbilityEvent.PerfectDodge event) {
        UUID playerId = event.playerId();
        grantXP(playerId, XP_PERFECT_DODGE, "perfect_dodge");
    }

    // =========================================================================
    // Daily Login
    // =========================================================================

    /**
     * Called on player login to grant daily login XP.
     * Should be called from the login event handler.
     */
    public void onPlayerLogin(ServerPlayer player) {
        UUID playerId = player.getUUID();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate lastGrant = dailyLoginGrants.get(playerId);

        if (lastGrant == null || !lastGrant.equals(today)) {
            dailyLoginGrants.put(playerId, today);
            grantXPAndSync(player, XP_DAILY_LOGIN, "daily_login");
            LOGGER.info("[SeasonPass] Daily login XP granted to {}", player.getName().getString());
        }
    }

    // =========================================================================
    // Core XP Grant
    // =========================================================================

    /**
     * Grant XP using UUID only (for listeners that don't have a ServerPlayer ref).
     * Syncs to client if the player is online.
     */
    private void grantXP(UUID playerId, int amount, String source) {
        SeasonPassSystem system = SeasonPassSystem.INSTANCE;
        if (!system.isSeasonActive()) return;

        system.awardXP(playerId, amount, source);

        // Try to sync to client
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                NetworkHandler.sendSeasonPassSync(player);
            }
        }
    }

    /**
     * Grant XP with direct player reference (avoids server lookup).
     */
    private void grantXPAndSync(ServerPlayer player, int amount, String source) {
        SeasonPassSystem system = SeasonPassSystem.INSTANCE;
        if (!system.isSeasonActive()) return;

        system.awardXP(player.getUUID(), amount, source);
        NetworkHandler.sendSeasonPassSync(player);
    }

    /**
     * Clean up player data on disconnect.
     */
    public void onPlayerDisconnect(UUID playerId) {
        comboMilestonesAwarded.remove(playerId);
        // Don't clear dailyLoginGrants - those persist for the server session
    }
}
