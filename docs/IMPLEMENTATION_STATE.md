# Implementation State (Agent Reference)

> Ultimo aggiornamento: 2026-02-03
> Scopo: snapshot completo dello stato implementativo per reference e versionamento

## Meta

| Campo | Valore |
|---|---|
| Mod ID | devmod |
| Mod Name (gradle) | mob config viewer |
| Versione | 0.1.0 |
| Autori | Frenk012, Vassago |
| Licenza | All Rights Reserved |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.216 |
| Parchment | 2024.11.17 |

## Update log (2026-02-03)

### Radial menu UX/robustness
- Release-to-select: debounce 200ms, richiede selezione esplicita; se bloccato mostra feedback e non chiude.
- Blocked feedback: action bar + reason; tooltip con reason + help contestuale; countdown combat.
- Hit target minimi: 44px per item e preferiti.
- Windows DPI: sync UI scale on open per evitare hover offset.
- Visual state BLOCKED: tint warning su item e preferiti + micro‑shake/flash se animazioni attive.
- Telemetria: `action_blocked` include `reasonKey` e `helpKey` (log dev).
- Debug actions: aggiunti `Reset Combat State` e `Context Status` nel radial (Analyze > Debug > Context).

## Build plugins

- java-library
- maven-publish
- net.neoforged.moddev
- idea
- jacoco
- checkstyle
- net.ltgt.errorprone
- com.github.spotbugs

## Source inventory

- Java files: 1822
- Packages: 403

### Package -> class list

#### .

- DevMod, ModConfig

#### Effekseer/swig

- EffekseerBackendCore, EffekseerCore, EffekseerCoreDeviceType, EffekseerCoreJNI, EffekseerEffectCore
- EffekseerManagerCore, EffekseerTextureType

#### abilities

- AbilityActionPayload, AbilityEventHandler, DashAbilitySystem, DodgeAbilitySystem, StaminaSyncPayload
- StaminaSystem

#### actions

- ActionCategory, ActionCommandInvoker, ActionContext, ActionIds, ActionOrigin, ActionPrecondition
- ActionPreconditions, ActionRegistry, ActionResult, ActionType, CommandSanitizer, DevModActions, RadialAction

#### actions/client

- ActionKeybindRegistry, ClientActionContexts, DevModClientActions, OnboardingActionPayload

#### ammo

- AmmoSystem

#### area

- AreaBlockEntities, AreaBlocks, AreaCreativeTab, AreaEventHandler, AreaItems, AreaModule

#### area/aesthetic

- AreaBuilderGuiConstants, AreaBuilderIcons, AreaBuilderInteraction, AreaBuilderMessages, AreaBuilderNaming
- AreaBuilderParticles, AreaBuilderSounds, AreaBuilderTiming, EditorState

#### area/block

- AreaEditorBlock, NexusEditorCentralBlock

#### area/block/entity

- AreaEditorBlockEntity, NexusEditorCentralBlockEntity

#### area/builder

- AreaBlockMapGenerator, AreaBuildStateRegistry, AreaBuildTask, AreaBuildTaskManager, AreaBuilder
- AreaPalettePresets, AreaShapeGenerator, AreaTemplatePresets, BiomeAreaGenerator, BiomeBlockProvider
- BiomeCaveGenerator, BiomeFeatureGenerator, BiomeRegistry, BiomeTerrainCalculator, BuildTaskState, QueuedBuild

#### area/command

- AreaCommandEvents, AreaCommands

#### area/data

- AreaAuditLog, AreaDefinition, AreaDimensions, AreaGenerationType, AreaOptions, AreaPalette, AreaRegistry
- AreaShape, BiomeGenerationConfig, BiomeTourConfig, BiomeTourSection, EntitySpawnConfig, EntitySpawnPoint
- GridSettings

#### area/network

- AreaNetworkHandler, AreaPayloadSizing, AreaPreviewPayload, BuildAreaPayload, BuildStatusPayload
- CaptureSnapshotPayload, CloneAreaPayload, CooldownManager, DeleteAreaPayload, DeleteSnapshotPayload
- DeleteTemplatePayload, LoadTemplatePayload, OpenAreaBuilderPayload, OpenEditorCentralPayload
- PauseBuildPayload, PromoteMainHubPayload, RequestOpenAreaBuilderPayload, RequestSnapshotListPayload
- RequestTemplateListPayload, RequestZoneListPayload, RestoreSnapshotPayload, ResumeBuildPayload
- SaveAreaPayload, SaveAreaResultPayload, SaveAreaTemplatePayload, SnapshotListPayload
- SnapshotManagementHandler, TemplateDataPayload, TemplateListPayload, TemplateManagementHandler
- ZoneListPayload

#### area/snapshot

- AreaSnapshot, AreaSnapshotCaptureTask, AreaSnapshotData, AreaSnapshotManager, AreaSnapshotRegistry
- AreaSnapshotRestoreTask

#### area/template

- AreaTemplate, AreaTemplateRegistry, TemplateCategory

#### arena

- ArenaCommandEvents, ArenaDebugState, BuildPhase, ProgressFlags

#### arena/alert

- AlertColors, AlertRouter, AlertRouterRegistry, ConsoleAlertChannel, DiscordAlertChannel, DuckDbAlertRecorder
- ErrorContext, LogAlertChannel, MailboxAlertChannel, TelemetryAlertChannel, WebhookAlertChannel

#### arena/analytics

- AnalyticsQueryParams

#### arena/api

- ArenaHandle

#### arena/autosmoke

- AutosmokeExceptions, AutosmokeGuard, AutosmokeReportHeader, AutosmokeReportWriter, AutosmokeRunner
- AutosmokeScheduler, AutosmokeSizeThresholds, AutosmokeThresholds

#### arena/budget

- BackpressureManager, BuildBudget, BuildTimeoutException

#### arena/builder

- ArenaBuilder, AsyncArenaBuildCoordinator, AsyncArenaBuilder, BuildDryRun, BuildDryRunCalculator
- BuildLimitExceededException, BuildTransaction, ChunkLoadingManager, CompactBlockTracker, TemplateArenaBuilder

#### arena/challenge

- AvailabilityResult

#### arena/cleanup

- ArenaCleanupExecutor, BlockIntegrityVerifier, CleanupPhase, CleanupResult, CleanupVerification
- PostBuildEntityAudit

#### arena/command

- ArenaActionBridge, ArenaActionRegistry, ArenaCommands

#### arena/concurrency

- ArenaBuildRateLimiter, BuildPermit, TemplateLockManager

#### arena/config

- ArenaTemplateConfig, FeatureFlag, FeatureFlagManager, FeatureFlagRegistry, InstanceLimitConfig

#### arena/currency

- CurrencySource

#### arena/dashboard

- ArenaDashboardEndpoint

#### arena/error

- UserFriendlyError

#### arena/event

- TemplateEvent, TemplateEventDispatcher

#### arena/failure

- FailureType

#### arena/fallback

- CircuitBreaker, FallbackMetrics

#### arena/gate

- InstanceOnlyGate

#### arena/identity

- ArenaIdentity

#### arena/instance

- ArenaInstance

#### arena/integration

- BatchBlockPlacer, MinecraftBlockPlacer, MinecraftEntitySpawner

#### arena/leaderboard

- LeaderboardType

#### arena/logging

- DuckDbDestination, LogAggregationPipeline, NdjsonWriter

#### arena/metrics

- ArenaMetricsContext, MetricsCompatibilityLayer

#### arena/migration

- WrapperAnalyzer

#### arena/monitor

- MsptMonitor, MsptSample

#### arena/monitoring

- BuildOutcomeMonitor

#### arena/network

- ArenaNetworkHandler, BuildProgressPayload

#### arena/override

- ForceTemplateCapability, OverrideManager, OverrideScope, TemplateOverride, TemplateOverrideAttachment
- TemplateOverrideCapability, TemplateOverrideManager

#### arena/performance

- PerformanceBudgetEnforcer

#### arena/policy

- ArenaPolicy, ArenaPolicyRegistry, BiomeFloorMapper, MutatorBinding, PolicyResolver, PolicySchemaValidator
- ResolveContext, ResolvedArena, TemplateSuggestion, VersionCompatibilityChecker

#### arena/pool

- PoolMetrics, PoolState, PooledArena, PrebuildPoolManager

#### arena/registry

- ArenaTemplate, ArenaTemplateRegistry, BlockEntityWhitelist, Bounds, ClasspathStructureDataProvider
- ContentWhitelist, CustomHazardRegistry, DiamondInheritanceException, GoldenReference, HazardValidator
- InheritanceCycleException, InheritanceDepthExceededException, InstanceSettingsValidator
- ParentTemplateNotFoundException, SchemaValidator, StructureManifest, StructureManifestParser
- StructureNbtLoader, StructureValidationInitializer, TemplateChecksum, TemplateDirectoryWatcher
- TemplateLoadException, TemplateLoader, TemplateManifest, TemplateMergeRules, TemplateRegistryBootstrap
- TemplateSpawnValidator, TemplateType, TemplateValidator, ValidationResult

#### arena/report

- ReportHeader

#### arena/rollout

- RolloutSuccessCriteria

#### arena/security

- ArenaCommandAudit, ArenaCommandPermissions, TemplatePermission

#### arena/serialization

- TemplateSerializer

#### arena/snapshot

- ArenaTemplateSnapshot

#### arena/spawn

- ForbiddenZone, SpawnOccupancyTracker, SpawnSlot, SpawnSlotConstraints, SpawnSlotResolver

#### arena/telemetry

- ArenaTelemetry

#### arena/validation

- SecurityLimitsEnforcer

#### arena/zone

- ArenaZone, ZoneDebugCommand, ZoneEnvironment, ZoneLayout, ZoneLayoutPlanner, ZoneSpawnSlotAllocator
- ZoneTransition

#### attributes

- AttributeLogColors, AttributeLogEntry, ModAttributes

#### blocks

- ModBlocks, NexusPortalBlock, NexusPortalColor

#### bridge

- ClientUiBridge

#### client

- ClientUiBridgeImpl, ClientVFXHelper, ClientVFXProxy, DevModClient

#### client/abilities

- ClientStaminaCache

#### client/area

- AreaBuilderScreen, AreaClientEvents, AreaClientHooks, AreaPreviewMesh, AreaPreviewRenderer
- BuildProgressTracker, CloneAreaDialog, DeleteAreaConfirmDialog, NexusEditorCentralScreen, SaveTemplateDialog
- SnapshotManagerDialog

#### client/area/widget

- BiomeConfigWidget, BiomeSelectorWidget, BiomeTourWizardWidget, CustomNbtWidget, DimensionsWidget
- EntitySpawnWidget, GridSettingsWidget, OptionsWidget, PaletteEditorWidget, PathWaypointWidget
- ShapeConfigWidget, TemplateSelectorWidget, ZoneSelectorWidget

#### client/arena/hud

- BuildProgressHud, BuildProgressHudTheme

#### client/arena/ui

- ArenaTestWizard, ArenaTestWizardColors

#### client/attributes

- AttributeHudOverlay, AttributeMonitoringSystem, AttributeRayVisualizer, TrackedEntity

#### client/collision/rendering

- OBBDebugRenderer

#### client/collision/transform

- ClientTransformProvider, ModelPartTransformCapture, ModelPartTransformExtractor

#### client/combat

- SignatureWeaponEvents, SignatureWeaponTooltip, WeaponTrailVFX

#### client/compat

- ClientCompatRegistrar

#### client/compat/mods/controlling

- ControllingCompat

#### client/compat/mods/epicfight

- ClientEpicFightCache

#### client/compat/mods/fancymenu

- FancyMenuCompat

#### client/compat/mods/yacl

- YaclCompat

#### client/config

- ClientMechanicsCache

#### client/debug

- DebugNetworkClientHandler, ZoneDebugRenderer, ZoneTransitionRenderer

#### client/debug/effects

- GapTransitionEffect, GradientTransitionEffect, HardTransitionEffect, TransitionEffect, WallTransitionEffect

#### client/effects

- PerceptionEventHandler, ShakeEffect, ShakeManager, TrailColors, TrailManager

#### client/endurance

- ArenaSelectionPanel, ClientArenaSuggestionsCache, ClientChallengeCache, ClientCombatFlowCache
- ClientMobPoolConfigCache, ClientNutritionCache, ClientPartyStatsCache, ClientPersonalRecordsCache
- ClientQuestCache, EnduranceClientDelegate, EnduranceQuestScreen, EnduranceSettingsScreen, EnduranceShopScreen
- EnduranceUiCache, EnduranceUiTheme, KitSelectionScreen, MobPoolEditorScreen, PerkSelectionScreen
- QuestCompletionScreen, QuestDeathScreen, QuestExitConfirmScreen, WaveCheckpointScreen, WaveDirectiveScreen

#### client/endurance/ui

- MobListPanel, MobStatsPanel

#### client/endurance/wis

- CombatEvent, WISClientBridge, WISOverlayHandler, WaveBriefingData, WaveIntelligenceManager, WavePhase
- WaveTelemetryCollector

#### client/endurance/wis/ui

- DebriefScreen, MinimalCombatHUD, PreWaveBriefOverlay

#### client/entity

- ClientEntityEvents

#### client/environment

- ClientEnvironmentCache

#### client/events

- ClientModEvents, CombatEvents, InteractionEvents

#### client/gametest

- TestHarnessClientDelegate

#### client/hologram

- HologramClientHandler, HologramEditorScreen

#### client/input

- KeyInputHandler, KeybindConflictDetector

#### client/network

