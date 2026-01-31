# Nexus 2.0 - Piano di Implementazione Step-by-Step

> Ultimo aggiornamento: 2026-01-31
> Stato: documento di piano (storico). L'implementazione e' presente nel codice; verificare con `docs/IMPLEMENTATION_STATE.md`.

> **IMPORTANTE**: Ogni step deve essere rivisto e approvato prima dell'implementazione.
> Questo documento serve come guida per l'agent che implementerà il codice.

---

## Overview

**Obiettivo**: Rifare la dimensione Nexus come hub modulare completamente editabile.

**Approccio**: REPLACEMENT totale del vecchio sistema (non duplicazione).

**Scala Target**: 100 chunks (1600x1600 blocchi)

**Principio Chiave**: Sfruttare i 50+ moduli esistenti invece di riscrivere.

---

## Pre-requisiti

Prima di iniziare, verificare:

```bash
# Build attuale funziona
./gradlew compileJava

# Test passano
./gradlew test
```

---

# FASE 1: DATA STRUCTURES

## Step 1.1: Creare `SlotType` enum

**File**: `src/main/java/com/devmod/nexus/data/SlotType.java`

**Scopo**: Definire i tipi di slot disponibili nell'hub.

```java
public enum SlotType {
    FIXED,        // Non modificabile (spawn, centro)
    EDITABLE,     // Editabile via Area Builder
    TEST_AREA,    // Temporaneo, rimovibile
    RESTRICTED    // Solo admin può modificare
}
```

**Domande da risolvere**:
- [x] Servono altri tipi? → No, 4 tipi sufficienti
- [x] Permessi granulari per tipo? → No, gestiti da Area Editor

**Status**: [x] Da rivedere [x] Approvato [x] Implementato

---

## Step 1.2: Creare `SlotPermissions` record

**File**: `src/main/java/com/devmod/nexus/data/SlotPermissions.java`

**Scopo**: Definire chi può fare cosa in uno slot.

```java
public record SlotPermissions(
    boolean canEdit,           // Può modificare contenuto
    boolean canPlace,          // Può piazzare blocchi
    boolean canBreak,          // Può rompere blocchi
    boolean canSpawnMobs,      // Può spawnare mob (Clone module)
    boolean canUsePortals,     // Può usare portali
    int minPermissionLevel     // Livello OP minimo (0-4)
) {
    public static final SlotPermissions DEFAULT = new SlotPermissions(
        false, false, false, false, true, 0
    );

    public static final SlotPermissions ADMIN_ONLY = new SlotPermissions(
        true, true, true, true, true, 2
    );

    public static final SlotPermissions FULL_ACCESS = new SlotPermissions(
        true, true, true, true, true, 0
    );
}
```

**Domande da risolvere**:
- [x] Integrazione con permission system esistente? → Usa minPermissionLevel (0-4)
- [ ] Permessi per team/party? → Da integrare con Party module in futuro

**Status**: [x] Da rivedere [x] Approvato [x] Implementato

---

## Step 1.3: Creare `ZoneSlot` record

**File**: `src/main/java/com/devmod/nexus/data/ZoneSlot.java`

**Scopo**: Rappresentare uno slot nell'hub che può contenere un'area.

```java
public record ZoneSlot(
    String slotId,                    // ID univoco: "spawn", "quest_north", "economia_west"
    String displayName,               // Nome visualizzato: "Spawn", "Quest Hub Nord"
    ZoneBounds bounds,                // Confini fisici (usa ZoneBounds esistente!)
    @Nullable UUID linkedAreaId,      // Link a AreaDefinition (null = vuoto)
    @Nullable UUID linkedZoneId,      // Link a ZoneDefinition (per tracking)
    SlotType type,                    // FIXED, EDITABLE, TEST_AREA, RESTRICTED
    SlotPermissions permissions,      // Permessi
    BlockPos portalPosition,          // Posizione portale relativa a bounds.min()
    PortalColor portalColor,          // Colore portale (usa PortalColor esistente!)
    @Nullable String templateId,      // Template default da applicare
    int priority,                     // Priorità per overlap resolution
    long createdAt,
    long updatedAt
) {
    // Codec per serializzazione NBT
    public static final Codec<ZoneSlot> CODEC = RecordCodecBuilder.create(instance -> ...);
}
```

**Dipendenze esistenti da riutilizzare**:
- `ZoneBounds` da `zone/data/`
- `PortalColor` da `portal/`
- Pattern Codec da `AreaDefinition`

