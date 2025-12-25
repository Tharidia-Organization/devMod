package com.devmod.recipe;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Singleton manager for custom recipe storage.
 * Handles persistence, CRUD operations, and datapack export.
 */
public final class RecipeConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecipeConfigManager.class);
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    private static final String INDEX_FILE = "index.json";
    private static final int FORMAT_VERSION = 1;

    // ═══════════════════════════════════════════════════════════════
    // STORAGE
    // ═══════════════════════════════════════════════════════════════

    // Server-side authoritative storage
    private static final Map<ResourceLocation, RecipeData> serverRecipes = new ConcurrentHashMap<>();

    // Client-side mirror (populated from server sync)
    private static final Map<ResourceLocation, RecipeData> clientRecipes = new ConcurrentHashMap<>();

    // Read-write lock for atomic compound operations (put + save, etc.)
    private static final ReadWriteLock serverLock = new ReentrantReadWriteLock();
    private static final ReadWriteLock clientLock = new ReentrantReadWriteLock();

    // Configuration
    private static volatile Path serverConfigPath = null;
    private static volatile boolean initialized = false;

    // Lock for initialization to prevent race conditions
    private static final Object initLock = new Object();

    private RecipeConfigManager() {}

    // ═══════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Initialize server-side storage.
     * Called during server setup. Thread-safe with double-checked locking.
     *
     * @param recipesDir The directory where recipes are stored (e.g., config/devmod/recipes/)
     */
    public static void initializeServer(Path recipesDir) {
        if (recipesDir == null) {
            LOGGER.error("[RecipeConfig] Cannot initialize - recipes directory is null");
            return;
        }

        if (initialized) {
            LOGGER.debug("[RecipeConfig] Already initialized, skipping");
            return;
        }

        synchronized (initLock) {
            // Double-check after acquiring lock
            if (initialized) {
                LOGGER.debug("[RecipeConfig] Already initialized (after lock), skipping");
                return;
            }

            // Use the provided path directly (no more nested resolve)
            serverConfigPath = recipesDir;
            try {
                Files.createDirectories(serverConfigPath);
                loadServerRecipes();
                initialized = true;
                LOGGER.info("[RecipeConfig] Server initialized with {} custom recipes at {}",
                    serverRecipes.size(), serverConfigPath);
            } catch (Exception e) {
                LOGGER.error("[RecipeConfig] Failed to initialize server storage at {}", recipesDir, e);
            }
        }
    }

    /**
     * Reset the manager state. Call when server stops to allow re-initialization.
     */
    public static void reset() {
        synchronized (initLock) {
            serverLock.writeLock().lock();
            try {
                serverRecipes.clear();
                serverConfigPath = null;
                initialized = false;
                LOGGER.info("[RecipeConfig] Manager state reset");
            } finally {
                serverLock.writeLock().unlock();
            }
        }

        clientLock.writeLock().lock();
        try {
            clientRecipes.clear();
        } finally {
            clientLock.writeLock().unlock();
        }
    }

    /**
     * Initialize client-side storage.
     * Called during client setup.
     */
    public static void initializeClient() {
        clientRecipes.clear();
        LOGGER.debug("[RecipeConfig] Client storage initialized");
    }

    /**
     * Check if manager is initialized.
     */
    public static boolean isInitialized() {
        return initialized;
    }

    // ═══════════════════════════════════════════════════════════════
    // SERVER CRUD OPERATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Add or update a recipe on the server (atomic operation).
     */
    public static void addRecipe(RecipeData recipe) {
        Objects.requireNonNull(recipe, "Recipe cannot be null");
        Objects.requireNonNull(recipe.id(), "Recipe ID cannot be null");

        serverLock.writeLock().lock();
        try {
            serverRecipes.put(recipe.id(), recipe);
            saveServerRecipesUnsafe();
            LOGGER.debug("[RecipeConfig] Added/updated recipe: {}", recipe.id());
        } finally {
            serverLock.writeLock().unlock();
        }
    }

    /**
     * Add or update a recipe only if it passes validation (atomic operation).
     * @return ValidationResult indicating success or failure
     */
    public static RecipeValidator.ValidationResult addRecipeIfValid(RecipeData recipe) {
        Objects.requireNonNull(recipe, "Recipe cannot be null");
        Objects.requireNonNull(recipe.id(), "Recipe ID cannot be null");

        var result = RecipeValidator.validate(recipe);
        if (result.valid()) {
            serverLock.writeLock().lock();
            try {
                serverRecipes.put(recipe.id(), recipe);
                saveServerRecipesUnsafe();
                LOGGER.debug("[RecipeConfig] Added/updated validated recipe: {}", recipe.id());
            } finally {
                serverLock.writeLock().unlock();
            }
        }
        return result;
    }

    /**
     * Remove a recipe from the server (atomic operation).
     * @return The removed recipe, or empty if not found
     */
    public static Optional<RecipeData> removeRecipe(ResourceLocation id) {
        Objects.requireNonNull(id, "Recipe ID cannot be null");

        serverLock.writeLock().lock();
        try {
            RecipeData removed = serverRecipes.remove(id);
            if (removed != null) {
                saveServerRecipesUnsafe();
                LOGGER.debug("[RecipeConfig] Removed recipe: {}", id);
            }
            return Optional.ofNullable(removed);
        } finally {
            serverLock.writeLock().unlock();
        }
    }

    /**
     * Atomically update a recipe if it exists.
     * @param id The recipe ID
     * @param updater Function to update the recipe (receives current, returns new)
     * @return The updated recipe, or empty if not found
     */
    public static Optional<RecipeData> updateRecipe(ResourceLocation id, Function<RecipeData, RecipeData> updater) {
        Objects.requireNonNull(id, "Recipe ID cannot be null");
        Objects.requireNonNull(updater, "Updater function cannot be null");

        serverLock.writeLock().lock();
        try {
            RecipeData existing = serverRecipes.get(id);
            if (existing == null) {
                return Optional.empty();
            }

            RecipeData updated = updater.apply(existing);
            if (updated != null) {
                serverRecipes.put(id, updated);
                saveServerRecipesUnsafe();
                LOGGER.debug("[RecipeConfig] Updated recipe: {}", id);
            }
            return Optional.ofNullable(updated);
        } finally {
            serverLock.writeLock().unlock();
        }
    }

    /**
     * Get a recipe by ID (read operation).
     */
    public static Optional<RecipeData> getRecipe(ResourceLocation id) {
        serverLock.readLock().lock();
        try {
            return Optional.ofNullable(serverRecipes.get(id));
        } finally {
            serverLock.readLock().unlock();
        }
    }

    /**
     * Get all custom recipes (snapshot copy for thread safety).
     */
    public static List<RecipeData> getAllCustomRecipes() {
        serverLock.readLock().lock();
        try {
            return new ArrayList<>(serverRecipes.values());
        } finally {
            serverLock.readLock().unlock();
        }
    }

    /**
     * Get recipes by type (snapshot copy for thread safety).
     */

    public static <T extends RecipeData> List<T> getRecipesByType(Class<T> type) {
        serverLock.readLock().lock();
        try {
            return serverRecipes.values().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
        } finally {
            serverLock.readLock().unlock();
        }
    }

    /**
     * Check if a recipe exists.
     */
    public static boolean hasRecipe(ResourceLocation id) {
        serverLock.readLock().lock();
        try {
            return serverRecipes.containsKey(id);
        } finally {
            serverLock.readLock().unlock();
        }
    }

    /**
     * Get the count of custom recipes.
     */
    public static int getRecipeCount() {
        serverLock.readLock().lock();
        try {
            return serverRecipes.size();
        } finally {
            serverLock.readLock().unlock();
        }
    }

    /**
     * Clear all custom recipes (atomic operation).
     */
    public static void clearAllRecipes() {
        serverLock.writeLock().lock();
        try {
            serverRecipes.clear();
            saveServerRecipesUnsafe();
            LOGGER.info("[RecipeConfig] Cleared all custom recipes");
        } finally {
            serverLock.writeLock().unlock();
        }
    }

    /**
     * Batch add multiple recipes atomically.
     * @return Number of recipes successfully added
     */
    public static int addRecipesBatch(Collection<RecipeData> recipes) {
        if (recipes == null || recipes.isEmpty()) return 0;

        serverLock.writeLock().lock();
        try {
            int added = 0;
            for (RecipeData recipe : recipes) {
                if (recipe != null && recipe.id() != null) {
                    serverRecipes.put(recipe.id(), recipe);
                    added++;
                }
            }
            if (added > 0) {
                saveServerRecipesUnsafe();
                LOGGER.debug("[RecipeConfig] Batch added {} recipes", added);
            }
            return added;
        } finally {
            serverLock.writeLock().unlock();
        }
    }

    /**
     * Batch remove multiple recipes atomically.
     * @return Number of recipes successfully removed
     */
    public static int removeRecipesBatch(Collection<ResourceLocation> ids) {
        if (ids == null || ids.isEmpty()) return 0;

        serverLock.writeLock().lock();
        try {
            int removed = 0;
            for (ResourceLocation id : ids) {
                if (serverRecipes.remove(id) != null) {
                    removed++;
                }
            }
            if (removed > 0) {
                saveServerRecipesUnsafe();
                LOGGER.debug("[RecipeConfig] Batch removed {} recipes", removed);
            }
            return removed;
        } finally {
            serverLock.writeLock().unlock();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // CLIENT OPERATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Add recipe on client (from server sync).
     * @throws NullPointerException if recipe or recipe.id() is null
     */
    public static void addRecipeClientOnly(RecipeData recipe) {
        Objects.requireNonNull(recipe, "Recipe cannot be null");
        Objects.requireNonNull(recipe.id(), "Recipe ID cannot be null");

        clientLock.writeLock().lock();
        try {
            clientRecipes.put(recipe.id(), recipe);
        } finally {
            clientLock.writeLock().unlock();
        }
    }

    /**
     * Batch sync all recipes on client (replaces all existing).
     */
    public static void syncAllClientRecipes(Collection<RecipeData> recipes) {
        clientLock.writeLock().lock();
        try {
            clientRecipes.clear();
            for (RecipeData recipe : recipes) {
                if (recipe != null && recipe.id() != null) {
                    clientRecipes.put(recipe.id(), recipe);
                }
            }
            LOGGER.debug("[RecipeConfig] Client synced {} recipes", clientRecipes.size());
        } finally {
            clientLock.writeLock().unlock();
        }
    }

    /**
     * Remove recipe on client (from server sync).
     * @throws NullPointerException if id is null
     */
    public static void removeRecipeClientOnly(ResourceLocation id) {
        Objects.requireNonNull(id, "Recipe ID cannot be null");

        clientLock.writeLock().lock();
        try {
            clientRecipes.remove(id);
        } finally {
            clientLock.writeLock().unlock();
        }
    }

    /**
     * Clear client recipes (on disconnect).
     */
    public static void clearClientRecipes() {
        clientLock.writeLock().lock();
        try {
            clientRecipes.clear();
        } finally {
            clientLock.writeLock().unlock();
        }
    }

    /**
     * Get all client-side recipes (snapshot copy for thread safety).
     */
    public static List<RecipeData> getClientRecipes() {
        clientLock.readLock().lock();
        try {
            return new ArrayList<>(clientRecipes.values());
        } finally {
            clientLock.readLock().unlock();
        }
    }

    /**
     * Get client recipe by ID.
     */
    public static Optional<RecipeData> getClientRecipe(ResourceLocation id) {
        clientLock.readLock().lock();
        try {
            return Optional.ofNullable(clientRecipes.get(id));
        } finally {
            clientLock.readLock().unlock();
        }
    }

    /**
     * Check if client has a recipe.
     */
    public static boolean hasClientRecipe(ResourceLocation id) {
        clientLock.readLock().lock();
        try {
            return clientRecipes.containsKey(id);
        } finally {
            clientLock.readLock().unlock();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // PERSISTENCE
    // ═══════════════════════════════════════════════════════════════

    private static void loadServerRecipes() {
        if (serverConfigPath == null) return;

        Path indexFile = serverConfigPath.resolve(INDEX_FILE);
        if (!Files.exists(indexFile)) {
            LOGGER.info("[RecipeConfig] No index file found, starting fresh");
            return;
        }

        try {
            String content = Files.readString(indexFile, StandardCharsets.UTF_8);
            JsonObject index = Objects.requireNonNull(GSON.fromJson(content, JsonObject.class), "index");

            // Version check
            int version = GsonHelper.getAsInt(index, "version", 1);
            if (version > FORMAT_VERSION) {
                LOGGER.warn("[RecipeConfig] Index file version {} is newer than supported {}", version, FORMAT_VERSION);
            }

            JsonArray recipesArray = index.getAsJsonArray("recipes");
            if (recipesArray == null) return;

            int loaded = 0;
            int failed = 0;

            for (JsonElement elem : recipesArray) {
                try {
                    if (!elem.isJsonObject()) {
                        failed++;
                        continue;
                    }
                    JsonObject recipeJson = Objects.requireNonNull(elem.getAsJsonObject(), "recipeJson");
                    String idStr = GsonHelper.getAsString(recipeJson, "id");
                    ResourceLocation id = ResourceLocation.parse(Objects.requireNonNull(idStr, "recipeId"));

                    RecipeData recipe = RecipeData.fromJson(id, recipeJson);
                    serverRecipes.put(id, recipe);
                    loaded++;
                } catch (Exception e) {
                    LOGGER.error("[RecipeConfig] Failed to load recipe entry", e);
                    failed++;
                }
            }

            LOGGER.info("[RecipeConfig] Loaded {} recipes ({} failed)", loaded, failed);
        } catch (Exception e) {
            LOGGER.error("[RecipeConfig] Failed to load index file", e);
        }
    }

    /**
     * Internal save method - MUST be called with serverLock.writeLock() held.
     */
    private static void saveServerRecipesUnsafe() {
        if (serverConfigPath == null) {
            LOGGER.warn("[RecipeConfig] Cannot save - not initialized");
            return;
        }

        try {
            JsonObject index = new JsonObject();
            index.addProperty("version", FORMAT_VERSION);
            index.addProperty("savedAt", System.currentTimeMillis());
            index.addProperty("count", serverRecipes.size());

            JsonArray recipesArray = new JsonArray();
            for (RecipeData recipe : serverRecipes.values()) {
                try {
                    JsonObject recipeJson = recipe.toJson();
                    recipeJson.addProperty("id", recipe.id().toString());
                    recipesArray.add(recipeJson);
                } catch (Exception e) {
                    LOGGER.error("[RecipeConfig] Failed to serialize recipe: {}", recipe.id(), e);
                }
            }
            index.add("recipes", recipesArray);

            Path indexFile = serverConfigPath.resolve(INDEX_FILE);
            Files.writeString(indexFile, GSON.toJson(index), StandardCharsets.UTF_8);

            LOGGER.debug("[RecipeConfig] Saved {} recipes", serverRecipes.size());
        } catch (Exception e) {
            LOGGER.error("[RecipeConfig] Failed to save recipes", e);
        }
    }

    /**
     * Public save method with proper locking.
     * Call this if you need to trigger a manual save.
     */
    public static void saveServerRecipes() {
        serverLock.writeLock().lock();
        try {
            saveServerRecipesUnsafe();
        } finally {
            serverLock.writeLock().unlock();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // DATAPACK EXPORT
    // ═══════════════════════════════════════════════════════════════

    /**
     * Export all custom recipes to a datapack (thread-safe).
     *
     * @param gameDir The game directory (for datapacks folder)
     * @param packName The datapack name
     * @return Number of recipes exported
     */
    public static int exportToDatapack(Path gameDir, String packName) {
        if (gameDir == null) {
            LOGGER.error("[RecipeConfig] Cannot export - game directory is null");
            return 0;
        }

        // Take a snapshot of recipes while holding the lock
        List<RecipeData> snapshot;
        serverLock.readLock().lock();
        try {
            if (serverRecipes.isEmpty()) {
                LOGGER.info("[RecipeConfig] No recipes to export");
                return 0;
            }
            snapshot = new ArrayList<>(serverRecipes.values());
        } finally {
            serverLock.readLock().unlock();
        }

        // Export without holding the lock (IO operation)
        Path packDir = gameDir.resolve("datapacks").resolve(packName);
        Path recipesDir = packDir.resolve("data").resolve("devmod").resolve("recipe");

        try {
            Files.createDirectories(recipesDir);
            writePackMeta(packDir, packName);

            int count = 0;
            for (RecipeData recipe : snapshot) {
                try {
                    String filename = recipe.id().getPath().replace("/", "_") + ".json";
                    Path recipeFile = recipesDir.resolve(filename);

                    JsonObject json = recipe.toJson();
                    Files.writeString(recipeFile, GSON.toJson(json), StandardCharsets.UTF_8);
                    count++;
                } catch (Exception e) {
                    LOGGER.error("[RecipeConfig] Failed to export recipe: {}", recipe.id(), e);
                }
            }

            LOGGER.info("[RecipeConfig] Exported {} recipes to datapack '{}'", count, packName);
            return count;
        } catch (Exception e) {
            LOGGER.error("[RecipeConfig] Failed to export datapack", e);
            return 0;
        }
    }

    private static void writePackMeta(Path packDir, String packName) throws IOException {
        JsonObject meta = new JsonObject();
        JsonObject pack = new JsonObject();
        pack.addProperty("description", "DevMod Custom Recipes - " + packName);
        pack.addProperty("pack_format", 48); // 1.21.x
        meta.add("pack", pack);

        Path metaFile = packDir.resolve("pack.mcmeta");
        Files.writeString(metaFile, GSON.toJson(meta), StandardCharsets.UTF_8);
    }

    /**
     * Export recipe priorities to datapack.
     */
    public static void exportPriorities(Path packDir, Map<ResourceLocation, Integer> priorities) {
        if (priorities == null || priorities.isEmpty()) return;

        try {
            Path prioritiesDir = packDir.resolve("data").resolve("devmod");
            Files.createDirectories(prioritiesDir);

            JsonObject json = new JsonObject();
            JsonObject values = new JsonObject();

            for (Map.Entry<ResourceLocation, Integer> entry : priorities.entrySet()) {
                values.addProperty(entry.getKey().toString(), entry.getValue());
            }

            json.add("values", values);

            Path file = prioritiesDir.resolve("recipe_priorities.json");
            Files.writeString(file, GSON.toJson(json), StandardCharsets.UTF_8);

            LOGGER.debug("[RecipeConfig] Exported {} recipe priorities", priorities.size());
        } catch (Exception e) {
            LOGGER.error("[RecipeConfig] Failed to export priorities", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // IMPORT
    // ═══════════════════════════════════════════════════════════════

    /**
     * Import recipes from a JSON file (thread-safe).
     *
     * @param jsonFile Path to the JSON file
     * @return Number of recipes imported
     */
    public static int importFromFile(Path jsonFile) {
        if (!Files.exists(jsonFile)) {
            LOGGER.error("[RecipeConfig] Import file does not exist: {}", jsonFile);
            return 0;
        }

        // Parse file without holding the lock (IO operation)
        List<RecipeData> parsedRecipes = new ArrayList<>();
        try {
            String content = Files.readString(jsonFile, StandardCharsets.UTF_8);
            JsonElement root = GSON.fromJson(content, JsonElement.class);
            if (root == null) {
                LOGGER.error("[RecipeConfig] Import file is empty or invalid: {}", jsonFile);
                return 0;
            }

            if (root.isJsonArray()) {
                // Array of recipes
                for (JsonElement elem : root.getAsJsonArray()) {
                    if (elem.isJsonObject()) {
                        RecipeData recipe = parseSingleRecipe(elem.getAsJsonObject());
                        if (recipe != null) {
                            parsedRecipes.add(recipe);
                        }
                    }
                }
            } else if (root.isJsonObject()) {
                // Single recipe or index format
                JsonObject obj = root.getAsJsonObject();
                if (obj.has("recipes")) {
                    // Index format
                    for (JsonElement elem : obj.getAsJsonArray("recipes")) {
                        if (elem.isJsonObject()) {
                            RecipeData recipe = parseSingleRecipe(elem.getAsJsonObject());
                            if (recipe != null) {
                                parsedRecipes.add(recipe);
                            }
                        }
                    }
                } else {
                    // Single recipe
                    RecipeData recipe = parseSingleRecipe(obj);
                    if (recipe != null) {
                        parsedRecipes.add(recipe);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("[RecipeConfig] Failed to parse import file", e);
            return 0;
        }

        // Add all parsed recipes atomically
        if (!parsedRecipes.isEmpty()) {
            int count = addRecipesBatch(parsedRecipes);
            LOGGER.info("[RecipeConfig] Imported {} recipes from {}", count, jsonFile);
            return count;
        }

        return 0;
    }

    /**
     * Parse a single recipe from JSON without adding to storage.
     * @return The parsed recipe, or null if invalid
     */
    private static RecipeData parseSingleRecipe(JsonObject json) {
        try {
            JsonObject root = Objects.requireNonNull(json, "json");
            String idStr = GsonHelper.getAsString(root, "id", null);
            if (idStr == null) {
                // Generate ID from type
                String type = Objects.requireNonNull(GsonHelper.getAsString(root, "type", "unknown"), "type");
                idStr = "devmod:imported_" + type.replace(":", "_") + "_" + System.currentTimeMillis();
            }

            ResourceLocation id = ResourceLocation.parse(Objects.requireNonNull(idStr, "recipeId"));
            RecipeData recipe = RecipeData.fromJson(id, root);

            // Validate before adding
            var result = RecipeValidator.validate(recipe);
            if (!result.valid()) {
                LOGGER.warn("[RecipeConfig] Skipping invalid recipe {}: {}", id, result.getFirstError());
                return null;
            }

            return recipe;
        } catch (Exception e) {
            LOGGER.error("[RecipeConfig] Failed to parse recipe", e);
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // VALIDATION HELPER
    // ═══════════════════════════════════════════════════════════════

    /**
     * Validate a recipe.
     */
    public static RecipeValidator.ValidationResult validate(RecipeData recipe) {
        return RecipeValidator.validate(recipe);
    }
}
