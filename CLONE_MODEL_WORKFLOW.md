# Clone Module - GeckoLib Model Workflow

Documento di riferimento per la creazione dei modelli animati del modulo Clone usando GeckoLib 4.7.4 per NeoForge 1.21.1.

---

## Struttura File

```
src/main/resources/assets/devmod/
├── geo/
│   └── clone_pulverizer.geo.json      # Modello 3D (Bedrock format)
├── animations/
│   └── clone_pulverizer.animation.json # Animazioni
├── textures/block/clone/
│   └── clone_pulverizer.png           # Texture 64x64
└── blockstates/
    └── clone_pulverizer.json          # Blockstate (vuoto per GeckoLib)

src/main/java/com/devmod/clone/
├── block/
│   └── CloneMachineBlock.java         # Block class
├── block/entity/
│   └── CloneMachineBlockEntity.java   # BlockEntity con AnimatableBlockEntity
└── client/renderer/
    └── CloneMachineRenderer.java      # GeoBlockRenderer
```

---

## 1. Modello Geometrico (.geo.json)

### Struttura Base

```json
{
  "format_version": "1.12.0",
  "minecraft:geometry": [{
    "description": {
      "identifier": "geometry.clone_pulverizer",
      "texture_width": 64,
      "texture_height": 64,
      "visible_bounds_width": 2,
      "visible_bounds_height": 2,
      "visible_bounds_offset": [0, 0.5, 0]
    },
    "bones": [...]
  }]
}
```

### Gerarchia Bones (clone_pulverizer)

```
root (pivot: [8, 0, 8])
├── base_plate      # Piattaforma base - 14x1x14
├── core_lower      # Modulo macchinario - 12x3x12
├── core_upper      # Core energia - 10x2x10
├── support_nw      # Supporto nord-ovest - 3x5x3
├── support_ne      # Supporto nord-est - 3x5x3
├── support_sw      # Supporto sud-ovest - 3x5x3
└── support_se      # Supporto sud-est - 3x5x3
```

### Regole Pivot Points

- **Pivot al centro del cubo** per rotazioni corrette
- Formula: `pivot = origin + (size / 2)`
- Esempio per support_nw (origin [1,1,1], size [3,5,3]):
  - pivot = [1+1.5, 1+2.5, 1+1.5] = [2.5, 3.5, 2.5]

### UV Mapping Per-Face

```json
"uv": {
  "north": {"uv": [0, 16], "uv_size": [12, 3]},
  "south": {"uv": [0, 16], "uv_size": [12, 3]},
  "east":  {"uv": [0, 16], "uv_size": [12, 3]},
  "west":  {"uv": [0, 16], "uv_size": [12, 3]},
  "up":    {"uv": [16, 0], "uv_size": [16, 16]},
  "down":  {"uv": [16, 0], "uv_size": [16, 16]}
}
```

### Regole UV Automatiche

Formula per calcolare `uv_size` in base alla dimensione del cubo:

| Faccia | uv_size |
|--------|---------|
| North/South | `[size.x, size.y]` |
| East/West | `[size.z, size.y]` |
| Up/Down | `[size.x, size.z]` |

**Esempio**: Cubo con `size: [3, 5, 3]`
- North/South: `uv_size: [3, 5]`
- East/West: `uv_size: [3, 5]`
- Up/Down: `uv_size: [3, 3]`

### Template Bone Completo

Esempio di bone con TUTTI i campi espansi:

```json
{
  "name": "support_nw",
  "parent": "root",
  "pivot": [2.5, 3.5, 2.5],
  "cubes": [{
    "origin": [1, 1, 1],
    "size": [3, 5, 3],
    "uv": {
      "north": {"uv": [48, 0], "uv_size": [3, 5]},
      "south": {"uv": [51, 0], "uv_size": [3, 5]},
      "east": {"uv": [54, 0], "uv_size": [3, 5]},
      "west": {"uv": [57, 0], "uv_size": [3, 5]},
      "up": {"uv": [48, 5], "uv_size": [3, 3]},
      "down": {"uv": [48, 8], "uv_size": [3, 3]}
    }
  }]
}
```

**Campi Obbligatori**:
- `name`: identificativo univoco del bone
- `pivot`: punto di rotazione `[x, y, z]`
- `cubes`: array di cubi (può essere vuoto per bones di raggruppamento)

**Campi Opzionali**:
- `parent`: nome del bone genitore (default: nessuno/root)
- `rotation`: rotazione iniziale `[rx, ry, rz]` in gradi

---

## 2. Animazioni (.animation.json)

