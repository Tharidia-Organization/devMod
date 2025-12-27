# Endurance Quest System - Game Design Improvements

> Last updated: 2025-12-26

> Created: 2025-12-25
> Status: **12/12 ALL TASKS COMPLETED** (P0 + P1 + P2 + P3 ALL Complete!)
> Priority: P0 = Critical, P1 = High, P2 = Medium, P3 = Low

---

## P0 - CRITICAL (Immediate Impact)

### [x] 1. Daily Challenges System COMPLETED

**Impact**: +40% Day-7 retention
**Effort**: Low

Created a daily challenge system with rotating objectives:

- `src/main/java/com/devmod/endurance/challenges/DailyChallenge.java` - Challenge definitions
- `src/main/java/com/devmod/endurance/challenges/DailyChallengeManager.java` - Rotation & tracking
- `src/main/java/com/devmod/endurance/challenges/ChallengeSyncPayload.java` - Network sync
- `src/main/java/com/devmod/client/endurance/ClientChallengeCache.java` - Client cache

**Challenges Implemented**:

- Kill challenges (10/25/50/100 mobs)
- Wave completion challenges (5/10/15 waves)
- Style rank challenges (reach B/A/S rank)
- Boss kill challenges (1/2/3 bosses)
- Critical hit challenges (10/25/50 crits)
- No-death challenges (wave 10/15 without dying)

**Features**:

- 4 difficulty tiers: Easy, Medium, Hard, Extreme
- Scaling rewards: 200-1500 tokens, 2-12 prestige
- Daily rotation with persistence
- Combo/style tracking integration

---

### [x] 2. Prestige Milestones COMPLETED

**Impact**: +25% Day-14 retention
**Effort**: Low

Created milestone system for permanent unlocks at prestige thresholds:

- `src/main/java/com/devmod/endurance/PrestigeMilestone.java` - 14 milestones

**Milestones Implemented**:

| Prestige | Reward |
| -------- | ------ |
| 5 | +5% token multiplier |
| 10 | +1 perk slot |
| 15 | Unlock "Double or Nothing" mutator |
| 25 | "Endurance Veteran" title |
| 35 | Starting bonus: Healing Surge |
| 50 | Unlock "Hell Pit" arena |
| 75 | Unlock "Phoenix Protocol" exclusive perk |
| 100 | +15% token multiplier, "The Centurion" title |
| 150 | +1 perk slot (second extra) |
| 200 | Unlock "Void Sanctum" arena |
| 300 | Unlock "Mayhem Mode" mutator |
| 500 | "Endurance Legend" title |
| 1000 | Unlock "Immortal Spirit" exclusive perk |

**Features**:

- Lifetime prestige tracking in PlayerWallet
- Automatic notification on milestone unlock
- Visual/audio feedback with fanfare

---

### [x] 3. Comeback Mechanic ("Rising Phoenix") COMPLETED

**Impact**: +20% engagement for struggling players
**Effort**: Low

Created Phoenix Rising system that triggers at critical health:

- `src/main/java/com/devmod/endurance/ComebackSystem.java`

**Trigger Conditions**:

- Health drops below 20%
- 60 second cooldown between triggers

**Buffs Applied** (10 seconds):

- Resistance II - Survive incoming damage
- Strength II - Deal more damage
- Speed I - Evade and reposition
- Regeneration II - Recover health

**Features**:

- Totem-like sound effect on trigger
- +500 bonus combo points
- Kill/damage tracking during Phoenix state
- Integration with EnduranceEventCombat

---

## P1 - HIGH PRIORITY

### [x] 4. Transformative Perks (10 new) COMPLETED

**Impact**: +30% engagement
**Effort**: Medium

Added 10 game-changing perks to `PerkSystem.java`:

