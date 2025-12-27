# DevMod Progressive Test Plan

> Last updated: 2025-12-26
> Status: PLANNING

## Document Overview

**Version:** 1.0
**Target:** NeoForge 1.21.1
**Date:** 2025-12-10
**Status:** Initial Analysis Complete

---

## 1. UX FLOW MAP

### 1.1 Core Flows

#### Flow A: Endurance Quest (Primary Feature)

```
[Player Entry]
    |
    v
[Open Radial Menu] --> [Select Endurance Quest]
    |
    v
[Endurance Quest Screen]
    |-- Select Mob Type
    |-- Configure Waves (1-100)
    |-- Toggle Endless Mode
    |-- Select Arena Template (e.g., default_flat_64)
    |
    v
[Start Quest] --> [StartQuestPayload to Server]
    |
    v
[InstanceManager.startQuest()]
    |-- Create PlayerInstanceSnapshot (disk-persisted)
    |-- Create Dynamic Dimension (DynamicDimensionManager)
    |-- Resolve Arena Template (TemplateResolver)
    |-- Build Arena (TemplateArenaBuilder)
    |-- Teleport Player to Instance
    |
    v
[Quest Active State]
    |
    +---> [Wave Loop]
    |         |
    |         v
    |     [WaveManager.startWave()]
    |         |-- Spawn mobs in arena
    |         |-- Apply wave modifiers
    |         |-- Start ComboSystem session
    |         |
    |         v
    |     [Combat Phase]
    |         |-- Track damage (CombatTracker)
    |         |-- Process hits (PerkSystem)
    |         |-- Update combo (ComboSystem)
    |         |-- Apply mutators (MutatorSystem)
    |         |
    |         v
    |     [Wave Complete]
    |         |-- Checkpoint reached
    |         |-- Offer Perk choices
    |         |-- Player decides: Continue / Exit
    |
    +---> [Player Death]
    |         |
    |         v
    |     [Death Screen]
    |         |-- Respawn (costs points)
    |         |-- Give Up (exit quest)
    |
    +---> [Quest End]
              |
              v
          [Completion Screen]
              |-- Calculate rewards (RewardSystem)
              |-- Update stats (GamificationManager)
              |-- Update leaderboards
              |
              v
          [RecoverySystem]
              |-- Restore player state
              |-- Teleport to original position
              |-- Cleanup instance dimension
```

#### Flow B: Mob Configuration

```
[Target Mob] --> [Press Inspect Key]
    |
    v
[MobConfigPage]
    |-- Health
    |-- Damage
    |-- Armor
    |-- Speed
    |-- Follow Range
    |
    v
[Apply Specific] --> [UpdateMobStatsPayload]
    |
    v
[Apply Global] --> [MobConfigManager.setGlobalStats()]
```

#### Flow C: Weapon Configuration

```
[Hold Weapon] --> [Open Weapon Editor]
    |
    v
[WeaponEditorScreen]
    |-- Head Multiplier
    |-- Body Multiplier
    |-- Legs Multiplier
    |-- Armor Penetration
    |-- Base Damage Bonus
    |
    v
[Apply] --> [UpdateWeaponPayload]
```

#### Flow D: UI Settings

```
[Open Radial Menu] --> [Settings]
    |
    v
[UnifiedSettingsScreen]
    |-- GeneralSettingsPage
    |-- CombatSettingsPage
    |-- DebugOverlaysPage
    |-- VisualizersPage
    |-- KeybindsPage
    |-- TelemetryPage
    |
    v
[SettingsManager] --> Persist to disk
```

### 1.2 Advanced Flows

#### Flow E: Multiplayer Endurance Quest (Party System)

```
[Party Leader] --> [Invite Players]
    |
    v
[Party Members Join]
    |
    v
[Start Party Quest]
    |-- All members get snapshot
    |-- All teleport to same instance
    |-- Shared wave state
    |
    v
[Combat Phase]
    |-- Independent combo tracking
    |-- Shared wave progress
    |-- Any death = choice for that player
```

