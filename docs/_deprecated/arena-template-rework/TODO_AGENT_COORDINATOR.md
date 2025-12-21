# Arena Template - Parallel Agent Coordinator

> DEPRECATED: coordination plan archived; see `docs/arena-template-rework/README.md` and COMPLETE docs.


## Overview
Questo progetto è suddiviso in 12 agent paralleli. Ogni agent lavora su un subset indipendente del sistema.

## Agent Dependency Graph

```
                    ┌─────────────────────────────────────────┐
                    │                                         │
    ┌───────────────┼─────────────────────────────────────────┼───────────────┐
    │               │                                         │               │
    ▼               ▼                                         ▼               ▼
┌────────┐    ┌────────┐    ┌────────┐    ┌────────┐    ┌────────┐    ┌────────┐
│Agent 01│    │Agent 05│    │Agent 07│    │Agent 08│    │Agent 09│    │Agent 10│
│Registry│    │Observe │    │Security│    │Cleanup │    │ Spawn  │    │Gamific.│
└────┬───┘    └────┬───┘    └────┬───┘    └────┬───┘    └────┬───┘    └────┬───┘
     │             │             │             │             │             │
     ▼             ▼             │             │             │             │
┌────────┐    ┌────────┐        │             │             │             │
│Agent 02│    │Agent 06│        │             │             │             │
│Builder │    │Identity│        │             │             │             │
└────┬───┘    └────┬───┘        │             │             │             │
     │             │             │             │             │             │
     ▼             │             │             │             │             │
┌────────┐        │             │             │             │             │
│Agent 03│        │             │             │             │             │
│ Budget │        │             │             │             │             │
└────┬───┘        │             │             │             │             │
     │             │             │             │             │             │
     ▼             │             │             │             │             │
┌────────┐        │             │             │             │             │
│Agent 04│        │             │             │             │             │
│Metriche│        │             │             │             │             │
└────┬───┘        │             │             │             │             │
     │             │             │             │             │             │
     └─────────────┴─────────────┴─────────────┴─────────────┴─────────────┘
                                        │
                                        ▼
                                  ┌────────┐
                                  │Agent 11│
                                  │Telemetr│
                                  └────┬───┘
                                       │
                                       ▼
                                  ┌────────┐
                                  │Agent 12│
                                  │Readines│ ◄── FINAL INTEGRATION
                                  └────────┘
```

## Parallel Execution Groups

### Group A - Can Start Immediately (No Dependencies)
| Agent | File | Role | DD Range |
|-------|------|------|----------|
| 01 | `TODO_AGENT_01_REGISTRY.md` | Registry & Resolver | DD 1-6 |
| 05 | `TODO_AGENT_05_OBSERVABILITY.md` | Logging, Persistence | DD 16-21 |
| 07 | `TODO_AGENT_07_OPERATIONS.md` | Security, Permissions | DD 29-36 |
| 08 | `TODO_AGENT_08_CLEANUP.md` | Cleanup, Migration | DD 37-43 |
| 09 | `TODO_AGENT_09_SPAWN.md` | Rollback, Spawn | DD 44-50 |
| 10 | `TODO_AGENT_10_GAMIFICATION.md` | Gamification | DD 51-56 |

### Group B - Depends on Group A
| Agent | File | Role | Waits For |
|-------|------|------|-----------|
| 02 | `TODO_AGENT_02_BUILDER.md` | Builder Transazionale | Agent 01 |
| 06 | `TODO_AGENT_06_IDENTITY.md` | Identity, Recovery | Agent 05 |

### Group C - Depends on Group B
| Agent | File | Role | Waits For |
|-------|------|------|-----------|
| 03 | `TODO_AGENT_03_BUDGET.md` | Budget & Async | Agent 02 |

### Group D - Depends on Group C
| Agent | File | Role | Waits For |
|-------|------|------|-----------|
| 04 | `TODO_AGENT_04_METRICHE.md` | Metriche & API | Agent 02, 03 |

### Group E - Integration
| Agent | File | Role | Waits For |
|-------|------|------|-----------|
| 11 | `TODO_AGENT_11_TELEMETRY.md` | Telemetry, Concurrency | Agent 04, 05, 06 |

### Group F - Final Verification
| Agent | File | Role | Waits For |
|-------|------|------|-----------|
| 12 | `TODO_AGENT_12_READINESS.md` | Release Gate, KPIs | ALL (01-11) |

