# DevMod - Combat Testing & Level Design Toolkit

A comprehensive NeoForge 1.21.1 mod providing advanced combat systems, debugging tools, telemetry, and analysis features for Minecraft level designers and mod developers.

## Requirements

- **Java 21** or later
- **NeoForge 21.1.x** (tested with 21.1.42)
- **Minecraft 1.21.1**

## Core Features

### Endurance Quest System
A roguelike-inspired wave-based combat mode with extensive gameplay systems:

- **Wave Combat**: Progressive difficulty waves with configurable mob types
- **Perk System**: Roguelike perk selection between waves (Common → Legendary tiers)
- **Combo System**: DMC-inspired style scoring (D → SSS ranks) with multipliers
- **Reward System**: Multi-currency economy (Tokens, Prestige, Blood Gems)
- **Party System**: Multiplayer party formation with synchronized quests
- **Shop System**: Permanent upgrades and unlocks
- **Achievement System**: 10+ achievements with currency rewards
- **Boss Waves**: Special boss encounters every 10 waves
- **Mutator System**: Gameplay modifiers for increased challenge/rewards

### Combat System
- **Body Part Detection**: Precise hitbox targeting with configurable damage multipliers
- **Weapon Configuration**: Per-weapon damage stats, penetration, and multipliers
- **Mob Configuration**: Customize health, damage, armor, and follow range per mob type
- **Real-time Damage Display**: HUD overlay showing impact damage and body part hit
- **Trail Effects**: Visual weapon trails during combat

### Debug Visualization Tools
| Key | Feature | Description |
|-----|---------|-------------|
| `G` | Debug Overlay | Wireframe hitboxes with body part visualization |
| `L` | Light Level Overlay | Shows spawn-valid light levels |
| `H` | Heatmap Toggle | Cycles through death, movement, camping heatmaps |
| `R` | Room Bounds | Visualizes room boundaries |
| `P` | Pathfinding Debug | Shows mob navigation paths |
| `V` | Line of Sight | Visualizes LoS between mobs and player |
| `Y` | Vertical Levels | Shows floor/mid/high zones |
| `C` | Safe Spots | Highlights potential exploit locations |
| `U` | Attribute Monitor | Tracks entity attribute changes |
| `F8` | FPS Tracker | Performance monitoring |
| `F9` | Performance Profiler | Detailed timing breakdowns |

### Configuration Screens
| Key | Screen | Description |
|-----|--------|-------------|
| `K` | Unified Settings | Main settings hub (Axiom-style UI) |
| `M` | Weapon Editor | Configure held weapon stats |
| `J` | Telemetry Dashboard | View collected analytics |
| `N` / `F7` | Testing Hub | QA testing interface |
| `O` | Radial Menu | Quick access to common actions |

### Telemetry System
Comprehensive data collection for level design analysis:
- **Combat Metrics**: Hit tracking, damage, TTK (Time To Kill), weapon stats
- **Spatial Metrics**: Movement heatmaps, death locations, stuck points
- **Behavior Metrics**: Aggro drops, boss resets, kiting patterns
- **Performance Metrics**: TPS, MSPT, entity counts, FPS

Data exported as NDJSON files in `run/telemetry/`.

## Installation

1. Install NeoForge 21.1.x for Minecraft 1.21.1
2. Download the mod JAR from releases
3. Place in your `mods/` folder
4. Launch Minecraft

## Building from Source

```bash
# Clone the repository
git clone https://github.com/your-repo/devmod.git
cd devmod

# Build the mod
./gradlew build

# Run the client for testing
./gradlew runClient

# Run tests
./gradlew test --no-build-cache
```

## Project Structure

```
src/main/java/com/frenkvs/devmod/
├── DevMod.java                 # Main mod entry point
├── DevModClient.java           # Client-side initialization
├── attributes/                 # Attribute monitoring system
├── debug/                      # Debug commands and rendering
├── effects/                    # Visual effects (trails)
├── endurance/                  # Endurance Quest System
│   ├── ArenaManager.java       # Legacy arena adapter (deprecated)
│   ├── WaveManager.java        # Wave spawning logic
│   ├── PerkSystem.java         # Roguelike perks
│   ├── ComboSystem.java        # Style scoring (D→SSS)
│   ├── RewardSystem.java       # Currency & rewards
│   ├── MutatorSystem.java      # Gameplay modifiers
│   └── GamificationManager.java # Progress tracking
├── hud/                        # HUD overlays
├── instance/                   # Dynamic dimension system
├── integration/                # Mod compatibility
├── panels/                     # Floating 3D panels
├── party/                      # Multiplayer party system
├── quest/                      # Quest framework
├── rendering/                  # Debug visualization
├── telemetry/                  # Data collection services
├── testing/                    # QA testing framework
└── ui/                         # Screen implementations
```

```
src/main/java/com/devmod/arena/
├── registry/                   # Template registry + validation
├── policy/                     # Policy routing + scoring
├── builder/                    # TemplateArenaBuilder + async build
├── telemetry/                  # Arena metrics + audit
├── cleanup/                    # Cleanup executor + residuals
└── ...                         # Pool, monitoring, alerts, etc.
```

## Configuration

Configuration files are stored in `run/config/devmod/`:

| File | Purpose |
|------|---------|
| `devmod-common.toml` | Main configuration |
| `telemetry_settings.json` | Telemetry thresholds |
| `mob_configs.json` | Per-mob stat overrides |
| `weapon_configs.json` | Per-weapon stat overrides |

### Body Part Damage Multipliers (Default)
| Body Part | Multiplier |
|-----------|------------|
| Head | 2.0x |
| Body | 1.0x |
| Arms | 0.8x |
| Legs | 0.8x |

## Mod Compatibility

DevMod includes soft integrations with:
- **Pehkui**: Entity scale support for hitbox calculations
- **Better Combat**: Enhanced combat system compatibility

## Testing

The mod includes **2172 automated tests** covering all major systems:

```bash
# Run all tests
./gradlew test --no-build-cache

# Run specific test suites
./gradlew test --no-build-cache --tests "com.devmod.endurance.*"
./gradlew test --no-build-cache --tests "com.devmod.party.*"
```

### Test Coverage by System
| System | Tests | Coverage |
|--------|-------|----------|
| Endurance Quest | 400+ | Perk, Combo, Reward, Wave |
| Party System | 200+ | Sync, Flow, Payload |
| Instance System | 150+ | Dimension, Recovery |
| Combat System | 100+ | Damage, Hit detection |
| Integration | 300+ | End-to-end flows |
| Stress Tests | 200+ | Concurrency, Memory |

## Documentation

- [Architecture Overview](docs/ARCHITECTURE.md)
- [Feature Documentation](docs/FEATURES.md)
- [Testing Guide](docs/testing/TESTING.md)
- [Progressive Test Plan](docs/testing/PROGRESSIVE_TEST_PLAN.md)

## Security Features

- **Path Sanitization**: All file I/O operations are validated
- **Packet Validation**: Network packets are rate-limited and value-clamped
- **Double-Spending Prevention**: Synchronized purchase operations
- **Memory Management**: Automatic cleanup of long-session data

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see [LICENSE](LICENSE) for details.

---

**Version:** 0.1.0
**Minecraft:** 1.21.1
**NeoForge:** 21.1.42+
**Tests:** 2172 passing
