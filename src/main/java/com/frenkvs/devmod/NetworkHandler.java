package com.frenkvs.devmod;

import static com.frenkvs.devmod.DevMod.MODID;
import com.frenkvs.devmod.util.I18n;
import com.frenkvs.devmod.endurance.ClientQuestCache;
import com.frenkvs.devmod.endurance.EnduranceQuestManager;
import com.frenkvs.devmod.endurance.QuestActionPayload;
import com.frenkvs.devmod.endurance.QuestSyncPayload;
import com.frenkvs.devmod.endurance.RewardSystem;
import com.frenkvs.devmod.endurance.ShopPurchasePayload;
import com.frenkvs.devmod.endurance.ShopSyncPayload;
import com.frenkvs.devmod.endurance.ClientShopCache;
import com.frenkvs.devmod.endurance.RequestShopSyncPayload;
import com.frenkvs.devmod.endurance.StartQuestPayload;
import com.frenkvs.devmod.endurance.QuestDeathPayload;
import com.frenkvs.devmod.endurance.QuestDeathScreen;
import com.frenkvs.devmod.endurance.PerkChoicesPayload;
import com.frenkvs.devmod.endurance.PerkSelectionPayload;
import com.frenkvs.devmod.endurance.PerkSelectionScreen;
import com.frenkvs.devmod.endurance.PerkSystem;
import com.frenkvs.devmod.endurance.QuestCompletionPayload;
import com.frenkvs.devmod.endurance.QuestCompletionScreen;
import com.frenkvs.devmod.endurance.ComboSystem;
import com.frenkvs.devmod.endurance.PersonalRecordsSyncPayload;
import com.frenkvs.devmod.endurance.RequestPersonalRecordsPayload;
import com.frenkvs.devmod.endurance.ClientPersonalRecordsCache;
import com.frenkvs.devmod.endurance.BossAlertPayload;
import com.frenkvs.devmod.endurance.BadgeUnlockPayload;
import com.frenkvs.devmod.endurance.TokenGainPayload;
import com.frenkvs.devmod.endurance.RecordBannerPayload;
import com.frenkvs.devmod.endurance.ComboDecayPayload;
import com.frenkvs.devmod.endurance.InstanceLoadingPayload;
import com.frenkvs.devmod.abilities.AbilityActionPayload;
import com.frenkvs.devmod.abilities.ClientStaminaCache;
import com.frenkvs.devmod.abilities.DashAbilitySystem;
import com.frenkvs.devmod.abilities.DodgeAbilitySystem;
import com.frenkvs.devmod.abilities.StaminaSyncPayload;
import com.frenkvs.devmod.telemetry.duckdb.packets.TelemetryBatchPayload;
import com.frenkvs.devmod.telemetry.duckdb.packets.TelemetryPacketHandler;
import com.frenkvs.devmod.party.ArrivalConfirmPayload;
import com.frenkvs.devmod.party.CancelSequencePayload;
import com.frenkvs.devmod.party.ClientPartyCache;
import com.frenkvs.devmod.party.InvitePopupScreen;
import com.frenkvs.devmod.party.InviteResponsePayload;
import com.frenkvs.devmod.party.NamedInvitePayload;
import com.frenkvs.devmod.party.PartyActionPayload;
import com.frenkvs.devmod.party.PartyData;
import com.frenkvs.devmod.party.PartyManager;
import com.frenkvs.devmod.party.PartyNotificationPayload;
import com.frenkvs.devmod.party.PartySyncPayload;
import com.frenkvs.devmod.party.QuestSequencePayload;
import com.frenkvs.devmod.party.QuestStartSequence;
import com.frenkvs.devmod.hud.QuestSequenceOverlay;
import com.frenkvs.devmod.hud.EnduranceQuestOverlay;
import com.frenkvs.devmod.hud.InstanceLoadingOverlay;
import com.frenkvs.devmod.hud.BadgePopupOverlay;
import com.frenkvs.devmod.hud.TokenGainOverlay;
import com.frenkvs.devmod.hud.RecordBannerOverlay;
import com.frenkvs.devmod.hud.ComboDecayOverlay;
import com.frenkvs.devmod.network.ClientConfigFeedback;
import com.frenkvs.devmod.network.EditorApplyConfirmPayload;
import com.frenkvs.devmod.network.MobConfigConfirmPayload;
import com.frenkvs.devmod.network.PacketSecurityService;
import com.frenkvs.devmod.network.PacketSecurityService.ValidationResult;
import com.frenkvs.devmod.network.RangedWeaponStatsPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@EventBusSubscriber(modid = MODID)
public class NetworkHandler {
	private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        // Channel 1: Monster Statistics
        event.registrar("1").playToServer(
                nn(UpdateMobStatsPayload.TYPE),
                nn(UpdateMobStatsPayload.STREAM_CODEC),
                NetworkHandler::handleMobData
        );
        // Channel 2: Weapon Statistics (legacy; kept for compatibility)
        event.registrar("2").playToServer(
                nn(UpdateWeaponPayload.TYPE),
                nn(UpdateWeaponPayload.STREAM_CODEC),
                NetworkHandler::handleWeaponData
        );
        // Channel 7: Weapon Stats Payload (NBT-based, preferred)
        event.registrar("7").playToServer(
                nn(com.frenkvs.devmod.network.WeaponStatsPayload.TYPE),
                nn(com.frenkvs.devmod.network.WeaponStatsPayload.STREAM_CODEC),
                NetworkHandler::handleWeaponStatsData
        );
        // Channel 17: Weapon Stats Payload v2 (typed)
        event.registrar("17").playToServer(
                nn(com.frenkvs.devmod.network.WeaponStatsPayloadV2.TYPE),
                nn(com.frenkvs.devmod.network.WeaponStatsPayloadV2.STREAM_CODEC),
                NetworkHandler::handleWeaponStatsDataV2
        );
        // Channel 3: Monster Equipment
        event.registrar("3").playToServer(
                nn(EquipMobPayload.TYPE),
                nn(EquipMobPayload.STREAM_CODEC),
                NetworkHandler::handleEquipData
        );
        // Channel 4: Complete Item Modification (durability, enchantments, attributes)
        event.registrar("4").playToServer(
                nn(ModifyItemPayload.TYPE),
                nn(ModifyItemPayload.STREAM_CODEC),
                NetworkHandler::handleItemModification
        );
        // Channel 5: Endurance Quest - Start
        event.registrar("5").playToServer(
                nn(StartQuestPayload.TYPE),
                nn(StartQuestPayload.STREAM_CODEC),
                NetworkHandler::handleStartEnduranceQuest
        );
        // Channel 6: Endurance Quest - Actions (respawn, checkpoint, abandon)
        event.registrar("6").playToServer(
                nn(QuestActionPayload.TYPE),
                nn(QuestActionPayload.STREAM_CODEC),
                NetworkHandler::handleQuestAction
        );
        // Channel 7: Endurance Quest - Sync (server to client)
        event.registrar("7").playToClient(
                nn(QuestSyncPayload.TYPE),
                nn(QuestSyncPayload.STREAM_CODEC),
                NetworkHandler::handleQuestSync
        );
        // Channel 8: Endurance Quest - Shop Purchase
        event.registrar("8").playToServer(
                nn(ShopPurchasePayload.TYPE),
                nn(ShopPurchasePayload.STREAM_CODEC),
                NetworkHandler::handleShopPurchase
        );
        // Channel 9: Endurance Quest - Shop Sync (server to client)
        event.registrar("9").playToClient(
                nn(ShopSyncPayload.TYPE),
                nn(ShopSyncPayload.STREAM_CODEC),
                NetworkHandler::handleShopSync
        );
        // Channel 10: Request Shop Sync (client to server)
        event.registrar("10").playToServer(
                nn(RequestShopSyncPayload.TYPE),
                nn(RequestShopSyncPayload.STREAM_CODEC),
                NetworkHandler::handleRequestShopSync
        );
        // Channel 11: Mob Config Confirmation (server to client)
        event.registrar("11").playToClient(
                nn(MobConfigConfirmPayload.TYPE),
                nn(MobConfigConfirmPayload.STREAM_CODEC),
                NetworkHandler::handleMobConfigConfirm
        );
        // Channel 12: Quest Death Screen (server to client)
        event.registrar("12").playToClient(
                nn(QuestDeathPayload.TYPE),
                nn(QuestDeathPayload.STREAM_CODEC),
                NetworkHandler::handleQuestDeath
        );
        // Channel 13: Perk Choices (server to client)
        event.registrar("13").playToClient(
                nn(PerkChoicesPayload.TYPE),
                nn(PerkChoicesPayload.STREAM_CODEC),
                NetworkHandler::handlePerkChoices
        );
        // Channel 14: Perk Selection (client to server)
        event.registrar("14").playToServer(
                nn(PerkSelectionPayload.TYPE),
                nn(PerkSelectionPayload.STREAM_CODEC),
                NetworkHandler::handlePerkSelection
        );
        // Channel 15: Quest Completion (server to client)
        event.registrar("15").playToClient(
                nn(QuestCompletionPayload.TYPE),
                nn(QuestCompletionPayload.STREAM_CODEC),
                NetworkHandler::handleQuestCompletion
        );
        // Channel 16: Personal Records Sync (server to client)
        event.registrar("16").playToClient(
                nn(PersonalRecordsSyncPayload.TYPE),
                nn(PersonalRecordsSyncPayload.STREAM_CODEC),
                NetworkHandler::handlePersonalRecordsSync
        );
        // Channel 17: Request Personal Records (client to server)
        event.registrar("17").playToServer(
                nn(RequestPersonalRecordsPayload.TYPE),
                nn(RequestPersonalRecordsPayload.STREAM_CODEC),
                NetworkHandler::handleRequestPersonalRecords
        );
        // Channel 18: Boss Alert (server to client)
        event.registrar("18").playToClient(
                nn(BossAlertPayload.TYPE),
                nn(BossAlertPayload.STREAM_CODEC),
                (payload, context) -> context.enqueueWork(() ->
                    EnduranceQuestOverlay.onBossAlert(payload.alertDurationMs(), payload.bossType()))
        );
        // Channel 19: Badge Unlock (server to client)
        event.registrar("19").playToClient(
                nn(BadgeUnlockPayload.TYPE),
                nn(BadgeUnlockPayload.STREAM_CODEC),
                (payload, context) -> context.enqueueWork(() ->
                    BadgePopupOverlay.showBadge(payload.badgeName(), payload.rarity()))
        );
        // Channel 20: Token Gain Animation (server to client)
        event.registrar("20").playToClient(
                nn(TokenGainPayload.TYPE),
                nn(TokenGainPayload.STREAM_CODEC),
                (payload, context) -> context.enqueueWork(() ->
                    TokenGainOverlay.show(payload.amount()))
        );
        // Channel 21: Record Banner (server to client)
        event.registrar("21").playToClient(
                nn(RecordBannerPayload.TYPE),
                nn(RecordBannerPayload.STREAM_CODEC),
                (payload, context) -> context.enqueueWork(() ->
                    RecordBannerOverlay.showRecord(payload.recordType(), payload.recordValue()))
        );
        // Channel 22: Combo Decay Feedback (server to client)
        event.registrar("22").playToClient(
                nn(ComboDecayPayload.TYPE),
                nn(ComboDecayPayload.STREAM_CODEC),
                (payload, context) -> context.enqueueWork(() ->
                    ComboDecayOverlay.show(payload.lostCombo(), payload.previousRankOrdinal(), payload.newRankOrdinal()))
        );
        // Channel 23: Instance Loading Overlay (server to client)
        event.registrar("23").playToClient(
                nn(InstanceLoadingPayload.TYPE),
                nn(InstanceLoadingPayload.STREAM_CODEC),
                NetworkHandler::handleInstanceLoading
        );

