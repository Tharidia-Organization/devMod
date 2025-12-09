# Analisi Integrazione Perception Mod con DevMod

## Panoramica Perception Mod

**Repository:** [Octo-Studios/perception](https://github.com/Octo-Studios/perception)
**Versione:** 0.1.6
**Minecraft:** 1.21.1
**Loader:** NeoForge 21.1.90 / Fabric

### Descrizione
Perception e una mod che arricchisce il mondo con effetti visivi immersivi:
- **Projectile Trails**: Scie luminose dietro proiettili (frecce, pozioni, trident, fireball, elytra)
- **Screen Shake**: Tremori direzionali della camera basati su suoni, esplosioni, movimenti

### Dipendenze
- **OctoLib** 0.6.0.1+1.21 (libreria condivisa Octo-Studios)
- **Architectury API** 13.0.6 (multi-loader support)

---

## Architettura Tecnica Perception

### Struttura Package
```
it.hurts.octostudios.perception.common/
├── Perception.java              # Entry point
├── PerceptionClient.java        # Client init, registra trail providers
├── config/                      # Config registry
├── init/                        # Initialization
├── misc/                        # Utilities
├── mixin/
│   ├── shakes/
│   │   ├── CameraMixin.java         # Applica shake alla camera
│   │   ├── GameRendererMixin.java   # Tick update degli shake
│   │   ├── PlayerMixin.java         # Player shake handling
│   │   ├── ClientLevelMixin.java    # World-level shake
│   │   └── AbstractClientPlayerMixin.java
│   └── trails/
│       └── particle/            # Trail particle rendering
└── modules/
    ├── base/config/             # Base config
    ├── shake/
    │   ├── Shake.java           # Core shake system (17KB!)
    │   ├── ShakeManager.java    # Gestisce shake attivi
    │   ├── config/              # Shake config
    │   └── data/                # Shake data
    └── trail/
        ├── config/              # Trail config
        └── misc/
            ├── TrailProviderFactory.java
            └── wrapper/         # Entity-specific wrappers
```

---

## Analisi Sistema Screen Shake

### Come Funziona

1. **ShakeManager** mantiene una `Map<UUID, Shake>` di effetti attivi

2. **GameRendererMixin** fa tick update ogni frame:
```java
@Inject(method = "tick", at = @At("HEAD"))
private void onTick(CallbackInfo ci) {
    Iterator<Shake> iterator = ShakeManager.SHAKES.values().iterator();
    while (iterator.hasNext()) {
        Shake effect = iterator.next();
        effect.update(player);
        if (effect.isFinished())
            iterator.remove();
    }
}
```

3. **CameraMixin** applica lo shake alla camera:
```java
@Inject(method = "setup", at = @At("TAIL"))
private void applyShake(BlockGetter level, Entity entity, ...) {
    for (var effect : ShakeManager.SHAKES.values()) {
        shakeRotation.add(effect.getShakeRotation(player, partialTicks));
        shakeOffset.add(effect.getShakeOffset(player, partialTicks));
    }
    // Trasforma in coordinate camera-relative
    // Applica rotazione e offset
}
```

### Classe Shake - Features

La classe `Shake.java` (17KB) e molto sofisticata:

- **3 tipi di effetto**: rotazione (pitch/yaw/roll), offset posizionale, FOV
- **Curve di easing** configurabili per fade-in/out
- **Attenuazione per distanza** dalla sorgente
- **Builder pattern** per configurazione flessibile
- **Sorgenti multiple**: Entity, Position, Custom
- **Interpolazione smooth** tra tick

```java
// Esempio API (dedotta dalla struttura)
Shake.builder()
    .amplitude(10f)
    .frequency(5f)
    .duration(40) // ticks
    .fadeIn(10)
    .fadeOut(10)
    .source(entity)
    .rangeMultiplier(32f)
    .build();
```

---

## Analisi Sistema Trail

### Come Funziona

1. **TrailProviderFactory** crea wrapper specifici per tipo entita:
```java
switch (entity) {
    case Arrow -> new ArrowTrailWrapper(entity, data);
    case ExperienceOrb -> new ExperienceOrbTrailWrapper(entity, data);
    case ThrownPotion -> new ThrownPotionTrailWrapper(entity, data);
    case FireworkRocketEntity -> new FireworkRocketTrailWrapper(entity, data);
    default -> new TrailWrapper<>(entity, data);
}
```

2. **EntityTrailRegistry** mantiene i provider registrati

3. I trail sono renderizzati come linee/particelle che seguono l'entita

---

## Valutazione Integrazione con DevMod

### Opzioni di Integrazione

#### OPZIONE A: Dipendenza Soft (Raccomandato)
Rilevare Perception se presente e usare le sue API.

**Pro:**
- Nessun codice duplicato
- Utenti con Perception hanno effetti gia configurati
- Manteniamo compatibilita senza Perception

**Contro:**
- Dipendenza da API esterna (potrebbe cambiare)
- Non disponibile se utente non ha Perception

#### OPZIONE B: Implementazione Indipendente
Reimplementare sistemi simili internamente.

**Pro:**
- Controllo totale
- Nessuna dipendenza esterna
- Personalizzato per le nostre esigenze

**Contro:**
- Codice duplicato se utente ha anche Perception
- Piu lavoro di manutenzione
- Possibili conflitti mixin

#### OPZIONE C: Ibrido (Migliore)
Implementare sistema interno con fallback/integrazione Perception.

---

## Piano di Integrazione Raccomandato

### FASE 1: Screen Shake per Combat Feedback (Alta Priorita)

Il nostro sistema di combat (HitHelper, DamageHandler) beneficerebbe enormemente di screen shake:

```java
// integration/PerceptionCompat.java
public class PerceptionCompat {
    private static final boolean PERCEPTION_LOADED = isLoaded();

    private static boolean isLoaded() {
        try {
            Class.forName("it.hurts.octostudios.perception.common.modules.shake.ShakeManager");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static void triggerHitShake(Entity target, float damage) {
        if (PERCEPTION_LOADED) {
            // Usa API Perception
            triggerHitShakePerception(target, damage);
        } else {
            // Fallback interno
            triggerHitShakeInternal(target, damage);
        }
    }
}
```

**Use Cases DevMod:**
- Colpo critico subito → shake forte
- Colpo normale → shake leggero
- Esplosione vicina → shake direzionale
- Boss phase change → shake drammatico

### FASE 2: Combat Trail Effects (Media Priorita)

Sfruttare i trail per:
- Visualizzare traiettoria armi (swing trail)
- Mostrare direzione attacco nemico
- Trail su proiettili player per debug

```java
// Esempio: Trail su attacco mob per debugging
if (ModConfig.showAttackTrails && isPerceptionLoaded()) {
    // Registra trail temporaneo per arma mob
}
```

### FASE 3: Sistema Shake Interno (Bassa Priorita)

Se decidiamo di avere shake senza Perception:

```java
// devmod/effects/ScreenShake.java
public class ScreenShake {
    private static final Map<UUID, ShakeEffect> ACTIVE_SHAKES = new ConcurrentHashMap<>();

    public static void addShake(ShakeEffect effect) {
        ACTIVE_SHAKES.put(UUID.randomUUID(), effect);
    }

    // Chiamato da nostro mixin (se non c'e Perception)
    public static Vec3 getShakeOffset(float partialTicks) {
        Vec3 total = Vec3.ZERO;
        for (ShakeEffect shake : ACTIVE_SHAKES.values()) {
            total = total.add(shake.getOffset(partialTicks));
        }
        return total;
    }
}
```

---

## Conflitti Potenziali

### Mixin Conflicts

Perception usa mixin su:
- `GameRenderer.tick()` - **CONFLITTO POTENZIALE** con nostro `GameRendererMixin`
- `Camera.setup()` - Noi non lo usiamo, OK

**Nostro GameRendererMixin:**
```java
@Inject(method = "processBlurEffect", at = @At("HEAD"), cancellable = true)
private void devmod$skipBlurForModScreens(...)
```

**Perception GameRendererMixin:**
```java
@Inject(method = "tick", at = @At("HEAD"))
private void onTick(CallbackInfo ci)
```

**Nessun conflitto diretto** - usiamo metodi diversi!

### Rendering Conflicts

I trail di Perception potrebbero interferire con:
- Nostri visualizer di proiettili
- Debug overlay su frecce/proiettili

**Soluzione:** Aggiungere config per disabilitare nostri visualizer quando Perception e attivo.

---

## Implementazione Suggerita

### File da Creare

1. **`integration/perception/PerceptionCompat.java`**
   - Detection e API wrapper
   - Metodi per triggerare shake

2. **`integration/perception/CombatShakeHandler.java`**
   - Logica shake per eventi combat
   - Configurazione intensita

3. **`effects/InternalScreenShake.java`** (opzionale)
   - Implementazione fallback senza Perception

### Modifiche Esistenti

1. **`DamageHandler.java`**
   - Aggiungere chiamate a PerceptionCompat per shake su danno

2. **`CombatEvents.java`**
   - Triggerare shake su eventi combat significativi

3. **`ModConfig.java`**
   - Aggiungere toggle per effetti screen shake
   - Slider per intensita shake

---

## Configurazione Suggerita

```toml
# devmod-common.toml

[effects]
    # Enable screen shake effects on combat
    enableCombatShake = true

    # Screen shake intensity multiplier (0.0 - 2.0)
    shakeIntensity = 1.0

    # Use Perception mod if available (recommended)
    usePerceptionIfAvailable = true

    # Show projectile trails in debug mode
    showProjectileTrails = false
```

---

## Conclusione

### Possiamo Sfruttare Perception? **SI, Assolutamente!**

**Benefici:**
1. **Screen Shake su Combat** - Feedback immersivo su colpi subiti/inflitti
2. **Trail per Debug** - Visualizzare traiettorie proiettili
3. **Boss Fight Enhancement** - Shake drammatici su fasi boss
4. **Zero conflitti mixin** - Usiamo metodi diversi

**Raccomandazione:**
1. Implementare **soft dependency** su Perception
2. Creare **wrapper API** per usare le sue funzionalita
3. Avere **fallback interno** per utenti senza Perception
4. Integrare shake nel nostro **sistema combat esistente**

### Effort Stimato
- FASE 1 (Shake Combat): 1-2 sessioni
- FASE 2 (Trail Debug): 1 sessione
- FASE 3 (Sistema interno): 2-3 sessioni (opzionale)

---

## Risorse

- [Perception GitHub](https://github.com/Octo-Studios/perception)
- [Perception Modrinth](https://modrinth.com/mod/perception)
- [Perception CurseForge](https://www.curseforge.com/minecraft/mc-mods/perception)
- [OctoLib GitHub](https://github.com/Octo-Studios/octo-lib)
- [Architectury API](https://github.com/architectury/architectury-api)
