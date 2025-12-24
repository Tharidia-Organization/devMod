# Instance Dimension System - Strategia di Test Progressiva

## Stato Attuale della Codebase

**Baseline Test Suite**: 100+ test unitari passati (GREEN)
**Coverage aree**: State machine, data consistency, recovery, concurrency, edge cases
**Gap identificati**: Test di integrazione server-side, GameTest, scenari multiplayer reali

Nota: per test specifici su Arena Template (build/rollback/validation/telemetria) vedi `docs/arena-template-rework/TODO_ARENA_TEMPLATE.md`.

---

## 1. Mappa dell'Esperienza Utente

### 1.1 Azioni Principali (Happy Path)

| # | Azione | Prerequisiti | Stato Sistema Atteso |
|---|--------|--------------|----------------------|
| 1 | Player apre UI Quest | Player in overworld, non in quest | Mostra lista mob disponibili |
| 2 | Player seleziona mob e avvia quest | Mob sbloccato, non in countdown | `PlayerState = PREPARING` |
| 3 | Sistema crea snapshot | - | Snapshot salvato su disco |
| 4 | Sistema crea dimensione | Snapshot salvato | `InstanceState = CREATING → READY` |
| 5 | Countdown 10s (o immediato) | Dimensione pronta | Messaggi countdown visibili |
| 6 | Teleport verso istanza | Countdown = 0 | `PlayerState = IN_TRANSIT → IN_INSTANCE` |
| 7 | Quest inizia (wave 1) | Player in istanza | `InstanceState = ACTIVE`, mob spawning |
| 8 | Player combatte waves | Quest attiva | Kill tracking, wave progression |
| 9 | Perk selection tra waves | Wave completata | UI perk visibile |
| 10 | Quest completata/fallita | Waves finite o morte | `InstanceState = COMPLETING` |
| 11 | Teleport ritorno | Quest terminata | `PlayerState = RETURNING → NORMAL` |
| 12 | Ripristino stato originale | Teleport completato | Inventory, health, pos restored |
| 13 | Distruzione istanza | Player uscito, delay 5s | `InstanceState = DESTROYING → DESTROYED` |

### 1.2 Azioni Secondarie

| Azione | Trigger | Risultato Atteso |
|--------|---------|------------------|
| Abbandono quest | Player usa comando/UI | Recovery + quest fallita |
| Disconnect durante quest | Rete/crash client | Snapshot preserved, recovery al login |
| Morte in quest | HP = 0 | Quest fallita, recovery immediato |
| Server reload/restart | Admin/crash | Recovery all'avvio |
| Cambio dimensione non autorizzato | Exploit/bug | Detect + force return |

### 1.3 Flussi Edge Case

```
EDGE CASE 1: Disconnect durante teleport (IN_TRANSIT)
┌────────────────────────────────────────────────────┐
│ T0: Player stato = IN_TRANSIT                      │
│ T1: DISCONNECT                                     │
│ T2: Server non può completare teleport             │
│ T3: Snapshot preserved (stato IN_TRANSIT)          │
│ T4: Al login → checkPendingRecovery()             │
│ T5: Recovery a posizione originale                 │
└────────────────────────────────────────────────────┘

EDGE CASE 2: Server crash durante quest
┌────────────────────────────────────────────────────┐
│ T0: Quest attiva, player in istanza                │
│ T1: SERVER CRASH                                   │
│ T2: Snapshot su disco (stato IN_INSTANCE)          │
│ T3: Server restart                                 │
│ T4: performStartupCleanup()                        │
│ T5: Al player login → recovery automatico          │
└────────────────────────────────────────────────────┘

EDGE CASE 3: Dimension creation timeout
┌────────────────────────────────────────────────────┐
│ T0: createDimensionAsync() chiamato                │
│ T1: Timeout (server sovraccarico)                  │
│ T2: Future completa con null                       │
│ T3: Trigger recovery per tutti i player            │
│ T4: Instance rimossa dal registry                  │
└────────────────────────────────────────────────────┘

EDGE CASE 4: Party member disconnect
┌────────────────────────────────────────────────────┐
│ T0: 4 player in party quest                        │
│ T1: Player B disconnette                           │
│ T2: Player B rimosso da instance.players           │
│ T3: Quest continua per A, C, D                     │
│ T4: Player B login → recovery a pos originale      │
│ T5: Istanza distrutta solo quando TUTTI usciti     │
└────────────────────────────────────────────────────┘
```

