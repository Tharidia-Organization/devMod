# Quality Pass Changelog

## Batch 1: Imports & Utility Classes (2025-12-25)

### Files Modified

#### Import Cleanup & Reordering
- `HitHelper.java` - Removed duplicate wildcard imports, added final + private ctor, reordered imports
- `ClientVFXHelper.java` - Added final + private ctor, reordered imports
- `CompatRegistry.java` - Replaced `java.util.*` with specific imports, reordered
- `ExecutionSystem.java` - Replaced `java.util.*` with specific imports, reordered
- `WeaponTraitRegistry.java` - Replaced `java.util.*` with specific imports, reordered
- `SoulImprint.java` - Replaced `java.util.*` with specific imports, reordered
- `SoulImprintManager.java` - Replaced `java.util.*` with specific imports, reordered

### Import Order Standard Applied
1. `java.*`
2. `javax.*`
3. External libraries (`org.slf4j.*`, `com.google.*`, etc.)
4. `net.minecraft.*`
5. `net.neoforged.*`
6. `com.devmod.*`

---

## Batch 2: Logging Standardization (2025-12-25)

### Files Modified

#### System.out/err → LOGGER Conversion
- `RadialAction.java` - Added LOGGER, converted System.err to LOGGER.error
- `RadialMenuConfig.java` - Added LOGGER, converted 6x System.err to LOGGER.warn
- `GridValidator.java` - Added LOGGER, converted 2x System.out to LOGGER.debug
- `EditorLayout.java` - Added LOGGER, converted System.err to LOGGER.debug
- `StaminaSystemEditor.java` - Added LOGGER, converted System.out to LOGGER.info

### Files Intentionally Unchanged
- `ConsoleAlertChannel.java` - Uses System.out/err by design (console alerting)
- `LegacyCallCheck.java` - CLI tool, System.out/err appropriate for CLI output

---

## Batch 3: Endurance Flow Fixes (2025-12-25)

### Files Modified
- `EnduranceEventHandler.java` - Use per-wave deaths for directive chains, use cumulative totals for perk discoveries, apply Devil's Bargain multiplier to wave rewards
- `EnduranceEventCombat.java` - Track recent critical hits for critical-kill challenges
- `RewardSystem.java` - Apply Devil's Bargain reward multiplier to quest rewards
- `EnduranceEventCombatTest.java` - Smoke test for critical-kill marker window

---

## Batch 4: Additional Wildcard Import Cleanup (2025-12-25)

### Client Endurance Screens
- `QuestDeathScreen.java` - `com.devmod.endurance.*` → explicit `QuestActionPayload`
- `WaveDirectiveScreen.java` - `com.devmod.endurance.*` → explicit payloads
- `WaveCheckpointScreen.java` - `com.devmod.endurance.*` → explicit `ComboSystem`, `QuestActionPayload`
- `PerkSelectionScreen.java` - `com.devmod.endurance.*` → explicit payloads
- `QuestCompletionScreen.java` - `com.devmod.endurance.*` → explicit `QuestCompletionPayload`
- `EnduranceQuestScreen.java` - `com.devmod.endurance.*`, `java.util.*` → explicit imports
- `EnduranceShopScreen.java` - `com.devmod.endurance.*`, `java.util.*` → explicit imports
- `QuestExitConfirmScreen.java` - `com.devmod.endurance.*` → explicit `QuestActionPayload`
- `EnduranceUiCache.java` - `com.devmod.endurance.*` → explicit payloads
- `ClientQuestCache.java` - `com.devmod.endurance.*` → explicit classes
- `ClientPersonalRecordsCache.java` - `com.devmod.endurance.*` → explicit `PersonalRecordsSyncPayload`
- `KitSelectionScreen.java` - `com.devmod.endurance.*`, `net.minecraft.world.item.*`, `java.util.*` → explicit imports

### Client Party
- `InvitePopupScreen.java` - `com.devmod.party.*` → explicit `PartyInvite`, `InviteResponsePayload`, `PartyNotificationPayload`
- `ClientPartyCache.java` - `com.devmod.party.*` → explicit party classes
- `PartyUiCache.java` - `com.devmod.party.*` → explicit `PartyNotificationPayload`
- `PartyScreen.java` - `com.devmod.party.*` → explicit party classes
- `PartyScreenRenderer.java` - `com.devmod.party.*` → explicit `PartyData`, `PartySyncPayload`

