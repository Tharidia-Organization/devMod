# Error Handling

> Last updated: 2025-12-26
> Status: CURRENT (verified against code)

This doc summarizes the error-handling patterns used across DevMod.

## Logging First

- Most components use `org.slf4j.Logger` for structured logging.
- Errors are logged with module-specific prefixes to ease tracing.

## Domain Exceptions (Arena)

Arena registry and validation errors are modeled explicitly:

- `TemplateLoadException`
- `InheritanceCycleException`
- `ParentTemplateNotFoundException`
- `InheritanceDepthExceededException`

## Fallback and Recovery

- Arena failures are routed through `ArenaFailureHandler` and `FallbackBuildStrategy`.
- Recovery helpers exist for arena templates (`TemplateRecoveryHandler`).
- Instance recovery is handled by `com.devmod.runtime.RecoverySystem`.

## Alert Routing

- `AlertRouter` delivers `ErrorContext` to multiple channels.
- Built-in channels include:
  - `DiscordAlertChannel`
  - `WebhookAlertChannel`
  - `LogAlertChannel`
  - `TelemetryAlertChannel`
  - `ConsoleAlertChannel`
  - `DuckDbAlertRecorder`

## Telemetry Error Classification

- `DuckDBErrorClassifier` classifies DuckDB failures for logging and reporting.
- `RateLimitedLogger` avoids log spam for repeated errors.
