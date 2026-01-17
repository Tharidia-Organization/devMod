# Implementation State (Agent Reference)

> Ultimo aggiornamento: 2026-01-15
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

- Java files: 1628
- Packages: 374

### Package -> class list

#### .

- DevMod, ModConfig

#### abilities

- AbilityActionPayload, AbilityEventHandler, DashAbilitySystem, DodgeAbilitySystem, StaminaSyncPayload, StaminaSystem

#### actions

- ActionCategory, ActionCommandInvoker, ActionContext, ActionIds, ActionOrigin, ActionPrecondition, ActionPreconditions, ActionRegistry
- ActionResult, ActionType, CommandSanitizer, DevModActions, RadialAction

#### actions/client

- ActionKeybindRegistry, ClientActionContexts, DevModClientActions, OnboardingActionPayload

#### ammo

- AmmoSystem

#### area

- AreaBlockEntities, AreaBlocks, AreaCreativeTab, AreaItems, AreaModule

#### area/aesthetic

- AreaBuilderGuiConstants, AreaBuilderIcons, AreaBuilderInteraction, AreaBuilderMessages, AreaBuilderNaming, AreaBuilderParticles, AreaBuilderSounds, AreaBuilderTiming
- EditorState

#### area/block

- AreaEditorBlock, NexusEditorCentralBlock

#### area/block/entity

- AreaEditorBlockEntity, NexusEditorCentralBlockEntity

#### area/builder

- AreaBlockMapGenerator, AreaBuildTask, AreaBuildTaskManager, AreaBuilder, AreaPalettePresets, AreaShapeGenerator, BiomeAreaGenerator, BiomeRegistry

#### area/data

- AreaDefinition, AreaDimensions, AreaGenerationType, AreaOptions, AreaPalette, AreaRegistry, AreaShape, BiomeGenerationConfig

#### area/network

- AreaNetworkHandler, AreaPreviewPayload, BuildAreaPayload, OpenAreaBuilderPayload, OpenEditorCentralPayload, RequestOpenAreaBuilderPayload, SaveAreaPayload

#### arena

- ArenaCommandEvents, ArenaDebugState, BuildPhase, ProgressFlags

#### arena/alert

- AlertColors, AlertRouter, AlertRouterRegistry, ConsoleAlertChannel, DiscordAlertChannel, DuckDbAlertRecorder, ErrorContext, LogAlertChannel
- MailboxAlertChannel, TelemetryAlertChannel, WebhookAlertChannel

#### arena/analytics

- AnalyticsQueryParams

#### arena/api

- ArenaHandle

#### arena/autosmoke

- AutosmokeExceptions, AutosmokeGuard, AutosmokeReportHeader, AutosmokeReportWriter, AutosmokeRunner, AutosmokeScheduler, AutosmokeSizeThresholds, AutosmokeThresholds

#### arena/budget

- BackpressureManager, BuildBudget, BuildTimeoutException

#### arena/builder

- ArenaBuilder, AsyncArenaBuildCoordinator, AsyncArenaBuilder, BuildDryRun, BuildDryRunCalculator, BuildLimitExceededException, BuildTransaction, ChunkLoadingManager
- CompactBlockTracker, TemplateArenaBuilder

#### arena/challenge

- AvailabilityResult

#### arena/cleanup

- ArenaCleanupExecutor, BlockIntegrityVerifier, CleanupPhase, CleanupResult, CleanupVerification, PostBuildEntityAudit

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

- BuildProgressPayload

#### arena/override

- ForceTemplateCapability, OverrideManager, OverrideScope, TemplateOverride, TemplateOverrideAttachment, TemplateOverrideCapability, TemplateOverrideManager

#### arena/performance

- PerformanceBudgetEnforcer

#### arena/policy

- ArenaPolicy, ArenaPolicyRegistry, BiomeFloorMapper, MutatorBinding, PolicyResolver, PolicySchemaValidator, ResolveContext, ResolvedArena
- TemplateSuggestion, VersionCompatibilityChecker

#### arena/pool

- PoolMetrics, PoolState, PooledArena, PrebuildPoolManager

#### arena/registry

- ArenaTemplate, ArenaTemplateRegistry, BlockEntityWhitelist, Bounds, ClasspathStructureDataProvider, ContentWhitelist, CustomHazardRegistry, DiamondInheritanceException
- GoldenReference, HazardValidator, InheritanceCycleException, InheritanceDepthExceededException, InstanceSettingsValidator, ParentTemplateNotFoundException, SchemaValidator, StructureManifest
- StructureManifestParser, StructureNbtLoader, StructureValidationInitializer, TemplateChecksum, TemplateDirectoryWatcher, TemplateLoadException, TemplateLoader, TemplateManifest
- TemplateMergeRules, TemplateRegistryBootstrap, TemplateSpawnValidator, TemplateType, TemplateValidator, ValidationResult

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

- ArenaZone, ZoneDebugCommand, ZoneEnvironment, ZoneLayout, ZoneLayoutPlanner, ZoneSpawnSlotAllocator, ZoneTransition

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

- AreaBuilderScreen, AreaClientEvents, AreaClientHooks, AreaPreviewMesh, AreaPreviewRenderer, NexusEditorCentralScreen

#### client/area/widget

- BiomeConfigWidget, BiomeSelectorWidget, DimensionsWidget, OptionsWidget, PaletteEditorWidget, ShapeConfigWidget

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

- ArenaSelectionPanel, ClientArenaSuggestionsCache, ClientChallengeCache, ClientCombatFlowCache, ClientMobPoolConfigCache, ClientNutritionCache, ClientPartyStatsCache, ClientPersonalRecordsCache
- ClientQuestCache, EnduranceClientDelegate, EnduranceQuestScreen, EnduranceSettingsScreen, EnduranceShopScreen, EnduranceUiCache, EnduranceUiTheme, KitSelectionScreen
- MobPoolEditorScreen, PerkSelectionScreen, QuestCompletionScreen, QuestDeathScreen, QuestExitConfirmScreen, WaveCheckpointScreen, WaveDirectiveScreen

#### client/endurance/ui

- MobListPanel, MobStatsPanel

#### client/endurance/wis

- CombatEvent, WISClientBridge, WISOverlayHandler, WaveBriefingData, WaveIntelligenceManager, WavePhase, WaveTelemetryCollector

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

