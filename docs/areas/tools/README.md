# Tools / QA / Autosmoke

> Last updated: 2025-12-26
> Status: NEEDS_VERIFICATION
> Risk Level: MEDIUM (mixed client/server test surfaces)

---

## 1. Purpose

- Provide in-game test commands and GameTest coverage.
- Provide QA UI flows and test progress tracking.
- Provide scheduled autosmoke runs with thresholds and reporting.

---

## 2. Key Components

### GameTest + Harness
- `com.devmod.gametest.DevModGameTests`
- `com.devmod.gametest.L0BootVerificationTests`
- `com.devmod.gametest.InstanceSystemGameTests`
- `com.devmod.gametest.DevModTestStructures`
- `com.devmod.gametest.TestHarnessCommands`

### QA / Testing UI
- `com.devmod.client.ui.hub.TestingHub`
- `com.devmod.client.ui.hub.TestingHubState`
- `com.devmod.client.ui.hub.QuickToolsPanel`
- `com.devmod.client.ui.hub.CategoryPanel`
- `com.devmod.client.ui.hub.TestDetailPanel`
- `com.devmod.client.testing.QATestingScreen`
- `com.devmod.client.testing.QAEventTracker`
- `com.devmod.client.testing.ActiveTestHudOverlay`

### QA Progress + Templates
- `com.devmod.testing.TestCase`
- `com.devmod.testing.TesterProfile`
- `com.devmod.testing.TesterProgress`
- `com.devmod.testing.DynamicTestGenerator`
- `com.devmod.testing.ModDiscoveryService`
- `com.devmod.testing.config.ModTestConfig`
- `com.devmod.testing.config.ConfigurableTestTemplate`

### Autosmoke
- `com.devmod.arena.autosmoke.AutosmokeGuard`
- `com.devmod.arena.autosmoke.AutosmokeScheduler`
- `com.devmod.arena.autosmoke.AutosmokeRunner`
- `com.devmod.arena.autosmoke.AutosmokeThresholds`
- `com.devmod.arena.autosmoke.AutosmokeSizeThresholds`
- `com.devmod.arena.autosmoke.AutosmokeReportHeader`
- `com.devmod.arena.autosmoke.AutosmokeReportWriter`
- `com.devmod.arena.autosmoke.AutosmokeExceptions`

---

## 3. Entrypoints

### /devtest commands (OP level 2)
```
/devtest hud <on|off|toggle>
/devtest hud export|import
/devtest panel <on|off|toggle>
/devtest panelclear
/devtest debug <on|off|toggle>
/devtest debugbox <size>
/devtest debugclear
/devtest info
/devtest qa
/devtest bodypart <part>
/devtest endurance stats
/devtest endurance perks
/devtest endurance smoke
/devtest endurance export <table|all>
/devtest endurance autosmoke
```

Notes:
- Client-side commands route through `com.devmod.client.gametest.TestHarnessClientDelegate`.
- `devtest qa` opens the Testing Hub UI.

---

## 4. Autosmoke Flow

- `AutosmokeGuard` blocks runs when any guard fails (`DEVMOD_ENV=production`, `devmod.autosmoke.enabled=false`, or `.production` marker present).
- `AutosmokeScheduler` schedules daily runs by default (03:00 local) and supports `ScheduleConfig.fromCron(...)` and `triggerNow()`.
- `AutosmokeRunner` applies `AutosmokeThresholds` + `AutosmokeSizeThresholds` and produces `AutosmokeReport` with rollback/residual metrics.
- `AutosmokeReportWriter` persists JSON/CSV report outputs.

---

## 5. Automated Validation

| Behavior | Test |
|---|---|
| Threshold selection + validation | `AutosmokeThresholdsDirectTest` |
| Size categorization + whitelist + failures | `AutosmokeSizeThresholdsDirectTest` |
| Report header hashing + formatting | `AutosmokeReportHeaderDirectTest` |
| Report CSV/JSON + rollup totals | `AutosmokeReportDirectTest` |
| Guard gating | `AutosmokeGuardTest` |
| Runner smoke coverage | `AutosmokeRunnerTest` |
| Scheduler timing | `AutosmokeSchedulerTest` |

---

## Cross-References

- `docs/testing/TEST_HARNESS.md`
- `docs/testing/TESTING.md`
- `docs/testing/TEST_WORLD_SETUP.md`
- `docs/testing/PROGRESSIVE_TEST_PLAN.md`
- `docs/areas/arena/README.md`