**Domande da risolvere**:
- [x] Aggiungere campo per warp node? → ✅ Implementato: `warpNodeId` field
- [x] Aggiungere campo per NPC spawn points? → ✅ Implementato: `npcSpawnPoints` List<BlockPos>
- [x] Campo per hologram position? → ✅ Implementato: `hologramPosition` field

**Campi Aggiuntivi (Step 1.3)**: ✅ Implementati
- `warpNodeId`: link a TransportRegistry warp node
- `npcSpawnPoints`: lista di posizioni spawn NPC (relative a bounds.min)
- `hologramPosition`: posizione hologram display (relativa a bounds.min)
- Builder methods: `withWarpNodeId()`, `withNpcSpawnPoints()`, `withHologramPosition()`
- Query methods: `hasWarpNode()`, `hasNpcSpawnPoints()`, `hasHologramPosition()`
- Absolute position methods: `getAbsoluteNpcSpawnPositions()`, `getAbsoluteHologramPosition()`

**Status**: [x] Da rivedere [x] Approvato [x] Implementato

---

## Step 1.4: Creare `ZoneSlotPresets`

**File**: `src/main/java/com/devmod/nexus/data/ZoneSlotPresets.java`

**Scopo**: Definire gli 11+ slot dal blueprint iniziale.

```java
public final class ZoneSlotPresets {

    // Costanti dal blueprint
    private static final int HUB_SIZE = 1600;        // 100 chunks
    private static final int CENTER_SIZE = 128;      // Area centrale
    private static final int ZONE_SIZE = 256;        // Dimensione zona standard
    private static final int CORRIDOR_WIDTH = 16;    // Larghezza corridoi
    private static final int FLOOR_Y = 64;

    public static List<ZoneSlot> createDefaultSlots() {
        List<ZoneSlot> slots = new ArrayList<>();

        // 1. SPAWN (centro, FIXED)
        slots.add(createSlot("spawn", "Spawn",
            bounds(0, 0, CENTER_SIZE, CENTER_SIZE),
            SlotType.FIXED, PortalColor.WHITE));

        // 2. TUTORIAL (nord)
        slots.add(createSlot("tutorial", "Tutorial",
            boundsNorth(1),
            SlotType.RESTRICTED, PortalColor.LIGHT_BLUE));

        // 3. HUB_QUEST Nord-Est
        slots.add(createSlot("quest_northeast", "Quest Hub",
            boundsNorthEast(),
            SlotType.EDITABLE, PortalColor.YELLOW));

        // 3. HUB_QUEST Nord-Ovest (simmetrico)
        slots.add(createSlot("quest_northwest", "Quest Hub",
            boundsNorthWest(),
            SlotType.EDITABLE, PortalColor.YELLOW));

        // 4. GATE_PROGRESSION (est)
        slots.add(createSlot("gate_progression", "Gate Progression",
            boundsEast(1),
            SlotType.RESTRICTED, PortalColor.PURPLE));

        // 5. BUILDING Est
        slots.add(createSlot("building_east", "Building Zone",
            boundsEast(2),
            SlotType.EDITABLE, PortalColor.GREEN));

        // 5. BUILDING Ovest
        slots.add(createSlot("building_west", "Building Zone",
            boundsWest(2),
            SlotType.EDITABLE, PortalColor.GREEN));

        // 6. TOWN_TEST / GESTIONALE / POLITICA (sud-ovest)
        slots.add(createSlot("town_management", "Town & Politics",
            boundsSouthWest(),
            SlotType.EDITABLE, PortalColor.BROWN));

        // 7. ECONOMIA Est
        slots.add(createSlot("economia_east", "Economia",
            boundsEast(3),
            SlotType.EDITABLE, PortalColor.GOLD));

        // 7. ECONOMIA Ovest
        slots.add(createSlot("economia_west", "Economia",
            boundsWest(3),
            SlotType.EDITABLE, PortalColor.GOLD));

        // 8. WAR_HUB Est
        slots.add(createSlot("war_hub_east", "War Hub",
            boundsSouthEast(1),
            SlotType.EDITABLE, PortalColor.RED));

        // 8. WAR_HUB Ovest
        slots.add(createSlot("war_hub_west", "War Hub",
            boundsSouthWest(1),
            SlotType.EDITABLE, PortalColor.RED));

        // 9. CLASSES_SYSTEM (sud)
        slots.add(createSlot("classes", "Classes System",
            boundsSouth(1),
            SlotType.EDITABLE, PortalColor.CYAN));

        // 10. EVENTI_PERIODICI (sud-est)
        slots.add(createSlot("eventi", "Eventi Periodici",
            boundsSouthEast(2),
            SlotType.EDITABLE, PortalColor.MAGENTA));

        // 11. DM_MOD (area admin)
        slots.add(createSlot("dm_mod", "DM Mod Testing",
            boundsSouth(2),
            SlotType.RESTRICTED, PortalColor.GRAY));

        return slots;
    }

    // Helper methods per calcolare bounds dal blueprint
    private static ZoneBounds bounds(int x, int z, int width, int depth) { ... }
    private static ZoneBounds boundsNorth(int ring) { ... }
    private static ZoneBounds boundsSouth(int ring) { ... }
    // etc.
}
```

