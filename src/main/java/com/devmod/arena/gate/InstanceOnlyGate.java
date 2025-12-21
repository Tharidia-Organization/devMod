package com.devmod.arena.gate;

import com.devmod.arena.config.ArenaTemplateConfig;
import com.devmod.arena.telemetry.ArenaTelemetry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Enforces instance-only arena creation (spec Fase0).
 */
public class InstanceOnlyGate {

    private static final Logger LOGGER = LoggerFactory.getLogger(InstanceOnlyGate.class);
    public static final String MESSAGE_KEY_INSTANCE_ONLY_BLOCKED = "devmod.arena.instance_only.blocked";
    public static final String MESSAGE_KEY_LEGACY_OVERWORLD_BLOCKED = "devmod.arena.legacy_overworld.blocked";
    /**
     * Allowlist of debug caller class simple names.
     * Matching uses exact simple name or fully-qualified class name ending with the caller.
     */
    private static final Set<String> DEBUG_CALLERS = Set.of(
        "QuickTestWizard",
        "DevCommand",
        "ArenaDebugCommand",
        "ArenaCommands"
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
        return checkDimensionKey(level != null ? level.dimension() : null, caller);
    }

    public Result checkDimensionKey(ResourceKey<Level> dimensionKey, String caller) {
        ResourceLocation location = dimensionKey != null ? dimensionKey.location() : null;
        boolean instanceWorld = isInstanceDimension(location);
        String dimension = location != null ? location.toString() : "unknown";

        Result result;
        if (!config.instanceOnly()) {
            if (config.allowLegacyOverworldArena() && isDebugCaller(caller)) {
                LOGGER.warn("[INSTANCE_GATE] Debug caller allowed while instanceOnly=false: {}", caller);
                result = Result.ALLOWED_DEBUG_ONLY;
            } else {
                LOGGER.error("[INSTANCE_GATE] BLOCKED caller={} dimension={} reason=instance_only_disabled",
                    caller, dimension);
                emitGateBlocked(caller, dimension, false, "instance_only_disabled");
                result = Result.BLOCKED;
            }
        } else if (instanceWorld) {
            result = Result.ALLOWED;
        } else if (isDebugCaller(caller)) {
            LOGGER.warn("[INSTANCE_GATE] Debug caller allowed outside instance: {}", caller);
            result = Result.ALLOWED_DEBUG_ONLY;
        } else {
            LOGGER.error("[INSTANCE_GATE] BLOCKED caller={} dimension={}", caller, dimension);
            emitGateBlocked(caller, dimension, false, null);
            result = Result.BLOCKED;
        }

        if (!instanceWorld) {
            emitLegacyCall(caller, dimension, result, false, null);
        }
        return result;
    }

    /**
     * Instance-only check when only an instanceId is available (no ServerLevel).
     */
    public Result checkInstanceId(UUID instanceId, String caller) {
        Result result;
        if (!config.instanceOnly()) {
            if (config.allowLegacyOverworldArena() && isDebugCaller(caller)) {
                LOGGER.warn("[INSTANCE_GATE] Debug caller allowed while instanceOnly=false: {}", caller);
                result = Result.ALLOWED_DEBUG_ONLY;
            } else {
                LOGGER.error("[INSTANCE_GATE] BLOCKED caller={} dimension=unknown reason=instance_only_disabled", caller);
                emitGateBlocked(caller, "unknown", false, "instance_only_disabled");
                result = Result.BLOCKED;
            }
        } else if (instanceId != null) {
            result = Result.ALLOWED;
        } else if (isDebugCaller(caller)) {
            LOGGER.warn("[INSTANCE_GATE] Debug caller allowed without instanceId: {}", caller);
            result = Result.ALLOWED_DEBUG_ONLY;
        } else {
            LOGGER.error("[INSTANCE_GATE] BLOCKED caller={} dimension=unknown", caller);
            emitGateBlocked(caller, "unknown", false, "missing_instance_id");
            result = Result.BLOCKED;
        }

        if (instanceId == null) {
            emitLegacyCall(caller, "unknown", result, false, "missing_instance_id");
        }
        return result;
    }

    /**
     * Enforced instance-only check, ignoring config.instanceOnly.
     * Intended for local feature flags that must not fall back to legacy behavior.
     */
    public Result checkEnforced(ServerLevel level, String caller, String reason) {
        ResourceLocation location = level != null ? level.dimension().location() : null;
        String dimension = location != null ? location.toString() : "unknown";
        boolean instanceWorld = isInstanceDimension(location);
        if (instanceWorld) {
            return Result.ALLOWED;
        }

        Result result;
        if (config.allowLegacyOverworldArena() && isDebugCaller(caller)) {
            LOGGER.warn("[INSTANCE_GATE] Debug caller allowed outside instance (enforced): {}", caller);
            result = Result.ALLOWED_DEBUG_ONLY;
        } else {
            LOGGER.error("[INSTANCE_GATE] BLOCKED caller={} dimension={} (enforced)", caller, dimension);
            emitGateBlocked(caller, dimension, true, reason);
            result = Result.BLOCKED;
        }

        emitLegacyCall(caller, dimension, result, true, reason);
        return result;
    }

    private boolean isInstanceDimension(ResourceLocation location) {
        if (location == null) {
            return false;
        }
        String ns = location.getNamespace();
        String path = location.getPath();
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

    public void requireAllowedOrThrow(ServerLevel level, String caller, String templateId, int templateVersion) {
        Result result = check(level, caller);
        if (result == Result.BLOCKED) {
            String msg = "Instance-only mode: build blocked for template '%s' v%d in %s"
                .formatted(templateId, templateVersion, level.dimension().location());
            throw new GateBlockedException(MESSAGE_KEY_INSTANCE_ONLY_BLOCKED, msg);
        }
    }

    private void emitLegacyCall(String caller, String dimension, Result result, boolean enforced, String reason) {
        if (telemetry == null) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("caller", caller != null ? caller : "");
        data.put("dimension", dimension != null ? dimension : "unknown");
        data.put("result", result.name());
        data.put("debug", result == Result.ALLOWED_DEBUG_ONLY);
        data.put("instanceOnly", config.instanceOnly());
        data.put("allowLegacyOverworldArena", config.allowLegacyOverworldArena());
        data.put("arenaTemplateEnabled", config.arenaTemplateEnabled());
        data.put("enforced", enforced);
        if (reason != null && !reason.isBlank()) {
            data.put("reason", reason);
        }
        telemetry.emit("arena.legacy.call", data);
    }

    private void emitGateBlocked(String caller, String dimension, boolean enforced, String reason) {
        if (telemetry == null) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("caller", caller != null ? caller : "");
        data.put("dimension", dimension != null ? dimension : "unknown");
        if (enforced) {
            data.put("enforced", true);
        }
        if (reason != null && !reason.isBlank()) {
            data.put("reason", reason);
        }
        telemetry.emit("arena.gate.blocked", data);
    }

    public static class GateBlockedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final String messageKey;

        public GateBlockedException(String messageKey, String message) {
            super(message);
            this.messageKey = messageKey;
        }

        public String getMessageKey() {
            return messageKey;
        }
    }
}
