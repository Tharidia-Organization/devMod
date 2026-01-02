package com.devmod.arena.registry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * P1: Block and entity whitelist validation for arena templates.
 *
 * Provides configurable whitelists for:
 * - Block materials (floor, walls, ceiling, underfloor)
 * - Entity types (spawn slots)
 *
 * Supports:
 * - Exact matches (e.g., "minecraft:stone")
 * - Namespace wildcards (e.g., "minecraft:*")
 * - Pattern matching (e.g., "minecraft:*_bricks")
 */
public final class BlockEntityWhitelist {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlockEntityWhitelist.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Default allowed block namespaces (common safe mods).
     */
    private static final Set<String> DEFAULT_BLOCK_NAMESPACES = Set.of(
        "minecraft", "devmod", "create", "supplementaries", "quark"
    );

    /**
     * Default allowed entity namespaces.
     */
    private static final Set<String> DEFAULT_ENTITY_NAMESPACES = Set.of(
        "minecraft", "devmod"
    );

    /**
     * Blocks that should never be used in arenas (security/stability risk).
     */
    private static final Set<String> DEFAULT_BLOCK_BLACKLIST = Set.of(
        "minecraft:tnt",
        "minecraft:command_block",
        "minecraft:chain_command_block",
        "minecraft:repeating_command_block",
        "minecraft:structure_block",
        "minecraft:structure_void",
        "minecraft:jigsaw",
        "minecraft:barrier",  // Sometimes useful but can hide bugs
        "minecraft:bedrock",  // Can't be broken
        "minecraft:spawner",
        "minecraft:end_portal",
        "minecraft:end_portal_frame",
        "minecraft:nether_portal"
    );

    /**
     * Entities that should never be spawned in arenas.
     */
    private static final Set<String> DEFAULT_ENTITY_BLACKLIST = Set.of(
        "minecraft:ender_dragon",
        "minecraft:wither",
        "minecraft:lightning_bolt",
        "minecraft:area_effect_cloud",
        "minecraft:armor_stand",  // Clutter
        "minecraft:item_frame",
        "minecraft:glow_item_frame",
        "minecraft:painting"
    );

    // Configuration
    private final Set<String> allowedBlockNamespaces;
    private final Set<String> allowedEntityNamespaces;
    private final Set<String> blockBlacklist;
    private final Set<String> entityBlacklist;
    private final Set<String> blockWhitelist;  // Explicit whitelist overrides namespace check
    private final Set<String> entityWhitelist; // Explicit whitelist overrides namespace check
    private final List<Pattern> blockPatterns;
    private final List<Pattern> entityPatterns;
    private final boolean enabled;
    private final Mode mode;

    /**
     * Validation mode.
     */
    public enum Mode {
        /** Whitelist violations are errors */
        STRICT,
        /** Whitelist violations are warnings */
        WARN,
        /** Skip whitelist validation */
        SKIP
    }

    private BlockEntityWhitelist(
            Set<String> allowedBlockNamespaces,
            Set<String> allowedEntityNamespaces,
            Set<String> blockBlacklist,
            Set<String> entityBlacklist,
            Set<String> blockWhitelist,
            Set<String> entityWhitelist,
            List<Pattern> blockPatterns,
            List<Pattern> entityPatterns,
            boolean enabled,
            Mode mode) {
        this.allowedBlockNamespaces = Set.copyOf(allowedBlockNamespaces);
        this.allowedEntityNamespaces = Set.copyOf(allowedEntityNamespaces);
        this.blockBlacklist = Set.copyOf(blockBlacklist);
        this.entityBlacklist = Set.copyOf(entityBlacklist);
        this.blockWhitelist = Set.copyOf(blockWhitelist);
        this.entityWhitelist = Set.copyOf(entityWhitelist);
        this.blockPatterns = List.copyOf(blockPatterns);
        this.entityPatterns = List.copyOf(entityPatterns);
        this.enabled = enabled;
        this.mode = mode;
    }

    /**
     * Create a default whitelist with common safe defaults.
     */
    public static BlockEntityWhitelist defaults() {
        return new BlockEntityWhitelist(
            DEFAULT_BLOCK_NAMESPACES,
            DEFAULT_ENTITY_NAMESPACES,
            DEFAULT_BLOCK_BLACKLIST,
            DEFAULT_ENTITY_BLACKLIST,
            Set.of(),
            Set.of(),
            List.of(),
            List.of(),
            true,
            Mode.WARN
        );
    }

    /**
     * Create an empty whitelist (allows everything).
     */
    public static BlockEntityWhitelist disabled() {
        return new BlockEntityWhitelist(
            Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
            List.of(), List.of(), false, Mode.SKIP
        );
    }

