# Orphanage Removals

**Date**: 2025-12-27

## Removed Files

- `src/main/java/com/devmod/client/notification/render/ToastRenderer.java`
  - Replacement: `src/main/java/com/devmod/client/notification/render/UnifiedToastOverlay.java`
  - Evidence: `rg -n "\bToastRenderer\b" src` -> no call-sites

- `src/main/java/com/devmod/client/notification/render/BannerRenderer.java`
  - Replacement: `src/main/java/com/devmod/client/notification/render/UnifiedToastOverlay.java`
  - Evidence: `rg -n "\bBannerRenderer\b" src` -> no call-sites

- `src/main/java/com/devmod/client/arena/ui/BuildProgressOverlay.java`
  - Replacement: `src/main/java/com/devmod/client/arena/hud/BuildProgressHud.java` + `src/main/java/com/devmod/arena/network/BuildProgressPayload.java`
  - Evidence: `rg -n "\bBuildProgressOverlay\b" src` -> no call-sites

- `src/test/java/com/devmod/arena/ui/BuildProgressOverlayTest.java`
  - Reason: Test orphan after overlay removal
  - Evidence: `rg -n "\bBuildProgressOverlayTest\b" src/test/java` -> no call-sites

- `src/main/java/com/devmod/client/overlay/BadgePopupOverlay.java`
- `src/main/java/com/devmod/client/overlay/ComboDecayOverlay.java`
- `src/main/java/com/devmod/client/overlay/RecordBannerOverlay.java`
- `src/main/java/com/devmod/client/overlay/SeasonTierUpToastOverlay.java`
- `src/main/java/com/devmod/client/overlay/TokenGainOverlay.java`
- `src/main/java/com/devmod/client/overlay/WelcomeToastOverlay.java`
  - Replacement: `NotificationService` + `UnifiedToastOverlay`
  - Evidence: `rg -n "BadgePopupOverlay|ComboDecayOverlay|RecordBannerOverlay|SeasonTierUpToastOverlay|TokenGainOverlay|WelcomeToastOverlay" src` -> no call-sites

- `src/main/java/com/devmod/endurance/BadgeUnlockPayload.java`
- `src/main/java/com/devmod/endurance/ComboDecayPayload.java`
- `src/main/java/com/devmod/endurance/RecordBannerPayload.java`
- `src/main/java/com/devmod/endurance/TokenGainPayload.java`
- `src/main/java/com/devmod/endurance/resonance/ResonanceNotificationPayload.java`
- `src/main/java/com/devmod/endurance/season/SeasonTierUpPayload.java`
  - Replacement: `src/main/java/com/devmod/notification/network/UnifiedNotificationPayload.java`
  - Evidence: `rg -n "BadgeUnlockPayload|ComboDecayPayload|RecordBannerPayload|TokenGainPayload|ResonanceNotificationPayload|SeasonTierUpPayload" src` -> only comment reference

- `src/main/java/com/devmod/mailbox/client/overlay/MailNotificationOverlay.java`
- `src/main/java/com/devmod/mailbox/client/overlay/NewsAlertOverlay.java`
  - Replacement: `NotificationService` (mail/news in unified notification center)
  - Evidence: `rg -n "MailNotificationOverlay|NewsAlertOverlay" src` -> no call-sites

- `src/main/java/com/devmod/party/PartyInvitePayload.java`
- `src/main/java/com/devmod/party/PartyNotificationPayload.java`
- `src/main/java/com/devmod/party/RequestOnlinePlayersPayload.java`
  - Replacement: none (payloads never registered)
  - Evidence: `rg -n "PartyInvitePayload|PartyNotificationPayload|RequestOnlinePlayersPayload" src/main/java` -> no call-sites

- `src/main/java/com/devmod/client/panels/tracking/PositionSmoother.java`
- `src/main/java/com/devmod/client/panels/types/TestProgressPanel.java`
- `src/main/java/com/devmod/client/panels/types/ToolStatusPanel.java`
  - Replacement: none (legacy panels not referenced)
  - Evidence: `rg -n "PositionSmoother|TestProgressPanel|ToolStatusPanel" src` -> no call-sites

- `src/main/resources/assets/devmod/textures/gui/icons/radial/macro_analyze.png`
- `src/main/resources/assets/devmod/textures/gui/icons/radial/macro_combat.png`
- `src/main/resources/assets/devmod/textures/gui/icons/radial/macro_play.png`
- `src/main/resources/assets/devmod/textures/gui/icons/radial/macro_tools.png`
  - Replacement: none (macro icons unused)
  - Evidence: `rg -n "macro_(analyze|combat|play|tools)" src/main/resources` -> no references

