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
    BADGE_UNLOCK(19, Direction.SERVER_TO_CLIENT, "BadgeUnlockPayload"),
    TOKEN_GAIN(20, Direction.SERVER_TO_CLIENT, "TokenGainPayload"),
    RECORD_BANNER(21, Direction.SERVER_TO_CLIENT, "RecordBannerPayload"),
    COMBO_DECAY(22, Direction.SERVER_TO_CLIENT, "ComboDecayPayload"),
    INSTANCE_LOADING(23, Direction.SERVER_TO_CLIENT, "InstanceLoadingPayload"),
    WAVE_DIRECTIVE_CHOICES(24, Direction.SERVER_TO_CLIENT, "WaveDirectiveChoicesPayload"),
    WAVE_DIRECTIVE_SELECTION(25, Direction.CLIENT_TO_SERVER, "WaveDirectiveSelectionPayload"),

    // ============================================================================
    // PARTY CHANNELS (26-35)
    // NOTE: Channel 24 was previously used for PartyAction, moved to 26 to avoid confusion
    // ============================================================================
    PARTY_ACTION(26, Direction.CLIENT_TO_SERVER, "PartyActionPayload"),
    PARTY_NOTIFICATION(27, Direction.SERVER_TO_CLIENT, "PartyNotificationPayload"),
    PARTY_SYNC(28, Direction.SERVER_TO_CLIENT, "PartySyncPayload"),
    QUEST_SEQUENCE(29, Direction.SERVER_TO_CLIENT, "QuestSequencePayload"),
    NAMED_INVITE(30, Direction.CLIENT_TO_SERVER, "NamedInvitePayload"),
    ARRIVAL_CONFIRM(31, Direction.CLIENT_TO_SERVER, "ArrivalConfirmPayload"),
    CANCEL_SEQUENCE(32, Direction.CLIENT_TO_SERVER, "CancelSequencePayload"),
    INVITE_RESPONSE(33, Direction.CLIENT_TO_SERVER, "InviteResponsePayload"),

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
    RESONANCE_NOTIFICATION(44, Direction.SERVER_TO_CLIENT, "ResonanceNotificationPayload"),
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

    // ============================================================================
    // SHIELD CHANNELS (56-65)
    // ============================================================================
    SHIELD_STATE(56, Direction.SERVER_TO_CLIENT, "ShieldStatePayload"),
    SHIELD_IMPACT(57, Direction.SERVER_TO_CLIENT, "ShieldImpactPayload"),
    SHIELD_SHATTER(58, Direction.SERVER_TO_CLIENT, "ShieldShatterPayload"),

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

    // ============================================================================
    // CHALLENGES CHANNELS (86-89)
    // ============================================================================
    CHALLENGE_SYNC(86, Direction.SERVER_TO_CLIENT, "ChallengeSyncPayload"),
    CHALLENGE_COMPLETE(87, Direction.SERVER_TO_CLIENT, "ChallengeCompletePayload"),

    // ============================================================================
    // DEBUG CHANNELS (90-91)
    // ============================================================================
    DEBUG_TOGGLE(90, Direction.CLIENT_TO_SERVER, "DebugTogglePayload"),
    DEBUG_SYNC(91, Direction.SERVER_TO_CLIENT, "DebugSyncPayload"),

    // ============================================================================
    // SEASON PASS CHANNELS (92-99)
    // ============================================================================
    SEASON_TIER_UP(92, Direction.SERVER_TO_CLIENT, "SeasonTierUpPayload"),

    // ============================================================================
    // MAILBOX SYSTEM CHANNELS (100-115)
    // ============================================================================
    MAILBOX_SYNC(100, Direction.SERVER_TO_CLIENT, "MailboxSyncPayload"),
    MAILBOX_SEND(101, Direction.CLIENT_TO_SERVER, "MailboxSendPayload"),
    MAILBOX_READ(102, Direction.CLIENT_TO_SERVER, "MailboxReadPayload"),
    MAILBOX_DELETE(103, Direction.CLIENT_TO_SERVER, "MailboxDeletePayload"),
    MAILBOX_CLAIM(104, Direction.CLIENT_TO_SERVER, "MailboxClaimPayload"),
    MAILBOX_NOTIFY(105, Direction.SERVER_TO_CLIENT, "MailboxNotifyPayload"),
    NEWS_SYNC(106, Direction.SERVER_TO_CLIENT, "NewsSyncPayload"),
    NEWS_READ(107, Direction.CLIENT_TO_SERVER, "NewsReadPayload"),
    TASK_SYNC(108, Direction.SERVER_TO_CLIENT, "TaskSyncPayload"),
    TASK_ACTION(109, Direction.CLIENT_TO_SERVER, "TaskActionPayload"),
    MAILBOX_STATUS(110, Direction.SERVER_TO_CLIENT, "MailboxStatusPayload"),
    MAILBOX_ACCESS(111, Direction.SERVER_TO_CLIENT, "MailboxAccessPayload"),

    // ============================================================================
    // UNIFIED NOTIFICATION CENTER CHANNELS (120-129)
    // ============================================================================
    UNIFIED_NOTIFICATION(120, Direction.SERVER_TO_CLIENT, "UnifiedNotificationPayload"),
    NOTIFICATION_PREFS_SYNC(121, Direction.SERVER_TO_CLIENT, "NotificationPreferencesSyncPayload"),
    NOTIFICATION_PREFS_UPDATE(122, Direction.CLIENT_TO_SERVER, "NotificationPreferencesUpdatePayload");

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
