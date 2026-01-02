# Radial Navigation Map (DevMod)

> Last updated: 2025-12-26
> Status: PLANNING (vision map; not guaranteed to match implementation)

Obiettivo: Radial come gateway unico. Max 6-8 categorie root, con sub-wheel contestuali, breadcrumb e search.

## Root Wheel (6–8 categorie)

1) **Tools** (CRAFTING_TABLE)
   - Settings (COMPARATOR): Keybinds + Quick Help + Onboarding + HUD Config (history/DPS/position/offset/presets) + Effects (Impact VFX + intensity, screen shake, trails, badge popups) + Telemetry Config + Combat Config
   - Welcome / Tutorial Start / Skip (BOOK / WRITABLE_BOOK / BARRIER)
   - Item Editor (ANVIL)
   - Mob Config (SPAWNER)
   - Mob Equipment (IRON_HELMET)
   - Room Bounds Editor (STRUCTURE_BLOCK)
   - Keybinds (BOOK)

2) **Combat** (IRON_SWORD)
   - Abilities: Dash, Dodge (FEATHER / SHIELD)
   - Impact HUD 2D/3D (NETHERITE_SWORD / ITEM_FRAME)
   - Boss Phase HUD (WITHER_SKELETON_SKULL)
   - Skill Efficacy (NETHER_STAR)
   - Stamina Editor (HONEY_BOTTLE)

3) **Endurance** (TOTEM_OF_UNDYING)
   - Start Quest (COMPASS)
   - Quest HUD (MAP)
   - Continue / Respawn (GOLDEN_APPLE)
   - Exit / Give Up (BARRIER, confirm)
   - Perks (ENCHANTED_BOOK)
   - Shop (EMERALD)
   - Checkpoints / Results (CLOCK / NETHER_STAR)

4) **Arena** (COMMAND_BLOCK)
   - Create Arena (STRUCTURE_BLOCK)
   - Templates: List / Info / Reload / Validate / Metrics (PAPER / BOOK / REDSTONE)
   - Force Template: Set / Clear / List / Status (NETHER_STAR)
   - Autosmoke: Run / Status / Schedule (FIREWORK_ROCKET / REDSTONE_TORCH)
   - Debug HUD (REDSTONE_LAMP)
   - Quick Test Wizard (TARGET)

5) **Telemetry** (MAP)
   - Dashboard (SPYGLASS)
   - Exports: Heatmaps / PNG / CSV / JSON / All (PAPER)
   - Heatmap Exports: Death / Movement / Camping / Stuck / Aggro Drop / Kiting / Choke Points / Parkour Falls
   - Damage Stats Export (WRITABLE_BOOK)
   - Scan: Light / Spawnability (TORCH / ZOMBIE_HEAD)
   - Dungeon Debug: Start / End / Status (NETHERITE_SWORD)
   - Desire Lines / Backtracking (STRING / BOOTS)

6) **Debug** (REDSTONE)
   - Debug Overlay / Body Parts (ARMOR_STAND)
   - Light Levels (TORCH)
   - Heatmaps (FILLED_MAP, Toggle/Cycle/Clear + per-type toggles incl. Light Spawnable/Dark)
   - Pathfinding / LOS / Aggro Range (COMPASS / SPYGLASS / TARGET)
   - Native Debug (DEBUG_STICK)
   - Room Bounds (Editor + Set A/B + Save + Delete) / Vertical / Safe Spots / Spawnability (STRUCTURE_BLOCK / LADDER / SHIELD / SPAWNER)
   - Perf: FPS / Profiler / Chunk Perf / Attribute Monitor (CLOCK / REDSTONE / OBSERVER)
   - VFX: Screen Shake Test (AMETHYST_SHARD)
   - HUD: Quick Help / Impact Dismiss (BOOK / BARRIER)

7) **Testing** (TARGET)
   - Testing Hub (LECTERN)
   - QA Testing (NOTE_BLOCK)
   - QA Actions: Start/Resume, Pass/Fail/Skip/Auto, Save/Copy Report
   - VoxelLab / UI Tests (PINK_CONCRETE / MAGENTA_DYE)
   - Impact HUD tools (GLASS_PANE)
   - Debug tools (STRUCTURE_VOID)

8) **Party** (NAME_TAG)
   - Party Screen (BELL)
   - Invites (PLAYER_HEAD)
   - Ready/Start (CLOCK)

## Sub-wheel structure (esempi)

- Debug → Visualizers
  - Debug Overlay (toggle)
  - Body Part Boxes (toggle)
  - Line of Sight (toggle)
  - Pathfinding (toggle)

- Telemetry → Export
  - Export Heatmaps / PNG / CSV / JSON / All

- Arena → Templates
  - List / Info / Reload / Validate / Metrics

- Endurance → In-Quest
  - Continue / Exit / Perks / Shop / Records

## Contextual rules

- **In Quest**: Endurance submenu becomes primary in root; show Continue/Exit/Perks; hide Create Arena.
- **In Instance**: Arena actions show Force/Status, hide Create if InstanceOnly.
- **Admin/Op**: show Telemetry export, Arena autosmoke, Debug toggles.
- **Combat**: reduce destructive actions; require confirm for Exit / Clear / Reload.
- **Creative/Test mode**: unlock Debug + Testing categories.

## Search & Breadcrumb

- Type-to-filter shows cross-category results; highlight path.
- Breadcrumb: `Root > Category > Submenu`.
- Hold action = details/confirm/remap; Click = invoke.

## Keybind integration

- Keybinds map 1:1 to actions already in registry.
- Radial detail view shows keybind and allows remap hint.

## Icon policy

- **Only vanilla item/block textures**, no custom assets.
- Icon picks documented above per category/action.

## Refactor plan (PR-sized steps)

1) Add ActionRegistry core + ActionIds + ActionContext + ActionKeybindRegistry.
2) Register actions for commands/telemetry/debug/test harness and wire command invokers.
3) Migrate keybind handlers to ActionRegistry and expose keybind hints in radial.
4) Rebuild radial categories with vanilla icons only + add testing submenus.
5) Add orphan check + smoke tests, verify actions registered.

## Acceptance checklist (manual)

1) Open radial keybind always shows the radial menu.
2) From radial: start/continue/exit Endurance, manage Arena templates, run autosmoke, open dashboards.
3) No critical command-only features: each command has a radial action entry.
4) Every keybind action appears in radial details with key hint.
5) Icons are vanilla item/block textures only.
6) Destructive actions require confirm (where applicable).

## Automatic smoke tests

- Action registry covers all ActionIds.
- Each keybind has a mapped action in ActionKeybindRegistry.
- Invoking safe actions does not crash.
