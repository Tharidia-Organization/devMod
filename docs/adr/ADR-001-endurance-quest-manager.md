# ADR-001: EnduranceQuestManager Architecture

> Last updated: 2025-12-26
> Status: CURRENT (verified against code)

Decision status: Accepted  
Decision date: 2025-12-26  
Context: Quality pass for large classes

---

## Summary

`EnduranceQuestManager` is the central coordinator for endurance quest lifecycle, arena preparation, and session state. The class stays large by design but delegates domain logic to specialized subsystems.

## Context

Endurance quests touch multiple subsystems at once (arena templates, instances, wave progression, rewards, and persistence). A single coordinator keeps ordering and cleanup consistent across those systems.

## Decision

Keep a single orchestrator with strong delegation instead of splitting into multiple services. This preserves transactional ordering (start/abort/cleanup) while keeping the heavy lifting in focused classes.

## Implementation Notes (Verified)

- Initializes arena registry + policy resolution (`ArenaTemplateRegistry`, `ArenaPolicyRegistry`, `PolicyResolver`).
- Tracks active sessions and templates using `ConcurrentHashMap`.
- Delegates to:
  - `EnduranceSessionHandler` (session lifecycle)
  - `EndurancePlayerStateManager` (player state tracking)
  - `EnduranceQuestPersistence` (stats IO)
  - `WaveManager` (wave progression)
  - `PerkSystem` / `RewardSystem` (progression + rewards)
  - `EnduranceAnalytics` (analytics hooks)
- Integrates with instance flow via `InstanceArenaManager`.
- Uses `PrebuildPoolManager` and `ForceTemplateCapability` for arena prep and overrides.
- Uses `CompletableFuture` for async arena preparation paths.

## Consequences

- Pros: single source of truth for quest state, deterministic ordering, easier cleanup.
- Cons: large class size and broad dependencies.

## Notes

Future refactors should preserve the coordinator role even if specific preparation or query helpers are extracted.
