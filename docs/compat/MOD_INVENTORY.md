# DevMod - Mod Compatibility Inventory

> Last Updated: 2024-12-24
> Source: `run/mods/` + `build.gradle` dependencies

## Summary

- **Total Mods Detected**: 227+
- **Integration DONE**: 21 (CompatModule pattern)
- **Integration PARTIAL**: 0
- **Priority Categories**: 6

## Compat Architecture Status: **COMPLETE**

The standardized compat layer is fully implemented:

- [Compat.java](../../src/main/java/com/devmod/compat/Compat.java) - Detection utility
- [CompatModule.java](../../src/main/java/com/devmod/compat/CompatModule.java) - Standard interface
- [CompatRegistry.java](../../src/main/java/com/devmod/compat/CompatRegistry.java) - Central registry
- [ModIntegrationManager.java](../../src/main/java/com/devmod/integration/ModIntegrationManager.java) - Entry point

---

## Priority Order for Integration

| Priority | Category | Impact Area | Count |
|----------|----------|-------------|-------|
| P1 | UI/Input/HUD | Player interaction, keybinds, overlays | ~15 |
| P2 | Dimension/Instance/World | Arena system, instances | ~8 |
| P3 | Combat/Mob Attributes | Damage calc, mob stats, weapons | ~35 |
| P4 | Telemetry/Performance | Metrics, optimization | ~12 |
| P5 | Quality-of-Life/Testing | Dev tools, debugging | ~20 |
| P6 | Cosmetic/Content | Visual, building blocks | ~137+ |

---

## P1 - UI/Input/HUD Mods (CRITICAL)

| Mod Name | ModID | Version | Source | Status | Notes |
|----------|-------|---------|--------|--------|-------|
| Better Combat | `bettercombat` | 2.2.5+1.21.1 | run/mods | **DONE** | Combat animations, extended reach |
| Cloth Config | `cloth-config` | 15.0.140 | run/mods | **DONE** | Config screen API |
| Controlling | `controlling` | 19.0.5 | run/mods | **DONE** | Keybind conflict detection |
| EMI | `emi` | 1.1.22+1.21.1 | run/mods | **DONE** | Recipe/item lookup integration |
| FancyMenu | `fancymenu` | 3.7.0 | run/mods | TODO | Menu customization |
| InvMove | `invmove` | 0.9.1 | run/mods | TODO | Movement in inventory |
| JourneyMap | `journeymap` | 6.0.0-beta.52 | run/mods | **DONE** | Waypoint API for Arena |
| MouseTweaks | `mousetweaks` | 2.26.1 | run/mods | TODO | Inventory mouse controls |
| Not Enough Animations | `notenoughanimations` | 1.10.6 | run/mods | TODO | Player animations |
| Player Animation Lib | `playeranimator` | 2.0.1+1.21.1 | run/mods | **DONE** | Animation state tracking |
| Yet Another Config Lib | `yacl` | 3.8.0+1.21.1 | run/mods | TODO | Config screen framework |
| Curios | `curios` | 9.5.1+1.21.1 | run/mods | **DONE** | Equipment slots API |
| Accessories | `accessories` | 1.1.0-beta.52 | run/mods | **DONE** | Modern equipment slots |
| Emotecraft | `emotecraft` | 2.4.12 | run/mods | TODO | Player emotes |
| First Person | `firstperson` | 2.5.0 | run/mods | TODO | First-person model |

---

## P2 - Dimension/Instance/World Mods (HIGH)

| Mod Name | ModID | Version | Source | Status | Notes |
|----------|-------|---------|--------|--------|-------|
| Distant Horizons | `distanthorizons` | 2.3.0-b | build.gradle | **DONE** | LOD rendering |
| C2ME | `c2me` | 0.3.0+alpha.0.87 | run/mods | TODO | Chunk loading optimization |
| Chunk Loaders | `chunkloaders` | 1.2.8 | run/mods | TODO | Keep chunks loaded |
| TerraBlender | `terrablender` | 4.1.0.8 | run/mods | TODO | Biome API |
| Terralith | `terralith` | 2.5.8 | run/mods | TODO | World generation |
| Structurify | `structurify` | 2.0.4a | run/mods | TODO | Structure control |
| YUNG's API | `yungsapi` | 5.1.6 | run/mods | TODO | Structure API |
| Open Loader | `openloader` | 21.1.5 | run/mods | TODO | Resource/datapack loading |