#### Flow F: Boss Wave System

```
[Every 5th Wave]
    |
    v
[BossWaveSystem.isBossWave(wave)]
    |
    v
[BossAlertPayload] --> Client shows alert
    |
    v
[Spawn Boss]
    |-- Select archetype (Berserker, Tank, etc.)
    |-- Apply special abilities
    |-- Custom AI behaviors
    |
    v
[Boss Combat]
    |-- Boss phases
    |-- Special attacks
    |-- Enrage timers
    |
    v
[Boss Defeated]
    |-- Bonus points
    |-- Badge unlock chance
```

### 1.3 Edge Cases

| Edge Case | Trigger | Expected Behavior |
|-----------|---------|-------------------|
| Server crash during quest | Server shutdown | RecoverySystem restores on login |
| Player disconnect during wave | Network loss | Player logs back in, quest recovers |
| Player dies multiple times | Spam respawn | Point cost increases per death |
| Exit arena during combat | Teleport/portal | ArenaConfinement teleports back |
| Mob dies from environment | Lava, fall, etc. | Mob respawns (not counted as kill) |
| Chunk unload during quest | Player far | Instance dimensions don't unload |
| Switch dimension during quest | Nether portal | Force end quest |
| Perk stacking limits | Select same perk | Respect maxStacks |
| Empty wave spawn | No valid positions | Fallback spawn positions |

### 1.4 Prerequisites

| System | Prerequisite |
|--------|--------------|
| Endurance Quest | Player not in another quest |
| Perk Selection | Wave completed, pending choices |
| Shop Purchase | Sufficient tokens in wallet |
| Boss Wave | Wave number divisible by 5 |
| Global Config | Operator permission |

---

## 2. PROGRESSIVE TEST PLAN

### Level 0 (L0) - Smoke / Boot

**Objective:** Verify mod loads without breaking the game

**Criteria:**
- Client starts without crash
- Server starts without crash
- World creation succeeds
- World loading succeeds
- All registrations complete

**Test Cases:**

| ID | Test Case | Type | Validation |
|----|-----------|------|------------|
| L0-01 | Client startup | Auto | No crash, no FATAL logs |
| L0-02 | Server startup | Auto | No crash, no FATAL logs |
| L0-03 | New world creation | Auto | World generates without error |
| L0-04 | Existing world load | Auto | World loads without migration errors |
| L0-05 | Registry validation | Auto | All items/blocks/entities registered |
| L0-06 | Mixin application | Auto | All mixins apply correctly |
| L0-07 | Network channel registration | Auto | All 22 channels registered |
| L0-08 | Config loading | Auto | Config files created/loaded |
| L0-09 | Keybind registration | Auto | All keybinds available in settings |
| L0-10 | Creative tab | Auto | Tab visible with viewer_item |

**Risk Assessment:**
- Registry errors: HIGH (blocks game load)
- Mixin conflicts: HIGH (can crash)
- Network codec errors: MEDIUM (runtime crashes)

---

### Level 1 (L1) - Core UX Entry

**Objective:** Simulate first user interaction

**Criteria:**
- Primary keybinds work
- UI screens open correctly
- Basic visual feedback present

**Test Cases:**

| ID | Test Case | Type | Validation |
|----|-----------|------|------------|
| L1-01 | Radial menu opens | Manual | Press key, menu renders |
| L1-02 | Settings screen opens | Manual | All categories visible |
| L1-03 | Endurance quest screen | Manual | Opens, mob list populated |
| L1-04 | Weapon editor opens | Manual | Opens when holding weapon |
| L1-05 | Mob inspector opens | Manual | Opens when targeting mob |
| L1-06 | Help overlay toggles | Manual | F1 shows/hides help |
| L1-07 | Quest HUD toggles | Manual | Overlay visible when active |
| L1-08 | Debug overlays toggle | Manual | Each overlay renders |
| L1-09 | Input validation | Auto | Invalid inputs rejected |
| L1-10 | Screen close behavior | Manual | ESC closes screens properly |

