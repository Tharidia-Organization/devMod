-- DuckDB Schema for Arena Template Observability (DD21)
-- Agent 05: Observability & Persistence

-- ============================================================================
-- Table: arena_template_builds
-- Stores build/compile events for arena templates
-- ============================================================================
CREATE TABLE IF NOT EXISTS arena_template_builds (
    -- Primary identification
    build_id UUID PRIMARY KEY,
    template_id VARCHAR NOT NULL,
    template_version VARCHAR NOT NULL,

    -- Build timing
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    duration_ms BIGINT,

    -- Build result
    status VARCHAR NOT NULL CHECK (status IN ('pending', 'building', 'success', 'failed', 'cancelled')),
    error_type VARCHAR,
    error_message VARCHAR,

    -- Build context
    builder_version VARCHAR,
    configuration_hash VARCHAR,
    source_hash VARCHAR,

    -- Resource metrics
    memory_used_mb INTEGER,
    cpu_time_ms BIGINT,
    output_size_bytes BIGINT,

    -- Metadata
    environment VARCHAR DEFAULT 'production',
    triggered_by VARCHAR,
    tags VARCHAR[], -- Array of tags for filtering

    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- Table: arena_template_usage
-- Stores usage/session events for arena templates
-- ============================================================================
CREATE TABLE IF NOT EXISTS arena_template_usage (
    -- Primary identification
    usage_id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    template_id VARCHAR NOT NULL,
    template_version VARCHAR NOT NULL,

    -- Session timing
    session_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    session_ended_at TIMESTAMP WITH TIME ZONE,
    duration_ms BIGINT,

    -- Session state
    status VARCHAR NOT NULL CHECK (status IN ('active', 'completed', 'failed', 'timeout', 'cancelled')),

    -- Version drift detection (DD16)
    initial_version VARCHAR,
    final_version VARCHAR,
    version_drift_detected BOOLEAN DEFAULT FALSE,
    configuration_drift_detected BOOLEAN DEFAULT FALSE,

    -- Usage metrics
    actions_count INTEGER DEFAULT 0,
    errors_count INTEGER DEFAULT 0,
    warnings_count INTEGER DEFAULT 0,

    -- Resource usage
    peak_memory_mb INTEGER,
    total_cpu_time_ms BIGINT,
    network_bytes_sent BIGINT,
    network_bytes_received BIGINT,

    -- User context (anonymized)
    user_id_hash VARCHAR,
    client_type VARCHAR,
    client_version VARCHAR,
    region VARCHAR,

    -- Snapshot reference
    initial_snapshot_checksum VARCHAR,
    final_snapshot_checksum VARCHAR,

    -- Metadata
    environment VARCHAR DEFAULT 'production',
    tags VARCHAR[],
    metadata JSON,

    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- Table: arena_template_errors
-- Stores error events with full context (DD18)
-- ============================================================================
CREATE TABLE IF NOT EXISTS arena_template_errors (
    -- Primary identification
    error_id UUID PRIMARY KEY,

    -- Error context
    error_type VARCHAR NOT NULL,
    error_message VARCHAR,
    severity VARCHAR NOT NULL CHECK (severity IN ('DEBUG', 'INFO', 'WARNING', 'ERROR', 'CRITICAL')),
    component VARCHAR NOT NULL,

    -- Stacktrace as JSON array (DD18: max 20 frames)
    stack_frames JSON,

    -- Related entities
    session_id UUID,
    build_id UUID,
    template_id VARCHAR,

    -- Error timing
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,

    -- Alert routing status (DD19)
    alert_routed BOOLEAN DEFAULT FALSE,
    alert_channels VARCHAR[],
    alert_delivery_status JSON,

    -- Metadata
    metadata JSON,

    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- Table: arena_template_alerts
-- Stores alert delivery records (DD19)
-- ============================================================================
CREATE TABLE IF NOT EXISTS arena_template_alerts (
    -- Primary identification
    alert_id UUID PRIMARY KEY,
    error_id UUID NOT NULL,

    -- Channel information
    channel_id VARCHAR NOT NULL,
    channel_type VARCHAR NOT NULL,
    is_critical BOOLEAN DEFAULT FALSE,

    -- Delivery status
    delivery_status VARCHAR NOT NULL CHECK (delivery_status IN ('pending', 'delivered', 'failed', 'retrying')),
    attempt_count INTEGER DEFAULT 0,
    max_attempts INTEGER DEFAULT 5,

    -- Timing
    first_attempt_at TIMESTAMP WITH TIME ZONE,
    last_attempt_at TIMESTAMP WITH TIME ZONE,
    delivered_at TIMESTAMP WITH TIME ZONE,
    next_retry_at TIMESTAMP WITH TIME ZONE,

    -- Error info (if failed)
    last_error VARCHAR,

    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- INDICES for Dashboard Queries (DD21: 5 indices, <200ms query time)
-- ============================================================================

-- Index 1: Builds by template and time (dashboard: build history)
CREATE INDEX IF NOT EXISTS idx_builds_template_time
ON arena_template_builds (template_id, started_at DESC);

-- Index 2: Usage by template and time (dashboard: usage trends)
CREATE INDEX IF NOT EXISTS idx_usage_template_time
ON arena_template_usage (template_id, session_started_at DESC);

-- Index 3: Errors by severity and time (dashboard: error monitoring)
CREATE INDEX IF NOT EXISTS idx_errors_severity_time
ON arena_template_errors (severity, occurred_at DESC);

-- Index 4: Version drift detection (dashboard: drift alerts)
CREATE INDEX IF NOT EXISTS idx_usage_version_drift
ON arena_template_usage (version_drift_detected, session_started_at DESC)
WHERE version_drift_detected = TRUE;

-- Index 5: Pending alerts for retry processing (DD19)
CREATE INDEX IF NOT EXISTS idx_alerts_pending_retry
ON arena_template_alerts (delivery_status, next_retry_at)
WHERE delivery_status IN ('pending', 'retrying');

-- ============================================================================
-- Additional Performance Indices
-- ============================================================================

-- Build status for active builds monitoring
CREATE INDEX IF NOT EXISTS idx_builds_status
ON arena_template_builds (status, started_at DESC)
WHERE status IN ('pending', 'building');

-- Usage session status for active sessions
CREATE INDEX IF NOT EXISTS idx_usage_status
ON arena_template_usage (status, session_started_at DESC)
WHERE status = 'active';

-- Errors by component for component-level analysis
CREATE INDEX IF NOT EXISTS idx_errors_component
ON arena_template_errors (component, occurred_at DESC);

-- ============================================================================
-- Views for Common Dashboard Queries
-- ============================================================================

-- View: Recent build summary
CREATE VIEW IF NOT EXISTS v_recent_builds AS
SELECT
    template_id,
    template_version,
    COUNT(*) as total_builds,
    COUNT(*) FILTER (WHERE status = 'success') as successful_builds,
    COUNT(*) FILTER (WHERE status = 'failed') as failed_builds,
    AVG(duration_ms) as avg_duration_ms,
    MAX(started_at) as last_build_at
FROM arena_template_builds
WHERE started_at >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
GROUP BY template_id, template_version;

-- View: Usage summary by template
CREATE VIEW IF NOT EXISTS v_usage_summary AS
SELECT
    template_id,
    template_version,
    COUNT(*) as total_sessions,
    COUNT(*) FILTER (WHERE status = 'completed') as completed_sessions,
    COUNT(*) FILTER (WHERE version_drift_detected) as drift_detected_count,
    AVG(duration_ms) as avg_session_duration_ms,
    SUM(actions_count) as total_actions,
    SUM(errors_count) as total_errors
FROM arena_template_usage
WHERE session_started_at >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
GROUP BY template_id, template_version;

-- View: Error summary by severity
CREATE VIEW IF NOT EXISTS v_error_summary AS
SELECT
    severity,
    component,
    error_type,
    COUNT(*) as error_count,
    MAX(occurred_at) as last_occurred_at
FROM arena_template_errors
WHERE occurred_at >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
GROUP BY severity, component, error_type
ORDER BY
    CASE severity
        WHEN 'CRITICAL' THEN 1
        WHEN 'ERROR' THEN 2
        WHEN 'WARNING' THEN 3
        ELSE 4
    END,
    error_count DESC;

-- View: Alert delivery status
CREATE VIEW IF NOT EXISTS v_alert_status AS
SELECT
    channel_id,
    delivery_status,
    COUNT(*) as alert_count,
    AVG(attempt_count) as avg_attempts,
    MAX(last_attempt_at) as last_attempt
FROM arena_template_alerts
WHERE created_at >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
GROUP BY channel_id, delivery_status;

-- ============================================================================
-- Maintenance Procedures
-- ============================================================================

-- Note: DuckDB doesn't support stored procedures in the same way as PostgreSQL
-- These are provided as example queries to be run periodically

-- Cleanup old data (run daily, keep 14 days as per DD17)
-- DELETE FROM arena_template_builds WHERE created_at < CURRENT_TIMESTAMP - INTERVAL '14 days';
-- DELETE FROM arena_template_usage WHERE created_at < CURRENT_TIMESTAMP - INTERVAL '14 days';
-- DELETE FROM arena_template_errors WHERE created_at < CURRENT_TIMESTAMP - INTERVAL '14 days';
-- DELETE FROM arena_template_alerts WHERE created_at < CURRENT_TIMESTAMP - INTERVAL '14 days';

-- ============================================================================
-- Example Queries for Performance Verification (DD21: <200ms target)
-- ============================================================================

-- Query 1: Build history for template (should use idx_builds_template_time)
-- EXPLAIN ANALYZE SELECT * FROM arena_template_builds
-- WHERE template_id = 'example-template'
-- ORDER BY started_at DESC LIMIT 100;

-- Query 2: Usage trends (should use idx_usage_template_time)
-- EXPLAIN ANALYZE SELECT DATE_TRUNC('hour', session_started_at) as hour, COUNT(*)
-- FROM arena_template_usage
-- WHERE template_id = 'example-template'
-- AND session_started_at >= CURRENT_TIMESTAMP - INTERVAL '7 days'
-- GROUP BY 1 ORDER BY 1;

-- Query 3: Critical errors (should use idx_errors_severity_time)
-- EXPLAIN ANALYZE SELECT * FROM arena_template_errors
-- WHERE severity IN ('CRITICAL', 'ERROR')
-- AND occurred_at >= CURRENT_TIMESTAMP - INTERVAL '1 hour'
-- ORDER BY occurred_at DESC;

-- Query 4: Version drift sessions (should use idx_usage_version_drift)
-- EXPLAIN ANALYZE SELECT * FROM arena_template_usage
-- WHERE version_drift_detected = TRUE
-- ORDER BY session_started_at DESC LIMIT 50;

-- Query 5: Pending alert retries (should use idx_alerts_pending_retry)
-- EXPLAIN ANALYZE SELECT * FROM arena_template_alerts
-- WHERE delivery_status IN ('pending', 'retrying')
-- AND next_retry_at <= CURRENT_TIMESTAMP;
