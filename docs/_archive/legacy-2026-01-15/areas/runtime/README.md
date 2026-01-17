# Runtime System

> Ultimo aggiornamento: 2025-12-30

Come DevMod crea dimensioni temporanee per arene e quest, e come protegge i dati dei giocatori.

---

## Il Concetto

Immagina di voler creare un'**arena privata** per ogni gruppo di giocatori che inizia una quest. Non puoi usare una sola arena — altri giocatori potrebbero essere lì. Non puoi creare centinaia di arene nel mondo — sprecheresti spazio.

La soluzione: **dimensioni dinamiche**. Quando un giocatore inizia una quest:

1. Creiamo una nuova dimensione (come il Nether, ma temporanea)
2. Generiamo l'arena dentro
3. Teletrasportiamo i giocatori
4. Quando finiscono, distruggiamo tutto

Ma cosa succede se il server crasha mentre il giocatore è nell'arena? O se si disconnette? Qui entra il **Recovery System**.

---

## Struttura Package

```
com.devmod.runtime/
├── DynamicDimensionManager.java  # Crea/distrugge dimensioni
├── InstanceManager.java          # Gestisce il flusso quest
├── RecoverySystem.java           # Backup e ripristino giocatori
├── InstanceRegistry.java         # Database di tutte le istanze
├── InstanceData.java             # Dati di una singola istanza
├── PlayerInstanceSnapshot.java   # Backup stato giocatore
├── InstanceState.java            # Stati dell'istanza
├── PlayerInstanceState.java      # Stati del giocatore nell'istanza
└── InstanceEventHandler.java     # Gestisce eventi tick e login
```

---

## Il Flusso Completo

### Fase 1: Preparazione

```
Giocatore clicca "Inizia Quest"
         ↓
    Creiamo SNAPSHOT
    (backup di tutto: posizione, inventario, vita, effetti, XP)
         ↓
    Salviamo su disco (snapshots/<playerId>.dat)
         ↓
    Stato giocatore: PREPARING
```

**Perché prima il backup?** Se qualcosa va storto nei passi successivi, possiamo sempre ripristinare.

### Fase 2: Creazione Dimensione

```
    Stato istanza: CREATING
         ↓
    DynamicDimensionManager.createDimensionAsync()
         ↓
    - Crea dimensione void (vuota)
    - Genera piattaforma bedrock
    - Genera arena sopra
    - Force-load dei chunk (così i mob si muovono)
    - Notifica Distant Horizons (se presente)
    - Notifica LittleTiles (se presente)
         ↓
    Stato istanza: READY
```

### Fase 3: Teletrasporto

```
    Countdown 10 secondi (opzionale)
         ↓
    Stato giocatore: IN_TRANSIT
         ↓
    player.teleportTo(dimensione, x, y, z)
         ↓
    Stato istanza: ACTIVE
    Stato giocatore: IN_INSTANCE
```

### Fase 4: Quest Attiva

```
    Giocatore combatte nell'arena
         ↓
    InstanceEventHandler.tickInstanceDimensions()
    - Tick esplicito della dimensione ogni server tick
    - Solo se ci sono giocatori dentro
         ↓
    Quest completata (vittoria/sconfitta/morte)
```

### Fase 5: Ritorno

```
    Stato giocatore: RETURNING
         ↓
    Ripristina inventario originale
    Ripristina vita, cibo, effetti
    Ripristina XP
         ↓
    Teletrasporta a posizione originale
         ↓
    Elimina snapshot (non più necessario)
    Stato giocatore: NORMAL
```

### Fase 6: Cleanup

```
    Ultimo giocatore esce dall'istanza
         ↓
    scheduleDestruction() — aspetta 5 secondi
    (nel caso si riconnetta)
         ↓
    Stato istanza: DESTROYING
         ↓
    - Espelli giocatori rimasti (edge case)
    - Notifica mod (DH, LittleTiles)
    - Rimuovi dimensione dal server
    - Elimina file su disco
         ↓
    Stato istanza: DESTROYED (terminale)
```

---

## Recovery: Cosa Succede se Qualcosa Va Storto

### Scenario 1: Crash Durante Teletrasporto

