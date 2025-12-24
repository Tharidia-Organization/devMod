# Target Package Architecture

## Overview

This document describes the final target package structure for DevMod after the December 2024 reorganization.

## Package Tree

```
com.devmod/
├── DevMod.java              # Main mod entrypoint
├── DevModClient.java        # Client-only entrypoint
├── ModConfig.java           # Root configuration
│
├── abilities/               # Player abilities (dash, dodge, stamina)
├── actions/                 # Action system
│   └── client/              # Client-side action contexts
├── ammo/                    # Ammo system utilities
│
├── arena/                   # Arena system (large module)
│   ├── admin/               # Admin commands
│   ├── alert/               # Alert system
│   ├── analytics/           # Arena analytics
│   ├── api/                 # Public API
│   ├── builder/             # Arena building
│   ├── challenge/           # Challenge modes
│   ├── cleanup/             # Resource cleanup
│   ├── command/             # Commands
│   ├── config/              # Arena config
│   ├── currency/            # Token/currency
│   ├── event/               # Event handling
│   ├── failure/             # Failure handling
│   ├── fallback/            # Fallback strategies
│   ├── gate/                # Entry gates
│   ├── health/              # Health monitoring
│   ├── hud/                 # Arena debug HUD
│   ├── identity/            # Arena identity
│   ├── instance/            # Arena instances
│   ├── integration/         # External integrations
│   ├── leaderboard/         # Leaderboards
│   ├── logging/             # Logging
│   ├── metrics/             # Metrics collection
│   ├── monitor/             # Monitoring
│   ├── network/             # Arena networking
│   ├── persistence/         # Data persistence
│   ├── pool/                # Resource pooling
│   ├── recovery/            # Crash recovery
│   ├── registry/            # Template registry
│   ├── rewards/             # Reward system
│   ├── security/            # Security checks
│   ├── serialization/       # Serialization
│   ├── spawn/               # Mob spawning
│   ├── tags/                # Tag system
│   ├── telemetry/           # Arena telemetry
│   ├── template/            # Template system
│   ├── ui/                  # Arena UI
│   └── validation/          # Input validation
│
├── attributes/              # Custom attributes
├── bridge/                  # Integration bridges
│
├── client/                  # Client-only code
│   └── input/               # Input handling
│
├── collision/               # Collision system
│   ├── bodypart/            # Body part detection
│   ├── compat/              # Compatibility
│   ├── integration/         # Integration
│   ├── obb/                 # OBB math
│   ├── registry/            # Collision registry
│   ├── rendering/           # Debug rendering
│   └── transform/           # Transformations
│
├── combat/                  # Combat system
│   ├── filter/              # Target filtering
│   ├── shield/              # Shield system
│   └── tracking/            # Combat tracking
│
├── compat/                  # Mod compatibility
│   └── mods/                # Per-mod compat
│       ├── clothconfig/
│       ├── curios/
│       ├── ironsspellbooks/
│       ├── rangedweaponapi/
│       ├── spark/
│       ├── spellengine/
│       └── spellpower/
│
├── components/              # Data components
├── config/                  # Configuration
├── damage/                  # Damage system
│
├── debug/                   # Debug utilities
│   └── client/              # Client debug
│
├── effects/                 # Mob effects
├── endurance/               # Endurance quest system
│   └── analytics/           # Quest analytics
│
├── events/                  # Event handlers
├── gametest/                # Game tests
├── integration/             # Generic integrations
├── migration/               # Data migration
├── mixin/                   # Mixins
│
├── network/                 # Network packets (was: transport)
│   └── handlers/            # Packet handlers
│
├── overlay/                 # HUD overlays
├── panels/                  # Debug panels
│   ├── context/
│   ├── core/
│   ├── tracking/
│   ├── types/
│   └── ui/
│
├── party/                   # Party system
├── quest/                   # Quest system
├── recipe/                  # Recipe system
│
├── rendering/               # Rendering
│   ├── shader/              # Shaders
│   └── shield/              # Shield rendering
│
├── runtime/                 # Runtime utilities
├── stats/                   # Statistics
├── tags/                    # Item/block tags
│
├── telemetry/               # Telemetry system
│   ├── boss/                # Boss telemetry
│   ├── combat/              # Combat telemetry
│   ├── damage/              # Damage telemetry
│   ├── dashboard/           # Dashboard
│   ├── duckdb/              # DuckDB integration
│   │   └── packets/
│   ├── dungeon/             # Dungeon telemetry
│   ├── economy/             # Economy telemetry
│   ├── endurance/           # Endurance telemetry
│   ├── entity/              # Entity telemetry
│   ├── export/              # Data export
│   ├── player/              # Player telemetry
│   ├── progression/         # Progression telemetry
│   ├── room/                # Room telemetry
│   ├── skills/              # Skills telemetry
│   ├── spatial/             # Spatial telemetry
│   ├── ui/                  # Telemetry UI
│   └── util/                # Utilities
│
├── testing/                 # Testing utilities
│   ├── config/
│   └── stats/
│
├── ui/                      # User interface
│   ├── components/          # Shared components
│   ├── editor/              # Item editor
│   │   ├── components/
│   │   ├── controller/
│   │   ├── core/
│   │   ├── debug/
│   │   ├── favorites/
│   │   ├── modules/
│   │   ├── overlay/
│   │   ├── sections/
│   │   ├── state/
│   │   └── systems/
│   ├── hub/                 # Testing hub
│   ├── radial/              # Radial menu
│   │   ├── animation/
│   │   ├── config/
│   │   ├── input/
│   │   ├── model/
│   │   └── render/
│   ├── screens/             # Game screens
│   ├── scroll/              # Scroll utilities
│   │   └── impl/
│   ├── testing/             # Test UI
│   │   ├── pages/
│   │   └── panel/
│   ├── unified/             # Unified settings
│   │   ├── components/
│   │   ├── pages/
│   │   └── persistence/
│   └── wizard/              # Wizard UI
│
└── util/                    # General utilities
```

## Module Boundaries

### Core Modules (Server + Client)
- `abilities/` - Stamina, dash, dodge
- `arena/` - Full arena system
- `combat/` - Combat calculations
- `endurance/` - Endurance quest
- `network/` - Packet handling
- `party/` - Party system
- `telemetry/` - Data collection

### Client-Only Modules
- `client/` - Client bootstrap
- `overlay/` - HUD overlays
- `rendering/` - Visual effects
- `ui/` - All screens and UI

### Infrastructure
- `config/` - Configuration
- `events/` - Event handlers
- `util/` - Utilities

## Key Naming Conventions

| Convention | Example |
|------------|---------|
| Package names | lowercase, single word preferred |
| Handler classes | `*Handler`, `*NetworkHandler` |
| Payload records | `*Payload` |
| Screen classes | `*Screen` |
| Event handlers | `*EventHandler` |
| Services | `*Service` |

## Files Excluded from Packages

The root package `com.devmod/` should contain only:
1. `DevMod.java` - Main entrypoint
2. `DevModClient.java` - Client entrypoint
3. `ModConfig.java` - Root config (if needed)

All other code should be in subpackages.
