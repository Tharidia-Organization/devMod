package com.devmod.arena.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Template validator orchestrating hazard + spawn validation and basic schema checks.
 */
public class TemplateValidator {

    private static final int MAX_SIZE = 256;
    private static final int MIN_SIZE = 8;
    private static final int MAX_SPAWN_SLOTS = 100;
    private static final int MAX_FORBIDDEN_ZONES = 20;
    private static final int MAX_LIGHT_SOURCES = 50;
    private static final int MAX_PLAYER_SPAWN_OFFSET = 16; // sanity clamp
    private static final List<String> ALLOWED_ROTATIONS = List.of("none", "clockwise_90", "180", "counterclockwise_90");
    private static final List<String> ALLOWED_MIRRORS = List.of("none", "front_back", "left_right");
    private static final List<String> ALLOWED_SEED_POLICIES = List.of("fixed", "perRun");
    private static final Set<String> DIMENSION_TAGS = Set.of("overworld", "nether", "end", "indoor", "outdoor");

    private ValidationMode mode = ValidationMode.STRICT;
    private InstanceSettingsValidator.InstanceLimits instanceLimits = InstanceSettingsValidator.InstanceLimits.defaults();
    private InstanceSettingsValidator.Telemetry instanceTelemetry;
    private StructureManifest structureManifest;
    private StructureDataProvider structureDataProvider;
    private StructureFallbackConfig structureFallbackConfig;
    private StructureTelemetry structureTelemetry;
    private HazardValidator.HazardTelemetry hazardTelemetry;
    private java.util.Set<String> registeredCustomHazards;
    private static final int EXPECTED_SCHEMA_VERSION = 1;
    private static final List<String> ALLOWED_PARTICLE_AREAS = List.of("bounds", "chunks");

    /**
     * Validation strictness mode for template validation.
     *
     * <p>Controls how validation errors are handled:
     *
     * <ul>
     *   <li><b>STRICT</b> (default): All errors cause validation failure.
     *       Use in production to ensure only valid templates are loaded.
     *       Any error in the errors list means {@code valid=false}.</li>
     *
     *   <li><b>PERMISSIVE</b>: Bounds-related errors are treated as warnings.
     *       Validation passes if no non-bounds errors exist. Useful during
     *       template development when spawn positions may temporarily exceed
     *       arena bounds. Errors are still collected for review.</li>
     *
     *   <li><b>LENIENT</b>: All errors are converted to warnings.
     *       Validation always passes ({@code valid=true}) but errors are
     *       moved to the warnings list. Use only for debugging or migration
     *       scenarios. Not recommended for production.</li>
     * </ul>
     *
     * <p>Set via environment variable {@code DEVMOD_TEMPLATE_VALIDATION_MODE}
     * or system property {@code devmod.arena.validationMode}.
     *
     * @see #fromString(String, ValidationMode)
     */
    public enum ValidationMode {
        /** All errors fail validation. Production default. */
        STRICT,
        /** Bounds errors become warnings; other errors still fail. */
        PERMISSIVE,
        /** All errors become warnings; validation always passes. Debug only. */
        LENIENT
    }

    /**
     * Parses a validation mode from string with fallback.
     */
    public static ValidationMode fromString(String value, ValidationMode defaultMode) {
        if (value == null || value.isBlank()) return defaultMode;
        return switch (value.trim().toLowerCase()) {
            case "strict" -> ValidationMode.STRICT;
            case "permissive" -> ValidationMode.PERMISSIVE;
            case "lenient" -> ValidationMode.LENIENT;
            default -> defaultMode;
        };
    }

    public TemplateValidator() {}

    public TemplateValidator(ValidationMode mode) {
        this.mode = mode;
    }

    public TemplateValidator withInstanceLimits(InstanceSettingsValidator.InstanceLimits limits) {
        this.instanceLimits = limits;
        return this;
    }

