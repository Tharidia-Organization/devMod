package com.devmod.endurance;

import java.util.ArrayList;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import com.devmod.DevMod;
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
import com.devmod.endurance.services.InstanceServicesFacade;
import com.devmod.mob.EnhancedMobRequirements;
import com.devmod.mob.EnhancedMobRequirementsRegistry;
import com.devmod.mob.MobRequirements;
import com.devmod.mob.MobRequirementsRegistry;
import com.devmod.runtime.InstanceData;
import com.devmod.runtime.InstanceManager;

/**
 * Manages arena template resolution, building, fallback strategies, and spawn extraction.
 * Extracted from EnduranceQuestManager to separate arena infrastructure from quest lifecycle.
 */
class ArenaSetupManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArenaSetupManager.class);

    private static final String FALLBACK_TEMPLATE_ID = "default_flat_64";
    private static final CircuitBreaker BUILD_FALLBACK_CIRCUIT = new CircuitBreaker();
    private static final FallbackMetrics BUILD_FALLBACK_METRICS = new FallbackMetrics();
    static final long INSTANCE_CREATION_TIMEOUT_SECONDS = 30;

    // Arena template system integration
    private volatile ArenaTemplateRegistry arenaTemplateRegistry;
    private volatile ArenaPolicyRegistry arenaPolicyRegistry;
    private volatile PolicyResolver policyResolver;
    private volatile OverrideManager overrideManager;
    private volatile @Nullable ForceTemplateCapability forceTemplateCapability;
    private volatile ArenaTelemetry arenaTelemetry;
    private volatile ArenaTemplateConfig arenaTemplateConfig;
    private volatile ArenaTemplateConfig.ConfigSnapshot arenaConfigSnapshot;
    private final PrebuildPoolManager prebuildPoolManager = new PrebuildPoolManager();
    final AsyncArenaBuildCoordinator asyncBuildCoordinator =
        new AsyncArenaBuildCoordinator(() -> arenaConfigSnapshot);

    // ========== Initialization ==========

    void initArenaTemplateIntegration(java.nio.file.Path configDir) {
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

        java.nio.file.Path policyDir = configDir.resolve("arena_policies");
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

    void applyArenaConfig(ArenaTemplateConfig config) {
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

    ArenaTemplateConfig getArenaTemplateConfig() {
        return arenaTemplateConfig;
    }

    void setArenaTemplateConfig(ArenaTemplateConfig config) {
        this.arenaTemplateConfig = config;
    }

    ArenaTemplateConfig.ConfigSnapshot getArenaConfigSnapshot() {
        return arenaConfigSnapshot;
    }

    void setArenaConfigSnapshot(ArenaTemplateConfig.ConfigSnapshot snapshot) {
        this.arenaConfigSnapshot = snapshot;
    }

    ArenaTelemetry getArenaTelemetry() {
        return arenaTelemetry;
    }

    void setArenaTelemetry(ArenaTelemetry telemetry) {
        this.arenaTelemetry = telemetry;
    }

    PrebuildPoolManager getPrebuildPoolManager() {
        return prebuildPoolManager;
    }

    void tickAsyncBuilds(net.minecraft.server.MinecraftServer server) {
        if (server == null) {
            return;
        }
        asyncBuildCoordinator.onServerTick(server);
    }

    void setForceTemplateCapability(@Nullable ForceTemplateCapability capability) {
        this.forceTemplateCapability = capability;
    }

    @Nullable
    PolicyResolver getPolicyResolver() {
        return policyResolver;
    }

    @Nullable
    ArenaPolicyRegistry getArenaPolicyRegistry() {
        return arenaPolicyRegistry;
    }

    OverrideManager getOverrideManager() {
        return overrideManager;
    }

    void shutdown() {
        prebuildPoolManager.shutdown();
    }

    // ========== Template System Readiness ==========

    boolean shouldUseTemplateSystem() {
        return arenaTemplateConfig != null
            && arenaTemplateConfig.arenaTemplateEnabled()
            && arenaTemplateRegistry != null
            && policyResolver != null;
    }

    @Nullable
    String getTemplateSystemReadinessError(boolean useInstanceDimensions) {
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

    // ========== Arena Resolution ==========

    ResolvedArena resolveArenaTemplate(UUID playerId, ResourceLocation mobId,
                                       EnduranceQuestManager.QuestSettings settings,
                                       @Nullable net.minecraft.server.MinecraftServer server) {
        if (policyResolver == null) {
            return null;
        }
        var mobConfig = EnduranceQuestRegistry.INSTANCE.getMobConfig(mobId).orElse(null);
        String questType = resolveQuestTypeLabel(settings, mobConfig);
        String difficulty = resolveDifficultyLabel(settings, mobConfig);
        Set<String> tags = resolveTags(settings, mobConfig);

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

        // Priority 1: forceTemplateId from settings
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

    String resolveQuestTypeLabel(EnduranceQuestManager.QuestSettings settings,
                                  EnduranceQuestRegistry.MobQuestConfig mobConfig) {
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

    String resolveDifficultyLabel(EnduranceQuestManager.QuestSettings settings,
                                   EnduranceQuestRegistry.MobQuestConfig mobConfig) {
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

    private Set<String> resolveTags(EnduranceQuestManager.QuestSettings settings,
                                    EnduranceQuestRegistry.MobQuestConfig mobConfig) {
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

    // ========== Arena Building ==========

    com.devmod.arena.builder.TemplateArenaBuilder createTemplateBuilder(ServerLevel level) {
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

    boolean shouldBuildAsync(ArenaTemplate template) {
        if (template == null || template.buildSettings() == null) {
            return false;
        }
        return template.buildSettings().buildPriority() == ArenaTemplate.BuildSettings.Priority.ASYNC;
    }

    record BuildAttemptResult(
        ResolvedArena resolved,
        OriginResolution origin,
        ArenaBuilder.BuildResult result,
        boolean fallbackAttempted,
        boolean fallbackSucceeded
    ) {}

    BuildAttemptResult buildWithFallback(com.devmod.arena.builder.TemplateArenaBuilder builder,
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

    @Nullable BuildAttemptResult attemptFallbackOnly(
        com.devmod.arena.builder.TemplateArenaBuilder builder,
        ResolvedArena primary,
        String context,
        @Nullable String primaryError) {
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

    private @Nullable ResolvedArena resolveFallbackArena(ResolvedArena primary) {
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

    // ========== Origin and Spawn ==========

    record OriginResolution(int originX, int originY, int originZ, int centerX, int centerZ) {}

    OriginResolution resolveTemplateOrigin(ArenaTemplate template) {
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

    private int resolveTemplateSize(ArenaTemplate template, Integer size) {
        return Objects.requireNonNullElse(size, template.size());
    }

    List<ArenaHandle.BlockPos> extractPlayerSpawns(ArenaTemplate template,
                                                    OriginResolution origin,
                                                    @Nullable ServerLevel level) {
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

    List<ArenaHandle.BlockPos> extractMobSpawns(ArenaTemplate template,
                                                 OriginResolution origin,
                                                 @Nullable ServerLevel level) {
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

    private double horizontalDistance(ArenaTemplate.SpawnSlot slot, double centerX, double centerZ) {
        int[] pos = slot.pos();
        if (pos == null || pos.length != 3) return Double.MAX_VALUE;
        double dx = pos[0] - centerX;
        double dz = pos[2] - centerZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ========== Arena Handle Helpers ==========

    ArenaHandle createArenaHandle(ArenaBuilder.BuildResult buildResult,
                                  ResolvedArena resolved,
                                  UUID instanceId,
                                  OriginResolution origin,
                                  @Nullable ServerLevel level) {
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

    boolean isHandleValid(@Nullable ArenaHandle handle) {
        if (handle == null) {
            return false;
        }
        boolean hasPlayerSpawns = handle.playerSpawnPositions() != null && !handle.playerSpawnPositions().isEmpty();
        boolean hasMobSpawns = handle.mobSpawnPositions() != null && !handle.mobSpawnPositions().isEmpty();
        return hasPlayerSpawns && hasMobSpawns;
    }

    ArenaContext createArenaAdapter(ServerLevel level, ArenaHandle handle) {
        return new ArenaContext(level, handle);
    }

    ArenaHandle.AABB computeHandleBounds(ArenaTemplate template, OriginResolution origin) {
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

    void updateInstanceArenaMetadata(InstanceData instance,
                                     ArenaTemplate template,
                                     @Nullable ArenaHandle handle,
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

    Map<net.minecraft.core.BlockPos, ArenaTemplate.SpawnSlot> buildPlayerSpawnSlotMap(
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

    // ========== Telemetry Helpers ==========

    void emitLegacyCall(String reason, String context, @Nullable ServerLevel level,
                        boolean useInstanceDimensions) {
        if (arenaTelemetry == null) {
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("caller", EnduranceQuestManager.class.getName());
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

    void emitGateFailure(String reason, @Nullable ResolvedArena resolved) {
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

    private void emitBuildFallbackAttempt(ResolvedArena primary,
                                          ResolvedArena fallback,
                                          String context,
                                          @Nullable String primaryError) {
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

    String handleBuildAbort(ResolvedArena resolved,
                            String context,
                            String technicalMessage,
                            @Nullable Throwable cause,
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

    private void emitSpawnStrategyTelemetry(String templateId, String strategy, int slots, @Nullable Double radius) {
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

    // ========== Cleanup ==========

    int cleanupExpiredOverrides() {
        int cleaned = 0;
        if (overrideManager != null) {
            cleaned = overrideManager.cleanupExpiredOverrides();
        }
        return cleaned;
    }
}
