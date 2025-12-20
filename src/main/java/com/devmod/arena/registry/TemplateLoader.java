package com.devmod.arena.registry;

import com.devmod.arena.serialization.TemplateSerializer;
import com.devmod.arena.telemetry.ArenaTelemetry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Loads templates from disk with schema validation and telemetry hooks.
 */
public class TemplateLoader {

    private final TemplateSerializer serializer = new TemplateSerializer();
    private final TemplateValidator validator;
    private final TemplateValidator.ValidationMode validationMode;
    private final ArenaTelemetry telemetry;

    public TemplateLoader(TemplateValidator validator,
                          TemplateValidator.ValidationMode validationMode,
                          ArenaTelemetry telemetry) {
        this.validator = validator;
        this.validationMode = validationMode;
        this.telemetry = telemetry;
        // Attempt to load JSON schema if present (draft schema in docs)
        try {
            Path schemaPath = Path.of("docs/arena-template-rework/arena_template.schema.json");
            SchemaValidator.tryLoadFromPath(schemaPath);
        } catch (Exception ignored) {
            // keep defaults if schema load fails
        }
    }

    /**
     * Loads all template JSON files in a directory.
     */
    public LoadResult loadAll(Path directory) {
        List<ArenaTemplate> loaded = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        if (!Files.isDirectory(directory)) {
            errors.add("Not a directory: " + directory);
            return new LoadResult(loaded, errors);
        }

        try (var stream = Files.list(directory)) {
            stream
                .filter(p -> p.toString().endsWith(".json"))
                .forEach(path -> loadSingle(path, loaded, errors));
        } catch (IOException e) {
            errors.add("Failed to list directory " + directory + ": " + e.getMessage());
        }

        // Post-validate inheritance references (parents must exist)
        if (errors.isEmpty()) {
            validateParentReferences(loaded, errors);
        } else {
            // Still validate parents to give actionable feedback even if other files failed
            validateParentReferences(loaded, errors);
        }

        // Remove any templates that reference missing parents to avoid returning invalid data
        if (!errors.isEmpty()) {
            Set<String> missingParentIds = new HashSet<>();
            for (String error : errors) {
                if (error.contains("extends missing parent")) {
                    int start = error.indexOf("parent '");
                    if (start >= 0) {
                        int end = error.indexOf("'", start + 8);
                        if (end > start) {
                            missingParentIds.add(error.substring(start + 8, end));
                        }
                    }
                }
            }
            if (!missingParentIds.isEmpty()) {
                loaded.removeIf(t -> missingParentIds.contains(t.extendsTemplate()));
            }
        }

        // Inject fallback default if nothing loaded
        if (loaded.isEmpty()) {
            loaded.add(ArenaTemplate.defaultTemplate());
        }

        return new LoadResult(loaded, errors);
    }

    /**
     * Loads templates from all sources with priority: jar resources < datapacks < config directory (provided dir).
     * This is a simplified implementation that looks at:
     * default jar path: data/devmod/arena_templates/
     * datapacks: datapacks/[pack]/data/devmod/arena_templates/
     * config override: directory parameter
     */
    public LoadResult loadAllSources(Path directory) {
        List<ArenaTemplate> loaded = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // 1) Jar resources (lowest priority)
        loadFromPath(Path.of("data/devmod/arena_templates/"), loaded, errors);

        // 2) Datapacks (override)
        Path datapacksRoot = Path.of("datapacks");
        if (Files.isDirectory(datapacksRoot)) {
            try (var packs = Files.list(datapacksRoot)) {
                packs.filter(Files::isDirectory).forEach(dp -> {
                    Path arenaPath = dp.resolve("data/devmod/arena_templates/");
                    loadFromPath(arenaPath, loaded, errors);
                });
            } catch (IOException e) {
                errors.add("Failed to list datapacks: " + e.getMessage());
            }
        }

        // 3) Config override (highest priority)
        loadFromPath(directory, loaded, errors);

        return new LoadResult(loaded, errors);
    }

    private void loadFromPath(Path path, List<ArenaTemplate> loaded, List<String> errors) {
        if (path == null || !Files.isDirectory(path)) return;
        try (var stream = Files.list(path)) {
            stream.filter(p -> p.toString().endsWith(".json"))
                .forEach(p -> loadSingle(p, loaded, errors));
        } catch (IOException e) {
            errors.add("Failed to list directory " + path + ": " + e.getMessage());
        }
    }