- ClientConfigFeedbackPayload, ClientConfigHandlers, ClientEnduranceHandlers, ClientImpactHandlers
- ClientNetworkPayloadHooks, ClientOverlayHandlers, ClientPartyHandlers, ClientShieldHandlers
- ClientTensionCache

#### client/nexus

- NexusBuildProgressOverlay, NexusDialogClientHandler, NexusDialogScreen

#### client/notification

- ClientNotificationManager, ClientNotificationPreferences, NotificationActionResolver, NotificationSoundManager
- NotificationUiTheme

#### client/notification/render

- UnifiedToastOverlay

#### client/notification/ui

- NotificationBadgeOverlay, NotificationCenterScreen, NotificationSettingsScreen

#### client/npc

- ActionEditorWidget, ConditionDebugWidget, ConditionEditorWidget, DialogEditorHistory, DialogEditorScreen
- DialogOptionEditorScreen, DialogPreviewScreen, NpcClientHooks, NpcConfigScreen, NpcDialogScreen

#### client/npc/graph

- DialogGraphScreen, ForceDirectedLayout, GraphCanvas, GraphConnection, GraphMinimap, GraphNode, NodeEditPanel

#### client/npc/group

- GroupDialogScreen

#### client/overlay

- BossPhaseOverlay, CombatFlowHudOverlay, CombatRecapScreen, CombatSessionTracker, ContractHudOverlay
- DynamicRadiusHudOverlay, EconomyOverlay, EnduranceQuestOverlay, EntityDensityOverlay, HeadshotFlashVFX
- Impact3DPanel, Impact3DPanelManager, Impact3DRenderer, ImpactData, ImpactDisplayMode, ImpactDpsTracker
- ImpactEffekseerVFX, ImpactHistory, ImpactHudContentBuilder, ImpactHudController, ImpactHudOverlay
- ImpactHudPresets, ImpactHudService, ImpactVFX, InstanceLoadingOverlay, IntegratedTestOverlay
- NutritionHudOverlay, OnboardingOverlay, PartyHudOverlay, QuestSequenceOverlay, QuickHelpOverlay
- ResonanceHudOverlay, SkillEfficacyOverlay, StaminaHudOverlay, TelemetryStatusOverlay

#### client/overlay/effekseer

- ComboTracker, EffectContext, EffectOrchestrator, EffectPreset, HitWeight

#### client/overlay/vfx

- GlyphRenderer, ImpactVFXConstants, LinesRenderer, SlashRenderer, VortexRenderer

#### client/panels/context

- ContextDetector, ContextMode

#### client/panels/core

- FloatingPanel, FloatingPanelManager, PanelState, PanelType

#### client/panels/tracking

- EntityTracker

#### client/panels/types

- CombatPanel, CombatPanelTheme, EntityInfoPanel

#### client/panels/ui

- PanelInteractionHandler, PanelRenderer

#### client/party

- ClientPartyCache, InvitePopupScreen, PartyScreen, PartyScreenRenderer, PartyUiCache

#### client/quest

- QuestEditorScreen, QuestHudOverlay

#### client/rendering

- AggroRangeVisualizer, BodyPartCalculator, BodyPartRenderer, ChunkPerformanceVisualizer, CustomRenderTypes
- DebugGeometryBatcher, DebugRenderer, EntityInfoOverlay, HeatmapVisualizer, LightLevelOverlay
- LineOfSightVisualizer, MobDebugOverlay, PathfindingDebugger, RenderEvents, RoomBoundsVisualizer
- SafeSpotVisualizer, SpawnabilityOverlay, SphereRenderer, TrigCache, VerticalLevelsVisualizer
- WorldRenderEvents

#### client/rendering/shader

- ShaderPipeline, ShaderPipelineDiagnostics, ShaderRenderTypeConfig, VFXShaderRegistry

#### client/rendering/shield

- EnergyShieldRenderer, HexagonalShieldMesh, ShieldShaderRegistry

#### client/season

- ClientSeasonPassCache

#### client/state

- BaseStateStore, ClientStateManager, StateSubscription

#### client/state/stores

- ConfigStateStore, MailboxStateStore, NotificationStateStore, PartyStateStore, QuestStateStore

#### client/telemetry

- ClientLVCCache, ClientTelemetryBuffer, FpsTracker, PerformanceProfiler, TelemetryClientDelegate
- TelemetryUiTheme, UiTelemetry

#### client/template

- TemplateClientHandler, TemplateEditorScreen

#### client/testing

- ActiveTestHudOverlay, BadgeTestScreen, DevModPresetTestCases, IntegratedTestSession, QAEventTracker
- QANotificationSystem, QATestingScreen, TestingSession, TestingUiTheme, TutorialManager
- UIResponsivenessTestScreen

#### client/transport

- PartyTeleportScreen, TransportClientEvents, TransportClientPayloadHooks, TransportConfigScreen
- TransportNetworkSelectScreen, TransportOverlay, WaypointSelectScreen

#### client/ui

- AxiomRenderer, BaseDevModScreen, ConfirmDialog, ErrorBoundaryScreen, ErrorFallbackScreen, ModScreen
- OpenExternalConfirmScreen, RoomBoundsEditorScreen, ScreenSafety, UISound, WelcomeScreen

#### client/ui/admin

- AdminInstanceClientHandler, AdminInstanceScreen

#### client/ui/animation

- UiAnimation

#### client/ui/components

- ComponentRegistry, CountdownTimer

#### client/ui/core

- ResponsiveOverlay, UIScaleManager

#### client/ui/editor

- AbstractEditorModule, EditorApplyFeedbackRouter, EditorModule, EditorSection, EditorStartTab
- ItemEditorDataManager, ItemEditorScreen, ModuleTab, PerformanceMonitor, PlaceholderModule, RangedWeaponModule
- StaminaSystemEditor, WeaponTypeDetector

#### client/ui/editor/components

- ButtonRow, EditorButton, EditorButtonWidget, EditorSlider, EditorTextField, EditorToggle, FooterComponent
- HeaderComponent, InfoButton, ItemInfoPanel, ItemPickerOverlay, LeftColumnComponent, ModeBadge, PreviewRenderer
- RecipeGridComponent, ScrollableContentArea, SlotSelector, SourceBadge, VirtualizedList

#### client/ui/editor/controller

- InputRouter, ModeController, OverlayController

#### client/ui/editor/core

- BaseOverlay, Bounds, DarkTheme, DesignTokens, DirtyRegionTracker, EditorCache, EditorComponent, EditorConfig
- EditorConstants, EditorDimensions, EditorLayout, EditorScaleCalculator, EditorSounds, EditorSpacing, FocusRing
- GridValidator, HighContrastTheme, LightTheme, RenderObjectPool, ResponsiveLayout, ScaledCoord, ScaledSpacing
- ScrollState, SectionBounds, SliderDescriptions, StringBuilderCache, Theme, ThemeManager, TooltipManager
- Typography, UiSounds

#### client/ui/editor/debug

- DebugInfo, DebugInfoSection, DebugOverlay, DebugReporter, DebugWarning, ItemDebugInfo, OverflowDetector
- ValueComparison, WarningType

#### client/ui/editor/favorites

- FavoritePresetStore

#### client/ui/editor/modules

- ArmorModule, ArmorModuleCore, ArmorModuleUI, FoodModule, FoodModuleCore, FoodModuleUI, FuelModule
- FuelModuleCore, FuelModuleUI, GeneralModule, GeneralModuleCore, GeneralModuleUI, RangedModule
- RangedModuleCore, RangedModuleUI, RecipeModule, RecipeModuleCore, RecipeModuleUI, UsableModule
- UsableModuleCore, UsableModuleUI, WeaponModule, WeaponModuleCore, WeaponModuleUI, WeaponModuleVariants

#### client/ui/editor/overlay

- EditorOverlay

#### client/ui/editor/sections

- AttributeListSection, EffectListSection, EnchantmentListSection, InfoListSection, InputSectionAdapter
- ModuleCardSection, ModuleSummarySection, SimpleHeaderSection, SimpleSpacer, SliderSectionAdapter
- TextNoteSection, ToggleSectionAdapter

#### client/ui/editor/snapshot

- ItemEditorHistoryEntry, ItemEditorSnapshot

#### client/ui/editor/state

- ItemEditorState

#### client/ui/editor/systems

- BatchEditResult, BatchUndoSnapshot, CraftingInfoPanel, DataPreset, DebugPanel, HelpOverlay
- ItemEditorPresetManager, LowConfidenceDetector, ModpackDetector, MultiEditManager, MultiEditPanel, Preset
- PresetBridge, PresetManager, PresetRegistry, PresetScope, PresetSelectorOverlay, TemplateOverlay

#### client/ui/hub

- CategoryPanel, EditorType, HubPanel, HubSectionHeader, ProgressFooter, QuickToolsPanel, TestDetailPanel
- TestingHub, TestingHubState, ToolType, Verdict

#### client/ui/overlay

- OverlayTheme

#### client/ui/radial

- RadialAction, RadialActionDetailScreen, RadialActionSafety, RadialCategory, RadialMenuActionLayout
- RadialMenuBuilder, RadialMenuConfig, RadialMenuItem, RadialMenuRegistry, RadialMenuRuntimeRegistry
- RadialMenuScreen

#### client/ui/radial/animation

- Easing, RadialAnimator

#### client/ui/radial/config

- ColorTokenResolver, RadialMenuConfigValidator, RadialMenuConstants, RadialMenuDefinitionConfig
- RadialMenuDefinitionLoader, RadialMenuScaler, RadialMenuThemeDefaults, VisibilitySupplierRegistry

#### client/ui/radial/input

- RadialSearchHandler

#### client/ui/radial/model

- MacroCategory

#### client/ui/radial/render

- RadialCategoryRenderer, RadialGeometry, RadialHubRenderer, RadialTooltipRenderer

#### client/ui/screens

- EditorHubScreen, MobConfigScreen, MobConfigScreenRenderer, MobConfigScreenState, MobEquipmentScreen
- TelemetryDashboardScreen, TelemetryLogViewerScreen

#### client/ui/scroll

- ScrollBehavior, ScrollManager, ScrollMetrics, ScrollMode, Scrollbar

#### client/ui/scroll/impl

- InstantScrollBehavior, SmoothScrollBehavior

#### client/ui/search

- ItemSearchQuery

#### client/ui/season

- SeasonPassScreen

#### client/ui/testing

- ImpactHudButtons, ToastMessage, VoxelLabPage, VoxelLabScreen, VoxelLabTab, VoxelLabUiTestScreen

#### client/ui/testing/pages

- AbstractVoxelLabPage, CombatPage, ComponentShowcasePage, DebugOverlaysPage, EffectsPage, HudSystemsPage
- OverviewPage, PageUtils, TelemetryPage

#### client/ui/testing/panel

- ButtonRow, CollapsiblePanel, CompositePanel, GridPanel, HeaderPanel, PanelConstants, PanelContainer
- SectionPanel, ShowcasePanel, SliderPanel, SpacerPanel, StatusPanel, UIPanel

#### client/ui/unified

- SettingsCategory, SettingsPage, UnifiedSettingsScreen

#### client/ui/unified/pages

- CombatSettingsPage, DebugOverlaysPage, EditorSettingsPage, GeneralSettingsPage, KeybindsPage, MobConfigPage
- RadialSettingsPage, TelemetryPage, VisualizersPage

#### client/ui/unified/persistence

- SettingsData, SettingsManager

#### client/ui/wizard

- QuickTestWizard

#### client/vfx/effekseer

- EffekseerClient, EffekseerClientSetup

#### client/vfx/effekseer/api

- DeviceType, Effekseer, EffekseerEffect, EffekseerManager, ParticleEmitter, SafeFinalized, TextureType

#### client/vfx/effekseer/installer

- NativeLibraryInstaller, NativePlatform

#### client/vfx/effekseer/loader

- EffekAssetLoader, EffekLoadException

#### client/vfx/effekseer/registry

- EffectDefinition, EffectRegistry

#### client/vfx/effekseer/render

- EffekRenderer, RenderStateCapture, RenderUtil

#### client/vfx/effekseer/util

- Helpers

#### client/zone

- ZoneClientCache, ZoneClientEvents, ZoneClientHooks, ZoneEditorScreen

#### client/zone/widget

- ZoneBoundsWidget, ZonePreviewRenderer

#### clone

- CloneBlockEntities, CloneBlocks, CloneCreativeTab, CloneItems, CloneMenus, CloneModule

#### clone/block

- CentrifugeBlock, CloneMachineBlock, ClonePulverizerBlock, ImprinterBlock, NeurocellBlock, NeurocellItemBlock
- NeurocellLBlock, NeurocellMannequinBlock, NeurolinkBlock, ReformerBlock, TelepadBlock

#### clone/block/entity

- CentrifugeBlockEntity, CloneMachineBlockEntity, ClonePulverizerBlockEntity, ImprinterBlockEntity
- NeurocellAccess, NeurocellBlockEntity, NeurocellItemBlockEntity, NeurocellLBlockEntity
- NeurocellMannequinBlockEntity, ReformerBlockEntity, TelepadBlockEntity

#### clone/client

- CloneClientSetup, MannequinInputHandler

#### clone/client/model

- CentrifugeModel, CloneMachineItemModel, CloneMachineModel, ClonePulverizerModel

#### clone/client/renderer