    public TemplateValidator withHazardTelemetry(HazardValidator.HazardTelemetry telemetry) {
        this.hazardTelemetry = telemetry;
        return this;
    }

    public TemplateValidator withInstanceTelemetry(InstanceSettingsValidator.Telemetry telemetry) {
        this.instanceTelemetry = telemetry;
        return this;
    }

    public TemplateValidator withRegisteredCustomHazards(java.util.Set<String> builderIds) {
        this.registeredCustomHazards = builderIds;
        return this;
    }

    /**
     * Provide manifest + data provider for structureNbt validation (checksum/limits).
     */
    public TemplateValidator withStructureValidation(StructureManifest manifest, StructureDataProvider provider) {
        this.structureManifest = manifest;
        this.structureDataProvider = provider;
        return this;
    }

    /**
     * Provide fallback limits for structure validation when manifest is missing.
     */
    public TemplateValidator withStructureFallbackConfig(StructureFallbackConfig config) {
        this.structureFallbackConfig = config;
        return this;
    }

    /**
     * Provide telemetry for structure validation outcomes.
     */
    public TemplateValidator withStructureTelemetry(StructureTelemetry telemetry) {
        this.structureTelemetry = telemetry;
        return this;
    }

    public void setMode(ValidationMode mode) {
        this.mode = mode;
    }

    public ValidationResult validate(ArenaTemplate template) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Schema version check (int-based)
        if (template.schemaVersion() != EXPECTED_SCHEMA_VERSION) {
            if (template.schemaVersion() / 1000 > EXPECTED_SCHEMA_VERSION / 1000) {
                errors.add("schemaVersion %d is incompatible with loader %d".formatted(template.schemaVersion(), EXPECTED_SCHEMA_VERSION));
            } else {
                warnings.add("schemaVersion %d differs from loader %d".formatted(template.schemaVersion(), EXPECTED_SCHEMA_VERSION));
            }
        }

        validateRequiredFields(template, errors);

        int maxDim = computeMaxDim(template);
        Integer sizeXVal = template.sizeX();
        Integer sizeZVal = template.sizeZ();
        int sizeX = sizeXVal != null ? sizeXVal : template.size();
        int sizeZ = sizeZVal != null ? sizeZVal : template.size();
        validateSizeBounds(maxDim, errors);
        validateLighting(template, errors);
        validateFloor(template.floor(), errors);
        validateWalls(template.walls(), errors);
        validateCeiling(template.ceiling(), errors);
        validateUnderfloor(template.underfloor(), errors);
        validatePalette(template.palette(), warnings, errors);
        validateBiome(template.biome(), errors);
        validateForbiddenZones(template, template.forbiddenZones(), sizeX, sizeZ, errors);
        validateStructureNbt(template, errors, warnings);
        validateMobSpawnStrategy(template, errors, warnings, sizeX, sizeZ);
        validateTags(template.tags(), warnings);
        validateEnvironment(template.environment(), warnings, errors);

        // Build arena bounds for spawn/hazard checks
        Bounds bounds = computeBounds(template);

        // Hazards
        HazardValidator hazardValidator = new HazardValidator(hazardTelemetry, registeredCustomHazards);
        ValidationResult hazardResult = hazardValidator.validate(template, bounds, template.spawnSlots());
        errors.addAll(hazardResult.errors());
        warnings.addAll(hazardResult.warnings());

        // Spawn slots
        if (template.spawnSlots() != null && template.spawnSlots().size() > MAX_SPAWN_SLOTS) {
            errors.add("Too many spawnSlots, max " + MAX_SPAWN_SLOTS);
        }
        SpawnSlotValidator spawnSlotValidator = new SpawnSlotValidator();
        ValidationResult spawnResult = spawnSlotValidator.validate(template, bounds);
        errors.addAll(spawnResult.errors());
        warnings.addAll(spawnResult.warnings());

        // Limits & instance settings
        validateLimits(template, errors);
        InstanceSettingsValidator isValidator = new InstanceSettingsValidator(instanceTelemetry);
        var isResult = isValidator.validate(template, instanceLimits);
        errors.addAll(isResult.errors());
        warnings.addAll(isResult.warnings());

