# Architettura DevMod

> Ultimo aggiornamento: 2026-01-02
> Verificato: Corrisponde al codice attuale

## Vista d'Insieme

DevMod è una mod Minecraft (NeoForge 1.21.1) che aggiunge sistemi avanzati di combattimento, quest, arene e analytics.

```mermaid
flowchart TB
    subgraph Mod["DevMod (com.devmod)"]
        subgraph Server["Server Runtime"]
            Arena["Arena System"]
            Endurance["Endurance Quest"]
            Combat["Combat System"]
            Party["Party System"]
            Instance["Instance Runtime"]
            Mailbox["Mailbox System"]
            Telemetry["Telemetry Pipeline"]
        end

        subgraph Client["Client Layer"]
            UI["UI Screens"]
            Overlay["HUD Overlays"]
            Panels["Floating Panels"]
            Actions["Radial Actions"]
            Rendering["Debug Rendering"]
        end

        subgraph Network["Network Layer"]
            Packets["Packet Registry"]
            Handlers["Packet Handlers"]
            Validation["Packet Validation"]
        end

        subgraph Persistence["Persistence"]
            DuckDB[(DuckDB)]
            Config["Config TOML"]
            NDJSON["NDJSON Logs"]
        end
    end

    subgraph External["External"]
        Dashboard["Dashboard Web"]
        AdminPanel["Admin Panel"]
    end

    Arena --> Instance
    Endurance --> Combat
    Endurance --> Arena
    Combat --> Telemetry
    Endurance --> Telemetry
    Arena --> Telemetry
    Mailbox --> Telemetry

    Telemetry --> DuckDB
    Mailbox --> DuckDB

    Server <--> Network
    Client <--> Network

    Dashboard --> DuckDB
    AdminPanel --> Mailbox
```

---

## Struttura Package

```mermaid
flowchart LR
    subgraph Core["Sistemi Core"]
        arena["arena/"]
        endurance["endurance/"]
        combat["combat/"]
        damage["damage/"]
        party["party/"]
        runtime["runtime/"]
        mailbox["mailbox/"]
        telemetry["telemetry/"]
    end

    subgraph Infra["Infrastruttura"]
        network["network/"]
        config["config/"]
        events["events/"]
        util["util/"]
    end

    subgraph Client["Client"]
        client["client/"]
        actions["actions/"]
        debug["debug/"]
    end

    subgraph Compat["Compatibilità"]
        compat["compat/"]
        integration["integration/"]
    end
```

### Package Principali

| Package | Responsabilità |
|---------|----------------|
| `arena/` | Template arena, policy, build, observability |
| `endurance/` | Quest system, wave, perk, combo, reward, boss |
| `combat/` | Damage handler, hit detection, body parts |
| `damage/` | Calcolo danni, breakdown |
| `party/` | Gestione party multiplayer |
| `runtime/` | Instance manager, dynamic dimensions |
| `mailbox/` | Messaggi, news, task, ticket |
| `telemetry/` | Pipeline analytics, DuckDB, dashboard |
| `network/` | Packet registry, handler, validation |
| `config/` | Configurazione globale |
| `client/` | UI, overlay, rendering, panels |
| `actions/` | Radial menu, azioni |

---

## Flusso Dati

### Combat Flow

```mermaid
sequenceDiagram
    participant Player
    participant DamageHandler
    participant HitHelper
    participant Telemetry
    participant DuckDB

    Player->>DamageHandler: Attacca
    DamageHandler->>HitHelper: Calcola hit
    HitHelper->>HitHelper: Body part detection
    HitHelper->>DamageHandler: HitData
    DamageHandler->>DamageHandler: Calcola danno
    DamageHandler->>Player: Applica danno
    DamageHandler->>Telemetry: Log hit event
    Telemetry->>DuckDB: Batch insert
```

### Endurance Quest Flow

```mermaid
sequenceDiagram
    participant Player
    participant QuestManager
    participant WaveManager
    participant PerkSystem
    participant RewardSystem
    participant Telemetry

    Player->>QuestManager: Avvia quest
    QuestManager->>QuestManager: Setup sessione
    loop Per ogni wave
        QuestManager->>WaveManager: Avvia wave
        WaveManager->>WaveManager: Spawn mob
        Player->>WaveManager: Uccidi mob
        WaveManager->>QuestManager: Wave completata
        QuestManager->>PerkSystem: Offri perk
        PerkSystem->>Player: Mostra scelta
        Player->>PerkSystem: Seleziona perk
    end
    QuestManager->>RewardSystem: Calcola reward
    RewardSystem->>Player: Dai reward
    QuestManager->>Telemetry: Log sessione
```

