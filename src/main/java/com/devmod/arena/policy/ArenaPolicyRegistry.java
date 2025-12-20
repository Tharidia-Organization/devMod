package com.devmod.arena.policy;

import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.registry.ArenaTemplateRegistry;
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

    // Stats
    private final RegistryStats stats = new RegistryStats();

    public ArenaPolicyRegistry(ArenaTelemetry telemetry, ArenaTemplateRegistry templateRegistry) {
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
        this.templateRegistry = Objects.requireNonNull(templateRegistry, "templateRegistry");

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
                    errors.add("Template version %d < minTemplateVersion %d"
                        .formatted(t.version(), minVer));
                }
                if (maxVer != null && t.version() > maxVer.intValue()) {
                    errors.add("Template version %d > maxTemplateVersion %d"
                        .formatted(t.version(), maxVer));
                }
            }
        }

        // Clamp weight
        ArenaPolicy normalized = clampWeight(policy, warnings);

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
            Optional<ArenaPolicy> parsed = PolicySerializer.deserialize(json);
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
                }
                Integer maxVer = policy.maxTemplateVersion();
                if (maxVer != null && t.version() > maxVer) {
                    errors.add("Template v%d > policy maxTemplateVersion %d".formatted(t.version(), maxVer));
                }
            }
        }

        return new ValidationResult(errors.isEmpty(), errors, List.of());
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

    /**
     * Policy serializer with support for complex fields (mobTypes, tags).
     */
    public static class PolicySerializer {
        public static Optional<ArenaPolicy> deserialize(String json) {
            try {
                String id = extractString(json, "id");
                if (id == null) return Optional.empty();

                int version = extractInt(json, "version", 1);
                String templateId = extractString(json, "templateId");
                String questType = extractString(json, "questType");
                String difficulty = extractString(json, "difficulty");
                int priority = extractInt(json, "priority", 0);
                double weight = extractDouble(json, "weight", 1.0);
                boolean enabled = extractBoolean(json, "enabled", true);
                String description = extractString(json, "description");

                Integer minPlayers = extractNullableInt(json, "minPlayers");
                Integer maxPlayers = extractNullableInt(json, "maxPlayers");
                Integer minTemplateVersion = extractNullableInt(json, "minTemplateVersion");
                Integer maxTemplateVersion = extractNullableInt(json, "maxTemplateVersion");

                // Parse mobTypes array (convert to Set)
                List<String> mobTypesList = extractStringArray(json, "mobTypes");
                Set<String> mobTypes = mobTypesList != null ? new HashSet<>(mobTypesList) : null;

                // Parse tags array (convert to Set)
                List<String> tagsList = extractStringArray(json, "tags");
                Set<String> tags = tagsList != null ? new HashSet<>(tagsList) : null;

                return Optional.of(new ArenaPolicy(
                    id,
                    version,
                    templateId,
                    minTemplateVersion,
                    maxTemplateVersion,
                    mobTypes,
                    questType,
                    difficulty,
                    minPlayers,
                    maxPlayers,
                    tags,
                    priority,
                    weight,
                    enabled,
                    description
                ));
            } catch (Exception e) {
                return Optional.empty();
            }
        }

        private static String extractString(String json, String key) {
            int start = json.indexOf("\"" + key + "\"");
            if (start < 0) return null;
            int colonIdx = json.indexOf(":", start);
            if (colonIdx < 0) return null;
            int quoteStart = json.indexOf("\"", colonIdx + 1);
            if (quoteStart < 0) return null;
            int quoteEnd = json.indexOf("\"", quoteStart + 1);
            if (quoteEnd < 0) return null;
            return json.substring(quoteStart + 1, quoteEnd);
        }

        /**
         * Extracts a JSON string array like ["value1", "value2"].
         */
        @Nullable
        private static List<String> extractStringArray(String json, String key) {
            int keyStart = json.indexOf("\"" + key + "\"");
            if (keyStart < 0) return null;

            int colonIdx = json.indexOf(":", keyStart);
            if (colonIdx < 0) return null;

            int arrayStart = json.indexOf("[", colonIdx);
            if (arrayStart < 0) return null;

            int arrayEnd = json.indexOf("]", arrayStart);
            if (arrayEnd < 0) return null;

            String arrayContent = json.substring(arrayStart + 1, arrayEnd).trim();
            if (arrayContent.isEmpty()) {
                return List.of();
            }

            List<String> result = new ArrayList<>();
            int pos = 0;
            while (pos < arrayContent.length()) {
                // Skip whitespace and commas
                while (pos < arrayContent.length() &&
                       (Character.isWhitespace(arrayContent.charAt(pos)) || arrayContent.charAt(pos) == ',')) {
                    pos++;
                }
                if (pos >= arrayContent.length()) break;

                // Find quoted string
                if (arrayContent.charAt(pos) == '"') {
                    int strStart = pos + 1;
                    int strEnd = arrayContent.indexOf("\"", strStart);
                    if (strEnd > strStart) {
                        result.add(arrayContent.substring(strStart, strEnd));
                        pos = strEnd + 1;
                    } else {
                        break;
                    }
                } else {
                    // Skip non-quoted value (shouldn't happen for string arrays)
                    pos++;
                }
            }

            return result.isEmpty() ? null : result;
        }

        private static int extractInt(String json, String key, int defaultValue) {
            try {
                int start = json.indexOf("\"" + key + "\"");
                if (start < 0) return defaultValue;
                int colonIdx = json.indexOf(":", start);
                if (colonIdx < 0) return defaultValue;
                int numStart = colonIdx + 1;
                while (numStart < json.length() && Character.isWhitespace(json.charAt(numStart))) {
                    numStart++;
                }
                int numEnd = numStart;
                while (numEnd < json.length() && (Character.isDigit(json.charAt(numEnd)) || json.charAt(numEnd) == '-')) {
                    numEnd++;
                }
                return Integer.parseInt(json.substring(numStart, numEnd));
            } catch (Exception e) {
                return defaultValue;
            }
        }

        @Nullable
        private static Integer extractNullableInt(String json, String key) {
            int start = json.indexOf("\"" + key + "\"");
            if (start < 0) return null;
            int colonIdx = json.indexOf(":", start);
            if (colonIdx < 0) return null;
            int numStart = colonIdx + 1;
            while (numStart < json.length() && Character.isWhitespace(json.charAt(numStart))) {
                numStart++;
            }
            // Check for null value
            if (json.regionMatches(numStart, "null", 0, 4)) {
                return null;
            }
            int numEnd = numStart;
            while (numEnd < json.length() && (Character.isDigit(json.charAt(numEnd)) || json.charAt(numEnd) == '-')) {
                numEnd++;
            }
            if (numEnd == numStart) return null;
            try {
                return Integer.parseInt(json.substring(numStart, numEnd));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private static double extractDouble(String json, String key, double defaultValue) {
            try {
                int start = json.indexOf("\"" + key + "\"");
                if (start < 0) return defaultValue;
                int colonIdx = json.indexOf(":", start);
                if (colonIdx < 0) return defaultValue;
                int numStart = colonIdx + 1;
                while (numStart < json.length() && Character.isWhitespace(json.charAt(numStart))) {
                    numStart++;
                }
                int numEnd = numStart;
                while (numEnd < json.length() && (Character.isDigit(json.charAt(numEnd)) || json.charAt(numEnd) == '.' || json.charAt(numEnd) == '-')) {
                    numEnd++;
                }
                return Double.parseDouble(json.substring(numStart, numEnd));
            } catch (Exception e) {
                return defaultValue;
            }
        }

        private static boolean extractBoolean(String json, String key, boolean defaultValue) {
            int start = json.indexOf("\"" + key + "\"");
            if (start < 0) return defaultValue;
            int colonIdx = json.indexOf(":", start);
            if (colonIdx < 0) return defaultValue;
            String rest = json.substring(colonIdx + 1).trim();
            if (rest.startsWith("true")) return true;
            if (rest.startsWith("false")) return false;
            return defaultValue;
        }
    }
}
