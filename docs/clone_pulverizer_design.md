# Clone Pulverizer - Design Completo

## Funzione
Riceve materiale dall'alto, lo frantuma con rulli dentati controrotanti, espelle il materiale processato.

---

## Stato Attuale del Modello

### Elementi Completati
| # | Elemento | Bone | Dimensioni Finali | Note |
|---|----------|------|-------------------|------|
| 1 | Base Platform | base_plate | 14x1x14 | Griglia grigia |
| 2 | Crushing Chamber | chamber | 12x6x12 | Pannelli industriali, apertura centrale |
| 3 | Twin Roller Left | roller_left | 6x8x8 (croce) | Forma a T, no Z-fighting |
| 4 | Twin Roller Right | roller_right | 6x8x8 (croce) | Offset 45° per mesh |
| 5 | Support Pillars | support_nw/ne/sw/se | 3x5x3 | Grigi, animazione dispiegamento |
| 6 | Hopper | hopper | 14x6x14 (imbuto) | 3 livelli: 14→10→6, sale dal basso |
| 7 | Feed Guide | feed_guide | 6x1x6 | 4 rail che guidano materiale |
| 8 | Discharge Chute | discharge | 6x3x7 | U-shape, esce dal blocco (Z=11) |
| 9 | Control Panel | panel | 6x4x1 | Lato Nord, schermo cyan + pulsanti |
| 10 | Belt Frame | belt | 14x4x6 | Telaio rettangolare esterno ai rulli |

### Elementi Rimossi (moduli separati)
| # | Elemento | Motivo |
|---|----------|--------|
| - | Motor | Modulo separato |

---

## Dettagli Tecnici Elementi Completati

### Twin Rollers - Design Finale

**Struttura (forma a T senza Z-fighting)**:
```
        ┌───┐
        │ V │  Y=10-13 (vertical arm top, 2x3x8)
        │ T │
┌───────┼───┼───────┐
│   H   │   │   H   │  Y=8-10 (horizontal arm, 6x2x8)
└───────┴───┴───────┘
        │ V │
        │ B │  Y=5-8 (vertical arm bottom, 2x3x8)
        └───┘
```

**Posizioni**:
- roller_left: pivot [-3, 9, 0]
- roller_right: pivot [3, 9, 0]
- Gap centrale: 0 unità (i denti si incastrano)

**Sincronizzazione Ingranaggi**:
- roller_left: 0° → 45° → 90° → ... → 360° (ogni 0.25s)
- roller_right: 45° → 0° → -45° → ... → -315° (offset 45°)
- Risultato: denti si incastrano come veri ingranaggi

**UV Texture**:
- Braccia: UV [0, 24] e [0, 27]
- Punte denti: UV [0, 30] (più chiaro)

---

## Gerarchia Bones Attuale

```
root
├── base_plate          # 14x1x14, Y=0
│
├── chamber             # 12x6x12, Y=1-7
│   ├── roller_left     # 6x8x8 croce, pivot [-3, 9, 0]
│   ├── roller_right    # 6x8x8 croce, pivot [3, 9, 0]
│   ├── core_upper      # 10x2x10, Y=4-6
│   ├── feed_guide      # 6x1x6, 4 rail guide
│   ├── discharge       # 6x3x5, U-channel esterno
│   └── panel           # 6x4x1, lato Nord
│
├── belt                # 14x4x6, telaio esterno ai rulli
│
├── support_nw          # 3x5x3, angolo NW
├── support_ne          # 3x5x3, angolo NE
├── support_sw          # 3x5x3, angolo SW
├── support_se          # 3x5x3, angolo SE
│
└── hopper              # Imbuto 3 livelli, Y=11-17
    ├── top ring        # 14x2x14, Y=15-17
    ├── middle ring     # 10x2x10, Y=13-15
    └── bottom ring     # 6x2x6, Y=11-13
```

---

## Layout Texture (64x64) - UV Indipendenti per Bone

Ogni bone ha la sua regione UV dedicata per consentire texture individuali e highlight.

