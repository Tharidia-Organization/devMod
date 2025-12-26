# Impact HUD - Architettura Dettagliata

## 1. Panoramica dei Componenti

Il sistema Impact HUD è organizzato in tre layer principali con responsabilità separate.

### 1.1 Layer di Acquisizione Dati

Questo layer intercetta gli eventi di danno e raccoglie le informazioni necessarie.

#### DamageHandler.java
**Path:** `src/main/java/com/frenkvs/devmod/DamageHandler.java`

**Responsabilità:**
- Intercetta `LivingIncomingDamageEvent` (priority HIGH)
- Identifica arma, body part, hit point
- Calcola danno modificato
- Crea e salva `ImpactData`
- Trigger VFX

**Entry Points:**
```java
@SubscribeEvent(priority = EventPriority.HIGH)
public static void onDamage(LivingIncomingDamageEvent event)

@SubscribeEvent
public static void onAttackEntity(AttackEntityEvent event)  // Per evasion detection

@SubscribeEvent(priority = EventPriority.NORMAL)
public static void onEnvironmentalDamage(LivingIncomingDamageEvent event)
```

**Dipendenze:**
- `HitHelper` - Body part detection
- `WeaponConfigManager` - Statistiche arma
- `DamageBreakdown` - Calcolo breakdown
- `ImpactData` - Storage dati
- `ClientVFXProxy` - Trigger VFX

#### HitHelper.java
**Path:** `src/main/java/com/frenkvs/devmod/HitHelper.java`

**Responsabilità:**
- Rilevamento body part tramite raycast AABB
- Cache per performance (TTL 100ms)
- Supporto entità non-umanoidi (draghi, boss)
- Tracking ultimo hit per QA

**Algoritmo Body Part Detection:**
```
1. Controlla cache (80%+ hit rate)
2. Se cache miss:
   a. Determina aspect ratio dell'entità
   b. Se horizontal body (dragon) → usa front/back/middle
   c. Se tall body (enderman) → usa head più piccola (15%)
   d. Altrimenti → usa divisione standard:
      - HEAD: top 25%
      - ARMS: lati 30% width, zona centrale
      - BODY: centro 40% width, zona centrale
      - LEGS: bottom 35%
3. Raycast da eye position dell'attacker
4. Ritorna prima intersezione trovata
5. Fallback: usa pitch angle se nessuna intersezione
```

**Zone Standard (Humanoid):**
```
100% ┌─────────────┐
     │    HEAD     │  25% (75-100%)
 75% ├─────────────┤
     │ARM│BODY│ARM │  40% (35-75%)
     │   │    │    │
 35% ├───┴────┴────┤
     │    LEGS     │  35% (0-35%)
  0% └─────────────┘
```

#### ActualDamageTracker.java
**Path:** `src/main/java/com/frenkvs/devmod/ActualDamageTracker.java`

**Responsabilità:**
- Intercetta `LivingDamageEvent.Post` (priority LOWEST)
- Cattura danno REALE post-riduzione
- Aggiorna `ImpactData` con valori effettivi

**Timing:**
```
LivingIncomingDamageEvent (DamageHandler) → Calcola danno teorico
                          ↓
            Minecraft applica riduzioni
                          ↓
LivingDamageEvent.Post (ActualDamageTracker) → Cattura danno reale
```

### 1.2 Layer di Storage

#### ImpactData.java
**Path:** `src/main/java/com/frenkvs/devmod/hud/ImpactData.java`

**Responsabilità:**
- Container immutabile per dati impatto
- Gestione lifecycle (3s display + 500ms fade)
- Isolamento multiplayer (per-player UUID)
- Auto-cleanup entries scadute
- **Timeout massimo 30s** per observation lock (BUG-005 FIXED)

**Struttura Dati:**
```java
public class ImpactData {
    // Identificazione
    public final long timestamp;
    public final UUID attackerUUID;
    public final WeakReference<LivingEntity> targetRef;
    public final String targetName;

    // Combat Data
    public final BodyPart bodyPart;
    public final float bodyPartMultiplier;
    public final DamageBreakdown breakdown;
    public final String attackSource;
    public final boolean isRanged;

    // 3D Position
    @Nullable public final Vec3 hitPoint;
    @Nullable public final Vec3 slashDirection;

    // Mod Integration
    @Nullable public final Float pehkuiVisualScale;
    @Nullable public final Float pehkuiHitboxScale;
    @Nullable public final String betterCombatAttackName;

    // Actual Damage (updated async)
    private volatile float actualDamageDealt = -1f;
    private volatile float healthBefore = -1f;
    private volatile float healthAfter = -1f;
}
```