- ClientConfigFeedbackPayload, ClientConfigHandlers, ClientEnduranceHandlers, ClientImpactHandlers, ClientNetworkPayloadHooks, ClientOverlayHandlers, ClientPartyHandlers, ClientShieldHandlers
- ClientTensionCache

#### client/nexus

- NexusDialogClientHandler, NexusDialogScreen

#### client/notification

- ClientNotificationManager, ClientNotificationPreferences, NotificationActionResolver, NotificationSoundManager, NotificationUiTheme

#### client/notification/render

- UnifiedToastOverlay

#### client/notification/ui

- NotificationBadgeOverlay, NotificationCenterScreen, NotificationSettingsScreen

#### client/npc

- ActionEditorWidget, ConditionDebugWidget, ConditionEditorWidget, DialogEditorHistory, DialogEditorScreen, DialogOptionEditorScreen, DialogPreviewScreen, NpcClientHooks
- NpcConfigScreen, NpcDialogScreen

#### client/npc/graph

- DialogGraphScreen, ForceDirectedLayout, GraphCanvas, GraphConnection, GraphMinimap, GraphNode, NodeEditPanel

#### client/npc/group

- GroupDialogScreen

#### client/overlay

- BossPhaseOverlay, CombatFlowHudOverlay, CombatRecapScreen, CombatSessionTracker, ContractHudOverlay, DynamicRadiusHudOverlay, EconomyOverlay, EnduranceQuestOverlay
- EntityDensityOverlay, HeadshotFlashVFX, Impact3DPanel, Impact3DPanelManager, Impact3DRenderer, ImpactData, ImpactDisplayMode, ImpactDpsTracker
- ImpactHistory, ImpactHudContentBuilder, ImpactHudController, ImpactHudOverlay, ImpactHudPresets, ImpactHudService, ImpactVFX, InstanceLoadingOverlay
- IntegratedTestOverlay, NutritionHudOverlay, OnboardingOverlay, PartyHudOverlay, QuestSequenceOverlay, QuickHelpOverlay, ResonanceHudOverlay, SkillEfficacyOverlay
- StaminaHudOverlay, TelemetryStatusOverlay

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

- AggroRangeVisualizer, BodyPartCalculator, BodyPartRenderer, ChunkPerformanceVisualizer, CustomRenderTypes, DebugGeometryBatcher, DebugRenderer, EntityInfoOverlay
- HeatmapVisualizer, LightLevelOverlay, LineOfSightVisualizer, MobDebugOverlay, PathfindingDebugger, RenderEvents, RoomBoundsVisualizer, SafeSpotVisualizer
- SpawnabilityOverlay, SphereRenderer, TrigCache, VerticalLevelsVisualizer, WorldRenderEvents

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

- ClientLVCCache, ClientTelemetryBuffer, FpsTracker, PerformanceProfiler, TelemetryClientDelegate, TelemetryUiTheme, UiTelemetry

#### client/template

- TemplateClientHandler, TemplateEditorScreen

#### client/testing

- ActiveTestHudOverlay, BadgeTestScreen, DevModPresetTestCases, IntegratedTestSession, QAEventTracker, QANotificationSystem, QATestingScreen, TestingSession
- TestingUiTheme, TutorialManager

#### client/transport

- PartyTeleportScreen, TransportClientEvents, TransportClientPayloadHooks, TransportConfigScreen, TransportNetworkSelectScreen, TransportOverlay, WaypointSelectScreen

#### client/ui

- AxiomRenderer, BaseDevModScreen, ConfirmDialog, ErrorBoundaryScreen, ModScreen, OpenExternalConfirmScreen, RoomBoundsEditorScreen, UISound
- WelcomeScreen

#### client/ui/admin

- AdminInstanceClientHandler, AdminInstanceScreen

#### client/ui/animation

- UiAnimation, package-info

#### client/ui/components

- ComponentRegistry, CountdownTimer

#### client/ui/editor

- AbstractEditorModule, EditorModule, EditorSection, EditorStartTab, ItemEditorDataManager, ItemEditorScreen, ModuleTab, PerformanceMonitor
- PlaceholderModule, RangedWeaponModule, StaminaSystemEditor, WeaponTypeDetector

#### client/ui/editor/components

- ButtonRow, EditorButton, EditorButtonWidget, EditorSlider, EditorTextField, EditorToggle, FooterComponent, HeaderComponent
- InfoButton, ItemInfoPanel, ItemPickerOverlay, LeftColumnComponent, ModeBadge, PreviewRenderer, RecipeGridComponent, ScrollableContentArea
- SlotSelector, SourceBadge, VirtualizedList

#### client/ui/editor/controller

- InputRouter, ModeController, OverlayController

#### client/ui/editor/core

- BaseOverlay, Bounds, DarkTheme, DesignTokens, DirtyRegionTracker, EditorCache, EditorComponent, EditorConfig
- EditorConstants, EditorDimensions, EditorLayout, EditorScaleCalculator, EditorSounds, EditorSpacing, FocusRing, GridValidator
- HighContrastTheme, LightTheme, RenderObjectPool, ResponsiveLayout, ScaledCoord, ScaledSpacing, ScrollState, SectionBounds
- SliderDescriptions, StringBuilderCache, Theme, ThemeManager, TooltipManager, Typography, UiSounds

#### client/ui/editor/debug

- DebugInfo, DebugInfoSection, DebugOverlay, DebugReporter, DebugWarning, ItemDebugInfo, OverflowDetector, ValueComparison
- WarningType

#### client/ui/editor/favorites

- FavoritePresetStore

#### client/ui/editor/modules

- ArmorModule, ArmorModuleCore, ArmorModuleUI, FoodModule, FoodModuleCore, FoodModuleUI, FuelModule, FuelModuleCore
- FuelModuleUI, GeneralModule, GeneralModuleCore, GeneralModuleUI, RangedModule, RangedModuleCore, RangedModuleUI, RecipeModule
- RecipeModuleCore, RecipeModuleUI, UsableModule, UsableModuleCore, UsableModuleUI, WeaponModule, WeaponModuleCore, WeaponModuleUI
- WeaponModuleVariants

#### client/ui/editor/overlay

- EditorOverlay

#### client/ui/editor/sections

- AttributeListSection, EnchantmentListSection, InfoListSection, InputSectionAdapter, ModuleCardSection, ModuleSummarySection, SimpleHeaderSection, SimpleSpacer
- SliderSectionAdapter, TextNoteSection, ToggleSectionAdapter

