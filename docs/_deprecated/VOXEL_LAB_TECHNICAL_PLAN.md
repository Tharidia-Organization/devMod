# VOXEL-LAB Piano Tecnico Dettagliato
## Sistema di Telemetria e Visualizzazione per Level Design

**Data:** 2025-12-07
**Versione:** 2.0 - Analisi Approfondita

---

## 1. ANALISI ARCHITETTURA ESISTENTE

### 1.1 Struttura Servizi Telemetria

L'architettura è già ben strutturata con separazione di responsabilità:

```
telemetry/
├── TelemetryService.java          # Facade principale (delega ai sub-services)
├── TelemetryEvents.java           # Event handlers NeoForge
├── AsyncTelemetryWriter.java      # I/O async su executor dedicato
│
├── combat/
│   └── FightSessionService.java   # Fight tracking, TTK, burst damage
│
├── boss/
│   └── BossPhaseService.java      # Boss phase transitions
│
├── damage/
│   └── DamageTrackingService.java # Damage aggregates per weapon/room
│
├── dungeon/
│   └── DungeonSessionService.java # Session tracking, backtracking, outcomes
│
├── entity/
│   ├── EntityTrackingService.java # Stuck, aggro, camping detection
│   └── MinionService.java         # Minion wave tracking
│
├── player/
│   └── PlayerTrackingService.java # Room transitions, OOB, movement sampling
│
├── room/
│   ├── RoomService.java           # Room definitions, resolution
│   └── RoomAnalysisService.java   # Room-level analytics
│
├── skills/
│   └── SkillTrackingService.java  # Skill cast/whiff detection
│
└── spatial/
    ├── HeatmapService.java        # Heatmap data collection
    ├── LightAnalysisService.java  # Light level scanning, spawnability
    └── SpatialMetricsService.java # Choke points, entity density, collisions
```

### 1.2 Stato Implementazione per Metrica

| ID | Metrica | Backend | Visualizer | Integrato |
|----|---------|---------|------------|-----------|
| M1 | Stuck Detection | ✅ EntityTrackingService | ✅ HeatmapVisualizer.STUCK | ✅ |
| M2 | Aggro Drop | ✅ EntityTrackingService | ✅ HeatmapVisualizer.AGGRO_DROP | ✅ |
| M3 | Boss Reset/Leash | ✅ BossPhaseService | ❌ | ⚠️ |
| M4 | Kiting Path | ✅ EntityTrackingService | ✅ HeatmapVisualizer.KITING | ✅ |
| M5 | Phase Transitions | ✅ BossPhaseService | ❌ | ⚠️ |
| M6 | Minion Efficacy | ✅ MinionService | ❌ | ⚠️ |
| M7 | Skill Whiff | ✅ SkillTrackingService | ❌ | ⚠️ |
| M8 | Spawn Failures | ⚠️ Partial | ❌ | ❌ |
| M9 | Death Heatmap | ✅ HeatmapService | ✅ HeatmapVisualizer.DEATH | ✅ |
| M10 | Movement Heatmap | ✅ PlayerTrackingService+HeatmapService | ✅ HeatmapVisualizer.MOVEMENT | ✅ |
| M11 | Camping/SafeSpot | ✅ EntityTrackingService | ✅ HeatmapVisualizer.CAMPING | ✅ |
| M12 | OOB Vertical | ✅ PlayerTrackingService | ❌ | ⚠️ |
| M13 | Invisible Collision | ✅ SpatialMetricsService | ❌ | ⚠️ Trigger mancante |
| M14 | Parkour Falls | ✅ SpatialMetricsService | ❌ | ⚠️ Trigger mancante |
| M27 | Spawnability Map | ✅ LightAnalysisService | ✅ HeatmapVisualizer.LIGHT_SPAWNABLE | ⚠️ Non connesso |
| M41 | Dungeon Outcome | ✅ DungeonSessionService | ❌ | ⚠️ UI mancante |
| M42 | Choke Point Quits | ✅ SpatialMetricsService | ❌ | ⚠️ |
| M47 | Backtracking | ✅ PlayerTrackingService + DungeonSessionService | ❌ | ⚠️ |
| M52 | Entity Density | ✅ SpatialMetricsService | ❌ | ⚠️ |

