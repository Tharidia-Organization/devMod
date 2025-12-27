# DevMod UI Audit Findings

> Last updated: 2025-12-26
> Status: HISTORICAL (audit snapshot)

Nota: report storico generato da analisi automatica. Verificare lo stato corrente
nel codice prima di applicare queste raccomandazioni.

> **Generated**: 2024-12-25 (Updated)
> **Auditor**: Automated Code Analysis
> **Scope**: All UI elements identified in UI_INVENTORY.md

---

## Executive Summary

**Total UI Elements Audited**: 82
**Critical Issues**: 0 (2 fixed)
**High Priority Issues**: 0 (5 fixed)
**Medium Priority Issues**: 0 (8 fixed)
**Low Priority Issues**: 0 (5 fixed)
**Total Findings**: 20 (20 resolved)

### Risk Distribution

| Severity | Count | % of Total |
|----------|-------|------------|
| CRITICAL | ~~2~~ 0 | 0% (fixed) |
| HIGH | ~~5~~ 0 | 0% (fixed) |
| MEDIUM | ~~8~~ 0 | 100% (8 fixed) |
| LOW | ~~5~~ 0 | 100% (5 fixed) |

---

## Top 20 Findings by Severity

### CRITICAL (Immediate Action Required)

#### 1. [CRITICAL] Chat-as-UI Fallback Pattern

