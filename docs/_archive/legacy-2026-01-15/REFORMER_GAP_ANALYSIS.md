# REFORMER GAP ANALYSIS - DevMod vs Oritech

**Data:** 2026-01-11
**Scopo:** Valutare i gap architetturali tra il Reformer di DevMod e i modelli equivalenti di Oritech

---

## 1. PANORAMICA ARCHITETTURALE

### DevMod Reformer - Stato Attuale
| Componente | File | Linee | Tecnologia |
|------------|------|-------|------------|
| Block | `ReformerBlock.java` | 146 | HorizontalDirectionalBlock |
| BlockEntity | `ReformerBlockEntity.java` | 314 | Custom tick logic |
| Model | `models/block/reformer.json` | 125 | Minecraft JSON (1.21.6) |
| Blockstate | `blockstates/reformer.json` | 17 | Solo FACING (4 varianti) |
| Renderer | **ASSENTE** | - | - |
| GUI/Menu | **ASSENTE** | - | - |

### Oritech SpawnerController - Reference
| Componente | File | Linee | Tecnologia |
|------------|------|-------|------------|
| Block | Simile architettura | ~150 | BlockWithEntity |
| BlockEntity | `SpawnerControllerBlockEntity.java` | ~300 | renderedEntity sync |
| Model | `*.geo.json` | 400-500 | GeckoLib Bedrock format |
| Blockstate | Definisce ACTIVE state | 8+ | FACING × ACTIVE |
| Renderer | `SpawnerControllerRenderer.java` | 56 | BlockEntityRenderer |
| GUI | Presente | - | ContainerScreen |

---

## 2. FORMATO MODELLI - CONFRONTO TECNICO

### DevMod - Minecraft Standard JSON

```json
// reformer.json - 125 linee
{
  "format_version": "1.21.6",
  "texture_size": [128, 128],
  "textures": {
    "0": "devmod:block/reformer",
    "particle": "devmod:block/reformer"
  },
  "elements": [
    {
      "from": [1, 3, 1],
      "to": [15, 7, 15],
      "faces": { /* UV mapping per faccia */ }
    }
    // ... altri elementi flat
  ]
}
```

**Caratteristiche:**
- Elementi flat senza gerarchia
- No supporto animazioni
- No bone/pivot system
- Texture singola
- Editabile in BlockBench (Java Edition mode)

### Oritech - GeckoLib Bedrock Format

```json
// centrifuge_block.geo.json - 457 linee
{
  "format_version": "1.12.0",
  "minecraft:geometry": [{
    "description": {
      "identifier": "geometry.unknown",
      "texture_width": 128,
      "texture_height": 128
    },
    "bones": [
      {
        "name": "base_machine",
        "pivot": [5, -4, 0],
        "cubes": [...]
      },
      {
        "name": "top",
        "pivot": [0, 29, 0],
        "parent": "base_machine",  // GERARCHIA
        "cubes": [...]
      },
      {
        "name": "glasses",
        "parent": "top",
        "pivot": [0, 27, 0],
        "rotation": [0, -60, 0]  // ANIMABILE
      }
    ]
  }]
}
```

**Caratteristiche:**
- Sistema bones gerarchico (parent/child)
- Pivot points per rotazioni animate
- Supporto animazioni GeckoLib
- Texture multiple + glowmask (`*_glowmask.png`)
- Editabile in BlockBench (Bedrock mode)

---

## 3. SISTEMA RENDERING

### DevMod Neurocell (Reference interno esistente)

