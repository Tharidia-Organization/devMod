# Radial Menu Map (Curated)

> Ultimo aggiornamento: 2026-02-03

This map reflects the new curated radial layout driven by `RadialMenuActionLayout` allowlist
and menu-path overrides. It is intentionally lean: overlays + top-level screens, while deep
telemetry/ops live in the Telemetry Dashboard screen.

## ANALYZE (Overlays)
- Debug
  - Overlays
    - devmod.debug.overlay.toggle
    - devmod.debug.body_parts.toggle
    - devmod.debug.overlays.enable_all
    - devmod.debug.overlays.disable_all
  - AI
    - devmod.debug.pathfinding.toggle
    - devmod.debug.los.toggle
    - devmod.debug.aggro_range.toggle
  - Light
    - devmod.debug.light_overlay.toggle
  - Native
    - devmod.debug.native.entity_pathing.toggle
    - devmod.debug.native.entity_goals.toggle
    - devmod.debug.native.entity_brains.toggle
    - devmod.debug.native.poi.toggle
    - devmod.debug.native.raids.toggle
    - devmod.debug.native.bees.toggle
    - devmod.debug.native.game_events.toggle
    - devmod.debug.native.structures.toggle
  - Context
    - devmod.debug.combat.reset
    - devmod.debug.context.status
- Spatial
  - devmod.debug.room_bounds.toggle
  - devmod.debug.room_bounds.gaps.toggle
  - devmod.debug.vertical_levels.toggle
  - devmod.debug.safe_spots.toggle
  - devmod.debug.spawnability.toggle
- Performance
  - devmod.debug.fps_tracker.toggle
  - devmod.debug.profiler.toggle
  - devmod.debug.entity_density.toggle
  - devmod.debug.chunk_perf.toggle
  - devmod.debug.attribute_monitor.toggle

## COMBAT
- Diagnostics
  - devmod.debug.boss_phase.toggle
  - devmod.debug.skill_efficacy.toggle
  - devmod.debug.economy.toggle
  - devmod.debug.economy.view_cycle
  - devmod.debug.economy.sort_cycle
- Heatmaps
  - devmod.debug.heatmap.toggle
  - devmod.debug.heatmap.cycle
  - Types
    - devmod.debug.heatmap.death.toggle
    - devmod.debug.heatmap.movement.toggle
    - devmod.debug.heatmap.camping.toggle
    - devmod.debug.heatmap.stuck.toggle
    - devmod.debug.heatmap.aggro_drop.toggle
    - devmod.debug.heatmap.kiting.toggle
    - devmod.debug.heatmap.light_spawnable.toggle
    - devmod.debug.heatmap.light_dark.toggle
  - devmod.debug.heatmap.clear_current
  - devmod.debug.heatmap.clear_all

## PLAY
- HUD
  - Impact HUD
    - devmod.hud.impact.toggle
    - Presets
      - devmod.hud.impact.preset.detailed
      - devmod.hud.impact.preset.minimal
      - devmod.hud.impact.preset.training
    - devmod.hud.impact.display_mode.cycle
    - devmod.hud.impact_3d.toggle
    - devmod.hud.impact.controller.toggle
    - devmod.hud.impact.show_recap
  - Endurance HUD
    - devmod.endurance.hud.toggle
    - devmod.hud.quest.toggle
    - devmod.endurance.hud.details_toggle
  - Party HUD
    - devmod.hud.party.toggle
  - Arena HUD
    - devmod.arena.hud.toggle
  - Help
    - devmod.hud.quick_help.toggle
- Endurance
  - devmod.ui.endurance_screen.open
- Party
  - devmod.ui.party.open

## TELEMETRY
- Dashboard
  - devmod.ui.telemetry_dashboard.open

## TOOLS
- Editors
  - devmod.ui.editor_hub.open
- Labs
  - devmod.ui.testing_hub.open
  - devmod.ui.voxellab.open
- Settings
  - devmod.ui.settings.open
  - devmod.ui.keybinds.open
  - devmod.ui.radial_settings.open

## Telemetry Dashboard Screen (moved from radial)
The radial now exposes only the Telemetry Dashboard entry. The dashboard contains:
- Overlays: overlay toggles
- Ops: server start/stop/status + dungeon ops
- Data: dumps + export (all/PNG/CSV/JSON)
- Scans: light/spawnability/desirelines/backtracking/dungeon stats
- Export: heatmap exports (per type)
- Stats + Visualizers: existing analytics panels

## Editor Hub Screen (moved from radial)
The radial now exposes only the Editor Hub entry. The hub contains:
- Item Editor: auto + weapon/armor/shield/general/recipe/food/fuel/usable
- Mob: config + equipment
- Quest/Endurance: quest editor + endurance editor + stamina editor
- World: room bounds editor

## Radial UX Notes (2026-02-03)
- Release-to-select: debounce 200ms, richiede selezione esplicita; se bloccato mostra feedback e resta aperto.
- Blocked feedback: tooltip con reason + help contestuale; combat mostra countdown residuo.
- Hit target minimi: 44px per item e preferiti (accessibilita).
- Windows DPI: sync UI scale su apertura per evitare offset hover.
- Stato BLOCKED: tint warning su item e preferiti + micro-shake/flash se animazioni attive.
- Telemetria: `action_blocked` include `reasonKey` e `helpKey` (log dev).
