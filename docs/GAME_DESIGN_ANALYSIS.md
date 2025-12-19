# Endurance Quest System - Game Design Analysis

## Executive Summary

**Stato: SISTEMA INTEGRATO E COESO**

L'Endurance Quest System opera come un'esperienza unificata. Tutti i sottosistemi (Combo, Perk, Mutator, Reward, Boss, Wave) sono orchestrati centralmente da `EnduranceEventHandler` e condividono lo stesso lifecycle di quest.

---

## 1. Flusso di Gioco Unificato

```
┌─────────────────────────────────────────────────────────────────┐
│                    PLAYER EXPERIENCE FLOW                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  [START QUEST] ──► Create Sessions ──► First Wave               │
│       │               │                    │                     │
│       │          ┌────┴────┐              │                     │
│       │          │ Combo   │              │                     │
│       │          │ Perk    │              ▼                     │
│       │          │ Mutator │         COMBAT LOOP                │
│       │          │ Combat  │         ┌─────────┐                │
│       │          └─────────┘         │ Attack  │◄───┐           │
│       │                              │ Kill    │    │           │
│       │                              │ Dodge   │    │           │
│       │                              └────┬────┘    │           │
│       │                                   │         │           │
│       │                              Style Rank ────┘           │
│       │                              Combo Counter              │
│       │                                   │                     │
│       │                              ▼                          │
│       │                         [WAVE COMPLETE]                 │
│       │                              │                          │
│       │                    ┌─────────┼─────────┐                │
│       │                    │         │         │                │
│       │               Perk      Mutator    Reward               │
│       │              Choice     Roll       Calc                 │
│       │                    │         │         │                │
│       │                    └─────────┼─────────┘                │
│       │                              │                          │
│       │                    [CHECKPOINT SCREEN]                  │
│       │                         │         │                     │
│       │                    Continue    Exit                     │
│       │                         │         │                     │
│       │                    Next Wave   [REWARDS]                │
│       │                         │         │                     │
│       └─────────────────────────┘         │                     │
│                                           │                     │
│                                    [QUEST END]                  │
│                                    All Systems                  │
│                                    Cleanup                      │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Integrazione dei Sottosistemi

### 2.1 ComboSystem ↔ RewardSystem
**Integrazione: FORTE**

```java
// In RewardSystem.calculateQuestRewards():
float styleMultiplier = switch (comboSession.getHighestRank()) {
    case SSS -> 3.0f;
    case SS -> 2.5f;
    case S -> 2.0f;
    case A -> 1.5f;
    case B -> 1.25f;
    case C -> 1.1f;
    default -> 1.0f;
};
```

**Game Design Impact:**
- I giocatori sono incentivati a mantenere combo alte
- Lo stile SSS triplica i token guadagnati
- Il sistema "skill-based rewards" premia il gameplay attivo

### 2.2 MutatorSystem ↔ RewardSystem
**Integrazione: FORTE**

```java
// Mutator multipliers stack:
float mutatorMultiplier = mutatorSession.getRewardMultiplier();
// Blessings: 0.8x (easier, less reward)
// Curses: 1.5x (harder, more reward)
```

**Game Design Impact:**
- Risk/Reward bilanciato
- I giocatori possono scegliere sfide più difficili per reward maggiori
- Replay value aumentato attraverso combinazioni di mutator

### 2.3 PerkSystem ↔ Gameplay
**Integrazione: FORTE**

```java
// Perks apply via attribute modifiers:
PerkSystem.INSTANCE.startSession(playerId, questId);
// Each perk selection applies stats immediately
// Cleanup removes all modifiers at quest end
```

**Game Design Impact:**
- Build diversity: ogni run può essere diversa
- Power fantasy progressiva durante la quest
- Synergies tra perk creano momenti "aha!"

### 2.4 WaveManager ↔ BossWaveSystem
**Integrazione: FORTE**

```java
// Every 5 waves = boss wave
boolean isBossWave = BossWaveSystem.INSTANCE.isBossWave(waveNumber);
if (isBossWave) {
    BossWaveSystem.INSTANCE.startBossWave(arena, mobId, waveNumber);
}
```

**Game Design Impact:**
- Pacing naturale con picchi di difficoltà
- Boss ogni 5 wave crea milestone significativi
- I boss droppano Blood Gems (currency rara)

---

## 3. Lifecycle Events Sincronizzati

| Evento | ComboSystem | PerkSystem | MutatorSystem | RewardSystem | CombatTracker |
|--------|-------------|------------|---------------|--------------|---------------|
| Quest Start | startSession | startSession | createSession | - | startTracking |
| Wave Start | startNewWave | - | - | - | - |
| Mob Kill | registerAction | - | onMobDeath | - | recordKill |
| Wave Complete | - | generateChoices | rollNewMutator | - | startNewWave |
| Quest End | endSession | endSession | endSession | calculate | stopTracking |

**Osservazione:** Tutti i sistemi hanno entry/exit points sincronizzati. Nessun sistema "orphano".

---

## 4. Multiplayer Scaling

### Party Quest Flow

```
Party Leader: "Start Quest"
       │
       ▼
