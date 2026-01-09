package com.devmod.arena.policy;

import java.util.ArrayList;
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
import java.util.concurrent.ScheduledFuture;
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
import com.devmod.arena.registry.TemplateLoadException;
import com.devmod.arena.registry.TemplateType;
import com.devmod.arena.telemetry.ArenaTelemetry;
import com.devmod.mob.EnhancedMobRequirements;
import com.devmod.mob.EnhancedMobRequirementsRegistry;
import com.devmod.mob.MobRequirements;
import com.devmod.mob.StructureCharacteristics;

public class PolicyResolver implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyResolver.class);
    private boolean loggingEnabled = true;

    // DD4: Weight configuration (taratura via telemetry)
    private static final int WEIGHT_MOB_MATCH = 5;
    private static final int WEIGHT_QUEST_TYPE = 4;
    private static final int WEIGHT_DIFFICULTY = 3;
    private static final int WEIGHT_PLAYER_COUNT = 2;
    private static final int WEIGHT_TAGS = 1;
    // MobRequirements-based scoring weights
    private static final int WEIGHT_SPACE_COMPATIBLE = 6;
    private static final int WEIGHT_BIOME_MATCH = 4;
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
    private final ScheduledFuture<?> cleanupTask;

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

        this.cleanupTask = cleanupScheduler.scheduleAtFixedRate(
            this::cleanupStaleLocks,
            lockCleanupIntervalMs,
            lockCleanupIntervalMs,
            TimeUnit.MILLISECONDS
        );

        if (loggingEnabled) {
            LOGGER.info("PolicyResolver initialized with lock cleanup every {}ms", lockCleanupIntervalMs);
        }
    }

    public void setLoggingEnabled(boolean enabled) {
        this.loggingEnabled = enabled;
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
            if (loggingEnabled) {
                LOGGER.warn("Resolve interrupted for player {}, falling back to default", playerId);
            }
            return getDefaultArena();
        }

        // DD62: Track lock wait time for contention analysis
        long waitTimeMs = System.currentTimeMillis() - lockStartTime;
        if (waitTimeMs > 100) { // Only log significant waits
            telemetry.emitLockContention(playerId, context.mobType() != null ? context.mobType() : "unknown", waitTimeMs);
        }

        if (!acquired) {
            lockTimeoutCount.incrementAndGet();
            if (loggingEnabled) {
                LOGGER.warn("Lock timeout for player {}, falling back to default", playerId);
            }
            telemetry.emitLockTimeout(playerId, lockTimeoutMs);
            return getDefaultArena();
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
                return resolveWithPolicy(policy, Map.of("forced", 100.0));
            }
            if (loggingEnabled) {
                LOGGER.warn("Forced policy '{}' not found, continuing with normal resolution", context.forcePolicyId());
            }
        }

        String forcedId = context.forceTemplateId();
        if (forcedId != null) {
            // DD-DYNAMIC: Check if this is a dynamic/custom template request
            MobRequirements mobReqs = context.mobRequirements();
            if (forcedId.startsWith("custom_") && mobReqs != null) {
                ArenaTemplate dynamicTemplate = generateDynamicTemplate(mobReqs, context.server());
                registerDynamicTemplate(dynamicTemplate);
                if (loggingEnabled) {
                    LOGGER.info("[PolicyResolver] Using dynamically generated template '{}' for mob '{}'",
                        dynamicTemplate.id(), mobReqs.mobId());
                }
                return ResolvedArena.create(
                    dynamicTemplate,
                    ArenaPolicy.DEFAULT,
                    Map.of("dynamic_template", 100.0, "custom_fit", 15.0)
                );
            }

            // Try to find in registry (static templates)
            Optional<ArenaTemplate> template = templateRegistry.get(forcedId);
            if (template.isPresent()) {
                return ResolvedArena.create(
                    template.get(),
                    ArenaPolicy.DEFAULT,
                    Map.of("forced_template", 100.0)
                );
            }
            if (loggingEnabled) {
                LOGGER.warn("Forced template '{}' not found, continuing with normal resolution", forcedId);
            }
        }

        // Score all policies
        List<ScoredPolicy> scoredPolicies = policies.values().stream()
            .filter(ArenaPolicy::enabled)
            .map(p -> scorePolicyIfCompatible(p, context))
            .flatMap(Optional::stream)
            .filter(sp -> sp.score() > 0) // Must match at least something
            .toList();

        if (scoredPolicies.isEmpty()) {
            if (loggingEnabled) {
                LOGGER.debug("No matching policies for context, using default");
            }
            fallbackCount.incrementAndGet();
            return getDefaultArena();
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
        emitResolutionTelemetry(winner, alternatives);

        return resolveWithPolicy(winner.policy(), winner.scoreBreakdown());
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
    private ResolvedArena resolveWithPolicy(ArenaPolicy policy, Map<String, Double> scoreBreakdown) {
        ArenaTemplate template = templateRegistry.getOrDefault(policy.templateId());
        return ResolvedArena.create(template, policy, scoreBreakdown);
    }

    /**
     * Scores a policy against the context.
     * Implements DD4 weight configuration.
     */
    private Optional<ScoredPolicy> scorePolicyIfCompatible(ArenaPolicy policy, ResolveContext context) {
        ArenaPolicy effective = clampPolicyWeight(policy);
        if (!effective.equals(policy)) {
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

        // Check if template size is sufficient for mob's space requirements
        MobRequirements mobReqs = context.mobRequirements();
        if (mobReqs != null) {
            int minRadius = mobReqs.space().minArenaRadius();
            int templateRadius = template.size() / 2;
            if (templateRadius < minRadius) {
                if (loggingEnabled) {
                    LOGGER.debug("Template '{}' too small for mob (need radius {}, have {})",
                        template.id(), minRadius, templateRadius);
                }
                telemetry.emit("arena.policy.template_too_small", Map.of(
                    "policyId", policy.id(),
                    "templateId", template.id(),
                    "requiredRadius", minRadius,
                    "templateRadius", templateRadius,
                    "mobType", context.mobType() != null ? context.mobType() : "unknown"
                ));
                return Optional.empty();
            }
        }

        return Optional.of(scorePolicy(policy, template, context));
    }

    private ArenaPolicy clampPolicyWeight(ArenaPolicy policy) {
        double requested = policy.weight();
        double clamped = Math.min(MAX_POLICY_WEIGHT, Math.max(MIN_POLICY_WEIGHT, requested));
        if (clamped != requested && weightClampEmitted.add(policy.id())) {
            if (loggingEnabled) {
                LOGGER.warn("Policy '{}' weight {} clamped to {}", policy.id(), requested, clamped);
            }
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

    private ScoredPolicy scorePolicy(ArenaPolicy policy, ArenaTemplate template, ResolveContext context) {
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

        // MobRequirements-based scoring
        MobRequirements mobReqs = context.mobRequirements();
        if (mobReqs != null) {
            // SPACE_COMPATIBLE (+6) - bonus for template that fits mob's space requirements
            int minRadius = mobReqs.space().minArenaRadius();
            int templateRadius = template.size() / 2;
            if (templateRadius >= minRadius) {
                // Closer fit = higher score (avoid oversized arenas)
                double fitRatio = (double) minRadius / templateRadius;
                double spaceScore = WEIGHT_SPACE_COMPATIBLE * fitRatio;
                breakdown.put("spaceScore", spaceScore);
                baseScore += spaceScore;
            }

            // BIOME_MATCH (+4) - bonus for matching biome preference
            var biomeReq = mobReqs.biome();
            if (!biomeReq.isDefault() && template.biome() != null) {
                String templateBiome = template.biome().id();
                boolean biomeMatches = false;

                // Check preferred biome
                if (biomeReq.preferredBiome().isPresent()) {
                    String preferred = biomeReq.preferredBiome().get().toString();
                    if (preferred.equals(templateBiome)) {
                        biomeMatches = true;
                    }
                }

                // Check valid biomes list
                if (!biomeMatches && !biomeReq.validBiomes().isEmpty()) {
                    for (var validBiome : biomeReq.validBiomes()) {
                        if (validBiome.toString().equals(templateBiome)) {
                            biomeMatches = true;
                            break;
                        }
                    }
                }

                if (biomeMatches) {
                    breakdown.put("biomeScore", (double) WEIGHT_BIOME_MATCH);
                    baseScore += WEIGHT_BIOME_MATCH;
                }
            }
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
    private void emitResolutionTelemetry(ScoredPolicy winner, List<ScoredPolicy> alternatives) {
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
    private ResolvedArena getDefaultArena() {
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
            if (loggingEnabled) {
                LOGGER.debug("Lock cleanup: removed {} stale locks, {} remaining", removedCount, playerLocks.size());
            }
            telemetry.emit("arena.resolver.lock_cleanup", Map.of(
                "removedCount", removedCount,
                "beforeSize", beforeSize,
                "afterSize", playerLocks.size()
            ));
        }
    }

    @Override
    public void close() {
        cleanupTask.cancel(false);
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
        if (loggingEnabled) {
            LOGGER.debug("Policy '{}' registered", normalized.id());
        }
    }

    /**
     * Unregisters a policy.
     */
    public boolean unregisterPolicy(String policyId) {
        ArenaPolicy removed = policies.remove(policyId);
        if (removed != null) {
            if (loggingEnabled) {
                LOGGER.debug("Policy '{}' unregistered", policyId);
            }
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
        cleanupTask.cancel(false);
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        if (loggingEnabled) {
            LOGGER.info("PolicyResolver shutdown complete");
        }
    }

    // ===================
    // Template Suggestions
    // ===================

    /**
     * Calculates template suggestions for a mob based on MobRequirements.
     * Used by the UI to show available arena templates with compatibility scores.
     *
     * @param mobReqs The mob requirements to score against
     * @return List of template suggestions sorted by score descending
     */
    public List<TemplateSuggestion> getTemplateSuggestions(MobRequirements mobReqs) {
        List<TemplateSuggestion> suggestions = new ArrayList<>();

        // DD-DYNAMIC: Add dynamic/custom template as first option (highest priority)
        TemplateSuggestion dynamicSuggestion = createDynamicSuggestion(mobReqs);
        suggestions.add(dynamicSuggestion);
        double highestScore = dynamicSuggestion.compatibilityScore();
        String selectedTemplateId = dynamicSuggestion.templateId();

        for (ArenaTemplate template : templateRegistry.all()) {
            int minRadius = mobReqs.space().minArenaRadius();
            int templateRadius = template.size() / 2;

            if (templateRadius < minRadius) {
                // Template too small - add as incompatible
                suggestions.add(TemplateSuggestion.incompatible(
                    template.id(),
                    template.id(),
                    template.size(),
                    template.biome() != null ? template.biome().id() : "unknown",
                    getArenaShapeName(template),
                    template.walls() != null && template.walls().enabled(),
                    template.hazards() != null && !template.hazards().isEmpty(),
                    template.spawnSlots() != null ? template.spawnSlots().size() : 0,
                    "Too small (need " + minRadius + " radius)"
                ));
                continue;
            }

            // Calculate score using same logic as scorePolicy
            Map<String, Double> breakdown = new LinkedHashMap<>();
            double score = 0;

            // Space score (closer fit = higher)
            double fitRatio = (double) minRadius / templateRadius;
            double spaceScore = WEIGHT_SPACE_COMPATIBLE * fitRatio;
            breakdown.put("spaceScore", spaceScore);
            score += spaceScore;

            // Biome score
            var biomeReq = mobReqs.biome();
            if (!biomeReq.isDefault() && template.biome() != null) {
                String templateBiome = template.biome().id();
                boolean biomeMatches = false;

                if (biomeReq.preferredBiome().isPresent()) {
                    if (biomeReq.preferredBiome().get().toString().equals(templateBiome)) {
                        biomeMatches = true;
                    }
                }
                if (!biomeMatches) {
                    for (var validBiome : biomeReq.validBiomes()) {
                        if (validBiome.toString().equals(templateBiome)) {
                            biomeMatches = true;
                            break;
                        }
                    }
                }

                if (biomeMatches) {
                    breakdown.put("biomeScore", (double) WEIGHT_BIOME_MATCH);
                    score += WEIGHT_BIOME_MATCH;
                }
            }

            if (score > highestScore) {
                highestScore = score;
                selectedTemplateId = template.id();
            }

            suggestions.add(TemplateSuggestion.compatible(
                template.id(),
                template.id(),
                template.size(),
                template.biome() != null ? template.biome().id() : "unknown",
                getArenaShapeName(template),
                template.walls() != null && template.walls().enabled(),
                template.hazards() != null && !template.hazards().isEmpty(),
                template.spawnSlots() != null ? template.spawnSlots().size() : 0,
                score,
                breakdown,
                false // Will be updated after sorting
            ));
        }

        // Sort by score descending
        suggestions.sort((a, b) -> Double.compare(b.compatibilityScore(), a.compatibilityScore()));

        // Mark the top compatible one as selected
        if (selectedTemplateId != null) {
            String finalSelectedId = selectedTemplateId;
            suggestions = suggestions.stream()
                .map(s -> s.templateId().equals(finalSelectedId) && s.isCompatible()
                    ? TemplateSuggestion.compatible(s.templateId(), s.templateName(), s.size(),
                        s.biome(), s.arenaShape(), s.hasWalls(), s.hasHazards(), s.spawnSlotCount(),
                        s.compatibilityScore(), s.scoreBreakdown(), true)
                    : s)
                .toList();
        }

        return suggestions;
    }

    /**
     * Gets the auto-selected template ID for a mob.
     */
    public String getAutoSelectedTemplateId(MobRequirements mobReqs) {
        List<TemplateSuggestion> suggestions = getTemplateSuggestions(mobReqs);
        return suggestions.stream()
            .filter(s -> s.isSelected())
            .map(TemplateSuggestion::templateId)
            .findFirst()
            .orElse(null);
    }

    // ===================
    // Dynamic Template Generation
    // ===================

    /**
     * Generates a dynamic ArenaTemplate tailored to the specific mob requirements.
     * This creates a "custom_[mobId]" template optimized for the mob's needs.
     *
     * Now uses EnhancedMobRequirements to consider spawn source (natural vs structure):
     * - Structure-only mobs: use structure characteristics for floor, light, biome
     * - Natural+structure mobs: depends on questType (dungeon/boss → structure)
     * - Natural-only mobs: use standard biome-based floor mapping
     *
     * @param mobReqs   The mob requirements to generate a template for
     * @param server    The server for registry access (may be null for pattern-based fallback)
     * @return A dynamically generated ArenaTemplate
     */
    public ArenaTemplate generateDynamicTemplate(MobRequirements mobReqs, @javax.annotation.Nullable net.minecraft.server.MinecraftServer server) {
        return generateDynamicTemplate(mobReqs, server, null);
    }

    /**
     * Generates a dynamic ArenaTemplate with quest type consideration.
     *
     * @param mobReqs   The mob requirements to generate a template for
     * @param server    The server for registry access (may be null for pattern-based fallback)
     * @param questType The quest type (affects structure priority for natural+structure mobs)
     * @return A dynamically generated ArenaTemplate
     */
    public ArenaTemplate generateDynamicTemplate(
        MobRequirements mobReqs,
        @javax.annotation.Nullable net.minecraft.server.MinecraftServer server,
        @javax.annotation.Nullable String questType
    ) {
        String mobId = mobReqs.mobId().getPath();
        String templateId = "custom_" + mobId.replace(":", "_");

        // Get enhanced requirements with spawn source detection
        EnhancedMobRequirements enhanced = EnhancedMobRequirementsRegistry.INSTANCE
            .getWithServer(mobReqs.mobId(), server);

        // Determine effective requirements based on spawn source and quest type
        MobRequirements effectiveReqs = enhanced.getEffectiveRequirements(questType);
        StructureCharacteristics structure = enhanced.primaryStructure();
        boolean useStructure = enhanced.spawnSource().shouldUseStructure(questType);

        // Calculate optimal size based on mob space requirements
        int minRadius = effectiveReqs.space().minArenaRadius();
        int size = Math.max(64, roundToNearest16(minRadius * 2 + 16)); // Add padding, round to 16

        // Determine biome - structure hint takes priority when using structure
        String biomeId;
        if (useStructure && structure != null && structure.biomeHint() != null) {
            biomeId = structure.biomeHint();
        } else {
            biomeId = effectiveReqs.biome().preferredBiome()
                .map(r -> r.toString())
                .orElse("minecraft:plains");
        }

        // Determine floor material - structure floor takes priority when using structure
        String floorMaterial;
        if (useStructure && structure != null) {
            floorMaterial = structure.floorMaterial();
            if (loggingEnabled) {
                LOGGER.info("[PolicyResolver] Using structure floor '{}' from '{}' for mob '{}'",
                    floorMaterial, structure.structureId(), mobId);
            }
        } else {
            // Fallback to biome-based floor mapping
            floorMaterial = BiomeFloorMapper.resolveFloorMaterial(effectiveReqs, server);
        }

        // Calculate lighting - structure light takes priority when using structure
        int skyLight;
        int blockLight;
        if (useStructure && structure != null) {
            skyLight = structure.dark() ? 0 : 15;
            blockLight = structure.lightLevel();
        } else {
            skyLight = effectiveReqs.light().prefersLight() ? 15 : (effectiveReqs.light().prefersDark() ? 0 : 15);
            blockLight = effectiveReqs.light().optimalLight();
        }

        // Determine if boss arena needed
        boolean isBoss = effectiveReqs.boss().isBoss();
        ArenaTemplate.ArenaShape shape = isBoss ? ArenaTemplate.ArenaShape.CIRCULAR : ArenaTemplate.ArenaShape.RECTANGULAR;

        // Wall height based on mob height + clearance
        int wallHeight = (int) Math.ceil(effectiveReqs.space().height() + effectiveReqs.space().clearanceAbove()) + 3;
        wallHeight = Math.max(11, Math.min(wallHeight, 20)); // Clamp between 11-20

        // Determine if arena should be enclosed (ceiling)
        boolean enclosed = (useStructure && structure != null) ? structure.enclosed() : effectiveReqs.light().prefersDark();

        if (loggingEnabled) {
            LOGGER.info("[PolicyResolver] Generated dynamic template '{}' for mob '{}': " +
                "size={}, biome={}, floor={}, light={}, spawnSource={}, useStructure={}",
                templateId, mobId, size, biomeId, floorMaterial, blockLight,
                enhanced.spawnSource(), useStructure);
            if (structure != null && useStructure) {
                LOGGER.info("[PolicyResolver] Using structure characteristics from '{}': {}",
                    structure.structureId(), structure.describeEnvironment());
            }
        }

        return ArenaTemplate.builder(templateId)
            .version(1)
            .schemaVersion(1)
            .breakingChange(false)
            .deprecated(false)
            .replacementVersion(null)
            .minParentVersion(null)
            .templateType(TemplateType.FlatTemplate.defaults())
            .origin(new ArenaTemplate.Origin(ArenaTemplate.OriginMode.CENTER, 0, 64, 0))
            .size(size)
            .arenaShape(shape)
            .floor(new ArenaTemplate.Floor(64, 1, floorMaterial, "solid", "minecraft:polished_andesite", 0))
            .walls(new ArenaTemplate.Walls(true, "minecraft:barrier", wallHeight, 1, 64, "solid"))
            .ceiling(new ArenaTemplate.Ceiling(enclosed, "minecraft:barrier", 64 + wallHeight - 1, 1))
            .underfloor(new ArenaTemplate.Underfloor("minecraft:bedrock", 3, false))
            .palette(new ArenaTemplate.Palette("minecraft:polished_andesite", "minecraft:glowstone", "minecraft:magma_block"))
            .biome(new ArenaTemplate.Biome(biomeId, ArenaTemplate.Biome.ApplyTo.BOUNDS))
            .lighting(new ArenaTemplate.Lighting(skyLight, blockLight, true, List.of()))
            .spawnSlots(generateSpawnSlots(size, isBoss))
            .playerSpawnOffset(new ArenaTemplate.Offset(0, 0, 0))
            .mobSpawnStrategy(isBoss ? ArenaTemplate.MobSpawnStrategy.RING : ArenaTemplate.MobSpawnStrategy.DISTRIBUTED)
            .forbiddenZones(List.of())
            .hazards(List.of())
            .environment(new ArenaTemplate.Environment(List.of(), null, new ArenaTemplate.Environment.Fog(false, 0.0f)))
            .compat(new ArenaTemplate.Compat(1, isBoss ? 6 : 4))
            .instanceSettings(new ArenaTemplate.InstanceSettings(
                Math.max(5, (size / 16) + 2), // chunkRadius
                4,
                true
            ))
            .structureNbt(null)
            .limits(new ArenaTemplate.Limits(
                isBoss ? 10000 : 5000,
                size * size * wallHeight,
                isBoss ? 150 : 100
            ))
            .buildSettings(new ArenaTemplate.BuildSettings(
                ArenaTemplate.BuildSettings.Priority.SYNC,
                ArenaTemplate.BuildSettings.Order.FLOOR_FIRST
            ))
            .zoneSettings(null)
            .tags(List.of("dynamic", "custom", mobId))
            .build();
    }

    /**
     * Generates appropriate spawn slots based on arena size and boss flag.
     */
    private List<ArenaTemplate.SpawnSlot> generateSpawnSlots(int size, boolean isBoss) {
        List<ArenaTemplate.SpawnSlot> slots = new ArrayList<>();
        int radius = size / 2;
        int innerRadius = radius / 2;

        // Player spawn at center
        slots.add(new ArenaTemplate.SpawnSlot(
            new int[]{0, 1, 0},
            ArenaTemplate.SpawnSlot.YMode.RELATIVE_TO_FLOOR,
            List.of("center", "player"),
            new ArenaTemplate.SpawnSlot.Validation(true, 2, 1)
        ));

        // Cardinal directions - melee spawns
        int meleeDistance = innerRadius;
        slots.add(new ArenaTemplate.SpawnSlot(new int[]{meleeDistance, 1, 0}, ArenaTemplate.SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("melee", "mob"), new ArenaTemplate.SpawnSlot.Validation(true, 2, 1)));
        slots.add(new ArenaTemplate.SpawnSlot(new int[]{-meleeDistance, 1, 0}, ArenaTemplate.SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("melee", "mob"), new ArenaTemplate.SpawnSlot.Validation(true, 2, 1)));
        slots.add(new ArenaTemplate.SpawnSlot(new int[]{0, 1, meleeDistance}, ArenaTemplate.SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("melee", "mob"), new ArenaTemplate.SpawnSlot.Validation(true, 2, 1)));
        slots.add(new ArenaTemplate.SpawnSlot(new int[]{0, 1, -meleeDistance}, ArenaTemplate.SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("melee", "mob"), new ArenaTemplate.SpawnSlot.Validation(true, 2, 1)));

        // Corner positions - ranged spawns
        int cornerDistance = (int) (innerRadius * 0.7);
        slots.add(new ArenaTemplate.SpawnSlot(new int[]{cornerDistance, 1, cornerDistance}, ArenaTemplate.SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("ranged", "mob"), new ArenaTemplate.SpawnSlot.Validation(true, 2, 1)));
        slots.add(new ArenaTemplate.SpawnSlot(new int[]{-cornerDistance, 1, cornerDistance}, ArenaTemplate.SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("ranged", "mob"), new ArenaTemplate.SpawnSlot.Validation(true, 2, 1)));
        slots.add(new ArenaTemplate.SpawnSlot(new int[]{cornerDistance, 1, -cornerDistance}, ArenaTemplate.SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("ranged", "mob"), new ArenaTemplate.SpawnSlot.Validation(true, 2, 1)));
        slots.add(new ArenaTemplate.SpawnSlot(new int[]{-cornerDistance, 1, -cornerDistance}, ArenaTemplate.SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("ranged", "mob"), new ArenaTemplate.SpawnSlot.Validation(true, 2, 1)));

        // Outer ring - flank spawns
        int outerDistance = radius - 5;
        slots.add(new ArenaTemplate.SpawnSlot(new int[]{outerDistance, 1, 0}, ArenaTemplate.SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("flank", "mob"), new ArenaTemplate.SpawnSlot.Validation(true, 2, 1)));
        slots.add(new ArenaTemplate.SpawnSlot(new int[]{-outerDistance, 1, 0}, ArenaTemplate.SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("flank", "mob"), new ArenaTemplate.SpawnSlot.Validation(true, 2, 1)));
        slots.add(new ArenaTemplate.SpawnSlot(new int[]{0, 1, outerDistance}, ArenaTemplate.SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("flank", "mob"), new ArenaTemplate.SpawnSlot.Validation(true, 2, 1)));
        slots.add(new ArenaTemplate.SpawnSlot(new int[]{0, 1, -outerDistance}, ArenaTemplate.SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("flank", "mob"), new ArenaTemplate.SpawnSlot.Validation(true, 2, 1)));

        // Boss-specific spawn
        if (isBoss) {
            int bossDistance = Math.min(radius - 6, innerRadius + 4);
            if (bossDistance == meleeDistance) {
                bossDistance = Math.min(radius - 6, innerRadius + 8);
            }
            slots.add(new ArenaTemplate.SpawnSlot(
                new int[]{0, 1, bossDistance},
                ArenaTemplate.SpawnSlot.YMode.RELATIVE_TO_FLOOR,
                List.of("boss", "mob"),
                new ArenaTemplate.SpawnSlot.Validation(true, 3, 2)
            ));
        }

        return slots;
    }

    private void registerDynamicTemplate(ArenaTemplate dynamicTemplate) {
        if (dynamicTemplate == null) {
            return;
        }
        ArenaTemplate existing = templateRegistry.get(dynamicTemplate.id()).orElse(null);
        if (existing != null && existing.equals(dynamicTemplate)) {
            return;
        }
        try {
            templateRegistry.load(dynamicTemplate);
        } catch (TemplateLoadException e) {
            if (loggingEnabled) {
                LOGGER.warn("[PolicyResolver] Failed to register dynamic template '{}': {}",
                    dynamicTemplate.id(), String.join("; ", e.getErrors()));
            }
        } catch (RuntimeException e) {
            if (loggingEnabled) {
                LOGGER.warn("[PolicyResolver] Failed to register dynamic template '{}': {}",
                    dynamicTemplate.id(), e.getMessage());
            }
        }
    }

    /**
     * Round to nearest multiple of 16 for chunk alignment.
     */
    private int roundToNearest16(int value) {
        return ((value + 8) / 16) * 16;
    }

    /**
     * Creates a TemplateSuggestion for a dynamically generated template.
     *
     * @param mobReqs The mob requirements
     * @param server  Optional server for registry-based biome lookup (may be null)
     */
    public TemplateSuggestion createDynamicSuggestion(MobRequirements mobReqs, @javax.annotation.Nullable net.minecraft.server.MinecraftServer server) {
        ArenaTemplate dynamic = generateDynamicTemplate(mobReqs, server);

        Map<String, Double> breakdown = new LinkedHashMap<>();
        breakdown.put("dynamicMatch", 10.0); // Perfect match since it's generated for this mob
        breakdown.put("customFit", 5.0);

        return TemplateSuggestion.compatible(
            dynamic.id(),
            "Custom: " + mobReqs.mobId().getPath(),
            dynamic.size(),
            dynamic.biome() != null ? dynamic.biome().id() : "minecraft:plains",
            getArenaShapeName(dynamic),
            dynamic.walls() != null && dynamic.walls().enabled(),
            dynamic.hazards() != null && !dynamic.hazards().isEmpty(),
            dynamic.spawnSlots() != null ? dynamic.spawnSlots().size() : 0,
            15.0, // High score for dynamic templates
            breakdown,
            false // Will be marked selected if highest
        );
    }

    /**
     * Creates a TemplateSuggestion for a dynamically generated template.
     * Convenience overload without server (uses pattern-based fallback).
     */
    public TemplateSuggestion createDynamicSuggestion(MobRequirements mobReqs) {
        return createDynamicSuggestion(mobReqs, null);
    }

    /**
     * Gets the arena shape name for preview purposes.
     */
    private static String getArenaShapeName(ArenaTemplate template) {
        if (template.arenaShape() == null) {
            return "RECTANGULAR";
        }
        return template.arenaShape().name();
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