    /**
     * Check if a block material is allowed.
     *
     * @param material Block material ID (e.g., "minecraft:stone")
     * @return Validation result
     */
    public ValidationResult validateBlock(String material) {
        if (!enabled || mode == Mode.SKIP) {
            return ValidationResult.ok();
        }

        if (material == null || material.isBlank()) {
            return ValidationResult.error("Block material is null or empty");
        }

        // Normalize material
        String normalized = normalizeMaterial(material);

        // Check blacklist first (always enforced)
        if (blockBlacklist.contains(normalized)) {
            return ValidationResult.blacklisted(normalized, mode == Mode.STRICT);
        }

        // Check explicit whitelist
        if (!blockWhitelist.isEmpty() && blockWhitelist.contains(normalized)) {
            return ValidationResult.ok();
        }

        // Check patterns
        for (Pattern pattern : blockPatterns) {
            if (pattern.matcher(normalized).matches()) {
                return ValidationResult.ok();
            }
        }

        // Check namespace
        String namespace = extractNamespace(normalized);
        if (allowedBlockNamespaces.contains(namespace)) {
            return ValidationResult.ok();
        }

        return ValidationResult.notAllowed(normalized, mode == Mode.STRICT);
    }

    /**
     * Check if an entity type is allowed.
     *
     * @param entityType Entity type ID (e.g., "minecraft:zombie")
     * @return Validation result
     */
    public ValidationResult validateEntity(String entityType) {
        if (!enabled || mode == Mode.SKIP) {
            return ValidationResult.ok();
        }

        if (entityType == null || entityType.isBlank()) {
            return ValidationResult.error("Entity type is null or empty");
        }

        // Normalize entity type
        String normalized = normalizeEntityType(entityType);

        // Check blacklist first (always enforced)
        if (entityBlacklist.contains(normalized)) {
            return ValidationResult.blacklisted(normalized, mode == Mode.STRICT);
        }

        // Check explicit whitelist
        if (!entityWhitelist.isEmpty() && entityWhitelist.contains(normalized)) {
            return ValidationResult.ok();
        }

        // Check patterns
        for (Pattern pattern : entityPatterns) {
            if (pattern.matcher(normalized).matches()) {
                return ValidationResult.ok();
            }
        }

        // Check namespace
        String namespace = extractNamespace(normalized);
        if (allowedEntityNamespaces.contains(namespace)) {
            return ValidationResult.ok();
        }

        return ValidationResult.notAllowed(normalized, mode == Mode.STRICT);
    }

    /**
     * Validate all block materials in a template.
     */
    public List<ValidationResult> validateTemplateBlocks(ArenaTemplate template) {
        List<ValidationResult> results = new ArrayList<>();

        if (template.floor() != null) {
            results.add(prefixResult("floor.material", validateBlock(template.floor().material())));
            results.add(prefixResult("floor.borderMaterial", validateBlock(template.floor().borderMaterial())));
        }

        if (template.walls() != null) {
            results.add(prefixResult("walls.material", validateBlock(template.walls().material())));
        }

        if (template.ceiling() != null && template.ceiling().enabled()) {
            results.add(prefixResult("ceiling.material", validateBlock(template.ceiling().material())));
        }

        if (template.underfloor() != null && !template.underfloor().sameAsFloor()) {
            results.add(prefixResult("underfloor.material", validateBlock(template.underfloor().material())));
        }

        // Check palette (3 fixed fields: accent, highlight, hazardBorder)
        if (template.palette() != null) {
            results.add(prefixResult("palette.accent", validateBlock(template.palette().accent())));
            results.add(prefixResult("palette.highlight", validateBlock(template.palette().highlight())));
            results.add(prefixResult("palette.hazardBorder", validateBlock(template.palette().hazardBorder())));
        }

        return results.stream().filter(r -> !r.allowed()).toList();
    }

