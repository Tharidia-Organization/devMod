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
│  [Undo][Redo] │ [History][Export][Import][Presets] │ [Apply]       │  60px
│               │           [Reset] [Cancel]         │               │
└─────────────────────────────────────────────────────────────────────┘
     140px                      390px
```

## 1.3 Posizioni Esatte (in pixel)

### Header Zone (y: 0 → 28)
| Elemento | X | Y | Width | Height |
|----------|---|---|-------|--------|
| Tab buttons | centered | 4 | 70 each | 20 |
| Mode badge | PANEL_WIDTH - 110 | 4 | 100 | 20 |
| Close button | PANEL_WIDTH - 25 | 4 | 20 | 20 |

### Left Column (x: 10, width: 140)
| Elemento | X | Y | Width | Height |
|----------|---|---|-------|--------|
| Preview | 12 | 20 | 130 | 130 |
| Rotate hint | 12 | 150 | 130 | 12 |
| Slot selector | 10 | 170 | 130 | 70 |
| Selected piece card | 10 | 248 | 130 | 46 |
| Item info | 10 | 300 | 130 | 100 |
| Dirty indicator | 15 | 360 | 120 | 15 |

> Nota: la card "Selected piece" sotto i quattro slot mostra il pezzo attivo (icona + label) e permette di ciclare rapidamente gli slot con un click.

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
| Actions row (History/Export/Import/Presets/Reset/Cancel) | 130 | 365 | 320 | 22 |
| Apply button | 420 | 365 | 120 | 50 |

> Nota: la row di quick actions è sempre visibile; ogni pulsante ha hover/border accent, senza dropdown. Apply mostra `Preview only` / `No changes` / `Apply (n)` in base allo stato.

> Nota overlay: il pannello Presets è modale (overlay scuro a schermo intero, pannello centrato) e viene renderizzato sopra al modello 3D.

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
│  [Undo][Redo] │ [History][Export][Presets] │ [Apply]            │  FOOTER: FIXED
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