# Config System

> Last updated: 2025-12-26
> Status: CURRENT (verified against code)

This doc covers the active configuration surfaces and their concrete storage locations.

## Scope

- ModConfigSpec configs registered in `DevMod` (`Config`, `GameMechanicsConfig`, `EditorClientConfig`).
- JSON design configs managed by `GameDesignConfigManager` (global + per-instance overrides).
- Gameplay override profiles in `GameplayOverridesManager`.
- Per-system config managers for items, mobs, and consumables.
- Shared config path helpers in `ConfigPaths`.

## Key Components

- `com.devmod.config.Config`
- `com.devmod.config.GameMechanicsConfig`
- `com.devmod.config.EditorClientConfig`
- `com.devmod.config.gamedesign.GameDesignConfig`
- `com.devmod.config.gamedesign.GameDesignConfigManager`
- `com.devmod.config.gamedesign.InstanceOverride`
- `com.devmod.config.GameplayOverridesManager`
- `com.devmod.config.ArmorConfigManager`
- `com.devmod.config.WeaponConfigManager`
- `com.devmod.config.UsableConfigManager`
- `com.devmod.config.FoodConfigManager`
- `com.devmod.config.FuelConfigManager`
- `com.devmod.config.MobConfigManager`

## Config Paths (Verified)

- Root: `config/devmod/` (`ConfigPaths.getConfigDir()`).
- Game design: `config/devmod/game_design.json`.
- Gameplay overrides: `config/devmod/overrides/*.json`.
- Item configs: `config/devmod/armor_configs.json`, `config/devmod/weapon_configs.json`, `config/devmod/usable_configs.json`.
- Item stats TOML: `config/devmod/devmod-items.toml` (Armor/Weapon managers).
- Consumables: `config/devmod/food_stats.json`, `config/devmod/fuel_stats.json`.
- Mob configs: `config/devmod/mob_configs.json` (with backups under `config/devmod/mob_configs/`).
- UI settings: `config/devmod/settings.json`.
- Telemetry settings: `config/devmod/telemetry_settings.json`, `config/devmod/telemetry_rooms.json`.

## Automated Validation

- `GameDesignConfigDirectTest`
- `InstanceOverrideDirectTest`
- `GameDesignConfigManagerDirectTest`
- `ModConfigDirectTest`
- `WeaponConfigTest`
- `MobConfigTest`
- `SettingsPersistenceValidationTest`

## Cross-References

- `docs/areas/arena/README.md`
- `docs/areas/endurance/README.md`
