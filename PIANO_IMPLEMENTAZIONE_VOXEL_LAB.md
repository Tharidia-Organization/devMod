# PIANO DI IMPLEMENTAZIONE VOXEL-LAB
## Sistema di Telemetria e Analisi per MMORPG Dungeon Design

**Progetto:** devMod - Mob Config Viewer
**Target:** Sistema completo di telemetria per Level Design e Encounter Balance
**Data Creazione:** 2025-12-02
**Versione Piano:** 1.0

---

## EXECUTIVE SUMMARY

### Stato Attuale del Progetto

**Build Status:** ✅ Compilazione Riuscita
**Runtime Status:** ❌ Mod Non Funzionante
**Crash Reports:** 6 totali (2 loading failures, 4 screen rendering)

### Sistema Telemetria Esistente

Il progetto dispone già di un sistema di telemetria **parzialmente implementato** con le seguenti caratteristiche:

#### ✅ Funzionalità Già Implementate (Base Solida)

1. **TelemetryService.java** (957 righe) - Servizio centralizzato che già raccoglie:
   - ✅ Hit tracking (danni, miss, body parts, distanze)
   - ✅ Death logging con TTK (Time To Kill)
   - ✅ Spawn tracking con fallimenti
   - ✅ Room tracking per player
   - ✅ Fight sessions con burst damage
   - ✅ Weapon aggregates (danno, hit, kill)
   - ✅ Boss phase transitions
   - ✅ Skill cast/whiff detection
   - ✅ Healing tracking
   - ✅ Stuck detection (M1 parziale)
   - ✅ Camping detection (M11 parziale)
   - ✅ Aggro drop detection (M2 parziale)
   - ✅ Boss reset/leash fail (M3 parziale)
   - ✅ Kiting path detection (M4 parziale)
   - ✅ Out-of-bounds vertical tracking (M12 parziale)
   - ✅ Performance monitoring (TPS, MSPT)

2. **Output Format:** NDJSON (Newline-Delimited JSON) su file separati:
   - `hits.ndjson` - Tutti i colpi
   - `deaths.ndjson` - Morti
   - `spawns.ndjson` - Spawn entità
   - `alerts.ndjson` - Anomalie (stuck, camping, aggro drop, reset, kiting)
   - `phases.ndjson` - Transizioni fasi boss
   - `fights.ndjson` - Fight sessions complete
   - `heals.ndjson` - Cure
   - `room_time.ndjson` - Tempo player per stanza
   - `minions.ndjson` - Stats minion
   - `projectiles.ndjson` - Proiettili
   - `performance.ndjson` - TPS/MSPT ogni 1s

3. **Room System:**
   - ✅ RoomDefinition con min/max BlockPos
   - ✅ Config file JSON caricabile (`telemetry_rooms.json`)
   - ✅ Fallback automatico a chunk-based room ID

4. **Configurazione:**
   - ✅ TelemetrySettings (soglie configurabili)
   - ✅ Reload command (`/telemetry reload`)

#### ❌ Gap da Colmare

**GRUPPO 1 - Comportamento Mob/Boss:**
- M5: Tempo transizione fasi (parzialmente implementato, da espandere)
- M6: Efficacia minion (dati base presenti, serve analisi)
- M7: Skill whiff (implementato, serve verifica funzionamento)
- M8: Spawn falliti (implementato base, serve dettaglio)

**GRUPPO 2 - Geometria e Navigazione:**
- M9: Heatmap morti ✅ (dati presenti, serve visualizzazione)
- M10: Heatmap movimento (MANCANTE - necessita sampling posizione)
- M11: Safe spot/camping ✅ (parziale, serve heatmap)
- M13: Collisioni invisibili (MANCANTE)
- M14: Punti caduta parkour (MANCANTE)
- M15: Trigger falliti (MANCANTE)
- M16: Direzione sguardo ingresso (MANCANTE)
- M17: Tempo idle/disorientamento (MANCANTE)
- M18: Lunghezza linee di vista (MANCANTE)
- M19: Uso percorsi verticali (MANCANTE)
- M20: Distribuzione altezze giocate (MANCANTE)
- M21: Densità traffico colli bottiglia (MANCANTE)
- M22: Frequenza salti e blocchi arrampicabili (MANCANTE)
- M23: Superfici calpestate vs mai usate (MANCANTE - serve heatmap blocchi)
- M24: Tipologia blocchi pavimento (MANCANTE)
- M25: Contrasto illuminazione (MANCANTE)
- M26: Landmark osservati (MANCANTE)
- M27: Light level & spawnability map (MANCANTE - CRITICO)
- M28: Superfici spawn-valid/blocking (MANCANTE)
- M29: Eventi confini chunk/region (MANCANTE)

**GRUPPO 3 - Bilanciamento Combattimento:**
- M30-M32: Hit/damage/weapon stats ✅ (già implementato)
- M33: Danno in per stanza ✅ (già in aggregati)
- M34: TTK ✅ (già implementato)
- M35: Durata fight ✅ (già implementato)
- M36: Distanza ingaggio ✅ (già tracciata in hits)
- M37: Stato vitali fine fight ✅ (già in fight sessions)
- M38-M39: Skill usage/efficacia ✅ (parziale)
- M40: Cure per stanza ✅ (già implementato)

**GRUPPO 4 - Flusso ed Economia:**
- M41: Esito run dungeon (MANCANTE - serve tracking session completa)
- M42: Choke point quit (MANCANTE)
- M43: Sequenza stanze (parzialmente in room_time)
- M44: Interazione loot (MANCANTE)
- M45: Uso loot stanze successive (MANCANTE)
- M46: Puzzle solving (MANCANTE)
- M47: Backtracking (MANCANTE)
- M48: Uso checkpoint/respawn (MANCANTE)
- M49: Scoperta shortcut/secret (MANCANTE)
- M50: Zone mai attraversate (MANCANTE - serve heatmap)

