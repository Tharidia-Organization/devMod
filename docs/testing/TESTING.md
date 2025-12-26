# DevMod Testing Guide

This document describes the testing strategy, test organization, and how to run and write tests for DevMod.

## Overview

DevMod uses a **Progressive Testing Methodology** (L0-L5) to ensure comprehensive coverage from basic sanity checks to stress tests.

**Current Status:** 2172 tests passing

## Test Levels

| Level | Name | Purpose | Execution Time |
|-------|------|---------|----------------|
| L0 | Smoke | Basic sanity, mod loads | < 5s |
| L1 | Unit | Individual component logic | < 30s |
| L2 | Integration | Component interactions | < 60s |
| L3 | Sync | Network payload serialization | < 30s |
| L4 | Flow | End-to-end user journeys | < 120s |
| L5 | Stress | Load, concurrency, memory | < 300s |

## Running Tests

### All Tests
```bash
./gradlew test
```

### Specific Test Class
```bash
./gradlew test --tests "com.devmod.endurance.PerkSystemTest"
```

### Specific Test Package
```bash
./gradlew test --tests "com.devmod.endurance.*"
```

### By Level
```bash
# L0 Smoke tests
./gradlew test --tests "*SmokeTest"
./gradlew test --tests "*L0*"

# L5 Stress tests
./gradlew test --tests "*StressTest"
./gradlew test --tests "*L5*"
```

### With Detailed Output
```bash
./gradlew test --info
```

### Force Re-run
```bash
./gradlew test --rerun
```

## Test Organization

```
src/test/java/com/devmod/
├── boot/
│   └── L0SmokeBootTest.java           # L0: Mod boot verification
├── endurance/
│   ├── ArenaWaveManagerTest.java      # L1-L2: Wave management
│   ├── PerkSystemTest.java            # L1-L5: Perk system (168 tests)
│   ├── ComboSystemTest.java           # L1-L5: Combo system (160 tests)
│   └── RewardSystemTest.java          # L1-L5: Reward system (84 tests)
├── party/
│   └── PartyPayloadSerializationTest.java  # L3: Party payloads
├── flow/
│   └── UserFlowSimulationTest.java    # L4: User journeys
├── instance/
│   ├── InstanceSystemLogicTest.java   # L1-L2: Instance logic
│   ├── DataSerializationTest.java     # L3: Data persistence
│   └── MultiplayerConcurrencyTest.java # L5: Concurrent access
├── integration/
│   ├── L6AdvancedIntegrationTest.java # L2-L4: Cross-system
│   └── SerializationRoundTripTest.java # L3: Full serialization
└── stress/
    ├── L5MemoryAndSoakTest.java       # L5: Memory leaks
    ├── ConcurrentOperationStressTest.java # L5: Thread safety
    └── PerformancePatternTest.java    # L5: Performance
```

## Test Categories by System

### Endurance Quest System
| Test Class | Tests | Coverage |
|------------|-------|----------|
| PerkSystemTest | 168 | Tiers, categories, sessions, stacking, synergies |
| ComboSystemTest | 160 | Ranks, actions, scoring, milestones, decay |
| RewardSystemTest | 84 | Currency, shop, achievements, loot |
| ArenaWaveManagerTest | 50+ | Wave spawning, progression |

### Party System
| Test Class | Tests | Coverage |
|------------|-------|----------|
| PartyPayloadSerializationTest | 80+ | All party payloads |
| UserFlowSimulationTest | 100+ | Party formation, quest start |

### Instance System
| Test Class | Tests | Coverage |
|------------|-------|----------|
| InstanceSystemLogicTest | 60+ | Dimension management |
| DataSerializationTest | 40+ | State persistence |
| RecoverySystem tests | 30+ | Crash recovery |

### Integration Tests
| Test Class | Tests | Coverage |
|------------|-------|----------|
| L6AdvancedIntegrationTest | 100+ | Cross-system flows |
| L7CrossSystemIntegrationTest | 50+ | Full stack |
| EdgeCaseValidationTest | 80+ | Boundary conditions |

## Writing Tests

