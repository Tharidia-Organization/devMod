# Endurance Quest System - Master Plan

## Panoramica del Sistema

Il sistema **Endurance Quest** è una meccanica di testing gamificato che combina elementi roguelike con analisi automatizzata del bilanciamento. Ogni mob nel gioco (vanilla + mods) avrà la propria quest dedicata, permettendo ai tester di raccogliere dati precisi su ogni aspetto del combattimento.

---

## Architettura Generale

```
┌─────────────────────────────────────────────────────────────────────┐
│                     ENDURANCE QUEST SYSTEM                          │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────────────────┐ │
│  │   QUEST     │    │   ARENA     │    │   ANALYTICS ENGINE      │ │
│  │   MANAGER   │───▶│   SYSTEM    │───▶│   (Data Collection)     │ │
│  └─────────────┘    └─────────────┘    └─────────────────────────┘ │
│        │                  │                       │                 │
│        ▼                  ▼                       ▼                 │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────────────────┐ │
│  │    WAVE     │    │   BARRIER   │    │   LEADERBOARD &         │ │
│  │   SPAWNER   │    │   MANAGER   │    │   GAMIFICATION          │ │
│  └─────────────┘    └─────────────┘    └─────────────────────────┘ │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Moduli del Sistema

### 1. Quest Manager (`EnduranceQuestManager.java`)
- Registro di tutti i mob disponibili (vanilla + mods)
- Generazione automatica quest per ogni EntityType
- Gestione stato quest (attiva, completata, fallita)
- Persistenza progressi per tester

### 2. Arena System (`ArenaManager.java`)
- Creazione arena 4 chunks (64x64 blocchi)
- Barriere invisibili ma invalicabili
- Terreno piatto temporaneo (opzionale)
- Restore automatico del terreno originale

### 3. Wave Spawner (`WaveSpawner.java`)
- Sistema wave progressivo
- Scaling difficoltà per wave
- Spawn intelligente (evita overlap)
- Timer tra wave

### 4. Analytics Engine (`EnduranceAnalytics.java`)
- Tracciamento dati combattimento in tempo reale
- Storage JSON/Database
- Export per analisi esterna
- Dashboard in-game

### 5. Gamification Layer (`EnduranceRewards.java`)
- Sistema punti/XP per tester
- Badge e achievements
- Leaderboard globale/locale
- Sfide giornaliere/settimanali

---

## Struttura Wave

```
WAVE STRUCTURE (Per Quest)
==========================

Wave 1:  1x Mob (Base Stats)
Wave 2:  2x Mob (Base Stats)
Wave 3:  3x Mob (+10% HP/DMG)
Wave 4:  4x Mob (+10% HP/DMG)
Wave 5:  5x Mob (+20% HP/DMG) [CHECKPOINT - Può uscire]
Wave 6:  6x Mob (+20% HP/DMG)
Wave 7:  7x Mob (+30% HP/DMG)
Wave 8:  8x Mob (+30% HP/DMG)
Wave 9:  9x Mob (+40% HP/DMG)
Wave 10: 10x Mob (+50% HP/DMG) [BOSS WAVE - 1 Elite variant]