#### client/ui/editor/snapshot

- ItemEditorHistoryEntry, ItemEditorSnapshot

#### client/ui/editor/state

- ItemEditorState

#### client/ui/editor/systems

- BatchEditResult, BatchUndoSnapshot, CraftingInfoPanel, DataPreset, DebugPanel, HelpOverlay, ItemEditorPresetManager, LowConfidenceDetector
- ModpackDetector, MultiEditManager, MultiEditPanel, Preset, PresetBridge, PresetManager, PresetRegistry, PresetScope
- PresetSelectorOverlay, TemplateOverlay

#### client/ui/hub

- CategoryPanel, EditorType, HubPanel, HubSectionHeader, ProgressFooter, QuickToolsPanel, TestDetailPanel, TestingHub
- TestingHubState, ToolType, Verdict

#### client/ui/overlay

- OverlayTheme

#### client/ui/radial

- RadialAction, RadialActionDetailScreen, RadialActionSafety, RadialCategory, RadialMenuActionLayout, RadialMenuBuilder, RadialMenuConfig, RadialMenuItem
- RadialMenuRegistry, RadialMenuRuntimeRegistry, RadialMenuScreen

#### client/ui/radial/animation

- Easing, RadialAnimator

#### client/ui/radial/config

- ColorTokenResolver, RadialMenuConfigValidator, RadialMenuConstants, RadialMenuDefinitionConfig, RadialMenuDefinitionLoader, RadialMenuThemeDefaults, VisibilitySupplierRegistry

#### client/ui/radial/input

- RadialSearchHandler

#### client/ui/radial/model

- MacroCategory

#### client/ui/radial/render

- RadialCategoryRenderer, RadialGeometry, RadialHubRenderer, RadialTooltipRenderer

#### client/ui/screens

- MobConfigScreen, MobConfigScreenRenderer, MobConfigScreenState, MobEquipmentScreen, TelemetryDashboardScreen, TelemetryLogViewerScreen

#### client/ui/scroll

- ScrollBehavior, ScrollManager, ScrollMetrics, ScrollMode, Scrollbar, package-info

#### client/ui/scroll/impl

- InstantScrollBehavior, SmoothScrollBehavior

#### client/ui/search

- ItemSearchQuery

#### client/ui/season

- SeasonPassScreen

#### client/ui/testing

- ImpactHudButtons, VoxelLabPage, VoxelLabScreen, VoxelLabTab, VoxelLabUiTestScreen

#### client/ui/testing/pages

- AbstractVoxelLabPage, CombatPage, ComponentShowcasePage, DebugOverlaysPage, EffectsPage, HudSystemsPage, OverviewPage, PageUtils
- TelemetryPage

#### client/ui/testing/panel

- ButtonRow, CollapsiblePanel, CompositePanel, GridPanel, HeaderPanel, PanelConstants, PanelContainer, SectionPanel
- ShowcasePanel, SliderPanel, SpacerPanel, StatusPanel, UIPanel

#### client/ui/unified

- SettingsCategory, SettingsPage, UnifiedSettingsScreen

#### client/ui/unified/pages

- CombatSettingsPage, DebugOverlaysPage, EditorSettingsPage, GeneralSettingsPage, KeybindsPage, MobConfigPage, RadialSettingsPage, TelemetryPage
- VisualizersPage

#### client/ui/unified/persistence

- SettingsData, SettingsManager

#### client/ui/wizard

- QuickTestWizard

#### client/zone

- ZoneClientCache, ZoneClientEvents, ZoneClientHooks, ZoneEditorScreen

#### client/zone/widget

- ZoneBoundsWidget, ZonePreviewRenderer

#### clone

- CloneBlockEntities, CloneBlocks, CloneCreativeTab, CloneItems, CloneMenus, CloneModule

#### clone/block

- CentrifugeBlock, CloneMachineBlock, ClonePulverizerBlock, ImprinterBlock, NeurocellBlock, NeurocellLBlock, NeurolinkBlock, ReformerBlock
- TelepadBlock

#### clone/block/entity

- CentrifugeBlockEntity, CloneMachineBlockEntity, ClonePulverizerBlockEntity, ImprinterBlockEntity, NeurocellAccess, NeurocellBlockEntity, NeurocellLBlockEntity, ReformerBlockEntity
- TelepadBlockEntity

#### clone/client

- CloneClientSetup

#### clone/client/model

- CentrifugeModel, CloneMachineItemModel, CloneMachineModel, ClonePulverizerModel

#### clone/client/renderer

- BillboardBatcher, CentrifugeRenderer, CloneMachineItemRenderer, CloneMachineRenderer, ClonePulverizerRenderer, EntityBillboardAtlas, EntityBillboardCache, NeurocellEffectsMesh
- NeurocellEffectsVBO, NeurocellLRenderer, NeurocellRenderer, PlayerCloneEntityRenderer

#### clone/client/screen

- CentrifugeScreen, NeurocellLScreen, NeurocellScreen, TelepadConfigScreen

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

- CentrifugeMenu, NeurocellLMenu, NeurocellMenu

#### clone/network

- TelepadConfigPayload, TelepadOpenScreenPayload

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

- CombatColors, DamageHandler, DamageTracker, ExecutionSystem, HitData, HitHelper, RangedProjectileHooks, ShieldDeflector

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

- Config, ConfigValidator, EditorClientConfig, GameMechanicsConfig, GameplayOverridesManager, MobConfigManager, MobPresetManager, WISClientConfig

#### config/command

- ConfigExportCommand, ConfigExportEvents

#### config/component

- BooleanComponent, FloatComponent, IntComponent, ListComponent

#### config/gamedesign

- GameDesignConfig, GameDesignConfigManager, InstanceOverride

#### config/handler

- AbstractConfigHandler, ConfigHandlerRegistry, DecomposedConfig, IConfigComponent, IConfigHandler, IDecomposedConfig

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

- DebugCommand, DebugEvents, DebugFeature, DebugManager, DebugModule, DebugNetworkHandler, DebugSyncPayload, DebugTogglePayload
- EntityGoalsPayload, EntityPathingPayload, NativeDebugSender, POIPayload, RaidsPayload

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

