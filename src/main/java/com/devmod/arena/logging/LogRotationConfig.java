package com.devmod.arena.logging;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

public record LogRotationConfig(
    Path logDirectory,
    String logPrefix,
    Duration maxAge,
    long maxSizeBytes,
    boolean compressionEnabled,
    String compressionFormat,
    Duration rotationCheckInterval
) {

    /**
     * Default max age for log files (DD17: 14 days).
     */
    public static final Duration DEFAULT_MAX_AGE = Duration.ofDays(14);

    /**
     * Default max size per log file (DD17: 500MB).
     */
    public static final long DEFAULT_MAX_SIZE_BYTES = 500L * 1024 * 1024;

    /**
     * Default compression format.
     */
    public static final String DEFAULT_COMPRESSION_FORMAT = "gz";

    /**
     * Default rotation check interval.
     */
    public static final Duration DEFAULT_ROTATION_CHECK_INTERVAL = Duration.ofHours(1);

    /**
     * Compact constructor with validation.
     */
    public LogRotationConfig {
        Objects.requireNonNull(logDirectory, "logDirectory must not be null");
        Objects.requireNonNull(logPrefix, "logPrefix must not be null");

        if (maxAge == null) {
            maxAge = DEFAULT_MAX_AGE;
        }
        if (maxSizeBytes <= 0) {
            maxSizeBytes = DEFAULT_MAX_SIZE_BYTES;
        }
        if (compressionFormat == null || compressionFormat.isBlank()) {
            compressionFormat = DEFAULT_COMPRESSION_FORMAT;
        }
        if (rotationCheckInterval == null) {
            rotationCheckInterval = DEFAULT_ROTATION_CHECK_INTERVAL;
        }
    }

    /**
     * Creates a configuration with default values.
     */
    public static LogRotationConfig withDefaults(Path logDirectory, String logPrefix) {
        return new LogRotationConfig(
            logDirectory,
            logPrefix,
            DEFAULT_MAX_AGE,
            DEFAULT_MAX_SIZE_BYTES,
            true,
            DEFAULT_COMPRESSION_FORMAT,
            DEFAULT_ROTATION_CHECK_INTERVAL
        );
    }

    /**
     * Creates a builder for custom configuration.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns max age in days.
     */
    public int maxAgeDays() {
        return (int) maxAge.toDays();
    }

    /**
     * Returns max size in megabytes.
     */
    public long maxSizeMb() {
        return maxSizeBytes / (1024 * 1024);
    }

    /**
     * Checks if a file size exceeds the max size.
     */
    public boolean exceedsMaxSize(long fileSizeBytes) {
        return fileSizeBytes >= maxSizeBytes;
    }

    /**
     * Returns the compressed file extension.
     */
    public String compressedExtension() {
        return "." + compressionFormat;
    }

    /**
     * Builder for LogRotationConfig.
     */
    public static class Builder {
        private Path logDirectory;
        private String logPrefix = "arena";
        private Duration maxAge = DEFAULT_MAX_AGE;
        private long maxSizeBytes = DEFAULT_MAX_SIZE_BYTES;
        private boolean compressionEnabled = true;
        private String compressionFormat = DEFAULT_COMPRESSION_FORMAT;
        private Duration rotationCheckInterval = DEFAULT_ROTATION_CHECK_INTERVAL;

        public Builder logDirectory(Path logDirectory) {
            this.logDirectory = logDirectory;
            return this;
        }

        public Builder logPrefix(String logPrefix) {
            this.logPrefix = logPrefix;
            return this;
        }

        public Builder maxAge(Duration maxAge) {
            this.maxAge = maxAge;
            return this;
        }

        public Builder maxAgeDays(int days) {
            this.maxAge = Duration.ofDays(days);
            return this;
        }

        public Builder maxSizeBytes(long maxSizeBytes) {
            this.maxSizeBytes = maxSizeBytes;
            return this;
        }

        public Builder maxSizeMb(long maxSizeMb) {
            this.maxSizeBytes = maxSizeMb * 1024 * 1024;
            return this;
        }

        public Builder compressionEnabled(boolean compressionEnabled) {
            this.compressionEnabled = compressionEnabled;
            return this;
        }

        public Builder compressionFormat(String compressionFormat) {
            this.compressionFormat = compressionFormat;
            return this;
        }

        public Builder rotationCheckInterval(Duration rotationCheckInterval) {
            this.rotationCheckInterval = rotationCheckInterval;
            return this;
        }

        public LogRotationConfig build() {
            Objects.requireNonNull(logDirectory, "logDirectory must be set");
            return new LogRotationConfig(
                logDirectory,
                logPrefix,
                maxAge,
                maxSizeBytes,
                compressionEnabled,
                compressionFormat,
                rotationCheckInterval
            );
        }
    }
}