**Domande da risolvere**:
- [x] Layout esatto dal blueprint (coordinate precise)? → Implementato in ZoneSlotPresets
- [x] Numero anelli dal centro? → 3 anelli con 15 slot totali
- [x] Dimensioni individuali per zona? → 256x256 standard, 128x128 centro

**Status**: [x] Da rivedere [x] Approvato [x] Implementato

---

# FASE 2: REGISTRY & PERSISTENCE

## Step 2.1: Creare `ZoneSlotRegistry`

**File**: `src/main/java/com/devmod/nexus/data/ZoneSlotRegistry.java`

**Scopo**: Persistenza e gestione CRUD degli slot.

```java
public class ZoneSlotRegistry extends SavedData {
    private static final String DATA_NAME = "devmod_nexus_slots";
    private static final int CURRENT_VERSION = 1;

    // Thread-safe storage (pattern da AreaRegistry)
    private final Map<String, ZoneSlot> slots = new ConcurrentHashMap<>();
    private final Map<UUID, String> areaToSlot = new ConcurrentHashMap<>();
    private final AtomicLong modificationVersion = new AtomicLong();

    // CRUD Operations
    public synchronized void registerSlot(@Nonnull ZoneSlot slot) { ... }
    public synchronized void updateSlot(@Nonnull ZoneSlot slot) { ... }
    public synchronized void deleteSlot(@Nonnull String slotId) { ... }

    // Queries
    public Optional<ZoneSlot> getSlot(@Nonnull String slotId) { ... }
    public Optional<ZoneSlot> getSlotByArea(@Nonnull UUID areaId) { ... }
    public List<ZoneSlot> getAllSlots() { ... }
    public List<ZoneSlot> getEditableSlots() { ... }

    // Linking
    public synchronized void linkArea(@Nonnull String slotId, @Nonnull UUID areaId) { ... }
    public synchronized void unlinkArea(@Nonnull String slotId) { ... }

    // Validation
    public boolean isPositionInSlot(@Nonnull BlockPos pos) { ... }
    public Optional<ZoneSlot> getSlotAt(@Nonnull BlockPos pos) { ... }
    public boolean boundsOverlap(@Nonnull ZoneBounds bounds) { ... }

    // SavedData implementation
    public static ZoneSlotRegistry get(MinecraftServer server) { ... }
    public static ZoneSlotRegistry load(CompoundTag tag, HolderLookup.Provider provider) { ... }
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) { ... }
}
```

**Pattern da seguire**: `AreaRegistry.java` (816 righe)

**Domande da risolvere**:
- [x] Validazione bounds al momento del link? → Sì, areBoundsCompatible() in NexusHubManager
- [x] Evento quando slot viene modificato? → modificationVersion tracking
- [x] Indice spaziale per query posizione? → getSlotAt() con stream filter

**Status**: [x] Da rivedere [x] Approvato [x] Implementato

---

# FASE 3: FOUNDATION BUILDER

## Step 3.1: Creare `NexusFoundationBuilder`

**File**: `src/main/java/com/devmod/nexus/builder/NexusFoundationBuilder.java`

**Scopo**: Costruire SOLO la struttura base minimale (sostituisce 2705 righe).

