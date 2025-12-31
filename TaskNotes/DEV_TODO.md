# DEV TODO - DevMod
Deadline: 24:00 local

## P0 - Security and Stability (must finish)
- [x] Apply PayloadValidation to server-bound packets in NetworkHandler
- [x] Add PayloadValidation cleanup hook on server tick
- [x] Add SizedPayload for Mailbox/Ticket/Telemetry/Recipe payloads
- [x] Add SizedPayload for remaining server-bound payloads (ability, quest, party, editor, etc.)
- [x] Add per-IP rate limiting for network and mailbox entry points
- [x] Add server-side payload size rejection metrics (telemetry counters + admin UI)
- [x] Fix WaveManager shared-state race conditions (spawned/killed/indices)
- [x] Fix spawned mob tracking leak when mobs die outside hooks
- [x] Gate hot-path logs (WaveManager, ArenaBuilder) behind debug flags
- [x] Replace DuckDB single-connection with pool or serialized access
- [x] Add safe shutdown for DuckDB writers (flush + drain queues)

## P1 - Arena/Template Hardening
- [ ] Wire structure checksum validation using manifest + computed hash
- [ ] Add block whitelist validation (floor/walls/ceiling/underfloor)
- [ ] Add entity whitelist validation for spawn slots
- [ ] Enforce per-template entity and block budgets at runtime
- [ ] Add post-build residual entity audit and rollback option
- [ ] Add block integrity verification after build (air gaps, wrong material)
- [ ] Add MSPT guard during arena build (throttle or abort)

## P1 - Mailbox MMO Quality
- [ ] Add per-IP and per-account rate limit layer
- [ ] Add spam detection scoring (rules first, ML later)
- [ ] Add reputation system gate for high-risk actions
- [ ] Add field-level redaction for ticket views
- [ ] Add ticket workflow state machine with auto-transitions
- [ ] Add attachment content validation and size caps in storage
- [ ] Add mailbox abuse audit log export

## P1 - Network Safety
- [ ] Add payload size estimation for editor payloads (NBT-heavy)
- [ ] Add dedicated limits for telemetry batches per player/session
- [ ] Add disconnect policy for repeated invalid payloads
- [ ] Add whitelist for client action IDs in ActionRegistry

## P2 - Performance
- [ ] Batch block placement in ArenaBuilder (chunk section writes)
- [ ] Add async build backpressure (queue depth + cancel)
- [ ] Precompute radial menu labels and cache per locale
- [ ] Reduce reflection in AmmoFilter (cache method lookup)
- [ ] Add pooled allocators for frequent small objects

## P2 - Architecture Cleanup
- [ ] Split NetworkHandler into per-domain registrars
- [ ] Split WaveManager into lifecycle/spawn/modifier/zone services
- [ ] Split RadialMenuRegistry into builder + data-driven config
- [ ] Convert MailboxConfig into modular records per section
- [ ] Replace global singletons with injected services where possible

## P2 - UX/UI
- [ ] Radial menu search bar + filter
- [ ] Favorites persistence (config + sync)
- [ ] Keyboard navigation and shortcuts
- [ ] Recent actions ring
- [ ] Context hints per action

## P2 - Telemetry
- [ ] Add P99 latency and error counters for DuckDB writes
- [ ] Add telemetry health endpoint for admin panel
- [ ] Add sampling controls per event category

## P3 - Testing / CI / Release
- [ ] Add unit tests for payload validation
- [ ] Add integration tests for arena build/validate
- [ ] Add fuzz tests for mailbox inputs
- [ ] Add load tests for party/endurance flows
- [ ] Add CI job for static analysis (SpotBugs/ErrorProne)
- [ ] Add release checklist automation (version bump + tag)

## P3 - Docs
- [ ] Update architecture map with current module boundaries
- [ ] Document network payload limits and policies
- [ ] Document mailbox moderation workflow
- [ ] Add README for arena template schema + examples
