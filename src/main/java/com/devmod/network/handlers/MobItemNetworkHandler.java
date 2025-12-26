package com.devmod.network.handlers;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

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

import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.DevMod;
import com.devmod.config.ArmorConfigManager;
import com.devmod.config.Config;
import com.devmod.config.FoodConfigManager;
import com.devmod.config.FuelConfigManager;
import com.devmod.config.MobConfigManager;
import com.devmod.config.UsableConfigManager;
import com.devmod.config.WeaponConfigManager;
import com.devmod.network.EquipMobPayload;
import com.devmod.network.MobConfigConfirmPayload;
import com.devmod.network.ModifyItemPayload;
import com.devmod.network.PacketValidator;
import com.devmod.network.PacketValidator.ValidationResult;
import com.devmod.network.RangedWeaponStatsPayload;
import com.devmod.network.UpdateArmorPayload;
import com.devmod.network.UpdateMobStatsPayload;
import com.devmod.network.UpdateWeaponPayload;
import com.devmod.stats.ArmorStats;
import com.devmod.stats.FoodStats;
import com.devmod.stats.FuelStats;
import com.devmod.stats.UsableStats;
import com.devmod.stats.WeaponStats;
import com.devmod.util.I18n;

public final class MobItemNetworkHandler extends NetworkHandlerBase {

    private MobItemNetworkHandler() {}

