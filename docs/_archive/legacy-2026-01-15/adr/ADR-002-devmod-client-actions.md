# ADR-002: DevModClientActions Architecture

> Last updated: 2025-12-26
> Status: CURRENT (verified against code)

Decision status: Accepted  
Decision date: 2025-12-26  
Context: Quality pass for large classes

---

## Summary

`DevModClientActions` is the centralized registry for client-side actions that drive UI screens, debug tools, telemetry actions, and gameplay utilities.

## Context

DevMod exposes a large set of client actions through the radial menu, keybinds, UI buttons, and commands. A central registry keeps action metadata and preconditions consistent.

## Decision

Keep a single client-side action registry that groups actions by category but registers them through one entrypoint.

## Implementation Notes (Verified)

- `register()` calls:
  - `registerUiActions()` (also calls `ArenaActionRegistry.registerClientActions()`)
  - `registerDebugActions()`
  - `registerConfigActions()` (includes `registerGameDesignConfigActions()`)
  - `registerTelemetryActions()`
  - `registerQuestActions()`
  - `registerEnduranceActions()`
  - `registerAbilityActions()`
- Actions are registered via `ActionRegistry` using `RadialAction.builder(...)`.
- Preconditions are composed with `ActionPreconditions` helpers and local factories:
  - `screenPrecondition()`
  - `developerModePrecondition()`
  - `qaSessionExistsPrecondition()`
  - `qaSessionActivePrecondition()`
  - `qaActiveTestPrecondition()`
- The registry is client-only and is initialized from `DevModClient`.

## Consequences

- Pros: consistent metadata, easy discovery, centralized preconditions.
- Cons: large file and high import count.

## Notes

Future splits can be done by category while keeping `register()` as the unified entrypoint.