        if (mode == ValidationMode.LENIENT) {
            return new ValidationResult(errors.isEmpty(), List.of(), warnings);
        }
        if (mode == ValidationMode.PERMISSIVE) {
            return new ValidationResult(errors.stream().noneMatch(e -> !e.contains("bounds")), errors, warnings);
        }
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    private void validateFloor(ArenaTemplate.Floor floor, List<String> errors) {
        if (floor == null) {
            errors.add("floor is required");
            return;
        }
        if (floor.thickness() <= 0) {
            errors.add("floor.thickness must be >0");
        }
        if (floor.pattern() == null || floor.pattern().isBlank()) {
            errors.add("floor.pattern is required");
        } else if (!List.of("solid", "checkerboard", "border").contains(floor.pattern())) {
            errors.add("floor.pattern must be solid|checkerboard|border");
        }
        if (floor.borderWidth() < 0) {
            errors.add("floor.borderWidth must be >=0");
        }
        if (floor.material() == null || floor.material().isBlank()) {
            errors.add("floor.material is required");
        }
        var borderMaterial = floor.borderMaterial();
        if (borderMaterial == null || borderMaterial.isBlank()) {
            errors.add("floor.borderMaterial is required");
        }
    }

    private void validateWalls(ArenaTemplate.Walls walls, List<String> errors) {
        if (walls == null) return;
        if (walls.height() <= 0) {
            errors.add("walls.height must be >0");
        }
        if (walls.thickness() <= 0) {
            errors.add("walls.thickness must be >0");
        }
        if (walls.material() == null || walls.material().isBlank()) {
            errors.add("walls.material is required");
        }
        if (walls.style() == null || walls.style().isBlank()) {
            errors.add("walls.style is required");
        }
    }

    private void validateCeiling(ArenaTemplate.Ceiling ceiling, List<String> errors) {
        if (ceiling == null) return;
        if (ceiling.enabled() && ceiling.thickness() <= 0) {
            errors.add("ceiling.thickness must be >0 when enabled");
        }
        if (ceiling.enabled() && (ceiling.material() == null || ceiling.material().isBlank())) {
            errors.add("ceiling.material is required when enabled");
        }
    }

    private void validateUnderfloor(ArenaTemplate.Underfloor underfloor, List<String> errors) {
        if (underfloor == null) return;
        if (underfloor.depth() < 0) {
            errors.add("underfloor.depth must be >=0");
        }
        if (underfloor.material() == null || underfloor.material().isBlank()) {
            errors.add("underfloor.material is required");
        }
    }

    private void validatePalette(ArenaTemplate.Palette palette, List<String> warnings, List<String> errors) {
        if (palette == null) return;
        if (palette.accent() == null || palette.accent().isBlank()) {
            errors.add("palette.accent is required");
        }
        if (palette.highlight() == null || palette.highlight().isBlank()) {
            errors.add("palette.highlight is required");
        }
        if (palette.hazardBorder() == null || palette.hazardBorder().isBlank()) {
            errors.add("palette.hazardBorder is required");
        }
    }

    private void validateBiome(ArenaTemplate.Biome biome, List<String> errors) {
        if (biome == null) return;
        if (biome.id() == null || biome.id().isBlank()) {
            errors.add("biome.id is required");
        }
    }

