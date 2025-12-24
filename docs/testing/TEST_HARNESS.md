# DevMod Test Harness Configuration

## Overview

DevMod utilizza un sistema di test a due livelli:
1. **JUnit 5** - Test unitari per logica isolata
2. **NeoForge GameTest** - Test in-game per validazione server-side

---

## JUnit 5 Configuration

### Dependencies (build.gradle)

```gradle
dependencies {
    testImplementation 'org.junit.jupiter:junit-jupiter-api:5.10.0'
    testImplementation 'org.junit.jupiter:junit-jupiter-params:5.10.0'
    testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.10.0'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

test {
    useJUnitPlatform()
    testLogging {
        events "passed", "skipped", "failed"
        showStandardStreams = true
    }
}
```

### Test Location

```
src/test/java/com/frenkvs/devmod/
├── instance/
│   ├── InstanceSystemLogicTest.java       # State machines
│   ├── InstanceFlowValidationTest.java    # Flow validation
│   ├── ErrorRecoveryScenarioTest.java     # Recovery tests
│   ├── EdgeCaseStressTest.java            # Stress tests
│   ├── MultiplayerConcurrencyTest.java    # Concurrency
│   ├── QuestLifecycleSimulationTest.java  # Quest lifecycle
│   ├── ServerRestartSimulationTest.java   # Restart scenarios
│   ├── PartyCoordinationTest.java         # Party system
│   └── ConcurrencyStressTest.java         # Additional stress
└── ... (other test packages)
```

```
src/test/java/com/devmod/arena/
├── registry/   # Template loader, schema, golden reference
├── builder/    # Async builder + dry-run
├── spawn/      # Spawn slots validation/resolution
├── fallback/   # Rollback/fallback strategy
├── cleanup/    # Cleanup executor tests
├── monitor/    # MSPT monitor tests
├── alert/      # Alert router tests
└── integration/# Arena integration tests
```

### Running JUnit Tests

```bash
# All tests
./gradlew test

# Specific package
./gradlew test --tests "com.devmod.instance.*"

# Specific test class
./gradlew test --tests "InstanceFlowValidationTest"

# Force rerun (ignore cache)
./gradlew test --rerun-tasks
```

---

## NeoForge GameTest Configuration

### Run Configuration (build.gradle)

```gradle
runs {
    gameTestServer {
        type = "gameTestServer"
        systemProperty 'neoforge.enabledGameTestNamespaces', project.mod_id
    }
}
```

### GameTest Location

```
src/main/java/com/frenkvs/devmod/gametest/
├── DevModGameTests.java           # Core mod tests (18 tests)
└── InstanceSystemGameTests.java   # Instance system tests (18 tests)
```

### Structure Templates

```
src/main/resources/data/devmod/structure/
├── empty.nbt          # 3x3x3 empty structure
├── empty_3x3.nbt      # 3x3x3 empty structure (alias)
├── empty_5x5.nbt      # 5x5x5 empty structure
└── combat_arena.nbt   # Combat test arena
```

### Running GameTests

```bash
# Run all GameTests (exits after completion)
./gradlew runGameTestServer

# Run in client (tests available via /test command)
./gradlew runClient

# Run on server (tests available via /test command)
./gradlew runServer
```

---

## Test Batches

### JUnit Batches (Nested Classes)

| Package | Test Class | Focus |
|---------|------------|-------|
| instance | InstanceSystemLogicTest | State machines, UUID parsing |
| instance | InstanceFlowValidationTest | Complete flow, race conditions |
| instance | ErrorRecoveryScenarioTest | All recovery scenarios |
| instance | EdgeCaseStressTest | Boundary conditions, stress |
| instance | MultiplayerConcurrencyTest | Party, thread safety |

### Arena Template JUnit Suite (Packages)

| Package | Focus |
|---------|-------|
| arena.registry | Schema validation, inheritance, golden reference |
| arena.builder | Dry-run estimation, async build, priority |
| arena.spawn | Spawn slot validation/resolution |
| arena.fallback | Rollback/fallback strategy |
| arena.alert | Alert routing |

### GameTest Batches (@GameTest batch parameter)

