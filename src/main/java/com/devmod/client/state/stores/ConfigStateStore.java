package com.devmod.client.state.stores;

import java.util.Map;

import javax.annotation.Nullable;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.state.BaseStateStore;

/**
 * Client-side store for server config and game mechanics.
 *
 * <p>Tracks:
 * <ul>
 *   <li>Combat mechanics (head multiplier, body parts enabled, etc.)</li>
 *   <li>Server feature flags</li>
 *   <li>Active quest context config</li>
 *   <li>Permission levels</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class ConfigStateStore extends BaseStateStore<ConfigStateStore.ConfigState> {

    public ConfigStateStore() {
        super("ConfigStore");
    }

    /**
     * Get a config value by key with default.
     */
    public double getDouble(String key, double defaultValue) {
        ConfigState state = getState();
        if (state == null || state.mechanics() == null) return defaultValue;
        return state.mechanics().getOrDefault(key, defaultValue);
    }

    /**
     * Check if a feature flag is enabled.
     */
    public boolean isFeatureEnabled(String flagId) {
        ConfigState state = getState();
        if (state == null || state.featureFlags() == null) return false;
        return state.featureFlags().getOrDefault(flagId, false);
    }

    /**
     * Get player's permission level.
     */
    public int getPermissionLevel() {
        ConfigState state = getState();
        return state != null ? state.permissionLevel() : 0;
    }

    /**
     * Check if body part detection is enabled.
     */
    public boolean isBodyPartDetectionEnabled() {
        return getDouble("combat.bodyPartDetection", 1.0) > 0;
    }

    /**
     * Get head damage multiplier.
     */
    public double getHeadMultiplier() {
        return getDouble("combat.headMultiplier", 2.0);
    }

    /**
     * Get body damage multiplier.
     */
    public double getBodyMultiplier() {
        return getDouble("combat.bodyMultiplier", 1.0);
    }

    /**
     * Get limbs damage multiplier.
     */
    public double getLimbsMultiplier() {
        return getDouble("combat.limbsMultiplier", 0.75);
    }

    /**
     * Immutable config state snapshot.
     */
    public record ConfigState(
        Map<String, Double> mechanics,
        Map<String, Boolean> featureFlags,
        int permissionLevel,
        boolean isOperator,
        @Nullable String activeQuestContext,
        @Nullable String serverVersion,
        long serverTimestamp
    ) {
        /**
         * Create with default values.
         */
        public static ConfigState defaults() {
            return new ConfigState(
                Map.of(
                    "combat.bodyPartDetection", 1.0,
                    "combat.headMultiplier", 2.0,
                    "combat.bodyMultiplier", 1.0,
                    "combat.limbsMultiplier", 0.75
                ),
                Map.of(),
                0,
                false,
                null,
                null,
                System.currentTimeMillis()
            );
        }

        /**
         * Create a builder for constructing state.
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Builder for ConfigState.
         */
        public static class Builder {
            private Map<String, Double> mechanics = Map.of();
            private Map<String, Boolean> featureFlags = Map.of();
            private int permissionLevel = 0;
            private boolean isOperator = false;
            @Nullable private String activeQuestContext = null;
            @Nullable private String serverVersion = null;

            public Builder mechanics(Map<String, Double> mechanics) {
                this.mechanics = mechanics;
                return this;
            }

            public Builder featureFlags(Map<String, Boolean> featureFlags) {
                this.featureFlags = featureFlags;
                return this;
            }

            public Builder permissionLevel(int level) {
                this.permissionLevel = level;
                return this;
            }

            public Builder operator(boolean isOperator) {
                this.isOperator = isOperator;
                return this;
            }

            public Builder activeQuestContext(@Nullable String context) {
                this.activeQuestContext = context;
                return this;
            }

            public Builder serverVersion(@Nullable String version) {
                this.serverVersion = version;
                return this;
            }

            public ConfigState build() {
                return new ConfigState(
                    mechanics,
                    featureFlags,
                    permissionLevel,
                    isOperator,
                    activeQuestContext,
                    serverVersion,
                    System.currentTimeMillis()
                );
            }
        }
    }
}