    private void validateEnvironment(ArenaTemplate.Environment env, List<String> warnings, List<String> errors) {
        if (env == null) return;
        if (env.particles() != null) {
            for (int i = 0; i < env.particles().size(); i++) {
                var p = env.particles().get(i);
                if (p.type() == null || p.type().isBlank()) {
                    errors.add("environment.particles[%d].type is required".formatted(i));
                }
                if (p.rate() < 0) {
                    warnings.add("environment.particles[%d].rate < 0".formatted(i));
                }
                if (p.area() != null && !ALLOWED_PARTICLE_AREAS.contains(p.area())) {
                    warnings.add("environment.particles[%d].area '%s' not in %s".formatted(i, p.area(), ALLOWED_PARTICLE_AREAS));
                }
            }
        }
        if (env.fog() != null) {
            double density = env.fog().density();
            if (density < 0.0 || density > 1.0) {
                warnings.add("environment.fog.density %.3f clamped to [0,1]".formatted(density));
            }
        }
        String ambientSound = env.ambientSound();
        if (ambientSound != null && ambientSound.isBlank()) {
            warnings.add("environment.ambientSound is blank");
        }
    }

    private void validateTags(List<String> tags, List<String> warnings) {
        if (tags == null) return;
        Set<String> dimensionFound = new java.util.HashSet<>();
        for (String tag : tags) {
            if (tag == null || tag.isBlank()) {
                warnings.add("tags contains blank entry");
            } else if (tag.contains(" ")) {
                warnings.add("tag '%s' contains whitespace".formatted(tag));
            } else if (DIMENSION_TAGS.contains(tag.toLowerCase())) {
                dimensionFound.add(tag.toLowerCase());
            }
        }
        if (dimensionFound.size() > 1) {
            warnings.add("Multiple dimension tags present: " + dimensionFound);
        }
    }

    private void validateRequiredFields(ArenaTemplate template, List<String> errors) {
        if (template.id() == null || template.id().isBlank()) {
            errors.add("Template ID is required");
        } else if (!isValidId(template.id())) {
            errors.add("Template ID must be lowercase alphanumeric with underscores, max 32 chars");
        }
        if (template.schemaVersion() <= 0) {
            errors.add("schemaVersion must be >=1");
        }
        if (template.version() < 1) {
            errors.add("version must be >=1");
        }
        if (template.origin() == null) {
            errors.add("origin is required");
        } else if (template.origin().mode() == null) {
            errors.add("origin.mode is required");
        }
        if (template.floor() == null) {
            errors.add("floor is required");
        }
        if (template.playerSpawnOffset() != null) {
            int ox = Math.abs(template.playerSpawnOffset().x());
            int oy = Math.abs(template.playerSpawnOffset().y());
            int oz = Math.abs(template.playerSpawnOffset().z());
            if (ox > MAX_PLAYER_SPAWN_OFFSET || oy > MAX_PLAYER_SPAWN_OFFSET || oz > MAX_PLAYER_SPAWN_OFFSET) {
                errors.add("playerSpawnOffset exceeds sanity limit " + MAX_PLAYER_SPAWN_OFFSET);
            }
        }
        if (template.mobSpawnStrategy() == null) {
            errors.add("mobSpawnStrategy is required");
        }
    }

    private void validateSizeBounds(int maxDim, List<String> errors) {
        if (maxDim < MIN_SIZE || maxDim > MAX_SIZE) {
            errors.add("size/sizeX/sizeZ must be between %d and %d".formatted(MIN_SIZE, MAX_SIZE));
        }
    }

    private void validateLighting(ArenaTemplate template, List<String> errors) {
        if (template.lighting() == null) return;
        if (template.lighting().skyLight() < 0 || template.lighting().skyLight() > 15) {
            errors.add("skyLight must be 0-15");
        }
        if (template.lighting().blockLight() < 0 || template.lighting().blockLight() > 15) {
            errors.add("blockLight must be 0-15");
        }
        if (template.lighting().lightSources() != null && template.lighting().lightSources().size() > MAX_LIGHT_SOURCES) {
            errors.add("Too many lightSources, max " + MAX_LIGHT_SOURCES);
        }
        if (template.lighting().lightSources() != null) {
            for (int i = 0; i < template.lighting().lightSources().size(); i++) {
                var ls = template.lighting().lightSources().get(i);
                if (ls.pos() == null || ls.pos().length != 3) {
                    errors.add("lighting.lightSources[%d].pos must be int[3]".formatted(i));
                }
                if (ls.block() == null || ls.block().isBlank()) {
                    errors.add("lighting.lightSources[%d].block is required".formatted(i));
                }
            }
        }
    }