### Test Class Structure
```java
@DisplayName("System Name Tests")
class SystemNameTest {

    // Simulated classes (avoid Minecraft dependencies)
    static class SimComponent { ... }

    @BeforeEach
    void setUp() {
        // Initialize test fixtures
    }

    @Nested
    @DisplayName("L1: Component Tests")
    class ComponentTests {
        @Test
        @DisplayName("Should do expected behavior")
        void shouldDoExpectedBehavior() {
            // Arrange
            // Act
            // Assert
        }
    }

    @Nested
    @DisplayName("L5: Stress Tests")
    class StressTests {
        @Test
        @DisplayName("Handle concurrent access")
        void handleConcurrentAccess() throws InterruptedException {
            // Multi-threaded test
        }
    }
}
```

### Simulated Classes Pattern
To avoid Minecraft dependencies in unit tests, create simulated versions:

```java
// Production enum
public enum PerkTier {
    COMMON(60), UNCOMMON(25), RARE(10), EPIC(4), LEGENDARY(1);
    public final int weight;
    PerkTier(int weight) { this.weight = weight; }
}

// Test simulation
enum SimPerkTier {
    COMMON(60), UNCOMMON(25), RARE(10), EPIC(4), LEGENDARY(1);
    public final int weight;
    SimPerkTier(int weight) { this.weight = weight; }
}
```

### Parameterized Tests
```java
@ParameterizedTest
@EnumSource(SimPerkTier.class)
@DisplayName("Each tier has valid weight")
void eachTierHasValidWeight(SimPerkTier tier) {
    assertTrue(tier.weight > 0);
}

@ParameterizedTest
@ValueSource(ints = {1, 5, 10, 25, 50, 100})
@DisplayName("Combo milestones at exact values")
void comboMilestonesAtExactValues(int targetCombo) {
    // Test each milestone
}
```

### Concurrent Tests
```java
@Test
@DisplayName("Thread-safe purchases")
void threadSafePurchases() throws InterruptedException {
    int threadCount = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
        executor.submit(() -> {
            try {
                startLatch.await();
                if (purchase()) successCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });
    }

    startLatch.countDown(); // Start all threads
    doneLatch.await(10, TimeUnit.SECONDS);
    executor.shutdown();

    assertEquals(1, successCount.get()); // Only one should succeed
}
```

## Test Naming Conventions

### Test Methods
```java
// Format: should[ExpectedBehavior]When[Condition]
void shouldReturnEmptyWhenNoSessionExists()
void shouldIncrementComboOnHit()
void shouldNotAllowDoubleSpending()
```

### Display Names
```java
@DisplayName("Returns empty when no session exists")
@DisplayName("Increments combo counter on each hit")
@DisplayName("Prevents double-spending with concurrent purchases")
```

## CI Integration

Tests run automatically on:
- Every push
- Every pull request

### Gradle Commands for CI
```bash
# Full test suite with reports
./gradlew test --continue

# Generate test report
./gradlew test jacocoTestReport

# Check for test failures
./gradlew check
```

## Test Reports

After running tests, find reports at:
```
build/reports/tests/test/index.html
```

## Common Issues

### Tests Timeout
Increase timeout in test:
```java
@Test
@Timeout(value = 30, unit = TimeUnit.SECONDS)
void longRunningTest() { ... }
```

### Flaky Statistical Tests
Use sufficient sample sizes:
```java
// Bad: Small sample, flaky
int trials = 100;

// Good: Large sample, stable
int trials = 50000;
```

### Thread Safety Issues
Use proper synchronization:
```java
// Use CountDownLatch for coordination
CountDownLatch startLatch = new CountDownLatch(1);
startLatch.countDown(); // Signal all threads to start

// Use AtomicInteger for counters
AtomicInteger counter = new AtomicInteger(0);
counter.incrementAndGet();
```

## Adding New Tests

1. **Identify the level** (L0-L5)
2. **Create test class** in appropriate package
3. **Use simulated classes** for Minecraft-independent tests
4. **Follow naming conventions**
5. **Add to appropriate nested class** by level
6. **Run locally** before committing:
   ```bash
   ./gradlew test --tests "YourNewTest"
   ```

## Related Documents

- [PROGRESSIVE_TEST_PLAN.md](PROGRESSIVE_TEST_PLAN.md) - Detailed test planning
- [ARCHITECTURE.md](../ARCHITECTURE.md) - System architecture
- [testing-reports](../_deprecated/testing-reports/) - Level reports (historical)
