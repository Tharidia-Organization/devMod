# TODO - Arena Template Rollout (Instance-First) v2.23

Allineato con [ARENA_TEMPLATE_ROLLOUT_PLAN.md](ARENA_TEMPLATE_ROLLOUT_PLAN.md) v2.2

## Changelog

**v2.23**: Aggiunte Design Decisions DD63-DD72: Prebuild Pool Deferral (DD63 - DEFERRED, evaluate dopo 2 settimane telemetria), Pool Cleanup Unused (DD64 - state machine READY→RESERVED→IN_USE, double-check eviction), Pool Metrics Operational (DD65 - hit/miss linked a feature flag, auto-disable >50%), Migration Wrapper Detection (DD66 - grep+AST+runtime telemetry), Deprecation CI+Runtime (DD67 - warning M1, -Werror M2, removal M3), Monitoring 48h Runbook (DD68 - soglie + ownership + escalation), Dashboard Validation (DD69 - automated job + manual checklist), Security Release Gate (DD70 - CI bloccante, 7 checks), Template Obsolescence (DD71 - versioned extends, session-safe deprecation), Success Criteria KPIs (DD72 - build_p95<5s, rollback<1%, completion>75%). Aggiunta categoria task Pool & Operational Readiness.
**v2.22**: Aggiunte Design Decisions DD57-DD62: Telemetry Propagation Audit (DD57 - TelemetryAuditJob daily, 12 sub-services, CI check eventi orfani), Room ID Uniqueness (DD58 - arenaId immutabile, sessionId per reconnect), Balance Report Job (DD59 - settimanale Dom 06:00, <30s, JSON+Slack), Lock Map Cleanup (DD60 - scheduled cleanup 5min, no leak), Rate Limit 4th Build (DD61 - queue max 10, timeout 60s, reject con retry-after), Telemetry Contention (DD62 - waitTimeMs + templateId per bottleneck analysis). Aggiunta categoria task Telemetry & Concurrency.
**v2.21**: Aggiunte Design Decisions DD51-DD56: Perk Suggestions Bias (DD51 - shuffle SUGGESTED, A/B test 10%, weekly winrate analysis), Badge Template Tracking (DD52 - usage table source of truth, version-agnostic count), Reward Multipliers (DD53 - weight*0.05+1.0, bounds 0.5-2.0, anti-exploit), Currency Source Enum (DD54 - enum ~15 valori, sourceId separato), Challenge Generation (DD55 - 5 availability checks, fallback generica), Leaderboard Batch (DD56 - calcolo 03:00 daily, Redis cache, O(1) read). Aggiunta categoria task Gamification & Balance.
**v2.20**: Aggiunte Design Decisions DD44-DD50: Rollback Staging Test (DD44 - scenario obbligatorio pre-deploy, checklist 4 punti), Fallback Chain Limits (DD45 - max 1 retry, circuit breaker 3/5min), Default Fail Message (DD46 - user-friendly, no tech details, stack trace solo log), SpawnSlots Distance (DD47 - melee 3-15, ranged 12-30, LOS+ground+forbidden), SpawnSlotValidator Performance (DD48 - O(n²) at load, O(1) runtime), Heatmap Privacy (DD49 - 5x5 cell, hourly bucket, no player ID), Mutator Binding (DD50 - SUGGESTED soft, EXCLUDED/REQUIRED hard). Aggiunta categoria task Rollback & Spawn.
**v2.19**: Aggiunte Design Decisions DD37-DD43: Cleanup Robusto (DD37 - 4 fasi: entità→blockEntities→scheduledTicks→blocchi, CleanupResult verificabile), Monitor MSPT (DD38 - baseline pre-build, sliding window, confidence score), Progress Overlay (DD39 - rate limit 4Hz, delta min 1%), Edge Cases Test (DD40 - failure mid-build, chunk timeout, malformed template, concurrency 2 party), Coverage Policy (DD41 - 80% core, 60% MC-dependent, 50% network/UI), Migration Inventory (DD42 - 12 call-site, 6 PR plan), Zero Legacy Gate (DD43 - CI grep + runtime deprecation warning). Aggiunta categoria task Cleanup & Migration.
**v2.18**: Aggiunte Design Decisions DD29-DD36: forceTemplateId Persistence (DD29 - session state + capability per relog), HUD Visibility (DD30 - permission + toggle esplicito), Command Permissions (DD31 - modello granulare + audit log), Autosmoke Production Guard (DD32 - triple guard ENV+flag+file), Autosmoke Assert Exceptions (DD33 - soglie per size + whitelist), Report Export Context (DD34 - header con git commit, config hash), Dashboard Auth (DD35 - token + cache + background refresh), Analytics Query Limits (DD36 - 30 giorni max, pagination, timeout 10s). Aggiunta categoria task Operations & Security.
**v2.17**: Aggiunte Design Decisions DD22-DD28: UUID Generation (DD22 - randomUUID + idempotency cache 5 min), Retention Job (DD23 - 04:00 daily, log separato), Source of Truth (DD24 - NDJSON append-only, DuckDB ricostruibile), Snapshot Versioning (DD25 - schemaVersion + migration chain), Instance Naming (DD26 - max 32 chars, [a-z0-9_], sanitization), Recovery Template Missing (DD27 - fallback default, no rebuild), Tag Dictionary (DD28 - enum predefiniti + autocomplete + typo detection). Aggiunta categoria task Identity & Recovery.
**v2.16**: Aggiunte Design Decisions DD16-DD21: Hot-Reload Session (DD16 - snapshot immutabile, version drift detection), Log Rotation (DD17 - 14 giorni, 500MB cap, .gz compression), Stacktrace JSON (DD18 - array max 20 frames), Alert Routing (DD19 - tutti i canali, retry per critici), NDJSON Non-blocking (DD20 - buffer 10k, flush 100 righe/1s), DuckDB Indici (DD21 - 5 indici, query <200ms). Aggiunta categoria task Observability & Persistence.
**v2.15**: Aggiunte Design Decisions DD11-DD15: Budget Soft/Hard (DD11 - WARN 80%, ERROR 100%), Async Build (DD12 - 500 blocks/tick, backpressure MSPT>40ms), Metriche Obbligatorie (DD13 - ArenaMetricsContext in tutti gli eventi), API Compatibility (DD14 - legacy deprecato + prepareArenaForPartyV2 + ResolveOptions), ArenaHandle Audit (DD15 - 7 call-site da migrare). Aggiornati task con categorie Budget/Async e Metriche/API.
**v2.14**: Estesa sezione Design Decisions con: Transazionalità Reale (DD7 - tracking blocchi/entità/chunks, rollback reverse), Memory Safety (DD8 - hard cap 150k, CompactBlockTracker, NBT streaming), Chunk Loading (DD9 - polling FULL status, failure sequence con chiusura istanza), Dry-Run Estimation (DD10 - euristica + storico DuckDB P75, accuratezza ±20%/±50%). Aggiornati task con categorizzazione Registry/Builder/Testing.
**v2.13**: Aggiunta sezione Design Decisions con risoluzioni per: Version Handling (last-wins), Inheritance Resolution (on load con caching), Tie-Break (deterministico score→version→id), Weight Taratura (piano telemetria), Override Scope (session-based con cleanup), Concurrency (lock per player con timeout 5s).
**v2.12**: Aggiunta Autosmoke Scheduler Implementation (ScheduledExecutorService, timezone handling, server restart behavior), Alert Channels Implementation (console/log/dashboard/telemetry/webhook handlers, AlertRouter), Feature Flag Chain (dipendenze hard-coded, FeatureFlagRegistry single source of truth, log format standard), Instance-Only Gate (mappatura percorsi legacy, debug allowlist, GateResult enum), Registry Fallback Strategy (quando fallback a default_flat_64, all() vs allWithStatus(), RegistryStats).
**v2.11**: Aggiunta definizione precisa residui (bounds AABB, entity filter, negative residual), Metrics Destination & Correlation (ArenaMetricsContext, query examples), ArenaTemplateConfig Integration (ModConfig interface, env override, validation, priority chain), Hot-Reload Safety (ConfigSnapshot immutabile, race prevention, reload rejection), Threshold Calibration (ambiente target, ServerProfile, ThresholdCalibrator, benchmark).
**v2.10**: Aggiunta Inheritance Test Coverage Matrix (13 test cases: catena depth, override liste, campi opzionali, parent mancante, circular), Baseline Metrics Consistency Strategy (punti misurazione legacy vs template, MetricsCompatibilityLayer, telemetria backward-compatible).
**v2.9**: Aggiunta Loader Strategy completa (error isolation non-catastrofico, source priority config>datapack>mod, JSON validation modes STRICT/PERMISSIVE/LENIENT, hot-reload atomic swap, memory leak prevention).
**v2.8**: Aggiunta Hazards Validation Strategy (whitelist tipi, limiti parametri, bounds check), Spawn Slots Validation Strategy (bounds, duplicates, forbidden zones, runtime check), Golden Reference Template (expected output deterministico per test).
**v2.7**: Aggiunta Instance Settings Compatibility Strategy (clamp to server limits, arena coverage check, runtime limit changes, telemetria).
**v2.6**: Aggiunta Error Handling Strategy per inheritance (severity table, cycle detection algorithm, InheritanceValidation sealed interface, user-friendly messages).
**v2.5**: Aggiunta Merge Strategy dettagliata per tutti i campi (OVERRIDE vs SHALLOW_MERGE vs SKIP, esempi, FieldMerger, FIELD_STRATEGIES mapping).
**v2.4**: Aggiunta Inheritance Strategy completa (catena lineare, no diamond, max depth 3, merge rules, eccezioni dedicate, unit test).
**v2.3**: Aggiunta Versioning Strategy completa (version int, schemaVersion SemVer, breakingChange, minTemplateVersion/maxTemplateVersion binding, VersionCompatibilityChecker).
**v2.2**: Separazione concettuale Template (L1 Layout) + Policy (L2 Gameplay). Integrazione completa con tutte le capacità esistenti del progetto.

---

## Design Decisions (Definitive)

Questa sezione documenta le decisioni di design finalizzate durante la review del sistema.

### 1. Version Handling - Last Wins (No Coexistence)

**Decisione**: Template con stesso ID ma versione diversa NON coesistono. L'ultimo caricato sostituisce il precedente.

**Razionale**: Semplicità operativa, nessuna ambiguità nel routing, facile rollback (ricaricare vecchio file).

```java
public void load(ArenaTemplate template) {
    ArenaTemplate existing = registry.get(template.id());
    if (existing != null && existing.version() != template.version()) {
        LOGGER.warn("Template '{}' version {} replaced by version {}",
            template.id(), existing.version(), template.version());
        telemetry.emit("arena.template.version_replaced", Map.of(
            "templateId", template.id(),
            "oldVersion", existing.version(),
            "newVersion", template.version()
        ));
    }
    registry.put(template.id(), template);
}
```

**Ownership**: Tech Lead
**Status**: ✅ DEFINITIVE

---

### 2. Inheritance Resolution - On Load with Caching

**Decisione**: `resolveInheritance()` viene chiamato **una sola volta al load**, NON al get. Il risultato è cached nel registry.

**Razionale**: O(1) per `get()`, nessun rischio di inconsistenza durante il runtime, inheritance già risolta.

```java
public void load(ArenaTemplate template) {
    // 1. Risolvi inheritance (chain ricorsiva)
    ArenaTemplate resolved = resolveInheritance(template);

    // 2. Valida il template risolto
    ValidationResult validation = validator.validate(resolved);
    if (!validation.valid()) {
        throw new TemplateLoadException(validation.errors());
    }

    // 3. Cache nel registry (già risolto, immutabile)
    registry.put(resolved.id(), resolved);
}

public Optional<ArenaTemplate> get(String id) {
    // O(1) - già risolto, nessuna logica di inheritance
    return Optional.ofNullable(registry.get(id));
}
```

**Caching Safety**:
- Registry usa `ImmutableMap` → thread-safe
- Template sono record immutabili
- Hot-reload fa swap atomico dell'intera mappa

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 3. Tie-Break Rule - Deterministic Ordering

**Decisione**: In caso di score uguale nel PolicyResolver, l'ordinamento è deterministico:
1. **Score** (descending) - punteggio weighted
2. **Version** (descending) - policy più recente vince
3. **ID** (alphabetic ascending) - fallback stabile

**Razionale**: Stessa configurazione → stesso risultato, sempre. Nessuna randomicità, debug e test semplificati.

```java
.sorted(Comparator
    .comparingInt((ScoredPolicy sp) -> sp.score()).reversed()      // Score desc
    .thenComparingInt(sp -> sp.policy().version()).reversed()      // Version desc
    .thenComparing(sp -> sp.policy().id()))                        // ID alpha asc
```

**Esempio**:
| Policy | Score | Version | Posizione Finale |
|--------|-------|---------|-----------------|
| boss_a | 10 | 2 | 1° (vince) |
| boss_b | 10 | 2 | 2° (a < b) |
| boss_c | 10 | 1 | 3° (version < 2) |
| casual | 5 | 3 | 4° (score < 10) |

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 4. Weight Taratura - Telemetry-Driven

**Decisione**: I pesi iniziali sono ragionati ma arbitrari. Taratura basata su telemetria dopo 2 settimane di dati reali.

**Pesi iniziali** (da ARENA_TEMPLATE_ROLLOUT_PLAN):
| Criterio | Peso | Razionale |
|----------|------|-----------|
| MOB_MATCH | +5 | Match esatto mob è il segnale più forte |
| QUEST_TYPE | +4 | Boss vs Normal cambia radicalmente l'esperienza |
| DIFFICULTY | +3 | Hard/ranked richiedono arena specifiche |
| PLAYER_COUNT | +2 | Scaling per party |
| TAGS | +1 | Hint generico, bassa priorità |

**Piano di Taratura**:
```java
// Telemetria per ogni risoluzione
telemetry.emit("arena.policy.resolved", Map.of(
    "policyId", result.policy().id(),
    "templateId", result.template().id(),
    "score", finalScore,
    "scoringDetails", Map.of(
        "mobScore", mobMatchScore,
        "questTypeScore", questTypeScore,
        "difficultyScore", difficultyScore,
        "playerCountScore", playerCountScore,
        "tagsScore", tagsScore
    ),
    "alternativeCount", alternatives.size(),
    "topAlternative", alternatives.isEmpty() ? null : alternatives.get(0).id(),
    "scoreDelta", alternatives.isEmpty() ? 0 : finalScore - alternatives.get(0).score()
));
```

**Criteri per aggiustamento pesi** (post 2 settimane):
1. Se `scoreDelta < 2` in >30% dei casi → aumentare differenziazione
2. Se `mobScore` determina >80% delle scelte → ridurre peso o aumentare altri
3. Se `alternativeCount == 0` frequente → routing troppo specifico
4. Se fallback rate >10% → pesi troppo selettivi

**Ownership**: Game Designer + Core Dev
**Status**: ✅ DEFINITIVE (pesi iniziali), 🔄 REVIEW dopo 2 settimane

---

### 5. Override Scope - Session-Based

**Decisione**: Il `forceTemplateId` / `forcePolicyId` è **per sessione**, legato al player/party, con cleanup automatico su logout/end quest.

**Razionale**: Override temporaneo per testing, non persiste, nessun rischio di "override dimenticato".

```java
public record TemplateOverride(
    String templateId,
    @Nullable String policyId,
    OverrideScope scope,         // PLAYER, PARTY, QUEST
    Instant createdAt,
    @Nullable Instant expiresAt, // opzionale TTL
    String source                // "command", "wizard", "api"
) {}

public enum OverrideScope {
    PLAYER,   // solo questo player
    PARTY,    // tutto il party
    QUEST     // questa quest specifica
}
```

**Lifecycle**:
```
Override set → Player/Party in arena → Quest end/abandon/fail → Override cleared
                                     ↓
                              Player logout → Override cleared
                                     ↓
                              Server restart → Override cleared (non persistito)
```

**Storage**: `Map<UUID, TemplateOverride>` in memoria, NON persistito.

**Cleanup**:
```java
// EnduranceQuestManager o equivalente
public void onQuestEnd(UUID playerId, QuestOutcome outcome) {
    overrideManager.clearOverride(playerId);
    telemetry.emit("arena.override.cleared", Map.of(
        "playerId", playerId,
        "reason", "quest_end",
        "outcome", outcome
    ));
}

public void onPlayerLogout(UUID playerId) {
    overrideManager.clearOverride(playerId);
    // Party cleanup se era leader
}
```

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 6. Concurrency - Lock per Player with Timeout

**Decisione**: Un lock per player/party durante la risoluzione del template, con timeout di **5 secondi** e fallback a default.

**Razionale**: Evita race condition se stesso player/party richiede template multipli, ma non blocca indefinitamente.

```java
public class PolicyResolver {
    private final ConcurrentHashMap<UUID, ReentrantLock> playerLocks = new ConcurrentHashMap<>();
    private static final long LOCK_TIMEOUT_MS = 5000;

    public ResolvedArena resolve(UUID playerId, ...) {
        ReentrantLock lock = playerLocks.computeIfAbsent(playerId, k -> new ReentrantLock());

        boolean acquired;
        try {
            acquired = lock.tryLock(LOCK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            telemetry.emit("arena.resolve.interrupted", Map.of("playerId", playerId));
            return getDefaultArena();
        }

        if (!acquired) {
            LOGGER.warn("Lock timeout for player {}, falling back to default", playerId);
            telemetry.emit("arena.resolve.lock_timeout", Map.of(
                "playerId", playerId,
                "timeoutMs", LOCK_TIMEOUT_MS
            ));
            return getDefaultArena();
        }

        try {
            return doResolve(playerId, ...);
        } finally {
            lock.unlock();
        }
    }
}
```

**Lock Cleanup**: Lock rimossi da `playerLocks` dopo 60s di inattività (scheduled cleanup task) per evitare memory leak.

```java
// Scheduled ogni 5 minuti
private void cleanupStaleLocks() {
    long now = System.currentTimeMillis();
    playerLocks.entrySet().removeIf(entry -> {
        ReentrantLock lock = entry.getValue();
        // Rimuovi se non locked e non usato da 60s
        // (richiede tracking lastUsed - implementazione semplificata)
        return !lock.isLocked() && !lock.hasQueuedThreads();
    });
}
```

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 7. TemplateArenaBuilder - Transazionalità Reale

**Decisione**: Il builder è **realmente transazionale**. Ogni blocco, entità e chunk forzato viene tracciato per rollback completo.

**Cosa viene tracciato**:
- Floor/Walls/Ceiling → ogni `setBlock` registra `(pos, previousState)`
- Hazards → blocchi lava/magma/void tracciati identicamente
- Structure NBT → `StructureTemplate.placeInWorld()` wrappato per intercettare ogni blocco
- Light sources → tracciati come blocchi normali
- Entità spawnate → UUID per rimozione
- Chunk forzati → per rilascio ticket

```java
public class BuildTransaction {
    private final List<BlockChange> blockChanges = new ArrayList<>();
    private final List<EntitySpawn> entitySpawns = new ArrayList<>();
    private final Set<ChunkPos> forcedChunks = new HashSet<>();

    public record BlockChange(BlockPos pos, BlockState previousState) {}
    public record EntitySpawn(UUID entityId) {}

    public void rollback(ServerLevel level) {
        // 1. Rimuovi entità spawnate
        for (EntitySpawn spawn : entitySpawns) {
            Entity e = level.getEntity(spawn.entityId());
            if (e != null) e.discard();
        }
        // 2. Ripristina blocchi in ordine inverso
        for (int i = blockChanges.size() - 1; i >= 0; i--) {
            BlockChange change = blockChanges.get(i);
            level.setBlock(change.pos(), change.previousState(), Block.UPDATE_ALL);
        }
        // 3. Rilascia chunk forzati
        releaseChunks(level);
    }
}
```

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 8. Memory Safety - Build Grandi (100k blocchi)

**Decisione**: Hard cap a 150k blocchi con strutture dati compatte.

**Problema**: 100k `BlockChange` records = ~2.4MB (24 bytes/record)

**Limiti per categoria**:
| Categoria | maxBlocks | Memory Budget |
|-----------|-----------|---------------|
| Default | 50,000 | ~1.2MB |
| Boss | 100,000 | ~2.4MB |
| Hard cap | 150,000 | ~3.6MB (abort oltre) |

**Struttura dati efficiente**:
```java
// Long-packed positions (più efficiente di List<BlockChange>)
public class CompactBlockTracker {
    private final LongArrayList positions = new LongArrayList();  // BlockPos.asLong()
    private final ObjectArrayList<BlockState> states = new ObjectArrayList<>();

    public void track(BlockPos pos, BlockState previous) {
        if (positions.size() >= MAX_TRACKED_BLOCKS) {
            throw new BuildLimitExceededException("Block limit exceeded: " + MAX_TRACKED_BLOCKS);
        }
        positions.add(pos.asLong());
        states.add(previous);
    }
}
```

**NBT Streaming** (non carica tutta la struttura in memoria):
```java
structureTemplate.placeInWorld(level, pos, placementSettings,
    (blockPos, state) -> {
        transaction.trackBlock(blockPos, level.getBlockState(blockPos));
        return true;  // procedi
    });
```

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 9. Chunk Loading - Garanzia FULL e Failure Handling

**Decisione**: `ensureChunksLoaded()` garantisce `ChunkStatus.FULL` con polling e timeout. Su failure: rollback completo + cleanup chunks + chiusura istanza.

**Garanzia FULL**:
```java
public ChunkLoadResult ensureChunksLoaded(ServerLevel level, AABB bounds, int timeoutMs) {
    Set<ChunkPos> required = computeRequiredChunks(bounds);
    long deadline = System.currentTimeMillis() + timeoutMs;

    // 1. Forza caricamento con ticket
    for (ChunkPos pos : required) {
        level.getChunkSource().addRegionTicket(
            TicketType.create("arena_build", Comparator.comparingLong(ChunkPos::toLong)),
            pos, 2, pos
        );
        forcedChunks.add(pos);
    }

    // 2. Polling per FULL status
    for (ChunkPos pos : required) {
        while (System.currentTimeMillis() < deadline) {
            ChunkAccess chunk = level.getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
            if (chunk != null && chunk.getStatus() == ChunkStatus.FULL) {
                break;
            }
            LockSupport.parkNanos(10_000_000);  // 10ms
        }

        // Verifica finale
        ChunkAccess chunk = level.getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
        if (chunk == null || chunk.getStatus() != ChunkStatus.FULL) {
            return ChunkLoadResult.timeout(pos);
        }
    }

    return ChunkLoadResult.success(required);
}
```

**Sequenza su timeout/failure**:
```
Exception durante build
        │
        ▼
┌───────────────────────────────────────┐
│ 1. transaction.rollback()             │
│    - Rimuovi entità                   │
│    - Ripristina blocchi (reverse)     │
│    - Rilascia chunk tickets           │
└───────────────────────────────────────┘
        │
        ▼
┌───────────────────────────────────────┐
│ 2. Se instanceId != null:             │
│    instanceManager.scheduleClose(     │
│        instanceId,                    │
│        CloseReason.BUILD_FAILED       │
│    )                                  │
│    - Teleporta player a safe spawn    │
│    - Cleanup asincrono istanza        │
└───────────────────────────────────────┘
        │
        ▼
┌───────────────────────────────────────┐
│ 3. Telemetria                         │
│    arena.build.failed {               │
│      templateId, reason, message,     │
│      blocksPlaced, rollbackMs         │
│    }                                  │
└───────────────────────────────────────┘
        │
        ▼
    throw ArenaBuildException
```

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 10. Dry-Run estimatedMs - Strategia Ibrida

**Decisione**: Stima ibrida euristica + storico DuckDB. Accuratezza target: ±20% con storico, ±50% con euristica.

**Euristica base** (sempre disponibile):
```java
public long estimateBuildTimeMs(ArenaTemplate template) {
    int totalBlocks = estimateBlockCount(template);

    // Baseline: ~0.05ms per blocco su hardware medio
    long baseEstimate = (long)(totalBlocks * 0.05);

    // Fattori moltiplicativi
    if (template.structureNbt() != null) baseEstimate *= 1.5;  // NBT più lento
    if (template.hazards().size() > 10) baseEstimate *= 1.2;   // Hazard complexity
    if (template.buildPriority() == BuildPriority.ASYNC) baseEstimate *= 0.8;

    return baseEstimate;
}
```

**Storico DuckDB** (quando disponibile, ≥5 samples):
```java
public Long estimateFromHistory(String templateId) {
    var stats = duckDb.query("""
        SELECT
            percentile_cont(0.75) WITHIN GROUP (ORDER BY build_ms) as p75,
            count(*) as sample_size
        FROM arena_template_builds
        WHERE template_id = ? AND result = 'success'
        AND created_at > now() - interval '7 days'
        """, templateId);

    return stats.sampleSize() >= 5 ? stats.p75() : null;  // Fallback a euristica
}
```

**Accuratezza target**:
| Fonte | Accuratezza Target | Azione se superato |
|-------|-------------------|-------------------|
| Storico (≥10 samples) | ±20% | WARN se >20% off |
| Storico (5-9 samples) | ±35% | Accettabile |
| Euristica | ±50% | WARN sempre con nota "estimate only" |

**Feedback loop**: dopo ogni build, registra `(actualMs, estimatedMs, source)` per calibrazione.

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 11. Budget Tempo/Blocchi - Soglie Soft e Hard

**Decisione**: Due soglie separate - **WARN (80%)** e **ERROR (100%)**.

| Soglia | Tempo | Blocchi | Comportamento |
|--------|-------|---------|---------------|
| **WARN** | 80% budget | 80% budget | Log + telemetria, build continua |
| **ERROR** | 100% budget | 100% budget | Abort + rollback + telemetria |

```java
public class BuildBudget {
    private final long maxTimeMs;
    private final int maxBlocks;
    private final long startTime = System.currentTimeMillis();
    private int blocksPlaced = 0;
    private boolean warnedTime = false, warnedBlocks = false;

    public void checkBudget() {
        long elapsed = System.currentTimeMillis() - startTime;

        // WARN thresholds (80%)
        if (elapsed > maxTimeMs * 0.8 && !warnedTime) {
            telemetry.emit("arena.build.budget_warn", Map.of("type", "time"));
            warnedTime = true;
        }
        if (blocksPlaced > maxBlocks * 0.8 && !warnedBlocks) {
            telemetry.emit("arena.build.budget_warn", Map.of("type", "blocks"));
            warnedBlocks = true;
        }

        // HARD FAIL thresholds (100%)
        if (elapsed > maxTimeMs) {
            throw new BuildTimeoutException("Timeout: %dms > %dms".formatted(elapsed, maxTimeMs));
        }
        if (blocksPlaced > maxBlocks) {
            throw new BuildLimitExceededException("Blocks: %d > %d".formatted(blocksPlaced, maxBlocks));
        }
    }
}
```

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 12. Async Build - Tick Distribution e Backpressure

**Decisione**: Build async usa **rate limiting per tick** con **backpressure** basata su MSPT.

| Parametro | Default | Descrizione |
|-----------|---------|-------------|
| `blocksPerTick` | 500 | Blocchi massimi per tick |
| `msptThreshold` | 40ms | Se MSPT > 40, riduci rate |
| `minBlocksPerTick` | 100 | Rate minimo (non scende sotto) |
| `backpressureMultiplier` | 0.5 | Riduzione rate su lag |

```java
public class AsyncArenaBuilder {
    private int currentRate = config.blocksPerTick();  // 500 default

    public void tickBuild() {
        // 1. Backpressure check
        double mspt = server.getAverageTickTime();
        if (mspt > config.msptThreshold()) {
            currentRate = Math.max(
                config.minBlocksPerTick(),
                (int)(currentRate * config.backpressureMultiplier())
            );
            telemetry.emit("arena.build.backpressure", Map.of("mspt", mspt, "newRate", currentRate));
        } else if (currentRate < config.blocksPerTick()) {
            currentRate = Math.min(config.blocksPerTick(), currentRate + 50);  // Gradual recovery
        }

        // 2. Build N blocks this tick
        for (int i = 0; i < currentRate && hasMoreWork(); i++) {
            placeNextBlock();
            budget.checkBudget();
        }

        // 3. Check completion
        if (!hasMoreWork()) completeAsyncBuild();
    }
}
```

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 13. Metriche - Campi Obbligatori

**Decisione**: **Tutti** gli eventi `arena.build.*` includono sempre `ArenaMetricsContext`.

```java
public record ArenaMetricsContext(
    String templateId,
    int templateVersion,
    UUID instanceId,
    UUID arenaId,
    @Nullable UUID playerId,
    @Nullable UUID partyId,
    Instant timestamp
) {
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("templateId", templateId);
        map.put("templateVersion", templateVersion);
        map.put("instanceId", instanceId.toString());
        map.put("arenaId", arenaId.toString());
        if (playerId != null) map.put("playerId", playerId.toString());
        if (partyId != null) map.put("partyId", partyId.toString());
        map.put("ts", timestamp.toString());
        return map;
    }
}
```

**Eventi e campi aggiuntivi**:
| Evento | Campi Extra |
|--------|-------------|
| `arena.build.start` | `estimatedMs`, `estimatedBlocks` |
| `arena.build.end` | `actualMs`, `actualBlocks`, `success` |
| `arena.build.fail` | `reason`, `exception`, `blocksPlaced`, `rollbackMs` |
| `arena.build.rollback` | `blocksReverted`, `entitiesRemoved`, `durationMs` |

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 14. API Compatibility - Backward Compat con Overload

**Decisione**: Mantenere signature legacy deprecata + nuovo overload con `ResolveOptions` e return `ArenaHandle`.

```java
// LEGACY (deprecato ma funzionante)
@Deprecated
public void prepareArenaForParty(UUID partyId, ResourceLocation mobId) {
    ArenaHandle handle = prepareArenaForPartyV2(partyId, mobId, ResolveOptions.defaults());
    legacyHandles.put(partyId, handle);
}

// NEW API (preferita)
public ArenaHandle prepareArenaForPartyV2(UUID partyId, ResourceLocation mobId, ResolveOptions options) {
    ResolvedArena resolved = policyResolver.resolve(
        options.forcePolicyId(), mobId, options.questType(),
        getPartySize(partyId), options.tags()
    );
    return templateArenaBuilder.build(resolved.template(), resolved.policy(),
        instanceManager.getOrCreateInstance(partyId));
}

public record ResolveOptions(
    @Nullable String forcePolicyId,
    @Nullable String forceTemplateId,
    String questType,
    Set<String> tags
) {
    public static ResolveOptions defaults() {
        return new ResolveOptions(null, null, "normal", Set.of());
    }
}
```

**Fallback chain**: Nuovo sistema → Log warning + legacy fallback (se flag abilitato) → Abort.

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 15. ArenaHandle - Call-site Audit

**Decisione**: `ArenaHandle` è il **return type standard**. Audit completo dei consumer.

```java
public record ArenaHandle(
    UUID arenaId,
    UUID instanceId,
    String templateId,
    int templateVersion,
    String policyId,
    int policyVersion,
    AABB bounds,
    List<BlockPos> playerSpawnPositions,
    List<BlockPos> mobSpawnPositions,
    Instant createdAt
) {
    public BlockPos primaryPlayerSpawn() {
        return playerSpawnPositions.isEmpty() ? BlockPos.ZERO : playerSpawnPositions.get(0);
    }
}
```

**Call-site da migrare**:
| Call-site | File | Modifica |
|-----------|------|----------|
| `QuestStartSequence.prepareArena()` | QuestStartSequence.java | Usare `ArenaHandle` |
| `EnduranceQuestManager.startPreparedQuest()` | EnduranceQuestManager.java | Accettare `ArenaHandle` |
| `InstanceArenaManager.startInstanceQuestForParty()` | InstanceArenaManager.java | Return `ArenaHandle` |
| `WaveManager.spawnWave()` | WaveManager.java | Usare `handle.mobSpawnPositions()` |
| `EndurancePlayerStateManager.teleportToArena()` | EndurancePlayerStateManager.java | Usare `handle.primaryPlayerSpawn()` |
| `ArenaCleanupTask` | (nuovo) | Accettare `ArenaHandle` |
| `EnduranceTelemetryService.logArenaEvent()` | EnduranceTelemetryService.java | Estrarre context da handle |

**Migration**: Fase 1 (campo opzionale) → Fase 2 (required dopo migrazione).

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 16. Hot-Reload durante Session Attiva

**Decisione**: Session usa **snapshot immutabile** al momento della creazione. Hot-reload NON impatta session attive.

```java
public record ArenaTemplateSnapshot(
    String templateId,
    int templateVersion,
    Instant loadedAt,
    int registryGeneration  // incrementa ad ogni reload
) {}
```

**Comportamento**:
| Evento | Azione |
|--------|--------|
| Session creata | Snapshot catturato da registry |
| Hot-reload durante session | Session continua con snapshot originale |
| Fine session | Log `arena.session.version_drift` se mismatch |

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 17. Log Rotation e Retention

**Decisione**: Log `logs/arena-template-*.log` con **rotation giornaliera**, **retention 14 giorni**, **cap 500MB**.

```xml
<rollingPolicy>
    <fileNamePattern>logs/arena-template-%d{yyyy-MM-dd}.log.gz</fileNamePattern>
    <maxHistory>14</maxHistory>
    <totalSizeCap>500MB</totalSizeCap>
    <cleanHistoryOnStart>true</cleanHistoryOnStart>
</rollingPolicy>
```

**Protezioni**: maxHistory=14, totalSizeCap=500MB, compression .gz, cleanup on start.

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 18. Stacktrace Parsabile in JSON

**Decisione**: Stacktrace come **array di stringhe** (max 20 frames), parsabile con jq.

```java
public record ErrorContext(
    String type,           // "BuildTimeoutException"
    String message,        // "Timeout: 5000ms > 5000ms"
    List<String> stack,    // ["at com.frenkvs...Builder.build(Builder.java:123)", ...]
    @Nullable String cause
) {
    public static ErrorContext from(Throwable t) {
        return new ErrorContext(
            t.getClass().getSimpleName(),
            t.getMessage(),
            Arrays.stream(t.getStackTrace()).limit(20).map(Object::toString).toList(),
            t.getCause() != null ? t.getCause().getMessage() : null
        );
    }
}
```

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 19. Alert Routing su Tutti i Canali

**Decisione**: `AlertRouter` garantisce delivery su **tutti** i canali configurati, con retry per canali critici.

```java
public class AlertRouter {
    public void route(Alert alert) {
        for (AlertChannel channel : channels) {
            try {
                channel.send(alert);
            } catch (Exception e) {
                if (channel.isCritical() && alert.severity() == Severity.ERROR) {
                    retryQueue.add(new RetryTask(channel, alert, 3));
                }
            }
        }
    }
}
```

**Canali**:
| Canale | Sincrono? | Retry? | Critical? |
|--------|-----------|--------|-----------|
| console | Sì | No | No |
| log | Sì | No | Sì |
| dashboard | Async | 3x | No |
| telemetry | Async | 3x | Sì |
| webhook | Async | 3x | No |

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 20. NDJSON Write Non-blocking

**Decisione**: Write NDJSON è **async** con buffer 10k, non-blocking offer, flush periodico.

```java
public class NdjsonWriter {
    private final BlockingQueue<String> buffer = new ArrayBlockingQueue<>(10_000);

    public void write(Object event) {
        String json = gson.toJson(event);
        boolean accepted = buffer.offer(json);  // Non-blocking
        if (!accepted) droppedCount.incrementAndGet();
    }

    // Background writer: flush ogni 100 righe o 1 secondo
}
```

**Garanzie**: Tick thread mai bloccato, buffer full → drop con warning, flush ogni 100 righe o 1s.

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 21. DuckDB Indici per Dashboard

**Decisione**: 5 indici ottimizzati per query dashboard <200ms.

```sql
-- Builds
CREATE INDEX idx_builds_template_day ON arena_template_builds(template_id, created_at::DATE);
CREATE INDEX idx_builds_result_day ON arena_template_builds(result, created_at::DATE);
CREATE INDEX idx_builds_created ON arena_template_builds(created_at DESC);

-- Usage
CREATE INDEX idx_usage_template_day ON arena_template_usage(template_id, created_at::DATE);
CREATE INDEX idx_usage_player ON arena_template_usage(player_id, created_at DESC);
```

**Performance target**:
| Query | Target | Indice |
|-------|--------|--------|
| Build rate 7d | <100ms | idx_builds_template_day |
| Failure breakdown 30d | <200ms | idx_builds_result_day |
| Usage heatmap 7d | <150ms | idx_usage_template_day |
| Player history | <50ms | idx_usage_player |

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 22. UUID Generation - Idempotency Cache

**Decisione**: UUID generati con `UUID.randomUUID()` + cache idempotency 5 minuti per retry.

**Razionale**:
- UUID v4 crypto-random sufficientemente unico (collisione praticamente impossibile)
- Cache 5 min permette retry client senza creare duplicati
- Request ID → Arena UUID memoizzato