---

## 2. Piano di Test Progressivo

### L0: Smoke Test (Baseline Verification)

**Scopo**: Verificare che il sistema si inizializzi correttamente senza crash.

| ID | Test | Tipo | Validazione |
|----|------|------|-------------|
| L0.1 | Server startup con mod | Manual | No exceptions in log |
| L0.2 | InstanceManager.initialize() | Unit | `initialized = true` |
| L0.3 | RecoverySystem directory creation | Unit | `snapshots/` exists |
| L0.4 | InstanceRegistry load empty | Unit | Empty registry loads OK |
| L0.5 | DynamicDimensionManager init | Unit | `isReady() = true` |

**Setup**: Server dedicato vanilla + devmod
**Teardown**: Nessuno
**Metriche**: 0 exceptions, boot time < 30s
**Rischi**: Mixin injection fallita, incompatibilità NeoForge

---

### L1: Core Flow Test (Single Player Happy Path)

**Scopo**: Validare il flusso completo di una quest single player.

| ID | Test | Tipo | Pre-condizioni | Validazione |
|----|------|------|----------------|-------------|
| L1.1 | Start quest immediate | GameTest | Player in overworld | `InstanceState = ACTIVE` in <5s |
| L1.2 | Snapshot created | GameTest | Quest started | Snapshot file exists |
| L1.3 | Dimension created | GameTest | Snapshot saved | ServerLevel accessibile |
| L1.4 | Player teleported | GameTest | Dimension ready | Player in instance dimension |
| L1.5 | Wave spawning | GameTest | Player in instance | Mobs spawned at wave start |
| L1.6 | Quest completion | GameTest | All waves cleared | `InstanceState = COMPLETING` |
| L1.7 | Player restored | GameTest | Quest ended | Player in original pos |
| L1.8 | Instance destroyed | GameTest | Player returned | Dimension folder deleted |

**Setup**:
```java
@GameTest(template = "devmod:empty_arena", setupTicks = 20)
public void testCoreFlow(GameTestHelper helper) {
    ServerPlayer player = helper.makeMockPlayer();
    // ... test sequence
}
```

**Teardown**: Force cleanup qualsiasi istanza residua
**Metriche**: Flow completo in <30s, no leaks
**Rischi**: Timing issues, async operations

---

### L2: Error Recovery Test (Failure Paths)

**Scopo**: Validare tutti i path di recovery per ogni tipo di failure.

| ID | Test | Failure Mode | Recovery Atteso |
|----|------|--------------|-----------------|
| L2.1 | Dimension creation fails | `createDimensionAsync` returns null | Player restored via snapshot |
| L2.2 | Teleport timeout | Player not in instance after 30s | Recovery triggered |
| L2.3 | Disconnect PREPARING | Player disconnects pre-teleport | Snapshot → login recovery |
| L2.4 | Disconnect IN_TRANSIT | Player disconnects during teleport | Snapshot → login recovery |
| L2.5 | Disconnect IN_INSTANCE | Player disconnects in quest | Snapshot → login recovery + quest fail |
| L2.6 | Disconnect RETURNING | Player disconnects during return | Force teleport on login |
| L2.7 | Server crash simulation | Kill process during quest | Startup cleanup + login recovery |
| L2.8 | Corrupted snapshot | Malformed .dat file | Fallback to overworld spawn |
| L2.9 | Missing dimension | Instance exists but no dimension | Clean registry + recovery |

**Setup**: Inject failures via test hooks
**Teardown**: Verify no orphaned resources
**Metriche**: 100% recovery success, no data loss
**Rischi**: Race conditions, incomplete cleanup

---

### L3: Stress Test (Load & Concurrency)

**Scopo**: Verificare performance e stabilità sotto carico.

| ID | Test | Scenario | Threshold |
|----|------|----------|-----------|
| L3.1 | Concurrent instance creation | 10 instances simultanee | <5s ciascuna |
| L3.2 | Rapid create/destroy | 100 cicli in 60s | No memory leak |
| L3.3 | Large snapshot data | Player con inventory pieno | Save/load <500ms |
| L3.4 | Registry with 1000 instances | Populate + query | Query <10ms |
| L3.5 | Concurrent state transitions | 50 players changing state | No inconsistencies |
| L3.6 | Dimension cleanup backlog | 50 pending destructions | All cleaned in <30s |
| L3.7 | TPS impact | 5 active instances | TPS > 18 |
| L3.8 | Memory pressure | 20 instances, GC triggered | No OOM, recovery works |