- BillboardBatcher, CentrifugeRenderer, CloneMachineItemRenderer, CloneMachineRenderer, ClonePulverizerRenderer
- EntityBillboardAtlas, EntityBillboardCache, MannequinBillboardRegistry, NeurocellEffectsMesh
- NeurocellEffectsVBO, NeurocellItemRenderer, NeurocellLRenderer, NeurocellMannequinRenderer, NeurocellRenderer
- PlayerCloneEntityRenderer, TelepadDepthRenderer, TelepadEffekseerController, TelepadPortalGeometry
- TelepadPortalRenderer, TelepadPortalShaderRegistry

#### clone/client/screen

- CentrifugeScreen, NeurocellItemScreen, NeurocellLScreen, NeurocellMannequinScreen, NeurocellScreen
- TelepadConfigScreen

#### clone/client/util

- PlayerUuidLookup

#### clone/component

- CloneComponents

#### clone/data

- BioscanData

#### clone/entity

- PlayerCloneEntity

#### clone/entity/ai

- CloneFollowOwnerGoal

#### clone/integration

- CloneEntitySpawner, EnduranceCloneWave

#### clone/item

- BioscannerItem, CloneMachineBlockItem, GrinderItem

#### clone/menu

- CentrifugeMenu, NeurocellItemMenu, NeurocellLMenu, NeurocellMannequinMenu, NeurocellMenu

#### clone/network

- CloneNetworkHandler, MannequinRotationPayload, MannequinSkinPayload, TelepadConfigPayload
- TelepadOpenScreenPayload

#### clone/recipe

- CentrifugingRecipe, CloneRecipeTypes, PulverizingRecipe

#### clone/util

- NeurolinkConnector

#### collision/bodypart

- BodyPartColors, BodyPartDefinition, BodyPartHierarchy, BodyPartInstance

#### collision/compat

- GeckoLibCompat

#### collision/integration

- OBBHitHelper

#### collision/obb

- OBBRaycast, OrientedBoundingBox

#### collision/registry

- BodyPartRegistry, VanillaBodyParts

#### collision/transform

- AnimationSnapshot, ServerTransformProvider, TransformProvider, TransformProviderRegistry

#### combat

- CombatColors, DamageHandler, DamageTracker, ExecutionSystem, HitData, HitHelper, RangedProjectileHooks
- ShieldDeflector

#### combat/filter

- AmmoFilter

#### combat/shield

- ShieldBlockHandler

#### combat/signature

- SoulImprint, SoulImprintManager, WeaponTrait, WeaponTraitRegistry

#### combat/tracking

- EvasionHandler

#### compat

- BaseCompatModule, Compat, CompatModule, CompatRegistry, ReflectionHelper

#### compat/mods/accessories

- AccessoriesCompat

#### compat/mods/apothicattributes

- ApothicAttributesCompat

#### compat/mods/azurelib

- AzureLibCompat

#### compat/mods/c2me

- C2MECompat

#### compat/mods/clothconfig

- ClothConfigCompat

#### compat/mods/curios

- CuriosCompat

#### compat/mods/dummmmmmy

- DummmmmmyCommands, DummmmmmyCompat, DummmmmmyEvents

#### compat/mods/easydiet

- EasyDietApi, EasyDietBridge, EasyDietCompat

#### compat/mods/easynpc

- EasyNpcCompat

#### compat/mods/elixirum

- ElixirumCompat

#### compat/mods/emi

- EmiCompat

#### compat/mods/emotecraft

- EmotecraftCompat

#### compat/mods/entityculling

- EntityCullingCompat

#### compat/mods/epicfight

- EpicFightCompat

#### compat/mods/ferritecore

- FerriteCoreCompat

#### compat/mods/geckolib

- GeckoLibModuleCompat

#### compat/mods/iris

- IrisCompat

#### compat/mods/ironsspellbooks

- IronsSpellbooksCompat

#### compat/mods/journeymap

- JourneyMapColors, JourneyMapCompat

#### compat/mods/lithium

- LithiumCompat

#### compat/mods/modernfix

- ModernFixCompat

#### compat/mods/mowziesmobs

- MowziesMobsCompat

#### compat/mods/playeranimator

- PlayerAnimatorCompat

#### compat/mods/puffishskills

- PuffishSkillsCompat

#### compat/mods/rangedweaponapi

- RangedWeaponApiCompat

#### compat/mods/relics

- RelicsCompat

#### compat/mods/rpgseries

- RpgSeriesCompat

#### compat/mods/shieldapi

- ShieldApiCompat

#### compat/mods/smartbrainlib

- SmartBrainLibCompat

#### compat/mods/sodium

- SodiumCompat

#### compat/mods/spark

- SparkCompat

#### compat/mods/spellengine

- SpellEngineCompat

#### compat/mods/spellpower

- SpellPowerCompat

#### compat/mods/terrablender

- TerraBlenderCompat

#### components

- ArmorComponents, FoodComponents, FuelComponents, RangedComponents, UsableComponents, WeaponComponents

#### config

- Config, ConfigValidator, EditorClientConfig, GameMechanicsConfig, GameplayOverridesManager, MobConfigManager
- MobPresetManager, WISClientConfig

#### config/command

- ConfigExportCommand, ConfigExportEvents

#### config/component

- BooleanComponent, FloatComponent, IntComponent, ListComponent

#### config/gamedesign

- GameDesignConfig, GameDesignConfigManager, InstanceOverride

#### config/handler

- AbstractConfigHandler, ConfigHandlerRegistry, DecomposedConfig, IConfigComponent, IConfigHandler
- IDecomposedConfig

#### config/handler/export

- CommandStringUtil, ConfigExportHelper, IExportableConfigHandler

#### config/handler/impl

- ArmorConfigHandler, FoodConfigHandler, FuelConfigHandler, UsableConfigHandler, WeaponConfigHandler

#### config/stats

- IItemStats

#### core

- ServiceRegistry, Services

#### damage

- DamageBreakdown, DamageCalculator

#### debug

- DebugCommand, DebugEvents, DebugFeature, DebugManager, DebugModule, DebugNetworkHandler, DebugSyncPayload
- DebugTogglePayload, EntityGoalsPayload, EntityPathingPayload, NativeDebugSender, POIPayload, RaidsPayload

#### debug/block

- DebugBlockEntities, DebugBlocks, EntityScannerBlock

#### debug/block/entity

- EntityScannerBlockEntity

#### debug/client

- DebugRenderBools, EntityScannerClientHandler, EntityScannerScreen, NativeDebugClientRenderer

#### debug/network

- EntityScanDataPayload

#### effects

- TrailEffect

#### endurance

- ArenaContext, ArenaSuggestionsPayload, BossAlertPayload, BossWaveSystem, ClientShopCache
- CombatFlowSyncPayload, CombatTracker, ComboSystem, ComebackSystem, CustomKit, DifficultyScaler, DirectiveChain
- DirectiveChainManager, EnduranceAnalytics, EnduranceColors, EnduranceConfigSyncPayload, EnduranceEventCombat
- EnduranceEventHandler, EnduranceEventTick, EnduranceMobConfigSyncPayload, EndurancePlayerStateManager
- EnduranceQuest, EnduranceQuestManager, EnduranceQuestPersistence, EnduranceQuestRegistry, EnduranceQuestState
- EnduranceSessionHandler, EnduranceTags, FlowStateTracker, GamificationManager, InstanceArenaManager
- InstanceLoadingPayload, KitManager, KitPersistence, KitPreset, KitSyncConfirmPayload, KitSyncPayload
- KitSyncPersistence, LeaderboardCommandEvents, LeaderboardCommands, LeaderboardSystem, MobPoolConfigSyncPayload
- MomentumTracker, MutatorSystem, PartyQuestSession, PartyStatsSyncPayload, PartyWaveStats, PerkChoicesPayload
- PerkSelectionPayload, PerkSynergySystem, PerkSystem, PersonalRecordsSyncPayload, PrestigeMilestone
- QuestActionPayload, QuestCompletionPayload, QuestDeathPayload, QuestSyncPayload, QuestType
- RequestArenaSuggestionsPayload, RequestMobPoolConfigPayload, RequestPersonalRecordsPayload
- RequestShopSyncPayload, RewardSystem, ShopPurchasePayload, ShopSyncPayload, SpawnAffix, StartQuestPayload
- TensionSystem, TensionUpdatePayload, WaveDirective, WaveDirectiveChoicesPayload, WaveDirectiveSelectionPayload
- WaveDirector, WaveManager, WaveObjectiveState

#### endurance/ai

- EnduranceMeleeAttackGoal, EnduranceTargetPlayerGoal

#### endurance/analytics

- AnalyticsHook, CombatMetrics, LiveAnalyticsHookManager, QuestResult, WaveSummary

#### endurance/bargain

- Curse, DevilsBargainManager

#### endurance/boss

- BossDNAMixer

#### endurance/challenges

- ChallengeSyncPayload, DailyChallenge, DailyChallengeManager, WeeklyChallenge, WeeklyChallengeManager

#### endurance/combat

- ComboSystemFacade

#### endurance/combat/api

- ComboEvent, IComboEventListener, IComboSession

#### endurance/combat/core

- CombatStatsTracker, ComboSessionImpl, ComboTracker, StyleTracker

#### endurance/combat/events

- ChallengeComboListener, ComboEventDispatcher, NotificationComboListener, TelemetryComboListener

#### endurance/combat/scoring

- StyleRankResolver

#### endurance/config

- ConfigProposalManager, ConfigScope, EffectiveConfig, EnduranceConfigManager, EnduranceMobConfig
- EnduranceMobPoolConfig, GlobalMobConfigStorage

#### endurance/contracts

- ActiveContractManager, BloodContract, ContractSyncPayload

#### endurance/guild

- Guild, GuildSystem

#### endurance/hazard

- ArenaHazardSystem

#### endurance/lifecycle

- PartyStatsCoordinator, QuestContext, QuestEventBus, QuestLifecycleEvent, QuestLifecycleListener, WaveContext

#### endurance/modifier

- WaveModifierSystem

#### endurance/nemesis

- NemesisAdaptation, NemesisEvolutionManager, NemesisProfile

#### endurance/nutrition

- NutritionBridgeSystem, NutritionCategory, NutritionSession, NutritionSyncPayload

#### endurance/perk

- PerkSynergyWeb

#### endurance/resonance

- ResonanceChainSystem

#### endurance/season

- PlayerSeasonProgress, RequestSeasonPassPayload, SeasonPassPayload, SeasonPassSystem

#### endurance/services

- InstanceServicesFacade, PlayerStateServicesFacade

#### endurance/tide

- TideLevel, TideManager

#### endurance/wave

- WaveModifierService

#### entity

- ModEntities, ModEntityEvents

#### events

- ArrowEvents, CommonModEvents, FoodEvents, FuelEvents, GlobalMobEvents, LoginProtectionEvents, UsableEvents

#### exception

- DevModException

#### gametest

- AreaSystemGameTests, DevModGameTests, DevModTestStructures, InstanceSystemGameTests, L0BootVerificationTests
- TestHarnessCommands

#### hologram

- HologramBlockEntities, HologramBlocks, HologramItems, HologramModule

#### hologram/block

- HologramProjectorBlock

#### hologram/block/entity

- HologramProjectorBlockEntity

#### hologram/client

- HologramClientSetup

#### hologram/client/renderer

- ArenaPreviewMesh, ArenaPreviewRenderer, HologramEntityData, HologramMesh, HologramMeshBuilder
- HologramRenderer, HologramShaderRegistry, HologramVBO

#### hologram/client/screen

- HologramConfigScreen

#### hologram/data

- HologramBillboard, HologramDefinition, HologramFilter, HologramOptions, HologramPosition, HologramPreset
- HologramRegistry, HologramState, HologramStyle, HologramType

#### hologram/item

- HologramPlacerItem

#### hologram/network

- DeleteHologramPayload, HologramConfigPayload, HologramNetworkHandler, HologramOpenScreenPayload
- HologramPayloadSizing, HologramSyncPayload, OpenHologramEditorPayload, SaveHologramPayload

#### hologram/runtime

- HologramManager, HologramMigration, HologramNaming, HologramParticles, HologramPlaceholderResolver
- HologramSounds

#### integration

- DistantHorizonsIntegration, LittleTilesIntegration, ModIntegrationManager, PehkuiIntegration, PufferfishCompat
- PufferfishIntegration

#### mailbox

- MailboxConfig, MailboxManager, MailboxMessage, MailboxPermissions, MessageType

#### mailbox/admin

- MailboxCommandEvents, MailboxCommands

#### mailbox/analytics

- MailboxAnalyticsEngine

#### mailbox/api

- ApiServerLauncher, AuthMiddleware, JavalinBootstrap, MailboxApiServer

#### mailbox/api/controllers

- AdminAuditController, AnalyticsController, BroadcastController, ConfigController, MessageController
- NewsController, SearchController, SecurityMetricsDto, SuccessResponse, TaskController, TicketController
- UserController

#### mailbox/attachment

- AttachmentTransactionLog, AttachmentValidator, CompositeAttachment, CurrencyAttachment, ItemAttachment
- MailAttachment

#### mailbox/broadcast

- BroadcastQueueWorker

#### mailbox/client

- ClientMailboxAccess, ClientMailboxCache, ClientNewsCache, ClientTaskCache, ClientTicketCache, MailboxUiSkin
- MailboxUiTheme

#### mailbox/client/screen

- MailboxComposeScreen, MailboxScreen, NewsScreen, TesterTaskScreen, TicketCommentScreen, TicketCreateScreen

#### mailbox/commands

- ReportCommand

#### mailbox/config

- MailboxConfigSections

#### mailbox/delivery

- MailboxDeliveryJob, MailboxDeliveryRuntime

#### mailbox/digest

- DigestManager

#### mailbox/moderation