**Storage Pattern:**
```java
// Thread-safe per-player storage
private static final Map<UUID, ImpactData> IMPACTS_BY_PLAYER = new ConcurrentHashMap<>();

// Cleanup automatico ogni 10 secondi
private static void maybeCleanup() {
    IMPACTS_BY_PLAYER.entrySet().removeIf(entry ->
        entry.getValue() == null || entry.getValue().isExpired()
    );
}
```

**Observation State Machine:**
```
                    ┌─────────────────┐
                    │   DISPLAYING    │
                    │  (alpha = 1.0)  │
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
              ▼              │              ▼
    ┌─────────────────┐      │    ┌─────────────────┐
    │    OBSERVING    │◄─────┴───►│   FADE_START    │
    │ (timer paused)  │           │ (3s countdown)  │
    │ MAX 30s timeout │ ◄── FIXED │                 │
    └─────────────────┘           └────────┬────────┘
                                           │
                                           ▼
                                  ┌─────────────────┐
                                  │    FADING       │
                                  │ (500ms fade)    │
                                  └────────┬────────┘
                                           │
                                           ▼
                                  ┌─────────────────┐
                                  │    EXPIRED      │
                                  │   (removed)     │
                                  └─────────────────┘
```

**Nota:** Il timeout massimo di 30 secondi (MAX_OBSERVATION_TIME_MS) previene che l'HUD rimanga bloccato indefinitamente (BUG-005 FIXED).

#### DamageBreakdown.java
**Path:** `src/main/java/com/frenkvs/devmod/damage/DamageBreakdown.java`

**Responsabilità:**
- Calcolo dettagliato componenti danno
- Generazione formula string per HUD (cached nel constructor)
- Gestione enchant bonus con filtro per target type

**Struttura:**
```java
public class DamageBreakdown {
    public final float baseWeaponDamage;
    public final List<EnchantBonus> enchantBonuses;
    public final float pehkuiSizeBonus;
    public final float pehkuiScale;
    public final float bodyPartMultiplier;
    public final float armorPenetrationBonus;
    public final float finalDamage;  // Risultato calcolato

    public record EnchantBonus(String name, int level, float bonus) {}
}
```

#### ImpactHudContentBuilder.java (NEW)

**Path:** `src/main/java/com/frenkvs/devmod/hud/ImpactHudContentBuilder.java`

**Responsabilità:**

- Generazione centralizzata contenuti HUD
- Usato sia da 2D overlay che 3D renderer
- Riduce duplicazione codice (OPT-001 ADDRESSED)

#### DamageCalculator.java (NEW)

**Path:** `src/main/java/com/frenkvs/devmod/damage/DamageCalculator.java`

**Responsabilità:**

- Calcolo centralizzato del danno
- Separazione logica di calcolo da event handling

### 1.3 Layer di Rendering

#### ImpactHudOverlay.java (2D)
**Path:** `src/main/java/com/frenkvs/devmod/hud/ImpactHudOverlay.java`

**Responsabilità:**
- Rendering overlay 2D sopra crosshair
- Gestione alpha/fade
- Hit-test crosshair per observation

**Registrazione:**
```java
@SubscribeEvent
public static void registerGuiLayers(RegisterGuiLayersEvent event) {
    event.registerAbove(
        VanillaGuiLayers.CROSSHAIR,
        LAYER_ID,
        ImpactHudOverlay::render
    );
}
```

**Layout:**
```
Screen
┌──────────────────────────────────────────────────────┐
│                                    ┌────────────────┐│
│                                    │Impact Analysis ││ ← Panel 1
│                                    │                ││
│                                    │ Part: HEAD     ││
│                                    │ Source: Melee  ││
│                                    │ Base: 7.0      ││
│                                    │ Sharpness: +3  ││
│                                    │ Formula: ...   ││
│                                    │ ACTUAL: 8.5    ││
│                                    └────────────────┘│
│                                    ┌────────────────┐│
│                                    │ Mod Specifics  ││ ← Panel 2
│                                    │ Pehkui: 1.2x   ││
│                                    └────────────────┘│
│                          +                           │ ← Crosshair
│                                                      │
└──────────────────────────────────────────────────────┘
```

