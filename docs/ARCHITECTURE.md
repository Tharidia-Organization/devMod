# DevMod Architecture

This document provides a high-level overview of the DevMod architecture, describing the major systems and their interactions.

## System Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              DevMod                                      │
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

## Core Systems

### 1. Combat System
**Package:** `com.frenkvs.devmod`

The combat system handles damage calculation, body part detection, and weapon mechanics.

```
CombatEvents.java ─────► DamageHandler.java ─────► HitHelper.java
                              │                         │
                              ▼                         ▼
                        WeaponStats.java         Body Part Detection
                              │
                              ▼
                        MobConfigManager.java
```

**Key Components:**
- `HitHelper`: Raycast-based body part detection using entity bounding boxes
- `DamageHandler`: Applies multipliers based on body part, weapon, and mob config
- `WeaponConfigManager`: Per-weapon stat overrides
- `MobConfigManager`: Per-mob stat overrides

### 2. Endurance Quest System
**Package:** `com.frenkvs.devmod.endurance`

A roguelike-inspired wave combat system with multiple subsystems.

```
EnduranceQuestManager ◄──────────────────────────────────────────┐
        │                                                         │
        ├── WaveManager (mob spawning, wave progression)          │
        ├── PerkSystem (roguelike perk selection)                 │
        ├── ComboSystem (DMC-style scoring D→SSS)                 │
        ├── RewardSystem (currency, loot, achievements)           │
        ├── MutatorSystem (gameplay modifiers)                    │
        ├── BossWaveSystem (boss encounters)                      │
        └── GamificationManager (progress, unlocks) ──────────────┘
```

**Subsystem Details:**

| Subsystem | Purpose | Key Classes |
|-----------|---------|-------------|
| WaveManager | Spawns mobs, tracks wave state | `WaveManager`, `ArenaHandle` |
| PerkSystem | Manages perk pool, selection, stacking | `PerkSystem`, `PerkSession` |
| ComboSystem | Style scoring, combo tracking | `ComboSystem`, `ComboSession` |
| RewardSystem | Currency, shop, achievements | `RewardSystem`, `PlayerWallet` |
| MutatorSystem | Difficulty modifiers | `MutatorSystem`, `MutatorSession` |

### 3. Arena Template System
**Package:** `com.devmod.arena`

Arena Template (L1) + Policy (L2) system for deterministic arena builds.

```
ArenaTemplateRegistry ──► TemplateResolver ──► TemplateArenaBuilder
        │                        │                      │
        ▼                        ▼                      ▼
   TemplateLoader          PolicyResolver           ArenaHandle
```

**Key Components:**
- `ArenaTemplateRegistry`: load/validate templates, inheritance, fallback
- `PolicyResolver`: routing/scoring for template selection
- `TemplateArenaBuilder`: transactional build with rollback
- `ArenaHandle`: runtime contract for spawn/bounds/metadata

### 4. Party System
**Package:** `com.frenkvs.devmod.party`

Multiplayer coordination for synchronized quest starts.

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

**State Machine:**
```
FORMING ──► READY ──► IN_QUEST ──► FORMING
    │                     │
    └─────────────────────┘ (on failure/complete)
```

### 5. Instance System
**Package:** `com.frenkvs.devmod.instance`

Dynamic dimension management for isolated quest instances.

```
InstanceArenaManager ──► DynamicDimensionManager
        │                        │
        ▼                        ▼
  Instance Lifecycle      Dimension Creation/Cleanup
        │
        ▼
  RecoverySystem (crash recovery)
```

### 6. Telemetry System
**Package:** `com.frenkvs.devmod.telemetry`

Data collection for level design analysis.

```
TelemetryService (Orchestrator)
        │
        ├── DamageTrackingService (combat data)
        ├── FightSessionService (TTK, kill times)
        ├── SpatialMetricsService (heatmaps)
        ├── EconomyMetricsService (currency flow)
        └── DungeonSessionService (dungeon runs)
                │
                ▼
        NDJSON Export (run/telemetry/*.ndjson)
```

## Client Layer

### HUD Overlays
**Package:** `com.frenkvs.devmod.hud`

| Overlay | Purpose |
|---------|---------|
| `ImpactHudOverlay` | Damage numbers, body part hit |
| `ComboDecayOverlay` | Style rank, combo counter |
| `EnduranceQuestOverlay` | Wave info, quest status |
| `RecordBannerOverlay` | Personal record achievements |
| `PartyHudOverlay` | Party member status |

### Screens (UI)
**Package:** `com.frenkvs.devmod.ui`