- Debug/telemetry/endurance/migration legacy:
  - `src/main/java/com/devmod/debug/client/DebugClientRenderer.java`
  - `src/main/java/com/devmod/debug/DebugDataCollector.java`
  - `src/main/java/com/devmod/endurance/PrestigeResetSystem.java`
  - `src/main/java/com/devmod/endurance/contracts/BloodContractRegistry.java`
  - `src/main/java/com/devmod/client/endurance/ClientSeasonCache.java`
  - `src/main/java/com/devmod/telemetry/spatial/LandmarkService.java`
  - `src/main/java/com/devmod/telemetry/spatial/JumpAnalysisService.java`
  - `src/main/java/com/devmod/telemetry/MemoryCleanupService.java`
  - `src/main/java/com/devmod/migration/ArmorMigrationHelper.java`
  - `src/main/java/com/devmod/arena/migration/LegacyCallCheck.java`
  - Evidence: `rg -n -e "\bDebugClientRenderer\b" -e "\bPrestigeResetSystem\b" -e "\bMemoryCleanupService\b" src/main/java src/main/resources` -> no call-sites
  - Tests removed: `src/test/java/com/devmod/ArmorMigrationHelperTest.java`

- UI/editor/radial legacy:
  - `src/main/java/com/devmod/client/ui/EscapeBehavior.java`
  - `src/main/java/com/devmod/client/ui/InputValidator.java`
  - `src/main/java/com/devmod/client/ui/components/ScrollableArea.java`
  - `src/main/java/com/devmod/client/ui/components/LoadingIndicator.java`
  - `src/main/java/com/devmod/client/ui/editor/ItemEditorRenderer.java`
  - `src/main/java/com/devmod/client/ui/editor/ItemEditorDataOps.java`
  - `src/main/java/com/devmod/client/ui/editor/ItemEditorInputHandler.java`
  - `src/main/java/com/devmod/client/ui/editor/TemplateSystem.java`
  - `src/main/java/com/devmod/client/ui/editor/VisualTesting.java`
  - `src/main/java/com/devmod/client/ui/editor/systems/ItemEditorDataService.java`
  - `src/main/java/com/devmod/client/ui/editor/systems/UndoRedoStack.java`
  - `src/main/java/com/devmod/client/ui/editor/systems/DirtyState.java`
  - `src/main/java/com/devmod/client/ui/editor/components/SectionHeader.java`
  - `src/main/java/com/devmod/client/ui/editor/components/HistoryPanel.java`
  - `src/main/java/com/devmod/client/ui/editor/core/OverlayInputGuard.java`
  - `src/main/java/com/devmod/client/ui/editor/core/FocusManager.java`
  - `src/main/java/com/devmod/client/ui/editor/core/InputHandler.java`
  - `src/main/java/com/devmod/client/ui/editor/core/LayoutManager.java`
  - `src/main/java/com/devmod/client/ui/editor/core/SectionLayout.java`
  - `src/main/java/com/devmod/client/ui/editor/core/AnimationState.java`
  - `src/main/java/com/devmod/client/ui/editor/core/RowLayout.java`
  - `src/main/java/com/devmod/client/ui/radial/model/RadialMenuState.java`
  - `src/main/java/com/devmod/client/ui/radial/animation/TransitionAnimator.java`
  - `src/main/java/com/devmod/client/ui/unified/ScrollableSettingsPage.java`
  - `src/main/java/com/devmod/client/arena/hud/ArenaDebugHud.java`
  - `src/main/java/com/devmod/client/arena/hud/ArenaHudKeyBinding.java`
  - Evidence: `rg -n -e "\bItemEditorRenderer\b" -e "\bRadialMenuState\b" -e "\bArenaDebugHud\b" src/main/java src/main/resources` -> no call-sites
  - Tests removed: `src/test/java/com/devmod/client/ui/editor/core/OverlayInputGuardTest.java`, `src/test/java/com/devmod/client/ui/radial/model/RadialMenuStateTest.java`, `src/test/java/com/devmod/client/ui/radial/animation/TransitionAnimatorTest.java`