```java
public class ArenaIdempotencyCache {
    private final Cache<String, UUID> requestToArena = Caffeine.newBuilder()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .maximumSize(1000)
        .build();

    public UUID getOrCreate(String requestId) {
        return requestToArena.get(requestId, k -> UUID.randomUUID());
    }

    public void invalidate(String requestId) {
        requestToArena.invalidate(requestId);
    }
}
```

**Client side**:
```java
// Client genera request ID, riusa se retry
String requestId = UUID.randomUUID().toString();
ArenaHandle handle = arenaService.build(template, requestId);
// Se timeout, riprova con stesso requestId → stesso arena UUID
```

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 23. Retention Job - Scheduled Cleanup

**Decisione**: Job giornaliero 04:00 local con log separato per audit.

```java
public class RetentionJob implements Runnable {
    private static final Logger RETENTION_LOG = LoggerFactory.getLogger("arena.retention");

    private final NdjsonArchiver archiver;
    private final DuckDBCleaner duckCleaner;
    private final int retentionDays = 90;

    @Override
    public void run() {
        RETENTION_LOG.info("Retention job started");
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

        // 1. Archive NDJSON to cold storage
        ArchiveResult archiveResult = archiver.archiveOlderThan(cutoff);
        RETENTION_LOG.info("Archived {} files, {} bytes",
            archiveResult.fileCount(), archiveResult.totalBytes());

        // 2. Prune DuckDB (re-ingestable from NDJSON anyway)
        int rowsDeleted = duckCleaner.deleteOlderThan(cutoff);
        RETENTION_LOG.info("Pruned {} rows from DuckDB", rowsDeleted);

        RETENTION_LOG.info("Retention job completed");
    }
}

// Scheduling
scheduler.scheduleAtFixedRate(
    new RetentionJob(),
    computeDelayTo(LocalTime.of(4, 0)),
    Duration.ofDays(1).toMillis(),
    TimeUnit.MILLISECONDS
);
```

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 24. Source of Truth - NDJSON Primary

**Decisione**: NDJSON append-only è source of truth. DuckDB è materialized view, ricostruibile.

**Flusso dati**:
```
Events → NdjsonWriter → *.ndjson files (PRIMARY)
                              ↓
                    DuckDB Ingester (async)
                              ↓
                    DuckDB tables (MATERIALIZED VIEW)
```

**Recovery**:
```java
public class DuckDBRecovery {
    public void rebuildFromNdjson(Path ndjsonDir) {
        LOGGER.info("Rebuilding DuckDB from NDJSON...");

        // 1. Truncate tables
        duckdb.execute("TRUNCATE arena_template_builds");
        duckdb.execute("TRUNCATE arena_template_usage");

        // 2. Re-ingest all NDJSON
        Files.walk(ndjsonDir)
            .filter(p -> p.toString().endsWith(".ndjson"))
            .sorted() // cronological order
            .forEach(this::ingestFile);

        // 3. Rebuild indices
        rebuildIndices();

        LOGGER.info("DuckDB rebuild complete");
    }
}
```

**Vantaggi**:
- NDJSON è immutabile (append-only), no corruption
- DuckDB può essere ricostruito in caso di problemi
- Audit trail completo nei file NDJSON

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 25. Snapshot Versioning - Migration Chain

**Decisione**: Ogni snapshot ha `schemaVersion` + migration chain per backward compat.

```java
public record ArenaSessionSnapshot(
    int schemaVersion,  // Incrementato ad ogni breaking change
    UUID arenaId,
    String templateId,
    int templateVersion,
    Instant createdAt,
    // ... altri campi
    @Nullable String migratedFrom  // null se nativo
) {
    public static final int CURRENT_SCHEMA = 2;

    public static ArenaSessionSnapshot migrate(ArenaSessionSnapshot old) {
        if (old.schemaVersion() == CURRENT_SCHEMA) {
            return old;
        }

        // Chain migration: v1 → v2 → ... → current
        ArenaSessionSnapshot migrated = old;
        while (migrated.schemaVersion() < CURRENT_SCHEMA) {
            migrated = switch (migrated.schemaVersion()) {
                case 1 -> migrateV1ToV2(migrated);
                // case 2 -> migrateV2ToV3(migrated);
                default -> throw new IllegalStateException(
                    "Unknown schema version: " + migrated.schemaVersion());
            };
        }
        return migrated;
    }

    private static ArenaSessionSnapshot migrateV1ToV2(ArenaSessionSnapshot v1) {
        return new ArenaSessionSnapshot(
            2,
            v1.arenaId(),
            v1.templateId(),
            v1.templateVersion(),
            v1.createdAt(),
            "v1"  // Mark as migrated
        );
    }
}
```

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 26. Instance Naming - Constraints

**Decisione**: Nomi istanza max 32 chars, pattern `[a-z0-9_]`, sanitization automatica.

```java
public record InstanceName(String value) {
    private static final Pattern VALID_PATTERN = Pattern.compile("^[a-z0-9_]{1,32}$");
    private static final int MAX_LENGTH = 32;

    public InstanceName {
        if (!VALID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                "Instance name must match [a-z0-9_]{1,32}: " + value);
        }
    }

    public static InstanceName sanitize(String raw) {
        String sanitized = raw.toLowerCase()
            .replaceAll("[^a-z0-9_]", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_|_$", "");

        if (sanitized.isEmpty()) {
            sanitized = "instance";
        }
        if (sanitized.length() > MAX_LENGTH) {
            sanitized = sanitized.substring(0, MAX_LENGTH);
        }

        return new InstanceName(sanitized);
    }

    public static InstanceName generate(String templateId) {
        String base = sanitize(templateId).value();
        String suffix = "_" + System.currentTimeMillis() % 10000;

        int maxBase = MAX_LENGTH - suffix.length();
        if (base.length() > maxBase) {
            base = base.substring(0, maxBase);
        }

        return new InstanceName(base + suffix);
    }
}
```

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 27. Recovery Template Missing - Graceful Fallback

**Decisione**: Se template mancante durante recovery, fallback a default. No rebuild con template diverso.

```java
public Optional<ArenaRecoveryResult> attemptRecovery(ArenaSessionSnapshot snapshot) {
    // 1. Find template
    Optional<ArenaTemplate> template = registry.get(snapshot.templateId());

    if (template.isEmpty()) {
        LOGGER.warn("Template '{}' not found for recovery of arena {}, using default",
            snapshot.templateId(), snapshot.arenaId());
        telemetry.emit("arena.recovery.template_missing", Map.of(
            "arenaId", snapshot.arenaId(),
            "missingTemplate", snapshot.templateId()
        ));

        // Fallback a default - ma NON rebuildiamo l'arena
        // (non ha senso costruire un'arena diversa da quella originale)
        return Optional.of(ArenaRecoveryResult.degraded(
            snapshot.arenaId(),
            "Template missing, arena not rebuilt"
        ));
    }

    // 2. Version check
    ArenaTemplate t = template.get();
    if (t.version() != snapshot.templateVersion()) {
        LOGGER.warn("Template '{}' version mismatch: snapshot={}, current={}",
            snapshot.templateId(), snapshot.templateVersion(), t.version());
        telemetry.emit("arena.recovery.version_mismatch", Map.of(
            "arenaId", snapshot.arenaId(),
            "snapshotVersion", snapshot.templateVersion(),
            "currentVersion", t.version()
        ));
        // Procede comunque con warning
    }

    // 3. Rebuild
    return rebuildArena(snapshot, t);
}

public sealed interface ArenaRecoveryResult {
    record Success(UUID arenaId, ArenaHandle handle) implements ArenaRecoveryResult {}
    record Degraded(UUID arenaId, String reason) implements ArenaRecoveryResult {}
    record Failed(UUID arenaId, Exception cause) implements ArenaRecoveryResult {}
}
```

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 28. Tag Dictionary - Predefined + Autocomplete

**Decisione**: Tag predefiniti come enum + autocomplete UI + typo detection.

```java
public enum PredefinedTag {
    // Difficulty
    EASY("easy", "Low difficulty"),
    MEDIUM("medium", "Medium difficulty"),
    HARD("hard", "High difficulty"),
    NIGHTMARE("nightmare", "Extreme difficulty"),

    // Environment
    INDOOR("indoor", "Indoor arena"),
    OUTDOOR("outdoor", "Outdoor arena"),
    UNDERGROUND("underground", "Underground arena"),
    NETHER("nether", "Nether theme"),
    END("end", "End theme"),

    // Size
    SMALL("small", "Small arena (< 32 blocks)"),
    MEDIUM_SIZE("medium_size", "Medium arena (32-64 blocks)"),
    LARGE("large", "Large arena (> 64 blocks)"),

    // Type
    BOSS("boss", "Boss arena"),
    WAVE("wave", "Wave defense"),
    PVP("pvp", "PvP arena"),
    PUZZLE("puzzle", "Puzzle arena");

    private final String id;
    private final String description;

    public static List<PredefinedTag> autocomplete(String partial) {
        String lower = partial.toLowerCase();
        return Arrays.stream(values())
            .filter(t -> t.id.startsWith(lower) || t.description.toLowerCase().contains(lower))
            .limit(5)
            .toList();
    }

    public static Optional<PredefinedTag> findSimilar(String typo) {
        // Levenshtein distance <= 2
        return Arrays.stream(values())
            .filter(t -> levenshteinDistance(t.id, typo.toLowerCase()) <= 2)
            .min(Comparator.comparingInt(t -> levenshteinDistance(t.id, typo.toLowerCase())));
    }
}

// Validazione con suggerimenti
public record TagValidationResult(
    List<String> validTags,
    List<String> unknownTags,
    Map<String, String> suggestions  // typo → suggerito
) {
    public static TagValidationResult validate(List<String> tags) {
        List<String> valid = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        Map<String, String> suggestions = new HashMap<>();

        for (String tag : tags) {
            if (PredefinedTag.fromId(tag).isPresent()) {
                valid.add(tag);
            } else {
                unknown.add(tag);
                PredefinedTag.findSimilar(tag)
                    .ifPresent(similar -> suggestions.put(tag, similar.id()));
            }
        }

        return new TagValidationResult(valid, unknown, suggestions);
    }
}
```

**Warning in log per tag sconosciuti**:
```java
TagValidationResult result = TagValidationResult.validate(template.tags());
if (!result.unknownTags().isEmpty()) {
    LOGGER.warn("Template '{}' has unknown tags: {}. Suggestions: {}",
        template.id(), result.unknownTags(), result.suggestions());
}
```

**Ownership**: Game Designer / Core Dev
**Status**: ✅ DEFINITIVE

---

### 29. forceTemplateId Persistence

**Decisione**: Session state in-memory + player capability per sopravvivere a relog.

```java
public class TemplateOverrideManager {
    // In-memory per sessione corrente (fast path)
    private final Map<UUID, TemplateOverride> sessionOverrides = new ConcurrentHashMap<>();

    // Capability per persistenza cross-relog (max 1 ora)
    public void setOverride(ServerPlayer player, String templateId, Duration ttl) {
        TemplateOverride override = new TemplateOverride(templateId, Instant.now().plus(ttl));

        // 1. Session state (primario)
        sessionOverrides.put(player.getUUID(), override);

        // 2. Capability (backup per relog)
        player.getCapability(TEMPLATE_OVERRIDE_CAP).ifPresent(cap -> {
            cap.setOverride(override);
            cap.markDirty();
        });

        LOGGER.info("[Override] Player {} forced to template '{}' for {}",
            player.getName().getString(), templateId, ttl);
    }

    public Optional<String> getOverride(ServerPlayer player) {
        // Check session first (O(1))
        TemplateOverride override = sessionOverrides.get(player.getUUID());
        if (override != null && !override.isExpired()) {
            return Optional.of(override.templateId());
        }

        // Fallback to capability (relog recovery)
        return player.getCapability(TEMPLATE_OVERRIDE_CAP)
            .map(cap -> cap.getOverride())
            .filter(o -> !o.isExpired())
            .map(TemplateOverride::templateId);
    }
}
```

**NON persistito in**: config file, database, player profile permanente (è temporaneo per testing).

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 30. HUD Overlay Visibility

**Decisione**: Visibile solo con permission `devmod.debug.hud` + toggle esplicito. Default OFF.

```java
public class ArenaDebugHud {
    private static final String PERMISSION = "devmod.debug.hud";

    public static boolean shouldRender(Player player) {
        // 1. Permission check
        if (!PermissionAPI.getPermission(player, PERMISSION)) {
            return false;
        }

        // 2. Explicit toggle (keybind F7 o comando)
        return ArenaDebugState.isHudEnabled(player.getUUID());
    }

    // Toggle command: /devmod debug hud [on|off]
}
```

**Contenuto HUD** (solo per autorizzati):
- Template ID + version
- Arena UUID (troncato)
- Block count / budget %
- Tempo build
- Session duration

**Nessun rischio spam UI** per player normali.

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 31. Command Permissions & Audit

**Decisione**: Modello permessi granulare + audit log obbligatorio per comandi mutanti.

```java
public class ArenaCommandPermissions {
    // Read-only (info, list, status)
    public static final String INFO = "devmod.arena.info";           // Level 0
    public static final String LIST = "devmod.arena.list";           // Level 0

    // Testing (non-destructive)
    public static final String FORCE = "devmod.arena.force";         // Level 2 (OP)
    public static final String DEBUG = "devmod.arena.debug";         // Level 2

    // Mutating (destructive)
    public static final String BUILD = "devmod.arena.build";         // Level 3
    public static final String DESTROY = "devmod.arena.destroy";     // Level 3
    public static final String RELOAD = "devmod.arena.reload";       // Level 4 (Owner)

    // Admin
    public static final String ADMIN = "devmod.arena.admin";         // Level 4
}

// Audit log per comandi mutanti
public class ArenaCommandAudit {
    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("arena.audit");

    public static void log(CommandSourceStack source, String command, Map<String, Object> params) {
        String executor = source.getTextName();
        String ip = source.getEntity() instanceof ServerPlayer sp
            ? sp.getIpAddress() : "console";

        AUDIT_LOG.info("[AUDIT] executor={} ip={} command='{}' params={}",
            executor, ip, command, params);

        // Telemetry per dashboard admin
        telemetry.emit("arena.command.executed", Map.of(
            "executor", executor,
            "command", command,
            "params", params,
            "timestamp", Instant.now().toString()
        ));
    }
}
```

**Comandi con audit obbligatorio**: `build`, `destroy`, `reload`, `force`, `cleanup`.

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 32. Autosmoke Production Guard

**Decisione**: Hard-coded environment check + feature flag + file lock (triple guard).

```java
public class AutosmokeGuard {
    private static final Set<String> ALLOWED_ENVS = Set.of("dev", "staging", "ci");

    public static boolean canRun() {
        // 1. Environment variable (HARD CHECK)
        String env = System.getenv("DEVMOD_ENV");
        if (env == null || !ALLOWED_ENVS.contains(env.toLowerCase())) {
            LOGGER.error("[Autosmoke] BLOCKED: DEVMOD_ENV='{}' not in allowed list {}",
                env, ALLOWED_ENVS);
            return false;
        }

        // 2. Feature flag (soft check, può essere overridden in staging)
        if (!FeatureFlags.AUTOSMOKE_ENABLED.get()) {
            LOGGER.warn("[Autosmoke] Disabled by feature flag");
            return false;
        }

        // 3. Lock file check (production marker)
        Path prodMarker = Path.of("config/.production");
        if (Files.exists(prodMarker)) {
            LOGGER.error("[Autosmoke] BLOCKED: .production marker file exists");
            return false;
        }

        return true;
    }

    // Entry point
    public void runScheduled() {
        if (!canRun()) {
            AUDIT_LOG.warn("[Autosmoke] Blocked execution attempt");
            return;
        }
        // Proceed with tests...
    }
}
```

**Triple guard**: ENV var + feature flag + file marker. Tutti e tre devono passare.

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 33. Autosmoke Assert Exceptions

**Decisione**: Soglie differenziate per template size + whitelist eccezioni note.

```java
public record AutosmokeThresholds(
    int maxFailures,
    int maxRollbacks,
    int maxResiduals,
    Duration maxBuildTime
) {
    // Default strict
    public static final AutosmokeThresholds STRICT =
        new AutosmokeThresholds(0, 0, 0, Duration.ofSeconds(30));

    // Large templates (> 50k blocks)
    public static final AutosmokeThresholds LARGE =
        new AutosmokeThresholds(0, 0, 5, Duration.ofMinutes(2));  // 5 residui tollerati

    // Async builders (tick distribution)
    public static final AutosmokeThresholds ASYNC =
        new AutosmokeThresholds(0, 1, 0, Duration.ofMinutes(5));  // 1 rollback tollerato

    public static AutosmokeThresholds forTemplate(ArenaTemplate template) {
        int blockCount = template.estimatedBlockCount();
        boolean isAsync = template.buildMode() == BuildMode.ASYNC;

        if (blockCount > 50_000) return LARGE;
        if (isAsync) return ASYNC;
        return STRICT;
    }
}

// Whitelist eccezioni note (template-specific)
public class AutosmokeExceptions {
    private static final Map<String, Set<String>> KNOWN_ISSUES = Map.of(
        "nether_fortress_128", Set.of("RESIDUAL"),  // Lava flow residuals expected
        "end_void_arena", Set.of("ROLLBACK")         // Void damage can cause rollback
    );

    public static boolean isKnownIssue(String templateId, String issueType) {
        return KNOWN_ISSUES.getOrDefault(templateId, Set.of()).contains(issueType);
    }
}
```

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 34. Report Export Context

**Decisione**: Header completo con contesto build + config snapshot hash.

```java
public record AutosmokeReportHeader(
    // Build info
    String serverVersion,      // "1.21.4"
    String modVersion,         // "2.3.1"
    String gitCommit,          // "abc123f"
    String gitBranch,          // "main"
    Instant buildTime,         // Quando è stata buildata la mod

    // Runtime info
    String environment,        // "staging"
    String serverName,         // "test-server-01"
    Instant reportTime,        // Quando è stato generato il report

    // Config snapshot
    String configHash,         // SHA256 dei config rilevanti
    Map<String, String> configSnapshot  // Valori chiave config
) {
    public static AutosmokeReportHeader capture() {
        return new AutosmokeReportHeader(
            SharedConstants.getCurrentVersion().getName(),
            ModList.get().getModContainerById("devmod")
                .map(c -> c.getModInfo().getVersion().toString()).orElse("unknown"),
            System.getProperty("devmod.git.commit", "unknown"),
            System.getProperty("devmod.git.branch", "unknown"),
            Instant.ofEpochMilli(Long.parseLong(
                System.getProperty("devmod.build.time", "0"))),
            System.getenv("DEVMOD_ENV"),
            getServerName(),
            Instant.now(),
            computeConfigHash(),
            captureConfigSnapshot()
        );
    }

    private static Map<String, String> captureConfigSnapshot() {
        return Map.of(
            "arena.budget.blocks", String.valueOf(ArenaConfig.BUDGET_BLOCKS.get()),
            "arena.budget.time", String.valueOf(ArenaConfig.BUDGET_TIME.get()),
            "arena.async.enabled", String.valueOf(ArenaConfig.ASYNC_ENABLED.get()),
            "arena.async.blocks_per_tick", String.valueOf(ArenaConfig.BLOCKS_PER_TICK.get())
        );
    }
}
```

**CSV**: header row come commento `# ...` prima dei dati.
**JSON**: oggetto `header` separato con tutti i campi.

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 35. Dashboard Auth & Performance

**Decisione**: Auth required + query cache + background refresh.

```java
public class ArenaDashboardEndpoint {
    // Auth middleware
    @Before
    public void authenticate(Request req, Response res) {
        String token = req.header("Authorization");
        if (!AuthService.validateDashboardToken(token)) {
            halt(401, "Unauthorized");
        }

        // Rate limit per token
        if (!RateLimiter.allow(token, 60, Duration.ofMinutes(1))) {
            halt(429, "Rate limit exceeded");
        }
    }

    // Cached metrics (refreshed ogni 5 min in background)
    private static final LoadingCache<String, DashboardMetrics> metricsCache =
        Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .refreshAfterWrite(4, TimeUnit.MINUTES)  // Background refresh
            .build(key -> computeMetrics(key));

    @Get("/api/arena/metrics")
    public DashboardMetrics getMetrics() {
        // Cache hit → O(1), no query
        return metricsCache.get("global");
    }

    // Heavy queries run in background, not on request
    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    public void refreshMetricsBackground() {
        metricsCache.refresh("global");
    }
}
```

**Performance**: Query mai on-demand, sempre da cache pre-computata.

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 36. Analytics Query Limits

**Decisione**: Pagination obbligatoria + max range 30 giorni + query timeout.

```java
public record AnalyticsQueryParams(
    Instant from,
    Instant to,
    int page,
    int pageSize,
    String templateId  // Optional filter
) {
    public AnalyticsQueryParams {
        // Max range 30 giorni
        Duration range = Duration.between(from, to);
        if (range.toDays() > 30) {
            throw new IllegalArgumentException(
                "Max query range is 30 days, requested: " + range.toDays());
        }

        // Page size limits
        if (pageSize < 1 || pageSize > 1000) {
            throw new IllegalArgumentException(
                "Page size must be 1-1000, requested: " + pageSize);
        }

        // From must be before to
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must be before 'to'");
        }
    }

    public static final int DEFAULT_PAGE_SIZE = 100;
    public static final int MAX_PAGE_SIZE = 1000;
    public static final int MAX_RANGE_DAYS = 30;
}

// Query execution with timeout
public class AnalyticsService {
    private static final Duration QUERY_TIMEOUT = Duration.ofSeconds(10);

    public AnalyticsResult query(AnalyticsQueryParams params) {
        return CompletableFuture.supplyAsync(() -> executeQuery(params))
            .orTimeout(QUERY_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
            .exceptionally(ex -> {
                if (ex instanceof TimeoutException) {
                    LOGGER.warn("Analytics query timeout: {}", params);
                    return AnalyticsResult.timeout();
                }
                throw new RuntimeException(ex);
            })
            .join();
    }
}
```

**Limiti**:
| Parametro | Limite | Motivo |
|-----------|--------|--------|
| Range | 30 giorni | Evita full table scan |
| Page size | 1000 max | Memory bound |
| Timeout | 10 sec | UX e resource bound |

**Per query > 30 giorni**: Export job asincrono con notifica email/webhook.

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 37. Cleanup Robusto - Definizione Completa

**Decisione**: Cleanup in 4 fasi con checklist verificabile.

```java
public class ArenaCleanupExecutor {

    public record CleanupResult(
        int blocksRemoved,
        int entitiesRemoved,
        int blockEntitiesRemoved,
        int scheduledTicksCancelled,
        int chunksUnloaded,
        Duration duration,
        List<CleanupWarning> warnings
    ) {
        public boolean isComplete() { return warnings.isEmpty(); }
    }

    public CleanupResult cleanup(ArenaHandle handle) {
        AABB bounds = handle.bounds();
        ServerLevel level = handle.level();

        // FASE 1: Entità (prima dei blocchi per evitare item drops)
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, bounds,
            e -> CLEANUP_ENTITY_TYPES.contains(e.getType()));
        for (Entity entity : entities) {
            entity.discard();
        }

        // FASE 2: Block Entities (chest, furnace, sign, etc.)
        // Clear contents + removeBlockEntity

        // FASE 3: Scheduled Ticks (redstone, liquids)
        // Cancel tick programmati nell'area

        // FASE 4: Blocchi (set to AIR, batch update flags 2|64)

        // FASE 5 (opzionale): Chunk unload per istanze temporanee

        // VERIFICA POST-CLEANUP
        return new CleanupResult(...);
    }

    private static final Set<EntityType<?>> CLEANUP_ENTITY_TYPES = Set.of(
        EntityType.ITEM, EntityType.EXPERIENCE_ORB, EntityType.ARROW,
        EntityType.SPECTRAL_ARROW, EntityType.TRIDENT, EntityType.FALLING_BLOCK,
        EntityType.ITEM_FRAME, EntityType.ARMOR_STAND, EntityType.AREA_EFFECT_CLOUD
    );
}
```

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 38. Monitor MSPT/TPS - Measurement Strategy

**Decisione**: Misura isolata con baseline pre-build + sliding window per rumore esterno.

```java
public class MsptMonitor {
    private static final int BASELINE_SAMPLES = 20;  // 1 secondo
    private static final int WINDOW_SIZE = 100;      // 5 secondi sliding

    public void captureBaseline(MinecraftServer server) {
        // Mediana di 20 samples per robustezza
        baselineMspt = computeMedian(samples);
    }

    public MsptSample sample(MinecraftServer server) {
        double currentMspt = server.getAverageTickTimeNanos() / 1_000_000.0;
        double buildImpact = currentMspt - baselineMspt;

        // Confidence: bassa se c'è alta varianza (carico esterno)
        double confidence = Math.max(0, 1.0 - (stdDev / 10.0));

        return new MsptSample(currentMspt, windowAvg, baselineMspt, buildImpact, confidence);
    }

    public record MsptSample(
        double current, double windowAverage, double baseline,
        double buildImpact, double confidence
    ) {
        public boolean shouldBackpressure() {
            return confidence > 0.7 && buildImpact > 20.0;
        }
    }
}
```

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 39. Progress Overlay - Rate Limited

**Decisione**: Update max 4 Hz (250ms) + skip se delta < 1%.

```java
public class BuildProgressOverlay {
    private static final long UPDATE_INTERVAL_MS = 250;  // 4 Hz max
    private static final double MIN_PROGRESS_DELTA = 0.01;  // 1%

    public void onBuildProgress(UUID arenaId, int blocksPlaced, int totalBlocks) {
        long now = System.currentTimeMillis();
        double progress = (double) blocksPlaced / totalBlocks;

        // Rate limit + delta check
        if (now - lastUpdateTime < UPDATE_INTERVAL_MS) return;
        if (Math.abs(progress - lastProgress) < MIN_PROGRESS_DELTA && progress < 1.0) return;

        lastUpdateTime = now;
        lastProgress = progress;

        // Packet: 28 bytes (UUID + float + 2x int)
        NetworkHandler.sendToAllInArena(arenaId, new BuildProgressPacket(...));
    }
}
```

**Overhead**: ~1 packet/250ms, ~28 bytes. Trascurabile.

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 40. Edge Cases - Test Deterministici

**Decisione**: Test suite con scenari riproducibili + seed fisso.

```java
@Tag("arena-edge-cases")
public class ArenaEdgeCaseTests {
    private static final long FIXED_SEED = 12345L;

    @Test
    void failureAtMidBuild_shouldRollbackCompletely() {
        // Inject failure at block 500/1000
        // Verify: 0 residual blocks, 0 residual entities
    }

    @Test
    void chunkTimeout_shouldRollbackAndCloseInstance() {
        // Mock chunk loading con timeout > 5s
        // Verify: ArenaHandle.Failed, instance closed
    }

    @ParameterizedTest
    @MethodSource("malformedTemplates")
    void malformedTemplate_shouldRejectWithClearError(String json, String expectedError) {
        // Test: missing id, negative size, circular inheritance, nonexistent parent
    }

    @Test
    void twoPartiesConcurrent_shouldNotInterfere() {
        // 2 party parallele → 2 arene separate, nessuna interferenza
    }

    @Test
    void samePlayerTwoRequests_shouldBlockSecond() {
        // Second request → ArenaHandle.Rejected
    }
}
```

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 41. Coverage Policy - Minecraft-Dependent Code

**Decisione**: Coverage differenziato per layer.

| Layer | Target | Counter |
|-------|--------|---------|
| Core logic (template, policy, validation) | 80% | BRANCH |
| Minecraft-dependent (builder, cleanup) | 60% | LINE |
| Network/UI | 50% | LINE |

```kotlin
// build.gradle.kts
tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            includes = listOf("*.template.*", "*.policy.*", "*.validation.*")
            limit { counter = "BRANCH"; minimum = "0.80".toBigDecimal() }
        }
        rule {
            includes = listOf("*.builder.*", "*.cleanup.*")
            limit { counter = "LINE"; minimum = "0.60".toBigDecimal() }
        }
    }
}
```

**Mock strategy**: `@ExtendWith(MinecraftMockExtension.class)` per ServerLevel, ChunkAccess, etc.

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 42. Migration Call-Site - Complete Inventory

**Decisione**: 12 call-site da migrare in 6 PR incrementali.

**Inventory**:
| # | File | Metodo | PR |
|---|------|--------|-----|
| 1 | QuestStartSequence.java:145 | createArena | PR #1 |
| 2 | EnduranceQuestManager.java:234 | createArena | PR #1 |
| 3 | InstanceArenaManager.java:89 | createArena | PR #1 |
| 4 | WaveManager.java:156 | spawnWave | PR #2 |
| 5 | WaveManager.java:203 | getSpawnSlots | PR #2 |
| 6 | EndurancePlayerStateManager.java:67 | teleportToArena | PR #3 |
| 7 | EndurancePlayerStateManager.java:112 | respawnPlayer | PR #3 |
| 8 | ArenaCleanupTask.java:45 | getArena | PR #4 |
| 9 | EnduranceTelemetryService.java:78 | getArena | PR #4 |
| 10 | ArenaCommand.java:34 | /arena create | PR #5 |
| 11 | ArenaCommand.java:67 | /arena info | PR #5 |
| 12 | DebugArenaCommand.java:23 | debug | PR #5 |

**PR Plan**:
- PR #1: Core Quest Flow (3 call-sites) - depends on ArenaHandle
- PR #2: Wave System (2 call-sites) - depends on PR #1
- PR #3: Player Management (2 call-sites) - depends on PR #1
- PR #4: Cleanup & Telemetry (2 call-sites) - depends on PR #1, #2
- PR #5: Commands (3 call-sites) - depends on PR #1
- PR #6: Remove Legacy ArenaManager - depends on all

**Ownership**: Tech Lead
**Status**: ✅ DEFINITIVE

---

### 43. Zero Legacy Call-Site Verification

**Decisione**: CI grep gate + runtime deprecation warning.

```yaml
# .github/workflows/legacy-check.yml
- name: Check for legacy ArenaManager usage
  run: |
    PATTERNS=("ArenaManager\.createArena" "ArenaManager\.getArena"
              "ArenaManager\.destroyArena" "prepareArenaForParty\s*\(")
    for pattern in "${PATTERNS[@]}"; do
      if grep -rn "$pattern" src/main/java; then
        echo "::error::Legacy call-site found: $pattern"
        exit 1
      fi
    done
```

**Runtime warning** (non-prod):
```java
@Deprecated(since = "2.4.0", forRemoval = true)
public static Arena createArena(...) {
    if (!"prod".equals(System.getenv("DEVMOD_ENV"))) {
        LOGGER.error("[DEPRECATED] ArenaManager.createArena() called from: {}",
            Thread.currentThread().getStackTrace()[2]);
        telemetry.emit("arena.legacy.call", ...);
    }
    return arenaService.prepareArenaForParty(...).toLegacyArena();
}
```

**Dashboard metric**: `arena.legacy.call` count = 0 in prod.

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 44. Rollback Plan - Staging Validation

**Decisione**: Scenario di test obbligatorio in staging con checklist pre-deploy.

```java
@Tag("staging-only")
public class RollbackTestScenario {

    @Test
    void scenario_buildFailMidway_shouldRollbackAndAbortQuest() {
        // SETUP: Party con 2 player, failure injection a block 500
        // VERIFY 1: Build fallisce (ArenaHandle.Failed)
        // VERIFY 2: Rollback completo (0 residual blocks/entities)
        // VERIFY 3: Quest abortita
        // VERIFY 4: Player ricevono messaggio user-friendly
        // VERIFY 5: Telemetry emessa
    }

    @Test
    void scenario_fallbackToDefault_shouldSucceed() {
        // Template custom non disponibile → fallback usato
    }

    @Test
    void scenario_defaultAlsoFails_shouldAbortGracefully() {
        // Worst case: graceful abort, no tech details to player
    }
}
```

**Staging Checklist pre-deploy**:
- [ ] Eseguito `RollbackTestScenario` con 0 failure
- [ ] Verificato log ha stack trace completo
- [ ] Verificato player message è user-friendly
- [ ] Verificato metriche dashboard popolate

**Ownership**: QA / Core Dev
**Status**: ✅ DEFINITIVE

---

### 45. Fallback Chain - Retry Limits

**Decisione**: Max 1 retry con default, circuit breaker, metriche dedicate.

```java
public class FallbackBuildStrategy {
    private static final int MAX_RETRIES = 1;
    private static final Duration CIRCUIT_BREAKER_WINDOW = Duration.ofMinutes(5);
    private static final int CIRCUIT_BREAKER_THRESHOLD = 3;

    public ArenaHandle buildWithFallback(Party party, ResolveOptions options) {
        if (circuitBreaker.isOpen()) {
            return ArenaHandle.rejected("Arena service temporarily unavailable");
        }

        ArenaHandle primary = tryBuild(options.primaryTemplate());
        if (primary instanceof ArenaHandle.Success) {
            return primary;
        }

        circuitBreaker.recordFailure();

        if (options.fallbackTemplate() == null) {
            return primary;
        }

        return tryBuild(options.fallbackTemplate());
    }
}
```

**Limiti anti-carico**:
| Limite | Valore | Motivo |
|--------|--------|--------|
| Max retries | 1 | No double-build indefinito |
| Circuit breaker | 3 failures/5min | Protegge da cascade |
| Cooldown dopo open | 30 sec | Recovery graduale |

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 46. Default Fail - User Message

**Decisione**: Messaggio player chiaro, non tecnico. Stack trace solo in log.

```java
public class ArenaFailureHandler {
    private static final Map<FailureType, String> PLAYER_MESSAGES = Map.of(
        FailureType.TEMPLATE_NOT_FOUND,
            "L'arena richiesta non è disponibile. Riprova più tardi.",
        FailureType.BUILD_TIMEOUT,
            "La creazione dell'arena sta impiegando troppo tempo. Riprova.",
        FailureType.BUILD_FAILED,
            "Si è verificato un problema nella creazione dell'arena.",
        FailureType.ALL_FALLBACKS_EXHAUSTED,
            "Impossibile creare l'arena al momento. Riprova tra qualche minuto."
    );

    public void handleFailure(Party party, ArenaHandle.Failed failure) {
        // 1. Log completo per dev (con stack trace)
        LOGGER.error("[ArenaFailure] ...", failure.cause());

        // 2. Messaggio player (user-friendly, NO tech details)
        String playerMessage = PLAYER_MESSAGES.get(failure.type());
        for (ServerPlayer player : party.onlineMembers()) {
            player.sendSystemMessage(Component.literal(playerMessage));
        }

        // 3. Alert per critici
        if (failure.type().isCritical()) {
            alertService.sendCritical(...);
        }
    }
}
```

**Requisiti**:
- ❌ NO: "NullPointerException at ArenaBuilder.java:234"
- ✅ SI: "L'arena non è disponibile. Riprova più tardi."

**Ownership**: Core Dev / UX
**Status**: ✅ DEFINITIVE

---

### 47. SpawnSlots - Melee/Ranged Distance

**Decisione**: Distanze definite + vincoli hard (LOS, collision, forbidden zones).

```java
public record SpawnSlotConstraints(
    float meleeMinDistance,      // 3 blocks
    float meleeMaxDistance,      // 15 blocks
    float rangedMinDistance,     // 12 blocks
    float rangedMaxDistance,     // 30 blocks
    boolean requireLineOfSight,
    boolean requireGroundBlock,
    float minPlayerDistance,     // 5 blocks
    List<AABB> forbiddenZones
) {
    public static final SpawnSlotConstraints DEFAULT = new SpawnSlotConstraints(
        3.0f, 15.0f, 12.0f, 30.0f, true, true, 5.0f, List.of());
}
```

**Distanze default**:
| Tipo | Min | Max | Note |
|------|-----|-----|------|
| Melee | 3 | 15 | Vicino ma non addosso |
| Ranged | 12 | 30 | Lontano per sparare |
| Boss | 8 | 20 | Centro arena |
| Support | 15 | 25 | Dietro ranged |

**Ownership**: Game Designer / Core Dev
**Status**: ✅ DEFINITIVE

---

### 48. SpawnSlotValidator - Performance

**Decisione**: Validazione cached al load, check leggero runtime.

```java
public class SpawnSlotValidator {
    private final Map<String, ValidationCache> templateCache = new ConcurrentHashMap<>();

    // O(n²) - chiamato UNA VOLTA al load
    public ValidationResult validateAtLoad(ArenaTemplate template) {
        // Bounds check, collision check, forbidden zones
        // Cache valid slots per tipo
    }

    // O(1) + O(entities) - chiamato AD OGNI SPAWN
    public boolean isValidForSpawn(String templateId, BlockPos pos, SpawnType type) {
        ValidationCache cache = templateCache.get(templateId);
        if (!cache.getValidPositions(type).contains(pos)) {
            return false;
        }
        return !isPositionOccupied(pos);  // O(entities in small area)
    }
}
```

**Performance**:
| Operazione | Complessità | Quando |
|------------|-------------|--------|
| validateAtLoad | O(n²) | 1 volta al load |
| isValidForSpawn | O(1) + O(entities) | Ogni spawn |

