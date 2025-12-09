# DevMod GameTests - Quick Reference Guide

## Critical Testing Components (Priority Order)

### 1. CORE DAMAGE SYSTEM (40% of effort)
**Files:** DamageHandler.java (589 lines), HitHelper.java (440 lines)

| Component | Test Cases | Priority | Complexity |
|-----------|-----------|----------|-----------|
| Melee damage multipliers | 4 tests | CRITICAL | High |
| Arrow/ranged detection | 3 tests | HIGH | Medium |
| Body part AABB raycast | 3 tests | CRITICAL | Very High |
| Environmental damage (44 types) | 2 tests | MEDIUM | High |
| Enderman evasion detection | 3 tests | MEDIUM | High |
| Hit caching (100ms TTL) | 3 tests | MEDIUM | Medium |
| **SUBTOTAL** | **18 tests** | — | — |

### 2. CONFIGURATION (20% of effort)
**Files:** MobConfigManager.java, WeaponConfigManager.java, WeaponStats.java

| Component | Test Cases | Priority | Complexity |
|-----------|-----------|----------|-----------|
| Weapon stats defaults | 1 test ✓ | HIGH | Low |
| Weapon NBT serialization | 3 tests | MEDIUM | Medium |
| Config fallback chain | 4 tests | CRITICAL | Medium |
| Mob config JSON I/O | 4 tests | HIGH | High |
| EntityType registry mapping | 3 tests | MEDIUM | Medium |
| Backwards compatibility | 2 tests | MEDIUM | Low |
| **SUBTOTAL** | **17 tests** | — | — |

### 3. NETWORK (20% of effort)
**Files:** NetworkHandler.java, UpdateMobStatsPayload.java, UpdateWeaponPayload.java, EquipMobPayload.java

| Component | Test Cases | Priority | Complexity |
|-----------|-----------|----------|-----------|
| Mob stats serialization | 3 tests | CRITICAL | Medium |
| Weapon payload serialization | 3 tests | CRITICAL | Medium |
| Equipment payload serialization | 3 tests | CRITICAL | Medium |
| Network handler dispatch | 3 tests | MEDIUM | Medium |
| **SUBTOTAL** | **12 tests** | — | — |

### 4. TELEMETRY & INTEGRATION (15% of effort)
**Files:** TelemetryService.java, ModIntegrationManager.java, HitContext.java

| Component | Test Cases | Priority | Complexity |
|-----------|-----------|----------|-----------|
| HitContext thread safety | 3 tests | HIGH | High |
| Async telemetry writing | 2 tests | MEDIUM | High |
| Mod availability detection | 3 tests | MEDIUM | Low |
| Pehkui integration | 3 tests | MEDIUM | Medium |
| Better Combat integration | 3 tests | MEDIUM | Medium |
| **SUBTOTAL** | **14 tests** | — | — |

### 5. HUD/RENDERING (5% of effort)
**Files:** ImpactData.java, DamageBreakdown.java

| Component | Test Cases | Priority | Complexity |
|-----------|-----------|----------|-----------|
| ImpactData storage/expiry | 3 tests | LOW | Low |
| Damage breakdown calculation | 4 tests | LOW | Medium |
| **SUBTOTAL** | **7 tests** | — | — |

---

## Test Implementation Schedule

### Week 1: Foundation (CRITICAL)
- [ ] Day 1: DamageHandler basic damage multiplier (3 tests)
- [ ] Day 2: WeaponStats defaults + NBT (4 tests)
- [ ] Day 3: HitHelper body part detection (3 tests)
- [ ] Day 4: Network payload serialization (9 tests)
- [ ] Day 5: Integration tests (damage + network)
**Target:** 19 tests, 0 failures

### Week 2: Configuration (HIGH)
- [ ] Day 1: MobConfigManager JSON I/O (4 tests)
- [ ] Day 2: WeaponConfigManager global/specific (4 tests)
- [ ] Day 3: Configuration fallback chain (4 tests)
- [ ] Day 4: EntityType registry mapping (3 tests)
- [ ] Day 5: Backwards compatibility (2 tests)
**Target:** 17 tests, 0 failures

### Week 3: Advanced (MEDIUM)
- [ ] Day 1: HitHelper caching + reach detection (3 tests)
- [ ] Day 2: Environmental damage types (2 tests)
- [ ] Day 3: Enderman evasion detection (2 tests)
- [ ] Day 4: TelemetryService thread safety (3 tests)
- [ ] Day 5: ModIntegrationManager + Pehkui (6 tests)
**Target:** 16 tests, 0 failures

### Week 4: Polish (LOW)
- [ ] Day 1: Better Combat integration (3 tests)
- [ ] Day 2: ImpactData + DamageBreakdown (7 tests)
- [ ] Day 3: Stress testing (concurrency, memory)
- [ ] Day 4: Performance benchmarks
- [ ] Day 5: Documentation + CI integration
**Target:** 10+ tests, <5% performance regression

---

## Highest-Impact Tests (Do First)

### Test 1: Basic Damage Multiplier (15 mins)
```java
@GameTest(template="empty", batch="core", required=true)
public static void damageMultiplierApplication(GameTestHelper helper) {
    // Given: 10 base damage, HEAD multiplier 2.0x
    // Expected: Final damage = 20
    // Edge cases: zero damage, negative damage, overflow
}
```

