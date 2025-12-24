# DevMod Telemetry System - Complete Documentation

## Overview

The DevMod telemetry system is a comprehensive data collection framework for tracking player behavior, combat mechanics, economy, spatial patterns, and performance metrics. All data is written to `.ndjson` (newline-delimited JSON) files in `run/telemetry/` for analysis.

**Key Statistics:**
- **17 Service Classes** (including EnduranceTelemetryService, PlayerAttributeTelemetryService, AbilityTelemetryService)
- **140+ Trackable Metrics** (32 endurance + 30 player attributes + 8 ability metrics)
- **33 NDJSON File Types** (new: player_attributes.ndjson, ability_usage.ndjson)
- **80+ Data Structures/Records**

**Last Updated:** 2025-12-12

---

## Table of Contents

1. [Quick Start](#1-quick-start)
2. [Service Reference](#2-service-reference)
3. [Metrics Catalog](#3-metrics-catalog)
4. [NDJSON Files](#4-ndjson-files)
5. [Integration Guide](#5-integration-guide)
6. [Integration Checklist](#6-integration-checklist)
7. [Best Practices](#7-best-practices)

---

## 1. Quick Start

### Basic Usage

```java
import com.devmod.telemetry.TelemetryService;

// Log a combat hit
TelemetryService.INSTANCE.logHit(
    room,           // String: room ID
    attacker,       // LivingEntity
    target,         // LivingEntity
    damage,         // float
    damageType,     // String: "melee", "ranged", "environmental"
    bodyPart,       // BodyPart enum
    armorPen,       // float: armor penetration bonus
    distance,       // double: distance to target
    hazardType,     // HazardType enum (nullable)
    entityState     // EntityState record (nullable)
);

// Log a death
TelemetryService.INSTANCE.logDeath(
    room,           // String
    target,         // LivingEntity
    cause           // String: death cause
);

// Log healing
TelemetryService.INSTANCE.logHeal(
    room,           // String
    target,         // LivingEntity
    healAmount,     // float
    source          // String: heal source
);
```

### Accessing Data

```java
// Get weapon statistics
List<String> weaponStats = TelemetryService.INSTANCE.getWeaponSummaries();

// Get room statistics
List<String> roomStats = TelemetryService.INSTANCE.getRoomSummaries();

// Get fight session summaries
List<String> fightStats = TelemetryService.INSTANCE.getFightSummaries();

// Get minion wave stats
List<String> minionStats = TelemetryService.INSTANCE.getAllMinionWaveStats();

// Get entity density report
List<String> densityReport = TelemetryService.INSTANCE.getEntityDensityReport();
```

---

## 2. Service Reference

### 2.1 TelemetryService (Central Coordinator)

**Location:** `com.devmod.telemetry.TelemetryService`

The central hub that coordinates all telemetry operations. Use this as the primary entry point.

#### Combat Methods

| Method | Description | Parameters |
|--------|-------------|------------|
| `logHit()` | Records combat damage | room, attacker, target, damage, type, bodyPart, armorPen, distance, hazard, state |
| `logMiss()` | Records projectile miss | room, attacker, projectileType |
| `logDeath()` | Records entity death | room, target, cause |
| `logHeal()` | Records healing event | room, target, amount, source |

#### Entity Methods

| Method | Description | Parameters |
|--------|-------------|------------|
| `logSpawn()` | Records entity spawn | room, entity, reason, spawnFail |
| `checkOutOfBounds()` | Detects vertical OOB | player |
| `tickAggro()` | Detects aggro/stuck/camping | (call every tick) |

#### Skill Methods

| Method | Description | Parameters |
|--------|-------------|------------|
| `logSkillCast()` | Records skill cast attempt | caster, skillId, room |
| `logSkillHit()` | Confirms skill hit | caster, skillId |
| `tickSkills()` | Detects skill whiffs | (call every tick) |

#### Boss Methods

| Method | Description | Parameters |
|--------|-------------|------------|
| `logBossPhaseStart()` | Starts boss phase tracking | bossName, bossType, phase, room |
| `logBossPhaseEnd()` | Ends boss phase | bossName |

#### Player Methods

| Method | Description | Parameters |
|--------|-------------|------------|
| `trackPlayerRoom()` | Tracks room transitions | player, room |
| `trackRoomVisit()` | Records room visit for backtracking | player, room |
| `logPlayerQuit()` | Records quit position (choke point) | player, room, position |

#### Dungeon Methods

| Method | Description | Parameters |
|--------|-------------|------------|
| `startDungeonSession()` | Begins dungeon tracking | player, dungeonId |
| `endDungeonSession()` | Ends dungeon session | player, outcome |

#### Spatial Methods

| Method | Description | Parameters |
|--------|-------------|------------|
| `updateEntityDensity()` | Updates entity count per room | room, entities |
| `logInvisibleCollision()` | Records invisible wall hit | player, room, position |
| `logParkourFall()` | Records parkour fall | player, room, position, fallDistance |

#### Performance Methods

| Method | Description | Parameters |
|--------|-------------|------------|
| `tickPerformance()` | Samples MSPT/TPS | (call every 20 ticks) |
| `tickFights()` | Closes inactive fights | (call every tick) |

#### Export Methods

| Method | Description |
|--------|-------------|
| `exportDeathHeatmap()` | Exports death positions |
| `exportMovementHeatmap()` | Exports movement samples |
| `exportCampingHeatmap()` | Exports camping positions |
| `exportStuckHeatmap()` | Exports stuck positions |
| `exportAggroDropHeatmap()` | Exports aggro drop positions |
| `exportKitingHeatmap()` | Exports kiting paths |
| `exportChokePointHeatmap()` | Exports quit positions |
| `exportParkourFallHeatmap()` | Exports fall positions |
| `exportDamageStats()` | Exports damage aggregates |

---

### 2.2 DamageTrackingService

**Location:** `com.devmod.telemetry.damage.DamageTrackingService`

Aggregates weapon and room-level damage statistics with persistence.

#### Tracking Methods

```java
// Register a weapon hit
DamageTrackingService.INSTANCE.registerWeaponHit(weaponId, damage, isKill);

// Register a weapon miss
DamageTrackingService.INSTANCE.registerWeaponMiss(weaponId);

// Register room damage
DamageTrackingService.INSTANCE.registerRoomDamage(room, isToPlayer, damage, isDeath);

// Register room healing
DamageTrackingService.INSTANCE.registerRoomHeal(room, isToPlayer, amount);

// Register minion damage
DamageTrackingService.INSTANCE.registerMinionDamage(entityId, damage);
```

#### Data Structures

```java
// WeaponAggregate
record WeaponAggregate(
    double totalDamage,
    int hits,
    int kills,
    int misses,
    double accuracy  // hits / (hits + misses) * 100
);

// RoomAggregate
record RoomAggregate(
    double damageToPlayers,
    double damageToMobs,
    int playerDeaths,
    int mobDeaths,
    double healToPlayers,
    double healToMobs
);
```

---

### 2.3 FightSessionService

**Location:** `com.devmod.telemetry.combat.FightSessionService`

Tracks individual fight sessions with participants, kills, TTK, and burst damage.

#### Tracking Methods

```java
// Register hit in active fight
FightSessionService.INSTANCE.registerHit(room, worldId, playerName, isMobKill, isPlayerDeath, mobType, playerName);

// Register burst damage (max in 2s window)
FightSessionService.INSTANCE.registerBurstDamage(room, damage);

// Register Time To Kill
FightSessionService.INSTANCE.registerTTK(room, entityType, ttkMs);

// Register HP after hit
FightSessionService.INSTANCE.registerHpAfterHit(room, isPlayer, hpPercent);

// Tick to close inactive fights (call every tick)
FightSessionService.INSTANCE.tick(fightResultConsumer);
```

#### Data Structures

```java
// FightSessionResult (written to fights.ndjson)
record FightSessionResult(
    String room,
    String worldId,
    long startMs,
    long endMs,
    long durationMs,
    int hits,
    int mobKills,
    int playerDeaths,
    Set<String> players,
    Map<String, Integer> mobKillsByType,
    Map<String, Integer> playerDeathsByName,
    Map<String, TTKAggregate> ttkByType,
    double maxBurst,
    double avgHpAfterPlayers,
    double avgHpAfterMobs
);

// TTKAggregate
record TTKAggregate(
    int count,
    long totalMs,
    long maxMs
) {
    long avgMs() { return count > 0 ? totalMs / count : 0; }
}
```

---

### 2.4 SkillTrackingService

**Location:** `com.devmod.telemetry.skills.SkillTrackingService`

Tracks skill casts and detects whiffs (casts without hits within 2s).

#### Tracking Methods

```java
// Record skill cast
SkillTrackingService.INSTANCE.recordCast(casterId, skillId, room, worldId, casterName, casterType);

// Confirm skill hit (removes from whiff detection)
SkillTrackingService.INSTANCE.recordHit(casterId, skillId);

// Record skill damage for stats
SkillTrackingService.INSTANCE.recordDamage(skillId, damage);

// Tick to detect whiffs (call every tick)
SkillTrackingService.INSTANCE.tick(whiffConsumer);
```

#### Data Structures

```java
// SkillWhiff (written to alerts.ndjson)
record SkillWhiff(
    String skillId,
    String room,
    String worldId,
    String casterName,
    String casterType,
    long castTime
);

// SkillStats
class SkillStats {
    String skillId;
    int uses;
    int hits;
    double totalDamage;
    long lastUseMs;
    double hitRate();  // hits / uses * 100
}
```

---

### 2.5 EntityTrackingService

**Location:** `com.devmod.telemetry.entity.EntityTrackingService`

Detects stuck entities, aggro drops, camping, kiting, and minion waves.

#### Tracking Methods

```java
// Check if entity is stuck (no movement for threshold)
EntityTrackingService.INSTANCE.checkStuck(entityId, currentPos);

// Check for aggro drop (no target for threshold)
EntityTrackingService.INSTANCE.checkAggroDrop(entityId, hasTarget);

// Check for camping (same position, multiple hits)
EntityTrackingService.INSTANCE.checkCamping(playerId, hitPos);

// Register entity spawn
EntityTrackingService.INSTANCE.registerSpawn(entityId, worldId, position);

// Update path and detect kiting/spin
EntityTrackingService.INSTANCE.updatePathAndDetectIssue(entityId, position, yaw);

// Minion tracking
EntityTrackingService.INSTANCE.registerMinionSpawn(room, minionId);
EntityTrackingService.INSTANCE.registerMinionDeath(room, minionId, damageDealt);
```

#### Configuration

```java
EntityTrackingService.INSTANCE.setStuckThresholdMs(3000);        // 3s default
EntityTrackingService.INSTANCE.setAggroDropThresholdMs(5000);    // 5s default
EntityTrackingService.INSTANCE.setCampingHitsThreshold(5);       // 5 hits default
EntityTrackingService.INSTANCE.setCampingTimeThresholdMs(10000); // 10s default
```

---

### 2.6 EconomyMetricsService

**Location:** `com.devmod.telemetry.economy.EconomyMetricsService`

Tracks loot distribution, item acquisition, drop rates, and resource scarcity.

#### Tracking Methods

```java
// Record chest opening
EconomyMetricsService.INSTANCE.recordChestOpen(chestId, playerName, items);

// Record item pickup
EconomyMetricsService.INSTANCE.recordItemPickup(itemId, playerName, quantity);

// Record mob drop
EconomyMetricsService.INSTANCE.recordMobDrop(mobType, itemId, quantity);

// Record mob kill
EconomyMetricsService.INSTANCE.recordMobKill(mobType, hadLoot);

// Record item used/consumed
EconomyMetricsService.INSTANCE.recordItemUsed(itemId, playerName, quantity);

// Record item discarded
EconomyMetricsService.INSTANCE.recordItemDiscarded(itemId, playerName, quantity);
```

#### Data Access

```java
// Get mob loot statistics
Optional<MobLootStats> stats = EconomyMetricsService.INSTANCE.getMobLootStats(mobType);

// Get all mob loot stats
Map<String, MobLootStats> allStats = EconomyMetricsService.INSTANCE.getAllMobLootStats();

// Get drop rate for specific item from mob
double dropRate = EconomyMetricsService.INSTANCE.getItemDropPercentage(mobType, itemId);

// Get top killed mobs
List<Map.Entry<String, MobLootStats>> topMobs = EconomyMetricsService.INSTANCE.getTopKilledMobs(10);

// Get scarcity index (0.0 = abundant, 1.0 = scarce)
double scarcity = EconomyMetricsService.INSTANCE.calculateScarcityIndex(itemId);

// Get session stats
SessionEconomyStats sessionStats = EconomyMetricsService.INSTANCE.getSessionStats();
```

#### Data Structures

```java
// MobLootStats
class MobLootStats {
    int killCount;
    int killsWithLoot;
    int totalItemsDropped;
    Map<String, ItemDropStats> itemDrops;
    double getLootDropPercentage();  // killsWithLoot / killCount * 100
    double getAvgItemsPerKill();     // totalItemsDropped / killCount
}

// SessionEconomyStats
record SessionEconomyStats(
    int totalChestsOpened,
    int totalItemsPickedUp,
    int totalItemsDropped,
    int totalItemsUsed,
    double itemsPerMinute,
    String mostAcquiredItem,
    int mostAcquiredCount,
    String mostScarceItem,
    double scarcityIndex
);
```

---

### 2.7 DungeonSessionService

**Location:** `com.devmod.telemetry.dungeon.DungeonSessionService`

Tracks dungeon runs with outcomes, room sequences, and combat statistics.

#### Tracking Methods

```java
// Start dungeon session
DungeonSessionService.INSTANCE.startSession(playerId, dungeonId);

// End dungeon session
SessionResult result = DungeonSessionService.INSTANCE.endSession(playerId, outcome);

// Record room entry
DungeonSessionService.INSTANCE.enterRoom(playerId, roomId);

// Record combat events within dungeon
DungeonSessionService.INSTANCE.recordDeath(playerId);
DungeonSessionService.INSTANCE.recordKill(playerId);
DungeonSessionService.INSTANCE.recordDamageDealt(playerId, damage);
DungeonSessionService.INSTANCE.recordDamageTaken(playerId, damage);

// Record room visit for backtrack detection
DungeonSessionService.INSTANCE.recordRoomVisit(playerId, roomId);
```

#### Data Structures

```java
// SessionResult (written to dungeon_sessions.ndjson)
record SessionResult(
    String playerName,
    String dungeonId,
    String outcome,      // "completed", "abandoned", "wipe"
    long startMs,
    long endMs,
    long durationMs,
    int deaths,
    int kills,
    double damageDealt,
    double damageTaken,
    List<String> roomSequence
);
```

---

### 2.8 HeatmapService

**Location:** `com.devmod.telemetry.spatial.HeatmapService`

Aggregates spatial position frequency data for 9 heatmap types.

#### Tracking Methods

```java
HeatmapService.INSTANCE.recordStuck(room, position);
HeatmapService.INSTANCE.recordAggroDrop(room, position);
HeatmapService.INSTANCE.recordKiting(room, position);
HeatmapService.INSTANCE.recordDeath(room, position);
HeatmapService.INSTANCE.recordMovement(room, position);
HeatmapService.INSTANCE.recordCamping(room, position);
HeatmapService.INSTANCE.recordChokePoint(room, position);
HeatmapService.INSTANCE.recordInvisibleCollision(room, position);
HeatmapService.INSTANCE.recordParkourFall(room, position);
```

#### Data Access

```java
// Get heatmap data (room -> position -> count)
Map<String, Map<BlockPos, Integer>> stuckData = HeatmapService.INSTANCE.getStuckHeatmap();
Map<String, Map<BlockPos, Integer>> deathData = HeatmapService.INSTANCE.getDeathHeatmap();
// ... etc for all 9 types

// Get stats for specific room
HeatmapStats stats = HeatmapService.INSTANCE.getStatsForRoom(room);
```

---

### 2.9 SpatialMetricsService

**Location:** `com.devmod.telemetry.spatial.SpatialMetricsService`

Tracks choke points, entity density, invisible collisions, and parkour falls.

#### Tracking Methods

```java
// Record player quit (choke point detection)
SpatialMetricsService.INSTANCE.recordQuit(room, position, playerName);

// Update entity density for room
SpatialMetricsService.INSTANCE.updateEntityDensity(room, entityList);

// Record invisible collision
SpatialMetricsService.INSTANCE.recordInvisibleCollision(room, position, playerName);

// Record parkour fall
SpatialMetricsService.INSTANCE.recordParkourFall(room, position, fallDistance, playerName);
```

#### Data Access

```java
// Get entity density info
Optional<EntityDensityInfo> density = SpatialMetricsService.INSTANCE.getDensityInfo(room);

// Get formatted density report
List<String> report = SpatialMetricsService.INSTANCE.getEntityDensityReport();
```

---

### 2.10 BossPhaseService

**Location:** `com.devmod.telemetry.boss.BossPhaseService`

Tracks boss phase transitions with duration and room context.

#### Tracking Methods

```java
// Start boss phase
BossPhaseService.INSTANCE.startPhase(bossName, bossType, phaseName, room, worldId);

// End boss phase
Optional<PhaseResult> result = BossPhaseService.INSTANCE.endPhase(bossName);
```

#### Data Structures

```java
// PhaseResult (written to phases.ndjson)
record PhaseResult(
    String bossName,
    String bossType,
    String phase,
    long startMs,
    long endMs,
    long durationMs,
    String room,
    String world
);
```

---

### 2.11 PlayerTrackingService

**Location:** `com.devmod.telemetry.player.PlayerTrackingService`

Tracks player room transitions, movement sampling, and backtracking.

#### Tracking Methods

```java
// Track player room (samples movement every 2s)
RoomTrackResult result = PlayerTrackingService.INSTANCE.trackPlayerRoom(playerId, room, position);

// Check for out of bounds
OutOfBoundsResult oob = PlayerTrackingService.INSTANCE.checkOutOfBounds(playerId, position, roomBounds);

// Track room visit for backtracking
BacktrackResult backtrack = PlayerTrackingService.INSTANCE.trackRoomVisit(playerId, room);

// Update idle tracking
IdleResult idle = PlayerTrackingService.INSTANCE.updateIdleTracking(playerId, position, yaw);
```

---

### 2.12 MinionService

**Location:** `com.devmod.telemetry.entity.MinionService`

Tracks minion wave metrics per room.

#### Tracking Methods

```java
// Record minion spawn
MinionService.INSTANCE.recordSpawn(room, minionId);

// Record minion death
MinionService.INSTANCE.recordDeath(room, minionId, damageDealt);
```

#### Data Access

```java
// Get peak concurrent minions
int peak = MinionService.INSTANCE.getPeakConcurrent(room);

// Get current minion count
int current = MinionService.INSTANCE.getCurrentCount(room);

// Get all stats formatted
List<String> allStats = MinionService.INSTANCE.getAllStats();
```

---

### 2.13 PlayerProgressionService (NEW)

**Location:** `com.devmod.telemetry.progression.PlayerProgressionService`

Tracks player progression metrics: blocks, XP, advancements, dimensions, combat details, trades, and fishing.

#### Block Tracking

```java
// Record block break
PlayerProgressionService.INSTANCE.recordBlockBreak(player, level, pos, blockId);

// Record block place
PlayerProgressionService.INSTANCE.recordBlockPlace(player, level, pos, blockId);
```

#### XP & Level Tracking

```java
// Record XP pickup
PlayerProgressionService.INSTANCE.recordXpPickup(player, amount);

// Record level change
PlayerProgressionService.INSTANCE.recordLevelChange(player, oldLevel, newLevel);
```

#### Advancement Tracking

```java
// Record advancement earned
PlayerProgressionService.INSTANCE.recordAdvancement(player, advancementId, title);
```

#### Dimension Tracking

```java
// Record dimension change
PlayerProgressionService.INSTANCE.recordDimensionChange(player, fromDim, toDim);
```

#### Combat Detail Tracking

```java
// Record critical hit
PlayerProgressionService.INSTANCE.recordCriticalHit(player, targetName, targetType, damage, multiplier);

// Record attack
PlayerProgressionService.INSTANCE.recordAttack(player, targetName, targetType, weaponId, isSweep);
```

#### Trade & Fishing Tracking

```java
// Record villager trade
PlayerProgressionService.INSTANCE.recordTrade(player, villagerType, profession, itemBought, buyCount, itemSold, sellCount);

// Record fishing catch
PlayerProgressionService.INSTANCE.recordFishing(player, itemCaught, count, pos);
```

#### Data Access

```java
// Get block stats for player
BlockStats stats = PlayerProgressionService.INSTANCE.getPlayerBlockStats(playerId);

// Get XP stats for player
XpStats xp = PlayerProgressionService.INSTANCE.getPlayerXpStats(playerId);

// Get combat stats for player
CombatStats combat = PlayerProgressionService.INSTANCE.getPlayerCombatStats(playerId);

// Get totals
int blocksBroken = PlayerProgressionService.INSTANCE.getTotalBlocksBroken();
int trades = PlayerProgressionService.INSTANCE.getTotalTrades();
```

---

### 2.14 EnduranceTelemetryService (NEW)

**Location:** `com.devmod.telemetry.endurance.EnduranceTelemetryService`

Centralized telemetry service for all Endurance Quest systems including waves, combos, perks, mutators, rewards, parties, bosses, and gamification.

#### Wave Tracking

```java
// Record wave start
EnduranceTelemetryService.INSTANCE.recordWaveStart(
    questId, waveNumber, enemyCount, playerCount, questType, modifiers
);

// Record wave completion
EnduranceTelemetryService.INSTANCE.recordWaveComplete(
    questId, waveNumber, kills, durationMs, timedOut, killsPerSecond
);

// Record individual wave kill
EnduranceTelemetryService.INSTANCE.recordWaveKill(
    questId, waveNumber, mobType, killerUUID, damageDealt
);
```

#### Combo System (DMC-Style)

```java
// Record style rank change
EnduranceTelemetryService.INSTANCE.recordStyleRankChange(
    playerId, questId, oldRank, newRank, styleScore, currentCombo
);

// Record combo milestone
EnduranceTelemetryService.INSTANCE.recordComboMilestone(
    playerId, questId, comboCount, styleGain, currentRank
);

// Record combo break
EnduranceTelemetryService.INSTANCE.recordComboBreak(
    playerId, questId, comboLost, previousRank, currentRank, damageTaken
);

// Record special action
EnduranceTelemetryService.INSTANCE.recordSpecialAction(
    playerId, questId, actionType, styleGain, comboAfter
);
```

#### Perk System (Roguelike)

```java
// Record perk selection
EnduranceTelemetryService.INSTANCE.recordPerkSelected(
    playerId, questId, perkId, perkName, tier, category, stacks, totalPerks
);

// Record perk choices offered
EnduranceTelemetryService.INSTANCE.recordPerkChoicesOffered(
    playerId, questId, waveNumber, choices
);
```

#### Mutator System

```java
// Record mutators assigned at quest start
EnduranceTelemetryService.INSTANCE.recordMutatorsAssigned(
    questId, activeMutators, totalRewardMultiplier
);

// Record mutator added mid-quest
EnduranceTelemetryService.INSTANCE.recordMutatorAdded(
    questId, mutatorId, waveNumber
);
```

#### Reward System

```java
// Record currency earned
EnduranceTelemetryService.INSTANCE.recordCurrencyEarned(
    playerId, questId, currency, amount, source
);

// Record loot drop
EnduranceTelemetryService.INSTANCE.recordLootDrop(
    playerId, questId, itemId, count, tier
);

// Record achievement unlocked
EnduranceTelemetryService.INSTANCE.recordAchievementUnlocked(
    playerId, questId, achievementId, name, rewardCurrency, rewardAmount
);

// Record shop purchase
EnduranceTelemetryService.INSTANCE.recordShopPurchase(
    playerId, itemId, currency, price, purchaseCount
);
```

#### Party System

```java
// Record party created
EnduranceTelemetryService.INSTANCE.recordPartyCreated(
    partyId, leaderId, leaderName, questType
);

// Record party join
EnduranceTelemetryService.INSTANCE.recordPartyJoin(
    partyId, playerId, playerName, memberCount
);

// Record party leave
EnduranceTelemetryService.INSTANCE.recordPartyLeave(
    partyId, playerId, reason, remainingMembers
);

// Record party disbanded
EnduranceTelemetryService.INSTANCE.recordPartyDisbanded(
    partyId, memberCount, reason
);

// Record invite sent
EnduranceTelemetryService.INSTANCE.recordInviteSent(
    partyId, senderId, targetId
);

// Record invite response
EnduranceTelemetryService.INSTANCE.recordInviteResponse(
    partyId, playerId, accepted
);
```

#### Boss Wave System

```java
// Record boss wave start
EnduranceTelemetryService.INSTANCE.recordBossWaveStart(
    questId, waveNumber, archetype, bossHealth, playerCount
);

// Record boss ability used
EnduranceTelemetryService.INSTANCE.recordBossAbility(
    arenaId, archetype, abilityName, playersHit, damage
);

// Record boss defeated
EnduranceTelemetryService.INSTANCE.recordBossDefeated(
    arenaId, waveNumber, archetype, fightDurationMs, bonusPoints, totalDamageTaken
);
```

#### Quest Lifecycle

```java
// Record quest start
EnduranceTelemetryService.INSTANCE.recordQuestStart(
    questId, playerId, mobId, tier, questType, partySize
);

// Record quest end
EnduranceTelemetryService.INSTANCE.recordQuestEnd(
    questId, outcome, finalWave, durationMs, totalKills, totalPoints
);
```

#### Gamification

```java
// Record badge unlocked
EnduranceTelemetryService.INSTANCE.recordBadgeUnlocked(
    playerId, badgeId, badgeName, bonusPoints
);

// Record leaderboard change
EnduranceTelemetryService.INSTANCE.recordLeaderboardChange(
    playerId, leaderboardName, oldRank, newRank, points
);
```

---

### 2.15 PlayerAttributeTelemetryService (NEW)

**Location:** `com.devmod.telemetry.player.PlayerAttributeTelemetryService`

Comprehensive player attribute tracking with periodic snapshots and event-based recording. Integrates with PerkSystem, MutatorSystem, ComboSystem, StaminaSystem, DashAbilitySystem, DodgeAbilitySystem, and Pehkui.

#### Snapshot Tracking

```java
// Configure snapshot interval (default: 100 ticks = 5 seconds)
PlayerAttributeTelemetryService.INSTANCE.setSnapshotInterval(100);

// Tick handler (call every server tick per player)
PlayerAttributeTelemetryService.INSTANCE.tick(player);

// Manual snapshot with trigger
PlayerAttributeTelemetryService.INSTANCE.recordSnapshot(player, "damage");
PlayerAttributeTelemetryService.INSTANCE.recordSnapshot(player, "heal");
PlayerAttributeTelemetryService.INSTANCE.recordSnapshot(player, "perk_acquired");
```

#### Event-Based Tracking

```java
// Record attribute change
PlayerAttributeTelemetryService.INSTANCE.recordAttributeChange(player, "attack_damage", 5.0, 7.0);

// Record health change
PlayerAttributeTelemetryService.INSTANCE.recordHealthChange(player, 20.0f, 15.0f, "skeleton_arrow");

// Record food/hunger change
PlayerAttributeTelemetryService.INSTANCE.recordFoodChange(player, 20, 18, -1.0f);

// Cleanup on logout
PlayerAttributeTelemetryService.INSTANCE.cleanupPlayer(playerId);
```

#### Tracked Attributes

| Category | Attributes |
|----------|------------|
| **Health** | hp, maxHp, hearts, absorption |
| **Food** | hunger, saturation, exhaustion |
| **Movement** | speed, velocityX/Y/Z, isSprinting, isSneaking, isSwimming, isFalling |
| **Melee** | attackDamage, attackSpeed, damageMultiplier, reductionMultiplier |
| **Magic** | damageMultiplier, reductionMultiplier |
| **Ranged** | damageMultiplier, reductionMultiplier |
| **Defense** | armor, armorToughness, knockbackResistance, damageReduction |
| **Physical** | reach, hitboxWidth, hitboxHeight, pehkuiScale, pehkuiHitboxScale |
| **Abilities** | stamina, maxStamina, dashAvailable, dashCooldown, dodgeAvailable, dodgeCooldown |
| **Modifiers** | hungerMultiplier, viewChunksBonus, resourceGatherSpeed, critChance, critDamageMultiplier, lifestealPercent, styleMultiplier |
| **Combat State** | currentCombo, styleRank, styleScore |

#### Data Structures

```java
// PlayerAttributeSnapshot (written to player_attributes.ndjson)
class PlayerAttributeSnapshot {
    // Health
    float healthHp, maxHealthHp, absorptionHp;
    int healthHearts;

    // Food
    int hungerLevel;
    float saturation, exhaustion;

    // Movement
    double movementSpeed, velocityX, velocityY, velocityZ;
    boolean isSprinting, isSneaking, isSwimming, isFalling;

    // Combat
    double meleeAttackDamage, meleeAttackSpeed;
    float meleeDamageMultiplier, meleeReduction;
    float magicDamageMultiplier, magicReduction;
    float rangedDamageMultiplier, rangedReduction;

    // Defense
    double armorValue, armorToughness, knockbackResistance;
    float totalDamageReduction;

    // Physical
    double reach;
    float hitboxWidth, hitboxHeight;
    Float pehkuiScale, pehkuiHitboxScale;

    // Abilities
    float stamina, maxStamina, dashCooldown, dodgeCooldown;
    boolean dashAvailable, dodgeAvailable;

    // Modifiers & Combat State
    float critChance, critDamageMultiplier, lifestealPercent, styleMultiplier;
    int currentCombo, styleScore;
    String styleRank;
}
```

---

### 2.16 AbilityTelemetryService (NEW)

**Location:** `com.devmod.telemetry.player.AbilityTelemetryService`

Tracks ability usage for Dash, Dodge, and Stamina systems with session-based aggregation.

#### Dash Tracking

```java
// Record dash attempt (success or failure)
AbilityTelemetryService.INSTANCE.recordDashAttempt(
    playerId,       // UUID
    success,        // boolean
    staminaBefore,  // float
    staminaAfter    // float
);
```

#### Dodge Tracking

```java
// Record dodge attempt
AbilityTelemetryService.INSTANCE.recordDodgeAttempt(
    playerId,       // UUID
    success,        // boolean
    direction,      // DodgeDirection (LEFT, RIGHT, BACK, FORWARD)
    staminaBefore,  // float
    staminaAfter    // float
);

// Record perfect dodge (damage negated by i-frames)
AbilityTelemetryService.INSTANCE.recordPerfectDodge(
    playerId,       // UUID
    damageNegated,  // float
    damageSource    // String
);
```

#### Stamina Tracking

```java
// Record stamina exhaustion
AbilityTelemetryService.INSTANCE.recordExhaustion(playerId, "dodge_spam");

// Record full stamina regeneration
AbilityTelemetryService.INSTANCE.recordStaminaFull(playerId, regenTimeMs);
```

#### Session Management

```java
// Export session summary (called on logout)
AbilityTelemetryService.INSTANCE.exportSessionSummary(playerId);

// Cleanup (exports summary then removes data)
AbilityTelemetryService.INSTANCE.cleanupPlayer(playerId);

// Get session stats for analytics
AbilitySessionStats stats = AbilityTelemetryService.INSTANCE.getSessionStats(playerId);
```

#### Data Structures

```java
// AbilitySessionStats
class AbilitySessionStats {
    // Dash stats
    int dashAttempts;
    int dashSuccesses;
    float totalStaminaUsedDash;

    // Dodge stats
    int dodgeAttempts;
    int dodgeSuccesses;
    float totalStaminaUsedDodge;
    int perfectDodges;
    float totalDamageNegated;

    // Dodge direction distribution
    int dodgeDirectionLeft;
    int dodgeDirectionRight;
    int dodgeDirectionBack;
    int dodgeDirectionForward;

    // Stamina stats
    int exhaustionCount;
    int fullRegenCount;
    long totalRegenTimeMs;
}
```

#### Event Types Written

| Event Type | Fields |
|------------|--------|
| `dash` | success, staminaBefore, staminaAfter, staminaCost, totalDashes, successRate |
| `dodge` | success, direction, staminaBefore, staminaAfter, staminaCost, totalDodges, successRate |
| `perfect_dodge` | damageNegated, source, totalPerfectDodges, totalDamageNegated |
| `exhaustion` | context, totalExhaustions |
| `stamina_full` | regenTimeMs, avgRegenTimeMs |
| `session_summary` | all aggregated stats including dodge direction distribution |

---

## 3. Metrics Catalog

### Combat Metrics (15)

| # | Metric | Service | Method |
|---|--------|---------|--------|
| 1 | Damage dealt | TelemetryService | `logHit()` |
| 2 | Damage type | TelemetryService | `logHit()` |
| 3 | Body part hit | TelemetryService | `logHit()` |
| 4 | Armor penetration | TelemetryService | `logHit()` |
| 5 | Time To Kill (TTK) | FightSessionService | `registerTTK()` |
| 6 | Hit accuracy | DamageTrackingService | `registerWeaponHit/Miss()` |
| 7 | Burst damage | FightSessionService | `registerBurstDamage()` |
| 8 | Weapon stats | DamageTrackingService | `registerWeaponHit()` |
| 9 | Kill count | FightSessionService | `registerHit()` |
| 10 | Critical hits | TelemetryService | `logHit()` (multiplier > 1.5) |
| 11 | Miss detection | TelemetryService | `logMiss()` |
| 12 | HP after hit | FightSessionService | `registerHpAfterHit()` |
| 13 | Entity state | TelemetryService | `logHit()` |
| 14 | Distance to target | TelemetryService | `logHit()` |
| 15 | Fight duration | FightSessionService | `tick()` |

### Death & Spawn Metrics (4)

| # | Metric | Service | Method |
|---|--------|---------|--------|
| 16 | Entity death | TelemetryService | `logDeath()` |
| 17 | Spawn success/fail | TelemetryService | `logSpawn()` |
| 18 | Spawn location | TelemetryService | `logSpawn()` |
| 19 | Death heatmap | HeatmapService | `recordDeath()` |

### Healing Metrics (1)

| # | Metric | Service | Method |
|---|--------|---------|--------|
| 20 | Healing amount | TelemetryService | `logHeal()` |

### Room Metrics (3)

| # | Metric | Service | Method |
|---|--------|---------|--------|
| 21 | Room transitions | PlayerTrackingService | `trackPlayerRoom()` |
| 22 | Room time | PlayerTrackingService | `trackPlayerRoom()` |
| 23 | Room damage | DamageTrackingService | `registerRoomDamage()` |

### Boss Metrics (2)

| # | Metric | Service | Method |
|---|--------|---------|--------|
| 24 | Phase transitions | BossPhaseService | `startPhase()` |
| 25 | Phase duration | BossPhaseService | `endPhase()` |

### Skill Metrics (3)

| # | Metric | Service | Method |
|---|--------|---------|--------|
| 26 | Skill casts | SkillTrackingService | `recordCast()` |
| 27 | Skill hit rate | SkillTrackingService | `recordHit()` |
| 28 | Skill whiffs | SkillTrackingService | `tick()` |

### Entity Tracking Metrics (5)

| # | Metric | Service | Method |
|---|--------|---------|--------|
| 29 | Stuck detection | EntityTrackingService | `checkStuck()` |
| 30 | Aggro drop | EntityTrackingService | `checkAggroDrop()` |
| 31 | Camping | EntityTrackingService | `checkCamping()` |
| 32 | Kiting path | EntityTrackingService | `updatePathAndDetectIssue()` |
| 33 | Spin detection | EntityTrackingService | `updatePathAndDetectIssue()` |

### Movement & Position Metrics (5)

| # | Metric | Service | Method |
|---|--------|---------|--------|
| 34 | Movement sampling | PlayerTrackingService | `trackPlayerRoom()` |
| 35 | Out of bounds | PlayerTrackingService | `checkOutOfBounds()` |
| 36 | Backtracking | PlayerTrackingService | `trackRoomVisit()` |
| 37 | Idle time | PlayerTrackingService | `updateIdleTracking()` |
| 38 | Confusion score | PlayerTrackingService | `updateIdleTracking()` |

### Minion Metrics (3)

| # | Metric | Service | Method |
|---|--------|---------|--------|
| 39 | Minion spawns | MinionService | `recordSpawn()` |
| 40 | Minion deaths | MinionService | `recordDeath()` |
| 41 | Peak concurrent | MinionService | `getPeakConcurrent()` |

### Dungeon Metrics (5)

| # | Metric | Service | Method |
|---|--------|---------|--------|
| 42 | Session start/end | DungeonSessionService | `startSession()` / `endSession()` |
| 43 | Outcome | DungeonSessionService | `endSession()` |
| 44 | Room sequence | DungeonSessionService | `enterRoom()` |
| 45 | Session duration | DungeonSessionService | `endSession()` |
| 46 | Deaths/kills | DungeonSessionService | `recordDeath()` / `recordKill()` |

### Spatial Metrics (4)

| # | Metric | Service | Method |
|---|--------|---------|--------|
| 47 | Entity density | SpatialMetricsService | `updateEntityDensity()` |
| 48 | Choke points | SpatialMetricsService | `recordQuit()` |
| 49 | Invisible collisions | SpatialMetricsService | `recordInvisibleCollision()` |
| 50 | Parkour falls | SpatialMetricsService | `recordParkourFall()` |

### Performance Metrics (3)

| # | Metric | Service | Method |
|---|--------|---------|--------|
| 51 | Server MSPT | TelemetryService | `tickPerformance()` |
| 52 | TPS | TelemetryService | `tickPerformance()` |
| 53 | Tick sampling | TelemetryService | `tickPerformance()` |

### Economy Metrics (8)

| # | Metric | Service | Method |
|---|--------|---------|--------|
| 54 | Chest opens | EconomyMetricsService | `recordChestOpen()` |
| 55 | Item acquisition | EconomyMetricsService | `recordItemPickup()` |
| 56 | Drop rates | EconomyMetricsService | `recordMobDrop()` |
| 57 | Loot fairness | EconomyMetricsService | `getSessionStats()` |
| 58 | Resource scarcity | EconomyMetricsService | `calculateScarcityIndex()` |
| 59 | Item usage | EconomyMetricsService | `recordItemUsed()` |
| 60 | Item discard | EconomyMetricsService | `recordItemDiscarded()` |
| 61 | Mob kills | EconomyMetricsService | `recordMobKill()` |

### Progression Metrics (17) - NEW

| # | Metric | Service | Method |
|---|--------|---------|--------|
| 62 | Block break | PlayerProgressionService | `recordBlockBreak()` |
| 63 | Block place | PlayerProgressionService | `recordBlockPlace()` |
| 64 | XP pickup | PlayerProgressionService | `recordXpPickup()` |
| 65 | Level change | PlayerProgressionService | `recordLevelChange()` |
| 66 | Advancement earned | PlayerProgressionService | `recordAdvancement()` |
| 67 | Dimension change | PlayerProgressionService | `recordDimensionChange()` |
| 68 | Critical hit | PlayerProgressionService | `recordCriticalHit()` |
| 69 | Attack event | PlayerProgressionService | `recordAttack()` |
| 70 | Villager trade | PlayerProgressionService | `recordTrade()` |
| 71 | Fishing catch | PlayerProgressionService | `recordFishing()` |
| 72 | Blocks broken (per type) | PlayerProgressionService | `getPlayerBlockStats()` |
| 73 | Blocks placed (per type) | PlayerProgressionService | `getPlayerBlockStats()` |
| 74 | Max level reached | PlayerProgressionService | `getPlayerXpStats()` |
| 75 | Total XP gained | PlayerProgressionService | `getPlayerXpStats()` |
| 76 | Dimensions visited | PlayerProgressionService | `recordDimensionChange()` |
| 77 | Crit damage total | PlayerProgressionService | `getPlayerCombatStats()` |
| 78 | Sweep attacks | PlayerProgressionService | `getPlayerCombatStats()` |

### Endurance Quest Metrics (32) - NEW

| # | Metric | Service | Method |
|---|--------|---------|--------|
| 79 | Wave start | EnduranceTelemetryService | `recordWaveStart()` |
| 80 | Wave complete | EnduranceTelemetryService | `recordWaveComplete()` |
| 81 | Wave kill | EnduranceTelemetryService | `recordWaveKill()` |
| 82 | Style rank change | EnduranceTelemetryService | `recordStyleRankChange()` |
| 83 | Combo milestone | EnduranceTelemetryService | `recordComboMilestone()` |
| 84 | Combo break | EnduranceTelemetryService | `recordComboBreak()` |
| 85 | Special action | EnduranceTelemetryService | `recordSpecialAction()` |
| 86 | Perk selected | EnduranceTelemetryService | `recordPerkSelected()` |
| 87 | Perk choices offered | EnduranceTelemetryService | `recordPerkChoicesOffered()` |
| 88 | Mutators assigned | EnduranceTelemetryService | `recordMutatorsAssigned()` |
| 89 | Mutator added | EnduranceTelemetryService | `recordMutatorAdded()` |
| 90 | Currency earned | EnduranceTelemetryService | `recordCurrencyEarned()` |
| 91 | Loot drop | EnduranceTelemetryService | `recordLootDrop()` |
| 92 | Achievement unlocked | EnduranceTelemetryService | `recordAchievementUnlocked()` |
| 93 | Shop purchase | EnduranceTelemetryService | `recordShopPurchase()` |
| 94 | Party created | EnduranceTelemetryService | `recordPartyCreated()` |
| 95 | Party join | EnduranceTelemetryService | `recordPartyJoin()` |
| 96 | Party leave | EnduranceTelemetryService | `recordPartyLeave()` |
| 97 | Party disbanded | EnduranceTelemetryService | `recordPartyDisbanded()` |
| 98 | Invite sent | EnduranceTelemetryService | `recordInviteSent()` |
| 99 | Invite response | EnduranceTelemetryService | `recordInviteResponse()` |
| 100 | Boss wave start | EnduranceTelemetryService | `recordBossWaveStart()` |
| 101 | Boss ability | EnduranceTelemetryService | `recordBossAbility()` |
| 102 | Boss defeated | EnduranceTelemetryService | `recordBossDefeated()` |
| 103 | Quest start | EnduranceTelemetryService | `recordQuestStart()` |
| 104 | Quest end | EnduranceTelemetryService | `recordQuestEnd()` |
| 105 | Badge unlocked | EnduranceTelemetryService | `recordBadgeUnlocked()` |
| 106 | Leaderboard change | EnduranceTelemetryService | `recordLeaderboardChange()` |
| 107 | Challenge completed | EnduranceTelemetryService | `recordChallengeCompleted()` |
| 108 | Streak updated | EnduranceTelemetryService | `recordStreakUpdated()` |
| 109 | Profile rank change | EnduranceTelemetryService | `recordProfileRankChange()` |
| 110 | Daily/Weekly reset | EnduranceTelemetryService | `recordReset()` |

### Player Attribute Metrics (30) - NEW

| # | Metric | Service | Method |
|---|--------|---------|--------|
| 111 | Health HP | PlayerAttributeTelemetryService | `recordSnapshot()` |
| 112 | Health hearts | PlayerAttributeTelemetryService | `recordSnapshot()` |
| 113 | Max HP | PlayerAttributeTelemetryService | `recordSnapshot()` |
| 114 | Absorption HP | PlayerAttributeTelemetryService | `recordSnapshot()` |
| 115 | Hunger level | PlayerAttributeTelemetryService | `recordSnapshot()` |
| 116 | Saturation | PlayerAttributeTelemetryService | `recordSnapshot()` |
| 117 | Exhaustion | PlayerAttributeTelemetryService | `recordSnapshot()` |
| 118 | Movement speed | PlayerAttributeTelemetryService | `recordSnapshot()` |
| 119 | Velocity (X/Y/Z) | PlayerAttributeTelemetryService | `recordSnapshot()` |
| 120 | Sprint/Sneak/Swim state | PlayerAttributeTelemetryService | `recordSnapshot()` |
| 121 | Melee attack damage | PlayerAttributeTelemetryService | `recordSnapshot()` |
| 122 | Melee attack speed | PlayerAttributeTelemetryService | `recordSnapshot()` |
| 123 | Melee damage multiplier | PlayerAttributeTelemetryService | `recordSnapshot()` |
| 124 | Magic damage multiplier | PlayerAttributeTelemetryService | `recordSnapshot()` |
| 125 | Ranged damage multiplier | PlayerAttributeTelemetryService | `recordSnapshot()` |
| 126 | Armor value | PlayerAttributeTelemetryService | `recordSnapshot()` |
| 127 | Armor toughness | PlayerAttributeTelemetryService | `recordSnapshot()` |
| 128 | Knockback resistance | PlayerAttributeTelemetryService | `recordSnapshot()` |
| 129 | Total damage reduction | PlayerAttributeTelemetryService | `recordSnapshot()` |
| 130 | Reach | PlayerAttributeTelemetryService | `recordSnapshot()` |
| 131 | Hitbox width/height | PlayerAttributeTelemetryService | `recordSnapshot()` |
| 132 | Pehkui scale | PlayerAttributeTelemetryService | `recordSnapshot()` |
| 133 | Stamina current | PlayerAttributeTelemetryService | `recordSnapshot()` |
| 134 | Stamina max | PlayerAttributeTelemetryService | `recordSnapshot()` |
| 135 | Dash available | PlayerAttributeTelemetryService | `recordSnapshot()` |
| 136 | Dash cooldown | PlayerAttributeTelemetryService | `recordSnapshot()` |
| 137 | Dodge available | PlayerAttributeTelemetryService | `recordSnapshot()` |
| 138 | Dodge cooldown | PlayerAttributeTelemetryService | `recordSnapshot()` |
| 139 | Health change event | PlayerAttributeTelemetryService | `recordHealthChange()` |
| 140 | Food change event | PlayerAttributeTelemetryService | `recordFoodChange()` |

### Ability Usage Metrics (8) - NEW

| # | Metric | Service | Method |
|---|--------|---------|--------|
| 141 | Dash attempt | AbilityTelemetryService | `recordDashAttempt()` |
| 142 | Dash success rate | AbilityTelemetryService | `recordDashAttempt()` |
| 143 | Dodge attempt | AbilityTelemetryService | `recordDodgeAttempt()` |
| 144 | Dodge direction | AbilityTelemetryService | `recordDodgeAttempt()` |
| 145 | Perfect dodge | AbilityTelemetryService | `recordPerfectDodge()` |
| 146 | Damage negated | AbilityTelemetryService | `recordPerfectDodge()` |
| 147 | Stamina exhaustion | AbilityTelemetryService | `recordExhaustion()` |
| 148 | Stamina regen time | AbilityTelemetryService | `recordStaminaFull()` |

---

## 4. NDJSON Files

All telemetry is written to `run/telemetry/` as newline-delimited JSON files.

### Combat Files

| File | Content | Written By |
|------|---------|------------|
| `hits.ndjson` | All damage events | TelemetryService.logHit() |
| `deaths.ndjson` | Entity deaths | TelemetryService.logDeath() |
| `heals.ndjson` | Healing events | TelemetryService.logHeal() |
| `fights.ndjson` | Fight session summaries | FightSessionService |
| `phases.ndjson` | Boss phase transitions | BossPhaseService |

### Entity Files

| File | Content | Written By |
|------|---------|------------|
| `spawns.ndjson` | Entity spawns | TelemetryService.logSpawn() |
| `minions.ndjson` | Minion death stats | TelemetryService.logDeath() |
| `alerts.ndjson` | Stuck, camping, aggro, OOB, whiffs | Various services |

### Player Files

| File | Content | Written By |
|------|---------|------------|
| `room_time.ndjson` | Room transitions | PlayerTrackingService |
| `player_quits.ndjson` | Quit positions | SpatialMetricsService |
| `backtracks.ndjson` | Backtrack events | TelemetryService |

### Dungeon Files

| File | Content | Written By |
|------|---------|------------|
| `dungeon_sessions.ndjson` | Dungeon completions | DungeonSessionService |
| `dungeon_runs.ndjson` | Dungeon progression | TelemetryService |

### Economy Files

| File | Content | Written By |
|------|---------|------------|
| `economy.ndjson` | chest_open, item_pickup, item_used, item_discard, mob_kill, mob_drop | EconomyMetricsService |

### Progression Files

| File | Content | Written By |
|------|---------|------------|
| `progression.ndjson` | block_break, block_place, xp_pickup, level_change, advancement, dimension_change, critical_hit, attack, trade, fishing | PlayerProgressionService |

### Player Attribute Files (NEW)

| File | Content | Written By |
|------|---------|------------|
| `player_attributes.ndjson` | periodic snapshots (health, food, movement, combat, defense, physical, abilities, modifiers, combat_state), attribute_change, health_change, food_change | PlayerAttributeTelemetryService |

### Ability Usage Files (NEW)

| File | Content | Written By |
|------|---------|------------|
| `ability_usage.ndjson` | dash, dodge, perfect_dodge, exhaustion, stamina_full, session_summary | AbilityTelemetryService |

### Endurance Quest Files

| File | Content | Written By |
|------|---------|------------|
| `endurance.ndjson` | wave_start, wave_complete, wave_kill, style_rank_change, combo_milestone, combo_break, special_action, perk_selected, perk_choices_offered, mutators_assigned, mutator_added, currency_earned, loot_drop, achievement_unlocked, shop_purchase, party_created, party_join, party_leave, party_disbanded, invite_sent, invite_response, boss_wave_start, boss_ability, boss_defeated, quest_start, quest_end, badge_unlocked, leaderboard_change | EnduranceTelemetryService |

### Heatmap Files (9 types)

| File | Content |
|------|---------|
| `death_heatmap.ndjson` | Death positions |
| `movement_heatmap.ndjson` | Movement samples |
| `camping_heatmap.ndjson` | Camping positions |
| `stuck_heatmap.ndjson` | Stuck positions |
| `aggro_drop_heatmap.ndjson` | Aggro drop positions |
| `kiting_heatmap.ndjson` | Kiting paths |
| `choke_point_heatmap.ndjson` | Quit positions |
| `invisible_collision_heatmap.ndjson` | Collision positions |
| `parkour_fall_heatmap.ndjson` | Fall positions |

### Performance Files

| File | Content | Written By |
|------|---------|------------|
| `performance.ndjson` | MSPT/TPS samples | TelemetryService |
| `damage_stats.ndjson` | Exported aggregates | TelemetryService |

---

## 5. Integration Guide

### 5.1 Combat System Integration

```java
// In your damage handler
public void onEntityDamage(LivingEntity attacker, LivingEntity target, float damage) {
    String room = TelemetryService.INSTANCE.resolveRoom(target.blockPosition());

    // Log the hit
    TelemetryService.INSTANCE.logHit(
        room,
        attacker,
        target,
        damage,
        "melee",           // or "ranged", "environmental"
        BodyPart.BODY,     // from hit detection
        0.0f,              // armor penetration
        attacker.distanceTo(target),
        null,              // HazardType if environmental
        null               // EntityState
    );

    // Register for weapon stats
    String weaponId = getWeaponId(attacker);
    boolean isKill = target.getHealth() - damage <= 0;
    DamageTrackingService.INSTANCE.registerWeaponHit(weaponId, damage, isKill);

    // Register for room stats
    boolean isToPlayer = target instanceof Player;
    DamageTrackingService.INSTANCE.registerRoomDamage(room, isToPlayer, damage, isKill);

    // Register for fight tracking
    FightSessionService.INSTANCE.registerHit(room, target.level().dimension().toString(),
        attacker.getName().getString(), isKill && !isToPlayer, isKill && isToPlayer,
        target.getType().toString(), target.getName().getString());

    // Register burst damage
    FightSessionService.INSTANCE.registerBurstDamage(room, damage);
}

// On entity death
public void onEntityDeath(LivingEntity entity, DamageSource source) {
    String room = TelemetryService.INSTANCE.resolveRoom(entity.blockPosition());
    TelemetryService.INSTANCE.logDeath(room, entity, source.getMsgId());
}
```

### 5.2 Economy System Integration

```java
// When chest is opened
public void onChestOpen(BlockPos chestPos, Player player, List<ItemStack> contents) {
    String chestId = chestPos.toShortString();
    Map<String, Integer> items = new HashMap<>();
    for (ItemStack stack : contents) {
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        items.merge(itemId, stack.getCount(), Integer::sum);
    }
    EconomyMetricsService.INSTANCE.recordChestOpen(chestId, player.getName().getString(), items);
}

// When item is picked up
public void onItemPickup(Player player, ItemStack stack) {
    String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    EconomyMetricsService.INSTANCE.recordItemPickup(itemId, player.getName().getString(), stack.getCount());
}

// When mob is killed
public void onMobKilled(Mob mob, Player killer, List<ItemStack> drops) {
    String mobType = EntityType.getKey(mob.getType()).toString();
    boolean hadLoot = !drops.isEmpty();

    EconomyMetricsService.INSTANCE.recordMobKill(mobType, hadLoot);

    for (ItemStack drop : drops) {
        String itemId = BuiltInRegistries.ITEM.getKey(drop.getItem()).toString();
        EconomyMetricsService.INSTANCE.recordMobDrop(mobType, itemId, drop.getCount());
    }
}
```

### 5.3 Dungeon System Integration

```java
// When player enters dungeon
public void onDungeonEnter(Player player, String dungeonId) {
    TelemetryService.INSTANCE.startDungeonSession(player.getUUID(), dungeonId);
}

// When player enters a room within dungeon
public void onRoomEnter(Player player, String roomId) {
    DungeonSessionService.INSTANCE.enterRoom(player.getUUID(), roomId);
}

// When dungeon is completed/abandoned
public void onDungeonExit(Player player, String outcome) {
    // outcome: "completed", "abandoned", "wipe"
    TelemetryService.INSTANCE.endDungeonSession(player.getUUID(), outcome);
}

// Combat within dungeon
public void onDungeonDeath(Player player) {
    DungeonSessionService.INSTANCE.recordDeath(player.getUUID());
}

public void onDungeonKill(Player player) {
    DungeonSessionService.INSTANCE.recordKill(player.getUUID());
}
```

### 5.4 Skill System Integration

```java
// When skill is cast
public void onSkillCast(LivingEntity caster, String skillId) {
    String room = TelemetryService.INSTANCE.resolveRoom(caster.blockPosition());
    TelemetryService.INSTANCE.logSkillCast(caster, skillId, room);
}

// When skill hits target
public void onSkillHit(LivingEntity caster, String skillId, float damage) {
    TelemetryService.INSTANCE.logSkillHit(caster, skillId);
    SkillTrackingService.INSTANCE.recordDamage(skillId, damage);
}
```

### 5.5 Tick Integration

```java
// In your server tick handler
public void onServerTick(MinecraftServer server) {
    // Every tick
    TelemetryService.INSTANCE.tickFights();
    TelemetryService.INSTANCE.tickAggro();
    TelemetryService.INSTANCE.tickSkills();

    // Every 20 ticks (1 second)
    if (server.getTickCount() % 20 == 0) {
        TelemetryService.INSTANCE.tickPerformance();
    }
}
```

---

## 6. Integration Checklist

Use this checklist to ensure your systems are properly integrated:

### Combat System
- [ ] `logHit()` called on every damage event
- [ ] `logMiss()` called on projectile misses
- [ ] `logDeath()` called on every entity death
- [ ] `logHeal()` called on healing events
- [ ] `registerWeaponHit/Miss()` called for weapon stats
- [ ] `registerRoomDamage()` called for room stats
- [ ] `registerBurstDamage()` called for fight tracking
- [ ] `registerTTK()` called on kills

### Economy System
- [ ] `recordChestOpen()` called on chest interactions
- [ ] `recordItemPickup()` called on item collection
- [ ] `recordMobDrop()` called on mob loot drops
- [ ] `recordMobKill()` called on mob deaths
- [ ] `recordItemUsed()` called on consumable use
- [ ] `recordItemDiscarded()` called on item discard

### Dungeon System
- [ ] `startDungeonSession()` called on entry
- [ ] `endDungeonSession()` called on exit
- [ ] `enterRoom()` called on room transitions
- [ ] `recordDeath/Kill()` called for combat events
- [ ] `recordDamageDealt/Taken()` called for damage

### Skill System
- [ ] `logSkillCast()` called on cast attempt
- [ ] `logSkillHit()` called on successful hit
- [ ] `recordDamage()` called for skill damage

### Entity Tracking
- [ ] `checkStuck()` called periodically for mobs
- [ ] `checkAggroDrop()` called periodically
- [ ] `checkCamping()` called on player hits
- [ ] `registerSpawn()` called on mob spawn
- [ ] `updatePathAndDetectIssue()` called for pathing

### Boss System
- [ ] `logBossPhaseStart()` called on phase begin
- [ ] `logBossPhaseEnd()` called on phase end

### Performance
- [ ] `tickPerformance()` called every 20 ticks
- [ ] `tickFights()` called every tick
- [ ] `tickAggro()` called every tick
- [ ] `tickSkills()` called every tick

---

## 7. Best Practices

### DO:
- Call telemetry methods **after** the game logic completes
- Use `resolveRoom()` to get room IDs from positions
- Handle null rooms gracefully (use "unknown" or chunk coordinates)
- Call tick methods from a central location
- Export data periodically for analysis

### DON'T:
- Call telemetry in tight loops without throttling
- Block game logic waiting for telemetry
- Forget to call `tick*()` methods
- Ignore the async writer (it handles I/O efficiently)
- Create circular dependencies between services

### Performance Tips:
- Telemetry uses async I/O (~0.1ms overhead per event)
- Movement is sampled every 2s, not every tick
- Performance metrics sampled every 20 ticks
- Entity cleanup happens automatically on death
- Stale entries are cleaned every 30s

---

## Appendix: Service Locations

| Service | Package |
|---------|---------|
| TelemetryService | `com.devmod.telemetry` |
| DamageTrackingService | `com.devmod.telemetry.damage` |
| FightSessionService | `com.devmod.telemetry.combat` |
| SkillTrackingService | `com.devmod.telemetry.skills` |
| EntityTrackingService | `com.devmod.telemetry.entity` |
| MinionService | `com.devmod.telemetry.entity` |
| PlayerTrackingService | `com.devmod.telemetry.player` |
| BossPhaseService | `com.devmod.telemetry.boss` |
| DungeonSessionService | `com.devmod.telemetry.dungeon` |
| HeatmapService | `com.devmod.telemetry.spatial` |
| SpatialMetricsService | `com.devmod.telemetry.spatial` |
| EconomyMetricsService | `com.devmod.telemetry.economy` |
| RoomService | `com.devmod.telemetry.room` |
| PlayerProgressionService | `com.devmod.telemetry.progression` |
| EnduranceTelemetryService | `com.devmod.telemetry.endurance` |
| PlayerAttributeTelemetryService | `com.devmod.telemetry.player` |
| AbilityTelemetryService | `com.devmod.telemetry.player` |

---

## Appendix: Bit-Packed Fields Decoding

Per ottimizzare le performance, alcuni campi telemetrici usano **bit packing** per comprimere
più valori booleani in un singolo intero. Questo riduce la dimensione dei dati del ~80%.

### Movement Flags (PlayerAttributeTelemetryService)

Campo: `"flags"` nella sezione movement

| Bit | Flag | Significato |
|-----|------|-------------|
| 0 | SPRINTING | Player sta correndo |
| 1 | SNEAKING | Player è accovacciato |
| 2 | SWIMMING | Player sta nuotando |
| 3 | FALLING | Player sta cadendo |

**Decoding JavaScript:**
```javascript
const isSprinting = (flags & 0x01) !== 0;
const isSneaking = (flags & 0x02) !== 0;
const isSwimming = (flags & 0x04) !== 0;
const isFalling = (flags & 0x08) !== 0;
```

**Decoding Python:**
```python
is_sprinting = (flags & 0x01) != 0
is_sneaking = (flags & 0x02) != 0
is_swimming = (flags & 0x04) != 0
is_falling = (flags & 0x08) != 0
```

### Ability Flags (PlayerAttributeTelemetryService)

Campo: `"flags"` nella sezione abilities

| Bit | Flag | Significato |
|-----|------|-------------|
| 0 | DASH_AVAILABLE | Dash è disponibile |
| 1 | DODGE_AVAILABLE | Dodge è disponibile |
| 2 | STAMINA_FULL | Stamina al massimo |
| 3 | STAMINA_EMPTY | Stamina esaurita |
| 4 | ABILITY_COOLDOWN | (riservato) |
| 5 | IFRAME_ACTIVE | (riservato) |

**Decoding JavaScript:**
```javascript
const dashAvailable = (flags & 0x01) !== 0;
const dodgeAvailable = (flags & 0x02) !== 0;
const staminaFull = (flags & 0x04) !== 0;
const staminaEmpty = (flags & 0x08) !== 0;
```

### Dodge Result (AbilityTelemetryService)

Campo: `"result"` negli eventi dodge

Il campo combina direzione (2 bit) e successo (1 bit):
- Bits 0-1: Direzione (0=LEFT, 1=RIGHT, 2=BACK, 3=FORWARD)
- Bit 2: Success flag

**Decoding JavaScript:**
```javascript
const direction = result & 0x03;  // 0-3
const success = (result & 0x04) !== 0;

const DIRECTIONS = ['LEFT', 'RIGHT', 'BACK', 'FORWARD'];
console.log(`Dodge ${DIRECTIONS[direction]}: ${success ? 'SUCCESS' : 'FAIL'}`);
```

**Decoding Python:**
```python
direction = result & 0x03  # 0-3
success = (result & 0x04) != 0

DIRECTIONS = ['LEFT', 'RIGHT', 'BACK', 'FORWARD']
print(f"Dodge {DIRECTIONS[direction]}: {'SUCCESS' if success else 'FAIL'}")
```

### Jump Flags (JumpAnalysisService)

Campo: `"flags"` negli eventi jump

| Bit | Flag | Significato |
|-----|------|-------------|
| 0 | LANDED | Il salto è atterrato correttamente |
| 1 | FAILED_JUMP | Salto fallito (troppo corto) |
| 2 | WALL_COLLISION | Collisione con muro |
| 3 | CEILING_COLLISION | Collisione con soffitto |

**Decoding JavaScript:**
```javascript
const landed = (flags & 0x01) !== 0;
const failedJump = (flags & 0x02) !== 0;
const wallCollision = (flags & 0x04) !== 0;
const ceilingCollision = (flags & 0x08) !== 0;
```

**Decoding Python:**
```python
landed = (flags & 0x01) != 0
failed_jump = (flags & 0x02) != 0
wall_collision = (flags & 0x04) != 0
ceiling_collision = (flags & 0x08) != 0
```

### Utility Java per Decoding

La classe `BitPackedFlags` fornisce metodi per il decoding:

```java
import com.devmod.telemetry.util.BitPackedFlags;

// Controlla un singolo flag
boolean isSprinting = BitPackedFlags.isSet(movementFlags, BitPackedFlags.FLAG_SPRINTING);

// Estrai tutti i flag come array
boolean[] flags = BitPackedFlags.unpack(movementFlags, 4);
// flags[0] = sprinting, flags[1] = sneaking, etc.

// Per jump flags
boolean landed = BitPackedFlags.isSet(jumpFlags, 0);
boolean failedJump = BitPackedFlags.isSet(jumpFlags, 1);
boolean wallCollision = BitPackedFlags.isSet(jumpFlags, 2);
boolean ceilingCollision = BitPackedFlags.isSet(jumpFlags, 3);
```

---

*Documentation generated for DevMod Telemetry System v1.3*

---

## Arena Template Telemetry (v2.23)

Per eventi e metriche specifiche del sistema Arena Template (build_ms, rollback, residuals, templateId/templateVersion), fare riferimento a:
- `docs/arena-template-rework/TODO_ARENA_TEMPLATE.md`
- `docs/runbook/arena-alerts.md`
