package com.devmod.arena.analytics;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DD36: Analytics Query Limits - 30 giorni max, pagination, timeout 10s
 *
 * Validated query parameters for analytics queries.
 * Enforces:
 * - Maximum 30-day date range
 * - Pagination with configurable page size
 * - Query timeout of 10 seconds
 */
public class AnalyticsQueryParams {

    /** Maximum date range in days */
    public static final int MAX_DATE_RANGE_DAYS = 30;

    /** Default page size */
    public static final int DEFAULT_PAGE_SIZE = 100;

    /** Maximum page size */
    public static final int MAX_PAGE_SIZE = 1000;

    /** Query timeout duration */
    public static final Duration QUERY_TIMEOUT = Duration.ofSeconds(10);

    private final Instant startDate;
    private final Instant endDate;
    private final int page;
    private final int pageSize;
    private final String templateFilter;
    private final String sortBy;
    private final boolean sortAscending;
    private final List<String> metrics;

    private AnalyticsQueryParams(Builder builder) {
        this.startDate = builder.startDate;
        this.endDate = builder.endDate;
        this.page = builder.page;
        this.pageSize = builder.pageSize;
        this.templateFilter = builder.templateFilter;
        this.sortBy = builder.sortBy;
        this.sortAscending = builder.sortAscending;
        this.metrics = new ArrayList<>(builder.metrics);
    }

    public Instant getStartDate() {
        return startDate;
    }

    public Instant getEndDate() {
        return endDate;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public int getOffset() {
        return page * pageSize;
    }

    public Optional<String> getTemplateFilter() {
        return Optional.ofNullable(templateFilter);
    }

    public String getSortBy() {
        return sortBy;
    }

    public boolean isSortAscending() {
        return sortAscending;
    }

    public List<String> getMetrics() {
        return new ArrayList<>(metrics);
    }

    /**
     * Gets the date range in days
     */
    public long getDateRangeDays() {
        return ChronoUnit.DAYS.between(startDate, endDate);
    }

    /**
     * Checks if this query requires async export (> 30 days)
     */
    public boolean requiresAsyncExport() {
        return getDateRangeDays() > MAX_DATE_RANGE_DAYS;
    }

    /**
     * Gets the query timeout
     */
    public Duration getTimeout() {
        return QUERY_TIMEOUT;
    }

    /**
     * Gets timeout in milliseconds
     */
    public long getTimeoutMillis() {
        return QUERY_TIMEOUT.toMillis();
    }

    /**
     * Creates a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates params for the last N days
     */
    public static AnalyticsQueryParams lastDays(int days) {
        Instant now = Instant.now();
        return builder()
                .startDate(now.minus(Duration.ofDays(Math.min(days, MAX_DATE_RANGE_DAYS))))
                .endDate(now)
                .build();
    }

    /**
     * Builder for AnalyticsQueryParams with validation
     */
    public static class Builder {
        private Instant startDate = Instant.now().minus(Duration.ofDays(7));
        private Instant endDate = Instant.now();
        private int page = 0;
        private int pageSize = DEFAULT_PAGE_SIZE;
        private String templateFilter;
        private String sortBy = "timestamp";
        private boolean sortAscending = false;
        private List<String> metrics = new ArrayList<>();

        public Builder startDate(Instant startDate) {
            this.startDate = startDate;
            return this;
        }

        public Builder startDate(LocalDate date) {
            this.startDate = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
            return this;
        }

        public Builder endDate(Instant endDate) {
            this.endDate = endDate;
            return this;
        }

        public Builder endDate(LocalDate date) {
            this.endDate = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
            return this;
        }

        public Builder page(int page) {
            this.page = Math.max(0, page);
            return this;
        }

        public Builder pageSize(int pageSize) {
            this.pageSize = Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));
            return this;
        }

        public Builder templateFilter(String templateFilter) {
            this.templateFilter = templateFilter;
            return this;
        }

        public Builder sortBy(String sortBy) {
            this.sortBy = sortBy != null ? sortBy : "timestamp";
            return this;
        }

        public Builder sortAscending(boolean ascending) {
            this.sortAscending = ascending;
            return this;
        }

        public Builder addMetric(String metric) {
            this.metrics.add(metric);
            return this;
        }

        public Builder metrics(List<String> metrics) {
            this.metrics = new ArrayList<>(metrics);
            return this;
        }

        /**
         * Validates and builds the query params
         *
         * @return The validated params
         * @throws IllegalArgumentException if validation fails
         */
        public AnalyticsQueryParams build() {
            ValidationResult result = validate();
            if (!result.isValid()) {
                throw new IllegalArgumentException(result.getErrorMessage());
            }
            return new AnalyticsQueryParams(this);
        }

        /**
         * Builds without throwing, returns validation result
         *
         * @return Result containing either the params or validation errors
         */
        public BuildResult buildSafe() {
            ValidationResult result = validate();
            if (!result.isValid()) {
                return BuildResult.failure(result);
            }
            return BuildResult.success(new AnalyticsQueryParams(this));
        }

        /**
         * Validates the current builder state
         */
        public ValidationResult validate() {
            List<String> errors = new ArrayList<>();

            // Date range validation
            if (startDate == null) {
                errors.add("Start date is required");
            }
            if (endDate == null) {
                errors.add("End date is required");
            }

            if (startDate != null && endDate != null) {
                if (startDate.isAfter(endDate)) {
                    errors.add("Start date must be before end date");
                }

                long days = ChronoUnit.DAYS.between(startDate, endDate);
                if (days > MAX_DATE_RANGE_DAYS) {
                    errors.add(String.format(
                            "Date range exceeds maximum of %d days (requested: %d days). Use async export for larger ranges.",
                            MAX_DATE_RANGE_DAYS, days));
                }
            }

            // Pagination validation
            if (page < 0) {
                errors.add("Page must be non-negative");
            }
            if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
                errors.add(String.format("Page size must be between 1 and %d", MAX_PAGE_SIZE));
            }

            return new ValidationResult(errors.isEmpty(), errors);
        }
    }

    /**
     * Validation result
     */
    public record ValidationResult(
        boolean isValid,
        List<String> errors
    ) {
        public String getErrorMessage() {
            return String.join("; ", errors);
        }
    }

    /**
     * Build result (success or failure with validation errors)
     */
    public record BuildResult(
        boolean success,
        AnalyticsQueryParams params,
        ValidationResult validation
    ) {
        public static BuildResult success(AnalyticsQueryParams params) {
            return new BuildResult(true, params, new ValidationResult(true, List.of()));
        }

        public static BuildResult failure(ValidationResult validation) {
            return new BuildResult(false, null, validation);
        }

        public Optional<AnalyticsQueryParams> getParams() {
            return Optional.ofNullable(params);
        }
    }
}
