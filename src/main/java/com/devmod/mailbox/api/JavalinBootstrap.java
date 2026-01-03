package com.devmod.mailbox.api;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bootstrap utility for Javalin HTTP server and dependencies.
 *
 * Downloads Javalin, Jetty, Jackson, and JJWT JARs from Maven Central
 * to config/devmod/libs/ and loads them via a custom URLClassLoader.
 *
 * This approach avoids NeoForge module system conflicts by loading
 * Javalin in an isolated classloader, similar to how DuckDB is handled.
 */
public final class JavalinBootstrap {
    private static final Logger LOGGER = LoggerFactory.getLogger(JavalinBootstrap.class);

    private static final String JAVALIN_CLASS = "io.javalin.Javalin";
    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";
    private static final String LIBS_DIR = "config/devmod/libs";

    private static final int DOWNLOAD_TIMEOUT_MS = 60_000;
    private static final int READ_TIMEOUT_MS = 120_000;

    @Nullable
    private static URLClassLoader javalinClassLoader = null;

    @Nullable
    private static Boolean available = null;

    private static boolean downloadAttempted = false;

    /**
     * All required dependencies for Javalin API server.
     * Format: {groupId, artifactId, version}
     */
    private static final String[][] DEPENDENCIES = {
        // Javalin core
        {"io.javalin", "javalin", "5.6.5"},

        // Jetty 11 modules
        {"org.eclipse.jetty", "jetty-server", "11.0.20"},
        {"org.eclipse.jetty", "jetty-servlet", "11.0.20"},
        {"org.eclipse.jetty", "jetty-webapp", "11.0.20"},
        {"org.eclipse.jetty", "jetty-xml", "11.0.20"},
        {"org.eclipse.jetty", "jetty-http", "11.0.20"},
        {"org.eclipse.jetty", "jetty-io", "11.0.20"},
        {"org.eclipse.jetty", "jetty-util", "11.0.20"},
        {"org.eclipse.jetty", "jetty-security", "11.0.20"},

        // Jetty WebSocket (for Javalin WebSocket support)
        {"org.eclipse.jetty.websocket", "websocket-jetty-server", "11.0.20"},
        {"org.eclipse.jetty.websocket", "websocket-jetty-common", "11.0.20"},
        {"org.eclipse.jetty.websocket", "websocket-jetty-api", "11.0.20"},
        {"org.eclipse.jetty.websocket", "websocket-core-server", "11.0.20"},
        {"org.eclipse.jetty.websocket", "websocket-core-common", "11.0.20"},
        {"org.eclipse.jetty.websocket", "websocket-servlet", "11.0.20"},

        // Servlet API
        {"org.eclipse.jetty.toolchain", "jetty-jakarta-servlet-api", "5.0.2"},

        // Jackson for JSON
        {"com.fasterxml.jackson.core", "jackson-databind", "2.17.2"},
        {"com.fasterxml.jackson.core", "jackson-core", "2.17.2"},
        {"com.fasterxml.jackson.core", "jackson-annotations", "2.17.2"},

        // JJWT for authentication
        {"io.jsonwebtoken", "jjwt-api", "0.12.3"},
        {"io.jsonwebtoken", "jjwt-impl", "0.12.3"},
        {"io.jsonwebtoken", "jjwt-gson", "0.12.3"},

        // JetBrains annotations (required by Javalin)
        {"org.jetbrains", "annotations", "24.1.0"},

        // Kotlin stdlib (required by Javalin - safe in isolated classloader)
        {"org.jetbrains.kotlin", "kotlin-stdlib", "1.9.22"},
    };

    private JavalinBootstrap() {}

    /**
     * Get the classloader for Javalin classes.
     * Returns null if Javalin is not available.
     */
    @Nullable
    public static ClassLoader getClassLoader() {
        return javalinClassLoader;
    }

