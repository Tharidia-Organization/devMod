# DevMod Architecture

> **Ultimo Aggiornamento**: 24 Dicembre 2024
> **Namespace**: `com.devmod.*` (unificato da `com.frenkvs.devmod`)
> **Build Status**: PASS - 2740 test superati

Questo documento fornisce una panoramica dell'architettura DevMod, descrivendo i sistemi principali e le loro interazioni.

## Panoramica Sistema

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              DevMod                                      │
│                    Namespace: com.devmod.*                               │
├─────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │
│  │   Combat    │  │  Endurance  │  │    Party    │  │  Telemetry  │    │
│  │   System    │  │   Quest     │  │   System    │  │   System    │    │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘    │
│         │                │                │                │            │
│         └────────────────┼────────────────┼────────────────┘            │
│                          │                │                             │
│                    ┌─────┴─────┐    ┌─────┴─────┐                       │
│                    │  Instance │    │  Network  │                       │
│                    │   System  │    │  Handler  │                       │
│                    └───────────┘    └───────────┘                       │
├─────────────────────────────────────────────────────────────────────────┤
│                         Client Layer                                     │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │
│  │     HUD     │  │   Screens   │  │  Rendering  │  │   Panels    │    │
│  │  Overlays   │  │     (UI)    │  │   (Debug)   │  │    (3D)     │    │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘    │
└─────────────────────────────────────────────────────────────────────────┘
```

## Sistemi Core

### 1. Sistema Combat
**Package:** `com.devmod.combat`

Il sistema combat gestisce calcolo danno, rilevamento body part, e meccaniche armi.

```
events/CombatEvents.java ─────► combat/DamageHandler.java ─────► combat/HitHelper.java
                                       │                              │
                                       ▼                              ▼
                                 stats/WeaponStats.java        Body Part Detection
                                       │
                                       ▼
                                config/MobConfigManager.java
```

**Componenti Chiave:**
- `HitHelper`: Rilevamento body part via raycast su entity bounding boxes
- `DamageHandler`: Applica moltiplicatori per body part, arma, e configurazione mob
- `WeaponConfigManager`: Override statistiche per arma
- `MobConfigManager`: Override statistiche per mob

### 2. Sistema Quest Endurance
**Package:** `com.devmod.endurance`

Sistema combattimento wave-based ispirato ai roguelike con sottosistemi multipli.

```
EnduranceQuestManager ◄──────────────────────────────────────────┐
        │                                                         │
        ├── WaveManager (spawn mob, progressione wave)            │
        ├── PerkSystem (selezione perk roguelike)                 │
        ├── ComboSystem (scoring stile DMC: D→SSS)                │
        ├── RewardSystem (valuta, loot, achievement)              │
        ├── MutatorSystem (modificatori gameplay)                 │
        ├── BossWaveSystem (encounter boss)                       │
        └── GamificationManager (progresso, sblocchi) ────────────┘
```

**Dettagli Sottosistemi:**

| Sottosistema | Scopo | Classi Chiave |
|--------------|-------|---------------|
| WaveManager | Spawn mob, stato wave | `WaveManager`, `ArenaHandle` |
| PerkSystem | Gestione pool perk, selezione, stacking | `PerkSystem`, `PerkSession` |
| ComboSystem | Scoring stile, tracking combo | `ComboSystem`, `ComboSession` |
| RewardSystem | Valuta, shop, achievement | `RewardSystem`, `PlayerWallet` |
| MutatorSystem | Modificatori difficoltà | `MutatorSystem`, `MutatorSession` |

### 3. Sistema Template Arena

**Package:** `com.devmod.arena`

Sistema Arena Template (L1) + Policy (L2) per build arene deterministiche.

```
ArenaTemplateRegistry ──► TemplateResolver ──► TemplateArenaBuilder
        │                        │                      │
        ▼                        ▼                      ▼
   TemplateLoader          PolicyResolver           ArenaHandle
