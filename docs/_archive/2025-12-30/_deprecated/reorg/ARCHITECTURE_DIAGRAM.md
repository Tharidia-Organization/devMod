# Architettura DevMod - Documentazione Completa

> **Versione**: 2.0 Post-Riorganizzazione
> **Data**: 24 Dicembre 2024
> **Stato Build**: PASS (2740 test superati)
> **Classi Java Totali**: 862

---

## Indice

1. [Panoramica Generale](#1-panoramica-generale)
2. [Struttura Root Package](#2-struttura-root-package)
3. [Layer Architetturale](#3-layer-architetturale)
4. [Moduli Core](#4-moduli-core)
5. [Sistema Arena](#5-sistema-arena)
6. [Sistema Combat](#6-sistema-combat)
7. [Sistema UI](#7-sistema-ui)
8. [Sistema Endurance](#8-sistema-endurance)
9. [Infrastruttura](#9-infrastruttura)
10. [Flusso delle Dipendenze](#10-flusso-delle-dipendenze)
11. [Riepilogo Quantitativo](#11-riepilogo-quantitativo)

---

## 1. Panoramica Generale

### Filosofia Architetturale

DevMod segue un'architettura a **layer separati** dove ogni modulo ha responsabilità ben definite. La riorganizzazione ha eliminato il namespace legacy `com.devmod` consolidando tutto sotto `com.devmod.*`.

### Principi Guida

- **Separazione delle Responsabilità**: Ogni package gestisce un solo dominio
- **Root Package Minimale**: Solo 3 file entrypoint nel root
- **Modularità**: I sistemi sono indipendenti e comunicano via interfacce
- **Testabilità**: Struttura che favorisce unit testing e game testing

```mermaid
graph TB
    subgraph FILOSOFIA["Principi Architetturali"]
        direction LR
        P1["Separazione<br/>Responsabilità"]
        P2["Root<br/>Minimale"]
        P3["Modularità<br/>Sistemi"]
        P4["Testabilità<br/>Integrata"]
    end

    subgraph RISULTATO["Risultato Riorganizzazione"]
        R1["862 classi<br/>organizzate"]
        R2["34 package<br/>tematici"]
        R3["3 file<br/>nel root"]
        R4["2740 test<br/>superati"]
    end

    FILOSOFIA --> RISULTATO
```

---

## 2. Struttura Root Package

### Il Cuore del Mod

Il root package `com.devmod/` contiene **esclusivamente** i tre file di ingresso del mod. Questa scelta architetturale garantisce chiarezza immediata su dove inizia l'esecuzione.

```mermaid
graph TB
    subgraph ROOT["com.devmod/ - Root Package"]
        direction TB

        DM["<b>DevMod.java</b><br/>━━━━━━━━━━━━━━━━━━━━<br/>Entrypoint principale del mod<br/>Registra tutti i sistemi server-side<br/>Inizializza registry, network, telemetry<br/>Gestisce lifecycle del mod"]

        DMC["<b>DevModClient.java</b><br/>━━━━━━━━━━━━━━━━━━━━<br/>Entrypoint lato client<br/>Registra keybindings e renderer<br/>Inizializza UI e HUD overlay<br/>Gestisce eventi client-only"]

        MC["<b>ModConfig.java</b><br/>━━━━━━━━━━━━━━━━━━━━<br/>Configurazione radice<br/>Espone flag globali<br/>Collegamento a NeoForge Config<br/>Valori default del mod"]
    end

    DM --> |"Server"| SYSTEMS["Sistemi Server"]
    DMC --> |"Client"| PRESENTATION["Layer Presentazione"]
    MC --> |"Configura"| DM
    MC --> |"Configura"| DMC

    style ROOT fill:#1a1a2e,stroke:#16213e,color:#eee
    style DM fill:#0f3460,stroke:#16213e,color:#fff
    style DMC fill:#0f3460,stroke:#16213e,color:#fff
    style MC fill:#0f3460,stroke:#16213e,color:#fff
```

### Perché Solo 3 File?

| Motivazione | Beneficio |
|-------------|-----------|
| **Chiarezza** | Sviluppatore nuovo trova subito gli entrypoint |
| **Manutenibilità** | Meno file = meno conflitti merge |
| **Convenzione NeoForge** | Allineamento con best practice della community |
| **Separazione Client/Server** | Evita errori di caricamento classi client su server dedicati |

---

## 3. Layer Architetturale

### Struttura a 5 Layer

L'architettura è organizzata in 5 layer distinti, dal più basso (Core) al più alto (Presentation).

```mermaid
graph TB
    subgraph L5["LAYER 5: PRESENTATION - Interfaccia Utente"]
        direction LR
        UI["<b>ui/</b><br/>227 classi<br/>━━━━━━━━━━<br/>Schermate, editor,<br/>radial menu, wizard"]
        HUD["<b>hud/</b><br/>29 classi<br/>━━━━━━━━━━<br/>Overlay in-game,<br/>barre salute, info"]
        PANELS["<b>panels/</b><br/>14 classi<br/>━━━━━━━━━━<br/>Pannelli testing,<br/>debug visivo"]
        RENDERING["<b>rendering/</b><br/>27 classi<br/>━━━━━━━━━━<br/>Renderer custom,<br/>effetti visuali"]
    end

    subgraph L4["LAYER 4: GAMEPLAY - Logica di Gioco"]
        direction LR
        ARENA["<b>arena/</b><br/>188 classi<br/>━━━━━━━━━━<br/>Sistema arene,<br/>template, spawn"]
        ENDURANCE["<b>endurance/</b><br/>69 classi<br/>━━━━━━━━━━<br/>Quest wave-based,<br/>perk, reward"]
        PARTY["<b>party/</b><br/>20 classi<br/>━━━━━━━━━━<br/>Sistema gruppi,<br/>matchmaking"]
        QUEST["<b>quest/</b><br/>5 classi<br/>━━━━━━━━━━<br/>Sistema quest<br/>generico"]
    end

    subgraph L3["LAYER 3: SYSTEMS - Sistemi di Gioco"]
        direction LR
        COMBAT["<b>combat/</b><br/>9 classi<br/>━━━━━━━━━━<br/>Danno, hit detection,<br/>body parts"]
        COLLISION["<b>collision/</b><br/>13 classi<br/>━━━━━━━━━━<br/>Hitbox custom,<br/>ray tracing"]
        ABILITIES["<b>abilities/</b><br/>7 classi<br/>━━━━━━━━━━<br/>Abilità giocatore,<br/>cooldown"]
        EFFECTS["<b>effects/</b><br/>5 classi<br/>━━━━━━━━━━<br/>Effetti status,<br/>buff/debuff"]
    end

    subgraph L2["LAYER 2: INFRASTRUCTURE - Servizi Trasversali"]
        direction LR
        NETWORK["<b>network/</b><br/>31 classi<br/>━━━━━━━━━━<br/>Payload, sicurezza,<br/>sync client-server"]
        TELEMETRY["<b>telemetry/</b><br/>62 classi<br/>━━━━━━━━━━<br/>Analytics, DuckDB,<br/>heatmap, metriche"]
        INSTANCE["<b>instance/</b><br/>9 classi<br/>━━━━━━━━━━<br/>Gestione istanze,<br/>stato giocatore"]
        MIXIN["<b>mixin/</b><br/>8 classi<br/>━━━━━━━━━━<br/>Hook nel codice<br/>Minecraft vanilla"]
    end

    subgraph L1["LAYER 1: CORE - Fondamenta"]
        direction LR
        CONFIG["<b>config/</b><br/>9 classi<br/>━━━━━━━━━━<br/>Gestori config<br/>per ogni sistema"]
        STATS["<b>stats/</b><br/>5 classi<br/>━━━━━━━━━━<br/>Dati statistiche<br/>armi, armature"]
        COMPONENTS["<b>components/</b><br/>6 classi<br/>━━━━━━━━━━<br/>Data components<br/>MC 1.21+"]
        ATTRIBUTES["<b>attributes/</b><br/>6 classi<br/>━━━━━━━━━━<br/>Attributi custom<br/>entità"]
        EVENTS["<b>events/</b><br/>9 classi<br/>━━━━━━━━━━<br/>Handler eventi<br/>NeoForge"]
    end

    L5 --> L4
    L4 --> L3
    L3 --> L2
    L2 --> L1

    style L5 fill:#2d3436,stroke:#636e72,color:#dfe6e9
    style L4 fill:#2d3436,stroke:#636e72,color:#dfe6e9
    style L3 fill:#2d3436,stroke:#636e72,color:#dfe6e9
    style L2 fill:#2d3436,stroke:#636e72,color:#dfe6e9
    style L1 fill:#2d3436,stroke:#636e72,color:#dfe6e9
```

### Regole di Dipendenza

| Layer | Può Dipendere Da | Non Può Dipendere Da |
|-------|------------------|----------------------|
| Presentation | Gameplay, Systems, Infrastructure, Core | - |
| Gameplay | Systems, Infrastructure, Core | Presentation |
| Systems | Infrastructure, Core | Presentation, Gameplay |
| Infrastructure | Core | Presentation, Gameplay, Systems |
| Core | Nessuno (solo librerie esterne) | Tutti gli altri layer |

---

## 4. Moduli Core

### Dettaglio Package Fondamentali

Questi package formano la base su cui tutti gli altri sistemi si appoggiano.

```mermaid
graph TB
    subgraph CONFIG_DETAIL["config/ - Gestione Configurazioni (9 classi)"]
        direction TB

        C_MAIN["<b>Config.java</b><br/>Configurazione principale del mod<br/>Valori globali, intervalli tick, flag feature"]

        C_EDITOR["<b>EditorClientConfig.java</b><br/>Configurazione editor lato client<br/>Preferenze UI, colori, layout"]

        subgraph CONFIG_MANAGERS["Manager per Tipo Item"]
            CM1["ArmorConfigManager<br/>Statistiche armature"]
            CM2["WeaponConfigManager<br/>Statistiche armi"]
            CM3["MobConfigManager<br/>Statistiche mob custom"]
            CM4["FoodConfigManager<br/>Proprietà cibi"]
            CM5["FuelConfigManager<br/>Tempi combustione"]
            CM6["UsableConfigManager<br/>Item usabili"]
        end

        C_PRESET["<b>MobPresetManager.java</b><br/>Gestione preset mob salvati<br/>Caricamento/salvataggio JSON"]
    end

    subgraph STATS_DETAIL["stats/ - Classi Dati Statistiche (5 classi)"]
        direction TB

        S1["<b>ArmorStats.java</b><br/>Difesa, resistenza, bonus set<br/>Penalità movimento, durabilità"]

        S2["<b>WeaponStats.java</b><br/>Danno base, velocità attacco<br/>Reach, knockback, crit chance"]

        S3["<b>FoodStats.java</b><br/>Nutrizione, saturazione<br/>Effetti, tempo consumo"]

        S4["<b>FuelStats.java</b><br/>Tempo combustione<br/>Moltiplicatore efficienza"]

        S5["<b>UsableStats.java</b><br/>Cooldown, durata effetto<br/>Tipo utilizzo"]
    end

    subgraph COMPONENTS_DETAIL["components/ - Data Components MC 1.21+ (6 classi)"]
        direction TB

        CP1["<b>ArmorComponents.java</b><br/>Registra DataComponentType per armature<br/>Collegamento stats → ItemStack"]

        CP2["<b>WeaponComponents.java</b><br/>Registra DataComponentType per armi<br/>Persistenza dati su item"]

        CP3["<b>RangedComponents.java</b><br/>Componenti per armi a distanza<br/>Archi, balestre, proiettili"]

        CP4["<b>FoodComponents.java</b><br/>Componenti cibi custom"]

        CP5["<b>FuelComponents.java</b><br/>Componenti combustibili"]

        CP6["<b>UsableComponents.java</b><br/>Componenti item usabili"]
    end

    subgraph EVENTS_DETAIL["events/ - Handler Eventi NeoForge (9 classi)"]
        direction TB

        E1["<b>ClientModEvents.java</b><br/>Eventi lato client: render, input, GUI<br/>Registrazione layer HUD"]

        E2["<b>CommonModEvents.java</b><br/>Eventi comuni client/server<br/>Registrazione attributi, capability"]

        E3["<b>GlobalMobEvents.java</b><br/>Eventi spawn mob, AI modification<br/>Applicazione stats custom"]

        E4["<b>CombatEvents.java</b><br/>Eventi combattimento<br/>Intercettazione danno"]

        E5["<b>ArrowEvents.java</b><br/>Eventi frecce e proiettili"]

        E6["<b>InteractionEvents.java</b><br/>Interazioni giocatore-mondo"]

        E7["<b>FoodEvents.java</b><br/>Consumo cibo custom"]

        E8["<b>FuelEvents.java</b><br/>Combustione custom"]

        E9["<b>UsableEvents.java</b><br/>Utilizzo item custom"]
    end

    CONFIG_DETAIL --> STATS_DETAIL
    STATS_DETAIL --> COMPONENTS_DETAIL
    COMPONENTS_DETAIL --> EVENTS_DETAIL

    style CONFIG_DETAIL fill:#1e272e,stroke:#485460,color:#d2dae2
    style STATS_DETAIL fill:#1e272e,stroke:#485460,color:#d2dae2
    style COMPONENTS_DETAIL fill:#1e272e,stroke:#485460,color:#d2dae2
    style EVENTS_DETAIL fill:#1e272e,stroke:#485460,color:#d2dae2
```

---

## 5. Sistema Arena

### Panoramica

Il sistema Arena è il modulo più complesso del mod con **188 classi** organizzate in **55 subpackage**. Gestisce la creazione, esecuzione e monitoraggio di arene di combattimento.

```mermaid
graph TB
    subgraph ARENA_MAIN["arena/ - Sistema Arena Completo (188 classi)"]
        direction TB

        subgraph ARENA_CORE["CORE - Nucleo del Sistema"]
            direction LR

            REG["<b>registry/ (30)</b><br/>━━━━━━━━━━━━━━<br/>ArenaTemplateRegistry<br/>ArenaTemplate<br/>TemplateValidator<br/>TemplateLoader<br/>ManifestReader<br/>VersionManager"]

            BLD["<b>builder/ (10)</b><br/>━━━━━━━━━━━━━━<br/>ArenaBuilder<br/>BuildTransaction<br/>BlockPlacement<br/>StructureResolver<br/>BuildProgress"]

            CMD["<b>command/ (3)</b><br/>━━━━━━━━━━━━━━<br/>ArenaCommand<br/>TestCommand<br/>AdminCommand"]
        end

        subgraph ARENA_POLICY["POLICY - Regole e Override"]
            direction LR

            POL["<b>policy/ (9)</b><br/>━━━━━━━━━━━━━━<br/>PolicyResolver<br/>ArenaPolicy<br/>PolicyValidator<br/>DefaultPolicies<br/>PolicyMerger"]

            OVR["<b>override/ (8)</b><br/>━━━━━━━━━━━━━━<br/>OverrideManager<br/>TemplateOverride<br/>RuntimeOverride<br/>OverrideStack"]

            CFG["<b>config/ (5)</b><br/>━━━━━━━━━━━━━━<br/>ArenaConfig<br/>SpawnConfig<br/>RewardConfig<br/>DifficultyConfig"]
        end

        subgraph ARENA_RUNTIME["RUNTIME - Esecuzione"]
            direction LR

            SPN["<b>spawn/ (6)</b><br/>━━━━━━━━━━━━━━<br/>SpawnManager<br/>SpawnSlot<br/>RuntimeSpawnValidator<br/>SpawnQueue"]

            CLN["<b>cleanup/ (6)</b><br/>━━━━━━━━━━━━━━<br/>CleanupManager<br/>EntityRemover<br/>BlockRestorer<br/>StateReset"]

            POOL["<b>pool/ (4)</b><br/>━━━━━━━━━━━━━━<br/>ArenaPool<br/>PoolManager<br/>PoolConfig<br/>Recycling"]

            SNP["<b>snapshot/ (3)</b><br/>━━━━━━━━━━━━━━<br/>ArenaSnapshot<br/>StateCapture<br/>Restoration"]

            IDN["<b>identity/ (3)</b><br/>━━━━━━━━━━━━━━<br/>ArenaIdentity<br/>ArenaHandle<br/>IdentityManager"]

            INST["<b>instance/ (4)</b><br/>━━━━━━━━━━━━━━<br/>ArenaInstance<br/>InstanceLifecycle<br/>InstanceState"]
        end

        subgraph ARENA_MONITOR["MONITORING - Osservabilità"]
            direction LR

            AUT["<b>autosmoke/ (8)</b><br/>━━━━━━━━━━━━━━<br/>AutosmokeGuard<br/>SmokeScheduler<br/>SmokeResult<br/>HealthCheck"]

            ALR["<b>alert/ (8)</b><br/>━━━━━━━━━━━━━━<br/>AlertManager<br/>AlertRule<br/>AlertChannel<br/>Notification"]

            LOG["<b>logging/ (4)</b><br/>━━━━━━━━━━━━━━<br/>ArenaLogger<br/>EventLog<br/>AuditTrail"]

            MET["<b>metrics/ (3)</b><br/>━━━━━━━━━━━━━━<br/>MetricsCollector<br/>PerformanceMetrics<br/>UsageStats"]

            MON["<b>monitoring/ (3)</b><br/>━━━━━━━━━━━━━━<br/>DashboardData<br/>HealthStatus<br/>Watchdog"]
        end

        subgraph ARENA_SAFETY["SAFETY - Gestione Errori"]
            direction LR

            FLB["<b>fallback/ (4)</b><br/>━━━━━━━━━━━━━━<br/>FallbackStrategy<br/>GracefulDegradation<br/>RecoveryPlan"]

            RCV["<b>recovery/ (3)</b><br/>━━━━━━━━━━━━━━<br/>RecoveryManager<br/>StateRecovery<br/>DataIntegrity"]

            SEC["<b>security/ (3)</b><br/>━━━━━━━━━━━━━━<br/>SecurityValidator<br/>AccessControl<br/>RateLimiter"]

            VAL["<b>validation/ (3)</b><br/>━━━━━━━━━━━━━━<br/>RuntimeValidator<br/>PreflightCheck<br/>ValidationResult"]
        end

        subgraph ARENA_UI["UI & ANALYTICS"]
            direction LR

            AHUD["<b>hud/ (4)</b><br/>━━━━━━━━━━━━━━<br/>ArenaHUD<br/>ProgressBar<br/>WaveIndicator"]

            ATEL["<b>telemetry/ (3)</b><br/>━━━━━━━━━━━━━━<br/>ArenaTelemetry<br/>EventTracking<br/>Analytics"]

            AUI["<b>ui/ (3)</b><br/>━━━━━━━━━━━━━━<br/>ArenaTestWizard<br/>TemplateSelector<br/>ConfigPanel"]
        end

        subgraph ARENA_OTHER["ALTRI SUBPACKAGE (48+ classi)"]
            direction LR

            GAM["gamification/<br/>Achievements, Score"]
            REW["rewards/<br/>RewardSystem"]
            LDB["leaderboard/<br/>Rankings"]
            NET["network/<br/>ArenaPackets"]
            API["api/<br/>PublicAPI"]
            MIG["migration/<br/>VersionMigration"]
        end
    end

    ARENA_CORE --> ARENA_POLICY
    ARENA_POLICY --> ARENA_RUNTIME
    ARENA_RUNTIME --> ARENA_MONITOR
    ARENA_MONITOR --> ARENA_SAFETY
    ARENA_SAFETY --> ARENA_UI
    ARENA_UI --> ARENA_OTHER

    style ARENA_MAIN fill:#0a0a0a,stroke:#333,color:#eee
```

### Flusso Lifecycle Arena

```mermaid
sequenceDiagram
    participant U as Utente
    participant R as Registry
    participant B as Builder
    participant P as Pool
    participant I as Instance
    participant M as Monitor

    U->>R: Richiedi template "dungeon_boss"
    R->>R: Valida template
    R-->>B: Template validato

    B->>P: Richiedi slot arena
    P-->>B: Slot assegnato

    B->>B: Costruisci struttura
    B->>B: Configura spawn points
    B->>B: Applica policy

    B-->>I: Arena pronta

    I->>M: Registra istanza
    M->>M: Avvia monitoring

    loop Durante Esecuzione
        I->>M: Invia metriche
        M->>M: Verifica salute
    end

    I->>I: Arena completata
    I->>P: Rilascia slot
    P->>P: Cleanup & recycle
```

---

## 6. Sistema Combat

### Architettura Combattimento

Il sistema combat gestisce il calcolo del danno, la hit detection con body parts, e gli effetti visuali.

```mermaid
graph TB
    subgraph COMBAT_SYSTEM["Sistema Combat Completo"]
        direction TB

        subgraph COMBAT_PKG["combat/ - Core Combat (9 classi)"]
            direction TB

            DH["<b>DamageHandler.java</b><br/>━━━━━━━━━━━━━━━━━━━━━━━━━━<br/>Cuore del sistema danno<br/>Intercetta LivingIncomingDamageEvent<br/>Calcola danno finale con:<br/>• Armor penetration<br/>• Body part multiplier<br/>• Resistenze elementali<br/>• Critical hit chance<br/>Circa 800 righe di logica"]

            HH["<b>HitHelper.java</b><br/>━━━━━━━━━━━━━━━━━━━━━━━━━━<br/>Utility ray tracing<br/>Determina body part colpita:<br/>• HEAD (1.5x danno)<br/>• TORSO (1.0x danno)<br/>• ARMS (0.75x danno)<br/>• LEGS (0.8x danno)<br/>Usa AABB intersection"]

            HC["<b>HitContext.java</b><br/>━━━━━━━━━━━━━━━━━━━━━━━━━━<br/>Contesto hit temporaneo<br/>Memorizza body part + armor pen<br/>Cleanup automatico ogni tick<br/>Evita ricalcolo in telemetry"]

            ADT["<b>ActualDamageTracker.java</b><br/>━━━━━━━━━━━━━━━━━━━━━━━━━━<br/>Traccia danno effettivo<br/>Differenza tra danno richiesto<br/>e danno applicato (post-armor)"]

            RH["<b>RangedHooks.java</b><br/>━━━━━━━━━━━━━━━━━━━━━━━━━━<br/>Hook per armi a distanza<br/>Modifica proiettili<br/>Calcolo drop-off distanza"]

            SD["<b>ShieldDeflector.java</b><br/>━━━━━━━━━━━━━━━━━━━━━━━━━━<br/>Sistema deflection scudi<br/>Calcolo angolo impatto<br/>Effetti prismatici"]

            VFX["<b>WeaponTrailVFX.java</b><br/>━━━━━━━━━━━━━━━━━━━━━━━━━━<br/>Effetti visuali swing arma<br/>Trail particelle<br/>Rendering custom"]
        end

        subgraph COLLISION_PKG["collision/ - Hit Detection (13 classi)"]
            direction TB

            BPH["<b>BodyPartHitbox.java</b><br/>Definizione hitbox per body part<br/>Offset e dimensioni relative"]

            HBM["<b>HitboxManager.java</b><br/>Gestione hitbox per entity type<br/>Cache e ottimizzazione"]

            COL_OTHER["RayTracer, AABBHelper,<br/>CollisionResolver, HitResult,<br/>PrecisionHit, etc."]
        end

        subgraph DAMAGE_PKG["damage/ - Tipi Danno (2 classi)"]
            DT["<b>Damage Types</b><br/>Definizione tipi danno custom<br/>Collegamento a DamageSource MC"]
        end

        subgraph EFFECTS_PKG["effects/ - Effetti Status (5 classi)"]
            EFF["<b>Effect System</b><br/>Buff/Debuff custom<br/>Stacking rules<br/>Duration management"]
        end
    end

    COMBAT_PKG --> COLLISION_PKG
    COLLISION_PKG --> DAMAGE_PKG
    DAMAGE_PKG --> EFFECTS_PKG

    style COMBAT_SYSTEM fill:#1a0000,stroke:#4a0000,color:#ffcccc
```

### Flusso Calcolo Danno

```mermaid
flowchart TD
    START([Attacco Iniziato]) --> RAY[Ray Trace verso Target]
    RAY --> BP{Body Part?}

    BP -->|HEAD| HEAD[Moltiplicatore 1.5x]
    BP -->|TORSO| TORSO[Moltiplicatore 1.0x]
    BP -->|ARMS| ARMS[Moltiplicatore 0.75x]
    BP -->|LEGS| LEGS[Moltiplicatore 0.8x]

    HEAD --> BASE[Calcola Danno Base]
    TORSO --> BASE
    ARMS --> BASE
    LEGS --> BASE

    BASE --> WEAPON[+ Bonus Arma]
    WEAPON --> CRIT{Critical Hit?}

    CRIT -->|Sì| CRITDMG[Danno x 1.5]
    CRIT -->|No| NORMAL[Danno Normale]

    CRITDMG --> ARMOR[Applica Riduzione Armor]
    NORMAL --> ARMOR

    ARMOR --> PEN[- Armor Penetration]
    PEN --> RESIST[- Resistenze Elementali]
    RESIST --> FINAL[Danno Finale]

    FINAL --> APPLY([Applica a Entity])
    APPLY --> LOG[Log Telemetry]
```

---

## 7. Sistema UI

### Panoramica Interfaccia Utente

Il sistema UI è il più grande del mod con **227 classi** che gestiscono ogni aspetto dell'interfaccia.

```mermaid
graph TB
    subgraph UI_SYSTEM["Sistema UI Completo (227 classi)"]
        direction TB

        subgraph UI_EDITOR["editor/ - Editor Item (~80 classi)"]
            direction TB

            ED_MAIN["<b>ItemEditorScreen.java</b><br/>Schermata principale editor<br/>Tab-based navigation<br/>Preview real-time item"]

            ED_COMP["<b>components/</b><br/>EditorButton, Slider,<br/>ColorPicker, NumberInput,<br/>Dropdown, Toggle, etc."]

            ED_TABS["<b>tabs/</b><br/>GeneralTab, StatsTab,<br/>EffectsTab, VisualsTab,<br/>AdvancedTab"]

            ED_SYS["<b>systems/</b><br/>UndoRedo, Validation,<br/>Preview, Export/Import"]
        end

        subgraph UI_SCREENS["screens/ - Schermate (~40 classi)"]
            direction TB

            SC_MOB["<b>MobConfigScreen</b><br/>Configurazione mob custom<br/>Stats, equipment, AI"]

            SC_TEL["<b>TelemetryDashboardScreen</b><br/>Visualizzazione analytics<br/>Grafici, heatmap"]

            SC_SET["<b>SettingsScreen</b><br/>Impostazioni mod<br/>Keybindings, preferenze"]

            SC_OTHER["WelcomeScreen,<br/>ArenaSelector,<br/>PartyManager, etc."]
        end

        subgraph UI_RADIAL["radial/ - Menu Radiale (~15 classi)"]
            direction TB

            RAD_MAIN["<b>RadialMenuScreenV3.java</b><br/>Menu radiale principale<br/>Azioni rapide context-aware"]

            RAD_ACT["<b>RadialAction.java</b><br/>Definizione azioni<br/>Icon, callback, conditions"]

            RAD_STATE["<b>RadialMenuState.java</b><br/>Stato menu, selezione,<br/>hover, animazioni"]
        end

        subgraph UI_UNIFIED["unified/ - UI Unificata (~20 classi)"]
            direction TB

            UNI_SET["<b>UnifiedSettingsScreen</b><br/>Settings consolidate<br/>Tutte le opzioni in un posto"]

            UNI_PAG["<b>pages/</b><br/>GeneralPage, CombatPage,<br/>TelemetryPage, DebugPage"]
        end

        subgraph UI_COMP["components/ - Componenti Riutilizzabili (~30 classi)"]
            direction TB

            COMP_BTN["Buttons, IconButton,<br/>ToggleButton, ButtonRow"]

            COMP_INPUT["TextInput, NumberField,<br/>SearchBox, Autocomplete"]

            COMP_DISPLAY["ProgressBar, Chart,<br/>Table, List, Grid"]

            COMP_DIALOG["ConfirmDialog, AlertDialog,<br/>InputDialog, FileDialog"]
        end

        subgraph UI_TESTING["testing/ - Pannelli Test (~25 classi)"]
            TEST_PANEL["TestingHub,<br/>WeaponTestPanel,<br/>ArmorTestPanel,<br/>CombatSimulator"]
        end

        subgraph UI_WIZARD["wizard/ - Wizard (~10 classi)"]
            WIZ["QuickTestWizard,<br/>SetupWizard,<br/>ImportWizard"]
        end
    end

    subgraph HUD_SYSTEM["hud/ - Overlay In-Game (29 classi)"]
        direction TB

        HUD_QUICK["<b>QuickHelpOverlay</b><br/>Help contestuale<br/>Shortcut reminder"]

        HUD_IMPACT["<b>Impact3DPanelManager</b><br/>Visualizzazione impatti<br/>Damage numbers 3D"]

        HUD_DEBUG["<b>DebugOverlay</b><br/>Info debug<br/>Performance stats"]

        HUD_OTHER["HealthBar, ManaBar,<br/>CooldownDisplay,<br/>ComboCounter, etc."]
    end

    subgraph PANELS_SYSTEM["panels/ - Pannelli Speciali (14 classi)"]
        PAN["Testing panels,<br/>Debug panels,<br/>Admin panels"]
    end

    subgraph RENDERING_SYSTEM["rendering/ - Rendering Custom (27 classi)"]
        RND_WORLD["<b>WorldRenderEvents</b><br/>Rendering mondo<br/>Effetti custom"]

        RND_ENTITY["Entity renderers,<br/>Hitbox visualization,<br/>Trail effects"]

        RND_UI["UI renderers,<br/>Custom widgets,<br/>Animations"]
    end

    UI_SYSTEM --> HUD_SYSTEM
    HUD_SYSTEM --> PANELS_SYSTEM
    PANELS_SYSTEM --> RENDERING_SYSTEM

    style UI_SYSTEM fill:#001a00,stroke:#004d00,color:#ccffcc
    style HUD_SYSTEM fill:#001a00,stroke:#004d00,color:#ccffcc
```

---

## 8. Sistema Endurance

### Quest Wave-Based

Il sistema Endurance gestisce sfide a onde progressive con sistema perk, shop e reward.

```mermaid
graph TB
    subgraph ENDURANCE_SYSTEM["endurance/ - Sistema Quest Wave-Based (69 classi)"]
        direction TB

        subgraph END_CORE["Core Management"]
            direction LR

            EQM["<b>EnduranceQuestManager.java</b><br/>━━━━━━━━━━━━━━━━━━━━━━<br/>Manager principale<br/>~1800 righe<br/>Gestisce stato quest,<br/>transizioni wave,<br/>condizioni vittoria/sconfitta"]

            WM["<b>WaveManager.java</b><br/>━━━━━━━━━━━━━━━━━━━━━━<br/>Gestione singola wave<br/>Spawn timing<br/>Difficulty scaling<br/>Wave complete detection"]
        end

        subgraph END_WAVE["Wave System (~15 classi)"]
            direction TB

            WD["<b>WaveDirective</b><br/>Definizione wave<br/>Enemy types, count, timing"]

            BWS["<b>BossWaveSystem</b><br/>Wave boss speciali<br/>Meccaniche uniche"]

            WC["<b>WaveConfig</b><br/>Configurazione wave<br/>Scaling parameters"]

            WS["<b>WaveState</b><br/>Stato corrente wave<br/>Progress tracking"]
        end

        subgraph END_PERK["Perk System (~12 classi)"]
            direction TB

            PS["<b>PerkSystem</b><br/>Gestione perk<br/>Unlock, upgrade, reset"]

            PD["<b>PerkData</b><br/>Definizione perk<br/>Effects, costs, tiers"]

            PT["<b>PerkTree</b><br/>Albero perk<br/>Dependencies, paths"]

            PP["<b>PerkProgress</b><br/>Progresso giocatore<br/>Punti spesi, unlock"]
        end

        subgraph END_REWARD["Reward System (~10 classi)"]
            direction TB

            RS["<b>RewardSystem</b><br/>Distribuzione reward<br/>Loot tables, chances"]

            RD["<b>RewardData</b><br/>Definizione reward<br/>Items, XP, currency"]

            RC["<b>RewardCalculator</b><br/>Calcolo reward<br/>Scaling, bonus"]
        end

        subgraph END_KIT["Kit System (~8 classi)"]
            direction TB

            KM["<b>KitManager</b><br/>Gestione kit<br/>Loadout predefiniti"]

            KD["<b>KitData</b><br/>Definizione kit<br/>Equipment, consumables"]
        end

        subgraph END_SHOP["Shop System (~10 classi)"]
            direction TB

            SM["<b>ShopManager</b><br/>Gestione shop in-run<br/>Currency, inventory"]

            SI["<b>ShopItem</b><br/>Item acquistabili<br/>Prezzi, stock"]
        end

        subgraph END_UI["UI Endurance (~14 classi)"]
            direction TB

            EQS["<b>EnduranceQuestScreen</b><br/>Schermata principale<br/>Selezione kit, start"]

            EQH["<b>EnduranceHUD</b><br/>HUD in-game<br/>Wave progress, timer"]

            EQR["<b>EnduranceResults</b><br/>Schermata risultati<br/>Stats, reward recap"]
        end
    end

    END_CORE --> END_WAVE
    END_WAVE --> END_PERK
    END_PERK --> END_REWARD
    END_REWARD --> END_KIT
    END_KIT --> END_SHOP
    END_SHOP --> END_UI

    style ENDURANCE_SYSTEM fill:#1a0a00,stroke:#4d2600,color:#ffd9b3
```

### Flusso Quest Endurance

```mermaid
stateDiagram-v2
    [*] --> SETUP: Avvia Quest

    SETUP --> KIT_SELECT: Seleziona Kit
    KIT_SELECT --> PERK_SELECT: Scegli Perk Iniziali
    PERK_SELECT --> WAVE_START: Conferma

    WAVE_START --> IN_WAVE: Spawn Nemici

    IN_WAVE --> WAVE_COMPLETE: Tutti Eliminati
    IN_WAVE --> PLAYER_DEATH: HP = 0

    WAVE_COMPLETE --> SHOP_PHASE: Shop Disponibile
    SHOP_PHASE --> PERK_UPGRADE: Upgrade Perk
    PERK_UPGRADE --> WAVE_START: Prossima Wave

    WAVE_COMPLETE --> BOSS_WAVE: Ogni 5 Wave
    BOSS_WAVE --> WAVE_COMPLETE: Boss Sconfitto
    BOSS_WAVE --> PLAYER_DEATH: Sconfitto

    PLAYER_DEATH --> CONTINUE: Vita Extra?
    CONTINUE --> IN_WAVE: Sì
    CONTINUE --> FAILED: No

    WAVE_COMPLETE --> VICTORY: Wave Finale Completata

    FAILED --> RESULTS: Mostra Risultati
    VICTORY --> RESULTS: Mostra Risultati
    RESULTS --> [*]: Fine
```

---

## 9. Infrastruttura

### Servizi Trasversali

```mermaid
graph TB
    subgraph INFRA["Layer Infrastruttura"]
        direction TB

        subgraph NETWORK_DETAIL["network/ - Networking (31 classi)"]
            direction TB

            NH["<b>NetworkHandler.java</b><br/>━━━━━━━━━━━━━━━━━━━━<br/>Registrazione canali<br/>Gestione payload<br/>Routing messaggi"]

            PSS["<b>PacketSecurityService</b><br/>━━━━━━━━━━━━━━━━━━━━<br/>Validazione packet<br/>Rate limiting<br/>Anti-cheat base"]

            subgraph PAYLOADS["Payload Types"]
                PL1["EquipMobPayload"]
                PL2["ModifyItemPayload"]
                PL3["UpdateArmorPayload"]
                PL4["UpdateMobStatsPayload"]
                PL5["UpdateWeaponPayload"]
            end

            NET_OTHER["Serializers,<br/>Handlers,<br/>SyncManager"]
        end

        subgraph TELEMETRY_DETAIL["telemetry/ - Analytics (62 classi)"]
            direction TB

            TS["<b>TelemetryService.java</b><br/>━━━━━━━━━━━━━━━━━━━━<br/>Servizio principale<br/>Logging eventi<br/>Aggregazione dati"]

            TE["<b>TelemetryEvents.java</b><br/>━━━━━━━━━━━━━━━━━━━━<br/>Handler eventi<br/>Intercettazione azioni"]

            subgraph TEL_SUB["Subpackage"]
                TEL_SPATIAL["spatial/<br/>Heatmap, DesireLines"]
                TEL_DUNGEON["dungeon/<br/>DungeonRunService"]
                TEL_ROOM["room/<br/>RoomService, Counter"]
                TEL_PLAYER["player/<br/>PlayerAttributeTelemetry"]
                TEL_DASH["dashboard/<br/>DashboardServer"]
            end

            DUCK["<b>DuckDB Integration</b><br/>Database analytics<br/>Query performanti"]
        end

        subgraph INSTANCE_DETAIL["instance/ - Gestione Istanze (9 classi)"]
            direction TB

            IS["<b>InstanceState.java</b><br/>Enum stati istanza:<br/>INITIALIZING, ACTIVE,<br/>PAUSED, DESTROYED"]

            IM["<b>InstanceManager.java</b><br/>Gestione lifecycle<br/>Creazione, cleanup"]

            PIS["<b>PlayerInstanceState.java</b><br/>Stato giocatore<br/>in istanza"]

            ID["<b>InstanceData.java</b><br/>Dati persistenti<br/>istanza"]
        end

        subgraph MIXIN_DETAIL["mixin/ - Hook Vanilla (8 classi)"]
            direction TB

            MX_SRV["<b>Server Mixins</b><br/>MinecraftServerAccessor<br/>RecipeManagerMixin"]

            MX_CLT["<b>Client Mixins</b><br/>GameRendererMixin<br/>CameraShakeMixin<br/>ModelPartTransformMixin<br/>LivingEntityRendererMixin"]
        end
    end

    NETWORK_DETAIL --> TELEMETRY_DETAIL
    TELEMETRY_DETAIL --> INSTANCE_DETAIL
    INSTANCE_DETAIL --> MIXIN_DETAIL

    style INFRA fill:#0a0a1a,stroke:#333366,color:#ccccff
```

---

## 10. Flusso delle Dipendenze

### Grafo Dipendenze Completo

```mermaid
graph TD
    subgraph ENTRY["Layer 0: Entrypoint"]
        DM[DevMod]
        DMC[DevModClient]
        MC[ModConfig]
    end

    subgraph CORE["Layer 1: Core"]
        CFG[config/]
        STAT[stats/]
        COMP[components/]
        ATTR[attributes/]
        EVT[events/]
    end

    subgraph INFRA["Layer 2: Infrastructure"]
        NET[network/]
        TEL[telemetry/]
        INST[instance/]
        MIX[mixin/]
    end

    subgraph SYSTEMS["Layer 3: Systems"]
        CMB[combat/]
        COL[collision/]
        ABL[abilities/]
        EFF[effects/]
    end

    subgraph GAMEPLAY["Layer 4: Gameplay"]
        ARN[arena/]
        END[endurance/]
        PRT[party/]
        QST[quest/]
    end

    subgraph UI["Layer 5: Presentation"]
        UI_P[ui/]
        HUD_P[hud/]
        PNL[panels/]
        RND[rendering/]
    end

    %% Entry → Core
    DM --> CFG
    DM --> EVT
    DMC --> EVT
    MC --> CFG

    %% Core relationships
    CFG --> STAT
    CFG --> COMP
    STAT --> COMP
    EVT --> CFG
    EVT --> ATTR

    %% Core → Infrastructure
    EVT --> NET
    EVT --> TEL
    CFG --> TEL

    %% Infrastructure relationships
    NET --> TEL
    INST --> TEL
    MIX --> INST

    %% Infrastructure → Systems
    NET --> CMB
    TEL --> CMB
    CMB --> COL
    CMB --> EFF
    ABL --> EFF

    %% Systems → Gameplay
    CMB --> ARN
    CMB --> END
    INST --> ARN
    INST --> END
    ABL --> END

    %% Gameplay relationships
    ARN --> PRT
    END --> QST

    %% Gameplay → Presentation
    ARN --> UI_P
    END --> UI_P
    PRT --> UI_P
    CMB --> HUD_P
    ARN --> HUD_P
    END --> HUD_P

    %% Presentation relationships
    UI_P --> RND
    HUD_P --> RND
    PNL --> UI_P

    style ENTRY fill:#2c3e50,stroke:#34495e,color:#ecf0f1
    style CORE fill:#27ae60,stroke:#2ecc71,color:#fff
    style INFRA fill:#2980b9,stroke:#3498db,color:#fff
    style SYSTEMS fill:#8e44ad,stroke:#9b59b6,color:#fff
    style GAMEPLAY fill:#d35400,stroke:#e67e22,color:#fff
    style UI fill:#c0392b,stroke:#e74c3c,color:#fff
```

---

## 11. Riepilogo Quantitativo

### Distribuzione Classi per Package

| Package | Classi | % Totale | Layer | Descrizione |
|---------|--------|----------|-------|-------------|
| **ui/** | 227 | 26.3% | Presentation | Interfaccia utente completa |
| **arena/** | 188 | 21.8% | Gameplay | Sistema arene |
| **endurance/** | 69 | 8.0% | Gameplay | Quest wave-based |
| **telemetry/** | 62 | 7.2% | Infrastructure | Analytics e metriche |
| **network/** | 31 | 3.6% | Infrastructure | Comunicazione rete |
| **testing/** | 29 | 3.4% | Support | Utilità testing |
| **hud/** | 29 | 3.4% | Presentation | Overlay in-game |
| **rendering/** | 27 | 3.1% | Presentation | Rendering custom |
| **party/** | 20 | 2.3% | Gameplay | Sistema gruppi |
| **debug/** | 16 | 1.9% | Support | Strumenti debug |
| **actions/** | 16 | 1.9% | Support | Azioni radial menu |
| **recipe/** | 15 | 1.7% | Support | Editor ricette |
| **panels/** | 14 | 1.6% | Presentation | Pannelli speciali |
| **collision/** | 13 | 1.5% | Systems | Hit detection |
| **instance/** | 9 | 1.0% | Infrastructure | Gestione istanze |
| **events/** | 9 | 1.0% | Core | Handler eventi |
| **config/** | 9 | 1.0% | Core | Configurazioni |
| **combat/** | 9 | 1.0% | Systems | Combattimento |
| **mixin/** | 8 | 0.9% | Infrastructure | Hook Minecraft |
| **abilities/** | 7 | 0.8% | Systems | Abilità giocatore |
| **util/** | 6 | 0.7% | Support | Utilità generiche |
| **components/** | 6 | 0.7% | Core | Data components |
| **attributes/** | 6 | 0.7% | Core | Attributi custom |
| **stats/** | 5 | 0.6% | Core | Classi statistiche |
| **quest/** | 5 | 0.6% | Gameplay | Sistema quest |
| **integration/** | 5 | 0.6% | Support | Integrazioni esterne |
| **gametest/** | 5 | 0.6% | Support | Game test |
| **effects/** | 5 | 0.6% | Systems | Effetti status |
| **client/** | 4 | 0.5% | Support | Codice client-only |
| **damage/** | 2 | 0.2% | Systems | Tipi danno |
| **tags/** | 1 | 0.1% | Support | Sistema tag |
| **migration/** | 1 | 0.1% | Support | Helper migrazione |
| **bridge/** | 1 | 0.1% | Support | Bridge cross-mod |
| **ammo/** | 1 | 0.1% | Support | Sistema munizioni |
| **TOTALE** | **~862** | **100%** | | |

### Distribuzione per Layer

```mermaid
pie title Distribuzione Classi per Layer
    "Presentation (297)" : 297
    "Gameplay (282)" : 282
    "Infrastructure (110)" : 110
    "Systems (36)" : 36
    "Core (35)" : 35
    "Support (99)" : 99
    "Root (3)" : 3
```

---

## 12. Consolidamento UIConstants (Fase 7)

### Struttura Prima/Dopo

Il sistema UIConstants è stato unificato in un'unica classe canonica.

```mermaid
flowchart TB
    subgraph PRIMA["Prima del Consolidamento"]
        UI1["ui/UIConstants.java<br/>313 LOC<br/>━━━━━━━━━━━━━━━━━━<br/>Sound, BodyPart,<br/>Status, Toggle, Position"]
        UI2["ui/editor/core/UIConstants.java<br/>515 LOC<br/>━━━━━━━━━━━━━━━━━━<br/>Spacing, Size, Color,<br/>Animation, Font"]
    end

    subgraph DOPO["Dopo il Consolidamento"]
        UI_MERGED["ui/editor/core/UIConstants.java<br/>660 LOC (unificato)<br/>━━━━━━━━━━━━━━━━━━<br/>TUTTE le costanti UI<br/>in un'unica fonte"]
    end

    UI1 -->|"Merge"| UI_MERGED
    UI2 -->|"Base"| UI_MERGED

    style UI1 fill:#ff6b6b,color:#fff
    style UI2 fill:#4ecdc4,color:#fff
    style UI_MERGED fill:#45b7d1,color:#fff
```

### Classi Aggiunte

| Classe Interna | Descrizione | Esempio Uso |
|----------------|-------------|-------------|
| `Sound` | Feedback audio UI | `Sound.CLICK`, `Sound.SUCCESS` |
| `BodyPart` | Colori body part | `BodyPart.HEAD`, `BodyPart.LEGS` |
| `Status` | Colori stato | `Status.SUCCESS`, `Status.ERROR` |
| `Toggle` | Colori toggle | `Toggle.ON`, `Toggle.OFF_HOVER` |
| `Position` | Costanti posizione | `Position.TITLE_Y`, `Position.CONTENT_START_Y` |

### Costanti Aggiunte

| Categoria | Costanti |
|-----------|----------|
| **Spacing** | `PADDING_XS/SM/MD/LG/XL`, `GAP_SMALL/MEDIUM/LARGE`, `HEADER_HEIGHT` |
| **Size** | `SIDEBAR_WIDTH_*`, `DIALOG_WIDTH_*`, `BUTTON_HEIGHT_PROMINENT` |
| **Background** | `SCREEN()`, `TOOLTIP()`, `HUD_PANEL()`, `GLOW()` |
| **Border** | `LIGHT()`, `GLOW()` |
| **Text** | `WHITE()`, `ACCENT()` |
| **Accent** | `PURPLE()`, `YELLOW()`, `GOLD()` |

### File Aggiornati

50+ file hanno ricevuto aggiornamento import:

```mermaid
graph LR
    subgraph IMPORT_UPDATE["File con Import Aggiornato"]
        F1["hud/*.java"]
        F2["endurance/*.java"]
        F3["panels/*.java"]
        F4["party/*.java"]
        F5["ui/hub/*.java"]
        F6["ui/screens/*.java"]
        F7["ui/unified/*.java"]
        F8["testing/*.java"]
    end

    UC["ui/editor/core/UIConstants"] --> F1
    UC --> F2
    UC --> F3
    UC --> F4
    UC --> F5
    UC --> F6
    UC --> F7
    UC --> F8

    style UC fill:#45b7d1,color:#fff
```

---

## Note Finali

### Punti di Forza Architettura

1. **Separazione Netta**: Ogni layer ha responsabilità chiare
2. **Root Minimale**: Solo 3 file entrypoint
3. **Modularità**: Sistemi indipendenti e testabili
4. **Scalabilità**: Facile aggiungere nuovi moduli
5. **UIConstants Unificato**: Una sola fonte di verità per costanti UI

### Aree di Evoluzione Futura

1. **Phase 5 Deferred**: Rename `hud/` → `overlay/`, `network/` → `transport/`
2. **Step 3**: DamageHandler split con pipeline pattern
3. **Step 4**: Consolidamento duplicati UI (ConfirmDialog, HelpOverlay)

---

*Documento generato: 24 Dicembre 2024*
*Versione: 2.1 - Aggiunto consolidamento UIConstants*
*Autore: Claude Code*
