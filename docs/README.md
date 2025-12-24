# DevMod Documentation

> **Last Updated**: 2024-12-23

---

## Quick Navigation

| Document | Purpose |
|----------|---------|
| [[MOC]] | **Master Map of Content** - Central navigation hub |
| [[PROJECT_TOPOLOGY]] | Codebase structure and package layout |
| [[ENTRYPOINTS]] | All entry points (commands, keybinds, events) |
| [[GLOSSARY]] | Key terms and definitions |
| [[AUDIT_REPORT]] | Current audit findings and gaps |
| [[TRACEABILITY_MATRIX]] | Feature → Component → Telemetry mapping |

---

## Area Dossiers

Each area has a dedicated README with architecture, flows, and gaps:

| Area | Dossier | Description |
|------|---------|-------------|
| Arena System | [[areas/arena/README]] | Template, policy, builder, registry |
| Endurance System | [[areas/endurance/README]] | Wave-based roguelike quests |
| Instance System | [[areas/instance/README]] | Dimension management, recovery |
| Telemetry | [[areas/telemetry/README]] | DuckDB, analytics, dashboard |
| Radial Menu/UX | [[areas/radial/README]] | Keybinds, commands, UI navigation |
| Client/Server | [[areas/client_server/README]] | Boundaries, proxies, dist safety |
| Config | [[areas/config/README]] | Feature flags, hot reload |
| Tools/QA | [[areas/tools/README]] | Autosmoke, CI gates, testing |

---

## Cross-Cutting Concerns

| Topic | Document |
|-------|----------|
| Concurrency | [[cross_cutting/CONCURRENCY]] |
| Client/Server Safety | [[cross_cutting/CLIENT_SERVER]] |
| Telemetry Conventions | [[cross_cutting/TELEMETRY_CONVENTIONS]] |
| Error Handling | [[cross_cutting/ERROR_HANDLING]] |

---

## Subsystem Documentation

### Arena Template System
- [[arena-template-rework/README]] - Canonical entrypoint
- [[arena-template-rework/ARENA_TEMPLATE_AUDIT]] - Audit + gap analysis
- [[arena-template-rework/TODO_ARENA_TEMPLATE]] - Spec DD1-DD72

### Editor/UI Systems
- [[editor-design-system/README]] - Editor design system
- [[impact-hud-audit/README]] - Impact HUD audit
- [[recipe-editor-spec/README]] - Recipe editor spec

### Telemetry
- [[telemetry/duckdb/DUCKDB_MIGRATION_AUDIT]] - DuckDB migration
- [[telemetry/dashboard/DASHBOARD_UPGRADE_PLAN]] - Dashboard upgrade

### Testing
- [[testing/TEST_HARNESS]] - Test harness configuration
- [[TESTING]] - Testing guide

---

## Reference Documents

| Category | Document |
|----------|----------|
| Architecture | [[ARCHITECTURE]] |
| Features | [[FEATURES]] |
| Game Design | [[GAME_DESIGN_ANALYSIS]] |
| UX Journey | [[UX_PLAYER_JOURNEY]] |
| Bug Log | [[BUG_LOG]] |
| Rendering | [[RENDERING_AUDIT]] |
| Shaders | [[SHADER_SYSTEM]] |

---

## Project Management

- [[project/NEXT_STEPS]] - Next steps
- [[project/TODO_WARNINGS]] - Warnings & todos
- [[DOCS_INVENTORY]] - Documentation inventory

---

## Archives

- [[_deprecated/]] - Archived/deprecated documentation

---

*Use [[MOC]] as the primary navigation hub for this documentation.*
