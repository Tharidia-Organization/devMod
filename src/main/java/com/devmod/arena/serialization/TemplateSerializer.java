package com.devmod.arena.serialization;

import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.registry.SchemaValidator;
import com.devmod.arena.registry.TemplateType;
import com.devmod.arena.registry.TemplateValidator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Template Serializer (DD11).
 *
 * <p>Provides JSON serialization/deserialization for ArenaTemplate records.
 *
 * <p>Features:
 * <ul>
 *   <li>Pretty-printed JSON output</li>
 *   <li>Validation on deserialize</li>
 *   <li>Batch load from directory</li>
 *   <li>Schema version checking</li>
 * </ul>
 */
public class TemplateSerializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TemplateSerializer.class);

    /**
     * DD11: Current schema version for serialized templates.
     */
    public static final String SCHEMA_VERSION = "1.0.0";

    /**
     * DD11: File extension for template files.
     */
    public static final String FILE_EXTENSION = ".json";

    private final Gson gson;

    public TemplateSerializer() {
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .disableHtmlEscaping()
            .registerTypeAdapter(TemplateType.class, new TemplateTypeAdapter())
            .create();
    }

    /**
     * Serializes a template to JSON string.
     * DD11: Template serialization.
     *
     * @param template the template to serialize
     * @return JSON string representation
     */
    public String serialize(ArenaTemplate template) {
        return gson.toJson(template);
    }

    /**
     * Deserializes a template from JSON string.
     * DD11: Template deserialization with validation.
     *
     * @param json the JSON string
     * @return the deserialized template, or empty if invalid
     */
    public Optional<ArenaTemplate> deserialize(String json) {
        try {
            ArenaTemplate template = gson.fromJson(json, ArenaTemplate.class);
            if (template == null) {
                LOGGER.warn("DD11: Failed to parse template JSON - null result");
                return Optional.empty();
            }
            if (template.id() == null || template.id().isBlank()) {
                LOGGER.warn("DD11: Template missing required 'id' field");
                return Optional.empty();
            }
            return Optional.of(template);
        } catch (JsonSyntaxException e) {
            LOGGER.error("DD11: Failed to deserialize template: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Saves a template to a file.
     * DD11: Template persistence.
     *
     * @param template the template to save
     * @param path the file path
     * @throws IOException if writing fails
     */
    public void saveToFile(ArenaTemplate template, Path path) throws IOException {
        String json = serialize(template);
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write(json);
        }
        LOGGER.info("DD11: Saved template '{}' to {}", template.id(), path);
    }

    /**
     * Loads a template from a file.
     * DD11: Template loading with validation.
     *
     * @param path the file path
     * @return the loaded template, or empty if invalid
     * @throws IOException if reading fails
     */
    public Optional<ArenaTemplate> loadFromFile(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }
            return deserialize(sb.toString());
        }
    }

    /**
     * Loads all templates from a directory.
     * DD11: Batch template loading.
     *
     * @param directory the directory containing template files
     * @return list of successfully loaded templates
     * @throws IOException if reading directory fails
     */
    public List<ArenaTemplate> loadFromDirectory(Path directory) throws IOException {
        List<ArenaTemplate> templates = new ArrayList<>();

        if (!Files.isDirectory(directory)) {
            LOGGER.warn("DD11: Not a directory: {}", directory);
            return templates;
        }

        try (var stream = Files.list(directory)) {
            stream.filter(p -> p.toString().endsWith(FILE_EXTENSION))
                .forEach(path -> {
                    try {
                        loadFromFile(path).ifPresent(templates::add);
                    } catch (IOException e) {
                        LOGGER.error("DD11: Failed to load template from {}: {}", path, e.getMessage());
                    }
                });
        }

        LOGGER.info("DD11: Loaded {} templates from {}", templates.size(), directory);
        return templates;
    }

    /**
     * Validates a template JSON without fully deserializing.
     * DD11: Pre-validation.
     *
     * @param json the JSON string
     * @param mode validation mode for unknown fields
     * @return validation result
     */
    public ValidationResult validate(String json, TemplateValidator.ValidationMode mode) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            SchemaValidator.ValidationResult schemaResult = SchemaValidator.validate(obj, mode);
            ArenaTemplate template = gson.fromJson(json, ArenaTemplate.class);
            if (template == null) {
                return new ValidationResult(false, "Null template");
            }
            if (template.id() == null || template.id().isBlank()) {
                return new ValidationResult(false, "Missing template ID");
            }
            if (template.schemaVersion() <= 0) {
                return new ValidationResult(false, "Missing or invalid schema version");
            }
            if (!schemaResult.valid()) {
                return new ValidationResult(false, String.join(";", schemaResult.errors()));
            }
            return new ValidationResult(true, null, schemaResult.warnings(), schemaResult.unknownFields());
        } catch (JsonSyntaxException e) {
            return new ValidationResult(false, e.getMessage());
        }
    }

    /**
     * Overload with STRICT mode by default (legacy callers).
     */
    public ValidationResult validate(String json) {
        return validate(json, TemplateValidator.ValidationMode.STRICT);
    }

    /**
     * Result of template validation.
     */
    public record ValidationResult(boolean valid, String error, List<String> warnings, List<String> unknownFields) {
        public ValidationResult(boolean valid, String error) {
            this(valid, error, List.of(), List.of());
        }
    }

    /**
     * Custom (de)serializer for TemplateType sealed interface to avoid JsonIOException
     * when Gson attempts to instantiate an interface. The deserializer picks the
     * concrete variant based on known fields; fallback is a flat template with
     * reasonable defaults so validation can proceed.
     */
    static final class TemplateTypeAdapter implements JsonSerializer<TemplateType>, JsonDeserializer<TemplateType> {

        @Override
        public JsonElement serialize(TemplateType src, Type typeOfSrc, JsonSerializationContext context) {
            if (src == null) {
                return JsonNull.INSTANCE;
            }
            return context.serialize(src, src.getClass());
        }

        @Override
        public TemplateType deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json == null || json.isJsonNull()) {
                return TemplateType.FlatTemplate.defaults();
            }
            JsonObject obj = json.getAsJsonObject();

            // Structure template
            if (obj.has("structurePath")) {
                String path = getString(obj, "structurePath", "devmod:structures/default");
                int offsetX = getInt(obj, "offsetX", 0);
                int offsetY = getInt(obj, "offsetY", 0);
                int offsetZ = getInt(obj, "offsetZ", 0);
                TemplateType.StructureTemplate.Rotation rotation = parseRotation(getString(obj, "rotation", "NONE"));
                TemplateType.StructureTemplate.Mirror mirror = parseMirror(getString(obj, "mirror", "NONE"));
                boolean ignoreAir = getBool(obj, "ignoreAir", false);
                Long seedOverride = obj.has("seedOverride") && !obj.get("seedOverride").isJsonNull()
                    ? obj.get("seedOverride").getAsLong()
                    : null;
                return new TemplateType.StructureTemplate(path, offsetX, offsetY, offsetZ, rotation, mirror, ignoreAir, seedOverride);
            }

            // Schematic template
            if (obj.has("schematicPath")) {
                String path = getString(obj, "schematicPath", "devmod:schematics/default.schem");
                TemplateType.SchematicTemplate.SchematicFormat format =
                    TemplateType.SchematicTemplate.SchematicFormat.fromPath(path);
                int offsetX = getInt(obj, "offsetX", 0);
                int offsetY = getInt(obj, "offsetY", 0);
                int offsetZ = getInt(obj, "offsetZ", 0);
                boolean replaceExisting = getBool(obj, "replaceExisting", true);
                return new TemplateType.SchematicTemplate(path, format, offsetX, offsetY, offsetZ, replaceExisting);
            }

            // Composite template
            if (obj.has("layers")) {
                List<TemplateType.CompositeTemplate.Layer> layers = new ArrayList<>();
                JsonArray arr = obj.getAsJsonArray("layers");
                if (arr != null) {
                    for (int i = 0; i < arr.size(); i++) {
                        JsonObject layerObj = arr.get(i).getAsJsonObject();
                        String name = getString(layerObj, "name", "layer_" + i);
                        TemplateType nested = context.deserialize(layerObj.get("template"), TemplateType.class);
                        int priority = getInt(layerObj, "priority", 0);
                        int[] offset = null;
                        if (layerObj.has("offset") && layerObj.get("offset").isJsonArray()) {
                            JsonArray off = layerObj.getAsJsonArray("offset");
                            if (off.size() == 3) {
                                offset = new int[]{off.get(0).getAsInt(), off.get(1).getAsInt(), off.get(2).getAsInt()};
                            }
                        }
                        layers.add(new TemplateType.CompositeTemplate.Layer(name, nested, priority, offset));
                    }
                }
                TemplateType.CompositeTemplate.MergeStrategy mergeStrategy = TemplateType.CompositeTemplate.MergeStrategy.OVERWRITE;
                if (obj.has("mergeStrategy")) {
                    try {
                        mergeStrategy = TemplateType.CompositeTemplate.MergeStrategy.valueOf(getString(obj, "mergeStrategy", "OVERWRITE"));
                    } catch (IllegalArgumentException ignored) {
                        // keep default
                    }
                }
                if (layers.isEmpty()) {
                    layers.add(new TemplateType.CompositeTemplate.Layer("base", TemplateType.FlatTemplate.defaults(), 0, null));
                }
                return new TemplateType.CompositeTemplate(layers, mergeStrategy);
            }

            // Default to flat template
            int sizeX = getInt(obj, "sizeX", 64);
            int sizeZ = getInt(obj, "sizeZ", 64);
            int floorY = getInt(obj, "floorY", 64);
            int wallHeight = getInt(obj, "wallHeight", 10);
            String floorMaterial = getString(obj, "floorMaterial", "minecraft:stone_bricks");
            String wallMaterial = getString(obj, "wallMaterial", "minecraft:barrier");
            String ceilingMaterial = obj.has("ceilingMaterial") && !obj.get("ceilingMaterial").isJsonNull()
                ? obj.get("ceilingMaterial").getAsString()
                : null;
            boolean generateUnderfloor = getBool(obj, "generateUnderfloor", true);
            return new TemplateType.FlatTemplate(sizeX, sizeZ, floorY, wallHeight, floorMaterial, wallMaterial, ceilingMaterial, generateUnderfloor);
        }

        private static boolean getBool(JsonObject obj, String key, boolean defaultVal) {
            return obj.has(key) ? obj.get(key).getAsBoolean() : defaultVal;
        }

        private static int getInt(JsonObject obj, String key, int defaultVal) {
            return obj.has(key) ? obj.get(key).getAsInt() : defaultVal;
        }

        private static String getString(JsonObject obj, String key, String defaultVal) {
            return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : defaultVal;
        }

        private static TemplateType.StructureTemplate.Rotation parseRotation(String value) {
            try {
                return TemplateType.StructureTemplate.Rotation.valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                return TemplateType.StructureTemplate.Rotation.NONE;
            }
        }

        private static TemplateType.StructureTemplate.Mirror parseMirror(String value) {
            try {
                return TemplateType.StructureTemplate.Mirror.valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                return TemplateType.StructureTemplate.Mirror.NONE;
            }
        }
    }
}
