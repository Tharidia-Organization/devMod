# Sistemi Core DevMod

> Ultimo aggiornamento: 2025-12-30

Questa guida spiega in dettaglio come funzionano i sistemi principali di DevMod.

---

## 1. Arena Template System

Il sistema Arena permette di creare arene modulari basate su template configurabili.

### Componenti

```mermaid
flowchart TB
    subgraph Registry["Registry Layer"]
        ATR["ArenaTemplateRegistry"]
        APR["ArenaPolicyRegistry"]
    end

    subgraph Resolution["Resolution Layer"]
        PR["PolicyResolver"]
        TRB["TemplateRegistryBootstrap"]
    end

    subgraph Build["Build Layer"]
        TAB["TemplateArenaBuilder"]
        AAB["AsyncArenaBuilder"]
        ABC["AsyncArenaBuildCoordinator"]
    end

    subgraph Observability["Observability"]
        AT["ArenaTelemetry"]
        AR["AlertRouter"]
        ADE["ArenaDashboardEndpoint"]
    end

    ATR --> PR
    APR --> PR
    PR --> TAB
    TAB --> AAB
    AAB --> ABC
    TAB --> AT
    AT --> AR
    AT --> ADE
```

### Concetti Chiave

#### Template
Un template definisce la struttura fisica di un'arena:
- **Blocchi**: Definizione dei blocchi da piazzare
- **Spawn points**: Punti di spawn per mob e giocatori
- **Bounds**: Confini dell'arena
- **Metadata**: Informazioni aggiuntive

#### Policy
Una policy definisce il comportamento di un'arena:
- **Mob config**: Quali mob spawnare
- **Wave config**: Configurazione wave
- **Loot config**: Drop e ricompense
- **Difficulty scaling**: Scalatura difficoltà

#### Build Process

```mermaid
sequenceDiagram
    participant Trigger
    participant PolicyResolver
    participant ArenaBuilder
    participant AsyncBuilder
    participant World

    Trigger->>PolicyResolver: resolve(context)
    PolicyResolver->>PolicyResolver: Match template + policy
    PolicyResolver->>ArenaBuilder: build(template, policy)
    ArenaBuilder->>ArenaBuilder: Validate
    ArenaBuilder->>AsyncBuilder: buildAsync()

    loop Chunk per chunk
        AsyncBuilder->>World: Place blocks
        AsyncBuilder->>AsyncBuilder: Check performance
        alt MSPT troppo alto
            AsyncBuilder->>AsyncBuilder: Pause/Throttle
        end
    end

    AsyncBuilder->>ArenaBuilder: Build complete
    ArenaBuilder->>Trigger: Arena ready
```

### File Principali

| File | Descrizione |
|------|-------------|
| `ArenaTemplateRegistry.java` | Registro template |
| `ArenaPolicyRegistry.java` | Registro policy |
| `PolicyResolver.java` | Risoluzione template+policy |
| `TemplateArenaBuilder.java` | Builder sincrono |
| `AsyncArenaBuilder.java` | Builder asincrono |
| `AsyncArenaBuildCoordinator.java` | Coordinatore build |
| `ArenaTelemetry.java` | Telemetry arena |
| `AlertRouter.java` | Routing alert |

---

## 2. Endurance Quest System

Sistema roguelike con wave progressive, perk, combo e ricompense.

### Architettura

```mermaid
flowchart TB
    subgraph Core["Core"]
        EQM["EnduranceQuestManager"]
        EEH["EnduranceEventHandler"]
    end

    subgraph Wave["Wave System"]
        WM["WaveManager"]
        BWS["BossWaveSystem"]
        WD["WaveDirective"]
    end

    subgraph Progression["Progression"]
        PS["PerkSystem"]
        CS["ComboSystem"]
        RS["RewardSystem"]
    end

    subgraph Gamification["Gamification"]
        SS["StyleSystem"]
        AS["AchievementSystem"]
        LS["LeaderboardSystem"]
    end

    EQM --> WM
    EQM --> PS
    EQM --> CS
    EQM --> RS
    WM --> BWS
    WM --> WD
    CS --> SS
```

### Flow di una Sessione