### Test 2: Melee vs Ranged Detection (20 mins)
```java
@GameTest(template="empty_5x5", batch="core", required=true)
public static void meleeVsRangedDetection(GameTestHelper helper) {
    // Melee: rayTrace AABB subdivision (95% accuracy target)
    // Ranged: Y-coordinate only (100% accuracy)
    // Verify correct path taken
}
```

### Test 3: Network Payload Round-Trip (20 mins)
```java
@GameTest(template="empty", batch="core", required=true)
public static void networkPayloadSerialization(GameTestHelper helper) {
    // UpdateMobStatsPayload encode/decode
    // UpdateWeaponPayload encode/decode
    // EquipMobPayload encode/decode
    // Float precision, boolean handling, string encoding
}
```

### Test 4: Config Fallback Chain (15 mins)
```java
@GameTest(template="empty", batch="config", required=true)
public static void configFallbackPriority(GameTestHelper helper) {
    // Priority: specific NBT > global map > defaults
    // Verify exact precedence
}
```

### Test 5: JSON Persistence (25 mins)
```java
@GameTest(template="empty", batch="config")
public static void mobConfigPersistence(GameTestHelper helper) {
    // Save 5 entity types
    // Load from disk
    // Verify exact match
    // Check auto-save on modification
}
```

---

## Testing Patterns

### Unit Test Pattern
```java
@GameTest(template=TEMPLATE_EMPTY, batch="core", required=true)
public static void descriptiveTestName(GameTestHelper helper) {
    // Arrange
    WeaponStats stats = new WeaponStats();
    
    // Act
    float damage = calculateDamage(10, stats.headMult);
    
    // Assert
    helper.assertTrue(Math.abs(damage - 15.0f) < 0.001f,
        "Damage should be 15.0, was: " + damage);
    
    helper.succeed();
}
```

### Integration Test Pattern
```java
@GameTest(template=TEMPLATE_5X5, batch="entities")
public static void entityInteractionTest(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    
    // Create test entities
    Zombie zombie = new Zombie(EntityTypes.ZOMBIE, level);
    
    // Perform operations
    MobConfigManager.setGlobalStats(EntityTypes.ZOMBIE, 32, 5, 20, 2);
    
    // Verify results
    SavedStats retrieved = MobConfigManager.getGlobalStats(EntityTypes.ZOMBIE);
    helper.assertTrue(Math.abs(retrieved.damage - 5.0) < 0.001f,
        "Damage mismatch");
    
    helper.succeed();
}
```

### Concurrency Test Pattern
```java
@GameTest(template=TEMPLATE_EMPTY, batch="core")
public static void concurrentOperations(GameTestHelper helper) {
    ExecutorService executor = Executors.newFixedThreadPool(10);
    
    for (int i = 0; i < 100; i++) {
        final int id = i;
        executor.submit(() -> {
            HitContext.store(createMockEntity(id), 
                           BodyPart.HEAD, true);
        });
    }
    
    executor.shutdown();
    executor.awaitTermination(5, TimeUnit.SECONDS);
    
    // Verify no exceptions, no data loss
    helper.succeed();
}
```

---

## Validation Checklist

### Pre-Commit
- [ ] All new tests pass locally: `./gradlew runGameTestServer`
- [ ] No test intermittent failures (run 3x)
- [ ] Code coverage increased (target: 60%+ on core classes)
- [ ] No memory leaks detected (monitor heap growth)

### Code Review
- [ ] Tests cover primary path + 2+ edge cases
- [ ] Thread safety verified (if applicable)
- [ ] Floating point precision handled (epsilon comparison)
- [ ] Null inputs handled gracefully

### Continuous Integration
- [ ] All required tests pass (batch="core")
- [ ] No performance regression (>5% slowdown)
- [ ] Network tests serialize correctly
- [ ] File I/O tests isolated (temp directories)

---

## Quick Reference: Component Dependencies

```
DamageHandler
├─ HitHelper (body part detection)
├─ WeaponConfigManager (get weapon stats)
├─ HitContext (store hit info)
├─ DamageBreakdown (HUD display)
└─ ImpactData (HUD panel)

HitHelper
├─ Config (cache TTL, reach)
├─ ModIntegrationManager (Better Combat reach)
└─ AABB calculations

WeaponConfigManager & MobConfigManager
├─ Config paths
├─ JSON serialization (GSON)
├─ NBT data (ItemStack)
└─ Registry lookups (BuiltInRegistries)

NetworkHandler
├─ UpdateMobStatsPayload
├─ UpdateWeaponPayload
├─ EquipMobPayload
└─ Entity/Item registry lookups

ModIntegrationManager
├─ PehkuiIntegration (reflection-based)
├─ BetterCombatIntegration (reflection-based)
└─ ModList.isLoaded checks
```

---

## Resources

- **GameTest Framework:** `/src/main/java/com/frenkvs/devmod/gametest/`
- **Existing Tests:** `DevModGameTests.java` (312 lines)
- **Build Command:** `./gradlew runGameTestServer`
- **Test Results:** `build/gametest-results/`

