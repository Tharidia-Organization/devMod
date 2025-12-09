# DevMod - Mob Config Viewer & Level Design Toolkit

A NeoForge 1.21.1 mod providing advanced debugging, telemetry, and analysis tools for Minecraft level designers and mod developers.

## Requirements

- **Java 21** or later
- **NeoForge 21.1.x** (tested with 21.1.42)
- **Minecraft 1.21.1**

## Features

### Combat System
- **Body Part Detection**: Precise hitbox targeting with configurable damage multipliers (head, body, arms, legs)
- **Weapon Configuration**: Per-weapon damage stats, penetration, and multipliers
- **Mob Configuration**: Customize health, damage, armor, and follow range per mob type
- **Real-time Damage Display**: HUD overlay showing impact damage and body part hit

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
| `K` | Voxel-Lab Dashboard | Main settings hub (Axiom-style UI) |
| `M` | Weapon Editor | Configure held weapon stats |
| `J` | Telemetry Dashboard | View collected analytics |
| `N` / `F7` | Testing Hub | QA testing interface |

### Telemetry System
Comprehensive data collection for level design analysis:
- **Combat Metrics**: Hit tracking, damage, TTK (Time To Kill), weapon stats
- **Spatial Metrics**: Movement heatmaps, death locations, stuck points
- **Behavior Metrics**: Aggro drops, boss resets, kiting patterns
- **Performance Metrics**: TPS, MSPT, entity counts

Data exported as NDJSON files in `run/telemetry/`:
- `hits.ndjson` - All combat hits
- `deaths.ndjson` - Death events with TTK
- `alerts.ndjson` - Anomalies (stuck, camping, aggro drop)
- `performance.ndjson` - Server performance

### Commands
```
/devtest hud <on|off|toggle>     - Toggle Impact HUD
/devtest panel <on|off|toggle>   - Toggle 3D panels
/devtest debug <on|off|toggle>   - Toggle debug renderer
/devtest debugbox <size>         - Add debug box at player position
/devtest debugclear              - Clear all debug shapes
/devtest panelclear              - Clear all 3D panels
/devtest info                    - Show system status
/devtest qa                      - Open Testing Hub
/devtest bodypart <part>         - Show body part multiplier info

/telemetry reload                - Reload telemetry config
```

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
./gradlew test
```

## Project Structure

```
src/main/java/com/frenkvs/devmod/
├── DevMod.java                 # Main mod entry point
├── DevModClient.java           # Client-side initialization
├── HitHelper.java              # Body part detection logic
├── DamageHandler.java          # Damage calculation and events
├── WeaponStats.java            # Weapon configuration
├── attributes/                 # Attribute monitoring system
├── gametest/                   # Test harness and commands
├── hud/                        # HUD overlays (Impact, 3D panels)
├── integration/                # Mod compatibility (Pehkui, BetterCombat)
├── rendering/                  # Debug visualization renderers
├── telemetry/                  # Data collection services
│   ├── TelemetryService.java   # Main orchestrator
│   ├── combat/                 # Fight sessions
│   ├── damage/                 # Damage tracking
│   ├── entity/                 # Entity tracking
│   └── spatial/                # Heatmaps
├── testing/                    # QA testing framework
├── ui/                         # Screen implementations
│   └── hub/                    # Modular UI components
└── util/                       # Utilities (PathSanitizer, etc.)
```

## Configuration

Configuration files are stored in `run/config/devmod/`:

- `devmod-common.toml` - Main configuration
- `telemetry_settings.json` - Telemetry thresholds
- `telemetry_rooms.json` - Room definitions for spatial analysis
- `mob_configs.json` - Per-mob stat overrides
- `weapon_configs.json` - Per-weapon stat overrides

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

## Security Features

- **Path Sanitization**: All file I/O operations are validated against allowed directories
- **Packet Validation**: Network packets are rate-limited and value-clamped
- **Memory Management**: Automatic cleanup of long-session data (logs, telemetry)

## Testing

The mod includes a comprehensive testing framework:

```bash
# Run all unit tests
./gradlew test

# Run specific test class
./gradlew test --tests "com.frenkvs.devmod.PacketSecurityServiceTest"
./gradlew test --tests "com.frenkvs.devmod.PathSanitizerTest"
```

### Test Coverage
- `PacketSecurityServiceTest` - Network security validation
- `PathSanitizerTest` - File path sanitization

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see [LICENSE](LICENSE) for details.

## Resources

- [NeoForge Documentation](https://docs.neoforged.net/)
- [NeoForge Discord](https://discord.neoforged.net/)
- [Mojang Mapping License](https://github.com/NeoForged/NeoForm/blob/main/Mojang.md)

---

**Version:** 0.0.1
**Minecraft:** 1.21.1
**NeoForge:** 21.1.42+
