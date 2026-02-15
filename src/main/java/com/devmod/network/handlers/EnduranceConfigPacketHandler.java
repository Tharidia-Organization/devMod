package com.devmod.network.handlers;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.endurance.EnduranceConfigSyncPayload;
import com.devmod.endurance.EnduranceQuestManager;
import com.devmod.endurance.MobPoolConfigSyncPayload;
import com.devmod.endurance.RequestMobPoolConfigPayload;
import com.devmod.endurance.config.ConfigProposalManager;
import com.devmod.endurance.config.ConfigScope;
import com.devmod.endurance.config.EnduranceMobConfig;
import com.devmod.endurance.config.EnduranceMobPoolConfig;
import com.devmod.endurance.config.GlobalMobConfigStorage;
import com.devmod.network.NetworkHandler;
import com.devmod.party.PartyData;
import com.devmod.party.PartyManager;
import com.devmod.util.I18n;

/**
 * Handles config sync and mob config payloads for the endurance system.
 * Delegated from {@link EnduranceNetworkHandler}.
 */
final class EnduranceConfigPacketHandler extends NetworkHandlerBase {

    private EnduranceConfigPacketHandler() {}

    // =================================================================================
    // CONFIG SYNC (server-side)
    // =================================================================================

    static void handleConfigSync(EnduranceConfigSyncPayload payload, IPayloadContext context) {
        enqueueWork(context, () -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            var validation = security().validatePacket(player, "config_sync", false);
            if (!validation.isSuccess()) {
                security().recordRateLimitHit("config_sync", player.getName().getString());
                return;
            }

            ConfigScope scope = payload.scope();
            List<EnduranceConfigSyncPayload.ConfigEntry> entries = payload.entries();

            if (entries.isEmpty()) {
                return;
            }

            switch (scope) {
                case GLOBAL -> handleGlobalConfigChange(player, entries);
                case PROPOSAL -> handleConfigProposal(player, entries);
                case SESSION -> handleSessionConfigOverride(player, entries);
                default -> LOGGER.warn("[Config] Unknown config scope: {}", scope);
            }
        });
    }

    private static void handleGlobalConfigChange(ServerPlayer player, List<EnduranceConfigSyncPayload.ConfigEntry> entries) {
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage(I18n.error("devmod.config.no_permission"));
            LOGGER.warn("[Config] Player {} attempted global config change without permission",
                player.getName().getString());
            return;
        }

        int applied = 0;
        for (var entry : entries) {
            if (applyConfigValue(entry.key(), entry.valueType(), entry.value())) {
                applied++;
            }
        }

        saveGameMechanicsConfig();

        player.sendSystemMessage(I18n.translate("devmod.config.applied_global", applied));
        LOGGER.info("[Config] {} applied {} global settings", player.getName().getString(), applied);
    }

    private static void handleConfigProposal(ServerPlayer player, List<EnduranceConfigSyncPayload.ConfigEntry> entries) {
        var proposalId = ConfigProposalManager.INSTANCE.submitProposal(player, entries, null);

        if (proposalId != null) {
            player.sendSystemMessage(I18n.translate("devmod.config.proposal_submitted", entries.size()));
            LOGGER.info("[Config] {} submitted proposal {} with {} entries",
                player.getName().getString(), proposalId, entries.size());
        } else {
            player.sendSystemMessage(I18n.error("devmod.config.proposal_limit_reached"));
            LOGGER.warn("[Config] {} proposal rejected - limit reached", player.getName().getString());
        }
    }

    private static void handleSessionConfigOverride(ServerPlayer player, List<EnduranceConfigSyncPayload.ConfigEntry> entries) {
        var sessionOpt = EnduranceQuestManager.INSTANCE.getActiveSession(player);
        if (sessionOpt.isEmpty()) {
            player.sendSystemMessage(I18n.error("devmod.config.no_active_session"));
            return;
        }

        var session = sessionOpt.get();
        if (!session.isHost(player.getUUID())) {
            player.sendSystemMessage(I18n.error("devmod.config.not_host"));
            return;
        }

        int applied = 0;
        for (var entry : entries) {
            session.setConfigOverride(entry.key(), entry.value());
            applied++;
        }

        player.sendSystemMessage(I18n.translate("devmod.config.applied_session", applied));
        LOGGER.info("[Config] {} applied {} session overrides", player.getName().getString(), applied);
    }

    // =================================================================================
    // MOB CONFIG SYNC (per-mob configuration for Endurance)
    // =================================================================================

    static void handleMobConfigSync(com.devmod.endurance.EnduranceMobConfigSyncPayload payload, IPayloadContext context) {
        enqueueWork(context, () -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            var validation = security().validatePacket(player, "mob_config_sync", false);
            if (!validation.isSuccess()) {
                security().recordRateLimitHit("mob_config_sync", player.getName().getString());
                return;
            }

            ConfigScope scope = payload.scope();

            switch (scope) {
                case GLOBAL -> handleGlobalMobConfigChange(player, payload);
                case PROPOSAL -> handleMobConfigProposal(player, payload);
                case SESSION -> handleSessionMobConfigOverride(player, payload);
                default -> LOGGER.warn("[MobConfig] Unknown config scope: {}", scope);
            }
        });
    }

    private static void handleGlobalMobConfigChange(ServerPlayer player, com.devmod.endurance.EnduranceMobConfigSyncPayload payload) {
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage(I18n.error("devmod.config.no_permission"));
            LOGGER.warn("[MobConfig] Player {} attempted global mob config change without permission",
                player.getName().getString());
            return;
        }

        var poolConfig = payload.toPoolConfig();
        GlobalMobConfigStorage.save(poolConfig);

        int mobCount = payload.mobEntries().size();
        player.sendSystemMessage(I18n.translate("devmod.config.mob_global_applied", mobCount));
        LOGGER.info("[MobConfig] {} applied global mob config ({} mobs)",
            player.getName().getString(), mobCount);
    }

    private static void handleMobConfigProposal(ServerPlayer player, com.devmod.endurance.EnduranceMobConfigSyncPayload payload) {
        var proposalId = ConfigProposalManager.INSTANCE.submitMobConfigProposal(player, payload, null);

        if (proposalId != null) {
            int mobCount = payload.mobEntries().size();
            player.sendSystemMessage(I18n.translate("devmod.config.mob_proposal_submitted", mobCount));
            LOGGER.info("[MobConfig] {} submitted mob config proposal {} ({} mobs)",
                player.getName().getString(), proposalId, mobCount);
        } else {
            player.sendSystemMessage(I18n.error("devmod.config.proposal_limit_reached"));
            LOGGER.warn("[MobConfig] {} mob proposal rejected - limit reached", player.getName().getString());
        }
    }

    private static void handleSessionMobConfigOverride(ServerPlayer player, com.devmod.endurance.EnduranceMobConfigSyncPayload payload) {
        EnduranceMobPoolConfig poolConfig = payload.toPoolConfig();
        int mobCount = payload.mobEntries().size();

        var sessionOpt = EnduranceQuestManager.INSTANCE.getActiveSession(player);
        if (sessionOpt.isEmpty()) {
            PartyData party = PartyManager.INSTANCE.getPlayerParty(player.getUUID());
            if (party != null && party.isLeader(player.getUUID())) {
                if (party.getState() == PartyData.PartyState.IN_QUEST) {
                    EnduranceQuestManager.INSTANCE.getPartySession(party.getPartyId())
                        .ifPresentOrElse(partySession -> {
                            for (UUID memberId : partySession.getMembers()) {
                                EnduranceQuestManager.INSTANCE.getActiveSession(memberId)
                                    .ifPresent(memberSession -> memberSession.setMobPoolConfig(poolConfig.copy()));
                            }
                            player.sendSystemMessage(I18n.translate("devmod.config.mob_session_applied", mobCount));
                            LOGGER.info("[MobConfig] {} applied session mob config via party {} ({} mobs)",
                                player.getName().getString(), party.getPartyId(), mobCount);
                        }, () -> player.sendSystemMessage(I18n.error("devmod.config.no_active_session")));
                    return;
                }
                party.setMobPoolConfig(poolConfig);
                player.sendSystemMessage(I18n.translate("devmod.config.mob_session_staged", mobCount));
                LOGGER.info("[MobConfig] {} staged session mob config for party {} ({} mobs)",
                    player.getName().getString(), party.getPartyId(), mobCount);
                return;
            }
            player.sendSystemMessage(I18n.error("devmod.config.no_active_session"));
            return;
        }

        var session = sessionOpt.get();
        if (!session.isHost(player.getUUID())) {
            player.sendSystemMessage(I18n.error("devmod.config.not_host"));
            return;
        }

        if (session.getPartyId() != null) {
            EnduranceQuestManager.INSTANCE.getPartySession(session.getPartyId())
                .ifPresentOrElse(partySession -> {
                    for (UUID memberId : partySession.getMembers()) {
                        EnduranceQuestManager.INSTANCE.getActiveSession(memberId)
                            .ifPresent(memberSession -> memberSession.setMobPoolConfig(poolConfig.copy()));
                    }
                }, () -> session.setMobPoolConfig(poolConfig));
        } else {
            session.setMobPoolConfig(poolConfig);
        }

        var overrides = payload.globalOverrides();
        player.sendSystemMessage(I18n.translate("devmod.config.mob_session_applied", mobCount));
        LOGGER.info("[MobConfig] {} applied session mob config: {} mobs, HP={}x, DMG={}x",
            player.getName().getString(), mobCount,
            overrides.healthMult(), overrides.damageMult());
    }

    // =================================================================================
    // MOB POOL CONFIG REQUEST (server-side) + SYNC (client-side)
    // =================================================================================

    static void handleRequestMobPoolConfig(RequestMobPoolConfigPayload payload, IPayloadContext context) {
        enqueueWork(context, () -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            ConfigScope scope = payload.scope();
            EnduranceMobPoolConfig poolConfig = null;
            boolean hasConfig = false;

            switch (scope) {
                case SESSION -> {
                    var sessionOpt = EnduranceQuestManager.INSTANCE.getActiveSession(player);
                    if (sessionOpt.isPresent()) {
                        var session = sessionOpt.get();
                        if (session.hasMobPoolConfig()) {
                            poolConfig = session.getMobPoolConfig();
                            hasConfig = true;
                        }
                    }
                    if (poolConfig == null) {
                        PartyData party = PartyManager.INSTANCE.getPlayerParty(player.getUUID());
                        if (party != null && party.hasMobPoolConfig()) {
                            poolConfig = party.getMobPoolConfig();
                            hasConfig = true;
                        }
                    }
                    if (poolConfig == null) {
                        poolConfig = GlobalMobConfigStorage.load().orElse(null);
                    }
                }
                case GLOBAL, PROPOSAL -> {
                    poolConfig = GlobalMobConfigStorage.load().orElse(null);
                    hasConfig = poolConfig != null && poolConfig.hasModifications();
                }
            }

            EnduranceMobPoolConfig normalized = normalizePoolConfig(poolConfig);
            var data = com.devmod.endurance.EnduranceMobConfigSyncPayload.fromPoolConfig(normalized, scope);
            MobPoolConfigSyncPayload response = new MobPoolConfigSyncPayload(hasConfig, data);
            sendPacket(player, response);
        });
    }

    static void handleMobPoolConfigSync(MobPoolConfigSyncPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            enqueueWork(context, () ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleMobPoolConfigSync(payload)));
        }
    }

    static EnduranceMobPoolConfig normalizePoolConfig(@javax.annotation.Nullable EnduranceMobPoolConfig poolConfig) {
        EnduranceMobPoolConfig normalized = poolConfig != null
            ? poolConfig.copy()
            : new EnduranceMobPoolConfig();
        Set<ResourceLocation> disabledMobs = new HashSet<>(normalized.getDisabledMobs());
        for (ResourceLocation mobId : disabledMobs) {
            if (normalized.getMobConfigOrNull(mobId) == null) {
                EnduranceMobConfig baseConfig = EnduranceMobConfig.fromRegistryOrDefault(mobId).withEnabled(false);
                normalized.setMobConfig(baseConfig);
            }
        }
        return normalized;
    }

    // =================================================================================
    // CONFIG VALUE APPLICATION HELPERS
    // =================================================================================

    @SuppressWarnings("UnusedVariable")
    static boolean applyConfigValue(String key, String valueType, String value) {
        var cfg = com.devmod.config.GameMechanicsConfig.class;
        try {
            return switch (key) {
                // Wave Configuration
                case "baseMobCount" -> setInt(cfg, "WAVE_BASE_MOB_COUNT", value);
                case "mobScaling" -> setDouble(cfg, "WAVE_MOB_SCALING", value);
                case "intermissionTicks" -> setInt(cfg, "WAVE_INTERMISSION_TICKS", value);
                case "eliteChanceBase" -> setDouble(cfg, "WAVE_ELITE_CHANCE_BASE", value);
                case "eliteChanceScaling" -> setDouble(cfg, "WAVE_ELITE_CHANCE_SCALING", value);
                case "bossInterval" -> setInt(cfg, "WAVE_BOSS_INTERVAL", value);

                // Execution System
                case "hpThreshold" -> setDouble(cfg, "EXECUTION_HP_THRESHOLD", value);
                case "durationTicks" -> setInt(cfg, "EXECUTION_DURATION_TICKS", value);
                case "cooldownTicks" -> setInt(cfg, "EXECUTION_COOLDOWN_TICKS", value);
                case "styleReward" -> setInt(cfg, "EXECUTION_STYLE_REWARD", value);
                case "hpRegenPercent" -> setDouble(cfg, "EXECUTION_HP_REGEN_PERCENT", value);
                case "dropBoost" -> setDouble(cfg, "EXECUTION_DROP_BOOST", value);
                case "range" -> setDouble(cfg, "EXECUTION_RANGE", value);

                // Combo System
                case "timeoutTicks" -> setInt(cfg, "COMBO_TIMEOUT_TICKS", value);
                case "basePoints" -> setInt(cfg, "COMBO_BASE_POINTS", value);
                case "multiplierIncrement" -> setDouble(cfg, "COMBO_MULTIPLIER_INCREMENT", value);
                case "maxMultiplier" -> setDouble(cfg, "COMBO_MAX_MULTIPLIER", value);
                case "finisherThreshold" -> setInt(cfg, "COMBO_FINISHER_THRESHOLD", value);
                case "juggleBonus" -> setInt(cfg, "COMBO_JUGGLE_BONUS", value);
                case "headshotBonus" -> setInt(cfg, "COMBO_HEADSHOT_BONUS", value);
                case "executionBonus" -> setInt(cfg, "COMBO_EXECUTION_BONUS", value);

                // Style Rank System
                case "cThreshold" -> setInt(cfg, "STYLE_RANK_C_THRESHOLD", value);
                case "bThreshold" -> setInt(cfg, "STYLE_RANK_B_THRESHOLD", value);
                case "aThreshold" -> setInt(cfg, "STYLE_RANK_A_THRESHOLD", value);
                case "sThreshold" -> setInt(cfg, "STYLE_RANK_S_THRESHOLD", value);
                case "ssThreshold" -> setInt(cfg, "STYLE_RANK_SS_THRESHOLD", value);
                case "sssThreshold" -> setInt(cfg, "STYLE_RANK_SSS_THRESHOLD", value);
                case "decayRate" -> setDouble(cfg, "STYLE_DECAY_RATE", value);
                case "decayDelayTicks" -> setInt(cfg, "STYLE_DECAY_DELAY_TICKS", value);

                // Momentum System
                case "momentumDecayRate" -> setDouble(cfg, "MOMENTUM_DECAY_RATE", value);
                case "killBoost" -> setDouble(cfg, "MOMENTUM_KILL_BOOST", value);
                case "hitBoost" -> setDouble(cfg, "MOMENTUM_HIT_BOOST", value);
                case "overdriveThreshold" -> setDouble(cfg, "MOMENTUM_OVERDRIVE_THRESHOLD", value);
                case "overdriveDurationTicks" -> setInt(cfg, "MOMENTUM_OVERDRIVE_DURATION_TICKS", value);
                case "staleThresholdTicks" -> setInt(cfg, "FLOW_STALE_THRESHOLD_TICKS", value);
                case "freshBonusMultiplier" -> setDouble(cfg, "FLOW_FRESH_BONUS_MULTIPLIER", value);

                // Bargain System
                case "enabled" -> setBool(cfg, "BARGAIN_ENABLED", value);
                case "altarSpawnWave" -> setInt(cfg, "BARGAIN_ALTAR_SPAWN_WAVE", value);
                case "altarIntervalWaves" -> setInt(cfg, "BARGAIN_ALTAR_INTERVAL_WAVES", value);
                case "maxCursesPerRun" -> setInt(cfg, "BARGAIN_MAX_CURSES_PER_RUN", value);
                case "choiceTimeoutTicks" -> setInt(cfg, "BARGAIN_CHOICE_TIMEOUT_TICKS", value);
                case "cursePowerMultiplier" -> setDouble(cfg, "BARGAIN_CURSE_POWER_MULTIPLIER", value);
                case "boonPowerMultiplier" -> setDouble(cfg, "BARGAIN_BOON_POWER_MULTIPLIER", value);

                // Hazards System
                case "hazardEnabled" -> setBool(cfg, "HAZARD_ENABLED", value);
                case "floorCrumbleWave" -> setInt(cfg, "HAZARD_FLOOR_CRUMBLE_WAVE", value);
                case "bloodMoonWave" -> setInt(cfg, "HAZARD_BLOOD_MOON_WAVE", value);
                case "arenaShrinkWave" -> setInt(cfg, "HAZARD_ARENA_SHRINK_WAVE", value);
                case "lightningStormWave" -> setInt(cfg, "HAZARD_LIGHTNING_STORM_WAVE", value);
                case "voidRiftsWave" -> setInt(cfg, "HAZARD_VOID_RIFTS_WAVE", value);
                case "lightningDamage" -> setDouble(cfg, "HAZARD_LIGHTNING_DAMAGE", value);
                case "bloodMoonMobBuff" -> setDouble(cfg, "HAZARD_BLOOD_MOON_MOB_BUFF", value);

                // Rewards
                case "xpMultiplier" -> setDouble(cfg, "REWARD_XP_MULTIPLIER", value);
                case "styleMultiplier" -> setDouble(cfg, "REWARD_STYLE_MULTIPLIER", value);
                case "dropRateBonus" -> setDouble(cfg, "REWARD_DROP_RATE_BONUS", value);
                case "bonusChestWaveInterval" -> setInt(cfg, "REWARD_BONUS_CHEST_WAVE_INTERVAL", value);

                default -> {
                    LOGGER.debug("[Config] Unhandled config key (may be session-only): {}", key);
                    yield false;
                }
            };
        } catch (Exception e) {
            LOGGER.warn("[Config] Failed to apply value for {}: {} - {}", key, value, e.getMessage());
            return false;
        }
    }

    private static boolean setInt(Class<?> cfg, String fieldName, String value) throws Exception {
        var field = cfg.getField(fieldName);
        var configValue = (net.neoforged.neoforge.common.ModConfigSpec.IntValue) field.get(null);
        configValue.set(Integer.parseInt(value));
        return true;
    }

    private static boolean setDouble(Class<?> cfg, String fieldName, String value) throws Exception {
        var field = cfg.getField(fieldName);
        var configValue = (net.neoforged.neoforge.common.ModConfigSpec.DoubleValue) field.get(null);
        configValue.set(Double.parseDouble(value));
        return true;
    }

    private static boolean setBool(Class<?> cfg, String fieldName, String value) throws Exception {
        var field = cfg.getField(fieldName);
        var configValue = (net.neoforged.neoforge.common.ModConfigSpec.BooleanValue) field.get(null);
        configValue.set(Boolean.parseBoolean(value));
        return true;
    }

    static void saveGameMechanicsConfig() {
        try {
            com.devmod.config.GameMechanicsConfig.SPEC.save();
        } catch (Exception e) {
            LOGGER.error("[Config] Failed to save config", e);
        }
    }

    private static void enqueueWork(IPayloadContext context, Runnable work) {
        var future = context.enqueueWork(java.util.Objects.requireNonNull(work));
        if (future.isCancelled()) {
            LOGGER.debug("[EnduranceNetwork] Enqueued work cancelled");
        }
    }
}
