package com.devmod.endurance;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.debug.DiagnosticLogger;
import com.devmod.arena.api.ArenaHandle;
import com.devmod.arena.builder.ArenaBuilder;
import com.devmod.arena.builder.AsyncArenaBuilder;
import com.devmod.arena.config.ArenaTemplateConfig;
import com.devmod.arena.override.ForceTemplateCapability;
import com.devmod.arena.policy.ArenaPolicy;
import com.devmod.arena.policy.PolicyResolver;
import com.devmod.arena.policy.ResolvedArena;
import com.devmod.arena.pool.PrebuildPoolManager;
import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.registry.TemplateSpawnValidator;
import com.devmod.arena.telemetry.ArenaTelemetry;
import com.devmod.endurance.ArenaSetupManager.BuildAttemptResult;
import com.devmod.endurance.ArenaSetupManager.OriginResolution;
import com.devmod.endurance.EnduranceLogger.Phase;
import com.devmod.endurance.combat.ComboSystemFacade;
import com.devmod.endurance.config.EnduranceConfigManager;
import com.devmod.endurance.services.InstanceServicesFacade;
import com.devmod.endurance.services.PlayerStateServicesFacade;
import com.devmod.network.GameMechanicsSyncPayload;
import com.devmod.party.QuestSequencePayload;
import com.devmod.runtime.InstanceData;
import com.devmod.runtime.InstanceManager;
import com.devmod.shared.SharedColorTokens;
import com.devmod.telemetry.TelemetryService;
import com.devmod.telemetry.endurance.EnduranceTelemetryService;
import com.devmod.util.I18n;

/**
 * Facade for the Endurance quest system. Delegates to:
 * <ul>
 *   <li>{@link ArenaSetupManager} - arena template resolution, building, spawn extraction</li>
 *   <li>{@link PartyQuestCoordinator} - party session management and coordination</li>
 *   <li>{@link EnduranceSessionHandler} - session lifecycle (abandon, death, respawn, wave)</li>
 * </ul>
 */