**Risk Assessment:**
- Screen rendering: MEDIUM (visual only)
- Keybind conflicts: LOW (configurable)
- Input handling: MEDIUM (can block actions)

---

### Level 2 (L2) - Core Loop Functional

**Objective:** Validate main gameplay systems

**Criteria:**
- Quest starts and completes
- Combat mechanics work
- State persists correctly

**Test Cases:**

| ID | Test Case | Type | Validation |
|----|-----------|------|------------|
| L2-01 | Start endurance quest | Manual | Player teleported to arena |
| L2-02 | Mob spawning | Manual | Correct mob count spawns |
| L2-03 | Kill tracking | Auto | Kills counted correctly |
| L2-04 | Damage dealt tracking | Auto | Damage numbers accurate |
| L2-05 | Wave completion | Manual | Wave ends when all killed |
| L2-06 | Perk selection | Manual | Perks apply correctly |
| L2-07 | Checkpoint exit | Manual | Player returns to origin |
| L2-08 | Quest completion | Manual | Rewards calculated |
| L2-09 | Player death handling | Manual | Death screen shows |
| L2-10 | Respawn mechanics | Manual | Respawn works, costs points |
| L2-11 | NBT persistence | Auto | Snapshot saves/loads |
| L2-12 | Token wallet persistence | Auto | Tokens persist across sessions |
| L2-13 | Mob config application | Manual | Stats change correctly |
| L2-14 | Weapon config application | Manual | Damage modifiers apply |
| L2-15 | Settings persistence | Auto | Settings persist after restart |

**Risk Assessment:**
- Quest state machine: HIGH (can softlock)
- Player teleportation: HIGH (can lose player)
- NBT serialization: HIGH (data loss)

---

### Level 3 (L3) - Edge UX & Anti-Exploit

**Objective:** Find human-triggered edge cases

**Criteria:**
- System handles rapid input
- State transitions are robust
- No exploits possible

**Test Cases:**

| ID | Test Case | Type | Validation |
|----|-----------|------|------------|
| L3-01 | Rapid quest start spam | Manual | Only one quest starts |
| L3-02 | Continue/exit spam | Manual | Single action executes |
| L3-03 | Perk selection spam | Manual | One perk selected |
| L3-04 | Dimension change during quest | Manual | Quest handles gracefully |
| L3-05 | Arena escape attempts | Manual | Player teleported back |
| L3-06 | Item drop during snapshot | Auto | Items preserved |
| L3-07 | Death during perk select | Manual | Screen closes, death handled |
| L3-08 | Mob external death | Auto | Mob respawns |
| L3-09 | Simultaneous quest actions | Auto | Thread-safe handling |
| L3-10 | Config value extremes | Auto | Values clamped correctly |
| L3-11 | Invalid packet data | Auto | Server rejects malformed |
| L3-12 | Permission bypass attempts | Auto | Security service blocks |
| L3-13 | Inventory manipulation | Manual | Protected during quest |
| L3-14 | Command usage during quest | Manual | Blocked or handled |
| L3-15 | World border interaction | Manual | Arena boundary takes precedence |

**Risk Assessment:**
- Race conditions: HIGH (state corruption)
- Security: MEDIUM (exploit potential)
- State desync: HIGH (visual/actual mismatch)

---

### Level 4 (L4) - Multiplayer & Network

**Objective:** Validate networked behavior

**Criteria:**
- Packets sync correctly
- Multi-player scenarios work
- Reconnection handles gracefully

**Test Cases:**

