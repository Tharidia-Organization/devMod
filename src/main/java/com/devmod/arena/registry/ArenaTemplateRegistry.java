package com.devmod.arena.registry;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.arena.config.ArenaTemplateConfig;
import com.devmod.arena.config.InstanceLimitConfig;
import com.devmod.arena.event.TemplateEventDispatcher;
import com.devmod.arena.telemetry.ArenaTelemetry;

public class ArenaTemplateRegistry implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArenaTemplateRegistry.class);
    private static final int MAX_INHERITANCE_DEPTH = 3;
    private static final long HEALTH_CHECK_INTERVAL_MS = 5 * 60 * 1000L;

    private final ConcurrentHashMap<String, ArenaTemplate> registry = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ArenaTemplate> unresolvedTemplates = new ConcurrentHashMap<>();
    private final AtomicInteger generation = new AtomicInteger(0);
    private final ArenaTelemetry telemetry;
    private final TemplateValidator validator;
    private final TemplateEventDispatcher eventDispatcher;
    private final ScheduledExecutorService healthCheckExecutor;
    @Nullable
    private ScheduledFuture<?> healthCheckTask;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    @Nullable
    private ArenaTemplateConfig.ConfigSnapshot configSnapshot;
    private boolean loggingEnabled = true;

    // Stats tracking
    private final RegistryStats stats = new RegistryStats();

    // Reload helpers (prevent leaks)
    private final Map<String, Object> templateLocks = new ConcurrentHashMap<>();
    private final Map<String, Object> listenerHandles = new ConcurrentHashMap<>();

    public ArenaTemplateRegistry(ArenaTelemetry telemetry) {
        this(telemetry, InstanceLimitConfig.load().toLimits(), null);
    }

    public ArenaTemplateRegistry(ArenaTelemetry telemetry, InstanceSettingsValidator.InstanceLimits instanceLimits) {
        this(telemetry, instanceLimits, null);
    }

    @SuppressWarnings("this-escape") // configure() reads manifest and registers validators immediately
    public ArenaTemplateRegistry(ArenaTelemetry telemetry,
                                 InstanceSettingsValidator.InstanceLimits instanceLimits,
                                 @Nullable ArenaTemplateConfig.ConfigSnapshot configSnapshot) {
        this.telemetry = telemetry;
        this.eventDispatcher = Objects.requireNonNull(TemplateEventDispatcher.getInstance(), "TemplateEventDispatcher");
        this.configSnapshot = configSnapshot;
        this.validator = new TemplateValidator()
            .withInstanceLimits(instanceLimits)
            .withInstanceTelemetry(new InstanceSettingsValidator.Telemetry() {
                @Override
                public void clamped(String templateId, String field, int requested, int effective, int serverMax) {
                    telemetry.emit("arena.instance.clamped", Map.of(
                        "templateId", templateId,
                        "field", field,
                        "requested", requested,
                        "effective", effective,
                        "serverMax", serverMax
                    ));
                }

                @Override
                public void coverageInsufficient(String templateId, int maxDim, int requiredChunks, int effectiveChunkRadius) {
                    telemetry.emit("arena.instance.coverage_insufficient", Map.of(
                        "templateId", templateId,
                        "maxDim", maxDim,
                        "requiredChunks", requiredChunks,
                    "effectiveChunkRadius", effectiveChunkRadius
                ));
            }
        });
        this.validator.withStructureTelemetry(new TemplateValidator.StructureTelemetry() {
            @Override
            public void loaded(String path, int blockCount, int entityCount) {
                telemetry.emit("arena.structure.loaded", Map.of(
                    "path", path,
                    "blockCount", blockCount,
                    "entityCount", entityCount
                ));
            }

            @Override
            public void rejected(String path, String reason) {
                telemetry.emit("arena.structure.rejected", Map.of(
                    "path", path,
                    "reason", reason
                ));
                if ("CHECKSUM_MISMATCH".equals(reason)) {
                    telemetry.emit("arena.structure.checksum_mismatch", Map.of(
                        "path", path
                    ));
                }
            }

            @Override
            public void fallbackUsed(String path, String reason) {
                telemetry.emit("arena.structure.fallback_used", Map.of(
                    "path", path,
                    "reason", reason
                ));
            }
        });
        this.validator.withHazardTelemetry(new HazardValidator.HazardTelemetry() {
            @Override
            public void validationFailed(String templateId, String reason) {
                telemetry.emit("arena.hazard.validation_failed", Map.of(
                    "templateId", templateId,
                    "reason", reason
                ));
            }

            @Override
            public void highCoverage(String templateId, double coverage) {
                telemetry.emit("arena.hazard.high_coverage", Map.of(
                    "templateId", templateId,
                    "coverage", coverage
                ));
            }
        });

        // Custom hazard registry (optional)
        this.validator.withRegisteredCustomHazards(CustomHazardRegistry.getInstance().all());

        // P1: Block/entity whitelist validation
        configureBlockEntityWhitelist(this.configSnapshot);

        // Optional: enable structure validation if manifest exists (default path)
        try {
            ArenaTemplateConfig.ConfigSnapshot activeSnapshot = this.configSnapshot;
            Path manifestPath = activeSnapshot != null && activeSnapshot.structureManifestPath() != null
                ? Path.of(activeSnapshot.structureManifestPath())
                : Path.of("config/devmod/structures_manifest.json");
            StructureValidationInitializer.configure(this, manifestPath, Thread.currentThread().getContextClassLoader(), activeSnapshot);
        } catch (Exception e) {
            if (loggingEnabled) {
                LOGGER.warn("Failed to auto-configure structure validation: {}", e.getMessage());
            }
        }

        this.healthCheckExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ArenaTemplateRegistry-Health");
            t.setDaemon(true);
            return t;
        });
        this.healthCheckTask = this.healthCheckExecutor.scheduleAtFixedRate(
            this::runHealthCheck,
            HEALTH_CHECK_INTERVAL_MS,
            HEALTH_CHECK_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
    }

    /**
     * Enable structure NBT validation using the provided manifest and data provider.
     */
    public void enableStructureValidation(StructureManifest manifest, TemplateValidator.StructureDataProvider provider) {
        this.validator.withStructureValidation(manifest, provider);
    }

    /**
     * P1: Configure block/entity whitelist validation from config snapshot.
     */
    private void configureBlockEntityWhitelist(@Nullable ArenaTemplateConfig.ConfigSnapshot snapshot) {
        if (snapshot == null) {
            // Use defaults
            this.validator.withBlockEntityWhitelist(BlockEntityWhitelist.defaults());
            return;
        }

        String whitelistPath = snapshot.whitelistPath();
        String whitelistMode = snapshot.whitelistMode();

        // Parse mode from config
        BlockEntityWhitelist.Mode mode = BlockEntityWhitelist.Mode.WARN;
        if (whitelistMode != null && !whitelistMode.isBlank()) {
            try {
                mode = BlockEntityWhitelist.Mode.valueOf(whitelistMode.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                if (loggingEnabled) {
                    LOGGER.warn("Invalid whitelist mode '{}', defaulting to WARN", whitelistMode);
                }
            }
        }

        // Skip validation entirely if mode is SKIP
        if (mode == BlockEntityWhitelist.Mode.SKIP) {
            this.validator.withBlockEntityWhitelist(BlockEntityWhitelist.disabled());
            return;
        }

        // Try to load from file, fall back to defaults
        BlockEntityWhitelist whitelist;
        if (whitelistPath != null && !whitelistPath.isBlank()) {
            try {
                whitelist = BlockEntityWhitelist.parse(Path.of(whitelistPath));
                if (loggingEnabled) {
                    LOGGER.info("Loaded block/entity whitelist from {} (mode={})", whitelistPath, whitelist.getMode());
                }
            } catch (Exception e) {
                if (loggingEnabled) {
                    LOGGER.warn("Failed to load whitelist from {}, using defaults: {}", whitelistPath, e.getMessage());
                }
                whitelist = BlockEntityWhitelist.defaults();
            }
        } else {
            whitelist = BlockEntityWhitelist.defaults();
        }

        whitelist = whitelist.withMode(mode);
        this.validator.withBlockEntityWhitelist(whitelist);

        // Emit telemetry
        telemetry.emit("arena.whitelist.configured", Map.of(
            "mode", whitelist.getMode().name(),
            "enabled", whitelist.isEnabled(),
            "path", whitelistPath != null ? whitelistPath : "default"
        ));
    }

    /**
     * Applies a new config snapshot to the registry runtime state.
     */
    public void applyConfigSnapshot(@Nullable ArenaTemplateConfig.ConfigSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        this.configSnapshot = snapshot;
        // Refresh validator inputs that depend on config/custom registry
        validator.withRegisteredCustomHazards(CustomHazardRegistry.getInstance().all());
        validator.withInstanceLimits(InstanceLimitConfig.load().toLimits());
        validator.withStructureFallbackConfig(new TemplateValidator.StructureFallbackConfig(
            snapshot.structureAllowedNamespaces() != null && !snapshot.structureAllowedNamespaces().isEmpty()
                ? Set.copyOf(snapshot.structureAllowedNamespaces())
                : Set.of("devmod"),
            snapshot.structureMaxFileSizeBytes() > 0 ? snapshot.structureMaxFileSizeBytes() : 512 * 1024,
            snapshot.structureMaxBlockCount() > 0 ? snapshot.structureMaxBlockCount() : 100_000,
            snapshot.structureMaxEntityCount() > 0 ? snapshot.structureMaxEntityCount() : 50
        ));

        // P1: Refresh block/entity whitelist on config reload
        configureBlockEntityWhitelist(snapshot);
    }

    public void setLoggingEnabled(boolean enabled) {
        this.loggingEnabled = enabled;
    }

    /**
     * Loads a template into the registry.
     * Implements DD1 (Last-Wins) and DD2 (Inheritance on-load with caching).
     *
     * @param template The template to load
     * @throws TemplateLoadException if validation fails or inheritance cannot be resolved
     */
    public void load(ArenaTemplate template) {
        Objects.requireNonNull(template, "Template cannot be null");
        Objects.requireNonNull(template.id(), "Template ID cannot be null");

        // DD1: Version handling - Last Wins
        ArenaTemplate existing = registry.get(template.id());
        if (existing != null && existing.version() != template.version()) {
            if (loggingEnabled) {
                LOGGER.warn("Template '{}' version {} replaced by version {}",
                    template.id(), existing.version(), template.version());
            }
            telemetry.emit("arena.template.version_replaced", Map.of(
                "templateId", template.id(),
                "oldVersion", existing.version(),
                "newVersion", template.version()
            ));
            stats.recordVersionReplacement();
        }

        // Store unresolved for inheritance chain resolution
        unresolvedTemplates.put(template.id(), template);

        // DD2: Resolve inheritance on-load
        ArenaTemplate resolved = resolveInheritance(template, new HashSet<>(), 0);

        // Validate the resolved template
        ValidationResult validation = validator.validate(resolved);
        if (!validation.valid()) {
            unresolvedTemplates.remove(template.id());
            throw new TemplateLoadException(template.id(), validation.errors());
        }

        // Cache the resolved template (immutable)
        registry.put(resolved.id(), resolved);
        generation.incrementAndGet();

        if (loggingEnabled) {
            LOGGER.info("Template '{}' v{} loaded successfully", resolved.id(), resolved.version());
        }
        telemetry.emit("arena.template.loaded", Map.of(
            "templateId", resolved.id(),
            "version", resolved.version(),
            "hasParent", template.extendsTemplate() != null,
            "generation", generation.get()
        ));

        // DD13: Emit TemplateRegistered event
        eventDispatcher.emitTemplateRegistered(resolved.id(), resolved.version(), "registry.load");

        stats.recordLoad();
    }

    /**
     * Gets a template by ID. O(1) operation since inheritance is already resolved.
     *
     * @param id The template ID
     * @return Optional containing the resolved template, or empty if not found
     */
    public Optional<ArenaTemplate> get(String id) {
        // DD2: O(1) - already resolved, no inheritance logic
        return Optional.ofNullable(registry.get(id));
    }

    /**
     * Gets a template, returning default if not found.
     *
     * @param id The template ID
     * @return The template, or default_flat_64 if not found
     */
    public ArenaTemplate getOrDefault(String id) {
        ArenaTemplate template = registry.get(id);
        if (template == null) {
            if (loggingEnabled) {
                LOGGER.warn("Template '{}' not found, falling back to default", id);
            }
            telemetry.emit("arena.template.fallback", Map.of(
                "requestedId", id,
                "fallbackId", "default_flat_64"
            ));
            stats.recordFallback();
            return registry.getOrDefault("default_flat_64", ArenaTemplate.defaultTemplate());
        }
        return template;
    }

    /**
     * Returns all loaded templates.
     *
     * @return Unmodifiable collection of all templates
     */
    public Collection<ArenaTemplate> all() {
        return Collections.unmodifiableCollection(registry.values());
    }

    /**
     * Returns all templates with their status.
     *
     * @return Map of template ID to status info
     */
    public Map<String, TemplateStatus> allWithStatus() {
        Map<String, TemplateStatus> result = new LinkedHashMap<>();
        for (ArenaTemplate template : registry.values()) {
            result.put(template.id(), new TemplateStatus(
                template.id(),
                template.version(),
                template.extendsTemplate() != null,
                TemplateState.ACTIVE
            ));
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Unloads a template from the registry.
     *
     * @param id The template ID to unload
     * @return true if the template was unloaded, false if not found
     */
    public boolean unload(String id) {
        ArenaTemplate removed = registry.remove(id);
        unresolvedTemplates.remove(id);
        if (removed != null) {
            generation.incrementAndGet();
            if (loggingEnabled) {
                LOGGER.info("Template '{}' unloaded", id);
            }
            telemetry.emit("arena.template.unloaded", Map.of(
                "templateId", id,
                "version", removed.version()
            ));

            // DD13: Emit TemplateUnregistered event
            eventDispatcher.emitTemplateUnregistered(id, "registry.unload");

            stats.recordUnload();
            return true;
        }
        return false;
    }

    /**
     * Hot-reload: atomically swaps the registry content.
     *
     * @param templates The new set of templates to load
     * @return Reload result with success/failure info
     */
    public ReloadResult hotReload(Collection<ArenaTemplate> templates) {
        if (loggingEnabled) {
            LOGGER.info("Hot-reload started with {} templates", templates.size());
        }
        Instant startTime = Instant.now();

        ConcurrentHashMap<String, ArenaTemplate> newRegistry = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, ArenaTemplate> newUnresolved = new ConcurrentHashMap<>();
        List<String> errors = new ArrayList<>();

        // First pass: store all unresolved
        for (ArenaTemplate template : templates) {
            newUnresolved.put(template.id(), template);
        }

        // Second pass: resolve and validate
        for (ArenaTemplate template : templates) {
            try {
                ArenaTemplate resolved = resolveInheritanceFrom(template, newUnresolved, new HashSet<>(), 0);
                ValidationResult validation = validator.validate(resolved);
                if (validation.valid()) {
                    newRegistry.put(resolved.id(), resolved);
                } else {
                    errors.add("Template '%s': %s".formatted(template.id(), validation.errors()));
                }
            } catch (Exception e) {
                errors.add("Template '%s': %s".formatted(template.id(), e.getMessage()));
            }
        }

        if (errors.isEmpty()) {
            // DD13: Emit TemplateUnregistered for old templates being replaced
            for (ArenaTemplate oldTemplate : registry.values()) {
                eventDispatcher.emitTemplateUnregistered(oldTemplate.id(), "hot-reload.replaced");
            }

            // Atomic swap
            registry.clear();
            registry.putAll(newRegistry);
            unresolvedTemplates.clear();
            unresolvedTemplates.putAll(newUnresolved);
            // Leak prevention: prune locks/listeners for removed templates
            templateLocks.keySet().removeIf(id -> !registry.containsKey(id));
            listenerHandles.keySet().removeIf(id -> !registry.containsKey(id));
            int newGen = generation.incrementAndGet();

            // DD13: Emit TemplateRegistered for all new templates
            for (ArenaTemplate newTemplate : newRegistry.values()) {
                eventDispatcher.emitTemplateRegistered(newTemplate.id(), newTemplate.version(), "hot-reload");
            }

            if (loggingEnabled) {
                LOGGER.info("Hot-reload completed successfully, generation {}", newGen);
            }
            telemetry.emit("arena.template.hot_reload", Map.of(
                "success", true,
                "templateCount", newRegistry.size(),
                "generation", newGen,
                "durationMs", java.time.Duration.between(startTime, Instant.now()).toMillis()
            ));

            return new ReloadResult(true, newRegistry.size(), List.of());
        } else {
            if (loggingEnabled) {
                LOGGER.error("Hot-reload failed with {} errors", errors.size());
            }
            telemetry.emit("arena.template.hot_reload", Map.of(
                "success", false,
                "errorCount", errors.size(),
                "errors", errors
            ));

            return new ReloadResult(false, 0, errors);
        }
    }

    /**
     * Loads all templates from a directory using the configured validator + schema mode.
     */
    public TemplateLoader.LoadResult loadFromDirectory(Path directory, TemplateValidator.ValidationMode mode) {
        validator.setMode(mode);
        TemplateLoader loader = new TemplateLoader(validator, mode, telemetry);
        TemplateLoader.LoadResult result = loader.loadAllSources(directory);
        for (ArenaTemplate t : result.templates()) {
            try {
                load(t);
            } catch (Exception e) {
                if (loggingEnabled) {
                    LOGGER.error("Failed to load template {} from {}", t.id(), directory, e);
                }
            }
        }

        // Fallback injection if nothing loaded
        if (registry.isEmpty()) {
            if (loggingEnabled) {
                LOGGER.warn("No templates loaded from {}. Injecting built-in fallbacks.", directory);
            }
            injectFallbackTemplates(directory, "load_empty");
        }
        return result;
    }

    /**
     * Loads all templates from a directory using validation mode from config/env (default STRICT).
     * Env var: DEVMOD_TEMPLATE_VALIDATION_MODE, SysProp: devmod.template.validationMode
     */
    public TemplateLoader.LoadResult loadFromDirectory(Path directory) {
        TemplateValidator.ValidationMode mode = resolveValidationMode();
        return loadFromDirectory(directory, mode);
    }

    /**
     * Atomic reload from directory: parses/validates all templates first, then hot-reloads if no errors.
     * Uses validation mode from env/sysprop (same as {@link #loadFromDirectory(Path)}).
     */
    public ReloadResult reloadFromDirectoryAtomic(Path directory) {
        TemplateValidator.ValidationMode mode = resolveValidationMode();
        TemplateLoader loader = new TemplateLoader(validator, mode, telemetry);
        TemplateLoader.LoadResult loadResult = loader.loadAllSources(directory);

        if (!loadResult.success()) {
            if (loggingEnabled) {
                LOGGER.error("Template reload aborted, {} errors", loadResult.errors().size());
            }
            // Leak prevention: clear ephemeral handles/locks even on failed reload attempts
            clearEphemeralState();
            return new ReloadResult(false, 0, loadResult.errors());
        }

        ReloadResult rr = hotReload(loadResult.templates());

        if (!rr.success() && registry.isEmpty()) {
            // If reload failed and registry would be empty, ensure fallback
            injectFallbackTemplates(directory, "reload_empty");
        }
        // Leak prevention: prune stale handles/locks on any reload attempt
        pruneEphemeralState();
        return rr;
    }

    private void injectFallbackTemplates(Path directory, String reason) {
        List<ArenaTemplate> fallbacks = List.of(
            ArenaTemplate.defaultTemplate(),
            ArenaTemplate.bossRing80Template(),
            ArenaTemplate.smokeFlat64Template()
        );
        for (ArenaTemplate template : fallbacks) {
            registry.put(template.id(), template);
        }
        telemetry.emit("arena.template.fallback_injected", Map.of(
            "source", directory.toString(),
            "reason", reason,
            "templates", fallbacks.stream().map(ArenaTemplate::id).toList()
        ));
    }

    private TemplateValidator.ValidationMode resolveValidationMode() {
        String configured = null;
        ArenaTemplateConfig.ConfigSnapshot snapshot = configSnapshot;
        if (snapshot != null && snapshot.schemaValidationMode() != null) {
            configured = snapshot.schemaValidationMode();
        }
        if (configured == null || configured.isBlank()) {
            String env = System.getenv("DEVMOD_TEMPLATE_VALIDATION_MODE");
            String prop = System.getProperty("devmod.template.validationMode");
            configured = prop != null ? prop : env;
        }
        return TemplateValidator.fromString(configured, TemplateValidator.ValidationMode.STRICT);
    }

    /**
     * Gets current registry generation. Increments on any modification.
     *
     * @return Current generation number
     */
    public int getGeneration() {
        return generation.get();
    }

    /**
     * Gets registry statistics.
     *
     * @return Current stats
     */
    public RegistryStats getStats() {
        return stats;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            ScheduledFuture<?> task = healthCheckTask;
            if (task != null) {
                task.cancel(true);
            }
            healthCheckExecutor.shutdownNow();
        } catch (Exception e) {
            if (loggingEnabled) {
                LOGGER.warn("Failed to shut down template registry health executor", e);
            }
        }
        clearEphemeralState();
        registry.clear();
        unresolvedTemplates.clear();
    }

    private void clearEphemeralState() {
        templateLocks.clear();
        listenerHandles.clear();
    }

    private void pruneEphemeralState() {
        templateLocks.keySet().removeIf(id -> !registry.containsKey(id));
        listenerHandles.keySet().removeIf(id -> !registry.containsKey(id));
    }

    private void runHealthCheck() {
        try {
            int locksBefore = templateLocks.size();
            int listenersBefore = listenerHandles.size();
            templateLocks.keySet().removeIf(id -> !registry.containsKey(id));
            listenerHandles.keySet().removeIf(id -> !registry.containsKey(id));
            int removedLocks = locksBefore - templateLocks.size();
            int removedListeners = listenersBefore - listenerHandles.size();
            if (removedLocks > 0 || removedListeners > 0) {
                telemetry.emit("arena.template.health_warning", Map.of(
                    "staleLocksRemoved", removedLocks,
                    "staleListenersRemoved", removedListeners,
                    "locksRemaining", templateLocks.size(),
                    "listenersRemaining", listenerHandles.size()
                ));
            }
        } catch (Exception e) {
            if (loggingEnabled) {
                LOGGER.warn("Template registry health check failed: {}", e.getMessage());
            }
        }
    }

    /**
     * Checks if a template exists.
     *
     * @param id The template ID
     * @return true if exists
     */
    public boolean contains(String id) {
        return registry.containsKey(id);
    }

    /**
     * Gets the count of loaded templates.
     *
     * @return Number of templates
     */
    public int size() {
        return registry.size();
    }

    // ===================
    // DD2: Inheritance Resolution
    // ===================

    private ArenaTemplate resolveInheritance(ArenaTemplate template, Set<String> visited, int depth) {
        return resolveInheritanceFrom(template, unresolvedTemplates, visited, depth);
    }

    private ArenaTemplate resolveInheritanceFrom(
            ArenaTemplate template,
            Map<String, ArenaTemplate> source,
            Set<String> visited,
            int depth) {

        // No parent - return as is
        if (template.extendsTemplate() == null) {
            return template;
        }

        if (depth >= MAX_INHERITANCE_DEPTH) {
            throw new InheritanceDepthExceededException(template.id(), depth, MAX_INHERITANCE_DEPTH);
        }

        String parentId = template.extendsTemplate();

        // Cycle detection
        if (visited.contains(template.id())) {
            throw new InheritanceCycleException(template.id(), visited);
        }
        visited.add(template.id());

        // Find parent
        ArenaTemplate parent = source.get(parentId);
        if (parent == null) {
            parent = registry.get(parentId);
        }
        if (parent == null) {
            throw new ParentTemplateNotFoundException(template.id(), parentId);
        }

        // Diamond detection: parent already seen elsewhere in chain
        if (visited.contains(parentId)) {
            throw new DiamondInheritanceException(template.id(), parentId, visited);
        }

        // Version compatibility: parent breaking change requires child minParentVersion >= parent.version
        if (parent.breakingChange() && parent.version() > 0) {
            Integer minParentVersion = template.minParentVersion();
            if (minParentVersion == null || minParentVersion < parent.version()) {
                telemetry.emit("arena.template.inheritance_error", Map.of(
                    "templateId", template.id(),
                    "parentId", parentId,
                    "type", "BREAKING_PARENT_VERSION",
                    "parentVersion", parent.version(),
                    "minParentVersion", minParentVersion != null ? minParentVersion : -1
                ));
                throw new TemplateLoadException(template.id(),
                    List.of("Parent '%s' has breakingChange at v%d; child minParentVersion is %s"
                        .formatted(parentId, parent.version(),
                            minParentVersion == null ? "null" : minParentVersion)));
            }
        }

        // Recursively resolve parent first
        ArenaTemplate resolvedParent = resolveInheritanceFrom(parent, source, visited, depth + 1);

        // Merge child onto parent
        return mergeTemplates(resolvedParent, template);
    }

    /**
     * Merges child template onto parent using field-specific merge strategies.
     *
     * <p>Merge rules:
     * <ul>
     *   <li>OVERRIDE: Child value completely replaces parent (size, floor, walls, ceiling, etc.)</li>
     *   <li>SHALLOW_MERGE: Nested objects merged field-by-field (floor, walls, ceiling, etc.)</li>
     *   <li>SKIP: Field not inherited (id, extends)</li>
     * </ul>
     */
    private ArenaTemplate mergeTemplates(ArenaTemplate parent, ArenaTemplate child) {
        return new ArenaTemplate(
            // SKIP id/extends (child id, extends nulled)
            child.id(),
            null,
            child.version(),
            child.schemaVersion() > 0 ? child.schemaVersion() : parent.schemaVersion(),
            child.breakingChange(),
            child.deprecated() || parent.deprecated(),
            child.replacementVersion() != null ? child.replacementVersion() : parent.replacementVersion(),
            child.minParentVersion() != null ? child.minParentVersion() : parent.minParentVersion(),
            Objects.requireNonNull(child.templateType() != null ? child.templateType() : parent.templateType(),
                "Missing templateType during merge for " + child.id()),
            // SHALLOW_MERGE for objects (spec v2.23)
            mergeRequiredByStrategy("origin", child.origin(), parent.origin(), this::mergeOrigin),
            mergeIntByStrategy("size", child.size(), parent.size(), 0),
            mergeIntegerByStrategy("sizeX", child.sizeX(), parent.sizeX()),
            mergeIntegerByStrategy("sizeZ", child.sizeZ(), parent.sizeZ()),
            // ArenaShape and ringInnerRadius (added for circular/ring arena support)
            child.arenaShape() != null ? child.arenaShape() : parent.arenaShape(),
            mergeIntegerByStrategy("ringInnerRadius", child.ringInnerRadius(), parent.ringInnerRadius()),
            mergeRequiredByStrategy("floor", child.floor(), parent.floor(), this::mergeFloor),
            mergeRequiredByStrategy("walls", child.walls(), parent.walls(), this::mergeWalls),
            mergeRequiredByStrategy("ceiling", child.ceiling(), parent.ceiling(), this::mergeCeiling),
            mergeRequiredByStrategy("underfloor", child.underfloor(), parent.underfloor(), this::mergeUnderfloor),
            mergeRequiredByStrategy("palette", child.palette(), parent.palette(), this::mergePalette),
            mergeRequiredByStrategy("biome", child.biome(), parent.biome(), this::mergeBiome),
            mergeRequiredByStrategy("lighting", child.lighting(), parent.lighting(), this::mergeLighting),
            // OVERRIDE for arrays (spec v2.23)
            mergeListByStrategy("spawnSlots", child.spawnSlots(), parent.spawnSlots()),
            mergeRequiredByStrategy("playerSpawnOffset", child.playerSpawnOffset(), parent.playerSpawnOffset(), null),
            mergeRequiredByStrategy("mobSpawnStrategy", child.mobSpawnStrategy(), parent.mobSpawnStrategy(), null),
            mergeListByStrategy("forbiddenZones", child.forbiddenZones(), parent.forbiddenZones()),
            mergeListByStrategy("hazards", child.hazards(), parent.hazards()),
            // SHALLOW_MERGE for objects
            mergeRequiredByStrategy("environment", child.environment(), parent.environment(), this::mergeEnvironment),
            mergeRequiredByStrategy("compat", child.compat(), parent.compat(), this::mergeCompat),
            mergeRequiredByStrategy("instanceSettings", child.instanceSettings(), parent.instanceSettings(), this::mergeInstanceSettings),
            // OVERRIDE for structureNbt (complete replacement)
            mergeByStrategy("structureNbt", child.structureNbt(), parent.structureNbt(), null),
            mergeRequiredByStrategy("limits", child.limits(), parent.limits(), this::mergeLimits),
            mergeRequiredByStrategy("buildSettings", child.buildSettings(), parent.buildSettings(), this::mergeBuildSettings),
            // OVERRIDE for zoneSettings (complete replacement)
            mergeByStrategy("zoneSettings", child.zoneSettings(), parent.zoneSettings(), null),
            // OVERRIDE for arrays
            mergeListByStrategy("tags", child.tags(), parent.tags())
        );
    }

    private <T> List<T> overrideList(@Nullable List<T> child, @Nullable List<T> parent) {
        if (child != null) return List.copyOf(child);
        if (parent != null) return List.copyOf(parent);
        return List.of();
    }

    @Nullable
    private <T> T mergeByStrategy(String field,
                                  @Nullable T child,
                                  @Nullable T parent,
                                  @Nullable java.util.function.BiFunction<T, T, T> shallowMerger) {
        TemplateMergeRules.MergeStrategy strategy = TemplateMergeRules.strategyFor(field);
        return switch (strategy) {
            case SKIP -> parent;
            case OVERRIDE -> child != null ? child : parent;
            case SHALLOW_MERGE -> shallowMerger != null ? shallowMerger.apply(child, parent) : (child != null ? child : parent);
        };
    }

    private <T> T mergeRequiredByStrategy(String field,
                                          @Nullable T child,
                                          @Nullable T parent,
                                          @Nullable java.util.function.BiFunction<T, T, T> shallowMerger) {
        T merged = mergeByStrategy(field, child, parent, shallowMerger);
        return Objects.requireNonNull(merged, "Missing required '" + field + "' during merge");
    }

    private <T> List<T> mergeListByStrategy(String field,
                                            @Nullable List<T> child,
                                            @Nullable List<T> parent) {
        TemplateMergeRules.MergeStrategy strategy = TemplateMergeRules.strategyFor(field);
        return switch (strategy) {
            case SKIP -> parent != null ? List.copyOf(parent) : List.of();
            case OVERRIDE, SHALLOW_MERGE -> overrideList(child, parent);
        };
    }

    private int mergeIntByStrategy(String field, int child, int parent, int sentinel) {
        TemplateMergeRules.MergeStrategy strategy = TemplateMergeRules.strategyFor(field);
        if (strategy == TemplateMergeRules.MergeStrategy.SKIP) {
            return parent;
        }
        return child != sentinel ? child : parent;
    }

    @Nullable
    private Integer mergeIntegerByStrategy(String field, @Nullable Integer child, @Nullable Integer parent) {
        TemplateMergeRules.MergeStrategy strategy = TemplateMergeRules.strategyFor(field);
        if (strategy == TemplateMergeRules.MergeStrategy.SKIP) {
            return parent;
        }
        return child != null ? child : parent;
    }

    // ===================
    // SHALLOW_MERGE for nested objects (spec v2.23)
    // Child can override specific fields, rest inherited from parent
    // ===================

    @Nullable
    private ArenaTemplate.Origin mergeOrigin(@Nullable ArenaTemplate.Origin child, @Nullable ArenaTemplate.Origin parent) {
        if (child == null) return parent;
        if (parent == null) return child;
        return new ArenaTemplate.Origin(
            child.mode() != null ? child.mode() : parent.mode(),
            child.x(),
            child.y(),
            child.z()
        );
    }

    @Nullable
    private ArenaTemplate.Floor mergeFloor(@Nullable ArenaTemplate.Floor child, @Nullable ArenaTemplate.Floor parent) {
        if (child == null) return parent;
        if (parent == null) return child;
        return new ArenaTemplate.Floor(
            child.y() != 0 ? child.y() : parent.y(),
            child.thickness() != 0 ? child.thickness() : parent.thickness(),
            child.material() != null ? child.material() : parent.material(),
            child.pattern() != null ? child.pattern() : parent.pattern(),
            child.borderMaterial() != null ? child.borderMaterial() : parent.borderMaterial(),
            child.borderWidth() != 0 ? child.borderWidth() : parent.borderWidth()
        );
    }

    @Nullable
    private ArenaTemplate.Walls mergeWalls(@Nullable ArenaTemplate.Walls child, @Nullable ArenaTemplate.Walls parent) {
        if (child == null) return parent;
        if (parent == null) return child;
        return new ArenaTemplate.Walls(
            child.enabled(),  // boolean always from child if specified
            child.material() != null ? child.material() : parent.material(),
            child.height() != 0 ? child.height() : parent.height(),
            child.thickness() != 0 ? child.thickness() : parent.thickness(),
            child.startY() != 0 ? child.startY() : parent.startY(),
            child.style() != null ? child.style() : parent.style()
        );
    }

    @Nullable
    private ArenaTemplate.Ceiling mergeCeiling(@Nullable ArenaTemplate.Ceiling child, @Nullable ArenaTemplate.Ceiling parent) {
        if (child == null) return parent;
        if (parent == null) return child;
        return new ArenaTemplate.Ceiling(
            child.enabled(),
            child.material() != null ? child.material() : parent.material(),
            child.y() != 0 || parent == null ? child.y() : parent.y(),
            child.thickness() != 0 || parent == null ? child.thickness() : parent.thickness()
        );
    }

    @Nullable
    private ArenaTemplate.Underfloor mergeUnderfloor(@Nullable ArenaTemplate.Underfloor child, @Nullable ArenaTemplate.Underfloor parent) {
        if (child == null) return parent;
        if (parent == null) return child;
        return new ArenaTemplate.Underfloor(
            child.material() != null ? child.material() : parent.material(),
            child.depth() != 0 ? child.depth() : parent.depth(),
            child.sameAsFloor()
        );
    }

    @Nullable
    private ArenaTemplate.Palette mergePalette(@Nullable ArenaTemplate.Palette child, @Nullable ArenaTemplate.Palette parent) {
        if (child == null) return parent;
        if (parent == null) return child;
        return new ArenaTemplate.Palette(
            child.accent() != null ? child.accent() : parent.accent(),
            child.highlight() != null ? child.highlight() : parent.highlight(),
            child.hazardBorder() != null ? child.hazardBorder() : parent.hazardBorder()
        );
    }

    @Nullable
    private ArenaTemplate.Biome mergeBiome(@Nullable ArenaTemplate.Biome child, @Nullable ArenaTemplate.Biome parent) {
        if (child == null) return parent;
        if (parent == null) return child;
        return new ArenaTemplate.Biome(
            child.id() != null ? child.id() : parent.id(),
            child.applyTo() != null ? child.applyTo() : parent.applyTo()
        );
    }

    @Nullable
    private ArenaTemplate.Lighting mergeLighting(@Nullable ArenaTemplate.Lighting child, @Nullable ArenaTemplate.Lighting parent) {
        if (child == null) return parent;
        if (parent == null) return child;
        // lightSources is OVERRIDE (array inside shallow-merged object)
        return new ArenaTemplate.Lighting(
            child.skyLight() != 0 ? child.skyLight() : parent.skyLight(),
            child.blockLight() != 0 ? child.blockLight() : parent.blockLight(),
            child.ambientLight(),
            mergeListByStrategy("lighting.lightSources", child.lightSources(), parent.lightSources())
        );
    }

    @Nullable
    private ArenaTemplate.Environment mergeEnvironment(@Nullable ArenaTemplate.Environment child, @Nullable ArenaTemplate.Environment parent) {
        if (child == null) return parent;
        if (parent == null) return child;
        // particles is OVERRIDE (array inside shallow-merged object)
        return new ArenaTemplate.Environment(
            mergeListByStrategy("environment.particles", child.particles(), parent.particles()),
            child.ambientSound() != null ? child.ambientSound() : parent.ambientSound(),
            child.fog() != null ? child.fog() : parent.fog()
        );
    }

    @Nullable
    private ArenaTemplate.Compat mergeCompat(@Nullable ArenaTemplate.Compat child, @Nullable ArenaTemplate.Compat parent) {
        if (child == null) return parent;
        if (parent == null) return child;
        return new ArenaTemplate.Compat(
            child.minPlayers() != 0 ? child.minPlayers() : parent.minPlayers(),
            child.maxPlayers() != 0 ? child.maxPlayers() : parent.maxPlayers()
        );
    }

    @Nullable
    private ArenaTemplate.InstanceSettings mergeInstanceSettings(@Nullable ArenaTemplate.InstanceSettings child, @Nullable ArenaTemplate.InstanceSettings parent) {
        if (child == null) return parent;
        if (parent == null) return child;
        return new ArenaTemplate.InstanceSettings(
            child.chunkRadius() != 0 || parent == null ? child.chunkRadius() : parent.chunkRadius(),
            child.tickDistance() != 0 || parent == null ? child.tickDistance() : parent.tickDistance(),
            child.keepLoaded()
        );
    }

    @Nullable
    private ArenaTemplate.Limits mergeLimits(@Nullable ArenaTemplate.Limits child, @Nullable ArenaTemplate.Limits parent) {
        if (child == null) return parent;
        if (parent == null) return child;
        return new ArenaTemplate.Limits(
            child.maxBuildTimeMs() != 0 || parent == null ? child.maxBuildTimeMs() : parent.maxBuildTimeMs(),
            child.maxBlocks() != 0 || parent == null ? child.maxBlocks() : parent.maxBlocks(),
            child.maxEntities() != 0 || parent == null ? child.maxEntities() : parent.maxEntities()
        );
    }

    @Nullable
    private ArenaTemplate.BuildSettings mergeBuildSettings(@Nullable ArenaTemplate.BuildSettings child, @Nullable ArenaTemplate.BuildSettings parent) {
        if (child == null) return parent;
        if (parent == null) return child;
        return new ArenaTemplate.BuildSettings(
            child.buildPriority() != null ? child.buildPriority() : parent.buildPriority(),
            child.buildOrder() != null ? child.buildOrder() : parent.buildOrder()
        );
    }

    // ===================
    // Supporting Types
    // ===================

    public record TemplateStatus(
        String id,
        int version,
        boolean hasParent,
        TemplateState state
    ) {}

    /**
     * DD71: Template lifecycle states.
     * OBSOLETE indicates template is no longer usable (auto-transition after 90 days without use).
     */
    public enum TemplateState {
        ACTIVE,
        DEPRECATED,
        OBSOLETE
    }

    public record ReloadResult(
        boolean success,
        int loadedCount,
        List<String> errors
    ) {}

    public static class RegistryStats {
        private final AtomicInteger loads = new AtomicInteger(0);
        private final AtomicInteger unloads = new AtomicInteger(0);
        private final AtomicInteger fallbacks = new AtomicInteger(0);
        private final AtomicInteger versionReplacements = new AtomicInteger(0);

        void recordLoad() { loads.incrementAndGet(); }
        void recordUnload() { unloads.incrementAndGet(); }
        void recordFallback() { fallbacks.incrementAndGet(); }
        void recordVersionReplacement() { versionReplacements.incrementAndGet(); }

        public int getLoads() { return loads.get(); }
        public int getUnloads() { return unloads.get(); }
        public int getFallbacks() { return fallbacks.get(); }
        public int getVersionReplacements() { return versionReplacements.get(); }

        public Map<String, Integer> toMap() {
            return Map.of(
                "loads", loads.get(),
                "unloads", unloads.get(),
                "fallbacks", fallbacks.get(),
                "versionReplacements", versionReplacements.get()
            );
        }
    }
}