    /**
     * Check if Javalin is available (either in classpath or via custom classloader).
     */
    public static boolean isAvailable() {
        if (available == null) {
            // First check if in classpath (shouldn't be, but check anyway)
            try {
                Class.forName(JAVALIN_CLASS);
                available = Boolean.TRUE;
                LOGGER.info("[Javalin] Libraries found in classpath");
                return true;
            } catch (ClassNotFoundException e) {
                // Expected - check custom classloader
            }

            // Check if custom classloader is initialized
            if (javalinClassLoader != null) {
                try {
                    javalinClassLoader.loadClass(JAVALIN_CLASS);
                    available = Boolean.TRUE;
                    LOGGER.info("[Javalin] Libraries loaded via custom classloader");
                    return true;
                } catch (ClassNotFoundException e) {
                    available = Boolean.FALSE;
                }
            } else {
                available = Boolean.FALSE;
            }
        }
        return Boolean.TRUE.equals(available);
    }

    /**
     * Ensure Javalin and all dependencies are available.
     *
     * This method will:
     * 1. Check if JARs exist in libs/ directory
     * 2. Download missing JARs from Maven Central
     * 3. Create a URLClassLoader with all JARs
     * 4. Return true if Javalin is ready to use
     *
     * @param gameDir the Minecraft game directory
     * @return true if Javalin is ready, false if failed
     */
    public static boolean ensureAvailable(Path gameDir) {
        if (isAvailable()) {
            return true;
        }

        if (downloadAttempted) {
            LOGGER.debug("[Javalin] Download already attempted this session");
            return isAvailable();
        }
        downloadAttempted = true;

        Path libsDir = gameDir.resolve(LIBS_DIR);

        try {
            Files.createDirectories(libsDir);

            // Download missing dependencies
            List<String> downloaded = new ArrayList<>();
            List<String> failed = new ArrayList<>();

            for (String[] dep : DEPENDENCIES) {
                String groupId = dep[0];
                String artifactId = dep[1];
                String version = dep[2];

                String jarName = artifactId + "-" + version + ".jar";
                Path targetPath = libsDir.resolve(jarName);

                if (Files.exists(targetPath) && Files.size(targetPath) > 1000) {
                    LOGGER.debug("[Javalin] {} already exists", jarName);
                    downloaded.add(jarName);
                    continue;
                }

                try {
                    downloadDependency(libsDir, groupId, artifactId, version);
                    downloaded.add(jarName);
                } catch (IOException e) {
                    LOGGER.error("[Javalin] Failed to download {}: {}", jarName, e.getMessage());
                    failed.add(jarName);
                }
            }

            if (!failed.isEmpty()) {
                LOGGER.error("[Javalin] Failed to download {} of {} dependencies",
                    failed.size(), DEPENDENCIES.length);
                LOGGER.error("[Javalin] Failed: {}", String.join(", ", failed));
                return false;
            }

            LOGGER.info("[Javalin] All {} dependencies available", downloaded.size());

            // Create classloader with all JARs
            return initializeClassLoader(libsDir);

        } catch (IOException e) {
            LOGGER.error("[Javalin] Failed to initialize: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Initialize the custom classloader with all JAR files.
     * Also includes the mod's own classes so MailboxApiServer can be loaded
     * with access to Javalin.
     */
    private static boolean initializeClassLoader(Path libsDir) {
        try {
            List<URL> urls = new ArrayList<>();

            // Add Javalin dependency JARs
            for (String[] dep : DEPENDENCIES) {
                String jarName = dep[1] + "-" + dep[2] + ".jar";
                Path jarPath = libsDir.resolve(jarName);

                if (Files.exists(jarPath)) {
                    urls.add(jarPath.toUri().toURL());
                } else {
                    LOGGER.warn("[Javalin] Missing JAR: {}", jarName);
                }
            }

            if (urls.isEmpty()) {
                LOGGER.error("[Javalin] No JAR files found");
                return false;
            }

            // Add the mod's own code location so MailboxApiServer can be loaded
            // from this classloader with access to Javalin classes
            try {
                URL modCodeSource = JavalinBootstrap.class.getProtectionDomain()
                    .getCodeSource().getLocation();
                if (modCodeSource != null) {
                    urls.add(modCodeSource);
                    LOGGER.debug("[Javalin] Added mod code source: {}", modCodeSource);
                }
            } catch (Exception e) {
                LOGGER.warn("[Javalin] Could not get mod code source: {}", e.getMessage());
            }

            // Create a parent-last (child-first) classloader
            // This loads classes from our URLs first, then falls back to parent
            // This allows MailboxApiServer to be loaded from our URLs (with access to Javalin)
            // while still having access to MC/NeoForge classes via parent
            javalinClassLoader = new ParentLastURLClassLoader(
                urls.toArray(new URL[0]),
                JavalinBootstrap.class.getClassLoader()
            );

            // Verify Javalin can be loaded
            try {
                Class<?> javalinClass = javalinClassLoader.loadClass(JAVALIN_CLASS);
                LOGGER.info("[Javalin] Successfully loaded {} from custom classloader",
                    javalinClass.getName());
                available = Boolean.TRUE;
                return true;
            } catch (ClassNotFoundException e) {
                LOGGER.error("[Javalin] Failed to load Javalin class: {}", e.getMessage());
                available = Boolean.FALSE;
                return false;
            }

        } catch (IOException e) {
            LOGGER.error("[Javalin] Failed to create classloader: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Download a single dependency from Maven Central.
     */
    private static void downloadDependency(Path libsDir, String groupId, String artifactId,
                                           String version) throws IOException {
        String groupPath = groupId.replace('.', '/');
        String jarFileName = artifactId + "-" + version + ".jar";
        String url = MAVEN_CENTRAL + groupPath + "/" + artifactId + "/" + version + "/" + jarFileName;

        LOGGER.info("[Javalin] Downloading {}...", jarFileName);

        Path downloadPath = libsDir.resolve(jarFileName + ".download");
        Path targetPath = libsDir.resolve(jarFileName);

        // Download
        downloadFile(url, downloadPath);

        // Verify size
        long size = Files.size(downloadPath);
        if (size < 1_000) {
            Files.deleteIfExists(downloadPath);
            throw new IOException("Downloaded file too small: " + size + " bytes");
        }

        // Move to final location
        Files.move(downloadPath, targetPath, StandardCopyOption.REPLACE_EXISTING);

        long sizeKB = size / 1024;
        LOGGER.info("[Javalin] Downloaded {} ({} KB)", jarFileName, sizeKB);
    }

    /**
     * Download a file from URL.
     */
    private static void downloadFile(String urlString, Path targetPath) throws IOException {
        URI uri = URI.create(urlString);
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();

        try {
            connection.setConnectTimeout(DOWNLOAD_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", "DevMod/1.0 (Minecraft Mod)");
            connection.setInstanceFollowRedirects(true);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + responseCode + " from " + urlString);
            }

            try (InputStream in = connection.getInputStream()) {
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

        } finally {
            connection.disconnect();
        }
    }

    /**
     * Shutdown and cleanup the classloader.
     */
    public static void shutdown() {
        if (javalinClassLoader != null) {
            try {
                javalinClassLoader.close();
                LOGGER.info("[Javalin] Classloader closed");
            } catch (IOException e) {
                LOGGER.warn("[Javalin] Error closing classloader: {}", e.getMessage());
            }
            javalinClassLoader = null;
            available = null;
        }
    }

    /**
     * Get list of expected JAR files.
     */
    public static List<String> getExpectedJarNames() {
        List<String> names = new ArrayList<>();
        for (String[] dep : DEPENDENCIES) {
            names.add(dep[1] + "-" + dep[2] + ".jar");
        }
        return names;
    }

    /**
     * Get total expected download size in MB (approximate).
     */
    public static int getExpectedDownloadSizeMB() {
        return 8; // ~8 MB total including Kotlin
    }

    /**
     * A parent-last (child-first) classloader.
     * Loads classes from URLs first, then delegates to parent if not found.
     * This allows our mod classes to be loaded with access to Javalin,
     * while still having access to MC/NeoForge classes.
     */
    private static class ParentLastURLClassLoader extends URLClassLoader {
        public ParentLastURLClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                // First check if already loaded
                Class<?> c = findLoadedClass(name);
                if (c != null) {
                    return c;
                }

                // For system classes, always delegate to parent
                if (name.startsWith("java.") || name.startsWith("javax.") ||
                    name.startsWith("sun.") || name.startsWith("jdk.")) {
                    return super.loadClass(name, resolve);
                }

                // Try to load from our URLs first (child-first)
                try {
                    c = findClass(name);
                    if (resolve) {
                        resolveClass(c);
                    }
                    return c;
                } catch (ClassNotFoundException e) {
                    // Not in our URLs, delegate to parent
                }

                // Fall back to parent
                return super.loadClass(name, resolve);
            }
        }
    }
}