| ID | Test Case | Type | Validation |
|----|-----------|------|------------|
| L4-01 | Quest sync to client | Auto | Client receives updates |
| L4-02 | Perk sync accuracy | Auto | Client shows correct perks |
| L4-03 | Combo sync | Auto | HUD shows current combo |
| L4-04 | Player disconnect during quest | Manual | Quest pauses/ends cleanly |
| L4-05 | Player reconnect recovery | Manual | State restored correctly |
| L4-06 | Two players same mob type | Manual | Separate quests work |
| L4-07 | Late packet handling | Auto | Old packets ignored |
| L4-08 | Packet ordering | Auto | Sequence maintained |
| L4-09 | Server lag simulation | Manual | Client handles delays |
| L4-10 | Large player count | Manual | Performance acceptable |
| L4-11 | Party quest creation | Manual | All members teleport |
| L4-12 | Party member death | Manual | Others continue |
| L4-13 | Party leader disconnect | Manual | Party handles gracefully |
| L4-14 | Cross-dimension packets | Auto | Packets reach correct dimension |
| L4-15 | Config broadcast | Auto | All clients see global changes |

**Risk Assessment:**
- Packet desync: HIGH (breaks gameplay)
- Concurrent modification: HIGH (crashes)
- Recovery failures: CRITICAL (player loss)

---

### Level 5 (L5) - Stress & Performance

**Objective:** Verify stability under load

**Criteria:**
- No memory leaks
- Performance acceptable
- Long sessions stable

**Test Cases:**

| ID | Test Case | Type | Validation |
|----|-----------|------|------------|
| L5-01 | Extended session (1hr) | Manual | No degradation |
| L5-02 | Repeated quest cycles | Auto | Memory stable |
| L5-03 | Many concurrent mobs | Manual | FPS acceptable |
| L5-04 | Rapid perk application | Auto | No attribute modifier leak |
| L5-05 | Event listener cleanup | Auto | Listeners removed on end |
| L5-06 | Cache invalidation | Auto | Caches cleared properly |
| L5-07 | Dimension cleanup | Auto | Old dimensions removed |
| L5-08 | Snapshot file cleanup | Auto | Old files removed |
| L5-09 | Combat tracker memory | Auto | Stats don't grow unbounded |
| L5-10 | HUD rendering performance | Manual | Overlay FPS impact minimal |
| L5-11 | Packet throughput | Auto | Network stable under load |
| L5-12 | GC pressure | Auto | Allocation rate acceptable |
| L5-13 | Thread pool saturation | Auto | No thread starvation |
| L5-14 | File I/O blocking | Auto | No main thread blocking |
| L5-15 | Analytics data growth | Auto | Data pruned appropriately |

**Risk Assessment:**
- Memory leaks: HIGH (eventual crash)
- Performance degradation: MEDIUM (poor UX)
- Thread issues: HIGH (deadlocks)

---

### Level 6 (L6) - Compatibility (Optional)

**Objective:** Verify modpack compatibility

**Criteria:**
- No conflicts with common mods
- Mixins don't conflict
- Events don't interfere

**Test Cases:**

| ID | Test Case | Type | Validation |
|----|-----------|------|------------|
| L6-01 | JEI integration | Manual | No conflicts |
| L6-02 | Shader compatibility | Manual | Renders correctly |
| L6-03 | Keybind manager mods | Manual | Keys configurable |
| L6-04 | Performance mods | Manual | No degradation |
| L6-05 | Pehkui (if present) | Manual | Scale integration works |
| L6-06 | Better Combat (if present) | Manual | Damage calc integrates |
| L6-07 | Overlay priority | Manual | HUD visible |
| L6-08 | Event bus priority | Auto | Events fire correctly |
| L6-09 | Mixin conflict scan | Auto | No overlapping injections |
| L6-10 | Dimension mod compat | Manual | Instance dims work |

---

## 3. GATING CHECKLIST

### Level Advancement Requirements

To advance from Level N to Level N+1:

1. **All automated tests pass** (100% green)
2. **Manual test checklist complete** (signed off)
3. **No new WARN/ERROR logs** related to tested features
4. **No observable crashes** during test execution
5. **No desync between client/server** state
6. **Memory profile stable** (for L4+)

### Level Report Template