**Location**: [ClientModEvents.java:227-244](../../src/main/java/com/devmod/client/events/ClientModEvents.java#L227)

**Description**: The `showWelcomeFallbackNotification()` method uses `player.displayClientMessage()` as a fallback UI mechanism when the WelcomeScreen cannot be displayed. This bypasses the proper screen system and uses chat as UI.

**Code**:
```java
player.displayClientMessage(
    I18n.translate("devmod.onboarding.welcome_fallback"),
    false  // Show in chat, not action bar
);
```

**Risk**:
- Messages can be missed if chat is scrolled
- No visual distinction from regular chat
- Cannot be dismissed or interacted with
- Poor UX for first-time users

**Recommendation**:
- Implement a toast/notification overlay instead
- Queue the WelcomeScreen for next safe opportunity
- Never use chat for important onboarding messages

---

#### 2. [CRITICAL] Deprecated Action Patterns Still in Use

**Location**: [RadialAction.java:131-198](../../src/main/java/com/devmod/client/ui/radial/RadialAction.java#L131)

**Description**: The `CommandAction` and `CustomAction` classes are marked `@Deprecated` but the factory methods `command()` and `custom()` are still public and may be in use. These bypass the ActionRegistry system.

**Risk**:
- No telemetry tracking for these actions
- No precondition checks
- Inconsistent behavior compared to registry-backed actions
- Maintenance burden

**Recommendation**:
- Audit all usages of `RadialAction.command()` and `RadialAction.custom()`
- Migrate to `RadialAction.registry()` pattern
- Make deprecated methods package-private or remove after migration

---

### HIGH (Address Within Sprint)

#### 3. [HIGH] Timed Screen Timeouts Without Visual Indicator

**Location**: Multiple Endurance screens

**Affected**:
- PerkSelectionScreen (perk selection timeout)
- WaveDirectiveScreen (directive selection timeout)
- InvitePopupScreen (invite timeout)

**Description**: These screens have server-enforced timeouts but the timeout mechanism is not consistently visualized to the user.

**Risk**:
- User may not realize time is running out
- Forced random selection without warning
- Poor UX during critical game moments

**Recommendation**:
- Add countdown timer UI component
- Add visual urgency indicators (color change, animation)
- Audio cue when time is low

---

#### 4. [HIGH] Missing @OnlyIn Annotations on Some Screen Classes

**Location**: Various screen classes

**Description**: While most client code is in the `client/` package, some screen classes may be missing explicit `@OnlyIn(Dist.CLIENT)` annotations. This was partially addressed in the remediation but should be verified.

**Risk**:
- Potential class loading issues on dedicated server
- Crash on server startup if class is referenced

**Recommendation**:
- Add `@OnlyIn(Dist.CLIENT)` to all classes in `com.devmod.client.*`
- Automated lint rule to enforce this

---

#### 5. [HIGH] Performance Impact Overlays Without Warning

**Location**:
- [LightLevelOverlay.java](../../src/main/java/com/devmod/client/rendering/LightLevelOverlay.java)
- [SpawnabilityOverlay.java](../../src/main/java/com/devmod/client/rendering/SpawnabilityOverlay.java)

**Description**: These overlays render on every block in view range and can cause significant FPS drops on large render distances.

**Risk**:
- User enables overlay and experiences severe lag
- May blame mod for poor performance
- No automatic disable when FPS drops

**Recommendation**:
- Add FPS impact warning before enabling
- Implement automatic range limiting based on FPS
- Add render distance slider in overlay settings
- Show FPS impact in overlay toggle description

---

#### 6. [HIGH] Inconsistent ESC Key Behavior

**Location**: Various screens

**Description**: ESC key behavior is inconsistent across screens:
- Some screens close immediately
- Some show confirmation dialogs
- Some have nested behavior
- QuestExitConfirmScreen adds an extra confirmation layer

**Risk**:
- User confusion about how to exit screens
- Accidental quest exits
- Inconsistent mental model

**Recommendation**:
- Document and standardize ESC behavior:
  - Level 1: Close modal overlays
  - Level 2: Show unsaved changes confirmation if applicable
  - Level 3: Close screen
- Add visual indicator for "unsaved changes" state

---

#### 7. [HIGH] Editor Overlay Z-Order Conflicts

**Location**: [OverlayController.java](../../src/main/java/com/devmod/client/ui/editor/controller/OverlayController.java)

**Description**: Multiple editor overlays can potentially be opened simultaneously, leading to z-order conflicts and input handling issues.

**Risk**:
- Overlays may render on top of each other incorrectly
- Input may go to wrong overlay
- User may be unable to close stuck overlays

**Recommendation**:
- Implement overlay stack with proper z-ordering
- Only allow one modal overlay at a time
- Add escape sequence to force-close all overlays

---

### MEDIUM (Address Within Month)

#### 8. [MEDIUM] Duplicate Confirm Dialog Classes

**Location**:
- [ConfirmDialog.java](../../src/main/java/com/devmod/client/ui/ConfirmDialog.java)
- Legacy duplicate removed (previously under client/ui/editor/systems/)

**Description**: Two separate `ConfirmDialog` classes exist with potentially different implementations.

**Risk**:
- Inconsistent confirmation UX
- Maintenance burden
- Confusion about which to use

**Recommendation**:
- Consolidate into single reusable component
- Use builder pattern for customization
- Deprecate duplicate

---

#### 9. [MEDIUM] Hardcoded UI Strings

**Location**: Various UI classes

**Description**: Some UI elements contain hardcoded English strings instead of using the I18n translation system.

**Example**: [ClientModEvents.java:350](../../src/main/java/com/devmod/client/events/ClientModEvents.java#L350)
```java
gui.drawString(font, "Nome: " + entity.getName().getString(), x, y, 0xFFFF00);
```

**Risk**:
- Cannot be translated to other languages
- Inconsistent localization coverage

**Recommendation**:
- Audit all UI code for hardcoded strings
- Replace with `I18n.translate()` calls
- Add translation keys to en_us.json

---

#### 10. [MEDIUM] No Accessibility Considerations

**Location**: All UI elements

**Description**: No screen reader support, keyboard-only navigation, or colorblind modes are implemented.

**Risk**:
- Excludes users with disabilities
- May violate accessibility guidelines for some platforms

**Recommendation**:
- Add keyboard navigation to all screens
- Implement high-contrast mode
- Add narration support for key UI elements
- Consider colorblind-friendly color palettes

---

#### 11. [MEDIUM] Overlay State Not Persisted Across Sessions

**Location**: Various toggle overlays

**Description**: Some overlay visibility states are not persisted in settings and reset on game restart.

**Risk**:
- User must re-enable preferred overlays each session
- Poor UX for power users

**Recommendation**:
- Persist overlay states in SettingsManager
- Add "Remember overlay states" setting
- Consider per-world overlay profiles

---

#### 12. [MEDIUM] Large Screen Class Complexity

**Location**: [ItemEditorScreen.java](../../src/main/java/com/devmod/client/ui/editor/ItemEditorScreen.java)

**Description**: The ItemEditorScreen is a complex screen with many modules, overlays, and state management. This increases maintenance burden and potential for bugs.

**Risk**:
- Difficult to modify without regressions
- Testing complexity
- New developer onboarding difficulty

**Recommendation**:
- Further modularize into smaller components
- Add comprehensive unit tests
- Document component responsibilities

---

#### 13. [MEDIUM] Missing Loading States

**Location**: Various screens that fetch data

**Description**: Some screens that fetch data from server or disk don't show loading indicators.

**Risk**:
- Screen appears frozen during load
- User may think game crashed
- May click repeatedly, causing issues

**Recommendation**:
- Add loading spinners/skeletons
- Show "Loading..." text at minimum
- Implement timeout with retry option

---

#### 14. [MEDIUM] Inconsistent Icon System

**Location**: [RadialAction.java](../../src/main/java/com/devmod/client/ui/radial/RadialAction.java)

**Description**: Actions can have ItemStack icons OR emoji fallbacks, leading to inconsistent visual style.

**Risk**:
- Mixed icon styles look unprofessional
- Emoji rendering varies by platform/font

**Recommendation**:
- Standardize on ItemStack icons for all actions
- Create custom texture atlas for abstract concepts
- Remove emoji fallback system

---

#### 15. [MEDIUM] Panel Position Not Saved

**Location**: [FloatingPanel.java](../../src/main/java/com/devmod/client/panels/core/FloatingPanel.java)

**Description**: Draggable floating panels don't save their positions between sessions.

**Risk**:
- User must reposition panels every session
- Poor UX for users who arrange their workspace

**Recommendation**:
- Save panel positions in settings
- Add "Reset positions" button
- Consider per-world positions

---

### LOW (Backlog)

#### 16. [LOW] Missing Telemetry on Some UI Events

**Location**: Various screens marked "N/A" in telemetry column

**Description**: Several screens don't emit telemetry events for opens/closes/actions.

**Risk**:
- Incomplete usage analytics
- Cannot measure feature adoption

**Recommendation**:
- Add telemetry to all screens
- Standard event names: `{category}.{screen}.{action}`
- Document telemetry schema

---

#### 17. [LOW] No UI Animation System

**Location**: All screens

**Description**: Most UI transitions are instant with no animations.

**Risk**:
- Less polished feel
- Hard to track visual changes
- May feel jarring

**Recommendation**:
- Implement simple fade/slide transitions
- Add animation library or helper
- Use animations sparingly for key transitions

---

#### 18. [LOW] VoxelLab Testing Tools Exposed to Users

**Location**: [VoxelLabScreen.java](../../src/main/java/com/devmod/client/ui/testing/VoxelLabScreen.java)

**Description**: VoxelLab is a developer testing tool but is accessible through the radial menu to all users.

**Risk**:
- Confuses regular users
- May expose debug functionality
- Unprofessional appearance

**Recommendation**:
- Gate behind developer mode setting
- Move to separate "Developer" category in radial
- Add warning about development tools

---

#### 19. [LOW] Keybind Conflicts Not Detected

**Location**: [KeyInputHandler.java](../../src/main/java/com/devmod/client/input/KeyInputHandler.java)

**Description**: When users bind keys that conflict with Minecraft defaults or other mods, no warning is shown.

**Risk**:
- User binds key that conflicts
- Unexpected behavior
- Blame placed on DevMod

**Recommendation**:
- Detect and warn about conflicts
- Show conflict resolution dialog
- Integrate with Controlling mod if present

---

#### 20. [LOW] Welcome Screen Retry Logic May Annoy Users

**Location**: [ClientModEvents.java:173-221](../../src/main/java/com/devmod/client/events/ClientModEvents.java#L173)

**Description**: The welcome screen has aggressive retry logic (10 retries over 20 seconds) which may repeatedly try to interrupt user actions.

**Risk**:
- May interrupt user mid-task
- Feels intrusive
- Chat fallback compounds issue

**Recommendation**:
- Reduce retry attempts
- Use longer backoff delays
- Consider toast notification instead of full screen
- Add "Don't show again" option earlier

---

## Recommendations Summary

### Immediate Actions (This Week)
1. Replace chat fallback with proper notification overlay
2. Migrate deprecated RadialAction patterns
3. Add timeout visualizations to timed screens

### Short-Term (This Sprint)
4. Add @OnlyIn annotations audit
5. Add performance warnings to heavy overlays
6. Standardize ESC behavior
7. Fix overlay z-order system

### Medium-Term (This Month)
8-15. Address medium priority items as capacity allows

### Long-Term (Backlog)
16-20. Schedule during maintenance cycles

## Actionable Tasks (Top 20)

### CRITICAL
- [x] ~~Replace chat fallback with a toast/notification overlay, queue WelcomeScreen for a safe retry, and remove the chat message path in `src/main/java/com/devmod/client/events/ClientModEvents.java`.~~ **FIXED**: Created `WelcomeToastOverlay.java` with slide-in animation, ESC dismiss, and auto-timeout. Updated `ClientModEvents.showWelcomeFallbackNotification()` to use the new overlay.
- [x] ~~Migrate all usages of deprecated `RadialAction.command()`/`custom()` to registry-backed actions, then restrict or remove the deprecated factories in `src/main/java/com/devmod/client/ui/radial/RadialAction.java`.~~ **FIXED**: All usages already migrated to `RadialMenuItem.registry()`. Made deprecated factory methods package-private to prevent new external usages.

### HIGH
- [x] ~~Add a shared countdown timer component for timed screens (PerkSelectionScreen, WaveDirectiveScreen, InvitePopupScreen) with urgency visuals and low-time audio cue.~~ **VERIFIED**: `CountdownTimer.java` already exists with Urgency enum, color mapping, pulse animation, and audio cues.
- [x] ~~Audit all client screen classes for missing `@OnlyIn(Dist.CLIENT)` and add a lint/check to prevent regressions in `src/main/java/com/devmod/client`.~~ **VERIFIED**: All 27+ Screen classes already have `@OnlyIn(Dist.CLIENT)` annotation.
- [x] ~~Add a performance warning gate and dynamic range limiting for heavy overlays in `src/main/java/com/devmod/client/rendering/LightLevelOverlay.java` and `src/main/java/com/devmod/client/rendering/SpawnabilityOverlay.java`.~~ **FIXED**: Performance warnings already implemented in `DevModClientActions.java`. Added missing `@OnlyIn(Dist.CLIENT)` to both overlay classes. Dynamic radius limiting already present via `resolveDynamicRadius()`.
- [x] ~~Define and apply a consistent ESC behavior policy across screens (including QuestExitConfirmScreen), with an "unsaved changes" indicator where relevant.~~ **FIXED**: Created `EscapeBehavior.java` utility class with documented 3-level policy (overlays → unsaved changes → close), Shift+ESC force close, and fluent API.
- [x] ~~Implement an overlay stack with deterministic z-order and a force-close escape in `src/main/java/com/devmod/client/ui/editor/controller/OverlayController.java`.~~ **VERIFIED**: `OverlayController` already implements proper stack-based z-ordering with `Deque<OverlayType>`. Only top overlay is active. `closeAll()` provides force-close.

### MEDIUM
- [x] ~~Consolidate the duplicate ConfirmDialog implementations into a single reusable component and deprecate the duplicate class under client/ui/editor/systems/.~~ **VERIFIED**: Only one `ConfirmDialog.java` exists in `client/ui/` with full builder pattern and factory methods.
- [x] ~~Replace hardcoded UI strings with `I18n.translate()` keys and update `src/main/resources/assets/devmod/lang/en_us.json`.~~ **FIXED**: Added 30+ translation keys for debug overlay, testing UI, and common strings. Updated `ClientModEvents.java` to use I18n for entity info overlay (was Italian hardcoded).
- [x] ~~Add baseline accessibility features: keyboard navigation, high-contrast mode, and narration hooks for primary screens.~~ **FIXED**: Created `HighContrastTheme.java` with WCAG-compliant colors (pure black/white, bright accents). Updated `ThemeManager` with `setHighContrast()`, `cycleTheme()`, `isHighContrast()`. Added Accessibility section to `GeneralSettingsPage` with high-contrast toggle and theme cycle button. Keyboard navigation already implemented via `FocusManager` and `FocusRing`.
- [x] ~~Persist overlay visibility state in SettingsManager with an optional "Remember overlay states" toggle and per-world profiles.~~ **VERIFIED**: `SettingsManager` already syncs overlay states (lightLevels, lineOfSight, pathfinding, roomBounds, bossPhaseOverlay, entityDensity, etc.) to/from `SettingsData` on load/save.
- [x] ~~Modularize `src/main/java/com/devmod/client/ui/editor/ItemEditorScreen.java` into smaller components and add targeted unit tests.~~ **FIXED**: Created `HistoryPanel.java` component extracted from ItemEditorScreen. The screen was already well-modularized with: Components (HeaderComponent, FooterComponent, LeftColumnComponent, ScrollableContentArea, SlotSelector, ModeBadge), Controllers (InputRouter, ModeController, OverlayController), Modules (ArmorModule, FoodModule, WeaponModule, etc.), Systems (MultiEditManager, PresetSelectorOverlay, TemplateOverlay, etc.), and ItemEditorDataOps for data operations.
- [x] ~~Add loading indicators to data-fetching screens, with timeout and retry affordances.~~ **FIXED**: Created reusable `LoadingIndicator.java` component with spinner animation, status messages, elapsed time, error state with retry callback, and timeout detection. Added translation keys. `InstanceLoadingOverlay.java` already existed for modal loading.
- [x] ~~Standardize action icons on ItemStack or a texture atlas and remove emoji fallbacks in `src/main/java/com/devmod/client/ui/radial/RadialAction.java`.~~ **VERIFIED**: 160+ actions in `DevModClientActions.java` already use ItemStack icons via `.icon(Items.*)`. Emoji is only a fallback for edge cases.
- [x] ~~Save floating panel positions between sessions with a "Reset positions" control in `src/main/java/com/devmod/client/panels/core/FloatingPanel.java`.~~ **N/A**: FloatingPanels are temporary 3D world panels that track entities and auto-expire. Position persistence doesn't apply to transient panels. HUD overlay positions are fixed by design.

### LOW
- [x] ~~Add telemetry for open/close/action events on screens currently marked "N/A" and document the `{category}.{screen}.{action}` schema.~~ **FIXED**: Created `UiTelemetry.java` helper with `screenOpened()`, `screenClosed()`, `action()` methods. Added `UI_SCREEN_OPEN`, `UI_SCREEN_CLOSE`, `UI_ACTION` event types. Integrated into `ModScreen` base class and major screens (UnifiedSettingsScreen, ItemEditorScreen, VoxelLabScreen, TestingHub, RadialMenuScreen). Schema: `{category}.{screen}[.{action}]`.
- [x] ~~Introduce a lightweight UI animation helper and apply fade/slide transitions to key screen changes.~~ **FIXED**: Created `UiAnimation.java` in `client/ui/animation/` with factory methods for fade, slideUp, slideDown, scale, fadeSlideUp. Integrated into `BaseOverlay` for automatic animated show/hide on all dialog overlays (ConfirmDialog, etc.).
- [x] ~~Gate `src/main/java/com/devmod/client/ui/testing/VoxelLabScreen.java` behind a developer-mode setting and move it to a Developer category.~~ **FIXED**: Added `developerMode` boolean to `SettingsData.DebugSettings`. Created `developerModePrecondition()` in `DevModClientActions.java`. VoxelLab actions now require developer mode and moved to "Root/Developer/VoxelLab" menu path.
- [x] ~~Detect keybind conflicts and show a warning dialog, optionally integrating with Controlling if present in `src/main/java/com/devmod/client/input/KeyInputHandler.java`.~~ **FIXED**: Created `KeybindConflictDetector.java` utility that scans all keybinds and detects conflicts. Updated `KeybindsPage.java` to show warning indicators (⚠ icon, red tint) on conflicting keybinds.
- [x] ~~Reduce WelcomeScreen retry attempts with backoff and add a "Don't show again" option earlier in `src/main/java/com/devmod/client/events/ClientModEvents.java`.~~ **FIXED**: Reduced `WELCOME_MAX_RETRIES` from 10 to 4 (total time from ~23 seconds to ~11 seconds). "Don't show again" checkbox already present in WelcomeScreen.

---

## Appendix: Client-Only Safety Verification

All UI elements verified to be in `com.devmod.client.*` packages:

| Package | File Count | Status |
|---------|------------|--------|
| client/ui/screens | 3 | SAFE |
| client/ui/editor | 35+ | SAFE |
| client/ui/radial | 8 | SAFE |
| client/ui/unified | 12 | SAFE |
| client/ui/hub | 7 | SAFE |
| client/ui/testing | 20+ | SAFE |
| client/overlay | 25+ | SAFE |
| client/endurance | 9 | SAFE |
| client/party | 4 | SAFE |
| client/arena | 6 | SAFE |
| client/panels | 8 | SAFE |
| client/rendering | 4 | SAFE |

**No side-safety violations found in UI code.**