**Overhead runtime**: ~0.1ms per spawn.

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 49. Heatmap - Privacy & Aggregation

**Decisione**: Aggregazione spaziale (5x5 grid) + temporale (hourly) + no player ID.

```java
public class HeatmapCollector {
    private static final int CELL_SIZE = 5;  // 5x5 blocks per cell
    private static final Duration AGGREGATION_WINDOW = Duration.ofHours(1);

    public record HeatmapEvent(
        String templateId,
        HeatmapEventType type,
        int cellX, int cellZ,  // Aggregato, non esatto
        Instant hourBucket,    // Arrotondato all'ora
        int count              // Counter, non singoli
    ) {}

    // Flush ogni 5 minuti (batch write)
    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    public void flush() { ... }
}
```

**Privacy measures**:
| Misura | Implementazione |
|--------|-----------------|
| No player ID | Eventi anonimi |
| Posizione aggregata | 5x5 cell |
| Tempo aggregato | Bucket orario |
| Retention | 30 giorni → aggregato settimanale |

**Ownership**: Core Dev / Privacy Officer
**Status**: ✅ DEFINITIVE

---

### 50. Mutator/Perk Binding - Soft vs Hard

**Decisione**: `SUGGESTED` = soft (preferenza UI), `EXCLUDED`/`REQUIRED` = hard (blocco).

```java
public record MutatorBinding(String mutatorId, BindingType type, String reason) {
    public enum BindingType {
        SUGGESTED,  // Soft: evidenziato in UI, ma selezionabile
        EXCLUDED,   // Hard: grayed out, non cliccabile
        REQUIRED    // Hard: sempre attivo, non disattivabile
    }
}
```

**Decision matrix**:
| Binding | UI Effect | Runtime Effect | Chi decide |
|---------|-----------|----------------|------------|
| SUGGESTED | Evidenziato, in alto | Nessuno | Game Designer |
| EXCLUDED | Grayed out | Hard block | Game Designer |
| REQUIRED | Locked on | Sempre attivo | Game Designer |

**Policy owner**: Game Designer definisce nel policy JSON. Tech non può override.

**Ownership**: Game Designer / Core Dev
**Status**: ✅ DEFINITIVE

---

### 51. Perk Suggestions Bias Prevention

**Decisione**: Shuffle dei SUGGESTED, A/B test 10%, weekly winrate analysis.

```java
public class PerkSuggestionEngine {
    private static final double AB_TEST_RATIO = 0.10;  // 10% random

    public List<String> getSuggestedPerks(ArenaPolicy policy, UUID playerId) {
        List<MutatorBinding> suggested = policy.mutatorBindings().stream()
            .filter(b -> b.type() == BindingType.SUGGESTED)
            .toList();

        // Shuffle per evitare position bias
        List<String> result = new ArrayList<>(suggested.stream()
            .map(MutatorBinding::mutatorId)
            .toList());
        Collections.shuffle(result);

        // A/B test: 10% riceve lista random invece che SUGGESTED
        if (isInAbTestGroup(playerId)) {
            telemetry.emit("perk.suggestion.ab_test", Map.of(
                "playerId", playerId,
                "group", "random"
            ));
            return getRandomPerks(policy);
        }

        return result;
    }

    private boolean isInAbTestGroup(UUID playerId) {
        // Deterministic based on UUID for consistency
        return (playerId.hashCode() & 0x7FFFFFFF) % 100 < (AB_TEST_RATIO * 100);
    }
}
```

**Weekly Analysis Query (DuckDB)**:
```sql
SELECT
    perk_id,
    COUNT(*) as picks,
    AVG(CASE WHEN win THEN 1.0 ELSE 0.0 END) as winrate,
    AVG(position_in_list) as avg_position
FROM perk_selection_events
WHERE timestamp > NOW() - INTERVAL '7 days'
GROUP BY perk_id
ORDER BY picks DESC;
```

**Ownership**: Data Analyst / Core Dev
**Status**: ✅ DEFINITIVE

---

### 52. Badge Template Tracking

**Decisione**: Usage table come source of truth, conteggio version-agnostic.

```java
public record BadgeUsage(
    String badgeId,
    String templateId,      // Version-agnostic
    int timesAwarded,
    Instant lastAwarded
) {}

// Query: "quante volte il badge X è stato dato in template Y (qualsiasi versione)?"
public int getBadgeCountForTemplate(String badgeId, String templateId) {
    return duckDb.query("""
        SELECT COUNT(*) FROM badge_awards
        WHERE badge_id = ? AND template_id = ?
        """, badgeId, templateId).getInt(0);
}

// Migration: popolare da NDJSON esistenti
public void migrateBadgeUsage() {
    duckDb.execute("""
        INSERT INTO badge_usage (badge_id, template_id, times_awarded, last_awarded)
        SELECT
            badge_id,
            template_id,
            COUNT(*) as times_awarded,
            MAX(timestamp) as last_awarded
        FROM badge_awards_ndjson
        GROUP BY badge_id, template_id
        """);
}
```

**Ownership**: Tools Dev / Core Dev
**Status**: ✅ DEFINITIVE

---

### 53. Reward Multipliers - Calculation & Bounds

**Decisione**: `weight * 0.05 + 1.0`, bounds `[0.5, 2.0]`, anti-exploit checks.

```java
public record RewardMultiplier(double value, String source) {
    public static final double MIN_MULTIPLIER = 0.5;
    public static final double MAX_MULTIPLIER = 2.0;

    public static RewardMultiplier fromWeight(int weight) {
        double raw = weight * 0.05 + 1.0;
        double clamped = Math.clamp(raw, MIN_MULTIPLIER, MAX_MULTIPLIER);
        return new RewardMultiplier(clamped, "weight_based");
    }

    public int applyTo(int baseReward) {
        return (int) Math.round(baseReward * value);
    }
}

// Anti-exploit: detect grinding patterns
public class RewardAntiExploit {
    private static final int MAX_REWARDS_PER_HOUR = 20;
    private static final int SUSPICIOUS_SPEED_THRESHOLD_MS = 60_000;  // 1 min

    public boolean shouldGrantReward(UUID playerId, ArenaSession session) {
        int rewardsThisHour = getRewardsInLastHour(playerId);
        if (rewardsThisHour >= MAX_REWARDS_PER_HOUR) {
            telemetry.emit("reward.rate_limited", Map.of("playerId", playerId));
            return false;
        }

        long duration = session.durationMs();
        if (duration < SUSPICIOUS_SPEED_THRESHOLD_MS) {
            telemetry.emit("reward.suspicious_speed", Map.of(
                "playerId", playerId,
                "durationMs", duration
            ));
            // Log but still grant - manual review later
        }

        return true;
    }
}
```

**Ownership**: Game Designer / Core Dev
**Status**: ✅ DEFINITIVE

---

### 54. Currency Source Enum

**Decisione**: Enum ~15 valori per controlled cardinality, `sourceId` campo separato.

```java
public enum CurrencySource {
    // Arena completion
    ARENA_VICTORY,
    ARENA_PARTICIPATION,
    ARENA_BONUS,

    // Quests
    QUEST_COMPLETE,
    QUEST_BONUS,
    QUEST_MILESTONE,

    // Challenges
    DAILY_CHALLENGE,
    WEEKLY_CHALLENGE,
    MONTHLY_CHALLENGE,

    // Events
    EVENT_REWARD,
    EVENT_MILESTONE,

    // Economy
    PURCHASE_REFUND,
    ADMIN_GRANT,
    MIGRATION_GRANT,

    // Meta
    ACHIEVEMENT_UNLOCK,
    LEVEL_UP_BONUS
}

public record CurrencyGrant(
    UUID playerId,
    int amount,
    CurrencySource source,
    String sourceId,      // es. "arena:boss_ring_80", "quest:daily_123"
    Instant timestamp
) {
    public CurrencyGrant {
        Objects.requireNonNull(source);
        if (sourceId != null && sourceId.length() > 64) {
            throw new IllegalArgumentException("sourceId max 64 chars");
        }
    }
}
```

**Query example (analytics)**:
```sql
SELECT source, SUM(amount) as total, COUNT(*) as grants
FROM currency_grants
WHERE timestamp > NOW() - INTERVAL '7 days'
GROUP BY source
ORDER BY total DESC;
```

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 55. Challenge Generation - Availability Checks

**Decisione**: 5 availability checks, fallback a challenge generica.

```java
public class ChallengeGenerator {

    public Challenge generateFor(Player player, ChallengeType type) {
        List<ChallengeTemplate> candidates = getCandidates(type);

        for (ChallengeTemplate template : candidates) {
            AvailabilityResult result = checkAvailability(player, template);
            if (result.isAvailable()) {
                return instantiate(template, player);
            }
            telemetry.emit("challenge.skipped", Map.of(
                "templateId", template.id(),
                "reason", result.reason()
            ));
        }

        // Fallback: challenge generica sempre disponibile
        LOGGER.warn("No challenge available for {} type={}, using fallback",
            player.getName(), type);
        return getFallbackChallenge(type);
    }

    private AvailabilityResult checkAvailability(Player player, ChallengeTemplate t) {
        // 1. Level requirement
        if (player.getLevel() < t.minLevel()) {
            return AvailabilityResult.unavailable("level_too_low");
        }

        // 2. Prerequisite challenges completed
        if (!hasCompletedPrerequisites(player, t.prerequisites())) {
            return AvailabilityResult.unavailable("missing_prerequisites");
        }

        // 3. Cooldown (non ripetere stessa challenge troppo presto)
        if (isOnCooldown(player, t)) {
            return AvailabilityResult.unavailable("on_cooldown");
        }

        // 4. Arena/template availability
        if (t.requiredTemplateId() != null && !isTemplateAvailable(t.requiredTemplateId())) {
            return AvailabilityResult.unavailable("template_unavailable");
        }

        // 5. Time-gated (es. weekend-only challenges)
        if (!isWithinTimeWindow(t)) {
            return AvailabilityResult.unavailable("outside_time_window");
        }

        return AvailabilityResult.available();
    }

    private Challenge getFallbackChallenge(ChallengeType type) {
        return switch (type) {
            case DAILY -> new GenericChallenge("Play 3 arena matches", 3);
            case WEEKLY -> new GenericChallenge("Win 5 arena matches", 5);
            case MONTHLY -> new GenericChallenge("Complete 20 arena matches", 20);
        };
    }
}

public sealed interface AvailabilityResult {
    boolean isAvailable();
    String reason();

    record Available() implements AvailabilityResult {
        public boolean isAvailable() { return true; }
        public String reason() { return "available"; }
    }

    record Unavailable(String reason) implements AvailabilityResult {
        public boolean isAvailable() { return false; }
    }

    static AvailabilityResult available() { return new Available(); }
    static AvailabilityResult unavailable(String reason) { return new Unavailable(reason); }
}
```

**Ownership**: Game Designer / Core Dev
**Status**: ✅ DEFINITIVE

---

### 56. Leaderboard Batch Calculation

**Decisione**: Calcolo batch 03:00 daily, Redis cache, O(1) read.

```java
public class LeaderboardService {
    private static final LocalTime CALCULATION_TIME = LocalTime.of(3, 0);
    private static final Duration CACHE_TTL = Duration.ofHours(25);  // Overlap per safety

    // Scheduled job - 03:00 daily server time
    @Scheduled(cron = "0 0 3 * * *")
    public void calculateDailyLeaderboards() {
        LOGGER.info("Starting daily leaderboard calculation");
        Instant start = Instant.now();

        for (LeaderboardType type : LeaderboardType.values()) {
            try {
                calculateAndCache(type);
            } catch (Exception e) {
                LOGGER.error("Failed to calculate leaderboard {}", type, e);
                alertRouter.send(Alert.error("leaderboard.calculation_failed",
                    Map.of("type", type.name())));
            }
        }

        LOGGER.info("Leaderboard calculation completed in {}ms",
            Duration.between(start, Instant.now()).toMillis());
    }

    private void calculateAndCache(LeaderboardType type) {
        List<LeaderboardEntry> entries = duckDb.query(type.getQuery());

        // Cache in Redis with TTL
        String key = "leaderboard:" + type.name().toLowerCase();
        redis.set(key, serialize(entries), CACHE_TTL);

        telemetry.emit("leaderboard.calculated", Map.of(
            "type", type.name(),
            "entries", entries.size()
        ));
    }

    // O(1) read - always from cache
    public List<LeaderboardEntry> getLeaderboard(LeaderboardType type, int page, int pageSize) {
        String key = "leaderboard:" + type.name().toLowerCase();
        List<LeaderboardEntry> all = redis.get(key, LeaderboardEntry.LIST_TYPE);

        if (all == null) {
            // Cache miss - should not happen, trigger recalc
            LOGGER.warn("Leaderboard cache miss for {}, returning empty", type);
            return List.of();
        }

        int start = page * pageSize;
        int end = Math.min(start + pageSize, all.size());
        return all.subList(start, end);
    }

    // Player rank lookup - O(1) via secondary index
    public OptionalInt getPlayerRank(LeaderboardType type, UUID playerId) {
        String key = "leaderboard:" + type.name().toLowerCase() + ":ranks";
        Integer rank = redis.hget(key, playerId.toString(), Integer.class);
        return rank != null ? OptionalInt.of(rank) : OptionalInt.empty();
    }
}

public enum LeaderboardType {
    WEEKLY_WINS("""
        SELECT player_id, COUNT(*) as score
        FROM arena_sessions
        WHERE win = true AND timestamp > NOW() - INTERVAL '7 days'
        GROUP BY player_id
        ORDER BY score DESC
        LIMIT 1000
        """),

    MONTHLY_POINTS("""
        SELECT player_id, SUM(points) as score
        FROM arena_sessions
        WHERE timestamp > NOW() - INTERVAL '30 days'
        GROUP BY player_id
        ORDER BY score DESC
        LIMIT 1000
        """),

    ALL_TIME_WINS("""
        SELECT player_id, COUNT(*) as score
        FROM arena_sessions
        WHERE win = true
        GROUP BY player_id
        ORDER BY score DESC
        LIMIT 1000
        """);

    private final String query;

    LeaderboardType(String query) { this.query = query; }
    public String getQuery() { return query; }
}
```

**Ownership**: Core Dev / Tools Dev
**Status**: ✅ DEFINITIVE

---

### 57. Telemetry Propagation Audit

**Decisione**: Audit automatico via `TelemetryAuditJob` + CI check per eventi orfani.

```java
public class TelemetryAuditJob {
    private static final Set<String> REQUIRED_FIELDS = Set.of(
        "templateId", "templateVersion", "sessionId", "arenaId"
    );

    // Scheduled daily 05:00
    @Scheduled(cron = "0 0 5 * * *")
    public void auditTelemetryCompleteness() {
        Instant since = Instant.now().minus(Duration.ofDays(1));

        // Query: eventi senza campi obbligatori
        List<OrphanEvent> orphans = duckDb.query("""
            SELECT event_type, COUNT(*) as count,
                   CASE WHEN template_id IS NULL THEN 'template_id' END as missing_field
            FROM arena_events
            WHERE timestamp > ?
              AND (template_id IS NULL OR template_version IS NULL
                   OR session_id IS NULL OR arena_id IS NULL)
            GROUP BY event_type, missing_field
            """, since);

        if (!orphans.isEmpty()) {
            LOGGER.error("Found {} orphan event types without required fields", orphans.size());
            alertRouter.send(Alert.error("telemetry.orphan_events", Map.of(
                "orphanTypes", orphans.stream().map(OrphanEvent::eventType).toList(),
                "count", orphans.stream().mapToInt(OrphanEvent::count).sum()
            )));
        }

        // Sub-service coverage check
        Set<String> expectedServices = Set.of(
            "ArenaBuilder", "PolicyResolver", "SpawnManager", "WaveManager",
            "HazardManager", "RewardService", "ChallengeService", "LeaderboardService",
            "CleanupExecutor", "RecoveryService", "MetricsAggregator", "SessionManager"
        );

        Set<String> foundServices = duckDb.query("""
            SELECT DISTINCT source_service FROM arena_events
            WHERE timestamp > ?
            """, since);

        Set<String> missing = new HashSet<>(expectedServices);
        missing.removeAll(foundServices);

        if (!missing.isEmpty()) {
            LOGGER.warn("Sub-services without telemetry in last 24h: {}", missing);
        }
    }
}
```

**12 sub-services verificati**:
| Service | Event Prefix | Context Required |
|---------|-------------|------------------|
| ArenaBuilder | `arena.build.*` | ✅ |
| PolicyResolver | `arena.policy.*` | ✅ |
| SpawnManager | `arena.spawn.*` | ✅ |
| WaveManager | `arena.wave.*` | ✅ |
| HazardManager | `arena.hazard.*` | ✅ |
| RewardService | `arena.reward.*` | ✅ |
| ChallengeService | `arena.challenge.*` | ✅ |
| LeaderboardService | `arena.leaderboard.*` | ✅ |
| CleanupExecutor | `arena.cleanup.*` | ✅ |
| RecoveryService | `arena.recovery.*` | ✅ |
| MetricsAggregator | `arena.metrics.*` | ✅ |
| SessionManager | `arena.session.*` | ✅ |

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 58. Room ID Uniqueness & Session Reconnect

**Decisione**: `roomId = arenaId` (UUID), `sessionId` separato per reconnect tracking.

```java
public record ArenaIdentity(
    UUID arenaId,       // Immutabile, generato al build
    String templateId,  // Per routing/analytics
    int templateVersion,
    UUID sessionId,     // Cambia ad ogni reconnect
    int reconnectCount
) {
    // arenaId è il "room id" per telemetria - sempre consistente
    public String roomId() {
        return arenaId.toString();
    }
}

public class SessionReconnectHandler {
    private final Map<UUID, ReconnectState> reconnectStates = new ConcurrentHashMap<>();

    public ArenaIdentity handleReconnect(UUID playerId, UUID arenaId) {
        ReconnectState state = reconnectStates.computeIfAbsent(arenaId,
            k -> new ReconnectState(arenaId, getTemplateInfo(arenaId)));

        UUID newSessionId = UUID.randomUUID();
        int reconnectCount = state.incrementReconnect(playerId);

        telemetry.emit("arena.session.reconnect", Map.of(
            "arenaId", arenaId,           // Sempre lo stesso
            "sessionId", newSessionId,     // Nuovo per questo reconnect
            "playerId", playerId,
            "reconnectCount", reconnectCount,
            "templateId", state.templateId()
        ));

        return new ArenaIdentity(
            arenaId,
            state.templateId(),
            state.templateVersion(),
            newSessionId,
            reconnectCount
        );
    }
}
```

**Consistenza garantita**:
- `arenaId` (roomId): generato una volta, immutabile per tutta la vita dell'arena
- `sessionId`: nuovo ad ogni reconnect, per tracciare comportamento player
- Query analytics usa sempre `arenaId` per aggregare, `sessionId` per dettaglio

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 59. Balance Report Job

**Decisione**: Server job settimanale (Domenica 06:00), costo stimato <30s, output JSON + Slack.

```java
public class BalanceReportJob {
    private static final Duration REPORT_WINDOW = Duration.ofDays(7);
    private static final int QUERY_TIMEOUT_SEC = 30;

    // Domenica 06:00 server time
    @Scheduled(cron = "0 0 6 * * SUN")
    public void generateWeeklyBalanceReport() {
        Instant start = Instant.now();
        LOGGER.info("Starting weekly balance report generation");

        try {
            BalanceReport report = generateReport();

            // Persist JSON
            Path reportPath = Path.of("reports/balance/weekly_" +
                LocalDate.now().format(DateTimeFormatter.ISO_DATE) + ".json");
            Files.writeString(reportPath, GSON.toJson(report));

            // Slack summary
            slackNotifier.send(formatSlackSummary(report));

            long durationMs = Duration.between(start, Instant.now()).toMillis();
            telemetry.emit("balance.report.generated", Map.of(
                "durationMs", durationMs,
                "templatesAnalyzed", report.templateStats().size(),
                "totalSessions", report.totalSessions()
            ));

        } catch (Exception e) {
            LOGGER.error("Balance report generation failed", e);
            alertRouter.send(Alert.error("balance.report.failed", Map.of()));
        }
    }

    private BalanceReport generateReport() {
        Instant since = Instant.now().minus(REPORT_WINDOW);

        // Query 1: Template winrates
        List<TemplateStats> templateStats = duckDb.queryWithTimeout("""
            SELECT
                template_id,
                COUNT(*) as sessions,
                AVG(CASE WHEN win THEN 1.0 ELSE 0.0 END) as winrate,
                AVG(duration_ms) as avg_duration,
                PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY duration_ms) as median_duration
            FROM arena_sessions
            WHERE timestamp > ?
            GROUP BY template_id
            ORDER BY sessions DESC
            """, QUERY_TIMEOUT_SEC, since);

        // Query 2: Perk usage & winrate
        List<PerkStats> perkStats = duckDb.queryWithTimeout("""
            SELECT
                perk_id,
                COUNT(*) as picks,
                AVG(CASE WHEN win THEN 1.0 ELSE 0.0 END) as winrate
            FROM perk_selection_events
            WHERE timestamp > ?
            GROUP BY perk_id
            HAVING COUNT(*) > 10
            ORDER BY winrate DESC
            """, QUERY_TIMEOUT_SEC, since);

        // Query 3: Outliers (winrate < 30% o > 70%)
        List<String> outlierTemplates = templateStats.stream()
            .filter(t -> t.sessions() > 50)
            .filter(t -> t.winrate() < 0.30 || t.winrate() > 0.70)
            .map(TemplateStats::templateId)
            .toList();

        return new BalanceReport(templateStats, perkStats, outlierTemplates,
            templateStats.stream().mapToInt(TemplateStats::sessions).sum());
    }
}

public record BalanceReport(
    List<TemplateStats> templateStats,
    List<PerkStats> perkStats,
    List<String> outlierTemplates,
    int totalSessions
) {}
```

**Costi**:
- Frequenza: 1x/settimana (Domenica 06:00)
- Durata stimata: 10-30 secondi (3 query aggregate)
- Storage: ~10KB/report JSON
- Slack: 1 messaggio/settimana

**Ownership**: Data Analyst / Core Dev
**Status**: ✅ DEFINITIVE

---

### 60. Lock Map Cleanup - No Leak

**Decisione**: Scheduled cleanup ogni 5 min per lock scaduti, no leak su template dinamici.

```java
public class TemplateLockManager {
    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(5);

    private final ConcurrentHashMap<String, TemplateLock> locks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor;

    public TemplateLockManager() {
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "TemplateLockCleanup"));
        cleanupExecutor.scheduleAtFixedRate(
            this::cleanupExpiredLocks,
            CLEANUP_INTERVAL.toMinutes(),
            CLEANUP_INTERVAL.toMinutes(),
            TimeUnit.MINUTES
        );
    }

    public boolean tryAcquire(String templateId, UUID playerId) {
        Instant now = Instant.now();

        return locks.compute(templateId, (k, existing) -> {
            if (existing == null || existing.isExpired(now)) {
                return new TemplateLock(playerId, now.plus(LOCK_TIMEOUT));
            }
            if (existing.ownerId().equals(playerId)) {
                return new TemplateLock(playerId, now.plus(LOCK_TIMEOUT));
            }
            return existing;
        }).ownerId().equals(playerId);
    }

    public void release(String templateId, UUID playerId) {
        locks.computeIfPresent(templateId, (k, lock) ->
            lock.ownerId().equals(playerId) ? null : lock);
    }

    private void cleanupExpiredLocks() {
        Instant now = Instant.now();
        int removed = 0;

        Iterator<Map.Entry<String, TemplateLock>> it = locks.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().isExpired(now)) {
                it.remove();
                removed++;
            }
        }

        if (removed > 0) {
            LOGGER.debug("Cleaned up {} expired template locks", removed);
            telemetry.emit("template.lock.cleanup", Map.of("removed", removed));
        }
    }

    public void shutdown() {
        cleanupExecutor.shutdown();
        locks.clear();
    }

    public int activeLockCount() {
        return (int) locks.values().stream()
            .filter(l -> !l.isExpired(Instant.now()))
            .count();
    }
}

record TemplateLock(UUID ownerId, Instant expiresAt) {
    boolean isExpired(Instant now) { return now.isAfter(expiresAt); }
}
```

**Timeout 30s adeguato**: Sì - build tipico 2-10s, build grande max 20s, margine 10s per latency.

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 61. Rate Limit 4th Build - Queue with Timeout

**Decisione**: Queue con max 10 waiting, timeout 60s, reject con retry-after header.

```java
public class ArenaBuildRateLimiter {
    private static final int MAX_CONCURRENT = 3;
    private static final int MAX_QUEUED = 10;
    private static final Duration QUEUE_TIMEOUT = Duration.ofSeconds(60);

    private final Semaphore concurrentSlots = new Semaphore(MAX_CONCURRENT);
    private final AtomicInteger queuedCount = new AtomicInteger(0);

    public BuildPermit tryAcquire(UUID requestId) {
        // Try immediate acquire
        if (concurrentSlots.tryAcquire()) {
            return BuildPermit.granted(requestId);
        }

        // Check queue capacity
        if (queuedCount.get() >= MAX_QUEUED) {
            telemetry.emit("arena.build.rejected", Map.of(
                "requestId", requestId,
                "reason", "queue_full",
                "queueSize", queuedCount.get()
            ));
            return BuildPermit.rejected("Server busy, retry in 30 seconds",
                Duration.ofSeconds(30));
        }

        // Queue with timeout
        queuedCount.incrementAndGet();
        Instant queueStart = Instant.now();

        try {
            boolean acquired = concurrentSlots.tryAcquire(
                QUEUE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

            Duration waitTime = Duration.between(queueStart, Instant.now());
            telemetry.emit("arena.build.queued", Map.of(
                "requestId", requestId,
                "waitTimeMs", waitTime.toMillis(),
                "acquired", acquired
            ));

            if (acquired) {
                return BuildPermit.granted(requestId);
            } else {
                return BuildPermit.rejected("Queue timeout", Duration.ofSeconds(15));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return BuildPermit.rejected("Interrupted", Duration.ofSeconds(10));
        } finally {
            queuedCount.decrementAndGet();
        }
    }

    public void release() {
        concurrentSlots.release();
    }

    public RateLimiterStats getStats() {
        return new RateLimiterStats(
            MAX_CONCURRENT - concurrentSlots.availablePermits(),
            queuedCount.get()
        );
    }
}

public sealed interface BuildPermit {
    record Granted(UUID requestId) implements BuildPermit {}
    record Rejected(String reason, Duration retryAfter) implements BuildPermit {}

    static BuildPermit granted(UUID id) { return new Granted(id); }
    static BuildPermit rejected(String reason, Duration retry) {
        return new Rejected(reason, retry);
    }
}
```

**Comportamento 4° request**: entra in coda (se <10), attende max 60s, timeout → reject con retry-after 15s, coda piena → reject immediato con retry-after 30s.

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 62. Telemetry Contention - Wait Time & TemplateId

**Decisione**: Ogni evento `arena.build.*` include `waitTimeMs` e `templateId` per bottleneck analysis.

```java
public class ArenaBuildTelemetry {

    public void emitBuildStart(BuildContext ctx, Duration waitTime) {
        telemetry.emit("arena.build.start", Map.of(
            "requestId", ctx.requestId(),
            "templateId", ctx.templateId(),
            "templateVersion", ctx.templateVersion(),
            "waitTimeMs", waitTime.toMillis(),
            "queuePositionAtStart", ctx.queuePosition(),
            "concurrentBuilds", ctx.concurrentCount()
        ));
    }

    public void emitBuildComplete(BuildContext ctx, BuildResult result,
                                   Duration buildTime, Duration totalTime) {
        telemetry.emit("arena.build.complete", Map.of(
            "requestId", ctx.requestId(),
            "templateId", ctx.templateId(),
            "success", result.isSuccess(),
            "waitTimeMs", totalTime.minus(buildTime).toMillis(),
            "buildTimeMs", buildTime.toMillis(),
            "totalTimeMs", totalTime.toMillis(),
            "blocksPlaced", result.blocksPlaced()
        ));
    }

    public void emitContention(String templateId, int waitingCount, Duration avgWait) {
        if (waitingCount > 2 || avgWait.toMillis() > 5000) {
            telemetry.emit("arena.build.contention", Map.of(
                "templateId", templateId,
                "waitingCount", waitingCount,
                "avgWaitMs", avgWait.toMillis(),
                "severity", waitingCount > 5 ? "high" : "medium"
            ));
        }
    }
}
```

**Dashboard query bottleneck**:
```sql
SELECT
    template_id,
    COUNT(*) as builds,
    AVG(wait_time_ms) as avg_wait,
    PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY wait_time_ms) as p95_wait,
    MAX(wait_time_ms) as max_wait
FROM arena_build_events
WHERE timestamp > NOW() - INTERVAL '1 hour'
GROUP BY template_id
HAVING AVG(wait_time_ms) > 1000
ORDER BY avg_wait DESC;
```

**Metriche contention**:
| Metrica | Alert Threshold |
|---------|-----------------|
| `waitTimeMs` | >10s |
| `queuePositionAtStart` | >5 |
| `concurrentBuilds` | =3 (saturation) |
| `avgWaitMs` per template | >5s |

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 63. Prebuild Pool - Justification & Deferral

**Decisione**: Pool NON incluso ora. Mancano dati reali. Valutare dopo 2 settimane di telemetria.

```java
// DEFERRED: Prebuild pool implementation
// Rationale: No real data to justify complexity overhead

public class PrebuildPoolConfig {
    // Feature flag - default OFF until data justifies
    public static final boolean POOL_ENABLED = false;

    // Decision criteria for enabling pool:
    // - IF build_time_p95 > 5000ms AND quest_rate > 0.5/min/player
    // - THEN pool justified
    // - ELSE direct build sufficient

    // Required data before implementation:
    // 1. Player count per hour (peak vs off-peak)
    // 2. Quest start rate (quests/min per player)
    // 3. Build time distribution (p50, p95, p99)
    // 4. Player wait tolerance (UX research)
}
```

**Ownership**: Tech Lead (decision), Core Dev (implementation when justified)
**Status**: ✅ DEFERRED - Evaluate after 2 weeks telemetry

---

### 64. Pool Cleanup - Unused Definition

**Decisione**: "Unused" = no assignment per 10 min E stato READY. Guard contro chiusura appena assegnata.

```java
public class PooledArenaLifecycle {
    private static final Duration UNUSED_THRESHOLD = Duration.ofMinutes(10);

    public enum PoolState {
        BUILDING,    // In costruzione
        READY,       // Pronta, non assegnata
        RESERVED,    // Assegnata ma non ancora usata
        IN_USE,      // Attivamente usata
        CLEANUP      // In fase di pulizia
    }

    public record PooledArena(
        UUID arenaId, String templateId, PoolState state,
        Instant createdAt, Instant lastStateChange,
        @Nullable UUID reservedFor
    ) {
        public boolean isUnused(Instant now) {
            // Solo READY può essere considerata unused
            if (state != PoolState.READY) return false;
            return Duration.between(lastStateChange, now).compareTo(UNUSED_THRESHOLD) > 0;
        }

        public boolean canBeEvicted() {
            // MAI evict se RESERVED o IN_USE
            return state == PoolState.READY || state == PoolState.BUILDING;
        }
    }

    // Reserve with instant state change (prevents eviction)
    public Optional<PooledArena> reserve(String templateId, UUID playerId) {
        // Immediate state change to RESERVED prevents cleanup race
    }
}
```

**Guard**: State machine READY→RESERVED→IN_USE, double-check atomico prima di eviction.

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE (quando pool abilitato)

---

### 65. Pool Metrics - Operational Decisions & Alerts

**Decisione**: Hit/miss ratio linked a feature flag, alert su miss_rate > 30%, auto-disable > 50%.

```java
public class PoolMetrics {
    private static final double MISS_RATE_WARN = 0.20;
    private static final double MISS_RATE_CRITICAL = 0.30;
    private static final double MISS_RATE_AUTO_DISABLE = 0.50;

    // Scheduled every 5 minutes
    @Scheduled(fixedRate = 300_000)
    public void evaluatePoolEffectiveness() {
        double missRate = (double) misses.get() / (hits.get() + misses.get());

        if (missRate > MISS_RATE_AUTO_DISABLE) {
            // Auto-disable if > 50% for 3 consecutive checks
            PrebuildPoolConfig.POOL_ENABLED = false;
            telemetry.emit("arena.pool.auto_disabled", Map.of("missRate", missRate));
        } else if (missRate > MISS_RATE_CRITICAL) {
            alertRouter.send(Alert.warn("arena.pool.high_miss_rate", Map.of(
                "missRate", missRate,
                "recommendation", "Consider disabling pool"
            )));
        }
    }
}
```

**Operational decisions**: <20% keep, 20-30% WARN, 30-50% CRITICAL, >50% auto-disable.

**Ownership**: Core Dev / SRE
**Status**: ✅ DEFINITIVE

---

### 66. Migration Script - Wrapper Detection

**Decisione**: Oltre a grep, AST analysis per wrapper methods + runtime telemetry per hidden calls.

```java
// CI check: migration-audit.gradle.kts
tasks.register("migrationAudit") {
    doLast {
        // 1. Direct grep for legacy calls
        val directCalls = grepLegacyCalls()

        // 2. AST analysis for wrapper methods
        val wrapperReport = analyzeWrappers()

        // 3. Cross-reference with known call-site inventory (DD42)
        val inventory = loadCallSiteInventory()

        if (directCalls.isNotEmpty() || wrapperReport.hasHiddenCalls()) {
            throw GradleException("Legacy call-sites detected")
        }
    }
}

// Runtime telemetry for hidden calls
@Deprecated
public static ArenaResult createArena(...) {
    // Capture call stack to find hidden wrappers
    String callerChain = captureCallerChain();
    telemetry.emit("arena.legacy.call_detected", Map.of("callerChain", callerChain));
    return legacyCreateArena(...);
}
```

**Detection layers**: Grep, AST wrapper analysis, Runtime telemetry, Inventory cross-reference.

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 67. Deprecation Warning - CI vs Runtime

**Decisione**: ENTRAMBI. CI = warning (non fail M1), Runtime = log + telemetria.

```java
// Milestone plan:
// M1: Warning only (current) - -Xlint:deprecation
// M2: -Werror enabled (+4 weeks) - blocks new usage
// M3: Legacy methods removed (+8 weeks)

@Deprecated(since = "2.0", forRemoval = true)
public ArenaResult createArena(ArenaSpec spec) {
    // Rate-limited log warning
    if (shouldLogDeprecationWarning()) {
        LOGGER.warn("DEPRECATED: Use ArenaService.build() instead. Caller: {}",
            getCallerInfo());
    }

    // Always emit telemetry
    telemetry.emit("arena.deprecated.call", Map.of(
        "method", "ArenaManager.createArena",
        "caller", getCallerInfo()
    ));

    return ArenaService.build(convertSpec(spec));
}
```

**Timeline**: M1 warning only → M2 -Werror → M3 removal.

**Ownership**: Core Dev
**Status**: ✅ DEFINITIVE

---

### 68. Monitoring 48h - Anomaly Thresholds & Runbook

**Decisione**: Soglie definite, runbook con ownership chiara.

```java
public class AnomalyThresholds {
    // BUILD
    public static final Duration BUILD_TIME_P95_WARN = Duration.ofSeconds(8);
    public static final Duration BUILD_TIME_P95_CRITICAL = Duration.ofSeconds(15);
    public static final double BUILD_FAILURE_RATE_CRITICAL = 0.10;

    // ROLLBACK
    public static final double ROLLBACK_RATE_WARN = 0.02;
    public static final double ROLLBACK_RATE_CRITICAL = 0.05;

    // COMPLETION
    public static final double COMPLETION_RATE_MIN = 0.70;
}
```

**Runbook**:
| Alert | Threshold | Owner | Actions |
|-------|-----------|-------|---------|
| build.p95_exceeded | >8s | Core Dev | Check contention, MSPT, template size |
| rollback.rate_exceeded | >2% | Core Dev | Query by template, check failure reason |
| completion.low | <70% | Game Designer | Difficulty or technical issue? |

**Escalation**: 30min → Tech Lead, 10 rollbacks/hour → disable template.

**Ownership**: SRE / Core Dev
**Status**: ✅ DEFINITIVE

---

### 69. Dashboard Validation Checklist

**Decisione**: Automated validation job daily + manual spot-check weekly.

```java
public class DashboardValidationJob {
    // Run daily 02:00
    @Scheduled(cron = "0 0 2 * * *")
    public void validateDashboardData() {
        List<ValidationResult> results = new ArrayList<>();

        results.add(validateRowCounts());      // NDJSON vs DuckDB
        results.add(validateAggregates());     // Dashboard vs raw query
        results.add(validateTemporalConsistency());  // No gaps, no future
        results.add(validateReferentialIntegrity());

        if (!results.stream().allMatch(ValidationResult::passed)) {
            alertRouter.send(Alert.error("dashboard.validation.failed", ...));
        }
    }
}
```