| Perk | Effect | Tier |
| ---- | ------ | ---- |
| Echo Strike | Attacks create echo at 50% damage after 0.5s | Epic |
| Bullet Time | Below 30% HP: nearby enemies 50% slower | Rare |
| Unstoppable Force | Immune to knockback, charge through enemies | Rare |
| Chain Reaction | Killed enemies explode after 1s | Epic |
| Blood Pact | Damage/healing shared with party members | Legendary |
| Phantom Shift | Every 10s: 2s invulnerability (can't attack) | Epic |
| Soul Harvest | Each kill stores soul (max 10) for massive attack | Legendary |
| Revenge | +20% damage per hit taken for 5s (stacks 5x) | Rare |
| Executioner's Wrath | Enemies below 25% HP take 3x damage | Epic |
| Adrenaline Surge | Below 10% HP: 3s invincibility + heal over time | Legendary |

---

### [x] 5. Leaderboard System COMPLETED

**Impact**: +50% competitive players
**Effort**: Medium

Created comprehensive leaderboard system:

- `src/main/java/com/devmod/endurance/LeaderboardSystem.java`

**Categories**:

- Waves Completed (higher is better)
- Best Time (lower is better)
- Highest Style Score
- Most Kills (single run)
- Endless Streak
- Total Prestige
- Flawless Runs (no-hit completions)

**Features**:

- Per-arena and global boards
- Weekly boards with automatic reset
- Top 100 entries per board
- Player rank lookup
- Context display (entries around player)
- JSON persistence

---

### [x] 6. Dynamic Tension System COMPLETED

**Impact**: +25% excitement
**Effort**: Medium

Created dynamic boss spawning system that replaces predictable "every 5 waves":

- `src/main/java/com/devmod/endurance/TensionSystem.java`
- `src/main/java/com/devmod/endurance/TensionUpdatePayload.java`
- `src/main/java/com/devmod/client/network/ClientTensionCache.java`

**Tension Sources**:

| Event | Tension Gain |
| ----- | ------------ |
| Base wave complete | +0.12 |
| No-hit wave | +0.20 bonus |
| High combo (50+) | +0.08 bonus |
| Style rank S+ | +0.10 bonus |
| Kill streak (10+ rapid) | +0.05 bonus |

**Boss Trigger Rules**:

- Threshold randomized between 0.70 and 1.00 each cycle
- Boss spawns when tension >= threshold
- Safety valve: force boss after 8 waves without one
- Minimum 3 waves before first boss can spawn
- After boss defeat: tension resets, new threshold generated

**Features**:

- Per-quest tension state tracking
- Real-time tension HUD sync to client
- 5 visual tension levels (Calm → BOSS INCOMING)
- Dynamic color coding for UI (green → red)
- Integration with ComboSystem, WaveManager, BossWaveSystem

---

## P2 - MEDIUM PRIORITY

### [x] 7. Directive Chains (Narrative Arcs) COMPLETED

**Impact**: +20% variety
**Effort**: High

Created multi-wave narrative arc system:

- `src/main/java/com/devmod/endurance/DirectiveChain.java` - Chain definitions
- `src/main/java/com/devmod/endurance/DirectiveChainManager.java` - Chain management

**Chain Themes** (6 categories):

- HUNT: Track and eliminate targets (Nemesis Hunt, Bounty Collector)
- SIEGE: Defend against overwhelming force (Fortress Siege, Bunker Defense)
- GAUNTLET: Face elite challenges (Elite Gauntlet, Proving Grounds)
- SWARM: Survive escalating waves (Rising Tide, Horde Night)
- TACTICAL: Strategic objective sequences (Strategic Assault)
- NIGHTMARE: Extreme difficulty (Hell Walk - 5 steps!)

**Chain Features**:

- 3-5 linked waves with narrative progression
- Step-by-step story text (title, narrative)
- Conditional unlocks (no death, min combo, min rank, flawless)
- Escalating rewards per step (1.1x → 2.0x multipliers)
- Major completion bonus (1000-5000 tokens, 10-50 prestige)
- Global reward multiplier on chain completion (1.2x-1.5x)
- Chain progress tracking per quest
- Automatic directive application during active chains

**8 Predefined Chains**:

| Chain | Theme | Steps | Bonus |
| ----- | ----- | ----- | ----- |
| Nemesis Hunt | Hunt | 3 | 2000 tokens |
| Bounty Collector | Hunt | 4 | 3000 tokens |
| Fortress Siege | Siege | 4 | 2500 tokens |
| Bunker Defense | Siege | 3 | 1800 tokens |
| Elite Gauntlet | Gauntlet | 3 | 2200 tokens |
| Proving Grounds | Gauntlet | 4 | 3500 tokens |
| Rising Tide | Swarm | 4 | 2000 tokens |
| Horde Night | Swarm | 3 | 1800 tokens |
| Strategic Assault | Tactical | 4 | 2200 tokens |
| Hell Walk | Nightmare | 5 | 5000 tokens |

---

### [x] 8. Synergy Preview UI COMPLETED

**Impact**: +15% depth perception
**Effort**: Medium

Created comprehensive perk synergy preview system:

- `src/main/java/com/devmod/endurance/PerkSynergySystem.java` - Synergy definitions & analysis

**Synergy Types**:

| Type | Description |
| ---- | ----------- |
| COMBO | Direct perk-to-perk enhancement (e.g., Critical Eye + Executioner) |
| THRESHOLD | Category count bonuses (e.g., 5+ Offense perks) |
| ARCHETYPE | Build archetype synergies (e.g., Path of the Berserker) |
| SPECIAL | Legendary combo unlocks (e.g., Avatar of War) |

**Synergy Strength Levels**:

- Minor (green): Small bonus
- Moderate (yellow): Noticeable improvement
- Strong (orange): Significant combo
- Legendary (magenta): Game-changing combo

**20 Predefined Synergies**:

- Critical Master (Crit Eye + Executioner)
- Blood Warrior (Lifesteal + Sharp Blades)
- Elemental Storm (Fire + Frost + Lightning)
- Speed Demon (Swift Feet + Fury)
- Iron Wall (Tough Skin + Vitality)
- Vampire Lord (Lifesteal + Blood Frenzy + Soul Drain)
- Combo Specialist (Combo Master + Showoff + Momentum)
- High Stakes (Glass Cannon + Berserker)
- Aftershock (Echo Strike + Chain Reaction)
- Last Stand (Revenge + Bullet Time)
- Grim Reaper (Soul Harvest + Executioner's Wrath)
- Ghost Recovery (Phantom Shift + Regeneration)
- Brotherhood (Blood Pact + Tough Skin)
- Juggernaut (Unstoppable Force + Momentum)
- Path of the Berserker (5+ Offense → Avatar of War)
- Path of the Guardian (5+ Defense → Unkillable)
- Cursed Gambler (Curse stacking for rewards)
- War Ascension, Immortal Ascension, Dance with Death

**UI Features**:

- SYNERGY! badge when perk completes a synergy
- Recommended indicator for near-complete synergies
- Synergy hint text on hover showing combo name
- Synergy score (S:X) in comparison panel
- Color-coded by synergy strength

---

### [x] 9. Weekly Challenges COMPLETED

**Impact**: +30% weekly retention
**Effort**: Low

Created weekly challenge system with cumulative tracking across sessions:

- `src/main/java/com/devmod/endurance/challenges/WeeklyChallenge.java` - Challenge definitions
- `src/main/java/com/devmod/endurance/challenges/WeeklyChallengeManager.java` - Rotation & tracking

**Challenge Tiers**:

| Tier | Reward Multiplier | Base Tokens | Base Prestige |
| ---- | ----------------- | ----------- | ------------- |
| STANDARD | 1.0x | 2,500 | 15 |
| EPIC | 2.0x | 5,000 | 30 |
| LEGENDARY | 4.0x | 10,000 | 60 |

**Weekly Challenge Types**:

- Total Kills (500/1000/2500 cumulative)
- Total Waves (50/100 cumulative)
- Total Runs (3/7 runs per week)
- Perfect Runs (3/5 deathless completions)
- Boss Slayer (5/10/20 bosses)
- Style Master (S rank x5, SS rank x3, SSS rank x1)
- Combo Master (50+ combo x3, 75+ combo x5)
- Endless Warrior (reach wave 25/50 in endless)
- Multi-Boss Run (kill 5 bosses in single run)

**Features**:

- Monday UTC reset (7-day cycle)
- Deterministic rotation per week
- 2 active challenges per week (varied tiers)
- Cumulative session tracking (kills, waves, combos persist)
- Extended progress tracking (highest combo, highest wave, perfect runs)
- JSON persistence with extended stats

---

## P3 - LOW PRIORITY (Future)

### [x] 10. Season/Battle Pass System COMPLETED

**Impact**: +45% long-term engagement
**Effort**: High

Created comprehensive Season Pass system with dual reward tracks:

- `src/main/java/com/devmod/endurance/season/SeasonPassSystem.java` - Core system
- `src/main/java/com/devmod/endurance/season/PlayerSeasonProgress.java` - Player tracking
- `src/main/java/com/devmod/endurance/season/SeasonPassPayload.java` - Network sync
- `src/main/java/com/devmod/client/endurance/ClientSeasonCache.java` - Client cache

**Season Structure**:

- 100 tiers per season
- 90-day season duration
- Free track: Rewards every 5 tiers (tokens, prestige, items)
- Premium track: Rewards every tier (enhanced + exclusives)

**XP Sources**:

| Activity | XP Reward |
| -------- | --------- |
| Kill | 2 XP |
| Wave Complete | 50 XP |
| Flawless Wave | +75 XP |
| Boss Kill | 200 XP |
| Daily Challenge | 500 XP |
| Weekly Challenge | 2000 XP |
| Style S/SS/SSS | 100/200/500 XP |
| High Combo 50+/100+ | 150/300 XP |

**Reward Types**:

- Tokens, Prestige points
- XP Boosts (timed multipliers)
- Exclusive items, cosmetics, perks, titles

**Premium Perks** (every 10 tiers):

- Season Striker (+5% damage)
- Season Guardian (+5% defense)
- Season Collector (+10% tokens)
- Season Elite (+1 perk choice)
- Season Eternal (+2% permanent stats)

### [x] 11. Guild/Clan Objectives COMPLETED

**Impact**: +50% social engagement
**Effort**: High

Created comprehensive Guild system with cooperative objectives:

- `src/main/java/com/devmod/endurance/guild/GuildSystem.java` - Core system
- `src/main/java/com/devmod/endurance/guild/Guild.java` - Guild data model

**Guild Features**:

- Create/join guilds (max 50 members)
- Ranks: Leader, Officer, Veteran, Member
- Guild bank for shared resources
- 20 level progression with XP

**Weekly Guild Objectives**:

| Objective | Type | Reward |
| --------- | ---- | ------ |
| Guild Slayers | Kill 5000+ mobs | 2000 tokens |
| Wave Warriors | Complete 200+ waves | 1500 tokens |
| Rotating (Boss/Style/Flawless) | Varies | 2500 tokens |

**Guild Perks** (unlocked by level):

- Level 5: +5% token gain
- Level 7: +5% season XP
- Level 10: +10% token gain
- Level 12: Extra weekly objective
- Level 15: +10% season XP
- Level 20: +15% token gain

**Leaderboards**:

- Weekly rankings with tier rewards
- All-time guild rankings
- Member contribution tracking

### [x] 12. Prestige Reset (New Game+) COMPLETED

**Impact**: +35% retention for completionists
**Effort**: Medium

Created Ascension/New Game+ system for prestige reset with permanent bonuses:

- `src/main/java/com/devmod/endurance/PrestigeResetSystem.java` - Core ascension mechanics

**Ascension Levels** (10 total):

| Level | Title | Cost | Key Bonus |
| ----- | ----- | ---- | --------- |
| 1 | The Initiated | 100 | +3% damage |
| 2 | The Awakened | 150 | +3% defense |
| 3 | The Proven | 200 | +5% token multiplier |
| 4 | The Exalted | 250 | +200 starting tokens |
| 5 | The Transcendent | 300 | +1% lifesteal |
| 6 | The Mythic | 350 | +2% crit chance |
| 7 | The Divine | 400 | -5% combo decay |
| 8 | The Celestial | 450 | +1 extra perk slot |
| 9 | The Immortal | 500 | +5% damage |
| 10 | The Eternal | 550 | +5% token multiplier |

**Exclusive Ascension Perks** (one per level):

- Ascended Vitality, Eternal Champion, Wealth of Ages
- Primordial Strikes, Undying Soul, Eternal Flow
- Transcendent Combo, Divine Protection, Cosmic Might
- Infinite Potential

**Features**:

- Permanent cumulative bonuses across runs
- Exclusive perks unlocked per ascension level
- Title progression for bragging rights
- Integration with PlayerWallet persistence

---

## Implementation Order

1. [x] Create this document
2. [x] P0.1 - Daily Challenges System
3. [x] P0.2 - Prestige Milestones
4. [x] P0.3 - Comeback Mechanic
5. [x] P1.4 - Transformative Perks
6. [x] P1.5 - Leaderboard System
7. [x] P1.6 - Dynamic Tension System
8. [x] P2.7 - Directive Chains
9. [x] P2.8 - Synergy Preview UI
10. [x] P2.9 - Weekly Challenges
11. [x] P3.12 - Prestige Reset (New Game+)

---

## Progress Log

| Date | Item | Status |
| ---- | ---- | ------ |
| 2025-12-25 | Document created | Done |
| 2025-12-25 | P0.1 - Daily Challenges System | **COMPLETED** |
| 2025-12-25 | P0.2 - Prestige Milestones | **COMPLETED** |
| 2025-12-25 | P0.3 - Comeback Mechanic (Rising Phoenix) | **COMPLETED** |
| 2025-12-25 | P1.4 - 10 Transformative Perks | **COMPLETED** |
| 2025-12-25 | P1.5 - Leaderboard System | **COMPLETED** |
| 2025-12-25 | P1.6 - Dynamic Tension System | **COMPLETED** |
| 2025-12-25 | P2.9 - Weekly Challenges | **COMPLETED** |
| 2025-12-25 | P2.7 - Directive Chains | **COMPLETED** |
| 2025-12-25 | P2.8 - Synergy Preview UI | **COMPLETED** |
| 2025-12-25 | P3.12 - Prestige Reset (New Game+) | **COMPLETED** |

---

## Files Created/Modified

### New Files

- `src/main/java/com/devmod/endurance/challenges/DailyChallenge.java`
- `src/main/java/com/devmod/endurance/challenges/DailyChallengeManager.java`
- `src/main/java/com/devmod/endurance/challenges/ChallengeSyncPayload.java`
- `src/main/java/com/devmod/client/endurance/ClientChallengeCache.java`
- `src/main/java/com/devmod/endurance/PrestigeMilestone.java`
- `src/main/java/com/devmod/endurance/ComebackSystem.java`
- `src/main/java/com/devmod/endurance/LeaderboardSystem.java`
- `src/main/java/com/devmod/endurance/TensionSystem.java`
- `src/main/java/com/devmod/endurance/TensionUpdatePayload.java`
- `src/main/java/com/devmod/client/network/ClientTensionCache.java`
- `src/main/java/com/devmod/endurance/challenges/WeeklyChallenge.java`
- `src/main/java/com/devmod/endurance/challenges/WeeklyChallengeManager.java`
- `src/main/java/com/devmod/endurance/DirectiveChain.java`
- `src/main/java/com/devmod/endurance/DirectiveChainManager.java`
- `src/main/java/com/devmod/endurance/PerkSynergySystem.java`
- `src/main/java/com/devmod/endurance/PrestigeResetSystem.java`

### Modified Files

- `src/main/java/com/devmod/endurance/RewardSystem.java` - Prestige milestone + Ascension system integration
- `src/main/java/com/devmod/endurance/PerkSystem.java` - 10 new transformative perks
- `src/main/java/com/devmod/endurance/EnduranceEventCombat.java` - Phoenix Rising + Tension tracking
- `src/main/java/com/devmod/endurance/EnduranceEventHandler.java` - Leaderboard + Tension integration
- `src/main/java/com/devmod/endurance/EnduranceEventTick.java` - Tension-based boss check
- `src/main/java/com/devmod/endurance/ComboSystem.java` - Challenge tracking
- `src/main/java/com/devmod/endurance/WaveManager.java` - Dynamic boss spawning
- `src/main/java/com/devmod/endurance/BossWaveSystem.java` - Tension-aware isBossWave
- `src/main/java/com/devmod/events/CommonModEvents.java` - System initialization
- `src/main/java/com/devmod/network/ChannelId.java` - Challenge + Tension channels
- `src/main/java/com/devmod/network/NetworkHandler.java` - Payload registration
- `src/main/java/com/devmod/network/handlers/EnduranceNetworkHandler.java` - sendTensionUpdate
- `src/main/java/com/devmod/client/network/ClientOverlayHandlers.java` - Tension handler
- `src/main/java/com/devmod/endurance/ComboSystem.java` - Weekly challenge tracking
- `src/main/java/com/devmod/endurance/PerkChoicesPayload.java` - Synergy preview data in payload
- `src/main/java/com/devmod/network/handlers/EnduranceNetworkHandler.java` - Synergy analysis on perk send
- `src/main/java/com/devmod/client/endurance/PerkSelectionScreen.java` - Synergy UI indicators
