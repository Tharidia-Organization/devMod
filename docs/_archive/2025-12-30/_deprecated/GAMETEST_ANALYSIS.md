# DevMod GameTests Analysis
## Comprehensive Testing Strategy for Core Components

**Report Date:** December 6, 2025  
**Total Java Classes:** 81  
**Total Lines of Code:** 17,188  
**Codebase:** Minecraft NeoForge 1.21 mod with combat telemetry system

---

## EXECUTIVE SUMMARY

The DevMod codebase is a sophisticated combat simulation and telemetry system for Minecraft. It has **three critical system components** that require comprehensive GameTest coverage:

1. **Core Damage Calculation System** (DamageHandler, HitHelper, HitContext)
2. **Configuration Management** (MobConfigManager, WeaponConfigManager)
3. **Network Communication & Serialization** (Payloads, NetworkHandler)

**Current Test Coverage:** Partial (312 lines of GameTests for ~17K LOC = ~1.8% coverage)

---

## I. CORE DAMAGE CALCULATION LOGIC

### A. DamageHandler (589 lines)

**Critical Functionality:**
- Damage event interception and modification
- Body part detection (raycast-based for melee, Y-coordinate for ranged)
- Damage multiplier application
- Armor penetration calculation
- Environmental damage tracking
- Enderman evasion detection

**Testing Requirements:**

#### 1.1 Melee Damage Calculation
```
Type: Unit Test
Priority: CRITICAL
Current Coverage: NO

Test Cases:
✗ Test 1: Basic damage multiplier application
  - Given: 10 base damage, head hit (2.0x mult)
  - Expected: 20 damage applied
  
✗ Test 2: Body part multiplier variations
  - Head: 1.5-2.5x
  - Body: 0.8-1.2x  
  - Arms: 0.6-0.8x
  - Legs: 0.5-0.7x
  
✗ Test 3: Armor penetration bonus
  - Given: 10 armor, 50% penetration
  - Expected: Damage increase = armor * penetration * 0.5
  
✗ Test 4: Base damage bonus stacking
  - Global stats + specific stats combination
  - Verify no double-application

Edge Cases:
- Zero health entities (should not crash)
- Negative damage (should be clamped)
- Armor > max armor value
- Penetration > 1.0 (overflow protection)
```

#### 1.2 Ranged Damage (Arrow) Detection
```
Type: Integration Test
Priority: HIGH
Current Coverage: NO

Test Cases:
✗ Test 1: Arrow Y-coordinate body part detection
  - Arrow at Y = target_feet + 0.25*height → LEGS
  - Arrow at Y = target_feet + 0.50*height → BODY
  - Arrow at Y = target_feet + 0.85*height → HEAD
  
✗ Test 2: Arrow velocity and direction capture
  - Verify arrow.getDeltaMovement() is normalized
  - Verify hitPoint is arrow center (not target center)
  
✗ Test 3: Arrow vs projectile discrimination
  - AbstractArrow subclasses only
  - Other projectiles should use different path

Edge Cases:
- Arrow hitting during teleport (Enderman)
- Arrow with zero velocity
- Arrow from non-arrow projectile launcher
```

#### 1.3 Enderman Evasion Detection
```
Type: Behavioral Test
Priority: MEDIUM
Current Coverage: PARTIAL (implemented but untested)

Test Cases:
✗ Test 1: Attack confirmation timing
  - onAttackEntity records position at T=0
  - onDamage called at T=150ms confirms hit
  - No call = evasion detected
  
✗ Test 2: Position capture accuracy
  - Saved position = target.position() + (0, height*0.5, 0)
  - Verify center-of-mass calculation
  
✗ Test 3: Evasion panel spawning
  - Verify only on client (FMLEnvironment.dist.isClient())
  - Verify 3D panel marker correct location

Timing Issues:
- 150ms sleep thread could race (multiple attacks)
- ConcurrentHashMap concurrent modification risk
- Check memory leak: are old attacks cleaned up?
```

#### 1.4 Environmental Damage
```
Type: Unit Test
Priority: MEDIUM
Current Coverage: NO

Test Cases:
✗ Test 1: Fire damage sources
  - DamageTypes.ON_FIRE → "Fire Damage"
  - DamageTypes.IN_FIRE → "In Fire"
  - DamageTypes.LAVA → "Lava Damage"
  
✗ Test 2: Fall damage detection
  - DamageTypes.FALL
  - Verify position = victim center, not feet
  
✗ Test 3: All 44 damage types
  - Drown, suffocation, lightning, etc.
  - Coverage matrix for all branches
  
✗ Test 4: Environmental damage multipliers
  - Environmental should NOT have body part mults
  - multiplier should be 1.0x always

Edge Cases:
- Unknown damage source (should return null gracefully)
- Null attacker with environmental (should not crash)
```

---

### B. HitHelper (440 lines)

**Critical Functionality:**
- Body part detection via AABB raycast
- Hitbox subdivision (head, arms, body, legs)
- Adaptive detection (horizontal bodies, tall entities)
- Performance caching (100ms TTL, thread-safe)
- Dynamic reach detection (Better Combat compatibility)

**Testing Requirements:**

