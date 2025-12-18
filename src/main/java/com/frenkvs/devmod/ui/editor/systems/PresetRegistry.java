package com.frenkvs.devmod.ui.editor.systems;

import com.frenkvs.devmod.DevMod;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Registry for hierarchical presets with 3-level priority system.
 *
 * Presets are loaded from:
 * - config/devmod/presets/global/       (priority 1)
 * - config/devmod/presets/category/     (priority 2)
 * - config/devmod/presets/modpack/{id}/ (priority 3)
 *
 * Resolution order: MODPACK > CATEGORY > GLOBAL (first match wins by priority)
 */
public final class PresetRegistry {

    // Singleton instance (eager initialization - thread-safe)
    public static final PresetRegistry INSTANCE = new PresetRegistry();

    // Presets organized by scope
    private final Map<PresetScope, List<RegistryPreset>> presets = new ConcurrentHashMap<>();

    // All presets flat (for UI listing)
    private final List<RegistryPreset> allPresets = Collections.synchronizedList(new ArrayList<>());

    // Detected modpack (cached)
    private String detectedModpack = null;

    // Bundled presets resource path
    private static final String BUNDLED_PRESETS_PATH = "/data/devmod/presets/";

    private PresetRegistry() {
        DevMod.LOGGER.info("[PresetRegistry] Initialized");
    }

    // ═══════════════════════════════════════════════════════════════
    // LOADING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Load all presets from config directory.
     * Should be called during FMLCommonSetupEvent.
     */
    public void loadFromConfig() {
        loadFromConfig(FMLPaths.CONFIGDIR.get().resolve("devmod/presets"));
    }

    /**
     * Load all presets from the specified directory.
     *
     * @param presetsDir root directory containing global/, category/, modpack/ subdirs
     */
    public void loadFromConfig(Path presetsDir) {
        presets.clear();
        allPresets.clear();

        // Detect modpack first
        detectedModpack = ModpackDetector.detect();

        try {
            // Ensure directory structure exists
            ensureDirectoryStructure(presetsDir);

            // Copy bundled presets on first run
            copyBundledPresetsIfNeeded(presetsDir);

            // Load global presets
            Path globalDir = presetsDir.resolve("global");
            if (Files.exists(globalDir)) {
                loadPresetsFromDirectory(globalDir, new PresetScope.Global());
            }

            // Load category presets
            Path categoryDir = presetsDir.resolve("category");
            if (Files.exists(categoryDir)) {
                loadCategoryPresets(categoryDir);
            }

            // Load modpack presets (if modpack detected)
            if (detectedModpack != null) {
                Path modpackDir = presetsDir.resolve("modpack").resolve(detectedModpack);
                if (Files.exists(modpackDir)) {
                    loadModpackPresets(modpackDir, detectedModpack);
                }
            }

            DevMod.LOGGER.info("[PresetRegistry] Loaded {} presets ({} global, {} category, {} modpack)",
                allPresets.size(),
                countByScope(PresetScope.Global.class),
                countByScope(PresetScope.Category.class),
                countByScope(PresetScope.Modpack.class));

        } catch (Exception e) {
            DevMod.LOGGER.error("[PresetRegistry] Failed to load presets: {}", e.getMessage(), e);
        }
    }

    private void ensureDirectoryStructure(Path presetsDir) throws IOException {
        Files.createDirectories(presetsDir.resolve("global"));
        Files.createDirectories(presetsDir.resolve("category"));
        Files.createDirectories(presetsDir.resolve("modpack"));
    }

    private void copyBundledPresetsIfNeeded(Path presetsDir) {
        Path globalDir = presetsDir.resolve("global");
        try {
            // Check if global dir is empty (first run)
            try (Stream<Path> files = Files.list(globalDir)) {
                if (files.findAny().isPresent()) {
                    return; // Already has files, don't overwrite
                }
            }

            // Copy bundled presets
            String[] bundledPresets = {
                "vanilla_default.json",
                "balanced_pvp.json",
                "overpowered_debug.json",
                "tank_build.json",
                "glass_cannon.json",
                "diamond_tier.json"
            };

            for (String filename : bundledPresets) {
                try (InputStream is = getClass().getResourceAsStream(BUNDLED_PRESETS_PATH + filename)) {
                    if (is != null) {
                        Path target = globalDir.resolve(filename);
                        Files.copy(is, target);
                        DevMod.LOGGER.debug("[PresetRegistry] Copied bundled preset: {}", filename);
                    }
                } catch (Exception e) {
                    DevMod.LOGGER.warn("[PresetRegistry] Failed to copy bundled preset {}: {}", filename, e.getMessage());
                }
            }

        } catch (Exception e) {
            DevMod.LOGGER.warn("[PresetRegistry] Failed to copy bundled presets: {}", e.getMessage());
        }
    }