public class EnduranceQuestManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnduranceQuestManager.class);

    public static final EnduranceQuestManager INSTANCE = new EnduranceQuestManager();

    private static <T> T requireNonNull(T value, String name) {
        return Objects.requireNonNull(value, name);
    }

    private static final int SHUTDOWN_PARTIAL_TOKENS_PER_WAVE = 15;

    // Active quests per player (player UUID -> active quest)
    private final Map<UUID, ActiveQuestSession> activeSessions = new ConcurrentHashMap<>();
    // Quest templates (mob ID -> quest template)
    private final Map<ResourceLocation, EnduranceQuest> questTemplates = new ConcurrentHashMap<>();

    // Delegate classes
    private final EnduranceQuestPersistence persistence = new EnduranceQuestPersistence();
    private volatile EnduranceSessionHandler sessionHandler;
    private final ArenaSetupManager arenaSetup = new ArenaSetupManager();
    private final PartyQuestCoordinator partyCoordinator = new PartyQuestCoordinator(activeSessions, arenaSetup);

    private Path dataDirectory;
    private volatile boolean initialized = false;
    private volatile boolean useInstanceDimensions = true;

    public static final int PRE_TELEPORT_COUNTDOWN_TICKS = 200;
    public static final int WAVE_START_COUNTDOWN_TICKS = 200;
    public static final int BRIEFING_TICKS = 80;
    public static final int SAFE_WINDOW_TICKS = 60;
    public static final int BOSS_INTRO_TICKS = 20;
    private static final String I18N_PREFIX = "i18n:";

    private EnduranceQuestManager() {}

    // ========== Instance Dimension Mode ==========

    public void setUseInstanceDimensions(boolean use) {
        this.useInstanceDimensions = use;
        if (!use) {
            LOGGER.error("[EnduranceQuest] Instance dimensions disabled; legacy arena path is deprecated and quests will fail");
        }
        LOGGER.info("[EnduranceQuest] Instance dimension mode: {}", use ? "ENABLED" : "DISABLED");
    }

    public boolean isUseInstanceDimensions() { return useInstanceDimensions; }

    public PrebuildPoolManager getPrebuildPoolManager() { return arenaSetup.getPrebuildPoolManager(); }

    public void tickAsyncBuilds(net.minecraft.server.MinecraftServer server) { arenaSetup.tickAsyncBuilds(server); }

    public void applyArenaConfig(ArenaTemplateConfig config) { arenaSetup.applyArenaConfig(config); }

    public void setForceTemplateCapability(@javax.annotation.Nullable ForceTemplateCapability capability) {
        arenaSetup.setForceTemplateCapability(capability);
    }

    // ========== Initialization ==========

    public void initialize(Path configDir) {
        if (initialized) return;
        this.dataDirectory = configDir.resolve("endurance_quests");
        try { Files.createDirectories(dataDirectory); } catch (IOException e) { LOGGER.error("[EnduranceQuest] Failed to create data directory", e); }

        EnduranceQuestRegistry.INSTANCE.initialize();
        for (EnduranceQuestRegistry.MobQuestConfig mobConfig : EnduranceQuestRegistry.INSTANCE.getAllMobConfigs()) {
            questTemplates.put(mobConfig.getMobId(), new EnduranceQuest(mobConfig));
        }
        persistence.initialize(dataDirectory);
        KitManager.INSTANCE.initializeSyncPersistence(dataDirectory);
        this.sessionHandler = new EnduranceSessionHandler(activeSessions, persistence);
        RewardSystem.INSTANCE.initialize(configDir);
        GamificationManager.INSTANCE.initialize(configDir);
        EnduranceAnalytics.INSTANCE.initialize(configDir);

        ArenaTemplateConfig templateConfig = ArenaTemplateConfig.load();
        arenaSetup.setArenaTemplateConfig(templateConfig);
        arenaSetup.setArenaConfigSnapshot(templateConfig.snapshot());
        arenaSetup.setArenaTelemetry(new ArenaTelemetry());
        arenaSetup.applyArenaConfig(templateConfig);
        if (templateConfig.instanceOnly()) {
            LOGGER.info("[EnduranceQuest] Instance-only mode enabled; legacy overworld arenas are deprecated");
        }
        arenaSetup.initArenaTemplateIntegration(configDir);
        if (!arenaSetup.shouldUseTemplateSystem()) {
            LOGGER.error("[EnduranceQuest] Arena template system required; Endurance quests are disabled until enabled");
        }
        initialized = true;
        LOGGER.info("[EnduranceQuest] Manager initialized with {} quest types", questTemplates.size());
    }

    public void shutdown() {
        if (!initialized) return;
        LOGGER.info("[EnduranceQuest] Shutting down with {} active sessions", activeSessions.size());

        Map<UUID, Integer> questIdCounts = new HashMap<>();
        for (ActiveQuestSession session : activeSessions.values()) {
            UUID qid = session.getQuest().getQuestId();
            questIdCounts.merge(qid, 1, Integer::sum);
        }
        java.util.Set<UUID> sharedCleanup = new java.util.HashSet<>();
        for (ActiveQuestSession session : activeSessions.values()) {
            try {
                EnduranceQuest quest = session.quest;
                UUID playerId = session.getPlayerId();
                int refs = questIdCounts.getOrDefault(quest.getQuestId(), 1);
                boolean doShared = refs <= 1 || sharedCleanup.add(quest.getQuestId());
                int partialTokens = quest.getCurrentWave() * SHUTDOWN_PARTIAL_TOKENS_PER_WAVE;
                if (partialTokens > 0) {
                    RewardSystem.INSTANCE.getWallet(playerId).addCurrency(RewardSystem.Currency.TOKENS, partialTokens);
                    LOGGER.info("[EnduranceQuest] Awarded {} partial tokens to player {} ({} waves)", partialTokens, playerId, quest.getCurrentWave());
                }
                try { quest.fail(true); } finally { handleForcedShutdownCleanup(session, doShared); }
            } catch (Exception e) { LOGGER.error("[EnduranceQuest] Error cleaning up session for player {}", session.getPlayerId(), e); }
        }
        activeSessions.clear();
        partyCoordinator.clearAll();
        persistence.savePlayerStats();
        RewardSystem.INSTANCE.saveAll();
        GamificationManager.INSTANCE.saveAll();
        questTemplates.clear();
        arenaSetup.shutdown();
        initialized = false;
        LOGGER.info("[EnduranceQuest] Shutdown complete");
    }

    public boolean isInitialized() { return initialized; }

    // ========== Arena Template Delegation ==========

    @javax.annotation.Nullable
    public PolicyResolver getPolicyResolver() { return arenaSetup.getPolicyResolver(); }

    @javax.annotation.Nullable
    String getTemplateSystemReadinessError() { return arenaSetup.getTemplateSystemReadinessError(useInstanceDimensions); }

    String getTemplateSystemReadinessErrorForTesting() { return getTemplateSystemReadinessError(); }

    // ========== Party Quest Flow (Separate Phases) ==========

    public PreparedArenaResult prepareArenaForParty(ServerPlayer leader, ResourceLocation mobId, QuestSettings settings) {
        EnduranceQuest template = questTemplates.get(mobId);
        if (template == null) return PreparedArenaResult.failure("Unknown quest type: " + mobId);
        String err = getTemplateSystemReadinessError();
        if (err != null) { LOGGER.error("[EnduranceQuest] prepareArenaForParty blocked: {}", err); return PreparedArenaResult.failure(err); }
        return prepareTemplateArenaForParty(leader, mobId, settings, template.getMobConfig());
    }

    public CompletableFuture<PreparedArenaResult> prepareArenaForPartyAsync(ServerPlayer leader, ResourceLocation mobId, QuestSettings settings) {
        EnduranceQuest template = questTemplates.get(mobId);
        if (template == null) return CompletableFuture.completedFuture(PreparedArenaResult.failure("Unknown quest type: " + mobId));
        String err = getTemplateSystemReadinessError();
        if (err != null) { LOGGER.error("[EnduranceQuest] prepareArenaForPartyAsync blocked: {}", err); return CompletableFuture.completedFuture(PreparedArenaResult.failure(err)); }
        return prepareTemplateArenaForPartyAsync(leader, mobId, settings, template.getMobConfig());
    }

    private CompletableFuture<PreparedArenaResult> prepareTemplateArenaForPartyAsync(
            ServerPlayer leader, ResourceLocation mobId, QuestSettings settings, EnduranceQuestRegistry.MobQuestConfig mobConfig) {
        ResolvedArena resolved = arenaSetup.resolveArenaTemplate(leader.getUUID(), mobId, settings, leader.getServer());
        if (resolved == null) return CompletableFuture.completedFuture(PreparedArenaResult.failure("No matching arena template/policy"));
        ArenaTemplate template = resolved.template();
        List<UUID> partyMembers = settings.partyMemberIds != null && !settings.partyMemberIds.isEmpty() ? new ArrayList<>(settings.partyMemberIds) : null;
        CompletableFuture<PreparedArenaResult> result = new CompletableFuture<>();
        var instanceFuture = InstanceManager.INSTANCE.prepareInstanceQuest(leader, template.id(), mobId.toString(), partyMembers)
            .orTimeout(ArenaSetupManager.INSTANCE_CREATION_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
        instanceFuture = instanceFuture.whenComplete((instanceId, throwable) -> {
            var server = leader.getServer();
            if (server == null) { if (instanceId != null) InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId); result.complete(PreparedArenaResult.failure("Server not available")); return; }
            server.execute(() -> {
                if (throwable != null || instanceId == null) {
                    String msg = throwable != null && throwable.getMessage() != null ? throwable.getMessage() : "Failed to create instance for party";
                    LOGGER.error("[EnduranceQuest] Failed to create instance for party: {}", msg);
                    result.complete(PreparedArenaResult.failure("Failed to create instance: " + msg));
                    return;
                }
                Optional<InstanceData> instanceOpt = InstanceServicesFacade.INSTANCE.getInstance(instanceId);
                if (instanceOpt.isEmpty()) { InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId); result.complete(PreparedArenaResult.failure("Instance not found after creation")); return; }
                InstanceData instance = instanceOpt.get();
                var dimensionKey = instance.getDimensionKey();
                if (dimensionKey == null) { InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId); result.complete(PreparedArenaResult.failure("Instance dimension not ready")); return; }
                ServerLevel instanceLevel = server.getLevel(dimensionKey);
                if (instanceLevel == null) { InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId); result.complete(PreparedArenaResult.failure("Instance level not found")); return; }

                OriginResolution origin = arenaSetup.resolveTemplateOrigin(template);
                if (arenaSetup.shouldBuildAsync(template)) {
                    AsyncArenaBuilder asyncBldr = arenaSetup.asyncBuildCoordinator.getOrCreate(instanceLevel);
                    UUID arenaId = UUID.randomUUID();
                    var buildFuture = asyncBldr.submitBuildAsync(arenaId, template, origin.centerX(), origin.originY(), origin.centerZ());
                    buildFuture = buildFuture.whenComplete((asyncResult, buildError) -> server.execute(() -> {
                        if (buildError != null || asyncResult == null || !asyncResult.success()) {
                            String msg2 = buildError != null && buildError.getMessage() != null ? buildError.getMessage() : (asyncResult != null ? asyncResult.errorMessage() : "Build failed");
                            var builder = arenaSetup.createTemplateBuilder(instanceLevel);
                            BuildAttemptResult fa = arenaSetup.attemptFallbackOnly(builder, resolved, "party_async", msg2);
                            if (fa != null && fa.result().success()) { result.complete(finalizePreparedArena(fa.result(), fa.resolved(), instanceId, fa.origin(), instanceLevel, instance, mobId, mobConfig)); return; }
                            String tech = msg2; if (fa != null && fa.result() != null && fa.result().errorMessage() != null) tech = fa.result().errorMessage();
                            String um = arenaSetup.handleBuildAbort(resolved, "party_async", tech, buildError, fa != null);
                            InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId); result.complete(PreparedArenaResult.failure(um)); return;
                        }
                        ArenaBuilder.BuildResult br = ArenaBuilder.BuildResult.success(asyncResult.arenaId(), template.id(), asyncResult.blocksPlaced(), asyncResult.durationMs());
                        result.complete(finalizePreparedArena(br, resolved, instanceId, origin, instanceLevel, instance, mobId, mobConfig));
                    }));
                    if (buildFuture.isCancelled()) LOGGER.debug("[EnduranceQuest] Async arena build cancelled for {}", arenaId);
                    return;
                }

                var builder = arenaSetup.createTemplateBuilder(instanceLevel);
                BuildAttemptResult attempt = arenaSetup.buildWithFallback(builder, resolved, origin, "party_sync");
                if (!attempt.result().success()) {
                    String msg2 = attempt.result().errorMessage() != null ? attempt.result().errorMessage() : "Build failed";
                    String um = arenaSetup.handleBuildAbort(attempt.resolved(), "party_sync", msg2, null, attempt.fallbackAttempted());
                    InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId); result.complete(PreparedArenaResult.failure(um)); return;
                }
                result.complete(finalizePreparedArena(attempt.result(), attempt.resolved(), instanceId, attempt.origin(), instanceLevel, instance, mobId, mobConfig));
            });
        });
        if (instanceFuture.isCancelled()) LOGGER.debug("[EnduranceQuest] Instance preparation cancelled for {}", mobId);
        return result;
    }

    private PreparedArenaResult prepareTemplateArenaForParty(ServerPlayer leader, ResourceLocation mobId, QuestSettings settings, EnduranceQuestRegistry.MobQuestConfig mobConfig) {
        ResolvedArena resolved = arenaSetup.resolveArenaTemplate(leader.getUUID(), mobId, settings, leader.getServer());
        if (resolved == null) return PreparedArenaResult.failure("No matching arena template/policy");
        ArenaTemplate template = resolved.template();
        UUID instanceId;
        try {
            List<UUID> partyMembers = settings.partyMemberIds != null && !settings.partyMemberIds.isEmpty() ? new ArrayList<>(settings.partyMemberIds) : null;
            instanceId = InstanceManager.INSTANCE.prepareInstanceQuest(leader, template.id(), mobId.toString(), partyMembers).get(ArenaSetupManager.INSTANCE_CREATION_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException | java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            LOGGER.error("[EnduranceQuest] Failed to create instance for party: {}", e.getMessage()); return PreparedArenaResult.failure("Failed to create instance: " + e.getMessage());
        }
        if (instanceId == null) return PreparedArenaResult.failure("Failed to create instance for party");
        Optional<InstanceData> instanceOpt = InstanceServicesFacade.INSTANCE.getInstance(instanceId);
        if (instanceOpt.isEmpty()) { InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId); return PreparedArenaResult.failure("Instance not found after creation"); }
        InstanceData instance = instanceOpt.get();
        var dimensionKey = instance.getDimensionKey();
        if (dimensionKey == null) { InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId); return PreparedArenaResult.failure("Instance dimension not ready"); }
        var server = leader.getServer();
        if (server == null) { InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId); return PreparedArenaResult.failure("Server not available"); }
        ServerLevel instanceLevel = server.getLevel(dimensionKey);
        if (instanceLevel == null) { InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId); return PreparedArenaResult.failure("Instance level not found"); }
        var builder = arenaSetup.createTemplateBuilder(instanceLevel);
        OriginResolution origin = arenaSetup.resolveTemplateOrigin(template);
        BuildAttemptResult attempt = arenaSetup.buildWithFallback(builder, resolved, origin, "party_sync");
        if (!attempt.result().success()) {
            String msg = attempt.result().errorMessage() != null ? attempt.result().errorMessage() : "Build failed";
            String um = arenaSetup.handleBuildAbort(attempt.resolved(), "party_sync", msg, null, attempt.fallbackAttempted());
            InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId); return PreparedArenaResult.failure(um);
        }
        return finalizePreparedArena(attempt.result(), attempt.resolved(), instanceId, attempt.origin(), instanceLevel, instance, mobId, mobConfig);
    }

    private PreparedArenaResult finalizePreparedArena(ArenaBuilder.BuildResult buildResult, ResolvedArena resolved, UUID instanceId, OriginResolution origin, ServerLevel instanceLevel, InstanceData instance, ResourceLocation mobId, EnduranceQuestRegistry.MobQuestConfig mobConfig) {
        ArenaTemplate template = resolved.template();
        ArenaHandle handle = arenaSetup.createArenaHandle(buildResult, resolved, instanceId, origin, instanceLevel);
        if (!arenaSetup.isHandleValid(handle)) { InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId); arenaSetup.emitGateFailure("missing_spawn_slots", resolved); return PreparedArenaResult.failure("Template missing required spawn slots (player/mob)"); }
        ArenaContext arena = arenaSetup.createArenaAdapter(instanceLevel, handle);
        arenaSetup.updateInstanceArenaMetadata(instance, template, handle, origin);
        return PreparedArenaResult.success(arena, handle, mobId, mobConfig, instanceId);
    }

    // ========== Teleportation ==========

    public Map<UUID, net.minecraft.core.BlockPos> teleportPlayersToArena(List<ServerPlayer> players, ArenaContext arena, @javax.annotation.Nullable ArenaHandle handle) {
        return teleportPlayersToArena(players, arena, handle, false, shouldUpdateSnapshotState(arena, handle));
    }

    public Map<UUID, net.minecraft.core.BlockPos> teleportPlayersToArena(List<ServerPlayer> players, ArenaContext arena, @javax.annotation.Nullable ArenaHandle handle, boolean allowFallback) {
        return teleportPlayersToArena(players, arena, handle, allowFallback, shouldUpdateSnapshotState(arena, handle));
    }

    public Map<UUID, net.minecraft.core.BlockPos> teleportPlayersToArena(List<ServerPlayer> players, ArenaContext arena, @javax.annotation.Nullable ArenaHandle handle, boolean allowFallback, boolean updateSnapshotState) {
        Map<UUID, net.minecraft.core.BlockPos> spawnPositions = new HashMap<>();
        if (handle == null || handle.playerSpawnPositions() == null || handle.playerSpawnPositions().isEmpty()) { LOGGER.error("[EnduranceQuest] Missing player spawn slots; ArenaHandle required"); return spawnPositions; }
        List<ArenaHandle.BlockPos> positions = handle.playerSpawnPositions();
        ServerLevel level = arena.getLevel();
        UUID instanceId = handle.instanceId();
        boolean isInstanceDimension = com.devmod.runtime.DynamicDimensionManager.INSTANCE.isInstanceDimension(level.dimension());
        boolean useTeleportTransaction = instanceId != null && isInstanceDimension;
        TemplateSpawnValidator runtimeValidator = new TemplateSpawnValidator(arenaSetup.getArenaTelemetry() != null ? arenaSetup.getArenaTelemetry() : new ArenaTelemetry());
        Map<net.minecraft.core.BlockPos, ArenaTemplate.SpawnSlot> slotMap = Collections.emptyMap();
        ArenaTemplate template = null;
        var registry = com.devmod.DevMod.getArenaTemplateRegistry();
        if (registry != null) { template = registry.get(handle.templateId()).orElse(null); if (template != null) slotMap = arenaSetup.buildPlayerSpawnSlotMap(template, handle); }
        com.devmod.arena.spawn.SpawnOccupancyTracker occupied = new com.devmod.arena.spawn.SpawnOccupancyTracker();

        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);
            if (player == null || !player.isAlive()) continue;
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> previousDimension = player.level().dimension();
            net.minecraft.core.BlockPos spawnPos = pickValidatedSpawnPosition(positions, i, occupied, runtimeValidator, slotMap, template, level);
            if (spawnPos == null && allowFallback) { spawnPos = pickFallbackSpawnPosition(positions, i, occupied); if (spawnPos != null) LOGGER.warn("[EnduranceQuest] Falling back to unvalidated player spawn at {}", spawnPos); }
            if (spawnPos == null) { LOGGER.warn("[EnduranceQuest] No valid template player spawn found"); spawnPositions.clear(); return spawnPositions; }
            if (useTeleportTransaction) {
                com.devmod.runtime.TeleportTransaction.TeleportResult result = com.devmod.runtime.TeleportTransaction.executeToArena(player, instanceId, level, spawnPos, updateSnapshotState);
                if (result == com.devmod.runtime.TeleportTransaction.TeleportResult.SUCCESS) { spawnPositions.put(player.getUUID(), spawnPos); }
                else { LOGGER.warn("[EnduranceQuest] Teleport transaction failed for {} (instance={}, result={})", player.getName().getString(), instanceId, result);
                    if (result == com.devmod.runtime.TeleportTransaction.TeleportResult.DIMENSION_NOT_FOUND || result == com.devmod.runtime.TeleportTransaction.TeleportResult.INSTANCE_INVALID_STATE) { spawnPositions.clear(); return spawnPositions; } continue; }
            } else {
                double x = spawnPos.getX() + 0.5, y = spawnPos.getY(), z = spawnPos.getZ() + 0.5;
                level.getChunkAt(spawnPos);
                player.teleportTo(level, x, y, z, Objects.requireNonNull(java.util.Set.<net.minecraft.world.entity.RelativeMovement>of(), "teleport flags"), player.getYRot(), player.getXRot());
                player.setDeltaMovement(0, 0, 0); player.fallDistance = 0;
                if (!previousDimension.equals(level.dimension())) player.connection.resetPosition();
                if (com.devmod.runtime.DynamicDimensionManager.INSTANCE.isInstanceDimension(level.dimension())) com.devmod.runtime.environment.DimensionEnvironmentManager.INSTANCE.syncEnvironmentToPlayer(player, level.dimension());
                spawnPositions.put(player.getUUID(), spawnPos);
            }
            LOGGER.debug("[EnduranceQuest] Teleported {} to handle spawn at ({}, {}, {})", player.getName().getString(), spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
        }
        LOGGER.info("[EnduranceQuest] Teleported {} players to arena {} using template spawns", spawnPositions.size(), arena.getId());
        return spawnPositions;
    }

    private boolean shouldUpdateSnapshotState(ArenaContext arena, @javax.annotation.Nullable ArenaHandle handle) {
        if (handle == null || handle.instanceId() == null || arena == null || arena.getLevel() == null) return false;
        return com.devmod.runtime.DynamicDimensionManager.INSTANCE.isInstanceDimension(arena.getLevel().dimension());
    }

    public boolean teleportPlayerToArena(ServerPlayer player, ActiveQuestSession session, boolean allowFallback, boolean updateSnapshotState) {
        if (player == null || session == null) return false;
        ArenaContext arena = session.getArena(); ArenaHandle handle = session.getArenaHandle();
        if (arena == null || handle == null) { LOGGER.warn("[EnduranceQuest] Teleport failed for {} - missing arena/handle", player.getName().getString()); return false; }
        return !teleportPlayersToArena(List.of(player), arena, handle, allowFallback, updateSnapshotState).isEmpty();
    }

    private net.minecraft.core.BlockPos pickValidatedSpawnPosition(List<ArenaHandle.BlockPos> positions, int startIndex, com.devmod.arena.spawn.SpawnOccupancyTracker occupied, TemplateSpawnValidator runtimeValidator, Map<net.minecraft.core.BlockPos, ArenaTemplate.SpawnSlot> slotMap, @javax.annotation.Nullable ArenaTemplate template, ServerLevel level) {
        if (positions == null || positions.isEmpty()) return null;
        int size = positions.size();
        for (int offset = 0; offset < size; offset++) {
            ArenaHandle.BlockPos candidate = positions.get((startIndex + offset) % size);
            net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(candidate.x(), candidate.y(), candidate.z());
            if (occupied.isOccupied(pos)) continue;
            if (template != null && !slotMap.isEmpty()) { ArenaTemplate.SpawnSlot slot = slotMap.get(pos); if (slot == null) continue; if (!runtimeValidator.validateAtRuntime(template.id(), slot, level, pos)) continue; }
            occupied.markOccupied(pos); return pos;
        }
        return null;
    }

    private net.minecraft.core.BlockPos pickFallbackSpawnPosition(List<ArenaHandle.BlockPos> positions, int startIndex, com.devmod.arena.spawn.SpawnOccupancyTracker occupied) {
        if (positions == null || positions.isEmpty()) return null;
        int size = positions.size();
        for (int offset = 0; offset < size; offset++) {
            ArenaHandle.BlockPos candidate = positions.get((startIndex + offset) % size);
            net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(candidate.x(), candidate.y(), candidate.z());
            if (occupied.isOccupied(pos)) continue;
            occupied.markOccupied(pos); return pos;
        }
        return null;
    }

    public boolean isPlayerInArena(ServerPlayer player, ArenaContext arena) { return isPlayerInArena(player, null, arena); }

    public boolean isPlayerInArena(ServerPlayer player, @javax.annotation.Nullable ArenaHandle handle, @javax.annotation.Nullable ArenaContext arena) {
        if (player == null) return false;
        if (handle != null && handle.bounds() != null) { BlockPos pos = player.blockPosition(); ArenaHandle.AABB bounds = handle.bounds(); return bounds.contains(pos.getX(), pos.getY(), pos.getZ()); }
        if (arena == null) return false;
        return arena.contains(Objects.requireNonNull(player.position()));
    }

    // ========== Briefing Helpers ==========

    private List<String> buildBriefingLines(EnduranceQuest quest, ActiveQuestSession session) {
        List<String> lines = new ArrayList<>();
        if (quest != null) {
            if (quest.isEndlessMode() || quest.getTotalWaves() <= 0) lines.add(i18nToken("devmod.endurance.briefing.objective", i18nToken("devmod.endurance.briefing.objective.endless")));
            else lines.add(i18nToken("devmod.endurance.briefing.objective", i18nToken("devmod.endurance.briefing.objective.waves", Math.max(1, quest.getTotalWaves()))));
        }
        String kitLabel = resolveKitLabel(session);
        if (!kitLabel.isBlank()) lines.add(i18nToken("devmod.endurance.briefing.kit", kitLabel));
        String diffKey = resolveDifficultyKey(session);
        if (diffKey != null) lines.add(i18nToken("devmod.endurance.briefing.difficulty", i18nToken(diffKey)));
        String modeKey = resolveModeKey(quest, session);
        if (modeKey != null) lines.add(i18nToken("devmod.endurance.briefing.mode", i18nToken(modeKey)));
        if (session == null || !session.isPracticeMode()) lines.add(i18nToken("devmod.endurance.briefing.rewards"));
        return lines;
    }

    private String resolveDifficultyKey(@javax.annotation.Nullable ActiveQuestSession session) {
        if (session == null) return null;
        String d = session.getDifficultyLabel();
        if (d == null || d.isBlank()) return null;
        return "hard".equalsIgnoreCase(d) ? "devmod.endurance.difficulty.hard" : "devmod.endurance.difficulty.normal";
    }

    private String resolveModeKey(@javax.annotation.Nullable EnduranceQuest quest, @javax.annotation.Nullable ActiveQuestSession session) {
        if (session != null && session.isPracticeMode()) return "devmod.endurance.briefing.mode.practice";
        if (quest != null && quest.isEndlessMode()) return "devmod.endurance.briefing.mode.endless";
        return "devmod.endurance.briefing.mode.standard";
    }

    private String resolveKitLabel(@javax.annotation.Nullable ActiveQuestSession session) {
        if (session == null) return "";
        String kid = session.getKitId();
        if (kid == null || kid.isBlank()) return "";
        if ("TEMPORARY".equals(kid)) { String n = KitManager.INSTANCE.getTemporaryKitName(session.getPlayerId()); return (n != null && !n.isBlank()) ? n : i18nToken("devmod.endurance.briefing.kit.temporary"); }
        if (kid.length() == 8) { Optional<CustomKit> sk = KitManager.INSTANCE.getSyncedCustomKit(session.getPlayerId(), kid); if (sk.isPresent()) return sk.get().getName(); Optional<CustomKit> sv = KitManager.INSTANCE.getCustomKit(kid); if (sv.isPresent()) return sv.get().getName(); return i18nToken("devmod.endurance.briefing.kit.custom"); }
        KitPreset preset = KitManager.INSTANCE.getKitById(kid);
        if (preset != null) return i18nToken("devmod.endurance.briefing.kit.preset." + preset.name().toLowerCase(Locale.ROOT));
        return kid;
    }

    private String i18nToken(String key, Object... args) {
        StringBuilder b = new StringBuilder(I18N_PREFIX).append(key != null ? key : "");
        if (args != null) for (Object arg : args) b.append('|').append(arg == null ? "" : String.valueOf(arg).replace("|", "/"));
        return b.toString();
    }

    // ========== Quest Start (Prepared + Solo) ==========

    public Map<UUID, StartQuestResult> startPreparedQuest(List<ServerPlayer> players, ArenaContext arena, ResourceLocation mobId, QuestSettings settings, @javax.annotation.Nullable UUID instanceId, @javax.annotation.Nullable ArenaHandle arenaHandle) {
        return startPreparedQuest(players, arena, mobId, settings, instanceId, arenaHandle, null);
    }

    public Map<UUID, StartQuestResult> startPreparedQuest(List<ServerPlayer> players, ArenaContext arena, ResourceLocation mobId, QuestSettings settings, @javax.annotation.Nullable UUID instanceId, @javax.annotation.Nullable ArenaHandle arenaHandle, @javax.annotation.Nullable EnduranceQuest sharedQuest) {
        Map<UUID, StartQuestResult> results = new HashMap<>();
        String err = getTemplateSystemReadinessError();
        if (err != null) { LOGGER.error("[EnduranceQuest] startPreparedQuest blocked: {}", err); for (ServerPlayer p : players) if (p != null) results.put(p.getUUID(), new StartQuestResult(false, err, null)); return results; }
        if (arenaHandle == null) { String e2 = "ArenaHandle required; legacy arena path is deprecated."; arenaSetup.emitLegacyCall("missing_arena_handle", "startPreparedQuest", arena != null ? arena.getLevel() : null, useInstanceDimensions); LOGGER.error("[EnduranceQuest] startPreparedQuest blocked: {}", e2); for (ServerPlayer p : players) if (p != null) results.put(p.getUUID(), new StartQuestResult(false, e2, null)); return results; }
        if (!arenaSetup.isHandleValid(arenaHandle)) { String e2 = "Arena template missing required spawn slots."; arenaSetup.emitGateFailure("missing_spawn_slots", null); LOGGER.error("[EnduranceQuest] startPreparedQuest blocked: {}", e2); for (ServerPlayer p : players) if (p != null) results.put(p.getUUID(), new StartQuestResult(false, e2, null)); return results; }
        EnduranceQuest template = questTemplates.get(mobId);
        if (template == null) { for (ServerPlayer p : players) results.put(p.getUUID(), new StartQuestResult(false, "Unknown quest type: " + mobId, null)); return results; }

        EnduranceQuest quest = sharedQuest != null ? sharedQuest : new EnduranceQuest(template.getMobConfig());
        if (!quest.getMobId().equals(mobId)) { for (ServerPlayer p : players) results.put(p.getUUID(), new StartQuestResult(false, "Quest mob mismatch: " + mobId, null)); return results; }
        quest.setTotalWaves(settings.totalWaves); quest.setEndlessMode(settings.endlessMode);
        if (sharedQuest != null && quest.getState() != EnduranceQuestState.IN_PROGRESS) quest.start(arena.getId());

        for (ServerPlayer player : players) {
            if (player == null || !player.isAlive()) continue;
            UUID playerId = player.getUUID();
            ActiveQuestSession placeholder = new ActiveQuestSession(playerId, quest, null, System.currentTimeMillis());
            ActiveQuestSession existing = activeSessions.putIfAbsent(playerId, placeholder);
            if (existing != null) { results.put(playerId, new StartQuestResult(false, Objects.requireNonNull(I18n.translate("devmod.endurance.active_quest")).getString(), null)); continue; }
            if (sharedQuest == null) quest.start(arena.getId());
            ActiveQuestSession session = new ActiveQuestSession(playerId, quest, arena, System.currentTimeMillis(), settings.partyId, settings.questType, settings.getPlayerCount());
            UUID effInstId = instanceId != null ? instanceId : arenaHandle.instanceId();
            if (effInstId != null) session.setInstanceId(effInstId);
            session.setArenaHandle(arenaHandle); updateSnapshotArenaTemplate(player, arenaHandle);
            session.setDifficultyLabel(arenaSetup.resolveDifficultyLabel(settings, quest.getMobConfig()));
            session.setQuestTypeLabel(arenaSetup.resolveQuestTypeLabel(settings, quest.getMobConfig()));
            session.setKitId(settings.resolveKitId(playerId)); session.setPracticeMode(settings.practiceMode);
            if (settings.mobPoolConfig != null) session.setMobPoolConfig(settings.mobPoolConfig.copy());
            session.transitionTo(ActiveQuestSession.LifecycleState.ACTIVE, "prepared quest started");
            activeSessions.put(playerId, session);
            try { applyAndSyncArenaOverrides(player, session); PlayerStateServicesFacade.INSTANCE.preparePlayerForQuest(player, session); EnduranceEventHandler.onQuestStart(player, session);
            } catch (Exception e) { LOGGER.error("[EnduranceQuest] Failed to start quest for player {}", player.getName().getString(), e); activeSessions.remove(playerId); PlayerStateServicesFacade.INSTANCE.loadSnapshot(playerId).ifPresent(s -> PlayerStateServicesFacade.INSTANCE.performRecovery(player, s, "Quest start failed")); results.put(playerId, new StartQuestResult(false, "Quest start failed", null)); continue; }
            if (!session.isPracticeMode()) { try { TelemetryService.INSTANCE.startDungeonSession(player, "endurance_party_" + mobId.toString().replace(":", "_")); } catch (Exception e) { LOGGER.warn("[EnduranceQuest] Failed to start telemetry for player {}", player.getName().getString(), e); } }
            results.put(playerId, new StartQuestResult(true, "Quest started!", session));
            LOGGER.info("[EnduranceQuest] Started prepared quest for player {}: {}", player.getName().getString(), quest.getDisplayName());
        }
        for (StartQuestResult r : results.values()) {
            if (r.success() && r.session() != null) { WaveManager.INSTANCE.startWave(r.session()); boolean applyShared = true;
                for (ServerPlayer p : players) { if (p != null && results.get(p.getUUID()) != null && results.get(p.getUUID()).success()) { EnduranceEventHandler.onWaveStart(p, r.session(), 1, applyShared); applyShared = false; } } break; }
        }
        return results;
    }

    public Optional<EnduranceQuest> createSharedQuest(ResourceLocation mobId, UUID questId) {
        EnduranceQuest t = questTemplates.get(mobId); return t == null ? Optional.empty() : Optional.of(new EnduranceQuest(t.getMobConfig(), questId));
    }

    public record PreparedArenaResult(boolean success, @Nullable String errorMessage, @Nullable ArenaContext arena, @javax.annotation.Nullable ArenaHandle handle, @Nullable ResourceLocation mobId, @Nullable EnduranceQuestRegistry.MobQuestConfig mobConfig, @javax.annotation.Nullable UUID instanceId) {
        public static PreparedArenaResult success(ArenaContext arena, @javax.annotation.Nullable ArenaHandle handle, ResourceLocation mobId, EnduranceQuestRegistry.MobQuestConfig mobConfig, @javax.annotation.Nullable UUID instanceId) { return new PreparedArenaResult(true, null, arena, handle, mobId, mobConfig, instanceId); }
        public static PreparedArenaResult failure(String message) { return new PreparedArenaResult(false, message, null, null, null, null, null); }
    }

    // ========== Single Player Quest Flow ==========

    public StartQuestResult startQuest(ServerPlayer player, ResourceLocation mobId, QuestSettings settings) {
        UUID playerId = player.getUUID();
        DiagnosticLogger.quest("startQuest: player=%s, mobId=%s, waves=%d, endless=%s", player.getName().getString(), mobId, settings.totalWaves, settings.endlessMode);
        EnduranceQuest template = questTemplates.get(mobId);
        if (template == null) return new StartQuestResult(false, "Unknown quest type: " + mobId, null);
        String err = getTemplateSystemReadinessError(); if (err != null) return new StartQuestResult(false, err, null);
        EnduranceQuest quest = new EnduranceQuest(template.getMobConfig()); quest.setTotalWaves(settings.totalWaves); quest.setEndlessMode(settings.endlessMode);
        EnduranceLogger.phase(Phase.QUEST_START, player, quest.getQuestId(), "Starting quest: mob=%s, waves=%d, endless=%s, practice=%s", mobId, settings.totalWaves, settings.endlessMode, settings.practiceMode);
        ActiveQuestSession placeholder = new ActiveQuestSession(playerId, quest, null, System.currentTimeMillis());
        ActiveQuestSession existing = activeSessions.putIfAbsent(playerId, placeholder);
        if (existing != null) return new StartQuestResult(false, Objects.requireNonNull(I18n.translate("devmod.endurance.active_quest")).getString(), null);
        String err2 = getTemplateSystemReadinessError(); if (err2 != null) { activeSessions.remove(playerId); return new StartQuestResult(false, err2, null); }
        return startQuestInInstanceDimensionWithTemplate(player, mobId, quest, settings);
    }

    private StartQuestResult startQuestInInstanceDimensionWithTemplate(ServerPlayer player, ResourceLocation mobId, EnduranceQuest quest, QuestSettings settings) {
        UUID playerId = player.getUUID();
        ActiveQuestSession pendingSession = activeSessions.get(playerId);
        if (pendingSession == null) { LOGGER.error("[EnduranceQuest] No placeholder session found"); return new StartQuestResult(false, "Internal error: missing session", null); }
        ResolvedArena resolved = arenaSetup.resolveArenaTemplate(playerId, mobId, settings, player.getServer());
        if (resolved == null) { activeSessions.remove(playerId); return new StartQuestResult(false, "No matching arena template/policy", null); }
        pendingSession.setPending(true);
        pendingSession.transitionTo(ActiveQuestSession.LifecycleState.PREPARING, "pending instance start");
        pendingSession.setDifficultyLabel(arenaSetup.resolveDifficultyLabel(settings, quest.getMobConfig()));
        pendingSession.setQuestTypeLabel(arenaSetup.resolveQuestTypeLabel(settings, quest.getMobConfig()));
        pendingSession.setKitId(settings.resolveKitId(playerId)); pendingSession.setPracticeMode(settings.practiceMode);
        pendingSession.scheduleBriefing(BRIEFING_TICKS); pendingSession.scheduleInstanceStart(mobId, settings, resolved, PRE_TELEPORT_COUNTDOWN_TICKS);
        int briefingSec = (int) Math.ceil(BRIEFING_TICKS / 20.0);
        List<String> briefingLines = buildBriefingLines(quest, pendingSession); pendingSession.setBriefingLines(briefingLines);
        sendSoloSequenceUpdate(player, pendingSession, QuestSequencePayload.Phase.BRIEFING, briefingSec, quest.getDisplayName(), i18nToken("devmod.endurance.briefing.subtitle"), briefingLines);
        pendingSession.setLastBriefingSeconds(briefingSec);
        return new StartQuestResult(true, "Preparing instance...", pendingSession);
    }

    void startPendingInstanceQuest(ServerPlayer player, ActiveQuestSession session) {
        if (player == null || session == null) return;
        DiagnosticLogger.quest("startPendingInstanceQuest: player=%s", player.getName().getString());
        ResourceLocation mobId = session.getPendingMobId(); QuestSettings settings = session.getPendingSettings(); ResolvedArena resolved = session.getPendingResolved();
        session.clearPendingInstanceStart();
        if (mobId == null || settings == null || resolved == null) { LOGGER.error("[EnduranceQuest] Pending instance start missing data for player {}", player.getName().getString()); activeSessions.remove(session.getPlayerId()); sendSoloSequenceUpdate(player, session, QuestSequencePayload.Phase.CANCELLED, 0); return; }
        session.setLoadingProtection(true); session.transitionTo(ActiveQuestSession.LifecycleState.TELEPORTING, "instance creation started");
        com.devmod.network.NetworkHandler.sendInstanceLoadingShow(player, "Creating template instance...");
        player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("[DevMod] Creating instance dimension...").withStyle(SharedColorTokens.Chat.YELLOW)));
        EnduranceLogger.phase(Phase.INSTANCE_CREATE, player, session.getQuest().getQuestId(), "Starting instance creation: template=%s, mob=%s", resolved.template().id(), mobId);
        var startFuture = InstanceManager.INSTANCE.prepareInstanceQuest(player, resolved.template().id(), mobId.toString(), null);
        startFuture = startFuture.whenComplete((instanceId, throwable) -> {
            var server = player.getServer();
            if (server == null) { failPendingInstanceSetup(player, session, instanceId, "Server not available"); return; }
            server.execute(() -> {
                ActiveQuestSession cs = activeSessions.get(session.getPlayerId()); ServerPlayer cp = server.getPlayerList().getPlayer(Objects.requireNonNull(session.getPlayerId()));
                if (cp == null || cs == null) { if (instanceId != null) InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId); return; }
                if (throwable != null) { failPendingInstanceSetup(cp, cs, instanceId, "Failed to create instance: " + (throwable.getMessage() != null ? throwable.getMessage() : "Unknown error")); return; }
                if (instanceId == null) { failPendingInstanceSetup(cp, cs, null, "Failed to create instance"); return; }
                completeTemplateInstanceQuestSetup(cp, cs.getPlayerId(), mobId, cs.getQuest(), settings, resolved, instanceId);
            });
        });
        if (startFuture.isCancelled()) LOGGER.debug("[EnduranceQuest] Instance start cancelled for {}", mobId);
    }

    void sendSoloSequenceUpdate(ServerPlayer player, ActiveQuestSession session, QuestSequencePayload.Phase phase, int secondsRemaining) { sendSoloSequenceUpdate(player, session, phase, secondsRemaining, null, null, List.of()); }

    void sendSoloSequenceUpdate(ServerPlayer player, ActiveQuestSession session, QuestSequencePayload.Phase phase, int secondsRemaining, @javax.annotation.Nullable String title, @javax.annotation.Nullable String subtitle, List<String> infoLines) {
        if (player == null || session == null || session.isMultiplayer()) return;
        if (phase == QuestSequencePayload.Phase.CANCELLED) EnduranceTelemetryService.INSTANCE.recordCountdownCancelled(session.getQuest().getQuestId());
        PacketDistributor.sendToPlayer(player, new QuestSequencePayload(player.getUUID(), phase, Math.max(0, secondsRemaining), 0, List.of(), title, subtitle, infoLines != null ? infoLines : List.of()));
    }

    private void completeTemplateInstanceQuestSetup(ServerPlayer player, UUID playerId, ResourceLocation mobId, EnduranceQuest quest, QuestSettings settings, ResolvedArena resolved, @javax.annotation.Nullable UUID instanceId) {
        var server = player.getServer(); ActiveQuestSession pendingSession = activeSessions.get(playerId);
        if (server == null || server.getPlayerList().getPlayer(Objects.requireNonNull(playerId)) == null) { LOGGER.warn("[EnduranceQuest] Player {} disconnected during instance creation", playerId); activeSessions.remove(playerId); if (instanceId != null) InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId); return; }
        if (instanceId == null) { LOGGER.error("[EnduranceQuest] Template instance creation failed for player {}", playerId); activeSessions.remove(playerId); com.devmod.network.NetworkHandler.sendInstanceLoadingHide(player); sendSoloSequenceUpdate(player, pendingSession, QuestSequencePayload.Phase.CANCELLED, 0); player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("[DevMod] Failed to create instance").withStyle(SharedColorTokens.Chat.RED))); return; }
        Optional<InstanceData> instanceOpt = InstanceServicesFacade.INSTANCE.getInstance(instanceId);
        if (instanceOpt.isEmpty()) { InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId); activeSessions.remove(playerId); com.devmod.network.NetworkHandler.sendInstanceLoadingHide(player); sendSoloSequenceUpdate(player, pendingSession, QuestSequencePayload.Phase.CANCELLED, 0); player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("[DevMod] Instance not found").withStyle(SharedColorTokens.Chat.RED))); return; }
        InstanceData instance = instanceOpt.get(); var dimensionKey = instance.getDimensionKey();
        if (dimensionKey == null) { InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId); activeSessions.remove(playerId); com.devmod.network.NetworkHandler.sendInstanceLoadingHide(player); sendSoloSequenceUpdate(player, pendingSession, QuestSequencePayload.Phase.CANCELLED, 0); player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("[DevMod] Instance dimension not ready").withStyle(SharedColorTokens.Chat.RED))); return; }
        ServerLevel instanceLevel = server.getLevel(dimensionKey);
        if (instanceLevel == null) { InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId); activeSessions.remove(playerId); com.devmod.network.NetworkHandler.sendInstanceLoadingHide(player); sendSoloSequenceUpdate(player, pendingSession, QuestSequencePayload.Phase.CANCELLED, 0); player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("[DevMod] Instance level not found").withStyle(SharedColorTokens.Chat.RED))); return; }
        ArenaTemplate template = resolved.template(); OriginResolution origin = arenaSetup.resolveTemplateOrigin(template);

        if (arenaSetup.shouldBuildAsync(template)) {
            AsyncArenaBuilder asyncBuilder = arenaSetup.asyncBuildCoordinator.getOrCreate(instanceLevel);
            UUID arenaId = UUID.randomUUID();
            var buildFuture = asyncBuilder.submitBuildAsync(arenaId, template, origin.centerX(), origin.originY(), origin.centerZ());
            buildFuture = buildFuture.whenComplete((asyncResult, buildError) -> server.execute(() -> {
                ActiveQuestSession cs = activeSessions.get(playerId); ServerPlayer cp = server.getPlayerList().getPlayer(Objects.requireNonNull(playerId));
                if (cp == null || cs == null) { InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId); activeSessions.remove(playerId); return; }
                if (buildError != null || asyncResult == null || !asyncResult.success()) {
                    String msg = buildError != null && buildError.getMessage() != null ? buildError.getMessage() : (asyncResult != null ? asyncResult.errorMessage() : "Build failed");
                    var builder = arenaSetup.createTemplateBuilder(instanceLevel); BuildAttemptResult fa = arenaSetup.attemptFallbackOnly(builder, resolved, "solo_async", msg);
                    if (fa != null && fa.result().success()) { finalizeTemplateInstanceQuestSetup(cp, cs, mobId, quest, settings, fa.resolved(), instanceId, instanceLevel, fa.origin(), instance, fa.result()); return; }
                    String tech = msg; if (fa != null && fa.result() != null && fa.result().errorMessage() != null) tech = fa.result().errorMessage();
                    failPendingInstanceSetup(cp, cs, instanceId, arenaSetup.handleBuildAbort(resolved, "solo_async", tech, buildError, fa != null)); return;
                }
                finalizeTemplateInstanceQuestSetup(cp, cs, mobId, quest, settings, resolved, instanceId, instanceLevel, origin, instance, ArenaBuilder.BuildResult.success(asyncResult.arenaId(), template.id(), asyncResult.blocksPlaced(), asyncResult.durationMs()));
            }));
            if (buildFuture.isCancelled()) LOGGER.debug("[EnduranceQuest] Async solo arena build cancelled for {}", arenaId);
            return;
        }
        var builder = arenaSetup.createTemplateBuilder(instanceLevel);
        BuildAttemptResult attempt = arenaSetup.buildWithFallback(builder, resolved, origin, "solo_sync");
        if (!attempt.result().success()) { String msg = attempt.result().errorMessage() != null ? attempt.result().errorMessage() : "Build failed"; failPendingInstanceSetup(player, pendingSession, instanceId, arenaSetup.handleBuildAbort(attempt.resolved(), "solo_sync", msg, null, attempt.fallbackAttempted())); return; }
        finalizeTemplateInstanceQuestSetup(player, pendingSession, mobId, quest, settings, attempt.resolved(), instanceId, instanceLevel, attempt.origin(), instance, attempt.result());
    }

    private void failPendingInstanceSetup(ServerPlayer player, @javax.annotation.Nullable ActiveQuestSession pendingSession, @javax.annotation.Nullable UUID instanceId, String message) {
        if (instanceId != null) InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId);
        if (pendingSession != null) { pendingSession.setLoadingProtection(false); pendingSession.transitionTo(ActiveQuestSession.LifecycleState.FAILED, "instance setup failed"); activeSessions.remove(pendingSession.getPlayerId()); }
        if (player != null) { com.devmod.network.NetworkHandler.sendInstanceLoadingHide(player); if (pendingSession != null) sendSoloSequenceUpdate(player, pendingSession, QuestSequencePayload.Phase.CANCELLED, 0); player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("[DevMod] " + (message != null ? message : "Build failed")).withStyle(SharedColorTokens.Chat.RED))); }
    }

    private void finalizeTemplateInstanceQuestSetup(ServerPlayer player, ActiveQuestSession pendingSession, ResourceLocation mobId, EnduranceQuest quest, QuestSettings settings, ResolvedArena resolved, UUID instanceId, ServerLevel instanceLevel, OriginResolution origin, InstanceData instance, ArenaBuilder.BuildResult buildResult) {
        ArenaTemplate template = resolved.template();
        ArenaHandle handle = arenaSetup.createArenaHandle(buildResult, resolved, instanceId, origin, instanceLevel);
        if (!arenaSetup.isHandleValid(handle)) { failPendingInstanceSetup(player, pendingSession, instanceId, "Template missing required spawn slots (player/mob)"); return; }
        ArenaContext arena = arenaSetup.createArenaAdapter(instanceLevel, handle);
        arenaSetup.updateInstanceArenaMetadata(instance, template, handle, origin);
        com.devmod.network.NetworkHandler.sendInstanceLoadingHide(player);
        if (teleportPlayersToArena(List.of(player), arena, handle).isEmpty()) { failPendingInstanceSetup(player, pendingSession, instanceId, "No valid player spawn slots in template"); return; }
        updateSnapshotArenaTemplate(player, handle);
        quest.start(arena.getId());
        UUID effPlayerId = pendingSession != null ? pendingSession.getPlayerId() : player.getUUID();
        ActiveQuestSession session = new ActiveQuestSession(effPlayerId, quest, arena, System.currentTimeMillis(), settings.partyId, settings.questType, settings.getPlayerCount());
        session.setInstanceId(instanceId); session.setArenaHandle(handle);
        if (pendingSession != null) { session.setDifficultyLabel(pendingSession.getDifficultyLabel()); session.setQuestTypeLabel(pendingSession.getQuestTypeLabel()); session.setKitId(pendingSession.getKitId()); session.setPracticeMode(pendingSession.isPracticeMode()); }
        else { session.setDifficultyLabel(arenaSetup.resolveDifficultyLabel(settings, quest.getMobConfig())); session.setQuestTypeLabel(arenaSetup.resolveQuestTypeLabel(settings, quest.getMobConfig())); session.setKitId(settings.resolveKitId(effPlayerId)); session.setPracticeMode(settings.practiceMode); }
        session.transitionTo(ActiveQuestSession.LifecycleState.ACTIVE, "quest started");
        if (pendingSession != null) pendingSession.setLoadingProtection(false);
        session.setLoadingProtection(true); activeSessions.put(effPlayerId, session);
        applyAndSyncArenaOverrides(player, session); PlayerStateServicesFacade.INSTANCE.preparePlayerForQuest(player, session); EnduranceEventHandler.onQuestStart(player, session);
        if (!session.isPracticeMode()) TelemetryService.INSTANCE.startDungeonSession(player, "endurance_instance_" + mobId.toString().replace(":", "_"));
        session.scheduleSafeWindow(SAFE_WINDOW_TICKS);
        if (SAFE_WINDOW_TICKS > 0) { PlayerStateServicesFacade.INSTANCE.applySafeWindowEffects(player, SAFE_WINDOW_TICKS); session.setLastSafeWindowSeconds((int) Math.ceil(SAFE_WINDOW_TICKS / 20.0)); }
        session.scheduleWaveStart(WAVE_START_COUNTDOWN_TICKS);
        sendSoloSequenceUpdate(player, session, QuestSequencePayload.Phase.SAFE_WINDOW, (int) Math.ceil(SAFE_WINDOW_TICKS / 20.0), quest.getDisplayName(), "Safe window", List.of("Invulnerability active"));
        session.setLoadingProtection(false);
        EnduranceLogger.phase(Phase.INSTANCE_READY, player, quest.getQuestId(), "Instance ready: id=%s, template=%s", instanceId, template.id());
        EnduranceLogger.phase(Phase.PLAYER_TELEPORT, player, quest.getQuestId(), "Teleported to instance dimension: instanceId=%s, arenaPos=(%.1f, %.1f, %.1f), scheduling wave in %d ticks", instanceId, player.getX(), player.getY(), player.getZ(), WAVE_START_COUNTDOWN_TICKS);
        LOGGER.info("[EnduranceQuest] Player {} started TEMPLATE quest: {} (instance: {})", player.getName().getString(), quest.getDisplayName(), instanceId);
        player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("[DevMod] Quest started in instance dimension!").withStyle(SharedColorTokens.Chat.GREEN)));
    }

    private void updateSnapshotArenaTemplate(ServerPlayer player, ArenaHandle handle) {
        if (player == null || handle == null) return;
        PlayerStateServicesFacade.INSTANCE.loadSnapshot(player.getUUID()).ifPresent(s -> { s.withArenaTemplate(handle.templateId(), handle.templateVersion(), handle.policyId(), handle.policyVersion()); PlayerStateServicesFacade.INSTANCE.saveSnapshot(s); });
    }

    // ========== Session Management ==========

    public Optional<ActiveQuestSession> getActiveSession(UUID playerId) {
        ActiveQuestSession s = activeSessions.get(playerId);
        if (s == null || s.isInitializing() || s.isPending() || s.isLoadingProtection()) return Optional.empty();
        return Optional.of(s);
    }
    public Optional<ActiveQuestSession> getActiveSession(Player player) { return getActiveSession(player.getUUID()); }
    public boolean hasActiveQuest(@javax.annotation.Nullable UUID questId) {
        if (questId == null) return false;
        for (ActiveQuestSession s : activeSessions.values()) if (questId.equals(s.getQuest().getQuestId())) return true;
        PartyQuestSession ps = partyCoordinator.getPartySessionByQuest(questId).orElse(null);
        return ps != null && ps.isActive();
    }
    public boolean isGamificationEnabled() { ArenaTemplateConfig c = arenaSetup.getArenaTemplateConfig(); return c != null && c.gamificationEnabled(); }
    public @javax.annotation.Nullable ArenaPolicy getPolicyForSession(@javax.annotation.Nullable ActiveQuestSession session) {
        if (session == null) return null; var reg = arenaSetup.getArenaPolicyRegistry(); if (reg == null) return null;
        String pid = session.getPolicyId(); if (pid == null || pid.isBlank()) return null; return reg.get(pid).orElse(null);
    }

    void applyAndSyncArenaOverrides(ServerPlayer player, ActiveQuestSession session) {
        try {
            ArenaPolicy policy = getPolicyForSession(requireNonNull(session, "session")); if (policy == null) return;
            ArenaPolicy.GameplayOverrides overrides = policy.gameplayOverrides(); if (overrides == null) return;
            UUID questId = session.getQuest().getQuestId();
            EnduranceConfigManager.INSTANCE.setArenaOverride(questId, overrides);
            net.minecraft.nbt.CompoundTag overrideTag = serializeGameplayOverrides(overrides);
            PacketDistributor.sendToPlayer(Objects.requireNonNull(requireNonNull(player, "player")), Objects.requireNonNull(requireNonNull(GameMechanicsSyncPayload.forQuest(questId, overrideTag), "payload")));
            LOGGER.debug("[EnduranceQuest] Applied arena config overrides for quest {} to player {}", questId, player.getName().getString());
        } catch (Exception e) { LOGGER.warn("[EnduranceQuest] Failed to apply arena config overrides: {}", e.getMessage()); }
    }

    private net.minecraft.nbt.CompoundTag serializeGameplayOverrides(ArenaPolicy.GameplayOverrides overrides) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        ArenaPolicy.ComboOverrides co = overrides.combo();
        if (co != null) { net.minecraft.nbt.CompoundTag c = new net.minecraft.nbt.CompoundTag(); putIntIfPresent(c, "timeoutTicks", co.timeoutTicks()); putIntIfPresent(c, "basePoints", co.basePoints()); putDoubleIfPresent(c, "multiplierIncrement", co.multiplierIncrement()); putDoubleIfPresent(c, "maxMultiplier", co.maxMultiplier()); tag.put("combo", c); }
        ArenaPolicy.TensionOverrides to = overrides.tension();
        if (to != null) { net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag(); putDoubleIfPresent(t, "baseWaveGain", to.baseWaveGain()); putDoubleIfPresent(t, "noHitBonus", to.noHitBonus()); putDoubleIfPresent(t, "minThreshold", to.minThreshold()); putDoubleIfPresent(t, "maxThreshold", to.maxThreshold()); putIntIfPresent(t, "minWavesBeforeBoss", to.minWavesBeforeBoss()); putIntIfPresent(t, "maxWavesWithoutBoss", to.maxWavesWithoutBoss()); tag.put("tension", t); }
        ArenaPolicy.WaveOverrides wo = overrides.waves();
        if (wo != null) { net.minecraft.nbt.CompoundTag w = new net.minecraft.nbt.CompoundTag(); putIntIfPresent(w, "baseMobCount", wo.baseMobCount()); putDoubleIfPresent(w, "mobScaling", wo.mobScaling()); putIntIfPresent(w, "intermissionTicks", wo.intermissionTicks()); putIntIfPresent(w, "bossInterval", wo.bossInterval()); tag.put("waves", w); }
        ArenaPolicy.PerkRarityOverrides pr = overrides.perkRarity();
        if (pr != null) { net.minecraft.nbt.CompoundTag p = new net.minecraft.nbt.CompoundTag(); putIntIfPresent(p, "commonWeight", pr.commonWeight()); putIntIfPresent(p, "uncommonWeight", pr.uncommonWeight()); putIntIfPresent(p, "rareWeight", pr.rareWeight()); putIntIfPresent(p, "epicWeight", pr.epicWeight()); putIntIfPresent(p, "legendaryWeight", pr.legendaryWeight()); tag.put("perkRarity", p); }
        return tag;
    }

    private static void putIntIfPresent(net.minecraft.nbt.CompoundTag tag, String key, Integer value) { if (value != null) tag.putInt(Objects.requireNonNull(key), value); }
    private static void putDoubleIfPresent(net.minecraft.nbt.CompoundTag tag, String key, Double value) { if (value != null) tag.putDouble(Objects.requireNonNull(key), value); }

    // ========== Session Handler Delegation ==========

    public void abandonQuest(ServerPlayer player) { sessionHandler.abandonQuest(player); }
    public void handlePlayerDeath(ServerPlayer player) { sessionHandler.handlePlayerDeath(player); }
    public void handleRespawnChoice(ServerPlayer player, boolean continueQuest) { sessionHandler.handleRespawnChoice(player, continueQuest); }
    public void handleVanillaRespawn(ServerPlayer player) { sessionHandler.handleVanillaRespawn(player); }
    public void completeWave(ServerPlayer player) { sessionHandler.completeWave(player); }
    public void continueToNextWave(ServerPlayer player) { DiagnosticLogger.quest("continueToNextWave: player=%s", player.getName().getString()); sessionHandler.continueToNextWave(player); }
    public void exitAtCheckpoint(ServerPlayer player) { DiagnosticLogger.quest("exitAtCheckpoint: player=%s", player.getName().getString()); sessionHandler.exitAtCheckpoint(player); }
    public void handleCriticalTeleportFailure(ServerPlayer player, ActiveQuestSession session, String reason) {
        if (player == null || session == null) return;
        String safeReason = reason != null && !reason.isBlank() ? reason : "teleport_failed";
        if (session.getPartyId() != null) { partyCoordinator.getPartySession(session.getPartyId()).ifPresent(ps -> partyCoordinator.endPartyRun(ps, false, safeReason)); return; }
        if (sessionHandler != null) sessionHandler.forceFailQuest(player, session, safeReason);
    }

    // ========== Party Quest Delegation ==========

    public void completePartyWave(PartyQuestSession partySession) { partyCoordinator.completePartyWave(partySession); }
    public void requestPartyContinue(UUID playerId) { partyCoordinator.requestPartyContinue(playerId); }
    public PartyQuestSession registerPartySession(PartyQuestSession session) { return partyCoordinator.registerPartySession(session); }
    public Optional<PartyQuestSession> getPartySession(UUID partyId) { return partyCoordinator.getPartySession(partyId); }
    public Optional<PartyQuestSession> getPartySessionByPlayer(UUID playerId) { return partyCoordinator.getPartySessionByPlayer(playerId); }
    public Optional<PartyQuestSession> getPartySessionByQuest(UUID questId) { return partyCoordinator.getPartySessionByQuest(questId); }
    public boolean isPartyQuest(UUID questId) { return partyCoordinator.isPartyQuest(questId); }
    public void removePartySession(UUID partyId) { partyCoordinator.removePartySession(partyId); }
    public boolean isPartyRunActiveForPlayer(UUID playerId) { return partyCoordinator.isPartyRunActiveForPlayer(playerId); }
    public void markPartyMemberInactive(UUID playerId, String reason) { partyCoordinator.markPartyMemberInactive(playerId, reason); }
    public void markPartyMemberActive(UUID playerId) { partyCoordinator.markPartyMemberActive(playerId); }
    public boolean attachPartyMemberSession(ServerPlayer player, PartyQuestSession partySession) { return partyCoordinator.attachPartyMemberSession(player, partySession); }
    public boolean rejoinPartyMember(ServerPlayer player) { return partyCoordinator.rejoinPartyMember(player); }
    public void endPartyRun(PartyQuestSession partySession, boolean completed, String reason) { partyCoordinator.endPartyRun(partySession, completed, reason); }

    // ========== Combat Events ==========

    public void recordKill(ServerPlayer player, ResourceLocation killedMobId) { ActiveQuestSession s = activeSessions.get(player.getUUID()); if (s != null && s.quest.getMobId().equals(killedMobId)) { s.quest.recordKill(); s.incrementKillCount(); } }
    public void recordDamageDealt(ServerPlayer player, float damage) { ActiveQuestSession s = activeSessions.get(player.getUUID()); if (s != null) s.quest.recordDamageDealt(damage); }
    public void recordDamageTaken(ServerPlayer player, float damage) { ActiveQuestSession s = activeSessions.get(player.getUUID()); if (s != null) s.quest.recordDamageTaken(damage); }

    // ========== Query Methods ==========

    public Collection<EnduranceQuest> getAllQuestTemplates() { return Collections.unmodifiableCollection(questTemplates.values()); }
    public Optional<EnduranceQuest> getQuestTemplate(ResourceLocation mobId) { return Optional.ofNullable(questTemplates.get(mobId)); }
    public PlayerQuestStats getPlayerStats(UUID playerId) { return persistence.getPlayerStats(playerId); }
    public boolean isPlayerInQuest(UUID playerId) { return activeSessions.containsKey(playerId); }
    public Map<UUID, ActiveQuestSession> getActiveSessions() { return Collections.unmodifiableMap(activeSessions); }

    // ========== Data Reset ==========

    public void clearAllPlayerStats() {
        LOGGER.info("[EnduranceQuest] Clearing all player stats and quest data...");
        persistence.clearAllStats(); questTemplates.clear(); persistence.deleteAllStatsFiles();
        LOGGER.info("[EnduranceQuest] All player stats cleared successfully");
        try { RewardSystem.INSTANCE.resetAll(); } catch (Exception e) { LOGGER.warn("[EnduranceQuest] Could not reset RewardSystem: {}", e.getMessage()); }
        try { GamificationManager.INSTANCE.resetAll(); } catch (Exception e) { LOGGER.warn("[EnduranceQuest] Could not reset GamificationManager: {}", e.getMessage()); }
        for (EnduranceQuestRegistry.MobQuestConfig mc : EnduranceQuestRegistry.INSTANCE.getAllMobConfigs()) questTemplates.put(mc.getMobId(), new EnduranceQuest(mc));
    }

    public int cleanupExpiredOverrides() { return arenaSetup.cleanupExpiredOverrides(); }

    private void handleForcedShutdownCleanup(ActiveQuestSession session, boolean cleanupShared) {
        try {
            EnduranceQuest quest = session.quest; UUID questId = quest.getQuestId(); UUID playerId = session.getPlayerId();
            if (cleanupShared) EnduranceConfigManager.INSTANCE.cleanupQuest(questId);
            EnduranceTelemetryService.INSTANCE.recordQuestEnd(questId, EnduranceQuestState.FAILED, quest.getCurrentWave(), quest.getSessionDuration(), quest.getMobsKilledThisSession(), quest.getTotalDamageDealtThisSession(), quest.getDamageTakenThisSession());
            persistence.updatePlayerStats(playerId, quest, false);
            if (cleanupShared) CombatTracker.INSTANCE.stopTracking(questId);
            if (ComboSystemFacade.isInitialized()) ComboSystemFacade.get().endSession(playerId);
            if (cleanupShared) { MutatorSystem.INSTANCE.endSession(questId); EnduranceEventCombat.removeMutatorSession(questId); }
            PerkSystem.INSTANCE.endSession(playerId);
            if (cleanupShared) { PlayerStateServicesFacade.INSTANCE.cleanupQuestSystems(session); PlayerStateServicesFacade.INSTANCE.cleanupArenaOrInstance(session, false); }
        } catch (Exception e) { LOGGER.warn("[EnduranceQuest] Failed shutdown cleanup for session {}", session.getPlayerId(), e); }
    }

    // ========== Inner Classes (kept for backward compatibility) ==========

    public static class QuestSettings {
        public int totalWaves = 10;
        public boolean endlessMode = false;
        public int arenaSize = 64;
        public QuestType questType = QuestType.PVE_COOP;
        public UUID partyId = null;
        public List<UUID> partyMemberIds = List.of();
        public Map<UUID, String> partyKitIds = Map.of();
        public String kitId = "STARTER";
        @javax.annotation.Nullable public String forceTemplateId = null;
        public boolean practiceMode = false;
        public @javax.annotation.Nullable com.devmod.endurance.config.EnduranceMobPoolConfig mobPoolConfig = null;
        public QuestSettings() {}
        public QuestSettings waves(int waves) { this.totalWaves = waves; return this; }
        public QuestSettings endless() { this.endlessMode = true; return this; }
        public QuestSettings arenaSize(int size) { this.arenaSize = size; return this; }
        public QuestSettings questType(QuestType type) { this.questType = type; this.arenaSize = type.getDefaultArenaSize(); return this; }
        public QuestSettings party(UUID partyId, List<UUID> memberIds) { this.partyId = partyId; this.partyMemberIds = memberIds; return this; }
        public QuestSettings partyKits(Map<UUID, String> kitIds) { this.partyKitIds = kitIds != null ? kitIds : Map.of(); return this; }
        public QuestSettings forceTemplate(@javax.annotation.Nullable String templateId) { this.forceTemplateId = templateId; return this; }
        public QuestSettings mobPoolConfig(@javax.annotation.Nullable com.devmod.endurance.config.EnduranceMobPoolConfig config) { this.mobPoolConfig = config != null ? config.copy() : null; return this; }
        public boolean isMultiplayer() { return partyId != null && !partyMemberIds.isEmpty(); }
        public int getPlayerCount() { return Math.max(1, partyMemberIds.size()); }
        public String resolveKitId(@javax.annotation.Nullable UUID playerId) { if (playerId != null && partyKitIds != null) { String kit = partyKitIds.get(playerId); if (kit != null && !kit.isBlank()) return kit; } return kitId != null ? kitId : "STARTER"; }
    }

    public record StartQuestResult(boolean success, String message, @Nullable ActiveQuestSession session) {}

    public static class ActiveQuestSession {
        public enum LifecycleState { INITIALIZING, PREPARING, TELEPORTING, ACTIVE, AWAITING_RESPAWN, CLEANUP, COMPLETED, FAILED }
        private final UUID playerId;
        final EnduranceQuest quest;
        final ArenaContext arena;
        private final long startTime;
        private int killsInCurrentWave = 0;
        private final AtomicBoolean awaitingRespawnChoice = new AtomicBoolean(false);
        private final AtomicBoolean respawnRequested = new AtomicBoolean(false);
        private long abandonConfirmUntilMs = 0;
        private long lastDimensionRecoveryMs = 0;
        private long lastConfinementLogMs = 0;
        private volatile LifecycleState lifecycleState = LifecycleState.INITIALIZING;
        private volatile long lifecycleUpdatedAtMs = System.currentTimeMillis();
        private UUID instanceId;
        private @javax.annotation.Nullable ArenaHandle arenaHandle;
        private @javax.annotation.Nullable String templateId;
        private @javax.annotation.Nullable Integer templateVersion;
        private @javax.annotation.Nullable String policyId;
        private @javax.annotation.Nullable Integer policyVersion;
        private boolean pending = false;
        private boolean loadingProtection = false;
        private final AtomicInteger pendingInstanceStartTicks = new AtomicInteger(0);
        private volatile int lastTeleportCountdownSeconds = -1;
        private @javax.annotation.Nullable ResourceLocation pendingMobId;
        private @javax.annotation.Nullable QuestSettings pendingSettings;
        private @javax.annotation.Nullable ResolvedArena pendingResolved;
        private final AtomicInteger pendingBriefingTicks = new AtomicInteger(0);
        private volatile int lastBriefingSeconds = -1;
        private List<String> briefingLines = List.of();
        private final AtomicInteger pendingWaveStartTicks = new AtomicInteger(0);
        private volatile int lastWaveCountdownSeconds = -1;
        private final AtomicInteger pendingSafeWindowTicks = new AtomicInteger(0);
        private volatile int lastSafeWindowSeconds = -1;
        private final AtomicInteger pendingBossIntroTicks = new AtomicInteger(0);
        private volatile int lastBossIntroSeconds = -1;
        private boolean respawnCountdownActive = false;
        private List<WaveDirective> pendingDirectives = List.of();
        private @javax.annotation.Nullable String selectedDirectiveId;
        private int directiveWaveNumber = -1;
        private boolean partySpectator = false;
        private GameType originalGameMode;
        private ListTag savedInventory;
        private ListTag savedArmor;
        private ListTag savedOffhand;
        private UUID partyId;
        private QuestType questType = QuestType.PVE_COOP;
        private int playerCount = 1;
        private String difficultyLabel;
        private String questTypeLabel;
        private String kitId = "STARTER";
        private boolean practiceMode = false;
        private final Map<String, String> configOverrides = new HashMap<>();
        private @javax.annotation.Nullable com.devmod.endurance.config.EnduranceMobPoolConfig mobPoolConfig;

        public ActiveQuestSession(UUID playerId, EnduranceQuest quest, @Nullable ArenaContext arena, long startTime) { this.playerId = playerId; this.quest = quest; this.arena = arena; this.startTime = startTime; }
        public ActiveQuestSession(UUID playerId, EnduranceQuest quest, ArenaContext arena, long startTime, UUID partyId, QuestType questType, int playerCount) { this.playerId = playerId; this.quest = quest; this.arena = arena; this.startTime = startTime; this.partyId = partyId; this.questType = questType; this.playerCount = Math.max(1, playerCount); }
        public UUID getPlayerId() { return playerId; }
        public EnduranceQuest getQuest() { return quest; }
        public ArenaContext getArena() { return arena; }
        public long getStartTime() { return startTime; }
        public int getKillsInCurrentWave() { return killsInCurrentWave; }
        public boolean isAwaitingRespawnChoice() { return awaitingRespawnChoice.get(); }
        public boolean isRespawnRequested() { return respawnRequested.get(); }
        public LifecycleState getLifecycleState() { return lifecycleState; }
        public long getLifecycleUpdatedAtMs() { return lifecycleUpdatedAtMs; }
        public boolean isLifecycleActive() { return lifecycleState == LifecycleState.ACTIVE; }
        public boolean shouldProcessGameplay() { return lifecycleState == LifecycleState.ACTIVE; }
        public synchronized boolean transitionTo(LifecycleState next, @javax.annotation.Nullable String reason) {
            LifecycleState current = this.lifecycleState; if (current == next) return true;
            boolean valid = isValidTransition(current, next); this.lifecycleState = next; this.lifecycleUpdatedAtMs = System.currentTimeMillis();
            if (!valid) LOGGER.warn("[EnduranceQuest] Lifecycle transition forced for {}: {} -> {} (reason={})", playerId, current, next, reason);
            else LOGGER.debug("[EnduranceQuest] Lifecycle transition for {}: {} -> {} (reason={})", playerId, current, next, reason);
            return valid;
        }
        private static boolean isValidTransition(LifecycleState from, LifecycleState to) {
            return switch (from) {
                case INITIALIZING -> to != LifecycleState.COMPLETED;
                case PREPARING -> to == LifecycleState.TELEPORTING || to == LifecycleState.ACTIVE || to == LifecycleState.FAILED || to == LifecycleState.CLEANUP;
                case TELEPORTING -> to == LifecycleState.ACTIVE || to == LifecycleState.FAILED || to == LifecycleState.CLEANUP;
                case ACTIVE -> to == LifecycleState.AWAITING_RESPAWN || to == LifecycleState.CLEANUP || to == LifecycleState.COMPLETED || to == LifecycleState.FAILED;
                case AWAITING_RESPAWN -> to == LifecycleState.TELEPORTING || to == LifecycleState.CLEANUP || to == LifecycleState.FAILED || to == LifecycleState.ACTIVE;
                case CLEANUP -> to == LifecycleState.COMPLETED || to == LifecycleState.FAILED;
                case COMPLETED, FAILED -> false;
            };
        }
        public void setAwaitingRespawnChoice(boolean awaiting) { this.awaitingRespawnChoice.set(awaiting); }
        public boolean compareAndSetAwaitingRespawnChoice(boolean expect, boolean update) { return this.awaitingRespawnChoice.compareAndSet(expect, update); }
        public void setRespawnRequested(boolean r) { this.respawnRequested.set(r); }
        public boolean compareAndSetRespawnRequested(boolean expect, boolean update) { return this.respawnRequested.compareAndSet(expect, update); }
        public boolean canAttemptDimensionRecovery(long nowMs, long cooldownMs) { return nowMs - lastDimensionRecoveryMs >= cooldownMs; }
        public void markDimensionRecoveryAttempt(long nowMs) { this.lastDimensionRecoveryMs = nowMs; }
        public boolean canLogConfinement(long nowMs, long cooldownMs) { return nowMs - lastConfinementLogMs >= cooldownMs; }
        public void markConfinementLog(long nowMs) { this.lastConfinementLogMs = nowMs; }
        public boolean confirmAbandonRequest(long windowMs) { long now = System.currentTimeMillis(); if (abandonConfirmUntilMs >= now) { abandonConfirmUntilMs = 0; return true; } abandonConfirmUntilMs = now + Math.max(0L, windowMs); return false; }
        public void clearAbandonConfirm() { abandonConfirmUntilMs = 0; }
        public void incrementKillCount() { killsInCurrentWave++; }
        public void resetWaveKills() { killsInCurrentWave = 0; }
        public boolean isWaveComplete() { return killsInCurrentWave >= quest.getCurrentWaveMobCount(); }
        public GameType getOriginalGameMode() { return originalGameMode; }
        public void setOriginalGameMode(GameType mode) { this.originalGameMode = mode; }
        public ListTag getSavedInventory() { return savedInventory; }
        public void setSavedInventory(ListTag inv) { this.savedInventory = inv; }
        public ListTag getSavedArmor() { return savedArmor; }
        public void setSavedArmor(ListTag armor) { this.savedArmor = armor; }
        public ListTag getSavedOffhand() { return savedOffhand; }
        public void setSavedOffhand(ListTag offhand) { this.savedOffhand = offhand; }
        public UUID getInstanceId() { return instanceId; }
        public void setInstanceId(UUID instanceId) { this.instanceId = instanceId; }
        public boolean isInInstanceDimension() { return instanceId != null; }
        public @javax.annotation.Nullable ArenaHandle getArenaHandle() { return arenaHandle; }
        public void setArenaHandle(@javax.annotation.Nullable ArenaHandle ah) { this.arenaHandle = ah; if (ah != null) { this.templateId = ah.templateId(); this.templateVersion = ah.templateVersion(); this.policyId = ah.policyId(); this.policyVersion = ah.policyVersion(); } }
        public @javax.annotation.Nullable String getTemplateId() { return templateId; }
        public @javax.annotation.Nullable Integer getTemplateVersion() { return templateVersion; }
        public @javax.annotation.Nullable String getPolicyId() { return policyId; }
        public @javax.annotation.Nullable Integer getPolicyVersion() { return policyVersion; }
        public boolean isPending() { return pending; }
        public void setPending(boolean pending) { this.pending = pending; }
        public boolean isInitializing() { return arena == null; }
        public boolean isLoadingProtection() { return loadingProtection; }
        public void setLoadingProtection(boolean lp) { this.loadingProtection = lp; }
        public void scheduleInstanceStart(ResourceLocation mobId, QuestSettings settings, ResolvedArena resolved, int ticks) { this.pendingMobId = mobId; this.pendingSettings = settings; this.pendingResolved = resolved; this.pendingInstanceStartTicks.set(Math.max(0, ticks)); this.lastTeleportCountdownSeconds = -1; }
        public void scheduleBriefing(int ticks) { this.pendingBriefingTicks.set(Math.max(0, ticks)); this.lastBriefingSeconds = -1; }
        public boolean isBriefingPending() { return pendingBriefingTicks.get() > 0; }
        public int tickBriefingCountdown() { int c; do { c = pendingBriefingTicks.get(); if (c <= 0) return 0; } while (!pendingBriefingTicks.compareAndSet(c, c - 1)); return c - 1; }
        public int getLastBriefingSeconds() { return lastBriefingSeconds; }
        public void setLastBriefingSeconds(int s) { this.lastBriefingSeconds = s; }
        public List<String> getBriefingLines() { return briefingLines; }
        public void setBriefingLines(List<String> bl) { this.briefingLines = bl != null ? List.copyOf(bl) : List.of(); }
        public boolean isInstanceStartPending() { return pendingInstanceStartTicks.get() > 0 && pendingMobId != null && pendingSettings != null && pendingResolved != null; }
        public int tickInstanceStartCountdown() { int c; do { c = pendingInstanceStartTicks.get(); if (c <= 0) return 0; } while (!pendingInstanceStartTicks.compareAndSet(c, c - 1)); return c - 1; }
        public int getLastTeleportCountdownSeconds() { return lastTeleportCountdownSeconds; }
        public void setLastTeleportCountdownSeconds(int s) { this.lastTeleportCountdownSeconds = s; }
        public @javax.annotation.Nullable ResourceLocation getPendingMobId() { return pendingMobId; }
        public @javax.annotation.Nullable QuestSettings getPendingSettings() { return pendingSettings; }
        public @javax.annotation.Nullable ResolvedArena getPendingResolved() { return pendingResolved; }
        public void clearPendingInstanceStart() { pendingInstanceStartTicks.set(0); lastTeleportCountdownSeconds = -1; pendingMobId = null; pendingSettings = null; pendingResolved = null; }
        public void scheduleWaveStart(int ticks) { if (ticks <= 0) { pendingWaveStartTicks.set(0); lastWaveCountdownSeconds = -1; return; } pendingWaveStartTicks.set(ticks); lastWaveCountdownSeconds = -1; }
        public boolean isWaveStartPending() { return pendingWaveStartTicks.get() > 0; }
        public int tickWaveStartCountdown() { int c; do { c = pendingWaveStartTicks.get(); if (c <= 0) return 0; } while (!pendingWaveStartTicks.compareAndSet(c, c - 1)); return c - 1; }
        public int getLastWaveCountdownSeconds() { return lastWaveCountdownSeconds; }
        public void setLastWaveCountdownSeconds(int s) { this.lastWaveCountdownSeconds = s; }
        public void clearPendingWaveStart() { pendingWaveStartTicks.set(0); lastWaveCountdownSeconds = -1; }
        public void scheduleSafeWindow(int ticks) { if (ticks <= 0) { pendingSafeWindowTicks.set(0); lastSafeWindowSeconds = -1; return; } pendingSafeWindowTicks.set(ticks); lastSafeWindowSeconds = -1; }
        public boolean isSafeWindowPending() { return pendingSafeWindowTicks.get() > 0; }
        public int tickSafeWindowCountdown() { int c; do { c = pendingSafeWindowTicks.get(); if (c <= 0) return 0; } while (!pendingSafeWindowTicks.compareAndSet(c, c - 1)); return c - 1; }
        public int getLastSafeWindowSeconds() { return lastSafeWindowSeconds; }
        public void setLastSafeWindowSeconds(int s) { this.lastSafeWindowSeconds = s; }
        public void clearPendingSafeWindow() { pendingSafeWindowTicks.set(0); lastSafeWindowSeconds = -1; }
        public void scheduleBossIntro(int ticks) { if (ticks <= 0) { pendingBossIntroTicks.set(0); lastBossIntroSeconds = -1; return; } pendingBossIntroTicks.set(ticks); lastBossIntroSeconds = -1; }
        public boolean isBossIntroPending() { return pendingBossIntroTicks.get() > 0; }
        public int tickBossIntroCountdown() { int c; do { c = pendingBossIntroTicks.get(); if (c <= 0) return 0; } while (!pendingBossIntroTicks.compareAndSet(c, c - 1)); return c - 1; }
        public int getLastBossIntroSeconds() { return lastBossIntroSeconds; }
        public void setLastBossIntroSeconds(int s) { this.lastBossIntroSeconds = s; }
        public void clearPendingBossIntro() { pendingBossIntroTicks.set(0); lastBossIntroSeconds = -1; }
        public boolean isRespawnCountdownActive() { return respawnCountdownActive; }
        public void setRespawnCountdownActive(boolean active) { this.respawnCountdownActive = active; }
        public boolean isPartySpectator() { return partySpectator; }
        public void setPartySpectator(boolean ps) { this.partySpectator = ps; }
        public void clearAllSequences() { clearPendingWaveStart(); clearPendingSafeWindow(); clearPendingBossIntro(); clearPendingInstanceStart(); pendingBriefingTicks.set(0); lastBriefingSeconds = -1; briefingLines = List.of(); respawnCountdownActive = false; clearDirectives(); }
        public UUID getPartyId() { return partyId; }
        public void setPartyId(UUID partyId) { this.partyId = partyId; }
        public QuestType getQuestType() { return questType; }
        public void setQuestType(QuestType qt) { this.questType = qt; }
        public int getPlayerCount() { return playerCount; }
        public void setPlayerCount(int pc) { this.playerCount = Math.max(1, pc); }
        public boolean isMultiplayer() { return partyId != null && playerCount > 1; }
        public String getDifficultyLabel() { return difficultyLabel; }
        public void setDifficultyLabel(String dl) { this.difficultyLabel = dl; }
        public String getQuestTypeLabel() { return questTypeLabel; }
        public void setQuestTypeLabel(String qtl) { this.questTypeLabel = qtl; }
        public String getKitId() { return kitId; }
        public void setKitId(String kitId) { this.kitId = kitId != null ? kitId : "STARTER"; }
        public boolean isPracticeMode() { return practiceMode; }
        public void setPracticeMode(boolean pm) { this.practiceMode = pm; }
        public void setPendingDirectives(List<WaveDirective> directives, int waveNumber) { this.pendingDirectives = directives != null ? List.copyOf(directives) : List.of(); this.directiveWaveNumber = waveNumber; this.selectedDirectiveId = null; }
        public List<WaveDirective> getPendingDirectives() { return pendingDirectives; }
        public boolean hasPendingDirectives() { return pendingDirectives != null && !pendingDirectives.isEmpty(); }
        public int getDirectiveWaveNumber() { return directiveWaveNumber; }
        public void selectDirective(@javax.annotation.Nullable String directiveId) { if (directiveId == null || pendingDirectives == null) return; for (WaveDirective d : pendingDirectives) if (d != null && d.id().equals(directiveId)) { this.selectedDirectiveId = directiveId; return; } }
        public @javax.annotation.Nullable WaveDirective consumeDirectiveForWave(int waveNumber) {
            if (waveNumber != directiveWaveNumber) return null;
            WaveDirective selected = null;
            if (selectedDirectiveId != null) { for (WaveDirective d : pendingDirectives) if (d != null && d.id().equals(selectedDirectiveId)) { selected = d; break; } }
            else { for (WaveDirective d : pendingDirectives) if (d != null && "steady".equals(d.id())) { selected = d; break; } }
            clearDirectives(); return selected;
        }
        public void clearDirectives() { pendingDirectives = List.of(); selectedDirectiveId = null; directiveWaveNumber = -1; }
        public boolean isHost(UUID uuid) { return playerId.equals(uuid); }
        public void setConfigOverride(String key, String value) { if (key != null && value != null) configOverrides.put(key, value); }
        public @javax.annotation.Nullable String getConfigOverride(String key) { return configOverrides.get(key); }
        public Map<String, String> getConfigOverrides() { return Collections.unmodifiableMap(configOverrides); }
        public void clearConfigOverrides() { configOverrides.clear(); }
        public void setMobPoolConfig(@javax.annotation.Nullable com.devmod.endurance.config.EnduranceMobPoolConfig config) { this.mobPoolConfig = config; }
        public @javax.annotation.Nullable com.devmod.endurance.config.EnduranceMobPoolConfig getMobPoolConfig() { return mobPoolConfig; }
        public boolean hasMobPoolConfig() { return mobPoolConfig != null && mobPoolConfig.hasModifications(); }
    }

    public static class PlayerQuestStats {
        private final UUID playerId;
        private int totalQuestsAttempted = 0, totalQuestsCompleted = 0, totalPointsEarned = 0, totalMobsKilled = 0;
        private long totalPlayTime = 0;
        private final Map<String, MobQuestRecord> mobRecords = new HashMap<>();
        public PlayerQuestStats(UUID playerId) { this.playerId = playerId; }
        public void recordQuestAttempt(ResourceLocation mobId, boolean completed, int points, int wavesReached, long duration, int mobsKilled) {
            totalQuestsAttempted++; if (completed) totalQuestsCompleted++; totalPointsEarned += points; totalMobsKilled += mobsKilled; totalPlayTime += duration;
            MobQuestRecord r = mobRecords.computeIfAbsent(mobId.toString(), k -> new MobQuestRecord()); r.attempts++; if (completed) r.completions++; if (points > r.bestScore) r.bestScore = points; if (wavesReached > r.highestWave) r.highestWave = wavesReached;
        }
        public UUID getPlayerId() { return playerId; }
        public int getTotalQuestsAttempted() { return totalQuestsAttempted; }
        public int getTotalMobsKilled() { return totalMobsKilled; }
        public int getTotalQuestsCompleted() { return totalQuestsCompleted; }
        public int getTotalPointsEarned() { return totalPointsEarned; }
        public long getTotalPlayTime() { return totalPlayTime; }
        public Map<String, MobQuestRecord> getMobRecords() { return mobRecords; }
        public float getCompletionRate() { return totalQuestsAttempted > 0 ? (float) totalQuestsCompleted / totalQuestsAttempted : 0; }
    }

    public static class MobQuestRecord { public int attempts = 0; public int completions = 0; public int bestScore = 0; public int highestWave = 0; }
}