### Struttura Base

```json
{
  "format_version": "1.8.0",
  "animations": {
    "animation.clone_pulverizer.deploy": {
      "loop": false,
      "animation_length": 4.0,
      "bones": {...}
    },
    "animation.clone_pulverizer.active": {
      "loop": true,
      "animation_length": 2.0,
      "bones": {...}
    }
  }
}
```

### Animazione Deploy (Transformer Style)

**Concetto**: La macchina si assembla dal centro quando viene piazzata.

**Timeline 4 secondi**:
1. `0.0s` - Base plate cade dall'alto (Y=6 → Y=0)
2. `0.4s` - Core lower emerge dal basso (Y=-4 → Y=0)
3. `0.8s` - Core upper sale in posizione (Y=-3 → Y=0)
4. `1.4s-2.6s` - Supporti si dispiegano uno alla volta (ogni 0.4s)

**Regole Animazione Meccanica**:
- NO scaling (solo position e rotation)
- Movimenti lineari con stop bruschi
- Supporti partono attaccati alla base (Y=0), non sospesi
- Rotazioni da 80° a 0° per effetto "dispiegamento"

### Keyframes Supporti

```json
"support_nw": {
  "position": {
    "0.0":  [3, 0, 3],      // Partenza: attaccato al centro
    "1.4":  [3, 0, 3],      // Attesa
    "1.8":  [1, 0, 1],      // Movimento verso angolo
    "2.0":  [0, 0, 0]       // Posizione finale
  },
  "rotation": {
    "0.0":  [80, 90, 0],    // Piegato verso centro
    "1.4":  [80, 90, 0],    // Attesa
    "1.8":  [40, 45, 0],    // Apertura
    "2.0":  [0, 0, 0]       // Dritto
  }
}
```

---

## 3. Texture (.png 64x64)

### Obiettivo

Creare texture in stile Minecraft tecnico, coerente con Neural Cellar e Clone Pulverizer.

### Riferimenti Palette/Stile (OBBLIGATORI)

Prima di creare qualsiasi texture, campiona colori SOLO da questi file:

```text
src/main/resources/assets/devmod/textures/block/neurocell_base.png   # Base metallica
src/main/resources/assets/devmod/textures/block/neurocell_frame.png  # Frame strutturale
src/main/resources/assets/devmod/textures/block/neurocell_glass.png  # Teal/glow (opzionale)
src/main/resources/assets/devmod/textures/block/clone/clone_pulverizer.png  # Riferimento iron/rubber
```

**REGOLA VINCOLANTE**: Usa SOLO colori presenti nei file sopra. Non inventare colori fuori palette.

### Palette Neural Cellar Completa

#### Dark/Steel (corpo principale)

```text
#141C2A  #17202F  #1B2434  #1F2839  #232D3D  #252E3D  #282A32  #283242
#2A3547  #2D3747  #2F3A4B  #32353D  #343F51  #374151  #393D47  #3C485A  #3E495B
```

#### Teal/Energy (display, glow, indicatori)

```text
#21415E  #285E88  #437A9E  #68B5BE  #72C7D0  #8CF3FF  #93FFFF  #9BF5FF  #A2FFFF  #66C6D2
```

#### Grigi Generici (transizioni, dettagli)

```text
#080A0C  #0E1013  #141C2A  #151D2B  #151D2C  #1C1E22  #192130  #1B2434  #1C2535
#1D2635  #1D2636  #24262A  #1E2737  #1E2838  #1F2838  #202939  #212A3A  #212B3B
#222C3C  #232C3C  #232D3D  #242E3E  #262F3F  #273141  #283242  #2A3444  #2B3545
#2D3747  #374151  #3C485A  #1C5862  #606872  #788291  #7A828C  #4096A5  #969EAA
#72C7D0  #B2BAC6  #C6CEDA  #8CF3FF
```

### Vincoli Tecnici

- **Non modificare UV o modelli**: lavora solo sul file `.png`
- **Risoluzione**: 64x64 pixel
- **Orientamento**: il fronte è NORTH
- **Simmetria**: richiesta su hopper e parti principali (evita dettagli direzionali)
- **Formato**: PNG 32-bit con alpha

### UV Map Clone Pulverizer (x, y, w, h)