- AdminAuditLog, ContentFilter, PlayerReputation, SpamDetector

#### mailbox/network

- MailboxNetworkHandler, TicketNetworkHandler

#### mailbox/network/payload

- MailboxAccessPayload, MailboxActionPayload, MailboxNotifyPayload, MailboxPayloadLimits, MailboxSendPayload
- MailboxStatusPayload, MailboxSyncPayload, NewsReadPayload, NewsSyncPayload, TaskActionPayload, TaskSyncPayload
- TicketActionPayload, TicketCreatePayload, TicketSyncPayload, TicketSyncRequestPayload

#### mailbox/news

- NewsArticle, NewsCategory, NewsManager, NewsPurgeJob

#### mailbox/persistence

- DbPerformanceMonitor, DuckDbMailboxRepository, MailboxRepository

#### mailbox/scheduler

- MessageScheduler

#### mailbox/search

- MailboxSearchEngine

#### mailbox/task

- TaskAuditEntry, TestTask, TestTaskManager

#### mailbox/template

- MessageTemplate, MessageTemplateRegistry

#### mailbox/ticket

- AutoTransitionService, Ticket, TicketCategory, TicketComment, TicketManager, TicketPriority, TicketRepository
- TicketStatus, TicketWorkflow

#### mailbox/webhook

- WebhookManager

#### mixin

- CommandsMixin, DebugPacketsMixin, DevModMixinPlugin, MinecraftServerAccessor, MobDespawnMixin
- RecipeManagerMixin, VanillaPackResourcesBuilderMixin

#### mixin/client

- CameraShakeMixin, ClientLevelTimeMixin, EffekseerLevelRendererMixin
- FabricScreenApiFixMixin, GameRendererMixin, HexereiDynamicRegistriesMixin, LevelAccessorTimeMixin
- LivingEntityRendererMixin, ModelPartTransformMixin, MoreCullingCompatMixin, PlayerRendererMixin
- SoundManagerMixin

#### mob

- EnhancedMobRequirements, EnhancedMobRequirementsRegistry, MobRequirements, MobRequirementsCommand
- MobRequirementsDetector, MobRequirementsLoader, MobRequirementsRegistry, SpawnSource, SpawnSourceDetector
- StructureCharacteristics

#### network

- ArmorStatsPayload, ChannelId, EditorApplyConfirmPayload, EquipMobPayload, FoodStatsPayload, FuelStatsPayload
- GameMechanicsSyncPayload, GlobalConfigSyncPayload, ImpactSyncPayload, IpRateLimiter, MobConfigConfirmPayload
- ModifyItemPayload, NetworkCommand, NetworkConstants, NetworkHandler, PacketValidator, PayloadSizeUtil
- PayloadValidation, PayloadValidationEvents, PayloadVersion, RangedWeaponStatsPayload, RecipeClientSyncPayload
- RecipeSyncPayload, ShieldImpactPayload, ShieldShatterPayload, ShieldStatePayload, UpdateArmorPayload
- UpdateMobStatsPayload, UpdateWeaponPayload, UsableStatsPayload, VersionedPayload, WeaponStatsPayload
- ZoneDebugPayload

#### network/handlers

- AbilityNetworkHandler, ConfigNetworkHandler, EnduranceNetworkHandler, MobItemNetworkHandler
- NetworkHandlerBase, PartyNetworkHandler, PayloadRegistrar, ShieldNetworkHandler

#### network/protocol

- MessageEnvelope, MessageType, PayloadCodecRegistry

#### nexus

- NexusDecorBlocks

#### nexus/builder

- NexusEntitySpawner, NexusFoundationBuilder, NexusHubCenterBuilder, NexusOverlayManager

#### nexus/client

- NexusClientCache

#### nexus/command

- NexusCommandEvents, NexusCommands

#### nexus/data

- SlotPermissions, SlotType, ZoneSlot, ZoneSlotPresets, ZoneSlotRegistry

#### nexus/network

- HubStatusPayload, NexusBuildProgressPayload, NexusNetworkHandler, RequestSlotListPayload, SlotListPayload

#### nexus/runtime

- NexusHubManager

#### notification

- Notification, NotificationCategory, NotificationCenterActionData, NotificationParamsCodec
- NotificationPriority, NotificationRouter, NotificationService, PartyInviteActionData, PartyNotificationBridge

#### notification/network

- NotificationNetworkHandler, NotificationPreferencesSyncPayload, NotificationPreferencesUpdatePayload
- UnifiedNotificationPayload

#### notification/persistence

- NotificationHistoryRepository, NotificationPreferencesRepository

#### npc

- NpcEmotion, NpcEventHandler, NpcModule, NpcParticles, NpcSounds, NpcState

#### npc/command

- NpcDialogCommand

#### npc/component

- NpcComponents

#### npc/data

- DialogMemory, NpcAppearance, NpcBehavior, NpcConfiguration, NpcRegistry, NpcVisitData

#### npc/dialog

- DialogLimits, DialogNode, DialogOption, DialogPresets, DialogRegistry, DialogSchedule, DialogSet, NpcContext
- NpcDialogManager, NpcDialogSession, PlaceholderResolver

#### npc/dialog/action

- DialogAction

#### npc/dialog/condition

- ConditionDebugResult, DialogCondition

#### npc/dialog/group

- GroupDialog, GroupDialogManager, GroupDialogNode, GroupDialogSession, SpeakerLine

#### npc/event

- DialogEvent, DialogEventBus, DialogEventListener

#### npc/io

- DialogSetIO

#### npc/item

- NeurocellNpcItem, NpcItems

#### npc/network

- DialogPayloadSizing, NpcDialogActionPayload, NpcDialogPayload, NpcNetworkHandler, OpenDialogEditorPayload
- OpenNpcConfigPayload, SaveDialogPayload, SaveNpcConfigPayload

#### party

- ArrivalConfirmPayload, CancelSequencePayload, InviteResponsePayload, NamedInvitePayload, OnlinePlayersPayload
- PartyActionPayload, PartyData, PartyInvite, PartyManager, PartySyncPayload, QuestSequencePayload
- QuestStartSequence

#### portal

- PortalBlocks, PortalClientSetup, PortalColor, PortalConfig, PortalCreativeTab, PortalData, PortalEvents
- PortalFrameDetector, PortalItems, PortalRegistry, PortalRuneEffects, RuneType

#### portal/block

- CustomPortalBlock, RuneBlock

#### portal/client

- PortalClientEvents, PortalPreviewOverlay, PortalTeleportOverlay

#### portal/command

- PortalCommand

#### portal/item

- PortalIgniterItem, PortalLinkerItem

#### portal/network

- PortalNetworkHandler, PortalPreviewPayload, PortalPreviewRequestPayload, PortalStatePayload

#### quest

- QuestData, QuestManager, QuestTask

#### recipe

- CraftingRecipeData, CraftingType, IngredientData, RecipeCategory, RecipeConfigManager, RecipeData
- RecipeInjector, RecipeReloadListener, RecipeValidator, ResultData, SmeltingRecipeData, SmeltingType
- SmithingRecipeData, SmithingType, StonecuttingRecipeData

#### recipe/command

- RecipeExportCommand, RecipeExportEvents

#### recipe/export

- RecipeExportHelper

#### rendering

- HeatmapColors, HeatmapType

#### runtime

- DynamicDimensionManager, InstanceData, InstanceEventHandler, InstanceLevelData, InstanceManager
- InstanceRegistry, InstanceState, NexusBuildStep, NexusBuildTask, NexusCommand, NexusDialogContext
- NexusDialogManager, NexusDimensionManager, NexusDummyFeedback, NexusEventHandler, NexusHubSavedData
- NexusPalette, NexusPerformanceManager, NexusPortalManager, NexusReturnSavedData, PlayerInstanceSnapshot
- PlayerInstanceState, RecoverySystem, RiftStampEventHandler, RiftStampManager

#### runtime/biome

- ModBiomeSources, ZoneBiomeSource

#### runtime/block

- AdminTerminalBlock

#### runtime/environment

- DimensionEnvironmentManager, EnvironmentSyncPayload, TimeController

#### runtime/generator

- ArenaChunkGenerator, ArenaFlatChunkGenerator, BiomePolicyResolver, DynamicArenaChunkGenerator
- ModChunkGenerators

#### runtime/network

- AdminInstanceActionPayload, AdminInstanceNetworkHandler, AdminInstanceSyncPayload, NexusDialogActionPayload
- NexusDialogPayload, NexusLogRequestPayload, NexusLogSnapshotPayload, NexusLogType, NexusNetworkHandler
- NexusUiPayload

#### security

- PermissionGuard

#### shared

- SharedColorTokens

#### stats

- ArmorStats, FoodStats, FuelStats, RangedWeaponStats, ShieldColors, UsableStats, WeaponStats

#### tags

- ModTags

#### telemetry

- AsyncTelemetryWriter, BossPhaseDetector, DeferredEntityProcessor, EffectSkillTracker, EnchantmentSkillTracker
- RoomDefinition, TelemetryConfig, TelemetryEvents, TelemetryJson, TelemetryLogHandlers, TelemetryReloadCommand
- TelemetryService, TelemetrySettings

#### telemetry/boss

- BossPhaseService, UnifiedBossDetector

#### telemetry/combat

- FightSessionService

#### telemetry/damage

- DamageAttributionResolver, DamageTrackingService

#### telemetry/dashboard

- DashboardCommand, TelemetryAnalyticsHandlers, TelemetryDashboardServer

#### telemetry/duckdb

- ArenaRecords, DuckDBBatchWriter, DuckDBBootstrap, DuckDBConfig, DuckDBConnectionManager, DuckDBErrorClassifier
- DuckDBMigrationService, DuckDBQueryAPI, DuckDBSchemaManager, DuckDBTelemetryService, LatencyTracker
- RateLimitedLogger

#### telemetry/duckdb/aggregation

- AbilityAggregateWindow, AggregationConfig, CombatAggregateWindow, HeatmapAggregateWindow, SnapshotSampler
- TelemetryAggregator, TelemetryAggregatorRegistry

#### telemetry/duckdb/lvc

- PlayerLVCEntry, RollingDPSWindow, TelemetryLVC

#### telemetry/duckdb/packets

- TelemetryBatchPayload, TelemetryPacketHandler

#### telemetry/dungeon

- DungeonCommand, DungeonRunService, DungeonSessionService

#### telemetry/economy

- EconomyMetricsService, LootTrackingEvents

#### telemetry/endurance

- EnduranceTelemetryService

#### telemetry/entity

- EntityTrackingService, MinionService

#### telemetry/export

- CsvExporter, HeatmapExporter, JsonReportExporter

#### telemetry/network

- LVCSyncPayload

#### telemetry/player

- AbilityTelemetryService, PlayerAttributeTelemetryService, PlayerTrackingService

#### telemetry/progression

- PlayerProgressionService, ProgressionTrackingEvents

#### telemetry/room

- RoomAnalysisService, RoomEntityCounter, RoomService

#### telemetry/skills

- SkillTrackingService

#### telemetry/spatial

- BacktrackingService, DesireLinesService, HeatmapService, LightAnalysisService, SpatialMetricsService

#### telemetry/util

- BitPackedFlags

#### template

- TemplateModule

#### template/data

- AnchorPoint, ComponentCategory, ComponentDefinition, ComponentPlacement, RelativePosition, RoomTemplate
- TemplateRegistry, TemplateSpacing, ZoneTemplate

#### template/network

- ApplyTemplatePayload, OpenTemplateEditorPayload, SaveTemplatePayload, TemplateNetworkHandler
- TemplatePayloadSizing

#### template/runtime

- ComponentBuilder, TemplateManager, TemplatePresets

#### testing

- DevModArmorTestCases, DynamicTestGenerator, ModDiscoveryService, TestCase, TesterProfile, TesterProgress
- TestingColors

#### testing/config

- ConfigurableTestTemplate, ModTestConfig

#### testing/stats

- AchievementTracker, CombatEventStatistics, DamageStatistics, EnchantmentStatistics, EnvironmentalDamageStats
- ExplosionStatistics, HazardTypeRegistry, KillStatistics, ModInteractionTracker, OverlayUsageTracker
- PotionStatistics, SessionStatistics

#### transport

- TransportBlockEntities, TransportBlocks, TransportColor, TransportData, TransportEnhancement
- TransportEventHandler, TransportMode, TransportModule, TransportNodeType, TransportRegistry, TransportState

#### transport/block

- ChromaticTransportModuleBlock, TransportCoreBlock, TransportFrameBlock, TransportModuleBlock

#### transport/block/entity

- TransportCoreBlockEntity

#### transport/bridge

- InstanceDimensionBridge, LegacyTransportBridge, NexusTransportBridge, QuestTransportBridge

#### transport/executor

- ArrivalManager, ChargeManager, CooldownManager, CountdownManager, RecoveryManager, TransportEffectManager
- TransportExecutor

#### transport/manager

- RiftGateManager

#### transport/network

- TransportArrivalConfirmPayload, TransportCancelPartyPayload, TransportChargeUpdatePayload
- TransportConfigOpenPayload, TransportConfigSavePayload, TransportCountdownPayload
- TransportDeleteWaypointPayload, TransportNetworkHandler, TransportNetworkListPayload
- TransportPartyStatusPayload, TransportPayloadLimits, TransportStatePayload, TransportWaypointSelectPayload

#### util

- ColorMasks, ConfigPaths, ContextLogger, DamageTypeConfig, DatapackIO, FollowRangeColors, HotPathLogger, I18n
- ItemLookup, ItemStackResetUtils, MessageColors, MixinLogFilter, NullGuard, ObjectPool, PathSanitizer
- ThreadLocalPool