```
UnifiedSettingsScreen ──► SettingsPage (interface)
                               │
        ┌──────────────────────┼──────────────────────┐
        │                      │                      │
  GeneralSettingsPage    CombatSettingsPage    TelemetryPage
```

### Debug Rendering
**Package:** `com.frenkvs.devmod.rendering`

| Renderer | Visualization |
|----------|---------------|
| `BodyPartRenderer` | Hitbox wireframes |
| `HeatmapVisualizer` | Death/movement heatmaps |
| `LightLevelOverlay` | Spawn light levels |
| `RoomBoundsVisualizer` | Room boundaries |
| `SafeSpotVisualizer` | Exploit locations |

### Custom Shader System
**Package:** `com.frenkvs.devmod.rendering.shield`, `com.frenkvs.devmod.rendering.shader`

GPU-accelerated visual effects using NeoForge's shader registration system.

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

**Key Components:**
| Component | Purpose |
|-----------|---------|
| `ShieldShaderRegistry` | Registers shader via `RegisterShadersEvent`, creates custom `RenderType` |
| `EnergyShieldRenderer` | Renders shield sphere, sets shader uniforms |
| `ShaderPipeline` | Shader registration + RenderType creation with fallback |
| `energy_shield.fsh` | Fragment shader with noise, fresnel, impact wave effects |

**Shader Files Location:** `assets/devmod/shaders/core/`

See [SHADER_SYSTEM.md](SHADER_SYSTEM.md) for detailed implementation guide.

## Network Layer
**Package:** `com.frenkvs.devmod` (payloads)

All client-server communication uses NeoForge's payload system:

```
Client ──► [Payload] ──► Server
       ◄── [Payload] ◄──
```

**Key Payloads:**
| Payload | Direction | Purpose |
|---------|-----------|---------|
| `PartyActionPayload` | C2S | Party commands |
| `PartySyncPayload` | S2C | Party state sync |
| `QuestSyncPayload` | S2C | Quest state sync |
| `PerkChoicesPayload` | S2C | Perk selection options |
| `TokenGainPayload` | S2C | Currency notifications |

## Data Flow Examples

### Quest Start Flow
```
1. Player clicks "Start Quest" in PartyScreen
2. PartyActionPayload(START_QUEST) sent to server
3. Server validates party state (all ready)
4. InstanceArenaManager creates instance
5. TemplateArenaBuilder builds arena from template
6. Players teleported to arena
7. WaveManager starts first wave
8. QuestSyncPayload sent to all party members
```

### Combat Hit Flow
```
1. Player attacks mob
2. LivingAttackEvent fired
3. HitHelper.calculateBodyPart() determines hit location
4. DamageHandler applies multipliers
5. ComboSystem.registerAction() updates style score
6. ImpactHudOverlay displays damage
7. TelemetryService logs hit data
```

### Perk Selection Flow
```
1. Wave completed
2. PerkSystem generates 3 perk choices
3. PerkChoicesPayload sent to client
4. PerkSelectionScreen displays options
5. Player selects perk
6. PerkSelectionPayload sent to server
7. PerkSession applies perk effects
```

## Thread Safety

Critical systems use concurrent data structures:

| System | Synchronization |
|--------|-----------------|
| RewardSystem | Per-player locks for purchases |
| TelemetryService | ConcurrentHashMap for sessions |
| PartyData | Synchronized methods |
| ComboSystem | ConcurrentHashMap for sessions |

## Configuration

```
run/config/devmod/
├── devmod-common.toml      # Main config (NeoForge config)
├── mob_configs.json        # Per-mob overrides
├── weapon_configs.json     # Per-weapon overrides
└── rewards/
    └── wallets.json        # Player currency data
```

## Extension Points

### Adding a New Perk
1. Add entry to `PerkSystem.initializePerks()`
2. Implement effect in `PerkSession.applyPerk()`
3. Add localization key to `en_us.json`

### Adding a New Mutator
1. Add entry to `MutatorSystem.initializeMutators()`
2. Implement effect in `MutatorSession.applyMutator()`
3. Update reward multiplier calculation

### Adding a New Debug Overlay
1. Create renderer class in `rendering/`
2. Register keybind in `KeyInputHandler`
3. Add toggle in `DebugOverlaysPage`

## Dependencies

- **NeoForge 21.1.x**: Mod loader, events, networking
- **Minecraft 1.21.1**: Base game APIs
- **Optional: Pehkui**: Entity scaling support
- **Optional: Better Combat**: Enhanced combat detection