- ArenaContext, ArenaSuggestionsPayload, BossAlertPayload, BossWaveSystem, ClientShopCache, CombatFlowSyncPayload, CombatTracker, ComboSystem
- ComebackSystem, CustomKit, DifficultyScaler, DirectiveChain, DirectiveChainManager, EnduranceAnalytics, EnduranceColors, EnduranceConfigSyncPayload
- EnduranceEventCombat, EnduranceEventHandler, EnduranceEventTick, EnduranceMobConfigSyncPayload, EndurancePlayerStateManager, EnduranceQuest, EnduranceQuestManager, EnduranceQuestPersistence
- EnduranceQuestRegistry, EnduranceQuestState, EnduranceSessionHandler, EnduranceTags, FlowStateTracker, GamificationManager, InstanceArenaManager, InstanceLoadingPayload
- KitManager, KitPersistence, KitPreset, KitSyncConfirmPayload, KitSyncPayload, KitSyncPersistence, LeaderboardCommandEvents, LeaderboardCommands
- LeaderboardSystem, MobPoolConfigSyncPayload, MomentumTracker, MutatorSystem, PartyQuestSession, PartyStatsSyncPayload, PartyWaveStats, PerkChoicesPayload
- PerkSelectionPayload, PerkSynergySystem, PerkSystem, PersonalRecordsSyncPayload, PrestigeMilestone, QuestActionPayload, QuestCompletionPayload, QuestDeathPayload
- QuestSyncPayload, QuestType, RequestArenaSuggestionsPayload, RequestMobPoolConfigPayload, RequestPersonalRecordsPayload, RequestShopSyncPayload, RewardSystem, ShopPurchasePayload
- ShopSyncPayload, SpawnAffix, StartQuestPayload, TensionSystem, TensionUpdatePayload, WaveDirective, WaveDirectiveChoicesPayload, WaveDirectiveSelectionPayload
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

#### endurance/config

- ConfigProposalManager, ConfigScope, EffectiveConfig, EnduranceConfigManager, EnduranceMobConfig, EnduranceMobPoolConfig, GlobalMobConfigStorage

#### endurance/contracts

- ActiveContractManager, BloodContract, ContractSyncPayload

#### endurance/guild

- Guild, GuildSystem

#### endurance/hazard

- ArenaHazardSystem

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

- DevModGameTests, DevModTestStructures, InstanceSystemGameTests, L0BootVerificationTests, TestHarnessCommands

#### hologram

- HologramBlockEntities, HologramBlocks, HologramItems, HologramModule

#### hologram/block

- HologramProjectorBlock

#### hologram/block/entity

- HologramProjectorBlockEntity

#### hologram/client

- HologramClientSetup

#### hologram/client/renderer

- ArenaPreviewMesh, ArenaPreviewRenderer, HologramMesh, HologramMeshBuilder, HologramRenderer, HologramVBO

#### hologram/client/screen

- HologramConfigScreen

#### hologram/data

- HologramBillboard, HologramDefinition, HologramOptions, HologramPosition, HologramPreset, HologramRegistry, HologramState, HologramStyle
- HologramType

#### hologram/item

- HologramPlacerItem

#### hologram/network

- DeleteHologramPayload, HologramConfigPayload, HologramOpenScreenPayload, HologramSyncPayload, OpenHologramEditorPayload, SaveHologramPayload

#### hologram/runtime

- HologramManager, HologramMigration, HologramNaming, HologramParticles, HologramPlaceholderResolver, HologramSounds

#### integration

- DistantHorizonsIntegration, LittleTilesIntegration, ModIntegrationManager, PehkuiIntegration, PufferfishCompat, PufferfishIntegration

#### mailbox

- MailboxConfig, MailboxManager, MailboxMessage, MailboxPermissions, MessageType

#### mailbox/admin

- MailboxCommandEvents, MailboxCommands

#### mailbox/analytics

- MailboxAnalyticsEngine

#### mailbox/api

- ApiServerLauncher, AuthMiddleware, JavalinBootstrap, MailboxApiServer

#### mailbox/api/controllers

- AdminAuditController, AnalyticsController, BroadcastController, ConfigController, MessageController, NewsController, SearchController, SecurityMetricsDto
- SuccessResponse, TaskController, TicketController, UserController

#### mailbox/attachment

- AttachmentTransactionLog, AttachmentValidator, CompositeAttachment, CurrencyAttachment, ItemAttachment, MailAttachment

#### mailbox/broadcast

- BroadcastQueueWorker

#### mailbox/client

- ClientMailboxAccess, ClientMailboxCache, ClientNewsCache, ClientTaskCache, ClientTicketCache, MailboxUiSkin, MailboxUiTheme

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

- MailboxAccessPayload, MailboxActionPayload, MailboxNotifyPayload, MailboxSendPayload, MailboxStatusPayload, MailboxSyncPayload, NewsReadPayload, NewsSyncPayload
- TaskActionPayload, TaskSyncPayload, TicketActionPayload, TicketCreatePayload, TicketSyncPayload, TicketSyncRequestPayload

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

- AutoTransitionService, Ticket, TicketCategory, TicketComment, TicketManager, TicketPriority, TicketRepository, TicketStatus
- TicketWorkflow

#### mailbox/webhook

- WebhookManager

#### mixin

- CommandsMixin, DebugPacketsMixin, DevModMixinPlugin, MinecraftServerAccessor, MobDespawnMixin, RecipeManagerMixin, VanillaPackResourcesBuilderMixin

#### mixin/client

- CameraShakeMixin, ClientLevelTimeMixin, DebugRendererMixin, FabricScreenApiFixMixin, GameRendererMixin, HexereiDynamicRegistriesMixin, LevelAccessorTimeMixin, LivingEntityRendererMixin
- ModelPartTransformMixin, MoreCullingCompatMixin, SoundManagerMixin

#### mob

- EnhancedMobRequirements, EnhancedMobRequirementsRegistry, MobRequirements, MobRequirementsCommand, MobRequirementsDetector, MobRequirementsLoader, MobRequirementsRegistry, SpawnSource
- SpawnSourceDetector, StructureCharacteristics

#### network

