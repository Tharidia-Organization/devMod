# Impact HUD - Sistema di Rendering

> Last updated: 2025-12-26
> Status: NEEDS_VERIFICATION

## 1. Overview

Il sistema di rendering dell'Impact HUD opera su due pipeline parallele:

```
┌─────────────────────────────────────────────────────────────────┐
│                     RENDERING PIPELINES                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────────────────────────┐                            │
│  │     2D HUD OVERLAY              │                            │
│  │     (Screen Space)              │                            │
│  │                                 │                            │
│  │  • RegisterGuiLayersEvent       │                            │
│  │  • Above CROSSHAIR layer        │                            │
│  │  • GuiGraphics API              │                            │
│  │  • Fixed screen position        │                            │
│  └─────────────────────────────────┘                            │
│                                                                  │
│  ┌─────────────────────────────────┐                            │
│  │     3D WORLD PANELS             │                            │
│  │     (World Space)               │                            │
│  │                                 │                            │
│  │  • RenderLevelStageEvent        │                            │
│  │  • AFTER_ENTITIES stage         │                            │
│  │  • PoseStack + MultiBufferSource│                            │
│  │  • Billboard towards camera     │                            │
│  └─────────────────────────────────┘                            │
│                                                                  │
│  ┌─────────────────────────────────┐                            │
│  │     3D VFX EFFECTS              │                            │
│  │     (World Space)               │                            │
│  │                                 │                            │
│  │  • RenderLevelStageEvent        │                            │
│  │  • AFTER_ENTITIES stage         │                            │
│  │  • debugLineStrip RenderType    │                            │
│  │  • Animated procedural geometry │                            │
│  └─────────────────────────────────┘                            │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. HUD 2D Overlay

### 2.1 Registrazione
**File:** `ImpactHudOverlay.java:64-71`

```java
@SubscribeEvent
public static void registerGuiLayers(RegisterGuiLayersEvent event) {
    event.registerAbove(
        VanillaGuiLayers.CROSSHAIR,
        LAYER_ID,  // "devmod:impact_analysis"
        ImpactHudOverlay::render
    );
}
```

### 2.2 Ciclo di Rendering
**File:** `ImpactHudOverlay.java:77-130`

```java
private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
    // 1. Check preconditions
    if (!enabled) return;
    ImpactData data = ImpactData.get();
    if (data == null) return;
    if (mc.player == null || mc.options.hideGui) return;

    // 2. Calculate dimensions
    int panelWidth = 260;
    int panelHeight = calculatePanelHeight(data, font);

    // 3. Position (fixed top-right)
    int panelX = screenWidth - panelWidth - 10;
    int panelY = 10;

    // 4. Observation check
    int crosshairX = screenWidth / 2;
    int crosshairY = screenHeight / 2;
    boolean isObserving = isCrosshairOverPanel(crosshairX, crosshairY);
    data.setObserved(isObserving);

    // 5. Alpha calculation
    float alpha = data.getRemainingAlpha();
    if (alpha <= 0.01f) return;

    // 6. Render panels
    renderImpactPanel(graphics, font, data, panelX, panelY, panelWidth, panelHeight, alpha);

    if (data.hasPehkuiModification() || data.isBetterCombatAttack()) {
        renderModSpecificsPanel(graphics, font, data, panelX, modPanelY, panelWidth, alpha);
    }
}
```

### 2.3 Layout del Pannello

```
┌────────────────────────────────────────────────────────────────┐
│  PANEL (260px width, dynamic height)                           │
│  Position: top-right, 10px margin                              │
├────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ Impact Analysis (Multi-Part & Mod Integrated)           │   │ ← Title (cyan)
│  ├─────────────────────────────────────────────────────────┤   │ ← Separator
│  │ Part Hit: HEAD (Modifier: x1.50)                        │   │ ← Body part (colored)
│  │ Source: Melee Attack                                    │   │ ← Source (gray)
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Base Weapon Dmg: 7.00                                   │   │ ← White
│  │ Enchant (Sharpness V): +3.00                            │   │ ← Green
│  │ Enchant (Fire Aspect II): +0.00                         │   │ ← Green
│  │ Pehkui Size Bonus (+25% of Base): +1.75                 │   │ ← Green
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Local Part Calc: (7.0+3.0+1.8) * 1.50 = 17.6            │   │ ← Formula (gold)
│  │                                                          │   │
│  │ ACTUAL DAMAGE: 12.50                                     │   │ ← Red, bold
│  │ HP: 20.00 -> 7.50                                        │   │ ← Gray
│  │ Armor/Effects reduced: -5.10                             │   │ ← Light red
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ Mod Specifics                                            │   │ ← Second panel
│  │ Better Combat: Arc Collision Detected                    │   │
│  │ Pehkui: Entity Size Modified (Scale 1.50)                │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└────────────────────────────────────────────────────────────────┘
```

### 2.4 Palette Colori
**File:** `ImpactHudOverlay.java:34-43`

```java
// Backgrounds
private static final int PANEL_BG = 0xCC1A1A2E;           // Dark blue 80%
private static final int PANEL_BORDER = 0xFF3D5AFE;       // Electric blue
private static final int PANEL_BORDER_GLOW = 0x553D5AFE;  // Glow 33%