### 1.3 Gap Critici Identificati

**Categoria A: Backend esiste ma non triggherato**
1. `SpatialMetricsService.recordInvisibleCollision()` - mai chiamato
2. `SpatialMetricsService.recordParkourFall()` - mai chiamato
3. `LightAnalysisService.scanRoomLightLevels()` - mai chiamato automaticamente
4. `DungeonSessionService.startSession()` - mai chiamato (manca trigger start dungeon)

**Categoria B: Backend esiste ma manca visualizzazione**
1. BossPhaseService → nessun overlay per fasi
2. MinionService → nessuna UI per stats minion
3. SkillTrackingService → nessun display skill efficacy
4. EntityDensity → nessun contatore in-game

**Categoria C: Completamente mancanti**
1. M17: Idle/confusion time tracking
2. M26: Landmark observation tracking
3. M48: Checkpoint/respawn usage
4. M49: Shortcut/secret discovery
5. M58: Client FPS tracking

---

## 2. PIANO DI IMPLEMENTAZIONE DETTAGLIATO

### 2.1 SPRINT 1: Trigger Mancanti (2-3 ore)

**Obiettivo:** Attivare il backend già implementato ma non connesso.

#### Task 1.1: Trigger Invisible Collision
**File:** `TelemetryEvents.java`

```java
// Aggiungere listener per collision con blocchi invisibili/barrier
@SubscribeEvent
public static void onEntityCollision(EntityMobGriefingEvent event) {
    // Detect collision with barrier blocks
    Entity entity = event.getEntity();
    if (entity instanceof ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        BlockState state = player.level().getBlockState(pos);
        if (state.is(Blocks.BARRIER) || state.is(Blocks.STRUCTURE_VOID)) {
            String roomId = RoomService.INSTANCE.resolveRoom(player.serverLevel(), pos);
            SpatialMetricsService.INSTANCE.recordInvisibleCollision(roomId, pos);
        }
    }
}
```

**Alternativa più robusta - Hook in player movement:**
```java
// In PlayerTrackingService.trackPlayerRoom()
// Dopo movement sampling, check per collisioni
if (player.horizontalCollision && !player.verticalCollision) {
    // Player sta spingendo contro qualcosa orizzontalmente
    BlockPos checkPos = player.blockPosition().relative(player.getDirection());
    BlockState state = player.level().getBlockState(checkPos);
    if (state.is(Blocks.BARRIER) || !state.getCollisionShape(level, checkPos).isEmpty()) {
        // Potenziale invisible collision
    }
}
```

#### Task 1.2: Trigger Parkour Fall
**File:** `TelemetryEvents.java`

```java
@SubscribeEvent
public static void onPlayerFall(LivingFallEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer player)) return;
    if (player.level().isClientSide()) return;

    float fallDistance = event.getDistance();
    if (fallDistance >= 3.0f) { // Almeno 3 blocchi di caduta
        BlockPos pos = player.blockPosition();
        String roomId = RoomService.INSTANCE.resolveRoom(player.serverLevel(), pos);
        SpatialMetricsService.INSTANCE.recordParkourFall(roomId, pos, fallDistance);

        // Log to telemetry
        TelemetryService.INSTANCE.appendLine("alerts.ndjson",
            SpatialMetricsService.INSTANCE.getParkourFallRecord(roomId, pos).toJson(player.getName().getString()));
    }
}
```

#### Task 1.3: Auto-scan Light Levels
**File:** `TelemetryEvents.java` o nuovo `ScanCommand.java`

