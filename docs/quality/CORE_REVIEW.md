# Core Review

## Endurance Core Flow
- P2: EnduranceEventHandler has very long lifecycle methods (onWaveComplete/onQuestEnd); readability and testability would improve with helper extraction in a future pass.
- P2: ArenaHazardSystem falls back to player position when no arena context is present; verify this is intended for non-instanced quests to avoid misplaced hazard bounds.
- Fixes applied: None in this review (see batch commits).

## Arena Registry / Builder / Policy
- P2: ArenaBuilder, ArenaTemplateRegistry, and ArenaPolicyRegistry exceed 600 LOC; safe refactors deferred per scope limits.
- Fixes applied: None in this review.

## Network Handlers + Validators
- P1: NetworkHandler references client-only classes inside Dist.CLIENT checks; consider isolating client handlers into a client-only registrar to reduce classloading risk on dedicated server.
- P2: PacketValidator uses string-concatenated keys for rate limits; ensure packetType cardinality is bounded to avoid unbounded growth between cleanups.
- Fixes applied: Import hygiene in NetworkHandler (batch 1).

## Telemetry Persistence + DuckDB
- P2: DuckDBBatchWriter and DuckDBSchemaManager contain >80-line methods (flushTableUnlocked/migrateSchema); defer refactor to avoid behavior changes in this pass.
- Fixes applied: None in this review.

## Radial Action Executor / Registry
- P2: ActionRegistry builds telemetry payloads manually; ensure any new fields continue to use TelemetryJson.escape to avoid malformed JSON.
- Fixes applied: None in this review.

## Client/Server Boundary Bridge
- P1: ClothConfigCompat (common package) reflects client-only Screen class; recommend moving to client package or guarding with DistExecutor to avoid accidental class loading.
- P2: ClientUiBridgeImpl.openEnduranceQuestScreen ignores templateId parameter; verify whether template selection should be forwarded.
- Fixes applied: None in this review.
