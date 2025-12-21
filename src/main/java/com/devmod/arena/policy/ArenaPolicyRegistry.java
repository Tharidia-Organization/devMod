package com.devmod.arena.policy;

import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.registry.ArenaTemplateRegistry;
import com.devmod.arena.registry.TemplateValidator;
import com.devmod.arena.telemetry.ArenaTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Registry for Arena Policies with hot-reload and validation.
 *
 * <p>Implements:
 * <ul>
 *   <li>DD1-like versioning: Last-Wins for same policy ID</li>
 *   <li>Template compatibility validation on load</li>
 *   <li>Hot-reload with atomic swap</li>
 *   <li>Telemetry integration</li>
 * </ul>
 */
public class ArenaPolicyRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArenaPolicyRegistry.class);

    private static final double MIN_WEIGHT = 0.1;
    private static final double MAX_WEIGHT = 10.0;

    private final ConcurrentHashMap<String, ArenaPolicy> policies = new ConcurrentHashMap<>();
    private final AtomicInteger generation = new AtomicInteger(0);
    private final ArenaTelemetry telemetry;
    private final ArenaTemplateRegistry templateRegistry;
    private final Set<String> weightClampWarnings = ConcurrentHashMap.newKeySet();
    private final TemplateValidator.ValidationMode schemaMode;

    // Stats
    private final RegistryStats stats = new RegistryStats();

    public ArenaPolicyRegistry(ArenaTelemetry telemetry, ArenaTemplateRegistry templateRegistry) {
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
        this.templateRegistry = Objects.requireNonNull(templateRegistry, "templateRegistry");
        this.schemaMode = TemplateValidator.fromString(
            System.getProperty("devmod.policy.validationMode") != null
                ? System.getProperty("devmod.policy.validationMode")
                : System.getenv("DEVMOD_POLICY_VALIDATION_MODE"),
            TemplateValidator.ValidationMode.STRICT
        );
        PolicySchemaValidator.tryLoadFromResource("schemas/arena_policy.schema.json");
        PolicySchemaValidator.tryLoadFromPath(Path.of("docs/arena-template-rework/arena_policy.schema.json"));

        // Always register default policy
        policies.put(ArenaPolicy.DEFAULT.id(), ArenaPolicy.DEFAULT);
        LOGGER.info("ArenaPolicyRegistry initialized with default policy");
    }

    /**
     * Registers a policy with validation.
     *
     * @param policy The policy to register
     * @return Validation result
     */
    public ValidationResult register(ArenaPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(policy.id(), "policy.id");

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Validate template exists
        if (policy.templateId() != null) {
            Optional<ArenaTemplate> template = templateRegistry.get(policy.templateId());
            if (template.isEmpty()) {
                errors.add("Template '%s' not found".formatted(policy.templateId()));
            } else {
                // Check version compatibility
                ArenaTemplate t = template.get();
                Integer minVer = policy.minTemplateVersion();
                Integer maxVer = policy.maxTemplateVersion();
                if (minVer != null && t.version() < minVer.intValue()) {
                    String msg = "Template version %d < minTemplateVersion %d"
                        .formatted(t.version(), minVer);
                    errors.add(msg);
                    telemetry.emit("arena.policy.version_mismatch", Map.of(
                        "policyId", policy.id(),
                        "templateId", policy.templateId(),
                        "templateVersion", t.version(),
                        "minTemplateVersion", minVer,
                        "reason", "template_version_too_low"
                    ));
                }
                if (maxVer != null && t.version() > maxVer.intValue()) {
                    String msg = "Template version %d > maxTemplateVersion %d"
                        .formatted(t.version(), maxVer);
                    errors.add(msg);
                    telemetry.emit("arena.policy.version_mismatch", Map.of(
                        "policyId", policy.id(),
                        "templateId", policy.templateId(),
                        "templateVersion", t.version(),
                        "maxTemplateVersion", maxVer,
                        "reason", "template_version_too_high"
                    ));
                }
                if (t.breakingChange() && policy.version() < t.version()) {
                    String msg = "Template has breakingChange at v%d, policy v%d too old"
                        .formatted(t.version(), policy.version());
                    errors.add(msg);
                    telemetry.emit("arena.policy.version_mismatch", Map.of(
                        "policyId", policy.id(),
                        "templateId", policy.templateId(),
                        "templateVersion", t.version(),
                        "policyVersion", policy.version(),
                        "reason", "breaking_change"
                    ));
                }
            }
        }

        // Clamp weight
        ArenaPolicy normalized = clampWeight(policy, warnings);

        // Validate bindings/rewards
        validateBindings(normalized, errors, warnings);
        validateRewards(normalized, errors, warnings);

        // Validate player count range
        Integer minP = normalized.minPlayers();
        Integer maxP = normalized.maxPlayers();
        if (minP != null && maxP != null) {
            if (minP.intValue() > maxP.intValue()) {
                errors.add("minPlayers (%d) > maxPlayers (%d)"
                    .formatted(minP, maxP));
            }
        }

        if (!errors.isEmpty()) {
            telemetry.emit("arena.policy.register_failed", Map.of(
                "policyId", policy.id(),
                "errors", errors
            ));
            return new ValidationResult(false, errors, warnings);
        }

        // Check for version replacement
        ArenaPolicy existing = policies.get(normalized.id());
        if (existing != null && existing.version() != normalized.version()) {
            LOGGER.info("Policy '{}' v{} replaced by v{}",
                normalized.id(), existing.version(), normalized.version());
            telemetry.emit("arena.policy.version_replaced", Map.of(
                "policyId", normalized.id(),
                "oldVersion", existing.version(),
                "newVersion", normalized.version()
            ));
            stats.recordVersionReplacement();
        }

        policies.put(normalized.id(), normalized);
        generation.incrementAndGet();

        LOGGER.debug("Policy '{}' v{} registered", normalized.id(), normalized.version());
        telemetry.emit("arena.policy.registered", Map.of(
            "policyId", normalized.id(),
            "version", normalized.version(),
            "templateId", normalized.templateId() != null ? normalized.templateId() : "",
            "enabled", normalized.enabled()
        ));

        stats.recordLoad();
        return new ValidationResult(true, List.of(), warnings);
    }

    /**
     * Unregisters a policy.
     *
     * @param policyId The policy ID
     * @return true if removed
     */
    public boolean unregister(String policyId) {
        if ("default".equals(policyId)) {
            LOGGER.warn("Cannot unregister default policy");
            return false;
        }

        ArenaPolicy removed = policies.remove(policyId);
        if (removed != null) {
            generation.incrementAndGet();
            LOGGER.debug("Policy '{}' unregistered", policyId);
            telemetry.emit("arena.policy.unregistered", Map.of(
                "policyId", policyId,
                "version", removed.version()
            ));
            stats.recordUnload();
            return true;
        }
        return false;
    }

    /**
     * Gets a policy by ID.
     */
    public Optional<ArenaPolicy> get(String policyId) {
        return Optional.ofNullable(policies.get(policyId));
    }

    /**
     * Gets a policy, returning default if not found.
     */
    public ArenaPolicy getOrDefault(String policyId) {
        return policies.getOrDefault(policyId, ArenaPolicy.DEFAULT);
    }

    /**
     * Returns all registered policies.
     */
    public Collection<ArenaPolicy> all() {
        return Collections.unmodifiableCollection(policies.values());
    }

    /**
     * Returns all enabled policies.
     */
    public Collection<ArenaPolicy> allEnabled() {
        return policies.values().stream()
            .filter(ArenaPolicy::enabled)
            .toList();
    }

    /**
     * Returns policies for a specific template.
     */
    public List<ArenaPolicy> forTemplate(String templateId) {
        return policies.values().stream()
            .filter(p -> templateId.equals(p.templateId()))
            .toList();
    }

    /**
     * Hot-reload: atomically replaces all policies.
     *
     * @param newPolicies The new policies to load
     * @return Reload result
     */
    public ReloadResult hotReload(Collection<ArenaPolicy> newPolicies) {
        LOGGER.info("Hot-reload started with {} policies", newPolicies.size());
        Instant startTime = Instant.now();

        ConcurrentHashMap<String, ArenaPolicy> newRegistry = new ConcurrentHashMap<>();
        List<String> errors = new ArrayList<>();
        weightClampWarnings.clear();

        // Always include default
        newRegistry.put(ArenaPolicy.DEFAULT.id(), ArenaPolicy.DEFAULT);

        // Validate and normalize each policy
        for (ArenaPolicy policy : newPolicies) {
            if ("default".equals(policy.id())) {
                continue; // Skip user-provided default
            }

            ValidationResult result = validatePolicy(policy);
            if (result.valid()) {
                ArenaPolicy normalized = clampWeight(policy, new ArrayList<>());
                newRegistry.put(normalized.id(), normalized);
            } else {
                errors.add("Policy '%s': %s".formatted(policy.id(), result.errors()));
            }
        }

        if (errors.isEmpty()) {
            // Atomic swap
            policies.clear();
            policies.putAll(newRegistry);
            int newGen = generation.incrementAndGet();

            long durationMs = java.time.Duration.between(startTime, Instant.now()).toMillis();
            LOGGER.info("Hot-reload completed: {} policies in {}ms, generation {}",
                newRegistry.size(), durationMs, newGen);

            telemetry.emit("arena.policy.hot_reload", Map.of(
                "success", true,
                "policyCount", newRegistry.size(),
                "generation", newGen,
                "durationMs", durationMs
            ));

            return new ReloadResult(true, newRegistry.size(), List.of());
        } else {
            LOGGER.error("Hot-reload failed with {} errors", errors.size());
            telemetry.emit("arena.policy.hot_reload", Map.of(
                "success", false,
                "errorCount", errors.size(),
                "errors", errors
            ));

            return new ReloadResult(false, 0, errors);
        }
    }

    /**
     * Loads policies from a directory of JSON files.
     */
    public LoadResult loadFromDirectory(Path directory) {
        List<ArenaPolicy> loaded = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        if (!Files.isDirectory(directory)) {
            errors.add("Not a directory: " + directory);
            return new LoadResult(loaded, errors);
        }

        try (var stream = Files.list(directory)) {
            stream
                .filter(p -> p.toString().endsWith(".json"))
                .forEach(path -> loadSinglePolicy(path, loaded, errors));
        } catch (IOException e) {
            errors.add("Failed to list directory: " + e.getMessage());
        }

        // Register all loaded policies
        for (ArenaPolicy policy : loaded) {
            ValidationResult result = register(policy);
            if (!result.valid()) {
                errors.addAll(result.errors().stream()
                    .map(err -> "Policy '%s': %s".formatted(policy.id(), err))
                    .toList());
            }
        }

        return new LoadResult(loaded, errors);
    }

    /**
     * Loads policies from multiple sources with priority: jar resources < datapacks < config directory.
     */
    public LoadResult loadAllSources(Path configDirectory) {
        List<ArenaPolicy> loaded = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // 1) Datapacks
        Path datapacksRoot = Path.of("datapacks");
        if (Files.isDirectory(datapacksRoot)) {
            try (var packs = Files.list(datapacksRoot)) {
                packs.filter(Files::isDirectory).forEach(dp -> {
                    Path arenaPath = dp.resolve("data/devmod/arena_policies/");
                    loadFromPath(arenaPath, loaded, errors);
                });
            } catch (IOException e) {
                errors.add("Failed to list datapacks: " + e.getMessage());
            }
        }

        // 2) Config override (highest priority)
        loadFromPath(configDirectory, loaded, errors);

        // Register loaded policies with validation
        for (ArenaPolicy policy : loaded) {
            ValidationResult result = register(policy);
            if (!result.valid()) {
                errors.addAll(result.errors().stream()
                    .map(err -> "Policy '%s': %s".formatted(policy.id(), err))
                    .toList());
            }
        }

        // Fallback: ensure at least default policy exists
        if (loaded.isEmpty() && !policies.containsKey(ArenaPolicy.DEFAULT.id())) {
            register(ArenaPolicy.DEFAULT);
            loaded.add(ArenaPolicy.DEFAULT);
        }

        return new LoadResult(loaded, errors);
    }

    private void loadFromPath(Path path, List<ArenaPolicy> loaded, List<String> errors) {
        if (path == null || !Files.isDirectory(path)) return;
        try (var stream = Files.list(path)) {
            stream.filter(p -> p.toString().endsWith(".json"))
                .forEach(p -> loadSinglePolicy(p, loaded, errors));
        } catch (IOException e) {
            errors.add("Failed to list directory: " + e.getMessage());
        }
    }

    private void loadSinglePolicy(Path path, List<ArenaPolicy> loaded, List<String> errors) {
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            var obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            PolicySchemaValidator.ValidationResult schemaResult = PolicySchemaValidator.validate(obj, schemaMode);
            if (!schemaResult.unknownFields().isEmpty()) {
                telemetry.emit("arena.policy.unknown_fields", Map.of(
                    "file", path.toString(),
                    "fields", schemaResult.unknownFields()
                ));
            }
            if (!schemaResult.valid()) {
                errors.add(path + ": " + schemaResult.errors());
                telemetry.emit("arena.policy.load_failed", Map.of(
                    "file", path.toString(),
                    "reason", schemaResult.errors()
                ));
                return;
            }
            Optional<ArenaPolicy> parsed = PolicySerializer.deserialize(obj);
            if (parsed.isPresent()) {
                loaded.add(parsed.get());
            } else {
                errors.add(path + ": failed to deserialize");
            }
        } catch (Exception e) {
            errors.add(path + ": " + e.getMessage());
        }
    }

    /**
     * Validates a policy without registering.
     */
    public ValidationResult validatePolicy(ArenaPolicy policy) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (policy.id() == null || policy.id().isBlank()) {
            errors.add("Policy ID is required");
        }

        if (policy.templateId() != null) {
            Optional<ArenaTemplate> template = templateRegistry.get(policy.templateId());
            if (template.isEmpty()) {
                errors.add("Template '%s' not found".formatted(policy.templateId()));
            } else {
                ArenaTemplate t = template.get();
                Integer minVer = policy.minTemplateVersion();
                if (minVer != null && t.version() < minVer) {
                    errors.add("Template v%d < policy minTemplateVersion %d".formatted(t.version(), minVer));
                    telemetry.emit("arena.policy.version_mismatch", Map.of(
                        "policyId", policy.id(),
                        "templateId", policy.templateId(),
                        "templateVersion", t.version(),
                        "minTemplateVersion", minVer,
                        "reason", "below_min"
                    ));
                }
                Integer maxVer = policy.maxTemplateVersion();
                if (maxVer != null && t.version() > maxVer) {
                    errors.add("Template v%d > policy maxTemplateVersion %d".formatted(t.version(), maxVer));
                    telemetry.emit("arena.policy.version_mismatch", Map.of(
                        "policyId", policy.id(),
                        "templateId", policy.templateId(),
                        "templateVersion", t.version(),
                        "maxTemplateVersion", maxVer,
                        "reason", "above_max"
                    ));
                }
                if (t.breakingChange() && policy.version() < t.version()) {
                    errors.add("Template breakingChange at v%d requires policy.version >= %d".formatted(t.version(), t.version()));
                    telemetry.emit("arena.policy.version_mismatch", Map.of(
                        "policyId", policy.id(),
                        "templateId", policy.templateId(),
                        "policyVersion", policy.version(),
                        "templateVersion", t.version(),
                        "reason", "breaking_change_requires_policy_bump"
                    ));
                }
            }
        }

        checkStringSet(policy.mobTypes(), "mobTypes", warnings);
        checkStringSet(policy.questTypes(), "questTypes", warnings);
        checkStringSet(policy.difficultyTags(), "difficultyTags", warnings);
        checkStringSet(policy.tags(), "tags", warnings);
        validateBindings(policy, errors, warnings);
        validateRewards(policy, errors, warnings);

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    private ArenaPolicy clampWeight(ArenaPolicy policy, List<String> warnings) {
        double requested = policy.weight();
        double clamped = Math.min(MAX_WEIGHT, Math.max(MIN_WEIGHT, requested));

        if (clamped != requested) {
            if (weightClampWarnings.add(policy.id())) {
                LOGGER.warn("Policy '{}' weight {} clamped to [{}, {}] = {}",
                    policy.id(), requested, MIN_WEIGHT, MAX_WEIGHT, clamped);
                telemetry.emit("arena.policy.weight_clamped", Map.of(
                    "policyId", policy.id(),
                    "requestedWeight", requested,
                    "clampedWeight", clamped
                ));
            }
            warnings.add("Weight clamped from %.2f to %.2f".formatted(requested, clamped));
            return policy.withWeight(clamped);
        }
        return policy;
    }

    private void validateBindings(ArenaPolicy policy, List<String> errors, List<String> warnings) {
        // No structural rules beyond presence; ensure sets have no blanks
        var perkBindings = policy.perkBindings();
        checkStringSet(perkBindings != null ? perkBindings.suggested() : null, "perkBindings.suggested", warnings);
        checkStringSet(perkBindings != null ? perkBindings.excluded() : null, "perkBindings.excluded", warnings);
        checkStringSet(perkBindings != null ? perkBindings.required() : null, "perkBindings.required", warnings);

        var mutatorBindings = policy.mutatorBindings();
        checkStringSet(mutatorBindings != null ? mutatorBindings.suggested() : null, "mutatorBindings.suggested", warnings);
        checkStringSet(mutatorBindings != null ? mutatorBindings.excluded() : null, "mutatorBindings.excluded", warnings);
        checkStringSet(mutatorBindings != null ? mutatorBindings.required() : null, "mutatorBindings.required", warnings);
    }

    private void validateRewards(ArenaPolicy policy, List<String> errors, List<String> warnings) {
        ArenaPolicy.RewardModifiers rm = policy.rewardModifiers();
        if (rm == null) return;
        if (rm.baseMultiplier() < 0) {
            errors.add("rewardModifiers.baseMultiplier must be >= 0");
        }
        if (rm.firstCompletionBonus() < 0) {
            warnings.add("rewardModifiers.firstCompletionBonus < 0, clamped to 0");
        }
        if (rm.hazardBonus() < 0) {
            warnings.add("rewardModifiers.hazardBonus < 0, clamped to 0");
        }
        if (rm.streakMultiplier() < 0) {
            warnings.add("rewardModifiers.streakMultiplier < 0, clamped to 0");
        }
    }

    private void checkStringSet(Set<String> set, String field, List<String> warnings) {
        if (set == null) return;
        for (String s : set) {
            if (s == null || s.isBlank()) {
                warnings.add(field + " contains blank entry");
                break;
            }
        }
    }

    /**
     * Gets current registry generation.
     */
    public int getGeneration() {
        return generation.get();
    }

    /**
     * Gets registry statistics.
     */
    public RegistryStats getStats() {
        return stats;
    }

    /**
     * Gets the count of registered policies.
     */
    public int size() {
        return policies.size();
    }

    /**
     * Checks if a policy exists.
     */
    public boolean contains(String policyId) {
        return policies.containsKey(policyId);
    }

    // ========== Records ==========

    public record ValidationResult(
        boolean valid,
        List<String> errors,
        List<String> warnings
    ) {}

    public record ReloadResult(
        boolean success,
        int loadedCount,
        List<String> errors
    ) {}

    public record LoadResult(
        List<ArenaPolicy> policies,
        List<String> errors
    ) {
        public boolean success() {
            return errors == null || errors.isEmpty();
        }
    }

    public static class RegistryStats {
        private final AtomicInteger loads = new AtomicInteger(0);
        private final AtomicInteger unloads = new AtomicInteger(0);
        private final AtomicInteger versionReplacements = new AtomicInteger(0);

        void recordLoad() { loads.incrementAndGet(); }
        void recordUnload() { unloads.incrementAndGet(); }
        void recordVersionReplacement() { versionReplacements.incrementAndGet(); }

        public int getLoads() { return loads.get(); }
        public int getUnloads() { return unloads.get(); }
        public int getVersionReplacements() { return versionReplacements.get(); }

        public Map<String, Integer> toMap() {
            return Map.of(
                "loads", loads.get(),
                "unloads", unloads.get(),
                "versionReplacements", versionReplacements.get()
            );
        }
    }

    public static class PolicySerializer {
        public static Optional<ArenaPolicy> deserialize(String json) {
            try {
                var obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
                return deserialize(obj);
            } catch (Exception e) {
                return Optional.empty();
            }
        }

        public static Optional<ArenaPolicy> deserialize(com.google.gson.JsonObject obj) {
            try {

                String id = getString(obj, "id", null);
                if (id == null || id.isBlank()) return Optional.empty();

                int version = getInt(obj, "version", 1);
                String templateId = getString(obj, "templateId", null);
                int priority = getInt(obj, "priority", 0);
                double weight = getDouble(obj, "weight", 1.0);
                boolean enabled = getBoolean(obj, "enabled", true);
                String description = getString(obj, "description", null);

                Integer minPlayers = getNullableInt(obj, "minPlayers");
                Integer maxPlayers = getNullableInt(obj, "maxPlayers");
                Integer minTemplateVersion = getNullableInt(obj, "minTemplateVersion");
                Integer maxTemplateVersion = getNullableInt(obj, "maxTemplateVersion");

                Set<String> mobTypes = toSet(obj, "mobTypes");
                Set<String> mobIds = toSet(obj, "mobIds");
                if (mobIds != null && !mobIds.isEmpty()) {
                    mobTypes = mobIds;
                }
                Set<String> questTypes = toSet(obj, "questTypes");
                if (questTypes == null || questTypes.isEmpty()) {
                    String legacyQuest = getString(obj, "questType", null);
                    if (legacyQuest != null) {
                        questTypes = Set.of(legacyQuest);
                    }
                }
                Set<String> difficultyTags = toSet(obj, "difficultyTags");
                if (difficultyTags == null || difficultyTags.isEmpty()) {
                    String legacyDifficulty = getString(obj, "difficulty", null);
                    if (legacyDifficulty != null) {
                        difficultyTags = Set.of(legacyDifficulty);
                    }
                }
                Set<String> tags = toSet(obj, "tags");

                if (obj.has("routing") && obj.get("routing").isJsonObject()) {
                    var routing = obj.getAsJsonObject("routing");
                    Set<String> routingMobIds = toSet(routing, "mobIds");
                    if (routingMobIds != null && !routingMobIds.isEmpty()) {
                        mobTypes = routingMobIds;
                    }
                    Set<String> routingQuestTypes = toSet(routing, "questTypes");
                    if (routingQuestTypes != null && !routingQuestTypes.isEmpty()) {
                        questTypes = routingQuestTypes;
                    }
                    Set<String> routingDifficultyTags = toSet(routing, "difficultyTags");
                    if (routingDifficultyTags != null && !routingDifficultyTags.isEmpty()) {
                        difficultyTags = routingDifficultyTags;
                    }
                    weight = getDouble(routing, "weight", weight);
                }

                ArenaPolicy.PerkBindings perk = null;
                if (obj.has("perkBindings") && obj.get("perkBindings").isJsonObject()) {
                    var pb = obj.getAsJsonObject("perkBindings");
                    perk = new ArenaPolicy.PerkBindings(
                        toSet(pb, "suggested"),
                        toSet(pb, "excluded"),
                        toSet(pb, "required")
                    );
                }

                ArenaPolicy.MutatorBindings mut = null;
                if (obj.has("mutatorBindings") && obj.get("mutatorBindings").isJsonObject()) {
                    var mb = obj.getAsJsonObject("mutatorBindings");
                    mut = new ArenaPolicy.MutatorBindings(
                        toSet(mb, "suggested"),
                        toSet(mb, "excluded"),
                        toSet(mb, "required")
                    );
                }

                ArenaPolicy.RewardModifiers rewards = null;
                if (obj.has("rewardModifiers") && obj.get("rewardModifiers").isJsonObject()) {
                    var rm = obj.getAsJsonObject("rewardModifiers");
                    rewards = new ArenaPolicy.RewardModifiers(
                        getDouble(rm, "baseMultiplier", 1.0),
                        getDouble(rm, "firstCompletionBonus", 0.0),
                        getDouble(rm, "hazardBonus", 0.0),
                        getDouble(rm, "streakMultiplier", 0.0)
                    );
                }

                ArenaPolicy.BalanceOverrides balance = null;
                if (obj.has("balanceOverrides") && obj.get("balanceOverrides").isJsonObject()) {
                    var bo = obj.getAsJsonObject("balanceOverrides");
                    balance = new ArenaPolicy.BalanceOverrides(
                        getNullableDouble(bo, "spawnRateMultiplier"),
                        getNullableDouble(bo, "damageMultiplier"),
                        getNullableDouble(bo, "waveScaling")
                    );
                }

                return Optional.of(new ArenaPolicy(
                    id,
                    version,
                    templateId,
                    minTemplateVersion,
                    maxTemplateVersion,
                    mobTypes,
                    questTypes,
                    difficultyTags,
                    minPlayers,
                    maxPlayers,
                    tags,
                    priority,
                    weight,
                    perk,
                    mut,
                    rewards,
                    balance,
                    enabled,
                    description
                ));
            } catch (Exception e) {
                return Optional.empty();
            }
        }

        private static String getString(com.google.gson.JsonObject obj, String key, String def) {
            return obj.has(key) && obj.get(key).isJsonPrimitive() && obj.get(key).getAsJsonPrimitive().isString()
                ? obj.get(key).getAsString()
                : def;
        }

        private static int getInt(com.google.gson.JsonObject obj, String key, int def) {
            return obj.has(key) && obj.get(key).isJsonPrimitive() && obj.get(key).getAsJsonPrimitive().isNumber()
                ? obj.get(key).getAsInt()
                : def;
        }

        @Nullable
        private static Integer getNullableInt(com.google.gson.JsonObject obj, String key) {
            return obj.has(key) && obj.get(key).isJsonPrimitive() && obj.get(key).getAsJsonPrimitive().isNumber()
                ? obj.get(key).getAsInt()
                : null;
        }

        private static double getDouble(com.google.gson.JsonObject obj, String key, double def) {
            return obj.has(key) && obj.get(key).isJsonPrimitive() && obj.get(key).getAsJsonPrimitive().isNumber()
                ? obj.get(key).getAsDouble()
                : def;
        }

        @Nullable
        private static Double getNullableDouble(com.google.gson.JsonObject obj, String key) {
            return obj.has(key) && obj.get(key).isJsonPrimitive() && obj.get(key).getAsJsonPrimitive().isNumber()
                ? obj.get(key).getAsDouble()
                : null;
        }

        private static boolean getBoolean(com.google.gson.JsonObject obj, String key, boolean def) {
            return obj.has(key) && obj.get(key).isJsonPrimitive() && obj.get(key).getAsJsonPrimitive().isBoolean()
                ? obj.get(key).getAsBoolean()
                : def;
        }

        @Nullable
        private static Set<String> toSet(com.google.gson.JsonObject obj, String key) {
            if (!obj.has(key) || !obj.get(key).isJsonArray()) return null;
            Set<String> set = new HashSet<>();
            obj.getAsJsonArray(key).forEach(el -> {
                if (el.isJsonPrimitive()) {
                    set.add(el.getAsString());
                }
            });
            return set;
        }
    }
}