```java
// Opzione A: Comando manuale /telemetry scan-lights
public static void registerScanCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(Commands.literal("telemetry")
        .then(Commands.literal("scan-lights")
            .then(Commands.argument("room", StringArgumentType.string())
                .executes(ctx -> {
                    String roomId = StringArgumentType.getString(ctx, "room");
                    ServerLevel level = ctx.getSource().getLevel();
                    LightAnalysisService.INSTANCE.scanRoomLightLevels(level, roomId,
                        (file, line) -> TelemetryService.INSTANCE.appendLine(file, line));
                    return 1;
                }))));
}

// Opzione B: Scan periodico ogni 5 minuti per stanze attive
// In TelemetryService.tickPerformance() ogni 6000 ticks
```

### 2.2 SPRINT 2: Visualizzatori Mancanti (4-6 ore)

#### Task 2.1: Boss Phase Overlay
**File nuovo:** `rendering/BossPhaseOverlay.java`

```java
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public class BossPhaseOverlay {
    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "boss_phase");

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, LAYER_ID, BossPhaseOverlay::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        // Mostra:
        // - Nome boss attivo
        // - Fase corrente (1/3, 2/3, etc.)
        // - HP threshold per prossima fase
        // - Timer fase corrente
    }
}
```

#### Task 2.2: Entity Density Counter
**File nuovo:** `rendering/EntityDensityOverlay.java`

```java
// HUD piccolo che mostra:
// Room: dungeon_room_3
// Entities: 15 (peak: 23)
// Types: zombie=8, skeleton=5, spider=2

// Posizione: angolo basso sinistro
// Toggle: keybind E
```

#### Task 2.3: Skill Efficacy Panel
**File nuovo:** `rendering/SkillEfficacyOverlay.java`

```java
// Panel che mostra ultime 5 skill usate:
// [Fireball] Hit: 3/5 (60%) Dmg: 45.2
// [Ice Storm] Hit: 8/12 (67%) Dmg: 89.0
// [Teleport] Uses: 2 Cooldown: 5s
```

### 2.3 SPRINT 3: Metriche Mancanti (6-8 ore)

#### Task 3.1: Idle/Confusion Time (M17)
**File:** `telemetry/player/PlayerTrackingService.java`

```java
// Aggiungere in PlayerTrackingService:
private final Map<UUID, IdleTracker> idleTrackers = new ConcurrentHashMap<>();

public static final class IdleTracker {
    Vec3 lastPosition;
    float lastYaw;
    long idleStartMs;
    long totalIdleMs;
    int lookAroundCount; // Rotazioni rapide = confusione

    void update(Vec3 pos, float yaw, long nowMs) {
        double dist = lastPosition.distanceTo(pos);
        float yawDelta = Math.abs(yaw - lastYaw);

        if (dist < 0.1) { // Fermo
            if (idleStartMs == 0) idleStartMs = nowMs;
        } else {
            if (idleStartMs > 0) {
                totalIdleMs += nowMs - idleStartMs;
                idleStartMs = 0;
            }
        }

        if (yawDelta > 90) { // Rotazione rapida
            lookAroundCount++;
        }

        lastPosition = pos;
        lastYaw = yaw;
    }
}
```

#### Task 3.2: Landmark Observation (M26)
**File nuovo:** `telemetry/spatial/LandmarkService.java`

```java
public class LandmarkService {
    public static final LandmarkService INSTANCE = new LandmarkService();

    // Landmarks definiti in config (come rooms)
    private final Map<String, LandmarkDefinition> landmarks = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> playerObservations = new ConcurrentHashMap<>();

    public record LandmarkDefinition(String id, BlockPos position, int observeRadius, String description) {}

    public void checkLandmarkObservation(ServerPlayer player) {
        Vec3 lookVec = player.getLookAngle();
        Vec3 eyePos = player.getEyePosition();

        for (LandmarkDefinition landmark : landmarks.values()) {
            Vec3 toLandmark = Vec3.atCenterOf(landmark.position).subtract(eyePos);
            double distance = toLandmark.length();

            if (distance < landmark.observeRadius) {
                // Check if player is looking at landmark
                double dot = lookVec.normalize().dot(toLandmark.normalize());
                if (dot > 0.8) { // ~36 degree cone
                    recordObservation(player.getUUID(), landmark.id);
                }
            }
        }
    }
}
```