```
Giocatore in stato IN_TRANSIT
Server crasha
         ↓
Server riavvia
         ↓
Giocatore si riconnette
         ↓
RecoverySystem.checkPendingRecovery()
         ↓
Trova snapshot in stato IN_TRANSIT
         ↓
performRecovery():
- Teletrasporta a posizione originale
- Ripristina inventario
- Ripristina tutto il resto
- Elimina snapshot
         ↓
Giocatore è al sicuro dove era prima
```

### Scenario 2: Disconnect Durante Quest

```
Giocatore in stato IN_INSTANCE
Si disconnette (crash client, internet, esce)
         ↓
InstanceEventHandler.onPlayerLoggedOut()
- Nota: giocatore aveva snapshot
- Non fa nulla di speciale, snapshot resta su disco
         ↓
Giocatore si riconnette (anche giorni dopo)
         ↓
checkPendingRecovery()
- Trova snapshot in stato IN_INSTANCE
- L'istanza potrebbe non esistere più
         ↓
performRecovery()
- Ripristina a posizione originale
- Ripristina tutto
         ↓
Giocatore ritrova i suoi item
```

### Scenario 3: Server Crasha con Istanze Attive

```
Server crasha con 3 istanze attive
         ↓
Server riavvia
         ↓
RecoverySystem.performStartupCleanup()
         ↓
Trova snapshot orfani:
- Se istanza esiste ancora → mantieni (giocatore recupererà al login)
- Se istanza non esiste → mantieni snapshot per recovery
         ↓
Quando giocatori si riconnettono → recovery automatico
```

---

## Gli Stati

### InstanceState (dell'istanza)

```
CREATING    → Dimensione in creazione
    ↓
READY       → Pronta, giocatori possono entrare
    ↓
ACTIVE      → Almeno un giocatore dentro, quest in corso
    ↓
COMPLETING  → Quest finita, giocatori stanno uscendo
    ↓
DESTROYING  → Pulizia in corso
    ↓
DESTROYED   → Fine (stato terminale)
```

### PlayerInstanceState (del giocatore)

```
NORMAL      → Non in un'istanza
    ↓
PREPARING   → Snapshot creato, in attesa
    ↓
IN_TRANSIT  → Teletrasporto in corso
    ↓
IN_INSTANCE → Dentro l'arena
    ↓
RETURNING   → Uscita in corso
    ↓
NORMAL      → Tornato al sicuro
```

---

## PlayerInstanceSnapshot

Cosa viene salvato nel backup:

```java
record PlayerInstanceSnapshot(
    UUID playerId,

    // Posizione originale
    ResourceKey<Level> originalDimension,
    double originalX, originalY, originalZ,
    float originalYaw, originalPitch,

    // Stato vitale
    float health,
    int foodLevel,
    float saturation,
    float exhaustion,

    // Inventario (serializzato NBT)
    CompoundTag inventoryData,
    CompoundTag enderChestData,

    // Effetti attivi
    CompoundTag potionEffectsData,

    // Esperienza
    int experienceLevel,
    float experienceProgress,
    int totalExperience,

    // Metadata
    PlayerInstanceState state,
    long createdAt,
    String arenaTemplate,
    UUID partyId
)
```

---

## InstanceRegistry

Database centrale di tutte le istanze.

```java
// Registra nuova istanza
InstanceRegistry.register(instanceId, instanceData);

// Trova istanza di un giocatore
Optional<InstanceData> inst = InstanceRegistry.getInstanceForPlayer(playerId);

// Programma distruzione
InstanceRegistry.scheduleDestruction(instanceId);

// Query
boolean exists = InstanceRegistry.exists(instanceId);
int count = InstanceRegistry.getActiveCount();
```

### Persistenza

Salvato in `config/instances.json` prima dello shutdown:
```json
{
  "instances": {
    "uuid-1234": {
      "state": "ACTIVE",
      "players": ["player-uuid-1", "player-uuid-2"],
      "dimensionKey": "devmod:instance_1234",
      "createdAt": 1735123456789
    }
  }
}
```

---

## DynamicDimensionManager

Il cuore tecnico: crea e distrugge dimensioni Minecraft.

### Creare una Dimensione