### Client Abilities & Attributes
- `ClientStaminaCache.java` - Removed unused `com.devmod.abilities.*`
- `AttributeMonitoringSystem.java` - `com.devmod.attributes.*` → explicit `AttributeLogEntry`
- `AttributeHudOverlay.java` - `com.devmod.attributes.*` → explicit `AttributeLogEntry`
- `TrackedEntity.java` - Removed unused `com.devmod.attributes.*`
- `AttributeRayVisualizer.java` - Removed unused `com.devmod.attributes.*`

### Client Effects & Combat
- `WeaponTrailVFX.java` - Removed unused `com.devmod.combat.*`
- `ShakeManager.java` - Removed unused `com.devmod.effects.*` (ShakeEffect is in same package)
- `TrailManager.java` - `com.devmod.effects.*` → explicit `TrailEffect`
- `PerceptionEventHandler.java` - Removed unused `com.devmod.effects.*`

### Client Arena & Quest
- `ArenaHudKeyBinding.java` - `com.devmod.arena.*` → explicit `ArenaDebugState`
- `ArenaTestWizard.java` - Removed unused `com.devmod.arena.*`
- `QuestHudOverlay.java` - `com.devmod.quest.*` → explicit `QuestData`, `QuestManager`, `QuestTask`

### Summary
- **30+ files** cleaned of wildcard imports
- **137 remaining** wildcard imports (down from 149)
- All tests passing, build successful

---

## Batch 5: Client Package Wildcard Cleanup (2025-12-25)

### Client Testing

- `BadgeTestScreen.java` - Removed unused `com.devmod.testing.*;`
- `ActiveTestHudOverlay.java` - `com.devmod.testing.*;` → explicit `TestCase`
- `IntegratedTestSession.java` - `com.devmod.testing.*;`, `com.devmod.client.overlay.*;` → explicit imports
- `TestingSession.java` - `com.devmod.testing.*;` → explicit `DynamicTestGenerator`, `ModDiscoveryService`, `TestCase`
- `QANotificationSystem.java` - `com.devmod.testing.*;` → explicit `TestCase`, `TesterProfile`
- `QAEventTracker.java` - `com.devmod.testing.*;` → explicit `TestCase`, `TesterProfile`, `TesterProgress`
- `TutorialManager.java` - `com.devmod.testing.*;` → explicit `TestCase`

### Client Network & Events

- `ClientConfigFeedbackPayload.java` - `com.devmod.network.*;` → explicit `MobConfigConfirmPayload`
- `CombatEvents.java` - Removed unused `com.devmod.events.*;`

### Client Telemetry

- `PerformanceProfiler.java` - Removed unused `com.devmod.telemetry.*;`
- `FpsTracker.java` - Removed unused `com.devmod.telemetry.*;`

### Client Compat

- `YaclCompat.java` - Removed redundant `com.devmod.compat.*;` (explicit imports already present)
- `FancyMenuCompat.java` - Removed redundant `com.devmod.compat.*;`
- `ControllingCompat.java` - Removed redundant `com.devmod.compat.*;`

### Client UI Editor

- `RecipeModule.java` - `com.devmod.recipe.*;`, `com.devmod.client.ui.editor.*;` → 10 explicit imports

### Client UI Testing Pages

- `HudSystemsPage.java` - `com.devmod.client.ui.testing.panel.*;` → 6 explicit imports
- `TelemetryPage.java` - `com.devmod.client.ui.testing.panel.*;` → 6 explicit imports
- `OverviewPage.java` - `com.devmod.client.ui.testing.panel.*;` → 4 explicit imports
- `ComponentShowcasePage.java` - `com.devmod.client.ui.testing.panel.*;` → 4 explicit imports
- `CombatPage.java` - `com.devmod.client.ui.testing.panel.*;` → 7 explicit imports
- `EffectsPage.java` - `com.devmod.client.ui.testing.panel.*;` → 6 explicit imports
- `DebugOverlaysPage.java` - `com.devmod.client.rendering.*;`, `com.devmod.client.ui.testing.panel.*;` → 17 explicit imports
- `VoxelLabUiTestScreen.java` - `com.devmod.client.ui.testing.panel.*;` → 8 explicit imports