```mermaid
stateDiagram-v2
    [*] --> Setup: Avvia quest
    Setup --> WaveStart: Sessione pronta
    WaveStart --> Combat: Spawn mob
    Combat --> WaveComplete: Tutti i mob uccisi
    WaveComplete --> PerkSelection: Offri perk
    PerkSelection --> WaveStart: Perk selezionato
    WaveComplete --> BossWave: Wave boss
    BossWave --> Combat: Spawn boss
    Combat --> Victory: Quest completata
    Combat --> Defeat: Giocatore morto
    Victory --> Rewards: Calcola ricompense
    Defeat --> Rewards: Ricompense parziali
    Rewards --> [*]: Fine sessione
```

### Perk System

I perk sono potenziamenti temporanei selezionabili tra le wave:

```mermaid
flowchart LR
    Wave["Wave completata"] --> Offer["Offri 3 perk"]
    Offer --> Select["Giocatore seleziona"]
    Select --> Apply["Applica perk"]
    Apply --> Stack["Aggiorna stack"]
```

**Categorie Perk:**
- **Offensive**: Aumentano danno
- **Defensive**: Riducono danno subito
- **Utility**: Abilità speciali
- **Legendary**: Perk rari e potenti

### Combo System

Il sistema combo premia il gioco aggressivo:

```mermaid
flowchart LR
    Kill["Uccidi mob"] --> Increase["Aumenta combo"]
    Increase --> Rank["Calcola rank"]
    Rank --> Multiplier["Applica moltiplicatore"]

    Damage["Subisci danno"] --> Decrease["Diminuisci combo"]
    Time["Tempo inattività"] --> Reset["Reset combo"]
```

**Style Ranks:**
| Rank | Combo | Moltiplicatore |
|------|-------|----------------|
| D | 0-4 | 1.0x |
| C | 5-14 | 1.2x |
| B | 15-29 | 1.5x |
| A | 30-49 | 2.0x |
| S | 50-99 | 2.5x |
| SS | 100+ | 3.0x |

### File Principali

| File | Descrizione |
|------|-------------|
| `EnduranceQuestManager.java` | Orchestratore principale |
| `EnduranceEventHandler.java` | Handler eventi |
| `WaveManager.java` | Gestione wave |
| `BossWaveSystem.java` | Sistema boss |
| `PerkSystem.java` | Sistema perk |
| `ComboSystem.java` | Sistema combo |
| `RewardSystem.java` | Sistema ricompense |

---

## 3. Combat System

Sistema di combattimento avanzato con body-part detection e damage breakdown.

### Architettura

```mermaid
flowchart TB
    subgraph Detection["Hit Detection"]
        HH["HitHelper"]
        BPD["BodyPartDetector"]
    end

    subgraph Calculation["Damage Calculation"]
        DH["DamageHandler"]
        DB["DamageBreakdown"]
    end

    subgraph Tracking["Tracking"]
        DT["DamageTracker"]
        HD["HitData"]
    end

    HH --> BPD
    BPD --> DH
    DH --> DB
    DH --> DT
    DT --> HD
```

### Body Part Detection

Il sistema rileva quale parte del corpo viene colpita:

```mermaid
flowchart LR
    Hit["Raycast hit"] --> Bounds["Calcola bounds entità"]
    Bounds --> YPos["Analizza Y relativa"]
    YPos --> Part["Determina parte corpo"]

    Part --> Head["HEAD: Y > 80%"]
    Part --> Torso["TORSO: Y 40-80%"]
    Part --> Legs["LEGS: Y < 40%"]
```

**Moltiplicatori Danno:**
| Parte | Moltiplicatore |
|-------|----------------|
| HEAD | 2.0x |
| TORSO | 1.0x |
| LEGS | 0.75x |
| ARMS | 0.85x |

### Damage Breakdown

Il danno viene calcolato considerando:

```mermaid
flowchart TB
    Base["Danno Base"] --> Modifiers["Modificatori"]
    Modifiers --> ArmorPen["Penetrazione Armatura"]
    ArmorPen --> Reduction["Riduzione Armatura"]
    Reduction --> DamageType["Tipo Danno"]
    DamageType --> Final["Danno Finale"]

    Modifiers --> |"Perk"| M1["Perk Bonus"]
    Modifiers --> |"Combo"| M2["Combo Multiplier"]
    Modifiers --> |"Crit"| M3["Critical Hit"]
```