#### 2.1 Standard Humanoid Body Part Detection
```
Type: Unit Test
Priority: CRITICAL
Current Coverage: NO

Test Cases:
✗ Test 1: AABB subdivision accuracy
  - HEAD: top 25% of hitbox
  - ARMS: 40% height, 30% width left/right
  - BODY: center 40% height, after arms removed
  - LEGS: bottom 35%
  
✗ Test 2: Raycast intersection priority
  - Head has highest priority
  - Should return HEAD if ray intersects head + legs
  
✗ Test 3: Fallback pitch-based detection
  - If no AABB hits, use attacker pitch
  - pitch < -15 → HEAD
  - pitch > 25 → LEGS
  - else → BODY

Precision Targets:
- 95% accuracy on standard zombie/player hitbox
- Metrics: hit rate for each body part at various angles
```

#### 2.2 Non-Humanoid Hitbox Adaptation
```
Type: Integration Test
Priority: HIGH
Current Coverage: PARTIAL (code exists, not tested)

Test Cases:
✗ Test 1: Horizontal body detection
  - Dragon/wither: width/depth > 2.0
  - Uses front/back/middle instead of head/body/legs
  - Front 30% = HEAD equivalent
  - Back 30% = LEGS equivalent
  
✗ Test 2: Tall body detection
  - Enderman/bosses: height > 3.0, aspect < 0.5
  - HEAD: top 15% (tighter)
  - BODY: next 35%
  - ARMS: next 30%
  - LEGS: bottom 20%
  
✗ Test 3: Entity type fallback
  - Humanoid ratio: 0.5 < w/h < 2.0
  - Should use standard detection

Edge Cases:
- Arbitrary hitbox dimensions (0.1x0.1x0.1)
- Floating point precision on aspect ratio
- Very large entities (>10 blocks)
```

#### 2.3 Caching Performance
```
Type: Performance Test
Priority: MEDIUM
Current Coverage: PARTIAL (code exists, not validated)

Test Cases:
✗ Test 1: Cache hit rate
  - Same attacker/target pair within 100ms
  - Expected: 80%+ hit rate
  - Measure: ms per calculation
  
✗ Test 2: Cache expiration
  - After 100ms, entry should expire
  - After 5s of inactivity, cleanup runs
  - Verify no stale entries
  
✗ Test 3: Cache eviction strategy
  - Max size: 1000 entries
  - When exceeded, remove oldest
  - Verify no ConcurrentModificationException

Memory Leaks:
- Position hash collision (is int hash sufficient?)
- WeakReference to entities (should not prevent GC)
- Cleanup frequency vs cache thrashing
```

#### 2.4 Dynamic Reach Detection
```
Type: Integration Test
Priority: MEDIUM
Current Coverage: NO

Test Cases:
✗ Test 1: Default reach (3.5 blocks)
  - Vanilla players/mobs
  
✗ Test 2: Better Combat extended reach
  - Read ENTITY_INTERACTION_RANGE attribute
  - Fallback to 3.5 if attribute unavailable
  
✗ Test 3: Custom reach attribute
  - Mod-added attributes
  - Verify reflection-based lookup is safe
  
✗ Test 4: Reach boundary conditions
  - Reach = 0 (should be clamped?)
  - Reach = 1000 (ultra-reach mod)
  - Negative reach (error handling)

Integration Points:
- ModIntegrationManager must be initialized
- Better Combat must be present (soft dependency)
```

---

### C. HitContext (68 lines)

**Critical Functionality:**
- Thread-safe context storage (body part data)
- Prevents double calculation in telemetry
- 100ms TTL with periodic cleanup
- ConcurrentHashMap synchronization

**Testing Requirements:**

#### 3.1 Context Storage & Retrieval
```
Type: Unit Test
Priority: MEDIUM
Current Coverage: NO

Test Cases:
✗ Test 1: Store and retrieve
  - Given: Entity, BodyPart.HEAD, isRanged=true
  - Call: HitContext.store(entity, part, isRanged)
  - Result: HitContext.retrieve(entity) returns same data
  
✗ Test 2: Auto-remove on retrieve
  - After retrieve(), second call returns null
  - Prevents stale data access

Edge Cases:
- Retrieve without prior store (should return null)
- Store same entity twice (overwrites)
```

#### 3.2 Expiration Handling
```
Type: Unit Test
Priority: MEDIUM
Current Coverage: NO

Test Cases:
✗ Test 1: 100ms TTL expiration
  - Store at T=0
  - Retrieve at T=99ms → returns data
  - Retrieve at T=101ms → returns null (expired)
  
✗ Test 2: Cleanup removes expired entries
  - Store 100 entities
  - Wait 101ms, call cleanup()
  - Map size should be 0

Thread Safety:
- Concurrent store() during cleanup() (synchronized)
- Multiple threads storing same entity UUID
```

#### 3.3 Thread Safety
```
Type: Concurrency Test
Priority: HIGH
Current Coverage: NO

Test Cases:
✗ Test 1: Concurrent store/retrieve
  - N threads store different entities
  - N threads retrieve concurrently
  - Verify no data loss, no crashes
  
✗ Test 2: Store during cleanup
  - cleanup() runs every ~100ms
  - Concurrent store() should not deadlock
  - Verify synchronized guards

Race Conditions:
- ConcurrentHashMap iteration during modification
- Double cleanup calls
```

---

## II. CONFIGURATION MANAGEMENT SYSTEMS

### A. WeaponConfigManager (64 lines)

**Critical Functionality:**
- Global weapon stats (by Item type)
- Item-specific stats (NBT CustomData)
- Fallback chain: specific → global → defaults
- Two-tier configuration (global + per-item)

**Testing Requirements:**

