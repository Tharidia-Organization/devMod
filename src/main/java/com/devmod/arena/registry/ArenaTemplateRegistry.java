package com.devmod.arena.registry;

import com.devmod.arena.telemetry.ArenaTelemetry;
import com.devmod.arena.config.ArenaTemplateConfig;
import com.devmod.arena.config.InstanceLimitConfig;
import com.devmod.arena.event.TemplateEventDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.Path;

/**
 * Registry for Arena Templates with version handling (DD1) and inheritance resolution (DD2).
 *
 * <p>Design Decisions:
 * <ul>
 *   <li>DD1: Last-Wins version handling - templates with same ID but different version replace the previous</li>
 *   <li>DD2: Inheritance resolved on-load with caching - O(1) for get(), already resolved and immutable</li>
 * </ul>
 *
 * @see <a href="TODO_ARENA_TEMPLATE.md">Arena Template Design Document</a>
 */
public class ArenaTemplateRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArenaTemplateRegistry.class);
    private static final int MAX_INHERITANCE_DEPTH = 3;

    private final ConcurrentHashMap<String, ArenaTemplate> registry = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ArenaTemplate> unresolvedTemplates = new ConcurrentHashMap<>();
    private final AtomicInteger generation = new AtomicInteger(0);
    private final ArenaTelemetry telemetry;
    private final TemplateValidator validator;
    private final TemplateEventDispatcher eventDispatcher;

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
        this.eventDispatcher = TemplateEventDispatcher.getInstance();
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

        // Optional: enable structure validation if manifest exists (default path)
        try {
            Path manifestPath = configSnapshot != null && configSnapshot.structureManifestPath() != null
                ? Path.of(configSnapshot.structureManifestPath())
                : Path.of("config/devmod/structures_manifest.json");
            StructureValidationInitializer.configure(this, manifestPath, Thread.currentThread().getContextClassLoader(), configSnapshot);
        } catch (Exception e) {
            LOGGER.warn("Failed to auto-configure structure validation: {}", e.getMessage());
        }
    }

    /**
     * Enable structure NBT validation using the provided manifest and data provider.
     */
    public void enableStructureValidation(StructureManifest manifest, TemplateValidator.StructureDataProvider provider) {
        this.validator.withStructureValidation(manifest, provider);
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
            LOGGER.warn("Template '{}' version {} replaced by version {}",
                template.id(), existing.version(), template.version());
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

        LOGGER.info("Template '{}' v{} loaded successfully", resolved.id(), resolved.version());
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
            LOGGER.warn("Template '{}' not found, falling back to default", id);
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
            LOGGER.info("Template '{}' unloaded", id);
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
        LOGGER.info("Hot-reload started with {} templates", templates.size());
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

            LOGGER.info("Hot-reload completed successfully, generation {}", newGen);
            telemetry.emit("arena.template.hot_reload", Map.of(
                "success", true,
                "templateCount", newRegistry.size(),
                "generation", newGen,
                "durationMs", java.time.Duration.between(startTime, Instant.now()).toMillis()
            ));

            return new ReloadResult(true, newRegistry.size(), List.of());
        } else {
            LOGGER.error("Hot-reload failed with {} errors", errors.size());
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
                LOGGER.error("Failed to load template {} from {}", t.id(), directory, e);
            }
        }

        // Fallback injection if nothing loaded
        if (registry.isEmpty()) {
            LOGGER.warn("No templates loaded from {}. Injecting default_flat_64 fallback.", directory);
            registry.put(ArenaTemplate.defaultTemplate().id(), ArenaTemplate.defaultTemplate());
            telemetry.emit("arena.template.fallback_injected", Map.of(
                "source", directory.toString()
            ));
        }
        return result;
    }

    /**
     * Loads all templates from a directory using validation mode from config/env (default STRICT).
     * Env var: DEVMOD_TEMPLATE_VALIDATION_MODE, SysProp: devmod.template.validationMode
     */
    public TemplateLoader.LoadResult loadFromDirectory(Path directory) {
        String env = System.getenv("DEVMOD_TEMPLATE_VALIDATION_MODE");
        String prop = System.getProperty("devmod.template.validationMode");
        TemplateValidator.ValidationMode mode = TemplateValidator.fromString(
            prop != null ? prop : env,
            TemplateValidator.ValidationMode.STRICT
        );
        return loadFromDirectory(directory, mode);
    }

    /**
     * Atomic reload from directory: parses/validates all templates first, then hot-reloads if no errors.
     * Uses validation mode from env/sysprop (same as {@link #loadFromDirectory(Path)}).
     */
    public ReloadResult reloadFromDirectoryAtomic(Path directory) {
        String env = System.getenv("DEVMOD_TEMPLATE_VALIDATION_MODE");
        String prop = System.getProperty("devmod.template.validationMode");
        TemplateValidator.ValidationMode mode = TemplateValidator.fromString(
            prop != null ? prop : env,
            TemplateValidator.ValidationMode.STRICT
        );
        TemplateLoader loader = new TemplateLoader(validator, mode, telemetry);
        TemplateLoader.LoadResult loadResult = loader.loadAll(directory);

        if (!loadResult.success()) {
            LOGGER.error("Template reload aborted, {} errors", loadResult.errors().size());
            // Leak prevention: clear ephemeral handles/locks even on failed reload attempts
            templateLocks.clear();
            listenerHandles.clear();
            return new ReloadResult(false, 0, loadResult.errors());
        }

        ReloadResult rr = hotReload(loadResult.templates());

        if (!rr.success() && registry.isEmpty()) {
            // If reload failed and registry would be empty, ensure fallback
            registry.put(ArenaTemplate.defaultTemplate().id(), ArenaTemplate.defaultTemplate());
            telemetry.emit("arena.template.fallback_injected", Map.of(
                "source", directory.toString(),
                "reason", "reload_empty"
            ));
        }
        // Leak prevention: clear stale listener handles on any reload attempt
        listenerHandles.clear();
        return rr;
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
            child.templateType() != null ? child.templateType() : parent.templateType(),
            // SHALLOW_MERGE for objects (spec v2.23)
            mergeOrigin(child.origin(), parent.origin()),
            child.size() > 0 ? child.size() : parent.size(),
            child.sizeX() != null ? child.sizeX() : parent.sizeX(),
            child.sizeZ() != null ? child.sizeZ() : parent.sizeZ(),
            mergeFloor(child.floor(), parent.floor()),
            mergeWalls(child.walls(), parent.walls()),
            mergeCeiling(child.ceiling(), parent.ceiling()),
            mergeUnderfloor(child.underfloor(), parent.underfloor()),
            mergePalette(child.palette(), parent.palette()),
            mergeBiome(child.biome(), parent.biome()),
            mergeLighting(child.lighting(), parent.lighting()),
            // OVERRIDE for arrays (spec v2.23)
            overrideList(child.spawnSlots(), parent.spawnSlots()),
            child.playerSpawnOffset() != null ? child.playerSpawnOffset() : parent.playerSpawnOffset(),
            child.mobSpawnStrategy() != null ? child.mobSpawnStrategy() : parent.mobSpawnStrategy(),
            overrideList(child.forbiddenZones(), parent.forbiddenZones()),
            overrideList(child.hazards(), parent.hazards()),
            // SHALLOW_MERGE for objects
            mergeEnvironment(child.environment(), parent.environment()),
            mergeCompat(child.compat(), parent.compat()),
            mergeInstanceSettings(child.instanceSettings(), parent.instanceSettings()),
            // OVERRIDE for structureNbt (complete replacement)
            child.structureNbt() != null ? child.structureNbt() : parent.structureNbt(),
            mergeLimits(child.limits(), parent.limits()),
            mergeBuildSettings(child.buildSettings(), parent.buildSettings()),
            // OVERRIDE for arrays
            overrideList(child.tags(), parent.tags())
        );
    }

    private <T> List<T> overrideList(@Nullable List<T> child, @Nullable List<T> parent) {
        if (child != null) return List.copyOf(child);
        if (parent != null) return List.copyOf(parent);
        return List.of();
    }

    // ===================
    // SHALLOW_MERGE for nested objects (spec v2.23)
    // Child can override specific fields, rest inherited from parent
    // ===================

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

    private ArenaTemplate.Underfloor mergeUnderfloor(@Nullable ArenaTemplate.Underfloor child, @Nullable ArenaTemplate.Underfloor parent) {
        if (child == null) return parent;
        if (parent == null) return child;
        return new ArenaTemplate.Underfloor(
            child.material() != null ? child.material() : parent.material(),
            child.depth() != 0 ? child.depth() : parent.depth(),
            child.sameAsFloor()
        );
    }

    private ArenaTemplate.Palette mergePalette(@Nullable ArenaTemplate.Palette child, @Nullable ArenaTemplate.Palette parent) {
        if (child == null) return parent;
        if (parent == null) return child;
        return new ArenaTemplate.Palette(
            child.accent() != null ? child.accent() : parent.accent(),
            child.highlight() != null ? child.highlight() : parent.highlight(),
            child.hazardBorder() != null ? child.hazardBorder() : parent.hazardBorder()
        );
    }

    private ArenaTemplate.Biome mergeBiome(@Nullable ArenaTemplate.Biome child, @Nullable ArenaTemplate.Biome parent) {
        if (child == null) return parent;
        if (parent == null) return child;
        return new ArenaTemplate.Biome(
            child.id() != null ? child.id() : parent.id(),
            child.applyTo() != null ? child.applyTo() : parent.applyTo()
        );
    }

    private ArenaTemplate.Lighting mergeLighting(@Nullable ArenaTemplate.Lighting child, @Nullable ArenaTemplate.Lighting parent) {
        if (child == null) return parent;
        if (parent == null) return child;
        // lightSources is OVERRIDE (array inside shallow-merged object)
        return new ArenaTemplate.Lighting(
            child.skyLight() != 0 ? child.skyLight() : parent.skyLight(),
            child.blockLight() != 0 ? child.blockLight() : parent.blockLight(),
            child.ambientLight(),
            child.lightSources() != null ? child.lightSources() : parent.lightSources()
        );
    }

    private ArenaTemplate.Environment mergeEnvironment(@Nullable ArenaTemplate.Environment child, @Nullable ArenaTemplate.Environment parent) {
        if (child == null) return parent;
        if (parent == null) return child;
        // particles is OVERRIDE (array inside shallow-merged object)
        return new ArenaTemplate.Environment(
            child.particles() != null ? child.particles() : parent.particles(),
            child.ambientSound() != null ? child.ambientSound() : parent.ambientSound(),
            child.fog() != null ? child.fog() : parent.fog()
        );
    }

    private ArenaTemplate.Compat mergeCompat(@Nullable ArenaTemplate.Compat child, @Nullable ArenaTemplate.Compat parent) {
        if (child == null) return parent;
        if (parent == null) return child;
        return new ArenaTemplate.Compat(
            child.minPlayers() != 0 ? child.minPlayers() : parent.minPlayers(),
            child.maxPlayers() != 0 ? child.maxPlayers() : parent.maxPlayers()
        );
    }

    private ArenaTemplate.InstanceSettings mergeInstanceSettings(@Nullable ArenaTemplate.InstanceSettings child, @Nullable ArenaTemplate.InstanceSettings parent) {
        if (child == null) return parent;
        if (parent == null) return child;
        return new ArenaTemplate.InstanceSettings(
            child.chunkRadius() != 0 || parent == null ? child.chunkRadius() : parent.chunkRadius(),
            child.tickDistance() != 0 || parent == null ? child.tickDistance() : parent.tickDistance(),
            child.keepLoaded()
        );
    }

    private ArenaTemplate.Limits mergeLimits(@Nullable ArenaTemplate.Limits child, @Nullable ArenaTemplate.Limits parent) {
        if (child == null) return parent;
        if (parent == null) return child;
        return new ArenaTemplate.Limits(
            child.maxBuildTimeMs() != 0 || parent == null ? child.maxBuildTimeMs() : parent.maxBuildTimeMs(),
            child.maxBlocks() != 0 || parent == null ? child.maxBlocks() : parent.maxBlocks(),
            child.maxEntities() != 0 || parent == null ? child.maxEntities() : parent.maxEntities()
        );
    }

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