    private void validateForbiddenZones(ArenaTemplate template, List<ArenaTemplate.ForbiddenZone> zones, int sizeX, int sizeZ, List<String> errors) {
        if (zones == null) return;
        if (zones.size() > MAX_FORBIDDEN_ZONES) {
            errors.add("Too many forbiddenZones, max " + MAX_FORBIDDEN_ZONES);
        }
        int minAllowedX = -sizeX / 2;
        int maxAllowedX = sizeX / 2 - 1;
        int minAllowedZ = -sizeZ / 2;
        int maxAllowedZ = sizeZ / 2 - 1;
        for (int i = 0; i < zones.size(); i++) {
            var zone = zones.get(i);
            if (zone.min() == null || zone.max() == null || zone.min().length != 3 || zone.max().length != 3) {
                errors.add("ForbiddenZone[%d] min/max must be int[3]".formatted(i));
                continue;
            }
            int minY = zone.min()[1];
            int maxY = zone.max()[1];
            if (zone.yMode() == ArenaTemplate.SpawnSlot.YMode.RELATIVE_TO_FLOOR) {
                minY += template.floor().y();
                maxY += template.floor().y();
            }
            boolean outOfBounds = zone.min()[0] < minAllowedX || zone.max()[0] > maxAllowedX
                || zone.min()[2] < minAllowedZ || zone.max()[2] > maxAllowedZ;
            if (outOfBounds) {
                errors.add("ForbiddenZone[%d] out of bounds".formatted(i));
            }
            for (int axis = 0; axis < 3; axis++) {
                if (zone.min()[axis] > zone.max()[axis]) {
                    errors.add("ForbiddenZone[%d] min[%d] > max[%d]".formatted(i, axis, axis));
                }
            }
            if (minY > maxY) {
                errors.add("ForbiddenZone[%d] minY > maxY".formatted(i));
            }
        }
    }

    private void validateLimits(ArenaTemplate template, List<String> errors) {
        if (template.limits() == null) return;
        if (template.limits().maxBlocks() > 150000) {
            errors.add("limits.maxBlocks exceeds hard cap 150000");
        }
        if (template.limits().maxBuildTimeMs() <= 0) {
            errors.add("limits.maxBuildTimeMs must be >0");
        }
    }

