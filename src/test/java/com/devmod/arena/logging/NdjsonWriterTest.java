package com.devmod.arena.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NdjsonWriter (DD17, DD20).
 * Verifies non-blocking behavior, buffer management, and log rotation.
 */
class NdjsonWriterTest {

    @TempDir
    Path tempDir;

    private NdjsonWriter writer;

    @AfterEach
    void tearDown() {
        if (writer != null) {
            writer.close();
        }
    }

    @Nested
    @DisplayName("Non-blocking Write Tests (DD20)")
    class NonBlockingWriteTests {

        @Test
        @DisplayName("Write is non-blocking even with slow flush")
        void writeIsNonBlocking() throws Exception {
            writer = new NdjsonWriter(tempDir, "test", 100, 1000, 10000);

            long startTime = System.currentTimeMillis();

            // Write many entries quickly
            for (int i = 0; i < 50; i++) {
                writer.write(Map.of("index", i, "message", "test message"));
            }

            long elapsed = System.currentTimeMillis() - startTime;

            // Should complete very quickly since writes are buffered
            assertTrue(elapsed < 500, "Writes should be non-blocking, took: " + elapsed + "ms");
        }

        @Test
        @DisplayName("Buffer full drops entries instead of blocking")
        void bufferFullDropsEntries() {
            // Small buffer for testing
            writer = new NdjsonWriter(tempDir, "test", 10, 100, 60000);

            int dropped = 0;

            // Try to write more than buffer capacity
            for (int i = 0; i < 50; i++) {
                if (!writer.write(Map.of("index", i))) {
                    dropped++;
                }
            }

            // Some entries should be dropped
            var stats = writer.getStats();
            assertTrue(stats.totalDropped() > 0 || dropped > 0,
                "Should drop entries when buffer is full");
        }

        @Test
        @DisplayName("Concurrent writes are handled correctly")
        void concurrentWritesHandled() throws Exception {
            writer = new NdjsonWriter(tempDir, "test", 1000, 100, 500);

            int threadCount = 10;
            int writesPerThread = 100;
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < writesPerThread; i++) {
                            writer.write(Map.of("thread", threadId, "index", i));
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            executor.shutdown();

            // Force flush
            writer.flush();

            var stats = writer.getStats();
            long totalProcessed = stats.totalWritten() + stats.totalDropped();
            assertEquals(threadCount * writesPerThread, totalProcessed,
                "All writes should be processed (written or dropped)");
        }
    }

    @Nested
    @DisplayName("Flush Policy Tests (DD20)")
    class FlushPolicyTests {

        @Test
        @DisplayName("Flushes after reaching line threshold")
        void flushesAfterLineThreshold() throws Exception {
            // Flush after 10 lines
            writer = new NdjsonWriter(tempDir, "test", 1000, 10, 60000);

            // Write exactly the threshold
            for (int i = 0; i < 10; i++) {
                writer.write(Map.of("index", i));
            }

            // Wait for flush
            Thread.sleep(500);

            // Check file exists and has content
            try (Stream<Path> files = Files.list(tempDir)) {
                Path logFile = files
                    .filter(p -> p.getFileName().toString().endsWith(".ndjson"))
                    .findFirst()
                    .orElse(null);

                assertNotNull(logFile, "Log file should exist after threshold flush");
                long lineCount = Files.lines(logFile).count();
                assertTrue(lineCount >= 10, "Should have flushed at least threshold lines");
            }
        }

        @Test
        @DisplayName("Flushes after time interval")
        void flushesAfterTimeInterval() throws Exception {
            // Flush every 500ms, high line threshold
            writer = new NdjsonWriter(tempDir, "test", 1000, 10000, 500);

            // Write a few lines (below threshold)
            for (int i = 0; i < 5; i++) {
                writer.write(Map.of("index", i));
            }

            // Wait for time-based flush
            Thread.sleep(1000);

            // Check file has content
            try (Stream<Path> files = Files.list(tempDir)) {
                Path logFile = files
                    .filter(p -> p.getFileName().toString().endsWith(".ndjson"))
                    .findFirst()
                    .orElse(null);

                assertNotNull(logFile, "Log file should exist after time-based flush");
                long lineCount = Files.lines(logFile).count();
                assertEquals(5, lineCount, "Should have flushed all lines");
            }
        }
    }

    @Nested
    @DisplayName("Log Rotation Tests (DD17)")
    class LogRotationTests {