#### 4.1 Configuration Retrieval Fallback
```
Type: Unit Test
Priority: CRITICAL
Current Coverage: PARTIAL (basic tests exist)

Test Cases:
✓ Test 1: Default stats (no config)
  - getStats(ItemStack) on unconfigured item
  - Should return WeaponStats() with defaults
  
✗ Test 2: Global stats override
  - setGlobalStats(Items.IRON_SWORD, custom)
  - getStats(iron_sword) should return custom
  
✗ Test 3: Specific stats priority
  - Set both global + specific on same item
  - Specific should take priority
  
✗ Test 4: Multiple items independent
  - Config diamond_sword
  - Config iron_sword separately
  - Verify they don't cross-contaminate

Edge Cases:
- Empty ItemStack (ItemStack.EMPTY)
- Null ItemStack parameter (should not crash)
- NBT corruption (malformed CustomData)
```

#### 4.2 NBT Serialization
```
Type: Unit Test
Priority: MEDIUM
Current Coverage: PARTIAL (load/save in WeaponStats)

Test Cases:
✗ Test 1: setSpecificStats writes to NBT
  - ItemStack before: no CustomData.WeaponModStats
  - setSpecificStats(stack, stats)
  - ItemStack after: has CustomData.WeaponModStats
  
✗ Test 2: Retrieved stats match saved stats
  - Save: head=2.5, armor_pen=0.3
  - Load: verify exact match (float precision)
  
✗ Test 3: NBT persistence across serialization
  - Save to NBT CompoundTag
  - ItemStack → CompoundTag → ItemStack
  - Verify stats survive round-trip

Precision Issues:
- Float serialization precision (1e-6?)
- NBT compound nesting (CustomData.WeaponModStats.xxx)
```

#### 4.3 Global Stats Isolation
```
Type: Integration Test
Priority: MEDIUM
Current Coverage: NO

Test Cases:
✗ Test 1: clearAllGlobalStats() for GameTests
  - Save global stats
  - clearAllGlobalStats()
  - Verify retrieval returns defaults
  - Original stats not persisted to disk

Test Cleanup:
- Must not affect player-saved configs
- Memory-only clear for test isolation
```

---

### B. MobConfigManager (133 lines)

**Critical Functionality:**
- Global mob stats by EntityType
- Persistent JSON serialization
- EntityType → ResourceLocation mapping
- ConfigDir management (/config/devmod/mob_configs.json)

**Testing Requirements:**

#### 5.1 Mob Configuration CRUD
```
Type: Unit Test
Priority: CRITICAL
Current Coverage: NO

Test Cases:
✗ Test 1: setGlobalStats persistence
  - setGlobalStats(EntityTypes.ZOMBIE, range=32, dmg=5, ...)
  - getGlobalStats(EntityTypes.ZOMBIE) returns same values
  
✗ Test 2: Multiple mob types
  - Config ZOMBIE, SKELETON, CREEPER independently
  - Verify each returns correct values
  
✗ Test 3: hasConfig() check
  - Before: hasConfig(ZOMBIE) = false
  - After setGlobalStats: hasConfig(ZOMBIE) = true

Edge Cases:
- Negative stat values (should be clamped?)
- Zero health (should be rejected?)
- Null EntityType (should not crash)
```

#### 5.2 JSON Persistence
```
Type: Integration Test
Priority: HIGH
Current Coverage: PARTIAL (code implemented, not validated)

Test Cases:
✗ Test 1: Save to JSON
  - Set stats for 5 entity types
  - Call save()
  - Verify mob_configs.json created in config dir
  
✗ Test 2: Load from JSON
  - save() 3 zombie configs
  - New instance: load()
  - globalConfigs should have 3 entries
  
✗ Test 3: JSON format validity
  - Save & load cycle
  - JSON should be readable, pretty-printed
  
✗ Test 4: Auto-save on modification
  - setGlobalStats() calls save() automatically
  - File should be updated immediately

File System Issues:
- ConfigDir creation (createDirectories)
- IOException handling (device full, permissions)
- Race conditions (multiple mods saving)
```

#### 5.3 EntityType Registry Mapping
```
Type: Integration Test
Priority: MEDIUM
Current Coverage: PARTIAL (code exists, not validated)

Test Cases:
✗ Test 1: ResourceLocation → EntityType
  - Save: ZOMBIE → "minecraft:zombie"
  - Load: "minecraft:zombie" → EntityType.ZOMBIE
  
✗ Test 2: Registry lookup safety
  - Valid registry key: should find entity
  - Invalid registry key: should skip gracefully
  
✗ Test 3: Modded entity type support
  - Config entity from modded mod
  - ResourceLocation = "custommod:custom_boss"
  - Should serialize with namespace

Edge Cases:
- Entity type not in registry (ResourceLocation.tryParse() = null)
- BuiltInRegistries.ENTITY_TYPE unavailable (mod load order?)
- Null check on registry lookup
```

#### 5.4 Backwards Compatibility
```
Type: Integration Test
Priority: MEDIUM
Current Coverage: PARTIAL (code has fallback constructor)

Test Cases:
✗ Test 1: Old config without attackReach field
  - SavedStats(range, damage, health, armor)
  - Load old JSON missing "attackReach"
  - Should default to 2.0
  
✗ Test 2: JSON schema evolution
  - Old file: 4 fields
  - New code: 5 fields
  - Should not crash, use defaults

Migration Path:
- Verify fallback constructor is used
- Test loading actual old config files
```

---