    private void validateStructureNbt(ArenaTemplate template, List<String> errors, List<String> warnings) {
        var structure = template.structureNbt();
        if (structure == null) return;

        StructureNbtLoader loader = new StructureNbtLoader();
        if (structure.path() == null || structure.path().isBlank()) {
            errors.add("structureNbt.path is required");
        } else if (!loader.isValidPath(structure.path())) {
            errors.add("structureNbt.path is invalid: " + structure.path());
        }

        if (structure.offset() == null) {
            errors.add("structureNbt.offset is required");
        }

        if (structure.rotation() != null && !ALLOWED_ROTATIONS.contains(structure.rotation())) {
            errors.add("structureNbt.rotation must be one of " + ALLOWED_ROTATIONS);
        }
        if (structure.mirror() != null && !ALLOWED_MIRRORS.contains(structure.mirror())) {
            errors.add("structureNbt.mirror must be one of " + ALLOWED_MIRRORS);
        }
        if (structure.seedPolicy() != null && !ALLOWED_SEED_POLICIES.contains(structure.seedPolicy())) {
            errors.add("structureNbt.seedPolicy must be one of " + ALLOWED_SEED_POLICIES);
        }

        // Deep validation against manifest/data if provided
        if (structureManifest != null && structureDataProvider != null && structure.path() != null) {
            try {
                byte[] data = structureDataProvider.load(structure.path());
                if (data == null) {
                    warnings.add("structureNbt data not found for path: " + structure.path());
                    emitStructureRejected(structure.path(), "DATA_NOT_FOUND");
                    return;
                }
                StructureNbtLoader.LoadResult result = loader.load(structure.path(), () -> data, structureManifest);
                if (!result.ok()) {
                    errors.add("structureNbt validation failed: " + result.errorCode() + " - " + result.message());
                    emitStructureRejected(structure.path(), result.errorCode());
                } else {
                    emitStructureLoaded(structure.path(), result.blockCount(), result.entityCount());
                }
            } catch (Exception e) {
                errors.add("structureNbt validation error: " + e.getMessage());
                emitStructureRejected(structure.path(), "EXCEPTION");
            }
        } else {
            // Fallback path: validation without manifest (dev-friendly)
            if (structureDataProvider == null || structure.path() == null) {
                errors.add("structureNbt present but manifest/provider not configured");
                emitStructureRejected(structure != null ? structure.path() : "unknown", "NO_MANIFEST");
                return;
            }
            if (structureFallbackConfig == null) {
                errors.add("structureNbt fallback config missing");
                emitStructureRejected(structure.path(), "NO_FALLBACK_CONFIG");
                return;
            }
            try {
                byte[] data = structureDataProvider.load(structure.path());
                if (data == null) {
                    warnings.add("structureNbt fallback: data not found for path " + structure.path());
                    emitStructureRejected(structure.path(), "DATA_NOT_FOUND");
                    return;
                }
                StructureNbtLoader.FallbackLimits limits = new StructureNbtLoader.FallbackLimits(
                    structureFallbackConfig.allowedNamespaces(),
                    structureFallbackConfig.maxFileSizeBytes(),
                    structureFallbackConfig.maxBlockCount(),
                    structureFallbackConfig.maxEntityCount()
                );
                var result = loader.loadWithLimits(structure.path(), () -> data, limits);
                if (!result.ok()) {
                    errors.add("structureNbt fallback validation failed: " + result.errorCode() + " - " + result.message());
                    emitStructureRejected(structure.path(), result.errorCode());
                } else {
                    warnings.add("structureNbt validated without manifest for path " + structure.path());
                    emitStructureFallback(structure.path(), "NO_MANIFEST");
                    emitStructureLoaded(structure.path(), result.blockCount(), result.entityCount());
                }
            } catch (Exception e) {
                warnings.add("structureNbt fallback parse failed: " + e.getMessage());
                emitStructureRejected(structure.path(), "EXCEPTION");
            }
        }
    }

    /**
     * Interface to supply structure data bytes by path.
     */
    public interface StructureDataProvider {
        byte[] load(String path) throws Exception;
    }

    /**
     * Fallback limits for structure validation when no manifest is available.
     */
    public record StructureFallbackConfig(
        Set<String> allowedNamespaces,
        long maxFileSizeBytes,
        int maxBlockCount,
        int maxEntityCount
    ) {}

    /**
     * Optional telemetry hook for structure validation outcomes.
     */
    public interface StructureTelemetry {
        void loaded(String path, int blockCount, int entityCount);
        void rejected(String path, String reason);
        default void fallbackUsed(String path, String reason) {}
    }

    private boolean isValidId(String id) {
        return id.length() <= 32 && id.matches("^[a-z0-9_]+$");
    }

    private void emitStructureLoaded(String path, int blockCount, int entityCount) {
        if (structureTelemetry != null) {
            structureTelemetry.loaded(path, blockCount, entityCount);
        }
    }

    private void emitStructureRejected(String path, String reason) {
        if (structureTelemetry != null) {
            structureTelemetry.rejected(path, reason);
            if ("CHECKSUM_MISMATCH".equals(reason)) {
                structureTelemetry.rejected(path, "CHECKSUM_MISMATCH");
            }
        }
    }

    private void emitStructureFallback(String path, String reason) {
        if (structureTelemetry != null) {
            structureTelemetry.fallbackUsed(path, reason);
        }
    }

