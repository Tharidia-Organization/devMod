# Client Boundary Audit - DevMod

> Last updated: 2025-12-22
> Status: ARCHIVED (superseded by `docs/areas/client_server/README.md`)
> Note: This audit captures historical migration notes and legacy package paths.

**Goal:** Ensure all client-only code is properly isolated to prevent dedicated server crashes

---

## Summary

| Category | Files | Status |
|----------|-------|--------|
| Already in `client/` package | 9 | OK |
| Files with `@OnlyIn(Dist.CLIENT)` | 18 | Partial |
| Client imports in non-client packages | ~150 | CRITICAL |

---

## Critical Files - Immediate Server Crash Risk

These files define client-only classes (KeyMapping, Screen) but are NOT in client packages:

### 1. KeyInputHandler - CRITICAL
| File | Problem | Impact | Fix |
|------|---------|--------|-----|
| `com.devmod.KeyInputHandler` | Defines 49 KeyMapping fields | Server crash on class load | Move to `client.input` |

### 2. Screen Classes - CRITICAL
| File | Problem | Impact | Fix |
|------|---------|--------|-----|
| `com.devmod.arena.ui.QuickTestWizard` | extends Screen | Server crash | Move to `arena.client.ui` |
| `com.devmod.ui.WelcomeScreen` | extends Screen | Server crash | Move to `client.ui` |
| `com.devmod.ui.ModScreen` | extends Screen | Server crash | Move to `client.ui` |
| `com.devmod.ui.OpenExternalConfirmScreen` | extends Screen | Server crash | Move to `client.ui` |
| `com.devmod.ui.RoomBoundsEditorScreen` | extends Screen | Server crash | Move to `client.ui` |
| `com.devmod.ui.unified.UnifiedSettingsScreen` | extends Screen | Server crash | Move to `client.ui.unified` |
| `com.devmod.ui.radial.RadialMenuScreenV3` | extends Screen | Server crash | Move to `client.ui.radial` |
| `com.devmod.ui.radial.RadialActionDetailScreen` | extends Screen | Server crash | Move to `client.ui.radial` |
| `com.devmod.ui.testing.VoxelLabScreen` | extends Screen | Server crash | Move to `client.ui.testing` |
| `com.devmod.ui.testing.VoxelLabUiTestScreen` | extends Screen | Server crash | Move to `client.ui.testing` |
| `com.devmod.ui.hub.TestingHub` | extends Screen | Server crash | Move to `client.ui.hub` |
| `com.devmod.ui.wizard.QuickTestWizard` | extends Screen | Server crash | Move to `client.ui.wizard` |
| `com.devmod.ui.editor.ItemEditorScreen` | extends Screen | Server crash | Move to `client.ui.editor` |
| `com.devmod.ui.editor.StaminaSystemEditor` | extends Screen | Server crash | Move to `client.ui.editor` |
| `com.devmod.testing.QATestingScreen` | extends Screen | Server crash | Move to `client.testing` |
| `com.devmod.testing.BadgeTestScreen` | extends Screen | Server crash | Move to `client.testing` |
| `com.devmod.TelemetryDashboardScreen` | extends Screen | Server crash | Move to `client.ui` |
| `com.devmod.MobConfigScreen` | extends Screen | Server crash | Move to `client.ui` |
| `com.devmod.MobEquipmentScreen` | extends Screen | Server crash | Move to `client.ui` |
| `com.devmod.party.PartyScreen` | extends Screen | Server crash | Move to `client.party` |
| `com.devmod.party.InvitePopupScreen` | extends Screen | Server crash | Move to `client.party` |
| `com.devmod.endurance.EnduranceQuestScreen` | extends Screen | Server crash | Move to `client.endurance` |
| `com.devmod.endurance.KitSelectionScreen` | extends Screen | Server crash | Move to `client.endurance` |
| `com.devmod.endurance.PerkSelectionScreen` | extends Screen | Server crash | Move to `client.endurance` |
| `com.devmod.endurance.WaveDirectiveScreen` | extends Screen | Server crash | Move to `client.endurance` |
| `com.devmod.endurance.QuestCompletionScreen` | extends Screen | Server crash | Move to `client.endurance` |
| `com.devmod.endurance.EnduranceShopScreen` | extends Screen | Server crash | Move to `client.endurance` |
| `com.devmod.endurance.QuestExitConfirmScreen` | extends Screen | Server crash | Move to `client.endurance` |
| `com.devmod.endurance.WaveCheckpointScreen` | extends Screen | Server crash | Move to `client.endurance` |
| `com.devmod.endurance.QuestDeathScreen` | extends Screen | Server crash | Move to `client.endurance` |

