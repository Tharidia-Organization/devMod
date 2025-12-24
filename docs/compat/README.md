# DevMod Compatibility Layer

This directory contains documentation for all mod compatibility integrations in DevMod.

## Overview

DevMod provides seamless integration with 200+ mods through a standardized compatibility layer. All integrations are:

- **Gated**: Features activate only when the corresponding mod is present
- **Safe**: No classloading crashes if mods are absent
- **Testable**: Each integration includes verification steps

## Architecture

```
com.devmod.compat/
├── Compat.java              # Core detection utilities
├── CompatModule.java        # Interface for mod integrations
├── CompatRegistry.java      # Module registration & lifecycle
└── mods/
    ├── accessories/         # Accessories API (modern Curios)
    ├── apothicattributes/   # Apothic Attributes (crit, lifesteal)
    ├── clothconfig/         # Cloth Config screen API
    ├── controlling/         # Keybind conflict detection
    ├── curios/              # Curios equipment slots
    ├── easynpc/             # Easy NPC for Arena
    ├── ironsspellbooks/     # Iron's Spellbooks magic system
    ├── journeymap/          # JourneyMap waypoints for Arena
    ├── playeranimator/      # Player Animation Library
    ├── rangedweaponapi/     # Ranged Weapon API
    ├── spark/               # Spark profiler TPS/MSPT
    ├── spellengine/         # Spell Engine framework
    └── spellpower/          # Spell Power attributes
```

## Quick Reference

| Document | Description |
|----------|-------------|
| [MOD_INVENTORY.md](MOD_INVENTORY.md) | Complete list of detected mods with status |
| [clothconfig.md](clothconfig.md) | Cloth Config API integration |
| [curios.md](curios.md) | Curios equipment slots |
| [irons_spellbooks.md](irons_spellbooks.md) | Iron's Spellbooks magic system |
| [spell_engine.md](spell_engine.md) | Spell Engine framework |
| [spark.md](spark.md) | Spark profiler hooks |

## Priority Categories

### P1 - UI/Input/HUD (Critical)
Mods that affect player interaction, keybinds, and overlays.
- Cloth Config, Curios, EMI, JourneyMap, etc.

### P2 - Dimension/Instance/World (High)
Mods that affect the Arena system and instance dimensions.
- Distant Horizons, C2ME, TerraBlender, etc.

### P3 - Combat/Mob Attributes (High)
Mods that affect damage calculation, mob stats, and weapons.
- Iron's Spellbooks, Spell Engine, Better Combat, Pehkui, etc.

### P4 - Telemetry/Performance (Medium)
Mods that provide metrics and optimization.
- Spark, ModernFix, FerriteCore, Lithium, etc.

### P5 - Quality-of-Life/Testing (Medium)
Development and testing tools.
- Crash Assistant, Easy NPC, Dummmmmmy, etc.

### P6 - Cosmetic/Content (Low)
Visual and content mods with minimal integration needs.
- Macaw's mods, Let's Do series, decoration mods, etc.

## Detection Pattern

All mod detection uses NeoForge's `ModList`:

```java
import net.neoforged.fml.ModList;

// Check if mod is loaded
boolean isLoaded = ModList.get().isLoaded("modid");

// Get mod version (if loaded)
String version = ModList.get()
    .getModContainerById("modid")
    .map(c -> c.getModInfo().getVersion().toString())
    .orElse("unknown");
```

## Adding New Integrations

1. Create a new class implementing `CompatModule`
2. Register it in `CompatRegistry`
3. Create documentation in `docs/compat/<modid>.md`
4. Update `MOD_INVENTORY.md` status

See existing integrations for examples.

## Status Legend

| Status | Meaning |
|--------|---------|
| **DONE** | Full integration complete |
| **PARTIAL** | Basic integration, more features planned |
| **TODO** | Not yet integrated |
| **N/A** | No integration needed (library/API only) |

## Changelog

| Date       | Changes                                                                                                         |
|------------|----------------------------------------------------------------------------------------------------------------|
| 2024-12-24 | Added: Controlling, JourneyMap, Apothic Attributes, Easy NPC, Accessories, Player Animation Lib compat modules |
| 2024-12-24 | Initial inventory and infrastructure                                                                            |
