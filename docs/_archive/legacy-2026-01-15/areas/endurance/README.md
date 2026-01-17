# Endurance System

> Last updated: 2025-12-26
> Status: CURRENT (verified against code)

Endurance is the wave-based quest mode. It manages quest lifecycle, wave orchestration, progression systems, and client UX.

## Scope

- Quest/session lifecycle, persistence, and player state
- Wave planning, objectives, and boss waves
- Progression systems (combo, perks, rewards, difficulty, tension, gamification)
- Feature modules (challenges, contracts, guilds, season, nemesis, resonance, tide, bargains)
- Analytics/telemetry hooks

## Architecture

### Core

- `com.devmod.endurance` (EnduranceQuestManager, EnduranceQuest, EnduranceQuestRegistry, EnduranceSessionHandler, EnduranceQuestPersistence, EndurancePlayerStateManager)
- `com.devmod.endurance.config` (EnduranceConfigManager)
- `com.devmod.endurance.analytics` (EnduranceAnalytics, LiveAnalyticsHookManager, QuestResult, WaveSummary)

### Wave + Combat

- Wave orchestration: `WaveManager`, `WaveDirector`, `WaveDirective`, `WaveObjectiveState`
- Boss + hazards: `BossWaveSystem`, `SpawnAffix`, `ArenaHazardSystem`
- Combat tracking: `CombatTracker`, `DifficultyScaler`, `MutatorSystem`

### Progression

- Combos: `ComboSystem` (notifications via `NotificationService`)
- Perks: `PerkSystem`, `PerkSynergySystem`, `PerkSynergyWeb`
- Rewards + rankings: `RewardSystem`, `GamificationManager`, `LeaderboardSystem` (PrestigeResetSystem removed)
- Momentum/tension: `MomentumTracker`, `TensionSystem`, `FlowStateTracker`

### Modules

- `com.devmod.endurance.challenges` (Daily/Weekly challenges + managers)
- `com.devmod.endurance.contracts` (ActiveContractManager; BloodContractRegistry removed)
- `com.devmod.endurance.season` (SeasonPassSystem, PlayerSeasonProgress)
- `com.devmod.endurance.guild` (GuildSystem)
- `com.devmod.endurance.nemesis` (NemesisEvolutionManager)
- `com.devmod.endurance.resonance` (ResonanceChainSystem)
- `com.devmod.endurance.tide` (TideManager)
- `com.devmod.endurance.bargain` (DevilsBargainManager)
- Kits: `KitManager`, `KitPersistence`, `CustomKit`, `KitPreset`

## Client + Network

- UI screens in `com.devmod.client.endurance`: EnduranceQuestScreen, PerkSelectionScreen, WaveDirectiveScreen, QuestDeathScreen, QuestCompletionScreen, KitSelectionScreen.
- Overlay: `com.devmod.client.overlay.EnduranceQuestOverlay`.
- Client caches: `ClientShopCache`.
- Network payloads in `com.devmod.endurance`:
  StartQuestPayload, QuestSyncPayload, QuestActionPayload, QuestCompletionPayload, QuestDeathPayload,
  WaveDirectiveChoicesPayload, WaveDirectiveSelectionPayload,
  PerkChoicesPayload, PerkSelectionPayload,
  ShopSyncPayload, RequestShopSyncPayload, ShopPurchasePayload,
  BossAlertPayload, TensionUpdatePayload, InstanceLoadingPayload,
  ContractSyncPayload, ChallengeSyncPayload,
  SeasonPassPayload,
  UnifiedNotificationPayload (token/badge/record/combo/season/resonance),
  PersonalRecordsSyncPayload, RequestPersonalRecordsPayload.

## Integration

- `InstanceArenaManager` bridges endurance quests to the instance system.
- `EnduranceConfigManager` applies runtime/arena overrides sourced from `GameMechanicsConfig` and arena policy overrides.

## Automated Validation

- Tests in `src/test/java/com/devmod/endurance/**` cover combo, perk, reward, difficulty scaling, boss waves, and smoke tests.