| Elemento | Posizione | Dimensione | Descrizione |
|----------|-----------|------------|-------------|
| base_top | (0, 0) | 16x16 | Piattaforma base superiore |
| chamber_top | (16, 0) | 12x12 | Camera di lavorazione superiore |
| core_top | (32, 0) | 16x16 | Core energia superiore |
| supports_side | (48, 0) | 3x5 | Lati supporti |
| supports_top | (48, 5) | 3x3 | Top supporti |
| supports_bottom | (48, 8) | 3x3 | Bottom supporti |
| chamber_sides | (0, 16) | 12x6 | Lati camera |
| hopper | (16, 16) | 14x10 | Tramoggia input (SIMMETRICA) |
| rollers | (32, 16) | 8x6 | Rulli frantumazione |
| belt | (48, 16) | 7x3 | Nastro trasportatore |
| core_sides | (0, 24) | 10x2 | Lati core energia |
| discharge | (0, 32) | 6x5 | Vassoio output |
| panel_frame | (16, 32) | 6x4 | Cornice pannello |
| screen | (32, 32) | 4x2 | Display/schermo |
| feed_guide | (48, 32) | 6x4 | Guida alimentazione |
| buttons | (16, 40) | 4x1 | Pulsanti controllo |
| base_side_strip | (0, 48) | 14x1 | Striscia laterale base |
| peg | (48, 48) | 1x1 | Piolo/connettore |

### Materiali per Sezione

#### Steel/Body (corpo principale)

- Ramp 4-5 toni dark/steel
- Bevel 1px esterno + 1px interno
- Noise leggero (5-10%)
- Colori: `#1B2434` → `#232D3D` → `#2D3747` → `#374151` → `#3C485A`

#### Iron/Rollers (rulli)

- Ramp metal chiaro con 1-2 pixel di specular highlight
- Micro-segmenti leggibili
- Colori: `#606872` → `#788291` → `#969EAA` → `#B2BAC6`

#### Rubber/Belt (nastro)

- Ramp nero/gomma 3 toni
- Solchi regolari orizzontali
- Niente highlight forti
- Colori: `#080A0C` → `#0E1013` → `#1C1E22`

#### Hopper (tramoggia)

- Grigio scuro metallico
- **SIMMETRICO** (nessun dettaglio direzionale)
- Rientro interno leggibile
- Colori: `#1F2839` → `#283242` → `#343F51`

#### Display/UI (schermo, indicatori)

- Teal acceso con 1 SOLO indicatore vicino al display
- **Evitare teal sparso** su altre parti
- Colori: `#21415E` → `#437A9E` → `#72C7D0` → `#8CF3FF`

#### Discharge (vassoio output)

- Grigio definito con slot leggibile
- Contrasto per visibilità
- Colori: `#232D3D` → `#2D3747` → `#374151`

#### Supporti

- Steel più scuro del body per stacco visivo
- Colori: `#141C2A` → `#17202F` → `#1B2434`

### Regole Estetiche

1. **Shading Minecraft**: bevel sui bordi + noise leggero interno
2. **Niente puntini teal casuali**: teal SOLO su display/indicatori
3. **Contrasto netto tra sezioni**: ogni parte deve essere leggibile
4. **Luce coerente**: fonte da alto-sinistra (highlight in alto/sinistra, ombre in basso/destra)
5. **Simmetria**: hopper e parti strutturali devono essere simmetriche

### Criteri di Accettazione

- [ ] Ogni sezione riconoscibile a colpo d'occhio
- [ ] Palette coerente con Neural Cellar (nessun colore fuori palette)
- [ ] Nessun dettaglio fuori tema
- [ ] Teal limitato a display/indicatori
- [ ] Hopper simmetrico
- [ ] Contrasto leggibile tra tutte le sezioni

### Template per Nuove Macchine

```text
Obiettivo: rifare la texture del modello [NOME_MACCHINA] in stile Minecraft tecnico,
coerente con Neural Cellar.

Riferimenti palette/stile (obbligatori):
- src/main/resources/assets/devmod/textures/block/neurocell_base.png
- src/main/resources/assets/devmod/textures/block/neurocell_frame.png
- src/main/resources/assets/devmod/textures/block/neurocell_glass.png
- src/main/resources/assets/devmod/textures/block/clone/clone_pulverizer.png

Regola palette (vincolante):
- Usa SOLO colori presenti nei file sopra.
- Non inventare colori fuori palette.

Vincoli tecnici:
- Non modificare UV o modelli: lavora solo su [PATH_TEXTURE_TARGET]
- Risoluzione: 64x64
- Orientamento: il fronte è north
- Simmetria dove richiesto

UV map (x, y, w, h):
[LISTA UV COMPLETA DELLA MACCHINA]

Materiali per sezione:
- Steel/Body: ramp 4-5 toni dark/steel; bevel 1px; noise 5-10%
- Iron/Rollers: ramp metal chiaro; 1-2px specular highlight
- Rubber/Belt: ramp nero/gomma 3 toni; solchi regolari
- Hopper: grigio scuro metallico, simmetrico
- Display/UI: teal acceso con 1 solo indicatore
- Discharge: grigio definito con slot leggibile
- Supporti: steel più scuro del body

Regole estetiche:
- Shading Minecraft (bevel + noise leggero)
- Niente puntini teal casuali
- Contrasto netto tra sezioni
- Luce coerente da alto-sinistra

Criteri di accettazione:
- Ogni sezione riconoscibile a colpo d'occhio
- Palette coerente con Neural Cellar
- Nessun dettaglio fuori tema
```

