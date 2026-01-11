# Clone Pulverizer - Design Completo

## Funzione
Riceve materiale dall'alto, lo frantuma con rulli dentati controrotanti, espelle il materiale processato.

---

## Schema Meccanico

```
            ┌─────────┐
            │ HOPPER  │  ← Tramoggia di carico (imbuto)
            └────┬────┘
                 │
         ┌───────┴───────┐
         │  FEED GUIDE   │  ← Guida materiale
         └───────┬───────┘
                 │
     ┌───────────┼───────────┐
     │     ╔═══╗ │ ╔═══╗     │  ← TWIN ROLLERS (rulli dentati)
     │     ║ ↓ ║ │ ║ ↓ ║     │
     │     ╚═══╝ │ ╚═══╝     │
     │           │           │
     │   CRUSHING CHAMBER    │  ← Camera frantumazione
     └───────────┬───────────┘
                 │
            ┌────┴────┐
            │ SCREEN  │  ← Griglia vaglio
            └────┬────┘
                 │
            ┌────┴────┐
            │DISCHARGE│  ← Scarico
            └─────────┘

          ┌──────────┐
          │  MOTOR   │  ← Motore laterale
          │  ⚙️ ⚙️   │
          └──────────┘
```

---

## Elementi del Modello

### Checklist Componenti

| # | Elemento | Bone | Dimensioni | Animazione | Texture | Status |
|---|----------|------|------------|------------|---------|--------|
| 1 | Base Platform | base_plate | 14x1x14 | Drop dall'alto | Grigio griglia | ✅ |
| 2 | Crushing Chamber | chamber | 12x6x12 | Emerge dal basso | Grigio pannelli | ✅ |
| 3 | Twin Roller Left | roller_left | 3x3x10 | Rotazione continua | Grigio cilindro dentato | ✅ |
| 4 | Twin Roller Right | roller_right | 3x3x10 | Rotazione opposta | Grigio cilindro dentato | ✅ |
| 5 | Hopper | hopper | 8x4x8 (imbuto) | Cala dall'alto | Grigio scuro | ❌ |
| 6 | Feed Guide | feed_guide | 6x2x6 | Emerge con chamber | Grigio | ❌ |
| 7 | Screen/Grate | screen | 10x1x10 | Vibrazione | Grigio griglia | ❌ |
| 8 | Discharge Chute | discharge | 4x3x6 | Si estende | Grigio scuro | ❌ |
| 9 | Motor | motor | 6x5x4 | Nessuna | Grigio scuro | ❌ |
| 10 | Drive Belt | belt | 1x1x6 | Movimento lineare | Nero/grigio | ❌ |
| 11 | Control Panel | panel | 4x3x1 | Nessuna | Grigio + LED cyan | ❌ |
| 12 | Support Legs (x4) | leg_nw/ne/sw/se | 2x2x2 | Dispiegamento | Grigio | ✅ (come supports) |

---

## Gerarchia Bones

```
root
├── base_plate          # Piattaforma base
│   ├── leg_nw          # Piedino nord-ovest
│   ├── leg_ne          # Piedino nord-est
│   ├── leg_sw          # Piedino sud-ovest
│   └── leg_se          # Piedino sud-est
│
├── chamber             # Camera di frantumazione
│   ├── roller_left     # Rullo sinistro (rotazione Z)
│   ├── roller_right    # Rullo destro (rotazione Z opposta)
│   ├── feed_guide      # Guida alimentazione
│   └── screen          # Griglia vaglio
│
├── hopper              # Tramoggia superiore
│
├── discharge           # Scarico inferiore
│
├── motor               # Motore laterale
│   └── belt            # Cinghia trasmissione
│
└── panel               # Pannello controllo
```

---

## Animazioni

### Deploy (assembaggio - 6 secondi)

| Tempo | Elemento | Azione |
|-------|----------|--------|
| 0.0s | base_plate | Cade dall'alto (Y=8 → Y=0) |
| 0.3s | leg_* | Si dispiegano dagli angoli |
| 0.8s | chamber | Emerge dal centro (Y=-6 → Y=1) |
| 1.2s | motor | Scivola lateralmente in posizione |
| 1.5s | belt | Si estende dal motore alla camera |
| 1.8s | discharge | Si estende verso il basso |
| 2.2s | feed_guide | Emerge dalla camera |
| 2.6s | hopper | Cala dall'alto sulla camera |
| 3.0s | panel | Scivola in posizione laterale |
| 3.2s | roller_* | Iniziano a ruotare (transizione ad active) |

