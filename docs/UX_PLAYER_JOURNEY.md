# Endurance Quest - Player Journey & UX Analysis

## Player Persona

**Nome:** Alex, Minecraft Veteran
**Obiettivo:** Completare una quest Endurance con amici, ottenere loot raro
**Contesto:** Prima volta con il sistema Endurance, conosce Minecraft combat

---

## Journey Map Completa

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           PLAYER JOURNEY                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  DISCOVERY        SETUP           COMBAT          PROGRESSION      REWARD   │
│      │              │               │                  │             │      │
│      ▼              ▼               ▼                  ▼             ▼      │
│  ┌──────┐      ┌──────┐       ┌──────────┐      ┌──────────┐   ┌─────────┐ │
│  │ Open │      │Party │       │  Fight   │      │  Perk    │   │Complete │ │
│  │ Menu │ ──► │Screen│ ──►  │  Waves   │ ──► │ Select   │──►│ Screen  │ │
│  └──────┘      └──────┘       └──────────┘      └──────────┘   └─────────┘ │
│                    │               │                  │             │      │
│               Invite          Kill Mobs          Choose         Get Loot   │
│               Friends         See HUD            Perks          Tokens     │
│               Pick Mob        Style Rank         Continue       Badges     │
│               Ready Up        Combo              or Exit                   │
│                                                                             │
│  Emotion:     Curious         Excited           Strategic      Satisfied   │
│  ────────────────────────────────────────────────────────────────────────  │
│  Anxiety:     Low             Medium→High       Medium          Low        │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Fase 1: Party Setup (PartyScreen)

### Cosa Vede il Player

```
┌─────────────────────────────────────────────────────────────────┐
│  [PVE_COOP] [RAID_BOSS] [EVENT]              Quest Type Tabs    │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  PARTY (2/4)          │  MOB SELECTION      │  3D PREVIEW       │
│  ─────────────        │  ──────────────     │  ──────────       │
│  ★ Alex (You) ✓      │  [Search...]        │    ╭───────╮      │
│  ● Marco       ✓      │  [All][MC][T][E]    │    │       │      │
│                       │                      │    │  👾   │      │
│  [Invite: ____]      │  > Zombie     🟢    │    │       │      │
│                       │    Skeleton   🟡    │    ╰───────╯      │
│                       │    Creeper    🟠    │                    │
│                       │    Enderman   🔴    │  HP: 20 → 32      │
│                       │    Wither     🟣    │  DMG: 5 → 8       │
│                       │                      │                    │
├─────────────────────────────────────────────────────────────────┤
│  WAVE PREVIEW: [◄] Wave 5 [►]  Mobs: 15  HP: 32  Elite: 10%    │
├─────────────────────────────────────────────────────────────────┤
│              [Ready]                    [Start Quest]            │
└─────────────────────────────────────────────────────────────────┘
```

### UX Analysis

| Elemento | Valutazione | Note |
|----------|-------------|------|
| Leggibilità party list | ⭐⭐⭐⭐⭐ | Chiaro chi è leader (★), chi ready (✓) |
| Mob selection | ⭐⭐⭐⭐ | Filtri per tier, ma molti mob da scrollare |
| 3D Preview | ⭐⭐⭐⭐⭐ | Drag to rotate, stats scaled per party |
| Wave preview | ⭐⭐⭐⭐⭐ | Slider mostra scaling, ottimo per planning |
| Call to Action | ⭐⭐⭐⭐ | "Start Quest" visibile ma potrebbe essere più prominente |

### Friction Points Identificati

1. **Search non auto-focus**: Player deve cliccare nella search box
2. **Tier icons piccole**: I colori tier potrebbero essere più evidenti
3. **No mob favorites**: Nessun modo per salvare mob preferiti

### Recommendations

- [ ] Auto-focus search box quando si apre mob selection
- [ ] Aggiungere sistema "Recent Mobs" o "Favorites"
- [ ] Tooltip su hover dei tier colors per spiegare difficoltà

---

## Fase 2: Combat Loop (Gameplay HUD)

### Cosa Vede il Player