    // =================================================================================
    // MOB MODIFICATION LOGIC
    // =================================================================================
    public static void handleMobData(UpdateMobStatsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
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
        context.enqueueWork(() -> {
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

                    String adminName = player.getName().getString();
                    var broadcastMsg = I18n.translate("devmod.network.weapon_global_broadcast", adminName, itemName);
                    for (ServerPlayer otherPlayer : player.server.getPlayerList().getPlayers()) {
                        if (!otherPlayer.getUUID().equals(player.getUUID())) {
                            otherPlayer.sendSystemMessage(broadcastMsg);
                        }
                    }
                } else {
                    WeaponConfigManager.setSpecificStats(stack, stats);
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
        context.enqueueWork(() -> {
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
                if (toLoad.contains("Mace")) variant.put("Mace", nn(toLoad.getCompound("Mace")));
                if (toLoad.contains("Trident")) variant.put("Trident", nn(toLoad.getCompound("Trident")));
                WeaponConfigManager.setSpecificStats(stack, stats, variant);
                sendEditorConfirm(player, true, false, "weapon", getItemId(stack), "Weapon specific updated");
            }
        });
    }

    public static void handleWeaponStatsDataV2(com.devmod.network.WeaponStatsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
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

            CompoundTag toLoad = tag.contains("weapon_stats_component") ? tag.getCompound("weapon_stats_component")
                : (tag.contains("WeaponModStats") ? tag.getCompound("WeaponModStats") : tag);

            WeaponStats stats;
            if (tag.contains("delta")) {
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
                if (toLoad.contains("Mace")) variant.put("Mace", nn(toLoad.getCompound("Mace")));
                if (toLoad.contains("Trident")) variant.put("Trident", nn(toLoad.getCompound("Trident")));
                WeaponConfigManager.setSpecificStats(stack, stats, variant);
                sendEditorConfirm(player, true, false, "weapon", getItemId(stack), "Weapon specific updated");
            }
        });
    }

    // =================================================================================
    // RANGED WEAPON LOGIC
    // =================================================================================
    public static void handleRangedWeaponData(RangedWeaponStatsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
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

                CompoundTag root = payload.statsTag() == null ? new CompoundTag() : payload.statsTag().copy();
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
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PacketValidator security = security();
                ValidationResult validation = security.validatePacket(player, "armor_stats", true);
                if (!validation.isSuccess()) {
                    player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", resolveValidationError(validation)));
                    sendEditorConfirm(player, false, payload.isGlobal(), "armor", payload.itemName(), resolveValidationError(validation));
                    return;
                }

                ArmorStats stats = payload.toArmorStats();
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
                    ResourceLocation itemLoc = ResourceLocation.tryParse(nn(payload.itemName()));
                    if (itemLoc != null && BuiltInRegistries.ITEM.containsKey(itemLoc)) {
                        Item item = BuiltInRegistries.ITEM.get(itemLoc);
                        ArmorConfigManager.setGlobalStats(item, stats);
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
                        if (!armor.isEmpty() && ArmorConfigManager.isArmor(armor)) {
                            ArmorConfigManager.setSpecificStats(armor, stats);
                            player.sendSystemMessage(I18n.translate("devmod.network.armor_specific_updated"));
                            sendEditorConfirm(player, true, false, "armor", getItemId(armor), "Armor specific updated");
                        } else {
                            player.sendSystemMessage(I18n.translate("devmod.network.no_armor_in_slot"));
                            sendEditorConfirm(player, false, false, "armor", getItemId(armor), "No armor in slot");
                        }
                    } else {
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

    public static void handleArmorStatsDataV2(com.devmod.network.ArmorStatsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
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
            if (tag == null || tag.isEmpty()) {
                sendEditorConfirm(player, false, payload.isGlobal(), "armor", getItemId(payloadStack), "Missing stats");
                return;
            }

            CompoundTag toLoad = tag.contains("armor_stats_component") ? tag.getCompound("armor_stats_component")
                : (tag.contains("ArmorModStats") ? tag.getCompound("ArmorModStats") : tag);
            ArmorStats stats = ArmorStats.load(toLoad == null ? new CompoundTag() : toLoad);

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

            if (target.isEmpty() || !ArmorConfigManager.isArmor(target)) {
                sendEditorConfirm(player, false, false, "armor", getItemId(payloadStack), "No matching armor piece");
                return;
            }

            ArmorConfigManager.setSpecificStats(target, stats);
            sendEditorConfirm(player, true, false, "armor", getItemId(target), "Armor specific updated");
        });
    }

    // =================================================================================
    // USABLE ITEMS MODIFICATION LOGIC
    // =================================================================================
    public static void handleUsableStatsData(com.devmod.network.UsableStatsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
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

            CompoundTag toLoad = tag.contains("usable_stats_component") ? tag.getCompound("usable_stats_component")
                : (tag.contains("UsableModStats") ? tag.getCompound("UsableModStats") : tag);

            UsableStats stats = UsableStats.load(toLoad);

            // Clamp values for security
            stats.useDuration = Math.max(0, Math.min(200, stats.useDuration));
            stats.cooldownDuration = Math.max(0, Math.min(600, stats.cooldownDuration));
            stats.projectileSpeed = Math.max(0.5f, Math.min(5.0f, stats.projectileSpeed));
            stats.projectileGravity = Math.max(0.0f, Math.min(0.1f, stats.projectileGravity));
            stats.projectileInaccuracy = Math.max(0.0f, Math.min(5.0f, stats.projectileInaccuracy));
            stats.projectileDamage = Math.max(0, Math.min(20, stats.projectileDamage));

            if (payload.isGlobal()) {
                Item item = stack.getItem();
                UsableConfigManager.setGlobalStats(item, stats);
                sendEditorConfirm(player, true, true, "usable", getItemId(stack), "Usable global saved");
                LOGGER.info("[UsableConfig] Player {} saved GLOBAL usable config for {}",
                    player.getName().getString(), getItemId(stack));
            } else {
                UsableConfigManager.setSpecificStats(stack, stats);
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
        context.enqueueWork(() -> {
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

            CompoundTag toLoad = tag.contains("food_stats_component") ? tag.getCompound("food_stats_component")
                : (tag.contains("FoodModStats") ? tag.getCompound("FoodModStats") : tag);

            FoodStats stats = new FoodStats();
            stats.load(toLoad);

            // Clamp values for security
            stats.nutrition = Math.max(0, Math.min(20, stats.nutrition));
            stats.saturation = Math.max(0.0f, Math.min(2.0f, stats.saturation));
            stats.consumptionTime = Math.max(1, Math.min(200, stats.consumptionTime));

            // Clamp effect values
            for (FoodStats.FoodEffect effect : stats.effects) {
                effect.duration = Math.max(1, Math.min(12000, effect.duration));
                effect.amplifier = Math.max(0, Math.min(255, effect.amplifier));
                effect.probability = Math.max(0.0f, Math.min(1.0f, effect.probability));
            }

            if (payload.isGlobal()) {
                String itemId = getItemId(stack);
                FoodConfigManager.setGlobalStats(itemId, stats);
                sendEditorConfirm(player, true, true, "food", itemId, "Food global saved");
                LOGGER.info("[FoodConfig] Player {} saved GLOBAL food config for {}",
                    player.getName().getString(), itemId);
            } else {
                FoodConfigManager.setSpecificStats(stack, stats);
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
        context.enqueueWork(() -> {
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

            CompoundTag toLoad = tag.contains("fuel_stats_component") ? tag.getCompound("fuel_stats_component")
                : (tag.contains("FuelModStats") ? tag.getCompound("FuelModStats") : tag);

            FuelStats stats = FuelStats.load(toLoad);

            // Clamp values for security
            stats.burnTime = Math.max(0, Math.min(32000, stats.burnTime));
            stats.efficiencyMultiplier = Math.max(0.1f, Math.min(3.0f, stats.efficiencyMultiplier));
            stats.furnaceCookTime = Math.max(1, Math.min(1000, stats.furnaceCookTime));
            stats.blastFurnaceCookTime = Math.max(1, Math.min(500, stats.blastFurnaceCookTime));
            stats.smokerCookTime = Math.max(1, Math.min(500, stats.smokerCookTime));
            stats.campfireCookTime = Math.max(1, Math.min(2000, stats.campfireCookTime));

            if (payload.isGlobal()) {
                String itemId = getItemId(stack);
                FuelConfigManager.setGlobalStats(itemId, stats);
                sendEditorConfirm(player, true, true, "fuel", itemId, "Fuel global saved");
                LOGGER.info("[FuelConfig] Player {} saved GLOBAL fuel config for {}",
                    player.getName().getString(), itemId);
            } else {
                FuelConfigManager.setSpecificStats(stack, stats);
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
        context.enqueueWork(() -> {
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
        context.enqueueWork(() -> {
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
        if (delta.contains("DmgBonus")) target.damageBonus = delta.getFloat("DmgBonus");
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
            String[] parts = change.split(":");
            if (parts.length < 2) {
                failCount++;
                failedEnchants.add(change + " (invalid format)");
                continue;
            }

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
            String[] parts = change.split(":");
            if (parts.length < 3) {
                failCount++;
                failedAttrs.add(change + " (invalid format)");
                continue;
            }

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
                    var mapped = com.devmod.integration.PufferfishCompat.map(attrLoc, registry);
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