```java
// NeurocellRenderer.java - 246 linee
public class NeurocellRenderer implements BlockEntityRenderer<NeurocellBlockEntity> {

    private final EntityRenderDispatcher entityRenderer;
    private final Map<String, Entity> entityCache;

    @Override
    public void render(NeurocellBlockEntity blockEntity, ...) {
        // LOD system: billboard > 16 blocks
        if (distSq > LOD_BILLBOARD_DISTANCE_SQ) {
            BillboardBatcher.getInstance().addBillboard(...);
            return;
        }

        // Full entity render
        Entity entity = entityCache.get(entityTypeString);
        if (entity == null) {
            entity = type.create(clientLevel);
            entity.noPhysics = true;
            entity.setNoGravity(true);
            entityCache.put(entityTypeString, entity);
        }

        // Render entity model
        entityRenderer.render(entity, 0.0, 0.0, 0.0, 0.0f, partialTick, poseStack, buffer, LightTexture.FULL_BRIGHT);

        // Energy effects
        renderEnergyEffectsVBO(poseStack, animTime);
    }
}
```

### Oritech SpawnerControllerRenderer

```java
// SpawnerControllerRenderer.java - 56 linee
public class SpawnerControllerRenderer implements BlockEntityRenderer<SpawnerControllerBlockEntity> {

    @Override
    public void render(SpawnerControllerBlockEntity entity, ...) {
        if (entity.renderedEntity != null && entity.hasCage) {

            // Progress-based color tinting
            var progress = Math.min(1f, entity.collectedSouls / (float) entity.maxSouls);
            var color = FastColor.ARGB32.color(
                (int) (75 + 180 * progress),      // Alpha
                (int) (255 * (1f - progress)),    // Red fades
                255,                               // Green
                255                                // Blue
            );

            // Render entity model with tint
            if (renderer instanceof LivingEntityRenderer) {
                var model = livingEntityRenderer.getModel();
                var renderLayer = RenderType.beaconBeam(...);
                model.renderToBuffer(matrices, vertexConsumer, light, overlay, color);
            }
        }
    }
}
```

### DevMod Reformer (ASSENTE)

```java
// NON ESISTE - ReformerRenderer.java
// Il Reformer usa SOLO:
// - serverTick() per particelle
// - Nessun rendering client-side dell'entita' in spawn
```

---

## 4. GAP CRITICI IDENTIFICATI

### GAP 1: Blockstate Incompleto
**Problema:** Il blockstate ignora la proprieta' ACTIVE definita nel Block

```json
// ATTUALE - reformer.json (blockstates)
{
  "variants": {
    "facing=north": { "model": "devmod:block/reformer" },
    "facing=south": { "model": "devmod:block/reformer", "y": 180 },
    "facing=west": { "model": "devmod:block/reformer", "y": 270 },
    "facing=east": { "model": "devmod:block/reformer", "y": 90 }
  }
}
// MANCANO: facing=north,active=true, etc. (8 varianti totali)
```

**Impatto:** Il blocco non cambia aspetto quando attivo
**Fix Required:** Aggiungere varianti ACTIVE o usare model predicate

---

### GAP 2: Nessun BlockEntityRenderer
**Problema:** Il Reformer spawna entita' ma non mostra preview

| Feature | Neurocell | Oritech Spawner | Reformer |
|---------|-----------|-----------------|----------|
| Entity preview | SI | SI | NO |
| Progress visual | SI (growth) | SI (color tint) | NO |
| Energy effects | SI (VBO) | SI | NO |
| LOD system | SI | - | N/A |

**Impatto:** L'utente non vede cosa sta per spawnare
**Fix Required:** Creare `ReformerRenderer.java` basato su NeurocellRenderer

---

### GAP 3: No GUI/Menu
**Problema:** Nessuna interfaccia per visualizzare stato/progresso

| Block | Menu Class | Screen Class |
|-------|------------|--------------|
| Neurocell | NeurocellMenu | NeurocellScreen |
| NeurocellL | NeurocellLMenu | NeurocellLScreen |
| Centrifuge | CentrifugeMenu | CentrifugeScreen |
| Reformer | **ASSENTE** | **ASSENTE** |

**Impatto:** Impossibile vedere progresso spawn senza guardare particelle
**Fix Required:** Creare ReformerMenu + ReformerScreen