```java
public final class NexusFoundationBuilder {

    // Configurazione (da Config.java)
    private final int hubSize;
    private final int centerSize;
    private final int corridorWidth;
    private final int floorY;

    // Palette minimale
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    private static final BlockState FLOOR = Blocks.DEEPSLATE_TILES.defaultBlockState();
    private static final BlockState CORRIDOR = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    private static final BlockState BORDER = Blocks.OBSIDIAN.defaultBlockState();

    public void build(ServerLevel level, BlockPos origin) {
        LOGGER.info("[Nexus] Building foundation at {}", origin);

        // Step 1: Bedrock base (Y=0-3)
        buildBedrockLayer(level, origin);

        // Step 2: Floor base (Y=floorY)
        buildFloorLayer(level, origin);

        // Step 3: Centro spawn platform
        buildCenterPlatform(level, origin);

        // Step 4: Corridoi radiali (8 direzioni)
        buildCorridors(level, origin);

        // Step 5: World border markers
        buildBorderMarkers(level, origin);

        LOGGER.info("[Nexus] Foundation complete");
    }

    private void buildBedrockLayer(ServerLevel level, BlockPos origin) {
        // 4 layer di bedrock per impedire escape
    }

    private void buildFloorLayer(ServerLevel level, BlockPos origin) {
        // Floor base per tutto l'hub
        // Le zone sovrascriveranno quando linkate
    }

    private void buildCenterPlatform(ServerLevel level, BlockPos origin) {
        // Piattaforma spawn decorata
        // Usa NexusDecorBlocks se disponibili
    }

    private void buildCorridors(ServerLevel level, BlockPos origin) {
        // 8 corridoi: N, NE, E, SE, S, SW, W, NW
        // Possibilità: usa Area module con PATH shape!
    }

    private void buildBorderMarkers(ServerLevel level, BlockPos origin) {
        // Pilastri ai 4 angoli per indicare confini
    }
}
```

**Confronto con vecchio sistema**:
- Vecchio `NexusHubBuilder.java`: 2705 righe, layout hardcoded
- Nuovo `NexusFoundationBuilder.java`: ~300 righe, solo struttura base

**Domande da risolvere**:
- [x] Usare `NexusDecorBlocks` per estetica? → Usati blocchi vanilla per ora
- [x] Corridoi come PATH areas o hardcoded? → Hardcoded con pattern decorativo
- [x] Animazioni/particelle al centro? → Implementato in NexusHubManager.spawnAmbientParticles()

**Status**: [x] Da rivedere [x] Approvato [x] Implementato

---

# FASE 4: RUNTIME INTEGRATION

## Step 4.1: Modificare `NexusDimensionManager`

**File**: `src/main/java/com/devmod/runtime/NexusDimensionManager.java`

**Modifiche richieste**:

```diff
// In ensureNexusDimension():
- NexusHubBuilder.build(level, HUB_ORIGIN);
+ NexusFoundationBuilder builder = new NexusFoundationBuilder(config);
+ builder.build(level, getHubOrigin());
+
+ // Inizializza slots
+ ZoneSlotRegistry slotRegistry = ZoneSlotRegistry.get(server);
+ if (slotRegistry.getAllSlots().isEmpty()) {
+     ZoneSlotPresets.createDefaultSlots().forEach(slotRegistry::registerSlot);
+ }

// Nuovo metodo per hub origin configurabile:
+ public static BlockPos getHubOrigin() {
+     return new BlockPos(0, Config.NEXUS_FLOOR_Y.get(), 0);
+ }

// Rimuovere riferimenti a NexusHubBuilder
- import com.devmod.runtime.NexusHubBuilder;
```

**Status**: [x] Da rivedere [x] Approvato [x] Implementato

---

## Step 4.2: Creare `NexusHubManager`

**File**: `src/main/java/com/devmod/nexus/runtime/NexusHubManager.java`

**Scopo**: Orchestrare tutti i moduli per l'hub.