### Arena Build Flow

```mermaid
sequenceDiagram
    participant Trigger
    participant PolicyResolver
    participant ArenaBuilder
    participant Instance
    participant Telemetry

    Trigger->>PolicyResolver: Richiedi arena
    PolicyResolver->>PolicyResolver: Risolvi template + policy
    PolicyResolver->>ArenaBuilder: Build arena
    ArenaBuilder->>Instance: Crea dimensione
    ArenaBuilder->>ArenaBuilder: Piazza blocchi (async)
    ArenaBuilder->>Telemetry: Log build metrics
    ArenaBuilder->>Trigger: Arena pronta
```

### Telemetry Pipeline

```mermaid
flowchart LR
    Events["Game Events"] --> Service["TelemetryService"]
    Service --> BatchWriter["DuckDBBatchWriter"]
    BatchWriter --> Buffer["Buffer (1000 eventi)"]
    Buffer --> |"Flush ogni 5s"| DuckDB[(DuckDB)]
    DuckDB --> Dashboard["Dashboard API"]
    DuckDB --> Export["Export CSV/JSON"]
```

---

## Componenti Chiave

### Server Side

#### Arena System

```mermaid
classDiagram
    class ArenaTemplateRegistry {
        +register(template)
        +get(id): ArenaTemplate
        +list(): List~ArenaTemplate~
    }

    class ArenaPolicyRegistry {
        +register(policy)
        +get(id): ArenaPolicy
    }

    class PolicyResolver {
        +resolve(context): ResolvedPolicy
    }

    class TemplateArenaBuilder {
        +build(template, policy)
        +rollback()
    }

    class AsyncArenaBuilder {
        +buildAsync(template, policy)
        +pause()
        +resume()
    }

    ArenaTemplateRegistry --> TemplateArenaBuilder
    ArenaPolicyRegistry --> PolicyResolver
    PolicyResolver --> TemplateArenaBuilder
    TemplateArenaBuilder --> AsyncArenaBuilder
```

#### Endurance System

```mermaid
classDiagram
    class EnduranceQuestManager {
        +startQuest(player, type)
        +endQuest(outcome)
        +getSession(player): Session
    }

    class WaveManager {
        +startWave(number)
        +completeWave()
        +getProgress(): WaveProgress
    }

    class PerkSystem {
        +offerPerks(player)
        +selectPerk(player, perk)
        +getActivePerks(): List~Perk~
    }

    class ComboSystem {
        +onKill(player)
        +onDamageTaken(player)
        +getCombo(): int
        +getRank(): StyleRank
    }

    class RewardSystem {
        +calculateReward(session)
        +grantReward(player)
    }

    EnduranceQuestManager --> WaveManager
    EnduranceQuestManager --> PerkSystem
    EnduranceQuestManager --> ComboSystem
    EnduranceQuestManager --> RewardSystem
```

#### Telemetry System

```mermaid
classDiagram
    class TelemetryService {
        +isEnabled(): boolean
        +logEvent(event)
        +flush()
    }

    class DuckDBTelemetryService {
        +getConnection(): Connection
        +isEnabled(): boolean
    }

    class DuckDBBatchWriter {
        +write(table, data)
        +flush()
    }

    class DuckDBQueryAPI {
        +query(sql): List
        +getTableCounts(): Map
    }

    class TelemetryDashboardServer {
        +start()
        +stop()
        +isRunning(): boolean
    }

    TelemetryService --> DuckDBTelemetryService
    DuckDBTelemetryService --> DuckDBBatchWriter
    DuckDBTelemetryService --> DuckDBQueryAPI
    TelemetryDashboardServer --> DuckDBQueryAPI
```

### Client Side

#### UI Layer

```mermaid
classDiagram
    class RadialMenuScreen {
        +show()
        +hide()
        +addAction(action)
    }

    class ActionRegistry {
        +register(action)
        +getActions(): List~RadialAction~
    }

    class UnifiedSettingsScreen {
        +show()
        +save()
    }

    class EnduranceQuestScreen {
        +show(session)
        +updateProgress()
    }

    class MailboxScreen {
        +show()
        +refresh()
    }

    RadialMenuScreen --> ActionRegistry
```

