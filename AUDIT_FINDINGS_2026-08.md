# DevMod — audit findings, August 2026

Full-codebase review: SpotBugs (MAX effort) plus nine parallel subsystem
reviewers. Each finding below was reported with >=80% confidence by a reviewer;
the ones marked **FIXED** were additionally verified by hand against the source
before the fix landed. Unverified findings are candidates, not confirmed bugs —
verify before acting.

Status legend: **FIXED** · **TODO** · **REJECTED** (checked, not a real bug)

---

## Build / static analysis

- **FIXED** `build.gradle:509` — publishing repo URI built by string concatenation
  broke when the project path contains a space.
- **FIXED** 130 SpotBugs `NP_NONNULL_PARAM_VIOLATION` — 223 of 224 packages
  declare `@ParametersAreNonnullByDefault`; 46 parameters that legitimately
  receive null were not annotated `@Nullable`.
- **FIXED** 185+ ineffective `@Order` annotations — `@TestMethodOrder` does not
  apply to `@Nested` classes.

## Runtime dimensions

- **FIXED** HIGH — the Nexus level and every arena instance dimension are
  injected into `MinecraftServer.levels`, which vanilla `tickChildren()` already
  iterates, and were then ticked a second time by
  `NexusDimensionManager.tick()` and `InstanceEventHandler`. Every entity, block
  entity and scheduled tick in the hub and in any occupied arena ran at double
  speed, and the Nexus throttle settings had the opposite of their intent.

## Mailbox

- **FIXED** Two worker threads shared one DuckDB `Connection`, producing
  `SQLTimeoutException INTERRUPT` and `ResultSet was closed` in production logs.
- **FIXED** `shutdown()` closed the connection before draining the executor.
- **FIXED** `WebhookManager` passed possibly-null values to `Map.of`.

## Telemetry

- **FIXED** `DuckDBTelemetryService.shutdown()` cleared `enabled` before flushing
  the aggregator registry, which drops events when disabled — every shutdown
  flush was discarded.
- **FIXED** `TelemetryAggregator.clearQuestContext()` discarded the result of
  `forceFlush()`, deleting each player's pending aggregates on every quest end.
- **FIXED** `DuckDBBatchWriter.queueInsert()` flushed inline on the server thread
  from damage/death events, blocking the tick loop on the DB connection lock.
- **FIXED** Dashboard sent `Access-Control-Allow-Origin: *` on a loopback server.
- **FIXED** `/api/query` allowed DuckDB file-reading table functions and chained
  statements behind a "starts with SELECT" check.
- **TODO** HIGH `DuckDBBatchWriter:1211` — batch writer drives transactions on the
  shared connection while dashboard threads run statements on it.
- **TODO** HIGH `TelemetryLogHandlers:110` — with aggregation on (the default),
  player hits never reach `combat_hits`, which every analytics endpoint queries.
- **TODO** HIGH `DuckDBBatchWriter:1222` — on a transient error only the last
  drained chunk is re-queued; earlier chunks are rolled back and lost.
- **TODO** HIGH `HeatmapAggregateWindow:119` — aggregated grid is anchored at world
  origin with a ±128 block extent, so real arena coordinates clamp into one cell.
- **TODO** MED — dashboard thread pool never shut down; query params never
  URL-decoded; timeline queries have no `LIMIT` or query timeout; backpressure
  compares a cross-table total against one table's capacity; sampling is applied
  to already-aggregated rows without inverse weighting.

## Arena

- **TODO** HIGH `AutosmokeRunner:255` — arena build and world cleanup run off the
  server thread from the scheduler, mutating chunk sections concurrently with the
  ticking server.
- **TODO** HIGH `ArenaBuilder:348` — force-load chunk tickets are released only on
  the failure path, so every successful build permanently pins its chunks.
- **TODO** HIGH `BatchBlockPlacer:203` — fast path writes into `LevelChunkSection`
  directly, skipping client sync, block entities and heightmaps.
