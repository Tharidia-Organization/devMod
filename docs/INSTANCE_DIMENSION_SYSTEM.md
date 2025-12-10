# Sistema Istanze Dimensionali Temporanee - DevMod

## Overview

Sistema per creare dimensioni temporanee isolate per le Endurance Quest. Ogni istanza viene generata on-demand, ospita una o più sessioni di quest, e viene **completamente distrutta** al termine.

### Obiettivi
- **Zero dati residui**: Istanza cancellata = nessun file/chunk rimasto
- **Isolamento totale**: Ogni quest in dimensione separata
- **Multiplayer ready**: Supporto party/coop + istanze parallele per utenti diversi
- **Recovery automatico**: Disconnect/crash → ripristino sicuro del player
- **Server-safe**: Compatibile con server dedicati e LAN

---

## Architettura

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         INSTANCE DIMENSION SYSTEM                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────┐    ┌──────────────────┐    ┌────────────────────┐        │
│  │InstanceManager│◄──►│DynamicDimensionMgr│◄──►│PlayerSnapshotStore│        │
│  └──────┬───────┘    └────────┬─────────┘    └─────────┬──────────┘        │
│         │                     │                        │                    │
│         ▼                     ▼                        ▼                    │
│  ┌──────────────┐    ┌──────────────────┐    ┌────────────────────┐        │
│  │InstanceData  │    │  Mixin Hooks     │    │  RecoverySystem    │        │
│  │ - UUID       │    │  - ServerLevels  │    │  - OnLogin         │        │
│  │ - Players[]  │    │  - DimStorage    │    │  - OnDisconnect    │        │
│  │ - Arena      │    │  - Teleport      │    │  - OnCrash         │        │
│  │ - State      │    └──────────────────┘    └────────────────────┘        │
│  └──────────────┘                                                           │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Stati del Sistema

### Instance States
```
CREATING     → Dimensione in fase di generazione
READY        → Arena pronta, attesa players
ACTIVE       → Quest in corso
COMPLETING   → Quest terminata, cleanup in corso
DESTROYING   → Rimozione dimensione dal server
DESTROYED    → Cleanup completato (stato finale)
```

### Player States (relativi all'istanza)
```
NORMAL       → Player nel mondo normale
PREPARING    → Snapshot salvato, pre-teleport
IN_TRANSIT   → Teleport in corso
IN_INSTANCE  → Player nell'istanza, quest attiva
RETURNING    → Teleport di ritorno in corso
```

---

## Flusso Completo

### FASE 1: Richiesta Avvio Quest

```
Player richiede quest
        │
        ▼
┌───────────────────────────────────────┐
│ 1. Validazione                        │
│    - Player non già in istanza        │
│    - Requisiti quest soddisfatti      │
│    - Server ha risorse disponibili    │
└───────────────────────────────────────┘
        │
        ▼
┌───────────────────────────────────────┐
│ 2. Blocco Player                      │
│    - Disabilita input (client freeze) │
│    - Mostra "Preparing Arena..."      │
│    - Previeni movimento/azioni        │
└───────────────────────────────────────┘
        │
        ▼
┌───────────────────────────────────────┐
│ 3. Salvataggio Snapshot               │
│    - Crea PlayerInstanceSnapshot      │
│    - Serializza su disco IMMEDIATO    │
│    - fsync() per garantire persist.   │
│    - Stato player → PREPARING         │
└───────────────────────────────────────┘
```

### FASE 2: Creazione Istanza

```
┌───────────────────────────────────────┐
│ 4. Genera UUID Istanza                │
│    - UUID.randomUUID()                │
│    - Registra in InstanceRegistry     │
└───────────────────────────────────────┘
        │
        ▼
┌───────────────────────────────────────┐
│ 5. Registra Dimensione (Mixin)        │
│    - Crea ResourceKey<Level>          │
│    - Inject in MinecraftServer.levels │
│    - Setup ChunkGenerator (void)      │
│    - Stato istanza → CREATING         │
└───────────────────────────────────────┘
        │
        ▼
┌───────────────────────────────────────┐
│ 6. Genera Arena (Async)               │
│    - Force-load chunks centrali       │
│    - Piazza struttura arena           │
│    - Spawn point setup                │
│    - Stato istanza → READY            │
└───────────────────────────────────────┘
        │
        ▼
┌───────────────────────────────────────┐
│ 7. Notifica Player                    │
│    - "Arena Ready! Teleporting..."    │
│    - Stato player → IN_TRANSIT        │
└───────────────────────────────────────┘
```

