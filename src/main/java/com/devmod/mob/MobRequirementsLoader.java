package com.devmod.mob;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import com.devmod.mob.MobRequirements.BiomeRequirement;
import com.devmod.mob.MobRequirements.BossRequirement;
import com.devmod.mob.MobRequirements.FloorRequirement;
import com.devmod.mob.MobRequirements.LightRequirement;
import com.devmod.mob.MobRequirements.RequirementSource;
import com.devmod.mob.MobRequirements.SpaceRequirement;
import com.devmod.mob.MobRequirements.TimeRequirement;

/**
 * Loads MobRequirements override definitions from JSON files.
 * JSON files are loaded from the config/devmod/mob_requirements/ directory.
 *
 * JSON Schema:
 * {
 *   "mobId": "minecraft:warden",
 *   "biome": {
 *     "preferred": "minecraft:deep_dark",
 *     "tag": "minecraft:is_overworld",
 *     "validBiomes": ["minecraft:deep_dark", "minecraft:dripstone_caves"],
 *     "required": true
 *   },
 *   "light": {
 *     "min": 0,
 *     "max": 0,
 *     "prefersDark": true,
 *     "prefersLight": false
 *   },
 *   "floor": {
 *     "blocks": ["minecraft:sculk", "minecraft:deepslate"],
 *     "requiresSolid": true,
 *     "canSpawnOnLiquid": false,
 *     "canSpawnInAir": false,
 *     "preferred": "minecraft:sculk"
 *   },
 *   "space": {
 *     "width": 0.9,
 *     "height": 2.9,
 *     "clearanceAbove": 4.0,
 *     "clearanceAround": 2.0
 *   },
 *   "time": "NIGHT",
 *   "boss": {
 *     "isBoss": true,
 *     "minPlayers": 1,
 *     "requiresActivation": false,
 *     "activationItem": null,
 *     "activationStructure": null
 *   }
 * }
 */