### Batch 5 Results

- **25 additional files** cleaned
- **100 remaining** wildcard imports (down from 149)
- Build successful

---

## Batch 6: Server-Side Wildcard Cleanup (2025-12-25)

### Endurance & Runtime

- `InstanceArenaManager.java` - `com.devmod.runtime.*;`, `java.util.*;` → explicit `InstanceManager`, `InstanceRegistry`, `Map`, `Optional`, `UUID`

### GameTest

- `L0BootVerificationTests.java` - `com.devmod.runtime.*;`, `net.minecraft.gametest.framework.*;` → 8 explicit runtime imports + 4 explicit gametest imports
- `InstanceSystemGameTests.java` - `com.devmod.runtime.*;`, `net.minecraft.gametest.framework.*;` → 8 explicit runtime imports + 4 explicit gametest imports

### Batch 6 Results

- **3 additional files** cleaned
- **94 remaining** wildcard imports (down from 100)
- Build successful

---

## Batch 7: Runtime & Endurance Core Cleanup (2025-12-25)

### Runtime System

- `InstanceManager.java` - `java.util.*;` → 11 explicit imports (ArrayList, HashSet, Iterator, List, Map, Objects, Optional, Set, UUID, CompletableFuture, ConcurrentHashMap)
- `RecoverySystem.java` - `java.util.*;` → 6 explicit imports (ArrayList, List, Objects, Optional, Set, UUID)
- `DynamicDimensionManager.java` - `java.util.*;` → 10 explicit imports

### Endurance System

- `EnduranceQuestManager.java` - `java.util.*;` → 12 explicit imports (includes Collections, Comparator, HashMap)
- `WaveManager.java` - `java.util.*;` → 10 explicit imports
- `ComboSystem.java` - `java.util.*;` → 9 explicit imports
- `BossWaveSystem.java` - `java.util.*;` → 11 explicit imports (includes EnumMap, Optional)
- `RewardSystem.java` - `java.io.*;`, `java.util.*;` → 16 explicit imports
- `GamificationManager.java` - `java.io.*;`, `java.util.*;` → 15 explicit imports

### Arena System

- `ArenaPolicyRegistry.java` - `java.util.*;` → 10 explicit imports
- `AsyncArenaBuilder.java` - `java.util.*;` → 11 explicit imports (includes Queue, LinkedHashMap)
- `ArenaQuestIntegration.java` - `java.util.*;` → 15 explicit imports

### Batch 7 Results

- **12 additional files** cleaned
- **83 remaining** wildcard imports (down from 94)
- Build successful

---

## Batch 8: Endurance Subsystems (2025-12-25)

### Endurance Challenges

- `EnduranceAnalytics.java` - `java.io.*;`, `java.util.*;` → 9 explicit imports (includes Arrays, Optional)
- `DailyChallengeManager.java` - `java.io.*;`, `java.util.*;` → 11 explicit imports
- `WeeklyChallengeManager.java` - `java.util.*;` → 10 explicit imports

### Perk & Nemesis Systems

- `PerkSynergySystem.java` - `java.util.*;` → 9 explicit imports
- `PerkSynergyWeb.java` - `java.util.*;` → 12 explicit imports (includes LinkedHashMap)
- `NemesisEvolutionManager.java` - `java.util.*;` → 10 explicit imports

### Batch 8 Results

- **6 additional files** cleaned
- **55 remaining** wildcard imports (down from 64)
- Build successful
- **Total reduction: 149 → 55 (63%)**

---

## Batch 9: Null-Safety Hygiene (2025-12-25)

### Files Modified
- `ContextDetector.java` - Annotated nullable state, used local copies for nullable field checks
- `ClientPartyCache.java` - Annotated nullable cache fields, used local copies before null checks

---

## Batch 10: Test Stability (2025-12-25)

### Files Modified
- `DuckDBPureIntegrationTest.java` - Make insert latency threshold configurable (default 1.0ms) to reduce environment flakiness

---

## Pending Batches

### Batch 11: Comments (Pending)
- Review TODO/FIXME comments
- Add invariant comments to critical sections

### Batch 12: Micro-refactor (Pending)
- Extract helpers only where significantly improves clarity