### FASE 3: Teleport e Quest

```
┌───────────────────────────────────────┐
│ 8. Teleport Player                    │
│    - ServerPlayer.changeDimension()   │
│    - Attendi sync posizione           │
│    - Verifica player nella dimensione │
└───────────────────────────────────────┘
        │
        ▼
┌───────────────────────────────────────┐
│ 9. Setup Quest                        │
│    - Stato player → IN_INSTANCE       │
│    - Stato istanza → ACTIVE           │
│    - Clear inventory, give kit        │
│    - Inizializza subsystems           │
│    - Sblocca input player             │
└───────────────────────────────────────┘
        │
        ▼
┌───────────────────────────────────────┐
│ 10. Quest Loop                        │
│     - Wave spawning                   │
│     - Combat tracking                 │
│     - Perk selection                  │
│     - Checkpoint saves                │
└───────────────────────────────────────┘
```

### FASE 4: Fine Quest

```
Quest completata/fallita/abbandonata
        │
        ▼
┌───────────────────────────────────────┐
│ 11. Pre-Return                        │
│     - Blocca input player             │
│     - Calcola rewards                 │
│     - Salva statistiche               │
│     - Stato istanza → COMPLETING      │
└───────────────────────────────────────┘
        │
        ▼
┌───────────────────────────────────────┐
│ 12. Ripristino Player                 │
│     - Stato player → RETURNING        │
│     - Teleport a posizione originale  │
│     - Ripristina inventory originale  │
│     - Ripristina gamemode             │
│     - Ripristina health/food/effects  │
└───────────────────────────────────────┘
        │
        ▼
┌───────────────────────────────────────┐
│ 13. Cleanup Player                    │
│     - Stato player → NORMAL           │
│     - Cancella snapshot file          │
│     - Rimuovi da istanza.players[]    │
│     - Sblocca input                   │
│     - Mostra risultati quest          │
└───────────────────────────────────────┘
        │
        ▼
┌───────────────────────────────────────┐
│ 14. Check Istanza Vuota               │
│     - Se players.isEmpty()            │
│     - Schedule distruzione (5s delay) │
└───────────────────────────────────────┘
```

### FASE 5: Distruzione Istanza

```
┌───────────────────────────────────────┐
│ 15. Pre-Destroy Validation            │
│     - Conferma nessun player          │
│     - Stato istanza → DESTROYING      │
└───────────────────────────────────────┘
        │
        ▼
┌───────────────────────────────────────┐
│ 16. Unload Dimensione                 │
│     - Unload tutti i chunks           │
│     - Flush pending saves             │
│     - Rimuovi da MinecraftServer.lvls │
└───────────────────────────────────────┘
        │
        ▼
┌───────────────────────────────────────┐
│ 17. Cleanup Filesystem                │
│     - Delete world/dimensions/devmod/ │
│       instance_<uuid>/                │
│     - Verifica cancellazione          │
└───────────────────────────────────────┘
        │
        ▼
┌───────────────────────────────────────┐
│ 18. Deregistra Istanza                │
│     - Rimuovi da InstanceRegistry     │
│     - Rimuovi da DimensionDataStorage │
│     - Stato istanza → DESTROYED       │
│     - Log completamento               │
└───────────────────────────────────────┘
```

---

## Recovery System

### Scenari di Failure e Recovery

#### Scenario A: Disconnect Durante IN_TRANSIT
```
Timeline:
  T0: Snapshot salvato, stato = PREPARING
  T1: Teleport inizia, stato = IN_TRANSIT
  T2: !! DISCONNECT !!
  T3: Server salva player in posizione intermedia

Recovery (al prossimo login):
  T4: PlayerLoggedInEvent fired
  T5: RecoverySystem.checkPendingRecovery(player)
  T6: Trova snapshot con stato IN_TRANSIT
  T7: IGNORA posizione dal playerdata
  T8: Teleport a snapshot.originalPosition
  T9: Ripristina inventory da snapshot
  T10: Cancella snapshot
  T11: Player stato → NORMAL
  T12: Notifica: "Quest cancelled due to disconnection"
```