---

## High Risk - UI Components

These use `Minecraft.getInstance()` or `GuiGraphics`:

### UI Package
| File | Client Imports |
|------|---------------|
| `ui.AxiomRenderer` | GuiGraphics, Font |
| `ui.ConfirmDialog` | Font, GuiGraphics |
| `ui.DebugOverlay` | GuiGraphics, Minecraft |
| `ui.HelpOverlay` | Font, GuiGraphics |
| `ui.UIConstants` | Minecraft, SoundInstance |
| `ui.scroll.ScrollManager` | GuiGraphics |
| `ui.components.ScrollableArea` | GuiGraphics |

### UI Unified Pages
| File | Client Imports |
|------|---------------|
| `ui.unified.SettingsPage` | Font, GuiGraphics |
| `ui.unified.ScrollableSettingsPage` | Font, GuiGraphics |
| `ui.unified.pages.MobConfigPage` | Minecraft, Font, GuiGraphics |
| `ui.unified.pages.VisualizersPage` | Font, GuiGraphics |
| `ui.unified.pages.DebugOverlaysPage` | Font, GuiGraphics |
| `ui.unified.pages.CombatSettingsPage` | Minecraft, Font, GuiGraphics |
| `ui.unified.pages.EditorSettingsPage` | Minecraft, Font, GuiGraphics |
| `ui.unified.pages.TelemetryPage` | Font, GuiGraphics |
| `ui.unified.pages.KeybindsPage` | KeyMapping, Font, GuiGraphics |
| `ui.unified.pages.GeneralSettingsPage` | Minecraft, Font, GuiGraphics |

### UI Testing Panels
| File | Client Imports |
|------|---------------|
| `ui.testing.VoxelLabPage` | GuiGraphics |
| `ui.testing.panel.*` (10+ files) | Minecraft, GuiGraphics |
| `ui.testing.pages.OverviewPage` | Minecraft, ClientLevel |
| `ui.testing.pages.AbstractVoxelLabPage` | GuiGraphics |

### UI Editor System (30+ files)
| File | Client Imports |
|------|---------------|
| `ui.editor.ItemEditorInputHandler` | Minecraft |
| `ui.editor.AbstractEditorModule` | Minecraft, GuiGraphics |
| `ui.editor.PlaceholderModule` | Minecraft, GuiGraphics |
| `ui.editor.sections.*` (10+ files) | Minecraft, Font, GuiGraphics |
| `ui.editor.components.*` (15+ files) | Minecraft, Font, GuiGraphics |
| `ui.editor.systems.*` (5+ files) | Minecraft, GuiGraphics |
| `ui.editor.modules.*` (5+ files) | Minecraft, GuiGraphics |
| `ui.editor.core.EditorSounds` | Minecraft, SoundInstance |
| `ui.editor.core.TooltipManager` | Minecraft, Font |
| `ui.editor.core.SliderDescriptions` | I18n |
| `ui.editor.debug.DebugInfoSection` | Minecraft |

### UI Radial Menu
| File | Client Imports |
|------|---------------|
| `ui.radial.RadialAction` | Minecraft |
| `ui.radial.RadialMenuConfig` | Minecraft |
| `ui.radial.RadialMenuRegistry` | Minecraft |
| `ui.radial.render.RadialGeometry` | GuiGraphics, RenderSystem |
| `ui.radial.render.RadialCategoryRenderer` | Font, GuiGraphics |
| `ui.radial.render.RadialHubRenderer` | Font, GuiGraphics |
| `ui.radial.render.RadialTooltipRenderer` | Font, GuiGraphics |