        // === QUEST SEQUENCE CHANNELS (28-30) ===
        // Channel 28: Quest Sequence Status (server -> client)
        event.registrar("28").playToClient(
                nn(QuestSequencePayload.TYPE),
                nn(QuestSequencePayload.STREAM_CODEC),
                NetworkHandler::handleQuestSequence
        );
        // Channel 29: Arrival Confirm (client -> server)
        event.registrar("29").playToServer(
                nn(ArrivalConfirmPayload.TYPE),
                nn(ArrivalConfirmPayload.STREAM_CODEC),
                NetworkHandler::handleArrivalConfirm
        );
        // Channel 30: Cancel Sequence (client -> server)
        event.registrar("30").playToServer(
                nn(CancelSequencePayload.TYPE),
                nn(CancelSequencePayload.STREAM_CODEC),
                NetworkHandler::handleCancelSequence
        );

        // === PARTY SYSTEM CHANNELS (24-27) ===
        // Channel 24: Party Action (unified client to server)
        event.registrar("24").playToServer(
                nn(PartyActionPayload.TYPE),
                nn(PartyActionPayload.STREAM_CODEC),
                NetworkHandler::handlePartyAction
        );
        // Channel 25: Invite Response (client to server) - accept/decline party invite
        event.registrar("25").playToServer(
                nn(InviteResponsePayload.TYPE),
                nn(InviteResponsePayload.STREAM_CODEC),
                NetworkHandler::handleInviteResponse
        );
        // Channel 26: Party Notification (server to client)
        event.registrar("26").playToClient(
                nn(PartyNotificationPayload.TYPE),
                nn(PartyNotificationPayload.STREAM_CODEC),
                NetworkHandler::handlePartyNotification
        );
        // Channel 27: Party Sync (server to client)
        event.registrar("27").playToClient(
                nn(PartySyncPayload.TYPE),
                nn(PartySyncPayload.STREAM_CODEC),
                NetworkHandler::handlePartySync
        );
        // Channel 28: Named Invite (client to server) - invite player by name
        event.registrar("28").playToServer(
                nn(NamedInvitePayload.TYPE),
                nn(NamedInvitePayload.STREAM_CODEC),
                NetworkHandler::handleNamedInvite
        );

        // Channel 31: Stamina Sync (server to client)
        event.registrar("31").playToClient(
                nn(StaminaSyncPayload.TYPE),
                nn(StaminaSyncPayload.STREAM_CODEC),
                (payload, context) -> context.enqueueWork(() ->
                    ClientStaminaCache.update(payload.currentStamina(), payload.maxStamina()))
        );

        // Channel 32: Ability Action (client to server) - dash, dodge
        event.registrar("32").playToServer(
                nn(AbilityActionPayload.TYPE),
                nn(AbilityActionPayload.STREAM_CODEC),
                NetworkHandler::handleAbilityAction
        );

        // Channel 33: Telemetry Batch (client to server) - multiplayer telemetry sync
        event.registrar("33").playToServer(
                nn(TelemetryBatchPayload.TYPE),
                nn(TelemetryBatchPayload.STREAM_CODEC),
                NetworkHandler::handleTelemetryBatch
        );

