# Arena Template Documentation Audit (v2.23)

> **Last Updated**: 2025-12-26
> **Status**: ✅ CURRENT - snapshot allineato con repo

## Scope
- Obiettivo: riallineare la documentazione Arena Template allo stato attuale del codice e all'obiettivo finale v2.23.
- Copertura: spec/template/policy, runbook alert, schemi JSON, migrazione legacy, readiness.
- Data audit: 2025-12-21 (allineamento docs + deprecazioni); refresh 2025-12-26.

## Source of Truth (canonicali)
1. `docs/subsystems/arena-template-rework/TODO_ARENA_TEMPLATE.md` - spec completa DD1-DD72 (v2.23)
2. `docs/subsystems/arena-template-rework/TODO_AGENT_*_COMPLETE.md` - stato implementazione per area
3. `src/main/resources/schemas/arena_template.schema.json` - schema L1 (ArenaTemplate)
4. `src/main/resources/schemas/arena_policy.schema.json` - schema L2 (ArenaPolicy)
5. `docs/runbook/arena-alerts.md` - runbook alert DD68 (48h monitoring)
6. `docs/subsystems/arena-template-rework/MIGRATION_INVENTORY.md` - inventario legacy + stato migrazione
7. `docs/subsystems/arena-template-rework/TODO_GAPS.md` - gap residui post-audit
8. `docs/subsystems/arena-template-rework/README.md` - indice e entrypoint documentale

Nota: le task list storiche `docs/_deprecated/arena-template-rework/TODO_AGENT_*.md` e il coordinatore `docs/_deprecated/arena-template-rework/TODO_AGENT_COORDINATOR.md` sono archivio; lo stato corrente e' nei `*_COMPLETE.md`.

## Stato attuale (snapshot)
- Implementazione core in `src/main/java/com/devmod/arena/*` con pacchetti per registry, builder, budget, metrics, telemetry, observability, recovery, security, cleanup, spawn, gamification, pool, monitoring.
- Schema template/policy presenti e allineati alle decisioni v2.23.
- Runbook alert centralizzato in `docs/runbook/arena-alerts.md`.
- Legacy API monitorate da `com.devmod.arena.migration.WrapperAnalyzer` (pattern di uso legacy).
- Prebuild pool implementata con enable/disable espliciti e criteri DD63; default disabilitata.

## Obiettivo finale (v2.23, spec)
- KPI di rollout: `build_p95 < 5s`, `rollback_rate < 1%`, `completion_rate > 75%` (DD72).
- Release gate con 7 check bloccanti (DD70).
- Monitoring 48h con soglie + ownership + escalation (DD68).

## Allineamenti fatti in questo audit
- Field name: `extends` -> `extendsTemplate` in spec e esempi.
- `schemaVersion` template: **int** (non SemVer string); `ArenaSessionSnapshot.schemaVersion` resta SemVer string.
- `origin.mode` usa enum `CENTER|CORNER_NW|CORNER_SW` come da schema.
- Runbook consolidato: `docs/runbook/arena-alerts.md` come unico riferimento.
- Doc duplicate marcate deprecated e rinviate ai canonicali.
- Aggiornati percorsi file errati nei COMPLETE (Agent 08).

## Gap residui (focus evolutivo)
1. **Prebuild Pool (DD63)**: decisione enablement basata su telemetria (go/no-go) e configurazione finale.
2. **Legacy cleanup**: continuare la rimozione di percorsi legacy individuati dal WrapperAnalyzer.

## Documenti deprecati (vedi header nei file)
- `docs/_deprecated/arena-template-rework/TODO_ARENA_TEMPLATE.md` -> usa `docs/subsystems/arena-template-rework/TODO_ARENA_TEMPLATE.md`
- `docs/_deprecated/arena-template-rework/TODO_AGENT_01_COMPLETE.md` -> usa `docs/subsystems/arena-template-rework/TODO_AGENT_01_COMPLETE.md`
- `docs/_deprecated/arena-template-rework/ARENA_TEMPLATE_ROLLOUT_PLAN.md` -> storico v2.2, sostituito dalla spec v2.23
- `docs/_deprecated/arena-template-rework/arena-alerts.md` -> usa `docs/runbook/arena-alerts.md`
- `docs/_deprecated/arena-template-rework/TODO_AGENT_*.md` -> task list archiviate
- `docs/_deprecated/arena-template-rework/TODO_AGENT_COORDINATOR.md` -> piano agent storico
- `docs/_deprecated/arena-template-rework/run_agents*.sh` -> script storici

## Documenti esterni (allineati)
- `docs/ARCHITECTURE.md` - aggiornato con Arena Template system
- `docs/areas/instance/INSTANCE_DIMENSION_SYSTEM.md` - allineato (TemplateArenaBuilder)
- `docs/areas/instance/INSTANCE_SYSTEM_TEST_STRATEGY.md` - strategia test istanze
- `docs/testing/PROGRESSIVE_TEST_PLAN.md` - flow template-based
- `docs/testing/TEST_HARNESS.md` - entrypoint harness (JUnit + GameTest)
- `docs/testing/TESTING.md` - guida test aggiornata
- `docs/telemetry/TELEMETRY_DOCUMENTATION.md` - sezione Arena Template Telemetry

## Documenti esterni (contesto)
- `docs/areas/instance/INSTANCE_SYSTEM_MANUAL_TEST_CHECKLIST.md` - ARCHIVED (manual checklist)
- `docs/areas/radial/RADIAL_CENSUS.md` - HISTORICAL (UI census legacy)
- `docs/GAME_DESIGN_ANALYSIS.md` - game design endurance (non sostituito)
- `docs/_deprecated/testing-reports/L0_REPORT.md` - report test storico
- `docs/project/BUG_LOG.md` - bug log attuale