---

## P3 - Combat/Mob Attributes Mods (HIGH)

| Mod Name | ModID | Version | Source | Status | Notes |
|----------|-------|---------|--------|--------|-------|
| Pehkui | `pehkui` | 3.8.3+1.21 | run/mods | **DONE** | Entity scaling |
| Iron's Spells | `irons_spellbooks` | 3.14.4 | run/mods | **DONE** | Magic system |
| Spell Engine | `spell_engine` | 1.8.11+1.21.1 | run/mods | **DONE** | Spell framework |
| Spell Power | `spell_power` | 1.4.3+1.21.1 | run/mods | **DONE** | Spell attributes |
| GeckoLib | `geckolib` | 4.8.2 | run/mods | **DONE** | Animation library, bone transforms |
| AzureLib | `azurelib` | 3.1.1 | run/mods | **DONE** | Animation library, bone transforms |
| More RPG Library | `more_rpg_library` | 2.5.1+1.21.1 | run/mods | TODO | RPG attributes |
| Apothic Attributes | `apothicattributes` | 2.9.0 | run/mods | **DONE** | Extended attributes (crit, lifesteal) |
| SmartBrainLib | `smartbrainlib` | 1.16.11 | run/mods | TODO | Mob AI |
| Epic Knights | `epic_knights` | 9.30 | run/mods | TODO | Medieval weapons |
| Archers | `archers` | 2.6.8+1.21.1 | run/mods | TODO | Archery system |
| Paladins | `paladins` | 2.6.3+1.21.1 | run/mods | TODO | Paladin class |
| Wizards | `wizards` | 2.6.4+1.21.1 | run/mods | TODO | Wizard class |
| Runes | `runes` | 1.2.0+1.21.1 | run/mods | TODO | Rune system |
| Arsenal | `arsenal` | 1.3.2+1.21.1 | run/mods | TODO | Weapons |
| Armory | `armory` | 1.2.9+1.21.1 | run/mods | TODO | Armor |
| Ranged Weapon API | `ranged_weapon_api` | 2.3.2+1.21.1 | run/mods | **DONE** | Ranged weapons |
| Shield API | `shield_api` | 2.2.0 | run/mods | TODO | Shield mechanics |
| Mowzie's Mobs | `mowziesmobs` | 1.7.5 | run/mods | **DONE** | Boss detection, ability tracking |
| Rotten Creatures | `rottencreatures` | 1.1.2 | run/mods | TODO | Undead mobs |
| Bosses Rise | `bossesrise` | 1.0.9 | run/mods | TODO | Boss encounters |
| FD Bosses | `fdbosses` | 2.0.5 | run/mods | TODO | Custom bosses |
| Butcher | `butcher` | 3.5.6 | run/mods | TODO | Combat overhaul |
| Swashbucklers | `swashbucklers` | 2.6.6C | run/mods | TODO | Pirate combat |
| Musket Mod | `musketmod` | 1.5.4 | run/mods | TODO | Firearms |
| Knight Quest | `knightquest` | 1.9.0 | run/mods | TODO | Knight abilities |
| Relics | `relics` | 1.2.1+1.21.1 | run/mods | TODO | Artifact system |
| Elixirum | `elixirum` | 0.2.2 | run/mods | TODO | Potions |
| Toxony | `toxony` | 0.10.5 | run/mods | TODO | Poison system |
| Hexalia | `hexalia` | 1.2.81 | run/mods | TODO | Magic |
| Walkers | `walkers` | 5.7 | run/mods | TODO | Mob morphing |
| Revive Me | `revive_me` | 5.4.6 | run/mods | TODO | Player revival |
| PVP Flagging | `pvp_flagging` | 1.1.2 | run/mods | TODO | PVP control |
| Puffish Skills | `puffish_skills` | 0.16.8 | run/mods | TODO | Skill tree |

---

## P4 - Telemetry/Performance Mods (MEDIUM)

