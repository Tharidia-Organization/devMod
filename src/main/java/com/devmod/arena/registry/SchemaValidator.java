package com.devmod.arena.registry;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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

    private static final Set<String> DEFAULT_REQUIRED_FIELDS = Set.of(
        "id",
        "version",
        "schemaVersion",
        "origin",
        "size",
        "floor",
        "mobSpawnStrategy"
    );

    private static final Map<String, Set<String>> EXTRA_ALLOWED_FIELDS = Map.ofEntries(
        Map.entry("lighting.lightSources", Set.of("pos", "block")),
        Map.entry("spawnSlots.validation", Set.of("requireSolidBelow", "requireAirAbove", "requireClearRadius")),
        Map.entry("environment.particles", Set.of("type", "rate", "area")),
        Map.entry("environment.fog", Set.of("enabled", "density")),
        Map.entry("structureNbt.offset", Set.of("x", "y", "z"))
    );

    private static volatile Set<String> allowedFields = DEFAULT_ALLOWED_FIELDS;
    private static volatile Set<String> requiredFields = DEFAULT_REQUIRED_FIELDS;
    private static volatile Map<String, Set<String>> nestedAllowedFields = defaultNestedAllowedFields();

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

        // Deep validation (type/range + nested unknown fields) best-effort
        validateDeep(json, mode, deepErrors::add, deepWarnings::add, unknownFields);

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
            applySchema(schema, schemaPath.toString());
        } catch (Exception e) {
            allowedFields = DEFAULT_ALLOWED_FIELDS;
            requiredFields = DEFAULT_REQUIRED_FIELDS;
            nestedAllowedFields = defaultNestedAllowedFields();
            LOGGER.warn("Failed to load schema from {}: {}. Reverting to defaults.", schemaPath, e.getMessage());
        }
    }

    /**
     * Attempt to load schema from classpath resource (e.g., schemas/arena_template.schema.json).
     */
    public static void tryLoadFromResource(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) return;
        try (InputStream in = SchemaValidator.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                LOGGER.debug("Schema resource not found at {}, keeping defaults", resourcePath);
                return;
            }
            JsonObject schema = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            applySchema(schema, "classpath:" + resourcePath);
        } catch (Exception e) {
            allowedFields = DEFAULT_ALLOWED_FIELDS;
            requiredFields = DEFAULT_REQUIRED_FIELDS;
            nestedAllowedFields = defaultNestedAllowedFields();
            LOGGER.warn("Failed to load schema resource {}: {}. Reverting to defaults.", resourcePath, e.getMessage());
        }
    }

    private static void applySchema(JsonObject schema, String source) {
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
        Map<String, Set<String>> nested = loadNestedAllowedFieldsFromSchema(schema);
        if (!nested.isEmpty()) {
            nestedAllowedFields = Map.copyOf(nested);
        }
        LOGGER.info("Loaded template schema from {} (fields={}, required={}, nested={})",
            source, allowedFields.size(), requiredFields.size(), nestedAllowedFields.size());
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
    private static void validateDeep(JsonObject json,
                                     TemplateValidator.ValidationMode mode,
                                     Consumer<String> errors,
                                     Consumer<String> warnings,
                                     List<String> unknownFields) {
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

        if (json.has("mobSpawnStrategy")) {
            String strategy = getString(json, "mobSpawnStrategy", null);
            if (strategy != null && !List.of("DISTRIBUTED", "CLUSTERED", "CORNERS", "RING").contains(strategy)) {
                errors.accept("mobSpawnStrategy must be DISTRIBUTED|CLUSTERED|CORNERS|RING");
            }
        }

        if (json.has("origin") && json.get("origin").isJsonObject()) {
            JsonObject origin = json.getAsJsonObject("origin");
            checkUnknownFields(origin, allowedForDef("Origin"), "origin", mode, errors, warnings, unknownFields);
            String modeValue = getString(origin, "mode", null);
            if (modeValue != null && !List.of("CENTER", "CORNER_NW", "CORNER_SW").contains(modeValue)) {
                errors.accept("origin.mode must be CENTER|CORNER_NW|CORNER_SW");
            }
        }

        if (json.has("templateType") && json.get("templateType").isJsonObject()) {
            JsonObject templateType = json.getAsJsonObject("templateType");
            checkUnknownFields(templateType, allowedForDef("TemplateType"), "templateType", mode, errors, warnings, unknownFields);
            String type = getString(templateType, "type", null);
            if (type != null && !List.of("flat", "structure", "schematic", "composite").contains(type)) {
                errors.accept("templateType.type must be flat|structure|schematic|composite");
            }
        }

        if (json.has("floor") && json.get("floor").isJsonObject()) {
            JsonObject floor = json.getAsJsonObject("floor");
            checkUnknownFields(floor, allowedForDef("Floor"), "floor", mode, errors, warnings, unknownFields);
            if (getInt(floor, "thickness", 1) <= 0) errors.accept("floor.thickness must be >0");
            if (getInt(floor, "borderWidth", 0) < 0) errors.accept("floor.borderWidth must be >=0");
            String pattern = getString(floor, "pattern", null);
            if (pattern != null && !List.of("solid", "checkerboard", "border").contains(pattern)) {
                errors.accept("floor.pattern invalid");
            }
        }

        if (json.has("walls") && json.get("walls").isJsonObject()) {
            JsonObject walls = json.getAsJsonObject("walls");
            checkUnknownFields(walls, allowedForDef("Walls"), "walls", mode, errors, warnings, unknownFields);
            if (getInt(walls, "height", 1) <= 0) errors.accept("walls.height must be >0");
            if (getInt(walls, "thickness", 1) <= 0) errors.accept("walls.thickness must be >0");
            String style = getString(walls, "style", null);
            if (style != null && !List.of("solid", "fence", "glass", "barrier").contains(style)) {
                errors.accept("walls.style must be solid|fence|glass|barrier");
            }
        }

        if (json.has("ceiling") && json.get("ceiling").isJsonObject()) {
            JsonObject ceiling = json.getAsJsonObject("ceiling");
            checkUnknownFields(ceiling, allowedForDef("Ceiling"), "ceiling", mode, errors, warnings, unknownFields);
            boolean enabled = getBoolean(ceiling, "enabled", false);
            if (enabled && getInt(ceiling, "thickness", 1) <= 0) {
                errors.accept("ceiling.thickness must be >0 when enabled");
            }
        }

        if (json.has("underfloor") && json.get("underfloor").isJsonObject()) {
            JsonObject uf = json.getAsJsonObject("underfloor");
            checkUnknownFields(uf, allowedForDef("Underfloor"), "underfloor", mode, errors, warnings, unknownFields);
            if (getInt(uf, "depth", 0) < 1) errors.accept("underfloor.depth must be >=1");
        }

        if (json.has("lighting") && json.get("lighting").isJsonObject()) {
            JsonObject light = json.getAsJsonObject("lighting");
            checkUnknownFields(light, allowedForDef("Lighting"), "lighting", mode, errors, warnings, unknownFields);
            int sky = getInt(light, "skyLight", 0);
            int block = getInt(light, "blockLight", 0);
            if (sky < 0 || sky > 15) errors.accept("lighting.skyLight must be 0-15");
            if (block < 0 || block > 15) errors.accept("lighting.blockLight must be 0-15");
            if (light.has("lightSources") && light.get("lightSources").isJsonArray()) {
                var arr = light.getAsJsonArray("lightSources");
                for (int i = 0; i < arr.size(); i++) {
                    if (!arr.get(i).isJsonObject()) continue;
                    checkUnknownFields(
                        arr.get(i).getAsJsonObject(),
                        EXTRA_ALLOWED_FIELDS.get("lighting.lightSources"),
                        "lighting.lightSources[" + i + "]",
                        mode,
                        errors,
                        warnings,
                        unknownFields
                    );
                }
            }
        }

        if (json.has("instanceSettings") && json.get("instanceSettings").isJsonObject()) {
            JsonObject is = json.getAsJsonObject("instanceSettings");
            checkUnknownFields(is, allowedForDef("InstanceSettings"), "instanceSettings", mode, errors, warnings, unknownFields);
            int chunkRadius = getInt(is, "chunkRadius", 0);
            int tickDistance = getInt(is, "tickDistance", 0);
            if (chunkRadius < 1) errors.accept("instanceSettings.chunkRadius must be >=1");
            if (tickDistance < 1) errors.accept("instanceSettings.tickDistance must be >=1");
        }

        if (json.has("compat") && json.get("compat").isJsonObject()) {
            JsonObject compat = json.getAsJsonObject("compat");
            checkUnknownFields(compat, allowedForDef("Compat"), "compat", mode, errors, warnings, unknownFields);
            int min = getInt(compat, "minPlayers", 0);
            int max = getInt(compat, "maxPlayers", 0);
            if (min < 1) errors.accept("compat.minPlayers must be >=1");
            if (max < 1) errors.accept("compat.maxPlayers must be >=1");
            if (min > 0 && max > 0 && min > max) {
                warnings.accept("compat.minPlayers > maxPlayers");
            }
        }

        if (json.has("limits") && json.get("limits").isJsonObject()) {
            JsonObject limits = json.getAsJsonObject("limits");
            checkUnknownFields(limits, allowedForDef("Limits"), "limits", mode, errors, warnings, unknownFields);
            if (getInt(limits, "maxBuildTimeMs", 1) <= 0) errors.accept("limits.maxBuildTimeMs must be >0");
            if (getInt(limits, "maxBlocks", 1) <= 0) errors.accept("limits.maxBlocks must be >0");
            if (getInt(limits, "maxEntities", 0) < 0) errors.accept("limits.maxEntities must be >=0");
        }

        if (json.has("palette") && json.get("palette").isJsonObject()) {
            JsonObject palette = json.getAsJsonObject("palette");
            checkUnknownFields(palette, allowedForDef("Palette"), "palette", mode, errors, warnings, unknownFields);
            if (isBlank(palette, "accent")) errors.accept("palette.accent is required");
            if (isBlank(palette, "highlight")) errors.accept("palette.highlight is required");
            if (isBlank(palette, "hazardBorder")) errors.accept("palette.hazardBorder is required");
        }

        if (json.has("biome") && json.get("biome").isJsonObject()) {
            JsonObject biome = json.getAsJsonObject("biome");
            checkUnknownFields(biome, allowedForDef("Biome"), "biome", mode, errors, warnings, unknownFields);
            String applyTo = getString(biome, "applyTo", null);
            if (applyTo != null && !List.of("BOUNDS", "CHUNKS").contains(applyTo)) {
                errors.accept("biome.applyTo must be BOUNDS|CHUNKS");
            }
        }

        if (json.has("environment") && json.get("environment").isJsonObject()) {
            JsonObject env = json.getAsJsonObject("environment");
            checkUnknownFields(env, allowedForDef("Environment"), "environment", mode, errors, warnings, unknownFields);
            if (env.has("particles") && env.get("particles").isJsonArray()) {
                var arr = env.getAsJsonArray("particles");
                for (int i = 0; i < arr.size(); i++) {
                    if (!arr.get(i).isJsonObject()) continue;
                    JsonObject p = arr.get(i).getAsJsonObject();
                    checkUnknownFields(p, EXTRA_ALLOWED_FIELDS.get("environment.particles"),
                        "environment.particles[" + i + "]", mode, errors, warnings, unknownFields);
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
                checkUnknownFields(fog, EXTRA_ALLOWED_FIELDS.get("environment.fog"),
                    "environment.fog", mode, errors, warnings, unknownFields);
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
            checkUnknownFields(bs, allowedForDef("BuildSettings"), "buildSettings", mode, errors, warnings, unknownFields);
            String priority = getString(bs, "buildPriority", null);
            if (priority != null && !List.of("sync", "async").contains(priority.toLowerCase())) {
                errors.accept("buildSettings.buildPriority must be sync|async");
            }
            String order = getString(bs, "buildOrder", null);
            if (order != null && !List.of("floor_first", "walls_first", "structure_first").contains(order.toLowerCase())) {
                errors.accept("buildSettings.buildOrder must be floor_first|walls_first|structure_first");
            }
        }

        if (json.has("structureNbt") && json.get("structureNbt").isJsonObject()) {
            JsonObject structure = json.getAsJsonObject("structureNbt");
            checkUnknownFields(structure, allowedForDef("StructureNbt"), "structureNbt", mode, errors, warnings, unknownFields);
            if (structure.has("offset") && structure.get("offset").isJsonObject()) {
                checkUnknownFields(structure.get("offset").getAsJsonObject(),
                    EXTRA_ALLOWED_FIELDS.get("structureNbt.offset"),
                    "structureNbt.offset",
                    mode,
                    errors,
                    warnings,
                    unknownFields);
            }
            String rotation = getString(structure, "rotation", null);
            if (rotation != null && !List.of("none", "clockwise_90", "180", "counterclockwise_90").contains(rotation)) {
                errors.accept("structureNbt.rotation must be none|clockwise_90|180|counterclockwise_90");
            }
            String mirror = getString(structure, "mirror", null);
            if (mirror != null && !List.of("none", "front_back", "left_right").contains(mirror)) {
                errors.accept("structureNbt.mirror must be none|front_back|left_right");
            }
            String seedPolicy = getString(structure, "seedPolicy", null);
            if (seedPolicy != null && !List.of("fixed", "perRun").contains(seedPolicy)) {
                errors.accept("structureNbt.seedPolicy must be fixed|perRun");
            }
        }

        if (json.has("playerSpawnOffset") && json.get("playerSpawnOffset").isJsonObject()) {
            checkUnknownFields(json.get("playerSpawnOffset").getAsJsonObject(),
                allowedForDef("Offset"),
                "playerSpawnOffset",
                mode,
                errors,
                warnings,
                unknownFields);
        }

        if (json.has("spawnSlots") && json.get("spawnSlots").isJsonArray()) {
            var slots = json.getAsJsonArray("spawnSlots");
            for (int i = 0; i < slots.size(); i++) {
                if (!slots.get(i).isJsonObject()) continue;
                JsonObject slot = slots.get(i).getAsJsonObject();
                checkUnknownFields(slot, allowedForDef("SpawnSlot"), "spawnSlots[" + i + "]", mode, errors, warnings, unknownFields);
                String yMode = getString(slot, "yMode", null);
                if (yMode != null && !List.of("RELATIVE_TO_FLOOR", "ABSOLUTE").contains(yMode)) {
                    errors.accept("spawnSlots[" + i + "].yMode must be RELATIVE_TO_FLOOR|ABSOLUTE");
                }
                if (slot.has("validation") && slot.get("validation").isJsonObject()) {
                    checkUnknownFields(slot.get("validation").getAsJsonObject(),
                        EXTRA_ALLOWED_FIELDS.get("spawnSlots.validation"),
                        "spawnSlots[" + i + "].validation",
                        mode,
                        errors,
                        warnings,
                        unknownFields);
                }
            }
        }

        if (json.has("forbiddenZones") && json.get("forbiddenZones").isJsonArray()) {
            var zones = json.getAsJsonArray("forbiddenZones");
            for (int i = 0; i < zones.size(); i++) {
                if (!zones.get(i).isJsonObject()) continue;
                JsonObject zone = zones.get(i).getAsJsonObject();
                checkUnknownFields(zone,
                    allowedForDef("ForbiddenZone"),
                    "forbiddenZones[" + i + "]",
                    mode,
                    errors,
                    warnings,
                    unknownFields);
                String yMode = getString(zone, "yMode", null);
                if (yMode != null && !List.of("RELATIVE_TO_FLOOR", "ABSOLUTE").contains(yMode)) {
                    errors.accept("forbiddenZones[" + i + "].yMode must be RELATIVE_TO_FLOOR|ABSOLUTE");
                }
            }
        }

        if (json.has("hazards") && json.get("hazards").isJsonArray()) {
            var hazards = json.getAsJsonArray("hazards");
            for (int i = 0; i < hazards.size(); i++) {
                if (!hazards.get(i).isJsonObject()) continue;
                JsonObject hazard = hazards.get(i).getAsJsonObject();
                checkUnknownFields(hazard,
                    allowedForDef("Hazard"),
                    "hazards[" + i + "]",
                    mode,
                    errors,
                    warnings,
                    unknownFields);
                String yMode = getString(hazard, "yMode", null);
                if (yMode != null && !List.of("RELATIVE_TO_FLOOR", "ABSOLUTE").contains(yMode)) {
                    errors.accept("hazards[" + i + "].yMode must be RELATIVE_TO_FLOOR|ABSOLUTE");
                }
            }
        }
    }

    private static Set<String> allowedForDef(String defName) {
        if (defName == null) return null;
        return nestedAllowedFields.get(defName);
    }

    private static void checkUnknownFields(JsonObject obj,
                                           Set<String> allowed,
                                           String path,
                                           TemplateValidator.ValidationMode mode,
                                           Consumer<String> errors,
                                           Consumer<String> warnings,
                                           List<String> unknownFields) {
        if (obj == null || allowed == null) return;
        List<String> unknown = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            String key = entry.getKey();
            if (shouldIgnore(key)) {
                continue;
            }
            if (!allowed.contains(key)) {
                unknown.add(key);
                unknownFields.add(path + "." + key);
            }
        }
        if (unknown.isEmpty()) return;
        String msg = "Unknown fields in " + path + ": " + unknown;
        switch (mode) {
            case STRICT -> errors.accept(msg);
            case PERMISSIVE -> warnings.accept(msg);
            case LENIENT -> { /* ignore */ }
        }
    }

    private static Map<String, Set<String>> defaultNestedAllowedFields() {
        Map<String, Set<String>> map = new HashMap<>();
        map.put("Origin", Set.of("mode", "x", "y", "z"));
        map.put("TemplateType", Set.of(
            "type",
            "sizeX",
            "sizeZ",
            "floorY",
            "wallHeight",
            "floorMaterial",
            "wallMaterial",
            "ceilingMaterial",
            "generateUnderfloor",
            "structurePath",
            "schematicPath",
            "offsetX",
            "offsetY",
            "offsetZ",
            "rotation",
            "mirror",
            "ignoreAir",
            "seedOverride",
            "replaceExisting",
            "layers",
            "mergeStrategy"
        ));
        map.put("Offset", Set.of("x", "y", "z"));
        map.put("Floor", Set.of("y", "thickness", "material", "pattern", "borderMaterial", "borderWidth"));
        map.put("Walls", Set.of("enabled", "material", "height", "thickness", "startY", "style"));
        map.put("Ceiling", Set.of("enabled", "material", "y", "thickness"));
        map.put("Underfloor", Set.of("material", "depth", "sameAsFloor"));
        map.put("Palette", Set.of("accent", "highlight", "hazardBorder"));
        map.put("Biome", Set.of("id", "applyTo"));
        map.put("Lighting", Set.of("skyLight", "blockLight", "ambientLight", "lightSources"));
        map.put("SpawnSlot", Set.of("pos", "yMode", "tags", "validation"));
        map.put("ForbiddenZone", Set.of("min", "max", "yMode", "reason"));
        map.put("Hazard", Set.of("type", "params", "y", "yMode"));
        map.put("Environment", Set.of("particles", "ambientSound", "fog"));
        map.put("Compat", Set.of("minPlayers", "maxPlayers"));
        map.put("InstanceSettings", Set.of("chunkRadius", "tickDistance", "keepLoaded"));
        map.put("StructureNbt", Set.of("path", "offset", "rotation", "mirror", "seedPolicy", "ignoreAir"));
        map.put("Limits", Set.of("maxBuildTimeMs", "maxBlocks", "maxEntities"));
        map.put("BuildSettings", Set.of("buildPriority", "buildOrder"));
        return Map.copyOf(map);
    }

    private static Map<String, Set<String>> loadNestedAllowedFieldsFromSchema(JsonObject schema) {
        Map<String, Set<String>> nested = new HashMap<>(defaultNestedAllowedFields());
        if (schema == null) {
            return nested;
        }
        if (schema.has("$defs") && schema.get("$defs").isJsonObject()) {
            JsonObject defs = schema.getAsJsonObject("$defs");
            for (Map.Entry<String, JsonElement> entry : defs.entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject def = entry.getValue().getAsJsonObject();
                JsonObject props = def.has("properties") ? def.getAsJsonObject("properties") : null;
                if (props == null) continue;
                Set<String> keys = new HashSet<>(props.keySet());
                if (!keys.isEmpty()) {
                    nested.put(entry.getKey(), Set.copyOf(keys));
                }
            }
        }
        return nested;
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
