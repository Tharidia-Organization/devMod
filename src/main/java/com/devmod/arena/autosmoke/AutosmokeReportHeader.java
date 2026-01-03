package com.devmod.arena.autosmoke;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutosmokeReportHeader {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutosmokeReportHeader.class);

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
                    .withZone(ZoneId.systemDefault());

    /** Build properties file (generated at build time) */
    private static final String BUILD_PROPERTIES_PATH = "/build.properties";

    /** Cached build info */
    private static volatile BuildInfo cachedBuildInfo;

    /**
     * Captures the current context for a report header
     *
     * @return The captured report header
     */
    public static ReportHeader capture() {
        return capture(null);
    }

    /**
     * Captures the current context with additional config for hashing
     *
     * @param configContent Configuration content to hash (can be null)
     * @return The captured report header
     */
    public static ReportHeader capture(String configContent) {
        BuildInfo buildInfo = getBuildInfo();
        String configHash = configContent != null ? computeHash(configContent) : "N/A";

        return new ReportHeader(
            TIMESTAMP_FORMAT.format(Instant.now()),
            buildInfo.gitCommit(),
            buildInfo.gitBranch(),
            buildInfo.buildTime(),
            buildInfo.modVersion(),
            configHash,
            System.getProperty("java.version", "unknown"),
            System.getProperty("os.name", "unknown"),
            Runtime.getRuntime().maxMemory() / (1024 * 1024),
            Runtime.getRuntime().availableProcessors()
        );
    }

    /**
     * Gets build information from properties file
     */
    private static BuildInfo getBuildInfo() {
        if (cachedBuildInfo != null) {
            return cachedBuildInfo;
        }

        Properties props = new Properties();
        try (InputStream is = AutosmokeReportHeader.class.getResourceAsStream(BUILD_PROPERTIES_PATH)) {
            if (is != null) {
                props.load(is);
            } else {
                LOGGER.debug("Build properties not found, using defaults");
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to load build properties: {}", e.getMessage());
        }

        cachedBuildInfo = new BuildInfo(
            props.getProperty("git.commit", "unknown"),
            props.getProperty("git.branch", "unknown"),
            props.getProperty("build.time", "unknown"),
            props.getProperty("mod.version", "unknown")
        );

        return cachedBuildInfo;
    }

    /**
     * Computes SHA-256 hash of content
     */
    private static String computeHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            // Return first 12 chars (short hash)
            return hexString.substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            LOGGER.error("SHA-256 not available", e);
            return "error";
        }
    }

    /**
     * Clears cached build info (for testing)
     */
    public static void clearCache() {
        cachedBuildInfo = null;
    }

    /**
     * Build information record
     */
    public record BuildInfo(
        String gitCommit,
        String gitBranch,
        String buildTime,
        String modVersion
    ) {}

    /**
     * Report header record with full context
     */
    public record ReportHeader(
        String timestamp,
        String gitCommit,
        String gitBranch,
        String buildTime,
        String modVersion,
        String configHash,
        String javaVersion,
        String osName,
        long maxMemoryMb,
        int availableProcessors
    ) {
        /**
         * Formats the header as a multi-line string for reports
         */
        public String format() {
            return String.format(
                "================================================================================%n" +
                "AUTOSMOKE REPORT%n" +
                "================================================================================%n" +
                "Timestamp:     %s%n" +
                "Git Commit:    %s%n" +
                "Git Branch:    %s%n" +
                "Build Time:    %s%n" +
                "Mod Version:   %s%n" +
                "Config Hash:   %s%n" +
                "Java Version:  %s%n" +
                "OS:            %s%n" +
                "Max Memory:    %d MB%n" +
                "CPUs:          %d%n" +
                "================================================================================%n",
                timestamp, gitCommit, gitBranch, buildTime, modVersion,
                configHash, javaVersion, osName, maxMemoryMb, availableProcessors
            );
        }

        /**
         * Formats the header as a single-line string for logs
         */
        public String formatCompact() {
            return String.format(
                "commit=%s branch=%s version=%s config=%s java=%s",
                gitCommit, gitBranch, modVersion, configHash, javaVersion
            );
        }

        /**
         * Formats as JSON for structured logging
         */
        public String formatJson() {
            return String.format("""
                {"timestamp":"%s","git_commit":"%s","git_branch":"%s","build_time":"%s","mod_version":"%s","config_hash":"%s","java_version":"%s","os":"%s","max_memory_mb":%d,"cpus":%d}""",
                timestamp, gitCommit, gitBranch, buildTime, modVersion,
                configHash, javaVersion, osName, maxMemoryMb, availableProcessors
            );
        }
    }
}
