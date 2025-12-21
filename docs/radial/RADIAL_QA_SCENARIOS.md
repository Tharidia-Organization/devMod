# Radial Menu QA Scenarios

Manual test scenarios for validating radial menu behavior across different contexts.

---

## Scenario 1: Singleplayer - Full Access

**Context**: Singleplayer world, player has all permissions

**Steps**:
1. Open radial menu (default keybind)
2. Navigate to Debug category
3. Toggle "Debug Overlay"
4. Navigate to Commands category
5. Execute "Creative Mode"
6. Navigate to Telemetry > Dashboard
7. Click "Open Dashboard"

**Expected Results**:
- [ ] Menu opens within 100ms
- [ ] All categories visible
- [ ] Debug overlay toggles and persists
- [ ] Gamemode changes to creative
- [ ] Dashboard confirmation dialog appears
- [ ] "Open in Browser" works OR "Copy URL" fallback works
- [ ] Telemetry events logged: `radial_menu_opened`, `radial_action_invoked` (x3), `radial_menu_closed`

---

## Scenario 2: Dedicated Server - Non-Admin Player

**Context**: Dedicated server, player without operator permissions

**Steps**:
1. Open radial menu
2. Attempt to navigate to Commands category
3. Attempt to access Arena actions
4. Navigate to HUD toggles
5. Toggle "Impact HUD"

**Expected Results**:
- [ ] Commands category hidden OR actions within show as unavailable
- [ ] Arena actions show as unavailable (grayed out or hidden)
- [ ] HUD toggles work normally (client-side actions)
- [ ] Impact HUD toggles successfully
- [ ] No error messages for blocked actions (graceful degradation)
- [ ] Telemetry logs `radial_action_blocked` for permission-gated attempts

---

## Scenario 3: Dedicated Server - Admin Player

**Context**: Dedicated server, player with operator level 2+

**Steps**:
1. Open radial menu
2. Navigate to Commands > Set Time Day
3. Navigate to Arena > Create
4. Navigate to Telemetry > Export All

**Expected Results**:
- [ ] All admin actions visible and executable
- [ ] Time changes to day
- [ ] Arena creation command executes
- [ ] Telemetry export runs
- [ ] Server chat feedback received for all commands
- [ ] All actions logged with `origin: "radial"`

---

## Scenario 4: Dashboard - Server Not Running

**Context**: Telemetry dashboard server is stopped

**Steps**:
1. Open radial menu
2. Navigate to Telemetry > Dashboard > Open
3. Click action

**Expected Results**:
- [ ] Confirmation screen appears: "Open Telemetry Dashboard?"
- [ ] Dashboard server starts automatically
- [ ] If successful: Browser opens, screen closes
- [ ] If failed: Error message displayed, URL copied to clipboard
- [ ] Status message visible ("Opened" or "Copied to clipboard")
- [ ] Telemetry logs `external_url_opened` or `external_url_copied`

---

## Scenario 5: Dashboard - Desktop.browse() Unavailable

**Context**: Running on headless system or Java without Desktop support

**Steps**:
1. Open radial menu
2. Navigate to Telemetry > Dashboard > Open
3. Click "Open in Browser"

**Expected Results**:
- [ ] Confirmation dialog appears
- [ ] "Open in Browser" shows error message
- [ ] Automatic fallback: URL copied to clipboard
- [ ] Status shows "Desktop not supported - URL copied"
- [ ] Telemetry logs `external_url_opened` with `success: false`
- [ ] Telemetry logs `external_url_copied`

---

## Scenario 6: Action RPC Failure

**Context**: Server-side action fails (e.g., arena creation in invalid location)

**Steps**:
1. Stand in mid-air or invalid location
2. Open radial menu
3. Navigate to Arena > Create
4. Execute action

**Expected Results**:
- [ ] Action executes (not blocked client-side)
- [ ] Server rejects with error message in chat
- [ ] No client-side crash
- [ ] Telemetry logs `radial_action_invoked` (client-side success)
- [ ] Server logs separate failure event

---

## Scenario 7: Toggle State Persistence

**Context**: Testing toggle actions maintain state

**Steps**:
1. Open radial menu
2. Toggle "Debug Overlay" ON
3. Close menu
4. Reopen menu
5. Verify toggle shows as active
6. Toggle OFF
7. Close and reopen
8. Verify toggle shows as inactive

**Expected Results**:
- [ ] Toggle visual state matches actual config state
- [ ] State persists across menu open/close cycles
- [ ] State persists across game restart (config saved)
- [ ] `isActive()` returns correct value

---

## Scenario 8: Destructive Action Confirmation

**Context**: Actions that require confirmation before execution

**Steps**:
1. Open radial menu
2. Navigate to Endurance > Exit Quest (while in quest)
3. Click action

**Expected Results**:
- [ ] Confirmation dialog appears (if `requiresConfirm: true`)
- [ ] "Cancel" returns to menu
- [ ] "Confirm" executes action
- [ ] Telemetry logs with confirmation step noted

*Note: If no confirmation-required actions exist in current build, document this as N/A*

---

## Scenario 9: Hidden Actions Without Permission

**Context**: Visibility gating for permission-restricted actions

**Steps**:
1. Join server as non-admin
2. Open radial menu
3. Count visible actions in Commands category
4. Get admin permissions (`/op`)
5. Reopen radial menu
6. Count visible actions in Commands category

**Expected Results**:
- [ ] Non-admin: Only client-safe actions visible
- [ ] Admin: All server commands visible
- [ ] No "locked" or "unavailable" spam for hidden actions
- [ ] Clean UI with only executable actions shown