ENDLESS MODE (Post Wave 10):
- Wave N: N mob con scaling esponenziale
- Ogni 5 wave: checkpoint per uscire
- Dati raccolti fino al fallimento
```

---

## Dati Raccolti (Analytics)

### Per Sessione Quest
```json
{
  "sessionId": "uuid",
  "testerId": "player_uuid",
  "testerName": "PlayerName",
  "questType": "minecraft:zombie",
  "startTime": 1234567890,
  "endTime": 1234567999,
  "result": "COMPLETED|FAILED|ABANDONED",
  "waveReached": 10,
  "totalKills": 55,
  "totalDeaths": 2,
  "totalDamageTaken": 450.5,
  "totalDamageDealt": 2340.0,
  "totalHealingUsed": 120.0,
  "timePerWave": [30, 45, 60, ...],
  "weaponUsed": {
    "primary": "minecraft:diamond_sword",
    "secondary": "minecraft:shield"
  },
  "armorSet": {
    "helmet": "minecraft:diamond_helmet",
    "chestplate": "minecraft:diamond_chestplate",
    "leggings": "minecraft:diamond_leggings",
    "boots": "minecraft:diamond_boots"
  },
  "enchantments": [...],
  "potionEffects": [...],
  "killDetails": [
    {
      "wave": 1,
      "mobId": "minecraft:zombie",
      "timeToKill": 5.2,
      "hitsLanded": 4,
      "hitsTaken": 1,
      "damageDealt": 40.0,
      "damageTaken": 6.0,
      "criticalHits": 1,
      "bodyPartsHit": {"head": 2, "body": 2}
    }
  ],
  "deathDetails": [
    {
      "wave": 7,
      "killedBy": "minecraft:zombie",
      "damageType": "mob_attack",
      "playerHealthAtDeath": 0,
      "timeAlive": 180.5
    }
  ]
}
```

### Metriche Aggregate (Per Mob Type)
```json
{
  "mobType": "minecraft:zombie",
  "totalTests": 150,
  "averageWaveReached": 7.5,
  "completionRate": 0.45,
  "averageTimeToKill": 4.8,
  "averageDamagePerKill": 35.0,
  "playerDeathRate": 0.12,
  "difficultyScore": 3.5,
  "weaponEffectiveness": {
    "diamond_sword": 0.85,
    "iron_sword": 0.65,
    "wooden_sword": 0.30
  },
  "armorEffectiveness": {
    "diamond_set": 0.90,
    "iron_set": 0.70,
    "leather_set": 0.40
  }
}
```

---

## UI/UX Design

### 1. Quest Selection Screen
```
┌────────────────────────────────────────────────────────────────┐
│  ENDURANCE QUEST HUB                              [X] Close    │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  [Search: ____________]  [Filter: All ▼]  [Sort: Name ▼]      │
│                                                                │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │ HOSTILE MOBS                                    [▼]      │ │
│  │  ├─ Zombie         ★★☆☆☆  Best: Wave 10  Tests: 45      │ │
│  │  ├─ Skeleton       ★★★☆☆  Best: Wave 8   Tests: 32      │ │
│  │  ├─ Creeper        ★★★★☆  Best: Wave 6   Tests: 28      │ │
│  │  ├─ Spider         ★★☆☆☆  Best: Wave 10  Tests: 51      │ │
│  │  └─ ...                                                  │ │
│  │                                                          │ │
│  │ MODDED: Iron's Spellbooks                       [▼]      │ │
│  │  ├─ Fire Mage      ★★★★★  Best: Wave 4   Tests: 12      │ │
│  │  ├─ Ice Elemental  ★★★★☆  Best: Wave 5   Tests: 8       │ │
│  │  └─ ...                                                  │ │
│  └──────────────────────────────────────────────────────────┘ │
│                                                                │
│  YOUR STATS                          LEADERBOARD              │
│  ───────────                         ───────────              │
│  Total Tests: 234                    1. TestMaster - 15,420   │
│  Completion Rate: 67%                2. BalanceKing - 12,890  │
│  Total Points: 8,450                 3. You - 8,450           │
│  Badges: 🏆🎯⚔️🛡️                    4. QATester1 - 7,200     │
│                                                                │
│  [START SELECTED QUEST]        [VIEW ANALYTICS]               │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