#### Scenario B: Disconnect Durante IN_INSTANCE
```
Policy: Quest automaticamente FALLITA
Motivo: Istanza temporanea, no reconnect support

Timeline:
  T0: Player in quest, stato = IN_INSTANCE
  T1: !! DISCONNECT !!
  T2: Server detecta disconnect
  T3: onPlayerLogout handler:
      - Marca quest come FAILED
      - Rimuovi player da istanza
      - Se istanza vuota → schedule destroy

Recovery (al prossimo login):
  T4: Trova snapshot con stato IN_INSTANCE
  T5: Teleport a snapshot.originalPosition
  T6: Ripristina inventory originale
  T7: Cancella snapshot
  T8: Notifica: "Quest failed - You disconnected"
```

#### Scenario C: Disconnect Durante RETURNING
```
Timeline:
  T0: Quest finita, stato = RETURNING
  T1: Teleport ritorno inizia
  T2: !! DISCONNECT !!

Recovery:
  T3: Trova snapshot con stato RETURNING
  T4: Rewards già calcolati e salvati
  T5: Forza teleport a posizione originale
  T6: Ripristina inventory
  T7: Mostra rewards (se non già mostrati)
  T8: Cancella snapshot
```

#### Scenario D: Server Crash
```
Al riavvio server:
  1. ServerStartedEvent
  2. Scansiona devmod_snapshots/*.dat
  3. Per ogni snapshot trovato:
     - Player non online → lascia per recovery al login
     - Player online (impossibile post-crash) → N/A
  4. Scansiona istanze orfane:
     - dimensions/devmod/instance_*/
     - Se nessun snapshot referenzia → DELETE
  5. Cleanup completato
```

---

## Strutture Dati

### PlayerInstanceSnapshot
```java
public class PlayerInstanceSnapshot {
    // === Identificatori ===
    private UUID playerId;
    private UUID instanceId;          // Null se ancora in creazione
    private long createdAt;
    private long lastUpdated;

    // === Stato Transazione ===
    private PlayerInstanceState state;
    // NORMAL, PREPARING, IN_TRANSIT, IN_INSTANCE, RETURNING

    // === Posizione Originale (per ritorno) ===
    private ResourceLocation originalDimension;
    private double originalX;
    private double originalY;
    private double originalZ;
    private float originalYaw;
    private float originalPitch;

    // === Stato Player Completo ===
    private CompoundTag inventoryNBT;      // Inventory completo
    private CompoundTag enderChestNBT;     // Ender chest (opzionale)
    private GameType originalGameMode;
    private float originalHealth;
    private float originalMaxHealth;       // Per attributi custom
    private int originalFoodLevel;
    private float originalSaturation;
    private float originalExhaustion;
    private CompoundTag potionEffectsNBT;  // Effetti attivi
    private int originalExperienceLevel;
    private float originalExperienceProgress;
    private int originalTotalExperience;

    // === Metadata Quest ===
    private String questType;              // "endurance", "boss_rush", etc.
    private ResourceLocation mobId;
    private int targetWaves;
    private boolean endlessMode;

    // === Multiplayer ===
    private UUID partyLeaderId;            // Null se solo
    private List<UUID> partyMembers;       // Include leader
}
```

### InstanceData
```java
public class InstanceData {
    // === Identificatori ===
    private UUID instanceId;
    private ResourceKey<Level> dimensionKey;
    private long createdAt;

    // === Stato ===
    private InstanceState state;
    // CREATING, READY, ACTIVE, COMPLETING, DESTROYING, DESTROYED

    // === Players ===
    private Set<UUID> currentPlayers;
    private int maxPlayers;                // 1 per solo, 4 per party
    private UUID ownerId;                  // Chi ha creato l'istanza

    // === Arena ===
    private BlockPos arenaCenter;
    private int arenaRadius;
    private String arenaTemplate;

    // === Quest State ===
    private ResourceLocation questMobId;
    private int currentWave;
    private int totalWaves;
    private long questStartTime;
    private QuestState questState;

    // === Cleanup ===
    private long markedForDestructionAt;   // 0 se non schedulato
    private static final long DESTROY_DELAY_MS = 5000;
}
```

### InstanceRegistry
```java
public class InstanceRegistry {
    // Istanze attive per UUID
    private Map<UUID, InstanceData> instances;

    // Lookup veloce: player → istanza
    private Map<UUID, UUID> playerToInstance;

    // Lookup: dimensionKey → istanza
    private Map<ResourceKey<Level>, UUID> dimensionToInstance;

    // Coda distruzione
    private Queue<ScheduledDestruction> destructionQueue;
}
```