        // Channel 34: Armor Statistics
        event.registrar("34").playToServer(
                nn(UpdateArmorPayload.TYPE),
                nn(UpdateArmorPayload.STREAM_CODEC),
                NetworkHandler::handleArmorData
        );
        // Channel 35: Editor apply confirmation (server to client)
        event.registrar("35").playToClient(
                nn(EditorApplyConfirmPayload.TYPE),
                nn(EditorApplyConfirmPayload.STREAM_CODEC),
                NetworkHandler::handleEditorApplyConfirm
        );
        // Channel 36: Ranged weapon stats (client to server)
        event.registrar("36").playToServer(
                nn(RangedWeaponStatsPayload.TYPE),
                nn(RangedWeaponStatsPayload.STREAM_CODEC),
                NetworkHandler::handleRangedWeaponData
        );
        // Channel 37: Armor Stats Payload v2 (component + typed)
        event.registrar("37").playToServer(
                nn(com.frenkvs.devmod.network.ArmorStatsPayloadV2.TYPE),
                nn(com.frenkvs.devmod.network.ArmorStatsPayloadV2.STREAM_CODEC),
                NetworkHandler::handleArmorStatsDataV2
        );
        // Channel 38: Global Config Sync (server to client)
        event.registrar("38").playToClient(
                nn(com.frenkvs.devmod.network.GlobalConfigSyncPayload.TYPE),
                nn(com.frenkvs.devmod.network.GlobalConfigSyncPayload.STREAM_CODEC),
                NetworkHandler::handleGlobalConfigSync
        );
    }

    // =================================================================================
    // 1. MOB MODIFICATION LOGIC (Health, Damage, Reach, Global/Specific)
    // =================================================================================
    private static void handleMobData(UpdateMobStatsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                // SECURITY: Validate packet and check permissions
                PacketSecurityService security = PacketSecurityService.INSTANCE;
                ValidationResult validation = security.validatePacket(player, "mob_stats", true);
                if (!validation.isSuccess()) {
                    player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", validation.getErrorMessage()));
                    return;
                }

                // SECURITY: Validate entity ID
                if (!security.validateEntityId(payload.entityId())) {
                    player.sendSystemMessage(I18n.translate("devmod.network.invalid_entity"));
                    return;
                }

                // SECURITY: Validate and clamp attribute values
                double followRange = security.validateFollowRange(payload.followRange());
                double damage = security.validateDamage(payload.damage());
                double maxHealth = security.validateHealth(payload.maxHealth());
                double armor = security.validateArmor(payload.armor());
                double attackRange = security.validateFollowRange(payload.attackRange());
                double speed = Math.max(0, Math.min(payload.speed(), 2.0)); // Clamp speed 0-2
                double knockbackResist = Math.max(0, Math.min(payload.knockbackResist(), 1.0)); // Clamp 0-1

                ServerLevel level = player.serverLevel();
                Entity targetEntity = level.getEntity(payload.entityId());

                if (targetEntity instanceof Mob targetMob) {
                    EntityType<?> typeToUpdate = targetMob.getType();

                    // --- SAVE GLOBAL CONFIGURATION ---
                    if (payload.isGlobal()) {
                        MobConfigManager.setGlobalStats(
                                typeToUpdate,
                                followRange,
                                damage,
                                maxHealth,
                                armor
                        );
                        player.sendSystemMessage(I18n.translate("devmod.network.mob_global_saved", typeToUpdate.toShortString()));
                    }

                    // --- APPLICATION TO EXISTING MOBS ---
                    int count = 0;

                    // PERFORMANCE FIX: Use getEntitiesOfClass with AABB limit instead of getAllEntities
                    BlockPos pos = player.blockPosition();
                    int searchRadius = Config.MOB_SEARCH_RADIUS.get();
                    net.minecraft.world.phys.AABB searchBox = new net.minecraft.world.phys.AABB(
                        pos.getX() - searchRadius, pos.getY() - searchRadius, pos.getZ() - searchRadius,
                        pos.getX() + searchRadius, pos.getY() + searchRadius, pos.getZ() + searchRadius
                    );

                    for (Mob mob : level.getEntitiesOfClass(Mob.class, searchBox,
                        m -> m.getType() == typeToUpdate)) {

                        // If specific, skip all except the right one
                        if (!payload.isGlobal() && mob.getId() != payload.entityId()) continue;

                        List<AttributeInstance> attributesToSync = new ArrayList<>();

                        applyAttribute(mob, Attributes.FOLLOW_RANGE, followRange, attributesToSync);
                        applyAttribute(mob, Attributes.ATTACK_DAMAGE, damage, attributesToSync);
                        applyAttribute(mob, Attributes.ARMOR, armor, attributesToSync);
                        applyAttribute(mob, Attributes.ENTITY_INTERACTION_RANGE, attackRange, attributesToSync);
                        applyAttribute(mob, Attributes.MOVEMENT_SPEED, speed, attributesToSync);
                        applyAttribute(mob, Attributes.KNOCKBACK_RESISTANCE, knockbackResist, attributesToSync);

                        AttributeInstance healthAttr = mob.getAttribute(Objects.requireNonNull(Attributes.MAX_HEALTH, "MAX_HEALTH"));
                        if (healthAttr != null) {
                            healthAttr.setBaseValue(maxHealth);
                            attributesToSync.add(healthAttr);
                            // Heal the mob if necessary
                            if (payload.isGlobal() || mob.getId() == payload.entityId()) {
                                mob.setHealth(mob.getMaxHealth());
                            }
                        }

                        if (!attributesToSync.isEmpty()) {
                            ClientboundUpdateAttributesPacket packet = new ClientboundUpdateAttributesPacket(mob.getId(), attributesToSync);
                            level.getChunkSource().broadcast(mob, packet);
                        }
                        count++;
                    }

                    // Send confirmation to client
                    String mobTypeName = Objects.requireNonNull(typeToUpdate.getDescription().getString(), "mobTypeName");
                    MobConfigConfirmPayload confirm = MobConfigConfirmPayload.success(
                        payload.isGlobal(), mobTypeName, count);
                    sendPacket(player, confirm);

                    LOGGER.info("[MobConfig] Player {} {} config for {} ({} mobs affected)",
                        player.getName().getString(),
                        payload.isGlobal() ? "saved GLOBAL" : "applied SPECIFIC",
                        mobTypeName, count);

                    // Notify other players about global config change
                    if (payload.isGlobal()) {
                        String adminName = player.getName().getString();
                        var broadcastMsg = I18n.translate("devmod.network.mob_global_broadcast", adminName, mobTypeName, count);
                        for (ServerPlayer otherPlayer : player.server.getPlayerList().getPlayers()) {
                            if (!otherPlayer.getUUID().equals(player.getUUID())) {
                                otherPlayer.sendSystemMessage(broadcastMsg);
                            }
                        }
                    }
                } else {
                    // Entity not found or not a mob
                    MobConfigConfirmPayload confirm = MobConfigConfirmPayload.failure(
                        Objects.requireNonNull(I18n.translate("devmod.network.target_not_found").getString(), "message"));
                    sendPacket(player, confirm);
                }
            }
        });
    }

    // =================================================================================
    // 2. WEAPON MODIFICATION LOGIC
    // =================================================================================
    private static void handleWeaponData(UpdateWeaponPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                // SECURITY: Validate packet and check permissions
                PacketSecurityService security = PacketSecurityService.INSTANCE;
                ValidationResult validation = security.validatePacket(player, "weapon_stats", true);
                if (!validation.isSuccess()) {
                    player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", validation.getErrorMessage()));
                    sendEditorConfirm(player, false, payload.isGlobal(), "weapon", "<unknown>", validation.getErrorMessage());
                    return;
                }

                ItemStack stack = player.getMainHandItem();
                if (stack.isEmpty()) {
                    sendEditorConfirm(player, false, payload.isGlobal(), "weapon", "<empty>", "No item in hand");
                    return;
                }

                // SECURITY: Validate and clamp multiplier values
                WeaponStats stats = new WeaponStats();
                stats.headMult = (float) security.validateMultiplier(payload.head());
                stats.bodyMult = (float) security.validateMultiplier(payload.body());
                stats.legsMult = (float) security.validateMultiplier(payload.legs());
                stats.armorPenetration = (float) security.validatePenetration(payload.pen());
                stats.baseDamageBonus = (float) security.validateDamage(payload.bonus());

                if (payload.isGlobal()) {
                    WeaponConfigManager.setGlobalStats(stack.getItem(), stats);
                    String itemName = stack.getHoverName().getString();
                    player.sendSystemMessage(I18n.translate("devmod.network.weapon_global_saved", itemName));
                    sendEditorConfirm(player, true, true, "weapon", getItemId(stack), "Weapon global saved");

                    // Notify all other players about global config change
                    String adminName = player.getName().getString();
                    var broadcastMsg = I18n.translate("devmod.network.weapon_global_broadcast", adminName, itemName);
                    for (ServerPlayer otherPlayer : player.server.getPlayerList().getPlayers()) {
                        if (!otherPlayer.getUUID().equals(player.getUUID())) {
                            otherPlayer.sendSystemMessage(broadcastMsg);
                        }
                    }
                } else {
                    WeaponConfigManager.setSpecificStats(stack, stats);
                    // SECURITY: Validate custom name
                    String customName = security.validateString(payload.name(), 64);
                    if (customName != null && !customName.isEmpty()) {
                        stack.set(nn(DataComponents.CUSTOM_NAME), Component.literal(nn(customName)));
                    }
                    player.sendSystemMessage(I18n.translate("devmod.network.weapon_specific_updated"));
                    sendEditorConfirm(player, true, false, "weapon", getItemId(stack), "Weapon specific updated");
                }
            }
        });
    }

    private static void handleWeaponStatsData(com.frenkvs.devmod.network.WeaponStatsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            PacketSecurityService security = PacketSecurityService.INSTANCE;
            ValidationResult validation = security.validatePacket(player, "weapon_stats_nbt", true);
            if (!validation.isSuccess()) {
                player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", validation.getErrorMessage()));
                sendEditorConfirm(player, false, payload.isGlobal(), "weapon", "<unknown>", validation.getErrorMessage());
                return;
            }

            ItemStack stack = player.getMainHandItem();
            if (stack.isEmpty()) {
                sendEditorConfirm(player, false, payload.isGlobal(), "weapon", "<empty>", "No item in hand");
                return;
            }

            // Ensure payload item matches held item
            if (!Objects.equals(stack.getItem(), payload.item().getItem())) {
                sendEditorConfirm(player, false, payload.isGlobal(), "weapon", getItemId(stack), "Item mismatch (held vs payload)");
                return;
            }

            CompoundTag tag = Objects.requireNonNull(payload.statsTag());

            CompoundTag toLoad;
            if (tag.contains("weapon_stats_component")) {
                toLoad = tag.getCompound("weapon_stats_component");
            } else if (tag.contains("WeaponModStats")) {
                toLoad = tag.getCompound("WeaponModStats");
            } else {
                toLoad = tag;
            }

            if (toLoad == null || toLoad.isEmpty()) {
                sendEditorConfirm(player, false, payload.isGlobal(), "weapon", getItemId(stack), "Missing stats");
                return;
            }

            WeaponStats stats = WeaponConfigManager.clampStats(WeaponStats.load(toLoad));
            sanitizeToolRules(stats, security);

            if (payload.isGlobal()) {
                WeaponConfigManager.setGlobalStats(stack.getItem(), stats);
                sendEditorConfirm(player, true, true, "weapon", getItemId(stack), "Weapon global saved");
            } else {
                CompoundTag variant = new CompoundTag();
                if (toLoad.contains("Mace")) variant.put("Mace", Objects.requireNonNull(toLoad.getCompound("Mace")));
                if (toLoad.contains("Trident")) variant.put("Trident", Objects.requireNonNull(toLoad.getCompound("Trident")));
                WeaponConfigManager.setSpecificStats(stack, stats, variant);
                sendEditorConfirm(player, true, false, "weapon", getItemId(stack), "Weapon specific updated");
            }
        });
    }

    private static void handleWeaponStatsDataV2(com.frenkvs.devmod.network.WeaponStatsPayloadV2 payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            PacketSecurityService security = PacketSecurityService.INSTANCE;
            ValidationResult validation = security.validatePacket(player, "weapon_stats_v2", true);
            if (!validation.isSuccess()) {
                player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", validation.getErrorMessage()));
                sendEditorConfirm(player, false, payload.isGlobal(), "weapon", "<unknown>", validation.getErrorMessage());
                return;
            }

            ItemStack stack = player.getMainHandItem();
            if (stack.isEmpty()) {
                sendEditorConfirm(player, false, payload.isGlobal(), "weapon", "<empty>", "No item in hand");
                return;
            }

            if (!Objects.equals(stack.getItem(), payload.item().getItem())) {
                sendEditorConfirm(player, false, payload.isGlobal(), "weapon", getItemId(stack), "Item mismatch (held vs payload)");
                return;
            }

            CompoundTag tag = payload.statsTag();
            if (tag == null || tag.isEmpty()) {
                sendEditorConfirm(player, false, payload.isGlobal(), "weapon", getItemId(stack), "Missing stats");
                return;
            }

            CompoundTag toLoad = tag.contains("weapon_stats_component") ? tag.getCompound("weapon_stats_component")
                : (tag.contains("WeaponModStats") ? tag.getCompound("WeaponModStats") : tag);

            WeaponStats stats;
            if (tag.contains("delta")) {
                // Merge delta onto current stack stats to avoid overwriting untouched fields
                WeaponStats base = WeaponConfigManager.getStats(stack).copy();
                applyDelta(base, tag.getCompound("delta"));
                toLoad = new CompoundTag();
                base.save(toLoad);
                stats = WeaponConfigManager.clampStats(base);
            } else {
                stats = WeaponConfigManager.clampStats(WeaponStats.load(toLoad));
            }
            sanitizeToolRules(stats, security);

            DevMod.LOGGER.info("[Server][WeaponApply] player={} item={} global={} dmg={} spd={} reach={} bonus={} pen={} shred={}",
                player.getGameProfile().getName(), stack.getItem(), payload.isGlobal(),
                stats.attackDamage, stats.attackSpeed, stats.attackReach, stats.baseDamageBonus,
                stats.armorPenetration, stats.armorShred);

            if (payload.isGlobal()) {
                WeaponConfigManager.setGlobalStats(stack.getItem(), stats);
                sendEditorConfirm(player, true, true, "weapon", getItemId(stack), "Weapon global saved");
            } else {
                CompoundTag variant = new CompoundTag();
                if (toLoad.contains("Mace")) variant.put("Mace", Objects.requireNonNull(toLoad.getCompound("Mace")));
                if (toLoad.contains("Trident")) variant.put("Trident", Objects.requireNonNull(toLoad.getCompound("Trident")));
                WeaponConfigManager.setSpecificStats(stack, stats, variant);
                sendEditorConfirm(player, true, false, "weapon", getItemId(stack), "Weapon specific updated");
            }
        });
    }

    private static void applyDelta(WeaponStats target, net.minecraft.nbt.CompoundTag delta) {
        if (target == null || delta == null || delta.isEmpty()) return;
        if (delta.contains("HeadMult")) target.headMult = delta.getFloat("HeadMult");
        if (delta.contains("BodyMult")) target.bodyMult = delta.getFloat("BodyMult");
        if (delta.contains("ArmsMult")) target.armsMult = delta.getFloat("ArmsMult");
        if (delta.contains("LegsMult")) target.legsMult = delta.getFloat("LegsMult");

        if (delta.contains("ArmorPen")) target.armorPenetration = delta.getFloat("ArmorPen");
        if (delta.contains("BaseDmg")) target.baseDamageBonus = delta.getFloat("BaseDmg");
        if (delta.contains("AtkDmg")) target.attackDamage = delta.getFloat("AtkDmg");
        if (delta.contains("AtkSpd")) target.attackSpeed = delta.getFloat("AtkSpd");
        if (delta.contains("AtkRch")) target.attackReach = delta.getFloat("AtkRch");
        if (delta.contains("AtkKB")) target.attackKnockback = delta.getFloat("AtkKB");

        if (delta.contains("DmgBonus")) {
            target.damageBonus = delta.getFloat("DmgBonus");
        }

        if (delta.contains("CritCh")) target.critChance = delta.getFloat("CritCh");
        if (delta.contains("CritDmg")) target.critDamage = delta.getFloat("CritDmg");
        if (delta.contains("ArmorShred")) target.armorShred = delta.getFloat("ArmorShred");

        if (delta.contains("FireDmg")) target.fireDamageBonus = delta.getFloat("FireDmg");
        if (delta.contains("MagicDmg")) target.magicDamageBonus = delta.getFloat("MagicDmg");
        if (delta.contains("Lifesteal")) target.lifesteal = delta.getFloat("Lifesteal");
        if (delta.contains("VsUndead")) target.damageVsUndead = delta.getFloat("VsUndead");
        if (delta.contains("VsArthro")) target.damageVsArthropods = delta.getFloat("VsArthro");
        if (delta.contains("VsPlayers")) target.damageVsPlayers = delta.getFloat("VsPlayers");
        if (delta.contains("TrueDmgPct")) target.trueDamagePercent = delta.getFloat("TrueDmgPct");

        if (delta.contains("MaxDur")) target.maxDurability = delta.getInt("MaxDur");
        if (delta.contains("CurDmg")) target.currentDamage = delta.getInt("CurDmg");
        if (delta.contains("Repair")) target.repairCost = delta.getInt("Repair");
        if (delta.contains("Unbreakable")) target.unbreakable = delta.getBoolean("Unbreakable");
        if (delta.contains("ClearToolRules")) target.clearToolRules = delta.getBoolean("ClearToolRules");
        if (delta.contains("DefaultSpeed")) target.toolDefaultMiningSpeed = delta.getFloat("DefaultSpeed");
        if (delta.contains("DamagePerBlock")) target.toolDamagePerBlock = delta.getInt("DamagePerBlock");
    }

    // =================================================================================
    // 2c. RANGED WEAPON LOGIC
    // =================================================================================
    private static void handleRangedWeaponData(RangedWeaponStatsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PacketSecurityService security = PacketSecurityService.INSTANCE;
                ValidationResult validation = security.validatePacket(player, "ranged_weapon", true);
                if (!validation.isSuccess()) {
                    player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", validation.getErrorMessage()));
                    sendEditorConfirm(player, false, payload.isGlobal(), "ranged", "<unknown>", validation.getErrorMessage());
                    return;
                }

                if (payload.isGlobal()) {
                    player.sendSystemMessage(I18n.translate("devmod.network.global_not_supported"));
                    sendEditorConfirm(player, false, true, "ranged", "<unknown>", "Global ranged not supported");
                    return;
                }

                ItemStack stack = player.getMainHandItem();
                if (stack.isEmpty() || !(stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem)) {
                    sendEditorConfirm(player, false, false, "ranged", getItemId(stack), "No ranged weapon in hand");
                    return;
                }

                // Ensure payload item matches held item to prevent mismatch edits
                if (!Objects.equals(stack.getItem(), payload.item().getItem())) {
                    sendEditorConfirm(player, false, false, "ranged", getItemId(stack), "Item mismatch (held vs payload)");
                    return;
                }

                CompoundTag root = payload.statsTag() == null ? new CompoundTag() : payload.statsTag().copy();
                CompoundTag data = stack.getOrDefault(nn(DataComponents.CUSTOM_DATA), nn(CustomData.EMPTY)).copyTag();
                if (root.contains("RangedStats")) {
                    CompoundTag ranged = Objects.requireNonNull(root.getCompound("RangedStats"));
                    clampRanged(ranged);
                    data.put("RangedStats", ranged);
                } else {
                    clampRanged(root);
                    data.put("RangedStats", Objects.requireNonNull(root));
                }
                stack.set(nn(DataComponents.CUSTOM_DATA), CustomData.of(data));
                player.sendSystemMessage(I18n.translate("devmod.network.weapon_specific_updated"));
                sendEditorConfirm(player, true, false, "ranged", getItemId(stack), "Ranged weapon updated");
                // Broadcast ammo filter info to client for HUD/source indicator
                if (root.contains("ammoFilter")) {
                    String filter = root.getString("ammoFilter");
                    player.sendSystemMessage(I18n.translate("devmod.network.ranged_ammo_filter", filter));
                }
            }
        });
    }

    private static void clampRanged(CompoundTag ranged) {
        ranged.putFloat("drawSpeed", clampFloat(ranged, "drawSpeed", 0.2f, (float) PacketSecurityService.MAX_RANGED_MULT));
        ranged.putFloat("chargeTime", clampFloat(ranged, "chargeTime", 0.2f, (float) PacketSecurityService.MAX_RANGED_MULT));
        ranged.putFloat("accuracy", clampFloat(ranged, "accuracy", 0.2f, 2.0f));
        ranged.putFloat("range", clampFloat(ranged, "range", 0.2f, 5.0f));
        ranged.putFloat("projectileSpeed", clampFloat(ranged, "projectileSpeed", 0.2f, (float) PacketSecurityService.MAX_RANGED_SPEED));
        ranged.putFloat("projectileGravity", clampFloat(ranged, "projectileGravity", 0f, (float) PacketSecurityService.MAX_RANGED_GRAVITY));
        ranged.putFloat("projectileSpread", clampFloat(ranged, "projectileSpread", 0f, (float) PacketSecurityService.MAX_RANGED_SPREAD));
        ranged.putFloat("baseDamage", clampFloat(ranged, "baseDamage", 0f, (float) PacketSecurityService.MAX_RANGED_BASE_DAMAGE));
        ranged.putInt("piercing", clampInt(ranged, "piercing", 0, 10));
        ranged.putInt("multishotCount", clampInt(ranged, "multishotCount", 1, 5));
        ranged.putBoolean("multishot", ranged.getBoolean("multishot"));
        ranged.putBoolean("infinityOverride", ranged.getBoolean("infinityOverride"));
        ranged.putFloat("critChance", clampFloat(ranged, "critChance", 0f, 1f));
        ranged.putFloat("critDamage", clampFloat(ranged, "critDamage", 0.5f, 5.0f));
        ranged.putFloat("riptideDistance", clampFloat(ranged, "riptideDistance", 0f, 64f));
        ranged.putFloat("loyaltySpeed", clampFloat(ranged, "loyaltySpeed", 0f, (float) PacketSecurityService.MAX_RANGED_SPEED));
        ranged.putBoolean("riptideRequiresWater", ranged.getBoolean("riptideRequiresWater"));
        ranged.putBoolean("channeling", ranged.getBoolean("channeling"));
    }

    private static float clampFloat(CompoundTag tag, String key, float min, float max) {
        String safeKey = Objects.requireNonNull(key);
        float val = tag.contains(safeKey) ? tag.getFloat(safeKey) : min;
        return Math.max(min, Math.min(max, val));
    }

    private static int clampInt(CompoundTag tag, String key, int min, int max) {
        String safeKey = Objects.requireNonNull(key);
        int val = tag.contains(safeKey) ? tag.getInt(safeKey) : min;
        return Math.max(min, Math.min(max, val));
    }

    private static void sanitizeToolRules(WeaponStats stats, PacketSecurityService security) {
        stats.toolDefaultMiningSpeed = (float) security.validateToolSpeed(stats.toolDefaultMiningSpeed);
        stats.toolDamagePerBlock = security.validateToolDamagePerBlock(stats.toolDamagePerBlock);
        if (stats.toolRules == null) {
            stats.toolRules = new ArrayList<>();
            return;
        }
        List<WeaponStats.ToolRuleData> cleaned = new ArrayList<>();
        for (WeaponStats.ToolRuleData rule : stats.toolRules) {
            if (rule == null || rule.isEmpty()) continue;
            String tag = security.validateItemId(rule.blockTag);
            if (tag == null || tag.isBlank()) continue;
            WeaponStats.ToolRuleData safe = new WeaponStats.ToolRuleData();
            safe.blockTag = tag;
            safe.speed = (float) security.validateToolSpeed(rule.speed);
            safe.correctForDrops = rule.correctForDrops;
            cleaned.add(safe);
            if (cleaned.size() >= PacketSecurityService.MAX_TOOL_RULES) {
                break;
            }
        }
        stats.toolRules = cleaned;
    }

    // =================================================================================
    // 2b. ARMOR MODIFICATION LOGIC
    // =================================================================================
    private static void handleArmorData(UpdateArmorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                // SECURITY: Validate packet and check permissions
                PacketSecurityService security = PacketSecurityService.INSTANCE;
                ValidationResult validation = security.validatePacket(player, "armor_stats", true);
                if (!validation.isSuccess()) {
                    player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", validation.getErrorMessage()));
                    sendEditorConfirm(player, false, payload.isGlobal(), "armor", payload.itemName(), validation.getErrorMessage());
                    return;
                }

                // Convert payload to ArmorStats
                ArmorStats stats = payload.toArmorStats();

                // Clamp values for security
                stats.physicalReduction = Math.max(0f, Math.min(1f, stats.physicalReduction));
                stats.fireReduction = Math.max(0f, Math.min(1f, stats.fireReduction));
                stats.magicReduction = Math.max(0f, Math.min(1f, stats.magicReduction));
                stats.explosionReduction = Math.max(0f, Math.min(1f, stats.explosionReduction));
                stats.projectileReduction = Math.max(0f, Math.min(1f, stats.projectileReduction));
                stats.armorBonus = Math.max(-20f, Math.min(30f, stats.armorBonus));
                stats.toughnessBonus = Math.max(-10f, Math.min(20f, stats.toughnessBonus));
                stats.knockbackResistance = Math.max(0f, Math.min(1f, stats.knockbackResistance));
                stats.thornsPercent = Math.max(0f, Math.min(0.5f, stats.thornsPercent));
                stats.shieldBlockStrength = Math.max(0f, Math.min(1f, stats.shieldBlockStrength));
                stats.shieldRecoverySpeed = Math.max(0f, Math.min(2f, stats.shieldRecoverySpeed));

                if (payload.isGlobal()) {
                    // Apply to item type globally
                    ResourceLocation itemLoc = ResourceLocation.tryParse(nn(payload.itemName()));
                    if (itemLoc != null && BuiltInRegistries.ITEM.containsKey(itemLoc)) {
                        Item item = BuiltInRegistries.ITEM.get(itemLoc);
                        ArmorConfigManager.setGlobalStats(item, stats);
                        String itemName = nn(item.getDescription().getString());
                        player.sendSystemMessage(I18n.translate("devmod.network.armor_global_saved", itemName));
                        sendEditorConfirm(player, true, true, "armor", itemName, "Armor global saved");

                        // Notify all other players about global config change
                        String adminName = player.getName().getString();
                        var broadcastMsg = I18n.translate("devmod.network.armor_global_broadcast", adminName, itemName);
                        for (ServerPlayer otherPlayer : player.server.getPlayerList().getPlayers()) {
                            if (!otherPlayer.getUUID().equals(player.getUUID())) {
                                otherPlayer.sendSystemMessage(broadcastMsg);
                            }
                        }
                    } else {
                        player.sendSystemMessage(I18n.translate("devmod.network.invalid_item"));
                        sendEditorConfirm(player, false, payload.isGlobal(), "armor", payload.itemName(), "Invalid item");
                    }
                } else {
                    // Apply to specific item in slot
                    EquipmentSlot slot = switch (payload.slot()) {
                        case 0 -> EquipmentSlot.HEAD;
                        case 1 -> EquipmentSlot.CHEST;
                        case 2 -> EquipmentSlot.LEGS;
                        case 3 -> EquipmentSlot.FEET;
                        default -> null;
                    };

                    if (slot != null) {
                        ItemStack armor = player.getItemBySlot(nn(slot));
                        if (!armor.isEmpty() && ArmorConfigManager.isArmor(armor)) {
                            ArmorConfigManager.setSpecificStats(armor, stats);
                            player.sendSystemMessage(I18n.translate("devmod.network.armor_specific_updated"));
                            sendEditorConfirm(player, true, false, "armor", getItemId(armor), "Armor specific updated");
                        } else {
                            player.sendSystemMessage(I18n.translate("devmod.network.no_armor_in_slot"));
                            sendEditorConfirm(player, false, false, "armor", getItemId(armor), "No armor in slot");
                        }
                    } else {
                        // slot == -1 or invalid: try main hand as fallback
                        ItemStack held = player.getMainHandItem();
                        if (!held.isEmpty() && ArmorConfigManager.isArmor(held)) {
                            ArmorConfigManager.setSpecificStats(held, stats);
                            player.sendSystemMessage(I18n.translate("devmod.network.armor_specific_updated"));
                            sendEditorConfirm(player, true, false, "armor", getItemId(held), "Armor specific updated (fallback main hand)");
                            return;
                        }
                        player.sendSystemMessage(I18n.translate("devmod.network.invalid_slot"));
                        sendEditorConfirm(player, false, false, "armor", payload.itemName(), "Invalid slot");
                    }
                }
            }
        });
    }

    private static void handleArmorStatsDataV2(com.frenkvs.devmod.network.ArmorStatsPayloadV2 payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            PacketSecurityService security = PacketSecurityService.INSTANCE;
            ValidationResult validation = security.validatePacket(player, "armor_stats_v2", true);
            if (!validation.isSuccess()) {
                player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", validation.getErrorMessage()));
                sendEditorConfirm(player, false, payload.isGlobal(), "armor", "<unknown>", validation.getErrorMessage());
                return;
            }

            ItemStack payloadStack = payload.item();
            if (payloadStack == null || payloadStack.isEmpty()) {
                sendEditorConfirm(player, false, payload.isGlobal(), "armor", "<empty>", "Missing item");
                return;
            }

            CompoundTag tag = payload.statsTag();
            if (tag == null || tag.isEmpty()) {
                sendEditorConfirm(player, false, payload.isGlobal(), "armor", getItemId(payloadStack), "Missing stats");
                return;
            }

            CompoundTag toLoad = tag.contains("armor_stats_component") ? tag.getCompound("armor_stats_component")
                : (tag.contains("ArmorModStats") ? tag.getCompound("ArmorModStats") : tag);
            ArmorStats stats = ArmorStats.load(toLoad == null ? new CompoundTag() : toLoad);

            // Clamp via PacketSecurityService to avoid malicious payloads
            stats.physicalReduction = (float) security.validateArmorReduction(stats.physicalReduction);
            stats.fireReduction = (float) security.validateArmorReduction(stats.fireReduction);
            stats.magicReduction = (float) security.validateArmorReduction(stats.magicReduction);
            stats.explosionReduction = (float) security.validateArmorReduction(stats.explosionReduction);
            stats.projectileReduction = (float) security.validateArmorReduction(stats.projectileReduction);
            stats.armorBonus = (float) security.validateArmorBonus(stats.armorBonus);
            stats.toughnessBonus = (float) security.validateToughnessBonus(stats.toughnessBonus);
            stats.knockbackResistance = (float) security.validateKnockbackResistance(stats.knockbackResistance);
            stats.thornsPercent = (float) security.validateThornsPercent(stats.thornsPercent);
            stats.shieldBlockStrength = (float) security.validateShieldBlock(stats.shieldBlockStrength);
            stats.shieldRecoverySpeed = (float) security.validateShieldRecovery(stats.shieldRecoverySpeed);

            if (payload.isGlobal()) {
                Item item = payloadStack.getItem();
                ArmorConfigManager.setGlobalStats(item, stats);
                sendEditorConfirm(player, true, true, "armor", getItemId(payloadStack), "Armor global saved");
                return;
            }

            EquipmentSlot slot = switch (payload.slot()) {
                case 0 -> EquipmentSlot.HEAD;
                case 1 -> EquipmentSlot.CHEST;
                case 2 -> EquipmentSlot.LEGS;
                case 3 -> EquipmentSlot.FEET;
                default -> null;
            };

            ItemStack target = ItemStack.EMPTY;
            if (slot != null) {
                target = player.getItemBySlot(nn(slot));
            }

            // Fallback: find matching armor piece by item type
            if (target.isEmpty()) {
                for (EquipmentSlot eqSlot : EquipmentSlot.values()) {
                    if (eqSlot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
                    ItemStack candidate = player.getItemBySlot(eqSlot);
                    if (!candidate.isEmpty() && Objects.equals(candidate.getItem(), payloadStack.getItem())) {
                        target = candidate;
                        slot = eqSlot;
                        break;
                    }
                }
            }

            // Final fallback: offhand/mainhand for shields
            if (target.isEmpty()) {
                ItemStack offhand = player.getOffhandItem();
                if (!offhand.isEmpty() && Objects.equals(offhand.getItem(), payloadStack.getItem())) {
                    target = offhand;
                    slot = EquipmentSlot.OFFHAND;
                }
            }
            if (target.isEmpty()) {
                ItemStack main = player.getMainHandItem();
                if (!main.isEmpty() && Objects.equals(main.getItem(), payloadStack.getItem())) {
                    target = main;
                    slot = EquipmentSlot.MAINHAND;
                }
            }

            if (target.isEmpty() || !ArmorConfigManager.isArmor(target)) {
                sendEditorConfirm(player, false, false, "armor", getItemId(payloadStack), "No matching armor piece");
                return;
            }

            ArmorConfigManager.setSpecificStats(target, stats);
            sendEditorConfirm(player, true, false, "armor", getItemId(target), "Armor specific updated");
        });
    }

    // =================================================================================
    // 3. EQUIPMENT LOGIC
    // =================================================================================
    private static void handleEquipData(EquipMobPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                // SECURITY: Validate packet and check permissions
                PacketSecurityService security = PacketSecurityService.INSTANCE;
                ValidationResult validation = security.validatePacket(player, "equip_mob", true);
                if (!validation.isSuccess()) {
                    player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", validation.getErrorMessage()));
                    return;
                }

                // SECURITY: Validate entity ID
                if (!security.validateEntityId(payload.entityId())) {
                    player.sendSystemMessage(I18n.translate("devmod.network.invalid_entity"));
                    return;
                }

                Entity target = player.serverLevel().getEntity(payload.entityId());
                if (target instanceof Mob mob) {
                    // SECURITY: Validate item IDs before equipping
                    equipSlotSecure(mob, EquipmentSlot.MAINHAND, payload.mainHand(), security);
                    equipSlotSecure(mob, EquipmentSlot.OFFHAND, payload.offHand(), security);
                    equipSlotSecure(mob, EquipmentSlot.HEAD, payload.head(), security);
                    equipSlotSecure(mob, EquipmentSlot.CHEST, payload.chest(), security);
                    equipSlotSecure(mob, EquipmentSlot.LEGS, payload.legs(), security);
                    equipSlotSecure(mob, EquipmentSlot.FEET, payload.feet(), security);

                    player.sendSystemMessage(I18n.translate("devmod.network.equip_updated", mob.getName().getString()));
                }
            }
        });
    }

    // =================================================================================
    // 4. ITEM MODIFICATION LOGIC (Durability, Enchantments, Attributes)
    // =================================================================================
    private static void handleItemModification(ModifyItemPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                // SECURITY: Validate packet and check permissions
                PacketSecurityService security = PacketSecurityService.INSTANCE;
                ValidationResult validation = security.validatePacket(player, "modify_item", true);
                if (!validation.isSuccess()) {
                    player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", validation.getErrorMessage()));
                    return;
                }

                ItemStack stack = player.getMainHandItem();
                if (stack.isEmpty()) {
                    player.sendSystemMessage(I18n.translate("devmod.network.no_item_in_hand"));
                    return;
                }

                // Apply durability
                if (stack.isDamageableItem()) {
                    int maxDamage = stack.getMaxDamage();
                    int newDamage = maxDamage - Math.max(0, Math.min(payload.durability(), maxDamage));
                    stack.setDamageValue(newDamage);
                }

                // Apply unbreakable
                if (payload.unbreakable()) {
                    stack.set(nn(DataComponents.UNBREAKABLE), new net.minecraft.world.item.component.Unbreakable(true));
                } else {
                    stack.remove(nn(DataComponents.UNBREAKABLE));
                }

                // Apply repair cost
                if (payload.repairCost() >= 0) {
                    stack.set(nn(DataComponents.REPAIR_COST), payload.repairCost());
                }

                // Apply enchantment changes
                int enchantFails = 0;
                if (!payload.enchantmentChanges().isEmpty()) {
                    enchantFails = applyEnchantmentChanges(player, stack, payload.enchantmentChanges());
                }

                // Apply attribute changes
                int attrFails = 0;
                if (!payload.attributeChanges().isEmpty()) {
                    attrFails = applyAttributeChanges(player, stack, payload.attributeChanges());
                }

                // Send appropriate feedback based on results
                int totalFails = enchantFails + attrFails;
                if (totalFails == 0) {
                    player.sendSystemMessage(I18n.translate("devmod.network.item_modified"));
                } else {
                    player.sendSystemMessage(I18n.translate("devmod.network.item_modified_with_errors", totalFails));
                }
            }
        });
    }

    /**
     * Apply enchantment changes to the item.
     * Format: "enchantment_id:level" (level 0 = remove)
     * @return Number of failed enchantment changes
     */
    private static int applyEnchantmentChanges(ServerPlayer player, ItemStack stack, List<String> changes) {
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(
            nn(stack.getOrDefault(nn(DataComponents.ENCHANTMENTS), nn(ItemEnchantments.EMPTY)))
        );

        int failCount = 0;
        List<String> failedEnchants = new ArrayList<>();

        for (String change : changes) {
            String[] parts = change.split(":");
            if (parts.length < 2) {
                failCount++;
                failedEnchants.add(change + " (invalid format)");
                continue;
            }

            // Handle both "namespace:path:level" and "namespace:path" formats
            String enchantId;
            int level;
            if (parts.length >= 3) {
                enchantId = parts[0] + ":" + parts[1];
                try {
                    level = Integer.parseInt(parts[2]);
                } catch (NumberFormatException e) {
                    failCount++;
                    failedEnchants.add(enchantId + " (invalid level)");
                    continue;
                }
            } else {
                // Assume minecraft namespace if not specified
                enchantId = parts[0].contains(":") ? parts[0] : "minecraft:" + parts[0];
                try {
                    level = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    failCount++;
                    failedEnchants.add(enchantId + " (invalid level)");
                    continue;
                }
            }

            try {
                ResourceLocation enchantLoc = nn(ResourceLocation.parse(nn(enchantId)));
                var registry = player.server.registryAccess().registryOrThrow(nn(Registries.ENCHANTMENT));
                var enchantHolder = registry.getHolder(nn(enchantLoc));

                if (enchantHolder.isPresent()) {
                    Holder<Enchantment> holder = nn(enchantHolder.get());
                    if (level <= 0) {
                        // Remove enchantment
                        enchantments.removeIf(h -> h.equals(holder));
                    } else {
                        // Add or update enchantment
                        enchantments.set(nn(holder), level);
                    }
                } else {
                    // Enchantment not found in registry
                    failCount++;
                    failedEnchants.add(enchantId + " (not found)");
                    LOGGER.warn("Enchantment '{}' not found in registry", enchantId);
                }
            } catch (Exception e) {
                failCount++;
                failedEnchants.add(enchantId + " (error)");
                LOGGER.warn("Failed to apply enchantment change '{}': {}", change, e.getMessage());
            }
        }

        stack.set(nn(DataComponents.ENCHANTMENTS), enchantments.toImmutable());

        // Send feedback to player about failed enchantments
        if (failCount > 0) {
            String failedList = String.join(", ", failedEnchants);
            player.sendSystemMessage(I18n.translate("devmod.network.enchant_failed", failCount, failedList));
        }

        return failCount;
    }

    /**
     * Apply attribute changes to the item.
     * Format: "attribute_id:value:operation"
     * @return Number of failed attribute changes
     */
    private static int applyAttributeChanges(ServerPlayer player, ItemStack stack, List<String> changes) {
        // Get existing modifiers or create new list
        ItemAttributeModifiers existing = nn(stack.getOrDefault(nn(DataComponents.ATTRIBUTE_MODIFIERS), nn(ItemAttributeModifiers.EMPTY)));
        List<ItemAttributeModifiers.Entry> entries = new ArrayList<>(existing.modifiers());

        int failCount = 0;
        List<String> failedAttrs = new ArrayList<>();

        for (String change : changes) {
            String[] parts = change.split(":");
            if (parts.length < 3) {
                failCount++;
                failedAttrs.add(change + " (invalid format)");
                continue;
            }

            // Handle "namespace:path:value:operation" format
            String attrId;
            double value;
            int operation;

            try {
                if (parts.length >= 4) {
                    attrId = parts[0] + ":" + parts[1];
                    value = Double.parseDouble(parts[2]);
                    operation = Integer.parseInt(parts[3]);
                } else {
                    attrId = parts[0].contains(":") ? parts[0] : "minecraft:" + parts[0];
                    value = Double.parseDouble(parts[1]);
                    operation = Integer.parseInt(parts[2]);
                }
            } catch (NumberFormatException e) {
                failCount++;
                failedAttrs.add(change + " (invalid number)");
                continue;
            }

            try {
                ResourceLocation attrLoc = nn(ResourceLocation.parse(nn(attrId)));
                var registry = player.server.registryAccess().registryOrThrow(nn(Registries.ATTRIBUTE));
                var attrHolder = registry.getHolder(nn(attrLoc));
                if (attrHolder.isEmpty()) {
                    var mapped = com.frenkvs.devmod.integration.PufferfishCompat.map(attrLoc, registry);
                    if (mapped != null) {
                        attrHolder = java.util.Optional.of(mapped);
                    }
                }

                if (attrHolder.isPresent()) {
                    Holder<Attribute> holder = nn(attrHolder.get());
                    AttributeModifier.Operation op = switch (operation) {
                        case 1 -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
                        case 2 -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
                        default -> AttributeModifier.Operation.ADD_VALUE;
                    };

                    // Remove existing modifiers for this attribute
                    entries.removeIf(e -> e.attribute().equals(holder));

                    // Add new modifier
                    ResourceLocation modifierId = nn(ResourceLocation.fromNamespaceAndPath("devmod", "custom_" + attrLoc.getPath()));
                    AttributeModifier modifier = new AttributeModifier(modifierId, value, op);
                    entries.add(new ItemAttributeModifiers.Entry(nn(holder), modifier, EquipmentSlotGroup.MAINHAND));
                } else {
                    // Attribute not found in registry
                    failCount++;
                    failedAttrs.add(attrId + " (not found)");
                    LOGGER.warn("Attribute '{}' not found in registry", attrId);
                }
            } catch (Exception e) {
                failCount++;
                failedAttrs.add(attrId + " (error)");
                LOGGER.warn("Failed to apply attribute change '{}': {}", change, e.getMessage());
            }
        }

        stack.set(nn(DataComponents.ATTRIBUTE_MODIFIERS), new ItemAttributeModifiers(entries, existing.showInTooltip()));

        // Send feedback to player about failed attributes
        if (failCount > 0) {
            String failedList = String.join(", ", failedAttrs);
            player.sendSystemMessage(I18n.translate("devmod.network.attr_failed", failCount, failedList));
        }

        return failCount;
    }

    // =================================================================================
    // HELPER METHODS (Must be INSIDE the class, before the closing brace)
    // =================================================================================

    /**
     * Secure equipSlot that validates item IDs before equipping.
     * Prevents injection attacks through malicious item ID strings.
     */
    private static void equipSlotSecure(Mob mob, EquipmentSlot slot, String itemName, PacketSecurityService security) {
        if (itemName == null || itemName.trim().isEmpty()) return;

        // Handle special clear commands
        if (itemName.equalsIgnoreCase("air") || itemName.equalsIgnoreCase("clear")) {
            mob.setItemSlot(nn(slot), nn(ItemStack.EMPTY));
            return;
        }

        // SECURITY: Validate item ID format
        String validatedItemId = security.validateItemId(itemName);
        if (validatedItemId == null) {
            LOGGER.warn("Invalid item ID rejected: '{}'", itemName);
            return;
        }

        try {
            ResourceLocation id = ResourceLocation.parse(validatedItemId.contains(":") ? validatedItemId : "minecraft:" + validatedItemId);
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item != net.minecraft.world.item.Items.AIR) {
                ItemStack stack = new ItemStack(item);
                mob.setItemSlot(nn(slot), stack);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to equip validated item '{}' to slot {} for mob {}: {}",
                validatedItemId, slot, mob.getType().getDescription().getString(), e.getMessage());
        }
    }

    // HERE IS THE METHOD THAT WAS GIVING YOU ERROR: NOW IT'S INSIDE THE CLASS
    private static void applyAttribute(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, double value, List<AttributeInstance> syncList) {
        AttributeInstance instance = mob.getAttribute(nn(attr));
        if (instance != null) {
            instance.setBaseValue(value);
            syncList.add(instance);
        }
    }

    // =================================================================================
    // 5. ENDURANCE QUEST LOGIC
    // =================================================================================
    private static void handleStartEnduranceQuest(StartQuestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                // SECURITY: Validate packet and check permissions
                PacketSecurityService security = PacketSecurityService.INSTANCE;
                ValidationResult validation = security.validatePacket(player, "endurance_quest", true);
                if (!validation.isSuccess()) {
                    player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", validation.getErrorMessage()));
                    return;
                }

                // Validate mob ID
                String mobId = payload.mobId();
                if (mobId == null || mobId.isEmpty()) {
                    player.sendSystemMessage(I18n.translate("devmod.network.invalid_entity"));
                    return;
                }

                // Validate wave count
                int waves = Math.max(1, Math.min(payload.totalWaves(), 100));
                int arenaSize = Math.max(32, Math.min(payload.arenaSize(), 128));

                try {
                    ResourceLocation mobLocation = ResourceLocation.parse(mobId);

                    // Create quest settings
                    EnduranceQuestManager.QuestSettings settings = new EnduranceQuestManager.QuestSettings();
                    settings.totalWaves = waves;
                    settings.endlessMode = payload.endlessMode();
                    settings.arenaSize = arenaSize;

                    // Start the quest
                    EnduranceQuestManager.StartQuestResult result = EnduranceQuestManager.INSTANCE.startQuest(
                        player,
                        mobLocation,
                        settings
                    );

                    if (result.success()) {
                        player.sendSystemMessage(I18n.translate("devmod.network.quest_started_msg", mobId));
                        LOGGER.info("[EnduranceQuest] Player {} started quest for {} ({} waves, endless={})",
                            player.getName().getString(), mobId, waves, payload.endlessMode());
                    } else {
                        player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", result.message()));
                    }

                } catch (Exception e) {
                    player.sendSystemMessage(I18n.translate("devmod.network.failed_start_quest", e.getMessage()));
                    LOGGER.error("[EnduranceQuest] Failed to start quest", e);
                }
            }
        });
    }

    // =================================================================================
    // 6. QUEST ACTIONS LOGIC (respawn, checkpoint, abandon)
    // =================================================================================
    private static void handleQuestAction(QuestActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                QuestActionPayload.Action action = payload.action();

                // Get current quest state to handle context-aware actions
                var sessionOpt = EnduranceQuestManager.INSTANCE.getActiveSession(player);
                if (sessionOpt.isEmpty()) {
                    player.sendSystemMessage(I18n.translate("devmod.network.no_active_quest"));
                    return;
                }
                var session = sessionOpt.get();
                boolean awaitingRespawn = session.isAwaitingRespawnChoice();
                boolean atCheckpoint = session.getQuest().getState() ==
                    com.frenkvs.devmod.endurance.EnduranceQuestState.WAVE_COMPLETE;

                try {
                    switch (action) {
                        case CONTINUE_AFTER_DEATH -> {
                            // Context-aware: if at checkpoint, continue to next wave; if dead, respawn
                            if (awaitingRespawn) {
                                EnduranceQuestManager.INSTANCE.handleRespawnChoice(player, true);
                                LOGGER.info("[EnduranceQuest] Player {} respawning after death",
                                    player.getName().getString());
                            } else if (atCheckpoint) {
                                EnduranceQuestManager.INSTANCE.continueToNextWave(player);
                                LOGGER.info("[EnduranceQuest] Player {} continuing to next wave",
                                    player.getName().getString());
                            } else {
                                player.sendSystemMessage(I18n.translate("devmod.network.cannot_continue"));
                            }
                        }
                        case GIVE_UP_AFTER_DEATH -> {
                            // Context-aware: if at checkpoint, exit; if dead, give up; otherwise abandon
                            if (awaitingRespawn) {
                                EnduranceQuestManager.INSTANCE.handleRespawnChoice(player, false);
                                LOGGER.info("[EnduranceQuest] Player {} gave up after death",
                                    player.getName().getString());
                            } else if (atCheckpoint) {
                                EnduranceQuestManager.INSTANCE.exitAtCheckpoint(player);
                                LOGGER.info("[EnduranceQuest] Player {} exited at checkpoint",
                                    player.getName().getString());
                            } else {
                                EnduranceQuestManager.INSTANCE.abandonQuest(player);
                                LOGGER.info("[EnduranceQuest] Player {} abandoned quest",
                                    player.getName().getString());
                            }
                        }
                        case CONTINUE_TO_NEXT_WAVE -> {
                            EnduranceQuestManager.INSTANCE.continueToNextWave(player);
                            LOGGER.info("[EnduranceQuest] Player {} continuing to next wave",
                                player.getName().getString());
                        }
                        case EXIT_AT_CHECKPOINT -> {
                            EnduranceQuestManager.INSTANCE.exitAtCheckpoint(player);
                            LOGGER.info("[EnduranceQuest] Player {} exited at checkpoint",
                                player.getName().getString());
                        }
                        case ABANDON_QUEST -> {
                            EnduranceQuestManager.INSTANCE.abandonQuest(player);
                            LOGGER.info("[EnduranceQuest] Player {} abandoned quest",
                                player.getName().getString());
                        }
                    }
                } catch (Exception e) {
                    player.sendSystemMessage(I18n.translate("devmod.network.quest_action_failed", e.getMessage()));
                    LOGGER.error("[EnduranceQuest] Quest action failed", e);
                }
            }
        });
    }

    // =================================================================================
    // 7. QUEST SYNC HANDLER (client-side)
    // =================================================================================
    private static void handleQuestSync(QuestSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Update client-side cache
            ClientQuestCache.update(payload);
        });
    }

    // =================================================================================
    // GLOBAL CONFIG SYNC HANDLER (server to client)
    // =================================================================================
    private static void handleGlobalConfigSync(com.frenkvs.devmod.network.GlobalConfigSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Apply received configs to client-side managers
            payload.applyToClientConfigs();
            LOGGER.debug("[NetworkHandler] Received global config sync");
        });
    }

    // =================================================================================
    // 8. SHOP PURCHASE HANDLER
    // =================================================================================
    private static void handleShopPurchase(ShopPurchasePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                String itemId = payload.itemId();

                // Validate item ID
                if (itemId == null || itemId.isEmpty()) {
                    player.sendSystemMessage(I18n.translate("devmod.network.invalid_item"));
                    return;
                }

                // Process purchase through RewardSystem
                RewardSystem.PurchaseResult result = RewardSystem.INSTANCE.purchaseItem(player, itemId);

                if (!result.success()) {
                    player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", result.message()));
                }
                // Success message is sent by RewardSystem.purchaseItem()

                // Always sync wallet after purchase attempt (so client sees updated balance)
                sendShopSync(player);

                LOGGER.info("[Shop] Player {} attempted purchase of {}: {}",
                    player.getName().getString(), itemId, result.success() ? "SUCCESS" : result.message());
            }
        });
    }

    // =================================================================================
    // 9. SHOP SYNC HANDLER (client-side)
    // =================================================================================
    private static void handleShopSync(ShopSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Update client-side shop cache
            ClientShopCache.update(payload);
        });
    }

    /**
     * Send shop/wallet sync data to a player.
     * Call this when:
     * - Player opens shop
     * - Player completes a purchase
     * - Player earns currency (quest rewards)
     */
    public static void sendShopSync(ServerPlayer player) {
        RewardSystem.PlayerWallet wallet = RewardSystem.INSTANCE.getWallet(player.getUUID());
        ShopSyncPayload payload = ShopSyncPayload.fromWallet(wallet);
        sendPacket(player, payload);
    }

    // =================================================================================
    // 10. REQUEST SHOP SYNC HANDLER (server-side)
    // =================================================================================
    private static void handleRequestShopSync(RequestShopSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                sendShopSync(player);
            }
        });
    }

    // =================================================================================
    // 11. MOB CONFIG CONFIRMATION HANDLER (client-side)
    // =================================================================================
    private static void handleMobConfigConfirm(MobConfigConfirmPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Delegate to client feedback handler
            ClientConfigFeedback.handleMobConfigConfirm(payload);
        });
    }

    // =================================================================================
    // 11b. EDITOR APPLY CONFIRMATION HANDLER (client-side)
    // =================================================================================
    private static void handleEditorApplyConfirm(EditorApplyConfirmPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.screen instanceof com.frenkvs.devmod.ui.editor.ItemEditorScreen screen) {
                screen.onServerConfirm(payload);
            }
        });
    }

    // =================================================================================
    // 12. QUEST DEATH SCREEN HANDLER (client-side)
    // =================================================================================
    private static void handleQuestDeath(QuestDeathPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Open the death screen on client
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> {
                    mc.setScreen(new QuestDeathScreen());
                });
            }
        });
    }

    /**
     * Send quest death notification to player.
     * Call this when player dies during an Endurance Quest.
     */
    public static void sendQuestDeathScreen(ServerPlayer player, int currentWave, int totalWaves,
            boolean endlessMode, int pointsEarned, int deathsThisRun, int respawnCost) {
        QuestDeathPayload payload = new QuestDeathPayload(
            currentWave, totalWaves, endlessMode, pointsEarned, deathsThisRun, respawnCost
        );
        sendPacket(player, payload);
    }

    // =================================================================================
    // 13. PERK CHOICES HANDLER (client-side)
    // =================================================================================
    private static void handlePerkChoices(PerkChoicesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Open the perk selection screen on client
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> {
                    mc.setScreen(new PerkSelectionScreen(payload.waveNumber(), payload.choices()));
                });
            }
        });
    }

    /**
     * Send perk choices to player for selection.
     * Call this after wave completion when perks should be offered.
     */
    public static void sendPerkChoices(ServerPlayer player, int waveNumber, java.util.List<PerkSystem.Perk> perks) {
        java.util.List<PerkChoicesPayload.PerkChoice> choices = new java.util.ArrayList<>();

        // Get player's current perk session for stacks
        java.util.Optional<PerkSystem.PerkSession> sessionOpt = PerkSystem.INSTANCE.getSession(player.getUUID());

        for (PerkSystem.Perk perk : perks) {
            int currentStacks = sessionOpt.map(s -> s.getPerkStacks(perk.id)).orElse(0);
            choices.add(PerkChoicesPayload.PerkChoice.from(perk, currentStacks));
        }

        PerkChoicesPayload payload = new PerkChoicesPayload(waveNumber, choices);
        sendPacket(player, payload);
    }

    // =================================================================================
    // 14. PERK SELECTION HANDLER (server-side)
    // =================================================================================
    private static void handlePerkSelection(PerkSelectionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                String perkId = payload.perkId();

                if (payload.isSkip()) {
                    // Player skipped perk selection
                    player.sendSystemMessage(nn(I18n.translate("devmod.network.perk_skipped")
                        .withStyle(net.minecraft.ChatFormatting.GRAY)));
                    LOGGER.info("[Perk] Player {} skipped perk selection", player.getName().getString());

                    // Clear pending choices
                    PerkSystem.INSTANCE.getSession(player.getUUID())
                        .ifPresent(PerkSystem.PerkSession::clearPendingChoices);
                } else {
                    // Find the perk index in pending choices
                    java.util.Optional<PerkSystem.PerkSession> sessionOpt = PerkSystem.INSTANCE.getSession(player.getUUID());
                    if (sessionOpt.isEmpty()) {
                        player.sendSystemMessage(I18n.translate("devmod.network.no_perk_session"));
                        return;
                    }

                    PerkSystem.PerkSession session = sessionOpt.get();
                    java.util.List<PerkSystem.Perk> pendingChoices = session.getPendingChoices();

                    // Find index of selected perk by ID
                    int choiceIndex = -1;
                    for (int i = 0; i < pendingChoices.size(); i++) {
                        if (pendingChoices.get(i).id.equals(perkId)) {
                            choiceIndex = i;
                            break;
                        }
                    }

                    if (choiceIndex >= 0) {
                        boolean success = PerkSystem.INSTANCE.selectPerk(player, choiceIndex);
                        if (success) {
                            LOGGER.info("[Perk] Player {} selected perk: {}", player.getName().getString(), perkId);
                        } else {
                            player.sendSystemMessage(I18n.translate("devmod.network.perk_failed", perkId));
                            LOGGER.warn("[Perk] Failed to apply perk {} for player {}", perkId, player.getName().getString());
                        }
                    } else {
                        player.sendSystemMessage(I18n.translate("devmod.network.perk_invalid", perkId));
                        LOGGER.warn("[Perk] Perk {} not found in pending choices for player {}", perkId, player.getName().getString());
                    }
                }
            }
        });
    }

    // =================================================================================
    // 15. QUEST COMPLETION HANDLER (client-side)
    // =================================================================================
    private static void handleQuestCompletion(QuestCompletionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Open the completion screen on client
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> {
                    mc.setScreen(new QuestCompletionScreen(payload));
                });
            }
        });
    }

    // =================================================================================
    // 23. INSTANCE LOADING OVERLAY (client-side)
    // =================================================================================
    private static void handleInstanceLoading(InstanceLoadingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.show()) {
                // Translate the i18n key to localized string
                String translatedStatus = com.frenkvs.devmod.util.I18n.translate(payload.status()).getString();
                InstanceLoadingOverlay.show(translatedStatus);
            } else {
                InstanceLoadingOverlay.hide();
            }
        });
    }

    /**
     * Send quest completion notification to player.
     * Call this when quest is completed successfully.
     */
    public static void sendQuestCompletionScreen(ServerPlayer player,
            com.frenkvs.devmod.endurance.EnduranceQuest quest,
            RewardSystem.QuestRewards rewards,
            ComboSystem.ComboSession comboSession,
            int maxCombo) {

        java.util.List<String> achievementNames = new java.util.ArrayList<>();
        if (rewards.achievementsUnlocked != null) {
            for (RewardSystem.Achievement achievement : rewards.achievementsUnlocked) {
                achievementNames.add(achievement.displayName);
            }
        }

        QuestCompletionPayload payload = new QuestCompletionPayload(
            quest.getDisplayName(),
            quest.getCurrentWave(),
            quest.getTotalWaves(),
            quest.isEndlessMode(),
            quest.getSessionDuration(),

            rewards.tokensEarned,
            rewards.baseTokens,
            rewards.prestigeEarned,
            rewards.bloodGemsEarned,

            rewards.styleMultiplier,
            rewards.mutatorMultiplier,

            rewards.noHitBonus,
            rewards.speedBonus,
            rewards.styleRank != null ? rewards.styleRank.ordinal() : 0,
            rewards.activeMutators,

            quest.getMobsKilledThisSession(),
            quest.getTotalDamageDealtThisSession(),
            quest.getDamageTakenThisSession(),
            quest.getDeathsThisSession(),
            maxCombo,

            achievementNames
        );

        sendPacket(player, payload);
    }

    // =================================================================================
    // 16. PERSONAL RECORDS SYNC HANDLER (client-side)
    // =================================================================================
    private static void handlePersonalRecordsSync(PersonalRecordsSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Update client cache
            ClientPersonalRecordsCache.update(payload);
        });
    }

    // =================================================================================
    // 17. REQUEST PERSONAL RECORDS HANDLER (server-side)
    // =================================================================================
    private static void handleRequestPersonalRecords(RequestPersonalRecordsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                sendPersonalRecordsSync(player);
            }
        });
    }

    /**
     * Send personal records to player.
     * Call this when player opens EnduranceQuestScreen.
     */
    public static void sendPersonalRecordsSync(ServerPlayer player) {
        EnduranceQuestManager.PlayerQuestStats stats = EnduranceQuestManager.INSTANCE.getPlayerStats(player.getUUID());

        // Convert mob records to payload format
        java.util.Map<String, PersonalRecordsSyncPayload.MobRecord> mobRecords = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, EnduranceQuestManager.MobQuestRecord> entry : stats.getMobRecords().entrySet()) {
            EnduranceQuestManager.MobQuestRecord record = entry.getValue();
            mobRecords.put(entry.getKey(), new PersonalRecordsSyncPayload.MobRecord(
                record.attempts,
                record.completions,
                record.bestScore,
                record.highestWave
            ));
        }

        PersonalRecordsSyncPayload syncPayload = new PersonalRecordsSyncPayload(
            stats.getTotalQuestsAttempted(),
            stats.getTotalQuestsCompleted(),
            stats.getTotalPointsEarned(),
            mobRecords
        );

        sendPacket(player, syncPayload);
    }

    // =================================================================================
    // BOSS ALERT
    // =================================================================================

    /**
     * Send boss alert to a player. Called from BossWaveSystem 3s before boss spawn.
     */
    public static void sendBossAlert(ServerPlayer player, long durationMs, String bossType) {
        BossAlertPayload payload = new BossAlertPayload(durationMs, bossType);
        sendPacket(player, payload);
    }

    // =================================================================================
    // BADGE UNLOCK
    // =================================================================================

    /**
     * Send badge unlock notification to a player.
     */
    public static void sendBadgeUnlock(ServerPlayer player, String badgeName, String rarity) {
        BadgeUnlockPayload payload = new BadgeUnlockPayload(badgeName, rarity);
        sendPacket(player, payload);
    }

    // =================================================================================
    // TOKEN GAIN ANIMATION
    // =================================================================================

    /**
     * Send token gain animation to a player. Shows floating "+X Tokens" text.
     */
    public static void sendTokenGain(ServerPlayer player, int amount) {
        if (amount > 0) {
            TokenGainPayload payload = new TokenGainPayload(amount);
            sendPacket(player, payload);
        }
    }

    // =================================================================================
    // RECORD BANNER
    // =================================================================================

    /**
     * Send record banner notification to a player.
     * Shows "NEW RECORD!" banner with record type and value.
     *
     * @param player The player to notify
     * @param recordType Type of record (e.g., "BEST WAVE", "HIGH SCORE", "FASTEST TIME")
     * @param recordValue Value achieved (e.g., "Wave 15", "12,500 pts", "2:34")
     */
    public static void sendRecordBanner(ServerPlayer player, String recordType, String recordValue) {
        RecordBannerPayload payload = new RecordBannerPayload(recordType, recordValue);
        sendPacket(player, payload);
    }

    // =================================================================================
    // COMBO DECAY FEEDBACK
    // =================================================================================

    /**
     * Send combo decay feedback to a player.
     * Shows visual/audio feedback when combo is lost or rank drops.
     *
     * @param player The player to notify
     * @param lostCombo The combo count that was lost
     * @param previousRank Previous style rank ordinal
     * @param newRank New style rank ordinal
     */
    public static void sendComboDecay(ServerPlayer player, int lostCombo, int previousRank, int newRank) {
        if (lostCombo >= 3 || newRank < previousRank) {
            ComboDecayPayload payload = new ComboDecayPayload(lostCombo, previousRank, newRank);
            sendPacket(player, payload);
        }
    }

    // =================================================================================
    // INSTANCE LOADING OVERLAY
    // =================================================================================

    /**
     * Show loading overlay on client during instance creation.
     */
    public static void sendInstanceLoadingShow(ServerPlayer player, String status) {
        InstanceLoadingPayload payload = new InstanceLoadingPayload(true, status);
        sendPacket(player, payload);
    }

    /**
     * Hide loading overlay on client when instance is ready.
     */
    public static void sendInstanceLoadingHide(ServerPlayer player) {
        InstanceLoadingPayload payload = InstanceLoadingPayload.hide();
        sendPacket(player, payload);
    }

    // =================================================================================
    // 25. INVITE RESPONSE HANDLER (server-side)
    // =================================================================================
    private static void handleInviteResponse(InviteResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                UUID playerId = player.getUUID();
                String playerName = player.getName().getString();
                UUID inviteId = payload.inviteId();

                PartyManager.ResponseResult result = PartyManager.INSTANCE.handleInviteResponse(
                    playerId, playerName, inviteId, payload.accepted());

                if (result.success()) {
                    if (payload.accepted()) {
                        LOGGER.info("[Party] {} accepted invite {}", playerName, inviteId);
                        // Sync party to new member
                        sendPartySyncToPlayer(player);
                        // Notify other members
                        if (result.partyId() != null) {
                            notifyPartyMembers(player.server, result.partyId(),
                                PartyNotificationPayload.memberJoined(playerId, playerName), playerId);
                            syncPartyToAllMembers(player.server, result.partyId());
                        }
                    } else {
                        LOGGER.info("[Party] {} declined invite {}", playerName, inviteId);
                    }
                } else {
                    // Failed - send error message
                    String errorMsg = result.errorMessage() != null ? result.errorMessage() : "Unknown error";
                    player.sendSystemMessage(I18n.translate("devmod.party.invite_error", errorMsg));
                }
            }
        });
    }

    // =================================================================================
    // 24. PARTY ACTION HANDLER (server-side)
    // =================================================================================
    private static void handlePartyAction(PartyActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                UUID playerId = player.getUUID();
                String playerName = player.getName().getString();

                switch (payload.action()) {
                    case CREATE_PARTY -> {
                        // Create a new party with this player as leader
                        var questType = payload.getQuestType();
                        PartyData party = PartyManager.INSTANCE.createParty(playerId, playerName, questType);
                        if (party != null) {
                            LOGGER.info("[Party] {} created party {} (type: {})", playerName, party.getPartyId(), questType);
                            sendPartySyncToPlayer(player);
                        } else {
                            player.sendSystemMessage(I18n.translate("devmod.party.already_in_party"));
                        }
                    }

                    case TOGGLE_READY -> {
                        PartyData party = PartyManager.INSTANCE.getPlayerParty(playerId);
                        if (party != null) {
                            boolean currentReady = party.isReady(playerId);
                            party.setReady(playerId, !currentReady);
                            LOGGER.debug("[Party] {} toggled ready: {}", playerName, !currentReady);
                            syncPartyToAllMembers(player.server, party.getPartyId());
                        }
                    }

                    case LEAVE_PARTY -> {
                        PartyData party = PartyManager.INSTANCE.getPlayerParty(playerId);
                        if (party != null) {
                            UUID partyId = party.getPartyId();
                            if (PartyManager.INSTANCE.leaveParty(playerId)) {
                                LOGGER.info("[Party] {} left party {}", playerName, partyId);
                                // Notify remaining members
                                notifyPartyMembers(player.server, partyId,
                                    PartyNotificationPayload.memberLeft(playerId, playerName), null);
                                syncPartyToAllMembers(player.server, partyId);
                                // Clear party state for leaving player
                                sendPartySyncToPlayer(player);
                            }
                        }
                    }

                    case KICK_MEMBER -> {
                        if (payload.targetPlayerId() != null) {
                            PartyData party = PartyManager.INSTANCE.getPlayerParty(playerId);
                            if (party != null && PartyManager.INSTANCE.kickMember(playerId, payload.targetPlayerId())) {
                                UUID partyId = party.getPartyId();
                                String kickedName = party.getMemberName(payload.targetPlayerId());
                                LOGGER.info("[Party] {} kicked {} from party {}", playerName, kickedName, partyId);

                                // Notify kicked player
                                ServerPlayer kickedPlayer = player.server.getPlayerList().getPlayer(nn(payload.targetPlayerId()));
                                if (kickedPlayer != null) {
                                    sendPartyNotification(kickedPlayer,
                                        PartyNotificationPayload.youWereKicked(playerId, playerName));
                                    sendPartySyncToPlayer(kickedPlayer);
                                }

                                // Sync to remaining members
                                syncPartyToAllMembers(player.server, partyId);
                            }
                        }
                    }

                    case SET_QUEST_TYPE -> {
                        PartyData party = PartyManager.INSTANCE.getPlayerParty(playerId);
                        if (party != null && party.isLeader(playerId)) {
                            var newType = payload.getQuestType();
                            if (party.setQuestType(newType)) {
                                LOGGER.info("[Party] {} changed quest type to {} in party {}",
                                    playerName, newType, party.getPartyId());
                                syncPartyToAllMembers(player.server, party.getPartyId());
                            }
                        }
                    }

                    case SET_MOB_TYPE -> {
                        PartyData party = PartyManager.INSTANCE.getPlayerParty(playerId);
                        if (party != null && party.isLeader(playerId)) {
                            ResourceLocation mobId = payload.getMobResourceLocation();
                            if (mobId != null) {
                                // Validate mob ID exists in registry
                                var mobConfig = com.frenkvs.devmod.endurance.EnduranceQuestRegistry.INSTANCE.getMobConfig(mobId);
                                if (mobConfig.isPresent()) {
                                    if (party.setSelectedMobId(playerId, mobId)) {
                                        LOGGER.info("[Party] {} changed mob type to {} in party {}",
                                            playerName, mobId, party.getPartyId());
                                        syncPartyToAllMembers(player.server, party.getPartyId());
                                    }
                                } else {
                                    LOGGER.warn("[Party] {} tried to set invalid mob type: {}", playerName, mobId);
                                    player.sendSystemMessage(I18n.translate("devmod.party.invalid_mob"));
                                }
                            }
                        }
                    }

                    case DISBAND_PARTY -> {
                        PartyData party = PartyManager.INSTANCE.getPlayerParty(playerId);
                        if (party != null && party.isLeader(playerId)) {
                            UUID partyId = party.getPartyId();
                            var members = new java.util.ArrayList<>(party.getMembers());

                            if (PartyManager.INSTANCE.disbandParty(playerId)) {
                                LOGGER.info("[Party] {} disbanded party {}", playerName, partyId);

                                // Notify all members and clear their party state
                                for (UUID memberId : members) {
                                    ServerPlayer member = player.server.getPlayerList().getPlayer(nn(memberId));
                                    if (member != null) {
                                        if (!memberId.equals(playerId)) {
                                            sendPartyNotification(member,
                                                PartyNotificationPayload.partyDisbanded(playerId, playerName));
                                        }
                                        sendPartySyncToPlayer(member);
                                    }
                                }
                            }
                        }
                    }

                    case START_QUEST -> {
                        PartyData party = PartyManager.INSTANCE.getPlayerParty(playerId);
                        if (party != null && party.isLeader(playerId) && party.canStartQuest()) {
                            LOGGER.info("[Party] {} starting quest for party {}", playerName, party.getPartyId());

                            // Start the quest sequence with correct signature
                            QuestStartSequence.ValidationResult result = QuestStartSequence.INSTANCE.startSequence(
                                player.server,
                                party,
                                player
                            );

                            if (!result.success()) {
                                player.sendSystemMessage(I18n.translate(result.errorMessage()));
                            }
                        } else {
                            player.sendSystemMessage(I18n.translate("devmod.party.cannot_start"));
                        }
                    }
                }
            }
        });
    }

    // =================================================================================
    // 28. NAMED INVITE HANDLER (server-side) - invite player by name
    // =================================================================================
    private static void handleNamedInvite(NamedInvitePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                UUID playerId = player.getUUID();
                String playerName = player.getName().getString();
                String targetName = payload.targetPlayerName();

                // Security: validate target name
                if (targetName == null || targetName.isBlank() || targetName.length() > 16) {
                    player.sendSystemMessage(I18n.translate("devmod.party.invalid_name"));
                    return;
                }

                // Find target player by name
                ServerPlayer targetPlayer = player.server.getPlayerList().getPlayerByName(targetName);
                if (targetPlayer == null) {
                    player.sendSystemMessage(I18n.translate("devmod.party.player_not_found", targetName));
                    return;
                }

                // Can't invite yourself
                if (targetPlayer.getUUID().equals(playerId)) {
                    player.sendSystemMessage(I18n.translate("devmod.party.cannot_invite_self"));
                    return;
                }

                // Get or create party
                PartyData existingParty = PartyManager.INSTANCE.getPlayerParty(playerId);
                PartyData party;
                if (existingParty == null) {
                    // Create new party with quest type from payload
                    party = PartyManager.INSTANCE.createParty(playerId, playerName, payload.getQuestType());
                    if (party == null) {
                        player.sendSystemMessage(I18n.translate("devmod.party.create_failed"));
                        return;
                    }
                    LOGGER.info("[Party] {} created party {} via named invite", playerName, party.getPartyId());
                } else {
                    party = existingParty;
                }

                // Check if player is leader
                if (!party.getLeaderId().equals(playerId)) {
                    player.sendSystemMessage(I18n.translate("devmod.party.not_leader"));
                    return;
                }

                // Send invite (returns PartyInvite or null)
                var invite = PartyManager.INSTANCE.sendInvite(playerId, targetPlayer.getUUID(), targetName);
                if (invite != null) {
                    player.sendSystemMessage(I18n.translate("devmod.party.invite_sent", targetName));
                    // Notify target player
                    sendPartyNotification(targetPlayer,
                        PartyNotificationPayload.inviteReceived(
                            invite.getInviteId(),
                            playerName,
                            party.getQuestType(),
                            invite.getExpiresAt()
                        ));
                    // Sync party state
                    sendPartySyncToPlayer(player);
                } else {
                    player.sendSystemMessage(I18n.translate("devmod.party.invite_failed", targetName));
                }
            }
        });
    }

    // =================================================================================
    // 26. PARTY NOTIFICATION HANDLER (client-side)
    // =================================================================================
    private static void handlePartyNotification(PartyNotificationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Update client cache
            ClientPartyCache.handleNotification(payload);

            // Show popup/toast for certain notification types
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            net.minecraft.client.player.LocalPlayer player = mc.player;
            if (player == null) return;

            switch (payload.notificationType()) {
                case INVITE_RECEIVED -> {
                    // Show invite popup screen
                    mc.execute(() -> InvitePopupScreen.showInvite(payload));
                }
                case YOU_WERE_KICKED, PARTY_DISBANDED -> {
                    // Show action bar message for kicked/disbanded
                    player.displayClientMessage(
                        I18n.translate("devmod.party." + payload.notificationType().name().toLowerCase()),
                        true
                    );
                }
                case MEMBER_JOINED, MEMBER_LEFT -> {
                    // Show action bar message for member changes
                    player.displayClientMessage(
                        I18n.translate("devmod.party.member_" +
                            (payload.notificationType() == PartyNotificationPayload.NotificationType.MEMBER_JOINED ? "joined" : "left"),
                            payload.playerName()),
                        true
                    );
                }
                default -> {
                    // Other notifications are handled by cache/UI updates only
                }
            }
        });
    }

    // =================================================================================
    // 27. PARTY SYNC HANDLER (client-side)
    // =================================================================================
    private static void handlePartySync(PartySyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Update client cache
            ClientPartyCache.update(payload);
        });
    }

    // =================================================================================
    // PARTY HELPER METHODS
    // =================================================================================

    /**
     * Send party sync to a specific player.
     */
    public static void sendPartySyncToPlayer(ServerPlayer player) {
        var partyOpt = PartyManager.INSTANCE.getPartyByPlayer(player.getUUID());
        PartySyncPayload payload;

        if (partyOpt.isPresent()) {
            payload = PartySyncPayload.fromParty(partyOpt.get(),
                uuid -> player.server.getPlayerList().getPlayer(nn(uuid)) != null);
        } else {
            payload = PartySyncPayload.empty();
        }

        sendPacket(player, payload);
    }

    /**
     * Sync party state to all members.
     */
    public static void syncPartyToAllMembers(net.minecraft.server.MinecraftServer server, UUID partyId) {
        Optional<com.frenkvs.devmod.party.PartyData> partyOpt = PartyManager.INSTANCE.getPartyOpt(partyId);
        if (partyOpt.isEmpty()) return;

        var party = partyOpt.get();
        PartySyncPayload payload = PartySyncPayload.fromParty(party,
            uuid -> server.getPlayerList().getPlayer(nn(uuid)) != null);

        for (UUID memberId : party.getMembers()) {
            ServerPlayer member = server.getPlayerList().getPlayer(nn(memberId));
            if (member != null) {
                sendPacket(member, payload);
            }
        }
    }

    /**
     * Send notification to all party members.
     */
    public static void notifyPartyMembers(net.minecraft.server.MinecraftServer server, UUID partyId,
            PartyNotificationPayload notification, UUID excludePlayer) {
        Optional<com.frenkvs.devmod.party.PartyData> partyOpt = PartyManager.INSTANCE.getPartyOpt(partyId);
        if (partyOpt.isEmpty()) return;

        for (UUID memberId : partyOpt.get().getMembers()) {
            if (excludePlayer != null && memberId.equals(excludePlayer)) continue;

            ServerPlayer member = server.getPlayerList().getPlayer(nn(memberId));
            if (member != null) {
                sendPacket(member, notification);
            }
        }
    }

    /**
     * Send party notification to a specific player.
     */
    public static void sendPartyNotification(ServerPlayer player, PartyNotificationPayload notification) {
        sendPacket(player, notification);
    }

    // =================================================================================
    // QUEST SEQUENCE HANDLERS (28-30)
    // =================================================================================

    /**
     * Handle quest sequence status update (server -> client).
     */
    private static void handleQuestSequence(QuestSequencePayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            QuestSequenceOverlay.INSTANCE.update(payload);
        });
    }

    /**
     * Handle arrival confirmation (client -> server).
     */
    private static void handleArrivalConfirm(ArrivalConfirmPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                // Use new method that verifies player position in arena
                var server = player.getServer();
                if (server != null) {
                    QuestStartSequence.INSTANCE.confirmArrival(payload.partyId(), player.getUUID(), server);
                }
            }
        });
    }

    /**
     * Handle cancel sequence request (client -> server).
     */
    private static void handleCancelSequence(CancelSequencePayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                boolean cancelled = QuestStartSequence.INSTANCE.cancelSequence(
                    payload.partyId(),
                    player.getUUID(),
                    "Cancelled by leader"
                );
                if (!cancelled) {
                    player.sendSystemMessage(I18n.translate("devmod.party.cannot_cancel"));
                }
            }
        });
    }

    // =================================================================================
    // ABILITY SYSTEM HANDLERS
    // =================================================================================

    /**
     * Handle ability action (dash, dodge) from client.
     */
    private static void handleAbilityAction(AbilityActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                switch (payload.ability()) {
                    case DASH -> {
                        boolean success = DashAbilitySystem.INSTANCE.tryDash(player);
                        if (!success) {
                            // Could send feedback to client here
                        }
                    }
                    case DODGE -> {
                        var direction = payload.getDodgeDirection();
                        boolean success = DodgeAbilitySystem.INSTANCE.tryDodge(player, direction);
                        if (!success) {
                            // Could send feedback to client here
                        }
                    }
                }
            }
        });
    }

    /**
     * Send stamina sync to a player.
     * Called periodically from StaminaSystem to update client HUD.
     */
    public static void sendStaminaSync(ServerPlayer player, float currentStamina, float maxStamina) {
        StaminaSyncPayload payload = new StaminaSyncPayload(currentStamina, maxStamina);
        sendPacket(player, payload);
    }

    private static void sendEditorConfirm(ServerPlayer player, boolean success, boolean global, String scope, String itemId, String message) {
        try {
            EditorApplyConfirmPayload payload = new EditorApplyConfirmPayload(success, global,
                scope == null ? "<unknown>" : scope,
                itemId == null ? "<unknown>" : itemId,
                message == null ? "" : message);
            sendPacket(player, payload);
        } catch (Exception e) {
            LOGGER.warn("Failed to send editor confirm to {}: {}", player.getName().getString(), e.getMessage());
        }
    }

    private static String getItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "<empty>";
        var key = BuiltInRegistries.ITEM.getKey(nn(stack.getItem()));
        return key == null ? stack.getHoverName().getString() : key.toString();
    }

    // =================================================================================
    // NULL-SAFETY HELPER METHODS
    // =================================================================================

    /**
     * Non-null assertion helper. Returns the value after null check.
     * Accepts potentially null value and guarantees non-null return.
     */
    @Nonnull
    private static <T> T nn(T value) {
        return Objects.requireNonNull(value);
    }

    /**
     * Send packet to player with null-safety.
     */
    private static <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> void sendPacket(
            ServerPlayer player, T payload) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
            Objects.requireNonNull(player, "player"),
            Objects.requireNonNull(payload, "payload")
        );
    }

    // =================================================================================
    // 33. TELEMETRY BATCH HANDLER (server-side)
    // =================================================================================

    /**
     * Handle telemetry batch from multiplayer clients.
     * Delegates to TelemetryPacketHandler for rate limiting and processing.
     */
    private static void handleTelemetryBatch(TelemetryBatchPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                TelemetryPacketHandler.INSTANCE.handleBatch(player, payload);
            }
        });
    }

} // <--- This is the essential closing brace