#### zone

- ZoneBlockEntities, ZoneBlocks, ZoneItems, ZoneModule

#### zone/block

- ZoneMarkerBlock

#### zone/block/entity

- ZoneMarkerBlockEntity

#### zone/data

- ZoneBounds, ZoneDefinition, ZoneNaming, ZonePresets, ZoneRegistry

#### zone/item

- ZoneMarkerItem

#### zone/network

- DeleteZonePayload, OpenZoneEditorPayload, SaveZonePayload, ZoneEnterPayload, ZoneNetworkHandler
- ZoneSyncPayload

#### zone/runtime

- ZoneResolver, ZoneTracker

## Registries

### Attributes

- crit_chance
- crit_multiplier
- armor_shred
- life_steal
- damage_bonus
- damage_vs_undead
- damage_vs_arthropods
- damage_vs_players
- true_damage_percent

### Data Components

- draw_time_ticks
- projectile_speed
- projectile_gravity
- projectile_spread
- base_arrow_damage
- multishot_count
- piercing_level
- ammo_tag_filter
- food_stats
- weapon_stats
- armor_stats
- fuel_stats
- usable_stats

### Entities

- player_clone

## Assets inventory

- blockstates: 260
- item models: 291
- block models: 528
- animations: 31
- geo: 31
- textures: {'.DS_Store': 1, 'block': 647, 'effect': 1, 'fluid': 116, 'gui': 78, 'item': 182, 'vfx': 5}

### Block IDs (from blockstates)

- admin_terminal, area_editor, centrifuge, chromatic_lens, clone_assembler, clone_atomic_forge, clone_battery
- clone_bio_generator, clone_centrifuge_l, clone_charger, clone_conveyor, clone_crusher, clone_drill, clone_energy_pipe
- clone_fertilizer, clone_foundry, clone_fuel_generator, clone_laser_arm, clone_lava_generator, clone_motor
- clone_processor, clone_pulverizer, clone_pump, clone_reactor, clone_refinery, clone_shrinker, clone_smelter
- clone_solar_panel, clone_steam_engine, clone_storage_unit, clone_tank, clone_tech_door, clone_treefeller, custom_portal
- dimensional_gate, entity_scanner, flux_capacitor, frame_segment, hologram_projector, imprinter, memory_core
- molten_ardite, molten_bronze, molten_cobalt, molten_copper, molten_electrum, molten_gold, molten_invar, molten_iron
- molten_lead, molten_manyullyn, molten_netherite, molten_nickel, molten_silver, molten_steel, molten_tin
- molten_void_metal, network_relay, neurocell, neurocell_item, neurocell_l, neurocell_mannequin, neurolink, nexus_azure
- nexus_azure_dark, nexus_azure_dark_slab, nexus_azure_light, nexus_azure_light_slab, nexus_azure_slab, nexus_binary
- nexus_binary_slab, nexus_brick, nexus_brick_slab, nexus_bronze, nexus_bronze_slab, nexus_carbon, nexus_carbon_slab
- nexus_center_floor, nexus_checker, nexus_checker_slab, nexus_circuit, nexus_circuit_gold, nexus_circuit_gold_slab
- nexus_circuit_gold_stairs, nexus_circuit_slab, nexus_circuit_stairs, nexus_cobalt, nexus_cobalt_slab, nexus_conduit
- nexus_conduit_slab, nexus_conduit_stairs, nexus_conduit_yellow, nexus_conduit_yellow_slab, nexus_conduit_yellow_stairs
- nexus_copper, nexus_copper_slab, nexus_core, nexus_core_slab, nexus_cross, nexus_cross_slab, nexus_crystal
- nexus_crystal_light, nexus_crystal_light_slab, nexus_crystal_slab, nexus_cyan_glow, nexus_cyan_glow_slab
- nexus_cyan_glow_stairs, nexus_dark_seamless, nexus_dark_seamless_slab, nexus_dark_seamless_stairs, nexus_data
- nexus_data_slab, nexus_diamond, nexus_diamond_slab, nexus_display, nexus_display_red, nexus_display_red_slab
- nexus_display_slab, nexus_dots, nexus_dots_slab, nexus_editor_central, nexus_ember, nexus_ember_slab, nexus_ember_stairs
- nexus_energy, nexus_energy_light, nexus_energy_light_slab, nexus_energy_slab, nexus_floor_light, nexus_floor_light_cyan
- nexus_frame, nexus_frame_slab, nexus_frame_white, nexus_frame_white_slab, nexus_glass_cyan, nexus_glass_dark
- nexus_glass_white, nexus_glow, nexus_glow_slab, nexus_glow_strip_cyan, nexus_glow_strip_white, nexus_gold
- nexus_gold_slab, nexus_gray_seamless, nexus_gray_seamless_slab, nexus_gray_seamless_stairs, nexus_grid, nexus_grid2
- nexus_grid2_slab, nexus_grid_gold, nexus_grid_gold_slab, nexus_grid_slab, nexus_hazard, nexus_hazard_slab, nexus_hex
- nexus_hex_slab, nexus_hologram, nexus_hologram_slab, nexus_iris, nexus_iris_slab, nexus_light, nexus_light_seamless
- nexus_light_seamless_slab, nexus_light_seamless_stairs, nexus_light_slab, nexus_matrix, nexus_matrix_light
- nexus_matrix_light_slab, nexus_matrix_slab, nexus_mesh, nexus_mesh_slab, nexus_mint, nexus_mint_slab, nexus_neon
- nexus_neon_blue, nexus_neon_blue_slab, nexus_neon_blue_stairs, nexus_neon_slab, nexus_onyx, nexus_onyx_slab, nexus_panel
- nexus_panel_dark, nexus_panel_dark_slab, nexus_panel_slab, nexus_plasma, nexus_plasma_dark, nexus_plasma_dark_slab
- nexus_plasma_light, nexus_plasma_light_slab, nexus_plasma_slab, nexus_plating, nexus_plating_slab, nexus_portal
- nexus_pulse, nexus_pulse_slab, nexus_quantum, nexus_quantum_slab, nexus_reactor, nexus_reactor_light
- nexus_reactor_light_slab, nexus_reactor_slab, nexus_ring_accent, nexus_ring_panel, nexus_rose, nexus_rose_slab
- nexus_scales, nexus_scales_slab, nexus_signal, nexus_signal_slab, nexus_silver, nexus_silver_slab, nexus_smooth
- nexus_smooth_slab, nexus_spiral, nexus_spiral_slab, nexus_steel, nexus_steel_seamless, nexus_steel_seamless_slab
- nexus_steel_seamless_stairs, nexus_steel_slab, nexus_stripes, nexus_stripes_slab, nexus_tech, nexus_tech_slab
- nexus_telepad_core, nexus_telepad_ring, nexus_terminal, nexus_terminal_green, nexus_terminal_green_slab
- nexus_terminal_slab, nexus_tile, nexus_tile_purple, nexus_tile_purple_slab, nexus_tile_purple_stairs, nexus_tile_slab
- nexus_vent, nexus_vent_red, nexus_vent_red_slab, nexus_vent_slab, nexus_void, nexus_void_slab, nexus_wave
- nexus_wave_slab, nexus_white_grid, nexus_white_grid_slab, nexus_white_grid_stairs, nexus_white_pure
- nexus_white_pure_slab, nexus_white_pure_stairs, party_beacon, range_amplifier, reformer, rune_enhancer, rune_gate
- rune_haste, rune_infinity, rune_strong_enhancer, telepad, warp_core, zone_marker

### Item IDs (from models/item)

- admin_terminal, area_editor, axe, battlesign, bioscanner, bow, centrifuge, chromatic_lens, clone_assembler
- clone_atomic_forge, clone_battery, clone_bio_generator, clone_centrifuge_l, clone_charger, clone_conveyor, clone_crusher
- clone_drill, clone_energy_pipe, clone_fertilizer, clone_foundry, clone_fuel_generator, clone_laser_arm
- clone_lava_generator, clone_motor, clone_processor, clone_pulverizer, clone_pump, clone_reactor, clone_refinery
- clone_shrinker, clone_smelter, clone_solar_panel, clone_steam_engine, clone_storage_unit, clone_tank, clone_tech_door
- clone_treefeller, crossbow, dimensional_gate, entity_scanner, flux_capacitor, frame_segment, grinder, hologram_placer
- hologram_projector, imprinter, manual, memory_core, molten_copper_bucket, molten_gold_bucket, molten_iron_bucket
- molten_netherite_bucket, network_relay, neurocell, neurocell_item, neurocell_l, neurocell_mannequin, neurocell_npc
- neurolink, nexus_azure, nexus_azure_dark, nexus_azure_dark_slab, nexus_azure_light, nexus_azure_light_slab
- nexus_azure_slab, nexus_binary, nexus_binary_slab, nexus_brick, nexus_brick_slab, nexus_bronze, nexus_bronze_slab
- nexus_carbon, nexus_carbon_slab, nexus_center_floor, nexus_checker, nexus_checker_slab, nexus_circuit
- nexus_circuit_gold, nexus_circuit_gold_slab, nexus_circuit_gold_stairs, nexus_circuit_slab, nexus_circuit_stairs
- nexus_cobalt, nexus_cobalt_slab, nexus_conduit, nexus_conduit_slab, nexus_conduit_stairs, nexus_conduit_yellow
- nexus_conduit_yellow_slab, nexus_conduit_yellow_stairs, nexus_copper, nexus_copper_slab, nexus_core, nexus_core_slab
- nexus_cross, nexus_cross_slab, nexus_crystal, nexus_crystal_light, nexus_crystal_light_slab, nexus_crystal_slab
- nexus_cyan_glow, nexus_cyan_glow_slab, nexus_cyan_glow_stairs, nexus_dark_seamless, nexus_dark_seamless_slab
- nexus_dark_seamless_stairs, nexus_data, nexus_data_slab, nexus_diamond, nexus_diamond_slab, nexus_display
- nexus_display_red, nexus_display_red_slab, nexus_display_slab, nexus_dots, nexus_dots_slab, nexus_editor_central
- nexus_ember, nexus_ember_slab, nexus_ember_stairs, nexus_energy, nexus_energy_light, nexus_energy_light_slab
- nexus_energy_slab, nexus_floor_light, nexus_floor_light_cyan, nexus_frame, nexus_frame_slab, nexus_frame_white
- nexus_frame_white_slab, nexus_glass_cyan, nexus_glass_dark, nexus_glass_white, nexus_glow, nexus_glow_slab
- nexus_glow_strip_cyan, nexus_glow_strip_white, nexus_gold, nexus_gold_slab, nexus_gray_seamless
- nexus_gray_seamless_slab, nexus_gray_seamless_stairs, nexus_grid, nexus_grid2, nexus_grid2_slab, nexus_grid_gold
- nexus_grid_gold_slab, nexus_grid_slab, nexus_hazard, nexus_hazard_slab, nexus_hex, nexus_hex_slab, nexus_hologram
- nexus_hologram_slab, nexus_iris, nexus_iris_slab, nexus_light, nexus_light_seamless, nexus_light_seamless_slab
- nexus_light_seamless_stairs, nexus_light_slab, nexus_matrix, nexus_matrix_light, nexus_matrix_light_slab
- nexus_matrix_slab, nexus_mesh, nexus_mesh_slab, nexus_mint, nexus_mint_slab, nexus_neon, nexus_neon_blue
- nexus_neon_blue_slab, nexus_neon_blue_stairs, nexus_neon_slab, nexus_onyx, nexus_onyx_slab, nexus_panel
- nexus_panel_dark, nexus_panel_dark_slab, nexus_panel_slab, nexus_plasma, nexus_plasma_dark, nexus_plasma_dark_slab
- nexus_plasma_light, nexus_plasma_light_slab, nexus_plasma_slab, nexus_plating, nexus_plating_slab, nexus_portal
- nexus_pulse, nexus_pulse_slab, nexus_quantum, nexus_quantum_slab, nexus_reactor, nexus_reactor_light
- nexus_reactor_light_slab, nexus_reactor_slab, nexus_ring_accent, nexus_ring_panel, nexus_rose, nexus_rose_slab
- nexus_scales, nexus_scales_slab, nexus_signal, nexus_signal_slab, nexus_silver, nexus_silver_slab, nexus_smooth
- nexus_smooth_slab, nexus_spiral, nexus_spiral_slab, nexus_steel, nexus_steel_seamless, nexus_steel_seamless_slab
- nexus_steel_seamless_stairs, nexus_steel_slab, nexus_stripes, nexus_stripes_slab, nexus_tech, nexus_tech_slab
- nexus_telepad_core, nexus_telepad_ring, nexus_terminal, nexus_terminal_green, nexus_terminal_green_slab
- nexus_terminal_slab, nexus_tile, nexus_tile_purple, nexus_tile_purple_slab, nexus_tile_purple_stairs, nexus_tile_slab
- nexus_vent, nexus_vent_red, nexus_vent_red_slab, nexus_vent_slab, nexus_void, nexus_void_slab, nexus_wave
- nexus_wave_slab, nexus_white_grid, nexus_white_grid_slab, nexus_white_grid_stairs, nexus_white_pure
- nexus_white_pure_slab, nexus_white_pure_stairs, party_beacon, plate_boots, plate_chestplate, plate_helmet
- plate_leggings, portal_igniter_black, portal_igniter_blue, portal_igniter_brown, portal_igniter_cyan
- portal_igniter_gray, portal_igniter_green, portal_igniter_light_blue, portal_igniter_light_gray, portal_igniter_lime
- portal_igniter_magenta, portal_igniter_orange, portal_igniter_pink, portal_igniter_purple, portal_igniter_red
- portal_igniter_white, portal_igniter_yellow, portal_linker, range_amplifier, reformer, rod, rune_enhancer, rune_gate
- rune_haste, rune_infinity, rune_strong_enhancer, slime_boots, slime_chestplate, slime_helmet, slime_leggings, staff
- staff_charging, tall, tall_axe, telepad, traveler_boots, traveler_chestplate, traveler_helmet, traveler_leggings
- viewer_item, warp_core, zone_marker

