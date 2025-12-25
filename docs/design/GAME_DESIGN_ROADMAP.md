# DevMod Game Design Roadmap

## Status Legend
- [ ] Not started
- [~] In progress
- [x] Completed
- [!] Blocked

---

## P0 - CRITICAL (This Sprint)

### 1. Flow State System - Combo Variety Enforcement
**Goal**: Prevent repetitive optimal combos, reward dynamic play

**Implementation Tasks**:
- [x] Create `FlowStateTracker` class in `endurance/`
- [x] Add action history tracking (last 10 actions)
- [x] Implement STALE detection (3+ same action = -50% style)
- [x] Implement FRESH bonus (+25% for new actions)
- [x] Implement VIRTUOSO state (5+ unique = 2x multiplier)
- [x] Integrate with existing `ComboSession`
- [x] Add HUD indicator for Flow State
- [ ] Add config options for thresholds

**Files to modify**:
- `src/main/java/com/devmod/combat/combo/ComboSession.java`
- `src/main/java/com/devmod/combat/combo/ActionType.java`
- NEW: `src/main/java/com/devmod/combat/combo/FlowStateTracker.java`
- `src/main/java/com/devmod/client/overlay/ComboHudOverlay.java`

---

## P1 - HIGH PRIORITY (Next Sprint)

### 2. Momentum System - Pacing Enforcement
**Goal**: Punish passive play, reward aggressive engagement

**Implementation Tasks**:
- [ ] Create `MomentumTracker` class
- [ ] Track kills (+15%), idle time (-3%/sec)
- [ ] Implement OVERDRIVE state at 100% (1.5x damage, 2x style, 15 sec)
- [ ] Implement STAGNANT debuff at 0% (mob spawn +20%, style decay 2x)
- [ ] Add momentum bar to HUD
- [ ] Integrate with wave spawning system
- [ ] Add sound/visual cues for state changes

**Files to modify**:
- NEW: `src/main/java/com/devmod/combat/MomentumTracker.java`
- `src/main/java/com/devmod/endurance/EnduranceEventHandler.java`
- `src/main/java/com/devmod/client/overlay/EnduranceQuestOverlay.java`

### 3. Boss DNA Mixing - Dynamic Boss Generation
**Goal**: Prevent predictable boss encounters

**Implementation Tasks**:
- [x] Create `BossDNAMixer` class
- [x] Implement weighted archetype selection (70% primary, 30% secondary)
- [x] Create ability inheritance system
- [x] Implement color blending for mixed bosses
- [x] Add procedural name generator
- [x] Create rare variants (Chimera 5%, Mirror 3%, Evolving 2%)
- [x] Update boss spawn logic in wave director
- [x] Integrate with BossFight state tracking
- [x] Add EVOLVING variant phase transitions

**Files modified**:
- NEW: `src/main/java/com/devmod/endurance/boss/BossDNAMixer.java`
- `src/main/java/com/devmod/endurance/BossWaveSystem.java` (BossFight class + spawnMixedBoss)

---

## P2 - MEDIUM PRIORITY

### 4. Execution System - Finisher Mechanics
**Goal**: Create power fantasy moments with risk/reward

**Implementation Tasks**:
- [x] Detect low HP enemies (<15%)
- [x] Add execution prompt system
- [x] Implement execution animation lock (2 sec i-frames)
- [x] Add rewards: +200 style, 5% HP regen, 30% item drop
- [x] Add risk: interrupted = 2x damage taken
- [x] 3 sec cooldown between executions
- [x] Visual indicator for executable enemies
- [x] Integrate with ComboSystem (EXECUTION action type)
- [x] Integrate with MomentumTracker (kill boost)

**Files modified**:
- NEW: `src/main/java/com/devmod/combat/ExecutionSystem.java`
- `src/main/java/com/devmod/endurance/ComboSystem.java` (added EXECUTION ActionType)
- `src/main/java/com/devmod/endurance/EnduranceEventCombat.java` (interrupt + vulnerability)
- `src/main/java/com/devmod/endurance/EnduranceEventTick.java` (tick integration)