- ArmorStatsPayload, ChannelId, EditorApplyConfirmPayload, EquipMobPayload, FoodStatsPayload, FuelStatsPayload, GameMechanicsSyncPayload, GlobalConfigSyncPayload
- ImpactSyncPayload, IpRateLimiter, MobConfigConfirmPayload, ModifyItemPayload, NetworkCommand, NetworkHandler, PacketValidator, PayloadValidation
- PayloadValidationEvents, RangedWeaponStatsPayload, RecipeClientSyncPayload, RecipeSyncPayload, ShieldImpactPayload, ShieldShatterPayload, ShieldStatePayload, UpdateArmorPayload
- UpdateMobStatsPayload, UpdateWeaponPayload, UsableStatsPayload, WeaponStatsPayload, ZoneDebugPayload

#### network/handlers

- AbilityNetworkHandler, ConfigNetworkHandler, EnduranceNetworkHandler, MobItemNetworkHandler, NetworkHandlerBase, PartyNetworkHandler, PayloadRegistrar, ShieldNetworkHandler

#### network/protocol

- MessageEnvelope, MessageType, PayloadCodecRegistry

#### nexus

- NexusDecorBlocks

#### notification

- Notification, NotificationCategory, NotificationCenterActionData, NotificationParamsCodec, NotificationPriority, NotificationRouter, NotificationService, PartyInviteActionData
- PartyNotificationBridge

#### notification/network

- NotificationNetworkHandler, NotificationPreferencesSyncPayload, NotificationPreferencesUpdatePayload, UnifiedNotificationPayload

#### notification/persistence

- NotificationHistoryRepository, NotificationPreferencesRepository

#### npc

- NpcEmotion, NpcModule, NpcParticles, NpcSounds, NpcState

#### npc/command

- NpcDialogCommand

#### npc/component

- NpcComponents

#### npc/data

- DialogMemory, NpcAppearance, NpcBehavior, NpcConfiguration, NpcRegistry, NpcVisitData

#### npc/dialog

- DialogNode, DialogOption, DialogPresets, DialogRegistry, DialogSchedule, DialogSet, NpcContext, NpcDialogManager, NpcDialogSession
- PlaceholderResolver

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

- NpcDialogActionPayload, NpcDialogPayload, NpcNetworkHandler, OpenDialogEditorPayload, OpenNpcConfigPayload, SaveDialogPayload, SaveNpcConfigPayload

#### party

- ArrivalConfirmPayload, CancelSequencePayload, InviteResponsePayload, NamedInvitePayload, OnlinePlayersPayload, PartyActionPayload, PartyData, PartyInvite
- PartyManager, PartySyncPayload, QuestSequencePayload, QuestStartSequence

#### portal

- PortalBlocks, PortalClientSetup, PortalColor, PortalConfig, PortalCreativeTab, PortalData, PortalEvents, PortalFrameDetector
- PortalItems, PortalRegistry, PortalRuneEffects, RuneType

#### portal/block

- CustomPortalBlock, RuneBlock

#### portal/client

- PortalClientEvents, PortalPreviewOverlay, PortalTeleportOverlay

#### portal/command

- PortalCommand

#### portal/item

- PortalIgniterItem, PortalLinkerItem

#### portal/network

- PortalPreviewPayload, PortalPreviewRequestPayload, PortalStatePayload

#### quest

- QuestData, QuestManager, QuestTask

#### recipe

- CraftingRecipeData, CraftingType, IngredientData, RecipeCategory, RecipeConfigManager, RecipeData, RecipeInjector, RecipeReloadListener
- RecipeValidator, ResultData, SmeltingRecipeData, SmeltingType, SmithingRecipeData, SmithingType, StonecuttingRecipeData

#### recipe/command

- RecipeExportCommand, RecipeExportEvents

#### recipe/export

- RecipeExportHelper

#### rendering

- HeatmapColors, HeatmapType

#### runtime

- DynamicDimensionManager, InstanceData, InstanceEventHandler, InstanceLevelData, InstanceManager, InstanceRegistry, InstanceState, NexusBuildStep
- NexusBuildTask, NexusCommand, NexusDialogContext, NexusDialogManager, NexusDimensionManager, NexusDummyFeedback, NexusEventHandler, NexusHubBuilder
- NexusHubSavedData, NexusPalette, NexusPerformanceManager, NexusPortalManager, NexusReturnSavedData, NexusSpawnManager, PlayerInstanceSnapshot, PlayerInstanceState
- RecoverySystem, RiftStampEventHandler, RiftStampManager

#### runtime/biome

- ModBiomeSources, ZoneBiomeSource

#### runtime/environment

- DimensionEnvironmentManager, EnvironmentSyncPayload, TimeController

#### runtime/generator

- ArenaChunkGenerator, ArenaFlatChunkGenerator, BiomePolicyResolver, DynamicArenaChunkGenerator, ModChunkGenerators

#### runtime/network

- AdminInstanceActionPayload, AdminInstanceNetworkHandler, AdminInstanceSyncPayload, NexusDialogActionPayload, NexusDialogPayload, NexusLogRequestPayload, NexusLogSnapshotPayload, NexusLogType
- NexusNetworkHandler, NexusUiPayload

#### security

- PermissionGuard

#### shared

- SharedColorTokens

#### stats

- ArmorStats, FoodStats, FuelStats, RangedWeaponStats, ShieldColors, UsableStats, WeaponStats

#### tags

- ModTags

#### telemetry

- AsyncTelemetryWriter, BossPhaseDetector, DeferredEntityProcessor, EffectSkillTracker, EnchantmentSkillTracker, RoomDefinition, TelemetryConfig, TelemetryEvents
- TelemetryJson, TelemetryLogHandlers, TelemetryReloadCommand, TelemetryService, TelemetrySettings

#### telemetry/boss

- BossPhaseService, UnifiedBossDetector

#### telemetry/combat

- FightSessionService

#### telemetry/damage

- DamageTrackingService

#### telemetry/dashboard

- DashboardCommand, TelemetryAnalyticsHandlers, TelemetryDashboardServer

#### telemetry/duckdb

- ArenaRecords, DuckDBBatchWriter, DuckDBBootstrap, DuckDBConfig, DuckDBConnectionManager, DuckDBErrorClassifier, DuckDBMigrationService, DuckDBQueryAPI
- DuckDBSchemaManager, DuckDBTelemetryService, LatencyTracker, RateLimitedLogger

#### telemetry/duckdb/aggregation

- AbilityAggregateWindow, AggregationConfig, CombatAggregateWindow, HeatmapAggregateWindow, SnapshotSampler, TelemetryAggregator, TelemetryAggregatorRegistry

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