### 2. In-Quest HUD
```
┌─────────────────────────────────────────────────────────────────┐
│ WAVE 5/10          ZOMBIE ENDURANCE          TIME: 03:45       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Enemies: ████████░░ 8/10        Your HP: ██████████░░ 85%     │
│                                                                 │
│  Stats This Wave:                                               │
│  ├─ Kills: 2                                                    │
│  ├─ Damage Dealt: 80                                           │
│  └─ Damage Taken: 24                                           │
│                                                                 │
│  [P] Pause  [ESC] Exit (forfeit)  [TAB] Detailed Stats         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 3. Wave Complete Screen
```
┌─────────────────────────────────────────────────────────────────┐
│                    ★ WAVE 5 COMPLETE ★                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Time: 01:23          Kills: 5          Damage Dealt: 200      │
│                                                                 │
│  CHECKPOINT REACHED!                                            │
│  You may exit now and keep your progress.                      │
│                                                                 │
│  Rewards Earned:                                                │
│  ├─ +50 Test Points                                            │
│  ├─ +1 Wave Completion (Zombie)                                │
│  └─ Badge Progress: 5/10 Zombie Waves                          │
│                                                                 │
│  Next Wave: 6 enemies with +20% HP/DMG                         │
│                                                                 │
│  [CONTINUE TO WAVE 6]     [EXIT & SAVE]     [VIEW STATS]       │
│                                                                 │
│              Auto-continue in: 15 seconds                       │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 4. Quest Complete/Failed Screen
```
┌─────────────────────────────────────────────────────────────────┐
│              🏆 QUEST COMPLETE: ZOMBIE MASTER 🏆                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Final Wave: 10/10 (COMPLETED)                                 │
│  Total Time: 12:45                                              │
│  Total Kills: 55                                                │
│                                                                 │
│  PERFORMANCE BREAKDOWN                                          │
│  ─────────────────────                                          │
│  Damage Efficiency: ★★★★☆ (85%)                                │
│  Survival: ★★★★★ (100% - No Deaths)                            │
│  Speed: ★★★☆☆ (Average)                                        │
│  Overall: ★★★★☆ (A Rank)                                       │
│                                                                 │
│  REWARDS                                                        │
│  ───────                                                        │
│  +500 Test Points                                               │
│  +1 Quest Completion                                            │
│  🏆 NEW BADGE: Zombie Slayer                                   │
│                                                                 │
│  WEAPON ANALYSIS                                                │
│  ───────────────                                                │
│  Diamond Sword performed WELL against this mob type.           │
│  Average TTK: 4.2s (Expected: 5.0s) - 16% faster               │
│                                                                 │
│  [RETRY QUEST]     [DIFFERENT QUEST]     [VIEW FULL ANALYTICS] │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Gamification Elements

### 1. Test Points System
```
ACTION                              POINTS
────────────────────────────────────────────
Complete Wave 1-4                   +10 each
Complete Wave 5 (Checkpoint)        +50
Complete Wave 6-9                   +20 each
Complete Wave 10 (Final)            +100
Complete Endless Wave               +30 each
No-Hit Wave Bonus                   +25
Speed Bonus (<30s per wave)         +15
First Test of a Mob                 +50
Full Quest Completion               +200
Death Penalty                       -10
Abandonment Penalty                 -25
```

### 2. Badges/Achievements
```
COMBAT BADGES
─────────────
🗡️ First Blood - Complete your first quest
⚔️ Warrior - Complete 10 quests
🏆 Champion - Complete 50 quests
👑 Legend - Complete 100 quests

SPECIALIST BADGES
─────────────────
💀 Undead Hunter - Complete all undead mob quests
🕷️ Arthropod Slayer - Complete all spider/arthropod quests
🔥 Nether Survivor - Complete all Nether mob quests
🐉 Ender Master - Complete all End mob quests

SKILL BADGES
────────────
🎯 Sharpshooter - 100 headshot kills
🛡️ Tank - Take 10,000 total damage and survive
⚡ Speedster - Complete a quest in under 5 minutes
💪 No-Hit Run - Complete Wave 10 without taking damage

DEDICATION BADGES
─────────────────
📊 Data Collector - Complete 500 total tests
🔬 Scientist - Test 50 different mob types
🌟 Perfectionist - Get A-rank on 25 quests
```

### 3. Daily/Weekly Challenges
```
DAILY CHALLENGES (Reset every 24h)
───────────────────────────────────
□ Complete 3 different mob quests        (+100 points)
□ Reach Wave 10 on any quest             (+75 points)
□ Deal 1000 total damage                 (+50 points)
□ Test a mob you've never tested         (+100 points)

WEEKLY CHALLENGES (Reset every 7 days)
───────────────────────────────────────
□ Complete 20 quests                     (+500 points)
□ Test 10 different mods' mobs           (+300 points)
□ Achieve 5 No-Hit waves                 (+250 points)
□ Reach Endless Wave 15                  (+400 points)
```

### 4. Leaderboards
```
GLOBAL LEADERBOARDS
───────────────────
• Total Points (All-Time)
• Weekly Points
• Quest Completions
• Highest Wave (Endless)
• Most Mob Types Tested
• Best Completion Rate

