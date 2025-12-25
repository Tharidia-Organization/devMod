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

---

## Pending Batches

### Batch 4: Null-safety (Pending)
- Review @Nullable field access patterns
- Add local variable copies where needed

### Batch 5: Comments (Pending)
- Review TODO/FIXME comments
- Add invariant comments to critical sections

### Batch 6: Micro-refactor (Pending)
- Extract helpers only where significantly improves clarity