- AnchorPoint, ComponentCategory, ComponentDefinition, ComponentPlacement, RelativePosition, RoomTemplate, TemplateRegistry, TemplateSpacing
- ZoneTemplate

#### template/network

- ApplyTemplatePayload, OpenTemplateEditorPayload, SaveTemplatePayload, TemplateNetworkHandler

#### template/runtime

- ComponentBuilder, TemplateManager, TemplatePresets

#### testing

- DevModArmorTestCases, DynamicTestGenerator, ModDiscoveryService, TestCase, TesterProfile, TesterProgress, TestingColors

#### testing/config

- ConfigurableTestTemplate, ModTestConfig

#### testing/stats

- AchievementTracker, CombatEventStatistics, DamageStatistics, EnchantmentStatistics, EnvironmentalDamageStats, ExplosionStatistics, HazardTypeRegistry, KillStatistics
- ModInteractionTracker, OverlayUsageTracker, PotionStatistics, SessionStatistics

#### transport

- TransportBlockEntities, TransportBlocks, TransportColor, TransportData, TransportEnhancement, TransportEventHandler, TransportMode, TransportModule
- TransportNodeType, TransportRegistry, TransportState

#### transport/block

- ChromaticTransportModuleBlock, TransportCoreBlock, TransportFrameBlock, TransportModuleBlock

#### transport/block/entity

- TransportCoreBlockEntity

#### transport/bridge

- InstanceDimensionBridge, LegacyTransportBridge, NexusTransportBridge, QuestTransportBridge, package-info

#### transport/executor

- ArrivalManager, ChargeManager, CooldownManager, CountdownManager, RecoveryManager, TransportEffectManager, TransportExecutor

#### transport/manager

- RiftGateManager, package-info

#### transport/network

- TransportArrivalConfirmPayload, TransportCancelPartyPayload, TransportChargeUpdatePayload, TransportConfigOpenPayload, TransportConfigSavePayload, TransportCountdownPayload, TransportDeleteWaypointPayload, TransportNetworkHandler
- TransportNetworkListPayload, TransportPartyStatusPayload, TransportStatePayload, TransportWaypointSelectPayload

#### util

- ColorMasks, ConfigPaths, ContextLogger, DamageTypeConfig, DatapackIO, FollowRangeColors, HotPathLogger, I18n
- ItemLookup, ItemStackResetUtils, MessageColors, MixinLogFilter, NullGuard, ObjectPool, PathSanitizer, ThreadLocalPool

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

- DeleteZonePayload, OpenZoneEditorPayload, SaveZonePayload, ZoneEnterPayload, ZoneNetworkHandler, ZoneSyncPayload

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

- ammo_tag_filter
- armor_stats
- base_arrow_damage
- draw_time_ticks
- food_stats
- fuel_stats
- multishot_count
- piercing_level
- projectile_gravity
- projectile_speed
- projectile_spread
- usable_stats
- weapon_stats

### Entities

- player_clone

## Assets inventory

- blockstates: 201
- item models: 223
- block models: 421
- animations: 30
- geo: 30
- textures: {'.DS_Store': 1, 'block': 318, 'gui': 5, 'item': 40}

### Block IDs (from blockstates)

- area_editor, centrifuge, chromatic_lens, clone_assembler, clone_atomic_forge, clone_battery, clone_bio_generator, clone_centrifuge_l, clone_charger, clone_conveyor
- clone_crusher, clone_drill, clone_energy_pipe, clone_fertilizer, clone_foundry, clone_fuel_generator, clone_laser_arm, clone_lava_generator, clone_motor, clone_processor
- clone_pulverizer, clone_pump, clone_reactor, clone_refinery, clone_shrinker, clone_smelter, clone_solar_panel, clone_steam_engine, clone_storage_unit, clone_tank
- clone_tech_door, clone_treefeller, custom_portal, dimensional_gate, entity_scanner, flux_capacitor, frame_segment, hologram_projector, imprinter, memory_core
- network_relay, neurocell, neurocell_l, neurolink, nexus_azure, nexus_azure_dark, nexus_azure_dark_slab, nexus_azure_light, nexus_azure_light_slab, nexus_azure_slab
- nexus_binary, nexus_binary_slab, nexus_brick, nexus_brick_slab, nexus_bronze, nexus_bronze_slab, nexus_carbon, nexus_carbon_slab, nexus_checker, nexus_checker_slab
- nexus_circuit, nexus_circuit_gold, nexus_circuit_gold_slab, nexus_circuit_slab, nexus_cobalt, nexus_cobalt_slab, nexus_conduit, nexus_conduit_slab, nexus_conduit_yellow, nexus_conduit_yellow_slab
- nexus_copper, nexus_copper_slab, nexus_core, nexus_core_slab, nexus_cross, nexus_cross_slab, nexus_crystal, nexus_crystal_light, nexus_crystal_light_slab, nexus_crystal_slab
- nexus_data, nexus_data_slab, nexus_diamond, nexus_diamond_slab, nexus_display, nexus_display_red, nexus_display_red_slab, nexus_display_slab, nexus_dots, nexus_dots_slab
- nexus_editor_central, nexus_ember, nexus_ember_slab, nexus_energy, nexus_energy_light, nexus_energy_light_slab, nexus_energy_slab, nexus_frame, nexus_frame_slab, nexus_frame_white
- nexus_frame_white_slab, nexus_glow, nexus_glow_slab, nexus_gold, nexus_gold_slab, nexus_grid, nexus_grid2, nexus_grid2_slab, nexus_grid_gold, nexus_grid_gold_slab
- nexus_grid_slab, nexus_hazard, nexus_hazard_slab, nexus_hex, nexus_hex_slab, nexus_hologram, nexus_hologram_slab, nexus_iris, nexus_iris_slab, nexus_light
- nexus_light_slab, nexus_matrix, nexus_matrix_light, nexus_matrix_light_slab, nexus_matrix_slab, nexus_mesh, nexus_mesh_slab, nexus_mint, nexus_mint_slab, nexus_neon
- nexus_neon_blue, nexus_neon_blue_slab, nexus_neon_slab, nexus_onyx, nexus_onyx_slab, nexus_panel, nexus_panel_dark, nexus_panel_dark_slab, nexus_panel_slab, nexus_plasma
- nexus_plasma_dark, nexus_plasma_dark_slab, nexus_plasma_light, nexus_plasma_light_slab, nexus_plasma_slab, nexus_plating, nexus_plating_slab, nexus_portal, nexus_pulse, nexus_pulse_slab
- nexus_quantum, nexus_quantum_slab, nexus_reactor, nexus_reactor_light, nexus_reactor_light_slab, nexus_reactor_slab, nexus_rose, nexus_rose_slab, nexus_scales, nexus_scales_slab
- nexus_signal, nexus_signal_slab, nexus_silver, nexus_silver_slab, nexus_smooth, nexus_smooth_slab, nexus_spiral, nexus_spiral_slab, nexus_steel, nexus_steel_slab
- nexus_stripes, nexus_stripes_slab, nexus_tech, nexus_tech_slab, nexus_terminal, nexus_terminal_green, nexus_terminal_green_slab, nexus_terminal_slab, nexus_tile, nexus_tile_purple
- nexus_tile_purple_slab, nexus_tile_slab, nexus_vent, nexus_vent_red, nexus_vent_red_slab, nexus_vent_slab, nexus_void, nexus_void_slab, nexus_wave, nexus_wave_slab
- party_beacon, range_amplifier, reformer, rune_enhancer, rune_gate, rune_haste, rune_infinity, rune_strong_enhancer, telepad, warp_core
- zone_marker