    private void loadPresetsFromDirectory(Path dir, PresetScope scope) throws IOException {
        try (Stream<Path> files = Files.walk(dir, 2)) {
            files.filter(p -> p.toString().endsWith(".json"))
                 .filter(Files::isRegularFile)
                 .forEach(p -> loadPresetFile(p, scope));
        }
    }

    private void loadCategoryPresets(Path categoryDir) throws IOException {
        try (Stream<Path> categories = Files.list(categoryDir)) {
            categories.filter(Files::isDirectory)
                      .forEach(catDir -> {
                          String category = catDir.getFileName().toString();
                          PresetScope scope = new PresetScope.Category(category);
                          try {
                              loadPresetsFromDirectory(catDir, scope);
                          } catch (IOException e) {
                              DevMod.LOGGER.warn("[PresetRegistry] Failed to load category {}: {}",
                                  category, e.getMessage());
                          }
                      });
        }
    }

    private void loadModpackPresets(Path modpackDir, String modpackId) throws IOException {
        try (Stream<Path> files = Files.walk(modpackDir, 3)) {
            files.filter(p -> p.toString().endsWith(".json"))
                 .filter(Files::isRegularFile)
                 .forEach(p -> {
                     // Determine category from directory structure
                     Path relative = modpackDir.relativize(p);
                     String category = relative.getParent() != null
                         ? relative.getParent().toString().replace("\\", "/")
                         : "general";
                     PresetScope scope = new PresetScope.Modpack(modpackId, category);
                     loadPresetFile(p, scope);
                 });
        }
    }