**Manual checklist**: Data freshness, row counts, aggregates, cross-reference, visual check.

**Ownership**: Tools Dev / SRE
**Status**: ✅ DEFINITIVE

---

### 70. Security Checklist - Release Gate

**Decisione**: Release gate bloccante in CI, 7 checks obbligatori.

```yaml
# .github/workflows/release-gate.yml
jobs:
  security-checklist:
    steps:
      - name: Documentation Check
      - name: Rollback Test Check
      - name: Timeout Test Check
      - name: Autosmoke Check
      - name: Security Scan
      - name: Coverage Gate
      - name: Legacy Check

  release-gate:
    needs: security-checklist
    steps:
      - name: Block on Failure
        if: failure()
        run: exit 1
```

**7 checks**: documentation, rollback_test, timeout_test, autosmoke, security_scan, coverage, legacy_calls.

**Ownership**: Tech Lead / SRE
**Status**: ✅ DEFINITIVE

---

### 71. Template Obsolescence - Migration Without Breaking

**Decisione**: Versioned extends, graceful degradation, session-safe deprecation.

```java
public class TemplateObsolescenceHandler {

    public ArenaTemplate resolveWithFallback(String templateId) {
        ArenaTemplate template = registry.get(templateId);

        if (template == null) {
            // Check for successor
            String successor = getSuccessor(templateId);
            if (successor != null) {
                telemetry.emit("arena.template.obsolete_redirect", ...);
                return registry.get(successor);
            }
            return registry.getDefault();
        }

        // Check if extends points to obsolete parent
        if (template.extendsId() != null && registry.get(template.extendsId()) == null) {
            return flattenWithDefaults(template);
        }

        return template;
    }

    public void removeTemplate(String templateId) {
        // Check for active sessions - wait for them to end
        List<UUID> activeSessions = sessionManager.findByTemplate(templateId);
        if (!activeSessions.isEmpty()) {
            scheduleRemovalAfterSessions(templateId, activeSessions);
            return;
        }
        registry.remove(templateId);
    }
}
```

**Migration layers**: Soft deprecation, session-safe removal, extends fallback, recovery degradation.

**Ownership**: Tech Lead / Core Dev
**Status**: ✅ DEFINITIVE

---

### 72. Success Criteria - Measurable KPIs

**Decisione**: KPI definiti, dashboard tracking, go/no-go gates.

```java
public record RolloutSuccessCriteria(
    // PERFORMANCE
    Duration buildTimeP50Target,      // < 2s
    Duration buildTimeP95Target,      // < 5s
    Duration buildTimeP99Target,      // < 10s

    // RELIABILITY
    double rollbackRateMax,           // < 1%
    double buildFailureRateMax,       // < 2%
    double cleanupSuccessRateMin,     // > 99%

    // QUALITY
    double completionRateMin,         // > 75%

    // OPERATIONAL
    int p0IncidentsMax,               // 0
    int p1IncidentsMax,               // 2
    double legacyCallReductionPct     // > 90%
) {
    public static RolloutSuccessCriteria production() {
        return new RolloutSuccessCriteria(
            Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(10),
            0.01, 0.02, 0.99, 0.75, 0, 2, 0.90
        );
    }
}

public enum RolloutPhase {
    STAGING,      // Internal testing
    CANARY,       // 5% traffic - requires P0=0
    LIMITED,      // 25% traffic - requires rollback<1%, build_p95<5s
    GENERAL       // 100% traffic - all KPIs pass
}
```

**Go/No-Go Gates**: Staging→Canary (P0=0), Canary→Limited (rollback<1%), Limited→General (all pass).

**Ownership**: Tech Lead / Product
**Status**: ✅ DEFINITIVE

---

### Design Decisions Tasks

**Registry & Resolver (DD 1-6)**:
- [ ] Implementare version handling in `ArenaTemplateRegistry.load()`
- [ ] Implementare inheritance resolution on-load con caching
- [ ] Implementare tie-break rule in `PolicyResolver`
- [ ] Implementare telemetria per weight taratura
- [ ] Implementare `TemplateOverride` record e `OverrideManager`
- [ ] Implementare session cleanup hooks
- [ ] Implementare lock per player con timeout in `PolicyResolver`
- [ ] Implementare lock cleanup scheduled task

**Builder Transazionale (DD 7-10)**:
- [ ] Implementare `BuildTransaction` con tracking blocchi/entità/chunks
- [ ] Implementare `CompactBlockTracker` con `LongArrayList`
- [ ] Implementare hard cap 150k blocchi con `BuildLimitExceededException`
- [ ] Implementare NBT streaming con callback per tracking
- [ ] Implementare `ensureChunksLoaded()` con polling FULL status
- [ ] Implementare rollback completo su failure (blocchi reverse + entità + chunks)
- [ ] Implementare chiusura istanza su build failure
- [ ] Implementare `estimateBuildTimeMs()` euristica
- [ ] Implementare `estimateFromHistory()` con DuckDB P75
- [ ] Implementare feedback loop accuratezza stima

**Budget & Async (DD 11-12)**:
- [ ] Implementare `BuildBudget` con soglie WARN 80% / ERROR 100%
- [ ] Implementare `BuildTimeoutException` e `BuildLimitExceededException`
- [ ] Implementare `AsyncArenaBuilder` con tick distribution
- [ ] Implementare backpressure basata su MSPT (threshold 40ms)
- [ ] Implementare gradual recovery rate dopo backpressure
- [ ] Registrare async builder in server tick event

**Metriche & API (DD 13-15)**:
- [ ] Implementare `ArenaMetricsContext` record
- [ ] Implementare `BuildTelemetry` wrapper con context obbligatorio
- [ ] Aggiornare tutti gli eventi arena.build.* con context completo
- [ ] Implementare `ResolveOptions` record
- [ ] Implementare `prepareArenaForPartyV2()` con nuovo return type
- [ ] Deprecare `prepareArenaForParty()` legacy
- [ ] Implementare `ArenaHandle` record completo
- [ ] Migrare `QuestStartSequence.prepareArena()` a ArenaHandle
- [ ] Migrare `EnduranceQuestManager.startPreparedQuest()` a ArenaHandle
- [ ] Migrare `InstanceArenaManager.startInstanceQuestForParty()` a ArenaHandle
- [ ] Migrare `WaveManager.spawnWave()` a handle.mobSpawnPositions()
- [ ] Migrare `EndurancePlayerStateManager.teleportToArena()` a handle.primaryPlayerSpawn()
- [ ] Creare `ArenaCleanupTask` con ArenaHandle
- [ ] Aggiornare `EnduranceTelemetryService` per estrarre context da handle

**Observability & Persistence (DD 16-21)**:
- [ ] Implementare `ArenaTemplateSnapshot` record per session
- [ ] Implementare version drift detection a fine session
- [ ] Configurare log rotation (14 giorni, 500MB cap, .gz)
- [ ] Implementare `ErrorContext` record con stacktrace array
- [ ] Implementare `AlertRouter` con delivery su tutti i canali
- [ ] Implementare retry queue per canali critici (log, telemetry)
- [ ] Implementare `NdjsonWriter` async con buffer 10k
- [ ] Implementare flush policy (100 righe o 1 secondo)
- [ ] Creare tabelle DuckDB `arena_template_builds` e `arena_template_usage`
- [ ] Creare 5 indici per query dashboard
- [ ] Verificare performance query <200ms

**Identity & Recovery (DD 22-28)**:
- [ ] Implementare `ArenaIdempotencyCache` con Caffeine (5 min TTL, 1000 max)
- [ ] Integrare idempotency cache in `ArenaService.build()`
- [ ] Implementare `RetentionJob` con scheduling 04:00 daily
- [ ] Implementare `NdjsonArchiver.archiveOlderThan()`
- [ ] Implementare `DuckDBCleaner.deleteOlderThan()`
- [ ] Configurare logger separato `arena.retention` per audit
- [ ] Implementare `DuckDBRecovery.rebuildFromNdjson()`
- [ ] Aggiungere `schemaVersion` a `ArenaSessionSnapshot`
- [ ] Implementare migration chain v1→v2 in snapshot
- [ ] Implementare `InstanceName` record con validazione
- [ ] Implementare `InstanceName.sanitize()` e `generate()`
- [ ] Implementare `ArenaRecoveryResult` sealed interface
- [ ] Implementare graceful fallback per template mancante in recovery
- [ ] Implementare `PredefinedTag` enum con 16 tag predefiniti
- [ ] Implementare `PredefinedTag.autocomplete()` e `findSimilar()`
- [ ] Implementare `TagValidationResult` con suggerimenti typo
- [ ] Aggiungere warning log per tag sconosciuti

**Operations & Security (DD 29-36)**:
- [ ] Implementare `TemplateOverrideManager` con session state + capability
- [ ] Registrare `TEMPLATE_OVERRIDE_CAP` capability
- [ ] Implementare `ArenaDebugHud` con permission check
- [ ] Implementare `ArenaDebugState.isHudEnabled()` toggle
- [ ] Implementare `ArenaCommandPermissions` con 7 permission levels
- [ ] Implementare `ArenaCommandAudit.log()` per comandi mutanti
- [ ] Configurare logger separato `arena.audit`
- [ ] Implementare `AutosmokeGuard.canRun()` con triple check
- [ ] Creare `.production` marker file in prod deployment
- [ ] Implementare `AutosmokeThresholds` record con STRICT/LARGE/ASYNC
- [ ] Implementare `AutosmokeExceptions` whitelist
- [ ] Implementare `AutosmokeReportHeader.capture()`
- [ ] Aggiungere git commit/branch in build properties
- [ ] Implementare `ArenaDashboardEndpoint` con auth middleware
- [ ] Implementare rate limiter per dashboard (60 req/min)
- [ ] Implementare metrics cache con background refresh (5 min)
- [ ] Implementare `AnalyticsQueryParams` con validation
- [ ] Implementare query timeout (10 sec)
- [ ] Implementare export job asincrono per query > 30 giorni

**Cleanup & Migration (DD 37-43)**:
- [ ] Implementare `ArenaCleanupExecutor` con 4 fasi
- [ ] Implementare `CleanupResult` record con contatori e warnings
- [ ] Implementare cleanup scheduled ticks (LevelTicks access)
- [ ] Implementare `CleanupVerification` post-cleanup
- [ ] Implementare `MsptMonitor` con baseline capture
- [ ] Implementare sliding window (100 samples, 5 sec)
- [ ] Implementare `MsptSample.shouldBackpressure()` con confidence
- [ ] Implementare `BuildProgressOverlay` con rate limit 4 Hz
- [ ] Implementare `BuildProgressPacket` (28 bytes)
- [ ] Implementare client-side `BuildProgressHud`
- [ ] Creare test suite `ArenaEdgeCaseTests` con seed fisso
- [ ] Implementare test failure mid-build + rollback verify
- [ ] Implementare test chunk timeout + instance close
- [ ] Implementare test malformed template parameterized
- [ ] Implementare test 2 party concurrent
- [ ] Configurare JaCoCo coverage rules (80%/60%/50%)
- [ ] Creare `MinecraftMockExtension` per unit test
- [ ] Documentare 12 call-site inventory
- [ ] Creare branch per 6 PR migration plan
- [ ] Aggiungere CI workflow `legacy-check.yml`
- [ ] Aggiungere `@Deprecated` a `ArenaManager.createArena()`
- [ ] Implementare runtime telemetry per legacy calls

**Rollback & Spawn (DD 44-50)**:
- [ ] Creare `RollbackTestScenario` test class con 3 scenari
- [ ] Implementare staging checklist pre-deploy
- [ ] Implementare `FallbackBuildStrategy` con circuit breaker
- [ ] Implementare `CircuitBreaker` (threshold 3, window 5min, cooldown 30s)
- [ ] Implementare metriche dedicate fallback (PRIMARY_SUCCESS, FALLBACK_USED, ALL_FAILED)
- [ ] Implementare `ArenaFailureHandler` con player messages localizzabili
- [ ] Definire `PLAYER_MESSAGES` map per tutti i FailureType
- [ ] Implementare alert per failure critici
- [ ] Implementare `SpawnSlotConstraints` record con distanze default
- [ ] Implementare `SpawnSlotResolver` con LOS check
- [ ] Implementare `hasLineOfSight()` con ClipContext
- [ ] Implementare `SpawnSlotValidator` con cache al load
- [ ] Implementare `ValidationCache` per O(1) lookup runtime
- [ ] Implementare `isPositionOccupied()` check leggero
- [ ] Implementare `HeatmapCollector` con aggregazione 5x5
- [ ] Implementare flush batch ogni 5 minuti
- [ ] Implementare retention 30 giorni + aggregazione settimanale
- [ ] Implementare `MutatorBinding` record con BindingType enum
- [ ] Implementare `PolicyMutatorResolver` con REQUIRED/EXCLUDED logic
- [ ] Implementare UI sorting per SUGGESTED

**Gamification & Balance (DD 51-56)**:
- [ ] Implementare `PerkSuggestionEngine` con shuffle SUGGESTED
- [ ] Implementare A/B test 10% per perk suggestions
- [ ] Implementare weekly winrate analysis query (DuckDB)
- [ ] Creare tabella `badge_usage` per tracking version-agnostic
- [ ] Implementare `BadgeUsage` record e query
- [ ] Implementare migration script badge_awards → badge_usage
- [ ] Implementare `RewardMultiplier` record con bounds [0.5, 2.0]
- [ ] Implementare `RewardAntiExploit` (rate limit 20/hour, speed check)
- [ ] Implementare `CurrencySource` enum (16 valori)
- [ ] Implementare `CurrencyGrant` record con sourceId validation
- [ ] Implementare `ChallengeGenerator` con 5 availability checks
- [ ] Implementare `AvailabilityResult` sealed interface
- [ ] Implementare fallback challenge per ogni ChallengeType
- [ ] Implementare `LeaderboardService` con scheduled calculation
- [ ] Configurare cron job 03:00 daily per leaderboard
- [ ] Implementare Redis cache per leaderboard (TTL 25h)
- [ ] Implementare `LeaderboardType` enum con query SQL
- [ ] Implementare player rank lookup O(1) via secondary index

**Telemetry & Concurrency (DD 57-62)**:
- [ ] Implementare `TelemetryAuditJob` con scheduled daily 05:00
- [ ] Implementare query orphan events (campi mancanti)
- [ ] Implementare sub-service coverage check (12 services)
- [ ] Aggiungere CI grep per emit() senza context
- [ ] Implementare `ArenaIdentity` record con roomId()
- [ ] Implementare `SessionReconnectHandler` con sessionId separato
- [ ] Implementare `ReconnectState` tracking
- [ ] Implementare `BalanceReportJob` scheduled Dom 06:00
- [ ] Implementare query templateStats, perkStats, outliers
- [ ] Implementare Slack notifier per balance report
- [ ] Implementare `TemplateLockManager` con cleanup scheduled 5min
- [ ] Implementare `TemplateLock` record con isExpired()
- [ ] Implementare shutdown hook per cleanup executor
- [ ] Implementare `ArenaBuildRateLimiter` con Semaphore(3)
- [ ] Implementare queue max 10 con timeout 60s
- [ ] Implementare `BuildPermit` sealed interface (Granted/Rejected)
- [ ] Implementare retry-after header per rejected builds
- [ ] Implementare `ArenaBuildTelemetry` con waitTimeMs
- [ ] Implementare emitContention() per high wait detection
- [ ] Creare dashboard query bottleneck (avg_wait, p95_wait)

**Testing**:
- [ ] Unit test version handling (last-wins)
- [ ] Unit test inheritance caching
- [ ] Unit test tie-break deterministico
- [ ] Unit test rollback completo (100 blocchi, failure a metà)
- [ ] Unit test memory limit (150k blocchi)
- [ ] Unit test chunk timeout → rollback + cleanup
- [ ] Unit test budget WARN a 80%, ERROR a 100%
- [ ] Unit test async backpressure (MSPT > 40ms)
- [ ] Unit test ArenaMetricsContext in tutti gli eventi
- [ ] Unit test snapshot immutabile durante hot-reload
- [ ] Unit test AlertRouter delivery su tutti i canali
- [ ] Unit test NdjsonWriter non-blocking (buffer full → drop)
- [ ] Unit test idempotency cache (stesso requestId → stesso UUID)
- [ ] Unit test idempotency cache expiry (dopo 5 min → nuovo UUID)
- [ ] Unit test RetentionJob (archive + prune)
- [ ] Unit test DuckDB recovery from NDJSON
- [ ] Unit test snapshot migration chain (v1→v2)
- [ ] Unit test InstanceName sanitization
- [ ] Unit test InstanceName validation (reject invalid chars)
- [ ] Unit test recovery template missing → degraded result
- [ ] Unit test recovery version mismatch → warning + proceed
- [ ] Unit test PredefinedTag autocomplete
- [ ] Unit test PredefinedTag typo detection (Levenshtein ≤ 2)
- [ ] Unit test TagValidationResult suggestions
- [ ] Unit test TemplateOverrideManager session state
- [ ] Unit test TemplateOverrideManager capability persistence (relog)
- [ ] Unit test TemplateOverride expiry (TTL)
- [ ] Unit test ArenaDebugHud permission check (block senza permission)
- [ ] Unit test ArenaDebugHud toggle (default OFF)
- [ ] Unit test ArenaCommandAudit log format
- [ ] Unit test AutosmokeGuard ENV check (block senza DEVMOD_ENV)
- [ ] Unit test AutosmokeGuard feature flag check
- [ ] Unit test AutosmokeGuard .production marker check
- [ ] Unit test AutosmokeThresholds.forTemplate() selection
- [ ] Unit test AutosmokeExceptions whitelist lookup
- [ ] Unit test AutosmokeReportHeader capture (tutti i campi)
- [ ] Unit test ArenaDashboardEndpoint auth (401 senza token)
- [ ] Unit test ArenaDashboardEndpoint rate limit (429)
- [ ] Unit test AnalyticsQueryParams validation (range > 30 giorni)
- [ ] Unit test AnalyticsQueryParams validation (page size > 1000)
- [ ] Unit test AnalyticsService timeout (10 sec)
- [ ] Unit test CleanupExecutor 4 fasi ordine corretto
- [ ] Unit test CleanupResult.isComplete() (warnings empty)
- [ ] Unit test cleanup Container.clearContent() pre-remove
- [ ] Unit test MsptMonitor baseline capture (mediana)
- [ ] Unit test MsptMonitor confidence score (alta varianza → bassa)
- [ ] Unit test MsptSample.shouldBackpressure() threshold
- [ ] Unit test BuildProgressOverlay rate limit (skip < 250ms)
- [ ] Unit test BuildProgressOverlay delta skip (< 1%)
- [ ] Unit test edge case failure mid-build rollback
- [ ] Unit test edge case chunk timeout → instance closed
- [ ] Unit test edge case malformed template rejection
- [ ] Unit test edge case 2 party no interference
- [ ] Unit test edge case same player blocked
- [ ] Unit test JaCoCo rules enforcement
- [ ] Unit test CI legacy-check grep patterns
- [ ] Unit test @Deprecated runtime telemetry emit
- [ ] Integration test concurrency (2 request parallele stesso player)
- [ ] Integration test legacy API backward compat
- [ ] Integration test dashboard cache background refresh
- [ ] Integration test cleanup completo (entity+blockEntity+ticks+blocks)
- [ ] Benchmark stima vs actual build time
- [ ] Benchmark query DuckDB <200ms
- [ ] Benchmark cleanup 10k blocks duration
- [ ] Staging test RollbackTestScenario 3 scenari pass
- [ ] Unit test FallbackBuildStrategy max 1 retry
- [ ] Unit test CircuitBreaker open dopo 3 failures
- [ ] Unit test CircuitBreaker cooldown 30 sec
- [ ] Unit test ArenaFailureHandler player message no tech details
- [ ] Unit test ArenaFailureHandler stack trace in log
- [ ] Unit test SpawnSlotConstraints distance validation
- [ ] Unit test SpawnSlotResolver LOS check
- [ ] Unit test SpawnSlotResolver forbidden zone rejection
- [ ] Unit test SpawnSlotValidator O(1) runtime lookup
- [ ] Unit test SpawnSlotValidator position occupied check
- [ ] Unit test HeatmapCollector 5x5 cell aggregation
- [ ] Unit test HeatmapCollector hourly bucket
- [ ] Unit test HeatmapCollector no player ID
- [ ] Unit test MutatorBinding SUGGESTED (soft, selectable)
- [ ] Unit test MutatorBinding EXCLUDED (hard block)
- [ ] Unit test MutatorBinding REQUIRED (always on)
- [ ] Unit test PolicyMutatorResolver REQUIRED auto-add
- [ ] Integration test fallback chain end-to-end
- [ ] Integration test staging rollback scenario
- [ ] Unit test PerkSuggestionEngine shuffle (no position bias)
- [ ] Unit test PerkSuggestionEngine A/B test deterministic (same UUID → same group)
- [ ] Unit test PerkSuggestionEngine A/B test ratio (10%)
- [ ] Unit test BadgeUsage count query (version-agnostic)
- [ ] Unit test BadgeUsage migration script
- [ ] Unit test RewardMultiplier fromWeight calculation
- [ ] Unit test RewardMultiplier bounds clamping [0.5, 2.0]
- [ ] Unit test RewardAntiExploit rate limit (21st reward blocked)
- [ ] Unit test RewardAntiExploit speed check (telemetry emitted)
- [ ] Unit test CurrencySource enum cardinality (16 values)
- [ ] Unit test CurrencyGrant sourceId validation (max 64 chars)
- [ ] Unit test ChallengeGenerator level check
- [ ] Unit test ChallengeGenerator prerequisite check
- [ ] Unit test ChallengeGenerator cooldown check
- [ ] Unit test ChallengeGenerator template availability check
- [ ] Unit test ChallengeGenerator time window check
- [ ] Unit test ChallengeGenerator fallback (no available → generic)
- [ ] Unit test AvailabilityResult sealed interface
- [ ] Unit test LeaderboardService scheduled calculation
- [ ] Unit test LeaderboardService Redis cache TTL (25h)
- [ ] Unit test LeaderboardService cache miss → empty list
- [ ] Unit test LeaderboardService pagination
- [ ] Unit test LeaderboardService player rank lookup O(1)
- [ ] Unit test LeaderboardType query validation (all 3 types)
- [ ] Integration test leaderboard end-to-end (calculate + cache + read)
- [ ] Integration test challenge generation pipeline
- [ ] Unit test TelemetryAuditJob orphan detection
- [ ] Unit test TelemetryAuditJob sub-service coverage (12 services)
- [ ] Unit test TelemetryAuditJob alert on missing fields
- [ ] Unit test ArenaIdentity roomId() immutability
- [ ] Unit test SessionReconnectHandler sessionId uniqueness
- [ ] Unit test SessionReconnectHandler reconnectCount increment
- [ ] Unit test BalanceReportJob query timeout (30s)
- [ ] Unit test BalanceReportJob outlier detection (<30%, >70%)
- [ ] Unit test BalanceReportJob JSON persistence
- [ ] Unit test TemplateLockManager acquire/release
- [ ] Unit test TemplateLockManager expiry (30s)
- [ ] Unit test TemplateLockManager cleanup job (5min)
- [ ] Unit test TemplateLockManager no leak (dynamic templates)
- [ ] Unit test ArenaBuildRateLimiter concurrent limit (3)
- [ ] Unit test ArenaBuildRateLimiter queue limit (10)
- [ ] Unit test ArenaBuildRateLimiter timeout (60s)
- [ ] Unit test ArenaBuildRateLimiter reject retry-after
- [ ] Unit test BuildPermit sealed interface
- [ ] Unit test ArenaBuildTelemetry waitTimeMs included
- [ ] Unit test ArenaBuildTelemetry contention detection (>2 waiting)
- [ ] Unit test ArenaBuildTelemetry severity levels
- [ ] Integration test telemetry audit end-to-end
- [ ] Integration test rate limiter under load (13 concurrent requests)
- [ ] Integration test lock cleanup after server restart

---

## Architettura a Livelli

| Livello | Nome | Responsabilità | File | Owner |
|---------|------|----------------|------|-------|
| **L1** | `ArenaTemplate` | Layout fisico: size, palette, spawnSlots, hazards, limits | `arena_templates/*.json` | Level Designer / Tech Lead |
| **L2** | `ArenaPolicy` | Routing, perk/mutator bindings, reward modifiers | `arena_policies/*.json` | Game Designer / Core Dev |
| **L3** | `ArenaGamification` | Badge, challenge, leaderboard rules | Codice GamificationManager | Tools Dev |

### Vantaggi separazione:
- **Riuso**: stesso layout con policy diverse (es. `boss_ring_80` ranked vs casual)
- **Ownership chiara**: Layout ≠ Gameplay ≠ Meta
- **Testing isolato**: build senza caricare reward/gamification
- **Varianti facili**: `boss_ring_80_hardcore.policy.json` eredita layout, cambia reward

---

## Fase 0 – Spec & Inventory (2 giorni)

### L1 - ArenaTemplate Schema (Layout fisico) - 100% Autocontenuto

Lo schema deve contenere **tutti** i parametri necessari per costruire l'arena. Zero valori hardcoded nel builder.

#### Metadati
- `id`: string, identificatore unico
- `version`: int, per tracking breaking changes
- `extends`: string|null, ID template parent per inheritance
- `schemaVersion`: string (es. "1.0.0"), versione dello schema
- `breakingChange`: bool, true se incompatibile con versioni precedenti

#### Geometria e Origine
- `origin`: oggetto che definisce il punto (0,0,0) del template
  ```json
  "origin": {
    "mode": "center" | "corner_nw" | "corner_sw",
    "x": 0, "y": 64, "z": 0
  }
  ```
- `size`: int, dimensione XZ (quadrato) - es. 64 → arena 64x64
- `sizeX`, `sizeZ`: int (opzionali), per arene rettangolari (override di size)
  - `maxDimension` per compat/check coverage = `max(sizeX, sizeZ)` (se mancanti, usa `size`)
- Non esiste un campo `height` top-level: i bounds verticali si derivano da `floor.y`, `walls.height`, `ceiling.y`.

#### Floor (Pavimento)
- `floor`: oggetto completo per il pavimento
  ```json
  "floor": {
    "y": 64,
    "thickness": 1,
    "material": "minecraft:stone_bricks",
    "pattern": "solid" | "checkerboard" | "border",
    "borderMaterial": "minecraft:polished_andesite",
    "borderWidth": 2
  }
  ```

#### Walls (Pareti)
- `walls`: oggetto per le pareti perimetrali
  ```json
  "walls": {
    "enabled": true,
    "material": "minecraft:barrier",
    "height": 10,
    "thickness": 1,
    "startY": 64,
    "style": "solid" | "glass" | "fence"
  }
  ```

#### Ceiling (Soffitto)
- `ceiling`: oggetto per il soffitto
  ```json
  "ceiling": {
    "enabled": true,
    "material": "minecraft:barrier",
    "y": 74,
    "thickness": 1
  }
  ```

#### Underfloor (Sotto il pavimento)
- `underfloor`: cosa c'è sotto il floor
  ```json
  "underfloor": {
    "material": "minecraft:bedrock" | "minecraft:void_air" | "same_as_floor",
    "depth": 3
  }
  ```
- `same_as_floor` copia solo il materiale del floor (non pattern/border), con spessore fisso 1; `depth` resta esplicito e clamp se sotto al minY del mondo.

#### Palette (materiali aggiuntivi)
- `palette`: materiali per accent e decorazioni
  ```json
"palette": {
  "accent": "minecraft:polished_andesite",
  "highlight": "minecraft:glowstone",
  "hazardBorder": "minecraft:magma_block"
}
```
- Il builder deve usare i materiali della palette per pavimento/muri/bordi/hazard (nessun fallback hardcoded).

#### Biome e Lighting
- `biome`: configurazione bioma
  ```json
"biome": {
  "id": "minecraft:plains",
  "applyTo": "bounds" | "chunks"
}
```
- `applyTo=bounds` applica il bioma solo dentro i bounds AABB; `chunks` setta tutti i chunk che intersecano l'arena (attenzione ai margini parziali).
- `lighting`: configurazione luce
  ```json
"lighting": {
  "skyLight": 15,
  "blockLight": 10,
  "ambientLight": true,
  "lightSources": [
    { "pos": [0, 70, 0], "block": "minecraft:glowstone" }
  ]
}
```
- Il builder deve piazzare i `lightSources` e garantire i livelli target (blockLight max 15); non è solo descrittivo.

#### Spawn Slots
- `spawnSlots`: array di posizioni spawn con validazione esplicita
  ```json
  "spawnSlots": [
    {
      "pos": [0, 1, 0],
      "yMode": "relativeToFloor" | "absolute",
      "tags": ["center", "player"],
      "validation": {
        "requireSolidBelow": true,
        "requireAirAbove": 2,
        "requireClearRadius": 1
      }
    }
  ]
  ```
- `playerSpawnOffset`: offset aggiuntivo per spawn player `{x, y, z}`
- `mobSpawnStrategy`: `"distributed"` | `"clustered"` | `"corners"` | `"ring"`
  - Mapping operativo: `distributed`=grid/fallback, `clustered`=vicino al centro con jitter, `corners`=slot tag `corner` o 4 angoli, `ring`=posizioni su anello r>=radius; fallback a distributed se impossibile.
- Task spawnSlots:
  - Validare che tutte le posizioni (dopo yMode) siano dentro i bounds AABB dell'arena.
  - Dedup posizioni (x,z,y) → ERROR su duplicati.
  - Applicare forbiddenZones: slot che intersecano zone vietate → ERROR (no spawn).

#### Forbidden Zones
- `forbiddenZones`: aree dove non spawnare
  ```json
  "forbiddenZones": [
    {
      "min": [-5, 0, -5],
      "max": [5, 10, 5],
      "yMode": "relativeToFloor" | "absolute",
      "reason": "player_safe_zone"
    }
  ]
  ```
- Applicare `yMode` prima della validazione; hard fail se la zona esce dai bounds calcolati da origin + sizeX/sizeZ + ceiling, oppure clamp con WARN+telemetria se il clamp è consentito.

#### Hazards
- `hazards`: elementi di pericolo
  ```json
  "hazards": [
    {
      "type": "lava_ring",
      "params": { "innerRadius": 35, "outerRadius": 38 },
      "y": 64,
      "yMode": "absolute"
    },
    {
      "type": "void_pit",
      "params": { "center": [10, 0, 10], "radius": 3 }
    }
  ]
  ```
- Task hazard support:
  - Whitelist tipi supportati iniziali (es. `lava_ring`, `void_pit`, `spike_grid`), reject altri.
  - Validare parametri per range (raggio >0, inner<outer, center/bounds dentro arena) e y/yMode.
  - Applicare limite quantitativo: max 50 hazard per template → ERROR se superato.
  - Telemetria `arena.hazard.rejected` con motivo.

#### Hazards Validation Strategy

##### Supported Hazard Types (Whitelist)

| Tipo | Parametri Required | Parametri Optional | Limite per Arena |
|------|-------------------|-------------------|------------------|
| `lava_ring` | `innerRadius`, `outerRadius` | `material` (default: lava) | max 3 |
| `lava_pool` | `center`, `radius` | `depth` (default: 1) | max 5 |
| `void_pit` | `center`, `radius` | `depth` (default: 10) | max 3 |
| `spike_trap` | `positions[]` | `damage`, `cooldownTicks` | max 20 positions |
| `fire_zone` | `min`, `max` | `intensity` (0.0-1.0) | max 5 |
| `magma_floor` | `coverage` (0.0-1.0) | `pattern` (random/grid) | max 1 |
| `falling_blocks` | `area`, `blockType` | `interval`, `count` | max 2 |
| `custom` | `builderId` | any | max 2 (requires registered builder) |

##### Parameter Validation Rules

| Parametro | Tipo | Range Consentito | Azione su violazione |
|-----------|------|------------------|----------------------|
| `innerRadius` | int | 1 - (size/2 - 2) | ERROR se fuori range |
| `outerRadius` | int | innerRadius+1 - size/2 | ERROR se ≤ innerRadius |
| `radius` | int | 1 - size/4 | CLAMP + WARN se > size/4 |
| `center` | int[3] | within bounds | ERROR se fuori bounds |
| `depth` | int | 1 - 64 | CLAMP + WARN |
| `positions[]` | array | max 20 elements | ERROR se > 20 |
| `coverage` | float | 0.0 - 0.5 | CLAMP + WARN se > 0.5 |
| `damage` | float | 0.0 - 20.0 | CLAMP + WARN |
| `interval` | int | 20 - 600 ticks | CLAMP + WARN |

##### Hazard Bounds & Overlap Check

```java
public HazardValidation validateHazard(Hazard hazard, ArenaTemplate template) {
    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();

    // 1. Type whitelist
    if (!SUPPORTED_HAZARD_TYPES.contains(hazard.type())) {
        errors.add("Unknown hazard type: '%s'".formatted(hazard.type()));
        return HazardValidation.error(errors);
    }

    // 2. Count limit per type
    int typeCount = countHazardsOfType(template, hazard.type());
    if (typeCount >= HAZARD_TYPE_LIMITS.get(hazard.type())) {
        errors.add("Too many '%s' hazards".formatted(hazard.type()));
    }

    // 3. Bounds check
    AABB hazardBounds = computeHazardBounds(hazard);
    if (!computeArenaBounds(template).contains(hazardBounds)) {
        errors.add("Hazard '%s' extends outside arena bounds".formatted(hazard.type()));
    }

    // 4. Overlap check con spawnSlots
    for (SpawnSlot slot : template.spawnSlots()) {
        if (hazardBounds.contains(slot.absolutePos())) {
            errors.add("Hazard overlaps with spawn slot '%s'".formatted(slot.tags()));
        }
    }

    // 5. Total coverage check (max 30%)
    double coverage = computeTotalHazardCoverage(template);
    if (coverage > 0.3) {
        warnings.add("Total hazard coverage %.1f%% > 30%%".formatted(coverage * 100));
    }

    return errors.isEmpty() ? HazardValidation.success(warnings) : HazardValidation.error(errors);
}
```

##### Hazard Validation Tasks

- [ ] Implementare `HazardType` enum con whitelist tipi supportati
- [ ] Implementare `HAZARD_TYPE_LIMITS` map con limiti per tipo
- [ ] Implementare `HazardValidator.validate(hazard, template)`:
  - [ ] Type whitelist check
  - [ ] Count limit per type
  - [ ] Bounds check (within arena)
  - [ ] Parameter range validation per tipo
  - [ ] Overlap check con spawnSlots
  - [ ] Total coverage check (max 30%)
- [ ] Implementare parameter validation per ogni tipo:
  - [ ] `lava_ring`: innerRadius < outerRadius, both within size/2
  - [ ] `void_pit`: center within bounds, radius ≤ size/4
  - [ ] `spike_trap`: max 20 positions, all within bounds
  - [ ] `fire_zone`: min < max, both within bounds
  - [ ] `magma_floor`: coverage ≤ 0.5
  - [ ] `falling_blocks`: area within bounds
  - [ ] `custom`: builderId must be registered
- [ ] Telemetria hazard:
  - `arena.hazard.validation_failed` con `{templateId, hazardType, reason}`
  - `arena.hazard.high_coverage` se coverage > 25%
- [ ] Unit test hazard validation:
  - [ ] Unknown type rejection
  - [ ] Type limit exceeded
  - [ ] Out of bounds rejection
  - [ ] Parameter range violations
  - [ ] SpawnSlot overlap rejection
  - [ ] Coverage warning threshold
- [ ] Owner: Core Dev · Durata: 0.25g · Done: tutti i tipi validati

---

#### Spawn Slots Validation Strategy

##### Validation Rules

| Check | Regola | Severity | Azione |
|-------|--------|----------|--------|
| **Within bounds** | pos inside arena bounds | ERROR | Blocco load |
| **No duplicates** | no stesse coordinate | ERROR | Blocco load |
| **Not in forbiddenZones** | pos not in any forbidden zone | ERROR | Blocco load |
| **Not in hazards** | pos not inside any hazard | ERROR | Blocco load |
| **Min distance** | ≥2 blocks tra slot | WARN | Log warning |
| **Required tags** | almeno 1 player, 1 mob/boss | ERROR | Blocco load |
| **Validation rules** | requireSolidBelow, requireAirAbove | ERROR | Blocco build |

##### SpawnSlot Validator