- **TODO** HIGH `SpawnSlotResolver:224` — unbounded backtracking with per-node
  world lookups on the calling thread.
- **TODO** HIGH `TemplateEventDispatcher:180` — listeners held only by
  `WeakReference`, so registered lambdas are collected at the next GC.
- **TODO** MED — build committed after the 5-minute transaction TTL is rolled back
  as failed; `TemplateLockManager` cleanup removes freshly acquired locks;
  chunk-ticket leak when `requestLoad` throws; async builds ignore template
  limits; check-then-act in `AsyncArenaBuilder` can enqueue a duplicate build;
  `Map.of` NPE when the failing exception has a null message; per-dimension
  builder map is never pruned; autosmoke `stop()` does not stop an in-flight run.

## Endurance

- **TODO** HIGH `CommonModEvents:101` + `ModLifecycleEvents:83` — duplicate
  `@EventBusSubscriber` handlers make every server-start block run twice;
  leaderboards double their entries on each boot.
- **TODO** HIGH `SeasonPassSystem` — two live singletons (`INSTANCE` and
  `getInstance()`); the client sync reads the empty one.
- **TODO** HIGH `EnduranceEventWave:199` — wave advanced twice per completed wave,
  pushing a phantom zero-duration entry into `waveStats`.
- **TODO** HIGH `EnduranceSessionHandler:58` — abandon short-circuits to spectator
  without removing the session, permanently blocking future quest starts.
- **TODO** HIGH `EnduranceEventHandler:236` — logout cleanup misses pending
  sessions, leaking an entry that blocks all future quest starts.
- **TODO** HIGH — daily/weekly challenge completion and reward methods have no
  callers; weekly combo challenges are gated by an always-false condition;
  `timesReachedRank`/`perfectRuns` are saved but never loaded.
- **TODO** HIGH `PartyQuestCoordinator:445` — offline members never get quest-end
  teardown, so perks and multipliers carry into their next quest.
- **TODO** MED — deaths counted twice; leaderboard `subList` view grows without
  bound; "high score" banner fires on every completion; overdrive can be extended
  indefinitely; knockback-resistance modifier never removed; Devil's Bargain curse
  permanently lowers max health; nemesis and contract subsystems are inert.

## Combat / collision / party

- **TODO** HIGH `BodyPartHierarchy:229` — `localOffset` applied twice, so every
  part OBB sits above the entity and OBB hit detection always misses.
- **TODO** HIGH `ExecutionSystem:373` — `ticksRemaining` decremented twice per
  tick; the damage-resistance effect outlives the execution.
- **TODO** HIGH `HitHelper:326` — arm boxes are world-X aligned and tested before
  the body box, so the body part returned depends on compass direction.
- **TODO** HIGH `ExecutionSystem:593` — target AI is never re-enabled when the
  player logs out mid-execution, freezing the mob permanently.
- **TODO** HIGH `ArrowEvents:163` — entity state read from a worker thread 150ms
  after impact, and client VFX invoked off-thread.
- **TODO** MED — only `AbstractArrow` counts as ranged, so melee stats are applied
  to every other projectile; `OBBRaycast` entry-face is always the negative side;
  `slabIntersectFast` never propagates the interval; body-part cache key ignores
  attacker position and view; party leadership transfer on disconnect notifies no
  listeners; `PartyManager.listeners` is an unguarded `ArrayList`.

## Network / transport / portal / notification

- **FIXED** HIGH `RecipeSyncPayload` / `RecipeClientSyncPayload` / `ZoneDebugPayload`
  — decode allocated from an unbounded client-supplied count before any
  permission check; one small packet could exhaust the heap.
- **FIXED** HIGH `TransportNetworkHandler` — destination node was not validated
  against the source node's network: teleport-anywhere exploit.
- **FIXED** HIGH `TransportNetworkHandler` — the destination node was passed as
  the source argument, so the chosen waypoint was never honored. Added
  `TransportExecutor.teleportToNode`.
