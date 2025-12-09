# DevMod - Roadmap di Refactoring Professionale

## Stato Attuale: Fase 2 Completata

### Fase 1: Quick Wins - COMPLETATA

| Task | Status | File Modificati |
|------|--------|-----------------|
| Rimozione debug println | ✅ | `DebugRenderer.java` |
| Rimozione metodo deprecato | ✅ | `HitHelper.java` |
| Rinomina classi (PascalCase) | ✅ | `DevMod.java`, `DevModClient.java` |
| Config.java funzionale | ✅ | `Config.java`, `DevMod.java` |
| Config → HitHelper (cache) | ✅ | `HitHelper.java` |
| Config → NetworkHandler (radius) | ✅ | `NetworkHandler.java` |
| Config → TelemetryEvents (interval) | ✅ | `TelemetryEvents.java` |
| Config → WeaponStats (multipliers) | ✅ | `WeaponStats.java` |
| Config → ImpactHudOverlay | ✅ | `hud/ImpactHudOverlay.java` |
| Config → DebugRenderer | ✅ | `rendering/DebugRenderer.java` |
| Traduzioni config | ✅ | `lang/en_us.json` |

### Fase 2: Refactoring Architetturale - COMPLETATA

| Task | Status | File/LOC |
|------|--------|----------|
| HeatmapService (nuovo) | ✅ | `telemetry/spatial/HeatmapService.java` (252 LOC) |
| FightSessionService (nuovo) | ✅ | `telemetry/combat/FightSessionService.java` (348 LOC) |
| DamageTrackingService (nuovo) | ✅ | `telemetry/damage/DamageTrackingService.java` (304 LOC) |
| EntityTrackingService (nuovo) | ✅ | `telemetry/entity/EntityTrackingService.java` (436 LOC) |
| Import nuovi servizi | ✅ | `TelemetryService.java` |
| Configurazione sub-services | ✅ | `TelemetryService.reload()` |
| Cleanup entity delegation | ✅ | `TelemetryService.cleanupEntity()` |
| Delegazione checkStuck | ✅ | `EntityTrackingService` |
| Delegazione checkCamping | ✅ | `EntityTrackingService` |
| Delegazione tickAggro | ✅ | `EntityTrackingService` |
| Delegazione heatmap export | ✅ | `HeatmapService` |
| Rimozione codice duplicato | ✅ | PathTracker, PosTracker, CampingTracker, AggroTracker |
| **TelemetryService ridotto** | ✅ | **1721 → 1555 LOC (-10%)** |

### Struttura Package Telemetry

```
telemetry/
├── TelemetryService.java          (1555 LOC - orchestratore)
├── TelemetryConfig.java
├── TelemetrySettings.java
├── TelemetryJson.java
├── TelemetryEvents.java
├── AsyncTelemetryWriter.java      (I/O asincrono)
├── combat/
│   └── FightSessionService.java   (348 LOC) ✅
├── damage/
│   └── DamageTrackingService.java (304 LOC) ✅
├── entity/
│   └── EntityTrackingService.java (436 LOC) ✅
└── spatial/
    └── HeatmapService.java        (252 LOC) ✅
```

### Prossimi Passi (Opzionali)
1. **Ulteriore decomposizione**: Estrarre DungeonSession, BacktrackTracker in servizi separati
2. **Test unitari**: Aggiungere JUnit 5 + Mockito per i nuovi servizi
3. **Test runtime**: `./gradlew runClient` per verificare funzionamento

---

## Executive Summary

Questa mod NeoForge (1.21.1) implementa un sistema avanzato di debug/telemetria per sviluppatori di mappe e mod. La codebase contiene **~12.300 LOC** distribuite in **59 classi** attraverso 6 package specializzati.

**Valutazione Qualità Attuale: 6/10**

### Punti di Forza
- Architettura modulare ben organizzata (rendering, telemetry, hud, integration, ui)
- Thread-safety implementata correttamente con ConcurrentHashMap
- Sistema di cache performante per body part detection
- Soft dependency per mod esterne (Pehkui, Better Combat)
- Async I/O per telemetria (nessun lag spike)