**GRUPPO 5 - Performance:**
- M51: Performance per stanza ✅ (già implementato TPS/MSPT globale)
- M52: Entity count per stanza (MANCANTE)
- M53: Light updates (MANCANTE)
- M54: Densità entità decorative (MANCANTE)
- M55: Latenza player (MANCANTE)
- M56: Block update rate (MANCANTE)
- M57: FPS budget stimato (MANCANTE)
- M58: FPS client (MANCANTE)

**TOOL VISUALIZZAZIONE (REQ-A1 a A10):**
- Tutti i 10 tool di visualizzazione in-editor sono MANCANTI
- Necessitano rendering client-side (gizmos, overlay, debug shapes)

---

## FASE 0: FIX BLOCCHI CRITICI [PRIORITÀ MASSIMA]

**Durata Stimata:** 1 giorno
**Blocco:** Il mod non si carica, impossibile testare telemetria

### Task 0.1: Fix PlayerTickEvent Registration

**File:** `TelemetryEvents.java:48`

**Problema:**
```
Cannot register listeners for abstract class net.neoforged.neoforge.event.tick.PlayerTickEvent
```

**Causa:** Il sistema EventBus di NeoForge sta rilevando la classe astratta invece della sottoclasse concreta.

**Soluzione:**
Il codice già usa `PlayerTickEvent.Pre` correttamente alla riga 48:
```java
public static void onPlayerTick(PlayerTickEvent.Pre event) {
```

**Ipotesi:**
- Il problema potrebbe essere causato dal fatto che l'annotation `@SubscribeEvent` non sta discriminando correttamente il tipo
- Potrebbe essere un problema di ordine di registrazione

**Azioni:**
1. Verificare versione NeoForge e compatibilità eventi
2. Provare a rimuovere temporaneamente l'evento PlayerTick per verificare se il mod si carica
3. Verificare se altri mod usano pattern simili
4. Possibile workaround: creare classe separata per PlayerTickEvent

**File da modificare:**
- `src/main/java/com/frenkvs/devmod/telemetry/TelemetryEvents.java`

**Test di verifica:**
```bash
./gradlew build
./gradlew runClient
# Verificare assenza errore "Cannot register listeners"
```

---

### Task 0.2: Fix MobConfigScreen NPE

**File:** `MobConfigScreen.java:154`

**Problema:**
```
NullPointerException: Cannot read field "level" because "this.minecraft" is null
at net.minecraft.client.gui.screens.Screen.renderBackground(Screen.java:378)
```

**Causa:** Il campo `minecraft` è null quando viene chiamato `super.render()` o `renderBackground()`.

**Soluzione:**
1. Verificare inizializzazione corretta nel costruttore
2. Aggiungere null-check prima di chiamare metodi che usano `minecraft`
3. Verificare che `init()` sia stato chiamato prima del render

**Azioni:**
```java
@Override
public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    if (this.minecraft == null) {
        return; // Safety check
    }
    super.render(graphics, mouseX, mouseY, partialTick);
    // resto del codice...
}
```

**File da modificare:**
- `src/main/java/com/frenkvs/devmod/MobConfigScreen.java`

**Test di verifica:**
- Aprire mod config screen con tasto K
- Verificare assenza crash

---

### Task 0.3: Implementare Persistence per MobConfigManager

**File:** `MobConfigManager.java`

**Problema:** Configurazioni salvate solo in memoria, perse al riavvio.

**Soluzione:**
Implementare salvataggio/caricamento JSON simile a TelemetryConfig:

```java
public void save() {
    Path file = FMLPaths.CONFIGDIR.get().resolve("devmod/mob_configs.json");
    // Serializza globalConfigs su file JSON
}

public void load() {
    Path file = FMLPaths.CONFIGDIR.get().resolve("devmod/mob_configs.json");
    // Deserializza da JSON
}
```

**Trigger salvataggio:**
- Al cambio config via GUI
- Ogni N secondi (auto-save)
- Al server shutdown

**File da modificare:**
- `src/main/java/com/frenkvs/devmod/MobConfigManager.java`

---

## FASE 1: CONSOLIDAMENTO ARCHITETTURA TELEMETRIA [1-2 settimane]

**Obiettivo:** Refactoring e ottimizzazione del sistema esistente prima di aggiungere nuove metriche.

### Task 1.1: Documentare Sistema Esistente

**Deliverable:** File `TELEMETRY_ARCHITECTURE.md`

**Contenuto:**
- Diagramma flusso dati (eventi → service → file)
- Mapping metriche VOXEL-LAB già implementate vs mancanti
- Schema NDJSON per ogni file output
- API pubblica di TelemetryService
- Esempi query dati per analisi

---

### Task 1.2: Ottimizzare Performance TelemetryService

**Problema attuale:** TelemetryService accumula dati in memoria (HashMap) senza limiti.

**Rischi:**
- Memory leak su sessioni lunghe
- Lag per aggregazioni costose

**Soluzioni:**
1. **LRU Cache** per tracker temporanei (mobPosTrackers, playerCamping, etc.)
2. **Batch write** invece di appendLine sincrono per ogni evento
3. **Async file I/O** tramite ExecutorService
4. **Sampling** configurabile (es. posizione player ogni 2s invece che ogni tick)

**File da modificare:**
- `TelemetryService.java`

**Aggiunte config:**
```json
{
  "asyncWrite": true,
  "batchSize": 100,
  "batchFlushMs": 5000,
  "maxTrackers": 1000,
  "positionSampleIntervalTicks": 40
}
```

---

### Task 1.3: Creare Sistema di Query e Export

**Obiettivo:** Permettere analisi dati senza tools esterni.

**Funzionalità:**
1. **Command in-game:**
   ```
   /telemetry export <metrica> <room> <timestamp_from> <timestamp_to>
   /telemetry heatmap deaths <room>
   /telemetry report fights <room>
   ```

2. **Output aggregati:**
   - CSV per import in Excel/Google Sheets
   - JSON summary per dashboard web

**Implementazione:**
- Nuovo package: `com.frenkvs.devmod.telemetry.export`
- Classes:
  - `TelemetryExporter` - Parser NDJSON
  - `AggregateCalculator` - Medie, percentili, distribuzione
  - `HeatmapGenerator` - Grid 3D con density values

