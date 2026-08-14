package com.devmod.network;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nonnull;

public enum ChannelId {

    // ============================================================================
    // MOB/ITEM CHANNELS (1-4)
    // ============================================================================
    MOB_STATS(1, Direction.CLIENT_TO_SERVER, "UpdateMobStatsPayload"),
    WEAPON_LEGACY(2, Direction.CLIENT_TO_SERVER, "UpdateWeaponPayload"),
    EQUIP_MOB(3, Direction.CLIENT_TO_SERVER, "EquipMobPayload"),
    MODIFY_ITEM(4, Direction.CLIENT_TO_SERVER, "ModifyItemPayload"),

    // ============================================================================
    // ENDURANCE QUEST CHANNELS (5-6, 8-25)
    // ============================================================================
    START_QUEST(5, Direction.CLIENT_TO_SERVER, "StartQuestPayload"),
    QUEST_ACTION(6, Direction.CLIENT_TO_SERVER, "QuestActionPayload"),
    QUEST_SYNC(7, Direction.SERVER_TO_CLIENT, "QuestSyncPayload"),
    SHOP_PURCHASE(8, Direction.CLIENT_TO_SERVER, "ShopPurchasePayload"),
    SHOP_SYNC(9, Direction.SERVER_TO_CLIENT, "ShopSyncPayload"),
    REQUEST_SHOP_SYNC(10, Direction.CLIENT_TO_SERVER, "RequestShopSyncPayload"),
    MOB_CONFIG_CONFIRM(11, Direction.SERVER_TO_CLIENT, "MobConfigConfirmPayload"),
    QUEST_DEATH(12, Direction.SERVER_TO_CLIENT, "QuestDeathPayload"),
    PERK_CHOICES(13, Direction.SERVER_TO_CLIENT, "PerkChoicesPayload"),
    PERK_SELECTION(14, Direction.CLIENT_TO_SERVER, "PerkSelectionPayload"),
    QUEST_COMPLETION(15, Direction.SERVER_TO_CLIENT, "QuestCompletionPayload"),
    PERSONAL_RECORDS_SYNC(16, Direction.SERVER_TO_CLIENT, "PersonalRecordsSyncPayload"),
    REQUEST_PERSONAL_RECORDS(17, Direction.CLIENT_TO_SERVER, "RequestPersonalRecordsPayload"),
    BOSS_ALERT(18, Direction.SERVER_TO_CLIENT, "BossAlertPayload"),
    REQUEST_ARENA_SUGGESTIONS(19, Direction.CLIENT_TO_SERVER, "RequestArenaSuggestionsPayload"),
    ARENA_SUGGESTIONS(20, Direction.SERVER_TO_CLIENT, "ArenaSuggestionsPayload"),
    KIT_SYNC(21, Direction.CLIENT_TO_SERVER, "KitSyncPayload"),
    KIT_SYNC_CONFIRM(22, Direction.SERVER_TO_CLIENT, "KitSyncConfirmPayload"),
    INSTANCE_LOADING(23, Direction.SERVER_TO_CLIENT, "InstanceLoadingPayload"),
    WAVE_DIRECTIVE_CHOICES(24, Direction.SERVER_TO_CLIENT, "WaveDirectiveChoicesPayload"),
    WAVE_DIRECTIVE_SELECTION(25, Direction.CLIENT_TO_SERVER, "WaveDirectiveSelectionPayload"),

    // ============================================================================
    // PARTY CHANNELS (26-35)
    // NOTE: Channel 24 was previously used for PartyAction, moved to 26 to avoid confusion
    // ============================================================================
    PARTY_ACTION(26, Direction.CLIENT_TO_SERVER, "PartyActionPayload"),
    PARTY_SYNC(28, Direction.SERVER_TO_CLIENT, "PartySyncPayload"),
    QUEST_SEQUENCE(29, Direction.SERVER_TO_CLIENT, "QuestSequencePayload"),
    NAMED_INVITE(30, Direction.CLIENT_TO_SERVER, "NamedInvitePayload"),
    ARRIVAL_CONFIRM(31, Direction.CLIENT_TO_SERVER, "ArrivalConfirmPayload"),
    CANCEL_SEQUENCE(32, Direction.CLIENT_TO_SERVER, "CancelSequencePayload"),
    INVITE_RESPONSE(33, Direction.CLIENT_TO_SERVER, "InviteResponsePayload"),
    PARTY_STATS_SYNC(34, Direction.SERVER_TO_CLIENT, "PartyStatsSyncPayload"),