```
Y=0-15 (Riga 0):
┌──────────────┬────────────┬────────────┬────────────────────┐
│  BASE_PLATE  │  CHAMBER   │ CORE_UPPER │     SUPPORTS       │
│   [0,0]      │  [16,0]    │  [32,0]    │ NW[48,0] NE[54,0]  │
│   14x14      │  12x12     │  10x10     │ SW[48,11] SE[58,11]│
│  top/down    │ top/down   │ top/down   │   3x5 sides each   │
└──────────────┴────────────┴────────────┴────────────────────┘

Y=16-31 (Riga 1):
┌──────────┬────────────┬─────────┬─────────┬──────────────────┐
│ CHAMBER  │   HOPPER   │ROLLER_L │ROLLER_R │       BELT       │
│  [0,16]  │  [16,16]   │ [32,16] │ [40,16] │     [48,16]      │
│  12x6    │  14x14     │  8x14   │  8x14   │      8x8         │
│  sides   │  walls     │  all    │  all    │   bars+wraps     │
└──────────┴────────────┴─────────┴─────────┴──────────────────┘

Y=24-31 (Riga 2 - dettagli):
┌────────────┬────────────┬────────────┬────────────┐
│ CORE_SIDE  │ FEED_GUIDE │   HOPPER   │            │
│  [0,24]    │  [12,24]   │ mid[16,24] │  (free)    │
│  10x4      │   6x8      │  10x8      │            │
│  cyan glow │   rails    │  walls     │            │
└────────────┴────────────┴────────────┴────────────┘

Y=32-47 (Riga 3):
┌────────────┬────────────┬──────────────────────────┐
│  DISCHARGE │    PEGS    │         PANEL            │
│   [0,32]   │  L[16,32]  │ frame[0,52] screen[8,52] │
│   12x14    │  R[18,32]  │ buttons[8,56]            │
│  floor+wall│   2x2 each │   6x4 + 4x2 + 4x1        │
└────────────┴────────────┴──────────────────────────┘

Y=48-63 (Riga 4):
┌────────────────────────────────────────────────────┐
│  BASE_PLATE_SIDES [0,48-51] 14x4 (N,E,S,W separate)│
│  PANEL [0,52] frame + [8,52] screen + [8,56] btns  │
│  FREE SPACE: [14,48] → [63,63] per dettagli extra  │
└────────────────────────────────────────────────────┘
```

### Mappa UV Dettagliata per Bone

| Bone | UV Region | Size | Note |
|------|-----------|------|------|
| base_plate | [0,0] top, [0,48-51] sides | 14x14, 14x1 | 4 lati separati |
| chamber | [16,0] top, [0,16] sides | 12x12, 12x6 | |
| core_upper | [32,0] top, [0,24] sides | 10x10, 10x2 | Cyan glow |
| support_nw | [48,0] | 3x5+3x3 | Indipendente |
| support_ne | [54,0] | 3x5+3x3 | Indipendente |
| support_sw | [48,11] | 3x5+3x3 | Indipendente |
| support_se | [58,11] | 3x5+3x3 | Indipendente |
| roller_left | [32,16] | 8x14 | Tutti i cubes |
| roller_right | [40,16] | 8x14 | Tutti i cubes |
| hopper | [16,16], [16,22], [16,24] | Multiple | Anelli separati |
| feed_guide | [12,24] | 6x8 | 4 rail |
| discharge | [0,32], [0,38], [0,45] | Multiple | Floor/walls/lip |
| panel | [0,52], [8,52], [8,56] | 6x4, 4x2, 4x1 | Frame/screen/btns |
| belt | [48,16], [48,18], [48,20] | 7x1, 1x3 | Bars/wraps |
| peg_left | [16,32] | 2x2 | Indipendente |
| peg_right | [18,32] | 2x2 | Indipendente |

---

## Animazioni Attuali

### Deploy (4 secondi)
| Tempo | Elemento | Azione |
|-------|----------|--------|
| 0.0s | base_plate | Cade (Y=6 → Y=0) |
| 0.4s | chamber | Emerge (Y=-6 → Y=0) |
| 0.7s | belt | Sale (Y=-6 → Y=0) |
| 0.8s | discharge | Scivola fuori (Y=-2, Z=-5 → Y=0, Z=0) |
| 0.9s | core_upper | Sale (Y=-3 → Y=0) |
| 1.2s | panel | Si apre (rot X: 90° → 0°) |
| 1.4s | support_nw | Dispiegamento |
| 1.6s | support_ne | Dispiegamento |
| 1.8s | support_sw | Dispiegamento |
| 2.0s | support_se | Dispiegamento |
| 2.6s | hopper | Sale dal basso (Y=-10 → Y=0) |

### Active (loop 2 secondi)
| Elemento | Animazione |
|----------|------------|
| roller_left | Rotazione Z: 0° → 360° |
| roller_right | Rotazione Z: 45° → -315° (controrotante, offset) |
| core_upper | Leggera oscillazione Y e rotazione |

---

## Note Tecniche

### Z-Fighting
- **Problema**: Facce sovrapposte causano flickering
- **Soluzione**: Mai sovrapporre cubes nello stesso bone
- **Esempio roller**: Braccia verticali NON si sovrappongono all'orizzontale

### Sincronizzazione Ingranaggi
- **Formula**: Se gear A ruota di +X°, gear B ruota di -X°
- **Offset**: 45° per forma a croce (4 denti = 90° tra denti)
- **Mesh perfetto**: Quando A ha dente a 0°, B ha vuoto a 0°

### Palette Colori
```
GRIGI:
- EDGE_LIGHT  = (73, 78, 90)
- MID         = (57, 61, 71)
- DARK        = (50, 53, 61)
- DARKER      = (40, 42, 50)
- DARKEST     = (32, 34, 42)

CYAN (solo core_upper):
- CYAN_BRIGHT = (133, 242, 242)
- CYAN_BORDER = (125, 220, 229)
```

---

*Ultimo aggiornamento: 2026-01-12 - UV indipendenti per ogni bone*
