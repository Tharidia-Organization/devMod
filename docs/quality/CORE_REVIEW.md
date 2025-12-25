# Core Review

## Endurance Core Flow
- P2: EnduranceEventHandler has very long lifecycle methods (onWaveComplete/onQuestEnd); readability and testability would improve with helper extraction in a future pass.
- P2: ArenaHazardSystem falls back to player position when no arena context is present; verify this is intended for non-instanced quests to avoid misplaced hazard bounds.
- Fixes applied: Migrated TideManager hook usage to the questId-aware API (batch 15).

## Arena Registry / Builder / Policy
- P2: ArenaBuilder, ArenaTemplateRegistry, and ArenaPolicyRegistry exceed 600 LOC; safe refactors deferred per scope limits.
- P2: ArenaPolicy core method spans >600 LOC; break into validation helpers when scope allows.
- Fixes applied: None in this review.

## Network Handlers + Validators
- P1 (addressed): NetworkHandler and related handlers referenced client-only classes inside Dist.CLIENT checks; now routed through client payload hooks to reduce classloading risk on dedicated servers.
- P2: PacketValidator uses string-concatenated keys for rate limits; ensure packetType cardinality is bounded to avoid unbounded growth between cleanups.
- Fixes applied: Client payload hooks extended across Config/Party/Shield/Endurance handlers and GameMechanicsSyncPayload (batch 7).

## Telemetry Persistence + DuckDB
- P2: DuckDBBatchWriter and DuckDBSchemaManager contain >80-line methods (flushTableUnlocked/migrateSchema); defer refactor to avoid behavior changes in this pass.
- Fixes applied: None in this review.

## Radial Action Executor / Registry
- P2: ActionRegistry builds telemetry payloads manually; ensure any new fields continue to use TelemetryJson.escape to avoid malformed JSON.
- Fixes applied: None in this review.

## Client/Server Boundary Bridge
- P1 (addressed): ClothConfigCompat (common package) reflects client-only Screen class; now guarded by Dist.CLIENT before reflection.
- P1 (addressed): DamageHandler direct client-only RangedWeaponModule usage; now resolved via reflection with server-safe fallback.
- P1 (addressed): DebugNetworkHandler and DashboardCommand client-only handlers now invoked via reflection guards.
- P2: ClientUiBridgeImpl.openEnduranceQuestScreen ignores templateId parameter; verify whether template selection should be forwarded.
- P2: TestHarnessCommands still reference client-only delegates directly; consider reflection/Dist guard to avoid dedicated server classloading.
- Fixes applied: Dist guard for ClothConfigCompat.setParentScreen (batch 6); reflection guards for client-only handlers (batch 13).