---

## File System

```
<world>/
├── level.dat
├── region/
├── dimensions/
│   └── devmod/
│       ├── instance_a1b2c3d4-e5f6-7890-abcd-ef1234567890/
│       │   ├── region/
│       │   │   └── r.0.0.mca
│       │   └── data/
│       │       └── raids.dat
│       └── instance_11223344-5566-7788-99aa-bbccddeeff00/
│           └── ...
│
├── devmod/
│   ├── snapshots/
│   │   ├── player_uuid_1.dat    ← NBT serialized PlayerInstanceSnapshot
│   │   └── player_uuid_2.dat
│   │
│   ├── instances.json           ← InstanceRegistry state (per recovery)
│   │
│   └── instance_logs/           ← Debug logs per istanza (opzionale)
│       └── instance_uuid.log
```

---

## Mixin Requirements

### 1. MinecraftServerAccessor
```java
@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {
    @Accessor("levels")
    Map<ResourceKey<Level>, ServerLevel> getLevels();

    @Invoker("createLevel")
    ServerLevel invokeCreateLevel(
        LevelStorageSource.LevelStorageAccess access,
        ResourceKey<Level> key,
        ...
    );
}
```

### 2. ServerLevelMixin (Cleanup)
```java
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Inject(method = "close", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        // Hook per cleanup pre-unload
    }
}
```

### 3. ServerPlayerMixin (Teleport Tracking)
```java
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Inject(method = "changeDimension", at = @At("HEAD"))
    private void onChangeDimensionPre(ServerLevel destination, CallbackInfoReturnable<Entity> cir) {
        // Marca IN_TRANSIT se è teleport verso istanza
    }

    @Inject(method = "changeDimension", at = @At("RETURN"))
    private void onChangeDimensionPost(ServerLevel destination, CallbackInfoReturnable<Entity> cir) {
        // Conferma arrivo, aggiorna stato
    }
}
```

### 4. PlayerListMixin (Login/Logout)
```java
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Inject(method = "placeNewPlayer", at = @At("TAIL"))
    private void onPlayerLogin(Connection connection, ServerPlayer player, ...) {
        RecoverySystem.INSTANCE.checkPendingRecovery(player);
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void onPlayerLogout(ServerPlayer player, CallbackInfo ci) {
        InstanceManager.INSTANCE.handlePlayerDisconnect(player);
    }
}
```

---

## Multiplayer Support

### Party System
```
┌─────────────────────────────────────────────────────┐
│ Party Creation                                       │
├─────────────────────────────────────────────────────┤
│ 1. Leader invita players                            │
│ 2. Players accettano                                │
│ 3. Party format: max 4 players                      │
│ 4. Leader avvia quest                               │
│ 5. TUTTI i party members:                           │
│    - Snapshot salvato                               │
│    - Teleportati nella stessa istanza               │
│ 6. Quest scala difficoltà per numero players        │
└─────────────────────────────────────────────────────┘
```

### Party Disconnect Handling
```
Se un membro disconnette:
  - Il suo snapshot viene marcato
  - Gli altri continuano
  - Al login: ripristino a posizione originale
  - Quest continua per gli altri

Se TUTTI disconnettono:
  - Istanza viene distrutta
  - Tutti ripristinati al login

Se LEADER disconnette:
  - Leadership passa al prossimo
  - Se era solo → come disconnect singolo
```

### Instanze Parallele
```
Server con 10 players:
  - Player A: solo quest → Instance_001
  - Players B,C,D,E: party → Instance_002
  - Player F: solo quest → Instance_003
  - Players G,H: party → Instance_004
  - Players I,J: nel mondo normale

Ogni istanza:
  - Dimensione separata
  - Chunk isolati
  - Nessuna interferenza
  - Distrutta quando vuota
```

---

## API Pubbliche

### InstanceManager
```java
public class InstanceManager {
    public static final InstanceManager INSTANCE;

    // === Creazione ===
    CompletableFuture<InstanceData> createInstance(
        ServerPlayer owner,
        QuestSettings settings,
        @Nullable List<ServerPlayer> partyMembers
    );

    // === Query ===
    Optional<InstanceData> getInstance(UUID instanceId);
    Optional<InstanceData> getPlayerInstance(UUID playerId);
    boolean isPlayerInInstance(UUID playerId);
    List<InstanceData> getActiveInstances();

    // === Lifecycle ===
    void scheduleDestruction(UUID instanceId);
    void forceDestroyInstance(UUID instanceId);

    // === Player Management ===
    void handlePlayerDisconnect(ServerPlayer player);
    void removePlayerFromInstance(ServerPlayer player);
}
```