```
┌────────────────────────────────────────────────────────────────────────────┐
│                                                                             │
│  ┌─────────────────────────┐                                               │
│  │ ⚔ Zombie Hunt    [250]  │                                               │
│  │ Wave 3/10      [12 kills]│                     ┌─────────────────┐      │
│  │ ████████░░ 8/10 Mobs    │                     │   COMBO: 15     │      │
│  │ [⚡Speed] [💀Curse]     │                     │   ══════════    │      │
│  │ DMG: 150 / 25           │                     │   S-RANK        │      │
│  │ Style: [████░] S        │                     │   ══════════    │      │
│  └─────────────────────────┘                     └─────────────────┘      │
│                                                                             │
│  ┌─────────────┐                                                           │
│  │ PARTY (2)   │                                                           │
│  │ ★ Alex ████ │                           GAME                            │
│  │ ● Marco ██░░│                           WORLD                           │
│  └─────────────┘                                                           │
│                                                                             │
│                                                                             │
│                                      +25                                    │
│                                    HEADSHOT!                                │
│                                                                             │
│                                                                             │
└────────────────────────────────────────────────────────────────────────────┘
```

### Feedback Loop Analysis

| Azione Player | Feedback Immediato | Feedback Delayed |
|--------------|-------------------|------------------|
| Colpisce mob | Damage number popup | Combo +1, Style points |
| Kill mob | Kill notification | Wave progress bar |
| Headshot | "HEADSHOT!" text | Bonus style points |
| Multi-kill | "DOUBLE KILL!" etc | Massive style bonus |
| Prende danno | Screen flash rosso | Combo halved, style penalty |
| Combo lost | "COMBO LOST!" overlay | Rank down animation |

### UX Analysis

| Elemento | Valutazione | Note |
|----------|-------------|------|
| Quest HUD visibility | ⭐⭐⭐⭐⭐ | Posizione top-left non intrusiva |
| Combo counter | ⭐⭐⭐⭐⭐ | Grande, visibile, pulsante |
| Style rank | ⭐⭐⭐⭐ | Barra progresso chiara, ma rank letters piccole |
| Party HUD | ⭐⭐⭐⭐ | Health bars utili, ma nomi troncati |
| Damage numbers | ⭐⭐⭐⭐⭐ | Colorati per tipo, scaling per danno |
| Wave modifiers | ⭐⭐⭐ | Icone piccole, potrebbero essere più chiare |

### Emotional Peaks

```
Excitement Level
     │
  ██ │                              ★ Boss Kill
  ██ │         ★ Multi-Kill
  ██ │    ★ Rank Up
  █░ │                    ★ Wave Complete
  ░░ │ ★ First Kill
  ░░ │
     └────────────────────────────────────────► Time
        Wave Start    Mid-Wave    Wave End
```

### Friction Points

1. **Wave modifiers confusi**: Icone senza tooltip durante combat
2. **No audio cue per rank up**: Solo visual feedback
3. **Party health non numerica**: Solo barra, non HP esatto

### Recommendations

- [ ] Aggiungere sound effect distintivo per rank up (S→SS→SSS)
- [ ] Tooltip on-hover per wave modifiers (durante pause)
- [ ] Opzione per mostrare HP numerici in party HUD

---

## Fase 3: Perk Selection (Between Waves)