#### Impact3DPanelManager.java
**Path:** `src/main/java/com/frenkvs/devmod/hud/Impact3DPanelManager.java`

**Responsabilità:**
- Gestione pool pannelli 3D (max 12)
- Distance culling (64 blocks)
- Lifecycle management

**Limiti Performance:**
```java
private static final int MAX_PANELS = 12;
private static final double MAX_RENDER_DISTANCE = 64.0;
```

#### Impact3DPanel.java
**Path:** `src/main/java/com/frenkvs/devmod/hud/Impact3DPanel.java`

**Responsabilità:**
- Singola istanza pannello nel mondo
- Gestione fade in/out
- Posizione relativa a hit point

**Lifecycle:**
```
0ms        500ms              3000ms      4000ms
│           │                    │           │
▼───────────▼────────────────────▼───────────▼
│  FADE IN  │     FULL ALPHA     │ FADE OUT  │
│  0 → 1    │       1.0          │  1 → 0    │
└───────────┴────────────────────┴───────────┘
```

#### Impact3DRenderer.java
**Path:** `src/main/java/com/frenkvs/devmod/hud/Impact3DRenderer.java`

**Responsabilità:**
- Rendering geometria 3D nel mondo
- Billboard rotation verso camera
- Connection line da hit point a pannello

**Pipeline Rendering:**
```
1. Render connection line (cyan, da hit point a pannello)
2. Push pose, translate to panel position
3. Billboard rotation (face camera)
4. Scale (0.02f world units)
5. Render background (debugQuads)
6. Render border (lines)
7. Render text content (SEE_THROUGH mode)
8. Pop pose
```

#### ImpactVFX.java
**Path:** `src/main/java/com/frenkvs/devmod/hud/ImpactVFX.java`

**Responsabilità:**
- Energy Vortex Core (spirale rotante)
- Slash Animation (arco che segue il colpo)
- Connection Lines (linee verso camera)

**Effetti:**
| Effetto | Durata | Descrizione |
|---------|--------|-------------|
| Core | 2500ms | Spirale energia al centro impatto |
| Slash | 600ms | Arco animato che simula il taglio |
| Lines | 2000ms | Linee che collegano core a camera |

---

## 2. Diagramma Flusso Completo

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              ATTACK EVENT                                    │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           AttackEntityEvent                                  │
│                         (DamageHandler.onAttackEntity)                       │
│                                                                              │
│  • Salva posizione target PRIMA del danno                                   │
│  • Per Enderman: schedula check evasione (150ms delay)                      │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                      LivingIncomingDamageEvent                               │
│                         (DamageHandler.onDamage)                             │
│                          PRIORITY: HIGH                                      │
│                                                                              │
│  1. Identifica attacco (melee vs ranged)                                    │
│     ├─ Ranged: usa arrow.getY() per body part                               │
│     └─ Melee: usa HitHelper.rayTraceBodyPartWithHitPoint()                  │
│                                                                              │
│  2. Recupera WeaponStats                                                     │
│     └─ WeaponConfigManager.getStats(weapon)                                 │
│                                                                              │
│  3. Calcola moltiplicatore body part                                        │
│     └─ HEAD/BODY/ARMS/LEGS → stats.headMult/bodyMult/etc                    │
│                                                                              │
│  4. Calcola danno finale                                                     │
│     └─ newDamage = (original + baseDamageBonus) * multiplier                │
│                                                                              │
│  5. Applica armor penetration                                                │
│     └─ calculateArmorPenBonus() con formula configurata                     │
│                                                                              │
│  6. Applica custom armor reduction                                           │
│     └─ Solo se vittima è Player con DevMod ArmorStats                       │
│                                                                              │
│  7. event.setAmount(newDamage)  ◄── MODIFICA DANNO                          │
│                                                                              │
│  8. Crea DamageBreakdown                                                     │
│     └─ Calcola: base + enchants + pehkui bonus                              │
│                                                                              │
│  9. Crea e salva ImpactData                                                  │
│     └─ ImpactData.store(impactData)                                         │
│                                                                              │
│ 10. Trigger VFX                                                              │
│     └─ ClientVFXProxy.addImpactVFX()                                        │
│                                                                              │
│ 11. Lifesteal (post-hit)                                                     │
│     └─ attacker.heal(newDamage * stats.lifesteal)                           │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         MINECRAFT DAMAGE SYSTEM                              │
│                                                                              │
│  Applica:                                                                    │
│  • Armor reduction (vanilla formula)                                         │
│  • Enchantment protection                                                    │
│  • Resistance/Absorption effects                                             │
│  • Shield blocking                                                           │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        LivingDamageEvent.Post                                │
│                     (ActualDamageTracker.onDamagePost)                       │
│                          PRIORITY: LOWEST                                    │
│                                                                              │
│  1. Recupera danno REALE                                                     │
│     └─ actualDamage = event.getNewDamage()                                  │
│                                                                              │
│  2. Calcola health before/after                                              │
│     └─ healthBefore = healthAfter + actualDamage                            │
│                                                                              │
│  3. Aggiorna ImpactData                                                      │
│     └─ impact.setActualDamage(healthBefore, healthAfter, actualDamage)      │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           RENDERING LOOP                                     │
│                          (Every Frame)                                       │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                    ImpactHudOverlay.render()                         │    │
│  │                                                                      │    │
│  │  1. Check enabled && ImpactData.get() != null                       │    │
│  │  2. Calculate panel dimensions                                       │    │
│  │  3. Check crosshair over panel (observation)                        │    │
│  │  4. Update observation state                                         │    │
│  │  5. Get alpha from ImpactData.getRemainingAlpha()                   │    │
│  │  6. Render Impact Analysis panel                                     │    │
│  │  7. Render Mod Specifics panel (if applicable)                      │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │              Impact3DPanelManager.renderAllPanels()                  │    │
│  │                    (RenderLevelStageEvent)                           │    │
│  │                                                                      │    │
│  │  For each panel in activePanels:                                    │    │
│  │    1. Distance check (skip if > 64 blocks)                          │    │
│  │    2. panel.render() → Impact3DRenderer.renderPanel()               │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                      ImpactVFX.render()                              │    │
│  │                    (RenderLevelStageEvent)                           │    │
│  │                                                                      │    │
│  │  For each effect in activeEffects:                                  │    │
│  │    1. Render Energy Vortex                                          │    │
│  │    2. Render Slash Animation                                         │    │
│  │    3. Render Connection Lines                                        │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Dipendenze Esterne

