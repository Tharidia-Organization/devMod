# Orphanage Inventory

**Date**: 2025-12-27
**Branch**: Banastaff

## Orphan Types

| Tipo | Descrizione |
|------|-------------|
| 1 | Never referenced - nessuna referenza dal codebase |
| 2 | Dead entrypoint - handler/screen/service non registrato o invocato |
| 3 | Legacy leftover - rimpiazzato da nuovo sistema |
| 4 | Duplicate responsibility - fa la stessa cosa di altro componente |
| 5 | Resource orphan - JSON/lang/texture non usata |
| 6 | Test orphan - test che non testa nulla di reale |
| 7 | Config orphan - config keys mai lette/applicate |

---

## KEEP + INTEGRATE

| File/Class | Tipo | Evidence | Risk if removed | Decision | Action taken |
|------------|------|----------|-----------------|----------|--------------|
| `src/main/java/com/devmod/testing/config/ConfigurableTestTemplate.java` | 2 | `rg -n "\bConfigurableTestTemplate\b" src/main/java` → solo definizione | MEDIO | KEEP+INTEGRATE | Registrato in `DynamicTestGenerator` via `registerConfigTemplates()` |
| `src/main/java/com/devmod/util/PathSanitizer.java` | 1 | `rg -n "\bPathSanitizer\b" src/main/java` → solo definizione (prima) | MEDIO | KEEP+INTEGRATE | Usato in `CsvExporter`, `JsonReportExporter`, `HeatmapExporter`; estensione `.png` consentita |

---

## REMOVE

