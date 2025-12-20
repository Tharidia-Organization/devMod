package com.devmod.arena.registry;

import java.util.ArrayList;
import java.util.List;

/**
 * Template validator orchestrating hazard + spawn validation and basic schema checks.
 */
public class TemplateValidator {

    private static final int MAX_SIZE = 256;
    private static final int MIN_SIZE = 8;
    private static final int MAX_SPAWN_SLOTS = 100;
    private static final int MAX_FORBIDDEN_ZONES = 20;
    private static final int MAX_LIGHT_SOURCES = 50;
    private static final List<String> ALLOWED_ROTATIONS = List.of("none", "clockwise_90", "180", "counterclockwise_90");
    private static final List<String> ALLOWED_MIRRORS = List.of("none", "front_back", "left_right");
    private static final List<String> ALLOWED_SEED_POLICIES = List.of("fixed", "perRun");

    private ValidationMode mode = ValidationMode.STRICT;
    private InstanceSettingsValidator.InstanceLimits instanceLimits = InstanceSettingsValidator.InstanceLimits.defaults();
    private StructureManifest structureManifest;
    private StructureDataProvider structureDataProvider;

    public enum ValidationMode {
        STRICT,
        PERMISSIVE,
        LENIENT
    }

    public TemplateValidator() {}

    public TemplateValidator(ValidationMode mode) {
        this.mode = mode;
    }

    public TemplateValidator withInstanceLimits(InstanceSettingsValidator.InstanceLimits limits) {
        this.instanceLimits = limits;
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

    public void setMode(ValidationMode mode) {
        this.mode = mode;
    }

    public ValidationResult validate(ArenaTemplate template) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        validateRequiredFields(template, errors);

        int maxDim = computeMaxDim(template);
        int sizeX = template.sizeX() != null ? template.sizeX() : template.size();
        int sizeZ = template.sizeZ() != null ? template.sizeZ() : template.size();
        validateSizeBounds(maxDim, errors);
        validateLighting(template, errors);
        validateForbiddenZones(template.forbiddenZones(), sizeX, sizeZ, errors);
        validateStructureNbt(template, errors, warnings);

        // Build arena bounds for spawn/hazard checks
        Bounds bounds = computeBounds(template);

        // Hazards
        HazardValidator hazardValidator = new HazardValidator();
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
        InstanceSettingsValidator isValidator = new InstanceSettingsValidator();
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

    private void validateRequiredFields(ArenaTemplate template, List<String> errors) {
        if (template.id() == null || template.id().isBlank()) {
            errors.add("Template ID is required");
        } else if (!isValidId(template.id())) {
            errors.add("Template ID must be lowercase alphanumeric with underscores, max 32 chars");
        }
        if (template.schemaVersion() == null || template.schemaVersion().isBlank()) {
            errors.add("schemaVersion is required");
        }
        if (template.version() < 1) {
            errors.add("version must be >=1");
        }
        if (template.floor() == null) {
            errors.add("floor is required");
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
    }

    private void validateForbiddenZones(List<ArenaTemplate.ForbiddenZone> zones, int sizeX, int sizeZ, List<String> errors) {
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
            if (zone.min()[0] < minAllowedX || zone.max()[0] > maxAllowedX
                || zone.min()[2] < minAllowedZ || zone.max()[2] > maxAllowedZ) {
                errors.add("ForbiddenZone[%d] out of bounds".formatted(i));
            }
            for (int axis = 0; axis < 3; axis++) {
                if (zone.min()[axis] > zone.max()[axis]) {
                    errors.add("ForbiddenZone[%d] min[%d] > max[%d]".formatted(i, axis, axis));
                }
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

    private void validateStructureNbt(ArenaTemplate template, List<String> errors) {
        validateStructureNbt(template, errors, new ArrayList<>());
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
                    return;
                }
                StructureNbtLoader.LoadResult result = loader.load(structure.path(), () -> data, structureManifest);
                if (!result.ok()) {
                    errors.add("structureNbt validation failed: " + result.errorCode() + " - " + result.message());
                }
            } catch (Exception e) {
                errors.add("structureNbt validation error: " + e.getMessage());
            }
        } else {
            warnings.add("structureNbt present but manifest/provider not configured - deep validation skipped");
        }
    }

    /**
     * Interface to supply structure data bytes by path.
     */
    public interface StructureDataProvider {
        byte[] load(String path) throws Exception;
    }

    private boolean isValidId(String id) {
        return id.length() <= 32 && id.matches("^[a-z0-9_]+$");
    }

    private int computeMaxDim(ArenaTemplate template) {
        int sx = template.sizeX() != null ? template.sizeX() : template.size();
        int sz = template.sizeZ() != null ? template.sizeZ() : template.size();
        return Math.max(sx, sz);
    }

    /**
     * Computes rough arena bounds using origin mode + floor/ceiling/walls.
     */
    private Bounds computeBounds(ArenaTemplate template) {
        int sizeX = template.sizeX() != null ? template.sizeX() : template.size();
        int sizeZ = template.sizeZ() != null ? template.sizeZ() : template.size();
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