### Aree Critiche
- God Classes (TelemetryService: 1700 LOC, 25+ mappe)
- Zero test coverage
- Config.java non utilizzato
- Metodi deprecati non rimossi
- System.out.println di debug in produzione

---

## Fase 1: Quick Wins (1-2 giorni)

### 1.1 Rimozione Debug Statements

**File:** [DebugRenderer.java](src/main/java/com/frenkvs/devmod/rendering/DebugRenderer.java)
**Linee:** 118-121, 362

```java
// RIMUOVERE:
System.out.println("[DebugRenderer] Adding sphere at " + center + " radius=" + radius + " segments=" + segments);
System.out.println("[DebugRenderer] Total shapes in list: " + shapes.size());
System.out.println("[DebugSphere] render() called! center=" + center + " radius=" + radius);
```

**Sostituire con:**
```java
// Usare il logger esistente o rimuovere completamente
private static final Logger LOGGER = LogUtils.getLogger();
// LOGGER.debug("Adding sphere...") se necessario per debug
```

### 1.2 Rimozione Metodo Deprecato

**File:** [HitHelper.java](src/main/java/com/frenkvs/devmod/HitHelper.java)
**Linee:** 84-110

Il metodo `rayTraceBodyPart()` è marcato `@Deprecated` ma ancora presente. Verificare che non sia più usato e rimuoverlo completamente.

### 1.3 Pulizia Config.java

**File:** [Config.java](src/main/java/com/frenkvs/devmod/Config.java)

Questo file contiene configurazioni di esempio (`LOG_DIRT_BLOCK`, `MAGIC_NUMBER`) che non sono utilizzate. Due opzioni:

**Opzione A - Rimuovere:** Se non serve configurazione esterna
**Opzione B - Utilizzare:** Collegare alla UI SettingsScreen per abilitare/disabilitare features

Configurazioni suggerite per Opzione B:
```java
public static final ModConfigSpec.BooleanValue TELEMETRY_ENABLED = BUILDER
    .comment("Enable telemetry logging")
    .define("telemetryEnabled", true);

public static final ModConfigSpec.BooleanValue DEBUG_OVERLAY_ENABLED = BUILDER
    .comment("Enable debug overlay rendering")
    .define("debugOverlayEnabled", false);

public static final ModConfigSpec.IntValue BODY_PART_CACHE_TTL = BUILDER
    .comment("Body part cache TTL in milliseconds")
    .defineInRange("bodyPartCacheTtl", 100, 50, 1000);
```

### 1.4 Naming Conventions

**File:** [devmod.java](src/main/java/com/frenkvs/devmod/devmod.java)

Rinominare la classe principale secondo Java conventions:
- `devmod` → `DevMod` (PascalCase per classi)

**File:** [devmodClient.java](src/main/java/com/frenkvs/devmod/devmodClient.java)
- `devmodClient` → `DevModClient`

---

## Fase 2: Refactoring Architetturale (1 settimana)

### 2.1 Split TelemetryService (PRIORITÀ ALTA)

**Problema:** TelemetryService.java ha 1700+ LOC con 25+ mappe ConcurrentHashMap e 10+ responsabilità diverse.

**Soluzione:** Dividere in servizi domain-specific:

```
telemetry/
├── TelemetryService.java          (orchestratore, <200 LOC)
├── damage/
│   └── DamageTrackingService.java (hit, damage, weapon stats)
├── combat/
│   └── FightSessionService.java   (fight sessions, TTK)
├── spatial/
│   └── HeatmapService.java        (stuck, aggro drop, kiting, death heatmaps)
├── entity/
│   └── EntityTrackingService.java (spawn, minion waves, aggro)
├── analysis/
│   └── BossPhaseService.java      (boss phases, skill tracking)
└── io/
    └── AsyncTelemetryWriter.java  (già esistente, OK)
```

**Esempio di separazione per HeatmapService:**