| Mod Name | ModID | Version | Source | Status | Notes |
|----------|-------|---------|--------|--------|-------|
| Spark | `spark` | 1.10.124 | run/mods | **DONE** | Profiler |
| ModernFix | `modernfix` | 5.25.1 | run/mods | TODO | Performance patches |
| FerriteCore | `ferritecore` | 7.0.2 | run/mods | TODO | Memory optimization |
| Lithium | `lithium` | 0.15.0 | run/mods | TODO | Game optimization |
| Sodium | `sodium` | 0.6.13 | run/mods | TODO | Rendering optimization |
| EntityCulling | `entityculling` | 1.9.3 | run/mods | TODO | Entity optimization |
| MoreCulling | `moreculling` | 1.0.6 | run/mods | TODO | Culling optimization |
| Dynamic FPS | `dynamic_fps` | 3.9.5 | run/mods | TODO | FPS management |
| Saturn | `saturn` | 0.1.5 | run/mods | TODO | Memory optimization |
| GPU Tape | `gputape` | 1.0.3 | run/mods | TODO | GPU monitoring |
| Scalable Lux | `scalablelux` | 0.1.0.1 | run/mods | TODO | Lighting optimization |
| Iris | `iris` | 1.8.8 | run/mods | TODO | Shader support |

---

## P5 - Quality-of-Life/Testing Mods (MEDIUM)

| Mod Name | ModID | Version | Source | Status | Notes |
|----------|-------|---------|--------|--------|-------|
| Crash Assistant | `crashassistant` | 1.10.19 | run/mods | TODO | Crash reports |
| Easy NPC | `easy_npc` | 6.0.21 | run/mods | **DONE** | Arena NPC spawning |
| Dummmmmmy | `dummmmmmy` | 2.0.9 | run/mods | **DONE** | Training dummies, DPS tracking |
| Default Options | `defaultoptions` | 21.1.5 | run/mods | TODO | Default settings |
| NBT Copy | `nbtcopy` | 1.0.4 | run/mods | TODO | NBT utilities |
| Searchables | `searchables` | 1.0.2 | run/mods | TODO | Search functionality |
| Plasmo Voice | `plasmovoice` | 2.1.6 | run/mods | TODO | Voice chat |
| Music Player | `music_player` | 2.7.1.351 | run/mods | TODO | In-game music |
| Antique Atlas | `antiqueatlas` | 8.0.1 | run/mods | TODO | Map system |
| Item Obliterator | `item_obliterator` | 2.3.0 | run/mods | TODO | Item deletion |
| Client Sort | `clientsort` | 2.1.2+1.21.1 | run/mods | TODO | Inventory sorting |
| Pick Up Notifier | `pickupnotifier` | 21.1.1 | run/mods | TODO | Item pickup HUD |
| Overflowing Bars | `overflowingbars` | 21.1.1 | run/mods | TODO | Health bar overflow |
| Leave My Bars Alone | `leavemybarsalone` | 21.1.2 | run/mods | TODO | HUD bar protection |
| Quick Help Overlay | `quickhelpoverlay` | - | internal | TODO | Help system |
| Notable Bubble Text | `notablebubbletext` | 4.0.2 | run/mods | TODO | Chat bubbles |
| Modopedia | `modopedia` | 1.1.5 | run/mods | TODO | Mod documentation |
| Fast IP Ping | `fast-ip-ping` | 1.0.7 | run/mods | TODO | Server ping |
| Disconnect Packet Fix | `disconnectpacketfix` | 2.0.1 | run/mods | TODO | Network fix |
| Packet Fixer | `packetfixer` | 3.3.0 | run/mods | TODO | Packet optimization |

---

## P6 - Cosmetic/Content Mods (LOW)

### Libraries & APIs
| Mod Name | ModID | Version | Status |
|----------|-------|---------|--------|
| Architectury | `architectury` | 13.0.8 | TODO |
| Balm | `balm` | 21.0.54 | TODO |
| Moonlight | `moonlight` | 2.26.1 | TODO |
| PuzzlesLib | `puzzleslib` | 21.1.39 | TODO |
| Resourceful Lib | `resourcefullib` | 3.0.12 | TODO |
| Resourceful Config | `resourcefulconfig` | 3.0.11 | TODO |
| Kiwi | `kiwi` | 15.8.1 | TODO |
| Kotlin for Forge | `kotlinforforge` | 5.10.0 | TODO |
| OwoLib | `owo-lib` | 0.12.15.5-beta.1 | TODO |
| Creative Core | `creativecore` | - | TODO |
| Forge Config API Port | `forgeconfig` | 21.1.4 | TODO |
| Forgified Fabric API | `fabric_api` | 0.115.6 | TODO |
| LibrarianLib | `librarianlib` | 5.0.0 | TODO |
| Supermartijn642 Core Lib | `supermartijn642corelib` | 1.1.18a | TODO |
| Supermartijn642 Config Lib | `supermartijn642configlib` | 1.1.8 | TODO |

