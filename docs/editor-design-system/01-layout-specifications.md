# Layout Specifications

## 1.1 Dimensioni Standard

```
┌─────────────────────────────────────────────────────────────────────┐
│                         ITEM EDITOR                                 │
│                        550px × 420px                                │
└─────────────────────────────────────────────────────────────────────┘
```

| Costante | Valore | Descrizione |
|----------|--------|-------------|
| `PANEL_WIDTH` | **550px** | Larghezza totale pannello |
| `PANEL_HEIGHT` | **420px** | Altezza totale pannello |
| `HEADER_HEIGHT` | **28px** | Altezza header con tabs |
| `FOOTER_HEIGHT` | **60px** | Altezza footer con bottoni |
| `LEFT_COLUMN_WIDTH` | **140px** | Colonna sinistra (preview + slots + info) |
| `CONTENT_WIDTH` | **390px** | Area contenuto tabs |
| `PREVIEW_SIZE` | **130px** | Dimensione preview 3D |
| `SLOT_AREA_HEIGHT` | **70px** | Area slot selector |
| `INFO_PANEL_HEIGHT` | **100px** | Pannello info item |

## 1.2 Layout Master

```
┌─────────────────────────────────────────────────────────────────────┐
│ [Tab1] [Tab2] [Tab3] [Tab4] [Tab5]               [MODE BADGE]  [X]  │  28px
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌────────────┐   ┌─────────────────────────────────────────────┐  │
│  │            │   │                                             │  │
│  │  PREVIEW   │   │                                             │  │
│  │  130x130   │   │                                             │  │
│  │            │   │           TAB CONTENT AREA                  │  │
│  │  [Rotate]  │   │                                             │  │  280px
│  └────────────┘   │           - Sliders                         │  │
│                   │           - Lists                           │  │
│  ┌────────────┐   │           - Pickers                         │  │
│  │   SLOTS    │   │           - Toggles                         │  │
│  │  [1][2]    │   │                                             │  │
│  │  [3][4]    │   │                                             │  │
│  └────────────┘   │                                             │  │
│                   └─────────────────────────────────────────────┘  │
│  ┌────────────┐                                                    │
│  │ ITEM INFO  │                                                    │
│  │ Name       │                                                    │
│  │ Stats      │                                                    │
│  │ ● 3 unsaved│                                                    │
│  └────────────┘                                                    │
├─────────────────────────────────────────────────────────────────────┤
│  [Undo][Redo] │ [◄][History][Export][Import][Presets][Templates][Recipe][Reset][Cancel][►] │ [Apply] │  60px
└───────────────────────────────────────────────────────────────────────────────────────────────────────┘
     140px                                    390px
```

## 1.3 Posizioni Esatte (in pixel)

### Header Zone (y: 0 → 28)
| Elemento | X | Y | Width | Height |
|----------|---|---|-------|--------|
| Tab buttons | centered | 4 | 70 each | 20 |
| Mode badge | PANEL_WIDTH - 110 | 4 | 100 | 20 |
| Close button | PANEL_WIDTH - 25 | 4 | 20 | 20 |

### Left Column (x: 5, width: 140)
| Elemento | X | Y | Width | Height |
|----------|---|---|-------|--------|
| Preview | 5 | 20 | 130 | 130 |
| Rotate hint | 5 | 150 | 130 | 12 |
| Slot selector | 5 | 170 | 130 | 70 |
| Armor card | 5 | 248 | 130 | 46 |
| Item info | 5 | 260 | 130 | 100 |
| Dirty indicator | 10 | 360 | 120 | 15 |

> **Nota**: Coordinate X relative all'origine della left column. Il codice usa offset 5px per massimizzare lo spazio utile. La posizione Y di Item info (260) è stata ottimizzata per evitare sovrapposizioni con Armor card.

### Content Area (x: 150, width: 390)
| Elemento | X | Y | Width | Height |
|----------|---|---|-------|--------|
| Tab content | 150 | 35 | 390 | 280 |
| Content padding | 8px internal | | | |