### File Principali

| File | Descrizione |
|------|-------------|
| `DamageHandler.java` | Handler principale danno |
| `HitHelper.java` | Utility hit detection |
| `DamageBreakdown.java` | Breakdown del danno |
| `DamageTracker.java` | Tracking danno |
| `HitData.java` | Dati hit |

---

## 4. Mailbox System

Sistema di messaggistica in-game con news, task e ticket.

### Architettura

```mermaid
flowchart TB
    subgraph Core["Core"]
        MM["MailboxManager"]
        NM["NewsManager"]
        TTM["TestTaskManager"]
    end

    subgraph Persistence["Persistence"]
        DMR["DuckDbMailboxRepository"]
        TR["TicketRepository"]
    end

    subgraph API["API"]
        MAS["MailboxApiServer"]
    end

    subgraph Client["Client"]
        MS["MailboxScreen"]
        NS["NewsScreen"]
        TTS["TesterTaskScreen"]
    end

    MM --> DMR
    NM --> DMR
    TTM --> DMR
    MAS --> MM
    MAS --> NM
    MAS --> TTM
    MS --> MM
    NS --> NM
    TTS --> TTM
```

### Tipi di Messaggio

| Tipo | Descrizione |
|------|-------------|
| `SYSTEM` | Messaggi di sistema |
| `PLAYER` | Messaggi tra giocatori |
| `REWARD` | Ricompense con allegato |
| `ANNOUNCEMENT` | Annunci globali |

### Allegati

I messaggi possono avere allegati (item) che vengono riscossi:

```mermaid
sequenceDiagram
    participant Player
    participant Mailbox
    participant Inventory

    Player->>Mailbox: Apri messaggio
    Mailbox->>Player: Mostra allegato
    Player->>Mailbox: Riscuoti allegato
    Mailbox->>Mailbox: attachment_claiming = true
    Mailbox->>Inventory: Dai item
    alt Successo
        Mailbox->>Mailbox: attachment_claimed = true
    else Inventario pieno
        Mailbox->>Mailbox: attachment_claiming = false
        Mailbox->>Player: "Inventario pieno"
    end
```

### File Principali

| File | Descrizione |
|------|-------------|
| `MailboxManager.java` | Manager messaggi |
| `NewsManager.java` | Manager news |
| `TestTaskManager.java` | Manager task |
| `DuckDbMailboxRepository.java` | Persistenza DuckDB |
| `MailboxApiServer.java` | API server (Javalin) |
| `MailboxScreen.java` | UI client |

---

## 5. Party System

Sistema per gestire gruppi di giocatori nelle quest multiplayer.

### Architettura

```mermaid
flowchart TB
    subgraph Core["Core"]
        PM["PartyManager"]
        PD["PartyData"]
    end

    subgraph Invites["Inviti"]
        PI["PartyInvite"]
    end

    subgraph Network["Network"]
        PNH["PartyNetworkHandler"]
        Payloads["Party*Payload"]
    end

    subgraph Client["Client"]
        PS["PartyScreen"]
    end

    PM --> PD
    PM --> PI
    PM --> PNH
    PNH --> Payloads
    PS --> PNH
```

### Party Lifecycle

```mermaid
stateDiagram-v2
    [*] --> Created: Leader crea party
    Created --> Inviting: Invia inviti
    Inviting --> Growing: Giocatore accetta
    Growing --> Inviting: Invia altri inviti
    Growing --> Ready: Party completo
    Ready --> InQuest: Avvia quest
    InQuest --> Ready: Quest finita
    Ready --> Disbanded: Leader scioglie
    Disbanded --> [*]
```

### File Principali

| File | Descrizione |
|------|-------------|
| `PartyManager.java` | Manager party |
| `PartyData.java` | Dati party |
| `PartyInvite.java` | Inviti |
| `PartyNetworkHandler.java` | Network handler |
| `PartyScreen.java` | UI client |

---