#### Task 3.3: Client FPS Tracking (M58)
**File esistente:** `telemetry/FpsTracker.java` (già esiste, verificare integrazione)

```java
// Client-side, invia al server periodicamente
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public class ClientFpsReporter {
    private static long lastReport = 0;
    private static final long REPORT_INTERVAL_MS = 5000;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        long now = System.currentTimeMillis();
        if (now - lastReport > REPORT_INTERVAL_MS) {
            int fps = Minecraft.getInstance().getFps();
            // Invia al server via custom packet
            NetworkHandler.sendToServer(new FpsReportPayload(fps));
            lastReport = now;
        }
    }
}
```

---

## 3. CONNESSIONI CLIENT-SERVER

### 3.1 Architettura Networking

```
Client                                    Server
  │                                          │
  ├─► FpsReportPayload ──────────────────────┤
  │   (ogni 5s, contiene FPS client)         │
  │                                          │
  │◄── HeatmapSyncPayload ───────────────────┤
  │   (on demand, dati heatmap per room)     │
  │                                          │
  │◄── BossPhasePayload ─────────────────────┤
  │   (on phase change, info fase boss)      │
  │                                          │
  │◄── EntityDensityPayload ─────────────────┤
  │   (ogni 2s, count entità per room)       │
```

### 3.2 Payloads da Implementare

**File:** `network/` (nuova cartella)

```java
// FpsReportPayload.java
public record FpsReportPayload(int fps) implements CustomPacketPayload {
    public static final Type<FpsReportPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("devmod", "fps_report"));
}

// HeatmapSyncPayload.java
public record HeatmapSyncPayload(
    String heatmapType,
    Map<BlockPos, Integer> data
) implements CustomPacketPayload {
    // Serializza mappa compressa
}

// EntityDensityPayload.java
public record EntityDensityPayload(
    String roomId,
    int currentCount,
    int peakCount,
    Map<String, Integer> typeBreakdown
) implements CustomPacketPayload {}
```

---

## 4. UI/UX IMPROVEMENTS

### 4.1 TelemetryPage Migliorata

La pagina telemetry nel settings panel deve mostrare:

```
┌─────────────────────────────────────────────────────────┐
│ Telemetry Dashboard                                      │
├─────────────────────────────────────────────────────────┤
│ Recording: ● ACTIVE          Room: dungeon_boss_1       │
│                                                          │
│ ═══ Current Session ═══                                  │
│ Duration: 00:15:32                                       │
│ Rooms Visited: 5                                         │
│ Backtracks: 2                                            │
│ Deaths: 1                                                │
│ Kills: 23                                                │
│                                                          │
│ ═══ Room Analysis ═══                                    │
│ Entity Count: 8 (peak: 15)                               │
│ Light Analysis: 12% spawnable                            │
│ Safe Spots: 3 detected                                   │
│                                                          │
│ ═══ Export ═══                                           │
│ [Export Session] [Scan Room Lights] [Clear Data]        │
└─────────────────────────────────────────────────────────┘
```

### 4.2 Nuovi Keybinds

| Tasto | Funzione | Priorità |
|-------|----------|----------|
| B | Toggle Boss Phase Overlay | Alta |
| E | Toggle Entity Density | Media |
| S | Scan Room Light Levels | Media |
| T | Toggle Telemetry Recording | Alta |

---

## 5. DIPENDENZE E ORDINE DI IMPLEMENTAZIONE

