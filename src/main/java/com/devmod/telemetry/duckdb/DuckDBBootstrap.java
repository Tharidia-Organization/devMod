package com.devmod.telemetry.duckdb;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Driver;
import java.util.Locale;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bootstrap utility for the DuckDB JDBC driver.
 *
 * The platform-specific driver is downloaded to {@code config/devmod/libs/} and loaded
 * through a dedicated {@link URLClassLoader}, exactly as {@code JavalinBootstrap} does for
 * Javalin and Jetty. Nothing is written to {@code mods/} and no restart is needed.
 *
 * <p>This used to download into {@code mods/}, repackaged with {@code FMLModType: GAMELIBRARY}
 * so NeoForge would pick it up on the next boot. That failed outright on a server whose mods
 * directory is read-only -- which is the normal state of a managed instance -- and even where
 * it succeeded it wrote into the directory whose contents are supposed to be fixed at launch,
 * and cost a restart before Mailbox, Notifications and DuckDB telemetry came up.
 *
 * <p>Bundling the driver instead (JarInJar) is deliberately NOT done: this project removed its
 * JarInJar dependencies because embedded libraries collide with other mods shipping the same
 * ones -- see the notes in build.gradle about Kotlin For Forge, Plasmo Voice and
 * "reads more than one module named jjwt.gson". The universal DuckDB artifact is also 81 MB
 * against 19 MB for one platform.
 */
public final class DuckDBBootstrap {

    private static final Logger LOGGER = LoggerFactory.getLogger(DuckDBBootstrap.class);

    private static final String DUCKDB_VERSION = "1.4.3.0";
    private static final String MAVEN_BASE_URL =
        "https://repo1.maven.org/maven2/org/duckdb/duckdb_jdbc/" + DUCKDB_VERSION + "/";
    private static final String DUCKDB_DRIVER_CLASS = "org.duckdb.DuckDBDriver";

    /** Same directory JavalinBootstrap uses. Writable on both a client and a managed server. */
    private static final String LIBS_DIR = "config/devmod/libs";

    /** A truncated or half-written download is worthless; the real artefact is tens of MB. */
    private static final long MIN_PLAUSIBLE_JAR_BYTES = 1_000_000L;

    private static final int DOWNLOAD_TIMEOUT_MS = 60_000;
    private static final int READ_TIMEOUT_MS = 120_000;

    private static Boolean available = null;
    private static boolean downloadAttempted = false;

    /** Non-null once the driver has been loaded, from wherever it was found. */
    @Nullable
    private static Driver driverInstance = null;

    /** Kept so the loader is not collected while connections created from it are still open. */
    @Nullable
    private static URLClassLoader driverClassLoader = null;

    private DuckDBBootstrap() {}

    /**
     * Check whether the DuckDB driver is usable right now.
     *
     * <p>Does not download: call {@link #ensureAvailable(Path)} for that.
     *
     * @return true if the driver has been loaded, or is present on the classpath
     */
    public static boolean isAvailable() {
        if (driverInstance != null) {
            return true;
        }
        if (available == null) {
            try {
                // JNA needs a writable temp directory before the driver's static init runs.
                ensureTempDirectory();

                Class<?> driverClass = Class.forName(DUCKDB_DRIVER_CLASS);
                driverInstance = (Driver) driverClass.getDeclaredConstructor().newInstance();
                available = Boolean.TRUE;
                LOGGER.info("[DuckDB] Driver found in classpath");
            } catch (ClassNotFoundException e) {
                available = Boolean.FALSE;
                LOGGER.warn("[DuckDB] Driver not found in classpath");
            } catch (NoClassDefFoundError | UnsatisfiedLinkError | ExceptionInInitializerError e) {
                available = Boolean.FALSE;
                LOGGER.error("[DuckDB] Native library initialization failed: {}", e.getMessage());
            } catch (ReflectiveOperationException e) {
                available = Boolean.FALSE;
                LOGGER.error("[DuckDB] Driver class found but not instantiable: {}", e.getMessage());
            }
        }
        return Boolean.TRUE.equals(available);
    }