    // ============================================================================
    // CONFIG/TELEMETRY CHANNELS (36-45)
    // ============================================================================
    UPDATE_ARMOR(36, Direction.CLIENT_TO_SERVER, "UpdateArmorPayload"),
    RANGED_WEAPON_STATS(37, Direction.CLIENT_TO_SERVER, "RangedWeaponStatsPayload"),
    ARMOR_STATS(38, Direction.CLIENT_TO_SERVER, "ArmorStatsPayload"),
    GLOBAL_CONFIG_SYNC(39, Direction.SERVER_TO_CLIENT, "GlobalConfigSyncPayload"),
    RECIPE_SYNC(40, Direction.CLIENT_TO_SERVER, "RecipeSyncPayload"),
    RECIPE_CLIENT_SYNC(41, Direction.SERVER_TO_CLIENT, "RecipeClientSyncPayload"),
    TELEMETRY_BATCH(42, Direction.CLIENT_TO_SERVER, "TelemetryBatchPayload"),
    EDITOR_APPLY_CONFIRM(43, Direction.SERVER_TO_CLIENT, "EditorApplyConfirmPayload"),
    ENDURANCE_CONFIG_SYNC(44, Direction.CLIENT_TO_SERVER, "EnduranceConfigSyncPayload"),
    CONTRACT_SYNC(45, Direction.SERVER_TO_CLIENT, "ContractSyncPayload"),

    // ============================================================================
    // ITEM STATS CHANNELS (46-55) - weapons, armor, food, fuel, usables
    // ============================================================================
    USABLE_STATS(46, Direction.CLIENT_TO_SERVER, "UsableStatsPayload"),
    FOOD_STATS(47, Direction.CLIENT_TO_SERVER, "FoodStatsPayload"),
    FUEL_STATS(48, Direction.CLIENT_TO_SERVER, "FuelStatsPayload"),
    WEAPON_STATS_V2(49, Direction.CLIENT_TO_SERVER, "WeaponStatsPayload v2"),
    // ID 50 reserved (WEAPON_STATS_NBT removed - uses same payload as V2)
    TENSION_UPDATE(51, Direction.SERVER_TO_CLIENT, "TensionUpdatePayload"),
    GAME_MECHANICS_SYNC(52, Direction.SERVER_TO_CLIENT, "GameMechanicsSyncPayload"),
    ENDURANCE_MOB_CONFIG_SYNC(53, Direction.CLIENT_TO_SERVER, "EnduranceMobConfigSyncPayload"),
    COMBAT_FLOW_SYNC(54, Direction.SERVER_TO_CLIENT, "CombatFlowSyncPayload"),

    // ============================================================================
    // SHIELD CHANNELS (56-65)
    // ============================================================================
    SHIELD_STATE(56, Direction.SERVER_TO_CLIENT, "ShieldStatePayload"),
    SHIELD_IMPACT(57, Direction.SERVER_TO_CLIENT, "ShieldImpactPayload"),
    SHIELD_SHATTER(58, Direction.SERVER_TO_CLIENT, "ShieldShatterPayload"),
    IMPACT_SYNC(59, Direction.SERVER_TO_CLIENT, "ImpactSyncPayload"),

    // ============================================================================
    // ABILITY CHANNELS (66-75)
    // ============================================================================
    STAMINA_SYNC(66, Direction.SERVER_TO_CLIENT, "StaminaSyncPayload"),
    ABILITY_ACTION(67, Direction.CLIENT_TO_SERVER, "AbilityActionPayload"),
    LVC_SYNC(68, Direction.SERVER_TO_CLIENT, "LVCSyncPayload"),

    // ============================================================================
    // ARENA CHANNELS (76-85)
    // ============================================================================
    BUILD_PROGRESS(76, Direction.SERVER_TO_CLIENT, "BuildProgressPayload"),
    ENVIRONMENT_SYNC(77, Direction.SERVER_TO_CLIENT, "EnvironmentSyncPayload"),
    ZONE_DEBUG(78, Direction.SERVER_TO_CLIENT, "ZoneDebugPayload"),

    // ============================================================================
    // CHALLENGES CHANNELS (86-89)
    // ============================================================================
    CHALLENGE_SYNC(86, Direction.SERVER_TO_CLIENT, "ChallengeSyncPayload"),
    // ID 87 reserved for future ChallengeCompletePayload

