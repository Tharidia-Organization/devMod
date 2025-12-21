# Arena Template Documentation Audit (v2.23)

## Scope
- Obiettivo: riallineare la documentazione Arena Template allo stato attuale del codice e all'obiettivo finale v2.23.
- Copertura: spec/template/policy, runbook alert, schemi JSON, migrazione legacy, readiness.
- Data audit: 2025-12-21 (allineamento docs + deprecazioni).

## Source of Truth (canonicali)
1. `docs/arena-template-rework/TODO_ARENA_TEMPLATE.md` - spec completa DD1-DD72 (v2.23)
2. `docs/arena-template-rework/TODO_AGENT_*_COMPLETE.md` - stato implementazione per area
3. `src/main/resources/schemas/arena_template.schema.json` - schema L1 (ArenaTemplate)
4. `src/main/resources/schemas/arena_policy.schema.json` - schema L2 (ArenaPolicy)
5. `docs/runbook/arena-alerts.md` - runbook alert DD68 (48h monitoring)
6. `docs/arena-template-rework/MIGRATION_INVENTORY.md` - inventario legacy + stato migrazione
7. `docs/arena-template-rework/TODO_GAPS.md` - gap residui post-audit
8. `docs/arena-template-rework/README.md` - indice e entrypoint documentale

Nota: le task list storiche `docs/_deprecated/arena-template-rework/TODO_AGENT_*.md` e il coordinatore `docs/_deprecated/arena-template-rework/TODO_AGENT_COORDINATOR.md` sono archivio; lo stato corrente e' nei `*_COMPLETE.md`.

## Stato attuale (snapshot)
- Implementazione core in `src/main/java/com/devmod/arena/*` con pacchetti per registry, builder, budget, metrics, telemetry, observability, recovery, security, cleanup, spawn, gamification, pool, monitoring.
- Schema template/policy presenti e allineati alle decisioni v2.23.
- Runbook alert centralizzato in `docs/runbook/arena-alerts.md`.
- Legacy `ArenaManager.createArena()` e percorsi legacy deprecati; nessuna chiamata diretta rilevata in `src/main/java`.
- Prebuild pool implementata ma DEFERRED (feature flag + valutazione post telemetria).

## Obiettivo finale (v2.23)
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
1. **Prebuild Pool (DD63)**: decisione enablement dopo 2 settimane di telemetria; definire go/no-go e configurazione finale.
2. **Legacy cleanup**: adapter legacy rimosso dai flussi endurance; resta solo il wrapper deprecato per compatibilita' storica.

## Documenti deprecati (vedi header nei file)
- `docs/_deprecated/arena-template-rework/TODO_ARENA_TEMPLATE.md` -> usa `docs/arena-template-rework/TODO_ARENA_TEMPLATE.md`
- `docs/_deprecated/arena-template-rework/TODO_AGENT_01_COMPLETE.md` -> usa `docs/arena-template-rework/TODO_AGENT_01_COMPLETE.md`
- `docs/_deprecated/arena-template-rework/ARENA_TEMPLATE_ROLLOUT_PLAN.md` -> storico v2.2, sostituito dalla spec v2.23
- `docs/arena-template-rework/arena-alerts.md` -> usa `docs/runbook/arena-alerts.md`
- `docs/_deprecated/arena-template-rework/TODO_AGENT_*.md` -> task list archiviate
- `docs/_deprecated/arena-template-rework/TODO_AGENT_COORDINATOR.md` -> piano agent storico
- `docs/_deprecated/arena-template-rework/run_agents*.sh` -> script storici

## Documenti esterni (allineati)
- `docs/ARCHITECTURE.md` - aggiornato con Arena Template system
- `docs/INSTANCE_DIMENSION_SYSTEM.md` - allineato (TemplateArenaBuilder)
- `docs/INSTANCE_SYSTEM_MANUAL_TEST_CHECKLIST.md` - note template + check aggiornato
- `docs/INSTANCE_SYSTEM_TEST_STRATEGY.md` - pointer ai test template
- `docs/PROGRESSIVE_TEST_PLAN.md` - flow aggiornato a template-based
- `docs/testing/TEST_HARNESS.md` - aggiunta suite test arena template
- `docs/telemetry/TELEMETRY_DOCUMENTATION.md` - sezione Arena Template Telemetry

## Documenti esterni (contesto, invariati)
- `docs/ui/radial/RADIAL_CENSUS.md` - comando `/arena` e UI census
- `docs/GAME_DESIGN_ANALYSIS.md` - game design endurance (non sostituito)
- `docs/testing/L0_REPORT.md` - report test
- `docs/BUG_LOG.md` - bug log attuale