**Setup**: Dedicated test server, performance monitoring
**Teardown**: Full GC + verify heap
**Metriche**: TPS, heap usage, response times
**Rischi**: Server freeze, memory leaks

---

### L4: Multiplayer Test (Party & Sync)

**Scopo**: Validare scenari multiplayer e sincronizzazione.

| ID | Test | Scenario | Validazione |
|----|------|----------|-------------|
| L4.1 | Party creation | 4 players form party | All in same instance |
| L4.2 | Party teleport sync | All members teleport | Arrive within 1s of each other |
| L4.3 | Party member disconnect | 1 of 4 disconnects | Others continue, 1 recovered |
| L4.4 | Party leader disconnect | Leader disconnects | New leader assigned |
| L4.5 | All party disconnect | All 4 disconnect | Instance destroyed |
| L4.6 | Party completion | Quest cleared | All restored to original pos |
| L4.7 | Mixed modes | Solo + party concurrent | No interference |
| L4.8 | Join/leave during countdown | Player joins/leaves party | Correct tracking |

**Setup**: 4 test clients, network simulation
**Teardown**: Verify all players recovered
**Metriche**: Sync <500ms, no desync
**Rischi**: Race conditions, state desync

---

### L5: Integration Test (Full System)

**Scopo**: Validare integrazione con altri sistemi mod.

| ID | Test | Integration Point | Validazione |
|----|------|-------------------|-------------|
| L5.1 | Perk system | Perk selection between waves | Perks applied correctly |
| L5.2 | Stats tracking | Kill count, damage dealt | Stats persisted |
| L5.3 | XP/rewards | Quest completion rewards | Correct XP granted |
| L5.4 | UI sync | RadialMenu, HUD | UI reflects instance state |
| L5.5 | Config reload | /reload command | Instance system survives |
| L5.6 | World reload | Dimension changes | Instances preserved |
| L5.7 | Mod compatibility | Other dimension mods | No conflicts |

**Setup**: Full mod environment
**Teardown**: Config restore
**Metriche**: Functional correctness
**Rischi**: API incompatibilities

---

## 3. Implementazione Test

### 3.1 Framework Selection

| Tipo Test | Framework | Quando Usare |
|-----------|-----------|--------------|
| Unit (logic) | JUnit 5 | State machines, data structures |
| Unit (mock server) | JUnit 5 + Mockito | Manager logic senza server reale |
| GameTest | NeoForge GameTest | Scenari server-side deterministici |
| Integration | Manual + Automated | Full system con UI |
| Stress | JUnit 5 + concurrent | Load testing |

### 3.2 GameTest Structure