```java
CompletableFuture<ServerLevel> future =
    DynamicDimensionManager.createDimensionAsync(
        server,
        "instance_" + uuid,
        VoidChunkGenerator::new,  // Dimensione vuota
        (level) -> {
            // Callback dopo creazione
            generateArenaPlatform(level);
        }
    );

future.thenAccept(level -> {
    // Dimensione pronta, teletrasporta giocatori
});
```

### Cosa Succede Internamente

1. Crea `ResourceKey<Level>` unico
2. Registra dimensione nel server
3. Genera chunk iniziali
4. Force-load chunk centrali
5. Posta `LevelEvent.Load`
6. Chiama callback
7. Notifica integrazioni (DH, LittleTiles)

### Distruggere una Dimensione

```java
DynamicDimensionManager.destroyDimensionAsync(server, dimensionKey)
    .thenRun(() -> {
        // Pulizia completata
    });
```

### Cosa Succede Internamente

1. Espelli giocatori rimasti
2. Posta `LevelEvent.Unload`
3. Rimuovi da `MinecraftServer.levels` (via Mixin accessor)
4. Notifica integrazioni
5. Elimina cartella dimensione su disco

---

## InstanceManager

Orchestratore del flusso utente.

### Iniziare una Quest

```java
// Con countdown
InstanceManager.startInstanceQuest(
    players,           // Lista giocatori
    arenaTemplate,     // Template arena
    10,                // Secondi countdown
    callback           // Chiamato quando tutti sono dentro
);

// Immediato
InstanceManager.startInstanceQuestImmediate(players, arenaTemplate, callback);
```

### Terminare una Quest

```java
// Vittoria
InstanceManager.completeQuest(instanceId, QuestResult.VICTORY);

// Sconfitta
InstanceManager.completeQuest(instanceId, QuestResult.DEFEAT);

// Giocatore morto
InstanceManager.onPlayerDeath(player); // Ripristina e rimuove
```

---

## Best Practices

### Sempre Validare Prima

```java
// Prima di iniziare una quest
if (InstanceRegistry.getInstanceForPlayer(player.getUUID()).isPresent()) {
    player.sendMessage("Sei già in un'istanza!");
    return;
}
```

### Gestire gli Errori

```java
InstanceManager.startInstanceQuestImmediate(players, template, result -> {
    if (!result.success()) {
        // Il recovery è già stato fatto automaticamente
        // Ma potresti voler notificare l'utente
        for (Player p : players) {
            p.sendMessage("Errore creazione istanza. Sei stato ripristinato.");
        }
    }
});
```

### Non Assumere che l'Istanza Esista

```java
// Nel tuo codice di quest
InstanceRegistry.getInstanceForPlayer(playerId)
    .ifPresentOrElse(
        instance -> {
            // Logica normale
        },
        () -> {
            // Giocatore non è in un'istanza
            // Probabilmente recovery in corso
        }
    );
```

---

## Diagramma Completo

```
┌─────────────────────────────────────────────────────────────────┐
│                        RUNTIME SYSTEM                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   ┌─────────────┐    ┌──────────────────┐    ┌──────────────┐  │
│   │InstanceMgr  │───▶│DynamicDimensionMgr│───▶│  ServerLevel │  │
│   │ (orchestr.) │    │ (crea/distrugge) │    │  (dimensione)│  │
│   └─────────────┘    └──────────────────┘    └──────────────┘  │
│          │                                          │          │
│          ▼                                          │          │
│   ┌─────────────┐    ┌──────────────────┐          │          │
│   │RecoverySys  │◀───│PlayerInstSnapshot│          │          │
│   │ (backup)    │    │ (dati salvati)   │          │          │
│   └─────────────┘    └──────────────────┘          │          │
│          │                                          │          │
│          ▼                                          │          │
│   ┌─────────────┐    ┌──────────────────┐          │          │
│   │InstRegistry │◀───│   InstanceData   │◀─────────┘          │
│   │ (database)  │    │ (stato istanza)  │                     │
│   └─────────────┘    └──────────────────┘                     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Dipendenze

- Minecraft Server API — per dimensioni e giocatori
- NeoForge Events — per tick e login
- Mixin — per accesso a `MinecraftServer.levels`
- `com.devmod.integration` — notifiche a DH/LittleTiles
- GSON/NBT — per persistenza
