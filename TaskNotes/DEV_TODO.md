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
- [x] Wire structure checksum validation using manifest + computed hash
- [x] Add block whitelist validation (floor/walls/ceiling/underfloor)
- [x] Add entity whitelist validation for spawn slots
- [x] Enforce per-template entity and block budgets at runtime
- [x] Add post-build residual entity audit and rollback option
- [x] Add block integrity verification after build (air gaps, wrong material)
- [x] Add MSPT guard during arena build (throttle or abort)

## P1 - Mailbox MMO Quality
- [x] Add per-IP and per-account rate limit layer
- [x] Add spam detection scoring (rules first, ML later)
- [x] Add reputation system gate for high-risk actions
- [x] Add field-level redaction for ticket views
- [x] Add ticket workflow state machine with auto-transitions
- [x] Add attachment content validation and size caps in storage
- [x] Add mailbox abuse audit log export

## P1 - Network Safety
- [x] Add payload size estimation for editor payloads (NBT-heavy)
- [x] Add dedicated limits for telemetry batches per player/session
- [x] Add disconnect policy for repeated invalid payloads
- [x] Add whitelist for client action IDs in ActionRegistry

## P2 - Performance
- [x] Batch block placement in ArenaBuilder (chunk section writes)
- [x] Add async build backpressure (queue depth + cancel)
- [x] Precompute radial menu labels and cache per locale
- [x] Reduce reflection in AmmoFilter (cache method lookup)
- [x] Add pooled allocators for frequent small objects

## P2 - Architecture Cleanup
- [x] Split NetworkHandler into per-domain registrars (PayloadRegistrar pattern)
  - AbilityNetworkHandler, ShieldNetworkHandler, ConfigNetworkHandler migrated
  - EnduranceNetworkHandler, PartyNetworkHandler, MobItemNetworkHandler migrated
- [x] Split WaveManager into lifecycle/spawn/modifier/zone services
  - WaveModifierService extracted (wave/WaveModifierService.java)
  - SpawnContext and SpawnPools made public with zone support
- [x] Split RadialMenuRegistry into builder + data-driven config
  - VisibilitySupplierRegistry: registry for dynamic visibility suppliers (10 built-in)
  - ColorTokenResolver: reflection-based DesignTokens.Radial resolution
  - RadialMenuDefinitionConfig: JSON data records for config loading
  - RadialMenuDefinitionLoader: JSON parsing with validation
  - RadialMenuRuntimeRegistry: central registry with JSON + programmatic categories
  - RadialMenuBuilder: fluent API for mod integration
  - RadialMenuScreen updated to use RuntimeRegistry with fallback
- [x] Convert MailboxConfig into modular records per section
  - MailboxConfigSections.java with 9 section records
- [x] Replace global singletons with injected services where possible
  - ServiceRegistry.java: thread-safe DI container with lazy init + overrides
  - Services.java: type-safe accessors for core services (party, waves, telemetry, etc.)
  - Backward compatible: existing INSTANCE singletons still work, registry enables testing

## P2 - UX/UI
- [x] Radial menu search bar + filter (already implemented: / or F to toggle, type to search)
- [x] Favorites persistence (config + sync) (already implemented: loadFavorites/persistConfig)
- [x] Keyboard navigation and shortcuts (already implemented: 1-6 macro, 7-0 categories, arrows)
- [x] Recent actions ring (already implemented: usageStats tracking + ranking)
- [x] Context hints per action (already implemented: renderHelpText + tooltips)

## P2 - Telemetry
- [x] Add P99 latency and error counters for DuckDB writes
- [x] Add telemetry health endpoint for admin panel
- [x] Add sampling controls per event category

## P3 - Testing / CI / Release
- [x] Add unit tests for payload validation
  - PayloadValidationTest.java: 35 tests covering rate limiting, metrics, cleanup
- [x] Add integration tests for arena build/validate
  - ArenaBuilderIntegrationTest.java: 21 tests covering validation modes, dry run, transactions
- [x] Add fuzz tests for mailbox inputs
  - ContentFilterFuzzTest.java, SpamDetectorFuzzTest.java, AttachmentValidatorFuzzTest.java
  - 107 fuzz tests covering boundary strings, control chars, ReDoS, Unicode
- [x] Add load tests for party/endurance flows
  - PartyManagerLoadTest.java: concurrent party creation, invites, member ops, cleanup
  - EnduranceLoadTest.java: quest lifecycle, wave states, scoring, objectives, tension
- [x] Add CI job for static analysis (SpotBugs/ErrorProne)
  - SpotBugs plugin added to build.gradle
  - config/spotbugs/exclude.xml with Minecraft-specific exclusions
- [x] Add release checklist automation (version bump + tag)
  - scripts/release.sh: automated version bump, changelog, git tag
  - docs/RELEASE_CHECKLIST.md: manual checklist and procedures

## P3 - Docs
- [x] Update architecture map with current module boundaries
  - Added Radial Menu System (data-driven) class diagram
  - Added Network Handlers (domain split) table
  - Added Payload Validation flow diagram
  - Added Service Registry pattern section
- [x] Document network payload limits and policies
  - docs/network/PAYLOAD_LIMITS.md
- [x] Document mailbox moderation workflow
  - docs/mailbox/MODERATION.md
- [x] Add README for arena template schema + examples
  - docs/arena/TEMPLATE_SCHEMA.md
