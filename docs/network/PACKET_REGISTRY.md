# DevMod Network Packet Registry

**Last Updated:** 2025-12-24  
**Source of Truth:** `src/main/java/com/devmod/network/ChannelId.java`

All payloads are registered via `RegisterPayloadHandlersEvent` in `NetworkHandler.java` (plus debug registrations in `DebugNetworkHandler.java`).

## Channel Ranges

| Range | Domain | Notes |
|-------|--------|-------|
| 1-4 | MOB/ITEM | Core entity editing |
| 5-25 | ENDURANCE | Quest system |
| 26-35 | PARTY | Party system |
| 36-45 | CONFIG/TELEMETRY | Config + telemetry |
| 46-55 | ITEM STATS | Weapon/armor/food/fuel |
| 56-65 | SHIELD | Shield VFX |
| 66-75 | ABILITY | Dash/dodge/stamina |
| 76-85 | ARENA | Build progress |
| 86-89 | CHALLENGES | Challenge sync |
| 90-99 | DEBUG | Dev tooling |

## Registry Table

Columns:
- **ID**: Channel ID
- **Side**: C->S or S->C
- **Validator/Guard**: `PacketValidator`, OP check, or Dist guard
- **Handler**: method or inline handler

### MOB/ITEM

| ID | ChannelId | Payload | Side | Validator/Guard | Handler |
|----|-----------|---------|------|-----------------|---------|
| 1 | `MOB_STATS` | `UpdateMobStatsPayload` | C->S | PacketValidator (OP) | `MobItemNetworkHandler.handleMobData` |
| 2 | `WEAPON_LEGACY` | `UpdateWeaponPayload` | C->S | PacketValidator (OP) | `MobItemNetworkHandler.handleWeaponData` |
| 3 | `EQUIP_MOB` | `EquipMobPayload` | C->S | PacketValidator (OP) | `MobItemNetworkHandler.handleEquipData` |
| 4 | `MODIFY_ITEM` | `ModifyItemPayload` | C->S | PacketValidator (OP) | `MobItemNetworkHandler.handleItemModification` |

### ENDURANCE

| ID | ChannelId | Payload | Side | Validator/Guard | Handler |
|----|-----------|---------|------|-----------------|---------|
| 5 | `START_QUEST` | `StartQuestPayload` | C->S | PacketValidator (OP) | `EnduranceNetworkHandler.handleStartEnduranceQuest` |
| 6 | `QUEST_ACTION` | `QuestActionPayload` | C->S | PacketValidator (rate limit) | `EnduranceNetworkHandler.handleQuestAction` |
| 7 | `QUEST_SYNC` | `QuestSyncPayload` | S->C | Dist guard | `EnduranceNetworkHandler.handleQuestSync` |
| 8 | `SHOP_PURCHASE` | `ShopPurchasePayload` | C->S | PacketValidator (rate limit) | `EnduranceNetworkHandler.handleShopPurchase` |
| 9 | `SHOP_SYNC` | `ShopSyncPayload` | S->C | Dist guard | `EnduranceNetworkHandler.handleShopSync` |
| 10 | `REQUEST_SHOP_SYNC` | `RequestShopSyncPayload` | C->S | PacketValidator (rate limit) | `EnduranceNetworkHandler.handleRequestShopSync` |
| 11 | `MOB_CONFIG_CONFIRM` | `MobConfigConfirmPayload` | S->C | Dist guard | `NetworkHandler` inline |
| 12 | `QUEST_DEATH` | `QuestDeathPayload` | S->C | Dist guard | `EnduranceNetworkHandler.handleQuestDeath` |
| 13 | `PERK_CHOICES` | `PerkChoicesPayload` | S->C | Dist guard | `EnduranceNetworkHandler.handlePerkChoices` |
| 14 | `PERK_SELECTION` | `PerkSelectionPayload` | C->S | PacketValidator (rate limit) | `EnduranceNetworkHandler.handlePerkSelection` |
| 15 | `QUEST_COMPLETION` | `QuestCompletionPayload` | S->C | Dist guard | `EnduranceNetworkHandler.handleQuestCompletion` |
| 16 | `PERSONAL_RECORDS_SYNC` | `PersonalRecordsSyncPayload` | S->C | Dist guard | `EnduranceNetworkHandler.handlePersonalRecordsSync` |
| 17 | `REQUEST_PERSONAL_RECORDS` | `RequestPersonalRecordsPayload` | C->S | PacketValidator (rate limit) | `EnduranceNetworkHandler.handleRequestPersonalRecords` |
| 18 | `BOSS_ALERT` | `BossAlertPayload` | S->C | Dist guard | `NetworkHandler` inline |
| 19 | `BADGE_UNLOCK` | `BadgeUnlockPayload` | S->C | Dist guard | `NetworkHandler` inline |
| 20 | `TOKEN_GAIN` | `TokenGainPayload` | S->C | Dist guard | `NetworkHandler` inline |
| 21 | `RECORD_BANNER` | `RecordBannerPayload` | S->C | Dist guard | `NetworkHandler` inline |
| 22 | `COMBO_DECAY` | `ComboDecayPayload` | S->C | Dist guard | `NetworkHandler` inline |
| 23 | `INSTANCE_LOADING` | `InstanceLoadingPayload` | S->C | Dist guard | `EnduranceNetworkHandler.handleInstanceLoading` |
| 24 | `WAVE_DIRECTIVE_CHOICES` | `WaveDirectiveChoicesPayload` | S->C | Dist guard | `EnduranceNetworkHandler.handleWaveDirectiveChoices` |
| 25 | `WAVE_DIRECTIVE_SELECTION` | `WaveDirectiveSelectionPayload` | C->S | PacketValidator (rate limit) | `EnduranceNetworkHandler.handleWaveDirectiveSelection` |