## III. NETWORK PAYLOADS & SERIALIZATION

### A. UpdateMobStatsPayload (47 lines)

**Critical Functionality:**
- Transport mob stats changes: entity ID, health, damage, armor, reach
- StreamCodec serialization (manual, supports 7+ fields)
- Boolean flag: isGlobal (applies to all vs single)

**Testing Requirements:**

#### 6.1 Serialization Round-Trip
```
Type: Unit Test
Priority: CRITICAL
Current Coverage: NO

Test Cases:
✗ Test 1: Encode/Decode cycle
  - Create payload: isGlobal=true, entityId=123, damage=5.5
  - Encode to ByteBuf
  - Decode from ByteBuf
  - Verify all fields match (including boolean)
  
✗ Test 2: Field precision
  - damage=5.123456 (double)
  - After round-trip: should match exactly
  - BitSet comparison for floating point
  
✗ Test 3: Boundary values
  - entityId=0, Integer.MAX_VALUE
  - damage=0.0, Double.MAX_VALUE
  - armor=0.0, 100.0

Edge Cases:
- Negative damage (if allowed)
- NaN/Infinity values (should handle)
```

#### 6.2 Global vs Specific Distinction
```
Type: Unit Test
Priority: MEDIUM
Current Coverage: NO

Test Cases:
✗ Test 1: isGlobal flag correctness
  - isGlobal=true: affects all mobs of type
  - isGlobal=false: only affects entityId
  
✗ Test 2: Network routing
  - Global payload reaches all entity instances
  - Specific payload only updates one

Data Integrity:
- Boolean serialization uses BOOL codec
- Field order matches encode/decode
```

---

### B. UpdateWeaponPayload (55 lines)

**Critical Functionality:**
- Transport weapon multipliers: head, body, legs, pen, bonus
- String name field (nullable)
- isGlobal flag for global/specific stats

**Testing Requirements:**

#### 7.1 Float Precision in Network
```
Type: Unit Test
Priority: CRITICAL
Current Coverage: NO

Test Cases:
✗ Test 1: Float multiplier round-trip
  - head=2.5f, body=1.2f, legs=0.7f
  - Encode → Decode
  - Verify exact float equality
  
✗ Test 2: Armor penetration range
  - pen=0.0f (no penetration)
  - pen=0.5f (50%)
  - pen=1.0f (100% penetration)
  
✗ Test 3: Damage bonus precision
  - bonus=0.0f, 3.5f, 10.0f
  - All should preserve precision

Edge Cases:
- NaN/Infinity in floats
- Negative penetration (validation at server?)
```

#### 7.2 String Name Serialization
```
Type: Unit Test
Priority: MEDIUM
Current Coverage: NO

Test Cases:
✗ Test 1: name field nullable handling
  - name = null → should encode as empty string
  - Constructor normalizes: name == null ? "" : name
  
✗ Test 2: UTF8 string encoding
  - name = "Divine Sword" (special chars)
  - name = "Espada de Fuego" (accents)
  - Should handle UTF-8 properly
  
✗ Test 3: String length limits
  - Empty string: ""
  - Very long string: 1000+ chars
  - Should not overflow buffer

Edge Cases:
- Emoji in names (UTF-8 encoding)
- Null-byte injection (safety)
```

---

### C. EquipMobPayload (61 lines)

**Critical Functionality:**
- Transport equipment changes: 6 slots (mainHand, offHand, head, chest, legs, feet)
- All fields are nullable item names (String)
- Manual codec (7 fields exceed auto limit)

**Testing Requirements:**

#### 8.1 Equipment Slot Encoding
```
Type: Unit Test
Priority: CRITICAL
Current Coverage: NO

Test Cases:
✗ Test 1: All slots serialization
  - mainHand="iron_sword", offHand="shield", head="diamond_helmet"
  - chest="diamond_chestplate", legs="air", feet="golden_boots"
  - Round-trip should preserve all 6 slots
  
✗ Test 2: Null slot handling
  - Empty slots: "air" or ""
  - Should encode/decode consistently
  
✗ Test 3: Item registry names
  - "minecraft:diamond_sword"
  - "custommod:custom_item"
  - Verify namespace:itemname format

Validation:
- Item names must match registry (validated on server)
- "air" or empty = remove equipment
```

#### 8.2 Compact Encoding
```
Type: Performance Test
Priority: MEDIUM
Current Coverage: NO

Test Cases:
✗ Test 1: Buffer size estimation
  - 6 string fields + 1 entity ID
  - Measure ByteBuf size before/after encode
  - Compare to alternative (map encoding)
  
✗ Test 2: Encoding efficiency
  - Short strings: faster than alternatives?
  - Long strings: compression worth it?
```

---

### D. NetworkHandler (219 lines)

**Critical Functionality:**
- Network packet registration
- Handler dispatch for 3 payload types
- Mob stats application with AABB search optimization
- Attribute syncing to clients

**Testing Requirements:**

#### 9.1 Payload Registration
```
Type: Integration Test
Priority: MEDIUM
Current Coverage: PARTIAL (code implemented, not validated)

Test Cases:
✗ Test 1: All 3 payloads registered
  - MobStats on channel "1"
  - WeaponPayload on channel "2"
  - EquipPayload on channel "3"
  - Verify registrar().playToServer() called

✗ Test 2: Type/Codec consistency
  - TYPE matches STREAM_CODEC generic
  - ResourceLocation correct (namespace "devmod")
```