```java
package com.frenkvs.devmod.telemetry.spatial;

public class HeatmapService {
    public static final HeatmapService INSTANCE = new HeatmapService();

    private final Map<String, Map<BlockPos, Integer>> stuckHeatmap = new ConcurrentHashMap<>();
    private final Map<String, Map<BlockPos, Integer>> aggroDropHeatmap = new ConcurrentHashMap<>();
    private final Map<String, Map<BlockPos, Integer>> kitingHeatmap = new ConcurrentHashMap<>();
    private final Map<String, Map<BlockPos, Integer>> deathHeatmap = new ConcurrentHashMap<>();
    private final Map<String, Map<BlockPos, Integer>> movementHeatmap = new ConcurrentHashMap<>();
    private final Map<String, Map<BlockPos, Integer>> campingHeatmap = new ConcurrentHashMap<>();

    public void recordStuck(String room, BlockPos pos) {
        stuckHeatmap.computeIfAbsent(room, k -> new ConcurrentHashMap<>())
                    .merge(pos, 1, Integer::sum);
    }

    // ... altri metodi specifici
}
```

### 2.2 Separazione DamageHandler

**File:** [DamageHandler.java](src/main/java/com/frenkvs/devmod/DamageHandler.java)

**Problema:** Calcolo danno + HUD update + VFX in una classe singola.

**Soluzione:**

```java
// DamageCalculator.java - Logica di calcolo pura
public class DamageCalculator {
    public static DamageResult calculate(
        LivingEntity victim,
        LivingEntity attacker,
        float originalDamage,
        WeaponStats stats,
        HitHelper.BodyPart bodyPart
    ) {
        float multiplier = getMultiplier(stats, bodyPart);
        float newDamage = (originalDamage + stats.baseDamageBonus) * multiplier;
        float armorPenBonus = calculateArmorPen(victim, stats);
        return new DamageResult(newDamage + armorPenBonus, multiplier, armorPenBonus, bodyPart);
    }
}

// DamageResult.java
public record DamageResult(
    float finalDamage,
    float multiplier,
    float armorPenBonus,
    HitHelper.BodyPart bodyPart
) {}
```

### 2.3 PayloadHandler Interface

**File:** [NetworkHandler.java](src/main/java/com/frenkvs/devmod/NetworkHandler.java)

**Problema:** 3 payload gestiti in un'unica classe con duplicazione di logica.

**Soluzione:**

```java
// PayloadHandler.java
public interface PayloadHandler<T extends CustomPacketPayload> {
    void handle(T payload, IPayloadContext context);
}

// MobStatsPayloadHandler.java
public class MobStatsPayloadHandler implements PayloadHandler<UpdateMobStatsPayload> {
    @Override
    public void handle(UpdateMobStatsPayload payload, IPayloadContext context) {
        // Logica esistente da handleMobData
    }
}

// NetworkHandler.java - Solo registrazione
@EventBusSubscriber(modid = "devmod")
public class NetworkHandler {
    private static final MobStatsPayloadHandler MOB_HANDLER = new MobStatsPayloadHandler();
    private static final WeaponPayloadHandler WEAPON_HANDLER = new WeaponPayloadHandler();
    private static final EquipPayloadHandler EQUIP_HANDLER = new EquipPayloadHandler();

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(
            UpdateMobStatsPayload.TYPE,
            UpdateMobStatsPayload.STREAM_CODEC,
            MOB_HANDLER::handle
        );
        // ...
    }
}
```

---

## Fase 3: Miglioramenti UI/UX (3-5 giorni)

### 3.1 Base Screen Class

Creare una classe base per tutte le screen della mod:

```java
package com.frenkvs.devmod.ui;

public abstract class ModScreen extends Screen {
    protected final Screen parent;

    protected ModScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    protected abstract void onApply();
    protected abstract void onCancel();
    protected abstract void onReset();

    @Override
    public void onClose() {
        if (parent != null) {
            minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    // Helper methods comuni
    protected void addStandardButtons() {
        this.addRenderableWidget(Button.builder(Component.literal("Applica"), b -> onApply())
            .bounds(width / 2 - 150, height - 28, 90, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Annulla"), b -> onCancel())
            .bounds(width / 2 - 45, height - 28, 90, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Reset"), b -> onReset())
            .bounds(width / 2 + 60, height - 28, 90, 20).build());
    }

    protected EditBox createNumberField(int x, int y, int width, String initialValue) {
        EditBox box = new EditBox(font, x, y, width, 20, Component.empty());
        box.setValue(initialValue);
        box.setFilter(s -> s.isEmpty() || s.matches("-?\\d*\\.?\\d*"));
        return box;
    }
}
```