---

### GAP 4: No onRemove() per Cleanup
**Problema:** Il Reformer non ha logica di cleanup quando rimosso

```java
// CentrifugeBlock.java - HA onRemove()
@Override
protected void onRemove(BlockState state, Level level, BlockPos pos,
                        BlockState newState, boolean isMoving) {
    if (!state.is(newState.getBlock())) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof CentrifugeBlockEntity centrifuge) {
            Containers.dropContents(level, pos, centrifuge.getInventory());
        }
    }
    super.onRemove(state, level, pos, newState, isMoving);
}

// ReformerBlock.java - NON HA onRemove()
// Potenziale leak di pendingData/pendingEntityName se rimosso durante spawn
```

---

### GAP 5: Modello Statico vs Animato
| Aspetto | DevMod | Oritech |
|---------|--------|---------|
| Format | Minecraft JSON | GeckoLib .geo.json |
| Bones | No | Si (gerarchici) |
| Animazioni | No | Si (idle, working) |
| Glowmask | No | Si (`*_glowmask.png`) |
| Complessita' | ~125 linee | ~450 linee |

**Impatto:** Modelli DevMod sono statici e meno dettagliati
**Nota:** Questo potrebbe essere intenzionale per stile/performance

---

## 5. RACCOMANDAZIONI

### Priorita' ALTA (Funzionalita')
1. **Creare ReformerRenderer.java** - basato su NeurocellRenderer esistente
   - Mostrare preview dell'entita' in spawn
   - Progress bar visuale con scale/color
   - Riutilizzare entityCache pattern

2. **Fixare Blockstate** - aggiungere varianti ACTIVE
   - Opzione A: 8 varianti complete (facing x active)
   - Opzione B: Usare `active` model con texture emissive

3. **Aggiungere onRemove()** - cleanup pendingData

### Priorita' MEDIA (UX)
4. **Creare GUI Reformer** - ReformerMenu + ReformerScreen
   - Mostrare: entita' target, progresso, tempo rimanente
   - Seguire pattern Centrifuge esistente

### Priorita' BASSA (Estetica)
5. **Valutare GeckoLib** - per modelli animati
   - Pro: Animazioni fluide, glowmask
   - Contro: Dipendenza extra, modelli da rifare
   - Decisione: Dipende dalla direzione artistica mod

---

## 6. DIFFERENZE FILOSOFICHE

| Aspetto | DevMod | Oritech |
|---------|--------|---------|
| Dipendenze | Minimal | GeckoLib required |
| Complessita' | Semplice | Complessa |
| Animazioni | Particelle + entity render | Full bone animation |
| LOD | Implementato (Neurocell) | Non osservato |
| Modelli | ~100 linee | ~450 linee |

DevMod sembra preferire un approccio piu' leggero con meno dipendenze esterne. La decisione di adottare GeckoLib dovrebbe essere presa considerando:
- Coerenza con resto della mod
- Complessita' aggiunta
- Skill del team per modeling Bedrock

---

## 7. FILE DI REFERENCE

### DevMod (da leggere)
- `src/main/java/com/devmod/clone/block/ReformerBlock.java`
- `src/main/java/com/devmod/clone/block/entity/ReformerBlockEntity.java`
- `src/main/java/com/devmod/clone/client/renderer/NeurocellRenderer.java` (template)
- `src/main/java/com/devmod/clone/client/CloneClientSetup.java`

### Oritech (reference)
- `oritech-reference/common/src/main/java/rearth/oritech/client/renderers/SpawnerControllerRenderer.java`
- `oritech-reference/common/src/main/java/rearth/oritech/client/renderers/MachineRenderer.java`
- `oritech-reference/common/src/main/resources/assets/oritech/geo/block/models/*.geo.json`

---

*Documento generato automaticamente - Gap Analysis Reformer DevMod vs Oritech*
