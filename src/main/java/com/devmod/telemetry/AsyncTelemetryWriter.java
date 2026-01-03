package com.devmod.telemetry;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

public class AsyncTelemetryWriter {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int QUEUE_CAPACITY = 1000; // Max 1000 pending writes

    private final BlockingQueue<WriteTask> writeQueue;
    private @Nullable Thread writerThread;
    private volatile boolean running;
    private volatile boolean started;

    public AsyncTelemetryWriter() {
        this.writeQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        this.running = true;
        this.started = false;
    }

    public synchronized void start() {
        if (started) {
            return;
        }
        writerThread = new Thread(this::writerLoop, "TelemetryWriter");
        writerThread.setDaemon(true); // Don't prevent JVM shutdown
        writerThread.start();
        started = true;
        LOGGER.info("AsyncTelemetryWriter started");
    }

    /**
     * Queue a write operation (non-blocking, ~0.1ms overhead)
     */
    public void queueWrite(Path file, String line) {
        if (!running) {
            LOGGER.warn("AsyncTelemetryWriter is shutting down, ignoring write to {}", file);
            return;
        }
        if (!started) {
            start();
        }

        WriteTask task = new WriteTask(file, line);

        // Try to add to queue (non-blocking)
        if (!writeQueue.offer(task)) {
            // Queue full (1000+ pending writes) - drop oldest or log warning
            LOGGER.warn("Telemetry write queue full ({}), dropping write to {}", QUEUE_CAPACITY, file);
        }
    }

    /**
     * Shutdown the writer thread gracefully
     */
    public synchronized void shutdown() {
        LOGGER.info("Shutting down AsyncTelemetryWriter...");
        running = false;
        Thread thread = writerThread;
        if (thread == null) {
            return;
        }
        thread.interrupt();

        try {
            // Wait max 5 seconds for pending writes to complete
            thread.join(5000);
            if (thread.isAlive()) {
                LOGGER.warn("AsyncTelemetryWriter did not finish in 5s, {} writes may be lost", writeQueue.size());
            } else {
                LOGGER.info("AsyncTelemetryWriter shutdown complete");
            }
        } catch (InterruptedException e) {
            LOGGER.error("Interrupted while waiting for AsyncTelemetryWriter shutdown", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Background thread loop - processes write queue
     */
    private void writerLoop() {
        while (running || !writeQueue.isEmpty()) {
            try {
                // Poll with timeout to allow shutdown check
                WriteTask task = writeQueue.poll(100, TimeUnit.MILLISECONDS);

                if (task != null) {
                    executeWrite(task);
                }
            } catch (InterruptedException e) {
                // Shutdown signal
                LOGGER.debug("AsyncTelemetryWriter interrupted, shutting down...");
                break;
            }
        }

        // Flush remaining writes before exit
        LOGGER.info("Flushing {} remaining telemetry writes...", writeQueue.size());
        WriteTask task;
        while ((task = writeQueue.poll()) != null) {
            executeWrite(task);
        }
    }

    /**
     * Execute a single write operation (blocking I/O)
     */
    private void executeWrite(WriteTask task) {
        try {
            // Create parent directories if needed
            Path parent = task.file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            // Append line to file (buffered for performance)
            try (BufferedWriter writer = Files.newBufferedWriter(
                    task.file,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND)) {
                writer.write(task.line);
                writer.newLine();
            }
        } catch (IOException e) {
            LOGGER.error("Failed to write telemetry to {}: {}", task.file, e.getMessage());
        }
    }

    /**
     * Get current queue size (for monitoring)
     */
    public int getQueueSize() {
        return writeQueue.size();
    }

    /**
     * Simple data class for write operations
     */
    private record WriteTask(Path file, String line) {}
}