| Batch | Test Class | Required | Focus |
|-------|------------|----------|-------|
| core | DevModGameTests | YES | Weapon stats, damage calc |
| network | DevModGameTests | YES | Payload serialization |
| entities | DevModGameTests | NO | Body parts, mob config |
| config | DevModGameTests | NO | Config persistence |
| cache | DevModGameTests | NO | Cache management |
| instance_smoke | InstanceSystemGameTests | YES | System initialization |
| instance_state | InstanceSystemGameTests | NO | State machines |
| instance_flow | InstanceSystemGameTests | NO | Data serialization |
| instance_recovery | InstanceSystemGameTests | NO | Recovery scenarios |

---

## Writing New Tests

### JUnit Test Template

```java
package com.devmod.instance;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Feature Name Tests")
class FeatureNameTest {

    @BeforeEach
    void setUp() {
        // Setup before each test
    }

    @AfterEach
    void tearDown() {
        // Cleanup after each test
    }

    @Nested
    @DisplayName("Scenario Category")
    class ScenarioCategory {

        @Test
        @DisplayName("Test description")
        void testSomething() {
            // Arrange
            // Act
            // Assert
            assertTrue(condition, "Failure message");
        }
    }
}
```

### GameTest Template

```java
package com.devmod.gametest;

import com.devmod.DevMod;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(DevMod.MODID)
@PrefixGameTestTemplate(false)
public class MyGameTests {

    private static final String TEMPLATE = "empty";

    @BeforeBatch(batch = "my_batch")
    public static void setup(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] Setting up batch");
    }

    @AfterBatch(batch = "my_batch")
    public static void cleanup(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] Cleaning up batch");
    }

    @GameTest(template = TEMPLATE, batch = "my_batch", required = true)
    public static void myTest(GameTestHelper helper) {
        // Test logic
        helper.assertTrue(condition, "Failure message");
        helper.succeed(); // Must call on success
    }

    @GameTest(template = TEMPLATE, batch = "my_batch", timeoutTicks = 200)
    public static void asyncTest(GameTestHelper helper) {
        // Setup
        helper.runAfterDelay(100, () -> {
            // Delayed assertion
            helper.assertTrue(condition, "Failure message");
            helper.succeed();
        });
    }
}
```

---

## Current Test Coverage

### Unit Tests (JUnit 5)

| Category | Tests | Status |
|----------|-------|--------|
| State Machines | 24 | PASS |
| Flow Validation | 16 | PASS |
| Error Recovery | 17 | PASS |
| Edge Cases | 27 | PASS |
| Concurrency | 12 | PASS |
| Quest Lifecycle | 15 | PASS |
| Server Restart | 24 | PASS |
| Party System | 8 | PASS |
| **Total** | **655** | **PASS** |

### GameTests

| Category | Tests | Status |
|----------|-------|--------|
| Core (weapon, damage) | 5 | READY |
| Network (payloads) | 4 | READY |
| Entities (mob, body) | 3 | READY |
| Config (persistence) | 3 | READY |
| Cache | 3 | READY |
| Instance Smoke | 4 | READY |
| Instance State | 5 | READY |
| Instance Flow | 3 | READY |
| Instance Recovery | 6 | READY |
| **Total** | **36** | **READY** |

---

## CI/CD Integration

### Recommended CI Pipeline

```yaml
# .github/workflows/test.yml
name: Test

on: [push, pull_request]

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Run Unit Tests
        run: ./gradlew test --no-daemon
      - name: Upload Test Results
        uses: actions/upload-artifact@v4
        with:
          name: test-results
          path: build/reports/tests/

  game-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Run GameTests
        run: ./gradlew runGameTestServer --no-daemon
```

---

## Troubleshooting

### Common Issues

1. **Tests not found**: Ensure test classes are in `src/test/java`
2. **GameTest template missing**: Verify NBT file in `data/devmod/structure/`
3. **Batch not running**: Check `@BeforeBatch` matches `@GameTest` batch name
4. **Timeout**: Increase `timeoutTicks` parameter

### Debug Commands

```bash
# Verbose test output
./gradlew test --info

# Debug mode
./gradlew test --debug

# Specific test with stacktrace
./gradlew test --tests "ClassName.methodName" --stacktrace
```
