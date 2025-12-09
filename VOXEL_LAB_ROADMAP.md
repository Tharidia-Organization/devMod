# VOXEL-LAB Project Roadmap
## Sistema di Telemetria e Visualizzazione per Level Design

**Ultimo Aggiornamento:** 2025-12-07
**Stato Progetto:** In Sviluppo Attivo

---

## Executive Summary

VOXEL-LAB è un sistema completo di telemetria e visualizzazione per Minecraft NeoForge 1.21.1, progettato per aiutare Level Designer e Game Designer a bilanciare dungeon e encounter.

### Stato Attuale

| Sistema | Stato | Completamento |
|---------|-------|---------------|
| **Core Telemetry** | ✅ Funzionante | 90% |
| **Persistence/Settings** | ✅ Completo | 100% |
| **Debug Overlays** | ✅ Completo | 100% |
| **Heatmap System** | ✅ Base funzionante | 70% |
| **Body Part HUD** | ✅ Completo | 100% |
| **Mod Integration** | ✅ Completo | 100% |
| **Unified Settings UI** | ✅ Completo | 100% |
| **QA Testing System** | ✅ Completo | 100% |

---

## Architettura Implementata

```
src/main/java/com/frenkvs/devmod/
├── telemetry/                    # Sistema telemetria [85%]
│   ├── TelemetryService.java     # Core service (~1000 righe)
│   ├── TelemetryEvents.java      # Event handlers
│   ├── AsyncTelemetryWriter.java # Async file I/O
│   ├── TelemetryConfig.java      # Configuration
│   ├── RoomDefinition.java       # Room system
│   ├── BossPhaseDetector.java    # Boss tracking
│   ├── FpsTracker.java           # Performance
│   └── MemoryCleanupService.java # Memory management
│
├── rendering/                    # Visualizzazione [100%]
│   ├── DebugRenderer.java        # Main debug overlay
│   ├── HeatmapVisualizer.java    # Heatmaps
│   ├── LightLevelOverlay.java    # Light levels
│   ├── LineOfSightVisualizer.java# LoS debug
│   ├── PathfindingDebugger.java  # Pathfinding
│   ├── BodyPartRenderer.java     # Hitbox visualization
│   ├── RoomBoundsVisualizer.java # Room bounds
│   ├── SafeSpotVisualizer.java   # Safe spots
│   ├── EntityInfoOverlay.java    # Entity info floating labels
│   └── AggroRangeVisualizer.java # Aggro range spheres
│
├── hud/                          # HUD System [100%]
│   ├── ImpactData.java           # Impact data holder
│   ├── DamageBreakdown.java      # Damage formula
│   ├── ImpactHudOverlay.java     # HUD rendering
│   └── Impact3DRenderer.java     # 3D labels
│
├── integration/                  # Mod Integration [100%]
│   ├── ModIntegrationManager.java
│   ├── PehkuiIntegration.java    # Pehkui soft-dep
│   └── BetterCombatIntegration.java
│
└── ui/unified/                   # Settings UI [100%]
    ├── UnifiedSettingsScreen.java
    ├── SettingsPage.java
    ├── pages/                    # 7 settings pages
    │   ├── GeneralSettingsPage.java
    │   ├── DebugOverlaysPage.java
    │   ├── VisualizersPage.java
    │   ├── CombatSettingsPage.java
    │   ├── MobConfigPage.java
    │   ├── TelemetryPage.java
    │   └── KeybindsPage.java
    └── persistence/
        ├── SettingsManager.java  # Load/save JSON
        └── SettingsData.java     # POJO
```

---

## Fasi di Sviluppo

### FASE 0: Stabilità Core [COMPLETATA]

- [x] Fix PlayerTickEvent registration (workaround via ServerTick)
- [x] Fix MobConfigScreen NPE
- [x] Persistence settings (SettingsManager)
- [x] Build stabile

### FASE 1: Sistema UI Unificato [COMPLETATA]

- [x] UnifiedSettingsScreen con sidebar
- [x] 7 pagine di impostazioni
- [x] Search, tooltips, keyboard navigation
- [x] Animazioni transizione
- [x] Persistenza automatica

