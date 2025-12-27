# Project Topology

> Last updated: 2025-12-26
> Status: CURRENT (verified against code)

---

## Overview

| Property | Value |
|----------|-------|
| Mod ID | `devmod` |
| Mod Version | `0.1.0` |
| Minecraft | `1.21.1` |
| NeoForge | `21.1.215` |
| Java | `21` |
| Group ID | `com.devmod` |
| Authors | `Frenk012, Vassago` |

Source: `gradle.properties`.

---

## Java Package Layout

All code lives under `src/main/java/com/devmod/`.

Root entrypoints:
- `DevMod.java` - Mod bootstrap
- `ModConfig.java` - Global config facade

### Core Systems
- `arena/` - Arena template system (policies, builders, validation)
- `abilities/` - Ability actions and payloads
- `ammo/` - Ranged/ammo rules
- `attributes/` - Attribute definitions and helpers
- `combat/`, `damage/`, `effects/` - Combat rules, damage model, status effects
- `endurance/` - Endurance quest system
- `mailbox/` - Mailbox/news/tasks system
- `party/` - Party system and quest sequencing
- `quest/` - Quest flows and state
- `telemetry/` - Telemetry capture and analytics pipeline

### Infrastructure and Glue
- `collision/` - Collision/transform utilities
- `config/` - Config and settings
- `debug/` - Debug overlays and tooling
- `events/` - Event wiring
- `migration/` - Migration helpers and data upgrades
- `mixin/` - Mixin hooks
- `network/` - Packet registration and handlers
- `runtime/` - Runtime services and lifecycle

### UX and Interaction
- `client/`, `bridge/` - Client side wiring and bridges
- `actions/` - Action registry
- `components/` - Shared components

### Content and Data
- `recipe/` - Recipe formats and editor support
- `tags/` - Tag helpers
- `stats/` - Stat tracking

### Testing and QA
- `testing/` - Test harness and QA helpers
- `gametest/` - GameTest suites

### Compatibility and Integrations
- `compat/` - Mod compatibility hooks
- `integration/` - Integration adapters

### Utilities
- `util/` - Shared utilities and helpers

For the full list, see `src/main/java/com/devmod/` directly.

---

## Resources Layout

```text
src/main/resources/
  assets/devmod/   # Client assets (textures, shaders, lang)
  data/devmod/     # Data packs and presets
  data/irons_spellbooks/  # Integration data
  schemas/         # JSON schemas
  db/              # DuckDB database artifacts
  dashboard/       # Telemetry dashboard assets
  build.properties
  log4j2-arena-audit.xml
  devmod.mixins.json
```

---

## Configuration

Runtime config is written to `config/devmod/` (in dev runs this resolves under `run/config/`).

---

## Related Docs
- [[README]] - Documentation home
- [[ARCHITECTURE]] - High level architecture
- [[ENTRYPOINTS]] - Entrypoints and triggers
