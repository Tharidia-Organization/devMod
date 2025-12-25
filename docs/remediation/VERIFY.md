# Verification Runbook

**Last Updated:** 2025-12-25  
**Purpose:** Dedicated server readiness checks

## Build + Smoke

```bash
./gradlew build
./gradlew runServer
./gradlew runClient
./gradlew test
```

## Guardrails

### 1) Client import boundary

```bash
tools/check-client-imports.sh
```

### 2) Mixin side lists

```bash
grep -A20 '"client"' src/main/resources/devmod.mixins.json
grep -A10 '"mixins"' src/main/resources/devmod.mixins.json
```

### 3) Network ID uniqueness

```bash
./gradlew test --tests com.devmod.network.ChannelIdCollisionTest
```

### 4) No legacy namespace

```bash
grep -rn "com\\.frenkvs" src/
```

## Expected Logs (Server)

```bash
grep "SpongePowered MIXIN" run/logs/latest.log | grep "Env=SERVER"
grep -c "ClassNotFoundException.*com\\.devmod" run/logs/latest.log
grep -n "RuntimeDistCleaner" run/logs/latest.log
```

Expected:
- Mixin env = SERVER
- ClassNotFoundException count = 0
- RuntimeDistCleaner: no missing class errors