public class MobRequirementsLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(MobRequirementsLoader.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Valid top-level field names in mob requirements JSON */
    private static final Set<String> VALID_TOP_LEVEL_FIELDS = Set.of(
        "mobId", "biome", "light", "floor", "space", "time", "boss"
    );

    /** Valid field names for each section */
    private static final Set<String> VALID_BIOME_FIELDS = Set.of(
        "preferred", "tag", "validBiomes", "required"
    );
    private static final Set<String> VALID_LIGHT_FIELDS = Set.of(
        "min", "max", "prefersDark", "prefersLight"
    );
    private static final Set<String> VALID_FLOOR_FIELDS = Set.of(
        "blocks", "requiresSolid", "canSpawnOnLiquid", "canSpawnInAir", "preferred"
    );
    private static final Set<String> VALID_SPACE_FIELDS = Set.of(
        "width", "height", "clearanceAbove", "clearanceAround"
    );
    private static final Set<String> VALID_BOSS_FIELDS = Set.of(
        "isBoss", "minPlayers", "requiresActivation", "activationItem", "activationStructure"
    );

    private final Path configDirectory;

    public MobRequirementsLoader(Path gameDir) {
        this.configDirectory = gameDir.resolve("config").resolve("devmod").resolve("mob_requirements");
    }

    /**
     * Loads all JSON override files from the config directory.
     *
     * @return Map of mob ID to loaded requirements
     */
    public Map<ResourceLocation, MobRequirements> loadAll() {
        Map<ResourceLocation, MobRequirements> overrides = new HashMap<>();

        if (!Files.exists(configDirectory)) {
            LOGGER.info("Mob requirements config directory does not exist: {}", configDirectory);
            ensureDirectoryExists();
            return overrides;
        }

        try (Stream<Path> files = Files.list(configDirectory)) {
            files.filter(p -> p.toString().endsWith(".json"))
                 .forEach(path -> {
                     try {
                         MobRequirements reqs = loadFromFile(path);
                         if (reqs != null) {
                             overrides.put(reqs.mobId(), reqs);
                             LOGGER.info("Loaded mob requirements override for: {}", reqs.mobId());
                         }
                     } catch (Exception e) {
                         LOGGER.error("Failed to load mob requirements from: {}", path, e);
                     }
                 });
        } catch (IOException e) {
            LOGGER.error("Failed to scan mob requirements directory", e);
        }

        return overrides;
    }

    /**
     * Loads a single MobRequirements from a JSON file.
     * Performs schema validation before parsing.
     *
     * @param path The path to the JSON file
     * @return The parsed MobRequirements, or null if validation failed
     * @throws IOException If the file cannot be read
     */
    @Nullable
    public MobRequirements loadFromFile(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            Path fileNamePath = path.getFileName();
            String filename = fileNamePath != null ? fileNamePath.toString() : path.toString();
            if (json == null) {
                LOGGER.error("Skipping invalid JSON file: {} (empty or malformed)", filename);
                return null;
            }

            // Validate JSON structure before parsing
            ValidationResult validation = validateJsonStructure(json, filename);
            if (!validation.valid()) {
                LOGGER.error("Skipping invalid JSON file: {} (errors: {})", filename, validation.errors().size());
                return null;
            }

            return parseRequirements(json);
        }
    }

    /**
     * Loads MobRequirements from a JSON string (useful for embedded resources).
     */
    public MobRequirements loadFromString(String json) {
        JsonObject obj = GSON.fromJson(json, JsonObject.class);
        return parseRequirements(obj);
    }

    private MobRequirements parseRequirements(JsonObject json) {
        if (!json.has("mobId")) {
            throw new JsonParseException("Missing required field 'mobId'");
        }

        ResourceLocation mobId = ResourceLocation.parse(Objects.requireNonNull(json.get("mobId").getAsString()));

        BiomeRequirement biome = json.has("biome")
            ? parseBiomeRequirement(json.getAsJsonObject("biome"))
            : BiomeRequirement.ANY;

        LightRequirement light = json.has("light")
            ? parseLightRequirement(json.getAsJsonObject("light"))
            : LightRequirement.ANY;

        FloorRequirement floor = json.has("floor")
            ? parseFloorRequirement(json.getAsJsonObject("floor"))
            : FloorRequirement.SOLID_GROUND;

        SpaceRequirement space = json.has("space")
            ? parseSpaceRequirement(json.getAsJsonObject("space"))
            : SpaceRequirement.DEFAULT;

        TimeRequirement time = json.has("time")
            ? TimeRequirement.valueOf(json.get("time").getAsString().toUpperCase(java.util.Locale.ROOT))
            : TimeRequirement.ANY;

        BossRequirement boss = json.has("boss")
            ? parseBossRequirement(json.getAsJsonObject("boss"))
            : BossRequirement.NOT_BOSS;

        return new MobRequirements(
            mobId,
            biome,
            light,
            floor,
            space,
            time,
            boss,
            RequirementSource.JSON_OVERRIDE,
            1.0f // JSON overrides have full confidence
        );
    }

    private BiomeRequirement parseBiomeRequirement(JsonObject json) {
        Optional<ResourceLocation> preferred = json.has("preferred")
            ? Optional.of(ResourceLocation.parse(Objects.requireNonNull(json.get("preferred").getAsString())))
            : Optional.empty();

        Optional<TagKey<Biome>> tag = json.has("tag")
            ? Optional.of(TagKey.create(Objects.requireNonNull(Registries.BIOME), Objects.requireNonNull(ResourceLocation.parse(Objects.requireNonNull(json.get("tag").getAsString())))))
            : Optional.empty();

        List<ResourceLocation> validBiomes = new ArrayList<>();
        if (json.has("validBiomes")) {
            for (JsonElement elem : json.getAsJsonArray("validBiomes")) {
                validBiomes.add(ResourceLocation.parse(Objects.requireNonNull(elem.getAsString())));
            }
        }

        boolean required = json.has("required") && json.get("required").getAsBoolean();

        return new BiomeRequirement(preferred, tag, validBiomes, required);
    }

    private LightRequirement parseLightRequirement(JsonObject json) {
        int min = json.has("min") ? json.get("min").getAsInt() : 0;
        int max = json.has("max") ? json.get("max").getAsInt() : 15;
        boolean prefersDark = json.has("prefersDark") && json.get("prefersDark").getAsBoolean();
        boolean prefersLight = json.has("prefersLight") && json.get("prefersLight").getAsBoolean();

        return new LightRequirement(min, max, prefersDark, prefersLight);
    }

    private FloorRequirement parseFloorRequirement(JsonObject json) {
        List<ResourceLocation> blocks = new ArrayList<>();
        if (json.has("blocks")) {
            for (JsonElement elem : json.getAsJsonArray("blocks")) {
                blocks.add(ResourceLocation.parse(Objects.requireNonNull(elem.getAsString())));
            }
        }

        boolean requiresSolid = !json.has("requiresSolid") || json.get("requiresSolid").getAsBoolean();
        boolean canSpawnOnLiquid = json.has("canSpawnOnLiquid") && json.get("canSpawnOnLiquid").getAsBoolean();
        boolean canSpawnInAir = json.has("canSpawnInAir") && json.get("canSpawnInAir").getAsBoolean();

        Optional<ResourceLocation> preferred = json.has("preferred")
            ? Optional.of(ResourceLocation.parse(Objects.requireNonNull(json.get("preferred").getAsString())))
            : Optional.empty();

        return new FloorRequirement(blocks, requiresSolid, canSpawnOnLiquid, canSpawnInAir, preferred);
    }

    private SpaceRequirement parseSpaceRequirement(JsonObject json) {
        float width = json.has("width") ? json.get("width").getAsFloat() : 1.0f;
        float height = json.has("height") ? json.get("height").getAsFloat() : 2.0f;
        float clearanceAbove = json.has("clearanceAbove") ? json.get("clearanceAbove").getAsFloat() : 1.0f;
        float clearanceAround = json.has("clearanceAround") ? json.get("clearanceAround").getAsFloat() : 1.0f;

        return new SpaceRequirement(width, height, clearanceAbove, clearanceAround);
    }

    private BossRequirement parseBossRequirement(JsonObject json) {
        boolean isBoss = json.has("isBoss") && json.get("isBoss").getAsBoolean();
        int minPlayers = json.has("minPlayers") ? json.get("minPlayers").getAsInt() : 1;
        boolean requiresActivation = json.has("requiresActivation") && json.get("requiresActivation").getAsBoolean();

        Optional<ResourceLocation> activationItem = json.has("activationItem") && !json.get("activationItem").isJsonNull()
            ? Optional.of(ResourceLocation.parse(Objects.requireNonNull(json.get("activationItem").getAsString())))
            : Optional.empty();

        Optional<String> activationStructure = json.has("activationStructure") && !json.get("activationStructure").isJsonNull()
            ? Optional.of(json.get("activationStructure").getAsString())
            : Optional.empty();

        return new BossRequirement(isBoss, minPlayers, requiresActivation, activationItem, activationStructure);
    }

    /**
     * Saves a MobRequirements to a JSON file.
     * Useful for generating example files or exporting detected requirements.
     */
    public void saveToFile(MobRequirements reqs, Path path) throws IOException {
        JsonObject json = serializeRequirements(reqs);
        String jsonString = GSON.toJson(json);
        Files.writeString(path, jsonString);
    }

    /**
     * Generates an example JSON file for a mob.
     */
    public void generateExample(ResourceLocation mobId) throws IOException {
        ensureDirectoryExists();

        MobRequirements example = MobRequirements.defaultFor(mobId);
        Path examplePath = configDirectory.resolve(mobId.getNamespace() + "_" + mobId.getPath() + ".json");

        if (!Files.exists(examplePath)) {
            saveToFile(example, examplePath);
            LOGGER.info("Generated example mob requirements file: {}", examplePath);
        }
    }

    private JsonObject serializeRequirements(MobRequirements reqs) {
        JsonObject json = new JsonObject();
        json.addProperty("mobId", reqs.mobId().toString());

        // Biome
        JsonObject biome = new JsonObject();
        reqs.biome().preferredBiome().ifPresent(b -> biome.addProperty("preferred", b.toString()));
        reqs.biome().biomeTag().ifPresent(t -> biome.addProperty("tag", t.location().toString()));
        if (!reqs.biome().validBiomes().isEmpty()) {
            JsonArray validBiomes = new JsonArray();
            reqs.biome().validBiomes().forEach(b -> validBiomes.add(b.toString()));
            biome.add("validBiomes", validBiomes);
        }
        biome.addProperty("required", reqs.biome().required());
        json.add("biome", biome);

        // Light
        JsonObject light = new JsonObject();
        light.addProperty("min", reqs.light().minLight());
        light.addProperty("max", reqs.light().maxLight());
        light.addProperty("prefersDark", reqs.light().prefersDark());
        light.addProperty("prefersLight", reqs.light().prefersLight());
        json.add("light", light);

        // Floor
        JsonObject floor = new JsonObject();
        if (!reqs.floor().validBlocks().isEmpty()) {
            JsonArray blocks = new JsonArray();
            reqs.floor().validBlocks().forEach(b -> blocks.add(b.toString()));
            floor.add("blocks", blocks);
        }
        floor.addProperty("requiresSolid", reqs.floor().requiresSolid());
        floor.addProperty("canSpawnOnLiquid", reqs.floor().canSpawnOnLiquid());
        floor.addProperty("canSpawnInAir", reqs.floor().canSpawnInAir());
        reqs.floor().preferredBlock().ifPresent(b -> floor.addProperty("preferred", b.toString()));
        json.add("floor", floor);

        // Space
        JsonObject space = new JsonObject();
        space.addProperty("width", reqs.space().width());
        space.addProperty("height", reqs.space().height());
        space.addProperty("clearanceAbove", reqs.space().clearanceAbove());
        space.addProperty("clearanceAround", reqs.space().clearanceAround());
        json.add("space", space);

        // Time
        json.addProperty("time", reqs.time().name());

        // Boss
        JsonObject boss = new JsonObject();
        boss.addProperty("isBoss", reqs.boss().isBoss());
        boss.addProperty("minPlayers", reqs.boss().minPlayers());
        boss.addProperty("requiresActivation", reqs.boss().requiresActivation());
        reqs.boss().activationItem().ifPresent(i -> boss.addProperty("activationItem", i.toString()));
        reqs.boss().activationStructure().ifPresent(s -> boss.addProperty("activationStructure", s));
        json.add("boss", boss);

        return json;
    }

    private void ensureDirectoryExists() {
        try {
            Files.createDirectories(configDirectory);
        } catch (IOException e) {
            LOGGER.error("Failed to create mob requirements config directory", e);
        }
    }

    /**
     * Returns the config directory path.
     */
    public Path getConfigDirectory() {
        return configDirectory;
    }

    // ============== JSON Schema Validation ==============

    /**
     * Result of JSON schema validation.
     */
    public record ValidationResult(
        boolean valid,
        List<String> errors,
        List<String> warnings
    ) {
        public static ValidationResult success() {
            return new ValidationResult(true, List.of(), List.of());
        }

        public static ValidationResult withWarnings(List<String> warnings) {
            return new ValidationResult(true, List.of(), warnings);
        }

        public static ValidationResult failure(List<String> errors, List<String> warnings) {
            return new ValidationResult(false, errors, warnings);
        }
    }

    /**
     * Validates the JSON structure before parsing.
     * Checks for required fields, unknown fields (typos), and value constraints.
     *
     * @param json The JSON object to validate
     * @param filename The filename for error reporting
     * @return ValidationResult with errors and warnings
     */
    public ValidationResult validateJsonStructure(JsonObject json, String filename) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Check required field
        if (!json.has("mobId")) {
            errors.add("Missing required field 'mobId'");
        } else {
            // Validate mobId format
            String mobId = json.get("mobId").getAsString();
            if (!mobId.contains(":")) {
                warnings.add("mobId '" + mobId + "' missing namespace, will default to 'minecraft:'");
            }
        }

        // Check for unknown top-level fields (potential typos)
        for (String key : json.keySet()) {
            if (!VALID_TOP_LEVEL_FIELDS.contains(key)) {
                warnings.add("Unknown field '" + key + "' (typo? valid fields: " + VALID_TOP_LEVEL_FIELDS + ")");
            }
        }

        // Validate nested objects
        if (json.has("biome") && json.get("biome").isJsonObject()) {
            validateSectionFields(json.getAsJsonObject("biome"), "biome", VALID_BIOME_FIELDS, warnings);
        }

        if (json.has("light") && json.get("light").isJsonObject()) {
            validateSectionFields(json.getAsJsonObject("light"), "light", VALID_LIGHT_FIELDS, warnings);
            validateLightValues(json.getAsJsonObject("light"), errors);
        }

        if (json.has("floor") && json.get("floor").isJsonObject()) {
            validateSectionFields(json.getAsJsonObject("floor"), "floor", VALID_FLOOR_FIELDS, warnings);
        }

        if (json.has("space") && json.get("space").isJsonObject()) {
            validateSectionFields(json.getAsJsonObject("space"), "space", VALID_SPACE_FIELDS, warnings);
            validateSpaceValues(json.getAsJsonObject("space"), errors);
        }

        if (json.has("boss") && json.get("boss").isJsonObject()) {
            validateSectionFields(json.getAsJsonObject("boss"), "boss", VALID_BOSS_FIELDS, warnings);
            validateBossValues(json.getAsJsonObject("boss"), errors);
        }

        // Validate time enum value
        if (json.has("time")) {
            String timeValue = json.get("time").getAsString().toUpperCase(java.util.Locale.ROOT);
            try {
                TimeRequirement.valueOf(timeValue);
            } catch (IllegalArgumentException e) {
                errors.add("Invalid time value '" + timeValue + "'. Valid values: " +
                    Arrays.toString(TimeRequirement.values()));
            }
        }

        // Log validation results
        if (!warnings.isEmpty()) {
            LOGGER.warn("[{}] JSON validation warnings:", filename);
            warnings.forEach(w -> LOGGER.warn("  - {}", w));
        }
        if (!errors.isEmpty()) {
            LOGGER.error("[{}] JSON validation errors:", filename);
            errors.forEach(e -> LOGGER.error("  - {}", e));
        }

        return errors.isEmpty()
            ? (warnings.isEmpty() ? ValidationResult.success() : ValidationResult.withWarnings(warnings))
            : ValidationResult.failure(errors, warnings);
    }

    private void validateSectionFields(JsonObject section, String sectionName, Set<String> validFields, List<String> warnings) {
        for (String key : section.keySet()) {
            if (!validFields.contains(key)) {
                warnings.add("Unknown field '" + sectionName + "." + key + "' (typo? valid fields: " + validFields + ")");
            }
        }
    }

    private void validateLightValues(JsonObject light, List<String> errors) {
        if (light.has("min")) {
            int min = light.get("min").getAsInt();
            if (min < 0) {
                errors.add("light.min cannot be negative (got: " + min + ")");
            }
            if (min > 15) {
                errors.add("light.min cannot exceed 15 (got: " + min + ")");
            }
        }
        if (light.has("max")) {
            int max = light.get("max").getAsInt();
            if (max < 0) {
                errors.add("light.max cannot be negative (got: " + max + ")");
            }
            if (max > 15) {
                errors.add("light.max cannot exceed 15 (got: " + max + ")");
            }
        }
        if (light.has("min") && light.has("max")) {
            int min = light.get("min").getAsInt();
            int max = light.get("max").getAsInt();
            if (min > max) {
                errors.add("light.min (" + min + ") cannot be greater than light.max (" + max + ")");
            }
        }
    }

    private void validateSpaceValues(JsonObject space, List<String> errors) {
        if (space.has("width")) {
            float width = space.get("width").getAsFloat();
            if (width <= 0) {
                errors.add("space.width must be positive (got: " + width + ")");
            }
        }
        if (space.has("height")) {
            float height = space.get("height").getAsFloat();
            if (height <= 0) {
                errors.add("space.height must be positive (got: " + height + ")");
            }
        }
    }

    private void validateBossValues(JsonObject boss, List<String> errors) {
        if (boss.has("minPlayers")) {
            int minPlayers = boss.get("minPlayers").getAsInt();
            if (minPlayers < 1) {
                errors.add("boss.minPlayers must be at least 1 (got: " + minPlayers + ")");
            }
        }
    }
}