### RecoverySystem
```java
public class RecoverySystem {
    public static final RecoverySystem INSTANCE;

    // === Snapshot Management ===
    void saveSnapshot(ServerPlayer player, InstanceData instance);
    void updateSnapshotState(UUID playerId, PlayerInstanceState state);
    Optional<PlayerInstanceSnapshot> loadSnapshot(UUID playerId);
    void deleteSnapshot(UUID playerId);

    // === Recovery ===
    void checkPendingRecovery(ServerPlayer player);
    void performRecovery(ServerPlayer player, PlayerInstanceSnapshot snapshot);

    // === Server Startup ===
    void cleanupOrphanedInstances();
    void scanPendingSnapshots();
}
```

### DynamicDimensionManager
```java
public class DynamicDimensionManager {
    public static final DynamicDimensionManager INSTANCE;

    // === Dimension Lifecycle ===
    ServerLevel createInstanceDimension(UUID instanceId);
    void unloadDimension(ResourceKey<Level> key);
    void deleteDimensionFiles(ResourceKey<Level> key);

    // === Query ===
    boolean dimensionExists(ResourceKey<Level> key);
    Optional<ServerLevel> getLevel(ResourceKey<Level> key);
}
```

---

## Configurazione

```java
public class InstanceSystemConfig {
    // === Limiti ===
    int maxConcurrentInstances = 50;
    int maxPlayersPerInstance = 4;
    int instanceTimeoutMinutes = 60;

    // === Timing ===
    int destructionDelaySeconds = 5;
    int chunkGenerationTimeoutSeconds = 30;
    int teleportConfirmationTimeoutMs = 5000;

    // === Cleanup ===
    boolean deleteInstanceFilesImmediately = true;
    boolean logInstanceLifecycle = true;
    int orphanCleanupIntervalMinutes = 5;

    // === Recovery ===
    boolean autoRecoverOnLogin = true;
    boolean notifyPlayerOnRecovery = true;
}
```

---

## Testing Checklist

### Unit Tests
- [ ] PlayerInstanceSnapshot serialization/deserialization
- [ ] InstanceData state machine transitions
- [ ] Recovery logic per ogni scenario

### Integration Tests
- [ ] Creazione istanza completa
- [ ] Teleport andata/ritorno
- [ ] Disconnect durante ogni fase
- [ ] Party creation e sync
- [ ] Distruzione istanza e cleanup files

### Stress Tests
- [ ] 50 istanze simultanee
- [ ] Rapid create/destroy cycles
- [ ] Multiple disconnects simultanei
- [ ] Server restart con istanze attive

---

## Implementation Order

1. **Core Data Structures**
   - PlayerInstanceSnapshot
   - InstanceData
   - InstanceRegistry

2. **Persistence Layer**
   - Snapshot save/load (NBT)
   - Registry persistence (JSON)

3. **Mixin Hooks**
   - MinecraftServerAccessor
   - ServerPlayerMixin (teleport tracking)
   - PlayerListMixin (login/logout)

4. **DynamicDimensionManager**
   - Creazione dimensioni
   - Cleanup dimensioni
   - File deletion

5. **InstanceManager**
   - Lifecycle completo
   - Player tracking

6. **RecoverySystem**
   - Login recovery
   - Server startup cleanup

7. **Arena Generation**
   - Void world generator
   - Arena structure placement

8. **Integration with Existing Quest System**
   - Modify EnduranceQuestManager
   - Update ArenaManager

9. **Multiplayer/Party**
   - Party system
   - Sync mechanisms

10. **UI/Feedback**
    - Loading screens
    - Recovery notifications

---

## Note di Sicurezza

### Perché l'Istanza Temporanea è SICURA

1. **Snapshot PRIMA di tutto**: Lo stato player è salvato su disco PRIMA di qualsiasi operazione rischiosa

2. **Recovery garantito**: Qualsiasi failure → ripristino da snapshot

3. **Nessun dato "in volo"**:
   - Inventory salvato in snapshot, non nell'istanza
   - Rewards calcolati e salvati PRIMA del ritorno
   - Statistiche persistite indipendentemente

