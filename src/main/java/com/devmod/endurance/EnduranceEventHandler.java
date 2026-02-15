package com.devmod.endurance;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.devmod.endurance.combat.ComboSystemFacade;
import com.devmod.endurance.combat.api.IComboSession;
import com.devmod.endurance.nutrition.NutritionBridgeSystem;
import com.devmod.party.QuestStartSequence;
import com.devmod.telemetry.duckdb.aggregation.AggregationConfig;
import com.devmod.telemetry.duckdb.aggregation.TelemetryAggregatorRegistry;

/**
 * Facade for endurance quest NeoForge event subscribers.
 * Business logic is delegated to:
 * <ul>
 *   <li>{@link EnduranceEventQuestLifecycle} - quest start/end, mailbox, analytics helpers</li>
 *   <li>{@link EnduranceEventWave} - wave start/complete, party wave stats</li>
 *   <li>{@link EnduranceEventCombat} - combat tracking (pre-existing)</li>
 *   <li>{@link EnduranceEventTick} - tick processing (pre-existing)</li>
 * </ul>
 */
@EventBusSubscriber(modid = "devmod")
public class EnduranceEventHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnduranceEventHandler.class);

    // ==============================================================
    // QUEST LIFECYCLE (delegates to EnduranceEventQuestLifecycle)
    // ==============================================================

    public static void onQuestStart(ServerPlayer player, EnduranceQuestManager.ActiveQuestSession session) {
        EnduranceEventQuestLifecycle.onQuestStart(player, session);
    }

    public static void onQuestEnd(ServerPlayer player, EnduranceQuestManager.ActiveQuestSession session,
                                   boolean completed) {
        EnduranceEventQuestLifecycle.onQuestEnd(player, session, completed, true);
    }

    public static void onQuestEnd(ServerPlayer player, EnduranceQuestManager.ActiveQuestSession session,
                                   boolean completed, boolean cleanupShared) {
        EnduranceEventQuestLifecycle.onQuestEnd(player, session, completed, cleanupShared);
    }

    // ==============================================================
    // WAVE EVENTS (delegates to EnduranceEventWave)
    // ==============================================================

    public static void onWaveStart(ServerPlayer player, EnduranceQuestManager.ActiveQuestSession session, int waveNumber) {
        EnduranceEventWave.onWaveStart(player, session, waveNumber, true);
    }

    public static void onWaveStart(ServerPlayer player, EnduranceQuestManager.ActiveQuestSession session,
                                   int waveNumber, boolean applyShared) {
        EnduranceEventWave.onWaveStart(player, session, waveNumber, applyShared);
    }

    public static void onWaveComplete(ServerPlayer player, EnduranceQuestManager.ActiveQuestSession session, int waveNumber) {
        EnduranceEventWave.onWaveComplete(player, session, waveNumber, true);
    }

    public static void onWaveComplete(ServerPlayer player, EnduranceQuestManager.ActiveQuestSession session,
                                      int waveNumber, boolean applyShared) {
        EnduranceEventWave.onWaveComplete(player, session, waveNumber, applyShared);
    }

    // ==============================================================
    // NEOFORGE EVENT SUBSCRIBERS
    // ==============================================================

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        var target = event.getEntity();
        var attacker = event.getSource().getEntity();
        boolean isQuestMob = EnduranceEventCombat.isQuestMob(target);
        boolean playerIsTarget = target instanceof ServerPlayer;
        boolean playerIsAttacker = attacker instanceof ServerPlayer;
        if ((isQuestMob || playerIsTarget) && event.getNewDamage() > 0f) {
            LOGGER.info("[CombatDebug] LivingDamageEvent.Post: target={}, attacker={}, damage={}, isQuestMob={}, playerTarget={}, playerAttacker={}",
                target.getName().getString(),
                attacker != null ? attacker.getName().getString() : "null",
                event.getNewDamage(),
                isQuestMob,
                playerIsTarget,
                playerIsAttacker);
        }
        EnduranceEventCombat.handleDamagePost(event.getEntity(), event.getSource(), event.getNewDamage());
    }

    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();

        if (target instanceof ServerPlayer player) {
            Optional<EnduranceQuestManager.ActiveQuestSession> session =
                EnduranceQuestManager.INSTANCE.getActiveSession(player);

            if (session.isPresent()) {
                if (session.get().isLoadingProtection()) {
                    if (event.getNewDamage() > 0f) {
                        LOGGER.debug("[EnduranceQuest] Blocked damage during loading for {} (source={}, damage={}, pos={}, dim={})",
                            player.getName().getString(),
                            event.getSource().getMsgId(),
                            event.getNewDamage(),
                            player.blockPosition(),
                            player.level().dimension().location());
                    }
                    event.setNewDamage(0f);
                    return;
                }
                float modifiedDamage = EnduranceEventCombat.handleDamagePre(player, event.getNewDamage());
                event.setNewDamage(modifiedDamage);
            }
        }
    }

    @SubscribeEvent
    public static void onCriticalHit(CriticalHitEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.isVanillaCritical()) {
            Entity target = event.getTarget();
            float damage = event.getDamageMultiplier() * player.getAttackStrengthScale(0.5f);
            EnduranceEventCombat.handleCriticalHit(player, target, damage);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();

        if (entity instanceof ServerPlayer p) {
            LOGGER.info("[DeathDebug] PLAYER DEATH EVENT: player={}, dimension={}, source={}, health={}, isDeadOrDying={}",
                p.getName().getString(),
                p.level().dimension().location(),
                event.getSource() != null ? event.getSource().type().msgId() : "null",
                p.getHealth(),
                p.isDeadOrDying());
        }

        if (EnduranceEventCombat.isQuestMob(entity)) {
            EnduranceEventCombat.handleMobDeath(entity, event.getSource());
        }

        if (entity instanceof ServerPlayer player) {
            var sessionOpt = EnduranceQuestManager.INSTANCE.getActiveSession(player);
            String damageType = event.getSource() != null ? event.getSource().type().msgId() : "unknown";
            if (sessionOpt.isPresent()) {
                var session = sessionOpt.get();
                LOGGER.info("[EnduranceQuest][DeathEvent] player={}, damageType={}, dimension={}, questState={}, awaitingRespawn={}, respawnRequested={}, wave={}, questId={}, instanceId={}",
                    player.getName().getString(),
                    damageType,
                    player.level().dimension().location(),
                    session.getQuest().getState(),
                    session.isAwaitingRespawnChoice(),
                    session.isRespawnRequested(),
                    session.getQuest().getCurrentWave(),
                    session.getQuest().getQuestId(),
                    session.getInstanceId());
            } else {
                boolean inInstance = com.devmod.runtime.InstanceManager.INSTANCE.isPlayerInInstance(player.getUUID());
                LOGGER.info("[EnduranceQuest][DeathEvent] player={}, damageType={}, dimension={}, hasSession=false, inInstance={}",
                    player.getName().getString(),
                    damageType,
                    player.level().dimension().location(),
                    inInstance);
            }
            EnduranceEventCombat.handlePlayerDeath(player);
        }
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (EnduranceQuestManager.INSTANCE.getActiveSession(player).isPresent()) {
                event.getDrops().clear();
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        EnduranceEventTick.onServerTick();
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            KitManager.INSTANCE.restoreSyncedKits(player);
            var sessionOpt = EnduranceQuestManager.INSTANCE.getActiveSession(player);
            if (sessionOpt.isPresent()) {
                var session = sessionOpt.get();
                if (session.getPartyId() != null && session.isPartySpectator()) {
                    EnduranceQuestManager.INSTANCE.rejoinPartyMember(player);
                }
            }

            var party = com.devmod.party.PartyManager.INSTANCE.getPlayerParty(player.getUUID());
            if (party != null && party.getState() != com.devmod.party.PartyData.PartyState.IN_QUEST) {
                UUID leaderId = party.getLeaderId();
                if (!leaderId.equals(player.getUUID())) {
                    MinecraftServer server = player.getServer();
                    if (server != null && server.getPlayerList().getPlayer(leaderId) == null) {
                        if (com.devmod.party.PartyManager.INSTANCE.transferLeadership(leaderId, player.getUUID())) {
                            com.devmod.network.handlers.PartyNetworkHandler.syncPartyToAllMembers(server, party.getPartyId());
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID playerId = player.getUUID();

            QuestStartSequence.INSTANCE.onPlayerDisconnect(playerId);
            NutritionBridgeSystem.INSTANCE.onPlayerDisconnect(playerId);

            Optional<EnduranceQuestManager.ActiveQuestSession> sessionOpt =
                EnduranceQuestManager.INSTANCE.getActiveSession(player);

            if (sessionOpt.isPresent()) {
                LOGGER.info("[EnduranceQuest] Player {} logged out during quest, cleaning up...",
                    player.getName().getString());
                var session = sessionOpt.get();
                if (session.getPartyId() != null
                    && EnduranceQuestManager.INSTANCE.getPartySession(session.getPartyId())
                        .map(PartyQuestSession::isActive).orElse(false)) {
                    EnduranceQuestManager.INSTANCE.markPartyMemberInactive(playerId, "disconnect");
                } else {
                    EnduranceQuestManager.INSTANCE.abandonQuest(player);
                }
            }

            var party = com.devmod.party.PartyManager.INSTANCE.getPlayerParty(playerId);
            if (party != null) {
                UUID partyId = party.getPartyId();
                boolean wasLeader = party.isLeader(playerId);
                boolean partyRunActive = EnduranceQuestManager.INSTANCE.getPartySession(partyId)
                    .map(PartyQuestSession::isActive).orElse(false);

                if (!partyRunActive) {
                    com.devmod.party.PartyManager.INSTANCE.handlePlayerDisconnect(playerId);
                }

                var server = player.getServer();
                if (server != null) {
                    var remainingParty = com.devmod.party.PartyManager.INSTANCE.getParty(partyId);
                    if (remainingParty != null) {
                        com.devmod.network.handlers.PartyNetworkHandler.syncPartyToAllMembers(server, partyId);
                        if (wasLeader) {
                            LOGGER.info("[Party] Leadership transferred due to disconnect, new leader: {}",
                                remainingParty.getLeaderName());
                        }
                    }
                }
            }

            if (AggregationConfig.AGGREGATION_ENABLED) {
                TelemetryAggregatorRegistry.INSTANCE.onPlayerLeave(playerId);
            }

            KitManager.INSTANCE.clearSyncedKits(playerId);
            com.devmod.arena.builder.ArenaBuilder.cleanupPlayerRateLimiter(playerId);
        }
    }

    // ==============================================================
    // PUBLIC GETTERS FOR UI
    // ==============================================================

    public static IComboSession getComboSession(UUID playerId) {
        return ComboSystemFacade.get().getSession(playerId).orElse(null);
    }

    public static MutatorSystem.MutatorSession getMutatorSession(UUID questId) {
        return EnduranceEventCombat.getMutatorSession(questId);
    }

    public static boolean canPlayerLeaveArena(ServerPlayer player) {
        return EnduranceEventTick.canPlayerLeaveArena(player);
    }

    public static void enforceArenaConfinement(ServerPlayer player) {
        EnduranceEventTick.enforceArenaConfinement(player);
    }
}
