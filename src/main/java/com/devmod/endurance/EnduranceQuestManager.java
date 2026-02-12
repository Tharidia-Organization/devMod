package com.devmod.endurance;
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
import java.util.Locale;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.DevMod;
import com.devmod.debug.DiagnosticLogger;
import com.devmod.arena.api.ArenaHandle;
import com.devmod.arena.builder.ArenaBuilder;
import com.devmod.arena.builder.AsyncArenaBuildCoordinator;
import com.devmod.arena.builder.AsyncArenaBuilder;
import com.devmod.arena.builder.ChunkLoadingManager;
import com.devmod.arena.config.ArenaTemplateConfig;
import com.devmod.arena.config.InstanceLimitConfig;
import com.devmod.arena.error.UserFriendlyError;
import com.devmod.arena.fallback.CircuitBreaker;
import com.devmod.arena.fallback.FallbackMetrics;
import com.devmod.arena.integration.MinecraftBlockPlacer;
import com.devmod.arena.integration.MinecraftEntitySpawner;
import com.devmod.arena.override.ForceTemplateCapability;
import com.devmod.arena.override.OverrideManager;
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
import com.devmod.endurance.EnduranceLogger.Phase;
import com.devmod.endurance.combat.ComboSystemFacade;
import com.devmod.endurance.config.EnduranceConfigManager;
import com.devmod.endurance.services.InstanceServicesFacade;
import com.devmod.endurance.services.PlayerStateServicesFacade;
import com.devmod.mob.EnhancedMobRequirements;
import com.devmod.mob.EnhancedMobRequirementsRegistry;
import com.devmod.mob.MobRequirements;
import com.devmod.mob.MobRequirementsRegistry;
import com.devmod.network.GameMechanicsSyncPayload;
import com.devmod.party.QuestSequencePayload;
import com.devmod.runtime.InstanceData;
import com.devmod.runtime.InstanceManager;
import com.devmod.shared.SharedColorTokens;
import com.devmod.telemetry.TelemetryService;
import com.devmod.telemetry.endurance.EnduranceTelemetryService;
import com.devmod.util.I18n;
public class EnduranceQuestManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnduranceQuestManager.class);

    public static final EnduranceQuestManager INSTANCE = new EnduranceQuestManager();

    private static <T> T requireNonNull(T value, String name) {
        return Objects.requireNonNull(value, name);
    }

    /** Tokens awarded per wave on server shutdown (~50% of normal wave rewards). */
    private static final int SHUTDOWN_PARTIAL_TOKENS_PER_WAVE = 15;

    // Active quests per player (player UUID -> active quest)
    private final Map<UUID, ActiveQuestSession> activeSessions = new ConcurrentHashMap<>();
    // Active party quests (party UUID -> party session)
    private final Map<UUID, PartyQuestSession> partySessions = new ConcurrentHashMap<>();
    // Quest UUID -> party UUID lookup for shared party runs
    private final Map<UUID, UUID> questToParty = new ConcurrentHashMap<>();
    // Lock for atomic party session registration/removal (updates both partySessions and questToParty)
    private final Object partySessionLock = new Object();

    // Quest templates (mob ID -> quest template with best records)
    private final Map<ResourceLocation, EnduranceQuest> questTemplates = new ConcurrentHashMap<>();

    // Delegate classes
    private final EnduranceQuestPersistence persistence = new EnduranceQuestPersistence();
    private volatile EnduranceSessionHandler sessionHandler;


    // Data directory
    private Path dataDirectory;

    // Volatile for thread visibility across initialization and query threads
    private volatile boolean initialized = false;

    // Instance dimension mode flag - when true, quests run in isolated temporary dimensions
    // Volatile for thread visibility when config is hot-reloaded
    private volatile boolean useInstanceDimensions = true;

    // Arena template system integration (L1/L2)
    // Volatile for thread visibility during hot-reload and concurrent access
    private volatile ArenaTemplateRegistry arenaTemplateRegistry;
    private volatile ArenaPolicyRegistry arenaPolicyRegistry;
    private volatile PolicyResolver policyResolver;
    private volatile OverrideManager overrideManager;
    private volatile @javax.annotation.Nullable ForceTemplateCapability forceTemplateCapability;
    private volatile ArenaTelemetry arenaTelemetry;
    private volatile ArenaTemplateConfig arenaTemplateConfig;
    private volatile ArenaTemplateConfig.ConfigSnapshot arenaConfigSnapshot;
    private final PrebuildPoolManager prebuildPoolManager = new PrebuildPoolManager();
    private final AsyncArenaBuildCoordinator asyncBuildCoordinator =
        new AsyncArenaBuildCoordinator(() -> arenaConfigSnapshot);
    private static final long INSTANCE_CREATION_TIMEOUT_SECONDS = 30;
    public static final int PRE_TELEPORT_COUNTDOWN_TICKS = 200;
    public static final int WAVE_START_COUNTDOWN_TICKS = 200;
    public static final int BRIEFING_TICKS = 80;
    public static final int SAFE_WINDOW_TICKS = 60;
    public static final int BOSS_INTRO_TICKS = 20;
    private static final String I18N_PREFIX = "i18n:";
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
            questTemplates.put(mobConfig.getMobId(), template);
        }

        // Initialize persistence
        persistence.initialize(dataDirectory);
        KitManager.INSTANCE.initializeSyncPersistence(dataDirectory);

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

        java.util.Map<UUID, Integer> questIdCounts = new java.util.HashMap<>();
        for (ActiveQuestSession session : activeSessions.values()) {
            UUID questId = session.getQuest().getQuestId();
            questIdCounts.put(questId, questIdCounts.getOrDefault(questId, 0) + 1);
        }
        java.util.Set<UUID> sharedCleanup = new java.util.HashSet<>();
        // Force-end all active sessions with partial rewards
        for (ActiveQuestSession session : activeSessions.values()) {
            try {
                EnduranceQuest quest = session.quest;
                UUID playerId = session.getPlayerId();
                int questRefs = questIdCounts.getOrDefault(quest.getQuestId(), 1);
                boolean cleanupShared = questRefs <= 1 || sharedCleanup.add(quest.getQuestId());

                // Award partial tokens based on waves completed (50% of normal rate)
                int wavesCompleted = quest.getCurrentWave();
                int partialTokens = wavesCompleted * SHUTDOWN_PARTIAL_TOKENS_PER_WAVE;

                if (partialTokens > 0) {
                    RewardSystem.PlayerWallet wallet = RewardSystem.INSTANCE.getWallet(playerId);
                    wallet.addCurrency(RewardSystem.Currency.TOKENS, partialTokens);
                    LOGGER.info("[EnduranceQuest] Awarded {} partial tokens to player {} (completed {} waves before shutdown)",
                        partialTokens, playerId, wavesCompleted);
                }

                try {
                    quest.fail(true); // Mark as abandoned
                } finally {
                    // Flush telemetry/stats and cleanup systems without granting full rewards
                    handleForcedShutdownCleanup(session, cleanupShared);
                }
            } catch (Exception e) {
                LOGGER.error("[EnduranceQuest] Error cleaning up session for player {}", session.getPlayerId(), e);
            }
        }
        activeSessions.clear();
        partySessions.clear();
        questToParty.clear();

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

    /**
     * Gets the PolicyResolver for template suggestions.
     * Used by network handlers to delegate scoring logic.
     */
    @javax.annotation.Nullable
    public PolicyResolver getPolicyResolver() {
        return policyResolver;
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

    private ResolvedArena resolveArenaTemplate(UUID playerId, ResourceLocation mobId, QuestSettings settings,
                                               @javax.annotation.Nullable net.minecraft.server.MinecraftServer server) {
        if (policyResolver == null) {
            return null;
        }
        var mobConfig = EnduranceQuestRegistry.INSTANCE.getMobConfig(mobId).orElse(null);
        String questType = resolveQuestTypeLabel(settings, mobConfig);
        String difficulty = resolveDifficultyLabel(settings, mobConfig);
        Set<String> tags = resolveTags(settings, mobConfig);

        // Get mob requirements for arena selection (space, biome, light, etc.)
        MobRequirements mobRequirements = MobRequirementsRegistry.INSTANCE.get(mobId);

        ResolveContext.Builder ctxBuilder = ResolveContext.builder(playerId)
            .partyId(settings.partyId)
            .mobType(mobId.toString())
            .mobRequirements(mobRequirements)
            .questType(questType)
            .difficulty(difficulty)
            .playerCount(settings.getPlayerCount())
            .tags(tags)
            .server(server);

        // Priority 1: forceTemplateId from settings (explicit user selection from UI)
        String selectedTemplateId = null;
        if (settings.forceTemplateId != null && !settings.forceTemplateId.isEmpty()) {
            LOGGER.info("[EnduranceQuest] Using explicit template override from settings: {}",
                settings.forceTemplateId);
            selectedTemplateId = settings.forceTemplateId;
        } else {
            // Priority 2: ForceTemplateCapability (admin override)
            ForceTemplateCapability capability = forceTemplateCapability;
            if (capability != null) {
                var forced = capability.getForcedTemplate(playerId);
                if (forced.isPresent()) {
                    LOGGER.info("[EnduranceQuest] Force template override active for {}: {}",
                        playerId, forced.get());
                    selectedTemplateId = forced.get();
                }
            }
        }

        // Priority 3: Auto-select dynamic template for structure-spawn mobs
        // This ensures mobs like acolyte (crypt) get appropriate arena style
        if (selectedTemplateId == null && mobRequirements != null) {
            EnhancedMobRequirements enhanced = EnhancedMobRequirementsRegistry.INSTANCE
                .getWithServer(mobId, server);
            if (enhanced.spawnSource().shouldUseStructure(questType)) {
                String customTemplateId = "custom_" + mobId.getPath().replace(":", "_");
                LOGGER.info("[EnduranceQuest] Auto-selecting dynamic template '{}' for structure-spawn mob '{}'",
                    customTemplateId, mobId);
                selectedTemplateId = customTemplateId;
            }
        }

        if (selectedTemplateId != null) {
            ctxBuilder.forceTemplateId(selectedTemplateId);
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
        if (mobConfig != null && mobConfig.getTier() == EnduranceQuestRegistry.MobTier.BOSS) {
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
        if (mobConfig != null && mobConfig.getTier() == EnduranceQuestRegistry.MobTier.BOSS) {
            return "hard";
        }
        if (mobConfig != null && mobConfig.getTier() == EnduranceQuestRegistry.MobTier.ELITE) {
            return "hard";
        }
        return "normal";
    }

    private List<String> buildBriefingLines(EnduranceQuest quest,
                                            ActiveQuestSession session) {
        List<String> lines = new ArrayList<>();
        if (quest != null) {
            if (quest.isEndlessMode() || quest.getTotalWaves() <= 0) {
                lines.add(i18nToken("devmod.endurance.briefing.objective",
                    i18nToken("devmod.endurance.briefing.objective.endless")));
            } else {
                int waves = Math.max(1, quest.getTotalWaves());
                lines.add(i18nToken("devmod.endurance.briefing.objective",
                    i18nToken("devmod.endurance.briefing.objective.waves", waves)));
            }
        }

        String kitLabel = resolveKitLabel(session);
        if (!kitLabel.isBlank()) {
            lines.add(i18nToken("devmod.endurance.briefing.kit", kitLabel));
        }

        String difficultyKey = resolveDifficultyKey(session);
        if (difficultyKey != null) {
            lines.add(i18nToken("devmod.endurance.briefing.difficulty", i18nToken(difficultyKey)));
        }

        String modeKey = resolveModeKey(quest, session);
        if (modeKey != null) {
            lines.add(i18nToken("devmod.endurance.briefing.mode", i18nToken(modeKey)));
        }

        if (session == null || !session.isPracticeMode()) {
            lines.add(i18nToken("devmod.endurance.briefing.rewards"));
        }
        return lines;
    }

    private String resolveDifficultyKey(@javax.annotation.Nullable ActiveQuestSession session) {
        if (session == null) {
            return null;
        }
        String difficulty = session.getDifficultyLabel();
        if (difficulty == null || difficulty.isBlank()) {
            return null;
        }
        if ("hard".equalsIgnoreCase(difficulty)) {
            return "devmod.endurance.difficulty.hard";
        }
        return "devmod.endurance.difficulty.normal";
    }

    private String resolveModeKey(@javax.annotation.Nullable EnduranceQuest quest,
                                  @javax.annotation.Nullable ActiveQuestSession session) {
        if (session != null && session.isPracticeMode()) {
            return "devmod.endurance.briefing.mode.practice";
        }
        if (quest != null && quest.isEndlessMode()) {
            return "devmod.endurance.briefing.mode.endless";
        }
        return "devmod.endurance.briefing.mode.standard";
    }

    private String resolveKitLabel(@javax.annotation.Nullable ActiveQuestSession session) {
        if (session == null) {
            return "";
        }
        String kitId = session.getKitId();
        if (kitId == null || kitId.isBlank()) {
            return "";
        }

        if ("TEMPORARY".equals(kitId)) {
            String name = KitManager.INSTANCE.getTemporaryKitName(session.getPlayerId());
            if (name != null && !name.isBlank()) {
                return name;
            }
            return i18nToken("devmod.endurance.briefing.kit.temporary");
        }

        if (kitId.length() == 8) {
            Optional<CustomKit> syncedKit = KitManager.INSTANCE.getSyncedCustomKit(session.getPlayerId(), kitId);
            if (syncedKit.isPresent()) {
                return syncedKit.get().getName();
            }
            Optional<CustomKit> savedKit = KitManager.INSTANCE.getCustomKit(kitId);
            if (savedKit.isPresent()) {
                return savedKit.get().getName();
            }
            return i18nToken("devmod.endurance.briefing.kit.custom");
        }

        KitPreset preset = KitManager.INSTANCE.getKitById(kitId);
        if (preset != null) {
            String key = "devmod.endurance.briefing.kit.preset." + preset.name().toLowerCase(Locale.ROOT);
            return i18nToken(key);
        }

        return kitId;
    }

    private String i18nToken(String key, Object... args) {
        StringBuilder builder = new StringBuilder(I18N_PREFIX).append(key != null ? key : "");
        if (args != null) {
            for (Object arg : args) {
                builder.append('|').append(sanitizeTokenArg(arg));
            }
        }
        return builder.toString();
    }

    private String sanitizeTokenArg(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        return text.replace("|", "/");
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
        if (mobConfig != null && mobConfig.getTier() == EnduranceQuestRegistry.MobTier.BOSS) {
            tags.add("boss");
        }
        if (arenaTemplateConfig != null && arenaTemplateConfig.routingEnabled() && mobConfig != null
            && mobConfig.getEntityType() != null) {
            Class<?> baseClass = mobConfig.getEntityType().getBaseClass();
            if (baseClass != null && RangedAttackMob.class.isAssignableFrom(baseClass)) {
                tags.add("ranged");
            } else {
                tags.add("melee");
            }
        }
        if (settings != null) {
            int arenaSize = settings.arenaSize;
            if (arenaSize > 0) {
                if (arenaSize <= 48) {
                    tags.add("arena_small");
                } else if (arenaSize <= 96) {
                    tags.add("arena_medium");
                } else {
                    tags.add("arena_large");
                }
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
        ResolvedArena resolved = resolveArenaTemplate(leader.getUUID(), mobId, settings, leader.getServer());
        if (resolved == null) {
            return CompletableFuture.completedFuture(PreparedArenaResult.failure("No matching arena template/policy"));
        }

        ArenaTemplate template = resolved.template();
        List<UUID> partyMembers = settings.partyMemberIds != null && !settings.partyMemberIds.isEmpty()
            ? new ArrayList<>(settings.partyMemberIds)
            : null;

        CompletableFuture<PreparedArenaResult> result = new CompletableFuture<>();
        var instanceFuture = InstanceManager.INSTANCE
            .prepareInstanceQuest(leader, template.id(), mobId.toString(), partyMembers)
            .orTimeout(INSTANCE_CREATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        instanceFuture = instanceFuture.whenComplete((instanceId, throwable) -> {
            var server = leader.getServer();
            if (server == null) {
                if (instanceId != null) {
                    InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId);
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

                    Optional<InstanceData> instanceOpt = InstanceServicesFacade.INSTANCE.getInstance(instanceId);
                    if (instanceOpt.isEmpty()) {
                        InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId);
                        result.complete(PreparedArenaResult.failure("Instance not found after creation"));
                        return;
                    }

                    InstanceData instance = instanceOpt.get();
                    var dimensionKey = instance.getDimensionKey();
                    if (dimensionKey == null) {
                        InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId);
                        result.complete(PreparedArenaResult.failure("Instance dimension not ready"));
                        return;
                    }

                    ServerLevel instanceLevel = server.getLevel(dimensionKey);
                    if (instanceLevel == null) {
                        InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId);
                        result.complete(PreparedArenaResult.failure("Instance level not found"));
                        return;
                    }

                    OriginResolution origin = resolveTemplateOrigin(template);
                    if (shouldBuildAsync(template)) {
                        AsyncArenaBuilder asyncBuilder = asyncBuildCoordinator.getOrCreate(instanceLevel);
                        UUID arenaId = UUID.randomUUID();
                        var buildFuture = asyncBuilder.submitBuildAsync(
                            arenaId,
                            template,
                            origin.centerX(),
                            origin.originY(),
                            origin.centerZ()
                        );
                        buildFuture = buildFuture.whenComplete((asyncResult, buildError) -> {
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
                                    InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId);
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
                        if (buildFuture.isCancelled()) {
                            LOGGER.debug("[EnduranceQuest] Async arena build cancelled for {}", arenaId);
                        }
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
                        InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId);
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
        if (instanceFuture.isCancelled()) {
            LOGGER.debug("[EnduranceQuest] Instance preparation cancelled for {}", mobId);
        }

        return result;
    }

    private PreparedArenaResult prepareTemplateArenaForParty(ServerPlayer leader,
                                                             ResourceLocation mobId,
                                                             QuestSettings settings,
                                                             EnduranceQuestRegistry.MobQuestConfig mobConfig) {
        ResolvedArena resolved = resolveArenaTemplate(leader.getUUID(), mobId, settings, leader.getServer());
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
                .prepareInstanceQuest(leader, template.id(), mobId.toString(), partyMembers)
                .get(INSTANCE_CREATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            LOGGER.error("[EnduranceQuest] Failed to create instance for party: {}", e.getMessage());
            return PreparedArenaResult.failure("Failed to create instance: " + e.getMessage());
        }

        if (instanceId == null) {
            return PreparedArenaResult.failure("Failed to create instance for party");
        }

        Optional<InstanceData> instanceOpt = InstanceServicesFacade.INSTANCE.getInstance(instanceId);
        if (instanceOpt.isEmpty()) {
            InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId);
            return PreparedArenaResult.failure("Instance not found after creation");
        }

        InstanceData instance = instanceOpt.get();
        var dimensionKey = instance.getDimensionKey();
        if (dimensionKey == null) {
            InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId);
            return PreparedArenaResult.failure("Instance dimension not ready");
        }

        var server = leader.getServer();
        if (server == null) {
            InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId);
            return PreparedArenaResult.failure("Server not available");
        }

        ServerLevel instanceLevel = server.getLevel(dimensionKey);
        if (instanceLevel == null) {
            InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId);
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
            InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId);
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
            InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId);
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
        return teleportPlayersToArena(players, arena, handle, false, shouldUpdateSnapshotState(arena, handle));
    }

    public Map<UUID, net.minecraft.core.BlockPos> teleportPlayersToArena(List<ServerPlayer> players,
                                                                        ArenaContext arena,
                                                                        @javax.annotation.Nullable ArenaHandle handle,
                                                                        boolean allowFallback) {
        return teleportPlayersToArena(players, arena, handle, allowFallback, shouldUpdateSnapshotState(arena, handle));
    }

    public Map<UUID, net.minecraft.core.BlockPos> teleportPlayersToArena(List<ServerPlayer> players,
                                                                        ArenaContext arena,
                                                                        @javax.annotation.Nullable ArenaHandle handle,
                                                                        boolean allowFallback,
                                                                        boolean updateSnapshotState) {
        Map<UUID, net.minecraft.core.BlockPos> spawnPositions = new HashMap<>();
        if (handle == null || handle.playerSpawnPositions() == null || handle.playerSpawnPositions().isEmpty()) {
            LOGGER.error("[EnduranceQuest] Missing player spawn slots; ArenaHandle required");
            return spawnPositions;
        }

        List<ArenaHandle.BlockPos> positions = handle.playerSpawnPositions();
        int playerCount = players.size();
        ServerLevel level = arena.getLevel();
        UUID instanceId = handle.instanceId();
        boolean isInstanceDimension = com.devmod.runtime.DynamicDimensionManager.INSTANCE.isInstanceDimension(level.dimension());
        boolean useTeleportTransaction = instanceId != null && isInstanceDimension;
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

            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> previousDimension =
                player.level().dimension();
            net.minecraft.core.BlockPos spawnPos = pickValidatedSpawnPosition(
                positions,
                i,
                occupied,
                runtimeValidator,
                slotMap,
                template,
                level
            );
            if (spawnPos == null && allowFallback) {
                spawnPos = pickFallbackSpawnPosition(positions, i, occupied);
                if (spawnPos != null) {
                    LOGGER.warn("[EnduranceQuest] Falling back to unvalidated player spawn at {}",
                        spawnPos);
                }
            }
            if (spawnPos == null) {
                LOGGER.warn("[EnduranceQuest] No valid template player spawn found");
                spawnPositions.clear();
                return spawnPositions;
            }
            if (useTeleportTransaction) {
                com.devmod.runtime.TeleportTransaction.TeleportResult result =
                    com.devmod.runtime.TeleportTransaction.executeToArena(
                        player,
                        instanceId,
                        level,
                        spawnPos,
                        updateSnapshotState
                    );
                if (result == com.devmod.runtime.TeleportTransaction.TeleportResult.SUCCESS) {
                    spawnPositions.put(player.getUUID(), spawnPos);
                } else {
                    LOGGER.warn("[EnduranceQuest] Teleport transaction failed for {} (instance={}, result={})",
                        player.getName().getString(), instanceId, result);
                    if (result == com.devmod.runtime.TeleportTransaction.TeleportResult.DIMENSION_NOT_FOUND
                        || result == com.devmod.runtime.TeleportTransaction.TeleportResult.INSTANCE_INVALID_STATE) {
                        spawnPositions.clear();
                        return spawnPositions;
                    }
                    continue;
                }
            } else {
                double x = spawnPos.getX() + 0.5;
                double y = spawnPos.getY();
                double z = spawnPos.getZ() + 0.5;

                // Use the full teleport method to ensure proper client sync.
                // Simple teleportTo(x,y,z) doesn't force network sync when client is stuck in "loading terrain".
                level.getChunkAt(spawnPos); // Ensure chunk is loaded on server
                player.teleportTo(
                    level,
                    x,
                    y,
                    z,
                    java.util.Objects.requireNonNull(java.util.Set.<net.minecraft.world.entity.RelativeMovement>of(), "teleport flags"),
                    player.getYRot(),
                    player.getXRot()
                );
                player.setDeltaMovement(0, 0, 0);
                player.fallDistance = 0;
                boolean dimensionChanged = !previousDimension.equals(level.dimension());
                if (dimensionChanged) {
                    player.connection.resetPosition();
                }
                if (com.devmod.runtime.DynamicDimensionManager.INSTANCE.isInstanceDimension(level.dimension())) {
                    com.devmod.runtime.environment.DimensionEnvironmentManager.INSTANCE.syncEnvironmentToPlayer(
                        player,
                        level.dimension()
                    );
                }
                spawnPositions.put(player.getUUID(), spawnPos);
            }

            LOGGER.debug("[EnduranceQuest] Teleported {} to handle spawn at ({}, {}, {})",
                player.getName().getString(), spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
        }

        LOGGER.info("[EnduranceQuest] Teleported {} players to arena {} using template spawns",
            spawnPositions.size(), arena.getId());
        return spawnPositions;
    }

    private boolean shouldUpdateSnapshotState(ArenaContext arena, @javax.annotation.Nullable ArenaHandle handle) {
        if (handle == null || handle.instanceId() == null) {
            return false;
        }
        if (arena == null || arena.getLevel() == null) {
            return false;
        }
        return com.devmod.runtime.DynamicDimensionManager.INSTANCE.isInstanceDimension(arena.getLevel().dimension());
    }

    public boolean teleportPlayerToArena(ServerPlayer player,
                                         ActiveQuestSession session,
                                         boolean allowFallback,
                                         boolean updateSnapshotState) {
        if (player == null || session == null) {
            return false;
        }
        ArenaContext arena = session.getArena();
        ArenaHandle handle = session.getArenaHandle();
        if (arena == null || handle == null) {
            LOGGER.warn("[EnduranceQuest] Teleport failed for {} - missing arena/handle (arena={}, handle={})",
                player.getName().getString(),
                arena != null ? arena.getId() : "null",
                handle != null ? handle.arenaId() : "null");
            return false;
        }
        boolean success = !teleportPlayersToArena(
            java.util.List.of(player),
            arena,
            handle,
            allowFallback,
            updateSnapshotState
        ).isEmpty();
        return success;
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

    private net.minecraft.core.BlockPos pickFallbackSpawnPosition(
            List<ArenaHandle.BlockPos> positions,
            int startIndex,
            com.devmod.arena.spawn.SpawnOccupancyTracker occupied) {
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
        return Objects.requireNonNullElse(size, template.size());
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
        InstanceServicesFacade.INSTANCE.markDirty();
    }

    private void updateSnapshotArenaTemplate(ServerPlayer player, ArenaHandle handle) {
        if (player == null || handle == null) {
            return;
        }
        PlayerStateServicesFacade.INSTANCE.loadSnapshot(player.getUUID()).ifPresent(snapshot -> {
            snapshot.withArenaTemplate(
                handle.templateId(),
                handle.templateVersion(),
                handle.policyId(),
                handle.policyVersion()
            );
            PlayerStateServicesFacade.INSTANCE.saveSnapshot(snapshot);
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
        return startPreparedQuest(players, arena, mobId, settings, instanceId, arenaHandle, null);
    }

    public Map<UUID, StartQuestResult> startPreparedQuest(
            List<ServerPlayer> players, ArenaContext arena,
            ResourceLocation mobId, QuestSettings settings,
            @javax.annotation.Nullable UUID instanceId,
            @javax.annotation.Nullable ArenaHandle arenaHandle,
            @javax.annotation.Nullable EnduranceQuest sharedQuest) {

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

        EnduranceQuest quest = sharedQuest != null ? sharedQuest : new EnduranceQuest(template.getMobConfig());
        if (!quest.getMobId().equals(mobId)) {
            for (ServerPlayer player : players) {
                results.put(player.getUUID(), new StartQuestResult(false, "Quest mob mismatch: " + mobId, null));
            }
            return results;
        }
        quest.setTotalWaves(settings.totalWaves);
        quest.setEndlessMode(settings.endlessMode);
        if (sharedQuest != null && quest.getState() != EnduranceQuestState.IN_PROGRESS) {
            quest.start(arena.getId());
        }

        // Start quest for each player
        for (ServerPlayer player : players) {
            if (player == null || !player.isAlive()) continue;

            UUID playerId = player.getUUID();

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
            if (sharedQuest == null) {
                quest.start(arena.getId());
            }

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
            session.setKitId(settings.resolveKitId(playerId));
            session.setPracticeMode(settings.practiceMode);
            if (settings.mobPoolConfig != null) {
                session.setMobPoolConfig(settings.mobPoolConfig.copy());
            }
            session.transitionTo(ActiveQuestSession.LifecycleState.ACTIVE, "prepared quest started");
            activeSessions.put(playerId, session); // Replaces placeholder

            try {
                // Apply arena policy config overrides and sync to client
                applyAndSyncArenaOverrides(player, session);

                // Prepare player (save state, give kit - NO TELEPORT, already done)
                PlayerStateServicesFacade.INSTANCE.preparePlayerForQuest(player, session);

                // Initialize all subsystems
                EnduranceEventHandler.onQuestStart(player, session);
            } catch (Exception e) {
                LOGGER.error("[EnduranceQuest] Failed to start quest for player {}", player.getName().getString(), e);
                activeSessions.remove(playerId);
                PlayerStateServicesFacade.INSTANCE.loadSnapshot(playerId).ifPresent(snapshot ->
                    PlayerStateServicesFacade.INSTANCE.performRecovery(player, snapshot, "Quest start failed"));
                results.put(playerId, new StartQuestResult(false, "Quest start failed", null));
                continue;
            }

            if (!session.isPracticeMode()) {
                // Start telemetry
                String dungeonId = "endurance_party_" + mobId.toString().replace(":", "_");
                try {
                    TelemetryService.INSTANCE.startDungeonSession(player, dungeonId);
                } catch (Exception e) {
                    LOGGER.warn("[EnduranceQuest] Failed to start telemetry for player {}", player.getName().getString(), e);
                }
            }

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
                boolean applyShared = true;
                for (ServerPlayer player : players) {
                    if (player != null && results.get(player.getUUID()) != null && results.get(player.getUUID()).success()) {
                        EnduranceEventHandler.onWaveStart(player, result.session(), 1, applyShared);
                        applyShared = false;
                    }
                }
                break;
            }
        }

        return results;
    }

    public Optional<EnduranceQuest> createSharedQuest(ResourceLocation mobId, UUID questId) {
        EnduranceQuest template = questTemplates.get(mobId);
        if (template == null) {
            return Optional.empty();
        }
        return Optional.of(new EnduranceQuest(template.getMobConfig(), questId));
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
        DiagnosticLogger.quest("startQuest: player=%s, mobId=%s, waves=%d, endless=%s",
            player.getName().getString(), mobId, settings.totalWaves, settings.endlessMode);

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

        // Structured logging for quest start
        EnduranceLogger.phase(Phase.QUEST_START, player, quest.getQuestId(),
            "Starting quest: mob=%s, waves=%d, endless=%s, practice=%s",
            mobId, settings.totalWaves, settings.endlessMode, settings.practiceMode);

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

        ResolvedArena resolved = resolveArenaTemplate(playerId, mobId, settings, player.getServer());
        if (resolved == null) {
            activeSessions.remove(playerId);
            return new StartQuestResult(false, "No matching arena template/policy", null);
        }

        pendingSession.setPending(true);
        pendingSession.transitionTo(ActiveQuestSession.LifecycleState.PREPARING, "pending instance start");
        pendingSession.setDifficultyLabel(resolveDifficultyLabel(settings, quest.getMobConfig()));
        pendingSession.setQuestTypeLabel(resolveQuestTypeLabel(settings, quest.getMobConfig()));
        pendingSession.setKitId(settings.resolveKitId(playerId));
        pendingSession.setPracticeMode(settings.practiceMode);
        pendingSession.scheduleBriefing(BRIEFING_TICKS);
        pendingSession.scheduleInstanceStart(mobId, settings, resolved, PRE_TELEPORT_COUNTDOWN_TICKS);

        int briefingSeconds = (int) Math.ceil(BRIEFING_TICKS / 20.0);
        List<String> briefingLines = buildBriefingLines(quest, pendingSession);
        pendingSession.setBriefingLines(briefingLines);
        sendSoloSequenceUpdate(
            player,
            pendingSession,
            QuestSequencePayload.Phase.BRIEFING,
            briefingSeconds,
            quest.getDisplayName(),
            i18nToken("devmod.endurance.briefing.subtitle"),
            briefingLines
        );
        pendingSession.setLastBriefingSeconds(briefingSeconds);

        return new StartQuestResult(true, "Preparing instance...", pendingSession);
    }

    void startPendingInstanceQuest(ServerPlayer player, ActiveQuestSession session) {
        if (player == null || session == null) {
            return;
        }
        DiagnosticLogger.quest("startPendingInstanceQuest: player=%s, sessionId=%s",
            player.getName().getString(), session.getPlayerId());

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

        session.setLoadingProtection(true);
        session.transitionTo(ActiveQuestSession.LifecycleState.TELEPORTING, "instance creation started");
        com.devmod.network.NetworkHandler.sendInstanceLoadingShow(player, "Creating template instance...");
        player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("[DevMod] Creating instance dimension...")
            .withStyle(SharedColorTokens.Chat.YELLOW)));

        // Structured logging for instance creation start
        EnduranceLogger.phase(Phase.INSTANCE_CREATE, player, session.getQuest().getQuestId(),
            "Starting instance creation: template=%s, mob=%s", resolved.template().id(), mobId);

        var startFuture = InstanceManager.INSTANCE
            .prepareInstanceQuest(player, resolved.template().id(), mobId.toString(), null);
        startFuture = startFuture.whenComplete((instanceId, throwable) -> {
            var server = player.getServer();
            if (server == null) {
                failPendingInstanceSetup(player, session, instanceId, "Server not available");
                return;
            }
            server.execute(() -> {
                ActiveQuestSession currentSession = activeSessions.get(session.getPlayerId());
                ServerPlayer currentPlayer = server.getPlayerList().getPlayer(Objects.requireNonNull(session.getPlayerId()));
                if (currentPlayer == null || currentSession == null) {
                    if (instanceId != null) {
                        InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId);
                    }
                    return;
                }
                if (throwable != null) {
                    String message = throwable.getMessage() != null ? throwable.getMessage() : "Unknown error";
                    failPendingInstanceSetup(currentPlayer, currentSession, instanceId,
                        "Failed to create instance: " + message);
                    return;
                }
                if (instanceId == null) {
                    failPendingInstanceSetup(currentPlayer, currentSession, null, "Failed to create instance");
                    return;
                }
                completeTemplateInstanceQuestSetup(
                    currentPlayer,
                    currentSession.getPlayerId(),
                    mobId,
                    currentSession.getQuest(),
                    settings,
                    resolved,
                    instanceId
                );
            });
        });
        if (startFuture.isCancelled()) {
            LOGGER.debug("[EnduranceQuest] Instance start cancelled for {}", mobId);
        }
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
                InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId);
            }
            return;
        }

        if (instanceId == null) {
            LOGGER.error("[EnduranceQuest] Template instance creation failed for player {}", playerId);
            activeSessions.remove(playerId);
            com.devmod.network.NetworkHandler.sendInstanceLoadingHide(player);
            sendSoloSequenceUpdate(player, pendingSession, QuestSequencePayload.Phase.CANCELLED, 0);
            player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("[DevMod] Failed to create instance")
                .withStyle(SharedColorTokens.Chat.RED)));
            return;
        }

        Optional<InstanceData> instanceOpt = InstanceServicesFacade.INSTANCE.getInstance(instanceId);
        if (instanceOpt.isEmpty()) {
            InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId);
            activeSessions.remove(playerId);
            com.devmod.network.NetworkHandler.sendInstanceLoadingHide(player);
            sendSoloSequenceUpdate(player, pendingSession, QuestSequencePayload.Phase.CANCELLED, 0);
            player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("[DevMod] Instance not found")
                .withStyle(SharedColorTokens.Chat.RED)));
            return;
        }

        InstanceData instance = instanceOpt.get();
        var dimensionKey = instance.getDimensionKey();
        if (dimensionKey == null) {
            InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId);
            activeSessions.remove(playerId);
            com.devmod.network.NetworkHandler.sendInstanceLoadingHide(player);
            sendSoloSequenceUpdate(player, pendingSession, QuestSequencePayload.Phase.CANCELLED, 0);
            player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("[DevMod] Instance dimension not ready")
                .withStyle(SharedColorTokens.Chat.RED)));
            return;
        }

        ServerLevel instanceLevel = server.getLevel(dimensionKey);
        if (instanceLevel == null) {
            InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId);
            activeSessions.remove(playerId);
            com.devmod.network.NetworkHandler.sendInstanceLoadingHide(player);
            sendSoloSequenceUpdate(player, pendingSession, QuestSequencePayload.Phase.CANCELLED, 0);
            player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("[DevMod] Instance level not found")
                .withStyle(SharedColorTokens.Chat.RED)));
            return;
        }

        ArenaTemplate template = resolved.template();
        OriginResolution origin = resolveTemplateOrigin(template);

        if (shouldBuildAsync(template)) {
            AsyncArenaBuilder asyncBuilder = asyncBuildCoordinator.getOrCreate(instanceLevel);
            UUID arenaId = UUID.randomUUID();
            var buildFuture = asyncBuilder.submitBuildAsync(
                arenaId,
                template,
                origin.centerX(),
                origin.originY(),
                origin.centerZ()
            );
            buildFuture = buildFuture.whenComplete((asyncResult, buildError) -> {
                server.execute(() -> {
                    ActiveQuestSession currentSession = activeSessions.get(playerId);
                    ServerPlayer currentPlayer = server.getPlayerList().getPlayer(Objects.requireNonNull(playerId));
                    if (currentPlayer == null || currentSession == null) {
                        InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId);
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
            if (buildFuture.isCancelled()) {
                LOGGER.debug("[EnduranceQuest] Async solo arena build cancelled for {}", arenaId);
            }
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
            InstanceServicesFacade.INSTANCE.cleanupFailedQuest(instanceId);
        }
        if (pendingSession != null) {
            pendingSession.setLoadingProtection(false);
            pendingSession.transitionTo(ActiveQuestSession.LifecycleState.FAILED, "instance setup failed");
            activeSessions.remove(pendingSession.getPlayerId());
        }
        if (player != null) {
            com.devmod.network.NetworkHandler.sendInstanceLoadingHide(player);
            if (pendingSession != null) {
                sendSoloSequenceUpdate(player, pendingSession, QuestSequencePayload.Phase.CANCELLED, 0);
            }
            String msg = message != null ? message : "Build failed";
            player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("[DevMod] " + msg)
                .withStyle(SharedColorTokens.Chat.RED)));
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
            session.setPracticeMode(pendingSession.isPracticeMode());
        } else {
            session.setDifficultyLabel(resolveDifficultyLabel(settings, quest.getMobConfig()));
            session.setQuestTypeLabel(resolveQuestTypeLabel(settings, quest.getMobConfig()));
            session.setKitId(settings.resolveKitId(effectivePlayerId));
            session.setPracticeMode(settings.practiceMode);
        }
        session.transitionTo(ActiveQuestSession.LifecycleState.ACTIVE, "quest started");
        if (pendingSession != null) {
            pendingSession.setLoadingProtection(false);
        }
        session.setLoadingProtection(true);
        activeSessions.put(effectivePlayerId, session);

        // Apply arena policy config overrides and sync to client
        applyAndSyncArenaOverrides(player, session);

        PlayerStateServicesFacade.INSTANCE.preparePlayerForQuest(player, session);

        EnduranceEventHandler.onQuestStart(player, session);

        if (!session.isPracticeMode()) {
            String dungeonId = "endurance_instance_" + mobId.toString().replace(":", "_");
            TelemetryService.INSTANCE.startDungeonSession(player, dungeonId);
        }

        session.scheduleSafeWindow(SAFE_WINDOW_TICKS);
        if (SAFE_WINDOW_TICKS > 0) {
            PlayerStateServicesFacade.INSTANCE.applySafeWindowEffects(player, SAFE_WINDOW_TICKS);
            session.setLastSafeWindowSeconds((int) Math.ceil(SAFE_WINDOW_TICKS / 20.0));
        }
        session.scheduleWaveStart(WAVE_START_COUNTDOWN_TICKS);
        sendSoloSequenceUpdate(player, session, QuestSequencePayload.Phase.SAFE_WINDOW,
            (int) Math.ceil(SAFE_WINDOW_TICKS / 20.0),
            quest.getDisplayName(),
            "Safe window",
            List.of("Invulnerability active"));
        session.setLoadingProtection(false);

        // Structured logging for instance ready and player teleport
        EnduranceLogger.phase(Phase.INSTANCE_READY, player, quest.getQuestId(),
            "Instance ready: id=%s, template=%s", instanceId, template.id());
        EnduranceLogger.phase(Phase.PLAYER_TELEPORT, player, quest.getQuestId(),
            "Teleported to instance dimension: instanceId=%s, arenaPos=(%.1f, %.1f, %.1f), scheduling wave in %d ticks",
            instanceId, player.getX(), player.getY(), player.getZ(), WAVE_START_COUNTDOWN_TICKS);

        LOGGER.info("[EnduranceQuest] Player {} started TEMPLATE quest: {} (instance: {})",
            player.getName().getString(), quest.getDisplayName(), instanceId);

        player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("[DevMod] Quest started in instance dimension!")
            .withStyle(SharedColorTokens.Chat.GREEN)));
    }

    // ========== Session Management (Delegated) ==========

    /**
     * Get active quest session for a player.
     * Returns empty if the session is not ready for active gameplay:
     * - isInitializing: arena not yet set (placeholder state)
     * - isPending: instance still being created
     * - isLoadingProtection: player still loading into instance
     * This prevents race conditions where code tries to use an incomplete session.
     */
    public Optional<ActiveQuestSession> getActiveSession(UUID playerId) {
        ActiveQuestSession session = activeSessions.get(playerId);
        if (session == null) {
            return Optional.empty();
        }
        // Filter out sessions that are not yet ready for active gameplay
        if (session.isInitializing() || session.isPending() || session.isLoadingProtection()) {
            return Optional.empty();
        }
        return Optional.of(session);
    }

    /**
     * Get active quest session for a player.
     */
    public Optional<ActiveQuestSession> getActiveSession(Player player) {
        return getActiveSession(player.getUUID());
    }

    public boolean hasActiveQuest(@javax.annotation.Nullable UUID questId) {
        if (questId == null) {
            return false;
        }
        for (ActiveQuestSession session : activeSessions.values()) {
            if (questId.equals(session.getQuest().getQuestId())) {
                return true;
            }
        }
        PartyQuestSession partySession = getPartySessionByQuest(questId).orElse(null);
        return partySession != null && partySession.isActive();
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
        ServerPlayer safePlayer = requireNonNull(player, "player");
        ActiveQuestSession safeSession = requireNonNull(session, "session");
        try {
            ArenaPolicy policy = getPolicyForSession(safeSession);
            if (policy == null) {
                return;
            }
            ArenaPolicy.GameplayOverrides overrides = policy.gameplayOverrides();
            if (overrides == null) {
                return;
            }
            UUID questId = safeSession.getQuest().getQuestId();

            // Apply override to server-side config manager
            EnduranceConfigManager.INSTANCE.setArenaOverride(questId, overrides);

            // Sync override to client
            net.minecraft.nbt.CompoundTag overrideTag = serializeGameplayOverrides(overrides);
            GameMechanicsSyncPayload payload =
                requireNonNull(GameMechanicsSyncPayload.forQuest(questId, overrideTag), "payload");
            PacketDistributor.sendToPlayer(Objects.requireNonNull(safePlayer), Objects.requireNonNull(payload));

            LOGGER.debug("[EnduranceQuest] Applied arena config overrides for quest {} to player {}",
                questId, safePlayer.getName().getString());
        } catch (Exception e) {
            LOGGER.warn("[EnduranceQuest] Failed to apply arena config overrides: {}", e.getMessage());
        }
    }

    /**
     * Serialize GameplayOverrides to CompoundTag for network sync.
     */
    private net.minecraft.nbt.CompoundTag serializeGameplayOverrides(ArenaPolicy.GameplayOverrides overrides) {
        ArenaPolicy.GameplayOverrides safeOverrides = requireNonNull(overrides, "overrides");
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();

        // Serialize each section if present
        ArenaPolicy.ComboOverrides comboOverrides = safeOverrides.combo();
        if (comboOverrides != null) {
            net.minecraft.nbt.CompoundTag combo = new net.minecraft.nbt.CompoundTag();
            putIntIfPresent(combo, "timeoutTicks", comboOverrides.timeoutTicks());
            putIntIfPresent(combo, "basePoints", comboOverrides.basePoints());
            putDoubleIfPresent(combo, "multiplierIncrement", comboOverrides.multiplierIncrement());
            putDoubleIfPresent(combo, "maxMultiplier", comboOverrides.maxMultiplier());
            tag.put("combo", combo);
        }

        ArenaPolicy.TensionOverrides tensionOverrides = safeOverrides.tension();
        if (tensionOverrides != null) {
            net.minecraft.nbt.CompoundTag tension = new net.minecraft.nbt.CompoundTag();
            putDoubleIfPresent(tension, "baseWaveGain", tensionOverrides.baseWaveGain());
            putDoubleIfPresent(tension, "noHitBonus", tensionOverrides.noHitBonus());
            putDoubleIfPresent(tension, "minThreshold", tensionOverrides.minThreshold());
            putDoubleIfPresent(tension, "maxThreshold", tensionOverrides.maxThreshold());
            putIntIfPresent(tension, "minWavesBeforeBoss", tensionOverrides.minWavesBeforeBoss());
            putIntIfPresent(tension, "maxWavesWithoutBoss", tensionOverrides.maxWavesWithoutBoss());
            tag.put("tension", tension);
        }

        ArenaPolicy.WaveOverrides waveOverrides = safeOverrides.waves();
        if (waveOverrides != null) {
            net.minecraft.nbt.CompoundTag waves = new net.minecraft.nbt.CompoundTag();
            putIntIfPresent(waves, "baseMobCount", waveOverrides.baseMobCount());
            putDoubleIfPresent(waves, "mobScaling", waveOverrides.mobScaling());
            putIntIfPresent(waves, "intermissionTicks", waveOverrides.intermissionTicks());
            putIntIfPresent(waves, "bossInterval", waveOverrides.bossInterval());
            tag.put("waves", waves);
        }

        ArenaPolicy.PerkRarityOverrides perkRarityOverrides = safeOverrides.perkRarity();
        if (perkRarityOverrides != null) {
            net.minecraft.nbt.CompoundTag perkRarity = new net.minecraft.nbt.CompoundTag();
            putIntIfPresent(perkRarity, "commonWeight", perkRarityOverrides.commonWeight());
            putIntIfPresent(perkRarity, "uncommonWeight", perkRarityOverrides.uncommonWeight());
            putIntIfPresent(perkRarity, "rareWeight", perkRarityOverrides.rareWeight());
            putIntIfPresent(perkRarity, "epicWeight", perkRarityOverrides.epicWeight());
            putIntIfPresent(perkRarity, "legendaryWeight", perkRarityOverrides.legendaryWeight());
            tag.put("perkRarity", perkRarity);
        }

        // Add more sections as needed...

        return tag;
    }

    private static void putIntIfPresent(net.minecraft.nbt.CompoundTag tag, String key, Integer value) {
        if (value != null) {
            tag.putInt(Objects.requireNonNull(key, "key"), value);
        }
    }

    private static void putDoubleIfPresent(net.minecraft.nbt.CompoundTag tag, String key, Double value) {
        if (value != null) {
            tag.putDouble(Objects.requireNonNull(key, "key"), value);
        }
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
     * Complete current wave for a party-run session.
     */
    public void completePartyWave(PartyQuestSession partySession) {
        if (partySession == null || !partySession.isActive()) {
            return;
        }
        EnduranceQuest quest = partySession.getQuest();
        if (quest.getState() != EnduranceQuestState.IN_PROGRESS) {
            return;
        }

        quest.completeWave();
        if (quest.getState() == EnduranceQuestState.COMPLETED) {
            endPartyRun(partySession, true, "completed");
        }
    }

    public void requestPartyContinue(UUID playerId) {
        ActiveQuestSession session = activeSessions.get(playerId);
        if (session == null || session.getPartyId() == null) {
            return;
        }
        PartyQuestSession partySession = partySessions.get(session.getPartyId());
        if (partySession == null || !partySession.isActive()) {
            return;
        }
        EnduranceQuest quest = partySession.getQuest();
        if (quest.getState() != EnduranceQuestState.WAVE_COMPLETE) {
            return;
        }
        partySession.markWaveReady(playerId);
        syncPartyState(partySession.getPartyId());
        if (partySession.isReadyForNextWave()) {
            advancePartyToNextWave(partySession);
        }
    }

    private void advancePartyToNextWave(PartyQuestSession partySession) {
        if (partySession == null || !partySession.isActive()) {
            return;
        }
        EnduranceQuest quest = partySession.getQuest();
        if (quest.getState() != EnduranceQuestState.WAVE_COMPLETE) {
            return;
        }
        int waveNumber = quest.getCurrentWave();
        if (!partySession.markWaveAdvance(waveNumber)) {
            return;
        }
        partySession.clearWaveReady();
        UUID arenaId = partySession.getArenaId();
        if (arenaId != null) {
            WaveManager.INSTANCE.clearCompletedWaveState(arenaId);
        }
        quest.continueToNextWave();
        updatePartyPlayerCount(partySession);

        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        for (UUID memberId : partySession.getMembers()) {
            ActiveQuestSession session = activeSessions.get(memberId);
            if (session == null) {
                continue;
            }
            session.resetWaveKills();
            session.scheduleWaveStart(WAVE_START_COUNTDOWN_TICKS);
            session.setRespawnCountdownActive(false);

            if (session.isPartySpectator() && server != null) {
                ServerPlayer player = server.getPlayerList().getPlayer(Objects.requireNonNull(memberId));
                if (player != null) {
                    rejoinPartyMember(player);
                }
            }
        }
    }

    /**
     * Continue to next wave after checkpoint.
     */
    public void continueToNextWave(ServerPlayer player) {
        DiagnosticLogger.quest("continueToNextWave: player=%s", player.getName().getString());
        sessionHandler.continueToNextWave(player);
    }

    /**
     * Exit at checkpoint (between waves).
     */
    public void exitAtCheckpoint(ServerPlayer player) {
        DiagnosticLogger.quest("exitAtCheckpoint: player=%s", player.getName().getString());
        sessionHandler.exitAtCheckpoint(player);
    }

    /**
     * Critical failure handler for teleport/dimension recovery.
     * Ends the quest safely and restores player state.
     */
    public void handleCriticalTeleportFailure(ServerPlayer player,
                                              ActiveQuestSession session,
                                              String reason) {
        if (player == null || session == null) {
            return;
        }
        String safeReason = reason != null && !reason.isBlank() ? reason : "teleport_failed";
        if (session.getPartyId() != null) {
            getPartySession(session.getPartyId()).ifPresent(partySession ->
                endPartyRun(partySession, false, safeReason));
            return;
        }
        if (sessionHandler != null) {
            sessionHandler.forceFailQuest(player, session, safeReason);
        }
    }

    public void endPartyRun(PartyQuestSession partySession, boolean completed, String reason) {
        if (partySession == null || !partySession.isActive()) {
            return;
        }
        DiagnosticLogger.quest("endPartyRun: partyId=%s, completed=%s, reason=%s, members=%d",
            partySession.getPartyId(), completed, reason, partySession.getMembers().size());

        partySession.end(completed ? PartyQuestSession.Status.COMPLETED : PartyQuestSession.Status.FAILED);

        UUID questId = partySession.getQuestId();
        ActiveQuestSession cleanupSession = null;
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        try {
            for (UUID memberId : partySession.getMembers()) {
                ActiveQuestSession session = activeSessions.remove(memberId);
                if (session == null) {
                    continue;
                }
                if (cleanupSession == null) {
                    cleanupSession = session;
                }
                ServerPlayer player = server != null ? server.getPlayerList().getPlayer(Objects.requireNonNull(memberId)) : null;

                if (player != null) {
                    try {
                        EnduranceEventHandler.onQuestEnd(player, session, completed);
                    } catch (Exception e) {
                        LOGGER.error("[EnduranceQuest] Failed onQuestEnd for party member {}",
                            player.getName().getString(), e);
                    }
                    if (!session.isPracticeMode()) {
                        try {
                            com.devmod.telemetry.TelemetryService.INSTANCE.endDungeonSession(
                                player, completed ? "completed" : (reason != null ? reason : "failed"));
                        } catch (Exception e) {
                            LOGGER.warn("[EnduranceQuest] Failed to end telemetry session for party member {}",
                                player.getName().getString(), e);
                        }
                    }
                    try {
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                            player, Objects.requireNonNull(QuestSyncPayload.empty()));
                    } catch (Exception e) {
                        LOGGER.warn("[EnduranceQuest] Failed to sync quest end to party member {}",
                            player.getName().getString(), e);
                    }
                    try {
                        PlayerStateServicesFacade.INSTANCE.restorePlayerAfterQuest(player, session);
                    } catch (Exception e) {
                        LOGGER.warn("[EnduranceQuest] Failed to restore party member {} state",
                            player.getName().getString(), e);
                    }
                }
            }

            try {
                EnduranceConfigManager.INSTANCE.cleanupQuest(questId);
            } catch (Exception e) {
                LOGGER.warn("[EnduranceQuest] Failed to cleanup quest config for party run {}", questId, e);
            }
            try {
                EnduranceEventCombat.removeMutatorSession(questId);
            } catch (Exception e) {
                LOGGER.warn("[EnduranceQuest] Failed to remove mutator session for party run {}", questId, e);
            }
            try {
                MutatorSystem.INSTANCE.endSession(questId);
            } catch (Exception e) {
                LOGGER.warn("[EnduranceQuest] Failed to end mutator session for party run {}", questId, e);
            }
            try {
                CombatTracker.INSTANCE.stopTracking(questId);
            } catch (Exception e) {
                LOGGER.warn("[EnduranceQuest] Failed to stop combat tracking for party run {}", questId, e);
            }
            try {
                TensionSystem.INSTANCE.endSession(questId);
            } catch (Exception e) {
                LOGGER.warn("[EnduranceQuest] Failed to end tension session for party run {}", questId, e);
            }
            try {
                com.devmod.endurance.bargain.DevilsBargainManager.INSTANCE.endSession(questId);
            } catch (Exception e) {
                LOGGER.warn("[EnduranceQuest] Failed to end bargain session for party run {}", questId, e);
            }
            try {
                com.devmod.endurance.hazard.ArenaHazardSystem.INSTANCE.endSession(questId);
            } catch (Exception e) {
                LOGGER.warn("[EnduranceQuest] Failed to end hazard session for party run {}", questId, e);
            }
            try {
                DirectiveChainManager.INSTANCE.endChain(questId);
            } catch (Exception e) {
                LOGGER.warn("[EnduranceQuest] Failed to end directive chain for party run {}", questId, e);
            }
        } finally {
            if (cleanupSession != null) {
                try {
                    PlayerStateServicesFacade.INSTANCE.cleanupQuestSystems(cleanupSession);
                } catch (Exception e) {
                    LOGGER.warn("[EnduranceQuest] Failed to cleanup quest systems for party run {}", questId, e);
                }
                try {
                    PlayerStateServicesFacade.INSTANCE.cleanupArenaOrInstance(cleanupSession, completed);
                } catch (Exception e) {
                    LOGGER.warn("[EnduranceQuest] Failed to cleanup instance for party run {}", questId, e);
                }
            } else {
                UUID instanceId = partySession.getInstanceId();
                if (instanceId != null) {
                    InstanceServicesFacade.INSTANCE.safeCleanup(instanceId, completed);
                }
            }
        }

        com.devmod.party.PartyManager.INSTANCE.finishQuest(java.util.Objects.requireNonNull(partySession.getPartyId()));
        if (server != null) {
            var party = com.devmod.party.PartyManager.INSTANCE.getParty(java.util.Objects.requireNonNull(partySession.getPartyId()));
            if (party != null) {
                UUID leaderId = party.getLeaderId();
                ServerPlayer leaderPlayer = server.getPlayerList().getPlayer(java.util.Objects.requireNonNull(leaderId));
                if (leaderPlayer == null) {
                    UUID newLeader = party.getMembers().stream()
                        .filter(id -> !id.equals(leaderId))
                        .filter(id -> server.getPlayerList().getPlayer(java.util.Objects.requireNonNull(id)) != null)
                        .findFirst()
                        .orElse(null);
                    if (newLeader != null) {
                        com.devmod.party.PartyManager.INSTANCE.transferLeadership(leaderId, newLeader);
                    }
                }
            }
            com.devmod.network.handlers.PartyNetworkHandler.syncPartyToAllMembers(server, partySession.getPartyId());
        }

        removePartySession(partySession.getPartyId());
        LOGGER.info("[EnduranceQuest] Party run ended partyId={} questId={} completed={}",
            partySession.getPartyId(), questId, completed);
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

    // ========== Party Quest Sessions ==========

    public PartyQuestSession registerPartySession(PartyQuestSession session) {
        if (session == null) {
            return null;
        }
        // Thread-safe: Atomic update of both maps under lock
        synchronized (partySessionLock) {
            partySessions.put(session.getPartyId(), session);
            questToParty.put(session.getQuestId(), session.getPartyId());
        }
        var party = com.devmod.party.PartyManager.INSTANCE.getParty(session.getPartyId());
        if (party != null && party.getState() != com.devmod.party.PartyData.PartyState.IN_QUEST) {
            com.devmod.party.PartyManager.INSTANCE.forceStartQuest(session.getPartyId(), session.getInstanceId());
        }
        updatePartyPlayerCount(session);
        return session;
    }

    public Optional<PartyQuestSession> getPartySession(UUID partyId) {
        return Optional.ofNullable(partySessions.get(partyId));
    }

    public Optional<PartyQuestSession> getPartySessionByPlayer(UUID playerId) {
        ActiveQuestSession session = activeSessions.get(playerId);
        if (session == null || session.getPartyId() == null) {
            return Optional.empty();
        }
        return getPartySession(session.getPartyId());
    }

    public Optional<PartyQuestSession> getPartySessionByQuest(UUID questId) {
        UUID partyId = questToParty.get(questId);
        if (partyId == null) {
            return Optional.empty();
        }
        return getPartySession(partyId);
    }

    public boolean isPartyQuest(UUID questId) {
        return questToParty.containsKey(questId);
    }

    public void removePartySession(UUID partyId) {
        // Thread-safe: Atomic removal from both maps under lock
        synchronized (partySessionLock) {
            PartyQuestSession session = partySessions.remove(partyId);
            if (session != null) {
                questToParty.remove(session.getQuestId());
            }
        }
    }

    public boolean isPartyRunActiveForPlayer(UUID playerId) {
        return getPartySessionByPlayer(playerId).map(PartyQuestSession::isActive).orElse(false);
    }

    public void markPartyMemberInactive(UUID playerId, String reason) {
        ActiveQuestSession session = activeSessions.get(playerId);
        if (session == null || session.getPartyId() == null) {
            return;
        }
        PartyQuestSession partySession = partySessions.get(session.getPartyId());
        if (partySession == null || !partySession.isActive()) {
            return;
        }
        session.setPartySpectator(true);
        partySession.markSpectator(playerId);
        updatePartyPlayerCount(partySession);
        syncPartyState(partySession.getPartyId());

        if (partySession.isWiped()) {
            EnduranceQuest quest = partySession.getQuest();
            quest.fail(false);
            endPartyRun(partySession, false, reason != null ? reason : "party_wipe");
            return;
        }
        tryAdvancePartyWave(partySession);
    }

    public void markPartyMemberActive(UUID playerId) {
        ActiveQuestSession session = activeSessions.get(playerId);
        if (session == null || session.getPartyId() == null) {
            return;
        }
        PartyQuestSession partySession = partySessions.get(session.getPartyId());
        if (partySession == null || !partySession.isActive()) {
            return;
        }
        session.setPartySpectator(false);
        partySession.markActive(playerId);
        updatePartyPlayerCount(partySession);
        syncPartyState(partySession.getPartyId());
    }

    private void updatePartyPlayerCount(PartyQuestSession partySession) {
        if (partySession == null) {
            return;
        }
        int playerCount = Math.max(1, partySession.getActiveMemberCount());
        for (UUID memberId : partySession.getMembers()) {
            ActiveQuestSession session = activeSessions.get(memberId);
            if (session != null) {
                session.setPlayerCount(playerCount);
            }
        }
    }

    private void tryAdvancePartyWave(PartyQuestSession partySession) {
        if (partySession == null || !partySession.isActive()) {
            return;
        }
        EnduranceQuest quest = partySession.getQuest();
        if (quest.getState() != EnduranceQuestState.WAVE_COMPLETE) {
            return;
        }
        if (!partySession.isReadyForNextWave()) {
            return;
        }
        advancePartyToNextWave(partySession);
    }

    private void syncPartyState(UUID partyId) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            com.devmod.network.handlers.PartyNetworkHandler.syncPartyToAllMembers(server, partyId);
        }
    }

    public boolean attachPartyMemberSession(ServerPlayer player, PartyQuestSession partySession) {
        if (player == null || partySession == null || !partySession.isActive()) {
            return false;
        }
        UUID playerId = player.getUUID();
        if (activeSessions.containsKey(playerId)) {
            return false;
        }

        ActiveQuestSession templateSession = null;
        for (UUID memberId : partySession.getMembers()) {
            ActiveQuestSession existing = activeSessions.get(memberId);
            if (existing != null) {
                templateSession = existing;
                break;
            }
        }
        ArenaContext arena = templateSession != null ? templateSession.getArena() : null;
        ArenaHandle arenaHandle = templateSession != null ? templateSession.getArenaHandle() : null;
        if (arena == null) {
            arena = partySession.getArena();
        }
        if (arenaHandle == null) {
            arenaHandle = partySession.getArenaHandle();
        }
        if (arena == null || arenaHandle == null) {
            return false;
        }

        ActiveQuestSession placeholder = new ActiveQuestSession(playerId, partySession.getQuest(), null, System.currentTimeMillis());
        ActiveQuestSession existing = activeSessions.putIfAbsent(playerId, placeholder);
        if (existing != null) {
            return false;
        }

        UUID instanceId = partySession.getInstanceId();
        if (instanceId != null && !prepareLateJoinInstance(player, instanceId, partySession)) {
            activeSessions.remove(playerId);
            return false;
        }

        EnduranceQuest quest = partySession.getQuest();
        ActiveQuestSession session = new ActiveQuestSession(
            playerId,
            quest,
            arena,
            System.currentTimeMillis(),
            partySession.getPartyId(),
            partySession.getQuestType(),
            Math.max(1, partySession.getActiveMemberCount())
        );
        if (instanceId != null) {
            session.setInstanceId(instanceId);
        }
        session.setArenaHandle(arenaHandle);
        updateSnapshotArenaTemplate(player, arenaHandle);
        if (templateSession != null) {
            session.setDifficultyLabel(templateSession.getDifficultyLabel());
            session.setQuestTypeLabel(templateSession.getQuestTypeLabel());
            session.setKitId(templateSession.getKitId());
            session.setPracticeMode(templateSession.isPracticeMode());
            for (var entry : templateSession.getConfigOverrides().entrySet()) {
                session.setConfigOverride(entry.getKey(), entry.getValue());
            }
            session.setMobPoolConfig(templateSession.getMobPoolConfig());
        }
        session.transitionTo(ActiveQuestSession.LifecycleState.ACTIVE, "party member attached");
        session.setPartySpectator(true);
        activeSessions.put(playerId, session);

        applyAndSyncArenaOverrides(player, session);
        PlayerStateServicesFacade.INSTANCE.preparePlayerForQuest(player, session);
        EnduranceEventHandler.onQuestStart(player, session);
        if (!session.isPracticeMode()) {
            String dungeonId = "endurance_party_" + quest.getMobId().toString().replace(":", "_");
            TelemetryService.INSTANCE.startDungeonSession(player, dungeonId);
        }

        partySession.markSpectator(playerId);
        updatePartyPlayerCount(partySession);
        syncPartyState(partySession.getPartyId());
        return true;
    }

    private boolean prepareLateJoinInstance(ServerPlayer player, UUID instanceId, PartyQuestSession partySession) {
        Optional<InstanceData> instanceOpt = InstanceServicesFacade.INSTANCE.getInstance(instanceId);
        if (instanceOpt.isEmpty()) {
            return false;
        }
        InstanceData instance = instanceOpt.get();
        if (!instance.addPlayer(player.getUUID())) {
            return false;
        }
        InstanceServicesFacade.INSTANCE.save();
        var snapshot = PlayerStateServicesFacade.INSTANCE.createSnapshotFromPlayer(player, instance);
        snapshot.setState(com.devmod.runtime.PlayerInstanceState.PREPARING);
        var party = com.devmod.party.PartyManager.INSTANCE.getParty(partySession.getPartyId());
        if (party != null) {
            snapshot.setPartyLeaderId(party.getLeaderId());
            snapshot.setPartyMembers(new java.util.HashSet<>(party.getMembers()));
        } else {
            snapshot.setPartyMembers(new java.util.HashSet<>(partySession.getMembers()));
        }
        PlayerStateServicesFacade.INSTANCE.saveSnapshot(snapshot);
        InstanceServicesFacade.INSTANCE.mapPlayer(player.getUUID(), instanceId);
        return true;
    }

    public boolean rejoinPartyMember(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        ActiveQuestSession session = activeSessions.get(player.getUUID());
        if (session == null || session.getPartyId() == null) {
            return false;
        }
        PartyQuestSession partySession = partySessions.get(session.getPartyId());
        if (partySession == null || !partySession.isActive()) {
            return false;
        }
        if (!session.isPartySpectator()) {
            return true;
        }

        boolean teleported = teleportPlayerToArena(
            player,
            session,
            true,
            session.isInInstanceDimension()
        );

        if (teleported) {
            player.setGameMode(GameType.SURVIVAL);
            PlayerStateServicesFacade.INSTANCE.resetQuestLoadout(player, session);
            PlayerStateServicesFacade.INSTANCE.applySafeWindowEffects(player, SAFE_WINDOW_TICKS);
            markPartyMemberActive(player.getUUID());
            player.sendSystemMessage(Objects.requireNonNull(
                net.minecraft.network.chat.Component.literal("[DevMod] Rejoined party run.")
                    .withStyle(SharedColorTokens.Chat.GREEN)));
        }

        return teleported;
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
            questTemplates.put(mobConfig.getMobId(), template);
        }
    }

    /**
     * Cleans up expired overrides to prevent memory leaks.
     * Should be called periodically (e.g., every 5 minutes).
     *
     * @return The number of expired overrides cleaned up
     */
    public int cleanupExpiredOverrides() {
        int cleaned = 0;
        if (overrideManager != null) {
            cleaned = overrideManager.cleanupExpiredOverrides();
        }
        return cleaned;
    }

    /**
     * Cleanup path used only during server shutdown to avoid dangling state
     * and to ensure telemetry/stats are flushed without granting full rewards.
     */
    private void handleForcedShutdownCleanup(ActiveQuestSession session, boolean cleanupShared) {
        try {
            EnduranceQuest quest = session.quest;
            UUID questId = quest.getQuestId();
            UUID playerId = session.getPlayerId();

            if (cleanupShared) {
                EnduranceConfigManager.INSTANCE.cleanupQuest(questId);
            }

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

            if (cleanupShared) {
                CombatTracker.INSTANCE.stopTracking(questId);
            }
            if (ComboSystemFacade.isInitialized()) {
                ComboSystemFacade.get().endSession(playerId);
            }
            if (cleanupShared) {
                MutatorSystem.INSTANCE.endSession(questId);
                EnduranceEventCombat.removeMutatorSession(questId);
            }
            PerkSystem.INSTANCE.endSession(playerId);

            if (cleanupShared) {
                PlayerStateServicesFacade.INSTANCE.cleanupQuestSystems(session);
                PlayerStateServicesFacade.INSTANCE.cleanupArenaOrInstance(session, false);
            }
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
        public java.util.Map<UUID, String> partyKitIds = java.util.Map.of();

        // Kit selection
        public String kitId = "STARTER";

        // Arena template override (null = auto-select based on MobRequirements)
        @javax.annotation.Nullable
        public String forceTemplateId = null;

        // Practice mode (uses training dummies instead of real mobs)
        public boolean practiceMode = false;

        // Pending mob pool config for party session (optional)
        public @javax.annotation.Nullable com.devmod.endurance.config.EnduranceMobPoolConfig mobPoolConfig = null;

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
            this.arenaSize = type.getDefaultArenaSize();
            return this;
        }

        public QuestSettings party(UUID partyId, java.util.List<UUID> memberIds) {
            this.partyId = partyId;
            this.partyMemberIds = memberIds;
            return this;
        }

        public QuestSettings partyKits(java.util.Map<UUID, String> kitIds) {
            this.partyKitIds = kitIds != null ? kitIds : java.util.Map.of();
            return this;
        }

        public QuestSettings forceTemplate(@javax.annotation.Nullable String templateId) {
            this.forceTemplateId = templateId;
            return this;
        }

        public QuestSettings mobPoolConfig(@javax.annotation.Nullable com.devmod.endurance.config.EnduranceMobPoolConfig config) {
            this.mobPoolConfig = config != null ? config.copy() : null;
            return this;
        }

        public boolean isMultiplayer() {
            return partyId != null && !partyMemberIds.isEmpty();
        }

        public int getPlayerCount() {
            return Math.max(1, partyMemberIds.size());
        }

        public String resolveKitId(@javax.annotation.Nullable UUID playerId) {
            if (playerId != null && partyKitIds != null) {
                String kit = partyKitIds.get(playerId);
                if (kit != null && !kit.isBlank()) {
                    return kit;
                }
            }
            return kitId != null ? kitId : "STARTER";
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
        public enum LifecycleState {
            INITIALIZING,
            PREPARING,
            TELEPORTING,
            ACTIVE,
            AWAITING_RESPAWN,
            CLEANUP,
            COMPLETED,
            FAILED
        }

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
        private boolean loadingProtection = false;

        // Instance start countdown (solo pre-teleport) - AtomicInteger for thread safety
        private final AtomicInteger pendingInstanceStartTicks = new AtomicInteger(0);
        private volatile int lastTeleportCountdownSeconds = -1;
        private @javax.annotation.Nullable ResourceLocation pendingMobId;
        private @javax.annotation.Nullable QuestSettings pendingSettings;
        private @javax.annotation.Nullable ResolvedArena pendingResolved;

        // Briefing countdown (solo pre-teleport lobby) - AtomicInteger for thread safety
        private final AtomicInteger pendingBriefingTicks = new AtomicInteger(0);
        private volatile int lastBriefingSeconds = -1;
        private List<String> briefingLines = List.of();

        // Wave start countdown (solo start / respawn delay) - AtomicInteger for thread safety
        private final AtomicInteger pendingWaveStartTicks = new AtomicInteger(0);
        private volatile int lastWaveCountdownSeconds = -1;

        // Safe window countdown (post-teleport / post-respawn) - AtomicInteger for thread safety
        private final AtomicInteger pendingSafeWindowTicks = new AtomicInteger(0);
        private volatile int lastSafeWindowSeconds = -1;

        // Boss intro countdown (short cinematic pause) - AtomicInteger for thread safety
        private final AtomicInteger pendingBossIntroTicks = new AtomicInteger(0);
        private volatile int lastBossIntroSeconds = -1;
        private boolean respawnCountdownActive = false;

        // Wave directive choices (risk/reward between waves)
        private List<WaveDirective> pendingDirectives = List.of();
        private @javax.annotation.Nullable String selectedDirectiveId;
        private int directiveWaveNumber = -1;

        // Party run spectator flag (inactive member)
        private boolean partySpectator = false;

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

        // Practice mode (uses training dummies instead of mobs)
        private boolean practiceMode = false;

        // Session-specific config overrides (for SESSION scope changes)
        private final Map<String, String> configOverrides = new HashMap<>();

        // Session-specific mob pool configuration (for mob editing in Endurance)
        private @javax.annotation.Nullable com.devmod.endurance.config.EnduranceMobPoolConfig mobPoolConfig;

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
        public boolean isAwaitingRespawnChoice() { return awaitingRespawnChoice.get(); }
        public boolean isRespawnRequested() { return respawnRequested.get(); }
        public LifecycleState getLifecycleState() { return lifecycleState; }
        public long getLifecycleUpdatedAtMs() { return lifecycleUpdatedAtMs; }
        public boolean isLifecycleActive() { return lifecycleState == LifecycleState.ACTIVE; }
        public boolean shouldProcessGameplay() {
            return lifecycleState == LifecycleState.ACTIVE;
        }

        public synchronized boolean transitionTo(LifecycleState next, @javax.annotation.Nullable String reason) {
            LifecycleState current = this.lifecycleState;
            if (current == next) {
                return true;
            }
            boolean valid = isValidTransition(current, next);
            this.lifecycleState = next;
            this.lifecycleUpdatedAtMs = System.currentTimeMillis();
            if (!valid) {
                LOGGER.warn("[EnduranceQuest] Lifecycle transition forced for {}: {} -> {} (reason={})",
                    playerId, current, next, reason);
            } else {
                LOGGER.debug("[EnduranceQuest] Lifecycle transition for {}: {} -> {} (reason={})",
                    playerId, current, next, reason);
            }
            return valid;
        }

        private static boolean isValidTransition(LifecycleState from, LifecycleState to) {
            return switch (from) {
                case INITIALIZING -> to != LifecycleState.COMPLETED;
                case PREPARING -> to == LifecycleState.TELEPORTING
                    || to == LifecycleState.ACTIVE
                    || to == LifecycleState.FAILED
                    || to == LifecycleState.CLEANUP;
                case TELEPORTING -> to == LifecycleState.ACTIVE
                    || to == LifecycleState.FAILED
                    || to == LifecycleState.CLEANUP;
                case ACTIVE -> to == LifecycleState.AWAITING_RESPAWN
                    || to == LifecycleState.CLEANUP
                    || to == LifecycleState.COMPLETED
                    || to == LifecycleState.FAILED;
                case AWAITING_RESPAWN -> to == LifecycleState.TELEPORTING
                    || to == LifecycleState.CLEANUP
                    || to == LifecycleState.FAILED
                    || to == LifecycleState.ACTIVE;
                case CLEANUP -> to == LifecycleState.COMPLETED
                    || to == LifecycleState.FAILED;
                case COMPLETED, FAILED -> false;
            };
        }

        public void setAwaitingRespawnChoice(boolean awaiting) {
            this.awaitingRespawnChoice.set(awaiting);
        }
        /**
         * Atomically set awaitingRespawnChoice from expected value to new value.
         * Returns true if successful (prevents race condition between vanilla respawn and handler).
         */
        public boolean compareAndSetAwaitingRespawnChoice(boolean expect, boolean update) {
            return this.awaitingRespawnChoice.compareAndSet(expect, update);
        }
        public void setRespawnRequested(boolean respawnRequested) { this.respawnRequested.set(respawnRequested); }
        /**
         * Atomically set respawnRequested from expected value to new value.
         */
        public boolean compareAndSetRespawnRequested(boolean expect, boolean update) {
            return this.respawnRequested.compareAndSet(expect, update);
        }
        public boolean canAttemptDimensionRecovery(long nowMs, long cooldownMs) {
            return nowMs - lastDimensionRecoveryMs >= cooldownMs;
        }
        public void markDimensionRecoveryAttempt(long nowMs) {
            this.lastDimensionRecoveryMs = nowMs;
        }
        public boolean canLogConfinement(long nowMs, long cooldownMs) {
            return nowMs - lastConfinementLogMs >= cooldownMs;
        }
        public void markConfinementLog(long nowMs) {
            this.lastConfinementLogMs = nowMs;
        }

        public boolean confirmAbandonRequest(long windowMs) {
            long now = System.currentTimeMillis();
            if (abandonConfirmUntilMs >= now) {
                abandonConfirmUntilMs = 0;
                return true;
            }
            abandonConfirmUntilMs = now + Math.max(0L, windowMs);
            return false;
        }

        public void clearAbandonConfirm() {
            abandonConfirmUntilMs = 0;
        }

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

        /**
         * Check if this session is still being initialized (placeholder state).
         * An initializing session has no arena yet and should not be used for quest logic.
         */
        public boolean isInitializing() {
            return arena == null;
        }
        public boolean isLoadingProtection() { return loadingProtection; }
        public void setLoadingProtection(boolean loadingProtection) { this.loadingProtection = loadingProtection; }

        public void scheduleInstanceStart(ResourceLocation mobId, QuestSettings settings, ResolvedArena resolved, int ticks) {
            this.pendingMobId = mobId;
            this.pendingSettings = settings;
            this.pendingResolved = resolved;
            this.pendingInstanceStartTicks.set(Math.max(0, ticks));
            this.lastTeleportCountdownSeconds = -1;
        }

        public void scheduleBriefing(int ticks) {
            this.pendingBriefingTicks.set(Math.max(0, ticks));
            this.lastBriefingSeconds = -1;
        }

        public boolean isBriefingPending() {
            return pendingBriefingTicks.get() > 0;
        }

        /**
         * Thread-safe countdown tick using atomic decrement.
         * Returns the new value after decrement (or current value if already 0).
         */
        public int tickBriefingCountdown() {
            int current;
            do {
                current = pendingBriefingTicks.get();
                if (current <= 0) return 0;
            } while (!pendingBriefingTicks.compareAndSet(current, current - 1));
            return current - 1;
        }

        public int getLastBriefingSeconds() { return lastBriefingSeconds; }

        public void setLastBriefingSeconds(int seconds) { this.lastBriefingSeconds = seconds; }

        public List<String> getBriefingLines() { return briefingLines; }

        public void setBriefingLines(List<String> briefingLines) {
            this.briefingLines = briefingLines != null ? List.copyOf(briefingLines) : List.of();
        }

        public boolean isInstanceStartPending() {
            return pendingInstanceStartTicks.get() > 0 && pendingMobId != null && pendingSettings != null && pendingResolved != null;
        }

        /**
         * Thread-safe countdown tick using atomic decrement.
         */
        public int tickInstanceStartCountdown() {
            int current;
            do {
                current = pendingInstanceStartTicks.get();
                if (current <= 0) return 0;
            } while (!pendingInstanceStartTicks.compareAndSet(current, current - 1));
            return current - 1;
        }

        public int getLastTeleportCountdownSeconds() { return lastTeleportCountdownSeconds; }
        public void setLastTeleportCountdownSeconds(int seconds) { this.lastTeleportCountdownSeconds = seconds; }

        public @javax.annotation.Nullable ResourceLocation getPendingMobId() { return pendingMobId; }
        public @javax.annotation.Nullable QuestSettings getPendingSettings() { return pendingSettings; }
        public @javax.annotation.Nullable ResolvedArena getPendingResolved() { return pendingResolved; }

        public void clearPendingInstanceStart() {
            pendingInstanceStartTicks.set(0);
            lastTeleportCountdownSeconds = -1;
            pendingMobId = null;
            pendingSettings = null;
            pendingResolved = null;
        }

        public void scheduleWaveStart(int ticks) {
            if (ticks <= 0) {
                pendingWaveStartTicks.set(0);
                lastWaveCountdownSeconds = -1;
                return;
            }
            pendingWaveStartTicks.set(ticks);
            lastWaveCountdownSeconds = -1;
        }

        public boolean isWaveStartPending() { return pendingWaveStartTicks.get() > 0; }

        /**
         * Thread-safe countdown tick using atomic decrement.
         */
        public int tickWaveStartCountdown() {
            int current;
            do {
                current = pendingWaveStartTicks.get();
                if (current <= 0) return 0;
            } while (!pendingWaveStartTicks.compareAndSet(current, current - 1));
            return current - 1;
        }

        public int getLastWaveCountdownSeconds() { return lastWaveCountdownSeconds; }

        public void setLastWaveCountdownSeconds(int seconds) { this.lastWaveCountdownSeconds = seconds; }

        public void clearPendingWaveStart() {
            pendingWaveStartTicks.set(0);
            lastWaveCountdownSeconds = -1;
        }

        public void scheduleSafeWindow(int ticks) {
            if (ticks <= 0) {
                pendingSafeWindowTicks.set(0);
                lastSafeWindowSeconds = -1;
                return;
            }
            pendingSafeWindowTicks.set(ticks);
            lastSafeWindowSeconds = -1;
        }

        public boolean isSafeWindowPending() { return pendingSafeWindowTicks.get() > 0; }

        /**
         * Thread-safe countdown tick using atomic decrement.
         */
        public int tickSafeWindowCountdown() {
            int current;
            do {
                current = pendingSafeWindowTicks.get();
                if (current <= 0) return 0;
            } while (!pendingSafeWindowTicks.compareAndSet(current, current - 1));
            return current - 1;
        }

        public int getLastSafeWindowSeconds() { return lastSafeWindowSeconds; }

        public void setLastSafeWindowSeconds(int seconds) { this.lastSafeWindowSeconds = seconds; }

        public void clearPendingSafeWindow() {
            pendingSafeWindowTicks.set(0);
            lastSafeWindowSeconds = -1;
        }

        public void scheduleBossIntro(int ticks) {
            if (ticks <= 0) {
                pendingBossIntroTicks.set(0);
                lastBossIntroSeconds = -1;
                return;
            }
            pendingBossIntroTicks.set(ticks);
            lastBossIntroSeconds = -1;
        }

        public boolean isBossIntroPending() { return pendingBossIntroTicks.get() > 0; }

        /**
         * Thread-safe countdown tick using atomic decrement.
         */
        public int tickBossIntroCountdown() {
            int current;
            do {
                current = pendingBossIntroTicks.get();
                if (current <= 0) return 0;
            } while (!pendingBossIntroTicks.compareAndSet(current, current - 1));
            return current - 1;
        }

        public int getLastBossIntroSeconds() { return lastBossIntroSeconds; }

        public void setLastBossIntroSeconds(int seconds) { this.lastBossIntroSeconds = seconds; }

        public void clearPendingBossIntro() {
            pendingBossIntroTicks.set(0);
            lastBossIntroSeconds = -1;
        }

        public boolean isRespawnCountdownActive() { return respawnCountdownActive; }

        public void setRespawnCountdownActive(boolean active) { this.respawnCountdownActive = active; }

        public boolean isPartySpectator() { return partySpectator; }

        public void setPartySpectator(boolean partySpectator) { this.partySpectator = partySpectator; }

        public void clearAllSequences() {
            clearPendingWaveStart();
            clearPendingSafeWindow();
            clearPendingBossIntro();
            clearPendingInstanceStart();
            pendingBriefingTicks.set(0);
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

        public boolean isPracticeMode() { return practiceMode; }

        public void setPracticeMode(boolean practiceMode) { this.practiceMode = practiceMode; }

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

        // Config override methods (SESSION scope)
        public boolean isHost(UUID uuid) {
            // For solo quests, the player is always the host
            // For party quests, check if this is the party leader (playerId is the session owner)
            return playerId.equals(uuid);
        }

        public void setConfigOverride(String key, String value) {
            if (key != null && value != null) {
                configOverrides.put(key, value);
            }
        }

        public @javax.annotation.Nullable String getConfigOverride(String key) {
            return configOverrides.get(key);
        }

        public Map<String, String> getConfigOverrides() {
            return Collections.unmodifiableMap(configOverrides);
        }

        public void clearConfigOverrides() {
            configOverrides.clear();
        }

        // ========== Mob Pool Config ==========

        /**
         * Set the mob pool configuration for this session.
         * @param config The mob pool configuration, or null to clear
         */
        public void setMobPoolConfig(@javax.annotation.Nullable com.devmod.endurance.config.EnduranceMobPoolConfig config) {
            this.mobPoolConfig = config;
        }

        /**
         * Get the mob pool configuration for this session.
         * @return The mob pool configuration, or null if not set
         */
        public @javax.annotation.Nullable com.devmod.endurance.config.EnduranceMobPoolConfig getMobPoolConfig() {
            return mobPoolConfig;
        }

        /**
         * Check if this session has a custom mob pool configuration.
         */
        public boolean hasMobPoolConfig() {
            return mobPoolConfig != null && mobPoolConfig.hasModifications();
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