```java
package com.devmod.gametest;

import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder(DevMod.MODID)
public class InstanceSystemGameTests {

    @GameTest(template = "devmod:empty_platform", setupTicks = 20)
    public void testInstanceCreation(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayer();

        // Start quest
        CompletableFuture<UUID> future = InstanceManager.INSTANCE
            .startInstanceQuestImmediate(player, "test_arena", "test_quest", null);

        helper.runAfterDelay(100, () -> {
            UUID instanceId = future.join();
            helper.assertTrue(instanceId != null, "Instance should be created");

            Optional<InstanceData> instance = InstanceRegistry.INSTANCE.getInstance(instanceId);
            helper.assertTrue(instance.isPresent(), "Instance should be in registry");
            helper.assertTrue(instance.get().getState() == InstanceState.ACTIVE,
                "Instance should be ACTIVE");

            helper.succeed();
        });
    }

    @GameTest(template = "devmod:empty_platform", setupTicks = 20)
    public void testRecoveryOnDisconnect(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayer();

        // Start quest and wait for active state
        CompletableFuture<UUID> future = InstanceManager.INSTANCE
            .startInstanceQuestImmediate(player, "test_arena", "test_quest", null);

        helper.runAfterDelay(100, () -> {
            UUID instanceId = future.join();

            // Verify snapshot exists
            helper.assertTrue(RecoverySystem.INSTANCE.hasSnapshot(player.getUUID()),
                "Snapshot should exist");

            // Simulate disconnect
            InstanceManager.INSTANCE.onPlayerLogout(player);

            // Verify cleanup
            helper.assertTrue(
                InstanceRegistry.INSTANCE.getPlayerInstance(player.getUUID()).isEmpty(),
                "Player should be unmapped");

            helper.succeed();
        });
    }

    @GameTest(template = "devmod:empty_platform", setupTicks = 20, timeoutTicks = 600)
    public void testFullQuestCycle(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayer();
        BlockPos originalPos = player.blockPosition();

        // Record original state
        int originalLevel = player.experienceLevel;

        // Start quest
        CompletableFuture<UUID> future = InstanceManager.INSTANCE
            .startInstanceQuestImmediate(player, "test_arena", "test_quest", null);

        helper.runAfterDelay(100, () -> {
            UUID instanceId = future.join();

            // Simulate quest completion
            InstanceManager.INSTANCE.endInstanceQuest(instanceId, true, "Test complete");

            helper.runAfterDelay(60, () -> {
                // Verify player restored
                helper.assertTrue(
                    player.level().dimension().equals(Level.OVERWORLD),
                    "Player should be back in overworld");

                // Verify instance destroyed
                helper.runAfterDelay(200, () -> {
                    helper.assertTrue(
                        InstanceRegistry.INSTANCE.getInstance(instanceId).isEmpty() ||
                        InstanceRegistry.INSTANCE.getInstance(instanceId).get().isDestroyed(),
                        "Instance should be destroyed");

                    helper.succeed();
                });
            });
        });
    }
}
```

### 3.3 Client/Server Validation Matrix

| Operazione | Server-side | Client-side | Sync Method |
|------------|-------------|-------------|-------------|
| Instance creation | InstanceManager | - | N/A |
| Snapshot save | RecoverySystem | - | N/A |
| Dimension creation | DynamicDimensionManager | - | N/A |
| Teleport | ServerPlayer.teleportTo() | Chunk sync | Automatic |
| State change | InstanceData.setState() | - | Packet (future) |
| UI update | - | HUD overlay | Custom packet |
| Countdown display | - | Toast/ActionBar | ActionBar packet |
| Inventory restore | Server loads NBT | Client receives | Container sync |

### 3.4 Sincronizzazione Client/Server

```
Validazione Sync Flow:
┌─────────────────────────────────────────────────────────────────┐
│ SERVER                           │ CLIENT                       │
├─────────────────────────────────────────────────────────────────┤
│ 1. createSnapshot()              │                              │
│ 2. createDimension()             │                              │
│ 3. → Send PREPARING packet ────────→ Show "Preparing..."       │
│ 4. startCountdown()              │                              │
│ 5. → Send COUNTDOWN packet ────────→ Show countdown overlay    │
│ 6. executeTeleport()             │                              │
│ 7. → Vanilla dimension sync ───────→ Client loads chunks       │
│ 8. setState(ACTIVE)              │                              │
│ 9. → Send ACTIVE packet ───────────→ Hide overlay, show HUD    │
│ 10. endInstanceQuest()           │                              │
│ 11. → Send COMPLETING packet ──────→ Show "Returning..."       │
│ 12. performRecovery()            │                              │
│ 13. → Vanilla dimension sync ──────→ Client loads chunks       │
│ 14. → Send NORMAL packet ──────────→ Hide all instance UI      │
└─────────────────────────────────────────────────────────────────┘

Validation Points (checkpoints per GameTest):
✓ After step 3: hasSnapshot(playerId) == true
✓ After step 6: player.level() != overworld
✓ After step 7: player.blockPosition().getY() == 65 (arena level)
✓ After step 9: InstanceData.state == ACTIVE
✓ After step 12: player.getInventory() restored
✓ After step 13: player.level() == overworld
```

---

## 4. Bug Log Template

### Template per ogni bug trovato