### GeckoLib assets

- Animations:
  - admin_terminal.animation, centrifuge.animation, clone_assembler.animation, clone_atomic_forge.animation
  - clone_battery.animation, clone_bio_generator.animation, clone_centrifuge_l.animation, clone_charger.animation
  - clone_conveyor.animation, clone_crusher.animation, clone_drill.animation, clone_energy_pipe.animation
  - clone_fertilizer.animation, clone_foundry.animation, clone_fuel_generator.animation, clone_laser_arm.animation
  - clone_lava_generator.animation, clone_motor.animation, clone_processor.animation, clone_pulverizer.animation
  - clone_pump.animation, clone_reactor.animation, clone_refinery.animation, clone_shrinker.animation
  - clone_smelter.animation, clone_solar_panel.animation, clone_steam_engine.animation, clone_storage_unit.animation
  - clone_tank.animation, clone_tech_door.animation, clone_treefeller.animation
- Geo models:
  - admin_terminal.geo, centrifuge.geo, clone_assembler.geo, clone_atomic_forge.geo, clone_battery.geo
  - clone_bio_generator.geo, clone_centrifuge_l.geo, clone_charger.geo, clone_conveyor.geo, clone_crusher.geo
  - clone_drill.geo, clone_energy_pipe.geo, clone_fertilizer.geo, clone_foundry.geo, clone_fuel_generator.geo
  - clone_laser_arm.geo, clone_lava_generator.geo, clone_motor.geo, clone_processor.geo, clone_pulverizer.geo
  - clone_pump.geo, clone_reactor.geo, clone_refinery.geo, clone_shrinker.geo, clone_smelter.geo, clone_solar_panel.geo
  - clone_steam_engine.geo, clone_storage_unit.geo, clone_tank.geo, clone_tech_door.geo, clone_treefeller.geo

## Data pack inventory

### loot_table

- loot_table/blocks/area_editor.json
- loot_table/blocks/hologram_projector.json
- loot_table/blocks/imprinter.json
- loot_table/blocks/neurocell.json
- loot_table/blocks/neurolink.json
- loot_table/blocks/reformer.json
- loot_table/blocks/telepad.json

### presets

- presets/balanced_pvp.json
- presets/diamond_tier.json
- presets/glass_cannon.json
- presets/overpowered_debug.json
- presets/tank_build.json
- presets/vanilla_default.json

### recipe

- recipe/bioscanner.json
- recipe/grinder.json
- recipe/hologram.json
- recipe/imprinter.json
- recipe/neurocell.json
- recipe/neurolink.json
- recipe/portal_igniter_black.json
- recipe/portal_igniter_blue.json
- recipe/portal_igniter_brown.json
- recipe/portal_igniter_cyan.json
- recipe/portal_igniter_gray.json
- recipe/portal_igniter_green.json
- recipe/portal_igniter_light_blue.json
- recipe/portal_igniter_light_gray.json
- recipe/portal_igniter_lime.json
- recipe/portal_igniter_magenta.json
- recipe/portal_igniter_orange.json
- recipe/portal_igniter_pink.json
- recipe/portal_igniter_purple.json
- recipe/portal_igniter_red.json
- recipe/portal_igniter_white.json
- recipe/portal_igniter_yellow.json
- recipe/portal_linker.json
- recipe/pulverizing/blaze_rod_to_powder.json
- recipe/pulverizing/bone_to_bonemeal.json
- recipe/pulverizing/cobblestone_to_gravel.json
- recipe/pulverizing/copper_ore_to_raw_copper.json
- recipe/pulverizing/deepslate_copper_ore_to_raw_copper.json
- recipe/pulverizing/deepslate_gold_ore_to_raw_gold.json
- recipe/pulverizing/deepslate_iron_ore_to_raw_iron.json
- recipe/pulverizing/gold_ore_to_raw_gold.json
- recipe/pulverizing/gravel_to_sand.json
- recipe/pulverizing/iron_ore_to_raw_iron.json
- recipe/pulverizing/stone_to_cobblestone.json
- recipe/reformer.json
- recipe/rune_enhancer.json
- recipe/rune_gate.json
- recipe/rune_haste.json
- recipe/rune_infinity.json
- recipe/rune_strong_enhancer.json
- recipe/telepad.json

### structure

- structure/combat_arena.nbt
- structure/empty.nbt
- structure/empty_3x3.nbt
- structure/empty_5x5.nbt

### tags

- tags/item/editable_melee_weapons.json
- tags/item/editable_ranged_weapons.json
- tags/item/editable_shields.json
- tags/item/melee_weapons.json
- tags/item/not_editable.json
- tags/item/ranged_weapons.json

### test_templates

- test_templates/irons_spellbooks.json

### damage_type

- damage_type/molten_metal.json

## Config inventory

### Runtime TOML
- run/config/devmod
- run/config/devmod-client.toml
- run/config/devmod-common-1.toml.bak
- run/config/devmod-common-2.toml.bak
- run/config/devmod-common-3.toml.bak
- run/config/devmod-common-4.toml.bak
- run/config/devmod-common-5.toml.bak
- run/config/devmod-common.toml
- run/config/devmod-mechanics-1.toml.bak
- run/config/devmod-mechanics.toml
- run/config/devmod-portals.toml

### config/devmod (runtime JSON)
- config/devmod/arena_policies/boss_ring_80_casual.policy.json
- config/devmod/arena_policies/boss_ring_80_ranked.policy.json
- config/devmod/arena_policies/default_flat_64.policy.json
- config/devmod/arena_policies/default_flat_64_melee.policy.json
- config/devmod/arena_policies/default_flat_64_ranged.policy.json
- config/devmod/arena_policies/smoke_flat_48.policy.json
- config/devmod/arena_templates/boss_ring_80.json
- config/devmod/arena_templates/default_flat_64.json
- config/devmod/arena_templates/smoke_flat_48.json

### src/main/resources/config/devmod (packaged defaults)
- src/main/resources/config/devmod/mob_requirements/minecraft_blaze.json
- src/main/resources/config/devmod/mob_requirements/minecraft_enderman.json
- src/main/resources/config/devmod/mob_requirements/minecraft_ghast.json
- src/main/resources/config/devmod/mob_requirements/minecraft_stray.json
- src/main/resources/config/devmod/mob_requirements/minecraft_warden.json
- src/main/resources/config/devmod/mob_requirements/minecraft_wither.json

## Network channels (ChannelId)

