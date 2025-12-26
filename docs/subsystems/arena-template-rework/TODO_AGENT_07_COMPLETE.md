# Agent 07 - Operations & Security (DD 29-36) - COMPLETE

> **Last Updated**: 2025-12-26
> **Status**: ✅ CURRENT

## Summary
All tasks for Agent 07 have been implemented. This agent focused on security, permissions, dashboard authentication, and production guards.

## Design Decisions Implemented

| DD | Description | Status |
|----|-------------|--------|
| DD29 | forceTemplateId Persistence - session state + capability per relog | DONE |
| DD30 | HUD Visibility - permission + toggle esplicito | DONE |
| DD31 | Command Permissions - modello granulare + audit log | DONE |
| DD32 | Autosmoke Production Guard - triple guard ENV+flag+file | DONE |
| DD33 | Autosmoke Assert Exceptions - soglie per size + whitelist | DONE |
| DD34 | Report Export Context - header con git commit, config hash | DONE |
| DD35 | Dashboard Auth - token + cache + background refresh | DONE |
| DD36 | Analytics Query Limits - 30 giorni max, pagination, timeout 10s | DONE |

## Files Created

### Override System (DD29)
- `src/main/java/com/devmod/arena/override/TemplateOverrideManager.java`
  - Session-based override storage with TTL (default 1h)
  - Persistent override via NeoForge Data Attachments
  - Automatic restore on player relog
  - Cleanup of expired overrides

- `src/main/java/com/devmod/arena/override/TemplateOverrideCapability.java`
  - NeoForge AttachmentType registration
  - Codec-based serialization for player data persistence
  - copyOnDeath() for override survival

### Debug HUD (DD30)
- `src/main/java/com/devmod/client/arena/hud/ArenaDebugHud.java`
  - Permission check before rendering
  - Toggle check (default OFF)
  - Debug info display with template, state, players, duration
  - Builder pattern for ArenaDebugInfo

- `src/main/java/com/devmod/arena/ArenaDebugState.java`
  - Per-player HUD enabled state
  - Global HUD toggle
  - Default OFF behavior

### Security & Permissions (DD31)
- `src/main/java/com/devmod/arena/security/ArenaCommandPermissions.java`
  - 7 permission levels: VIEWER, PARTICIPANT, SPECTATOR, CREATOR, MODERATOR, ADMIN, SUPERADMIN
  - 20+ command categories with required levels
  - Mutating vs non-mutating command distinction
  - Op-level based automatic permission promotion
  - Explicit player permission overrides

- `src/main/java/com/devmod/arena/security/ArenaCommandAudit.java`
  - Separate logger "arena.audit" for easy filtering
  - Structured log format for parsing
  - Logs: command execution, permission denied, permission changes, arena events, security events
  - Configurable: enable/disable, log non-mutating commands

### Autosmoke Guards (DD32, DD33, DD34)
- `src/main/java/com/devmod/arena/autosmoke/AutosmokeGuard.java`
  - Triple guard: ENV variable + feature flag + .production marker
  - ALL three checks must pass for autosmoke to run
  - Detailed GuardResult with block reasons

- `src/main/java/com/devmod/arena/autosmoke/AutosmokeThresholds.java`
  - Preset thresholds (STRICT, LARGE, ASYNC)
  - Template-specific threshold overrides
  - Validation with detailed violation reporting

- `src/main/java/com/devmod/arena/autosmoke/AutosmokeSizeThresholds.java`
  - Size categories (SMALL/MEDIUM/LARGE/XLARGE)
  - Build/rollback time and entity thresholds per size
  - Whitelist support and mutable threshold updates

- `src/main/java/com/devmod/arena/autosmoke/AutosmokeExceptions.java`
  - Whitelist management for templates
  - Exception categories: PLAYER_COUNT, DURATION, MEMORY, ALL_THRESHOLDS, PRODUCTION_ALLOWED, NAMED_ASSERTION
  - Named assertion whitelisting

- `src/main/java/com/devmod/arena/autosmoke/AutosmokeReportHeader.java`
  - Captures git commit, branch, build time, mod version
  - Config hash (SHA-256, first 12 chars)
  - Runtime info: Java version, OS, memory, CPUs
  - Multiple output formats: multi-line, compact, JSON

- `src/main/java/com/devmod/arena/autosmoke/AutosmokeScheduler.java`
  - Daily scheduling (default 03:00) with optional run-on-startup
  - Optional AlertRouter integration for failure alerts

### Dashboard & Analytics (DD35, DD36)
- `src/main/java/com/devmod/arena/dashboard/ArenaDashboardEndpoint.java`
  - Token-based authentication with Bearer header
  - Rate limiting: 60 req/min per token
  - Token expiry (24 hours default)
  - Metrics cache with 5-minute background refresh
  - Token permissions (readOnly, full)

- `src/main/java/com/devmod/arena/analytics/AnalyticsQueryParams.java`
  - Maximum 30-day date range validation
  - Pagination with configurable page size (max 1000)
  - Query timeout: 10 seconds
  - Async export detection for > 30 days
  - Builder pattern with safe build option

- `src/main/java/com/devmod/arena/analytics/AnalyticsService.java`
  - Query execution with timeout enforcement
  - Async export job system for large date ranges
  - Job status tracking and cancellation
  - QueryProvider interface for backend implementation

### Configuration Files
- `src/main/resources/build.properties`
  - Template for git/build info (populated at build time)

- `src/main/resources/log4j2-arena-audit.xml`
  - Configuration for arena.audit logger
  - Console + rolling file appenders
  - 30-day retention for audit logs

- `docs/subsystems/arena-template-rework/PRODUCTION_MARKER_README.md`
  - Documentation for .production marker file usage

### Build Configuration
- `build.gradle` (modified)
  - Added `generateBuildProperties` task
  - Captures git commit, branch, build time at build
  - Runs before processResources

## Dependencies

- **Depends on**: AlertRouter (optional) via AutosmokeScheduler
- **Outputs consumed by**: Agent 10 (Operational Readiness)

## Shared Resources

The following files may be modified by other agents:
- `ArenaCommandPermissions.java` - May be extended with new command categories
- `ArenaDashboardEndpoint.java` - May be integrated with other dashboard features

## Unit Tests Required (for Agent 12)

1. TemplateOverrideManager
   - Session state persistence
   - Capability persistence (relog)
   - TTL expiry

2. ArenaDebugHud
   - Permission check (block without permission)
   - Toggle check (default OFF)

3. ArenaCommandAudit
   - Log format validation

4. AutosmokeGuard
   - ENV check
   - Feature flag check
   - .production marker check

5. AutosmokeThresholds
   - forTemplate() selection logic

6. AutosmokeSizeThresholds
   - Size category selection and whitelist behavior

7. AutosmokeExceptions
   - Whitelist lookup

8. AutosmokeReportHeader
   - Capture with all fields

9. ArenaDashboardEndpoint
   - Auth (401 without token)
   - Rate limit (429 when exceeded)

10. AnalyticsQueryParams
    - Validation (date range, pagination)

11. AnalyticsService
    - Timeout (10 sec)

## Notes

- Singleton accessors used by TemplateOverrideManager, ArenaDebugHud, ArenaCommandPermissions, ArenaCommandAudit, AutosmokeGuard
- AutosmokeThresholds and AutosmokeSizeThresholds are value classes (not singletons)
- Thread-safe structures where needed (ConcurrentHashMap, volatile flags)
- Daemon threads used for background tasks (dashboard cache refresh, analytics export)
- Graceful shutdown methods provided for executor services
