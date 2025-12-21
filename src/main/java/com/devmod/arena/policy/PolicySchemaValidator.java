package com.devmod.arena.policy;

import com.devmod.arena.registry.TemplateValidator;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

/**
 * Validates policy JSON against allowed fields with STRICT/PERMISSIVE/LENIENT modes.
 *
 * <p>This mirrors SchemaValidator for templates but scoped to ArenaPolicy (L2).
 */
public final class PolicySchemaValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicySchemaValidator.class);

    private static final Set<String> DEFAULT_ALLOWED_FIELDS = Set.of(
        "id",
        "version",
        "templateId",
        "schemaVersion",
        "schemaHash",
        "minTemplateVersion",
        "maxTemplateVersion",
        "priority",
        "weight",
        "enabled",
        "description",
        "minPlayers",
        "maxPlayers",
        "mobTypes",
        "mobIds",
        "questTypes",
        "difficultyTags",
        "questType",
        "difficulty",
        "tags",
        "routing",
        "perkBindings",
        "mutatorBindings",
        "rewardModifiers",
        "balanceOverrides"
    );

    private static final Set<String> DEFAULT_REQUIRED_FIELDS = Set.of(
        "id",
        "version",
        "templateId"
    );

    private static final Map<String, Set<String>> DEFAULT_NESTED_ALLOWED_FIELDS = Map.ofEntries(
        Map.entry("Routing", Set.of("mobIds", "questTypes", "difficultyTags", "weight")),
        Map.entry("PerkBindings", Set.of("suggested", "excluded", "required")),
        Map.entry("MutatorBindings", Set.of("suggested", "excluded", "required")),
        Map.entry("RewardModifiers", Set.of("baseMultiplier", "firstCompletionBonus", "hazardBonus", "streakMultiplier")),
        Map.entry("BalanceOverrides", Set.of("spawnRateMultiplier", "damageMultiplier", "waveScaling"))
    );

    private static volatile Set<String> allowedFields = DEFAULT_ALLOWED_FIELDS;
    private static volatile Set<String> requiredFields = DEFAULT_REQUIRED_FIELDS;
    private static volatile Map<String, Set<String>> nestedAllowedFields = DEFAULT_NESTED_ALLOWED_FIELDS;

    private PolicySchemaValidator() {}

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

        validateDeep(json, mode, errors::add, warnings::add, unknownFields);

        boolean valid = errors.isEmpty();
        return new ValidationResult(valid, errors, warnings, unknownFields);
    }

    public static void tryLoadFromPath(Path schemaPath) {
        if (schemaPath == null) return;
        try {
            if (!Files.isRegularFile(schemaPath)) {
                LOGGER.debug("Policy schema file not found at {}, keeping defaults", schemaPath);
                return;
            }
            JsonObject schema = JsonParser.parseReader(Files.newBufferedReader(schemaPath)).getAsJsonObject();
            applySchema(schema, schemaPath.toString());
        } catch (Exception e) {
            allowedFields = DEFAULT_ALLOWED_FIELDS;
            requiredFields = DEFAULT_REQUIRED_FIELDS;
            nestedAllowedFields = DEFAULT_NESTED_ALLOWED_FIELDS;
            LOGGER.warn("Failed to load policy schema from {}: {}. Reverting to defaults.", schemaPath, e.getMessage());
        }
    }

    public static void tryLoadFromResource(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) return;
        try (InputStream in = PolicySchemaValidator.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                LOGGER.debug("Policy schema resource not found at {}, keeping defaults", resourcePath);
                return;
            }
            JsonObject schema = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            applySchema(schema, "classpath:" + resourcePath);
        } catch (Exception e) {
            allowedFields = DEFAULT_ALLOWED_FIELDS;
            requiredFields = DEFAULT_REQUIRED_FIELDS;
            nestedAllowedFields = DEFAULT_NESTED_ALLOWED_FIELDS;
            LOGGER.warn("Failed to load policy schema resource {}: {}. Reverting to defaults.", resourcePath, e.getMessage());
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

        LOGGER.info("Loaded policy schema from {} (fields={}, required={}, nested={})",
            source, allowedFields.size(), requiredFields.size(), nestedAllowedFields.size());
    }

    private static void validateDeep(JsonObject json,
                                     TemplateValidator.ValidationMode mode,
                                     Consumer<String> errors,
                                     Consumer<String> warnings,
                                     List<String> unknownFields) {
        if (json.has("routing") && json.get("routing").isJsonObject()) {
            JsonObject routing = json.getAsJsonObject("routing");
            checkUnknownFields(routing, allowedForDef("Routing"), "routing", mode, errors, warnings, unknownFields);
            validateStringArray(routing, "mobIds", "routing.mobIds", errors);
            validateStringArray(routing, "questTypes", "routing.questTypes", errors);
            validateStringArray(routing, "difficultyTags", "routing.difficultyTags", errors);
            Double weight = readNumber(routing, "weight", errors, "routing.weight");
            if (weight != null && (weight < 0.1 || weight > 10.0)) {
                warnings.accept("routing.weight out of range [0.1, 10.0]");
            }
        }
        if (json.has("perkBindings") && json.get("perkBindings").isJsonObject()) {
            JsonObject pb = json.getAsJsonObject("perkBindings");
            checkUnknownFields(pb, allowedForDef("PerkBindings"), "perkBindings", mode, errors, warnings, unknownFields);
            validateStringArray(pb, "suggested", "perkBindings.suggested", errors);
            validateStringArray(pb, "excluded", "perkBindings.excluded", errors);
            validateStringArray(pb, "required", "perkBindings.required", errors);
        }
        if (json.has("mutatorBindings") && json.get("mutatorBindings").isJsonObject()) {
            JsonObject mb = json.getAsJsonObject("mutatorBindings");
            checkUnknownFields(mb, allowedForDef("MutatorBindings"), "mutatorBindings", mode, errors, warnings, unknownFields);
            validateStringArray(mb, "suggested", "mutatorBindings.suggested", errors);
            validateStringArray(mb, "excluded", "mutatorBindings.excluded", errors);
            validateStringArray(mb, "required", "mutatorBindings.required", errors);
        }
        if (json.has("rewardModifiers") && json.get("rewardModifiers").isJsonObject()) {
            JsonObject rm = json.getAsJsonObject("rewardModifiers");
            checkUnknownFields(rm, allowedForDef("RewardModifiers"), "rewardModifiers", mode, errors, warnings, unknownFields);
            Double base = readNumber(rm, "baseMultiplier", errors, "rewardModifiers.baseMultiplier");
            if (base != null && base < 0.0) {
                errors.accept("rewardModifiers.baseMultiplier must be >= 0");
            }
            Double first = readNumber(rm, "firstCompletionBonus", errors, "rewardModifiers.firstCompletionBonus");
            if (first != null && first < 0.0) {
                warnings.accept("rewardModifiers.firstCompletionBonus < 0, clamped to 0");
            }
            Double hazard = readNumber(rm, "hazardBonus", errors, "rewardModifiers.hazardBonus");
            if (hazard != null && hazard < 0.0) {
                warnings.accept("rewardModifiers.hazardBonus < 0, clamped to 0");
            }
            Double streak = readNumber(rm, "streakMultiplier", errors, "rewardModifiers.streakMultiplier");
            if (streak != null && streak < 0.0) {
                warnings.accept("rewardModifiers.streakMultiplier < 0, clamped to 0");
            }
        }
        if (json.has("balanceOverrides") && json.get("balanceOverrides").isJsonObject()) {
            JsonObject bo = json.getAsJsonObject("balanceOverrides");
            checkUnknownFields(bo, allowedForDef("BalanceOverrides"), "balanceOverrides", mode, errors, warnings, unknownFields);
            readNumber(bo, "spawnRateMultiplier", errors, "balanceOverrides.spawnRateMultiplier");
            readNumber(bo, "damageMultiplier", errors, "balanceOverrides.damageMultiplier");
            readNumber(bo, "waveScaling", errors, "balanceOverrides.waveScaling");
        }

        Double weight = readNumber(json, "weight", errors, "weight");
        if (weight != null && (weight < 0.1 || weight > 10.0)) {
            warnings.accept("weight out of range [0.1, 10.0]");
        }
        Integer minPlayers = readInt(json, "minPlayers", errors, "minPlayers");
        if (minPlayers != null && minPlayers < 1) {
            errors.accept("minPlayers must be >= 1");
        }
        Integer maxPlayers = readInt(json, "maxPlayers", errors, "maxPlayers");
        if (maxPlayers != null && maxPlayers < 1) {
            errors.accept("maxPlayers must be >= 1");
        }

        // Arrays of strings for tags/mobTypes/questTypes/difficultyTags
        validateStringArray(json, "tags", "tags", errors);
        validateStringArray(json, "mobTypes", "mobTypes", errors);
        validateStringArray(json, "mobIds", "mobIds", errors);
        validateStringArray(json, "questTypes", "questTypes", errors);
        validateStringArray(json, "difficultyTags", "difficultyTags", errors);
    }

    private static void validateStringArray(JsonObject json, String field, String path, Consumer<String> errors) {
        if (!json.has(field)) return;
        if (!json.get(field).isJsonArray()) {
            errors.accept(path + " must be an array of strings");
            return;
        }
        int idx = 0;
        for (JsonElement el : json.getAsJsonArray(field)) {
            if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
                errors.accept(path + " contains non-string at index " + idx);
                return;
            }
            idx++;
        }
    }

    private static Double readNumber(JsonObject obj, String field, Consumer<String> errors, String path) {
        if (!obj.has(field) || obj.get(field).isJsonNull()) return null;
        if (!obj.get(field).isJsonPrimitive() || !obj.get(field).getAsJsonPrimitive().isNumber()) {
            errors.accept(path + " must be a number");
            return null;
        }
        return obj.get(field).getAsDouble();
    }

    private static Integer readInt(JsonObject obj, String field, Consumer<String> errors, String path) {
        if (!obj.has(field) || obj.get(field).isJsonNull()) return null;
        if (!obj.get(field).isJsonPrimitive() || !obj.get(field).getAsJsonPrimitive().isNumber()) {
            errors.accept(path + " must be an integer");
            return null;
        }
        return obj.get(field).getAsInt();
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

    private static Map<String, Set<String>> loadNestedAllowedFieldsFromSchema(JsonObject schema) {
        Map<String, Set<String>> nested = new HashMap<>(DEFAULT_NESTED_ALLOWED_FIELDS);
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
