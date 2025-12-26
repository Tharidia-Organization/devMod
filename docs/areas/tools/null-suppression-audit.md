# Null-Suppression Audit Plan

> Last updated: 2025-12-26
> Status: PLANNING (not active; excluded from validation)

Goal: remove `@SuppressWarnings("null")` (and similar unchecked-null issues) across the project with deterministic coverage and no regressions.

## Inventory (from `rg '@SuppressWarnings("null")'`)

- HUD/Overlays: `EnduranceQuestOverlay`, `TokenGainOverlay`, `AttributeHudOverlay`, `ImpactHudOverlay`, `QuickHelpOverlay`, `BossPhaseOverlay`, `ImpactVFX`, `Impact3DRenderer`, `Impact3DPanel`, `Impact3DPanelManager`, `StaminaHudOverlay`, `EntityDensityOverlay`, `PartyHudOverlay`, `SkillEfficacyOverlay`, `EconomyOverlay`, `QuestHudOverlay`, `Render overlays (LineOfSightVisualizer, LightLevelOverlay, EntityInfoOverlay, SpawnabilityOverlay, DebugRenderer, BodyPartRenderer, AggroRangeVisualizer, HeatmapVisualizer)`, `WorldRenderEvents`.
- UI Screens/Wizards: `MobConfigScreen`, `TelemetryDashboardScreen`, `WelcomeScreen`, `RoomBoundsEditorScreen`, `TestingHub`, `QuickTestWizard`, `UnifiedSettingsScreen`, `MobConfigPage`, `DebugOverlaysPage`, `VisualizersPage`, `RadialMenuScreenV3`, `RadialHubRenderer`, `RadialGeometry`, `RadialCategoryRenderer`, `QuickToolsPanel`, `CategoryPanel`, `WaveCheckpointScreen`, `QuestExitConfirmScreen`.
- Telemetry/Analytics: `TelemetryService`, `PerformanceProfiler`, `TelemetryEvents`, `FightSessionService`, `FpsTracker`.
- Combat/Endurance/Systems: `WeaponTrailVFX`, `WeaponConfigManager`, `EnduranceQuestRegistry`, `EnduranceEventHandler`, `EnduranceQuestManager`, `EnduranceQuestScreen`, `EnduranceAnalytics`, `ComboSystem`, `RecoverySystem`, `DevModTestStructures`.
- Testing/Stats: `DamageStatistics`, `ModInteractionTracker`, `EnchantmentStatistics`, `KillStatistics`, `EnvironmentalDamageStats`, `PotionStatistics`, `ActiveTestHudOverlay`, `QATestingScreen`, `DevModTestStructures`.
- Rendering/Shader: `ShieldShaderRegistry`, `EnergyShieldRenderer`, `PathfindingDebugger`, `HeatmapVisualizer`, `Radial rendering helpers`, `BodyPartRenderer`, `DebugRenderer`.
- Misc: `UIConstants` (nested suppression), `MobEquipmentScreen`, `RoomBoundsEditorScreen`, `WelcomeScreen`, `WorldRenderEvents`.

## Phased Refactor Plan

1) **Rendering Core Pass** (high risk, isolated types): `ShieldShaderRegistry`, `EnergyShieldRenderer`, `LineOfSightVisualizer`, `HeatmapVisualizer`, `BodyPartRenderer`, `DebugRenderer`, `PathfindingDebugger`, `AggroRangeVisualizer`, `SpawnabilityOverlay`, `LightLevelOverlay`, `EntityInfoOverlay`, `WorldRenderEvents`.
2) **HUD/Overlay Pass**: all Impact/Endurance/Quest overlays and HUD widgets listed above. Normalize `Objects.requireNonNull`, early returns, and local finals after checks; remove suppressions.
3) **UI Screen Pass**: main screens/wizards (MobConfig, TelemetryDashboard, Welcome, TestingHub, UnifiedSettings, Radial menu renderers). Focus on event handlers, render methods, and optional fields.
4) **Telemetry/Analytics Pass**: `TelemetryService`, `PerformanceProfiler`, `TelemetryEvents`, `FightSessionService`, `FpsTracker`.
5) **Combat/Systems Pass**: `WeaponTrailVFX`, `WeaponConfigManager`, Endurance systems/registry/screen/analytics/ComboSystem/RecoverySystem, `DevModTestStructures`.
6) **Testing/Stats Pass**: all `*Statistics`, `ActiveTestHudOverlay`, `QATestingScreen`, etc.

## Execution Rules

- Replace suppressions with explicit null-handling (`Objects.requireNonNull`, early returns, guards).
- Prefer local `final` variables post-null-check to satisfy analysis.
- Keep method contracts: annotate parameters with `@Nonnull/@Nullable` where appropriate instead of suppressing.
- Avoid behavioral change unless required for safety; add minimal comments only for non-obvious guards.
- After each phase, re-run static analysis (IDE/compile) and prune leftover suppressions in scope.

## Scheduling

- Day 1: Rendering Core Pass + HUD/Overlay Pass (Impact & Endurance overlays).
- Day 2: UI Screen Pass + Telemetry/Analytics Pass.
- Day 3: Combat/Systems Pass + Testing/Stats Pass + final sweep (`rg '@SuppressWarnings("null")'`).  
- Deliver interim diffs per phase to keep review manageable.

## Open Questions

- Acceptable behavior when external APIs return null? (e.g., Minecraft render helpers). If yes, keep guard-and-return; if not, wrap with requireNonNull.
- Any modules to exempt due to imminent rewrite? (mark in plan to skip). 