4. **Istanza = usa e getta**:
   - Contiene SOLO dati di gioco temporanei
   - Mobs spawned, blocchi arena, entities
   - NIENTE di valore viene perso se crasha

5. **Isolamento**:
   - Crash istanza ≠ crash server
   - Istanza corrotta → DELETE, player recuperato da snapshot

---

## Miglioramenti Concreti Proposti

### Hardening Stato/Recovery
- Introdurre una state machine centralizzata con validazione delle transizioni (InstanceData/PlayerInstanceState) e assert nei punti di confine (teleport, login/logout) per evitare stati impossibili.
- Aggiungere versioning, checksum e scrittura su file temporaneo + rename atomico per gli snapshot: riduce il rischio di snapshot corrotti dopo crash o out-of-disk.
- Inserire un watchdog di coerenza su server start: confronta `devmod/instances.json` con le dimensioni presenti su disco e forza cleanup o recovery guidato.

### Performance e Gestione Risorse
- Pre-caricare e riutilizzare un pool di dimensioni vuote già registrate (warming) per abbattere la latenza di creazione istanza e gli spike di GC server.
- Limitare il tick cost delle istanze inattive con `no-tick view distance` e scaricamento aggressivo dei chunk periferici; esporre config per tarare radius e tick budget.
- Aggiungere telemetria su heap/CPU per istanza (campionamento leggero) e un circuito di backpressure: se oltre soglia, rifiutare nuove istanze con messaggio chiaro.

### Strumenti Operativi e Debug
- Comandi admin `devmod:instance <id>` per: elenco istanze, stato corrente, giocatori, dimensionKey, tempo di vita, ultimo tick; include opzione di `force-destroy` con log dettagliato.
- Logging strutturato per lifecycle (creazione, teleport, recovery, destroy) con correlation-id = instanceId e playerId per facilitare il tracing multi-player.
- Modalità "failure injection" limitata a dev: forza disconnect a step specifici (IN_TRANSIT/RETURNING) per validare i percorsi di recovery senza riavviare il server.

### Esperienza Giocatore e Sicurezza Dati
- Aggiungere schermata client-side di stato istanza (preparing/ready/returning) con timeout visibile; riduce la percezione di freeze e aiuta a segnalare problemi di rete.
- Supporto opzionale al rejoin entro finestra breve per le quest in party: se un membro cade, può rientrare finché l'istanza è ACTIVE, preservando difficoltà e progresso.
- Integrare un controllo di integrità su rewards/statistiche prima del ritorno: se il calcolo fallisce, blocca il teleport e avvia recovery soft per evitare duplicazioni o perdite.

### Metriche e Alert
- Counter: istanze create/distrutte, failure rate creazione, failure rate teleport (pre/post), recovery eseguiti, backpressure refusals.
- Timer/Histogram: tempo CREATING→READY, tempo READY→ACTIVE, durata istanza, latenza snapshot save/load, tempo distruzione.
- Gauge: istanze attive, istanze in stato non-terminal, memoria stimata per istanza, chunks caricati per istanza.
- Alert: percentuale di teleport falliti > soglia, istanze bloccate > X minuti, snapshot corrotti rilevati, distruzioni che falliscono.

### Rollout e Feature Flag
- Introdurre flag separati per: recovery automatico, pool di dimensioni warm, failure injection, rejoin party. Permette rollout incrementale e rollback rapido.
- Canary: abilita il sistema su percentuale di giocatori o su env staging; raccogli metriche, poi estendi.
- Kill-switch operativi: comando admin per disabilitare nuove istanze e chiudere gradualmente le attive (drain mode) prima di hotfix.

### Edge Case da Coprire
- Login doppio dello stesso account: prevenire join contemporanei in istanze diverse e consolidare stato snapshot.
- Server senza spazio disco: rifiutare nuove istanze e mostrare errore esplicito; trigger cleanup aggressivo dei log di istanza.
- Timeout di conferma teleport: se non ricevi conferma entro finestra, annulla e recupera usando snapshot, evitando soft-lock.
- Multiverso con altre mod che aggiungono dimensioni: namespacing rigoroso `devmod:instance_<uuid>` ed evitamento collisioni ResourceKey.