### PARTY

| ID | ChannelId | Payload | Side | Validator/Guard | Handler |
|----|-----------|---------|------|-----------------|---------|
| 26 | `PARTY_ACTION` | `PartyActionPayload` | C->S | PacketValidator (rate limit) | `PartyNetworkHandler.handlePartyAction` |
| 27 | `PARTY_NOTIFICATION` | `PartyNotificationPayload` | S->C | Dist guard | `PartyNetworkHandler.handlePartyNotification` |
| 28 | `PARTY_SYNC` | `PartySyncPayload` | S->C | Dist guard | `PartyNetworkHandler.handlePartySync` |
| 29 | `QUEST_SEQUENCE` | `QuestSequencePayload` | S->C | Dist guard | `PartyNetworkHandler.handleQuestSequence` |
| 30 | `NAMED_INVITE` | `NamedInvitePayload` | C->S | PacketValidator (rate limit) | `PartyNetworkHandler.handleNamedInvite` |
| 31 | `ARRIVAL_CONFIRM` | `ArrivalConfirmPayload` | C->S | PacketValidator (rate limit) | `PartyNetworkHandler.handleArrivalConfirm` |
| 32 | `CANCEL_SEQUENCE` | `CancelSequencePayload` | C->S | PacketValidator (rate limit) | `PartyNetworkHandler.handleCancelSequence` |
| 33 | `INVITE_RESPONSE` | `InviteResponsePayload` | C->S | PacketValidator (rate limit) | `PartyNetworkHandler.handleInviteResponse` |

### CONFIG / TELEMETRY

| ID | ChannelId | Payload | Side | Validator/Guard | Handler |
|----|-----------|---------|------|-----------------|---------|
| 36 | `UPDATE_ARMOR` | `UpdateArmorPayload` | C->S | PacketValidator (OP) | `MobItemNetworkHandler.handleArmorData` |
| 37 | `RANGED_WEAPON_STATS` | `RangedWeaponStatsPayload` | C->S | PacketValidator (OP) | `MobItemNetworkHandler.handleRangedWeaponData` |
| 38 | `ARMOR_STATS` | `ArmorStatsPayload` | C->S | PacketValidator (OP) | `MobItemNetworkHandler.handleArmorStatsDataV2` |
| 39 | `GLOBAL_CONFIG_SYNC` | `GlobalConfigSyncPayload` | S->C | Dist guard | `NetworkHandler` inline |
| 40 | `RECIPE_SYNC` | `RecipeSyncPayload` | C->S | PacketValidator (OP + rate limit) | `ConfigNetworkHandler.handleRecipeSync` |
| 41 | `RECIPE_CLIENT_SYNC` | `RecipeClientSyncPayload` | S->C | Dist guard | `NetworkHandler` inline |
| 42 | `TELEMETRY_BATCH` | `TelemetryBatchPayload` | C->S | PacketValidator (rate limit) | `ConfigNetworkHandler.handleTelemetryBatch` |
| 43 | `EDITOR_APPLY_CONFIRM` | `EditorApplyConfirmPayload` | S->C | Dist guard | `NetworkHandler` inline |
| 44 | `RESONANCE_NOTIFICATION` | `ResonanceNotificationPayload` | S->C | Dist guard | `NetworkHandler` inline |
| 45 | `CONTRACT_SYNC` | `ContractSyncPayload` | S->C | Dist guard | `NetworkHandler` inline |

