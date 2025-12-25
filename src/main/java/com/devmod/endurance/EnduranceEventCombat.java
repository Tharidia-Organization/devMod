package com.devmod.endurance;

import com.devmod.arena.api.ArenaHandle;
import com.devmod.combat.signature.SoulImprintManager;
import com.devmod.endurance.challenges.DailyChallengeManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.telemetry.endurance.EnduranceTelemetryService;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Combat event processing for EnduranceQuest system.
 * Handles damage, kills, critical hits, and combat tracking.
 */
public class EnduranceEventCombat {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnduranceEventCombat.class);

    // Track combo sessions per player (shared with EnduranceEventHandler)
    static final Map<UUID, ComboSystem.ComboSession> comboSessions = new ConcurrentHashMap<>();

    // Track mutator sessions per quest (shared with EnduranceEventHandler)
    static final Map<UUID, MutatorSystem.MutatorSession> mutatorSessions = new ConcurrentHashMap<>();

    // ═══════════════════════════════════════════════════════════════
    // DAMAGE HANDLING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Handle damage dealt by players to mobs in quests (Post event for tracking).
     */
    public static void handleDamagePost(LivingEntity target, DamageSource source, float damage) {
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

                // Record damage for signature weapon imprint
                SoulImprintManager.INSTANCE.recordDamage(player, damage);

                // Check for headshot
                if ("HEAD".equals(bodyPart)) {
                    SoulImprintManager.INSTANCE.recordHeadshot(player);
                }

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

                    // Update tension system with combo progress
                    if (target instanceof Mob mob) {
                        CompoundTag mobData = mob.getPersistentData();
                        if (mobData.contains("endurance_quest_id")) {
                            UUID questId = mobData.getUUID("endurance_quest_id");
                            TensionSystem.INSTANCE.onComboUpdate(questId, comboSession.getCurrentCombo(), comboSession.getCurrentRank());
                        }
                    }
                }

                // Process resonance chain system (party combo synergy)
                if (target instanceof Mob mob) {
                    CompoundTag mobData = mob.getPersistentData();
                    UUID resonanceQuestId = mobData.contains("endurance_quest_id") ?
                        mobData.getUUID("endurance_quest_id") : null;

                    if (resonanceQuestId != null) {
                        var resonanceResult = com.devmod.endurance.resonance.ResonanceChainSystem.INSTANCE
                            .recordHit(player, target, damage, resonanceQuestId);

                        // Apply damage multiplier if resonance triggered
                        if (resonanceResult.triggered()) {
                            // Additional damage is applied as bonus hit
                            float bonusDamage = damage * (resonanceResult.damageMultiplier() - 1.0f);
                            if (bonusDamage > 0) {
                                target.hurt(source, bonusDamage);
                            }
                            // Resonance chains reduce global tide
                            com.devmod.endurance.tide.TideManager.INSTANCE.onResonance(playerId, resonanceQuestId);
                        }
                    }
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
                                player.hurt(Objects.requireNonNull(player.damageSources().magic()), mirrorDamage);
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
                if (com.devmod.combat.ExecutionSystem.INSTANCE.isExecuting(player)) {
                    com.devmod.combat.ExecutionSystem.INSTANCE.interruptExecution(player);
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

                        com.devmod.network.NetworkHandler.sendComboDecay(player, lostCombo, prevRank, newRank);
                    }
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
            float vulnerabilityMult = com.devmod.combat.ExecutionSystem.getVulnerabilityMultiplier(player);
            if (vulnerabilityMult > 1.0f) {
                damage *= vulnerabilityMult;
                LOGGER.debug("[Combat] Execution vulnerability: {} -> {} ({}x)",
                    originalDamage, damage, vulnerabilityMult);
            }

            // Apply perk damage reduction
            return PerkSystem.INSTANCE.processDamageTaken(player, damage);
        }
        return originalDamage;
    }

    // ═══════════════════════════════════════════════════════════════
    // CRITICAL HITS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Handle critical hits.
     */
    public static void handleCriticalHit(ServerPlayer player, Entity target, float damage) {
        if (isQuestMob(target)) {
            CombatTracker.INSTANCE.onCriticalHit(player, damage);

            // Record for signature weapon imprint
            SoulImprintManager.INSTANCE.recordCriticalHit(player);

            // Bonus combo points for critical hits
            UUID playerId = player.getUUID();
            ComboSystem.ComboSession comboSession = comboSessions.get(playerId);
            if (comboSession != null) {
                comboSession.registerAction(ComboSystem.ActionType.CRITICAL_HIT, damage);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // DEATH HANDLING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Handle mob deaths in quests.
     */
    public static void handleMobDeath(LivingEntity entity, DamageSource source) {
        // Check if this is a quest mob
        if (!isQuestMob(entity)) return;

        CompoundTag data = entity.getPersistentData();
        UUID arenaId = data.contains("endurance_arena_id") ? data.getUUID("endurance_arena_id") : null;
        UUID questId = data.contains("endurance_quest_id") ? data.getUUID("endurance_quest_id") : null;

        // Get the mob type
        ResourceLocation mobId = BuiltInRegistries.ENTITY_TYPE.getKey(Objects.requireNonNull(entity.getType()));

        // Find who killed it
        if (source.getEntity() instanceof ServerPlayer player) {
            UUID playerId = player.getUUID();

            // Record kill in quest manager
            EnduranceQuestManager.INSTANCE.recordKill(player, mobId);

            // Record in combat tracker
            CombatTracker.INSTANCE.onMobKilled(player, entity);

            // Check if this is a boss kill
            boolean isBoss = data.getBoolean("endurance_is_boss");

            // === NEMESIS EVOLUTION - Record boss defeat for player profile ===
            if (isBoss && entity instanceof Mob) {
                com.devmod.endurance.nemesis.NemesisEvolutionManager.INSTANCE.recordBossDefeat(entity.getUUID());
                // Also reduce global tide when boss is killed
                com.devmod.endurance.tide.TideManager.INSTANCE.onBossKilled(entity.getUUID(), questId);
            }

            // Record kill for signature weapon imprint
            SoulImprintManager.INSTANCE.recordKill(player, entity, isBoss);

            // Check for execute kill (target was below 10% health)
            float targetHealthPercent = entity.getHealth() / entity.getMaxHealth();
            if (targetHealthPercent <= 0.10f) {
                SoulImprintManager.INSTANCE.recordExecuteKill(player);
            }

            // Record kill in combo system
            ComboSystem.ComboSession comboSession = comboSessions.get(playerId);
            if (comboSession != null) {
                comboSession.registerKill(false, 0); // Basic kill
            }

            // Process momentum gain from kill
            MomentumTracker.MomentumResult momentumResult = MomentumTracker.INSTANCE.onPlayerKill(playerId);
            if (momentumResult != null && momentumResult.stateChanged()) {
                // State changed - could trigger sound/visual effects here
                LOGGER.debug("[Momentum] Player {} state changed to {}", playerId, momentumResult.state());
            }

            // Track kill during Phoenix Rising for bonus rewards
            ComebackSystem.INSTANCE.recordKill(playerId);

            // Track kill for tension system (rapid kill streaks)
            if (questId != null) {
                TensionSystem.INSTANCE.onMobKill(questId);
            }

            // Record wave kill telemetry
            if (questId != null) {
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

            // Track daily challenge progress
            boolean isCritical = source.is(net.minecraft.tags.DamageTypeTags.ALWAYS_KILLS_ARMOR_STANDS); // Approximate critical detection
            DailyChallengeManager.INSTANCE.onMobKill(playerId, isCritical, isBoss);

            // Track weekly challenge progress
            com.devmod.endurance.challenges.WeeklyChallengeManager.INSTANCE.onMobKill(playerId, isBoss);

            // Notify wave manager
            if (arenaId != null) {
                EnduranceQuestManager.ActiveQuestSession session =
                    EnduranceQuestManager.INSTANCE.getActiveSession(player).orElse(null);
                WaveManager.INSTANCE.handleMobDeath(entity.getUUID(), arenaId, session, player);
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

    /**
     * Handle player death in quest.
     */
    public static void handlePlayerDeath(ServerPlayer player) {
        Optional<EnduranceQuestManager.ActiveQuestSession> session =
            EnduranceQuestManager.INSTANCE.getActiveSession(player);

        if (session.isPresent()) {
            EnduranceQuestManager.ActiveQuestSession activeSession = session.get();
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
            com.devmod.endurance.tide.TideManager.INSTANCE.onPlayerDeath(player.getUUID(), questId);

            EnduranceQuestManager.INSTANCE.handlePlayerDeath(player);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if an entity is a quest mob.
     */
    public static boolean isQuestMob(Entity entity) {
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
            com.devmod.combat.HitHelper.BodyPart bodyPart =
                com.devmod.combat.HitHelper.rayTraceBodyPartAABB(livingAttacker, target);
            return bodyPart.name();
        }

        // Fallback: try to use direct hit position if available
        Entity directCause = source.getDirectEntity();
        if (directCause != null) {
            // For projectiles, use their Y position relative to target
            double hitY = directCause.getY();
            com.devmod.combat.HitHelper.BodyPart bodyPart =
                com.devmod.combat.HitHelper.getBodyPart(target, hitY);
            return bodyPart.name();
        }

        return "BODY"; // Default fallback
    }

    // ═══════════════════════════════════════════════════════════════
    // SESSION ACCESSORS
    // ═══════════════════════════════════════════════════════════════

    public static ComboSystem.ComboSession getComboSession(UUID playerId) {
        return comboSessions.get(playerId);
    }

    public static MutatorSystem.MutatorSession getMutatorSession(UUID questId) {
        return mutatorSessions.get(questId);
    }

    static void putComboSession(UUID playerId, ComboSystem.ComboSession session) {
        comboSessions.put(playerId, session);
    }

    static ComboSystem.ComboSession removeComboSession(UUID playerId) {
        return comboSessions.remove(playerId);
    }

    static void putMutatorSession(UUID questId, MutatorSystem.MutatorSession session) {
        mutatorSessions.put(questId, session);
    }

    static MutatorSystem.MutatorSession removeMutatorSession(UUID questId) {
        return mutatorSessions.remove(questId);
    }
}
