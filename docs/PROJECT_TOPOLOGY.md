# Project Topology

> Last updated: 2025-12-26
> Status: NEEDS_VERIFICATION

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

### Core Systems
- `arena/` - Arena template system (policies, builders, validation)
- `endurance/` - Endurance quest system
- `telemetry/` - Telemetry capture and analytics pipeline
- `combat/`, `damage/`, `attributes/` - Combat rules and stats
- `debug/` - Debug overlays and tooling

### Infrastructure and Glue
- `config/` - Config and settings
- `network/` - Packet registration and handlers
- `events/` - Event wiring
- `mixin/` - Mixin hooks
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
  devmod.mixins.json
```

---

## Configuration

Runtime config is written to `run/config/devmod/`.

---

## Related Docs
- [[README]] - Documentation home
- [[ARCHITECTURE]] - High level architecture
- [[ENTRYPOINTS]] - Entrypoints and triggers