### Active (funzionamento - loop 2 secondi)

| Elemento | Animazione |
|----------|------------|
| roller_left | Rotazione Z continua (360°/2s) |
| roller_right | Rotazione Z opposta (-360°/2s) |
| screen | Vibrazione leggera (Y ±0.05) |
| belt | Movimento texture o leggero shake |
| chamber | Micro-vibrazione (0.02 su tutti gli assi) |

---

## Layout Texture (64x64)

```
+--------+--------+--------+--------+
| BASE   | CHAMBER| CHAMBER| HOPPER |
| TOP    | TOP    | SIDE   | SIDE   |
| 0-16   | 16-32  | 32-48  | 48-64  |
| 0-16   | 0-16   | 0-16   | 0-16   |
+--------+--------+--------+--------+
| ROLLER | ROLLER | SCREEN | DISCHG |
| SIDE   | END    | TOP    | SIDE   |
| 0-16   | 16-24  | 24-40  | 40-56  |
| 16-32  | 16-24  | 16-32  | 16-32  |
+--------+--------+--------+--------+
| MOTOR  | MOTOR  | BELT   | PANEL  |
| SIDE   | TOP    |        | FRONT  |
| 0-16   | 16-28  | 28-36  | 36-48  |
| 32-48  | 32-44  | 32-40  | 32-44  |
+--------+--------+--------+--------+
| LEG    | LEG    |        |        |
| SIDE   | TOP    | (free) | (free) |
| 0-8    | 8-16   |        |        |
| 48-56  | 48-56  |        |        |
+--------+--------+--------+--------+
```

---

## Dettagli Texture per Elemento

### 1. Base Platform (✅ Completato)
- **Top**: Griglia grigia 16x16, linee ogni 4px
- **Side**: Bordo grigio scuro 1px

### 2. Crushing Chamber (❌ Da fare)
- **Top**: Pannello grigio con apertura centrale per rulli
- **Side**: Pannelli industriali con bulloni, linee orizzontali

### 3. Twin Rollers (❌ Da fare)
- **Side (cilindro)**: Pattern denti verticali alternati
- **End (cerchio)**: Cerchio con asse centrale

### 4. Hopper (❌ Da fare)
- **Side**: Forma trapezoidale, pannelli grigi con rivetti
- **Inside**: Più scuro

### 5. Feed Guide (❌ Da fare)
- **Side**: Pannello grigio con freccia direzionale

### 6. Screen/Grate (❌ Da fare)
- **Top**: Pattern griglia fitta (linee ogni 2px)

### 7. Discharge Chute (❌ Da fare)
- **Side**: Tubo/scivolo grigio scuro

### 8. Motor (❌ Da fare)
- **Side**: Box grigio scuro con ventole
- **Top**: Griglia ventilazione

### 9. Drive Belt (❌ Da fare)
- **All**: Nero/grigio scuro, pattern linee

### 10. Control Panel (❌ Da fare)
- **Front**: Grigio con 2-3 LED cyan, pulsante

### 11. Support Legs (✅ Completato come supports)
- **Side**: Grigio uniforme
- **Top/Bottom**: Grigio scuro

---

## Prossimi Step

1. ❌ Aggiornare .geo.json con nuovi bones
2. ❌ Aggiornare UV mapping per nuovi elementi
3. ❌ Creare texture per ogni nuovo elemento
4. ❌ Aggiornare animazione deploy
5. ❌ Aggiungere animazione active con rotazione rulli
6. ❌ Test in-game

---

## Note Tecniche

- **Rulli**: Usare cilindri (cubo 3x3 ruotato o approssimazione ottagonale)
- **Hopper**: Forma imbuto = piramide tronca invertita
- **Vibrazione**: Offset ±0.02-0.05 blocchi, randomizzato
- **Rotazione rulli**: 180° per secondo = aspetto realistico

---

*Creato: 2026-01-11*
