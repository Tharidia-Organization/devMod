package com.devmod.config.gamedesign;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.devmod.util.ConfigPaths;

public class GameDesignConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameDesignConfigManager.class);

    public static final GameDesignConfigManager INSTANCE = new GameDesignConfigManager();

    private static final String CONFIG_FILENAME = "game_design.json";
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .create();

    // Global config (defaults)
    @Nonnull
    private volatile GameDesignConfig globalConfig = new GameDesignConfig();

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
        return Objects.requireNonNull(globalConfig, "globalConfig");
    }

    /**
     * Get effective config for a specific instance (with overrides applied).
     */
    @Nonnull
    public GameDesignConfig getEffectiveConfig(@Nullable UUID instanceId) {
        GameDesignConfig baseConfig = Objects.requireNonNull(globalConfig, "globalConfig");
        if (instanceId == null) {
            return baseConfig;
        }

        InstanceOverride override = instanceOverrides.get(instanceId);
        if (override == null) {
            return baseConfig;
        }

        // Apply overrides to a copy of global config
        return Objects.requireNonNull(override.applyTo(baseConfig.copy()), "effectiveConfig");
    }

    /**
     * Update global configuration.
     */
    public void setGlobalConfig(@Nonnull GameDesignConfig config) {
        this.globalConfig = Objects.requireNonNull(config, "config");
        markDirty();
        notifyListeners();
    }

    // ========== Instance Overrides ==========

    /**
     * Set overrides for a specific instance.
     */
    public void setInstanceOverride(UUID instanceId, InstanceOverride override) {
        if (instanceId == null || override == null) {
            LOGGER.warn("[GameDesignConfig] Ignoring null override for instance {}", instanceId);
            return;
        }
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
                LOGGER.info("[GameDesignConfig] Loaded configuration (version {})", loaded.getVersion());
            } else {
                LOGGER.warn("[GameDesignConfig] Config file parsed to null, keeping defaults");
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
            Path parent = configPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String json = GSON.toJson(getGlobalConfig());
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
        GameDesignConfig current = getGlobalConfig();
        for (Consumer<GameDesignConfig> listener : List.copyOf(changeListeners)) {
            try {
                listener.accept(current);
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
        return getEffectiveConfig(instanceId).getResonance();
    }

    /**
     * Get contracts config for an instance.
     */
    public GameDesignConfig.ContractsConfig getContractsConfig(@Nullable UUID instanceId) {
        return getEffectiveConfig(instanceId).getContracts();
    }

    /**
     * Get signature weapons config.
     */
    public GameDesignConfig.SignatureWeaponsConfig getSignatureWeaponsConfig() {
        return getGlobalConfig().getSignatureWeapons();
    }

    /**
     * Get signature weapons config for an instance.
     */
    public GameDesignConfig.SignatureWeaponsConfig getSignatureWeaponsConfig(@Nullable UUID instanceId) {
        return getEffectiveConfig(instanceId).getSignatureWeapons();
    }

    /**
     * Get nemesis config for an instance.
     */
    public GameDesignConfig.NemesisConfig getNemesisConfig(@Nullable UUID instanceId) {
        return getEffectiveConfig(instanceId).getNemesis();
    }

    /**
     * Get tide config.
     */
    public GameDesignConfig.TideConfig getTideConfig() {
        return getGlobalConfig().getTide();
    }

    /**
     * Get tide config for an instance.
     */
    public GameDesignConfig.TideConfig getTideConfig(@Nullable UUID instanceId) {
        return getEffectiveConfig(instanceId).getTide();
    }

    // ========== Quick Enabled Checks ==========

    public boolean isResonanceEnabled(@Nullable UUID instanceId) {
        return getResonanceConfig(instanceId).enabled;
    }

    public boolean isContractsEnabled(@Nullable UUID instanceId) {
        return getContractsConfig(instanceId).enabled;
    }

    public boolean isSignatureWeaponsEnabled() {
        return getGlobalConfig().getSignatureWeapons().enabled;
    }

    public boolean isNemesisEnabled(@Nullable UUID instanceId) {
        return getNemesisConfig(instanceId).enabled;
    }

    public boolean isTideEnabled() {
        return getGlobalConfig().getTide().enabled;
    }
}
