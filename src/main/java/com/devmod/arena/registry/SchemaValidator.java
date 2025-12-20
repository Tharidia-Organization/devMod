package com.devmod.arena.registry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Validates template JSON against allowed top-level fields with configurable severity.
 *
 * <p>Implements STRICT/PERMISSIVE/LENIENT behavior:
 * <ul>
 *   <li>STRICT: unknown fields -> ERROR</li>
 *   <li>PERMISSIVE: unknown fields -> WARN</li>
 *   <li>LENIENT: unknown fields ignored</li>
 * </ul>
 */
public final class SchemaValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(SchemaValidator.class);

    private static final Set<String> DEFAULT_ALLOWED_FIELDS = Set.of(
        "id",
        "extendsTemplate",
        "version",
        "schemaVersion",
        "breakingChange",
        "deprecated",
        "replacementVersion",
        "minParentVersion",
        "templateType",
        "origin",
        "size",
        "sizeX",
        "sizeZ",
        "floor",
        "walls",
        "ceiling",
        "underfloor",
        "palette",
        "biome",
        "lighting",
        "playerSpawnOffset",
        "mobSpawnStrategy",
        "spawnSlots",
        "forbiddenZones",
        "hazards",
        "environment",
        "compat",
        "instanceSettings",
        "structureNbt",
        "limits",
        "buildSettings",
        "tags"
    );

    private static final Set<String> DEFAULT_REQUIRED_FIELDS = Set.of("id", "version", "schemaVersion", "size", "origin");

    private static volatile Set<String> allowedFields = DEFAULT_ALLOWED_FIELDS;
    private static volatile Set<String> requiredFields = DEFAULT_REQUIRED_FIELDS;

    private SchemaValidator() {}

    public static ValidationResult validate(JsonObject json, TemplateValidator.ValidationMode mode) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> unknownFields = new ArrayList<>();
        List<String> missingFields = new ArrayList<>();
        List<String> deepErrors = new ArrayList<>();
        List<String> deepWarnings = new ArrayList<>();

        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String key = entry.getKey();
            if (shouldIgnore(key)) {
                continue;
            }
            if (!allowedFields.contains(key)) {
                unknownFields.add(key);
            }
        }

        for (String required : requiredFields) {
            if (!json.has(required) || json.get(required).isJsonNull()) {
                missingFields.add(required);
            }
        }

        if (!unknownFields.isEmpty()) {
            String msg = "Unknown fields: " + unknownFields;
            switch (mode) {
                case STRICT -> errors.add(msg);
                case PERMISSIVE -> warnings.add(msg);
                case LENIENT -> { /* ignore */ }
            }
        }

        if (!missingFields.isEmpty()) {
            String msg = "Missing required fields: " + missingFields;
            switch (mode) {
                case STRICT -> errors.add(msg);
                case PERMISSIVE, LENIENT -> warnings.add(msg);
            }
        }

        // Deep validation (type/range) best-effort
        validateDeep(json, deepErrors::add, deepWarnings::add);

        errors.addAll(deepErrors);
        warnings.addAll(deepWarnings);

        boolean valid = errors.isEmpty();
        return new ValidationResult(valid, errors, warnings, unknownFields);
    }

    /**
     * Attempt to load allowed/required fields from a JSON schema file (draft format).
     * Falls back to defaults on failure.
     */
    public static void tryLoadFromPath(Path schemaPath) {
        if (schemaPath == null) return;
        try {
            if (!Files.isRegularFile(schemaPath)) {
                LOGGER.debug("Schema file not found at {}, keeping defaults", schemaPath);
                return;
            }
            JsonObject schema = JsonParser.parseReader(Files.newBufferedReader(schemaPath)).getAsJsonObject();
            JsonObject props = schema.has("properties") ? schema.getAsJsonObject("properties") : null;
            Set<String> newAllowed = new HashSet<>();
            if (props != null) {
                newAllowed.addAll(props.keySet());
            }
            Set<String> newRequired = new HashSet<>();
            if (schema.has("required") && schema.get("required").isJsonArray()) {
                schema.getAsJsonArray("required").forEach(el -> newRequired.add(el.getAsString()));
            }
            if (!newAllowed.isEmpty()) {
                allowedFields = Set.copyOf(newAllowed);
            }
            if (!newRequired.isEmpty()) {
                requiredFields = Set.copyOf(newRequired);
            }
            LOGGER.info("Loaded template schema from {} (fields={}, required={})",
                schemaPath, allowedFields.size(), requiredFields.size());
        } catch (Exception e) {
            allowedFields = DEFAULT_ALLOWED_FIELDS;
            requiredFields = DEFAULT_REQUIRED_FIELDS;
            LOGGER.warn("Failed to load schema from {}: {}. Reverting to defaults.", schemaPath, e.getMessage());
        }
    }

    private static boolean shouldIgnore(String key) {
        return key.startsWith("$") || key.startsWith("_") || key.startsWith("//");
    }

    public record ValidationResult(
        boolean valid,
        List<String> errors,
        List<String> warnings,
        List<String> unknownFields
    ) {}

    /**
     * Best-effort deep validation for common fields (type/range).
     */
    private static void validateDeep(JsonObject json, Consumer<String> errors, Consumer<String> warnings) {
        int size = getInt(json, "size", -1);
        if (size != -1 && (size < 8 || size > 256)) {
            errors.accept("size must be 8-256");
        }
        int sizeX = getInt(json, "sizeX", -1);
        if (sizeX != -1 && (sizeX < 8 || sizeX > 256)) {
            errors.accept("sizeX must be 8-256");
        }
        int sizeZ = getInt(json, "sizeZ", -1);
        if (sizeZ != -1 && (sizeZ < 8 || sizeZ > 256)) {
            errors.accept("sizeZ must be 8-256");
        }

        if (json.has("origin") && json.get("origin").isJsonObject()) {
            JsonObject origin = json.getAsJsonObject("origin");
            String mode = getString(origin, "mode", null);
            if (mode != null && !List.of("CENTER", "CORNER_NW", "CORNER_SW").contains(mode)) {
                errors.accept("origin.mode must be CENTER|CORNER_NW|CORNER_SW");
            }
        }

        if (json.has("floor") && json.get("floor").isJsonObject()) {
            JsonObject floor = json.getAsJsonObject("floor");
            if (getInt(floor, "thickness", 1) <= 0) errors.accept("floor.thickness must be >0");
            if (getInt(floor, "borderWidth", 0) < 0) errors.accept("floor.borderWidth must be >=0");
            String pattern = getString(floor, "pattern", null);
            if (pattern != null && !List.of("solid", "checkerboard", "border").contains(pattern)) {
                errors.accept("floor.pattern invalid");
            }
        }

        if (json.has("walls") && json.get("walls").isJsonObject()) {
            JsonObject walls = json.getAsJsonObject("walls");
            if (getInt(walls, "height", 1) <= 0) errors.accept("walls.height must be >0");
            if (getInt(walls, "thickness", 1) <= 0) errors.accept("walls.thickness must be >0");
        }

        if (json.has("ceiling") && json.get("ceiling").isJsonObject()) {
            JsonObject ceiling = json.getAsJsonObject("ceiling");
            boolean enabled = getBoolean(ceiling, "enabled", false);
            if (enabled && getInt(ceiling, "thickness", 1) <= 0) {
                errors.accept("ceiling.thickness must be >0 when enabled");
            }
        }

        if (json.has("underfloor") && json.get("underfloor").isJsonObject()) {
            JsonObject uf = json.getAsJsonObject("underfloor");
            if (getInt(uf, "depth", 0) < 0) errors.accept("underfloor.depth must be >=0");
        }

        if (json.has("lighting") && json.get("lighting").isJsonObject()) {
            JsonObject light = json.getAsJsonObject("lighting");
            int sky = getInt(light, "skyLight", 0);
            int block = getInt(light, "blockLight", 0);
            if (sky < 0 || sky > 15) errors.accept("lighting.skyLight must be 0-15");
            if (block < 0 || block > 15) errors.accept("lighting.blockLight must be 0-15");
        }

        if (json.has("instanceSettings") && json.get("instanceSettings").isJsonObject()) {
            JsonObject is = json.getAsJsonObject("instanceSettings");
            int chunkRadius = getInt(is, "chunkRadius", 0);
            int tickDistance = getInt(is, "tickDistance", 0);
            if (chunkRadius < 0) errors.accept("instanceSettings.chunkRadius must be >=0");
            if (tickDistance < 0) errors.accept("instanceSettings.tickDistance must be >=0");
        }

        if (json.has("compat") && json.get("compat").isJsonObject()) {
            JsonObject compat = json.getAsJsonObject("compat");
            int min = getInt(compat, "minPlayers", 0);
            int max = getInt(compat, "maxPlayers", 0);
            if (min < 0) errors.accept("compat.minPlayers must be >=0");
            if (max < 0) errors.accept("compat.maxPlayers must be >=0");
            if (min > 0 && max > 0 && min > max) {
                warnings.accept("compat.minPlayers > maxPlayers");
            }
        }

        if (json.has("limits") && json.get("limits").isJsonObject()) {
            JsonObject limits = json.getAsJsonObject("limits");
            if (getInt(limits, "maxBuildTimeMs", 1) <= 0) errors.accept("limits.maxBuildTimeMs must be >0");
            if (getInt(limits, "maxBlocks", 1) <= 0) errors.accept("limits.maxBlocks must be >0");
            if (getInt(limits, "maxEntities", 0) < 0) errors.accept("limits.maxEntities must be >=0");
        }

        if (json.has("palette") && json.get("palette").isJsonObject()) {
            JsonObject palette = json.getAsJsonObject("palette");
            if (isBlank(palette, "accent")) errors.accept("palette.accent is required");
            if (isBlank(palette, "highlight")) errors.accept("palette.highlight is required");
            if (isBlank(palette, "hazardBorder")) errors.accept("palette.hazardBorder is required");
        }

        if (json.has("environment") && json.get("environment").isJsonObject()) {
            JsonObject env = json.getAsJsonObject("environment");
            if (env.has("particles") && env.get("particles").isJsonArray()) {
                var arr = env.getAsJsonArray("particles");
                for (int i = 0; i < arr.size(); i++) {
                    if (!arr.get(i).isJsonObject()) continue;
                    JsonObject p = arr.get(i).getAsJsonObject();
                    if (isBlank(p, "type")) errors.accept("environment.particles[" + i + "].type is required");
                    if (getDouble(p, "rate", 0.0) < 0) errors.accept("environment.particles[" + i + "].rate must be >=0");
                    String area = getString(p, "area", null);
                    if (area != null && !List.of("bounds", "chunks").contains(area)) {
                        errors.accept("environment.particles[" + i + "].area must be bounds|chunks");
                    }
                }
            }
            if (env.has("fog") && env.get("fog").isJsonObject()) {
                JsonObject fog = env.getAsJsonObject("fog");
                double density = getDouble(fog, "density", 0.0);
                if (density < 0.0 || density > 1.0) {
                    errors.accept("environment.fog.density must be 0.0-1.0");
                }
            }
            if (env.has("ambientSound") && isBlank(env, "ambientSound")) {
                errors.accept("environment.ambientSound is blank");
            }
        }

        if (json.has("buildSettings") && json.get("buildSettings").isJsonObject()) {
            JsonObject bs = json.getAsJsonObject("buildSettings");
            String priority = getString(bs, "buildPriority", null);
            if (priority != null && !List.of("sync", "async").contains(priority.toLowerCase())) {
                errors.accept("buildSettings.buildPriority must be sync|async");
            }
            String order = getString(bs, "buildOrder", null);
            if (order != null && !List.of("floor_first", "walls_first", "structure_first").contains(order.toLowerCase())) {
                errors.accept("buildSettings.buildOrder must be floor_first|walls_first|structure_first");
            }
        }
    }

    private static int getInt(JsonObject obj, String key, int def) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return def;
        try {
            return obj.get(key).getAsInt();
        } catch (Exception e) {
            return def;
        }
    }

    private static String getString(JsonObject obj, String key, String def) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return def;
        try {
            return obj.get(key).getAsString();
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean getBoolean(JsonObject obj, String key, boolean def) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return def;
        try {
            return obj.get(key).getAsBoolean();
        } catch (Exception e) {
            return def;
        }
    }

    private static double getDouble(JsonObject obj, String key, double def) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return def;
        try {
            return obj.get(key).getAsDouble();
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean isBlank(JsonObject obj, String key) {
        String val = getString(obj, key, null);
        return val == null || val.isBlank();
    }
}
