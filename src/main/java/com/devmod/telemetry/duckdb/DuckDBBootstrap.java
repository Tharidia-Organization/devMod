package com.devmod.telemetry.duckdb;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bootstrap utility for DuckDB JDBC driver.
 *
 * Automatically downloads the platform-specific DuckDB JDBC driver
 * if not available in the classpath. The JAR is saved to the mods/
 * directory and will be loaded by NeoForge on the next server restart.
 */
public final class DuckDBBootstrap {
    private static final Logger LOGGER = LoggerFactory.getLogger(DuckDBBootstrap.class);

    private static final String DUCKDB_VERSION = "1.4.3.0";
    private static final String MAVEN_BASE_URL =
        "https://repo1.maven.org/maven2/org/duckdb/duckdb_jdbc/" + DUCKDB_VERSION + "/";
    private static final String DUCKDB_DRIVER_CLASS = "org.duckdb.DuckDBDriver";
    private static final String DOWNLOADED_JAR_NAME = "duckdb-jdbc-" + DUCKDB_VERSION + ".jar";

    // Expected SHA-256 checksums for verification (optional but recommended)
    private static final int DOWNLOAD_TIMEOUT_MS = 60_000;
    private static final int READ_TIMEOUT_MS = 120_000;

    @Nullable
    private static Boolean available = null;
    private static boolean downloadAttempted = false;

    private DuckDBBootstrap() {}

    /**
     * Check if DuckDB is available in the classpath.
     *
     * @return true if DuckDB driver can be loaded
     */
    public static boolean isAvailable() {
        if (available == null) {
            try {
                Class.forName(DUCKDB_DRIVER_CLASS);
                available = Boolean.TRUE;
                LOGGER.info("[DuckDB] Driver found in classpath");
            } catch (ClassNotFoundException e) {
                available = Boolean.FALSE;
                LOGGER.warn("[DuckDB] Driver not found in classpath");
            }
        }
        return Boolean.TRUE.equals(available);
    }