| ID | Name | Direction | Payload |
|---|---|---|---|
| 1 | MOB_STATS | CLIENT_TO_SERVER | UpdateMobStatsPayload |
| 2 | WEAPON_LEGACY | CLIENT_TO_SERVER | UpdateWeaponPayload |
| 3 | EQUIP_MOB | CLIENT_TO_SERVER | EquipMobPayload |
| 4 | MODIFY_ITEM | CLIENT_TO_SERVER | ModifyItemPayload |
| 5 | START_QUEST | CLIENT_TO_SERVER | StartQuestPayload |
| 6 | QUEST_ACTION | CLIENT_TO_SERVER | QuestActionPayload |
| 7 | QUEST_SYNC | SERVER_TO_CLIENT | QuestSyncPayload |
| 8 | SHOP_PURCHASE | CLIENT_TO_SERVER | ShopPurchasePayload |
| 9 | SHOP_SYNC | SERVER_TO_CLIENT | ShopSyncPayload |
| 10 | REQUEST_SHOP_SYNC | CLIENT_TO_SERVER | RequestShopSyncPayload |
| 11 | MOB_CONFIG_CONFIRM | SERVER_TO_CLIENT | MobConfigConfirmPayload |
| 12 | QUEST_DEATH | SERVER_TO_CLIENT | QuestDeathPayload |
| 13 | PERK_CHOICES | SERVER_TO_CLIENT | PerkChoicesPayload |
| 14 | PERK_SELECTION | CLIENT_TO_SERVER | PerkSelectionPayload |
| 15 | QUEST_COMPLETION | SERVER_TO_CLIENT | QuestCompletionPayload |
| 16 | PERSONAL_RECORDS_SYNC | SERVER_TO_CLIENT | PersonalRecordsSyncPayload |
| 17 | REQUEST_PERSONAL_RECORDS | CLIENT_TO_SERVER | RequestPersonalRecordsPayload |
| 18 | BOSS_ALERT | SERVER_TO_CLIENT | BossAlertPayload |
| 19 | REQUEST_ARENA_SUGGESTIONS | CLIENT_TO_SERVER | RequestArenaSuggestionsPayload |
| 20 | ARENA_SUGGESTIONS | SERVER_TO_CLIENT | ArenaSuggestionsPayload |
| 21 | KIT_SYNC | CLIENT_TO_SERVER | KitSyncPayload |
| 22 | KIT_SYNC_CONFIRM | SERVER_TO_CLIENT | KitSyncConfirmPayload |
| 23 | INSTANCE_LOADING | SERVER_TO_CLIENT | InstanceLoadingPayload |
| 24 | WAVE_DIRECTIVE_CHOICES | SERVER_TO_CLIENT | WaveDirectiveChoicesPayload |
| 25 | WAVE_DIRECTIVE_SELECTION | CLIENT_TO_SERVER | WaveDirectiveSelectionPayload |
| 26 | PARTY_ACTION | CLIENT_TO_SERVER | PartyActionPayload |
| 28 | PARTY_SYNC | SERVER_TO_CLIENT | PartySyncPayload |
| 29 | QUEST_SEQUENCE | SERVER_TO_CLIENT | QuestSequencePayload |
| 30 | NAMED_INVITE | CLIENT_TO_SERVER | NamedInvitePayload |
| 31 | ARRIVAL_CONFIRM | CLIENT_TO_SERVER | ArrivalConfirmPayload |
| 32 | CANCEL_SEQUENCE | CLIENT_TO_SERVER | CancelSequencePayload |
| 33 | INVITE_RESPONSE | CLIENT_TO_SERVER | InviteResponsePayload |
| 34 | PARTY_STATS_SYNC | SERVER_TO_CLIENT | PartyStatsSyncPayload |
| 36 | UPDATE_ARMOR | CLIENT_TO_SERVER | UpdateArmorPayload |
| 37 | RANGED_WEAPON_STATS | CLIENT_TO_SERVER | RangedWeaponStatsPayload |
| 38 | ARMOR_STATS | CLIENT_TO_SERVER | ArmorStatsPayload |
| 39 | GLOBAL_CONFIG_SYNC | SERVER_TO_CLIENT | GlobalConfigSyncPayload |
| 40 | RECIPE_SYNC | CLIENT_TO_SERVER | RecipeSyncPayload |
| 41 | RECIPE_CLIENT_SYNC | SERVER_TO_CLIENT | RecipeClientSyncPayload |
| 42 | TELEMETRY_BATCH | CLIENT_TO_SERVER | TelemetryBatchPayload |
| 43 | EDITOR_APPLY_CONFIRM | SERVER_TO_CLIENT | EditorApplyConfirmPayload |
| 44 | ENDURANCE_CONFIG_SYNC | CLIENT_TO_SERVER | EnduranceConfigSyncPayload |
| 45 | CONTRACT_SYNC | SERVER_TO_CLIENT | ContractSyncPayload |
| 46 | USABLE_STATS | CLIENT_TO_SERVER | UsableStatsPayload |
| 47 | FOOD_STATS | CLIENT_TO_SERVER | FoodStatsPayload |
| 48 | FUEL_STATS | CLIENT_TO_SERVER | FuelStatsPayload |
| 49 | WEAPON_STATS_V2 | CLIENT_TO_SERVER | WeaponStatsPayload v2 |
| 51 | TENSION_UPDATE | SERVER_TO_CLIENT | TensionUpdatePayload |
| 52 | GAME_MECHANICS_SYNC | SERVER_TO_CLIENT | GameMechanicsSyncPayload |
| 53 | ENDURANCE_MOB_CONFIG_SYNC | CLIENT_TO_SERVER | EnduranceMobConfigSyncPayload |
| 54 | COMBAT_FLOW_SYNC | SERVER_TO_CLIENT | CombatFlowSyncPayload |
| 56 | SHIELD_STATE | SERVER_TO_CLIENT | ShieldStatePayload |
| 57 | SHIELD_IMPACT | SERVER_TO_CLIENT | ShieldImpactPayload |
| 58 | SHIELD_SHATTER | SERVER_TO_CLIENT | ShieldShatterPayload |
| 59 | IMPACT_SYNC | SERVER_TO_CLIENT | ImpactSyncPayload |
| 66 | STAMINA_SYNC | SERVER_TO_CLIENT | StaminaSyncPayload |
| 67 | ABILITY_ACTION | CLIENT_TO_SERVER | AbilityActionPayload |
| 68 | LVC_SYNC | SERVER_TO_CLIENT | LVCSyncPayload |
| 76 | BUILD_PROGRESS | SERVER_TO_CLIENT | BuildProgressPayload |
| 77 | ENVIRONMENT_SYNC | SERVER_TO_CLIENT | EnvironmentSyncPayload |
| 78 | ZONE_DEBUG | SERVER_TO_CLIENT | ZoneDebugPayload |
| 86 | CHALLENGE_SYNC | SERVER_TO_CLIENT | ChallengeSyncPayload |
| 90 | DEBUG_TOGGLE | CLIENT_TO_SERVER | DebugTogglePayload |
| 91 | DEBUG_SYNC | SERVER_TO_CLIENT | DebugSyncPayload |
| 92 | ENTITY_PATHING | SERVER_TO_CLIENT | EntityPathingPayload |
| 93 | ENTITY_SCAN_DATA | SERVER_TO_CLIENT | EntityScanDataPayload |
| 94 | ENTITY_SCANNER_OPEN | SERVER_TO_CLIENT | EntityScannerOpenPayload |
| 100 | MAILBOX_SYNC | SERVER_TO_CLIENT | MailboxSyncPayload |
| 101 | MAILBOX_SEND | CLIENT_TO_SERVER | MailboxSendPayload |
| 102 | MAILBOX_READ | CLIENT_TO_SERVER | MailboxReadPayload |
| 105 | MAILBOX_NOTIFY | SERVER_TO_CLIENT | MailboxNotifyPayload |
| 106 | NEWS_SYNC | SERVER_TO_CLIENT | NewsSyncPayload |
| 107 | NEWS_READ | CLIENT_TO_SERVER | NewsReadPayload |
| 108 | TASK_SYNC | SERVER_TO_CLIENT | TaskSyncPayload |
| 109 | TASK_ACTION | CLIENT_TO_SERVER | TaskActionPayload |
| 110 | MAILBOX_STATUS | SERVER_TO_CLIENT | MailboxStatusPayload |
| 111 | MAILBOX_ACCESS | SERVER_TO_CLIENT | MailboxAccessPayload |
| 112 | TICKET_SYNC | SERVER_TO_CLIENT | TicketSyncPayload |
| 113 | TICKET_CREATE | CLIENT_TO_SERVER | TicketCreatePayload |
| 114 | TICKET_SYNC_REQUEST | CLIENT_TO_SERVER | TicketSyncRequestPayload |
| 115 | TICKET_ACTION | CLIENT_TO_SERVER | TicketActionPayload |
| 120 | UNIFIED_NOTIFICATION | SERVER_TO_CLIENT | UnifiedNotificationPayload |
| 121 | NOTIFICATION_PREFS_SYNC | SERVER_TO_CLIENT | NotificationPreferencesSyncPayload |
| 122 | NOTIFICATION_PREFS_UPDATE | CLIENT_TO_SERVER | NotificationPreferencesUpdatePayload |
| 123 | SEASON_PASS_SYNC | SERVER_TO_CLIENT | SeasonPassPayload |
| 124 | REQUEST_SEASON_PASS | CLIENT_TO_SERVER | RequestSeasonPassPayload |
| 130 | NUTRITION_SYNC | SERVER_TO_CLIENT | NutritionSyncPayload |
| 131 | REQUEST_MOB_POOL_CONFIG | CLIENT_TO_SERVER | RequestMobPoolConfigPayload |
| 132 | MOB_POOL_CONFIG_SYNC | SERVER_TO_CLIENT | MobPoolConfigSyncPayload |
| 140 | NEXUS_DIALOG | SERVER_TO_CLIENT | NexusDialogPayload |
| 141 | NEXUS_DIALOG_ACTION | CLIENT_TO_SERVER | NexusDialogActionPayload |
| 142 | NEXUS_UI | SERVER_TO_CLIENT | NexusUiPayload |
| 143 | NEXUS_LOG_REQUEST | CLIENT_TO_SERVER | NexusLogRequestPayload |
| 144 | NEXUS_LOG_SNAPSHOT | SERVER_TO_CLIENT | NexusLogSnapshotPayload |
| 150 | PORTAL_STATE | SERVER_TO_CLIENT | PortalStatePayload |
| 151 | PORTAL_PREVIEW_REQUEST | CLIENT_TO_SERVER | PortalPreviewRequestPayload |
| 152 | PORTAL_PREVIEW | SERVER_TO_CLIENT | PortalPreviewPayload |
| 160 | HOLOGRAM_CONFIG | CLIENT_TO_SERVER | HologramConfigPayload |
| 161 | HOLOGRAM_OPEN_SCREEN | SERVER_TO_CLIENT | HologramOpenScreenPayload |
| 162 | HOLOGRAM_EDITOR_OPEN | SERVER_TO_CLIENT | OpenHologramEditorPayload |
| 163 | HOLOGRAM_SAVE | CLIENT_TO_SERVER | SaveHologramPayload |
| 164 | HOLOGRAM_DELETE | CLIENT_TO_SERVER | DeleteHologramPayload |
| 165 | HOLOGRAM_SYNC | SERVER_TO_CLIENT | HologramSyncPayload |
| 170 | TELEPAD_CONFIG | CLIENT_TO_SERVER | TelepadConfigPayload |
| 171 | TELEPAD_OPEN_SCREEN | SERVER_TO_CLIENT | TelepadOpenScreenPayload |
| 172 | MANNEQUIN_ROTATION | CLIENT_TO_SERVER | MannequinRotationPayload |
| 173 | MANNEQUIN_SKIN | CLIENT_TO_SERVER | MannequinSkinPayload |
| 180 | NPC_CONFIG_OPEN | SERVER_TO_CLIENT | OpenNpcConfigPayload |
| 181 | NPC_CONFIG_SAVE | CLIENT_TO_SERVER | SaveNpcConfigPayload |
| 182 | NPC_DIALOG_EDITOR_OPEN | SERVER_TO_CLIENT | OpenDialogEditorPayload |
| 183 | NPC_DIALOG_SAVE | CLIENT_TO_SERVER | SaveDialogPayload |
| 184 | NPC_DIALOG_OPEN | SERVER_TO_CLIENT | NpcDialogPayload |
| 185 | NPC_DIALOG_ACTION | CLIENT_TO_SERVER | NpcDialogActionPayload |
| 190 | AREA_BUILDER_OPEN | SERVER_TO_CLIENT | OpenAreaBuilderPayload |
| 191 | AREA_EDITOR_CENTRAL_OPEN | SERVER_TO_CLIENT | OpenEditorCentralPayload |
| 192 | AREA_SAVE | CLIENT_TO_SERVER | SaveAreaPayload |
| 193 | AREA_BUILD | CLIENT_TO_SERVER | BuildAreaPayload |
| 194 | AREA_PREVIEW | SERVER_TO_CLIENT | AreaPreviewPayload |
| 195 | AREA_BUILDER_REQUEST | CLIENT_TO_SERVER | RequestOpenAreaBuilderPayload |
| 196 | AREA_ZONE_LIST_REQUEST | CLIENT_TO_SERVER | RequestZoneListPayload |
| 197 | AREA_ZONE_LIST | SERVER_TO_CLIENT | ZoneListPayload |
| 198 | AREA_TEMPLATE_REQUEST | CLIENT_TO_SERVER | RequestTemplateListPayload |
| 199 | AREA_TEMPLATE_LIST | SERVER_TO_CLIENT | TemplateListPayload |
| 200 | ZONE_EDITOR_OPEN | SERVER_TO_CLIENT | OpenZoneEditorPayload |
| 201 | ZONE_SAVE | CLIENT_TO_SERVER | SaveZonePayload |
| 202 | ZONE_DELETE | CLIENT_TO_SERVER | DeleteZonePayload |
| 203 | ZONE_SYNC | SERVER_TO_CLIENT | ZoneSyncPayload |
| 204 | ZONE_ENTER | SERVER_TO_CLIENT | ZoneEnterPayload |
| 205 | AREA_TEMPLATE_LOAD | CLIENT_TO_SERVER | LoadTemplatePayload |
| 206 | AREA_TEMPLATE_DATA | SERVER_TO_CLIENT | TemplateDataPayload |
| 207 | AREA_TEMPLATE_SAVE | CLIENT_TO_SERVER | SaveAreaTemplatePayload |
| 208 | AREA_CLONE | CLIENT_TO_SERVER | CloneAreaPayload |
| 209 | AREA_TEMPLATE_DELETE | CLIENT_TO_SERVER | DeleteTemplatePayload |
| 210 | TRANSPORT_CONFIG_OPEN | SERVER_TO_CLIENT | TransportConfigOpenPayload |
| 211 | TRANSPORT_CONFIG_SAVE | CLIENT_TO_SERVER | TransportConfigSavePayload |
| 212 | TRANSPORT_STATE | SERVER_TO_CLIENT | TransportStatePayload |
| 213 | TRANSPORT_CHARGE_UPDATE | SERVER_TO_CLIENT | TransportChargeUpdatePayload |
| 214 | TRANSPORT_WAYPOINT_SELECT | CLIENT_TO_SERVER | TransportWaypointSelectPayload |
| 215 | TRANSPORT_NETWORK_LIST | SERVER_TO_CLIENT | TransportNetworkListPayload |
| 216 | TRANSPORT_COUNTDOWN | SERVER_TO_CLIENT | TransportCountdownPayload |
| 217 | TRANSPORT_PARTY_STATUS | SERVER_TO_CLIENT | TransportPartyStatusPayload |
| 218 | TRANSPORT_ARRIVAL_CONFIRM | CLIENT_TO_SERVER | TransportArrivalConfirmPayload |
| 219 | TRANSPORT_CANCEL_PARTY | CLIENT_TO_SERVER | TransportCancelPartyPayload |
| 220 | TRANSPORT_DELETE_WAYPOINT | CLIENT_TO_SERVER | TransportDeleteWaypointPayload |
| 225 | AREA_DELETE | CLIENT_TO_SERVER | DeleteAreaPayload |
| 226 | AREA_PROMOTE_MAIN_HUB | CLIENT_TO_SERVER | PromoteMainHubPayload |
| 227 | AREA_SAVE_RESULT | SERVER_TO_CLIENT | SaveAreaResultPayload |
| 228 | AREA_BUILDER_CONTROL | CLIENT_TO_SERVER | AreaBuilderControlPayloads |
| 229 | AREA_BUILDER_FEEDBACK | SERVER_TO_CLIENT | AreaBuilderFeedbackPayloads |
| 230 | ADMIN_INSTANCE_SYNC | SERVER_TO_CLIENT | AdminInstanceSyncPayload |
| 231 | ADMIN_INSTANCE_ACTION | CLIENT_TO_SERVER | AdminInstanceActionPayload |
| 240 | NEXUS_SLOT_LIST_REQUEST | CLIENT_TO_SERVER | RequestSlotListPayload |
| 241 | NEXUS_SLOT_LIST | SERVER_TO_CLIENT | SlotListPayload |
| 242 | NEXUS_HUB_STATUS | SERVER_TO_CLIENT | HubStatusPayload |
| 243 | NEXUS_BUILD_PROGRESS | SERVER_TO_CLIENT | NexusBuildProgressPayload |

## Commands (literal tokens)

### src/main/java/com/devmod/compat/mods/dummmmmmy/DummmmmmyCommands.java
- dummy, spawn, remove, global, clear, list, stats, reset, status

### src/main/java/com/devmod/portal/command/PortalCommand.java
- portal, list, stats, nearest, \u00A7e=== Custom Portals ===, \u00A77/portal list \u00A7f- List all portals, \u00A77/portal list <color> \u00A7f- Filter by color, \u00A77/portal stats \u00A7f- Show portal statistics, \u00A77/portal nearest \u00A7f- Find nearest portal, \u00A77No portals registered, \u00A7e=== Portal Statistics ===, \u00A7eBy color:, \u00A77No portals found in this dimension, \u00A7e=== Nearest Portal ===

### src/main/java/com/devmod/area/command/AreaCommands.java
- devmod, area, === DevMod Area Commands ===, /devmod area list [page],  - List all areas, /devmod area info <name>,  - Show area details, /devmod area build <name> [clear],  - Build an area, /devmod area pause <name>,  - Pause active build, /devmod area resume <name>,  - Resume paused build, /devmod area clone <name> <newName>,  - Clone an area, /devmod area promote <name>,  - Set as main hub, /devmod area status,  - Show build queue status, /devmod area snapshot list|capture|restore|delete,  - Snapshot commands, /devmod area template list|save|delete|apply,  - Template commands, /devmod area zone list|info|link|unlink,  - Zone commands, /devmod area delete <name> <confirm>,  - Delete an area, list, No areas defined.,  - ,  [MAIN HUB], Click for details, [Next Page], info, ID: , Shape: , Dimensions: , Center: , Dimension: , Generation: , Main Hub: , YES, Build Status: , build, Area is already being built., Build queue is full. Try again later., pause, Failed to pause build., resume, clone, Invalid name. Must be 1-64 characters., An area with this name already exists., promote, Area is already the main hub., Failed to promote area (revision mismatch)., status, === Build Queue Status ===, No active builds., snapshot, capture, restore, delete, Click to restore, Failed to start snapshot capture., Snapshot restore started., Failed to start snapshot restore. Check logs for details., You must confirm deletion with 'true'. This action cannot be undone!, Failed to delete snapshot., template, save, apply, No templates defined., === Area Templates ===, Click to apply template, Failed to save template., Failed to delete template., Invalid area name. Must be 1-64 characters., zone, link, unlink, No zones defined., Display Name: , Priority: , Teleport: , Yes, Linked Areas: None, Linked Areas:, Failed to update area (revision mismatch)., Area is not linked to any zone., Cannot delete the main hub area.