#### 9.2 Mob Stats Application
```
Type: Integration Test
Priority: HIGH
Current Coverage: NO

Test Cases:
✗ Test 1: Global stats application
  - isGlobal=true
  - Apply to all Zombie in 128-block radius
  - Verify count returned
  
✗ Test 2: Specific stats application
  - isGlobal=false, entityId=123
  - Only apply to that specific mob
  
✗ Test 3: AABB search radius
  - Config.MOB_SEARCH_RADIUS = 128
  - Search box centered on player
  - Should NOT search entire world (performance)

Attribute Syncing:
- All modified attributes must be in syncList
- ClientboundUpdateAttributesPacket broadcast
- Player receives confirmation message
```

#### 9.3 Equipment Application
```
Type: Integration Test
Priority: MEDIUM
Current Coverage: NO

Test Cases:
✗ Test 1: Item registry lookup
  - equipSlot(mob, MAINHAND, "iron_sword")
  - Should find Item in BuiltInRegistries.ITEM
  
✗ Test 2: Namespace fallback
  - "diamond_helmet" → assumes "minecraft:diamond_helmet"
  - "custommod:custom" → uses exact namespace
  
✗ Test 3: Clear equipment
  - "air" or "" → mob.setItemSlot(slot, ItemStack.EMPTY)
  
✗ Test 4: Error handling
  - Invalid item name: should log warning, not crash
  - Verification: LOGGER.warn() called

Edge Cases:
- ResourceLocation.parse() throws exception
- Item = null from registry
- Empty itemName string
```

---

## IV. TELEMETRY SYSTEM

### A. TelemetryService (1344 lines - LARGEST CLASS)

**Critical Functionality:**
- Central event logging (damage, heals, deaths, mob actions)
- Async file writing (NDJSON format)
- Player room tracking with definitions
- Performance metrics (FPS, tick time, entity count)
- Thread-safe concurrent maps (ConcurrentHashMap)

**Testing Requirements:**

#### 10.1 Async Writing Safety
```
Type: Integration Test
Priority: HIGH
Current Coverage: PARTIAL (code implemented, not validated)

Test Cases:
✗ Test 1: AsyncTelemetryWriter thread safety
  - Queue 1000 events concurrently
  - Verify all written to disk
  - No corruption, no data loss
  
✗ Test 2: Graceful shutdown
  - During active writes: shutdown() called
  - All pending writes flushed
  - File left in valid state
  
✗ Test 3: NDJSON line integrity
  - Each line valid JSON
  - Each line complete (no truncation)

Performance Targets:
- < 1ms overhead per event
- < 10MB/min file growth
```

#### 10.2 Thread Safety (ConcurrentHashMap)
```
Type: Concurrency Test
Priority: HIGH
Current Coverage: NO

Test Cases:
✗ Test 1: Concurrent map access
  - Multiple threads read/write playerRooms
  - No ConcurrentModificationException
  
✗ Test 2: Null safety
  - .get() returns null for missing key
  - No NPE on put(key, null)
  
✗ Test 3: Iteration safety
  - Iterate while concurrent modification
  - Should not deadlock or lose data

Maps requiring testing:
- playerRooms (UUID → PlayerRoomTracker)
- mobSpawnTime, confirmedHits, skillTrackers
- bossPhases, dungeonSessions, etc.
```

#### 10.3 Room Definition Loading
```
Type: Integration Test
Priority: MEDIUM
Current Coverage: PARTIAL

Test Cases:
✗ Test 1: Room definitions parsed
  - reload() loads from config
  - getRoomDefinitions() returns list
  
✗ Test 2: Room-entity tracking
  - Player in room A tracks stats separately
  - Player in room B separate tracker
```

---

### B. TelemetryEvents (80+ lines)

**Critical Functionality:**
- Event listeners: ServerTick, Damage, Death, EntityJoin/Leave
- HitContext cleanup (every tick)
- Telemetry throttling (20-tick interval configurable)

**Testing Requirements:**

#### 11.1 Event Registration
```
Type: Integration Test
Priority: MEDIUM
Current Coverage: NO

Test Cases:
✗ Test 1: All events registered
  - onServerStarted (TelemetryService.reload)
  - onServerStopped (shutdown)
  - onServerTick (cleanup, throttled telemetry)
  
✗ Test 2: Event dispatch order
  - Must run before/after other mods
  - No missed events due to timing
```

#### 11.2 HitContext Cleanup
```
Type: Unit Test
Priority: HIGH
Current Coverage: PARTIAL

Test Cases:
✓ Test: cleanup() called every tick
  - onServerTick → HitContext.cleanup()
  - Expired entries removed
```

#### 11.3 Telemetry Throttling
```
Type: Unit Test
Priority: MEDIUM
Current Coverage: PARTIAL

Test Cases:
✗ Test 1: Interval calculation
  - Config.TELEMETRY_TICK_INTERVAL = 20
  - Every 20 ticks: telemetryTickCounter resets
  - Telemetry operations run 1/20th as often
  
✗ Test 2: No frame loss
  - Even with throttling, no events dropped
  - Queue maintained until process time
```

---

## V. INTEGRATION SYSTEMS

### A. ModIntegrationManager (151 lines)

**Critical Functionality:**
- Soft dependency checking (ModList.isLoaded)
- Delegated initialization to sub-integrations
- Wraps Pehkui and Better Combat APIs
- Graceful fallback when mods absent

**Testing Requirements:**