### Test Mirati da Aggiungere
- Fault injection su fsync fallito/IOExceptions durante salvataggio snapshot → verificare rollback e messaggio chiaro al player.
- Stress test con gossip/tick cost elevato: verifica che l'algoritmo di backpressure scatti e che i messaggi al player siano coerenti.
- Test di compatibilità con reload del datapack o `/reload`: confermare che registry e dimensioni temporanee non vengano perse.

---

## Piano Tecnico NeoForge 1.21.1 (dettagli non sommari)

### Hook/Eventi da usare (no mixin dove non serve)
- Registrare su `NeoForge.EVENT_BUS`: `ServerAboutToStartEvent` (bootstrap registry access), `ServerStartedEvent` (scan snapshot/istanze orfane), `ServerStoppingEvent` (drain + destroy forzato).
- `EntityTravelToDimensionEvent` (pre-teleport) e `EntityChangedDimensionEvent` (post) per tracciare IN_TRANSIT/IN_INSTANCE senza patchare `changeDimension`. Mixin solo se serve per bypassare logica vanilla.
- `PlayerEvent.PlayerLoggedInEvent` e `PlayerEvent.PlayerLoggedOutEvent` per recovery/cleanup; evitare injection sul `PlayerList` se non necessario.

### Creazione/Destroy dinamica dimensioni (1.21.1)
- Creazione su server thread con `server.executeBlocking(...)` per garantire ordine con chunk/task. Nessun async durante la costruzione della `ServerLevel`.
- `ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(modid, "instance_" + uuid));`
- `DimensionType`: riusa OVERWORLD come base, ma setta `fixedTime = Optional.of(6000L)` e `natural = false` per arene statiche, oppure profilo custom registrato in `devmod:dimension_type/instance.json`.
- `ChunkGenerator`: usa `FlatLevelSource` con preset void e un singolo biome `Biomes.THE_VOID` per ridurre tick cost; forza `StructurePlacement` vuoto per evitare spawn indesiderati.
- `LevelStem stem = new LevelStem(() -> dimensionTypeHolder, chunkGenerator);` e invoca `MinecraftServer#createLevel` via access transformer/mixin o API dedicata, aggiornando `MinecraftServer.levels`.
- Distruzione: `DynamicDimensionManager.unloadDimension(key)` → `ServerLevel#close()` → flush saves → `MinecraftServer.levels.remove(key)` → cancellazione path `dimensions/devmod/instance_<uuid>/` con verifica esistenza e log.

### Snapshot/Recovery (persistenza 1.21.1)
- Salvare snapshot usando `CompoundTag tag = player.saveWithoutId(new CompoundTag());` e aggiungere componenti custom (quest metadata) sotto `devmod:snapshot`.
- Scrittura: `Path tmp = snapshotPath.resolveSibling(name + ".tmp"); Files.write(tmp, NbtIo.writeCompressed(tag)); Files.move(tmp, finalPath, REPLACE_EXISTING, ATOMIC_MOVE se supportato);` + checksum SHA-256 nel registry per detection.
- Recovery: su login, carica snapshot, controlla versione, verifica checksum; se mismatch → non applicare, log errore e fallback a posizione sicura nel mondo principale.

### Concorrenza e Scheduler
- Tutto il lifecycle istanza deve eseguire sul server thread; usare `server.execute` per task derivanti da completable future (arena generation, pre-warm).
- Per operazioni IO heavy (compress/uncompress, delete ricorsivi), usare un pool dedicato e ritornare al server thread per mutare stato/collezioni.
- Integrare `TickTask` per heartbeat delle istanze: verifica timeout, stato players, backpressure, cleanup schedulati.

### Integrazione UI Client (Neoforge)
- Pacchetto networking custom con `CustomPacketPayload` registrato su `PayloadTypeRegistry` per notifiche di stato (PREPARING/READY/RETURNING) e messaggi di errore/timeout.
- Overlay lato client con lock input controllato via capability client-side; timeout visibile e codice di errore (es. `TP_TIMEOUT`, `SNAPSHOT_FAIL`).

### Compatibilità/Interoperabilità
- Namespacing rigoroso per dimensioni e file: `devmod/instances/<uuid>`, `devmod/snapshots/<player>.dat`, `devmod/instance_logs/<id>.log`.
- Evitare conflitti con altre mod che patchano `MinecraftServer.levels`: controllare presenza di chiavi e loggare collisioni; se API di terze parti esiste, registrare la dimensione tramite essa.
- Resilienza a `/reload`: mantenere registry persistito su disco e ripristinare `InstanceRegistry` al successivo tick server se i livelli sono stati scaricati.

