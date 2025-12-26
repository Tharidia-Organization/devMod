package com.devmod.arena.policy;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.arena.config.ArenaTemplateConfig;
import com.devmod.arena.override.OverrideManager;
import com.devmod.arena.override.TemplateOverride;
import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.registry.ArenaTemplateRegistry;
import com.devmod.arena.telemetry.ArenaTelemetry;
public class PolicyResolver implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyResolver.class);

    // DD4: Weight configuration (taratura via telemetry)
    private static final int WEIGHT_MOB_MATCH = 5;
    private static final int WEIGHT_QUEST_TYPE = 4;
    private static final int WEIGHT_DIFFICULTY = 3;
    private static final int WEIGHT_PLAYER_COUNT = 2;
    private static final int WEIGHT_TAGS = 1;
    private static final double MIN_POLICY_WEIGHT = 0.1;
    private static final double MAX_POLICY_WEIGHT = 10.0;

    // DD6: Per-player lock map
    private final ConcurrentHashMap<UUID, LockEntry> playerLocks = new ConcurrentHashMap<>();
    private final Set<String> weightClampEmitted = ConcurrentHashMap.newKeySet();

    // Policy storage
    private final ConcurrentHashMap<String, ArenaPolicy> policies = new ConcurrentHashMap<>();

    private final ArenaTemplateRegistry templateRegistry;
    private final ArenaTelemetry telemetry;
    private final OverrideManager overrideManager;
    private final VersionCompatibilityChecker versionChecker = new VersionCompatibilityChecker();

    // DD6: Configurable lock settings
    private final long lockTimeoutMs;
    private final long lockCleanupIntervalMs;
    private final long lockStaleThresholdMs;

    // Lock cleanup scheduler
    private final ScheduledExecutorService cleanupScheduler;

    // Stats
    private final AtomicLong resolveCount = new AtomicLong(0);
    private final AtomicLong lockTimeoutCount = new AtomicLong(0);
    private final AtomicLong fallbackCount = new AtomicLong(0);

    /**
     * Lock entry with last-used timestamp for cleanup.
     */
    private static class LockEntry {
        final ReentrantLock lock = new ReentrantLock();
        volatile long lastUsedMs = System.currentTimeMillis();

        void touch() {
            lastUsedMs = System.currentTimeMillis();
        }

        boolean isStale(long thresholdMs) {
            return System.currentTimeMillis() - lastUsedMs > thresholdMs;
        }
    }

    public PolicyResolver(
            ArenaTemplateRegistry templateRegistry,
            ArenaTelemetry telemetry,
            OverrideManager overrideManager,
            ArenaTemplateConfig config) {
        this.templateRegistry = templateRegistry;
        this.telemetry = telemetry;
        this.overrideManager = overrideManager;

        // DD6: Load lock settings from config
        this.lockTimeoutMs = config.lockTimeoutMs();
        this.lockCleanupIntervalMs = config.lockCleanupIntervalMs();
        this.lockStaleThresholdMs = config.lockStaleThresholdMs();

        // DD60: Schedule lock cleanup task
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PolicyResolver-LockCleanup");
            t.setDaemon(true);
            return t;
        });

        cleanupScheduler.scheduleAtFixedRate(
            this::cleanupStaleLocks,
            lockCleanupIntervalMs,
            lockCleanupIntervalMs,
            TimeUnit.MILLISECONDS
        );

        LOGGER.info("PolicyResolver initialized with lock cleanup every {}ms", lockCleanupIntervalMs);
    }

    /**
     * Resolves the best arena for the given context.
     *
     * <p>Implements DD6: Lock per player with 5s timeout and fallback to default.
     *
     * @param context The resolution context
     * @return The resolved arena with template and policy
     */
    public ResolvedArena resolve(ResolveContext context) {
        UUID playerId = context.playerId();
        LockEntry lockEntry = playerLocks.computeIfAbsent(playerId, k -> new LockEntry());
        lockEntry.touch();

        long lockStartTime = System.currentTimeMillis();
        boolean acquired;

        try {
            acquired = lockEntry.lock.tryLock(lockTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            telemetry.emit("arena.resolve.interrupted", Map.of("playerId", playerId.toString()));
            LOGGER.warn("Resolve interrupted for player {}, falling back to default", playerId);
            return getDefaultArena(context);
        }

        // DD62: Track lock wait time for contention analysis
        long waitTimeMs = System.currentTimeMillis() - lockStartTime;
        if (waitTimeMs > 100) { // Only log significant waits
            telemetry.emitLockContention(playerId, context.mobType() != null ? context.mobType() : "unknown", waitTimeMs);
        }

        if (!acquired) {
            lockTimeoutCount.incrementAndGet();
            LOGGER.warn("Lock timeout for player {}, falling back to default", playerId);
            telemetry.emitLockTimeout(playerId, lockTimeoutMs);
            return getDefaultArena(context);
        }

        try {
            lockEntry.touch();
            return doResolve(context);
        } finally {
            lockEntry.lock.unlock();
        }
    }

    /**
     * Internal resolution logic (called while holding player lock).
     */
    private ResolvedArena doResolve(ResolveContext context) {
        resolveCount.incrementAndGet();

        // DD5: Check for override first
        Optional<TemplateOverride> override = overrideManager.getOverride(context.playerId());
        if (override.isPresent()) {
            return resolveWithOverride(context, override.get());
        }

        // Check for force policy/template in context
        if (context.forcePolicyId() != null) {
            ArenaPolicy policy = policies.get(context.forcePolicyId());
            if (policy != null) {
                return resolveWithPolicy(context, policy, Map.of("forced", 100.0));
            }
            LOGGER.warn("Forced policy '{}' not found, continuing with normal resolution", context.forcePolicyId());
        }

        if (context.forceTemplateId() != null) {
            Optional<ArenaTemplate> template = templateRegistry.get(context.forceTemplateId());
            if (template.isPresent()) {
                return ResolvedArena.create(
                    template.get(),
                    ArenaPolicy.DEFAULT,
                    Map.of("forced_template", 100.0)
                );
            }
            LOGGER.warn("Forced template '{}' not found, continuing with normal resolution", context.forceTemplateId());
        }

        // Score all policies
        List<ScoredPolicy> scoredPolicies = policies.values().stream()
            .filter(ArenaPolicy::enabled)
            .map(p -> scorePolicyIfCompatible(p, context))
            .flatMap(Optional::stream)
            .filter(sp -> sp.score() > 0) // Must match at least something
            .toList();

        if (scoredPolicies.isEmpty()) {
            LOGGER.debug("No matching policies for context, using default");
            fallbackCount.incrementAndGet();
            return getDefaultArena(context);
        }

        // DD3: Deterministic tie-break - score (desc) -> version (desc) -> id (alpha asc)
        List<ScoredPolicy> sorted = scoredPolicies.stream()
            .sorted(
                Comparator.comparingDouble(ScoredPolicy::score).reversed()               // Score desc
                    .thenComparing(Comparator.comparingInt((ScoredPolicy sp) -> sp.policy().version()).reversed()) // Version desc
                    .thenComparing(sp -> sp.policy().id())                               // ID alpha asc
            )
            .toList();

        ScoredPolicy winner = sorted.get(0);
        List<ScoredPolicy> alternatives = sorted.size() > 1 ? sorted.subList(1, Math.min(5, sorted.size())) : List.of();

        // DD4: Emit detailed telemetry for weight taratura
        emitResolutionTelemetry(winner, alternatives, context);

        return resolveWithPolicy(context, winner.policy(), winner.scoreBreakdown());
    }

    /**
     * Resolves using an override.
     */
    private ResolvedArena resolveWithOverride(ResolveContext context, TemplateOverride override) {
        ArenaTemplate template = templateRegistry.getOrDefault(override.templateId());

        ArenaPolicy policy;
        if (override.policyId() != null) {
            policy = policies.getOrDefault(override.policyId(), ArenaPolicy.DEFAULT);
        } else {
            policy = ArenaPolicy.DEFAULT;
        }

        telemetry.emit("arena.resolve.override_used", Map.of(
            "playerId", context.playerId().toString(),
            "templateId", override.templateId(),
            "policyId", override.policyId() != null ? override.policyId() : "",
            "scope", override.scope().name(),
            "source", override.source()
        ));

        return ResolvedArena.create(template, policy, Map.of("override", 100.0));
    }

    /**
     * Resolves using a specific policy.
     */
    private ResolvedArena resolveWithPolicy(ResolveContext context, ArenaPolicy policy, Map<String, Double> scoreBreakdown) {
        ArenaTemplate template = templateRegistry.getOrDefault(policy.templateId());
        return ResolvedArena.create(template, policy, scoreBreakdown);
    }

    /**
     * Scores a policy against the context.
     * Implements DD4 weight configuration.
     */
    private Optional<ScoredPolicy> scorePolicyIfCompatible(ArenaPolicy policy, ResolveContext context) {
        ArenaPolicy effective = clampPolicyWeight(policy);
        if (effective != policy) {
            policies.put(effective.id(), effective);
            policy = effective;
        }
        Optional<ArenaTemplate> templateOpt = templateRegistry.get(policy.templateId());
        if (templateOpt.isEmpty()) {
            emitVersionMismatch(policy.id(), policy.templateId(), "template_not_found", policy.version(), null, null);
            return Optional.empty();
        }
        ArenaTemplate template = templateOpt.get();
        if (!isPolicyCompatible(policy, template)) {
            return Optional.empty();
        }
        return Optional.of(scorePolicy(policy, context));
    }

    private ArenaPolicy clampPolicyWeight(ArenaPolicy policy) {
        double requested = policy.weight();
        double clamped = Math.min(MAX_POLICY_WEIGHT, Math.max(MIN_POLICY_WEIGHT, requested));
        if (clamped != requested && weightClampEmitted.add(policy.id())) {
            LOGGER.warn("Policy '{}' weight {} clamped to {}", policy.id(), requested, clamped);
            telemetry.emit("arena.routing.weight_clamped", Map.of(
                "policyId", policy.id(),
                "requestedWeight", requested,
                "clampedWeight", clamped,
                "min", MIN_POLICY_WEIGHT,
                "max", MAX_POLICY_WEIGHT
            ));
        }
        if (clamped != requested) {
            return policy.withWeight(clamped);
        }
        return policy;
    }

    private boolean isPolicyCompatible(ArenaPolicy policy, ArenaTemplate template) {
        VersionCompatibilityChecker.VersionCheck check = versionChecker.check(template, policy);
        if (!check.compatible()) {
            emitVersionMismatch(
                policy.id(),
                template.id(),
                check.reason(),
                template.version(),
                policy.minTemplateVersion(),
                policy.maxTemplateVersion()
            );
            return false;
        }
        return true;
    }

    private void emitVersionMismatch(String policyId, String templateId, String reason, int templateVersion,
                                     Integer minTemplateVersion, Integer maxTemplateVersion) {
        telemetry.emit("arena.policy.version_mismatch", Map.of(
            "policyId", policyId,
            "templateId", templateId != null ? templateId : "",
            "reason", reason,
            "templateVersion", templateVersion,
            "minTemplateVersion", minTemplateVersion != null ? minTemplateVersion : -1,
            "maxTemplateVersion", maxTemplateVersion != null ? maxTemplateVersion : -1
        ));
    }

    private ScoredPolicy scorePolicy(ArenaPolicy policy, ResolveContext context) {
        Map<String, Double> breakdown = new LinkedHashMap<>();
        double baseScore = 0.0;

        // DD4: MOB_MATCH (+5) - key with "Score" suffix for taratura
        var policyMobTypes = policy.mobTypes();
        if (policyMobTypes != null && context.mobType() != null) {
            if (policyMobTypes.contains(context.mobType())) {
                breakdown.put("mobScore", (double) WEIGHT_MOB_MATCH);
                baseScore += WEIGHT_MOB_MATCH;
            }
        }

        // DD4: QUEST_TYPE (+4) - key with "Score" suffix for taratura
        var policyQuestTypes = policy.questTypes();
        if (policyQuestTypes != null && context.questType() != null) {
            if (containsIgnoreCase(policyQuestTypes, context.questType())) {
                breakdown.put("questTypeScore", (double) WEIGHT_QUEST_TYPE);
                baseScore += WEIGHT_QUEST_TYPE;
            }
        }

        // DD4: DIFFICULTY (+3) - key with "Score" suffix for taratura
        var policyDifficultyTags = policy.difficultyTags();
        if (policyDifficultyTags != null && context.difficulty() != null) {
            if (containsIgnoreCase(policyDifficultyTags, context.difficulty())) {
                breakdown.put("difficultyScore", (double) WEIGHT_DIFFICULTY);
                baseScore += WEIGHT_DIFFICULTY;
            }
        }

        // DD4: PLAYER_COUNT (+2) - key with "Score" suffix for taratura
        if (matchesPlayerCount(policy, context.playerCount())) {
            breakdown.put("playerCountScore", (double) WEIGHT_PLAYER_COUNT);
            baseScore += WEIGHT_PLAYER_COUNT;
        }

        // DD4: TAGS (+1 per matching tag) - key with "Score" suffix for taratura
        var policyTags = policy.tags();
        if (policyTags != null && context.tags() != null) {
            int tagMatches = 0;
            for (String tag : policyTags) {
                if (context.tags().contains(tag)) {
                    tagMatches++;
                }
            }
            if (tagMatches > 0) {
                int tagScore = tagMatches * WEIGHT_TAGS;
                breakdown.put("tagsScore", (double) tagScore);
                baseScore += tagScore;
            }
        }

        // DD4: Priority bonus - key with "Score" suffix for taratura
        if (policy.priority() > 0) {
            breakdown.put("priorityScore", (double) policy.priority());
            baseScore += policy.priority();
        }

        double total = baseScore;
        if (baseScore > 0) {
            breakdown.put("weightScore", policy.weight());
            total += policy.weight();
        }

        return new ScoredPolicy(policy, total, breakdown);
    }

    private boolean matchesPlayerCount(ArenaPolicy policy, int playerCount) {
        Integer minPlayers = policy.minPlayers();
        Integer maxPlayers = policy.maxPlayers();
        if (minPlayers == null && maxPlayers == null) {
            return false; // No player count filter
        }
        if (minPlayers != null && playerCount < minPlayers) {
            return false;
        }
        if (maxPlayers != null && playerCount > maxPlayers) {
            return false;
        }
        return true;
    }

    private boolean containsIgnoreCase(Set<String> values, String target) {
        for (String value : values) {
            if (value != null && value.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Emits detailed resolution telemetry for weight taratura (DD4).
     */
    private void emitResolutionTelemetry(ScoredPolicy winner, List<ScoredPolicy> alternatives, ResolveContext context) {
        String topAlternative = alternatives.isEmpty() ? null : alternatives.get(0).policy().id();
        double scoreDelta = alternatives.isEmpty() ? 0 : winner.score() - alternatives.get(0).score();

        telemetry.emitPolicyResolved(
            winner.policy().id(),
            winner.policy().templateId(),
            winner.score(),
            winner.scoreBreakdown(),
            alternatives.size(),
            topAlternative,
            scoreDelta
        );
    }

    /**
     * Returns the default arena for fallback scenarios.
     */
    private ResolvedArena getDefaultArena(ResolveContext context) {
        fallbackCount.incrementAndGet();
        ArenaTemplate template = templateRegistry.getOrDefault("default_flat_64");
        return ResolvedArena.create(template, ArenaPolicy.DEFAULT, Map.of("fallback", 1.0));
    }

    // ===================
    // DD60: Lock Cleanup
    // ===================

    /**
     * Cleans up stale locks from the lock map.
     * Called every 5 minutes by scheduled task.
     */
    private void cleanupStaleLocks() {
        long beforeSize = playerLocks.size();
        long removedCount = 0;

        Iterator<Map.Entry<UUID, LockEntry>> iter = playerLocks.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<UUID, LockEntry> entry = iter.next();
            LockEntry lockEntry = entry.getValue();

            // Only remove if stale AND not currently locked AND no waiting threads
            if (lockEntry.isStale(lockStaleThresholdMs)
                && !lockEntry.lock.isLocked()
                && !lockEntry.lock.hasQueuedThreads()) {
                iter.remove();
                removedCount++;
            }
        }

        if (removedCount > 0) {
            LOGGER.debug("Lock cleanup: removed {} stale locks, {} remaining", removedCount, playerLocks.size());
            telemetry.emit("arena.resolver.lock_cleanup", Map.of(
                "removedCount", removedCount,
                "beforeSize", beforeSize,
                "afterSize", playerLocks.size()
            ));
        }
    }

    @Override
    public void close() {
        cleanupScheduler.shutdownNow();
    }

    // ===================
    // Policy Management
    // ===================

    /**
     * Registers a policy.
     */
    public void registerPolicy(ArenaPolicy policy) {
        ArenaPolicy normalized = clampPolicyWeight(policy);
        policies.put(normalized.id(), normalized);
        LOGGER.debug("Policy '{}' registered", normalized.id());
    }

    /**
     * Unregisters a policy.
     */
    public boolean unregisterPolicy(String policyId) {
        ArenaPolicy removed = policies.remove(policyId);
        if (removed != null) {
            LOGGER.debug("Policy '{}' unregistered", policyId);
            return true;
        }
        return false;
    }

    /**
     * Gets a policy by ID.
     */
    public Optional<ArenaPolicy> getPolicy(String policyId) {
        return Optional.ofNullable(policies.get(policyId));
    }

    /**
     * Gets all registered policies.
     */
    public Collection<ArenaPolicy> getAllPolicies() {
        return Collections.unmodifiableCollection(policies.values());
    }

    /**
     * Gets the count of registered policies.
     */
    public int getPolicyCount() {
        return policies.size();
    }

    // ===================
    // Stats
    // ===================

    /**
     * Gets resolver statistics.
     */
    public Map<String, Object> getStats() {
        return Map.of(
            "resolveCount", resolveCount.get(),
            "lockTimeoutCount", lockTimeoutCount.get(),
            "fallbackCount", fallbackCount.get(),
            "policyCount", policies.size(),
            "activeLocks", playerLocks.size()
        );
    }

    /**
     * Shuts down the resolver cleanly.
     */
    public void shutdown() {
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("PolicyResolver shutdown complete");
    }

    // ===================
    // Supporting Records
    // ===================

    /**
     * A policy with its calculated score.
     */
    public record ScoredPolicy(
        ArenaPolicy policy,
        double score,
        Map<String, Double> scoreBreakdown
    ) {}
}
