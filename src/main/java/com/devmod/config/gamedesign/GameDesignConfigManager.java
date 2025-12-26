package com.devmod.config.gamedesign;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.util.ConfigPaths;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Manages Game Design configuration with:
 * - Global defaults (persisted to JSON)
 * - Per-instance overrides (runtime only)
 * - Hot-reload support
 * - Change listeners for live updates
 */
public class GameDesignConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameDesignConfigManager.class);

    public static final GameDesignConfigManager INSTANCE = new GameDesignConfigManager();

    private static final String CONFIG_FILENAME = "game_design.json";
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .create();

    // Global config (defaults)
    private GameDesignConfig globalConfig = new GameDesignConfig();

    // Per-instance overrides (questId/arenaId -> partial config)
    private final Map<UUID, InstanceOverride> instanceOverrides = new ConcurrentHashMap<>();

    // Change listeners
    private final List<Consumer<GameDesignConfig>> changeListeners = new ArrayList<>();

    // Dirty flag for lazy save
    private boolean dirty = false;

    private GameDesignConfigManager() {}

    // ========== Global Config ==========

    /**
     * Get the global configuration.
     */
    @Nonnull
    public GameDesignConfig getGlobalConfig() {
        return globalConfig;
    }

    /**
     * Get effective config for a specific instance (with overrides applied).
     */
    @Nonnull
    public GameDesignConfig getEffectiveConfig(@Nullable UUID instanceId) {
        if (instanceId == null) {
            return globalConfig;
        }

        InstanceOverride override = instanceOverrides.get(instanceId);
        if (override == null) {
            return globalConfig;
        }

        // Apply overrides to a copy of global config
        return override.applyTo(globalConfig.copy());
    }

    /**
     * Update global configuration.
     */
    public void setGlobalConfig(GameDesignConfig config) {
        this.globalConfig = config;
        markDirty();
        notifyListeners();
    }

    // ========== Instance Overrides ==========

    /**
     * Set overrides for a specific instance.
     */
    public void setInstanceOverride(UUID instanceId, InstanceOverride override) {
        instanceOverrides.put(instanceId, override);
        LOGGER.debug("[GameDesignConfig] Override set for instance {}", instanceId);
    }

    /**
     * Remove overrides for an instance.
     */
    public void removeInstanceOverride(UUID instanceId) {
        instanceOverrides.remove(instanceId);
    }

    /**
     * Get override for an instance (if any).
     */
    @Nullable
    public InstanceOverride getInstanceOverride(UUID instanceId) {
        return instanceOverrides.get(instanceId);
    }

    /**
     * Clear all instance overrides.
     */
    public void clearInstanceOverrides() {
        instanceOverrides.clear();
    }

    // ========== Persistence ==========

    /**
     * Load configuration from disk.
     */
    public void load() {
        Path configPath = getConfigPath();
        if (!Files.exists(configPath)) {
            LOGGER.info("[GameDesignConfig] No config file found, using defaults");
            save(); // Create default file
            return;
        }

        try {
            String json = Files.readString(configPath);
            GameDesignConfig loaded = GSON.fromJson(json, GameDesignConfig.class);
            if (loaded != null) {
                this.globalConfig = loaded;
                LOGGER.info("[GameDesignConfig] Loaded configuration (version {})", loaded.version);
            }
        } catch (IOException e) {
            LOGGER.error("[GameDesignConfig] Failed to load config", e);
        }
    }

    /**
     * Save configuration to disk.
     */
    public void save() {
        Path configPath = getConfigPath();
        try {
            Files.createDirectories(configPath.getParent());
            String json = GSON.toJson(globalConfig);
            Files.writeString(configPath, json);
            dirty = false;
            LOGGER.debug("[GameDesignConfig] Saved configuration");
        } catch (IOException e) {
            LOGGER.error("[GameDesignConfig] Failed to save config", e);
        }
    }

    /**
     * Save if dirty.
     */
    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    /**
     * Mark config as dirty (needs saving).
     */
    public void markDirty() {
        this.dirty = true;
    }

    /**
     * Reload configuration from disk (hot-reload).
     */
    public void reload() {
        load();
        notifyListeners();
        LOGGER.info("[GameDesignConfig] Configuration reloaded");
    }

    /**
     * Reset to defaults.
     */
    public void resetToDefaults() {
        this.globalConfig = new GameDesignConfig();
        markDirty();
        notifyListeners();
        LOGGER.info("[GameDesignConfig] Reset to defaults");
    }

    private Path getConfigPath() {
        return ConfigPaths.getConfigDir().resolve(CONFIG_FILENAME);
    }

    // ========== Change Listeners ==========

    /**
     * Add a listener for config changes.
     */
    public void addChangeListener(Consumer<GameDesignConfig> listener) {
        changeListeners.add(listener);
    }

    /**
     * Remove a change listener.
     */
    public void removeChangeListener(Consumer<GameDesignConfig> listener) {
        changeListeners.remove(listener);
    }

    private void notifyListeners() {
        for (Consumer<GameDesignConfig> listener : changeListeners) {
            try {
                listener.accept(globalConfig);
            } catch (Exception e) {
                LOGGER.error("[GameDesignConfig] Error notifying listener", e);
            }
        }
    }

    // ========== Convenience Getters ==========

    /**
     * Get resonance config for an instance.
     */
    public GameDesignConfig.ResonanceConfig getResonanceConfig(@Nullable UUID instanceId) {
        return getEffectiveConfig(instanceId).resonance;
    }

    /**
     * Get contracts config for an instance.
     */
    public GameDesignConfig.ContractsConfig getContractsConfig(@Nullable UUID instanceId) {
        return getEffectiveConfig(instanceId).contracts;
    }

    /**
     * Get signature weapons config.
     */
    public GameDesignConfig.SignatureWeaponsConfig getSignatureWeaponsConfig() {
        return globalConfig.signatureWeapons;
    }

    /**
     * Get signature weapons config for an instance.
     */
    public GameDesignConfig.SignatureWeaponsConfig getSignatureWeaponsConfig(@Nullable UUID instanceId) {
        return getEffectiveConfig(instanceId).signatureWeapons;
    }

    /**
     * Get nemesis config for an instance.
     */
    public GameDesignConfig.NemesisConfig getNemesisConfig(@Nullable UUID instanceId) {
        return getEffectiveConfig(instanceId).nemesis;
    }

    /**
     * Get tide config.
     */
    public GameDesignConfig.TideConfig getTideConfig() {
        return globalConfig.tide;
    }

    /**
     * Get tide config for an instance.
     */
    public GameDesignConfig.TideConfig getTideConfig(@Nullable UUID instanceId) {
        return getEffectiveConfig(instanceId).tide;
    }

    // ========== Quick Enabled Checks ==========

    public boolean isResonanceEnabled(@Nullable UUID instanceId) {
        return getResonanceConfig(instanceId).enabled;
    }

    public boolean isContractsEnabled(@Nullable UUID instanceId) {
        return getContractsConfig(instanceId).enabled;
    }

    public boolean isSignatureWeaponsEnabled() {
        return globalConfig.signatureWeapons.enabled;
    }

    public boolean isNemesisEnabled(@Nullable UUID instanceId) {
        return getNemesisConfig(instanceId).enabled;
    }

    public boolean isTideEnabled() {
        return globalConfig.tide.enabled;
    }
}
