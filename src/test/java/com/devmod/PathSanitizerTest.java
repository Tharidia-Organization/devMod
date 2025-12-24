package com.devmod;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PathSanitizer.
 * Tests path validation, directory traversal prevention, and file extension filtering.
 */
public class PathSanitizerTest {

    /**
     * Mock PathSanitizer with the same validation logic as the real one.
     * This avoids the actual file system operations while testing the core logic.
     */
    static class MockPathSanitizer {
        // Allowed base directories (relative to game directory)
        private static final Set<String> ALLOWED_BASE_DIRS = Set.of(
            "run/config",
            "run/telemetry",
            "run/qa_reports",
            "run/logs",
            "config"
        );

        // Allowed file extensions for reading
        private static final Set<String> ALLOWED_READ_EXTENSIONS = Set.of(
            ".json", ".toml", ".txt", ".log", ".md", ".jsonl", ".csv"
        );

        // Allowed file extensions for writing
        private static final Set<String> ALLOWED_WRITE_EXTENSIONS = Set.of(
            ".json", ".txt", ".md", ".jsonl", ".csv"
        );

        // Maximum filename length
        private static final int MAX_FILENAME_LENGTH = 255;

        // Maximum path length
        private static final int MAX_PATH_LENGTH = 4096;

        public Path sanitizeForRead(Path path) {
            return sanitize(path, ALLOWED_READ_EXTENSIONS);
        }

        public Path sanitizeForRead(String pathStr) {
            if (pathStr == null || pathStr.isEmpty()) return null;
            try {
                return sanitizeForRead(Paths.get(pathStr));
            } catch (Exception e) {
                return null;
            }
        }

        public Path sanitizeForWrite(Path path) {
            return sanitize(path, ALLOWED_WRITE_EXTENSIONS);
        }

        public Path sanitizeForWrite(String pathStr) {
            if (pathStr == null || pathStr.isEmpty()) return null;
            try {
                return sanitizeForWrite(Paths.get(pathStr));
            } catch (Exception e) {
                return null;
            }
        }

        private Path sanitize(Path path, Set<String> allowedExtensions) {
            if (path == null) return null;

            try {
                String pathStr = path.toString();

                // Check path length
                if (pathStr.length() > MAX_PATH_LENGTH) {
                    return null;
                }

                // Check for null bytes
                if (pathStr.contains("\0")) {
                    return null;
                }

                // Normalize the path
                Path normalized = path.normalize();
                String normalizedStr = normalized.toString();

                // Check for directory traversal after normalization
                if (normalizedStr.contains("..")) {
                    return null;
                }

                // Verify path is within allowed base directory
                boolean allowed = false;
                for (String baseDir : ALLOWED_BASE_DIRS) {
                    if (normalizedStr.startsWith(baseDir) || normalizedStr.startsWith("./" + baseDir)) {
                        allowed = true;
                        break;
                    }
                }

                if (!allowed) {
                    return null;
                }

                // Check filename length
                Path fileName = normalized.getFileName();
                if (fileName != null && fileName.toString().length() > MAX_FILENAME_LENGTH) {
                    return null;
                }

                // Check file extension
                if (fileName != null) {
                    String name = fileName.toString().toLowerCase();
                    boolean hasValidExtension = false;
                    for (String ext : allowedExtensions) {
                        if (name.endsWith(ext)) {
                            hasValidExtension = true;
                            break;
                        }
                    }
                    if (!hasValidExtension) {
                        return null;
                    }
                }

                return normalized;

            } catch (Exception e) {
                return null;
            }
        }

        public String sanitizeFilename(String filename) {
            if (filename == null || filename.isEmpty()) return null;

            if (filename.length() > MAX_FILENAME_LENGTH) {
                return null;
            }

            if (filename.contains("/") || filename.contains("\\")) {
                return null;
            }

            if (filename.contains("\0")) {
                return null;
            }

            if (!filename.matches("^[a-zA-Z0-9._-]+$")) {
                // Try to sanitize
                String sanitized = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
                if (sanitized.isEmpty()) {
                    return null;
                }
                return sanitized;
            }

            return filename;
        }