## Execution Plan

### Phase 1 - Parallel Start (6 agents)
```bash
# Terminal 1: Agent 01
claude --file TODO_AGENT_01_REGISTRY.md

# Terminal 2: Agent 05
claude --file TODO_AGENT_05_OBSERVABILITY.md

# Terminal 3: Agent 07
claude --file TODO_AGENT_07_OPERATIONS.md

# Terminal 4: Agent 08
claude --file TODO_AGENT_08_CLEANUP.md

# Terminal 5: Agent 09
claude --file TODO_AGENT_09_SPAWN.md

# Terminal 6: Agent 10
claude --file TODO_AGENT_10_GAMIFICATION.md
```

### Phase 2 - When Phase 1 agents complete
```bash
# Wait for Agent 01 → Start Agent 02
# Wait for Agent 05 → Start Agent 06
```

### Phase 3 - When Phase 2 agents complete
```bash
# Wait for Agent 02 → Start Agent 03
```

### Phase 4 - When Phase 3 agents complete
```bash
# Wait for Agent 02, 03 → Start Agent 04
```

### Phase 5 - When Phase 4 agents complete
```bash
# Wait for Agent 04, 05, 06 → Start Agent 11
```

### Phase 6 - Final Integration
```bash
# Wait for ALL agents 01-11 → Start Agent 12
```

## Completion Tracking

Check for completion files:
```bash
ls -la TODO_AGENT_*_COMPLETE.md
```

Expected files when complete:
- [ ] `TODO_AGENT_01_COMPLETE.md`
- [ ] `TODO_AGENT_02_COMPLETE.md`
- [ ] `TODO_AGENT_03_COMPLETE.md`
- [ ] `TODO_AGENT_04_COMPLETE.md`
- [ ] `TODO_AGENT_05_COMPLETE.md`
- [ ] `TODO_AGENT_06_COMPLETE.md`
- [ ] `TODO_AGENT_07_COMPLETE.md`
- [ ] `TODO_AGENT_08_COMPLETE.md`
- [ ] `TODO_AGENT_09_COMPLETE.md`
- [ ] `TODO_AGENT_10_COMPLETE.md`
- [ ] `TODO_AGENT_11_COMPLETE.md`
- [ ] `TODO_AGENT_12_COMPLETE.md`

## Shared Resources - Conflict Prevention

### File Lock Convention
Each agent owns specific packages:
- Agent 01: `com.devmod.arena.registry`, `com.devmod.arena.policy`
- Agent 02: `com.devmod.arena.builder`
- Agent 03: `com.devmod.arena.budget`
- Agent 04: `com.devmod.arena.metrics`, `com.devmod.arena.api`
- Agent 05: `com.devmod.arena.logging`, `com.devmod.arena.alert`
- Agent 06: `com.devmod.arena.identity`, `com.devmod.arena.retention`
- Agent 07: `com.devmod.arena.security`, `com.devmod.arena.dashboard`
- Agent 08: `com.devmod.arena.cleanup`, `com.devmod.arena.monitor`
- Agent 09: `com.devmod.arena.fallback`, `com.devmod.arena.spawn`
- Agent 10: `com.devmod.arena.gamification`, `com.devmod.arena.leaderboard`
- Agent 11: `com.devmod.arena.telemetry`, `com.devmod.arena.concurrency`
- Agent 12: `com.devmod.arena.pool`, `com.devmod.arena.rollout`

### Cross-Agent Communication
If you need a class from another agent:
1. Check if their COMPLETE.md exists
2. If not, create an interface in your package
3. Leave TODO comment: `// INTEGRATE: needs Agent XX completion`

## DD Coverage Summary

| Agent | Design Decisions | Count |
|-------|-----------------|-------|
| 01 | DD 1-6 | 6 |
| 02 | DD 7-10 | 4 |
| 03 | DD 11-12 | 2 |
| 04 | DD 13-15 | 3 |
| 05 | DD 16-21 | 6 |
| 06 | DD 22-28 | 7 |
| 07 | DD 29-36 | 8 |
| 08 | DD 37-43 | 7 |
| 09 | DD 44-50 | 7 |
| 10 | DD 51-56 | 6 |
| 11 | DD 57-62 | 6 |
| 12 | DD 63-72 | 10 |
| **Total** | | **72** |

## Quick Reference - Original TODO
See `TODO_ARENA_TEMPLATE.md` for complete Design Decisions documentation (v2.23).
