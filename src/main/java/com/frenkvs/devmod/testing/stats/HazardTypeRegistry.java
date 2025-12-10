package com.frenkvs.devmod.testing.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Config-driven registry for environmental hazard types.
 * Replaces hardcoded string matching with configurable patterns.
 *
 * <p>Features:
 * <ul>
 *   <li>Fail-fast validation on malformed configs</li>
 *   <li>Extensible hazard type definitions</li>
 *   <li>Priority-based matching (first match wins)</li>
 *   <li>Default config generation on first run</li>
 * </ul>
 *
 * <p>Config file location: config/devmod/hazard_types.json
 */
public class HazardTypeRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(HazardTypeRegistry.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final HazardTypeRegistry INSTANCE = new HazardTypeRegistry();

    // === Hazard Type Enum ===
    public enum HazardType {
        FALL,
        FIRE,
        LAVA,
        DROWNING,
        EXPLOSION,
        POISON,
        WITHER,
        FREEZING,
        LIGHTNING,
        CACTUS,
        VOID,
        MAGIC,
        UNKNOWN
    }

    // === Config Entry ===
    public static class HazardPattern {
        public final HazardType type;
        public final List<String> patterns;
        public final boolean triggerAchievement;
        public final String achievementId;

        public HazardPattern(HazardType type, List<String> patterns,
                           boolean triggerAchievement, @Nullable String achievementId) {
            this.type = Objects.requireNonNull(type, "HazardType cannot be null");
            this.patterns = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(patterns, "Patterns list cannot be null")));
            this.triggerAchievement = triggerAchievement;
            this.achievementId = achievementId;

            // Fail-fast: patterns list cannot be empty
            if (patterns.isEmpty()) {
                throw new IllegalArgumentException(
                    "HazardPattern for " + type + " must have at least one pattern");
            }
        }

        /**
         * Check if a damage source type matches any of this hazard's patterns.
         */
        public boolean matches(String sourceType) {
            if (sourceType == null || sourceType.isEmpty()) return false;
            String lowerSource = sourceType.toLowerCase(Locale.ROOT);
            for (String pattern : patterns) {
                if (lowerSource.contains(pattern.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            return false;
        }
    }

    // === Registry State ===
    private final List<HazardPattern> registeredPatterns = new ArrayList<>();
    private boolean loaded = false;
    private Path configPath;

    private HazardTypeRegistry() {}

    /**
     * Initialize the registry with config path.
     * Should be called during mod initialization.
     */
    public void initialize(Path gameDir) {
        this.configPath = gameDir.resolve("config").resolve("devmod").resolve("hazard_types.json");
        load();
    }

    /**
     * Load hazard types from config file, or create defaults if missing.
     * Throws RuntimeException on malformed config (fail-fast).
     */
    public void load() {
        registeredPatterns.clear();

        if (configPath == null) {
            LOGGER.warn("[HazardTypeRegistry] Config path not set, using defaults");
            loadDefaults();
            return;
        }

        if (!Files.exists(configPath)) {
            LOGGER.info("[HazardTypeRegistry] Creating default config at {}", configPath);
            loadDefaults();
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);

            if (root == null) {
                throw new JsonParseException("Config file is empty or invalid JSON");
            }

            // Validate version
            int version = root.has("version") ? root.get("version").getAsInt() : 0;
            if (version < 1) {
                LOGGER.warn("[HazardTypeRegistry] Old config version {}, migrating to v1", version);
            }

            // Parse hazard patterns
            if (!root.has("hazards") || !root.get("hazards").isJsonArray()) {
                throw new JsonParseException("Config missing 'hazards' array");
            }

            JsonArray hazards = root.getAsJsonArray("hazards");
            for (JsonElement element : hazards) {
                if (!element.isJsonObject()) {
                    throw new JsonParseException("Hazard entry must be an object");
                }

                JsonObject hazardObj = element.getAsJsonObject();
                HazardPattern pattern = parseHazardPattern(hazardObj);
                registeredPatterns.add(pattern);
            }

            loaded = true;
            LOGGER.info("[HazardTypeRegistry] Loaded {} hazard patterns from config",
                registeredPatterns.size());

        } catch (JsonParseException e) {
            // FAIL-FAST: Invalid config should crash early, not silently use defaults
            String msg = "Malformed hazard_types.json config: " + e.getMessage();
            LOGGER.error("[HazardTypeRegistry] {}", msg);
            throw new RuntimeException(msg, e);
        } catch (IOException e) {
            LOGGER.error("[HazardTypeRegistry] Failed to read config: {}", e.getMessage());
            loadDefaults();
        }
    }

    private HazardPattern parseHazardPattern(JsonObject obj) throws JsonParseException {
        // Required: type
        if (!obj.has("type")) {
            throw new JsonParseException("Hazard entry missing required 'type' field");
        }

        String typeStr = obj.get("type").getAsString();
        HazardType type;
        try {
            type = HazardType.valueOf(typeStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new JsonParseException("Unknown hazard type: " + typeStr +
                ". Valid types: " + Arrays.toString(HazardType.values()));
        }

        // Required: patterns array
        if (!obj.has("patterns") || !obj.get("patterns").isJsonArray()) {
            throw new JsonParseException("Hazard entry for " + type + " missing 'patterns' array");
        }

        List<String> patterns = new ArrayList<>();
        for (JsonElement patternEl : obj.getAsJsonArray("patterns")) {
            String pattern = patternEl.getAsString();
            if (pattern == null || pattern.isBlank()) {
                throw new JsonParseException("Empty pattern in hazard " + type);
            }
            patterns.add(pattern);
        }

        if (patterns.isEmpty()) {
            throw new JsonParseException("Hazard " + type + " must have at least one pattern");
        }

        // Optional: achievement trigger
        boolean triggerAchievement = obj.has("triggerAchievement") &&
            obj.get("triggerAchievement").getAsBoolean();
        String achievementId = obj.has("achievementId") ?
            obj.get("achievementId").getAsString() : null;

        return new HazardPattern(type, patterns, triggerAchievement, achievementId);
    }

    /**
     * Load default hazard patterns (used when config doesn't exist).
     */
    private void loadDefaults() {
        registeredPatterns.clear();

        // Default patterns matching original hardcoded behavior
        registeredPatterns.add(new HazardPattern(
            HazardType.FALL, List.of("fall"), false, null));

        registeredPatterns.add(new HazardPattern(
            HazardType.FIRE, List.of("fire", "burn", "in_fire", "on_fire"), false, null));

        registeredPatterns.add(new HazardPattern(
            HazardType.LAVA, List.of("lava"), false, null));

        registeredPatterns.add(new HazardPattern(
            HazardType.DROWNING, List.of("drown"), false, null));

        registeredPatterns.add(new HazardPattern(
            HazardType.EXPLOSION, List.of("explosion", "explode"),
            true, "survived_explosion"));

        registeredPatterns.add(new HazardPattern(
            HazardType.POISON, List.of("poison"), false, null));

        registeredPatterns.add(new HazardPattern(
            HazardType.WITHER, List.of("wither"), false, null));

        registeredPatterns.add(new HazardPattern(
            HazardType.FREEZING, List.of("freeze", "frozen"), false, null));

        registeredPatterns.add(new HazardPattern(
            HazardType.LIGHTNING, List.of("lightning"), false, null));

        registeredPatterns.add(new HazardPattern(
            HazardType.CACTUS, List.of("cactus"), false, null));

        registeredPatterns.add(new HazardPattern(
            HazardType.VOID, List.of("void", "out_of_world"), false, null));

        registeredPatterns.add(new HazardPattern(
            HazardType.MAGIC, List.of("magic", "indirect_magic"), false, null));

        loaded = true;
        LOGGER.info("[HazardTypeRegistry] Loaded {} default hazard patterns",
            registeredPatterns.size());
    }

    /**
     * Save current patterns to config file.
     */
    public void save() {
        if (configPath == null) {
            LOGGER.warn("[HazardTypeRegistry] Cannot save: config path not set");
            return;
        }

        try {
            Files.createDirectories(configPath.getParent());

            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            root.addProperty("_comment", "DevMod Hazard Type Registry - defines environmental damage patterns");

            JsonArray hazards = new JsonArray();
            for (HazardPattern pattern : registeredPatterns) {
                JsonObject hazardObj = new JsonObject();
                hazardObj.addProperty("type", pattern.type.name());

                JsonArray patternsArray = new JsonArray();
                for (String p : pattern.patterns) {
                    patternsArray.add(p);
                }
                hazardObj.add("patterns", patternsArray);

                if (pattern.triggerAchievement) {
                    hazardObj.addProperty("triggerAchievement", true);
                    if (pattern.achievementId != null) {
                        hazardObj.addProperty("achievementId", pattern.achievementId);
                    }
                }

                hazards.add(hazardObj);
            }
            root.add("hazards", hazards);

            try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }

            LOGGER.info("[HazardTypeRegistry] Saved config to {}", configPath);

        } catch (IOException e) {
            LOGGER.error("[HazardTypeRegistry] Failed to save config: {}", e.getMessage());
        }
    }

    // === Query Methods ===

    /**
     * Classify a damage source into a hazard type.
     * @param source The damage source to classify
     * @return The matching HazardType, or UNKNOWN if no pattern matches
     */
    public HazardType classify(DamageSource source) {
        if (source == null) return HazardType.UNKNOWN;

        // Extract source type string
        String msgId = source.type().msgId();
        ResourceLocation typeKey = msgId != null ?
            ResourceLocation.tryParse(Objects.requireNonNull(msgId)) : null;
        String sourceType = typeKey != null ? Objects.requireNonNull(typeKey.toString()) : Objects.requireNonNull(source.getMsgId());

        return classifyByString(sourceType);
    }

    /**
     * Classify a damage source string into a hazard type.
     * @param sourceType The damage source type string
     * @return The matching HazardType, or UNKNOWN if no pattern matches
     */
    public HazardType classifyByString(String sourceType) {
        if (sourceType == null || sourceType.isEmpty()) {
            return HazardType.UNKNOWN;
        }

        // First matching pattern wins (order matters!)
        for (HazardPattern pattern : registeredPatterns) {
            if (pattern.matches(sourceType)) {
                return pattern.type;
            }
        }

        return HazardType.UNKNOWN;
    }

    /**
     * Get the pattern for a specific hazard type.
     * @return The HazardPattern, or null if not registered
     */
    @Nullable
    public HazardPattern getPattern(HazardType type) {
        for (HazardPattern pattern : registeredPatterns) {
            if (pattern.type == type) {
                return pattern;
            }
        }
        return null;
    }

    /**
     * Check if a damage source triggers an achievement.
     * @return The achievement ID, or null if none
     */
    @Nullable
    public String getTriggeredAchievement(DamageSource source) {
        if (source == null) return null;

        String msgId = source.type().msgId();
        ResourceLocation typeKey = msgId != null ?
            ResourceLocation.tryParse(Objects.requireNonNull(msgId)) : null;
        String sourceType = typeKey != null ? Objects.requireNonNull(typeKey.toString()) : Objects.requireNonNull(source.getMsgId());

        for (HazardPattern pattern : registeredPatterns) {
            if (pattern.matches(sourceType) && pattern.triggerAchievement) {
                return pattern.achievementId;
            }
        }

        return null;
    }

    /**
     * Get all registered hazard types.
     */
    public List<HazardPattern> getAllPatterns() {
        return Collections.unmodifiableList(registeredPatterns);
    }

    /**
     * Check if registry is loaded.
     */
    public boolean isLoaded() {
        return loaded;
    }

    /**
     * Reload config from disk.
     */
    public void reload() {
        LOGGER.info("[HazardTypeRegistry] Reloading config...");
        load();
    }
}