#### 12.1 Mod Detection
```
Type: Integration Test
Priority: MEDIUM
Current Coverage: NO

Test Cases:
✗ Test 1: Pehkui detection
  - ModList.isLoaded("pehkui") = true/false
  - isPehkuiLoaded() matches
  
✗ Test 2: Better Combat detection
  - ModList.isLoaded("bettercombat") = true/false
  - isBetterCombatLoaded() matches
  
✗ Test 3: Initialization state
  - First call: init()
  - Second call: returns early (idempotent)

Environments:
- With both mods loaded
- With no mods loaded
- With only Pehkui
- With only Better Combat
```

#### 12.2 Graceful Fallback
```
Type: Unit Test
Priority: MEDIUM
Current Coverage: PARTIAL

Test Cases:
✗ Test 1: Pehkui calls when unavailable
  - isAvailable() = false
  - getPehkuiScale(entity) returns null
  - No exception thrown
  
✗ Test 2: Better Combat calls when unavailable
  - isBetterCombatLoaded() = false
  - getBetterCombatAttackName(player) returns null
  - No reflection errors

Error Handling:
- Reflection exceptions caught
- Logged at DEBUG level
- No crash propagation
```

---

### B. BetterCombatIntegration (177 lines)

**Critical Functionality:**
- Reflection-based API access
- Attack name retrieval
- Weapon attributes (reach, damage)
- Combo detection and counting

**Testing Requirements:**

#### 13.1 Reflection Safety
```
Type: Integration Test
Priority: MEDIUM
Current Coverage: NO

Test Cases:
✗ Test 1: Class loading via reflection
  - init() tries Class.forName("net.bettercombat.logic.PlayerAttackHelper")
  - If not found: gracefully sets available=false
  
✗ Test 2: Method lookup resilience
  - getMethod() on various BC versions
  - If method signature changed: return null/default
  
✗ Test 3: Version compatibility
  - Test with multiple BC versions
  - Major API changes don't crash

Edge Cases:
- Classpath excluded BC (should not throw)
- BC JAR deleted at runtime (rare but possible)
- Obfuscated class names (version-specific)
```

#### 13.2 Attack Data Retrieval
```
Type: Integration Test (Requires BC mod loaded)
Priority: MEDIUM
Current Coverage: NO

Test Cases (if Better Combat present):
✗ Test 1: getCurrentAttackName()
  - Player with sword: returns "Slash", "Thrust", etc.
  - Player empty handed: returns null
  
✗ Test 2: getExtendedReach()
  - Returns reach bonus from weapon
  - Vanilla: 0.0
  - BC sword: 3.5-7.5 range
  
✗ Test 3: Combo detection
  - isInCombo() during combo sequence
  - getComboCount() increments 1,2,3...

Mocking Strategy:
- Mock reflection results
- Or: conditional test skip if BC not present
```

---

### C. PehkuiIntegration (172 lines)

**Critical Functionality:**
- Scale retrieval (visual and hitbox)
- Lazy initialization with cached reflection
- Version compatibility (ScaleTypes.HITBOX_WIDTH → WIDTH fallback)

**Testing Requirements:**

#### 14.1 Scale Retrieval
```
Type: Integration Test (Requires Pehkui mod loaded)
Priority: MEDIUM
Current Coverage: NO

Test Cases (if Pehkui present):
✗ Test 1: getScale() base visual scale
  - Scaled entity: 2.0 → returns 2.0f
  - Normal entity: 1.0 → returns 1.0f
  
✗ Test 2: getHitboxScale() hitbox scale
  - May differ from visual scale
  - Different per entity type
  
✗ Test 3: isAvailable() check
  - After successful init: true
  - After failed init: false

Edge Cases:
- Entity not in Pehkui system (returns 1.0f)
- Null entity parameter (returns null)
```

#### 14.2 Reflection Caching
```
Type: Unit Test
Priority: MEDIUM
Current Coverage: PARTIAL (lazy init exists)

Test Cases:
✗ Test 1: Lazy initialization
  - First call to getScale(): runs initReflection()
  - Second call: uses cached methods (fast)
  
✗ Test 2: Reflection cache hit
  - Measure: first call vs 100th call
  - 100th call should be ~100x faster

✗ Test 3: Thread safety
  - Multiple threads call getScale()
  - initReflection() runs only once (synchronized)
```

#### 14.3 Version Compatibility
```
Type: Integration Test
Priority: MEDIUM
Current Coverage: PARTIAL (fallback code exists)

Test Cases:
✗ Test 1: HITBOX_WIDTH field present
  - New Pehkui version: uses HITBOX_WIDTH
  
✗ Test 2: WIDTH field fallback
  - Old Pehkui version: uses WIDTH instead
  
✗ Test 3: BASE scale fallback
  - Very old version: uses BASE for hitbox
  - Should not crash, returns visual scale

Versioning:
- Test with 3.7, 3.8, 3.8.3+1.21
```

---

## VI. HUD/RENDERING COMPONENTS

### A. ImpactData (269 lines)

**Critical Functionality:**
- Stores last impact info (body part, damage, multiplier)
- Integration with Pehkui and Better Combat
- Thread-safe atomic singleton (AtomicReference)
- Auto-expire after 3 seconds

**Testing Requirements:**

