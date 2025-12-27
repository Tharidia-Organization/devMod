# Verification Runbook

> Last updated: 2025-12-26
> Status: CURRENT (manual runbook; aligned to repo scripts/tests)
> Purpose: Dedicated server readiness checks

## Build + Smoke

```bash
./gradlew build
./gradlew test
./gradlew runGameTestServer   # server-side validation (recommended)
./gradlew runServer           # manual server sanity (optional)
./gradlew runClient           # client sanity (optional)
```

## Guardrails

### 1) Client import boundary (fast)

```bash
tools/check-client-imports.sh
```

### 2) Client boundary regression (stricter)

```bash
scripts/check-client-boundary.sh
```

### 3) Mixin side lists

```bash
rg -n '"client"' src/main/resources/devmod.mixins.json
rg -n '"mixins"' src/main/resources/devmod.mixins.json
```

### 4) Network ID uniqueness

```bash
./gradlew test --tests 'com.devmod.network.ChannelIdCollisionTest'
```

### 5) Namespace checks

```bash
scripts/architecture-check.sh
```

## Expected Logs (Server)

```bash
LOG=run/logs/latest.log  # Gradle run tasks
# LOG=logs/latest.log    # Packaged server
rg "SpongePowered MIXIN" "$LOG" | rg "Env=SERVER"
rg -c "ClassNotFoundException.*com\\.devmod" "$LOG"
rg -n "RuntimeDistCleaner" "$LOG"
```

Expected:
- Mixin env = SERVER
- ClassNotFoundException count = 0
- RuntimeDistCleaner: no missing class errors