PER-MOB LEADERBOARDS
────────────────────
• Fastest Completion Time
• Highest Wave Reached
• Most Efficient (Damage Dealt/Taken ratio)
• Most Tests Completed
```

---

## Implementazione - Fasi

### FASE 1: Core System (Foundation)
**File da creare:**
1. `EnduranceQuestManager.java` - Gestione quest principale
2. `EnduranceQuest.java` - Classe singola quest
3. `EnduranceQuestState.java` - Enum stati quest
4. `EnduranceQuestRegistry.java` - Registro automatico mob

**Funzionalità:**
- Scansione automatica tutti gli EntityType
- Creazione quest per ogni mob
- Sistema base start/stop quest

### FASE 2: Arena System
**File da creare:**
1. `ArenaManager.java` - Gestione arena
2. `ArenaBarrier.java` - Sistema barriere
3. `ArenaConfig.java` - Configurazione dimensioni

**Funzionalità:**
- Creazione arena 64x64 (4 chunks)
- Barriere invisibili
- Teleport player al centro
- Cleanup arena a fine quest

### FASE 3: Wave System
**File da creare:**
1. `WaveManager.java` - Gestione wave
2. `WaveConfig.java` - Configurazione wave
3. `MobSpawner.java` - Spawn intelligente
4. `WaveScaling.java` - Calcolo scaling difficoltà

**Funzionalità:**
- Spawn wave progressivo
- Scaling HP/DMG per wave
- Timer tra wave
- Checkpoint system

### FASE 4: Combat Tracking
**File da creare:**
1. `CombatTracker.java` - Tracciamento combattimento
2. `DamageEvent.java` - Record singolo danno
3. `KillEvent.java` - Record singola uccisione
4. `SessionData.java` - Dati sessione completa

**Funzionalità:**
- Hook su tutti gli eventi danno
- Tracciamento hit/miss
- Tracciamento body parts
- Calcolo statistiche real-time

### FASE 5: Analytics Engine
**File da creare:**
1. `EnduranceAnalytics.java` - Engine principale
2. `AnalyticsStorage.java` - Persistenza dati
3. `AnalyticsAggregator.java` - Aggregazione metriche
4. `AnalyticsExporter.java` - Export CSV/JSON

**Funzionalità:**
- Storage sessioni su file JSON
- Aggregazione dati per mob type
- Calcolo metriche bilanciamento
- Export per analisi esterna

### FASE 6: UI System
**File da creare:**
1. `EnduranceQuestScreen.java` - Schermata selezione
2. `EnduranceHUD.java` - HUD in-game
3. `WaveCompleteScreen.java` - Schermata fine wave
4. `QuestResultScreen.java` - Schermata risultati
5. `AnalyticsDashboard.java` - Dashboard analytics

**Funzionalità:**
- UI completa selezione quest
- HUD real-time durante quest
- Schermate transizione wave
- Dashboard visualizzazione dati

### FASE 7: Gamification
**File da creare:**
1. `EnduranceRewards.java` - Sistema reward
2. `BadgeManager.java` - Gestione badge
3. `LeaderboardManager.java` - Leaderboard
4. `ChallengeManager.java` - Sfide daily/weekly
5. `TesterProfile.java` - Profilo tester

**Funzionalità:**
- Sistema punti completo
- Badge e achievements
- Leaderboard locali
- Sfide temporizzate

### FASE 8: Polish & Integration
**Attività:**
- Keybind dedicato (default: K)
- Integrazione con telemetria esistente
- Comandi admin (/endurance)
- Config file per personalizzazione
- Documentazione in-game

---

## Struttura Directory

```
src/main/java/com/frenkvs/devmod/
├── endurance/
│   ├── EnduranceQuestManager.java
│   ├── EnduranceQuest.java
│   ├── EnduranceQuestState.java
│   ├── EnduranceQuestRegistry.java
│   │
│   ├── arena/
│   │   ├── ArenaManager.java
│   │   ├── ArenaBarrier.java
│   │   ├── ArenaConfig.java
│   │   └── ArenaCleanup.java
│   │
│   ├── wave/
│   │   ├── WaveManager.java
│   │   ├── WaveConfig.java
│   │   ├── WaveScaling.java
│   │   └── MobSpawner.java
│   │
│   ├── combat/
│   │   ├── CombatTracker.java
│   │   ├── DamageEvent.java
│   │   ├── KillEvent.java
│   │   └── SessionData.java
│   │
│   ├── analytics/
│   │   ├── EnduranceAnalytics.java
│   │   ├── AnalyticsStorage.java
│   │   ├── AnalyticsAggregator.java
│   │   ├── AnalyticsExporter.java
│   │   └── BalanceReport.java
│   │
│   ├── gamification/
│   │   ├── EnduranceRewards.java
│   │   ├── BadgeManager.java
│   │   ├── Badge.java
│   │   ├── LeaderboardManager.java
│   │   ├── ChallengeManager.java
│   │   ├── Challenge.java
│   │   └── TesterProfile.java
│   │
│   └── ui/
│       ├── EnduranceQuestScreen.java
│       ├── EnduranceHUD.java
│       ├── WaveCompleteScreen.java
│       ├── QuestResultScreen.java
│       ├── AnalyticsDashboard.java
│       └── LeaderboardScreen.java
│
└── resources/
    └── data/devmod/
        └── endurance/
            ├── default_config.json
            └── default_challenges.json