### Test automatici (GameTest/Integration)
- GameTest: scenario di creazione/distruzione dimensione con assert su `server.getLevel(key)` non nullo, teleport di un manichino, e verifica cleanup file post destroy.
- GameTest: fault injection su teleport (simula `EntityTravelToDimensionEvent` cancellato) e verifica recovery coerente.
- Integration test headless: 10 istanze parallele, distruzione ritardata, rejoin party; raccogli metriche e verifica nessun crash/slowtick.

### Next Steps prioritari (azione concreta)
1) Implementare `DynamicDimensionManager` con path atomico + API NeoForge per eventi dimension change; coprire `create/unload/delete` completo.
2) Implementare `InstanceRegistry` persistente con checksum e versioning, più watchdog all'avvio che riallinea disco/registry.
3) Implementare `InstanceManager` che orchestra state machine validata e usa `server.execute` per tutte le mutazioni.
4) Aggiungere packet `InstanceStatusPayload` e overlay client con timeout; messaggi di errore localizzati.
5) Aggiungere GameTest per create/destroy/recovery e un test di backpressure (max instances) per bloccare regressioni.

---

## UI/UX Instance System (standard senior-grade)

### Inventario Schermate/Componenti
- Overlay Preparing/Teleport/Returning: blocco input, stato e progress; visibile in tutte le risoluzioni.
- Panel Quest HUD (in-istanza): timer, wave, mob icon, party status, vita/armatura. Deve adattarsi a UI scale 80–120%.
- Recovery/Errore Modale: testo, codice errore, due pulsanti (Riprova/Esci), focus evidente.
- Summary Rewards: lista ricompense con scroll verticale se overflow; pulsante continua/chiudi.
- Admin Debug Panel (solo op): elenco istanze, stato, player; tabella scrollabile.

### Regole Layout (niente sovrapposizioni)
- Griglia 12 colonne con gutter 8px e padding contenitore 16px; breakpoint unico per 1080p/1440p con scaling UI di Minecraft (80–120%). Usa percentuali e min/max width (es. 320–420px per overlay).
- Line-height 1.25, font-size base 12px (scala con UI scale), max 16px per titoli. Troncamento ellissi solo su identificatori lunghi; altrimenti wrap morbido.
- Margini verticali uniformi 8px tra blocchi; label e campo distanziati 4px; icone 16px allineate a testo.
- Evita stacking di overlay: un solo livello attivo; se appare un modale, nascondi/haiout overlay HUD non critico.

### Overflow/Scroll
- Aggiungi `scrollY` al Summary Rewards e Admin Debug Panel; altezza max 60% viewport per evitare doppio scroll globale.
- Nel Quest HUD, liste di party e status mob: wrap verticale, mai oltre 4 elementi senza scroll; aggiungi fade o scrollbar per indicare contenuto aggiuntivo.
- Testo di errore: max width 320px con wrap; nessun cut-off a 80% UI scale.

### Focus/State/Feedback
- Stati hover/focus per pulsanti/input con bordo 1px e colore acceso (es. #4da3ff) per visibilità; stato disabled grigio chiaro.
- Feedback per timeout teleport: barra progress con countdown numerico; se scade, mostra modale Recovery con pulsante “Ritorna al mondo”.
- Party HUD: highlight leader, badge “Disconnected” in grigio, tooltip statico (no overlap) con reason.

### Layering e Z-index
- Modali sopra overlay HUD (`z = 900`), overlay stato sopra gioco (`z = 800`), HUD base a `z = 700`. Niente elementi invisibili cattura-click.
- Tooltip confinato dentro il panel genitore con offset 8px; mai oltre il bordo dello schermo: clamp sulle coordinate.

### Verifica Responsive (obbligatoria)
- Test 1080p e 1440p con UI scale 80/90/100/110/120: controllare che nessuna label venga troncata e che i pulsanti restino tappabili ≥36px altezza.
- Simula liste lunghe (party 8 membri, rewards 20 item) per validare scroll e assenza di sovrapposizioni.

### Root Cause affrontata
- Mancavano specifiche di layout, spacing e gestione overflow per le schermate dell’istanza: rischio di testi tagliati/sovrapposti e UI non scalabile. Ora sono definiti griglie, limiti dimensionali, layering e policy di scroll per prevenire overlap e cut-off.