---

### Task 1.4: Implementare Rotazione File di Log

**Problema:** File NDJSON crescono indefinitamente.

**Soluzione:**
- Rotazione giornaliera o per dimensione (es. >100 MB)
- Formato: `hits_2025-12-02.ndjson`, `hits_2025-12-03.ndjson`
- Compressione automatica file vecchi (gzip)
- Pulizia file più vecchi di N giorni

**Config:**
```json
{
  "rotationStrategy": "daily", // o "size"
  "maxFileSizeMB": 100,
  "retentionDays": 30,
  "compressOldFiles": true
}
```

---

## FASE 2: IMPLEMENTAZIONE METRICHE GRUPPO 1 (Comportamento Mob/Boss) [1 settimana]

### Task 2.1: M1 - Punti di Stuck [HEATMAP] ✅

**Status:** Parzialmente implementato in `checkStuck()`, serve solo heatmap.

**Integrazioni necessarie:**
- Salvare coordinate stuck in struttura aggregata per heatmap
- Export su file `stuck_heatmap.ndjson` con formato:
  ```json
  {"room": "boss_arena_1", "x": 100, "y": 64, "z": 200, "count": 15}
  ```

---

### Task 2.2: M2 - Perdita Aggro Anomala [HEATMAP] ✅

**Status:** Implementato in `tickAggro()`, serve heatmap.

**Azioni:**
- Aggregare posizioni aggro drop per room
- Visualizzazione in-world (vedi FASE 4)

---

### Task 2.3: M3 - Reset Boss (Leash Fail) ✅

**Status:** Implementato in `tickAggro()` con euristica distanza spawn.

**Miglioramenti:**
- Parametrizzare soglia distanza per tipo mob (config)
- Distinguere reset intenzionali (player lontano) vs bug geometria

---

### Task 2.4: M4 - Kiting Path e Rotazione Boss [HEATMAP] ✅

**Status:** Implementato in `PathTracker`, serve heatmap traiettoria.

**Azioni:**
- Salvare campioni posizione boss durante fight
- Export traiettoria come polyline per visualizzazione
- Calcolare "spin rate" (rotazioni/minuto)

---

### Task 2.5: M5 - Tempo Transizione Fasi Boss ✅

**Status:** Implementato in `logBossPhaseStart/End`.

**Verifica:**
- Testare con boss MythicMobs multi-fase
- Aggiungere metriche: HP threshold per fase, durata fase, numero morti player per fase

---

### Task 2.6: M6 - Efficacia dei Minion ✅

**Status:** Dati base in `minionDamage` e file `minions.ndjson`.

**Analisi da aggiungere:**
- Numero minion vivi contemporaneamente (peak concurrent)
- Grafico temporale spawn/death minion durante boss fight
- Correlazione morte minion → aumento difficoltà

**File da estendere:**
- `TelemetryService.logSpawn()` - Marcare minion vs boss
- Nuovo tracker: `MinionWaveTracker`

---

### Task 2.7: M7 - Skill Whiff del Boss ✅

**Status:** Implementato in `SkillTracker`.

**Verifica:**
- Integrare con MythicMobs skill casting
- Aggiungere hook per rilevare "nessun player in AoE"

---

### Task 2.8: M8 - Spawn Falliti o Anomali ✅

**Status:** Base implementata in `logSpawn()`.

**Estensioni:**
- Rilevare spawn in block non-navigabile (lava, void, aria)
- Spawn fuori bounds room
- Screenshot automatico posizione spawn fallito (debug)

---

## FASE 3: IMPLEMENTAZIONE METRICHE GRUPPO 2 (Geometria e Navigazione) [2-3 settimane]

**Focus:** Questo è il gruppo più CRITICO per Level Design.

### Task 3.1: M10 - Heatmap Movimento (Desire Lines)

