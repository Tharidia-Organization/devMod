package com.devmod.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PathSanitizer {
    private static final Logger LOGGER = LoggerFactory.getLogger(PathSanitizer.class);

    /**
     * Gets the set of allowed base directories dynamically from ConfigPaths.
     * This ensures paths work correctly in both dev and production environments.
     */
    private static Set<Path> getAllowedBaseDirs() {
        Set<Path> dirs = new HashSet<>();
        try {
            dirs.add(ConfigPaths.getConfigDir());
            dirs.add(ConfigPaths.getTelemetryExportDir());
            dirs.add(ConfigPaths.getQAReportsDir());
            dirs.add(ConfigPaths.getGameDir().resolve("logs"));
        } catch (Exception e) {
            // FMLPaths may not be ready during early class loading
            LOGGER.debug("Could not get ConfigPaths directories: {}", e.getMessage());
        }
        return dirs;
    }

    /**
     * Checks if a path is within one of the allowed base directories.
     */
    private static boolean isPathInAllowedDirectory(Path path) {
        if (path == null) return false;

        Path absolutePath;
        try {
            absolutePath = path.toAbsolutePath().normalize();
        } catch (Exception e) {
            // Fall back to normalized path if absolute conversion fails
            absolutePath = path.normalize();
        }

        for (Path allowedDir : getAllowedBaseDirs()) {
            if (allowedDir == null) continue;
            try {
                Path absoluteAllowed = allowedDir.toAbsolutePath().normalize();
                if (absolutePath.startsWith(absoluteAllowed)) {
                    return true;
                }
            } catch (Exception e) {
                // Skip this directory if we can't resolve it
                LOGGER.debug("Could not resolve allowed directory: {}", allowedDir);
            }
        }
        return false;
    }

    // Allowed file extensions for reading
    private static final Set<String> ALLOWED_READ_EXTENSIONS = Set.of(
        ".json", ".toml", ".txt", ".log", ".md", ".jsonl", ".csv"
    );

    // Allowed file extensions for writing
    private static final Set<String> ALLOWED_WRITE_EXTENSIONS = Set.of(
        ".json", ".txt", ".md", ".jsonl", ".csv", ".png"
    );

    // Maximum filename length
    private static final int MAX_FILENAME_LENGTH = 255;

    // Maximum path length
    private static final int MAX_PATH_LENGTH = 4096;

    private PathSanitizer() {}

    /**
     * Validate and sanitize a path for reading.
     *
     * @param path The path to validate
     * @return Sanitized path or null if invalid
     */
    public static Path sanitizeForRead(Path path) {
        return sanitize(path, ALLOWED_READ_EXTENSIONS, false);
    }

    /**
     * Validate and sanitize a path for reading.
     *
     * @param pathStr The path string to validate
     * @return Sanitized path or null if invalid
     */
    public static Path sanitizeForRead(String pathStr) {
        if (pathStr == null || pathStr.isEmpty()) return null;
        try {
            return sanitizeForRead(Paths.get(pathStr));
        } catch (Exception e) {
            LOGGER.warn("Invalid path string for read: {}", pathStr);
            return null;
        }
    }

    /**
     * Validate and sanitize a path for writing.
     *
     * @param path The path to validate
     * @return Sanitized path or null if invalid
     */
    public static Path sanitizeForWrite(Path path) {
        return sanitize(path, ALLOWED_WRITE_EXTENSIONS, true);
    }

    /**
     * Validate and sanitize a path for writing.
     *
     * @param pathStr The path string to validate
     * @return Sanitized path or null if invalid
     */
    public static Path sanitizeForWrite(String pathStr) {
        if (pathStr == null || pathStr.isEmpty()) return null;
        try {
            return sanitizeForWrite(Paths.get(pathStr));
        } catch (Exception e) {
            LOGGER.warn("Invalid path string for write: {}", pathStr);
            return null;
        }
    }

    /**
     * Core sanitization logic.
     */
    private static Path sanitize(Path path, Set<String> allowedExtensions, boolean forWrite) {
        if (path == null) return null;

        try {
            // Convert to string for analysis
            String pathStr = path.toString();

            // Check path length
            if (pathStr.length() > MAX_PATH_LENGTH) {
                LOGGER.warn("Path too long: {} chars", pathStr.length());
                return null;
            }

            // Check for null bytes (injection attack)
            if (pathStr.contains("\0")) {
                LOGGER.warn("Path contains null byte: {}", pathStr);
                return null;
            }

            // Normalize the path to resolve any . or ..
            Path normalized = path.normalize();
            String normalizedStr = normalized.toString();

            // Check for directory traversal after normalization
            if (normalizedStr.contains("..")) {
                LOGGER.warn("Directory traversal attempt detected: {}", pathStr);
                return null;
            }

            // Verify the path is within an allowed base directory
            boolean allowed = isPathInAllowedDirectory(normalized);

            if (!allowed) {
                LOGGER.warn("Path not in allowed directory: {}", normalizedStr);
                return null;
            }

            // Check filename length
            Path fileName = normalized.getFileName();
            if (fileName != null && fileName.toString().length() > MAX_FILENAME_LENGTH) {
                LOGGER.warn("Filename too long: {}", fileName);
                return null;
            }

            // Check file extension
            if (fileName != null) {
                String name = fileName.toString().toLowerCase(Locale.ROOT);
                boolean hasValidExtension = false;
                for (String ext : allowedExtensions) {
                    if (name.endsWith(ext)) {
                        hasValidExtension = true;
                        break;
                    }
                }
                if (!hasValidExtension) {
                    LOGGER.warn("Invalid file extension: {}", name);
                    return null;
                }
            }

            // For writes, ensure parent directory exists or can be created
            if (forWrite) {
                Path parent = normalized.getParent();
                if (parent != null && !Files.exists(parent)) {
                    // We don't create here, just validate it could be created
                    // The actual creation should be done by the caller
                }
            }

            return normalized;

        } catch (Exception e) {
            LOGGER.warn("Path sanitization failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Validate a filename (without path) for use in file operations.
     *
     * @param filename The filename to validate
     * @return Sanitized filename or null if invalid
     */
    public static String sanitizeFilename(String filename) {
        if (filename == null || filename.isEmpty()) return null;

        // Check length
        if (filename.length() > MAX_FILENAME_LENGTH) {
            return null;
        }

        // Remove any path separators
        if (filename.contains("/") || filename.contains("\\")) {
            return null;
        }

        // Remove null bytes
        if (filename.contains("\0")) {
            return null;
        }

        // Only allow alphanumeric, dash, underscore, dot
        if (!filename.matches("^[a-zA-Z0-9._-]+$")) {
            // Try to sanitize by removing invalid characters
            String sanitized = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
            if (sanitized.isEmpty()) {
                return null;
            }
            LOGGER.debug("Filename sanitized: {} -> {}", filename, sanitized);
            return sanitized;
        }

        return filename;
    }

    /**
     * Build a safe path within an allowed directory.
     *
     * @param baseDir The allowed base directory (must be from ConfigPaths)
     * @param subPath The sub-path within the directory
     * @return Safe resolved path or null if invalid
     */
    public static Path buildSafePath(Path baseDir, String... subPath) {
        if (baseDir == null || !isPathInAllowedDirectory(baseDir)) {
            LOGGER.warn("Base directory not allowed: {}", baseDir);
            return null;
        }

        try {
            Path result = baseDir;
            for (String part : subPath) {
                // Sanitize each path component
                String sanitized = sanitizeFilename(part);
                if (sanitized == null) {
                    return null;
                }
                result = result.resolve(sanitized);
            }
            Path normalized = result.normalize();

            // sanitizeFilename's character allowlist admits "." and "..", so the normalized
            // result can still escape the base directory - re-check it.
            if (!normalized.toAbsolutePath().normalize()
                    .startsWith(baseDir.toAbsolutePath().normalize())) {
                LOGGER.warn("Sub-path escaped base directory: {}", normalized);
                return null;
            }
            return normalized;
        } catch (Exception e) {
            LOGGER.warn("Failed to build safe path: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Safely create directories if they don't exist.
     *
     * @param path The directory path to create
     * @return true if directories exist or were created, false on failure
     */
    public static boolean createDirectoriesSafe(Path path) {
        if (path == null) return false;

        // Validate path is in allowed directory
        if (!isPathInAllowedDirectory(path)) {
            LOGGER.warn("Cannot create directory outside allowed paths: {}", path);
            return false;
        }

        try {
            Files.createDirectories(path);
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to create directories: {}", e.getMessage());
            return false;
        }
    }
}
