package com.devmod.arena.serialization;

import com.devmod.arena.registry.ArenaTemplate;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
     * @return validation result
     */
    public ValidationResult validate(String json) {
        try {
            ArenaTemplate template = gson.fromJson(json, ArenaTemplate.class);
            if (template == null) {
                return new ValidationResult(false, "Null template");
            }
            if (template.id() == null || template.id().isBlank()) {
                return new ValidationResult(false, "Missing template ID");
            }
            if (template.schemaVersion() == null) {
                return new ValidationResult(false, "Missing schema version");
            }
            return new ValidationResult(true, null);
        } catch (JsonSyntaxException e) {
            return new ValidationResult(false, e.getMessage());
        }
    }

    /**
     * Result of template validation.
     */
    public record ValidationResult(boolean valid, String error) {}
}