QuestStartSequence
       │
  ┌────┴────┐
  │ PHASE 1 │ Validate all members
  ├─────────┤
  │ PHASE 2 │ 5s countdown
  ├─────────┤
  │ PHASE 3 │ Create arena, teleport all
  ├─────────┤
  │ PHASE 4 │ Wait for arrivals (30s timeout)
  ├─────────┤
  │ PHASE 5 │ 3s sync countdown
  ├─────────┤
  │ PHASE 6 │ Start quest for ALL members
  └─────────┘
       │
       ▼
Each player gets INDEPENDENT sessions:
  - Own ComboSession (own style rank)
  - Own PerkSession (own perk choices)
  - Own CombatStats (own performance)

BUT they SHARE:
  - Same arena
  - Same mobs
  - Same wave state
  - Kills count for everyone
```

### Scaling Formulas

| Stat | Formula | Example (3 players, RAID_BOSS) |
|------|---------|--------------------------------|
| Mob Count | `base * sqrt(players) * diffMult` | `10 * 1.73 * 1.5 = 26` |
| Mob HP | `base * (1 + (players-1) * 0.3)` | `100 * 1.6 = 160` |
| Mob Damage | `base * (1 + (players-1) * 0.1)` | `10 * 1.2 = 12` |
| Boss HP | `base * (1 + (players-1) * 0.3) * diff` | `1000 * 1.6 * 1.5 = 2400` |

---

## 5. Feedback Loop Analysis

### Core Loop: Action → Feedback → Reward

```
ACTION                    FEEDBACK                      REWARD
───────────────────────────────────────────────────────────────
Hit mob                → Damage number HUD            → +5 style
                       → Combo counter +1
                       → Sound effect

Kill mob               → Kill notification            → +60 style
                       → Multi-kill detection         → Bonus if multi
                       → Style rank animation         → Rank up possible

Take damage            → Screen flash                 → -style penalty
                       → Combo halved                 → Risk of rank down

Wave complete          → Banner animation             → Perk choice
                       → Stats summary                → Progress toward reward
                       → Checkpoint option

Quest complete         → Full-screen results          → Tokens
                       → Style rank showcase          → Prestige
                       → Loot drops                   → Blood Gems
                       → Achievement check            → Badges
```

### Motivational Hooks

1. **Short-term:** Combo counter, style rank (every few seconds)
2. **Medium-term:** Wave completion, perk selection (every 1-3 minutes)
3. **Long-term:** Tokens, shop unlocks, achievements (end of session)

---

## 6. Punti di Forza del Design

### ✅ Emergent Gameplay
La combinazione di Perk + Mutator crea esperienze uniche ogni run:
- Run con "Lifesteal + Curse of Frailty" = glass cannon playstyle
- Run con "Armored + Thorns" = tank build
- Ogni combinazione richiede strategie diverse

### ✅ Skill Expression
Il ComboSystem permette ai giocatori esperti di:
- Mantenere stile SSS per 3x rewards
- Usare variety bonus (azioni diverse)
- Concatenare multi-kill per bonus massicci

### ✅ Progression Curve
```
Wave 1-5:   Learning phase, easy mobs
Wave 5:     First boss, difficulty spike
Wave 6-10:  New mutators added
Wave 10:    Second boss, harder
Wave 11+:   Roguelike mastery required
```

### ✅ Risk/Reward Balance
- Exit at checkpoint = safe rewards
- Continue = more waves = more rewards but risk losing all
- Mutator curses = harder but better loot

---

## 7. Aree di Miglioramento Identificate

### 🔶 Party Member Visibility
**Attuale:** I membri del party non vedono lo stile/perk degli altri in tempo reale.
**Suggerimento:** Aggiungere Party HUD con sync dello stato.

### 🔶 Shared vs Individual Sessions
**Attuale:** Ogni player ha sessioni indipendenti.
**Considerazione:** Per alcuni quest type (RAID_BOSS), potrebbe essere interessante avere mutatori condivisi.

### 🔶 Boss Fight Coordination
**Attuale:** Boss HP è per-player.
**Suggerimento:** Shared boss entity con damage contribution tracking.

### 🔶 Real-time Achievements
**Attuale:** Badge notificati solo a fine quest.
**Suggerimento:** Stream notifications durante gameplay per momenti "WOW".

---

## 8. Conclusione

**L'Endurance Quest System è un'esperienza di gioco coesa e ben integrata.**

| Aspetto | Valutazione |
|---------|-------------|
| Orchestrazione centrale | ⭐⭐⭐⭐⭐ |
| Sincronizzazione lifecycle | ⭐⭐⭐⭐⭐ |
| Feedback loop | ⭐⭐⭐⭐⭐ |
| Multiplayer scaling | ⭐⭐⭐⭐ |
| Progression systems | ⭐⭐⭐⭐⭐ |
| Risk/reward balance | ⭐⭐⭐⭐⭐ |

**Voto Complessivo: 4.8/5**

Il sistema opera come un'esperienza unificata dove ogni azione del giocatore ha impatto su tutti i sottosistemi, creando un loop di gameplay coinvolgente e rewarding.