```java
public final class NexusHubManager {
    public static final NexusHubManager INSTANCE = new NexusHubManager();

    private NexusHubManager() {}

    // === SLOT MANAGEMENT ===

    public void linkAreaToSlot(MinecraftServer server, String slotId, UUID areaId) {
        ZoneSlotRegistry slots = ZoneSlotRegistry.get(server);
        AreaRegistry areas = AreaRegistry.get(server);

        ZoneSlot slot = slots.getSlot(slotId).orElseThrow();
        AreaDefinition area = areas.getArea(areaId).orElseThrow();

        // Verifica bounds compatibili
        validateBoundsMatch(slot.bounds(), area.dimensions());

        // Link
        slots.linkArea(slotId, areaId);

        // Trigger build
        AreaBuildTaskManager.INSTANCE.queueBuild(server, area, slot.bounds().min());

        // Crea zona tracking se non esiste
        ensureZoneExists(server, slot);

        // Crea portale
        createPortalForSlot(server, slot);
    }

    public void unlinkAreaFromSlot(MinecraftServer server, String slotId) {
        ZoneSlotRegistry slots = ZoneSlotRegistry.get(server);
        ZoneSlot slot = slots.getSlot(slotId).orElseThrow();

        // Rimuovi area (clear blocks?)
        slots.unlinkArea(slotId);

        // Rimuovi portale
        removePortalForSlot(server, slot);
    }

    // === PORTAL INTEGRATION ===

    private void createPortalForSlot(MinecraftServer server, ZoneSlot slot) {
        // Usa NexusPortalManager esistente
        NexusPortalManager.createPortal(
            server,
            slot.portalPosition(),
            slot.portalColor(),
            slot.slotId()
        );
    }

    // === ZONE INTEGRATION ===

    private void ensureZoneExists(MinecraftServer server, ZoneSlot slot) {
        ZoneRegistry zones = ZoneRegistry.get(server);

        if (slot.linkedZoneId() == null) {
            // Crea zona da slot
            ZoneDefinition zone = ZoneDefinition.fromSlot(slot);
            zones.registerZone(zone);
            // Update slot con zone ID
        }
    }

    // === TRANSPORT INTEGRATION ===

    public void createWarpNodeForSlot(MinecraftServer server, ZoneSlot slot) {
        // Usa TransportRegistry esistente
    }

    // === EVENT HANDLERS ===

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        INSTANCE.tick(event.getServer());
    }

    private void tick(MinecraftServer server) {
        // Verifica integrità portali
        // Cleanup slot temporanei scaduti
    }
}
```

**Status**: [x] Da rivedere [x] Approvato [x] Implementato

---

# FASE 5: NETWORK & COMMANDS

## Step 5.1: Creare `NexusHubNetworkHandler`

**File**: `src/main/java/com/devmod/nexus/network/NexusHubNetworkHandler.java`

**Payloads da creare**:

```java
// Client → Server
record SlotLinkPayload(String slotId, UUID areaId) {}
record SlotUnlinkPayload(String slotId) {}
record SlotCreatePayload(ZoneSlot slot) {}
record SlotDeletePayload(String slotId) {}
record RequestSlotListPayload() {}

// Server → Client
record SlotListPayload(List<ZoneSlot> slots) {}
record SlotUpdatePayload(ZoneSlot slot) {}
record HubStatusPayload(boolean initialized, int slotCount, int linkedCount) {}
```

**Status**: [x] Da rivedere [x] Approvato [x] Implementato

---

## Step 5.2: Creare `NexusCommands`

**File**: `src/main/java/com/devmod/nexus/command/NexusCommands.java`

**Comandi**:

```
/devmod nexus status
  → Mostra stato hub (inizializzato, slot count, etc.)

/devmod nexus slot list [page]
  → Lista tutti gli slot con stato

/devmod nexus slot info <slotId>
  → Dettagli slot (bounds, linked area, permissions)

/devmod nexus slot link <slotId> <areaName>
  → Collega area esistente a slot

/devmod nexus slot unlink <slotId>
  → Scollega area da slot

/devmod nexus slot create <slotId> --bounds=x1,y1,z1,x2,y2,z2 [--type=TEST_AREA]
  → Crea nuovo slot custom

/devmod nexus slot delete <slotId> --confirm
  → Elimina slot (solo custom)

/devmod nexus rebuild [--force]
  → Ricostruisce foundation

/devmod nexus teleport <slotId>
  → Teleport a slot specifico
```

**Status**: [x] Da rivedere [x] Approvato [x] Implementato

---

# FASE 6: CONFIG & CLEANUP

## Step 6.1: Aggiornare `Config.java`

**File**: `src/main/java/com/devmod/config/Config.java`

**Nuove opzioni**:

