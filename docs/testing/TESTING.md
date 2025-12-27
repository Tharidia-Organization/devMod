# DevMod Testing Guide

> Last updated: 2025-12-26
> Status: CURRENT (testing guide)
> Audit date: 2025-12-26
> Risk level: MEDIUM (MC-dependent tests use GameTest)

---

## 1. Overview

DevMod testing uses three layers:
- **JUnit 5** for deterministic logic (direct tests).
- **NeoForge GameTest** for Minecraft-dependent behavior.
- **QA flows** via in-game testing screens and progress tracking.

---

## 2. Test Taxonomy

- **Direct tests**: `*DirectTest` classes focus on pure logic that can run without Minecraft.
- **GameTests**: `com.devmod.gametest.*` validate systems that require a live game server.
- **Progressive levels**: some suites use L0 (boot/smoke) through L5 (stress) naming.

---

## 3. Running Tests

```bash
# All JUnit tests
./gradlew test

# Direct tests only (pure logic)
./gradlew test --tests 'com.devmod.*DirectTest'

# Narrow package
./gradlew test --tests 'com.devmod.arena.*'

# GameTests (headless server)
./gradlew runGameTestServer
```

---

## 4. Locations

```
src/test/java/com/devmod/              # JUnit 5 tests
src/main/java/com/devmod/gametest/     # GameTest suites
src/main/resources/data/devmod/structure/ # GameTest templates (*.nbt)
```

QA state is stored under `config/devmod/` (see `com.devmod.util.ConfigPaths`).

---

## 5. Writing Tests

- Prefer direct tests for logic that can run without Minecraft.
- Use GameTests for behaviors that require world, entities, or registries.
- Keep tests deterministic and avoid timing-based flakes.

---

## 6. Reports

JUnit reports are written to:
```
build/reports/tests/test/index.html
```
