package com.devmod.notification.persistence;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import com.devmod.notification.NotificationCategory;
import com.devmod.notification.NotificationPriority;
import com.devmod.notification.NotificationRouter.NotificationPreferences;
import com.devmod.notification.NotificationRouter.NotificationPreferences.CategoryPreference;
import com.devmod.telemetry.duckdb.DuckDBConnectionManager;
/**
 * DuckDB-based persistence for notification preferences.
 *
 * <p>Stores per-player notification preferences including:
 * <ul>
 *   <li>Global mute settings</li>
 *   <li>Minimum priority thresholds</li>
 *   <li>Per-category preferences</li>
 *   <li>Sound volume settings</li>
 * </ul>
 */
public class NotificationPreferencesRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationPreferencesRepository.class);
    private static final Gson GSON = new Gson();

    public static final NotificationPreferencesRepository INSTANCE = new NotificationPreferencesRepository();

    @Nullable
    private DuckDBConnectionManager connectionManager;
    @Nullable
    private ExecutorService executor;
    private volatile boolean initialized = false;

    // In-memory cache for fast access
    private final Map<UUID, NotificationPreferences> cache = new ConcurrentHashMap<>();

    private NotificationPreferencesRepository() {}

    // ============================================================================
    // INITIALIZATION
    // ============================================================================

    /**
     * Initialize the repository with the given database path.
     */
    public CompletableFuture<Void> initialize(Path dbPath) {
        if (initialized) {
            return CompletableFuture.completedFuture(null);
        }

        this.connectionManager = new DuckDBConnectionManager(dbPath);
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "NotificationPrefsDB-Worker");
            t.setDaemon(true);
            return t;
        });

        return CompletableFuture.runAsync(() -> {
            try {
                createSchema();
                initialized = true;
                LOGGER.info("[NotificationPrefs] Repository initialized successfully");
            } catch (Exception e) {
                LOGGER.error("[NotificationPrefs] Failed to initialize repository", e);
                throw new RuntimeException("Failed to initialize notification preferences", e);
            }
        }, executor);
    }

    /**
     * Shutdown the repository.
     */
    public void shutdown() {
        if (!initialized) return;

        initialized = false;
        cache.clear();
        if (executor != null) {
            executor.shutdown();
        }
        if (connectionManager != null) {
            connectionManager.shutdown();
        }
        LOGGER.info("[NotificationPrefs] Repository shutdown complete");
    }

    private void createSchema() throws SQLException {
        if (connectionManager == null) return;

        String createTableSql = """
            CREATE TABLE IF NOT EXISTS notification_preferences (
                player_uuid VARCHAR PRIMARY KEY,
                global_mute BOOLEAN DEFAULT FALSE,
                min_overlay_priority INTEGER DEFAULT 0,
                prefer_chat BOOLEAN DEFAULT FALSE,
                master_volume FLOAT DEFAULT 1.0,
                category_prefs_json VARCHAR
            )
            """;

        DuckDBConnectionManager cm = connectionManager;
        if (cm == null) {
            throw new SQLException("ConnectionManager not initialized");
        }
        Connection conn = cm.getConnectionUnchecked();
        try (PreparedStatement stmt = conn.prepareStatement(createTableSql)) {
            stmt.execute();
            LOGGER.debug("[NotificationPrefs] Schema created/verified");
        }
    }

    // ============================================================================
    // PREFERENCE OPERATIONS
    // ============================================================================

    /**
     * Get preferences for a player.
     * Uses in-memory cache for performance.
     */
    public NotificationPreferences getPreferences(UUID playerUuid) {
        // Check cache first
        NotificationPreferences cached = cache.get(playerUuid);
        if (cached != null) {
            return cached;
        }

        // Create default and load async
        NotificationPreferences defaults = new NotificationPreferences(playerUuid);
        cache.put(playerUuid, defaults);

        // Trigger async load
        loadPreferencesAsync(playerUuid);

        return defaults;
    }

    /**
     * Load preferences from database asynchronously.
     * Fire-and-forget pattern with proper exception handling.
     */
    private void loadPreferencesAsync(UUID playerUuid) {
        loadPreferences(playerUuid).exceptionally(e -> {
            LOGGER.error("[NotificationPrefs] Async load failed for {}: {}", playerUuid, e.getMessage());
            return null;
        });
    }

    /**
     * Load preferences from database and return a future with the result.
     */
    public CompletableFuture<NotificationPreferences> loadPreferences(UUID playerUuid) {
        ExecutorService exec = executor;
        DuckDBConnectionManager cm = connectionManager;
        if (!initialized || exec == null || cm == null) {
            NotificationPreferences defaults = new NotificationPreferences(playerUuid);
            cache.put(playerUuid, defaults);
            return CompletableFuture.completedFuture(defaults);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM notification_preferences WHERE player_uuid = ?";
            NotificationPreferences prefs = new NotificationPreferences(playerUuid);

            try (Connection conn = cm.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        prefs = mapFromResultSet(rs, playerUuid);
                        LOGGER.debug("[NotificationPrefs] Loaded preferences for {}", playerUuid);
                    }
                }
            } catch (SQLException e) {
                LOGGER.warn("[NotificationPrefs] Failed to load preferences: {}", e.getMessage());
            }

            cache.put(playerUuid, prefs);
            return prefs;
        }, exec);
    }

    /**
     * Save preferences for a player.
     */
    public CompletableFuture<Void> savePreferences(NotificationPreferences prefs) {
        ExecutorService exec = executor;
        DuckDBConnectionManager cm = connectionManager;
        if (!initialized || exec == null || cm == null) {
            return CompletableFuture.completedFuture(null);
        }

        // Update cache immediately
        cache.put(prefs.getPlayerUuid(), prefs);

        return CompletableFuture.runAsync(() -> {
            String sql = """
                INSERT INTO notification_preferences
                (player_uuid, global_mute, min_overlay_priority, prefer_chat, master_volume, category_prefs_json)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (player_uuid) DO UPDATE SET
                    global_mute = excluded.global_mute,
                    min_overlay_priority = excluded.min_overlay_priority,
                    prefer_chat = excluded.prefer_chat,
                    master_volume = excluded.master_volume,
                    category_prefs_json = excluded.category_prefs_json
                """;

            try (Connection conn = cm.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, prefs.getPlayerUuid().toString());
                stmt.setBoolean(2, prefs.isGlobalMute());
                stmt.setInt(3, prefs.getMinOverlayPriority().ordinal());
                stmt.setBoolean(4, prefs.prefersChatOverOverlay());
                stmt.setFloat(5, prefs.getMasterVolume());
                stmt.setString(6, serializeCategoryPrefs(prefs));

                stmt.executeUpdate();
                LOGGER.debug("[NotificationPrefs] Saved preferences for {}", prefs.getPlayerUuid());
            } catch (SQLException e) {
                LOGGER.warn("[NotificationPrefs] Failed to save preferences: {}", e.getMessage());
            }
        }, exec);
    }

    /**
     * Update a single category preference.
     */
    public CompletableFuture<Void> updateCategoryPreference(
            UUID playerUuid, NotificationCategory category,
            boolean overlayEnabled, boolean soundEnabled, float soundVolume) {

        NotificationPreferences prefs = getPreferences(playerUuid);
        prefs.setCategoryOverlayEnabled(category, overlayEnabled);
        prefs.setCategorySoundEnabled(category, soundEnabled);
        prefs.setCategorySoundVolume(category, soundVolume);

        return savePreferences(prefs);
    }

    /**
     * Update global mute setting.
     */
    public CompletableFuture<Void> setGlobalMute(UUID playerUuid, boolean muted) {
        NotificationPreferences prefs = getPreferences(playerUuid);
        prefs.setGlobalMute(muted);
        return savePreferences(prefs);
    }

    /**
     * Clear cached preferences for a player.
     */
    public void clearCache(UUID playerUuid) {
        cache.remove(playerUuid);
    }

    // ============================================================================
    // HELPERS
    // ============================================================================

    private NotificationPreferences mapFromResultSet(ResultSet rs, UUID playerUuid) throws SQLException {
        NotificationPreferences prefs = new NotificationPreferences(playerUuid);

        prefs.setGlobalMute(rs.getBoolean("global_mute"));
        prefs.setMinOverlayPriority(NotificationPriority.fromOrdinal(rs.getInt("min_overlay_priority")));
        prefs.setPreferChatOverOverlay(rs.getBoolean("prefer_chat"));
        prefs.setMasterVolume(rs.getFloat("master_volume"));

        String categoryJson = rs.getString("category_prefs_json");
        if (categoryJson != null && !categoryJson.isEmpty()) {
            deserializeCategoryPrefs(prefs, categoryJson);
        }

        return prefs;
    }

    private String serializeCategoryPrefs(NotificationPreferences prefs) {
        Map<String, CategoryPrefDto> dtoMap = new ConcurrentHashMap<>();

        for (NotificationCategory category : NotificationCategory.values()) {
            CategoryPreference pref = prefs.getCategoryPreference(category);
            if (pref != null) {
                dtoMap.put(category.getId(), new CategoryPrefDto(
                        pref.overlayEnabled(),
                        pref.soundEnabled(),
                        pref.soundVolume()
                ));
            }
        }

        return GSON.toJson(dtoMap);
    }

    private void deserializeCategoryPrefs(NotificationPreferences prefs, String json) {
        try {
            Map<String, CategoryPrefDto> dtoMap = GSON.fromJson(json,
                    new TypeToken<Map<String, CategoryPrefDto>>(){}.getType());

            if (dtoMap != null) {
                for (Map.Entry<String, CategoryPrefDto> entry : dtoMap.entrySet()) {
                    NotificationCategory category = NotificationCategory.fromId(entry.getKey());
                    CategoryPrefDto dto = entry.getValue();
                    prefs.setCategoryOverlayEnabled(category, dto.overlayEnabled);
                    prefs.setCategorySoundEnabled(category, dto.soundEnabled);
                    prefs.setCategorySoundVolume(category, dto.soundVolume);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[NotificationPrefs] Failed to deserialize category prefs: {}", e.getMessage());
        }
    }

    public boolean isInitialized() {
        return initialized;
    }

    /**
     * DTO for serializing category preferences.
     */
    private record CategoryPrefDto(boolean overlayEnabled, boolean soundEnabled, float soundVolume) {}
}