    // ============================================================================
    // DEBUG CHANNELS (90-99)
    // ============================================================================
    DEBUG_TOGGLE(90, Direction.CLIENT_TO_SERVER, "DebugTogglePayload"),
    DEBUG_SYNC(91, Direction.SERVER_TO_CLIENT, "DebugSyncPayload"),
    ENTITY_PATHING(92, Direction.SERVER_TO_CLIENT, "EntityPathingPayload"),
    ENTITY_SCAN_DATA(93, Direction.SERVER_TO_CLIENT, "EntityScanDataPayload"),
    ENTITY_SCANNER_OPEN(94, Direction.SERVER_TO_CLIENT, "EntityScannerOpenPayload"),
    DEBUG_STRUCTURES(95, Direction.SERVER_TO_CLIENT, "StructuresPayload"),
    DEBUG_POI(96, Direction.SERVER_TO_CLIENT, "POIPayload"),
    DEBUG_RAIDS(97, Direction.SERVER_TO_CLIENT, "RaidsPayload"),
    DEBUG_BRAINS(98, Direction.SERVER_TO_CLIENT, "BrainsPayload"),
    DEBUG_BEES(99, Direction.SERVER_TO_CLIENT, "BeesPayload"),
    // 91-99 are taken and the block ends at 99; ids are scoped per direction, so 90 is still
    // free S→C even though DEBUG_TOGGLE holds it C→S.
    DEBUG_GOALS(90, Direction.SERVER_TO_CLIENT, "EntityGoalsPayload"),

    // ============================================================================
    // MAILBOX SYSTEM CHANNELS (100-115)
    // ============================================================================
    MAILBOX_SYNC(100, Direction.SERVER_TO_CLIENT, "MailboxSyncPayload"),
    MAILBOX_SEND(101, Direction.CLIENT_TO_SERVER, "MailboxSendPayload"),
    MAILBOX_READ(102, Direction.CLIENT_TO_SERVER, "MailboxReadPayload"),
    // ID 103-104 reserved for MailboxDeletePayload/MailboxClaimPayload
    MAILBOX_NOTIFY(105, Direction.SERVER_TO_CLIENT, "MailboxNotifyPayload"),
    NEWS_SYNC(106, Direction.SERVER_TO_CLIENT, "NewsSyncPayload"),
    NEWS_READ(107, Direction.CLIENT_TO_SERVER, "NewsReadPayload"),
    TASK_SYNC(108, Direction.SERVER_TO_CLIENT, "TaskSyncPayload"),
    TASK_ACTION(109, Direction.CLIENT_TO_SERVER, "TaskActionPayload"),
    MAILBOX_STATUS(110, Direction.SERVER_TO_CLIENT, "MailboxStatusPayload"),
    MAILBOX_ACCESS(111, Direction.SERVER_TO_CLIENT, "MailboxAccessPayload"),
    TICKET_SYNC(112, Direction.SERVER_TO_CLIENT, "TicketSyncPayload"),
    TICKET_CREATE(113, Direction.CLIENT_TO_SERVER, "TicketCreatePayload"),
    TICKET_SYNC_REQUEST(114, Direction.CLIENT_TO_SERVER, "TicketSyncRequestPayload"),
    TICKET_ACTION(115, Direction.CLIENT_TO_SERVER, "TicketActionPayload"),

    // ============================================================================
    // UNIFIED NOTIFICATION CENTER CHANNELS (120-129)
    // ============================================================================
    UNIFIED_NOTIFICATION(120, Direction.SERVER_TO_CLIENT, "UnifiedNotificationPayload"),
    NOTIFICATION_PREFS_SYNC(121, Direction.SERVER_TO_CLIENT, "NotificationPreferencesSyncPayload"),
    NOTIFICATION_PREFS_UPDATE(122, Direction.CLIENT_TO_SERVER, "NotificationPreferencesUpdatePayload"),
    SEASON_PASS_SYNC(123, Direction.SERVER_TO_CLIENT, "SeasonPassPayload"),
    REQUEST_SEASON_PASS(124, Direction.CLIENT_TO_SERVER, "RequestSeasonPassPayload"),
    CLAIM_SEASON_REWARD(125, Direction.CLIENT_TO_SERVER, "ClaimSeasonRewardPayload"),