### Item IDs (from models/item)

- area_editor, bioscanner, centrifuge, chromatic_lens, clone_assembler, clone_atomic_forge, clone_battery, clone_bio_generator, clone_centrifuge_l, clone_charger
- clone_conveyor, clone_crusher, clone_drill, clone_energy_pipe, clone_fertilizer, clone_foundry, clone_fuel_generator, clone_laser_arm, clone_lava_generator, clone_motor
- clone_processor, clone_pulverizer, clone_pump, clone_reactor, clone_refinery, clone_shrinker, clone_smelter, clone_solar_panel, clone_steam_engine, clone_storage_unit
- clone_tank, clone_tech_door, clone_treefeller, dimensional_gate, entity_scanner, flux_capacitor, frame_segment, grinder, hologram_placer, hologram_projector
- imprinter, manual, memory_core, network_relay, neurocell, neurocell_l, neurocell_npc, neurolink, nexus_azure, nexus_azure_dark
- nexus_azure_dark_slab, nexus_azure_light, nexus_azure_light_slab, nexus_azure_slab, nexus_binary, nexus_binary_slab, nexus_brick, nexus_brick_slab, nexus_bronze, nexus_bronze_slab
- nexus_carbon, nexus_carbon_slab, nexus_checker, nexus_checker_slab, nexus_circuit, nexus_circuit_gold, nexus_circuit_gold_slab, nexus_circuit_slab, nexus_cobalt, nexus_cobalt_slab
- nexus_conduit, nexus_conduit_slab, nexus_conduit_yellow, nexus_conduit_yellow_slab, nexus_copper, nexus_copper_slab, nexus_core, nexus_core_slab, nexus_cross, nexus_cross_slab
- nexus_crystal, nexus_crystal_light, nexus_crystal_light_slab, nexus_crystal_slab, nexus_data, nexus_data_slab, nexus_diamond, nexus_diamond_slab, nexus_display, nexus_display_red
- nexus_display_red_slab, nexus_display_slab, nexus_dots, nexus_dots_slab, nexus_editor_central, nexus_ember, nexus_ember_slab, nexus_energy, nexus_energy_light, nexus_energy_light_slab
- nexus_energy_slab, nexus_frame, nexus_frame_slab, nexus_frame_white, nexus_frame_white_slab, nexus_glow, nexus_glow_slab, nexus_gold, nexus_gold_slab, nexus_grid
- nexus_grid2, nexus_grid2_slab, nexus_grid_gold, nexus_grid_gold_slab, nexus_grid_slab, nexus_hazard, nexus_hazard_slab, nexus_hex, nexus_hex_slab, nexus_hologram
- nexus_hologram_slab, nexus_iris, nexus_iris_slab, nexus_light, nexus_light_slab, nexus_matrix, nexus_matrix_light, nexus_matrix_light_slab, nexus_matrix_slab, nexus_mesh
- nexus_mesh_slab, nexus_mint, nexus_mint_slab, nexus_neon, nexus_neon_blue, nexus_neon_blue_slab, nexus_neon_slab, nexus_onyx, nexus_onyx_slab, nexus_panel
- nexus_panel_dark, nexus_panel_dark_slab, nexus_panel_slab, nexus_plasma, nexus_plasma_dark, nexus_plasma_dark_slab, nexus_plasma_light, nexus_plasma_light_slab, nexus_plasma_slab, nexus_plating
- nexus_plating_slab, nexus_portal, nexus_pulse, nexus_pulse_slab, nexus_quantum, nexus_quantum_slab, nexus_reactor, nexus_reactor_light, nexus_reactor_light_slab, nexus_reactor_slab
- nexus_rose, nexus_rose_slab, nexus_scales, nexus_scales_slab, nexus_signal, nexus_signal_slab, nexus_silver, nexus_silver_slab, nexus_smooth, nexus_smooth_slab
- nexus_spiral, nexus_spiral_slab, nexus_steel, nexus_steel_slab, nexus_stripes, nexus_stripes_slab, nexus_tech, nexus_tech_slab, nexus_terminal, nexus_terminal_green
- nexus_terminal_green_slab, nexus_terminal_slab, nexus_tile, nexus_tile_purple, nexus_tile_purple_slab, nexus_tile_slab, nexus_vent, nexus_vent_red, nexus_vent_red_slab, nexus_vent_slab
- nexus_void, nexus_void_slab, nexus_wave, nexus_wave_slab, party_beacon, portal_igniter_black, portal_igniter_blue, portal_igniter_brown, portal_igniter_cyan, portal_igniter_gray
- portal_igniter_green, portal_igniter_light_blue, portal_igniter_light_gray, portal_igniter_lime, portal_igniter_magenta, portal_igniter_orange, portal_igniter_pink, portal_igniter_purple, portal_igniter_red, portal_igniter_white
- portal_igniter_yellow, portal_linker, range_amplifier, reformer, rune_enhancer, rune_gate, rune_haste, rune_infinity, rune_strong_enhancer, telepad
- viewer_item, warp_core, zone_marker

### GeckoLib assets