---

## 4. Codice Java

### CloneMachineBlock.java

```java
public class CloneMachineBlock extends Block implements EntityBlock {

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CloneMachineBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED; // Importante per GeckoLib
    }
}
```

### CloneMachineBlockEntity.java

```java
public class CloneMachineBlockEntity extends BlockEntity implements GeoBlockEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // Animazione statica - NON ricreare ogni tick
    protected static final RawAnimation DEPLOY_THEN_ACTIVE = RawAnimation.begin()
            .thenPlay("animation.clone_pulverizer.deploy")
            .thenLoop("animation.clone_pulverizer.active");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> {
            // Ritorna SEMPRE la stessa animazione - GeckoLib gestisce la continuità
            return state.setAndContinue(DEPLOY_THEN_ACTIVE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
```

### CloneMachineRenderer.java

```java
public class CloneMachineRenderer extends GeoBlockRenderer<CloneMachineBlockEntity> {

    public CloneMachineRenderer() {
        super(new DefaultedBlockGeoModel<>(
            ResourceLocation.fromNamespaceAndPath("devmod", "clone_pulverizer")
        ));
    }
}
```

### DefaultedBlockGeoModel Path Conventions

Quando usi `DefaultedBlockGeoModel` con un ResourceLocation, GeckoLib cerca i file in questi path:

```text
ResourceLocation("devmod", "clone_pulverizer") cerca:

geo:       assets/devmod/geo/block/clone_pulverizer.geo.json
animation: assets/devmod/animations/block/clone_pulverizer.animation.json
texture:   assets/devmod/textures/block/clone_pulverizer.png
```

**IMPORTANTE**: Nota il sottofolder `block/` per geo e animations!

Se vuoi path custom, usa `DefaultedBlockGeoModel` con override:

```java
public class CustomGeoModel extends DefaultedBlockGeoModel<MyBlockEntity> {
    public CustomGeoModel() {
        super(ResourceLocation.fromNamespaceAndPath("devmod", "clone_pulverizer"));
    }

    @Override
    public ResourceLocation getModelResource(MyBlockEntity entity) {
        return ResourceLocation.fromNamespaceAndPath("devmod", "geo/clone_pulverizer.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(MyBlockEntity entity) {
        return ResourceLocation.fromNamespaceAndPath("devmod", "textures/block/clone/clone_pulverizer.png");
    }

    @Override
    public ResourceLocation getAnimationResource(MyBlockEntity entity) {
        return ResourceLocation.fromNamespaceAndPath("devmod", "animations/clone_pulverizer.animation.json");
    }
}
```

### Registrazione Completa

#### CloneBlocks.java (Registrazione Block)

```java
public class CloneBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
        DeferredRegister.createBlocks("devmod");

    public static final DeferredBlock<ClonePulverizerBlock> CLONE_PULVERIZER =
        BLOCKS.register("clone_pulverizer", () -> new ClonePulverizerBlock(
            BlockBehaviour.Properties.of()
                .strength(3.5f)
                .requiresCorrectToolForDrops()
                .noOcclusion()  // Importante per GeckoLib
        ));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
```

#### CloneBlockEntities.java (Registrazione BlockEntity)

```java
public class CloneBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "devmod");

    public static final Supplier<BlockEntityType<ClonePulverizerBlockEntity>> CLONE_PULVERIZER =
        BLOCK_ENTITIES.register("clone_pulverizer", () ->
            BlockEntityType.Builder.of(
                ClonePulverizerBlockEntity::new,
                CloneBlocks.CLONE_PULVERIZER.get()
            ).build(null)
        );

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
```

#### CloneClientSetup.java (Registrazione Renderer)

```java
@EventBusSubscriber(modid = "devmod", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class CloneClientSetup {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
            CloneBlockEntities.CLONE_PULVERIZER.get(),
            ctx -> new ClonePulverizerRenderer()
        );
    }
}
```

