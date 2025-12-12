# P0 Runtime Verification Test

**Purpose:** Prove that with `NDJSON_FALLBACK=false`, NDJSON files are NOT written while DuckDB receives data.

---

## Pre-Test Checklist

Config values in `DuckDBConfig.java`:
```java
public static boolean ENABLED = true;
public static boolean NDJSON_FALLBACK = false;
public static boolean FALLBACK_ON_ERROR = true;
```

---

## Test 1: Filesystem Proof (No NDJSON Written)

### Before Test - Record Timestamps
```bash
# Run from project root
ls -la run/telemetry/endurance.ndjson run/telemetry/player_attributes.ndjson run/telemetry/ability_usage.ndjson 2>&1
# Expected: "No such file or directory" for all three
```

### Run Server (10 minutes)
1. Start server: `./gradlew runServer` or via IDE
2. Join the game
3. Generate these events:
   - **Endurance:** Start quest, complete 2 waves, select 1 perk, trigger combo rank change, add 1 mutator, create/join party, trigger boss ability
   - **Abilities:** Use dash 3x, use dodge 3x
   - **PlayerAttributes:** Wait for 2 periodic snapshots (~10 seconds apart), take damage for health_change

### After Test - Verify No New NDJSON Files
```bash
ls -la run/telemetry/endurance.ndjson run/telemetry/player_attributes.ndjson run/telemetry/ability_usage.ndjson 2>&1
# Expected: Still "No such file or directory" for all three

# Alternative: Check modification times of ALL ndjson files
find run/telemetry -name "*.ndjson" -type f -newer /tmp/test_start_marker 2>/dev/null
# Should return nothing for the 3 target files
```

---

## Test 2: DuckDB Row Count Proof

After the 10-minute test, run these queries against the DuckDB file.

### Connect to DuckDB
```bash
# Install DuckDB CLI if needed: brew install duckdb
duckdb run/telemetry/devmod_telemetry.duckdb
```

### Query: Recent Events (Last 10 Minutes)
```sql
-- ENDURANCE TABLES
SELECT 'endurance_sessions' as tbl, COUNT(*) as cnt FROM endurance_sessions WHERE start_ts > NOW() - INTERVAL '10 minutes'
UNION ALL SELECT 'endurance_waves', COUNT(*) FROM endurance_waves WHERE ts > NOW() - INTERVAL '10 minutes'
UNION ALL SELECT 'endurance_wave_kills', COUNT(*) FROM endurance_wave_kills WHERE ts > NOW() - INTERVAL '10 minutes'
UNION ALL SELECT 'endurance_combos', COUNT(*) FROM endurance_combos WHERE ts > NOW() - INTERVAL '10 minutes'
UNION ALL SELECT 'endurance_perks', COUNT(*) FROM endurance_perks WHERE ts > NOW() - INTERVAL '10 minutes'
UNION ALL SELECT 'endurance_mutators', COUNT(*) FROM endurance_mutators WHERE ts > NOW() - INTERVAL '10 minutes'
UNION ALL SELECT 'endurance_parties', COUNT(*) FROM endurance_parties WHERE ts > NOW() - INTERVAL '10 minutes'
UNION ALL SELECT 'endurance_bosses', COUNT(*) FROM endurance_bosses WHERE ts > NOW() - INTERVAL '10 minutes';

-- PLAYER TABLES
SELECT 'player_snapshots' as tbl, COUNT(*) as cnt FROM player_snapshots WHERE ts > NOW() - INTERVAL '10 minutes'
UNION ALL SELECT 'player_abilities', COUNT(*) FROM player_abilities WHERE ts > NOW() - INTERVAL '10 minutes';

-- COMBAT TABLES (existing, should also have data)
SELECT 'combat_hits' as tbl, COUNT(*) as cnt FROM combat_hits WHERE ts > NOW() - INTERVAL '10 minutes'
UNION ALL SELECT 'combat_deaths', COUNT(*) FROM combat_deaths WHERE ts > NOW() - INTERVAL '10 minutes'
UNION ALL SELECT 'performance_samples', COUNT(*) FROM performance_samples WHERE ts > NOW() - INTERVAL '10 minutes';
```

### Expected Results
- `endurance_sessions`: >= 1
- `endurance_waves`: >= 2
- `endurance_combos`: >= 1
- `endurance_perks`: >= 1
- `endurance_mutators`: >= 1
- `endurance_parties`: >= 1
- `player_snapshots`: >= 2
- `player_abilities`: >= 6

---

## Test 3: Log Proof

Check server logs for:

### Startup Config Log
```
[DuckDB] === TELEMETRY CONFIG ===
[DuckDB]   ENABLED = true
[DuckDB]   NDJSON_FALLBACK = false
[DuckDB]   FALLBACK_ON_ERROR = true
[DuckDB] With NDJSON_FALLBACK=false, NDJSON writes will be SKIPPED
```

### Skip Log (if debug enabled)
```
[Endurance] NDJSON writes SKIPPED (DuckDB primary mode) - skip count: N
```

---

## Test 4: Circuit Breaker Compliance

### Setup
1. Stop server
2. Change `DuckDBConfig.java`:
   ```java
   public static String DB_FILENAME = "/invalid/path/test.duckdb";
   ```
3. Keep `NDJSON_FALLBACK = false`
4. Start server

### Expected Behavior
- Log shows: `[DuckDB] Failed to initialize: ...`
- Log shows: `[DuckDB] DuckDB disabled, NDJSON fallback also disabled (strict mode)`
- **NO** `endurance.ndjson`, `player_attributes.ndjson`, `ability_usage.ndjson` files created
- Telemetry is completely OFF (no writes anywhere)

### Verification
```bash
ls -la run/telemetry/endurance.ndjson run/telemetry/player_attributes.ndjson run/telemetry/ability_usage.ndjson 2>&1
# Expected: "No such file or directory"
```

---

## Definition of Done

P0 is COMPLETE when all of these are true:

| Check | Requirement |
|-------|-------------|
| FS Proof | 0 bytes written to target NDJSON files during test |
| DB Proof | DuckDB tables have row counts >= minimum for each event type |
| Log Proof | Startup log shows config, skip log shows events skipped |
| Circuit Breaker | Invalid path -> no NDJSON fallback (strict mode) |

---

## Quick Verification Script

Run after test:
```bash
echo "=== NDJSON FILES (should not exist or be from before test) ==="
ls -la run/telemetry/endurance.ndjson run/telemetry/player_attributes.ndjson run/telemetry/ability_usage.ndjson 2>&1

echo ""
echo "=== DuckDB FILE ==="
ls -la run/telemetry/devmod_telemetry.duckdb 2>&1

echo ""
echo "=== DUCKDB ROW COUNTS ==="
duckdb run/telemetry/devmod_telemetry.duckdb -c "
SELECT 'endurance_sessions' as tbl, COUNT(*) as cnt FROM endurance_sessions
UNION ALL SELECT 'endurance_waves', COUNT(*) FROM endurance_waves
UNION ALL SELECT 'endurance_perks', COUNT(*) FROM endurance_perks
UNION ALL SELECT 'endurance_combos', COUNT(*) FROM endurance_combos
UNION ALL SELECT 'player_snapshots', COUNT(*) FROM player_snapshots
UNION ALL SELECT 'player_abilities', COUNT(*) FROM player_abilities
ORDER BY tbl;
"
```
