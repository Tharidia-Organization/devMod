package com.devmod.endurance.lifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import com.devmod.endurance.CombatTracker;
import com.devmod.endurance.EnduranceQuest;
import com.devmod.endurance.EnduranceQuestManager;
import com.devmod.endurance.PartyQuestSession;
import com.devmod.endurance.PartyStatsSyncPayload;
import com.devmod.endurance.PartyWaveStats;
import com.devmod.endurance.lifecycle.QuestLifecycleEvent.WaveCompleted;

/**
 * Coordinates party statistics aggregation and synchronization.
 *
 * <p>This coordinator handles the collection and distribution of per-player
 * combat statistics to all party members at the end of each wave. It was
 * extracted from the 176-line {@code sendPartyWaveStats} method in
 * EnduranceEventHandler.</p>
 *
 * <h2>Responsibilities:</h2>
 * <ul>
 *   <li>Aggregate wave stats from all party members</li>
 *   <li>Calculate party-wide totals (kills, damage, DPS)</li>
 *   <li>Determine MVP for the wave</li>
 *   <li>Sync stats to all party members for debrief UI</li>
 * </ul>
 *
 * <h2>Priority:</h2>
 * <p>Low priority (-100) - runs after all combat tracking is finalized.</p>
 */
public class PartyStatsCoordinator implements QuestLifecycleListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(PartyStatsCoordinator.class);

    public static final PartyStatsCoordinator INSTANCE = new PartyStatsCoordinator();

    private PartyStatsCoordinator() {}

    @Override
    public void onWaveCompleted(WaveCompleted event) {
        if (!event.applyShared()) {
            return; // Skip if not processing shared state
        }

        WaveContext ctx = event.context();
        if (ctx.partyId() == null) {
            return; // Solo run, no party stats needed
        }

        aggregateAndSyncPartyStats(ctx);
    }

    @Override
    public int getPriority() {
        return -100; // Low priority - runs after combat tracking is finalized
    }

    @Override
    public String getListenerName() {
        return "PartyStatsCoordinator";
    }

    /**
     * Aggregates stats from all party members and syncs to clients.
     */
    private void aggregateAndSyncPartyStats(WaveContext ctx) {
        UUID partyId = ctx.partyId();
        int waveNumber = ctx.waveNumber();

        PartyQuestSession partySession = EnduranceQuestManager.INSTANCE
            .getPartySession(partyId).orElse(null);
        if (partySession == null || !partySession.isActive()) {
            return;
        }

        // Only aggregate once per wave (first player to complete triggers aggregation)
        EnduranceQuest quest = partySession.getQuest();
        if (quest.getCurrentWave() != waveNumber) {
            return; // Already advanced, skip
        }

        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        // Get shared combat session for this quest
        CombatTracker.QuestCombatSession combatSession =
            CombatTracker.INSTANCE.getSession(partySession.getQuestId()).orElse(null);
        CombatTracker.WaveCombatStats sharedWaveStats = combatSession != null
            ? combatSession.getWaveStats(waveNumber)
            : null;

        // Finalize duration BEFORE reading it (otherwise it's 0)
        if (sharedWaveStats != null) {
            sharedWaveStats.finalizeDuration();
        }

        long waveDurationMs = sharedWaveStats != null ? sharedWaveStats.duration : 0;

        // Aggregate stats from all party members
        List<PartyWaveStats.PlayerWaveData> playerStats = new ArrayList<>();
        int partyTotalKills = 0;
        int partyEliteKills = 0;
        int partyTotalDamageDealt = 0;
        int partyTotalDamageTaken = 0;
        int partyDeaths = 0;
        int partyMaxCombo = 0;

        for (UUID memberId : partySession.getMembers()) {
            if (memberId == null) {
                LOGGER.warn("[PartyStats] Null member ID in party during stats collection");
                continue;
            }
            ServerPlayer memberPlayer = server.getPlayerList().getPlayer(memberId);
            if (memberPlayer == null) continue;

            String playerName = memberPlayer.getName().getString();
            boolean isSpectator = partySession.isSpectator(memberId);

            if (isSpectator) {
                playerStats.add(PartyWaveStats.PlayerWaveData.spectator(memberId, playerName));
                continue;
            }

            // Get member's session data
            EnduranceQuestManager.ActiveQuestSession memberSession =
                EnduranceQuestManager.INSTANCE.getActiveSession(memberPlayer).orElse(null);
            if (memberSession == null) continue;

            // Get per-player stats using explicit wave number
            CombatTracker.PlayerWaveCombatStats playerWaveStats = combatSession != null
                ? combatSession.getPlayerWaveStats(memberId, waveNumber)
                : null;

            // Use all 0s as fallback if no tracking data
            int kills = playerWaveStats != null ? playerWaveStats.kills : 0;
            int damageDealt = playerWaveStats != null ? (int) playerWaveStats.damageDealt : 0;
            int damageTaken = playerWaveStats != null ? (int) playerWaveStats.damageTaken : 0;
            int deaths = playerWaveStats != null ? playerWaveStats.deaths : 0;
            int eliteKills = playerWaveStats != null ? playerWaveStats.eliteKills : 0;
            int maxCombo = playerWaveStats != null ? playerWaveStats.maxCombo : 0;

            float dps = waveDurationMs > 0 ? (damageDealt * 1000f / waveDurationMs) : 0;

            partyTotalKills += kills;
            partyEliteKills += eliteKills;
            partyTotalDamageDealt += damageDealt;
            partyTotalDamageTaken += damageTaken;
            partyDeaths += deaths;
            if (maxCombo > partyMaxCombo) partyMaxCombo = maxCombo;

            playerStats.add(new PartyWaveStats.PlayerWaveData(
                memberId.toString(),
                playerName,
                kills,
                eliteKills,
                damageDealt,
                damageTaken,
                deaths,
                maxCombo,
                dps,
                0f, // killPercent calculated below
                false
            ));
        }

        // Calculate kill percentages
        if (partyTotalKills > 0) {
            List<PartyWaveStats.PlayerWaveData> updatedStats = new ArrayList<>();
            for (PartyWaveStats.PlayerWaveData data : playerStats) {
                float killPercent = (data.kills() * 100f) / partyTotalKills;
                updatedStats.add(new PartyWaveStats.PlayerWaveData(
                    data.playerId(), data.playerName(), data.kills(), data.eliteKills(),
                    data.damageDealt(), data.damageTaken(), data.deaths(), data.maxCombo(),
                    data.dps(), killPercent, data.wasSpectator()
                ));
            }
            playerStats = updatedStats;
        }

        // Calculate party DPS and no-damage status
        float partyDPS = waveDurationMs > 0 ? (partyTotalDamageDealt * 1000f / waveDurationMs) : 0;
        boolean partyNoDamageWave = partyTotalDamageTaken == 0 && partyTotalKills > 0;

        // Determine MVP
        String mvpPlayerId = PartyWaveStats.determineMvp(playerStats);
        PartyWaveStats.PlayerWaveData mvpData = null;
        for (PartyWaveStats.PlayerWaveData data : playerStats) {
            if (data.playerId().equals(mvpPlayerId)) {
                mvpData = data;
                break;
            }
        }
        String mvpReason = PartyWaveStats.determineMvpReason(mvpData, playerStats, waveNumber);

        // Build and send payload to all party members
        PartyWaveStats stats = new PartyWaveStats(
            waveNumber,
            quest.getTotalWaves(),
            waveDurationMs,
            playerStats,
            partyTotalKills,
            partyEliteKills,
            partyTotalDamageDealt,
            partyTotalDamageTaken,
            partyDeaths,
            partyMaxCombo,
            partyDPS,
            partyNoDamageWave,
            mvpPlayerId,
            mvpReason
        );

        PartyStatsSyncPayload payload = PartyStatsSyncPayload.fromPartyWaveStats(stats);

        if (payload == null) {
            LOGGER.warn("[PartyStats] Failed to create PartyStatsSyncPayload for wave {}", waveNumber);
            return;
        }

        for (UUID memberId : partySession.getMembers()) {
            if (memberId == null) {
                LOGGER.warn("[PartyStats] Null member ID in party session during stats sync");
                continue;
            }
            ServerPlayer memberPlayer = server.getPlayerList().getPlayer(memberId);
            if (memberPlayer != null) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(memberPlayer, payload);
            }
        }

        LOGGER.debug("[PartyStats] Party stats synced for wave {}: {} members, {} total kills, MVP: {}",
            waveNumber, playerStats.size(), partyTotalKills, mvpPlayerId);
    }
}
