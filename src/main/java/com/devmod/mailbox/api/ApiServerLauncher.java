package com.devmod.mailbox.api;

import java.nio.file.Path;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Safe launcher for the Mailbox API server.
 *
 * This class acts as a facade that checks if Javalin is available before
 * attempting to load or use the MailboxApiServer class. This prevents
 * NoClassDefFoundError when Javalin libraries are not installed.
 *
 * Usage:
 * - Call {@link #tryStart(Path)} to start the API server (downloads deps if needed)
 * - Call {@link #stop()} to stop the API server (safe even if not running)
 * - Call {@link #isRunning()} to check server status
 */
public final class ApiServerLauncher {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiServerLauncher.class);

    private static boolean startAttempted = false;
    @Nullable
    private static ClassLoader apiClassLoader = null;

    private ApiServerLauncher() {}

    /**
     * Attempt to start the API server.
     *
     * If Javalin is not available, this will trigger a download of all required
     * dependencies. The server will not start until the next restart.
     *
     * @param gameDir the Minecraft game directory (for downloading dependencies)
     * @return true if server started, false if restart required or disabled
     */
    public static boolean tryStart(Path gameDir) {
        return tryStart(gameDir, 8765);
    }

    /**
     * Attempt to start the API server on a specific port.
     *
     * @param gameDir the Minecraft game directory
     * @param port the port to start on
     * @return true if server started, false if restart required or disabled
     */
    public static boolean tryStart(Path gameDir, int port) {
        if (startAttempted) {
            return isRunning();
        }
        startAttempted = true;

        // Check if Javalin is available
        if (!JavalinBootstrap.isAvailable()) {
            // Try to download dependencies
            LOGGER.info("[API] Javalin not available, checking for download...");
            boolean ready = JavalinBootstrap.ensureAvailable(gameDir);

            if (!ready) {
                LOGGER.info("[API] Javalin dependencies downloaded or pending - restart required");
                return false;
            }
        }

        // Javalin is available - start the server via reflection
        // We must load MailboxApiServer from the custom classloader so it can
        // access Javalin classes
        ClassLoader javalinLoader = resolveClassLoader();
        if (javalinLoader == null) {
            LOGGER.error("[API] Custom classloader not available");
            return false;
        }

        try {
            LOGGER.info("[API] Starting Mailbox API server on port {}...", port);

            // Load MailboxApiServer from custom classloader
            Class<?> serverClass = javalinLoader.loadClass(
                "com.devmod.mailbox.api.MailboxApiServer");

            // Call start(int port) method
            java.lang.reflect.Method startMethod = serverClass.getMethod("start", int.class);
            startMethod.invoke(null, port);

            apiClassLoader = javalinLoader;
            LOGGER.info("[API] Mailbox API server started successfully on port {}", port);
            return true;
        } catch (ClassNotFoundException e) {
            LOGGER.error("[API] Failed to load MailboxApiServer: {}", e.getMessage());
            return false;
        } catch (NoClassDefFoundError e) {
            LOGGER.error("[API] Failed to start - missing class: {}", e.getMessage());
            return false;
        } catch (java.lang.reflect.InvocationTargetException e) {
            LOGGER.error("[API] Failed to start API server: {}",
                e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            return false;
        } catch (Exception e) {
            LOGGER.error("[API] Failed to start API server", e);
            return false;
        }
    }

    /**
     * Stop the API server if running.
     *
     * This method is safe to call even if Javalin is not installed or
     * the server was never started.
     */
    public static void stop() {
        ClassLoader javalinLoader = resolveApiClassLoader();
        if (javalinLoader == null) {
            return;
        }

        try {
            Class<?> serverClass = javalinLoader.loadClass(
                "com.devmod.mailbox.api.MailboxApiServer");
            java.lang.reflect.Method stopMethod = serverClass.getMethod("stop");
            stopMethod.invoke(null);
        } catch (ClassNotFoundException e) {
            LOGGER.debug("[API] Stop called but MailboxApiServer not loaded");
        } catch (Exception e) {
            LOGGER.error("[API] Error stopping API server", e);
        }

        // Also shutdown the custom classloader if we own it
        if (javalinLoader == JavalinBootstrap.getClassLoader()) {
            JavalinBootstrap.shutdown();
        }
    }

    /**
     * Check if the API server is currently running.
     *
     * @return true if server is running
     */
    public static boolean isRunning() {
        ClassLoader javalinLoader = resolveApiClassLoader();
        if (javalinLoader == null) {
            return false;
        }

        try {
            Class<?> serverClass = javalinLoader.loadClass(
                "com.devmod.mailbox.api.MailboxApiServer");
            java.lang.reflect.Method isRunningMethod = serverClass.getMethod("isRunning");
            Object result = isRunningMethod.invoke(null);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get the port the server is running on.
     *
     * @return port number, or -1 if not running
     */
    public static int getPort() {
        ClassLoader javalinLoader = resolveApiClassLoader();
        if (javalinLoader == null) {
            return -1;
        }

        try {
            Class<?> serverClass = javalinLoader.loadClass(
                "com.devmod.mailbox.api.MailboxApiServer");
            java.lang.reflect.Method getPortMethod = serverClass.getMethod("getPort");
            Object result = getPortMethod.invoke(null);
            return result instanceof Integer ? (Integer) result : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Check if Javalin libraries are available.
     *
     * @return true if API server can be started
     */
    public static boolean isApiAvailable() {
        return JavalinBootstrap.isAvailable();
    }

    /**
     * Check if dependencies need to be downloaded.
     *
     * @return true if Javalin is not available
     */
    public static boolean needsDownload() {
        return !JavalinBootstrap.isAvailable();
    }

    @Nullable
    private static ClassLoader resolveApiClassLoader() {
        if (apiClassLoader != null) {
            return apiClassLoader;
        }
        return resolveClassLoader();
    }

    @Nullable
    private static ClassLoader resolveClassLoader() {
        ClassLoader javalinLoader = JavalinBootstrap.getClassLoader();
        if (javalinLoader != null) {
            return javalinLoader;
        }
        if (JavalinBootstrap.isAvailable()) {
            LOGGER.info("[API] Using default classloader for Mailbox API");
            return ApiServerLauncher.class.getClassLoader();
        }
        return null;
    }
}