    /**
     * Ensure DuckDB is available, downloading if necessary.
     *
     * If DuckDB is not in the classpath, this method will:
     * 1. Detect the current platform (linux_amd64, windows_amd64, macos_universal)
     * 2. Download the appropriate JAR from Maven Central
     * 3. Save it to the mods/ directory
     * 4. Return false to indicate a restart is required
     *
     * @param gameDir the Minecraft game directory (parent of mods/)
     * @return true if DuckDB is ready to use, false if a restart is required
     */
    public static boolean ensureAvailable(Path gameDir) {
        // Already available
        if (isAvailable()) {
            return true;
        }

        // Already tried downloading this session
        if (downloadAttempted) {
            LOGGER.debug("[DuckDB] Download already attempted this session");
            return false;
        }
        downloadAttempted = true;

        // Check if JAR already exists in mods/ (downloaded but not yet loaded)
        Path modsDir = gameDir.resolve("mods");
        Path targetPath = modsDir.resolve(DOWNLOADED_JAR_NAME);

        if (Files.exists(targetPath)) {
            // Check if the JAR has the correct manifest
            if (hasNeoForgeManifest(targetPath)) {
                LOGGER.info("[DuckDB] JAR already downloaded at {}", targetPath);
                logRestartRequired();
                return false;
            } else {
                // JAR exists but without NeoForge manifest - reprocess it
                LOGGER.info("[DuckDB] Existing JAR missing NeoForge manifest, reprocessing...");
                try {
                    Path tempFile = modsDir.resolve(DOWNLOADED_JAR_NAME + ".repack");
                    repackageWithNeoForgeManifest(targetPath, tempFile);
                    Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.info("[DuckDB] JAR repackaged successfully");
                    logRestartRequired();
                    return false;
                } catch (IOException e) {
                    LOGGER.error("[DuckDB] Failed to repackage JAR: {}", e.getMessage());
                    // Continue to re-download
                }
            }
        }

        // Detect platform and download
        String platform = detectPlatform();
        if (platform == null) {
            LOGGER.error("[DuckDB] Unsupported platform: {} / {}",
                System.getProperty("os.name"), System.getProperty("os.arch"));
            return false;
        }

        String jarFileName = "duckdb_jdbc-" + DUCKDB_VERSION + "-" + platform + ".jar";
        String downloadUrl = MAVEN_BASE_URL + jarFileName;

        LOGGER.info("[DuckDB] Downloading {} from Maven Central...", jarFileName);

        try {
            // Ensure mods directory exists
            Files.createDirectories(modsDir);

            // Download to temp file
            Path downloadedFile = modsDir.resolve(DOWNLOADED_JAR_NAME + ".download");
            Path repackagedFile = modsDir.resolve(DOWNLOADED_JAR_NAME + ".tmp");
            downloadFile(downloadUrl, downloadedFile);

            // Verify download size (basic sanity check)
            long size = Files.size(downloadedFile);
            if (size < 1_000_000) { // Less than 1 MB is suspicious
                Files.deleteIfExists(downloadedFile);
                LOGGER.error("[DuckDB] Downloaded file too small ({} bytes), aborting", size);
                return false;
            }

            // Repackage JAR with NeoForge manifest attributes
            repackageWithNeoForgeManifest(downloadedFile, repackagedFile);

            // Clean up original download
            Files.deleteIfExists(downloadedFile);

            // Move repackaged JAR to final location
            Files.move(repackagedFile, targetPath, StandardCopyOption.REPLACE_EXISTING);

            long sizeMB = Files.size(targetPath) / (1024 * 1024);
            LOGGER.info("[DuckDB] Successfully downloaded {} ({} MB)", DOWNLOADED_JAR_NAME, sizeMB);

            logRestartRequired();
            return false;

        } catch (IOException e) {
            LOGGER.error("[DuckDB] Failed to download: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Detect the current platform for DuckDB native library selection.
     *
     * @return platform classifier (e.g., "linux_amd64") or null if unsupported
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

        // DuckDB uses different naming: osx_universal, linux_amd64, windows_amd64
        if (osName.equals("osx")) {
            return "macos_universal";
        }
        return osName + "_" + archName;
    }

    /**
     * Download a file from URL to the specified path.
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

    /**
     * Check if a JAR file has the NeoForge FMLModType manifest attribute.
     */
    private static boolean hasNeoForgeManifest(Path jarPath) {
        try (JarInputStream jarIn = new JarInputStream(Files.newInputStream(jarPath))) {
            Manifest manifest = jarIn.getManifest();
            if (manifest == null) {
                return false;
            }
            String fmlModType = manifest.getMainAttributes().getValue("FMLModType");
            return fmlModType != null && !fmlModType.isEmpty();
        } catch (IOException e) {
            LOGGER.warn("[DuckDB] Could not read JAR manifest: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Repackage a JAR file to add NeoForge-compatible manifest attributes.
     * This is required because NeoForge only loads JARs from mods/ if they have
     * FMLModType in their manifest.
     *
     * @param sourceJar the original JAR file
     * @param targetJar the output JAR file with modified manifest
     */
    private static void repackageWithNeoForgeManifest(Path sourceJar, Path targetJar) throws IOException {
        LOGGER.info("[DuckDB] Adding NeoForge manifest attributes...");

        try (JarInputStream jarIn = new JarInputStream(Files.newInputStream(sourceJar))) {
            // Get original manifest or create new one
            Manifest originalManifest = jarIn.getManifest();
            Manifest newManifest = originalManifest != null ? new Manifest(originalManifest) : new Manifest();

            // Add NeoForge attributes
            Attributes mainAttrs = newManifest.getMainAttributes();
            if (!mainAttrs.containsKey(Attributes.Name.MANIFEST_VERSION)) {
                mainAttrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
            }
            mainAttrs.putValue("FMLModType", "GAMELIBRARY");
            mainAttrs.putValue("Automatic-Module-Name", "org.duckdb.jdbc");

            // Write new JAR with modified manifest
            try (OutputStream fileOut = Files.newOutputStream(targetJar);
                 JarOutputStream jarOut = new JarOutputStream(fileOut, newManifest)) {

                byte[] buffer = new byte[8192];
                JarEntry entry;

                while ((entry = jarIn.getNextJarEntry()) != null) {
                    // Skip the original manifest - we already included our modified one
                    if (entry.getName().equalsIgnoreCase("META-INF/MANIFEST.MF")) {
                        continue;
                    }

                    jarOut.putNextEntry(new JarEntry(entry.getName()));

                    int bytesRead;
                    while ((bytesRead = jarIn.read(buffer)) != -1) {
                        jarOut.write(buffer, 0, bytesRead);
                    }

                    jarOut.closeEntry();
                }
            }
        }

        LOGGER.info("[DuckDB] JAR repackaged with FMLModType: GAMELIBRARY");
    }

    /**
     * Log a prominent message about restart requirement.
     */
    private static void logRestartRequired() {
        LOGGER.warn("");
        LOGGER.warn("========================================================");
        LOGGER.warn("  DUCKDB JDBC DRIVER DOWNLOADED SUCCESSFULLY!");
        LOGGER.warn("  ");
        LOGGER.warn("  Please RESTART THE SERVER to complete installation.");
        LOGGER.warn("  ");
        LOGGER.warn("  Mailbox and Telemetry features will be available");
        LOGGER.warn("  after the restart.");
        LOGGER.warn("========================================================");
        LOGGER.warn("");
    }

    /**
     * Get the expected JAR file path in the mods directory.
     */
    public static Path getExpectedJarPath(Path gameDir) {
        return gameDir.resolve("mods").resolve(DOWNLOADED_JAR_NAME);
    }

    /**
     * Check if the JAR has been downloaded but not yet loaded.
     */
    public static boolean isDownloadedButNotLoaded(Path gameDir) {
        return !isAvailable() && Files.exists(getExpectedJarPath(gameDir));
    }
}