```markdown
### BUG-XXX: [Titolo Breve]

**Severità**: Critical / High / Medium / Low
**Stato**: Open / In Progress / Fixed / Verified

**Sintomo Osservato**:
[Descrizione del comportamento anomalo osservato]

**Riproduzione Precisa**:
1. Step 1
2. Step 2
3. ...
Expected: [comportamento atteso]
Actual: [comportamento effettivo]

**Root Cause**:
[Analisi tecnica della causa alla radice]

**Fix Definitivo**:
```java
// Codice correttivo
```

**File Modificati**:
- `path/to/file.java:line`

**Test di Regressione**:
```java
@Test
void testBugXXXRegression() {
    // Test che fallisce PRIMA del fix
    // Test che passa DOPO il fix
}
```

**Impatto su Moduli Correlati**:
- [Lista moduli impattati]
```

---

## 5. Gap Analysis - Test Mancanti

### 5.1 Test Unitari (Già Coperti ✓)

- ✓ State machine transitions
- ✓ Bidirectional map consistency
- ✓ Recovery scenarios
- ✓ Concurrent operations
- ✓ Edge cases

### 5.2 GameTest (Da Implementare)

- ⬜ Creazione dimensione reale
- ⬜ Teleport effettivo
- ⬜ Chunk generation in void world
- ⬜ Arena platform creation
- ⬜ Mob spawning in instance
- ⬜ Player death in instance
- ⬜ Inventory restoration con items reali
- ⬜ Potion effects restoration
- ⬜ Experience restoration

### 5.3 Integration Test (Da Implementare)

- ⬜ EnduranceQuestManager ↔ InstanceManager
- ⬜ InstanceArenaManager ↔ DynamicDimensionManager
- ⬜ UI components sync con instance state
- ⬜ Network packet delivery
- ⬜ Config reload survivability

### 5.4 Manual Test (Checklist)

- ⬜ Esperienza completa da UI
- ⬜ Multiplayer con 2+ giocatori
- ⬜ Server dedicato (non singleplayer)
- ⬜ LAN mode
- ⬜ Alt-tab durante quest
- ⬜ Disconnect forzato (kill client)
- ⬜ Server restart durante quest

---

## 6. Checklist di Verifica Pre-Release

### 6.1 Unit Test

- [ ] Tutti i test `com.devmod.instance.*` passano
- [ ] Coverage >80% su classi core
- [ ] No test @Disabled senza issue ticket

### 6.2 Integration

- [ ] Build clean (`./gradlew clean build`)
- [ ] Server start senza exceptions
- [ ] Mod load success in log
- [ ] Mixin injection verificata

### 6.3 Functional

- [ ] Quest completa in singleplayer
- [ ] Recovery da disconnect funziona
- [ ] Instance distrutta correttamente
- [ ] Nessun file residuo dopo distruzione

### 6.4 Performance

- [ ] TPS stabile durante quest
- [ ] No memory leak dopo 10 cicli
- [ ] Snapshot save <100ms
- [ ] Dimension creation <3s

### 6.5 Multiplayer

- [ ] 2 player party funziona
- [ ] Disconnect di un membro non crashha altri
- [ ] Istanze parallele non interferiscono

---

## 7. Prossimi Passi Immediati

1. **Implementare GameTest base** per creazione/distruzione dimensione
2. **Aggiungere network packet** per sync stato client
3. **Creare test integration** EnduranceQuestManager ↔ InstanceManager
4. **Eseguire test manuali** con checklist documentata
5. **Documentare ogni bug** trovato con template standard

---

## Appendice A: Comandi di Debug

```bash
# Esegui tutti i test instance
./gradlew test --tests "com.devmod.instance.*"

# Esegui solo test specifico
./gradlew test --tests "InstanceFlowValidationTest"

# Build con report
./gradlew build

# GameTest (quando implementato)
./gradlew runGameTestServer
```

## Appendice B: Log Patterns da Monitorare

```
# Inizializzazione corretta
[InstanceManager] Initialized
[Recovery] Initialized, snapshots dir: ...
[DynamicDim] Initialized with server

# Flow normale
[Instance] <uuid> state changed: CREATING -> READY
[DynamicDim] Successfully created dimension devmod:instance_...
[DynamicDim] Teleported <player> to instance <uuid>
[Instance] <uuid> state changed: READY -> ACTIVE

# Recovery
[Recovery] Found pending snapshot for <player> in state <state>
[Recovery] Performing recovery for <player> - <reason>
[Recovery] Successfully recovered player <player>

# Errori da investigare
[Recovery] Failed to save snapshot
[DynamicDim] Failed to create dimension
[InstanceManager] Teleport failed
```