// Text
private static final int TEXT_TITLE = 0xFF00FFFF;         // Cyan
private static final int TEXT_NORMAL = 0xFFFFFFFF;        // White
private static final int TEXT_VALUE = 0xFF00FF00;         // Green
private static final int TEXT_FORMULA = 0xFFFFD700;       // Gold
private static final int TEXT_MUTED = 0xFFAAAAAA;         // Gray
```

### 2.5 Dimensioni
**File:** `ImpactHudOverlay.java:45-48`

```java
private static final int PANEL_PADDING = 8;
private static final int LINE_HEIGHT = 10;
private static final int SECTION_SPACING = 6;
```

### 2.6 Calcolo Altezza Dinamica
**File:** `ImpactHudOverlay.java:304-342`

```java
private static int calculatePanelHeight(ImpactData data, Font font) {
    int height = PANEL_PADDING * 2;  // Top + bottom

    height += LINE_HEIGHT + 2;  // Title
    height += SECTION_SPACING;  // Separator
    height += LINE_HEIGHT + 2;  // Part Hit
    height += LINE_HEIGHT + SECTION_SPACING;  // Source

    height += LINE_HEIGHT;  // Base Weapon Dmg

    // Dynamic: Enchants
    for (EnchantBonus eb : bd.enchantBonuses) {
        if (eb.bonus() > 0) height += LINE_HEIGHT;
    }

    // Dynamic: Pehkui
    if (bd.pehkuiSizeBonus > 0) height += LINE_HEIGHT;

    height += SECTION_SPACING + LINE_HEIGHT;  // Formula

    // Dynamic: Actual damage section
    if (data.hasActualDamage()) {
        height += LINE_HEIGHT + 2;  // ACTUAL DAMAGE
        height += LINE_HEIGHT;      // HP: before -> after
        if (Math.abs(data.getDamageReduction()) > 0.1f) {
            height += LINE_HEIGHT;  // Armor/Effects
        }
    } else {
        height += LINE_HEIGHT + 2;  // Calculated Dmg
    }

    return height;
}
```

---

## 3. Pannelli 3D (World Space)

### 3.1 Architettura

```
Impact3DPanelManager (Singleton)
        │
        ├── activePanels: List<Impact3DPanel>
        │       │
        │       └── Impact3DPanel
        │               ├── hitPoint: Vec3
        │               ├── panelPosition: Vec3
        │               ├── data: ImpactData
        │               └── spawnTime: long
        │
        └── Impact3DRenderer (Singleton)
                └── renderPanel(): void
```

### 3.2 Lifecycle Pannello 3D
**File:** `Impact3DPanel.java:23-27`

```java
private static final long LIFETIME_MS = 4000;        // Total: 4 seconds
private static final long FADE_IN_MS = 500;          // Fade in: 500ms
private static final long FADE_OUT_MS = 1000;        // Fade out: 1000ms
private static final long FADE_OUT_START = LIFETIME_MS - FADE_OUT_MS;  // 3000ms
```

**Timeline:**
```
0ms        500ms              3000ms      4000ms
│           │                    │           │
├───────────┼────────────────────┼───────────┤
│  FADE IN  │     FULL ALPHA     │ FADE OUT  │
│  α: 0→1   │       α: 1.0       │  α: 1→0   │
└───────────┴────────────────────┴───────────┘
```

### 3.3 Posizionamento Pannello
**File:** `Impact3DRenderer.java:388-407`

```java
public Vec3 calculatePanelPosition(Vec3 hitPoint, Vec3 cameraPos) {
    // Direction from camera to hit
    Vec3 toHit = hitPoint.subtract(cameraPos).normalize();

    // Perpendicular vector (right of view)
    Vec3 right = toHit.cross(new Vec3(0, 1, 0)).normalize();

    // Fallback for vertical look
    if (right.lengthSqr() < 0.001) {
        right = new Vec3(1, 0, 0);
    }

    // Position: right and above hit point
    return hitPoint
        .add(right.scale(PANEL_OFFSET_SIDE))   // 4.5 units right
        .add(0, PANEL_OFFSET_UP, 0);           // 1.0 unit up
}
```

### 3.4 Billboard Rotation
**File:** `Impact3DRenderer.java:76-86`

```java
// Calculate billboard rotation (face camera)
Vec3 toCamera = cameraPos.subtract(panelWorldPos).normalize();
float yaw = (float) Math.atan2(toCamera.x, toCamera.z);
poseStack.mulPose(new Quaternionf().rotationY(yaw));