```java
public SpawnSlotValidation validateSpawnSlots(ArenaTemplate template) {
    List<SpawnSlot> slots = template.spawnSlots();
    AABB bounds = computeArenaBounds(template);
    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    Set<String> seenPositions = new HashSet<>();

    for (int i = 0; i < slots.size(); i++) {
        SpawnSlot slot = slots.get(i);
        Vec3i absPos = computeAbsolutePos(slot, template);
        String posKey = "%d,%d,%d".formatted(absPos.getX(), absPos.getY(), absPos.getZ());

        // 1. Within bounds
        if (!bounds.contains(absPos)) {
            errors.add("SpawnSlot[%d] at %s outside bounds".formatted(i, posKey));
        }
        // 2. Duplicates
        if (!seenPositions.add(posKey)) {
            errors.add("SpawnSlot[%d] at %s is duplicate".formatted(i, posKey));
        }
        // 3. ForbiddenZone check
        for (ForbiddenZone zone : template.forbiddenZones()) {
            if (computeZoneAABB(zone, template).contains(absPos)) {
                errors.add("SpawnSlot[%d] in forbiddenZone '%s'".formatted(i, zone.reason()));
            }
        }
        // 4. Hazard check
        for (Hazard hazard : template.hazards()) {
            if (computeHazardAABB(hazard, template).contains(absPos)) {
                errors.add("SpawnSlot[%d] in hazard '%s'".formatted(i, hazard.type()));
            }
        }
        // 5. Min distance (warn)
        for (int j = i + 1; j < slots.size(); j++) {
            Vec3i other = computeAbsolutePos(slots.get(j), template);
            if (absPos.distanceTo(other) < 2) {
                warnings.add("SpawnSlot[%d] and [%d] too close".formatted(i, j));
            }
        }
    }

    // Required tags
    if (slots.stream().noneMatch(s -> s.tags().contains("player"))) {
        errors.add("No 'player' tagged spawn slot");
    }
    if (slots.stream().noneMatch(s -> s.tags().contains("mob") || s.tags().contains("boss"))) {
        errors.add("No 'mob'/'boss' tagged spawn slot");
    }

    return new SpawnSlotValidation(errors.isEmpty(), errors, warnings, slots.size());
}
```

##### Runtime Validation (during build)

```java
public boolean validateSlotAtRuntime(SpawnSlot slot, ServerLevel level, BlockPos absPos) {
    var rules = slot.validation();
    // 1. Solid below
    if (rules.requireSolidBelow() && !level.getBlockState(absPos.below()).isSolidRender(level, absPos.below())) {
        telemetry.emit("arena.spawnslot.validation_failed", Map.of("reason", "not_solid_below"));
        return false;
    }
    // 2. Air above
    for (int y = 0; y < rules.requireAirAbove(); y++) {
        if (!level.getBlockState(absPos.above(y)).isAir()) {
            telemetry.emit("arena.spawnslot.validation_failed", Map.of("reason", "not_air_above"));
            return false;
        }
    }
    // 3. Clear radius
    int r = rules.requireClearRadius();
    if (r > 0) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (level.getBlockState(absPos.offset(dx, 0, dz)).isSolidRender(level, absPos.offset(dx, 0, dz))) {
                    return false;
                }
            }
        }
    }
    return true;
}
```

##### Spawn Slots Validation Tasks

- [ ] Implementare `SpawnSlotValidator.validate(template)`:
  - [ ] Within bounds check
  - [ ] Duplicate detection
  - [ ] ForbiddenZone intersection
  - [ ] Hazard intersection
  - [ ] Min distance warning
  - [ ] Required tags check
- [ ] Implementare `validateAtRuntime(slot, level, pos)`:
  - [ ] requireSolidBelow
  - [ ] requireAirAbove
  - [ ] requireClearRadius
- [ ] Integrare in loader/builder:
  - [ ] Reject template se errors > 0
  - [ ] Abort build se tutti player slots fail
- [ ] Telemetria spawnslot:
  - `arena.spawnslot.validation_failed`
  - `arena.spawnslot.out_of_bounds`
  - `arena.spawnslot.in_forbidden_zone`
- [ ] Unit test:
  - [ ] Out of bounds, duplicates, forbidden zone, hazard intersection
  - [ ] Missing tags, runtime checks
- [ ] Owner: Core Dev · Durata: 0.25g · Done: validation completa

---

#### Golden Reference Template (default_flat_64)

##### Expected Output (Deterministic)

| Metrica | Valore Atteso | Tolleranza |
|---------|---------------|------------|
| **Total Blocks** | 22,880 | ±0 |
| **Floor Blocks** | 4,096 (64×64×1) | ±0 |
| **Wall Blocks** | 2,400 (perimeter×10) | ±0 |
| **Ceiling Blocks** | 4,096 (64×64×1) | ±0 |
| **Underfloor Blocks** | 12,288 (64×64×3) | ±0 |
| **Build Time** | <2000ms | ±500ms |
| **Spawn Slots** | 4 | ±0 |

##### Expected Bounds & Positions

```java
public static final GoldenBounds DEFAULT_FLAT_64 = new GoldenBounds(
    new AABB(-32, 64, -32, 32, 74, 32),  // arena bounds
    new AABB(-32, 64, -32, 32, 64, 32),  // floor
    new AABB(-32, 74, -32, 32, 74, 32),  // ceiling
    List.of(
        new BlockPos(0, 65, 0),    // player
        new BlockPos(10, 65, 0),   // melee mob
        new BlockPos(-10, 65, 0),  // ranged mob
        new BlockPos(20, 65, 20)   // corner mob
    )
);
```

##### Golden Reference Test

```java
@Test void testDefaultFlat64GoldenReference() {
    ArenaTemplate template = loader.load("default_flat_64");

    // Bounds match
    assertEquals(GOLDEN.arenaBounds(), builder.computeBounds(template));

    // Block count match
    BuildDryRun dryRun = builder.dryRun(template);
    assertEquals(GOLDEN.totalBlocks(), dryRun.totalBlocks());

    // Spawn slots match
    List<BlockPos> slots = builder.computeAbsoluteSpawnPositions(template);
    assertEquals(GOLDEN.spawnSlots(), slots);

    // Build succeeds
    BuildResult result = builder.build(template, testLevel, BlockPos.ZERO);
    assertTrue(result.success());
    assertTrue(result.buildTimeMs() < 2500);

    // Cleanup clean
    CleanupResult cleanup = builder.cleanup(result.arenaId());
    assertEquals(0, cleanup.entitiesRemaining());
}
```

##### Block Count Formula

```
Floor:      64 × 64 × 1 = 4,096
Walls:      (64×4 - 4corners) × 10 = 252 × 10 = 2,520
Ceiling:    64 × 64 × 1 = 4,096
Underfloor: 64 × 64 × 3 = 12,288
Total:      ~23,000 (exact depends on corner handling)
```

##### Golden Reference Tasks

- [ ] Creare `GoldenBounds` record per `default_flat_64`
- [ ] Implementare `BuildDryRun` (block count senza piazzare)
- [ ] Determinare formula esatta wall block count
- [ ] Creare `GoldenReferenceTest`:
  - [ ] Bounds match
  - [ ] Block count per component
  - [ ] Spawn slot positions
  - [ ] Build time threshold
  - [ ] Cleanup verification
- [ ] Aggiungere golden reference per `boss_ring_80`
- [ ] Autosmoke include golden reference
- [ ] CI: fail build se mismatch
- [ ] Telemetria: `arena.golden_reference.passed/mismatch`
- [ ] Owner: QA + Core Dev · Durata: 0.25g · Done: golden test passa

---

#### Structure NBT (opzionale)
- `structureNbt`: per layout complessi da file NBT
  ```json
  "structureNbt": {
    "path": "devmod:structures/boss_arena",
    "offset": { "x": 0, "y": 0, "z": 0 },
    "rotation": "none" | "clockwise_90" | "180" | "counterclockwise_90",
    "mirror": "none" | "front_back" | "left_right",
    "seedPolicy": "fixed" | "perRun",
    "ignoreAir": true
  }
  ```
  - **Sicurezza NBT**:
    - Whitelist path: solo namespace/package `devmod:structures/*` (niente path relativi/dal filesystem).
    - Checksum SHA-256 obbligatorio in manifest NBT; il loader rifiuta se mismatch.
    - Dimensione massima file NBT: 512 KB (configurabile) per evitare OOM.
    - Disabilitare I/O runtime arbitrario: niente lettura file esterni, niente network; solo risorse registrate nel resource pack/mod.

#### Structure NBT Security Policy

| Controllo | Regola | Azione su violazione |
|-----------|--------|----------------------|
| **Path whitelist** | Solo `devmod:structures/*` o namespace registrati | ERROR, blocco load |
| **Path traversal** | No `..`, `./`, path assoluti, backslash | ERROR, blocco load |
| **Checksum SHA-256** | Hash in manifest deve matchare file | ERROR, blocco load |
| **Max file size** | 512 KB default (configurabile) | ERROR, blocco load |
| **Max blocks** | 100k blocchi per struttura | WARN >50k, ERROR >100k |
| **Max entities** | 50 entità embedded | ERROR se superato |
| **I/O runtime** | Solo ResourceLocation, no File/URL | ERROR, blocco load |
| **Namespace** | Solo namespace in whitelist config | ERROR se non registrato |

#### NBT Manifest Schema
```json
{
  "structures": {
    "devmod:structures/boss_arena": {
      "sha256": "a1b2c3d4e5f6...",
      "sizeBytes": 45678,
      "blockCount": 12000,
      "entityCount": 0
    }
  },
  "allowedNamespaces": ["devmod", "custompack"],
  "maxFileSizeBytes": 524288,
  "maxBlockCount": 100000,
  "maxEntityCount": 50
}
```

#### NBT Loader Validation
```java
public StructureLoadResult load(String path, StructureManifest manifest) {
    // 1. Path validation (no traversal, valid ResourceLocation)
    if (!isValidPath(path)) {
        return error(INVALID_PATH, "Invalid path: " + path);
    }

    // 2. Namespace whitelist check
    String namespace = extractNamespace(path);
    if (!manifest.allowedNamespaces().contains(namespace)) {
        return error(UNAUTHORIZED_NAMESPACE, "Namespace not allowed: " + namespace);
    }

    // 3. Manifest entry exists
    StructureEntry entry = manifest.structures().get(path);
    if (entry == null) {
        return error(NOT_IN_MANIFEST, "Not in manifest: " + path);
    }

    // 4. Size pre-check
    if (entry.sizeBytes() > manifest.maxFileSizeBytes()) {
        return error(FILE_TOO_LARGE, "File too large: " + entry.sizeBytes());
    }

    // 5. Load via ResourceLocation (sandboxed)
    byte[] data = loadFromResourcePack(path);

    // 6. Checksum verification
    if (!sha256(data).equals(entry.sha256())) {
        telemetry.emit("arena.structure.checksum_mismatch", path);
        return error(CHECKSUM_MISMATCH, "Hash mismatch");
    }

    // 7. Content limits
    CompoundTag nbt = parseNbt(data);
    if (countBlocks(nbt) > manifest.maxBlockCount()) {
        return error(TOO_MANY_BLOCKS, "Block limit exceeded");
    }
    if (countEntities(nbt) > manifest.maxEntityCount()) {
        return error(TOO_MANY_ENTITIES, "Entity limit exceeded");
    }

    return success(nbt);
}

private boolean isValidPath(String path) {
    return !path.contains("..") && !path.contains("./") && !path.contains("\\")
        && !path.startsWith("/") && !path.contains("://")
        && path.matches("^[a-z0-9_.-]+:[a-z0-9_/.-]+$");
}
```