### FASE 2: Debug Overlays [COMPLETATA]

- [x] DebugRenderer principale
- [x] LightLevelOverlay
- [x] LineOfSightVisualizer
- [x] PathfindingDebugger
- [x] RoomBoundsVisualizer
- [x] BodyPartRenderer
- [x] Keybind toggles (G, L, H, R, P, V, Y, C)
- [x] Entity Info floating labels (`EntityInfoOverlay.java`)
- [x] Aggro range spheres (`AggroRangeVisualizer.java`)

### FASE 3: Heatmap System [70% COMPLETATA]

- [x] HeatmapVisualizer base
- [x] Movement heatmap
- [x] Deaths heatmap
- [x] Stuck points heatmap
- [x] Camping spots heatmap
- [x] Aggro drop heatmap
- [x] Kiting path heatmap
- [ ] Real-time heatmap update
- [ ] Heatmap export to image
- [ ] 3D heatmap layers

### FASE 4: Body Part HUD [COMPLETATA]

- [x] ImpactData thread-safe storage
- [x] DamageBreakdown formula
- [x] ImpactHudOverlay rendering
- [x] Pehkui integration
- [x] Better Combat integration
- [x] Actual damage tracking
- [x] Fade-out animation

### FASE 5: Metriche Avanzate [COMPLETATA]

**Gruppo Geometria (Priorità Alta):**
- [x] M10: Movement desire lines (`DesireLinesService.java`)
- [x] M13: Invisible collision detection (in `TelemetryEvents.java`)
- [x] M14: Parkour fall points (in `TelemetryEvents.java`)
- [x] M17: Idle/confusion time (existing)
- [x] M27: Spawnability map (`SpawnabilityOverlay.java`)

**Gruppo Performance:**
- [x] M52: Entity count per room (`RoomEntityCounter.java`)
- [x] M58: Client FPS tracking (`FpsTracker.java` → `client_fps.ndjson`)

**Gruppo Flow:**
- [x] M41: Dungeon run outcomes (`DungeonRunService.java`)
- [x] M47: Backtracking detection (`BacktrackingService.java`)

### FASE 6: Dashboard & Export [90% COMPLETATA]

- [ ] Web dashboard (embedded server) - future enhancement
- [x] CSV export (`CsvExporter.java`)
- [x] JSON report generation (`JsonReportExporter.java`)
- [x] Heatmap PNG export (`HeatmapExporter.java`)
- [ ] Session replay - future enhancement

---

## Sprint Completati

### Sprint 1: Spawnability Map (M27) ✅
- File: `rendering/SpawnabilityOverlay.java`
- Keybind: F4

### Sprint 2: Entity Count per Room (M52) ✅
- File: `telemetry/room/RoomEntityCounter.java`
- Integrato in TelemetryEvents

### Sprint 3: Heatmap Export ✅
- File: `telemetry/export/HeatmapExporter.java`
- Comando: `/devmod telemetry export png`

### Sprint 4: Metriche Avanzate ✅
- M10: `DesireLinesService.java` - Movement patterns
- M41: `DungeonRunService.java` - Dungeon run tracking
- M47: `BacktrackingService.java` - Confusion detection

### Sprint 5: Export System ✅
- `CsvExporter.java` - CSV data export
- `JsonReportExporter.java` - Comprehensive JSON reports

## Prossimi Step (Opzionali)

### Web Dashboard (Future Enhancement)
- Embedded web server (Javalin)
- Real-time WebSocket streaming
- Interactive heatmap viewer

### Session Replay (Future Enhancement)
- Recording player movement/actions
- Playback with timeline scrubbing

---

## Keybinds Attivi