// Scale and flip
poseStack.scale(PANEL_SCALE, -PANEL_SCALE, PANEL_SCALE);  // -Y per flip verticale

// Center panel
poseStack.translate(-PANEL_WIDTH_PX / 2, -PANEL_HEIGHT_PX / 2, 0);
```

### 3.5 Connection Line
**File:** `Impact3DRenderer.java:101-129`

```java
private void renderConnectionLine(PoseStack poseStack, MultiBufferSource bufferSource,
                                   Vec3 cameraPos, Vec3 hitPoint, Vec3 panelPos, float alpha) {
    // Use RenderType.lines() for stability
    VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());

    // Cyan color
    float r = 0.0f, g = 0.9f, b = 1.0f, a = alpha * 0.9f;

    // Line from hit point to panel
    consumer.addVertex(matrix, hitRel.x, hitRel.y, hitRel.z)
        .setColor(r, g, b, a)
        .setNormal(poseStack.last(), 0, 1, 0);
    consumer.addVertex(matrix, panelRel.x, panelRel.y, panelRel.z)
        .setColor(r, g, b, a)
        .setNormal(poseStack.last(), 0, 1, 0);
}
```

### 3.6 Rendering Testo 3D
**File:** `Impact3DRenderer.java:336-362`

```java
private void renderText3D(PoseStack poseStack, MultiBufferSource bufferSource, Font font,
                           String text, float x, float y, int color, float globalAlpha) {
    poseStack.pushPose();
    poseStack.translate(x, y, -0.5f);  // Z offset per depth

    // SEE_THROUGH per visibilità corretta in 3D
    font.drawInBatch(
        text,
        0, 0,
        finalColor,
        false,  // no shadow
        matrix,
        bufferSource,
        Font.DisplayMode.SEE_THROUGH,
        0,        // backgroundColor
        15728880  // full bright
    );

    poseStack.popPose();
}
```

### 3.7 Distance Culling
**File:** `Impact3DPanelManager.java:32-34`

```java
private static final double MAX_RENDER_DISTANCE = 64.0;
private static final double MAX_RENDER_DISTANCE_SQ = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;
```

**Applicazione:**
```java
for (Impact3DPanel panel : activePanels) {
    double distSq = panelPos.distanceToSqr(cameraPos);
    if (distSq > MAX_RENDER_DISTANCE_SQ) {
        continue;  // Skip rendering
    }
    panel.render(...);
}
```

---

## 4. Effetti VFX 3D

### 4.1 Tipi di Effetti
**File:** `ImpactVFX.java`

| Effetto | Durata | Descrizione |
|---------|--------|-------------|
| Energy Vortex | 2500ms | Spirale rotante al centro impatto |
| Slash Trail | 600ms | Arco animato che simula il taglio |
| Connection Lines | 2000ms | Linee che collegano core a camera |

### 4.2 Energy Vortex
**File:** `ImpactVFX.java:117-227`

Componenti:
1. **Outer Spiral** (clockwise rotation)
2. **Inner Spiral** (counter-clockwise)
3. **Concentric Rings** (wave effect)
4. **Rays from Center**
5. **Bright Center Point**

```java
// Outer spiral
for (int i = 0; i <= spiralSegments; i++) {
    float t = (float) i / spiralSegments;
    float angle = rotation + t * spiralRotations * PI * 2;
    float radius = baseRadius * (1.0f - t * 0.6f);  // Narrows towards center

    float x = cos(angle) * radius;
    float y = sin(angle) * radius * 0.5f;  // Vertically flattened
    float z = sin(angle) * radius;

    consumer.addVertex(matrix, x, y, z)
        .setColor(r, g, b, alpha * (1.0f - t * 0.5f))
        .setNormal(0, 1, 0);
}
```

### 4.3 Slash Animation
**File:** `ImpactVFX.java:233-366`

L'animazione simula una lama che taglia attraverso il punto di impatto:

```
progress 0.0                  progress 0.5                  progress 1.0

     │                            │                              │
     │                         ───┼───                        ───┤
     │                            │                              │
     │                            │                              │
