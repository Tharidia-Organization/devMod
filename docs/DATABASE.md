# Database (DuckDB)

> Ultimo aggiornamento: 2026-01-15

DevMod usa DuckDB embedded per telemetry, analytics e componenti di audit. La creazione delle tabelle avviene in due punti:

- **Schema runtime principale**: `src/main/java/com/devmod/telemetry/duckdb/DuckDBSchemaManager.java`
- **Schema arena observability**: `src/main/resources/db/duckdb_schema.sql` (focus su arena template)

## Domini principali (schema runtime)

- **System**: `migrations`, `performance_samples`
- **Combat**: `combat_hits`, `combat_deaths`, `combat_heals`, `combat_spawns`, `combat_fights`, `combat_aggregates`
- **Endurance**: `endurance_sessions`, `endurance_waves`, `endurance_wave_kills`, `endurance_combos`, `endurance_perks`, `endurance_mutators`, `endurance_rewards`, `endurance_performance`, `endurance_parties`, `endurance_bosses`
- **Player**: `player_snapshots`, `player_attribute_changes`, `player_abilities`
- **Progression**: `progression_blocks`, `progression_xp`, `progression_advancements`, `progression_dimensions`, `progression_trades`, `progression_fishing`
- **Economy**: `economy_mob_kills`, `economy_mob_drops`, `economy_item_pickups`, `economy_item_usage`
- **Spatial**: `spatial_heatmaps`, `spatial_alerts`, `spatial_room_transitions`, `arena_spatial_events`, `heatmap_aggregates`
- **Arena Template Observability**: `arena_template_builds`, `arena_template_usage`, `arena_template_errors`, `arena_template_alerts`
- **Dungeon**: `dungeon_runs`
- **Ability**: `ability_aggregates`

## Nota

Per dettagli di colonne e tipi usare come fonte canonica `src/main/java/com/devmod/telemetry/duckdb/DuckDBSchemaManager.java` e `src/main/resources/db/duckdb_schema.sql`.