### Building & Decoration
| Mod Name | ModID | Version | Status |
|----------|-------|---------|--------|
| Macaw's Bridges | `mcw_bridges` | 3.1.1 | TODO |
| Macaw's Doors | `mcw_doors` | 1.1.2 | TODO |
| Macaw's Fences | `mcw_fences` | 1.2.0 | TODO |
| Macaw's Furniture | `mcw_furniture` | 3.4.0 | TODO |
| Macaw's Lights | `mcw_lights` | 1.1.2 | TODO |
| Macaw's Paths | `mcw_paths` | 1.1.1 | TODO |
| Macaw's Windows | `mcw_windows` | 2.4.1 | TODO |
| Macaw's Stairs | `mcw_stairs` | 1.0.1 | TODO |
| Macaw's Trapdoors | `mcw_trapdoors` | 1.1.4 | TODO |
| Chipped | `chipped` | 4.0.2 | TODO |
| Handcrafted | `handcrafted` | 4.0.3 | TODO |
| Amendments | `amendments` | 2.0.8 | TODO |
| Supplementaries | `supplementaries` | 3.4.20 | TODO |
| Dawn of Time Builder | `dawnoftimebuilder` | 1.6.4 | TODO |
| Domum Ornamentum | `domum_ornamentum` | 1.0.222 | TODO |
| Let's Do Furniture | `furniture` | 1.1.1 | TODO |
| Immersive Furniture | `immersive_furniture` | 0.1.2 | TODO |

### Food & Farming
| Mod Name | ModID | Version | Status |
|----------|-------|---------|--------|
| Farmer's Delight | `farmersdelight` | 1.2.9 | TODO |
| Croptopia | `croptopia` | 4.2.1 | TODO |
| Let's Do Bakery | `bakery` | 2.1.2 | TODO |
| Let's Do Brewery | `brewery` | 2.1.5 | TODO |
| Let's Do Candlelight | `candlelight` | 2.1.4 | TODO |
| Let's Do Vinery | `vinery` | 1.5.1 | TODO |
| Let's Do Meadow | `meadow` | 1.4.3 | TODO |
| Let's Do Herbal Brews | `herbalbrews` | 1.1.2 | TODO |
| Let's Do Beach Party | `beachparty` | 2.1.2 | TODO |
| Let's Do Farm & Charm | `farm_and_charm` | 1.1.12 | TODO |
| Cultural Delights | `culturaldelights` | 0.17.7 | TODO |
| Cuisine Delight | `cuisinedelight` | 1.2.6 | TODO |
| Rustic Delight | `rusticdelight` | 1.5.1 | TODO |
| Extra Delight | `extradelight` | 2.6.2 | TODO |
| Expanded Delight | `expandeddelight` | 0.1.3.2 | TODO |
| Seed Delight | `seeddelight` | 1.0.1 | TODO |
| SOL Valpotato | `sol_valpotato` | 1.2 | TODO |
| Spoiled | `spoiled` | 6.2.1 | TODO |

### Mobs & Creatures
| Mod Name | ModID | Version | Status |
|----------|-------|---------|--------|
| Critters and Companions | `crittersandcompanions` | 2.3.4 | TODO |
| Fowl Play | `fowlplay` | 1.1.0-beta.3 | TODO |
| Graveyard | `graveyard` | 1.0.1 | TODO |
| Chocco's Mobs | `choccos_mobs` | 0.2.0 | TODO |
| Blast from the Past | `blastfromthepast` | 1.0.4 | TODO |
| Heralds Luna | `heralds_luna` | 2.4 | TODO |
| Mythrais | `mythrais` | 4.0.0 | TODO |