```java
// Nexus 2.0 - Hub Size
NEXUS_HUB_SIZE = BUILDER
    .comment("Total hub size in blocks (default: 1600 = 100 chunks)")
    .defineInRange("hubSize", 1600, 256, 4096);

NEXUS_CENTER_SIZE = BUILDER
    .comment("Center spawn area size (default: 128)")
    .defineInRange("centerSize", 128, 32, 512);

NEXUS_CORRIDOR_WIDTH = BUILDER
    .comment("Corridor width in blocks (default: 16)")
    .defineInRange("corridorWidth", 16, 4, 32);

NEXUS_FLOOR_Y = BUILDER
    .comment("Floor Y level (default: 64)")
    .defineInRange("floorY", 64, 4, 256);

NEXUS_SLOTS_CONFIG_PATH = BUILDER
    .comment("External JSON path for custom slot configuration (empty = use defaults)")
    .define("slotsConfigPath", "");

NEXUS_AUTO_CREATE_ZONES = BUILDER
    .comment("Automatically create ZoneDefinition when linking area to slot")
    .define("autoCreateZones", true);

NEXUS_AUTO_CREATE_PORTALS = BUILDER
    .comment("Automatically create portals when linking area to slot")
    .define("autoCreatePortals", true);
```

**Status**: [x] Da rivedere [x] Approvato [x] Implementato

---

## Step 6.2: Migrazione Completa a Nexus 2.0

**Decisione**: Migrazione completa al nuovo sistema modulare.

**Vecchio sistema rimosso**:
- ~~NexusHubBuilder.java~~ (2705 righe) - **ELIMINATO**

**Nuovo sistema modulare**:
- **NexusFoundationBuilder** (~420 righe) - Costruzione base hub con buildSteps()
- **NexusEntitySpawner** - Spawn combat dummies (estratto da NexusHubBuilder)
- **NexusOverlayManager** - Template overlay (estratto da NexusHubBuilder)
- **NexusPalette** - Block palette configurabile (già esistente)
- **NexusBuildStep** - Step per build staggerato (già esistente)
- **NexusHubManager** - Gestione logica degli slot e link alle aree

**Vantaggi**:
- [x] Riduzione da 2705 righe a ~420 righe (codice modulare)
- [x] Ogni funzionalità in un modulo dedicato
- [x] Manutenibilità migliorata
- [x] NexusDimensionManager usa i nuovi moduli

**Status**: [x] Da rivedere [x] Approvato [x] Implementato (migrazione completa)

---

# FASE 7: TESTING & VALIDATION

## Step 7.1: Test unitari

**File**: `src/test/java/com/devmod/nexus/`

```
ZoneSlotTest.java - Test record, validazione, SlotType, SlotPermissions
ZoneSlotRegistryTest.java - Test CRUD, linking, queries, inizializzazione
NexusHubManagerTest.java - Test singleton, status, registry integration, bounds
```

**Test creati (67 totali)**:
- `ZoneSlotTest`: 19 test (validation, query methods, builder methods, SlotType, SlotPermissions)
- `ZoneSlotRegistryTest`: 20 test (CRUD, linking, queries, initialization, slot counts)
- `NexusHubManagerTest`: 17 test (singleton, status, HubStatus record, registry, bounds, portals)

**Bug risolto durante testing**:
- ZoneSlotPresets aveva overlap tra slot (solo 7/15 registrati)
- Fix: Cardinal zones usano CENTER_HALF per dimensione perpendicolare
- Fix: dm_mod usa CENTER_HALF per evitare overlap con town_management
- Fix: economia_east/west aggiunto CORRIDOR_WIDTH gap
- Risultato: Tutti 15 slot ora si registrano correttamente

**Status**: [x] Da rivedere [x] Approvato [x] Implementato

---

## Step 7.2: Test integrazione

**Checklist manuale**:

```
[ ] Server si avvia senza errori
[ ] Nexus dimension si crea
[ ] Foundation viene costruita
[ ] /devmod nexus status funziona
[ ] /devmod nexus slot list mostra 15 slot default
[ ] /devmod nexus slot info <slotId> mostra dettagli
[ ] /devmod area create funziona in Nexus
[ ] Link area a slot funziona
[ ] Portale si crea automaticamente
[ ] Teleport tra zone funziona
[ ] Editing area via AreaBuilder funziona
[ ] Unlink area funziona
[ ] Test unitari passano (67 test)
```

**Come testare**:
1. Avviare server con `./gradlew runServer`
2. Entrare nel mondo e teletrasportarsi al Nexus: `/devmod nexus teleport spawn`
3. Verificare i comandi nexus funzionino
4. Creare un'area di test e linkarla a uno slot

**Status**: [x] Da rivedere [ ] Approvato [ ] Implementato

---

# RIEPILOGO FASI

