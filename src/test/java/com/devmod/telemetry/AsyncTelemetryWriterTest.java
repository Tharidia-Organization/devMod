package com.devmod.telemetry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AsyncTelemetryWriter")
class AsyncTelemetryWriterTest {

    @TempDir
    Path tempDir;

    private AsyncTelemetryWriter writer;

    @BeforeEach
    void setUp() {
        writer = new AsyncTelemetryWriter();
    }

    @AfterEach
    void tearDown() {
        writer.shutdown();
    }

    @Test
    @DisplayName("queueWrite writes to file asynchronously")
    void queueWriteWritesToFile() throws Exception {
        Path file = tempDir.resolve("test.ndjson");

        writer.start();
        writer.queueWrite(file, "{\"event\":\"test\"}");

        // Wait for async write
        Thread.sleep(500);

        assertTrue(Files.exists(file));
        List<String> lines = Files.readAllLines(file);
        assertEquals(1, lines.size());
        assertEquals("{\"event\":\"test\"}", lines.get(0));
    }

    @Test
    @DisplayName("queueWrite appends multiple lines")
    void queueWriteAppendsMultipleLines() throws Exception {
        Path file = tempDir.resolve("multi.ndjson");

        writer.start();
        writer.queueWrite(file, "line1");
        writer.queueWrite(file, "line2");
        writer.queueWrite(file, "line3");

        Thread.sleep(500);

        List<String> lines = Files.readAllLines(file);
        assertEquals(3, lines.size());
    }

    @Test
    @DisplayName("queueWrite creates parent directories")
    void queueWriteCreatesParentDirs() throws Exception {
        Path file = tempDir.resolve("sub/dir/test.ndjson");

        writer.start();
        writer.queueWrite(file, "data");

        Thread.sleep(500);

        assertTrue(Files.exists(file));
    }

    @Test
    @DisplayName("start is idempotent")
    void startIsIdempotent() {
        writer.start();
        writer.start(); // second call should not throw
    }

    @Test
    @DisplayName("getQueueSize returns pending count")
    void getQueueSizeReturnsPending() {
        assertEquals(0, writer.getQueueSize());
    }

    @Test
    @DisplayName("auto-start on first queueWrite")
    void autoStartOnFirstWrite() throws Exception {
        Path file = tempDir.resolve("autostart.ndjson");

        // Don't call start() explicitly
        writer.queueWrite(file, "auto-started");

        Thread.sleep(500);

        assertTrue(Files.exists(file));
    }

    @Test
    @DisplayName("shutdown flushes remaining writes")
    void shutdownFlushesRemaining() throws Exception {
        Path file = tempDir.resolve("shutdown.ndjson");

        writer.start();
        for (int i = 0; i < 10; i++) {
            writer.queueWrite(file, "line" + i);
        }

        writer.shutdown();

        List<String> lines = Files.readAllLines(file);
        assertEquals(10, lines.size());
    }

    @Test
    @DisplayName("queueWrite after shutdown is ignored")
    void queueWriteAfterShutdownIgnored() throws Exception {
        Path file = tempDir.resolve("after_shutdown.ndjson");

        writer.start();
        writer.shutdown();
        writer.queueWrite(file, "should be ignored");

        Thread.sleep(200);

        // File should not exist or be empty
        if (Files.exists(file)) {
            assertEquals(0, Files.readAllLines(file).size());
        }
    }

    @Test
    @DisplayName("handles write to different files")
    void handlesDifferentFiles() throws Exception {
        Path file1 = tempDir.resolve("file1.ndjson");
        Path file2 = tempDir.resolve("file2.ndjson");

        writer.start();
        writer.queueWrite(file1, "data1");
        writer.queueWrite(file2, "data2");

        Thread.sleep(500);

        assertEquals(1, Files.readAllLines(file1).size());
        assertEquals(1, Files.readAllLines(file2).size());
    }
}
