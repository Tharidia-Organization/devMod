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

## Pending Batches

### Batch 5: Null-safety (Pending)
- Review @Nullable field access patterns
- Add local variable copies where needed

### Batch 5: Comments (Pending)
- Review TODO/FIXME comments
- Add invariant comments to critical sections

### Batch 6: Micro-refactor (Pending)
- Extract helpers only where significantly improves clarity
