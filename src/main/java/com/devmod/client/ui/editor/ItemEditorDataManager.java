package com.devmod.client.ui.editor;

import com.devmod.DevMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.devmod.util.ConfigPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Manages persistent data for the Item Editor:
 * - Presets (saved to JSON)
 * - Favorites (enchantments/attributes)
 * - History log
 * - Templates
 */
public class ItemEditorDataManager {

    public static final ItemEditorDataManager INSTANCE = new ItemEditorDataManager();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Data containers
    private final List<PresetData> presets = new ArrayList<>();
    private final Set<String> favoriteEnchantments = new LinkedHashSet<>();
    private final Set<String> favoriteAttributes = new LinkedHashSet<>();
    private final List<HistoryEntry> history = new ArrayList<>();
    private final Map<String, TemplateData> templates = new LinkedHashMap<>();

    private static final int MAX_HISTORY = 100;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault());

    private boolean initialized = false;

    private ItemEditorDataManager() {
        // Don't load in constructor - do lazy init
    }

    /**
     * Ensure data is loaded. Call this before accessing any data.
     */
    public void ensureInitialized() {
        if (!initialized) {
            initialized = true;
            loadAll();
        }
    }

    /**
     * Get the data directory using ConfigPaths (works in both dev and production).
     */
    private Path getDataDirectory() {
        return ConfigPaths.getItemEditorDir();
    }

    // ==================== PRESETS ====================

    public static class PresetData {
        public String name;
        public String itemType; // For filtering by item type
        public long createdAt;
        public String scope = "SPECIFIC";
        public String devmodVersion = "unknown";
        public List<Float> statValues = new ArrayList<>();
        public List<EnchantData> enchantments = new ArrayList<>();
        public List<AttrData> attributes = new ArrayList<>();
        public boolean unbreakable;

        public PresetData() {
            this.createdAt = System.currentTimeMillis();
        }

        public PresetData(String name) {
            this.name = name;
            this.createdAt = System.currentTimeMillis();
        }
    }

    public static class EnchantData {
        public String id; // e.g., "minecraft:sharpness"
        public int level;

        public EnchantData() {}
        public EnchantData(String id, int level) {
            this.id = id;
            this.level = level;
        }
    }

    public static class AttrData {
        public String id; // e.g., "minecraft:generic.attack_damage"
        public double value;
        public int operation; // 0=ADD, 1=MULT_BASE, 2=MULT_TOTAL

        public AttrData() {}
        public AttrData(String id, double value, int operation) {
            this.id = id;
            this.value = value;
            this.operation = operation;
        }
    }

    public List<PresetData> getPresets() {
        ensureInitialized();
        return Collections.unmodifiableList(presets);
    }

    public List<PresetData> getPresetsForItemType(String itemType) {
        ensureInitialized();
        if (itemType == null || itemType.isEmpty()) return getPresets();
        return presets.stream()
            .filter(p -> matchesItemType(p.itemType, itemType))
            .toList();
    }

    /**
     * Check if a preset's itemType matches the requested itemType.
     * Supports:
     * - Exact match (minecraft:diamond_sword)
     * - Category match (weapon, armor, ranged)
     * - Tag match (#forge:swords)
     * - GLOBAL scope (matches all)
     * - Empty/null itemType (matches all)
     */
    private boolean matchesItemType(String presetItemType, String requestedType) {
        // Null or empty preset itemType matches everything
        if (presetItemType == null || presetItemType.isEmpty()) return true;

        // GLOBAL scope matches everything
        if ("GLOBAL".equalsIgnoreCase(presetItemType)) return true;

        String preset = presetItemType.trim().toLowerCase(java.util.Locale.ROOT);
        String requested = requestedType.trim().toLowerCase(java.util.Locale.ROOT);

        // Exact match (case-insensitive)
        if (preset.equals(requested)) return true;

        // Category matching: preset "weapon" matches "minecraft:diamond_sword"
        if (isCategory(preset)) {
            return matchesCategory(preset, requested);
        }

        // Tag matching: preset "#forge:swords" should be checked at runtime
        // Here we just allow tag-based presets to show up in the list
        if (preset.startsWith("#")) {
            // Let the preset show - actual filtering happens at apply time via DataPreset.scope()
            return true;
        }

        // Path-only match: "diamond_sword" matches "minecraft:diamond_sword"
        if (!preset.contains(":") && requested.contains(":")) {
            String requestedPath = requested.substring(requested.indexOf(':') + 1);
            if (preset.equals(requestedPath)) return true;
        }

        // Namespace-agnostic match: "modid:sword" matches "minecraft:sword" if paths match
        if (preset.contains(":") && requested.contains(":")) {
            String presetPath = preset.substring(preset.indexOf(':') + 1);
            String requestedPath = requested.substring(requested.indexOf(':') + 1);
            if (presetPath.equals(requestedPath)) return true;
        }

        return false;
    }

    private boolean isCategory(String type) {
        return "weapon".equals(type) || "armor".equals(type) || "ranged".equals(type)
            || "melee".equals(type) || "tool".equals(type);
    }

    private boolean matchesCategory(String category, String itemType) {
        String lower = itemType.toLowerCase(java.util.Locale.ROOT);
        return switch (category) {
            case "weapon", "melee" -> lower.contains("sword") || lower.contains("axe")
                || lower.contains("mace") || lower.contains("trident");
            case "ranged" -> lower.contains("bow") || lower.contains("crossbow");
            case "armor" -> lower.contains("helmet") || lower.contains("chestplate")
                || lower.contains("leggings") || lower.contains("boots")
                || lower.contains("shield");
            case "tool" -> lower.contains("pickaxe") || lower.contains("shovel")
                || lower.contains("hoe");
            default -> false;
        };
    }

    public void savePreset(PresetData preset) {
        ensureInitialized();
        // Remove existing with same name
        presets.removeIf(p -> p.name.equals(preset.name));
        presets.add(0, preset); // Add at beginning (most recent first)
        savePresets();
    }

    public void deletePreset(String name) {
        ensureInitialized();
        presets.removeIf(p -> p.name.equals(name));
        savePresets();
    }

    public Optional<PresetData> getPreset(String name) {
        ensureInitialized();
        return presets.stream().filter(p -> p.name.equals(name)).findFirst();
    }

    // ==================== FAVORITES ====================

    public Set<String> getFavoriteEnchantments() {
        ensureInitialized();
        return Collections.unmodifiableSet(favoriteEnchantments);
    }

    public Set<String> getFavoriteAttributes() {
        ensureInitialized();
        return Collections.unmodifiableSet(favoriteAttributes);
    }

    public boolean isEnchantmentFavorite(String id) {
        ensureInitialized();
        return favoriteEnchantments.contains(id);
    }

    public boolean isAttributeFavorite(String id) {
        ensureInitialized();
        return favoriteAttributes.contains(id);
    }

    public void toggleEnchantmentFavorite(String id) {
        ensureInitialized();
        if (favoriteEnchantments.contains(id)) {
            favoriteEnchantments.remove(id);
        } else {
            favoriteEnchantments.add(id);
        }
        saveFavorites();
    }

    public void toggleAttributeFavorite(String id) {
        ensureInitialized();
        if (favoriteAttributes.contains(id)) {
            favoriteAttributes.remove(id);
        } else {
            favoriteAttributes.add(id);
        }
        saveFavorites();
    }

    // ==================== HISTORY ====================

    public static class HistoryEntry {
        public long timestamp;
        public String action; // "modify", "preset_load", "preset_save", etc.
        public String itemName;
        public String details;

        public HistoryEntry() {}
        public HistoryEntry(String action, String itemName, String details) {
            this.timestamp = System.currentTimeMillis();
            this.action = action;
            this.itemName = itemName;
            this.details = details;
        }

        public String getFormattedTime() {
            return TIME_FORMAT.format(Instant.ofEpochMilli(timestamp));
        }
    }

    public List<HistoryEntry> getHistory() {
        ensureInitialized();
        return Collections.unmodifiableList(history);
    }

    public List<HistoryEntry> getRecentHistory(int count) {
        ensureInitialized();
        int start = Math.max(0, history.size() - count);
        return history.subList(start, history.size());
    }

    public void addHistoryEntry(String action, String itemName, String details) {
        ensureInitialized();
        history.add(new HistoryEntry(action, itemName, details));
        // Trim history if too long
        while (history.size() > MAX_HISTORY) {
            history.remove(0);
        }
        saveHistory();
    }

    public void clearHistory() {
        ensureInitialized();
        history.clear();
        saveHistory();
    }

    // ==================== TEMPLATES ====================

    public static class TemplateData {
        public String name;
        public String itemCategory; // "sword", "axe", "pickaxe", "bow", "armor", etc.
        public List<EnchantData> enchantments = new ArrayList<>();
        public List<AttrData> attributes = new ArrayList<>();
        public boolean isBuiltIn = false;
    }

    public Map<String, TemplateData> getTemplates() {
        ensureInitialized();
        return Collections.unmodifiableMap(templates);
    }

    public Optional<TemplateData> getTemplateForCategory(String category) {
        ensureInitialized();
        return Optional.ofNullable(templates.get(category));
    }

    public void saveTemplate(TemplateData template) {
        ensureInitialized();
        templates.put(template.itemCategory, template);
        saveTemplates();
    }

    /**
     * Get suggested template based on item ID.
     */
    public Optional<TemplateData> suggestTemplate(String itemId) {
        ensureInitialized();
        if (itemId == null) return Optional.empty();

        String lower = itemId.toLowerCase();
        if (lower.contains("sword")) return getTemplateForCategory("sword");
        if (lower.contains("axe") && !lower.contains("pickaxe")) return getTemplateForCategory("axe");
        if (lower.contains("pickaxe")) return getTemplateForCategory("pickaxe");
        if (lower.contains("shovel") || lower.contains("spade")) return getTemplateForCategory("shovel");
        if (lower.contains("hoe")) return getTemplateForCategory("hoe");
        if (lower.contains("bow") && !lower.contains("crossbow")) return getTemplateForCategory("bow");
        if (lower.contains("crossbow")) return getTemplateForCategory("crossbow");
        if (lower.contains("trident")) return getTemplateForCategory("trident");
        if (lower.contains("helmet") || lower.contains("cap")) return getTemplateForCategory("helmet");
        if (lower.contains("chestplate") || lower.contains("tunic")) return getTemplateForCategory("chestplate");
        if (lower.contains("leggings") || lower.contains("pants")) return getTemplateForCategory("leggings");
        if (lower.contains("boots")) return getTemplateForCategory("boots");
        if (lower.contains("shield")) return getTemplateForCategory("shield");
        if (lower.contains("fishing")) return getTemplateForCategory("fishing_rod");

        return Optional.empty();
    }

    private void initDefaultTemplates() {
        // Sword template
        TemplateData sword = new TemplateData();
        sword.name = "Sword (Combat)";
        sword.itemCategory = "sword";
        sword.isBuiltIn = true;
        sword.enchantments.add(new EnchantData("minecraft:sharpness", 5));
        sword.enchantments.add(new EnchantData("minecraft:fire_aspect", 2));
        sword.enchantments.add(new EnchantData("minecraft:looting", 3));
        sword.enchantments.add(new EnchantData("minecraft:unbreaking", 3));
        sword.enchantments.add(new EnchantData("minecraft:mending", 1));
        templates.putIfAbsent("sword", sword);

        // Axe template
        TemplateData axe = new TemplateData();
        axe.name = "Axe (Combat/Utility)";
        axe.itemCategory = "axe";
        axe.isBuiltIn = true;
        axe.enchantments.add(new EnchantData("minecraft:sharpness", 5));
        axe.enchantments.add(new EnchantData("minecraft:efficiency", 5));
        axe.enchantments.add(new EnchantData("minecraft:unbreaking", 3));
        axe.enchantments.add(new EnchantData("minecraft:mending", 1));
        templates.putIfAbsent("axe", axe);

        // Pickaxe template
        TemplateData pickaxe = new TemplateData();
        pickaxe.name = "Pickaxe (Mining)";
        pickaxe.itemCategory = "pickaxe";
        pickaxe.isBuiltIn = true;
        pickaxe.enchantments.add(new EnchantData("minecraft:efficiency", 5));
        pickaxe.enchantments.add(new EnchantData("minecraft:fortune", 3));
        pickaxe.enchantments.add(new EnchantData("minecraft:unbreaking", 3));
        pickaxe.enchantments.add(new EnchantData("minecraft:mending", 1));
        templates.putIfAbsent("pickaxe", pickaxe);

        // Bow template
        TemplateData bow = new TemplateData();
        bow.name = "Bow (Ranged)";
        bow.itemCategory = "bow";
        bow.isBuiltIn = true;
        bow.enchantments.add(new EnchantData("minecraft:power", 5));
        bow.enchantments.add(new EnchantData("minecraft:infinity", 1));
        bow.enchantments.add(new EnchantData("minecraft:flame", 1));
        bow.enchantments.add(new EnchantData("minecraft:unbreaking", 3));
        templates.putIfAbsent("bow", bow);

        // Helmet template
        TemplateData helmet = new TemplateData();
        helmet.name = "Helmet (Protection)";
        helmet.itemCategory = "helmet";
        helmet.isBuiltIn = true;
        helmet.enchantments.add(new EnchantData("minecraft:protection", 4));
        helmet.enchantments.add(new EnchantData("minecraft:unbreaking", 3));
        helmet.enchantments.add(new EnchantData("minecraft:mending", 1));
        helmet.enchantments.add(new EnchantData("minecraft:respiration", 3));
        helmet.enchantments.add(new EnchantData("minecraft:aqua_affinity", 1));
        templates.putIfAbsent("helmet", helmet);

        // Chestplate template
        TemplateData chestplate = new TemplateData();
        chestplate.name = "Chestplate (Protection)";
        chestplate.itemCategory = "chestplate";
        chestplate.isBuiltIn = true;
        chestplate.enchantments.add(new EnchantData("minecraft:protection", 4));
        chestplate.enchantments.add(new EnchantData("minecraft:unbreaking", 3));
        chestplate.enchantments.add(new EnchantData("minecraft:mending", 1));
        templates.putIfAbsent("chestplate", chestplate);

        // Leggings template
        TemplateData leggings = new TemplateData();
        leggings.name = "Leggings (Protection)";
        leggings.itemCategory = "leggings";
        leggings.isBuiltIn = true;
        leggings.enchantments.add(new EnchantData("minecraft:protection", 4));
        leggings.enchantments.add(new EnchantData("minecraft:unbreaking", 3));
        leggings.enchantments.add(new EnchantData("minecraft:mending", 1));
        leggings.enchantments.add(new EnchantData("minecraft:swift_sneak", 3));
        templates.putIfAbsent("leggings", leggings);

        // Boots template
        TemplateData boots = new TemplateData();
        boots.name = "Boots (Protection)";
        boots.itemCategory = "boots";
        boots.isBuiltIn = true;
        boots.enchantments.add(new EnchantData("minecraft:protection", 4));
        boots.enchantments.add(new EnchantData("minecraft:unbreaking", 3));
        boots.enchantments.add(new EnchantData("minecraft:mending", 1));
        boots.enchantments.add(new EnchantData("minecraft:feather_falling", 4));
        boots.enchantments.add(new EnchantData("minecraft:depth_strider", 3));
        templates.putIfAbsent("boots", boots);
    }

    // ==================== EXPORT/IMPORT ====================

    public static class ItemConfigExport {
        public String version = "1.0";
        public String itemId;
        public String itemName;
        public long exportTime;
        public List<Float> stats = new ArrayList<>();
        public List<EnchantData> enchantments = new ArrayList<>();
        public List<AttrData> attributes = new ArrayList<>();
        public Integer durability;
        public Boolean unbreakable;
        public Integer repairCost;
    }

    public String exportToJson(ItemConfigExport config) {
        config.exportTime = System.currentTimeMillis();
        return GSON.toJson(config);
    }

    public ItemConfigExport importFromJson(String json) throws JsonParseException {
        return GSON.fromJson(json, ItemConfigExport.class);
    }

    public boolean exportToFile(ItemConfigExport config, String filename) {
        ensureInitialized();
        try {
            Files.createDirectories(getDataDirectory().resolve("exports"));
            Path file = getDataDirectory().resolve("exports").resolve(filename + ".json");
            Files.writeString(file, exportToJson(config), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            DevMod.LOGGER.error("Failed to export config to file: {}", e.getMessage());
            return false;
        }
    }

    public ItemConfigExport importFromFile(String filename) throws IOException {
        ensureInitialized();
        Path file = getDataDirectory().resolve("exports").resolve(filename + ".json");
        String json = Files.readString(file, StandardCharsets.UTF_8);
        return importFromJson(json);
    }

    public List<String> listExportFiles() {
        ensureInitialized();
        try {
            Path exportDir = getDataDirectory().resolve("exports");
            if (!Files.exists(exportDir)) return Collections.emptyList();

            return Files.list(exportDir)
                .filter(p -> p.toString().endsWith(".json"))
                .map(p -> p.getFileName().toString().replace(".json", ""))
                .sorted()
                .toList();
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    // ==================== PERSISTENCE ====================

    private void loadAll() {
        try {
            Files.createDirectories(getDataDirectory());
        } catch (IOException e) {
            DevMod.LOGGER.error("Failed to create data directory: {}", e.getMessage());
        }

        loadPresets();
        loadFavorites();
        loadHistory();
        loadTemplates();
        initDefaultTemplates();
    }

    private void loadPresets() {
        Path file = getDataDirectory().resolve("presets.json");
        if (!Files.exists(file)) return;

        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            PresetData[] loaded = GSON.fromJson(json, PresetData[].class);
            if (loaded != null) {
                presets.clear();
                presets.addAll(Arrays.asList(loaded));
            }
        } catch (Exception e) {
            DevMod.LOGGER.error("Failed to load presets: {}", e.getMessage());
        }
    }

    private void savePresets() {
        try {
            Files.createDirectories(getDataDirectory());
            Path file = getDataDirectory().resolve("presets.json");
            Files.writeString(file, GSON.toJson(presets), StandardCharsets.UTF_8);
        } catch (IOException e) {
            DevMod.LOGGER.error("Failed to save presets: {}", e.getMessage());
        }
    }

    private void loadFavorites() {
        Path file = getDataDirectory().resolve("favorites.json");
        if (!Files.exists(file)) return;

        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            if (obj != null) {
                favoriteEnchantments.clear();
                favoriteAttributes.clear();

                if (obj.has("enchantments")) {
                    obj.getAsJsonArray("enchantments").forEach(e ->
                        favoriteEnchantments.add(e.getAsString()));
                }
                if (obj.has("attributes")) {
                    obj.getAsJsonArray("attributes").forEach(e ->
                        favoriteAttributes.add(e.getAsString()));
                }
            }
        } catch (Exception e) {
            DevMod.LOGGER.error("Failed to load favorites: {}", e.getMessage());
        }
    }

    private void saveFavorites() {
        try {
            Files.createDirectories(getDataDirectory());
            JsonObject obj = new JsonObject();
            JsonArray enchArr = new JsonArray();
            favoriteEnchantments.forEach(enchArr::add);
            obj.add("enchantments", enchArr);

            JsonArray attrArr = new JsonArray();
            favoriteAttributes.forEach(attrArr::add);
            obj.add("attributes", attrArr);

            Path file = getDataDirectory().resolve("favorites.json");
            Files.writeString(file, GSON.toJson(obj), StandardCharsets.UTF_8);
        } catch (IOException e) {
            DevMod.LOGGER.error("Failed to save favorites: {}", e.getMessage());
        }
    }

    private void loadHistory() {
        Path file = getDataDirectory().resolve("history.json");
        if (!Files.exists(file)) return;

        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            HistoryEntry[] loaded = GSON.fromJson(json, HistoryEntry[].class);
            if (loaded != null) {
                history.clear();
                history.addAll(Arrays.asList(loaded));
            }
        } catch (Exception e) {
            DevMod.LOGGER.error("Failed to load history: {}", e.getMessage());
        }
    }

    private void saveHistory() {
        try {
            Files.createDirectories(getDataDirectory());
            Path file = getDataDirectory().resolve("history.json");
            Files.writeString(file, GSON.toJson(history), StandardCharsets.UTF_8);
        } catch (IOException e) {
            DevMod.LOGGER.error("Failed to save history: {}", e.getMessage());
        }
    }

    private void loadTemplates() {
        Path file = getDataDirectory().resolve("templates.json");
        if (!Files.exists(file)) return;

        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            if (obj != null) {
                obj.entrySet().forEach(entry -> {
                    TemplateData template = GSON.fromJson(entry.getValue(), TemplateData.class);
                    if (template != null && !template.isBuiltIn) {
                        templates.put(entry.getKey(), template);
                    }
                });
            }
        } catch (Exception e) {
            DevMod.LOGGER.error("Failed to load templates: {}", e.getMessage());
        }
    }

    private void saveTemplates() {
        try {
            Files.createDirectories(getDataDirectory());
            // Only save non-built-in templates
            Map<String, TemplateData> toSave = new LinkedHashMap<>();
            templates.forEach((k, v) -> {
                if (!v.isBuiltIn) toSave.put(k, v);
            });

            Path file = getDataDirectory().resolve("templates.json");
            Files.writeString(file, GSON.toJson(toSave), StandardCharsets.UTF_8);
        } catch (IOException e) {
            DevMod.LOGGER.error("Failed to save templates: {}", e.getMessage());
        }
    }

    /**
     * Force reload all data from disk.
     */
    public void reload() {
        presets.clear();
        favoriteEnchantments.clear();
        favoriteAttributes.clear();
        history.clear();
        templates.clear();
        loadAll();
    }

    /**
     * Reset all Item Editor data (presets, favorites, history, custom templates).
     * Called during a full player reset to return to first-time experience.
     */
    public void resetAll() {
        DevMod.LOGGER.info("[ItemEditor] Resetting all Item Editor data...");

        // Clear in-memory data
        presets.clear();
        favoriteEnchantments.clear();
        favoriteAttributes.clear();
        history.clear();
        templates.clear();

        // Delete data files
        try {
            Path dataDir = getDataDirectory();
            deleteIfExists(dataDir.resolve("presets.json"));
            deleteIfExists(dataDir.resolve("favorites.json"));
            deleteIfExists(dataDir.resolve("history.json"));
            deleteIfExists(dataDir.resolve("templates.json"));

            // Delete exports directory contents
            Path exportsDir = dataDir.resolve("exports");
            if (Files.exists(exportsDir)) {
                Files.list(exportsDir).forEach(file -> {
                    try {
                        Files.deleteIfExists(file);
                    } catch (IOException e) {
                        DevMod.LOGGER.warn("[ItemEditor] Could not delete export file: {}", file.getFileName());
                    }
                });
            }

            DevMod.LOGGER.info("[ItemEditor] All data reset successfully");
        } catch (Exception e) {
            DevMod.LOGGER.error("[ItemEditor] Error during reset: {}", e.getMessage());
        }

        // Reinitialize with defaults
        initDefaultTemplates();
        initialized = true;
    }

    private void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            DevMod.LOGGER.warn("[ItemEditor] Could not delete file: {}", path.getFileName());
        }
    }
}
