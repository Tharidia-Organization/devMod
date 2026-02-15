package com.devmod.endurance;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import com.devmod.arena.api.ArenaHandle;
import com.devmod.combat.bridge.CombatEnduranceBridge;
import com.devmod.compat.mods.dummmmmmy.DummmmmmyCompat;
import com.devmod.compat.mods.easydiet.EasyDietCompat;
import com.devmod.config.gamedesign.GameDesignConfigManager;
import com.devmod.endurance.challenges.DailyChallengeManager;
import com.devmod.endurance.combat.ComboSystemFacade;
import com.devmod.endurance.combat.api.IComboSession;
import com.devmod.endurance.nutrition.NutritionBridgeSystem;
import com.devmod.telemetry.endurance.EnduranceTelemetryService;

public class EnduranceEventCombat {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnduranceEventCombat.class);

    // Track mutator sessions per quest (shared with EnduranceEventHandler)
    static final Map<UUID, MutatorSystem.MutatorSession> mutatorSessions = new ConcurrentHashMap<>();

    private static final long CRITICAL_KILL_WINDOW_MS = 3000L;
    private static final Map<UUID, CriticalHitMarker> lastCriticalHits = new ConcurrentHashMap<>();

    // ===============================================================
    // DAMAGE HANDLING
    // ===============================================================

    /**
     * Handle damage dealt by players to mobs in quests (Post event for tracking).
     */
    public static void handleDamagePost(LivingEntity target, DamageSource source, float damage) {
        // Check if damage was dealt by a player
        if (source.getEntity() instanceof ServerPlayer player) {
            // Check if target is a quest mob
            boolean questMob = isQuestMob(target);
            // Diagnostic logging for combat debugging
            if (!questMob && target instanceof Mob) {
                var session = EnduranceQuestManager.INSTANCE.getActiveSession(player).orElse(null);
                if (session != null) {
                    var data = target.getPersistentData();
                    boolean hasQuestId = data.contains(EnduranceTags.QUEST_ID);
                    boolean hasArenaId = data.contains(EnduranceTags.ARENA_ID);
                    LOGGER.info("[CombatDebug] Player {} hit non-quest mob {} (hasQuestId={}, hasArenaId={}, mobType={}, damage={}, playerInInstance={})",
                        player.getName().getString(),
                        target.getType().toString(),
                        hasQuestId,
                        hasArenaId,
                        target.getClass().getSimpleName(),
                        damage,
                        session.isInInstanceDimension());
                }
            }
            if (questMob) {
                UUID playerId = player.getUUID();
                EnduranceQuestManager.ActiveQuestSession session =
                    EnduranceQuestManager.INSTANCE.getActiveSession(player).orElse(null);
                boolean signatureEnabled = isSignatureWeaponsEnabled(session);
                boolean tideEnabled = isTideEnabled(session);
                UUID tideScopeId = resolveDesignScopeId(session);

                // Get body part if available (integration with body part system)
                String bodyPart = getBodyPartHit(target, source);

                // Record damage in combat tracker
                CombatTracker.INSTANCE.onPlayerDealsDamage(player, target, damage, source, bodyPart);

                // Record in quest manager
                EnduranceQuestManager.INSTANCE.recordDamageDealt(player, damage);

                if (signatureEnabled) {
                    // Record damage for signature weapon imprint
                    CombatEnduranceBridge.get().recordSoulDamage(player, damage);

                    // Check for headshot
                    if ("HEAD".equals(bodyPart)) {
                        CombatEnduranceBridge.get().recordSoulHeadshot(player);
                    }
                }

                // Process combo system - record hit
                IComboSession comboSession = ComboSystemFacade.isInitialized()
                    ? ComboSystemFacade.get().getSession(playerId).orElse(null) : null;
                if (comboSession != null) {
                    // Determine action type based on damage
                    ComboSystem.ActionType actionType =
                        damage >= 20 ? ComboSystem.ActionType.HEAVY_ATTACK : ComboSystem.ActionType.LIGHT_ATTACK;
                    IComboSession.ActionResult result = comboSession.registerAction(actionType, damage);

                    // Sync combat flow state to client
                    syncCombatFlowToClient(player, actionType.getDisplayName(), result.styleEarned());

                    // Update tension system with combo progress
                    if (target instanceof Mob mob) {
                        CompoundTag mobData = mob.getPersistentData();
                        if (mobData.contains(EnduranceTags.QUEST_ID)) {
                            UUID questId = mobData.getUUID(EnduranceTags.QUEST_ID);
                            TensionSystem.INSTANCE.onComboUpdate(questId, comboSession.getCurrentCombo(), comboSession.getCurrentRank());

                            // FIX #2: Track per-wave max combo in CombatTracker
                            CombatTracker.INSTANCE.getSession(questId).ifPresent(combatTrackerSession ->
                                combatTrackerSession.updatePlayerCombo(playerId, comboSession.getCurrentCombo())
                            );
                        }
                    }
                }

                // Process resonance chain system (party combo synergy)
                if (target instanceof Mob mob) {
                    CompoundTag mobData = mob.getPersistentData();
                    UUID resonanceQuestId = mobData.contains(EnduranceTags.QUEST_ID) ?
                        mobData.getUUID(EnduranceTags.QUEST_ID) : null;

                    if (resonanceQuestId != null) {
                        var resonanceResult = com.devmod.endurance.resonance.ResonanceChainSystem.INSTANCE
                            .recordHit(player, target, damage, resonanceQuestId);

                        // Apply damage multiplier if resonance triggered
                        if (resonanceResult.triggered()) {
                            // Additional damage is applied as bonus hit (uses same source to respect armor)
                            float bonusDamage = damage * (resonanceResult.damageMultiplier() - 1.0f);
                            if (bonusDamage > 0) {
                                target.hurt(source, bonusDamage);
                            }
                            // Resonance chains reduce global tide
                            if (tideEnabled) {
                                com.devmod.endurance.tide.TideManager.INSTANCE.onResonance(
                                    playerId, tideScopeId != null ? tideScopeId : resonanceQuestId);
                            }
                        }
                    }
                }

                // Process mutator effects
                if (target instanceof Mob mob) {
                    CompoundTag data = mob.getPersistentData();
                    UUID questId = data.contains(EnduranceTags.QUEST_ID) ?
                        data.getUUID(EnduranceTags.QUEST_ID) : null;

                    if (questId != null) {
                        MutatorSystem.MutatorSession mutatorSession = mutatorSessions.get(questId);
                        if (mutatorSession != null) {
                            // Mirror damage - use generic() to respect armor instead of magic()
                            float mirrorDamage = MutatorSystem.INSTANCE.getMirrorDamage(questId, damage);
                            if (mirrorDamage > 0) {
                                player.hurt(Objects.requireNonNull(player.damageSources().generic()), mirrorDamage);
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

                // Check if player is currently executing - interrupt it
                if (CombatEnduranceBridge.get().isExecuting(player)) {
                    CombatEnduranceBridge.get().interruptExecution(player);
                }

                // Note: damage reduction from perks should be applied in LivingDamageEvent.Pre
                // Here we just track it for statistics
                CombatTracker.INSTANCE.onPlayerTakesDamage(player, damage, source);
                EnduranceQuestManager.INSTANCE.recordDamageTaken(player, damage);

                // Check for Phoenix Rising comeback trigger
                float currentHealth = player.getHealth();
                float maxHealth = player.getMaxHealth();
                float newHealthPercent = currentHealth / maxHealth;
                float previousHealthPercent = (currentHealth + damage) / maxHealth;
                ComebackSystem.INSTANCE.checkAndTrigger(player, newHealthPercent, previousHealthPercent);

                // Notify tension system that player was hit (affects no-hit bonus)
                UUID questId = session.get().getQuest().getQuestId();
                TensionSystem.INSTANCE.onPlayerDamaged(questId);

                // Combo penalty on getting hit (notifications handled via NotificationComboListener)
                IComboSession comboSession = ComboSystemFacade.isInitialized()
                    ? ComboSystemFacade.get().getSession(playerId).orElse(null) : null;
                if (comboSession != null) {
                    comboSession.onDamageTaken(damage);
                }
            }
        }
    }

    /**
     * Handle damage reduction from perks (Pre event to modify damage).
     */
    public static float handleDamagePre(ServerPlayer player, float originalDamage) {
        Optional<EnduranceQuestManager.ActiveQuestSession> session =
            EnduranceQuestManager.INSTANCE.getActiveSession(player);

        if (session.isPresent()) {
            float damage = originalDamage;

            // Apply execution vulnerability if interrupted recently
            float vulnerabilityMult = CombatEnduranceBridge.get().getVulnerabilityMultiplier(player);
            if (vulnerabilityMult > 1.0f) {
                damage *= vulnerabilityMult;
                LOGGER.debug("[Combat] Execution vulnerability: {} -> {} ({}x)",
                    originalDamage, damage, vulnerabilityMult);
            }

            // Apply perk damage reduction
            damage = PerkSystem.INSTANCE.processDamageTaken(player, damage);

            // Apply nutrition-based damage modification (Easy-Diet integration)
            if (EasyDietCompat.isAvailable()) {
                float nutritionReduction = NutritionBridgeSystem.INSTANCE.getDamageReduction(player);
                if (Math.abs(nutritionReduction) > 0.001f) {
                    // Positive = reduction, negative = increased damage taken
                    damage *= (1.0f - nutritionReduction);
                    LOGGER.debug("[Combat] Nutrition modifier: {} ({}% change)",
                        nutritionReduction > 0 ? "well-fed" : "malnourished",
                        (int) (nutritionReduction * 100));
                }
            }

            return damage;
        }
        return originalDamage;
    }

    // ===============================================================
    // CRITICAL HITS
    // ===============================================================

    /**
     * Handle critical hits.
     */
    public static void handleCriticalHit(ServerPlayer player, Entity target, float damage) {
        if (isQuestMob(target)) {
            UUID playerId = player.getUUID();
            CombatTracker.INSTANCE.onCriticalHit(player, damage);

            // Record for signature weapon imprint
            EnduranceQuestManager.ActiveQuestSession session =
                EnduranceQuestManager.INSTANCE.getActiveSession(player).orElse(null);
            if (isSignatureWeaponsEnabled(session)) {
                CombatEnduranceBridge.get().recordSoulCriticalHit(player);
            }

            lastCriticalHits.put(playerId, new CriticalHitMarker(target.getId(), System.currentTimeMillis()));

            // Bonus combo points for critical hits
            IComboSession comboSession = ComboSystemFacade.isInitialized()
                ? ComboSystemFacade.get().getSession(playerId).orElse(null) : null;
            if (comboSession != null) {
                comboSession.registerAction(ComboSystem.ActionType.CRITICAL_HIT, damage);
            }
        }
    }

    // ===============================================================
    // DEATH HANDLING
    // ===============================================================

    /**
     * Handle mob deaths in quests.
     */
    public static void handleMobDeath(LivingEntity entity, DamageSource source) {
        // Check if this is a quest mob
        if (!isQuestMob(entity)) return;

        CompoundTag data = entity.getPersistentData();
        UUID arenaId = data.contains(EnduranceTags.ARENA_ID) ? data.getUUID(EnduranceTags.ARENA_ID) : null;
        UUID questId = data.contains(EnduranceTags.QUEST_ID) ? data.getUUID(EnduranceTags.QUEST_ID) : null;

        // Get the mob type (allow override for practice dummies)
        ResourceLocation mobId = BuiltInRegistries.ENTITY_TYPE.getKey(Objects.requireNonNull(entity.getType()));
        String taggedMobId = data.getString(EnduranceTags.MOB_ID);
        if (taggedMobId.isEmpty()) {
            taggedMobId = data.getString(EnduranceTags.MOB_ID_OVERRIDE);
        }
        if (!taggedMobId.isEmpty()) {
            ResourceLocation override = ResourceLocation.tryParse(taggedMobId);
            if (override != null) {
                mobId = override;
            }
        }

        // Find who killed it
        if (source.getEntity() instanceof ServerPlayer player) {
            UUID playerId = player.getUUID();
            EnduranceQuestManager.ActiveQuestSession session =
                EnduranceQuestManager.INSTANCE.getActiveSession(player).orElse(null);
            boolean practice = session != null && session.isPracticeMode();
            boolean signatureEnabled = isSignatureWeaponsEnabled(session);
            boolean nemesisEnabled = isNemesisEnabled(session);
            boolean tideEnabled = isTideEnabled(session);
            UUID tideScopeId = resolveDesignScopeId(session);

            // Record kill in quest manager
            EnduranceQuestManager.INSTANCE.recordKill(player, mobId);

            // Check if this is an elite mob (FIX #1: track elite kills)
            // FIX #9A: Also check endurance_elite boolean as fallback
            String affixStr = data.getString(EnduranceTags.AFFIX);
            boolean flagElite = data.getBoolean("endurance_elite"); // Fallback: direct elite flag
            boolean affixElite = false;
            if (!affixStr.isEmpty()) {
                try {
                    SpawnAffix affix = SpawnAffix.valueOf(affixStr);
                    affixElite = affix.isElite();
                } catch (IllegalArgumentException ignored) {
                    // Invalid affix name, keep false
                }
            }
            // FIX AUDIT #10: Log diagnostic when sources disagree
            if (flagElite != affixElite && (flagElite || affixElite)) {
                LOGGER.debug("[EnduranceQuest] Elite detection mismatch for {}: flag={}, affix={} ({})",
                    mobId, flagElite, affixElite, affixStr);
            }
            boolean isElite = flagElite || affixElite; // Either source counts

            // Record in combat tracker with elite status
            CombatTracker.INSTANCE.onMobKilled(player, entity, isElite);

            // Check if this is a boss kill
            boolean isBoss = data.getBoolean("endurance_is_boss");

            // === NEMESIS EVOLUTION - Record boss defeat for player profile ===
            if (nemesisEnabled && isBoss && entity instanceof Mob) {
                com.devmod.endurance.nemesis.NemesisEvolutionManager.INSTANCE.recordBossDefeat(entity.getUUID());
            }
            if (tideEnabled && isBoss && entity instanceof Mob) {
                // Also reduce global tide when boss is killed
                com.devmod.endurance.tide.TideManager.INSTANCE.onBossKilled(
                    entity.getUUID(), tideScopeId != null ? tideScopeId : questId);
            }
            if (signatureEnabled) {
                // Record kill for signature weapon imprint
                CombatEnduranceBridge.get().recordSoulKill(player, entity, isBoss);

                // Check for execute kill (target was below 10% health)
                float targetHealthPercent = entity.getHealth() / entity.getMaxHealth();
                if (targetHealthPercent <= 0.10f) {
                    CombatEnduranceBridge.get().recordSoulExecuteKill(player);
                }
            }

            // Record kill in combo system
            IComboSession comboSession = ComboSystemFacade.isInitialized()
                ? ComboSystemFacade.get().getSession(playerId).orElse(null) : null;
            IComboSession.ActionResult killResult = null;
            if (comboSession != null) {
                killResult = comboSession.registerKill(false, 0); // Basic kill
            }

            // Process momentum gain from kill
            MomentumTracker.MomentumResult momentumResult = MomentumTracker.INSTANCE.onPlayerKill(playerId);
            if (momentumResult != null && momentumResult.stateChanged()) {
                // State changed - could trigger sound/visual effects here
                LOGGER.debug("[Momentum] Player {} state changed to {}", playerId, momentumResult.state());
            }

            // Sync combat flow state after kill (includes momentum update)
            String killAction = killResult != null && killResult.announcement() != null
                ? killResult.announcement().action().getDisplayName() : "Kill";
            int killPoints = killResult != null ? killResult.styleEarned() : 0;
            syncCombatFlowToClient(player, killAction, killPoints);

            // Track kill during Phoenix Rising for bonus rewards
            ComebackSystem.INSTANCE.recordKill(playerId);

            // Track kill for tension system (rapid kill streaks)
            if (questId != null) {
                TensionSystem.INSTANCE.onMobKill(questId);
            }

            // Record wave kill telemetry
            if (!practice && questId != null) {
                var waveStateOpt = arenaId != null ? WaveManager.INSTANCE.getWaveState(arenaId) : Optional.<WaveManager.WaveState>empty();
                int waveNumber = waveStateOpt.map(WaveManager.WaveState::getWaveNumber).orElse(1);
                String weaponId = player.getMainHandItem().getItem().getDescriptionId();
                float damageDealt = CombatTracker.INSTANCE.getSession(questId)
                    .map(s -> s.getCurrentWaveStats() != null ? s.getCurrentWaveStats().damageDealt : 0f)
                    .orElse(0f);
                EnduranceTelemetryService.INSTANCE.recordWaveKill(
                    questId, waveNumber, mobId.toString(), false, weaponId, damageDealt
                );
            }

            // Process mutator death effects (exploding mobs, etc.)
            if (questId != null && entity instanceof Mob mob) {
                MutatorSystem.INSTANCE.onMobDeath(questId, mob, player);
            }

            // Process perk on-kill effects (lifesteal, blood frenzy, etc.)
            PerkSystem.INSTANCE.processKill(player);

            if (!practice) {
                // Track daily challenge progress
                boolean isCritical = isRecentCriticalKill(playerId, entity.getId());
                DailyChallengeManager.INSTANCE.onMobKill(playerId, isCritical, isBoss);

                // Track weekly challenge progress
                com.devmod.endurance.challenges.WeeklyChallengeManager.INSTANCE.onMobKill(playerId, isBoss);
            }

            // Notify wave manager
            if (arenaId != null) {
                WaveManager.INSTANCE.handleMobDeath(entity.getUUID(), arenaId, session, player);
            }
        } else {
            // Mob died from external cause (not player kill)
            String deathCause = source.type().msgId();
            LOGGER.debug("[EnduranceQuest] Quest mob {} died from external cause: {} (arena: {})",
                mobId, deathCause, arenaId);

            // Count external deaths toward wave completion if the mob belongs to an active instance.
            if (arenaId != null) {
                EnduranceQuestManager.ActiveQuestSession session =
                    findSessionForArena(arenaId, questId, entity);
                if (session != null) {
                    var server = ServerLifecycleHooks.getCurrentServer();
                    ServerPlayer questPlayer = server != null
                        ? server.getPlayerList().getPlayer(Objects.requireNonNull(session.getPlayerId()))
                        : null;
                    WaveManager.INSTANCE.handleMobDeath(entity.getUUID(), arenaId, session, questPlayer);
                }
            }
        }
    }

    /**
     * Handle player death in quest.
     */
    public static void handlePlayerDeath(ServerPlayer player) {
        Optional<EnduranceQuestManager.ActiveQuestSession> session =
            EnduranceQuestManager.INSTANCE.getActiveSession(player);

        if (session.isPresent()) {
            EnduranceQuestManager.ActiveQuestSession activeSession = session.get();
            LOGGER.info("[EnduranceQuest][DeathEventCombat] player={}, questState={}, awaitingRespawn={}, respawnRequested={}, wave={}, questId={}, instanceId={}, dimension={}",
                player.getName().getString(),
                activeSession.getQuest().getState(),
                activeSession.isAwaitingRespawnChoice(),
                activeSession.isRespawnRequested(),
                activeSession.getQuest().getCurrentWave(),
                activeSession.getQuest().getQuestId(),
                activeSession.getInstanceId(),
                player.level().dimension().location());
            UUID questId = activeSession.getQuest().getQuestId();
            ArenaHandle handle = activeSession.getArenaHandle();
            if (handle != null) {
                EnduranceTelemetryService.INSTANCE.recordDeathHeatmap(
                    questId,
                    handle,
                    player.blockPosition(),
                    player.getUUID()
                );
            }
            CombatTracker.INSTANCE.onPlayerDeath(player);

            // Cleanup Phoenix Rising state
            ComebackSystem.INSTANCE.onPlayerDeath(player.getUUID());

            // === THE TIDE - Record player death for global threat level ===
            if (isTideEnabled(activeSession)) {
                com.devmod.endurance.tide.TideManager.INSTANCE.onPlayerDeath(
                    player.getUUID(), resolveDesignScopeId(activeSession));
            }

            // === UX Q4: Notify party members when a teammate dies ===
            UUID partyId = activeSession.getPartyId();
            if (partyId != null) {
                com.devmod.notification.NotificationService.INSTANCE.notifyPartyMemberDeath(
                    player.getUUID(), player.getName().getString(), partyId);
            }

            EnduranceQuestManager.INSTANCE.handlePlayerDeath(player);
        }
    }

    // ===============================================================
    // UTILITY METHODS
    // ===============================================================

    /**
     * Check if an entity is a quest mob.
     */
    public static boolean isQuestMob(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return false;
        }
        CompoundTag data = living.getPersistentData();
        if (!data.contains(EnduranceTags.QUEST_ID)) {
            return false;
        }
        if (living instanceof Mob) {
            return true;
        }
        return data.getBoolean(EnduranceTags.PRACTICE_DUMMY)
            || DummmmmmyCompat.isDummy(living)
            || living.getTags().contains("devmod_arena_dummy");
    }

    private static @javax.annotation.Nullable UUID resolveDesignScopeId(
            @javax.annotation.Nullable EnduranceQuestManager.ActiveQuestSession session) {
        if (session == null) {
            return null;
        }
        UUID instanceId = session.getInstanceId();
        return instanceId != null ? instanceId : session.getQuest().getQuestId();
    }

    private static boolean isSignatureWeaponsEnabled(
            @javax.annotation.Nullable EnduranceQuestManager.ActiveQuestSession session) {
        if (session == null) {
            return false;
        }
        return GameDesignConfigManager.INSTANCE.isSignatureWeaponsEnabled(
            resolveDesignScopeId(session), session.isPracticeMode());
    }

    private static boolean isNemesisEnabled(
            @javax.annotation.Nullable EnduranceQuestManager.ActiveQuestSession session) {
        if (session == null) {
            return false;
        }
        return GameDesignConfigManager.INSTANCE.isNemesisEnabled(
            resolveDesignScopeId(session), session.isPracticeMode());
    }

    private static boolean isTideEnabled(
            @javax.annotation.Nullable EnduranceQuestManager.ActiveQuestSession session) {
        if (session == null) {
            return false;
        }
        return GameDesignConfigManager.INSTANCE.isTideEnabled(
            resolveDesignScopeId(session), session.isPracticeMode());
    }

    /**
     * Get the body part that was hit (integration with body part system).
     */
    private static String getBodyPartHit(LivingEntity target, DamageSource source) {
        // Try to get attacker from damage source
        Entity attacker = source.getEntity();
        if (attacker instanceof LivingEntity livingAttacker) {
            // Use bridge's raycast-based body part detection
            return CombatEnduranceBridge.get().rayTraceBodyPart(livingAttacker, target);
        }

        // Fallback: try to use direct hit position if available
        Entity directCause = source.getDirectEntity();
        if (directCause != null) {
            // For projectiles, use their Y position relative to target
            double hitY = directCause.getY();
            return CombatEnduranceBridge.get().getBodyPartFromHitY(target, hitY);
        }

        return "BODY"; // Default fallback
    }

    private static @javax.annotation.Nullable EnduranceQuestManager.ActiveQuestSession findSessionForArena(
            UUID arenaId,
            @javax.annotation.Nullable UUID questId,
            LivingEntity entity) {
        if (arenaId == null || entity == null) {
            return null;
        }
        for (EnduranceQuestManager.ActiveQuestSession session :
            EnduranceQuestManager.INSTANCE.getActiveSessions().values()) {
            if (session == null || session.getQuest() == null) {
                continue;
            }
            if (session.getQuest().getState() != EnduranceQuestState.IN_PROGRESS) {
                continue;
            }
            var arena = session.getArena();
            if (arena == null || !arenaId.equals(arena.getId())) {
                continue;
            }
            if (questId != null && !questId.equals(session.getQuest().getQuestId())) {
                continue;
            }
            if (arena.getLevel() != null && arena.getLevel() != entity.level()) {
                continue;
            }
            return session;
        }
        return null;
    }

    private static boolean isRecentCriticalKill(UUID playerId, int entityId) {
        CriticalHitMarker marker = lastCriticalHits.get(playerId);
        if (marker == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - marker.timestamp > CRITICAL_KILL_WINDOW_MS) {
            lastCriticalHits.remove(playerId);
            return false;
        }
        if (marker.targetId == entityId) {
            lastCriticalHits.remove(playerId);
            return true;
        }
        return false;
    }

    // ===============================================================
    // SESSION ACCESSORS
    // ===============================================================

    public static MutatorSystem.MutatorSession getMutatorSession(UUID questId) {
        return mutatorSessions.get(questId);
    }

    static void putMutatorSession(UUID questId, MutatorSystem.MutatorSession session) {
        mutatorSessions.put(questId, session);
    }

    static MutatorSystem.MutatorSession removeMutatorSession(UUID questId) {
        return mutatorSessions.remove(questId);
    }

    private record CriticalHitMarker(int targetId, long timestamp) {}

    // ===============================================================
    // COMBAT FLOW SYNC
    // ===============================================================

    /**
     * Syncs combat flow state (combo, style, momentum) to the client.
     * Call after any combo action or momentum change.
     */
    public static void syncCombatFlowToClient(ServerPlayer player, String lastAction, int lastPoints) {
        if (player == null) return;

        UUID playerId = player.getUUID();
        IComboSession comboSession = ComboSystemFacade.isInitialized()
            ? ComboSystemFacade.get().getSession(playerId).orElse(null) : null;
        MomentumTracker.MomentumSession momentumSession = MomentumTracker.INSTANCE.getSession(playerId);

        CombatFlowSyncPayload payload = CombatFlowSyncPayload.fromSession(
            comboSession, momentumSession, lastAction, lastPoints
        );

        PacketDistributor.sendToPlayer(player, Objects.requireNonNull(payload));
    }

    /**
     * Syncs combat flow state without action info.
     */
    public static void syncCombatFlowToClient(ServerPlayer player) {
        syncCombatFlowToClient(player, "", 0);
    }
}