### 5. Devil's Bargain - Mid-Run Risk/Reward
**Goal**: Allow players to stack difficulty for rewards

**Implementation Tasks**:
- [x] Create `DevilsBargainManager` class
- [x] Design curse pool (14 curses in 3 tiers: Minor, Major, Cursed)
- [x] Implement altar spawn every 3 waves
- [x] Create curse selection via chat commands
- [x] Track active curses per session
- [x] Apply reward multipliers (stacking)
- [x] Add visual effects for cursed players (soul particles)
- [x] Integrate with quest lifecycle (start/end sessions)
- [x] Add tick effects (Hunger drain, Burning Soul ignite)

**Files modified**:
- NEW: `src/main/java/com/devmod/endurance/bargain/DevilsBargainManager.java`
- NEW: `src/main/java/com/devmod/endurance/bargain/Curse.java`
- `src/main/java/com/devmod/endurance/EnduranceEventHandler.java` (session lifecycle + altar spawn)
- `src/main/java/com/devmod/endurance/EnduranceEventTick.java` (curse tick effects)

---

## P3 - LOW PRIORITY

### 6. Perk Synergy Web - Hidden Combinations
**Goal**: Add discovery element to perk system

**Implementation Tasks**:
- [x] Define synergy graph (perk → unlocks)
- [x] Create hidden perk pool (10 hidden perks)
- [x] Implement prerequisite checking (PerkCombination, Wave, Category conditions)
- [x] Add "???" display for undiscovered synergies
- [x] Track player discoveries persistently (SavedData + NBT)
- [x] Add sacrifice mechanic (remove perk for stronger one)

**Files modified**:
- NEW: `src/main/java/com/devmod/endurance/perk/PerkSynergyWeb.java`
- `src/main/java/com/devmod/endurance/EnduranceEventHandler.java` (discovery tracking)
- `src/main/java/com/devmod/combat/ExecutionSystem.java` (execution tracking)
- `src/main/java/com/devmod/endurance/bargain/DevilsBargainManager.java` (curse tracking)

### 7. Dynamic Arena Hazards
**Goal**: Make arenas feel alive

**Implementation Tasks**:
- [x] Create hazard event system (ArenaHazardSystem)
- [x] Implement floor crumble (wave 3) - blocks collapse, damage on contact
- [x] Implement blood moon (wave 5) - mobs gain strength/speed buffs
- [x] Implement arena shrink (wave 7) - boundaries contract, void damage outside
- [x] Implement lightning storm (wave 9) - random lightning strikes
- [x] Implement void rifts (wave 11) - portal effects spawn extra enemies
- [x] Add hazard indicators to HUD (HazardInfo record)
- [x] Integrate with wave director (checkWaveHazards in onWaveComplete)

**Files modified**:
- NEW: `src/main/java/com/devmod/endurance/hazard/ArenaHazardSystem.java`
- `src/main/java/com/devmod/endurance/EnduranceEventHandler.java` (lifecycle integration)
- `src/main/java/com/devmod/endurance/EnduranceEventTick.java` (tick processing)

---

## Technical Notes

### Integration Points
- All combat systems hook into `LivingDamageEvent` and `LivingDeathEvent`
- HUD updates via client-side tick handlers
- Multiplayer sync via custom payloads in `NetworkHandler`

### Performance Considerations
- Flow State: O(1) lookups using circular buffer
- Momentum: Single float per player, tick-based decay
- Boss DNA: One-time calculation at spawn

### Config Integration
All new systems should integrate with existing Config system:
- `FlowStateConfig` section
- `MomentumConfig` section
- `BossVarietyConfig` section

---

## Implementation Order

```
Week 1: Flow State System (P0)
Week 2: Momentum System (P1)
Week 3: Boss DNA Mixing (P1)
Week 4: Execution System (P2)
Week 5: Devil's Bargain (P2)
Week 6+: P3 features as time permits
```

---

*Last Updated: 2025-12-25*
*Author: Game Design Team*