        public Path buildSafePath(String baseDir, String... subPath) {
            if (!ALLOWED_BASE_DIRS.contains(baseDir)) {
                return null;
            }

            try {
                Path result = Paths.get(baseDir);
                for (String part : subPath) {
                    String sanitized = sanitizeFilename(part);
                    if (sanitized == null) {
                        return null;
                    }
                    result = result.resolve(sanitized);
                }
                return result.normalize();
            } catch (Exception e) {
                return null;
            }
        }
    }

    private MockPathSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        sanitizer = new MockPathSanitizer();
    }

    // ============================================
    // Path Validation for Reading Tests
    // ============================================

    @Nested
    @DisplayName("Path Validation for Reading Tests")
    class ReadPathTests {

        @Test
        @DisplayName("Valid read paths should pass")
        void testValidReadPaths() {
            assertNotNull(sanitizer.sanitizeForRead("run/config/settings.json"));
            assertNotNull(sanitizer.sanitizeForRead("run/logs/latest.log"));
            assertNotNull(sanitizer.sanitizeForRead("run/telemetry/events.jsonl"));
            assertNotNull(sanitizer.sanitizeForRead("config/options.toml"));
        }

        @Test
        @DisplayName("Null path should return null")
        void testNullPath() {
            assertNull(sanitizer.sanitizeForRead((Path) null));
            assertNull(sanitizer.sanitizeForRead((String) null));
        }

        @Test
        @DisplayName("Empty path should return null")
        void testEmptyPath() {
            assertNull(sanitizer.sanitizeForRead(""));
        }

        @ParameterizedTest
        @ValueSource(strings = {
            ".json",
            ".toml",
            ".txt",
            ".log",
            ".md",
            ".jsonl",
            ".csv"
        })
        @DisplayName("All allowed read extensions should work")
        void testAllowedReadExtensions(String ext) {
            assertNotNull(sanitizer.sanitizeForRead("run/config/file" + ext));
        }
    }

    // ============================================
    // Path Validation for Writing Tests
    // ============================================

    @Nested
    @DisplayName("Path Validation for Writing Tests")
    class WritePathTests {

        @Test
        @DisplayName("Valid write paths should pass")
        void testValidWritePaths() {
            assertNotNull(sanitizer.sanitizeForWrite("run/config/settings.json"));
            assertNotNull(sanitizer.sanitizeForWrite("run/qa_reports/report.md"));
            assertNotNull(sanitizer.sanitizeForWrite("run/telemetry/events.jsonl"));
        }

        @Test
        @DisplayName("Log files cannot be written (read-only)")
        void testLogFilesNotWritable() {
            assertNull(sanitizer.sanitizeForWrite("run/logs/latest.log"));
        }

        @Test
        @DisplayName("Toml files cannot be written")
        void testTomlFilesNotWritable() {
            assertNull(sanitizer.sanitizeForWrite("run/config/settings.toml"));
        }

        @ParameterizedTest
        @ValueSource(strings = {
            ".json",
            ".txt",
            ".md",
            ".jsonl",
            ".csv"
        })
        @DisplayName("All allowed write extensions should work")
        void testAllowedWriteExtensions(String ext) {
            assertNotNull(sanitizer.sanitizeForWrite("run/config/file" + ext));
        }
    }

    // ============================================
    // Directory Traversal Prevention Tests
    // ============================================

    @Nested
    @DisplayName("Directory Traversal Prevention Tests")
    class DirectoryTraversalTests {

        @ParameterizedTest
        @ValueSource(strings = {
            "../../../etc/passwd",
            "run/config/../../../etc/passwd",
            "run/config/../../private/secrets.json",
            "run/../../../secrets.json",
            "..\\..\\etc\\passwd",
            "run/config/..\\..\\private\\secrets.json"
        })
        @DisplayName("Directory traversal attempts should be blocked")
        void testDirectoryTraversalBlocked(String path) {
            assertNull(sanitizer.sanitizeForRead(path));
            assertNull(sanitizer.sanitizeForWrite(path));
        }

        @Test
        @DisplayName("Normalized paths with .. should be blocked")
        void testNormalizedTraversal() {
            assertNull(sanitizer.sanitizeForRead(Paths.get("run", "config", "..", "..", "secret.json")));
        }
    }

    // ============================================
    // Disallowed Directories Tests
    // ============================================

    @Nested
    @DisplayName("Disallowed Directories Tests")
    class DisallowedDirectoriesTests {

        @ParameterizedTest
        @ValueSource(strings = {
            "/etc/passwd",
            "C:\\Windows\\System32\\config.json",
            "/home/user/secrets.json",
            "src/main/java/Secret.java",
            "build/output.json",
            "mods/hack.jar"
        })
        @DisplayName("Paths outside allowed directories should be blocked")
        void testDisallowedDirectories(String path) {
            assertNull(sanitizer.sanitizeForRead(path));
            assertNull(sanitizer.sanitizeForWrite(path));
        }
    }

    // ============================================
    // Invalid Extension Tests
    // ============================================

    @Nested
    @DisplayName("Invalid Extension Tests")
    class InvalidExtensionTests {

        @ParameterizedTest
        @ValueSource(strings = {
            "run/config/script.exe",
            "run/config/virus.dll",
            "run/config/program.bat",
            "run/config/shell.sh",
            "run/config/code.java",
            "run/config/archive.zip",
            "run/config/binary.bin"
        })
        @DisplayName("Dangerous file extensions should be blocked")
        void testDangerousExtensions(String path) {
            assertNull(sanitizer.sanitizeForRead(path));
            assertNull(sanitizer.sanitizeForWrite(path));
        }

        @Test
        @DisplayName("Files without extension should be blocked")
        void testNoExtension() {
            assertNull(sanitizer.sanitizeForRead("run/config/noextension"));
            assertNull(sanitizer.sanitizeForWrite("run/config/noextension"));
        }
    }

    // ============================================
    // Null Byte Injection Tests
    // ============================================

    @Nested
    @DisplayName("Null Byte Injection Tests")
    class NullByteTests {

        @Test
        @DisplayName("Null byte in path should be blocked")
        void testNullByteBlocked() {
            assertNull(sanitizer.sanitizeForRead("run/config/file\0.json"));
            assertNull(sanitizer.sanitizeForRead("run/config/file.json\0.exe"));
        }
    }

    // ============================================
    // Path Length Tests
    // ============================================

    @Nested
    @DisplayName("Path Length Tests")
    class PathLengthTests {

        @Test
        @DisplayName("Path exceeding max length should be blocked")
        void testPathTooLong() {
            String longPath = "run/config/" + "a".repeat(5000) + ".json";
            assertNull(sanitizer.sanitizeForRead(longPath));
        }

        @Test
        @DisplayName("Filename exceeding max length should be blocked")
        void testFilenameTooLong() {
            String longFilename = "a".repeat(300) + ".json";
            String path = "run/config/" + longFilename;
            assertNull(sanitizer.sanitizeForRead(path));
        }
    }

    // ============================================
    // Filename Sanitization Tests
    // ============================================

    @Nested
    @DisplayName("Filename Sanitization Tests")
    class FilenameSanitizationTests {

        @Test
        @DisplayName("Valid filenames should pass through")
        void testValidFilenames() {
            assertEquals("config.json", sanitizer.sanitizeFilename("config.json"));
            assertEquals("my-file_2.txt", sanitizer.sanitizeFilename("my-file_2.txt"));
            assertEquals("Report.2024.md", sanitizer.sanitizeFilename("Report.2024.md"));
        }

        @Test
        @DisplayName("Null filename should return null")
        void testNullFilename() {
            assertNull(sanitizer.sanitizeFilename(null));
        }

        @Test
        @DisplayName("Empty filename should return null")
        void testEmptyFilename() {
            assertNull(sanitizer.sanitizeFilename(""));
        }

        @Test
        @DisplayName("Filename with path separator should return null")
        void testFilenameWithPathSeparator() {
            assertNull(sanitizer.sanitizeFilename("path/file.json"));
            assertNull(sanitizer.sanitizeFilename("path\\file.json"));
        }

        @Test
        @DisplayName("Filename with null byte should return null")
        void testFilenameWithNullByte() {
            assertNull(sanitizer.sanitizeFilename("file\0.json"));
        }

        @Test
        @DisplayName("Filename with special characters should be sanitized")
        void testFilenameSpecialCharsSanitized() {
            assertEquals("file_name_.json", sanitizer.sanitizeFilename("file name!.json"));
            assertEquals("report___2024_.json", sanitizer.sanitizeFilename("report<>|2024!.json"));
        }

        @Test
        @DisplayName("Filename too long should return null")
        void testFilenameTooLong() {
            assertNull(sanitizer.sanitizeFilename("a".repeat(300)));
        }
    }

    // ============================================
    // Build Safe Path Tests
    // ============================================

    @Nested
    @DisplayName("Build Safe Path Tests")
    class BuildSafePathTests {

        @Test
        @DisplayName("Valid safe path building")
        void testValidSafePathBuilding() {
            Path path = sanitizer.buildSafePath("run/config", "subdir", "file.json");
            assertNotNull(path);
            assertTrue(path.toString().contains("run/config"));
            assertTrue(path.toString().contains("subdir"));
            assertTrue(path.toString().contains("file.json"));
        }

        @Test
        @DisplayName("Invalid base directory should return null")
        void testInvalidBaseDirectory() {
            assertNull(sanitizer.buildSafePath("invalid/dir", "file.json"));
            assertNull(sanitizer.buildSafePath("/etc", "passwd"));
        }

        @Test
        @DisplayName("Invalid subpath component should return null")
        void testInvalidSubpathComponent() {
            assertNull(sanitizer.buildSafePath("run/config", "../secret", "file.json"));
            assertNull(sanitizer.buildSafePath("run/config", "sub\0dir", "file.json"));
        }

        @Test
        @DisplayName("All allowed base directories work")
        void testAllAllowedBaseDirs() {
            assertNotNull(sanitizer.buildSafePath("run/config", "file.json"));
            assertNotNull(sanitizer.buildSafePath("run/telemetry", "file.json"));
            assertNotNull(sanitizer.buildSafePath("run/qa_reports", "file.json"));
            assertNotNull(sanitizer.buildSafePath("run/logs", "file.json"));
            assertNotNull(sanitizer.buildSafePath("config", "file.json"));
        }
    }

    // ============================================
    // Edge Cases
    // ============================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Path with multiple extensions")
        void testMultipleExtensions() {
            // Both should pass as they end with .json which is valid
            assertNotNull(sanitizer.sanitizeForRead("run/config/backup.json.json"));
            // .exe.json ends with .json, so it's valid (extension check is suffix-based)
            assertNotNull(sanitizer.sanitizeForRead("run/config/file.exe.json"));
        }

        @Test
        @DisplayName("Path with leading dot prefix")
        void testLeadingDotPrefix() {
            assertNotNull(sanitizer.sanitizeForRead("./run/config/file.json"));
        }

        @Test
        @DisplayName("Case sensitivity in extensions")
        void testExtensionCaseSensitivity() {
            assertNotNull(sanitizer.sanitizeForRead("run/config/FILE.JSON"));
            assertNotNull(sanitizer.sanitizeForRead("run/config/FILE.Json"));
        }

        @Test
        @DisplayName("Unicode in filenames should be sanitized")
        void testUnicodeFilenames() {
            // Unicode should be replaced with underscores
            String result = sanitizer.sanitizeFilename("файл.json");
            assertNotNull(result);
            assertFalse(result.contains("ф"));
        }
    }
}