### Visual & Effects
| Mod Name | ModID | Version | Status |
|----------|-------|---------|--------|
| Entity Model Features | `emf` | 3.0.1 | TODO |
| Entity Texture Features | `etf` | 7.0.2 | TODO |
| Wavey Capes | `waveycapes` | 1.7.0 | TODO |
| Stellar View | `stellarview` | 0.5.2 | TODO |
| Snow Real Magic | `snowrealmagic` | 12.1.2 | TODO |
| Ambient Sounds | `ambientsounds` | 6.2.2 | TODO |
| Better Days | `betterdays` | 3.3.6.1 | TODO |
| Serene Seasons | `sereneseasons` | 10.1.0.3 | TODO |
| Better Grassify | `bettergrassify` | 1.7.0 | TODO |

### Transport & Vehicles
| Mod Name | ModID | Version | Status |
|----------|-------|---------|--------|
| Small Ships | `smallships` | 2.0.0-b2.1 | TODO |
| Astikor Carts Redux | `astikorcartsredux` | 1.2.2 | TODO |
| Siege Machines | `siegemachines` | 1.33 | TODO |
| Paraglider | `paraglider` | 21.1.3 | TODO |

### Character & Cosmetics
| Mod Name | ModID | Version | Status |
|----------|-------|---------|--------|
| Armor of the Ages | `armoroftheages` | 1.5.7 | TODO |
| Fantasy Armor | `fantasy_armor` | 1.1.1 | TODO |
| Vesture | `vesture` | 1.4.1 | TODO |
| Self Expression | `selfexpression` | 2.22a | TODO |
| Lucky's Wardrobe | `luckyswardrobe` | 2.0.0 | TODO |
| Skin Shifter | `skinshifter` | 1.3 | TODO |
| Armourer's Workshop | `armourersworkshop` | 3.2.7-beta | TODO |
| Female Gender Mod | `femalegendermod` | 3.2.2 | TODO |

### Miscellaneous
| Mod Name | ModID | Version | Status |
|----------|-------|---------|--------|
| Iron Chests | `ironchest` | 16.0.7 | TODO |
| Resource Backpacks | `resource_backpacks` | 1.3.0 | TODO |
| Numismatic Overhaul | `numismaticoverhaul` | 2.0.1 | TODO |
| Shoppy | `shoppy` | 2.1.1 | TODO |
| Playing Cards | `playingcards` | 2.0.1 | TODO |
| Charta | `charta` | 1.1.0 | TODO |
| Genshin Instrument | `genshinstrument` | 5.1 | TODO |
| Even More Instruments | `evenmoreinstruments` | 6.1.4 | TODO |

---

## Integration Status Summary

| Status | Count | Percentage |
|--------|-------|------------|
| **DONE** | 10 | ~4.4% |
| **PARTIAL** | 1 | ~0.4% |
| **TODO** | 217+ | ~95.6% |

---

## Existing Integrations (from code)

### Already Implemented
1. **Better Combat** (`bettercombat`) - `BetterCombatIntegration.java`
   - Attack name detection
   - Extended reach
   - Combo state/count

2. **Pehkui** (`pehkui`) - `PehkuiIntegration.java`
   - Visual scale
   - Hitbox scale

3. **Distant Horizons** (`distanthorizons`) - `DistantHorizonsIntegration.java`
   - Dynamic dimension registration
   - LOD skip for instances

4. **GeckoLib** (`geckolib`) - `GeckoLibCompat.java` (partial)
   - Animation compatibility

5. **Cloth Config** (`cloth-config`) - `ClothConfigCompat.java`
   - Config screen builder API
   - Category and option creation
   - Save callbacks

6. **Curios** (`curios`) - `CuriosCompat.java`
   - Equipment slot detection
   - Curio item enumeration
   - Ring/necklace convenience methods

7. **Iron's Spellbooks** (`irons_spellbooks`) - `IronsSpellbooksCompat.java`
   - Mana tracking (current/max)
   - Casting state detection
   - Magic status display

8. **Spark** (`spark`) - `SparkCompat.java`
   - TPS monitoring (10s/1m/5m/15m windows)
   - MSPT tracking
   - Health status checks

---

## Next Steps

1. Create standardized `CompatModule` interface
2. Process P1 mods (UI/Input) first
3. Process P2 mods (Dimension) for Arena system
4. Process P3 mods (Combat) for damage/stats
5. Generate individual `docs/compat/<modid>.md` files

---

## Notes

- All mods detected from `run/mods/` folder (runtime environment)
- Build dependencies: DuckDB, Distant Horizons (compileOnly)
- Minecraft version: 1.21.1
- NeoForge version: 21.1.215
