package com.frenkvs.devmod.endurance;

import com.frenkvs.devmod.util.I18n;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.neoforge.network.PacketDistributor;

import com.frenkvs.devmod.endurance.analytics.LiveAnalyticsHookManager;
import com.frenkvs.devmod.endurance.analytics.WaveSummary;
import com.frenkvs.devmod.endurance.analytics.QuestResult;
import com.frenkvs.devmod.party.QuestStartSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central Event Handler for the Endurance Quest system.
 *
 * This handler integrates all the subsystems:
 * - EnduranceQuestManager: Core quest logic
 * - ComboSystem: Style scoring (DMC-style)
 * - PerkSystem: Roguelike upgrades
 * - BossWaveSystem: Boss mechanics
 * - MutatorSystem: Dynamic modifiers
 * - RewardSystem: Loot and currency
 * - CombatTracker: Stats tracking
 */
@SuppressWarnings("null") // Minecraft/NeoForge API null-safety (Attributes, MobEffects, etc.)
@EventBusSubscriber(modid = "devmod")
public class EnduranceEventHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnduranceEventHandler.class);

    private static int tickCounter = 0;
    private static final int WAVE_CHECK_INTERVAL = 20; // Check every second
    private static final int ARENA_CLEANUP_INTERVAL = 40; // Check every 2 seconds
    private static final int MOB_VALIDATION_INTERVAL = 60; // Check every 3 seconds

    // Track combo sessions per player
    private static final Map<UUID, ComboSystem.ComboSession> comboSessions = new ConcurrentHashMap<>();

    // Track mutator sessions per quest
    private static final Map<UUID, MutatorSystem.MutatorSession> mutatorSessions = new ConcurrentHashMap<>();

    // ========== Quest Lifecycle ==========

    /**
     * Initialize all systems when a quest starts.
     * Called by EnduranceQuestManager.startQuest()
     */
    public static void onQuestStart(ServerPlayer player, EnduranceQuestManager.ActiveQuestSession session) {
        UUID playerId = player.getUUID();
        UUID questId = session.getQuest().getQuestId();

        // Create combo session for DMC-style scoring
        ComboSystem.ComboSession comboSession = ComboSystem.INSTANCE.startSession(playerId, questId);
        comboSessions.put(playerId, comboSession);

        // Create mutator session with random mutators
        MutatorSystem.MutatorSession mutatorSession = MutatorSystem.INSTANCE.createSession(questId, 3, 1);
        mutatorSessions.put(questId, mutatorSession);

        // Create perk session for roguelike upgrades
        PerkSystem.INSTANCE.startSession(playerId, questId);

        // Create combat tracking session
        CombatTracker.INSTANCE.startTracking(questId, playerId, session.getQuest().getMobConfig().mobId);

        // Start live analytics session for real-time feedback hooks
        LiveAnalyticsHookManager.INSTANCE.onQuestStart(questId, playerId);

        LOGGER.info("[EnduranceQuest] Quest started for {} with {} mutators",
            player.getName().getString(), mutatorSession.getActiveMutatorCount());
    }

    /**
     * Cleanup all systems when a quest ends.
     * Called when quest completes, fails, or is abandoned.
     */
    public static void onQuestEnd(ServerPlayer player, EnduranceQuestManager.ActiveQuestSession session,
                                   boolean completed) {
        UUID playerId = player.getUUID();
        UUID questId = session.getQuest().getQuestId();

        // Get sessions before cleanup
        ComboSystem.ComboSession comboSession = comboSessions.remove(playerId);
        MutatorSystem.MutatorSession mutatorSession = mutatorSessions.remove(questId);

        // Get max combo before cleanup
        int maxCombo = comboSession != null ? comboSession.getMaxCombo() : 0;

        // Award rewards based on performance
        RewardSystem.QuestRewards rewards = RewardSystem.INSTANCE.calculateQuestRewards(
            player, session.getQuest(), comboSession, mutatorSession);

        // Send completion screen to client (only if quest completed or exited at checkpoint)
        if (rewards != null) {
            com.frenkvs.devmod.NetworkHandler.sendQuestCompletionScreen(
                player, session.getQuest(), rewards, comboSession, maxCombo);

            // Send token gain overlay animation
            if (rewards.tokensEarned > 0) {
                com.frenkvs.devmod.NetworkHandler.sendTokenGain(player, rewards.tokensEarned);
            }
        }

        // Cleanup combo system
        ComboSystem.INSTANCE.endSession(playerId);

        // Cleanup mutator system
        MutatorSystem.INSTANCE.endSession(questId);

        // Cleanup perk system (removes applied attribute modifiers)
        PerkSystem.INSTANCE.endSession(player);

        // Finalize and stop combat tracking
        CombatTracker.QuestCombatSession combatSessionData = CombatTracker.INSTANCE.getSession(questId).orElse(null);
        CombatTracker.INSTANCE.stopTracking(questId);

        // Record gamification stats (leaderboards, badges, challenges)
        if (completed && combatSessionData != null) {
            GamificationManager.QuestCompletionResult gamificationResult =
                GamificationManager.INSTANCE.recordQuestCompletion(
                    playerId,
                    player.getName().getString(),
                    session.getQuest(),
                    combatSessionData
                );

            // Send badge unlock notifications for newly earned badges
            for (GamificationManager.Badge badge : gamificationResult.newBadges) {
                com.frenkvs.devmod.NetworkHandler.sendBadgeUnlock(
                    player, badge.name, badge.rarity.displayName);
            }

            // Send record banner for new personal records
            if (gamificationResult.isNewWaveRecord) {
                com.frenkvs.devmod.NetworkHandler.sendRecordBanner(
                    player, "BEST WAVE", "Wave " + session.getQuest().getCurrentWave());
            }
            if (gamificationResult.isNewHighScore) {
                com.frenkvs.devmod.NetworkHandler.sendRecordBanner(
                    player, "HIGH SCORE", String.format("%,d pts", session.getQuest().getPointsEarnedThisSession()));
            }
        }

        // Record analytics session for detailed tracking
        if (combatSessionData != null) {
            EnduranceAnalytics.INSTANCE.recordSession(
                combatSessionData,
                session.getQuest(),
                player.getName().getString()
            );
        }

        // Notify live analytics hooks with quest result
        QuestResult result = buildQuestResult(
            questId, playerId, session.getQuest(), combatSessionData, comboSession, completed
        );
        LiveAnalyticsHookManager.INSTANCE.onQuestEnd(result);

        LOGGER.info("[EnduranceQuest] Quest ended for {} - Completed: {}, Style Rank: {}",
            player.getName().getString(), completed,
            comboSession != null ? comboSession.getHighestRank().displayName : "N/A");
    }

    // ========== Wave Events ==========

    /**
     * Called when a new wave starts.
     */
    public static void onWaveStart(ServerPlayer player, EnduranceQuestManager.ActiveQuestSession session, int waveNumber) {
        UUID playerId = player.getUUID();
        EnduranceQuest quest = session.getQuest();

        // Reset combo for new wave
        ComboSystem.ComboSession comboSession = comboSessions.get(playerId);
        if (comboSession != null) {
            comboSession.startNewWave();
        }

        // Check if this is a boss wave
        boolean isBossWave = BossWaveSystem.INSTANCE.isBossWave(waveNumber);

        if (isBossWave) {
            // Special boss wave announcement
            var bossFight = BossWaveSystem.INSTANCE.getBossFight(session.getArena().getId());
            String bossType = bossFight.map(bf -> bf.getArchetype().displayName).orElse("Champion");

            player.sendSystemMessage(I18n.translate("devmod.wave.boss_wave_divider")
                .withStyle(ChatFormatting.DARK_PURPLE));
            player.sendSystemMessage(I18n.translate("devmod.wave.boss_wave_title", waveNumber)
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            player.sendSystemMessage(I18n.translate("devmod.wave.boss_appeared", bossType, quest.getMobConfig().displayName)
                .withStyle(ChatFormatting.RED));
            player.sendSystemMessage(I18n.translate("devmod.wave.defeat_boss")
                .withStyle(ChatFormatting.GRAY));
            player.sendSystemMessage(I18n.translate("devmod.wave.boss_wave_divider")
                .withStyle(ChatFormatting.DARK_PURPLE));
        } else {
            // Normal wave announcement
            int mobCount = quest.getCurrentWaveMobCount();
            player.sendSystemMessage(I18n.translate("devmod.wave.normal_divider")
                .withStyle(ChatFormatting.DARK_RED));
            player.sendSystemMessage(I18n.translate("devmod.wave.wave_title", waveNumber, quest.getTotalWaves())
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            player.sendSystemMessage(I18n.translate("devmod.wave.enemies_count", mobCount, quest.getMobConfig().displayName)
                .withStyle(ChatFormatting.GRAY));
            player.sendSystemMessage(I18n.translate("devmod.wave.normal_divider")
                .withStyle(ChatFormatting.DARK_RED));
        }

        LOGGER.debug("[EnduranceQuest] Wave {} started for {} (boss: {})",
            waveNumber, player.getName().getString(), isBossWave);
    }

    /**
     * Called when a wave is completed.
     */
    public static void onWaveComplete(ServerPlayer player, EnduranceQuestManager.ActiveQuestSession session, int waveNumber) {
        UUID playerId = player.getUUID();
        UUID questId = session.getQuest().getQuestId();
        EnduranceQuest quest = session.getQuest();

        // Get tracking sessions
        ComboSystem.ComboSession comboSession = comboSessions.get(playerId);
        MutatorSystem.MutatorSession mutatorSession = mutatorSessions.get(questId);

        // Get combat stats for this wave
        CombatTracker.QuestCombatSession combatSession = CombatTracker.INSTANCE.getSession(questId).orElse(null);
        CombatTracker.WaveCombatStats waveStats = combatSession != null ? combatSession.getCurrentWaveStats() : null;

        // Calculate wave statistics
        String styleRank = comboSession != null ? comboSession.getCurrentRank().displayName : "D";
        int maxCombo = comboSession != null ? comboSession.getMaxCombo() : 0;
        float waveDamage = waveStats != null ? waveStats.damageDealt : 0;
        int waveKills = waveStats != null ? waveStats.kills : 0;
        float waveDamageTaken = waveStats != null ? waveStats.damageTaken : 0;

        // === DETAILED LOGGING ===
        LOGGER.info("[EnduranceQuest] Wave {} completed for quest {} by player {}",
            waveNumber, questId, player.getName().getString());
        LOGGER.info("[EnduranceQuest]   Combat Stats: {} damage dealt, {} kills, {} damage taken",
            String.format("%.1f", waveDamage), waveKills, String.format("%.1f", waveDamageTaken));
        LOGGER.info("[EnduranceQuest]   Style Stats: Rank {}, Max Combo {}", styleRank, maxCombo);
        if (mutatorSession != null) {
            LOGGER.info("[EnduranceQuest]   Active Mutators: {}", mutatorSession.getActiveMutatorCount());
        }

        // === COMBAT TRACKER - Prepare for next wave ===
        if (combatSession != null) {
            // Finalize current wave stats and prepare for next wave
            combatSession.startNewWave(waveNumber + 1);
            LOGGER.debug("[EnduranceQuest] Combat tracker advanced to wave {}", waveNumber + 1);
        }

        // === LIVE ANALYTICS - Notify hooks of wave transition ===
        WaveSummary waveSummary = buildWaveSummary(waveNumber, waveStats, comboSession);
        LiveAnalyticsHookManager.INSTANCE.onWaveComplete(waveNumber, waveSummary, waveNumber + 1);

        // === MUTATOR SYSTEM - Roll new mutators between waves ===
        if (mutatorSession != null && waveNumber % 3 == 0) {
            // Every 3 waves, potentially add a new mutator
            MutatorSystem.INSTANCE.rollNewMutator(mutatorSession);
            LOGGER.info("[EnduranceQuest] New mutator rolled at wave {}", waveNumber);
        }

        // === PERK SYSTEM - Generate perk choices for player ===
        List<PerkSystem.Perk> perkChoices = PerkSystem.INSTANCE.generatePerkChoices(player, waveNumber);
        if (!perkChoices.isEmpty()) {
            LOGGER.info("[EnduranceQuest] Generated {} perk choices for player {}: {}",
                perkChoices.size(), player.getName().getString(),
                perkChoices.stream().map(p -> p.name).toList());
            // Send perk choices to client for UI display
            com.frenkvs.devmod.NetworkHandler.sendPerkChoices(player, waveNumber, perkChoices);
        }

        // === NOTIFY PLAYER ===
        player.sendSystemMessage(I18n.translate("devmod.wave.complete_divider")
            .withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(I18n.translate("devmod.wave.complete_title", waveNumber)
            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        player.sendSystemMessage(I18n.translate("devmod.wave.style_rank_combo", styleRank, maxCombo)
            .withStyle(ChatFormatting.YELLOW));

        // Show wave combat summary
        player.sendSystemMessage(I18n.translate("devmod.wave.combat_divider")
            .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(I18n.translate("devmod.wave.damage_kills", String.format("%.0f", waveDamage), waveKills)
            .withStyle(ChatFormatting.WHITE));
        if (waveDamageTaken > 0) {
            player.sendSystemMessage(I18n.translate("devmod.wave.damage_taken", String.format("%.0f", waveDamageTaken))
                .withStyle(ChatFormatting.RED));
        } else {
            player.sendSystemMessage(I18n.translate("devmod.wave.flawless")
                .withStyle(ChatFormatting.AQUA));
        }


        if (quest.getCurrentWave() < quest.getTotalWaves() || quest.isEndlessMode()) {
            player.sendSystemMessage(I18n.translate("devmod.wave.checkpoint")
                .withStyle(ChatFormatting.AQUA));
            player.sendSystemMessage(I18n.translate("devmod.wave.continue_option", waveNumber + 1)
                .withStyle(ChatFormatting.WHITE));
            player.sendSystemMessage(I18n.translate("devmod.wave.exit_option")
                .withStyle(ChatFormatting.WHITE));
        } else {
            player.sendSystemMessage(I18n.translate("devmod.wave.quest_complete_final")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
        player.sendSystemMessage(I18n.translate("devmod.wave.complete_divider")
            .withStyle(ChatFormatting.GOLD));
    }

    // ========== Combat Events ==========

    /**
     * Handle damage dealt by players to mobs in quests.
     */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();
        float damage = event.getNewDamage();

        // Check if damage was dealt by a player
        if (source.getEntity() instanceof ServerPlayer player) {
            // Check if target is a quest mob
            if (isQuestMob(target)) {
                UUID playerId = player.getUUID();

                // Get body part if available (integration with body part system)
                String bodyPart = getBodyPartHit(target, source);

                // Record damage in combat tracker
                CombatTracker.INSTANCE.onPlayerDealsDamage(player, target, damage, source, bodyPart);

                // Record in quest manager
                EnduranceQuestManager.INSTANCE.recordDamageDealt(player, damage);

                // Process combo system - record hit
                ComboSystem.ComboSession comboSession = comboSessions.get(playerId);
                if (comboSession != null) {
                    // Determine action type based on damage
                    ComboSystem.ActionType actionType;
                    if (damage >= 20) {
                        actionType = ComboSystem.ActionType.HEAVY_ATTACK;
                    } else if (damage >= 10) {
                        actionType = ComboSystem.ActionType.LIGHT_ATTACK;
                    } else {
                        actionType = ComboSystem.ActionType.LIGHT_ATTACK;
                    }
                    comboSession.registerAction(actionType, damage);
                }

                // Process mutator effects
                if (target instanceof Mob mob) {
                    CompoundTag data = mob.getPersistentData();
                    UUID questId = data.contains("endurance_quest_id") ?
                        data.getUUID("endurance_quest_id") : null;

                    if (questId != null) {
                        MutatorSystem.MutatorSession mutatorSession = mutatorSessions.get(questId);
                        if (mutatorSession != null) {
                            // Mirror damage
                            float mirrorDamage = MutatorSystem.INSTANCE.getMirrorDamage(questId, damage);
                            if (mirrorDamage > 0) {
                                player.hurt(player.damageSources().magic(), mirrorDamage);
                            }
                        }
                    }

                    // Check for fire aspect modifier
                    if (data.getBoolean("endurance_fire_aspect")) {
                        player.igniteForSeconds(3);
                    }
                }
            }
        }

        // Check if player took damage while in quest
        if (target instanceof ServerPlayer player) {
            Optional<EnduranceQuestManager.ActiveQuestSession> session =
                EnduranceQuestManager.INSTANCE.getActiveSession(player);

            if (session.isPresent()) {
                UUID playerId = player.getUUID();

                // Note: damage reduction from perks should be applied in LivingDamageEvent.Pre
                // Here we just track it for statistics
                CombatTracker.INSTANCE.onPlayerTakesDamage(player, damage, source);
                EnduranceQuestManager.INSTANCE.recordDamageTaken(player, damage);

                // Combo penalty on getting hit
                ComboSystem.ComboSession comboSession = comboSessions.get(playerId);
                if (comboSession != null) {
                    comboSession.onDamageTaken(damage);

                    // Check for pending combo decay notification
                    if (comboSession.hasPendingDecayNotification()) {
                        int lostCombo = comboSession.consumePendingComboLost();
                        ComboSystem.StyleRank[] rankChange = comboSession.consumePendingRankChange();
                        int prevRank = rankChange != null ? rankChange[0].ordinal() : comboSession.getCurrentRank().ordinal();
                        int newRank = rankChange != null ? rankChange[1].ordinal() : comboSession.getCurrentRank().ordinal();

                        com.frenkvs.devmod.NetworkHandler.sendComboDecay(player, lostCombo, prevRank, newRank);
                    }
                }
            }
        }
    }

    /**
     * Handle damage reduction from perks (Pre event to modify damage).
     */
    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();

        // Only process for players in quests
        if (target instanceof ServerPlayer player) {
            Optional<EnduranceQuestManager.ActiveQuestSession> session =
                EnduranceQuestManager.INSTANCE.getActiveSession(player);

            if (session.isPresent()) {
                float originalDamage = event.getOriginalDamage();

                // Apply perk damage reduction
                float modifiedDamage = PerkSystem.INSTANCE.processDamageTaken(player, originalDamage);

                // Set the new damage amount
                event.setNewDamage(modifiedDamage);
            }
        }
    }

    /**
     * Handle critical hits.
     */
    @SubscribeEvent
    public static void onCriticalHit(CriticalHitEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.isVanillaCritical()) {
            Entity target = event.getTarget();
            if (isQuestMob(target)) {
                float damage = event.getDamageMultiplier() * player.getAttackStrengthScale(0.5f);
                CombatTracker.INSTANCE.onCriticalHit(player, damage);

                // Bonus combo points for critical hits
                UUID playerId = player.getUUID();
                ComboSystem.ComboSession comboSession = comboSessions.get(playerId);
                if (comboSession != null) {
                    comboSession.registerAction(ComboSystem.ActionType.CRITICAL_HIT, damage);
                }
            }
        }
    }

    /**
     * Handle mob deaths in quests.
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();

        // Check if this is a quest mob
        if (isQuestMob(entity)) {
            CompoundTag data = entity.getPersistentData();
            UUID arenaId = data.contains("endurance_arena_id") ? data.getUUID("endurance_arena_id") : null;
            UUID questId = data.contains("endurance_quest_id") ? data.getUUID("endurance_quest_id") : null;

            // Get the mob type
            ResourceLocation mobId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());

            // Find who killed it
            DamageSource source = event.getSource();
            if (source.getEntity() instanceof ServerPlayer player) {
                UUID playerId = player.getUUID();

                // Record kill in quest manager
                EnduranceQuestManager.INSTANCE.recordKill(player, mobId);

                // Record in combat tracker
                CombatTracker.INSTANCE.onMobKilled(player, entity);

                // Record kill in combo system
                ComboSystem.ComboSession comboSession = comboSessions.get(playerId);
                if (comboSession != null) {
                    comboSession.registerKill(false, 0); // Basic kill
                }

                // Process mutator death effects (exploding mobs, etc.)
                if (questId != null && entity instanceof Mob mob) {
                    MutatorSystem.INSTANCE.onMobDeath(questId, mob, player);
                }

                // Process perk on-kill effects (lifesteal, blood frenzy, etc.)
                PerkSystem.INSTANCE.processKill(player);

                // Notify wave manager
                if (arenaId != null) {
                    WaveManager.INSTANCE.handleMobDeath(entity.getUUID(), arenaId);

                    // Check if wave is complete
                    if (WaveManager.INSTANCE.isWaveComplete(arenaId)) {
                        EnduranceQuestManager.INSTANCE.getActiveSession(player).ifPresent(session -> {
                            onWaveComplete(player, session, session.getQuest().getCurrentWave());
                        });
                        EnduranceQuestManager.INSTANCE.completeWave(player);
                    }
                }
            } else {
                // Mob died from external cause (not player kill)
                // Log for debugging - the periodic validation will handle respawning
                String deathCause = source.type().msgId();
                LOGGER.debug("[EnduranceQuest] Quest mob {} died from external cause: {} (arena: {})",
                    mobId, deathCause, arenaId);

                // Note: We do NOT call handleMobDeath here because we want the validation
                // system to detect this as a "missing" mob and respawn it.
                // This ensures the wave can't be completed by environmental deaths.
            }
        }

        // Check if a player in a quest died
        if (entity instanceof ServerPlayer player) {
            Optional<EnduranceQuestManager.ActiveQuestSession> session =
                EnduranceQuestManager.INSTANCE.getActiveSession(player);

            if (session.isPresent()) {
                CombatTracker.INSTANCE.onPlayerDeath(player);
                EnduranceQuestManager.INSTANCE.handlePlayerDeath(player);
            }
        }
    }

    /**
     * Server tick handler for wave management and quest updates.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;

        // Update combo decay every tick
        comboSessions.values().forEach(ComboSystem.ComboSession::tick);

        // Tick live analytics hooks (throttled internally to 1/sec)
        LiveAnalyticsHookManager.INSTANCE.tick();

        // Tick quest start sequences (countdown, validation, teleport) and quest-related updates
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            QuestStartSequence.INSTANCE.tick(server);

            // Tick perk effects and enforce arena confinement for all players in active quests
            for (UUID playerId : EnduranceQuestManager.INSTANCE.getActiveSessions().keySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    PerkSystem.INSTANCE.tick(player);

                    // Check arena boundaries every 10 ticks (0.5 seconds)
                    if (tickCounter % 10 == 0) {
                        enforceArenaConfinement(player);
                    }
                }
            }
        }

        // Arena cleanup - remove external mobs (every 2 seconds)
        if (tickCounter % ARENA_CLEANUP_INTERVAL == 0) {
            cleanupExternalMobsInArenas();
        }

        // Mob validation - check wave mobs are alive (every 3 seconds)
        if (tickCounter % MOB_VALIDATION_INTERVAL == 0) {
            validateAndRespawnWaveMobs();
        }

        // Periodic checks (every second)
        if (tickCounter >= WAVE_CHECK_INTERVAL) {
            tickCounter = 0;

            // Sync quest state to all players with active quests
            syncQuestStateToClients();

            // Check gamification resets every minute (60 ticks * 20 = 1200 ticks)
            gamificationTickCounter++;
            if (gamificationTickCounter >= 60) {
                gamificationTickCounter = 0;
                GamificationManager.INSTANCE.tickResets();
            }
        }
    }

    private static int gamificationTickCounter = 0;

    /**
     * Send quest state sync packets to all players with active quests.
     */
    private static void syncQuestStateToClients() {
        for (Map.Entry<UUID, EnduranceQuestManager.ActiveQuestSession> entry :
                EnduranceQuestManager.INSTANCE.getActiveSessions().entrySet()) {

            UUID playerId = entry.getKey();
            EnduranceQuestManager.ActiveQuestSession session = entry.getValue();
            EnduranceQuest quest = session.getQuest();

            // Find the player
            var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server == null) continue;

            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) continue;

            // Build modifier list
            List<String> modifiers = new ArrayList<>();
            var waveStateOpt = WaveManager.INSTANCE.getWaveState(session.getArena().getId());
            if (waveStateOpt.isPresent()) {
                for (WaveManager.WaveModifier mod : waveStateOpt.get().getModifiers()) {
                    modifiers.add(mod.displayName);
                }
            }

            // Get combo session data
            ComboSystem.ComboSession comboSession = comboSessions.get(playerId);
            int currentCombo = comboSession != null ? comboSession.getCurrentCombo() : 0;
            int maxCombo = comboSession != null ? comboSession.getMaxCombo() : 0;
            int styleScore = comboSession != null ? comboSession.getStyleScore() : 0;
            int styleRankOrdinal = comboSession != null ? comboSession.getCurrentRank().ordinal() : 0;

            // Get wave progress
            int mobsKilledInWave = waveStateOpt.map(WaveManager.WaveState::getKilled).orElse(0);
            int totalMobsInWave = waveStateOpt.map(WaveManager.WaveState::getTotalToSpawn).orElse(quest.getCurrentWaveMobCount());

            // Create sync payload
            QuestSyncPayload payload = new QuestSyncPayload(
                true,
                quest.getDisplayName(),
                quest.getCurrentWave(),
                quest.getTotalWaves(),
                quest.isEndlessMode(),
                quest.getPointsEarnedThisSession(),
                quest.getMobsKilledThisSession(),
                mobsKilledInWave,
                totalMobsInWave,
                quest.getTotalDamageDealtThisSession(),
                (int) quest.getDamageTakenThisSession(),
                quest.getDeathsThisSession(),
                quest.getSessionDuration(),
                quest.getState().ordinal(),
                modifiers,
                currentCombo,
                maxCombo,
                styleScore,
                styleRankOrdinal
            );

            // Send to player
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    // ========== Utility Methods ==========

    /**
     * Check if an entity is a quest mob.
     */
    private static boolean isQuestMob(Entity entity) {
        if (entity instanceof Mob mob) {
            CompoundTag data = mob.getPersistentData();
            return data.contains("endurance_quest_id");
        }
        return false;
    }

    /**
     * Get the body part that was hit (integration with body part system).
     */
    private static String getBodyPartHit(LivingEntity target, DamageSource source) {
        // Try to get attacker from damage source
        Entity attacker = source.getEntity();
        if (attacker instanceof LivingEntity livingAttacker) {
            // Use HitHelper's raycast-based body part detection
            com.frenkvs.devmod.HitHelper.BodyPart bodyPart =
                com.frenkvs.devmod.HitHelper.rayTraceBodyPartAABB(livingAttacker, target);
            return bodyPart.name();
        }

        // Fallback: try to use direct hit position if available
        Entity directCause = source.getDirectEntity();
        if (directCause != null) {
            // For projectiles, use their Y position relative to target
            double hitY = directCause.getY();
            com.frenkvs.devmod.HitHelper.BodyPart bodyPart =
                com.frenkvs.devmod.HitHelper.getBodyPart(target, hitY);
            return bodyPart.name();
        }

        return "BODY"; // Default fallback
    }

    /**
     * Check if player is trying to leave the arena.
     */
    public static boolean canPlayerLeaveArena(ServerPlayer player) {
        Optional<EnduranceQuestManager.ActiveQuestSession> session =
            EnduranceQuestManager.INSTANCE.getActiveSession(player);

        if (session.isPresent()) {
            EnduranceQuestState state = session.get().getQuest().getState();
            // Can only leave during checkpoint (between waves)
            return state == EnduranceQuestState.WAVE_COMPLETE;
        }

        return true;
    }

    /**
     * Teleport player back to arena if they try to escape.
     */
    public static void enforceArenaConfinement(ServerPlayer player) {
        Optional<EnduranceQuestManager.ActiveQuestSession> session =
            EnduranceQuestManager.INSTANCE.getActiveSession(player);

        if (session.isPresent()) {
            ArenaManager.Arena arena = session.get().getArena();
            if (!arena.contains(player.position())) {
                // Teleport back to center
                player.teleportTo(
                    arena.getCenter().getX() + 0.5,
                    arena.getCenter().getY(),
                    arena.getCenter().getZ() + 0.5
                );
            }
        }
    }

    /**
     * Remove any non-quest mobs that spawn inside active arenas.
     * This prevents external mobs from interfering with the quest.
     */
    private static void cleanupExternalMobsInArenas() {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (EnduranceQuestManager.ActiveQuestSession session :
                EnduranceQuestManager.INSTANCE.getActiveSessions().values()) {

            ArenaManager.Arena arena = session.getArena();
            net.minecraft.server.level.ServerLevel level = arena.getLevel();

            // Get all entities in arena bounds
            List<Entity> entitiesInArena = level.getEntities(
                (Entity) null,
                arena.getBounds(),
                entity -> entity instanceof Mob
            );

            for (Entity entity : entitiesInArena) {
                if (entity instanceof Mob mob) {
                    CompoundTag data = mob.getPersistentData();

                    // Check if this mob belongs to our quest
                    boolean isQuestMob = data.contains("endurance_quest_id") &&
                        data.contains("endurance_arena_id") &&
                        data.getUUID("endurance_arena_id").equals(arena.getId());

                    if (!isQuestMob) {
                        // This is an external mob - remove it
                        mob.discard();
                        LOGGER.debug("[EnduranceQuest] Removed external mob {} from arena {}",
                            mob.getType().getDescriptionId(), arena.getId());
                    }
                }
            }
        }
    }

    /**
     * Validate that wave mobs are still alive. If mobs died from external causes
     * (not player kills), respawn them to ensure wave can be completed.
     */
    private static void validateAndRespawnWaveMobs() {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (Map.Entry<UUID, EnduranceQuestManager.ActiveQuestSession> entry :
                EnduranceQuestManager.INSTANCE.getActiveSessions().entrySet()) {

            EnduranceQuestManager.ActiveQuestSession session = entry.getValue();
            EnduranceQuest quest = session.getQuest();

            // Only validate during active wave combat
            if (quest.getState() != EnduranceQuestState.IN_PROGRESS) continue;

            ArenaManager.Arena arena = session.getArena();
            Optional<WaveManager.WaveState> waveStateOpt = WaveManager.INSTANCE.getWaveState(arena.getId());

            if (waveStateOpt.isEmpty()) continue;

            WaveManager.WaveState waveState = waveStateOpt.get();
            net.minecraft.server.level.ServerLevel level = arena.getLevel();

            // Count how many spawned mobs are still alive
            int aliveMobs = 0;
            List<UUID> deadMobIds = new ArrayList<>();

            for (UUID mobId : waveState.getSpawnedMobs()) {
                Entity entity = level.getEntity(mobId);
                if (entity != null && entity.isAlive()) {
                    aliveMobs++;
                } else {
                    deadMobIds.add(mobId);
                }
            }

            // Calculate expected alive mobs (spawned - killed by player)
            int expectedAlive = waveState.getSpawned() - waveState.getKilled();

            // If we have fewer alive mobs than expected, some died from external causes
            int missingMobs = expectedAlive - aliveMobs;

            if (missingMobs > 0) {
                LOGGER.warn("[EnduranceQuest] Wave {} has {} missing mobs (external deaths). Respawning...",
                    waveState.getWaveNumber(), missingMobs);

                // Respawn the missing mobs
                respawnMissingWaveMobs(session, waveState, missingMobs, deadMobIds);
            }
        }
    }

    /**
     * Respawn mobs that died from external causes (not player kills).
     * Applies the same wave modifiers as original spawns.
     */
    private static void respawnMissingWaveMobs(
            EnduranceQuestManager.ActiveQuestSession session,
            WaveManager.WaveState waveState,
            int count,
            List<UUID> deadMobIds) {

        ArenaManager.Arena arena = session.getArena();
        net.minecraft.server.level.ServerLevel level = arena.getLevel();
        EnduranceQuestRegistry.MobQuestConfig mobConfig = waveState.getQuest().getMobConfig();
        net.minecraft.world.entity.EntityType<?> entityType = mobConfig.entityType;

        List<net.minecraft.core.BlockPos> spawnPositions = arena.getDistributedSpawnPositions(count);
        int successfulRespawns = 0;

        for (int i = 0; i < count; i++) {
            // Use modulo to reuse positions if we don't have enough
            net.minecraft.core.BlockPos spawnPos = spawnPositions.get(i % Math.max(1, spawnPositions.size()));

            Entity entity = entityType.create(level);
            if (entity instanceof Mob mob) {
                // Position the mob
                mob.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

                // Apply wave modifiers (same as original spawns)
                applyWaveModifiersToMob(mob, waveState);

                // Finalize spawn
                @SuppressWarnings({"deprecation", "unused"})
                var spawnData = mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos),
                    net.minecraft.world.entity.MobSpawnType.MOB_SUMMONED, null);

                // Tag mob as quest mob BEFORE adding to world
                CompoundTag tag = mob.getPersistentData();
                tag.putUUID("endurance_quest_id", waveState.getQuest().getQuestId());
                tag.putUUID("endurance_arena_id", arena.getId());
                tag.putBoolean("endurance_respawned", true); // Mark as respawned

                // Add to world and verify success
                boolean added = level.addFreshEntity(mob);

                if (added && mob.isAlive()) {
                    // Replace dead mob UUID with new one in wave state tracking
                    if (i < deadMobIds.size()) {
                        waveState.getSpawnedMobs().remove(deadMobIds.get(i));
                    }
                    waveState.getSpawnedMobs().add(mob.getUUID());
                    successfulRespawns++;

                    LOGGER.info("[EnduranceQuest] Respawned {} at {} for wave {}",
                        entityType.getDescriptionId(), spawnPos, waveState.getWaveNumber());
                } else {
                    LOGGER.warn("[EnduranceQuest] Failed to respawn mob at {}", spawnPos);
                }
            }
        }

        // Notify player about respawned mobs
        if (successfulRespawns > 0) {
            UUID playerId = session.getPlayerId();
            var serverInstance = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            ServerPlayer player = serverInstance != null ? serverInstance.getPlayerList().getPlayer(playerId) : null;
            if (player != null) {
                player.sendSystemMessage(I18n.translate("devmod.endurance.mobs_respawned", successfulRespawns)
                    .withStyle(ChatFormatting.YELLOW));
            }
        }
    }

    /**
     * Apply wave modifiers to a respawned mob (mirrors WaveManager.applyMobModifiers).
     */
    private static void applyWaveModifiersToMob(Mob mob, WaveManager.WaveState waveState) {
        for (WaveManager.WaveModifier modifier : waveState.getModifiers()) {
            switch (modifier) {
                case SPEED_BOOST -> {
                    var speedAttr = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
                    if (speedAttr != null) {
                        speedAttr.setBaseValue(speedAttr.getBaseValue() * 1.25);
                    }
                }
                case DAMAGE_BOOST -> {
                    var attackAttr = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                    if (attackAttr != null) {
                        attackAttr.setBaseValue(attackAttr.getBaseValue() * 1.25);
                    }
                }
                case HEALTH_BOOST -> {
                    var healthAttr = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                    if (healthAttr != null) {
                        healthAttr.setBaseValue(healthAttr.getBaseValue() * 1.5);
                        mob.setHealth(mob.getMaxHealth());
                    }
                }
                case ARMOR_BOOST -> {
                    var armorAttr = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
                    if (armorAttr != null) {
                        armorAttr.setBaseValue(armorAttr.getBaseValue() + 8);
                    }
                }
                case FIRE_ASPECT -> {
                    mob.getPersistentData().putBoolean("endurance_fire_aspect", true);
                }
                case INVISIBILITY -> {
                    mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
                }
                case REGEN -> {
                    mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.REGENERATION, Integer.MAX_VALUE, 0, false, false));
                }
                case DOUBLE_SPAWN -> {
                    // Not applicable for individual mob spawns
                }
            }
        }
    }

    // ========== Player Events ==========

    /**
     * Handle player logout - cleanup quest session.
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Notify QuestStartSequence about disconnect (cancels sequence if needed)
            QuestStartSequence.INSTANCE.onPlayerDisconnect(player.getUUID());

            Optional<EnduranceQuestManager.ActiveQuestSession> sessionOpt =
                EnduranceQuestManager.INSTANCE.getActiveSession(player);

            if (sessionOpt.isPresent()) {
                LOGGER.info("[EnduranceQuest] Player {} logged out during quest, cleaning up...",
                    player.getName().getString());

                // Treat as abandonment
                EnduranceQuestManager.INSTANCE.abandonQuest(player);
            }
        }
    }

    // ========== Public Getters for UI ==========

    public static ComboSystem.ComboSession getComboSession(UUID playerId) {
        return comboSessions.get(playerId);
    }

    public static MutatorSystem.MutatorSession getMutatorSession(UUID questId) {
        return mutatorSessions.get(questId);
    }

    // ========== Analytics Helper Methods ==========

    /**
     * Builds a WaveSummary from combat and combo data.
     */
    private static WaveSummary buildWaveSummary(int waveNumber,
            CombatTracker.WaveCombatStats waveStats,
            ComboSystem.ComboSession comboSession) {

        if (waveStats == null) {
            return WaveSummary.empty(waveNumber);
        }

        return new WaveSummary(
            waveNumber,
            waveStats.duration,
            waveStats.damageDealt,
            waveStats.damageTaken,
            waveStats.kills,
            waveStats.deaths,
            waveStats.criticalHits,
            waveStats.hitsLanded,
            comboSession != null ? comboSession.getMaxCombo() : 0,
            comboSession != null ? comboSession.getHighestRank().name() : "D",
            "unknown", // primaryWeapon - could be tracked in future
            Map.of() // bodyPartHits - retrieved from CombatTracker if needed
        );
    }

    /**
     * Builds a QuestResult from all tracking data.
     */
    private static QuestResult buildQuestResult(UUID questId, UUID playerId,
            EnduranceQuest quest,
            CombatTracker.QuestCombatSession combatSession,
            ComboSystem.ComboSession comboSession,
            boolean completed) {

        // Extract data with null safety
        float totalDamageDealt = combatSession != null ? combatSession.getTotalDamageDealt() : 0;
        float totalDamageTaken = combatSession != null ? combatSession.getTotalDamageTaken() : 0;
        int totalKills = combatSession != null ? combatSession.getKills() : 0;
        int deaths = combatSession != null ? combatSession.getDeaths() : 0;
        int totalHits = combatSession != null ? combatSession.getTotalHitsLanded() : 0;
        int criticalHits = combatSession != null ? combatSession.getCriticalHits() : 0;
        long duration = combatSession != null ? combatSession.getSessionDuration() : 0;
        float dps = combatSession != null ? combatSession.getDPS() : 0;
        float critRate = combatSession != null ? combatSession.getCriticalHitRate() : 0;
        @SuppressWarnings("unused")
        float avgKillTime = combatSession != null ? combatSession.getAverageKillTime() : 0; // Reserved for future use

        int maxCombo = comboSession != null ? comboSession.getMaxCombo() : 0;
        String maxRank = comboSession != null ? comboSession.getHighestRank().name() : "D";

        // Body part stats
        Map<String, Integer> bodyPartHits = combatSession != null ?
            combatSession.getBodyPartHits() : Map.of();
        int headshots = bodyPartHits.getOrDefault("HEAD", 0);
        float headshotRate = totalHits > 0 ? (float) headshots / totalHits : 0;

        // Weapon stats
        Map<String, Float> weaponDamageMap = new java.util.HashMap<>();
        String bestWeapon = "unknown";
        float bestDamage = 0;
        if (combatSession != null) {
            for (var entry : combatSession.getWeaponStats().entrySet()) {
                weaponDamageMap.put(entry.getKey(), entry.getValue().totalDamage);
                if (entry.getValue().totalDamage > bestDamage) {
                    bestDamage = entry.getValue().totalDamage;
                    bestWeapon = entry.getKey();
                }
            }
        }

        // Wave times
        List<Long> waveTimes = new ArrayList<>();
        List<WaveSummary> waveSummaries = new ArrayList<>();
        if (combatSession != null) {
            for (CombatTracker.WaveCombatStats ws : combatSession.getWaveStats()) {
                waveTimes.add(ws.duration);
                waveSummaries.add(buildWaveSummary(ws.waveNumber, ws, comboSession));
            }
        }

        return new QuestResult(
            questId,
            playerId,
            quest.getQuestId().toString(),
            quest.getMobConfig().mobId.toString(),
            completed,
            quest.getCurrentWave(),
            quest.getTotalWaves(),
            completed ? null : "Quest not completed",
            duration,
            waveTimes,
            totalDamageDealt,
            totalDamageTaken,
            totalKills,
            deaths,
            totalHits,
            criticalHits,
            dps,
            critRate,
            headshotRate,
            maxCombo,
            maxRank,
            bestWeapon,
            bestWeapon,
            weaponDamageMap,
            waveSummaries,
            0, // xpEarned - set by reward system
            0, // coinsEarned - set by reward system
            List.of() // achievementsUnlocked - set by gamification
        );
    }
}
