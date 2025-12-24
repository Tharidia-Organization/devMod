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
- `com.frenkvs.devmod`

---

### System: Combat

**Package**: `com.frenkvs.devmod.combat`
**Description**: Handles damage calculation, body part detection, and weapon mechanics.

**Key Classes**:

- `DamageHandler`
- `HitHelper`
- `WeaponConfigManager`
- `MobConfigManager`

---

### System: Endurance Quest

**Package**: `com.frenkvs.devmod.endurance`
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

**Package**: `com.frenkvs.devmod.party`
**Description**: Multiplayer coordination for synchronized quest starts.

**Key Components**:

- `PartyData` (State)
- `PartyActionPayload` (Network)
- `PartyScreen` (UI)

---

### System: Instance

**Package**: `com.frenkvs.devmod.instance`
**Description**: Dynamic dimension management for isolated quest instances.

**Key Components**:

- `InstanceArenaManager`
- `DynamicDimensionManager`
- `RecoverySystem`

---

### System: Telemetry

**Package**: `com.frenkvs.devmod.telemetry`
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

- **HUD Overlays**: `com.frenkvs.devmod.hud`
  - `ImpactHudOverlay`
  - `ComboDecayOverlay`
  - `EnduranceQuestOverlay`
- **Screens (UI)**: `com.frenkvs.devmod.ui`
  - `UnifiedSettingsScreen`
- **Debug Rendering**: `com.frenkvs.devmod.rendering`
  - `BodyPartRenderer`
  - `HeatmapVisualizer`

---

## Rendering & Shaders

### Custom Shader System

**Packages**:

- `com.frenkvs.devmod.rendering.shader`
- `com.frenkvs.devmod.rendering.shield`

**Asset Path**: `assets/devmod/shaders/core/`

**Key Components**:

- `ShieldShaderRegistry`
- `EnergyShieldRenderer`
- `ShaderPipeline`
- `energy_shield.fsh` (Fragment Shader)

---

## Network Layer

### Payloads

**Package**: `com.frenkvs.devmod.network`
**Description**: NeoForge's payload system for client-server communication.

**Key Payloads**:

- `PartyActionPayload` (C2S)
- `PartySyncPayload` (S2C)
- `QuestSyncPayload` (S2C)
- `PerkChoicesPayload` (S2C)
- `TokenGainPayload` (S2C)

---

## Other Key Packages

- `com.frenkvs.devmod.abilities`
- `com.frenkvs.devmod.attributes`
- `com.frenkvs.devmod.damage`
- `com.frenkvs.devmod.debug`
- `com.frenkvs.devmod.effects`
- `com.frenkvs.devmod.quest`
- `com.frenkvs.devmod.mixin`