```
# Level [N] Report

## Objective
[State the level objective]

## Coverage
- Tests executed: X/Y
- Pass rate: Z%
- Coverage areas: [list]

## Results
- PASS: [count]
- FAIL: [count]
- SKIP: [count]

## Issues Found
[List issues with severity]

## Residual Risks
[List any accepted risks with justification]

## Sign-off
- Tested by: [name]
- Date: [date]
- Approved for L[N+1]: [yes/no]
```

---

## 4. BUG LOG FORMAT

```
## Bug #[ID]: [Short Title]

**Severity:** CRITICAL | HIGH | MEDIUM | LOW

**Symptom:**
[Observable behavior]

**Reproduction Steps:**
1. [Step 1]
2. [Step 2]
3. [Step 3]

**Expected:**
[What should happen]

**Actual:**
[What actually happens]

**Root Cause:**
[Technical explanation]

**Fix:**
[Code changes made]

**Files Changed:**
- [file1.java]: [reason]
- [file2.java]: [reason]

**Regression Test:**
[Test ID that validates fix]

**Impact Assessment:**
[What systems might be affected]
```

---

## 5. IMPLEMENTATION PRIORITY

### Phase 1: Critical Path Testing
1. L0 Smoke tests (immediate blockers)
2. L2-01 through L2-08 (quest lifecycle)
3. L3-04 (dimension handling)

### Phase 2: Robustness Testing
1. L3 edge cases
2. L4 networking
3. L2-11 through L2-15 (persistence)

### Phase 3: Polish Testing
1. L1 UX validation
2. L5 performance
3. L6 compatibility

---

## 6. IDENTIFIED SYSTEMS FOR TESTING

### Core Systems
| System | Primary Class | Test Priority |
|--------|---------------|---------------|
| Quest Manager | EnduranceQuestManager | CRITICAL |
| Instance Manager | InstanceManager | CRITICAL |
| Recovery System | RecoverySystem | CRITICAL |
| Wave Manager | WaveManager | HIGH |
| Perk System | PerkSystem | HIGH |
| Combo System | ComboSystem | MEDIUM |
| Combat Tracker | CombatTracker | MEDIUM |
| Reward System | RewardSystem | HIGH |
| Arena Template System | TemplateArenaBuilder | HIGH |
| Dynamic Dimension | DynamicDimensionManager | CRITICAL |

### Network Payloads
| Payload | Direction | Channels |
|---------|-----------|----------|
| StartQuestPayload | C→S | 5 |
| QuestActionPayload | C→S | 6 |
| QuestSyncPayload | S→C | 7 |
| ShopPurchasePayload | C→S | 8 |
| ShopSyncPayload | S→C | 9 |
| PerkChoicesPayload | S→C | 13 |
| PerkSelectionPayload | C→S | 14 |
| QuestCompletionPayload | S→C | 15 |
| BossAlertPayload | S→C | 18 |
| ComboDecayPayload | S→C | 22 |

### UI Components
| Component | Type | Test Focus |
|-----------|------|------------|
| RadialMenuScreenV3 | Screen | Input handling |
| UnifiedSettingsScreen | Screen | Settings persistence |
| PerkSelectionScreen | Screen | Selection logic |
| QuestDeathScreen | Screen | Action routing |
| QuestCompletionScreen | Screen | Data display |
| EnduranceQuestOverlay | HUD | State sync |
| ComboDecayOverlay | HUD | Animation timing |

### Event Handlers
| Handler | Events | Test Focus |
|---------|--------|------------|
| EnduranceEventHandler | Damage, Death, Tick | Combat tracking |
| InstanceEventHandler | Login, Logout, Dimension | Recovery |
| KeyInputHandler | KeyPress | Keybind routing |

---

## 7. NEXT STEPS

1. **Implement L0 GameTests** - Automated boot verification
2. **Create test world** - Pre-configured for testing
3. **Build test harness** - JUnit 5 + NeoForge GameTest
4. **Execute L0** - Validate before proceeding
5. **Document findings** - Bug log + level report

---

*Document generated: 2025-12-10*
*DevMod version: 0.1.0+*
