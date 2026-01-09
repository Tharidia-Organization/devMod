package com.devmod.network.handlers;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import com.google.common.base.Splitter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.DevMod;
import com.devmod.config.Config;
import com.devmod.config.MobConfigManager;
import com.devmod.config.handler.impl.ArmorConfigHandler;
import com.devmod.config.handler.impl.FoodConfigHandler;
import com.devmod.config.handler.impl.FuelConfigHandler;
import com.devmod.config.handler.impl.UsableConfigHandler;
import com.devmod.config.handler.impl.WeaponConfigHandler;
import com.devmod.network.ArmorStatsPayload;
import com.devmod.network.ChannelId;
import com.devmod.network.EquipMobPayload;
import com.devmod.network.FoodStatsPayload;
import com.devmod.network.FuelStatsPayload;
import com.devmod.network.MobConfigConfirmPayload;
import com.devmod.network.ModifyItemPayload;
import com.devmod.network.PacketValidator.ValidationResult;
import com.devmod.network.PacketValidator;
import com.devmod.network.PayloadValidation.PayloadLimits;
import com.devmod.network.RangedWeaponStatsPayload;
import com.devmod.network.UpdateArmorPayload;
import com.devmod.network.UpdateMobStatsPayload;
import com.devmod.network.UpdateWeaponPayload;
import com.devmod.network.UsableStatsPayload;
import com.devmod.network.WeaponStatsPayload;
import com.devmod.stats.ArmorStats;
import com.devmod.stats.FoodStats;
import com.devmod.stats.FuelStats;
import com.devmod.stats.UsableStats;
import com.devmod.stats.WeaponStats;
import com.devmod.util.I18n;

import static com.devmod.network.PayloadValidation.validated;

/**
 * P2: Domain-specific network handler for mob and item editor payloads.
 * Handles mob stats, weapon/armor/ranged/usable/food/fuel modifications.
 */
public final class MobItemNetworkHandler extends NetworkHandlerBase implements PayloadRegistrar {

    public static final MobItemNetworkHandler INSTANCE = new MobItemNetworkHandler();

    private static final Splitter COLON_SPLITTER = Splitter.on(':');

    private MobItemNetworkHandler() {}

    // =================================================================================
    // PAYLOAD REGISTRATION (P2: Domain-specific registration)
    // =================================================================================

    @Override
    public String getDomainName() {
        return "mob_item";
    }

    @Override
    public void registerPayloads(RegisterPayloadHandlersEvent event) {
        // MOB_STATS: Client -> Server mob stat modifications
        event.registrar(ChannelId.MOB_STATS.asString()).playToServer(
            nn(UpdateMobStatsPayload.TYPE),
            nn(UpdateMobStatsPayload.STREAM_CODEC),
            validated(MobItemNetworkHandler::handleMobData, PayloadLimits.EDITOR)
        );

        // WEAPON_LEGACY: Client -> Server legacy weapon updates
        event.registrar(ChannelId.WEAPON_LEGACY.asString()).playToServer(
            nn(UpdateWeaponPayload.TYPE),
            nn(UpdateWeaponPayload.STREAM_CODEC),
            validated(MobItemNetworkHandler::handleWeaponData, PayloadLimits.EDITOR)
        );

        // EQUIP_MOB: Client -> Server mob equipment changes
        event.registrar(ChannelId.EQUIP_MOB.asString()).playToServer(
            nn(EquipMobPayload.TYPE),
            nn(EquipMobPayload.STREAM_CODEC),
            validated(MobItemNetworkHandler::handleEquipData, PayloadLimits.EDITOR)
        );

        // MODIFY_ITEM: Client -> Server item modifications (enchants, attributes)
        event.registrar(ChannelId.MODIFY_ITEM.asString()).playToServer(
            nn(ModifyItemPayload.TYPE),
            nn(ModifyItemPayload.STREAM_CODEC),
            validated(MobItemNetworkHandler::handleItemModification, PayloadLimits.EDITOR)
        );

        // UPDATE_ARMOR: Client -> Server armor stat updates
        event.registrar(ChannelId.UPDATE_ARMOR.asString()).playToServer(
            nn(UpdateArmorPayload.TYPE),
            nn(UpdateArmorPayload.STREAM_CODEC),
            validated(MobItemNetworkHandler::handleArmorData, PayloadLimits.EDITOR)
        );

        // RANGED_WEAPON_STATS: Client -> Server ranged weapon modifications
        event.registrar(ChannelId.RANGED_WEAPON_STATS.asString()).playToServer(
            nn(RangedWeaponStatsPayload.TYPE),
            nn(RangedWeaponStatsPayload.STREAM_CODEC),
            validated(MobItemNetworkHandler::handleRangedWeaponData, PayloadLimits.EDITOR)
        );

        // ARMOR_STATS: Client -> Server armor stats V2
        event.registrar(ChannelId.ARMOR_STATS.asString()).playToServer(
            nn(ArmorStatsPayload.TYPE),
            nn(ArmorStatsPayload.STREAM_CODEC),
            validated(MobItemNetworkHandler::handleArmorStatsDataV2, PayloadLimits.EDITOR)
        );

        // USABLE_STATS: Client -> Server usable item stats
        event.registrar(ChannelId.USABLE_STATS.asString()).playToServer(
            nn(UsableStatsPayload.TYPE),
            nn(UsableStatsPayload.STREAM_CODEC),
            validated(MobItemNetworkHandler::handleUsableStatsData, PayloadLimits.EDITOR)
        );

        // FOOD_STATS: Client -> Server food item stats
        event.registrar(ChannelId.FOOD_STATS.asString()).playToServer(
            nn(FoodStatsPayload.TYPE),
            nn(FoodStatsPayload.STREAM_CODEC),
            validated(MobItemNetworkHandler::handleFoodStatsData, PayloadLimits.EDITOR)
        );

        // FUEL_STATS: Client -> Server fuel item stats
        event.registrar(ChannelId.FUEL_STATS.asString()).playToServer(
            nn(FuelStatsPayload.TYPE),
            nn(FuelStatsPayload.STREAM_CODEC),
            validated(MobItemNetworkHandler::handleFuelStatsData, PayloadLimits.EDITOR)
        );

        // WEAPON_STATS_V2: Client -> Server weapon stats V2
        event.registrar(ChannelId.WEAPON_STATS_V2.asString()).playToServer(
            nn(WeaponStatsPayload.TYPE),
            nn(WeaponStatsPayload.STREAM_CODEC),
            validated(MobItemNetworkHandler::handleWeaponStatsDataV2, PayloadLimits.EDITOR)
        );
    }