#### Blockstate per GeckoLib (clone_pulverizer.json)

Path: `assets/devmod/blockstates/clone_pulverizer.json`

```json
{
  "variants": {
    "": {
      "model": "devmod:block/clone_pulverizer"
    }
  }
}
```

**NOTA**: Il blockstate punta a un model file, ma per GeckoLib il model file può essere vuoto o un placeholder. Il rendering effettivo è gestito dal `GeoBlockRenderer`.

#### Model Placeholder (opzionale)

Path: `assets/devmod/models/block/clone_pulverizer.json`

```json
{
  "parent": "block/cube_all",
  "textures": {
    "all": "devmod:block/clone/clone_pulverizer"
  }
}
```

Questo è solo un fallback per l'item form o se il renderer non è caricato.

---

## 5. Workflow Creazione Nuovo Modello

### Step 1: Analisi Riferimento
1. Identificare il modello Oritech di riferimento
2. Documentare: dimensioni, parti mobili, effetti visivi
3. Decidere quali parti animare

### Step 2: Creare Geometria
1. Creare file `geo/clone_[nome].geo.json`
2. Definire bones con gerarchia corretta
3. Impostare pivot points al centro di ogni cubo
4. Mappare UV per-face alla texture

### Step 3: Creare Animazioni
1. Creare file `animations/clone_[nome].animation.json`
2. Animazione `deploy` (loop: false) - assemblaggio iniziale
3. Animazione `active` (loop: true) - idle/funzionamento
4. Usare solo position/rotation, NO scale

### Step 4: Creare Texture
1. Creare file `textures/block/clone/clone_[nome].png` (64x64)
2. Usare palette colori Neurocell
3. Creare UN ELEMENTO PER VOLTA con analisi
4. Elementi grigi per parti strutturali
5. Cyan solo per parti energia/glow

### Step 5: Codice Java
1. Creare/riusare Block class
2. Creare BlockEntity con AnimatableBlockEntity
3. Registrare animazione statica (non ricreare ogni tick)
4. Creare Renderer con DefaultedBlockGeoModel
5. Registrare renderer in ClientSetup

### Step 6: Test
1. Build progetto
2. Test in-game piazzamento blocco
3. Verificare animazione deploy
4. Verificare animazione active loop
5. Verificare texture corretta

---

## 6. Problemi Comuni e Soluzioni

### Animazione dura solo 1-2 secondi
**Causa**: Flag `deployed` che cambia stato controller
**Soluzione**: Ritornare SEMPRE la stessa RawAnimation, GeckoLib gestisce la continuità

### Parti sospese in aria
**Causa**: Position iniziale con Y > 0
**Soluzione**: Parti devono partire attaccate alla base (Y=0)

### Animazione troppo "morbida"
**Causa**: Uso di scaling o easing
**Soluzione**: Solo position/rotation con keyframes bruschi

### Texture non corrisponde
**Causa**: UV mapping errato o colori sbagliati
**Soluzione**: Usare palette Neurocell esatta, creare elementi uno alla volta

---

## 7. Item Rendering in postRender (GeoBlockRenderer)

Quando si vuole renderizzare un item in una posizione specifica del modello (es. sul discharge tray del pulverizer), bisogna capire il sistema di coordinate di GeckoLib.

### Sistema di Coordinate

In `GeoBlockRenderer.postRender()`:

- **L'origine (0, 0, 0)** è al **centro del blocco** (non all'angolo!)
- **Y** segue la convenzione standard: positivo verso l'alto
- **X e Z** sono **invertiti** rispetto alle coordinate world di Minecraft

### Mappatura FACING → Offset

Quando il blocco ha una proprietà `FACING` (HorizontalDirectionalBlock), l'offset per posizionare un item sul retro del blocco (es. discharge tray) segue questa logica **invertita**:

```java
switch (facing) {
    case NORTH -> offsetZ = -trayOffset;  // Item verso -Z (non +Z!)
    case SOUTH -> offsetZ = trayOffset;   // Item verso +Z
    case EAST -> offsetX = -trayOffset;   // Item verso -X
    case WEST -> offsetX = trayOffset;    // Item verso +X
}
```

**Attenzione**: Questo è l'**opposto** di quello che ci si aspetterebbe! Se il blocco "guarda NORTH" e il discharge è sul retro (sud), l'offset deve essere **negativo** in Z, non positivo.

### Perché è Invertito?

GeckoLib applica trasformazioni al modello Bedrock che invertono gli assi X e Z rispetto allo spazio world di Minecraft. Quando si lavora in `postRender()`, si è nello spazio trasformato del modello.