```
Sprint 1 (Trigger)
    │
    ├── Task 1.1 Invisible Collision ───┐
    ├── Task 1.2 Parkour Fall ──────────┼── Nessuna dipendenza
    └── Task 1.3 Light Scan Command ────┘

Sprint 2 (Visualizers)
    │
    ├── Task 2.1 Boss Phase Overlay ────── Dipende da: BossPhaseService funzionante
    ├── Task 2.2 Entity Density ────────── Dipende da: SpatialMetricsService.updateEntityDensity
    └── Task 2.3 Skill Efficacy ────────── Dipende da: SkillTrackingService

Sprint 3 (Metriche Nuove)
    │
    ├── Task 3.1 Idle Time ─────────────── Modifica PlayerTrackingService
    ├── Task 3.2 Landmark Observation ──── Nuovo service + config format
    └── Task 3.3 Client FPS ────────────── Nuovo payload + handler
```

---

## 6. TESTING CHECKLIST

### 6.1 Test Funzionali

**Sprint 1:**
- [ ] Collision con barrier block genera entry in alerts.ndjson
- [ ] Caduta > 3 blocchi genera entry in alerts.ndjson
- [ ] `/telemetry scan-lights dungeon_room_1` produce output in light_levels.ndjson

**Sprint 2:**
- [ ] Boss phase overlay appare quando targeting boss
- [ ] Entity density mostra count corretto
- [ ] Skill panel si aggiorna dopo ogni cast

**Sprint 3:**
- [ ] Idle time incrementa quando player fermo
- [ ] Landmark observation registra quando guarda punto interesse
- [ ] FPS client arriva al server e viene loggato

### 6.2 Test Performance

- [ ] Con 50+ entità, entity density update < 5ms
- [ ] Light scan di room 100x100x50 < 2 secondi
- [ ] Heatmap render con 1000+ punti mantiene 60 FPS

### 6.3 Test Integrazione

- [ ] Settings persistence include nuovi toggle
- [ ] Keybinds nuovi funzionano e sono configurabili
- [ ] Export session include tutte le nuove metriche

---

## 7. FILE DA CREARE/MODIFICARE

### Nuovi File
```
src/main/java/com/frenkvs/devmod/
├── rendering/
│   ├── BossPhaseOverlay.java
│   ├── EntityDensityOverlay.java
│   └── SkillEfficacyOverlay.java
├── network/
│   ├── FpsReportPayload.java
│   ├── HeatmapSyncPayload.java
│   └── EntityDensityPayload.java
└── telemetry/
    └── spatial/
        └── LandmarkService.java
```

### File da Modificare
```
TelemetryEvents.java          # Aggiungere triggers
PlayerTrackingService.java    # Aggiungere IdleTracker
KeyInputHandler.java          # Nuovi keybinds
RenderEvents.java             # Registrare nuovi overlays
SettingsData.java             # Nuovi toggle
TelemetryPage.java            # Dashboard migliorata
```

---

## 8. STIMA EFFORT

| Sprint | Ore | Rischio | Note |
|--------|-----|---------|------|
| Sprint 1 | 2-3h | Basso | Backend già esiste |
| Sprint 2 | 4-6h | Medio | Rendering può avere edge cases |
| Sprint 3 | 6-8h | Alto | Nuovo networking, nuovi services |
| Testing | 2-3h | Basso | Automatizzabile parzialmente |
| **Totale** | **14-20h** | | |

---

## 9. PROSSIMI PASSI IMMEDIATI

1. **Ora:** Implementare Task 1.2 (Parkour Fall trigger) - 30 min
2. **Poi:** Implementare Task 1.1 (Invisible Collision) - 30 min
3. **Poi:** Implementare Task 1.3 (Light Scan Command) - 1h
4. **Test:** Verificare che i dati appaiono nei file NDJSON
5. **Commit:** "feat: activate spatial metrics triggers"

Vuoi procedere con Sprint 1?