#### NBT Security Tasks
- [ ] Implementare `StructureNbtLoader` con validazione completa
- [ ] Implementare `StructureManifest` parser
- [ ] Implementare path validation:
  - [ ] Regex ResourceLocation format
  - [ ] Path traversal check (`..`, `./`, `\`)
  - [ ] Absolute path check (`/`, `://`)
- [ ] Implementare namespace whitelist:
  - [ ] Default: `["devmod"]`
  - [ ] Configurabile in `ArenaTemplateConfig.allowedStructureNamespaces`
- [ ] Implementare checksum verification:
  - [ ] SHA-256 computation
  - [ ] Manifest comparison
  - [ ] Telemetria `arena.structure.checksum_mismatch`
- [ ] Implementare size limits:
  - [ ] Pre-load check da manifest
  - [ ] Configurabile: `maxStructureFileSizeBytes` (default 512KB)
- [ ] Implementare content limits:
  - [ ] Block count da NBT
  - [ ] Entity count da NBT
  - [ ] Configurabile: `maxStructureBlocks`, `maxStructureEntities`
- [ ] Sandbox I/O:
  - [ ] Solo `ResourceLocation` API
  - [ ] No `File`, `FileInputStream`, `URL`, `Socket`
- [ ] Manifest generation tool:
  - [ ] `/devmod structure manifest generate`
  - [ ] Scansiona structures, calcola SHA-256
  - [ ] Output `structures_manifest.json`
- [ ] Telemetria:
  - `arena.structure.loaded` con `{path, blockCount, loadMs}`
  - `arena.structure.rejected` con `{path, reason}`
- [ ] Unit test security:
  - [ ] Path traversal rejection
  - [ ] Unauthorized namespace rejection
  - [ ] Checksum mismatch rejection
  - [ ] Oversize file rejection
  - [ ] Content limits rejection
  - [ ] Valid structure success
- [ ] Owner: Core Dev · Durata: 0.5g · Done: loader sicuro, manifest, test passati

---

#### Environment Effects (opzionale)
- `environment`: effetti ambientali
  ```json
  "environment": {
    "particles": [
      { "type": "minecraft:smoke", "rate": 0.1, "area": "bounds" }
    ],
    "ambientSound": "minecraft:ambient.cave",
    "fog": { "enabled": false, "density": 0.0 }
  }
  ```

#### Compatibility e Instance
- `compat`: requisiti player
  ```json
  "compat": { "minPlayers": 1, "maxPlayers": 4 }
  ```
- `instanceSettings`: configurazione dimensione
  ```json
  "instanceSettings": {
    "chunkRadius": 2,
    "tickDistance": 4,
    "keepLoaded": true
  }
  ```
  - Compatibilità con Instance System:
    - I valori sono validati contro i limiti del server/InstanceManager; se sopra al massimo consentito vengono **clampati** e generano WARN + telemetria `arena.instance.clamped`.
    - Se dopo il clamp `chunkRadius` è insufficiente per coprire l'arena (`size/16 + buffer`), la build viene rifiutata (ERROR) con messaggio chiaro.
    - Se il server forza limiti più bassi a runtime, l'istanza usa i limiti effettivi del server; overlay/log mostrano i valori effettivi.

#### Instance Settings Compatibility Strategy

| Parametro | Limite Server Tipico | Comportamento se Superato |
|-----------|---------------------|---------------------------|
| `chunkRadius` | 2-8 (config dependent) | CLAMP + WARN |
| `tickDistance` | 1-10 (config dependent) | CLAMP + WARN |
| `keepLoaded` | true/false | Sempre rispettato |

#### Validation Flow

```
Template.instanceSettings
        │
        ▼
┌───────────────────────────────────────┐
│ 1. Get server limits from InstanceManager │
│    serverMaxChunkRadius = config.maxChunkRadius │
│    serverMaxTickDistance = config.maxTickDistance │
└───────────────────────────────────────┘
        │
        ▼
┌───────────────────────────────────────┐
│ 2. Clamp values to server limits      │
│    effectiveChunkRadius = min(template.chunkRadius, serverMax) │
│    effectiveTickDistance = min(template.tickDistance, serverMax) │
└───────────────────────────────────────┘
        │
        ▼
┌───────────────────────────────────────┐
│ 3. Validate arena coverage            │
│    maxDim = max(sizeX,sizeZ,size)     │
│    requiredChunks = ceil(maxDim / 16) + 1 │
│    if (effectiveChunkRadius < requiredChunks) → ERROR │
└───────────────────────────────────────┘
        │
        ▼
┌───────────────────────────────────────┐
│ 4. Apply & report                     │
│    if (clamped) → WARN + telemetry    │
│    use effectiveValues for instance   │
└───────────────────────────────────────┘
```

#### Instance Compatibility Checker

```java
public record InstanceSettingsValidation(
    boolean valid,
    int effectiveChunkRadius,
    int effectiveTickDistance,
    List<String> warnings,
    @Nullable String error
) {
    public static InstanceSettingsValidation success(int chunkRadius, int tickDistance, List<String> warnings) {
        return new InstanceSettingsValidation(true, chunkRadius, tickDistance, warnings, null);
    }
    public static InstanceSettingsValidation error(String error) {
        return new InstanceSettingsValidation(false, 0, 0, List.of(), error);
    }
}

public InstanceSettingsValidation validateInstanceSettings(
    ArenaTemplate template,
    InstanceManagerConfig serverConfig
) {
    List<String> warnings = new ArrayList<>();

    // 1. Get template values
    int requestedChunkRadius = template.instanceSettings().chunkRadius();
    int requestedTickDistance = template.instanceSettings().tickDistance();

    // 2. Get server limits
    int serverMaxChunkRadius = serverConfig.maxChunkRadius();   // e.g., 8
    int serverMaxTickDistance = serverConfig.maxTickDistance(); // e.g., 10

    // 3. Clamp to server limits
    int effectiveChunkRadius = Math.min(requestedChunkRadius, serverMaxChunkRadius);
    int effectiveTickDistance = Math.min(requestedTickDistance, serverMaxTickDistance);

    // 4. Log clamp warnings
    if (effectiveChunkRadius < requestedChunkRadius) {
        warnings.add("chunkRadius clamped: %d → %d (server max: %d)".formatted(
            requestedChunkRadius, effectiveChunkRadius, serverMaxChunkRadius));
        telemetry.emit("arena.instance.clamped", Map.of(
            "templateId", template.id(),
            "field", "chunkRadius",
            "requested", requestedChunkRadius,
            "effective", effectiveChunkRadius,
            "serverMax", serverMaxChunkRadius
        ));
    }
    if (effectiveTickDistance < requestedTickDistance) {
        warnings.add("tickDistance clamped: %d → %d (server max: %d)".formatted(
            requestedTickDistance, effectiveTickDistance, serverMaxTickDistance));
        telemetry.emit("arena.instance.clamped", Map.of(
            "templateId", template.id(),
            "field", "tickDistance",
            "requested", requestedTickDistance,
            "effective", effectiveTickDistance,
            "serverMax", serverMaxTickDistance
        ));
    }

    // 5. Validate arena coverage
    int maxDim = Math.max(template.sizeXOrDefault(), template.sizeZOrDefault()); // rettangolari supportati
    int requiredChunks = (int) Math.ceil(maxDim / 16.0) + 1; // +1 buffer
    if (effectiveChunkRadius < requiredChunks) {
        return InstanceSettingsValidation.error(
            "Arena size %d requires %d chunks, but effective chunkRadius is only %d (server max: %d). "
            + "Reduce arena size or increase server maxChunkRadius.".formatted(
                maxDim, requiredChunks, effectiveChunkRadius, serverMaxChunkRadius));
    }

    return InstanceSettingsValidation.success(effectiveChunkRadius, effectiveTickDistance, warnings);
}
```

#### Runtime Limit Override

```java
// Se il server cambia limiti a runtime (es. reload config)
public void onServerConfigReload(InstanceManagerConfig newConfig) {
    for (ActiveArenaInstance instance : activeInstances.values()) {
        int currentChunkRadius = instance.getChunkRadius();
        int newMaxChunkRadius = newConfig.maxChunkRadius();

        if (currentChunkRadius > newMaxChunkRadius) {
            // Server ha abbassato i limiti - l'istanza esistente continua
            // ma logga warning per nuove istanze
            LOGGER.warn("Active instance {} has chunkRadius {} > new server max {}. "
                + "Instance continues with current settings, new instances will use lower limit.",
                instance.getId(), currentChunkRadius, newMaxChunkRadius);

            telemetry.emit("arena.instance.limit_exceeded_post_reload", Map.of(
                "instanceId", instance.getId(),
                "templateId", instance.getTemplateId(),
                "currentChunkRadius", currentChunkRadius,
                "newServerMax", newMaxChunkRadius
            ));

            // Optional: show warning overlay to players in instance
            instance.getPlayers().forEach(player ->
                player.sendSystemMessage(Component.literal(
                    "⚠ Server config changed. Arena may have reduced chunk loading."
                ).withStyle(ChatFormatting.YELLOW))
            );
        }
    }
}
```

#### Instance Settings Compatibility Tasks

- [ ] Implementare `InstanceSettingsValidation` record
- [ ] Implementare `validateInstanceSettings()`:
  - [ ] Get server limits da `InstanceManagerConfig`
  - [ ] Clamp `chunkRadius` e `tickDistance`
  - [ ] Log warning se clamped
  - [ ] Telemetria `arena.instance.clamped`
- [ ] Implementare arena coverage check:
  - [ ] Formula: `requiredChunks = ceil(max(sizeX,sizeZ,size)/16) + 1`
  - [ ] ERROR se `effectiveChunkRadius < requiredChunks`
  - [ ] Messaggio user-friendly con suggerimento fix
- [ ] Integrare validazione in `TemplateArenaBuilder.validateBuild()`:
  - [ ] Call `validateInstanceSettings()` prima del build
  - [ ] Propagate warnings al caller
  - [ ] Abort su error
- [ ] Gestire runtime limit changes:
  - [ ] Subscribe a server config reload events
  - [ ] Istanze esistenti: continuano, log warning
  - [ ] Nuove istanze: usano nuovi limiti
  - [ ] Optional: overlay warning ai player
- [ ] UI/HUD feedback:
  - [ ] Mostrare `effectiveChunkRadius` vs `requestedChunkRadius` se diversi
  - [ ] Warning icon se clamped
- [ ] Telemetria:
  - `arena.instance.clamped` con `{templateId, field, requested, effective, serverMax}`
- `arena.instance.coverage_insufficient` con `{templateId, maxDim, requiredChunks, effectiveChunkRadius}`
  - `arena.instance.limit_exceeded_post_reload` per cambio config runtime
- [ ] Unit test:
  - [ ] Clamp chunkRadius quando > server max
  - [ ] Clamp tickDistance quando > server max
  - [ ] Coverage check pass (size 64 → need 5 chunks, chunkRadius 5 OK)
  - [ ] Coverage check fail (size 128 → need 9 chunks, chunkRadius 4 FAIL)
  - [ ] Runtime config reload con istanze attive
  - [ ] keepLoaded sempre rispettato
- [ ] Documentare limiti raccomandati nel README:
  - Default: `chunkRadius: 2-4` per arene fino a 64 blocks
  - Boss: `chunkRadius: 5-6` per arene fino a 80-96 blocks
  - Large: `chunkRadius: 8` per arene fino a 128 blocks
- [ ] Owner: Core Dev · Durata: 0.25g · Done: validazione integrata, clamp funzionante, telemetria attiva

#### Build Settings
- `buildPriority`: `"sync"` | `"async"`
- `buildOrder`: `"floor_first"` | `"walls_first"` | `"structure_first"`
  - `buildOrder` governa la sequenza: es. `structure_first` piazza NBT prima di floor/walls; `buildPriority` decide budget tick (sync vs async distribuito).
- `limits`: budget build
  ```json
  "limits": {
    "maxBuildTimeMs": 5000,
    "maxBlocks": 50000,
    "maxEntities": 100
  }
  ```
  - Nota: i limiti sono **per singola build transaction** (un tentativo di build); il conteggio dei blocchi include quelli piazzati anche se poi rollati; un retry riparte con un nuovo budget.

#### Tags
- `tags`: array di stringhe per categorizzazione layout (es. `["flat", "melee-friendly", "smoke"]`)

#### **Campi esclusi (vanno in Policy L2)**
- ❌ `routing` (mobIds, questTypes, weight)
- ❌ `perkBindings`, `mutatorBindings`
- ❌ `rewardModifiers`, `balanceOverrides`

---

### Versioning Strategy

#### Template Versioning
- [ ] Implementare campo `version` (int) - incrementa ad ogni modifica del template
- [ ] Implementare campo `schemaVersion` (SemVer, es. "1.0.0") - versione della struttura dello schema
- [ ] Implementare campo `breakingChange` (bool) - true se incompatibile con versioni precedenti

#### Breaking Change Rules
| Modifica | Breaking? | Azione richiesta |
|----------|-----------|------------------|
| Cambio `size` | ✅ SÌ | `breakingChange: true`, bump version |
| Cambio `floor.y` | ✅ SÌ | `breakingChange: true`, bump version |
| Rimozione `spawnSlots` | ✅ SÌ | `breakingChange: true`, bump version |
| Cambio `hazards` layout | ✅ SÌ | `breakingChange: true`, bump version |
| Cambio `palette` (materiali) | ❌ NO | bump version solo |
| Aggiunta nuovo `spawnSlot` | ❌ NO | bump version solo |
| Cambio `lighting` | ❌ NO | bump version solo |
| Cambio `environment` effects | ❌ NO | bump version solo |

- [ ] Documentare regole breaking change nello schema
- [ ] Validazione automatica: warn se breaking change senza flag

#### Policy Versioning e Binding
- [ ] Implementare campo `minTemplateVersion` (int|null) in Policy - versione minima template supportata
- [ ] Implementare campo `maxTemplateVersion` (int|null) in Policy - versione massima template supportata (opzionale)
- [ ] Binding logic:
  ```
  Se minTemplateVersion != null && template.version < minTemplateVersion → INCOMPATIBILE
  Se maxTemplateVersion != null && template.version > maxTemplateVersion → INCOMPATIBILE
  Se template.breakingChange && policy.version < template.version → INCOMPATIBILE (policy non aggiornata)
  ```

#### Compatibility Check
- [ ] Implementare `VersionCompatibilityChecker.check(template, policy)`:
  ```java
  public record VersionCheck(boolean compatible, String reason) {}

  public VersionCheck check(ArenaTemplate template, ArenaPolicy policy) {
      if (policy.minTemplateVersion() != null && template.version() < policy.minTemplateVersion()) {
          return new VersionCheck(false, "Template v%d < policy minVersion v%d".formatted(
              template.version(), policy.minTemplateVersion()));
      }
      if (policy.maxTemplateVersion() != null && template.version() > policy.maxTemplateVersion()) {
          return new VersionCheck(false, "Template v%d > policy maxVersion v%d".formatted(
              template.version(), policy.maxTemplateVersion()));
      }
      if (template.breakingChange() && policy.version() < template.version()) {
          return new VersionCheck(false, "Template has breaking change at v%d, policy at v%d needs update".formatted(
              template.version(), policy.version()));
      }
      return new VersionCheck(true, null);
  }
  ```
- [ ] Integrare check in `PolicyResolver.resolve()`
- [ ] Owner: Core Dev · Durata: 0.25g · Done: check implementato, integrato in resolver

#### Mismatch Behavior
- [ ] Su incompatibilità versione:
  1. Log `arena.policy.version_mismatch` con dettaglio (templateId, templateVersion, policyId, policyMinVersion, reason)
  2. Telemetria evento per tracking rate mismatch
  3. Fallback a `default.policy.json` se disponibile
  4. Se nessun fallback → abort con messaggio chiaro
- [ ] Alert dashboard se mismatch rate > 5% in 24h
- [ ] Owner: Core Dev · Durata: incluso in Compatibility Check · Done: behavior implementato

#### Schema Version Migration
- [ ] Se `schemaVersion` diverso da loader:
  - Minor bump (1.0 → 1.1): load con warning, campi nuovi = default
  - Major bump (1.x → 2.x): reject load, richiede migration
- [ ] Migration script per upgrade schema:
  - `arena-migrate --from 1.0 --to 2.0 templates/`
- [ ] Owner: Core Dev · Durata: 0.25g (opzionale) · Done: migration path documentato

---

- [ ] Creare template `default_flat_64.template.json` completo
  - [ ] Golden reference deterministico: snapshot di AABB, conteggio blocchi per materiale, lista spawnSlots, forbiddenZones, lightSources (per test regression)
- [ ] Creare template `boss_ring_80.template.json` con inheritance
- [ ] (Opzionale) Creare template `ranged_corridor_48.template.json`
- [ ] Owner: Tech Lead · Durata: 0.75g · Done: schema validato, 2 template layout caricati

### L2 - ArenaPolicy Schema (Gameplay rules)
- [ ] Pubblicare `arena_policy.schema.json` con campi:
  - `id`, `version`, `templateId` (riferimento a L1)
  - `minTemplateVersion` (o `templateVersion` esatto) e `schemaVersion`/`schemaHash`
  - `routing`: `{ mobIds[], questTypes[], difficultyTags[], weight }`
  - `perkBindings`: `{ suggested[], excluded[], required[] }`
  - `mutatorBindings`: `{ suggested[], excluded[], required[] }`
  - `rewardModifiers`: `{ baseMultiplier, firstCompletionBonus, hazardBonus }`
  - `balanceOverrides`: `{ spawnRateMultiplier, damageMultiplier, waveScaling }`
  - `tags` (gameplay tags: ranked, casual, hardcore, smoke, etc.)
  - **Routing weight policy**:
    - `routing.weight` default: `1.0` se non specificato.
    - Range consentito: `0.1`–`10.0` (valori fuori range vengono clampati con WARN + telemetria `arena.routing.weight_clamped`).
    - Normalizzazione: nel resolver il punteggio finale somma gli altri fattori + `routing.weight` (post-clamp); non è una probabilità ma un bonus additivo controllato per evitare template dominanti.
    - Se tutti i template hanno weight=0 (dopo clamp) → fallback a weight=1 per tutti con WARN.

- [ ] Creare policy `default_flat_64.policy.json` (policy base)
- [ ] Creare policy `boss_ring_80_ranked.policy.json` (boss con reward alto)
- [ ] Creare policy `boss_ring_80_casual.policy.json` (stesso layout, reward normale)
- [ ] Owner: Game Designer / Core Dev · Durata: 0.5g · Done: schema policy validato, 3 policy caricate

### Binding Template ↔ Policy
- [ ] Un template può avere 0-N policy associate
- [ ] Se nessuna policy → usa `default.policy.json` (fallback globale)
- [ ] Policy resolution: `policyId` esplicito > routing match > default
- [ ] Gestione mismatch versione: se `policy.minTemplateVersion` > template.version → error/fallback; telemetria `arena.policy.version_mismatch`
- [ ] Owner: Core Dev · Durata: 0.25g · Done: binding funzionante

### Loader e Baseline
- [ ] Implementare `ArenaTemplateLoader` per L1 (layout JSON)
- [ ] Implementare `ArenaPolicyLoader` per L2 (policy JSON)
- [ ] Implementare risoluzione inheritance ricorsiva (solo per template, policy non eredita)
- [ ] Validazione JSON schema: modalità strict (ERROR su campi sconosciuti) con opzione permissiva (WARN) configurabile via flag.

#### Error Isolation Strategy (Non-Catastrophic Loading)

| Scenario | Comportamento | Log Level | Registry State |
|----------|---------------|-----------|----------------|
| Template singolo invalido | Skip, continua altri | ERROR | Altri template caricati |
| Template parent mancante | Skip child, continua | ERROR | Parent e siblings caricati |
| Tutti template invalidi | Registry vuota + fallback | FATAL | Solo `default_flat_64` hardcoded |
| Policy singola invalida | Skip, continua altre | ERROR | Altre policy caricate |
| Directory non trovata | Crea vuota, log warning | WARN | Registry vuota (ok se primo avvio) |

```java
public RegistryLoadSummary loadAll(Path directory) {
    List<String> loaded = new ArrayList<>();
    List<LoadError> errors = new ArrayList<>();

    for (Path file : listTemplateFiles(directory)) {
        try {
            ArenaTemplate template = loadSingle(file);
            if (validate(template).isValid()) {
                rawTemplates.put(template.id(), template);
                loaded.add(template.id());
            } else {
                errors.add(new LoadError(file, "Validation failed"));
            }
        } catch (Exception e) {
            errors.add(new LoadError(file, e.getMessage()));
            telemetry.emit("arena.template.load_failed", Map.of("file", file.toString()));
        }
    }

    // Fallback if all failed
    if (loaded.isEmpty() && !errors.isEmpty()) {
        rawTemplates.put("default_flat_64", HARDCODED_FALLBACK);
        telemetry.emit("arena.template.fallback_injected", Map.of());
    }

    return new RegistryLoadSummary(loaded, errors);
}
```

##### Error Isolation Tasks
- [ ] Try-catch per ogni file load (non throw globale)
- [ ] Accumulare `LoadError` list per report
- [ ] Log summary a fine load (`Loaded X/Y templates`)
- [ ] Fallback hardcoded se registry vuota
- [ ] Esporre `getLoadErrors()` per diagnostica
- [ ] Comando `/devmod arena status` mostra errori

---

#### Template Source Priority

| Priorità | Source | Path | Use Case |
|----------|--------|------|----------|
| 1 (alta) | **Config override** | `config/devmod/arena_templates/` | Server customization |
| 2 | **Datapack** | `datapacks/*/data/devmod/arena_templates/` | Community templates |
| 3 (bassa) | **Mod resources** | `data/devmod/arena_templates/` (jar) | Default templates |

```java
public Map<String, ArenaTemplate> loadAllSources() {
    Map<String, ArenaTemplate> result = new LinkedHashMap<>();

    // 1. Mod resources (lowest)
    loadFromResources("data/devmod/arena_templates/").forEach(result::put);

    // 2. Datapacks (override)
    for (Path dp : getActiveDatapacks()) {
        loadFromPath(dp.resolve("data/devmod/arena_templates/")).forEach((id, t) -> {
            if (result.containsKey(id)) {
                telemetry.emit("arena.template.overridden", Map.of("id", id, "source", "datapack"));
            }
            result.put(id, t);
        });
    }

    // 3. Config (highest)
    loadFromPath(Path.of("config/devmod/arena_templates/")).forEach((id, t) -> {
        if (result.containsKey(id)) {
            telemetry.emit("arena.template.overridden", Map.of("id", id, "source", "config"));
        }
        result.put(id, t);
    });

    return result;
}
```

##### Source Priority Tasks
- [ ] Caricamento da mod resources (jar)
- [ ] Caricamento da datapacks attivi
- [ ] Caricamento da config directory
- [ ] Priority: config > datapack > mod
- [ ] Log quando template sovrascritto
- [ ] Persistere `sourceMap` per debug
- [ ] Comando `/devmod arena sources`

---

#### JSON Schema Validation Mode

| Mode | Campi ignoti | Campi required mancanti | Config Flag |
|------|--------------|-------------------------|-------------|
| **STRICT** (prod default) | ERROR | ERROR | `schemaValidation: "strict"` |
| **PERMISSIVE** (dev) | WARN | ERROR | `schemaValidation: "permissive"` |
| **LENIENT** (migration) | IGNORE | WARN + default | `schemaValidation: "lenient"` |

```java
public SchemaValidationResult validateSchema(JsonObject json, ValidationMode mode) {
    List<String> errors = new ArrayList<>();
    Set<String> unknownFields = findUnknownFields(json);

    if (!unknownFields.isEmpty()) {
        String msg = "Unknown fields: %s".formatted(unknownFields);
        switch (mode) {
            case STRICT -> errors.add(msg);
            case PERMISSIVE -> LOGGER.warn(msg);
            case LENIENT -> { /* ignore */ }
        }
        telemetry.emit("arena.template.unknown_fields", Map.of("fields", unknownFields));
    }

    return new SchemaValidationResult(errors.isEmpty(), errors, unknownFields);
}
```

##### Schema Validation Tasks
- [ ] Implementare `ValidationMode` enum
- [ ] Config flag `schemaValidation` (default STRICT)
- [ ] Skip `$schema`, `_comment`, `//` prefixed fields
- [ ] Required fields: `id`, `version`, `schemaVersion`, `size`
- [ ] Telemetria `arena.template.unknown_fields`
- [ ] Unit test per ogni mode

---

#### Hot-Reload Strategy (Atomic Swap)

```java
public class ArenaTemplateRegistry {
    private volatile ImmutableMap<String, ArenaTemplate> activeRegistry = ImmutableMap.of();
    private final ReentrantReadWriteLock reloadLock = new ReentrantReadWriteLock();

    public void reload() {
        // 1. Build new registry in isolation
        Map<String, ArenaTemplate> newRegistry = loader.loadAll(templateDirectory);

        // 2. Resolve inheritance on new data
        newRegistry.replaceAll((id, t) -> resolveInheritance(id, newRegistry));

        // 3. Atomic swap
        reloadLock.writeLock().lock();
        try {
            ImmutableMap<String, ArenaTemplate> old = activeRegistry;
            activeRegistry = ImmutableMap.copyOf(newRegistry);
            listeners.forEach(l -> l.onRegistryReloaded(old, activeRegistry));
            telemetry.emit("arena.template.reloaded", Map.of(
                "oldCount", old.size(), "newCount", activeRegistry.size()));
        } finally {
            reloadLock.writeLock().unlock();
        }
    }

    public Optional<ArenaTemplate> get(String id) {
        return Optional.ofNullable(activeRegistry.get(id)); // lock-free
    }
}
```

##### Hot-Reload Tasks
- [ ] `ImmutableMap` per registry attiva
- [ ] `ReentrantReadWriteLock` per reload
- [ ] Atomic swap (no stato intermedio)
- [ ] Read path lock-free (volatile read)
- [ ] Build nuova registry in isolamento
- [ ] Notificare listeners dopo swap
- [ ] Telemetria reload con diff

---

#### Memory Leak Prevention

| Potenziale Leak | Causa | Mitigazione |
|-----------------|-------|-------------|
| Listener accumulation | Non deregistrati | `WeakReference` + cleanup |
| Cache stale | Non invalidata | Clear cache prima di swap |
| File watcher handles | Non chiuso | `close()` in shutdown |
| Lock map entries | Template rimossi | Prune su reload |
| Scheduled tasks | Non cancellati | `shutdownNow()` su close |

```java
public class ArenaTemplateRegistry implements AutoCloseable {
    private final List<WeakReference<RegistryListener>> listeners = new CopyOnWriteArrayList<>();
    private final Cache<String, ResolvedTemplate> resolvedCache;
    private WatchService fileWatcher;

    public void reload() {
        resolvedCache.invalidateAll();  // Clear before reload
        // ... load new registry ...
        templateLocks.keySet().removeIf(id -> !activeRegistry.containsKey(id));  // Prune
        listeners.removeIf(ref -> ref.get() == null);  // Prune dead refs
    }

    @Override
    public void close() {
        if (fileWatcher != null) fileWatcher.close();
        if (hotReloadScheduler != null) hotReloadScheduler.shutdownNow();
        templateLocks.clear();
        listeners.clear();
        resolvedCache.invalidateAll();
    }
}
```

##### Memory Leak Prevention Tasks
- [ ] `WeakReference` per listeners
- [ ] `AutoCloseable` per registry
- [ ] Clear cache prima di reload
- [ ] Prune stale locks dopo reload
- [ ] Prune dead listeners periodicamente
- [ ] Close file watcher su shutdown
- [ ] Cancel scheduled tasks su shutdown
- [ ] Health check periodico (5 min):
  - [ ] Count stale locks, dead listeners
  - [ ] Telemetria `arena.template.health_warning`
- [ ] Stress test: reload 100x, verificare no leak
- [ ] Owner: Core Dev · Durata: 0.5g · Done: no leak su stress test

---

#### Inheritance Strategy (Template Only)

| Aspetto | Decisione | Rationale |
|---------|-----------|-----------|
| Catena lineare | ✅ Consentita (A → B → C) | Varianti incrementali |
| Diamond inheritance | ❌ Vietato | Evita ambiguità merge |
| Max depth | 3 livelli | Previene complessità |
| Merge strategy | Shallow merge top-level | Prevedibile |

#### Merge Rules
| Tipo campo | Comportamento |
|------------|---------------|
| Primitivi (string, int, bool) | Child vince |
| Array (spawnSlots, hazards, forbiddenZones, lightSources, particles, tags) | Child SOSTITUISCE (no merge/concat/dedup) |
| Oggetti top-level (floor, walls, ceiling, underfloor, palette, biome, lighting, environment, instanceSettings, limits) | Shallow merge (child sovrascrive i campi specificati, il resto ereditato) |
| Oggetti nested (es. spawnSlots[].validation) | Child SOSTITUISCE intero oggetto |

#### Merge Strategy per Campo (Dettaglio)

| Campo | Tipo | Strategia | Rationale |
|-------|------|-----------|-----------|
| `id` | string | N/A (mai ereditato) | Identità unica |
| `version` | int | N/A (mai ereditato) | Versione child |
| `extends` | string | N/A (consumato dal loader) | Solo per risoluzione |
| `size`, `sizeX`, `sizeZ` | int | **OVERRIDE** | Dimensioni = identità layout |
| `origin` | object | **SHALLOW MERGE** | `{...parent.origin, ...child.origin}` |
| `floor` | object | **SHALLOW MERGE** | Child può cambiare solo `material` senza ripetere `y`, `thickness` |
| `walls` | object | **SHALLOW MERGE** | Child può cambiare solo `height` senza ripetere `material` |
| `ceiling` | object | **SHALLOW MERGE** | Idem |
| `underfloor` | object | **SHALLOW MERGE** | Idem |
| `palette` | object | **SHALLOW MERGE** | Child aggiunge/sovrascrive chiavi, non rimuove |
| `biome` | object | **SHALLOW MERGE** | Idem |
| `lighting` | object | **SHALLOW MERGE** | Tranne `lightSources` (array → override) |
| `lighting.lightSources` | array | **OVERRIDE** | Child sostituisce intero array |
| `spawnSlots` | array | **OVERRIDE** | Child definisce tutti gli slot, no concat |
| `forbiddenZones` | array | **OVERRIDE** | Child definisce tutte le zone |
| `hazards` | array | **OVERRIDE** | Child definisce tutti gli hazard |
| `environment` | object | **SHALLOW MERGE** | Tranne `particles` (array → override) |
| `environment.particles` | array | **OVERRIDE** | Child sostituisce intero array |
| `compat` | object | **SHALLOW MERGE** | Child può cambiare solo `maxPlayers` |
| `instanceSettings` | object | **SHALLOW MERGE** | Idem |
| `structureNbt` | object | **OVERRIDE** | Se child specifica, sostituisce tutto |
| `limits` | object | **SHALLOW MERGE** | Child può override singoli limiti |
| `tags` | array | **OVERRIDE** | Child definisce tutti i tag (no concat/dedup) |

#### Merge Examples

**Esempio 1: Shallow merge `floor`**
```json
// Parent
"floor": { "y": 64, "thickness": 1, "material": "stone_bricks", "pattern": "border", "borderWidth": 2 }

// Child (specifica solo 2 campi)
"floor": { "material": "deepslate_bricks", "pattern": "solid" }

// Risultato merged
"floor": { "y": 64, "thickness": 1, "material": "deepslate_bricks", "pattern": "solid", "borderWidth": 2 }
```

**Esempio 2: Override `spawnSlots` (NO concat)**
```json
// Parent: 4 spawn slots
"spawnSlots": [ {pos:[0,1,0]}, {pos:[10,1,0]}, {pos:[-10,1,0]}, {pos:[20,1,20]} ]

// Child: definisce solo 2 slot
"spawnSlots": [ {pos:[0,1,0], tags:["boss"]}, {pos:[30,1,0], tags:["player"]} ]

// Risultato: SOLO i 2 slot del child (parent ignorato)
"spawnSlots": [ {pos:[0,1,0], tags:["boss"]}, {pos:[30,1,0], tags:["player"]} ]
```

**Esempio 3: Override `tags` (NO dedup)**
```json
// Parent
"tags": ["flat", "melee-friendly", "smoke"]

// Child
"tags": ["ring", "hazard", "boss-layout", "smoke"]

// Risultato: SOLO tag del child
"tags": ["ring", "hazard", "boss-layout", "smoke"]
```

**Esempio 4: Shallow merge `palette` (additive keys)**
```json
// Parent
"palette": { "accent": "polished_andesite", "highlight": "glowstone" }

// Child
"palette": { "accent": "gilded_blackstone", "hazardBorder": "magma_block" }

// Risultato: merge, child vince su conflitti
"palette": { "accent": "gilded_blackstone", "highlight": "glowstone", "hazardBorder": "magma_block" }
```

#### Merge Tasks
- [ ] Implementare `MergeStrategy` enum: `OVERRIDE`, `SHALLOW_MERGE`, `SKIP`
- [ ] Implementare `FieldMerger.merge(parent, child, strategy)`:
  ```java
  public Object merge(Object parent, Object child, MergeStrategy strategy) {
      return switch (strategy) {
          case OVERRIDE -> child != null ? child : parent;
          case SHALLOW_MERGE -> shallowMerge((Map)parent, (Map)child);
          case SKIP -> parent; // mai sovrascrive
      };
  }

  private Map<String, Object> shallowMerge(Map<String, Object> parent, Map<String, Object> child) {
      if (parent == null) return child;
      if (child == null) return parent;
      Map<String, Object> result = new HashMap<>(parent);
      result.putAll(child); // child vince su conflitti
      return result;
  }
  ```
- [ ] Creare mapping `FIELD_STRATEGIES`:
  ```java
  Map<String, MergeStrategy> FIELD_STRATEGIES = Map.ofEntries(
      entry("id", SKIP),
      entry("version", SKIP),
      entry("extends", SKIP),
      entry("size", OVERRIDE),
      entry("floor", SHALLOW_MERGE),
      entry("walls", SHALLOW_MERGE),
      entry("spawnSlots", OVERRIDE),
      entry("hazards", OVERRIDE),
      entry("tags", OVERRIDE),
      // ... altri campi
  );
  ```
- [ ] Gestire array nested in oggetti shallow-merged:
  - `lighting.lightSources` → OVERRIDE anche se `lighting` è SHALLOW_MERGE
  - `environment.particles` → OVERRIDE anche se `environment` è SHALLOW_MERGE
- [ ] Telemetria `arena.template.merge` con:
  - `fieldsOverridden`: conteggio campi sovrascritti
  - `fieldsInherited`: conteggio campi ereditati
  - `mergeDepth`: profondità catena
- [ ] Unit test merge:
  - [ ] Shallow merge oggetti top-level
  - [ ] Override array (spawnSlots, hazards, tags)
  - [ ] Array nested in oggetti shallow-merged
  - [ ] Child null → eredita parent
  - [ ] Parent null → usa child
  - [ ] Entrambi null → usa default schema
- [ ] Owner: Core Dev · Durata: 0.5g · Done: merge testato per tutti i tipi campo

---

#### Inheritance Tasks
- [ ] Implementare `resolveInheritance(templateId)` con:
  ```java
  public ArenaTemplate resolveInheritance(String templateId) {
      List<String> chain = new ArrayList<>();
      Set<String> seen = new HashSet<>();
      ArenaTemplate current = get(templateId);

      while (current.extends_() != null) {
          // Cycle check
          if (seen.contains(current.extends_())) {
              throw new CircularInheritanceException(templateId, chain);
          }
          // Depth check
          if (chain.size() >= MAX_INHERITANCE_DEPTH) {
              throw new InheritanceDepthException(templateId, MAX_INHERITANCE_DEPTH);
          }
          // Diamond check (duplicate ancestor)
          if (!seen.add(current.extends_())) {
              throw new DiamondInheritanceException(templateId, current.extends_());
          }
          chain.add(current.id());
          current = get(current.extends_());
      }

      // Merge dalla root verso il child
      return mergeChain(chain.reversed(), current);
  }
  ```
- [ ] Implementare `mergeTemplates(parent, child)`:
  - Primitivi: `child.field != null ? child.field : parent.field`
  - Array: `child.array != null ? child.array : parent.array` (no merge)
  - Oggetti: shallow merge con `{...parent.obj, ...child.obj}`
- [ ] Implementare eccezioni dedicate:
  - `CircularInheritanceException` (A → B → A)
  - `DiamondInheritanceException` (D → B, D → C dove B e C → A)
  - `InheritanceDepthException` (catena > 3 livelli)

#### Error Handling Strategy (Inheritance)

| Problema | Severity | Azione | Fallback |
|----------|----------|--------|----------|
| Ciclo diretto (A → B → A) | **ERROR** | Blocco load, throw exception | Nessuno (template inutilizzabile) |
| Ciclo indiretto (A → B → C → A) | **ERROR** | Blocco load, throw exception | Nessuno |
| Max depth exceeded (> 3) | **ERROR** | Blocco load, throw exception | Nessuno |
| Parent non trovato | **ERROR** | Blocco load, throw exception | Nessuno |
| Parent con `breakingChange=true` incompatibile | **ERROR** | Blocco load, throw exception | Nessuno |
| Parent deprecato (`deprecated=true`) | **WARN** | Log warning, continua load | Usa parent deprecato |
| schemaVersion mismatch (minor) | **WARN** | Log warning, usa defaults per campi nuovi | Template parziale |
| schemaVersion mismatch (major) | **ERROR** | Blocco load | Nessuno |

#### Cycle Detection Algorithm
```java
public InheritanceValidation validateInheritance(String templateId) {
    Set<String> visited = new HashSet<>();
    List<String> path = new ArrayList<>();
    ArenaTemplate current = rawTemplates.get(templateId);

    while (current != null && current.extends_() != null) {
        String parentId = current.extends_();
        path.add(current.id());

        // 1. Cycle check (ERROR - hard block)
        if (visited.contains(parentId)) {
            String cycle = formatCycle(path, parentId);
            telemetry.emit("arena.template.cycle_detected", Map.of(
                "templateId", templateId,
                "cycle", cycle,
                "path", path
            ));
            return InheritanceValidation.error(
                InheritanceError.CIRCULAR_REFERENCE,
                "Circular inheritance detected: %s".formatted(cycle)
            );
        }

        // 2. Depth check (ERROR - hard block)
        if (path.size() > MAX_INHERITANCE_DEPTH) {
            telemetry.emit("arena.template.depth_exceeded", Map.of(
                "templateId", templateId,
                "depth", path.size(),
                "maxDepth", MAX_INHERITANCE_DEPTH
            ));
            return InheritanceValidation.error(
                InheritanceError.MAX_DEPTH_EXCEEDED,
                "Inheritance depth %d exceeds max %d".formatted(path.size(), MAX_INHERITANCE_DEPTH)
            );
        }

        // 3. Parent exists check (ERROR - hard block)
        ArenaTemplate parent = rawTemplates.get(parentId);
        if (parent == null) {
            telemetry.emit("arena.template.parent_not_found", Map.of(
                "templateId", templateId,
                "parentId", parentId
            ));
            return InheritanceValidation.error(
                InheritanceError.PARENT_NOT_FOUND,
                "Parent template '%s' not found".formatted(parentId)
            );
        }

        // 4. Breaking change check (ERROR - hard block)
        if (parent.breakingChange() && !isVersionCompatible(current, parent)) {
            return InheritanceValidation.error(
                InheritanceError.BREAKING_CHANGE_INCOMPATIBLE,
                "Parent '%s' has breaking change at v%d, child not compatible".formatted(
                    parentId, parent.version())
            );
        }

        // 5. Deprecation check (WARN - soft continue)
        if (parent.deprecated()) {
            telemetry.emit("arena.template.deprecated_parent", Map.of(
                "templateId", templateId,
                "parentId", parentId,
                "replacement", parent.replacementVersion()
            ));
            LOGGER.warn("Template '{}' extends deprecated parent '{}'. Consider migrating to '{}'",
                templateId, parentId, parent.replacementVersion());
        }

        visited.add(current.id());
        current = parent;
    }

    return InheritanceValidation.success(path);
}

private String formatCycle(List<String> path, String cycleTarget) {
    int idx = path.indexOf(cycleTarget);
    if (idx >= 0) {
        return String.join(" → ", path.subList(idx, path.size())) + " → " + cycleTarget;
    }
    return String.join(" → ", path) + " → " + cycleTarget;
}
```

#### Error Response Format
```java
public sealed interface InheritanceValidation {
    record Success(List<String> chain) implements InheritanceValidation {}
    record Error(InheritanceError type, String message) implements InheritanceValidation {}

    static InheritanceValidation success(List<String> chain) { return new Success(chain); }
    static InheritanceValidation error(InheritanceError type, String msg) { return new Error(type, msg); }
}

public enum InheritanceError {
    CIRCULAR_REFERENCE,           // A → B → A
    MAX_DEPTH_EXCEEDED,           // chain > 3
    PARENT_NOT_FOUND,             // extends non-existent template
    BREAKING_CHANGE_INCOMPATIBLE, // parent.breakingChange && version mismatch
    SCHEMA_VERSION_MAJOR_MISMATCH // schemaVersion 1.x → 2.x
}
```

#### Error Handling Tasks
- [ ] Implementare `InheritanceValidation` sealed interface
- [ ] Implementare `InheritanceError` enum con tutti i tipi
- [ ] Implementare `validateInheritance()` con algoritmo sopra
- [ ] Formattare messaggi errore user-friendly:
  - Ciclo: `"Circular inheritance: A → B → C → A. Remove one extends."`
  - Depth: `"Chain too deep (4 > 3): A → B → C → D. Flatten hierarchy."`
  - Parent not found: `"'foo' extends 'bar' but 'bar' does not exist."`
- [ ] Telemetria per ogni tipo di errore:
  - `arena.template.inheritance_error` con `{type, templateId, message, chain}`
- [ ] UI/CLI output:
  - ERROR: rosso, blocca load, mostra path completo
  - WARN: giallo, continua load, mostra suggerimento
- [ ] Comando `/devmod arena validate <id>` per validazione senza load
- [ ] Unit test error handling:
  - [ ] Ciclo diretto A → B → A
  - [ ] Ciclo indiretto A → B → C → A
  - [ ] Depth 4 (exceed max 3)
  - [ ] Parent mancante
  - [ ] Parent con breakingChange
  - [ ] Parent deprecato (warn, non error)
- [ ] Unit test inheritance coverage: catena lunga (depth limit), override array/lista, campi opzionali assenti (default), parent mancante → errore chiaro
- [ ] Owner: Core Dev · Durata: 0.25g · Done: tutti gli errori gestiti con messaggi chiari

---

- [ ] Validare parent `breakingChange`:
  - Se `parent.breakingChange=true` e `child.minParentVersion` < parent.version → errore
- [ ] Telemetria `arena.template.inheritance_resolved` con chain length
- [ ] Unit test casi edge:
  - [ ] Catena lunga al limite MAX_INHERITANCE_DEPTH valida
  - [ ] Override liste (spawnSlots/hazards/tags) resta OVERRIDE post-inheritance
  - [ ] Campi opzionali mancanti (sizeX/sizeZ) usano default size senza errore
  - [ ] Template mancante (parent non trovato) → errore
  - [ ] Catena lineare valida (A → B → C)
  - [ ] Cycle detection (A → B → A)
  - [ ] Diamond detection (se possibile in futuro)
  - [ ] Max depth exceeded
  - [ ] Parent con breakingChange
- [ ] Owner: Core Dev · Durata: 0.5g · Done: inheritance testata, eccezioni chiare
- [ ] Attivare baseline log build/destroy attuale (build_ms, entities_residual, blocks_residual)
  - [ ] Verificare che build_ms/entities_residual/blocks_residual mantengano definizione e strumentazione pre v2.1 (telemetry + NDJSON)
- [ ] Documentare priorità routing (weighted scoring) nel README/schema
- [ ] Owner: Core Dev · Durata: 1g · Done: entrambi i loader funzionanti, inheritance testata
- [ ] Gestione `breakingChange=true` nel loader:
  - Se template/policy `breakingChange=true` e versione non supportata → blocco hard + log error + telemetria `arena.template.unsupported_version`
  - Se `breakingChange=false` → consentito fallback/upcast con warning
  - Deprecation metadata (`deprecated: true`, `replacementVersion`) per versioni precedenti
  - Persisti `schemaVersion`/`schemaHash` per audit

### ArenaTemplateConfig (NUOVO - sfrutta Config system esistente)
- [ ] Creare `ArenaTemplateConfig.java` con:
  - `templateDirectory` - path directory template
  - `enableArenaTemplate` - feature flag principale
  - `enableRouting` - feature flag routing
  - `defaultBuildBudget` - maxBlocks (8000), maxBuildTimeMs (5000) default
  - `bossTemplateBudget` - override per template boss/grandi: maxBlocks (100000), maxBuildTimeMs (15000)
  - `autosmokeSchedule` - cron expression (default: `0 3 * * *` = 03:00 daily)
  - `alertThresholds`:
    - `build_ms_warn`: 3000ms (default), `build_ms_error`: 8000ms
    - `failure_rate_warn`: 5%, `failure_rate_error`: 15%
    - `rollback_rate_warn`: 2%, `rollback_rate_error`: 10%
    - `mspt_warn`: 40ms, `mspt_error`: 50ms
    - `tps_warn`: 18, `tps_error`: 15
  - `prebuildPoolEnabled` - default: false (opzionale per produzioni piccole)
  - `prebuildPoolConfig` - solo se enabled: `{ "default_flat_64": 2 }`
  - `alertChannels` - dove inviare alert: `["console", "log", "dashboard"]`
- [ ] Integrazione con ModConfig esistente
- [ ] Hot-reload config senza restart
- [ ] Owner: Core Dev · Durata: 0.25g · Done: config caricata, hot-reload funzionante

### Feature Flag Chain (NUOVO)
- [ ] Implementare chain progressiva:
  1. `INSTANCE_ONLY` - gate legacy overworld paths
  2. `ARENA_TEMPLATE_ENABLED` - abilita sistema template
  3. `ROUTING_ENABLED` - abilita weighted scoring routing
  4. `GAMIFICATION_ENABLED` - abilita badge/challenge template
- [ ] Ogni flag dipende dal precedente
- [ ] Logging chiaro su flag state
- [ ] Owner: Core Dev · Durata: 0.25g · Done: chain funzionante, logging attivo

### Regola dipendenze cross-ruolo
- Core chiude API/base (Config/Flags → Registry/Resolver/Builder → Telemetria/Persistenza → Instance/Recovery) prima che Tools costruisca UI/Commands/Dashboard.
- Tools parte solo con contract stabili (schema/endpoint/handle definiti).
- QA testa dopo Core+Tools “done” su area, usando feature flag (INSTANCE_ONLY, ARENA_TEMPLATE_ENABLED, ROUTING_ENABLED) per isolare; autosmoke su staging.
- Ordine minimo: F0 (Core/Tech Lead) → F1 Core → F2 Tools (post handle/telemetria) → QA (F1/F2) → F3 Core hardening → QA edge → F4 rollout/monitor.

---

## Fase 1 – Registry / Resolver / Builder (3 giorni)

### ArenaTemplateRegistry (L1 - Layout)
- [ ] Implementare `ArenaTemplateRegistry` con:
  - `load(path)` - carica template da `arena_templates/`
  - `get(id)` - ottiene template per ID
  - `all()` - lista tutti i template
  - `reload()` - hot-reload da disco
  - `resolveInheritance()` - merge ricorsivo campi
  - Sorgenti e priorità: `configDir/arena_templates` > datapack/resource pack > jar defaults; hot-reload osserva la dir configurata.
- [ ] Hot-reload atomico: carica snapshot separato, valida, poi swap unico del registry; blocca resolve/build durante lo swap (read/write lock), lascia il vecchio stato su errore.
- [ ] Teardown risorse su reload: svuotare cache temporanee, ricreare lock map, chiudere file watcher/listener per prevenire leak.
- [ ] Validazione schema con warning per campi mancanti
- [ ] Fallback automatico su `default_flat_64`
- [ ] Owner: Core Dev · Durata: 0.5g · Done: reload senza memory leak, fallback testato

### ArenaPolicyRegistry (L2 - Gameplay)
- [ ] Implementare `ArenaPolicyRegistry` con:
  - `load(path)` - carica policy da `arena_policies/`
  - `get(id)` - ottiene policy per ID
  - `getForTemplate(templateId)` - lista policy per un template
  - `all()` - lista tutte le policy
  - `reload()` - hot-reload da disco
- [ ] Validazione riferimento templateId (deve esistere in TemplateRegistry)
- [ ] Fallback automatico su `default.policy.json`
- [ ] Owner: Core Dev · Durata: 0.25g · Done: policy caricate, riferimenti validati

### PolicyResolver (Weighted Scoring)
- [ ] Implementare `PolicyResolver` per selezionare Policy (non Template direttamente):
  - Override manuale `forcePolicyId` (priorità assoluta)
  - Mob match da `policy.routing.mobIds`: +5 punti
  - QuestType match da `policy.routing.questTypes`: +4 punti
  - Difficulty tag match da `policy.routing.difficultyTags`: +3 punti
  - PlayerCount in range da `template.compat`: +2 punti
  - Tags match da `policy.tags`: +1 punto
  - Bonus `policy.routing.weight` esplicito
- [ ] Restituisce `ResolvedArena { template, policy }` come coppia
- [ ] Telemetria `arena.policy.chosen|fallback|force_not_found` con motivo
- [ ] Owner: Core Dev · Durata: 0.5g · Done: unit test scoring, telemetria emessa
- [ ] Concorrenza: lock per player/party durante resolve

### TemplateArenaBuilder (Transazionale)
- [ ] Implementare architettura modulare:
  - `ChunkLoader` - forza caricamento chunk prima del build
  - `FloorBuilder` - costruisce pavimento
  - `WallBuilder` - costruisce pareti/barriere
  - `SpawnSlotValidator` - valida slot (aria sopra, solido sotto)
  - `HazardPlacer` - posiziona hazard
  - `BuildTelemetry` - metriche build

- [ ] Implementare build transazionale:
  - `beginTransaction()` - inizia tracking blocchi
  - `ensureChunksLoaded()` - attende chunk FULL con timeout 10s
  - `buildFloor()`, `buildWalls()`, `buildSpawnSlots()`, `placeHazards()`
  - `validateFinal()` - verifica finale
  - `commit()` - conferma build
  - `rollback()` - rimuove TUTTI i blocchi piazzati su failure

- [ ] Implementare dry-run validation:
  ```java
  BuildValidation { valid, blocksRequired, chunksRequired, estimatedMs, warnings, errors }
  ```

- [ ] Budget blocchi/tempo con alert se superato (default: maxBlocks=8000, maxBuildTimeMs=5000; override per template)
- [ ] Eventi telemetria `arena.build.start|end|fail|rollback`
- [ ] Owner: Core Dev · Durata: 1.5g · Done: rollback testato, alert su budget, telemetria JSON
- [ ] Concorrenza: lock per template durante build (no double-build stesso template+instance); opzionale prefetch chunk/prebuild queue

### Integrazione
- [ ] Integrare PolicyResolver+Builder in `EnduranceQuestManager.prepareArenaForParty`
- [ ] Integrare PolicyResolver+Builder in `startQuestInInstanceDimension`
- [ ] Salvare `templateId`, `templateVersion`, `policyId`, `policyVersion` in session
- [ ] Restituire `ArenaHandle { arenaId, instanceId, templateId, templateVersion, policyId, policyVersion, bounds }`
- [ ] Owner: Core Dev · Durata: 0.5g · Done: session contiene template+policy, ArenaHandle completo

### ResolvedArena Record
```java
public record ResolvedArena(
    ArenaTemplate template,  // L1 - layout
    ArenaPolicy policy       // L2 - gameplay rules
) {}
```

### Telemetria e Logging
- [ ] Propagare `templateId`/`templateVersion`/`policyId`/`policyVersion`/`instanceId` in tutti gli eventi
- [ ] Log separato `logs/arena-template-*.log` (JSON line: {ts, level, templateId, policyId, instanceId, arenaId, phase, msg, error})
- [ ] Mappare spawn distribution: usa `spawnSlots` se presenti, altrimenti fallback distribuito
- [ ] Metriche baseline: emettere `build_ms`, `entities_residual`, `blocks_residual` con lo stesso perimetro di misurazione pre-v2.1 (start/stop identici) per confronti storici
- [ ] Validare che le metriche baseline (`build_ms`, `entities_residual`, `blocks_residual`) mantengano la stessa definizione/metodo di misura pre/post v2.1 e che i dati storici restino confrontabili (telemetria/NDJSON)
- [ ] Owner: Core Dev · Durata: 0.25g · Done: eventi visibili in telemetria, log file creato
- [ ] Alert channel: warn su budget overrun; error su fail/rollback; output in log + telemetry

### Persistence Layer (NUOVO - sfrutta NDJSON + DuckDB esistenti)
- [ ] Nuovo file NDJSON: `endurance_templates.ndjson` con eventi:
  - `{ts, type: "template_build", templateId, templateVersion, build_ms, result, rollback_count, entities_residual, blocks_residual}`
  - `{ts, type: "template_used", templateId, templateVersion, questId, waveNumber, outcome}`
  - `{ts, type: "template_cleanup", templateId, instanceId, cleanup_ms, residuals}`
- [ ] DuckDB table schema `arena_template_builds`:
  ```sql
  CREATE TABLE arena_template_builds (
    id UUID PRIMARY KEY,
    template_id VARCHAR NOT NULL,
    template_version INT NOT NULL,
    instance_id UUID,
    build_ms INT,
    result VARCHAR, -- 'success', 'fail', 'rollback'
    blocks_placed INT,
    entities_residual INT,
    blocks_residual INT,
    created_at TIMESTAMP DEFAULT NOW()
  );
  CREATE INDEX idx_template_builds_template ON arena_template_builds(template_id, template_version);
  ```
- [ ] DuckDB table schema `arena_template_usage`:
  ```sql
  CREATE TABLE arena_template_usage (
    id UUID PRIMARY KEY,
    template_id VARCHAR NOT NULL,
    template_version INT NOT NULL,
    quest_id UUID,
    player_id UUID,
    wave_reached INT,
    outcome VARCHAR, -- 'complete', 'fail', 'abandon'
    duration_ms INT,
    created_at TIMESTAMP DEFAULT NOW()
  );
  ```
- [ ] Retention policy: 30 giorni per build data, 90 giorni per usage data
- [ ] Owner: Core Dev · Durata: 0.5g · Done: tabelle create, NDJSON scritto, retention applicata

### Instance System Integration (NUOVO - sfrutta RecoverySystem esistente)
- [ ] Estendere `PlayerInstanceSnapshot` con campi template:
  - `templateId`, `templateVersion`, `templateConfig` (serialized)
- [ ] Instance naming scheme: `instance_<templateId>_<shortSessionId>`
- [ ] Recovery include template info per restore completo
- [ ] ArenaHandle come return type standard:
  ```java
  public record ArenaHandle(
    UUID arenaId,
    UUID instanceId,
    String templateId,
    int templateVersion,
    String policyId,
    int policyVersion,
    AABB bounds,
    List<BlockPos> spawnSlots
  ) {}
  ```
- [ ] Owner: Core Dev · Durata: 0.25g · Done: snapshot include template, recovery testato

---

## Fase 2 – UX / Tooling / Autosmoke (1.5 giorni)

### QuickTestWizard / TestingHub
- [ ] Select template con dropdown + filtro per tag
- [ ] Mostra requisiti: min/max player, size, materiale
- [ ] Preview palette/size
- [ ] Supporta `forceTemplateId` per sessione
- [ ] Owner: UI Dev · Durata: 0.5g · Done: dropdown popolato, filtro tag funzionante, forceTemplateId salvato

### HUD Overlay
- [ ] Mostrare `Template: <id> v<version> (size) | Instance: <short-id>`
- [ ] Messaggi party su template scelto
- [ ] Owner: UI Dev · Durata: 0.25g · Done: overlay visibile in quest, aggiornato su cambio template

### Dev Commands `/devmod arena ...`
- [ ] `list` - tutti i template disponibili
- [ ] `info <id>` - dettagli template
- [ ] `create <id>` - build in test instance
- [ ] `validate <id>` - dry-run con report
- [ ] `reload` - hot-reload da disco (NUOVO)
- [ ] `force <id>` - forza per sessione corrente
- [ ] `metrics <id>` - ultimi N build (tempo, fail, rollback)
- [ ] Owner: Tools Dev · Durata: 0.5g · Done: comandi registrati, permessi tester, output JSON/clear

### Autosmoke
- [ ] Cicla template con `tags.contains("smoke")`
- [ ] Usa `forceTemplateId` per ogni run
- [ ] Export CSV/JSON con metriche:
  - build_ms
  - leak_ent (entità residue)
  - leak_blocks (blocchi residui)
  - rollback_count (NUOVO)
  - wave_start_ok
  - fail_reason
- [ ] Assert: `build.fail == 0`, `rollback_count == 0`, `residui == 0`
- [ ] Owner: QA · Durata: 0.5g · Done: report generato, assert applicati

### Dashboard
- [ ] Link "Arena metrics" dal pulsante Dashboard
- [ ] Viste per-template: build_ms, failure_rate, rollback_rate
- [ ] Owner: Tools Dev · Durata: 0.5g · Done: link attivo, viste base funzionanti

### Dashboard Analytics Endpoints (NUOVO - sfrutta DASHBOARD_UPGRADE_PLAN)
- [ ] Endpoint `/api/analytics/arena/build-metrics`:
  - Query params: `templateId`, `templateVersion`, `from`, `to`
  - Response: `{ avg_build_ms, failure_rate, rollback_rate, total_builds, by_day[] }`
- [ ] Endpoint `/api/analytics/arena/performance`:
  - Query params: `templateId`, `from`, `to`
  - Response: `{ avg_ttk, avg_kps, avg_dtps, completion_rate, by_template[] }`
- [ ] Endpoint `/api/analytics/arena/spawn-heatmap`:
  - Query params: `templateId`
  - Response: `{ positions: [{x,y,z,count}], grid_size }`
- [ ] Endpoint `/api/analytics/arena/death-heatmap`:
  - Query params: `templateId`
  - Response: `{ positions: [{x,y,z,count,cause}], grid_size }`
- [ ] Endpoint `/api/analytics/arena/wave-correlation`:
  - Query params: `templateId`
  - Response: `{ wave_fail_rate[], spawn_issues[], by_wave[] }`
- [ ] Integrazione con DuckDB query builder esistente
- [ ] Owner: Tools Dev · Durata: 0.5g · Done: endpoint funzionanti, query ottimizzate

### Dashboard Frontend (NUOVO - sfrutta Chart.js esistente)
- [ ] Tab "Arena Template" nel dashboard principale
- [ ] Dropdown filtro per template selection
- [ ] Chart.js grafici:
  - Line chart: build_ms trend over time
  - Bar chart: failure_rate per template
  - Pie chart: usage distribution per template
  - Heatmap: death/spawn positions (se canvas supportato)
- [ ] Export CSV/JSON/PNG per ogni grafico
- [ ] Owner: Tools Dev · Durata: 0.5g · Done: tab visibile, grafici caricati, export funzionante

---

## Fase 3 – Hardening / Validator avanzato (2 giorni)

### Validazioni avanzate
- [ ] Chunk loaded completamente prima di build
- [ ] SpawnSlots: aria sopra, blocco solido sotto
- [ ] SpawnSlots: dentro bounds arena, no duplicati, non intersecano forbiddenZones
- [ ] ForbiddenZones: nessun spawn in zone vietate
- [ ] Hazard: posizionamento corretto
- [ ] Bounds: senza gap, ceiling presente, barriere complete
- [ ] Owner: Core Dev · Durata: 0.5g · Done: tutti i check passano su template validi, warning su invalidi

### Limiti di sicurezza
- [ ] Max template size: 256 blocchi
- [ ] Max hazards: 50
- [ ] Max spawn slots: 100
- [ ] Max build time: configurabile per template
- [ ] Max blocks: configurabile per template
- [ ] Owner: Core Dev · Durata: 0.25g · Done: limiti applicati, errore chiaro su violazione

### Gate "instance-only"
- [ ] Errore chiaro se flag `useInstanceDimensions` off
- [ ] Fallback solo in debug mode
- [ ] Rimuovere percorsi overworld residui
- [ ] Owner: Core Dev · Durata: 0.25g · Done: gate attivo, log chiaro su tentativo legacy

### Cleanup robusto
- [ ] Conteggio blocchi modificati (dal transaction tracker)
- [ ] Conteggio entità residue
- [ ] Telemetria `arena.cleanup.start|end` con residui
- [ ] Alert se residui > 0
- [ ] Owner: Core Dev · Durata: 0.25g · Done: cleanup completo, telemetria emessa, alert funzionante

### Performance budget
- [ ] Alert/log se build_ms > soglia template (default 5s salvo override)
- [ ] Alert/log se blocchi > soglia template (default 8000 salvo override)
- [ ] Monitor MSPT/TPS durante build:
  - Hook su `MinecraftServer.getAverageTickTimeNanos()` pre/post build
  - Log se delta > 20ms o TPS scende sotto 18
  - Opzione `buildPriority: "async"` per template grandi (build distribuito su più tick)
- [ ] Progress overlay opzionale per build lente
- [ ] Owner: Core Dev · Durata: 0.5g · Done: alert emessi, MSPT monitorato, async build funzionante

### Recovery
- [ ] Serializzare `templateId` + `templateVersion` nel session state
- [ ] Restore su disconnect con template info
- [ ] Chiudere istanza immediatamente se build fail
- [ ] Owner: Core Dev · Durata: 0.25g · Done: recovery testato, istanza chiusa su failure

### Test casi edge
- [ ] Build parziale (failure a metà) → rollback completo
- [ ] Chunk timeout → failure con cleanup
- [ ] Template malformato → fallback + warning
- [ ] Concorrenza: 2 party richiedono stesso template
- [ ] Owner: QA · Durata: 0.5g · Done: tutti i test edge passano

---

## Fase 4 – Migrazione / Rollout controllato (2 giorni)

### Migrazione call-site
- [ ] Mappare ogni uso di `ArenaManager.createArena`
- [ ] Migrare a `TemplateArenaBuilder`
- [ ] Flag debug per legacy fallback
- [ ] Owner: Core Dev · Durata: 0.5g · Done: zero call-site legacy rimasti, flag funzionante

### Rilascio template
- [ ] Rilasciare 3+ template iniziali dietro feature flag
- [ ] Abilitare routing per mob prioritari (boss/melee/ranged)
- [ ] Configurazione routing per quest type
- [ ] Owner: Core Dev · Durata: 0.25g · Done: template attivi, routing funzionante

### Testing finale
- [ ] Autosmoke completo su tutti i template smoke
- [ ] `INSTANCE_SYSTEM_MANUAL_TEST_CHECKLIST` con colonne:
  - templateId
  - templateVersion
  - build_ms
  - entities_residual
  - blocks_residual
  - rollback_count
- [ ] Test single player
- [ ] Test party (2-4 giocatori)
- [ ] Owner: QA · Durata: 0.5g · Done: checklist completa, zero failure

### Monitoring 48h
- [ ] Dashboard per-template attiva
- [ ] Metriche: build fail rate, rollback rate
- [ ] Metriche: TTK/KPS/DTPS per template
- [ ] Metriche: death heatmap, spawn fail
- [ ] Alert su anomalie
- [ ] Owner: QA + Core Dev · Durata: 0.5g · Done: dashboard live, alert configurati

### Rollback plan
- [ ] Flag per disattivare template routing
- [ ] Tornare a default instance arena se necessario
- [ ] Documentare procedura rollback
- [ ] Owner: Core Dev · Durata: 0.25g · Done: procedura documentata e testata

### Fallback chain su build failure
1. Rollback blocchi piazzati
2. Log `arena.build.fail` con motivo
3. Retry con `default_flat_64` (max 1 retry)
4. Se anche default fallisce → abort quest con messaggio player
5. Telemetria `arena.build.abort` con stack trace
- [ ] Owner: Core Dev · Durata: incluso in Builder · Done: chain testata end-to-end

---

## Integrazione sistemi (wave/boss/perk/reward/telemetry)

### Wave/Boss System (ESTESO - sfrutta WaveManager esistente)
- [ ] Usare `spawnSlots` per seed spawn distribution:
  - Se template ha spawnSlots definiti → usa coordinate fixed
  - Tag `melee` → spawn vicino al player
  - Tag `ranged` → spawn lontano dal player
  - Tag `corner` → spawn agli angoli
  - Altrimenti fallback a distribuito random esistente
- [ ] Tag `boss` per instradare a boss-specific template
- [ ] SpawnSlotValidator integration con WaveManager:
  - Verifica slot valido prima di spawn
  - Telemetria su spawn failure per slot invalido
  - Fallback a random se slot non disponibile
- [ ] Log phase con `templateId` + `templateVersion`
- [ ] Heatmap spawn positions per template (riusa movement tracking)
- [ ] Owner: Core Dev · Durata: 0.5g · Done: spawn usa slots, validator integrato, boss routing attivo

### Mutator/Perk System (ESTESO - bindings da Policy L2)
- [ ] Leggere bindings da `policy.perkBindings` e `policy.mutatorBindings`:
  ```json
  // In arena_policies/boss_ring_80_ranked.policy.json
  "perkBindings": {
    "suggested": ["shield_start", "lifesteal"],
    "excluded": ["glass_cannon"],
    "required": []
  },
  "mutatorBindings": {
    "suggested": ["ranged_boost", "speed_up"],
    "excluded": ["melee_only"],
    "required": ["boss_enrage"]
  }
  ```
- [ ] `required` forza applicazione automatica (non può essere deselezionato)
- [ ] `excluded` rimuove dalla pool di selezione
- [ ] `suggested` evidenzia in UI ma non forza
- [ ] Telemetria per correlare outcome per policy+mutator/perk
- [ ] UI mostra suggerimenti per policy nel perk selection screen
- [ ] Per-policy perk winrate analysis
- [ ] Owner: Core Dev · Durata: 0.25g · Done: binding funzionante, telemetria emessa, UI aggiornata

### Reward/Gamification (ESTESO - sfrutta GamificationManager esistente)
- [ ] Badge "Template explorer" (X template unici completati):
  - COMMON: 3 template
  - UNCOMMON: 5 template
  - RARE: 10 template
  - EPIC: tutti i template
- [ ] Badge "Smoke ranger" (tutti i template con tag `smoke:true` passati)
- [ ] Badge "Arena Master" (completa ogni template almeno 3 volte)
- [ ] Badge "Template Speedrunner" (completa qualsiasi template sotto il par time)
- [ ] Owner: Tools Dev · Durata: 0.25g · Done: badge registrati in GamificationManager

### Reward System Integration (NUOVO - modifiers da Policy L2)
- [ ] Leggere modifiers da `policy.rewardModifiers`:
  ```json
  // In arena_policies/boss_ring_80_ranked.policy.json
  "rewardModifiers": {
    "baseMultiplier": 1.5,
    "firstCompletionBonus": 0.25,
    "hazardBonus": 0.1,
    "streakMultiplier": 0.05
  }
  ```
- [ ] Applicare nel reward calculation:
  ```java
  float policyMultiplier = policy.rewardModifiers().baseMultiplier();
  float firstBonus = isFirstCompletion ? policy.rewardModifiers().firstCompletionBonus() : 0;
  float hazardBonus = template.hasHazards() ? policy.rewardModifiers().hazardBonus() : 0;
  finalReward = baseReward * (policyMultiplier + firstBonus + hazardBonus);
  ```
- [ ] Achievement per-policy (es. "First Blood in Boss Ring Ranked")
- [ ] Loot tier boost su template con tag `hazard`: +5% rare drop chance
- [ ] Telemetria `recordCurrencyEarned` con source="policy_<id>"
- [ ] Owner: Core Dev · Durata: 0.25g · Done: modifiers applicati, achievement registrati

### Challenge System (NUOVO - sfrutta Daily/Weekly challenge esistente)
- [ ] Challenge giornaliere template-aware:
  - "Complete 2 quests in different templates"
  - "Reach wave 10 in template X"
  - "Complete a boss template without dying"
- [ ] Challenge settimanali:
  - "Try 5 different templates"
  - "Complete all smoke templates"
  - "Speedrun any 3 templates"
- [ ] Tracking template completion nel player profile
- [ ] Leaderboard colonna "template_coverage" (% template provati)
- [ ] Owner: Tools Dev · Durata: 0.25g · Done: challenge generate, leaderboard aggiornato

### Telemetria (ESTESO - sfrutta TelemetryService 12 sub-services)
- [ ] Propagare `templateId` + `templateVersion` in TUTTI gli eventi esistenti:
  - wave events (WaveManager)
  - death events (con posizione per heatmap)
  - spawn events (con slot usato)
  - perk/mutator picks
  - combat events (DPS, hits, damage)
  - combo/style events
  - boss phase events
  - party events
- [ ] `TelemetryService` room id = templateId/arenaId
- [ ] Correlazione wave fail → template specifiche per balance analysis
- [ ] Heatmap aggregato per template (riusa EnduranceAnalytics.movement tracking)
- [ ] Per-template balance report automatico:
  - Completion rate per template
  - Average wave reached per template
  - DPS/TTK variations per template
  - Perk winrate per template
- [ ] Owner: Core Dev · Durata: 0.5g · Done: tutti gli eventi contengono templateId/version, balance report generato

### Dashboard/Analytics (ESTESO)
- [ ] Endpoint filtro per `templateId` + `templateVersion`
- [ ] Grafici: build_ms over time
- [ ] Grafici: failure_rate, rollback_rate
- [ ] Grafici: death heatmap per template
- [ ] Grafici: spawn fail per template
- [ ] Grafici: TTK/KPS/DTPS per template
- [ ] Trend analysis: build time trends, failure rate trends (7d, 30d, 90d)
- [ ] Export CSV con breakdown per template
- [ ] Owner: Tools Dev · Durata: 0.5g · Done: endpoint live, grafici minimi caricati

---

## Monitoraggio e QA continuo

### Checklist manuale
- [ ] Aggiornare checklist con campi:
  - templateId
  - templateVersion
  - build_ms
  - entities_residual
  - blocks_residual
  - rollback_count
- [ ] Owner: QA · Durata: 0.25g · Done: checklist aggiornata e usata

### Test automatici
- [ ] Unit test: schema validation
- [ ] Unit test: inheritance resolution
- [ ] Unit test: routing scoring
- [ ] Integration test: transactional build
- [ ] Integration test: rollback su failure
- [ ] Integration test: chunk loading timeout
- [ ] Owner: Core Dev · Durata: 0.5g · Done: tutti i test verdi, coverage > 80%

### Autosmoke continuo
- [ ] Esecuzione giornaliera su template `smoke:true`
- [ ] Alert se build fail > 0
- [ ] Alert se rollback_count > 0
- [ ] Alert se residui > 0
- [ ] Report storico per trend
- [ ] Owner: QA · Durata: continuo · Done: pipeline programmata

### Autosmoke - ambiente e scheduling
- [ ] Ambiente: **staging/not-prod** (mai in produzione)
- [ ] Cron schedule: `0 3 * * *` (03:00 daily) - configurabile in ArenaTemplateConfig
- [ ] Destinazione alert:
  - **Console**: WARN/ERROR immediato durante esecuzione
  - **Log file**: `logs/autosmoke-YYYY-MM-DD.json` con dettaglio completo
  - **Dashboard**: badge rosso/verde su tab Arena Template
  - **Slack/Webhook** (opzionale): solo su ERROR, configurabile con `autosmokeWebhookUrl`
- [ ] Report location: `run/autosmoke-reports/YYYY-MM-DD.json`
- [ ] Retention report: 30 giorni
- [ ] Owner: QA · Durata: 0.25g · Done: cron attivo, destinazioni configurate

### Autosmoke - timing
- [ ] Single template smoke: max 30s (build + 1 wave + cleanup)
- [ ] Full smoke suite: max 5 minuti per tutti i template `smoke:true`
- [ ] Timeout per singolo test: 60s → fail + skip
- [ ] Owner: QA · Durata: incluso in Autosmoke · Done: timeout applicati

### Alert e monitoring - canali e soglie
- [ ] **Canali di consumo alert**:
  - `console`: output immediato (WARN giallo, ERROR rosso)
  - `log`: file `logs/arena-alerts.json` (JSON line format)
  - `dashboard`: widget alert su tab Arena Template con history 7 giorni
  - `telemetry`: evento `arena.alert` in EnduranceTelemetryService per analytics
- [ ] **Soglie default** (override in ArenaTemplateConfig):
  | Metrica | WARN | ERROR |
  |---------|------|-------|
  | build_ms | 3000ms | 8000ms |
  | failure_rate (24h) | 5% | 15% |
  | rollback_rate (24h) | 2% | 10% |
  | mspt_during_build | 40ms | 50ms |
  | tps_during_build | <18 | <15 |
  | entities_residual | >0 | >5 |
  | blocks_residual | >0 | >10 |
- [ ] **Soglie per template boss/grandi** (applicate se tag `boss` o `large`):
  | Metrica | WARN | ERROR |
  |---------|------|-------|
  | build_ms | 8000ms | 15000ms |
  | mspt_during_build | 45ms | 55ms |
- [ ] Alert per mismatch arena↔instance map
- [ ] Alert per leak entità
- [ ] Alert per build_ms anomalo
- [ ] Log strutturato JSON per debug
- [ ] Owner: Core Dev · Durata: 0.25g · Done: alert configurati, log JSON attivo, soglie applicate

---

## Checklist di sicurezza finale

- [ ] Documentazione API pubblica per `ArenaTemplate`, `TemplateResolver`, `TemplateArenaBuilder`
- [ ] Logging strutturato (JSON) per debug build failures
- [ ] Metriche esportabili per monitoring (build_time, failure_rate, rollback_rate)
- [ ] Limiti di sicurezza applicati: max size 256, max hazards 50, max spawn slots 100
- [ ] Gestione concorrenza: lock per template durante build o pool pre-buildate
- [ ] Versioning in ogni evento telemetria
- [ ] Migration path documentato per template obsoleti
- [ ] Hot-reload testato senza memory leak
- [ ] Rollback testato con failure simulata a ogni step del build
- [ ] Chunk loading timeout testato
- [ ] Cleanup residui verificato con autosmoke

---

## Concurrency Model (NUOVO)

### Template Build Concurrency
- [ ] Lock strategy per template durante build:
  - `ConcurrentHashMap<String, ReentrantLock>` per templateId
  - Timeout 30s per acquisizione lock
  - Log su contention (telemetria `arena.build.contention`)
- [ ] Rate limiting: max 3 build concorrenti per server (configurabile)
- [ ] Owner: Core Dev · Durata: 0.25g · Done: lock strategy implementata

### Prebuild Pool (OPZIONALE - per produzioni grandi)
- [ ] **Quando abilitare**: solo se server con >50 player concorrenti o >10 quest/minuto
- [ ] **Impatto risorse**:
  - Memoria: ~5MB per istanza prebuildata (dimensione chunk + metadata)
  - CPU: background thread a bassa priorità, ~1% CPU idle
  - Chunk: mantiene chunk loaded per pool → aumenta memoria mondo
- [ ] **Configurazione**:
  ```json
  "prebuildPoolEnabled": false,  // default: disabilitato
  "prebuildPoolConfig": {
    "default_flat_64": 2,  // mantieni 2 istanze pronte
    "boss_ring_80": 1      // mantieni 1 istanza pronta
  },
  "prebuildPoolMaxTotal": 5,     // max istanze totali in pool
  "prebuildPoolRefreshInterval": 300000  // 5 minuti tra refresh
  ```
- [ ] Background thread che:
  - Monitora pool size
  - Rebuilda se sotto soglia
  - Cleanup istanze vecchie (>10 minuti unused)
- [ ] Fallback a on-demand se pool esaurito
- [ ] Metriche pool: `arena.pool.size`, `arena.pool.hit`, `arena.pool.miss`
- [ ] Owner: Core Dev · Durata: 0.5g · Done: pool funzionante, metriche emesse
- [ ] **Nota**: Skip per produzioni piccole (<20 player) - overhead non giustificato

### Resolve Concurrency
- [ ] Lock per player/party durante resolve:
  - Evita race su `forceTemplateId`
  - Timeout 5s
- [ ] Atomic update di session state
- [ ] Owner: Core Dev · Durata: incluso in Resolver · Done: no race condition

---

## Migration Script (NUOVO)

### Legacy Path Migration
- [ ] Script per identificare tutti i call-site di `ArenaManager.createArena`:
  ```bash
  grep -r "ArenaManager.createArena" --include="*.java" src/
  ```
- [ ] Migration checklist per ogni call-site:
  - [ ] Sostituire con `TemplateArenaBuilder.build()`
  - [ ] Passare `ArenaHandle` invece di `Arena`
  - [ ] Aggiornare telemetria chiamante
- [ ] Feature flag per rollback a legacy:
  ```java
  if (!ArenaTemplateConfig.isEnabled()) {
    return legacyArenaManager.createArena(...);
  }
  ```
- [ ] Deprecation warning su `ArenaManager.createArena`
- [ ] Owner: Core Dev · Durata: 0.25g · Done: tutti i call-site migrati, warning attivi

---

## Stime totali (AGGIORNATE v2.2)

| Fase | Durata | Note |
|------|--------|------|
| F0 - Spec + Config + Versioning | 3g | +0.5g per Versioning Strategy e Compatibility Check |
| F1 - Registry/Builder + Persistence | 4g | +1g per DuckDB, NDJSON, Instance integration |
| F2 - UX/Tooling + Dashboard | 3g | +1.5g per Dashboard endpoints e frontend |
| F3 - Hardening | 2g | invariato |
| F4 - Rollout + Migration | 2.5g | +0.5g per migration script |
| Integrazione sistemi | 2g | Wave/Perk/Reward/Gamification/Telemetria estesa |
| Concurrency + Prebuild Pool | 0.75g | Lock strategy + pool opzionale |
| Alert/Monitoring setup | 0.5g | Canali, soglie, autosmoke scheduling |
| **Totale** | **17.75g** | ~3.5 settimane lavorative |

### Breakdown per ruolo

| Ruolo | Giorni | Attività principali |
|-------|--------|---------------------|
| Tech Lead | 1g | Schema, spec review, architettura |
| Core Dev | 10g | Registry, Builder, Persistence, Concurrency, Telemetria |
| UI/Tools Dev | 3.5g | Wizard, HUD, Dashboard, Commands |
| QA | 2.75g | Autosmoke, Checklist, Test edge cases |

---

## Capacità del progetto sfruttate (Checklist finale)

| Sistema | Sfruttamento | Sezione TODO |
|---------|--------------|--------------|
| TelemetryService (12 sub-services) | ✅ 100% | Telemetria ESTESO |
| EnduranceTelemetryService | ✅ 100% | Telemetria ESTESO |
| EnduranceAnalytics | ✅ 100% | Balance report |
| GamificationManager (badge/challenge) | ✅ 100% | Reward/Gamification ESTESO |
| RewardSystem (3 currency) | ✅ 100% | Reward System Integration |
| Daily/Weekly Challenges | ✅ 100% | Challenge System |
| TestingHub/QuickTestWizard | ✅ 100% | UX/Tooling |
| HUD Overlays (20+) | ✅ 100% | HUD Overlay |
| Config/Feature Flags | ✅ 100% | ArenaTemplateConfig, Feature Flag Chain |
| NDJSON Persistence | ✅ 100% | Persistence Layer |
| DuckDB Storage | ✅ 100% | Persistence Layer |
| Dashboard (DASHBOARD_UPGRADE_PLAN) | ✅ 100% | Dashboard Analytics Endpoints, Frontend |
| WaveManager | ✅ 100% | Wave/Boss System ESTESO |
| PerkSystem/MutatorSystem | ✅ 100% | Mutator/Perk System ESTESO |
| InstanceManager/RecoverySystem | ✅ 100% | Instance System Integration |
| Dev Commands | ✅ 100% | Dev Commands |
| Autosmoke | ✅ 100% | Autosmoke + scheduling |
| Alert System | ✅ 100% | Canali, soglie, dashboard widget |

---

## Inheritance Test Coverage Matrix (v2.10)

### Test Cases per Inheritance Resolution

| Test Case | Input | Expected | Verifica |
|-----------|-------|----------|----------|
| **Catena valida depth=3** | `child → parent → grandparent` | Merge corretto, tutti i campi ereditati | ✅ depth max rispettato |
| **Catena depth=4 (limite superato)** | `child → p1 → p2 → p3 → p4` | `InheritanceDepthExceededException` | ✅ errore chiaro con chain path |
| **Override lista spawnSlots** | parent: `[A,B]`, child: `[C]` | child: `[C]` (replace, non merge) | ✅ lista sostituita, non appesa |
| **Override lista hazards** | parent: `[lava]`, child: `[spikes]` | child: `[spikes]` | ✅ lista sostituita |
| **Override lista tags** | parent: `[smoke]`, child: `[boss]` | child: `[boss]` | ✅ lista sostituita |
| **Merge lista tags (strategia)** | parent: `[smoke]`, child: `[boss]` + `"tagsMerge": "append"` | child: `[smoke, boss]` | ✅ merge append se richiesto |
| **Campo opzionale assente** | parent ha `hazards`, child no | child eredita `hazards` da parent | ✅ fallback corretto |
| **Campo opzionale esplicito null** | parent: `{ceiling: stone}`, child: `{ceiling: null}` | child: `{ceiling: null}` (override) | ✅ null esplicito vince |
| **Campo required mancante** | child non ha `size`, parent nemmeno | `RequiredFieldMissingException` | ✅ errore chiaro |
| **Parent mancante** | `"parent": "nonexistent_template"` | `ParentNotFoundException` + template id | ✅ errore non-catastrofico |
| **Circular inheritance** | `A → B → A` | `CircularInheritanceException` | ✅ detection loop |
| **Override campo primitivo** | parent: `size: 64`, child: `size: 80` | child: `size: 80` | ✅ override semplice |
| **Override oggetto nested** | parent: `{floor: {block: stone}}`, child: `{floor: {block: dirt}}` | child: `{floor: {block: dirt}}` | ✅ deep merge o replace |

### Codice Test Inheritance
```java
public class InheritanceResolutionTest {

    @Test
    void testValidChainDepth3() {
        // Setup: grandparent → parent → child (depth=3)
        Map<String, ArenaTemplate> registry = Map.of(
            "grandparent", template("grandparent", null, 64, List.of("base")),
            "parent", template("parent", "grandparent", 72, null),
            "child", template("child", "parent", null, List.of("smoke"))
        );

        ArenaTemplate resolved = resolver.resolve("child", registry);

        assertThat(resolved.size()).isEqualTo(72);  // inherited from parent
        assertThat(resolved.tags()).containsExactly("smoke");  // overridden, not merged
    }

    @Test
    void testDepthExceeded() {
        // Setup: chain of 5 templates (exceeds max depth=3)
        Map<String, ArenaTemplate> registry = chainOfDepth(5);

        assertThatThrownBy(() -> resolver.resolve("child", registry))
            .isInstanceOf(InheritanceDepthExceededException.class)
            .hasMessageContaining("Inheritance chain exceeds max depth 3")
            .hasMessageContaining("child → p1 → p2 → p3");
    }

    @Test
    void testListOverride_NotMerge() {
        // Verify lists are REPLACED, not merged
        ArenaTemplate parent = template("parent", null, 64, null)
            .withSpawnSlots(List.of(pos(0,0,0), pos(1,0,0)));
        ArenaTemplate child = template("child", "parent", null, null)
            .withSpawnSlots(List.of(pos(5,0,5)));

        ArenaTemplate resolved = resolver.resolve("child", Map.of("parent", parent, "child", child));

        assertThat(resolved.spawnSlots()).hasSize(1);  // NOT 3
        assertThat(resolved.spawnSlots().get(0)).isEqualTo(pos(5,0,5));
    }

    @Test
    void testOptionalFieldInherited() {
        // Child doesn't specify hazards → inherits from parent
        ArenaTemplate parent = template("parent", null, 64, null)
            .withHazards(List.of(hazard("lava", pos(10,0,10))));
        ArenaTemplate child = template("child", "parent", 80, null);  // no hazards

        ArenaTemplate resolved = resolver.resolve("child", Map.of("parent", parent, "child", child));

        assertThat(resolved.hazards()).hasSize(1);
        assertThat(resolved.hazards().get(0).type()).isEqualTo("lava");
    }

    @Test
    void testMissingParent() {
        ArenaTemplate child = template("child", "nonexistent", 64, null);

        assertThatThrownBy(() -> resolver.resolve("child", Map.of("child", child)))
            .isInstanceOf(ParentNotFoundException.class)
            .hasMessageContaining("nonexistent")
            .hasMessageContaining("referenced by 'child'");
    }

    @Test
    void testCircularInheritance() {
        Map<String, ArenaTemplate> registry = Map.of(
            "a", template("a", "b", 64, null),
            "b", template("b", "a", 64, null)
        );

        assertThatThrownBy(() -> resolver.resolve("a", registry))
            .isInstanceOf(CircularInheritanceException.class)
            .hasMessageContaining("Circular inheritance detected: a → b → a");
    }
}
```

### Checklist Test Inheritance
- [ ] Test catena valida depth=3: merge corretto, campi ereditati
- [ ] Test catena depth=4: `InheritanceDepthExceededException` con chain path
- [ ] Test override lista (spawnSlots, hazards, tags): replace, non merge
- [ ] Test merge lista con strategia esplicita `"tagsMerge": "append"`
- [ ] Test campo opzionale assente: eredita da parent
- [ ] Test campo opzionale esplicito `null`: override vince
- [ ] Test campo required mancante: `RequiredFieldMissingException`
- [ ] Test parent mancante: `ParentNotFoundException` con ID
- [ ] Test circular inheritance: `CircularInheritanceException` con loop path
- [ ] Test override campo primitivo: valore child vince
- [ ] Coverage target: 100% su `InheritanceResolver.resolve()`
- [ ] Owner: Core Dev · Durata: 0.25g · Done: tutti i test passano

---

## Baseline Metrics Consistency Strategy (v2.10)

### Problema
Le metriche `build_ms`, `entities_residual`, `blocks_residual` esistono già nel sistema pre-v2.1. Per garantire confronti storici accurati, devono essere misurate **nello stesso modo** pre e post migrazione.

### Punti di Misurazione Pre-v2.1 (legacy)

| Metrica | Start | Stop | Calcolo |
|---------|-------|------|---------|
| `build_ms` | Prima chiamata `ArenaManager.createArena()` | Dopo ultimo `setBlock()` | `stop - start` |
| `entities_residual` | N/A | Post-cleanup, conta entità in bounds | `countEntitiesInAABB(bounds)` |
| `blocks_residual` | N/A | Post-cleanup, conta blocchi non-aria in bounds | `countNonAirBlocks(bounds) - expectedAirBlocks` |

### Punti di Misurazione Post-v2.1 (template system)

| Metrica | Start | Stop | Calcolo | Compatibilità |
|---------|-------|------|---------|---------------|
| `build_ms` | Prima `TransactionalArenaBuilder.build()` | Dopo `transaction.commit()` | `stop - start` | ✅ Stesso perimetro: prima API build → dopo ultimo blocco |
| `entities_residual` | N/A | Post-cleanup, conta entità in bounds | `countEntitiesInAABB(template.bounds)` | ✅ Stesso metodo, bounds da template |
| `blocks_residual` | N/A | Post-cleanup | `countNonAirBlocks(bounds) - template.expectedBlockCount()` | ✅ Stesso metodo, expected da template |

### Garanzia Compatibilità

```java
public class MetricsCompatibilityLayer {

    /**
     * Misura build_ms con lo stesso perimetro pre e post v2.1.
     * IMPORTANTE: start PRIMA di qualsiasi operazione build,
     *             stop DOPO l'ultimo blocco piazzato (non dopo cleanup).
     */
    public record BuildMetrics(
        long build_ms,
        int blocksPlaced,
        int chunksLoaded
    ) {}

    public BuildMetrics measureBuild(Runnable buildAction) {
        long start = System.nanoTime();  // SAME: prima di build
        buildAction.run();
        long stop = System.nanoTime();   // SAME: dopo ultimo blocco

        return new BuildMetrics(
            (stop - start) / 1_000_000,  // ms
            transaction.getBlocksPlaced(),
            chunksLoaded.size()
        );
    }

    /**
     * Misura residui con lo stesso metodo pre e post v2.1.
     * IMPORTANTE: chiamare DOPO cleanup completo.
     */
    public record ResidualMetrics(
        int entities_residual,
        int blocks_residual
    ) {}

    public ResidualMetrics measureResiduals(AABB bounds, int expectedNonAirBlocks) {
        // SAME: conta entità vive in bounds
        int entities = level.getEntitiesOfClass(Entity.class, bounds).size();

        // SAME: conta blocchi non-aria
        int nonAirBlocks = countNonAirBlocks(bounds);
        int blocksResidual = nonAirBlocks - expectedNonAirBlocks;

        return new ResidualMetrics(entities, blocksResidual);
    }

    private int countNonAirBlocks(AABB bounds) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(bounds.min(), bounds.max())) {
            if (!level.getBlockState(pos).isAir()) {
                count++;
            }
        }
        return count;
    }
}
```

### Telemetria Compatibile

```java
// Pre-v2.1 event format (MANTENERE per compatibilità)
{
    "type": "arena.build.end",
    "build_ms": 1234,
    "entities_residual": 0,
    "blocks_residual": 0
}

// Post-v2.1 event format (ESTENSIONE, non breaking)
{
    "type": "arena.build.end",
    "build_ms": 1234,              // SAME field name and meaning
    "entities_residual": 0,        // SAME field name and meaning
    "blocks_residual": 0,          // SAME field name and meaning
    // NUOVI campi (additivi, non breaking)
    "templateId": "boss_ring_80",
    "templateVersion": 2,
    "blocksPlaced": 4096,
    "chunksLoaded": 4,
    "transactionId": "uuid"
}
```

### Test Compatibilità Metriche

```java
public class MetricsCompatibilityTest {

    @Test
    void testBuildMsConsistency() {
        // Scenario: stesso arena, misurato con legacy e template system

        // Legacy measurement
        long legacyStart = System.nanoTime();
        legacyArenaManager.createArena(size, material);
        long legacyMs = (System.nanoTime() - legacyStart) / 1_000_000;

        // Template measurement (same arena spec)
        BuildMetrics templateMetrics = metricsLayer.measureBuild(() ->
            templateBuilder.build(defaultTemplate)
        );

        // Tolerance: ±10% (account for minor timing differences)
        assertThat(templateMetrics.build_ms())
            .isCloseTo(legacyMs, within(legacyMs * 0.10));
    }

    @Test
    void testResidualsConsistency() {
        // Scenario: cleanup e misura residui identica

        // Legacy residual measurement
        legacyCleanup.cleanup(bounds);
        int legacyEntities = countEntitiesInAABB(bounds);
        int legacyBlocks = countNonAirBlocks(bounds) - expectedBlocks;

        // Template residual measurement
        templateCleanup.cleanup(bounds);
        ResidualMetrics templateResiduals = metricsLayer.measureResiduals(bounds, expectedBlocks);

        // Must be IDENTICAL (same counting method)
        assertThat(templateResiduals.entities_residual()).isEqualTo(legacyEntities);
        assertThat(templateResiduals.blocks_residual()).isEqualTo(legacyBlocks);
    }

    @Test
    void testTelemetryBackwardsCompatible() {
        // Ensure old dashboard queries still work
        JsonObject event = telemetryCaptor.getLastEvent("arena.build.end");

        // Required fields for legacy compatibility
        assertThat(event.has("build_ms")).isTrue();
        assertThat(event.has("entities_residual")).isTrue();
        assertThat(event.has("blocks_residual")).isTrue();

        // Types must match legacy
        assertThat(event.get("build_ms").isNumber()).isTrue();
        assertThat(event.get("entities_residual").isNumber()).isTrue();
        assertThat(event.get("blocks_residual").isNumber()).isTrue();
    }
}
```

### Checklist Baseline Metrics
- [ ] Documentare punti di misurazione legacy (pre-v2.1) per riferimento
- [ ] Implementare `MetricsCompatibilityLayer` con stessi punti start/stop
- [ ] `build_ms`: start prima di build API, stop dopo ultimo blocco
- [ ] `entities_residual`: conteggio entità in bounds post-cleanup
- [ ] `blocks_residual`: conteggio blocchi non-aria vs expected
- [ ] Telemetria: mantenere field names identici (`build_ms`, `entities_residual`, `blocks_residual`)
- [ ] Telemetria: nuovi campi additivi (templateId, version, etc.) non breaking
- [ ] Test A/B: stesso arena, legacy vs template, tolerance ±10% su build_ms
- [ ] Test residuals: conteggio identico pre/post migration
- [ ] Dashboard queries esistenti: verificare compatibilità (no breaking changes)
- [ ] Retention metriche: 30 giorni per poter confrontare trend pre/post
- [ ] Owner: Core Dev · Durata: 0.25g · Done: layer implementato, test passano, telemetria compatibile

---

## Residual Definition & Scope (v2.11)

### Definizione "Residuo"

| Metrica | Perimetro | Definizione | Cosa conta |
|---------|-----------|-------------|------------|
| `entities_residual` | **Arena bounds (AABB)** | Entità vive in bounds post-cleanup | Mob, item, projectile, armor stand, etc. Esclude: player, marker entity |
| `blocks_residual` | **Arena bounds (AABB)** | Blocchi non-aria oltre l'expected | `countNonAir(bounds) - template.expectedBlockCount()` |
| `chunks_dirty` | **Chunk touched** | Chunk modificati non ripristinati | Solo per debug, non metrica primaria |

### Perché bounds e non instance intera?

1. **Precisione**: l'instance può contenere più arene (futuro multi-arena)
2. **Isolamento**: non contaminiamo la metrica con entità/blocchi fuori arena
3. **Comparabilità**: pre-v2.1 usava bounds, manteniamo coerenza

### Entity Filter

```java
public class ResidualCounter {

    private static final Set<EntityType<?>> EXCLUDED_TYPES = Set.of(
        EntityType.PLAYER,
        EntityType.MARKER,
        EntityType.AREA_EFFECT_CLOUD  // spawned by potions, auto-decay
    );

    public int countEntityResiduals(Level level, AABB bounds) {
        return (int) level.getEntitiesOfClass(Entity.class, bounds, e ->
            !EXCLUDED_TYPES.contains(e.getType()) &&
            e.isAlive() &&
            !e.isRemoved()
        ).size();
    }

    public int countBlockResiduals(Level level, AABB bounds, int expectedNonAir) {
        int actual = 0;
        for (BlockPos pos : BlockPos.betweenClosed(
            BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
            BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ)
        )) {
            if (!level.getBlockState(pos).isAir()) {
                actual++;
            }
        }
        return actual - expectedNonAir;
    }
}
```

### Checklist Residual Definition
- [ ] Definire EXCLUDED_TYPES per entity residual (player, marker, area_effect_cloud)
- [ ] Usare bounds AABB, non chunk né instance intera
- [ ] `expectedBlockCount` calcolato da template (floor + walls + ceiling + hazards)
- [ ] Negative residual = blocchi mancanti (possibile se qualcosa li ha distrutti)
- [ ] Log warning se residual < 0 (anomalia)
- [ ] Owner: Core Dev · Durata: 0.25g · Done: counter implementato, filter applicato

---

## Metrics Destination & Correlation (v2.11)

### Dove finiscono le metriche

| Metrica | Log | Telemetry | NDJSON | DuckDB | Dashboard |
|---------|-----|-----------|--------|--------|-----------|
| `build_ms` | ✅ JSON | ✅ `arena.build.end` | ✅ `template_build` | ✅ `arena_template_builds` | ✅ Chart |
| `entities_residual` | ✅ JSON | ✅ `arena.cleanup.end` | ✅ `template_build` | ✅ `arena_template_builds` | ✅ Alert widget |
| `blocks_residual` | ✅ JSON | ✅ `arena.cleanup.end` | ✅ `template_build` | ✅ `arena_template_builds` | ✅ Alert widget |
| `rollback_count` | ✅ JSON | ✅ `arena.build.rollback` | ✅ `template_build` | ✅ `arena_template_builds` | ✅ Chart |

### Correlation Keys

Ogni evento contiene le chiavi per correlazione:

```java
public record ArenaMetricsContext(
    UUID instanceId,      // dimensione instance
    UUID arenaId,         // arena specifica in instance
    String templateId,    // template usato
    int templateVersion,  // versione template
    String policyId,      // policy applicata (nullable)
    int policyVersion,    // versione policy
    UUID questId,         // quest associata (nullable)
    UUID sessionId        // sessione player (nullable)
) {}

// Ogni evento include questo context
public void emitBuildEnd(ArenaMetricsContext ctx, long buildMs, int blocksPlaced) {
    telemetry.emit("arena.build.end", Map.of(
        "instanceId", ctx.instanceId(),
        "arenaId", ctx.arenaId(),
        "templateId", ctx.templateId(),
        "templateVersion", ctx.templateVersion(),
        "policyId", ctx.policyId(),
        "build_ms", buildMs,
        "blocksPlaced", blocksPlaced
    ));
}
```

### Query Correlation Examples

```sql
-- DuckDB: join build con usage per templateId
SELECT
    b.template_id,
    AVG(b.build_ms) as avg_build_ms,
    AVG(u.wave_reached) as avg_wave,
    COUNT(CASE WHEN b.entities_residual > 0 THEN 1 END) as leak_count
FROM arena_template_builds b
LEFT JOIN arena_template_usage u
    ON b.template_id = u.template_id
    AND b.instance_id = u.quest_id  -- correlazione via instance
WHERE b.created_at > NOW() - INTERVAL '7 days'
GROUP BY b.template_id;

-- Telemetry: correlazione via arenaId
SELECT * FROM telemetry_events
WHERE event_type LIKE 'arena.%'
  AND properties->>'arenaId' = '${arenaId}'
ORDER BY timestamp;
```

### Checklist Metrics Destination
- [ ] Ogni evento include `ArenaMetricsContext` completo
- [ ] Log JSON: path `logs/arena-metrics-YYYY-MM-DD.json`
- [ ] Telemetry: eventi `arena.*` con tutti i correlation keys
- [ ] NDJSON: append a `endurance_templates.ndjson`
- [ ] DuckDB: insert in tabelle con foreign key semantica (non enforced)
- [ ] Dashboard: query con join su templateId + date range
- [ ] Correlation test: dato arenaId, recuperare build + cleanup + usage
- [ ] Owner: Core Dev · Durata: 0.25g · Done: correlation keys in tutti gli eventi

---

## ArenaTemplateConfig Integration (v2.11)

### Integrazione con Config System esistente

```java
public class ArenaTemplateConfig implements ModConfig {

    // === DEFAULT VALUES ===
    private static final ArenaTemplateConfig DEFAULTS = new ArenaTemplateConfig();

    // === FIELDS (with defaults) ===
    @ConfigField(comment = "Directory for template JSON files")
    private String templateDirectory = "data/devmod/arena_templates/";

    @ConfigField(comment = "Enable arena template system")
    private boolean arenaTemplateEnabled = false;

    @ConfigField(comment = "Enable weighted policy routing")
    private boolean routingEnabled = false;

    @ConfigField(comment = "Default max blocks for standard templates")
    @Range(min = 1000, max = 500000)
    private int defaultMaxBlocks = 8000;

    @ConfigField(comment = "Default max build time in ms")
    @Range(min = 1000, max = 60000)
    private int defaultMaxBuildTimeMs = 5000;

    // === ENV OVERRIDE ===
    @Override
    public void applyEnvironmentOverrides() {
        // DEVMOD_ARENA_TEMPLATE_ENABLED=true
        String envEnabled = System.getenv("DEVMOD_ARENA_TEMPLATE_ENABLED");
        if (envEnabled != null) {
            this.arenaTemplateEnabled = Boolean.parseBoolean(envEnabled);
        }

        // DEVMOD_ARENA_MAX_BLOCKS=10000
        String envMaxBlocks = System.getenv("DEVMOD_ARENA_MAX_BLOCKS");
        if (envMaxBlocks != null) {
            this.defaultMaxBlocks = Integer.parseInt(envMaxBlocks);
        }

        // DEVMOD_ARENA_BUILD_TIMEOUT_MS=8000
        String envTimeout = System.getenv("DEVMOD_ARENA_BUILD_TIMEOUT_MS");
        if (envTimeout != null) {
            this.defaultMaxBuildTimeMs = Integer.parseInt(envTimeout);
        }
    }

    // === VALIDATION ===
    @Override
    public ValidationResult validate() {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Path validation
        Path templatePath = Path.of(templateDirectory);
        if (!Files.isDirectory(templatePath)) {
            warnings.add("templateDirectory does not exist: " + templateDirectory);
        }

        // Range validation (enforced by @Range, but double-check)
        if (defaultMaxBlocks < 1000 || defaultMaxBlocks > 500000) {
            errors.add("defaultMaxBlocks out of range [1000, 500000]: " + defaultMaxBlocks);
        }

        if (defaultMaxBuildTimeMs < 1000 || defaultMaxBuildTimeMs > 60000) {
            errors.add("defaultMaxBuildTimeMs out of range [1000, 60000]: " + defaultMaxBuildTimeMs);
        }

        // Logical validation
        if (routingEnabled && !arenaTemplateEnabled) {
            errors.add("routingEnabled=true requires arenaTemplateEnabled=true");
        }

        // Alert thresholds validation
        if (alertThresholds.warnBuildMs >= alertThresholds.errorBuildMs) {
            errors.add("alertThresholds.warnBuildMs must be < errorBuildMs");
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    // === DEFAULTS ACCESS ===
    public static ArenaTemplateConfig defaults() {
        return DEFAULTS;
    }

    // === HOT-RELOAD CALLBACK ===
    @Override
    public void onReload(ArenaTemplateConfig oldConfig) {
        LOGGER.info("ArenaTemplateConfig reloaded: {} → {}", oldConfig, this);
        // Notify listeners
        listeners.forEach(l -> l.onConfigChanged(oldConfig, this));
    }
}
```

### Config Priority

| Priority | Source | Example |
|----------|--------|---------|
| 1 (highest) | Environment variable | `DEVMOD_ARENA_TEMPLATE_ENABLED=true` |
| 2 | Config file override | `config/devmod/arena-template.json` |
| 3 | Datapack override | `data/devmod/config/arena-template.json` |
| 4 (lowest) | Code defaults | `ArenaTemplateConfig.DEFAULTS` |

### Checklist Config Integration
- [ ] Implementare `ModConfig` interface (validate, applyEnvironmentOverrides, onReload)
- [ ] Annotare campi con `@ConfigField`, `@Range`, `@Required`
- [ ] Supportare env override per tutti i flag critici
- [ ] Validare ranges, dependencies (routing requires template enabled)
- [ ] Log warning per config file mancante (usa defaults)
- [ ] Log error per validation failure (non avviare se critico)
- [ ] Owner: Core Dev · Durata: 0.25g · Done: config integrata, validation attiva

---

## Hot-Reload Safety (v2.11)

### Race Condition durante build

**Problema**: se ricarichi config mentre una build è in corso, le soglie cambiano a metà.

**Soluzione**: snapshot config all'inizio del build.

```java
public class TemplateArenaBuilder {

    public ArenaHandle build(ArenaTemplate template, ArenaPolicy policy) {
        // SNAPSHOT config at build start - immutable for entire build
        ArenaTemplateConfig configSnapshot = ArenaTemplateConfig.current().snapshot();

        BuildContext ctx = new BuildContext(template, policy, configSnapshot);

        try {
            // Usa configSnapshot per TUTTE le decisioni
            if (ctx.config().defaultMaxBuildTimeMs() < estimatedBuildTime(template)) {
                return handleBudgetExceeded(ctx);
            }

            transaction.begin();

            // ... build logic uses ctx.config() consistently ...

            transaction.commit();

            // Alert check usa snapshot (coerente con build)
            checkAlerts(ctx, buildMetrics);

            return handle;

        } catch (Exception e) {
            transaction.rollback();
            throw e;
        }
    }
}

// Immutable snapshot
public record ConfigSnapshot(
    int defaultMaxBlocks,
    int defaultMaxBuildTimeMs,
    AlertThresholds alertThresholds
    // ... altri campi
) {
    public static ConfigSnapshot from(ArenaTemplateConfig config) {
        return new ConfigSnapshot(
            config.getDefaultMaxBlocks(),
            config.getDefaultMaxBuildTimeMs(),
            config.getAlertThresholds().copy()
        );
    }
}
```

### Reload Behavior

| Scenario | Behavior |
|----------|----------|
| Reload durante build in corso | Build usa snapshot precedente, nuova config per prossimo build |
| Reload con build queue | Queue usa config al momento dello start di ogni build |
| Reload con prebuild pool | Pool invalida istanze obsolete, rebuilda con nuova config |
| Reload config invalida | Rifiuta reload, mantieni config precedente, log error |

### Safe Reload Implementation

```java
public class ArenaTemplateConfigManager {

    private volatile ArenaTemplateConfig activeConfig;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public void reload(Path configPath) {
        ArenaTemplateConfig newConfig = loader.load(configPath);

        // Validate BEFORE applying
        ValidationResult result = newConfig.validate();
        if (!result.isValid()) {
            LOGGER.error("Config reload rejected: {}", result.errors());
            telemetry.emit("arena.config.reload_failed", result.errors());
            return;  // Keep old config
        }

        // Atomic swap
        lock.writeLock().lock();
        try {
            ArenaTemplateConfig oldConfig = activeConfig;
            activeConfig = newConfig;
            newConfig.onReload(oldConfig);
            telemetry.emit("arena.config.reloaded", Map.of(
                "changes", diffConfigs(oldConfig, newConfig)
            ));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public ArenaTemplateConfig current() {
        lock.readLock().lock();
        try {
            return activeConfig;
        } finally {
            lock.readLock().unlock();
        }
    }

    public ConfigSnapshot snapshot() {
        return ConfigSnapshot.from(current());
    }
}
```

### Checklist Hot-Reload Safety
- [ ] Snapshot config all'inizio di ogni build
- [ ] Build usa snapshot per tutte le decisioni (budget, alert, limits)
- [ ] Reload valida config PRIMA di applicare
- [ ] Reload rifiutato se validation fallisce (mantieni vecchia config)
- [ ] Telemetry su reload success/failure con diff
- [ ] Prebuild pool: invalida istanze se config cambia limiti
- [ ] Test: reload durante build → build completa con vecchia config
- [ ] Test: reload con config invalida → vecchia config mantenuta
- [ ] Owner: Core Dev · Durata: 0.25g · Done: snapshot implementato, test passano

---

## Threshold Calibration (v2.11)

### Ambiente Target

| Parametro | Valore Target | Note |
|-----------|---------------|------|
| Server tickrate | 20 TPS (50ms/tick) | Standard Minecraft |
| MSPT target | < 40ms | Margine 10ms per altri sistemi |
| Hardware reference | 4 core, 8GB RAM | Server Minecraft tipico |
| Player count | 1-20 concurrent | Small-medium server |
| Quest/min peak | 5-10 | Durante prime time |

### Soglie calibrate per ambiente target

| Metrica | WARN | ERROR | Rationale |
|---------|------|-------|-----------|
| `build_ms` (standard) | 3000ms | 8000ms | 60-160 tick budget, accettabile per 64×64 |
| `build_ms` (boss) | 8000ms | 15000ms | 160-300 tick budget, template grandi |
| `mspt_during_build` | 40ms | 50ms | 80-100% tick budget, rischio lag spike |
| `tps_during_build` | <18 | <15 | 10-25% TPS loss, player-noticeable |
| `entities_residual` | >0 | >5 | Qualsiasi leak è warning, 5+ è grave |
| `blocks_residual` | >0 | >10 | Qualsiasi è warning, 10+ indica problema build |

### Calibration per hardware diverso

```java
public class ThresholdCalibrator {

    public AlertThresholds calibrate(ServerProfile profile) {
        AlertThresholds base = AlertThresholds.defaults();

        // Scale by CPU cores
        double cpuFactor = Math.min(2.0, profile.cpuCores() / 4.0);

        // Scale by RAM
        double ramFactor = Math.min(2.0, profile.ramGB() / 8.0);

        // Scale by expected load
        double loadFactor = 20.0 / profile.expectedPlayers();

        double scaleFactor = (cpuFactor + ramFactor + loadFactor) / 3.0;

        return new AlertThresholds(
            (int) (base.warnBuildMs() * scaleFactor),
            (int) (base.errorBuildMs() * scaleFactor),
            base.warnMspt(),  // MSPT non scala (è relativo a tick)
            base.errorMspt(),
            base.warnTps(),   // TPS non scala (è assoluto)
            base.errorTps(),
            base.warnEntitiesResidual(),
            base.errorEntitiesResidual(),
            base.warnBlocksResidual(),
            base.errorBlocksResidual()
        );
    }
}

// Server profiles predefiniti
public enum ServerProfile {
    SMALL(2, 4, 10),    // 2 core, 4GB, 10 players
    MEDIUM(4, 8, 20),   // 4 core, 8GB, 20 players (default)
    LARGE(8, 16, 50),   // 8 core, 16GB, 50 players
    CUSTOM(0, 0, 0);    // Manual config

    public final int cpuCores;
    public final int ramGB;
    public final int expectedPlayers;
}
```

### Benchmark per validare soglie

```java
public class ThresholdBenchmark {

    @Test
    void benchmarkDefaultTemplate() {
        // Setup: default_flat_64 su MEDIUM profile
        ArenaTemplate template = registry.get("default_flat_64").orElseThrow();

        // Warmup
        for (int i = 0; i < 3; i++) {
            builder.build(template);
            cleanup();
        }

        // Measure
        List<Long> buildTimes = new ArrayList<>();
        List<Double> msptDuringBuild = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            long start = System.nanoTime();
            double msptBefore = server.getAverageTickTimeNanos() / 1_000_000.0;

            builder.build(template);

            double msptAfter = server.getAverageTickTimeNanos() / 1_000_000.0;
            long elapsed = (System.nanoTime() - start) / 1_000_000;

            buildTimes.add(elapsed);
            msptDuringBuild.add(msptAfter - msptBefore);

            cleanup();
        }

        // Assert soglie sono calibrate correttamente
        double avgBuildMs = buildTimes.stream().mapToLong(l -> l).average().orElse(0);
        double p95BuildMs = percentile(buildTimes, 0.95);
        double avgMsptDelta = msptDuringBuild.stream().mapToDouble(d -> d).average().orElse(0);

        // WARN threshold should be > p95
        assertThat(AlertThresholds.defaults().warnBuildMs())
            .isGreaterThan((int) p95BuildMs)
            .describedAs("WARN threshold should allow p95 builds without alert");

        // MSPT delta should be < WARN
        assertThat(avgMsptDelta)
            .isLessThan(AlertThresholds.defaults().warnMspt() - 20)
            .describedAs("MSPT during build should have margin before WARN");

        // Log for calibration review
        LOGGER.info("Benchmark results: avg={}ms, p95={}ms, msptDelta={}",
            avgBuildMs, p95BuildMs, avgMsptDelta);
    }
}
```

### Checklist Threshold Calibration
- [ ] Documentare ambiente target (tickrate, hardware, player count)
- [ ] Soglie default calibrate per MEDIUM profile (4 core, 8GB, 20 players)
- [ ] MSPT/TPS thresholds non scalano (sono assoluti rispetto a tick)
- [ ] build_ms scala con CPU/RAM/load
- [ ] Implementare `ThresholdCalibrator` per ambienti diversi
- [ ] Predefinire `ServerProfile` (SMALL, MEDIUM, LARGE, CUSTOM)
- [ ] Benchmark per validare soglie su template reference
- [ ] Documentare come re-calibrare per hardware specifico
- [ ] Config override per soglie custom (per chi ha hardware diverso)
- [ ] Owner: Core Dev · Durata: 0.25g · Done: calibrator implementato, benchmark passano

---

## Autosmoke Scheduler Implementation (v2.12)

### Chi esegue il cron

| Opzione | Pro | Contro | Scelta |
|---------|-----|--------|--------|
| Server main thread | Semplice | Blocca tick, no timezone control | ❌ |
| ScheduledExecutorService dedicato | Isolato, configurabile | Richiede shutdown handling | ✅ **Scelta** |
| Quartz Scheduler | Feature-rich | Overkill, dependency pesante | ❌ |

### Implementazione

```java
public class AutosmokeScheduler {

    private final ScheduledExecutorService executor =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Autosmoke-Scheduler");
            t.setDaemon(true);  // Non blocca shutdown
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });

    private ScheduledFuture<?> scheduledTask;
    private final ZoneId timezone;

    public AutosmokeScheduler(ArenaTemplateConfig config) {
        // Timezone da config, default server timezone
        this.timezone = ZoneId.of(
            config.getAutosmokeTimezone(),
            ZoneId.SHORT_IDS
        );
    }

    public void start() {
        String cronExpr = config.getAutosmokeSchedule();  // "0 3 * * *"
        CronExpression cron = CronExpression.parse(cronExpr);

        scheduleNext(cron);
    }

    private void scheduleNext(CronExpression cron) {
        ZonedDateTime now = ZonedDateTime.now(timezone);
        ZonedDateTime next = cron.next(now);
        long delayMs = Duration.between(now, next).toMillis();

        scheduledTask = executor.schedule(() -> {
            runAutosmoke();
            scheduleNext(cron);  // Reschedule per prossima esecuzione
        }, delayMs, TimeUnit.MILLISECONDS);

        LOGGER.info("Autosmoke scheduled for {} (in {}ms)", next, delayMs);
    }

    public void stop() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);  // Non interrompe se in corso
        }
        executor.shutdown();
    }

    // === SERVER RESTART HANDLING ===
    public void onServerStart() {
        // Riprendi scheduling
        start();

        // Check se era in corso un run interrotto
        AutosmokeState lastState = persistence.loadLastState();
        if (lastState != null && lastState.isIncomplete()) {
            LOGGER.warn("Autosmoke was interrupted. Last run: {}", lastState);
            telemetry.emit("autosmoke.interrupted", lastState.toMap());
            // Non riprendiamo: attendi prossimo schedule
        }
    }

    public void onServerStop() {
        stop();
        // Salva stato se in corso
        if (currentRun != null) {
            persistence.saveState(currentRun.toInterruptedState());
        }
    }
}
```

### Timezone Handling

```java
// Config
public class ArenaTemplateConfig {
    // Cron schedule
    String autosmokeSchedule = "0 3 * * *";  // 03:00 daily

    // Timezone (default: server timezone)
    String autosmokeTimezone = "UTC";  // o "Europe/Rome", "America/New_York"

    // Alternativa: offset da server time
    // String autosmokeTimezone = "SERVER";  // usa ZoneId.systemDefault()
}

// Log con timezone esplicito
LOGGER.info("Autosmoke scheduled: {} {} (server time: {})",
    next.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
    timezone,
    next.withZoneSameInstant(ZoneId.systemDefault())
);
```

### Server Restart Behavior

| Scenario | Behavior |
|----------|----------|
| Restart prima del cron time | Ricalcola delay, esegue al prossimo orario schedulato |
| Restart dopo il cron time (mancato) | **Non** esegue retroattivamente, attende prossimo schedule |
| Restart durante esecuzione | Log interrupted, non riprende, attende prossimo schedule |
| Crash durante esecuzione | Report parziale salvato, telemetria `autosmoke.interrupted` |

### Checklist Autosmoke Scheduler
- [ ] ScheduledExecutorService daemon thread, low priority
- [ ] Timezone configurabile (default UTC o SERVER)
- [ ] CronExpression parsing (usa libreria `cron-utils` o semplice parser)
- [ ] Reschedule dopo ogni esecuzione
- [ ] onServerStart/onServerStop hooks
- [ ] Salva stato se interrotto, log warning al restart
- [ ] Non esegue retroattivamente run mancati
- [ ] Telemetria `autosmoke.scheduled|started|completed|interrupted`
- [ ] Owner: Core Dev · Durata: 0.25g · Done: scheduler funzionante, restart testato

---

## Alert Channels Implementation (v2.12)

### Stato implementazione

| Canale | Implementato? | Classe | Note |
|--------|---------------|--------|------|
| `console` | ✅ Sì | `ConsoleAlertHandler` | Usa LOGGER con colori ANSI |
| `log` | ✅ Sì | `FileAlertHandler` | Append a `logs/arena-alerts.json` |
| `dashboard` | 🔄 Parziale | `DashboardAlertHandler` | Dipende da DashboardWebSocketService |
| `telemetry` | ✅ Sì | `TelemetryAlertHandler` | Usa EnduranceTelemetryService esistente |
| `webhook` | 🔲 Placeholder | `WebhookAlertHandler` | Solo stub, richiede config URL |

### Implementazione concreta

```java
public sealed interface AlertHandler permits
    ConsoleAlertHandler,
    FileAlertHandler,
    DashboardAlertHandler,
    TelemetryAlertHandler,
    WebhookAlertHandler {

    void handle(Alert alert);
    String channelName();
    boolean isEnabled();
}

// === CONSOLE (implementato) ===
public final class ConsoleAlertHandler implements AlertHandler {
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_RESET = "\u001B[0m";

    @Override
    public void handle(Alert alert) {
        String color = alert.severity() == Severity.WARN ? ANSI_YELLOW : ANSI_RED;
        LOGGER.warn("{}[ARENA ALERT] {} - {}{}", color, alert.type(), alert.message(), ANSI_RESET);
    }

    @Override public String channelName() { return "console"; }
    @Override public boolean isEnabled() { return true; }  // sempre attivo
}

// === LOG FILE (implementato) ===
public final class FileAlertHandler implements AlertHandler {
    private final Path logPath = Path.of("logs/arena-alerts.json");

    @Override
    public void handle(Alert alert) {
        String json = GSON.toJson(alert.toMap());
        Files.writeString(logPath, json + "\n", StandardOpenOption.APPEND, StandardOpenOption.CREATE);
    }

    @Override public String channelName() { return "log"; }
    @Override public boolean isEnabled() { return true; }
}

// === DASHBOARD (parziale - dipende da WebSocket) ===
public final class DashboardAlertHandler implements AlertHandler {
    private final DashboardWebSocketService wsService;

    @Override
    public void handle(Alert alert) {
        if (wsService != null && wsService.hasConnectedClients()) {
            wsService.broadcast("arena.alert", alert.toMap());
        }
        // Anche se nessun client connesso, salva in buffer per history
        alertHistoryBuffer.add(alert);  // Ring buffer 7 giorni
    }

    @Override public String channelName() { return "dashboard"; }
    @Override public boolean isEnabled() { return wsService != null; }
}

// === TELEMETRY (implementato - usa esistente) ===
public final class TelemetryAlertHandler implements AlertHandler {
    private final EnduranceTelemetryService telemetry;

    @Override
    public void handle(Alert alert) {
        telemetry.emit("arena.alert", alert.toMap());
    }

    @Override public String channelName() { return "telemetry"; }
    @Override public boolean isEnabled() { return true; }
}

// === WEBHOOK (placeholder) ===
public final class WebhookAlertHandler implements AlertHandler {
    private final String webhookUrl;
    private final HttpClient httpClient;

    @Override
    public void handle(Alert alert) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return;  // Non configurato
        }

        // Solo ERROR (non WARN) per webhook
        if (alert.severity() != Severity.ERROR) {
            return;
        }

        // Async HTTP POST
        CompletableFuture.runAsync(() -> {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(alert.toSlackFormat())))
                .build();
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding());
        });
    }

    @Override public String channelName() { return "webhook"; }
    @Override public boolean isEnabled() { return webhookUrl != null && !webhookUrl.isBlank(); }
}
```

### Alert Router

```java
public class AlertRouter {
    private final List<AlertHandler> handlers;
    private final Set<String> enabledChannels;

    public AlertRouter(ArenaTemplateConfig config) {
        this.enabledChannels = new HashSet<>(config.getAlertChannels());

        this.handlers = List.of(
            new ConsoleAlertHandler(),
            new FileAlertHandler(),
            new DashboardAlertHandler(dashboardService),
            new TelemetryAlertHandler(telemetryService),
            new WebhookAlertHandler(config.getAutosmokeWebhookUrl())
        );
    }

    public void route(Alert alert) {
        handlers.stream()
            .filter(h -> enabledChannels.contains(h.channelName()))
            .filter(AlertHandler::isEnabled)
            .forEach(h -> {
                try {
                    h.handle(alert);
                } catch (Exception e) {
                    LOGGER.error("Alert handler {} failed: {}", h.channelName(), e.getMessage());
                }
            });
    }
}
```

### Checklist Alert Channels
- [ ] `ConsoleAlertHandler`: ANSI colors, sempre attivo
- [ ] `FileAlertHandler`: append JSON, rotation daily
- [ ] `DashboardAlertHandler`: WebSocket broadcast + ring buffer history
- [ ] `TelemetryAlertHandler`: usa EnduranceTelemetryService.emit()
- [ ] `WebhookAlertHandler`: async HTTP POST, solo ERROR, Slack format
- [ ] AlertRouter: filtra per enabledChannels, catch exception per handler
- [ ] Config: `alertChannels: ["console", "log", "dashboard", "telemetry"]`
- [ ] Owner: Core Dev · Durata: 0.25g · Done: tutti i canali implementati

---

## Feature Flag Chain (v2.12)

### Dipendenze hard-coded

```
instanceOnly ──┬── arenaTemplateEnabled ──┬── routingEnabled
               │                          │
               │                          └── gamificationEnabled
               │
               └── (legacy overworld blocked)
```

### Implementazione con dipendenze

```java
public class FeatureFlagChain {

    // Ordine: ogni flag dipende dai precedenti
    private static final List<String> FLAG_ORDER = List.of(
        "instanceOnly",
        "arenaTemplateEnabled",
        "routingEnabled",
        "gamificationEnabled"
    );

    private static final Map<String, List<String>> DEPENDENCIES = Map.of(
        "arenaTemplateEnabled", List.of("instanceOnly"),
        "routingEnabled", List.of("instanceOnly", "arenaTemplateEnabled"),
        "gamificationEnabled", List.of("instanceOnly", "arenaTemplateEnabled")
    );

    public ValidationResult validateFlags(ArenaTemplateConfig config) {
        List<String> errors = new ArrayList<>();

        // Check: arenaTemplateEnabled richiede instanceOnly
        if (config.isArenaTemplateEnabled() && !config.isInstanceOnly()) {
            errors.add("arenaTemplateEnabled=true requires instanceOnly=true");
        }

        // Check: routingEnabled richiede arenaTemplateEnabled
        if (config.isRoutingEnabled() && !config.isArenaTemplateEnabled()) {
            errors.add("routingEnabled=true requires arenaTemplateEnabled=true");
        }

        // Check: gamificationEnabled richiede arenaTemplateEnabled
        if (config.isGamificationEnabled() && !config.isArenaTemplateEnabled()) {
            errors.add("gamificationEnabled=true requires arenaTemplateEnabled=true");
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    public void logFlagState(ArenaTemplateConfig config) {
        LOGGER.info("Feature Flag State:");
        LOGGER.info("  instanceOnly: {} {}", config.isInstanceOnly(),
            config.isInstanceOnly() ? "✓" : "✗ (legacy overworld ALLOWED)");
        LOGGER.info("  arenaTemplateEnabled: {} {}",
            config.isArenaTemplateEnabled(),
            !config.isArenaTemplateEnabled() ? "(template system DISABLED)" : "");
        LOGGER.info("  routingEnabled: {} {}",
            config.isRoutingEnabled(),
            !config.isRoutingEnabled() ? "(weighted scoring DISABLED, uses default)" : "");
        LOGGER.info("  gamificationEnabled: {} {}",
            config.isGamificationEnabled(),
            !config.isGamificationEnabled() ? "(badges/challenges DISABLED)" : "");
    }

    // Runtime check con log esplicito
    public boolean requiresArenaTemplate(String caller) {
        if (!config.isArenaTemplateEnabled()) {
            LOGGER.warn("{} called but arenaTemplateEnabled=false. Using legacy path.", caller);
            return false;
        }
        return true;
    }
}
```

### Single Source of Truth

```java
public class FeatureFlagRegistry {

    // === SINGLE SOURCE OF TRUTH ===
    private volatile FeatureFlagState activeState;

    public record FeatureFlagState(
        boolean instanceOnly,
        boolean arenaTemplateEnabled,
        boolean routingEnabled,
        boolean gamificationEnabled,
        Instant loadedAt,
        String source  // "config", "env_override", "runtime_override"
    ) {}

    public void initialize(ArenaTemplateConfig config) {
        // 1. Load from config file
        FeatureFlagState fromConfig = loadFromConfig(config);

        // 2. Apply env overrides
        FeatureFlagState withEnv = applyEnvOverrides(fromConfig);

        // 3. Validate dependencies
        ValidationResult validation = validateFlags(withEnv);
        if (!validation.isValid()) {
            LOGGER.error("Feature flag validation failed: {}", validation.errors());
            throw new ConfigurationException(validation.errors());
        }

        // 4. Set as active
        this.activeState = withEnv;

        // 5. Log final state
        logFlagState(activeState);
    }

    // Cache for fast access (no lock needed - volatile reference)
    public boolean isInstanceOnly() {
        return activeState.instanceOnly();
    }

    public boolean isArenaTemplateEnabled() {
        return activeState.arenaTemplateEnabled();
    }

    // etc.
}
```

### Log format standard

```java
// Standard log format per flag state
// [FEATURE_FLAGS] state=LOADED source=config instanceOnly=true arenaTemplateEnabled=false ...
public void logFlagState(FeatureFlagState state) {
    LOGGER.info("[FEATURE_FLAGS] state=LOADED source={} instanceOnly={} arenaTemplateEnabled={} routingEnabled={} gamificationEnabled={} loadedAt={}",
        state.source(),
        state.instanceOnly(),
        state.arenaTemplateEnabled(),
        state.routingEnabled(),
        state.gamificationEnabled(),
        state.loadedAt()
    );
}

// Log on dependency violation (during validation)
// [FEATURE_FLAGS] VALIDATION_ERROR: routingEnabled=true requires arenaTemplateEnabled=true
public void logValidationError(String error) {
    LOGGER.error("[FEATURE_FLAGS] VALIDATION_ERROR: {}", error);
}

// Log on runtime check failure
// [FEATURE_FLAGS] BLOCKED caller=PolicyResolver.resolve reason=arenaTemplateEnabled=false
public void logBlockedCall(String caller, String reason) {
    LOGGER.warn("[FEATURE_FLAGS] BLOCKED caller={} reason={}", caller, reason);
}
```

### Checklist Feature Flag Chain
- [ ] Dipendenze hard-coded: routing/gamification richiedono arenaTemplateEnabled
- [ ] arenaTemplateEnabled richiede instanceOnly
- [ ] Validation al load: errore se dipendenze non rispettate
- [ ] Log esplicito perché flag è disabilitato
- [ ] Single source of truth: FeatureFlagRegistry con volatile state
- [ ] Env override applicato dopo config file
- [ ] Log format standard: `[FEATURE_FLAGS] ...`
- [ ] Runtime check con log caller + reason
- [ ] Owner: Core Dev · Durata: 0.25g · Done: chain validata, log chiari

---

## Instance-Only Gate (v2.12)

### Mappatura percorsi legacy overworld

| Percorso | Classe/Metodo | Cosa fa | Post-gate |
|----------|---------------|---------|-----------|
| `ArenaManager.createArenaAtPosition(overworld, pos)` | `ArenaManager` | Crea arena in overworld | ❌ **BLOCCATO** |
| `EnduranceQuestManager.startQuestInOverworld()` | `EnduranceQuestManager` | Legacy single-player | ❌ **BLOCCATO** |
| `QuickTestWizard.spawnArenaHere()` | `QuickTestWizard` | Debug spawn in-place | ⚠️ **AMMESSO in debug** |
| `DevCommand.createTestArena(dimension)` | `DevCommands` | Dev testing | ⚠️ **AMMESSO in debug** |
| `InstanceArenaManager.createInInstance()` | `InstanceArenaManager` | Instance dimension | ✅ **SEMPRE OK** |

### Gate Implementation

```java
public class InstanceOnlyGate {

    public enum GateResult {
        ALLOWED,
        BLOCKED_LEGACY,
        ALLOWED_DEBUG_ONLY
    }

    public GateResult check(ServerLevel level, String caller) {
        // Instance dimension: sempre OK
        if (isInstanceDimension(level)) {
            return GateResult.ALLOWED;
        }

        // Overworld/Nether/End: check flag
        if (!config.isInstanceOnly()) {
            // Flag disabilitato: ammetti legacy (con warning)
            LOGGER.warn("[INSTANCE_GATE] Legacy overworld path allowed (instanceOnly=false): {}", caller);
            return GateResult.ALLOWED;
        }

        // Flag abilitato: check debug mode
        if (config.isDebugModeEnabled() && isDebugCaller(caller)) {
            LOGGER.info("[INSTANCE_GATE] Debug caller allowed in overworld: {}", caller);
            return GateResult.ALLOWED_DEBUG_ONLY;
        }

        // Bloccato
        LOGGER.error("[INSTANCE_GATE] BLOCKED legacy overworld call: {} in dimension {}",
            caller, level.dimension().location());
        telemetry.emit("arena.gate.blocked", Map.of(
            "caller", caller,
            "dimension", level.dimension().location().toString()
        ));
        return GateResult.BLOCKED_LEGACY;
    }

    private boolean isInstanceDimension(ServerLevel level) {
        // Pattern: devmod:instance_<uuid>
        return level.dimension().location().getNamespace().equals("devmod") &&
               level.dimension().location().getPath().startsWith("instance_");
    }

    private static final Set<String> DEBUG_CALLERS = Set.of(
        "QuickTestWizard.spawnArenaHere",
        "DevCommand.createTestArena",
        "ArenaDebugCommand.forceSpawn"
    );

    private boolean isDebugCaller(String caller) {
        return DEBUG_CALLERS.contains(caller);
    }
}

// Uso nel codice
public void createArena(ServerLevel level, BlockPos pos, String caller) {
    GateResult result = gate.check(level, caller);

    switch (result) {
        case BLOCKED_LEGACY -> throw new LegacyPathBlockedException(
            "Arena creation in overworld is disabled. Use instance dimensions. " +
            "Enable instanceOnly=false in config for legacy support."
        );
        case ALLOWED_DEBUG_ONLY -> LOGGER.warn("Debug-only arena in overworld: {}", caller);
        case ALLOWED -> {}  // proceed
    }

    // ... create arena
}
```

### Debug Mode Allowlist

```java
// Config
public class ArenaTemplateConfig {
    boolean instanceOnly = true;
    boolean debugModeEnabled = false;  // Solo in dev environment

    // Caller espliciti ammessi in debug
    List<String> debugAllowedCallers = List.of(
        "QuickTestWizard.spawnArenaHere",
        "DevCommand.createTestArena"
    );
}
```

### Checklist Instance-Only Gate
- [ ] Mappare tutti i percorsi legacy (createArenaAtPosition, startQuestInOverworld, etc.)
- [ ] `isInstanceDimension()` check basato su namespace `devmod:instance_*`
- [ ] Gate bloccato di default se instanceOnly=true
- [ ] Debug callers whitelist in config
- [ ] Log esplicito: `[INSTANCE_GATE] BLOCKED/ALLOWED caller=X dimension=Y`
- [ ] Telemetria su block: `arena.gate.blocked`
- [ ] Exception chiara: `LegacyPathBlockedException` con messaggio user-friendly
- [ ] Owner: Core Dev · Durata: 0.25g · Done: gate attivo, tutti i percorsi mappati

---

## Registry Fallback Strategy (v2.12)

### Quando scatta fallback a default_flat_64

| Scenario | Fallback? | Behavior |
|----------|-----------|----------|
| Template richiesto non esiste in registry | ✅ Sì | Usa `default_flat_64`, log WARN |
| Template esiste ma validation fallisce | ✅ Sì | Usa `default_flat_64`, log ERROR |
| Template esiste ma inheritance fallisce | ✅ Sì | Usa `default_flat_64`, log ERROR |
| PolicyResolver non trova match | ✅ Sì | Usa `default` policy + `default_flat_64` |
| `default_flat_64` non esiste | ❌ No | **FATAL**: startup failure |
| `default_flat_64` validation fallisce | ❌ No | **FATAL**: startup failure |

### Implementazione

```java
public class ArenaTemplateRegistry {

    private static final String DEFAULT_TEMPLATE_ID = "default_flat_64";
    private ArenaTemplate defaultTemplate;  // Cached, never null after init

    public void initialize() {
        // Load default template FIRST
        defaultTemplate = loadAndValidate(DEFAULT_TEMPLATE_ID)
            .orElseThrow(() -> new FatalConfigException(
                "Default template '" + DEFAULT_TEMPLATE_ID + "' not found or invalid. " +
                "This is required for fallback. Check arena_templates/ directory."
            ));

        LOGGER.info("Default template loaded: {} v{}", DEFAULT_TEMPLATE_ID, defaultTemplate.version());

        // Load all others
        loadAll();
    }

    public ArenaTemplate get(String id) {
        ArenaTemplate template = registry.get(id);

        if (template == null) {
            LOGGER.warn("Template '{}' not found, falling back to '{}'", id, DEFAULT_TEMPLATE_ID);
            telemetry.emit("arena.template.fallback", Map.of(
                "requested", id,
                "reason", "not_found",
                "fallback", DEFAULT_TEMPLATE_ID
            ));
            return defaultTemplate;
        }

        if (!template.isValid()) {
            LOGGER.error("Template '{}' is invalid ({}), falling back to '{}'",
                id, template.validationErrors(), DEFAULT_TEMPLATE_ID);
            telemetry.emit("arena.template.fallback", Map.of(
                "requested", id,
                "reason", "invalid",
                "errors", template.validationErrors(),
                "fallback", DEFAULT_TEMPLATE_ID
            ));
            return defaultTemplate;
        }

        return template;
    }

    public ArenaTemplate getDefault() {
        return defaultTemplate;  // Never null after init
    }
}
```

### all() behavior: validi vs invalidi

```java
public class ArenaTemplateRegistry {

    // === all() restituisce SOLO template validi ===
    public List<ArenaTemplate> all() {
        return registry.values().stream()
            .filter(ArenaTemplate::isValid)
            .toList();
    }

    // === allWithStatus() include anche invalidi (per debug/admin) ===
    public List<TemplateWithStatus> allWithStatus() {
        return registry.values().stream()
            .map(t -> new TemplateWithStatus(
                t,
                t.isValid() ? Status.VALID : Status.INVALID,
                t.isValid() ? List.of() : t.validationErrors()
            ))
            .toList();
    }

    public record TemplateWithStatus(
        ArenaTemplate template,
        Status status,
        List<String> errors
    ) {}

    public enum Status {
        VALID,
        INVALID,
        LOADING,  // Durante reload
        DEPRECATED  // Se version < minSupportedVersion
    }

    // === Conteggi per monitoring ===
    public RegistryStats getStats() {
        long valid = registry.values().stream().filter(ArenaTemplate::isValid).count();
        long invalid = registry.values().stream().filter(t -> !t.isValid()).count();

        return new RegistryStats(
            registry.size(),
            (int) valid,
            (int) invalid,
            defaultTemplate != null
        );
    }

    public record RegistryStats(
        int total,
        int valid,
        int invalid,
        boolean defaultLoaded
    ) {}
}
```

### Template validity tracking

```java
public class ArenaTemplate {

    private final String id;
    private final int version;
    // ... altri campi

    // Validation state
    private boolean valid = true;
    private List<String> validationErrors = List.of();

    public void markInvalid(List<String> errors) {
        this.valid = false;
        this.validationErrors = List.copyOf(errors);
    }

    public boolean isValid() {
        return valid;
    }

    public List<String> validationErrors() {
        return validationErrors;
    }
}
```

### Checklist Registry Fallback
- [ ] `default_flat_64` caricato PRIMA di tutti gli altri
- [ ] Startup failure se default non esiste o invalido
- [ ] `get(id)` fallback a default se not found
- [ ] `get(id)` fallback a default se invalid
- [ ] Log WARN/ERROR con reason e fallback
- [ ] Telemetria `arena.template.fallback` con reason
- [ ] `all()` restituisce SOLO template validi
- [ ] `allWithStatus()` include invalidi con errors
- [ ] `getStats()` per monitoring (total, valid, invalid)
- [ ] Template.isValid() + validationErrors() per tracking
- [ ] Owner: Core Dev · Durata: 0.25g · Done: fallback robusto, all() filtra invalidi
