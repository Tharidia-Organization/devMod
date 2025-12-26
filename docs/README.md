# DevMod Documentation

> Last updated: 2025-12-26
> Status: NEEDS_VERIFICATION

## Start Here
- [[MOC]] - Master index (curated)
- [[PROJECT_TOPOLOGY]] - Package and resource layout
- [[ARCHITECTURE]] - High level architecture
- [[ENTRYPOINTS]] - Key entrypoints and triggers
- [[GLOSSARY]] - Terms and domain language
- [[FEATURES]] - Feature overview
- [[TRACEABILITY_MATRIX]] - Feature to component mapping
- [[AUDIT_REPORT]] - Audit findings (historical snapshot)

## Documentation Status
- [[DOCUMENTATION_STATUS]] - Status by area and cleanup backlog
- [[DOCS_GUIDE]] - Where docs live and how to update them
- [[DOCS_VERIFICATION_REPORT]] - Automated link/path checks

## Area Dossiers (core systems)
- [[areas/arena/README]] - Arena templates and lifecycle
- [[areas/endurance/README]] - Endurance quest system
- [[areas/instance/README]] - Instance dimension system
- [[areas/telemetry/README]] - Telemetry pipelines
- [[areas/radial/README]] - Radial UX and navigation
- [[areas/client_server/README]] - Client/server boundaries
- [[areas/config/README]] - Config and feature flags
- [[areas/tools/README]] - QA tools and automation

## Cross-Cutting Concerns
- [[cross_cutting/CONCURRENCY]]
- [[cross_cutting/CLIENT_SERVER]]
- [[cross_cutting/TELEMETRY_CONVENTIONS]]
- [[cross_cutting/ERROR_HANDLING]]

## Subsystems and Deep Dives
- [[subsystems/arena-template-rework/README]] - Arena template rework (specs + audits)
- [[subsystems/editor-design-system/README]] - Editor design system
- [[subsystems/impact-hud-audit/README]] - Impact HUD audit
- [[subsystems/prismatic-shield-integration/00-overview]] - Prismatic shield integration
- [[subsystems/recipe-editor-spec/README]] - Recipe editor spec (planning)

## Design and UX
- [[GAME_DESIGN_ANALYSIS]]
- [[design/GAME_DESIGN_EVOLUTION]]
- [[design/GAME_DESIGN_ROADMAP]]
- [[UX_PLAYER_JOURNEY]]
- [[ui/UI_INVENTORY]]
- [[ui/UI_NAVIGATION_MAP]]
- [[ui/UI_AUDIT_FINDINGS]]
- [[ui/UI_LOCALIZATION_TODO]]

## Testing and QA
- [[testing/TESTING]] - Testing guide
- [[testing/TEST_HARNESS]] - Harness setup
- [[testing/PROGRESSIVE_TEST_PLAN]] - Progressive plan
- [[_deprecated/testing-reports/L0_REPORT]] through [[_deprecated/testing-reports/L6_REPORT]] - Test reports (historical snapshots)
- [[testing/QA_WEAPON_PROPERTIES]]
- [[testing/TEST_WORLD_SETUP]]

## Operations, Telemetry, and Runbooks
- [[telemetry/TELEMETRY_DOCUMENTATION]]
- [[telemetry/duckdb/DUCKDB_MIGRATION_AUDIT]]
- [[telemetry/dashboard/DASHBOARD_UPGRADE_PLAN]]
- [[telemetry/MISSING_TELEMETRY_HOOKS]]
- [[runbook/arena-alerts]]
- [[infrastructure/INFRASTRUCTURE_PLAN]]

## Compatibility and Network
- [[compat/README]]
- [[compat/MOD_INVENTORY]]
- [[network/SECURITY_HARDENING]]
- [[network/PACKET_REGISTRY]]

## Audits, Quality, and Remediation
- [[_deprecated/audit/CLIENT_SERVER_REMEDIATION]]
- [[_deprecated/audit/MIXIN_SIDE_SAFETY]]
- [[_deprecated/audit/STATIC_STATE_AND_LEAKS]]
- [[_deprecated/quality/BASELINE]]
- [[_deprecated/quality/CORE_REVIEW]]
- [[_deprecated/quality/REFACTOR_PLAN]]
- [[_deprecated/quality/LOGGING_GUIDELINES]]
- [[_deprecated/remediation/VERIFY]]
- [[_deprecated/remediation/FINAL_REPORT]]
- [[_deprecated/remediation/CHANGELOG_REMEDIATION]]
- [[_deprecated/remediation/DEDICATED_SERVER_READINESS]]

## Planning and Project Tracking
- [[project/IMPLEMENTATION_STATUS]]
- [[project/NEXT_STEPS]]
- [[project/TODO_WARNINGS]]
- [[project/BUG_LOG]]
- [[project/pr-drafts/PR_DRAFT_Debug_MultiEdit]]

## Decisions (ADRs)
- [[adr/ADR-001-endurance-quest-manager]]
- [[adr/ADR-002-devmod-client-actions]]
- [[adr/ADR-003-item-editor-screen]]

## Tools
- [[tools/mcp/codex-mcp-server/README]]

## Archive
- [[_deprecated/]] - Deprecated and historical docs
- [[_deprecated/reorg/REORG_COMPLETE]] - 2024 reorganization notes

*Use [[MOC]] for navigation and [[DOCUMENTATION_STATUS]] for trust level and cleanup tracking.*
