package com.devmod.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.fml.loading.FMLPaths;

/**
 * Runtime utility for TesterModality gating.
 *
 * <p>Production modules (combat, radial menu, abilities, core infra) are always active.
 * Tester modules (endurance, NPC, transport, debug, clone, portal, hologram, etc.)
 * are gated behind this flag at runtime.
 *
 * <p>Thread-safe: uses volatile + AtomicBoolean for safe cross-thread access.
 *
 * <p>Usage:
 * <pre>{@code
 * if (TesterModality.isDisabled()) return; // early-return in handlers/screens/renderers
 * }</pre>
 */
public final class TesterModality {

    private static final Logger LOGGER = LoggerFactory.getLogger(TesterModality.class);

    /** Server-synced value (set on login, reset on logout). */
    private static volatile boolean serverValue = true;

    /** Whether the server has synced its value to this client. */
    private static final AtomicBoolean serverSynced = new AtomicBoolean(false);
    /** Early bootstrap fallback before NeoForge config values become readable. */
    private static volatile Boolean bootstrapConfigValue;

    private TesterModality() {}

    // ========================================================================
    // QUERY
    // ========================================================================

    /**
     * Returns {@code true} if tester modality is enabled.
     * Checks the config value directly (works on both client and server).
     */
    public static boolean isEnabled() {
        try {
            return Config.TESTER_MODALITY.get();
        } catch (IllegalStateException e) {
            return resolveBootstrapConfigValue();
        }
    }

    /**
     * Returns {@code true} if tester modality is disabled.
     * Convenience inverse of {@link #isEnabled()}.
     */
    public static boolean isDisabled() {
        return !isEnabled();
    }

    // ========================================================================
    // SERVER SYNC (client-side)
    // ========================================================================

    /**
     * Called on client when the server sends its TesterModality value.
     *
     * @param serverEnabled the server's tester modality setting
     * @return {@code true} if client and server match, {@code false} on mismatch
     */
    public static boolean onServerSync(boolean serverEnabled) {
        serverValue = serverEnabled;
        serverSynced.set(true);

        boolean clientEnabled = isEnabled();
        boolean match = clientEnabled == serverEnabled;

        if (match) {
            LOGGER.info("[TesterModality] Client/server in sync: enabled={}", serverEnabled);
        } else {
            LOGGER.warn("[TesterModality] MISMATCH - client={}, server={}",
                clientEnabled, serverEnabled);
        }

        return match;
    }

    /**
     * Returns the last value received from the server.
     * Only meaningful after {@link #onServerSync} has been called.
     */
    public static boolean getServerValue() {
        return serverValue;
    }

    /**
     * Returns {@code true} if the server has synced its value to this client.
     */
    public static boolean isServerSynced() {
        return serverSynced.get();
    }

    /**
     * Deterministic compatibility rule for client/server tester modality.
     */
    public static boolean isCompatible(boolean serverEnabled, boolean clientEnabled) {
        return serverEnabled == clientEnabled;
    }

    /**
     * Standard mismatch reason for disconnect messaging/logging.
     */
    public static String mismatchReason(boolean serverEnabled, boolean clientEnabled) {
        return "TesterModality mismatch: server=" + serverEnabled
            + ", client=" + clientEnabled
            + ". Access denied.";
    }

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    /**
     * Reset client-side sync state. Called on player logout.
     */
    public static void reset() {
        serverValue = true;
        serverSynced.set(false);
        LOGGER.debug("[TesterModality] Reset (client logout)");
    }

    private static boolean resolveBootstrapConfigValue() {
        Boolean cached = bootstrapConfigValue;
        if (cached != null) {
            return cached;
        }

        boolean resolved = true;
        try {
            Path configFile = FMLPaths.CONFIGDIR.get().resolve("devmod-common.toml");
            if (Files.exists(configFile)) {
                boolean inTesterSection = false;
                for (String rawLine : Files.readAllLines(configFile)) {
                    String line = rawLine == null ? "" : rawLine.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }

                    if (line.startsWith("[") && line.endsWith("]")) {
                        inTesterSection = "testerModality".equals(line.substring(1, line.length() - 1).trim());
                        continue;
                    }

                    if (inTesterSection && line.startsWith("enabled")) {
                        int eqIndex = line.indexOf('=');
                        if (eqIndex > 0 && eqIndex < line.length() - 1) {
                            String value = line.substring(eqIndex + 1).split("#", 2)[0].trim();
                            if ("false".equalsIgnoreCase(value)) {
                                resolved = false;
                            } else if ("true".equalsIgnoreCase(value)) {
                                resolved = true;
                            }
                        }
                        break;
                    }
                }
            }
        } catch (IOException | RuntimeException ex) {
            LOGGER.debug("[TesterModality] Bootstrap config read failed, using default=true: {}", ex.getMessage());
        }

        bootstrapConfigValue = resolved;
        return resolved;
    }
}
