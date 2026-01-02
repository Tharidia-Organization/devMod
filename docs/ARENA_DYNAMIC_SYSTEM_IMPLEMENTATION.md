# Sistema Arena Dinamiche - Documentazione Implementazione Completa

**Data**: 2025-12-30
**Versione**: 1.0 - Post Phase 5
**Framework**: NeoForge 1.21.1

---

## Indice

1. [Panoramica Architetturale](#1-panoramica-architetturale)
2. [MobRequirements System](#2-mobrequirements-system)
3. [Zone System](#3-zone-system)
4. [ChunkGenerator e Multi-Biome](#4-chunkgenerator-e-multi-biome)
5. [Environment Sync e Time Control](#5-environment-sync-e-time-control)
6. [Lighting System](#6-lighting-system)
7. [Transition Effects](#7-transition-effects)
8. [WaveManager Integration](#8-wavemanager-integration)
9. [Problemi Noti e Fix Applicati](#9-problemi-noti-e-fix-applicati)
10. [Metriche e Telemetria](#10-metriche-e-telemetria)

---

## 1. Panoramica Architetturale

### Diagramma Componenti

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        ARENA DYNAMIC SYSTEM                              │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌──────────────────────┐    ┌──────────────────────┐                   │
│  │  MobRequirements     │───>│  ZoneLayoutPlanner   │                   │
│  │  Registry            │    │  (Grouping & Layout) │                   │
│  └──────────────────────┘    └──────────┬───────────┘                   │
│           │                              │                               │
│           │                              ▼                               │
│           │                  ┌──────────────────────┐                   │
│           │                  │     ZoneLayout       │                   │
│           │                  │  (Bounds & Shapes)   │                   │
│           │                  └──────────┬───────────┘                   │
│           │                              │                               │
│           ▼                              ▼                               │
│  ┌──────────────────────┐    ┌──────────────────────┐                   │
│  │ DimensionEnvironment │    │  ArenaChunkGenerator │                   │
│  │ Manager              │    │  + ZoneBiomeSource   │                   │
│  │ (Time/Biome/Light)   │    │  (Multi-Biome Gen)   │                   │
│  └──────────┬───────────┘    └──────────────────────┘                   │
│             │                                                            │
│             ▼                                                            │
│  ┌──────────────────────┐    ┌──────────────────────┐                   │
│  │   TimeController     │───>│ EnvironmentSyncPayload│                  │
│  │   (Frozen Time)      │    │ (Network S→C)        │                   │
│  └──────────────────────┘    └──────────┬───────────┘                   │
│                                          │                               │
│                                          ▼                               │
│                              ┌──────────────────────┐                   │
│                              │ClientEnvironmentCache│                   │
│                              │+ ClientLevelTimeMixin│                   │
│                              └──────────────────────┘                   │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Flusso Dati Principale

1. **Quest Start** → MobRequirementsRegistry fornisce requisiti mob
2. **Zone Planning** → ZoneLayoutPlanner raggruppa mob compatibili
3. **Dimension Creation** → ArenaFlatChunkGenerator + ZoneBiomeSource creano world multi-biome
4. **Environment Setup** → DimensionEnvironmentManager configura tempo/bioma/luce
5. **Client Sync** → EnvironmentSyncPayload sincronizza tempo frozen al client
6. **Wave Spawn** → WaveManager usa ZoneSpawnSlotAllocator per spawn zone-aware

---

## 2. MobRequirements System

### File Principali

| File | Descrizione | LOC |
|------|-------------|-----|
| `mob/MobRequirements.java` | Record principale con nested types | ~320 |
| `mob/MobRequirementsRegistry.java` | Singleton registry con cache | ~250 |
| `mob/MobRequirementsDetector.java` | Auto-detection da API vanilla | ~280 |
| `mob/MobRequirementsLoader.java` | Parser JSON override + validazione | ~540 |

### Struttura MobRequirements

```java
public record MobRequirements(
    ResourceLocation mobId,
    BiomeRequirement biome,      // preferredBiome, biomeTag, validBiomes
    LightRequirement light,      // min/max light, prefersDark/prefersLight
    FloorRequirement floor,      // validBlocks, requiresSolid, canSpawnOnLiquid
    SpaceRequirement space,      // width, height, clearanceAbove
    TimeRequirement time,        // DAY, NIGHT, ANY, DAWN, DUSK
    BossRequirement boss,        // isBoss, minPlayers, activationItem
    RequirementSource source,    // AUTO_DETECTED, JSON_OVERRIDE, MERGED
    float confidence             // 0.0-1.0
)
```

### Nested Types

#### BiomeRequirement
```java
record BiomeRequirement(
    Optional<ResourceLocation> preferredBiome,
    Optional<TagKey<Biome>> biomeTag,
    List<ResourceLocation> validBiomes,
    boolean required
)
```
- Factory: `preferred()`, `required()`, `fromTag()`, `anyOf()`
- **Nota**: `anyOf()` usa solo il primo bioma come preferred

#### LightRequirement
```java
record LightRequirement(
    int minLight,           // 0-15
    int maxLight,           // 0-15
    boolean prefersDark,    // true per mob notturni
    boolean prefersLight    // true per mob diurni
)
```
- Constants: `ANY`, `DARK`, `BRIGHT`, `PITCH_BLACK`
- `optimalLight()` restituisce il livello luce ideale

#### TimeRequirement
```java
enum TimeRequirement {
    DAY(0, 12000),
    NIGHT(12000, 24000),
    ANY(0, 24000),
    DAWN(22000, 24000),  // ATTENZIONE: semantica errata, vedi issue
    DUSK(10000, 14000)
}
```
- `isValidAt(long worldTime)` verifica se il tempo corrente è valido

### Auto-Detection Flow

```
EntityType<?> → SpawnPlacements.getPlacementType()
             → MobCategory.getCategory()
             → EntityType.getDimensions()
             → Biome.getMobSettings().getMobs()
                        ↓
              MobRequirements con confidence 0.4-0.9
```

### JSON Override Schema

```json
{
  "mobId": "minecraft:warden",
  "biome": {
    "preferred": "minecraft:deep_dark",
    "required": true
  },
  "light": {
    "min": 0,
    "max": 0,
    "prefersDark": true
  },
  "floor": {
    "blocks": ["minecraft:sculk", "minecraft:deepslate"],
    "requiresSolid": true
  },
  "space": {
    "width": 0.9,
    "height": 2.9,
    "clearanceAbove": 4
  },
  "time": "NIGHT",
  "boss": {
    "isBoss": true,
    "minPlayers": 2,
    "requiresActivation": false
  }
}
```

### Validazione JSON Implementata

La validazione JSON include:
- Controllo campi required (mobId)
- Rilevamento typos (campi sconosciuti)
- Validazione range (light 0-15)
- Validazione ResourceLocation format
- Warning per campi deprecati

---

## 3. Zone System

### File Principali

| File | Descrizione | LOC |
|------|-------------|-----|
| `arena/zone/ArenaZone.java` | Record zona con bounds e environment | ~320 |
| `arena/zone/ZoneEnvironment.java` | Configurazione ambiente per zona | ~200 |
| `arena/zone/ZoneLayout.java` | Container zone + builder | ~380 |
| `arena/zone/ZoneLayoutPlanner.java` | Algoritmo grouping e layout | ~350 |
| `arena/zone/ZoneSpawnSlotAllocator.java` | Allocazione spawn per zona | ~200 |
| `arena/zone/ZoneTransition.java` | Enum tipi transizione | ~50 |

### ArenaZone Record

```java
public record ArenaZone(
    String name,
    ZoneShape shape,           // RECTANGULAR, CIRCULAR, RING
    ZoneBounds bounds,         // Coordinate spaziali
    ZoneEnvironment environment,
    List<ResourceLocation> assignedMobs,
    int priority               // Per zone sovrapposte
)
```

### ZoneBounds

```java
public record ZoneBounds(
    int x1, int z1,
    int x2, int z2,
    Optional<Integer> radius,       // Per CIRCULAR/RING
    Optional<Integer> innerRadius   // Per RING (configurabile)
)
```

Factory methods:
- `rect(x1, z1, x2, z2)` - bounds rettangolari
- `circle(radius)` - cerchio centrato all'origine
- `circle(cx, cz, radius)` - cerchio centrato a coordinate
- `ring(outerRadius, innerRadius)` - anello configurabile

### ZoneEnvironment

```java
public record ZoneEnvironment(
    Optional<ResourceLocation> biome,
    Optional<ResourceLocation> floorMaterial,
    LightingConfig lighting,
    TimeConfig time
)
```

Presets:
- `DEFAULT` - sky=15, block=0, ANY time
- `nether()` - nether_wastes, NIGHT, dark
- `frozen()` - snowy_plains, BRIGHT, packed_ice
- `end()` - the_end, NIGHT, dark

### Layout Strategies

```java
enum LayoutStrategy {
    SINGLE,              // 1 zona, arena intera
    STRIPED_HORIZONTAL,  // 2 zone, divise N/S
    STRIPED_VERTICAL,    // 2 zone, divise E/W
    QUADRANT,            // 4 zone, NW/NE/SW/SE
    RADIAL               // N zone, anelli concentrici
}
```

### ZoneLayoutPlanner Algorithm

1. **Input**: Lista mobIds, arenaRadius
2. **Grouping**: Raggruppa mob per compatibilità (biome, light, time)
3. **Strategy Selection**: Sceglie layout basato su numero gruppi
4. **Bounds Calculation**: Calcola coordinate per ogni zona
5. **Output**: ZoneLayout completo con zone assegnate

```java
// Compatibilità check
private boolean areMobsCompatible(MobRequirements a, MobRequirements b) {
    return isBiomeCompatible(a.biome(), b.biome())
        && isLightCompatible(a.light(), b.light())
        && isTimeCompatible(a.time(), b.time());
}
```

### ZoneSpawnSlotAllocator

```java
public class ZoneSpawnSlotAllocator {
    private final ZoneLayout layout;
    private final Map<ResourceLocation, ArenaZone> mobZoneAssignments;
    private final Map<String, Integer> zoneSpawnCounts;

    // Alloca posizione spawn per mob specifico
    public BlockPos allocateSpawnPosition(ResourceLocation mobId) {
        ArenaZone zone = getZoneForMob(mobId);
        return zone.getRandomPosition(random, baseY);
    }
}
```

---

## 4. ChunkGenerator e Multi-Biome

### File Principali

| File | Descrizione | LOC |
|------|-------------|-----|
| `runtime/generator/ArenaChunkGenerator.java` | Classe base astratta | ~300 |
| `runtime/generator/ArenaFlatChunkGenerator.java` | Implementazione flat | ~340 |
| `runtime/generator/ModChunkGenerators.java` | Registrazione codec | ~45 |
| `runtime/biome/ZoneBiomeSource.java` | BiomeSource zone-aware | ~265 |
| `runtime/biome/ModBiomeSources.java` | Registrazione codec | ~40 |

### ArenaChunkGenerator (Base)

```java
public abstract class ArenaChunkGenerator extends ChunkGenerator {
    protected final ZoneBiomeSource zoneBiomeSource;
    protected final ArenaBounds arenaBounds;
    protected final ArenaZone.ZoneShape arenaShape;

    // Ottimizzazione: salta chunk fuori arena
    protected boolean chunkIntersectsArena(ChunkPos chunkPos) {
        return switch (arenaShape) {
            case RECTANGULAR -> rectangleIntersects(...);
            case CIRCULAR, RING -> circleIntersectsChunk(...);
        };
    }

    // Check posizione dentro arena
    protected boolean positionInArena(int x, int z) {
        return switch (arenaShape) {
            case RECTANGULAR -> x >= minX && x <= maxX && z >= minZ && z <= maxZ;
            case CIRCULAR -> distSq <= radiusSq;
            case RING -> distSq <= outerRadiusSq && distSq >= innerRadiusSq;
        };
    }
}
```

### ArenaBounds Record

```java
public record ArenaBounds(
    int minX, int minZ,
    int maxX, int maxZ,
    int centerX, int centerZ,
    int radius,
    int innerRadius  // Per RING shapes
)
```

### ArenaFlatChunkGenerator

```java
public class ArenaFlatChunkGenerator extends ArenaChunkGenerator {
    private final FlatLevelGeneratorSettings flatSettings;
    private final List<BlockState> layerStates;
    private final int floorY;
    private final boolean useBarrierOutside;

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(...) {
        if (!chunkIntersectsArena(chunkPos)) {
            return CompletableFuture.completedFuture(chunk); // Skip void chunks
        }
        generateFlatTerrain(chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    private void generateFlatTerrain(ChunkAccess chunk) {
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                if (positionInArena(worldX, worldZ)) {
                    generateColumn(chunk, pos, localX, localZ, ...);
                } else if (useBarrierOutside) {
                    placeBarrierColumn(chunk, pos, localX, localZ);
                }
                // else: void (air)
            }
        }
    }
}
```

### ZoneBiomeSource

**Funzionalità chiave**: Risolve biomi diversi per zone diverse.

```java
public class ZoneBiomeSource extends BiomeSource {
    private final ZoneLayout zoneLayout;
    private final Holder<Biome> defaultBiome;
    private final Map<String, Holder<Biome>> zoneBiomes;

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        if (zoneLayout == null) return defaultBiome;

        // Converti quart → block coordinates
        int blockX = QuartPos.toBlock(quartX);
        int blockZ = QuartPos.toBlock(quartZ);

        // Trova zona contenente questa posizione
        Optional<ArenaZone> zone = zoneLayout.getZoneAt(blockX, blockZ);

        if (zone.isPresent()) {
            return zoneBiomes.getOrDefault(zone.get().name(), defaultBiome);
        }
        return defaultBiome;
    }
}
```

### Compatibilità Biomi Moddati

```java
private Holder<Biome> resolveBiomeSafe(ResourceLocation biomeId, Registry<Biome> registry, Holder<Biome> fallback) {
    try {
        ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, biomeId);
        Optional<Holder.Reference<Biome>> holder = registry.getHolder(key);
        if (holder.isPresent()) {
            return holder.get();
        }
        LOGGER.warn("Biome {} not found in registry, using fallback", biomeId);
    } catch (Exception e) {
        LOGGER.warn("Error resolving biome {}: {}", biomeId, e.getMessage());
    }
    return fallback;
}
```

**Mod supportate nativamente**:
- Biomes O' Plenty
- Terralith
- Qualsiasi mod che registra biomi in `Registries.BIOME`

### Integrazione in DynamicDimensionManager

```java
// In createChunkGenerator()
if (zoneLayout != null && !zoneLayout.zones().isEmpty()) {
    ZoneBiomeSource zoneBiomeSource = ZoneBiomeSource.fromZoneLayout(
        zoneLayout, biomeRegistry, defaultBiome
    );
    return new ArenaFlatChunkGenerator(zoneBiomeSource, bounds, shape, flatSettings);
}
// Fallback a FlatLevelSource standard
return new FlatLevelSource(flatSettings);
```

---

## 5. Environment Sync e Time Control

### File Principali

| File | Descrizione | LOC |
|------|-------------|-----|
| `runtime/environment/DimensionEnvironmentManager.java` | Coordinatore ambiente | ~320 |
| `runtime/environment/TimeController.java` | Controllo tempo frozen | ~120 |
| `runtime/environment/EnvironmentSyncPayload.java` | Payload network S→C | ~100 |
| `client/environment/ClientEnvironmentCache.java` | Cache client override | ~80 |
| `mixin/client/ClientLevelTimeMixin.java` | Override getDayTime() | ~60 |

### DimensionEnvironmentManager

```java
public class DimensionEnvironmentManager {
    public static final DimensionEnvironmentManager INSTANCE = new DimensionEnvironmentManager();

    private final ConcurrentHashMap<ResourceKey<Level>, EnvironmentSettings> dimensionSettings;
    private final TimeController timeController;

    // Configura ambiente per dimensione
    public void configureEnvironment(
        ResourceKey<Level> dimensionKey,
        ArenaTemplate template,
        List<ResourceLocation> mobIds,
        ServerLevel level
    ) {
        EnvironmentSettings settings = calculateSettings(template, mobIds, level);
        dimensionSettings.put(dimensionKey, settings);

        // Configura tempo
        if (settings.timeConfig() != ZoneEnvironment.TimeConfig.ANY) {
            timeController.setTime(dimensionKey, settings.timeConfig(), level);
        }

        // Applica lighting se necessario
        applyLighting(dimensionKey, settings, level);
    }

    // Tick chiamato ogni server tick
    public void tick(MinecraftServer server) {
        timeController.tick(server);
    }
}
```

### TimeController

```java
public class TimeController {
    private final ConcurrentHashMap<ResourceKey<Level>, TimeSettings> dimensionTime;

    // Imposta tempo frozen
    public void setTime(ResourceKey<Level> dimensionKey, ZoneEnvironment.TimeConfig config, ServerLevel level) {
        long targetTime = switch (config) {
            case DAY -> 6000L;
            case NIGHT -> 18000L;
            case DAWN -> 23000L;
            case DUSK -> 12000L;
            case ANY -> level.getDayTime();
        };
        dimensionTime.put(dimensionKey, new TimeSettings(targetTime, config != ZoneEnvironment.TimeConfig.ANY));
    }

    // Enforce tempo ogni 20 tick
    public void tick(MinecraftServer server) {
        if (tickCounter++ % 20 != 0) return;

        for (var entry : dimensionTime.entrySet()) {
            TimeSettings settings = entry.getValue();
            if (settings.frozen) {
                ServerLevel level = server.getLevel(entry.getKey());
                if (level != null) {
                    long currentTime = level.getDayTime() % 24000;
                    if (Math.abs(currentTime - settings.targetTime) > 1) {
                        level.setDayTime(settings.targetTime);
                    }
                }
            }
        }
    }

    // Sync a player specifico
    public void syncToPlayer(ServerPlayer player, ResourceKey<Level> dimensionKey, String biomeId) {
        TimeSettings settings = dimensionTime.get(dimensionKey);
        if (settings != null && settings.frozen) {
            EnvironmentSyncPayload payload = EnvironmentSyncPayload.frozen(
                dimensionKey.location().toString(),
                settings.targetTime,
                biomeId
            );
            NetworkHandler.sendEnvironmentSync(player, payload);
        }
    }

    // Clear sync quando player esce
    public void clearSyncForPlayer(ServerPlayer player, ResourceKey<Level> dimensionKey) {
        EnvironmentSyncPayload clearPayload = EnvironmentSyncPayload.clear(
            dimensionKey.location().toString()
        );
        NetworkHandler.sendEnvironmentSync(player, clearPayload);
    }
}
```

### EnvironmentSyncPayload

```java
public record EnvironmentSyncPayload(
    String dimensionKey,
    long frozenTime,
    boolean isTimeFrozen,
    String biomeId
) implements CustomPacketPayload {

    public static final StreamCodec<FriendlyByteBuf, EnvironmentSyncPayload> STREAM_CODEC = ...;

    // Factory per tempo frozen
    public static EnvironmentSyncPayload frozen(String dimKey, long time, String biome) {
        return new EnvironmentSyncPayload(dimKey, time, true, biome);
    }

    // Factory per clear
    public static EnvironmentSyncPayload clear(String dimKey) {
        return new EnvironmentSyncPayload(dimKey, 0, false, "");
    }
}
```

### ClientEnvironmentCache

```java
public final class ClientEnvironmentCache {
    private static final ConcurrentHashMap<String, EnvironmentOverride> cache = new ConcurrentHashMap<>();

    public static void setOverride(String dimensionKey, long frozenTime, String biomeId) {
        cache.put(dimensionKey, new EnvironmentOverride(frozenTime, biomeId));
    }

    public static void clearOverride(String dimensionKey) {
        cache.remove(dimensionKey);
    }

    public static Optional<Long> getTimeOverride(String dimensionKey) {
        EnvironmentOverride override = cache.get(dimensionKey);
        return override != null ? Optional.of(override.frozenTime()) : Optional.empty();
    }

    public static void clearAll() {
        cache.clear();
    }

    record EnvironmentOverride(long frozenTime, String biomeId) {}
}
```

### ClientLevelTimeMixin

```java
@Mixin(ClientLevel.class)
public abstract class ClientLevelTimeMixin {

    @Inject(method = "getDayTime", at = @At("HEAD"), cancellable = true)
    private void onGetDayTime(CallbackInfoReturnable<Long> cir) {
        ClientLevel self = (ClientLevel)(Object)this;
        String dimension = self.dimension().location().toString();

        Optional<Long> frozenTime = ClientEnvironmentCache.getTimeOverride(dimension);
        if (frozenTime.isPresent()) {
            cir.setReturnValue(frozenTime.get());
        }
    }

    @Inject(method = "getTimeOfDay", at = @At("HEAD"), cancellable = true)
    private void onGetTimeOfDay(float partialTick, CallbackInfoReturnable<Float> cir) {
        ClientLevel self = (ClientLevel)(Object)this;
        String dimension = self.dimension().location().toString();

        Optional<Long> frozenTime = ClientEnvironmentCache.getTimeOverride(dimension);
        if (frozenTime.isPresent()) {
            // Replica formula vanilla con tempo frozen
            float timeOfDay = (frozenTime.get() % 24000L + partialTick) / 24000.0F - 0.25F;
            if (timeOfDay < 0.0F) timeOfDay += 1.0F;
            if (timeOfDay > 1.0F) timeOfDay -= 1.0F;
            cir.setReturnValue(timeOfDay);
        }
    }
}
```

### Cleanup Paths

1. **Player Logout**: `ClientModEvents.onPlayerLogout()` → `ClientEnvironmentCache.clearAll()`
2. **Leave Arena**: `DynamicDimensionManager.teleportToOverworld()` → `TimeController.clearSyncForPlayer()`
3. **Dimension Destroyed**: `DimensionEnvironmentManager.cleanupDimension()` → remove settings

---

## 6. Lighting System

### File Principali

| File | Descrizione | LOC |
|------|-------------|-----|
| `arena/builder/ArenaBuilder.java` | Metodi `placeLighting()` | ~1300 |
| `arena/builder/BuildDryRunCalculator.java` | Stima blocchi lighting | ~150 |
| `arena/registry/ArenaTemplate.java` | `Lighting` record | ~350 |

### ArenaTemplate.Lighting

```java
public record Lighting(
    int skyLight,           // 0-15, livello luce cielo
    int blockLight,         // 0-15, target livello blocco
    boolean ambientLight,   // true = piazza griglia automatica
    List<int[]> sources     // Explicit light sources [[x,y,z], ...]
)
```

**Y Coordinate Convention**:
- Se `floor` esiste: Y è floor-relative (`floor.y() + pos[1]`)
- Altrimenti: Y è assoluto

### Algoritmo Ambient Lighting

```java
// In ArenaBuilder.placeAmbientLighting()

// 1. Calcola spacing basato su target light level
int spacing = Math.max(4, Math.min(12, (15 - targetLight) * 2 + 2));

// 2. Determina Y level per piazzamento
int lightY = floorY + 1;
if (template.ceiling() != null && template.ceiling().enabled()) {
    lightY = template.ceiling().y() - 1;
} else if (template.walls() != null && template.walls().enabled()) {
    lightY = template.walls().startY() + template.walls().height() - 2;
}

// 3. Piazza griglia
for (int dx = spacing / 2; dx < sizeX; dx += spacing) {
    for (int dz = spacing / 2; dz < sizeZ; dz += spacing) {
        String lightBlock = selectLightBlock(targetLight);
        placeBlock(startX + dx, lightY, startZ + dz, lightBlock, tx);
    }
}
```

### Selezione Blocco Luce

```java
private String selectLightBlock(int targetLight) {
    if (targetLight >= 14) return "minecraft:sea_lantern";     // Light 15
    if (targetLight >= 11) return "minecraft:glowstone";       // Light 15
    if (targetLight >= 8)  return "minecraft:lantern";         // Light 15
    if (targetLight >= 5)  return "minecraft:torch";           // Light 14
    return "minecraft:soul_lantern";                           // Light 10
}
```

### Zone-Aware Lighting

```java
// In ArenaBuilder.placeZoneAwareLighting()

for (var zoneDef : zoneSettings.zones()) {
    Integer blockLight = zoneDef.blockLight();
    if (blockLight == null || blockLight <= 0) continue;

    // Trova zona corrispondente per bounds
    ArenaZone zone = layout.zones().stream()
        .filter(z -> z.name().equals(zoneDef.name()))
        .findFirst()
        .orElse(null);

    if (zone != null) {
        placeZoneLighting(zone, originX, originZ, floorY, blockLight, template, tx);
    }
}
```

### Stima Blocchi (Dry Run)

```java
// In BuildDryRunCalculator.estimateLightingBlocks()

int spacing = Math.max(4, Math.min(12, (15 - blockLight) * 2 + 2));

// Grid estimation
int gridX = (sizeX - spacing / 2 + spacing - 1) / spacing;
int gridZ = (sizeZ - spacing / 2 + spacing - 1) / spacing;
count += Math.max(0, gridX) * Math.max(0, gridZ);

// Explicit sources
if (lighting.sources() != null) {
    count += lighting.sources().size();
}
```

---

## 7. Transition Effects

### File Principali

| File | Descrizione | LOC |
|------|-------------|-----|
| `client/debug/effects/TransitionEffect.java` | Interface base | ~52 |
| `client/debug/effects/HardTransitionEffect.java` | Linee + glow pulsante | ~330 |
| `client/debug/effects/GradientTransitionEffect.java` | Fog + particelle | ~355 |
| `client/debug/effects/WallTransitionEffect.java` | Muro elettrico | ~315 |
| `client/debug/effects/GapTransitionEffect.java` | Void + particelle cadenti | ~365 |

### TransitionEffect Interface

```java
public interface TransitionEffect {
    void render(
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        Vec3 cameraPos,
        ArenaZone zone,
        int baseY,
        float time
    );

    String getName();

    default boolean usesParticles() { return false; }
    default boolean usesGlow() { return false; }
}
```

### Effetti Implementati

| Effetto | Descrizione | Particles | Glow |
|---------|-------------|-----------|------|
| HARD | Linee colorate con glow pulsante | No | Yes |
| GRADIENT | Fog sfumato con particelle END_ROD | Yes | Yes |
| WALL | Barriera verticale con ELECTRIC_SPARK | Yes | Yes |
| GAP | Void scuro con ASH + SOUL particles | Yes | Yes |

### Colori per Biome

```java
private int getZoneColor(ZoneEnvironment env) {
    var biome = env.biome();
    if (biome.isPresent()) {
        String path = biome.get().getPath().toLowerCase();
        if (path.contains("nether")) return 0xFF4444;  // Red
        if (path.contains("end"))    return 0xAA44FF;  // Purple
        if (path.contains("snow"))   return 0x44FFFF;  // Cyan
        if (path.contains("desert")) return 0xFFFF44;  // Yellow
        if (path.contains("ocean"))  return 0x4444FF;  // Blue
        if (path.contains("forest")) return 0x44FF44;  // Green
    }

    if (env.time() == TimeConfig.NIGHT) return 0x6644AA;
    if (env.time() == TimeConfig.DAY)   return 0xFFDD44;

    return 0xCCCCCC; // Default gray
}
```

---

## 8. WaveManager Integration

### SpawnContext

```java
static class SpawnContext {
    final List<BlockPos> positions;
    final Map<BlockPos, ArenaTemplate.SpawnSlot> slotMap;
    final @Nullable ArenaTemplate template;
    final @Nullable TemplateSpawnValidator runtimeValidator;
    final SpawnPools pools;
    final @Nullable ZoneLayout zoneLayout;
    final @Nullable ZoneSpawnSlotAllocator zoneAllocator;

    boolean hasZones() {
        return zoneLayout != null && zoneLayout.isMultiZone();
    }
}
```

### Zone-Aware Spawn Flow

```java
// In WaveManager.spawnWaveMobs()

// 1. Costruisci SpawnContext con zone allocator
SpawnContext context = buildSpawnContext(template, arenaHandle, mobConfigs, level, baseY);

// 2. Per ogni mob da spawnare
for (int i = 0; i < spawnCount; i++) {
    BlockPos spawnPos = null;

    // 3. Prova zone allocation prima
    if (context.hasZones() && context.zoneAllocator != null) {
        spawnPos = context.zoneAllocator.allocateSpawnPosition(mobId);
        if (spawnPos != null && occupied.isOccupied(spawnPos)) {
            spawnPos = null; // Fall back
        }
    }

    // 4. Fallback a pool-based selection
    if (spawnPos == null) {
        List<BlockPos> pool = chooseSpawnPool(context.pools, mobConfig);
        spawnPos = pickValidatedSpawnPosition(pool, ...);
    }

    // 5. Spawn entity
    if (spawnPos != null) {
        spawnEntityAt(level, mobConfig, spawnPos, ...);
    }
}

// 6. Record telemetry
if (context.hasZones()) {
    EnduranceTelemetryService.INSTANCE.recordZoneAllocationMetrics(
        questId, waveNumber, zoneAllocSuccess, zoneAllocFallback, ...
    );
}
```

### Environment Conditions Check

```java
// In WaveManager.ensureEnvironmentConditions()

// 1. Ottieni requirements per mob
MobRequirements reqs = MobRequirementsRegistry.INSTANCE.getOrDefault(mobId);

// 2. Verifica/applica tempo
if (reqs.time().prefersDark() && level.isDay()) {
    TimeController.setTime(level.dimension(), TimeConfig.NIGHT, level);
}

// 3. Re-configura ambiente con mobIds reali
DimensionEnvironmentManager.INSTANCE.configureEnvironment(
    level.dimension(),
    template,
    List.of(mobId),  // Ora con mob reale
    level
);

// 4. Verifica difficoltà
if (level.getDifficulty() == Difficulty.PEACEFUL) {
    level.getServer().setDifficulty(Difficulty.NORMAL, true);
}
```

### Respawn con Zone Allocation

```java
// In WaveManager.respawnMissingMobs()

if (waveState.getSpawnContext() != null &&
    waveState.getSpawnContext().hasZones() &&
    waveState.getSpawnContext().zoneAllocator != null) {

    spawnPos = waveState.getSpawnContext().zoneAllocator.allocateSpawnPosition(mobId);
    if (spawnPos != null && occupied.isOccupied(spawnPos)) {
        spawnPos = null;
    }
}

// Fallback se zone allocation fallisce
if (spawnPos == null) {
    spawnPos = pickValidatedSpawnPosition(candidatePool, ...);
}
```

---

## 9. Problemi Noti e Fix Applicati

### Fix Applicati

| # | Issue | File | Fix |
|---|-------|------|-----|
| 1 | TimeController.tick() mai chiamato | EnduranceEventTick.java:78 | Aggiunto call |
| 2 | Zone allocator non usato in spawn | WaveManager.java:541 | Integrato allocator |
| 3 | Zone allocator non usato in respawn | WaveManager.java:799-812 | Integrato allocator |
| 4 | Cache client non pulita su logout | ClientModEvents.java:310 | clearAll() call |
| 5 | Clear sync mancante su leave | DynamicDimensionManager.java:738-742 | clearSyncForPlayer() |
| 6 | Integer overflow in random position | ArenaZone.java:217-221 | Long arithmetic |
| 7 | Division by zero in allocator | ZoneSpawnSlotAllocator.java:152-155 | Guard clause |
| 8 | Ring inner radius non configurabile | ArenaZone.java:291-316 | Aggiunto innerRadius |
| 9 | JSON validation mancante | MobRequirementsLoader.java:66-539 | Full validation |
| 10 | Lighting dry-run non incluso | BuildDryRunCalculator.java:110-135 | estimateLightingBlocks() |

### Problemi Noti Residui

| # | Issue | Severità | Note |
|---|-------|----------|------|
| 1 | ~~TimeRequirement.DAWN semantica errata~~ | ✅ RISOLTO | Usa wrap-around (23000, 1000) |
| 2 | getTimeOfDay() signature mismatch potenziale | BASSA | Verificare con test |
| 3 | No re-sync su respawn in arena | BASSA | Client cache potrebbe essere vuota |
| 4 | ~~Zone lighting ignora skyLight~~ | ✅ BY DESIGN | Vedi nota sotto |
| 5 | ~~Spacing formula non raggiunge light < 8~~ | ✅ RISOLTO | Max spacing ora 20, usa redstone_torch |

### Note Design: SkyLight vs BlockLight nelle Zone

**SkyLight** in Minecraft è controllato a livello di **dimensione** (DimensionType), non per zona:
- Una dimensione ha UN valore di skylight (has_skylight = true/false)
- Non è possibile avere skylight diverso per zone diverse nella stessa dimensione
- Il campo `skyLight` in `ZoneDefinition` è metadata per futura implementazione multi-dimensione

**BlockLight** invece è controllabile per zona tramite placement di blocchi luminosi:
- Ogni zona può avere livelli di luce diversi piazzando torce/lanterne
- Il sistema usa `selectLightBlock()` per scegliere la sorgente appropriata
- Spacing formula: `Math.max(4, Math.min(20, (15 - targetLight) * 2 + 2))`

**Blocchi luce per target level**:
| Target | Blocco | Light Level |
|--------|--------|-------------|
| 14-15 | sea_lantern | 15 |
| 11-13 | glowstone | 15 |
| 8-10 | lantern | 15 |
| 5-7 | soul_lantern | 10 |
| 1-4 | redstone_torch | 7 |

---

## 10. Metriche e Telemetria

### Zone Allocation Metrics

```java
EnduranceTelemetryService.INSTANCE.recordZoneAllocationMetrics(
    String questId,
    int waveNumber,
    int zoneAllocSuccess,     // Spawn in zona corretta
    int zoneAllocFallback,    // Fallback a pool generico
    int totalSpawns,
    String templateId
);
```

### Spawn Telemetry

```java
// Per ogni wave
EnduranceTelemetryService.INSTANCE.recordWaveSpawns(
    questId,
    waveNumber,
    totalMobsSpawned,
    avgSpawnTime,
    failedSpawnAttempts
);
```

### Performance Considerations

- Zone lookup: O(n) per getZoneAt() - pre-sort consigliato
- Allocator: O(1) amortized per allocateSpawnPosition()
- Biome resolution: O(1) lookup in HashMap

---

## Appendice A: Registrazioni

### ModChunkGenerators

```java
public class ModChunkGenerators {
    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
        DeferredRegister.create(Registries.CHUNK_GENERATOR, DevMod.MODID);

    public static final Supplier<MapCodec<? extends ChunkGenerator>> ARENA_FLAT =
        CHUNK_GENERATORS.register("arena_flat", () -> ArenaFlatChunkGenerator.CODEC);
}
```

### ModBiomeSources

```java
public class ModBiomeSources {
    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
        DeferredRegister.create(Registries.BIOME_SOURCE, DevMod.MODID);

    public static final Supplier<MapCodec<? extends BiomeSource>> ZONE_BIOME_SOURCE =
        BIOME_SOURCES.register("zone_biome_source", () -> ZoneBiomeSource.CODEC);
}
```

### Network Registration

```java
// In NetworkHandler.java
event.registrar(ENVIRONMENT_SYNC.asString()).playToClient(
    EnvironmentSyncPayload.TYPE,
    EnvironmentSyncPayload.STREAM_CODEC,
    (payload, context) -> {
        enqueueWork(context, () ->
            withClientHooks(hooks -> hooks.handleEnvironmentSync(payload)));
    }
);
```

---

## Appendice B: Configurazione JSON Esempi

### Arena Template con Zone

```json
{
  "id": "multi_zone_arena",
  "shape": "RECTANGULAR",
  "radius": 32,
  "zoneSettings": {
    "enabled": true,
    "autoGenerate": false,
    "preferredStrategy": "QUADRANT",
    "zones": [
      {
        "name": "nether_zone",
        "biome": "minecraft:nether_wastes",
        "floorMaterial": "minecraft:netherrack",
        "skyLight": 0,
        "blockLight": 8,
        "time": "NIGHT",
        "mobTags": ["blaze", "wither_skeleton"]
      },
      {
        "name": "ice_zone",
        "biome": "minecraft:snowy_plains",
        "floorMaterial": "minecraft:packed_ice",
        "skyLight": 15,
        "blockLight": 12,
        "time": "DAY",
        "mobTags": ["stray", "polar_bear"]
      }
    ]
  },
  "lighting": {
    "skyLight": 15,
    "blockLight": 10,
    "ambientLight": true,
    "sources": []
  }
}
```

### Mob Requirements Override

```json
{
  "mobId": "minecraft:blaze",
  "biome": {
    "preferred": "minecraft:nether_wastes",
    "validBiomes": ["minecraft:nether_wastes", "minecraft:basalt_deltas"],
    "required": false
  },
  "light": {
    "min": 0,
    "max": 11,
    "prefersDark": true,
    "prefersLight": false
  },
  "floor": {
    "blocks": ["minecraft:netherrack", "minecraft:magma_block"],
    "requiresSolid": true,
    "canSpawnOnLiquid": false,
    "canSpawnInAir": true
  },
  "space": {
    "width": 0.6,
    "height": 1.8,
    "clearanceAbove": 2.0,
    "clearanceAround": 1.0
  },
  "time": "ANY"
}
```

---

**Fine Documentazione**

*Generata automaticamente da analisi codebase - 2025-12-30*