| Fase | Descrizione | Steps | Effort Stimato |
|------|-------------|-------|----------------|
| 1 | Data Structures | 1.1-1.4 | 3h |
| 2 | Registry & Persistence | 2.1 | 2h |
| 3 | Foundation Builder | 3.1 | 1.5h |
| 4 | Runtime Integration | 4.1-4.2 | 2h |
| 5 | Network & Commands | 5.1-5.2 | 2h |
| 6 | Config & Cleanup | 6.1-6.2 | 1h |
| 7 | Testing | 7.1-7.2 | 1.5h |

**Totale: ~13 ore**

---

# NOTE PER L'AGENT

1. **Ogni step richiede approvazione** prima dell'implementazione
2. **Seguire i pattern esistenti** (vedi `AreaRegistry`, `AreaNetworkHandler`)
3. **Non modificare moduli esistenti** se non strettamente necessario
4. **Creare branch separato** per il lavoro: `feature/nexus-2.0`
5. **Commit frequenti** con messaggi descrittivi
6. **Test dopo ogni step** con `./gradlew compileJava`

---

# CHANGELOG

| Data | Step | Stato | Note |
|------|------|-------|------|
| 2026-01-18 | 1.1 | Completato | SlotType enum con 4 tipi |
| 2026-01-18 | 1.2 | Completato | SlotPermissions record con preset e metodi helper |
| 2026-01-18 | 1.3 | Completato | ZoneSlot record con Codec e builder methods |
| 2026-01-18 | 1.4 | Completato | ZoneSlotPresets con 15 slot default |
| 2026-01-18 | 2.1 | Completato | ZoneSlotRegistry con persistenza SavedData |
| 2026-01-18 | 3.1 | Completato | NexusFoundationBuilder (~350 righe vs 2705) |
| 2026-01-18 | 4.1 | Completato | NexusHubManager orchestratore |
| 2026-01-18 | 4.2 | Completato | NexusDimensionManager integrato |
| 2026-01-18 | 5.2 | Completato | NexusCommands con status/slot/teleport/rebuild |
| 2026-01-18 | FIX | Completato | SlotPermissions.STREAM_CODEC aggiunto |
| 2026-01-18 | FIX | Completato | ZoneSlot.STREAM_CODEC completo (13 campi) |
| 2026-01-18 | FIX | Completato | ZoneSlotRegistry validazione con AreaRegistry |
| 2026-01-18 | FIX | Completato | NexusHubManager → NexusPortalManager integrato |
| 2026-01-18 | FIX | Completato | NexusHubManager → AreaBuildTaskManager integrato |
| 2026-01-18 | FIX | Completato | PortalColor.STREAM_CODEC aggiunto |
| 2026-01-18 | 5.1 | Completato | NexusNetworkHandler con payloads |
| 2026-01-18 | 6.1 | Completato | Config Nexus 2.0 slot system (9 nuove opzioni) |
| 2026-01-18 | 6.2 | Completato | Coesistenza HubBuilder + HubManager |
| 2026-01-18 | MIGRATION | Completato | Rimosso NexusHubBuilder.java (2705 righe) |
| 2026-01-18 | MIGRATION | Completato | Creato NexusEntitySpawner (spawn dummies) |
| 2026-01-18 | MIGRATION | Completato | Creato NexusOverlayManager (overlay templates) |
| 2026-01-18 | MIGRATION | Completato | Aggiunto buildSteps() a NexusFoundationBuilder |
| 2026-01-18 | MIGRATION | Completato | NexusDimensionManager usa nuovi moduli |
| 2026-01-18 | 7.1 | Completato | ZoneSlotTest.java (19 test) |
| 2026-01-18 | 7.1 | Completato | ZoneSlotRegistryTest.java (20 test) |
| 2026-01-18 | 7.1 | Completato | NexusHubManagerTest.java (17 test) |
| 2026-01-18 | BUG FIX | Completato | ZoneSlotPresets overlap fix (15/15 slot ora registrati) |
| 2026-01-18 | 6.1 | Completato | Config Nexus 2.0 slot system (9 opzioni: hubSize, centerSize, zoneSize, corridorWidth, floorY, zoneHeight, slotsConfigPath, autoCreateZones, autoCreatePortals) |
| 2026-01-18 | 3.1 | Completato | Ambient particles al centro hub (END_ROD, SOUL, ENCHANT) |
| 2026-01-18 | 1.3+ | Completato | ZoneSlot campi aggiuntivi: warpNodeId, npcSpawnPoints, hologramPosition + builder/query methods |