### Esempio Completo: Discharge Tray

```java
private void renderOutputItem(PoseStack poseStack, ...) {
    // Offset in unità blocco (8.5 pixel model / 16 = 0.53)
    double trayOffset = 0.53;
    double offsetX = 0;
    double offsetZ = 0;

    switch (facing) {
        case NORTH -> offsetZ = -trayOffset;
        case SOUTH -> offsetZ = trayOffset;
        case EAST -> offsetX = -trayOffset;
        case WEST -> offsetX = trayOffset;
    }

    // Origine già al centro, Y basso per il vassoio
    poseStack.translate(offsetX, 0.1, offsetZ);

    // Scale e render
    poseStack.scale(0.35f, 0.35f, 0.35f);
    Minecraft.getInstance().getItemRenderer().renderStatic(...);
}
```

### Regole Pratiche

1. **Usa (0, Y, 0) per il centro** - non (0.5, Y, 0.5)
2. **Inverti le direzioni** - NORTH = -Z, EAST = -X
3. **Test empirico**: se l'item appare sul lato sbagliato, inverti il segno dell'offset
4. **Y = 0** è il fondo del blocco, Y = 1 è il top

### Troubleshooting: Processo di Debug

Se l'item non appare nella posizione corretta, segui questo processo:

#### Step 1: Verifica che il rendering funzioni

```java
poseStack.translate(0, 1.5, 0);  // Alto sopra il blocco
```

Se l'item appare in aria sopra la macchina → il rendering funziona, problema di coordinate.

#### Step 2: Trova l'origine

```java
poseStack.translate(0, 0.1, 0);  // Centro del blocco, basso
```

- Se l'item è al centro del blocco → origine corretta a (0,0,0)
- Se l'item è in un angolo → origine spostata, aggiusta di conseguenza

#### Step 3: Aggiungi offset con facing NORTH

```java
poseStack.translate(0, 0.1, 0.5);  // Test offset +Z
```

- Se l'item appare sul lato OPPOSTO a quello atteso → inverti il segno
- Se l'item appare su un asse diverso (es. X invece di Z) → gli assi sono scambiati

#### Step 4: Mappa tutte le direzioni

Una volta trovato l'offset corretto per NORTH, le altre direzioni seguono:

- Se NORTH usa -Z → SOUTH usa +Z
- Se NORTH usa -Z → EAST usa -X, WEST usa +X

#### Errori Comuni

| Sintomo                  | Causa                               | Soluzione                 |
|--------------------------|-------------------------------------|---------------------------|
| Item non visibile        | Y troppo basso, dentro la geometria | Aumenta Y a 1.5 per test  |
| Item sul lato opposto    | Segno dell'offset sbagliato         | Inverti il segno          |
| Item su asse sbagliato   | Confusione X/Z                      | Scambia offsetX e offsetZ |
| Item non segue rotazione | Offset non dipende da facing        | Aggiungi switch su facing |

---

## 8. Checklist Validazione Pre-Build

Prima di eseguire la build, verifica che tutti i file siano corretti:

### Geometria (.geo.json)

- [ ] `format_version` è `"1.12.0"`
- [ ] `geometry identifier` segue pattern `geometry.[nome_modello]`
- [ ] `texture_width` e `texture_height` sono 64 (o potenza di 2)
- [ ] Tutti i bones hanno `name` univoco
- [ ] Tutti i bones con parent hanno parent esistente
- [ ] `pivot` calcolato correttamente: `origin + (size / 2)`
- [ ] `uv_size` corrisponde alla dimensione del cubo per ogni faccia
- [ ] Nessuna sovrapposizione UV (a meno che intenzionale)

### Animazioni (.animation.json)

- [ ] `format_version` è `"1.8.0"`
- [ ] Nomi animazioni seguono pattern `animation.[nome_modello].[nome_anim]`
- [ ] Tutti i bones referenziati esistono nel .geo.json
- [ ] `animation_length` impostato correttamente
- [ ] `loop: true` solo per animazioni cicliche (idle, active)
- [ ] `loop: false` per animazioni one-shot (deploy)
- [ ] NO scale keyframes (solo position/rotation)

### Texture (.png)

- [ ] Dimensione 64x64 (o come specificato in geometry)
- [ ] Palette colori Neurocell rispettata
- [ ] Tutti gli UV del modello coperti
- [ ] Formato PNG 32-bit (con alpha)

### Java Code