### UI Hub
| File | Client Imports |
|------|---------------|
| `ui.hub.CategoryPanel` | Font, GuiGraphics, EditBox |
| `ui.hub.HubPanel` | GuiGraphics |
| `ui.hub.HubSectionHeader` | Font, GuiGraphics |
| `ui.hub.ProgressFooter` | Font, GuiGraphics |
| `ui.hub.QuickToolsPanel` | Minecraft, Font, GuiGraphics |
| `ui.hub.TestDetailPanel` | Font, GuiGraphics |

---

## High Risk - HUD/Overlays

All use `Minecraft.getInstance()` and rendering:

| File | Client Imports |
|------|---------------|
| `hud.EnduranceQuestOverlay` | Minecraft, GuiGraphics |
| `hud.QuestSequenceOverlay` | Minecraft, GuiGraphics |
| `hud.TokenGainOverlay` | Minecraft, GuiGraphics |
| `hud.StaminaHudOverlay` | Minecraft, GuiGraphics |
| `hud.SkillEfficacyOverlay` | Minecraft, GuiGraphics |
| `hud.QuickHelpOverlay` | Minecraft, KeyMapping |
| `hud.PartyHudOverlay` | Minecraft, GuiGraphics |
| `hud.OnboardingOverlay` | Minecraft, KeyMapping |
| `hud.ImpactVFX` | RenderSystem, PoseStack |
| `hud.ImpactHudOverlay` | Minecraft, GuiGraphics |
| `hud.Impact3DPanelManager` | RenderSystem |
| `hud.Impact3DRenderer` | RenderSystem, PoseStack |
| `hud.Impact3DPanel` | RenderSystem |
| `hud.EntityDensityOverlay` | Minecraft, GuiGraphics |
| `hud.EconomyOverlay` | Minecraft, GuiGraphics |
| `hud.BossPhaseOverlay` | Minecraft, GuiGraphics |
| `hud.TelemetryStatusOverlay` | Minecraft |
| `hud.RecordBannerOverlay` | Minecraft |
| `hud.ImpactData` | Minecraft |
| `hud.InstanceLoadingOverlay` | Minecraft |
| `hud.IntegratedTestHud` | Minecraft |
| `hud.ComboDecayOverlay` | Minecraft |
| `hud.BadgePopupOverlay` | Minecraft |

---

## High Risk - Rendering

All require client-side rendering APIs:

| File | Client Imports |
|------|---------------|
| `rendering.RenderEvents` | Minecraft, PoseStack |
| `rendering.RoomBoundsVisualizer` | Minecraft, RenderSystem |
| `rendering.SpawnabilityOverlay` | Minecraft, RenderSystem |
| `rendering.PathfindingDebugger` | Minecraft, RenderSystem |
| `rendering.LineOfSightVisualizer` | Minecraft, RenderSystem |
| `rendering.LightLevelOverlay` | Minecraft, RenderSystem |
| `rendering.HeatmapVisualizer` | RenderSystem |
| `rendering.EntityInfoOverlay` | Minecraft, RenderSystem |
| `rendering.DebugRenderer` | Minecraft, RenderSystem |
| `rendering.AggroRangeVisualizer` | Minecraft, RenderSystem |
| `rendering.SphereRenderer` | RenderSystem |
| `rendering.VerticalLevelsVisualizer` | RenderSystem |
| `rendering.SafeSpotVisualizer` | RenderSystem |
| `rendering.BodyPartRenderer` | RenderSystem |
| `rendering.ChunkPerformanceVisualizer` | Minecraft, RenderSystem |
| `rendering.CustomRenderTypes` | RenderSystem |
| `rendering.MobDebugOverlay` | Minecraft |
| `rendering.shield.*` (3 files) | RenderSystem, Shader |
| `rendering.shader.*` (4 files) | RenderSystem, Shader |

---

## High Risk - Arena HUD

| File | Client Imports |
|------|---------------|
| `arena.hud.BuildProgressHud` | Minecraft, GuiGraphics |
| `arena.hud.ArenaHudKeyBinding` | KeyMapping, Minecraft |
| `arena.hud.ArenaDebugHud` | Minecraft, GuiGraphics |

---

## High Risk - Other

