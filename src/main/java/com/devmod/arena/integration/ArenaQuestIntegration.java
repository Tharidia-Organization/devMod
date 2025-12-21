package com.devmod.arena.integration;

import com.devmod.arena.api.ArenaHandle;
import com.devmod.arena.builder.ArenaBuilder;
import com.devmod.arena.gate.InstanceOnlyGate;
import com.devmod.arena.policy.ArenaPolicy;
import com.devmod.arena.policy.ArenaPolicyRegistry;
import com.devmod.arena.policy.PolicyResolver;
import com.devmod.arena.policy.ResolveContext;
import com.devmod.arena.policy.ResolvedArena;
import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.registry.ArenaTemplateRegistry;
import com.devmod.arena.telemetry.ArenaTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Integration layer between Arena system and Quest system.
 *
 * <p>Implements:
 * <ul>
 *   <li>DD15: ArenaHandle as standard return type</li>
 *   <li>Quest flow integration for Endurance and other quest types</li>
 *   <li>Async arena preparation</li>
 *   <li>Session lifecycle management</li>
 * </ul>
 *
 * <p>Call-sites to integrate:
 * <ul>
 *   <li>QuestStartSequence.prepareArena() - use ArenaHandle</li>
 *   <li>EnduranceQuestManager.startPreparedQuest() - accept ArenaHandle</li>
 *   <li>InstanceArenaManager.startInstanceQuestForParty() - return ArenaHandle</li>
 *   <li>WaveManager.spawnWave() - use handle.mobSpawnPositions()</li>
 *   <li>EndurancePlayerStateManager.teleportToArena() - use handle.primaryPlayerSpawn()</li>
 *   <li>ArenaCleanupTask - accept ArenaHandle</li>
 * </ul>
 */