| Tasto | Funzione | File |
|-------|----------|------|
| K | Apri Unified Settings | KeyInputHandler.java |
| G | Toggle Debug Overlay | RenderEvents.java |
| L | Toggle Light Levels | RenderEvents.java |
| H | Toggle Heatmaps | RenderEvents.java |
| R | Toggle Room Bounds | RenderEvents.java |
| P | Toggle Pathfinding | RenderEvents.java |
| V | Toggle Vertical Levels | RenderEvents.java |
| Y | Toggle Safe Spots | RenderEvents.java |
| C | Cycle Heatmap Type | RenderEvents.java |
| F | Toggle FPS Tracker | RenderEvents.java |
| F4 | Toggle Spawnability Map | RenderEvents.java |

---

## Output Files (Telemetria)

Tutti i file sono in formato NDJSON (newline-delimited JSON):

```
run/telemetry/
├── hits.ndjson          # Tutti i colpi
├── deaths.ndjson        # Morti
├── spawns.ndjson        # Spawn entità
├── alerts.ndjson        # Anomalie (stuck, camping, aggro drop)
├── phases.ndjson        # Transizioni fasi boss
├── fights.ndjson        # Fight sessions complete
├── heals.ndjson         # Cure
├── room_time.ndjson     # Tempo player per stanza
├── minions.ndjson       # Stats minion
├── projectiles.ndjson   # Proiettili
├── performance.ndjson   # TPS/MSPT
├── dungeon_runs.ndjson  # Risultati run dungeon (M41)
├── backtracks.ndjson    # Eventi backtracking (M47)
├── client_fps.ndjson    # Client FPS/performance (M58)
├── exports/             # Heatmap PNG images
├── csv/                 # CSV data exports
└── reports/             # JSON comprehensive reports
```

---

## Config Files

```
config/devmod/
├── settings.json        # Impostazioni UI persistenti
├── mob_configs.json     # Configurazioni mob
├── weapon_stats.json    # Stats armi
└── telemetry_rooms.json # Definizioni stanze
```

---

## Dipendenze

### Required
- NeoForge 1.21.1
- Minecraft 1.21.1

### Soft Dependencies (Opzionali)
- Pehkui - Scaling entità
- Better Combat - Combo system

---

## Testing Checklist

### Build
- [ ] `./gradlew compileJava` passa
- [ ] `./gradlew build` produce JAR
- [ ] `./gradlew runClient` avvia senza crash

### Funzionalità Core
- [ ] Unified Settings si apre con K
- [ ] Toggle overlays funzionano
- [ ] Heatmaps visibili
- [ ] Impact HUD appare al colpo
- [ ] Settings persistono al riavvio

### Performance
- [ ] MSPT < 50ms con telemetria attiva
- [ ] Nessun memory leak in 1h sessione
- [ ] FPS > 30 con overlay attivi

---

## Risorse

### Documentazione
- [NeoForge GUI Docs](https://docs.neoforged.net/docs/1.21.1/gui/screens/)
- [NeoForge Events](https://docs.neoforged.net/docs/concepts/events/)

### Files Chiave
- `KeyInputHandler.java` - Tutti i keybind
- `RenderEvents.java` - Rendering hooks
- `TelemetryService.java` - Core telemetria
- `UnifiedSettingsScreen.java` - UI principale

---

## Note per Sviluppatori

### Aggiungere Nuovo Overlay

1. Creare classe in `rendering/`
2. Implementare `enable()`, `disable()`, `toggle()`, `isEnabled()`
3. Aggiungere hook in `RenderEvents.java`
4. Aggiungere keybind in `KeyInputHandler.java`
5. Aggiungere toggle in `DebugOverlaysPage.java`
6. Aggiungere persistenza in `SettingsData.java`

### Aggiungere Nuova Metrica Telemetria

1. Aggiungere campo in `TelemetryService.java`
2. Creare metodo `log<Metrica>()`
3. Aggiungere file output in costruttore
4. Chiamare da evento appropriato in `TelemetryEvents.java`

---

## Changelog

### v0.3.0 (2025-12-07)
- Completato Unified Settings UI
- Implementata persistenza settings
- Aggiunte animazioni e search
- Keyboard navigation

### v0.2.0 (2025-12-05)
- Body Part HUD System
- Pehkui/Better Combat integration
- Impact analysis panel

### v0.1.0 (2025-12-02)
- Setup iniziale
- Telemetry base
- Debug overlays base
