package com.devmod.debug;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Diagnostic logging utility for in-game debugging of DevMod systems.
 *
 * <p>Logs are only emitted when a player has enabled the corresponding
 * diagnostic feature via {@code /devmod debug <feature>}.
 *
 * <p>Usage:
 * <pre>
 * DiagnosticLogger.quest("Starting wave %d with %d mobs", waveNum, mobCount);
 * DiagnosticLogger.combat("Damage: %.1f -> %.1f (blocked: %.1f)", raw, final, blocked);
 * </pre>
 */
public final class DiagnosticLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger("DevMod-Diag");

    private DiagnosticLogger() {}

    /**
     * Log a diagnostic message if the feature is enabled for any player.
     *
     * @param feature The diagnostic feature category
     * @param message The message format string
     * @param args    Format arguments
     */
    public static void log(DebugFeature feature, String message, Object... args) {
        if (!DebugManager.INSTANCE.anyPlayerHasFeature(feature)) {
            return;
        }
        String formatted = args.length > 0 ? String.format(message, args) : message;
        LOGGER.info("[{}] {}", feature.getId(), formatted);
    }

    // === Shortcut methods for common categories ===

    /**
     * Log quest-related diagnostic info.
     * Enable with: /devmod debug diag_quest
     */
    public static void quest(String message, Object... args) {
        log(DebugFeature.DIAG_QUEST, message, args);
    }

    /**
     * Log combat-related diagnostic info.
     * Enable with: /devmod debug diag_combat
     */
    public static void combat(String message, Object... args) {
        log(DebugFeature.DIAG_COMBAT, message, args);
    }

    /**
     * Log transport-related diagnostic info.
     * Enable with: /devmod debug diag_transport
     */
    public static void transport(String message, Object... args) {
        log(DebugFeature.DIAG_TRANSPORT, message, args);
    }

    /**
     * Log arena-related diagnostic info.
     * Enable with: /devmod debug diag_arena
     */
    public static void arena(String message, Object... args) {
        log(DebugFeature.DIAG_ARENA, message, args);
    }

    /**
     * Log clone machine diagnostic info.
     * Enable with: /devmod debug diag_clone
     */
    public static void clone(String message, Object... args) {
        log(DebugFeature.DIAG_CLONE, message, args);
    }

    /**
     * Log NPC/dialog diagnostic info.
     * Enable with: /devmod debug diag_npc
     */
    public static void npc(String message, Object... args) {
        log(DebugFeature.DIAG_NPC, message, args);
    }

    /**
     * Check if any diagnostic feature is currently active.
     * Useful for expensive diagnostic operations.
     */
    public static boolean isAnyDiagnosticActive() {
        return DebugManager.INSTANCE.anyPlayerHasFeature(DebugFeature.DIAG_QUEST)
            || DebugManager.INSTANCE.anyPlayerHasFeature(DebugFeature.DIAG_COMBAT)
            || DebugManager.INSTANCE.anyPlayerHasFeature(DebugFeature.DIAG_TRANSPORT)
            || DebugManager.INSTANCE.anyPlayerHasFeature(DebugFeature.DIAG_ARENA)
            || DebugManager.INSTANCE.anyPlayerHasFeature(DebugFeature.DIAG_CLONE)
            || DebugManager.INSTANCE.anyPlayerHasFeature(DebugFeature.DIAG_NPC);
    }

    /**
     * Check if a specific diagnostic feature is active.
     */
    public static boolean isActive(DebugFeature feature) {
        return DebugManager.INSTANCE.anyPlayerHasFeature(feature);
    }
}
