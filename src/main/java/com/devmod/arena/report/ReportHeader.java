package com.devmod.arena.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;

public record ReportHeader(
    /** DD34: Git commit hash (short) */
    String gitCommit,
    /** DD34: Full git commit hash */
    String gitCommitFull,
    /** Git branch name */
    String gitBranch,
    /** DD34: Configuration hash for reproducibility */
    String configHash,
    /** Build version */
    String version,
    /** Build timestamp */
    Instant buildTimestamp,
    /** Report generation timestamp */
    Instant generatedAt,
    /** Environment (dev, staging, prod) */
    String environment,
    /** Server instance ID */
    String instanceId,
    /** Additional metadata */
    Map<String, String> metadata
) {
    /**
     * Creates a ReportHeader with current timestamp.
     */
    public ReportHeader {
        if (generatedAt == null) {
            generatedAt = Instant.now();
        }
        if (metadata == null) {
            metadata = Map.of();
        }
    }

    /**
     * Builder for ReportHeader.
     */
    public static class Builder {
        private String gitCommit = "unknown";
        private String gitCommitFull = "unknown";
        private String gitBranch = "unknown";
        private String configHash = "unknown";
        private String version = "0.0.0";
        private Instant buildTimestamp = Instant.now();
        private Instant generatedAt = Instant.now();
        private String environment = "unknown";
        private String instanceId = "unknown";
        private Map<String, String> metadata = Map.of();

        public Builder gitCommit(String commit) {
            this.gitCommitFull = commit;
            this.gitCommit = commit != null && commit.length() > 7 ? commit.substring(0, 7) : commit;
            return this;
        }

        public Builder gitBranch(String branch) {
            this.gitBranch = branch;
            return this;
        }

        public Builder configHash(String hash) {
            this.configHash = hash;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder buildTimestamp(Instant timestamp) {
            this.buildTimestamp = timestamp;
            return this;
        }

        public Builder generatedAt(Instant timestamp) {
            this.generatedAt = timestamp;
            return this;
        }

        public Builder environment(String env) {
            this.environment = env;
            return this;
        }

        public Builder instanceId(String id) {
            this.instanceId = id;
            return this;
        }

        public Builder metadata(Map<String, String> meta) {
            this.metadata = meta;
            return this;
        }

        public ReportHeader build() {
            return new ReportHeader(
                gitCommit, gitCommitFull, gitBranch, configHash,
                version, buildTimestamp, generatedAt, environment,
                instanceId, metadata
            );
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Loads header from build.properties file.
     * DD34: Read git commit and config hash from build properties.
     *
     * @param propertiesPath path to build.properties
     * @return populated Builder
     */
    public static Builder fromBuildProperties(Path propertiesPath) {
        Builder builder = new Builder();

        if (Files.exists(propertiesPath)) {
            try {
                Properties props = new Properties();
                props.load(Files.newInputStream(propertiesPath));

                builder.gitCommit(props.getProperty("git.commit", "unknown"));
                builder.gitBranch(props.getProperty("git.branch", "unknown"));
                builder.version(props.getProperty("build.version", "0.0.0"));

                String buildTime = props.getProperty("build.timestamp");
                if (buildTime != null) {
                    try {
                        builder.buildTimestamp(Instant.parse(buildTime));
                    } catch (Exception e) {
                        // Use default
                    }
                }
            } catch (IOException e) {
                // Use defaults
            }
        }

        return builder;
    }

    /**
     * Calculates configuration hash.
     * DD34: Hash of config for reproducibility.
     *
     * @param configContent the configuration content
     * @return SHA-256 hash (first 16 chars)
     */
    public static String calculateConfigHash(String configContent) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(configContent.getBytes(StandardCharsets.UTF_8));
            String fullHash = HexFormat.of().formatHex(hash);
            return fullHash.substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return "error";
        }
    }

    /**
     * Calculates configuration hash from a file.
     *
     * @param configPath path to configuration file
     * @return SHA-256 hash (first 16 chars)
     */
    public static String calculateConfigHash(Path configPath) {
        try {
            String content = Files.readString(configPath);
            return calculateConfigHash(content);
        } catch (IOException e) {
            return "file-not-found";
        }
    }

    /**
     * Converts header to JSON string.
     *
     * @return JSON representation
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"gitCommit\": \"").append(gitCommit).append("\",\n");
        sb.append("  \"gitCommitFull\": \"").append(gitCommitFull).append("\",\n");
        sb.append("  \"gitBranch\": \"").append(gitBranch).append("\",\n");
        sb.append("  \"configHash\": \"").append(configHash).append("\",\n");
        sb.append("  \"version\": \"").append(version).append("\",\n");
        sb.append("  \"buildTimestamp\": \"").append(buildTimestamp).append("\",\n");
        sb.append("  \"generatedAt\": \"").append(generatedAt).append("\",\n");
        sb.append("  \"environment\": \"").append(environment).append("\",\n");
        sb.append("  \"instanceId\": \"").append(instanceId).append("\"");

        if (!metadata.isEmpty()) {
            sb.append(",\n  \"metadata\": {\n");
            int i = 0;
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                sb.append("    \"").append(entry.getKey()).append("\": \"")
                  .append(entry.getValue()).append("\"");
                if (++i < metadata.size()) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append("  }");
        }

        sb.append("\n}");
        return sb.toString();
    }

    /**
     * Converts header to single-line summary.
     *
     * @return summary string
     */
    public String toSummary() {
        return String.format("v%s [%s@%s] config:%s env:%s",
            version, gitCommit, gitBranch, configHash, environment);
    }

    /**
     * Checks if this is a development build.
     *
     * @return true if development environment
     */
    public boolean isDevelopment() {
        return "dev".equalsIgnoreCase(environment) ||
               "development".equalsIgnoreCase(environment) ||
               "local".equalsIgnoreCase(environment);
    }

    /**
     * Checks if this is a production build.
     *
     * @return true if production environment
     */
    public boolean isProduction() {
        return "prod".equalsIgnoreCase(environment) ||
               "production".equalsIgnoreCase(environment);
    }
}