#### 15.1 Data Storage
```
Type: Unit Test
Priority: MEDIUM
Current Coverage: NO

Test Cases:
✗ Test 1: store() and get()
  - create ImpactData with all fields
  - store() saves to LAST_IMPACT
  - get() returns same instance
  
✗ Test 2: Multiple stores (overwrite)
  - store(impact1)
  - store(impact2)
  - get() returns impact2 (latest)
  
✗ Test 3: Weak reference handling
  - target entity GC'd
  - targetRef.get() returns null
  - Should not crash on access
```

#### 15.2 Integration Data
```
Type: Integration Test
Priority: MEDIUM
Current Coverage: PARTIAL

Test Cases:
✗ Test 1: Pehkui scale capture
  - ImpactData captures ModIntegrationManager.getPehkuiScale(target)
  - Stored in pehkuiVisualScale field
  
✗ Test 2: Better Combat attack name
  - Captured via modIntegrationManager
  - Stored in betterCombatAttackName
  
✗ Test 3: Null handling
  - Pehkui/BC unavailable: fields = null
  - Should not NPE on access
```

#### 15.3 Expiration
```
Type: Unit Test
Priority: MEDIUM
Current Coverage: NO

Test Cases:
✗ Test 1: DISPLAY_DURATION_MS timeout
  - store() at T=0
  - isExpired() at T=2999ms → false
  - isExpired() at T=3001ms → true
  
✗ Test 2: Auto-clear
  - After 3s: HUD should not render
  - get() should return null
```

---

### B. DamageBreakdown (100+ lines)

**Critical Functionality:**
- Calculates damage composition: base + enchants + Pehkui + multiplier
- Enchantment detection (Sharpness, Smite, Bane of Arthropods)
- Pehkui size scaling (25% per scale point)
- Final damage calculation

**Testing Requirements:**

#### 16.1 Enchantment Bonus Calculation
```
Type: Unit Test
Priority: MEDIUM
Current Coverage: NO

Test Cases:
✗ Test 1: Sharpness bonus
  - Sharpness I: +1.0 damage
  - Sharpness II: +1.5 damage
  - Sharpness V: +3.0 damage
  
✗ Test 2: Smite vs undead only
  - Target = Zombie (isInvertedHealAndHarm() = true)
  - Smite III: +7.5 damage
  - Target = Player: +0 damage (not undead)
  
✗ Test 3: Bane of Arthropods
  - Target = Spider: +2.5 per level
  - Target = Zombie: +0 damage
  - isArthropod() check accuracy

Enchant Types Tested:
- Sharpness (all mobs)
- Smite (undead only: zombie, skeleton, wither)
- Bane of Arthropods (spiders, silverfish, bees)
- Fire Aspect (noted but no damage bonus)
```

#### 16.2 Pehkui Size Bonus
```
Type: Integration Test
Priority: MEDIUM
Current Coverage: NO

Test Cases:
✗ Test 1: Size scaling formula
  - Scale = 2.0: bonus = base * 0.25 * (2.0 - 1.0) = 0.25 * base
  - Scale = 1.5: bonus = base * 0.25 * (1.5 - 1.0) = 0.125 * base
  - Scale = 1.0: bonus = 0
  
✗ Test 2: No bonus for normal size
  - Scale = 1.0: should return 0 bonus
  
✗ Test 3: Proportional scaling
  - Larger entity: larger bonus
  - Verification: bonus ∝ (scale - 1.0)

Edge Cases:
- Scale = 0.5 (smaller): should be 0, not negative
- Scale = infinity (mod overflow): clamp?
```

#### 16.3 Final Damage Calculation
```
Type: Unit Test
Priority: CRITICAL
Current Coverage: NO

Test Cases:
✗ Test 1: Formula verification
  - finalDamage = (base + enchants + pehkui) * bodyPartMult + armorPen
  - Verify equation exactly
  
✗ Test 2: Body part multiplier
  - HEAD (2.5x): 10 base → 25 final
  - LEGS (0.5x): 10 base → 5 final
  
✗ Test 3: Armor penetration additive
  - base=10, mult=1.0, pen=3.0
  - final = (10 * 1.0) + 3.0 = 13.0
  - Should add after multiplier

Calculation Order:
- Sum all additive bonuses (base + enchants + pehkui)
- Multiply by body part
- Add armor penetration

Edge Cases:
- Negative final damage (should be clamped to 0?)
- Float overflow
```

---

## VII. CRITICAL EDGE CASES & FAILURE MODES

### Priority Issues Across All Systems

#### A. Null Pointer Prevention
```
Components:
- DamageHandler.onDamage() with null event.getSource()
- HitHelper.rayTraceBodyPartAABB() with null attacker
- WeaponStats.load(null tag) → should handle gracefully
- TelemetryService operations with null player
- ImpactData with null target (but has WeakRef)

Required Tests:
✗ Test each component with null inputs
✗ Verify error message logging (not silent fails)
✗ Verify no NPE propagation to console
```

#### B. Floating Point Precision
```
Components:
- Damage calculations (0.1 damage deltas)
- Body part multipliers (0.7f vs 0.70000012f)
- Network serialization (float round-trip)
- Scale calculations (Pehkui 1.005x precision)

Required Tests:
✗ Test equality with epsilon (1e-6) not ==
✗ Test serialization preserves precision
✗ Test visual damage display vs actual stored value
```

#### C. Thread Safety & Race Conditions
```
Components:
- HitContext concurrent store/retrieve/cleanup
- TelemetryService ConcurrentHashMap access
- DamageHandler pendingAttacks map
- ImpactData AtomicReference swap

Required Tests:
✗ Stress test with 100+ concurrent threads
✗ Interleave operations (store during cleanup)
✗ Verify no ConcurrentModificationException
✗ Measure contention (lock hold time)
```

