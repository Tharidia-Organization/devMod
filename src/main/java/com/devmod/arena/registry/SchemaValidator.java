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
}
