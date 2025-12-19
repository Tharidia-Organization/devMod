# DevMod Features

Complete documentation of all DevMod features and systems.

## Table of Contents
- [Endurance Quest System](#endurance-quest-system)
- [Combat System](#combat-system)
- [Party System](#party-system)
- [Debug Visualization](#debug-visualization)
- [Telemetry System](#telemetry-system)
- [Configuration Screens](#configuration-screens)
- [Keybinds](#keybinds)

---

## Endurance Quest System

A roguelike-inspired wave-based combat mode featuring multiple interconnected gameplay systems.

### Wave Combat
Progressive waves of enemies with increasing difficulty.

- **Wave Progression**: Each wave spawns more enemies with higher stats
- **Mob Selection**: Choose target mob type before starting
- **Arena System**: Isolated instances for each quest
- **Boss Waves**: Special boss encounters every 10 waves

### Perk System
Roguelike perk selection between waves.

**Perk Tiers:**
| Tier | Weight | Color | Description |
|------|--------|-------|-------------|
| Common | 60% | Gray | Basic stat boosts |
| Uncommon | 25% | Green | Enhanced effects |
| Rare | 10% | Blue | Powerful abilities |
| Epic | 4% | Purple | Game-changing perks |
| Legendary | 1% | Gold | Ultimate powers |

**Perk Categories:**
- **Offense**: Damage, crit chance, attack speed
- **Defense**: Health, armor, damage reduction
- **Utility**: Speed, lifesteal, cooldown reduction
- **Special**: Unique mechanics (thorns, execute, etc.)
- **Curse**: Negative effects with bonus rewards

**Perk Mechanics:**
- 3 perks offered per wave completion
- Perks can stack (up to max stacks)
- Synergies between certain perks
- Curse perks increase reward multiplier

### Combo System (DMC-Style)
Style scoring inspired by Devil May Cry.

**Style Ranks:**
| Rank | Threshold | Multiplier |
|------|-----------|------------|
| D (Dull) | 0 | 1.0x |
| C (Crazy) | 500 | 1.2x |
| B (Brutal) | 1500 | 1.5x |
| A (Apocalyptic) | 3500 | 2.0x |
| S (Savage) | 7000 | 3.0x |
| SS (Sadistic) | 12000 | 4.0x |
| SSS (Sensational) | 20000 | 5.0x |

**Scoring Actions:**
| Action | Base Points | Style Points |
|--------|-------------|--------------|
| Light Attack | 10 | 5 |
| Heavy Attack | 25 | 15 |
| Critical Hit | 50 | 30 |
| Aerial Attack | 40 | 25 |
| Backstab | 60 | 40 |
| Counter Attack | 75 | 50 |
| Perfect Dodge | 30 | 20 |
| Parry | 45 | 35 |
| Kill | 100 | 60 |
| Quick Kill | 150 | 90 |
| Overkill | 200 | 120 |
| Multi-Kill (5x) | 1000 | 700 |

**Combo Mechanics:**
- Combo timer: 3 seconds between actions
- Variety bonus: +5% per unique action type
- Combo multiplier: +2% per hit in combo
- Milestone bonuses at 5, 10, 25, 50, 100 hits
- Style decay after 1 second of inactivity
- Damage taken reduces style and halves combo

### Reward System
Multi-currency economy with permanent progression.

**Currencies:**
| Currency | Source | Use |
|----------|--------|-----|
| Tokens | Quest completion, waves | Shop purchases |
| Prestige | Completing all waves | Premium upgrades |
| Blood Gems | Boss kills | Rare items |

**Reward Multipliers:**
- Style rank bonus (up to 3x at SSS)
- Mutator bonus (based on active modifiers)
- No-hit bonus (1.5x if no damage taken)
- Speed bonus (up to 1.5x for fast clears)

**Shop Items:**
- **Stats**: Health boost, damage boost, speed boost
- **Perks**: Starting perks, extra perk slots
- **Utility**: Respawn tokens, loot luck, token multiplier
- **Cosmetics**: Titles, auras

### Achievement System
Track milestones and earn bonus rewards.

| Achievement | Requirement | Reward |
|-------------|-------------|--------|
| First Blood | Complete first quest | 100 Tokens |
| Warmed Up | Complete 10 waves | 250 Tokens |
| Getting Serious | Complete 20 waves | 5 Prestige |
| Unstoppable | 50 waves in endless | 20 Prestige |
| Smokin' Sexy Style! | Reach SSS rank | 500 Tokens |
| Untouchable | 10 waves no damage | 10 Prestige |
| Massacre | 100 kills in one wave | 25 Blood Gems |
| Boss Slayer | Defeat 10 bosses | 30 Blood Gems |

### Mutator System
Optional modifiers that increase difficulty and rewards.

**Positive Mutators:** (Increase difficulty)
- Double Health: Enemies have 2x HP
- Speed Demons: Enemies move 50% faster
- Armored: Enemies have +10 armor
- Enraged: Enemies deal 50% more damage

**Negative Mutators:** (Decrease rewards)
- Easy Mode: Enemies have 50% HP
- Slow Motion: Enemies move 25% slower

---

## Combat System

### Body Part Detection
Precise hitbox targeting using raycasting.

**Detection Zones:**
| Part | Y-Range | Multiplier |
|------|---------|------------|
| Head | 75-100% | 2.0x |
| Body | 40-75% | 1.0x |
| Arms | 40-75% (sides) | 0.8x |
| Legs | 0-40% | 0.8x |

**Features:**
- Works with scaled entities (Pehkui support)
- Configurable multipliers per mob type
- Real-time HUD display of hit location

### Weapon Configuration
Per-weapon stat customization.

**Configurable Stats:**
- Base damage multiplier
- Penetration value (ignores armor)
- Critical hit chance bonus
- Attack speed modifier
- Special effects (lifesteal, etc.)

### Mob Configuration
Per-mob stat overrides.

**Configurable Stats:**
- Health multiplier
- Damage multiplier
- Armor value
- Follow range
- Movement speed

---

## Party System

Multiplayer coordination for synchronized quests.

### Party Formation
1. Create party (auto-assigns leader)
2. Invite players by name
3. Players accept/decline invitations
4. Configure quest settings (mob type, quest type)

### Party States
```
FORMING → READY → IN_QUEST → FORMING
```

- **FORMING**: Members joining, configuring
- **READY**: All members ready, can start
- **IN_QUEST**: Quest in progress

### Leader Controls
- Kick members
- Change quest type
- Change mob type
- Start quest (when all ready)
- Disband party

### Quest Types
| Type | Description |
|------|-------------|
| Wave Defense | Standard wave combat |
| Survival | Endless waves |
| Boss Rush | Boss encounters only |
| Time Attack | Complete in time limit |

---

## Debug Visualization

### Overlay Keybinds
| Key | Feature | Description |
|-----|---------|-------------|
| `G` | Debug Overlay | Hitbox wireframes, body parts |
| `L` | Light Level | Spawn-valid light levels |
| `H` | Heatmap | Death/movement/camping maps |
| `R` | Room Bounds | Room boundary visualization |
| `P` | Pathfinding | Mob navigation paths |
| `V` | Line of Sight | LoS between mobs and player |
| `Y` | Vertical Levels | Floor/mid/high zones |
| `C` | Safe Spots | Potential exploit locations |
| `U` | Attribute Monitor | Entity attribute changes |
| `F8` | FPS Tracker | Frame rate monitoring |
| `F9` | Performance Profiler | Detailed timing |

### Heatmap Types
Cycle through with `H`:
1. **Death Heatmap**: Where players/mobs die
2. **Movement Heatmap**: Traffic patterns
3. **Camping Heatmap**: Stationary positions

---

## Telemetry System

### Data Collection
| Service | Data Collected |
|---------|----------------|
| DamageTracking | Every hit, damage, body part |
| FightSession | TTK, DPS, weapon usage |
| SpatialMetrics | Positions, heatmaps |
| EconomyMetrics | Currency flow |
| DungeonSession | Run statistics |

### Export Format
NDJSON files in `run/telemetry/`:
- `hits.ndjson` - Combat hits
- `deaths.ndjson` - Death events
- `alerts.ndjson` - Anomalies
- `performance.ndjson` - Server metrics

---

## Configuration Screens

### Unified Settings (`K`)
Tabbed settings interface with categories:
- **General**: Core mod settings
- **Combat**: Body part multipliers, weapon config
- **Debug**: Overlay toggles
- **Telemetry**: Data collection settings
- **Keybinds**: Customize controls

### Weapon Editor (`M`)
Configure held weapon:
- Damage multiplier slider
- Penetration value
- Critical chance
- Save/load presets

### Testing Hub (`N` / `F7`)
QA testing interface:
- Spawn test mobs
- Trigger scenarios
- View test results
- Export reports

### Radial Menu (`O`)
Quick access wheel:
- Toggle common features
- Quick teleport
- Spawn shortcuts

---

## Keybinds

### Debug Keys
| Key | Action |
|-----|--------|
| `G` | Toggle debug overlay |
| `L` | Toggle light levels |
| `H` | Cycle heatmaps |
| `R` | Toggle room bounds |
| `P` | Toggle pathfinding |
| `V` | Toggle line of sight |
| `Y` | Toggle vertical levels |
| `C` | Toggle safe spots |
| `U` | Toggle attribute monitor |

### Screen Keys
| Key | Screen |
|-----|--------|
| `K` | Unified Settings |
| `M` | Weapon Editor |
| `J` | Telemetry Dashboard |
| `N` | Testing Hub |
| `F7` | Testing Hub (alt) |
| `O` | Radial Menu |

### Performance Keys
| Key | Action |
|-----|--------|
| `F8` | Toggle FPS tracker |
| `F9` | Toggle performance profiler |

---

## Commands

```
/devtest hud <on|off|toggle>     - Toggle Impact HUD
/devtest panel <on|off|toggle>   - Toggle 3D panels
/devtest debug <on|off|toggle>   - Toggle debug renderer
/devtest debugbox <size>         - Add debug box at player
/devtest debugclear              - Clear debug shapes
/devtest panelclear              - Clear 3D panels
/devtest info                    - Show system status
/devtest qa                      - Open Testing Hub
/devtest bodypart <part>         - Show body part info

/telemetry reload                - Reload telemetry config
```