    private void validateParentReferences(List<ArenaTemplate> loaded, List<String> errors) {
        Set<String> ids = new HashSet<>();
        for (ArenaTemplate template : loaded) {
            ids.add(template.id());
        }

        for (ArenaTemplate template : loaded) {
            String extendsTemplate = template.extendsTemplate();
            if (extendsTemplate == null || extendsTemplate.isBlank()) {
                continue;
            }
            if (!ids.contains(extendsTemplate)) {
                String msg = "Template '%s' extends missing parent '%s'".formatted(
                    template.id(), extendsTemplate);
                errors.add(msg);
                telemetry.emit("arena.template.parent_not_found", Map.of(
                    "templateId", template.id(),
                    "parentId", extendsTemplate
                ));
            }
        }
    }

    private void loadSingle(Path path, List<ArenaTemplate> loaded, List<String> errors) {
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            TemplateSerializer.ValidationResult validation = serializer.validate(json, validationMode);

            if (!validation.unknownFields().isEmpty()) {
                telemetry.emit("arena.template.unknown_fields", Map.of(
                    "file", path.toString(),
                    "fields", validation.unknownFields()
                ));
            }

            if (!validation.valid()) {
                errors.add(path + ": " + validation.error());
                telemetry.emit("arena.template.load_failed", Map.of(
                    "file", path.toString(),
                    "reason", validation.error()
                ));
                return;
            }

            Optional<ArenaTemplate> parsed = serializer.deserialize(json);
            if (parsed.isEmpty()) {
                errors.add(path + ": failed to deserialize");
                telemetry.emit("arena.template.load_failed", Map.of(
                    "file", path.toString(),
                    "reason", "deserialization_failed"
                ));
                return;
            }

            ArenaTemplate normalized = ensureMutableHazards(parsed.get());

            ValidationResult result = validator.validate(normalized);
            if (!result.valid()) {
                errors.add(path + ": " + result.errors());
                telemetry.emit("arena.template.load_failed", Map.of(
                    "file", path.toString(),
                    "reason", result.errors()
                ));
                return;
            }

            loaded.add(normalized);
            telemetry.emit("arena.template.loaded_file", Map.of(
                "templateId", normalized.id(),
                "file", path.toString()
            ));
        } catch (Exception e) {
            errors.add(path + ": " + e.getMessage());
            telemetry.emit("arena.template.load_failed", Map.of(
                "file", path.toString(),
                "reason", e.getMessage()
            ));
        }
    }

    /**
     * Ensure hazard param maps are mutable so validators can clamp values even if
     * the original JSON used immutable maps (Map.of).
     */
    private ArenaTemplate ensureMutableHazards(ArenaTemplate template) {
        if (template.hazards() == null || template.hazards().isEmpty()) {
            return template;
        }
        List<ArenaTemplate.Hazard> mutableHazards = new ArrayList<>(template.hazards().size());
        for (ArenaTemplate.Hazard hazard : template.hazards()) {
            Map<String, Object> params = hazard.params() == null
                ? new java.util.HashMap<>()
                : new java.util.HashMap<>(hazard.params());
            mutableHazards.add(new ArenaTemplate.Hazard(
                hazard.type(),
                params,
                hazard.y(),
                hazard.yMode()
            ));
        }

        return new ArenaTemplate(
            template.id(),
            template.extendsTemplate(),
            template.version(),
            template.schemaVersion(),
            template.breakingChange(),
            template.deprecated(),
            template.replacementVersion(),
            template.minParentVersion(),
            template.templateType(),
            template.origin(),
            template.size(),
            template.sizeX(),
            template.sizeZ(),
            template.floor(),
            template.walls(),
            template.ceiling(),
            template.underfloor(),
            template.palette(),
            template.biome(),
            template.lighting(),
            template.spawnSlots(),
            template.playerSpawnOffset(),
            template.mobSpawnStrategy(),
            template.forbiddenZones(),
            List.copyOf(mutableHazards),
            template.environment(),
            template.compat(),
            template.instanceSettings(),
            template.structureNbt(),
            template.limits(),
            template.buildSettings(),
            template.tags()
        );
    }

    public record LoadResult(List<ArenaTemplate> templates, List<String> errors) {
        public boolean success() {
            return errors == null || errors.isEmpty();
        }
    }
}
