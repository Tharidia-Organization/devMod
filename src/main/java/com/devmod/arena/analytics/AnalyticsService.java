package com.devmod.arena.analytics;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnalyticsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnalyticsService.class);

    private static final AnalyticsService INSTANCE = new AnalyticsService();

    /** Query executor with bounded thread pool */
    private final ExecutorService queryExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "Analytics-Query");
        t.setDaemon(true);
        return t;
    });

    /** Async export executor (single thread for sequential processing) */
    private final ExecutorService exportExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Analytics-Export");
        t.setDaemon(true);
        return t;
    });

    /** Active export jobs */
    private final Map<String, ExportJob> activeExports = new ConcurrentHashMap<>();

    /** Query result provider (to be set by the actual analytics backend) */
    private QueryProvider queryProvider;

    private AnalyticsService() {}

    public static AnalyticsService getInstance() {
        return INSTANCE;
    }

    /**
     * Sets the query provider implementation
     *
     * @param provider The query provider
     */
    public void setQueryProvider(QueryProvider provider) {
        this.queryProvider = provider;
    }

    /**
     * Executes a query with timeout enforcement
     *
     * @param params The validated query parameters
     * @return The query result
     * @throws QueryTimeoutException if query exceeds timeout
     * @throws QueryException if query fails
     */
    public QueryResult executeQuery(AnalyticsQueryParams params) throws QueryTimeoutException, QueryException {
        if (queryProvider == null) {
            throw new QueryException("Query provider not configured");
        }

        // Check if async export is required
        if (params.requiresAsyncExport()) {
            throw new QueryException(
                    "Date range exceeds 30 days. Use submitExportJob() for large exports.");
        }

        Future<QueryResult> future = queryExecutor.submit(() -> queryProvider.execute(params));

        try {
            return future.get(params.getTimeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            LOGGER.warn("Query timeout after {}ms", params.getTimeoutMillis());
            throw new QueryTimeoutException("Query exceeded " + params.getTimeout().toSeconds() + " second timeout");
        } catch (ExecutionException e) {
            throw new QueryException("Query execution failed: " + e.getCause().getMessage(), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new QueryException("Query interrupted", e);
        }
    }

    /**
     * Submits an async export job for large date ranges (> 30 days)
     *
     * @param paramsBuilder The query parameters (date range validation is skipped)
     * @param onComplete Callback when export completes
     * @return The export job ID
     */
    public String submitExportJob(AnalyticsQueryParams.Builder paramsBuilder, Consumer<ExportResult> onComplete) {
        String jobId = UUID.randomUUID().toString();

        ExportJob job = new ExportJob(
                jobId,
                paramsBuilder,
                onComplete,
                Instant.now()
        );

        activeExports.put(jobId, job);

        exportExecutor.execute(() -> executeExportJob(job));

        LOGGER.info("Submitted export job: {}", jobId);
        return jobId;
    }

    /**
     * Executes an export job (runs in background)
     */
    private void executeExportJob(ExportJob job) {
        LOGGER.info("Starting export job: {}", job.jobId());

        try {
            job.updateStatus(ExportStatus.RUNNING);

            // Build params without validation (allow > 30 days)
            AnalyticsQueryParams.Builder builder = job.paramsBuilder();

            // Execute in chunks to avoid memory issues
            List<Map<String, Object>> allResults = new ArrayList<>();
            int page = 0;
            boolean hasMore = true;

            while (hasMore) {
                AnalyticsQueryParams chunkParams = AnalyticsQueryParams.builder()
                        .startDate(builder.build().getStartDate())
                        .endDate(builder.build().getEndDate())
                        .page(page)
                        .pageSize(AnalyticsQueryParams.MAX_PAGE_SIZE)
                        .build();

                QueryResult chunkResult = queryProvider.execute(chunkParams);
                allResults.addAll(chunkResult.rows());

                hasMore = chunkResult.hasMore();
                page++;

                job.updateProgress(allResults.size());
            }

            // Complete
            ExportResult result = new ExportResult(
                    job.jobId(),
                    true,
                    allResults.size(),
                    null,
                    Instant.now()
            );

            job.updateStatus(ExportStatus.COMPLETED);
            job.onComplete().accept(result);

            LOGGER.info("Completed export job: {} with {} rows", job.jobId(), allResults.size());

        } catch (Exception e) {
            LOGGER.error("Export job failed: {}", job.jobId(), e);

            job.updateStatus(ExportStatus.FAILED);

            ExportResult result = new ExportResult(
                    job.jobId(),
                    false,
                    0,
                    e.getMessage(),
                    Instant.now()
            );

            job.onComplete().accept(result);
        } finally {
            // Keep job in map for status queries, but mark as done
            activeExports.computeIfPresent(job.jobId(), (k, v) -> {
                v.markComplete();
                return v;
            });
        }
    }

    /**
     * Gets the status of an export job
     *
     * @param jobId The job ID
     * @return The job status, or empty if not found
     */
    public Optional<ExportJobStatus> getExportJobStatus(String jobId) {
        ExportJob job = activeExports.get(jobId);
        if (job == null) {
            return Optional.empty();
        }

        return Optional.of(new ExportJobStatus(
                job.jobId(),
                job.status(),
                job.processedRows(),
                job.startTime(),
                job.isComplete() ? Duration.between(job.startTime(), Instant.now()) : null
        ));
    }

    /**
     * Cancels an export job
     *
     * @param jobId The job ID
     * @return true if job was found and cancelled
     */
    public boolean cancelExportJob(String jobId) {
        ExportJob job = activeExports.remove(jobId);
        if (job != null) {
            job.updateStatus(ExportStatus.CANCELLED);
            return true;
        }
        return false;
    }

    /**
     * Gets count of active export jobs
     */
    public int getActiveExportCount() {
        return (int) activeExports.values().stream()
                .filter(j -> !j.isComplete())
                .count();
    }

    /**
     * Shuts down the service
     */
    public void shutdown() {
        queryExecutor.shutdown();
        exportExecutor.shutdown();

        try {
            if (!queryExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                queryExecutor.shutdownNow();
            }
            if (!exportExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                exportExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            queryExecutor.shutdownNow();
            exportExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Query provider interface (implemented by actual analytics backend)
     */
    public interface QueryProvider {
        QueryResult execute(AnalyticsQueryParams params) throws Exception;
    }

    /**
     * Query result record
     */
    public record QueryResult(
        List<Map<String, Object>> rows,
        int totalCount,
        boolean hasMore,
        Duration executionTime
    ) {}

    /**
     * Export job status enum
     */
    public enum ExportStatus {
        PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
    }

    /**
     * Export job tracking
     */
    private static class ExportJob {
        private final String jobId;
        private final AnalyticsQueryParams.Builder paramsBuilder;
        private final Consumer<ExportResult> onComplete;
        private final Instant startTime;
        private volatile ExportStatus status = ExportStatus.PENDING;
        private volatile int processedRows = 0;
        private volatile boolean complete = false;

        ExportJob(String jobId, AnalyticsQueryParams.Builder paramsBuilder,
                  Consumer<ExportResult> onComplete, Instant startTime) {
            this.jobId = jobId;
            this.paramsBuilder = paramsBuilder;
            this.onComplete = onComplete;
            this.startTime = startTime;
        }

        String jobId() { return jobId; }
        AnalyticsQueryParams.Builder paramsBuilder() { return paramsBuilder; }
        Consumer<ExportResult> onComplete() { return onComplete; }
        Instant startTime() { return startTime; }
        ExportStatus status() { return status; }
        int processedRows() { return processedRows; }
        boolean isComplete() { return complete; }

        void updateStatus(ExportStatus status) { this.status = status; }
        void updateProgress(int rows) { this.processedRows = rows; }
        void markComplete() { this.complete = true; }
    }

    /**
     * Export job status record
     */
    public record ExportJobStatus(
        String jobId,
        ExportStatus status,
        int processedRows,
        Instant startTime,
        Duration duration
    ) {}

    /**
     * Export result record
     */
    public record ExportResult(
        String jobId,
        boolean success,
        int totalRows,
        String errorMessage,
        Instant completedAt
    ) {}

    /**
     * Query timeout exception
     */
    public static class QueryTimeoutException extends Exception {
        private static final long serialVersionUID = 1L;
        public QueryTimeoutException(String message) {
            super(message);
        }
    }

    /**
     * General query exception
     */
    public static class QueryException extends Exception {
        private static final long serialVersionUID = 1L;
        public QueryException(String message) {
            super(message);
        }

        public QueryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