### Cosa Vede il Player

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                              │
│                        ★ WAVE 3 COMPLETE! ★                                 │
│                                                                              │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐             │
│  │   [LEGENDARY]   │  │     [RARE]      │  │    [COMMON]     │             │
│  │                 │  │                 │  │                 │             │
│  │   BERSERKER     │  │   IRON SKIN     │  │   SWIFT FEET    │             │
│  │   ───────────   │  │   ──────────    │  │   ──────────    │             │
│  │   Offense       │  │   Defense       │  │   Utility       │             │
│  │                 │  │                 │  │                 │             │
│  │   +50% damage   │  │   +20 armor     │  │   +10% speed    │             │
│  │   when below    │  │   permanent     │  │   permanent     │             │
│  │   30% health    │  │                 │  │                 │             │
│  │                 │  │   Stacks: 0/3   │  │   Stacks: 1/5   │             │
│  │                 │  │                 │  │                 │             │
│  │    [Select]     │  │    [Select]     │  │    [Select]     │             │
│  │       [1]       │  │       [2]       │  │       [3]       │             │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘             │
│                                                                              │
│                            [Skip Perk]                                       │
│                                                                              │
│  Keybinds: 1/2/3 = Select | ESC = Skip                                      │
└─────────────────────────────────────────────────────────────────────────────┘
```

### UX Analysis

| Elemento | Valutazione | Note |
|----------|-------------|------|
| Card layout | ⭐⭐⭐⭐⭐ | Tre opzioni chiare, non overwhelming |
| Tier visibility | ⭐⭐⭐⭐⭐ | Badge colorato in alto, impossibile da perdere |
| Description clarity | ⭐⭐⭐⭐ | Multi-line ma a volte troppo tecnico |
| Stack info | ⭐⭐⭐⭐⭐ | Chiaro quanti stack hai e max |
| Keyboard shortcuts | ⭐⭐⭐⭐⭐ | 1/2/3 per quick select, ottimo per flow |
| Skip option | ⭐⭐⭐⭐ | Presente ma non prominente (giusto) |

### Decision Time Analysis

```
Tempo medio decisione perk:
- Common vs Common: ~2 secondi
- Rare vs Common: ~3 secondi
- Legendary presente: ~5-8 secondi (legge descrizione)
- Synergy decision: ~10+ secondi (valuta build)
```

### Friction Points

1. **No perk history**: Non vedo quali perk ho già
2. **No synergy hints**: Non so quali perk sinergizzano
3. **Timer assente**: Nessuna pressione temporale (potrebbe essere voluto)

### Recommendations

- [ ] Aggiungere "My Perks" sidebar con perk attuali
- [ ] Highlight synergies (es. "Works well with: Lifesteal")
- [ ] Opzionale: Soft timer con auto-skip dopo 30s

---

## Fase 4: Death Experience

### Cosa Vede il Player

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                              │
│                              ☠ YOU DIED ☠                                   │
│                                                                              │
│                           Wave 5 / 10                                        │
│                         Points: 1,250                                        │
│                         Deaths: 1                                            │
│                                                                              │
│                    ─────────────────────────                                 │
│                                                                              │
│                       CHOOSE YOUR FATE                                       │
│                                                                              │
│           ┌─────────────────────────────────────────┐                       │
│           │         RESPAWN AND CONTINUE            │                       │
│           │         Cost: -100 points               │                       │
│           │         Continue the fight!             │                       │
│           │                [F11]                    │                       │
│           └─────────────────────────────────────────┘                       │
│                                                                              │
│           ┌─────────────────────────────────────────┐                       │
│           │          GIVE UP AND COLLECT            │                       │
│           │          Reward: 1,250 points           │                       │
│           │          Collect your earnings          │                       │
│           │                [F12]                    │                       │
│           └─────────────────────────────────────────┘                       │
│                                                                              │
│                   ESC is blocked - Must choose                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### UX Analysis

| Elemento | Valutazione | Note |
|----------|-------------|------|
| Emotional impact | ⭐⭐⭐⭐⭐ | Skull icon, red theme - chiaro che è serio |
| Choice clarity | ⭐⭐⭐⭐⭐ | Due opzioni chiare con conseguenze |
| Cost visibility | ⭐⭐⭐⭐⭐ | "-100 points" in rosso, reward in gold |
| Forced decision | ⭐⭐⭐⭐ | ESC bloccato - controverso ma previene abandon |
| Quick keys | ⭐⭐⭐⭐⭐ | F11/F12 per decisione rapida |

### Psychological Design

```
Death Screen Flow:
                    ┌──────────────────┐
                    │     SHOCK        │ (0-1s)
                    │   "I died!"      │
                    └────────┬─────────┘
                             │
                    ┌────────▼─────────┐
                    │   ASSESSMENT     │ (1-3s)
                    │  "How far was I?"│
                    │  "Worth respawn?"│
                    └────────┬─────────┘
                             │
              ┌──────────────┴──────────────┐
              │                             │
     ┌────────▼────────┐          ┌────────▼────────┐
     │    CONTINUE     │          │    GIVE UP      │
     │ "I can do this" │          │ "Better safe"   │
     │ Risk-seeking    │          │ Risk-averse     │
     └─────────────────┘          └─────────────────┘