### Footer Zone (y: 360 → 420)
| Elemento | X | Y | Width | Height |
|----------|---|---|-------|--------|
| Undo button | 10 | 365 | 50 | 22 |
| Redo button | 65 | 365 | 50 | 22 |
| Separator | 120 | 365 | 1 | 50 |
| Actions row | 130 | 365 | dinamico | 28 |
| Apply button | PANEL_WIDTH - 112 | centrato | 112 | 36 |

**Azioni disponibili nel footer** (inline scrollabili):
- History, Export, Import, Presets, Templates, Recipe, Reset, Cancel

> **Nota implementazione**: Le actions sono bottoni inline con scroll orizzontale (frecce < >) quando non entrano nel viewport. Il bottone Apply ha dimensioni 112×36 (ottimizzate rispetto al design originale 120×50).

> **Nota overlay**: I pannelli Presets, Templates e Crafting sono modali (overlay scuro a schermo intero, pannello centrato).

## Scroll Policy: Rigid Layout

Tutte le tab condividono lo **stesso layout rigido**. Lo scroll è consentito **solo** nel content area.

### Layout Zones

```
┌─────────────────────────────────────────────────────────────────┐
│ [Tab1] [Tab2] [Tab3] [Tab4] [Tab5]               [MODE]    [X]  │  HEADER: FIXED
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌────────────┐   ┌─────────────────────────────────────────┐  │
│  │            │   │                                         │  │
│  │  PREVIEW   │   │                                         │  │
│  │   FIXED    │   │       SCROLLABLE CONTENT AREA           │  │
│  │            │   │                                         │  │
│  └────────────┘   │  ┌─────────────────────────────────┐    │  │
│                   │  │ Section 1                       │    │  │  LEFT: FIXED
│  ┌────────────┐   │  │ Section 2                       │    │  │
│  │   SLOTS    │   │  │ Section 3                       │◄───┼──┼── SCROLL
│  │   FIXED    │   │  │ Section 4                       │    │  │   ONLY HERE
│  └────────────┘   │  │ Section 5                       │    │  │
│                   │  │ ...                             │    │  │
│  ┌────────────┐   │  └─────────────────────────────────┘    │  │
│  │   INFO     │   │                                         │  │
│  │   FIXED    │   └─────────────────────────────────────────┘  │
│  └────────────┘                                                │
├─────────────────────────────────────────────────────────────────┤
│  [Undo][Redo] │ [◄][...actions scrollabili...][►] │ [Apply]      │  FOOTER: FIXED
└─────────────────────────────────────────────────────────────────┘
```

### Zone Behavior Table

| Zona | Scroll | Dimensioni | Contenuto |
|------|--------|------------|-----------|
| **Header** | ❌ FIXED | 28px height | Tab bar, mode badge, close button |
| **Left Column** | ❌ FIXED | 140px width × 280px height | Preview, slots, item info |
| **Content Area** | ✅ SCROLL | 390px width × 280px viewport | Tab-specific sections |
| **Footer** | ❌ FIXED | 60px height | Action buttons |

## Preview Component

### Specifiche comuni
| Proprietà | Valore |
|-----------|--------|
| Size | 130x130px |
| Position | Left column, top |
| Background | Transparent/subtle gradient |
| Border | 1px, UIConstants.Border.MUTED |

### Interazione
- **Mouse drag**: Ruota il modello orizzontalmente
- **Mouse scroll**: Zoom in/out (opzionale)
- **Fallback**: Auto-rotate lento se nessuna interazione

### Rendering
| Editor | Contenuto |
|--------|-----------|
| Armor | Player model con armatura equipaggiata, slot attivo evidenziato |
| Weapon | Item 3D flottante con rotazione |

## Responsive Layout & Scaling

### Hotbar Reserve
Il layout riserva **24px** in basso per evitare sovrapposizioni con la hotbar vanilla di Minecraft.

```java
// EditorLayout.java
int hotbarReserve = ScaledCoord.scaleDim(24, scale);
panelHeight = Math.max(panelHeight - hotbarReserve, minHeight);
```

### UI Scaling
Tutte le dimensioni sono scalate dinamicamente tramite `ScaledCoord.scaleDim()`:

| Scale Factor | Panel Size | Uso tipico |
|--------------|------------|------------|
| 1.0x | 550×420 | Schermi 1080p |
| 1.25x | 687×525 | Schermi 1440p |
| 1.5x | 825×630 | Schermi 4K |
| 2.0x | 1100×840 | HiDPI |

