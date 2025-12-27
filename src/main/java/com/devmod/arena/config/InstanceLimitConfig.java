package com.devmod.arena.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.arena.registry.InstanceSettingsValidator;

public record InstanceLimitConfig(int maxChunkRadius, int maxTickDistance) {

    private static final Logger LOGGER = LoggerFactory.getLogger(InstanceLimitConfig.class);
    private static final int DEFAULT_CHUNK_RADIUS = 8;
    private static final int DEFAULT_TICK_DISTANCE = 10;

    public InstanceSettingsValidator.InstanceLimits toLimits() {
        return new InstanceSettingsValidator.InstanceLimits(maxChunkRadius, maxTickDistance);
    }

    public static InstanceLimitConfig load() {
        int chunkRadius = getInt("devmod.instance.maxChunkRadius", "DEVMOD_MAX_CHUNK_RADIUS", DEFAULT_CHUNK_RADIUS);
        int tickDistance = getInt("devmod.instance.maxTickDistance", "DEVMOD_MAX_TICK_DISTANCE", DEFAULT_TICK_DISTANCE);
        return new InstanceLimitConfig(chunkRadius, tickDistance);
    }

    private static int getInt(String sysProp, String envVar, int defaultValue) {
        String sys = System.getProperty(sysProp);
        if (sys != null && !sys.isBlank()) {
            try {
                return Integer.parseInt(sys);
            } catch (NumberFormatException e) {
                LOGGER.warn("Invalid value for {}: {}", sysProp, sys, e);
            }
        }
        String env = System.getenv(envVar);
        if (env != null && !env.isBlank()) {
            try {
                return Integer.parseInt(env);
            } catch (NumberFormatException e) {
                LOGGER.warn("Invalid value for {}: {}", envVar, env, e);
            }
        }
        return defaultValue;
    }
}