- [ ] Block registrato in DeferredRegister
- [ ] BlockEntity registrato in DeferredRegister
- [ ] BlockEntity implementa `GeoBlockEntity`
- [ ] `getRenderShape()` ritorna `ENTITYBLOCK_ANIMATED`
- [ ] Renderer registrato in `EntityRenderersEvent.RegisterRenderers`
- [ ] `AnimatableInstanceCache` creato con `GeckoLibUtil.createInstanceCache(this)`
- [ ] RawAnimation definita come `static final` (non ricreata ogni tick)

### File System

- [ ] `.geo.json` in `assets/devmod/geo/block/` (o path custom nel model)
- [ ] `.animation.json` in `assets/devmod/animations/block/` (o path custom)
- [ ] `.png` in `assets/devmod/textures/block/clone/`
- [ ] Blockstate in `assets/devmod/blockstates/`
- [ ] Model placeholder in `assets/devmod/models/block/` (opzionale)

### Naming Conventions

- [ ] Tutti i file usano lo stesso nome base (es. `clone_pulverizer`)
- [ ] Geometry identifier: `geometry.clone_pulverizer`
- [ ] Animazioni: `animation.clone_pulverizer.deploy`, `animation.clone_pulverizer.active`
- [ ] Registry names: `clone_pulverizer` (snake_case)

---

## 9. Best Practices Tecniche (Lezioni Apprese)

Questa sezione documenta le best practices scoperte durante lo sviluppo del Clone Pulverizer attraverso analisi, test e refactoring iterativo.

### 9.1 Animation Chaining (Raccomandato)

**Problema**: Timer manuali per gestire transizioni tra animazioni causano:
- Codice duplicato tra client e server
- Rischio desync
- Stato aggiuntivo da sincronizzare

**Soluzione**: Usare animation chaining nativo di GeckoLib

```java
// EVITARE: Timer manuale
private int deployTimer = 0;
private static final int DEPLOY_DURATION = 80;
protected static final RawAnimation DEPLOY = RawAnimation.begin().thenPlay("...");
protected static final RawAnimation IDLE = RawAnimation.begin().thenLoop("...");

// Controller complesso
if (deployTimer < DEPLOY_DURATION) {
    return state.setAndContinue(DEPLOY);
}
return state.setAndContinue(active ? ACTIVE : IDLE);
```

```java
// PREFERIRE: Animation chaining
protected static final RawAnimation DEPLOY_THEN_IDLE = RawAnimation.begin()
    .thenPlay("animation.clone_pulverizer.deploy")
    .thenLoop("animation.clone_pulverizer.idle");

protected static final RawAnimation ACTIVE = RawAnimation.begin()
    .thenLoop("animation.clone_pulverizer.active");

// Controller semplificato
return state.setAndContinue(active ? ACTIVE : DEPLOY_THEN_IDLE);
```

**Vantaggi**:
- ~20 linee di codice in meno
- Zero stato da sincronizzare per animazioni
- GeckoLib gestisce automaticamente le transizioni
- Nessun rischio desync client/server

### 9.2 Network Sync con Dirty Flags

**Problema**: `syncToClient()` chiamato ogni tick spreca banda e CPU

**Soluzione**: Dirty flags pattern

```java
// Dirty flags per ogni tipo di dato sincronizzato
private boolean dirtyActive = false;
private boolean dirtyOutput = false;

// Marcare dirty quando i dati cambiano
private void setActive(boolean newActive) {
    if (active != newActive) {
        active = newActive;
        dirtyActive = true;
    }
}

// Sync solo se necessario
private void syncToClient() {
    if (!dirtyActive && !dirtyOutput) return;

    Level lvl = level;
    if (lvl != null && !lvl.isClientSide) {
        lvl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        dirtyActive = dirtyOutput = false;
        setChanged();
    }
}

// Force sync per casi speciali (es. crafting completato)
private void forceSyncToClient() {
    dirtyActive = dirtyOutput = true;
    syncToClient();
}
```

**Metriche**:
| Metrica | Prima | Dopo |
|---------|-------|------|
| Sync per tick (idle) | 1-2 | 0 |
| Sync per tick (active) | 3-5 | 0-1 |

### 9.3 Costanti nel Renderer

**Problema**: Magic numbers sparsi nel codice rendono difficile la manutenzione

**Soluzione**: Estrarre tutte le costanti con nomi descrittivi