    /**
     * Validate all entity types in spawn slots.
     */
    public List<ValidationResult> validateTemplateEntities(ArenaTemplate template) {
        List<ValidationResult> results = new ArrayList<>();

        if (template.spawnSlots() != null) {
            for (int i = 0; i < template.spawnSlots().size(); i++) {
                ArenaTemplate.SpawnSlot slot = template.spawnSlots().get(i);
                // Spawn slots use tags to identify mob types, not direct entity IDs
                // The actual entity type validation happens at spawn time
                // Here we validate any explicit entity types if specified
                for (String tag : slot.tags()) {
                    // Tags starting with "entity:" are direct entity references
                    if (tag.startsWith("entity:")) {
                        String entityType = tag.substring(7);
                        results.add(prefixResult("spawnSlots[" + i + "].entity", validateEntity(entityType)));
                    }
                }
            }
        }

        return results.stream().filter(r -> !r.allowed()).toList();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Mode getMode() {
        return mode;
    }

    public BlockEntityWhitelist withMode(Mode mode) {
        if (mode == null || mode == this.mode) {
            return this;
        }
        return new BlockEntityWhitelist(
            allowedBlockNamespaces,
            allowedEntityNamespaces,
            blockBlacklist,
            entityBlacklist,
            blockWhitelist,
            entityWhitelist,
            blockPatterns,
            entityPatterns,
            enabled,
            mode
        );
    }

    // ===== Parsing =====

    /**
     * Parse whitelist from JSON file.
     */
    public static BlockEntityWhitelist parse(Path path) throws IOException {
        if (!Files.exists(path)) {
            LOGGER.info("Block/entity whitelist not found at {}, using defaults", path);
            return defaults();
        }

        String json = Files.readString(path);
        return parseJson(json);
    }

    /**
     * Parse whitelist from JSON string.
     */
    public static BlockEntityWhitelist parseJson(String json) {
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);

            boolean enabled = root.has("enabled") ? root.get("enabled").getAsBoolean() : true;

            Mode mode = Mode.WARN;
            if (root.has("mode")) {
                try {
                    mode = Mode.valueOf(root.get("mode").getAsString().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("Invalid mode in whitelist, defaulting to WARN");
                }
            }

            Set<String> blockNamespaces = parseStringSet(root, "allowedBlockNamespaces", DEFAULT_BLOCK_NAMESPACES);
            Set<String> entityNamespaces = parseStringSet(root, "allowedEntityNamespaces", DEFAULT_ENTITY_NAMESPACES);
            Set<String> blockBlacklist = parseStringSet(root, "blockBlacklist", DEFAULT_BLOCK_BLACKLIST);
            Set<String> entityBlacklist = parseStringSet(root, "entityBlacklist", DEFAULT_ENTITY_BLACKLIST);
            Set<String> blockWhitelist = parseStringSet(root, "blockWhitelist", Set.of());
            Set<String> entityWhitelist = parseStringSet(root, "entityWhitelist", Set.of());
            List<Pattern> blockPatterns = parsePatternList(root, "blockPatterns");
            List<Pattern> entityPatterns = parsePatternList(root, "entityPatterns");

            LOGGER.info("Loaded block/entity whitelist: enabled={}, mode={}, blockNamespaces={}, entityNamespaces={}",
                enabled, mode, blockNamespaces.size(), entityNamespaces.size());

            return new BlockEntityWhitelist(
                blockNamespaces, entityNamespaces,
                blockBlacklist, entityBlacklist,
                blockWhitelist, entityWhitelist,
                blockPatterns, entityPatterns,
                enabled, mode
            );

        } catch (Exception e) {
            LOGGER.error("Failed to parse block/entity whitelist: {}", e.getMessage());
            return defaults();
        }
    }

    private static Set<String> parseStringSet(JsonObject root, String key, Set<String> defaults) {
        if (!root.has(key)) return new HashSet<>(defaults);
        JsonArray arr = root.getAsJsonArray(key);
        Set<String> result = new HashSet<>();
        for (JsonElement e : arr) {
            result.add(e.getAsString());
        }
        return result;
    }

    private static List<Pattern> parsePatternList(JsonObject root, String key) {
        if (!root.has(key)) return List.of();
        JsonArray arr = root.getAsJsonArray(key);
        List<Pattern> result = new ArrayList<>();
        for (JsonElement e : arr) {
            try {
                result.add(Pattern.compile(e.getAsString()));
            } catch (Exception ex) {
                LOGGER.warn("Invalid pattern in whitelist {}: {}", key, e.getAsString());
            }
        }
        return result;
    }

    // ===== Utilities =====

    private String normalizeMaterial(String material) {
        if (!material.contains(":")) {
            return "minecraft:" + material;
        }
        return material.toLowerCase(Locale.ROOT);
    }

    private String normalizeEntityType(String entityType) {
        if (!entityType.contains(":")) {
            return "minecraft:" + entityType;
        }
        return entityType.toLowerCase(Locale.ROOT);
    }

    private String extractNamespace(String id) {
        int colonIndex = id.indexOf(':');
        return colonIndex > 0 ? id.substring(0, colonIndex) : "minecraft";
    }

    private ValidationResult prefixResult(String field, ValidationResult result) {
        if (result.allowed()) return result;
        return new ValidationResult(
            false, result.strict(),
            field + ": " + result.message()
        );
    }

    /**
     * Validation result.
     */
    public record ValidationResult(
        boolean allowed,
        boolean strict,
        @Nullable String message
    ) {
        public static ValidationResult ok() {
            return new ValidationResult(true, false, null);
        }

        public static ValidationResult blacklisted(String id, boolean strict) {
            return new ValidationResult(false, strict, "Blacklisted: " + id);
        }

        public static ValidationResult notAllowed(String id, boolean strict) {
            return new ValidationResult(false, strict, "Not in whitelist: " + id);
        }

        public static ValidationResult error(String message) {
            return new ValidationResult(false, true, message);
        }

        public boolean isError() {
            return !allowed && strict;
        }

        public boolean isWarning() {
            return !allowed && !strict;
        }
    }
}