    // ============================================================================
    // COMPAT MODULE CHANNELS (130-139)
    // Reserved for future compat module network channels
    // ============================================================================
    NUTRITION_SYNC(130, Direction.SERVER_TO_CLIENT, "NutritionSyncPayload"),
    REQUEST_MOB_POOL_CONFIG(131, Direction.CLIENT_TO_SERVER, "RequestMobPoolConfigPayload"),
    MOB_POOL_CONFIG_SYNC(132, Direction.SERVER_TO_CLIENT, "MobPoolConfigSyncPayload"),

    // ============================================================================
    // NEXUS SYSTEM CHANNELS (140-149)
    // ============================================================================
    NEXUS_DIALOG(140, Direction.SERVER_TO_CLIENT, "NexusDialogPayload"),
    NEXUS_DIALOG_ACTION(141, Direction.CLIENT_TO_SERVER, "NexusDialogActionPayload"),
    NEXUS_UI(142, Direction.SERVER_TO_CLIENT, "NexusUiPayload"),
    NEXUS_LOG_REQUEST(143, Direction.CLIENT_TO_SERVER, "NexusLogRequestPayload"),
    NEXUS_LOG_SNAPSHOT(144, Direction.SERVER_TO_CLIENT, "NexusLogSnapshotPayload"),

    // ============================================================================
    // PORTAL CHANNELS (150-159)
    // ============================================================================
    PORTAL_STATE(150, Direction.SERVER_TO_CLIENT, "PortalStatePayload"),
    PORTAL_PREVIEW_REQUEST(151, Direction.CLIENT_TO_SERVER, "PortalPreviewRequestPayload"),
    PORTAL_PREVIEW(152, Direction.SERVER_TO_CLIENT, "PortalPreviewPayload"),

    // ============================================================================
    // HOLOGRAM CHANNELS (160-169)
    // ============================================================================
    HOLOGRAM_CONFIG(160, Direction.CLIENT_TO_SERVER, "HologramConfigPayload"),
    HOLOGRAM_OPEN_SCREEN(161, Direction.SERVER_TO_CLIENT, "HologramOpenScreenPayload"),
    HOLOGRAM_EDITOR_OPEN(162, Direction.SERVER_TO_CLIENT, "OpenHologramEditorPayload"),
    HOLOGRAM_SAVE(163, Direction.CLIENT_TO_SERVER, "SaveHologramPayload"),
    HOLOGRAM_DELETE(164, Direction.CLIENT_TO_SERVER, "DeleteHologramPayload"),
    HOLOGRAM_SYNC(165, Direction.SERVER_TO_CLIENT, "HologramSyncPayload"),
    HOLOGRAM_PRESET(166, Direction.CLIENT_TO_SERVER, "HologramPresetPayload"),

    // ============================================================================
    // CLONE MODULE CHANNELS (170-179)
    // ============================================================================
    TELEPAD_CONFIG(170, Direction.CLIENT_TO_SERVER, "TelepadConfigPayload"),
    TELEPAD_OPEN_SCREEN(171, Direction.SERVER_TO_CLIENT, "TelepadOpenScreenPayload"),
    MANNEQUIN_ROTATION(172, Direction.CLIENT_TO_SERVER, "MannequinRotationPayload"),
    MANNEQUIN_SKIN(173, Direction.CLIENT_TO_SERVER, "MannequinSkinPayload"),

    // ============================================================================
    // NPC SYSTEM CHANNELS (180-189)
    // ============================================================================
    NPC_CONFIG_OPEN(180, Direction.SERVER_TO_CLIENT, "OpenNpcConfigPayload"),
    NPC_CONFIG_SAVE(181, Direction.CLIENT_TO_SERVER, "SaveNpcConfigPayload"),
    NPC_DIALOG_EDITOR_OPEN(182, Direction.SERVER_TO_CLIENT, "OpenDialogEditorPayload"),
    NPC_DIALOG_SAVE(183, Direction.CLIENT_TO_SERVER, "SaveDialogPayload"),
    NPC_DIALOG_OPEN(184, Direction.SERVER_TO_CLIENT, "NpcDialogPayload"),
    NPC_DIALOG_ACTION(185, Direction.CLIENT_TO_SERVER, "NpcDialogActionPayload"),