- Animations:
  - centrifuge.animation, clone_assembler.animation, clone_atomic_forge.animation, clone_battery.animation, clone_bio_generator.animation, clone_centrifuge_l.animation, clone_charger.animation, clone_conveyor.animation, clone_crusher.animation, clone_drill.animation
  - clone_energy_pipe.animation, clone_fertilizer.animation, clone_foundry.animation, clone_fuel_generator.animation, clone_laser_arm.animation, clone_lava_generator.animation, clone_motor.animation, clone_processor.animation, clone_pulverizer.animation, clone_pump.animation
  - clone_reactor.animation, clone_refinery.animation, clone_shrinker.animation, clone_smelter.animation, clone_solar_panel.animation, clone_steam_engine.animation, clone_storage_unit.animation, clone_tank.animation, clone_tech_door.animation, clone_treefeller.animation
- Geo models:
  - centrifuge.geo, clone_assembler.geo, clone_atomic_forge.geo, clone_battery.geo, clone_bio_generator.geo, clone_centrifuge_l.geo, clone_charger.geo, clone_conveyor.geo, clone_crusher.geo, clone_drill.geo
  - clone_energy_pipe.geo, clone_fertilizer.geo, clone_foundry.geo, clone_fuel_generator.geo, clone_laser_arm.geo, clone_lava_generator.geo, clone_motor.geo, clone_processor.geo, clone_pulverizer.geo, clone_pump.geo
  - clone_reactor.geo, clone_refinery.geo, clone_shrinker.geo, clone_smelter.geo, clone_solar_panel.geo, clone_steam_engine.geo, clone_storage_unit.geo, clone_tank.geo, clone_tech_door.geo, clone_treefeller.geo

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

## Config inventory

### Runtime TOML
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
| 200 | ZONE_EDITOR_OPEN | SERVER_TO_CLIENT | OpenZoneEditorPayload |
| 201 | ZONE_SAVE | CLIENT_TO_SERVER | SaveZonePayload |
| 202 | ZONE_DELETE | CLIENT_TO_SERVER | DeleteZonePayload |
| 203 | ZONE_SYNC | SERVER_TO_CLIENT | ZoneSyncPayload |
| 204 | ZONE_ENTER | SERVER_TO_CLIENT | ZoneEnterPayload |
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
| 230 | ADMIN_INSTANCE_SYNC | SERVER_TO_CLIENT | AdminInstanceSyncPayload |
| 231 | ADMIN_INSTANCE_ACTION | CLIENT_TO_SERVER | AdminInstanceActionPayload |

## Commands (literal tokens)

### src/main/java/com/devmod/arena/command/ArenaCommands.java
- arena, create, template, list, info, reload, status, autosmoke, run, schedule
- validate, force, clear, metrics, hud, toggle, on, off, help

### src/main/java/com/devmod/arena/zone/ZoneDebugCommand.java
- devmod, zone, debug, on, off, info

### src/main/java/com/devmod/compat/mods/dummmmmmy/DummmmmmyCommands.java
- dummy, spawn, remove, global, clear, list, stats, reset, status

### src/main/java/com/devmod/config/command/ConfigExportCommand.java
- devmod, export, --pretty, --file, hand

### src/main/java/com/devmod/debug/DebugCommand.java
- devdebug, list, off, biome

### src/main/java/com/devmod/endurance/LeaderboardCommands.java
- leaderboard, help, list, top, me, player, weekly, arena

### src/main/java/com/devmod/gametest/TestHarnessCommands.java
- devtest, hud, on, off, toggle, export, import, panel, debug, endurance
- stats, perks, smoke, all, autosmoke, debugbox, debugclear, panelclear, info, qa
- bodypart

### src/main/java/com/devmod/mailbox/admin/MailboxCommands.java
- mailbox, help, stats, send, broadcast, inbox, purge, news, list, create
- delete, publish

### src/main/java/com/devmod/mailbox/commands/ReportCommand.java
- report, bug, suggestion, question, other, player, list

### src/main/java/com/devmod/mob/MobRequirementsCommand.java
- devmod, mobrequirements, reload, list, info, test, cache, clear, generate

### src/main/java/com/devmod/network/NetworkCommand.java
- devmod, network, stats, detailed, reset, health

### src/main/java/com/devmod/npc/command/NpcDialogCommand.java
- npc, dialog, list, export, import, edit, info, reload

### src/main/java/com/devmod/portal/command/PortalCommand.java
- portal, list, stats, nearest

### src/main/java/com/devmod/recipe/command/RecipeExportCommand.java
- devmod, exportrecipes, --pretty, --file

### src/main/java/com/devmod/runtime/NexusCommand.java
- devmod, nexus, help, zones, tp, go, enter, return, exit, hub
- test, riftstamp, bug, suggestion, question, status, rebuild, lock, unlock, admin
- instances

### src/main/java/com/devmod/telemetry/TelemetryReloadCommand.java
- devmod, telemetry, reload, dump, weapons, rooms, fights, minions, export, heatmaps
- png, csv, json, all, scan, light, spawnability, desirelines, dungeons, backtracking
- confusing

### src/main/java/com/devmod/telemetry/dashboard/DashboardCommand.java
- devmod, dashboard, start, stop, status

### src/main/java/com/devmod/telemetry/dungeon/DungeonCommand.java
- devmod, dungeon, start, end, status

## Keybinds

- key.devmod.attribute_monitor, key.devmod.boss_phase, key.devmod.chunk_perf, key.devmod.dash, key.devmod.dashboard, key.devmod.debug_overlay, key.devmod.dismiss_impact_hud, key.devmod.dodge, key.devmod.economy, key.devmod.endurance_quest
- key.devmod.entity_density, key.devmod.fps_tracker, key.devmod.heatmap, key.devmod.help_overlay, key.devmod.inspect_mob, key.devmod.light_overlay, key.devmod.los, key.devmod.mailbox, key.devmod.notification_center, key.devmod.party
- key.devmod.pathfinding, key.devmod.profiler, key.devmod.qa_testing, key.devmod.quest_complete, key.devmod.quest_continue, key.devmod.quest_editor, key.devmod.quest_exit, key.devmod.quest_hud, key.devmod.radial_menu, key.devmod.room_bounds
- key.devmod.safe_spot, key.devmod.settings, key.devmod.skill_efficacy, key.devmod.spawnability, key.devmod.test_shake, key.devmod.tester_tasks, key.devmod.testing_hub, key.devmod.vertical_levels, key.devmod.weapon_editor