- **TODO** HIGH `PortalNetworkHandler:104` — client-controlled `BlockPos` reaches
  `getBlockState`, forcing synchronous chunk generation anywhere in the world.
- **TODO** HIGH `NotificationPreferencesRepository:182` — try-with-resources closes
  the shared DuckDB connection the manager explicitly documents as do-not-close.
- **TODO** HIGH `PortalRegistry:42` — position index is not keyed by dimension, so
  portals at identical coordinates in different dimensions collide.
- **TODO** HIGH `TransportExecutor:372` — non-player cross-dimension teleport moves
  the entity within the current level using the destination's coordinates.
- **TODO** MED — arrival cooldown written with a null node id but read with the
  real one; transport config save has no ownership check; `cancelSession` has no
  membership check; S2C payloads allocate from unbounded counts.

## Clone / hologram / NPC

- **FIXED** HIGH `NeurocellBlockEntity` — `getInventory()` returned a fresh
  detached container each call: item duplication. The L and Item variants have
  the same defect and are still **TODO**.
- **FIXED** HIGH `ClonePulverizerBlock` — no `onRemove`/loot table, so the whole
  block-entity inventory was voided on break.
- **FIXED** HIGH `TelepadBlockEntity` — `wasActive` computed after the removal
  list was built, so the `ACTIVE` blockstate could never go false→true.
- **TODO** HIGH `EntityBillboardCache:364` — render target unbind sits outside the
  try/finally, so a throwing entity renderer leaves the offscreen target bound.
- **FIXED** HIGH `HologramProjectorBlockEntity` — client-supplied `scanSize` and
  `blockSize` were applied unclamped; a crafted packet queried entities over a
  multi-million block AABB.
- **FIXED** HIGH `NpcDialogManager` — the selected option's `showCondition` was
  not re-checked before running its action, so gated rewards and console commands
  could be triggered by a modified client.
- **TODO** MED — `removeItemNoUpdate` overrides omit `setChanged()`; pulverizer
  progress is zeroed by every client sync; failed reformer spawn discards the
  bioscan; hologram presets are missing from `getUpdateTag`.

## Rejected after checking

- `ExecutionSystem.completeExecution(player, null)` — every target dereference is
  already null-guarded.
- `HitData` static store — cleaned every server tick; not a leak.
- Payload handlers running on the network thread — NeoForge's `PayloadRegistrar`
  defaults to `HandlerThread.MAIN` and wraps handlers accordingly.
- `UnifiedNotificationPayload.from` — cannot return null on any path.
- Recipe result stacks — all `getResult`/`assemble` paths already copy.
- Portal entity duplication on simultaneous entry — guarded by portal cooldown.


## Client UI (reviewed last; all TODO)

- HIGH `EditorApplyFeedbackRouter:39` — `it.remove()` on a `CopyOnWriteArrayList`
  iterator throws `UnsupportedOperationException` out of screen `onClose()`.
- HIGH `MobPoolEditorScreen:214` — resizing rebuilds the mob list with everything
  enabled and never re-applies the pool config, so Apply re-enables every
  disabled mob server-wide.
- HIGH `RadialMenuScreen:1963` — search result index not bounds-checked.
- HIGH `EnduranceQuestScreen:261` — resize leaks a cache listener and a native
  `VertexBuffer` per rebuild.
- HIGH `ClientImpactHandlers:48` — body-part ordinal from the wire indexes
  `values()` without a bounds check.
- MED — several screens re-run one-time setup in `init()` (which re-runs on every
  resize): `UnifiedSettingsScreen` discards unsaved edits, `VoxelLabScreen` skips
  page `onDeactivate()`, `ItemEditorScreen` wipes undo history,
  `NotificationCenterScreen` resets the tab and re-requests data.
- MED `SettingsManager:124` — a save while another is in flight is dropped, and
  the in-flight task clears `dirty` for data it never saw.
- MED — shield/pathfinding/heatmap renderers set per-entity shader uniforms on a
  batch flushed once, so with two or more subjects all render with the last
  one's values.