**Implementazione:**
Nuovo evento: `PlayerTickEvent` (già abbiamo l'hook!)

```java
// In TelemetryEvents.onPlayerTick()
if (tickCounter % 40 == 0) { // Sample ogni 2 secondi
    Vec3 pos = player.position();
    BlockPos blockPos = player.blockPosition();
    TelemetryService.INSTANCE.recordMovement(player, blockPos);
}
```

**Storage:**
- File: `movement_heatmap.ndjson`
- Formato: `{"player": "...", "x": 100, "y": 64, "z": 200, "ts": "..."}`

**Aggregazione:**
- Grid 3D: `Map<BlockPos, Integer>` conteggio visite
- Export per visualizzazione density map

**Visualizzazione (FASE 4):**
- Overlay in-world con particelle colorate (rosso = alta densità, blu = bassa)

---

### Task 3.2: M13 - Collisioni Invisibili

**Implementazione:**
Hook: `PlayerTickEvent` - Rilevare velocità 0 senza blocco solido visibile

```java
if (player.getDeltaMovement().lengthSqr() < 0.01 && player.input.hasForwardImpulse()) {
    BlockPos pos = player.blockPosition();
    BlockState state = level.getBlockState(pos);
    if (state.isAir() || !state.isSolid()) {
        // Probabile barriera invisibile
        TelemetryService.INSTANCE.logInvisibleCollision(player, pos);
    }
}
```

**Output:**
- File: `alerts.ndjson` con type `invisible_collision`

---

### Task 3.3: M14 - Punti di Caduta Parkour [HEATMAP]

**Implementazione:**
Hook: `LivingFallEvent` + tracking posizione pre-caduta

```java
@SubscribeEvent
public static void onPlayerFall(LivingFallEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) {
        if (event.getDistance() > 3.0) { // Caduta significativa
            TelemetryService.INSTANCE.logParkourFall(player, event.getDistance());
        }
    }
}
```

**Analisi:**
- Clustering cadute per identificare "jump difficile"
- Correlazione altezza caduta → successo/fallimento

---

### Task 3.4: M15 - Trigger Falliti

**Implementazione:**
Richiede integrazione con sistema trigger/region:

```java
// Quando player attraversa region senza trigger
public void checkTriggerProximity(ServerPlayer player, Region region) {
    double dist = region.distance(player.position());
    if (dist < 2.0 && !region.wasTriggered()) {
        TelemetryService.INSTANCE.logTriggerFail(player, region.id(), dist);
    }
}
```

**Nota:** Dipende da sistema region management (WorldGuard, MythicMobs, custom).

---

### Task 3.5: M16 - Direzione Sguardo all'Ingresso

**Implementazione:**
Hook: `PlayerEvent.PlayerChangedDimensionEvent` o custom event ingresso stanza

```java
// Tracciare yaw/pitch primi 3 secondi in room
public void onRoomEnter(ServerPlayer player, String roomId) {
    long now = System.currentTimeMillis();
    GazeTracker tracker = new GazeTracker(now, player.getYRot(), player.getXRot());
    // Campiona per 3 secondi
}
```

**Analisi:**
- Direzione media iniziale vs landmark desiderato
- "Confusion time" = tempo prima di guardare verso obiettivo

---

### Task 3.6: M17 - Tempo Idle (Disorientamento)

**Implementazione:**
```java
// In PlayerTickEvent
if (player.getDeltaMovement().lengthSqr() < 0.01 && !inCombat(player)) {
    idleTrackers.computeIfAbsent(player.getUUID(), k -> new IdleTracker(now));
} else {
    IdleTracker tracker = idleTrackers.remove(player.getUUID());
    if (tracker != null && tracker.duration() > 5000) { // >5s idle
        TelemetryService.INSTANCE.logIdle(player, tracker.duration());
    }
}
```

---

### Task 3.7: M18 - Lunghezza Linee di Vista (LoS)

**Implementazione:**
Raycast periodico da player verso mob/landmark

```java
public void sampleLoS(ServerPlayer player) {
    Vec3 eye = player.getEyePosition();
    List<LivingEntity> nearbyMobs = level.getEntitiesOfClass(LivingEntity.class,
        player.getBoundingBox().inflate(50));

    for (LivingEntity mob : nearbyMobs) {
        HitResult result = level.clip(new ClipContext(...));
        if (result.getType() == HitResult.Type.BLOCK) {
            double losDistance = eye.distanceTo(result.getLocation());
            TelemetryService.INSTANCE.recordLoS(player, mob, losDistance, false);
        } else {
            TelemetryService.INSTANCE.recordLoS(player, mob,
                eye.distanceTo(mob.position()), true);
        }
    }
}
```

**Metriche:**
- LoS media/max per room
- Percentuale mob in LoS vs occlusi

---

### Task 3.8: M19-M20 - Uso Percorsi Verticali e Distribuzione Altezze

**Implementazione:**
Bucket posizioni player per fascia Y:

```java
// Aggregatore continuo
public void trackVerticalUsage(ServerPlayer player) {
    double y = player.getY();
    String room = resolveRoom(player);
    RoomDefinition def = getRoom(room);

    double minY = def.min().getY();
    double maxY = def.max().getY();
    double normalizedY = (y - minY) / (maxY - minY); // 0.0-1.0

    String bucket;
    if (normalizedY < 0.33) bucket = "floor";
    else if (normalizedY < 0.66) bucket = "mid";
    else bucket = "high";

    verticalUsage.get(room).merge(bucket, 1, Integer::sum);
}
```

**Report:**
```json
{
  "room": "arena_boss_1",
  "floor": 8500,  // ticks trascorsi
  "mid": 1200,
  "high": 300
}
```

---

### Task 3.9: M21 - Densità Traffico in Collo di Bottiglia

**Implementazione:**
Marcare zone "bottleneck" in room config:

```json
{
  "id": "corridor_1",
  "min": [100, 64, 200],
  "max": [105, 67, 220],
  "tags": ["bottleneck"]
}
```

Tracciare player simultanei:

```java
public void tickBottlenecks(ServerLevel level) {
    for (Region bottleneck : bottlenecks) {
        List<ServerPlayer> inside = getPlayersInRegion(bottleneck);
        if (inside.size() > 1) {
            TelemetryService.INSTANCE.logBottleneckCongestion(
                bottleneck.id(), inside.size()
            );
        }
    }
}
```

---

### Task 3.10: M22 - Frequenza Salti e Blocchi Arrampicabili

**Implementazione:**
Hook: `LivingEvent.LivingJumpEvent`

```java
@SubscribeEvent
public static void onJump(LivingEvent.LivingJumpEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) {
        BlockPos below = player.blockPosition().below();
        BlockState state = player.level().getBlockState(below);

        jumpTracker.merge(player.getUUID(), 1, Integer::sum);

        // Rileva "decoration climbing"
        if (isDecoration(state)) {
            TelemetryService.INSTANCE.logDecorationClimb(player, state);
        }
    }
}
```

**Metriche:**
- Salti/metro percorso
- Top 10 blocchi "scalati" non intenzionalmente

---

### Task 3.11: M23 - Superfici Calpestate vs Mai Usate [HEATMAP]

**Implementazione:**
Unione M10 (movimento) + analisi room footprint:

```java
// Alla fine sessione/run
public Map<BlockPos, Integer> getUnusedFloorBlocks(String room) {
    Set<BlockPos> allFloorBlocks = getRoomFloorBlocks(room);
    Set<BlockPos> visited = movementHeatmap.get(room).keySet();

    Set<BlockPos> unused = Sets.difference(allFloorBlocks, visited);
    return unused;
}
```

**Output:**
- File: `unused_areas.ndjson`
- Visualizzazione: overlay rosso su blocchi mai calpestati

---

### Task 3.12: M24 - Tipologia Blocchi Pavimento sul Path

**Implementazione:**
Durante sampling movimento (M10):

```java
BlockState floorBlock = level.getBlockState(player.blockPosition().below());
String blockType = BuiltInRegistries.BLOCK.getKey(floorBlock.getBlock()).toString();

pathBlockTypes.computeIfAbsent(room, k -> new HashMap<>())
    .merge(blockType, 1, Integer::sum);
```

**Analisi:**
- "Primary path" = blocchi con >50% traffico
- Verifica coerenza linguaggio visivo (es. stone_brick = path principale)

---

### Task 3.13: M25 - Contrasto Illuminazione Path vs Laterali

**Implementazione:**
Sampling light level durante movimento:

```java
int lightLevel = level.getBrightness(LightLayer.BLOCK, player.blockPosition());
boolean onMainPath = isMainPath(player.blockPosition(), room);

if (onMainPath) {
    pathLightLevels.add(lightLevel);
} else {
    sideAreaLightLevels.add(lightLevel);
}
```

**Metriche:**
- Media light level path vs side areas
- Contrasto (differenza media)
- Identificare zone troppo scure/chiare

---

### Task 3.14: M26 - Landmark Osservati

**Implementazione:**
Raycast direzione sguardo player:

```java
Vec3 lookVec = player.getLookAngle();
HitResult hit = level.clip(new ClipContext(eye, eye.add(lookVec.scale(50)), ...));

if (hit.getType() == HitResult.Type.BLOCK) {
    BlockPos hitPos = ((BlockHitResult) hit).getBlockPos();

    // Verifica se è landmark registrato
    Landmark landmark = getLandmark(hitPos, room);
    if (landmark != null) {
        landmarkGazeTracker.merge(landmark.id(), 1, Integer::sum);
    }
}
```

**Config landmark:**
```json
{
  "room": "arena_1",
  "landmarks": [
    {"id": "altar", "pos": [100, 65, 200], "radius": 5},
    {"id": "boss_throne", "pos": [150, 70, 220], "radius": 8}
  ]
}
```

---

### Task 3.15: M27 - Light Level & Spawnability Map [HEATMAP] ⚠️ CRITICO

**Importanza:** Questa metrica è FONDAMENTALE per Level Designer!

**Implementazione:**
Scan completo room al load/cambio:

```java
public SpawnabilityMap generateSpawnabilityMap(String roomId) {
    RoomDefinition room = getRoom(roomId);
    Map<BlockPos, SpawnData> map = new HashMap<>();

    for (int x = room.min().getX(); x <= room.max().getX(); x++) {
        for (int y = room.min().getY(); y <= room.max().getY(); y++) {
            for (int z = room.min().getZ(); z <= room.max().getZ(); z++) {
                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = level.getBlockState(pos);

                if (!state.isAir() && state.isFaceSturdy(...)) { // Superficie solida
                    int lightLevel = level.getBrightness(LightLayer.BLOCK, pos.above());
                    boolean canSpawn = NaturalSpawner.isValidEmptySpawnBlock(...);

                    map.put(pos, new SpawnData(lightLevel, canSpawn));
                }
            }
        }
    }

    return new SpawnabilityMap(roomId, map);
}
```

**Export:**
- File: `spawnability_<room>.json`
- Formato: Grid 2D (top-down) con color coding light level

**Visualizzazione (REQ-A Tool):**
- Overlay in-editor: verde = spawn-safe, rosso = spawn-valid, grigio = no-spawn

---

### Task 3.16: M28 - Superfici Spawn-valid / Spawn-blocking

**Implementazione:**
Classificazione blocchi:

```java
public enum SpawnSurfaceType {
    SPAWN_VALID,        // Dirt, stone, etc.
    SPAWN_BLOCKED,      // Slab, carpet, glass, leaves
    SPAWN_CUSTOM_ONLY   // Spawner può, vanilla no
}

public SpawnSurfaceType classifySurface(BlockState state) {
    Block block = state.getBlock();

    if (block instanceof SlabBlock || block instanceof CarpetBlock) {
        return SpawnSurfaceType.SPAWN_BLOCKED;
    }
    if (block.defaultBlockState().isFaceSturdy(...)) {
        return SpawnSurfaceType.SPAWN_VALID;
    }
    return SpawnSurfaceType.SPAWN_BLOCKED;
}
```

**Uso:**
- Guida per builder: quali blocchi usare per "no-spawn zones"
- Heatmap: sovrapporre a M27 per evidenziare superfici "trappola"

---

### Task 3.17: M29 - Eventi su Confini Chunk/Region [HEATMAP]

**Implementazione:**
Flag eventi entro 4 blocchi da confine chunk:

```java
public boolean isNearChunkBoundary(BlockPos pos) {
    int chunkX = pos.getX() & 15;  // 0-15
    int chunkZ = pos.getZ() & 15;

    return chunkX < 4 || chunkX > 11 || chunkZ < 4 || chunkZ > 11;
}

// In logHit, logStuck, etc.
if (isNearChunkBoundary(pos)) {
    chunkBoundaryEvents.merge(pos, 1, Integer::sum);
}
```

**Analisi:**
- Correlazione problemi (stuck, reset, trigger fail) con confini chunk
- Identificare se builder deve evitare design cross-chunk

---

## FASE 4: IMPLEMENTAZIONE TOOL VISUALIZZAZIONE (REQ-A1 a A10) [2-3 settimane]

**Tecnologia:** Client-side rendering con `RenderLevelStageEvent` e debug shapes.

### Task 4.1: Sistema Base Rendering Debug

**Nuovo package:** `com.frenkvs.devmod.rendering`

**Classi:**
- `DebugRenderer` - Manager rendering overlay
- `DebugShape` - Interfaccia per forme (box, sphere, line, etc.)
- `DebugShapeBox`, `DebugShapeSphere`, `DebugShapeLine` - Implementazioni
- `DebugRenderMode` enum - Wireframe, Solid, Transparent

**Hook:**
```java
@EventBusSubscriber(modid = MODID, bus = Bus.MOD, value = Dist.CLIENT)
public class RenderEvents {
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            DebugRenderer.INSTANCE.render(event.getPoseStack(), event.getCamera());
        }
    }
}
```

---

### Task 4.2: REQ-A1 - Anteprima Hitbox "Ghost"

**Implementazione:**
```java
// Quando player guarda un mob con tool attivo
public void renderGhostHitbox(Mob mob, PoseStack poseStack) {
    AABB box = mob.getBoundingBox();
    DebugRenderer.INSTANCE.addShape(new DebugShapeBox(
        box,
        new Color(255, 0, 0, 50), // Rosso trasparente
        DebugRenderMode.WIREFRAME
    ));

    // Annotazioni dimensioni
    addLabel(poseStack, box.getCenter(),
        String.format("W: %.2f H: %.2f D: %.2f",
            box.getXsize(), box.getYsize(), box.getZsize()));
}
```

**Toggle:**
- Keybind: `G` (Ghost) o menu contestuale

---

### Task 4.3: REQ-A2 - Visualizzatore Raggio Aggro e Leash

**Implementazione:**
```java
public void renderAggroRanges(Mob mob) {
    Vec3 pos = mob.position();

    // Aggro range
    double aggroRange = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
    DebugRenderer.INSTANCE.addShape(new DebugShapeSphere(
        pos, aggroRange,
        new Color(255, 255, 0, 30), // Giallo trasparente
        DebugRenderMode.TRANSPARENT
    ));

    // Leash range (da config o attributo custom)
    double leashRange = getLeashRange(mob);
    DebugRenderer.INSTANCE.addShape(new DebugShapeSphere(
        pos, leashRange,
        new Color(255, 0, 0, 20), // Rosso trasparente
        DebugRenderMode.TRANSPARENT
    ));

    // Linea spawn → posizione corrente
    SpawnInfo spawn = getSpawnInfo(mob);
    if (spawn != null) {
        DebugRenderer.INSTANCE.addShape(new DebugShapeLine(
            spawn.pos, pos,
            new Color(255, 255, 255, 100),
            2.0f // thickness
        ));
    }
}
```

---

### Task 4.4: REQ-A3 - Overlay Skill AoE (Telegraphing)

**Implementazione:**
Richiede integrazione con sistema skill (MythicMobs o custom):

```java
public void renderSkillAoE(BossEntity boss, Skill skill) {
    Vec3 targetPos = boss.getTarget().position();

    switch (skill.getAoEType()) {
        case CIRCLE:
            renderCircleAoE(targetPos, skill.getRadius(), skill.getColor());
            break;
        case CONE:
            renderConeAoE(boss.position(), boss.getLookAngle(),
                skill.getAngle(), skill.getRange(), skill.getColor());
            break;
        case LINE:
            renderLineAoE(boss.position(), targetPos,
                skill.getWidth(), skill.getColor());
            break;
    }
}

private void renderCircleAoE(Vec3 center, double radius, Color color) {
    // Decal su pavimento
    for (int angle = 0; angle < 360; angle += 5) {
        double rad = Math.toRadians(angle);
        Vec3 point = center.add(
            Math.cos(rad) * radius,
            0,
            Math.sin(rad) * radius
        );
        // Render punto
    }
}
```

---

### Task 4.5: REQ-A4 - Debugger NavMesh / Pathfinding

**Implementazione:**
Simulazione pathfinding:

```java
public void debugPath(Vec3 start, Vec3 end) {
    // Usa PathNavigation di Minecraft
    PathNavigation nav = new PathNavigation(null, level);
    Path path = nav.createPath(BlockPos.containing(end), 0);

    if (path == null || !path.canReach()) {
        // Linea rossa diretta = non raggiungibile
        DebugRenderer.addLine(start, end, Color.RED);
        addLabel(end, "UNREACHABLE");
    } else {
        // Disegna path effettivo
        List<Node> nodes = path.nodes;
        for (int i = 0; i < nodes.size() - 1; i++) {
            Vec3 p1 = Vec3.atCenterOf(nodes.get(i).asBlockPos());
            Vec3 p2 = Vec3.atCenterOf(nodes.get(i + 1).asBlockPos());
            DebugRenderer.addLine(p1, p2, Color.GREEN);
        }

        // Info path
        addLabel(end, String.format("Path Length: %d nodes", nodes.size()));
    }
}
```

**Uso:**
- Click destro punto A, click destro punto B con tool attivo
- Mostra path che seguirebbe un mob

---

### Task 4.6: REQ-A5 - Anchor Point Visualizer

**Implementazione:**
```java
public void renderAnchorPoints(BossEntity boss) {
    List<Vec3> anchors = boss.getPhaseAnchors();

    for (int i = 0; i < anchors.size(); i++) {
        Vec3 anchor = anchors.get(i);

        // Marker visivo
        DebugRenderer.addShape(new DebugShapeBox(
            AABB.ofSize(anchor, 1, 1, 1),
            Color.CYAN,
            DebugRenderMode.SOLID
        ));

        // Label fase
        addLabel(anchor.add(0, 2, 0), "Phase " + (i + 1));

        // Verifica spawn-safe
        if (!isSpawnSafe(anchor)) {
            DebugRenderer.addShape(new DebugShapeBox(
                AABB.ofSize(anchor, 1.5, 1.5, 1.5),
                Color.RED,
                DebugRenderMode.WIREFRAME
            ));
            addLabel(anchor.add(0, 3, 0), "⚠ UNSAFE SPAWN");
        }
    }
}
```

---

### Task 4.7: REQ-A6 - Visualizzatore Linee di Vista (LoS)

**Implementazione:**
```java
public void renderLoS(LivingEntity viewer, LivingEntity target) {
    Vec3 eye = viewer.getEyePosition();
    Vec3 targetPos = target.getEyePosition();

    HitResult hit = level.clip(new ClipContext(eye, targetPos, ...));

    if (hit.getType() == HitResult.Type.ENTITY) {
        // LoS libera
        DebugRenderer.addLine(eye, targetPos, Color.GREEN, 2.0f);
        addLabel(targetPos, "✓ LoS Clear");
    } else {
        // LoS bloccata
        Vec3 hitPos = hit.getLocation();
        DebugRenderer.addLine(eye, hitPos, Color.YELLOW, 2.0f);
        DebugRenderer.addLine(hitPos, targetPos, Color.RED, 1.0f);

        addLabel(hitPos, "✗ LoS Blocked");

        // Evidenzia blocco che interrompe LoS
        if (hit instanceof BlockHitResult blockHit) {
            DebugRenderer.addShape(new DebugShapeBox(
                AABB.ofSize(Vec3.atCenterOf(blockHit.getBlockPos()), 1, 1, 1),
                new Color(255, 0, 0, 100),
                DebugRenderMode.TRANSPARENT
            ));
        }
    }
}
```

---

### Task 4.8: REQ-A7 - Visualizzatore Livelli Verticali

**Implementazione:**
```java
public void renderVerticalLevels(RoomDefinition room) {
    double minY = room.min().getY();
    double maxY = room.max().getY();
    double height = maxY - minY;

    // Dividi in 3 fasce
    double floorMax = minY + height * 0.33;
    double midMax = minY + height * 0.66;

    AABB roomBox = AABB.of(room.min(), room.max());

    // Floor zone (verde)
    renderZone(roomBox, minY, floorMax, new Color(0, 255, 0, 20));

    // Mid zone (giallo)
    renderZone(roomBox, floorMax, midMax, new Color(255, 255, 0, 20));

    // High zone (rosso)
    renderZone(roomBox, midMax, maxY, new Color(255, 0, 0, 20));

    // Labels
    addLabel(new Vec3(room.center().getX(), floorMax, room.center().getZ()),
        "FLOOR LEVEL");
    addLabel(new Vec3(room.center().getX(), midMax, room.center().getZ()),
        "MID LEVEL");
}

private void renderZone(AABB bounds, double minY, double maxY, Color color) {
    AABB zone = new AABB(
        bounds.minX, minY, bounds.minZ,
        bounds.maxX, maxY, bounds.maxZ
    );
    DebugRenderer.addShape(new DebugShapeBox(zone, color, DebugRenderMode.TRANSPARENT));
}
```

---

### Task 4.9: REQ-A8 - Traffic / Heatmap Overlay in Editor

**Implementazione:**
Carica dati heatmap da telemetria e renderizza:

```java
public void renderMovementHeatmap(String room) {
    Map<BlockPos, Integer> heatmap = TelemetryExporter.loadMovementHeatmap(room);

    // Normalizza valori 0-1
    int maxVisits = heatmap.values().stream().max(Integer::compareTo).orElse(1);

    for (Map.Entry<BlockPos, Integer> entry : heatmap.entrySet()) {
        BlockPos pos = entry.getKey();
        float density = (float) entry.getValue() / maxVisits;

        // Gradient colore: blu (basso) → rosso (alto)
        Color color = Color.getHSBColor(
            (1.0f - density) * 0.66f, // Hue: blu=0.66, rosso=0
            1.0f,                      // Saturation
            1.0f                       // Brightness
        );

        // Render particella o box semi-trasparente
        DebugRenderer.addShape(new DebugShapeBox(
            AABB.ofSize(Vec3.atCenterOf(pos), 1, 0.1, 1), // Flat decal
            new Color(color.getRed(), color.getGreen(), color.getBlue(), 100),
            DebugRenderMode.TRANSPARENT
        ));
    }
}
```

**Toggle layers:**
- Movimento player
- Morti
- Stuck points
- Camping spots

---

### Task 4.10: REQ-A9 - Trigger & Region Visualizer

**Implementazione:**
```java
public void renderRegions(List<Region> regions) {
    for (Region region : regions) {
        Color color = getColorForRegionType(region.type());

        DebugRenderer.addShape(new DebugShapeBox(
            region.bounds(),
            color,
            DebugRenderMode.WIREFRAME
        ));

        // Label con nome e evento
        addLabel(region.center(),
            String.format("%s\n[%s]", region.name(), region.event()));
    }
}

private Color getColorForRegionType(String type) {
    return switch (type) {
        case "spawn" -> Color.GREEN;
        case "trigger" -> Color.YELLOW;
        case "boss_arena" -> Color.RED;
        case "checkpoint" -> Color.CYAN;
        default -> Color.WHITE;
    };
}
```

---

### Task 4.11: REQ-A10 - Coverage & Safe-Spot Visualizer

**Implementazione:**
```java
public void renderSafeSpots(String room) {
    List<SafeSpot> spots = TelemetryExporter.loadCampingSpots(room);

    for (SafeSpot spot : spots) {
        // Marker rosso lampeggiante
        DebugRenderer.addShape(new DebugShapeBox(
            AABB.ofSize(spot.position(), 1, 2, 1),
            new Color(255, 0, 0, (int)(Math.sin(System.currentTimeMillis() / 200.0) * 50 + 100)),
            DebugRenderMode.TRANSPARENT
        ));

        // Info exploit
        addLabel(spot.position().add(0, 2.5, 0),
            String.format("⚠ SAFE SPOT\n%d hits\n%.1fs",
                spot.hitCount(), spot.duration() / 1000.0));
    }
}
```

---

## FASE 5: IMPLEMENTAZIONE GRUPPI 3, 4, 5 [1-2 settimane]

**Gruppo 3 (Combattimento):** Già ~80% completo, serve solo integrazione UI.

**Gruppo 4 (Flusso/Economia):**
- Richiede tracking session completa dungeon (run start/end)
- Implementare DungeonRunTracker
- Loot interaction hooks (da integrare con sistema inventory)

**Gruppo 5 (Performance):**
- Entity counting per chunk/room
- Block update profiling (hook Forge events)
- Client FPS reporting (richiede packet custom)

---

## FASE 6: DASHBOARD E ANALYTICS [1 settimana]

**Obiettivo:** Web dashboard per analisi dati senza tools esterni.

### Componenti

1. **Backend - API REST**
   - Embedded web server (Javalin o similar)
   - Endpoint: `/api/metrics/<room>/<type>`
   - Real-time WebSocket per dati live

2. **Frontend - Dashboard Web**
   - Framework: React o Vue.js
   - Charts: Chart.js o D3.js
   - Heatmap 3D: Three.js

3. **Features**
   - Grafici temporali (TPS, danni, morti)
   - Heatmap 2D/3D interattiva
   - Filtri per room, time range, player
   - Export CSV/PNG

**Esposizione:**
- Solo localhost per sicurezza
- Avvio via comando `/telemetry dashboard start`
- URL: `http://localhost:8080/telemetry`

---

## FASE 7: TESTING E OTTIMIZZAZIONE [1 settimana]

### Test Cases

1. **Stress Test Performance**
   - 10 player contemporanei in dungeon
   - 50+ mob attivi
   - Verificare MSPT < 50ms

2. **Memory Leak Test**
   - Sessione 4 ore
   - Verificare heap stabile

3. **File Size Management**
   - 1000 fight sessions
   - Verificare rotazione file funzionante

4. **Heatmap Accuracy**
   - Path noto → verificare heatmap matching
   - Safe spot deliberato → verificare rilevamento

5. **Tool Visualizzazione**
   - Rendering 10+ overlay simultanei
   - Verificare FPS > 30

---

## FASE 8: DOCUMENTAZIONE [3 giorni]

### Deliverables

1. **User Guide** (`VOXEL_LAB_USER_GUIDE.md`)
   - Installazione
   - Configurazione room definitions
   - Uso comandi
   - Interpretazione metriche

2. **Level Designer Manual** (`LD_MANUAL.md`)
   - Workflow completo analisi dungeon
   - Checklist metriche per room type (boss, corridor, puzzle)
   - Case study: ottimizzazione boss fight reale

3. **API Documentation** (`API.md`)
   - TelemetryService public methods
   - Hook personalizzati per integration
   - Schema NDJSON completo

4. **Video Tutorial**
   - 10 minuti: "Analizzare un Dungeon con VOXEL-LAB"
   - Screencast workflow completo

---

## TIMELINE COMPLESSIVO

| Fase | Durata | Dipendenze |
|------|--------|------------|
| **0. Fix Critici** | 1 giorno | - |
| **1. Consolidamento** | 1-2 settimane | Fase 0 |
| **2. Gruppo 1 (Mob/Boss)** | 1 settimana | Fase 1 |
| **3. Gruppo 2 (Geometria)** | 2-3 settimane | Fase 1 |
| **4. Tool Visualizzazione** | 2-3 settimane | Fase 2, 3 |
| **5. Gruppi 3, 4, 5** | 1-2 settimane | Fase 1 |
| **6. Dashboard** | 1 settimana | Fase 2, 3, 5 |
| **7. Testing** | 1 settimana | Tutte |
| **8. Documentazione** | 3 giorni | Tutte |
| **TOTALE** | **8-12 settimane** | |

---

## PRIORITÀ DI IMPLEMENTAZIONE (se risorse limitate)

### TIER 1 - MUST HAVE (Sistema Funzionante Minimo)

1. ✅ Fix Fase 0 (critici bloccanti)
2. ✅ Gruppo 2: M10 (movimento), M27 (spawnability), M9 (morti)
3. ✅ REQ-A2 (aggro/leash), REQ-A4 (pathfinding), REQ-A8 (heatmap overlay)
4. ✅ Export CSV base

### TIER 2 - SHOULD HAVE (Sistema Completo)

5. Gruppo 1 completo (mob/boss behavior)
6. Gruppo 2 completo (geometria)
7. Gruppo 3 completo (combattimento)
8. Tool visualizzazione completi (REQ-A1 a A10)

### TIER 3 - NICE TO HAVE (Polish)

9. Dashboard web
10. Gruppo 4 (economia/flow)
11. Gruppo 5 (performance avanzata)
12. Export avanzati (PDF report, video replay)

---

## RISCHI E MITIGAZIONI

### Rischio 1: Performance Impact

**Problema:** Sistema telemetria troppo pesante → lag in-game

**Mitigazione:**
- Sampling aggressivo (non ogni tick)
- Async I/O obbligatorio
- Profiling continuo (MSPT target < 5ms per telemetry)
- Flag `telemetry.enabled=false` per produzione

### Rischio 2: Complessità Integrazione Tool Visualizzazione

**Problema:** Rendering client-side complesso → bug grafici, crash

**Mitigazione:**
- Prototipo rendering base PRIMA di implementare tutte le metriche
- Fallback graceful se rendering fallisce
- Toggle per disabilitare singoli overlay

### Rischio 3: Storage Dati Eccessivo

**Problema:** File NDJSON giganti (>10 GB)

**Mitigazione:**
- Rotazione file obbligatoria
- Compressione automatica
- Aggregazione pre-calcolo per query frequenti
- Purge automatico dati >30 giorni

### Rischio 4: Scope Creep

**Problema:** Requisiti continuano ad espandersi

**Mitigazione:**
- Bloccare scope dopo Fase 3
- Nuove richieste → backlog Fase 9 (futuro)
- Prioritizzare TIER 1 sempre

---

## METRICHE DI SUCCESSO PROGETTO

**Il sistema VOXEL-LAB è considerato completo quando:**

1. ✅ Mod si carica senza errori
2. ✅ Almeno 40/58 metriche implementate e funzionanti
3. ✅ Almeno 6/10 tool visualizzazione funzionanti
4. ✅ Export CSV/JSON per tutte le metriche implementate
5. ✅ Heatmap movimento e morti visualizzabili in-world
6. ✅ Documentazione completa pubblicata
7. ✅ Zero memory leak in sessione 4 ore
8. ✅ Overhead performance < 5ms MSPT medio
9. ✅ Almeno 1 case study reale analisi dungeon documentato

---

## NOTE FINALI

Questo piano è **ambizioso ma realizzabile** dato che:

1. **Base solida già presente:** ~30% funzionalità già implementate in TelemetryService
2. **Architettura chiara:** NDJSON + service pattern scalabile
3. **Modularità:** Ogni metrica è indipendente, implementabile incrementalmente
4. **Prioritizzazione:** TIER system permette rilascio early alpha funzionante

**Prossimo Step Immediato:**
→ Eseguire **Fase 0** per sbloccare testing sistema esistente.

---

**Autore Piano:** Claude Code (Anthropic)
**Data:** 2025-12-02
**Versione:** 1.0
**Status:** DRAFT - In attesa approvazione stakeholder