## 6. Instance Runtime

Sistema per gestire dimensioni dinamiche (istanze separate per arene/dungeon).

### Architettura

```mermaid
flowchart TB
    subgraph Core["Core"]
        IM["InstanceManager"]
        IR["InstanceRegistry"]
        ID["InstanceData"]
    end

    subgraph Recovery["Recovery"]
        RS["RecoverySystem"]
        PIS["PlayerInstanceSnapshot"]
    end

    subgraph Events["Events"]
        IEH["InstanceEventHandler"]
    end

    IM --> IR
    IM --> ID
    IM --> RS
    RS --> PIS
    IEH --> IM
```

### Instance Lifecycle

```mermaid
stateDiagram-v2
    [*] --> Creating: Richiedi istanza
    Creating --> Ready: Dimensione creata
    Ready --> InUse: Giocatore entra
    InUse --> Ready: Giocatore esce
    Ready --> Cleanup: Timeout/Manual
    Cleanup --> [*]: Dimensione rimossa
```

### Recovery System

Se un giocatore si disconnette in un'istanza:

```mermaid
sequenceDiagram
    participant Player
    participant Instance
    participant Recovery
    participant Snapshot

    Player->>Instance: Disconnect
    Instance->>Snapshot: Salva stato
    Snapshot->>Snapshot: Inventario, HP, posizione

    Note over Player: Riconnessione

    Player->>Recovery: Login
    Recovery->>Snapshot: Recupera stato
    Snapshot->>Player: Ripristina stato
    Recovery->>Instance: Teleporta in istanza
```

### File Principali

| File | Descrizione |
|------|-------------|
| `InstanceManager.java` | Manager istanze |
| `InstanceRegistry.java` | Registro istanze |
| `InstanceData.java` | Dati istanza |
| `RecoverySystem.java` | Sistema recovery |
| `PlayerInstanceSnapshot.java` | Snapshot giocatore |
| `InstanceEventHandler.java` | Handler eventi |

---

## 7. Telemetry Pipeline

Sistema per raccogliere, processare e visualizzare analytics.

### Architettura

```mermaid
flowchart TB
    subgraph Collection["Collection"]
        Events["Game Events"]
        TS["TelemetryService"]
        TE["TelemetryEvents"]
    end

    subgraph Processing["Processing"]
        DTS["DuckDBTelemetryService"]
        DBW["DuckDBBatchWriter"]
    end

    subgraph Storage["Storage"]
        DDB[(DuckDB)]
        QA["DuckDBQueryAPI"]
    end

    subgraph Presentation["Presentation"]
        TDS["TelemetryDashboardServer"]
        TAH["TelemetryAnalyticsHandlers"]
    end

    Events --> TS
    TS --> TE
    TE --> DTS
    DTS --> DBW
    DBW --> DDB
    DDB --> QA
    QA --> TDS
    TDS --> TAH
```

### Batch Writing

Per ottimizzare le performance, gli eventi vengono bufferizzati:

```mermaid
sequenceDiagram
    participant Event
    participant Service
    participant Writer
    participant Buffer
    participant DuckDB

    Event->>Service: logEvent()
    Service->>Writer: write(event)
    Writer->>Buffer: add(event)

    alt Buffer pieno (1000 eventi)
        Buffer->>DuckDB: Batch INSERT
    else Timeout (5 secondi)
        Buffer->>DuckDB: Batch INSERT
    else Flush manuale
        Buffer->>DuckDB: Batch INSERT
    end
```

### File Principali

| File | Descrizione |
|------|-------------|
| `TelemetryService.java` | Servizio principale |
| `TelemetryEvents.java` | Handler eventi |
| `DuckDBTelemetryService.java` | Servizio DuckDB |
| `DuckDBBatchWriter.java` | Writer batch |
| `DuckDBQueryAPI.java` | API query |
| `DuckDBSchemaManager.java` | Gestione schema |
| `TelemetryDashboardServer.java` | Server dashboard |

---

## Riferimenti

- [Architettura](ARCHITECTURE.md)
- [Database Schema](DATABASE.md)
- [Pannelli Esterni](PANELS.md)
- [Quickstart](QUICKSTART.md)