@SuppressWarnings("unused") // templateRegistry and policyRegistry reserved for future query APIs
public class ArenaQuestIntegration {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArenaQuestIntegration.class);

    private final ArenaTemplateRegistry templateRegistry;
    private final ArenaPolicyRegistry policyRegistry;
    private final PolicyResolver policyResolver;
    private final ArenaBuilder arenaBuilder;
    private final ArenaTelemetry telemetry;
    private final Executor asyncExecutor;
    @Nullable
    private final Supplier<com.devmod.arena.config.ArenaTemplateConfig.ConfigSnapshot> configSnapshotSupplier;

    // Active sessions: sessionId -> SessionContext
    private final ConcurrentHashMap<UUID, SessionContext> activeSessions = new ConcurrentHashMap<>();

    // Prepared arenas awaiting use
    private final ConcurrentHashMap<UUID, ArenaHandle> preparedArenas = new ConcurrentHashMap<>();

    // Event listeners
    private final List<Consumer<ArenaEvent>> eventListeners = new ArrayList<>();

    public ArenaQuestIntegration(
            ArenaTemplateRegistry templateRegistry,
            ArenaPolicyRegistry policyRegistry,
            PolicyResolver policyResolver,
            ArenaBuilder arenaBuilder,
            ArenaTelemetry telemetry,
            Executor asyncExecutor) {
        this(templateRegistry, policyRegistry, policyResolver, arenaBuilder, telemetry, asyncExecutor,
            (Supplier<com.devmod.arena.config.ArenaTemplateConfig.ConfigSnapshot>) null);
    }

    public ArenaQuestIntegration(
            ArenaTemplateRegistry templateRegistry,
            ArenaPolicyRegistry policyRegistry,
            PolicyResolver policyResolver,
            ArenaBuilder arenaBuilder,
            ArenaTelemetry telemetry,
            Executor asyncExecutor,
            com.devmod.arena.config.ArenaTemplateConfig.ConfigSnapshot configSnapshot) {
        this(templateRegistry, policyRegistry, policyResolver, arenaBuilder, telemetry, asyncExecutor,
            configSnapshot != null ? () -> configSnapshot : null);
    }

    public ArenaQuestIntegration(
            ArenaTemplateRegistry templateRegistry,
            ArenaPolicyRegistry policyRegistry,
            PolicyResolver policyResolver,
            ArenaBuilder arenaBuilder,
            ArenaTelemetry telemetry,
            Executor asyncExecutor,
            @Nullable Supplier<com.devmod.arena.config.ArenaTemplateConfig.ConfigSnapshot> configSnapshotSupplier) {
        this.templateRegistry = Objects.requireNonNull(templateRegistry, "templateRegistry");
        this.policyRegistry = Objects.requireNonNull(policyRegistry, "policyRegistry");
        this.policyResolver = Objects.requireNonNull(policyResolver, "policyResolver");
        this.arenaBuilder = Objects.requireNonNull(arenaBuilder, "arenaBuilder");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
        this.asyncExecutor = Objects.requireNonNull(asyncExecutor, "asyncExecutor");
        this.configSnapshotSupplier = configSnapshotSupplier;

        // Load policies from default path if present (config/devmod/arena_policies)
        loadPoliciesFromDisk();

        LOGGER.info("ArenaQuestIntegration initialized");
    }

    // ========== Quest Start Sequence ==========

    /**
     * Prepares an arena for a quest based on quest context.
     * This is the main entry point for quest system integration.
     *
     * @param context Quest context with player, quest type, etc.
     * @return CompletableFuture with prepared ArenaHandle
     */
    public CompletableFuture<PrepareResult> prepareArena(QuestContext context) {
        UUID sessionId = UUID.randomUUID();
        Instant startTime = Instant.now();

        LOGGER.info("Preparing arena for quest: type={}, players={}, sessionId={}",
            context.questType(), context.playerCount(), sessionId);

        return CompletableFuture.supplyAsync(() -> {
            try {
                InstanceOnlyGate gate = resolveInstanceGate();
                if (gate != null) {
                    InstanceOnlyGate.Result gateResult = gate.checkInstanceId(
                        context.instanceId(),
                        "ArenaQuestIntegration.prepareArena"
                    );
                    if (gateResult == InstanceOnlyGate.Result.BLOCKED) {
                        return PrepareResult.failed(sessionId, "Instance-only mode: provide instanceId");
                    }
                    if (gateResult == InstanceOnlyGate.Result.ALLOWED_DEBUG_ONLY) {
                        LOGGER.warn("[INSTANCE_GATE] Debug-only prepareArena allowed without instanceId");
                    }
                }

                // Step 1: Resolve policy and template
                ResolveContext resolveCtx = ResolveContext.builder(context.leaderId())
                    .questType(context.questType())
                    .difficulty(context.difficulty())
                    .playerCount(context.playerCount())
                    .mobType(context.mobTypes() != null && !context.mobTypes().isEmpty()
                        ? context.mobTypes().get(0) : null)
                    .tags(context.tags() != null ? new java.util.HashSet<>(context.tags()) : Set.of())
                    .build();

                ResolvedArena resolved = policyResolver.resolve(resolveCtx);
                if (resolved == null) {
                    return PrepareResult.failed(sessionId, "No matching template found");
                }

                LOGGER.debug("Resolved template '{}' with policy '{}'",
                    resolved.template().id(), resolved.policy().id());

                // Step 2: Calculate origin (from context or default)
                Integer prefX = context.preferredOriginX();
                Integer prefY = context.preferredOriginY();
                Integer prefZ = context.preferredOriginZ();
                int originX = prefX != null ? prefX.intValue() : 0;
                int originY = prefY != null ? prefY.intValue() : 64;
                int originZ = prefZ != null ? prefZ.intValue() : 0;

                // Step 3: Build arena (with policy context for telemetry)
                ArenaBuilder.BuildResult buildResult = arenaBuilder.build(
                    resolved.template(),
                    resolved.policy().id(),
                    resolved.policy().version(),
                    originX, originY, originZ
                );

                if (!buildResult.success()) {
                    LOGGER.error("Arena build failed: {}", buildResult.errorMessage());
                    return PrepareResult.failed(sessionId, buildResult.errorMessage());
                }

                // Step 4: Create ArenaHandle
                ArenaHandle handle = createHandle(
                    buildResult,
                    resolved,
                    context.instanceId(),
                    originX, originY, originZ
                );

                // Step 5: Store prepared arena
                preparedArenas.put(handle.arenaId(), handle);

                // Step 6: Create session context
                SessionContext session = new SessionContext(
                    sessionId,
                    handle,
                    context,
                    startTime,
                    SessionState.PREPARED
                );
                activeSessions.put(sessionId, session);

                // Step 7: Emit telemetry
                Duration prepTime = Duration.between(startTime, Instant.now());
                telemetry.emit("arena.quest.prepared", Map.of(
                    "sessionId", sessionId.toString(),
                    "templateId", resolved.template().id(),
                    "policyId", resolved.policy().id(),
                    "questType", context.questType(),
                    "playerCount", context.playerCount(),
                    "prepTimeMs", prepTime.toMillis()
                ));

                // Notify listeners
                fireEvent(new ArenaEvent(ArenaEventType.PREPARED, sessionId, handle));

                LOGGER.info("Arena prepared: sessionId={}, arenaId={}, template={}",
                    sessionId, handle.arenaId(), resolved.template().id());

                return PrepareResult.success(sessionId, handle);

            } catch (Exception e) {
                LOGGER.error("Arena preparation failed", e);
                telemetry.emit("arena.quest.prepare_failed", Map.of(
                    "sessionId", sessionId.toString(),
                    "error", e.getMessage() != null ? e.getMessage() : "Unknown error"
                ));
                return PrepareResult.failed(sessionId, e.getMessage());
            }
        }, asyncExecutor);
    }

    @Nullable
    private InstanceOnlyGate resolveInstanceGate() {
        Supplier<com.devmod.arena.config.ArenaTemplateConfig.ConfigSnapshot> snapshotSupplier = configSnapshotSupplier;
        if (snapshotSupplier == null) {
            return null;
        }
        com.devmod.arena.config.ArenaTemplateConfig.ConfigSnapshot snapshot = snapshotSupplier.get();
        if (snapshot == null) {
            return null;
        }
        return new InstanceOnlyGate(snapshot, telemetry);
    }

    /**
     * Starts a quest session using a prepared arena.
     *
     * @param sessionId The session ID from prepareArena
     * @return true if session started successfully
     */
    public boolean startSession(UUID sessionId) {
        SessionContext session = activeSessions.get(sessionId);
        if (session == null) {
            LOGGER.warn("Session not found: {}", sessionId);
            return false;
        }

        if (session.state() != SessionState.PREPARED) {
            LOGGER.warn("Session {} not in PREPARED state: {}", sessionId, session.state());
            return false;
        }

        // Update state
        SessionContext updated = session.withState(SessionState.ACTIVE);
        activeSessions.put(sessionId, updated);

        telemetry.emit("arena.quest.started", Map.of(
            "sessionId", sessionId.toString(),
            "arenaId", session.handle().arenaId().toString(),
            "templateId", session.handle().templateId()
        ));

        fireEvent(new ArenaEvent(ArenaEventType.STARTED, sessionId, session.handle()));

        LOGGER.info("Quest session started: {}", sessionId);
        return true;
    }

    /**
     * Ends a quest session with the given outcome.
     *
     * @param sessionId The session ID
     * @param outcome   The quest outcome (WIN, LOSS, ABANDONED, etc.)
     * @param stats     Optional session statistics
     */
    public void endSession(UUID sessionId, String outcome, @Nullable Map<String, Object> stats) {
        SessionContext session = activeSessions.remove(sessionId);
        if (session == null) {
            LOGGER.warn("Session not found for end: {}", sessionId);
            return;
        }

        // Remove prepared arena
        preparedArenas.remove(session.handle().arenaId());

        Duration duration = Duration.between(session.startedAt(), Instant.now());

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("sessionId", sessionId.toString());
        eventData.put("arenaId", session.handle().arenaId().toString());
        eventData.put("templateId", session.handle().templateId());
        eventData.put("outcome", outcome);
        eventData.put("durationMs", duration.toMillis());
        if (stats != null) {
            eventData.putAll(stats);
        }

        telemetry.emit("arena.quest.ended", eventData);

        fireEvent(new ArenaEvent(ArenaEventType.ENDED, sessionId, session.handle()));

        LOGGER.info("Quest session ended: sessionId={}, outcome={}, duration={}s",
            sessionId, outcome, duration.toSeconds());
    }

    private void loadPoliciesFromDisk() {
        Path policyDir = Path.of("config/devmod/arena_policies");
        if (!Files.isDirectory(policyDir)) {
            return;
        }
        var result = policyRegistry.loadAllSources(policyDir);
        if (!result.errors().isEmpty()) {
            result.errors().forEach(err -> LOGGER.error("Policy load error: {}", err));
        } else {
            LOGGER.info("Loaded {} arena policies from {}", result.policies().size(), policyDir);
        }
    }

    // ========== Handle Creation ==========

    private ArenaHandle createHandle(
            ArenaBuilder.BuildResult buildResult,
            ResolvedArena resolved,
            UUID instanceId,
            int originX, int originY, int originZ) {

        ArenaTemplate template = resolved.template();

        // Calculate bounds
        Integer sx = template.sizeX();
        Integer sz = template.sizeZ();
        int sizeX = sx != null ? sx.intValue() : template.size();
        int sizeZ = sz != null ? sz.intValue() : template.size();
        int height = template.walls() != null && template.walls().enabled()
            ? template.walls().height()
            : 10; // Default height

        int halfX = sizeX / 2;
        int halfZ = sizeZ / 2;

        ArenaHandle.AABB bounds = new ArenaHandle.AABB(
            originX - halfX,
            originY,
            originZ - halfZ,
            originX + halfX,
            originY + height,
            originZ + halfZ
        );

        // Extract spawn positions from template
        List<ArenaHandle.BlockPos> playerSpawns = extractPlayerSpawns(template, originX, originY, originZ);
        List<ArenaHandle.BlockPos> mobSpawns = extractMobSpawns(template, originX, originY, originZ);

        return ArenaHandle.builder()
            .arenaId(buildResult.arenaId())
            .instanceId(instanceId != null ? instanceId : UUID.randomUUID())
            .templateId(template.id())
            .templateVersion(template.version())
            .policyId(resolved.policy().id())
            .policyVersion(resolved.policy().version())
            .bounds(bounds)
            .origin(originX, originY, originZ)
            .playerSpawnPositions(playerSpawns)
            .mobSpawnPositions(mobSpawns)
            .build();
    }

    private List<ArenaHandle.BlockPos> extractPlayerSpawns(
            ArenaTemplate template, int originX, int originY, int originZ) {

        List<ArenaHandle.BlockPos> spawns = new ArrayList<>();

        if (template.spawnSlots() != null) {
            for (ArenaTemplate.SpawnSlot slot : template.spawnSlots()) {
                if (slot.tags() != null && (slot.tags().contains("player") || slot.tags().contains("team"))) {
                    int[] pos = slot.pos();
                    if (pos == null || pos.length != 3) continue;
                    int x = originX + pos[0];
                    int y = resolveSpawnY(slot, template, originY);
                    int z = originZ + pos[2];
                    spawns.add(new ArenaHandle.BlockPos(x, y, z));
                }
            }
        }

        // Fallback: center spawn
        if (spawns.isEmpty()) {
            int floorY = template.floor() != null ? template.floor().y() : originY;
            spawns.add(new ArenaHandle.BlockPos(originX, floorY + 1, originZ));
        }

        return spawns;
    }

    private List<ArenaHandle.BlockPos> extractMobSpawns(
            ArenaTemplate template, int originX, int originY, int originZ) {

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

        List<ArenaTemplate.SpawnSlot> selected = selectByStrategy(template, mobSlots);
        List<ArenaHandle.BlockPos> spawns = new ArrayList<>(selected.size());
        for (ArenaTemplate.SpawnSlot slot : selected) {
            int[] pos = slot.pos();
            if (pos == null || pos.length != 3) continue;
            int x = originX + pos[0];
            int y = resolveSpawnY(slot, template, originY);
            int z = originZ + pos[2];
            spawns.add(new ArenaHandle.BlockPos(x, y, z));
        }
        return spawns;
    }

    private int resolveSpawnY(ArenaTemplate.SpawnSlot slot, ArenaTemplate template, int originY) {
        int baseY = slot.pos() != null && slot.pos().length == 3 ? slot.pos()[1] : 0;
        if (slot.yMode() == ArenaTemplate.SpawnSlot.YMode.RELATIVE_TO_FLOOR && template.floor() != null) {
            return template.floor().y() + baseY;
        }
        return baseY;
    }

    private List<ArenaTemplate.SpawnSlot> selectByStrategy(ArenaTemplate template, List<ArenaTemplate.SpawnSlot> mobSlots) {
        ArenaTemplate.MobSpawnStrategy strategy = template.mobSpawnStrategy() != null
            ? template.mobSpawnStrategy()
            : ArenaTemplate.MobSpawnStrategy.DISTRIBUTED;

        // Center reference for distances
        double centerX = template.origin() != null ? template.origin().x() : 0;
        double centerZ = template.origin() != null ? template.origin().z() : 0;

        switch (strategy) {
            case CLUSTERED -> {
                List<ArenaTemplate.SpawnSlot> centered = mobSlots.stream()
                    .filter(s -> s.tags() != null && s.tags().contains("center"))
                    .toList();
                if (!centered.isEmpty()) {
                    telemetry.emit("arena.spawn.strategy_used", Map.of(
                        "templateId", template.id(),
                        "strategy", "clustered",
                        "slots", centered.size()
                    ));
                    return centered;
                }
                // fallback: nearest 4 to center
                List<ArenaTemplate.SpawnSlot> nearest = new ArrayList<>(mobSlots);
                nearest.sort(Comparator.comparingDouble(s -> horizontalDistance(s, centerX, centerZ)));
                List<ArenaTemplate.SpawnSlot> picked = nearest.subList(0, Math.min(4, nearest.size()));
                telemetry.emit("arena.spawn.strategy_used", Map.of(
                    "templateId", template.id(),
                    "strategy", "clustered_fallback",
                    "slots", picked.size()
                ));
                return picked;
            }
            case CORNERS -> {
                List<ArenaTemplate.SpawnSlot> corners = mobSlots.stream()
                    .filter(s -> s.tags() != null && s.tags().contains("corner"))
                    .toList();
                if (corners.size() >= 4) {
                    telemetry.emit("arena.spawn.strategy_used", Map.of(
                        "templateId", template.id(),
                        "strategy", "corners",
                        "slots", corners.size()
                    ));
                    return corners;
                }
                List<ArenaTemplate.SpawnSlot> farthest = new ArrayList<>(mobSlots);
                farthest.sort(Comparator.comparingDouble((ArenaTemplate.SpawnSlot s) -> horizontalDistance(s, centerX, centerZ)).reversed());
                List<ArenaTemplate.SpawnSlot> picked = farthest.subList(0, Math.min(4, farthest.size()));
                telemetry.emit("arena.spawn.strategy_used", Map.of(
                    "templateId", template.id(),
                    "strategy", "corners_fallback",
                    "slots", picked.size()
                ));
                return picked;
            }
            case RING -> {
                int sizeX = Objects.requireNonNullElse(template.sizeX(), template.size());
                int sizeZ = Objects.requireNonNullElse(template.sizeZ(), template.size());
                double requiredRadius = Math.max(sizeX, sizeZ) / 4.0;
                List<ArenaTemplate.SpawnSlot> ring = mobSlots.stream()
                    .filter(s -> horizontalDistance(s, centerX, centerZ) >= requiredRadius)
                    .toList();
                if (!ring.isEmpty()) {
                    telemetry.emit("arena.spawn.strategy_used", Map.of(
                        "templateId", template.id(),
                        "strategy", "ring",
                        "slots", ring.size(),
                        "radius", requiredRadius
                    ));
                    return ring;
                }
                telemetry.emit("arena.spawn.strategy_used", Map.of(
                    "templateId", template.id(),
                    "strategy", "ring_fallback_distributed",
                    "radius", requiredRadius
                ));
                return mobSlots;
            }
            default -> {
                telemetry.emit("arena.spawn.strategy_used", Map.of(
                    "templateId", template.id(),
                    "strategy", "distributed",
                    "slots", mobSlots.size()
                ));
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

    // ========== Query Methods ==========

    /**
     * Gets a prepared arena by ID.
     */
    public Optional<ArenaHandle> getPreparedArena(UUID arenaId) {
        return Optional.ofNullable(preparedArenas.get(arenaId));
    }

    /**
     * Gets an active session by ID.
     */
    public Optional<SessionContext> getSession(UUID sessionId) {
        return Optional.ofNullable(activeSessions.get(sessionId));
    }

    /**
     * Gets the session for an arena.
     */
    public Optional<SessionContext> getSessionByArena(UUID arenaId) {
        return activeSessions.values().stream()
            .filter(s -> s.handle().arenaId().equals(arenaId))
            .findFirst();
    }

    /**
     * Gets all active sessions.
     */
    public Collection<SessionContext> getActiveSessions() {
        return Collections.unmodifiableCollection(activeSessions.values());
    }

    /**
     * Gets the count of active sessions.
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    // ========== Event System ==========

    /**
     * Registers an event listener.
     */
    public void addEventListener(Consumer<ArenaEvent> listener) {
        eventListeners.add(listener);
    }

    /**
     * Removes an event listener.
     */
    public void removeEventListener(Consumer<ArenaEvent> listener) {
        eventListeners.remove(listener);
    }

    private void fireEvent(ArenaEvent event) {
        for (Consumer<ArenaEvent> listener : eventListeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                LOGGER.error("Error in arena event listener", e);
            }
        }
    }

    // ========== Cleanup ==========

    /**
     * Cleans up stale sessions older than the given duration.
     *
     * @param maxAge Maximum session age
     * @return Number of sessions cleaned up
     */
    public int cleanupStaleSessions(Duration maxAge) {
        Instant cutoff = Instant.now().minus(maxAge);
        int cleaned = 0;

        Iterator<Map.Entry<UUID, SessionContext>> it = activeSessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, SessionContext> entry = it.next();
            if (entry.getValue().startedAt().isBefore(cutoff)) {
                it.remove();
                preparedArenas.remove(entry.getValue().handle().arenaId());
                cleaned++;

                telemetry.emit("arena.quest.stale_cleanup", Map.of(
                    "sessionId", entry.getKey().toString(),
                    "age", Duration.between(entry.getValue().startedAt(), Instant.now()).toMinutes()
                ));
            }
        }

        if (cleaned > 0) {
            LOGGER.info("Cleaned up {} stale sessions", cleaned);
        }

        return cleaned;
    }

    // ========== Records ==========

    /**
     * Quest context for arena preparation.
     */
    public record QuestContext(
        UUID leaderId,
        String questType,
        String difficulty,
        int playerCount,
        List<String> mobTypes,
        List<String> tags,
        @Nullable UUID instanceId,
        @Nullable Integer preferredOriginX,
        @Nullable Integer preferredOriginY,
        @Nullable Integer preferredOriginZ
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private UUID leaderId;
            private String questType = "endurance";
            private String difficulty = "normal";
            private int playerCount = 1;
            private List<String> mobTypes = List.of();
            private List<String> tags = List.of();
            private UUID instanceId;
            private Integer preferredOriginX;
            private Integer preferredOriginY;
            private Integer preferredOriginZ;

            public Builder leaderId(UUID leaderId) { this.leaderId = leaderId; return this; }
            public Builder questType(String questType) { this.questType = questType; return this; }
            public Builder difficulty(String difficulty) { this.difficulty = difficulty; return this; }
            public Builder playerCount(int playerCount) { this.playerCount = playerCount; return this; }
            public Builder mobTypes(List<String> mobTypes) { this.mobTypes = mobTypes; return this; }
            public Builder tags(List<String> tags) { this.tags = tags; return this; }
            public Builder instanceId(UUID instanceId) { this.instanceId = instanceId; return this; }
            public Builder origin(int x, int y, int z) {
                this.preferredOriginX = x;
                this.preferredOriginY = y;
                this.preferredOriginZ = z;
                return this;
            }

            public QuestContext build() {
                return new QuestContext(
                    leaderId, questType, difficulty, playerCount,
                    mobTypes, tags, instanceId,
                    preferredOriginX, preferredOriginY, preferredOriginZ
                );
            }
        }
    }

    /**
     * Request for policy resolution.
     */
    public record ResolveRequest(
        UUID playerId,
        String questType,
        String difficulty,
        int playerCount,
        List<String> mobTypes,
        List<String> tags
    ) {}

    /**
     * Result of arena preparation.
     */
    public record PrepareResult(
        boolean success,
        UUID sessionId,
        @Nullable ArenaHandle handle,
        @Nullable String errorMessage
    ) {
        public static PrepareResult success(UUID sessionId, ArenaHandle handle) {
            return new PrepareResult(true, sessionId, handle, null);
        }

        public static PrepareResult failed(UUID sessionId, String error) {
            return new PrepareResult(false, sessionId, null, error);
        }
    }

    /**
     * Session state enum.
     */
    public enum SessionState {
        PREPARED,
        ACTIVE,
        PAUSED,
        ENDING
    }

    /**
     * Active session context.
     */
    public record SessionContext(
        UUID sessionId,
        ArenaHandle handle,
        QuestContext questContext,
        Instant startedAt,
        SessionState state
    ) {
        public SessionContext withState(SessionState newState) {
            return new SessionContext(sessionId, handle, questContext, startedAt, newState);
        }
    }

    /**
     * Arena event types.
     */
    public enum ArenaEventType {
        PREPARED,
        STARTED,
        PAUSED,
        RESUMED,
        ENDED,
        CLEANUP
    }

    /**
     * Arena event for listeners.
     */
    public record ArenaEvent(
        ArenaEventType type,
        UUID sessionId,
        ArenaHandle handle
    ) {}
}
