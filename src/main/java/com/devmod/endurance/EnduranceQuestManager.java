package com.devmod.endurance;

import com.devmod.arena.api.ArenaHandle;
import com.devmod.arena.builder.AsyncArenaBuildCoordinator;
import com.devmod.arena.builder.AsyncArenaBuilder;
import com.devmod.arena.builder.ArenaBuilder;
import com.devmod.arena.builder.ChunkLoadingManager;
import com.devmod.arena.config.ArenaTemplateConfig;
import com.devmod.arena.config.InstanceLimitConfig;
import com.devmod.arena.error.UserFriendlyError;
import com.devmod.arena.fallback.CircuitBreaker;
import com.devmod.arena.fallback.FallbackMetrics;
import com.devmod.arena.integration.MinecraftBlockPlacer;
import com.devmod.arena.integration.MinecraftEntitySpawner;
import com.devmod.arena.override.OverrideManager;
import com.devmod.arena.override.ForceTemplateCapability;
import com.devmod.arena.policy.ArenaPolicy;
import com.devmod.arena.policy.ArenaPolicyRegistry;
import com.devmod.arena.policy.PolicyResolver;
import com.devmod.arena.policy.ResolveContext;
import com.devmod.arena.policy.ResolvedArena;
import com.devmod.arena.pool.PrebuildPoolManager;
import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.registry.ArenaTemplateRegistry;
import com.devmod.arena.registry.TemplateSpawnValidator;
import com.devmod.arena.telemetry.ArenaTelemetry;
import com.devmod.DevMod;
import com.devmod.runtime.InstanceData;
import com.devmod.runtime.InstanceManager;
import com.devmod.runtime.InstanceRegistry;
import com.devmod.runtime.RecoverySystem;
import com.devmod.party.QuestSequencePayload;
import com.devmod.telemetry.TelemetryService;
import com.devmod.telemetry.endurance.EnduranceTelemetryService;
import com.devmod.endurance.config.EnduranceConfigManager;
import com.devmod.network.GameMechanicsSyncPayload;
import com.devmod.util.I18n;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.nbt.ListTag;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Central manager for all Endurance Quest operations.
 * Handles quest creation, player sessions, persistence, and coordination.
 *
 * Delegates to:
 * - EnduranceQuestPersistence: Player stats loading/saving
 * - EndurancePlayerStateManager: Player state management during quests
 * - EnduranceSessionHandler: Session lifecycle events
 */