        @Test
        @DisplayName("Creates new log file with date in name")
        void createsLogFileWithDate() throws Exception {
            writer = new NdjsonWriter(tempDir, "arena-log", 100, 10, 500);
            writer.write(Map.of("test", "message"));
            writer.flush();

            try (Stream<Path> files = Files.list(tempDir)) {
                boolean hasDateFile = files
                    .anyMatch(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("arena-log_") && name.contains("-") && name.endsWith(".ndjson");
                    });
                assertTrue(hasDateFile, "Log file should have date in name");
            }
        }

        @Test
        @DisplayName("Compresses log file on close")
        void compressesLogFileOnClose() throws Exception {
            writer = new NdjsonWriter(tempDir, "test", 100, 10, 500);

            // Write some data
            for (int i = 0; i < 20; i++) {
                writer.write(Map.of("index", i));
            }
            writer.flush();

            // Close triggers compression
            writer.close();
            writer = null;

            // Check for .gz file
            try (Stream<Path> files = Files.list(tempDir)) {
                boolean hasGzFile = files.anyMatch(p -> p.getFileName().toString().endsWith(".gz"));
                assertTrue(hasGzFile, "Should have compressed .gz file after close");
            }
        }
    }

    @Nested
    @DisplayName("Statistics Tests")
    class StatisticsTests {

        @Test
        @DisplayName("Tracks written and dropped counts")
        void tracksWrittenAndDroppedCounts() throws Exception {
            writer = new NdjsonWriter(tempDir, "test", 100, 50, 500);

            for (int i = 0; i < 50; i++) {
                writer.write(Map.of("index", i));
            }

            writer.flush();

            var stats = writer.getStats();
            assertTrue(stats.totalWritten() > 0, "Should track written count");
            assertNotNull(stats.currentFilePath(), "Should track current file path");
        }

        @Test
        @DisplayName("Drop rate is calculated correctly")
        void dropRateCalculatedCorrectly() {
            writer = new NdjsonWriter(tempDir, "test", 5, 100, 60000);

            // Fill buffer and cause drops
            for (int i = 0; i < 20; i++) {
                writer.write(Map.of("index", i));
            }

            var stats = writer.getStats();
            if (stats.totalDropped() > 0) {
                double dropRate = stats.dropRate();
                assertTrue(dropRate > 0 && dropRate <= 1, "Drop rate should be between 0 and 1");
            }
        }
    }

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Builder creates writer with custom settings")
        void builderCreatesWithCustomSettings() {
            writer = NdjsonWriter.builder()
                .logDirectory(tempDir)
                .logPrefix("custom-prefix")
                .bufferCapacity(500)
                .flushThreshold(25)
                .flushIntervalMs(2000)
                .build();

            assertTrue(writer.isRunning());

            // Write and check
            writer.write(Map.of("test", "value"));
            assertEquals(1, writer.getBufferSize());
        }

        @Test
        @DisplayName("Builder requires log directory")
        void builderRequiresLogDirectory() {
            assertThrows(NullPointerException.class, () -> {
                NdjsonWriter.builder()
                    .logPrefix("test")
                    .build();
            });
        }
    }

    @Nested
    @DisplayName("JSON Serialization Tests")
    class JsonSerializationTests {

        @Test
        @DisplayName("Serializes various types correctly")
        void serializesVariousTypes() throws Exception {
            writer = new NdjsonWriter(tempDir, "test", 100, 10, 500);

            writer.write(Map.of(
                "string", "hello",
                "number", 42,
                "boolean", true,
                "nested", Map.of("key", "value")
            ));

            writer.flush();
            Thread.sleep(200);

            try (Stream<Path> files = Files.list(tempDir)) {
                Path logFile = files
                    .filter(p -> p.getFileName().toString().endsWith(".ndjson"))
                    .findFirst()
                    .orElse(null);

                assertNotNull(logFile);
                String content = Files.readString(logFile);

                assertTrue(content.contains("\"string\":\"hello\""));
                assertTrue(content.contains("\"number\":42"));
                assertTrue(content.contains("\"boolean\":true"));
            }
        }

        @Test
        @DisplayName("Escapes special characters in strings")
        void escapesSpecialCharacters() throws Exception {
            writer = new NdjsonWriter(tempDir, "test", 100, 10, 500);

            writer.write(Map.of("message", "line1\nline2\ttab\"quote"));
            writer.flush();
            Thread.sleep(200);

            try (Stream<Path> files = Files.list(tempDir)) {
                Path logFile = files
                    .filter(p -> p.getFileName().toString().endsWith(".ndjson"))
                    .findFirst()
                    .orElse(null);

                assertNotNull(logFile);
                String content = Files.readString(logFile);

                assertTrue(content.contains("\\n"), "Should escape newlines");
                assertTrue(content.contains("\\t"), "Should escape tabs");
                assertTrue(content.contains("\\\""), "Should escape quotes");
            }
        }
    }
}