#### Radial Menu System (Data-Driven)

```mermaid
classDiagram
    class RadialMenuRuntimeRegistry {
        +initialize()
        +getCategories(macro): List~RadialCategory~
        +addCategory(macro, category)
        +reload()
    }

    class RadialMenuBuilder {
        +forMacro(macro): Builder
        +category(id): CategoryBuilder
        +register()
    }

    class RadialMenuDefinitionLoader {
        +load(): Map
        +getConfigPath(): Path
    }

    class VisibilitySupplierRegistry {
        +register(id, supplier)
        +get(id): BooleanSupplier
        +registerDefaults()
    }

    class ColorTokenResolver {
        +resolve(tokenName): int
        +isValidToken(name): boolean
    }

    RadialMenuRuntimeRegistry --> RadialMenuDefinitionLoader
    RadialMenuRuntimeRegistry --> RadialMenuBuilder
    RadialMenuDefinitionLoader --> VisibilitySupplierRegistry
    RadialMenuDefinitionLoader --> ColorTokenResolver
```

Il sistema Radial Menu supporta:

- **JSON Config**: `config/devmod/radial_menu_definitions.json`
- **Programmatic API**: `RadialMenuBuilder` per registrazione runtime
- **Visibility Suppliers**: Condizioni dinamiche per visibilità item
- **Color Tokens**: Riferimenti a `DesignTokens.Radial`

#### Floating Panels

```mermaid
classDiagram
    class FloatingPanelManager {
        +spawn(panel)
        +despawn(id)
        +tick()
        -panels: List~FloatingPanel~
    }

    class FloatingPanel {
        +render()
        +update()
        +getState(): PanelState
    }

    class CombatPanel {
        +showStats(stats)
    }

    class EntityInfoPanel {
        +showEntity(entity)
    }

    FloatingPanelManager --> FloatingPanel
    FloatingPanel <|-- CombatPanel
    FloatingPanel <|-- EntityInfoPanel
```

---

## Network

### Packet Flow

```mermaid
flowchart LR
    subgraph Client
        ClientHandler["Client Handler"]
    end

    subgraph Network
        ChannelId["ChannelId Registry"]
        Validator["PacketValidator"]
    end

    subgraph Server
        ServerHandler["Server Handler"]
    end

    ClientHandler --> |"Payload"| ChannelId
    ChannelId --> Validator
    Validator --> ServerHandler
    ServerHandler --> |"Response"| ChannelId
    ChannelId --> ClientHandler
```

### Packet Registration

```java
// ChannelId.java definisce tutti i packet ID
// NetworkHandler.java registra i payload via RegisterPayloadHandlersEvent
// PacketValidator.java valida i packet in arrivo
```

### Network Handlers (Domain Split)

I network handler sono suddivisi per dominio:

| Handler | Responsabilità |
|---------|----------------|
| `AbilityNetworkHandler` | Payload abilità |
| `ShieldNetworkHandler` | Payload scudi |
| `ConfigNetworkHandler` | Sync configurazione |
| `EnduranceNetworkHandler` | Quest endurance |
| `PartyNetworkHandler` | Gestione party |
| `MobItemNetworkHandler` | Mob equipment |
| `MailboxNetworkHandler` | Sistema mailbox |
| `NotificationNetworkHandler` | Notifiche |

### Payload Validation

```mermaid
flowchart LR
    Packet["Incoming Packet"] --> SizeCheck["Size Check"]
    SizeCheck --> |"< Limit"| RateLimit["Rate Limiter"]
    SizeCheck --> |"> Limit"| Reject["Reject + Metrics"]
    RateLimit --> |"Under Limit"| Process["Process"]
    RateLimit --> |"Over Limit"| Throttle["Throttle/Disconnect"]
    Reject --> Telemetry["Log to Telemetry"]
```

Componenti:

- **PayloadValidation**: Rate limiting per player/IP
- **SizedPayload**: Interface per payload con size estimation
- **IpRateLimiter**: Rate limit a livello IP
- **PacketValidator**: Validazione centralizzata

---

## Service Registry

