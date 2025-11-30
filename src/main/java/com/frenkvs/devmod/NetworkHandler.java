package com.frenkvs.devmod;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = "devmod", bus = EventBusSubscriber.Bus.MOD)
public class NetworkHandler {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        // Canale 1: Statistiche Mostri
        event.registrar("1").playToServer(
                UpdateMobStatsPayload.TYPE, UpdateMobStatsPayload.STREAM_CODEC, NetworkHandler::handleMobData
        );
        // Canale 2: Statistiche Armi
        event.registrar("2").playToServer(
                UpdateWeaponPayload.TYPE, UpdateWeaponPayload.STREAM_CODEC, NetworkHandler::handleWeaponData
        );
        // Canale 3: Equipaggiamento Mostri
        event.registrar("3").playToServer(
                EquipMobPayload.TYPE, EquipMobPayload.STREAM_CODEC, NetworkHandler::handleEquipData
        );
        event.registrar("4").playToClient(AggroLinkPayload.TYPE, AggroLinkPayload.STREAM_CODEC, NetworkHandler::handleAggroLink);
    }

    // =================================================================================
    // 1. LOGICA MODIFICA MOSTRI (Vita, Danno, Reach, Globale/Specifico)
    // =================================================================================
    private static void handleMobData(UpdateMobStatsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ServerLevel level = player.serverLevel();
                Entity targetEntity = level.getEntity(payload.entityId());

                if (targetEntity instanceof Mob targetMob) {
                    EntityType<?> typeToUpdate = targetMob.getType();

                    // --- SALVATAGGIO CONFIGURAZIONE GLOBALE ---
                    if (payload.isGlobal()) {
                        MobConfigManager.setGlobalStats(
                                typeToUpdate,
                                payload.followRange(),
                                payload.damage(),
                                payload.maxHealth(),
                                payload.armor(),
                                payload.attackRange()
                        );
                        player.sendSystemMessage(Component.literal("§6[GLOBALE] §fSalvate impostazioni RAM."));
                    }

                    // --- APPLICAZIONE AI MOB ESISTENTI ---
                    int count = 0;
                    for (Entity entity : level.getAllEntities()) {
                        if (entity.getType() == typeToUpdate && entity instanceof Mob mob) {

                            // Se è specifico, salta tutti tranne quello giusto
                            if (!payload.isGlobal() && mob.getId() != payload.entityId()) continue;

                            List<AttributeInstance> attributesToSync = new ArrayList<>();

                            applyAttribute(mob, Attributes.FOLLOW_RANGE, payload.followRange(), attributesToSync);
                            applyAttribute(mob, Attributes.ATTACK_DAMAGE, payload.damage(), attributesToSync);
                            applyAttribute(mob, Attributes.ARMOR, payload.armor(), attributesToSync);
                            applyAttribute(mob, Attributes.LUCK, payload.attackRange(), attributesToSync);

                            AttributeInstance healthAttr = mob.getAttribute(Attributes.MAX_HEALTH);
                            if (healthAttr != null) {
                                healthAttr.setBaseValue(payload.maxHealth());
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
                    }

                    if (!payload.isGlobal()) {
                        player.sendSystemMessage(Component.literal("§a[SPECIFICO] §fAggiornato singolo mob."));
                    } else {
                        player.sendSystemMessage(Component.literal("§a[AGGIORNAMENTO] §fApplicato a " + count + " mob presenti."));
                    }
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
                ItemStack stack = player.getMainHandItem();
                if (stack.isEmpty()) return;

                WeaponStats stats = new WeaponStats();
                stats.armorPenetration = payload.pen();
                stats.baseDamageBonus = payload.bonus();

                if (payload.isGlobal()) {
                    WeaponConfigManager.setGlobalStats(stack.getItem(), stats);
                    player.sendSystemMessage(Component.literal("§a[WeaponConfig] §fSalvate impostazioni GLOBALI per " + stack.getHoverName().getString()));
                } else {
                    WeaponConfigManager.setSpecificStats(stack, stats);
                    if (payload.name() != null && !payload.name().isEmpty()) {
                        stack.set(DataComponents.CUSTOM_NAME, Component.literal(payload.name()));
                    }
                    player.sendSystemMessage(Component.literal("§a[WeaponConfig] §fArma specifica aggiornata!"));
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
                Entity target = player.serverLevel().getEntity(payload.entityId());
                if (target instanceof Mob mob) {

                    equipSlot(mob, EquipmentSlot.MAINHAND, payload.mainHand());
                    equipSlot(mob, EquipmentSlot.OFFHAND, payload.offHand());
                    equipSlot(mob, EquipmentSlot.HEAD, payload.head());
                    equipSlot(mob, EquipmentSlot.CHEST, payload.chest());
                    equipSlot(mob, EquipmentSlot.LEGS, payload.legs());
                    equipSlot(mob, EquipmentSlot.FEET, payload.feet());

                    player.sendSystemMessage(Component.literal("§a[Equip] §fOggetti aggiornati su: " + mob.getName().getString()));
                }
            }
        });
    }

    private static void handleAggroLink(AggroLinkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Aggiungiamo la linea al sistema di rendering
            WorldRenderEvents.addAggroLine(payload.sourceId(), payload.targetId());
        });
    }

    // =================================================================================
    // METODI HELPER (Devono stare DENTRO la classe, prima dell'ultima parentesi graffa)
    // =================================================================================

    private static void equipSlot(Mob mob, EquipmentSlot slot, String itemName) {
        if (itemName == null || itemName.trim().isEmpty()) return;
        try {
            if (itemName.equalsIgnoreCase("air") || itemName.equalsIgnoreCase("clear")) {
                mob.setItemSlot(slot, ItemStack.EMPTY);
                return;
            }
            ResourceLocation id = ResourceLocation.parse(itemName.contains(":") ? itemName : "minecraft:" + itemName);
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item != net.minecraft.world.item.Items.AIR) {
                mob.setItemSlot(slot, new ItemStack(item));
            }
        } catch (Exception e) { }
    }

    // ECCO IL METODO CHE TI DAVA ERRORE: ORA È DENTRO LA CLASSE
    private static void applyAttribute(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, double value, List<AttributeInstance> syncList) {
        AttributeInstance instance = mob.getAttribute(attr);
        if (instance != null) {
            instance.setBaseValue(value);
            syncList.add(instance);
        }
    }

} // <--- Questa è la parentesi finale fondamentale