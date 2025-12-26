# Config System

> Last updated: 2025-12-26
> Status: NEEDS_VERIFICATION
> Risk Level: MEDIUM (multiple config sources + file IO)

---

## 1. Scope

This doc covers the config surfaces that are validated by automated tests:
- NeoForge ModConfigSpec TOML files (common/client).
- Game design JSON config (global + per-instance overrides).
- Runtime overlay/debug toggles (non-persisted).

Out of scope (tracked elsewhere):
- Item/mob/armor config managers (MC-dependent; validated via GameTests).
- Arena templates/policies (see `docs/areas/arena/README.md`).

---

## 2. Config Sources

### TOML (ModConfigSpec)
- `com.devmod.config.Config` -> `config/devmod-common.toml`
- `com.devmod.config.GameMechanicsConfig` -> `config/devmod-mechanics.toml`
- `com.devmod.config.EditorClientConfig` -> `config/devmod-client.toml`

### JSON (Game Design)
- `com.devmod.config.gamedesign.GameDesignConfigManager`
  - file: `config/devmod/game_design.json`
  - overrides: `com.devmod.config.gamedesign.InstanceOverride`

### Runtime (non-persisted)
- `com.devmod.ModConfig` (overlay/debug toggles + color cycling)

---

## 3. Key Components

- `com.devmod.config.Config` (telemetry/combat/overlay/perf/FX toggles)
- `com.devmod.config.GameMechanicsConfig` (gameplay tuning knobs)
- `com.devmod.config.EditorClientConfig` (editor UX defaults)
- `com.devmod.config.gamedesign.GameDesignConfig`
- `com.devmod.config.gamedesign.GameDesignConfigManager`
- `com.devmod.config.gamedesign.InstanceOverride`
- `com.devmod.ModConfig`

---

## 4. Automated Validation

| Behavior | Test |
|---|---|
| GameDesignConfig copy returns an independent config with the same primitive values | `GameDesignConfigDirectTest` |
| Instance overrides (including presets) apply expected values | `InstanceOverrideDirectTest` |
| Effective config uses global defaults or applies overrides without mutating global | `GameDesignConfigManagerDirectTest` |
| ModConfig color palette cycles deterministically | `ModConfigDirectTest` |

---

## Cross-References

- `docs/areas/arena/README.md`
- `docs/areas/endurance/README.md`