Pattern DI per servizi core:

```mermaid
classDiagram
    class ServiceRegistry {
        +register(type, supplier)
        +get(type): T
        +override(type, supplier)
        +reset()
    }

    class Services {
        +party(): PartyManager
        +waves(): WaveManager
        +telemetry(): TelemetryService
        +mailbox(): MailboxService
    }

    ServiceRegistry --> Services
```

Vantaggi:

- Testing: Override dei servizi per test
- Lazy init: Servizi inizializzati on-demand
- Backward compat: Singleton esistenti funzionano ancora

---

## Event System

### Server Events

```mermaid
flowchart TB
    ServerStart["ServerStartedEvent"] --> ArenaBootstrap
    ServerStart --> TelemetryInit
    ServerStart --> MailboxInit

    ServerTick["ServerTickEvent"] --> ArenaUpdate
    ServerTick --> EnduranceUpdate
    ServerTick --> TelemetryFlush

    PlayerJoin["PlayerLoggedInEvent"] --> InstanceRecovery
    PlayerJoin --> MailboxSync

    PlayerDeath["LivingDeathEvent"] --> CombatLog
    PlayerDeath --> EnduranceHandle
```

### Client Events

```mermaid
flowchart TB
    ClientSetup["FMLClientSetupEvent"] --> UIInit
    ClientSetup --> OverlayInit
    ClientSetup --> KeybindInit

    RenderTick["RenderGuiLayerEvent"] --> OverlayRender
    RenderTick --> PanelRender

    KeyInput["KeyInputEvent"] --> RadialMenu
    KeyInput --> Mailbox
```

---

## Persistence

### DuckDB

Database embedded per telemetry e mailbox.

```mermaid
flowchart LR
    subgraph Telemetry["Telemetry DB"]
        Combat["combat_*"]
        Endurance["endurance_*"]
        Player["player_*"]
        Arena["arena_*"]
    end

    subgraph Mailbox["Mailbox DB"]
        Messages["mailbox_messages"]
        News["news_articles"]
        Tasks["test_tasks"]
    end

    Combat --> DuckDB[(DuckDB)]
    Endurance --> DuckDB
    Player --> DuckDB
    Arena --> DuckDB
    Messages --> DuckDB
    News --> DuckDB
    Tasks --> DuckDB
```

### Config

```
config/devmod/
├── devmod-common.toml      # Config comune
├── telemetry_settings.json # Settings telemetry
└── ...
```

---

## Compatibilità

### Mod Supportate

| Mod | Package | Descrizione |
|-----|---------|-------------|
| Pehkui | `compat/pehkui/` | Scaling hitbox |
| Iron's Spellbooks | `compat/irons/` | Integrazione spell |
| Little Tiles | `integration/` | Compatibilità tiles |

### Integration Pattern

```mermaid
flowchart LR
    DevMod --> |"Compat Check"| ModIntegrationManager
    ModIntegrationManager --> |"Se presente"| PehkuiCompat
    ModIntegrationManager --> |"Se presente"| IronsCompat
    ModIntegrationManager --> |"Se presente"| LittleTilesIntegration
```

---

## Entry Points

### Mod Bootstrap

| Entry Point | File | Descrizione |
|-------------|------|-------------|
| `@Mod("devmod")` | `DevMod.java` | Bootstrap comune |
| Client init | `DevModClient.java` | Init client |
| Config events | `DevMod.java` | Load/reload config |

### Commands

| Comando | Handler | Descrizione |
|---------|---------|-------------|
| `/devtest` | `TestHarnessCommands` | Debug e test |
| `/arena` | `ArenaCommands` | Operazioni arena |
| `/devmod telemetry` | `TelemetryReloadCommand` | Gestione telemetry |
| `/devmod dashboard` | `DashboardCommand` | Dashboard server |
| `/mailbox` | `MailboxCommands` | Admin mailbox |

### Keybinds

| Tasto | Azione | Handler |
|-------|--------|---------|
| G | Menu radiale | `KeyInputHandler` |
| M | Mailbox | `KeyInputHandler` |
| T | Task tester | `KeyInputHandler` |

---

## Riferimenti

- [Database Schema](DATABASE.md)
- [Pannelli Esterni](PANELS.md)
- [Sistemi](SYSTEMS.md)
- [Quickstart](QUICKSTART.md)