---

## Scenario 10: Time-to-First-Action Telemetry

**Context**: Validating menu performance metrics

**Steps**:
1. Open radial menu
2. Wait 2 seconds
3. Execute first action
4. Close menu
5. Check telemetry log

**Expected Results**:
- [ ] `radial_menu_opened` event logged at open time
- [ ] `radial_time_to_first_action` event logged with `timeMs` ~2000
- [ ] `radial_action_invoked` logged for the action
- [ ] `radial_menu_closed` logged with `actionsExecuted: 1`, `durationMs` >= 2000
- [ ] All timestamps are monotonically increasing

---

## Telemetry Validation Checklist

After running scenarios, verify telemetry file contains:

```json
// Example expected events
{"type": "radial_menu_opened", "screenId": "RadialMenuScreenV3", ...}
{"type": "radial_time_to_first_action", "timeMs": 1250}
{"type": "radial_action_invoked", "actionId": "devmod.debug.overlay.toggle", "result": "OK", ...}
{"type": "radial_action_blocked", "actionId": "devmod.command.gamemode.creative", "errorCode": "PRECONDITION_FAILED", ...}
{"type": "radial_menu_closed", "actionsExecuted": 2, "durationMs": 5000}
{"type": "external_url_opened", "url": "http://localhost:8080/dashboard", "success": true}
```

---

## Pass/Fail Criteria

| Scenario | Critical | Notes |
|----------|----------|-------|
| 1 | YES | Core functionality |
| 2 | YES | Permission gating security |
| 3 | YES | Admin functionality |
| 4 | YES | Dashboard accessibility |
| 5 | MEDIUM | Graceful degradation |
| 6 | MEDIUM | Error handling |
| 7 | YES | State consistency |
| 8 | LOW | Confirmation UX |
| 9 | YES | Security - no privilege leak |
| 10 | MEDIUM | Telemetry accuracy |

**Minimum for release**: Scenarios 1, 2, 3, 4, 7, 9 must pass.

---

## Implementation Coverage Checklist

### Scenario 1 (Singleplayer - Full Access) - COVERED
- [x] Menu opens (RadialMenuScreenV3.init())
- [x] All categories visible (getActiveCategories())
- [x] Debug overlay toggles and persists (toggle() + SettingsManager.markDirty())
- [x] Gamemode changes (executeCommand via ActionContext)
- [x] Dashboard confirmation dialog (OpenExternalConfirmScreen)
- [x] Telemetry events logged (logMenuOpened, logTimeToFirstAction, logMenuClosed)

### Scenario 2 (Non-Admin Player) - COVERED
- [x] Commands hidden via visibilityPredicate (DevModActions.hasPermission)
- [x] HUD toggles work (client-side, no permission needed)
- [x] Blocked actions log radial_action_blocked (ActionRegistry.logInvocationExtended)
- [x] No error spam (precondition blocks gracefully)

### Scenario 3 (Admin Player) - COVERED
- [x] All admin actions visible after /op (visibilityPredicate refreshed)
- [x] Telemetry logs origin: "radial" (ActionContext.getOrigin())

### Scenario 4 (Dashboard - Server Not Running) - COVERED
- [x] Confirmation screen appears (OpenExternalConfirmScreen)
- [x] Server starts automatically (TelemetryDashboardServer.start())
- [x] Browser opens or URL copied (Desktop.browse / clipboard fallback)
- [x] Telemetry logged (external_url_opened, external_url_copied)

### Scenario 7 (Toggle Persistence) - COVERED
- [x] Toggle visual state matches config (toggle() activePredicate)
- [x] State persists across menu open/close (config read on each check)
- [x] State persists after restart (SettingsManager.markDirty -> config saved)
- [x] isActive() returns correct value (activePredicate.test())

### Scenario 9 (Hidden Actions Without Permission) - COVERED
- [x] visibilityPredicate hides admin actions (RadialAction.isVisible)
- [x] Visibility gate separate from execution gate
- [x] After /op, actions become visible (visibilityPredicate re-evaluated)

---

## Contract -> Implementation Mapping

| Contract Field | Implementation | Enforced |
|----------------|----------------|----------|
| id | RadialAction.id | YES |
| labelKey | RadialAction.labelKey | YES |
| category | RadialAction.category | YES |
| actionType | RadialAction.actionType | YES |
| visibilityPredicate | RadialAction.visibilityPredicate | YES (NEW) |
| precondition | RadialAction.precondition | YES |
| permissionLevel | RadialAction.permissionLevel | YES (NEW) |
| uiFeedback | RadialAction.uiFeedback | YES (NEW) |
| requiresConfirm | RadialAction.requiresConfirm | YES |
| toggle/activePredicate | RadialAction.toggle + activePredicate | YES |
| commandHint | RadialAction.commandHint | YES |

---

## Legacy Shim Actions

The following action patterns are marked as `@Deprecated` but retained for backward compatibility:

| Pattern | Status | Migration Path |
|---------|--------|----------------|
| RadialAction.command() | LEGACY_SHIM | Use RadialAction.registry() + ActionRegistry |
| RadialAction.custom() | LEGACY_SHIM | Use RadialAction.registry() + ActionRegistry |
| CommandAction (inner class) | LEGACY_SHIM | Use RegistryAction |
| CustomAction (inner class) | LEGACY_SHIM | Use RegistryAction |

All RadialMenuRegistry entries use `RadialMenuItem.registry()` - no legacy patterns in active use.