| File/Class | Tipo | Evidence | Risk if removed | Decision | Action taken |
|------------|------|----------|-----------------|----------|--------------|
| `src/main/java/com/devmod/client/notification/render/ToastRenderer.java` | 3 | `rg -n "\bToastRenderer\b" src/main/java` → solo definizione | BASSO | REMOVE | File rimosso (sostituito da `UnifiedToastOverlay`) |
| `src/main/java/com/devmod/client/notification/render/BannerRenderer.java` | 3 | `rg -n "\bBannerRenderer\b" src/main/java` → solo definizione | BASSO | REMOVE | File rimosso (sistema banner non piu` usato) |
| `src/main/java/com/devmod/client/arena/ui/BuildProgressOverlay.java` | 2 | `rg -n "\bBuildProgressOverlay\b" src` → solo test/docs | BASSO | REMOVE | File rimosso; test e docs aggiornati |
| `src/test/java/com/devmod/arena/ui/BuildProgressOverlayTest.java` | 6 | `rg -n "\bBuildProgressOverlayTest\b" src/test/java` → solo overlay test | BASSO | REMOVE | Test rimosso (copertura specifica overlay) |
| `src/main/java/com/devmod/client/overlay/BadgePopupOverlay.java`<br>`src/main/java/com/devmod/client/overlay/ComboDecayOverlay.java`<br>`src/main/java/com/devmod/client/overlay/RecordBannerOverlay.java`<br>`src/main/java/com/devmod/client/overlay/SeasonTierUpToastOverlay.java`<br>`src/main/java/com/devmod/client/overlay/TokenGainOverlay.java`<br>`src/main/java/com/devmod/client/overlay/WelcomeToastOverlay.java` | 3 | `rg -n -e "BadgePopupOverlay" -e "ComboDecayOverlay" -e "RecordBannerOverlay" -e "SeasonTierUpToastOverlay" -e "TokenGainOverlay" -e "WelcomeToastOverlay" src` → nessun call-site; sostituiti da `NotificationService` + `UnifiedToastOverlay` | BASSO | REMOVE | File rimossi (notifiche unify) |
| `src/main/java/com/devmod/endurance/BadgeUnlockPayload.java`<br>`src/main/java/com/devmod/endurance/ComboDecayPayload.java`<br>`src/main/java/com/devmod/endurance/RecordBannerPayload.java`<br>`src/main/java/com/devmod/endurance/TokenGainPayload.java`<br>`src/main/java/com/devmod/endurance/resonance/ResonanceNotificationPayload.java`<br>`src/main/java/com/devmod/endurance/season/SeasonTierUpPayload.java` | 3 | `rg -n -e "BadgeUnlockPayload" -e "ComboDecayPayload" -e "RecordBannerPayload" -e "TokenGainPayload" -e "ResonanceNotificationPayload" -e "SeasonTierUpPayload" src` → solo commento in `UnifiedNotificationPayload` | BASSO | REMOVE | File rimossi (sostituiti da `UnifiedNotificationPayload`) |
| `src/main/java/com/devmod/mailbox/client/overlay/MailNotificationOverlay.java`<br>`src/main/java/com/devmod/mailbox/client/overlay/NewsAlertOverlay.java` | 3 | `rg -n -e "MailNotificationOverlay" -e "NewsAlertOverlay" src` → nessun call-site | BASSO | REMOVE | File rimossi (mail/news via `NotificationService`) |
| `src/main/java/com/devmod/party/PartyInvitePayload.java`<br>`src/main/java/com/devmod/party/PartyNotificationPayload.java`<br>`src/main/java/com/devmod/party/RequestOnlinePlayersPayload.java` | 2 | `rg -n -e "PartyInvitePayload" -e "PartyNotificationPayload" -e "RequestOnlinePlayersPayload" src/main/java` → solo definizioni | BASSO | REMOVE | File rimossi (payload non registrati in `NetworkHandler`) |
| `src/main/java/com/devmod/client/panels/tracking/PositionSmoother.java`<br>`src/main/java/com/devmod/client/panels/types/TestProgressPanel.java`<br>`src/main/java/com/devmod/client/panels/types/ToolStatusPanel.java` | 1 | `rg -n -e "PositionSmoother" -e "TestProgressPanel" -e "ToolStatusPanel" src` → nessun call-site | BASSO | REMOVE | File rimossi (pannelli legacy non usati) |
| `src/main/resources/assets/devmod/textures/gui/icons/radial/macro_analyze.png`<br>`src/main/resources/assets/devmod/textures/gui/icons/radial/macro_combat.png`<br>`src/main/resources/assets/devmod/textures/gui/icons/radial/macro_play.png`<br>`src/main/resources/assets/devmod/textures/gui/icons/radial/macro_tools.png` | 5 | `rg -n -e "macro_analyze" -e "macro_combat" -e "macro_play" -e "macro_tools" src/main/resources` → nessuna referenza | BASSO | REMOVE | Asset rimossi (icone macro non referenziate) |
| Debug pipeline: `src/main/java/com/devmod/debug/client/DebugClientRenderer.java`<br>`src/main/java/com/devmod/debug/DebugDataCollector.java` | 3 | `rg -n -e "\bDebugClientRenderer\b" -e "\bDebugDataCollector\b" src/main/java src/main/resources` → nessun call-site | BASSO | REMOVE | File rimossi |
| Endurance legacy: `src/main/java/com/devmod/endurance/PrestigeResetSystem.java`<br>`src/main/java/com/devmod/endurance/contracts/BloodContractRegistry.java` | 3 | `rg -n -e "\bPrestigeResetSystem\b" -e "\bBloodContractRegistry\b" src/main/java src/main/resources` → nessun call-site | MEDIO | REMOVE | File rimossi |
| Client endurance cache: `src/main/java/com/devmod/client/endurance/ClientSeasonCache.java` | 2 | `rg -n "\bClientSeasonCache\b" src/main/java src/main/resources` → nessun call-site | MEDIO | REMOVE | File rimosso |
| Telemetry legacy: `src/main/java/com/devmod/telemetry/spatial/LandmarkService.java`<br>`src/main/java/com/devmod/telemetry/spatial/JumpAnalysisService.java`<br>`src/main/java/com/devmod/telemetry/MemoryCleanupService.java` | 3 | `rg -n -e "\bLandmarkService\b" -e "\bJumpAnalysisService\b" -e "\bMemoryCleanupService\b" src/main/java src/main/resources` → nessun call-site | BASSO | REMOVE | File rimossi |
| Migration helpers: `src/main/java/com/devmod/migration/ArmorMigrationHelper.java`<br>`src/main/java/com/devmod/arena/migration/LegacyCallCheck.java` | 3 | `rg -n -e "\bArmorMigrationHelper\b" -e "\bLegacyCallCheck\b" src/main/java src/main/resources` → nessun call-site (solo test per `ArmorMigrationHelperTest`) | BASSO | REMOVE | File rimossi + test orfano eliminato |
| Client UI/editor (core + systems + components):<br>`src/main/java/com/devmod/client/ui/EscapeBehavior.java`<br>`src/main/java/com/devmod/client/ui/InputValidator.java`<br>`src/main/java/com/devmod/client/ui/components/ScrollableArea.java`<br>`src/main/java/com/devmod/client/ui/components/LoadingIndicator.java`<br>`src/main/java/com/devmod/client/ui/editor/ItemEditorRenderer.java`<br>`src/main/java/com/devmod/client/ui/editor/ItemEditorDataOps.java`<br>`src/main/java/com/devmod/client/ui/editor/ItemEditorInputHandler.java`<br>`src/main/java/com/devmod/client/ui/editor/TemplateSystem.java`<br>`src/main/java/com/devmod/client/ui/editor/VisualTesting.java`<br>`src/main/java/com/devmod/client/ui/editor/systems/ItemEditorDataService.java`<br>`src/main/java/com/devmod/client/ui/editor/systems/UndoRedoStack.java`<br>`src/main/java/com/devmod/client/ui/editor/systems/DirtyState.java`<br>`src/main/java/com/devmod/client/ui/editor/components/SectionHeader.java`<br>`src/main/java/com/devmod/client/ui/editor/components/HistoryPanel.java`<br>`src/main/java/com/devmod/client/ui/editor/core/OverlayInputGuard.java`<br>`src/main/java/com/devmod/client/ui/editor/core/FocusManager.java`<br>`src/main/java/com/devmod/client/ui/editor/core/InputHandler.java`<br>`src/main/java/com/devmod/client/ui/editor/core/LayoutManager.java`<br>`src/main/java/com/devmod/client/ui/editor/core/SectionLayout.java`<br>`src/main/java/com/devmod/client/ui/editor/core/AnimationState.java`<br>`src/main/java/com/devmod/client/ui/editor/core/RowLayout.java`<br>`src/main/java/com/devmod/client/ui/unified/ScrollableSettingsPage.java` | 3 | `rg -n -e "\bItemEditorRenderer\b" -e "\bTemplateSystem\b" -e "\bOverlayInputGuard\b" src/main/java src/main/resources` → nessun call-site (solo test `OverlayInputGuardTest` rimosso) | MEDIO | REMOVE | File rimossi + test orfano eliminato |
| Radial UI state/anim: `src/main/java/com/devmod/client/ui/radial/model/RadialMenuState.java`<br>`src/main/java/com/devmod/client/ui/radial/animation/TransitionAnimator.java` | 3 | `rg -n -e "\bRadialMenuState\b" -e "\bTransitionAnimator\b" src/main/java src/main/resources` → nessun call-site (solo test rimossi) | BASSO | REMOVE | File rimossi + test orfani eliminati |
| Arena HUD/UI entrypoints: `src/main/java/com/devmod/client/arena/hud/ArenaDebugHud.java`<br>`src/main/java/com/devmod/client/arena/hud/ArenaHudKeyBinding.java` | 2 | `rg -n -e "\bArenaDebugHud\b" -e "\bArenaHudKeyBinding\b" src/main/java src/main/resources` → nessun call-site | MEDIO | REMOVE | File rimossi |
| Arena subsystems (advanced/ops):<br>`src/main/java/com/devmod/arena/analytics/HeatmapCollector.java`<br>`src/main/java/com/devmod/arena/analytics/AnalyticsService.java`<br>`src/main/java/com/devmod/arena/policy/PolicyMutatorResolver.java`<br>`src/main/java/com/devmod/arena/validation/RuntimePreflightCheck.java`<br>`src/main/java/com/devmod/arena/validation/AdvancedArenaTemplateValidator.java`<br>`src/main/java/com/devmod/arena/spawn/RuntimeSpawnValidator.java`<br>`src/main/java/com/devmod/arena/logging/LogRotationConfig.java`<br>`src/main/java/com/devmod/arena/currency/CurrencyGrant.java`<br>`src/main/java/com/devmod/arena/monitoring/DashboardValidationJob.java`<br>`src/main/java/com/devmod/arena/monitoring/AnomalyThresholds.java`<br>`src/main/java/com/devmod/arena/challenge/ChallengeGenerator.java`<br>`src/main/java/com/devmod/arena/obsolescence/TemplateObsolescenceHandler.java`<br>`src/main/java/com/devmod/arena/telemetry/TelemetryAuditJob.java`<br>`src/main/java/com/devmod/arena/telemetry/ArenaBuildTelemetry.java`<br>`src/main/java/com/devmod/arena/report/BalanceReportJob.java`<br>`src/main/java/com/devmod/arena/admin/HotReloadEndpoint.java`<br>`src/main/java/com/devmod/arena/fallback/GracefulDegradationManager.java`<br>`src/main/java/com/devmod/arena/fallback/FallbackBuildStrategy.java`<br>`src/main/java/com/devmod/arena/rewards/RewardAntiExploit.java`<br>`src/main/java/com/devmod/arena/rewards/RewardMultiplier.java`<br>`src/main/java/com/devmod/arena/api/ResolveOptions.java`<br>`src/main/java/com/devmod/arena/api/ArenaService.java`<br>`src/main/java/com/devmod/arena/template/TemplatePlaceholder.java`<br>`src/main/java/com/devmod/arena/tags/PredefinedTag.java`<br>`src/main/java/com/devmod/arena/override/TemplateOverrideService.java`<br>`src/main/java/com/devmod/arena/health/HealthCheckEndpoint.java`<br>`src/main/java/com/devmod/arena/leaderboard/LeaderboardService.java`<br>`src/main/java/com/devmod/arena/rollout/RolloutEvaluator.java`<br>`src/main/java/com/devmod/arena/naming/InstanceName.java`<br>`src/main/java/com/devmod/arena/identity/SessionReconnectHandler.java`<br>`src/main/java/com/devmod/arena/identity/ArenaIdempotencyCache.java`<br>`src/main/java/com/devmod/arena/registry/HazardRegistry.java`<br>`src/main/java/com/devmod/arena/registry/StructureManifestLoader.java`<br>`src/main/java/com/devmod/arena/registry/HotReloadManager.java`<br>`src/main/java/com/devmod/arena/registry/TagValidator.java`<br>`src/main/java/com/devmod/arena/persistence/DuckDbRepository.java`<br>`src/main/java/com/devmod/arena/failure/ArenaFailureHandler.java`<br>`src/main/java/com/devmod/arena/recovery/TemplateRecoveryHandler.java`<br>`src/main/java/com/devmod/arena/recovery/ArenaSessionSnapshot.java`<br>`src/main/java/com/devmod/arena/recovery/ArenaRecoveryResult.java`<br>`src/main/java/com/devmod/arena/snapshot/VersionDriftDetector.java`<br>`src/main/java/com/devmod/arena/snapshot/ArenaSnapshotManager.java`<br>`src/main/java/com/devmod/arena/dryrun/DryRunEstimator.java`<br>`src/main/java/com/devmod/arena/retention/RetentionJob.java`<br>`src/main/java/com/devmod/arena/metrics/BuildTelemetry.java`<br>`src/main/java/com/devmod/arena/cleanup/RobustCleanupManager.java`<br>`src/main/java/com/devmod/arena/cleanup/CleanupResidualChecker.java`<br>`src/main/java/com/devmod/arena/integration/ArenaQuestIntegration.java`<br>`src/main/java/com/devmod/arena/gamification/BadgeUsage.java`<br>`src/main/java/com/devmod/arena/gamification/PerkSuggestionEngine.java` | 3 | `rg -n -e "\bHeatmapCollector\b" -e "\bArenaService\b" -e "\bRobustCleanupManager\b" src/main/java src/main/resources` → nessun call-site (solo test rimossi) | MEDIO | REMOVE | File rimossi + suite test orfane eliminate |
| Test orfani (solo coverage su classi rimosse):<br>`src/test/java/com/devmod/ArmorMigrationHelperTest.java`<br>`src/test/java/com/devmod/arena/analytics/HeatmapCollectorTest.java`<br>`src/test/java/com/devmod/arena/policy/PolicyMutatorResolverTest.java`<br>`src/test/java/com/devmod/arena/validation/HardeningEdgeTests.java`<br>`src/test/java/com/devmod/arena/integration/ArenaQuestIntegrationAsyncTest.java`<br>`src/test/java/com/devmod/arena/registry/StructureManifestLoaderTest.java`<br>`src/test/java/com/devmod/arena/fallback/FallbackIntegrationTest.java`<br>`src/test/java/com/devmod/arena/fallback/FallbackBuildStrategyTest.java`<br>`src/test/java/com/devmod/arena/fallback/RollbackTestScenario.java`<br>`src/test/java/com/devmod/arena/persistence/DuckDbRepositoryTest.java`<br>`src/test/java/com/devmod/arena/failure/ArenaFailureHandlerTest.java`<br>`src/test/java/com/devmod/arena/spawn/RuntimeSpawnValidatorTest.java`<br>`src/test/java/com/devmod/client/ui/radial/model/RadialMenuStateTest.java`<br>`src/test/java/com/devmod/client/ui/radial/animation/TransitionAnimatorTest.java`<br>`src/test/java/com/devmod/client/ui/editor/core/OverlayInputGuardTest.java` | 6 | `rg -n -e "\bHeatmapCollector\b" -e "\bPolicyMutatorResolver\b" -e "\bTransitionAnimator\b" src/test/java` → solo test dedicati | BASSO | REMOVE | Test rimossi insieme alle classi orfane |

---

## KEEP + QUARANTINE

Nessun elemento: quarantena risolta con rimozioni documentate in `DECISIONS.md` e `REMOVALS.md`.

---

## KEEP (False Positives - Reflection)

| File/Class | Tipo | Evidence | Risk if removed | Decision | Action taken |
|------------|------|----------|-----------------|----------|--------------|
| `src/main/java/com/devmod/gametest/L0BootVerificationTests.java` | - | `@GameTestHolder` + `@GameTest` (discovery per reflection) | ALTO | KEEP | Nessuna azione |
| `src/main/java/com/devmod/gametest/InstanceSystemGameTests.java` | - | `@GameTestHolder` + `@GameTest` (discovery per reflection) | ALTO | KEEP | Nessuna azione |

---

## Statistiche

| Categoria | Trovati | Integrati | Quarantinati | Rimossi | Keep |
|-----------|---------|-----------|--------------|--------|------|
| Codice (main) | 113 | 2 | 0 | 109 | 2 |
| Test | 16 | 0 | 0 | 16 | 0 |
| Risorse | 4 | 0 | 0 | 4 | 0 |
| **Totale** | 133 | 2 | 0 | 129 | 2 |

---

## Note

- Scansione iniziale: `docs/orphanage/_candidate_unreferenced.txt` (rg su `src/main/java`).
- Quarantena: `docs/orphanage/_quarantine_targets.txt` (risolta con rimozioni).
- Extra discovery: orfani emersi da audit doc e rimozioni pre-esistenti (notifiche/overlay/payload/panels/risorse).
