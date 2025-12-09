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
import com.frenkvs.devmod.hud.EnduranceQuestOverlay;
import com.frenkvs.devmod.hud.BadgePopupOverlay;
import com.frenkvs.devmod.hud.TokenGainOverlay;
import com.frenkvs.devmod.hud.RecordBannerOverlay;
import com.frenkvs.devmod.hud.ComboDecayOverlay;
import com.frenkvs.devmod.network.ClientConfigFeedback;
import com.frenkvs.devmod.network.MobConfigConfirmPayload;
import com.frenkvs.devmod.network.PacketSecurityService;
import com.frenkvs.devmod.network.PacketSecurityService.ValidationResult;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@EventBusSubscriber(modid = MODID)
public class NetworkHandler {
	private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        // Canale 1: Statistiche Mostri
        event.registrar("1").playToServer(
                UpdateMobStatsPayload.TYPE,
                UpdateMobStatsPayload.STREAM_CODEC,
                NetworkHandler::handleMobData
        );
        // Canale 2: Statistiche Armi
        event.registrar("2").playToServer(
                UpdateWeaponPayload.TYPE,
                UpdateWeaponPayload.STREAM_CODEC,
                NetworkHandler::handleWeaponData
        );
        // Canale 3: Equipaggiamento Mostri
        event.registrar("3").playToServer(
                EquipMobPayload.TYPE,
                EquipMobPayload.STREAM_CODEC,
                NetworkHandler::handleEquipData
        );
        // Canale 4: Modifica Item Completa (durabilità, incantesimi, attributi)
        event.registrar("4").playToServer(
                ModifyItemPayload.TYPE,
                ModifyItemPayload.STREAM_CODEC,
                NetworkHandler::handleItemModification
        );
        // Canale 5: Endurance Quest - Start
        event.registrar("5").playToServer(
                StartQuestPayload.TYPE,
                StartQuestPayload.STREAM_CODEC,
                NetworkHandler::handleStartEnduranceQuest
        );
        // Canale 6: Endurance Quest - Actions (respawn, checkpoint, abandon)
        event.registrar("6").playToServer(
                QuestActionPayload.TYPE,
                QuestActionPayload.STREAM_CODEC,
                NetworkHandler::handleQuestAction
        );
        // Canale 7: Endurance Quest - Sync (server to client)
        event.registrar("7").playToClient(
                QuestSyncPayload.TYPE,
                QuestSyncPayload.STREAM_CODEC,
                NetworkHandler::handleQuestSync
        );
        // Canale 8: Endurance Quest - Shop Purchase
        event.registrar("8").playToServer(
                ShopPurchasePayload.TYPE,
                ShopPurchasePayload.STREAM_CODEC,
                NetworkHandler::handleShopPurchase
        );
        // Canale 9: Endurance Quest - Shop Sync (server to client)
        event.registrar("9").playToClient(
                ShopSyncPayload.TYPE,
                ShopSyncPayload.STREAM_CODEC,
                NetworkHandler::handleShopSync
        );
        // Canale 10: Request Shop Sync (client to server)
        event.registrar("10").playToServer(
                RequestShopSyncPayload.TYPE,
                RequestShopSyncPayload.STREAM_CODEC,
                NetworkHandler::handleRequestShopSync
        );
        // Canale 11: Mob Config Confirmation (server to client)
        event.registrar("11").playToClient(
                MobConfigConfirmPayload.TYPE,
                MobConfigConfirmPayload.STREAM_CODEC,
                NetworkHandler::handleMobConfigConfirm
        );
        // Canale 12: Quest Death Screen (server to client)
        event.registrar("12").playToClient(
                QuestDeathPayload.TYPE,
                QuestDeathPayload.STREAM_CODEC,
                NetworkHandler::handleQuestDeath
        );
        // Canale 13: Perk Choices (server to client)
        event.registrar("13").playToClient(
                PerkChoicesPayload.TYPE,
                PerkChoicesPayload.STREAM_CODEC,
                NetworkHandler::handlePerkChoices
        );
        // Canale 14: Perk Selection (client to server)
        event.registrar("14").playToServer(
                PerkSelectionPayload.TYPE,
                PerkSelectionPayload.STREAM_CODEC,
                NetworkHandler::handlePerkSelection
        );
        // Canale 15: Quest Completion (server to client)
        event.registrar("15").playToClient(
                QuestCompletionPayload.TYPE,
                QuestCompletionPayload.STREAM_CODEC,
                NetworkHandler::handleQuestCompletion
        );
        // Canale 16: Personal Records Sync (server to client)
        event.registrar("16").playToClient(
                PersonalRecordsSyncPayload.TYPE,
                PersonalRecordsSyncPayload.STREAM_CODEC,
                NetworkHandler::handlePersonalRecordsSync
        );
        // Canale 17: Request Personal Records (client to server)
        event.registrar("17").playToServer(
                RequestPersonalRecordsPayload.TYPE,
                RequestPersonalRecordsPayload.STREAM_CODEC,
                NetworkHandler::handleRequestPersonalRecords
        );
        // Canale 18: Boss Alert (server to client)
        event.registrar("18").playToClient(
                BossAlertPayload.TYPE,
                BossAlertPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                    EnduranceQuestOverlay.onBossAlert(payload.alertDurationMs(), payload.bossType()))
        );
        // Canale 19: Badge Unlock (server to client)
        event.registrar("19").playToClient(
                BadgeUnlockPayload.TYPE,
                BadgeUnlockPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                    BadgePopupOverlay.showBadge(payload.badgeName(), payload.rarity()))
        );
        // Canale 20: Token Gain Animation (server to client)
        event.registrar("20").playToClient(
                TokenGainPayload.TYPE,
                TokenGainPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                    TokenGainOverlay.show(payload.amount()))
        );
        // Canale 21: Record Banner (server to client)
        event.registrar("21").playToClient(
                RecordBannerPayload.TYPE,
                RecordBannerPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                    RecordBannerOverlay.showRecord(payload.recordType(), payload.recordValue()))
        );
        // Canale 22: Combo Decay Feedback (server to client)
        event.registrar("22").playToClient(
                ComboDecayPayload.TYPE,
                ComboDecayPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                    ComboDecayOverlay.show(payload.lostCombo(), payload.previousRankOrdinal(), payload.newRankOrdinal()))
        );
    }

    // =================================================================================
    // 1. LOGICA MODIFICA MOSTRI (Vita, Danno, Reach, Globale/Specifico)
    // =================================================================================
    private static void handleMobData(UpdateMobStatsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                // SECURITY: Validate packet and check permissions
                PacketSecurityService security = PacketSecurityService.INSTANCE;
                ValidationResult validation = security.validatePacket(player, "mob_stats", true);
                if (!validation.isSuccess()) {
                    player.sendSystemMessage(I18n.translate("devmod.ui.error").append(": " + validation.getErrorMessage()));
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

                ServerLevel level = player.serverLevel();
                Entity targetEntity = level.getEntity(payload.entityId());

                if (targetEntity instanceof Mob targetMob) {
                    EntityType<?> typeToUpdate = targetMob.getType();

                    // --- SALVATAGGIO CONFIGURAZIONE GLOBALE ---
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

                    // --- APPLICAZIONE AI MOB ESISTENTI ---
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

                        // Se è specifico, salta tutti tranne quello giusto
                        if (!payload.isGlobal() && mob.getId() != payload.entityId()) continue;

                        List<AttributeInstance> attributesToSync = new ArrayList<>();

                        applyAttribute(mob, Attributes.FOLLOW_RANGE, followRange, attributesToSync);
                        applyAttribute(mob, Attributes.ATTACK_DAMAGE, damage, attributesToSync);
                        applyAttribute(mob, Attributes.ARMOR, armor, attributesToSync);
                        applyAttribute(mob, Attributes.LUCK, attackRange, attributesToSync);

                        AttributeInstance healthAttr = mob.getAttribute(Attributes.MAX_HEALTH);
                        if (healthAttr != null) {
                            healthAttr.setBaseValue(maxHealth);
                            attributesToSync.add(healthAttr);
                            // Curiamo il mob se necessario
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
                    String mobTypeName = typeToUpdate.getDescription().getString();
                    MobConfigConfirmPayload confirm = MobConfigConfirmPayload.success(
                        payload.isGlobal(), mobTypeName, count);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, confirm);

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
                    MobConfigConfirmPayload confirm = MobConfigConfirmPayload.failure(I18n.translate("devmod.network.target_not_found").getString());
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, confirm);
                }
            }
        });
    }

    // =================================================================================
    // 2. LOGICA MODIFICA ARMI
    // =================================================================================
    private static void handleWeaponData(UpdateWeaponPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                // SECURITY: Validate packet and check permissions
                PacketSecurityService security = PacketSecurityService.INSTANCE;
                ValidationResult validation = security.validatePacket(player, "weapon_stats", true);
                if (!validation.isSuccess()) {
                    player.sendSystemMessage(I18n.translate("devmod.ui.error").append(": " + validation.getErrorMessage()));
                    return;
                }

                ItemStack stack = player.getMainHandItem();
                if (stack.isEmpty()) return;

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
                        stack.set(DataComponents.CUSTOM_NAME, Component.literal(customName));
                    }
                    player.sendSystemMessage(I18n.translate("devmod.network.weapon_specific_updated"));
                }
            }
        });
    }

    // =================================================================================
    // 3. LOGICA EQUIPAGGIAMENTO
    // =================================================================================
    private static void handleEquipData(EquipMobPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                // SECURITY: Validate packet and check permissions
                PacketSecurityService security = PacketSecurityService.INSTANCE;
                ValidationResult validation = security.validatePacket(player, "equip_mob", true);
                if (!validation.isSuccess()) {
                    player.sendSystemMessage(I18n.translate("devmod.ui.error").append(": " + validation.getErrorMessage()));
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
    // 4. LOGICA MODIFICA ITEM (Durabilità, Incantesimi, Attributi)
    // =================================================================================
    private static void handleItemModification(ModifyItemPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                // SECURITY: Validate packet and check permissions
                PacketSecurityService security = PacketSecurityService.INSTANCE;
                ValidationResult validation = security.validatePacket(player, "modify_item", true);
                if (!validation.isSuccess()) {
                    player.sendSystemMessage(I18n.translate("devmod.ui.error").append(": " + validation.getErrorMessage()));
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
                    stack.set(DataComponents.UNBREAKABLE, new net.minecraft.world.item.component.Unbreakable(true));
                } else {
                    stack.remove(DataComponents.UNBREAKABLE);
                }

                // Apply repair cost
                if (payload.repairCost() >= 0) {
                    stack.set(DataComponents.REPAIR_COST, payload.repairCost());
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
            stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
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
                ResourceLocation enchantLoc = ResourceLocation.parse(enchantId);
                var registry = player.server.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
                var enchantHolder = registry.getHolder(enchantLoc);

                if (enchantHolder.isPresent()) {
                    Holder<Enchantment> holder = enchantHolder.get();
                    if (level <= 0) {
                        // Remove enchantment
                        enchantments.removeIf(h -> h.equals(holder));
                    } else {
                        // Add or update enchantment
                        enchantments.set(holder, level);
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

        stack.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());

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
        ItemAttributeModifiers existing = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
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
                ResourceLocation attrLoc = ResourceLocation.parse(attrId);
                var registry = player.server.registryAccess().registryOrThrow(Registries.ATTRIBUTE);
                var attrHolder = registry.getHolder(attrLoc);

                if (attrHolder.isPresent()) {
                    Holder<Attribute> holder = attrHolder.get();
                    AttributeModifier.Operation op = switch (operation) {
                        case 1 -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
                        case 2 -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
                        default -> AttributeModifier.Operation.ADD_VALUE;
                    };

                    // Remove existing modifiers for this attribute
                    entries.removeIf(e -> e.attribute().equals(holder));

                    // Add new modifier
                    ResourceLocation modifierId = ResourceLocation.fromNamespaceAndPath("devmod", "custom_" + attrLoc.getPath());
                    AttributeModifier modifier = new AttributeModifier(modifierId, value, op);
                    entries.add(new ItemAttributeModifiers.Entry(holder, modifier, EquipmentSlotGroup.MAINHAND));
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

        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(entries, existing.showInTooltip()));

        // Send feedback to player about failed attributes
        if (failCount > 0) {
            String failedList = String.join(", ", failedAttrs);
            player.sendSystemMessage(I18n.translate("devmod.network.attr_failed", failCount, failedList));
        }

        return failCount;
    }

    // =================================================================================
    // METODI HELPER (Devono stare DENTRO la classe, prima dell'ultima parentesi graffa)
    // =================================================================================

    /**
     * Secure equipSlot that validates item IDs before equipping.
     * Prevents injection attacks through malicious item ID strings.
     */
    private static void equipSlotSecure(Mob mob, EquipmentSlot slot, String itemName, PacketSecurityService security) {
        if (itemName == null || itemName.trim().isEmpty()) return;

        // Handle special clear commands
        if (itemName.equalsIgnoreCase("air") || itemName.equalsIgnoreCase("clear")) {
            mob.setItemSlot(slot, ItemStack.EMPTY);
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
                mob.setItemSlot(slot, stack);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to equip validated item '{}' to slot {} for mob {}: {}",
                validatedItemId, slot, mob.getType().getDescription().getString(), e.getMessage());
        }
    }

    // ECCO IL METODO CHE TI DAVA ERRORE: ORA È DENTRO LA CLASSE
    private static void applyAttribute(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, double value, List<AttributeInstance> syncList) {
        AttributeInstance instance = mob.getAttribute(attr);
        if (instance != null) {
            instance.setBaseValue(value);
            syncList.add(instance);
        }
    }

    // =================================================================================
    // 5. LOGICA ENDURANCE QUEST
    // =================================================================================
    private static void handleStartEnduranceQuest(StartQuestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                // SECURITY: Validate packet and check permissions
                PacketSecurityService security = PacketSecurityService.INSTANCE;
                ValidationResult validation = security.validatePacket(player, "endurance_quest", true);
                if (!validation.isSuccess()) {
                    player.sendSystemMessage(I18n.translate("devmod.ui.error").append(": " + validation.getErrorMessage()));
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
                        player.sendSystemMessage(I18n.translate("devmod.ui.error").append(": " + result.message()));
                    }

                } catch (Exception e) {
                    player.sendSystemMessage(I18n.translate("devmod.network.failed_start_quest", e.getMessage()));
                    LOGGER.error("[EnduranceQuest] Failed to start quest", e);
                }
            }
        });
    }

    // =================================================================================
    // 6. LOGICA QUEST ACTIONS (respawn, checkpoint, abandon)
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
                    player.sendSystemMessage(I18n.translate("devmod.ui.error").append(": " + result.message()));
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
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
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
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
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
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
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
                    player.sendSystemMessage(I18n.translate("devmod.network.perk_skipped")
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
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

        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
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

        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, syncPayload);
    }

    // =================================================================================
    // BOSS ALERT
    // =================================================================================

    /**
     * Send boss alert to a player. Called from BossWaveSystem 3s before boss spawn.
     */
    public static void sendBossAlert(ServerPlayer player, long durationMs, String bossType) {
        BossAlertPayload payload = new BossAlertPayload(durationMs, bossType);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
    }

    // =================================================================================
    // BADGE UNLOCK
    // =================================================================================

    /**
     * Send badge unlock notification to a player.
     */
    public static void sendBadgeUnlock(ServerPlayer player, String badgeName, String rarity) {
        BadgeUnlockPayload payload = new BadgeUnlockPayload(badgeName, rarity);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
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
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
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
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
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
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        }
    }

} // <--- Questa è la parentesi finale fondamentale