```

### Friction Points

1. **No "close call" indicator**: Non so quanto HP aveva il boss
2. **No party vote**: In multiplayer, decisione è individuale

### Recommendations

- [ ] Mostrare "Boss HP: 15%" se stavi combattendo boss
- [ ] In party: Mostrare quanti compagni sono ancora vivi

---

## Fase 5: Quest Completion

### Cosa Vede il Player

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                              │
│                           ★ QUEST COMPLETE! ★                               │
│                                                                              │
│                        Zombie Hunt - Wave 10/10                              │
│                            Time: 12:34                                       │
│                                                                              │
│  ═══════════════════════════════════════════════════════════════════════    │
│                                                                              │
│                              REWARDS                                         │
│                                                                              │
│         ⭐ 2,450 Tokens                                                      │
│            (Base: 800 | Style: x2.0 | Mutator: x1.5)                        │
│                                                                              │
│         💎 15 Blood Gems                                                     │
│         🔱 3 Prestige                                                        │
│                                                                              │
│  ───────────────────────────────────────────────────────────────────────    │
│                                                                              │
│    ✓ No Hit Bonus!          │    Kills: 127                                 │
│    ✓ Speed Bonus!           │    Max Combo: 45                              │
│    ✓ 3 Mutators Active      │    Style Rank: SS                             │
│                              │    Damage: 12,450                             │
│                              │    Damage Taken: 0                            │
│                                                                              │
│  ───────────────────────────────────────────────────────────────────────    │
│                                                                              │
│    🏆 ACHIEVEMENTS UNLOCKED:                                                 │
│       ★ Untouchable - Complete 10 waves without damage                      │
│       ★ Style Master - Reach SS rank                                        │
│                                                                              │
│                          [Continue]                                          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### UX Analysis

| Elemento | Valutazione | Note |
|----------|-------------|------|
| Celebration feel | ⭐⭐⭐⭐⭐ | Star icon, gold theme, pulsing |
| Reward breakdown | ⭐⭐⭐⭐⭐ | Mostra come sono calcolati i token |
| Stats summary | ⭐⭐⭐⭐⭐ | Due colonne, facile da scansionare |
| Achievement display | ⭐⭐⭐⭐⭐ | Prominente, con descrizione |
| Bonuses highlight | ⭐⭐⭐⭐⭐ | Checkmarks verdi, satisfaction |

### Emotional Design

```
Completion Screen Timeline:

0ms     │ Background fade in
        │ ↓ Anticipation builds
300ms   │ "QUEST COMPLETE!" appears
        │ ↓ Initial satisfaction
600ms   │ Rewards start counting up
        │ ↓ Excitement grows as numbers climb
1100ms  │ Counter finishes
        │ ↓ Achievement unlocks appear
1500ms  │ Stats revealed
        │ ↓ Reflection on performance
2000ms+ │ "Continue" button enabled
        │ ↓ Ready to proceed or screenshot
```

### Friction Points

1. **No screenshot button**: Player deve usare F2 manualmente
2. **No share option**: Nessun modo per condividere risultati
3. **Single continue button**: Potrebbe offrire "Play Again" diretto

### Recommendations

- [ ] Aggiungere "Screenshot" button che salva con nome quest
- [ ] "Play Again" button per rifare stessa quest
- [ ] "Share" button che copia stats in clipboard

---

## Summary: UX Scorecard

### Strengths

| Area | Score | Highlight |
|------|-------|-----------|
| Visual Consistency | 9/10 | Dark theme, color coding coerente |
| Feedback Loops | 9/10 | Ogni azione ha feedback immediato |
| Progression Clarity | 9/10 | Sempre chiaro dove sei nella quest |
| Reward Satisfaction | 10/10 | Breakdown dettagliato, multipliers visibili |
| Accessibility | 7/10 | Keyboard shortcuts presenti, ma no colorblind mode |

### Areas for Improvement

| Area | Current | Target | Priority |
|------|---------|--------|----------|
| Perk Synergies | No hints | Show synergies | Medium |
| Party Communication | Basic | Voice/ping system | Low |
| Onboarding | None | Tutorial quest | High |
| Colorblind Support | None | Symbols + colors | Medium |

### Overall UX Score: 8.5/10

**Verdict:** L'esperienza utente è polished e professionale. Il feedback loop è coinvolgente, le schermate sono chiare, e la progressione è soddisfacente. Le aree di miglioramento sono principalmente quality-of-life features piuttosto che problemi fondamentali.
