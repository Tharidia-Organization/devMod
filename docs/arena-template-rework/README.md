# Arena Template Rework

Documentazione e task per il rework del sistema Arena Template.

## Struttura

```
docs/arena-template-rework/
├── README.md                      # Questo file
├── TODO_ARENA_TEMPLATE.md         # Design Decisions complete (DD1-DD72)
├── TODO_AGENT_COORDINATOR.md      # Piano di coordinamento agent paralleli
├── TODO_AGENT_01_REGISTRY.md      # Agent 01: Registry & Resolver (DD 1-6)
├── TODO_AGENT_02_BUILDER.md       # Agent 02: Builder Transazionale (DD 7-10)
├── TODO_AGENT_03_BUDGET.md        # Agent 03: Budget & Async (DD 11-12)
├── TODO_AGENT_04_METRICHE.md      # Agent 04: Metriche & API (DD 13-15)
├── TODO_AGENT_05_OBSERVABILITY.md # Agent 05: Observability (DD 16-21)
├── TODO_AGENT_06_IDENTITY.md      # Agent 06: Identity & Recovery (DD 22-28)
├── TODO_AGENT_07_OPERATIONS.md    # Agent 07: Operations & Security (DD 29-36)
├── TODO_AGENT_08_CLEANUP.md       # Agent 08: Cleanup & Migration (DD 37-43)
├── TODO_AGENT_09_SPAWN.md         # Agent 09: Rollback & Spawn (DD 44-50)
├── TODO_AGENT_10_GAMIFICATION.md  # Agent 10: Gamification (DD 51-56)
├── TODO_AGENT_11_TELEMETRY.md     # Agent 11: Telemetry & Concurrency (DD 57-62)
└── TODO_AGENT_12_READINESS.md     # Agent 12: Pool & Readiness (DD 63-72)
```

## Quick Start

### Opzione A: Script Automatico (Consigliato)
```bash
cd docs/arena-template-rework
./run_agents.sh          # Esegue tutte le fasi in sequenza
./run_agents.sh status   # Controlla stato completamento
```

### Opzione B: Tmux con Controllo Manuale
```bash
cd docs/arena-template-rework
./run_agents_tmux.sh           # Avvia Fase 1 (6 agent)
tmux attach -t arena-agents    # Connettiti per monitorare

# Quando Fase 1 completa:
./run_agents_tmux.sh phase2    # Avvia Agent 02, 06
./run_agents_tmux.sh phase3    # Avvia Agent 03
./run_agents_tmux.sh phase4    # Avvia Agent 04
./run_agents_tmux.sh phase5    # Avvia Agent 11
./run_agents_tmux.sh phase6    # Avvia Agent 12
./run_agents_tmux.sh status    # Controlla completamento
```

### Opzione C: Manuale (12 terminali)
Vedi [TODO_AGENT_COORDINATOR.md](TODO_AGENT_COORDINATOR.md) per il grafo delle dipendenze

### Tracking Completamento

Ogni agent crea un file `TODO_AGENT_XX_COMPLETE.md` quando finisce:
```bash
ls docs/arena-template-rework/TODO_AGENT_*_COMPLETE.md
```

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
