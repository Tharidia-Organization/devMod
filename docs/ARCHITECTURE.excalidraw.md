---

excalidraw-plugin: parsed
tags: [excalidraw]

---

%%

# Excalidraw Data

```json
{
  "type": "excalidraw",
  "version": 2,
  "source": "https://github.com/zsviczian/obsidian-excalidraw-plugin/releases/tag/1.9.19",
  "elements": [],
  "appState": {
    "gridSize": null,
    "viewBackgroundColor": "#ffffff"
  }
}
```

%%

# DevMod Architecture

This document provides a high-level overview of the DevMod architecture. You can copy the markdown elements into the Excalidraw canvas to visualize the structure.

---

## Core Systems

### Primary Packages

- `com.devmod.arena`
- `com.devmod`

---

### System: Combat

**Package**: `com.devmod.combat`
**Description**: Handles damage calculation, body part detection, and weapon mechanics.

**Key Classes**:

- `DamageHandler`
- `HitHelper`
- `WeaponConfigManager`
- `MobConfigManager`

---

### System: Endurance Quest

**Package**: `com.devmod.endurance`
**Description**: A roguelike-inspired wave combat system.

**Sub-systems**:

- `WaveManager`
- `PerkSystem`
- `ComboSystem`
- `RewardSystem`
- `MutatorSystem`
- `BossWaveSystem`
- `GamificationManager`

---

### System: Arena Template

**Package**: `com.devmod.arena`
**Description**: System for deterministic arena builds using templates and policies.

**Key Components**:

- `ArenaTemplateRegistry`
- `PolicyResolver`
- `TemplateArenaBuilder`
- `ArenaHandle`

---

### System: Party

**Package**: `com.devmod.party`
**Description**: Multiplayer coordination for synchronized quest starts.

**Key Components**:

- `PartyData` (State)
- `PartyActionPayload` (Network)
- `PartyScreen` (UI)

---

### System: Instance

**Package**: `com.devmod.instance`
**Description**: Dynamic dimension management for isolated quest instances.

**Key Components**:

- `InstanceArenaManager`
- `DynamicDimensionManager`
- `RecoverySystem`

---

### System: Telemetry

**Package**: `com.devmod.telemetry`
**Description**: Data collection for level design analysis. Exports to NDJSON.

**Services**:

- `DamageTrackingService`
- `FightSessionService`
- `SpatialMetricsService`
- `EconomyMetricsService`
- `DungeonSessionService`

---

## Client Layer

### UI & HUD

- **HUD Overlays**: `com.devmod.hud`
  - `ImpactHudOverlay`
  - `ComboDecayOverlay`
  - `EnduranceQuestOverlay`
- **Screens (UI)**: `com.devmod.ui`
  - `UnifiedSettingsScreen`
- **Debug Rendering**: `com.devmod.rendering`
  - `BodyPartRenderer`
  - `HeatmapVisualizer`

---

## Rendering & Shaders

### Custom Shader System

**Packages**:

- `com.devmod.rendering.shader`
- `com.devmod.rendering.shield`

**Asset Path**: `assets/devmod/shaders/core/`

**Key Components**:

- `ShieldShaderRegistry`
- `EnergyShieldRenderer`
- `ShaderPipeline`
- `energy_shield.fsh` (Fragment Shader)

---

## Network Layer

### Payloads

**Package**: `com.devmod.network`
**Description**: NeoForge's payload system for client-server communication.

**Key Payloads**:

- `PartyActionPayload` (C2S)
- `PartySyncPayload` (S2C)
- `QuestSyncPayload` (S2C)
- `PerkChoicesPayload` (S2C)
- `TokenGainPayload` (S2C)

---

## Other Key Packages

- `com.devmod.abilities`
- `com.devmod.attributes`
- `com.devmod.damage`
- `com.devmod.debug`
- `com.devmod.effects`
- `com.devmod.quest`
- `com.devmod.mixin`
