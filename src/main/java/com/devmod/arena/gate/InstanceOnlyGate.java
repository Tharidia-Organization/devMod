package com.devmod.arena.gate;

import com.devmod.arena.config.ArenaTemplateConfig;
import com.devmod.arena.telemetry.ArenaTelemetry;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Enforces instance-only arena creation (spec Fase0).
 */
public class InstanceOnlyGate {

    private static final Logger LOGGER = LoggerFactory.getLogger(InstanceOnlyGate.class);
    /**
     * Allowlist of debug caller class simple names.
     * Matching uses exact simple name or fully-qualified class name ending with the caller.
     */
    private static final Set<String> DEBUG_CALLERS = Set.of(
        "QuickTestWizard",
        "DevCommand",
        "ArenaDebugCommand"
    );

    private final ArenaTemplateConfig.ConfigSnapshot config;
    private final ArenaTelemetry telemetry;

    public enum Result {
        ALLOWED,
        ALLOWED_DEBUG_ONLY,
        BLOCKED
    }

    public InstanceOnlyGate(ArenaTemplateConfig.ConfigSnapshot config, ArenaTelemetry telemetry) {
        this.config = config;
        this.telemetry = telemetry;
    }

    public Result check(ServerLevel level, String caller) {
        if (!config.instanceOnly()) {
            return Result.ALLOWED;
        }
        if (isInstanceDimension(level)) {
            return Result.ALLOWED;
        }
        if (config.arenaTemplateEnabled() && config.instanceOnly()) {
            if (isDebugCaller(caller)) {
                LOGGER.warn("[INSTANCE_GATE] Debug caller allowed outside instance: {}", caller);
                return Result.ALLOWED_DEBUG_ONLY;
            }
            LOGGER.error("[INSTANCE_GATE] BLOCKED caller={} dimension={}", caller, level.dimension().location());
            if (telemetry != null) {
                telemetry.emit("arena.gate.blocked", Map.of(
                    "caller", caller,
                    "dimension", level.dimension().location().toString()
                ));
            }
            return Result.BLOCKED;
        }
        return Result.ALLOWED;
    }

    /**
     * Instance-only check when only an instanceId is available (no ServerLevel).
     */
    public Result checkInstanceId(UUID instanceId, String caller) {
        if (!config.instanceOnly()) {
            return Result.ALLOWED;
        }
        if (instanceId != null) {
            return Result.ALLOWED;
        }
        if (config.arenaTemplateEnabled() && config.instanceOnly()) {
            if (isDebugCaller(caller)) {
                LOGGER.warn("[INSTANCE_GATE] Debug caller allowed without instanceId: {}", caller);
                return Result.ALLOWED_DEBUG_ONLY;
            }
            LOGGER.error("[INSTANCE_GATE] BLOCKED caller={} dimension=unknown", caller);
            if (telemetry != null) {
                telemetry.emit("arena.gate.blocked", Map.of(
                    "caller", caller,
                    "dimension", "unknown"
                ));
            }
            return Result.BLOCKED;
        }
        return Result.ALLOWED;
    }

    private boolean isInstanceDimension(ServerLevel level) {
        String ns = level.dimension().location().getNamespace();
        String path = level.dimension().location().getPath();
        return "devmod".equals(ns) && path.startsWith("instance_");
    }

    /**
     * Checks if the caller is in the debug allowlist.
     * Uses exact match on simple name or endsWith for fully-qualified class names.
     * This prevents false positives from substring matching (e.g., "QuickTestWizardFoo").
     */
    private boolean isDebugCaller(String caller) {
        if (caller == null || caller.isBlank()) return false;
        for (String dbg : DEBUG_CALLERS) {
            // Exact match for simple name
            if (caller.equals(dbg)) return true;
            // Fully-qualified class name: ends with ".ClassName"
            if (caller.endsWith("." + dbg)) return true;
        }
        return false;
    }
}