public class EnduranceQuestManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnduranceQuestManager.class);

    public static final EnduranceQuestManager INSTANCE = new EnduranceQuestManager();

    // Active quests per player (player UUID -> active quest)
    private final Map<UUID, ActiveQuestSession> activeSessions = new ConcurrentHashMap<>();

    // Quest templates (mob ID -> quest template with best records)
    private final Map<ResourceLocation, EnduranceQuest> questTemplates = new ConcurrentHashMap<>();

    // Delegate classes
    private final EnduranceQuestPersistence persistence = new EnduranceQuestPersistence();
    private volatile EnduranceSessionHandler sessionHandler;


    // Data directory
    private Path dataDirectory;

    private boolean initialized = false;

    // Instance dimension mode flag - when true, quests run in isolated temporary dimensions
    private boolean useInstanceDimensions = true;

    // Arena template system integration (L1/L2)
    private ArenaTemplateRegistry arenaTemplateRegistry;
    private ArenaPolicyRegistry arenaPolicyRegistry;
    private PolicyResolver policyResolver;
    private OverrideManager overrideManager;
    private @javax.annotation.Nullable ForceTemplateCapability forceTemplateCapability;
    private ArenaTelemetry arenaTelemetry;
    private ArenaTemplateConfig arenaTemplateConfig;
    private ArenaTemplateConfig.ConfigSnapshot arenaConfigSnapshot;
    private final PrebuildPoolManager prebuildPoolManager = new PrebuildPoolManager();
    private final AsyncArenaBuildCoordinator asyncBuildCoordinator =
        new AsyncArenaBuildCoordinator(() -> arenaConfigSnapshot);
    private static final long INSTANCE_CREATION_TIMEOUT_SECONDS = 30;
    public static final int PRE_TELEPORT_COUNTDOWN_TICKS = 200;
    public static final int WAVE_START_COUNTDOWN_TICKS = 200;
    public static final int BRIEFING_TICKS = 80;
    public static final int SAFE_WINDOW_TICKS = 60;
    public static final int BOSS_INTRO_TICKS = 20;
    private static final String FALLBACK_TEMPLATE_ID = "default_flat_64";
    private static final CircuitBreaker BUILD_FALLBACK_CIRCUIT = new CircuitBreaker();
    private static final FallbackMetrics BUILD_FALLBACK_METRICS = new FallbackMetrics();

    private EnduranceQuestManager() {}

    // ========== Instance Dimension Mode ==========

    /**
     * Enable or disable instance dimension mode.
     * When enabled, quests run in isolated temporary dimensions instead of overworld arenas.
     */
    public void setUseInstanceDimensions(boolean use) {
        this.useInstanceDimensions = use;
        if (!use) {
            LOGGER.error("[EnduranceQuest] Instance dimensions disabled; legacy arena path is deprecated and quests will fail");
        }
        LOGGER.info("[EnduranceQuest] Instance dimension mode: {}", use ? "ENABLED" : "DISABLED");
    }

    /**
     * Check if instance dimension mode is enabled.
     */
    public boolean isUseInstanceDimensions() {
        return useInstanceDimensions;
    }

    public PrebuildPoolManager getPrebuildPoolManager() {
        return prebuildPoolManager;
    }

    public void tickAsyncBuilds(net.minecraft.server.MinecraftServer server) {
        if (server == null) {
            return;
        }
        asyncBuildCoordinator.onServerTick(server);
    }

    /**
     * Apply updated arena template config at runtime (hot-reload support).
     */
    public void applyArenaConfig(ArenaTemplateConfig config) {
        if (config == null) {
            return;
        }
        this.arenaTemplateConfig = config;
        this.arenaConfigSnapshot = config.snapshot();
        if (arenaTelemetry == null) {
            arenaTelemetry = new ArenaTelemetry();
        }
        if (config.prebuildPoolEnabled()) {
            prebuildPoolManager.enable();
        } else {
            prebuildPoolManager.disable();
        }
    }

    public void setForceTemplateCapability(@javax.annotation.Nullable ForceTemplateCapability capability) {
        this.forceTemplateCapability = capability;
    }

    /**
     * Initialize the manager. Should be called during server start.
     */
    public void initialize(Path configDir) {
        if (initialized) return;

        // configDir is already config/devmod/, so just add endurance_quests
        this.dataDirectory = configDir.resolve("endurance_quests");
        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException e) {
            LOGGER.error("[EnduranceQuest] Failed to create data directory", e);
        }

        // Initialize the registry first
        EnduranceQuestRegistry.INSTANCE.initialize();

        // Create quest templates for all registered mobs
        for (EnduranceQuestRegistry.MobQuestConfig mobConfig : EnduranceQuestRegistry.INSTANCE.getAllMobConfigs()) {
            EnduranceQuest template = new EnduranceQuest(mobConfig);
            questTemplates.put(mobConfig.mobId, template);
        }

        // Initialize persistence
        persistence.initialize(dataDirectory);

        // Initialize arena manager
        // Legacy overworld arenas are deprecated; instance-only flow enforced.

        // Initialize session handler with dependencies
        this.sessionHandler = new EnduranceSessionHandler(activeSessions, persistence);

        // Initialize reward system
        RewardSystem.INSTANCE.initialize(configDir);

        // Initialize gamification system (leaderboards, badges, challenges)
        GamificationManager.INSTANCE.initialize(configDir);

        // Initialize analytics system for session tracking
        EnduranceAnalytics.INSTANCE.initialize(configDir);

        // Configure instance-only gate for legacy overworld protection
        this.arenaTemplateConfig = ArenaTemplateConfig.load();
        this.arenaConfigSnapshot = arenaTemplateConfig.snapshot();
        this.arenaTelemetry = new ArenaTelemetry();
        applyArenaConfig(arenaTemplateConfig);
        if (arenaTemplateConfig.instanceOnly()) {
            LOGGER.info("[EnduranceQuest] Instance-only mode enabled; legacy overworld arenas are deprecated");
        }

        // Initialize arena template integration (policy resolver + registry)
        initArenaTemplateIntegration(configDir);
        if (!shouldUseTemplateSystem()) {
            LOGGER.error("[EnduranceQuest] Arena template system required; Endurance quests are disabled until enabled");
        }

        initialized = true;
        LOGGER.info("[EnduranceQuest] Manager initialized with {} quest types", questTemplates.size());
    }

    /**
     * Shutdown the manager. Should be called during server stop.
     * Cleans up all active sessions and saves data.
     *
     * IMPROVEMENT: Award partial rewards to players with active quests
     * so they don't lose all progress on server shutdown.
     */
    public void shutdown() {
        if (!initialized) return;

        LOGGER.info("[EnduranceQuest] Shutting down with {} active sessions", activeSessions.size());

        // Force-end all active sessions with partial rewards
        for (ActiveQuestSession session : activeSessions.values()) {
            try {
                EnduranceQuest quest = session.quest;
                UUID playerId = session.getPlayerId();

                // Award partial tokens based on waves completed (50% of normal rate)
                int wavesCompleted = quest.getCurrentWave();
                int partialTokens = wavesCompleted * 15; // ~50% of normal wave rewards

                if (partialTokens > 0) {
                    RewardSystem.PlayerWallet wallet = RewardSystem.INSTANCE.getWallet(playerId);
                    // SAFETY: Check wallet is not null before using
                    if (wallet != null) {
                        wallet.addCurrency(RewardSystem.Currency.TOKENS, partialTokens);
                        LOGGER.info("[EnduranceQuest] Awarded {} partial tokens to player {} (completed {} waves before shutdown)",
                            partialTokens, playerId, wavesCompleted);
                    } else {
                        LOGGER.warn("[EnduranceQuest] Could not find wallet for player {}", playerId);
                    }
                }

                quest.fail(true); // Mark as abandoned

                // Flush telemetry/stats and cleanup systems without granting full rewards
                handleForcedShutdownCleanup(session);
            } catch (Exception e) {
                LOGGER.error("[EnduranceQuest] Error cleaning up session for player {}", session.getPlayerId(), e);
            }
        }
        activeSessions.clear();

        // Save all player stats (includes partial rewards)
        persistence.savePlayerStats();

        // Save reward system data
        RewardSystem.INSTANCE.saveAll();

        // Save gamification data (leaderboards, badges, challenges)
        GamificationManager.INSTANCE.saveAll();

        // Clear templates (will be rebuilt on next init)
        questTemplates.clear();

        prebuildPoolManager.shutdown();

        initialized = false;
        LOGGER.info("[EnduranceQuest] Shutdown complete");
    }

    /**
     * Check if manager is initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }

    // ========== Arena Template Integration ==========

    private void initArenaTemplateIntegration(Path configDir) {
        arenaTemplateRegistry = DevMod.getArenaTemplateRegistry();
        if (arenaTemplateRegistry == null) {
            LOGGER.error("[EnduranceQuest] ArenaTemplateRegistry not available; Endurance now requires arena templates");
            return;
        }
        if (arenaTemplateConfig == null || !arenaTemplateConfig.arenaTemplateEnabled()) {
            LOGGER.error("[EnduranceQuest] Arena template system disabled; enable devmod.arena.templateEnabled");
            return;
        }

        if (arenaTelemetry == null) {
            arenaTelemetry = new ArenaTelemetry();
        }
        overrideManager = new OverrideManager(arenaTelemetry);
        policyResolver = new PolicyResolver(arenaTemplateRegistry, arenaTelemetry, overrideManager, arenaTemplateConfig);
        arenaPolicyRegistry = new ArenaPolicyRegistry(arenaTelemetry, arenaTemplateRegistry);

        Path policyDir = configDir.resolve("arena_policies");
        var loadResult = arenaPolicyRegistry.loadAllSources(policyDir);
        if (!loadResult.errors().isEmpty()) {
            loadResult.errors().forEach(err -> LOGGER.error("[EnduranceQuest] Policy load error: {}", err));
        } else {
            LOGGER.info("[EnduranceQuest] Loaded {} arena policies from {}", loadResult.policies().size(), policyDir);
        }

        for (var policy : arenaPolicyRegistry.all()) {
            policyResolver.registerPolicy(policy);
        }
    }

    private boolean shouldUseTemplateSystem() {
        return arenaTemplateConfig != null
            && arenaTemplateConfig.arenaTemplateEnabled()
            && arenaTemplateRegistry != null
            && policyResolver != null;
    }

    @javax.annotation.Nullable
    private String getTemplateSystemReadinessError() {
        if (!useInstanceDimensions) {
            emitGateFailure("instance_dimensions_disabled", null);
            return "Instance dimensions required for Endurance. Please enable instance mode.";
        }
        if (arenaTemplateConfig == null) {
            emitGateFailure("arena_template_config_missing", null);
            return "Arena template config missing; templates are required for Endurance.";
        }
        if (!arenaTemplateConfig.instanceOnly()) {
            emitGateFailure("instance_only_disabled", null);
            return "Arena templates require instance-only mode. Enable devmod.arena.instanceOnly.";
        }
        if (!arenaTemplateConfig.arenaTemplateEnabled()) {
            emitGateFailure("arena_template_disabled", null);
            return "Arena template system disabled. Enable devmod.arena.templateEnabled.";
        }
        if (!shouldUseTemplateSystem()) {
            emitGateFailure("arena_template_not_ready", null);
            return "Arena template system not initialized. Load templates/policies and retry.";
        }
        return null;
    }

    String getTemplateSystemReadinessErrorForTesting() {
        return getTemplateSystemReadinessError();
    }

    private ResolvedArena resolveArenaTemplate(UUID playerId, ResourceLocation mobId, QuestSettings settings) {
        if (policyResolver == null) {
            return null;
        }
        var mobConfig = EnduranceQuestRegistry.INSTANCE.getMobConfig(mobId).orElse(null);
        String questType = resolveQuestTypeLabel(settings, mobConfig);
        String difficulty = resolveDifficultyLabel(settings, mobConfig);
        Set<String> tags = resolveTags(settings, mobConfig);

        ResolveContext.Builder ctxBuilder = ResolveContext.builder(playerId)
            .partyId(settings.partyId)
            .mobType(mobId.toString())
            .questType(questType)
            .difficulty(difficulty)
            .playerCount(settings.getPlayerCount())
            .tags(tags);
        if (forceTemplateCapability != null) {
            forceTemplateCapability.getForcedTemplate(playerId)
                .ifPresent(templateId -> {
                    LOGGER.info("[EnduranceQuest] Force template override active for {}: {}",
                        playerId, templateId);
                    ctxBuilder.forceTemplateId(templateId);
                });
        }

        return policyResolver.resolve(ctxBuilder.build());
    }

    private String resolveQuestTypeLabel(QuestSettings settings, EnduranceQuestRegistry.MobQuestConfig mobConfig) {
        if (settings != null && settings.questType == QuestType.RAID_BOSS) {
            return "boss";
        }
        if (settings != null && settings.questType == QuestType.EVENT) {
            return "event";
        }
        if (mobConfig != null && mobConfig.tier == EnduranceQuestRegistry.MobTier.BOSS) {
            return "boss";
        }
        return "endurance";
    }

    private String resolveDifficultyLabel(QuestSettings settings, EnduranceQuestRegistry.MobQuestConfig mobConfig) {
        if (settings != null && settings.questType == QuestType.RAID_BOSS) {
            return "hard";
        }
        if (settings != null && settings.questType == QuestType.EVENT) {
            return "hard";
        }
        if (mobConfig != null && mobConfig.tier == EnduranceQuestRegistry.MobTier.BOSS) {
            return "hard";
        }
        if (mobConfig != null && mobConfig.tier == EnduranceQuestRegistry.MobTier.ELITE) {
            return "hard";
        }
        return "normal";
    }

    private List<String> buildBriefingLines(EnduranceQuest quest,
                                            ResolvedArena resolved,
                                            ActiveQuestSession session) {
        List<String> lines = new ArrayList<>();
        if (resolved != null) {
            lines.add("Template: " + resolved.template().id() + " v" + resolved.template().version());
            lines.add("Policy: " + resolved.policy().id() + " v" + resolved.policy().version());
        }
        if (session != null) {
            String difficulty = session.getDifficultyLabel();
            if (difficulty != null && !difficulty.isBlank()) {
                lines.add("Difficulty: " + difficulty);
            }
        }
        lines.add("Goal: Survive waves");
        lines.add("Rewards: Tokens + loot drops");
        if (quest != null && quest.getQuestId() != null) {
            lines.add("Run ID: " + quest.getQuestId());
        }
        return lines;
    }

    private Set<String> resolveTags(QuestSettings settings, EnduranceQuestRegistry.MobQuestConfig mobConfig) {
        Set<String> tags = new HashSet<>();
        if (settings != null && settings.isMultiplayer()) {
            tags.add("party");
        }
        if (settings != null && settings.questType == QuestType.RAID_BOSS) {
            tags.add("boss");
        }
        if (settings != null && settings.questType == QuestType.EVENT) {
            tags.add("event");
        }
        if (mobConfig != null && mobConfig.tier == EnduranceQuestRegistry.MobTier.BOSS) {
            tags.add("boss");
        }
        if (arenaTemplateConfig != null && arenaTemplateConfig.routingEnabled() && mobConfig != null
            && mobConfig.entityType != null) {
            Class<?> baseClass = mobConfig.entityType.getBaseClass();
            if (baseClass != null && RangedAttackMob.class.isAssignableFrom(baseClass)) {
                tags.add("ranged");
            } else {
                tags.add("melee");
            }
        }
        return tags;
    }

    private com.devmod.arena.builder.TemplateArenaBuilder createTemplateBuilder(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        ArenaTelemetry telemetry = arenaTelemetry != null ? arenaTelemetry : new ArenaTelemetry();
        MinecraftBlockPlacer blockPlacer = new MinecraftBlockPlacer(level);
        MinecraftEntitySpawner entitySpawner = new MinecraftEntitySpawner(level);
        ChunkStatus fullStatus = Objects.requireNonNull(ChunkStatus.FULL, "fullStatus");
        ChunkLoadingManager chunkManager = new ChunkLoadingManager(
            (chunkX, chunkZ) -> level.getChunk(chunkX, chunkZ),
            (chunkX, chunkZ) -> level.getChunk(chunkX, chunkZ, fullStatus, false) != null,
            new ChunkLoadingManager.TicketManager() {
                @Override
                public void addTicket(int chunkX, int chunkZ) {
                    level.setChunkForced(chunkX, chunkZ, true);
                }

                @Override
                public void removeTicket(int chunkX, int chunkZ) {
                    level.setChunkForced(chunkX, chunkZ, false);
                }
            }
        );
        var instanceLimits = InstanceLimitConfig.load().toLimits();
        return new com.devmod.arena.builder.TemplateArenaBuilder(
            telemetry,
            blockPlacer,
            entitySpawner,
            chunkManager,
            null,
            instanceLimits,
            null,
            arenaConfigSnapshot
        );
    }

    // ========== Party Quest Flow (Separate Phases) ==========

    /**
     * PHASE 1: Prepare arena for a party quest WITHOUT teleporting or starting.
     * Creates the arena and returns the info needed for teleportation.
     *
     * Used by QuestStartSequence to separate arena creation from teleport.
     *
     * @param leader The party leader
     * @param mobId The mob type for the quest
     * @param settings Quest settings (includes party info)
     * @return PreparedArenaResult with arena info, or failure message
     */
    public PreparedArenaResult prepareArenaForParty(ServerPlayer leader, ResourceLocation mobId, QuestSettings settings) {
        // Validate quest type
        EnduranceQuest template = questTemplates.get(mobId);
        if (template == null) {
            return PreparedArenaResult.failure("Unknown quest type: " + mobId);
        }

        String readinessError = getTemplateSystemReadinessError();
        if (readinessError != null) {
            LOGGER.error("[EnduranceQuest] prepareArenaForParty blocked: {}", readinessError);
            return PreparedArenaResult.failure(readinessError);
        }

        return prepareTemplateArenaForParty(leader, mobId, settings, template.getMobConfig());
    }

    public CompletableFuture<PreparedArenaResult> prepareArenaForPartyAsync(ServerPlayer leader,
                                                                            ResourceLocation mobId,
                                                                            QuestSettings settings) {
        EnduranceQuest template = questTemplates.get(mobId);
        if (template == null) {
            return CompletableFuture.completedFuture(PreparedArenaResult.failure("Unknown quest type: " + mobId));
        }

        String readinessError = getTemplateSystemReadinessError();
        if (readinessError != null) {
            LOGGER.error("[EnduranceQuest] prepareArenaForPartyAsync blocked: {}", readinessError);
            return CompletableFuture.completedFuture(PreparedArenaResult.failure(readinessError));
        }

        return prepareTemplateArenaForPartyAsync(leader, mobId, settings, template.getMobConfig());
    }

    private CompletableFuture<PreparedArenaResult> prepareTemplateArenaForPartyAsync(
        ServerPlayer leader,
        ResourceLocation mobId,
        QuestSettings settings,
        EnduranceQuestRegistry.MobQuestConfig mobConfig) {
        ResolvedArena resolved = resolveArenaTemplate(leader.getUUID(), mobId, settings);
        if (resolved == null) {
            return CompletableFuture.completedFuture(PreparedArenaResult.failure("No matching arena template/policy"));
        }

        ArenaTemplate template = resolved.template();
        List<UUID> partyMembers = settings.partyMemberIds != null && !settings.partyMemberIds.isEmpty()
            ? new ArrayList<>(settings.partyMemberIds)
            : null;

        CompletableFuture<PreparedArenaResult> result = new CompletableFuture<>();
        InstanceManager.INSTANCE
            .startInstanceQuestImmediate(leader, template.id(), mobId.toString(), partyMembers)
            .orTimeout(INSTANCE_CREATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .whenComplete((instanceId, throwable) -> {
                var server = leader.getServer();
                if (server == null) {
                    if (instanceId != null) {
                        InstanceArenaManager.INSTANCE.endInstanceQuest(instanceId, false);
                    }
                    result.complete(PreparedArenaResult.failure("Server not available"));
                    return;
                }
                server.execute(() -> {
                    if (throwable != null || instanceId == null) {
                        String message = throwable != null && throwable.getMessage() != null
                            ? throwable.getMessage()
                            : "Failed to create instance for party";
                        LOGGER.error("[EnduranceQuest] Failed to create instance for party: {}", message);
                        result.complete(PreparedArenaResult.failure("Failed to create instance: " + message));
                        return;
                    }

                    Optional<InstanceData> instanceOpt = InstanceRegistry.INSTANCE.getInstance(instanceId);
                    if (instanceOpt.isEmpty()) {
                        InstanceArenaManager.INSTANCE.endInstanceQuest(instanceId, false);
                        result.complete(PreparedArenaResult.failure("Instance not found after creation"));
                        return;
                    }

                    InstanceData instance = instanceOpt.get();
                    var dimensionKey = instance.getDimensionKey();
                    if (dimensionKey == null) {
                        InstanceArenaManager.INSTANCE.endInstanceQuest(instanceId, false);
                        result.complete(PreparedArenaResult.failure("Instance dimension not ready"));
                        return;
                    }

                    ServerLevel instanceLevel = server.getLevel(dimensionKey);
                    if (instanceLevel == null) {
                        InstanceArenaManager.INSTANCE.endInstanceQuest(instanceId, false);
                        result.complete(PreparedArenaResult.failure("Instance level not found"));
                        return;
                    }

                    OriginResolution origin = resolveTemplateOrigin(template);
                    if (shouldBuildAsync(template)) {
                        AsyncArenaBuilder asyncBuilder = asyncBuildCoordinator.getOrCreate(instanceLevel);
                        UUID arenaId = UUID.randomUUID();
                        asyncBuilder.submitBuildAsync(
                            arenaId,
                            template,
                            origin.centerX(),
                            origin.originY(),
                            origin.centerZ()
                        ).whenComplete((asyncResult, buildError) -> {
                            server.execute(() -> {
                                if (buildError != null || asyncResult == null || !asyncResult.success()) {
                                    String msg = buildError != null && buildError.getMessage() != null
                                        ? buildError.getMessage()
                                        : (asyncResult != null ? asyncResult.errorMessage() : "Build failed");
                                    com.devmod.arena.builder.TemplateArenaBuilder builder =
                                        createTemplateBuilder(instanceLevel);
                                    BuildAttemptResult fallbackAttempt = attemptFallbackOnly(
                                        builder,
                                        resolved,
                                        "party_async",
                                        msg
                                    );
                                    if (fallbackAttempt != null && fallbackAttempt.result().success()) {
                                        result.complete(finalizePreparedArena(
                                            fallbackAttempt.result(),
                                            fallbackAttempt.resolved(),
                                            instanceId,
                                            fallbackAttempt.origin(),
                                            instanceLevel,
                                            instance,
                                            mobId,
                                            mobConfig
                                        ));
                                        return;
                                    }
                                    String technicalMessage = msg;
                                    if (fallbackAttempt != null && fallbackAttempt.result() != null
                                        && fallbackAttempt.result().errorMessage() != null) {
                                        technicalMessage = fallbackAttempt.result().errorMessage();
                                    }
                                    String userMessage = handleBuildAbort(
                                        resolved,
                                        "party_async",
                                        technicalMessage,
                                        buildError,
                                        fallbackAttempt != null
                                    );
                                    InstanceArenaManager.INSTANCE.endInstanceQuest(instanceId, false);
                                    result.complete(PreparedArenaResult.failure(userMessage));
                                    return;
                                }
                                ArenaBuilder.BuildResult buildResult = ArenaBuilder.BuildResult.success(
                                    asyncResult.arenaId(),
                                    template.id(),
                                    asyncResult.blocksPlaced(),
                                    asyncResult.durationMs()
                                );
                                result.complete(finalizePreparedArena(
                                    buildResult,
                                    resolved,
                                    instanceId,
                                    origin,
                                    instanceLevel,
                                    instance,
                                    mobId,
                                    mobConfig
                                ));
                            });
                        });
                        return;
                    }

                    com.devmod.arena.builder.TemplateArenaBuilder builder = createTemplateBuilder(instanceLevel);
                    BuildAttemptResult attempt = buildWithFallback(
                        builder,
                        resolved,
                        origin,
                        "party_sync"
                    );

                    if (!attempt.result().success()) {
                        String msg = attempt.result().errorMessage() != null
                            ? attempt.result().errorMessage()
                            : "Build failed";
                        String userMessage = handleBuildAbort(
                            attempt.resolved(),
                            "party_sync",
                            msg,
                            null,
                            attempt.fallbackAttempted()
                        );
                        InstanceArenaManager.INSTANCE.endInstanceQuest(instanceId, false);
                        result.complete(PreparedArenaResult.failure(userMessage));
                        return;
                    }

                    result.complete(finalizePreparedArena(
                        attempt.result(),
                        attempt.resolved(),
                        instanceId,
                        attempt.origin(),
                        instanceLevel,
                        instance,
                        mobId,
                        mobConfig
                    ));
                });
            });

        return result;
    }

    private PreparedArenaResult prepareTemplateArenaForParty(ServerPlayer leader,
                                                             ResourceLocation mobId,
                                                             QuestSettings settings,
                                                             EnduranceQuestRegistry.MobQuestConfig mobConfig) {
        ResolvedArena resolved = resolveArenaTemplate(leader.getUUID(), mobId, settings);
        if (resolved == null) {
            return PreparedArenaResult.failure("No matching arena template/policy");
        }

        ArenaTemplate template = resolved.template();
        UUID instanceId;
        try {
            List<UUID> partyMembers = settings.partyMemberIds != null && !settings.partyMemberIds.isEmpty()
                ? new ArrayList<>(settings.partyMemberIds)
                : null;
            instanceId = InstanceManager.INSTANCE
                .startInstanceQuestImmediate(leader, template.id(), mobId.toString(), partyMembers)
                .get(INSTANCE_CREATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            LOGGER.error("[EnduranceQuest] Failed to create instance for party: {}", e.getMessage());
            return PreparedArenaResult.failure("Failed to create instance: " + e.getMessage());
        }

        if (instanceId == null) {
            return PreparedArenaResult.failure("Failed to create instance for party");
        }

        Optional<InstanceData> instanceOpt = InstanceRegistry.INSTANCE.getInstance(instanceId);
        if (instanceOpt.isEmpty()) {
            InstanceArenaManager.INSTANCE.endInstanceQuest(instanceId, false);
            return PreparedArenaResult.failure("Instance not found after creation");
        }

        InstanceData instance = instanceOpt.get();
        var dimensionKey = instance.getDimensionKey();
        if (dimensionKey == null) {
            InstanceArenaManager.INSTANCE.endInstanceQuest(instanceId, false);
            return PreparedArenaResult.failure("Instance dimension not ready");
        }

        var server = leader.getServer();
        if (server == null) {
            InstanceArenaManager.INSTANCE.endInstanceQuest(instanceId, false);
            return PreparedArenaResult.failure("Server not available");
        }

        ServerLevel instanceLevel = server.getLevel(dimensionKey);
        if (instanceLevel == null) {
            InstanceArenaManager.INSTANCE.endInstanceQuest(instanceId, false);
            return PreparedArenaResult.failure("Instance level not found");
        }

        com.devmod.arena.builder.TemplateArenaBuilder builder = createTemplateBuilder(instanceLevel);
        OriginResolution origin = resolveTemplateOrigin(template);
        BuildAttemptResult attempt = buildWithFallback(
            builder,
            resolved,
            origin,
            "party_sync"
        );

        if (!attempt.result().success()) {
            String msg = attempt.result().errorMessage() != null
                ? attempt.result().errorMessage()
                : "Build failed";
            String userMessage = handleBuildAbort(
                attempt.resolved(),
                "party_sync",
                msg,
                null,
                attempt.fallbackAttempted()
            );
            InstanceArenaManager.INSTANCE.endInstanceQuest(instanceId, false);
            return PreparedArenaResult.failure(userMessage);
        }

        return finalizePreparedArena(
            attempt.result(),
            attempt.resolved(),
            instanceId,
            attempt.origin(),
            instanceLevel,
            instance,
            mobId,
            mobConfig
        );
    }

    private PreparedArenaResult finalizePreparedArena(ArenaBuilder.BuildResult buildResult,
                                                      ResolvedArena resolved,
                                                      UUID instanceId,
                                                      OriginResolution origin,
                                                      ServerLevel instanceLevel,
                                                      InstanceData instance,
                                                      ResourceLocation mobId,
                                                      EnduranceQuestRegistry.MobQuestConfig mobConfig) {
        ArenaTemplate template = resolved.template();
        ArenaHandle handle = createArenaHandle(buildResult, resolved, instanceId, origin, instanceLevel);
        if (!isHandleValid(handle)) {
            InstanceArenaManager.INSTANCE.endInstanceQuest(instanceId, false);
            emitGateFailure("missing_spawn_slots", resolved);
            return PreparedArenaResult.failure("Template missing required spawn slots (player/mob)");
        }
        ArenaContext arena = createArenaAdapter(instanceLevel, handle);
        updateInstanceArenaMetadata(instance, template, handle, origin);
        return PreparedArenaResult.success(arena, handle, mobId, mobConfig, instanceId);
    }

    /**
     * PHASE 2: Teleport players to a prepared arena.
     * Players should be teleported BEFORE starting the quest.
     *
     * @param players List of players to teleport
     * @param arena The prepared arena
     * @param handle The arena handle with spawn positions
     * @return Map of player UUID to their spawn position in arena
     */
    public Map<UUID, net.minecraft.core.BlockPos> teleportPlayersToArena(List<ServerPlayer> players,
                                                                        ArenaContext arena,
                                                                        @javax.annotation.Nullable ArenaHandle handle) {
        if (handle == null || handle.playerSpawnPositions() == null || handle.playerSpawnPositions().isEmpty()) {
            LOGGER.error("[EnduranceQuest] Missing player spawn slots; ArenaHandle required");
            return Collections.emptyMap();
        }

        Map<UUID, net.minecraft.core.BlockPos> spawnPositions = new HashMap<>();
        List<ArenaHandle.BlockPos> positions = handle.playerSpawnPositions();
        int playerCount = players.size();
        ServerLevel level = arena.getLevel();
        com.devmod.arena.registry.TemplateSpawnValidator runtimeValidator =
            new com.devmod.arena.registry.TemplateSpawnValidator(arenaTelemetry);
        Map<net.minecraft.core.BlockPos, ArenaTemplate.SpawnSlot> slotMap = Collections.emptyMap();
        ArenaTemplate template = null;
        if (arenaTemplateRegistry != null) {
            template = arenaTemplateRegistry.get(handle.templateId()).orElse(null);
            if (template != null) {
                slotMap = buildPlayerSpawnSlotMap(template, handle);
            }
        }
        com.devmod.arena.spawn.SpawnOccupancyTracker occupied = new com.devmod.arena.spawn.SpawnOccupancyTracker();

        for (int i = 0; i < playerCount; i++) {
            ServerPlayer player = players.get(i);
            if (player == null || !player.isAlive()) continue;

            net.minecraft.core.BlockPos spawnPos = pickValidatedSpawnPosition(
                positions,
                i,
                occupied,
                runtimeValidator,
                slotMap,
                template,
                level
            );
            if (spawnPos == null) {
                LOGGER.warn("[EnduranceQuest] No valid template player spawn found");
                return Collections.emptyMap();
            }
            double x = spawnPos.getX() + 0.5;
            double y = spawnPos.getY();
            double z = spawnPos.getZ() + 0.5;

            player.teleportTo(x, y, z);
            spawnPositions.put(player.getUUID(), spawnPos);

            LOGGER.debug("[EnduranceQuest] Teleported {} to handle spawn at ({}, {}, {})",
                player.getName().getString(), spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
        }

        LOGGER.info("[EnduranceQuest] Teleported {} players to arena {} using template spawns",
            spawnPositions.size(), arena.getId());
        return spawnPositions;
    }

    private net.minecraft.core.BlockPos pickValidatedSpawnPosition(
            List<ArenaHandle.BlockPos> positions,
            int startIndex,
            com.devmod.arena.spawn.SpawnOccupancyTracker occupied,
            com.devmod.arena.registry.TemplateSpawnValidator runtimeValidator,
            Map<net.minecraft.core.BlockPos, ArenaTemplate.SpawnSlot> slotMap,
            @javax.annotation.Nullable ArenaTemplate template,
            ServerLevel level) {
        if (positions == null || positions.isEmpty()) {
            return null;
        }
        int size = positions.size();
        for (int offset = 0; offset < size; offset++) {
            ArenaHandle.BlockPos candidate = positions.get((startIndex + offset) % size);
            net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(candidate.x(), candidate.y(), candidate.z());
            if (occupied.isOccupied(pos)) {
                continue;
            }
            if (template != null && !slotMap.isEmpty()) {
                ArenaTemplate.SpawnSlot slot = slotMap.get(pos);
                if (slot == null) {
                    continue;
                }
                if (!runtimeValidator.validateAtRuntime(template.id(), slot, level, pos)) {
                    continue;
                }
            }
            occupied.markOccupied(pos);
            return pos;
        }
        return null;
    }

    private void emitLegacyCall(String reason, String context, @javax.annotation.Nullable ServerLevel level) {
        if (arenaTelemetry == null) {
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("caller", getClass().getName());
        if (context != null && !context.isBlank()) {
            data.put("context", context);
        }
        data.put("dimension", level != null ? level.dimension().location().toString() : "unknown");
        data.put("result", "BLOCKED");
        data.put("debug", false);
        data.put("useInstanceDimensions", useInstanceDimensions);
        if (arenaConfigSnapshot != null) {
            data.put("instanceOnly", arenaConfigSnapshot.instanceOnly());
            data.put("allowLegacyOverworldArena", arenaConfigSnapshot.allowLegacyOverworldArena());
            data.put("arenaTemplateEnabled", arenaConfigSnapshot.arenaTemplateEnabled());
        }
        if (reason != null && !reason.isBlank()) {
            data.put("reason", reason);
        }
        arenaTelemetry.emit("arena.legacy.call", data);
    }

    private void emitGateFailure(String reason, @javax.annotation.Nullable ResolvedArena resolved) {
        if (arenaTelemetry == null) {
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("reason", reason);
        data.put("instanceOnly", arenaTemplateConfig != null && arenaTemplateConfig.instanceOnly());
        data.put("templateEnabled", arenaTemplateConfig != null && arenaTemplateConfig.arenaTemplateEnabled());
        if (resolved != null) {
            data.put("templateId", resolved.template().id());
            data.put("policyId", resolved.policy().id());
        }
        arenaTelemetry.emit("endurance.gate.failure", data);
    }

    private Map<net.minecraft.core.BlockPos, ArenaTemplate.SpawnSlot> buildPlayerSpawnSlotMap(
            ArenaTemplate template, ArenaHandle handle) {
        Map<net.minecraft.core.BlockPos, ArenaTemplate.SpawnSlot> slotMap = new HashMap<>();
        if (template.spawnSlots() == null) {
            return slotMap;
        }
        for (ArenaTemplate.SpawnSlot slot : template.spawnSlots()) {
            if (slot.tags() == null || !(slot.tags().contains("player") || slot.tags().contains("team"))) {
                continue;
            }
            int[] pos = slot.pos();
            if (pos == null || pos.length != 3) continue;

            int offsetX = 0;
            int offsetY = 0;
            int offsetZ = 0;
            if (template.playerSpawnOffset() != null) {
                offsetX = template.playerSpawnOffset().x();
                offsetY = template.playerSpawnOffset().y();
                offsetZ = template.playerSpawnOffset().z();
            }

            int x = handle.originX() + pos[0] + offsetX;
            int y = resolveSpawnY(slot, template, handle.originY()) + offsetY;
            int z = handle.originZ() + pos[2] + offsetZ;
            slotMap.put(new net.minecraft.core.BlockPos(x, y, z), slot);
        }
        return slotMap;
    }

    /**
     * Check if a player is inside the specified arena.
     * Used for arrival confirmation.
     *
     * @param player The player to check
     * @param arena The arena to check against
     * @return true if player is inside arena bounds
     */
    public boolean isPlayerInArena(ServerPlayer player, ArenaContext arena) {
        return isPlayerInArena(player, null, arena);
    }

    public boolean isPlayerInArena(ServerPlayer player,
                                   @javax.annotation.Nullable ArenaHandle handle,
                                   @javax.annotation.Nullable ArenaContext arena) {
        if (player == null) {
            return false;
        }
        if (handle != null && handle.bounds() != null) {
            BlockPos pos = player.blockPosition();
            ArenaHandle.AABB bounds = handle.bounds();
            return bounds.contains(pos.getX(), pos.getY(), pos.getZ());
        }
        if (arena == null) {
            return false;
        }
        return arena.contains(Objects.requireNonNull(player.position()));
    }

    // ========== Template Handle Helpers ==========

    private record OriginResolution(int originX, int originY, int originZ, int centerX, int centerZ) {}

    private int resolveTemplateSize(ArenaTemplate template, Integer size) {
        return Objects.requireNonNullElse(size, Objects.requireNonNull(template.size(), "template.size"));
    }

    private OriginResolution resolveTemplateOrigin(ArenaTemplate template) {
        int originX = template.origin() != null ? template.origin().x() : 0;
        int originY = template.origin() != null ? template.origin().y() : 64;
        int originZ = template.origin() != null ? template.origin().z() : 0;
        int sizeX = resolveTemplateSize(template, template.sizeX());
        int sizeZ = resolveTemplateSize(template, template.sizeZ());
        int halfX = sizeX / 2;
        int halfZ = sizeZ / 2;

        ArenaTemplate.OriginMode mode = template.origin() != null && template.origin().mode() != null
            ? template.origin().mode()
            : ArenaTemplate.OriginMode.CENTER;

        int centerX;
        int centerZ;
        switch (mode) {
            case CORNER_NW -> {
                centerX = originX + halfX;
                centerZ = originZ + halfZ;
            }
            case CORNER_SW -> {
                centerX = originX + halfX;
                centerZ = originZ - halfZ + (sizeZ % 2 == 0 ? 1 : 0);
            }
            case CENTER -> {
                centerX = originX;
                centerZ = originZ;
            }
            default -> {
                centerX = originX;
                centerZ = originZ;
            }
        }

        return new OriginResolution(originX, originY, originZ, centerX, centerZ);
    }

    private boolean shouldBuildAsync(ArenaTemplate template) {
        if (template == null || template.buildSettings() == null) {
            return false;
        }
        return template.buildSettings().buildPriority() == ArenaTemplate.BuildSettings.Priority.ASYNC;
    }

    private record BuildAttemptResult(
        ResolvedArena resolved,
        OriginResolution origin,
        ArenaBuilder.BuildResult result,
        boolean fallbackAttempted,
        boolean fallbackSucceeded
    ) {}

    private BuildAttemptResult buildWithFallback(com.devmod.arena.builder.TemplateArenaBuilder builder,
                                                 ResolvedArena resolved,
                                                 OriginResolution origin,
                                                 String context) {
        long primaryStart = System.nanoTime();
        ArenaBuilder.BuildResult primaryResult = builder.build(
            resolved,
            origin.centerX(),
            origin.originY(),
            origin.centerZ()
        );
        BUILD_FALLBACK_METRICS.recordPrimaryTime(System.nanoTime() - primaryStart);

        if (primaryResult.success()) {
            BUILD_FALLBACK_CIRCUIT.recordSuccess();
            BUILD_FALLBACK_METRICS.record(FallbackMetrics.MetricType.PRIMARY_SUCCESS);
            return new BuildAttemptResult(resolved, origin, primaryResult, false, false);
        }

        ResolvedArena fallbackResolved = resolveFallbackArena(resolved);
        if (fallbackResolved == null || fallbackResolved.template().id().equals(resolved.template().id())) {
            BUILD_FALLBACK_METRICS.record(FallbackMetrics.MetricType.ALL_FAILED);
            return new BuildAttemptResult(resolved, origin, primaryResult, false, false);
        }

        if (!BUILD_FALLBACK_CIRCUIT.allowRequest()) {
            emitBuildFallbackBlocked(resolved, fallbackResolved, context, "circuit_open");
            BUILD_FALLBACK_METRICS.record(FallbackMetrics.MetricType.ALL_FAILED);
            return new BuildAttemptResult(resolved, origin, primaryResult, false, false);
        }

        emitBuildFallbackAttempt(resolved, fallbackResolved, context, primaryResult.errorMessage());
        OriginResolution fallbackOrigin = resolveTemplateOrigin(fallbackResolved.template());
        long fallbackStart = System.nanoTime();
        ArenaBuilder.BuildResult fallbackResult = builder.build(
            fallbackResolved,
            fallbackOrigin.centerX(),
            fallbackOrigin.originY(),
            fallbackOrigin.centerZ()
        );
        BUILD_FALLBACK_METRICS.recordFallbackTime(System.nanoTime() - fallbackStart);

        if (fallbackResult.success()) {
            BUILD_FALLBACK_CIRCUIT.recordSuccess();
            BUILD_FALLBACK_METRICS.record(FallbackMetrics.MetricType.FALLBACK_USED);
            return new BuildAttemptResult(fallbackResolved, fallbackOrigin, fallbackResult, true, true);
        }

        BUILD_FALLBACK_CIRCUIT.recordFailure();
        BUILD_FALLBACK_METRICS.record(FallbackMetrics.MetricType.ALL_FAILED);
        return new BuildAttemptResult(fallbackResolved, fallbackOrigin, fallbackResult, true, false);
    }

    private @javax.annotation.Nullable BuildAttemptResult attemptFallbackOnly(
        com.devmod.arena.builder.TemplateArenaBuilder builder,
        ResolvedArena primary,
        String context,
        @javax.annotation.Nullable String primaryError) {
        ResolvedArena fallbackResolved = resolveFallbackArena(primary);
        if (fallbackResolved == null || fallbackResolved.template().id().equals(primary.template().id())) {
            BUILD_FALLBACK_METRICS.record(FallbackMetrics.MetricType.ALL_FAILED);
            return null;
        }

        if (!BUILD_FALLBACK_CIRCUIT.allowRequest()) {
            emitBuildFallbackBlocked(primary, fallbackResolved, context, "circuit_open");
            BUILD_FALLBACK_METRICS.record(FallbackMetrics.MetricType.ALL_FAILED);
            return null;
        }

        emitBuildFallbackAttempt(primary, fallbackResolved, context, primaryError);
        OriginResolution fallbackOrigin = resolveTemplateOrigin(fallbackResolved.template());
        long fallbackStart = System.nanoTime();
        ArenaBuilder.BuildResult fallbackResult = builder.build(
            fallbackResolved,
            fallbackOrigin.centerX(),
            fallbackOrigin.originY(),
            fallbackOrigin.centerZ()
        );
        BUILD_FALLBACK_METRICS.recordFallbackTime(System.nanoTime() - fallbackStart);

        if (fallbackResult.success()) {
            BUILD_FALLBACK_CIRCUIT.recordSuccess();
            BUILD_FALLBACK_METRICS.record(FallbackMetrics.MetricType.FALLBACK_USED);
            return new BuildAttemptResult(fallbackResolved, fallbackOrigin, fallbackResult, true, true);
        }

        BUILD_FALLBACK_CIRCUIT.recordFailure();
        BUILD_FALLBACK_METRICS.record(FallbackMetrics.MetricType.ALL_FAILED);
        return new BuildAttemptResult(fallbackResolved, fallbackOrigin, fallbackResult, true, false);
    }

    private @javax.annotation.Nullable ResolvedArena resolveFallbackArena(ResolvedArena primary) {
        if (arenaTemplateRegistry == null || primary == null) {
            return null;
        }
        if (FALLBACK_TEMPLATE_ID.equals(primary.template().id())) {
            return null;
        }
        ArenaTemplate fallbackTemplate = arenaTemplateRegistry.get(FALLBACK_TEMPLATE_ID).orElse(null);
        if (fallbackTemplate == null) {
            return null;
        }
        ArenaPolicy policy = selectFallbackPolicy(fallbackTemplate.id());
        return ResolvedArena.create(fallbackTemplate, policy, Map.of("fallback", 1.0));
    }

    private ArenaPolicy selectFallbackPolicy(String templateId) {
        if (arenaPolicyRegistry != null) {
            List<ArenaPolicy> candidates = arenaPolicyRegistry.forTemplate(templateId).stream()
                .filter(ArenaPolicy::enabled)
                .toList();
            if (!candidates.isEmpty()) {
                return candidates.stream()
                    .sorted(Comparator
                        .comparingInt(ArenaPolicy::priority).reversed()
                        .thenComparingDouble(ArenaPolicy::weight).reversed()
                        .thenComparingInt(ArenaPolicy::version).reversed())
                    .findFirst()
                    .orElse(ArenaPolicy.DEFAULT);
            }
        }
        return ArenaPolicy.DEFAULT;
    }

    private void emitBuildFallbackAttempt(ResolvedArena primary,
                                          ResolvedArena fallback,
                                          String context,
                                          @javax.annotation.Nullable String primaryError) {
        if (arenaTelemetry == null || primary == null || fallback == null) {
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("context", context != null ? context : "");
        data.put("primaryTemplateId", primary.template().id());
        data.put("primaryTemplateVersion", primary.template().version());
        data.put("primaryPolicyId", primary.policy().id());
        data.put("fallbackTemplateId", fallback.template().id());
        data.put("fallbackTemplateVersion", fallback.template().version());
        data.put("fallbackPolicyId", fallback.policy().id());
        data.put("circuitState", BUILD_FALLBACK_CIRCUIT.getState().name());
        if (primaryError != null && !primaryError.isBlank()) {
            data.put("primaryError", primaryError);
        }
        arenaTelemetry.emit("arena.build.fallback_attempt", data);
    }

    private void emitBuildFallbackBlocked(ResolvedArena primary,
                                          ResolvedArena fallback,
                                          String context,
                                          String reason) {
        if (arenaTelemetry == null || primary == null || fallback == null) {
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("context", context != null ? context : "");
        data.put("primaryTemplateId", primary.template().id());
        data.put("fallbackTemplateId", fallback.template().id());
        data.put("reason", reason != null ? reason : "unknown");
        data.put("circuitState", BUILD_FALLBACK_CIRCUIT.getState().name());
        arenaTelemetry.emit("arena.build.fallback_blocked", data);
    }

    private String handleBuildAbort(ResolvedArena resolved,
                                    String context,
                                    String technicalMessage,
                                    @javax.annotation.Nullable Throwable cause,
                                    boolean fallbackAttempted) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("context", context != null ? context : "");
        if (resolved != null) {
            ctx.put("templateId", resolved.template().id());
            ctx.put("templateVersion", resolved.template().version());
            ctx.put("policyId", resolved.policy().id());
            ctx.put("policyVersion", resolved.policy().version());
        }
        ctx.put("fallbackAttempted", fallbackAttempted);

        UserFriendlyError error = new UserFriendlyError.Builder()
            .type(UserFriendlyError.ErrorType.ARENA_BUILD_FAILED)
            .technicalMessage(technicalMessage != null ? technicalMessage : "Build failed")
            .cause(cause)
            .context(ctx)
            .build();
        error.log();

        if (arenaTelemetry != null && resolved != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("context", context != null ? context : "");
            data.put("templateId", resolved.template().id());
            data.put("templateVersion", resolved.template().version());
            data.put("policyId", resolved.policy().id());
            data.put("policyVersion", resolved.policy().version());
            data.put("fallbackAttempted", fallbackAttempted);
            data.put("errorRef", error.getShortRef());
            if (technicalMessage != null && !technicalMessage.isBlank()) {
                data.put("error", technicalMessage);
            }
            arenaTelemetry.emit("arena.build.abort", data);
        }

        return error.getPlayerMessage();
    }

    private ArenaHandle createArenaHandle(ArenaBuilder.BuildResult buildResult,
                                          ResolvedArena resolved,
                                          UUID instanceId,
                                          OriginResolution origin,
                                          @javax.annotation.Nullable ServerLevel level) {
        ArenaTemplate template = resolved.template();
        ArenaHandle.AABB bounds = computeHandleBounds(template, origin);
        List<ArenaHandle.BlockPos> playerSpawns = extractPlayerSpawns(template, origin, level);
        List<ArenaHandle.BlockPos> mobSpawns = extractMobSpawns(template, origin, level);

        return ArenaHandle.builder()
            .arenaId(buildResult.arenaId())
            .instanceId(instanceId)
            .templateId(template.id())
            .templateVersion(template.version())
            .policyId(resolved.policy().id())
            .policyVersion(resolved.policy().version())
            .bounds(bounds)
            .origin(origin.originX(), origin.originY(), origin.originZ())
            .playerSpawnPositions(playerSpawns)
            .mobSpawnPositions(mobSpawns)
            .build();
    }

    private boolean isHandleValid(@javax.annotation.Nullable ArenaHandle handle) {
        if (handle == null) {
            return false;
        }
        boolean hasPlayerSpawns = handle.playerSpawnPositions() != null && !handle.playerSpawnPositions().isEmpty();
        boolean hasMobSpawns = handle.mobSpawnPositions() != null && !handle.mobSpawnPositions().isEmpty();
        return hasPlayerSpawns && hasMobSpawns;
    }

    private ArenaContext createArenaAdapter(ServerLevel level, ArenaHandle handle) {
        return new ArenaContext(level, handle);
    }

    private ArenaHandle.AABB computeHandleBounds(ArenaTemplate template, OriginResolution origin) {
        int sizeX = resolveTemplateSize(template, template.sizeX());
        int sizeZ = resolveTemplateSize(template, template.sizeZ());
        int halfX = sizeX / 2;
        int halfZ = sizeZ / 2;

        ArenaTemplate.OriginMode mode = template.origin() != null && template.origin().mode() != null
            ? template.origin().mode()
            : ArenaTemplate.OriginMode.CENTER;

        int minX;
        int minZ;
        int maxX;
        int maxZ;
        switch (mode) {
            case CORNER_NW -> {
                minX = origin.originX();
                minZ = origin.originZ();
                maxX = origin.originX() + sizeX - 1;
                maxZ = origin.originZ() + sizeZ - 1;
            }
            case CORNER_SW -> {
                minX = origin.originX();
                minZ = origin.originZ() - sizeZ + 1;
                maxX = origin.originX() + sizeX - 1;
                maxZ = origin.originZ();
            }
            case CENTER -> {
                minX = origin.originX() - halfX;
                maxX = origin.originX() + halfX - 1;
                minZ = origin.originZ() - halfZ;
                maxZ = origin.originZ() + halfZ - 1;
            }
            default -> {
                minX = origin.originX() - halfX;
                maxX = origin.originX() + halfX - 1;
                minZ = origin.originZ() - halfZ;
                maxZ = origin.originZ() + halfZ - 1;
            }
        }

        int minY = template.floor() != null ? template.floor().y() : origin.originY();
        int maxY = minY;
        if (template.ceiling() != null) {
            maxY = Math.max(maxY, template.ceiling().y());
        }
        if (template.walls() != null) {
            maxY = Math.max(maxY, template.walls().startY() + template.walls().height());
        }

        return new ArenaHandle.AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private void updateInstanceArenaMetadata(InstanceData instance,
                                             ArenaTemplate template,
                                             @javax.annotation.Nullable ArenaHandle handle,
                                             OriginResolution origin) {
        if (instance == null || template == null) {
            return;
        }
        int sizeX = resolveTemplateSize(template, template.sizeX());
        int sizeZ = resolveTemplateSize(template, template.sizeZ());
        int radius = Math.max(1, Math.max(sizeX, sizeZ) / 2);

        net.minecraft.core.BlockPos center;
        if (handle != null && handle.playerSpawnPositions() != null && !handle.playerSpawnPositions().isEmpty()) {
            ArenaHandle.BlockPos spawn = handle.primaryPlayerSpawn();
            center = new net.minecraft.core.BlockPos(spawn.x(), spawn.y(), spawn.z());
        } else {
            center = new net.minecraft.core.BlockPos(origin.centerX(), origin.originY(), origin.centerZ());
        }

        int templateVersion = handle != null ? handle.templateVersion() : template.version();
        String policyId = handle != null ? handle.policyId() : null;
        int policyVersion = handle != null ? handle.policyVersion() : 0;
        instance.setArena(center, radius, template.id(), templateVersion, policyId, policyVersion);
        InstanceRegistry.INSTANCE.markDirty();
    }

    private void updateSnapshotArenaTemplate(ServerPlayer player, ArenaHandle handle) {
        if (player == null || handle == null) {
            return;
        }
        RecoverySystem.INSTANCE.loadSnapshot(player.getUUID()).ifPresent(snapshot -> {
            snapshot.withArenaTemplate(
                handle.templateId(),
                handle.templateVersion(),
                handle.policyId(),
                handle.policyVersion()
            );
            RecoverySystem.INSTANCE.saveSnapshot(snapshot);
        });
    }

    private List<ArenaHandle.BlockPos> extractPlayerSpawns(ArenaTemplate template,
                                                           OriginResolution origin,
                                                           @javax.annotation.Nullable ServerLevel level) {
        List<ArenaHandle.BlockPos> spawns = new ArrayList<>();
        TemplateSpawnValidator validator = null;
        if (level != null) {
            ArenaTelemetry telemetry = arenaTelemetry != null ? arenaTelemetry : new ArenaTelemetry();
            validator = new TemplateSpawnValidator(telemetry);
        }
        if (template.spawnSlots() != null) {
            for (int i = 0; i < template.spawnSlots().size(); i++) {
                ArenaTemplate.SpawnSlot slot = template.spawnSlots().get(i);
                if (slot.tags() != null && (slot.tags().contains("player") || slot.tags().contains("team"))) {
                    int[] pos = slot.pos();
                    if (pos == null || pos.length != 3) continue;
                    int offsetX = 0;
                    int offsetY = 0;
                    int offsetZ = 0;
                    if (template.playerSpawnOffset() != null) {
                        offsetX = template.playerSpawnOffset().x();
                        offsetY = template.playerSpawnOffset().y();
                        offsetZ = template.playerSpawnOffset().z();
                    }
                    int x = origin.originX() + pos[0] + offsetX;
                    int y = resolveSpawnY(slot, template, origin.originY()) + offsetY;
                    int z = origin.originZ() + pos[2] + offsetZ;
                    if (validator != null) {
                        BlockPos absPos = new BlockPos(x, y, z);
                        if (!validator.validateAtRuntime(template.id(), slot, level, absPos)) {
                            LOGGER.warn("[EnduranceQuest] Player spawn slot failed runtime validation at {} (template: {})",
                                absPos, template.id());
                            continue;
                        }
                    }
                    spawns.add(new ArenaHandle.BlockPos(x, y, z));
                }
            }
        }

        if (spawns.isEmpty()) {
            int floorY = template.floor() != null ? template.floor().y() : origin.originY();
            spawns.add(new ArenaHandle.BlockPos(origin.centerX(), floorY + 1, origin.centerZ()));
        }

        return spawns;
    }

    private List<ArenaHandle.BlockPos> extractMobSpawns(ArenaTemplate template,
                                                        OriginResolution origin,
                                                        @javax.annotation.Nullable ServerLevel level) {
        List<ArenaTemplate.SpawnSlot> mobSlots = new ArrayList<>();
        if (template.spawnSlots() != null) {
            for (ArenaTemplate.SpawnSlot slot : template.spawnSlots()) {
                if (slot.tags() != null && (slot.tags().contains("mob") || slot.tags().contains("boss"))) {
                    mobSlots.add(slot);
                }
            }
        }

        if (mobSlots.isEmpty()) {
            return List.of();
        }

        TemplateSpawnValidator validator = null;
        if (level != null) {
            ArenaTelemetry telemetry = arenaTelemetry != null ? arenaTelemetry : new ArenaTelemetry();
            validator = new TemplateSpawnValidator(telemetry);
        }

        int centerOffsetX = origin.centerX() - origin.originX();
        int centerOffsetZ = origin.centerZ() - origin.originZ();
        List<ArenaTemplate.SpawnSlot> selected = selectByStrategy(template, mobSlots, centerOffsetX, centerOffsetZ);
        List<ArenaHandle.BlockPos> spawns = new ArrayList<>(selected.size());
        for (ArenaTemplate.SpawnSlot slot : selected) {
            int[] pos = slot.pos();
            if (pos == null || pos.length != 3) continue;
            int x = origin.originX() + pos[0];
            int y = resolveSpawnY(slot, template, origin.originY());
            int z = origin.originZ() + pos[2];
            if (validator != null) {
                BlockPos absPos = new BlockPos(x, y, z);
                if (!validator.validateAtRuntime(template.id(), slot, level, absPos)) {
                    LOGGER.warn("[EnduranceQuest] Mob spawn slot failed runtime validation at {} (template: {})",
                        absPos, template.id());
                    continue;
                }
            }
            spawns.add(new ArenaHandle.BlockPos(x, y, z));
        }
        return spawns;
    }

    private int resolveSpawnY(ArenaTemplate.SpawnSlot slot, ArenaTemplate template, int originY) {
        int baseY = slot.pos() != null && slot.pos().length == 3 ? slot.pos()[1] : 0;
        int floorY = template.floor() != null ? template.floor().y() : originY;
        if (slot.yMode() == ArenaTemplate.SpawnSlot.YMode.RELATIVE_TO_FLOOR) {
            return floorY + baseY;
        }
        return baseY;
    }

    private List<ArenaTemplate.SpawnSlot> selectByStrategy(ArenaTemplate template,
                                                           List<ArenaTemplate.SpawnSlot> mobSlots,
                                                           int centerOffsetX,
                                                           int centerOffsetZ) {
        ArenaTemplate.MobSpawnStrategy strategy = template.mobSpawnStrategy() != null
            ? template.mobSpawnStrategy()
            : ArenaTemplate.MobSpawnStrategy.DISTRIBUTED;

        switch (strategy) {
            case CLUSTERED -> {
                List<ArenaTemplate.SpawnSlot> centered = mobSlots.stream()
                    .filter(s -> s.tags() != null && s.tags().contains("center"))
                    .toList();
                if (!centered.isEmpty()) {
                    emitSpawnStrategyTelemetry(template.id(), "clustered", centered.size(), null);
                    return centered;
                }
                List<ArenaTemplate.SpawnSlot> nearest = new ArrayList<>(mobSlots);
                nearest.sort(Comparator.comparingDouble(s -> horizontalDistance(s, centerOffsetX, centerOffsetZ)));
                List<ArenaTemplate.SpawnSlot> picked = nearest.subList(0, Math.min(4, nearest.size()));
                emitSpawnStrategyTelemetry(template.id(), "clustered_fallback", picked.size(), null);
                return picked;
            }
            case CORNERS -> {
                List<ArenaTemplate.SpawnSlot> corners = mobSlots.stream()
                    .filter(s -> s.tags() != null && s.tags().contains("corner"))
                    .toList();
                if (corners.size() >= 4) {
                    emitSpawnStrategyTelemetry(template.id(), "corners", corners.size(), null);
                    return corners;
                }
                List<ArenaTemplate.SpawnSlot> farthest = new ArrayList<>(mobSlots);
                farthest.sort(Comparator.comparingDouble((ArenaTemplate.SpawnSlot s) ->
                    horizontalDistance(s, centerOffsetX, centerOffsetZ)).reversed());
                List<ArenaTemplate.SpawnSlot> picked = farthest.subList(0, Math.min(4, farthest.size()));
                emitSpawnStrategyTelemetry(template.id(), "corners_fallback", picked.size(), null);
                return picked;
            }
            case RING -> {
                int sizeX = resolveTemplateSize(template, template.sizeX());
                int sizeZ = resolveTemplateSize(template, template.sizeZ());
                double requiredRadius = Math.max(sizeX, sizeZ) / 4.0;
                List<ArenaTemplate.SpawnSlot> ring = mobSlots.stream()
                    .filter(s -> horizontalDistance(s, centerOffsetX, centerOffsetZ) >= requiredRadius)
                    .toList();
                if (!ring.isEmpty()) {
                    emitSpawnStrategyTelemetry(template.id(), "ring", ring.size(), requiredRadius);
                    return ring;
                }
                emitSpawnStrategyTelemetry(template.id(), "ring_fallback_distributed", mobSlots.size(), requiredRadius);
                return mobSlots;
            }
            default -> {
                emitSpawnStrategyTelemetry(template.id(), "distributed", mobSlots.size(), null);
                return mobSlots;
            }
        }
    }

    private void emitSpawnStrategyTelemetry(String templateId, String strategy, int slots, Double radius) {
        if (arenaTelemetry == null) {
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("templateId", templateId);
        data.put("strategy", strategy);
        data.put("slots", slots);
        if (radius != null) {
            data.put("radius", radius);
        }
        arenaTelemetry.emit("arena.spawn.strategy_used", data);
    }

    private double horizontalDistance(ArenaTemplate.SpawnSlot slot, double centerX, double centerZ) {
        int[] pos = slot.pos();
        if (pos == null || pos.length != 3) return Double.MAX_VALUE;
        double dx = pos[0] - centerX;
        double dz = pos[2] - centerZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * PHASE 3: Start a quest for players using a pre-created arena.
     * All players should already be teleported to the arena.
     *
     * @param players List of players to start the quest for
     * @param arena The prepared arena
     * @param mobId The mob type for the quest
     * @param settings Quest settings
     * @return Map of player UUID to StartQuestResult
     */
    public Map<UUID, StartQuestResult> startPreparedQuest(
            List<ServerPlayer> players, ArenaContext arena,
            ResourceLocation mobId, QuestSettings settings,
            @javax.annotation.Nullable UUID instanceId,
            @javax.annotation.Nullable ArenaHandle arenaHandle) {

        Map<UUID, StartQuestResult> results = new HashMap<>();

        String readinessError = getTemplateSystemReadinessError();
        if (readinessError != null) {
            LOGGER.error("[EnduranceQuest] startPreparedQuest blocked: {}", readinessError);
            for (ServerPlayer player : players) {
                if (player != null) {
                    results.put(player.getUUID(), new StartQuestResult(false, readinessError, null));
                }
            }
            return results;
        }
        if (arenaHandle == null) {
            String error = "ArenaHandle required; legacy arena path is deprecated.";
            emitLegacyCall("missing_arena_handle", "startPreparedQuest",
                arena != null ? arena.getLevel() : null);
            LOGGER.error("[EnduranceQuest] startPreparedQuest blocked: {}", error);
            for (ServerPlayer player : players) {
                if (player != null) {
                    results.put(player.getUUID(), new StartQuestResult(false, error, null));
                }
            }
            return results;
        }
        if (!isHandleValid(arenaHandle)) {
            String error = "Arena template missing required spawn slots.";
            emitGateFailure("missing_spawn_slots", null);
            LOGGER.error("[EnduranceQuest] startPreparedQuest blocked: {}", error);
            for (ServerPlayer player : players) {
                if (player != null) {
                    results.put(player.getUUID(), new StartQuestResult(false, error, null));
                }
            }
            return results;
        }

        // Get quest template
        EnduranceQuest template = questTemplates.get(mobId);
        if (template == null) {
            for (ServerPlayer player : players) {
                results.put(player.getUUID(), new StartQuestResult(false, "Unknown quest type: " + mobId, null));
            }
            return results;
        }

        // Start quest for each player
        for (ServerPlayer player : players) {
            if (player == null || !player.isAlive()) continue;

            UUID playerId = player.getUUID();

            // Create quest instance
            EnduranceQuest quest = new EnduranceQuest(template.getMobConfig());
            quest.setTotalWaves(settings.totalWaves);
            quest.setEndlessMode(settings.endlessMode);

            // Create placeholder session for atomic insert
            ActiveQuestSession placeholderSession = new ActiveQuestSession(playerId, quest, null, System.currentTimeMillis());

            // Atomic insert
            ActiveQuestSession existingSession = activeSessions.putIfAbsent(playerId, placeholderSession);
            if (existingSession != null) {
                results.put(playerId, new StartQuestResult(false,
                    Objects.requireNonNull(I18n.translate("devmod.endurance.active_quest")).getString(), null));
                continue;
            }

            // Start the quest
            quest.start(arena.getId());

            // Create the real session with arena and party settings
            ActiveQuestSession session = new ActiveQuestSession(
                playerId, quest, arena, System.currentTimeMillis(),
                settings.partyId, settings.questType, settings.getPlayerCount()
            );
            UUID effectiveInstanceId = instanceId != null ? instanceId : arenaHandle.instanceId();
            if (effectiveInstanceId != null) {
                session.setInstanceId(effectiveInstanceId);
            }
            session.setArenaHandle(arenaHandle);
            updateSnapshotArenaTemplate(player, arenaHandle);
            session.setDifficultyLabel(resolveDifficultyLabel(settings, quest.getMobConfig()));
            session.setQuestTypeLabel(resolveQuestTypeLabel(settings, quest.getMobConfig()));
            session.setKitId(settings.kitId);
            activeSessions.put(playerId, session); // Replaces placeholder

            // Apply arena policy config overrides and sync to client
            applyAndSyncArenaOverrides(player, session);

            // Prepare player (save state, give kit - NO TELEPORT, already done)
            EndurancePlayerStateManager.INSTANCE.preparePlayerForQuest(player, session);

            // Initialize all subsystems
            EnduranceEventHandler.onQuestStart(player, session);

            // Start telemetry
            String dungeonId = "endurance_party_" + mobId.toString().replace(":", "_");
            TelemetryService.INSTANCE.startDungeonSession(player, dungeonId);

            results.put(playerId, new StartQuestResult(true, "Quest started!", session));

            LOGGER.info("[EnduranceQuest] Started prepared quest for player {}: {}",
                player.getName().getString(), quest.getDisplayName());
        }

        // Start wave 1 for the arena (only once, shared between all players)
        // Use the first successful session
        for (StartQuestResult result : results.values()) {
            if (result.success() && result.session() != null) {
                WaveManager.INSTANCE.startWave(result.session());

                // Notify all players that wave 1 started
                for (ServerPlayer player : players) {
                    if (player != null && results.get(player.getUUID()) != null && results.get(player.getUUID()).success()) {
                        EnduranceEventHandler.onWaveStart(player, result.session(), 1);
                    }
                }
                break;
            }
        }

        return results;
    }

    /**
     * Result of preparing an arena for party quest.
     */
    public record PreparedArenaResult(
        boolean success,
        String errorMessage,
        ArenaContext arena,
        @javax.annotation.Nullable ArenaHandle handle,
        ResourceLocation mobId,
        EnduranceQuestRegistry.MobQuestConfig mobConfig,
        @javax.annotation.Nullable UUID instanceId
    ) {
        public static PreparedArenaResult success(ArenaContext arena,
                                                   @javax.annotation.Nullable ArenaHandle handle,
                                                   ResourceLocation mobId,
                                                   EnduranceQuestRegistry.MobQuestConfig mobConfig,
                                                   @javax.annotation.Nullable UUID instanceId) {
            return new PreparedArenaResult(true, null, arena, handle, mobId, mobConfig, instanceId);
        }

        public static PreparedArenaResult failure(String message) {
            return new PreparedArenaResult(false, message, null, null, null, null, null);
        }
    }

    // ========== Single Player Quest Flow ==========

    /**
     * Start a new quest for a player.
     */
    public StartQuestResult startQuest(ServerPlayer player, ResourceLocation mobId, QuestSettings settings) {
        UUID playerId = player.getUUID();

        // Get quest template first (before any state modification)
        EnduranceQuest template = questTemplates.get(mobId);
        if (template == null) {
            return new StartQuestResult(false, "Unknown quest type: " + mobId, null);
        }
        String readinessError = getTemplateSystemReadinessError();
        if (readinessError != null) {
            return new StartQuestResult(false, readinessError, null);
        }

        // Create new quest instance upfront (before atomic check)
        EnduranceQuest quest = new EnduranceQuest(template.getMobConfig());
        quest.setTotalWaves(settings.totalWaves);
        quest.setEndlessMode(settings.endlessMode);

        // Create placeholder session for atomic insert
        // NOTE: Arena is null here - will be set after successful creation
        ActiveQuestSession placeholderSession = new ActiveQuestSession(playerId, quest, null, System.currentTimeMillis());

        // ATOMIC: Use putIfAbsent to prevent race condition
        // This ensures only one thread can start a quest for this player
        ActiveQuestSession existingSession = activeSessions.putIfAbsent(playerId, placeholderSession);
        if (existingSession != null) {
            return new StartQuestResult(false, Objects.requireNonNull(I18n.translate("devmod.endurance.active_quest")).getString(), null);
        }

        // === INSTANCE DIMENSION MODE (forced) ===
        return startQuestInInstanceDimension(player, mobId, quest, settings);
    }

    /**
     * Start a quest in an isolated instance dimension.
     * This is the new preferred method using the Instance Dimension System.
     *
     * IMPORTANT: This method is NON-BLOCKING. It returns immediately with a "pending" status
     * and the quest setup is completed asynchronously when the instance is ready.
     */
    private StartQuestResult startQuestInInstanceDimension(ServerPlayer player, ResourceLocation mobId,
                                                           EnduranceQuest quest, QuestSettings settings) {
        String readinessError = getTemplateSystemReadinessError();
        if (readinessError != null) {
            activeSessions.remove(player.getUUID());
            return new StartQuestResult(false, readinessError, null);
        }
        return startQuestInInstanceDimensionWithTemplate(player, mobId, quest, settings);
    }

    private StartQuestResult startQuestInInstanceDimensionWithTemplate(ServerPlayer player, ResourceLocation mobId,
                                                                       EnduranceQuest quest, QuestSettings settings) {
        UUID playerId = player.getUUID();

        ActiveQuestSession pendingSession = activeSessions.get(playerId);
        if (pendingSession == null) {
            LOGGER.error("[EnduranceQuest] No placeholder session found for template instance quest start");
            return new StartQuestResult(false, "Internal error: missing session", null);
        }

        ResolvedArena resolved = resolveArenaTemplate(playerId, mobId, settings);
        if (resolved == null) {
            activeSessions.remove(playerId);
            return new StartQuestResult(false, "No matching arena template/policy", null);
        }

        pendingSession.setPending(true);
        pendingSession.setDifficultyLabel(resolveDifficultyLabel(settings, quest.getMobConfig()));
        pendingSession.setQuestTypeLabel(resolveQuestTypeLabel(settings, quest.getMobConfig()));
        pendingSession.setKitId(settings.kitId);
        pendingSession.scheduleBriefing(BRIEFING_TICKS);
        pendingSession.scheduleInstanceStart(mobId, settings, resolved, PRE_TELEPORT_COUNTDOWN_TICKS);

        int briefingSeconds = (int) Math.ceil(BRIEFING_TICKS / 20.0);
        List<String> briefingLines = buildBriefingLines(quest, resolved, pendingSession);
        pendingSession.setBriefingLines(briefingLines);
        sendSoloSequenceUpdate(
            player,
            pendingSession,
            QuestSequencePayload.Phase.BRIEFING,
            briefingSeconds,
            quest.getDisplayName(),
            "Endurance briefing",
            briefingLines
        );
        pendingSession.setLastBriefingSeconds(briefingSeconds);

        return new StartQuestResult(true, "Preparing instance...", pendingSession);
    }

    void startPendingInstanceQuest(ServerPlayer player, ActiveQuestSession session) {
        if (player == null || session == null) {
            return;
        }

        ResourceLocation mobId = session.getPendingMobId();
        QuestSettings settings = session.getPendingSettings();
        ResolvedArena resolved = session.getPendingResolved();
        session.clearPendingInstanceStart();

        if (mobId == null || settings == null || resolved == null) {
            LOGGER.error("[EnduranceQuest] Pending instance start missing data for player {}",
                player.getName().getString());
            activeSessions.remove(session.getPlayerId());
            sendSoloSequenceUpdate(player, session, QuestSequencePayload.Phase.CANCELLED, 0);
            return;
        }

        com.devmod.network.NetworkHandler.sendInstanceLoadingShow(player, "Creating template instance...");
        player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("[DevMod] Creating instance dimension...")
            .withStyle(ChatFormatting.YELLOW)));

        InstanceManager.INSTANCE
            .startInstanceQuestImmediate(player, resolved.template().id(), mobId.toString(), null)
            .thenAccept(instanceId -> {
                completeTemplateInstanceQuestSetup(player, session.getPlayerId(), mobId, session.getQuest(),
                    settings, resolved, instanceId);
            });
    }

    void sendSoloSequenceUpdate(ServerPlayer player, ActiveQuestSession session,
                                QuestSequencePayload.Phase phase, int secondsRemaining) {
        sendSoloSequenceUpdate(player, session, phase, secondsRemaining, null, null, List.of());
    }

    void sendSoloSequenceUpdate(ServerPlayer player, ActiveQuestSession session,
                                QuestSequencePayload.Phase phase, int secondsRemaining,
                                @javax.annotation.Nullable String title,
                                @javax.annotation.Nullable String subtitle,
                                List<String> infoLines) {
        if (player == null || session == null || session.isMultiplayer()) {
            return;
        }
        if (phase == QuestSequencePayload.Phase.CANCELLED) {
            EnduranceTelemetryService.INSTANCE.recordCountdownCancelled(session.getQuest().getQuestId());
        }
        PacketDistributor.sendToPlayer(player, new QuestSequencePayload(
            player.getUUID(),
            phase,
            Math.max(0, secondsRemaining),
            0,
            List.of(),
            title,
            subtitle,
            infoLines != null ? infoLines : List.of()
        ));
    }

    private void completeTemplateInstanceQuestSetup(ServerPlayer player, UUID playerId, ResourceLocation mobId,
                                                    EnduranceQuest quest, QuestSettings settings,
                                                    ResolvedArena resolved,
                                                    @javax.annotation.Nullable UUID instanceId) {
        var server = player.getServer();
        ActiveQuestSession pendingSession = activeSessions.get(playerId);
        if (server == null || server.getPlayerList().getPlayer(Objects.requireNonNull(playerId)) == null) {
            LOGGER.warn("[EnduranceQuest] Player {} disconnected during template instance creation", playerId);
            activeSessions.remove(playerId);
            if (instanceId != null) {
                InstanceArenaManager.INSTANCE.endInstanceQuest(instanceId, false);
            }
            return;
        }

        if (instanceId == null) {
            LOGGER.error("[EnduranceQuest] Template instance creation failed for player {}", playerId);
            activeSessions.remove(playerId);
            com.devmod.network.NetworkHandler.sendInstanceLoadingHide(player);
            sendSoloSequenceUpdate(player, pendingSession, QuestSequencePayload.Phase.CANCELLED, 0);
            player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("[DevMod] Failed to create instance")
                .withStyle(ChatFormatting.RED)));
            return;
        }

        Optional<InstanceData> instanceOpt = InstanceRegistry.INSTANCE.getInstance(instanceId);
        if (instanceOpt.isEmpty()) {
            InstanceArenaManager.INSTANCE.endInstanceQuest(instanceId, false);
            activeSessions.remove(playerId);
            com.devmod.network.NetworkHandler.sendInstanceLoadingHide(player);
            sendSoloSequenceUpdate(player, pendingSession, QuestSequencePayload.Phase.CANCELLED, 0);
            player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("[DevMod] Instance not found")
                .withStyle(ChatFormatting.RED)));
            return;
        }

        InstanceData instance = instanceOpt.get();
        var dimensionKey = instance.getDimensionKey();
        if (dimensionKey == null) {
            InstanceArenaManager.INSTANCE.endInstanceQuest(instanceId, false);
            activeSessions.remove(playerId);
            com.devmod.network.NetworkHandler.sendInstanceLoadingHide(player);
            sendSoloSequenceUpdate(player, pendingSession, QuestSequencePayload.Phase.CANCELLED, 0);
            player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("[DevMod] Instance dimension not ready")
                .withStyle(ChatFormatting.RED)));
            return;
        }

        ServerLevel instanceLevel = server.getLevel(dimensionKey);
        if (instanceLevel == null) {
            InstanceArenaManager.INSTANCE.endInstanceQuest(instanceId, false);
            activeSessions.remove(playerId);
            com.devmod.network.NetworkHandler.sendInstanceLoadingHide(player);
            sendSoloSequenceUpdate(player, pendingSession, QuestSequencePayload.Phase.CANCELLED, 0);
            player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("[DevMod] Instance level not found")
                .withStyle(ChatFormatting.RED)));
            return;
        }

        ArenaTemplate template = resolved.template();
        OriginResolution origin = resolveTemplateOrigin(template);

        if (shouldBuildAsync(template)) {
            AsyncArenaBuilder asyncBuilder = asyncBuildCoordinator.getOrCreate(instanceLevel);
            UUID arenaId = UUID.randomUUID();
            asyncBuilder.submitBuildAsync(
                arenaId,
                template,
                origin.centerX(),
                origin.originY(),
                origin.centerZ()
            ).whenComplete((asyncResult, buildError) -> {
                server.execute(() -> {
                    ActiveQuestSession currentSession = activeSessions.get(playerId);
                    ServerPlayer currentPlayer = server.getPlayerList().getPlayer(Objects.requireNonNull(playerId));
                    if (currentPlayer == null || currentSession == null) {
                        InstanceArenaManager.INSTANCE.endInstanceQuest(instanceId, false);
                        activeSessions.remove(playerId);
                        return;
                    }
                    if (buildError != null || asyncResult == null || !asyncResult.success()) {
                        String msg = buildError != null && buildError.getMessage() != null
                            ? buildError.getMessage()
                            : (asyncResult != null ? asyncResult.errorMessage() : "Build failed");
                        com.devmod.arena.builder.TemplateArenaBuilder builder =
                            createTemplateBuilder(instanceLevel);
                        BuildAttemptResult fallbackAttempt = attemptFallbackOnly(
                            builder,
                            resolved,
                            "solo_async",
                            msg
                        );
                        if (fallbackAttempt != null && fallbackAttempt.result().success()) {
                            finalizeTemplateInstanceQuestSetup(
                                currentPlayer,
                                currentSession,
                                mobId,
                                quest,
                                settings,
                                fallbackAttempt.resolved(),
                                instanceId,
                                instanceLevel,
                                fallbackAttempt.origin(),
                                instance,
                                fallbackAttempt.result()
                            );
                            return;
                        }
                        String technicalMessage = msg;
                        if (fallbackAttempt != null && fallbackAttempt.result() != null
                            && fallbackAttempt.result().errorMessage() != null) {
                            technicalMessage = fallbackAttempt.result().errorMessage();
                        }
                        String userMessage = handleBuildAbort(
                            resolved,
                            "solo_async",
                            technicalMessage,
                            buildError,
                            fallbackAttempt != null
                        );
                        failPendingInstanceSetup(currentPlayer, currentSession, instanceId, userMessage);
                        return;
                    }
                    ArenaBuilder.BuildResult buildResult = ArenaBuilder.BuildResult.success(
                        asyncResult.arenaId(),
                        template.id(),
                        asyncResult.blocksPlaced(),
                        asyncResult.durationMs()
                    );
                    finalizeTemplateInstanceQuestSetup(
                        currentPlayer,
                        currentSession,
                        mobId,
                        quest,
                        settings,
                        resolved,
                        instanceId,
                        instanceLevel,
                        origin,
                        instance,
                        buildResult
                    );
                });
            });
            return;
        }

        com.devmod.arena.builder.TemplateArenaBuilder builder = createTemplateBuilder(instanceLevel);
        BuildAttemptResult attempt = buildWithFallback(
            builder,
            resolved,
            origin,
            "solo_sync"
        );

        if (!attempt.result().success()) {
            String msg = attempt.result().errorMessage() != null
                ? attempt.result().errorMessage()
                : "Build failed";
            String userMessage = handleBuildAbort(
                attempt.resolved(),
                "solo_sync",
                msg,
                null,
                attempt.fallbackAttempted()
            );
            failPendingInstanceSetup(player, pendingSession, instanceId, userMessage);
            return;
        }

        finalizeTemplateInstanceQuestSetup(
            player,
            pendingSession,
            mobId,
            quest,
            settings,
            attempt.resolved(),
            instanceId,
            instanceLevel,
            attempt.origin(),
            instance,
            attempt.result()
        );
    }

    private void failPendingInstanceSetup(ServerPlayer player,
                                          @javax.annotation.Nullable ActiveQuestSession pendingSession,
                                          @javax.annotation.Nullable UUID instanceId,
                                          String message) {
        if (instanceId != null) {
            InstanceArenaManager.INSTANCE.endInstanceQuest(instanceId, false);
        }
        if (pendingSession != null) {
            activeSessions.remove(pendingSession.getPlayerId());
        }
        if (player != null) {
            com.devmod.network.NetworkHandler.sendInstanceLoadingHide(player);
            if (pendingSession != null) {
                sendSoloSequenceUpdate(player, pendingSession, QuestSequencePayload.Phase.CANCELLED, 0);
            }
            String msg = message != null ? message : "Build failed";
            player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("[DevMod] " + msg)
                .withStyle(ChatFormatting.RED)));
        }
    }

    private void finalizeTemplateInstanceQuestSetup(ServerPlayer player,
                                                    ActiveQuestSession pendingSession,
                                                    ResourceLocation mobId,
                                                    EnduranceQuest quest,
                                                    QuestSettings settings,
                                                    ResolvedArena resolved,
                                                    UUID instanceId,
                                                    ServerLevel instanceLevel,
                                                    OriginResolution origin,
                                                    InstanceData instance,
                                                    ArenaBuilder.BuildResult buildResult) {
        ArenaTemplate template = resolved.template();
        ArenaHandle handle = createArenaHandle(buildResult, resolved, instanceId, origin, instanceLevel);
        if (!isHandleValid(handle)) {
            failPendingInstanceSetup(player, pendingSession, instanceId, "Template missing required spawn slots (player/mob)");
            return;
        }
        ArenaContext arena = createArenaAdapter(instanceLevel, handle);
        updateInstanceArenaMetadata(instance, template, handle, origin);

        com.devmod.network.NetworkHandler.sendInstanceLoadingHide(player);

        // Ensure player is positioned at template-defined spawn
        if (teleportPlayersToArena(java.util.List.of(player), arena, handle).isEmpty()) {
            failPendingInstanceSetup(player, pendingSession, instanceId, "No valid player spawn slots in template");
            return;
        }

        updateSnapshotArenaTemplate(player, handle);

        quest.start(arena.getId());

        UUID effectivePlayerId = pendingSession != null ? pendingSession.getPlayerId() : player.getUUID();
        ActiveQuestSession session = new ActiveQuestSession(
            effectivePlayerId, quest, arena, System.currentTimeMillis(),
            settings.partyId, settings.questType, settings.getPlayerCount()
        );
        session.setInstanceId(instanceId);
        session.setArenaHandle(handle);
        if (pendingSession != null) {
            session.setDifficultyLabel(pendingSession.getDifficultyLabel());
            session.setQuestTypeLabel(pendingSession.getQuestTypeLabel());
            session.setKitId(pendingSession.getKitId());
        } else {
            session.setDifficultyLabel(resolveDifficultyLabel(settings, quest.getMobConfig()));
            session.setQuestTypeLabel(resolveQuestTypeLabel(settings, quest.getMobConfig()));
            session.setKitId(settings.kitId);
        }
        activeSessions.put(effectivePlayerId, session);

        // Apply arena policy config overrides and sync to client
        applyAndSyncArenaOverrides(player, session);

        EndurancePlayerStateManager.INSTANCE.preparePlayerForQuest(player, session);

        EnduranceEventHandler.onQuestStart(player, session);

        String dungeonId = "endurance_instance_" + mobId.toString().replace(":", "_");
        TelemetryService.INSTANCE.startDungeonSession(player, dungeonId);

        session.scheduleSafeWindow(SAFE_WINDOW_TICKS);
        session.scheduleWaveStart(WAVE_START_COUNTDOWN_TICKS);
        sendSoloSequenceUpdate(player, session, QuestSequencePayload.Phase.SAFE_WINDOW,
            (int) Math.ceil(SAFE_WINDOW_TICKS / 20.0),
            quest.getDisplayName(),
            "Safe window",
            List.of("Invulnerability active"));

        LOGGER.info("[EnduranceQuest] Player {} started TEMPLATE quest: {} (instance: {})",
            player.getName().getString(), quest.getDisplayName(), instanceId);

        player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("[DevMod] Quest started in instance dimension!")
            .withStyle(ChatFormatting.GREEN)));
    }

    // ========== Session Management (Delegated) ==========

    /**
     * Get active quest session for a player.
     */
    public Optional<ActiveQuestSession> getActiveSession(UUID playerId) {
        return Optional.ofNullable(activeSessions.get(playerId));
    }

    /**
     * Get active quest session for a player.
     */
    public Optional<ActiveQuestSession> getActiveSession(Player player) {
        return getActiveSession(player.getUUID());
    }

    public boolean isGamificationEnabled() {
        return arenaTemplateConfig != null && arenaTemplateConfig.gamificationEnabled();
    }

    public @javax.annotation.Nullable ArenaPolicy getPolicyForSession(@javax.annotation.Nullable ActiveQuestSession session) {
        if (session == null || arenaPolicyRegistry == null) {
            return null;
        }
        String policyId = session.getPolicyId();
        if (policyId == null || policyId.isBlank()) {
            return null;
        }
        return arenaPolicyRegistry.get(policyId).orElse(null);
    }

    /**
     * Apply arena policy config overrides and sync to client.
     * Called when a quest starts to apply per-arena config adjustments.
     */
    private void applyAndSyncArenaOverrides(ServerPlayer player, ActiveQuestSession session) {
        try {
            ArenaPolicy policy = getPolicyForSession(session);
            if (policy != null && policy.gameplayOverrides() != null) {
                UUID questId = session.getQuest().getQuestId();

                // Apply override to server-side config manager
                EnduranceConfigManager.INSTANCE.setArenaOverride(questId, policy.gameplayOverrides());

                // Sync override to client
                net.minecraft.nbt.CompoundTag overrideTag = serializeGameplayOverrides(policy.gameplayOverrides());
                GameMechanicsSyncPayload payload = GameMechanicsSyncPayload.forQuest(questId, overrideTag);
                PacketDistributor.sendToPlayer(player, payload);

                LOGGER.debug("[EnduranceQuest] Applied arena config overrides for quest {} to player {}",
                    questId, player.getName().getString());
            }
        } catch (Exception e) {
            LOGGER.warn("[EnduranceQuest] Failed to apply arena config overrides: {}", e.getMessage());
        }
    }

    /**
     * Serialize GameplayOverrides to CompoundTag for network sync.
     */
    private net.minecraft.nbt.CompoundTag serializeGameplayOverrides(ArenaPolicy.GameplayOverrides overrides) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();

        // Serialize each section if present
        if (overrides.combo() != null) {
            net.minecraft.nbt.CompoundTag combo = new net.minecraft.nbt.CompoundTag();
            if (overrides.combo().timeoutTicks() != null) combo.putInt("timeoutTicks", overrides.combo().timeoutTicks());
            if (overrides.combo().basePoints() != null) combo.putInt("basePoints", overrides.combo().basePoints());
            if (overrides.combo().multiplierIncrement() != null) combo.putDouble("multiplierIncrement", overrides.combo().multiplierIncrement());
            if (overrides.combo().maxMultiplier() != null) combo.putDouble("maxMultiplier", overrides.combo().maxMultiplier());
            tag.put("combo", combo);
        }

        if (overrides.tension() != null) {
            net.minecraft.nbt.CompoundTag tension = new net.minecraft.nbt.CompoundTag();
            if (overrides.tension().baseWaveGain() != null) tension.putDouble("baseWaveGain", overrides.tension().baseWaveGain());
            if (overrides.tension().noHitBonus() != null) tension.putDouble("noHitBonus", overrides.tension().noHitBonus());
            if (overrides.tension().minThreshold() != null) tension.putDouble("minThreshold", overrides.tension().minThreshold());
            if (overrides.tension().maxThreshold() != null) tension.putDouble("maxThreshold", overrides.tension().maxThreshold());
            if (overrides.tension().minWavesBeforeBoss() != null) tension.putInt("minWavesBeforeBoss", overrides.tension().minWavesBeforeBoss());
            if (overrides.tension().maxWavesWithoutBoss() != null) tension.putInt("maxWavesWithoutBoss", overrides.tension().maxWavesWithoutBoss());
            tag.put("tension", tension);
        }

        if (overrides.waves() != null) {
            net.minecraft.nbt.CompoundTag waves = new net.minecraft.nbt.CompoundTag();
            if (overrides.waves().baseMobCount() != null) waves.putInt("baseMobCount", overrides.waves().baseMobCount());
            if (overrides.waves().mobScaling() != null) waves.putDouble("mobScaling", overrides.waves().mobScaling());
            if (overrides.waves().intermissionTicks() != null) waves.putInt("intermissionTicks", overrides.waves().intermissionTicks());
            if (overrides.waves().bossInterval() != null) waves.putInt("bossInterval", overrides.waves().bossInterval());
            tag.put("waves", waves);
        }

        if (overrides.perkRarity() != null) {
            net.minecraft.nbt.CompoundTag perkRarity = new net.minecraft.nbt.CompoundTag();
            if (overrides.perkRarity().commonWeight() != null) perkRarity.putInt("commonWeight", overrides.perkRarity().commonWeight());
            if (overrides.perkRarity().uncommonWeight() != null) perkRarity.putInt("uncommonWeight", overrides.perkRarity().uncommonWeight());
            if (overrides.perkRarity().rareWeight() != null) perkRarity.putInt("rareWeight", overrides.perkRarity().rareWeight());
            if (overrides.perkRarity().epicWeight() != null) perkRarity.putInt("epicWeight", overrides.perkRarity().epicWeight());
            if (overrides.perkRarity().legendaryWeight() != null) perkRarity.putInt("legendaryWeight", overrides.perkRarity().legendaryWeight());
            tag.put("perkRarity", perkRarity);
        }

        // Add more sections as needed...

        return tag;
    }

    /**
     * Abandon current quest.
     */
    public void abandonQuest(ServerPlayer player) {
        sessionHandler.abandonQuest(player);
    }

    /**
     * Handle player death during quest.
     */
    public void handlePlayerDeath(ServerPlayer player) {
        sessionHandler.handlePlayerDeath(player);
    }

    /**
     * Handle player choosing to continue after death (with penalty) or give up.
     */
    public void handleRespawnChoice(ServerPlayer player, boolean continueQuest) {
        sessionHandler.handleRespawnChoice(player, continueQuest);
    }

    /**
     * Handle vanilla respawn after death (player clicked the vanilla respawn button).
     */
    public void handleVanillaRespawn(ServerPlayer player) {
        sessionHandler.handleVanillaRespawn(player);
    }

    /**
     * Complete current wave.
     */
    public void completeWave(ServerPlayer player) {
        sessionHandler.completeWave(player);
    }

    /**
     * Continue to next wave after checkpoint.
     */
    public void continueToNextWave(ServerPlayer player) {
        sessionHandler.continueToNextWave(player);
    }

    /**
     * Exit at checkpoint (between waves).
     */
    public void exitAtCheckpoint(ServerPlayer player) {
        sessionHandler.exitAtCheckpoint(player);
    }

    // ========== Combat Events ==========

    /**
     * Record a mob kill by the player.
     */
    public void recordKill(ServerPlayer player, ResourceLocation killedMobId) {
        ActiveQuestSession session = activeSessions.get(player.getUUID());
        if (session != null && session.quest.getMobId().equals(killedMobId)) {
            session.quest.recordKill();
            session.incrementKillCount();
        }
    }

    /**
     * Record damage dealt by player.
     */
    public void recordDamageDealt(ServerPlayer player, float damage) {
        ActiveQuestSession session = activeSessions.get(player.getUUID());
        if (session != null) {
            session.quest.recordDamageDealt(damage);
        }
    }

    /**
     * Record damage taken by player.
     */
    public void recordDamageTaken(ServerPlayer player, float damage) {
        ActiveQuestSession session = activeSessions.get(player.getUUID());
        if (session != null) {
            session.quest.recordDamageTaken(damage);
        }
    }

    // ========== Query Methods ==========

    /**
     * Get all available quest types.
     */
    public Collection<EnduranceQuest> getAllQuestTemplates() {
        return Collections.unmodifiableCollection(questTemplates.values());
    }

    /**
     * Get quest template by mob ID.
     */
    public Optional<EnduranceQuest> getQuestTemplate(ResourceLocation mobId) {
        return Optional.ofNullable(questTemplates.get(mobId));
    }

    /**
     * Get player statistics.
     */
    public PlayerQuestStats getPlayerStats(UUID playerId) {
        return persistence.getPlayerStats(playerId);
    }

    /**
     * Check if player is in a quest.
     */
    public boolean isPlayerInQuest(UUID playerId) {
        return activeSessions.containsKey(playerId);
    }

    /**
     * Get all active sessions (for sync purposes).
     */
    public Map<UUID, ActiveQuestSession> getActiveSessions() {
        return Collections.unmodifiableMap(activeSessions);
    }

    // ========== Data Reset ==========

    /**
     * Clear ALL player stats and quest data. Used for full player reset.
     * This deletes all endurance quest records, stats, and progress.
     */
    public void clearAllPlayerStats() {
        LOGGER.info("[EnduranceQuest] Clearing all player stats and quest data...");

        // Clear in-memory stats and templates
        persistence.clearAllStats();
        questTemplates.clear();

        // Delete player stats files
        persistence.deleteAllStatsFiles();

        LOGGER.info("[EnduranceQuest] All player stats cleared successfully");

        // Reset RewardSystem
        try {
            RewardSystem.INSTANCE.resetAll();
        } catch (Exception e) {
            LOGGER.warn("[EnduranceQuest] Could not reset RewardSystem: {}", e.getMessage());
        }

        // Reset GamificationManager
        try {
            GamificationManager.INSTANCE.resetAll();
        } catch (Exception e) {
            LOGGER.warn("[EnduranceQuest] Could not reset GamificationManager: {}", e.getMessage());
        }

        // Reinitialize quest templates
        for (EnduranceQuestRegistry.MobQuestConfig mobConfig : EnduranceQuestRegistry.INSTANCE.getAllMobConfigs()) {
            EnduranceQuest template = new EnduranceQuest(mobConfig);
            questTemplates.put(mobConfig.mobId, template);
        }
    }

    /**
     * Cleanup path used only during server shutdown to avoid dangling state
     * and to ensure telemetry/stats are flushed without granting full rewards.
     */
    private void handleForcedShutdownCleanup(ActiveQuestSession session) {
        try {
            EnduranceQuest quest = session.quest;
            UUID questId = quest.getQuestId();
            UUID playerId = session.getPlayerId();

            // Cleanup config overrides for this quest
            EnduranceConfigManager.INSTANCE.cleanupQuest(questId);

            // Record end-of-session telemetry and stats (abandoned outcome)
            EnduranceTelemetryService.INSTANCE.recordQuestEnd(
                questId,
                EnduranceQuestState.FAILED,
                quest.getCurrentWave(),
                quest.getSessionDuration(),
                quest.getMobsKilledThisSession(),
                quest.getTotalDamageDealtThisSession(),
                quest.getDamageTakenThisSession()
            );
            persistence.updatePlayerStats(playerId, quest, false);

            // Stop trackers and sessions to avoid leaks
            CombatTracker.INSTANCE.stopTracking(questId);
            ComboSystem.INSTANCE.endSession(playerId);
            EnduranceEventCombat.removeComboSession(playerId);
            MutatorSystem.INSTANCE.endSession(questId);
            EnduranceEventCombat.removeMutatorSession(questId);
            PerkSystem.INSTANCE.endSession(playerId);

            // Cleanup arena/boss state (handles both legacy and instance modes)
            EndurancePlayerStateManager.INSTANCE.cleanupQuestSystems(session);
            EndurancePlayerStateManager.INSTANCE.cleanupArenaOrInstance(session, false);
        } catch (Exception e) {
            LOGGER.warn("[EnduranceQuest] Failed shutdown cleanup for session {}", session.getPlayerId(), e);
        }
    }

    // ========== Inner Classes ==========

    /**
     * Settings for starting a quest.
     */
    public static class QuestSettings {
        public int totalWaves = 10;
        public boolean endlessMode = false;
        public int arenaSize = 64; // blocks (4 chunks)

        // Party/Multiplayer settings
        public QuestType questType = QuestType.PVE_COOP;
        public UUID partyId = null;
        public java.util.List<UUID> partyMemberIds = java.util.List.of();

        // Kit selection
        public String kitId = "STARTER";

        public QuestSettings() {}

        public QuestSettings waves(int waves) {
            this.totalWaves = waves;
            return this;
        }

        public QuestSettings endless() {
            this.endlessMode = true;
            return this;
        }

        public QuestSettings arenaSize(int size) {
            this.arenaSize = size;
            return this;
        }

        public QuestSettings questType(QuestType type) {
            this.questType = type;
            // Auto-adjust arena size based on quest type
            this.arenaSize = type.defaultArenaSize;
            return this;
        }

        public QuestSettings party(UUID partyId, java.util.List<UUID> memberIds) {
            this.partyId = partyId;
            this.partyMemberIds = memberIds;
            return this;
        }

        public boolean isMultiplayer() {
            return partyId != null && !partyMemberIds.isEmpty();
        }

        public int getPlayerCount() {
            return Math.max(1, partyMemberIds.size());
        }
    }

    /**
     * Result of starting a quest.
     */
    public record StartQuestResult(boolean success, String message, ActiveQuestSession session) {}

    /**
     * Active quest session for a player.
     */
    public static class ActiveQuestSession {
        private final UUID playerId;
        final EnduranceQuest quest;
        final ArenaContext arena;
        private final long startTime;
        private int killsInCurrentWave = 0;
        private boolean awaitingRespawnChoice = false;
        private boolean respawnRequested = false;

        // Instance dimension ID (null if using legacy overworld arena)
        private UUID instanceId;

        // Template system handle (optional, set when using ArenaTemplate)
        private @javax.annotation.Nullable ArenaHandle arenaHandle;
        private @javax.annotation.Nullable String templateId;
        private @javax.annotation.Nullable Integer templateVersion;
        private @javax.annotation.Nullable String policyId;
        private @javax.annotation.Nullable Integer policyVersion;

        // Pending flag - true while instance is being created asynchronously
        private boolean pending = false;

        // Instance start countdown (solo pre-teleport)
        private int pendingInstanceStartTicks = 0;
        private int lastTeleportCountdownSeconds = -1;
        private @javax.annotation.Nullable ResourceLocation pendingMobId;
        private @javax.annotation.Nullable QuestSettings pendingSettings;
        private @javax.annotation.Nullable ResolvedArena pendingResolved;

        // Briefing countdown (solo pre-teleport lobby)
        private int pendingBriefingTicks = 0;
        private int lastBriefingSeconds = -1;
        private List<String> briefingLines = List.of();

        // Wave start countdown (solo start / respawn delay)
        private int pendingWaveStartTicks = 0;
        private int lastWaveCountdownSeconds = -1;

        // Safe window countdown (post-teleport / post-respawn)
        private int pendingSafeWindowTicks = 0;
        private int lastSafeWindowSeconds = -1;

        // Boss intro countdown (short cinematic pause)
        private int pendingBossIntroTicks = 0;
        private int lastBossIntroSeconds = -1;
        private boolean respawnCountdownActive = false;

        // Wave directive choices (risk/reward between waves)
        private List<WaveDirective> pendingDirectives = List.of();
        private @javax.annotation.Nullable String selectedDirectiveId;
        private int directiveWaveNumber = -1;

        // Saved player state (to restore after quest)
        private GameType originalGameMode;
        private ListTag savedInventory;
        private ListTag savedArmor;
        private ListTag savedOffhand;

        // Party/Multiplayer scaling fields
        private UUID partyId;
        private QuestType questType = QuestType.PVE_COOP;
        private int playerCount = 1;
        private String difficultyLabel;
        private String questTypeLabel;

        // Kit selection for this quest
        private String kitId = "STARTER";

        public ActiveQuestSession(UUID playerId, EnduranceQuest quest, ArenaContext arena, long startTime) {
            this.playerId = playerId;
            this.quest = quest;
            this.arena = arena;
            this.startTime = startTime;
        }

        /**
         * Constructor with party/multiplayer parameters.
         */
        public ActiveQuestSession(UUID playerId, EnduranceQuest quest, ArenaContext arena,
                                  long startTime, UUID partyId, QuestType questType, int playerCount) {
            this.playerId = playerId;
            this.quest = quest;
            this.arena = arena;
            this.startTime = startTime;
            this.partyId = partyId;
            this.questType = questType;
            this.playerCount = Math.max(1, playerCount);
        }

        public UUID getPlayerId() { return playerId; }
        public EnduranceQuest getQuest() { return quest; }
        public ArenaContext getArena() { return arena; }
        public long getStartTime() { return startTime; }
        public int getKillsInCurrentWave() { return killsInCurrentWave; }
        public boolean isAwaitingRespawnChoice() { return awaitingRespawnChoice; }
        public boolean isRespawnRequested() { return respawnRequested; }

        public void setAwaitingRespawnChoice(boolean awaiting) {
            this.awaitingRespawnChoice = awaiting;
        }
        public void setRespawnRequested(boolean respawnRequested) { this.respawnRequested = respawnRequested; }

        public void incrementKillCount() {
            killsInCurrentWave++;
        }

        public void resetWaveKills() {
            killsInCurrentWave = 0;
        }

        public boolean isWaveComplete() {
            return killsInCurrentWave >= quest.getCurrentWaveMobCount();
        }

        // Saved state getters/setters
        public GameType getOriginalGameMode() { return originalGameMode; }
        public void setOriginalGameMode(GameType mode) { this.originalGameMode = mode; }
        public ListTag getSavedInventory() { return savedInventory; }
        public void setSavedInventory(ListTag inv) { this.savedInventory = inv; }
        public ListTag getSavedArmor() { return savedArmor; }
        public void setSavedArmor(ListTag armor) { this.savedArmor = armor; }
        public ListTag getSavedOffhand() { return savedOffhand; }
        public void setSavedOffhand(ListTag offhand) { this.savedOffhand = offhand; }

        // Instance dimension getters/setters
        public UUID getInstanceId() { return instanceId; }
        public void setInstanceId(UUID instanceId) { this.instanceId = instanceId; }
        public boolean isInInstanceDimension() { return instanceId != null; }

        public @javax.annotation.Nullable ArenaHandle getArenaHandle() { return arenaHandle; }
        public void setArenaHandle(@javax.annotation.Nullable ArenaHandle arenaHandle) {
            this.arenaHandle = arenaHandle;
            if (arenaHandle != null) {
                this.templateId = arenaHandle.templateId();
                this.templateVersion = arenaHandle.templateVersion();
                this.policyId = arenaHandle.policyId();
                this.policyVersion = arenaHandle.policyVersion();
            }
        }
        public @javax.annotation.Nullable String getTemplateId() { return templateId; }
        public @javax.annotation.Nullable Integer getTemplateVersion() { return templateVersion; }
        public @javax.annotation.Nullable String getPolicyId() { return policyId; }
        public @javax.annotation.Nullable Integer getPolicyVersion() { return policyVersion; }

        // Pending state (while instance is being created)
        public boolean isPending() { return pending; }
        public void setPending(boolean pending) { this.pending = pending; }

        public void scheduleInstanceStart(ResourceLocation mobId, QuestSettings settings, ResolvedArena resolved, int ticks) {
            this.pendingMobId = mobId;
            this.pendingSettings = settings;
            this.pendingResolved = resolved;
            this.pendingInstanceStartTicks = Math.max(0, ticks);
            this.lastTeleportCountdownSeconds = -1;
        }

        public void scheduleBriefing(int ticks) {
            this.pendingBriefingTicks = Math.max(0, ticks);
            this.lastBriefingSeconds = -1;
        }

        public boolean isBriefingPending() {
            return pendingBriefingTicks > 0;
        }

        public int tickBriefingCountdown() {
            if (pendingBriefingTicks > 0) {
                pendingBriefingTicks--;
            }
            return pendingBriefingTicks;
        }

        public int getLastBriefingSeconds() { return lastBriefingSeconds; }

        public void setLastBriefingSeconds(int seconds) { this.lastBriefingSeconds = seconds; }

        public List<String> getBriefingLines() { return briefingLines; }

        public void setBriefingLines(List<String> briefingLines) {
            this.briefingLines = briefingLines != null ? List.copyOf(briefingLines) : List.of();
        }

        public boolean isInstanceStartPending() {
            return pendingInstanceStartTicks > 0 && pendingMobId != null && pendingSettings != null && pendingResolved != null;
        }

        public int tickInstanceStartCountdown() {
            if (pendingInstanceStartTicks > 0) {
                pendingInstanceStartTicks--;
            }
            return pendingInstanceStartTicks;
        }

        public int getLastTeleportCountdownSeconds() { return lastTeleportCountdownSeconds; }
        public void setLastTeleportCountdownSeconds(int seconds) { this.lastTeleportCountdownSeconds = seconds; }

        public @javax.annotation.Nullable ResourceLocation getPendingMobId() { return pendingMobId; }
        public @javax.annotation.Nullable QuestSettings getPendingSettings() { return pendingSettings; }
        public @javax.annotation.Nullable ResolvedArena getPendingResolved() { return pendingResolved; }

        public void clearPendingInstanceStart() {
            pendingInstanceStartTicks = 0;
            lastTeleportCountdownSeconds = -1;
            pendingMobId = null;
            pendingSettings = null;
            pendingResolved = null;
        }

        public void scheduleWaveStart(int ticks) {
            if (ticks <= 0) {
                pendingWaveStartTicks = 0;
                lastWaveCountdownSeconds = -1;
                return;
            }
            pendingWaveStartTicks = ticks;
            lastWaveCountdownSeconds = -1;
        }

        public boolean isWaveStartPending() { return pendingWaveStartTicks > 0; }

        public int tickWaveStartCountdown() {
            if (pendingWaveStartTicks > 0) {
                pendingWaveStartTicks--;
            }
            return pendingWaveStartTicks;
        }

        public int getLastWaveCountdownSeconds() { return lastWaveCountdownSeconds; }

        public void setLastWaveCountdownSeconds(int seconds) { this.lastWaveCountdownSeconds = seconds; }

        public void clearPendingWaveStart() {
            pendingWaveStartTicks = 0;
            lastWaveCountdownSeconds = -1;
        }

        public void scheduleSafeWindow(int ticks) {
            if (ticks <= 0) {
                pendingSafeWindowTicks = 0;
                lastSafeWindowSeconds = -1;
                return;
            }
            pendingSafeWindowTicks = ticks;
            lastSafeWindowSeconds = -1;
        }

        public boolean isSafeWindowPending() { return pendingSafeWindowTicks > 0; }

        public int tickSafeWindowCountdown() {
            if (pendingSafeWindowTicks > 0) {
                pendingSafeWindowTicks--;
            }
            return pendingSafeWindowTicks;
        }

        public int getLastSafeWindowSeconds() { return lastSafeWindowSeconds; }

        public void setLastSafeWindowSeconds(int seconds) { this.lastSafeWindowSeconds = seconds; }

        public void clearPendingSafeWindow() {
            pendingSafeWindowTicks = 0;
            lastSafeWindowSeconds = -1;
        }

        public void scheduleBossIntro(int ticks) {
            if (ticks <= 0) {
                pendingBossIntroTicks = 0;
                lastBossIntroSeconds = -1;
                return;
            }
            pendingBossIntroTicks = ticks;
            lastBossIntroSeconds = -1;
        }

        public boolean isBossIntroPending() { return pendingBossIntroTicks > 0; }

        public int tickBossIntroCountdown() {
            if (pendingBossIntroTicks > 0) {
                pendingBossIntroTicks--;
            }
            return pendingBossIntroTicks;
        }

        public int getLastBossIntroSeconds() { return lastBossIntroSeconds; }

        public void setLastBossIntroSeconds(int seconds) { this.lastBossIntroSeconds = seconds; }

        public void clearPendingBossIntro() {
            pendingBossIntroTicks = 0;
            lastBossIntroSeconds = -1;
        }

        public boolean isRespawnCountdownActive() { return respawnCountdownActive; }

        public void setRespawnCountdownActive(boolean active) { this.respawnCountdownActive = active; }

        public void clearAllSequences() {
            clearPendingWaveStart();
            clearPendingSafeWindow();
            clearPendingBossIntro();
            clearPendingInstanceStart();
            pendingBriefingTicks = 0;
            lastBriefingSeconds = -1;
            briefingLines = List.of();
            respawnCountdownActive = false;
            clearDirectives();
        }

        // Party/Multiplayer getters/setters
        public UUID getPartyId() { return partyId; }
        public void setPartyId(UUID partyId) { this.partyId = partyId; }
        public QuestType getQuestType() { return questType; }
        public void setQuestType(QuestType questType) { this.questType = questType; }
        public int getPlayerCount() { return playerCount; }
        public void setPlayerCount(int playerCount) { this.playerCount = Math.max(1, playerCount); }
        public boolean isMultiplayer() { return partyId != null && playerCount > 1; }

        public String getDifficultyLabel() { return difficultyLabel; }

        public void setDifficultyLabel(String difficultyLabel) { this.difficultyLabel = difficultyLabel; }

        public String getQuestTypeLabel() { return questTypeLabel; }

        public void setQuestTypeLabel(String questTypeLabel) { this.questTypeLabel = questTypeLabel; }

        public String getKitId() { return kitId; }

        public void setKitId(String kitId) { this.kitId = kitId != null ? kitId : "STARTER"; }

        public void setPendingDirectives(List<WaveDirective> directives, int waveNumber) {
            this.pendingDirectives = directives != null ? List.copyOf(directives) : List.of();
            this.directiveWaveNumber = waveNumber;
            this.selectedDirectiveId = null;
        }

        public List<WaveDirective> getPendingDirectives() {
            return pendingDirectives;
        }

        public boolean hasPendingDirectives() {
            return pendingDirectives != null && !pendingDirectives.isEmpty();
        }

        public int getDirectiveWaveNumber() {
            return directiveWaveNumber;
        }

        public void selectDirective(@javax.annotation.Nullable String directiveId) {
            if (directiveId == null || pendingDirectives == null) {
                return;
            }
            for (WaveDirective directive : pendingDirectives) {
                if (directive != null && directive.id().equals(directiveId)) {
                    this.selectedDirectiveId = directiveId;
                    return;
                }
            }
        }

        public @javax.annotation.Nullable WaveDirective consumeDirectiveForWave(int waveNumber) {
            if (waveNumber != directiveWaveNumber) {
                return null;
            }
            WaveDirective selected = null;
            if (selectedDirectiveId != null) {
                for (WaveDirective directive : pendingDirectives) {
                    if (directive != null && directive.id().equals(selectedDirectiveId)) {
                        selected = directive;
                        break;
                    }
                }
            } else {
                for (WaveDirective directive : pendingDirectives) {
                    if (directive != null && "steady".equals(directive.id())) {
                        selected = directive;
                        break;
                    }
                }
            }
            clearDirectives();
            return selected;
        }

        public void clearDirectives() {
            pendingDirectives = List.of();
            selectedDirectiveId = null;
            directiveWaveNumber = -1;
        }
    }

    /**
     * Persistent player statistics.
     */
    public static class PlayerQuestStats {
        private final UUID playerId;
        private int totalQuestsAttempted = 0;
        private int totalQuestsCompleted = 0;
        private int totalPointsEarned = 0;
        private int totalMobsKilled = 0;
        private long totalPlayTime = 0; // milliseconds
        private final Map<String, MobQuestRecord> mobRecords = new HashMap<>();

        public PlayerQuestStats(UUID playerId) {
            this.playerId = playerId;
        }

        public void recordQuestAttempt(ResourceLocation mobId, boolean completed, int points, int wavesReached, long duration, int mobsKilled) {
            totalQuestsAttempted++;
            if (completed) totalQuestsCompleted++;
            totalPointsEarned += points;
            totalMobsKilled += mobsKilled;
            totalPlayTime += duration;

            MobQuestRecord record = mobRecords.computeIfAbsent(mobId.toString(), k -> new MobQuestRecord());
            record.attempts++;
            if (completed) record.completions++;
            if (points > record.bestScore) record.bestScore = points;
            if (wavesReached > record.highestWave) record.highestWave = wavesReached;
        }

        public UUID getPlayerId() { return playerId; }
        public int getTotalQuestsAttempted() { return totalQuestsAttempted; }
        public int getTotalMobsKilled() { return totalMobsKilled; }
        public int getTotalQuestsCompleted() { return totalQuestsCompleted; }
        public int getTotalPointsEarned() { return totalPointsEarned; }
        public long getTotalPlayTime() { return totalPlayTime; }
        public Map<String, MobQuestRecord> getMobRecords() { return mobRecords; }

        public float getCompletionRate() {
            return totalQuestsAttempted > 0 ? (float) totalQuestsCompleted / totalQuestsAttempted : 0;
        }
    }

    /**
     * Record for a specific mob's quest attempts.
     */
    public static class MobQuestRecord {
        public int attempts = 0;
        public int completions = 0;
        public int bestScore = 0;
        public int highestWave = 0;
    }
}
