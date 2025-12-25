# DevMod UI Inventory

> **Generated**: 2024-12-25 (Updated)
> **Scope**: All visible in-game interfaces for DevMod (NeoForge 1.21.1)
> **Total UI Elements**: 82 (31 Screens + 30 Overlays + 11 Rendering Overlays + 5 Floating Panels + 5 Editor Overlays)

---

## Table of Contents
1. [Screens](#1-screens-extends-screen)
2. [HUD Overlays](#2-hud-overlays-layereddrawlayer)
3. [Rendering Overlays](#3-rendering-overlays-3d-world-rendering)
4. [Editor Overlays](#4-editor-overlays-modal-dialogs)
5. [Floating Panels](#5-floating-panels-draggable-3d-panels)
6. [Keybind Registry](#6-keybind-registry)

---

## 1. Screens (extends Screen)

| UI Name | Type | Class/Resource | Where it Shows | How to Open | Permissions/Gating | Dependencies | User-facing Purpose | Known Issues/Risks | Telemetry |
|---------|------|----------------|----------------|-------------|-------------------|--------------|--------------------|--------------------|-----------|
| Radial Menu | Screen | [RadialMenuScreen.java](src/main/java/com/devmod/client/ui/radial/RadialMenuScreen.java) | Full screen overlay | G key (default) | None | ActionRegistry | Main entry point to all DevMod tools | None | action.radial.open |
| Radial Action Detail | Screen | [RadialActionDetailScreen.java](src/main/java/com/devmod/client/ui/radial/RadialActionDetailScreen.java) | Full screen | Click action in Radial | None | RadialMenuScreen | Shows action details and keybind hints | None | N/A |
| Unified Settings | Screen | [UnifiedSettingsScreen.java](src/main/java/com/devmod/client/ui/unified/UnifiedSettingsScreen.java) | Full screen | Radial > Settings | None | SettingsManager | All DevMod configuration in one place | None | settings.open |
| Item Editor | Screen | [ItemEditorScreen.java](src/main/java/com/devmod/client/ui/editor/ItemEditorScreen.java) | Full screen | Radial > Editor, M key | Hold item in hand | WeaponTypeDetector | Edit weapon/armor stats visually | Complex, many modules | editor.open |
| Mob Config | Screen | [MobConfigScreen.java](src/main/java/com/devmod/client/ui/screens/MobConfigScreen.java) | Full screen | Radial > Mob Config | Look at mob | MobConfigScreenRenderer | Configure mob attributes | None | mob.config.open |
| Mob Equipment | Screen | [MobEquipmentScreen.java](src/main/java/com/devmod/client/ui/screens/MobEquipmentScreen.java) | Full screen | From MobConfigScreen | Has mob selected | MobConfigScreen | Equip items on mobs | None | mob.equip.open |
| Telemetry Dashboard | Screen | [TelemetryDashboardScreen.java](src/main/java/com/devmod/client/ui/screens/TelemetryDashboardScreen.java) | Full screen | Radial > Telemetry | None | TelemetryService | View telemetry data and statistics | None | telemetry.dashboard.open |
| Welcome Screen | Screen | [WelcomeScreen.java](src/main/java/com/devmod/client/ui/WelcomeScreen.java) | Full screen | Auto on first join | !hasSeenWelcome | SettingsManager | Onboarding for new users | Uses WelcomeToastOverlay fallback | onboarding.welcome |
| Testing Hub | Screen | [TestingHub.java](src/main/java/com/devmod/client/ui/hub/TestingHub.java) | Full screen | Radial > Testing | None | TestingSession | Central hub for QA testing tools | None | testing.hub.open |
| QA Testing | Screen | [QATestingScreen.java](src/main/java/com/devmod/client/testing/QATestingScreen.java) | Full screen | Radial > QA | None | TestingSession | Manual QA test execution | None | qa.testing.open |
| Badge Test | Screen | [BadgeTestScreen.java](src/main/java/com/devmod/client/testing/BadgeTestScreen.java) | Full screen | From TestingHub | None | TesterProfile | Test badge display system | None | N/A |
| VoxelLab | Screen | [VoxelLabScreen.java](src/main/java/com/devmod/client/ui/testing/VoxelLabScreen.java) | Full screen | Radial > Debug > VoxelLab | None | VoxelLabPage | UI component testing sandbox | None | N/A |
| VoxelLab UI Test | Screen | [VoxelLabUiTestScreen.java](src/main/java/com/devmod/client/ui/testing/VoxelLabUiTestScreen.java) | Full screen | From VoxelLab | None | VoxelLabScreen | Extended UI testing | None | N/A |
| Mod Screen | Screen | [ModScreen.java](src/main/java/com/devmod/client/ui/ModScreen.java) | Full screen | ModMenu integration | None | None | Main mod menu entry | Legacy | N/A |
| Room Bounds Editor | Screen | [RoomBoundsEditorScreen.java](src/main/java/com/devmod/client/ui/RoomBoundsEditorScreen.java) | Full screen | Radial > Arena > Bounds | None | ArenaManager | Edit arena room boundaries | None | arena.bounds.edit |
| Quick Test Wizard | Screen | [QuickTestWizard.java](src/main/java/com/devmod/client/ui/wizard/QuickTestWizard.java) | Full screen | Radial > Quick Test | None | TestingSession | Guided quick test setup | None | wizard.quicktest |
| Open External Confirm | Screen | [OpenExternalConfirmScreen.java](src/main/java/com/devmod/client/ui/OpenExternalConfirmScreen.java) | Modal dialog | Before external links | None | None | Confirm opening external URLs | None | N/A |
| Stamina System Editor | Screen | [StaminaSystemEditor.java](src/main/java/com/devmod/client/ui/editor/StaminaSystemEditor.java) | Full screen | Radial > Abilities | None | StaminaConfig | Configure stamina system | None | stamina.editor.open |
| **Endurance Quest Screens** | | | | | | | | | |
| Endurance Quest | Screen | [EnduranceQuestScreen.java](src/main/java/com/devmod/client/endurance/EnduranceQuestScreen.java) | Full screen | Radial > Endurance | None | QuestManager | Main endurance mode launcher | None | endurance.open |
| Endurance Shop | Screen | [EnduranceShopScreen.java](src/main/java/com/devmod/client/endurance/EnduranceShopScreen.java) | Full screen | During quest breaks | inQuest | ClientShopCache | Buy items during quest | None | endurance.shop.open |
| Endurance Settings | Screen | [EnduranceSettingsScreen.java](src/main/java/com/devmod/client/endurance/EnduranceSettingsScreen.java) | Full screen | From EnduranceQuestScreen | None | EnduranceConfig | Configure endurance mode settings | None | endurance.settings.open |
| Perk Selection | Screen | [PerkSelectionScreen.java](src/main/java/com/devmod/client/endurance/PerkSelectionScreen.java) | Full screen | On level up | perkChoicesAvailable | PerkChoicesPayload | Choose perks during quest | Timed (needs countdown) | endurance.perk.select |
| Quest Death | Screen | [QuestDeathScreen.java](src/main/java/com/devmod/client/endurance/QuestDeathScreen.java) | Full screen | On player death in quest | playerDead | QuestDeathPayload | Death summary and options | None | endurance.death |
| Wave Directive | Screen | [WaveDirectiveScreen.java](src/main/java/com/devmod/client/endurance/WaveDirectiveScreen.java) | Full screen | Between waves | directiveChoices | WaveDirectivePayload | Choose wave modifiers | Timed (needs countdown) | endurance.directive |
| Wave Checkpoint | Screen | [WaveCheckpointScreen.java](src/main/java/com/devmod/client/endurance/WaveCheckpointScreen.java) | Full screen | At checkpoints | atCheckpoint | QuestSequencePayload | Checkpoint summary | None | endurance.checkpoint |
| Kit Selection | Screen | [KitSelectionScreen.java](src/main/java/com/devmod/client/endurance/KitSelectionScreen.java) | Full screen | Quest start | !questStarted | KitConfig | Choose starting loadout | None | endurance.kit.select |
| Quest Completion | Screen | [QuestCompletionScreen.java](src/main/java/com/devmod/client/endurance/QuestCompletionScreen.java) | Full screen | On quest complete | questCompleted | QuestCompletionPayload | Victory summary and rewards | None | endurance.complete |
| Quest Exit Confirm | Screen | [QuestExitConfirmScreen.java](src/main/java/com/devmod/client/endurance/QuestExitConfirmScreen.java) | Modal dialog | ESC during quest | inQuest | None | Confirm quest exit | None | N/A |
| **Party Screens** | | | | | | | | | |
| Party Screen | Screen | [PartyScreen.java](src/main/java/com/devmod/client/party/PartyScreen.java) | Full screen | Radial > Party | None | PartyManager | Manage party members | None | party.open |
| Invite Popup | Screen | [InvitePopupScreen.java](src/main/java/com/devmod/client/party/InvitePopupScreen.java) | Modal dialog | On party invite | hasInvite | PartyNotificationPayload | Accept/decline party invite | Timed (needs countdown) | party.invite.show |
| **Arena Screens** | | | | | | | | | |
| Arena Test Wizard | Screen | [ArenaTestWizard.java](src/main/java/com/devmod/client/arena/ui/ArenaTestWizard.java) | Full screen | Radial > Arena > Test | None | ArenaManager | Setup arena test scenarios | None | arena.test.wizard |

---

## 2. HUD Overlays (LayeredDraw.Layer)

| UI Name | Type | Class/Resource | Where it Shows | How to Open | Permissions/Gating | Dependencies | User-facing Purpose | Known Issues/Risks | Telemetry |
|---------|------|----------------|----------------|-------------|-------------------|--------------|--------------------|--------------------|-----------|
| Mob Stats Layer | HUD | [ClientModEvents.MobStatsLayer](src/main/java/com/devmod/client/events/ClientModEvents.java#L309) | HUD crosshair area | Look at mob + ModConfig.showOverlay | None | None | Shows mob HP, armor, damage, reach | None | N/A |
| QA Notifications | HUD | [ClientModEvents.QANotificationsLayer](src/main/java/com/devmod/client/events/ClientModEvents.java#L100) | Top-right corner | On achievements/levelups | None | QANotificationSystem | Achievement popups | None | N/A |
| **Welcome Toast** | HUD | [WelcomeToastOverlay.java](src/main/java/com/devmod/client/overlay/WelcomeToastOverlay.java) | Top-center (slide-in) | Fallback for WelcomeScreen | !hasSeenWelcome | SettingsManager | Non-intrusive welcome for new users | None | onboarding.toast |
| Resonance HUD | HUD | [ResonanceHudOverlay.java](src/main/java/com/devmod/client/overlay/ResonanceHudOverlay.java) | Bottom-left | hasResonanceData | None | ResonanceNotificationPayload | Shows resonance status | None | N/A |
| Contract HUD | HUD | [ContractHudOverlay.java](src/main/java/com/devmod/client/overlay/ContractHudOverlay.java) | HUD area | hasContract | None | ContractSyncPayload | Shows active contract progress | None | N/A |
| Dynamic Radius HUD | HUD | [DynamicRadiusHudOverlay.java](src/main/java/com/devmod/client/overlay/DynamicRadiusHudOverlay.java) | HUD area | Toggle enabled | None | None | Shows dynamic effect radii | None | N/A |
| Endurance Quest Overlay | HUD | [EnduranceQuestOverlay.java](src/main/java/com/devmod/client/overlay/EnduranceQuestOverlay.java) | Top area | inQuest | None | ClientQuestCache | Wave, kills, tokens, combo | None | N/A |
| Token Gain | HUD | [TokenGainOverlay.java](src/main/java/com/devmod/client/overlay/TokenGainOverlay.java) | Floating text | On token gain | None | TokenGainPayload | +X tokens animation | None | N/A |
| Quest Sequence | HUD | [QuestSequenceOverlay.java](src/main/java/com/devmod/client/overlay/QuestSequenceOverlay.java) | Center screen | During transitions | None | QuestSequencePayload | Wave start/end animations | None | N/A |
| Telemetry Status | HUD | [TelemetryStatusOverlay.java](src/main/java/com/devmod/client/overlay/TelemetryStatusOverlay.java) | Corner | When enabled | None | TelemetryService | Recording indicator | None | N/A |
| Impact HUD | HUD | [ImpactHudOverlay.java](src/main/java/com/devmod/client/overlay/ImpactHudOverlay.java) | Bottom area | On damage dealt | None | ImpactData | Damage numbers breakdown | None | N/A |
| Quick Help | HUD | [QuickHelpOverlay.java](src/main/java/com/devmod/client/overlay/QuickHelpOverlay.java) | Side panel | F1 or first use | None | None | Keybind hints | None | N/A |
| Integrated Test | HUD | [IntegratedTestOverlay.java](src/main/java/com/devmod/client/overlay/IntegratedTestOverlay.java) | HUD area | During test run | None | TestingSession | Test progress indicator | None | N/A |
| Boss Phase | HUD | [BossPhaseOverlay.java](src/main/java/com/devmod/client/overlay/BossPhaseOverlay.java) | Top area | Boss encounter | None | BossAlertPayload | Boss health and phase | None | N/A |
| Record Banner | HUD | [RecordBannerOverlay.java](src/main/java/com/devmod/client/overlay/RecordBannerOverlay.java) | Center banner | On new record | None | RecordBannerPayload | NEW RECORD! animation | None | N/A |
| Onboarding | HUD | [OnboardingOverlay.java](src/main/java/com/devmod/client/overlay/OnboardingOverlay.java) | Bottom area | First actions | !completedOnboarding | SettingsManager | Tutorial hints | None | N/A |
| Stamina HUD | HUD | [StaminaHudOverlay.java](src/main/java/com/devmod/client/overlay/StaminaHudOverlay.java) | Near hotbar | staminaEnabled | None | StaminaSyncPayload | Stamina bar | None | N/A |
| Badge Popup | HUD | [BadgePopupOverlay.java](src/main/java/com/devmod/client/overlay/BadgePopupOverlay.java) | Top-right | On badge unlock | None | BadgeUnlockPayload | Badge earned animation | None | N/A |
| Instance Loading | HUD | [InstanceLoadingOverlay.java](src/main/java/com/devmod/client/overlay/InstanceLoadingOverlay.java) | Full screen dim | During load | None | InstanceLoadingPayload | Loading progress | None | N/A |
| Entity Density | HUD | [EntityDensityOverlay.java](src/main/java/com/devmod/client/overlay/EntityDensityOverlay.java) | Corner stats | Toggle enabled | None | None | Entity count per chunk | None | N/A |
| Party HUD | HUD | [PartyHudOverlay.java](src/main/java/com/devmod/client/overlay/PartyHudOverlay.java) | Side panel | inParty | None | PartySyncPayload | Party member health | None | N/A |
| Skill Efficacy | HUD | [SkillEfficacyOverlay.java](src/main/java/com/devmod/client/overlay/SkillEfficacyOverlay.java) | Corner | Toggle enabled | None | None | DPS/efficiency metrics | None | N/A |
| Combo Decay | HUD | [ComboDecayOverlay.java](src/main/java/com/devmod/client/overlay/ComboDecayOverlay.java) | Near combo | hasCombo | None | ComboDecayPayload | Combo timer ring | None | N/A |
| Economy | HUD | [EconomyOverlay.java](src/main/java/com/devmod/client/overlay/EconomyOverlay.java) | Corner | Toggle enabled | None | None | Token balance | None | N/A |
| Arena Debug | HUD | [ArenaDebugHud.java](src/main/java/com/devmod/client/arena/hud/ArenaDebugHud.java) | Debug area | debugEnabled | None | ArenaManager | Arena state debug | None | N/A |
| Build Progress | HUD | [BuildProgressHud.java](src/main/java/com/devmod/client/arena/hud/BuildProgressHud.java) | Progress bar | During build | None | BuildProgressPayload | Structure build % | None | N/A |
| Quest HUD | HUD | [QuestHudOverlay.java](src/main/java/com/devmod/client/quest/QuestHudOverlay.java) | Side panel | hasQuest | None | QuestManager | Quest objectives | None | N/A |
| Attribute HUD | HUD | [AttributeHudOverlay.java](src/main/java/com/devmod/client/attributes/AttributeHudOverlay.java) | Corner | Toggle enabled | None | None | Player attribute monitor | None | N/A |
| Active Test HUD | HUD | [ActiveTestHudOverlay.java](src/main/java/com/devmod/client/testing/ActiveTestHudOverlay.java) | Top area | During test | None | TestingSession | Test status indicator | None | N/A |
| Headshot Flash VFX | VFX | [HeadshotFlashVFX.java](src/main/java/com/devmod/client/overlay/HeadshotFlashVFX.java) | Screen flash | On headshot | None | None | Visual feedback | None | N/A |

---

## 3. Rendering Overlays (3D World Rendering)

| UI Name | Type | Class/Resource | Where it Shows | How to Open | Permissions/Gating | Dependencies | User-facing Purpose | Known Issues/Risks | Telemetry |
|---------|------|----------------|----------------|-------------|-------------------|--------------|--------------------|--------------------|-----------|
| Light Level | 3D Overlay | [LightLevelOverlay.java](src/main/java/com/devmod/client/rendering/LightLevelOverlay.java) | On blocks | L key toggle | None | None | Shows light levels on blocks | Performance impact | N/A |
| Mob Debug | 3D Overlay | [MobDebugOverlay.java](src/main/java/com/devmod/client/rendering/MobDebugOverlay.java) | Above mobs | O key toggle | None | None | Shows mob AI state | None | N/A |
| Entity Info | 3D Overlay | [EntityInfoOverlay.java](src/main/java/com/devmod/client/rendering/EntityInfoOverlay.java) | Above entities | Toggle enabled | None | None | Entity debug info | None | N/A |
| Spawnability | 3D Overlay | [SpawnabilityOverlay.java](src/main/java/com/devmod/client/rendering/SpawnabilityOverlay.java) | On blocks | Toggle enabled | None | None | Mob spawn conditions | Performance impact | N/A |
| Build Progress | 3D Overlay | [BuildProgressOverlay.java](src/main/java/com/devmod/client/arena/ui/BuildProgressOverlay.java) | On structures | During build | None | ArenaManager | Structure ghost preview | None | N/A |

---

## 4. Editor Overlays (Modal Dialogs)

| UI Name | Type | Class/Resource | Where it Shows | How to Open | Permissions/Gating | Dependencies | User-facing Purpose | Known Issues/Risks | Telemetry |
|---------|------|----------------|----------------|-------------|-------------------|--------------|--------------------|--------------------|-----------|
| Debug Overlay | Editor Modal | [DebugOverlay.java](src/main/java/com/devmod/client/ui/editor/debug/DebugOverlay.java) | Over editor | D key in editor | inEditor | ItemEditorScreen | Debug panel for editor | None | N/A |
| Help Overlay | Editor Modal | [HelpOverlay.java](src/main/java/com/devmod/client/ui/editor/systems/HelpOverlay.java) | Over editor | ? key in editor | inEditor | ItemEditorScreen | Editor keybinds help | None | N/A |
| Preset Selector | Editor Modal | [PresetSelectorOverlay.java](src/main/java/com/devmod/client/ui/editor/systems/PresetSelectorOverlay.java) | Over editor | Load preset button | inEditor | ItemEditorScreen | Select stat presets | None | N/A |
| Template Overlay | Editor Modal | [TemplateOverlay.java](src/main/java/com/devmod/client/ui/editor/systems/TemplateOverlay.java) | Over editor | Template button | inEditor | ItemEditorScreen | Apply templates | None | N/A |
| Item Picker | Editor Modal | [ItemPickerOverlay.java](src/main/java/com/devmod/client/ui/editor/components/ItemPickerOverlay.java) | Over editor | Recipe edit | inEditor | ItemEditorScreen | Pick items for recipes | None | N/A |
| Confirm Dialog | Modal | [ConfirmDialog.java](src/main/java/com/devmod/client/ui/ConfirmDialog.java) | Center screen | Before destructive action | None | None | Confirm dangerous actions | Duplicate exists | N/A |
| Editor Confirm | Modal | [ConfirmDialog.java](src/main/java/com/devmod/client/ui/editor/systems/ConfirmDialog.java) | Over editor | Before discard | inEditor | ItemEditorScreen | Confirm discard changes | Duplicate of above | N/A |

---

## 5. Floating Panels (Draggable 3D Panels)

| UI Name | Type | Class/Resource | Where it Shows | How to Open | Permissions/Gating | Dependencies | User-facing Purpose | Known Issues/Risks | Telemetry |
|---------|------|----------------|----------------|-------------|-------------------|--------------|--------------------|--------------------|-----------|
| Floating Panel (Base) | Panel | [FloatingPanel.java](src/main/java/com/devmod/client/panels/core/FloatingPanel.java) | 3D world | Various triggers | None | FloatingPanelManager | Base class for all panels | None | N/A |
| Tool Status Panel | Panel | [ToolStatusPanel.java](src/main/java/com/devmod/client/panels/types/ToolStatusPanel.java) | Near player | On tool use | None | FloatingPanelManager | Current tool status | None | N/A |
| Combat Panel | Panel | [CombatPanel.java](src/main/java/com/devmod/client/panels/types/CombatPanel.java) | Near target | In combat | None | FloatingPanelManager | Combat statistics | None | N/A |
| Entity Info Panel | Panel | [EntityInfoPanel.java](src/main/java/com/devmod/client/panels/types/EntityInfoPanel.java) | Above entity | Look at entity | None | FloatingPanelManager | Detailed entity info | None | N/A |
| Test Progress Panel | Panel | [TestProgressPanel.java](src/main/java/com/devmod/client/panels/types/TestProgressPanel.java) | Near player | During test | None | FloatingPanelManager, TestingSession | Test progress | None | N/A |
| Impact 3D Panel | Panel | [Impact3DPanel.java](src/main/java/com/devmod/client/overlay/Impact3DPanel.java) | At hit location | On damage | None | Impact3DPanelManager | 3D damage numbers | None | N/A |

---

## 6. Keybind Registry

| Keybind Name | Default Key | Category | Opens/Triggers | Conflict Context |
|--------------|-------------|----------|----------------|------------------|
| Radial Menu | G | devmod | RadialMenuScreen | IN_GAME |
| Settings | (unbound) | devmod | UnifiedSettingsScreen | IN_GAME |
| Weapon Editor | (unbound) | devmod | ItemEditorScreen | IN_GAME |
| Dashboard | (unbound) | devmod | TelemetryDashboardScreen | IN_GAME |
| Inspect Mob | (unbound) | devmod | MobConfigScreen | IN_GAME |
| Debug Overlay | (unbound) | devmod | Toggle MobDebugOverlay | IN_GAME |
| Light Overlay | (unbound) | devmod | Toggle LightLevelOverlay | IN_GAME |
| Heatmap | (unbound) | devmod | Toggle SpawnabilityOverlay | IN_GAME |
| Dismiss Impact HUD | (unbound) | devmod | Clear ImpactHudOverlay | IN_GAME |
| Room Bounds | (unbound) | devmod | Toggle room bounds rendering | IN_GAME |
| Pathfinding | (unbound) | devmod | Toggle pathfinding debug | IN_GAME |
| LOS | (unbound) | devmod | Toggle line-of-sight debug | IN_GAME |
| Vertical Levels | (unbound) | devmod | Toggle vertical level display | IN_GAME |
| Safe Spot | (unbound) | devmod | Toggle safe spot highlighting | IN_GAME |
| Attribute Monitor | (unbound) | devmod | Toggle AttributeHudOverlay | IN_GAME |
| FPS Tracker | (unbound) | devmod | Toggle FPS display | IN_GAME |
| Profiler | (unbound) | devmod | Toggle PerformanceProfiler | IN_GAME |
| Entity Density | (unbound) | devmod | Toggle EntityDensityOverlay | IN_GAME |
| Boss Phase | (unbound) | devmod | Toggle BossPhaseOverlay | IN_GAME |
| Skill Efficacy | (unbound) | devmod | Toggle SkillEfficacyOverlay | IN_GAME |
| Spawnability | (unbound) | devmod | Toggle SpawnabilityOverlay | IN_GAME |
| QA Testing | (unbound) | devmod | QATestingScreen | IN_GAME |
| Testing Hub | (unbound) | devmod | TestingHub | IN_GAME |
| Quest HUD | (unbound) | devmod | Toggle QuestHudOverlay | IN_GAME |
| Quest Complete | (unbound) | devmod | Complete current quest task | IN_GAME |
| Quest Editor | (unbound) | devmod | QuestEditorScreen | IN_GAME |
| Endurance Quest | (unbound) | devmod | EnduranceQuestScreen | IN_GAME |
| Quest Continue | (unbound) | devmod | Continue after wave | IN_GAME |
| Quest Exit | (unbound) | devmod | Exit current quest | IN_GAME |
| Party | (unbound) | devmod | PartyScreen | IN_GAME |
| Economy | (unbound) | devmod | Toggle EconomyOverlay | IN_GAME |
| Chunk Perf | (unbound) | devmod | Toggle chunk performance | IN_GAME |
| Help Overlay | (unbound) | devmod | Toggle QuickHelpOverlay | IN_GAME |
| Test Shake | (unbound) | devmod | Test screen shake effect | IN_GAME |
| Dash | (unbound) | devmod.abilities | Execute dash ability | IN_GAME |
| Dodge | (unbound) | devmod.abilities | Execute dodge ability | IN_GAME |

---

## Summary Statistics

| Category | Count |
|----------|-------|
| Full Screens | 31 |
| HUD Overlays | 30 |
| 3D Rendering Overlays | 5 |
| Editor Modal Overlays | 7 |
| Floating 3D Panels | 6 |
| Registered Keybinds | 36 |
| **Total UI Elements** | **82** |

---

## Recent Changes

### Added in Latest Update
- **WelcomeToastOverlay** - Non-intrusive slide-in toast replacing chat fallback (CRITICAL fix)
- **EnduranceSettingsScreen** - Configure endurance mode settings
- **DynamicRadiusHudOverlay** - Shows dynamic effect radii

### Fixed Issues
- Chat-as-UI fallback replaced with proper WelcomeToastOverlay
- Deprecated RadialAction methods now package-private

---

## Client-Only Safety Status

All UI elements in this inventory are located under `com.devmod.client.*` packages and are properly isolated from server-side code. The client module consolidation completed in commit `143883e` ensures:

- All Screen classes are in `client/` subpackages
- All overlays use `@OnlyIn(Dist.CLIENT)` where applicable
- Network handlers use reflection delegation for client-side operations
- Mixin configuration separates client and common mixins

See [FINAL_REPORT.md](../remediation/FINAL_REPORT.md) for full side-safety audit results.