| File | Client Imports |
|------|---------------|
| `combat.WeaponTrailVFX` | RenderSystem, PoseStack |
| `collision.rendering.OBBDebugRenderer` | RenderSystem |
| `WorldRenderEvents` | Minecraft, PoseStack |
| `CombatEvents` | Minecraft |
| `ClientModEvents` | Minecraft |
| `DevModClient` | Minecraft (OK - has @Mod dist) |
| `effects.ShakeManager` | Minecraft |
| `effects.TrailManager` | RenderSystem |
| `effects.PerceptionEventHandler` | Minecraft |
| `quest.QuestHudOverlay` | Minecraft |
| `testing.TestingSession` | Minecraft |
| `testing.TutorialManager` | Minecraft |
| `testing.QAEventTracker` | Minecraft |
| `testing.QANotificationSystem` | Minecraft |
| `testing.IntegratedTestSession` | Minecraft |
| `testing.ActiveTestHudOverlay` | Minecraft |
| `telemetry.FpsTracker` | Minecraft |
| `telemetry.PerformanceProfiler` | Minecraft |
| `telemetry.dashboard.DashboardCommand` | Minecraft |
| `network.handlers.PartyNetworkHandler` | Minecraft |
| `network.handlers.ConfigNetworkHandler` | Minecraft |
| `network.ClientConfigFeedback` | Minecraft |
| `MobConfigScreenState` | Minecraft |
| `panels.*` (8 files) | Minecraft, RenderSystem |
| `party.PartyScreenRenderer` | Minecraft |
| `attributes.AttributeHudOverlay` | Minecraft |
| `attributes.AttributeMonitoringSystem` | Minecraft |
| `attributes.AttributeRayVisualizer` | Minecraft |
| `attributes.TrackedEntity` | Minecraft |
| `gametest.TestHarnessCommands` | Minecraft |

---

## Files Already Correct

### In `client/` package (OK)
- `client.ClientVFXHelper`
- `client.ClientVFXProxy` (has @OnlyIn)
- `debug.client.DebugClientRenderer`
- `debug.client.DebugRenderBools`
- `debug.client.NativeDebugClientRenderer`
- `actions.client.ActionKeybindRegistry`
- `actions.client.OnboardingActionPayload`
- `actions.client.ClientActionContexts`
- `actions.client.DevModClientActions`

### Has @OnlyIn Annotation (Partial OK)
- Some Screen classes have @OnlyIn
- Some cache classes have @OnlyIn
- Shader-related classes

---

## Recommended Package Structure

```
com.devmod/
├── client/                       # All client-only code
│   ├── input/                    # KeyInputHandler
│   ├── ui/                       # All screens
│   │   ├── arena/               # Arena screens
│   │   ├── editor/              # Item editor
│   │   ├── endurance/           # Endurance screens
│   │   ├── hub/                 # Testing hub
│   │   ├── party/               # Party screens
│   │   ├── radial/              # Radial menu
│   │   ├── testing/             # Test screens
│   │   ├── unified/             # Settings
│   │   └── wizard/              # Wizards
│   ├── hud/                      # All HUD overlays
│   ├── rendering/                # All rendering code
│   ├── effects/                  # VFX, trails, shake
│   ├── panels/                   # Floating panels
│   ├── debug/                    # Debug renderers
│   └── vfx/                      # VFX helpers
├── common/                       # Shared logic
│   ├── arena/                   # Arena system (server logic)
│   ├── endurance/               # Endurance logic (non-UI)
│   ├── party/                   # Party logic (non-UI)
│   └── ...
└── server/                       # Server-only code
```

---

## Action Plan

1. **Phase 1 - Create ClientUiBridge** (prevents import chains)
2. **Phase 2 - Move all Screen classes** to `client.ui.*`
3. **Phase 3 - Move KeyInputHandler** to `client.input`
4. **Phase 4 - Move HUD/Overlay** to `client.hud`
5. **Phase 5 - Move Rendering** to `client.rendering`
6. **Phase 6 - Add @OnlyIn** to all client classes
7. **Phase 7 - Add regression guard**

---

## Notes

- Files in `com.devmod.arena.*` (not `com.devmod`) also need attention
- Mixin classes in `mixin/` are already properly separated in mixins.json
- Some classes like `DevModClient` are correctly using `@Mod(dist=Dist.CLIENT)`