### ITEM STATS

| ID | ChannelId | Payload | Side | Validator/Guard | Handler |
|----|-----------|---------|------|-----------------|---------|
| 46 | `USABLE_STATS` | `UsableStatsPayload` | C->S | PacketValidator (OP) | `MobItemNetworkHandler.handleUsableStatsData` |
| 47 | `FOOD_STATS` | `FoodStatsPayload` | C->S | PacketValidator (OP) | `MobItemNetworkHandler.handleFoodStatsData` |
| 48 | `FUEL_STATS` | `FuelStatsPayload` | C->S | PacketValidator (OP) | `MobItemNetworkHandler.handleFuelStatsData` |
| 49 | `WEAPON_STATS_V2` | `WeaponStatsPayload` | C->S | PacketValidator (OP) | `MobItemNetworkHandler.handleWeaponStatsDataV2` |
| 51 | `TENSION_UPDATE` | `TensionUpdatePayload` | S->C | Dist guard | `NetworkHandler` inline |

### SHIELD

| ID | ChannelId | Payload | Side | Validator/Guard | Handler |
|----|-----------|---------|------|-----------------|---------|
| 56 | `SHIELD_STATE` | `ShieldStatePayload` | S->C | Dist guard | `ShieldNetworkHandler.handleShieldState` |
| 57 | `SHIELD_IMPACT` | `ShieldImpactPayload` | S->C | Dist guard | `ShieldNetworkHandler.handleShieldImpact` |
| 58 | `SHIELD_SHATTER` | `ShieldShatterPayload` | S->C | Dist guard | `ShieldNetworkHandler.handleShieldShatter` |

### ABILITY

| ID | ChannelId | Payload | Side | Validator/Guard | Handler |
|----|-----------|---------|------|-----------------|---------|
| 66 | `STAMINA_SYNC` | `StaminaSyncPayload` | S->C | Dist guard | `NetworkHandler` inline |
| 67 | `ABILITY_ACTION` | `AbilityActionPayload` | C->S | PacketValidator + payload validation | `AbilityNetworkHandler.handleAbilityAction` |

### ARENA

| ID | ChannelId | Payload | Side | Validator/Guard | Handler |
|----|-----------|---------|------|-----------------|---------|
| 76 | `BUILD_PROGRESS` | `BuildProgressPayload` | S->C | Bounds clamp + Dist guard | `NetworkHandler` inline |

### CHALLENGES

| ID | ChannelId | Payload | Side | Validator/Guard | Handler |
|----|-----------|---------|------|-----------------|---------|
| 86 | `CHALLENGE_SYNC` | `ChallengeSyncPayload` | S->C | Dist guard | `NetworkHandler` inline |

### DEBUG

| ID | ChannelId | Payload | Side | Validator/Guard | Handler |
|----|-----------|---------|------|-----------------|---------|
| 90 | `DEBUG_TOGGLE` | `DebugTogglePayload` | C->S | PacketValidator (OP) | `DebugNetworkHandler.handleDebugToggle` |
| 91 | `DEBUG_SYNC` | `DebugSyncPayload` | S->C | Dist guard | `DebugNetworkHandler.handleDebugSync` |

## Notes

- `ChannelId.CHALLENGE_COMPLETE` is reserved but not currently registered.
- Channel ID collision detection is enforced at startup via `ChannelId.validateNoCollisions()` and guarded by unit tests.