```

---

## Checklist Implementazione

### Fase 1: Core System
- [ ] Creare package `endurance`
- [ ] Implementare `EnduranceQuestRegistry` (scan EntityTypes)
- [ ] Implementare `EnduranceQuest` (classe base)
- [ ] Implementare `EnduranceQuestState` (enum)
- [ ] Implementare `EnduranceQuestManager` (singleton)
- [ ] Test: Verificare registrazione tutti i mob

### Fase 2: Arena System
- [ ] Implementare `ArenaConfig` (dimensioni, opzioni)
- [ ] Implementare `ArenaManager` (creazione/distruzione)
- [ ] Implementare `ArenaBarrier` (barriere invisibili)
- [ ] Sistema teleport player
- [ ] Test: Creare e distruggere arena

### Fase 3: Wave System
- [ ] Implementare `WaveConfig` (parametri wave)
- [ ] Implementare `WaveScaling` (calcoli scaling)
- [ ] Implementare `MobSpawner` (spawn logic)
- [ ] Implementare `WaveManager` (orchestrazione)
- [ ] Sistema checkpoint
- [ ] Test: Completare 5 wave

### Fase 4: Combat Tracking
- [ ] Implementare `DamageEvent` (record)
- [ ] Implementare `KillEvent` (record)
- [ ] Implementare `SessionData` (aggregatore)
- [ ] Implementare `CombatTracker` (hook eventi)
- [ ] Integrazione con body part detection
- [ ] Test: Verificare dati raccolti

### Fase 5: Analytics Engine
- [ ] Implementare `AnalyticsStorage` (JSON persistence)
- [ ] Implementare `EnduranceAnalytics` (engine)
- [ ] Implementare `AnalyticsAggregator` (metriche)
- [ ] Implementare `AnalyticsExporter` (export)
- [ ] Test: Export e importare dati

### Fase 6: UI System
- [ ] Implementare `EnduranceQuestScreen` (selezione)
- [ ] Implementare `EnduranceHUD` (in-game)
- [ ] Implementare `WaveCompleteScreen`
- [ ] Implementare `QuestResultScreen`
- [ ] Implementare `AnalyticsDashboard`
- [ ] Test: Flusso UI completo

### Fase 7: Gamification
- [ ] Implementare `TesterProfile` (profilo)
- [ ] Implementare `EnduranceRewards` (punti)
- [ ] Implementare `Badge` + `BadgeManager`
- [ ] Implementare `Challenge` + `ChallengeManager`
- [ ] Implementare `LeaderboardManager`
- [ ] Test: Sistema reward completo

### Fase 8: Polish
- [ ] Aggiungere keybind (K)
- [ ] Creare comandi admin
- [ ] Config file
- [ ] Suoni e effetti visivi
- [ ] Documentazione
- [ ] Test finale end-to-end

---

## Note Tecniche Importanti

### Performance
- Spawn mob in batch (non tutti insieme)
- Limit rendering arena (solo player inside)
- Analytics scritti async su disco
- Lazy loading UI screens

### Compatibilità Mod
- Scan EntityType registry per tutti i mob
- Fallback per mob senza AI standard
- Skip mob non-ostili se configurato
- Support per mob con behaviour custom

### Persistenza
- Sessioni salvate in `config/devmod/endurance/sessions/`
- Profili tester in `config/devmod/endurance/profiles/`
- Analytics aggregate in `config/devmod/endurance/analytics/`
- Backup automatico ogni 10 sessioni

### Sicurezza
- Validazione server-side per tutti i dati
- Rate limiting spawn mob
- Timeout sessione (max 30 min)
- Anti-cheat basic (impossibile uscire senza API)

---

## Timeline Stimata

| Fase | Descrizione | Complessità | Priorità |
|------|-------------|-------------|----------|
| 1 | Core System | Media | CRITICA |
| 2 | Arena System | Media | CRITICA |
| 3 | Wave System | Alta | CRITICA |
| 4 | Combat Tracking | Alta | CRITICA |
| 5 | Analytics Engine | Media | ALTA |
| 6 | UI System | Alta | ALTA |
| 7 | Gamification | Media | MEDIA |
| 8 | Polish | Bassa | BASSA |

---

## Prossimi Passi

1. **Approvazione piano** - Conferma struttura e funzionalità
2. **Fase 1** - Implementazione Core System
3. **Test iterativi** - Ogni fase testata prima di procedere
4. **Feedback loop** - Raccolta feedback tester durante sviluppo

---

*Documento creato: 2025-12-08*
*Versione: 1.0*
*Autore: DevMod Team*