### src/main/java/com/devmod/config/command/ConfigExportCommand.java
- devmod, export, --pretty, --file, hand, Must be run by a player, No item in hand, No exportable handler for this item type, No config for held item, No configs to export

### src/main/java/com/devmod/gametest/TestHarnessCommands.java
- devtest, hud, on, off, toggle, export, import, panel, debug, endurance, stats, perks, smoke, all, autosmoke, debugbox, debugclear, panelclear, info, qa, bodypart, No perk data yet, Endurance manager not initialized, Endurance auto-smoke run executed

### src/main/java/com/devmod/mailbox/admin/MailboxCommands.java
- mailbox, help, stats, send, broadcast, inbox, purge, news, list, create, delete, publish, \u00A7e=== Mailbox Admin Commands ===, \u00A77/mailbox stats \u00A7f- Show mailbox statistics, \u00A77/mailbox send <uuid> <subject> <body> \u00A7f- Send message, \u00A77/mailbox broadcast <subject> <body> \u00A7f- Broadcast to all, \u00A77/mailbox inbox <uuid> \u00A7f- View player inbox, \u00A77/mailbox purge <uuid> \u00A7f- Delete all player messages, \u00A7e=== Mailbox Statistics ===, \u00A7e=== News Admin Commands ===, \u00A77/news list \u00A7f- List all news articles, \u00A77/news create <category> <title> <content> \u00A7f- Create article, \u00A77/news delete <id> \u00A7f- Delete article, \u00A77/news publish <id> \u00A7f- Publish unpublished article

### src/main/java/com/devmod/mailbox/commands/ReportCommand.java
- report, bug, suggestion, question, other, player, list

### src/main/java/com/devmod/recipe/command/RecipeExportCommand.java
- devmod, exportrecipes, --pretty, --file, No custom recipes to export

### src/main/java/com/devmod/network/NetworkCommand.java
- devmod, network, stats, detailed, reset, health, \u00A7e=== Network Payload Metrics ===, \u00A7e--- IP Rate Limiter ---, \u00A7e=== Detailed Network Metrics ===, \u00A7e--- Size Rejections by Type ---, \u00A7e--- Rate Limit Rejections by Type ---, \u00A7e--- IP Rate Limit Rejections by Type ---, \u00A7a[Network] All payload metrics reset.

### src/main/java/com/devmod/runtime/NexusCommand.java
- devmod, nexus, help, zones, slot, list, info, link, unlink, tp, teleport, go, enter, return, exit, hub, test, riftstamp, bug, suggestion, question, status, rebuild, lock, unlock, admin, instances, \u00A7b=== DevMod Nexus ===, \u00A77/\u00A7fdevmod nexus tp <zone> \u00A77- teleport to a Nexus zone, \u00A77/\u00A7fdevmod nexus enter \u00A77- enter hub and save return, \u00A77/\u00A7fdevmod nexus return \u00A77- return to previous location, \u00A77/\u00A7fdevmod nexus bug <msg> \u00A77- file a bug report, \u00A77/\u00A7fdevmod nexus hub \u00A77- open Testing Hub (F7), \u00A77/\u00A7fdevmod nexus riftstamp \u00A77- spawn a RiftStamp portal (admin), \u00A77/\u00A7fdevmod nexus zones \u00A77- list zone ids, \u00A77/\u00A7fdevmod nexus slot list \u00A77- list hub slots, \u00A77/\u00A7fdevmod nexus slot info <id> \u00A77- show slot details, \u00A77/\u00A7fdevmod nexus slot link <id> <area> \u00A77- link area to slot, \u00A77/\u00A7fdevmod nexus slot unlink <id> \u00A77- unlink slot, \u00A77/\u00A7fdevmod nexus teleport <slotId> \u00A77- teleport to slot, \u00A77/\u00A7fdevmod nexus status \u00A77- show Nexus state, \u00A77/\u00A7fdevmod nexus rebuild \u00A77- rebuild hub (admin), \u00A7eZones: \u00A7f<none>, \u00A7eNo slots defined. Run /devmod nexus rebuild., \u00A77Linked Area: \u00A7eNone, \u00A7cFailed to link area to slot., \u00A7cFailed to unlink slot., \u00A7cPlayer required, \u00A7cNexus dimension not available., \u00A7cNexus is disabled in config, \u00A7cNo return point saved. Use /devmod nexus enter., \u00A7aReturned to your previous location, \u00A77Open Testing Hub with \u00A7fF7\u00A77 or the radial menu, \u00A7cFailed to spawn RiftStamp here., \u00A7aRiftStamp opened for 60 seconds., \u00A7cAdd more detail (min 10 chars)., \u00A7cFailed to submit ticket., \u00A7e=== Nexus Status ===, \u00A7aNexus rebuild queued, \u00A7eNexus rebuild already in progress, \u00A7aOpening Instance Control Panel...

### src/main/java/com/devmod/nexus/command/NexusCommands.java
- devmod, nexus, === DevMod Nexus Commands ===, /devmod nexus status,  - Show hub status, /devmod nexus slot list [page],  - List all slots, /devmod nexus slot info <slotId>,  - Show slot details, /devmod nexus slot link <slotId> <areaName>,  - Link area to slot, /devmod nexus slot unlink <slotId>,  - Unlink area from slot, /devmod nexus teleport <slotId>,  - Teleport to slot, /devmod nexus rebuild,  - Rebuild hub foundation, status, === Nexus Hub Status ===, Session: , Slots Initialized: , Total Slots: , Linked: ,  / Empty: , slot, list, No slots defined. Run /devmod nexus rebuild to initialize., info, Display Name: , Type: , Bounds: , Portal Color: , Linked Area: , None, link, Failed to link area to slot. Check logs for details., unlink, Failed to unlink slot. Check logs for details., teleport, This command requires a player., Nexus dimension not available., rebuild, --force, Building Nexus foundation..., Foundation build complete!, Foundation already exists. Use --force to rebuild.

### src/main/java/com/devmod/arena/zone/ZoneDebugCommand.java
- devmod, zone, debug, on, off, info

### src/main/java/com/devmod/arena/command/ArenaCommands.java
- arena, create, template, list, info, reload, status, autosmoke, run, schedule, validate, force, clear, metrics, hud, toggle, on, off, help, \u00A7e=== Arena Commands ===, \u00A77/arena create <template> \u00A7f- Create arena, \u00A77/arena template list \u00A7f- List templates, \u00A77/arena template info <id> \u00A7f- Template details, \u00A77/arena template reload \u00A7f- Reload templates, \u00A77/arena autosmoke run \u00A7f- Run smoke tests, \u00A77/arena autosmoke status \u00A7f- Autosmoke status, \u00A77/arena status \u00A7f- Arena system status, \u00A77/arena validate <id> \u00A7f- Validate template (dry-run), \u00A77/arena force <id> [mins] \u00A7f- Force template session, \u00A77/arena force clear \u00A7f- Clear forced template, \u00A77/arena metrics <id> \u00A7f- Template build metrics, \u00A77/arena hud toggle \u00A7f- Toggle debug HUD, \u00A77/arena hud status \u00A7f- Show HUD status, \u00A7cAsync arena builder not available, \u00A7cArena builder not available, \u00A77No templates loaded, \u00A7c⚠ DEPRECATED, \u00A7cReload already in progress. Please wait..., \u00A77Reloading templates..., \u00A7cReload failed with errors:, \u00A7e=== Template System Status ===, \u00A7e⟳ Reload in progress..., \u00A7cErrors:, \u00A77Last load: \u00A78Not initialized, \u00A77Bootstrap: \u00A7cNot configured, \u00A7cAutosmoke runner not configured, \u00A7cAutosmoke already running, \u00A77Starting autosmoke tests..., \u00A7cAutosmoke blocked by guard, \u00A7e=== Autosmoke Status ===, \u00A77Runner: \u00A7cNot configured, \u00A77Last run: \u00A78Never, \u00A7e=== Autosmoke Schedule ===, \u00A77Scheduler: \u00A7cNot configured, \u00A7e=== Arena System Status ===, \u00A7a✓ Template is valid, \u00A7c✗ Template has errors, \u00A77No issues found., \u00A7cForce template capability not configured, \u00A7cThis command can only be used by players, \u00A7cFailed to create force session - check permissions, \u00A7aForced template cleared, \u00A77No active force session, \u00A7e=== Active Force Sessions ===, \u00A77No active sessions, \u00A7e=== Force Template Status ===, \u00A7cYou don't have permission to use the debug HUD, \u00A7aDebug HUD enabled, \u00A77Debug HUD disabled, \u00A7e=== Debug HUD Status ===, \u00A7a✓ HUD is visible, \u00A7c✗ HUD globally disabled, \u00A77Use \u00A7f/arena hud on\u00A77 to enable

### src/main/java/com/devmod/telemetry/TelemetryReloadCommand.java
- devmod, telemetry, reload, dump, weapons, rooms, fights, minions, export, heatmaps, png, csv, json, all, scan, light, spawnability, desirelines, dungeons, backtracking, confusing, :

### src/main/java/com/devmod/telemetry/dungeon/DungeonCommand.java
- devmod, dungeon, start, end, status, \u00A7e=== Dungeon Run Debug Commands ===, \u00A77/devmod dungeon start <dungeon_id> \u00A7f- Start a debug dungeon run, \u00A77/devmod dungeon end <outcome> [kills] [deaths] [rewards] \u00A7f- End run, \u00A77/devmod dungeon status \u00A7f- Show active run status, \u00A7eOutcomes: SUCCESS, DEATH, ABANDONED, TIMEOUT, This command must be run by a player, \u00A7e=== Dungeon Run Status ===, \u00A77No completed runs yet., \u00A77Recent runs:

### src/main/java/com/devmod/telemetry/dashboard/DashboardCommand.java
- devmod, dashboard, start, stop, status, Starting dashboard server..., Dashboard server is not running., Dashboard server stopped.

### src/main/java/com/devmod/mob/MobRequirementsCommand.java
- devmod, mobrequirements, reload, list, info, test, cache, clear, generate

### src/main/java/com/devmod/npc/command/NpcDialogCommand.java
- npc, dialog, list, export, import, edit, info, reload, \u00A7e=== NPC Dialog Commands ===, \u00A77/npc dialog list \u00A7f- List all dialog sets, \u00A77/npc dialog export <id> \u00A7f- Export to JSON, \u00A77/npc dialog import <file> \u00A7f- Import from JSON, \u00A77/npc dialog edit <id> \u00A7f- Open dialog editor, \u00A77/npc dialog info <id> \u00A7f- Show dialog info, \u00A77/npc dialog reload \u00A7f- Reload dialog files, \u00A77No dialog sets found, Click to view details, Click to open file location, Only players can open the dialog editor, \u00A7eNodes:

### src/main/java/com/devmod/endurance/LeaderboardCommands.java
- leaderboard, help, list, top, me, player, weekly, arena, \u00A7e=== Leaderboard Commands ===, \u00A77/leaderboard list \u00A78- \u00A7fList all categories, \u00A77/leaderboard top <category> [limit] \u00A78- \u00A7fView top players, \u00A77/leaderboard me [category] \u00A78- \u00A7fView your rankings, \u00A77/leaderboard weekly [category] \u00A78- \u00A7fView weekly leaderboard, \u00A77/leaderboard arena <id> [category] \u00A78- \u00A7fArena-specific rankings, \u00A7e=== Leaderboard Categories ===, \u00A7cThis command can only be run by players, \u00A7e=== Your Rankings ===, \u00A7cPlayer not found, \u00A77No entries yet

### src/main/java/com/devmod/endurance/LeaderboardCommandEvents.java
- Use: /leaderboard player <name>, Use: /leaderboard arena <id>

### src/main/java/com/devmod/debug/DebugCommand.java
- devdebug, list, off, biome, \u00A7e=== DevMod Debug System ===, \u00A77/devdebug <feature> \u00A7f- Toggle a debug feature, \u00A77/devdebug list \u00A7f- Show all features and status, \u00A77/devdebug off \u00A7f- Disable all features, \u00A7eAvailable features:, This command must be run by a player, \u00A7e=== Debug Features Status ===, \u00A7e=== Biome Matching Diagnostics ===, \u00A77Testing against registered mob entities..., \u00A7eMatched mobs:, \u00A76⚠ Keywords with many matches:, \u00A77Examples: \u00A7fzombie\u00A77, \u00A7fdesert\u00A77, \u00A7fnether\u00A77, \u00A7fice, \u00A7eFiltered results:

## Keybinds

- key.devmod.attribute_monitor, key.devmod.boss_phase, key.devmod.chunk_perf, key.devmod.dash, key.devmod.dashboard
- key.devmod.debug_overlay, key.devmod.dismiss_impact_hud, key.devmod.dodge, key.devmod.economy
- key.devmod.endurance_quest, key.devmod.entity_density, key.devmod.fps_tracker, key.devmod.heatmap
- key.devmod.help_overlay, key.devmod.inspect_mob, key.devmod.light_overlay, key.devmod.los, key.devmod.mailbox
- key.devmod.notification_center, key.devmod.party, key.devmod.pathfinding, key.devmod.profiler, key.devmod.qa_testing
- key.devmod.quest_complete, key.devmod.quest_continue, key.devmod.quest_editor, key.devmod.quest_exit
- key.devmod.quest_hud, key.devmod.radial_menu, key.devmod.room_bounds, key.devmod.safe_spot, key.devmod.settings
- key.devmod.skill_efficacy, key.devmod.spawnability, key.devmod.test_shake, key.devmod.tester_tasks
- key.devmod.testing_hub, key.devmod.vertical_levels, key.devmod.weapon_editor
