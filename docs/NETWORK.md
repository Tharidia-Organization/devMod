# Network

> Ultimo aggiornamento: 2026-01-31

DevMod usa payload NeoForge registrati in `com.devmod.network.NetworkHandler` con mapping in `com.devmod.network.ChannelId`.

## Struttura

- Registry centralizzato con id numerici e direction (client->server / server->client).
- Validazione payload e limiti dimensione con `PayloadValidation`.
- Handler separati per dominio (endurance, party, config, mailbox, ecc.).

## Domini principali (range ChannelId)

- 1-4: mob/item editor
- 5-25: endurance quest
- 26-35: party
- 36-45: config/telemetry
- 46-55: item stats + mechanics
- 56-65: shield/impact
- 66-75: abilities
- 76-85: arena
- 86-89: challenges
- 90-99: debug
- 100-115: mailbox/news/task/ticket
- 120-129: notification center
- 130-139: compat/nutrition/mob pool
- 140-149: nexus system
- 150-159: portal
- 160-169: hologram
- 170-179: clone
- 180-189: npc
- 190-199, 205-209, 225-229: area builder
- 200-204: zone marker
- 210-220: transport
- 230-231: admin instance
- 240-243: nexus hub (slot system)

## Registry completo (ChannelId)

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