    // ============================================================================
    // AREA BUILDER CHANNELS (190-199, 205-209, 225-229)
    // ============================================================================
    AREA_BUILDER_OPEN(190, Direction.SERVER_TO_CLIENT, "OpenAreaBuilderPayload"),
    AREA_EDITOR_CENTRAL_OPEN(191, Direction.SERVER_TO_CLIENT, "OpenEditorCentralPayload"),
    AREA_SAVE(192, Direction.CLIENT_TO_SERVER, "SaveAreaPayload"),
    AREA_BUILD(193, Direction.CLIENT_TO_SERVER, "BuildAreaPayload"),
    AREA_PREVIEW(194, Direction.SERVER_TO_CLIENT, "AreaPreviewPayload"),
    AREA_BUILDER_REQUEST(195, Direction.CLIENT_TO_SERVER, "RequestOpenAreaBuilderPayload"),
    AREA_ZONE_LIST_REQUEST(196, Direction.CLIENT_TO_SERVER, "RequestZoneListPayload"),
    AREA_ZONE_LIST(197, Direction.SERVER_TO_CLIENT, "ZoneListPayload"),
    AREA_TEMPLATE_REQUEST(198, Direction.CLIENT_TO_SERVER, "RequestTemplateListPayload"),
    AREA_TEMPLATE_LIST(199, Direction.SERVER_TO_CLIENT, "TemplateListPayload"),
    AREA_TEMPLATE_LOAD(205, Direction.CLIENT_TO_SERVER, "LoadTemplatePayload"),
    AREA_TEMPLATE_DATA(206, Direction.SERVER_TO_CLIENT, "TemplateDataPayload"),
    AREA_TEMPLATE_SAVE(207, Direction.CLIENT_TO_SERVER, "SaveAreaTemplatePayload"),
    AREA_CLONE(208, Direction.CLIENT_TO_SERVER, "CloneAreaPayload"),
    AREA_TEMPLATE_DELETE(209, Direction.CLIENT_TO_SERVER, "DeleteTemplatePayload"),
    AREA_DELETE(225, Direction.CLIENT_TO_SERVER, "DeleteAreaPayload"),
    AREA_PROMOTE_MAIN_HUB(226, Direction.CLIENT_TO_SERVER, "PromoteMainHubPayload"),
    AREA_SAVE_RESULT(227, Direction.SERVER_TO_CLIENT, "SaveAreaResultPayload"),
    AREA_BUILDER_CONTROL(228, Direction.CLIENT_TO_SERVER, "AreaBuilderControlPayloads"),
    AREA_BUILDER_FEEDBACK(229, Direction.SERVER_TO_CLIENT, "AreaBuilderFeedbackPayloads"),

    // ============================================================================
    // ZONE MARKER CHANNELS (200-204)
    // ============================================================================
    ZONE_EDITOR_OPEN(200, Direction.SERVER_TO_CLIENT, "OpenZoneEditorPayload"),
    ZONE_SAVE(201, Direction.CLIENT_TO_SERVER, "SaveZonePayload"),
    ZONE_DELETE(202, Direction.CLIENT_TO_SERVER, "DeleteZonePayload"),
    ZONE_SYNC(203, Direction.SERVER_TO_CLIENT, "ZoneSyncPayload"),
    ZONE_ENTER(204, Direction.SERVER_TO_CLIENT, "ZoneEnterPayload"),

    // ============================================================================
    // UNIFIED TRANSPORT CHANNELS (210-220)
    // ============================================================================
    TRANSPORT_CONFIG_OPEN(210, Direction.SERVER_TO_CLIENT, "TransportConfigOpenPayload"),
    TRANSPORT_CONFIG_SAVE(211, Direction.CLIENT_TO_SERVER, "TransportConfigSavePayload"),
    TRANSPORT_STATE(212, Direction.SERVER_TO_CLIENT, "TransportStatePayload"),
    TRANSPORT_CHARGE_UPDATE(213, Direction.SERVER_TO_CLIENT, "TransportChargeUpdatePayload"),
    TRANSPORT_WAYPOINT_SELECT(214, Direction.CLIENT_TO_SERVER, "TransportWaypointSelectPayload"),
    TRANSPORT_NETWORK_LIST(215, Direction.SERVER_TO_CLIENT, "TransportNetworkListPayload"),
    TRANSPORT_COUNTDOWN(216, Direction.SERVER_TO_CLIENT, "TransportCountdownPayload"),
    TRANSPORT_PARTY_STATUS(217, Direction.SERVER_TO_CLIENT, "TransportPartyStatusPayload"),
    TRANSPORT_ARRIVAL_CONFIRM(218, Direction.CLIENT_TO_SERVER, "TransportArrivalConfirmPayload"),
    TRANSPORT_CANCEL_PARTY(219, Direction.CLIENT_TO_SERVER, "TransportCancelPartyPayload"),
    TRANSPORT_DELETE_WAYPOINT(220, Direction.CLIENT_TO_SERVER, "TransportDeleteWaypointPayload"),