```java
// Costanti di rendering con commenti
private static final float ROLLER_HEIGHT = 0.5625f;  // 9/16 model units
private static final double TRAY_OFFSET = 0.53;      // 8.5/16 from center
private static final double OUTPUT_HEIGHT = 0.1;
private static final float PROCESSING_SCALE = 0.4f;
private static final float OUTPUT_SCALE = 0.35f;
private static final float SPIN_RATE = 10f;          // degrees per tick
private static final float MAX_SQUEEZE = 0.5f;       // squeeze to 50%
```

### 9.4 Helper Methods per Facing

**Problema**: Logica direzione duplicata in più metodi

**Soluzione**: Helper method riutilizzabile

```java
/**
 * Calculate directional offset based on facing.
 * Uses GeckoLib's inverted coordinate system.
 */
private Vec3 getDirectionalOffset(Direction facing, double distance) {
    return switch (facing) {
        case NORTH -> new Vec3(0, 0, -distance);
        case SOUTH -> new Vec3(0, 0, distance);
        case EAST -> new Vec3(-distance, 0, 0);
        case WEST -> new Vec3(distance, 0, 0);
        default -> Vec3.ZERO;
    };
}

private float getFacingRotation(Direction facing) {
    return switch (facing) {
        case SOUTH -> 180f;
        case WEST -> 90f;
        case EAST -> -90f;
        default -> 0f;
    };
}
```

### 9.5 NBT Keys come Costanti

**Problema**: Stringhe hardcoded per chiavi NBT causano errori di typo

**Soluzione**: Costanti statiche

```java
private static final String TAG_PROGRESS = "Progress";
private static final String TAG_MAX_PROGRESS = "MaxProgress";
private static final String TAG_PROCESSING_ITEM = "ProcessingItem";
private static final String TAG_ACTIVE = "Active";
private static final String TAG_OUTPUT_ITEM = "OutputItem";
private static final String TAG_INVENTORY = "Inventory";
```

### 9.6 Null Safety nel Renderer

**Problema**: `entity.getLevel()` può ritornare null, causando NPE

**Soluzione**: Early return pattern

```java
private void renderProcessingItem(...) {
    ItemStack processingItem = entity.getProcessingItem();
    if (processingItem.isEmpty()) return;

    Level level = entity.getLevel();
    if (level == null) return;  // Early exit

    // Resto del codice usando 'level' cached
    float spin = (level.getGameTime() + partialTick) * SPIN_RATE;
    // ...
}
```

### 9.7 Processo di Debug per Coordinate GeckoLib

Quando un item renderizzato appare nella posizione sbagliata:

1. **Test visibilità**: `poseStack.translate(0, 1.5, 0)` - item sopra il blocco?
2. **Trova origine**: `poseStack.translate(0, 0.1, 0)` - item al centro?
3. **Test asse singolo**: `poseStack.translate(0, 0.1, 0.5)` - quale direzione?
4. **Inverti se necessario**: Se appare opposto, inverti il segno
5. **Mappa tutte le direzioni**: Una volta trovato NORTH, le altre seguono

**Regola d'oro**: In GeckoLib postRender(), gli assi X e Z sono invertiti rispetto a Minecraft world.

### 9.8 Ottimizzazioni Modello .geo.json

Il modello Clone Pulverizer è ben strutturato. Ottimizzazioni possibili ma non necessarie:

| Parte | Cubi Attuali | Cubi Possibili | Note |
|-------|--------------|----------------|------|
| Hopper | 8 | 4 | Merge pareti parallele |
| Roller arms | 8 | 6 | Merge cubi allineati |
| Supports | 4 | 4 | Già ottimale |

**Raccomandazione**: Non ottimizzare a meno che non ci siano problemi di performance. La leggibilità del modello è più importante.

---

## 10. Modelli Completati

| Modello          | Geo | Animation | Texture | Java | Testato |
|------------------|-----|-----------|---------|------|---------|
| clone_pulverizer | OK  | OK        | OK      | OK   | OK      |

---

## 11. Modelli Da Fare (28 rimanenti)

1. clone_assembler
2. clone_atomic_forge
3. clone_battery
4. clone_bio_generator
5. clone_centrifuge_l
6. clone_charger
7. clone_conveyor
8. clone_crusher
9. clone_drill
10. clone_energy_pipe
11. clone_fertilizer
12. clone_foundry
13. clone_fuel_generator
14. clone_laser_arm
15. clone_lava_generator
16. clone_motor
17. clone_processor
18. clone_pump
19. clone_reactor
20. clone_refinery
21. clone_shrinker
22. clone_smelter
23. clone_solar_panel
24. clone_steam_engine
25. clone_storage_unit
26. clone_tank
27. clone_tech_door
28. clone_treefeller

---

*Ultimo aggiornamento: 2026-01-12*