    /**
     * The loaded driver, or null when DuckDB is unavailable.
     *
     * <p>Callers must use this instead of {@code DriverManager}: a driver loaded from our own
     * {@link URLClassLoader} is invisible to {@code DriverManager}, which only offers drivers
     * visible to the caller's classloader. That is the whole reason this accessor exists.
     *
     * @return the driver instance, or null if DuckDB could not be made available
     */
    @Nullable
    public static Driver getDriver() {
        if (driverInstance == null) {
            // Resolves a driver already on the classpath, which is how it is present under test
            // and in a dev run (localRuntime). Without this the accessor would answer null until
            // somebody happened to call isAvailable() first, and a caller holding a perfectly
            // good classpath driver would be told there is none.
            isAvailable();
        }
        return driverInstance;
    }

    /**
     * Ensure JNA temp directory is set for native library extraction.
     * DuckDB uses JNA internally which requires a writable temp directory.
     */
    private static void ensureTempDirectory() {
        // Check if jna.tmpdir is already set
        String jnaTmpDir = System.getProperty("jna.tmpdir");
        if (jnaTmpDir != null && !jnaTmpDir.isEmpty()) {
            Path tmpPath = Path.of(jnaTmpDir);
            if (Files.isDirectory(tmpPath) && Files.isWritable(tmpPath)) {
                return; // Already configured correctly
            }
        }

        // Try to use java.io.tmpdir
        String javaTmpDir = System.getProperty("java.io.tmpdir");
        if (javaTmpDir != null && !javaTmpDir.isEmpty()) {
            try {
                Path tmpPath = Path.of(javaTmpDir);
                if (Files.isDirectory(tmpPath) && Files.isWritable(tmpPath)) {
                    // Create a dedicated subdirectory for DuckDB
                    Path duckDbTmpDir = tmpPath.resolve("duckdb-native");
                    Files.createDirectories(duckDbTmpDir);
                    System.setProperty("jna.tmpdir", duckDbTmpDir.toAbsolutePath().toString());
                    LOGGER.info("[DuckDB] Set jna.tmpdir to: {}", duckDbTmpDir);
                    return;
                }
            } catch (IOException e) {
                LOGGER.warn("[DuckDB] Could not create temp directory in java.io.tmpdir: {}", e.getMessage());
            }
        }

        // Fallback: try user home directory
        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.isEmpty()) {
            try {
                Path homeTmpDir = Path.of(userHome, ".duckdb-tmp");
                Files.createDirectories(homeTmpDir);
                System.setProperty("jna.tmpdir", homeTmpDir.toAbsolutePath().toString());
                LOGGER.info("[DuckDB] Set jna.tmpdir to fallback: {}", homeTmpDir);
            } catch (IOException e) {
                LOGGER.error("[DuckDB] Could not create temp directory in user home: {}", e.getMessage());
            }
        }
    }

    /**
     * Ensure DuckDB is available, downloading it if necessary.
     *
     * <p>If the driver is not already on the classpath this method will:
     * <ol>
     *   <li>detect the platform (linux_amd64, windows_amd64, macos_universal, ...);</li>
     *   <li>download the matching JAR from Maven Central into {@code config/devmod/libs/};</li>
     *   <li>load it through a dedicated URLClassLoader.</li>
     * </ol>
     *
     * <p>Unlike the previous implementation, a successful download is usable immediately: the
     * return value is true and no restart is required.
     *
     * @param gameDir the Minecraft game directory
     * @return true if DuckDB is ready to use
     */
    public static boolean ensureAvailable(Path gameDir) {
        if (isAvailable()) {
            return true;
        }

        // One attempt per session: a failing download should not be retried on every caller.
        if (downloadAttempted) {
            return false;
        }
        downloadAttempted = true;

        String platform = detectPlatform();
        if (platform == null) {
            LOGGER.error("[DuckDB] Unsupported platform: {} / {}",
                System.getProperty("os.name"), System.getProperty("os.arch"));
            return false;
        }

        String jarFileName = "duckdb_jdbc-" + DUCKDB_VERSION + "-" + platform + ".jar";
        Path libsDir = gameDir.resolve(LIBS_DIR);
        Path targetPath = libsDir.resolve(jarFileName);

        try {
            Files.createDirectories(libsDir);

            if (Files.exists(targetPath) && Files.size(targetPath) >= MIN_PLAUSIBLE_JAR_BYTES) {
                LOGGER.info("[DuckDB] Using cached driver at {}", targetPath);
            } else {
                LOGGER.info("[DuckDB] Downloading {} from Maven Central...", jarFileName);

                // Download beside the target, then move into place, so an interrupted download
                // never leaves a half-written JAR that the next boot would treat as cached.
                Path partial = libsDir.resolve(jarFileName + ".part");
                downloadFile(MAVEN_BASE_URL + jarFileName, partial);

                long size = Files.size(partial);
                if (size < MIN_PLAUSIBLE_JAR_BYTES) {
                    Files.deleteIfExists(partial);
                    LOGGER.error("[DuckDB] Downloaded file too small ({} bytes), aborting", size);
                    return false;
                }

                Files.move(partial, targetPath, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("[DuckDB] Downloaded {} ({} MB)", jarFileName, size / (1024 * 1024));
            }

            return loadDriverFrom(targetPath);

        } catch (IOException e) {
            LOGGER.error("[DuckDB] Failed to provision driver: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Load the driver from a JAR through a dedicated classloader.
     *
     * @param jarPath the driver JAR
     * @return true if the driver was loaded and instantiated
     */
    private static boolean loadDriverFrom(Path jarPath) {
        ensureTempDirectory();
        try {
            URLClassLoader loader = new URLClassLoader(
                new java.net.URL[] { jarPath.toUri().toURL() },
                DuckDBBootstrap.class.getClassLoader());

            Class<?> driverClass = Class.forName(DUCKDB_DRIVER_CLASS, true, loader);
            driverInstance = (Driver) driverClass.getDeclaredConstructor().newInstance();
            driverClassLoader = loader;
            available = Boolean.TRUE;

            LOGGER.info("[DuckDB] Driver loaded from {}", jarPath);
            return true;

        } catch (MalformedURLException e) {
            LOGGER.error("[DuckDB] Bad driver path {}: {}", jarPath, e.getMessage());
        } catch (ClassNotFoundException e) {
            LOGGER.error("[DuckDB] {} not present in {}", DUCKDB_DRIVER_CLASS, jarPath);
        } catch (ReflectiveOperationException e) {
            LOGGER.error("[DuckDB] Driver class not instantiable: {}", e.getMessage());
        } catch (NoClassDefFoundError | UnsatisfiedLinkError | ExceptionInInitializerError e) {
            LOGGER.error("[DuckDB] Native library initialization failed: {}", e.getMessage());
        }

        available = Boolean.FALSE;
        return false;
    }

    /**
     * Detect the platform classifier used by DuckDB's Maven artifacts.
     *
     * @return the classifier, or null when this platform has no published build
     */
    @Nullable
    private static String detectPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);

        String osName;
        if (os.contains("linux")) {
            osName = "linux";
        } else if (os.contains("mac") || os.contains("darwin")) {
            osName = "osx";
        } else if (os.contains("win")) {
            osName = "windows";
        } else {
            return null;
        }

        String archName;
        if (osName.equals("osx")) {
            // macOS uses universal binary
            archName = "universal";
        } else if (arch.equals("amd64") || arch.equals("x86_64")) {
            archName = "amd64";
        } else if (arch.equals("aarch64") || arch.equals("arm64")) {
            archName = "arm64";
        } else {
            return null;
        }

        // DuckDB uses different naming: macos_universal, linux_amd64, windows_amd64
        if (osName.equals("osx")) {
            return "macos_universal";
        }
        return osName + "_" + archName;
    }

    /**
     * Download a file over HTTP into the given path.
     *
     * @param urlString the source URL
     * @param targetPath where to write it
     * @throws IOException if the transfer fails or the server answers with anything but 200
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

            long contentLength = connection.getContentLengthLong();
            LOGGER.info("[DuckDB] Downloading {} bytes...", contentLength > 0 ? contentLength : "unknown");

            try (InputStream in = connection.getInputStream()) {
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

        } finally {
            connection.disconnect();
        }
    }
}
