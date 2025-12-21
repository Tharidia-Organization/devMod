package com.devmod.arena.logging;

import com.devmod.arena.telemetry.ArenaTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * DD59: Unified Log Aggregation Pipeline.
 *
 * <p>Connects telemetry events to structured logging with filtering,
 * transformation, and multi-destination routing:
 *
 * <pre>
 *   ArenaTelemetry → Filter → Transform → [NdjsonWriter, DuckDB, Metrics]
 * </pre>
 *
 * <p>Features:
 * <ul>
 *   <li>Event filtering by name pattern and severity</li>
 *   <li>Field transformation and enrichment</li>
 *   <li>Multi-destination routing (NDJSON, database, metrics)</li>
 *   <li>Batching for efficiency</li>
 *   <li>Backpressure handling</li>
 * </ul>
 */
public class LogAggregationPipeline implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(LogAggregationPipeline.class);

    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final Duration DEFAULT_FLUSH_INTERVAL = Duration.ofSeconds(1);
    private static final int DEFAULT_QUEUE_CAPACITY = 10_000;

    private final BlockingQueue<LogEvent> eventQueue;
    private final List<LogDestination> destinations;
    private final List<Predicate<LogEvent>> filters;
    private final List<EventTransformer> transformers;

    private final ScheduledExecutorService scheduler;
    private final ExecutorService workerPool;

    private final int batchSize;
    private final Duration flushInterval;

    private final AtomicLong eventsReceived = new AtomicLong(0);
    private final AtomicLong eventsFiltered = new AtomicLong(0);
    private final AtomicLong eventsWritten = new AtomicLong(0);
    private final AtomicLong eventsDropped = new AtomicLong(0);

    private volatile boolean running = false;

    private LogAggregationPipeline(Builder builder) {
        this.eventQueue = new LinkedBlockingQueue<>(builder.queueCapacity);
        this.destinations = new ArrayList<>(builder.destinations);
        this.filters = new ArrayList<>(builder.filters);
        this.transformers = new ArrayList<>(builder.transformers);
        this.batchSize = builder.batchSize;
        this.flushInterval = builder.flushInterval;

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "log-aggregation-scheduler");
            t.setDaemon(true);
            return t;
        });

        this.workerPool = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "log-aggregation-worker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Creates a new builder for the pipeline.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * DD59: Starts the aggregation pipeline.
     */
    public void start() {
        if (running) {
            return;
        }
        running = true;

        // Schedule periodic flush
        scheduler.scheduleAtFixedRate(
            this::flush,
            flushInterval.toMillis(),
            flushInterval.toMillis(),
            TimeUnit.MILLISECONDS
        );

        // Start worker for continuous processing
        workerPool.submit(this::processLoop);

        LOGGER.info("Log aggregation pipeline started with {} destinations", destinations.size());
    }

    /**
     * DD59: Ingests an event into the pipeline.
     */
    public boolean ingest(LogEvent event) {
        if (!running) {
            return false;
        }

        eventsReceived.incrementAndGet();

        // Apply filters
        for (Predicate<LogEvent> filter : filters) {
            if (!filter.test(event)) {
                eventsFiltered.incrementAndGet();
                return true; // Filtered out is not an error
            }
        }

        // Apply transformers
        LogEvent transformed = event;
        for (EventTransformer transformer : transformers) {
            transformed = transformer.transform(transformed);
        }

        // Non-blocking offer
        if (!eventQueue.offer(transformed)) {
            eventsDropped.incrementAndGet();
            return false;
        }

        return true;
    }

    /**
     * DD59: Ingests a telemetry event.
     */
    public boolean ingest(ArenaTelemetry.TelemetryEvent telemetryEvent) {
        return ingest(LogEvent.fromTelemetry(telemetryEvent));
    }

    /**
     * DD59: Creates a telemetry consumer that routes to this pipeline.
     */
    public java.util.function.Consumer<ArenaTelemetry.TelemetryEvent> asTelemetryConsumer() {
        return event -> ingest(LogEvent.fromTelemetry(event));
    }

    private void processLoop() {
        List<LogEvent> batch = new ArrayList<>(batchSize);

        while (running) {
            try {
                // Wait for first event
                LogEvent event = eventQueue.poll(100, TimeUnit.MILLISECONDS);
                if (event == null) {
                    continue;
                }

                batch.add(event);

                // Drain up to batch size
                eventQueue.drainTo(batch, batchSize - 1);

                // Write to all destinations
                writeBatch(batch);
                batch.clear();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.error("Error in log aggregation pipeline", e);
            }
        }

        // Final flush
        if (!batch.isEmpty()) {
            writeBatch(batch);
        }
    }

    private void writeBatch(List<LogEvent> batch) {
        for (LogDestination destination : destinations) {
            try {
                destination.write(batch);
                eventsWritten.addAndGet(batch.size());
            } catch (Exception e) {
                LOGGER.error("Failed to write to destination {}: {}",
                    destination.name(), e.getMessage());
            }
        }
    }

    /**
     * DD59: Forces a flush of pending events.
     */
    public void flush() {
        List<LogEvent> pending = new ArrayList<>();
        eventQueue.drainTo(pending);
        if (!pending.isEmpty()) {
            writeBatch(pending);
        }
    }

    /**
     * DD59: Returns pipeline statistics.
     */
    public PipelineStats getStats() {
        return new PipelineStats(
            eventsReceived.get(),
            eventsFiltered.get(),
            eventsWritten.get(),
            eventsDropped.get(),
            eventQueue.size()
        );
    }

    @Override
    public void close() {
        running = false;

        // Final flush
        flush();

        scheduler.shutdown();
        workerPool.shutdown();

        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
            workerPool.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Close destinations
        for (LogDestination destination : destinations) {
            try {
                destination.close();
            } catch (Exception e) {
                LOGGER.warn("Error closing destination {}: {}", destination.name(), e.getMessage());
            }
        }

        LOGGER.info("Log aggregation pipeline stopped. Stats: {}", getStats());
    }

    // === Types ===

    /**
     * DD59: Unified log event structure.
     */
    public record LogEvent(
        String eventName,
        Instant timestamp,
        Severity severity,
        Map<String, Object> data
    ) {
        public static LogEvent fromTelemetry(ArenaTelemetry.TelemetryEvent event) {
            Severity severity = inferSeverity(event.name());
            return new LogEvent(event.name(), event.timestamp(), severity, event.data());
        }

        private static Severity inferSeverity(String eventName) {
            if (eventName.contains("error") || eventName.contains("failed")) {
                return Severity.ERROR;
            } else if (eventName.contains("warn")) {
                return Severity.WARN;
            } else if (eventName.contains("debug")) {
                return Severity.DEBUG;
            }
            return Severity.INFO;
        }

        public LogEvent withData(String key, Object value) {
            Map<String, Object> newData = new LinkedHashMap<>(data);
            newData.put(key, value);
            return new LogEvent(eventName, timestamp, severity, newData);
        }
    }

    public enum Severity {
        DEBUG, INFO, WARN, ERROR
    }

    /**
     * DD59: Destination for log events.
     */
    public interface LogDestination extends AutoCloseable {
        String name();
        void write(List<LogEvent> events) throws Exception;
        @Override
        void close();
    }

    /**
     * DD59: Event transformer for enrichment.
     */
    @FunctionalInterface
    public interface EventTransformer {
        LogEvent transform(LogEvent event);
    }

    /**
     * Pipeline statistics.
     */
    public record PipelineStats(
        long received,
        long filtered,
        long written,
        long dropped,
        int queueSize
    ) {
        public double dropRate() {
            return received > 0 ? (double) dropped / received : 0;
        }

        public double filterRate() {
            return received > 0 ? (double) filtered / received : 0;
        }
    }

    // === Built-in Destinations ===

    /**
     * DD59: NDJSON file destination.
     */
    public static class NdjsonDestination implements LogDestination {
        private final NdjsonWriter writer;

        public NdjsonDestination(Path logPath) {
            this.writer = new NdjsonWriter(logPath.getParent(), logPath.getFileName().toString());
        }

        public NdjsonDestination(NdjsonWriter writer) {
            this.writer = writer;
        }

        @Override
        public String name() {
            return "ndjson";
        }

        @Override
        public void write(List<LogEvent> events) {
            for (LogEvent event : events) {
                Map<String, Object> enriched = new LinkedHashMap<>(event.data());
                enriched.put("event", event.eventName());
                enriched.put("timestamp", event.timestamp().toString());
                enriched.put("severity", event.severity().name());
                writer.write(enriched);
            }
        }

        @Override
        public void close() {
            writer.close();
        }
    }

    /**
     * DD59: Console destination for development.
     */
    public static class ConsoleDestination implements LogDestination {
        private static final Logger LOG = LoggerFactory.getLogger("arena.pipeline");

        @Override
        public String name() {
            return "console";
        }

        @Override
        public void write(List<LogEvent> events) {
            for (LogEvent event : events) {
                switch (event.severity()) {
                    case ERROR -> LOG.error("[{}] {}", event.eventName(), event.data());
                    case WARN -> LOG.warn("[{}] {}", event.eventName(), event.data());
                    case DEBUG -> LOG.debug("[{}] {}", event.eventName(), event.data());
                    default -> LOG.info("[{}] {}", event.eventName(), event.data());
                }
            }
        }

        @Override
        public void close() {
            // No-op
        }
    }

    // === Builder ===

    public static class Builder {
        private final List<LogDestination> destinations = new ArrayList<>();
        private final List<Predicate<LogEvent>> filters = new ArrayList<>();
        private final List<EventTransformer> transformers = new ArrayList<>();
        private int batchSize = DEFAULT_BATCH_SIZE;
        private Duration flushInterval = DEFAULT_FLUSH_INTERVAL;
        private int queueCapacity = DEFAULT_QUEUE_CAPACITY;

        public Builder addDestination(LogDestination destination) {
            destinations.add(destination);
            return this;
        }

        public Builder addNdjsonDestination(Path logPath) {
            destinations.add(new NdjsonDestination(logPath));
            return this;
        }

        public Builder addConsoleDestination() {
            destinations.add(new ConsoleDestination());
            return this;
        }

        public Builder addFilter(Predicate<LogEvent> filter) {
            filters.add(filter);
            return this;
        }

        public Builder filterByEventName(String pattern) {
            filters.add(event -> event.eventName().matches(pattern));
            return this;
        }

        public Builder filterBySeverity(Severity minSeverity) {
            filters.add(event -> event.severity().ordinal() >= minSeverity.ordinal());
            return this;
        }

        public Builder addTransformer(EventTransformer transformer) {
            transformers.add(transformer);
            return this;
        }

        public Builder enrichWithHostname(String hostname) {
            transformers.add(event -> event.withData("hostname", hostname));
            return this;
        }

        public Builder enrichWithEnvironment(String env) {
            transformers.add(event -> event.withData("environment", env));
            return this;
        }

        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public Builder flushInterval(Duration flushInterval) {
            this.flushInterval = flushInterval;
            return this;
        }

        public Builder queueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
            return this;
        }

        public LogAggregationPipeline build() {
            if (destinations.isEmpty()) {
                throw new IllegalStateException("At least one destination is required");
            }
            return new LogAggregationPipeline(this);
        }
    }
}