    private static void enqueueWork(IPayloadContext context, Runnable work) {
        observeFuture(context.enqueueWork(Objects.requireNonNull(work, "work")), "mob item update");
    }

    // =================================================================================
    // MOB MODIFICATION LOGIC
    // =================================================================================
    public static void handleMobData(UpdateMobStatsPayload payload, IPayloadContext context) {
        enqueueWork(context, () -> {
            if (context.player() instanceof ServerPlayer player) {
                PacketValidator security = security();
                ValidationResult validation = security.validatePacket(player, "mob_stats", true);
                if (!validation.isSuccess()) {
                    player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", resolveValidationError(validation)));
                    return;
                }

                if (!security.validateEntityId(payload.entityId())) {
                    player.sendSystemMessage(I18n.translate("devmod.network.invalid_entity"));
                    return;
                }

                double followRange = security.validateFollowRange(payload.followRange());
                double damage = security.validateDamage(payload.damage());
                double maxHealth = security.validateHealth(payload.maxHealth());
                double armor = security.validateArmor(payload.armor());
                double attackRange = security.validateFollowRange(payload.attackRange());
                double speed = Math.max(0, Math.min(payload.speed(), 2.0));
                double knockbackResist = Math.max(0, Math.min(payload.knockbackResist(), 1.0));

                ServerLevel level = player.serverLevel();
                Entity targetEntity = level.getEntity(payload.entityId());

                if (targetEntity instanceof Mob targetMob) {
                    EntityType<?> typeToUpdate = targetMob.getType();

                    if (payload.isGlobal()) {
                        MobConfigManager.setGlobalStats(typeToUpdate, followRange, damage, maxHealth, armor);
                        player.sendSystemMessage(I18n.translate("devmod.network.mob_global_saved", typeToUpdate.toShortString()));
                    }

                    int count = 0;
                    BlockPos pos = player.blockPosition();
                    int searchRadius = Config.MOB_SEARCH_RADIUS.get();
                    net.minecraft.world.phys.AABB searchBox = new net.minecraft.world.phys.AABB(
                        pos.getX() - searchRadius, pos.getY() - searchRadius, pos.getZ() - searchRadius,
                        pos.getX() + searchRadius, pos.getY() + searchRadius, pos.getZ() + searchRadius
                    );

                    for (Mob mob : level.getEntitiesOfClass(Mob.class, searchBox,
                        m -> m.getType() == typeToUpdate)) {

                        if (!payload.isGlobal() && mob.getId() != payload.entityId()) continue;

                        List<AttributeInstance> attributesToSync = new ArrayList<>();
                        applyAttribute(mob, Attributes.FOLLOW_RANGE, followRange, attributesToSync);
                        applyAttribute(mob, Attributes.ATTACK_DAMAGE, damage, attributesToSync);
                        applyAttribute(mob, Attributes.ARMOR, armor, attributesToSync);
                        applyAttribute(mob, Attributes.ENTITY_INTERACTION_RANGE, attackRange, attributesToSync);
                        applyAttribute(mob, Attributes.MOVEMENT_SPEED, speed, attributesToSync);
                        applyAttribute(mob, Attributes.KNOCKBACK_RESISTANCE, knockbackResist, attributesToSync);

                        AttributeInstance healthAttr = mob.getAttribute(nn(Attributes.MAX_HEALTH));
                        if (healthAttr != null) {
                            healthAttr.setBaseValue(maxHealth);
                            attributesToSync.add(healthAttr);
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

                    String mobTypeName = nn(typeToUpdate.getDescription().getString());
                    MobConfigConfirmPayload confirm = MobConfigConfirmPayload.success(payload.isGlobal(), mobTypeName, count);
                    sendPacket(player, confirm);

                    LOGGER.info("[MobConfig] Player {} {} config for {} ({} mobs affected)",
                        player.getName().getString(),
                        payload.isGlobal() ? "saved GLOBAL" : "applied SPECIFIC",
                        mobTypeName, count);

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
                    MobConfigConfirmPayload confirm = MobConfigConfirmPayload.failure(
                        nn(I18n.translate("devmod.network.target_not_found").getString()));
                    sendPacket(player, confirm);
                }
            }
        });
    }

    // =================================================================================
    // WEAPON MODIFICATION LOGIC
    // =================================================================================
    public static void handleWeaponData(UpdateWeaponPayload payload, IPayloadContext context) {
        enqueueWork(context, () -> {
            if (context.player() instanceof ServerPlayer player) {
                PacketValidator security = security();
                ValidationResult validation = security.validatePacket(player, "weapon_stats", true);
                if (!validation.isSuccess()) {
                    player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", resolveValidationError(validation)));
                    sendEditorConfirm(player, false, payload.isGlobal(), "weapon", "<unknown>", resolveValidationError(validation));
                    return;
                }

                ItemStack stack = player.getMainHandItem();
                if (stack.isEmpty()) {
                    sendEditorConfirm(player, false, payload.isGlobal(), "weapon", "<empty>", "No item in hand");
                    return;
                }

                WeaponStats stats = new WeaponStats();
                stats.setHeadMult((float) security.validateMultiplier(payload.head()));
                stats.setBodyMult((float) security.validateMultiplier(payload.body()));
                stats.setLegsMult((float) security.validateMultiplier(payload.legs()));
                stats.setArmorPenetration((float) security.validatePenetration(payload.pen()));
                stats.setBaseDamageBonus((float) security.validateDamage(payload.bonus()));

                if (payload.isGlobal()) {
                    WeaponConfigHandler.INSTANCE.setGlobalStats(stack.getItem(), stats);
                    String itemName = stack.getHoverName().getString();
                    player.sendSystemMessage(I18n.translate("devmod.network.weapon_global_saved", itemName));
                    sendEditorConfirm(player, true, true, "weapon", getItemId(stack), "Weapon global saved");

                    String adminName = player.getName().getString();
                    var broadcastMsg = I18n.translate("devmod.network.weapon_global_broadcast", adminName, itemName);
                    for (ServerPlayer otherPlayer : player.server.getPlayerList().getPlayers()) {
                        if (!otherPlayer.getUUID().equals(player.getUUID())) {
                            otherPlayer.sendSystemMessage(broadcastMsg);
                        }
                    }
                } else {
                    WeaponConfigHandler.INSTANCE.setSpecificStats(stack, stats);
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

    public static void handleWeaponStatsData(com.devmod.network.WeaponStatsPayload payload, IPayloadContext context) {
        enqueueWork(context, () -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            PacketValidator security = security();
            ValidationResult validation = security.validatePacket(player, "weapon_stats_nbt", true);
            if (!validation.isSuccess()) {
                player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", resolveValidationError(validation)));
                sendEditorConfirm(player, false, payload.isGlobal(), "weapon", "<unknown>", resolveValidationError(validation));
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

            CompoundTag tag = nn(payload.statsTag());
            if (tag.getBoolean("reset")) {
                if (payload.isGlobal()) {
                    WeaponConfigHandler.INSTANCE.clearGlobalStats(stack.getItem());
                    sendEditorConfirm(player, true, true, "weapon", getItemId(stack), "Weapon global reset");
                } else {
                    WeaponConfigHandler.clearItemSpecificStats(stack);
                    sendEditorConfirm(player, true, false, "weapon", getItemId(stack), "Weapon reset to defaults");
                }
                return;
            }
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

            WeaponStats stats = WeaponConfigHandler.clampStats(WeaponStats.fromTag(toLoad));
            sanitizeToolRules(stats, security);

            if (payload.isGlobal()) {
                WeaponConfigHandler.INSTANCE.setGlobalStats(stack.getItem(), stats);
                sendEditorConfirm(player, true, true, "weapon", getItemId(stack), "Weapon global saved");
            } else {
                CompoundTag variant = new CompoundTag();
                if (toLoad.contains("Mace")) variant.put("Mace", nn(toLoad.getCompound("Mace")));
                if (toLoad.contains("Trident")) variant.put("Trident", nn(toLoad.getCompound("Trident")));
                WeaponConfigHandler.INSTANCE.setSpecificStats(stack, stats, variant);
                sendEditorConfirm(player, true, false, "weapon", getItemId(stack), "Weapon specific updated");
            }
        });
    }

    public static void handleWeaponStatsDataV2(com.devmod.network.WeaponStatsPayload payload, IPayloadContext context) {
        enqueueWork(context, () -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            PacketValidator security = security();
            ValidationResult validation = security.validatePacket(player, "weapon_stats_v2", true);
            if (!validation.isSuccess()) {
                player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", resolveValidationError(validation)));
                sendEditorConfirm(player, false, payload.isGlobal(), "weapon", "<unknown>", resolveValidationError(validation));
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

            if (tag.getBoolean("reset")) {
                if (payload.isGlobal()) {
                    WeaponConfigHandler.INSTANCE.clearGlobalStats(stack.getItem());
                    sendEditorConfirm(player, true, true, "weapon", getItemId(stack), "Weapon global reset");
                } else {
                    WeaponConfigHandler.clearItemSpecificStats(stack);
                    sendEditorConfirm(player, true, false, "weapon", getItemId(stack), "Weapon reset to defaults");
                }
                return;
            }

            CompoundTag toLoad = tag.contains("weapon_stats_component") ? tag.getCompound("weapon_stats_component")
                : (tag.contains("WeaponModStats") ? tag.getCompound("WeaponModStats") : tag);

            WeaponStats stats;
            if (tag.contains("delta")) {
                WeaponStats base = WeaponConfigHandler.INSTANCE.getStats(stack).copy();
                applyDelta(base, tag.getCompound("delta"));
                toLoad = new CompoundTag();
                base.save(toLoad);
                stats = WeaponConfigHandler.clampStats(base);
            } else {
                stats = WeaponConfigHandler.clampStats(WeaponStats.fromTag(toLoad));
            }
            sanitizeToolRules(stats, security);

            DevMod.LOGGER.info("[Server][WeaponApply] player={} item={} global={} dmg={} spd={} reach={} bonus={} pen={} shred={}",
                player.getGameProfile().getName(), stack.getItem(), payload.isGlobal(),
                stats.getAttackDamage(), stats.getAttackSpeed(), stats.getAttackReach(), stats.getBaseDamageBonus(),
                stats.getArmorPenetration(), stats.getArmorShred());

            if (payload.isGlobal()) {
                WeaponConfigHandler.INSTANCE.setGlobalStats(stack.getItem(), stats);
                sendEditorConfirm(player, true, true, "weapon", getItemId(stack), "Weapon global saved");
            } else {
                CompoundTag variant = new CompoundTag();
                if (toLoad.contains("Mace")) variant.put("Mace", nn(toLoad.getCompound("Mace")));
                if (toLoad.contains("Trident")) variant.put("Trident", nn(toLoad.getCompound("Trident")));
                WeaponConfigHandler.INSTANCE.setSpecificStats(stack, stats, variant);
                sendEditorConfirm(player, true, false, "weapon", getItemId(stack), "Weapon specific updated");
            }
        });
    }

    // =================================================================================
    // RANGED WEAPON LOGIC
    // =================================================================================
    public static void handleRangedWeaponData(RangedWeaponStatsPayload payload, IPayloadContext context) {
        enqueueWork(context, () -> {
            if (context.player() instanceof ServerPlayer player) {
                PacketValidator security = security();
                ValidationResult validation = security.validatePacket(player, "ranged_weapon", true);
                if (!validation.isSuccess()) {
                    player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", resolveValidationError(validation)));
                    sendEditorConfirm(player, false, payload.isGlobal(), "ranged", "<unknown>", resolveValidationError(validation));
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

                if (!Objects.equals(stack.getItem(), payload.item().getItem())) {
                    sendEditorConfirm(player, false, false, "ranged", getItemId(stack), "Item mismatch (held vs payload)");
                    return;
                }

                CompoundTag root = payload.statsTag().copy();
                if (root.getBoolean("reset")) {
                    com.devmod.client.ui.editor.RangedWeaponModule.clearStats(stack);
                    player.sendSystemMessage(I18n.translate("devmod.network.weapon_specific_updated"));
                    sendEditorConfirm(player, true, false, "ranged", getItemId(stack), "Ranged weapon reset to defaults");
                    return;
                }
                CompoundTag data = stack.getOrDefault(nn(DataComponents.CUSTOM_DATA), nn(CustomData.EMPTY)).copyTag();
                if (root.contains("RangedStats")) {
                    CompoundTag ranged = nn(root.getCompound("RangedStats"));
                    clampRanged(ranged);
                    data.put("RangedStats", ranged);
                } else {
                    clampRanged(root);
                    data.put("RangedStats", nn(root));
                }
                stack.set(nn(DataComponents.CUSTOM_DATA), CustomData.of(data));
                player.sendSystemMessage(I18n.translate("devmod.network.weapon_specific_updated"));
                sendEditorConfirm(player, true, false, "ranged", getItemId(stack), "Ranged weapon updated");
                if (root.contains("ammoFilter")) {
                    String filter = root.getString("ammoFilter");
                    player.sendSystemMessage(I18n.translate("devmod.network.ranged_ammo_filter", filter));
                }
            }
        });
    }

    // =================================================================================
    // ARMOR MODIFICATION LOGIC
    // =================================================================================
    public static void handleArmorData(UpdateArmorPayload payload, IPayloadContext context) {
        enqueueWork(context, () -> {
            if (context.player() instanceof ServerPlayer player) {
                PacketValidator security = security();
                ValidationResult validation = security.validatePacket(player, "armor_stats", true);
                if (!validation.isSuccess()) {
                    player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", resolveValidationError(validation)));
                    sendEditorConfirm(player, false, payload.isGlobal(), "armor", payload.itemName(), resolveValidationError(validation));
                    return;
                }

                ArmorStats stats = payload.toArmorStats();
                stats.setPhysicalReduction(Math.max(0f, Math.min(1f, stats.getPhysicalReduction())));
                stats.setFireReduction(Math.max(0f, Math.min(1f, stats.getFireReduction())));
                stats.setMagicReduction(Math.max(0f, Math.min(1f, stats.getMagicReduction())));
                stats.setExplosionReduction(Math.max(0f, Math.min(1f, stats.getExplosionReduction())));
                stats.setProjectileReduction(Math.max(0f, Math.min(1f, stats.getProjectileReduction())));
                stats.setArmorBonus(Math.max(-20f, Math.min(30f, stats.getArmorBonus())));
                stats.setToughnessBonus(Math.max(-10f, Math.min(20f, stats.getToughnessBonus())));
                stats.setKnockbackResistance(Math.max(0f, Math.min(1f, stats.getKnockbackResistance())));
                stats.setThornsPercent(Math.max(0f, Math.min(0.5f, stats.getThornsPercent())));
                stats.setShieldBlockStrength(Math.max(0f, Math.min(1f, stats.getShieldBlockStrength())));
                stats.setShieldRecoverySpeed(Math.max(0f, Math.min(2f, stats.getShieldRecoverySpeed())));

                if (payload.isGlobal()) {
                    ResourceLocation itemLoc = ResourceLocation.tryParse(nn(payload.itemName()));
                    if (itemLoc != null && BuiltInRegistries.ITEM.containsKey(itemLoc)) {
                        Item item = BuiltInRegistries.ITEM.get(itemLoc);
                        ArmorConfigHandler.INSTANCE.setGlobalStats(item, stats);
                        String itemName = nn(item.getDescription().getString());
                        player.sendSystemMessage(I18n.translate("devmod.network.armor_global_saved", itemName));
                        sendEditorConfirm(player, true, true, "armor", itemName, "Armor global saved");

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
                    EquipmentSlot slot = switch (payload.slot()) {
                        case 0 -> EquipmentSlot.HEAD;
                        case 1 -> EquipmentSlot.CHEST;
                        case 2 -> EquipmentSlot.LEGS;
                        case 3 -> EquipmentSlot.FEET;
                        default -> null;
                    };

                    if (slot != null) {
                        ItemStack armor = player.getItemBySlot(nn(slot));
                        if (!armor.isEmpty() && ArmorConfigHandler.isArmor(armor)) {
                            ArmorConfigHandler.INSTANCE.setSpecificStats(armor, stats);
                            player.sendSystemMessage(I18n.translate("devmod.network.armor_specific_updated"));
                            sendEditorConfirm(player, true, false, "armor", getItemId(armor), "Armor specific updated");
                        } else {
                            player.sendSystemMessage(I18n.translate("devmod.network.no_armor_in_slot"));
                            sendEditorConfirm(player, false, false, "armor", getItemId(armor), "No armor in slot");
                        }
                    } else {
                        ItemStack held = player.getMainHandItem();
                        if (!held.isEmpty() && ArmorConfigHandler.isArmor(held)) {
                            ArmorConfigHandler.INSTANCE.setSpecificStats(held, stats);
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

    public static void handleArmorStatsDataV2(com.devmod.network.ArmorStatsPayload payload, IPayloadContext context) {
        enqueueWork(context, () -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            PacketValidator security = security();
            ValidationResult validation = security.validatePacket(player, "armor_stats_v2", true);
            if (!validation.isSuccess()) {
                player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", resolveValidationError(validation)));
                sendEditorConfirm(player, false, payload.isGlobal(), "armor", "<unknown>", resolveValidationError(validation));
                return;
            }

            ItemStack payloadStack = payload.item();
            if (payloadStack == null || payloadStack.isEmpty()) {
                sendEditorConfirm(player, false, payload.isGlobal(), "armor", "<empty>", "Missing item");
                return;
            }

            CompoundTag tag = payload.statsTag();
            if (tag.isEmpty()) {
                sendEditorConfirm(player, false, payload.isGlobal(), "armor", getItemId(payloadStack), "Missing stats");
                return;
            }

            if (payload.isGlobal()) {
                if (tag.getBoolean("reset")) {
                    ArmorConfigHandler.INSTANCE.clearGlobalStats(payloadStack.getItem());
                    sendEditorConfirm(player, true, true, "armor", getItemId(payloadStack), "Armor global reset");
                    return;
                }
                CompoundTag toLoad = tag.contains("armor_stats_component") ? tag.getCompound("armor_stats_component")
                    : (tag.contains("ArmorModStats") ? tag.getCompound("ArmorModStats") : tag);
                ArmorStats stats = ArmorStats.fromTag(toLoad);

                stats.setPhysicalReduction((float) security.validateArmorReduction(stats.getPhysicalReduction()));
                stats.setFireReduction((float) security.validateArmorReduction(stats.getFireReduction()));
                stats.setMagicReduction((float) security.validateArmorReduction(stats.getMagicReduction()));
                stats.setExplosionReduction((float) security.validateArmorReduction(stats.getExplosionReduction()));
                stats.setProjectileReduction((float) security.validateArmorReduction(stats.getProjectileReduction()));
                stats.setArmorBonus((float) security.validateArmorBonus(stats.getArmorBonus()));
                stats.setToughnessBonus((float) security.validateToughnessBonus(stats.getToughnessBonus()));
                stats.setKnockbackResistance((float) security.validateKnockbackResistance(stats.getKnockbackResistance()));
                stats.setThornsPercent((float) security.validateThornsPercent(stats.getThornsPercent()));
                stats.setShieldBlockStrength((float) security.validateShieldBlock(stats.getShieldBlockStrength()));
                stats.setShieldRecoverySpeed((float) security.validateShieldRecovery(stats.getShieldRecoverySpeed()));

                Item item = payloadStack.getItem();
                ArmorConfigHandler.INSTANCE.setGlobalStats(item, stats);
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

            if (target.isEmpty() || !ArmorConfigHandler.isArmor(target)) {
                sendEditorConfirm(player, false, false, "armor", getItemId(payloadStack), "No matching armor piece");
                return;
            }

            if (tag.getBoolean("reset")) {
                ArmorConfigHandler.clearItemSpecificStats(target);
                sendEditorConfirm(player, true, false, "armor", getItemId(target), "Armor reset to defaults");
                return;
            }

            CompoundTag toLoad = tag.contains("armor_stats_component") ? tag.getCompound("armor_stats_component")
                : (tag.contains("ArmorModStats") ? tag.getCompound("ArmorModStats") : tag);
            ArmorStats stats = ArmorStats.fromTag(toLoad);

            stats.setPhysicalReduction((float) security.validateArmorReduction(stats.getPhysicalReduction()));
            stats.setFireReduction((float) security.validateArmorReduction(stats.getFireReduction()));
            stats.setMagicReduction((float) security.validateArmorReduction(stats.getMagicReduction()));
            stats.setExplosionReduction((float) security.validateArmorReduction(stats.getExplosionReduction()));
            stats.setProjectileReduction((float) security.validateArmorReduction(stats.getProjectileReduction()));
            stats.setArmorBonus((float) security.validateArmorBonus(stats.getArmorBonus()));
            stats.setToughnessBonus((float) security.validateToughnessBonus(stats.getToughnessBonus()));
            stats.setKnockbackResistance((float) security.validateKnockbackResistance(stats.getKnockbackResistance()));
            stats.setThornsPercent((float) security.validateThornsPercent(stats.getThornsPercent()));
            stats.setShieldBlockStrength((float) security.validateShieldBlock(stats.getShieldBlockStrength()));
            stats.setShieldRecoverySpeed((float) security.validateShieldRecovery(stats.getShieldRecoverySpeed()));

            ArmorConfigHandler.INSTANCE.setSpecificStats(target, stats);
            sendEditorConfirm(player, true, false, "armor", getItemId(target), "Armor specific updated");
        });
    }

    // =================================================================================
    // USABLE ITEMS MODIFICATION LOGIC
    // =================================================================================
    public static void handleUsableStatsData(com.devmod.network.UsableStatsPayload payload, IPayloadContext context) {
        enqueueWork(context, () -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            PacketValidator security = security();
            ValidationResult validation = security.validatePacket(player, "usable_stats", true);
            if (!validation.isSuccess()) {
                player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", resolveValidationError(validation)));
                sendEditorConfirm(player, false, payload.isGlobal(), "usable", "<unknown>", resolveValidationError(validation));
                return;
            }

            ItemStack stack = player.getMainHandItem();
            if (stack.isEmpty()) {
                sendEditorConfirm(player, false, payload.isGlobal(), "usable", "<empty>", "No item in hand");
                return;
            }

            if (!Objects.equals(stack.getItem(), payload.item().getItem())) {
                sendEditorConfirm(player, false, payload.isGlobal(), "usable", getItemId(stack), "Item mismatch (held vs payload)");
                return;
            }

            CompoundTag tag = payload.statsTag();
            if (tag == null || tag.isEmpty()) {
                sendEditorConfirm(player, false, payload.isGlobal(), "usable", getItemId(stack), "Missing stats");
                return;
            }
            if (tag.getBoolean("reset")) {
                if (payload.isGlobal()) {
                    UsableConfigHandler.INSTANCE.clearGlobalStats(stack.getItem());
                    sendEditorConfirm(player, true, true, "usable", getItemId(stack), "Usable global reset");
                } else {
                    UsableConfigHandler.clearItemSpecificStats(stack);
                    sendEditorConfirm(player, true, false, "usable", getItemId(stack), "Usable reset to defaults");
                }
                return;
            }

            CompoundTag toLoad = tag.contains("usable_stats_component") ? tag.getCompound("usable_stats_component")
                : (tag.contains("UsableModStats") ? tag.getCompound("UsableModStats") : tag);

            UsableStats stats = UsableStats.fromTag(toLoad);

            // Clamp values for security
            stats.setUseDuration(Math.max(0, Math.min(200, stats.getUseDuration())));
            stats.setCooldownDuration(Math.max(0, Math.min(600, stats.getCooldownDuration())));
            stats.setProjectileSpeed(Math.max(0.5f, Math.min(5.0f, stats.getProjectileSpeed())));
            stats.setProjectileGravity(Math.max(0.0f, Math.min(0.1f, stats.getProjectileGravity())));
            stats.setProjectileInaccuracy(Math.max(0.0f, Math.min(5.0f, stats.getProjectileInaccuracy())));
            stats.setProjectileDamage(Math.max(0, Math.min(20, stats.getProjectileDamage())));

            if (payload.isGlobal()) {
                Item item = stack.getItem();
                UsableConfigHandler.INSTANCE.setGlobalStats(item, stats);
                sendEditorConfirm(player, true, true, "usable", getItemId(stack), "Usable global saved");
                LOGGER.info("[UsableConfig] Player {} saved GLOBAL usable config for {}",
                    player.getName().getString(), getItemId(stack));
            } else {
                UsableConfigHandler.INSTANCE.setSpecificStats(stack, stats);
                sendEditorConfirm(player, true, false, "usable", getItemId(stack), "Usable specific updated");
                LOGGER.info("[UsableConfig] Player {} updated SPECIFIC usable config for {}",
                    player.getName().getString(), getItemId(stack));
            }
        });
    }

    // =================================================================================
    // FOOD ITEMS MODIFICATION LOGIC
    // =================================================================================
    public static void handleFoodStatsData(com.devmod.network.FoodStatsPayload payload, IPayloadContext context) {
        enqueueWork(context, () -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            PacketValidator security = security();
            ValidationResult validation = security.validatePacket(player, "food_stats", true);
            if (!validation.isSuccess()) {
                player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", resolveValidationError(validation)));
                sendEditorConfirm(player, false, payload.isGlobal(), "food", "<unknown>", resolveValidationError(validation));
                return;
            }

            ItemStack stack = player.getMainHandItem();
            if (stack.isEmpty()) {
                sendEditorConfirm(player, false, payload.isGlobal(), "food", "<empty>", "No item in hand");
                return;
            }

            if (!Objects.equals(stack.getItem(), payload.item().getItem())) {
                sendEditorConfirm(player, false, payload.isGlobal(), "food", getItemId(stack), "Item mismatch (held vs payload)");
                return;
            }

            CompoundTag tag = payload.statsTag();
            if (tag == null || tag.isEmpty()) {
                sendEditorConfirm(player, false, payload.isGlobal(), "food", getItemId(stack), "Missing stats");
                return;
            }
            if (tag.getBoolean("reset")) {
                if (payload.isGlobal()) {
                    FoodConfigHandler.clearGlobalStats(getItemId(stack));
                    sendEditorConfirm(player, true, true, "food", getItemId(stack), "Food global reset");
                } else {
                    FoodConfigHandler.clearItemSpecificStats(stack);
                    sendEditorConfirm(player, true, false, "food", getItemId(stack), "Food reset to defaults");
                }
                return;
            }

            CompoundTag toLoad = tag.contains("food_stats_component") ? tag.getCompound("food_stats_component")
                : (tag.contains("FoodModStats") ? tag.getCompound("FoodModStats") : tag);

            FoodStats stats = new FoodStats();
            stats.load(toLoad);

            // Clamp values for security
            stats.setNutrition(Math.max(0, Math.min(20, stats.getNutrition())));
            stats.setSaturation(Math.max(0.0f, Math.min(2.0f, stats.getSaturation())));
            stats.setConsumptionTime(Math.max(1, Math.min(200, stats.getConsumptionTime())));

            // Clamp effect values
            for (FoodStats.FoodEffect effect : stats.getEffects()) {
                effect.duration = Math.max(1, Math.min(12000, effect.duration));
                effect.amplifier = Math.max(0, Math.min(255, effect.amplifier));
                effect.probability = Math.max(0.0f, Math.min(1.0f, effect.probability));
            }

            if (payload.isGlobal()) {
                String itemId = getItemId(stack);
                FoodConfigHandler.setGlobalStats(itemId, stats);
                sendEditorConfirm(player, true, true, "food", itemId, "Food global saved");
                LOGGER.info("[FoodConfig] Player {} saved GLOBAL food config for {}",
                    player.getName().getString(), itemId);
            } else {
                FoodConfigHandler.INSTANCE.setSpecificStats(stack, stats);
                sendEditorConfirm(player, true, false, "food", getItemId(stack), "Food specific updated");
                LOGGER.info("[FoodConfig] Player {} updated SPECIFIC food config for {}",
                    player.getName().getString(), getItemId(stack));
            }
        });
    }

    // =================================================================================
    // FUEL ITEMS MODIFICATION LOGIC
    // =================================================================================
    public static void handleFuelStatsData(com.devmod.network.FuelStatsPayload payload, IPayloadContext context) {
        enqueueWork(context, () -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            PacketValidator security = security();
            ValidationResult validation = security.validatePacket(player, "fuel_stats", true);
            if (!validation.isSuccess()) {
                player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", resolveValidationError(validation)));
                sendEditorConfirm(player, false, payload.isGlobal(), "fuel", "<unknown>", resolveValidationError(validation));
                return;
            }

            ItemStack stack = player.getMainHandItem();
            if (stack.isEmpty()) {
                sendEditorConfirm(player, false, payload.isGlobal(), "fuel", "<empty>", "No item in hand");
                return;
            }

            if (!Objects.equals(stack.getItem(), payload.item().getItem())) {
                sendEditorConfirm(player, false, payload.isGlobal(), "fuel", getItemId(stack), "Item mismatch (held vs payload)");
                return;
            }

            CompoundTag tag = payload.statsTag();
            if (tag == null || tag.isEmpty()) {
                sendEditorConfirm(player, false, payload.isGlobal(), "fuel", getItemId(stack), "Missing stats");
                return;
            }
            if (tag.getBoolean("reset")) {
                if (payload.isGlobal()) {
                    FuelConfigHandler.clearGlobalStats(getItemId(stack));
                    sendEditorConfirm(player, true, true, "fuel", getItemId(stack), "Fuel global reset");
                } else {
                    FuelConfigHandler.clearItemSpecificStats(stack);
                    sendEditorConfirm(player, true, false, "fuel", getItemId(stack), "Fuel reset to defaults");
                }
                return;
            }

            CompoundTag toLoad = tag.contains("fuel_stats_component") ? tag.getCompound("fuel_stats_component")
                : (tag.contains("FuelModStats") ? tag.getCompound("FuelModStats") : tag);

            FuelStats stats = FuelStats.fromTag(toLoad);

            // Clamp values for security
            stats.setBurnTime(Math.max(0, Math.min(32000, stats.getBurnTime())));
            stats.setEfficiencyMultiplier(Math.max(0.1f, Math.min(3.0f, stats.getEfficiencyMultiplier())));
            stats.setFurnaceCookTime(Math.max(1, Math.min(1000, stats.getFurnaceCookTime())));
            stats.setBlastFurnaceCookTime(Math.max(1, Math.min(500, stats.getBlastFurnaceCookTime())));
            stats.setSmokerCookTime(Math.max(1, Math.min(500, stats.getSmokerCookTime())));
            stats.setCampfireCookTime(Math.max(1, Math.min(2000, stats.getCampfireCookTime())));

            if (payload.isGlobal()) {
                String itemId = getItemId(stack);
                FuelConfigHandler.setGlobalStats(itemId, stats);
                sendEditorConfirm(player, true, true, "fuel", itemId, "Fuel global saved");
                LOGGER.info("[FuelConfig] Player {} saved GLOBAL fuel config for {}",
                    player.getName().getString(), itemId);
            } else {
                FuelConfigHandler.INSTANCE.setSpecificStats(stack, stats);
                sendEditorConfirm(player, true, false, "fuel", getItemId(stack), "Fuel specific updated");
                LOGGER.info("[FuelConfig] Player {} updated SPECIFIC fuel config for {}",
                    player.getName().getString(), getItemId(stack));
            }
        });
    }

    // =================================================================================
    // EQUIPMENT LOGIC
    // =================================================================================
    public static void handleEquipData(EquipMobPayload payload, IPayloadContext context) {
        enqueueWork(context, () -> {
            if (context.player() instanceof ServerPlayer player) {
                PacketValidator security = security();
                ValidationResult validation = security.validatePacket(player, "equip_mob", true);
                if (!validation.isSuccess()) {
                    player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", resolveValidationError(validation)));
                    return;
                }

                if (!security.validateEntityId(payload.entityId())) {
                    player.sendSystemMessage(I18n.translate("devmod.network.invalid_entity"));
                    return;
                }

                Entity target = player.serverLevel().getEntity(payload.entityId());
                if (target instanceof Mob mob) {
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
    // ITEM MODIFICATION LOGIC
    // =================================================================================
    public static void handleItemModification(ModifyItemPayload payload, IPayloadContext context) {
        enqueueWork(context, () -> {
            if (context.player() instanceof ServerPlayer player) {
                PacketValidator security = security();
                ValidationResult validation = security.validatePacket(player, "modify_item", true);
                if (!validation.isSuccess()) {
                    player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", resolveValidationError(validation)));
                    return;
                }

                ItemStack stack = player.getMainHandItem();
                if (stack.isEmpty()) {
                    player.sendSystemMessage(I18n.translate("devmod.network.no_item_in_hand"));
                    return;
                }

                if (stack.isDamageableItem()) {
                    int maxDamage = stack.getMaxDamage();
                    int newDamage = maxDamage - Math.max(0, Math.min(payload.durability(), maxDamage));
                    stack.setDamageValue(newDamage);
                }

                if (payload.unbreakable()) {
                    stack.set(nn(DataComponents.UNBREAKABLE), new net.minecraft.world.item.component.Unbreakable(true));
                } else {
                    stack.remove(nn(DataComponents.UNBREAKABLE));
                }

                if (payload.repairCost() >= 0) {
                    stack.set(nn(DataComponents.REPAIR_COST), payload.repairCost());
                }

                int enchantFails = 0;
                if (!payload.enchantmentChanges().isEmpty()) {
                    enchantFails = applyEnchantmentChanges(player, stack, payload.enchantmentChanges());
                }

                int attrFails = 0;
                if (!payload.attributeChanges().isEmpty()) {
                    attrFails = applyAttributeChanges(player, stack, payload.attributeChanges());
                }

                int totalFails = enchantFails + attrFails;
                if (totalFails == 0) {
                    player.sendSystemMessage(I18n.translate("devmod.network.item_modified"));
                } else {
                    player.sendSystemMessage(I18n.translate("devmod.network.item_modified_with_errors", totalFails));
                }
            }
        });
    }

    // =================================================================================
    // HELPER METHODS
    // =================================================================================
    private static String resolveValidationError(PacketValidator.ValidationResult validation) {
        return Objects.requireNonNullElse(validation.getErrorMessage(), "Validation failed");
    }

    private static void applyAttribute(Mob mob, Holder<Attribute> attr, double value, List<AttributeInstance> syncList) {
        AttributeInstance instance = mob.getAttribute(nn(attr));
        if (instance != null) {
            instance.setBaseValue(value);
            syncList.add(instance);
        }
    }

    private static void equipSlotSecure(Mob mob, EquipmentSlot slot, String itemName, PacketValidator security) {
        if (itemName == null || itemName.trim().isEmpty()) return;

        if (itemName.equalsIgnoreCase("air") || itemName.equalsIgnoreCase("clear")) {
            mob.setItemSlot(nn(slot), nn(ItemStack.EMPTY));
            return;
        }

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

    private static void applyDelta(WeaponStats target, CompoundTag delta) {
        if (target == null || delta == null || delta.isEmpty()) return;
        if (delta.contains("HeadMult")) target.setHeadMult(delta.getFloat("HeadMult"));
        if (delta.contains("BodyMult")) target.setBodyMult(delta.getFloat("BodyMult"));
        if (delta.contains("ArmsMult")) target.setArmsMult(delta.getFloat("ArmsMult"));
        if (delta.contains("LegsMult")) target.setLegsMult(delta.getFloat("LegsMult"));
        if (delta.contains("ArmorPen")) target.setArmorPenetration(delta.getFloat("ArmorPen"));
        if (delta.contains("BaseDmg")) target.setBaseDamageBonus(delta.getFloat("BaseDmg"));
        if (delta.contains("AtkDmg")) target.setAttackDamage(delta.getFloat("AtkDmg"));
        if (delta.contains("AtkSpd")) target.setAttackSpeed(delta.getFloat("AtkSpd"));
        if (delta.contains("AtkRch")) target.setAttackReach(delta.getFloat("AtkRch"));
        if (delta.contains("AtkKB")) target.setAttackKnockback(delta.getFloat("AtkKB"));
        if (delta.contains("DmgBonus")) target.setDamageBonus(delta.getFloat("DmgBonus"));
        if (delta.contains("SweepRatio")) target.setSweepingRatio(delta.getFloat("SweepRatio"));
        if (!delta.contains("DmgBonus") && delta.contains("Sweep")) {
            target.setDamageBonus(delta.getFloat("Sweep"));
        }
        if (delta.contains("CritCh")) target.setCritChance(delta.getFloat("CritCh"));
        if (delta.contains("CritDmg")) target.setCritDamage(delta.getFloat("CritDmg"));
        if (delta.contains("ArmorShred")) target.setArmorShred(delta.getFloat("ArmorShred"));
        if (delta.contains("FireDmg")) target.setFireDamageBonus(delta.getFloat("FireDmg"));
        if (delta.contains("MagicDmg")) target.setMagicDamageBonus(delta.getFloat("MagicDmg"));
        if (delta.contains("Lifesteal")) target.setLifesteal(delta.getFloat("Lifesteal"));
        if (delta.contains("VsUndead")) target.setDamageVsUndead(delta.getFloat("VsUndead"));
        if (delta.contains("VsArthro")) target.setDamageVsArthropods(delta.getFloat("VsArthro"));
        if (delta.contains("VsPlayers")) target.setDamageVsPlayers(delta.getFloat("VsPlayers"));
        if (delta.contains("TrueDmgPct")) target.setTrueDamagePercent(delta.getFloat("TrueDmgPct"));
        if (delta.contains("MaxDur")) target.setMaxDurability(delta.getInt("MaxDur"));
        if (delta.contains("CurDmg")) target.setCurrentDamage(delta.getInt("CurDmg"));
        if (delta.contains("Repair")) target.setRepairCost(delta.getInt("Repair"));
        if (delta.contains("Unbreakable")) target.setUnbreakable(delta.getBoolean("Unbreakable"));
        if (delta.contains("ClearToolRules")) target.setClearToolRules(delta.getBoolean("ClearToolRules"));
        if (delta.contains("DefaultSpeed")) target.setToolDefaultMiningSpeed(delta.getFloat("DefaultSpeed"));
        if (delta.contains("DamagePerBlock")) target.setToolDamagePerBlock(delta.getInt("DamagePerBlock"));
        if (delta.contains("AttrMods")) {
            target.setCustomAttributeModifiers(WeaponStats.readAttributeModifiers(delta.getList("AttrMods", 10)));
        }
    }

    private static void clampRanged(CompoundTag ranged) {
        ranged.putFloat("drawSpeed", clampFloat(ranged, "drawSpeed", 0.2f, (float) PacketValidator.MAX_RANGED_MULT));
        ranged.putFloat("chargeTime", clampFloat(ranged, "chargeTime", 0.2f, (float) PacketValidator.MAX_RANGED_MULT));
        ranged.putFloat("accuracy", clampFloat(ranged, "accuracy", 0.2f, 2.0f));
        ranged.putFloat("range", clampFloat(ranged, "range", 0.2f, 5.0f));
        ranged.putFloat("projectileSpeed", clampFloat(ranged, "projectileSpeed", 0.2f, (float) PacketValidator.MAX_RANGED_SPEED));
        ranged.putFloat("projectileGravity", clampFloat(ranged, "projectileGravity", 0f, (float) PacketValidator.MAX_RANGED_GRAVITY));
        ranged.putFloat("projectileSpread", clampFloat(ranged, "projectileSpread", 0f, (float) PacketValidator.MAX_RANGED_SPREAD));
        ranged.putFloat("baseDamage", clampFloat(ranged, "baseDamage", 0f, (float) PacketValidator.MAX_RANGED_BASE_DAMAGE));
        ranged.putInt("piercing", clampInt(ranged, "piercing", 0, 10));
        ranged.putInt("multishotCount", clampInt(ranged, "multishotCount", 1, 5));
        ranged.putBoolean("multishot", ranged.getBoolean("multishot"));
        ranged.putBoolean("infinityOverride", ranged.getBoolean("infinityOverride"));
        ranged.putFloat("critChance", clampFloat(ranged, "critChance", 0f, 1f));
        ranged.putFloat("critDamage", clampFloat(ranged, "critDamage", 0.5f, 5.0f));
        ranged.putFloat("riptideDistance", clampFloat(ranged, "riptideDistance", 0f, 64f));
        ranged.putFloat("loyaltySpeed", clampFloat(ranged, "loyaltySpeed", 0f, (float) PacketValidator.MAX_RANGED_SPEED));
        ranged.putBoolean("riptideRequiresWater", ranged.getBoolean("riptideRequiresWater"));
        ranged.putBoolean("channeling", ranged.getBoolean("channeling"));
    }

    private static float clampFloat(CompoundTag tag, @Nonnull String key, float min, float max) {
        float val = tag.contains(key) ? tag.getFloat(key) : min;
        return Math.max(min, Math.min(max, val));
    }

    private static int clampInt(CompoundTag tag, @Nonnull String key, int min, int max) {
        int val = tag.contains(key) ? tag.getInt(key) : min;
        return Math.max(min, Math.min(max, val));
    }

    private static void sanitizeToolRules(WeaponStats stats, PacketValidator security) {
        stats.setToolDefaultMiningSpeed((float) security.validateToolSpeed(stats.getToolDefaultMiningSpeed()));
        stats.setToolDamagePerBlock(security.validateToolDamagePerBlock(stats.getToolDamagePerBlock()));
        if (stats.toolRules == null) {
            stats.toolRules = new ArrayList<>();
            return;
        }
        List<WeaponStats.ToolRuleData> cleaned = new ArrayList<>();
        for (WeaponStats.ToolRuleData rule : stats.toolRules) {
            if (rule == null || rule.isEmpty()) continue;
            String rawTag = rule.blockTag == null ? "" : rule.blockTag;
            String tag = security.validateTagId(rawTag);
            if (tag == null || tag.isBlank()) continue;
            WeaponStats.ToolRuleData safe = new WeaponStats.ToolRuleData();
            safe.blockTag = tag;
            safe.speed = (float) security.validateToolSpeed(rule.speed);
            safe.correctForDrops = rule.correctForDrops;
            safe.isTag = rule.isTag || rawTag.startsWith("#");
            cleaned.add(safe);
            if (cleaned.size() >= PacketValidator.MAX_TOOL_RULES) break;
        }
        stats.toolRules = cleaned;
    }

    private static int applyEnchantmentChanges(ServerPlayer player, ItemStack stack, List<String> changes) {
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(
            nn(stack.getOrDefault(nn(DataComponents.ENCHANTMENTS), nn(ItemEnchantments.EMPTY)))
        );

        int failCount = 0;
        List<String> failedEnchants = new ArrayList<>();

        for (String change : changes) {
            List<String> parts = COLON_SPLITTER.splitToList(Objects.requireNonNull(change, "change"));
            if (parts.size() < 2) {
                failCount++;
                failedEnchants.add(change + " (invalid format)");
                continue;
            }

            String enchantId;
            int level;
            if (parts.size() >= 3) {
                enchantId = parts.get(0) + ":" + parts.get(1);
                try {
                    level = Integer.parseInt(parts.get(2));
                } catch (NumberFormatException e) {
                    failCount++;
                    failedEnchants.add(enchantId + " (invalid level)");
                    continue;
                }
            } else {
                String rawId = parts.get(0);
                enchantId = rawId.contains(":") ? rawId : "minecraft:" + rawId;
                try {
                    level = Integer.parseInt(parts.get(1));
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
                        enchantments.removeIf(h -> h.equals(holder));
                    } else {
                        enchantments.set(nn(holder), level);
                    }
                } else {
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

        if (failCount > 0) {
            String failedList = String.join(", ", failedEnchants);
            player.sendSystemMessage(I18n.translate("devmod.network.enchant_failed", failCount, failedList));
        }

        return failCount;
    }

    private static int applyAttributeChanges(ServerPlayer player, ItemStack stack, List<String> changes) {
        ItemAttributeModifiers existing = nn(stack.getOrDefault(nn(DataComponents.ATTRIBUTE_MODIFIERS), nn(ItemAttributeModifiers.EMPTY)));
        List<ItemAttributeModifiers.Entry> entries = new ArrayList<>(existing.modifiers());

        int failCount = 0;
        List<String> failedAttrs = new ArrayList<>();

        for (String change : changes) {
            List<String> parts = COLON_SPLITTER.splitToList(Objects.requireNonNull(change, "change"));
            if (parts.size() < 3) {
                failCount++;
                failedAttrs.add(change + " (invalid format)");
                continue;
            }

            String attrId;
            double value;
            int operation;

            try {
                if (parts.size() >= 4) {
                    attrId = parts.get(0) + ":" + parts.get(1);
                    value = Double.parseDouble(parts.get(2));
                    operation = Integer.parseInt(parts.get(3));
                } else {
                    String rawId = parts.get(0);
                    attrId = rawId.contains(":") ? rawId : "minecraft:" + rawId;
                    value = Double.parseDouble(parts.get(1));
                    operation = Integer.parseInt(parts.get(2));
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
                    var mapped = com.devmod.integration.PufferfishIntegration.map(attrLoc, registry);
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

                    entries.removeIf(e -> e.attribute().equals(holder));

                    ResourceLocation modifierId = nn(ResourceLocation.fromNamespaceAndPath("devmod", "custom_" + attrLoc.getPath()));
                    AttributeModifier modifier = new AttributeModifier(modifierId, value, op);
                    entries.add(new ItemAttributeModifiers.Entry(nn(holder), modifier, EquipmentSlotGroup.MAINHAND));
                } else {
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

        if (failCount > 0) {
            String failedList = String.join(", ", failedAttrs);
            player.sendSystemMessage(I18n.translate("devmod.network.attr_failed", failCount, failedList));
        }

        return failCount;
    }
}