```

**Componenti Chiave:**

- `ArenaTemplateRegistry`: carica/valida template, ereditarietà, fallback
- `PolicyResolver`: routing/scoring per selezione template
- `TemplateArenaBuilder`: build transazionale con rollback
- `ArenaHandle`: contratto runtime per spawn/bounds/metadata

### 4. Sistema Party

**Package:** `com.devmod.party`

Coordinazione multiplayer per avvio sincronizzato quest.

```
PartyScreen (UI) ◄────► PartyActionPayload (Network)
                              │
                              ▼
                        PartyData (State)
                              │
                        ┌─────┴─────┐
                        │           │
                   PartyMember   QuestType
```

**Macchina a Stati:**

```
FORMING ──► READY ──► IN_QUEST ──► FORMING
    │                     │
    └─────────────────────┘ (su failure/complete)
```

### 5. Sistema Instance

**Package:** `com.devmod.instance`

Gestione dinamica dimensioni per istanze quest isolate.

```
InstanceArenaManager ──► DynamicDimensionManager
        │                        │
        ▼                        ▼
  Instance Lifecycle      Creazione/Cleanup Dimensione
        │
        ▼
  RecoverySystem (recupero crash)
```

### 6. Sistema Telemetry

**Package:** `com.devmod.telemetry`

Raccolta dati per analisi level design.

```
TelemetryService (Orchestratore)
        │
        ├── DamageTrackingService (dati combat)
        ├── FightSessionService (TTK, tempi kill)
        ├── SpatialMetricsService (heatmap)
        ├── EconomyMetricsService (flusso valuta)
        └── DungeonSessionService (run dungeon)
                │
                ▼
        Export NDJSON (run/telemetry/*.ndjson)
```

## Layer Client

### Overlay HUD

**Package:** `com.devmod.hud`

| Overlay | Scopo |
|---------|-------|
| `ImpactHudOverlay` | Numeri danno, body part colpita |
| `ComboDecayOverlay` | Rank stile, contatore combo |
| `EnduranceQuestOverlay` | Info wave, stato quest |
| `RecordBannerOverlay` | Achievement record personali |
| `PartyHudOverlay` | Stato membri party |

### Schermate (UI)

**Package:** `com.devmod.ui`

```
UnifiedSettingsScreen ──► SettingsPage (interface)
                               │
        ┌──────────────────────┼──────────────────────┐
        │                      │                      │
  GeneralSettingsPage    CombatSettingsPage    TelemetryPage
```

### Rendering Debug

**Package:** `com.devmod.rendering`

| Renderer | Visualizzazione |
|----------|-----------------|
| `BodyPartRenderer` | Wireframe hitbox |
| `HeatmapVisualizer` | Heatmap morte/movimento |
| `LightLevelOverlay` | Livelli luce spawn |
| `RoomBoundsVisualizer` | Confini stanza |
| `SafeSpotVisualizer` | Posizioni exploit |

### Sistema Shader Custom

**Package:** `com.devmod.rendering.shield`, `com.devmod.rendering.shader`

Effetti visuali GPU-accelerati usando il sistema registrazione shader di NeoForge.

```
RegisterShadersEvent ──► ShieldShaderRegistry
                              │
                              ▼
                       ShaderInstance
                              │
                              ▼
                    Custom RenderType
                   (ShaderStateShard)
                              │
                              ▼
                   EnergyShieldRenderer
                   (Uniforms: color, impact, time)
```

**Componenti Chiave:**

| Componente | Scopo |
|------------|-------|
| `ShieldShaderRegistry` | Registra shader via `RegisterShadersEvent`, crea `RenderType` custom |
| `EnergyShieldRenderer` | Renderizza sfera scudo, imposta shader uniforms |
| `ShaderPipeline` | Registrazione shader + creazione RenderType con fallback |
| `energy_shield.fsh` | Fragment shader con noise, fresnel, effetti onda impatto |

**Posizione File Shader:** `assets/devmod/shaders/core/`

Vedi [SHADER_SYSTEM.md](SHADER_SYSTEM.md) per guida implementazione dettagliata.

## Layer Network

**Package:** `com.devmod.network`

Tutta la comunicazione client-server usa il sistema payload di NeoForge:

```
Client ──► [Payload] ──► Server
       ◄── [Payload] ◄──
```

**Payload Principali:**

| Payload | Direzione | Scopo |
|---------|-----------|-------|
| `PartyActionPayload` | C2S | Comandi party |
| `PartySyncPayload` | S2C | Sync stato party |
| `QuestSyncPayload` | S2C | Sync stato quest |
| `PerkChoicesPayload` | S2C | Opzioni selezione perk |
| `TokenGainPayload` | S2C | Notifiche valuta |

## Esempi Flusso Dati

### Flusso Avvio Quest

```
1. Giocatore clicca "Avvia Quest" in PartyScreen
2. PartyActionPayload(START_QUEST) inviato al server
3. Server valida stato party (tutti pronti)
4. InstanceArenaManager crea istanza
5. TemplateArenaBuilder costruisce arena da template
6. Giocatori teletrasportati in arena
7. WaveManager avvia prima wave
8. QuestSyncPayload inviato a tutti i membri party
```

### Flusso Hit Combat

```
1. Giocatore attacca mob
2. LivingAttackEvent fired
3. HitHelper.calculateBodyPart() determina location hit
4. DamageHandler applica moltiplicatori
5. ComboSystem.registerAction() aggiorna score stile
6. ImpactHudOverlay mostra danno
7. TelemetryService logga dati hit
```

### Flusso Selezione Perk

```
1. Wave completata
2. PerkSystem genera 3 scelte perk
3. PerkChoicesPayload inviato al client
4. PerkSelectionScreen mostra opzioni
5. Giocatore seleziona perk
6. PerkSelectionPayload inviato al server
7. PerkSession applica effetti perk
```

## Thread Safety

Sistemi critici usano strutture dati concorrenti:

| Sistema | Sincronizzazione |
|---------|------------------|
| RewardSystem | Lock per-player per acquisti |
| TelemetryService | ConcurrentHashMap per sessioni |
| PartyData | Metodi sincronizzati |
| ComboSystem | ConcurrentHashMap per sessioni |

## Configurazione

```
run/config/devmod/
├── devmod-common.toml      # Config principale (NeoForge config)
├── mob_configs.json        # Override per-mob
├── weapon_configs.json     # Override per-arma
└── rewards/
    └── wallets.json        # Dati valuta giocatore
```

## Punti di Estensione

### Aggiungere un Nuovo Perk

1. Aggiungere entry a `PerkSystem.initializePerks()`
2. Implementare effetto in `PerkSession.applyPerk()`
3. Aggiungere chiave localizzazione a `en_us.json`

### Aggiungere un Nuovo Mutator

1. Aggiungere entry a `MutatorSystem.initializeMutators()`
2. Implementare effetto in `MutatorSession.applyMutator()`
3. Aggiornare calcolo moltiplicatore reward

### Aggiungere un Nuovo Debug Overlay

1. Creare classe renderer in `rendering/`
2. Registrare keybind in `KeyInputHandler`
3. Aggiungere toggle in `DebugOverlaysPage`

## Dipendenze

- **NeoForge 21.1.x**: Mod loader, eventi, networking
- **Minecraft 1.21.1**: API base game
- **Opzionale: Pehkui**: Supporto scaling entità
- **Opzionale: Better Combat**: Rilevamento combat avanzato

---

## Riorganizzazione Dicembre 2024

### Cambiamenti Namespace

| Vecchio | Nuovo |
|---------|-------|
| `com.frenkvs.devmod.*` | `com.devmod.*` |
| `com.devmod.ui.UIConstants` | `com.devmod.ui.editor.core.UIConstants` |
| `com.devmod.combat.ArrowEvents` | `com.devmod.events.ArrowEvents` |
| `com.devmod.combat.CombatEvents` | `com.devmod.events.CombatEvents` |

### File Root Package

Solo 3 file nel root `com.devmod/`:

- `DevMod.java` - Entrypoint principale
- `DevModClient.java` - Entrypoint client
- `ModConfig.java` - Configurazione root

### Documentazione Dettagliata

Vedi [docs/reorg/REORG_COMPLETE.md](reorg/REORG_COMPLETE.md) per dettagli completi.

---

*Ultimo aggiornamento: 24 Dicembre 2024*