    // ============================================================================
    // ADMIN INSTANCE CHANNELS (230-239)
    // ============================================================================
    ADMIN_INSTANCE_SYNC(230, Direction.SERVER_TO_CLIENT, "AdminInstanceSyncPayload"),
    ADMIN_INSTANCE_ACTION(231, Direction.CLIENT_TO_SERVER, "AdminInstanceActionPayload"),

    // ============================================================================
    // NEXUS HUB CHANNELS (240-249)
    // ============================================================================
    NEXUS_SLOT_LIST_REQUEST(240, Direction.CLIENT_TO_SERVER, "RequestSlotListPayload"),
    NEXUS_SLOT_LIST(241, Direction.SERVER_TO_CLIENT, "SlotListPayload"),
    NEXUS_HUB_STATUS(242, Direction.SERVER_TO_CLIENT, "HubStatusPayload"),
    NEXUS_BUILD_PROGRESS(243, Direction.SERVER_TO_CLIENT, "NexusBuildProgressPayload"),

    // ============================================================================
    // TESTER MODALITY CHANNELS (250-251)
    // ============================================================================
    TESTER_MODALITY_SYNC(250, Direction.SERVER_TO_CLIENT, "TesterModalitySyncPayload"),
    TESTER_MODALITY_ACK(251, Direction.CLIENT_TO_SERVER, "TesterModalityAckPayload"),

    // ============================================================================
    // DEBUG CHANNELS, OVERFLOW (252-259)
    // The 90-99 block is full in both directions, so new debug channels live here.
    // ============================================================================
    DEBUG_BLOCK_UPDATES(252, Direction.SERVER_TO_CLIENT, "BlockUpdatesPayload"),
    ;

    // ============================================================================
    // ENUM INFRASTRUCTURE
    // ============================================================================

    private final int id;
    private final Direction direction;
    private final String payloadName;

    ChannelId(int id, Direction direction, String payloadName) {
        this.id = id;
        this.direction = direction;
        this.payloadName = payloadName;
    }

    /**
     * Returns the numeric channel ID as a string for NeoForge registration.
     */
    @Nonnull
    public String asString() {
        return Objects.requireNonNull(String.valueOf(id));
    }

    /**
     * Returns the numeric channel ID.
     */
    public int getId() {
        return id;
    }

    /**
     * Returns the packet direction.
     */
    public Direction getDirection() {
        return direction;
    }

    /**
     * Returns the payload class name for documentation.
     */
    public String getPayloadName() {
        return payloadName;
    }

    /**
     * Packet direction enum.
     */
    public enum Direction {
        CLIENT_TO_SERVER("C→S"),
        SERVER_TO_CLIENT("S→C");

        private final String symbol;

        Direction(String symbol) {
            this.symbol = symbol;
        }

        public String getSymbol() {
            return symbol;
        }
    }

    // ============================================================================
    // COLLISION DETECTION (called at startup)
    // ============================================================================

    /**
     * Validates that there are no channel ID collisions within the same direction.
     * Call this at mod initialization to catch issues early.
     *
     * @throws IllegalStateException if a collision is detected
     */
    public static void validateNoCollisions() {
        Set<String> clientToServer = new HashSet<>();
        Set<String> serverToClient = new HashSet<>();

        for (ChannelId channel : values()) {
            String key = String.valueOf(channel.id);
            Set<String> set = channel.direction == Direction.CLIENT_TO_SERVER
                    ? clientToServer : serverToClient;

            if (!set.add(key)) {
                throw new IllegalStateException(
                    String.format("CHANNEL ID COLLISION: Channel %d (%s) is already registered in direction %s. " +
                                  "Payload: %s",
                        channel.id, channel.name(), channel.direction.getSymbol(), channel.payloadName));
            }
        }
    }

    /**
     * Prints the full channel registry for debugging.
     */
    public static String dumpRegistry() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== DevMod Channel Registry ===\n");

        for (ChannelId channel : values()) {
            sb.append(String.format("  [%3d] %-35s %s %s%n",
                channel.id, channel.name(), channel.direction.getSymbol(), channel.payloadName));
        }

        return sb.toString();
    }
}