    private void validateMobSpawnStrategy(ArenaTemplate template, List<String> errors, List<String> warnings, int sizeX, int sizeZ) {
        if (template.mobSpawnStrategy() == null) {
            errors.add("mobSpawnStrategy is required");
            return;
        }
        List<ArenaTemplate.SpawnSlot> slots = template.spawnSlots() != null ? template.spawnSlots() : List.of();
        int centerCount = (int) slots.stream().filter(s -> s.tags() != null && s.tags().contains("center")).count();
        int cornerCount = (int) slots.stream().filter(s -> s.tags() != null && s.tags().contains("corner")).count();
        switch (template.mobSpawnStrategy()) {
            case DISTRIBUTED -> {
                // no-op
            }
            case CLUSTERED -> {
                if (centerCount == 0) {
                    warnings.add("mobSpawnStrategy=CLUSTERED but no spawnSlots tagged 'center'");
                }
            }
            case CORNERS -> {
                if (cornerCount < 4) {
                    warnings.add("mobSpawnStrategy=CORNERS expects >=4 corner spawnSlots; found " + cornerCount);
                }
            }
            case RING -> {
                // expect slots at distance >= size/4 from origin
                int requiredRadius = Math.max(1, Math.max(sizeX, sizeZ) / 4);
                long ringCount = slots.stream().filter(s -> {
                    int[] p = s.pos();
                    if (p == null || p.length != 3) return false;
                    int dx = p[0];
                    int dz = p[2];
                    int dist2 = dx * dx + dz * dz;
                    return dist2 >= requiredRadius * requiredRadius;
                }).count();
                if (ringCount < 2) {
                    warnings.add("mobSpawnStrategy=RING expects spawnSlots at radius >= " + requiredRadius);
                }
            }
            default -> errors.add("Unknown mobSpawnStrategy: " + template.mobSpawnStrategy());
        }
    }

    private int computeMaxDim(ArenaTemplate template) {
        Integer sxVal = template.sizeX();
        Integer szVal = template.sizeZ();
        int sx = sxVal != null ? sxVal : template.size();
        int sz = szVal != null ? szVal : template.size();
        return Math.max(sx, sz);
    }

    /**
     * Computes rough arena bounds using origin mode + floor/ceiling/walls.
     */
    private Bounds computeBounds(ArenaTemplate template) {
        Integer sizeXVal = template.sizeX();
        Integer sizeZVal = template.sizeZ();
        int sizeX = sizeXVal != null ? sizeXVal : template.size();
        int sizeZ = sizeZVal != null ? sizeZVal : template.size();
        int originX = template.origin().x();
        int originZ = template.origin().z();

        int minX;
        int minZ;
        int maxX;
        int maxZ;
        int halfWidth = sizeX / 2;
        int halfDepth = sizeZ / 2;
        switch (template.origin().mode()) {
            case CORNER_NW -> {
                minX = originX;
                minZ = originZ;
                maxX = originX + sizeX - 1;
                maxZ = originZ + sizeZ - 1;
            }
            case CORNER_SW -> {
                minX = originX;
                minZ = originZ - sizeZ + 1;
                maxX = originX + sizeX - 1;
                maxZ = originZ;
            }
            case CENTER -> {
                minX = originX - halfWidth;
                maxX = originX + halfWidth - 1;
                minZ = originZ - halfDepth;
                maxZ = originZ + halfDepth - 1;
            }
            default -> {
                minX = originX - halfWidth;
                maxX = originX + halfWidth - 1;
                minZ = originZ - halfDepth;
                maxZ = originZ + halfDepth - 1;
            }
        }

        int minY = template.floor() != null ? template.floor().y() : 0;
        int maxY = minY;
        if (template.ceiling() != null) {
            maxY = Math.max(maxY, template.ceiling().y());
        }
        if (template.walls() != null) {
            maxY = Math.max(maxY, template.walls().startY() + template.walls().height());
        }

        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ, originX, originZ);
    }
}