- Arena subsystems (non integrati):
  - `src/main/java/com/devmod/arena/analytics/HeatmapCollector.java`
  - `src/main/java/com/devmod/arena/analytics/AnalyticsService.java`
  - `src/main/java/com/devmod/arena/policy/PolicyMutatorResolver.java`
  - `src/main/java/com/devmod/arena/validation/RuntimePreflightCheck.java`
  - `src/main/java/com/devmod/arena/validation/AdvancedArenaTemplateValidator.java`
  - `src/main/java/com/devmod/arena/spawn/RuntimeSpawnValidator.java`
  - `src/main/java/com/devmod/arena/logging/LogRotationConfig.java`
  - `src/main/java/com/devmod/arena/currency/CurrencyGrant.java`
  - `src/main/java/com/devmod/arena/monitoring/DashboardValidationJob.java`
  - `src/main/java/com/devmod/arena/monitoring/AnomalyThresholds.java`
  - `src/main/java/com/devmod/arena/challenge/ChallengeGenerator.java`
  - `src/main/java/com/devmod/arena/obsolescence/TemplateObsolescenceHandler.java`
  - `src/main/java/com/devmod/arena/telemetry/TelemetryAuditJob.java`
  - `src/main/java/com/devmod/arena/telemetry/ArenaBuildTelemetry.java`
  - `src/main/java/com/devmod/arena/report/BalanceReportJob.java`
  - `src/main/java/com/devmod/arena/admin/HotReloadEndpoint.java`
  - `src/main/java/com/devmod/arena/fallback/GracefulDegradationManager.java`
  - `src/main/java/com/devmod/arena/fallback/FallbackBuildStrategy.java`
  - `src/main/java/com/devmod/arena/rewards/RewardAntiExploit.java`
  - `src/main/java/com/devmod/arena/rewards/RewardMultiplier.java`
  - `src/main/java/com/devmod/arena/api/ResolveOptions.java`
  - `src/main/java/com/devmod/arena/api/ArenaService.java`
  - `src/main/java/com/devmod/arena/template/TemplatePlaceholder.java`
  - `src/main/java/com/devmod/arena/tags/PredefinedTag.java`
  - `src/main/java/com/devmod/arena/override/TemplateOverrideService.java`
  - `src/main/java/com/devmod/arena/health/HealthCheckEndpoint.java`
  - `src/main/java/com/devmod/arena/leaderboard/LeaderboardService.java`
  - `src/main/java/com/devmod/arena/rollout/RolloutEvaluator.java`
  - `src/main/java/com/devmod/arena/naming/InstanceName.java`
  - `src/main/java/com/devmod/arena/identity/SessionReconnectHandler.java`
  - `src/main/java/com/devmod/arena/identity/ArenaIdempotencyCache.java`
  - `src/main/java/com/devmod/arena/registry/HazardRegistry.java`
  - `src/main/java/com/devmod/arena/registry/StructureManifestLoader.java`
  - `src/main/java/com/devmod/arena/registry/HotReloadManager.java`
  - `src/main/java/com/devmod/arena/registry/TagValidator.java`
  - `src/main/java/com/devmod/arena/persistence/DuckDbRepository.java`
  - `src/main/java/com/devmod/arena/failure/ArenaFailureHandler.java`
  - `src/main/java/com/devmod/arena/recovery/TemplateRecoveryHandler.java`
  - `src/main/java/com/devmod/arena/recovery/ArenaSessionSnapshot.java`
  - `src/main/java/com/devmod/arena/recovery/ArenaRecoveryResult.java`
  - `src/main/java/com/devmod/arena/snapshot/VersionDriftDetector.java`
  - `src/main/java/com/devmod/arena/snapshot/ArenaSnapshotManager.java`
  - `src/main/java/com/devmod/arena/dryrun/DryRunEstimator.java`
  - `src/main/java/com/devmod/arena/retention/RetentionJob.java`
  - `src/main/java/com/devmod/arena/metrics/BuildTelemetry.java`
  - `src/main/java/com/devmod/arena/cleanup/RobustCleanupManager.java`
  - `src/main/java/com/devmod/arena/cleanup/CleanupResidualChecker.java`
  - `src/main/java/com/devmod/arena/integration/ArenaQuestIntegration.java`
  - `src/main/java/com/devmod/arena/gamification/BadgeUsage.java`
  - `src/main/java/com/devmod/arena/gamification/PerkSuggestionEngine.java`
  - Evidence: `rg -n -e "\bHeatmapCollector\b" -e "\bArenaService\b" -e "\bRobustCleanupManager\b" src/main/java src/main/resources` -> no call-sites
  - Tests removed:
    - `src/test/java/com/devmod/arena/analytics/HeatmapCollectorTest.java`
    - `src/test/java/com/devmod/arena/policy/PolicyMutatorResolverTest.java`
    - `src/test/java/com/devmod/arena/validation/HardeningEdgeTests.java`
    - `src/test/java/com/devmod/arena/integration/ArenaQuestIntegrationAsyncTest.java`
    - `src/test/java/com/devmod/arena/registry/StructureManifestLoaderTest.java`
    - `src/test/java/com/devmod/arena/fallback/FallbackIntegrationTest.java`
    - `src/test/java/com/devmod/arena/fallback/FallbackBuildStrategyTest.java`
    - `src/test/java/com/devmod/arena/fallback/RollbackTestScenario.java`
    - `src/test/java/com/devmod/arena/persistence/DuckDbRepositoryTest.java`
    - `src/test/java/com/devmod/arena/failure/ArenaFailureHandlerTest.java`
    - `src/test/java/com/devmod/arena/spawn/RuntimeSpawnValidatorTest.java`

## Related Updates

- `src/test/java/com/devmod/arena/integration/ArenaSystemIntegrationTest.java` trimmed to remove overlay-only paths.
- Docs updated: `docs/CLIENT_BOUNDARY_AUDIT.md`, `docs/ui/UI_INVENTORY.md`, `docs/subsystems/arena-template-rework/*`.