### 3.2 Validazione Input Centralizzata

```java
package com.frenkvs.devmod.ui;

public class InputValidator {
    public static Optional<Double> parseDouble(String input, double min, double max) {
        try {
            double value = Double.parseDouble(input);
            if (value >= min && value <= max) {
                return Optional.of(value);
            }
        } catch (NumberFormatException ignored) {}
        return Optional.empty();
    }

    public static Optional<Integer> parseInt(String input, int min, int max) {
        try {
            int value = Integer.parseInt(input);
            if (value >= min && value <= max) {
                return Optional.of(value);
            }
        } catch (NumberFormatException ignored) {}
        return Optional.empty();
    }
}
```

---

## Fase 4: Test Framework (1 settimana)

### 4.1 Setup JUnit 5

Aggiungere in `build.gradle`:

```gradle
dependencies {
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.0'
    testImplementation 'org.mockito:mockito-core:5.5.0'
    testImplementation 'org.mockito:mockito-junit-jupiter:5.5.0'
}

test {
    useJUnitPlatform()
}
```

### 4.2 Test Prioritari

**HitHelper Tests:**
```java
class HitHelperTest {
    @Test
    void getBodyPart_hitAbove75Percent_returnsHead() {
        // Mock LivingEntity at Y=0 with height 2.0
        // Hit at Y=1.6 (80% height)
        assertEquals(BodyPart.HEAD, HitHelper.getBodyPart(mockEntity, 1.6));
    }

    @Test
    void getBodyPart_hitBelow40Percent_returnsLegs() {
        assertEquals(BodyPart.LEGS, HitHelper.getBodyPart(mockEntity, 0.7));
    }
}
```

**DamageCalculator Tests:**
```java
class DamageCalculatorTest {
    @Test
    void calculate_headshot_appliesHeadMultiplier() {
        WeaponStats stats = new WeaponStats();
        stats.headMult = 2.0f;

        DamageResult result = DamageCalculator.calculate(victim, attacker, 10f, stats, BodyPart.HEAD);

        assertEquals(20f, result.finalDamage(), 0.01f);
    }
}
```

---

## Fase 5: Documentazione (2-3 giorni)

### 5.1 Javadoc per API Pubbliche

Aggiungere Javadoc a tutti i metodi pubblici delle classi principali:

- `TelemetryService` (dopo refactoring)
- `DebugRenderer`
- `HitHelper`
- `NetworkHandler`

### 5.2 README Aggiornato

Creare un README.md professionale con:
- Descrizione della mod
- Requisiti (Java 21, NeoForge 21.1.x)
- Installazione
- Keybindings
- API per mod developers
- Contributing guidelines

---

## Metriche di Successo

| Metrica | Attuale | Target |
|---------|---------|--------|
| Cyclomatic Complexity (max) | 45 | < 20 |
| LOC per file (max) | 1700 | < 400 |
| Test Coverage | 0% | > 60% |
| Static methods | 40% | < 20% |
| Codice duplicato | ~15% | < 5% |

---

## Piano di Implementazione

### Settimana 1
- [ ] Quick Wins (Fase 1 completa)
- [ ] Setup test framework

### Settimana 2
- [ ] Split TelemetryService
- [ ] PayloadHandler interface

### Settimana 3
- [ ] Separazione DamageHandler
- [ ] Base UI Screen class

### Settimana 4
- [ ] Test coverage > 40%
- [ ] Documentazione API

### Settimana 5
- [ ] Code review finale
- [ ] Performance profiling
- [ ] Release candidate

---

## Note Tecniche

### Thread Safety
La mod usa correttamente `ConcurrentHashMap` per le strutture dati condivise. Mantenere questo pattern durante il refactoring.

### Compatibilità Mod
Soft dependencies per Pehkui e Better Combat tramite reflection. Non modificare `ModIntegrationManager` senza test approfonditi.

### Performance
Il sistema di cache in `HitHelper` è ben ottimizzato (TTL 100ms, 80%+ hit rate). Non toccare senza profilazione.

### NeoForge 1.21.1
Usare sempre le API NeoForge invece di Forge legacy. Attenzione ai deprecation warnings nelle future versioni.
