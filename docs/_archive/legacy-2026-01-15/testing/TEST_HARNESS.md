# DevMod Test Harness

> Last updated: 2025-12-26
> Status: CURRENT (testing harness)
> Audit date: 2025-12-26
> Risk level: MEDIUM (GameTest relies on live MC server)

---

## 1. Harness Entry Points

- **JUnit 5**: `src/test/java/com/devmod/` (direct and integration tests).
- **GameTests**: `com.devmod.gametest.*`
  - `DevModGameTests`
  - `InstanceSystemGameTests`
  - `L0BootVerificationTests`
- **Test harness commands**: `com.devmod.gametest.TestHarnessCommands`
- **QA UI**: `com.devmod.client.ui.hub.TestingHub`, `com.devmod.client.testing.QATestingScreen`

---

## 2. Running Tests

```bash
# JUnit suite
./gradlew test --no-build-cache

# Direct tests (pure logic)
./gradlew test --no-build-cache --tests 'com.devmod.*DirectTest'

# GameTests (headless server)
./gradlew runGameTestServer
```

---

## 3. GameTest Templates

```
src/main/resources/data/devmod/structure/
├── empty.nbt
├── empty_3x3.nbt
├── empty_5x5.nbt
└── combat_arena.nbt
```

---

## 4. QA State Files

The QA system persists progress under `config/devmod/` via `com.devmod.util.ConfigPaths`:
- `qa_session.json`
- `tester_profile.json`
- `tester_progress.json`

---

## 5. Notes

- Use direct tests for deterministic logic (no Minecraft classes).
- Use GameTests when behavior depends on world state, registries, or server lifecycle.
- `/devtest` commands are registered in `TestHarnessCommands`.