    private void loadPresetFile(Path file, PresetScope scope) {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            RegistryPreset preset = parsePreset(json, scope, file.getFileName().toString());
            if (preset != null) {
                addPreset(preset);
            }
        } catch (Exception e) {
            DevMod.LOGGER.warn("[PresetRegistry] Failed to load preset {}: {}",
                file.getFileName(), e.getMessage());
        }
    }

    @Nullable
    private RegistryPreset parsePreset(JsonObject json, PresetScope scope, String filename) {
        try {
            String id = json.has("id") ? json.get("id").getAsString()
                : filename.replace(".json", "");
            String name = json.has("name") ? json.get("name").getAsString() : id;
            String description = json.has("description") ? json.get("description").getAsString() : "";
            String category = json.has("category") ? json.get("category").getAsString() : "general";

            // Parse values map
            Map<String, Object> values = new LinkedHashMap<>();
            if (json.has("values") && json.get("values").isJsonObject()) {
                JsonObject valuesObj = json.getAsJsonObject("values");
                for (var entry : valuesObj.entrySet()) {
                    var val = entry.getValue();
                    if (val.isJsonPrimitive()) {
                        if (val.getAsJsonPrimitive().isNumber()) {
                            values.put(entry.getKey(), val.getAsDouble());
                        } else if (val.getAsJsonPrimitive().isBoolean()) {
                            values.put(entry.getKey(), val.getAsBoolean());
                        } else {
                            values.put(entry.getKey(), val.getAsString());
                        }
                    }
                }
            }

            // Parse metadata
            String version = json.has("version") ? json.get("version").getAsString() : "1.0.0";
            String author = json.has("author") ? json.get("author").getAsString() : "unknown";
            List<String> tags = new ArrayList<>();
            if (json.has("tags") && json.get("tags").isJsonArray()) {
                json.getAsJsonArray("tags").forEach(e -> tags.add(e.getAsString()));
            }

            PresetMetadata metadata = new PresetMetadata(version, author, Instant.now(), tags);

            return new RegistryPreset(id, name, description, scope, category, values, metadata);

        } catch (Exception e) {
            DevMod.LOGGER.warn("[PresetRegistry] Failed to parse preset: {}", e.getMessage());
            return null;
        }
    }

    private void addPreset(RegistryPreset preset) {
        presets.computeIfAbsent(preset.scope(), k -> Collections.synchronizedList(new ArrayList<>()))
               .add(preset);
        allPresets.add(preset);
    }

    private long countByScope(Class<? extends PresetScope> scopeClass) {
        return allPresets.stream()
            .filter(p -> scopeClass.isInstance(p.scope()))
            .count();
    }

    // ═══════════════════════════════════════════════════════════════
    // RESOLUTION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Resolve presets applicable to the given item, sorted by priority (highest first).
     *
     * @param item the item to resolve presets for
     * @return list of applicable presets, sorted by priority descending
     */
    public List<RegistryPreset> resolveForItem(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return List.of();
        }

        String itemCategory = detectItemCategory(item);

        return allPresets.stream()
            .filter(p -> matchesCategory(p, itemCategory))
            .sorted(Comparator.comparingInt((RegistryPreset p) -> p.scope().priority()).reversed())
            .collect(Collectors.toList());
    }

    /**
     * Get all presets for a specific category.
     */
    public List<RegistryPreset> getPresetsForCategory(String category) {
        return allPresets.stream()
            .filter(p -> p.category().equalsIgnoreCase(category) ||
                        p.category().equalsIgnoreCase("general") ||
                        "weapon".equalsIgnoreCase(p.category()) && isWeaponCategory(category) ||
                        "armor".equalsIgnoreCase(p.category()) && isArmorCategory(category))
            .sorted(Comparator.comparingInt((RegistryPreset p) -> p.scope().priority()).reversed())
            .collect(Collectors.toList());
    }

    /**
     * Get all loaded presets.
     */
    public List<RegistryPreset> getAllPresets() {
        return Collections.unmodifiableList(new ArrayList<>(allPresets));
    }

    /**
     * Get presets by scope.
     */
    public List<RegistryPreset> getPresetsByScope(PresetScope scope) {
        return presets.getOrDefault(scope, List.of());
    }

    /**
     * Get the detected modpack ID, if any.
     */
    @Nullable
    public String getDetectedModpack() {
        return detectedModpack;
    }

    // ═══════════════════════════════════════════════════════════════
    // CATEGORY DETECTION
    // ═══════════════════════════════════════════════════════════════

    private boolean matchesCategory(RegistryPreset preset, String itemCategory) {
        String presetCat = preset.category().toLowerCase();
        String itemCat = itemCategory.toLowerCase();

        // Exact match
        if (presetCat.equals(itemCat)) return true;

        // General presets match everything
        if (presetCat.equals("general")) return true;

        // Weapon presets match all weapon types
        if (presetCat.equals("weapon") && isWeaponCategory(itemCat)) return true;

        // Armor presets match all armor types
        if (presetCat.equals("armor") && isArmorCategory(itemCat)) return true;

        return false;
    }

    private boolean isWeaponCategory(String category) {
        return Set.of("sword", "axe", "pickaxe", "shovel", "hoe", "bow", "crossbow", "trident")
            .contains(category.toLowerCase());
    }

    private boolean isArmorCategory(String category) {
        return Set.of("helmet", "chestplate", "leggings", "boots", "shield")
            .contains(category.toLowerCase());
    }

    /**
     * Detect item category from ItemStack.
     */
    public static String detectItemCategory(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "general";

        Item item = stack.getItem();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(item, "item"));
        String path = id.getPath().toLowerCase();

        // Check by item name
        if (path.contains("sword")) return "sword";
        if (path.contains("axe") && !path.contains("pickaxe")) return "axe";
        if (path.contains("pickaxe")) return "pickaxe";
        if (path.contains("shovel") || path.contains("spade")) return "shovel";
        if (path.contains("hoe")) return "hoe";
        if (path.contains("bow") && !path.contains("crossbow")) return "bow";
        if (path.contains("crossbow")) return "crossbow";
        if (path.contains("trident")) return "trident";
        if (path.contains("helmet") || path.contains("cap") || path.contains("hood")) return "helmet";
        if (path.contains("chestplate") || path.contains("tunic") || path.contains("jacket")) return "chestplate";
        if (path.contains("leggings") || path.contains("pants") || path.contains("greaves")) return "leggings";
        if (path.contains("boots") || path.contains("shoes") || path.contains("sabatons")) return "boots";
        if (path.contains("shield")) return "shield";

        // Check by tags
        try {
            if (stack.is(Objects.requireNonNull(ItemTags.SWORDS))) return "sword";
            if (stack.is(Objects.requireNonNull(ItemTags.AXES))) return "axe";
            if (stack.is(Objects.requireNonNull(ItemTags.PICKAXES))) return "pickaxe";
            if (stack.is(Objects.requireNonNull(ItemTags.SHOVELS))) return "shovel";
            if (stack.is(Objects.requireNonNull(ItemTags.HOES))) return "hoe";
        } catch (Exception ignored) {}

        return "general";
    }

    // ═══════════════════════════════════════════════════════════════
    // INNER TYPES
    // ═══════════════════════════════════════════════════════════════

    /**
     * A preset loaded from the registry.
     */
    public record RegistryPreset(
        String id,
        String name,
        String description,
        PresetScope scope,
        String category,
        Map<String, Object> values,
        PresetMetadata metadata
    ) {
        /**
         * Get a value as double, or default if not present.
         */
        public double getDouble(String key, double defaultValue) {
            Object val = values.get(key);
            if (val instanceof Number n) return n.doubleValue();
            return defaultValue;
        }

        /**
         * Get a value as float, or default if not present.
         */
        public float getFloat(String key, float defaultValue) {
            return (float) getDouble(key, defaultValue);
        }

        /**
         * Get a value as boolean, or default if not present.
         */
        public boolean getBoolean(String key, boolean defaultValue) {
            Object val = values.get(key);
            if (val instanceof Boolean b) return b;
            return defaultValue;
        }

        /**
         * Check if this preset is from a user (not bundled/official).
         */
        public boolean isUserPreset() {
            return metadata.author().equalsIgnoreCase("user");
        }
    }

    /**
     * Metadata for a preset.
     */
    public record PresetMetadata(
        String version,
        String author,
        Instant created,
        List<String> tags
    ) {}
}