### Classi di riferimento
- `EditorLayout.java` - Calcolo bounds centralizzato
- `EditorScaleCalculator.java` - Calcolo scale factor
- `ScaledCoord.java` - Utility per scaling coordinate
- `ResponsiveLayout.java` - Layout responsive con breakpoints

## Component System

Il sistema UI utilizza componenti riutilizzabili per garantire consistenza visiva e ridurre la duplicazione del codice.

### Gerarchia Componenti

```
EditorComponent (interface - futuro)
├── Input Components
│   ├── EditorButton     - Bottone multi-stile con toggle
│   ├── EditorSlider     - Slider numerico con input
│   ├── EditorToggle     - Switch boolean
│   └── EditorTextField  - Campo testo con validazione
├── Layout Components
│   ├── ButtonRow        - Layout orizzontale bottoni
│   ├── HeaderComponent  - Tab bar + badge + close
│   ├── FooterComponent  - Undo/Redo + actions + apply
│   └── LeftColumnComponent - Preview + slots + info
└── Overlay Components
    ├── BaseOverlay (abstract) - Template per modali
    ├── ConfirmDialog
    ├── HelpOverlay
    ├── TemplateOverlay
    └── CraftingInfoPanel
```

### Pattern EditorButton

```java
// Stili disponibili
EditorButton.Style.NORMAL   // Default grigio
EditorButton.Style.PRIMARY  // Azione principale (teal)
EditorButton.Style.DANGER   // Azione distruttiva (rosso)
EditorButton.Style.SUCCESS  // Conferma (verde)
EditorButton.Style.GHOST    // Trasparente

// Taglie
EditorButton.Size.SMALL     // 16px height
EditorButton.Size.MEDIUM    // 20px height (default)
EditorButton.Size.LARGE     // 24px height

// Esempio uso
EditorButton applyBtn = new EditorButton("apply", "Apply")
    .style(EditorButton.Style.SUCCESS)
    .size(EditorButton.Size.LARGE)
    .hotkeyHint("Ctrl+Enter")
    .onClick(() -> applyChanges());
```

### Pattern BaseOverlay

Classe astratta per overlay modali che fornisce:
- Rendering del backdrop scuro
- Centratura automatica del pannello
- Gestione ESC per chiusura
- Click fuori dal pannello per chiusura

```java
public class MyOverlay extends BaseOverlay {
    @Override
    protected int getPanelWidth() { return 320; }

    @Override
    protected int getPanelHeight() { return 200; }

    @Override
    protected void renderContent(GuiGraphics g, Font font,
                                 int x, int y, int w, int h,
                                 int mouseX, int mouseY) {
        // Render contenuto specifico
    }
}
```

### Pattern ButtonRow

Layout orizzontale per gruppi di bottoni con allineamento configurabile:

```java
ButtonRow actionButtons = new ButtonRow()
    .add(cancelButton)
    .add(applyButton)
    .gap(EditorSpacing.S)
    .alignment(ButtonRow.Alignment.RIGHT);

actionButtons.render(graphics, x, y, width, mouseX, mouseY);
```

### Costanti di Riferimento

| Categoria | Classe | Uso |
|-----------|--------|-----|
| Colori | `UIConstants.*` | Background, Border, Text, Accent |
| Spacing | `EditorSpacing.*` | XS(4), S(8), M(12), L(16), XL(24) |
| Dimensioni | `EditorDimensions.*` | Button, Slider, Toggle heights |
| Scaling | `ScaledCoord.scaleDim()` | Tutte le coordinate |
| Bounds | `ResponsiveLayout.Rect` | Hit detection, layout |

### Best Practices

1. **Usare sempre EditorButton** invece di rendering manuale per i bottoni
2. **Usare UIConstants** per tutti i colori
3. **Usare EditorSpacing** per tutti gli spacing (multipli di 4px)
4. **Usare ScaledCoord.scaleDim()** per tutte le coordinate
5. **Estendere BaseOverlay** per overlay modali
6. **Usare ButtonRow** per gruppi di bottoni orizzontali