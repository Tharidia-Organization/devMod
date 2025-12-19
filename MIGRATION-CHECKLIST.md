# MIGRATION CHECKLIST: NDJSON → DuckDB

**Autore**: Senior Engineer Audit
**Data**: 2025-12-12
**Revisione**: 3.0 (P0/P1/P2-A COMPLETE)

---

## Executive Summary

### Stato Reale della Migrazione (AGGIORNATO 2025-12-12)

| Categoria | Tabelle Schema | Call-site Integrati | Gate Status | Note |
|-----------|----------------|---------------------|-------------|------|
| Combat | 5 | **5/5** ✅ | P0 PASS | Tutti integrati |
| Endurance | 9 | **9/9** ✅ | P0 PASS | Tutti integrati |
| Player | 3 | **3/3** ✅ | P0 PASS | Tutti integrati |
| Progression | 6 | **6/6** ✅ | P1 PASS | `p1_progression_gate.sh` |
| Economy | 4 | **4/4** ✅ | P1 PASS | `p1_economy_gate.sh` |
| Spatial | 3 | **3/3** ✅ | P1/P2-A PASS | `p1_spatial_gate.sh`, `p2_heatmaps_gate.sh` |
| System | 1 | **1/1** ✅ | P0 PASS | `performance_samples` |
| **TOTALE** | **31 data** | **31/31** ✅ | **ALL PASS** | |

**Stato Attuale:**
- **Schema**: 32 tabelle (31 data + 1 migrations meta)
- **Call-site integrati**: 31/31 data tables ✅
- **Gate verification**: P0, P1, P2-A ALL PASS
- **NDJSON_FALLBACK**: `false` (DuckDB PRIMARY)

---

## Gate Verification Status

| Gate | Script | Tables | Status | Evidence |
|------|--------|--------|--------|----------|
| P0 | Manual verification | Combat, Endurance, Player, System | ✅ PASS | Runtime tests |
| P1 Economy | `p1_economy_gate.sh` | economy_mob_kills, economy_mob_drops, economy_item_pickups, economy_item_usage | ✅ PASS | 25, 8, 85, 1 rows |
| P1 Progression | `p1_progression_gate.sh` | progression_blocks, progression_xp, progression_advancements, progression_dimensions, progression_trades, progression_fishing | ✅ PASS | 36, 7, 8, 1, 8, 1 rows |
| P1 Spatial | `p1_spatial_gate.sh` | spatial_alerts, spatial_room_transitions | ✅ PASS | 2107, 36 rows + noise invariants |
| P2-A Heatmaps | `p2_heatmaps_gate.sh` | spatial_heatmaps | ✅ PASS | 39 rows, 4 types |

---

## Mappatura Dettagliata (AGGIORNATA)

### COMBAT (5/5 call-site integrati) ✅

| Tabella | Metodo DuckDB | Call-site | Status |
|---------|---------------|-----------|--------|
| `combat_hits` | `logHit()` | `TelemetryService` | ✅ |
| `combat_deaths` | `logDeath()` | `TelemetryService` | ✅ |
| `combat_heals` | `logHeal()` | `TelemetryService` | ✅ |
| `combat_spawns` | `logSpawn()` | `TelemetryService` | ✅ |
| `combat_fights` | `logFight()` | `FightSessionService` | ✅ (P0) |

### ENDURANCE (9/9 call-site integrati) ✅

| Tabella | Metodo DuckDB | Call-site | Status |
|---------|---------------|-----------|--------|
| `endurance_sessions` | `logSession()` | `EnduranceTelemetryService` | ✅ |
| `endurance_waves` | `logWaveStart/Complete()` | `EnduranceTelemetryService` | ✅ |
| `endurance_wave_kills` | `logWaveKill()` | `EnduranceTelemetryService` | ✅ |
| `endurance_combos` | `logComboEvent()` | `EnduranceTelemetryService` | ✅ |
| `endurance_perks` | `logPerkSelected()` | `EnduranceTelemetryService` | ✅ |
| `endurance_mutators` | `logMutator()` | `EnduranceTelemetryService` | ✅ |
| `endurance_rewards` | `logCurrencyEarned()` | `EnduranceTelemetryService` | ✅ |
| `endurance_parties` | `logPartyEvent()` | `EnduranceTelemetryService` | ✅ |
| `endurance_bosses` | `logBossEvent()` | `EnduranceTelemetryService` | ✅ |

### PLAYER (3/3 call-site integrati) ✅

| Tabella | Metodo DuckDB | Call-site | Status |
|---------|---------------|-----------|--------|
| `player_snapshots` | `logPlayerSnapshot()` | `PlayerAttributeTelemetryService` | ✅ |
| `player_attribute_changes` | `logAttributeChange()` | `PlayerAttributeTelemetryService` | ✅ (P0) |
| `player_abilities` | `logAbility()` | `AbilityTelemetryService` | ✅ |

### PROGRESSION (6/6 call-site integrati) ✅ P1

| Tabella | Metodo DuckDB | Call-site | Status |
|---------|---------------|-----------|--------|
| `progression_blocks` | `logBlockBreak/Place()` | `ProgressionTrackingEvents` | ✅ |
| `progression_xp` | `logXpGain()` | `ProgressionTrackingEvents` | ✅ |
| `progression_advancements` | `logAdvancement()` | `ProgressionTrackingEvents` | ✅ |
| `progression_dimensions` | `logDimensionChange()` | `ProgressionTrackingEvents` | ✅ |
| `progression_trades` | `logTrade()` | `ProgressionTrackingEvents` | ✅ |
| `progression_fishing` | `logFishing()` | `ProgressionTrackingEvents` | ✅ |

### ECONOMY (4/4 call-site integrati) ✅ P1

| Tabella | Metodo DuckDB | Call-site | Status |
|---------|---------------|-----------|--------|
| `economy_mob_kills` | `logMobKill()` | `LootTrackingEvents` | ✅ |
| `economy_mob_drops` | `logMobDrop()` | `LootTrackingEvents` | ✅ |
| `economy_item_pickups` | `logItemPickup()` | `LootTrackingEvents` | ✅ |
| `economy_item_usage` | `logItemUsage()` | `LootTrackingEvents` | ✅ |

### SPATIAL (3/3 call-site integrati) ✅ P1/P2-A

| Tabella | Metodo DuckDB | Call-site | Status |
|---------|---------------|-----------|--------|
| `spatial_alerts` | `logAlert()` | `TelemetryService` (11 types) | ✅ P1 |
| `spatial_room_transitions` | `logRoomTransition()` | `TelemetryService` | ✅ P1 |
| `spatial_heatmaps` | `logHeatmap()` | `HeatmapService` (aggregated flush) | ✅ P2-A |

### SYSTEM (1/1 integrato) ✅

| Tabella | Metodo DuckDB | Call-site | Status |
|---------|---------------|-----------|--------|
| `performance_samples` | `logPerformance()` | `TelemetryService` | ✅ |

---

## Remaining Work (P2-B+)

| Table | Status | Notes |
|-------|--------|-------|
| `dungeon_runs` | P2-B PENDING | Schema decision needed |
| Gamification | Future | NDJSON only (low priority) |
| Projectiles | Future | NDJSON only |
| Skills | Future | In-memory only |

---

## Changelog

| Data | Versione | Note |
|------|----------|------|
| 2025-12-12 | 1.0 | Audit iniziale |
| 2025-12-12 | 2.0 | Correzioni post-review |
| 2025-12-12 | 3.0 | **P0/P1/P2-A COMPLETE** - All 31 data tables migrated + gate verified |