BLADE ─►                       BLADE                          ◄─ TRAIL
```

Componenti:
1. **Cut Trail** - Scia luminosa che segue la lama
2. **Blade Point** - Punto luminoso in movimento
3. **Spark Particles** - Scintille lungo la scia
4. **Outer Arc** - Bordo del taglio

### 4.4 Connection Lines
**File:** `ImpactVFX.java:395-480`

```java
// Main lines towards camera
for (int i = 0; i < lineCount; i++) {
    float angleOffset = (2 * PI * i / lineCount) + rotation * 0.2f;

    for (int j = 0; j <= segments; j++) {
        float t = (float) j / segments;

        // Interpolation towards camera
        float x = startX * (1 - t) + toCamera.x * lineLength * t;
        float y = toCamera.y * lineLength * t;
        float z = startZ * (1 - t) + toCamera.z * lineLength * t;

        // Pulse effect
        float pulse = sin(t * PI * 2 + rotation * 3) * 0.3f + 0.7f;
        float segmentAlpha = alpha * (1.0f - t * 0.7f) * pulse;

        consumer.addVertex(matrix, x, y, z)
            .setColor(r, g, b, segmentAlpha)
            .setNormal(0, 1, 0);
    }
}
```

---

## 5. RenderTypes Utilizzati

### 5.1 Per 2D HUD

| Metodo | Utilizzo |
|--------|----------|
| `GuiGraphics.fill()` | Background, bordi |
| `GuiGraphics.drawString()` | Testo |

### 5.2 Per 3D

| RenderType | Utilizzo | Note |
|------------|----------|------|
| `RenderType.debugQuads()` | Background pannello 3D | Filled quads |
| `RenderType.lines()` | Bordi, connection lines | Stable line rendering |
| `RenderType.debugLineStrip(width)` | VFX spirali, trails | Connected line strips |

### 5.3 Font Display Mode

```java
Font.DisplayMode.SEE_THROUGH  // Per testo 3D visibile attraverso geometry
```

---

## 6. Performance Considerations

### 6.1 Limiti Implementati

| Limite | Valore | Motivo |
|--------|--------|--------|
| Max 3D Panels | 12 | Evita overhead GPU |
| Max VFX Effects | 5 | Memory e CPU |
| Render Distance | 64 blocks | Distance culling |
| Cache TTL | 100ms | Body part cache |

### 6.2 Thread Safety

```java
// Liste thread-safe per evitare ConcurrentModificationException
private final List<Impact3DPanel> activePanels = new CopyOnWriteArrayList<>();
private static final List<ImpactEffect> activeEffects = new CopyOnWriteArrayList<>();
```

### 6.3 Cleanup

```java
// Rimuovi pannelli/effetti scaduti ogni frame
activePanels.removeIf(Impact3DPanel::isExpired);
activeEffects.removeIf(e -> e.isExpired(now));
```

---

## 7. Problemi di Rendering Identificati

### 7.1 Duplicazione Codice

Il rendering del contenuto è quasi identico tra `ImpactHudOverlay` e `Impact3DRenderer`:
- Stesse stringhe
- Stessa logica di layout
- Stessi colori

**Soluzione Proposta:** Estrarre logica comune in `ImpactHudContent` condiviso.

### 7.2 Hardcoded Strings

Molte stringhe sono hardcoded invece di usare i18n:
```java
String title = "Impact Analysis (Multi-Part & Mod Integrated)";  // Non tradotto
```

### 7.3 Posizione Fissa 2D

L'HUD 2D è sempre in top-right senza opzioni di configurazione:
```java
int panelX = screenWidth - panelWidth - 10;
int panelY = 10;
```

### 7.4 Z-Fighting Potenziale

Il pannello 3D usa offset Z fissi che potrebbero causare z-fighting in alcune situazioni:
```java
poseStack.translate(0, 0, -0.01f);  // Text offset
consumer.addVertex(matrix, x, y, 0.001f);  // Background offset
```

### 7.5 Formula String Ricalcolata

`getFormulaString()` viene chiamato ogni frame ma il risultato è immutabile:
```java
// In renderImpactPanel(), chiamato ogni frame:
g.drawString(font, "Local Part Calc: " + bd.getFormulaString(), ...);
```

**Soluzione:** Cache la stringa al momento della creazione di DamageBreakdown.
