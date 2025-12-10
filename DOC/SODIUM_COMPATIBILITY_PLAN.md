# DevMod - Piano di Compatibilita con la Suite Sodium

## Stato Attuale delle Mod Disabilitate

Le seguenti mod della suite Sodium sono presenti in `run/mods/disabled_mods/`:

| Mod | Versione | Repository | Priorita |
|-----|----------|------------|----------|
| **Sodium** | 0.6.13+mc1.21.1 | [Modrinth](https://modrinth.com/mod/sodium) / [GitHub](https://github.com/CaffeineMC/sodium) | CRITICA |
| **Lithium** | 0.15.0+mc1.21.1 | [Modrinth](https://modrinth.com/mod/lithium) / [GitHub](https://github.com/CaffeineMC/lithium) | ALTA |
| **Iris** | 1.8.8+mc1.21.1 | [Modrinth](https://modrinth.com/mod/iris) / [GitHub](https://github.com/IrisShaders/Iris) | ALTA |
| **ImmediatelyFast** | 1.6.7+1.21.1 | [Modrinth](https://modrinth.com/mod/immediatelyfast) / [GitHub](https://github.com/RaphiMC/ImmediatelyFast) | ALTA |
| **EntityCulling** | 1.9.3-mc1.21.1 | [Modrinth](https://modrinth.com/mod/entityculling) / [GitHub](https://github.com/tr7zw/EntityCulling) | MEDIA |
| **FerriteCore** | 7.0.2 | [Modrinth](https://modrinth.com/mod/ferrite-core) / [GitHub](https://github.com/malte0811/FerriteCore) | MEDIA |
| **MoreCulling** | 1.0.6-1.21.1 | [Modrinth](https://modrinth.com/mod/moreculling) | MEDIA |
| **Dynamic FPS** | 3.9.5 | [Modrinth](https://modrinth.com/mod/dynamic-fps) | BASSA |
| **BadOptimizations** | 2.3.1 | [Modrinth](https://modrinth.com/mod/badoptimizations) | BASSA |
| **C2ME** | 0.3.0+alpha.0.87 | [Modrinth](https://modrinth.com/mod/c2me-fabric) / [GitHub](https://github.com/RelativityMC/C2ME-fabric) | MEDIA |
| **Reese's Sodium Options** | 1.8.3+mc1.21.4 | [Modrinth](https://modrinth.com/mod/reeses-sodium-options) | BASSA |
| **Sodium Options API** | 1.0.10 | [Modrinth](https://modrinth.com/mod/sodium-options-api) | BASSA |
| **Sodium Options Mod Compat** | 1.0.0 | [Modrinth](https://modrinth.com/mod/sodium-options-mod-compat) | BASSA |
| **Sodium Dynamic Lights** | 1.0.10 | [Modrinth](https://modrinth.com/mod/sodiumdynamiclights) | MEDIA |
| **Sodium Leaf Culling** | 1.0.1 | [Modrinth](https://modrinth.com/mod/sodiumleafculling) | BASSA |
| **Sodium Core Shader Support** | 1.3.4 | [Modrinth](https://modrinth.com/mod/sodiumcoreshadersupport) | MEDIA |

---

## Analisi dei Potenziali Conflitti con DevMod

### 1. Rendering System (CRITICO)

**File DevMod coinvolti:**
- `WorldRenderEvents.java` - Usa `RenderType.lines()`, `RenderType.debugQuads()`
- `CustomRenderTypes.java` - RenderType custom per overlay trasparenti
- `rendering/*.java` - 18 visualizers che usano `VertexConsumer`
- `hud/*.java` - HUD overlays con rendering diretto

**Potenziali conflitti con Sodium:**
- Sodium ottimizza il chunk rendering con buffer batching
- I nostri `RenderType` custom potrebbero non essere riconosciuti
- Il rendering delle linee debug potrebbe essere bypassato

**Potenziali conflitti con ImmediatelyFast:**
- Il batching HUD di ImmediatelyFast puo interferire con i nostri overlay
- La config `hud_batching: true` potrebbe causare glitch visivi
- Dal 1.21.2+ l'API di batching e deprecata (usare `DrawContext`)

**Potenziali conflitti con Iris:**
- Gli shader possono modificare come vengono renderizzati i nostri elementi debug
- I `RenderType` custom potrebbero non essere supportati da tutti gli shader pack

### 2. Entity Rendering

**File DevMod coinvolti:**
- `MobDebugOverlay.java` - Info sopra i mob
- `BodyPartRenderer.java` - Hitbox parti del corpo
- `AggroRangeVisualizer.java` - Sfere di range

**Potenziali conflitti con EntityCulling:**
- Le entita cullate non attiveranno i nostri render events
- I nostri visualizer potrebbero non vedere entita "invisibili" al culling
- Necessario whitelist per entita con rendering custom

### 3. Mixin Conflicts

**DevMod Mixins:**
- `GameRendererMixin.java` - Intercetta `processBlurEffect`

**Potenziali conflitti:**
- Lithium ha mixin aggressivi su GameRenderer
- Sodium modifica pesantemente il rendering pipeline
- Iris intercetta molti punti del GameRenderer

---

## Piano di Implementazione

### FASE 1: Compatibilita Base Sodium (Priorita CRITICA)

#### 1.1 Verifica RenderType Compatibility
```java
// Aggiungere in CustomRenderTypes.java
public static boolean isSodiumPresent() {
    try {
        Class.forName("net.caffeinemc.mods.sodium.client.SodiumClientMod");
        return true;
    } catch (ClassNotFoundException e) {
        return false;
    }
}
```

#### 1.2 Usare RenderLevelStageEvent correttamente
Il nostro uso di `RenderLevelStageEvent.Stage.AFTER_ENTITIES` e gia corretto per Sodium.

#### 1.3 Test Items
- [ ] Abilitare Sodium e verificare che tutti i visualizer funzionino
- [ ] Verificare che le linee debug siano visibili
- [ ] Verificare che le hitbox siano renderizzate correttamente
- [ ] Controllare che gli overlay HUD non flickerino

### FASE 2: Compatibilita ImmediatelyFast (Priorita ALTA)

#### 2.1 Evitare conflitti con HUD Batching
```java
// In tutti gli HUD overlay, usare DrawContext invece di rendering diretto
// Questo e il modo raccomandato da ImmediatelyFast per 1.21+
```

#### 2.2 Configurazione raccomandata
Creare un file di configurazione che suggerisce:
```json
{
  "hud_batching": true,
  "experimental_screen_batching": false  // Disabilitare se conflitti
}
```

#### 2.3 Test Items
- [ ] Testare tutti gli HUD overlay con ImmediatelyFast attivo
- [ ] Verificare che FpsTracker e PerformanceProfiler non flickerino
- [ ] Testare RadialMenuScreen con screen batching

### FASE 3: Compatibilita Iris Shaders (Priorita ALTA)

#### 3.1 Shader-Safe Rendering
I nostri visualizer debug dovrebbero essere visibili anche con shader attivi:
```java
// Considerare l'uso di RenderType che bypassano gli shader
// O aggiungere supporto per il tag system di Iris
```

#### 3.2 Test con Shader Pack popolari
- [ ] Testare con Complementary Shaders
- [ ] Testare con BSL Shaders
- [ ] Verificare visibilita linee debug
- [ ] Verificare visibilita overlay trasparenti

### FASE 4: Compatibilita EntityCulling (Priorita MEDIA)

#### 4.1 Rilevare entita cullate
```java
// Aggiungere check per entita visibili al culling
public static boolean isEntityVisible(Entity entity) {
    // Se EntityCulling e presente, usare la sua API
    // Altrimenti, sempre true
}
```

#### 4.2 Whitelist automatica (opzionale)
Considerare l'aggiunta automatica alla whitelist di EntityCulling per:
- Entita con overlay attivi
- Entita tracciate da AttributeMonitoringSystem
- Mob target di PathfindingDebugger

#### 4.3 Test Items
- [ ] Verificare che MobDebugOverlay funzioni su mob cullati
- [ ] Testare BodyPartRenderer con culling attivo
- [ ] Verificare AggroRangeVisualizer

### FASE 5: Compatibilita Lithium (Priorita MEDIA)

#### 5.1 Verificare Mixin Compatibility
Il nostro `GameRendererMixin` e semplice e non dovrebbe confliggere, ma:
- [ ] Testare con Lithium attivo
- [ ] Verificare che il blur skip funzioni ancora
- [ ] Controllare log per warning mixin

#### 5.2 Configurazione Lithium
Se necessario, documentare quali ottimizzazioni Lithium disabilitare:
```properties
# lithium.properties - se necessario
mixin.entity.fast_retrieval=false  # Solo se conflitti con entity tracking
```

### FASE 6: Integrazione Avanzata (Priorita BASSA)

#### 6.1 Sodium Options API Integration
Aggiungere pannello DevMod nelle opzioni Sodium:
```java
// Usare SodiumOptionsAPI per aggiungere sezione "DevMod Debug"
// Toggle per: Debug Overlay, Body Part Boxes, etc.
```

#### 6.2 Performance Metrics Integration
- Leggere metriche da Sodium per il nostro PerformanceProfiler
- Integrare chunk stats nel ChunkPerformanceVisualizer

#### 6.3 Dynamic Lights Awareness
- Rilevare SodiumDynamicLights per evitare calcoli duplicati
- Usare le sue API per light level se disponibili

---

## Matrice di Test

| Mod Combo | Priorita | Status |
|-----------|----------|--------|
| DevMod + Sodium | CRITICA | DA TESTARE |
| DevMod + Sodium + Iris | ALTA | DA TESTARE |
| DevMod + Sodium + ImmediatelyFast | ALTA | DA TESTARE |
| DevMod + Lithium | MEDIA | DA TESTARE |
| DevMod + EntityCulling | MEDIA | DA TESTARE |
| DevMod + FerriteCore | MEDIA | DA TESTARE |
| DevMod + Full Stack (tutte) | CRITICA | DA TESTARE |

---

## Implementazione Suggerita

### File da creare/modificare:

1. **`integration/SodiumCompat.java`** (NUOVO)
   - Detection delle mod Sodium-suite
   - Helper per rendering compatibile
   - Fallback per quando Sodium non e presente

2. **`integration/ImmediatelyFastCompat.java`** (NUOVO)
   - Wrapper per HUD rendering
   - Gestione batching compatibility

3. **`integration/EntityCullingCompat.java`** (NUOVO)
   - Whitelist management
   - Visibility checks

4. **`ModConfig.java`** (MODIFICA)
   - Aggiungere opzioni per compatibility mode
   - Toggle per disabilitare feature problematiche

5. **`rendering/DebugRenderer.java`** (MODIFICA)
   - Aggiungere fallback RenderType per Sodium
   - Ottimizzare per batching

---

## Timeline Suggerita

| Fase | Durata Stimata | Dipendenze |
|------|----------------|------------|
| FASE 1 (Sodium base) | 2-3 sessioni | Nessuna |
| FASE 2 (ImmediatelyFast) | 1-2 sessioni | FASE 1 |
| FASE 3 (Iris) | 2-3 sessioni | FASE 1 |
| FASE 4 (EntityCulling) | 1-2 sessioni | FASE 1 |
| FASE 5 (Lithium) | 1 sessione | FASE 1 |
| FASE 6 (Integrazione) | 3-4 sessioni | FASE 1-5 |

---

## Risorse e Documentazione

### Repository Ufficiali
- Sodium: https://github.com/CaffeineMC/sodium
- Lithium: https://github.com/CaffeineMC/lithium
- Iris: https://github.com/IrisShaders/Iris
- ImmediatelyFast: https://github.com/RaphiMC/ImmediatelyFast
- EntityCulling: https://github.com/tr7zw/EntityCulling
- FerriteCore: https://github.com/malte0811/FerriteCore

### Documentazione API
- Sodium Options API: https://modrinth.com/mod/sodium-options-api
- Lithium Mixin Config: https://github.com/CaffeineMC/lithium/blob/develop/lithium-neoforge-mixin-config.md
- ImmediatelyFast Config: Vedere file `immediatelyfast.json` nella cartella config

### Note Tecniche
- Dal 1.21.2+ ImmediatelyFast batching API deprecata, usare DrawContext
- Sodium 0.6.x supporta FRAPI out of the box
- Lithium puo avere conflitti con FerriteCore (risolti in versioni recenti)
- EntityCulling usa path-tracing async, considerare per performance

---

## Conclusioni

La DevMod usa principalmente:
1. **RenderLevelStageEvent** - Compatibile con Sodium
2. **RenderType standard** (lines, debugQuads) - Generalmente compatibili
3. **Un solo Mixin** su GameRenderer - Basso rischio conflitto

**Raccomandazione**: Iniziare abilitando solo Sodium e testare tutti i visualizer.
Se funziona, aggiungere progressivamente le altre mod seguendo le fasi.

La maggior parte dei potenziali problemi sono prevedibili e risolvibili con
piccole modifiche al codice di rendering esistente.
