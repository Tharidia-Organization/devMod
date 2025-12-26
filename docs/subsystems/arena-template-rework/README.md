# Arena Template Rework

> **Last Updated**: 2024-12-23
> **Status**: ✅ CURRENT - Sistema Arena Template v2.23 documentato
> **Design Decisions**: 72 DD completate (DD1-DD72)
> **Main Reference**: [[areas/arena/README]] ← Documento principale consolidato

Documentazione dettagliata e track record per il rework del sistema Arena Template.

**Nota**: Per una panoramica consolidata del sistema Arena, vedere [[areas/arena/README]]. Questa cartella contiene le specifiche dettagliate e i record di implementazione.

## Struttura

```
docs/subsystems/arena-template-rework/
├── README.md                      # Questo file
├── ARENA_TEMPLATE_AUDIT.md        # Audit + mappa doc + gap residui
├── TODO_ARENA_TEMPLATE.md         # Design Decisions complete (DD1-DD72)
├── DOCUMENTATION_AUDIT_REPORT.md  # Report audit documentazione
├── PRODUCTION_MARKER_README.md    # Guard autosmoke (DD32)
├── arena_template.schema.json     # Schema L1 (ArenaTemplate)
├── arena_policy.schema.json       # Schema L2 (ArenaPolicy)
├── TODO_AGENT_01_COMPLETE.md      # Agent 01: Registry & Resolver
├── TODO_AGENT_02_COMPLETE.md      # Agent 02: Builder Transazionale
├── TODO_AGENT_03_COMPLETE.md      # Agent 03: Budget & Async
├── TODO_AGENT_04_COMPLETE.md      # Agent 04: Metriche & API
├── TODO_AGENT_05_COMPLETE.md      # Agent 05: Observability & Persistence
├── TODO_AGENT_06_COMPLETE.md      # Agent 06: Identity & Recovery
├── TODO_AGENT_07_COMPLETE.md      # Agent 07: Operations & Security
├── TODO_AGENT_08_COMPLETE.md      # Agent 08: Cleanup & Migration
├── TODO_AGENT_09_COMPLETE.md      # Agent 09: Rollback & Spawn
├── TODO_AGENT_10_COMPLETE.md      # Agent 10: Gamification & Balance
├── TODO_AGENT_11_COMPLETE.md      # Agent 11: Telemetry & Concurrency
└── TODO_AGENT_12_COMPLETE.md      # Agent 12: Pool & Readiness
```

Archivio storico (task list e script): `docs/_deprecated/arena-template-rework/`.

## Flusso consigliato (entrypoint)

1. `docs/subsystems/arena-template-rework/ARENA_TEMPLATE_AUDIT.md` - stato corrente, gap residui, doc canonicali
2. `docs/subsystems/arena-template-rework/TODO_ARENA_TEMPLATE.md` - spec completa v2.23
3. `docs/subsystems/arena-template-rework/TODO_AGENT_*_COMPLETE.md` - implementazione per area
4. `docs/runbook/arena-alerts.md` - runbook alert (DD68)

**Note**: I file `TODO_GAPS.md` e `MIGRATION_INVENTORY.md` sono stati spostati in `_deprecated/` poiché obsoleti.

### Tracking Completamento

Ogni agent crea un file `TODO_AGENT_XX_COMPLETE.md` quando finisce:
```bash
ls docs/subsystems/arena-template-rework/TODO_AGENT_*_COMPLETE.md
```

## Archivio (deprecated)

Materiale storico non piu' operativo:
- `docs/_deprecated/arena-template-rework/TODO_AGENT_*.md` - task list originali
- `docs/_deprecated/arena-template-rework/TODO_AGENT_COORDINATOR.md` - grafo dipendenze storico
- `docs/_deprecated/arena-template-rework/run_agents*.sh` - script di esecuzione parallela
- `docs/_deprecated/arena-template-rework/arena-alerts.md` - sostituito dal runbook
- `docs/_deprecated/arena-template-rework/TODO_ARENA_TEMPLATE.md` - spec legacy (root)
- `docs/_deprecated/arena-template-rework/ARENA_TEMPLATE_ROLLOUT_PLAN.md` - piano storico v2.2

## Design Decisions Summary

| Range | Categoria | Agent |
|-------|-----------|-------|
| DD 1-6 | Registry & Resolver | 01 |
| DD 7-10 | Builder Transazionale | 02 |
| DD 11-12 | Budget & Async | 03 |
| DD 13-15 | Metriche & API | 04 |
| DD 16-21 | Observability & Persistence | 05 |
| DD 22-28 | Identity & Recovery | 06 |
| DD 29-36 | Operations & Security | 07 |
| DD 37-43 | Cleanup & Migration | 08 |
| DD 44-50 | Rollback & Spawn | 09 |
| DD 51-56 | Gamification & Balance | 10 |
| DD 57-62 | Telemetry & Concurrency | 11 |
| DD 63-72 | Pool & Operational Readiness | 12 |

**Totale: 72 Design Decisions**

## Versione

- **TODO_ARENA_TEMPLATE.md**: v2.23
- **Agent Files**: Sincronizzati con v2.23