#### D. Config Load Order Dependencies
```
Components:
- WeaponStats default values depend on Config loaded
- HitHelper cache TTL from Config
- NetworkHandler mob search radius from Config

Required Tests:
✗ Test with Config not initialized (fallback values)
✗ Test Config loaded after component init (refresh)
✗ Test Config disabled (all defaults)
```

#### E. Memory Leaks
```
Components:
- HitHelper.BODY_PART_CACHE (1000 entries max)
- HitContext.CONTEXT (100ms TTL cleanup)
- ImpactData.WeakReference (should allow GC)
- TelemetryService maps (mob tracking)

Required Tests:
✗ Allocate 10K entities, track all
✗ Verify memory freed when entities unload
✗ Measure heap growth over time
✗ Check cleanup frequency is sufficient
```

#### F. Network Deserialization Safety
```
Components:
- UpdateMobStatsPayload with Double.POSITIVE_INFINITY
- UpdateWeaponPayload with NaN floats
- EquipMobPayload with very long item names (65K+)

Required Tests:
✗ Fuzz testing with malformed ByteBuf
✗ Buffer overflow attempts
✗ Invalid enum values
✗ Negative entity IDs
```

---

## VIII. TEST IMPLEMENTATION ROADMAP

### Phase 1: Core Systems (Weeks 1-2)
**Priority: CRITICAL** - Must pass before any release

1. **DamageHandler Tests**
   - Basic damage multiplier application
   - Body part detection (melee vs ranged)
   - Armor penetration calculation
   - Environmental damage mapping

2. **HitHelper Tests**
   - AABB raycast accuracy
   - Body part detection precision
   - Cache functionality
   - Non-humanoid entity adaptation

3. **WeaponStats Tests**
   - Default values validation
   - NBT round-trip serialization
   - Global/specific stat priority

**Estimated Lines:** 500-700 test code

### Phase 2: Configuration Systems (Weeks 2-3)
**Priority: HIGH** - Required for GameTest validation

1. **MobConfigManager Tests**
   - JSON persistence
   - EntityType → ResourceLocation mapping
   - Backwards compatibility (4-field → 5-field)

2. **WeaponConfigManager Tests**
   - Global/specific stat retrieval
   - NBT-based item-specific storage

**Estimated Lines:** 300-400 test code

### Phase 3: Network & Integration (Weeks 3-4)
**Priority: MEDIUM** - Required for multiplayer validation

1. **Network Payload Tests**
   - StreamCodec serialization (all 3 payloads)
   - Float precision in network
   - String encoding (UTF-8)

2. **Integration Tests**
   - Pehkui integration (if available)
   - Better Combat integration (if available)
   - ModIntegrationManager fallback

**Estimated Lines:** 400-500 test code

### Phase 4: Telemetry & HUD (Weeks 4-5)
**Priority: MEDIUM** - Optional but recommended

1. **TelemetryService Tests**
   - Async writing safety
   - Thread-safe map operations
   - Event dispatching

2. **HUD Components Tests**
   - ImpactData storage/retrieval
   - DamageBreakdown calculation
   - Enchantment bonus accuracy

**Estimated Lines:** 300-400 test code

### Phase 5: Stress & Performance (Week 5+)
**Priority: LOW** - Performance regression testing

1. **Concurrency Stress Tests**
   - 100+ simultaneous damage events
   - 1000+ cached entries

2. **Performance Benchmarks**
   - Body part detection time
   - Network serialization throughput
   - Cache hit rate validation

**Estimated Lines:** 200-300 test code

---

## IX. TEST INFRASTRUCTURE RECOMMENDATIONS

### Test Framework
```
- Minecraft GameTest Framework (built-in)
- JUnit 5 for unit tests (already in gradle)
- Mockito for mocking Minecraft APIs
- Concurrency testing library (jcstress optional)
```

### Mock Objects Required
```
- LivingEntity mock (health, attributes, position)
- ItemStack mock (enchantments, NBT data)
- ByteBuf mock (for network payload tests)
- Config mock (for testing fallback values)
- ModList mock (for integration tests)
```

### Test Data
```
- Zombie/Player/Enderman entity templates
- Iron/Diamond weapon item stacks
- Sample mob config JSON files
- Network payload ByteBuf dumps
```

### Continuous Integration
```
- Run GameTests on PR: ./gradlew runGameTestServer
- Unit test target: 70%+ coverage on core classes
- Performance regression: body part detection < 1ms
- Network payload tests on serialization/deserialization
```

---

## CONCLUSION

This codebase contains **3 critical systems** with high complexity and interdependencies:

1. **Damage Calculation** (DamageHandler + HitHelper) - 95% accuracy required
2. **Configuration Management** (Manager classes) - Zero data loss acceptable
3. **Network Communication** (Payloads) - Must handle untrusted input safely

**Current Test Coverage: ~1.8%** (312 lines in 17K LOC)  
**Recommended Coverage Target: 60%+** (all critical paths)  
**Estimated Test Code: 1500-2000 lines** (5-7 week effort)

### Most Critical Tests to Implement First:
1. DamageHandler damage calculation (15 tests)
2. HitHelper body part detection (12 tests)
3. Network payload serialization (10 tests)
4. WeaponStats NBT round-trip (8 tests)
5. MobConfigManager JSON persistence (8 tests)