### 3.1 Mod Integration

#### Pehkui
```java
// ModIntegrationManager.java
public static Float getPehkuiScale(LivingEntity entity) {
    // Ritorna scala visiva dell'entità
}

public static Float getPehkuiHitboxScale(LivingEntity entity) {
    // Ritorna scala hitbox dell'entità
}
```

#### Better Combat
- Rilevamento attacchi arc collision
- Nome attacco per display

### 3.2 Minecraft/NeoForge APIs

| API | Utilizzo |
|-----|----------|
| `LivingIncomingDamageEvent` | Intercettazione danno pre-riduzione |
| `LivingDamageEvent.Post` | Cattura danno post-riduzione |
| `AttackEntityEvent` | Rilevamento attacco (per evasion) |
| `RegisterGuiLayersEvent` | Registrazione HUD overlay |
| `RenderLevelStageEvent` | Rendering 3D nel mondo |
| `DataComponents.ENCHANTMENTS` | Lettura enchantment arma |
| `Attributes.ENTITY_INTERACTION_RANGE` | Reach dinamico |

---

## 4. Thread Safety

### Strutture Thread-Safe

| Struttura | Tipo | Utilizzo |
|-----------|------|----------|
| `IMPACTS_BY_PLAYER` | ConcurrentHashMap | Storage ImpactData |
| `activePanels` | CopyOnWriteArrayList | Lista pannelli 3D |
| `activeEffects` | CopyOnWriteArrayList | Lista effetti VFX |
| `BODY_PART_CACHE` | ConcurrentHashMap | Cache body part |

### Volatile Fields

```java
// ImpactData.java
private volatile long stoppedLookingTimestamp = -1;
private volatile boolean isBeingObserved = false;
private volatile float actualDamageDealt = -1f;
private volatile float healthBefore = -1f;
private volatile float healthAfter = -1f;
```

### Sincronizzazione Evasion Detection

```java
// DamageHandler.java
private static final Object EVASION_LOCK = new Object();

private static void scheduleEvasionCheck(...) {
    EVASION_SCHEDULER.schedule(() -> {
        synchronized (EVASION_LOCK) {
            // Check atomico pending/confirmed
        }
    }, 150, TimeUnit.MILLISECONDS);
}

private static void confirmHit(LivingEntity target) {
    synchronized (EVASION_LOCK) {
        confirmedHits.put(target.getId(), System.currentTimeMillis());
    }
}
```
