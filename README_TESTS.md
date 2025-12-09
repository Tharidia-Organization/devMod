# DevMod GameTest Documentation

This document describes how to run, write, and maintain GameTests for the DevMod project.

## Overview

DevMod uses the NeoForge GameTest framework to validate critical mod functionality. Tests are organized into batches and run in an isolated server environment.

### Test Statistics

- **Total Tests**: 7
- **Required Tests**: 2 (pipeline-breaking)
- **Batches**: 3 (core, entities, config)

## Running Tests

### Local Development

Run all GameTests locally:

```bash
./gradlew runGameTestServer
```

Run with verbose output:

```bash
./gradlew runGameTestServer --info
```

### CI Pipeline

Tests run automatically on GitHub Actions via `.github/workflows/build.yml`:
1. **Build job**: Compiles the mod
2. **GameTest job**: Runs `runGameTestServer` with 10-minute timeout

Test results are uploaded as artifacts including:
- `run/logs/latest.log`
- `run/logs/debug.log`

## Test Organization

### Batches

Tests are grouped into batches with dedicated setup/teardown:

| Batch | Purpose | Setup |
|-------|---------|-------|
| `core` | WeaponStats, NBT serialization | Clears WeaponConfigManager |
| `entities` | Entity interactions, body parts | Clears MobConfigManager |
| `config` | Configuration managers | Clears both managers |

### Structure Templates

Located in `src/main/resources/data/devmod/structure/`:

| Template | Size | Use Case |
|----------|------|----------|
| `empty_3x3.nbt` | 3x3x3 | Unit tests |
| `empty_5x5.nbt` | 5x5x5 | Entity tests |
| `combat_arena.nbt` | 7x5x7 | Combat/damage tests |

## Test Catalog

### Required Tests (Pipeline-Breaking)

These tests MUST pass for the build to succeed:

| Test | Batch | Description |
|------|-------|-------------|
| `testWeaponStatsDefaults` | core | Validates default damage multipliers |
| `testBodyPartDamageMultiplierOrdering` | entities | Ensures HEAD > BODY > ARMS > LEGS |

### Optional Tests

| Test | Batch | Description |
|------|-------|-------------|
| `testWeaponStatsNBTSerialization` | core | NBT save/load roundtrip |
| `testBodyPartDetection` | entities | Body part enum validation |
| `testMobConfigManagerSaveLoad` | config | Mob config persistence |
| `testWeaponConfigGlobalStats` | config | Global weapon stats storage |
| `testWeaponConfigSpecificStatsPriority` | config | Specific > global priority |

## Writing New Tests

### Basic Test Structure

```java
@GameTest(template = "devmod:empty_3x3", batch = "core")
public static void testMyFeature(GameTestHelper helper) {
    // Arrange
    MyClass instance = new MyClass();

    // Act
    instance.doSomething();

    // Assert
    if (instance.getValue() != expectedValue) {
        helper.fail("Expected " + expectedValue + " but got " + instance.getValue());
    }

    helper.succeed();
}
```

### Test with Entities

```java
@GameTest(template = "devmod:empty_5x5", batch = "entities")
public static void testEntityBehavior(GameTestHelper helper) {
    // Spawn entity at center of structure
    Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 1, 2));

    // Perform actions
    zombie.setHealth(10.0f);

    // Verify after delay
    helper.runAfterDelay(20, () -> {
        if (zombie.getHealth() != 10.0f) {
            helper.fail("Health was modified unexpectedly");
        }
        helper.succeed();
    });
}
```

### Required Test

```java
@GameTest(template = "devmod:empty_3x3", batch = "core", required = true)
public static void testCriticalFeature(GameTestHelper helper) {
    // This test MUST pass or the build fails
    helper.succeed();
}
```

## Naming Conventions

- **Test methods**: `test<Feature><Aspect>` (e.g., `testWeaponStatsDefaults`)
- **Batch names**: lowercase, descriptive (`core`, `entities`, `config`)
- **Templates**: `<modid>:<name>` (e.g., `devmod:empty_5x5`)

## Creating Structure Templates

### Option 1: In-Game

1. Build structure in creative mode
2. Use structure block to save as `.nbt`
3. Copy to `src/main/resources/data/devmod/structure/`

### Option 2: Programmatic (Recommended)

Use `DevModTestStructures.buildEmptyStructure()` for simple platforms:

```java
DevModTestStructures.buildEmptyStructure(
    level,           // ServerLevel
    pos,             // BlockPos origin
    5,               // width
    5,               // height
    5,               // depth
    true             // add barrier walls
);
```

## Harness Commands

For manual testing of UI/HUD features that are difficult to automate:

| Command | Description |
|---------|-------------|
| `/devtest hud <on\|off\|toggle>` | Toggle Impact HUD |
| `/devtest panel <on\|off\|toggle>` | Toggle 3D panels |
| `/devtest debug <on\|off\|toggle>` | Toggle debug renderer |
| `/devtest debugbox <size>` | Add debug box at player |
| `/devtest debugclear` | Clear all debug shapes |
| `/devtest panelclear` | Clear all 3D panels |
| `/devtest info` | Show system status |
| `/devtest bodypart <part>` | Show body part info |

**Note**: Requires OP level 2 permissions.

### Example Usage

```
/devtest info
/devtest debug on
/devtest debugbox 2.0
/devtest bodypart HEAD
```

## Troubleshooting

### Tests Not Found

Ensure `DevModTestStructures` is properly annotated:
```java
@EventBusSubscriber(modid = DevMod.MODID)
```

### Structure Not Loading

- Check file exists at `data/devmod/structure/<name>.nbt`
- Verify template name includes modid: `devmod:template_name`

### Batch Cleanup Not Running

- Verify `@BeforeBatch` and `@AfterBatch` annotations
- Check batch name matches test batch exactly

### CI Timeout

- Default timeout is 10 minutes
- Increase in `.github/workflows/build.yml` if needed:
  ```yaml
  timeout-minutes: 15
  ```

## File Structure

```
src/main/java/com/frenkvs/devmod/gametest/
├── DevModGameTests.java      # Test implementations
├── DevModTestStructures.java # Structure helpers & registration
└── TestHarnessCommands.java  # Manual testing commands

src/main/resources/data/devmod/structure/
├── empty_3x3.nbt
├── empty_5x5.nbt
└── combat_arena.nbt
```

## Contributing

When adding new tests:
1. Choose appropriate batch based on feature area
2. Use existing templates or create new ones
3. Add `required = true` only for critical features
4. Update this documentation
5. Verify CI passes before merging
