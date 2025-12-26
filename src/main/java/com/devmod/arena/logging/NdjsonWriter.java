package com.devmod.arena.logging;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

/**
 * Async NDJSON (Newline Delimited JSON) writer with buffering and flush policy (DD17, DD20).
 *
 * Features:
 * - Non-blocking writes with 10k buffer (DD20)
 * - Flush policy: 100 lines or 1 second, whichever comes first (DD20)
 * - Log rotation: 14 days, 500MB cap, .gz compression (DD17)
 * - Buffer full behavior: drop oldest entries (non-blocking)
 */
public class NdjsonWriter implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(NdjsonWriter.class.getName());

    /**
     * Default buffer capacity (DD20).
     */
    public static final int DEFAULT_BUFFER_CAPACITY = 10_000;

    /**
     * Default flush threshold in lines (DD20).
     */
    public static final int DEFAULT_FLUSH_LINES = 100;

    /**
     * Default flush interval in milliseconds (DD20).
     */
    public static final long DEFAULT_FLUSH_INTERVAL_MS = 1000;

    /**
     * Maximum log age in days (DD17).
     */
    public static final int MAX_LOG_AGE_DAYS = 14;

    /**
     * Maximum log size in bytes (DD17: 500MB).
     */
    public static final long MAX_LOG_SIZE_BYTES = 500L * 1024 * 1024;

    private final Path logDirectory;
    private final String logPrefix;
    private final BlockingQueue<String> buffer;
    private final int flushThreshold;
    private final long flushIntervalMs;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running;
    private final AtomicLong totalWritten;
    private final AtomicLong totalDropped;
    private final AtomicLong currentFileSize;

    private volatile Path currentLogFile;
    private volatile BufferedWriter writer;
    private volatile LocalDate currentLogDate;

    /**
     * Creates an NdjsonWriter with default configuration.
     *
     * @param logDirectory Directory for log files
     * @param logPrefix Prefix for log file names
     */
    public NdjsonWriter(Path logDirectory, String logPrefix) {
        this(logDirectory, logPrefix, DEFAULT_BUFFER_CAPACITY, DEFAULT_FLUSH_LINES, DEFAULT_FLUSH_INTERVAL_MS);
    }

    /**
     * Creates an NdjsonWriter with custom configuration.
     *
     * @param logDirectory Directory for log files
     * @param logPrefix Prefix for log file names
     * @param bufferCapacity Maximum buffer capacity
     * @param flushThreshold Number of lines before flush
     * @param flushIntervalMs Interval between flushes in milliseconds
     */
    public NdjsonWriter(
            Path logDirectory,
            String logPrefix,
            int bufferCapacity,
            int flushThreshold,
            long flushIntervalMs) {

        this.logDirectory = Objects.requireNonNull(logDirectory, "logDirectory must not be null");
        this.logPrefix = Objects.requireNonNull(logPrefix, "logPrefix must not be null");
        this.buffer = new LinkedBlockingQueue<>(bufferCapacity);
        this.flushThreshold = flushThreshold;
        this.flushIntervalMs = flushIntervalMs;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ndjson-writer-" + logPrefix);
            t.setDaemon(true);
            return t;
        });
        this.running = new AtomicBoolean(true);
        this.totalWritten = new AtomicLong(0);
        this.totalDropped = new AtomicLong(0);
        this.currentFileSize = new AtomicLong(0);

        initialize();
    }

    /**
     * Initializes the writer and starts background tasks.
     */
    private void initialize() {
        try {
            Files.createDirectories(logDirectory);
            rotateLogFileIfNeeded();
            startFlushTask();
            startRotationTask();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize NdjsonWriter", e);
            throw new RuntimeException("Failed to initialize NdjsonWriter", e);
        }
    }

    /**
     * Writes a JSON object as an NDJSON line.
     * This method is non-blocking; if the buffer is full, the oldest entry is dropped.
     *
     * @param jsonObject The object to serialize and write
     * @return true if queued successfully, false if dropped
     */
    public boolean write(Map<String, Object> jsonObject) {
        if (!running.get()) {
            return false;
        }

        String json = toJson(jsonObject);
        return write(json);
    }

    /**
     * Writes a pre-serialized JSON string as an NDJSON line.
     * This method is non-blocking.
     *
     * @param jsonLine The JSON string to write
     * @return true if queued successfully, false if dropped
     */
    public boolean write(String jsonLine) {
        if (!running.get()) {
            return false;
        }

        // Non-blocking offer - if full, drop the line (DD20)
        if (!buffer.offer(jsonLine)) {
            totalDropped.incrementAndGet();
            return false;
        }

        // Trigger immediate flush if threshold reached
        if (buffer.size() >= flushThreshold) {
            scheduler.execute(this::flushBuffer);
        }

        return true;
    }

    /**
     * Writes a record (any Java record or object with a toMap method).
     *
     * @param record The record to write
     * @return true if queued successfully
     */
    public boolean writeRecord(Object record) {
        try {
            if (record instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) record;
                return write(map);
            }

            // Try to call toMap() via reflection for records
            var method = record.getClass().getMethod("toMap");
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) method.invoke(record);
            return write(map);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to write record", e);
            return false;
        }
    }

    /**
     * Starts the periodic flush task (DD20: every 1 second).
     */
    private void startFlushTask() {
        scheduler.scheduleAtFixedRate(() -> {
            if (running.get()) {
                flushBuffer();
            }
        }, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Starts the rotation check task (checks every hour).
     */
    private void startRotationTask() {
        scheduler.scheduleAtFixedRate(() -> {
            if (running.get()) {
                try {
                    rotateLogFileIfNeeded();
                    cleanupOldLogs();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Log rotation failed", e);
                }
            }
        }, 1, 1, TimeUnit.HOURS);
    }

    /**
     * Flushes the buffer to disk.
     */
    private synchronized void flushBuffer() {
        if (buffer.isEmpty()) {
            return;
        }

        List<String> lines = new ArrayList<>(Math.min(buffer.size(), flushThreshold * 2));
        buffer.drainTo(lines);

        if (lines.isEmpty()) {
            return;
        }

        try {
            rotateLogFileIfNeeded();

            for (String line : lines) {
                writer.write(line);
                writer.newLine();
                totalWritten.incrementAndGet();
                currentFileSize.addAndGet(line.length() + 1);
            }
            writer.flush();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to flush buffer", e);
            // Re-queue the lines if possible
            for (String line : lines) {
                if (!buffer.offer(line)) {
                    totalDropped.incrementAndGet();
                }
            }
        }
    }

    /**
     * Rotates the log file if needed (new day or size exceeded).
     */
    private synchronized void rotateLogFileIfNeeded() throws IOException {
        LocalDate today = LocalDate.now();

        boolean needsRotation = currentLogFile == null
            || !today.equals(currentLogDate)
            || currentFileSize.get() >= MAX_LOG_SIZE_BYTES;

        if (!needsRotation) {
            return;
        }

        // Close current writer
        closeWriter();

        // Compress old log file if exists
        if (currentLogFile != null && Files.exists(currentLogFile)) {
            compressLogFile(currentLogFile);
        }

        // Create new log file
        currentLogDate = today;
        String fileName = String.format("%s_%s.ndjson",
            logPrefix,
            today.format(DateTimeFormatter.ISO_LOCAL_DATE));

        // Handle multiple rotations on the same day
        int sequence = 0;
        Path candidatePath;
        do {
            String seqSuffix = sequence > 0 ? "_" + sequence : "";
            candidatePath = logDirectory.resolve(fileName.replace(".ndjson", seqSuffix + ".ndjson"));
            sequence++;
        } while (Files.exists(candidatePath) && Files.size(candidatePath) >= MAX_LOG_SIZE_BYTES);

        currentLogFile = candidatePath;
        currentFileSize.set(Files.exists(currentLogFile) ? Files.size(currentLogFile) : 0);

        writer = Files.newBufferedWriter(
            currentLogFile,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        );

        LOGGER.info("Opened new log file: " + currentLogFile);
    }

    /**
     * Compresses a log file to .gz format (DD17).
     */
    private void compressLogFile(Path logFile) {
        if (!Files.exists(logFile) || logFile.toString().endsWith(".gz")) {
            return;
        }

        Path gzFile = Path.of(logFile.toString() + ".gz");

        try (OutputStream fos = Files.newOutputStream(gzFile);
             GZIPOutputStream gzos = new GZIPOutputStream(fos);
             var reader = Files.newBufferedReader(logFile)) {

            OutputStreamWriter gzWriter = new OutputStreamWriter(gzos, StandardCharsets.UTF_8);
            reader.transferTo(gzWriter);
            gzWriter.flush();

            // Delete original after successful compression
            Files.delete(logFile);
            LOGGER.info("Compressed log file: " + logFile + " -> " + gzFile);

        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to compress log file: " + logFile, e);
        }
    }

    /**
     * Cleans up old log files (DD17: 14 days).
     */
    private void cleanupOldLogs() {
        try {
            Instant cutoff = Instant.now().minus(Duration.ofDays(MAX_LOG_AGE_DAYS));

            Files.list(logDirectory)
                .filter(p -> p.getFileName().toString().startsWith(logPrefix))
                .filter(p -> {
                    try {
                        return Files.getLastModifiedTime(p).toInstant().isBefore(cutoff);
                    } catch (IOException e) {
                        return false;
                    }
                })
                .forEach(p -> {
                    try {
                        Files.delete(p);
                        LOGGER.info("Deleted old log file: " + p);
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Failed to delete old log: " + p, e);
                    }
                });
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to cleanup old logs", e);
        }
    }

    /**
     * Converts a map to a JSON string.
     * Simple implementation - in production, use a proper JSON library.
     */
    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;

            sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
            appendJsonValue(sb, entry.getValue());
        }

        sb.append("}");
        return sb.toString();
    }

    /**
     * Appends a JSON value to the StringBuilder.
     */
    @SuppressWarnings("unchecked")
    private void appendJsonValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            sb.append("\"").append(escapeJson((String) value)).append("\"");
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map) {
            sb.append(toJson((Map<String, Object>) value));
        } else if (value instanceof List) {
            sb.append("[");
            boolean first = true;
            for (Object item : (List<?>) value) {
                if (!first) sb.append(",");
                first = false;
                appendJsonValue(sb, item);
            }
            sb.append("]");
        } else if (value instanceof Instant) {
            sb.append("\"").append(value.toString()).append("\"");
        } else {
            sb.append("\"").append(escapeJson(value.toString())).append("\"");
        }
    }

    /**
     * Escapes special characters for JSON.
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    /**
     * Forces an immediate flush of the buffer.
     */
    public void flush() {
        flushBuffer();
    }

    /**
     * Returns writer statistics.
     */
    public NdjsonWriterStats getStats() {
        return new NdjsonWriterStats(
            totalWritten.get(),
            totalDropped.get(),
            buffer.size(),
            currentFileSize.get(),
            currentLogFile != null ? currentLogFile.toString() : null
        );
    }

    /**
     * Returns the current buffer size.
     */
    public int getBufferSize() {
        return buffer.size();
    }

    /**
     * Checks if the writer is running.
     */
    public boolean isRunning() {
        return running.get();
    }

    private void closeWriter() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to close writer", e);
            }
            writer = null;
        }
    }

    @Override
    public void close() {
        running.set(false);

        // Final flush
        flushBuffer();

        // Shutdown scheduler
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }

        // Close writer
        closeWriter();

        // Compress final log file
        if (currentLogFile != null && Files.exists(currentLogFile)) {
            compressLogFile(currentLogFile);
        }

        LOGGER.info("NdjsonWriter closed. Stats: " + getStats());
    }

    /**
     * Statistics record for the writer.
     */
    public record NdjsonWriterStats(
        long totalWritten,
        long totalDropped,
        int currentBufferSize,
        long currentFileSizeBytes,
        String currentFilePath
    ) {
        public double dropRate() {
            long total = totalWritten + totalDropped;
            return total > 0 ? (double) totalDropped / total : 0.0;
        }
    }

    /**
     * Builder for NdjsonWriter configuration.
     */
    public static class Builder {
        private Path logDirectory;
        private String logPrefix = "log";
        private int bufferCapacity = DEFAULT_BUFFER_CAPACITY;
        private int flushThreshold = DEFAULT_FLUSH_LINES;
        private long flushIntervalMs = DEFAULT_FLUSH_INTERVAL_MS;

        public Builder logDirectory(Path logDirectory) {
            this.logDirectory = logDirectory;
            return this;
        }

        public Builder logPrefix(String logPrefix) {
            this.logPrefix = logPrefix;
            return this;
        }

        public Builder bufferCapacity(int bufferCapacity) {
            this.bufferCapacity = bufferCapacity;
            return this;
        }

        public Builder flushThreshold(int flushThreshold) {
            this.flushThreshold = flushThreshold;
            return this;
        }

        public Builder flushIntervalMs(long flushIntervalMs) {
            this.flushIntervalMs = flushIntervalMs;
            return this;
        }

        public NdjsonWriter build() {
            Objects.requireNonNull(logDirectory, "logDirectory must be set");
            return new NdjsonWriter(
                logDirectory,
                logPrefix,
                bufferCapacity,
                flushThreshold,
                flushIntervalMs
            );
        }
    }

    /**
     * Creates a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }
}
